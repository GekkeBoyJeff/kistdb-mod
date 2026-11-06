package nl.libreria.collector.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.collector.Achievement;
import nl.libreria.collector.Achievement.Category;
import nl.libreria.collector.Catalog;
import nl.libreria.collector.CollectorMod;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Menu;

/**
 * The achievements menu: your card and title at the top, the four categories, all items, and the title picker.
 * Collections open a page that shows exactly which items you still need.
 */
public final class AchievementsGui {
	private AchievementsGui() {
	}

	public static void open(ServerPlayer player) {
		new Main(player).open();
	}

	/** One category's page (also for the screenshot test). */
	public static void openCategory(ServerPlayer player, Category c) {
		new CategoryPage(player, c, 0).open();
	}

	public static void openTitles(ServerPlayer player) {
		new Titles(player, 0, true).open();
	}

	private static Item itemOf(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
	}

	private static String num(long n) {
		return String.format(Locale.US, "%,d", n);
	}

	private static int earnedIn(CollectorMod.Data d, List<Achievement> list) {
		int n = 0;
		for (Achievement a : list) if (d.earned.containsKey(a.id())) n++;
		return n;
	}

	/** An achievement: glowing and in its rarity colour when earned, grey with a progress bar while you work on it. */
	static GuiElementBuilder icon(ServerPlayer p, CollectorMod.Data d, Achievement a) {
		GuiElementBuilder b = base(p, d, a);
		boolean done = d.earned.containsKey(a.id());
		b.addLoreLine(Component.empty());
		if (a.isCollection()) Menu.action(b, "Click: see what you have and miss");
		if (done) Menu.action(b, (a.isCollection() ? "Right-click" : "Click") + ": use as title ✦ " + a.name() + " ✦");
		return b;
	}

	/** Name, rarity and grade, what to do, and how far you are (no click hints). */
	static GuiElementBuilder base(ServerPlayer p, CollectorMod.Data d, Achievement a) {
		Long at = d.earned.get(a.id());
		boolean done = at != null;
		long cur = a.current(p, d), target = a.target();
		String grade = Catalog.gradeText(a);
		GuiElementBuilder b = new GuiElementBuilder(a.icon())
				.setName(Ui.text((done ? "✔ " : "") + a.name(), done ? a.rarity().color : ChatFormatting.GRAY).withStyle(ChatFormatting.BOLD))
				.addLoreLine(Ui.text(a.rarity().label + (grade == null ? "" : "  ·  " + grade), a.rarity().color))
				.addLoreLine(Ui.text(a.description(), ChatFormatting.GRAY))
				.addLoreLine(Component.empty())
				.addLoreLine(Component.empty().append(Ui.bar(target == 0 ? 1 : (double) cur / target, 12))
						.append(Ui.text("  " + num(cur) + " / " + num(target), done ? ChatFormatting.GREEN : ChatFormatting.WHITE)))
				.hideDefaultTooltip();
		if (done) {
			b.addLoreLine(Ui.text("Earned on " + CollectorMod.date(at), ChatFormatting.DARK_GRAY));
			b.glow();
		}
		return b;
	}

	// ---------------------------------------------------------------- main page

