package nl.libreria.core.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import nl.libreria.core.Domain;
import nl.libreria.core.Module;
import nl.libreria.core.Setting;
import nl.libreria.core.Start;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Values;
import nl.libreria.core.dialog.Window;

/**
 * Your settings, by the same three domains as the start screen: Settings → You → "Tools &amp; refills" → checkboxes
 * and a Save button. Groups have player names ("When you die", "Search &amp; sorting"), whichever module the setting
 * comes from. Every player has their own settings; nothing here changes anything for others.
 */
public final class SettingsWindow {
	/** Groups in this order within their domain; unknown ones after these. */
	private static final List<String> ORDER = List.of("Search & sorting", "Quick stack & supply chests", "Tools & refills", "Land map",
			"Finding your way", "When you die", "Achievements", "Your name", "What you see", "Effects", "Small comforts");

	private SettingsWindow() {
	}

	/** One group of settings: where it is, what's in it, and the extra screens (like "Choose effects") that go with it. */
	public record Section(Domain domain, String name, List<Setting> settings, List<Module.SettingsButton> buttons) {
		public ItemStack icon() {
			return new ItemStack(this.settings.isEmpty() ? this.domain.icon : this.settings.getFirst().icon);
		}
	}

	/** The groups of one domain, in order. */
	public static List<Section> sections(Domain d) {
		Map<String, Section> out = new LinkedHashMap<>();
		for (Setting s : Module.allSettings()) {
			// What the server decided for everybody isn't a player's to change, so it isn't shown
			if (s.domain() != d || s.isForced() || !s.module.on()) continue;
			out.computeIfAbsent(s.section(), k -> new Section(d, k, new ArrayList<>(), new ArrayList<>())).settings().add(s);
		}
		for (Module m : Module.all()) {
			if (m.domain() != d || m.settingsButtons().isEmpty() || !m.on()) continue;
			String name = m.settings().stream().filter(s -> s.domain() == d).map(Setting::section).findFirst().orElse(m.name);
			out.computeIfAbsent(name, k -> new Section(d, k, new ArrayList<>(), new ArrayList<>())).buttons().addAll(m.settingsButtons());
		}
		List<Section> list = new ArrayList<>(out.values());
		list.sort((a, b) -> Integer.compare(rank(a.name()), rank(b.name())));
		return list;
	}

	private static int rank(String section) {
		int i = ORDER.indexOf(section);
		return i < 0 ? ORDER.size() : i;
	}

	/** Does this domain have any settings at all? */
	public static boolean has(Domain d) {
		return !sections(d).isEmpty();
	}

	// ---------------------------------------------------------------- screens

	/** Settings: the three domains (and for operators, the server). */
	public static void open(ServerPlayer player) {
		if (CanvasWindow.available(player)) {
			SettingsCanvas.open(player);
			return;
		}
		Window w = Window.of(Ui.text("Settings", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD))
				.text(Ui.text("Only for you: nothing here changes anything for other players.", ChatFormatting.GRAY));
		for (Domain d : Domain.values()) {
			List<Section> sections = sections(d);
			if (sections.isEmpty()) continue;
			w.button(d.title(), Ui.text(String.join(" · ", sections.stream().map(Section::name).toList()), ChatFormatting.GRAY),
					(p, v) -> open(p, d, SettingsWindow::open));
		}
		if (Ui.isOp(player)) {
			w.button(Ui.text("Server settings", ChatFormatting.GOLD), Ui.text("Operators: house rules for everybody, links, leaderboards.",
					ChatFormatting.GRAY), (p, v) -> AdminWindow.open(p));
		}
		w.back(Start::open).show(player);
	}

	/** One domain's settings: its groups, or straight the checkboxes when there is only one. */
	public static void open(ServerPlayer player, Domain d, Consumer<ServerPlayer> back) {
		if (CanvasWindow.available(player)) {
			SettingsCanvas.open(player, d, back);
			return;
		}
		List<Section> sections = sections(d);
		if (sections.size() == 1) {
			open(player, sections.getFirst(), back);
			return;
		}
		Window w = Window.of(d, "Settings");
		w.text(Ui.text("Only for you: nothing here changes anything for other players.", ChatFormatting.GRAY));
		for (Section s : sections) {
			List<String> labels = new ArrayList<>(s.settings().stream().map(x -> x.label).toList());
			for (Module.SettingsButton b : s.buttons()) labels.add(b.label());
			w.button(Ui.text(s.name(), ChatFormatting.WHITE), Ui.text(String.join(" · ", labels), ChatFormatting.GRAY),
					(p, v) -> open(p, s, q -> open(q, d, back)));
		}
		w.back(back).show(player);
	}

