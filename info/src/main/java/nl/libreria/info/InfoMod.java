package nl.libreria.info;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.Items;
import nl.libreria.core.Domain;
import nl.libreria.core.Module;
import nl.libreria.core.Services;
import nl.libreria.core.Setting;

/** InfoMod – een Libreria-module: kijk-info, kompas- en klok-info, paard-statistieken en een statistieken-menu. */
public final class InfoMod implements ModInitializer {
	public static final Module MODULE = Module.register("info", "Info", Items.SPYGLASS,
			"See what you're looking at, what your", "compass and clock say, horse stats", "and your own statistics.")
			.in(Domain.YOU, "What you see");
	public static final Setting.Choice LOOK = MODULE.choice("kijk", "Look info", Items.SPYGLASS, 2,
			new int[] {0, 1, 2}, new String[] {"Off", "Only interesting things", "Everything"},
			"A small tooltip above what you look at", "(within reach): crop growth, honey,", "chest slots, animal health, horse stats,",
			"players' achievements…", "'Only interesting' skips plain blocks.");
	public static final Setting.Toggle COMPASS = MODULE.toggle("kompas", "Compass info", true, Items.COMPASS,
			"Compass in hand: direction, coordinates", "and biome. Recovery compass: distance", "to your death point. Lodestone compass:", "distance to its lodestone.").in(Domain.PLACES, "Finding your way");
	public static final Setting.Toggle CLOCK = MODULE.toggle("klok", "Clock info", true, Items.CLOCK,
			"Clock in hand: time of day, how long", "until night or day, and whether", "you can sleep.");
	public static final Setting.Choice PLACES = MODULE.choice("places", "Place names", Items.FILLED_MAP, 0,
			new int[] {0, 1, 2}, new String[] {"Always", "Only new places", "Off"},
			"Walk into a village, a temple or another", "stretch of land: its name fades in, small,", "at the top. Everything goes in your journal.").in(Domain.PLACES, "Finding your way");
	public static final Setting.Toggle HORSE = MODULE.toggle("paard", "Horse stats", true, Items.SADDLE,
			"Speed, jump and health of horses,", "donkeys and mules. Hold {sneak} and", "press {use} on a horse for the full card.");

