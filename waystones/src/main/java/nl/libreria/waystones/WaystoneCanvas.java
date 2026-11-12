package nl.libreria.waystones;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.Domain;
import nl.libreria.core.Notify;
import nl.libreria.core.PlaceList;
import nl.libreria.core.Players;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Window;
import nl.libreria.core.gui.Canvas;
import nl.libreria.core.gui.CanvasWindow;
import nl.libreria.core.gui.Glyphs;

/**
 * The waystone windows in Libreria's own look. One waystone (its owner, an operator, or for a village's who found it):
 * where it is and whose, its name (click: type a new one; owner and operators), who can use it (click: the next of only
 * the owner and who they trust, everyone who found it, everyone: on everyone's list; a village's has the last two), and
 * for operators: go there, remove it. All waystones
 * (operators): a list, the nearest first, with whose each is; click one for its window.
 */
final class WaystoneCanvas {
	static final int ACCENT = 0xC79BF0;
	private static final int TEXT = 0xFFFFFF, VALUE = 0x8FE3F0, DANGER = 0xE0605A;
	private static final int ROOM_FULL = 18 * 9 - 2 * Canvas.CARD_MARGIN - 12;
	private static final int ROOM = 18 * 8 - 2 * Canvas.CARD_MARGIN - 12;

	private WaystoneCanvas() {
	}

	static boolean available(ServerPlayer p) {
		return CanvasWindow.available(p);
	}

	private static int title(Canvas c, String first, int firstRgb, String last) {
		c.text(first, 8, 5, firstRgb);
		int x = 8 + Glyphs.width(Component.literal(first));
		c.text(" › ", x, 5, Glyphs.muted);
		x += Glyphs.width(Component.literal(" › "));
		c.text(Canvas.fit(last, 168 - x), x, 5, TEXT);
		return x;
	}

	private static void small(Canvas c, int col, int w, String label) {
		if (label.isEmpty()) return;
		c.card(col, 9, w, 1, Glyphs.neutral);
		c.centred(label, Canvas.cellX(col) + 9 * w, Canvas.cellY(9) + 5, TEXT);
	}

	private static String whose(Stones.Stone s) {
		if (s.village) return s.grand ? "A village's grand waystone" : "A village's waystone";
		if (s.owner == null) return s.grand ? "A grand waystone" : "Placed by the server";
		return (s.grand ? "Grand · placed by " : "Placed by ") + s.ownerName + (s.made > 0 ? ", " + Players.ago(s.made).toLowerCase(Locale.ROOT) : "");
	}

	private static String where(Stones.Stone s) {
		return Stones.world(s.dim) + " · " + s.x + " " + s.y + " " + s.z;
	}

	// ---------------------------------------------------------------- one waystone

	/** One waystone's window. {@code fromAdmin}: Back goes to the list of all waystones. */
	static final class One extends CanvasWindow {
		private final Stones.Stone stone;
		private final boolean fromAdmin;

		One(ServerPlayer p, Stones.Stone stone, boolean fromAdmin) {
			super(p);
			this.stone = stone;
			this.fromAdmin = fromAdmin;
		}

		private boolean op() {
			return Ui.isOp(this.player);
		}

		/** A grand waystone of theirs: they take it down (and get the waystone and the nether star). */
		private boolean takesDown() {
			return this.stone.grand && Stones.owns(this.player, this.stone);
		}

		private boolean canSecret() {
			return !this.stone.village && this.stone.owner != null;
		}

		/** May give it another name: its owner, an operator. */
		private boolean canName() {
			return Stones.owns(this.player, this.stone) || op();
		}

		/** May change who can use it: the owner or an operator; a village's, who found it too. */
		private boolean canChoose() {
			return canSecret() ? canName() : Stones.mayShare(this.player, this.stone);
		}

		private static final int PRIVATE = 0, OPEN = 1, SHARED = 2;

		private List<Integer> choices() {
			return canSecret() ? List.of(PRIVATE, OPEN, SHARED) : List.of(OPEN, SHARED);
		}

