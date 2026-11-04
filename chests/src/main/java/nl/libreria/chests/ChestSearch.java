package nl.libreria.chests;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import nl.libreria.chests.gui.Locate;
import net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket;
import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.world.inventory.RecipeBookType;
import nl.libreria.core.Libreria;
import nl.libreria.core.Pack;
import nl.libreria.core.Ui;

/**
 * Searching your chests in a real window: a text field and buttons (a Minecraft "dialog", which servers may send to
 * vanilla clients). Opens from the search button in your inventory (where the recipe book button is; the pack draws
 * a magnifying glass there), from the Libreria menu, or with /chest find. Results show each item with its icon, how
 * many you have and where; one click shows the way.
 */
public final class ChestSearch {
	private static final Map<UUID, String> LAST = new HashMap<>();
	private static final int MAX_RESULTS = 10;

	private ChestSearch() {
	}

	/** The search window with a text field (filled with your last search). */
	public static void open(ServerPlayer p) {
		JsonObject d = dialog("Search your chests");
		JsonArray body = new JsonArray();
		body.add(message(Component.literal("Type (part of) an item name, like ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal("diamond").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" or ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal("log").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(".").withStyle(ChatFormatting.GRAY)), 280));
		d.add("body", body);
		JsonArray inputs = new JsonArray();
		JsonObject field = new JsonObject();
		field.addProperty("type", "minecraft:text");
		field.addProperty("key", "q");
		field.add("label", text(Component.literal("Item")));
		field.addProperty("width", 280);
		field.addProperty("initial", LAST.getOrDefault(p.getUUID(), ""));
		field.addProperty("max_length", 40);
		inputs.add(field);
		d.add("inputs", inputs);
		JsonArray actions = new JsonArray();
		// "q:" so the command always gets an argument, also when the field is empty
		actions.add(button(Component.literal("Search").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD),
				Component.literal("Find it in your chests"), 136, template("chest find q:$(q)")));
		actions.add(button(Component.literal("All my items"), Component.literal("Everything in your chests, most first"), 136, command("chest")));
		d.add("actions", actions);
		d.addProperty("columns", 2);
		show(p, d);
	}

	/** /chest find q:&lt;text&gt;: one match shows the way at once, more open a list, none says so. */
	public static int find(ServerPlayer p, String raw) {
		String q = raw.startsWith("q:") ? raw.substring(2) : raw;
		q = q.trim();
		if (q.isEmpty()) {
			open(p);
			return 1;
		}
		LAST.put(p.getUUID(), q);
		List<Search.Agg> found = Search.aggregate(p, q, false);
		if (found.isEmpty()) {
			JsonObject d = dialog("Nothing found");
			JsonArray body = new JsonArray();
			body.add(message(Component.literal("None of your chests has ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal("“" + q + "”").withStyle(ChatFormatting.WHITE))
					.append(Component.literal(". Chests you haven't opened since you joined aren't known yet.").withStyle(ChatFormatting.GRAY)), 280));
			d.add("body", body);
			JsonArray actions = new JsonArray();
			actions.add(button(Component.literal("Search again").withStyle(ChatFormatting.YELLOW), Component.literal("Try another name"), 136, command("chest find")));
			actions.add(button(Component.literal("All my items"), Component.literal("Everything in your chests"), 136, command("chest")));
			d.add("actions", actions);
			d.addProperty("columns", 2);
			show(p, d);
			return 0;
		}
		if (found.size() == 1) {
			Search.Agg a = found.getFirst();
			Locate.go(p, a.sample(), a.hits());
			return 1;
		}
		JsonObject d = dialog(found.size() + " kinds of items match “" + q + "”");
		JsonArray body = new JsonArray();
		JsonArray actions = new JsonArray();
		for (int i = 0; i < Math.min(MAX_RESULTS, found.size()); i++) {
			Search.Agg a = found.get(i);
			String id = BuiltInRegistries.ITEM.getKey(a.item()).toString();
			String name = a.sample().getHoverName().getString();
			Search.Hit near = a.nearest();
			String where = a.hits().size() == 1 ? "1 chest" : a.hits().size() + " chests";
			MutableComponent desc = Component.literal(name).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
					.append(Component.literal("\n" + String.format(Locale.US, "%,d", a.total()) + " in " + where + " · nearest "
							+ Math.round(near.distance()) + " m")
							.withStyle(ChatFormatting.GRAY));
			body.add(item(id, desc));
			actions.add(button(Component.literal("◆ " + name), Component.literal("Show the way to the nearest chest with " + name), 136,
					command("chest search " + id)));
		}
		if (found.size() > MAX_RESULTS) {
			body.add(message(Component.literal("…and " + (found.size() - MAX_RESULTS) + " more. Type more of the name to narrow it down.")
					.withStyle(ChatFormatting.GRAY), 280));
		}
		d.add("body", body);
		actions.add(button(Component.literal("Search again").withStyle(ChatFormatting.YELLOW), Component.literal("Try another name"), 136, command("chest find")));
		d.add("actions", actions);
		d.addProperty("columns", 2);
		show(p, d);
		return 1;
	}

	// ---------------------------------------------------------------- building the dialog (its JSON format)

	private static JsonObject dialog(String title) {
		JsonObject d = new JsonObject();
		d.addProperty("type", "minecraft:multi_action");
		d.add("title", text(Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		d.add("external_title", text(Component.literal("Search chests")));
		d.addProperty("can_close_with_escape", true);
		d.addProperty("pause", false);
		d.addProperty("after_action", "close");
		return d;
	}

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

	private static JsonObject item(String id, Component description) {
		JsonObject o = new JsonObject();
		o.addProperty("type", "minecraft:item");
		JsonObject stack = new JsonObject();
		stack.addProperty("id", id);
		stack.addProperty("count", 1);
		o.add("item", stack);
		JsonObject desc = new JsonObject();
		desc.add("contents", text(description));
		desc.addProperty("width", 240);
		o.add("description", desc);
		o.addProperty("show_decorations", true);
		o.addProperty("show_tooltip", true);
		return o;
	}

	private static JsonObject button(Component label, Component tooltip, int width, JsonObject action) {
		JsonObject o = new JsonObject();
		o.add("label", text(label));
		o.add("tooltip", text(tooltip));
		o.addProperty("width", width);
		o.add("action", action);
		return o;
	}

	private static JsonObject command(String cmd) {
		JsonObject o = new JsonObject();
		o.addProperty("type", "minecraft:run_command");
		o.addProperty("command", cmd);
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
			Libreria.LOG.error("Chests: could not build the search window: {}", json, e);
			p.sendSystemMessage(Ui.prefix("Chests").append(Ui.text("The search window couldn't open; use /chest instead.", ChatFormatting.RED)));
		}
	}

	static String lower(String s) {
		return s.toLowerCase(Locale.ROOT);
	}

	/**
	 * The recipe book button in your inventory was clicked (from the mixin). With the pack it is the search button:
	 * open the search and keep the recipe book closed. Returns true when it was handled.
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
