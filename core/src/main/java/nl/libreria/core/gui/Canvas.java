package nl.libreria.core.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

/**
 * The picture of a Libreria window, drawn by the title of a chest screen: Minecraft draws that title before the slots
 * and the items, so the whole window can be ours (the Libreria pack takes care of the pieces). Everything is placed in
 * window pixels (0, 0 = the window's top left; it is 176 × 222). Things are drawn in the order you add them.
 *
 * <ul>
 * <li>{@link #panel()} — the window itself.</li>
 * <li>{@link #card} — a card on the grid of cells (columns 0–8, rows 0–9: six of the chest, three of your inventory
 * and the hotbar), tinted in any colour.</li>
 * <li>{@link #text} and {@link #picture} — anywhere (y from 5 to 219).</li>
 * </ul>
 */
public final class Canvas {
	/** The title line starts here. */
	private static final int START_X = 8;
	public static final int WIDTH = 176, HEIGHT = 222;
	public static final int MIN_Y = 5, MAX_Y = 219;

	private final MutableComponent out = Component.empty();
	private int x = START_X;
	/** Texts with a key in them: drawn last (only the client knows how wide a key's name is). */
	private final List<Runnable> late = new ArrayList<>();

	/** The window: Libreria's panel over the whole chest screen. */
	public Canvas panel() {
		return glyph(0, Glyphs.FONT, "win.panel", 0xFFFFFF);
	}

	/** Left edge of a column of cells (its slot frame). */
	public static int cellX(int col) {
		return 7 + 18 * col;
	}

	/** Top of a row of cells (its slot frame): rows 0–5 are the chest's, 6–8 your inventory's, 9 the hotbar. */
	public static int cellY(int row) {
		if (row < 6) return 17 + 18 * row;
		if (row < 9) return 139 + 18 * (row - 6);
		return 197;
	}

	/** Pixels of room between a card and the edge of its cells. */
	public static final int CARD_MARGIN = 2;

	/**
	 * A card over {@code w × h} cells starting at ({@code col}, {@code row}) (h from 1 to 4, all in one part of the
	 * window), in the colour {@code rgb}: a bright rim in that colour around a dark fill.
	 */
	public Canvas card(int col, int row, int w, int h, int rgb) {
		int width = 18 * w - 2 * CARD_MARGIN;
		String back = Glyphs.g("space-1");
		String pieces = Glyphs.g("card" + h + ".l") + back + Glyphs.tiles("card" + h + ".m", width - 8) + Glyphs.g("card" + h + ".r") + back;
		move(cellX(col) + CARD_MARGIN);
		this.out.append(Component.literal(pieces).withStyle(s -> s.withFont(row(row)).withColor(TextColor.fromRgb(rgb))));
		this.x += width;
		return this;
	}

	/**
	 * The card of a list row: from window x {@code x}, {@code width} wide, 16 px high and level with the item picture
	 * in a cell of that row (so a picture beside it is exactly as high).
	 */
	public Canvas line(int x, int row, int width, int rgb) {
		String back = Glyphs.g("space-1");
		String pieces = Glyphs.g("line.l") + back + Glyphs.tiles("line.m", width - 8) + Glyphs.g("line.r") + back;
		move(x);
		this.out.append(Component.literal(pieces).withStyle(s -> s.withFont(row(row)).withColor(TextColor.fromRgb(rgb))));
		this.x += width;
		return this;
	}

	/**
	 * A bar in a list row: a row card from {@code x}, {@code width} wide, filled for {@code fraction} in {@code rgb}.
	 * Text drawn after it lands on top.
	 */
	public Canvas bar(int x, int row, int width, double fraction, int rgb) {
		line(x, row, width, Glyphs.neutral);
		int fill = (int) Math.round(Math.max(0, Math.min(1, fraction)) * (width - 6));
		if (fill > 0 && Glyphs.has("fill.m1")) {
			move(x + 3);
			this.out.append(Component.literal(Glyphs.tiles("fill.m", fill)).withStyle(s -> s.withFont(row(row)).withColor(TextColor.fromRgb(rgb))));
			this.x += fill;
		}
		return this;
	}

