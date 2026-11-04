package nl.libreria.info;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.HubGui;
import nl.libreria.core.gui.Menu;

/**
 * Your vanilla statistics, but readable: hours, kilometers and top lists. A player card at the top, then one row per
 * section (General, Combat, Travel, Top lists) with a coloured label on both sides.
 */
final class StatsGui extends Menu {
	private static final Locale EN = Locale.US;

	private StatsGui(ServerPlayer player) {
		super(player, 6, "Statistics");
		build();
	}

	static void open(ServerPlayer player) {
		new StatsGui(player).open();
	}

	// ---------------------------------------------------------------- formatting

	static String num(long n) {
		return String.format(EN, "%,d", n);
	}

	static String times(long n) {
		return num(n) + (n == 1 ? " time" : " times");
	}

	/** Damage stats are stored in tenths of a health point; a heart is two health points. */
	static String hearts(long tenths) {
		return num(tenths / 20) + " ❤";
	}

	/** Ticks to "3 d 4 h", "12 h 5 min" or "35 min". */
	static String time(long ticks) {
		long min = ticks / 20 / 60;
		long h = min / 60, d = h / 24;
		if (d > 0) return d + " d " + (h % 24) + " h";
		if (h > 0) return h + " h " + (min % 60) + " min";
		return min + " min";
	}

	/** Centimeters to meters or kilometers. */
	static String dist(long cm) {
		double m = cm / 100.0;
		return m >= 1000 ? String.format(EN, "%.1f km", m / 1000) : String.format(EN, "%.0f m", m);
	}

	private int custom(Identifier stat) {
		return this.player.getStats().getValue(Stats.CUSTOM, stat);
	}

	/** Custom stat by id, for stats we don't reference by constant (unknown ids simply count as 0). */
	private int custom(String id) {
		return custom(Identifier.withDefaultNamespace(id));
	}

	/**
	 * A stat: bold name in the section colour, the value as the first lore line (skipped when empty), then grey
	 * "key: value" details.
	 */
	private static GuiElementBuilder stat(Item icon, String name, ChatFormatting color, String value, Component... details) {
		GuiElementBuilder b = button(icon, name, color);
		if (!value.isEmpty()) b.addLoreLine(Ui.text(value, ChatFormatting.WHITE));
		for (Component d : details) b.addLoreLine(d);
		return b;
	}

	private static Component kv(String key, String value) {
		return Ui.kv(key, value, ChatFormatting.WHITE);
	}

	/** One section row: a labelled pane on the left and right, the items centred in between. */
	private void section(int r, String pane, String label, ChatFormatting color, List<GuiElementBuilder> items, String... headerLore) {
		for (int col : new int[] {0, 8}) {
			GuiElementBuilder h = header(pane, label, color);
			for (String l : headerLore) h.addLoreLine(Ui.text(l, ChatFormatting.GRAY));
			this.setSlot(r * 9 + col, h);
		}
		int[] slots = row(r, items.size());
		for (int i = 0; i < slots.length; i++) this.setSlot(slots[i], items.get(i));
	}

	// ---------------------------------------------------------------- main page

