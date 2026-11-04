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
import nl.libreria.core.Module;
import nl.libreria.core.Services;
import nl.libreria.core.Setting;

/** InfoMod – een Libreria-module: kijk-info, kompas- en klok-info, paard-statistieken en een statistieken-menu. */
public final class InfoMod implements ModInitializer {
	public static final Module MODULE = Module.register("info", "Info", Items.SPYGLASS,
			"See what you're looking at, what your", "compass and clock say, horse stats", "and your own statistics.");
	public static final Setting.Choice LOOK = MODULE.choice("kijk", "Look info", Items.SPYGLASS, 2,
			new int[] {0, 1, 2}, new String[] {"Off", "Only interesting things", "Everything"},
			"A small tooltip above what you look at", "(within reach): crop growth, honey,", "chest slots, animal health, horse stats,",
			"players' achievements…", "'Only interesting' skips plain blocks.");
	public static final Setting.Toggle COMPASS = MODULE.toggle("kompas", "Compass info", true, Items.COMPASS,
			"Compass in hand: direction, coordinates", "and biome. Recovery compass: distance", "to your death point. Lodestone compass:", "distance to its lodestone.");
	public static final Setting.Toggle CLOCK = MODULE.toggle("klok", "Clock info", true, Items.CLOCK,
			"Clock in hand: time of day, how long", "until night or day, and whether", "you can sleep.");
	public static final Setting.Choice PLACES = MODULE.choice("places", "Place names", Items.FILLED_MAP, 0,
			new int[] {0, 1, 2}, new String[] {"Always", "Only new places", "Off"},
			"Walk into a village, a temple or another", "stretch of land: its name fades in, small,", "at the top. Everything goes in your journal.");
	public static final Setting.Toggle HORSE = MODULE.toggle("paard", "Horse stats", true, Items.SADDLE,
			"Speed, jump and health of horses,", "donkeys and mules. Sneak-right-click", "a horse for the full card.");

	@Override
	public void onInitialize() {
		MODULE.hubEntry(Items.WRITABLE_BOOK, "Statistics", ChatFormatting.AQUA, StatsGui::open,
				"Playtime, distances, kills, most", "mined blocks and more.");
		Services.provideMenu(Services.MENU_STATS, StatsGui::open);
		Services.provideMenu(Services.MENU_JOURNAL, Places::journal);
		VillagerNames.init();
		MODULE.hubEntry(Module.Category.WORLD, Items.WRITABLE_BOOK, "Journal", ChatFormatting.GOLD, Places::journal,
				"Every village, temple and biome", "you found, with a beam to them.");

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
		MODULE.commands("/stats – your statistics", "/journal – places you found");
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTickCount();
			if (tick % 2 == 0) Look.tick(server);
			if (tick % 10 == 5) Held.tick(server);
			Leaderboards.tick(server, tick);
			if (tick % 10 == 7) Places.tick(server, tick);
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
