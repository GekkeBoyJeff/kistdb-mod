package nl.libreria.core;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import nl.libreria.core.gui.Canvas;
import nl.libreria.core.gui.CanvasWindow;
import nl.libreria.core.gui.Glyphs;
import nl.libreria.core.gui.SettingsWindow;

/**
 * The start screen as a window of our own: Progress the width of the window (how far you are, your place), under it
 * Land &amp; places and You side by side, below them your places, and under that settings, the chronicle, tips and
 * help, on the same two columns.
 */
public final class StartWindow extends CanvasWindow {
	/** Cards that belong to no domain, quiet text and the title: in the pack's look. */
	static final int NEUTRAL = nl.libreria.core.gui.Glyphs.neutral;
	static final int TEXT = 0xFFFFFF, MUTED = nl.libreria.core.gui.Glyphs.muted, TITLE = nl.libreria.core.gui.Glyphs.titleColor;

	private StartWindow(ServerPlayer p) {
		super(p);
	}

	public static void open(ServerPlayer p) {
		new StartWindow(p).show();
	}

	/** Minecraft's picture for each domain. */
	private static String icon(Domain d) {
		return switch (d) {
			case PLACES -> "filled_map";
			case PROGRESS -> "nether_star";
			case YOU -> "name_tag";
		};
	}

	/** The cards: {column, row, width}. Progress the whole width, the other two on two equal columns (column 4 between). */
	private static int[] spot(Domain d) {
		return switch (d) {
			case PROGRESS -> new int[] {0, 0, 9};
			case PLACES -> new int[] {0, 3, 4};
			case YOU -> new int[] {5, 3, 4};
		};
	}

	private static final int PAD = 6;
	/** A card four cells wide. */
	private static final int CARD_W = 18 * 4 - 2 * Canvas.CARD_MARGIN;

	@Override
	protected Canvas draw() {
		Canvas c = new Canvas().panel();
		c.text("Libreria", 8, 5, TITLE);
		c.right(Canvas.fit(this.player.getName().getString(), 90), 168, 5, MUTED);
		for (Domain d : Domain.values()) {
			int[] s = spot(d);
			int x0 = Canvas.cellX(s[0]) + Canvas.CARD_MARGIN, y0 = Canvas.cellY(s[1]) + Canvas.CARD_MARGIN;
			c.card(s[0], s[1], s[2], 3, d.rgb);
			c.picture("ico16." + icon(d), x0 + PAD, y0 + 6, 0xFFFFFF);
			if (d == Domain.PROGRESS) progress(c, x0 + 18 * 9 - 2 * Canvas.CARD_MARGIN - PAD, y0);
			// The name at the bottom of the card, in two lines when it doesn't fit in one
			List<String> lines = Canvas.wrap(d.label, CARD_W - 2 * PAD);
			int y = lines.size() == 1 ? y0 + 36 : y0 + 27;
			for (String l : lines.subList(0, Math.min(2, lines.size()))) {
				c.text(Canvas.fit(l, CARD_W - 2 * PAD), x0 + PAD, y, TEXT);
				y += 10;
			}
		}
		// Your places, the width of the window, and under it the small ones on the same two columns as the cards
		int x0 = Canvas.cellX(0) + Canvas.CARD_MARGIN, y0 = Canvas.cellY(6) + Canvas.CARD_MARGIN;
		int inner = 18 * 9 - 2 * Canvas.CARD_MARGIN - 2 * PAD;
		c.card(0, 6, 9, 2, Domain.PLACES.rgb);
		List<PlaceList.Place> places = PlaceList.any() ? PlaceList.all(this.player) : List.of();
		if (!places.isEmpty()) c.right(Integer.toString(places.size()), x0 + PAD + inner, y0 + 7, MUTED);
		c.text("Your places", x0 + PAD, y0 + 7, TEXT);
		String[] near = nearest(places);
		int farW = near[1].isEmpty() ? 0 : Glyphs.width(Component.literal(near[1])) + 8;
		if (!near[1].isEmpty()) c.right(near[1], x0 + PAD + inner, y0 + 19, MUTED);
		c.text(Canvas.fit(near[0], inner - farW), x0 + PAD, y0 + 19, MUTED);
		small(c, 0, 8, "Settings");
		small(c, 5, 8, "Chronicle");
		small(c, 0, 9, "Tips");
		small(c, 5, 9, "Help");
		return c;
	}

