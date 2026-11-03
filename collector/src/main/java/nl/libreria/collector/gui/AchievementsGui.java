package nl.libreria.collector.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.HubGui;
import nl.libreria.collector.Achievement;
import nl.libreria.collector.Achievement.Category;
import nl.libreria.collector.Catalog;
import nl.libreria.collector.CollectorMod;

/** Het prestatie-menu: overzicht, categorieën, verzamelingen (wat heb je, wat mis je) en titel kiezen. */
public final class AchievementsGui {
	private AchievementsGui() {
	}

	public static void open(ServerPlayer player) {
		new Main(player).open();
	}

	public static void openTitles(ServerPlayer player) {
		new Titles(player, 0).open();
	}

	// ---------------------------------------------------------------- bouwstenen

	private static Item itemOf(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
	}

	/** Icoon van een prestatie: glimmend en in rariteitskleur als je hem hebt, grijs als je eraan werkt. */
	static GuiElementBuilder icon(ServerPlayer p, CollectorMod.Data d, Achievement a) {
		Long at = d.earned.get(a.id());
		boolean done = at != null;
		long cur = a.current(p, d), target = a.target();
		GuiElementBuilder b = new GuiElementBuilder(a.icon())
				.setName(Component.literal((done ? "✔ " : "") + a.name()).withStyle(done ? a.rarity().color : ChatFormatting.GRAY))
				.addLoreLine(Ui.text(a.rarity().label, a.rarity().color))
				.addLoreLine(Ui.text(a.description(), ChatFormatting.GRAY))
				.addLoreLine(Component.empty())
				.addLoreLine(Component.empty().append(Ui.bar(target == 0 ? 1 : (double) cur / target, 12))
						.append(Ui.text("  " + cur + " / " + target, done ? ChatFormatting.GREEN : ChatFormatting.WHITE)))
				.hideDefaultTooltip();
		if (done) {
			b.addLoreLine(Ui.text("Behaald op " + CollectorMod.date(at), ChatFormatting.DARK_GRAY));
			b.glow();
		}
		if (a.isCollection()) b.addLoreLine(Ui.text("Klik: bekijk wat je hebt en wat je mist", ChatFormatting.AQUA));
		return b;
	}

	private static int earnedIn(CollectorMod.Data d, List<Achievement> list) {
		int n = 0;
		for (Achievement a : list) if (d.earned.containsKey(a.id())) n++;
		return n;
	}

	// ---------------------------------------------------------------- hoofdmenu

