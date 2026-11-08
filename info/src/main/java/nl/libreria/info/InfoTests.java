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
		SelfTests.register("info.completion", level -> {
			java.util.function.Function<String, java.util.Set<String>> keys = id -> {
				var c = nl.libreria.core.Completion.get(id);
				java.util.Set<String> out = new java.util.HashSet<>();
				if (c != null) for (var e : c.entries()) out.add(e.key());
				return out;
			};
			SelfTests.check(keys.apply("stories").size() == Found.STORIES.size() && !Found.STORIES.isEmpty(), "every story");
			var biomes = keys.apply("biomes");
			SelfTests.check(biomes.contains("minecraft:plains") && biomes.contains("minecraft:nether_wastes") && !biomes.contains("minecraft:river")
					&& !biomes.contains("minecraft:the_void"), "biomes: " + biomes.size());
			SelfTests.check(keys.apply("places").containsAll(java.util.Set.of("Village", "Stronghold")), "places: " + keys.apply("places"));
		});
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
			var cow = net.minecraft.world.entity.EntityTypes.COW.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			if (cow != null) {
				cow.setPos(0, 0, 1.5);
				net.minecraft.world.phys.Vec3 at = Look.beside(eye, cow);
				SelfTests.check(at.x < -0.5 && Math.abs(at.z - 1.5) < 0.01, "beside: on your right (west, facing south): " + at);
				SelfTests.check(Look.fits(eye, dir, at), "and that's on the screen");
				// Right up against it, looking down at it: the card hangs in front of its nearest part, and to the right of
				// how wide it looks from here
				cow.setPos(0, 0, 1.0);
				net.minecraft.world.phys.Vec3 hung = Look.hang(eye, cow, cow.getEyeY() - 0.2, cow.getBbWidth() / 2 + 0.45);
				var box = cow.getBoundingBox();
				double gap = Math.sqrt(Math.pow(Math.max(box.minZ - eye.z, 0), 2) + Math.pow(Math.max(eye.y - box.maxY, 0), 2));
				SelfTests.check(hung.distanceTo(eye) <= Math.max(0.35, gap - 0.1) + 1.0e-6, "in front of it: " + hung.distanceTo(eye) + " vs " + gap);
				double cornerSlope = (cow.getBbWidth() / 2) / (box.minZ - eye.z);
				SelfTests.check(-hung.x / hung.z >= cornerSlope, "right of its near corner: " + hung);
			}
		});
		SelfTests.register("info.restand", level -> {
			// For one build every story was cut down to fit one armour stand; what was found or read then goes back
			SelfTests.check(Found.BACK.size() > 300 && "7".equals(Found.BACK.get("lantern:2")) && "-".equals(Found.BACK.get("lantern:3"))
					&& "6".equals(Found.BACK.get("lantern:5")), "which part then is which part now: " + Found.BACK.size());
			var access = level.registryAccess();
			java.util.function.Supplier<Found.Text> any = () -> Found.LINES.get(0);
			// A lantern bow that was taken out of its story then: Mothsparer, part 2 of the Lantern Keeper again
			ItemStack bow = new ItemStack(Items.BOW);
			Found.write(bow, any.get(), "in a shipwreck", 4, "Anna", access);
			then(bow, "x:a:lantern:2", null, 0);
			SelfTests.check(Found.fix(null, bow, access), "brought back");
			var st = Stand.stamp(bow);
			SelfTests.check(st.getStringOr("id", "").equals("a:lantern:2") && st.getIntOr("part", 0) == 2 && "lantern".equals(st.getStringOr("story", ""))
					&& Lore.get(bow, Lore.FOUND).getFirst().getString().contains("part 2 of"), "a part again: " + st);
			SelfTests.check(Lore.get(bow, Lore.FOUND).stream().anyMatch(l -> l.getString().contains("day 4 · Anna")), "where, when and by whom stay");
			SelfTests.check(!Found.fix(null, bow, access), "once");
			// Maren's Hood was part 2 then: part 7 again
			ItemStack hood = new ItemStack(Items.LEATHER_HELMET);
			Found.write(hood, any.get(), "Fished up", 9, "Jeff", access);
			then(hood, "a:lantern:2", "lantern", 2);
			SelfTests.check(Found.fix(null, hood, access) && Stand.stamp(hood).getIntOr("part", 0) == 7
					&& Stand.stamp(hood).getStringOr("id", "").equals("a:lantern:7"), "the hood is part 7 again: " + Stand.stamp(hood));
			// A part that was made up then (the keeper's coat): its words stay, without a story
			ItemStack coat = new ItemStack(Items.LEATHER_CHESTPLATE);
			Found.write(coat, any.get(), "Fished up", 2, "Jeff", access);
			then(coat, "a:lantern:3", "lantern", 3);
			SelfTests.check(Found.fix(null, coat, access), "the coat brought in line");
			st = Stand.stamp(coat);
			SelfTests.check(st.getStringOr("story", "").isEmpty() && st.getStringOr("id", "").equals("x2:a:lantern:3")
					&& Found.byId("x2:a:lantern:3") != null && "The Keeper's Coat".equals(Found.byId("x2:a:lantern:3").name()), "words without a story: " + st);
			SelfTests.check(!Lore.get(coat, Lore.FOUND).getFirst().getString().startsWith("✦ "), "no story line on it");
			// A lore book of then
			Found.Seen seen = new Found.Seen();
			seen.v = 2;
			seen.parts.put("lantern", new java.util.HashSet<>(java.util.Set.of(2, 3, 5)));
			seen.parts.put("odric", new java.util.HashSet<>(java.util.Set.of(1, 6)));
			seen.partInfo.put("lantern:2", "Fished up|9|Jeff");
			seen.partInfo.put("lantern:3", "Fished up|2|Jeff");
			Found.Find a = new Found.Find(), b = new Found.Find();
			a.id = "x:a:lantern:2";
			b.id = "a:lantern:3";
			seen.finds.add(a);
			seen.finds.add(b);
			Found.renumber(seen);
			SelfTests.check(seen.parts.get("lantern").equals(java.util.Set.of(7, 6)) && seen.parts.get("odric").equals(java.util.Set.of(1, 6)),
					"the lore book: " + seen.parts);
			SelfTests.check(seen.partInfo.equals(java.util.Map.of("lantern:7", "Fished up|9|Jeff")), "where they were found: " + seen.partInfo);
			SelfTests.check(a.id.equals("a:lantern:2") && b.id.equals("x2:a:lantern:3"), "finds: " + a.id + ", " + b.id);
			SelfTests.check(Found.SEEN.get(FakePlayer.get(level)).v == Found.VERSION, "a new lore book is new already");
			// A story that grew (or a part with new words): its items catch up
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			Found.write(sword, Found.byId("a:lantern:1"), "in a shipwreck", 5, "Anna", access);
			SelfTests.check(!Found.fix(null, sword, access), "up to date: left alone");
			net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, sword, root -> {
				var t = root.getCompoundOrEmpty(Found.STAMP).copy();
				t.putString("sig", "old");
				root.put(Found.STAMP, t);
			});
			SelfTests.check(Found.fix(null, sword, access) && Stand.stamp(sword).getStringOr("sig", "").equals(Found.sig(Found.byId("a:lantern:1"))),
					"written again");
		});
		SelfTests.register("info.look-stand", level -> {
			// An armour stand wearing a story: the card tells all of it, and hangs beside the stand, not in front of it
			var stand = net.minecraft.world.entity.EntityTypes.ARMOR_STAND.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			SelfTests.check(stand != null, "an armour stand");
			if (stand == null) return;
			SelfTests.check(Stand.card(stand, false) == null && !Stand.dressed(stand), "a bare stand: no card");
			stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			SelfTests.check(Stand.dressed(stand) && Stand.card(stand, false) == null && Stand.card(stand, true) != null, "plain gear: a card only with everything on");
			Stand.showcase(stand, false);
			Look.Card card = Stand.card(stand, false);
			SelfTests.check(card != null && card.name().getString().startsWith("✦ ") && card.subtitle().getString().startsWith("4 of "),
					"a set of one story: " + (card == null ? null : card.name().getString() + " / " + card.subtitle().getString()));
			if (card != null) {
				String all = String.join("\n", card.rows().stream().map(r -> r.value().getString()).toList());
				// The whole text (wrapped differently perhaps) and where it was found
				String whole = Stand.text(stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD), 1000).get(0);
				SelfTests.check(all.replace("\n", " ").contains(whole) && all.contains("day 3"), "the whole story on the card: " + all);
				SelfTests.check(card.rows().size() <= Math.max(6, nl.libreria.core.gui.Glyphs.maxPanel - 3), "fits its panel: " + card.rows().size());
				org.slf4j.LoggerFactory.getLogger("Libreria found").info("STAND-CARD {} rows:\n{}", card.rows().size(), all);
			}
			// A sword with a one-off story too: still one card, still fits
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			Found.write(sword, Found.LINES.get(0), "in a dungeon", 7, "Anna", level.registryAccess());
			stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, sword);
			Look.Card five = Stand.card(stand, false);
			SelfTests.check(five != null && five.name().getString().equals("Armour stand") && five.rows().size() <= Math.max(6, nl.libreria.core.gui.Glyphs.maxPanel - 3),
					"five things: " + (five == null ? null : five.subtitle().getString() + ", " + five.rows().size() + " rows"));
			stand.setInvisible(true);
			SelfTests.check(Stand.dressed(stand), "an invisible stand wearing something can be looked at");
			// Placement: a stand 2.5 blocks south of you, looking at it: right of it (west), bottom not in the floor, smaller
			stand.setPos(0, 0, 2.5);
			net.minecraft.world.phys.Vec3 eye = new net.minecraft.world.phys.Vec3(0, 1.62, 0);
			Look.Target t = Stand.target(eye, new net.minecraft.world.phys.Vec3(0, -0.2, 1).normalize(), stand, five, true);
			SelfTests.check(t.side() == 1 && t.anchor().x < 0 && t.anchor().y > 0 && t.size() <= 1 && t.size() >= 0.08, "beside the stand: " + t.anchor() + " size " + t.size());
			// A step away: smaller, so the whole card still fits on the screen
			stand.setPos(0, 0, 1.0);
			Look.Target close = Stand.target(eye, new net.minecraft.world.phys.Vec3(0, -0.6, 1).normalize(), stand, five, true);
			double angle = Look.dims(five, true)[1] * close.size() / close.anchor().distanceTo(eye);
			SelfTests.check(close.size() < t.size() && angle < 0.8, "up close: size " + close.size() + ", " + Math.round(Math.toDegrees(Math.atan(angle))) + "° tall");
			float[] d = Look.dims(five, true);
			SelfTests.check(d[0] > 0.5 && d[1] > 0.5, "card size " + d[0] + " x " + d[1]);
			// Looking steeply down at something right in front of you: the card comes down into the screen
			net.minecraft.world.phys.Vec3 down = new net.minecraft.world.phys.Vec3(0, -1.2, 1).normalize();
			net.minecraft.world.phys.Vec3 high = new net.minecraft.world.phys.Vec3(-0.6, 1.4, 0.8);
			net.minecraft.world.phys.Vec3 kept = Look.keepOnScreen(eye, down, high, 0.3, Look.DOWN);
			double top = Math.toDegrees(Math.atan2(kept.y + 0.3 - eye.y, Math.hypot(kept.x, kept.z))), look = Math.toDegrees(Math.atan2(down.y, Math.hypot(down.x, down.z)));
			SelfTests.check(kept.y < high.y && top <= look + Look.UP + 0.01, "kept on screen: " + kept + ", top " + top + "° vs look " + look + "°");
			SelfTests.check(Look.keepOnScreen(eye, new net.minecraft.world.phys.Vec3(0, 0, 1), new net.minecraft.world.phys.Vec3(-0.6, 1.4, 2), 0.3, Look.DOWN).y == 1.4,
					"looking straight at it: stays");
		});
		SelfTests.register("info.lorebook", level -> {
			// The lore book as a book: every page fits the book screen, the list of parts leads to each part's page
			FakePlayer fp = FakePlayer.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("lorebook".getBytes()), "Reader"));
			Found.Seen seen = Found.SEEN.get(fp);
			String story = Found.STORIES.keySet().iterator().next();
			java.util.List<Found.Text> parts = new java.util.ArrayList<>();
			for (Found.Text t : Found.PIECES) if (story.equals(t.story())) parts.add(t);
			seen.parts.put(story, new java.util.HashSet<>(java.util.List.of(1, parts.size())));
			seen.partInfo.put(story + ":1", "Found in a shipwreck|12|Reader");
			int[] at = new int[parts.size()];
			Found.Seen s2 = seen;
			LoreBook.storyBook(s2, story, parts, seen.parts.get(story), at);
			nl.libreria.core.gui.Book book = LoreBook.storyBook(s2, story, parts, seen.parts.get(story), at);
			java.util.List<java.util.List<Component>> pages = book.layout();
			for (int i = 0; i < parts.size(); i++) {
				SelfTests.check(at[i] >= 2 && at[i] <= pages.size() && pages.get(at[i] - 1).get(0).getString().equals("Part " + (i + 1)),
						"part " + (i + 1) + " on page " + at[i]);
			}
			int[] how = {1};
			LoreBook.contents(fp, how);
			java.util.List<java.util.List<java.util.List<Component>>> books = java.util.List.of(pages, LoreBook.contents(fp, how).layout());
			for (var b : books) {
				for (var page : b) {
					SelfTests.check(page.size() <= nl.libreria.core.gui.Book.LINES, "a page of " + page.size() + " lines");
					for (Component line : page) SelfTests.check(nl.libreria.core.gui.Glyphs.width(line) <= nl.libreria.core.gui.Book.WIDTH + 1,
							"fits the page (" + nl.libreria.core.gui.Glyphs.width(line) + " px): " + line.getString());
				}
			}
			SelfTests.check(how[0] >= 2 && LoreBook.contents(fp, how).layout().get(how[0] - 1).get(0).getString().contains("How it works"), "how it works on page " + how[0]);
			org.slf4j.LoggerFactory.getLogger("Libreria found").info("LOREBOOK {} pages: {}", pages.size(),
					String.join(" / ", pages.get(0).stream().map(Component::getString).toList()));
			seen.parts.clear();
			seen.partInfo.clear();
		});
		SelfTests.register("info.found.collect", level -> {
			// A story part is told on exactly one item; anyone can find it, again too, but less often than a part they lack
			FakePlayer a = FakePlayer.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("collect-a".getBytes()), "CollectA"));
			Found.Data d = Found.data();
			java.util.Set<String> before = new java.util.HashSet<>(d.used);
			Found.Origin ow = new Found.Origin("overworld", "village", "in a village");
			java.util.Random r = new java.util.Random(9);
			java.util.Map<String, Integer> times = new java.util.HashMap<>();
			int pieces = 0;
			for (int i = 0; i < 600; i++) {
				Found.Text t = Found.pick("sword", "iron_sword", ow, r, a);
				if (t == null || t.story() == null) continue;
				pieces++;
				SelfTests.check(t.kinds().equals(java.util.Set.of("iron_sword")), "a part told on an iron sword: " + t.id() + " " + t.kinds());
				times.merge(t.id(), 1, Integer::sum);
				Found.SEEN.get(a).parts.computeIfAbsent(t.story(), k -> new java.util.HashSet<>()).add(t.part());
			}
			SelfTests.check(pieces > 60, "parts are found often enough: " + pieces + " of 600");
			SelfTests.check(times.values().stream().anyMatch(n -> n > 1), "and the same part can be found again: " + times);
			SelfTests.check(Found.pick("sword", null, ow, r, a) == null || Found.pick("sword", null, ow, r, a).story() == null, "no exact item: no part");
			// A part on the wrong thing (from before): told right
			Found.Text lantern = null;
			for (Found.Text t : Found.PIECES) if ("lantern".equals(t.story()) && t.part() == 1) lantern = t;
			if (lantern != null) {
				ItemStack wrong = new ItemStack(Items.DIAMOND_SWORD);
				Found.write(wrong, lantern, "in a shipwreck", 12, "Anna", level.registryAccess());
				SelfTests.check(Found.fix(null, wrong, level.registryAccess()), "a lantern part on a diamond sword gets fixed");
				String now = wrong.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty(Found.STAMP).getStringOr("id", "");
				Found.Text t = Found.byId(now);
				SelfTests.check(t != null && !now.equals(lantern.id()) && (t.story() == null || Found.on(t, "diamond_sword")), "now: " + now);
				SelfTests.check(Lore.get(wrong, Lore.FOUND).stream().anyMatch(l -> l.getString().contains("day 12 · Anna")), "where, when and by whom stay");
				SelfTests.check(wrong.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME) == null
						|| !wrong.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString().equals(lantern.name()), "not named after the wrong story");
				ItemStack right = new ItemStack(Items.IRON_SWORD);
				Found.write(right, lantern, "in a shipwreck", 12, "Anna", level.registryAccess());
				SelfTests.check(!Found.fix(null, right, level.registryAccess()), "on its own item it stays");
			}
			Found.SEEN.get(a).parts.clear();
			d.used.clear();
			d.used.addAll(before);
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
		// Real loot from the three worlds: gear in it gets a story of that world (here stamped by hand: a fake player
		// isn't a finder, a real one gets it the moment the chest opens)
		SelfTests.register("info.found.loot", level -> {
			var server = level.getServer();
			String[][] tables = {{"overworld", "chests/ruined_portal"}, {"nether", "chests/bastion_treasure"}, {"end", "chests/end_city_treasure"}};
			for (String[] t : tables) {
				var dim = t[0].equals("nether") ? net.minecraft.world.level.Level.NETHER : t[0].equals("end") ? net.minecraft.world.level.Level.END
						: net.minecraft.world.level.Level.OVERWORLD;
				net.minecraft.server.level.ServerLevel lv = server.getLevel(dim);
				var table = server.reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(
						net.minecraft.core.registries.Registries.LOOT_TABLE, net.minecraft.resources.Identifier.withDefaultNamespace(t[1])));
				FakePlayer fp = FakePlayer.get(lv);
				int samples = 0;
				for (int i = 0; i < 60 && samples < 2; i++) {
					var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(lv)
							.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.ZERO)
							.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
					for (ItemStack st : table.getRandomItems(params)) {
						if (Found.kindOf(st) == null) continue;
						SelfTests.check(Found.has(st, Found.PENDING), t[1] + ": gear from loot remembers where it's from");
						Found.stamp(fp, st);
						SelfTests.check(Found.has(st, Found.STAMP) && !Lore.get(st, Lore.FOUND).isEmpty(), t[1] + ": and gets a story");
						String text = String.join(" / ", Lore.get(st, Lore.FOUND).stream().map(Component::getString).toList());
						org.slf4j.LoggerFactory.getLogger("Libreria found").info("FOUND-SAMPLE {} {} {}: {}", t[0], st.getItem(),
								st.get(DataComponents.CUSTOM_NAME) == null ? "" : "\"" + st.get(DataComponents.CUSTOM_NAME).getString() + "\"", text);
						samples++;
					}
				}
				SelfTests.check(samples > 0, t[1] + ": gear found in 60 chests");
			}
		});
		SelfTests.register("info.found", level -> {
			// The texts: many, every kind and place known, every story at least three parts, no stray {slots}
			SelfTests.check(Found.LINES.size() >= 1000, "found texts: " + Found.summary());
			SelfTests.check(Found.STORIES.size() >= 20 && Found.PIECES.size() >= 120, "stories: " + Found.summary());
			java.util.Set<String> known = new java.util.HashSet<>(java.util.List.of("*", "weapon", "armour", "tool", "sword", "axe", "bow", "crossbow",
					"trident", "mace", "spear", "helmet", "chestplate", "leggings", "boots", "shield", "elytra", "pickaxe", "shovel", "hoe", "shears",
					"rod", "brush", "flint", "stick"));
			java.util.Set<String> where = new java.util.HashSet<>(java.util.List.of("", "overworld", "nether", "end", "bastion", "fortress",
					"ruined_portal", "end_city", "ancient_city", "stronghold", "mansion", "outpost", "shipwreck", "buried", "desert", "jungle", "igloo",
					"mineshaft", "dungeon", "trial", "village", "fishing", "barter", "mob", "archaeology"));
			java.util.List<Found.Text> all = new java.util.ArrayList<>(Found.LINES);
			all.addAll(Found.PIECES);
			for (Found.Text t : all) {
				// a one-off text names kinds of things; a story part the exact item it's told on, one that is really found
				boolean kinds = t.story() == null ? t.kinds().stream().allMatch(k -> known.contains(k) || Found.FINDABLE.contains(k))
						: t.kinds().size() == 1 && Found.FINDABLE.containsAll(t.kinds());
				SelfTests.check(kinds && where.contains(t.where()), "kinds/where of " + t.id() + ": " + t.kinds() + " @" + t.where());
				SelfTests.check(!t.text().replace("{item}", "").contains("{") && !t.text().contains("|"), "no stray slot in " + t.text());
				SelfTests.check(t.text().length() <= 300, "not too long: " + t.text());
			}
			for (String id : Found.FINDABLE) SelfTests.check(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(
					net.minecraft.resources.Identifier.withDefaultNamespace(id)), "a real item: " + id);
			for (var e : Found.partsPerStory().entrySet()) SelfTests.check(e.getValue() >= 3, "story " + e.getKey() + " has " + e.getValue() + " parts");
			for (Found.Family f : Found.FAMILIES) {
				for (String slot : f.slots()) SelfTests.check(f.form().contains("{" + slot + "}"), "family " + f.id() + " uses {" + slot + "}");
				SelfTests.check(f.size() >= 10, "family " + f.id() + " has " + f.size());
			}
			// Kinds of things
			SelfTests.check("sword".equals(Found.kindOf(new ItemStack(Items.NETHERITE_SWORD))) && "pickaxe".equals(Found.kindOf(new ItemStack(Items.WOODEN_PICKAXE)))
					&& "helmet".equals(Found.kindOf(new ItemStack(Items.TURTLE_HELMET))) && "rod".equals(Found.kindOf(new ItemStack(Items.FISHING_ROD)))
					&& Found.kindOf(new ItemStack(Items.ENCHANTED_BOOK)) == null && Found.kindOf(new ItemStack(Items.STONE)) == null, "kinds of found gear");
			// Never the same text twice: 400 swords from the overworld, 60 boots from a bastion
			java.util.Random r = new java.util.Random(4);
			java.util.Set<String> ids = new java.util.HashSet<>();
			Found.Origin ow = new Found.Origin("overworld", "dungeon", "in a dungeon"), nb = new Found.Origin("nether", "bastion", "in a bastion");
			int got = 0;
			for (int i = 0; i < 400; i++) {
				Found.Text t = Found.pick("sword", ow, r);
				if (t != null) {
					got++;
					ids.add(t.id());
					SelfTests.check(Found.fits(t.kinds(), "sword") && ow.matches(t.where()), "a text for a sword from a dungeon: " + t.text());
				}
			}
			for (int i = 0; i < 60; i++) {
				Found.Text t = Found.pick("boots", nb, r);
				if (t != null) {
					got++;
					ids.add(t.id());
					SelfTests.check(Found.fits(t.kinds(), "boots") && nb.matches(t.where()), "a text for boots from a bastion: " + t.text());
				}
			}
			SelfTests.check(got == 460 && ids.size() == 460, "460 finds, 460 different texts: " + ids.size());
			// The lore book reads every text back from its id, a family's combination too
			for (String id : ids) {
				Found.Text back = Found.byId(id);
				SelfTests.check(back != null && back.id().equals(id), "the lore book finds " + id + " again");
			}
			Found.Family fam = Found.FAMILIES.get(0);
			StringBuilder fid = new StringBuilder("f:" + fam.id());
			String expect = fam.form();
			for (int i = 0; i < fam.slots().size(); i++) {
				fid.append(":0");
				expect = expect.replace("{" + fam.slots().get(i) + "}", fam.options().get(i).get(0));
			}
			SelfTests.check(Found.byId(fid.toString()) != null && Found.byId(fid.toString()).text().equals(expect), "a family text from its id: " + expect);
			SelfTests.check(Found.byId("f:" + fam.id() + ":999:0:0:0") == null && Found.byId("t:nothing") == null, "unknown ids: nothing");
			// Written on an item: the story part names it and says which part; the weapon's own story stays last
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			Found.Text piece = Found.PIECES.get(0);
			Found.write(sword, piece, "in a shipwreck", 12, "Anna", level.registryAccess());
			var lore = sword.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
			SelfTests.check(lore.get(0).getString().contains(piece.title()) && lore.get(0).getString().contains("part 1 of"), "story header: " + lore.get(0).getString());
			SelfTests.check(lore.get(lore.size() - 1).getString().equals("Found in a shipwreck · day 12 · Anna"), "where: " + lore.get(lore.size() - 1).getString());
			SelfTests.check(sword.get(DataComponents.CUSTOM_NAME) != null && sword.get(DataComponents.CUSTOM_NAME).getString().equals(piece.name()), "named after the story");
			SelfTests.check((piece.story() + ":1").equals(Found.pieceOf(sword)), "knows its part: " + Found.pieceOf(sword));
			int found = lore.size();
			Lore.set(sword, Lore.OWN, java.util.List.of(Lore.line("\"mine\"", Describe.OWN, true), Lore.line("  ~ Anna", Describe.BY, false)));
			SelfTests.check(sword.get(DataComponents.LORE).lines().size() == found + 2 && Lore.get(sword, Lore.FOUND).size() == found, "own words after the find");
			SelfTests.check("mine".equals(Describe.text(sword)), "own words read back: " + Describe.text(sword));
			Lore.set(sword, Lore.OWN, java.util.List.of());
			SelfTests.check(sword.get(DataComponents.LORE).lines().size() == found, "own words taken off again");
			SelfTests.check("a b".equals(Describe.clean(" a \n\u0007 b ")) && Describe.clean("x".repeat(500)).length() == Describe.MAX, "own words are plain text");
			// Loot remembers where it came from; the story comes when a player takes it
			SelfTests.check(Found.origin("chests/bastion_treasure", level) != null && "bastion".equals(Found.origin("chests/bastion_treasure", level).place())
					&& "fishing".equals(Found.origin("gameplay/fishing/treasure", level).place()) && Found.origin("blocks/stone", level) == null, "where loot comes from");
		});
		SelfTests.register("info.details", level -> {
			FakePlayer p = FakePlayer.get(level);
			var reg = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
			java.util.function.Function<String, net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>> ench = id -> reg.getOrThrow(
					net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, net.minecraft.resources.Identifier.withDefaultNamespace(id)));
			// Every vanilla enchantment is explained
			reg.listElements().forEach(h -> SelfTests.check(Details.what(h, 1) != null, "explained: " + h.getRegisteredName()));
			ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
			EnchantmentHelper.updateEnchantments(sword, m -> {
				m.set(ench.apply("sharpness"), 5);
				m.set(ench.apply("looting"), 3);
			});
			sword.set(DataComponents.LORE, new ItemLore(java.util.List.of(Component.literal("old line"))));
			ItemStack before = sword.copy();
			Details.ON.set(p, true);
			ItemStack seen = Details.view(p, sword);
			SelfTests.check(ItemStack.isSameItemSameComponents(sword, before), "the item itself is not changed");
			String all = String.join("\n", seen.get(DataComponents.LORE).lines().stream().map(Component::getString).toList());
			SelfTests.check(all.contains("Every hit does 3 more damage.") && all.contains("old line") && all.contains("(+3)"), "explained, with the bonus: " + all);
			var d = seen.get(DataComponents.TOOLTIP_DISPLAY);
			SelfTests.check(d != null && !d.shows(DataComponents.ENCHANTMENTS) && !d.shows(DataComponents.ATTRIBUTE_MODIFIERS), "the game's own lines hidden");
			SelfTests.check(ItemStack.isSameItemSameComponents(Details.clean(seen), before), "creative mode gets the item back as it was");
			Details.ON.set(p, false);
			SelfTests.check(Details.view(p, sword) == sword, "off: as it is");
			Details.ON.set(p, true);
			ItemStack boots = new ItemStack(Items.IRON_BOOTS);
			EnchantmentHelper.updateEnchantments(boots, m -> m.set(ench.apply("depth_strider"), 3));
			String b = String.join("\n", Details.view(p, boots).get(DataComponents.LORE).lines().stream().map(Component::getString).toList());
			SelfTests.check(b.contains("Depth Strider") && b.contains("(Depth Strider)"), "an enchantment's own attribute: " + b);
			// What blocks, items and potion effects are for
			SelfTests.check(Details.tipCount() >= 150, "item tips: " + Details.tipCount());
			SelfTests.check(Details.tip("magma_block") != null && Details.tip("magma_block").size() == 2, "a magma block, on land and underwater");
			SelfTests.check(Details.tip("red_bed") != null && Details.tip("waxed_cut_copper") != null && Details.tip("stone") == null, "tips by pattern");
			net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.keySet().forEach(id ->
					SelfTests.check(Details.effect(id.getPath()) != null, "effect explained: " + id));
			ItemStack potion = new ItemStack(Items.POTION);
			potion.set(DataComponents.POTION_CONTENTS, new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.SWIFTNESS));
			String pl = String.join("\n", Details.view(p, potion).get(DataComponents.LORE).lines().stream().map(Component::getString).toList());
			SelfTests.check(pl.contains("You walk faster."), "a potion says what it does: " + pl);
			ItemStack magma = new ItemStack(Items.MAGMA_BLOCK);
			String ml = String.join("\n", Details.view(p, magma).get(DataComponents.LORE).lines().stream().map(Component::getString).toList());
			SelfTests.check(ml.contains("Underwater") && ItemStack.isSameItemSameComponents(Details.clean(Details.view(p, magma)), magma), "a magma block, and back: " + ml);
			ItemStack omen = new ItemStack(Items.OMINOUS_BOTTLE);
			ItemStack omenView = Details.view(p, omen);
			String ol = String.join("\n", omenView.get(DataComponents.LORE).lines().stream().map(Component::getString).toList());
			SelfTests.check(ol.contains("Bad Omen") && ol.contains("raid") && !omenView.getOrDefault(DataComponents.TOOLTIP_DISPLAY,
					net.minecraft.world.item.component.TooltipDisplay.DEFAULT).shows(DataComponents.OMINOUS_BOTTLE_AMPLIFIER)
					&& ItemStack.isSameItemSameComponents(Details.clean(omenView), omen), "an ominous bottle: Bad Omen, then what it does: " + ol);
			SelfTests.check(java.util.Arrays.stream(ol.split("\n")).allMatch(l -> l.length() <= Details.WIDTH + 4), "no line runs off the screen: " + ol);
			SelfTests.check(Details.view(p, new ItemStack(Items.STONE)).getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().isEmpty(), "stone: nothing to say");
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

	/** Makes an item look stamped in the short-lived numbering 2 (one build in Nov 2026). */
	private static void then(ItemStack s, String id, String story, int part) {
		net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, s, root -> {
			var t = root.getCompoundOrEmpty(Found.STAMP).copy();
			t.putInt("v", 2);
			t.putString("id", id);
			t.remove("sig");
			if (story == null) {
				t.remove("story");
				t.remove("part");
			} else {
				t.putString("story", story);
				t.putInt("part", part);
			}
			root.put(Found.STAMP, t);
		});
	}
}
