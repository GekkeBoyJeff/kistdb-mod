package nl.libreria.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * The animals a player leads on a lead come along when they teleport (a home, a waystone…): only animals that don't
 * attack you (no monsters, no people). Taken right after the player, so the lead holds.
 */
public final class Leashed {
	/** How far a led animal may be from you and still come along (a lead reaches about this far). */
	private static final double REACH = 12;

	private Leashed() {
	}

	/** The animals on this player's lead now. */
	public static List<Mob> along(ServerPlayer p) {
		List<Mob> out = new ArrayList<>();
		if (p.isSpectator()) return out;
		for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(REACH), m -> m.isAlive() && m.getLeashHolder() == p)) {
			if (comes(m)) out.add(m);
		}
		return out;
	}

	/** An animal that comes along: no monster (a hoglin is both), nothing that attacks you. */
	public static boolean comes(Entity e) {
		return e instanceof Animal && !(e instanceof Enemy);
	}

	/**
	 * After the player went (they're at their new place now): the animals that were on their lead go there too, and stay
	 * on the lead. From one world to another as well.
	 */
	public static void bring(ServerPlayer p, List<Mob> animals) {
		if (animals.isEmpty()) return;
		ServerLevel to = p.level();
		Vec3 at = p.position();
		for (Mob m : animals) {
			if (!m.isAlive()) continue;
			try {
				if (m.level() == to) {
					m.teleportTo(at.x, at.y, at.z);
					m.setDeltaMovement(Vec3.ZERO);
					if (m.getLeashHolder() != p) m.setLeashedTo(p, true);
					continue;
				}
				Entity moved = m.teleport(new TeleportTransition(to, at, Vec3.ZERO, m.getYRot(), m.getXRot(), Set.of(), TeleportTransition.DO_NOTHING));
				if (moved instanceof Leashable l && moved.isAlive()) l.setLeashedTo(p, true);
			} catch (RuntimeException e) {
				Libreria.LOG.warn("Couldn't take {} along with {}", m.getName().getString(), p.getName().getString(), e);
			}
		}
	}

	/** "your horse", "your 2 animals": for the price line and the messages. */
	public static String words(List<Mob> animals) {
		if (animals.size() == 1) return "your " + animals.getFirst().getType().getDescription().getString().toLowerCase(java.util.Locale.ROOT);
		return "your " + animals.size() + " animals";
	}
}
