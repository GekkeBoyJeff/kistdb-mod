package nl.libreria.claims;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import nl.libreria.core.SelfTests;

/** Zelftests: claimen, budget en bescherming. */
final class ClaimsTests {
	private ClaimsTests() {
	}

	static void register() {
		SelfTests.register("gebied.claimen-en-beschermen", level -> {
			FakePlayer owner = FakePlayer.get(level);
			FakePlayer guest = FakePlayer.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Gast"));
			ChunkPos c = new ChunkPos(30, 30);
			ClaimsMod.STORE.get().claims.remove(ClaimsMod.key(level, c));
			SelfTests.check(ClaimsMod.budget(owner) >= ClaimsMod.BASE, "budget minstens de basis");
			SelfTests.check(ClaimsMod.claim(owner, level, c), "claimen lukt");
			SelfTests.check(!ClaimsMod.claim(guest, level, c), "dubbel claimen kan niet");
			BlockPos inside = new BlockPos(c.getMinBlockX() + 3, 100, c.getMinBlockZ() + 3);
			SelfTests.check(ClaimsMod.allowed(owner, level, inside, ClaimsMod.Action.BUILD), "eigenaar mag bouwen");
			SelfTests.check(!ClaimsMod.allowed(guest, level, inside, ClaimsMod.Action.BUILD), "gast mag niet bouwen");
			SelfTests.check(ClaimsMod.allowed(guest, level, inside, ClaimsMod.Action.SWITCH), "gast mag wel deuren");
			ClaimsMod.trust(owner, guest.getUUID(), "Gast", true);
			SelfTests.check(ClaimsMod.allowed(guest, level, inside, ClaimsMod.Action.BUILD), "vertrouwd: mag bouwen");
			ClaimsMod.trust(owner, guest.getUUID(), "Gast", false);
			SelfTests.check(ClaimsMod.allowed(guest, level, inside.offset(16, 0, 0), ClaimsMod.Action.BUILD), "buiten de chunk: vrij");
			SelfTests.check(ClaimsMod.unclaim(owner, level, c), "vrijgeven");
			SelfTests.check(ClaimsMod.allowed(guest, level, inside, ClaimsMod.Action.BUILD), "na vrijgeven: vrij");
		});
	}
}
