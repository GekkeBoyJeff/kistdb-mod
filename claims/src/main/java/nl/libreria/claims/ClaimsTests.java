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
		SelfTests.register("claims.claim-and-protect", level -> {
			FakePlayer owner = FakePlayer.get(level);
			FakePlayer guest = FakePlayer.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Guest"));
			ChunkPos c = new ChunkPos(30, 30);
			ClaimsMod.STORE.get().claims.remove(ClaimsMod.key(level, c));
			SelfTests.check(ClaimsMod.budget(owner) >= ClaimsMod.BASE, "budget is at least the base");
			SelfTests.check(ClaimsMod.claim(owner, level, c), "claiming works");
			SelfTests.check(!ClaimsMod.claim(guest, level, c), "can't claim twice");
			BlockPos inside = new BlockPos(c.getMinBlockX() + 3, 100, c.getMinBlockZ() + 3);
			SelfTests.check(ClaimsMod.allowed(owner, level, inside, ClaimsMod.Action.BUILD), "owner can build");
			SelfTests.check(!ClaimsMod.allowed(guest, level, inside, ClaimsMod.Action.BUILD), "guest can't build");
			SelfTests.check(ClaimsMod.allowed(guest, level, inside, ClaimsMod.Action.SWITCH), "guest can use doors");
			ClaimsMod.trust(owner, guest.getUUID(), "Guest", true);
			SelfTests.check(ClaimsMod.allowed(guest, level, inside, ClaimsMod.Action.BUILD), "trusted: can build");
			ClaimsMod.trust(owner, guest.getUUID(), "Guest", false);
			SelfTests.check(ClaimsMod.allowed(guest, level, inside.offset(16, 0, 0), ClaimsMod.Action.BUILD), "outside the chunk: free");
			SelfTests.check(ClaimsMod.unclaim(owner, level, c), "release");
			SelfTests.check(ClaimsMod.allowed(guest, level, inside, ClaimsMod.Action.BUILD), "after release: free");
		});
	}
}
