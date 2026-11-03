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
			SelfTests.check(WaypointsMod.add(p, "Thuis"), "punt zetten");
			SelfTests.check(WaypointsMod.find(p, "thuis") != null, "zoeken is hoofdletter-ongevoelig");
			SelfTests.check(WaypointsMod.STORE.get(p).points.size() == 1, "1 punt");
			SelfTests.check(WaypointsMod.add(p, "Thuis"), "zelfde naam = bijwerken");
			SelfTests.check(WaypointsMod.STORE.get(p).points.size() == 1, "nog steeds 1 punt");
			SelfTests.check(WaypointsMod.go(p, "Thuis") && Guide.has(p), "straal naar het punt");
			Guide.clear(p);
			SelfTests.check(WaypointsMod.remove(p, "THUIS"), "verwijderen");
			SelfTests.check(WaypointsMod.STORE.get(p).points.isEmpty(), "leeg");
			SelfTests.check(!WaypointsMod.add(p, "   "), "lege naam geweigerd");
		});
	}
}
