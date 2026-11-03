package nl.kistdb;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KistDB – volledig server-side.
 * <ul>
 *   <li>Sorteer-icoon in het crafting-uitvak van het E-scherm (linksklik sorteren, rechtsklik zoeken)</li>
 *   <li>Middelklik (of dubbelklik op een leeg slot) in een kist sorteert die kist of je inventory</li>
 *   <li>Database per speler van kisten die hij opent, met iconen-GUI, afstand en een lichtstraal</li>
 *   <li>noIndex per kist (sneak + linksklik met lege hand, of in de GUI)</li>
 * </ul>
 */
public final class KistDB implements ModInitializer {
	public static final String MOD_ID = "kistdb";
	public static final Logger LOG = LoggerFactory.getLogger("KistDB");

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(Store::init);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Beams.clearAll();
			Store.saveAll();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Store.get(handler.player.getUUID()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Beams.clear(handler.player);
			Tracker.forget(handler.player);
			Store.unload(handler.player.getUUID());
		});
		ServerTickEvents.END_SERVER_TICK.register(KistDB::tick);
		UseBlockCallback.EVENT.register(Tracker::onUseBlock);
		AttackBlockCallback.EVENT.register(Tracker::onAttackBlock);
		CommandRegistrationCallback.EVENT.register(KistCommands::register);
		LOG.info("KistDB geladen");
	}

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Tracker.tick(player, tick);
			if (tick % 10 == 0) {
				CraftButton.refresh(player);
			}
		}
		Beams.tick(server, tick);
		if (tick % 1200 == 0) {
			Store.saveDirty();
		}
	}
}
