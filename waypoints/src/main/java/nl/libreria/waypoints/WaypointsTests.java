package nl.libreria.waypoints;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Guide;
import nl.libreria.core.SelfTests;

/** Zelftests van pings en de sterfplek (alleen in de CI). */
final class WaypointsTests {
	private WaypointsTests() {
	}

	static void register() {
		SelfTests.register("waypoints.pings", level -> {
			FakePlayer a = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Pinger"));
			FakePlayer b = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Friend"));
			a.setPos(100.5, 80, 100.5);
			b.setPos(110.5, 80, 100.5);
			try {
				Vec3 spot = new Vec3(100.5, 80, 80.5);
				SelfTests.check(Pings.pingAt(a, level, spot, Component.literal("Oak Log")), "a ping");
				Pings.Ping ping = Pings.ACTIVE.get(a.getUUID());
				SelfTests.check(ping != null && ping.at().equals(spot) && "Pinger".equals(ping.ownerName()), "the ping is stored with who and where");
				SelfTests.check(!Pings.pingAt(a, level, spot.add(1, 0, 0), Component.literal("Stone")), "not twice in a moment");
				SelfTests.check(Pings.ACTIVE.get(a.getUUID()).at().equals(spot), "the first ping stays");
				SelfTests.check(Pings.show(b, a.getUUID()) && Guide.has(b), "someone else is shown the way to it");
				SelfTests.check(Pings.markers(b).stream().anyMatch(m -> m.name().equals("Pinger's ping")), "on the map for others");
				SelfTests.check("north".equals(Pings.direction(a, spot)), "the direction: north is -z (" + Pings.direction(a, spot) + ")");
				SelfTests.check("east".equals(Pings.direction(a, new Vec3(130.5, 80, 100.5))), "east is +x");
				Pings.clear(a.getUUID());
				SelfTests.check(!Pings.ACTIVE.containsKey(a.getUUID()) && !Pings.show(b, a.getUUID()), "cleared: gone for everyone");
				SelfTests.check(Pings.SECONDS == 30 && Pings.REACH >= 128, "30 seconds, far enough to ping a hill");
			} finally {
				Guide.clear(a);
				Guide.clear(b);
				Pings.clear(a.getUUID());
			}
		});
		SelfTests.register("waypoints.death", level -> {
			FakePlayer p = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Nobody"));
			SelfTests.check(WaypointsMod.deathPoint(p) == null && !WaypointsMod.goDeath(p), "no death point yet: nothing to show");
		});
	}
}
