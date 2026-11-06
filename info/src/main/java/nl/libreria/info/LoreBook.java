package nl.libreria.info;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.Domain;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Window;

/**
 * The lore book (Items › Lore book, /lore): every story you found a part of, to read in order (the parts you haven't
 * found as "???"), and everything you found yourself, newest first, with its text, where and when.
 */
final class LoreBook {
	static final int PER_PAGE = 8;

	private LoreBook() {
	}

	static void init() {
		InfoMod.MODULE.hubEntry(Domain.ITEMS, 85, Items.WRITTEN_BOOK, "Lore book", LoreBook::open,
				"Every story you found a part of, and everything you found, to read again.");
		InfoMod.MODULE.commands(Domain.ITEMS, "/lore – the lore book: stories and everything you found");
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
				dispatcher.register(Commands.literal("lore").executes(ctx -> {
					open(ctx.getSource().getPlayerOrException());
					return 1;
				})));
	}

	private static MutableComponent t(String s, int rgb) {
		return Component.literal(s).withStyle(Style.EMPTY.withColor(rgb).withItalic(false));
	}

	/** The index: your finds, then the stories you know a part of. */
	static void open(ServerPlayer p) {
		Found.Seen seen = Found.SEEN.get(p);
		Window w = Window.of(Ui.text("Lore book", ChatFormatting.WHITE));
		int known = 0, complete = 0;
		for (var e : Found.STORIES.entrySet()) {
			Set<Integer> got = seen.parts.get(e.getKey());
			if (got == null || got.isEmpty()) continue;
			known++;
			if (got.size() >= e.getValue().parts()) complete++;
		}
		int total = Found.STORIES.size();
		w.text(Ui.text("Every story you found a part of, and everything you found. Click a story to read it.", ChatFormatting.GRAY));
		w.text(t(known + " of " + total + " stories known · " + complete + " complete · " + seen.finds.size() + " finds", 0x8A8A8A));
		w.button(Ui.text("Your finds (" + seen.finds.size() + ")", ChatFormatting.WHITE), Ui.text("Everything you found, newest first", ChatFormatting.GRAY),
				(pl, v) -> finds(pl, 0));
		w.button(Ui.text("How it works", ChatFormatting.GRAY), null, (pl, v) -> help(pl));
		for (var e : Found.STORIES.entrySet()) {
			Set<Integer> got = seen.parts.get(e.getKey());
			if (got == null || got.isEmpty()) continue;
			int parts = e.getValue().parts();
			boolean done = got.size() >= parts;
			String story = e.getKey();
			w.button(Component.empty().append(Ui.text(e.getValue().title(), done ? ChatFormatting.GOLD : ChatFormatting.WHITE))
					.append(Ui.text("  " + got.size() + "/" + parts, done ? ChatFormatting.GOLD : ChatFormatting.GRAY)), null, (pl, v) -> story(pl, story));
		}
		if (known < total) {
			w.text(t((total - known) + (known == 0 ? " stories wait to be found. Their parts are out there, on things in chests, in the sea and on monsters."
					: " more stories you haven't found a part of yet."), 0x8A8A8A));
		}
		w.done();
		w.show(p);
	}

	static void help(ServerPlayer p) {
		Window w = Window.of(Ui.text("Lore book", ChatFormatting.WHITE));
		w.text(Ui.text("Gear you find in chests, vaults, the sea, the sand, from piglins or on monsters gets a text of its own when you first "
				+ "take it: never the same one twice on this server.", ChatFormatting.GRAY));
		w.text(Ui.text("Some of those texts are a part of a longer story, about a person, a place or a whole set of gear. Such a part has a "
				+ "name, and its tooltip says which story and which part it is. Hold every part of a story once (trading counts) and you "
				+ "know the whole story; that's an achievement too.", ChatFormatting.GRAY));
		w.text(Ui.text("Everything you found yourself stays in this book, even when the item is long gone.", ChatFormatting.GRAY));
		w.back(LoreBook::open);
		w.show(p);
	}

	/** An item that stands for a kind of thing ("a bow, somewhere"). */
	static ItemStack icon(String kinds) {
		String k = kinds.split(",")[0].trim();
		Item it = switch (k) {
			case "sword", "weapon" -> Items.IRON_SWORD;
			case "axe", "tool" -> Items.IRON_AXE;
			case "bow" -> Items.BOW;
			case "crossbow" -> Items.CROSSBOW;
			case "trident" -> Items.TRIDENT;
			case "mace" -> Items.MACE;
			case "spear" -> Items.IRON_SPEAR;
			case "helmet", "armour" -> Items.IRON_HELMET;
			case "chestplate" -> Items.IRON_CHESTPLATE;
			case "leggings" -> Items.IRON_LEGGINGS;
			case "boots" -> Items.IRON_BOOTS;
			case "shield" -> Items.SHIELD;
			case "elytra" -> Items.ELYTRA;
			case "pickaxe" -> Items.IRON_PICKAXE;
			case "shovel" -> Items.IRON_SHOVEL;
			case "hoe" -> Items.IRON_HOE;
			case "shears" -> Items.SHEARS;
			case "rod" -> Items.FISHING_ROD;
			case "brush" -> Items.BRUSH;
			case "flint" -> Items.FLINT_AND_STEEL;
			case "stick" -> Items.CARROT_ON_A_STICK;
			default -> Items.WRITTEN_BOOK;
		};
		return new ItemStack(it);
	}

	static String noun(String kinds) {
		String k = kinds.split(",")[0].trim();
		return switch (k) {
			case "*" -> "something";
			case "weapon" -> "a weapon";
			case "armour" -> "a piece of armour";
			case "tool" -> "a tool";
			case "rod" -> "a fishing rod";
			case "flint" -> "a flint and steel";
			case "stick" -> "a stick with something on it";
			case "leggings" -> "leggings";
			case "boots" -> "boots";
			case "shears" -> "shears";
			case "axe", "elytra" -> "an " + k;
			default -> "a " + k;
		};
	}

	/** Where a part waits to be found: "in the Nether", "in an ancient city"… */
	static String hint(String where) {
		return switch (where) {
			case "" -> "somewhere";
			case "overworld" -> "somewhere in the overworld";
			case "nether" -> "somewhere in the Nether";
			case "end" -> "somewhere in the End";
			case "fishing" -> "from the water";
			case "mob" -> "on a monster";
			case "barter" -> "from a piglin";
			case "archaeology" -> "in the sand";
			case "ancient_city" -> "in an ancient city";
			case "end_city" -> "in an End city";
			case "bastion" -> "in a bastion";
			case "fortress" -> "in a Nether fortress";
			case "shipwreck" -> "in a shipwreck";
			case "stronghold" -> "in a stronghold";
			case "desert" -> "in a desert pyramid";
			case "trial" -> "in a trial chamber";
			default -> "somewhere";
		};
	}

	/** One story, its parts in order. */
	static void story(ServerPlayer p, String story) {
		Found.Seen seen = Found.SEEN.get(p);
		Set<Integer> got = seen.parts.getOrDefault(story, Set.of());
		List<Found.Text> parts = new ArrayList<>();
		for (Found.Text t : Found.PIECES) if (story.equals(t.story())) parts.add(t);
		if (parts.isEmpty() || got.isEmpty()) {
			open(p);
			return;
		}
		Found.Text first = parts.get(0);
		Window w = Window.of(Component.empty().append(Ui.text(first.title(), ChatFormatting.WHITE))
				.append(Ui.text(" · " + got.size() + " of " + first.parts() + " parts", ChatFormatting.GRAY)));
		for (Found.Text t : parts) {
			String kinds = String.join(",", t.kinds());
			if (got.contains(t.part())) {
				String info = seen.partInfo.getOrDefault(story + ":" + t.part(), "");
				String[] i = info.split("\\|", -1);
				MutableComponent d = Component.empty().append(t("Part " + t.part() + " · " + t.name(), Found.HEAD)).append("\n")
						.append(Component.literal(t.text().replace("{item}", "thing")).withStyle(Style.EMPTY.withColor(Found.TEXT).withItalic(true)));
				if (i.length == 3 && !i[0].isEmpty()) {
					d.append("\n").append(t(i[0] + (i[1].equals("0") ? "" : " · day " + i[1]) + (i[2].isEmpty() ? "" : " · " + i[2]), Found.WHERE));
				}
				w.item(icon(kinds), d);
			} else {
				w.item(icon(kinds), Component.empty().append(t("Part " + t.part() + " · ???", 0x6E6E6E)).append("\n")
						.append(t(Lore.cap(noun(kinds)) + ", " + hint(t.where()) + ".", 0x6E6E6E)));
			}
		}
		w.back(LoreBook::open);
		w.show(p);
	}

	/** Everything you found, newest first, a page at a time. */
	static void finds(ServerPlayer p, int page) {
		List<Found.Find> all = Found.SEEN.get(p).finds;
		int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
		int at = Math.max(0, Math.min(page, pages - 1));
		Window w = Window.of(Component.empty().append(Ui.text("Your finds", ChatFormatting.WHITE))
				.append(Ui.text(" · page " + (at + 1) + " of " + pages, ChatFormatting.GRAY)));
		if (all.isEmpty()) {
			w.text(Ui.text("Nothing yet. Gear from chests, vaults, the sea, the sand, piglins and monsters gets a story when you take it.",
					ChatFormatting.GRAY));
		}
		for (int i = 0; i < PER_PAGE; i++) {
			int idx = all.size() - 1 - (at * PER_PAGE + i);
			if (idx < 0) break;
			Found.Find f = all.get(idx);
			Item item = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(f.item)).orElse(Items.WRITTEN_BOOK);
			ItemStack stack = new ItemStack(item);
			Found.Text t = Found.byId(f.id);
			String kind = Found.kindOf(stack);
			String text = t == null ? "(a text this server doesn't have any more)" : t.text().replace("{item}", kind == null ? "thing" : Found.noun(kind, stack));
			String name = f.name.isEmpty() ? stack.getHoverName().getString() : f.name;
			MutableComponent d = Component.empty().append(t(name, f.name.isEmpty() ? 0xFFFFFF : Found.HEAD))
					.append(t("  " + f.where + " · day " + f.day, Found.WHERE));
			if (t != null && t.story() != null) d.append("\n").append(t("✦ " + t.title() + " · part " + t.part() + " of " + t.parts(), Found.HEAD));
			d.append("\n").append(Component.literal(text).withStyle(Style.EMPTY.withColor(Found.TEXT).withItalic(true)));
			w.item(stack, d);
		}
		if (pages > 1) {
			w.button(Ui.text("◀ Newer", at > 0 ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY), null, (pl, v) -> finds(pl, at - 1));
			w.button(Ui.text("Older ▶", at < pages - 1 ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY), null, (pl, v) -> finds(pl, at + 1));
		}
		w.back(LoreBook::open);
		w.show(p);
	}

	/** For the self-test: how many stories this player knows a part of. */
	static int known(ServerPlayer p) {
		int n = 0;
		for (Map.Entry<String, Set<Integer>> e : Found.SEEN.get(p).parts.entrySet()) if (!e.getValue().isEmpty()) n++;
		return n;
	}
}
