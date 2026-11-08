package nl.libreria.core.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.Libreria;
import nl.libreria.core.Links;
import nl.libreria.core.Module;
import nl.libreria.core.ServerSetting;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Values;
import nl.libreria.core.dialog.Window;

/**
 * The admin window (operators only): the house rules of every module, with sliders, checkboxes and text fields.
 * Changes apply at once, without config files or a restart, and are logged.
 */
public final class AdminWindow {
	private AdminWindow() {
	}

	public static void open(ServerPlayer player) {
		if (!allowed(player)) return;
		Window w = Window.path("Settings", "Server settings")
				.text(Ui.text("Rules for everybody on this server. Only operators see this window. Grouped by the part of "
						+ "Libreria (the module) they belong to.", ChatFormatting.GRAY));
		for (Module m : Module.all()) {
			if (m.serverSettings().isEmpty() && m.adminButtons().isEmpty() && m.settings().isEmpty()) continue;
			List<String> names = new ArrayList<>();
			for (ServerSetting s : m.serverSettings()) names.add(s.label + ": " + s.display());
			int forced = 0;
			for (nl.libreria.core.Setting st : m.settings()) if (st.isForced()) forced++;
			if (!m.settings().isEmpty()) names.add(m.settings().size() + " players' settings" + (forced > 0 ? ", " + forced + " set for everybody" : ""));
			for (Module.SettingsButton b : m.adminButtons()) names.add(b.label());
			Component name = m.on() ? Ui.text(m.name, ChatFormatting.WHITE) : Ui.text(m.name + " · turned off", ChatFormatting.RED);
			w.button(name, Ui.text(String.join("\n", names), ChatFormatting.GRAY), (p, v) -> open(p, m));
		}
		w.back(SettingsWindow::open).show(player);
	}

	public static void open(ServerPlayer player, Module module) {
		if (!allowed(player)) return;
		Window w = Window.path("Settings", "Server settings", module.name);
		w.item(new ItemStack(module.icon), Ui.text("Server-wide: this changes it for every player.", ChatFormatting.GRAY));
		for (ServerSetting s : module.serverSettings()) {
			Component label = Ui.text(s.label, ChatFormatting.WHITE);
			switch (s) {
				case ServerSetting.Number n -> w.slider(s.key, label, n.min, n.max, n.step, n.get());
				case ServerSetting.Toggle t -> w.toggle(s.key, label, t.get());
				case ServerSetting.Text t -> w.field(s.key, label, t.get(), t.maxLength);
			}
		}
		// The players' own settings: each player chooses (starting at what you set here), or one value for everybody
		if (!module.settings().isEmpty()) {
			w.text(Ui.text("Players' settings: let each player choose (and what they start with), or set it for everybody. "
					+ "Set for everybody, a setting is no longer in the players' settings.", ChatFormatting.GRAY));
		}
		for (nl.libreria.core.Setting st : module.settings()) {
			Component label = Ui.text(nl.libreria.core.Keys.plain(st.label), ChatFormatting.WHITE);
			List<String> ids = new ArrayList<>();
			List<Component> names = new ArrayList<>();
			String now;
			switch (st) {
				case nl.libreria.core.Setting.Toggle t -> {
					ids.addAll(List.of("p1", "p0", "a1", "a0"));
					names.add(Ui.text("Each player · on at first", ChatFormatting.WHITE));
					names.add(Ui.text("Each player · off at first", ChatFormatting.WHITE));
					names.add(Ui.text("On for everybody", ChatFormatting.GREEN));
					names.add(Ui.text("Off for everybody", ChatFormatting.RED));
					Integer f = t.forced();
					now = f != null ? "a" + (f != 0 ? 1 : 0) : "p" + (t.start() ? 1 : 0);
				}
				case nl.libreria.core.Setting.Choice c -> {
					for (int i = 0; i < c.size(); i++) {
						ids.add("p" + c.valueAt(i));
						names.add(Ui.text("Each player · " + c.labelAt(i) + " at first", ChatFormatting.WHITE));
					}
					for (int i = 0; i < c.size(); i++) {
						ids.add("a" + c.valueAt(i));
						names.add(Ui.text(c.labelAt(i) + " for everybody", ChatFormatting.GOLD));
					}
					Integer f = c.forced();
					now = f != null ? "a" + f : "p" + c.start();
				}
			}
			w.choice("ps_" + st.key, label, ids, names, now);
		}
		for (Module.SettingsButton b : module.adminButtons()) {
			w.button(Ui.text(b.label() + " ▸", ChatFormatting.AQUA), Ui.text(b.tooltip(), ChatFormatting.GRAY), (p, v) -> {
				if (allowed(p)) b.action().accept(p);
			});
		}
		if (module.serverSettings().isEmpty() && module.settings().isEmpty()) {
			w.back(AdminWindow::open).show(player);
			return;
		}
		w.button(Ui.text("✔ Save", ChatFormatting.GREEN), Ui.text("Apply for everybody, right away", ChatFormatting.GRAY), (p, v) -> {
			if (!allowed(p)) return;
			List<String> problems = new ArrayList<>();
			int changed = apply(p, module, v, problems);
			Ui.sound(p, SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, 1.4f);
			if (!problems.isEmpty()) {
				Toast.show(p, Items.BARRIER, Toast.WARN, "Not saved: " + problems.getFirst(), "That has to be a full https:// address.");
				open(p, module);
			} else {
				Toast.show(p, module.icon, Toast.GOOD, changed == 0 ? "Nothing changed" : "Server settings saved",
						module.name + (changed == 0 ? "" : " · " + changed + (changed == 1 ? " change" : " changes")));
				open(p);
			}
		});
		w.button(Ui.text("Defaults", ChatFormatting.YELLOW), Ui.text("Back to what the module ships with", ChatFormatting.GRAY), (p, v) -> {
			if (!allowed(p)) return;
			for (ServerSetting s : module.serverSettings()) s.reset();
			for (nl.libreria.core.Setting st : module.settings()) st.house(null, null);
			Libreria.LOG.info("{} reset the server settings of {}", p.getName().getString(), module.name);
			open(p, module);
		});
		w.button(Ui.text("What do these do?", ChatFormatting.WHITE), Ui.text("One line per setting", ChatFormatting.GRAY), (p, v) -> {
			Window h = Window.path("Settings", "Server settings", module.name, "Help");
			for (ServerSetting s : module.serverSettings()) h.text(explain(s));
			h.back(pl -> open(pl, module)).show(p);
		});
		w.cancel(AdminWindow::open).show(player);
	}