	private void build() {
		ServerPlayer p = this.player;
		ChatFormatting general = ChatFormatting.YELLOW, combat = ChatFormatting.RED, travel = ChatFormatting.GREEN;

		long onFoot = (long) custom(Stats.WALK_ONE_CM) + custom(Stats.SPRINT_ONE_CM) + custom(Stats.CROUCH_ONE_CM);
		long water = (long) custom(Stats.SWIM_ONE_CM) + custom("walk_under_water_one_cm") + custom("walk_on_water_one_cm");
		long riding = (long) custom(Stats.HORSE_ONE_CM) + custom("pig_one_cm") + custom("strider_one_cm")
				+ custom("happy_ghast_one_cm") + custom("nautilus_one_cm");
		long traveled = onFoot + water + riding + custom(Stats.AVIATE_ONE_CM) + custom(Stats.BOAT_ONE_CM)
				+ custom(Stats.MINECART_ONE_CM) + custom(Stats.CLIMB_ONE_CM);

		// ---- Header: your player card
		GuiElementBuilder head = new GuiElementBuilder(Items.PLAYER_HEAD).setProfile(p.getGameProfile())
				.setName(title(p.getName().getString(), ChatFormatting.GOLD)).hideDefaultTooltip();
		head.addLoreLine(kv("Playtime: ", time(custom(Stats.PLAY_TIME))));
		head.addLoreLine(kv("Traveled: ", dist(traveled)));
		head.addLoreLine(kv("Since your last death: ", time(custom(Stats.TIME_SINCE_DEATH))));
		head.addLoreLine(kv("Since you last slept: ", time(custom(Stats.TIME_SINCE_REST))));
		Services.progress().ifPresent(pr -> {
			List<Component> card = pr.card(p);
			if (card.isEmpty()) return;
			head.addLoreLine(Component.empty());
			card.forEach(head::addLoreLine);
		});
		this.setSlot(4, head);

		// ---- General
		section(1, "yellow", "General", general, List.of(
				stat(Items.EMERALD, "Villager trades", general, times(custom(Stats.TRADED_WITH_VILLAGER)),
						kv("Talked to villagers: ", times(custom(Stats.TALKED_TO_VILLAGER)))),
				stat(Ui.item("red_bed"), "Slept in a bed", general, times(custom(Stats.SLEEP_IN_BED))),
				stat(Items.ENCHANTING_TABLE, "Items enchanted", general, num(custom(Stats.ENCHANT_ITEM))),
				stat(Items.WHEAT, "Animals bred", general, num(custom(Stats.ANIMALS_BRED))),
				stat(Items.FISHING_ROD, "Fish caught", general, num(custom(Stats.FISH_CAUGHT))),
				stat(Items.CHEST, "Chests opened", general, num(custom(Stats.OPEN_CHEST)),
						kv("Barrels: ", num(custom(Stats.OPEN_BARREL))),
						kv("Shulker boxes: ", num(custom(Stats.OPEN_SHULKER_BOX))),
						kv("Ender chest: ", num(custom(Stats.OPEN_ENDERCHEST)))),
				stat(Items.CAKE, "Fun facts", general, "",
						kv("Cake slices eaten: ", num(custom(Stats.EAT_CAKE_SLICE))),
						kv("Discs played: ", num(custom(Stats.PLAY_RECORD))),
						kv("Note blocks played: ", num(custom("play_noteblock"))),
						kv("Bells rung: ", num(custom("bell_ring"))),
						kv("Flowers potted: ", num(custom(Stats.POT_FLOWER))))));

		// ---- Combat
		section(2, "red", "Combat", combat, List.of(
				stat(Items.IRON_SWORD, "Mobs killed", combat, num(custom(Stats.MOB_KILLS)),
						kv("Players killed: ", num(custom(Stats.PLAYER_KILLS)))),
				stat(Items.SKELETON_SKULL, "Deaths", combat, times(custom(Stats.DEATHS))),
				stat(Items.DIAMOND_SWORD, "Damage dealt", combat, hearts(custom(Stats.DAMAGE_DEALT))),
				stat(Items.SHIELD, "Damage taken", combat, hearts(custom(Stats.DAMAGE_TAKEN)),
						kv("Blocked by shield: ", hearts(custom(Stats.DAMAGE_BLOCKED_BY_SHIELD)))),
				stat(Items.BELL, "Raids won", combat, num(custom(Stats.RAID_WIN)),
						kv("Raids started: ", num(custom(Stats.RAID_TRIGGER)))),
				stat(Items.TARGET, "Targets hit", combat, num(custom(Stats.TARGET_HIT)))));

		// ---- Travel
		List<Component> flight = new ArrayList<>();
		if (custom("fly_one_cm") > 0) flight.add(kv("Creative flight: ", dist(custom("fly_one_cm"))));
		List<Component> rides = new ArrayList<>();
		rides.add(kv("Horses: ", dist(custom(Stats.HORSE_ONE_CM))));
		ride(rides, "Pigs: ", "pig_one_cm");
		ride(rides, "Striders: ", "strider_one_cm");
		ride(rides, "Happy ghasts: ", "happy_ghast_one_cm");
		ride(rides, "Nautiluses: ", "nautilus_one_cm");
		section(3, "lime", "Travel", travel, List.of(
				stat(Items.LEATHER_BOOTS, "On foot", travel, dist(onFoot),
						kv("Walking: ", dist(custom(Stats.WALK_ONE_CM))),
						kv("Sprinting: ", dist(custom(Stats.SPRINT_ONE_CM))),
						kv("Sneaking: ", dist(custom(Stats.CROUCH_ONE_CM)))),
				stat(Ui.item("water_bucket"), "In the water", travel, dist(water),
						kv("Swimming: ", dist(custom(Stats.SWIM_ONE_CM))),
						kv("Walking under water: ", dist(custom("walk_under_water_one_cm"))),
						kv("Walking on water: ", dist(custom("walk_on_water_one_cm")))),
				stat(Ui.item("elytra"), "Elytra flight", travel, dist(custom(Stats.AVIATE_ONE_CM)), flight.toArray(Component[]::new)),
				stat(Ui.item("saddle"), "Riding", travel, dist(riding), rides.toArray(Component[]::new)),
				stat(Ui.item("oak_boat"), "By boat", travel, dist(custom(Stats.BOAT_ONE_CM))),
				stat(Ui.item("minecart"), "By minecart", travel, dist(custom(Stats.MINECART_ONE_CM))),
				stat(Items.RABBIT_FOOT, "Jumped", travel, times(custom(Stats.JUMP)),
						kv("Fallen: ", dist(custom(Stats.FALL_ONE_CM))),
						kv("Climbed: ", dist(custom(Stats.CLIMB_ONE_CM))))));

		// ---- Top lists
		section(4, "light_blue", "Top lists", ChatFormatting.AQUA, List.of(
				top(Items.DIAMOND_PICKAXE, "Most mined", "Mined", "Mine some blocks first!",
						Stats.BLOCK_MINED, b -> new ItemStack(b.asItem()), b -> b.getName()),
				top(Items.CRAFTING_TABLE, "Most crafted", "Crafted", "Craft something first!",
						Stats.ITEM_CRAFTED, ItemStack::new, it -> new ItemStack(it).getHoverName()),
				top(Items.GOLDEN_APPLE, "Most used", "Used", "Use some items first!",
						Stats.ITEM_USED, ItemStack::new, it -> new ItemStack(it).getHoverName()),
				top(Items.HOPPER, "Most picked up", "Picked up", "Pick up some items first!",
						Stats.ITEM_PICKED_UP, ItemStack::new, it -> new ItemStack(it).getHoverName()),
				top(Items.ANVIL, "Items broken", "Broken", "No tools broken yet. Nice!",
						Stats.ITEM_BROKEN, ItemStack::new, it -> new ItemStack(it).getHoverName()),
				top(Items.ZOMBIE_HEAD, "Most killed", "Killed", "Defeat some mobs first!",
						Stats.ENTITY_KILLED, StatsGui::egg, EntityType::getDescription),
				top(Items.WITHER_SKELETON_SKULL, "Killed by", "Killed you", "Nothing has killed you yet!",
						Stats.ENTITY_KILLED_BY, StatsGui::egg, EntityType::getDescription)),
				"Click one for the full ranking.");

		// ---- Navigation bar
		back("the Libreria menu", () -> HubGui.open(p));
		Services.menu(Services.MENU_ACHIEVEMENTS).ifPresent(achievements -> this.setSlot(navRow() + 2,
				actions(button(Items.NETHER_STAR, "Achievements", ChatFormatting.GOLD,
						"What you've collected so far", "and what you're still missing."), "Click: open")
						.glow().setCallback((i, t, a, g) -> {
							Ui.click(p);
							achievements.accept(p);
						})));
		closeButton();
		help("All your vanilla statistics,", "grouped and easy to read.", "Hover an icon for the details.", "",
				"Top lists: click one for the full", "ranking. Also: /stats");
		finish();
	}

