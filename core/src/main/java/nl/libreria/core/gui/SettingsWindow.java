package nl.libreria.core.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import nl.libreria.core.Module;
import nl.libreria.core.Setting;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Values;
import nl.libreria.core.dialog.Window;

/**
 * Your settings, as a real window: first the modules, then one module with a checkbox or option button per setting
 * and a Save button. Every player has their own settings; nothing here changes anything for others.
 */
public final class SettingsWindow {
	private SettingsWindow() {
	}

	/** All modules that have settings. */
	public static void open(ServerPlayer player) {
		Window w = Window.of("Settings")
				.text(Ui.text("Your own settings: nothing here changes anything for other players.", ChatFormatting.GRAY));
		for (Module m : Module.all()) {
			if (m.settings().isEmpty() && m.settingsButtons().isEmpty()) continue;
			w.button(Ui.text(m.name, ChatFormatting.WHITE), Ui.text(String.join(" ", m.description), ChatFormatting.GRAY),
					(p, v) -> open(p, m));
		}
		if (Ui.isOp(player)) {
			w.button(Ui.text("⚙ Server (admin)", ChatFormatting.GOLD), Ui.text("House rules for everybody: budgets, radii, links…",
					ChatFormatting.GRAY), (p, v) -> AdminWindow.open(p));
		}
		w.done().show(player);
	}

	/** One module: a line of explanation per setting, then the checkboxes and options, then Save. */
	public static void open(ServerPlayer player, Module module) {
		Window w = Window.of(module.name + " · Settings");
		w.item(new ItemStack(module.icon), Ui.text(String.join(" ", module.description), ChatFormatting.GRAY));
		for (Setting s : module.settings()) {
			Component label = Ui.text(s.label, ChatFormatting.WHITE);
			if (s instanceof Setting.Toggle t) {
				w.toggle(s.key, label, t.get(player));
			} else if (s instanceof Setting.Choice c) {
				List<String> ids = new ArrayList<>();
				List<Component> names = new ArrayList<>();
				for (int i = 0; i < c.size(); i++) {
					ids.add(Integer.toString(c.valueAt(i)));
					names.add(Ui.text(c.labelAt(i), ChatFormatting.AQUA));
				}
				w.choice(s.key, label, ids, names, Integer.toString(c.get(player)));
			}
		}
		if (!module.settings().isEmpty()) {
			w.button(Ui.text("✔ Save", ChatFormatting.GREEN), Ui.text("Keep these settings", ChatFormatting.GRAY), (p, v) -> {
				int changed = apply(p, module, v);
				Ui.sound(p, SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, 1.4f);
				Toast.show(p, module.icon, Toast.GOOD, changed == 0 ? "Nothing changed" : "Settings saved",
						module.name + (changed == 0 ? "" : " · " + changed + (changed == 1 ? " change" : " changes")));
			});
			w.button(Ui.text("Defaults", ChatFormatting.YELLOW), Ui.text("Back to how they were at the start", ChatFormatting.GRAY),
					(p, v) -> {
						for (Setting s : module.settings()) {
							if (s instanceof Setting.Toggle t) t.set(p, t.def);
							else if (s instanceof Setting.Choice c) c.set(p, c.defaultValue());
						}
						open(p, module);
					});
		}
		if (!module.settings().isEmpty()) {
			w.button(Ui.text("What do these do?", ChatFormatting.WHITE), Ui.text("One line per setting", ChatFormatting.GRAY),
					(p, v) -> help(p, module));
		}
		for (Module.SettingsButton b : module.settingsButtons()) {
			w.button(Ui.text(b.label() + " ▸", ChatFormatting.AQUA), Ui.text(b.tooltip(), ChatFormatting.GRAY), (p, v) -> b.action().accept(p));
		}
		w.back(SettingsWindow::open).show(player);
	}

	/** What each setting of a module does. */
	static void help(ServerPlayer player, Module module) {
		Window w = Window.of(module.name + " · What do these do?");
		for (Setting s : module.settings()) w.text(explain(s));
		w.back(p -> open(p, module)).show(player);
	}

	/** "Label: what it does" in one line. */
	private static Component explain(Setting s) {
		MutableComponent c = Ui.text(s.label, ChatFormatting.WHITE);
		if (!s.lore.isEmpty()) c.append(Ui.text(" – " + String.join(" ", s.lore), ChatFormatting.GRAY));
		return c;
	}

	/** Stores what was filled in; returns how many settings changed. */
	static int apply(ServerPlayer p, Module module, Values v) {
		int changed = 0;
		for (Setting s : module.settings()) {
			if (!v.has(s.key)) continue;
			if (s instanceof Setting.Toggle t) {
				boolean now = v.bool(s.key, t.get(p));
				if (now != t.get(p)) {
					t.set(p, now);
					changed++;
				}
			} else if (s instanceof Setting.Choice c) {
				int value;
				try {
					value = Integer.parseInt(v.text(s.key, ""));
				} catch (NumberFormatException e) {
					continue;
				}
				boolean known = false;
				for (int i = 0; i < c.size(); i++) known |= c.valueAt(i) == value;
				if (known && value != c.get(p)) {
					c.set(p, value);
					changed++;
				}
			}
		}
		return changed;
	}
}
