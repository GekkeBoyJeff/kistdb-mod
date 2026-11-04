package nl.libreria.glint;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import nl.libreria.core.Setting;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Menu;

/**
 * Per effect on/off: applies to the textures and the particles you see. One row per group (with a coloured label on
 * the left that switches the whole group), the effects to the right of it, each with a clear ✔/✘ state.
 */
final class EffectsGui extends Menu {
	private static final Map<String, String> ICONS = Map.ofEntries(
			Map.entry("fire", "blaze_powder"), Map.entry("lightning", "lightning_rod"), Map.entry("frost", "blue_ice"),
			Map.entry("soul", "soul_lantern"), Map.entry("holy", "glowstone_dust"), Map.entry("poison", "spider_eye"),
			Map.entry("tide", "nautilus_shell"), Map.entry("water", "water_bucket"), Map.entry("sculk", "sculk"),
			Map.entry("blast", "tnt"), Map.entry("sweep", "iron_sword"), Map.entry("feather", "feather"),
			Map.entry("loyalty", "lead"), Map.entry("sharp", "flint"), Map.entry("impale", "prismarine_shard"),
			Map.entry("heavy", "heavy_core"), Map.entry("breach", "cracked_stone_bricks"), Map.entry("power", "redstone"),
			Map.entry("multishot", "arrow"), Map.entry("haste", "sugar"), Map.entry("wind", "wind_charge"),
			Map.entry("infinity", "nether_star"), Map.entry("thorns", "cactus"), Map.entry("fireguard", "magma_cream"),
			Map.entry("arrowguard", "spectral_arrow"), Map.entry("protection", "shield"), Map.entry("silk", "string"),
			Map.entry("sturdy", "iron_ingot"), Map.entry("curse", "wither_rose"), Map.entry("luck", "emerald"),
			Map.entry("sea", "fishing_rod"), Map.entry("mending", "experience_bottle"));

	/** A section of effects ({@link GlintMod.Family#group()}: A, B, or anything else = C). */
	private record Group(String label, String pane, ChatFormatting color, List<String> about) {
	}

	private static final List<Group> GROUPS = List.of(
			new Group("Around the item", "orange", ChatFormatting.GOLD, List.of("Drawn around the edge", "of your enchanted items.")),
			new Group("On the item", "light_blue", ChatFormatting.AQUA, List.of("Drawn on top of your", "enchanted items.")),
			new Group("Particles", "purple", ChatFormatting.LIGHT_PURPLE, List.of("Small particles floating", "on your enchanted items.")));

	/** One row of the menu: a group label and up to 8 of its effects. */
	private record Line(int group, List<GlintMod.Family> families) {
	}

	private static final int PER_LINE = 8;

	private int page;

	private EffectsGui(ServerPlayer p) {
		super(p, 6, "Choose effects");
		rebuild();
	}

	static void open(ServerPlayer p) {
		new EffectsGui(p).open();
	}

	private static int groupOf(GlintMod.Family f) {
		return switch (f.group()) {
			case "A" -> 0;
			case "B" -> 1;
			default -> 2;
		};
	}

	private static List<GlintMod.Family> familiesOf(int group) {
		List<GlintMod.Family> out = new ArrayList<>();
		for (GlintMod.Family f : GlintMod.FAMILIES.values()) if (groupOf(f) == group) out.add(f);
		return out;
	}

	private static List<Line> lines() {
		List<Line> out = new ArrayList<>();
		for (int g = 0; g < GROUPS.size(); g++) {
			List<GlintMod.Family> fams = familiesOf(g);
			for (int i = 0; i < fams.size(); i += PER_LINE) out.add(new Line(g, fams.subList(i, Math.min(fams.size(), i + PER_LINE))));
		}
		return out;
	}

