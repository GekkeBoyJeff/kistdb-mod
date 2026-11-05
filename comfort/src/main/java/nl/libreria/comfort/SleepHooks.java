package nl.libreria.comfort;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/** What the mixins ask (they live in another package, Sleep itself stays package-private). */
public final class SleepHooks {
	private SleepHooks() {
	}

	public static boolean handlesNight(ServerLevel level) {
		return Sleep.handlesNight(level);
	}

	public static boolean keepAsleep(Player p) {
		return Sleep.keepAsleep(p);
	}
}
