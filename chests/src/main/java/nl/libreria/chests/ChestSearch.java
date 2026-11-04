package nl.libreria.chests;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import nl.libreria.chests.gui.Locate;
import nl.libreria.core.Libreria;
import nl.libreria.core.Pack;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Glyphs;

/**
 * "Your items": everything you carry and everything in your chests in one scrollable window (a Minecraft dialog,
 * which servers may send to vanilla clients: the world stays visible, blurred, behind it). Each item is a button with
 * its picture, name and how many you have; hover for where it is, click to follow the beam to the nearest chest. A
 * search field on top narrows the list down. Opens from the search button in your inventory (the magnifying glass),
 * the Libreria menu, /chest or /chest find.
 */
public final class ChestSearch {
	private enum Sort {
		MOST("Most first"), NAME("A–Z"), NEAREST("Nearest first");

		final String label;

		Sort(String label) {
			this.label = label;
		}

		Sort next() {
			return values()[(ordinal() + 1) % values().length];
		}

		static Sort of(String s) {
			for (Sort v : values()) {
				if (v.name().equalsIgnoreCase(s)) return v;
			}
			return MOST;
		}
	}

	/** One kind of item: how many you carry, how many are in chests and where. */
	private record Row(Item item, ItemStack sample, int carried, long stored, List<Search.Hit> hits) {
		long total() {
			return this.carried + this.stored;
		}

		double nearest() {
			if (this.carried > 0) return 0;
			return this.hits.isEmpty() ? Double.MAX_VALUE : this.hits.getFirst().distance();
		}
	}

	private static final Map<UUID, Sort> SORT = new HashMap<>();
	private static final int PER_PAGE = 60;
	/** Two columns of buttons; the label of an item button is laid out to exactly this width (pack only). */
	private static final int BUTTON_W = 150, LABEL_W = 138, ICON_COLUMN = 19;

	private ChestSearch() {
	}

	/** The window with all your items. */
	public static void open(ServerPlayer p) {
		browse(p, "", SORT.getOrDefault(p.getUUID(), Sort.MOST), 0);
	}

	/** /chest find &lt;text&gt; from chat: exactly one kind in your chests shows the way at once, otherwise the list. */
	public static int find(ServerPlayer p, String raw) {
		String q = raw.startsWith("q:") ? raw.substring(2) : raw;
		q = q.trim();
		if (!q.isEmpty()) {
			List<Row> rows = filter(rows(p), q);
			if (rows.size() == 1 && !rows.getFirst().hits().isEmpty()) {
				Row r = rows.getFirst();
				Locate.go(p, r.sample(), r.hits());
				return 1;
			}
		}
		browse(p, q, SORT.getOrDefault(p.getUUID(), Sort.MOST), 0);
		return 1;
	}

	/**
	 * /chest browse [s:&lt;sort&gt;] [p:&lt;page&gt;] [pick:&lt;item&gt;] [q:&lt;text…&gt;] – what the window's buttons
	 * run. q comes last, so it may contain spaces.
	 */
	public static int command(ServerPlayer p, String args) {
		String q = "";
		int at = args.startsWith("q:") ? 0 : args.indexOf(" q:") + 1;
		if (at > 0 || args.startsWith("q:")) {
			q = args.substring(at + 2).trim();
			args = args.substring(0, at);
		}
		Sort sort = SORT.getOrDefault(p.getUUID(), Sort.MOST);
		int page = 0;
		String pick = null;
		for (String t : args.trim().split("\\s+")) {
			if (t.startsWith("s:")) sort = Sort.of(t.substring(2));
			else if (t.startsWith("p:")) page = parse(t.substring(2));
			else if (t.startsWith("pick:")) pick = t.substring(5);
		}
		SORT.put(p.getUUID(), sort);
		if (pick != null) {
			Item item = Search.itemById(pick);
			List<Search.Hit> hits = Search.find(p, item);
			if (!hits.isEmpty()) {
				p.closeDialog();
				Locate.go(p, new ItemStack(item), hits);
				return 1;
			}
			// Only on you: nothing to show the way to, the window stays
			Ui.sound(p, SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 0.7f);
		}
		browse(p, q, sort, page);
		return 1;
	}

