package nl.libreria.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import nl.libreria.core.gui.Canvas;
import nl.libreria.core.gui.CanvasWindow;
import nl.libreria.core.gui.Glyphs;

/**
 * Progress: everything there is to complete, in three tabs (Collect, Do, Explore). A card with how far you are overall
 * and your place among everybody; one row per collection with how many you have and a thin bar; under it the top
 * lists, the server goal and your statistics. The same window shows anyone else's progress (from a top list or their
 * profile), then without the buttons that are only yours.
 */
public final class ProgressWindow extends CanvasWindow {
	static final int GOLD = Domain.PROGRESS.rgb, COMPLETE = 0x7CE07C, OTHER = 0x6E7480;
	private static final int[] ROWS = {2, 3, 4, 5, 6, 7, 8};
	/** The tab each player looked at last, so coming back lands there. */
	private static final Map<UUID, Completion.Tab> LAST = new HashMap<>();

	private final UUID who;
	private final String name;
	private final String via;
	private final Consumer<ServerPlayer> back;
	private Completion.Tab tab;
	private Completion.Snap snap;
	private List<Completion.Place> overall;

	private ProgressWindow(ServerPlayer viewer, UUID who, String name, String via, Consumer<ServerPlayer> back) {
		super(viewer);
		this.who = who;
		this.name = name;
		this.via = via;
		this.back = back;
		this.tab = LAST.getOrDefault(viewer.getUUID(), Completion.Tab.COLLECT);
	}

	/** Your own progress. */
	public static void open(ServerPlayer p) {
		open(p, p.getUUID(), "", Start::open);
	}

	/** {@code who}'s progress, opened from {@code via} ("Top lists", "Players"…; "" for your own from the start screen). */
	public static void open(ServerPlayer viewer, UUID who, String via, Consumer<ServerPlayer> back) {
		Players.Known k = Players.get(viewer.level().getServer(), who);
		String name = k != null ? k.label() : viewer.getUUID().equals(who) ? viewer.getName().getString() : "Someone";
		new ProgressWindow(viewer, who, name, via, back).show();
	}

	private boolean own() {
		return this.who.equals(this.player.getUUID());
	}

	private MinecraftServer server() {
		return this.player.level().getServer();
	}

	/** Counts again (each time the window is drawn anew). */
	private void count() {
		this.snap = Completion.of(server(), this.who, 2_000);
		this.overall = Completion.ranking(server(), Completion.OVERALL);
	}

	private Consumer<ServerPlayer> reopen() {
		return q -> open(q, this.who, this.via, this.back);
	}

	// ---------------------------------------------------------------- picture

	@Override
	protected Canvas draw() {
		if (this.snap == null) count();
		Canvas c = new Canvas().panel();
		if (own()) crumbs(c, "Libreria", Domain.PROGRESS.label);
		else crumbs(c, this.via.isEmpty() ? "Progress" : this.via, this.name);

		// The card: how far, and your place
		int x0 = Canvas.cellX(0) + Canvas.CARD_MARGIN + 6, x1 = Canvas.cellX(9) - Canvas.CARD_MARGIN - 6;
		c.card(0, 0, 9, 1, GOLD);
		int pct = Completion.percent(Completion.overall(this.snap));
		int rank = Completion.rank(this.overall, this.who);
		String place = rank > 0 ? "#" + rank + " of " + this.overall.size() + "  ›" : "";
		int pw = place.isEmpty() ? 0 : Glyphs.width(Component.literal(place)) + 8;
		if (!place.isEmpty()) c.right(place, x1, Canvas.cellY(0) + 5, Glyphs.muted);
		String left = own() ? "Completion " + pct + "%" : this.name + " · " + pct + "%";
		c.text(Canvas.fit(left, x1 - x0 - pw), x0, Canvas.cellY(0) + 5, StartWindow.TEXT);

		// The tabs
		Completion.Tab[] tabs = Completion.Tab.values();
		for (int i = 0; i < tabs.length; i++) {
			boolean on = tabs[i] == this.tab;
			c.card(i * 3, 1, 3, 1, on ? GOLD : Glyphs.neutral);
			c.centred(tabs[i].label, Canvas.cellX(i * 3) + 27, Canvas.cellY(1) + 5, on ? StartWindow.TEXT : Glyphs.muted);
		}

		// One row per collection
		List<Completion.Collection> list = Completion.of(this.tab);
		for (int i = 0; i < ROWS.length && i < list.size(); i++) row(c, ROWS[i], list.get(i));
		if (list.isEmpty()) c.centred("Nothing here on this server yet.", 88, Canvas.cellY(4) + 5, Glyphs.muted);

		if (own()) {
			small(c, 0, 2, "Back");
			small(c, 2, 3, "Top lists");
			small(c, 5, 2, "Goal");
			small(c, 7, 2, "Stats");
		} else {
			small(c, 0, 4, "Back");
			small(c, 4, 5, "Profile");
		}
		return c;
	}

