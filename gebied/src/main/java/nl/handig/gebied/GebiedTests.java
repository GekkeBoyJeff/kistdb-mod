package nl.handig.gebied;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import nl.handig.kern.SelfTests;

/** Zelftests: claimen, budget en bescherming. */
final class GebiedTests {
	private GebiedTests() {
	}

	static void register() {
		SelfTests.register("gebied.claimen-en-beschermen", level -> {
			FakePlayer owner = FakePlayer.get(level);
			FakePlayer guest = FakePlayer.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Gast"));
			ChunkPos c = new ChunkPos(30, 30);
			Gebied.STORE.get().claims.remove(Gebied.key(level, c));
			SelfTests.check(Gebied.budget(owner) >= Gebied.BASE, "budget minstens de basis");
			SelfTests.check(Gebied.claim(owner, level, c), "claimen lukt");
			SelfTests.check(!Gebied.claim(guest, level, c), "dubbel claimen kan niet");
			BlockPos inside = new BlockPos(c.getMinBlockX() + 3, 100, c.getMinBlockZ() + 3);
			SelfTests.check(Gebied.allowed(owner, level, inside, Gebied.Action.BUILD), "eigenaar mag bouwen");
			SelfTests.check(!Gebied.allowed(guest, level, inside, Gebied.Action.BUILD), "gast mag niet bouwen");
			SelfTests.check(Gebied.allowed(guest, level, inside, Gebied.Action.SWITCH), "gast mag wel deuren");
			Gebied.trust(owner, guest.getUUID(), "Gast", true);
			SelfTests.check(Gebied.allowed(guest, level, inside, Gebied.Action.BUILD), "vertrouwd: mag bouwen");
			Gebied.trust(owner, guest.getUUID(), "Gast", false);
			SelfTests.check(Gebied.allowed(guest, level, inside.offset(16, 0, 0), Gebied.Action.BUILD), "buiten de chunk: vrij");
			SelfTests.check(Gebied.unclaim(owner, level, c), "vrijgeven");
			SelfTests.check(Gebied.allowed(guest, level, inside, Gebied.Action.BUILD), "na vrijgeven: vrij");
		});
	}
}
