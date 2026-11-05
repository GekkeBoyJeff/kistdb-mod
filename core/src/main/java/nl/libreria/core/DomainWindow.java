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
	private static final int[] ROWS = {0, 1, 2, 3, 4, 5, 6, 7, 8};

	private final Domain domain;

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
			out.add(new Row(new ItemStack(e.icon()), e.name(), firstSentence(about), lore(about), this.domain.rgb, e.action()));
		}
		if (this.domain == Domain.PLACES && Guide.has(this.player)) {
			out.add(new Row(new ItemStack(Items.SPECTRAL_ARROW), "Stop showing the way", "Removes the beam",
					lore("Removes the beam you're following."), 0xE06A5A, pl -> {
						Guide.clear(pl);
						Notify.whisper(pl, "Stopped showing the way", ChatFormatting.GRAY);
						open(pl, this.domain);
					}));
		}
		return out.size() > ROWS.length ? out.subList(0, ROWS.length) : out;
	}

	@Override
	protected Canvas draw() {
		Canvas c = new Canvas().panel();
		c.text("Libreria", 8, 5, StartWindow.MUTED);
		int x = 8 + nl.libreria.core.gui.Glyphs.width(Component.literal("Libreria"));
		c.text(" › ", x, 5, StartWindow.MUTED);
		c.text(this.domain.label, x + nl.libreria.core.gui.Glyphs.width(Component.literal(" › ")), 5, this.domain.rgb);
		List<Row> rows = rows();
		if (rows.isEmpty()) c.centred("Nothing here on this server yet.", 88, 60, StartWindow.MUTED);
		for (int i = 0; i < rows.size(); i++) {
			Row r = rows.get(i);
			int row = ROWS[i];
			// The row's card is as high as the picture beside it, with a little room between them
			int x0 = Canvas.cellX(1) + 4, w = Canvas.cellX(9) - Canvas.CARD_MARGIN - x0;
			c.line(x0, row, w, r.tint());
			int left = x0 + 6, right = x0 + w - 6;
			int y = Canvas.cellY(row) + 5;
			c.text(Canvas.fit(r.name(), right - left - 10), left, y, StartWindow.TEXT);
			c.text("›", right - 3, y, StartWindow.MUTED);
		}
		boolean settings = SettingsWindow.has(this.domain);
		small(c, 0, 9, settings ? 4 : 9, "Back");
		if (settings) small(c, 4, 9, 5, "Settings");
		return c;
	}

	private static void small(Canvas c, int col, int row, int w, String label) {
		c.card(col, row, w, 1, StartWindow.NEUTRAL);
		c.centred(label, Canvas.cellX(col) + 9 * w, Canvas.cellY(row) + 5, StartWindow.TEXT);
	}

	@Override
	protected void fill() {
		List<Row> rows = rows();
		for (int i = 0; i < rows.size(); i++) {
			Row r = rows.get(i);
			Component name = Ui.text(r.name(), r.tint()).withStyle(ChatFormatting.BOLD);
			area(1, ROWS[i], 8, 1, name, r.lore(), r.action());
			icon(0, ROWS[i], r.icon(), name, r.lore(), r.action());
		}
		boolean settings = SettingsWindow.has(this.domain);
		area(0, 9, settings ? 4 : 9, 1, Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD),
				lore("To the start screen."), Start::open);
		if (settings) {
			area(4, 9, 5, 1, Ui.text("Settings", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD),
					lore("Your settings for " + this.domain.label + "."), pl -> SettingsWindow.open(pl, this.domain, q -> open(q, this.domain)));
		}
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
