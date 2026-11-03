package nl.kistdb;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * De straal naar de kist. Alles bestaat alleen als nep-entiteiten bij de zoekende speler (andere spelers zien niets):
 * <ul>
 *   <li>een gloeiende lijn vanaf je borst naar de kist (door muren heen zichtbaar)</li>
 *   <li>een gloeiende omlijning om de kist en een lichtzuil erboven</li>
 *   <li>het gezochte item zwevend boven de kist, met een label "23× · 12 m" dat door muren heen leesbaar is</li>
 *   <li>in de actiebalk een pijl, de afstand en de coördinaten</li>
 * </ul>
 */
public final class Beams {
	private static final int LINE_COLOR = 0x55FFFF;
	private static final int BOX_COLOR = 0xFFAA00;
	private static final float LINE_WIDTH = 0.07f;
	/** Het lijn-entity staat zoveel blokken onder de speler, zodat het zicht-vak (culling) de hele lijn omvat. */
	private static final float CULL_OFFSET = 160f;
	private static final Map<UUID, Beam> ACTIVE = new HashMap<>();
	private static final Map<UUID, ArrayList<Flash>> FLASHES = new HashMap<>();

	private record Flash(ElementHolder holder, int[] ticksLeft) {
	}

	private static final class Beam {
		ServerPlayer player;
		ServerLevel level;
		BlockPos key;
		Vec3 center;
		AABB box;
		ElementHolder site;
		ElementHolder follow;
		BlockDisplayElement line;
		@Nullable TextDisplayElement label;
		Component what;
		int count;
		int ticksLeft;
		int nearTicks;
	}

	private Beams() {
	}

	/** Toont een straal naar de container op {@code key}. {@code item} mag null zijn (dan alleen "Kist"). */
	public static void show(ServerPlayer player, ServerLevel level, BlockPos key, @Nullable ItemStack item, int count, int seconds) {
		clear(player);
		Beam b = new Beam();
		b.player = player;
		b.level = level;
		b.key = key;
		b.box = Containers.boxOf(level, key);
		b.center = b.box.getCenter();
		b.what = item != null ? item.getHoverName() : Component.literal("Kist");
		b.count = count;
		b.ticksLeft = seconds * 20;

		// --- bij de kist ---
		b.site = new ElementHolder();
		double top = b.box.maxY - b.center.y;
		b.site.addElement(outline(b.box, b.center, BOX_COLOR));

		BlockDisplayElement column = new BlockDisplayElement(Sounds.block("light_blue_stained_glass"));
		column.setOffset(new Vec3(0, top, 0));
		column.setTranslation(new Vector3f(-0.1f, 0, -0.1f));
		column.setScale(new Vector3f(0.2f, 48f, 0.2f));
		column.setGlowing(true);
		column.setGlowColorOverride(LINE_COLOR);
		column.setBrightness(new Brightness(15, 15));
		column.setViewRange(8f);
		column.setDisplaySize(1f, 50f);
		b.site.addElement(column);

		if (item != null) {
			ItemDisplayElement icon = new ItemDisplayElement(item.copyWithCount(1));
			icon.setOffset(new Vec3(0, top + 0.75, 0));
			icon.setScale(new Vector3f(0.6f, 0.6f, 0.6f));
			icon.setBillboardMode(Display.BillboardConstraints.CENTER);
			icon.setBrightness(new Brightness(15, 15));
			icon.setViewRange(8f);
			b.site.addElement(icon);
		}

		TextDisplayElement label = new TextDisplayElement(labelText(b, player));
		label.setOffset(new Vec3(0, top + 1.35, 0));
		label.setBillboardMode(Display.BillboardConstraints.CENTER);
		label.setSeeThrough(true);
		label.setViewRange(8f);
		b.site.addElement(label);
		b.label = label;

		Vec3 center = b.center;
		new ManualAttachment(b.site, level, () -> center);
		b.site.startWatching(player);

		// --- de lijn, die met de speler meebeweegt ---
		b.follow = new ElementHolder();
		b.line = new BlockDisplayElement(Sounds.block("light_blue_stained_glass"));
		b.line.setOffset(new Vec3(0, -CULL_OFFSET, 0));
		b.line.setGlowing(true);
		b.line.setGlowColorOverride(LINE_COLOR);
		b.line.setBrightness(new Brightness(15, 15));
		b.line.setViewRange(8f);
		b.line.setDisplaySize(2 * CULL_OFFSET, 2 * CULL_OFFSET);
		b.line.setInterpolationDuration(1);
		b.line.setTeleportDuration(1);
		updateLine(b);
		b.follow.addElement(b.line);
		new ManualAttachment(b.follow, level, player::position);
		b.follow.startWatching(player);

		ACTIVE.put(player.getUUID(), b);
		Sounds.play(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4f, 1.6f);
	}

	/** Korte gekleurde omlijning (bv. bij noIndex aan/uit). */
	public static void flash(ServerPlayer player, ServerLevel level, BlockPos key, int color, int ticks) {
		AABB box = Containers.boxOf(level, key);
		Vec3 center = box.getCenter();
		ElementHolder holder = new ElementHolder();
		holder.addElement(outline(box, center, color));
		new ManualAttachment(holder, level, () -> center);
		holder.startWatching(player);
		FLASHES.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(new Flash(holder, new int[]{ticks}));
	}

