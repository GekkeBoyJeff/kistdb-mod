package nl.libreria.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import nl.libreria.core.gui.Canvas;
import nl.libreria.core.gui.CanvasWindow;
import nl.libreria.core.gui.Glyphs;

/**
 * One collection: everything in it as a grid of pictures, what you have in colour and the rest dark. Under it the top
 * list of this collection. Big collections (every item, every achievement) turn pages.
 */
public final class CollectionWindow extends CanvasWindow {
	/** Rows of the grid: the chest's five under the card and the three of the inventory. */
	private static final int[] GRID = {1, 2, 3, 4, 5, 6, 7, 8};
	private static final int PER_PAGE = GRID.length * 9;
	/** The Libreria pack's deep shade over an item (custom model data flag 2): what you don't have yet. */
	private static final List<Boolean> DIM = List.of(false, false, true);

	private final UUID who;
	private final String name;
	private final Completion.Collection col;
	private final Consumer<ServerPlayer> back;
	private int page;
	private final List<Completion.Entry> entries;
	private final Set<String> done;
	private final Profile.Stats stats;

	private CollectionWindow(ServerPlayer viewer, UUID who, Completion.Collection col, Consumer<ServerPlayer> back) {
		super(viewer);
		this.who = who;
		this.col = col;
		this.back = back;
		MinecraftServer server = viewer.level().getServer();
		Players.Known k = Players.get(server, who);
		this.name = k != null ? k.label() : viewer.getName().getString();
		this.entries = col.entries();
		this.stats = Profile.stats(server, who);
		this.done = col.done(server, who, this.stats);
	}

	/** Opens {@code col} as {@code who} has it, for {@code viewer} (a window of its own when the collection has one). */
	public static void open(ServerPlayer viewer, UUID who, Completion.Collection col, Consumer<ServerPlayer> back) {
		Completion.Opener own = col.own();
		if (own != null) {
			try {
				if (own.open(viewer, who, back)) return;
			} catch (RuntimeException e) {
				Libreria.LOG.warn("Completion {}: its own window failed", col.id, e);
			}
		}
		new CollectionWindow(viewer, who, col, back).show();
	}

	/** The grid, always (also for a collection with a window of its own). */
	public static void grid(ServerPlayer viewer, UUID who, Completion.Collection col, Consumer<ServerPlayer> back) {
		new CollectionWindow(viewer, who, col, back).show();
	}

	/** For the self-tests: the picture of a collection as {@code who} has it, as JSON. */
	static String picture(ServerPlayer viewer, UUID who, Completion.Collection col) {
		return new CollectionWindow(viewer, who, col, NOTE).pictureJson();
	}

	private boolean own() {
		return this.who.equals(this.player.getUUID());
	}

	private int pages() {
		return Math.max(1, (this.entries.size() + PER_PAGE - 1) / PER_PAGE);
	}

	private Consumer<ServerPlayer> reopen() {
		int p = this.page;
		return q -> {
			CollectionWindow w = new CollectionWindow(q, this.who, this.col, this.back);
			w.page = p;
			w.show();
		};
	}

	@Override
	protected Canvas draw() {
		Canvas c = new Canvas().panel();
		ProgressWindow.crumbs2(c, own() ? "Progress" : this.name, this.col.name);
		int x0 = Canvas.cellX(0) + Canvas.CARD_MARGIN + 6, x1 = Canvas.cellX(9) - Canvas.CARD_MARGIN - 6;
		boolean complete = this.done.size() >= this.entries.size() && !this.entries.isEmpty();
		c.card(0, 0, 9, 1, complete ? ProgressWindow.COMPLETE : ProgressWindow.GOLD);
		List<Completion.Place> board = Completion.ranking(this.player.level().getServer(), this.col.id);
		int r = Completion.rank(board, this.who);
		String place = r > 0 ? "#" + r + " of " + board.size() : "";
		int pw = place.isEmpty() ? 0 : Glyphs.width(Component.literal(place)) + 8;
		if (!place.isEmpty()) c.right(place, x1, Canvas.cellY(0) + 5, Glyphs.muted);
		String text = this.col.yes() + " " + Ui.n(this.done.size()) + " of " + Ui.n(this.entries.size());
		c.text(Canvas.fit(text, x1 - x0 - pw), x0, Canvas.cellY(0) + 5, StartWindow.TEXT);
		if (this.entries.isEmpty()) c.centred("Nothing here on this server yet.", 88, Canvas.cellY(3) + 5, Glyphs.muted);
		int pages = pages();
		if (pages > 1) {
			ProgressWindow.pager(c, this.page, pages, "Top list");
		} else {
			ProgressWindow.small(c, 0, 4, "Back");
			ProgressWindow.small(c, 4, 5, "Top list");
		}
		return c;
	}

