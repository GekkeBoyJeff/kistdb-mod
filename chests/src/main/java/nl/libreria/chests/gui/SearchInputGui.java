package nl.libreria.chests.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import nl.libreria.chests.Search;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Menu;

/**
 * The search field (an anvil screen): type part of a name or ID, then click the spyglass on the right. The spyglass
 * shows live how many kinds of items match. Anvil screens can't use the Menu layout, so this one stays minimal.
 */
final class SearchInputGui extends AnvilInputGui {
	private final ServerPlayer viewer;
	private final String previous;
	private final OverviewGui.Sort sort;
	/** Everything in your chests nearby, read once, so the live match count is cheap while typing. */
	private final List<Search.Agg> all;

	private SearchInputGui(ServerPlayer player, String current, OverviewGui.Sort sort) {
		super(player, false);
		this.viewer = player;
		this.previous = current == null ? "" : current;
		this.sort = sort;
		this.all = Search.aggregate(player, "", false);
		this.setTitle(Component.literal("Chests · Search"));
		this.setDefaultInputValue(this.previous);
		this.setSlot(1, Menu.actions(Menu.button(Items.BARRIER, "✖ Cancel", ChatFormatting.RED,
				"Back to the chest index,", "without changing the search."), "Click: go back")
				.setCallback((index, type, action, gui) -> {
					Ui.click(player);
					OverviewGui.open(player, this.previous, this.sort, 0);
				}));
		this.setSlot(2, output(this.previous));
	}

	static void open(ServerPlayer player, String current, OverviewGui.Sort sort) {
		new SearchInputGui(player, current, sort).open();
	}

	@Override
	public void onInput(String input) {
		this.setSlot(2, output(input));
	}

	/** The spyglass on the right: what clicking it will search for, and how many kinds match. */
	private GuiElementBuilder output(String input) {
		String text = input == null ? "" : input.trim();
		GuiElementBuilder b;
		if (text.isEmpty()) {
			b = Menu.button(Items.SPYGLASS, "Show everything", ChatFormatting.GREEN,
					"Type part of a name or ID on the", "left, like diamond, oak or iron.",
					"", Fmt.count(this.all.size(), "kind", "kinds") + " in your chests");
			Menu.actions(b, "Click: show all");
		} else {
			String lower = text.toLowerCase(Locale.ROOT);
			long matches = this.all.stream().filter(a -> Search.matches(a.item(), lower)).count();
			b = Menu.button(Items.SPYGLASS, "Search \"" + text + "\"", matches > 0 ? ChatFormatting.GREEN : ChatFormatting.RED,
					matches > 0 ? Fmt.count(matches, "kind matches", "kinds match") : "Nothing matches yet");
			Menu.actions(b, "Click: show results");
		}
		return b.setCallback((index, type, action, gui) -> {
			Ui.click(this.viewer);
			OverviewGui.open(this.viewer, this.getInput(), this.sort, 0);
		});
	}
}