	private void rebuild() {
		clearContent();
		for (int i = navRow(); i < navRow() + 9; i++) this.clearSlot(i);

		List<Line> lines = lines();
		int perPage = this.rows - 1;
		int pages = Math.max(1, (lines.size() + perPage - 1) / perPage);
		this.page = Math.max(0, Math.min(this.page, pages - 1));
		for (int r = 0; r < perPage; r++) {
			int idx = this.page * perPage + r;
			if (idx >= lines.size()) break;
			Line line = lines.get(idx);
			this.setSlot(r * 9, groupHeader(line.group()));
			for (int i = 0; i < line.families().size(); i++) this.setSlot(r * 9 + 1 + i, effect(line.families().get(i)));
		}

		// Navigation bar: Back · Effect settings · (pages) · Close · All on · All off · Help
		back("Effects", () -> EffectsWindow.open(this.player));
		GuiElementBuilder settings = button(Items.COMPARATOR, "Effect settings", ChatFormatting.WHITE,
				"Textures, book covers and", "particles around players.");
		if (!nl.libreria.core.Pack.has(this.player)) {
			settings.addLoreLine(Component.empty());
			settings.addLoreLine(Ui.text("✘ The Libreria resource pack is off,", ChatFormatting.RED));
			settings.addLoreLine(Ui.text("so effects on items don't show.", ChatFormatting.RED));
			settings.addLoreLine(Ui.text("(Settings › You › What you see)", ChatFormatting.GRAY));
		}
		actions(settings, "Click: open");
		this.setSlot(navRow() + 1, settings.setCallback((i, t, a, g) -> {
			Ui.click(this.player);
			new GlintSettings(this.player).open();
		}));
		pages(this.page, pages, p -> {
			this.page = p;
			rebuild();
		});
		closeButton();
		this.setSlot(navRow() + 6, actions(button(Ui.item("lime_dye"), "All on", ChatFormatting.GREEN,
				"Turn every effect on."), "Click: all on")
				.setCallback((i, t, a, g) -> setAll(new ArrayList<>(GlintMod.FAMILIES.values()), true)));
		this.setSlot(navRow() + 7, actions(button(Ui.item("gray_dye"), "All off", ChatFormatting.GRAY,
				"Turn every effect off."), "Click: all off")
				.setCallback((i, t, a, g) -> setAll(new ArrayList<>(GlintMod.FAMILIES.values()), false)));
		help("Effects show on enchanted weapons,",
				"tools and armor, in your inventory",
				"and in your hand. Click an effect,",
				"or a group label on the left, to",
				"switch it. Only you are affected;",
				"your screen briefly reloads.");
		finish();
	}

	private GuiElementBuilder groupHeader(int group) {
		Group gr = GROUPS.get(group);
		List<GlintMod.Family> fams = familiesOf(group);
		long on = fams.stream().filter(f -> GlintMod.enabled(this.player, f.id())).count();
		GuiElementBuilder b = header(gr.pane(), gr.label(), gr.color());
		for (String l : gr.about()) b.addLoreLine(Ui.text(l, ChatFormatting.GRAY));
		b.addLoreLine(Component.empty());
		b.addLoreLine(Ui.kv("On: ", on + " of " + fams.size(),
				on == fams.size() ? ChatFormatting.GREEN : on == 0 ? ChatFormatting.RED : ChatFormatting.YELLOW));
		actions(b, "Click: turn all on", "Right-click: turn all off");
		return b.setCallback((i, type, a, g) -> setAll(fams, !secondary(type)));
	}

	private GuiElementBuilder effect(GlintMod.Family f) {
		boolean on = GlintMod.enabled(this.player, f.id());
		Item icon = Ui.item(ICONS.getOrDefault(f.id(), "enchanted_book"));
		GuiElementBuilder b = button(icon, (on ? "✔ " : "✘ ") + f.name(), on ? ChatFormatting.GREEN : ChatFormatting.RED);
		for (String l : wrap(f.enchants())) b.addLoreLine(Ui.text(l, ChatFormatting.LIGHT_PURPLE));
		b.addLoreLine(Component.empty());
		b.addLoreLine(on ? Ui.text("✔ On – you see it", ChatFormatting.GREEN) : Ui.text("✘ Off – hidden", ChatFormatting.RED));
		action(b, "Click: turn " + (on ? "off" : "on"));
		if (on) b.glow();
		return b.setCallback((i, t, a, g) -> {
			GlintMod.setEnabled(this.player, f.id(), !on);
			Ui.sound(this.player, SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, on ? 0.7f : 1.4f);
			rebuild();
		});
	}

	private void setAll(List<GlintMod.Family> families, boolean on) {
		for (GlintMod.Family f : families) GlintMod.setEnabled(this.player, f.id(), on);
		Ui.sound(this.player, SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, on ? 1.4f : 0.7f);
		rebuild();
	}

	/** "Respiration, Aqua Affinity, Depth Strider" → two short lines. */
	private static List<String> wrap(String text) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String part : text.split(", ")) {
			if (!line.isEmpty() && line.length() + 2 + part.length() > 30) {
				out.add(line + ",");
				line = new StringBuilder();
			}
			if (!line.isEmpty()) line.append(", ");
			line.append(part);
		}
		if (!line.isEmpty()) out.add(line.toString());
		return out;
	}

	/** The Glint settings (same switches as Libreria settings → Glint), with Back to the effects. */
	private static final class GlintSettings extends Menu {
		GlintSettings(ServerPlayer p) {
			super(p, 4, "Effect settings");
			rebuild();
		}

		private void rebuild() {
			clearContent();
			List<Setting> settings = GlintMod.MODULE.settings();
			int[] slots = row(1, Math.min(7, settings.size()));
			for (int i = 0; i < slots.length; i++) setting(slots[i], settings.get(i), this::rebuild);
			back("Choose effects", () -> EffectsGui.open(this.player));
			closeButton();
			help("Click a setting or the lamp below it",
					"to change it. Changes apply at once.",
					"Also in Settings › You › Effects.");
			finish();
		}
	}
}
