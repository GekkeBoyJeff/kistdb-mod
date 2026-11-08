package nl.libreria.glint;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.ServerSetting;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Uncommon, rare and epic items lying on the ground give off a soft glow in the colour of their rarity (yellow, light
 * blue, purple), and now and then a mote of light rises from them: a good drop is never missed. A glowing sprite from
 * the pack follows the item; nothing about the item itself changes.
 */
final class DropGlow {
	static final ServerSetting.Toggle ON = GlintMod.MODULE.serverToggle("drop_glow", "Rare items on the ground glow", Items.GLOWSTONE_DUST,
			true, "Uncommon, rare and epic items on the", "ground give off a soft glow in their", "rarity's colour.");
	private static final Map<Rarity, Integer> COLOURS = Map.of(Rarity.UNCOMMON, 0xFFE066, Rarity.RARE, 0x6FE3FF, Rarity.EPIC, 0xD07BFF);

	/** The glow of each item that has one, and the rarity it glows for. */
	private record Glow(ElementHolder holder, Rarity rarity) {
	}

	private static final Map<ItemEntity, Glow> GLOWS = new HashMap<>();

	private DropGlow() {
	}

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item) apply(item);
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item) drop(item);
		});
		ServerTickEvents.END_SERVER_TICK.register(DropGlow::tick);
	}

	private static boolean on() {
		return GlintMod.MODULE.on() && ON.get();
	}

	/** The rarity an item on the ground glows for (as its name is coloured: enchanted is a step rarer), or null. */
	static @Nullable Rarity of(ItemEntity item) {
		var stack = item.getItem();
		if (stack.isEmpty()) return null;
		Rarity r = stack.getOrDefault(net.minecraft.core.component.DataComponents.RARITY, Rarity.COMMON);
		if (stack.isEnchanted()) r = r == Rarity.EPIC || r == Rarity.RARE ? Rarity.EPIC : Rarity.RARE;
		return r == Rarity.COMMON ? null : r;
	}

	static boolean glows(ItemEntity item) {
		return GLOWS.containsKey(item);
	}

	/** Gives the item its glow, changes it, or takes it away, as its stack and the switch say. */
	static void apply(ItemEntity item) {
		Rarity r = on() && item.isAlive() ? of(item) : null;
		Glow now = GLOWS.get(item);
		if (now != null && now.rarity() == r) return;
		drop(item);
		if (r == null) return;
		ElementHolder holder = new ElementHolder();
		// A soft haze round the item (not a disc: the item stays clear in the middle of it)
		ItemDisplayElement glow = new ItemDisplayElement(Fx.sprite("haze", COLOURS.get(r)));
		glow.setItemDisplayContext(ItemDisplayContext.FIXED);
		glow.setBillboardMode(Display.BillboardConstraints.CENTER);
		glow.setBrightness(new Brightness(15, 15));
		glow.setScale(new Vector3f(r == Rarity.EPIC ? 1.5f : r == Rarity.RARE ? 1.3f : 1.1f));
		glow.setOffset(new Vec3(0, 0.42, 0));
		glow.setViewRange(0.5f);
		holder.addElement(glow);
		EntityAttachment.ofTicking(holder, item);
		GLOWS.put(item, new Glow(holder, r));
	}

	private static void drop(ItemEntity item) {
		Glow g = GLOWS.remove(item);
		if (g != null) g.holder().destroy();
	}

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		// Gone items, and stacks that changed (merged) or the switch: the items around players looked at again
		if (t % 40 == 17) {
			GLOWS.keySet().removeIf(item -> {
				if (item.isAlive()) return false;
				GLOWS.get(item).holder().destroy();
				return true;
			});
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				for (ItemEntity item : p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(64))) apply(item);
			}
		}
		// Now and then a mote of light rises from each glowing item someone is near
		if (t % 12 != 5 || GLOWS.isEmpty()) return;
		for (var e : new ArrayList<>(GLOWS.entrySet())) {
			ItemEntity item = e.getKey();
			if (!(item.level() instanceof ServerLevel level) || item.getRandom().nextInt(3) != 0) continue;
			List<ServerPlayer> near = new ArrayList<>();
			for (ServerPlayer p : level.players()) if (p.distanceToSqr(item) < 24 * 24) near.add(p);
			if (near.isEmpty()) continue;
			double dx = (item.getRandom().nextDouble() - 0.5) * 0.35, dz = (item.getRandom().nextDouble() - 0.5) * 0.35;
			int rgb = COLOURS.get(e.getValue().rarity());
			Fx.emit(level, item.position(), near, List.of(Fx.line("mote", rgb, new Vec3(dx, 0.25, dz), new Vec3(dx * 1.4, 1.0, dz * 1.4),
					0.16f, 0.02f, 0, 0, 34)));
		}
	}
}