	private static final class Main extends Menu {
		Main(ServerPlayer p) {
			super(p, 6, "Achievements");
			CollectorMod.scanNow(p);
			CollectorMod.Data d = CollectorMod.STORE.get(p);
			List<Achievement> all = Catalog.all();

			// Your card
			GuiElementBuilder head = new GuiElementBuilder(Items.PLAYER_HEAD).setProfile(p.getGameProfile())
					.setName(title(p.getName().getString(), ChatFormatting.GOLD)).hideDefaultTooltip();
			CollectorMod.card(p).forEach(head::addLoreLine);
			this.setSlot(4, head);

			// Your title, front and centre
			Achievement current = CollectorMod.titleAchievement(d);
			GuiElementBuilder titleBtn = button(Items.NAME_TAG, "Your title", ChatFormatting.LIGHT_PURPLE,
					"Floats above your name for everyone.");
			titleBtn.addLoreLine(current == null ? Ui.text("None yet – earn an achievement!", ChatFormatting.DARK_GRAY)
					: Component.literal("✦ " + current.name() + " ✦").withStyle(current.rarity().color));
			titleBtn.addLoreLine(Ui.text(d.title.isEmpty() ? "(automatic: your rarest)" : d.title.equals("-") ? "(hidden)" : "(chosen by you)",
					ChatFormatting.DARK_GRAY));
			actions(titleBtn, "Click: choose a title");
			this.setSlot(22, titleBtn.setCallback((i, t, a, g) -> {
				Ui.click(p);
				new Titles(p, 0, false).open();
			}));

			// Categories
			Category[] cats = Category.values();
			int[] slots = row(3, cats.length);
			for (int i = 0; i < cats.length && i < slots.length; i++) {
				Category c = cats[i];
				List<Achievement> list = Catalog.of(c);
				int got = earnedIn(d, list);
				GuiElementBuilder b = button(Ui.item(c.icon), c.label, ChatFormatting.YELLOW, c.hint);
				b.addLoreLine(Component.empty().append(Ui.bar(list.isEmpty() ? 0 : (double) got / list.size(), 12))
						.append(Ui.text("  " + got + " / " + list.size(), ChatFormatting.WHITE)));
				actions(b, "Click: open");
				this.setSlot(slots[i], b.setCallback((x, t, a, g) -> {
					Ui.click(p);
					new CategoryPage(p, c, 0).open();
				}));
			}
			GuiElementBuilder items = button(Items.BOOKSHELF, "All items", ChatFormatting.AQUA,
					num(Catalog.countAll(d)) + " of " + num(Catalog.allItems().size()) + " collected",
					"An item counts once it has been", "in your inventory.");
			actions(items, "Click: see what you're still missing");
			this.setSlot(40, items.setCallback((x, t, a, g) -> {
				Ui.click(p);
				new ItemsPage(p, "All items", null, Catalog.allItems(), Filter.MISSING, 0, () -> AchievementsGui.open(p)).open();
			}));

			// Recently earned
			List<Map.Entry<String, Long>> recent = new ArrayList<>(d.earned.entrySet());
			recent.sort(Map.Entry.<String, Long>comparingByValue().reversed());
			int[] recentSlots = {10, 11, 12, 14, 15, 16};
			int n = 0;
			for (Map.Entry<String, Long> e : recent) {
				Achievement a = Catalog.get(e.getKey());
				if (a == null) continue;
				this.setSlot(recentSlots[n], icon(p, d, a).setCallback((x, t, act, g) -> {
					CollectorMod.setTitle(p, a.id());
					new Main(p).open();
				}));
				if (++n >= recentSlots.length) break;
			}
			if (n == 0) {
				this.setSlot(13, button(Items.PAPER, "Nothing earned yet", ChatFormatting.GRAY,
						"Collect items, breed animals and", "play to earn achievements."));
			} else {
				this.setSlot(13, header("yellow", "Recently earned", ChatFormatting.YELLOW));
			}

			up();
			Services.menu(Services.MENU_STATS).ifPresent(stats -> this.setSlot(navRow() + 6,
					action(button(Items.WRITABLE_BOOK, "Statistics", ChatFormatting.AQUA), "Click: open").setCallback((x, t, a, g) -> stats.accept(p))));
			closeButton();
			help("Achievements have no rewards – just bragging", "rights and a title in the color of", "their rarity:",
					"  Common, Uncommon, Rare, Epic,", "  Legendary, Mythic.", "",
					"They also appear in the vanilla", "advancements screen (L).");
			finish();
		}
	}

	// ---------------------------------------------------------------- category

