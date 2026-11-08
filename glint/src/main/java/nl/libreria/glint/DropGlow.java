package nl.libreria.glint;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A thin beam of light rises from uncommon, rare and epic items lying on the ground, in the colour of their rarity
 * (yellow, light blue, purple; taller for rarer): a good drop is never missed.
 *
 * <p>Light on purpose: one still sprite per item, only once it lies still (not while it flies, floats or rides a water
 * stream or a hopper line), at most a few in one spot (a farm's pile gets a few beams, not hundreds), never moved or
 * redrawn; it goes with the item.
 */
final class DropGlow {
	static final ServerSetting.Toggle ON = GlintMod.MODULE.serverToggle("drop_glow", "Beams over rare drops", Items.GLOWSTONE_DUST,
			true, "A thin beam of light over uncommon,", "rare and epic items on the ground,", "in their rarity's colour.");
	private static final Map<Rarity, Integer> COLOURS = Map.of(Rarity.UNCOMMON, 0xFFE066, Rarity.RARE, 0x6FE3FF, Rarity.EPIC, 0xD07BFF);
	/** Beams within this many blocks of each other count as one spot. */
	private static final double SPOT = 3;
	/** Most beams in one spot. */
	private static final int PER_SPOT = 3;
	/** An item has to lie still this long (ticks) before it gets a beam. */
	private static final int SETTLED = 30;

	/** An item's beam, the rarity it shows and where the item was then. */
	private record Beam(ElementHolder holder, Rarity rarity, Vec3 at) {
	}

	private static final Map<ItemEntity, Beam> BEAMS = new HashMap<>();

	private DropGlow() {
	}

	static void init() {
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item) drop(item);
		});
		ServerTickEvents.END_SERVER_TICK.register(DropGlow::tick);
	}

	private static boolean on() {
		return GlintMod.MODULE.on() && ON.get();
	}

	/** The rarity an item on the ground shows (as its name is coloured: enchanted is a step rarer), or null. */
	static @Nullable Rarity of(ItemEntity item) {
		var stack = item.getItem();
		if (stack.isEmpty()) return null;
		Rarity r = stack.getOrDefault(net.minecraft.core.component.DataComponents.RARITY, Rarity.COMMON);
		if (stack.isEnchanted()) r = r == Rarity.EPIC || r == Rarity.RARE ? Rarity.EPIC : Rarity.RARE;
		return r == Rarity.COMMON ? null : r;
	}

	/** Lying still on the ground, out of the water, for a moment. */
	private static boolean settled(ItemEntity item) {
		return (item.onGround() || item.isNoGravity()) && !item.isInWater() && item.tickCount >= SETTLED
				&& item.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4;
	}

	static boolean shows(ItemEntity item) {
		return BEAMS.containsKey(item);
	}

	/** How many beams there already are around a spot. */
	private static int around(ItemEntity item) {
		int n = 0;
		for (var e : BEAMS.entrySet()) {
			if (e.getKey() != item && e.getKey().level() == item.level() && e.getValue().at().distanceToSqr(item.position()) < SPOT * SPOT) n++;
		}
		return n;
	}

	/** Gives the item its beam, changes it, or takes it away. */
	static void apply(ItemEntity item) {
		Rarity r = on() && item.isAlive() && settled(item) ? of(item) : null;
		Beam now = BEAMS.get(item);
		if (now != null && now.rarity() == r && now.at().distanceToSqr(item.position()) < 0.25) return;
		drop(item);
		if (r == null || around(item) >= PER_SPOT) return;
		float tall = r == Rarity.EPIC ? 3.2f : r == Rarity.RARE ? 2.4f : 1.6f;
		ElementHolder holder = new ElementHolder();
		ItemDisplayElement beam = new ItemDisplayElement(Fx.sprite("beam", COLOURS.get(r)));
		beam.setItemDisplayContext(ItemDisplayContext.FIXED);
		// Turns to face you round its own axis, stays upright; brightest at the item, fading upwards
		beam.setBillboardMode(Display.BillboardConstraints.VERTICAL);
		beam.setBrightness(new Brightness(15, 15));
		beam.setLeftRotation(new Quaternionf().rotateZ((float) Math.PI));
		beam.setScale(new Vector3f(r == Rarity.EPIC ? 0.22f : 0.17f, tall, 1f));
		beam.setTranslation(new Vector3f(0, tall / 2 + 0.1f, 0));
		beam.setDisplaySize(1, tall + 0.5f);
		beam.setViewRange(0.6f);
		holder.addElement(beam);
		EntityAttachment.of(holder, item);
		BEAMS.put(item, new Beam(holder, r, item.position()));
	}

	private static void drop(ItemEntity item) {
		Beam b = BEAMS.remove(item);
		if (b != null) b.holder().destroy();
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 17) return;
		BEAMS.keySet().removeIf(item -> {
			if (item.isAlive()) return false;
			BEAMS.get(item).holder().destroy();
			return true;
		});
		if (!on()) {
			for (Beam b : BEAMS.values()) b.holder().destroy();
			BEAMS.clear();
			return;
		}
		// The items around players: the ones that came to rest get a beam, the ones that moved or changed again
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			for (ItemEntity item : p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(48))) apply(item);
		}
	}
}