	/** The settings window a module's own screens link to (e.g. "Settings" in Your graves). */
	public static void open(ServerPlayer player, Module module) {
		Domain d = module.domain();
		if (CanvasWindow.available(player)) {
			SettingsCanvas.open(player, d, p -> Start.domain(p, d));
			return;
		}
		for (Section s : sections(d)) {
			for (Setting x : s.settings()) {
				if (x.module == module) {
					open(player, s, p -> open(p, d, q -> Start.domain(q, d)));
					return;
				}
			}
		}
		open(player, d, p -> Start.domain(p, d));
	}

	/** One group: a checkbox or option button per setting, then Save. */
	public static void open(ServerPlayer player, Section section, Consumer<ServerPlayer> back) {
		Window w = Window.of(section.domain(), "Settings", section.name());
		for (Setting s : section.settings()) {
			Component label = Ui.text(s.label, ChatFormatting.WHITE);
			if (s instanceof Setting.Toggle t) {
				w.toggle(input(s), label, t.get(player));
			} else if (s instanceof Setting.Choice c) {
				List<String> ids = new ArrayList<>();
				List<Component> names = new ArrayList<>();
				for (int i = 0; i < c.size(); i++) {
					ids.add(Integer.toString(c.valueAt(i)));
					names.add(Ui.text(c.labelAt(i), ChatFormatting.AQUA));
				}
				w.choice(input(s), label, ids, names, Integer.toString(c.get(player)));
			}
		}
		if (!section.settings().isEmpty()) {
			w.button(Ui.text("✔ Save", ChatFormatting.GREEN), Ui.text("Keep these settings", ChatFormatting.GRAY), (p, v) -> {
				saved(p, section, apply(p, section, v), true);
				back.accept(p);
			});
			w.button(Ui.text("Defaults", ChatFormatting.YELLOW), Ui.text("Back to how they were at the start", ChatFormatting.GRAY),
					(p, v) -> {
						for (Setting s : section.settings()) {
							if (s instanceof Setting.Toggle t) t.set(p, t.start());
							else if (s instanceof Setting.Choice c) c.set(p, c.defaultValue());
						}
						nl.libreria.core.Notify.whisper(p, "Back to the defaults", ChatFormatting.GRAY);
						open(p, section, back);
					});
			w.button(Ui.text("What do these do?", ChatFormatting.WHITE), Ui.text("One line per setting", ChatFormatting.GRAY),
					(p, v) -> {
						saved(p, section, apply(p, section, v), false);
						help(p, section, back);
					});
		}
		for (Module.SettingsButton b : section.buttons()) {
			w.button(Ui.text(b.label() + " ▸", ChatFormatting.AQUA), Ui.text(b.tooltip(), ChatFormatting.GRAY), (p, v) -> {
				saved(p, section, apply(p, section, v), false);
				b.action().accept(p);
			});
		}
		// Back (and Escape) keep what you changed too: nothing is lost by not pressing Save
		w.bottom(Ui.text("◀ Back", ChatFormatting.WHITE), (p, v) -> {
			saved(p, section, apply(p, section, v), false);
			back.accept(p);
		}).show(player);
	}

	/** The card after saving; {@code always}: also when nothing changed (the Save button). */
	private static void saved(ServerPlayer p, Section section, int changed, boolean always) {
		if (changed == 0 && !always) return;
		Ui.sound(p, SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, 1.4f);
		Toast.show(p, section.icon(), Toast.GOOD, Ui.text(changed == 0 ? "Nothing changed" : "Settings saved", ChatFormatting.WHITE),
				Ui.text(section.name() + (changed == 0 ? "" : " · " + changed + (changed == 1 ? " change" : " changes")), ChatFormatting.GRAY));
	}

	/** What each setting of a group does. */
	static void help(ServerPlayer player, Section section, Consumer<ServerPlayer> back) {
		Window w = Window.of(section.domain(), "Settings", section.name(), "Help");
		for (Setting s : section.settings()) w.text(explain(s));
		w.back(p -> open(p, section, back)).show(player);
	}

	/** "Label: what it does" in one line. */
	private static Component explain(Setting s) {
		MutableComponent c = Ui.text(s.label, ChatFormatting.WHITE);
		if (!s.lore.isEmpty()) c.append(Ui.text(" – " + String.join(" ", s.lore), ChatFormatting.GRAY));
		return c;
	}

	/** The field name in the window: unique across modules (two modules may both have a "refill"). */
	private static String input(Setting s) {
		return s.module.id + "_" + s.key;
	}

	/** Stores what was filled in; returns how many settings changed. */
	static int apply(ServerPlayer p, Section section, Values v) {
		int changed = 0;
		for (Setting s : section.settings()) {
			String key = input(s);
			if (!v.has(key)) continue;
			if (s instanceof Setting.Toggle t) {
				boolean now = v.bool(key, t.get(p));
				if (now != t.get(p)) {
					t.set(p, now);
					changed++;
				}
			} else if (s instanceof Setting.Choice c) {
				int value;
				try {
					value = Integer.parseInt(v.text(key, ""));
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
