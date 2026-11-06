package nl.libreria.collector.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import nl.libreria.collector.Achievement;
import nl.libreria.collector.Catalog;
import nl.libreria.collector.CollectorMod;
import nl.libreria.core.Domain;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Canvas;
import nl.libreria.core.gui.CanvasWindow;
import nl.libreria.core.gui.Glyphs;

/**
 * The grades of one ladder, lowest first, each in the colour of its rarity. The icon shows the grade's number. Click
 * one you earned and you wear it as your title (a lower grade too, if you like its name better), in its own colour.
 */
public final class GradesWindow extends CanvasWindow {
	private static final int TEXT = 0xFFFFFF, NOT_YET = 0x6E7480;

	private final List<Achievement> ladder;
	private final String place;
	private final Consumer<ServerPlayer> back;

	private GradesWindow(ServerPlayer p, List<Achievement> ladder, String place, Consumer<ServerPlayer> back) {
		super(p);
		this.ladder = ladder;
		this.place = place;
		this.back = back;
	}

	/** {@code place}: where it was opened from ("Mining"), for the header. */
	public static void open(ServerPlayer p, List<Achievement> ladder, String place, Consumer<ServerPlayer> back) {
		new GradesWindow(p, ladder, place, back).show();
	}

	private static int rgb(Achievement a) {
		TextColor c = TextColor.fromLegacyFormat(a.rarity().color);
		return c == null ? 0xFFFFFF : c.getValue();
	}

	private boolean got(Achievement a) {
		return CollectorMod.STORE.get(this.player).earned.containsKey(a.id());
	}

	private boolean wearing(Achievement a) {
		CollectorMod.Data d = CollectorMod.STORE.get(this.player);
		return a.id().equals(d.title) || (d.title.isEmpty() && CollectorMod.titleAchievement(d) == a);
	}

	/** "1,000" for a grade you have, "Wearing" for the one you wear, "49%" for one still to go. */
	private String right(Achievement a) {
		if (got(a)) return wearing(a) ? "Wearing" : String.format(Locale.US, "%,d", a.target());
		long now = a.current(this.player, CollectorMod.STORE.get(this.player));
		return Math.min(99, (int) (now * 100 / Math.max(1, a.target()))) + "%";
	}

	@Override
	protected Canvas draw() {
		Catalog.fresh();
		Canvas c = new Canvas().panel();
		int x = 8;
		c.text(this.place, x, 5, Domain.PROGRESS.rgb);
		x += Glyphs.width(Component.literal(this.place));
		c.text(" › ", x, 5, Glyphs.muted);
		c.text("Grades", x + Glyphs.width(Component.literal(" › ")), 5, TEXT);
		long have = this.ladder.stream().filter(this::got).count();
		c.right(have + " of " + this.ladder.size(), 168 - 2, 5, Glyphs.muted);
		int left = Canvas.cellX(1) + 4, width = Canvas.cellX(9) - Canvas.CARD_MARGIN - left;
		for (int i = 0; i < this.ladder.size() && i < 9; i++) {
			Achievement a = this.ladder.get(i);
			boolean got = got(a);
			int y = Canvas.cellY(i) + 5;
			String r = right(a);
			c.line(left, i, width, got ? rgb(a) : NOT_YET);
			int rw = Glyphs.width(Component.literal(r));
			c.text(Canvas.fit(a.name(), width - rw - 20), left + 6, y, got ? rgb(a) : 0x8A909C);
			c.right(r, left + width - 6, y, got ? (wearing(a) ? TEXT : Glyphs.muted) : 0x8A909C);
		}
		c.card(0, 9, 9, 1, Glyphs.neutral);
		c.centred("Back", Canvas.cellX(0) + 81, Canvas.cellY(9) + 5, TEXT);
		return c;
	}

	@Override
	protected void fill() {
		CollectorMod.Data d = CollectorMod.STORE.get(this.player);
		for (int i = 0; i < this.ladder.size() && i < 9; i++) {
			Achievement a = this.ladder.get(i);
			boolean got = got(a);
			List<Component> lore = new ArrayList<>();
			lore.add(Component.literal(a.rarity().label + " · grade " + (i + 1) + " of " + this.ladder.size()).withStyle(a.rarity().color));
			lore.add(Ui.text(a.description(), ChatFormatting.GRAY));
			lore.add(Component.empty());
			Consumer<ServerPlayer> click = p -> {
			};
			if (!got) {
				long now = a.current(this.player, d);
				lore.add(Ui.text(String.format(Locale.US, "Not yet: %,d of %,d", Math.min(now, a.target()), a.target()), ChatFormatting.DARK_GRAY));
			} else if (wearing(a)) {
				lore.add(Ui.text("✔ Your title now", ChatFormatting.GREEN));
			} else {
				lore.add(Ui.text("▸ Click: wear this grade", ChatFormatting.YELLOW));
				click = p -> {
					CollectorMod.setTitle(p, a.id());
					this.refresh();
				};
			}
			Component name = Ui.text(a.name(), got ? rgb(a) : 0x8A909C).withStyle(ChatFormatting.BOLD);
			ItemStack icon = new ItemStack(a.icon(), Math.min(99, i + 1));
			area(1, i, 8, 1, name, lore, click);
			icon(0, i, icon, name, lore, click);
		}
		area(0, 9, 9, 1, Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), List.of(Ui.text("To " + this.place + ".", ChatFormatting.GRAY)),
				this.back);
	}
}
