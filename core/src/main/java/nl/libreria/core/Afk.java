package nl.libreria.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Who is away from the keyboard: nobody moved or looked around for a few minutes (admin setting). Shown as a grey
 * "AFK" after the name in the tab list, and counted in its header.
 */
public final class Afk {
	public static final ServerSetting.Number MINUTES = Libreria.MODULE.serverNumber("afk_minutes", "Away after", Items.CLOCK,
			5, 1, 30, 1, "minutes", "Players who don't move or look around", "for this long show as AFK in the tab list.");

	private record Seen(Vec3 pos, float yaw, float pitch, int since) {
	}

	private static final Map<UUID, Seen> SEEN = new HashMap<>();
	private static final java.util.Set<UUID> AWAY = new java.util.HashSet<>();

	private Afk() {
	}

	static void init() {
		// Loading the class registers the setting
	}

	public static boolean isAway(ServerPlayer p) {
		return AWAY.contains(p.getUUID());
	}

	public static int awayCount() {
		return AWAY.size();
	}

	/** From the core tick, once a second. */
	static void tick(MinecraftServer server, int now) {
		int limit = MINUTES.get() * 60 * 20;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Seen s = SEEN.get(p.getUUID());
			boolean moved = s == null || s.pos().distanceToSqr(p.position()) > 0.01 || Math.abs(s.yaw() - p.getYRot()) > 0.5
					|| Math.abs(s.pitch() - p.getXRot()) > 0.5;
			if (moved) SEEN.put(p.getUUID(), new Seen(p.position(), p.getYRot(), p.getXRot(), now));
			int since = moved ? now : s.since();
			boolean away = now - since >= limit;
			if (away != AWAY.contains(p.getUUID())) {
				if (away) AWAY.add(p.getUUID());
				else AWAY.remove(p.getUUID());
				server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, p));
			}
		}
	}

	static void forget(UUID id) {
		SEEN.remove(id);
		AWAY.remove(id);
	}
}
