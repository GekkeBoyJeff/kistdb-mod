package nl.libreria.chests.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import nl.libreria.chests.Search;
import nl.libreria.chests.Sorter;
import nl.libreria.chests.Store;
import nl.libreria.core.Guide;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.HubGui;
import nl.libreria.core.gui.Menu;

/**
 * The chest index: every item in your chests nearby, as icons. A toolbar on top (summary, sort order, search,
 * my chests, sort inventory), the items below, the navigation bar at the bottom. Click = beam to the nearest chest.
 */
public final class OverviewGui extends Menu {
	/** Sort orders for the index. */
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
	/** The sort order a player picked last, so /chest opens the way they like it. */
	private static final Map<UUID, Sort> LAST_SORT = new HashMap<>();

	private final String filter;
	private Sort sort;
	private int page;

	private OverviewGui(ServerPlayer player, String filter, Sort sort, int page) {
		super(player, 6, filter.isEmpty() ? "Chests · Search" : "Chests · Search: " + filter);
		this.filter = filter;
		this.sort = sort;
		this.page = page;
		rebuild();
	}

	/** Opens the chest index, optionally already filtered ("" = everything). */
	public static void open(ServerPlayer player, String filter) {
		open(player, filter, LAST_SORT.getOrDefault(player.getUUID(), Sort.MOST), 0);
	}

	static void open(ServerPlayer player, String filter, Sort sort, int page) {
		new OverviewGui(player, filter == null ? "" : filter.trim(), sort, page).open();
	}

	private void rebuild() {
		clearContent();
		Fmt.clearRow(this, this.rows - 1);

		List<Search.Agg> list = Search.aggregate(this.player, this.filter, this.sort == Sort.NAME);
		if (this.sort == Sort.NEAREST) list.sort(Comparator.comparingDouble(a -> a.nearest().distance()));
		int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
		this.page = Math.max(0, Math.min(this.page, pages - 1));

		toolbar(list);
		for (int i = 0; i < PER_PAGE; i++) {
			int idx = this.page * PER_PAGE + i;
			if (idx >= list.size()) break;
			this.setSlot(FIRST + i, element(list.get(idx)));
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
		help("Everything in your chests nearby.",
				"Click an item: beam to the nearest.",
				"Right-click: every chest that has it.",
				"Opening a chest adds it to the index.",
				"Sneak + left-click a chest (empty",
				"hand): skip it in search (noIndex).");
		finish();
	}

	// ---------------------------------------------------------------- toolbar

	private void toolbar(List<Search.Agg> list) {
		// Summary
		long items = 0;
		Set<Store.Entry> chests = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Search.Agg a : list) {
			items += a.total();
			for (Search.Hit h : a.hits()) chests.add(h.entry());
		}
		GuiElementBuilder summary = button(Items.BOOKSHELF, this.filter.isEmpty() ? "Your chest index" : "Search results",
				ChatFormatting.GOLD);
		if (!this.filter.isEmpty()) summary.addLoreLine(Ui.kv("Search: ", "\"" + this.filter + "\"", ChatFormatting.AQUA));
		summary.addLoreLine(Ui.kv("Kinds: ", Fmt.number(list.size()), ChatFormatting.WHITE));
		summary.addLoreLine(Ui.kv("Items: ", Fmt.number(items), ChatFormatting.GREEN));
		summary.addLoreLine(Ui.kv("Chests: ", Fmt.number(chests.size()), ChatFormatting.YELLOW));
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
					"Type part of a name or ID,", "like diamond, oak or iron.");
			actions(search, "Click: search");
		} else {
			search = button(Items.SPYGLASS, "Search: \"" + this.filter + "\"", ChatFormatting.AQUA,
					Fmt.count(list.size(), "kind matches", "kinds match"));
			actions(search, "Click: new search", "Right-click: clear search");
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
					ChestsGui.open(this.player, "the chest index", this::reopen);
				}));

		// Sort inventory
		this.setSlot(8, actions(button(Items.HOPPER, "Sort inventory", ChatFormatting.YELLOW,
				"Merges and sorts your stacks.", "Your hotbar stays as it is."), "Click: sort")
				.setCallback((i, t, a, g) -> Sorter.sortPlayer(this.player)));

		Fmt.toolbar(this, 0);
	}

	/** Back here, with the same search, order and page. */
	private void reopen() {
		open(this.player, this.filter, this.sort, this.page);
	}

	// ---------------------------------------------------------------- items

	private GuiElementBuilder element(Search.Agg agg) {
		Search.Hit near = agg.nearest();
		int shown = (int) Math.max(1, Math.min(agg.total(), 99));
		int chests = agg.hits().size();
		GuiElementBuilder b = GuiElementBuilder.from(agg.sample().copyWithCount(shown)).hideDefaultTooltip()
				.addLoreLine(Ui.kv("Total: ", Fmt.number(agg.total()), ChatFormatting.GREEN))
				.addLoreLine(Ui.kv("In: ", Fmt.count(chests, "chest", "chests"), ChatFormatting.WHITE))
				.addLoreLine(Ui.kv("Nearest: ", Fmt.distance(this.player, near.entry(), near.distance()), ChatFormatting.YELLOW));
		if (!near.live()) b.addLoreLine(Ui.text("Last seen " + Search.ago(near.entry().seen), ChatFormatting.DARK_GRAY));
		actions(b, "Click: beam to the nearest", "Right-click: list all " + Fmt.count(chests, "chest", "chests"));
		b.setCallback((index, type, action, gui) -> {
			if (type.isRight) {
				Ui.click(this.player);
				ItemGui.open(this.player, agg.item(), this.filter.isEmpty() ? "the chest index" : "your search", this::reopen);
			} else if (type.isLeft) {
				this.close();
				Locate.go(this.player, agg.sample(), agg.hits());
			}
		});
		return b;
	}

	/** Friendly explanation when there is nothing to show. */
	private GuiElementBuilder emptyState() {
		if (!this.filter.isEmpty()) {
			GuiElementBuilder b = button(Items.BARRIER, "Nothing matches \"" + this.filter + "\"", ChatFormatting.RED,
					"Not in your chests nearby.", "Try another word: part of a name", "works too, like dia or oak.");
			actions(b, "Click: search again", "Right-click: clear search");
			return b.setCallback((i, type, a, g) -> {
				Ui.click(this.player);
				if (type.isRight) open(this.player, "", this.sort, 0);
				else SearchInputGui.open(this.player, this.filter, this.sort);
			});
		}
		if (Store.get(this.player.getUUID()).entries.isEmpty()) {
			return button(Items.CHEST, "No chests yet", ChatFormatting.GOLD,
					"Open a chest, barrel or shulker", "box: it's added to your index.", "Then find anything right here.");
		}
		if (Search.boxes(this.player, false).isEmpty()) {
			return button(Items.COMPASS, "No chests nearby", ChatFormatting.GOLD,
					"Only chests within " + (int) Search.RADIUS + " m in this", "world are searched, and not the",
					"ones you skip (noIndex).");
		}
		return button(Items.CHEST, "Your chests are empty", ChatFormatting.GOLD,
				"Put something in a chest nearby", "and it shows up here.");
	}
}
