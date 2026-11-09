package nl.libreria.info;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import nl.libreria.core.Domain;
import nl.libreria.core.dialog.Dialogs;
import nl.libreria.core.gui.Book;

/**
 * The lore book (Progress › Lore book, /lore), in the game's own book screen: the contents (every story you found a part
 * of, a link each), a book per story (what it is, its parts, a page per part, the ones you haven't found as "???"), and
 * a book of everything you found yourself, newest first, with its text, where and when.
 */
final class LoreBook {
	private LoreBook() {
	}

	static void init() {
		// The links in the book
		Dialogs.on("lore_open", (p, v) -> open(p));
		Dialogs.on("lore_finds", (p, v) -> finds(p));
		Dialogs.on("lore_story", (p, v) -> story(p, v.text("story", ""), parts(v.text("shown", ""))));
		InfoMod.MODULE.hubEntry(Domain.PROGRESS, 85, Items.WRITTEN_BOOK, "Lore book", LoreBook::open,
				"Every story you found a part of, and everything you found, to read again.");
		InfoMod.MODULE.commands(Domain.PROGRESS, "/lore – the lore book: stories and everything you found");
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
				dispatcher.register(Commands.literal("lore").requires(InfoMod.MODULE.available()).executes(ctx -> {
					open(ctx.getSource().getPlayerOrException());
					return 1;
				})));
	}

	private static Style st(int rgb) {
		return Book.style(rgb);
	}

	/** The contents: how far you got, a link per story you know a part of, your finds, how it works (its own page). */
	static void open(ServerPlayer p) {
		// (written twice: the second time the link to "how it works" knows its page)
		int[] how = {1};
		contents(p, how);
		contents(p, how).open(p, "Lore book");
	}

	/** {@code how}: in, the page "how it works" is on; out, where it ended up. */
	static Book contents(ServerPlayer p, int[] how) {
		int howPage = how[0];
		Found.Seen seen = Found.SEEN.get(p);
		int known = 0, complete = 0;
		for (var e : Found.STORIES.entrySet()) {
			Set<Integer> got = seen.parts.get(e.getKey());
			if (got == null || got.isEmpty()) continue;
			known++;
			if (got.size() >= e.getValue().parts()) complete++;
		}
		int total = Found.STORIES.size();
		Book b = Book.create();
		b.centred("Lore book", st(Book.TITLE).withBold(true));
		b.centred(known + " of " + total + " stories" + (complete > 0 ? ", " + complete + " complete" : ""), st(Book.MUTED));
		b.centred(seen.finds.size() == 1 ? "1 thing found" : seen.finds.size() + " things found", st(Book.MUTED));
		b.gap();
		b.line(Component.literal("» Your finds").withStyle(Book.action(st(Book.BUTTON), "lore_finds", new CompoundTag())));
		b.line(Component.literal("» How it works").withStyle(Book.toPage(st(Book.BUTTON), Math.max(1, howPage))));
		b.line(Component.literal("» Show them off").withStyle(Book.toPage(st(Book.BUTTON), Math.max(1, howPage + 1))));
		b.gap();
		for (var e : Found.STORIES.entrySet()) {
			Set<Integer> got = seen.parts.get(e.getKey());
			if (got == null || got.isEmpty()) continue;
			int parts = e.getValue().parts();
			CompoundTag which = new CompoundTag();
			which.putString("story", e.getKey());
			Style link = Book.action(st(Book.LINK), "lore_story", which);
			List<String> title = Book.wrap("✦ " + e.getValue().title(), link);
			b.keep(title.size() + 1);
			for (int i = 0; i < title.size(); i++) b.line(Component.literal((i > 0 ? "   " : "") + title.get(i)).withStyle(link));
			b.line(got.size() >= parts ? Component.literal("   complete ✔").withStyle(Book.action(st(Book.ACCENT), "lore_story", which))
					: Component.literal("   " + got.size() + " of " + parts + " parts").withStyle(Book.action(st(Book.MUTED), "lore_story", which)));
		}
		if (known < total) {
			b.gap();
			b.text(known == 0 ? total + " stories wait to be found. Their parts are out there, on things in chests, in the sea and on monsters."
					: (total - known) + " more stories wait to be found.", st(Book.MUTED));
		}
		// How it works
		b.newPage();
		how[0] = b.pageNumber();
		b.centred("How it works", st(Book.TITLE).withBold(true));
		b.gap();
		b.text("Gear you find in chests, vaults, the sea, the sand, from piglins or on monsters has a story of its own.", st(Book.INK));
		b.gap();
		b.text("Some are a part of a longer story. Hold every part once (trading counts) and you know it all.", st(Book.INK));
		b.gap();
		b.text("Everything you found stays in this book, even when the thing is long gone.", st(Book.INK));
		b.gap();
		b.line(Component.literal("« Contents").withStyle(Book.toPage(st(Book.BUTTON), 1)));
		// Showing them off
		b.newPage();
		b.centred("Show them off", st(Book.TITLE).withBold(true));
		b.gap();
		b.text("A mannequin wears a whole story: armour, a thing in each hand, two on its back, two at its hips, two at its feet.",
				st(Book.INK));
		b.text("Craft: an armour stand and a book.", st(Book.MUTED));
		b.gap();
		b.text("A story wall hangs on a wall, two spots a piece; pieces of one wood side by side join, up to eight spots.", st(Book.INK));
		b.text("Craft: two planks, an item frame, a book and a gold nugget.", st(Book.MUTED));
		b.gap();
		b.text("Right-click them with a part. Missing parts show as dark shapes on a wall.", st(Book.INK));
		b.gap();
		b.line(Component.literal("« Contents").withStyle(Book.toPage(st(Book.BUTTON), 1)));
		return b;
	}

	static void help(ServerPlayer p) {
		open(p);
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

	/** One story: what it is, its parts (a link to each), then a page per part, the missing ones too. */
	static void story(ServerPlayer p, String story) {
		story(p, story, Set.of());
	}

	/**
	 * One story, with these parts readable too ({@code shown}: what a mannequin or story wall holds, whether or not you
	 * found them yourself).
	 */
	static void story(ServerPlayer p, String story, Set<Integer> shown) {
		Found.Seen seen = Found.SEEN.get(p);
		Set<Integer> got = new java.util.HashSet<>(seen.parts.getOrDefault(story, Set.of()));
		got.addAll(shown);
		List<Found.Text> parts = new ArrayList<>();
		for (Found.Text t : Found.PIECES) if (story.equals(t.story())) parts.add(t);
		if (parts.isEmpty() || got.isEmpty()) {
			open(p);
			return;
		}
		// (written twice: the second time the list knows on which page each part starts)
		int[] at = new int[parts.size()];
		storyBook(seen, story, parts, got, at, !shown.isEmpty());
		storyBook(seen, story, parts, got, at, !shown.isEmpty()).open(p, parts.get(0).title());
	}

	/** An introduction: whole sentences to a page; the first page under the title if all of it fits there. */
	static void introPages(Book b, String intro, Style style) {
		List<String> all = Book.wrap(intro, style);
		if (all.size() + 1 <= b.room()) {
			b.gap();
			for (String l : all) b.line(Component.literal(l).withStyle(style));
			return;
		}
		b.newPage();
		List<String> sentences = new ArrayList<>();
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("[^.!?]+[.!?]+[\"')]*\\s*|[^.!?]+$").matcher(intro.trim());
		while (m.find()) if (!m.group().isBlank()) sentences.add(m.group().trim());
		StringBuilder page = new StringBuilder();
		for (String sentence : sentences) {
			String tryPage = page.isEmpty() ? sentence : page + " " + sentence;
			if (!page.isEmpty() && Book.wrap(tryPage, style).size() > Book.LINES) {
				for (String l : Book.wrap(page.toString(), style)) b.line(Component.literal(l).withStyle(style));
				b.newPage();
				page.setLength(0);
				page.append(sentence);
			} else {
				page.setLength(0);
				page.append(tryPage);
			}
		}
		for (String l : Book.wrap(page.toString(), style)) b.line(Component.literal(l).withStyle(style));
	}

	static Book storyBook(Found.Seen seen, String story, List<Found.Text> parts, Set<Integer> got, int[] at, boolean showcase) {
		Found.Text first = parts.get(0);
		Book b = Book.create();
		b.centred(first.title(), st(Book.TITLE).withBold(true));
		b.centred("a story in " + first.parts() + " parts", st(Book.MUTED));
		String intro = Found.INTROS.get(story);
		if (intro != null) {
			// What it's about first, under the title when it fits there, else on a page of its own; never a sentence
			// broken over two pages. Then the parts.
			introPages(b, intro, st(Book.INK).withItalic(true));
			b.newPage();
		}
		String count = showcase ? (got.size() >= first.parts() ? "All of them are here!" : got.size() + " of them here.")
				: got.size() >= first.parts() ? "You have all of them!" : "You have " + got.size() + " of them.";
		b.centred(count, st(got.size() >= first.parts() ? Book.ACCENT : Book.INK));
		b.gap();
		for (int i = 0; i < parts.size(); i++) {
			Found.Text t = parts.get(i);
			boolean have = got.contains(t.part());
			Style link = Book.toPage(st(have ? Book.LINK : Book.MUTED), Math.max(1, at[i]));
			List<String> name = Book.wrap(t.part() + "  " + (have ? t.name() : "???"), link);
			for (int k = 0; k < name.size(); k++) b.line(Component.literal((k > 0 ? "    " : "") + name.get(k)).withStyle(link));
		}
		b.gap();
		b.line(Component.literal("« Contents").withStyle(Book.action(st(Book.BUTTON), "lore_open", new CompoundTag())));
		for (int i = 0; i < parts.size(); i++) {
			Found.Text t = parts.get(i);
			b.newPage();
			at[i] = b.pageNumber();
			if (got.contains(t.part())) {
				// A part on one page: the empty lines go first when it's long
				List<Component> head = new ArrayList<>(), body = new ArrayList<>(), foot = new ArrayList<>();
				head.add(Component.literal("Part " + t.part()).withStyle(st(Book.ACCENT).withBold(true)));
				for (String l : Book.wrap(t.name(), st(Book.ACCENT).withBold(true))) head.add(Component.literal(l).withStyle(st(Book.ACCENT).withBold(true)));
				for (String l : Book.wrap(t.text().replace("{item}", "thing"), st(Book.INK))) body.add(Component.literal(l).withStyle(st(Book.INK)));
				String[] info = seen.partInfo.getOrDefault(story + ":" + t.part(), "").split("\\|", -1);
				if (info.length == 3 && !info[0].isEmpty()) {
					String where = info[0] + (info[1].equals("0") ? "" : " · day " + info[1]) + (info[2].isEmpty() ? "" : " · " + info[2]);
					for (String l : Book.wrap(where, st(Book.MUTED))) foot.add(Component.literal(l).withStyle(st(Book.MUTED)));
				}
				int lines = head.size() + body.size() + foot.size();
				head.forEach(b::line);
				if (lines + 2 <= Book.LINES) b.gap();
				body.forEach(b::line);
				if (!foot.isEmpty() && lines + 1 <= Book.LINES) b.gap();
				foot.forEach(b::line);
			} else {
				String kinds = String.join(",", t.kinds());
				b.line(Component.literal("Part " + t.part()).withStyle(st(Book.MUTED).withBold(true)));
				b.line(Component.literal("???").withStyle(st(Book.MUTED).withBold(true)));
				b.gap();
				b.text("Not found yet.", st(Book.INK));
				b.gap();
				b.text(Lore.cap(noun(kinds)) + ", " + hint(t.where()) + ".", st(Book.INK));
			}
		}
		return b;
	}

	/** Everything you found, newest first: what it is, its story (and of which longer story it's a part), where and when. */
	static void finds(ServerPlayer p, int page) {
		finds(p);
	}

	static void finds(ServerPlayer p) {
		List<Found.Find> all = Found.SEEN.get(p).finds;
		Book b = Book.create();
		b.centred("Your finds", st(Book.TITLE).withBold(true));
		b.centred(all.isEmpty() ? "nothing yet" : (all.size() == 1 ? "1 thing" : all.size() + " things") + ", newest first", st(Book.MUTED));
		b.line(Book.centre(Component.literal("« Contents").withStyle(Book.action(st(Book.BUTTON), "lore_open", new CompoundTag()))));
		b.gap();
		if (all.isEmpty()) {
			b.text("Gear from chests, vaults, the sea, the sand, piglins and monsters gets a story when you take it. It shows up here.", st(Book.INK));
		}
		for (int i = all.size() - 1; i >= 0 && b.pageNumber() < Book.MAX_PAGES; i--) {
			Found.Find f = all.get(i);
			Item item = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(f.item)).orElse(Items.WRITTEN_BOOK);
			ItemStack stack = new ItemStack(item);
			Found.Text t = Found.byId(f.id);
			String kind = Found.kindOf(stack);
			String text = t == null ? "(a text this server doesn't have any more)" : t.text().replace("{item}", kind == null ? "thing" : Found.noun(kind, stack));
			String name = f.name.isEmpty() ? stack.getHoverName().getString() : f.name;
			List<String> lines = Book.wrap(text, st(Book.INK));
			b.keep(Math.min(Book.LINES, lines.size() + 3 + (t != null && t.story() != null ? 1 : 0)));
			b.text(name, st(f.name.isEmpty() ? Book.INK : Book.ACCENT).withBold(true));
			if (t != null && t.story() != null) {
				CompoundTag which = new CompoundTag();
				which.putString("story", t.story());
				b.text("✦ " + t.title() + ", part " + t.part(), Book.action(st(Book.LINK), "lore_story", which));
			}
			for (String l : lines) b.line(Component.literal(l).withStyle(st(Book.INK)));
			b.text(f.where + ", day " + f.day, st(Book.MUTED));
			b.gap();
		}
		b.open(p, "Your finds");
	}

	/** For the self-test: how many stories this player knows a part of. */
	static int known(ServerPlayer p) {
		int n = 0;
		for (Map.Entry<String, Set<Integer>> e : Found.SEEN.get(p).parts.entrySet()) if (!e.getValue().isEmpty()) n++;
		return n;
	}

	/** "2,5,7" → {2, 5, 7}. */
	static Set<Integer> parts(String list) {
		Set<Integer> out = new java.util.HashSet<>();
		for (String x : list.split(",")) {
			try {
				if (!x.isBlank()) out.add(Integer.parseInt(x.trim()));
			} catch (NumberFormatException ignored) {
				// not a number
			}
		}
		return out;
	}

	/**
	 * The stories of what a mannequin or story wall holds: one story, its book (the parts it holds readable); several, a
	 * short book with a link to each.
	 */
	static void showcase(ServerPlayer p, String what, Map<String, Set<Integer>> stories) {
		if (stories.size() == 1) {
			var e = stories.entrySet().iterator().next();
			story(p, e.getKey(), e.getValue());
			return;
		}
		Book b = Book.create();
		b.centred("On this " + what, st(Book.TITLE).withBold(true));
		b.centred(stories.size() + " stories", st(Book.MUTED));
		b.gap();
		for (var e : stories.entrySet()) {
			Found.Text last = Found.STORIES.get(e.getKey());
			if (last == null) continue;
			CompoundTag which = new CompoundTag();
			which.putString("story", e.getKey());
			which.putString("shown", String.join(",", e.getValue().stream().sorted().map(String::valueOf).toList()));
			b.text(last.title(), Book.action(st(Book.LINK), "lore_story", which));
			b.text("   " + e.getValue().size() + " of " + last.parts() + " parts", st(Book.MUTED));
		}
		b.open(p, "On this " + what);
	}
}
