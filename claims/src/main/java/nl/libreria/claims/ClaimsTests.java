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

		SelfTests.register("claims.admin", level -> {
			java.util.UUID who = java.util.UUID.randomUUID();
			// two pieces: three chunks in a row, and one on its own
			ChunkPos[] row = {new ChunkPos(-200, 90), new ChunkPos(-199, 90), new ChunkPos(-198, 90)};
			ChunkPos alone = new ChunkPos(-190, 90);
			for (ChunkPos c : row) ClaimsMod.debugClaim(level, c, who, "Dana");
			ClaimsMod.debugClaim(level, alone, who, "Dana");
			try {
				var pieces = ClaimsAdmin.pieces(who);
				SelfTests.check(pieces.size() == 2 && pieces.get(0).chunks().size() == 3 && pieces.get(1).chunks().size() == 1,
						"land in pieces, biggest first: " + pieces.stream().map(pc -> pc.chunks().size()).toList());
				SelfTests.check(ClaimsAdmin.holders().stream().anyMatch(h -> h.id().equals(who) && h.chunks() == 4 && h.pieces() == 2),
						"in the list of everyone's land");
				ClaimsAdmin.rename(pieces.get(0), "Dana's Rest");
				SelfTests.check("Dana's Rest".equals(ClaimsMod.claimAt(level, row[2]).area) && ClaimsMod.claimAt(level, alone).area.isEmpty(),
						"renaming a piece names only its chunks");
				ClaimsAdmin.Piece found = ClaimsAdmin.piece(who, pieces.get(0).dim(), row[1]);
				SelfTests.check(found != null && "Dana's Rest".equals(found.name()), "a piece is found by any of its chunks");
				int before = ClaimsMod.budget(who);
				ClaimsMod.setExtra(who, 10);
				SelfTests.check(ClaimsMod.budget(who) == before + 10 && ClaimsMod.extra(who) == 10, "extra chunks on top of what was earned");
				ClaimsMod.setExtra(who, -1000);
				SelfTests.check(ClaimsMod.budget(who) == 0, "taking more than there is leaves none");
				ClaimsMod.setExtra(who, 0);
				SelfTests.check(!ClaimsMod.STORE.get().extra.containsKey(who), "no extra: nothing stored");
				SelfTests.check(ClaimsAdmin.release(pieces.get(1)) == 1 && ClaimsMod.claimAt(level, alone) == null
						&& ClaimsMod.claimAt(level, row[0]) != null, "releasing a piece leaves the other");
				SelfTests.check(ClaimsAdmin.releaseAll(who) == 3 && ClaimsMod.owned(who) == 0, "releasing all of it");
			} finally {
				for (ChunkPos c : row) ClaimsMod.STORE.get().claims.remove(ClaimsMod.key(level, c));
				ClaimsMod.STORE.get().claims.remove(ClaimsMod.key(level, alone));
				ClaimsMod.setExtra(who, 0);
			}
		});

		SelfTests.register("claims.grief", level -> {
			java.util.UUID who = java.util.UUID.randomUUID();
			ChunkPos claimed = new ChunkPos(120, 120), free = new ChunkPos(121, 120);
			ClaimsMod.STORE.get().claims.remove(ClaimsMod.key(level, free));
			ClaimsMod.debugClaim(level, claimed, who, "Erik");
			int y = 120, z = claimed.getMinBlockZ() + 8;
			BlockPos inside = new BlockPos(claimed.getMaxBlockX() - 1, y, z), outside = new BlockPos(free.getMinBlockX() + 2, y, z);
			BlockPos boom = new BlockPos(free.getMinBlockX(), y, z);
			var planks = net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState();
			try {
				level.setBlockAndUpdate(inside, planks);
				level.setBlockAndUpdate(outside, planks);
				net.minecraft.world.entity.decoration.ArmorStand stand = new net.minecraft.world.entity.decoration.ArmorStand(level,
						inside.getX() + 0.5, y + 1, z + 0.5);
				level.addFreshEntity(stand);
				// a blast right on the border, as a creeper would: the claimed side stays whole
				level.explode(null, boom.getX() + 0.5, y + 0.5, z + 0.5, 4f, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
				SelfTests.check(level.getBlockState(inside).is(net.minecraft.world.level.block.Blocks.OAK_PLANKS), "a blast breaks nothing in a claim");
				SelfTests.check(level.getBlockState(outside).isAir(), "but does break free land next to it");
				SelfTests.check(!stand.isRemoved() && stand.isAlive(), "an armour stand in the claim sits it out");
				stand.discard();
				SelfTests.check(Grief.flowsIn(level, outside, inside) && !Grief.flowsIn(level, inside, inside.west()),
						"lava and water don't flow in from outside (but do inside)");
				SelfTests.check(Grief.claimed(level, inside) && !Grief.claimed(level, outside), "fire knows what's claimed");
				SelfTests.check(Grief.guarded(level, inside, null), "a mob can't change claimed blocks");
				// an enderman and a vindicator (their goals load, with the claim checks in them)
				var enderman = new net.minecraft.world.entity.monster.Enderman(net.minecraft.world.entity.EntityType.ENDERMAN, level);
				var vindicator = new net.minecraft.world.entity.monster.illager.Vindicator(net.minecraft.world.entity.EntityType.VINDICATOR, level);
				SelfTests.check(enderman != null && vindicator != null, "endermen and door breakers know about claims");
			} finally {
				level.setBlockAndUpdate(inside, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(outside, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				ClaimsMod.STORE.get().claims.remove(ClaimsMod.key(level, claimed));
			}
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
