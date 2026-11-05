package nl.libreria.chests.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.chests.Beam;
import nl.libreria.chests.Search;
import nl.libreria.chests.Tracker;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Menu;

/** "Where is …": every chest with one item, nearest first, with count and distance. Click = show the way. */
public final class ItemGui extends Menu {
	private static final int PER_ROW = 7;
	private static final int PER_PAGE = PER_ROW * 4;

	private final Item item;
	private final String backLabel;
	private final Runnable back;
	private int page;

	private ItemGui(ServerPlayer player, Item item, String backLabel, Runnable back) {
		super(player, 6, "Where is: " + new ItemStack(item).getHoverName().getString());
		this.item = item;
		this.backLabel = backLabel;
		this.back = back;
		rebuild();
	}

	/** Opens the list for one item; Back goes to the chest index. */
	public static void open(ServerPlayer player, Item item) {
		open(player, item, "your items", () -> ItemSearch.open(player, ""));
	}

	static void open(ServerPlayer player, Item item, String backLabel, Runnable back) {
		new ItemGui(player, item, backLabel, back).open();
	}

	private void rebuild() {
		clearContent();
		Fmt.clearRow(this, this.rows - 1);
		ItemStack sample = new ItemStack(this.item);
		List<Search.Hit> hits = Search.find(this.player, this.item);
		int pages = Math.max(1, (hits.size() + PER_PAGE - 1) / PER_PAGE);
		this.page = Math.max(0, Math.min(this.page, pages - 1));

		// Toolbar: the item itself, with totals
		long total = hits.stream().mapToLong(Search.Hit::count).sum();
		GuiElementBuilder head = GuiElementBuilder.from(sample.copyWithCount(1)).hideDefaultTooltip()
				.addLoreLine(Ui.kv("Total: ", Fmt.number(total), ChatFormatting.GREEN))
				.addLoreLine(Ui.kv("In: ", Fmt.count(hits.size(), "chest", "chests"), ChatFormatting.WHITE));
		if (!hits.isEmpty()) {
			Search.Hit near = hits.getFirst();
			head.addLoreLine(Ui.kv("Nearest: ", Fmt.distance(this.player, near.entry(), near.distance()), ChatFormatting.YELLOW));
			actions(head, "Click: show the way to the nearest");
			head.setCallback((i, t, a, g) -> {
				this.close();
				Locate.go(this.player, sample, hits);
			});
		}
		this.setSlot(4, head);
		Fmt.toolbar(this, 0);

		// The chests, 7 per row, centred
		int from = this.page * PER_PAGE;
		for (int r = 0; r < 4; r++) {
			int start = from + r * PER_ROW;
			int n = Math.min(PER_ROW, hits.size() - start);
			if (n <= 0) break;
			int[] slots = row(1 + r, n);
			for (int i = 0; i < n; i++) this.setSlot(slots[i], element(hits.get(start + i), sample));
		}
		if (hits.isEmpty()) {
			this.setSlot(22, button(Items.BARRIER, "Not in your chests nearby", ChatFormatting.RED,
					"No chest within " + (int) Search.radius() + " m has this", "(skipped chests don't count).",
					"Open a chest to update what Libreria knows."));
		}

		back(this.backLabel, this.back);
		pages(this.page, pages, p -> {
			this.page = p;
			rebuild();
		});
		closeButton();
		help("Every chest with this item,",
				"nearest first.",
				"Click a chest: show the way.",
				"Shift-click: leave that chest out of",
				"search. Undo it in Your chests.");
		finish();
	}

	private GuiElementBuilder element(Search.Hit hit, ItemStack sample) {
		var e = hit.entry();
		GuiElementBuilder b = button(Search.blockItem(e), Search.kind(e) + " · " + Ui.meters(hit.distance()), ChatFormatting.YELLOW)
				.setCount(Math.max(1, Math.min(hit.count(), 64)))
				.addLoreLine(Ui.kv("Here: ", Fmt.number(hit.count()) + "×", ChatFormatting.GREEN))
				.addLoreLine(Ui.kv("Distance: ", Fmt.distance(this.player, e, hit.distance()), ChatFormatting.YELLOW))
				.addLoreLine(Ui.kv("Where: ", Fmt.where(e), ChatFormatting.WHITE));
		if (!hit.live()) b.addLoreLine(Ui.text("Last seen " + Search.ago(e.seen), ChatFormatting.DARK_GRAY));
		actions(b, "Click: show the way", "Shift-click: leave out of search");
		b.setCallback((index, type, action, gui) -> {
			if (type.shift) {
				Ui.click(this.player);
				Tracker.toggleNoIndex(this.player, this.player.level(), e.pos());
				rebuild();
			} else if (type.isLeft || type.isRight) {
				this.close();
				Beam.to(this.player, this.player.level(), e.pos(), sample, hit.count());
			}
		});
		return b;
	}
}