	/** For the screenshot test: an enchanted sword with a long story, that just earned its name (in hotbar slot 1). */
	public static void debugLegend(ServerPlayer p) {
		var reg = p.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
		java.util.function.Function<String, net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>> ench = id -> reg.getOrThrow(
				net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
						net.minecraft.resources.Identifier.withDefaultNamespace(id)));
		net.minecraft.world.item.ItemStack sword = new net.minecraft.world.item.ItemStack(Items.DIAMOND_SWORD);
		net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(sword, m -> {
			m.set(ench.apply("sharpness"), 5);
			m.set(ench.apply("unbreaking"), 3);
			m.set(ench.apply("mending"), 1);
			m.set(ench.apply("looting"), 3);
			m.set(ench.apply("fire_aspect"), 2);
		});
		p.getInventory().setItem(0, sword);
		net.minecraft.world.item.ItemStack held = p.getInventory().getItem(0);
		for (int i = 0; i < 140; i++) Weapons.count(p, held, "zombie");
		for (int i = 0; i < 45; i++) Weapons.count(p, held, "skeleton");
		for (int i = 0; i < 12; i++) Weapons.count(p, held, "creeper");
		Weapons.count(p, held, "wither");
	}

	/**
	 * For the screenshots: the hotbar full of found gear. A part of a story (named), a found mace and boots with their
	 * texts, a book, and a pickaxe with its owner's own words.
	 */
	public static void debugFound(ServerPlayer p) {
		var access = p.level().registryAccess();
		var reg = access.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
		java.util.function.Function<String, net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>> ench = id -> reg.getOrThrow(
				net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
						net.minecraft.resources.Identifier.withDefaultNamespace(id)));
		java.util.function.BiFunction<net.minecraft.world.item.Item, Object[], net.minecraft.world.item.ItemStack> make = (item, list) -> {
			net.minecraft.world.item.ItemStack s = new net.minecraft.world.item.ItemStack(item);
			net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(s, m -> {
				for (int i = 0; i + 1 < list.length; i += 2) m.set(ench.apply((String) list[i]), (Integer) list[i + 1]);
			});
			return s;
		};
		var sword = make.apply(Items.DIAMOND_SWORD, new Object[] {"sharpness", 5, "fire_aspect", 2, "looting", 3, "unbreaking", 3, "mending", 1});
		Found.Text piece = Found.PIECES.stream().filter(t -> "lantern".equals(t.story()) && t.part() == 1).findFirst().orElse(null);
		if (piece != null) {
			Found.write(sword, piece, "in a shipwreck", 312, p.getName().getString(), access);
			Found.remember(p, sword, piece, "in a shipwreck", 312);
		}
		// a second part of the same story, as if it was found earlier and given away
		Found.Text third = Found.PIECES.stream().filter(t -> "lantern".equals(t.story()) && t.part() == 3).findFirst().orElse(null);
		if (third != null) {
			var axe = new net.minecraft.world.item.ItemStack(Items.GOLDEN_AXE);
			Found.write(axe, third, "in a village", 330, p.getName().getString(), access);
			Found.remember(p, axe, third, "in a village", 330);
			Found.saw(p, "lantern:3", axe);
		}
		var mace = make.apply(Items.MACE, new Object[] {"density", 5, "breach", 4});
		Found.Text sold = Found.LINES.stream().filter(t -> t.text().startsWith("Sold \"as is\"")).findFirst().orElse(null);
		if (sold != null) {
			Found.write(mace, sold, "Dropped by a pillager", 298, p.getName().getString(), access);
			Found.remember(p, mace, sold, "Dropped by a pillager", 298);
		}
		var boots = make.apply(Items.NETHERITE_BOOTS, new Object[] {"depth_strider", 3, "feather_falling", 4});
		Found.Text stain = Found.LINES.stream().filter(t -> t.text().startsWith("Comes with a small stain")).findFirst().orElse(null);
		if (stain != null) {
			Found.write(boots, stain, "Fished up", 300, p.getName().getString(), access);
			Found.remember(p, boots, stain, "Fished up", 300);
		}
		var book = new net.minecraft.world.item.ItemStack(Items.ENCHANTED_BOOK);
		book.set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, make.apply(Items.DIAMOND_SWORD,
				new Object[] {"mending", 1, "unbreaking", 3}).get(net.minecraft.core.component.DataComponents.ENCHANTMENTS));
		var pick = make.apply(Items.DIAMOND_PICKAXE, new Object[] {"efficiency", 5, "fortune", 3});
		java.util.List<net.minecraft.network.chat.Component> own = new java.util.ArrayList<>();
		for (String l : Lore.wrap("\"Found my first diamonds with this. Do not lend it to Tobias.\"", 40)) own.add(Lore.line(l, Describe.OWN, true));
		own.add(Lore.line("  ~ " + p.getName().getString(), Describe.BY, false));
		Lore.set(pick, Lore.OWN, own);
		var inv = p.getInventory();
		inv.setItem(0, sword);
		inv.setItem(1, mace);
		inv.setItem(2, boots);
		inv.setItem(3, book);
		inv.setItem(4, pick);
		inv.setItem(5, new net.minecraft.world.item.ItemStack(Items.MAGMA_BLOCK));
		var potion = new net.minecraft.world.item.ItemStack(Items.POTION);
		potion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
				new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.TURTLE_MASTER));
		inv.setItem(6, potion);
		inv.setItem(7, new net.minecraft.world.item.ItemStack(Items.OMINOUS_BOTTLE));
	}

	/** For the screenshots: the lore book, a story in it, or your finds. */
	public static void debugLoreBook(ServerPlayer p, String page) {
		switch (page) {
			case "story" -> LoreBook.story(p, "lantern");
			case "finds" -> LoreBook.finds(p, 0);
			default -> LoreBook.open(p);
		}
	}

	/** For the screenshots: the window to write on what's in your hand. */
	public static void debugDescribe(ServerPlayer p) {
		Describe.open(p);
	}

	/** For the screenshots: the enchantment details on or off. */
	public static void setDetails(ServerPlayer p, boolean on) {
		Details.ON.set(p, on);
	}

	@Override
	public void onInitialize() {
		MODULE.hubEntry(Domain.PROGRESS, 20, Items.WRITABLE_BOOK, "Statistics", StatsGui::open,
				"Playtime, distances, kills, most mined blocks and more, with the top players.");
		Services.provideMenu(Services.MENU_STATS, StatsGui::open);
		Services.provideMenu(Services.MENU_JOURNAL, Places::journal);
		Services.provideDiscoveries(new Services.Discoveries() {
			@Override
			public int places(ServerPlayer player) {
				return Places.STORE.get(player).places.size();
			}

			@Override
			public int biomes(ServerPlayer player) {
				return Places.STORE.get(player).biomes.size();
			}

			@Override
			public int ofKind(ServerPlayer player, String kind) {
				int n = 0;
				for (Places.Place pl : Places.STORE.get(player).places) if (pl.kind.equals(kind)) n++;
				return n;
			}

			@Override
			public int structureKinds(ServerPlayer player) {
				java.util.Set<String> all = PlaceNames.structureLabels(), seen = new java.util.HashSet<>();
				for (Places.Place pl : Places.STORE.get(player).places) if (all.contains(pl.kind)) seen.add(pl.kind);
				return seen.size();
			}

			@Override
			public int structureKindsTotal() {
				return PlaceNames.structureLabels().size();
			}
		});
		VillagerNames.init();
		Weapons.init();
		Found.init();
		Describe.init();
		Details.init();
		Pets.init();
		Pets.registerPlaces();
		Places.registerPlaces();
		nl.libreria.core.Profile.add(40, Items.FILLED_MAP, "First discoveries", (server, who, stats) -> {
			int n = Discoveries.firstsOf(who);
			return n == 0 ? null : nl.libreria.core.Ui.n(n);
		});
		Services.provideMenu(Services.MENU_PETS, Pets::open);
		MODULE.hubEntry(Domain.PLACES, 80, Items.LEAD, "Your pets", Pets::open,
				"Your tamed animals and where they are; click one to be shown the way.");
		MODULE.hubEntry(Domain.PLACES, 90, Items.WRITABLE_BOOK, "Journal", Places::journal,
				"Every village, temple, peak, lake and biome you found.");

		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Look.forget(handler.player.getUUID());
			Places.forget(handler.player.getUUID());
			Leaderboards.record(handler.player);
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Look.clearAll();
			Leaderboards.clearAll();
		});
		MODULE.adminButton("Leaderboards", "Place a floating top ten in the world", Leaderboards::placeWindow);
		MODULE.commands(Domain.PROGRESS, "/stats – your statistics");
		MODULE.commands(Domain.PLACES, "/journal – places you found", "/pets – your pets and where they are");
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTickCount();
			if (tick % 2 == 0) Look.tick(server);
			if (tick % 10 == 5) Held.tick(server);
			Leaderboards.tick(server, tick);
			if (tick % 10 == 7) Places.tick(server, tick);
			if (tick % 20 == 11) Pets.tick(server, tick);
			if (tick % 40 == 13) Weapons.tick(server);
			if (tick % 40 == 29) Found.tick(server);
		});

		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp
					&& sp.isShiftKeyDown() && sp.getMainHandItem().isEmpty()
					&& entity instanceof AbstractHorse horse && HORSE.get(sp)) {
				Horses.sendCard(sp, horse);
			}
			return InteractionResult.PASS;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
			dispatcher.register(Commands.literal("pets").executes(ctx -> {
				Pets.open(ctx.getSource().getPlayerOrException());
				return 1;
			}));
			dispatcher.register(Commands.literal("journal").executes(ctx -> {
				Places.journal(ctx.getSource().getPlayerOrException());
				return 1;
			}));
			dispatcher.register(Commands.literal("leaderboard").executes(ctx -> {
				ServerPlayer p = ctx.getSource().getPlayerOrException();
				if (!nl.libreria.core.Ui.isOp(p)) return 0;
				Leaderboards.placeWindow(p);
				return 1;
			}).then(Commands.literal("remove").executes(ctx -> {
				ServerPlayer p = ctx.getSource().getPlayerOrException();
				if (!nl.libreria.core.Ui.isOp(p)) return 0;
				Leaderboards.removeNearest(p);
				return 1;
			})));
			for (String name : new String[] {"stats", "statistics"}) {
				dispatcher.register(Commands.literal(name).executes(ctx -> {
					StatsGui.open(ctx.getSource().getPlayerOrException());
					return 1;
				}));
			}
		});

		InfoTests.register();
	}
}
