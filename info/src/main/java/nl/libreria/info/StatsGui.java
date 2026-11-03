package nl.libreria.info;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
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
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.HubGui;

/** Je vanilla-statistieken, maar leesbaar: in uren, kilometers en top-lijstjes. */
final class StatsGui extends SimpleGui {
	private static final Locale EN = Locale.US;

	private StatsGui(ServerPlayer player) {
		super(MenuType.GENERIC_9x6, player, false);
		this.setTitle(Component.literal("Statistics – " + player.getName().getString()));
		build();
	}

	static void open(ServerPlayer player) {
		new StatsGui(player).open();
	}

	// ---------------------------------------------------------------- opmaak

	static String num(long n) {
		return String.format(EN, "%,d", n);
	}

	/** Ticks naar "3 d 4 h", "12 h 5 min" of "35 min". */
	static String time(long ticks) {
		long min = ticks / 20 / 60;
		long h = min / 60, d = h / 24;
		if (d > 0) return d + " d " + (h % 24) + " h";
		if (h > 0) return h + " h " + (min % 60) + " min";
		return min + " min";
	}

	/** Centimeters naar meters of kilometers. */
	static String dist(long cm) {
		double m = cm / 100.0;
		return m >= 1000 ? String.format(EN, "%.1f km", m / 1000) : String.format(EN, "%.0f m", m);
	}

	private int custom(Identifier stat) {
		return this.getPlayer().getStats().getValue(Stats.CUSTOM, stat);
	}

	private void stat(int slot, Item icon, String name, String value, String... lore) {
		GuiElementBuilder b = new GuiElementBuilder(icon).setName(Ui.text(name, ChatFormatting.YELLOW))
				.addLoreLine(Ui.text(value, ChatFormatting.WHITE)).hideDefaultTooltip();
		for (String l : lore) b.addLoreLine(Ui.text(l, ChatFormatting.GRAY));
		this.setSlot(slot, b.build());
	}

	// ---------------------------------------------------------------- hoofdpagina

	private void build() {
		ServerPlayer p = this.getPlayer();
		stat(0, Items.CLOCK, "Playtime", time(custom(Stats.PLAY_TIME)),
				"Since your last death: " + time(custom(Stats.TIME_SINCE_DEATH)),
				"Since you last slept: " + time(custom(Stats.TIME_SINCE_REST)));
		stat(1, Items.SKELETON_SKULL, "Died", num(custom(Stats.DEATHS)) + " times");
		stat(2, Items.IRON_SWORD, "Mobs killed", num(custom(Stats.MOB_KILLS)),
				"Players: " + num(custom(Stats.PLAYER_KILLS)));
		stat(3, Items.WHEAT, "Animals bred", num(custom(Stats.ANIMALS_BRED)));
		long walked = (long) custom(Stats.WALK_ONE_CM) + custom(Stats.SPRINT_ONE_CM) + custom(Stats.CROUCH_ONE_CM);
		stat(4, Items.LEATHER_BOOTS, "Walked", dist(walked),
				"Swum: " + dist(custom(Stats.SWIM_ONE_CM)),
				"Flown (elytra): " + dist(custom(Stats.AVIATE_ONE_CM)),
				"On horseback: " + dist(custom(Stats.HORSE_ONE_CM)),
				"By boat: " + dist(custom(Stats.BOAT_ONE_CM)),
				"By minecart: " + dist(custom(Stats.MINECART_ONE_CM)),
				"Fallen: " + dist(custom(Stats.FALL_ONE_CM)),
				"Climbed: " + dist(custom(Stats.CLIMB_ONE_CM)));
		stat(5, Items.FISHING_ROD, "Fish caught", num(custom(Stats.FISH_CAUGHT)));
		stat(6, Items.EMERALD, "Villager trades", num(custom(Stats.TRADED_WITH_VILLAGER)) + " times",
				"Talked to: " + num(custom(Stats.TALKED_TO_VILLAGER)) + " times");
		stat(7, Ui.item("red_bed"), "Slept in a bed", num(custom(Stats.SLEEP_IN_BED)) + " times");
		stat(8, Items.ENCHANTING_TABLE, "Enchanted", num(custom(Stats.ENCHANT_ITEM)) + " items");

		stat(9, Items.DIAMOND_SWORD, "Damage dealt", num(custom(Stats.DAMAGE_DEALT) / 20) + " ❤");
		stat(10, Items.SHIELD, "Damage taken", num(custom(Stats.DAMAGE_TAKEN) / 20) + " ❤",
				"Blocked by shield: " + num(custom(Stats.DAMAGE_BLOCKED_BY_SHIELD) / 20) + " ❤");
		stat(11, Items.RABBIT_FOOT, "Jumped", num(custom(Stats.JUMP)) + " times");
		stat(12, Items.CAKE, "Cake slices eaten", num(custom(Stats.EAT_CAKE_SLICE)));
		stat(13, Items.CHEST, "Chests opened", num(custom(Stats.OPEN_CHEST)),
				"Barrels: " + num(custom(Stats.OPEN_BARREL)),
				"Shulker boxes: " + num(custom(Stats.OPEN_SHULKER_BOX)),
				"Ender chest: " + num(custom(Stats.OPEN_ENDERCHEST)));
		stat(14, Items.BELL, "Raids won", num(custom(Stats.RAID_WIN)),
				"Started: " + num(custom(Stats.RAID_TRIGGER)));
		stat(15, Items.JUKEBOX, "Discs played", num(custom(Stats.PLAY_RECORD)));
		stat(16, Items.FLOWER_POT, "Flowers potted", num(custom(Stats.POT_FLOWER)));
		stat(17, Items.TARGET, "Targets hit", num(custom(Stats.TARGET_HIT)));

		top(28, Items.DIAMOND_PICKAXE, "Most mined", Stats.BLOCK_MINED, b -> new ItemStack(b.asItem()), b -> b.getName());
		top(29, Items.CRAFTING_TABLE, "Most crafted", Stats.ITEM_CRAFTED, ItemStack::new, i -> new ItemStack(i).getHoverName());
		top(30, Items.GOLDEN_APPLE, "Most used", Stats.ITEM_USED, ItemStack::new, i -> new ItemStack(i).getHoverName());
		top(31, Items.HOPPER, "Most picked up", Stats.ITEM_PICKED_UP, ItemStack::new, i -> new ItemStack(i).getHoverName());
		top(32, Items.ANVIL, "Items broken", Stats.ITEM_BROKEN, ItemStack::new, i -> new ItemStack(i).getHoverName());
		top(33, Items.ZOMBIE_HEAD, "Most killed", Stats.ENTITY_KILLED, StatsGui::egg, EntityType::getDescription);
		top(34, Items.WITHER_SKELETON_SKULL, "Killed by", Stats.ENTITY_KILLED_BY, StatsGui::egg, EntityType::getDescription);

		this.setSlot(45, Ui.button(Items.ARROW, "Back to Libreria", ChatFormatting.GRAY)
				.setCallback((i, t, a, g) -> HubGui.open(p)).build());
		Services.menu(Services.MENU_ACHIEVEMENTS).ifPresent(open -> this.setSlot(49,
				Ui.button(Items.NETHER_STAR, "Achievements & collection", ChatFormatting.GOLD)
						.addLoreLine(Ui.text("What you've collected so far", ChatFormatting.GRAY))
						.addLoreLine(Ui.text("and what you're still missing.", ChatFormatting.GRAY))
						.glow().setCallback((i, t, a, g) -> open.accept(p)).build()));
	}