	private static int parse(String s) {
		try {
			return Math.max(0, Integer.parseInt(s));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	// ---------------------------------------------------------------- the list

	/** Everything you carry (hotbar, inventory, off hand) and everything in your known chests, per kind of item. */
	private static List<Row> rows(ServerPlayer p) {
		Map<Item, Integer> carried = new LinkedHashMap<>();
		Inventory inv = p.getInventory();
		for (int i = 0; i < 36; i++) add(carried, inv.getItem(i));
		add(carried, p.getOffhandItem());
		Map<Item, Search.Agg> stored = new LinkedHashMap<>();
		for (Search.Agg a : Search.aggregate(p, "", false)) stored.put(a.item(), a);
		Set<Item> all = new HashSet<>(carried.keySet());
		all.addAll(stored.keySet());
		List<Row> out = new ArrayList<>();
		for (Item item : all) {
			Search.Agg a = stored.get(item);
			out.add(new Row(item, new ItemStack(item), carried.getOrDefault(item, 0), a == null ? 0 : a.total(), a == null ? List.of() : a.hits()));
		}
		return out;
	}

	private static void add(Map<Item, Integer> into, ItemStack s) {
		if (!s.isEmpty()) into.merge(s.getItem(), s.getCount(), Integer::sum);
	}

	private static List<Row> filter(List<Row> rows, String q) {
		String f = q.trim().toLowerCase(Locale.ROOT);
		if (f.isEmpty()) return rows;
		List<Row> out = new ArrayList<>();
		for (Row r : rows) {
			if (Search.matches(r.item(), f)) out.add(r);
		}
		return out;
	}

	private static void sort(List<Row> rows, Sort sort) {
		Comparator<Row> byName = Comparator.comparing(r -> r.sample().getHoverName().getString().toLowerCase(Locale.ROOT));
		switch (sort) {
			case MOST -> rows.sort(Comparator.comparingLong(Row::total).reversed().thenComparing(byName));
			case NAME -> rows.sort(byName);
			case NEAREST -> rows.sort(Comparator.comparingDouble(Row::nearest).thenComparing(byName));
		}
	}

	// ---------------------------------------------------------------- the window

	private static void browse(ServerPlayer p, String q, Sort sort, int page) {
		boolean pack = Pack.has(p);
		List<Row> everything = rows(p);
		List<Row> rows = new ArrayList<>(filter(everything, q));
		sort(rows, sort);
		int pages = Math.max(1, (rows.size() + PER_PAGE - 1) / PER_PAGE);
		page = Math.min(page, pages - 1);

		JsonObject d = new JsonObject();
		d.addProperty("type", "minecraft:multi_action");
		d.add("title", text(Component.literal("◆ Your items ◆").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		d.add("external_title", text(Component.literal("Your items")));
		d.addProperty("can_close_with_escape", true);
		d.addProperty("pause", false);
		// The server answers every button with a new window (or closes it), so this one stays until then: no flicker
		d.addProperty("after_action", "none");

		JsonArray body = new JsonArray();
		body.add(message(summary(everything, rows, q), 300));
		d.add("body", body);

		JsonArray inputs = new JsonArray();
		JsonObject field = new JsonObject();
		field.addProperty("type", "minecraft:text");
		field.addProperty("key", "q");
		field.add("label", text(Component.literal("Search").withStyle(ChatFormatting.GRAY)));
		field.addProperty("width", 302);
		field.addProperty("initial", q);
		field.addProperty("max_length", 40);
		inputs.add(field);
		d.add("inputs", inputs);

		String keep = "s:" + sort.name().toLowerCase(Locale.ROOT);
		JsonArray actions = new JsonArray();
		actions.add(button(Component.literal("Search").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
				Component.literal("Show the items whose name has what you typed.\nEmpty = everything."), template("chest browse " + keep + " q:$(q)")));
		actions.add(button(Component.literal("⇅ " + sort.label).withStyle(ChatFormatting.AQUA),
				Component.literal("Click: sort " + sort.next().label.toLowerCase(Locale.ROOT)),
				command("chest browse s:" + sort.next().name().toLowerCase(Locale.ROOT) + " q:" + q)));
		for (int i = page * PER_PAGE; i < Math.min(rows.size(), (page + 1) * PER_PAGE); i++) {
			Row r = rows.get(i);
			String id = BuiltInRegistries.ITEM.getKey(r.item()).toString();
			actions.add(button(label(r, pack), tooltip(r), command("chest browse pick:" + id + " " + keep + " p:" + page + " q:" + q)));
		}
		if (page > 0) {
			actions.add(button(Component.literal("◂ Previous").withStyle(ChatFormatting.YELLOW), Component.literal("Page " + page + " of " + pages),
					command("chest browse " + keep + " p:" + (page - 1) + " q:" + q)));
		}
		if (page < pages - 1) {
			actions.add(button(Component.literal("Next ▸").withStyle(ChatFormatting.YELLOW), Component.literal("Page " + (page + 2) + " of " + pages),
					command("chest browse " + keep + " p:" + (page + 1) + " q:" + q)));
		}
		d.add("actions", actions);
		d.addProperty("columns", 2);
		show(p, d);
	}

	/** "87 kinds · 312 on you · 922 in 12 chests", or how many match the search. */
	private static Component summary(List<Row> everything, List<Row> shown, String q) {
		long carried = 0, stored = 0;
		Set<Store.Entry> chests = new HashSet<>();
		for (Row r : everything) {
			carried += r.carried();
			stored += r.stored();
			for (Search.Hit h : r.hits()) chests.add(h.entry());
		}
		MutableComponent c = Component.empty();
		if (everything.isEmpty()) {
			return c.append(Component.literal("Nothing yet. Chests you open are remembered, so they show up here.").withStyle(ChatFormatting.GRAY));
		}
		if (!q.isBlank()) {
			if (shown.isEmpty()) {
				return c.append(Component.literal("Nothing matches ").withStyle(ChatFormatting.GRAY))
						.append(Component.literal("“" + q + "”").withStyle(ChatFormatting.WHITE))
						.append(Component.literal(". Chests you haven't opened since you joined aren't known yet.").withStyle(ChatFormatting.GRAY));
			}
			return c.append(Component.literal(shown.size() + " of " + everything.size()).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" kinds match ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal("“" + q + "”").withStyle(ChatFormatting.WHITE));
		}
		return c.append(Component.literal(String.valueOf(everything.size())).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" kinds  ·  ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(number(carried)).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" on you  ·  ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(number(stored)).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" in " + chests.size() + (chests.size() == 1 ? " chest" : " chests")).withStyle(ChatFormatting.GRAY));
	}

	/** Picture, name and count, laid out to exactly {@link #LABEL_W} px so the count lines up on the right. */
	private static Component label(Row r, boolean pack) {
		Component count = Component.literal(compact(r.total())).withStyle(r.stored() > 0 ? ChatFormatting.GOLD : ChatFormatting.GREEN);
		String name = r.sample().getHoverName().getString();
		ChatFormatting color = rarityColor(r.sample());
		if (!pack) {
			return Component.literal(name + " ").withStyle(color).append(count);
		}
		MutableComponent out = Component.empty();
		MutableComponent icon = Glyphs.buttonIcon(r.item(), ICON_COLUMN);
		int used = ICON_COLUMN;
		if (icon != null) {
			out.append(icon);
		} else {
			out.append(Glyphs.ui(Glyphs.space(ICON_COLUMN)));
		}
		int room = LABEL_W - used - Glyphs.width(count) - 4;
		Component shown = Component.literal(name).withStyle(color);
		if (Glyphs.width(shown) > room) {
			String cut = name;
			while (cut.length() > 1 && Glyphs.width(Component.literal(cut + "…")) > room) cut = cut.substring(0, cut.length() - 1);
			shown = Component.literal(cut.stripTrailing() + "…").withStyle(color);
		}
		out.append(shown);
		out.append(Glyphs.ui(Glyphs.space(LABEL_W - used - Glyphs.width(shown) - Glyphs.width(count))));
		out.append(count);
		return out;
	}

	private static Component tooltip(Row r) {
		MutableComponent t = Component.literal(r.sample().getHoverName().getString()).withStyle(rarityColor(r.sample()), ChatFormatting.BOLD);
		if (r.carried() > 0) {
			t.append(Component.literal("\nOn you: ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(number(r.carried())).withStyle(ChatFormatting.GREEN));
		}
		if (r.stored() > 0) {
			int n = r.hits().size();
			t.append(Component.literal("\nIn chests: ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(number(r.stored())).withStyle(ChatFormatting.GOLD))
					.append(Component.literal(" in " + n + (n == 1 ? " chest" : " chests")).withStyle(ChatFormatting.GRAY))
					.append(Component.literal("\nNearest: ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(Ui.meters(r.hits().getFirst().distance())).withStyle(ChatFormatting.WHITE))
					.append(Component.literal("\n\n▶ Click: show the way").withStyle(ChatFormatting.YELLOW));
		} else {
			t.append(Component.literal("\nNone in your chests").withStyle(ChatFormatting.DARK_GRAY));
		}
		return t;
	}

	private static ChatFormatting rarityColor(ItemStack s) {
		Rarity rarity = s.get(DataComponents.RARITY);
		if (rarity == null) return ChatFormatting.WHITE;
		return switch (rarity) {
			case UNCOMMON -> ChatFormatting.YELLOW;
			case RARE -> ChatFormatting.AQUA;
			case EPIC -> ChatFormatting.LIGHT_PURPLE;
			default -> ChatFormatting.WHITE;
		};
	}

	private static String number(long n) {
		return String.format(Locale.US, "%,d", n);
	}

	/** 64, 1,234, 12.3k, 1.2M: short enough for a button. */
	private static String compact(long n) {
		if (n < 10_000) return number(n);
		if (n < 1_000_000) return String.format(Locale.US, "%.1fk", n / 1000.0);
		return String.format(Locale.US, "%.1fM", n / 1_000_000.0);
	}

	// ---------------------------------------------------------------- building the dialog (its JSON format)

	private static JsonElement text(Component c) {
		return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, c).getOrThrow();
	}

	private static JsonObject message(Component c, int width) {
		JsonObject o = new JsonObject();
		o.addProperty("type", "minecraft:plain_message");
		o.add("contents", text(c));
		o.addProperty("width", width);
		return o;
	}

	private static JsonObject button(Component label, Component tooltip, JsonObject action) {
		JsonObject o = new JsonObject();
		o.add("label", text(label));
		o.add("tooltip", text(tooltip));
		o.addProperty("width", BUTTON_W);
		o.add("action", action);
		return o;
	}

	private static JsonObject command(String cmd) {
		JsonObject o = new JsonObject();
		o.addProperty("type", "minecraft:run_command");
		o.addProperty("command", cmd.strip());
		return o;
	}

	private static JsonObject template(String cmd) {
		JsonObject o = new JsonObject();
		o.addProperty("type", "minecraft:dynamic/run_command");
		o.addProperty("template", cmd);
		return o;
	}

	private static void show(ServerPlayer p, JsonObject json) {
		try {
			var ops = RegistryOps.create(JsonOps.INSTANCE, p.registryAccess());
			Dialog dialog = Dialog.DIRECT_CODEC.parse(ops, json).getOrThrow();
			p.openDialog(Holder.direct(dialog));
		} catch (RuntimeException e) {
			Libreria.LOG.error("Chests: could not build the items window: {}", json, e);
			p.sendSystemMessage(Ui.prefix("Chests").append(Ui.text("The items window couldn't open.", ChatFormatting.RED)));
		}
	}

	/**
	 * The recipe book button in your inventory was clicked (from the mixin). With the pack it is the search button:
	 * open the items window and keep the recipe book closed. Returns true when it was handled.
	 */
	public static boolean onRecipeBookButton(ServerPlayer p, RecipeBookType type, boolean open, boolean filtering) {
		if (type != RecipeBookType.CRAFTING || !open || p.containerMenu != p.inventoryMenu) return false;
		if (!ChestsMod.SEARCH_BUTTON.get(p) || !Pack.has(p)) return false;
		// Tell the client the book is still closed, so the button keeps working as a button
		RecipeBookSettings settings = new RecipeBookSettings();
		settings.setFiltering(RecipeBookType.CRAFTING, filtering);
		p.connection.send(new ClientboundRecipeBookSettingsPacket(settings));
		Ui.click(p);
		open(p);
		return true;
	}
}
