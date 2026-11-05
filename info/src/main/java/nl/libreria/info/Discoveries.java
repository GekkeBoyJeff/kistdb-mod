package nl.libreria.info;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import nl.libreria.core.Nameplate;
import nl.libreria.core.Players;
import nl.libreria.core.WorldStore;
import org.jetbrains.annotations.Nullable;

/**
 * Who found each place first, for the whole server: a village, a temple, a peak, a stretch of forest. The first one to
 * walk in is its discoverer; everyone after them sees "found by Anna". What players had found before this existed
 * counts too: whoever wrote it in their journal first.
 */
final class Discoveries {
	static final class First {
		public @Nullable UUID who;
		public String name = "";
		public long at;
	}

	static final class Data {
		public Map<String, First> firsts = new HashMap<>();
		/** The journals have been read once (for what was found before this existed). */
		public boolean seeded;
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/discoveries.json", Data.class, Data::new);

	private Discoveries() {
	}

	static Data data() {
		Data d = STORE.get();
		if (d.firsts == null) d.firsts = new HashMap<>();
		return d;
	}

	static @Nullable First first(String key) {
		return data().firsts.get(key);
	}

	/** You are here: the first one if nobody was before. True if that's you, right now. */
	static boolean arrive(ServerPlayer p, String key) {
		Data d = data();
		if (d.firsts.containsKey(key)) return false;
		First f = new First();
		f.who = p.getUUID();
		f.name = Nameplate.displayName(p).getString();
		f.at = System.currentTimeMillis();
		d.firsts.put(key, f);
		STORE.markDirty();
		return true;
	}

	/** Who found it first, as others see the name now (their nickname, if they have one). */
	static String name(MinecraftServer server, First f) {
		if (f.who != null) {
			ServerPlayer online = server.getPlayerList().getPlayer(f.who);
			if (online != null) return Nameplate.displayName(online).getString();
			Players.Known k = Players.get(server, f.who);
			if (k != null) return k.label();
		}
		return f.name;
	}

	/** Once: every journal on disk, the earliest finder of each place becomes its discoverer. */
	static void seed(MinecraftServer server) {
		Data d = data();
		if (d.seeded) return;
		d.seeded = true;
		STORE.markDirty();
		Path dir = server.getWorldPath(LevelResource.ROOT).resolve("libreria/journal");
		if (!Files.isDirectory(dir)) return;
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : (Iterable<Path>) files::iterator) {
				String n = file.getFileName().toString();
				if (!n.endsWith(".json")) continue;
				UUID id;
				try {
					id = UUID.fromString(n.substring(0, n.length() - ".json".length()));
				} catch (IllegalArgumentException e) {
					continue;
				}
				Players.Known k = Players.get(server, id);
				for (Places.Place pl : Places.STORE.get(id).places) {
					First f = d.firsts.get(pl.key);
					if (f != null && f.at <= pl.found) continue;
					First n2 = new First();
					n2.who = id;
					n2.name = k != null ? k.label() : "someone";
					n2.at = pl.found;
					d.firsts.put(pl.key, n2);
				}
			}
		} catch (IOException e) {
			nl.libreria.core.Libreria.LOG.warn("Could not read the journals for who found what first", e);
		}
		nl.libreria.core.Libreria.LOG.info("Discoveries: {} places with a first finder", d.firsts.size());
	}

	/** How many places this player found before anyone else. */
	static int firstsOf(UUID who) {
		int n = 0;
		for (First f : data().firsts.values()) if (who.equals(f.who)) n++;
		return n;
	}

	/** For the self-test. */
	static void forget(String key) {
		data().firsts.remove(key);
		STORE.markDirty();
	}
}
