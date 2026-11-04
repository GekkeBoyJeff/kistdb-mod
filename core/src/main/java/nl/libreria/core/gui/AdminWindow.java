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
		Window w = Window.of(Ui.text("⚙ Server settings", ChatFormatting.GOLD).withStyle(ChatFormatting.BOLD))
				.text(Ui.text("Rules for everybody on this server. Only operators see this window.", ChatFormatting.GRAY));
		for (Module m : Module.all()) {
			if (m.serverSettings().isEmpty() && m.adminButtons().isEmpty()) continue;
			List<String> names = new ArrayList<>();
			for (ServerSetting s : m.serverSettings()) names.add(s.label + ": " + s.display());
			for (Module.SettingsButton b : m.adminButtons()) names.add(b.label());
			w.button(Ui.text(m.name, ChatFormatting.WHITE), Ui.text(String.join("\n", names), ChatFormatting.GRAY), (p, v) -> open(p, m));
		}
		w.done().show(player);
	}

	public static void open(ServerPlayer player, Module module) {
		if (!allowed(player)) return;
		Window w = Window.of(Ui.text("⚙ " + module.name, ChatFormatting.GOLD).withStyle(ChatFormatting.BOLD));
		w.item(new ItemStack(module.icon), Ui.text("Server-wide: this changes it for every player.", ChatFormatting.GRAY));
		for (ServerSetting s : module.serverSettings()) {
			Component label = Ui.text(s.label, ChatFormatting.WHITE);
			switch (s) {
				case ServerSetting.Number n -> w.slider(s.key, label, n.min, n.max, n.step, n.get());
				case ServerSetting.Toggle t -> w.toggle(s.key, label, t.get());
				case ServerSetting.Text t -> w.field(s.key, label, t.get(), t.maxLength);
			}
		}
		for (Module.SettingsButton b : module.adminButtons()) {
			w.button(Ui.text(b.label() + " ▸", ChatFormatting.AQUA), Ui.text(b.tooltip(), ChatFormatting.GRAY), (p, v) -> {
				if (allowed(p)) b.action().accept(p);
			});
		}
		if (module.serverSettings().isEmpty()) {
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
			} else {
				Toast.show(p, module.icon, Toast.GOOD, changed == 0 ? "Nothing changed" : "Server settings saved",
						module.name + (changed == 0 ? "" : " · " + changed + (changed == 1 ? " change" : " changes")));
			}
		});
		w.button(Ui.text("Defaults", ChatFormatting.YELLOW), Ui.text("Back to what the module ships with", ChatFormatting.GRAY), (p, v) -> {
			if (!allowed(p)) return;
			for (ServerSetting s : module.serverSettings()) s.reset();
			Libreria.LOG.info("{} reset the server settings of {}", p.getName().getString(), module.name);
			open(p, module);
		});
		w.button(Ui.text("What do these do?", ChatFormatting.WHITE), Ui.text("One line per setting", ChatFormatting.GRAY), (p, v) -> {
			Window h = Window.of("⚙ " + module.name + " · What do these do?");
			for (ServerSetting s : module.serverSettings()) h.text(explain(s));
			h.back(pl -> open(pl, module)).show(p);
		});
		w.back(AdminWindow::open).show(player);
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
		return changed;
	}
}
