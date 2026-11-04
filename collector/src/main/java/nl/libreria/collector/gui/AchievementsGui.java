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
import nl.libreria.core.gui.HubGui;
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

	public static void openTitles(ServerPlayer player) {
		new Titles(player, 0).open();
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
		Long at = d.earned.get(a.id());
		boolean done = at != null;
		long cur = a.current(p, d), target = a.target();
		GuiElementBuilder b = new GuiElementBuilder(a.icon())
				.setName(Ui.text((done ? "✔ " : "") + a.name(), done ? a.rarity().color : ChatFormatting.GRAY).withStyle(ChatFormatting.BOLD))
				.addLoreLine(Ui.text(a.rarity().label, a.rarity().color))
				.addLoreLine(Ui.text(a.description(), ChatFormatting.GRAY))
				.addLoreLine(Component.empty())
				.addLoreLine(Component.empty().append(Ui.bar(target == 0 ? 1 : (double) cur / target, 12))
						.append(Ui.text("  " + num(cur) + " / " + num(target), done ? ChatFormatting.GREEN : ChatFormatting.WHITE)))
				.hideDefaultTooltip();
		if (done) {
			b.addLoreLine(Ui.text("Earned on " + CollectorMod.date(at), ChatFormatting.DARK_GRAY));
			b.glow();
		}
		b.addLoreLine(Component.empty());
		if (a.isCollection()) Menu.action(b, "Click: see what you have and miss");
		if (done) Menu.action(b, (a.isCollection() ? "Right-click" : "Click") + ": use as title ✦ " + a.name() + " ✦");
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
				new Titles(p, 0).open();
			}));

			// Categories
			Item[] icons = {Items.CHEST, Items.WHEAT, Items.CLOCK, Items.IRON_SWORD};
			int[] slots = row(3, 4);
			Category[] cats = Category.values();
			for (int i = 0; i < cats.length; i++) {
				Category c = cats[i];
				List<Achievement> list = Catalog.of(c);
				int got = earnedIn(d, list);
				GuiElementBuilder b = button(icons[i], c.label, ChatFormatting.YELLOW, c.hint);
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

			back("the Libreria menu", () -> HubGui.open(p));
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
			CollectorMod.Data d = CollectorMod.STORE.get(p);
			List<Achievement> list = new ArrayList<>(Catalog.of(c));
			// What you're closest to first; earned ones at the end
			list.sort(Comparator.<Achievement>comparingInt(a -> d.earned.containsKey(a.id()) ? 1 : 0)
					.thenComparingDouble(a -> -((double) a.current(p, d) / Math.max(1, a.target())))
					.thenComparingInt(a -> a.rarity().ordinal()));
			int per = contentSize();
			int pages = Math.max(1, (list.size() + per - 1) / per);
			int pg = Math.min(page, pages - 1);
			for (int i = 0; i < per; i++) {
				int idx = pg * per + i;
				if (idx >= list.size()) break;
				Achievement a = list.get(idx);
				GuiElementBuilder b = icon(p, d, a);
				boolean done = d.earned.containsKey(a.id());
				b.setCallback((x, t, act, g) -> {
					if (a.isCollection() && !t.isRight) {
						Ui.click(p);
						new ItemsPage(p, a.name(), a, a.items(), Filter.ALL, 0, () -> new CategoryPage(p, c, pg).open()).open();
					} else if (done) {
						CollectorMod.setTitle(p, a.id());
						new CategoryPage(p, c, pg).open();
					}
				});
				this.setSlot(i, b);
			}
			back("achievements", () -> AchievementsGui.open(p));
			pages(pg, pages, n -> new CategoryPage(p, c, n).open());
			closeButton();
			help("Sorted by how close you are.", "Earned ones glow and come last.", "", "Click an earned one to use it", "as your title.");
			finish();
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
			help("✔ = you have had it in your inventory.", "✘ = still to find.", "", "Click a collected item to beam", "to the chest it's in (Chests).");
			finish();
		}
	}

	// ---------------------------------------------------------------- choosing a title

	private static final class Titles extends Menu {
		Titles(ServerPlayer p, int page) {
			super(p, 6, "Choose your title");
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
					new Titles(p, pg).open();
				});
				this.setSlot(i, b);
			}
			if (earned.isEmpty()) {
				this.setSlot(22, button(Items.PAPER, "No titles yet", ChatFormatting.GRAY, "Every achievement you earn becomes", "a title you can show above your name."));
			}
			back("achievements", () -> AchievementsGui.open(p));
			pages(pg, pages, n -> new Titles(p, n).open());
			this.setSlot(navRow() + 1, actions(button(Items.NETHER_STAR, "Automatic", d.title.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.WHITE,
					"Always your rarest achievement."), d.title.isEmpty() ? "Active" : "Click: use automatic")
					.setCallback((x, t, a, g) -> {
						CollectorMod.setTitle(p, "");
						new Titles(p, pg).open();
					}));
			this.setSlot(navRow() + 7, actions(button(Ui.item("gray_dye"), "No title",
					"-".equals(d.title) ? ChatFormatting.GREEN : ChatFormatting.WHITE, "Show just your name."),
					"-".equals(d.title) ? "Active" : "Click: hide my title")
					.setCallback((x, t, a, g) -> {
						CollectorMod.setTitle(p, "-");
						new Titles(p, pg).open();
					}));
			closeButton();
			help("Your title floats above your name,", "in the color of its rarity.", "", "Tip: click [Use as title] in chat", "right after earning an achievement.");
			finish();
		}
	}
}
