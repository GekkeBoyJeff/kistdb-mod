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
	private static final int TEXT = 0xFFFFFF, VALUE = 0x8FE3F0, DANGER = 0xE0605A, BACK = 0x6EDC78;
	private static final net.minecraft.resources.Identifier SWITCH_ON = net.minecraft.resources.Identifier.fromNamespaceAndPath("libreria", "gui/switch_on");
	private static final net.minecraft.resources.Identifier SWITCH_OFF = net.minecraft.resources.Identifier.fromNamespaceAndPath("libreria", "gui/switch_off");
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

		/** On claimed land, for its owner (or an operator): open to visitors, and who has it on their list. */
		private boolean land() {
			return Stones.mayOpenToVisitors(this.player, this.stone);
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
			int arrow = Canvas.cellX(9) - Canvas.CARD_MARGIN - 8;
			if (land()) {
				// Its land's owner (rows 6–7): open to visitors (a switch), who has it on their list
				c.card(0, 6, 8, 1, Glyphs.neutral);
				c.text("Open to visitors", left, Canvas.cellY(6) + 5, TEXT);
				c.card(0, 7, 9, 1, Glyphs.neutral);
				c.text(Canvas.fit(listsLabel(s, this.player), ROOM_FULL - 16), left, Canvas.cellY(7) + 5, TEXT);
				if (!Finders.who(s, this.player).isEmpty()) c.text("›", arrow, Canvas.cellY(7) + 5, Glyphs.muted);
				// Below them (row 8): an operator's two side by side, or the owner's taking it down
				if (op()) {
					int mid = Canvas.cellX(4);
					c.card(0, 8, 4, 1, Glyphs.neutral);
					c.text("Go there", left, Canvas.cellY(8) + 5, TEXT);
					c.text("›", mid - Canvas.CARD_MARGIN - 8, Canvas.cellY(8) + 5, Glyphs.muted);
					c.card(4, 8, 5, 1, DANGER);
					c.text(takesDown() ? "Take it down" : "Remove it", mid + Canvas.CARD_MARGIN + 6, Canvas.cellY(8) + 5, TEXT);
					c.text("›", arrow, Canvas.cellY(8) + 5, Glyphs.muted);
				} else if (takesDown()) {
					c.card(0, 8, 9, 1, DANGER);
					c.text("Take it down", left, Canvas.cellY(8) + 5, TEXT);
					c.text("›", arrow, Canvas.cellY(8) + 5, Glyphs.muted);
				}
			} else if (op()) {
				// Operators (rows 6–8)
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

		private void takeDownArea(int col, int row, int w) {
			area(col, row, w, 1, Ui.text("Take it down", 0xE0605A).withStyle(ChatFormatting.BOLD), List.of(Ui.text("The shrine and its floor go;", ChatFormatting.GRAY),
					Ui.text("it's off everyone's list. You get the", ChatFormatting.GRAY), Ui.text("waystone and the nether star back.", ChatFormatting.GRAY),
					Component.empty(), Ui.text("▸ Click: take it down (you're asked first)", ChatFormatting.YELLOW)), p -> confirmTakeDown(p, this.stone, this.fromAdmin));
		}

		/** Its land's owner: the switch for visitors (row 6) and who has it on their list (row 7). */
		private void landAreas() {
			Stones.Stone s = this.stone;
			boolean on = s.visitors;
			Component name = Ui.text("Open to visitors", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
			List<Component> lore = List.of(Ui.text("Now: " + (on ? "open" : "closed"), ChatFormatting.AQUA),
					Ui.text("Open: anyone may click it here, find it", ChatFormatting.GRAY), Ui.text("and travel on from it.", ChatFormatting.GRAY),
					Ui.text("Closed: only who may build here can", ChatFormatting.GRAY), Ui.text("click it. Who has it on their list can", ChatFormatting.GRAY),
					Ui.text("still travel here.", ChatFormatting.GRAY), Component.empty(), Ui.text("▸ Click: " + (on ? "close it" : "open it"), ChatFormatting.YELLOW));
			java.util.function.Consumer<ServerPlayer> flip = p -> {
				if (!Stones.mayOpenToVisitors(p, s)) return;
				s.visitors = !s.visitors;
				Stones.STORE.markDirty();
				Notify.whisper(p, Ui.text(s.name + ": " + (s.visitors ? "open to visitors" : "closed to visitors"), ChatFormatting.GRAY));
				refreshCells();
			};
			area(0, 6, 8, 1, name, lore, flip);
			ItemStack sw = new ItemStack(Items.PAPER);
			sw.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, on ? SWITCH_ON : SWITCH_OFF);
			icon(8, 6, sw, name, lore, flip);
			List<Finders.Who> all = Finders.who(s, this.player);
			Component lists = Ui.text("Who has it on their list", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
			if (all.isEmpty()) {
				area(0, 7, 9, 1, lists, List.of(Ui.text(listsLabel(s, this.player) + ".", ChatFormatting.GRAY)), NOTE);
			} else {
				area(0, 7, 9, 1, lists, List.of(Ui.text(listsLabel(s, this.player), ChatFormatting.AQUA),
						Ui.text("Take someone off, or give it back:", ChatFormatting.GRAY), Ui.text("one by one, the others keep theirs.", ChatFormatting.GRAY),
						Component.empty(), Ui.text("▸ Click: see who", ChatFormatting.YELLOW)), p -> finders(p, s, this.fromAdmin));
			}
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
			boolean land = land();
			if (land) landAreas();
			if (op()) {
				// on claimed land the two share row 8; elsewhere rows 7 and 8
				int goRow = land ? 8 : 7, goW = land ? 4 : 9, offCol = land ? 4 : 0, offW = land ? 5 : 9;
				area(0, goRow, goW, 1, Ui.text("Go there", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD),
						List.of(Ui.text("Free, as an operator.", ChatFormatting.GRAY), Component.empty(), Ui.text("▸ Click: go", ChatFormatting.YELLOW)), p -> {
							ServerLevel level = Stones.check(p, s);
							if (level == null) return;
							p.closeContainer();
							Stones.go(p, level, s);
						});
				if (takesDown()) takeDownArea(offCol, 8, offW);
				else area(offCol, 8, offW, 1, Ui.text("Remove it", 0xE0605A).withStyle(ChatFormatting.BOLD), List.of(Ui.text(s.grand ? "The shrine and its floor go;"
						: "The stone and its altar go;", ChatFormatting.GRAY), Ui.text("it's off everyone's list. Nothing drops.", ChatFormatting.GRAY), Component.empty(),
						Ui.text("▸ Click: remove (you're asked first)", ChatFormatting.YELLOW)), p -> confirmRemove(p, s, this.fromAdmin));
			} else if (takesDown()) {
				takeDownArea(0, land ? 8 : 7, 9);
			}
			area(0, 9, 9, 1, Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), List.of(), p -> {
				if (this.fromAdmin) All.open(p, 0);
				else PlaceList.open(p, WaystonePlaces.KIND);
			});
		}
	}

	/** What the row of who has it on their list says: how many, and how many were taken off. */
	static String listsLabel(Stones.Stone s, ServerPlayer viewer) {
		List<Finders.Who> all = Finders.who(s, viewer);
		long on = all.stream().filter(w -> !w.off()).count(), off = all.size() - on;
		String a = s.shared ? "On everyone's list" : on == 0 ? "On no one else's list yet" : "On " + on + (on == 1 ? " player's list" : " players' lists");
		return off > 0 ? a + " · " + off + " taken off" : a;
	}

	/** Who has it on their list: take someone off, give it back (its land's owner; a dialog for who can't see our windows). */
	static void finders(ServerPlayer p, Stones.Stone s, boolean fromAdmin) {
		if (!Stones.mayOpenToVisitors(p, s)) return;
		new Finders(p, s, fromAdmin).show();
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

	// ---------------------------------------------------------------- who has it on their list (its land's owner)

	/**
	 * Everyone who has this waystone on their list, and who was taken off: click someone to take it off their list, or
	 * to give it back (on their list right away, no need to find it again). Nobody else's list changes.
	 */
	static final class Finders extends CanvasWindow {
		private static final int PER_PAGE = 9;
		private final Stones.Stone stone;
		private final boolean fromAdmin;
		private int page;

		/** Someone on the list, or taken off it. */
		record Who(java.util.UUID id, String name, boolean off) {
		}

		Finders(ServerPlayer p, Stones.Stone stone, boolean fromAdmin) {
			super(p);
			this.stone = stone;
			this.fromAdmin = fromAdmin;
		}

		/** Everyone but its owner (always theirs) and who's looking: by name, so nobody moves when clicked. */
		static List<Who> who(Stones.Stone s, ServerPlayer viewer) {
			var server = viewer.level().getServer();
			List<Who> out = new ArrayList<>();
			java.util.function.BiConsumer<java.util.Map<java.util.UUID, String>, Boolean> add = (m, off) -> {
				if (m == null) return;
				m.forEach((id, n) -> {
					if (id.equals(s.owner) || id.equals(viewer.getUUID())) return;
					Players.Known k = server == null ? null : Players.get(server, id);
					out.add(new Who(id, k != null ? k.name() : n, off));
				});
			};
			add.accept(s.finders, false);
			add.accept(s.takenOff, true);
			out.sort(Comparator.comparing((Who w) -> w.name().toLowerCase(Locale.ROOT)));
			return out;
		}

		private int pages(int n) {
			return Math.max(1, (n + PER_PAGE - 1) / PER_PAGE);
		}

		@Override
		protected Canvas draw() {
			List<Who> all = who(this.stone, this.player);
			int pages = pages(all.size());
			this.page = Math.max(0, Math.min(this.page, pages - 1));
			Canvas c = new Canvas().panel();
			String count = all.size() + (all.size() == 1 ? " player" : " players");
			String tail = " › ";
			int room = 168 - 8 - Glyphs.width(Component.literal(tail + "On lists")) - Glyphs.width(Component.literal(count)) - 10;
			String name = Canvas.fit(this.stone.name, room);
			c.text(name, 8, 5, ACCENT);
			int x = 8 + Glyphs.width(Component.literal(name));
			c.text(tail, x, 5, Glyphs.muted);
			c.text("On lists", x + Glyphs.width(Component.literal(tail)), 5, TEXT);
			c.right(count, 168 - 2, 5, Glyphs.muted);
			int left = Canvas.cellX(1) + 4, width = Canvas.cellX(9) - Canvas.CARD_MARGIN - left;
			int from = this.page * PER_PAGE;
			for (int i = 0; i < PER_PAGE && from + i < all.size(); i++) {
				Who w = all.get(from + i);
				c.line(left, i, width, Glyphs.neutral);
				int y = Canvas.cellY(i) + 5;
				String act = w.off() ? "Give back" : "Take off";
				int aw = Glyphs.width(Component.literal(act));
				c.right(act, left + width - 6, y, w.off() ? BACK : DANGER);
				c.text(Canvas.fit(w.name(), width - 12 - aw - 8), left + 6, y, w.off() ? Glyphs.muted : TEXT);
			}
			if (all.isEmpty()) {
				c.centred("No one else has it on their list", 88, Canvas.cellY(3), TEXT);
				c.centred("Who finds it shows up here.", 88, Canvas.cellY(3) + 11, Glyphs.muted);
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

		@Override
		protected void fill() {
			Stones.Stone s = this.stone;
			List<Who> all = who(s, this.player);
			int from = this.page * PER_PAGE;
			for (int i = 0; i < PER_PAGE && from + i < all.size(); i++) {
				Who w = all.get(from + i);
				Component name = Ui.text(w.name(), w.off() ? ChatFormatting.GRAY : ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
				List<Component> lore = new ArrayList<>();
				if (w.off()) {
					for (String l : Canvas.wrap("Taken off: " + s.name + " isn't on their list.", 180)) lore.add(Ui.text(l, ChatFormatting.GRAY));
					lore.add(Component.empty());
					for (String l : Canvas.wrap("Given back, it's on their list right away and they can travel here. They don't need to find it again.", 180)) {
						lore.add(Ui.text(l, ChatFormatting.GRAY));
					}
					lore.add(Component.empty());
					lore.add(Ui.text("▸ Click: give it back to " + w.name(), ChatFormatting.YELLOW));
				} else {
					for (String l : Canvas.wrap("Has " + s.name + " on their list.", 180)) lore.add(Ui.text(l, ChatFormatting.GRAY));
					lore.add(Component.empty());
					for (String l : Canvas.wrap("Taken off, it's gone from their list and they can't use it, until you give it back. The others keep it.", 180)) {
						lore.add(Ui.text(l, ChatFormatting.GRAY));
					}
					lore.add(Component.empty());
					lore.add(Ui.text("▸ Click: take " + w.name() + " off", ChatFormatting.YELLOW));
				}
				java.util.function.Consumer<ServerPlayer> act = p -> change(p, w);
				icon(0, i, headOf(p(), w), name, lore, act);
				area(1, i, 8, 1, name, lore, act);
			}
			Component back = Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
			java.util.function.Consumer<ServerPlayer> toStone = p -> WaystonesWindows.settings(p, s, this.fromAdmin);
			int pages = pages(all.size());
			if (pages > 1) {
				area(0, 9, 3, 1, back, List.of(), toStone);
				if (this.page > 0) area(3, 9, 1, 1, Ui.text("Previous page", ChatFormatting.WHITE), List.of(), p -> {
					this.page--;
					refresh();
				});
				if (this.page < pages - 1) area(5, 9, 1, 1, Ui.text("Next page", ChatFormatting.WHITE), List.of(), p -> {
					this.page++;
					refresh();
				});
			} else {
				area(0, 9, 9, 1, back, List.of(), toStone);
			}
		}

		private ServerPlayer p() {
			return this.player;
		}

		private static ItemStack headOf(ServerPlayer viewer, Who w) {
			Players.Known k = Players.get(viewer.level().getServer(), w.id());
			return k != null ? nl.libreria.core.ProfileWindow.head(k)
					: new eu.pb4.sgui.api.elements.GuiElementBuilder(Items.PLAYER_HEAD).setProfile(w.id()).asStack();
		}

		/** Takes them off, or gives it back; they hear it if they're here. */
		private void change(ServerPlayer p, Who w) {
			Stones.Stone s = this.stone;
			if (!Stones.mayOpenToVisitors(p, s)) return;
			var server = p.level().getServer();
			ServerPlayer them = server.getPlayerList().getPlayer(w.id());
			if (w.off()) {
				if (!Stones.giveBack(server, s, w.id())) return;
				Notify.whisper(p, Ui.text(w.name() + " has " + s.name + " on their list again", ChatFormatting.GRAY));
				nl.libreria.core.Libreria.LOG.info("Waystones: {} gave {} ({}) back to {}", p.getName().getString(), s.name, s.key(), w.name());
				if (them != null) Notify.card(them, new ItemStack(WaystonesMod.WAYSTONE_ITEM), ACCENT,
						Ui.text("Back on your list: ", ChatFormatting.WHITE).append(Ui.text(s.name, ACCENT)),
						Ui.text("You can travel there again", ChatFormatting.GRAY));
			} else {
				Stones.unfind(server, s, w.id());
				Notify.whisper(p, Ui.text(w.name() + " is off " + s.name + "'s list", ChatFormatting.GRAY));
				nl.libreria.core.Libreria.LOG.info("Waystones: {} took {} ({}) off {}'s list", p.getName().getString(), s.name, s.key(), w.name());
				if (them != null) Notify.info(them, WaystonesMod.WAYSTONE_ITEM, s.name + " is off your list", "Whoever owns its land took it off.");
			}
			refresh();
		}
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
