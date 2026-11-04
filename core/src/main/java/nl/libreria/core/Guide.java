package nl.libreria.core;

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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Wijst de weg naar een plek. Alles bestaat alleen als nep-entiteiten bij die ene speler:
 * een gloeiende lijn vanaf je borst (door muren heen zichtbaar), een omlijning en lichtzuil bij het doel,
 * een zwevend icoon met label en afstand, en een pijl met afstand in de actiebalk.
 */
public final class Guide {
	private static final int LINE_COLOR = 0x55FFFF;
	private static final int BOX_COLOR = 0xFFAA00;
	private static final float LINE_WIDTH = 0.07f;
	/** Het lijn-entity staat zo ver onder de speler, zodat het zichtvak (culling) de hele lijn omvat. */
	private static final float CULL_OFFSET = 160f;

	private static final Map<UUID, Beam> ACTIVE = new HashMap<>();
	private static final Map<UUID, ArrayList<Flash>> FLASHES = new HashMap<>();

	private record Flash(ElementHolder holder, int[] ticksLeft) {
	}

	private static final class Beam {
		ServerPlayer player;
		ServerLevel level;
		@Nullable AABB box;
		Vec3 center;
		Vec3 top;
		BlockPos anchor;
		ElementHolder site;
		ElementHolder follow;
		BlockDisplayElement line;
		TextDisplayElement label;
		Component what;
		int count;
		int ticksLeft;
		int nearTicks;
	}

	private Guide() {
	}

	/** Straal naar een container (of ander blok-gebied). seconds <= 0 = instelling van de speler. */
	public static void toBox(ServerPlayer player, ServerLevel level, AABB box, @Nullable ItemStack icon, Component what, int count, int seconds) {
		start(player, level, box, box.getCenter(), new Vec3(box.getCenter().x, box.maxY, box.getCenter().z), icon, what, count, seconds);
	}

	/** Straal naar een punt in de wereld (bv. een waypoint of je sterfplek). */
	public static void toPoint(ServerPlayer player, ServerLevel level, Vec3 point, @Nullable ItemStack icon, Component what, int seconds) {
		start(player, level, null, point.add(0, 0.5, 0), point.add(0, 1.0, 0), icon, what, 0, seconds);
	}

