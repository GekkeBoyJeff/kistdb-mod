package nl.handig.info;

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
import nl.handig.kern.Services;
import nl.handig.kern.Ui;
import nl.handig.kern.gui.HubGui;

/** Je vanilla-statistieken, maar leesbaar: in uren, kilometers en top-lijstjes. */
final class StatsGui extends SimpleGui {
	private static final Locale NL = Locale.of("nl", "NL");

	private StatsGui(ServerPlayer player) {
		super(MenuType.GENERIC_9x6, player, false);
		this.setTitle(Component.literal("Statistieken – " + player.getName().getString()));
		build();
	}

	static void open(ServerPlayer player) {
		new StatsGui(player).open();
	}

	// ---------------------------------------------------------------- opmaak

	static String num(long n) {
		return String.format(NL, "%,d", n);
	}

	/** Ticks naar "3 d 4 u", "12 u 5 min" of "35 min". */
	static String time(long ticks) {
		long min = ticks / 20 / 60;
		long h = min / 60, d = h / 24;
		if (d > 0) return d + " d " + (h % 24) + " u";
		if (h > 0) return h + " u " + (min % 60) + " min";
		return min + " min";
	}

	/** Centimeters naar meters of kilometers. */
	static String dist(long cm) {
		double m = cm / 100.0;
		return m >= 1000 ? String.format(NL, "%.1f km", m / 1000) : String.format(NL, "%.0f m", m);
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
		stat(0, Items.CLOCK, "Speeltijd", time(custom(Stats.PLAY_TIME)),
				"Sinds je laatste dood: " + time(custom(Stats.TIME_SINCE_DEATH)),
				"Sinds je laatste slaap: " + time(custom(Stats.TIME_SINCE_REST)));
		stat(1, Items.SKELETON_SKULL, "Doodgegaan", num(custom(Stats.DEATHS)) + " keer");
		stat(2, Items.IRON_SWORD, "Mobs verslagen", num(custom(Stats.MOB_KILLS)),
				"Spelers: " + num(custom(Stats.PLAYER_KILLS)));
		stat(3, Items.WHEAT, "Dieren gefokt", num(custom(Stats.ANIMALS_BRED)));
		long walked = (long) custom(Stats.WALK_ONE_CM) + custom(Stats.SPRINT_ONE_CM) + custom(Stats.CROUCH_ONE_CM);
		stat(4, Items.LEATHER_BOOTS, "Gelopen", dist(walked),
				"Gezwommen: " + dist(custom(Stats.SWIM_ONE_CM)),
				"Gevlogen (elytra): " + dist(custom(Stats.AVIATE_ONE_CM)),
				"Te paard: " + dist(custom(Stats.HORSE_ONE_CM)),
				"Per boot: " + dist(custom(Stats.BOAT_ONE_CM)),
				"Per mijnkar: " + dist(custom(Stats.MINECART_ONE_CM)),
				"Gevallen: " + dist(custom(Stats.FALL_ONE_CM)),
				"Geklommen: " + dist(custom(Stats.CLIMB_ONE_CM)));
		stat(5, Items.FISHING_ROD, "Vissen gevangen", num(custom(Stats.FISH_CAUGHT)));
		stat(6, Items.EMERALD, "Handel met dorpelingen", num(custom(Stats.TRADED_WITH_VILLAGER)) + " keer",
				"Gepraat: " + num(custom(Stats.TALKED_TO_VILLAGER)) + " keer");
		stat(7, Ui.item("red_bed"), "Geslapen", num(custom(Stats.SLEEP_IN_BED)) + " keer");
		stat(8, Items.ENCHANTING_TABLE, "Betoverd", num(custom(Stats.ENCHANT_ITEM)) + " items");

		stat(9, Items.DIAMOND_SWORD, "Schade uitgedeeld", num(custom(Stats.DAMAGE_DEALT) / 20) + " ❤");
		stat(10, Items.SHIELD, "Schade opgelopen", num(custom(Stats.DAMAGE_TAKEN) / 20) + " ❤",
				"Geblokt met schild: " + num(custom(Stats.DAMAGE_BLOCKED_BY_SHIELD) / 20) + " ❤");
		stat(11, Items.RABBIT_FOOT, "Gesprongen", num(custom(Stats.JUMP)) + " keer");
		stat(12, Items.CAKE, "Taartpunten", num(custom(Stats.EAT_CAKE_SLICE)));
		stat(13, Items.CHEST, "Kisten geopend", num(custom(Stats.OPEN_CHEST)),
				"Vaten: " + num(custom(Stats.OPEN_BARREL)),
				"Shulkerdozen: " + num(custom(Stats.OPEN_SHULKER_BOX)),
				"Enderkist: " + num(custom(Stats.OPEN_ENDERCHEST)));
		stat(14, Items.BELL, "Overvallen gewonnen", num(custom(Stats.RAID_WIN)),
				"Gestart: " + num(custom(Stats.RAID_TRIGGER)));
		stat(15, Items.JUKEBOX, "Platen gedraaid", num(custom(Stats.PLAY_RECORD)));
		stat(16, Items.FLOWER_POT, "Bloemen gepot", num(custom(Stats.POT_FLOWER)));
		stat(17, Items.TARGET, "Doelwit geraakt", num(custom(Stats.TARGET_HIT)));

		top(28, Items.DIAMOND_PICKAXE, "Meest gemijnd", Stats.BLOCK_MINED, b -> new ItemStack(b.asItem()), b -> b.getName());
		top(29, Items.CRAFTING_TABLE, "Meest gemaakt", Stats.ITEM_CRAFTED, ItemStack::new, i -> new ItemStack(i).getHoverName());
		top(30, Items.GOLDEN_APPLE, "Meest gebruikt", Stats.ITEM_USED, ItemStack::new, i -> new ItemStack(i).getHoverName());
		top(31, Items.HOPPER, "Meest opgepakt", Stats.ITEM_PICKED_UP, ItemStack::new, i -> new ItemStack(i).getHoverName());
		top(32, Items.ANVIL, "Kapotgegaan", Stats.ITEM_BROKEN, ItemStack::new, i -> new ItemStack(i).getHoverName());
		top(33, Items.ZOMBIE_HEAD, "Meest verslagen", Stats.ENTITY_KILLED, StatsGui::egg, EntityType::getDescription);
		top(34, Items.WITHER_SKELETON_SKULL, "Gedood door", Stats.ENTITY_KILLED_BY, StatsGui::egg, EntityType::getDescription);

		this.setSlot(45, Ui.button(Items.ARROW, "Terug naar Handig", ChatFormatting.GRAY)
				.setCallback((i, t, a, g) -> HubGui.open(p)).build());
		Services.menu(Services.MENU_ACHIEVEMENTS).ifPresent(open -> this.setSlot(49,
				Ui.button(Items.NETHER_STAR, "Prestaties & verzameling", ChatFormatting.GOLD)
						.addLoreLine(Ui.text("Wat je al verzameld hebt en", ChatFormatting.GRAY))
						.addLoreLine(Ui.text("wat je nog mist.", ChatFormatting.GRAY))
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
				.addLoreLine(Ui.text("Klik: de top 45", ChatFormatting.GRAY))
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
				this.setSlot(22, Ui.button(Ui.item("gray_dye"), "Nog niets", ChatFormatting.GRAY).build());
			}
			this.setSlot(49, Ui.button(Items.ARROW, "Terug", ChatFormatting.GRAY)
					.setCallback((i, t, a, g) -> StatsGui.open(player)).build());
		}

		static <T> void open(ServerPlayer player, String name, StatType<T> type, Function<T, ItemStack> iconOf, Function<T, Component> nameOf) {
			new TopGui(player, name, type, iconOf, nameOf).open();
		}
	}
}
