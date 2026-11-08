package nl.libreria.chests.gui;

import eu.pb4.sgui.api.ClickType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
import nl.libreria.core.gui.Canvas;
import nl.libreria.core.gui.CanvasWindow;
import nl.libreria.core.gui.Glyphs;

/**
 * "Your chests" as a window of our own: which chests (all, the ones in your item search, the ones left out) on top,
 * then a list, nearest first, with how many items each holds and how far it is. Click: show the way; right-click: see
 * inside; shift-click: leave it out of search (or take it back); Q: forget it.
 */
public final class ChestsWindow extends CanvasWindow {
	private static final int PER_PAGE = 8;
	private static final int TEXT = 0xFFFFFF, LEFT_OUT = 0xE06A5A, OFF = 0x7A808C;

	enum Show {
		ALL("All", "All your chests"), SEARCHED("In search", "Chests in your item search"), SKIPPED("Left out", "Left out of search");

		final String tab, about;

		Show(String tab, String about) {
			this.tab = tab;
			this.about = about;
		}

		boolean accepts(Store.Entry e) {
			return this == ALL || (this == SKIPPED) == e.noIndex;
		}
	}

	private final Runnable back;
	private Show show = Show.ALL;
	private boolean fullest;
	private int page;

	private ChestsWindow(ServerPlayer p, Runnable back) {
		super(p);
		this.back = back;
	}

	public static void open(ServerPlayer p, Runnable back) {
		new ChestsWindow(p, back).show();
	}

	private static long total(Search.Box box) {
		return box.counts().values().stream().mapToLong(Integer::longValue).sum();
	}

	private List<Search.Box> all() {
		return Search.boxes(this.player, true);
	}

	private List<Search.Box> shown(List<Search.Box> all) {
		List<Search.Box> out = new ArrayList<>();
		for (Search.Box b : all) if (this.show.accepts(b.entry())) out.add(b);
		if (this.fullest) out.sort(Comparator.comparingLong(ChestsWindow::total).reversed());
		return out;
	}

	private int pages(List<Search.Box> shown) {
		return Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
	}

	/** The three tabs on top: (first column, width) for each. */
	private static int[] tab(Show s) {
		return switch (s) {
			case ALL -> new int[] {0, 2};
			case SEARCHED -> new int[] {2, 4};
			case SKIPPED -> new int[] {6, 3};
		};
	}

	// ---------------------------------------------------------------- picture

	@Override
	protected Canvas draw() {
		List<Search.Box> shown = shown(all());
		int pages = pages(shown);
		this.page = Math.max(0, Math.min(this.page, pages - 1));
		Canvas c = new Canvas().panel();
		c.text(Domain.YOU.label, 8, 5, Domain.YOU.rgb);
		int x = 8 + Glyphs.width(Component.literal(Domain.YOU.label));
		c.text(" › ", x, 5, Glyphs.muted).text("Your chests", x + Glyphs.width(Component.literal(" › ")), 5, TEXT);
		for (Show s : Show.values()) {
			int[] t = tab(s);
			boolean on = s == this.show;
			c.card(t[0], 0, t[1], 1, on ? Domain.YOU.rgb : OFF);
			c.centred(s.tab, Canvas.cellX(t[0]) + 9 * t[1], Canvas.cellY(0) + 5, on ? TEXT : 0xC8CCD4);
		}
		int left = Canvas.cellX(1) + 4, width = Canvas.cellX(9) - 2 - left;
		int from = this.page * PER_PAGE;
		for (int i = 0; i < PER_PAGE && from + i < shown.size(); i++) {
			Search.Box box = shown.get(from + i);
			Store.Entry e = box.entry();
			int row = i + 1;
			c.line(left, row, width, e.noIndex ? LEFT_OUT : Domain.YOU.rgb);
			int y = Canvas.cellY(row) + 5;
			String far = Ui.meters(box.distance());
			long total = total(box);
			String count = total == 0 ? "empty" : Fmt.number(total);
			int right = left + width - 6;
			c.text(Canvas.fit(Search.kind(e) + (e.supply ? " · supply" : ""), width - 12 - 70), left + 6, y, TEXT);
			c.right(count, right - 36, y, Glyphs.muted);
			c.right(far, right, y, TEXT);
		}
		if (shown.isEmpty()) {
			boolean none = all().isEmpty();
			int y = Canvas.cellY(3);
			c.centred(none ? "No chests yet" : "None here", 88, y, TEXT);
			String[] lines = none ? new String[] {"Open a chest, barrel or shulker", "box once: Libreria remembers it."}
					: new String[] {this.show == Show.SKIPPED ? "No chests are left out of search." : "All your chests are left out of search."};
			for (String l : lines) c.centred(l, 88, y += 11, Glyphs.muted);
		}
		// Bottom: Back, the pages, and three small buttons (search, order, quick stack)
		c.card(0, 9, 3, 1, Glyphs.neutral);
		c.centred("Back", Canvas.cellX(0) + 27, Canvas.cellY(9) + 5, TEXT);
		if (pages > 1) {
			if (this.page > 0) {
				c.card(3, 9, 1, 1, Glyphs.neutral);
				c.centred("‹", Canvas.cellX(3) + 9, Canvas.cellY(9) + 5, TEXT);
			}
			c.centred((this.page + 1) + "/" + pages, Canvas.cellX(4) + 9, Canvas.cellY(9) + 5, Glyphs.muted);
			if (this.page < pages - 1) {
				c.card(5, 9, 1, 1, Glyphs.neutral);
				c.centred("›", Canvas.cellX(5) + 9, Canvas.cellY(9) + 5, TEXT);
			}
		}
		return c;
	}

