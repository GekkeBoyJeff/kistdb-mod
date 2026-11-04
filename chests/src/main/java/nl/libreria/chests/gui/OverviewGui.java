package nl.libreria.chests.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import nl.libreria.chests.Search;
import nl.libreria.chests.Sorter;
import nl.libreria.chests.Store;
import nl.libreria.core.Guide;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.HubGui;
import nl.libreria.core.gui.Menu;

/**
 * "Your items": everything you carry and everything in your chests nearby, as one wall of items with their real
 * totals (200 oak logs show 200). A toolbar on top (summary, sort order, search, my chests, sort inventory), the
 * items below, the navigation bar at the bottom. Click an item: the menu closes and the nearest chest with it lights
 * up, with a beam to it. Right-click: every chest that has it. The magnifying glass in your inventory opens this.
 */
public final class OverviewGui extends Menu {
	/** Sort orders. */
	enum Sort {
		MOST("Most first"),
		NAME("A–Z"),
		NEAREST("Nearest first");

		final String label;

		Sort(String label) {
			this.label = label;
		}

		Sort step(int step) {
			Sort[] all = values();
			return all[Math.floorMod(ordinal() + step, all.length)];
		}
	}

	private static final int FIRST = 9;
	private static final int PER_PAGE = 36;
	/** A slot shows at most this many (the real number is in the tooltip). */
	static final int MAX_SHOWN = 999;
	/** The sort order a player picked last, so the menu opens the way they like it. */
	private static final Map<UUID, Sort> LAST_SORT = new HashMap<>();

	private final String filter;
	private Sort sort;
	private int page;

	private OverviewGui(ServerPlayer player, String filter, Sort sort, int page) {
		super(player, 6, filter.isEmpty() ? "Your items" : "Your items · “" + filter + "”");
		this.filter = filter;
		this.sort = sort;
		this.page = page;
		rebuild();
	}

	/** Opens your items, optionally already filtered ("" = everything). */
	public static void open(ServerPlayer player, String filter) {
		open(player, filter, LAST_SORT.getOrDefault(player.getUUID(), Sort.MOST), 0);
	}

	static void open(ServerPlayer player, String filter, Sort sort, int page) {
		new OverviewGui(player, filter == null ? "" : filter.trim(), sort, page).open();
	}

	/** The live search, already typed (for the screenshot test and /chest find). */
	public static void openSearch(ServerPlayer player, String typed) {
		SearchInputGui.open(player, typed, LAST_SORT.getOrDefault(player.getUUID(), Sort.MOST));
	}

	static List<Search.Stock> sorted(List<Search.Stock> list, Sort sort) {
		Comparator<Search.Stock> byName = Comparator.comparing(s -> s.sample().getHoverName().getString().toLowerCase(Locale.ROOT));
		switch (sort) {
			case MOST -> list.sort(Comparator.comparingLong(Search.Stock::total).reversed().thenComparing(byName));
			case NAME -> list.sort(byName);
			case NEAREST -> list.sort(Comparator.comparingDouble(Search.Stock::nearest).thenComparing(byName));
		}
		return list;
	}

	private void rebuild() {
		clearContent();
		Fmt.clearRow(this, this.rows - 1);

		List<Search.Stock> list = sorted(Search.stock(this.player, this.filter), this.sort);
		int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
		this.page = Math.max(0, Math.min(this.page, pages - 1));

		toolbar(list);
		for (int i = 0; i < PER_PAGE; i++) {
			int idx = this.page * PER_PAGE + i;
			if (idx >= list.size()) break;
			this.setSlot(FIRST + i, element(this.player, list.get(idx), this.filter.isEmpty() ? "your items" : "your search", this::reopen));
		}
		if (list.isEmpty()) this.setSlot(22, emptyState());

		// Navigation bar
		back("the Libreria menu", () -> HubGui.open(this.player));
		if (Guide.has(this.player)) {
			this.setSlot(navRow() + 1, actions(button(Items.BARRIER, "Stop beam", ChatFormatting.RED,
					"Removes the beam you are following."), "Click: stop").setCallback((i, t, a, g) -> {
						Guide.clear(this.player);
						Ui.click(this.player);
						rebuild();
					}));
		}
		pages(this.page, pages, p -> {
			this.page = p;
			rebuild();
		});
		closeButton();
		help("Everything on you and in your",
				"chests nearby, with real totals.",
				"Click an item: its nearest chest",
				"lights up and a beam shows the way.",
				"Right-click: every chest that has it.",
				"Opening a chest adds it to the index.");
		finish();
	}

	// ---------------------------------------------------------------- toolbar

	private void toolbar(List<Search.Stock> list) {
		// Summary
		long carried = 0, stored = 0;
		Set<Store.Entry> chests = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Search.Stock s : list) {
			carried += s.carried();
			stored += s.stored();
			for (Search.Hit h : s.hits()) chests.add(h.entry());
		}
		GuiElementBuilder summary = button(Items.BUNDLE, this.filter.isEmpty() ? "Your items" : "Search results", ChatFormatting.GOLD);
		if (!this.filter.isEmpty()) summary.addLoreLine(Ui.kv("Search: ", "“" + this.filter + "”", ChatFormatting.AQUA));
		summary.addLoreLine(Ui.kv("Kinds: ", Fmt.number(list.size()), ChatFormatting.WHITE));
		summary.addLoreLine(Ui.kv("On you: ", Fmt.number(carried), ChatFormatting.GREEN));
		summary.addLoreLine(Ui.kv("In chests: ", Fmt.number(stored) + " in " + Fmt.count(chests.size(), "chest", "chests"), ChatFormatting.GOLD));
		summary.addLoreLine(Component.empty());
		summary.addLoreLine(Ui.text("Chests within " + (int) Search.RADIUS + " m in this world,", ChatFormatting.DARK_GRAY));
		summary.addLoreLine(Ui.text("except skipped (noIndex) ones.", ChatFormatting.DARK_GRAY));
		this.setSlot(0, summary);

