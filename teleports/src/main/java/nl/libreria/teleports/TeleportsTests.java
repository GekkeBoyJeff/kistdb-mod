package nl.libreria.teleports;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.SelfTests;

/** Self-tests of homes, teleport asks, /back and /spawn (CI only). */
final class TeleportsTests {
	private TeleportsTests() {
	}

	private static boolean near(Vec3 a, Vec3 b) {
		return a.distanceTo(b) < 0.01;
	}

	static void register() {
		SelfTests.register("teleports.homes", level -> {
			FakePlayer p = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Homer"));
			TeleportsMod.Data d = TeleportsMod.PLAYERS.get(p);
			d.homes.clear();
			d.favourite = "";
			d.extraHomes = 0;
			try {
				Vec3 base = new Vec3(200.5, 90, 200.5), farm = new Vec3(260.5, 90, 200.5), mine = new Vec3(300.5, 40, 200.5);
				p.teleportTo(level, base.x, base.y, base.z, java.util.Set.of(), 0, 0, false);
				SelfTests.check(Homes.set(p, null) && "Home".equals(Homes.favourite(d)), "/sethome without a name: Home, and it's the /home");
				p.teleportTo(level, farm.x, farm.y, farm.z, java.util.Set.of(), 0, 0, false);
				SelfTests.check(Homes.set(p, "Farm") && d.homes.size() == 2, "/sethome Farm: a second home");
				SelfTests.check("Home".equals(Homes.favourite(d)), "the first stays the /home");
				SelfTests.check(Homes.go(p, null) && near(p.position(), base), "/home goes to the favourite (" + p.position() + ")");
				SelfTests.check(Homes.go(p, "farm") && near(p.position(), farm), "/home farm (any case) goes there");
				SelfTests.check(Travel.back(p) && near(p.position(), base), "/back goes to where you were before the teleport");
				SelfTests.check(Travel.back(p) && near(p.position(), farm), "/back again: back and forth");
				// The limit: the server's number, plus what an operator gives
				int max = TeleportsMod.maxHomes(p.getUUID());
				for (int i = d.homes.size(); i < max; i++) SelfTests.check(Homes.set(p, "Spot " + i), "room for home " + (i + 1) + " of " + max);
				SelfTests.check(!Homes.set(p, "One too many"), "no more than " + max + " homes");
				p.teleportTo(level, mine.x, mine.y, mine.z, java.util.Set.of(), 0, 0, false);
				SelfTests.check(Homes.set(p, "Farm") && d.homes.size() == max && near(new Vec3(Homes.find(d, "Farm").x, Homes.find(d, "Farm").y,
						Homes.find(d, "Farm").z), mine), "the same name again moves that home, even when all are set");
				d.extraHomes = 1;
				SelfTests.check(Homes.set(p, "Extra"), "an operator's extra home makes room");
				Homes.star(p, "Farm");
				SelfTests.check("Farm".equals(Homes.favourite(d)), "a starred home is the /home");
				SelfTests.check(Homes.delete(p, "Farm") && "Home".equals(Homes.favourite(d)), "deleting the /home: the first one is it again");
				SelfTests.check(Homes.rename(p, "Home", "Castle") && "Castle".equals(Homes.favourite(d)), "renaming keeps it the /home");
				SelfTests.check(!Homes.rename(p, "Castle", "Extra") && Homes.clean("§cred  house ") != null && !Homes.clean("§cred").contains("§"),
						"no two homes with one name, colour codes go");
				// Dying: /back takes you there
				p.teleportTo(level, base.x, base.y, base.z, java.util.Set.of(), 0, 0, false);
				Travel.rememberBack(p, Travel.Why.DEATH);
				SelfTests.check(d.back != null && d.back.death && near(new Vec3(d.back.x, d.back.y, d.back.z), base), "/back after dying");
				SelfTests.check(Travel.spawnPoint(level.getServer()).level() != null, "spawn is known");
			} finally {
				d.homes.clear();
				d.favourite = "";
				d.extraHomes = 0;
				d.back = null;
				Travel.forget(p.getUUID());
			}
		});

		SelfTests.register("teleports.import", level -> {
			UUID id = UUID.randomUUID();
			try {
				java.nio.file.Path f = java.nio.file.Files.createTempFile("TeleportCommands", ".json");
				java.nio.file.Files.writeString(f, "{\"Warps\":[],\"Players\":[{\"UUID\":\"" + id + "\",\"DefaultHome\":\"huis\",\"Homes\":["
						+ "{\"name\":\"mine\",\"x\":-58,\"y\":-3,\"z\":809,\"world\":\"minecraft:overworld\"},"
						+ "{\"name\":\"huis\",\"x\":62,\"y\":63,\"z\":768,\"world\":\"minecraft:overworld\"},"
						+ "{\"name\":\".\",\"x\":-476,\"y\":125,\"z\":246,\"world\":\"minecraft:the_nether\"}]}]}");
				var players = HomeImport.read(f);
				java.nio.file.Files.deleteIfExists(f);
				SelfTests.check(players != null && players.size() == 1 && players.getFirst().homes().size() == 3, "the old file is read");
				SelfTests.check(HomeImport.apply(players.getFirst()) == 3, "all three homes come over");
				TeleportsMod.Data d = TeleportsMod.PLAYERS.get(id);
				SelfTests.check("huis".equals(Homes.favourite(d)), "their default home is their /home");
				TeleportsMod.Home mine = Homes.find(d, "mine");
				SelfTests.check(mine != null && mine.x == -57.5 && mine.z == 809.5 && "minecraft:the_nether".equals(Homes.find(d, ".").dim),
						"in the middle of the block, in the right world");
				SelfTests.check(HomeImport.apply(players.getFirst()) == 0 && d.homes.size() == 3, "taken over twice: nothing doubles");
			} catch (java.io.IOException e) {
				throw new AssertionError("could not write the test file: " + e);
			} finally {
				TeleportsMod.PLAYERS.get(id).homes.clear();
			}
		});

		SelfTests.register("teleports.asks", level -> {
			FakePlayer a = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Asker"));
			FakePlayer b = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Host"));
			a.teleportTo(level, 100.5, 90, 100.5, java.util.Set.of(), 0, 0, false);
			b.teleportTo(level, 140.5, 90, 130.5, java.util.Set.of(), 0, 0, false);
			try {
				SelfTests.check(Asks.ask(a, b, false) && Asks.waitingFor(b).size() == 1, "an ask waits for the other");
				SelfTests.check(Asks.sent(a, b) != null, "and the one who asked sees it went out");
				SelfTests.check(Asks.answer(b, "asker", true) && near(a.position(), b.position()), "accepted: the asker comes over");
				SelfTests.check(Asks.waitingFor(b).isEmpty(), "and the ask is gone");
				a.teleportTo(level, 100.5, 90, 100.5, java.util.Set.of(), 0, 0, false);
				SelfTests.check(Asks.ask(a, b, true) && Asks.answer(b, null, true) && near(b.position(), a.position()), "/tpahere: they come to you");
				SelfTests.check(Asks.ask(a, b, false) && Asks.answer(b, null, false) && Asks.waitingFor(b).isEmpty(), "said no: gone, nobody moved");
				SelfTests.check(Asks.ask(a, b, false) && Asks.cancel(a, null) && Asks.waitingFor(b).isEmpty(), "taken back");
				SelfTests.check(!Asks.ask(a, a, false), "not yourself");
				TeleportsMod.ASKS.set(b, 2);
				SelfTests.check(!Asks.ask(a, b, false), "not someone who takes no asks");
				TeleportsMod.ASKS.set(b, 1);
				SelfTests.check(!Asks.ask(a, b, false), "only people they trust: not a stranger");
				TeleportsMod.ASKS.set(b, 0);
				TeleportsMod.PLAYERS.get(a).noAsks = true;
				SelfTests.check(!Asks.ask(a, b, false), "an operator took asking away");
			} finally {
				TeleportsMod.PLAYERS.get(a).noAsks = false;
				TeleportsMod.ASKS.set(b, 0);
				Asks.forget(a);
				Asks.forget(b);
				Travel.forget(a.getUUID());
				Travel.forget(b.getUUID());
			}
		});
	}
}
