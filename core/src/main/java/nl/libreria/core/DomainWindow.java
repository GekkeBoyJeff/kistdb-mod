package nl.libreria.core;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.gui.Canvas;
import nl.libreria.core.gui.CanvasWindow;
import nl.libreria.core.gui.SettingsWindow;

/**
 * One domain page as a window of our own: a list, one row per button of the domain (its picture on the left, its
 * name on a card; what it does is in the tooltip), and at the bottom Back and the domain's settings.
 */
public final class DomainWindow extends CanvasWindow {
	/** Rows for entries: the six of the chest and the three of the inventory (the hotbar row is for Back). */
	private static final int[] ALL_ROWS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
	/** Below the card at the top (Progress without the pack's Progress window). */
	private static final int[] UNDER_CARD = {2, 3, 4, 5, 6, 7, 8};
	/** The page of rows shown, when there are more than fit. */
	private int page;

	private final Domain domain;
	/** A short value right of a row's name ("24%" for the server goal), by the name of the row. */
	private static final java.util.Map<String, java.util.function.Function<ServerPlayer, String>> VALUES = new java.util.HashMap<>();

	/** Shows {@code value} (short; null or "" = nothing) on the right of the row called {@code name}. */
	public static void value(String name, java.util.function.Function<ServerPlayer, String> value) {
		VALUES.put(name, value);
	}

	private DomainWindow(ServerPlayer p, Domain d) {
		super(p);
		this.domain = d;
	}

	public static void open(ServerPlayer p, Domain d) {
		new DomainWindow(p, d).show();
	}

	/** One row of the list. */
	private record Row(ItemStack icon, String name, String hint, List<Component> lore, int tint,
			java.util.function.Consumer<ServerPlayer> action) {
	}

	private List<Row> rows() {
		List<Row> out = new ArrayList<>();
		for (Module.HubEntry e : Module.hubEntries(this.domain)) {
			String about = String.join(" ", e.lore());
			// A player head is your own head
			ItemStack icon = e.icon() == Items.PLAYER_HEAD
					? new eu.pb4.sgui.api.elements.GuiElementBuilder(Items.PLAYER_HEAD).setProfile(this.player.getGameProfile()).asStack()
					: new ItemStack(e.icon());
			out.add(new Row(icon, e.name(), firstSentence(about), lore(about), this.domain.rgb, e.action()));
		}
		if (this.domain == Domain.PLACES && Guide.has(this.player)) {
			out.add(new Row(new ItemStack(Items.SPECTRAL_ARROW), "Stop showing the way", "Removes the beam",
					lore("Removes the beam you're following."), 0xE06A5A, pl -> {
						Guide.clear(pl);
						Notify.whisper(pl, "Stopped showing the way", ChatFormatting.GRAY);
						open(pl, this.domain);
					}));
		}
		return out;
	}

	/** The rows of this page. */
	private List<Row> pageRows() {
		List<Row> all = rows();
		int per = rowsFor().length;
		this.page = Math.max(0, Math.min(this.page, pages(all) - 1));
		int from = this.page * per;
		return all.subList(Math.min(from, all.size()), Math.min(from + per, all.size()));
	}

	private int pages(List<Row> all) {
		int per = rowsFor().length;
		return Math.max(1, (all.size() + per - 1) / per);
	}

	/** The two lines of the card at the top of Progress (without its own window), or null for the other places. */
	private String[] card() {
		switch (this.domain) {
			case PROGRESS -> {
				Services.CardData d = Services.progress().map(pr -> pr.cardData(this.player)).orElse(null);
				if (d == null) return null;
				return new String[] {"Achievements " + d.earned() + " / " + d.total(), "",
						"Items " + d.items() + " / " + d.itemsTotal(), d.points() + " points"};
			}
			default -> {
				return null;
			}
		}
	}

	private int[] rowsFor() {
		return card() != null ? UNDER_CARD : ALL_ROWS;
	}

