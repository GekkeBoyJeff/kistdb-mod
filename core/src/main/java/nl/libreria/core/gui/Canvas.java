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

	/** Text with its top at ({@code x}, {@code y}), with a soft shadow. */
	public Canvas text(String text, int x, int y, int rgb) {
		return text(Component.literal(text), x, y, rgb, true);
	}

	/** Text with its top at ({@code x}, {@code y}). */
	public Canvas text(Component text, int x, int y, int rgb, boolean shadow) {
		if (y < MIN_Y || y > MAX_Y) return this;
		int w = Glyphs.width(text);
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
		return text(text, cx - Glyphs.width(Component.literal(text)) / 2, y, rgb);
	}

	/** Text that ends at {@code right}. */
	public Canvas right(String text, int right, int y, int rgb) {
		return text(text, right - Glyphs.width(Component.literal(text)), y, rgb);
	}

	/** One of the pack's pictures (a big or small emblem: "big.chest", "sm.gear" …) with its top left at (x, y). */
	public Canvas picture(String glyph, int x, int y, int rgb) {
		if (y < MIN_Y || y > MAX_Y || !Glyphs.has(glyph)) return this;
		return glyph(x, font(y), glyph, rgb);
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

	/** The finished title. */
	public Component build() {
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
