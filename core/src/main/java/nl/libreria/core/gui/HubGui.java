package nl.libreria.core.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Items;
import nl.libreria.core.Guide;
import nl.libreria.core.Module;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;

/**
 * The Libreria menu. One row per category (items, world, progress, you) with a coloured label on the left, your
 * player card at the top, and Settings / Close / Help at the bottom.
 */
public final class HubGui extends Menu {
	private HubGui(ServerPlayer player) {
		super(player, 6, "Libreria");
		build();
	}

	public static void open(ServerPlayer player) {
		new HubGui(player).open();
	}

	private void build() {
		// Header: your own player card
		GuiElementBuilder head = new GuiElementBuilder(Items.PLAYER_HEAD).setProfile(this.player.getGameProfile())
				.setName(title(this.player.getName().getString(), ChatFormatting.GOLD)).hideDefaultTooltip();
		int hours = this.player.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME) / 72000;
		head.addLoreLine(Ui.kv("Playtime: ", hours + (hours == 1 ? " hour" : " hours"), ChatFormatting.WHITE));
		Services.progress().ifPresent(p -> p.card(this.player).forEach(head::addLoreLine));
		this.setSlot(4, head);

		// One row per category
		Module.Category[] cats = Module.Category.values();
		int row = 1;
		for (Module.Category cat : cats) {
			List<Module.HubEntry> entries = new ArrayList<>();
			for (Module.HubEntry e : Module.allHubEntries()) if (e.category() == cat) entries.add(e);
			if (entries.isEmpty() || row > 4) continue;
			this.setSlot(row * 9, header(cat.pane, cat.label, cat.color));
			this.setSlot(row * 9 + 8, header(cat.pane, cat.label, cat.color));
			int[] slots = row(row, entries.size());
			for (int i = 0; i < slots.length; i++) {
				Module.HubEntry e = entries.get(i);
				GuiElementBuilder b = button(e.icon(), e.name(), e.color(), e.lore().toArray(String[]::new));
				actions(b, "Click: open");
				b.setCallback((index, type, action, gui) -> {
					Ui.click(this.player);
					e.action().accept(this.player);
				});
				this.setSlot(slots[i], b);
			}
			row++;
		}

		// Bottom bar
		if (Guide.has(this.player)) {
			this.setSlot(navRow(), action(button(Items.BARRIER, "Stop beam", ChatFormatting.RED, "Removes the beam you are following."),
					"Click: stop").setCallback((i, t, a, g) -> {
						Guide.clear(this.player);
						Ui.click(this.player);
						this.close();
					}));
		}
		this.setSlot(navRow() + 2, actions(button(Items.COMPARATOR, "Settings", ChatFormatting.WHITE,
				"Turn features on or off and", "tweak them, per module.", "", "Also: /libreria settings"), "Click: open")
				.setCallback((i, t, a, g) -> {
					Ui.click(this.player);
					SettingsGui.open(this.player);
				}));
		closeButton();
		List<String> help = new ArrayList<>(List.of(
				"The glowing icon in the crafting slot",
				"of your inventory (E):",
				"  left-click = sort your inventory",
				"  right-click = this menu",
				"",
				"Commands:",
				"  /libreria (or /lib) – this menu",
				"  /libreria settings – settings"));
		for (Module m : Module.all()) for (String c : m.commandHelp()) help.add("  " + c);
		help(help.toArray(String[]::new));
		finish();
	}
}
