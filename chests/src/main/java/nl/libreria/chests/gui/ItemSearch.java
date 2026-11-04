package nl.libreria.chests.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.chests.Search;
import nl.libreria.core.Pack;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Glyphs;
import nl.libreria.core.gui.Menu;

/**
 * "Your items": one simple screen. A search field on top (you can type right away; the results change with every
 * letter) and below it everything you carry and everything in your chests, most first, with the real totals (200 oak
 * logs show 200). Click an item: the screen closes and its nearest chest lights up, with a beam to it. Right-click:
 * every chest that has it.
 *
 * <p>It is an anvil screen (the only screen with a text field); the Libreria pack draws a search screen in
 * Minecraft's own grey style over the whole anvil, and the results fill the slots where your inventory normally is.
 */
public final class ItemSearch extends AnvilInputGui {
	/** The anvil's own three slots come first, then the 36 slots of the player inventory area. */
	private static final int RESULTS = 3, SLOTS = 36;
	/** A slot shows at most this many (the real number is in the tooltip). */
	private static final int MAX_SHOWN = 999;
	private static final Identifier EMPTY = Identifier.fromNamespaceAndPath("libreria", "gui/empty");

	private final ServerPlayer viewer;
	private final boolean pack;
	/** Everything on you and in your chests nearby, read once, so typing stays instant. */
	private final List<Search.Stock> all;
	private String shown = "\u0000";
	private int page;

	private ItemSearch(ServerPlayer player, String typed) {
		super(player, true);
		this.viewer = player;
		this.pack = Pack.has(player);
		this.all = new ArrayList<>(Search.stock(player, ""));
		this.all.sort(Comparator.comparingLong(Search.Stock::total).reversed()
				.thenComparing(s -> s.sample().getHoverName().getString().toLowerCase(Locale.ROOT)));
		this.setTitle(title());
		String text = typed == null ? "" : typed;
		this.setDefaultInputValue(text);
		if (this.pack) {
			// The item the anvil needs for its text field: invisible, the search screen is drawn over it
			ItemStack field = new ItemStack(Items.PAPER);
			field.set(DataComponents.CUSTOM_NAME, Component.literal(text));
			field.set(DataComponents.ITEM_MODEL, EMPTY);
			this.setSlot(0, GuiElementBuilder.from(field).hideTooltip());
		}
		render(text);
	}

	public static void open(ServerPlayer player, String typed) {
		new ItemSearch(player, typed).open();
	}

	@Override
	public void onInput(String input) {
		this.page = 0;
		render(input);
	}

	/** With the pack: the search screen over the whole anvil, "Your items" on top and the totals in the dark bar. */
	private Component title() {
		if (!this.pack) return Component.literal("Your items");
		long items = 0;
		for (Search.Stock s : this.all) items += s.total();
		int adv = Glyphs.advance("frame.search");
		// The anvil writes its title at x 60. The screen starts 8 px left of the anvil's own left edge; after it,
		// "Your items" centred on top, then the totals right-aligned in the dark bar.
		Component name = Component.literal("Your items");
		int nameX = (176 - Glyphs.width(name)) / 2;
		MutableComponent t = Component.empty()
				.append(Glyphs.ui(Glyphs.space(-68) + Glyphs.g("frame.search") + Glyphs.space(nameX - 60 - (-68 + adv)), 0xFFFFFF));
		t.append(name);
		java.util.Set<String> chests = new java.util.HashSet<>();
		for (Search.Stock s : this.all) for (Search.Hit h : s.hits()) chests.add(h.entry().dim + h.entry().pos());
		String summary = Fmt.number(items) + (items == 1 ? " item" : " items")
				+ (chests.isEmpty() ? "" : "  ·  " + Fmt.count(chests.size(), "chest", "chests"));
		int width = Glyphs.width(Component.literal(summary));
		// In the dark bar (the font is moved down 65 px, from the title line into the bar)
		t.append(Glyphs.ui(Glyphs.space((175 - width) - (nameX + Glyphs.width(name)))));
		t.append(Component.literal(summary).withStyle(st -> st.withFont(Glyphs.hud(6)).withColor(0xE6E6E6)));
		return t;
	}