	/**
	 * A thin bar (2 px) in a list row, under its text (draw the row's text a pixel higher: at the row's top + 4): from
	 * {@code x}, {@code width} wide, a dark track filled for {@code fraction} in {@code rgb}.
	 */
	public Canvas thin(int x, int row, int width, double fraction, int rgb) {
		if (!Glyphs.has("thin.m1") || width <= 0) return this;
		move(x);
		this.out.append(Component.literal(Glyphs.tiles("thin.m", width)).withStyle(s -> s.withFont(row(row)).withColor(TextColor.fromRgb(TRACK))));
		this.x += width;
		int fill = (int) Math.round(Math.max(0, Math.min(1, fraction)) * width);
		// a sliver of colour as soon as there is anything at all
		if (fraction > 0 && fill == 0) fill = 1;
		if (fill > 0) {
			move(x);
			this.out.append(Component.literal(Glyphs.tiles("thin.m", fill)).withStyle(s -> s.withFont(row(row)).withColor(TextColor.fromRgb(rgb))));
			this.x += fill;
		}
		return this;
	}

	/** The empty part of a thin bar. */
	private static final int TRACK = 0x34373E;

	/** Text with its top at ({@code x}, {@code y}), with a soft shadow. */
	public Canvas text(String text, int x, int y, int rgb) {
		return text(Component.literal(clean(text)), x, y, rgb, true);
	}

	/** Look-alikes for characters a window can't draw (the client would draw a box for them, on the title line). */
	private static final java.util.Map<Integer, String> LOOK_ALIKE = java.util.Map.ofEntries(
			java.util.Map.entry((int) '✚', "+"), java.util.Map.entry((int) '▸', "›"), java.util.Map.entry((int) '▶', "›"),
			java.util.Map.entry((int) '►', "›"), java.util.Map.entry((int) '◀', "‹"), java.util.Map.entry((int) '◄', "‹"),
			java.util.Map.entry((int) '•', "·"), java.util.Map.entry((int) '–', "-"), java.util.Map.entry((int) '—', "-"),
			java.util.Map.entry((int) '’', "'"), java.util.Map.entry((int) '“', "\""), java.util.Map.entry((int) '”', "\""));

	/** {@code text} with only characters a window can draw (look-alikes where there are, otherwise left out). */
	public static String clean(String text) {
		if (Glyphs.CANVAS_CHARS.isEmpty()) return text;
		StringBuilder sb = new StringBuilder();
		text.codePoints().forEach(cp -> {
			if (Glyphs.CANVAS_CHARS.contains(cp)) sb.appendCodePoint(cp);
			else if (LOOK_ALIKE.containsKey(cp)) sb.append(LOOK_ALIKE.get(cp));
		});
		return sb.toString();
	}

	/** A piece of text drawn on the canvas: what it says and where (its top left). */
	public record Run(String text, int x, int y, int width) {
	}

	private final List<Run> runs = new ArrayList<>();

	/** Every piece of text drawn, in the order it was drawn (a window shown as a dialog says the same). */
	public List<Run> runs() {
		return this.runs;
	}

	/** Text with its top at ({@code x}, {@code y}). */
	public Canvas text(Component text, int x, int y, int rgb, boolean shadow) {
		if (y < MIN_Y || y > MAX_Y) return this;
		int w = Glyphs.width(text);
		this.runs.add(new Run(text.getString(), x, y, w));
		move(x);
		FontDescription font = font(y);
		MutableComponent c = text.copy().withStyle(s -> {
			var st = s.withFont(font).withColor(TextColor.fromRgb(rgb)).withItalic(false);
			return shadow ? st.withShadowColor(0xC0000000 | shade(rgb)) : st;
		});
		this.out.append(c);
		this.x += w;
		return this;
	}

