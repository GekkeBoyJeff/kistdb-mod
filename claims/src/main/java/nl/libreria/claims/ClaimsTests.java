package nl.libreria.claims;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import nl.libreria.core.SelfTests;
import nl.libreria.core.Ui;

/** Zelftests: claimen, budget en bescherming, de teksten en het kaartmenu. */
final class ClaimsTests {
	private ClaimsTests() {
	}

	static void register() {
		SelfTests.register("claims.claim-and-protect", level -> {
			FakePlayer owner = FakePlayer.get(level);
			FakePlayer guest = FakePlayer.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Guest"));
			ChunkPos c = new ChunkPos(30, 30);
			ClaimsMod.STORE.get().claims.remove(ClaimsMod.key(level, c));
			SelfTests.check(ClaimsMod.budget(owner) >= ClaimsMod.START.get(), "budget is at least the base");
			SelfTests.check(ClaimsMod.claim(owner, level, c), "claiming works");
			SelfTests.check(!ClaimsMod.claim(guest, level, c), "can't claim twice");
			BlockPos inside = new BlockPos(c.getMinBlockX() + 3, 100, c.getMinBlockZ() + 3);
			SelfTests.check(ClaimsMod.allowed(owner, level, inside, ClaimsMod.Action.BUILD), "owner can build");
			SelfTests.check(!ClaimsMod.allowed(guest, level, inside, ClaimsMod.Action.BUILD), "guest can't build");
			SelfTests.check(ClaimsMod.allowed(guest, level, inside, ClaimsMod.Action.SWITCH), "guest can use doors");
			ClaimsMod.Claim claim = ClaimsMod.claimAt(level, c);
			SelfTests.check(claim != null && !ClaimsMod.trustedIn(guest, claim), "guest isn't trusted yet");
			ClaimsMod.trust(owner, guest.getUUID(), "Guest", true);
			SelfTests.check(ClaimsMod.allowed(guest, level, inside, ClaimsMod.Action.BUILD), "trusted: can build");
			SelfTests.check(ClaimsMod.trustedIn(guest, claim), "trusted: shown as trusted (cyan)");
			ClaimsMod.trust(owner, guest.getUUID(), "Guest", false);
			SelfTests.check(ClaimsMod.allowed(guest, level, inside.offset(16, 0, 0), ClaimsMod.Action.BUILD), "outside the chunk: free");
			SelfTests.check(ClaimsMod.unclaim(owner, level, c), "release");
			SelfTests.check(ClaimsMod.allowed(guest, level, inside, ClaimsMod.Action.BUILD), "after release: free");
		});

		SelfTests.register("claims.names", level -> {
			FakePlayer owner = FakePlayer.get(level);
			ChunkPos a = new ChunkPos(60, 60), b = new ChunkPos(61, 60), far = new ChunkPos(70, 60), c = new ChunkPos(62, 60);
			for (ChunkPos x : new ChunkPos[] {a, b, far, c}) ClaimsMod.STORE.get().claims.remove(ClaimsMod.key(level, x));
			SelfTests.check(ClaimsMod.claim(owner, level, a) && ClaimsMod.claim(owner, level, far), "two pieces of land");
			SelfTests.check(ClaimsMod.nameArea(owner, level, a, "  Raven  hold "), "name the first piece");
			SelfTests.check("Raven hold".equals(ClaimsMod.claimAt(level, a).area), "spaces cleaned up: " + ClaimsMod.claimAt(level, a).area);
			SelfTests.check(ClaimsMod.claimAt(level, far).area.isEmpty(), "the other piece keeps no name");
			SelfTests.check(ClaimsMod.claim(owner, level, b) && "Raven hold".equals(ClaimsMod.claimAt(level, b).area),
					"a chunk claimed next to it gets its name");
			SelfTests.check(ClaimsMod.nameArea(owner, level, far, "Eastwatch") && "Eastwatch".equals(ClaimsMod.claimAt(level, far).area),
					"the other piece gets a name of its own");
			SelfTests.check(!ClaimsMod.nameArea(owner, level, a, "x") && ClaimsMod.cleanName("§cRed") != null
					&& !ClaimsMod.cleanName("§cRed").contains("§"), "too short is refused, colour codes go");
			SelfTests.check(ClaimsMod.area(level, a).size() == 2, "a piece is the chunks that touch");
			ClaimsMod.claim(owner, level, c);
			SelfTests.check("Raven hold".equals(ClaimsMod.claimAt(level, c).area), "growing keeps the name");
			for (ChunkPos x : new ChunkPos[] {a, b, far, c}) ClaimsMod.unclaim(owner, level, x);
		});

		SelfTests.register("claims.texts", level -> {
			FakePlayer p = FakePlayer.get(level);
			SelfTests.check(ClaimsMod.duration(45).equals("45 min"), "duration in minutes");
			SelfTests.check(ClaimsMod.duration(120).equals("2 h"), "duration in hours");
			SelfTests.check(ClaimsMod.duration(80).equals("1 h 20 min"), "duration in hours and minutes");
			SelfTests.check(ClaimsMod.budgetText(p).startsWith("You can claim " + ClaimsMod.budget(p) + " chunks ("), "budget text");
			SelfTests.check(ClaimsMod.growthSentence().startsWith("+1 per 2 hours played"), "how the budget grows");
			SelfTests.check(ClaimsMod.nextChunkSentence(p).startsWith("Next chunk: play "), "what earns the next chunk");
			for (String line : ClaimsMod.nextChunkLines(p)) SelfTests.check(line.length() <= 36, "fits in a tooltip: " + line);
			String next = ClaimsMod.nextChunkShort(p);
			SelfTests.check(next.contains(" play") || next.contains("most"), "next chunk, short: " + next);
			double f = ClaimsMod.nextChunkFraction(p);
			SelfTests.check(f >= 0 && f <= 1, "next chunk, how far: " + f);
		});

		SelfTests.register("claims.map", level -> {
			FakePlayer p = FakePlayer.get(level);
			ChunkPos here = p.chunkPosition();
			ChunkPos east = new ChunkPos(here.x() + 1, here.z());
			ClaimsMod.STORE.get().claims.remove(ClaimsMod.key(level, east));
			SelfTests.check(ClaimsMod.claim(p, level, east), "claim the chunk east of you");
			MapGui map = MapGui.create(p);
			int middle = (MapGui.MAP_ROWS / 2) * 9 + MapGui.COLS / 2;
			SelfTests.check(map.getGuiElement(middle).getItemStack().is(Items.PLAYER_HEAD), "your head is in the middle of the map");
			SelfTests.check(map.getGuiElement(middle + 1).getItemStack().is(Ui.item("lime_concrete")), "east is to the right, yours is lime");
			SelfTests.check(map.getGuiElement(5 * 9 + 5).getItemStack().is(Items.EMERALD), "budget item in the bottom row");
			SelfTests.check(ClaimsMod.unclaim(p, level, east), "release it again");
			MapGui.TrustGui trust = new MapGui.TrustGui(p);
			SelfTests.check(trust.getGuiElement(31).getItemStack().is(Items.OAK_DOOR), "trusted players menu has the doors switch");
		});
	}
}