	@Override
	protected Canvas draw() {
		Canvas c = new Canvas().panel();
		c.text("Libreria", 8, 5, StartWindow.MUTED);
		int x = 8 + nl.libreria.core.gui.Glyphs.width(Component.literal("Libreria"));
		c.text(" › ", x, 5, StartWindow.MUTED);
		c.text(this.domain.label, x + nl.libreria.core.gui.Glyphs.width(Component.literal(" › ")), 5, this.domain.rgb);
		List<Row> rows = pageRows();
		int[] ROWS = rowsFor();
		String[] card = card();
		if (card != null) {
			// The card at the top: who you are (You) or how far you are (Progress); not a button
			int x0 = Canvas.cellX(0) + Canvas.CARD_MARGIN + 6, x1 = Canvas.cellX(9) - Canvas.CARD_MARGIN - 6;
			c.card(0, 0, 9, 2, this.domain.rgb);
			// Two lines, each with something on the left and (maybe) something on the right
			int r1 = card[1].isEmpty() ? 0 : Math.min(76, nl.libreria.core.gui.Glyphs.width(Component.literal(card[1])) + 8);
			int r2 = card[3].isEmpty() ? 0 : Math.min(76, nl.libreria.core.gui.Glyphs.width(Component.literal(card[3])) + 8);
			if (r1 > 0) c.right(Canvas.fit(card[1], r1 - 8), x1, Canvas.cellY(0) + 9, StartWindow.MUTED);
			c.text(Canvas.fit(card[0], x1 - x0 - r1), x0, Canvas.cellY(0) + 9, StartWindow.TEXT);
			if (r2 > 0) c.right(Canvas.fit(card[3], r2 - 8), x1, Canvas.cellY(0) + 20, StartWindow.MUTED);
			c.text(Canvas.fit(card[2], x1 - x0 - r2), x0, Canvas.cellY(0) + 20, StartWindow.MUTED);
		}
		if (rows.isEmpty()) c.centred("Nothing here on this server yet.", 88, 60, StartWindow.MUTED);
		for (int i = 0; i < rows.size(); i++) {
			Row r = rows.get(i);
			int row = ROWS[i];
			// The row's card is as high as the picture beside it, with a little room between them
			int x0 = Canvas.cellX(1) + 4, w = Canvas.cellX(9) - Canvas.CARD_MARGIN - x0;
			c.line(x0, row, w, r.tint());
			int left = x0 + 6, right = x0 + w - 6;
			int y = Canvas.cellY(row) + 5;
			var f = VALUES.get(r.name());
			String value = f == null ? null : f.apply(this.player);
			int vw = value == null || value.isEmpty() ? 0 : nl.libreria.core.gui.Glyphs.width(Component.literal(value)) + 8;
			if (vw > 0) c.right(value, right - 9, y, StartWindow.MUTED);
			c.text(Canvas.fit(r.name(), right - left - 10 - vw), left, y, StartWindow.TEXT);
			c.text("›", right - 3, y, StartWindow.MUTED);
		}
		boolean settings = SettingsWindow.has(this.domain);
		int pages = pages(rows());
		if (pages > 1) {
			small(c, 0, 9, 3, "Back");
			if (this.page > 0) small(c, 3, 9, 1, "‹");
			c.centred((this.page + 1) + "/" + pages, Canvas.cellX(4) + 9, Canvas.cellY(9) + 5, StartWindow.MUTED);
			if (this.page < pages - 1) small(c, 5, 9, 1, "›");
			if (settings) small(c, 6, 9, 3, "Settings");
		} else {
			small(c, 0, 9, settings ? 4 : 9, "Back");
			if (settings) small(c, 4, 9, 5, "Settings");
		}
		return c;
	}

	private static void small(Canvas c, int col, int row, int w, String label) {
		c.card(col, row, w, 1, StartWindow.NEUTRAL);
		c.centred(label, Canvas.cellX(col) + 9 * w, Canvas.cellY(row) + 5, StartWindow.TEXT);
	}

	@Override
	protected void fill() {
		List<Row> rows = pageRows();
		int[] ROWS = rowsFor();
		for (int i = 0; i < rows.size(); i++) {
			Row r = rows.get(i);
			Component name = Ui.text(r.name(), r.tint()).withStyle(ChatFormatting.BOLD);
			area(1, ROWS[i], 8, 1, name, r.lore(), r.action());
			icon(0, ROWS[i], r.icon(), name, r.lore(), r.action());
		}
		boolean settings = SettingsWindow.has(this.domain);
		Component back = Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
		Component set = Ui.text("Settings", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
		java.util.function.Consumer<ServerPlayer> toSettings = pl -> SettingsWindow.open(pl, this.domain, q -> open(q, this.domain));
		int pages = pages(rows());
		if (pages > 1) {
			area(0, 9, 3, 1, back, lore("To the start screen."), Start::open);
			if (this.page > 0) area(3, 9, 1, 1, Ui.text("Previous page", ChatFormatting.WHITE), List.of(), pl -> turn(-1));
			if (this.page < pages - 1) area(5, 9, 1, 1, Ui.text("Next page", ChatFormatting.WHITE), List.of(), pl -> turn(1));
			if (settings) area(6, 9, 3, 1, set, lore("Your settings for " + this.domain.label + "."), toSettings);
		} else {
			area(0, 9, settings ? 4 : 9, 1, back, lore("To the start screen."), Start::open);
			if (settings) area(4, 9, 5, 1, set, lore("Your settings for " + this.domain.label + "."), toSettings);
		}
	}

	private void turn(int step) {
		this.page += step;
		Ui.click(this.player);
		refresh();
	}

	/** The first sentence (or the whole text if it has no full stop), for the hint on the right of a row. */
	private static String firstSentence(String about) {
		int dot = about.indexOf(". ");
		String s = dot > 0 ? about.substring(0, dot) : about;
		if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
		int colon = s.indexOf(':');
		return colon > 0 ? s.substring(0, colon) : s;
	}

	private static List<Component> lore(String about) {
		List<Component> out = new ArrayList<>();
		for (String l : Canvas.wrap(about, 180)) out.add(Ui.text(l, ChatFormatting.GRAY));
		out.add(Component.empty());
		out.add(Ui.text("▸ Click: open", ChatFormatting.YELLOW));
		return out;
	}
}