	@Override
	protected void fill() {
		this.page = Math.max(0, Math.min(this.page, pages() - 1));
		int from = this.page * PER_PAGE;
		for (int i = 0; i < PER_PAGE && from + i < this.entries.size(); i++) {
			Completion.Entry e = this.entries.get(from + i);
			boolean has = this.done.contains(e.key());
			ItemStack icon = e.icon().copy();
			if (!has) {
				CustomModelData had = icon.get(DataComponents.CUSTOM_MODEL_DATA);
				icon.set(DataComponents.CUSTOM_MODEL_DATA, had == null ? new CustomModelData(List.of(), DIM, List.of(), List.of())
						: new CustomModelData(had.floats(), DIM, had.strings(), had.colors()));
			}
			icon.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
			List<Component> lore = new ArrayList<>();
			String who = own() ? "" : this.name + ": ";
			lore.add(Ui.text(who + (has ? "✔ " + this.col.yes() : this.col.no()), has ? ChatFormatting.GREEN : ChatFormatting.GRAY));
			Completion.Detail d = this.col.detail();
			if (d != null) {
				try {
					String line = d.of(e, this.stats, has);
					if (line != null && !line.isEmpty()) lore.add(Ui.text(line, ChatFormatting.DARK_GRAY));
				} catch (RuntimeException ex) {
					Libreria.LOG.warn("Completion {}: detail failed", this.col.id, ex);
				}
			}
			int row = GRID[i / 9], col = i % 9;
			icon(col, row, icon, Ui.text(e.name(), has ? 0xFFFFFF : 0x9AA0AC), lore, NOTE);
		}
		List<Component> about = new ArrayList<>();
		for (String l : Canvas.wrap(this.col.about(), 180)) about.add(Ui.text(l, ChatFormatting.GRAY));
		about.add(Component.empty());
		about.add(Ui.text("In colour: " + this.col.yes().toLowerCase(java.util.Locale.ROOT) + ". Dark: not yet.", ChatFormatting.DARK_GRAY));
		area(0, 0, 9, 1, Ui.text(this.col.name, ProgressWindow.GOLD).withStyle(ChatFormatting.BOLD), about, NOTE);
		Component back = Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
		Component top = Ui.text("Top list", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
		List<Component> topLore = List.of(Ui.text("Who has the most " + this.col.name.toLowerCase(java.util.Locale.ROOT) + ".", ChatFormatting.GRAY));
		Consumer<ServerPlayer> toTop = q -> TopListsWindow.board(q, this.col.id, reopen());
		if (pages() > 1) {
			area(0, 9, 2, 1, back, List.of(), this.back);
			if (this.page > 0) area(2, 9, 1, 1, Ui.text("Previous page", ChatFormatting.WHITE), List.of(), q -> turn(-1));
			if (this.page < pages() - 1) area(5, 9, 1, 1, Ui.text("Next page", ChatFormatting.WHITE), List.of(), q -> turn(1));
			area(6, 9, 3, 1, top, topLore, toTop);
		} else {
			area(0, 9, 4, 1, back, List.of(), this.back);
			area(4, 9, 5, 1, top, topLore, toTop);
		}
	}

	private void turn(int step) {
		this.page += step;
		Ui.click(this.player);
		refresh();
	}
}
