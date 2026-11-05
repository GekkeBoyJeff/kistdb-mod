package nl.libreria.core.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.Pack;
import nl.libreria.core.Ui;

/**
 * A window of our own: a chest screen whose picture is a {@link Canvas} (drawn by its title) and whose cells are
 * invisible click spots. The player's inventory is hidden while it is open, so all 90 cells are ours (9 columns, rows
 * 0–5 of the chest, 6–8 of the inventory and 9, the hotbar). An {@link #area} makes a rectangle of cells one button:
 * every cell gets the same tooltip and action, so it works wherever you point. Icons are real items on top.
 *
 * <p>Only with the Libreria pack ({@link #available}); without it, callers show their usual window.
 */
public abstract class CanvasWindow extends SimpleGui {
	private static final Identifier EMPTY = Identifier.fromNamespaceAndPath("libreria", "gui/empty");
	/** The tooltip of our windows and menus: the look's panel. */
	public static final Identifier TOOLTIP = Identifier.fromNamespaceAndPath("libreria", "panel");

	protected final ServerPlayer player;

	protected CanvasWindow(ServerPlayer player) {
		super(MenuType.GENERIC_9x6, player, true);
		this.player = player;
	}

	/** Can this player see our own windows? (They need the pack and its window pieces.) */
	public static boolean available(ServerPlayer p) {
		return Pack.has(p) && Glyphs.has("win.panel") && Glyphs.has("card3.l");
	}

	/** The slot index of a cell. */
	public static int cell(int col, int row) {
		if (row < 6) return row * 9 + col;
		if (row < 9) return 54 + (row - 6) * 9 + col;
		return 81 + col;
	}

	/** Draws the window's picture. */
	protected abstract Canvas draw();

	/** Fills the cells (areas and icons). */
	protected abstract void fill();

	/** Builds everything and opens the window. */
	public void show() {
		for (int i = 0; i < 90; i++) this.clearSlot(i);
		fill();
		this.setTitle(draw().build());
		this.open();
	}

	/** Redraws in place (after something changed). */
	protected void refresh() {
		for (int i = 0; i < 90; i++) this.clearSlot(i);
		fill();
		this.setTitle(draw().build());
	}

	/** A button over {@code w × h} cells: the same tooltip and action in each of them. */
	protected void area(int col, int row, int w, int h, Component name, List<Component> lines, Consumer<ServerPlayer> action) {
		for (int r = row; r < row + h; r++) {
			for (int c = col; c < col + w; c++) {
				this.setSlot(cell(c, r), element(new ItemStack(Items.PAPER), true, name, lines, action));
			}
		}
	}

	/** Like {@link #area}, but the action is told whether it was a right-click. */
	protected void areaClick(int col, int row, int w, int h, Component name, List<Component> lines,
			java.util.function.BiConsumer<ServerPlayer, Boolean> action) {
		for (int r = row; r < row + h; r++) {
			for (int c = col; c < col + w; c++) {
				GuiElementBuilder b = element(new ItemStack(Items.PAPER), true, name, lines, null);
				b.setCallback((index, type, act, gui) -> {
					Ui.click(this.player);
					action.accept(this.player, type.isRight);
				});
				this.setSlot(cell(c, r), b);
			}
		}
	}

	/** A real item (an icon) in one cell of an area, with the area's tooltip and action. */
	protected void icon(int col, int row, ItemStack stack, Component name, List<Component> lines, Consumer<ServerPlayer> action) {
		this.setSlot(cell(col, row), element(stack, false, name, lines, action));
	}

	private GuiElementBuilder element(ItemStack stack, boolean invisible, Component name, List<Component> lines,
			Consumer<ServerPlayer> action) {
		ItemStack st = stack.copyWithCount(1);
		st.set(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE, TOOLTIP);
		GuiElementBuilder b = GuiElementBuilder.from(st).setName(name).hideDefaultTooltip();
		if (invisible) b.model(EMPTY);
		for (Component l : lines) b.addLoreLine(l);
		if (action != null) {
			b.setCallback((index, type, act, gui) -> {
				Ui.click(this.player);
				action.accept(this.player);
			});
		}
		return b;
	}
}
