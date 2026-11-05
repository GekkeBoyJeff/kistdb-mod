package nl.libreria.collector.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import nl.libreria.collector.Achievement;
import nl.libreria.collector.Catalog;
import nl.libreria.collector.CollectorMod;
import nl.libreria.core.Domain;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Canvas;
import nl.libreria.core.gui.CanvasWindow;
import nl.libreria.core.gui.Glyphs;

/**
 * Choosing your showcase: everything you earned, rarest first, the name in its rarity's colour. The three in your
 * showcase carry their place ("1. Diamond King"). Click one: it goes in the place you came for (or out, if it's in).
 */
public final class ShowcaseWindow extends CanvasWindow {
	private static final int[] ROWS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
	private static final int TEXT = 0xFFFFFF;

	private final int slot;
	private final Consumer<ServerPlayer> back;
	private int page;

	private ShowcaseWindow(ServerPlayer p, int slot, Consumer<ServerPlayer> back) {
		super(p);
		this.slot = slot;
		this.back = back;
	}

	public static void open(ServerPlayer p, int slot, Consumer<ServerPlayer> back) {
		new ShowcaseWindow(p, slot, back).show();
	}

	private List<Achievement> earned() {
		return CollectorMod.earnedRarestFirst(CollectorMod.STORE.get(this.player));
	}

	private static int rgb(Achievement a) {
		TextColor c = TextColor.fromLegacyFormat(a.rarity().color);
		return c == null ? 0xFFFFFF : c.getValue();
	}

	/** 1, 2 or 3 if it's in your showcase, else 0. */
	private int place(Achievement a) {
		List<String> s = CollectorMod.STORE.get(this.player).showcase;
		int i = s.indexOf(a.id());
		return i < 0 ? 0 : i + 1;
	}

	private int pages(List<Achievement> all) {
		return Math.max(1, (all.size() + ROWS.length - 1) / ROWS.length);
	}

	@Override
	protected Canvas draw() {
		List<Achievement> all = earned();
		int pages = pages(all);
		this.page = Math.max(0, Math.min(this.page, pages - 1));
		Canvas c = new Canvas().panel();
		int x = 8;
		c.text(Domain.YOU.label, x, 5, Domain.YOU.rgb);
		x += Glyphs.width(Component.literal(Domain.YOU.label));
		c.text(" › ", x, 5, Glyphs.muted);
		c.text("Showcase", x + Glyphs.width(Component.literal(" › ")), 5, TEXT);
		int chosen = CollectorMod.STORE.get(this.player).showcase.size();
		c.right(chosen == 0 ? "Automatic" : chosen + " chosen", 168 - 2, 5, Glyphs.muted);
		int left = Canvas.cellX(1) + 4, width = Canvas.cellX(9) - Canvas.CARD_MARGIN - left;
		int from = this.page * ROWS.length;
		for (int i = 0; i < ROWS.length && from + i < all.size(); i++) {
			Achievement a = all.get(from + i);
			int row = ROWS[i], place = place(a);
			c.line(left, row, width, rgb(a));
			c.text(Canvas.fit((place > 0 ? place + ". " : "") + a.name(), width - 12), left + 6, Canvas.cellY(row) + 5, rgb(a));
		}
		if (all.isEmpty()) {
			c.centred("Nothing to show yet", 88, Canvas.cellY(3), TEXT);
			c.centred("Earn an achievement first.", 88, Canvas.cellY(3) + 11, Glyphs.muted);
		}
		small(c, 0, 3, "Back");
		if (pages > 1) {
			if (this.page > 0) small(c, 3, 1, "‹");
			c.centred((this.page + 1) + "/" + pages, Canvas.cellX(4) + 9, Canvas.cellY(9) + 5, Glyphs.muted);
			if (this.page < pages - 1) small(c, 5, 1, "›");
		}
		small(c, 6, 3, "Auto");
		return c;
	}

	private static void small(Canvas c, int col, int w, String label) {
		c.card(col, 9, w, 1, Glyphs.neutral);
		c.centred(label, Canvas.cellX(col) + 9 * w, Canvas.cellY(9) + 5, TEXT);
	}

	@Override
	protected void fill() {
		List<Achievement> all = earned();
		int pages = pages(all);
		int from = this.page * ROWS.length;
		for (int i = 0; i < ROWS.length && from + i < all.size(); i++) {
			Achievement a = all.get(from + i);
			int place = place(a);
			List<Component> lore = new ArrayList<>();
			String grade = Catalog.gradeText(a);
			lore.add(Component.literal(a.rarity().label + (grade == null ? "" : " · " + grade)).withStyle(a.rarity().color));
			lore.add(Ui.text(a.description(), ChatFormatting.GRAY));
			lore.add(Component.empty());
			lore.add(Ui.text(place > 0 ? "▸ Click: take it out of your showcase" : "▸ Click: put it in place " + (this.slot + 1), ChatFormatting.YELLOW));
			Component name = Ui.text(a.name(), rgb(a)).withStyle(ChatFormatting.BOLD);
			Consumer<ServerPlayer> pick = p -> pick(a);
			area(1, ROWS[i], 8, 1, name, lore, pick);
			icon(0, ROWS[i], new ItemStack(a.icon()), name, lore, pick);
		}
		area(0, 9, 3, 1, Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), List.of(Ui.text("To your profile.", ChatFormatting.GRAY)),
				this.back);
		if (pages > 1) {
			if (this.page > 0) area(3, 9, 1, 1, Ui.text("Previous page", ChatFormatting.WHITE), List.of(), p -> turn(-1));
			if (this.page < pages - 1) area(5, 9, 1, 1, Ui.text("Next page", ChatFormatting.WHITE), List.of(), p -> turn(1));
		}
		area(6, 9, 3, 1, Ui.text("Auto", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD),
				List.of(Ui.text("Show your three rarest,", ChatFormatting.GRAY), Ui.text("whatever they are.", ChatFormatting.GRAY)), p -> {
					CollectorMod.Data d = CollectorMod.STORE.get(p);
					d.showcase.clear();
					CollectorMod.STORE.markDirty(p);
					this.back.accept(p);
				});
	}

	/** Out if it's in; otherwise into the place you came for. Then back to your profile. */
	private void pick(Achievement a) {
		CollectorMod.Data d = CollectorMod.STORE.get(this.player);
		// Start from what's shown now (the automatic three, when nothing was chosen yet)
		if (d.showcase.isEmpty()) for (Achievement s : CollectorMod.showcase(d)) d.showcase.add(s.id());
		if (d.showcase.remove(a.id())) {
			CollectorMod.STORE.markDirty(this.player);
			this.back.accept(this.player);
			return;
		}
		if (this.slot < d.showcase.size()) d.showcase.set(this.slot, a.id());
		else d.showcase.add(a.id());
		while (d.showcase.size() > 3) d.showcase.removeLast();
		CollectorMod.STORE.markDirty(this.player);
		this.back.accept(this.player);
	}

	private void turn(int step) {
		this.page += step;
		refresh();
	}
}
