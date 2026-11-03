package nl.libreria.chests.gui;

import nl.libreria.core.Ui;

import eu.pb4.sgui.api.gui.AnvilInputGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Zoekveld via het aambeeld-scherm: typ een naam of ID en klik op het resultaat. */
final class SearchInputGui extends AnvilInputGui {
	private SearchInputGui(ServerPlayer player, String current) {
		super(player, false);
		this.setTitle(Component.literal("Search item (name or ID)"));
		this.setDefaultInputValue(current == null ? "" : current);
		this.setSlot(1, Ui.button(Items.BARRIER, "Cancel", ChatFormatting.RED)
				.setCallback((index, type, action, gui) -> OverviewGui.open(player, ""))
				.build());
		this.setSlot(2, Ui.button(Items.SPYGLASS, "Search", ChatFormatting.GREEN)
				.addLoreLine(Ui.text("Item name or ID, e.g. diamond, oak, iron", ChatFormatting.GRAY))
				.setCallback((index, type, action, gui) -> OverviewGui.open(player, this.getInput()))
				.build());
	}

	static void open(ServerPlayer player, String current) {
		new SearchInputGui(player, current).open();
	}
}
