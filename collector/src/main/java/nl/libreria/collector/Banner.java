package nl.libreria.collector;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.TopCard;

/**
 * When someone earns an achievement, the whole server sees it: a banner at the top of everyone's screen (the
 * achievement's icon, name and rarity, who earned it and what for) and fireworks. Everyone hears them; players near
 * the one who earned it also see real rockets go up and burst in the rarity's colours. The rockets exist only on the
 * players' screens, so they can't hurt anyone. Banners queue up, one at a time.
 */
final class Banner {
	private static final String CHANNEL = "collector.banner";
	/** Players this close to the one who earned it see the rockets (and hear their own bursts). */
	private static final double SEE = 96;
	private static final int MAX_QUEUE = 8;

	private record Pending(UUID who, Component name, Achievement achievement) {
	}

	private record Later(int at, Runnable run) {
	}

	private static final ArrayDeque<Pending> QUEUE = new ArrayDeque<>();
	private static final List<Later> LATER = new ArrayList<>();
	private static int busyUntil, now;

	private Banner() {
	}

	static void add(ServerPlayer p, Achievement a) {
		if (QUEUE.size() >= MAX_QUEUE) return;
		QUEUE.add(new Pending(p.getUUID(), nl.libreria.core.Nameplate.displayName(p).copy(), a));
	}