		private int now() {
			return this.stone.secret ? PRIVATE : this.stone.shared ? SHARED : OPEN;
		}

		private String said(int choice) {
			Stones.Stone s = this.stone;
			boolean mine = Stones.owns(this.player, s);
			return switch (choice) {
				case PRIVATE -> "Only " + (mine ? "you" : s.ownerName) + " and who " + (mine ? "you trust" : "they trust");
				case SHARED -> "On everyone's list";
				default -> "Everyone who found it";
			};
		}

		private int next() {
			List<Integer> c = choices();
			int at = c.indexOf(now());
			return c.get((at + 1) % c.size());
		}

		@Override
		protected Canvas draw() {
			Stones.Stone s = this.stone;
			Canvas c = new Canvas().panel();
			title(c, "Waystones", ACCENT, s.name);
			int left = Canvas.cellX(0) + Canvas.CARD_MARGIN + 6;
			// Where and whose (rows 0–1)
			c.card(0, 0, 9, 2, Glyphs.neutral);
			c.text(Canvas.fit(where(s), ROOM_FULL), left, Canvas.cellY(0) + 2 + (18 * 2 - 4 - 18) / 2, TEXT);
			c.text(Canvas.fit(whose(s), ROOM_FULL), left, Canvas.cellY(0) + 12 + (18 * 2 - 4 - 18) / 2, Glyphs.muted);
			// Its name (rows 2–3)
			c.card(0, 2, 9, 2, ACCENT);
			int y1 = Canvas.cellY(2) + 2 + (18 * 2 - 4 - 18) / 2;
			c.text("Name", left, y1, TEXT);
			c.text(Canvas.fit(s.name, ROOM_FULL - 16), left, y1 + 10, VALUE);
			if (canName()) c.text("›", Canvas.cellX(9) - Canvas.CARD_MARGIN - 8, y1 + 10, Glyphs.muted);
			// Who can use it (rows 4–5): only you and who you trust, who found it, everyone
			if (canSecret() || canChoose()) {
				c.card(0, 4, 9, 2, now() == OPEN ? Glyphs.neutral : ACCENT);
				int y = Canvas.cellY(4) + 2 + (18 * 2 - 4 - 18) / 2;
				c.text("Who can use it", left, y, TEXT);
				c.text(Canvas.fit(said(now()), ROOM_FULL - 16), left, y + 10, VALUE);
				if (canChoose()) c.text("›", Canvas.cellX(9) - Canvas.CARD_MARGIN - 8, y + 10, Glyphs.muted);
			}
			// Operators (rows 6–8)
			if (op()) {
				c.text("Operators", Canvas.cellX(0) + 3, Canvas.cellY(6) + 5, nl.libreria.core.gui.AdminCanvas.TINT);
				c.card(0, 7, 9, 1, Glyphs.neutral);
				c.text("Go there", left, Canvas.cellY(7) + 5, TEXT);
				c.text("›", Canvas.cellX(9) - Canvas.CARD_MARGIN - 8, Canvas.cellY(7) + 5, Glyphs.muted);
				c.card(0, 8, 9, 1, DANGER);
				c.text(takesDown() ? "Take it down" : "Remove it", left, Canvas.cellY(8) + 5, TEXT);
				c.text("›", Canvas.cellX(9) - Canvas.CARD_MARGIN - 8, Canvas.cellY(8) + 5, Glyphs.muted);
			} else if (takesDown()) {
				// Its owner: a grand waystone comes down from here
				c.text("Grand waystone", Canvas.cellX(0) + 3, Canvas.cellY(6) + 5, ACCENT);
				c.card(0, 7, 9, 1, DANGER);
				c.text("Take it down", left, Canvas.cellY(7) + 5, TEXT);
				c.text("›", Canvas.cellX(9) - Canvas.CARD_MARGIN - 8, Canvas.cellY(7) + 5, Glyphs.muted);
			}
			small(c, 0, 9, "Back");
			return c;
		}