	private void row(Canvas c, int row, Completion.Collection col) {
		int have = this.snap.have.getOrDefault(col.id, 0), total = col.total();
		boolean done = total > 0 && have >= total;
		int tint = done ? COMPLETE : GOLD;
		int x0 = Canvas.cellX(1) + 4, w = Canvas.cellX(9) - Canvas.CARD_MARGIN - x0;
		c.line(x0, row, w, tint);
		int left = x0 + 6, right = x0 + w - 6, y = Canvas.cellY(row) + 4;
		String value = Ui.n(have) + "/" + Ui.n(total);
		int vw = Glyphs.width(Component.literal(value)) + 8;
		c.right(value, right - 9, y, done ? StartWindow.TEXT : Glyphs.muted);
		c.text(Canvas.fit(col.name, right - left - 10 - vw), left, y, StartWindow.TEXT);
		c.text("›", right - 3, y, Glyphs.muted);
		c.thin(left, row, right - left - 10, total <= 0 ? 0 : have / (double) total, tint);
	}

	/** "Libreria › Progress", or "Top lists › Anna". */
	static void crumbs(Canvas c, String first, String last) {
		int x = 8;
		c.text(first, x, 5, Glyphs.muted);
		x += Glyphs.width(Component.literal(first));
		c.text(" › ", x, 5, Glyphs.muted);
		x += Glyphs.width(Component.literal(" › "));
		c.text(Canvas.fit(last, 168 - x), x, 5, GOLD);
	}

	/** "Progress › Tasted": the first in gold, the second white. */
	static void crumbs2(Canvas c, String first, String last) {
		int x = 8;
		c.text(first, x, 5, GOLD);
		x += Glyphs.width(Component.literal(first));
		c.text(" › ", x, 5, Glyphs.muted);
		x += Glyphs.width(Component.literal(" › "));
		c.text(Canvas.fit(last, 168 - x), x, 5, StartWindow.TEXT);
	}

	/** A small button in the bottom row. */
	static void small(Canvas c, int col, int w, String label) {
		c.card(col, 9, w, 1, Glyphs.neutral);
		c.centred(label, Canvas.cellX(col) + 9 * w, Canvas.cellY(9) + 5, StartWindow.TEXT);
	}

	// ---------------------------------------------------------------- cells