	// ---------------------------------------------------------------- cells

	@Override
	protected void fill() {
		List<Search.Box> all = all();
		List<Search.Box> shown = shown(all);
		int pages = pages(shown);
		long skipped = all.stream().filter(b -> b.entry().noIndex).count();
		for (Show s : Show.values()) {
			int[] t = tab(s);
			long n = s == Show.ALL ? all.size() : s == Show.SKIPPED ? skipped : all.size() - skipped;
			boolean on = s == this.show;
			List<Component> lore = List.of(Ui.text(s.about + ": " + Fmt.number(n), ChatFormatting.GRAY),
					Ui.text("Within " + (int) Search.radius() + " m in this world.", ChatFormatting.DARK_GRAY), Component.empty(),
					on ? Ui.text("✔ Showing now", ChatFormatting.GREEN) : Ui.text("▸ Click: show these", ChatFormatting.YELLOW));
			area(t[0], 0, t[1], 1, Ui.text(s.tab, on ? ChatFormatting.GREEN : ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), lore, p -> {
				if (this.show == s) return;
				this.show = s;
				this.page = 0;
				refresh();
			});
		}
		int from = this.page * PER_PAGE;
		for (int i = 0; i < PER_PAGE && from + i < shown.size(); i++) {
			Search.Box box = shown.get(from + i);
			Store.Entry e = box.entry();
			Component name = Ui.text((e.noIndex ? "✘ " : "") + Search.kind(e) + " · " + Ui.meters(box.distance()),
					e.noIndex ? ChatFormatting.RED : ChatFormatting.YELLOW).withStyle(ChatFormatting.BOLD);
			List<Component> lore = lore(box);
			iconTyped(0, i + 1, new ItemStack(Search.blockItem(e)), name, lore, (p, type) -> click(box, type));
			areaTyped(1, i + 1, 8, 1, name, lore, (p, type) -> click(box, type));
		}
		area(0, 9, 3, 1, Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), List.of(), p -> this.back.run());
		if (pages > 1) {
			if (this.page > 0) area(3, 9, 1, 1, Ui.text("Previous page", ChatFormatting.WHITE), List.of(), p -> turn(-1));
			if (this.page < pages - 1) area(5, 9, 1, 1, Ui.text("Next page", ChatFormatting.WHITE), List.of(), p -> turn(1));
		}
		icon(6, 9, new ItemStack(Items.SPYGLASS), Ui.text("Search…", ChatFormatting.AQUA).withStyle(ChatFormatting.BOLD),
				List.of(Ui.text("Find an item in all your chests.", ChatFormatting.GRAY), Component.empty(),
						Ui.text("▸ Click: open Your items", ChatFormatting.YELLOW)), p -> ItemSearch.open(p, ""));
		icon(7, 9, new ItemStack(Items.COMPARATOR), Ui.text(this.fullest ? "Most items first" : "Nearest first", ChatFormatting.WHITE)
				.withStyle(ChatFormatting.BOLD), List.of(Ui.text(this.fullest ? "▶ Most items first" : "   Most items first",
						this.fullest ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY),
						Ui.text(this.fullest ? "   Nearest first" : "▶ Nearest first", this.fullest ? ChatFormatting.DARK_GRAY : ChatFormatting.GREEN),
						Component.empty(), Ui.text("▸ Click: switch", ChatFormatting.YELLOW)), p -> {
							this.fullest = !this.fullest;
							this.page = 0;
							refresh();
						});
		icon(8, 9, new ItemStack(Items.CHEST_MINECART), Ui.text("Quick stack", ChatFormatting.GREEN).withStyle(ChatFormatting.BOLD),
				List.of(Ui.text("Puts inventory items into nearby", ChatFormatting.GRAY), Ui.text("chests that already have them.", ChatFormatting.GRAY),
						Ui.text("Your hotbar stays as it is.", ChatFormatting.GRAY), Component.empty(), Ui.text("▸ Click: stack", ChatFormatting.YELLOW)),
				p -> {
					QuickStack.run(p);
					refresh();
				});
	}

	private List<Component> lore(Search.Box box) {
		Store.Entry e = box.entry();
		long total = total(box);
		int kinds = box.counts().size();
		List<Component> out = new ArrayList<>();
		out.add(Ui.kv("Distance: ", Fmt.distance(this.player, e, box.distance()), ChatFormatting.YELLOW));
		out.add(Ui.kv("Where: ", Fmt.where(e), ChatFormatting.WHITE));
		out.add(total == 0 ? Ui.kv("Contents: ", "empty", ChatFormatting.DARK_GRAY)
				: Ui.kv("Contents: ", Fmt.count(total, "item", "items") + " · " + Fmt.count(kinds, "kind", "kinds"), ChatFormatting.GREEN));
		if (!box.live()) out.add(Ui.text("Last seen " + Search.ago(e.seen), ChatFormatting.DARK_GRAY));
		out.add(e.noIndex ? Ui.text("✘ Left out of your item search", ChatFormatting.RED) : Ui.text("✔ Shows up in search", ChatFormatting.GREEN));
		out.add(Component.empty());
		out.add(Ui.text("▸ Click: show the way", ChatFormatting.YELLOW));
		out.add(Ui.text("▸ Right-click: see inside", ChatFormatting.YELLOW));
		out.add(Ui.text(e.noIndex ? "▸ Shift-click: take back into search" : "▸ Shift-click: leave out of search", ChatFormatting.YELLOW));
		out.add(Ui.text("▸ {drop}: forget this chest", ChatFormatting.YELLOW));
		return out;
	}

	private void click(Search.Box box, ClickType type) {
		Store.Entry e = box.entry();
		if (type == ClickType.DROP || type == ClickType.CTRL_DROP) {
			Ui.click(this.player);
			Store.remove(this.player.getUUID(), e);
			Tracker.actionbar(this.player, Ui.text("Chest forgotten – open it to add it again", ChatFormatting.GRAY));
			refresh();
		} else if (type.shift) {
			Ui.click(this.player);
			Tracker.toggleNoIndex(this.player, this.player.level(), e.pos());
			refresh();
		} else if (type.isRight) {
			Ui.click(this.player);
			ChestsGui.ContentsGui.open(this.player, box, () -> {
				ChestsWindow w = new ChestsWindow(this.player, this.back);
				w.show = this.show;
				w.fullest = this.fullest;
				w.page = this.page;
				w.show();
			});
		} else if (type.isLeft) {
			this.close();
			Beam.to(this.player, this.player.level(), e.pos(), null, 0);
		}
	}

	private void turn(int step) {
		this.page += step;
		refresh();
	}
}