		private void takeDownArea(int row) {
			area(0, row, 9, 1, Ui.text("Take it down", 0xE0605A).withStyle(ChatFormatting.BOLD), List.of(Ui.text("The shrine and its floor go;", ChatFormatting.GRAY),
					Ui.text("it's off everyone's list. You get the", ChatFormatting.GRAY), Ui.text("waystone and the nether star back.", ChatFormatting.GRAY),
					Component.empty(), Ui.text("▸ Click: take it down (you're asked first)", ChatFormatting.YELLOW)), p -> confirmTakeDown(p, this.stone, this.fromAdmin));
		}

		@Override
		protected void fill() {
			Stones.Stone s = this.stone;
			area(0, 0, 9, 2, Ui.text(s.name, ACCENT).withStyle(ChatFormatting.BOLD), List.of(Ui.text(where(s), ChatFormatting.WHITE),
					Ui.text(whose(s), ChatFormatting.GRAY)), NOTE);
			if (canName()) {
				area(0, 2, 9, 2, Ui.text("Name", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), List.of(Ui.text("Now: " + s.name, ChatFormatting.AQUA),
						Ui.text("Shown above the stone, in everyone's list", ChatFormatting.GRAY), Ui.text("and on the pearls bound to it.", ChatFormatting.GRAY),
						Component.empty(), Ui.text("▸ Click: give it another name", ChatFormatting.YELLOW)), p -> rename(p, s, this.fromAdmin));
			} else {
				area(0, 2, 9, 2, Ui.text("Name", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), List.of(Ui.text(s.name, ChatFormatting.AQUA),
						Ui.text("A village's waystone keeps its village's name.", ChatFormatting.GRAY)), NOTE);
			}
			if (canSecret() || canChoose()) {
				Component name = Ui.text("Who can use it", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
				List<Component> lore = new ArrayList<>();
				lore.add(Ui.text("Now: " + said(now()), ChatFormatting.AQUA));
				if (canSecret()) lore.add(Ui.text("Only " + (Stones.owns(this.player, s) ? "you" : "the owner") + " and who " + (Stones.owns(this.player, s) ? "you trust" : "they trust"),
						ChatFormatting.GRAY));
				lore.add(Ui.text("Everyone who found it: who right-clicked", ChatFormatting.GRAY));
				lore.add(Ui.text("it once can travel here.", ChatFormatting.GRAY));
				lore.add(Ui.text("On everyone's list: everyone can, found", ChatFormatting.GRAY));
				lore.add(Ui.text("or not.", ChatFormatting.GRAY));
				if (canChoose()) {
					lore.add(Component.empty());
					lore.add(Ui.text("▸ Click: " + said(next()).toLowerCase(java.util.Locale.ROOT), ChatFormatting.YELLOW));
				}
				area(0, 4, 9, 2, name, lore, p -> {
					if (!canChoose()) return;
					int to = next();
					s.secret = to == PRIVATE;
					s.shared = to == SHARED;
					Stones.STORE.markDirty();
					Notify.whisper(p, Ui.text(s.name + ": " + said(to).toLowerCase(java.util.Locale.ROOT), ChatFormatting.GRAY));
					refresh();
				});
			}
			if (op()) {
				area(0, 7, 9, 1, Ui.text("Go there", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD),
						List.of(Ui.text("Free, as an operator.", ChatFormatting.GRAY), Component.empty(), Ui.text("▸ Click: go", ChatFormatting.YELLOW)), p -> {
							ServerLevel level = Stones.check(p, s);
							if (level == null) return;
							p.closeContainer();
							Stones.go(p, level, s);
						});
				if (takesDown()) takeDownArea(8);
				else area(0, 8, 9, 1, Ui.text("Remove it", 0xE0605A).withStyle(ChatFormatting.BOLD), List.of(Ui.text(s.grand ? "The shrine and its floor go;"
						: "The stone and its altar go;", ChatFormatting.GRAY), Ui.text("it's off everyone's list. Nothing drops.", ChatFormatting.GRAY), Component.empty(),
						Ui.text("▸ Click: remove (you're asked first)", ChatFormatting.YELLOW)), p -> confirmRemove(p, s, this.fromAdmin));
			} else if (takesDown()) {
				takeDownArea(7);
			}
			area(0, 9, 9, 1, Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), List.of(), p -> {
				if (this.fromAdmin) All.open(p, 0);
				else PlaceList.open(p, WaystonePlaces.KIND);
			});
		}
	}