	private static final class Main extends SimpleGui {
		Main(ServerPlayer p) {
			super(MenuType.GENERIC_9x6, p, false);
			this.setTitle(Component.literal("Prestaties"));
			CollectorMod.Data d = CollectorMod.STORE.get(p);
			CollectorMod.scanNow(p);
			List<Achievement> all = Catalog.all();
			Achievement title = CollectorMod.titleAchievement(d);

			this.setSlot(4, new GuiElementBuilder(Items.NETHER_STAR).glow()
					.setName(Component.literal(p.getName().getString()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
					.addLoreLine(Ui.kv("Punten: ", String.valueOf(CollectorMod.points(d)), ChatFormatting.YELLOW))
					.addLoreLine(Ui.kv("Behaald: ", earnedIn(d, all) + " / " + all.size(), ChatFormatting.WHITE))
					.addLoreLine(Ui.kv("Items verzameld: ", Catalog.countAll(d) + " / " + Catalog.allItems().size(), ChatFormatting.WHITE))
					.addLoreLine(title == null ? Ui.kv("Titel: ", "geen", ChatFormatting.DARK_GRAY)
							: Component.empty().append(Ui.text("Titel: ", ChatFormatting.GRAY))
									.append(Component.literal(title.name()).withStyle(title.rarity().color))));

			Item[] icons = {Items.CHEST, Items.WHEAT, Items.CLOCK, Items.IRON_SWORD};
			int[] slots = {19, 20, 21, 22};
			Category[] cats = Category.values();
			for (int i = 0; i < cats.length; i++) {
				Category c = cats[i];
				List<Achievement> list = Catalog.of(c);
				int got = earnedIn(d, list);
				this.setSlot(slots[i], new GuiElementBuilder(icons[i])
						.setName(Ui.text(c.label, ChatFormatting.YELLOW))
						.addLoreLine(Ui.text(c.hint, ChatFormatting.GRAY))
						.addLoreLine(Component.empty().append(Ui.bar(list.isEmpty() ? 0 : (double) got / list.size(), 12))
								.append(Ui.text("  " + got + " / " + list.size(), ChatFormatting.WHITE)))
						.hideDefaultTooltip()
						.setCallback((x, t, a, g) -> {
							Ui.click(p);
							new CategoryPage(p, c, 0).open();
						}));
			}
			this.setSlot(24, Ui.button(Items.BOOKSHELF, "Alle items", ChatFormatting.AQUA)
					.addLoreLine(Ui.text(Catalog.countAll(d) + " van " + Catalog.allItems().size() + " verzameld", ChatFormatting.GRAY))
					.addLoreLine(Ui.text("Klik: wat mis je nog?", ChatFormatting.AQUA))
					.setCallback((x, t, a, g) -> {
						Ui.click(p);
						new ItemsPage(p, "Alle items", null, Catalog.allItems(), Filter.MISSING, 0, () -> AchievementsGui.open(p)).open();
					}));
			this.setSlot(25, Ui.button(Items.NAME_TAG, "Titel kiezen", ChatFormatting.LIGHT_PURPLE)
					.addLoreLine(Ui.text("Welke prestatie boven je naam staat.", ChatFormatting.GRAY))
					.setCallback((x, t, a, g) -> {
						Ui.click(p);
						new Titles(p, 0).open();
					}));

			// De laatste zeven behaalde prestaties
			List<Map.Entry<String, Long>> recent = new ArrayList<>(d.earned.entrySet());
			recent.sort(Map.Entry.<String, Long>comparingByValue().reversed());
			int slot = 37;
			for (Map.Entry<String, Long> e : recent) {
				Achievement a = Catalog.get(e.getKey());
				if (a == null) continue;
				this.setSlot(slot++, icon(p, d, a));
				if (slot > 43) break;
			}
			if (recent.isEmpty()) {
				this.setSlot(40, Ui.button(Items.PAPER, "Nog niets behaald", ChatFormatting.GRAY)
						.addLoreLine(Ui.text("Ga op avontuur!", ChatFormatting.DARK_GRAY)));
			}

			this.setSlot(45, Ui.button(Items.ARROW, "Terug naar Libreria", ChatFormatting.GRAY).setCallback((x, t, a, g) -> HubGui.open(p)));
			Services.menu(Services.MENU_STATS).ifPresent(stats -> this.setSlot(53,
					Ui.button(Items.WRITABLE_BOOK, "Statistieken", ChatFormatting.AQUA).setCallback((x, t, a, g) -> stats.accept(p))));
		}
	}

	// ---------------------------------------------------------------- categorie

	private static final class CategoryPage extends SimpleGui {
		CategoryPage(ServerPlayer p, Category c, int page) {
			super(MenuType.GENERIC_9x6, p, false);
			CollectorMod.Data d = CollectorMod.STORE.get(p);
			List<Achievement> list = new ArrayList<>(Catalog.of(c));
			// Eerst wat je bijna hebt: behaald achteraan, daarna op zeldzaamheid
			list.sort(Comparator.<Achievement>comparingInt(a -> d.earned.containsKey(a.id()) ? 1 : 0)
					.thenComparingInt(a -> a.rarity().ordinal()));
			int pages = Math.max(1, (list.size() + 44) / 45);
			int pg = Math.min(page, pages - 1);
			this.setTitle(Component.literal(c.label + " (" + earnedIn(d, list) + "/" + list.size() + ")"));
			for (int i = 0; i < 45; i++) {
				int idx = pg * 45 + i;
				if (idx >= list.size()) break;
				Achievement a = list.get(idx);
				GuiElementBuilder b = icon(p, d, a);
				if (a.isCollection()) {
					b.setCallback((x, t, act, g) -> {
						Ui.click(p);
						new ItemsPage(p, a.name(), a, a.items(), Filter.ALL, 0, () -> new CategoryPage(p, c, pg).open()).open();
					});
				}
				this.setSlot(i, b);
			}
			nav(this, p, pg, pages, () -> AchievementsGui.open(p), n -> new CategoryPage(p, c, n).open());
		}
	}

	private static void nav(SimpleGui gui, ServerPlayer p, int page, int pages, Runnable back, java.util.function.IntConsumer go) {
		if (page > 0) gui.setSlot(45, Ui.button(Items.ARROW, "Vorige pagina", ChatFormatting.WHITE).setCallback((x, t, a, g) -> go.accept(page - 1)));
		gui.setSlot(49, Ui.button(Items.BARRIER, "Terug", ChatFormatting.GRAY).setCallback((x, t, a, g) -> back.run()));
		if (page + 1 < pages) gui.setSlot(53, Ui.button(Items.ARROW, "Volgende pagina", ChatFormatting.WHITE).setCallback((x, t, a, g) -> go.accept(page + 1)));
		if (pages > 1) gui.setSlot(50, new GuiElementBuilder(Items.PAPER).setName(Ui.text("Pagina " + (page + 1) + " / " + pages, ChatFormatting.GRAY)));
	}

	// ---------------------------------------------------------------- items van een verzameling

	enum Filter {
		ALL("Alles"), MISSING("Wat je mist"), HAVE("Wat je hebt");

		final String label;

		Filter(String label) {
			this.label = label;
		}

		Filter next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	private static final class ItemsPage extends SimpleGui {
		ItemsPage(ServerPlayer p, String name, Achievement owner, List<String> ids, Filter filter, int page, Runnable back) {
			super(MenuType.GENERIC_9x6, p, false);
			CollectorMod.Data d = CollectorMod.STORE.get(p);
			List<String> shown = new ArrayList<>();
			int have = 0;
			for (String id : ids) {
				boolean h = owner != null ? owner.has(p, d, id) : d.items.contains(id);
				if (h) have++;
				if (filter == Filter.ALL || (filter == Filter.HAVE) == h) shown.add(id);
			}
			int pages = Math.max(1, (shown.size() + 44) / 45);
			int pg = Math.min(page, pages - 1);
			this.setTitle(Component.literal(name + " (" + have + "/" + ids.size() + ")"));
			for (int i = 0; i < 45; i++) {
				int idx = pg * 45 + i;
				if (idx >= shown.size()) break;
				String id = shown.get(idx);
				boolean h = owner != null ? owner.has(p, d, id) : d.items.contains(id);
				ItemStack st = new ItemStack(itemOf(id));
				GuiElementBuilder b = GuiElementBuilder.from(st).setCount(1)
						.setName(Component.literal((h ? "✔ " : "✘ ")).withStyle(h ? ChatFormatting.GREEN : ChatFormatting.RED)
								.append(st.getHoverName().copy().withStyle(h ? ChatFormatting.WHITE : ChatFormatting.GRAY)))
						.setLore(List.of(h ? Ui.text("Verzameld", ChatFormatting.GREEN) : Ui.text("Nog niet verzameld", ChatFormatting.DARK_GRAY)))
						.hideDefaultTooltip();
				if (h) {
					b.glow();
					Services.itemFinder().ifPresent(finder -> b.addLoreLine(Ui.text("Klik: zoek hem in je kisten", ChatFormatting.AQUA))
							.setCallback((x, t, a, g) -> {
								if (finder.find(p, st)) this.close();
							}));
				}
				this.setSlot(i, b);
			}
			nav(this, p, pg, pages, back, n -> new ItemsPage(p, name, owner, ids, filter, n, back).open());
			this.setSlot(48, Ui.button(Items.HOPPER, "Toon: " + filter.label, ChatFormatting.YELLOW)
					.addLoreLine(Ui.text("Klik om te wisselen", ChatFormatting.GRAY))
					.setCallback((x, t, a, g) -> {
						Ui.click(p);
						new ItemsPage(p, name, owner, ids, filter.next(), 0, back).open();
					}));
		}
	}

	// ---------------------------------------------------------------- titel kiezen

	private static final class Titles extends SimpleGui {
		Titles(ServerPlayer p, int page) {
			super(MenuType.GENERIC_9x6, p, false);
			CollectorMod.Data d = CollectorMod.STORE.get(p);
			List<Achievement> earned = new ArrayList<>();
			for (String id : d.earned.keySet()) {
				Achievement a = Catalog.get(id);
				if (a != null) earned.add(a);
			}
			earned.sort(Comparator.<Achievement>comparingInt(a -> -a.rarity().ordinal()).thenComparing(Achievement::name));
			int pages = Math.max(1, (earned.size() + 44) / 45);
			int pg = Math.min(page, pages - 1);
			this.setTitle(Component.literal("Kies je titel"));
			Achievement current = CollectorMod.titleAchievement(d);
			for (int i = 0; i < 45; i++) {
				int idx = pg * 45 + i;
				if (idx >= earned.size()) break;
				Achievement a = earned.get(idx);
				boolean chosen = a == current && !d.title.isEmpty();
				GuiElementBuilder b = new GuiElementBuilder(a.icon())
						.setName(Component.literal("✦ " + a.name() + " ✦").withStyle(a.rarity().color))
						.addLoreLine(Ui.text(a.rarity().label, a.rarity().color))
						.addLoreLine(Ui.text(a.description(), ChatFormatting.GRAY))
						.addLoreLine(Ui.text(chosen ? "Dit is je titel" : "Klik: gebruik als titel", chosen ? ChatFormatting.GREEN : ChatFormatting.AQUA))
						.hideDefaultTooltip();
				if (chosen) b.glow();
				b.setCallback((x, t, act, g) -> {
					d.title = a.id();
					CollectorMod.STORE.markDirty(p);
					Ui.click(p);
					new Titles(p, pg).open();
				});
				this.setSlot(i, b);
			}
			nav(this, p, pg, pages, () -> AchievementsGui.open(p), n -> new Titles(p, n).open());
			this.setSlot(46, Ui.button(Items.NETHER_STAR, "Automatisch", d.title.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.WHITE)
					.addLoreLine(Ui.text("Altijd je zeldzaamste prestatie.", ChatFormatting.GRAY))
					.setCallback((x, t, a, g) -> {
						d.title = "";
						CollectorMod.STORE.markDirty(p);
						Ui.click(p);
						new Titles(p, pg).open();
					}));
			this.setSlot(47, Ui.button(Items.BARRIER, "Geen titel", "-".equals(d.title) ? ChatFormatting.GREEN : ChatFormatting.WHITE)
					.setCallback((x, t, a, g) -> {
						d.title = "-";
						CollectorMod.STORE.markDirty(p);
						Ui.click(p);
						new Titles(p, pg).open();
					}));
			if (earned.isEmpty()) {
				this.setSlot(22, Ui.button(Items.PAPER, "Nog geen titels", ChatFormatting.GRAY)
						.addLoreLine(Ui.text("Haal prestaties om titels te krijgen.", ChatFormatting.DARK_GRAY)));
			}
		}
	}
}
