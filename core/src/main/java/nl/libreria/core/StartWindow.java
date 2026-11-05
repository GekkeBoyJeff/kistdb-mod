package nl.libreria.core;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import nl.libreria.core.gui.Canvas;
import nl.libreria.core.gui.CanvasWindow;
import nl.libreria.core.gui.SettingsWindow;

/**
 * The start screen as a window of our own: four big cards (one per {@link Domain}), the settings and "getting
 * started" between them, and below them your places and the small things (chronicle, tips, commands, links).
 */
public final class StartWindow extends CanvasWindow {
	/** Cards that belong to no domain: bronze. */
	static final int NEUTRAL = 0xC8A060;
	static final int TEXT = 0xFFFFFF, MUTED = 0xB9AD95, TITLE = 0xECDCB4;

	private StartWindow(ServerPlayer p) {
		super(p);
	}

	public static void open(ServerPlayer p) {
		new StartWindow(p).show();
	}

	/** Minecraft's picture for each domain (twice as big on its card). */
	private static String icon(Domain d) {
		return switch (d) {
			case ITEMS -> "bundle";
			case PLACES -> "filled_map";
			case PROGRESS -> "nether_star";
			case YOU -> "name_tag";
		};
	}

	/** The cards, staggered (4 + 5 wide, then 5 + 4), so every cell belongs to one and they keep 4 px apart. */
	private static int[] spot(Domain d) {
		return switch (d) {
			case ITEMS -> new int[] {0, 0, 4};
			case PLACES -> new int[] {4, 0, 5};
			case PROGRESS -> new int[] {0, 3, 5};
			case YOU -> new int[] {5, 3, 4};
		};
	}

	private static final int PAD = 6;

	@Override
	protected Canvas draw() {
		Canvas c = new Canvas().panel();
		c.text("Libreria", 8, 5, TITLE);
		c.right(Canvas.fit(this.player.getName().getString(), 90), 168, 5, MUTED);
		for (Domain d : Domain.values()) {
			int[] s = spot(d);
			int x0 = Canvas.cellX(s[0]) + Canvas.CARD_MARGIN, y0 = Canvas.cellY(s[1]) + Canvas.CARD_MARGIN;
			int w = 18 * s[2] - 2 * Canvas.CARD_MARGIN;
			c.card(s[0], s[1], s[2], 3, d.rgb);
			c.icon(icon(d), x0 + PAD, s[1], 4);
			c.text(Canvas.fit(d.label, w - 2 * PAD), x0 + PAD, y0 + 39, TEXT);
		}
		// Below, where your inventory normally is: your places (lined up with Progress), and the small ones beside it
		int x0 = Canvas.cellX(0) + Canvas.CARD_MARGIN, y0 = Canvas.cellY(6) + Canvas.CARD_MARGIN;
		c.card(0, 6, 5, 3, Domain.PLACES.rgb);
		c.icon("compass_00", x0 + PAD, 6, 4);
		int places = PlaceList.any() ? PlaceList.all(this.player).size() : 0;
		if (places > 0) c.right(Integer.toString(places), x0 + 86 - PAD, y0 + PAD, MUTED);
		c.text("Your places", x0 + PAD, y0 + 39, TEXT);
		small(c, 5, 6, 4, "Settings");
		small(c, 5, 7, 4, "Chronicle");
		small(c, 5, 8, 4, "Tips");
		small(c, 0, 9, 5, "Getting started");
		small(c, 5, 9, 4, "Links");
		return c;
	}

	/** A one-row card with its label centred. */
	private static void small(Canvas c, int col, int row, int w, String label) {
		c.card(col, row, w, 1, NEUTRAL);
		int inner = 18 * w - 2 * Canvas.CARD_MARGIN - 8;
		c.centred(Canvas.fit(label, inner), Canvas.cellX(col) + 9 * w, Canvas.cellY(row) + 5, TEXT);
	}

	@Override
	protected void fill() {
		for (Domain d : Domain.values()) {
			int[] s = spot(d);
			area(s[0], s[1], s[2], 3, d.title().withStyle(ChatFormatting.BOLD), lore(d.blurb, "Click: open"), p -> Start.domain(p, d));
		}
		area(0, 6, 5, 3, Ui.text("Your places", Domain.PLACES.rgb).withStyle(ChatFormatting.BOLD),
				lore("Your waypoints, land, graves, pets and the places you found: click one to be shown the way.", "Click: open"),
				PlaceList::open);
		item(5, 6, 4, "Settings", "Your own settings, by the same four places.", SettingsWindow::open);
		item(5, 7, 4, "Chronicle", "What happened in this world.", Chronicle::open);
		item(5, 8, 4, "Tips", "Every tip, by the four places.", Tips::all);
		item(0, 9, 5, "Getting started", "Keys, tips and links. Press {menu} (or Libreria in the pause menu) to come back here.",
				Welcome::guide);
		item(5, 9, 4, "Links", "The server's website, Discord and rules.", Links::open);
	}

	private void item(int col, int row, int w, String name, String about, Consumer<ServerPlayer> action) {
		area(col, row, w, 1, Ui.text(name, ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), lore(about, "Click: open"), action);
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
