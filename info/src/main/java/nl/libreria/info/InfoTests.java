package nl.libreria.info;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
		SelfTests.register("info.look-close", level -> {
			// Looking straight ahead at someone a step away: above their head is off the screen, beside them it fits
			net.minecraft.world.phys.Vec3 eye = new net.minecraft.world.phys.Vec3(0, 1.62, 0), dir = new net.minecraft.world.phys.Vec3(0, 0, 1);
			SelfTests.check(!Look.fits(eye, dir, new net.minecraft.world.phys.Vec3(0, 3.4, 1.1)), "a step away, above the head is off screen");
			SelfTests.check(Look.fits(eye, dir, new net.minecraft.world.phys.Vec3(0, 3.4, 6)), "a few steps back it fits");
			var cow = net.minecraft.world.entity.EntityType.COW.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			if (cow != null) {
				cow.setPos(0, 0, 1.5);
				net.minecraft.world.phys.Vec3 at = Look.beside(eye, cow);
				SelfTests.check(at.x < -0.5 && Math.abs(at.z - 1.5) < 0.01, "beside: on your right (west, facing south): " + at);
				SelfTests.check(Look.fits(eye, dir, at), "and that's on the screen");
			}
		});
		SelfTests.register("info.discoveries", level -> {
			FakePlayer p = FakePlayer.get(level);
			String mine = "test|disc|mine", theirs = "test|disc|theirs";
			Discoveries.forget(mine);
			Discoveries.forget(theirs);
			SelfTests.check(Discoveries.arrive(p, mine) && !Discoveries.arrive(p, mine), "the first one here is its discoverer, once");
			java.util.List<nl.libreria.core.gui.Caption.Part> first = Places.tag(p, mine, true, true);
			SelfTests.check(first.size() == 1 && first.get(0).text().equals("first discovery!"), "first: " + first);
			SelfTests.check(Places.tag(p, mine, false, false).isEmpty(), "been here before: just the name");
			Discoveries.First anna = new Discoveries.First();
			anna.who = java.util.UUID.nameUUIDFromBytes("libreria-test-anna".getBytes());
			anna.name = "Anna";
			anna.at = System.currentTimeMillis() - 1000;
			Discoveries.data().firsts.put(theirs, anna);
			SelfTests.check(!Discoveries.arrive(p, theirs), "someone else was here first");
			java.util.List<nl.libreria.core.gui.Caption.Part> other = Places.tag(p, theirs, true, false);
			SelfTests.check(other.size() == 2 && other.get(0).text().equals("found by ") && other.get(1).text().equals("Anna"), "other: " + other);
			SelfTests.check(Discoveries.firstsOf(p.getUUID()) >= 1 && Discoveries.firstsOf(anna.who) == 1, "counted per player");
			Discoveries.forget(mine);
			Discoveries.forget(theirs);
		});
		SelfTests.register("info.landmarks", level -> {
			FakePlayer p = FakePlayer.get(level);
			var stone = Blocks.STONE.defaultBlockState();
			var water = Blocks.WATER.defaultBlockState();
			// The land around each spot has to be loaded (as it is around a real player)
			java.util.function.BiConsumer<Integer, Integer> load = (x, z) -> {
				for (int cx = (x - 56) >> 4; cx <= (x + 56) >> 4; cx++) for (int cz = (z - 56) >> 4; cz <= (z + 56) >> 4; cz++) level.getChunk(cx, cz);
			};
			// A lake: a pond of 49 by 49 in the ground
			int lx = 2000, lz = 2000;
			load.accept(lx, lz);
			int gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz) - 1;
			for (int dx = -24; dx <= 24; dx++) for (int dz = -24; dz <= 24; dz++) level.setBlock(new BlockPos(lx + dx, gy, lz + dz), water, 2);
			p.setPos(lx + 0.5, gy + 1, lz + 0.5);
			Places.Mark lake = Places.landmarkAt(p, "minecraft:plains");
			SelfTests.check(lake != null && lake.type() == PlaceNames.Landmark.LAKE, "a lake: " + lake);
			SelfTests.check(Places.landmarkAt(p, "minecraft:river") == null, "no lake in a river");
			// A peak: a tall spire on flat land
			int px = 2400, pz = 2400;
			load.accept(px, pz);
			int base = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
			for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int y = base; y <= 130; y++) {
				level.setBlock(new BlockPos(px + dx, y, pz + dz), stone, 2);
			}
			p.setPos(px + 0.5, 131, pz + 0.5);
			Places.Mark peak = Places.landmarkAt(p, "minecraft:plains");
			SelfTests.check(peak != null && peak.type() == PlaceNames.Landmark.PEAK, "a peak: " + peak);
			p.setPos(px + 30.5, base, pz + 0.5);
			SelfTests.check(Places.landmarkAt(p, "minecraft:plains") == null, "at its foot: nothing");
			// A cavern: a big hollow room with a roof
			int cx = 2800, cz = 2800, y0 = 140;
			load.accept(cx, cz);
			for (int dx = -14; dx <= 14; dx++) for (int dz = -14; dz <= 14; dz++) {
				level.setBlock(new BlockPos(cx + dx, y0, cz + dz), stone, 2);
				level.setBlock(new BlockPos(cx + dx, y0 + 20, cz + dz), stone, 2);
				if (Math.abs(dx) == 14 || Math.abs(dz) == 14) for (int y = y0; y <= y0 + 20; y++) level.setBlock(new BlockPos(cx + dx, y, cz + dz), stone, 2);
			}
			p.setPos(cx + 0.5, y0 + 1, cz + 0.5);
			Places.Mark cave = Places.landmarkAt(p, "minecraft:plains");
			SelfTests.check(cave != null && cave.type() == PlaceNames.Landmark.CAVE, "a cavern: " + cave);
			// Names: the same peak within its reach, a new one far away
			Names.Area a1 = Names.landmark("test:dim", PlaceNames.Landmark.PEAK, 0, 0, 1);
			Names.Area a2 = Names.landmark("test:dim", PlaceNames.Landmark.PEAK, 50, 20, 1);
			Names.Area a3 = Names.landmark("test:dim", PlaceNames.Landmark.PEAK, 1000, 0, 1);
			SelfTests.check(a1 == a2 && a1 != a3 && !a1.name.equals(a3.name), "peaks: " + a1.name + " / " + a3.name);
			java.util.Set<String> lakes = new java.util.HashSet<>();
			for (int i = 0; i < 300; i++) lakes.add(Names.landmark("test:dim", PlaceNames.Landmark.LAKE, i * 1000, 0, 3).name);
			SelfTests.check(lakes.size() == 300, "300 lakes, 300 names: " + lakes.size() + ", e.g. " + lakes.iterator().next());
			var d = Names.data();
			d.areas.removeIf(ar -> ar.dim.equals("test:dim"));
		});
		SelfTests.register("info.weapons", level -> {
			FakePlayer p = FakePlayer.get(level);
			var access = level.registryAccess();
			var reg = access.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
			java.util.function.Function<String, net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>> ench = id -> reg.getOrThrow(
					net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
							net.minecraft.resources.Identifier.withDefaultNamespace(id)));
			ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
			SelfTests.check(Weapons.potential(sword, access) == 7, "a sword carries 7 enchantments at most: " + Weapons.potential(sword, access));
			SelfTests.check(Weapons.potential(new ItemStack(Items.TRIDENT), access) == 5,
					"a trident 5 (Loyalty and Channeling, not Riptide): " + Weapons.potential(new ItemStack(Items.TRIDENT), access));
			SelfTests.check(Weapons.quality(sword, access) == Weapons.Quality.PLAIN, "no enchantments: plain");
			EnchantmentHelper.updateEnchantments(sword, m -> {
				m.set(ench.apply("sharpness"), 5);
				m.set(ench.apply("unbreaking"), 3);
				m.set(ench.apply("mending"), 1);
			});
			SelfTests.check(Weapons.quality(sword, access) == Weapons.Quality.RARE, "three maxed: rare: " + Weapons.quality(sword, access));
			EnchantmentHelper.updateEnchantments(sword, m -> {
				m.set(ench.apply("looting"), 3);
				m.set(ench.apply("fire_aspect"), 2);
				m.set(ench.apply("knockback"), 2);
				m.set(ench.apply("sweeping_edge"), 3);
			});
			SelfTests.check(Weapons.quality(sword, access) == Weapons.Quality.LEGENDARY, "everything maxed: legendary");
			// Counting
			for (int i = 0; i < 3; i++) Weapons.count(p, sword, "zombie");
			SelfTests.check(Weapons.story(sword).getIntOr("total", 0) == 3, "three foes counted");
			String lore = text(sword.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
			SelfTests.check(lore.contains("Foes felled 3") && lore.contains("Zombie 3") && lore.contains("Legendary"), "tooltip: " + lore);
			SelfTests.check(sword.get(DataComponents.CUSTOM_NAME) == null, "no name yet");
			// A boss: a name straight away, gold because everything on it is maxed
			Weapons.count(p, sword, "wither");
			Component name = sword.get(DataComponents.CUSTOM_NAME);
			SelfTests.check(name != null && name.getString().equals(Weapons.story(sword).getStringOr("name", "")), "it earned a name: " + name);
			SelfTests.check(name != null && net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.GOLD).equals(name.getStyle().getColor()),
					"in gold: " + (name == null ? null : name.getStyle().getColor()));
			lore = text(sword.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
			SelfTests.check(lore.contains("Slew the Wither") && lore.contains("Earned its name"), "tooltip after the boss: " + lore);
			// A name a player gave stays; its colour follows the enchantments (none: white)
			ItemStack bob = new ItemStack(Items.IRON_AXE);
			bob.set(DataComponents.CUSTOM_NAME, Component.literal("Bob"));
			Weapons.count(p, bob, "wither");
			Component bobName = bob.get(DataComponents.CUSTOM_NAME);
			SelfTests.check(bobName != null && bobName.getString().equals("Bob") && Weapons.story(bob).getBooleanOr("legend", false),
					"Bob stays Bob: " + bobName);
			SelfTests.check(net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.WHITE).equals(bobName.getStyle().getColor()), "Bob in white");
			// Lore that was there stays; ours is replaced, not added again
			ItemStack bow = new ItemStack(Items.BOW);
			bow.set(DataComponents.LORE, new ItemLore(java.util.List.of(Component.literal("Made by Ann"))));
			Weapons.count(p, bow, "skeleton");
			int lines = bow.get(DataComponents.LORE).lines().size();
			Weapons.count(p, bow, "skeleton");
			var after = bow.get(DataComponents.LORE).lines();
			SelfTests.check(after.size() == lines && after.get(0).getString().equals("Made by Ann"), "lore kept and replaced: " + text(after));
			// Weapon names are never given twice
			java.util.Set<String> names = new java.util.HashSet<>();
			for (int i = 0; i < 300; i++) names.add(Names.weapon(java.util.UUID.nameUUIDFromBytes(("w" + i).getBytes()), Weapons.Kind.SWORD));
			SelfTests.check(names.size() == 300, "300 weapons, 300 names: " + names.size() + ", e.g. " + names.iterator().next());
			Names.data().weaponsTaken.clear();
		});
		SelfTests.register("info.pets", level -> {
			java.util.Set<String> pets = new java.util.HashSet<>();
			for (int i = 0; i < 400; i++) pets.add(Names.pet(java.util.UUID.nameUUIDFromBytes(("p" + i).getBytes())));
			SelfTests.check(pets.size() == 400, "400 pets, 400 names: " + pets.size());
			for (String n : pets) Names.releasePet(n);
			SelfTests.check(Names.data().petsTaken.isEmpty(), "names free again");
			SelfTests.check(Pets.egg("wolf").is(Items.WOLF_SPAWN_EGG) && Pets.egg("nothing").is(Items.LEAD), "spawn egg icons");
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
	private static String text(java.util.List<Component> lines) {
		StringBuilder b = new StringBuilder();
		for (Component c : lines) b.append(c.getString()).append(" | ");
		return b.toString();
	}

	private static String text(Look.Card card) {
		return card == null ? "" : card.plain().toLowerCase(java.util.Locale.ROOT);
	}
}
