package nl.handig.info;

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
import nl.handig.kern.Module;
import nl.handig.kern.Services;
import nl.handig.kern.Setting;

/** Info – een Handig-module: kijk-info, kompas- en klok-info, paard-statistieken en een statistieken-menu. */
public final class Info implements ModInitializer {
	public static final Module MODULE = Module.register("info", "Info", Items.SPYGLASS,
			"Zie waar je naar kijkt, wat je kompas", "en klok zeggen, paard-statistieken", "en je eigen statistieken.");
	public static final Setting.Choice LOOK = MODULE.choice("kijk", "Kijk-info", Items.SPYGLASS, 1,
			new int[] {0, 1, 2}, new String[] {"Uit", "Alleen interessant", "Alles"},
			"Toont boven je hotbar waar je naar kijkt:", "groei van gewassen, honing, kistvulling,",
			"levens van dieren, paardwaarden…", "'Alles' toont ook gewone blokken.");
	public static final Setting.Toggle COMPASS = MODULE.toggle("kompas", "Kompas-info", true, Items.COMPASS,
			"Kompas in je hand: richting, coördinaten", "en bioom. Herstelkompas: afstand tot je", "sterfplek. Magneetkompas: afstand.");
	public static final Setting.Toggle CLOCK = MODULE.toggle("klok", "Klok-info", true, Items.CLOCK,
			"Klok in je hand: tijd van de dag, hoe lang", "tot het nacht/dag wordt en of je kunt slapen.");
	public static final Setting.Toggle HORSE = MODULE.toggle("paard", "Paard-statistieken", true, Items.SADDLE,
			"Snelheid, sprong en levens van paarden,", "ezels en muilezels. Sneak-rechtsklik op", "een paard voor de volledige kaart.");

	@Override
	public void onInitialize() {
		MODULE.hubEntry(Items.WRITABLE_BOOK, "Statistieken", ChatFormatting.AQUA, StatsGui::open,
				"Speeltijd, afstanden, kills, meest", "gemijnde blokken en meer.");
		Services.provideMenu(Services.MENU_STATS, StatsGui::open);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTickCount();
			if (tick % 4 == 0) Look.tick(server);
			if (tick % 10 == 5) Held.tick(server);
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
			for (String name : new String[] {"statistieken", "stats"}) {
				dispatcher.register(Commands.literal(name).executes(ctx -> {
					StatsGui.open(ctx.getSource().getPlayerOrException());
					return 1;
				}));
			}
		});

		InfoTests.register();
	}
}
