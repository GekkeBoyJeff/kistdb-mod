package nl.handig.glans;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import nl.handig.kern.Ui;
import nl.handig.kern.gui.HubGui;
import nl.handig.kern.gui.SettingsGui;

/** Per effect aan/uit: geldt voor de plaatjes én de deeltjes die jij ziet. */
final class EffectsGui extends SimpleGui {
	private static final Map<String, String> ICONS = Map.ofEntries(
			Map.entry("vuur", "blaze_powder"), Map.entry("bliksem", "lightning_rod"), Map.entry("ijs", "blue_ice"),
			Map.entry("ziel", "soul_lantern"), Map.entry("heilig", "glowstone_dust"), Map.entry("gif", "spider_eye"),
			Map.entry("getij", "nautilus_shell"), Map.entry("water", "water_bucket"), Map.entry("sculk", "sculk"),
			Map.entry("explosie", "tnt"), Map.entry("zwaai", "iron_sword"), Map.entry("veer", "feather"),
			Map.entry("trouw", "lead"), Map.entry("scherp", "flint"), Map.entry("spiets", "prismarine_shard"),
			Map.entry("zwaar", "heavy_core"), Map.entry("breuk", "cracked_stone_bricks"), Map.entry("kracht", "redstone"),
			Map.entry("drievoud", "arrow"), Map.entry("snel", "sugar"), Map.entry("wind", "wind_charge"),
			Map.entry("oneindig", "nether_star"), Map.entry("doornen", "cactus"), Map.entry("vuurschild", "magma_cream"),
			Map.entry("pijlschild", "spectral_arrow"), Map.entry("schild", "shield"), Map.entry("zijde", "string"),
			Map.entry("stevig", "iron_ingot"), Map.entry("vloek", "wither_rose"), Map.entry("geluk", "emerald"),
			Map.entry("zee", "fishing_rod"), Map.entry("herstel", "experience_bottle"));

	private EffectsGui(ServerPlayer p) {
		super(MenuType.GENERIC_9x6, p, false);
		this.setTitle(Component.literal("Glans – effecten"));
		build();
	}

	static void open(ServerPlayer p) {
		new EffectsGui(p).open();
	}

	private void build() {
		ServerPlayer p = this.getPlayer();
		int slot = 0;
		for (Glans.Family f : Glans.FAMILIES.values()) {
			if (slot >= 45) break;
			boolean on = Glans.enabled(p, f.id());
			Item icon = Ui.item(ICONS.getOrDefault(f.id(), "enchanted_book"));
			GuiElementBuilder b = new GuiElementBuilder(icon)
					.setName(Component.literal((on ? "✔ " : "✘ ") + f.name()).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY))
					.addLoreLine(Ui.text(f.enchants(), ChatFormatting.LIGHT_PURPLE))
					.addLoreLine(Ui.text(switch (f.group()) {
						case "A" -> "Rondom het item";
						case "B" -> "Over het item heen";
						default -> "Losse deeltjes";
					}, ChatFormatting.DARK_GRAY))
					.addLoreLine(Ui.text(on ? "Klik: uitzetten" : "Klik: aanzetten", ChatFormatting.AQUA))
					.hideDefaultTooltip();
			if (on) b.glow();
			b.setCallback((i, t, a, g) -> {
				Glans.setEnabled(p, f.id(), !on);
				Ui.click(p);
				open(p);
			});
			this.setSlot(slot++, b);
		}
		this.setSlot(45, Ui.button(Items.ARROW, "Terug naar Handig", ChatFormatting.GRAY).setCallback((i, t, a, g) -> HubGui.open(p)));
		this.setSlot(47, Ui.button(Ui.item("lime_dye"), "Alles aan", ChatFormatting.GREEN)
				.setCallback((i, t, a, g) -> {
					for (String f : Glans.FAMILIES.keySet()) Glans.setEnabled(p, f, true);
					open(p);
				}));
		this.setSlot(48, Ui.button(Ui.item("gray_dye"), "Alles uit", ChatFormatting.GRAY)
				.setCallback((i, t, a, g) -> {
					for (String f : Glans.FAMILIES.keySet()) Glans.setEnabled(p, f, false);
					open(p);
				}));
		this.setSlot(50, Ui.button(Items.COMPARATOR, "Pakket, boeken en deeltjes", ChatFormatting.WHITE)
				.addLoreLine(Ui.text("Instellingen → Glans", ChatFormatting.GRAY))
				.setCallback((i, t, a, g) -> SettingsGui.open(p)));
		this.setSlot(53, Ui.button(Items.BOOK, "Uitleg", ChatFormatting.YELLOW)
				.addLoreLine(Ui.text("Effecten verschijnen op betoverde wapens,", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("gereedschap en harnas (in je inventory en", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("in je hand), plus deeltjes rond spelers.", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("Wisselen laadt je scherm heel even opnieuw.", ChatFormatting.DARK_GRAY)));
	}
}
