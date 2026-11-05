package nl.libreria.core.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.function.IntConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import nl.libreria.core.Pack;
import nl.libreria.core.Setting;
import nl.libreria.core.Ui;

/**
 * The shared look of every Libreria menu, so all modules feel like one product:
 * <ul>
 *   <li>with the Libreria pack: Minecraft's own grey panel, empty slots melt into it; without: dark glass;</li>
 *   <li>bottom row: {@code ◀ Back} (left), page buttons (middle), {@code ✖ Close} (centre), {@code ? Help} (right);</li>
 *   <li>buttons: coloured name, grey description, then a yellow "▸ Click: …" action line;</li>
 *   <li>toggles: the setting's icon with a green/red "lamp" pane underneath, both clickable.</li>
 * </ul>
 * Subclasses put their content in the rows above the navigation bar and call {@link #finish()} last.
 */
public abstract class Menu extends SimpleGui {
	private static final MenuType<?>[] TYPES = {MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
			MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6};

	protected final ServerPlayer player;
	protected final int rows;
	/** Does the player have the Libreria resource pack? Then the menu gets its tool's frame and seamless filler. */
	protected final boolean pack;
	private Component plainTitle = Component.empty();

	protected Menu(ServerPlayer player, int rows, String title) {
		super(TYPES[Math.max(1, Math.min(6, rows)) - 1], player, false);
		this.player = player;
		this.rows = Math.max(1, Math.min(6, rows));
		this.pack = Pack.has(player);
		this.setTitle(Component.literal(title));
	}

	/**
	 * Sets the title; with the pack the menu gets the Libreria panel with its domain's colour and emblem, and the title
	 * in the light colour of the look, with a shadow.
	 */
	@Override
	public void setTitle(Component title) {
		this.plainTitle = title;
		if (this.pack) {
			Component light = Component.literal(title.getString())
					.withStyle(s -> s.withColor(Glyphs.titleColor).withShadowColor(0xFF000000 | Glyphs.titleShadow));
			super.setTitle(Glyphs.framedTitle(theme(), this.rows, light));
		} else {
			// Without the pack there's no coloured frame: a small diamond in the domain's colour says where you are
			nl.libreria.core.Domain d = domain();
			super.setTitle(d == null ? title : Component.empty().append(Ui.text("◆ ", d.dark())).append(title));
		}
	}

	/** With the pack, coloured glass panes (headers, lamps) are shown as little plaques on the menu's tray. */
	@Override
	public void setSlot(int index, eu.pb4.sgui.api.elements.GuiElement element) {
		super.setSlot(index, this.pack ? plaque(element) : element);
	}