	@Override
	protected void fill() {
		if (this.snap == null) count();
		int pct = Completion.percent(Completion.overall(this.snap));
		int rank = Completion.rank(this.overall, this.who);
		List<Component> about = new ArrayList<>();
		about.add(Ui.text(pct + "% of everything there is to do here", ChatFormatting.WHITE));
		about.add(Ui.text(Completion.complete(this.snap) + " of " + Completion.all().size() + " collections complete", ChatFormatting.GRAY));
		if (rank > 0) about.add(Ui.text("#" + rank + " of " + this.overall.size() + " on this server", ChatFormatting.GRAY));
		about.add(Component.empty());
		about.add(Ui.text("Every collection counts the same,", ChatFormatting.DARK_GRAY));
		about.add(Ui.text("however big it is.", ChatFormatting.DARK_GRAY));
		about.add(Component.empty());
		about.add(Ui.text("▸ Click: the top list", ChatFormatting.YELLOW));
		area(0, 0, 9, 1, Ui.text(own() ? "Completion" : this.name, GOLD).withStyle(ChatFormatting.BOLD), about,
				q -> TopListsWindow.board(q, Completion.OVERALL, reopen()));

		for (Completion.Tab t : Completion.Tab.values()) {
			List<Component> lore = new ArrayList<>();
			lore.add(Ui.text(t.about, ChatFormatting.GRAY));
			for (Completion.Collection col : Completion.of(t)) {
				lore.add(Ui.text("· " + col.name + "  " + this.snap.have.getOrDefault(col.id, 0) + "/" + col.total(), ChatFormatting.DARK_GRAY));
			}
			if (t != this.tab) {
				lore.add(Component.empty());
				lore.add(Ui.text("▸ Click: show", ChatFormatting.YELLOW));
			}
			area(t.ordinal() * 3, 1, 3, 1, Ui.text(t.label, GOLD).withStyle(ChatFormatting.BOLD), lore, q -> {
				this.tab = t;
				LAST.put(q.getUUID(), t);
				Ui.click(q);
				refresh();
			});
		}

		List<Completion.Collection> list = Completion.of(this.tab);
		for (int i = 0; i < ROWS.length && i < list.size(); i++) {
			Completion.Collection col = list.get(i);
			int have = this.snap.have.getOrDefault(col.id, 0), total = col.total();
			List<Completion.Place> board = Completion.ranking(server(), col.id);
			int r = Completion.rank(board, this.who);
			List<Component> lore = new ArrayList<>();
			for (String l : Canvas.wrap(col.about(), 180)) lore.add(Ui.text(l, ChatFormatting.GRAY));
			lore.add(Component.empty());
			lore.add(Ui.text(Ui.n(have) + " of " + Ui.n(total) + " · " + Completion.percent(total == 0 ? 0 : have / (double) total) + "%",
					have >= total ? ChatFormatting.GREEN : ChatFormatting.WHITE));
			if (r > 0) lore.add(Ui.text("#" + r + " of " + board.size() + " on this server", ChatFormatting.GRAY));
			lore.add(Component.empty());
			lore.add(Ui.text("▸ Click: see them", ChatFormatting.YELLOW));
			Component label = Ui.text(col.name, have >= total ? COMPLETE : GOLD).withStyle(ChatFormatting.BOLD);
			Consumer<ServerPlayer> go = q -> CollectionWindow.open(q, this.who, col, reopen());
			area(1, ROWS[i], 8, 1, label, lore, go);
			icon(0, ROWS[i], new ItemStack(col.icon), label, lore, go);
		}

		Component bold = Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
		if (own()) {
			area(0, 9, 2, 1, bold, List.of(Ui.text("To the start screen.", ChatFormatting.GRAY)), this.back);
			area(2, 9, 3, 1, Ui.text("Top lists", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD),
					List.of(Ui.text("Who did the most of each: click", ChatFormatting.GRAY), Ui.text("someone to see their progress.", ChatFormatting.GRAY)),
					q -> TopListsWindow.open(q, reopen()));
			area(5, 9, 2, 1, Ui.text("Server goal", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD),
					List.of(Ui.text("One goal for everybody together.", ChatFormatting.GRAY)), q -> menu(q, Services.MENU_GOAL));
			area(7, 9, 2, 1, Ui.text("Statistics", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD),
					List.of(Ui.text("Your statistics, readable.", ChatFormatting.GRAY)), q -> menu(q, Services.MENU_STATS));
		} else {
			area(0, 9, 4, 1, bold, List.of(), this.back);
			area(4, 9, 5, 1, Ui.text("Profile", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD),
					List.of(Ui.text(this.name + "'s profile.", ChatFormatting.GRAY)), q -> ProfileWindow.open(q, this.who, "Progress", reopen()));
		}
	}

	private static void menu(ServerPlayer p, String key) {
		var opener = Services.menu(key);
		if (opener.isPresent()) opener.get().accept(p);
		else Notify.warn(p, net.minecraft.world.item.Items.BARRIER, "Not on this server", "That part of Libreria isn't installed here.");
	}
}
