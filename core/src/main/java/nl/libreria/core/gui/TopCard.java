package nl.libreria.core.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A card at the top of the screen (with the Libreria resource pack): a boss bar whose title is drawn as a whole
 * adventure card (panel, icon in a slot, gem, name, subtitle, rule, rows with pictures and pips) and whose bar the pack
 * makes invisible. Modules show cards on their own channel with a priority; the highest one is shown. The info tooltip
 * for blocks uses it, and so does the land map.
 */
public final class TopCard {
	/** One row: a little picture (icon name, or "" for none), a grey label, optional pips and a value. */
	public record Row(String icon, String label, double fraction, int barColor, int segments, Component value) {
		public static Row text(String icon, String label, Component value) {
			return new Row(icon, label, -1, 0, 0, value);
		}

		public static Row bar(String icon, String label, double fraction, int barColor, int segments, Component value) {
			return new Row(icon, label, fraction, barColor, segments, value);
		}
	}

	/** What the card shows. accent = colour of the gem (-1 = plain bronze). */
	public record Card(ItemStack icon, int accent, Component name, Component subtitle, List<Row> rows) {
	}

	public static final int BRONZE = 0xF2C36B;

	private record Entry(Card card, int priority) {
	}

	private static final class Top {
		final ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.WHITE,
				BossEvent.BossBarOverlay.PROGRESS);
		final Map<String, Entry> cards = new HashMap<>();
		@Nullable String shown;
		boolean visible;
	}

	private static final Map<UUID, Top> TOPS = new HashMap<>();

	private TopCard() {
	}

	/** Shows (or updates) a card on a channel. Only the card with the highest priority is on screen. */
	public static void show(ServerPlayer p, String channel, Card card, int priority) {
		Top top = TOPS.computeIfAbsent(p.getUUID(), k -> {
			Top t = new Top();
			t.bar.setProgress(0f);
			return t;
		});
		top.cards.put(channel, new Entry(card, priority));
		update(p, top);
	}

	public static void hide(ServerPlayer p, String channel) {
		Top top = TOPS.get(p.getUUID());
		if (top != null && top.cards.remove(channel) != null) update(p, top);
	}

	public static void forget(UUID id) {
		Top top = TOPS.remove(id);
		if (top != null) top.bar.removeAllPlayers();
	}

	public static void clearAll() {
		TOPS.values().forEach(t -> t.bar.removeAllPlayers());
		TOPS.clear();
	}

	private static void update(ServerPlayer p, Top top) {
		Entry best = null;
		for (Entry e : top.cards.values()) {
			if (best == null || e.priority() > best.priority()) best = e;
		}
		if (best == null) {
			if (top.visible) {
				top.bar.removePlayer(p);
				top.visible = false;
				top.shown = null;
			}
			return;
		}
		Card c = best.card();
		String key = key(c);
		if (!key.equals(top.shown)) {
			top.bar.setName(compose(c));
			top.shown = key;
		}
		if (!top.visible) {
			top.bar.addPlayer(p);
			top.visible = true;
		}
	}

	private static String key(Card c) {
		StringBuilder b = new StringBuilder().append(c.accent()).append('|').append(c.name()).append('|').append(c.subtitle()).append('|')
				.append(BuiltInRegistries.ITEM.getKey(c.icon().getItem())).append(c.icon().getComponentsPatch());
		for (Row r : c.rows()) b.append('|').append(r.icon()).append(r.label()).append(r.fraction()).append(r.barColor()).append(r.value());
		return b.toString();
	}

	private static MutableComponent row(Row r) {
		MutableComponent out = Component.empty();
		if (!r.icon().isEmpty()) out.append(Glyphs.icon(r.icon()));
		if (!r.label().isEmpty()) out.append(Component.literal(r.label() + " ").withStyle(ChatFormatting.GRAY));
		if (r.fraction() >= 0) {
			int full = (int) Math.round(Math.max(0, Math.min(1, r.fraction())) * r.segments());
			out.append(Glyphs.pips(full, r.segments(), r.barColor())).append(Component.literal(" "));
		}
		return out.append(r.value());
	}

	/**
	 * The card as one line of text: the panel, then every line in its own font (each font is moved down one line),
	 * jumping back to the left edge with negative space in between. Ends at the panel's right edge so the boss bar
	 * centres it.
	 */
	public static Component compose(Card c) {
		int[] icon = c.icon().isEmpty() ? null : Glyphs.icon(c.icon().getItem());
		boolean slot = !c.icon().isEmpty();
		int indent = slot ? 24 : 0;
		List<MutableComponent> rows = new ArrayList<>();
		for (Row r : c.rows()) rows.add(row(r));
		int innerW = Math.max(Math.max(indent + Glyphs.width(c.name()), indent + Glyphs.width(c.subtitle())), Glyphs.width(rows)) + 2;
		innerW = Math.max(innerW, 64);
		int gemX = innerW / 2 - 3;

		List<Component> lines = new ArrayList<>();
		List<Integer> widths = new ArrayList<>();
		int gem = c.accent() < 0 ? BRONZE : c.accent();
		MutableComponent first = Component.empty()
				.append(Glyphs.ui(Glyphs.space(gemX) + Glyphs.g("gem") + Glyphs.space(-(gemX + Glyphs.advance("gem"))), gem));
		if (slot) {
			first.append(Glyphs.ui(Glyphs.g("slot") + Glyphs.space(2 - Glyphs.advance("slot"))));
			int iconAdv = 0;
			if (icon != null) {
				first.append(Glyphs.ui(Character.toString(icon[0]), icon[1] == 1 ? 0x6AA84F : 0xFFFFFF));
				iconAdv = icon[2];
			}
			first.append(Glyphs.ui(Glyphs.space(indent - 2 - iconAdv)));
		}
		first.append(c.name());
		lines.add(first);
		widths.add(indent + Glyphs.width(c.name()));
		lines.add(Component.empty().append(Glyphs.ui(Glyphs.space(indent))).append(c.subtitle()));
		widths.add(indent + Glyphs.width(c.subtitle()));
		if (!rows.isEmpty()) {
			lines.add(Glyphs.divider(innerW));
			widths.add(innerW);
			for (MutableComponent r : rows) {
				lines.add(r);
				widths.add(Glyphs.width(r));
			}
		}
		int n = Math.min(lines.size(), Glyphs.hudLines);
		int wp = innerW + 2 * Glyphs.padX;
		MutableComponent out = Component.empty();
		out.append(Glyphs.inFont(Glyphs.panel(n, innerW), Glyphs.hud(0), true));
		out.append(Glyphs.ui(Glyphs.space(-wp)));
		for (int k = 0; k < n; k++) {
			out.append(Glyphs.ui(Glyphs.space(Glyphs.padX)));
			out.append(Glyphs.inFont(lines.get(k), Glyphs.hud(k), true));
			out.append(Glyphs.ui(Glyphs.space(-(Glyphs.padX + widths.get(k)))));
		}
		return out.append(Glyphs.ui(Glyphs.space(wp)));
	}
}
