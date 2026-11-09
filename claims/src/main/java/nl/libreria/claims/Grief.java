package nl.libreria.claims;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Hud;
import org.jetbrains.annotations.Nullable;

/**
 * Claimed land can't be griefed, by mobs or by anything else. Explosions (creepers, TNT, ghasts, withers, beds…) still go
 * off and still hurt monsters and players, but break no blocks in a claim and hurt nothing that belongs to it (animals,
 * pets, villagers, armour stands, mannequins, item frames, paintings). Fire doesn't burn claimed blocks or spread into a
 * claim, lava doesn't flow into it from outside (water may: only placing it needs permission), zombies don't break its doors, endermen don't take its blocks
 * and nothing tramples its farmland. Only a player who may build there (the owner, someone trusted) can still blow it
 * up with their own TNT. When an explosion is stopped near the owner, it counts for "Saved by My Claim".
 *
 * <p>The mixins in {@code nl.libreria.claims.mixin} call in here.
 */
public final class Grief {
	/** How near the owner has to be for a stopped explosion to count as theirs (and to hear about it). */
	private static final double NEAR = 64;
	/** At most one "your claim held" line per this long. */
	private static final long TOLD_MS = 30_000;
	private static final Map<UUID, Long> TOLD = new HashMap<>();

	private Grief() {
	}

	private static @Nullable ClaimsMod.Claim claim(ServerLevel level, BlockPos pos) {
		return ClaimsMod.claimAt(level, ChunkPos.containing(pos));
	}

	/** Is this spot in a claim (with claims turned on)? */
	public static boolean claimed(ServerLevel level, BlockPos pos) {
		return ClaimsMod.MODULE.on() && claim(level, pos) != null;
	}

	/** Can't this (a mob, a player, nothing) change the block here? Only players who may build there can. */
	public static boolean guarded(ServerLevel level, BlockPos pos, @Nullable Entity cause) {
		if (!claimed(level, pos)) return false;
		return !(cause instanceof ServerPlayer p && ClaimsMod.allowed(p, level, pos, ClaimsMod.Action.BUILD));
	}

	/** Is there a claim within {@code reach} blocks (sideways) of this spot? */
	public static boolean nearClaim(ServerLevel level, BlockPos pos, int reach) {
		if (!ClaimsMod.MODULE.on()) return false;
		for (int dx = -reach; dx <= reach; dx += reach) {
			for (int dz = -reach; dz <= reach; dz += reach) {
				if (claim(level, pos.offset(dx, 0, dz)) != null) return true;
			}
		}
		return false;
	}

	/** Lava flowing from {@code from} into {@code to}: not into a claim from outside it (or from someone else's). */
	public static boolean flowsIn(ServerLevel level, BlockPos from, BlockPos to) {
		if ((from.getX() >> 4) == (to.getX() >> 4) && (from.getZ() >> 4) == (to.getZ() >> 4)) return false;
		if (!ClaimsMod.MODULE.on()) return false;
		ClaimsMod.Claim into = claim(level, to);
		if (into == null) return false;
		ClaimsMod.Claim out = claim(level, from);
		return out == null || !out.owner.equals(into.owner);
	}

	/** An entity that belongs to the land (animals, pets, stands, frames…) sits out explosions in a claim. */
	public static boolean shelters(Entity e, Explosion explosion) {
		if (!(e.level() instanceof ServerLevel level) || !ClaimsMod.protectedEntity(e)) return false;
		if (!(e instanceof LivingEntity) && !(e instanceof BlockAttachedEntity)) return false;
		return guarded(level, e.blockPosition(), explosion.getIndirectSourceEntity());
	}

	/**
	 * The blocks an explosion is about to break or set fire to: those in a claim stay (unless whoever lit it may build
	 * there). Returns the list that may go.
	 */
	public static List<BlockPos> explosion(Explosion explosion, List<BlockPos> blocks) {
		if (blocks.isEmpty() || !ClaimsMod.MODULE.on()) return blocks;
		ServerLevel level = explosion.level();
		LivingEntity cause = explosion.getIndirectSourceEntity();
		List<BlockPos> keep = new ArrayList<>(blocks.size());
		Set<UUID> owners = new HashSet<>();
		for (BlockPos pos : blocks) {
			ClaimsMod.Claim cl = claim(level, pos);
			if (cl == null || cause instanceof ServerPlayer p && ClaimsMod.allowed(p, level, pos, ClaimsMod.Action.BUILD)) {
				keep.add(pos);
				continue;
			}
			if (!level.getBlockState(pos).isAir()) owners.add(cl.owner);
		}
		if (keep.size() == blocks.size()) return blocks;
		Explosion.BlockInteraction how = explosion.getBlockInteraction();
		if (how == Explosion.BlockInteraction.DESTROY || how == Explosion.BlockInteraction.DESTROY_WITH_DECAY) saved(level, explosion.center(), owners);
		return keep;
	}

	/** The owners near a stopped explosion: it counts (Saved by My Claim), and they hear their claim held. */
	private static void saved(ServerLevel level, Vec3 at, Set<UUID> owners) {
		for (UUID id : owners) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p == null || p.level() != level || p.position().distanceTo(at) > NEAR) continue;
			ClaimsMod.Data d = ClaimsMod.STORE.get();
			if (d.saved == null) d.saved = new HashMap<>();
			d.saved.merge(id, 1, Integer::sum);
			ClaimsMod.STORE.markDirty();
			long now = System.currentTimeMillis();
			Long told = TOLD.get(id);
			if (told == null || now - told > TOLD_MS) {
				TOLD.put(id, now);
				Hud.flash(p, Component.literal("Your claim held: the blast broke nothing").withStyle(ChatFormatting.GREEN));
			}
		}
	}

	/** How many explosions a player's claim stopped near them. */
	public static int saved(UUID id) {
		Map<UUID, Integer> m = ClaimsMod.STORE.get().saved;
		Integer n = m == null ? null : m.get(id);
		return n == null ? 0 : n;
	}

	static void forget(UUID id) {
		TOLD.remove(id);
	}
}