	/** Adds "Pigs: 1.2 km" to a lore list, but only for mounts you've actually ridden. */
	private void ride(List<Component> lines, String label, String stat) {
		int cm = custom(stat);
		if (cm > 0) lines.add(kv(label, dist(cm)));
	}

	/** Spawn egg as the icon for a mob (a head for players), otherwise a name tag. */
	static ItemStack egg(EntityType<?> type) {
		if (BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath().equals("player")) return new ItemStack(Items.PLAYER_HEAD);
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		Item egg = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg")).orElse(Items.NAME_TAG);
		return new ItemStack(egg);
	}

	/** A top-list button: shows your #1 and the total, click for the full paged list. */
	private <T> GuiElementBuilder top(Item icon, String name, String verb, String empty, StatType<T> type,
			Function<T, ItemStack> iconOf, Function<T, Component> nameOf) {
		TopList list = collect(this.player, name, icon, verb, empty, type, iconOf, nameOf);
		GuiElementBuilder b = button(icon, name, ChatFormatting.AQUA);
		if (list.entries().isEmpty()) {
			b.addLoreLine(Ui.text("Nothing yet", ChatFormatting.DARK_GRAY));
		} else {
			Entry first = list.entries().get(0);
			b.addLoreLine(Ui.text("#1 ", ChatFormatting.GOLD).append(first.name().copy().withStyle(ChatFormatting.WHITE))
					.append(Ui.text(" · " + num(first.count()), ChatFormatting.GRAY)));
			b.addLoreLine(kv("Total: ", num(list.total())));
			b.addLoreLine(kv("Different kinds: ", num(list.entries().size())));
		}
		actions(b, "Click: see the full list");
		b.setCallback((i, t, a, g) -> {
			Ui.click(this.player);
			// Collect again, so the list is fresh
			new TopGui(this.player, collect(this.player, name, icon, verb, empty, type, iconOf, nameOf)).open();
		});
		return b;
	}

