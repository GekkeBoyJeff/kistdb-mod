package nl.libreria.teleports;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Hud;
import nl.libreria.core.Notify;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * Every teleport goes through here: the rules ops set (between worlds, stand still first, wait between teleports),
 * the place you leave becomes where /back goes, and you hear and see it. /back itself and /spawn live here too.
 */
public final class Travel {
	/** Why /back goes where it goes: you teleported away from it, or you died there. */
	public enum Why {
		TELEPORT, DEATH
	}

	/** A teleport that waits for you to stand still a moment. */
	private record Pending(ServerPlayer player, Vec3 from, int until, int total, Runnable go, Component what) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	/** When each player last teleported (ms), for the wait between teleports. */
	private static final Map<UUID, Long> LAST = new HashMap<>();
	private static final String HUD = "teleport";

	private Travel() {
	}

	/** A spot in a world. */
	record Target(ServerLevel level, Vec3 pos, float yaw, float pitch) {
	}

	/**
	 * Teleports {@code p} to {@code to} by the server's rules (it may wait for them to stand still first). False when it
	 * can't (another world, still waiting): they're told why.
	 */
	static boolean go(ServerPlayer p, Target to, Component what) {
		if (!TeleportsMod.MODULE.on()) return false;
		boolean op = Ui.isOp(p);
		if (to.level() != p.level() && !TeleportsMod.WORLDS.get() && !op) {
			Notify.warn(p, Items.OBSIDIAN, "Not between worlds", "Teleports stay in one world on this server.");
			return false;
		}
		long now = System.currentTimeMillis();
		int wait = TeleportsMod.COOLDOWN.get();
		Long last = LAST.get(p.getUUID());
		if (!op && wait > 0 && last != null && now - last < wait * 1000L) {
			long left = (wait * 1000L - (now - last) + 999) / 1000;
			Notify.info(p, Items.CLOCK, "Not yet", "You can teleport again in " + left + (left == 1 ? " second." : " seconds."));
			return false;
		}
		int warmup = TeleportsMod.WARMUP.get();
		if (warmup <= 0 || op) {
			now(p, to, what);
			return true;
		}
		int tick = p.level().getServer().getTickCount();
		PENDING.put(p.getUUID(), new Pending(p, p.position(), tick + warmup * 20, warmup * 20, () -> now(p, to, what), what));
		return true;
	}

	/** Teleports right now: /back remembers where you were. */
	static void now(ServerPlayer p, Target to, Component what) {
		PENDING.remove(p.getUUID());
		Hud.hide(p, HUD);
		rememberBack(p, Why.TELEPORT);
		ServerLevel from = p.level();
		Vec3 was = p.position();
		// The animals on your lead come along
		var animals = nl.libreria.core.Leashed.along(p);
		if (p.isPassenger()) p.stopRiding();
		p.teleportTo(to.level(), to.pos().x, to.pos().y, to.pos().z, Set.of(), to.yaw(), to.pitch(), false);
		p.fallDistance = 0;
		nl.libreria.core.Leashed.bring(p, animals);
		LAST.put(p.getUUID(), System.currentTimeMillis());
		puff(from, was);
		puff(to.level(), to.pos());
		to.level().playSound(null, to.pos().x, to.pos().y, to.pos().z, SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.2f);
		Hud.flash(p, Ui.text("↷ ", ChatFormatting.AQUA).append(what.copy().withStyle(ChatFormatting.WHITE)));
	}

