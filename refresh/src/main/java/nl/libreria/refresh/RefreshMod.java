package nl.libreria.refresh;

import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Items;
import nl.libreria.core.Module;
import nl.libreria.core.Notify;
import nl.libreria.core.ServerSetting;
import nl.libreria.core.Ui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * World refresh (an addon for operators): make parts of the worlds anew without touching the bases. See {@link Plan}
 * for what stays, {@link MapScreen} for the map, {@link Restart} and {@link Layout} for what happens at the restart.
 */
public final class RefreshMod implements ModInitializer {
	static final Logger LOG = LoggerFactory.getLogger("Libreria");

	static final Module MODULE = Module.register("refresh", "World refresh", Items.RECOVERY_COMPASS,
			"Make parts of the worlds anew, without the bases: a map", "of every chunk, what stays and what goes; at the next",
			"restart the rest comes back fresh, its structures elsewhere.").adminPage(RefreshCanvas::open);
	static final ServerSetting.Number NEAR = MODULE.serverNumber("near", "Stays if people were near", Items.CLOCK, 10, 0, 600, 5, "minutes",
			"Minecraft counts per chunk how long someone was", "near it. Where people spent this long, it stays.");
	static final ServerSetting.Number ROOM = MODULE.serverNumber("room", "Room around claims", Items.FILLED_MAP, 2, 0, 16, 1, "chunks",
			"This many chunks around every claim stay too.");
	static final ServerSetting.Toggle MOVE = MODULE.serverToggle("structures", "Structures move", Items.COMPASS, true,
			"What's made anew gets its structures in new places.", "Strongholds stay where they are.");

	@Override
	public void onInitialize() {
		// Before the worlds open: a planned refresh happens, and the worlds learn their layouts
		ServerLifecycleEvents.SERVER_STARTING.register(Restart::apply);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Scan.start(server);
			done(server);
			Restart.tellGone(server);
			// Whatever was planned happened at this start: nothing is planned now
			if (Plan.STORE.get().planned) {
				Plan.STORE.get().planned = false;
				Plan.STORE.markDirty();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			MapScreen.closeAll();
			Scan.stop(server);
			// The list of what goes, with the land as it is now (claims made since, time spent since): never more than planned
			if (Plan.STORE.get().planned) Restart.write(server, true);
		});
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> Scan.loaded(level, chunk));
		ServerChunkEvents.CHUNK_UNLOAD.register(Scan::unloaded);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Scan.tick(server);
			MapScreen.tick(server);
		});
		ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> server.execute(() -> {
			Plan.Data d = Plan.STORE.get();
			if (Ui.isOp(h.player) && d.planned) {
				Notify.whisper(h.player, Ui.text("A world refresh is planned for the next restart (Server settings › World refresh)", ChatFormatting.GOLD));
			}
		}));
		ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> MapScreen.close(h.player));
		RefreshTests.register();
	}

	/** After a refresh: noted for the operators, and nothing planned any more. */
	private static void done(net.minecraft.server.MinecraftServer server) {
		Path f = Layout.dir(Scan.root(server)).resolve("done.txt");
		if (!Files.isRegularFile(f)) return;
		try {
			String[] lines = Files.readString(f).split("\n");
			Plan.Data d = Plan.STORE.get();
			d.planned = false;
			d.lastDone = Long.parseLong(lines[0].trim());
			StringBuilder sb = new StringBuilder();
			for (int i = 1; i < lines.length; i++) {
				String[] p = lines[i].split("\t");
				if (p.length < 2) continue;
				Plan.Dim dim = Plan.dim(p[0]);
				dim.on = false;
				dim.letGo.clear();
				if (sb.length() > 0) sb.append(", ");
				sb.append(p[0]).append(": ").append(p[1]);
			}
			d.lastSummary = sb.toString();
			Plan.STORE.markDirty();
			Files.delete(f);
			LOG.info("World refresh done: {}", d.lastSummary);
		} catch (Exception e) {
			LOG.warn("World refresh: can't read {}: {}", f, e.toString());
		}
	}
}
