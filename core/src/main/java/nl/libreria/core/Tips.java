package nl.libreria.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.dialog.Window;
import nl.libreria.core.gui.TopCard;

/**
 * Tips at the moment they're useful, once: the first time you open a chest you learn that middle-click sorts it; the
 * first time you're on the land map, that the mouse turns it. A card at the top in the colour of its domain, never chat.
 * Every tip can be read back under Getting started → Tips, so nothing is lost by missing one.
 *
 * <pre>{@code
 * static final Tips.Tip SORT = Tips.register("chest_sort", Domain.ITEMS, Items.CHEST, "Sorting a chest",
 *         "Middle-click an empty slot to sort it.", "Shift-click the ⇅ button for this chest's options.");
 * ...
 * Tips.once(player, SORT);
 * }</pre>
 */
public final class Tips {
	/** A tip: a title and one or two short lines. */
	public record Tip(String key, Domain domain, Item icon, String title, List<String> lines) {
	}

	public static final class Data {
		public Set<String> seen = new HashSet<>();
	}

	static final PlayerStore<Data> STORE = PlayerStore.create("libreria/tips", Data.class, Data::new, d -> {
		if (d.seen == null) d.seen = new HashSet<>();
	});

	public static final Setting.Toggle ENABLED = Libreria.MODULE.toggle("tips", "Tips when they're useful", true, Items.LANTERN,
			"A card at the top, once, the first time", "something is useful. All of them are under", "Getting started › Tips.")
			.in(Domain.YOU, "What you see");

	private static final Map<String, Tip> TIPS = new LinkedHashMap<>();
	private static final String CHANNEL = "tip";
	/** Under a toast (so "Chest sorted" still shows), above the look-at card and place names. */
	private static final int PRIORITY = 35, TICKS = 170;

	private Tips() {
	}

	public static Tip register(String key, Domain domain, Item icon, String title, String... lines) {
		Tip t = new Tip(key, domain, icon, title, List.of(lines));
		TIPS.put(key, t);
		return t;
	}

	/** Shows the tip if this player has never seen it (and hasn't switched tips off). */
	public static void once(ServerPlayer p, Tip tip) {
		Data d = STORE.get(p);
		if (d.seen.contains(tip.key())) return;
		if (!ENABLED.get(p)) return;
		d.seen.add(tip.key());
		STORE.markDirty(p);
		show(p, tip);
	}

	/** Has this player seen it? */
	public static boolean seen(ServerPlayer p, Tip tip) {
		return STORE.get(p).seen.contains(tip.key());
	}

	/** The card itself. */
	public static void show(ServerPlayer p, Tip tip) {
		List<TopCard.Row> rows = new ArrayList<>();
		for (int i = 1; i < tip.lines().size(); i++) rows.add(TopCard.Row.text("", "", Ui.text(tip.lines().get(i), ChatFormatting.WHITE)));
		Component subtitle = tip.lines().isEmpty() ? Component.empty() : Ui.text(tip.lines().getFirst(), ChatFormatting.WHITE);
		TopCard.show(p, CHANNEL, new TopCard.Card(new ItemStack(tip.icon()), tip.domain().rgb,
				Component.empty().append(Ui.text("Tip  ", tip.domain().rgb)).append(Ui.text(tip.title(), ChatFormatting.WHITE)), subtitle, rows),
				PRIORITY, TICKS + 30 * rows.size());
		Ui.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f, 1.6f);
	}

	public static List<Tip> all(Domain d) {
		List<Tip> out = new ArrayList<>();
		for (Tip t : TIPS.values()) if (t.domain() == d) out.add(t);
		return out;
	}

	/** Getting started → Tips: every tip, by domain; the ones you haven't come across yet are marked. */
	public static void all(ServerPlayer p) {
		Window w = Window.path("Getting started", "Tips");
		Data data = STORE.get(p);
		boolean any = false;
		for (Domain d : Domain.values()) {
			List<Tip> tips = all(d);
			if (tips.isEmpty()) continue;
			if (any) w.text(Component.empty());
			any = true;
			w.text(d.title().withStyle(ChatFormatting.BOLD));
			for (Tip t : tips) {
				var line = Component.empty().append(Ui.text(t.title(), ChatFormatting.WHITE));
				if (!data.seen.contains(t.key())) line.append(Ui.text("  new", d.rgb));
				line.append(Ui.text("  " + String.join(" ", t.lines()), ChatFormatting.GRAY));
				w.text(line);
			}
		}
		if (!any) w.text("No tips on this server.");
		w.back(Welcome::guide).show(p);
	}
}
