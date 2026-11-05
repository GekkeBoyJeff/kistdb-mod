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

	private static String emblem(Domain d) {
		return switch (d) {
			case ITEMS -> "chest";
			case PLACES -> "map";
			case PROGRESS -> "trophy";
			case YOU -> "tag";
		};
	}

	/** Where each domain's card sits (column, row). */
	private static int[] spot(Domain d) {
		return switch (d) {
			case ITEMS -> new int[] {0, 0};
			case PLACES -> new int[] {5, 0};
			case PROGRESS -> new int[] {0, 3};
			case YOU -> new int[] {5, 3};
		};
	}

	@Override
	protected Canvas draw() {
		Canvas c = new Canvas().panel();
		c.text("Libreria", 8, 5, TITLE);
		c.right(this.player.getName().getString(), 168, 5, MUTED);
		for (Domain d : Domain.values()) {
			int[] s = spot(d);
			c.card(s[0], s[1], 4, 3, d.rgb);
			int x0 = Canvas.cellX(s[0]) + Canvas.CARD_MARGIN, y0 = Canvas.cellY(s[1]) + Canvas.CARD_MARGIN;
			int w = 18 * 4 - 2 * Canvas.CARD_MARGIN;
			c.picture("big." + emblem(d), x0 + (w - 24) / 2, y0 + 6, d.rgb);
			List<String> lines = Canvas.wrap(d.label, w - 6);
			int y = lines.size() == 1 ? y0 + 36 : y0 + 32;
			for (String l : lines) {
				c.centred(l, x0 + w / 2, y, TEXT);
				y += 9;
			}
		}
		// Between the cards: settings and getting started
		small(c, 1, "sm.gear");
		small(c, 4, "sm.help");
		// Your places, over the first two rows of where your inventory is
		c.card(0, 6, 9, 2, Domain.PLACES.rgb);
		int x0 = Canvas.cellX(0) + Canvas.CARD_MARGIN, y0 = Canvas.cellY(6) + Canvas.CARD_MARGIN;
		c.picture("big.compass", x0 + 5, y0 + 4, Domain.PLACES.rgb);
		c.text("Your places", x0 + 36, y0 + 7, TEXT);
		int places = PlaceList.any() ? PlaceList.all(this.player).size() : 0;
		c.text(places == 0 ? "Nothing to show the way to yet" : places + (places == 1 ? " place" : " places") + " to be shown the way to",
				x0 + 36, y0 + 18, MUTED);
		// The small ones
		row(c, 8, new String[] {"Chronicle", "Tips", "Commands"});
		c.card(0, 9, 3, 1, NEUTRAL);
		c.centred("Links", Canvas.cellX(0) + 27, Canvas.cellY(9) + 5, TEXT);
		c.card(6, 9, 3, 1, 0x9A8C74);
		c.centred("Close", Canvas.cellX(6) + 27, Canvas.cellY(9) + 5, TEXT);
		c.centred("G opens this", Canvas.cellX(4) + 9, Canvas.cellY(9) + 5, MUTED);
		return c;
	}

	private static void small(Canvas c, int row, String glyph) {
		c.card(4, row, 1, 1, NEUTRAL);
		c.picture(glyph, Canvas.cellX(4) + Canvas.CARD_MARGIN + 3, Canvas.cellY(row) + Canvas.CARD_MARGIN + 3, 0xFFE6B0);
	}

	private static void row(Canvas c, int row, String[] labels) {
		for (int i = 0; i < labels.length; i++) {
			c.card(3 * i, row, 3, 1, NEUTRAL);
			c.centred(labels[i], Canvas.cellX(3 * i) + 27, Canvas.cellY(row) + 5, TEXT);
		}
	}

	@Override
	protected void fill() {
		for (Domain d : Domain.values()) {
			int[] s = spot(d);
			area(s[0], s[1], 4, 3, d.title().withStyle(ChatFormatting.BOLD), lore(d.blurb, "Click: open"), p -> Start.domain(p, d));
		}
		area(4, 1, 1, 1, Ui.text("Settings", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD),
				lore("Your own settings, by the same four places.", "Click: open"), SettingsWindow::open);
		area(4, 4, 1, 1, Ui.text("Getting started", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD),
				lore("Keys, tips and links.", "Click: open"), Welcome::guide);
		item(0, 8, "Chronicle", "What happened in this world.", Chronicle::open);
		item(3, 8, "Tips", "Every tip, by the four places.", Tips::all);
		item(6, 8, "Commands", "Every command, in short.", Welcome::commands);
		item(0, 9, "Links", "The server's website, Discord and rules.", Links::open);
		area(6, 9, 3, 1, Ui.text("Close", ChatFormatting.GRAY), List.of(), p -> p.closeContainer());
		area(3, 9, 3, 1, Ui.text("Libreria", ChatFormatting.WHITE), lore("Press G (or Libreria in the pause menu) to come back here."), null);
		area(0, 6, 9, 2, Ui.text("Your places", Domain.PLACES.rgb).withStyle(ChatFormatting.BOLD),
				lore("Your waypoints, land, graves, pets and the places you found: click one to be shown the way.", "Click: open"),
				PlaceList::open);
	}

	private void item(int col, int row, String name, String about, Consumer<ServerPlayer> action) {
		area(col, row, 3, 1, Ui.text(name, ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), lore(about, "Click: open"), action);
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
