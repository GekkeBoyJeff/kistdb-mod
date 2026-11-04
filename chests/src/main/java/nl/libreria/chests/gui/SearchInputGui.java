package nl.libreria.chests.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import java.util.ArrayList;
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
 * The live search: type in the field at the top and the matches fill the slots below while you type (where your
 * own inventory normally is), most first. Click one: the menu closes and its nearest chest lights up. The spyglass on
 * the right shows every match in the big "Your items" window.
 */
final class SearchInputGui extends AnvilInputGui {
	/** The anvil's own three slots come first, then the 36 slots of the player inventory area. */
	private static final int RESULTS = 3, RESULT_SLOTS = 36;

	private final ServerPlayer viewer;
	private final String previous;
	private final OverviewGui.Sort sort;
	/** Everything on you and in your chests nearby, read once, so typing stays instant. */
	private final List<Search.Stock> all;
	private String shown = "\u0000";

	private SearchInputGui(ServerPlayer player, String current, OverviewGui.Sort sort) {
		super(player, true);
		this.viewer = player;
		this.previous = current == null ? "" : current;
		this.sort = sort;
		this.all = OverviewGui.sorted(Search.stock(player, ""), sort);
		this.setTitle(Component.literal("Search your items"));
		this.setDefaultInputValue(this.previous);
		this.setSlot(1, Menu.actions(Menu.button(Items.BARRIER, "Back", ChatFormatting.RED,
				"Back to your items,", "without changing the search."), "Click: go back")
				.setCallback((index, type, action, gui) -> {
					Ui.click(player);
					OverviewGui.open(player, this.previous, this.sort, 0);
				}));
		render(this.previous);
	}

	static void open(ServerPlayer player, String current, OverviewGui.Sort sort) {
		new SearchInputGui(player, current, sort).open();
	}

	@Override
	public void onInput(String input) {
		render(input);
	}

	/** The spyglass on the right and the matches below, for what's typed now. */
	private void render(String input) {
		String text = input == null ? "" : input.trim();
		if (text.equals(this.shown)) return;
		this.shown = text;
		String lower = text.toLowerCase(Locale.ROOT);
		List<Search.Stock> matches = new ArrayList<>();
		for (Search.Stock s : this.all) {
			if (lower.isEmpty() || Search.matches(s.item(), lower)) matches.add(s);
		}
		GuiElementBuilder out;
		if (text.isEmpty()) {
			out = Menu.button(Items.SPYGLASS, "Show everything", ChatFormatting.GREEN,
					"Type part of a name at the top,", "like diamond, oak or iron.", "",
					Fmt.count(this.all.size(), "kind", "kinds") + " on you and in your chests");
			Menu.actions(out, "Click: open the full list");
		} else {
			out = Menu.button(Items.SPYGLASS, "“" + text + "”", matches.isEmpty() ? ChatFormatting.RED : ChatFormatting.GREEN,
					matches.isEmpty() ? "Nothing matches yet" : Fmt.count(matches.size(), "kind matches", "kinds match"));
			if (!matches.isEmpty()) Menu.actions(out, "Click: show them in the full list");
		}
		this.setSlot(2, out.setCallback((index, type, action, gui) -> {
			Ui.click(this.viewer);
			OverviewGui.open(this.viewer, this.getInput(), this.sort, 0);
		}));
		for (int i = 0; i < RESULT_SLOTS; i++) {
			if (i < matches.size()) {
				this.setSlot(RESULTS + i, OverviewGui.element(this.viewer, matches.get(i), "your search", this::reopen));
			} else {
				this.clearSlot(RESULTS + i);
			}
		}
	}

	private void reopen() {
		open(this.viewer, this.getInput(), this.sort);
	}
}
