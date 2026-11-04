package nl.libreria.info;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import nl.libreria.core.SelfTests;

/** Zelftests voor de InfoMod-module (draaien alleen met -Dlibreria.selftest=true). */
final class InfoTests {
	private InfoTests() {
	}

	static void register() {
		SelfTests.register("info.places", level -> {
			PlaceNames.Kind plains = PlaceNames.kind("village_plains");
			SelfTests.check(plains != null && plains.named() && "Village".equals(plains.label()), "plains village is a named village");
			SelfTests.check(PlaceNames.kind("mineshaft") == null && PlaceNames.kind("buried_treasure") == null, "no titles for mineshafts");
			SelfTests.check("Ruined Portal".equals(PlaceNames.kind("ruined_portal_desert").label()), "ruined portals by their kind");
			// The same place keeps its name; 500 villages all get different ones
			String a = Names.place("test|v|0", plains, 42), b = Names.place("test|v|0", plains, 42);
			SelfTests.check(a.equals(b), "the same place keeps its name: " + a);
			java.util.Set<String> towns = new java.util.HashSet<>();
			for (int i = 0; i < 500; i++) towns.add(Names.place("test|v|" + i, plains, 7));
			SelfTests.check(towns.size() == 500, "500 villages, 500 names: " + towns.size());
			// Areas: nearby the same name, far away a new one
			Names.Area f1 = Names.area("test:dim", "minecraft:forest", 0, 0, 1);
			Names.Area f2 = Names.area("test:dim", "minecraft:forest", 100, 50, 1);
			Names.Area f3 = Names.area("test:dim", "minecraft:forest", 5000, 0, 1);
			SelfTests.check(f1 == f2 && f1 != f3 && !f1.name.equals(f3.name), "areas: " + f1.name + " / " + f3.name);
			// Villagers: never the same name twice
			java.util.Set<String> people = new java.util.HashSet<>();
			for (int i = 0; i < 300; i++) people.add(Names.villager(java.util.UUID.nameUUIDFromBytes(("v" + i).getBytes())));
			SelfTests.check(people.size() == 300, "300 villagers, 300 names: " + people.size());
			// Running out of names never stalls or crashes: a pool of one gives "Same", "Same II", "Same III"…
			java.util.Set<String> pool = new java.util.HashSet<>();
			java.util.List<String> got = new java.util.ArrayList<>();
			for (int i = 0; i < 30; i++) got.add(Names.fresh(pool, i, "Fallback", (r, attempt) -> "Same"));
			SelfTests.check(got.get(0).equals("Same") && got.get(1).equals("Same II") && got.get(2).equals("Same III")
					&& new java.util.HashSet<>(got).size() == 30, "an empty pool numbers its names: " + got.subList(0, 4) + "… " + got.get(29));
			// A broken or empty generator falls back to the plain name
			String broken = Names.fresh(pool, 1, "Fallback", (r, attempt) -> {
				throw new IllegalStateException("test");
			});
			String empty = Names.fresh(pool, 2, "Fallback", (r, attempt) -> null);
			SelfTests.check(broken.equals("Fallback") && empty.equals("Fallback II"), "a broken generator: " + broken + ", " + empty);
			// Clean up the test names
			var d = Names.data();
			d.places.keySet().removeIf(k -> k.startsWith("test|"));
			d.areas.removeIf(ar -> ar.dim.equals("test:dim"));
			for (int i = 0; i < 300; i++) Names.releaseVillager(java.util.UUID.nameUUIDFromBytes(("v" + i).getBytes()));
			d.taken.clear();
			d.taken.addAll(d.places.values().stream().map(n -> n.toLowerCase(java.util.Locale.ROOT)).toList());
			d.areas.forEach(ar -> d.taken.add(ar.name.toLowerCase(java.util.Locale.ROOT)));
			FakePlayer p = FakePlayer.get(level);
			SelfTests.check(Places.remember(p, "test|village|1,2", a, "Village", new BlockPos(20, 64, 40)), "first visit is new");
			SelfTests.check(!Places.remember(p, "test|village|1,2", a, "Village", new BlockPos(20, 64, 40)), "second visit isn't");
			Places.STORE.get(p).places.removeIf(pl -> pl.key.startsWith("test|"));
		});
		SelfTests.register("info.look-info", level -> {
			FakePlayer p = FakePlayer.get(level);
			BlockPos pos = new BlockPos(60, 120, 60);
			CropBlock wheat = (CropBlock) Blocks.WHEAT;
			level.setBlock(pos, wheat.getStateForAge(wheat.getMaxAge()), 2);
			String c = text(Look.describeBlock(p, level, pos, false));
			SelfTests.check(c.contains("ripe"), "ripe wheat: " + c);
			level.setBlock(pos, wheat.getStateForAge(3), 2);
			c = text(Look.describeBlock(p, level, pos, false));
			SelfTests.check(c.contains("growth"), "growing wheat");
			level.setBlock(pos, Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5), 2);
			c = text(Look.describeBlock(p, level, pos, false));
			SelfTests.check(c.contains("honey"), "full bee nest");
			level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
			SelfTests.check(Look.describeBlock(p, level, pos, false) == null, "plain stone: nothing in 'interesting'");
			SelfTests.check(Look.describeBlock(p, level, pos, true) != null, "plain stone: shown in 'everything'");
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		});
		SelfTests.register("info.compass-clock-horse", level -> {
			SelfTests.check(Held.direction(0).equals("South"), "yaw 0 = south");
			SelfTests.check(Held.direction(180).equals("North"), "yaw 180 = north");
			SelfTests.check(Held.direction(-90).equals("East"), "yaw -90 = east");
			SelfTests.check(Held.direction(135).equals("Northwest"), "yaw 135 = northwest");
			SelfTests.check(Held.clockText(0, false).getString().contains("06:00"), "tick 0 = 06:00");
			SelfTests.check(Held.clockText(18000, false).getString().contains("00:00"), "tick 18000 = midnight");
			SelfTests.check(Held.clockText(18000, false).getString().contains("sleep"), "you can sleep at night");
			SelfTests.check(!Held.clockText(6000, false).getString().contains("sleep"), "not at noon");
			double top = Horses.jumpHeight(1.0);
			SelfTests.check(top > 5.1 && top < 5.4, "max jump ~5.3 blocks: " + top);
			SelfTests.check(StatsGui.time(20L * 60 * 90).equals("1 h 30 min"), "time format");
			SelfTests.check(StatsGui.dist(250_000).equals("2.5 km"), "distance format: " + StatsGui.dist(250_000));
		});
	}

	/** The whole card as one lower-case string ("" if there is no card). */
	private static String text(Look.Card card) {
		return card == null ? "" : card.plain().toLowerCase(java.util.Locale.ROOT);
	}
}