	/** Spawn-ei als icoon voor een mob, anders een naamkaartje. */
	static ItemStack egg(EntityType<?> type) {
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		Item egg = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg")).orElse(Items.NAME_TAG);
		return new ItemStack(egg);
	}

	private <T> void top(int slot, Item icon, String name, StatType<T> type, Function<T, ItemStack> iconOf, Function<T, Component> nameOf) {
		ServerPlayer p = this.getPlayer();
		this.setSlot(slot, Ui.button(icon, name, ChatFormatting.AQUA)
				.addLoreLine(Ui.text("Click: the top 45", ChatFormatting.GRAY))
				.setCallback((i, t, a, g) -> {
					Ui.click(p);
					TopGui.open(p, name, type, iconOf, nameOf);
				}).build());
	}

	// ---------------------------------------------------------------- top-lijst

	private static final class TopGui extends SimpleGui {
		private <T> TopGui(ServerPlayer player, String name, StatType<T> type, Function<T, ItemStack> iconOf, Function<T, Component> nameOf) {
			super(MenuType.GENERIC_9x6, player, false);
			ServerStatsCounter stats = player.getStats();
			Registry<T> registry = type.getRegistry();
			record Row<T>(T value, int count) {
			}
			List<Row<T>> rows = new ArrayList<>();
			long total = 0;
			for (T value : registry) {
				int c = stats.getValue(type, value);
				if (c > 0) {
					rows.add(new Row<>(value, c));
					total += c;
				}
			}
			rows.sort(Comparator.comparingInt((Row<T> r) -> r.count()).reversed());
			this.setTitle(Component.literal(name + " (" + num(total) + ")"));
			for (int i = 0; i < Math.min(45, rows.size()); i++) {
				Row<T> r = rows.get(i);
				ItemStack st = iconOf.apply(r.value());
				if (st.isEmpty()) st = new ItemStack(Items.BARRIER);
				this.setSlot(i, GuiElementBuilder.from(st).setCount(1)
						.setName(Component.literal("#" + (i + 1) + " ").withStyle(ChatFormatting.GOLD).append(nameOf.apply(r.value()).copy().withStyle(ChatFormatting.WHITE)))
						.setLore(List.of(Ui.text(num(r.count()) + "×", ChatFormatting.YELLOW)))
						.hideDefaultTooltip().build());
			}
			if (rows.isEmpty()) {
				this.setSlot(22, Ui.button(Ui.item("gray_dye"), "Nothing yet", ChatFormatting.GRAY).build());
			}
			this.setSlot(49, Ui.button(Items.ARROW, "Back", ChatFormatting.GRAY)
					.setCallback((i, t, a, g) -> StatsGui.open(player)).build());
		}

		static <T> void open(ServerPlayer player, String name, StatType<T> type, Function<T, ItemStack> iconOf, Function<T, Component> nameOf) {
			new TopGui(player, name, type, iconOf, nameOf).open();
		}
	}
}
