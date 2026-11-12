package nl.libreria.core;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.gui.Canvas;
import nl.libreria.core.gui.CanvasWindow;
import nl.libreria.core.gui.Glyphs;

/**
 * The top lists. Without a board: every list (everything together, each collection, each counter) with your place on
 * it. With a board: who has the most, you highlighted, the previous and next list on the card; click someone to see
 * their progress.
 */
public final class TopListsWindow extends CanvasWindow {
	private static final int[] LIST_ROWS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
	private static final int[] PLACE_ROWS = {2, 3, 4, 5, 6, 7, 8};

	/** The board shown, or null for the list of boards. */
	private String board;
	private int page;
	private final Consumer<ServerPlayer> back;
	private List<Completion.Place> ranking = List.of();

	private TopListsWindow(ServerPlayer p, String board, Consumer<ServerPlayer> back) {
		super(p);
		this.board = board;
		this.back = back;
	}

	/** Every list. */
	public static void open(ServerPlayer p, Consumer<ServerPlayer> back) {
		new TopListsWindow(p, null, back).show();
	}

	/** One list. */
	public static void board(ServerPlayer p, String board, Consumer<ServerPlayer> back) {
		new TopListsWindow(p, board, back).show();
	}

	/** For the self-tests: the picture of a list (null: every list), as JSON. */
	static String picture(ServerPlayer p, String board) {
		TopListsWindow w = new TopListsWindow(p, board, NOTE);
		w.load();
		return w.pictureJson();
	}

	private MinecraftServer server() {
		return this.player.level().getServer();
	}

	@Override
	public void show() {
		load();
		super.show();
	}

	private void load() {
		if (this.board != null) this.ranking = Completion.ranking(server(), this.board);
	}

	private Consumer<ServerPlayer> reopen() {
		String b = this.board;
		int p = this.page;
		return q -> {
			TopListsWindow w = new TopListsWindow(q, b, this.back);
			w.page = p;
			w.show();
		};
	}

	// ---------------------------------------------------------------- the list of lists

	private int listPages() {
		return Math.max(1, (Completion.boards().size() + LIST_ROWS.length - 1) / LIST_ROWS.length);
	}

	/** Your number and place on a board, short: "9/41 · #4". */
	private String mine(String board) {
		List<Completion.Place> r = Completion.ranking(server(), board);
		for (Completion.Place p : r) {
			if (!p.who().equals(this.player.getUUID())) continue;
			return "#" + Completion.rank(r, p.who());
		}
		return "";
	}

	// ---------------------------------------------------------------- picture

	@Override
	protected Canvas draw() {
		Canvas c = new Canvas().panel();
		if (this.board == null) {
			ProgressWindow.crumbs2(c, "Progress", "Top lists");
			List<String> all = Completion.boards();
			int from = this.page * LIST_ROWS.length;
			for (int i = 0; i < LIST_ROWS.length && from + i < all.size(); i++) {
				String b = all.get(from + i);
				int row = LIST_ROWS[i];
				int x0 = Canvas.cellX(1) + 4, w = Canvas.cellX(9) - Canvas.CARD_MARGIN - x0;
				boolean counter = Completion.counter(b) != null;
				c.line(x0, row, w, counter ? ProgressWindow.OTHER : ProgressWindow.GOLD);
				int y = Canvas.cellY(row) + 5, right = x0 + w - 6;
				String v = mine(b);
				int vw = v.isEmpty() ? 0 : Glyphs.width(Component.literal(v)) + 8;
				if (!v.isEmpty()) c.right(v, right - 9, y, Glyphs.muted);
				c.text(Canvas.fit(Completion.boardName(b), w - 22 - vw), x0 + 6, y, StartWindow.TEXT);
				c.text("›", right - 3, y, Glyphs.muted);
			}
			bottom(c, listPages(), Services.menu(Services.MENU_RECORDS).isPresent() ? "Records" : null);
			return c;
		}
		ProgressWindow.crumbs2(c, "Top lists", Completion.boardName(this.board));
		// The card: which list, how many on it, and the lists before and after it
		int x0 = Canvas.cellX(0) + Canvas.CARD_MARGIN + 6, x1 = Canvas.cellX(9) - Canvas.CARD_MARGIN - 6;
		c.card(0, 0, 9, 2, ProgressWindow.GOLD);
		String count = this.ranking.size() + (this.ranking.size() == 1 ? " player" : " players");
		int cw = Glyphs.width(Component.literal(count)) + 8;
		c.right(count, x1, Canvas.cellY(0) + 9, Glyphs.muted);
		c.text(Canvas.fit(Completion.boardName(this.board), x1 - x0 - cw), x0, Canvas.cellY(0) + 9, StartWindow.TEXT);
		String prev = "‹ " + Completion.boardName(step(-1)), next = Completion.boardName(step(1)) + " ›";
		c.text(Canvas.fit(prev, 70), x0, Canvas.cellY(0) + 20, Glyphs.muted);
		c.right(Canvas.fit(next, 70), x1, Canvas.cellY(0) + 20, Glyphs.muted);
		// The places
		List<int[]> shown = places();
		long best = 1;
		for (Completion.Place p : this.ranking) best = Math.max(best, p.value());
		for (int i = 0; i < shown.size(); i++) {
			int row = shown.get(i)[0], at = shown.get(i)[1];
			Completion.Place p = this.ranking.get(at);
			boolean me = p.who().equals(this.player.getUUID());
			int left = Canvas.cellX(1) + 4, w = Canvas.cellX(9) - Canvas.CARD_MARGIN - left;
			c.line(left, row, w, me ? ProgressWindow.GOLD : ProgressWindow.OTHER);
			int y = Canvas.cellY(row) + 4, right = left + w - 6;
			String v = Completion.show(this.board, p.value(), p.total());
			int vw = Glyphs.width(Component.literal(v)) + 8;
			c.right(v, right - 9, y, me ? StartWindow.TEXT : Glyphs.muted);
			int rank = Completion.rank(this.ranking, p.who());
			c.text(Canvas.fit(rank + ". " + p.name(), w - 22 - vw), left + 6, y, StartWindow.TEXT);
			c.text("›", right - 3, y, Glyphs.muted);
			double frac = p.total() > 0 ? p.value() / (double) p.total() : p.value() / (double) best;
			c.thin(left + 6, row, w - 22, frac, me ? ProgressWindow.GOLD : 0x9AA0AC);
		}
		if (this.ranking.isEmpty()) c.centred("Nobody yet. Be the first!", 88, Canvas.cellY(4) + 5, Glyphs.muted);
		bottom(c, placePages(), "All lists");
		return c;
	}

