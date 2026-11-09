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
 * Tips at the moment they're useful, once: the first time you open a chest you learn that a double-click on an empty slot sorts it; the
 * first time you're on the land map, that the mouse turns it. A card at the top in the colour of its domain, never chat.
 * Every tip can be read back under Help → Tips, so nothing is lost by missing one.
 *
 * <pre>{@code
 * static final Tips.Tip SORT = Tips.register("chest_sort", Domain.YOU, Items.CHEST, "Sorting a chest",
 *         "Double-click an empty slot to sort it.", "Shift-click the ⇅ button for this chest's options.");
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
			"A card at the top, once, the first time", "something is useful. All of them are under", "Help › Tips.")
			.in(Domain.YOU, "What you see");

	private static final Map<String, Tip> TIPS = new LinkedHashMap<>();
	private static final String CHANNEL = "tip";
	/** Under a toast (so "Chest sorted" still shows), above the look-at card and place names. */
	private static final int PRIORITY = 35, TICKS = 170;

	private Tips() {
	}

	/** Loads the class at startup, so its setting is there from the start. */
	static void init() {
	}

	public static Tip register(String key, Domain domain, Item icon, String title, String... lines) {
		Tip t = new Tip(key, domain, icon, title, List.of(lines));
		TIPS.put(key, t);
		return t;
	}

	/** Tips waiting for a free moment, per player (the welcome card, a toast or the land map comes first). */
	private static final Map<java.util.UUID, java.util.ArrayDeque<Tip>> WAITING = new java.util.HashMap<>();
	/** Anything at or above this on top of the screen (place names, the welcome card, toasts, banners, the land map) goes first. */
	private static final int BUSY = 20;

	/**
	 * Shows the tip if this player has never seen it (and hasn't switched tips off). When something else is on top of
	 * the screen, the tip waits for it; it only counts as seen once it was actually shown.
	 */
	public static void once(ServerPlayer p, Tip tip) {
		if (STORE.get(p).seen.contains(tip.key()) || !ENABLED.get(p)) return;
		java.util.ArrayDeque<Tip> q = WAITING.computeIfAbsent(p.getUUID(), k -> new java.util.ArrayDeque<>());
		if (!q.contains(tip)) q.add(tip);
		next(p);
	}

	/** The tip on screen now, per player, and until when (server tick). */
	private record Shown(Tip tip, int until) {
	}

	private static final Map<java.util.UUID, Shown> SHOWN = new java.util.HashMap<>();
	/** When the top of the screen was last busy (a card of what you look at, a toast…), per player. */
	private static final Map<java.util.UUID, Integer> LAST_BUSY = new java.util.HashMap<>();

	/**
	 * Shows the next waiting tip when the top of the screen is free: nothing else there, also not the card of what you
	 * look at, and that for a second already (so a tip doesn't jump in between two looks).
	 */
	private static void next(ServerPlayer p) {
		java.util.ArrayDeque<Tip> q = WAITING.get(p.getUUID());
		if (q == null || q.isEmpty()) return;
		int now = p.level().getServer().getTickCount();
		if (nl.libreria.core.gui.TopCard.shownPriority(p) >= 0) {
			// (only the card of what you look at comes and goes as you look about: after it, a moment's quiet first)
			if (nl.libreria.core.gui.TopCard.anyBetween(p, 0, BUSY)) LAST_BUSY.put(p.getUUID(), now);
			return;
		}
		if (now - LAST_BUSY.getOrDefault(p.getUUID(), -1000) < 20) return;
		Tip tip = q.poll();
		if (q.isEmpty()) WAITING.remove(p.getUUID());
		if (!ENABLED.get(p)) return;
		Data d = STORE.get(p);
		if (!d.seen.add(tip.key())) return;
		STORE.markDirty(p);
		show(p, tip);
	}

	static void tick(net.minecraft.server.MinecraftServer server) {
		// A tip on screen makes way for the card of what you look at: it goes back in line and shows again, whole, later
		int now = server.getTickCount();
		for (java.util.UUID id : List.copyOf(SHOWN.keySet())) {
			Shown s = SHOWN.get(id);
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null || s.until() <= now) {
				SHOWN.remove(id);
				continue;
			}
			if (!nl.libreria.core.gui.TopCard.anyBetween(p, 0, BUSY)) continue;
			SHOWN.remove(id);
			nl.libreria.core.gui.TopCard.hide(p, CHANNEL);
			Data d = STORE.get(p);
			d.seen.remove(s.tip().key());
			STORE.markDirty(p);
			WAITING.computeIfAbsent(id, k -> new java.util.ArrayDeque<>()).addFirst(s.tip());
			LAST_BUSY.put(id, now);
		}
		for (java.util.UUID id : List.copyOf(WAITING.keySet())) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null) WAITING.remove(id);
			else next(p);
		}
	}

	static void forget(java.util.UUID id) {
		WAITING.remove(id);
		SHOWN.remove(id);
		LAST_BUSY.remove(id);
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
		int ticks = TICKS + 30 * rows.size();
		TopCard.show(p, CHANNEL, new TopCard.Card(new ItemStack(tip.icon()), tip.domain().rgb,
				Component.empty().append(Ui.text("Tip  ", tip.domain().rgb)).append(Ui.text(tip.title(), ChatFormatting.WHITE)), subtitle, rows),
				PRIORITY, ticks);
		SHOWN.put(p.getUUID(), new Shown(tip, p.level().getServer().getTickCount() + ticks));
		Ui.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f, 1.6f);
	}

	public static List<Tip> all(Domain d) {
		List<Tip> out = new ArrayList<>();
		for (Tip t : TIPS.values()) if (t.domain() == d) out.add(t);
		return out;
	}

	/** Help → Tips: every tip, by domain; the ones you haven't come across yet are marked. */
	public static void all(ServerPlayer p) {
		Window w = Window.path("Help", "Tips");
		Data data = STORE.get(p);
		boolean any = false;
		for (Domain d : Domain.values()) {
			List<Tip> tips = all(d);
			if (tips.isEmpty()) continue;
			any = true;
			var block = Component.empty().append(d.title().withStyle(ChatFormatting.BOLD));
			for (Tip t : tips) {
				block.append("\n\n").append(Ui.text(t.title(), ChatFormatting.WHITE));
				if (!data.seen.contains(t.key())) block.append(Ui.text("  new", d.rgb));
				block.append("\n").append(Ui.text(String.join(" ", t.lines()), ChatFormatting.GRAY));
			}
			w.text(block);
		}
		if (!any) w.text("No tips on this server.");
		w.back(Welcome::guide).show(p);
	}
}