	static void tick(MinecraftServer server) {
		now = server.getTickCount();
		List<Later> due = new ArrayList<>();
		for (Iterator<Later> it = LATER.iterator(); it.hasNext(); ) {
			Later l = it.next();
			if (l.at() <= now) {
				due.add(l);
				it.remove();
			}
		}
		due.forEach(l -> l.run().run());
		if (now < busyUntil || QUEUE.isEmpty()) return;
		Pending next = QUEUE.poll();
		Rarity r = next.achievement().rarity();
		int ticks = 70 + 15 * r.ordinal();
		busyUntil = now + ticks + 8;
		ServerPlayer who = server.getPlayerList().getPlayer(next.who());
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!CollectorMod.BIG.get(p)) continue;
			TopCard.show(p, CHANNEL, card(next, p.getUUID().equals(next.who())), 50, ticks);
			boolean seesRockets = who != null && p.level() == who.level() && p.distanceTo(who) < SEE;
			sounds(p, r, seesRockets);
		}
		if (who != null) rockets(who, r);
	}

	private static TopCard.Card card(Pending pending, boolean self) {
		Achievement a = pending.achievement();
		Rarity r = a.rarity();
		Integer rgb = TextColor.fromLegacyFormat(r.color) == null ? null : TextColor.fromLegacyFormat(r.color).getValue();
		MutableComponent name = Component.literal(a.name()).withStyle(r.color, ChatFormatting.BOLD);
		MutableComponent subtitle = Component.empty()
				.append(self ? Component.literal("You").withStyle(ChatFormatting.WHITE) : pending.name().copy().withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" earned it  ·  ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(r.label).withStyle(r.color))
				.append(Component.literal("  ·  +" + r.points + (r.points == 1 ? " point" : " points")).withStyle(ChatFormatting.GRAY));
		List<TopCard.Row> rows = List.of(TopCard.Row.text("trophy", "", Component.literal(a.description()).withStyle(ChatFormatting.GRAY)));
		return new TopCard.Card(new ItemStack(a.icon()), rgb == null ? -1 : rgb, name, subtitle, rows);
	}

	/** Everyone hears it go up and burst; who sees the rockets also hears their bursts, so only the launch here. */
	private static void sounds(ServerPlayer p, Rarity r, boolean seesRockets) {
		Ui.sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.7f, 1.0f);
		if (seesRockets) return;
		int bursts = bursts(r);
		for (int i = 0; i < bursts; i++) {
			int at = 18 + 7 * i;
			SoundEvent blast = r.ordinal() >= Rarity.RARE.ordinal() ? SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR : SoundEvents.FIREWORK_ROCKET_BLAST_FAR;
			later(at, () -> Ui.sound(p, blast, 0.9f, 0.95f + 0.1f * (float) Math.random()));
			if (r.ordinal() >= Rarity.RARE.ordinal()) later(at + 8, () -> Ui.sound(p, SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, 0.7f, 1f));
		}
	}

	private static int bursts(Rarity r) {
		return switch (r) {
			case COMMON, UNCOMMON -> 1;
			case RARE -> 2;
			case EPIC -> 3;
			case LEGENDARY -> 4;
			case MYTHIC -> 6;
		};
	}

	/** Rockets that rise from around the player and burst in the rarity's colours. */
	private static void rockets(ServerPlayer who, Rarity r) {
		ServerLevel level = who.level();
		RandomSource random = level.getRandom();
		List<ServerPlayer> viewers = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.distanceTo(who) < SEE) viewers.add(p);
		}
		if (viewers.isEmpty()) return;
		for (int i = 0; i < bursts(r); i++) {
			double x = who.getX() + (random.nextDouble() - 0.5) * 5;
			double z = who.getZ() + (random.nextDouble() - 0.5) * 5;
			int delay = 7 * i;
			later(delay, () -> rocket(level, viewers, x, who.getY() + 0.5, z, explosion(r, random), 20 + random.nextInt(6)));
		}
	}

	private static FireworkExplosion explosion(Rarity r, RandomSource random) {
		int[] colors = switch (r) {
			case COMMON -> new int[] {0xFFFFFF, 0xC8C8C8};
			case UNCOMMON -> new int[] {0x55FF55, 0xB8FF8A};
			case RARE -> new int[] {0x55FFFF, 0x3FA7FF};
			case EPIC -> new int[] {0xFF55FF, 0xB66CFF};
			case LEGENDARY -> new int[] {0xFFAA00, 0xFFE15A};
			case MYTHIC -> new int[] {0xFF5555, 0xFFAA00, 0xFFFFFF};
		};
		FireworkExplosion.Shape shape = switch (r) {
			case COMMON -> FireworkExplosion.Shape.SMALL_BALL;
			case UNCOMMON -> FireworkExplosion.Shape.BURST;
			case RARE, EPIC -> FireworkExplosion.Shape.LARGE_BALL;
			case LEGENDARY, MYTHIC -> random.nextBoolean() ? FireworkExplosion.Shape.STAR : FireworkExplosion.Shape.LARGE_BALL;
		};
		boolean fancy = r.ordinal() >= Rarity.RARE.ordinal();
		return new FireworkExplosion(shape, IntList.of(colors), IntList.of(0xFFFFFF), fancy, fancy);
	}

	/**
	 * A rocket only these players' screens know about: it flies up by itself on their side, then bursts when told
	 * (entity event 17) and is gone. The server never adds it to the world.
	 */
	private static void rocket(ServerLevel level, List<ServerPlayer> viewers, double x, double y, double z, FireworkExplosion explosion, int fuse) {
		ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
		stack.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(explosion)));
		FireworkRocketEntity rocket = new FireworkRocketEntity(level, x, y, z, stack);
		var add = new ClientboundAddEntityPacket(rocket.getId(), rocket.getUUID(), x, y, z, 0f, 0f, EntityType.FIREWORK_ROCKET, 0,
				rocket.getDeltaMovement(), 0);
		var values = rocket.getEntityData().getNonDefaultValues();
		for (ServerPlayer p : viewers) {
			if (p.hasDisconnected()) continue;
			p.connection.send(add);
			if (values != null) p.connection.send(new ClientboundSetEntityDataPacket(rocket.getId(), values));
		}
		later(fuse, () -> {
			for (ServerPlayer p : viewers) {
				if (p.hasDisconnected()) continue;
				p.connection.send(new ClientboundEntityEventPacket(rocket, (byte) 17));
				p.connection.send(new ClientboundRemoveEntitiesPacket(rocket.getId()));
			}
		});
	}

	private static void later(int ticks, Runnable run) {
		LATER.add(new Later(now + ticks, run));
	}

	static void clear() {
		QUEUE.clear();
		LATER.clear();
		busyUntil = 0;
	}
}