	private void bottom(Canvas c, int pages, String right) {
		if (pages > 1) {
			ProgressWindow.pager(c, this.page, pages, right);
		} else if (right != null) {
			ProgressWindow.small(c, 0, 4, "Back");
			ProgressWindow.small(c, 4, 5, right);
		} else {
			ProgressWindow.small(c, 0, 9, "Back");
		}
	}

	private int placePages() {
		return Math.max(1, (this.ranking.size() + PLACE_ROWS.length - 1) / PLACE_ROWS.length);
	}

	/**
	 * Which places go in which rows: {row, index}. On the first page, when you are further down than the page reaches,
	 * the last row is yours.
	 */
	private List<int[]> places() {
		List<int[]> out = new ArrayList<>();
		int from = this.page * PLACE_ROWS.length;
		int mine = -1;
		for (int i = 0; i < this.ranking.size(); i++) if (this.ranking.get(i).who().equals(this.player.getUUID())) mine = i;
		int room = PLACE_ROWS.length;
		boolean pinMe = this.page == 0 && mine >= room;
		for (int i = 0; i < room - (pinMe ? 1 : 0) && from + i < this.ranking.size(); i++) out.add(new int[] {PLACE_ROWS[i], from + i});
		if (pinMe) out.add(new int[] {PLACE_ROWS[room - 1], mine});
		return out;
	}

	private String step(int by) {
		List<String> all = Completion.boards();
		int i = all.indexOf(this.board);
		return all.get(Math.floorMod((i < 0 ? 0 : i) + by, all.size()));
	}

	// ---------------------------------------------------------------- cells