	private static boolean allowed(ServerPlayer p) {
		if (Ui.isOp(p)) return true;
		Toast.show(p, Items.BARRIER, Toast.BAD, "Operators only", "The server settings are for admins.");
		return false;
	}

	private static Component explain(ServerSetting s) {
		MutableComponent c = Ui.text(s.label, ChatFormatting.WHITE);
		if (!s.lore.isEmpty()) c.append(Ui.text(" – " + String.join(" ", s.lore), ChatFormatting.GRAY));
		return c;
	}

	/** Stores what was filled in (links must be web addresses); returns how many settings changed. */
	static int apply(ServerPlayer p, Module module, Values v, List<String> problems) {
		int changed = 0;
		for (ServerSetting s : module.serverSettings()) {
			if (!v.has(s.key)) continue;
			String before = s.display();
			switch (s) {
				case ServerSetting.Number n -> n.set(v.integer(s.key, n.get()));
				case ServerSetting.Toggle t -> t.set(v.bool(s.key, t.get()));
				case ServerSetting.Text t -> {
					String text = v.text(s.key, t.get()).strip();
					if (s.key.startsWith("link_") && !text.isEmpty() && Links.uri(text) == null) {
						problems.add(s.label);
						continue;
					}
					t.set(text);
				}
			}
			if (!before.equals(s.display())) {
				changed++;
				Libreria.LOG.info("{} set {} to {}", p.getName().getString(), s.fullKey(), s.display());
			}
		}
		for (nl.libreria.core.Setting st : module.settings()) {
			String pick = v.text("ps_" + st.key, "");
			if (pick.length() < 2) continue;
			int value;
			try {
				value = Integer.parseInt(pick.substring(1));
			} catch (NumberFormatException e) {
				continue;
			}
			Integer forcedBefore = st.forced();
			boolean everybody = pick.charAt(0) == 'a';
			int shipped = switch (st) {
				case nl.libreria.core.Setting.Toggle t -> t.def ? 1 : 0;
				case nl.libreria.core.Setting.Choice c -> c.defaultValue();
			};
			Integer startBefore = switch (st) {
				case nl.libreria.core.Setting.Toggle t -> t.start() ? 1 : 0;
				case nl.libreria.core.Setting.Choice c -> c.start();
			};
			Integer forced = everybody ? value : null;
			// What players start with stays as it was when it's set for everybody (so "each player" again finds it back)
			Integer start = everybody ? (startBefore == shipped ? null : startBefore) : (value == shipped ? null : value);
			if (java.util.Objects.equals(forcedBefore, forced) && java.util.Objects.equals(startBefore, start == null ? shipped : start)) continue;
			st.house(forced, start);
			changed++;
			Libreria.LOG.info("{} set the players' setting {} to {}", p.getName().getString(), st.fullKey(),
					everybody ? value + " for everybody" : "each player, starting at " + value);
		}
		return changed;
	}
}