	/** (For the screenshots: the question, as from its window.) */
	static void askTakeDown(ServerPlayer p, Stones.Stone s) {
		confirmTakeDown(p, s, false);
	}

	/** Take it down: asked first; the waystone and the nether star come back. */
	private static void confirmTakeDown(ServerPlayer p, Stones.Stone s, boolean fromAdmin) {
		Window c = Window.path(s.name, "Take it down");
		c.text(Ui.text("Take " + s.name + " down? The shrine and its floor go, and it's off everyone's list. You get the waystone and the nether "
				+ "star back; the diamonds and the sculk are gone.", ChatFormatting.GRAY));
		c.button(Ui.text("Take it down", ChatFormatting.RED), null, (q, vv) -> {
			if (!Grand.mayTakeDown(q, s) || !s.grand) return;
			Grand.takeDown(s, Stones.level(q.level().getServer(), s.dim), q);
			if (fromAdmin) All.open(q, 0);
			else q.closeContainer();
		});
		c.cancel(q -> one(q, s, fromAdmin)).show(p);
	}

	/** Shift + right-click on your waystone, Settings in the list: its window. */
	static void one(ServerPlayer p, Stones.Stone s, boolean fromAdmin) {
		new One(p, s, fromAdmin).show();
	}

	/** A new name: typed in a small window, then back. */
	private static void rename(ServerPlayer p, Stones.Stone s, boolean fromAdmin) {
		Window w = Window.of(Domain.PLACES, "Waystones", s.name, "Name");
		w.field("name", Ui.text("Name", ChatFormatting.WHITE), s.name, 32);
		w.button(Ui.text("✔ Save", ChatFormatting.GREEN), null, (pl, v) -> {
			String name = WaystonesWindows.clean(v.text("name", s.name));
			if (!name.isEmpty() && (Stones.owns(pl, s) || Ui.isOp(pl))) {
				s.name = name;
				Stones.STORE.markDirty();
			}
			one(pl, s, fromAdmin);
		});
		w.cancel(pl -> one(pl, s, fromAdmin)).show(p);
	}

	private static void confirmRemove(ServerPlayer p, Stones.Stone s, boolean fromAdmin) {
		Window c = Window.path(s.name, "Remove");
		c.text(Ui.text("Remove " + s.name + "? It's gone from everyone's list; nothing drops.", ChatFormatting.GRAY));
		c.button(Ui.text("Remove", ChatFormatting.RED), null, (q, vv) -> {
			if (!Ui.isOp(q)) return;
			WaystonesWindows.remove(q, s);
			All.open(q, 0);
		});
		c.cancel(q -> one(q, s, fromAdmin)).show(p);
	}

	// ---------------------------------------------------------------- all of them (operators)

	static final class All extends CanvasWindow {
		private static final int PER_PAGE = 8;
		private static final int[] ROWS = {1, 2, 3, 4, 5, 6, 7, 8};
		private int page;

		All(ServerPlayer p, int page) {
			super(p);
			this.page = page;
		}

		static void open(ServerPlayer p, int page) {
			new All(p, page).show();
		}

		private List<Stones.Stone> list() {
			List<Stones.Stone> all = new ArrayList<>(Stones.list());
			String here = Stones.dim(this.player.level());
			all.sort(Comparator.comparingDouble((Stones.Stone s) -> s.dim.equals(here)
					? this.player.position().distanceToSqr(s.x + 0.5, s.y, s.z + 0.5) : Double.MAX_VALUE).thenComparing(s -> s.name));
			return all;
		}