	// ---------------------------------------------------------------- top list

	private record Entry(ItemStack icon, Component name, int count) {
	}

	/** A sorted top list (most first) with everything the menu needs to show it. */
	private record TopList(String title, Item icon, String verb, String empty, List<Entry> entries, long total) {
	}

	private static <T> TopList collect(ServerPlayer player, String title, Item icon, String verb, String empty, StatType<T> type,
			Function<T, ItemStack> iconOf, Function<T, Component> nameOf) {
		ServerStatsCounter stats = player.getStats();
		Registry<T> registry = type.getRegistry();
		List<Entry> entries = new ArrayList<>();
		long total = 0;
		for (T value : registry) {
			int c = stats.getValue(type, value);
			if (c <= 0) continue;
			ItemStack st = iconOf.apply(value);
			if (st.isEmpty()) st = new ItemStack(Items.PAPER);
			entries.add(new Entry(st, nameOf.apply(value), c));
			total += c;
		}
		entries.sort(Comparator.comparingInt((Entry e) -> e.count()).reversed());
		return new TopList(title, icon, verb, empty, entries, total);
	}

	/** The full ranking, 28 per page (rows 2–5, columns 2–8), with a summary on top. */
	private static final class TopGui extends Menu {
		private static final int PER_PAGE = 28;
		private final TopList list;

		TopGui(ServerPlayer player, TopList list) {
			super(player, 6, "Statistics · " + list.title());
			this.list = list;
			back("Statistics", () -> StatsGui.open(player));
			closeButton();
			help("Ranked from most to least.", "Hover an icon for the exact count",
					"and its share of the total.", "", "Use ◀ ▶ below to turn pages.");
			show(0);
		}

		private void show(int requested) {
			List<Entry> entries = this.list.entries();
			int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
			int page = Math.max(0, Math.min(pages - 1, requested));
			clearContent();
			// Reset the page buttons; pages() only places the ones that apply
			for (int s : new int[] {2, 3, 5}) this.clearSlot(navRow() + s);

			// Summary on top
			GuiElementBuilder sum = button(this.list.icon(), this.list.title(), ChatFormatting.AQUA);
			sum.addLoreLine(Ui.kv("Total: ", num(this.list.total()), ChatFormatting.WHITE));
			sum.addLoreLine(Ui.kv("Different kinds: ", num(entries.size()), ChatFormatting.WHITE));
			if (!entries.isEmpty()) {
				int from = page * PER_PAGE + 1, to = Math.min(entries.size(), (page + 1) * PER_PAGE);
				sum.addLoreLine(Ui.kv("Showing: ", "#" + from + " – #" + to, ChatFormatting.WHITE));
			}
			this.setSlot(4, sum);

			if (entries.isEmpty()) {
				this.setSlot(22, button(Ui.item("gray_dye"), "Nothing here yet", ChatFormatting.GRAY, this.list.empty()));
			}
			for (int i = 0; i < PER_PAGE; i++) {
				int idx = page * PER_PAGE + i;
				if (idx >= entries.size()) break;
				Entry e = entries.get(idx);
				int rank = idx + 1;
				boolean podium = rank <= 3;
				GuiElementBuilder b = GuiElementBuilder.from(e.icon().copy()).setCount(1).hideDefaultTooltip()
						.setName(Ui.text("#" + rank + " ", podium ? ChatFormatting.GOLD : ChatFormatting.YELLOW)
								.append(e.name().copy().withStyle(ChatFormatting.WHITE)));
				b.addLoreLine(Ui.kv(this.list.verb() + ": ", num(e.count()), ChatFormatting.YELLOW));
				double share = this.list.total() > 0 ? 100.0 * e.count() / this.list.total() : 0;
				b.addLoreLine(Ui.text(String.format(EN, "%.1f%% of the total", share), ChatFormatting.DARK_GRAY));
				if (podium) b.glow();
				this.setSlot((1 + i / 7) * 9 + 1 + i % 7, b);
			}
			pages(page, pages, this::show);
			finish();
		}
	}
}