		// Sort order
		GuiElementBuilder sortButton = button(Items.COMPARATOR, "Sort: " + this.sort.label, ChatFormatting.WHITE);
		for (Sort s : Sort.values()) {
			boolean sel = s == this.sort;
			sortButton.addLoreLine(Ui.text((sel ? "▶ " : "   ") + s.label, sel ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
		}
		actions(sortButton, "Click: next order", "Right-click: previous order");
		this.setSlot(2, sortButton.setCallback((i, type, a, g) -> {
			this.sort = this.sort.step(type.isRight ? -1 : 1);
			LAST_SORT.put(this.player.getUUID(), this.sort);
			this.page = 0;
			Ui.click(this.player);
			rebuild();
		}));

		// Search – the main thing, in the middle
		GuiElementBuilder search;
		if (this.filter.isEmpty()) {
			search = button(Items.SPYGLASS, "Search…", ChatFormatting.AQUA,
					"Type part of a name: the results", "appear while you type.");
			actions(search, "Click: search");
		} else {
			search = button(Items.SPYGLASS, "Search: “" + this.filter + "”", ChatFormatting.AQUA,
					Fmt.count(list.size(), "kind matches", "kinds match"));
			actions(search, "Click: new search", "Right-click: show everything");
			search.glow();
		}
		this.setSlot(4, search.setCallback((i, type, a, g) -> {
			Ui.click(this.player);
			if (type.isRight && !this.filter.isEmpty()) {
				open(this.player, "", this.sort, 0);
			} else {
				SearchInputGui.open(this.player, this.filter, this.sort);
			}
		}));

		// My chests
		this.setSlot(6, actions(button(Items.CHEST, "My chests", ChatFormatting.GOLD,
				"Every chest in your index: see", "inside, beam to it, skip it."), "Click: open")
				.setCallback((i, t, a, g) -> {
					Ui.click(this.player);
					ChestsGui.open(this.player, "your items", this::reopen);
				}));

		// Sort inventory
		this.setSlot(8, actions(button(Items.HOPPER, "Sort inventory", ChatFormatting.YELLOW,
				"Merges and sorts your stacks.", "Your hotbar stays as it is."), "Click: sort")
				.setCallback((i, t, a, g) -> {
					Sorter.sortPlayer(this.player);
					rebuild();
				}));

		Fmt.toolbar(this, 0);
	}

	/** Back here, with the same search, order and page. */
	private void reopen() {
		open(this.player, this.filter, this.sort, this.page);
	}

	// ---------------------------------------------------------------- items

	/**
	 * One kind of item: shown with its total (up to 999), where it is in the tooltip. Click: the menu closes and the
	 * nearest chest lights up; right-click: every chest with it.
	 */
	static GuiElementBuilder element(ServerPlayer player, Search.Stock stock, String backLabel, Runnable back) {
		int shown = (int) Math.max(1, Math.min(stock.total(), MAX_SHOWN));
		int chests = stock.hits().size();
		GuiElementBuilder b = GuiElementBuilder.from(stock.sample().copyWithCount(shown)).hideDefaultTooltip();
		if (stock.total() > MAX_SHOWN) b.addLoreLine(Ui.kv("Total: ", Fmt.number(stock.total()), ChatFormatting.WHITE));
		if (stock.carried() > 0) b.addLoreLine(Ui.kv("On you: ", Fmt.number(stock.carried()), ChatFormatting.GREEN));
		if (chests > 0) {
			Search.Hit near = stock.hits().getFirst();
			b.addLoreLine(Ui.kv("In chests: ", Fmt.number(stock.stored()) + " in " + Fmt.count(chests, "chest", "chests"), ChatFormatting.GOLD));
			b.addLoreLine(Ui.kv("Nearest: ", Fmt.distance(player, near.entry(), near.distance()), ChatFormatting.YELLOW));
			if (!near.live()) b.addLoreLine(Ui.text("Last seen " + Search.ago(near.entry().seen), ChatFormatting.DARK_GRAY));
			actions(b, "Click: light up the nearest chest", "Right-click: every chest with it");
		} else {
			b.addLoreLine(Ui.text("None in your chests", ChatFormatting.DARK_GRAY));
		}
		b.setCallback((index, type, action, gui) -> {
			if (chests == 0) {
				Ui.sound(player, SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 0.7f);
				return;
			}
			if (type.isRight) {
				Ui.click(player);
				ItemGui.open(player, stock.item(), backLabel, back);
			} else if (type.isLeft) {
				gui.close();
				Locate.go(player, stock.sample(), stock.hits());
			}
		});
		return b;
	}

	/** Friendly explanation when there is nothing to show. */
	private GuiElementBuilder emptyState() {
		if (!this.filter.isEmpty()) {
			GuiElementBuilder b = button(Items.BARRIER, "Nothing matches “" + this.filter + "”", ChatFormatting.RED,
					"Not on you, nor in your chests nearby.", "Part of a name works too, like dia or oak.");
			actions(b, "Click: search again", "Right-click: show everything");
			return b.setCallback((i, type, a, g) -> {
				Ui.click(this.player);
				if (type.isRight) open(this.player, "", this.sort, 0);
				else SearchInputGui.open(this.player, this.filter, this.sort);
			});
		}
		return button(Items.CHEST, "Nothing yet", ChatFormatting.GOLD,
				"Open a chest, barrel or shulker", "box: it's added to your index.", "Then find anything right here.");
	}
}