	private static final class CategoryPage extends Menu {
		CategoryPage(ServerPlayer p, Category c, int page) {
			super(p, 6, "Achievements · " + c.label);
			Catalog.fresh();
			CollectorMod.Data d = CollectorMod.STORE.get(p);
			// One icon per ladder: the grade you're working on (or the top one, once you have them all)
			List<List<Achievement>> ladders = new ArrayList<>();
			java.util.Set<String> seen = new java.util.HashSet<>();
			for (Achievement a : Catalog.of(c)) {
				List<Achievement> ladder = Catalog.ladder(a);
				if (seen.add(ladder.get(0).id())) ladders.add(ladder);
			}
			// What you're closest to first; finished ladders at the end
			// (and secrets you haven't found after everything else)
			ladders.sort(Comparator.<List<Achievement>>comparingInt(l -> next(d, l) == null ? 1 : Catalog.isSecret(next(d, l)) ? 2 : 0)
					.thenComparingDouble(l -> {
						Achievement n = next(d, l);
						return n == null ? 0 : -((double) n.current(p, d) / Math.max(1, n.target()));
					}));
			int per = contentSize();
			int pages = Math.max(1, (ladders.size() + per - 1) / per);
			int pg = Math.min(page, pages - 1);
			for (int i = 0; i < per; i++) {
				int idx = pg * per + i;
				if (idx >= ladders.size()) break;
				List<Achievement> ladder = ladders.get(idx);
				Achievement shown = next(d, ladder) != null ? next(d, ladder) : ladder.get(ladder.size() - 1);
				if (Catalog.isSecret(shown) && !d.earned.containsKey(shown.id())) {
					// Hidden: no name, no description; only how rare it is and a vague clue
					this.setSlot(i, new GuiElementBuilder(Items.FIREWORK_STAR).setName(Ui.text("???", ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.BOLD))
							.hideDefaultTooltip()
							.addLoreLine(Ui.text("Secret · " + shown.rarity().label, shown.rarity().color))
							.addLoreLine(Component.empty())
							.addLoreLine(Ui.text(Catalog.secretClue(shown), ChatFormatting.GRAY))
							.addLoreLine(Ui.text("You'll know when you find it.", ChatFormatting.DARK_GRAY)));
					continue;
				}
				Achievement best = best(d, ladder);
				GuiElementBuilder b = ladder.size() > 1 ? base(p, d, shown) : icon(p, d, shown);
				if (ladder.size() > 1) {
					b.addLoreLine(Component.empty());
					b.addLoreLine(Ui.text("Grades", ChatFormatting.WHITE));
					for (int g = 0; g < ladder.size(); g++) {
						Achievement a = ladder.get(g);
						boolean got = d.earned.containsKey(a.id());
						b.addLoreLine(Component.empty()
								.append(Ui.text(got ? " ✔ " : " ✘ ", got ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
								.append(Ui.text(roman(g + 1) + "  ", ChatFormatting.DARK_GRAY))
								.append(Ui.text(a.name(), got ? a.rarity().color : ChatFormatting.GRAY))
								.append(Ui.text("  " + num(a.target()), ChatFormatting.DARK_GRAY)));
					}
					b.addLoreLine(Component.empty());
					if (shown.isCollection()) {
						Menu.action(b, "Click: see what you have and miss");
						Menu.action(b, "Right-click: pick a grade to wear");
					} else {
						Menu.action(b, "Click: pick a grade to wear");
						if (best != null) Menu.action(b, "Right-click: wear ✦ " + best.name() + " ✦");
					}
				}
				boolean graded = ladder.size() > 1;
				b.setCallback((x, t, act, g) -> {
					if (shown.isCollection() && !t.isRight) {
						Ui.click(p);
						new ItemsPage(p, shown.name(), shown, shown.items(), Filter.ALL, 0, () -> new CategoryPage(p, c, pg).open()).open();
					} else if (graded && (shown.isCollection() || !t.isRight)) {
						// The grades of this ladder: wear any one you earned
						Ui.click(p);
						GradesWindow.open(p, ladder, c.label, q -> new CategoryPage(q, c, pg).open());
					} else if (best != null) {
						CollectorMod.setTitle(p, best.id());
						new CategoryPage(p, c, pg).open();
					}
				});
				this.setSlot(i, b);
			}
			back("Achievements", () -> AchievementsGui.open(p));
			pages(pg, pages, n -> new CategoryPage(p, c, n).open());
			closeButton();
			help("One icon per achievement, with its", "grades: mine 100 stone, then 1,000,", "then 10,000… Every grade is a title.", "",
					"Sorted by how close you are.", "Click one to pick the grade you", "wear (each in its own colour).");
			finish();
		}

		/** The lowest grade not yet earned, or null if you have them all. */
		private static Achievement next(CollectorMod.Data d, List<Achievement> ladder) {
			for (Achievement a : ladder) if (!d.earned.containsKey(a.id())) return a;
			return null;
		}

		/** The highest grade earned, or null. */
		private static Achievement best(CollectorMod.Data d, List<Achievement> ladder) {
			Achievement best = null;
			for (Achievement a : ladder) if (d.earned.containsKey(a.id())) best = a;
			return best;
		}

		private static String roman(int n) {
			return switch (n) {
				case 1 -> "I";
				case 2 -> "II";
				case 3 -> "III";
				case 4 -> "IV";
				case 5 -> "V";
				case 6 -> "VI";
				case 7 -> "VII";
				case 8 -> "VIII";
				case 9 -> "IX";
				default -> Integer.toString(n);
			};
		}
	}

	// ---------------------------------------------------------------- items in a collection

	enum Filter {
		ALL("Everything"), MISSING("Still missing"), HAVE("Collected");

		final String label;

		Filter(String label) {
			this.label = label;
		}

		Filter next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	private static final class ItemsPage extends Menu {
		ItemsPage(ServerPlayer p, String name, Achievement owner, List<String> ids, Filter filter, int page, Runnable back) {
			super(p, 6, name);
			CollectorMod.Data d = CollectorMod.STORE.get(p);
			List<String> shown = new ArrayList<>();
			int have = 0;
			for (String id : ids) {
				boolean h = owner != null ? owner.has(p, d, id) : d.items.contains(id);
				if (h) have++;
				if (filter == Filter.ALL || (filter == Filter.HAVE) == h) shown.add(id);
			}
			this.setTitle(Component.literal(name + " · " + have + "/" + ids.size()));
			int per = contentSize();
			int pages = Math.max(1, (shown.size() + per - 1) / per);
			int pg = Math.min(page, pages - 1);
			for (int i = 0; i < per; i++) {
				int idx = pg * per + i;
				if (idx >= shown.size()) break;
				String id = shown.get(idx);
				boolean h = owner != null ? owner.has(p, d, id) : d.items.contains(id);
				ItemStack st = new ItemStack(itemOf(id));
				GuiElementBuilder b = GuiElementBuilder.from(st).setCount(1)
						.setName(Component.literal(h ? "✔ " : "✘ ").withStyle(h ? ChatFormatting.GREEN : ChatFormatting.RED)
								.append(st.getHoverName().copy().withStyle(h ? ChatFormatting.WHITE : ChatFormatting.GRAY)))
						.setLore(List.of(h ? Ui.text("Collected", ChatFormatting.GREEN) : Ui.text("Still missing", ChatFormatting.DARK_GRAY)))
						.hideDefaultTooltip();
				if (h) {
					b.glow();
					Services.itemFinder().ifPresent(finder -> {
						Menu.action(b, "Click: find it in your chests");
						b.setCallback((x, t, a, g) -> {
							if (finder.find(p, st)) this.close();
						});
					});
				}
				this.setSlot(i, b);
			}
			if (shown.isEmpty()) {
				this.setSlot(22, button(Ui.item("lime_dye"),
						filter == Filter.MISSING ? "Nothing missing – complete!" : "Nothing here", ChatFormatting.GREEN));
			}
			back("the previous page", back);
			pages(pg, pages, n -> new ItemsPage(p, name, owner, ids, filter, n, back).open());
			this.setSlot(navRow() + 6, actions(button(Items.HOPPER, "Showing: " + filter.label, ChatFormatting.YELLOW), "Click: switch")
					.setCallback((x, t, a, g) -> {
						Ui.click(p);
						new ItemsPage(p, name, owner, ids, filter.next(), 0, back).open();
					}));
			closeButton();
			help("✔ = you have had it in your inventory.", "✘ = still to find.", "", "Click a collected item to be shown", "the way to the chest it's in.");
			finish();
		}
	}

	// ---------------------------------------------------------------- choosing a title

	private static final class Titles extends Menu {
		private final boolean fromYou;

		@Override
		protected nl.libreria.core.Domain domain() {
			return nl.libreria.core.Domain.YOU;
		}

		/** {@code fromYou}: opened from the You page (Back goes there) rather than from Achievements. */
		Titles(ServerPlayer p, int page, boolean fromYou) {
			super(p, 6, "Choose your title");
			this.fromYou = fromYou;
			CollectorMod.Data d = CollectorMod.STORE.get(p);
			List<Achievement> earned = new ArrayList<>();
			for (String id : d.earned.keySet()) {
				Achievement a = Catalog.get(id);
				if (a != null) earned.add(a);
			}
			earned.sort(Comparator.<Achievement>comparingInt(a -> -a.rarity().ordinal()).thenComparing(Achievement::name));
			Achievement current = CollectorMod.titleAchievement(d);
			int per = contentSize();
			int pages = Math.max(1, (earned.size() + per - 1) / per);
			int pg = Math.min(page, pages - 1);
			for (int i = 0; i < per; i++) {
				int idx = pg * per + i;
				if (idx >= earned.size()) break;
				Achievement a = earned.get(idx);
				boolean chosen = a == current;
				GuiElementBuilder b = new GuiElementBuilder(a.icon())
						.setName(Component.literal("✦ " + a.name() + " ✦").withStyle(a.rarity().color, ChatFormatting.BOLD))
						.addLoreLine(Ui.text(a.rarity().label, a.rarity().color))
						.addLoreLine(Ui.text(a.description(), ChatFormatting.GRAY))
						.addLoreLine(Component.empty())
						.addLoreLine(chosen ? Ui.text("✔ Your current title", ChatFormatting.GREEN) : Ui.text("▸ Click: use this title", ChatFormatting.YELLOW))
						.hideDefaultTooltip();
				if (chosen) b.glow();
				b.setCallback((x, t, act, g) -> {
					CollectorMod.setTitle(p, a.id());
					new Titles(p, pg, fromYou).open();
				});
				this.setSlot(i, b);
			}
			if (earned.isEmpty()) {
				this.setSlot(22, button(Items.PAPER, "No titles yet", ChatFormatting.GRAY, "Every achievement you earn becomes", "a title you can show above your name."));
			}
			if (fromYou) up();
			else back("Achievements", () -> AchievementsGui.open(p));
			pages(pg, pages, n -> new Titles(p, n, fromYou).open());
			this.setSlot(navRow() + 1, actions(button(Items.NETHER_STAR, "Automatic", d.title.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.WHITE,
					"Always your rarest achievement."), d.title.isEmpty() ? "Active" : "Click: use automatic")
					.setCallback((x, t, a, g) -> {
						CollectorMod.setTitle(p, "");
						new Titles(p, pg, fromYou).open();
					}));
			this.setSlot(navRow() + 7, actions(button(Ui.item("gray_dye"), "No title",
					"-".equals(d.title) ? ChatFormatting.GREEN : ChatFormatting.WHITE, "Show just your name."),
					"-".equals(d.title) ? "Active" : "Click: hide my title")
					.setCallback((x, t, a, g) -> {
						CollectorMod.setTitle(p, "-");
						new Titles(p, pg, fromYou).open();
					}));
			closeButton();
			help("Your title floats above your name,", "in the color of its rarity.", "", "Tip: click [Use as title] in chat", "right after earning an achievement.");
			finish();
		}
	}
}