	private static eu.pb4.sgui.api.elements.GuiElement plaque(eu.pb4.sgui.api.elements.GuiElement el) {
		if (el == null) return null;
		net.minecraft.world.item.ItemStack st = el.getItemStack();
		if (st.isEmpty()) return el;
		if (!st.has(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE)) {
			// The tooltip in the look of the menu
			st = st.copy();
			st.set(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE, CanvasWindow.TOOLTIP);
			el = new eu.pb4.sgui.api.elements.SimpleGuiElement(st, el.getGuiCallback());
		}
		String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
		if (!path.endsWith("_stained_glass_pane")) {
			// Buttons (they have their own name) don't get a rarity frame: a "Close" barrier is not an epic item
			var rarity = st.get(net.minecraft.core.component.DataComponents.RARITY);
			if (st.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) && rarity != null
					&& rarity != net.minecraft.world.item.Rarity.COMMON) {
				net.minecraft.world.item.ItemStack copy = st.copy();
				copy.set(net.minecraft.core.component.DataComponents.RARITY, net.minecraft.world.item.Rarity.COMMON);
				return new eu.pb4.sgui.api.elements.SimpleGuiElement(copy, el.getGuiCallback());
			}
			return el;
		}
		net.minecraft.resources.Identifier model = st.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
		if (model != null && model.getNamespace().equals("libreria")) return el;
		net.minecraft.world.item.ItemStack copy = st.copy();
		String color = path.substring(0, path.length() - "_stained_glass_pane".length());
		copy.set(net.minecraft.core.component.DataComponents.ITEM_MODEL,
				net.minecraft.resources.Identifier.fromNamespaceAndPath("libreria", "gui/pane_" + color));
		return new eu.pb4.sgui.api.elements.SimpleGuiElement(copy, el.getGuiCallback());
	}

	/** The title without the frame (what the player reads). */
	public Component plainTitle() {
		return this.plainTitle;
	}

	/**
	 * The domain this menu belongs to: its frame gets that domain's colour and emblem, so a menu looks the same family
	 * whichever module drew it. By default from the package (chests and tools are Items, claims, waypoints and graves
	 * Land &amp; places, and so on); override for a menu that lives elsewhere.
	 */
	protected nl.libreria.core.Domain domain() {
		String[] parts = this.getClass().getPackageName().split("\\.");
		String module = parts.length > 2 ? parts[2] : "core";
		return switch (module) {
			case "chests", "tools" -> nl.libreria.core.Domain.ITEMS;
			case "claims", "waypoints", "graves" -> nl.libreria.core.Domain.PLACES;
			case "collector", "info" -> nl.libreria.core.Domain.PROGRESS;
			case "nicknames", "glint", "comfort" -> nl.libreria.core.Domain.YOU;
			default -> null;
		};
	}

	/** Frame theme in the pack: the domain's, or the plain Libreria one. */
	protected String theme() {
		nl.libreria.core.Domain d = domain();
		return d == null ? "libreria" : d.theme();
	}

	// ---------------------------------------------------------------- layout

	/** First slot of the navigation bar (bottom row). */
	protected int navRow() {
		return (this.rows - 1) * 9;
	}

	/** Number of content slots above the navigation bar. */
	protected int contentSize() {
		return (this.rows - 1) * 9;
	}

	/**
	 * Fills every slot that is still empty: with the pack an invisible item (the frame already hides the slot),
	 * otherwise dark glass above and black glass in the navigation bar.
	 */
	protected void finish() {
		for (int i = 0; i < this.rows * 9; i++) {
			if (this.getGuiElement(i) == null) this.setSlot(i, this.pack ? TILE : i >= navRow() ? NAV_FILL : FILL);
		}
	}

	/** Clears the content area (keeps the navigation bar). */
	protected void clearContent() {
		for (int i = 0; i < contentSize(); i++) this.clearSlot(i);
	}

	private static final eu.pb4.sgui.api.elements.GuiElement FILL = new GuiElementBuilder(Ui.item("gray_stained_glass_pane")).hideTooltip().build();
	private static final eu.pb4.sgui.api.elements.GuiElement NAV_FILL = new GuiElementBuilder(Ui.item("black_stained_glass_pane")).hideTooltip().build();
	private static final eu.pb4.sgui.api.elements.GuiElement TILE = new GuiElementBuilder(Items.PAPER)
			.model(net.minecraft.resources.Identifier.fromNamespaceAndPath("libreria", "gui/tile")).hideTooltip().build();

	/** A decorative filler in a custom colour, e.g. to separate sections. */
	public static GuiElementBuilder pane(String color) {
		return new GuiElementBuilder(Ui.item(color + "_stained_glass_pane")).hideTooltip();
	}

	// ---------------------------------------------------------------- navigation bar

	/** {@code ◀ Back} in the bottom-left corner. */
	protected void back(String where, Runnable action) {
		this.setSlot(navRow(), button(Items.ARROW, "◀ Back", ChatFormatting.WHITE, "To " + where).setCallback((i, t, a, g) -> {
			Ui.click(this.player);
			action.run();
		}));
	}

	/** {@code ◀ Back} to the domain page this menu hangs under (the top menu of a feature). */
	protected void up() {
		nl.libreria.core.Domain d = domain();
		if (d == null) return;
		back(d.label, () -> {
			this.close();
			nl.libreria.core.Start.domain(this.player, d);
		});
	}

	/** {@code ✖ Close} in the middle of the bottom row. */
	protected void closeButton() {
		this.setSlot(navRow() + 4, button(Items.BARRIER, "✖ Close", ChatFormatting.RED).setCallback((i, t, a, g) -> {
			Ui.click(this.player);
			this.close();
		}));
	}

	/** {@code ? Help} in the bottom-right corner, with explanation lines. */
	protected void help(String... lines) {
		GuiElementBuilder b = new GuiElementBuilder(Items.BOOK).setName(Ui.text("? Help", ChatFormatting.YELLOW));
		for (String l : lines) b.addLoreLine(l.isEmpty() ? Component.empty() : Ui.text(l, ChatFormatting.GRAY));
		this.setSlot(navRow() + 8, b.hideDefaultTooltip());
	}

	/** Previous/next page buttons and a page indicator in the bottom row. */
	protected void pages(int page, int pages, IntConsumer go) {
		if (pages <= 1) return;
		if (page > 0) {
			this.setSlot(navRow() + 3, button(Items.ARROW, "◀ Previous page", ChatFormatting.WHITE).setCallback((i, t, a, g) -> {
				Ui.sound(this.player, SoundEvents.BOOK_PAGE_TURN, 0.8f, 1f);
				go.accept(page - 1);
			}));
		}
		this.setSlot(navRow() + 2, new GuiElementBuilder(Items.PAPER).setCount(Math.min(64, page + 1))
				.setName(Ui.text("Page " + (page + 1) + " of " + pages, ChatFormatting.GRAY)).hideDefaultTooltip());
		if (page + 1 < pages) {
			this.setSlot(navRow() + 5, button(Items.ARROW, "Next page ▶", ChatFormatting.WHITE).setCallback((i, t, a, g) -> {
				Ui.sound(this.player, SoundEvents.BOOK_PAGE_TURN, 0.8f, 1f);
				go.accept(page + 1);
			}));
		}
	}

	// ---------------------------------------------------------------- buttons

	/** A button: coloured name, grey description lines. Add an action line with {@link #action}. */
	public static GuiElementBuilder button(Item icon, String name, ChatFormatting color, String... description) {
		GuiElementBuilder b = new GuiElementBuilder(icon).setName(Ui.text(name, color).withStyle(ChatFormatting.BOLD)).hideDefaultTooltip();
		for (String d : description) b.addLoreLine(d.isEmpty() ? Component.empty() : Ui.text(d, ChatFormatting.GRAY));
		return b;
	}

	/** Adds an action line such as "▸ Click: open" (yellow). */
	public static GuiElementBuilder action(GuiElementBuilder b, String text) {
		return b.addLoreLine(Ui.text("▸ " + text, ChatFormatting.YELLOW));
	}

	/** Adds an empty line followed by an action line. */
	public static GuiElementBuilder actions(GuiElementBuilder b, String... lines) {
		b.addLoreLine(Component.empty());
		for (String l : lines) action(b, l);
		return b;
	}

	/** A section header (a coloured pane with a name), useful above a row of items. */
	public static GuiElementBuilder header(String color, String title, ChatFormatting textColor) {
		return new GuiElementBuilder(Ui.item(color + "_stained_glass_pane")).setName(Ui.text(title, textColor).withStyle(ChatFormatting.BOLD))
				.hideDefaultTooltip();
	}

	/** "✔ On" / "✘ Off" lamp pane for under a toggle. */
	public static GuiElementBuilder lamp(boolean on) {
		return new GuiElementBuilder(Ui.item(on ? "lime_stained_glass_pane" : "red_stained_glass_pane"))
				.setName(Ui.text(on ? "✔ On" : "✘ Off", on ? ChatFormatting.GREEN : ChatFormatting.RED).withStyle(ChatFormatting.BOLD))
				.addLoreLine(Ui.text("▸ Click: turn " + (on ? "off" : "on"), ChatFormatting.YELLOW))
				.hideDefaultTooltip();
	}

	/**
	 * Places a setting at {@code slot} (icon) and, for toggles, its lamp at {@code slot + 9}. Clicking either changes the
	 * setting and calls {@code refresh}.
	 */
	protected void setting(int slot, Setting s, Runnable refresh) {
		GuiElementBuilder icon = new GuiElementBuilder(s.icon).setName(Ui.text(s.label, ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD))
				.hideDefaultTooltip();
		for (String l : s.lore) icon.addLoreLine(Ui.text(l, ChatFormatting.GRAY));
		if (s instanceof Setting.Toggle t) {
			boolean on = t.get(this.player);
			icon.addLoreLine(Component.empty());
			icon.addLoreLine(Ui.text(on ? "✔ On" : "✘ Off", on ? ChatFormatting.GREEN : ChatFormatting.RED));
			action(icon, "Click: turn " + (on ? "off" : "on"));
			if (on) icon.glow();
			Runnable flip = () -> {
				t.set(this.player, !on);
				Ui.sound(this.player, SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, on ? 0.7f : 1.4f);
				refresh.run();
			};
			icon.setCallback((i, type, a, g) -> flip.run());
			this.setSlot(slot, icon);
			if (slot + 9 < navRow()) this.setSlot(slot + 9, lamp(on).setCallback((i, type, a, g) -> flip.run()));
		} else if (s instanceof Setting.Choice c) {
			int current = c.index(this.player);
			icon.addLoreLine(Component.empty());
			for (int i = 0; i < c.size(); i++) {
				boolean sel = i == current;
				icon.addLoreLine(Ui.text((sel ? "▶ " : "   ") + c.labelAt(i), sel ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
			}
			action(icon, "Left-click: next");
			action(icon, "Right-click: previous");
			icon.setCallback((i, type, a, g) -> {
				c.cycle(this.player, type.isRight ? -1 : 1);
				Ui.click(this.player);
				refresh.run();
			});
			this.setSlot(slot, icon);
			if (slot + 9 < navRow()) {
				this.setSlot(slot + 9, new GuiElementBuilder(Ui.item("light_blue_stained_glass_pane"))
						.setName(Ui.text("◀ " + c.display(this.player) + " ▶", ChatFormatting.AQUA).withStyle(ChatFormatting.BOLD))
						.addLoreLine(Ui.text("▸ Left-click: next · right-click: previous", ChatFormatting.YELLOW)).hideDefaultTooltip()
						.setCallback((i, type, a, g) -> {
							c.cycle(this.player, type.isRight ? -1 : 1);
							Ui.click(this.player);
							refresh.run();
						}));
			}
		}
	}

	/** True for clicks that mean "do the secondary thing" (right-click or shift-click). */
	public static boolean secondary(ClickType type) {
		return type.isRight || type.shift;
	}

	/** Nicely spaced slots for {@code count} items in one row (centred, max 7, using columns 1–7). */
	public static int[] row(int rowIndex, int count) {
		int n = Math.min(7, count);
		int[] out = new int[n];
		int start = 1 + (7 - n) / 2;
		// With an even count, leave the middle column free so it stays symmetric
		for (int i = 0; i < n; i++) {
			int col = start + i;
			if (n % 2 == 0 && i >= n / 2) col++;
			out[i] = rowIndex * 9 + Math.min(7, col);
		}
		return out;
	}

	public static MutableComponent title(String text, ChatFormatting color) {
		return Ui.text(text, color).withStyle(ChatFormatting.BOLD);
	}

}
