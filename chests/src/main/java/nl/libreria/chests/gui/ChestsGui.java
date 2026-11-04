package nl.libreria.chests.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.chests.Beam;
import nl.libreria.chests.QuickStack;
import nl.libreria.chests.Search;
import nl.libreria.chests.Store;
import nl.libreria.chests.Tracker;
import nl.libreria.core.Domain;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Menu;

/** "Your chests": every container Libreria knows of (also the left-out ones), nearest first. */
public final class ChestsGui extends Menu {
	/** Which chests to list. */
	private enum Show {
		ALL("All chests"),
		SEARCHED("In search"),
		SKIPPED("Left out of search");

		final String label;

		Show(String label) {
			this.label = label;
		}

		boolean accepts(Store.Entry e) {
			return this == ALL || (this == SKIPPED) == e.noIndex;
		}
	}

	private enum Order {
		NEAREST("Nearest first"),
		FULLEST("Most items first");

		final String label;

		Order(String label) {
			this.label = label;
		}
	}

	private static final int FIRST = 9;
	private static final int PER_PAGE = 36;

	private final String backLabel;
	private final Runnable back;
	private Show show;
	private Order order;
	private int page;

	private ChestsGui(ServerPlayer player, String backLabel, Runnable back, Show show, Order order, int page) {
		super(player, 6, "Your chests");
		this.backLabel = backLabel;
		this.back = back;
		this.show = show;
		this.order = order;
		this.page = page;
		rebuild();
	}

	/** Opens "My chests"; Back goes to the Libreria menu. */
	public static void open(ServerPlayer player) {
		open(player, Domain.ITEMS.label, () -> nl.libreria.core.Start.domain(player, Domain.ITEMS));
	}

	static void open(ServerPlayer player, String backLabel, Runnable back) {
		new ChestsGui(player, backLabel, back, Show.ALL, Order.NEAREST, 0).open();
	}

	/** Back here with the same filter, order and page. */
	private void reopen() {
		new ChestsGui(this.player, this.backLabel, this.back, this.show, this.order, this.page).open();
	}

	private static long total(Search.Box box) {
		return box.counts().values().stream().mapToLong(Integer::longValue).sum();
	}

	private void rebuild() {
		clearContent();
		Fmt.clearRow(this, this.rows - 1);

		List<Search.Box> all = Search.boxes(this.player, true);
		List<Search.Box> boxes = new ArrayList<>();
		for (Search.Box b : all) if (this.show.accepts(b.entry())) boxes.add(b);
		if (this.order == Order.FULLEST) boxes.sort(Comparator.comparingLong(ChestsGui::total).reversed());
		int pages = Math.max(1, (boxes.size() + PER_PAGE - 1) / PER_PAGE);
		this.page = Math.max(0, Math.min(this.page, pages - 1));

		toolbar(all);
		for (int i = 0; i < PER_PAGE; i++) {
			int idx = this.page * PER_PAGE + i;
			if (idx >= boxes.size()) break;
			this.setSlot(FIRST + i, element(boxes.get(idx)));
		}
		if (boxes.isEmpty()) this.setSlot(22, emptyState(all.isEmpty()));

		back(this.backLabel, this.back);
		pages(this.page, pages, p -> {
			this.page = p;
			rebuild();
		});
		closeButton();
		help("Every chest of yours nearby.",
				"Click: show the way.",
				"Right-click: see what's inside.",
				"Shift-click: leave it out of search,",
				"or take it back in.",
				"Q: forget it (until you open it).");
		finish();
	}

