package nl.handig.wegwijzer;

import net.fabricmc.fabric.api.entity.FakePlayer;
import nl.handig.kern.Guide;
import nl.handig.kern.SelfTests;

/** Zelftests van Wegwijzer (alleen in de CI). */
final class WegwijzerTests {
	private WegwijzerTests() {
	}

	static void register() {
		SelfTests.register("wegwijzer.punten", level -> {
			FakePlayer p = FakePlayer.get(level);
			p.setPos(100.5, 80, 100.5);
			Wegwijzer.STORE.get(p).points.clear();
			SelfTests.check(Wegwijzer.add(p, "Thuis"), "punt zetten");
			SelfTests.check(Wegwijzer.find(p, "thuis") != null, "zoeken is hoofdletter-ongevoelig");
			SelfTests.check(Wegwijzer.STORE.get(p).points.size() == 1, "1 punt");
			SelfTests.check(Wegwijzer.add(p, "Thuis"), "zelfde naam = bijwerken");
			SelfTests.check(Wegwijzer.STORE.get(p).points.size() == 1, "nog steeds 1 punt");
			SelfTests.check(Wegwijzer.go(p, "Thuis") && Guide.has(p), "straal naar het punt");
			Guide.clear(p);
			SelfTests.check(Wegwijzer.remove(p, "THUIS"), "verwijderen");
			SelfTests.check(Wegwijzer.STORE.get(p).points.isEmpty(), "leeg");
			SelfTests.check(!Wegwijzer.add(p, "   "), "lege naam geweigerd");
		});
	}
}
