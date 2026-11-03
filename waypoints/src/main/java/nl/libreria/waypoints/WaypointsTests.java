package nl.libreria.waypoints;

import net.fabricmc.fabric.api.entity.FakePlayer;
import nl.libreria.core.Guide;
import nl.libreria.core.SelfTests;

/** Zelftests van WaypointsMod (alleen in de CI). */
final class WaypointsTests {
	private WaypointsTests() {
	}

	static void register() {
		SelfTests.register("waypoints.list", level -> {
			FakePlayer p = FakePlayer.get(level);
			p.setPos(100.5, 80, 100.5);
			WaypointsMod.STORE.get(p).points.clear();
			SelfTests.check(WaypointsMod.add(p, "Home"), "set waypoint");
			SelfTests.check(WaypointsMod.find(p, "home") != null, "lookup is case-insensitive");
			SelfTests.check(WaypointsMod.STORE.get(p).points.size() == 1, "1 waypoint");
			SelfTests.check(WaypointsMod.add(p, "Home"), "same name = update");
			SelfTests.check(WaypointsMod.STORE.get(p).points.size() == 1, "still 1 waypoint");
			SelfTests.check(WaypointsMod.go(p, "Home") && Guide.has(p), "beam to the waypoint");
			Guide.clear(p);
			SelfTests.check(WaypointsMod.remove(p, "HOME"), "remove");
			SelfTests.check(WaypointsMod.STORE.get(p).points.isEmpty(), "empty");
			SelfTests.check(!WaypointsMod.add(p, "   "), "empty name rejected");
		});
	}
}
