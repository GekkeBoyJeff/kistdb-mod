package nl.libreria.core;

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
		SelfTests.register("core.framework", level -> {
			FakePlayer p = FakePlayer.get(level);
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
			nl.libreria.core.gui.SettingsWindow.open(p, Domain.ITEMS, Start::open);
			// One voice in chat
			SelfTests.check(Say.line(Domain.PLACES).getString().equals("◆ "), "chat lines start with a diamond");
			// Your places: tabs from the modules, and a list without errors
			SelfTests.check(PlaceList.kinds().size() >= 5, "kinds of places: " + PlaceList.kinds().size());
			SelfTests.check(PlaceList.all(p) != null, "places listed");
			// Tips: once, then never again
			Tips.Tip tip = Tips.all(Domain.ITEMS).getFirst();
			Tips.STORE.get(p).seen.remove(tip.key());
			Tips.once(p, tip);
			SelfTests.check(Tips.seen(p, tip), "a tip is remembered once seen");
			Tips.STORE.get(p).seen.remove(tip.key());
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
}
