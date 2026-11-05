package nl.libreria.collector.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.collector.Goals;
import nl.libreria.core.Domain;
import nl.libreria.core.Players;
import nl.libreria.core.Start;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Canvas;
import nl.libreria.core.gui.CanvasWindow;
import nl.libreria.core.gui.Glyphs;

/**
 * The server goal, as a window of our own: a card with the goal and the time left, a bar with how far the server
 * is, the five who did the most (you highlighted), and the past goals.
 */
public final class GoalWindow extends CanvasWindow {
	private static final int TEXT = 0xFFFFFF, OTHER = 0x6E7480;
	private static final int[] TOP = {3, 4, 5, 6, 7};
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

	private boolean past;
	private int page;

	private GoalWindow(ServerPlayer p, boolean past, int page) {
		super(p);
		this.past = past;
		this.page = page;
	}

	public static void open(ServerPlayer p) {
		new GoalWindow(p, false, 0).show();
	}

	public static void openPast(ServerPlayer p) {
		new GoalWindow(p, true, 0).show();
	}

	private MinecraftServer server() {
		return this.player.level().getServer();
	}

	/** Who did what, the most first. */
	private static List<Map.Entry<UUID, Long>> ranking(Map<UUID, Long> done) {
		List<Map.Entry<UUID, Long>> out = new ArrayList<>();
		for (Map.Entry<UUID, Long> e : done.entrySet()) if (e.getValue() > 0) out.add(e);
		out.sort(Comparator.comparingLong((Map.Entry<UUID, Long> e) -> e.getValue()).reversed());
		return out;
	}

	private static String left(long ends) {
		long ms = ends - System.currentTimeMillis();
		if (ms <= 0) return "Ending";
		long h = ms / 3_600_000L;
		if (h >= 48) return h / 24 + " days left";
		if (h >= 1) return h + (h == 1 ? " hour left" : " hours left");
		return Math.max(1, ms / 60_000L) + " min left";
	}

	private String name(UUID id) {
		Players.Known k = Players.get(server(), id);
		return k == null ? "Someone" : k.label();
	}

	private ItemStack head(UUID id) {
		Players.Known k = Players.get(server(), id);
		GuiElementBuilder b = new GuiElementBuilder(Items.PLAYER_HEAD);
		if (k != null && k.online() != null) b.setProfile(k.online().getGameProfile());
		else b.setProfile(id);
		return b.asStack();
	}

	// ---------------------------------------------------------------- drawing

	@Override
	protected Canvas draw() {
		return this.past ? drawPast() : drawNow();
	}

	private void crumbs(Canvas c, String last) {
		int x = 8;
		c.text(Domain.PROGRESS.label, x, 5, Domain.PROGRESS.rgb);
		x += Glyphs.width(Component.literal(Domain.PROGRESS.label));
		c.text(" › ", x, 5, Glyphs.muted);
		c.text(last, x + Glyphs.width(Component.literal(" › ")), 5, TEXT);
	}

	private Canvas drawNow() {
		Canvas c = new Canvas().panel();
		crumbs(c, "Server goal");
		Goals.Running r = Goals.now();
		int x0 = Canvas.cellX(0) + Canvas.CARD_MARGIN + 6, x1 = Canvas.cellX(9) - Canvas.CARD_MARGIN - 6;
		c.card(0, 0, 9, 2, Domain.PROGRESS.rgb);
		if (r == null) {
			c.text("No server goal right now", x0, Canvas.cellY(0) + 9, TEXT);
			c.text(Canvas.fit(Goals.ON.get() ? "The next one starts soon." : "Server goals are off here.", x1 - x0), x0, Canvas.cellY(0) + 20,
					Glyphs.muted);
		} else {
			Goals.Kind k = Goals.kind(r.kind);
			String time = left(r.ends);
			int tw = Glyphs.width(Component.literal(time)) + 8;
			c.right(time, x1, Canvas.cellY(0) + 9, Glyphs.muted);
			c.text(Canvas.fit(k.name(), x1 - x0 - tw), x0, Canvas.cellY(0) + 9, TEXT);
			c.text(Canvas.fit(k.text(r.target) + ", together", x1 - x0), x0, Canvas.cellY(0) + 20, Glyphs.muted);
			// The bar, with the score on it
			int bx = Canvas.cellX(0) + Canvas.CARD_MARGIN, bw = 18 * 9 - 2 * Canvas.CARD_MARGIN;
			long total = Goals.total(r);
			c.bar(bx, 2, bw, total / (double) Math.max(1, r.target), Domain.PROGRESS.rgb);
			c.centred(Ui.n(total / k.divisor()) + " / " + Ui.n(r.target / k.divisor()) + "   " + Goals.percent(r) + "%", bx + bw / 2,
					Canvas.cellY(2) + 5, TEXT);
			// The five who did the most
			List<Map.Entry<UUID, Long>> rank = ranking(r.done);
			int left = Canvas.cellX(1) + 4, width = Canvas.cellX(9) - Canvas.CARD_MARGIN - left;
			int mine = -1;
			for (int i = 0; i < rank.size(); i++) if (rank.get(i).getKey().equals(this.player.getUUID())) mine = i;
			for (int i = 0; i < TOP.length && i < rank.size(); i++) row(c, TOP[i], i, rank.get(i), k, left, width);
			if (mine >= TOP.length) {
				row(c, 8, mine, rank.get(mine), k, left, width);
			} else if (rank.isEmpty()) {
				c.centred("Nobody has started yet. Be the first!", 88, Canvas.cellY(4) + 5, Glyphs.muted);
			} else {
				c.centred(k.hard() ? "Most at the end: Legendary" : "Most at the end: Epic", 88, Canvas.cellY(8) + 5, Glyphs.muted);
			}
		}
		small(c, 0, 4, "Back");
		small(c, 4, 5, "Past goals");
		return c;
	}