	private static void start(ServerPlayer player, ServerLevel level, @Nullable AABB box, Vec3 center, Vec3 top,
			@Nullable ItemStack icon, Component what, int count, int seconds) {
		clear(player);
		Beam b = new Beam();
		b.player = player;
		b.level = level;
		b.box = box;
		b.center = center;
		b.top = top;
		b.anchor = BlockPos.containing(center);
		b.what = what;
		b.count = count;
		b.ticksLeft = (seconds > 0 ? seconds : Libreria.GUIDE_SECONDS.get(player)) * 20;

		// --- bij het doel ---
		b.site = new ElementHolder();
		double up = top.y - center.y;
		if (box != null) b.site.addElement(outline(box, center, BOX_COLOR));

		BlockDisplayElement column = new BlockDisplayElement(Ui.block("light_blue_stained_glass"));
		column.setOffset(new Vec3(0, up, 0));
		column.setTranslation(new Vector3f(-0.1f, 0, -0.1f));
		column.setScale(new Vector3f(0.2f, 48f, 0.2f));
		column.setGlowing(true);
		column.setGlowColorOverride(LINE_COLOR);
		column.setBrightness(new Brightness(15, 15));
		column.setViewRange(8f);
		column.setDisplaySize(1f, 50f);
		b.site.addElement(column);

		if (icon != null && !icon.isEmpty()) {
			ItemDisplayElement item = new ItemDisplayElement(icon.copyWithCount(1));
			item.setOffset(new Vec3(0, up + 0.75, 0));
			item.setScale(new Vector3f(0.6f, 0.6f, 0.6f));
			item.setBillboardMode(Display.BillboardConstraints.CENTER);
			item.setBrightness(new Brightness(15, 15));
			item.setViewRange(8f);
			b.site.addElement(item);
		}

		b.label = new TextDisplayElement(labelText(b));
		b.label.setOffset(new Vec3(0, up + 1.35, 0));
		b.label.setBillboardMode(Display.BillboardConstraints.CENTER);
		b.label.setSeeThrough(true);
		b.label.setViewRange(8f);
		b.site.addElement(b.label);

		Vec3 c = center;
		new ManualAttachment(b.site, level, () -> c);
		b.site.startWatching(player);

		// --- de lijn, die met de speler meebeweegt ---
		b.follow = new ElementHolder();
		b.line = new BlockDisplayElement(Ui.block("light_blue_stained_glass"));
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
		Ui.sound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4f, 1.6f);
	}

	/** Korte gekleurde omlijning (bv. bevestiging van noIndex of opbergen). */
	public static void flash(ServerPlayer player, ServerLevel level, AABB box, int color, int ticks) {
		Vec3 center = box.getCenter();
		ElementHolder holder = new ElementHolder();
		holder.addElement(outline(box, center, color));
		new ManualAttachment(holder, level, () -> center);
		holder.startWatching(player);
		FLASHES.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(new Flash(holder, new int[]{ticks}));
	}

	private static BlockDisplayElement outline(AABB box, Vec3 center, int color) {
		BlockDisplayElement e = new BlockDisplayElement(Ui.block("white_stained_glass"));
		e.setOffset(new Vec3(box.minX - center.x, box.minY - center.y, box.minZ - center.z));
		e.setTranslation(new Vector3f(-0.02f, -0.02f, -0.02f));
		e.setScale(new Vector3f((float) box.getXsize() + 0.04f, (float) box.getYsize() + 0.04f, (float) box.getZsize() + 0.04f));
		e.setGlowing(true);
		e.setGlowColorOverride(color);
		e.setBrightness(new Brightness(15, 15));
		e.setViewRange(8f);
		// Culling box as big as the outline (a chunk border is 16 wide), so it doesn't vanish when a corner is off-screen
		e.setDisplaySize((float) Math.max(box.getXsize(), box.getZsize()) * 2 + 2, (float) box.getYsize() + 2);
		return e;
	}

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
		Vector3f centerOffset = rot.transform(new Vector3f(-LINE_WIDTH / 2, -LINE_WIDTH / 2, 0));
		b.line.setTranslation(origin.add(centerOffset));
		b.line.setLeftRotation(rot);
		b.line.setScale(new Vector3f(LINE_WIDTH, LINE_WIDTH, length));
		b.line.startInterpolationIfDirty();
	}

	private static Component labelText(Beam b) {
		int dist = (int) Math.round(b.player.position().distanceTo(b.center));
		MutableComponent text = Component.empty();
		if (b.count > 0) text.append(Component.literal(b.count + "× ").withStyle(ChatFormatting.GREEN));
		text.append(b.what.copy().withStyle(ChatFormatting.WHITE));
		text.append(Component.literal("  " + dist + " m").withStyle(ChatFormatting.YELLOW));
		return text;
	}

	/** Pijl t.o.v. de kijkrichting, met ▲/▼ als het doel hoger/lager ligt. */
	public static String arrow(ServerPlayer player, Vec3 target) {
		Vec3 d = target.subtract(player.position());
		double yawTo = Math.toDegrees(Math.atan2(-d.x, d.z));
		double rel = Mth.wrapDegrees(yawTo - player.getYRot());
		String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
		String a = arrows[(int) Math.floorMod(Math.round(rel / 45.0), 8)];
		if (d.y > 3) a += " ▲";
		else if (d.y < -3) a += " ▼";
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
					b.label.setText(labelText(b));
					Component bar = Component.literal(arrow(p, b.center) + " ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
							.append(b.what.copy().withStyle(ChatFormatting.WHITE))
							.append(Component.literal("  " + Math.round(dist) + " m").withStyle(ChatFormatting.YELLOW))
							.append(Component.literal("  (" + b.anchor.getX() + " " + b.anchor.getY() + " " + b.anchor.getZ() + ")").withStyle(ChatFormatting.GRAY));
					Hud.show(p, "guide", bar, Hud.PRIO_GUIDE, 30);
				}
				b.site.tick();
				b.follow.tick();
				if (b.box == null) {
					// Punt: aangekomen als je er (bijna) staat
					if (dist < 2.5) arrive(p);
				} else if (dist < 2.2) {
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

	/** Roep aan als de speler een blok gebruikt (bv. een kist opent): is dat het doel, dan is hij er. */
	public static void arrivedAt(ServerPlayer player, ServerLevel level, BlockPos pos) {
		Beam b = ACTIVE.get(player.getUUID());
		if (b != null && b.level == level && b.box != null && b.box.inflate(0.01).contains(Vec3.atCenterOf(pos))) {
			arrive(player);
		}
	}

	private static void arrive(ServerPlayer player) {
		clear(player);
		Hud.flash(player, Component.literal("✔ Arrived").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
		Ui.sound(player, SoundEvents.NOTE_BLOCK_CHIME, 0.5f, 1.8f);
	}

	/** The beam's target as a compass marker (same world only), or null. */
	public static @Nullable nl.libreria.core.gui.Compass.Marker marker(ServerPlayer player) {
		Beam b = ACTIVE.get(player.getUUID());
		if (b == null || b.level != player.level()) return null;
		return new nl.libreria.core.gui.Compass.Marker(b.center, "target", 0x55FFFF, b.what.getString());
	}

	public static boolean has(ServerPlayer player) {
		return ACTIVE.containsKey(player.getUUID());
	}

	public static void clear(ServerPlayer player) {
		Beam b = ACTIVE.remove(player.getUUID());
		if (b != null) {
			b.site.destroy();
			b.follow.destroy();
			Hud.hide(player, "guide");
		}
	}

	static void clearFlashes(UUID id) {
		ArrayList<Flash> flashes = FLASHES.remove(id);
		if (flashes != null) flashes.forEach(f -> f.holder().destroy());
	}

	static void clearAll() {
		for (Beam b : new ArrayList<>(ACTIVE.values())) clear(b.player);
		for (ArrayList<Flash> list : FLASHES.values()) list.forEach(f -> f.holder().destroy());
		FLASHES.clear();
	}
}
