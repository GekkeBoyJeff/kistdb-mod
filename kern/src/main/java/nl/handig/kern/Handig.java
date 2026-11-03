package nl.handig.kern;

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
import nl.handig.kern.gui.HubGui;
import nl.handig.kern.gui.SettingsGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handig – de kern. Andere Handig-mods (KistDB, Gereedschap, Wegwijzer, Oogst) bouwen hierop:
 * instellingen per speler (/opties), het Handig-menu met de knop in het E-scherm, de gedeelde actiebalk
 * en de straal die de weg wijst.
 */
public final class Handig implements ModInitializer {
	public static final String MOD_ID = "handig";
	public static final Logger LOG = LoggerFactory.getLogger("Handig");

	public static final Module MODULE = Module.register("handig", "Handig (algemeen)", Items.HOPPER,
			"Algemene instellingen van Handig.");
	public static final Setting.Toggle HUB_BUTTON = MODULE.toggle("knop", "Knop in het E-scherm", true, Items.HOPPER,
			"Het gloeiende Handig-icoon in het", "crafting-uitvak van je inventory.");
	public static final Setting.Choice GUIDE_SECONDS = MODULE.choice("straal", "Hoe lang de straal blijft", Items.SPECTRAL_ARROW, 1,
			new int[]{45, 90, 180, 300}, new String[]{"45 seconden", "90 seconden", "3 minuten", "5 minuten"},
			"De straal stopt ook als je er bent.");

	private static final Map<UUID, Boolean> HUB_SHOWN = new HashMap<>();

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			PlayerStore.initAll(server);
			if (SelfTests.enabled()) server.execute(() -> SelfTests.runAll(server));
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Guide.clearAll();
			PlayerStore.saveDirtyAll();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			Guide.clear(handler.player);
			Guide.clearFlashes(id);
			Hud.forget(id);
			HUB_SHOWN.remove(id);
			PlayerStore.unloadAll(id);
		});
		ServerTickEvents.END_SERVER_TICK.register(Handig::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			dispatcher.register(Commands.literal("handig").executes(c -> {
				HubGui.open(c.getSource().getPlayerOrException());
				return 1;
			}));
			dispatcher.register(Commands.literal("opties").executes(c -> {
				SettingsGui.open(c.getSource().getPlayerOrException());
				return 1;
			}));
		});
		KernTests.register();
		LOG.info("Handig-kern geladen");
	}

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % 20 == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) refreshHub(p);
		}
		Guide.tick(server, tick);
		Hud.tick(server, tick);
		if (tick % 1200 == 0) PlayerStore.saveDirtyAll();
	}

	/** Houdt het icoon in het crafting-uitvak zichtbaar (of haalt het weg als het niet meer mag). */
	private static void refreshHub(ServerPlayer p) {
		if (p.containerMenu != p.inventoryMenu) return;
		boolean active = Hub.active(p);
		boolean was = HUB_SHOWN.getOrDefault(p.getUUID(), false);
		if (active) {
			Hub.send(p);
		} else if (was && p.inventoryMenu.getSlot(0).getItem().isEmpty()) {
			Hub.remove(p);
		}
		HUB_SHOWN.put(p.getUUID(), active);
	}
}