	private void toolbar(List<Search.Box> all) {
		long skipped = all.stream().filter(b -> b.entry().noIndex).count();
		this.setSlot(0, button(Items.BOOKSHELF, "Your chests", ChatFormatting.GOLD)
				.addLoreLine(Ui.kv("Chests: ", Fmt.number(all.size()), ChatFormatting.WHITE))
				.addLoreLine(Ui.kv("In search: ", Fmt.number(all.size() - skipped), ChatFormatting.GREEN))
				.addLoreLine(Ui.kv("Left out: ", Fmt.number(skipped), ChatFormatting.RED))
				.addLoreLine(Ui.text("Within " + (int) Search.radius() + " m in this world.", ChatFormatting.DARK_GRAY)));

		GuiElementBuilder showButton = button(Items.ENDER_EYE, "Show: " + this.show.label, ChatFormatting.WHITE);
		for (Show s : Show.values()) {
			boolean sel = s == this.show;
			showButton.addLoreLine(Ui.text((sel ? "▶ " : "   ") + s.label, sel ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
		}
		actions(showButton, "Click: next", "Right-click: previous");
		this.setSlot(2, showButton.setCallback((i, type, a, g) -> {
			Show[] values = Show.values();
			this.show = values[Math.floorMod(this.show.ordinal() + (type.isRight ? -1 : 1), values.length)];
			this.page = 0;
			Ui.click(this.player);
			rebuild();
		}));

		this.setSlot(4, actions(button(Items.SPYGLASS, "Search…", ChatFormatting.AQUA,
				"Find an item in all your chests."), "Click: open Your items")
				.setCallback((i, t, a, g) -> {
					Ui.click(this.player);
					ItemSearch.open(this.player, "");
				}));

		GuiElementBuilder orderButton = button(Items.COMPARATOR, "Sort: " + this.order.label, ChatFormatting.WHITE);
		for (Order o : Order.values()) {
			boolean sel = o == this.order;
			orderButton.addLoreLine(Ui.text((sel ? "▶ " : "   ") + o.label, sel ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
		}
		actions(orderButton, "Click: switch");
		this.setSlot(6, orderButton.setCallback((i, t, a, g) -> {
			this.order = this.order == Order.NEAREST ? Order.FULLEST : Order.NEAREST;
			this.page = 0;
			Ui.click(this.player);
			rebuild();
		}));

		this.setSlot(8, actions(button(Items.CHEST_MINECART, "Quick stack", ChatFormatting.GREEN,
				"Puts inventory items into nearby", "chests that already have them.", "Your hotbar stays as it is."), "Click: stack")
				.setCallback((i, t, a, g) -> {
					QuickStack.run(this.player);
					rebuild();
				}));
		Fmt.toolbar(this, 0);
	}

	private GuiElementBuilder element(Search.Box box) {
		Store.Entry e = box.entry();
		long total = total(box);
		int kinds = box.counts().size();
		GuiElementBuilder b = button(Search.blockItem(e), (e.noIndex ? "✘ " : "") + Search.kind(e) + " · " + Ui.meters(box.distance()),
				e.noIndex ? ChatFormatting.RED : ChatFormatting.YELLOW)
				.addLoreLine(Ui.kv("Distance: ", Fmt.distance(this.player, e, box.distance()), ChatFormatting.YELLOW))
				.addLoreLine(Ui.kv("Where: ", Fmt.where(e), ChatFormatting.WHITE))
				.addLoreLine(total == 0
						? Ui.kv("Contents: ", "empty", ChatFormatting.DARK_GRAY)
						: Ui.kv("Contents: ", Fmt.count(total, "item", "items") + " · " + Fmt.count(kinds, "kind", "kinds"), ChatFormatting.GREEN));
		if (!box.live()) b.addLoreLine(Ui.text("Last seen " + Search.ago(e.seen), ChatFormatting.DARK_GRAY));
		b.addLoreLine(e.noIndex
				? Ui.text("✘ Left out of your item search", ChatFormatting.RED)
				: Ui.text("✔ Shows up in search", ChatFormatting.GREEN));
		actions(b, "Click: show the way", "Right-click: see inside",
				e.noIndex ? "Shift-click: take back into search" : "Shift-click: leave out of search",
				"Q: forget this chest");
		b.setCallback((index, type, action, gui) -> {
			if (type == ClickType.DROP || type == ClickType.CTRL_DROP) {
				Ui.click(this.player);
				Store.remove(this.player.getUUID(), e);
				Tracker.actionbar(this.player, Ui.text("Chest forgotten – open it to add it again", ChatFormatting.GRAY));
				rebuild();
			} else if (type.shift) {
				Ui.click(this.player);
				Tracker.toggleNoIndex(this.player, this.player.level(), e.pos());
				rebuild();
			} else if (type.isRight) {
				Ui.click(this.player);
				ContentsGui.open(this.player, box, this::reopen);
			} else if (type.isLeft) {
				this.close();
				Beam.to(this.player, this.player.level(), e.pos(), null, 0);
			}
		});
		return b;
	}

	private GuiElementBuilder emptyState(boolean none) {
		if (none) {
			return button(Items.CHEST, Store.get(this.player.getUUID()).entries.isEmpty() ? "No chests yet" : "No chests nearby",
					ChatFormatting.GOLD,
					"Open a chest, barrel or shulker", "box once: Libreria remembers it.",
					"Chests within " + (int) Search.radius() + " m in this world", "show up here.");
		}
		return button(Items.BARRIER, "None here", ChatFormatting.RED,
				this.show == Show.SKIPPED ? "No chests are left out of search." : "All your chests are left out of search.",
				"Change \"Show\" at the top.");
	}

	/** Read-only view of what's in one chest. */
	static final class ContentsGui extends Menu {
		private static final int PER_PAGE = 45;
		private final Search.Box box;
		private final Runnable back;
		private int page;

		private ContentsGui(ServerPlayer player, Search.Box box, Runnable back) {
			super(player, 6, Search.kind(box.entry()) + " · " + Fmt.where(box.entry()) + (box.live() ? "" : " (last seen)"));
			this.box = box;
			this.back = back;
			rebuild();
		}

		static void open(ServerPlayer player, Search.Box box, Runnable back) {
			new ContentsGui(player, box, back).open();
		}

		private void beam(ItemStack item) {
			this.close();
			Store.Entry e = this.box.entry();
			int count = item == null ? 0 : this.box.counts().getOrDefault(item.getItem(), item.getCount());
			Beam.to(this.player, this.player.level(), e.pos(), item, count);
		}

		private void rebuild() {
			clearContent();
			Fmt.clearRow(this, this.rows - 1);
			List<ItemStack> stacks = this.box.stacks();
			int pages = Math.max(1, (stacks.size() + PER_PAGE - 1) / PER_PAGE);
			this.page = Math.max(0, Math.min(this.page, pages - 1));
			for (int i = 0; i < PER_PAGE; i++) {
				int idx = this.page * PER_PAGE + i;
				if (idx >= stacks.size()) break;
				ItemStack s = stacks.get(idx);
				GuiElementBuilder b = GuiElementBuilder.from(s.copy());
				if (!this.box.live()) {
					Integer n = this.box.counts().get(s.getItem());
					if (n != null) b.addLoreLine(Ui.kv("In this chest: ", Fmt.number(n), ChatFormatting.GREEN));
				}
				actions(b, "Click: show the way to this chest");
				b.setCallback((index, type, action, gui) -> beam(s));
				this.setSlot(i, b);
			}
			if (stacks.isEmpty()) {
				this.setSlot(22, button(Items.BARRIER, "Empty", ChatFormatting.GRAY, "There's nothing in this chest."));
			}

			back("Your chests", this.back);
			pages(this.page, pages, p -> {
				this.page = p;
				rebuild();
			});
			closeButton();
			this.setSlot(navRow() + 6, actions(button(Search.blockItem(this.box.entry()), "Show the way", ChatFormatting.AQUA,
					Ui.meters(this.box.distance()) + " away, at " + Fmt.where(this.box.entry())), "Click: show the way")
					.setCallback((i, t, a, g) -> beam(null)));
			if (this.box.live()) {
				help("What's in this chest right now.",
						"Look only: you can't take items here.",
						"Click any item: show the way here.");
			} else {
				help("What was in it when you last saw it",
						"(its chunk isn't loaded right now).",
						"Look only: you can't take items here.",
						"Click any item: show the way here.");
			}
			finish();
		}
	}
}