		private int pages(int n) {
			return Math.max(1, (n + PER_PAGE - 1) / PER_PAGE);
		}

		@Override
		protected Canvas draw() {
			List<Stones.Stone> all = list();
			int pages = pages(all.size());
			this.page = Math.max(0, Math.min(this.page, pages - 1));
			Canvas c = new Canvas().panel();
			title(c, "Server settings", nl.libreria.core.gui.AdminCanvas.TINT, "All waystones");
			long villages = all.stream().filter(s -> s.village).count();
			c.text(all.size() + (all.size() == 1 ? " waystone" : " waystones") + " · " + villages + " in villages", Canvas.cellX(0) + 3,
					Canvas.cellY(0) + 5, Glyphs.muted);
			int left = Canvas.cellX(1) + 4, width = Canvas.cellX(9) - 2 - left;
			int from = this.page * PER_PAGE;
			for (int i = 0; i < PER_PAGE && from + i < all.size(); i++) {
				Stones.Stone s = all.get(from + i);
				int row = ROWS[i];
				c.line(left, row, width, s.secret ? Glyphs.neutral : ACCENT);
				int y = Canvas.cellY(row) + 5;
				String who = s.village ? "village" : s.owner == null ? "server" : s.ownerName;
				int ww = Glyphs.width(Component.literal(who));
				c.text(Canvas.fit(s.name, width - 12 - ww - 8), left + 6, y, TEXT);
				c.right(who, left + width - 6, y, Glyphs.muted);
			}
			if (all.isEmpty()) {
				c.centred("No waystones yet", 88, Canvas.cellY(3), TEXT);
				c.centred("New villages get one by their bell;", 88, Canvas.cellY(3) + 11, Glyphs.muted);
				c.centred("players craft their own.", 88, Canvas.cellY(3) + 22, Glyphs.muted);
			}
			small(c, 0, 3, "Back");
			if (pages > 1) {
				if (this.page > 0) small(c, 3, 1, "‹");
				c.centred((this.page + 1) + "/" + pages, Canvas.cellX(4) + 9, Canvas.cellY(9) + 5, Glyphs.muted);
				if (this.page < pages - 1) small(c, 5, 1, "›");
			}
			return c;
		}

		@Override
		protected void fill() {
			if (!Ui.isOp(this.player)) return;
			List<Stones.Stone> all = list();
			int from = this.page * PER_PAGE;
			for (int i = 0; i < PER_PAGE && from + i < all.size(); i++) {
				Stones.Stone s = all.get(from + i);
				List<Component> lore = new ArrayList<>();
				lore.add(Ui.text(where(s), ChatFormatting.WHITE));
				lore.add(Ui.text(whose(s), ChatFormatting.GRAY));
				if (s.secret) lore.add(Ui.text("Private", ChatFormatting.GRAY));
				lore.add(Component.empty());
				lore.add(Ui.text("▸ Click: name, private, go there, remove", ChatFormatting.YELLOW));
				Component name = Ui.text(s.name, ACCENT).withStyle(ChatFormatting.BOLD);
				icon(0, ROWS[i], new ItemStack(s.grand ? WaystonesMod.HEART : WaystonesMod.WAYSTONE_ITEM), name, lore, p -> one(p, s, true));
				area(1, ROWS[i], 8, 1, name, lore, p -> one(p, s, true));
			}
			area(0, 9, 3, 1, Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), List.of(),
					p -> nl.libreria.core.gui.AdminWindow.open(p, WaystonesMod.MODULE));
			int pages = pages(all.size());
			if (this.page > 0) area(3, 9, 1, 1, Ui.text("Previous page", ChatFormatting.WHITE), List.of(), p -> {
				this.page--;
				refresh();
			});
			if (this.page < pages - 1) area(5, 9, 1, 1, Ui.text("Next page", ChatFormatting.WHITE), List.of(), p -> {
				this.page++;
				refresh();
			});
		}
	}
}
