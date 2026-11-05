package nl.libreria.collector.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import nl.libreria.collector.Records;
import nl.libreria.core.Domain;
import nl.libreria.core.Start;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Canvas;
import nl.libreria.core.gui.CanvasWindow;
import nl.libreria.core.gui.Glyphs;

/**
 * The server's records as a list: what, how much, who. Your own records in gold. The tooltip says since when and
 * whose it was before.
 */
public final class RecordsWindow extends CanvasWindow {
	private static final int TEXT = 0xFFFFFF, OTHER = 0x6E7480;
	private static final int[] ROWS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private RecordsWindow(ServerPlayer p) {
		super(p);
	}

	public static void open(ServerPlayer p) {
		new RecordsWindow(p).show();
	}

	private static String holder(Records.Rec r) {
		return r == null ? "nobody" : r.name;
	}

	@Override
	protected Canvas draw() {
		Canvas c = new Canvas().panel();
		int x = 8;
		c.text(Domain.PROGRESS.label, x, 5, Domain.PROGRESS.rgb);
		x += Glyphs.width(Component.literal(Domain.PROGRESS.label));
		c.text(" › ", x, 5, Glyphs.muted);
		c.text("Records", x + Glyphs.width(Component.literal(" › ")), 5, TEXT);
		Records.Kind[] kinds = Records.Kind.values();
		int left = Canvas.cellX(1) + 4, width = Canvas.cellX(9) - Canvas.CARD_MARGIN - left;
		// Who on the right, the value in a column left of the widest name
		int whoW = 0;
		for (Records.Kind k : kinds) whoW = Math.max(whoW, Glyphs.width(Component.literal(Canvas.fit(holder(Records.get(k)), 48))));
		int valueRight = left + width - 6 - whoW - 10;
		for (int i = 0; i < kinds.length && i < ROWS.length; i++) {
			Records.Kind k = kinds[i];
			Records.Rec r = Records.get(k);
			boolean mine = r != null && this.player.getUUID().equals(r.who);
			int row = ROWS[i], y = Canvas.cellY(row) + 5;
			c.line(left, row, width, mine ? Domain.PROGRESS.rgb : OTHER);
			c.right(Canvas.fit(holder(r), 48), left + width - 6, y, r == null ? 0x8A909C : mine ? TEXT : Glyphs.muted);
			String v = r == null ? "–" : k.format(r.value);
			int vw = Glyphs.width(Component.literal(v));
			c.right(v, valueRight, y, TEXT);
			c.text(Canvas.fit(k.label, valueRight - vw - 8 - (left + 6)), left + 6, y, TEXT);
		}
		c.card(0, 9, 9, 1, Glyphs.neutral);
		c.centred("Back", Canvas.cellX(0) + 81, Canvas.cellY(9) + 5, TEXT);
		return c;
	}

	@Override
	protected void fill() {
		Records.Kind[] kinds = Records.Kind.values();
		for (int i = 0; i < kinds.length && i < ROWS.length; i++) {
			Records.Kind k = kinds[i];
			Records.Rec r = Records.get(k);
			List<Component> lore = new ArrayList<>();
			if (r == null) {
				lore.add(Ui.text("Nobody has this record yet.", ChatFormatting.GRAY));
			} else {
				lore.add(Component.empty().append(Ui.text(r.name + " ", ChatFormatting.YELLOW)).append(Ui.text(k.verb + " " + k.format(r.value),
						ChatFormatting.WHITE)));
				lore.add(Ui.text("Since " + DATE.format(Instant.ofEpochMilli(r.at).atZone(ZoneId.systemDefault())), ChatFormatting.GRAY));
				if (r.before != null) lore.add(Ui.text("Before: " + r.beforeName + ", " + k.format(r.beforeValue), ChatFormatting.DARK_GRAY));
			}
			lore.add(Component.empty());
			lore.add(Ui.text("Holding it ever earns the title " + k.title, ChatFormatting.GRAY));
			Component name = Ui.text(k.about, Domain.PROGRESS.rgb).withStyle(ChatFormatting.BOLD);
			area(1, ROWS[i], 8, 1, name, lore, p -> {
			});
			icon(0, ROWS[i], new ItemStack(k.icon), name, lore, p -> {
			});
		}
		area(0, 9, 9, 1, Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), List.of(Ui.text("To Progress.", ChatFormatting.GRAY)),
				p -> Start.domain(p, Domain.PROGRESS));
	}
}
