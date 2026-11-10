package nl.libreria.teleports;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import nl.libreria.core.Libreria;
import nl.libreria.core.WorldStore;
import org.jetbrains.annotations.Nullable;

/**
 * Homes from the teleport mod that was here before (Teleport Commands: {@code <world>/TeleportCommands/storage.json}, with
 * {@code {"Players":[{"UUID","DefaultHome","Homes":[{"name","x","y","z","world"}]}]}}), taken over once at start, so
 * nobody loses a home. Any JSON file of that shape in the world folder or the config folder counts. A home whose name
 * a player already has here is left alone; the default home becomes their /home. Homes over the limit stay (they just
 * can't set new ones until they have room). Each file is read once: from then on Libreria's own files are what counts
 * (a home deleted or a /home changed here never comes back from the old file).
 */
final class HomeImport {
	public static final class Done {
		/** The files already taken over (once each: after that the homes live in Libreria's own files). */
		public Set<String> files = new HashSet<>();
	}

	static final WorldStore<Done> DONE = WorldStore.create("libreria/teleports_imported.json", Done.class, Done::new);

	/** One player's homes in the old file. */
	record OldPlayer(UUID id, String defaultHome, List<TeleportsMod.Home> homes) {
	}

	private HomeImport() {
	}

	/** At start: every file of the old shape that wasn't taken over yet. */
	static void run(MinecraftServer server) {
		Path world = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize();
		List<Path> candidates = candidates(world, FabricLoader.getInstance().getConfigDir());
		Done done = DONE.get();
		if (done.files == null) done.files = new HashSet<>();
		Set<Path> seen = new HashSet<>();
		for (Path f : candidates) {
			if (!seen.add(f.toAbsolutePath().normalize()) || !Files.isRegularFile(f)) continue;
			String key = f.toAbsolutePath().normalize().toString();
			if (done.files.contains(key)) continue;
			List<OldPlayer> players = read(f);
			if (players == null) continue;
			int homes = 0;
			for (OldPlayer o : players) homes += apply(o);
			done.files.add(key);
			DONE.markDirty();
			Libreria.LOG.info("Teleports: took over {} homes of {} players from {}", homes, players.size(), f);
		}
	}

	/**
	 * Where the old homes can be: Teleport Commands keeps them in {@code <world>/TeleportCommands/storage.json}; any
	 * JSON file in the world folder and the folders right in it (not the game's own: stats, advancements, data
	 * packs) and in the config folder is looked at too.
	 */
	static List<Path> candidates(Path world, Path config) {
		List<Path> out = new ArrayList<>();
		out.add(world.resolve("TeleportCommands").resolve("storage.json"));
		// a newer Teleport Commands moves the old file aside when it upgrades
		out.add(world.resolve("TeleportCommands").resolve("storage.json.bak"));
		out.add(world.resolve("TeleportCommands.json"));
		addJson(out, world, 2);
		addJson(out, world.resolve("data"), 2);
		addJson(out, config, 2);
		return out;
	}

	private static final Set<String> SKIP = Set.of("advancements", "stats", "datapacks", "playerdata", "region", "entities", "poi", "libreria");

	private static void addJson(List<Path> out, Path dir, int depth) {
		if (!Files.isDirectory(dir)) return;
		try (Stream<Path> s = Files.walk(dir, depth)) {
			s.filter(p -> p.getFileName().toString().endsWith(".json") && !p.toString().contains("libreria")
					&& (p.getParent() == null || !SKIP.contains(p.getParent().getFileName().toString()))).forEach(out::add);
		} catch (Exception ignored) {
			// a folder we can't read: fine
		}
	}

	/** The players and homes in a file of the old shape, or null when it isn't one. */
	static @Nullable List<OldPlayer> read(Path f) {
		JsonElement json;
		try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
			json = JsonParser.parseReader(r);
		} catch (Exception e) {
			return null;
		}
		if (!(json instanceof JsonObject root) || !(root.get("Players") instanceof JsonArray players)) return null;
		List<OldPlayer> out = new ArrayList<>();
		for (JsonElement el : players) {
			if (!(el instanceof JsonObject p) || !(p.get("Homes") instanceof JsonArray homes)) continue;
			UUID id;
			try {
				id = UUID.fromString(p.get("UUID").getAsString());
			} catch (RuntimeException e) {
				continue;
			}
			String def = p.has("DefaultHome") && p.get("DefaultHome").isJsonPrimitive() ? p.get("DefaultHome").getAsString() : "";
			List<TeleportsMod.Home> list = new ArrayList<>();
			for (JsonElement he : homes) {
				if (!(he instanceof JsonObject h)) continue;
				try {
					TeleportsMod.Home home = new TeleportsMod.Home();
					home.name = h.get("name").getAsString();
					String world = h.has("world") ? h.get("world").getAsString() : "minecraft:overworld";
					home.dim = world.contains(":") ? world : "minecraft:" + world;
					// whole numbers are a block: stand in its middle
					home.x = coordinate(h, "x");
					home.y = h.get("y").getAsDouble();
					home.z = coordinate(h, "z");
					home.yaw = h.has("yaw") ? h.get("yaw").getAsFloat() : 0;
					home.pitch = h.has("pitch") ? h.get("pitch").getAsFloat() : 0;
					home.set = System.currentTimeMillis();
					if (Homes.clean(home.name) != null) {
						home.name = Homes.clean(home.name);
						list.add(home);
					}
				} catch (RuntimeException ignored) {
					// a broken home: skipped
				}
			}
			out.add(new OldPlayer(id, def, list));
		}
		return out;
	}

	private static double coordinate(JsonObject h, String key) {
		double v = h.get(key).getAsDouble();
		return v == Math.floor(v) ? v + 0.5 : v;
	}

	/** Adds a player's old homes (names they have already stay theirs); returns how many came in. */
	static int apply(OldPlayer o) {
		TeleportsMod.Data d = TeleportsMod.PLAYERS.get(o.id());
		int n = 0;
		for (TeleportsMod.Home h : o.homes()) {
			if (Homes.find(d, h.name) != null) continue;
			d.homes.add(h);
			n++;
		}
		if (n > 0) {
			TeleportsMod.Home def = o.defaultHome().isEmpty() ? null : Homes.find(d, o.defaultHome());
			if (def != null && (d.favourite.isEmpty() || d.homes.size() == n)) d.favourite = def.name;
			TeleportsMod.PLAYERS.markDirty(o.id());
		}
		return n;
	}
}