	/** Text centred on {@code cx}. */
	public Canvas centred(String text, int cx, int y, int rgb) {
		String t = clean(text);
		return text(t, cx - Glyphs.width(Component.literal(t)) / 2, y, rgb);
	}

	/** Text that ends at {@code right}. */
	public Canvas right(String text, int right, int y, int rgb) {
		String t = clean(text);
		return text(t, right - Glyphs.width(Component.literal(t)), y, rgb);
	}

	/**
	 * One of the pack's pictures with its top left at (x, y): an emblem ("big.chest", "sm.gear") anywhere, or one of
	 * Minecraft's item pictures at twice the size ("ico.bundle") or at their own size ("ico16.bundle"), which exist a
	 * card's top + 4, 6 or 8 px down.
	 */
	public Canvas picture(String glyph, int x, int y, int rgb) {
		if (y < MIN_Y || y > MAX_Y || !Glyphs.has(glyph)) return this;
		if (glyph.startsWith("ico") && !Glyphs.ICON_Y.contains(y)) return this;
		return glyph(x, font(y), glyph, rgb);
	}

	/** A big item picture on a card, {@code dy} (4, 6 or 8) below the top of the card's first row. */
	public Canvas icon(String item, int x, int row, int dy) {
		return picture("ico." + item, x, cellY(row) + CARD_MARGIN + dy, 0xFFFFFF);
	}

	/** {@code text}, cut short with "…" if it is wider than {@code max} pixels. */
	public static String fit(String text, int max) {
		text = clean(text);
		if (Glyphs.width(Component.literal(text)) <= max) return text;
		String t = text;
		while (t.length() > 1 && Glyphs.width(Component.literal(t + "…")) > max) t = t.substring(0, t.length() - 1);
		return t.stripTrailing() + "…";
	}

	private Canvas glyph(int px, FontDescription font, String glyph, int rgb) {
		move(px);
		this.out.append(Component.literal(Glyphs.g(glyph)).withStyle(s -> s.withFont(font).withColor(TextColor.fromRgb(rgb))
				.withShadowColor(0).withItalic(false).withBold(false)));
		this.x += Glyphs.advance(glyph);
		return this;
	}

	/** The cursor to window x {@code px}. */
	private void move(int px) {
		int dx = px - this.x;
		if (dx != 0) this.out.append(Glyphs.ui(Glyphs.space(dx)));
		this.x = px;
	}

	/**
	 * Text with the player's own keys in it ({@code {sneak}} …), its left edge at x. It is drawn after everything
	 * else, because the width of a key's name is only known to the client; only the first of these is exact, so a
	 * window has at most one or two.
	 */
	public Canvas keys(String text, int x, int y, int rgb) {
		String t = clean(text);
		this.late.add(() -> text(nl.libreria.core.Keys.text(t), x, y, rgb, true));
		return this;
	}

	/** The finished title. */
	public Component build() {
		for (Runnable r : this.late) r.run();
		this.late.clear();
		return this.out;
	}

	/**
	 * Splits {@code text} into lines no wider than {@code max} pixels (at spaces).
	 */
	public static List<String> wrap(String text, int max) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && Glyphs.width(Component.literal(next)) > max) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		if (!line.isEmpty()) lines.add(line.toString());
		return lines;
	}

	private static int shade(int rgb) {
		int r = (rgb >> 16 & 255) / 5, g = (rgb >> 8 & 255) / 5, b = (rgb & 255) / 5;
		return r << 16 | g << 8 | b;
	}

	private static FontDescription font(int y) {
		return new FontDescription.Resource(Identifier.fromNamespaceAndPath("libreria", "y" + y));
	}

	private static FontDescription row(int row) {
		return new FontDescription.Resource(Identifier.fromNamespaceAndPath("libreria", "row" + row));
	}
}
