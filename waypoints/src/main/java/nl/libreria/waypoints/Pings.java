package nl.libreria.waypoints;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Domain;
import nl.libreria.core.Guide;
import nl.libreria.core.Hotkeys;
import nl.libreria.core.Notify;
import nl.libreria.core.Say;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * Pings: look at a spot (or an animal, a mob, a player) and press sneak + Q, or sneak + middle-click within reach.
 * Everyone in the same world within {@link #SEE} blocks sees it on the spot for {@link #SECONDS} seconds ({@link PingMarks},
 * in the look the server picked), gets a card with who pinged what and how far, and the way there in the action bar
 * (unless they're already being shown the way somewhere: then a chat line offers it). One ping per player; a new one
 * takes the old one's place.
 */
final class Pings {
	static final int SECONDS = 30;
	/** How far you can ping (blocks). */
	static final double REACH = 160;
	/** Who sees a ping: everyone in the same world this close. */
	static final double SEE = 512;
	private static final long COOLDOWN_MS = 1200;
	static final int COLOR = 0xFFD966;

	record Ping(UUID owner, String ownerName, ServerLevel level, Vec3 at, Component what, long until) {
		int secondsLeft() {
			return (int) Math.max(1, (this.until - System.currentTimeMillis() + 999) / 1000);
		}
	}

	static final Map<UUID, Ping> ACTIVE = new LinkedHashMap<>();
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private Pings() {
	}

	static void init() {
		// sneak + Q (the drop key, whatever the player put it on): at any distance; the drop doesn't happen
		Hotkeys.register((player, key, sneaking) -> {
			if (!WaypointsMod.MODULE.on() || !sneaking || key == Hotkeys.Key.SWAP_HANDS) return false;
			int mode = WaypointsMod.PING_KEY.get(player);
			if (mode != 0 && mode != 1) return false;
			ping(player);
			// the client already took the item out of the hand: give it its slot back
			player.containerMenu.sendAllDataToRemote();
			return true;
		});
		// sneak + middle-click (pick block) on a block within reach
		Hotkeys.registerPick((player, pos, sneaking) -> {
			if (!sneaking) return false;
			int mode = WaypointsMod.PING_KEY.get(player);
			if (mode != 0 && mode != 2) return false;
			ServerLevel level = (ServerLevel) player.level();
			// the side you look at (pick block only gives the block)
			BlockHitResult hit = level.clip(new ClipContext(player.getEyePosition(), Vec3.atCenterOf(pos), ClipContext.Block.OUTLINE,
					ClipContext.Fluid.NONE, player));
			Direction face = hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) ? hit.getDirection() : null;
			pingAt(player, level, spotOn(level, pos, face), blockName(level, pos));
			return true;
		});
	}

	/** Ping what the player looks at. False when there's nothing there (the sky, or too far). */
	static boolean ping(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getViewVector(1f);
		Vec3 end = eye.add(look.scale(REACH));
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		double max = block.getType() == HitResult.Type.MISS ? REACH : block.getLocation().distanceTo(eye);
		// an animal, a mob or a player in front of the block
		Entity hitEntity = null;
		double best = max;
		AABB box = new AABB(eye, eye.add(look.scale(max))).inflate(1.5);
		for (Entity e : level.getEntities(p, box, e -> !e.isSpectator() && !e.isInvisible() && e.isPickable())) {
			Optional<Vec3> hit = e.getBoundingBox().inflate(0.3).clip(eye, eye.add(look.scale(max)));
			if (hit.isEmpty()) continue;
			double d = hit.get().distanceTo(eye);
			if (d < best) {
				best = d;
				hitEntity = e;
			}
		}
		if (hitEntity != null) return pingAt(p, level, hitEntity.position(), hitEntity.getDisplayName().copy());
		if (block.getType() == HitResult.Type.MISS) {
			Notify.info(p, Items.SPYGLASS, "Nothing to ping there", "Look at a block or a creature (up to " + (int) REACH + " m).");
			return false;
		}
		BlockPos pos = block.getBlockPos();
		return pingAt(p, level, spotOn(level, pos, block.getDirection()), blockName(level, pos));
	}

	/**
	 * Where a ping on this block stands: on top of it; or, when something is on top (a wall, a tree trunk), in front of
	 * the side you looked at, down on the ground there (a few blocks at most).
	 */
	static Vec3 spotOn(ServerLevel level, BlockPos pos, @Nullable Direction face) {
		BlockPos above = pos.above();
		if (face == null || face == Direction.UP || free(level, above)) return Vec3.atBottomCenterOf(above);
		BlockPos at = pos.relative(face);
		for (int i = 0; i < 4 && free(level, at.below()) && at.getY() > level.getMinY(); i++) at = at.below();
		return Vec3.atBottomCenterOf(at);
	}

	private static boolean free(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	private static Component blockName(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getBlock().getName();
	}

	/** Places the ping and shows it to everyone near. */
	static boolean pingAt(ServerPlayer p, ServerLevel level, Vec3 at, Component what) {
		long now = System.currentTimeMillis();
		Long last = LAST.get(p.getUUID());
		if (last != null && now - last < COOLDOWN_MS) return false;
		LAST.put(p.getUUID(), now);
		Ping ping = new Ping(p.getUUID(), p.getName().getString(), level, at, what, now + SECONDS * 1000L);
		ACTIVE.put(p.getUUID(), ping);
		// your own: on the spot, and the arrow there when it's not right in front of you
		PingMarks.show(p, ping);
		if (p.position().distanceTo(at) > 6) Guide.follow(p, level, at, Ui.text("Your ping", COLOR), SECONDS);
		Ui.sound(p, SoundEvents.NOTE_BLOCK_BELL, 0.5f, 1.8f);
		int seen = 0;
		for (ServerPlayer viewer : level.players()) {
			if (viewer == p || viewer.position().distanceTo(at) > SEE || !WaypointsMod.SEE_PINGS.get(viewer)) continue;
			seen++;
			PingMarks.show(viewer, ping);
			showTo(viewer, ping, true);
		}
		nl.libreria.core.Tips.once(p, WaypointsMod.TIP_PING);
		if (seen > 0) nl.libreria.core.Hud.flash(p, Ui.text("Pinged · " + seen + (seen == 1 ? " player sees it" : " players see it"), ChatFormatting.GRAY));
		return true;
	}

	/** Someone else's ping: a card, a soft bell, and the way there (or a chat line when you're being shown another way). */
	private static void showTo(ServerPlayer viewer, Ping ping, boolean fresh) {
		double d = viewer.position().distanceTo(ping.at());
		MutableComponent title = Ui.text(ping.ownerName() + " pinged ", ChatFormatting.WHITE).append(ping.what().copy().withStyle(s -> s.withColor(COLOR)));
		MutableComponent sub = Ui.text(Ui.meters(d) + " " + direction(viewer, ping.at()), ChatFormatting.GRAY);
		Notify.card(viewer, new ItemStack(Items.SPYGLASS), COLOR, title, sub);
		if (fresh) Ui.sound(viewer, SoundEvents.NOTE_BLOCK_BELL, 0.6f, 1.5f);
		if (!Guide.has(viewer)) {
			beam(viewer, ping);
		} else {
			Say.to(viewer, Domain.PLACES, title.copy().append(Ui.text(" · ", ChatFormatting.DARK_GRAY))
					.append(Say.run("Show the way", "/ping show " + ping.ownerName(), "A beam to " + ping.ownerName() + "'s ping")));
		}
	}

	private static void beam(ServerPlayer viewer, Ping ping) {
		Guide.follow(viewer, ping.level(), ping.at(), Ui.text(ping.ownerName() + "'s ping", COLOR), ping.secondsLeft());
		if (!PingMarks.has(viewer, ping.owner())) PingMarks.show(viewer, ping);
	}

	/** {@code /ping show <player>}: the way to their ping. */
	static boolean show(ServerPlayer viewer, UUID owner) {
		Ping ping = ACTIVE.get(owner);
		if (ping == null || ping.until() < System.currentTimeMillis()) {
			Notify.info(viewer, Items.SPYGLASS, "That ping is gone", "Pings last " + SECONDS + " seconds.");
			return false;
		}
		if (ping.level() != viewer.level()) {
			Notify.warn(viewer, Items.SPYGLASS, "In another world", "That ping is in the " + WaypointsMod.dimName(WaypointsMod.dimOf(ping.level())) + ".");
			return false;
		}
		beam(viewer, ping);
		return true;
	}

	static void clear(UUID owner) {
		ACTIVE.remove(owner);
		PingMarks.removeOwner(owner);
	}

	/** For the screenshots: a stand-in player may ping again right away. */
	static void forgetCooldown(UUID owner) {
		LAST.remove(owner);
	}

	static void tick(MinecraftServer server) {
		if (!WaypointsMod.MODULE.on()) {
			PingMarks.clearAll();
			return;
		}
		PingMarks.tick(server);
		if (server.getTickCount() % 20 != 7 || ACTIVE.isEmpty()) return;
		long now = System.currentTimeMillis();
		ACTIVE.values().removeIf(p -> p.until() < now);
		// who came near (or joined) after the ping was placed sees it too, without a card; who turned pings off doesn't
		for (Ping ping : ACTIVE.values()) {
			for (ServerPlayer viewer : ping.level().players()) {
				boolean own = viewer.getUUID().equals(ping.owner());
				boolean sees = own || WaypointsMod.SEE_PINGS.get(viewer);
				boolean near = viewer.position().distanceTo(ping.at()) <= SEE;
				boolean has = PingMarks.has(viewer, ping.owner());
				if (sees && near && !has) PingMarks.show(viewer, ping);
				else if (!sees && has) PingMarks.remove(viewer.getUUID(), ping.owner());
			}
		}
	}

	/** Active pings in this player's world, for the land map and compass. */
	static List<nl.libreria.core.gui.Markers.Marker> markers(ServerPlayer p) {
		List<nl.libreria.core.gui.Markers.Marker> out = new ArrayList<>();
		long now = System.currentTimeMillis();
		for (Ping ping : ACTIVE.values()) {
			if (ping.until() < now || ping.level() != p.level()) continue;
			if (!ping.owner().equals(p.getUUID()) && !WaypointsMod.SEE_PINGS.get(p)) continue;
			out.add(new nl.libreria.core.gui.Markers.Marker(ping.at(), "diamond", COLOR, ping.ownerName() + "'s ping"));
		}
		return out;
	}

	/** "north-east" from the viewer to a spot (north is -z). */
	static String direction(ServerPlayer viewer, Vec3 to) {
		Vec3 d = to.subtract(viewer.position());
		if (d.horizontalDistanceSqr() < 4) return d.y > 0 ? "above you" : "below you";
		double angle = Math.toDegrees(Math.atan2(d.x, -d.z));
		String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
		return names[(int) Math.floorMod(Math.round(angle / 45.0), 8)];
	}
}