	@Override
	protected void fill() {
		Component back = Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
		if (this.board == null) {
			List<String> all = Completion.boards();
			int from = this.page * LIST_ROWS.length;
			for (int i = 0; i < LIST_ROWS.length && from + i < all.size(); i++) {
				String b = all.get(from + i);
				Completion.Counter k = Completion.counter(b);
				Completion.Collection col = Completion.get(b);
				List<Component> lore = new ArrayList<>();
				String about = Completion.OVERALL.equals(b) ? "Everything together: the average of every collection."
						: col != null ? col.about() : k != null ? k.about() : "";
				for (String l : Canvas.wrap(about, 180)) lore.add(Ui.text(l, ChatFormatting.GRAY));
				List<Completion.Place> r = Completion.ranking(server(), b);
				for (Completion.Place me : r) {
					if (me.who().equals(this.player.getUUID())) {
						lore.add(Ui.text("You: " + Completion.show(b, me.value(), me.total()) + " · #" + Completion.rank(r, me.who()), ChatFormatting.AQUA));
					}
				}
				for (int j = 0; j < 3 && j < r.size(); j++) {
					Completion.Place p = r.get(j);
					lore.add(Ui.text((j + 1) + ". " + p.name() + "  " + Completion.show(b, p.value(), p.total()),
							j == 0 ? ChatFormatting.GOLD : ChatFormatting.WHITE));
				}
				lore.add(Component.empty());
				lore.add(Ui.text("▸ Click: the whole list", ChatFormatting.YELLOW));
				Component label = Ui.text(Completion.boardName(b), ProgressWindow.GOLD).withStyle(ChatFormatting.BOLD);
				Consumer<ServerPlayer> go = q -> board(q, b, reopen());
				area(1, LIST_ROWS[i], 8, 1, label, lore, go);
				icon(0, LIST_ROWS[i], new ItemStack(Completion.boardIcon(b)), label, lore, go);
			}
			boolean records = Services.menu(Services.MENU_RECORDS).isPresent();
			pager(listPages(), back, records ? Ui.text("Records", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD) : null,
					List.of(Ui.text("The server's records: who holds them.", ChatFormatting.GRAY)),
					q -> Services.menu(Services.MENU_RECORDS).ifPresent(m -> m.accept(q)));
			return;
		}
		// The card: left half the previous list, right half the next
		String prev = step(-1), next = step(1);
		area(0, 0, 4, 2, Ui.text("‹ " + Completion.boardName(prev), ProgressWindow.GOLD).withStyle(ChatFormatting.BOLD),
				List.of(Ui.text("▸ Click: the previous list", ChatFormatting.YELLOW)), q -> switchTo(prev));
		area(4, 0, 1, 2, Ui.text(Completion.boardName(this.board), ProgressWindow.GOLD).withStyle(ChatFormatting.BOLD),
				List.of(Ui.text(this.ranking.size() + " players", ChatFormatting.GRAY)), NOTE);
		area(5, 0, 4, 2, Ui.text(Completion.boardName(next) + " ›", ProgressWindow.GOLD).withStyle(ChatFormatting.BOLD),
				List.of(Ui.text("▸ Click: the next list", ChatFormatting.YELLOW)), q -> switchTo(next));
		for (int[] s : places()) {
			Completion.Place p = this.ranking.get(s[1]);
			Players.Known k = Players.get(server(), p.who());
			int rank = Completion.rank(this.ranking, p.who());
			List<Component> lore = new ArrayList<>();
			lore.add(Ui.text("#" + rank + " · " + Completion.show(this.board, p.value(), p.total()), ChatFormatting.WHITE));
			if (k != null) lore.add(Ui.text(k.online() != null ? "Here now" : "Last here: " + Players.ago(k.last()).toLowerCase(java.util.Locale.ROOT),
					ChatFormatting.GRAY));
			lore.add(Component.empty());
			lore.add(Ui.text("▸ Click: their progress", ChatFormatting.YELLOW));
			Component label = Ui.text(p.name(), ProgressWindow.GOLD).withStyle(ChatFormatting.BOLD);
			Consumer<ServerPlayer> go = q -> ProgressWindow.open(q, p.who(), "Top lists", reopen());
			area(1, s[0], 8, 1, label, lore, go);
			ItemStack head = k != null ? ProfileWindow.head(k) : new GuiElementBuilder(Items.PLAYER_HEAD).setProfile(p.who()).asStack();
			icon(0, s[0], head, label, lore, go);
		}
		pager(placePages(), back, Ui.text("All lists", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD),
				List.of(Ui.text("Every top list.", ChatFormatting.GRAY)), q -> open(q, this.back));
	}

	private void pager(int pages, Component back, Component right, List<Component> rightLore, Consumer<ServerPlayer> rightAction) {
		if (pages > 1) {
			area(0, 9, 2, 1, back, List.of(), this.back);
			if (this.page > 0) area(2, 9, 1, 1, Ui.text("Previous page", ChatFormatting.WHITE), List.of(), q -> turn(-1));
			if (this.page < pages - 1) area(5, 9, 1, 1, Ui.text("Next page", ChatFormatting.WHITE), List.of(), q -> turn(1));
			if (right != null) area(6, 9, 3, 1, right, rightLore, rightAction);
		} else if (right != null) {
			area(0, 9, 4, 1, back, List.of(), this.back);
			area(4, 9, 5, 1, right, rightLore, rightAction);
		} else {
			area(0, 9, 9, 1, back, List.of(), this.back);
		}
	}

	private void switchTo(String b) {
		this.board = b;
		this.page = 0;
		Ui.click(this.player);
		load();
		refresh();
	}

	private void turn(int step) {
		this.page += step;
		Ui.click(this.player);
		refresh();
	}
}
