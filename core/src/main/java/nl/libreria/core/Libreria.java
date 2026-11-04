package nl.libreria.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import nl.libreria.core.gui.HubGui;
import nl.libreria.core.gui.SettingsGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Libreria – de kern. Andere Libreria-mods (ChestsMod, ToolsMod, WaypointsMod, Oogst) bouwen hierop:
 * instellingen per speler (/libreria settings), het Libreria-menu met de knop in het E-scherm, de gedeelde actiebalk
 * en de straal die de weg wijst.
 */
public final class Libreria implements ModInitializer {
	public static final String MOD_ID = "libreria";
	public static final Logger LOG = LoggerFactory.getLogger("Libreria");

	public static final Module MODULE = Module.register("libreria", "Libreria (general)", Items.HOPPER,
			"General Libreria settings.");
	public static final Setting.Toggle HUB_BUTTON = MODULE.toggle("knop", "Inventory buttons", true, Items.HOPPER,
			"Buttons in your inventory's empty crafting", "grid: Libreria menu, sort, quick stack.");
	public static final Setting.Toggle OWN_PLATE = MODULE.toggle("eigen_naam", "Your own name tag", true, Items.NAME_TAG,
			"See your own title and nickname above", "your head in third person (F5).");
	public static final Setting.Choice GUIDE_SECONDS = MODULE.choice("straal", "Beam duration", Items.SPECTRAL_ARROW, 1,
			new int[]{45, 90, 180, 300}, new String[]{"45 seconds", "90 seconds", "3 minutes", "5 minutes"},
			"The beam also stops once you arrive.");

	private static final Map<UUID, Boolean> HUB_SHOWN = new HashMap<>();

	@Override
	public void onInitialize() {
		Pack.init();
		// Opslag zo vroeg mogelijk klaarzetten, zodat modules ze in hun eigen SERVER_STARTED al kunnen lezen
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			PlayerStore.initAll(server);
			WorldStore.initAll(server);
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (SelfTests.enabled()) server.execute(() -> SelfTests.runAll(server));
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Guide.clearAll();
			Nameplate.clearAll();
			PlayerStore.saveDirtyAll();
			WorldStore.saveAll();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			Guide.clear(handler.player);
			Guide.clearFlashes(id);
			Hud.forget(id);
			Nameplate.forget(id);
			HUB_SHOWN.remove(id);
			PlayerStore.unloadAll(id);
		});
		ServerTickEvents.END_SERVER_TICK.register(Libreria::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			// /libreria (or /lib) opens the menu, /libreria settings the per-player settings
			for (String name : new String[] {"libreria", "lib"}) {
				dispatcher.register(Commands.literal(name).executes(c -> {
					HubGui.open(c.getSource().getPlayerOrException());
					return 1;
				}).then(Commands.literal("settings").executes(c -> {
					SettingsGui.open(c.getSource().getPlayerOrException());
					return 1;
				})));
			}
		});
		CoreTests.register();
		LOG.info("Libreria core loaded");
	}

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % 20 == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) refreshHub(p);
		}
		Guide.tick(server, tick);
		if (tick % 10 == 3) Nameplate.tick(server);
		Hud.tick(server, tick);
		if (tick % 1200 == 0) {
			PlayerStore.saveDirtyAll();
			WorldStore.saveAll();
		}
	}

	/** Houdt het icoon in het crafting-uitvak zichtbaar (of haalt het weg als het niet meer mag). */
	private static void refreshHub(ServerPlayer p) {
		if (p.containerMenu != p.inventoryMenu) return;
		boolean active = Hub.active(p);
		boolean was = HUB_SHOWN.getOrDefault(p.getUUID(), false);
		if (active) {
			Hub.send(p);
		} else if (was) {
			Hub.remove(p);
		}
		HUB_SHOWN.put(p.getUUID(), active);
	}
}
