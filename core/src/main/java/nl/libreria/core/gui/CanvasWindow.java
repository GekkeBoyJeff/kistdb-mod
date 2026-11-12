package nl.libreria.core.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.Pack;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Window;
import org.jetbrains.annotations.Nullable;

/**
 * A window of our own: a chest screen whose picture is a {@link Canvas} (drawn by its title) and whose cells are
 * invisible click spots. The player's inventory is hidden while it is open, so all 90 cells are ours (9 columns, rows
 * 0–5 of the chest, 6–8 of the inventory and 9, the hotbar). An {@link #area} makes a rectangle of cells one button:
 * every cell gets the same tooltip and action, so it works wherever you point. Icons are real items on top.
 *
 * <p>Only with the Libreria pack ({@link #available}); without it (Bedrock players, for one) the same window is shown as
 * a dialog: see {@link #asDialog}.
 */
public abstract class CanvasWindow extends SimpleGui {
	private static final Identifier EMPTY = Identifier.fromNamespaceAndPath("libreria", "gui/empty");
	/** The tooltip of our windows and menus: the look's panel. */
	public static final Identifier TOOLTIP = Identifier.fromNamespaceAndPath("libreria", "panel");

	protected final ServerPlayer player;
	/** What an area does that only tells something (no click): shown as text, not a button, when the window is a dialog. */
	protected static final Consumer<ServerPlayer> NOTE = p -> {
	};

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