	private static void puff(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 24, 0.3, 0.6, 0.3, 0.2);
	}

	/** Where the player is now becomes where /back goes. */
	public static void rememberBack(ServerPlayer p, Why why) {
		TeleportsMod.Data d = TeleportsMod.PLAYERS.get(p);
		TeleportsMod.Spot s = new TeleportsMod.Spot();
		s.dim = p.level().dimension().identifier().toString();
		s.x = p.getX();
		s.y = p.getY();
		s.z = p.getZ();
		s.yaw = p.getYRot();
		s.pitch = p.getXRot();
		s.death = why == Why.DEATH;
		s.at = System.currentTimeMillis();
		d.back = s;
		TeleportsMod.PLAYERS.markDirty(p);
	}

	/** /back: to where you were before your last teleport, or where you died. */
	static boolean back(ServerPlayer p) {
		if (!TeleportsMod.BACK.get()) {
			Notify.warn(p, Items.BARRIER, "No /back here", "Going back is turned off on this server.");
			return false;
		}
		TeleportsMod.Spot s = TeleportsMod.PLAYERS.get(p).back;
		ServerLevel level = s == null ? null : level(p.level().getServer(), s.dim);
		if (s == null || level == null) {
			Notify.info(p, Items.ENDER_PEARL, "Nowhere to go back to", "After a teleport or dying, /back takes you there.");
			return false;
		}
		return go(p, new Target(level, new Vec3(s.x, s.y, s.z), s.yaw, s.pitch), Ui.text(s.death ? "Where you died" : "Back where you were", ChatFormatting.WHITE));
	}

	/** /spawn: the world's spawn point. */
	static boolean spawn(ServerPlayer p) {
		if (!TeleportsMod.SPAWN.get()) {
			Notify.warn(p, Items.BARRIER, "No /spawn here", "Teleporting to spawn is turned off on this server.");
			return false;
		}
		return go(p, spawnPoint(p.level().getServer()), Ui.text("Spawn", ChatFormatting.WHITE));
	}

	/** The world's spawn point, on the first open spot there. */
	static Target spawnPoint(MinecraftServer server) {
		var data = server.overworld().getRespawnData();
		ServerLevel level = server.getLevel(data.dimension());
		if (level == null) level = server.overworld();
		BlockPos at = data.pos();
		for (int i = 0; i < 64 && !(open(level, at) && open(level, at.above())); i++) at = at.above();
		return new Target(level, Vec3.atBottomCenterOf(at), data.yaw(), 0);
	}

	private static boolean open(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	static @Nullable ServerLevel level(MinecraftServer server, String dim) {
		for (ServerLevel l : server.getAllLevels()) {
			if (l.dimension().identifier().toString().equals(dim)) return l;
		}
		return null;
	}

	/** Teleports waiting for you to stand still: a countdown, and moving or getting hurt calls it off. */
	static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) return;
		int now = server.getTickCount();
		for (Pending w : PENDING.values().toArray(Pending[]::new)) {
			ServerPlayer p = w.player();
			if (p.isRemoved() || p.hasDisconnected()) {
				PENDING.remove(p.getUUID());
				continue;
			}
			if (p.position().distanceToSqr(w.from()) > 0.04) {
				cancel(p, "You moved");
				continue;
			}
			if (now >= w.until()) {
				w.go().run();
				continue;
			}
			int left = (w.until() - now + 19) / 20;
			MutableComponent bar = Ui.text("↷ ", ChatFormatting.AQUA).append(Ui.text("Teleporting to ", ChatFormatting.GRAY))
					.append(w.what().copy().withStyle(ChatFormatting.WHITE)).append(Ui.text(" in " + left, ChatFormatting.YELLOW))
					.append(Ui.text(" · stand still", ChatFormatting.GRAY));
			Hud.show(p, HUD, bar, Hud.PRIO_WARNING, 10);
		}
	}

	/** Getting hurt while waiting to teleport calls it off. */
	static void hurt(ServerPlayer p) {
		if (PENDING.containsKey(p.getUUID())) cancel(p, "You got hurt");
	}

	private static void cancel(ServerPlayer p, String why) {
		PENDING.remove(p.getUUID());
		Hud.hide(p, HUD);
		Hud.flash(p, Ui.text("✖ " + why + ": no teleport", ChatFormatting.RED));
		Ui.sound(p, SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f, 0.8f);
	}

	static boolean waiting(ServerPlayer p) {
		return PENDING.containsKey(p.getUUID());
	}

	static void forget(UUID id) {
		PENDING.remove(id);
		LAST.remove(id);
	}
}