	/** The results for what's typed now. */
	private void render(String input) {
		String text = input == null ? "" : input.trim();
		String key = text.toLowerCase(Locale.ROOT) + "|" + this.page;
		if (key.equals(this.shown)) return;
		this.shown = key;
		String lower = text.toLowerCase(Locale.ROOT);
		List<Search.Stock> matches = new ArrayList<>();
		for (Search.Stock s : this.all) {
			if (lower.isEmpty() || Search.matches(s.item(), lower)) matches.add(s);
		}
		// More than fit: the last slot turns the page
		boolean paged = matches.size() > SLOTS;
		int perPage = paged ? SLOTS - 1 : SLOTS;
		int pages = Math.max(1, (matches.size() + perPage - 1) / perPage);
		this.page = Math.min(this.page, pages - 1);
		for (int i = 0; i < SLOTS; i++) {
			int idx = this.page * perPage + i;
			if (paged && i == SLOTS - 1) {
				this.setSlot(RESULTS + i, more(this.page, pages, matches.size()));
			} else if (i < perPage && idx < matches.size()) {
				this.setSlot(RESULTS + i, element(matches.get(idx)));
			} else {
				this.clearSlot(RESULTS + i);
			}
		}
		if (matches.isEmpty()) {
			this.setSlot(RESULTS + 13, Menu.button(Items.BARRIER, text.isEmpty() ? "Nothing yet" : "Nothing matches “" + text + "”",
					ChatFormatting.RED, text.isEmpty() ? "Open a chest: it's added to your index." : "Not on you, nor in your chests nearby.",
					text.isEmpty() ? "" : "Part of a name works too, like dia or oak."));
		}
		this.setSlot(2, ItemStack.EMPTY);
	}

	private GuiElementBuilder more(int page, int pages, int total) {
		GuiElementBuilder b = Menu.button(Items.ARROW, "More (" + (page + 1) + " / " + pages + ")", ChatFormatting.YELLOW,
				Fmt.count(total, "kind matches", "kinds match") + ".", "Type a bit more to narrow it down.");
		Menu.actions(b, "Click: next page");
		return b.setCallback((index, type, action, gui) -> {
			Ui.click(this.viewer);
			this.page = (this.page + 1) % pages;
			render(this.getInput());
		});
	}

	/** One kind of item with its total. Click: close and light up the nearest chest; right-click: every chest. */
	private GuiElementBuilder element(Search.Stock stock) {
		int shownCount = (int) Math.max(1, Math.min(stock.total(), MAX_SHOWN));
		int chests = stock.hits().size();
		GuiElementBuilder b = GuiElementBuilder.from(stock.sample().copyWithCount(shownCount)).hideDefaultTooltip();
		b.setName(stock.sample().getHoverName().copy().withStyle(st -> st.withItalic(false)));
		if (stock.total() > MAX_SHOWN) b.addLoreLine(Ui.kv("Total: ", Fmt.number(stock.total()), ChatFormatting.WHITE));
		if (stock.carried() > 0) b.addLoreLine(Ui.kv("On you: ", Fmt.number(stock.carried()), ChatFormatting.GREEN));
		if (chests > 0) {
			Search.Hit near = stock.hits().getFirst();
			b.addLoreLine(Ui.kv("In chests: ", Fmt.number(stock.stored()) + " in " + Fmt.count(chests, "chest", "chests"), ChatFormatting.GOLD));
			b.addLoreLine(Ui.kv("Nearest: ", Fmt.distance(this.viewer, near.entry(), near.distance()), ChatFormatting.YELLOW));
			Menu.actions(b, "Click: light up the nearest chest", "Right-click: every chest with it");
		} else {
			b.addLoreLine(Ui.text("All of it is on you", ChatFormatting.DARK_GRAY));
		}
		return b.setCallback((index, type, action, gui) -> {
			if (chests == 0) {
				Ui.sound(this.viewer, SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 0.7f);
				return;
			}
			if (type.isRight) {
				Ui.click(this.viewer);
				String typed = this.getInput();
				ItemGui.open(this.viewer, stock.item(), "your items", () -> open(this.viewer, typed));
			} else if (type.isLeft) {
				gui.close();
				Locate.go(this.viewer, stock.sample(), stock.hits());
			}
		});
	}
}
