package nl.libreria.core;

import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Zelftests van de kern (alleen in de CI). */
final class CoreTests {
	private CoreTests() {
	}

	static void register() {
		SelfTests.register("core.house", level -> {
			FakePlayer p = FakePlayer.get(level);
			Setting.STORE.get(p).values.remove(Tips.ENABLED.fullKey());
			SelfTests.check(Tips.ENABLED.get(p), "on as shipped");
			Tips.ENABLED.house(0, null);
			SelfTests.check(!Tips.ENABLED.get(p) && Tips.ENABLED.isForced(), "off for everybody");
			Tips.ENABLED.set(p, true);
			SelfTests.check(!Tips.ENABLED.get(p), "a player can't turn it on then");
			SelfTests.check(nl.libreria.core.gui.SettingsWindow.sections(Tips.ENABLED.domain()).stream()
					.noneMatch(sec -> sec.settings().contains(Tips.ENABLED)), "and doesn't see it");
			Tips.ENABLED.house(null, 0);
			Setting.STORE.get(p).values.remove(Tips.ENABLED.fullKey());
			SelfTests.check(!Tips.ENABLED.get(p) && Tips.ENABLED.start() == false, "off at first for a new player");
			Tips.ENABLED.set(p, true);
			SelfTests.check(Tips.ENABLED.get(p), "who may turn it on");
			Tips.ENABLED.house(null, null);
			Setting.STORE.get(p).values.remove(Tips.ENABLED.fullKey());
			SelfTests.check(Tips.ENABLED.get(p) && !Tips.ENABLED.isForced(), "back as shipped");
		});
		SelfTests.register("core.itemlink", level -> {
			FakePlayer p = FakePlayer.get(level);
			SelfTests.check(ItemLink.line(net.minecraft.network.chat.Component.literal("Ann"),
					new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG, 64)).getString().equals("<Ann> [Oak Log] ×64"),
					"the chat line: " + ItemLink.line(net.minecraft.network.chat.Component.literal("Ann"),
							new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG, 64)).getString());
			var inv = p.getInventory();
			net.minecraft.world.item.ItemStack before = inv.getItem(9);
			inv.setItem(9, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
			ItemLink.LAST.remove(p.getUUID());
			int n = ItemLink.shown;
			var menu = p.inventoryMenu;
			var quick = net.minecraft.world.inventory.ContainerInput.QUICK_MOVE;
			SelfTests.check(!ItemLink.onClick(p, menu, 9, quick, 0), "Shift + left-click stays vanilla");
			SelfTests.check(ItemLink.onClick(p, menu, 9, quick, 1) && ItemLink.shown == n + 1, "Shift + right-click: in chat");
			SelfTests.check(inv.getItem(9).is(net.minecraft.world.item.Items.DIAMOND_SWORD), "and the sword stays where it was");
			SelfTests.check(ItemLink.onClick(p, menu, 9, quick, 1) && ItemLink.shown == n + 1, "not twice in five seconds");
			SelfTests.check(!ItemLink.onClick(p, menu, 1, quick, 1), "not from the crafting grid (empty)");
			ItemLink.LAST.remove(p.getUUID());
			ItemLink.MINE.set(p, false);
			SelfTests.check(!ItemLink.onClick(p, menu, 9, quick, 1), "a player can turn it off");
			ItemLink.MINE.set(p, true);
			ItemLink.ON.set(false);
			SelfTests.check(!ItemLink.onClick(p, menu, 9, quick, 1), "an op can turn it off for everybody");
			ItemLink.ON.set(true);
			ItemLink.LAST.remove(p.getUUID());
			inv.setItem(9, before);
		});
		SelfTests.register("core.completion", level -> {
			net.minecraft.server.MinecraftServer s = level.getServer();
			SelfTests.check(Completion.all().size() >= 5, "collections: " + Completion.all().size());
			for (Completion.Tab t : Completion.Tab.values()) SelfTests.check(!Completion.of(t).isEmpty(), "something on " + t.label);
			java.util.function.Function<String, java.util.Set<String>> keys = id -> {
				Completion.Collection c = Completion.get(id);
				java.util.Set<String> out = new java.util.HashSet<>();
				if (c != null) for (Completion.Entry e : c.entries()) out.add(e.key());
				return out;
			};
			var tasted = keys.apply("tasted");
			SelfTests.check(tasted.containsAll(Set.of("minecraft:bread", "minecraft:cake", "minecraft:potion", "minecraft:milk_bucket"))
					&& !tasted.contains("minecraft:splash_potion") && !tasted.contains("minecraft:stone"), "tasted: " + tasted.size());
			var crafted = keys.apply("crafted");
			SelfTests.check(crafted.contains("minecraft:crafting_table") && crafted.contains("minecraft:chest") && crafted.size() > 300
					&& !crafted.contains("minecraft:egg"), "crafted: " + crafted.size());
			var defeated = keys.apply("defeated");
			SelfTests.check(defeated.contains("minecraft:zombie") && defeated.contains("minecraft:warden") && !defeated.contains("minecraft:giant")
					&& !defeated.contains("minecraft:cow"), "defeated: " + defeated.size());
			var adv = keys.apply("advancements");
			SelfTests.check(adv.size() > 50 && adv.contains("minecraft:story/mine_stone"), "advancements: " + adv.size());
			SelfTests.check(keys.apply("mined").contains("minecraft:diamond_ore"), "mined");
			// Numbers and places
			SelfTests.check(Completion.percent(0.999) == 99 && Completion.percent(1) == 100 && Completion.percent(0.5) == 50, "percent");
			SelfTests.check(Completion.show("tasted", 9, 41).equals("9/41") && Completion.show(Completion.OVERALL, 412, 1000).equals("41%"),
					"shown: " + Completion.show("tasted", 9, 41));
			UUID tess = UUID.fromString("00000000-0000-0000-0000-0000000000c1"), ruud = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
			long now = System.currentTimeMillis();
			Players.note(tess, "Tess", now, now);
			Players.note(ruud, "Ruud", now, now);
			Completion.Snap counted = Completion.snap(s, tess);
			SelfTests.check(counted.have.getOrDefault("tasted", -1) == 0 && Completion.overall(counted) == 0, "nothing done yet");
			Completion.debugSnap(tess, "Tess", java.util.Map.of("tasted", 9), java.util.Map.of("playtime", 72000L * 3));
			Completion.debugSnap(ruud, "Ruud", java.util.Map.of("tasted", 30), java.util.Map.of("playtime", 72000L));
			var board = Completion.ranking(s, "tasted");
			SelfTests.check(Completion.rank(board, ruud) < Completion.rank(board, tess), "more tasted, higher place");
			var hours = Completion.ranking(s, "playtime");
			SelfTests.check(Completion.rank(hours, tess) < Completion.rank(hours, ruud) && Completion.show("playtime", 72000L * 3, 0).equals("3 h"),
					"counters rank too");
			// The windows draw
			FakePlayer p = FakePlayer.get(level);
			for (Completion.Tab t : Completion.Tab.values()) SelfTests.check(ProgressWindow.picture(p, tess, t).length() > 100, "progress " + t.label);
			SelfTests.check(CollectionWindow.picture(p, tess, Completion.get("tasted")).length() > 100, "a collection");
			SelfTests.check(TopListsWindow.picture(p, null).length() > 100 && TopListsWindow.picture(p, "tasted").length() > 100, "top lists");
			Completion.debugForget(tess);
			Completion.debugForget(ruud);
			Players.forget(tess);
			Players.forget(ruud);
		});
		SelfTests.register("core.chronicle", level -> {
			Chronicle.Data d = Chronicle.data();
			int before = d.entries.size();
			Chronicle.write(null, "", Chronicle.Kind.WORLD, "A test moment", false);
			SelfTests.check(d.entries.size() == before + 1 && d.entries.getLast().text.equals("A test moment"), "written down");
			SelfTests.check(Chronicle.firstTime("test:first") && !Chronicle.firstTime("test:first"), "only the first is the first");
			FakePlayer p = FakePlayer.get(level);
			Chronicle.write(p, Chronicle.Kind.FIRST, "tested the chronicle", true);
			SelfTests.check(Chronicle.line(d.entries.getLast(), false).getString().endsWith(p.getName().getString() + " tested the chronicle"),
					"a line: " + Chronicle.line(d.entries.getLast(), false).getString());
			Chronicle.open(p);
			d.entries.removeIf(e -> e.text.equals("A test moment") || e.text.equals("tested the chronicle"));
			d.firsts.remove("test:first");
		});
		SelfTests.register("core.profile", level -> {
			FakePlayer p = FakePlayer.get(level);
			net.minecraft.server.MinecraftServer s = level.getServer();
			UUID anna = UUID.fromString("00000000-0000-0000-0000-0000000000a9");
			long now = System.currentTimeMillis(), day = 24L * 3600 * 1000;
			Players.note(anna, "Anna", now - 10 * day, now - 3 * day);
			Players.Known k = Players.get(s, anna);
			SelfTests.check(k != null && k.name().equals("Anna") && k.online() == null && k.first() == now - 10 * day, "known: " + k);
			SelfTests.check(Players.byName(s, "anna") != null, "by name, any case");
			SelfTests.check(Players.ago(now - 3 * day - 1000).equals("3 days ago") && Players.ago(now).equals("Just now")
					&& Players.ago(now - 26 * 3600 * 1000L).equals("Yesterday") && Players.ago(now - 90 * 60 * 1000L).equals("1 hour ago"),
					"how long ago: " + Players.ago(now - 3 * day - 1000));
			SelfTests.check(Players.activeWithin(s, 7 * day) >= 1 && Players.activeWithin(s, day) < Players.activeWithin(s, 7 * day) + 1,
					"active players counted");
			SelfTests.check(Profile.lines().size() >= 3, "lines on a profile: " + Profile.lines().size());
			String picture = ProfileWindow.picture(p, anna);
			SelfTests.check(picture.contains("Anna") && picture.contains("Playtime"), "a profile is drawn: " + picture.length());
			SelfTests.check(Profile.stats(s, anna).custom("play_time") == 0, "no statistics file: zero");
			// While you were away: what others did, not what you did
			Chronicle.write(anna, "Anna", Chronicle.Kind.BOSS, "slew the Wither (self-test)", true);
			SelfTests.check(Chronicle.since(p.getUUID(), now - 1000).stream().anyMatch(e -> e.text.equals("slew the Wither (self-test)")),
					"others' moments since");
			SelfTests.check(Chronicle.since(anna, now - 1000).stream().noneMatch(e -> e.text.equals("slew the Wither (self-test)")),
					"not your own");
			Chronicle.forget(e -> e.text.equals("slew the Wither (self-test)"));
			Players.forget(anna);
			SelfTests.check(Players.get(s, anna) == null, "forgotten again");
		});
		SelfTests.register("core.chronicle-history", level -> {
			// Anna found diamonds on 1 January, before the chronicle; Bram on 1 February, and was called the first
			UUID anna = UUID.fromString("00000000-0000-0000-0000-00000000000a"), bram = UUID.fromString("00000000-0000-0000-0000-00000000000b");
			java.nio.file.Path world;
			try {
				world = java.nio.file.Files.createTempDirectory("libreria-history");
				java.nio.file.Path adv = java.nio.file.Files.createDirectories(world.resolve("players").resolve("advancements"));
				java.nio.file.Path stats = java.nio.file.Files.createDirectories(world.resolve("stats"));
				java.nio.file.Files.writeString(adv.resolve(anna + ".json"), """
						{"minecraft:recipes/misc/crafting_table": {"criteria": {"unlock_right_away": "2025-12-31 09:00:00 +0100"}, "done": true},
						 "minecraft:story/mine_diamond": {"criteria": {"diamond": "2026-01-01 10:00:00 +0100"}, "done": true},
						 "minecraft:story/enter_the_nether": {"criteria": {"entered_nether": "2026-03-01 10:00:00 +0100"}, "done": true},
						 "DataVersion": 4000}""");
				java.nio.file.Files.writeString(adv.resolve(bram + ".json"), """
						{"minecraft:story/mine_diamond": {"criteria": {"diamond": "2026-02-01 10:00:00 +0100"}, "done": true},
						 "minecraft:story/enter_the_nether": {"criteria": {"entered_nether": "2026-02-01 10:00:00 +0100"}, "done": true},
						 "DataVersion": 4000}""");
				java.nio.file.Files.writeString(stats.resolve(anna + ".json"),
						"{\"stats\": {\"minecraft:killed\": {\"minecraft:wither\": 1}, \"minecraft:custom\": {\"minecraft:deaths\": 2}}}");
				java.nio.file.Files.writeString(world.resolve("players").resolve("advancements").resolve("not-a-player.json"), "{}");
			} catch (java.io.IOException e) {
				throw new IllegalStateException(e);
			}
			ChronicleHistory h = ChronicleHistory.read(world, Set.of("story/mine_diamond", "story/enter_the_nether"), Set.of("wither", "warden"));
			SelfTests.check(h.files == 3, "three player files read: " + h.files);
			SelfTests.check(h.done.get("story/mine_diamond").size() == 2, "both found diamonds");
			Chronicle.Data d = new Chronicle.Data();
			long feb = java.time.OffsetDateTime.parse("2026-02-01T10:00:05+01:00").toInstant().toEpochMilli();
			d.entries.add(entry(bram, "Bram", Chronicle.Kind.FIRST, "was the first to find diamonds", feb));
			d.entries.add(entry(bram, "Bram", Chronicle.Kind.FIRST, "was the first to reach the Nether", feb));
			d.entries.add(entry(bram, "Bram", Chronicle.Kind.ARRIVAL, "was the first to arrive in this world", feb - 3_600_000L));
			int fixed = Chronicle.apply(d, h);
			SelfTests.check(fixed == 2, "two moments corrected: " + fixed);
			SelfTests.check(d.entries.get(0).text.equals("found diamonds") && !d.entries.get(0).major, "diamonds: " + d.entries.get(0).text);
			SelfTests.check(d.entries.get(1).text.equals("was the first to reach the Nether") && d.entries.get(1).major, "Bram was first in the Nether");
			SelfTests.check(d.entries.get(2).text.equals("arrived in the world"), "arrival: " + d.entries.get(2).text);
			SelfTests.check(d.firsts.containsAll(Set.of("adv:story/mine_diamond", "adv:story/enter_the_nether", "boss:wither", "death:" + anna))
					&& !d.firsts.contains("boss:warden"), "firsts: " + d.firsts);
			SelfTests.check(d.arrived.containsAll(Set.of(anna, bram)), "both arrived already");
			SelfTests.check(Chronicle.apply(d, h) == 0, "a second start changes nothing");
		});
		SelfTests.register("core.settings", level -> {
			FakePlayer p = FakePlayer.get(level);
			boolean before = Libreria.HUB_BUTTON.get(p);
			Libreria.HUB_BUTTON.set(p, !before);
			SelfTests.check(Libreria.HUB_BUTTON.get(p) == !before, "toggle flips");
			Libreria.HUB_BUTTON.set(p, before);
			int idx = Libreria.GUIDE_SECONDS.index(p);
			Libreria.GUIDE_SECONDS.cycle(p, 1);
			SelfTests.check(Libreria.GUIDE_SECONDS.index(p) == (idx + 1) % Libreria.GUIDE_SECONDS.size(), "choice cycles");
			Libreria.GUIDE_SECONDS.cycle(p, -1);
			SelfTests.check(Libreria.GUIDE_SECONDS.get(p) == 90, "default is 90 s");
		});
		nl.libreria.core.dialog.DialogTests.register();
		SelfTests.register("core.server-settings", level -> {
			ServerSetting.Text discord = Links.KINDS.getFirst().setting();
			discord.set("https://discord.gg/abc");
			SelfTests.check(discord.get().equals("https://discord.gg/abc") && Links.present().size() >= 1, "link stored");
			SelfTests.check(Links.uri("javascript:alert(1)") == null && Links.uri("discord.gg/x") == null
					&& Links.uri("https://example.org") != null, "only web addresses");
			discord.reset();
			SelfTests.check(!discord.isSet(), "reset clears it");
			SelfTests.check(ServerSetting.all().size() >= 6, "settings registered: " + ServerSetting.all().size());
		});
		SelfTests.register("core.windows", level -> {
			// The pieces of our own windows are in the pack
			for (String g : new String[] {"win.panel", "card1.l", "card4.r", "card2.m64", "big.chest", "sm.gear", "ico.bundle"}) {
				SelfTests.check(nl.libreria.core.gui.Glyphs.has(g), "window piece " + g);
			}
			SelfTests.check(!nl.libreria.core.gui.Glyphs.ICON_Y.isEmpty(), "big pictures have heights");
			// A canvas places things in window pixels and fits text
			var c = new nl.libreria.core.gui.Canvas().panel().card(0, 0, 4, 3, 0x62D6E8).text("Items", 15, 58, 0xFFFFFF);
			SelfTests.check(c.build().getString().contains("Items"), "canvas text is in the title");
			String cut = nl.libreria.core.gui.Canvas.fit("A rather long name for a little card", 60);
			SelfTests.check(cut.endsWith("…") && nl.libreria.core.gui.Glyphs.width(Component.literal(cut)) <= 60, "text fits its card: " + cut);
			SelfTests.check(nl.libreria.core.gui.Canvas.wrap("Sort button in your inventory", 112).size() == 2, "a long label wraps");
			SelfTests.check(nl.libreria.core.gui.Canvas.cellY(6) == 139 && nl.libreria.core.gui.Canvas.cellY(9) == 197, "cell rows");
			// Keys show as the player has them; pictures drawn by the server say what the key does
			Component k = Ui.text("{sneak} + {swap}", net.minecraft.ChatFormatting.WHITE);
			boolean keybind = k.getSiblings().stream().anyMatch(x -> x.getContents() instanceof net.minecraft.network.chat.contents.KeybindContents);
			SelfTests.check(keybind, "a key in text is the player's own key");
			SelfTests.check(Keys.plain("{sneak} + {swap}").equals("Sneak + Swap hands"), "keys in words: " + Keys.plain("{sneak} + {swap}"));
			SelfTests.check(nl.libreria.core.gui.Glyphs.width(k) >= 40, "a key is measured as its name");
			FakePlayer p = FakePlayer.get(level);
			SelfTests.check(!nl.libreria.core.gui.CanvasWindow.available(p), "no pack, no own windows");
			// What a window's title holds (read back in the CI log when a picture looks wrong)
			String json = nl.libreria.core.gui.SettingsCanvas.unopened(p, Domain.PLACES).pictureJson();
			Libreria.LOG.info("LIBRERIA-PICTURE settings.places {}", json);
			SelfTests.check(json.length() > 100, "the settings window has a picture");
			// Characters the window fonts lack become look-alikes (the client would draw a box for them)
			SelfTests.check(nl.libreria.core.gui.Canvas.clean("✚ New waypoint").equals("+ New waypoint"),
					"look-alikes: " + nl.libreria.core.gui.Canvas.clean("✚ New waypoint"));
		});
		SelfTests.register("core.framework", level -> {
			FakePlayer p = FakePlayer.get(level);
			// The start screen comes from the data pack (the same one G opens)
			Start.open(p);
			SelfTests.check(level.getServer().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DIALOG)
					.get(Start.START).isPresent(), "the start screen is in the data pack");
			// Every domain has a page that opens, and buttons on it
			for (Domain d : Domain.values()) {
				Start.domain(p, d);
				SelfTests.check(!Module.hubEntries(d).isEmpty(), "buttons on " + d.label);
			}
			// Settings: grouped per domain under player names, never under a module's name
			java.util.Set<String> moduleNames = new java.util.HashSet<>();
			for (Module m : Module.all()) moduleNames.add(m.name);
			moduleNames.remove("Waypoints");
			for (Domain d : Domain.values()) {
				var sections = nl.libreria.core.gui.SettingsWindow.sections(d);
				SelfTests.check(!sections.isEmpty(), "settings for " + d.label);
				for (var sec : sections) {
					SelfTests.check(!moduleNames.contains(sec.name()), "group " + sec.name() + " is a module name");
					java.util.Set<String> fields = new java.util.HashSet<>();
					for (Setting st : sec.settings()) SelfTests.check(fields.add(st.module.id + "_" + st.key), "one field per setting: " + st.fullKey());
				}
			}
			nl.libreria.core.gui.SettingsWindow.open(p);
			nl.libreria.core.gui.SettingsWindow.open(p, Domain.YOU, Start::open);
			// One voice in chat
			SelfTests.check(Say.line(Domain.PLACES).getString().equals("◆ "), "chat lines start with a diamond");
			// Your places: tabs from the modules, and a list without errors
			SelfTests.check(PlaceList.kinds().size() >= 4, "kinds of places: " + PlaceList.kinds().size());
			SelfTests.check(PlaceList.all(p) != null, "places listed");
			// Tips: once, then never again
			Tips.Tip tip = Tips.all(Domain.YOU).getFirst();
			// Start clean: other tests may have left tips waiting for this (fake) player, and the queue is first come, first served
			Tips.forget(p.getUUID());
			nl.libreria.core.gui.TopCard.forget(p.getUUID());
			Tips.STORE.get(p).seen.remove(tip.key());
			nl.libreria.core.gui.Toast.show(p, Items.BOOK, nl.libreria.core.gui.Toast.INFO, "Busy", "A card is on top");
			Tips.once(p, tip);
			SelfTests.check(!Tips.seen(p, tip), "a tip waits while a card is on top");
			nl.libreria.core.gui.Toast.hide(p);
			Tips.once(p, tip);
			SelfTests.check(Tips.seen(p, tip), "a tip shows (and is remembered) once the top is free");
			Tips.STORE.get(p).seen.remove(tip.key());
			Tips.forget(p.getUUID());
			nl.libreria.core.gui.TopCard.forget(p.getUUID());
			// Commands and help built from the modules
			int lines = 0;
			for (Module m : Module.all()) lines += m.commandHelp().size();
			SelfTests.check(lines >= 15, "command help lines: " + lines);
			Welcome.commands(p);
			Welcome.guide(p);
			Tips.all(p);
		});
		SelfTests.register("core.caption", level -> {
			FakePlayer p = FakePlayer.get(level);
			nl.libreria.core.gui.Caption.show(p, Component.literal("Oakhaven"), Component.literal("Village"));
			nl.libreria.core.gui.Caption.forget(p.getUUID());
			var marks = nl.libreria.core.gui.Markers.forPlayer(p);
			SelfTests.check(marks != null, "markers for a player");
		});
		SelfTests.register("core.beam", level -> {
			FakePlayer p = FakePlayer.get(level);
			Guide.toPoint(p, level, new Vec3(10, 100, 10), new ItemStack(Items.COMPASS), Component.literal("Test"), 5);
			SelfTests.check(Guide.has(p), "beam active");
			Guide.clear(p);
			SelfTests.check(!Guide.has(p), "beam cleared");
			Guide.toBox(p, level, new AABB(0, 100, 0, 2, 101, 1), new ItemStack(Items.DIAMOND), Component.literal("Chest"), 3, 5);
			Guide.flash(p, level, new AABB(0, 100, 0, 1, 101, 1), 0xFF0000, 10);
			Guide.clear(p);
			Hud.show(p, "test", Component.literal("hud"), Hud.PRIO_INFO, 20);
			Hud.flash(p, Component.literal("flash"));
		});
	}

	private static Chronicle.Entry entry(UUID who, String name, Chronicle.Kind kind, String text, long at) {
		Chronicle.Entry e = new Chronicle.Entry();
		e.who = who;
		e.name = name;
		e.kind = kind.name();
		e.text = text;
		e.major = true;
		e.at = at;
		return e;
	}
}