	/** On the Progress card, on the right: how far you are and your place among everybody. */
	private void progress(Canvas c, int right, int y0) {
		try {
			var server = this.player.level().getServer();
			Completion.Snap snap = Completion.of(server, this.player.getUUID(), 10_000);
			var all = Completion.ranking(server, Completion.OVERALL);
			int rank = Completion.rank(all, this.player.getUUID());
			c.right(Completion.percent(Completion.overall(snap)) + "% complete", right, y0 + 8, TEXT);
			if (rank > 0) c.right("#" + rank + " of " + all.size() + (all.size() == 1 ? " player" : " players"), right, y0 + 19, MUTED);
		} catch (RuntimeException e) {
			Libreria.LOG.warn("Start screen: could not count your progress", e);
		}
	}

	/** {"Nearest: Home", "12 m N"} (in this world), or what to do when there's nothing yet; the distance stands apart so it is never cut off. */
	private String[] nearest(List<PlaceList.Place> places) {
		String dim = this.player.level().dimension().identifier().toString();
		PlaceList.Place best = null;
		double bestD = Double.MAX_VALUE;
		for (PlaceList.Place p : places) {
			if (!p.dim.equals(dim) || p.isDimmed()) continue;
			double d = p.pos.distanceToSqr(this.player.position());
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		if (best == null) return new String[] {places.isEmpty() ? "Land, graves, pets and more" : "None in this world", ""};
		double dx = best.pos.x - this.player.getX(), dz = best.pos.z - this.player.getZ();
		double d = Math.sqrt(bestD);
		String far = d < 8 ? "right here" : d >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f km", d / 1000) : Math.round(d) + " m";
		String[] winds = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
		String way = d < 8 ? "" : " " + winds[(int) Math.floorMod(Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0), 8)];
		return new String[] {"Nearest: " + best.name, far + way};
	}

	/** A one-row card four cells wide, its label centred. */
	private static void small(Canvas c, int col, int row, String label) {
		c.card(col, row, 4, 1, NEUTRAL);
		c.centred(Canvas.fit(label, CARD_W - 8), Canvas.cellX(col) + 36, Canvas.cellY(row) + 5, TEXT);
	}

	@Override
	protected void fill() {
		for (Domain d : Domain.values()) {
			int[] s = spot(d);
			area(s[0], s[1], s[2], 3, d.title().withStyle(ChatFormatting.BOLD), lore(d.blurb, "Click: open"), p -> Start.domain(p, d));
		}
		area(0, 6, 9, 2, Ui.text("Your places", Domain.PLACES.rgb).withStyle(ChatFormatting.BOLD),
				lore("Your land, graves, pets and the places you found: click one to be shown the way.", "Click: open"),
				PlaceList::open);
		item(0, 8, "Settings", "Your own settings, by the same three places.", SettingsWindow::open);
		item(5, 8, "Chronicle", "What happened in this world.", Chronicle::open);
		item(0, 9, "Tips", "Every tip, by the three places.", Tips::all);
		item(5, 9, "Help", "Keys, commands and the server's links. Press {menu} (or Libreria in the pause menu) to come back here.",
				Welcome::guide);
	}

	private void item(int col, int row, String name, String about, Consumer<ServerPlayer> action) {
		area(col, row, 4, 1, Ui.text(name, ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), lore(about, "Click: open"), action);
	}

	/** Grey description (wrapped) and a yellow action line. */
	private static List<Component> lore(String about, String... actions) {
		java.util.ArrayList<Component> out = new java.util.ArrayList<>();
		for (String l : Canvas.wrap(about, 180)) out.add(Ui.text(l, ChatFormatting.GRAY));
		if (actions.length > 0) out.add(Component.empty());
		for (String a : actions) out.add(Ui.text("▸ " + a, ChatFormatting.YELLOW));
		return out;
	}
}