	/** The window's picture as JSON (for the self-tests: what exactly the title holds). */
	public String pictureJson() {
		var c = draw().build();
		return net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, c)
				.result().map(Object::toString).orElse("?");
	}

	/** Builds everything and opens the window (without the pack: as a dialog). */
	public void show() {
		if (!available(this.player)) {
			asDialog();
			return;
		}
		for (int i = 0; i < 90; i++) this.clearSlot(i);
		fill();
		this.setTitle(draw().build());
		this.open();
	}

	/** Redraws in place (after something changed). */
	protected void refresh() {
		if (this.dialog) {
			asDialog();
			return;
		}
		for (int i = 0; i < 90; i++) this.clearSlot(i);
		fill();
		this.setTitle(draw().build());
	}

	/** A button over {@code w × h} cells: the same tooltip and action in each of them. */
	protected void area(int col, int row, int w, int h, Component name, List<Component> lines, Consumer<ServerPlayer> action) {
		if (record(col, row, w, h, name, lines, action, null)) return;
		for (int r = row; r < row + h; r++) {
			for (int c = col; c < col + w; c++) {
				this.setSlot(cell(c, r), element(new ItemStack(Items.PAPER), true, name, lines, action));
			}
		}
	}

	/** Like {@link #area}, but the action is told whether it was a right-click. */
	protected void areaClick(int col, int row, int w, int h, Component name, List<Component> lines,
			java.util.function.BiConsumer<ServerPlayer, Boolean> action) {
		if (record(col, row, w, h, name, lines, p -> action.accept(p, false), p -> action.accept(p, true))) return;
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

	/** Like {@link #area}, but the action gets the kind of click (left, right, shift, Q …). */
	protected void areaTyped(int col, int row, int w, int h, Component name, List<Component> lines,
			java.util.function.BiConsumer<ServerPlayer, eu.pb4.sgui.api.ClickType> action) {
		if (record(col, row, w, h, name, lines, p -> action.accept(p, eu.pb4.sgui.api.ClickType.MOUSE_LEFT),
				p -> action.accept(p, eu.pb4.sgui.api.ClickType.MOUSE_RIGHT))) return;
		for (int r = row; r < row + h; r++) {
			for (int c = col; c < col + w; c++) {
				this.setSlot(cell(c, r), typed(new ItemStack(Items.PAPER), true, name, lines, action));
			}
		}
	}

	/** A real item in one cell, with a tooltip and an action that gets the kind of click. */
	protected void iconTyped(int col, int row, ItemStack stack, Component name, List<Component> lines,
			java.util.function.BiConsumer<ServerPlayer, eu.pb4.sgui.api.ClickType> action) {
		if (record(col, row, 1, 1, name, lines, p -> action.accept(p, eu.pb4.sgui.api.ClickType.MOUSE_LEFT),
				p -> action.accept(p, eu.pb4.sgui.api.ClickType.MOUSE_RIGHT))) return;
		this.setSlot(cell(col, row), typed(stack, false, name, lines, action));
	}

	private GuiElementBuilder typed(ItemStack stack, boolean invisible, Component name, List<Component> lines,
			java.util.function.BiConsumer<ServerPlayer, eu.pb4.sgui.api.ClickType> action) {
		GuiElementBuilder b = element(stack, invisible, name, lines, null);
		return b.setCallback((index, type, act, gui) -> action.accept(this.player, type));
	}

	/** One of the old menus' buttons (a builder with its tooltip and action) as an invisible area. */
	protected void areaFrom(int col, int row, int w, GuiElementBuilder button) {
		if (this.recording != null) {
			var el = button.build();
			ItemStack st = el.getItemStack();
			List<Component> lore = new ArrayList<>();
			var l = st.get(net.minecraft.core.component.DataComponents.LORE);
			if (l != null) lore.addAll(l.lines());
			var callback = el.getGuiCallback();
			int index = cell(col, row);
			record(col, row, w, 1, st.getHoverName(), lore, p -> pressed(callback, index), null);
			return;
		}
		button.model(EMPTY).hideDefaultTooltip();
		var el = button.build();
		ItemStack st = el.getItemStack().copy();
		st.set(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE, TOOLTIP);
		var wrapped = new eu.pb4.sgui.api.elements.SimpleGuiElement(st, el.getGuiCallback());
		for (int c = col; c < col + w; c++) this.setSlot(cell(c, row), wrapped);
	}

	/** A real item (an icon) in one cell of an area, with the area's tooltip and action. */
	protected void icon(int col, int row, ItemStack stack, Component name, List<Component> lines, Consumer<ServerPlayer> action) {
		if (record(col, row, 1, 1, name, lines, action, null)) return;
		this.setSlot(cell(col, row), element(stack, false, name, lines, action));
	}

	private GuiElementBuilder element(ItemStack stack, boolean invisible, Component name, List<Component> lines,
			Consumer<ServerPlayer> action) {
		ItemStack st = stack.copyWithCount(1);
		st.set(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE, TOOLTIP);
		// A picture on a button is no treasure: no rarity frame around it
		st.set(net.minecraft.core.component.DataComponents.RARITY, net.minecraft.world.item.Rarity.COMMON);
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

	// ---------------------------------------------------------------- without the pack: a dialog

	/** A button or a note of the window, as it fills its cells: where, what it says, what a click (or a right-click) does. */
	private record Entry(int col, int row, int w, int h, Component name, List<Component> lines, @Nullable Consumer<ServerPlayer> left,
			@Nullable Consumer<ServerPlayer> right) {
		boolean holds(Canvas.Run r) {
			return r.x() >= Canvas.cellX(this.col) && r.x() < Canvas.cellX(this.col + this.w) && r.y() >= Canvas.cellY(this.row)
					&& r.y() < Canvas.cellY(this.row + this.h - 1) + 18;
		}
	}

	/** Shown as a dialog (refreshing shows it again). */
	private boolean dialog;
	/** While filling for the dialog: what the window has. */
	private @Nullable List<Entry> recording;

	private boolean record(int col, int row, int w, int h, Component name, List<Component> lines, @Nullable Consumer<ServerPlayer> left,
			@Nullable Consumer<ServerPlayer> right) {
		if (this.recording == null) return false;
		this.recording.add(new Entry(col, row, w, h, name, List.copyOf(lines), left, right));
		return true;
	}

	/** One of the old menus' buttons, pressed (from a dialog). */
	private void pressed(Object callback, int index) {
		for (var m : callback.getClass().getMethods()) {
			if (!m.getName().equals("click") || m.getParameterCount() != 4) continue;
			try {
				m.setAccessible(true);
				m.invoke(callback, index, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, null, this);
			} catch (ReflectiveOperationException | RuntimeException e) {
				nl.libreria.core.Libreria.LOG.warn("A button of {} failed as a dialog button", getClass().getSimpleName(), e);
			}
			return;
		}
	}

	/** Something to click (not a {@link #NOTE}). */
	private static boolean clickable(Entry e) {
		return e.left() != null && e.left() != NOTE;
	}

	private static boolean arrow(String t) {
		String s = t.strip();
		return s.isEmpty() || s.equals("›") || s.equals("‹") || s.equals("▸") || s.equals(">") || s.equals("<");
	}

	/** The text runs, line by line (top to bottom, left to right), as plain lines. */
	private static List<String> lines(List<Canvas.Run> runs) {
		TreeMap<Integer, List<Canvas.Run>> byY = new TreeMap<>();
		for (Canvas.Run r : runs) {
			if (arrow(r.text())) continue;
			byY.computeIfAbsent(r.y(), k -> new ArrayList<>()).add(r);
		}
		List<String> out = new ArrayList<>();
		for (List<Canvas.Run> line : byY.values()) {
			line.sort(Comparator.comparingInt(Canvas.Run::x));
			StringBuilder sb = new StringBuilder();
			for (Canvas.Run r : line) {
				if (sb.length() > 0) sb.append("  ");
				sb.append(r.text().strip());
			}
			String s = sb.toString().strip();
			if (!s.isEmpty()) out.add(s);
		}
		return out;
	}

	/**
	 * The window as a dialog, for whoever can't see our windows (no pack; Bedrock players, whose game shows a dialog as
	 * one of its own forms, made for touch and controllers). It says what the window says, line by line, and has a
	 * button for everything you can click, labelled with what's written on it (and one more for what a right-click does).
	 * After a click that changes the window, it's shown again.
	 */
	protected void asDialog() {
		this.dialog = true;
		List<Entry> entries = new ArrayList<>();
		this.recording = entries;
		try {
			fill();
		} finally {
			this.recording = null;
		}
		List<Canvas.Run> runs = draw().runs();
		int top = Canvas.cellY(0);
		// The buttons, top to bottom, left to right; an icon and the area beside it with the same name are one
		entries.sort(Comparator.comparingInt(Entry::row).thenComparingInt(Entry::col));
		List<Entry> buttons = new ArrayList<>();
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (Entry e : entries) {
			if (!clickable(e)) continue;
			if (seen.add(e.row() + "|" + e.name().getString())) buttons.add(e);
		}
		// The title: what's written above the cells
		List<Canvas.Run> heading = new ArrayList<>(runs.stream().filter(r -> r.y() < top).toList());
		heading.sort(Comparator.comparingInt(Canvas.Run::x));
		StringBuilder title = new StringBuilder();
		int end = Integer.MIN_VALUE;
		for (Canvas.Run r : heading) {
			// pieces that touch belong together ("You", " › ", "Players"); a gap between them is a space
			if (title.length() > 0 && r.x() > end + 4) title.append("   ");
			title.append(r.text());
			end = r.x() + r.width();
		}
		String t = title.toString().strip();
		Window w = Window.of(Ui.text(t.isEmpty() ? "Libreria" : t, ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD)).wide();
		// The text, without what's written on the buttons (it's their label)
		List<Canvas.Run> body = new ArrayList<>();
		for (Canvas.Run r : runs) {
			if (r.y() < top) continue;
			boolean onButton = false;
			for (Entry e : buttons) {
				for (Entry same : entries) {
					if (same.row() == e.row() && same.name().getString().equals(e.name().getString()) && same.holds(r)) onButton = true;
				}
			}
			if (!onButton) body.add(r);
		}
		for (String line : lines(body)) w.text(Ui.text(line, ChatFormatting.WHITE));
		for (Entry e : buttons) {
			List<Canvas.Run> on = new ArrayList<>();
			for (Canvas.Run r : runs) {
				for (Entry same : entries) {
					if (same.row() == e.row() && same.name().getString().equals(e.name().getString()) && same.holds(r) && !on.contains(r)) on.add(r);
				}
			}
			String label = String.join(" · ", lines(on));
			if (label.isEmpty()) label = Canvas.plain(e.name()).strip();
			Component tip = e.lines().isEmpty() ? null : Component.empty();
			if (tip != null) {
				var c = Component.empty();
				for (int i = 0; i < e.lines().size(); i++) {
					if (i > 0) c.append("\n");
					c.append(Component.literal(Canvas.plain(e.lines().get(i))).withStyle(e.lines().get(i).getStyle()));
				}
				tip = c;
			}
			Consumer<ServerPlayer> left = e.left();
			w.button(Ui.text(label, ChatFormatting.WHITE), tip, (p, v) -> {
				if (left != null) left.accept(p);
			});
			if (e.right() != null) {
				String how = null;
				for (Component l : e.lines()) {
					String s = Canvas.plain(l).strip();
					int at = s.toLowerCase(java.util.Locale.ROOT).indexOf("right-click");
					if (at >= 0) how = s.substring(at + "right-click".length()).replaceFirst("^[:\\s]+", "").strip();
				}
				if (how != null && !how.isEmpty()) {
					Consumer<ServerPlayer> right = e.right();
					w.button(Ui.text(label + " – " + how, ChatFormatting.WHITE), tip, (p, v) -> right.accept(p));
				}
			}
		}
		if (body.isEmpty() && buttons.isEmpty()) w.text(Ui.text("Nothing here yet.", ChatFormatting.GRAY));
		w.show(this.player);
	}
}
