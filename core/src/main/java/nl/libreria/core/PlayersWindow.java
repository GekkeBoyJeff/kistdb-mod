package nl.libreria.core;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import nl.libreria.core.gui.Canvas;
import nl.libreria.core.gui.CanvasWindow;
import nl.libreria.core.gui.Glyphs;

/**
 * Everybody who has played here, as a list: who's online first, then the most recently seen. Click someone: their
 * profile.
 */
public final class PlayersWindow extends CanvasWindow {
	private static final int[] ROWS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
	private int page;

	private PlayersWindow(ServerPlayer p, int page) {
		super(p);
		this.page = page;
	}

	public static void open(ServerPlayer p) {
		open(p, 0);
	}

	static void open(ServerPlayer p, int page) {
		new PlayersWindow(p, page).show();
	}

	private List<Players.Known> all() {
		return Players.all(this.player.level().getServer());
	}

	private int pages(List<Players.Known> all) {
		return Math.max(1, (all.size() + ROWS.length - 1) / ROWS.length);
	}

	private String status(Players.Known k) {
		if (k.online() == null) return k.last() > 0 ? Players.ago(k.last()) : "";
		return k.id().equals(this.player.getUUID()) ? "Online (you)" : "Online";
	}

	@Override
	protected Canvas draw() {
		List<Players.Known> all = all();
		int pages = pages(all);
		this.page = Math.max(0, Math.min(this.page, pages - 1));
		Canvas c = new Canvas().panel();
		int x = 8;
		c.text(Domain.YOU.label, x, 5, Domain.YOU.rgb);
		x += Glyphs.width(Component.literal(Domain.YOU.label));
		c.text(" › ", x, 5, Glyphs.muted);
		c.text("Players", x + Glyphs.width(Component.literal(" › ")), 5, StartWindow.TEXT);
		c.right(all.size() + (all.size() == 1 ? " player" : " players"), 168 - 2, 5, Glyphs.muted);

		int left = Canvas.cellX(1) + 4, width = Canvas.cellX(9) - Canvas.CARD_MARGIN - left;
		int from = this.page * ROWS.length;
		for (int i = 0; i < ROWS.length && from + i < all.size(); i++) {
			Players.Known k = all.get(from + i);
			int row = ROWS[i];
			boolean here = k.online() != null;
			c.line(left, row, width, here ? Domain.YOU.rgb : 0x8A909C);
			int y = Canvas.cellY(row) + 5;
			String s = status(k);
			int sw = Glyphs.width(Component.literal(s));
			c.right(s, left + width - 6, y, here ? ProfileWindow.ONLINE : Glyphs.muted);
			c.text(Canvas.fit(k.label(), width - 12 - sw - 8), left + 6, y, StartWindow.TEXT);
		}
		if (pages > 1) {
			small(c, 0, 3, "Back");
			if (this.page > 0) small(c, 3, 1, "‹");
			c.centred((this.page + 1) + "/" + pages, Canvas.cellX(4) + 9, Canvas.cellY(9) + 5, Glyphs.muted);
			if (this.page < pages - 1) small(c, 5, 1, "›");
		} else {
			small(c, 0, 9, "Back");
		}
		return c;
	}

	private static void small(Canvas c, int col, int w, String label) {
		c.card(col, 9, w, 1, Glyphs.neutral);
		c.centred(label, Canvas.cellX(col) + 9 * w, Canvas.cellY(9) + 5, StartWindow.TEXT);
	}

	@Override
	protected void fill() {
		List<Players.Known> all = all();
		int pages = pages(all);
		int from = this.page * ROWS.length;
		int page = this.page;
		for (int i = 0; i < ROWS.length && from + i < all.size(); i++) {
			Players.Known k = all.get(from + i);
			Component name = Ui.text(k.label(), Domain.YOU.rgb).withStyle(ChatFormatting.BOLD);
			List<Component> lore = List.of(Ui.text(status(k), k.online() != null ? ChatFormatting.GREEN : ChatFormatting.GRAY), Component.empty(),
					Ui.text("▸ Click: their profile", ChatFormatting.YELLOW));
			java.util.function.Consumer<ServerPlayer> show = p -> ProfileWindow.open(p, k.id(), "Players", q -> open(q, page));
			area(1, ROWS[i], 8, 1, name, lore, show);
			icon(0, ROWS[i], ProfileWindow.head(k), name, lore, show);
		}
		Component back = Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
		if (pages > 1) {
			area(0, 9, 3, 1, back, List.of(), p -> Start.domain(p, Domain.YOU));
			if (this.page > 0) area(3, 9, 1, 1, Ui.text("Previous page", ChatFormatting.WHITE), List.of(), p -> turn(-1));
			if (this.page < pages - 1) area(5, 9, 1, 1, Ui.text("Next page", ChatFormatting.WHITE), List.of(), p -> turn(1));
		} else {
			area(0, 9, 9, 1, back, List.of(), p -> Start.domain(p, Domain.YOU));
		}
	}

	private void turn(int step) {
		this.page += step;
		refresh();
	}
}