	private void row(Canvas c, int row, int place, Map.Entry<UUID, Long> e, Goals.Kind k, int left, int width) {
		boolean me = e.getKey().equals(this.player.getUUID());
		c.line(left, row, width, me ? Domain.PROGRESS.rgb : OTHER);
		int y = Canvas.cellY(row) + 5;
		String n = Ui.n(e.getValue() / k.divisor());
		int nw = Glyphs.width(Component.literal(n));
		c.right(n, left + width - 6, y, me ? TEXT : Glyphs.muted);
		c.text(Canvas.fit((place + 1) + ". " + name(e.getKey()), width - 12 - nw - 8), left + 6, y, TEXT);
	}

	private List<Goals.Past> pastList() {
		List<Goals.Past> out = new ArrayList<>();
		var all = nl.libreria.collector.Goals.past();
		for (int i = all.size() - 1; i >= 0; i--) if (Goals.kind(all.get(i).kind) != null) out.add(all.get(i));
		return out;
	}

	private static final int[] PAST_ROWS = {0, 1, 2, 3, 4, 5, 6, 7, 8};

	private Canvas drawPast() {
		Canvas c = new Canvas().panel();
		List<Goals.Past> list = pastList();
		int pages = Math.max(1, (list.size() + PAST_ROWS.length - 1) / PAST_ROWS.length);
		this.page = Math.max(0, Math.min(this.page, pages - 1));
		int x = 8;
		c.text("Server goal", x, 5, Domain.PROGRESS.rgb);
		x += Glyphs.width(Component.literal("Server goal"));
		c.text(" › ", x, 5, Glyphs.muted);
		c.text("Past goals", x + Glyphs.width(Component.literal(" › ")), 5, TEXT);
		int left = Canvas.cellX(1) + 4, width = Canvas.cellX(9) - Canvas.CARD_MARGIN - left;
		int from = this.page * PAST_ROWS.length;
		for (int i = 0; i < PAST_ROWS.length && from + i < list.size(); i++) {
			Goals.Past p = list.get(from + i);
			Goals.Kind k = Goals.kind(p.kind);
			int row = PAST_ROWS[i];
			c.line(left, row, width, p.reached ? Domain.PROGRESS.rgb : OTHER);
			int y = Canvas.cellY(row) + 5;
			String right = p.reached ? DATE.format(Instant.ofEpochMilli(p.ended).atZone(ZoneId.systemDefault()))
					: (p.total * 100 / Math.max(1, p.target)) + "%";
			int rw = Glyphs.width(Component.literal(right));
			c.right(right, left + width - 6, y, Glyphs.muted);
			c.text(Canvas.fit(k.name(), width - 12 - rw - 8), left + 6, y, p.reached ? TEXT : 0x9AA0AC);
		}
		if (list.isEmpty()) c.centred("No goals have ended yet.", 88, Canvas.cellY(3), Glyphs.muted);
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
		c.centred(label, Canvas.cellX(col) + 9 * w, Canvas.cellY(9) + 5, TEXT);
	}

	// ---------------------------------------------------------------- cells

