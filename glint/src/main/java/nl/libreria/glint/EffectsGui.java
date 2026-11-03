package nl.libreria.glint;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.HubGui;
import nl.libreria.core.gui.SettingsGui;

/** Per effect aan/uit: geldt voor de plaatjes én de deeltjes die jij ziet. */
final class EffectsGui extends SimpleGui {
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

	private EffectsGui(ServerPlayer p) {
		super(MenuType.GENERIC_9x6, p, false);
		this.setTitle(Component.literal("Glint – effects"));
		build();
	}

	static void open(ServerPlayer p) {
		new EffectsGui(p).open();
	}

	private void build() {
		ServerPlayer p = this.getPlayer();
		int slot = 0;
		for (GlintMod.Family f : GlintMod.FAMILIES.values()) {
			if (slot >= 45) break;
			boolean on = GlintMod.enabled(p, f.id());
			Item icon = Ui.item(ICONS.getOrDefault(f.id(), "enchanted_book"));
			GuiElementBuilder b = new GuiElementBuilder(icon)
					.setName(Component.literal((on ? "✔ " : "✘ ") + f.name()).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY))
					.addLoreLine(Ui.text(f.enchants(), ChatFormatting.LIGHT_PURPLE))
					.addLoreLine(Ui.text(switch (f.group()) {
						case "A" -> "Around the item";
						case "B" -> "Overlay on the item";
						default -> "Floating particles";
					}, ChatFormatting.DARK_GRAY))
					.addLoreLine(Ui.text(on ? "Click: turn off" : "Click: turn on", ChatFormatting.AQUA))
					.hideDefaultTooltip();
			if (on) b.glow();
			b.setCallback((i, t, a, g) -> {
				GlintMod.setEnabled(p, f.id(), !on);
				Ui.click(p);
				open(p);
			});
			this.setSlot(slot++, b);
		}
		this.setSlot(45, Ui.button(Items.ARROW, "Back to Libreria", ChatFormatting.GRAY).setCallback((i, t, a, g) -> HubGui.open(p)));
		this.setSlot(47, Ui.button(Ui.item("lime_dye"), "All on", ChatFormatting.GREEN)
				.setCallback((i, t, a, g) -> {
					for (String f : GlintMod.FAMILIES.keySet()) GlintMod.setEnabled(p, f, true);
					open(p);
				}));
		this.setSlot(48, Ui.button(Ui.item("gray_dye"), "All off", ChatFormatting.GRAY)
				.setCallback((i, t, a, g) -> {
					for (String f : GlintMod.FAMILIES.keySet()) GlintMod.setEnabled(p, f, false);
					open(p);
				}));
		this.setSlot(50, Ui.button(Items.COMPARATOR, "Pack, books and particles", ChatFormatting.WHITE)
				.addLoreLine(Ui.text("Settings → Glint", ChatFormatting.GRAY))
				.setCallback((i, t, a, g) -> SettingsGui.open(p)));
		this.setSlot(53, Ui.button(Items.BOOK, "How it works", ChatFormatting.YELLOW)
				.addLoreLine(Ui.text("Effects show on enchanted weapons,", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("tools and armor (in your inventory", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("and in your hand), plus particles", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("around players.", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("Switching briefly reloads your screen.", ChatFormatting.DARK_GRAY)));
	}
}