	private static BlockDisplayElement outline(AABB box, Vec3 center, int color) {
		BlockState glass = Sounds.block("white_stained_glass");
		BlockDisplayElement e = new BlockDisplayElement(glass);
		e.setOffset(new Vec3(box.minX - center.x, box.minY - center.y, box.minZ - center.z));
		e.setTranslation(new Vector3f(-0.02f, -0.02f, -0.02f));
		e.setScale(new Vector3f((float) box.getXsize() + 0.04f, (float) box.getYsize() + 0.04f, (float) box.getZsize() + 0.04f));
		e.setGlowing(true);
		e.setGlowColorOverride(color);
		e.setBrightness(new Brightness(15, 15));
		e.setViewRange(8f);
		e.setDisplaySize(3f, 2f);
		return e;
	}

	/** Zet de lijn van de borst van de speler richting de kist. */
	private static void updateLine(Beam b) {
		Vec3 from = b.player.position().add(0, 1.0, 0);
		Vec3 d = b.center.subtract(from);
		double dist = d.length();
		float start = 1.0f;
		float length = (float) (dist - start - 0.6);
		if (length < 0.3f || dist < 0.01) {
			b.line.setScale(new Vector3f(0, 0, 0));
			b.line.startInterpolationIfDirty();
			return;
		}
		Vector3f dir = new Vector3f((float) d.x, (float) d.y, (float) d.z).normalize();
		Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), dir);
		Vector3f origin = new Vector3f(0, CULL_OFFSET + 1.0f, 0).add(new Vector3f(dir).mul(start));
		Vector3f center = rot.transform(new Vector3f(-LINE_WIDTH / 2, -LINE_WIDTH / 2, 0));
		b.line.setTranslation(origin.add(center));
		b.line.setLeftRotation(rot);
		b.line.setScale(new Vector3f(LINE_WIDTH, LINE_WIDTH, length));
		b.line.startInterpolationIfDirty();
	}

	private static Component labelText(Beam b, ServerPlayer player) {
		int dist = (int) Math.round(player.position().distanceTo(b.center));
		var text = Component.empty();
		if (b.count > 0) {
			text.append(Component.literal(b.count + "× ").withStyle(ChatFormatting.GREEN));
		}
		text.append(b.what.copy().withStyle(ChatFormatting.WHITE));
		text.append(Component.literal("  " + dist + " m").withStyle(ChatFormatting.YELLOW));
		return text;
	}

	/** Pijl t.o.v. waar de speler naar kijkt. */
	private static String arrow(ServerPlayer player, Vec3 target) {
		Vec3 d = target.subtract(player.position());
		double yawTo = Math.toDegrees(Math.atan2(-d.x, d.z));
		double rel = Mth.wrapDegrees(yawTo - player.getYRot());
		String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
		int idx = (int) Math.floorMod(Math.round(rel / 45.0), 8);
		String a = arrows[idx];
		double dy = d.y;
		if (dy > 3) a += " ▲";
		else if (dy < -3) a += " ▼";
		return a;
	}

	static void tick(MinecraftServer server, int tick) {
		if (!ACTIVE.isEmpty()) {
			for (Beam b : new ArrayList<>(ACTIVE.values())) {
				ServerPlayer p = b.player;
				if (p.isRemoved() || p.hasDisconnected() || p.level() != b.level || --b.ticksLeft <= 0) {
					clear(p);
					continue;
				}
				updateLine(b);
				double dist = p.position().distanceTo(b.center);
				if (tick % 5 == 0) {
					if (b.label != null) b.label.setText(labelText(b, p));
					Component bar = Component.literal(arrow(p, b.center) + " ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
							.append(b.what.copy().withStyle(ChatFormatting.WHITE))
							.append(Component.literal("  " + Math.round(dist) + " m").withStyle(ChatFormatting.YELLOW))
							.append(Component.literal("  (" + b.key.getX() + " " + b.key.getY() + " " + b.key.getZ() + ")").withStyle(ChatFormatting.GRAY));
					Tracker.actionbar(p, bar);
				}
				b.site.tick();
				b.follow.tick();
				// Lang dichtbij gestaan: klaar
				if (dist < 2.2) {
					if (++b.nearTicks > 60) clear(p);
				} else {
					b.nearTicks = 0;
				}
			}
		}
		if (!FLASHES.isEmpty()) {
			FLASHES.values().forEach(list -> list.removeIf(f -> {
				f.holder().tick();
				if (--f.ticksLeft()[0] <= 0) {
					f.holder().destroy();
					return true;
				}
				return false;
			}));
			FLASHES.values().removeIf(ArrayList::isEmpty);
		}
	}

	/** Speler opent een kist: is dat het doel, dan is hij er. */
	public static void arrivedAt(ServerPlayer player, ServerLevel level, BlockPos key) {
		Beam b = ACTIVE.get(player.getUUID());
		if (b != null && b.level == level && b.key.equals(key)) {
			clear(player);
			Tracker.actionbar(player, Component.literal("✔ Gevonden").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
		}
	}

	public static boolean has(ServerPlayer player) {
		return ACTIVE.containsKey(player.getUUID());
	}

	public static void clear(ServerPlayer player) {
		Beam b = ACTIVE.remove(player.getUUID());
		if (b != null) {
			b.site.destroy();
			b.follow.destroy();
		}
		ArrayList<Flash> flashes = FLASHES.remove(player.getUUID());
		if (flashes != null) flashes.forEach(f -> f.holder().destroy());
	}

	static void clearAll() {
		for (Beam b : new ArrayList<>(ACTIVE.values())) clear(b.player);
		for (ArrayList<Flash> list : FLASHES.values()) list.forEach(f -> f.holder().destroy());
		FLASHES.clear();
	}
}