	@Override
	protected void fill() {
		if (this.past) {
			fillPast();
			return;
		}
		Goals.Running r = Goals.now();
		Component bold = Ui.text("Server goal", Domain.PROGRESS.rgb).withStyle(ChatFormatting.BOLD);
		List<Component> about = new ArrayList<>();
		about.add(Ui.text("One goal for everybody together.", ChatFormatting.GRAY));
		about.add(Ui.text("Only what you do while it runs counts.", ChatFormatting.GRAY));
		about.add(Component.empty());
		about.add(Ui.text("Reached in time: everyone who did at least", ChatFormatting.GRAY));
		about.add(Ui.text("1% gets its achievement; the one who did", ChatFormatting.GRAY));
		about.add(Ui.text("the most gets its Champion achievement.", ChatFormatting.GRAY));
		if (r != null) {
			Goals.Kind k = Goals.kind(r.kind);
			bold = Ui.text(k.name(), Domain.PROGRESS.rgb).withStyle(ChatFormatting.BOLD);
			about.addFirst(Ui.text(k.text(r.target) + ", together", ChatFormatting.WHITE));
			long mine = r.done.getOrDefault(this.player.getUUID(), 0L);
			about.add(1, Ui.text("You: " + Ui.n(mine / k.divisor()), ChatFormatting.AQUA));
		}
		area(0, 0, 9, 2, bold, about, p -> {
		});
		if (r != null) {
			Goals.Kind k = Goals.kind(r.kind);
			area(0, 2, 9, 1, Ui.text(Goals.percent(r) + "%", Domain.PROGRESS.rgb).withStyle(ChatFormatting.BOLD),
					List.of(Ui.text(Ui.n(Goals.total(r) / k.divisor()) + " of " + Ui.n(r.target / k.divisor()), ChatFormatting.GRAY)), p -> {
					});
			List<Map.Entry<UUID, Long>> rank = ranking(r.done);
			for (int i = 0; i < TOP.length && i < rank.size(); i++) person(TOP[i], rank.get(i), k);
			for (int i = TOP.length; i < rank.size(); i++) {
				if (rank.get(i).getKey().equals(this.player.getUUID())) person(8, rank.get(i), k);
			}
		}
		area(0, 9, 4, 1, Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), List.of(Ui.text("To Progress.", ChatFormatting.GRAY)),
				p -> Start.domain(p, Domain.PROGRESS));
		area(4, 9, 5, 1, Ui.text("Past goals", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD),
				List.of(Ui.text("Every goal so far, and who did the most.", ChatFormatting.GRAY)), p -> {
					this.past = true;
					this.page = 0;
					refresh();
				});
	}

	private void person(int row, Map.Entry<UUID, Long> e, Goals.Kind k) {
		Component name = Ui.text(name(e.getKey()), Domain.PROGRESS.rgb).withStyle(ChatFormatting.BOLD);
		List<Component> lore = List.of(Ui.text(Ui.n(e.getValue() / k.divisor()) + " for this goal", ChatFormatting.GRAY));
		area(1, row, 8, 1, name, lore, p -> {
		});
		icon(0, row, head(e.getKey()), name, lore, p -> {
		});
	}

	private void fillPast() {
		List<Goals.Past> list = pastList();
		int pages = Math.max(1, (list.size() + PAST_ROWS.length - 1) / PAST_ROWS.length);
		int from = this.page * PAST_ROWS.length;
		for (int i = 0; i < PAST_ROWS.length && from + i < list.size(); i++) {
			Goals.Past p = list.get(from + i);
			Goals.Kind k = Goals.kind(p.kind);
			List<Component> lore = new ArrayList<>();
			lore.add(Ui.text(k.text(p.target) + ", together", ChatFormatting.WHITE));
			lore.add(Ui.text(p.reached ? "✔ Reached" : "✘ Ran out at " + (p.total * 100 / Math.max(1, p.target)) + "%",
					p.reached ? ChatFormatting.GREEN : ChatFormatting.RED));
			List<Map.Entry<UUID, Long>> rank = ranking(p.done);
			for (int j = 0; j < 3 && j < rank.size(); j++) {
				lore.add(Ui.text((j + 1) + ". " + name(rank.get(j).getKey()) + " · " + Ui.n(rank.get(j).getValue() / k.divisor()),
						j == 0 && p.reached ? ChatFormatting.GOLD : ChatFormatting.GRAY));
			}
			long mine = p.done.getOrDefault(this.player.getUUID(), 0L);
			if (mine > 0) lore.add(Ui.text("You: " + Ui.n(mine / k.divisor()), ChatFormatting.AQUA));
			Component name = Ui.text(k.name(), p.reached ? Domain.PROGRESS.rgb : 0x9AA0AC).withStyle(ChatFormatting.BOLD);
			area(1, PAST_ROWS[i], 8, 1, name, lore, pl -> {
			});
			icon(0, PAST_ROWS[i], new ItemStack(k.icon()), name, lore, pl -> {
			});
		}
		Component back = Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
		java.util.function.Consumer<ServerPlayer> toNow = p -> {
			this.past = false;
			refresh();
		};
		if (pages > 1) {
			area(0, 9, 3, 1, back, List.of(), toNow);
			if (this.page > 0) area(3, 9, 1, 1, Ui.text("Previous page", ChatFormatting.WHITE), List.of(), p -> turn(-1));
			if (this.page < pages - 1) area(5, 9, 1, 1, Ui.text("Next page", ChatFormatting.WHITE), List.of(), p -> turn(1));
		} else {
			area(0, 9, 9, 1, back, List.of(), toNow);
		}
	}

	private void turn(int step) {
		this.page += step;
		refresh();
	}
}
