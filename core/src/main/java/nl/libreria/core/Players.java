package nl.libreria.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Everybody who ever played here: their name, when they first came and when they were last here. For the profiles,
 * the Players list, "while you were away" and the size of the server goals. Players from before Libreria are found in
 * Minecraft's own files at start (their names in usercache.json).
 */
public final class Players {
	public static final class Person {
		public String name = "";
		/** How they were shown last (nickname), "" = their name. */
		public String shown = "";
		/** First seen and last seen (epoch ms; 0 = unknown). */
		public long first;
		public long last;
		/** When Libreria itself last saw them here (0 = not since this was kept): the only time that's sure. */
		public long seen;
	}

	static final class Data {
		public Map<UUID, Person> people = new HashMap<>();
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/players.json", Data.class, Data::new);

	/** One player as the lists show them. {@code online} is null when they aren't here now. */
	public record Known(UUID id, String name, String shown, long first, long last, @Nullable ServerPlayer online) {
		/** The nickname if they have one, otherwise the name. */
		public String label() {
			return this.shown.isEmpty() ? this.name : this.shown;
		}
	}

	private Players() {
	}

	private static Data data() {
		Data d = STORE.get();
		if (d.people == null) d.people = new HashMap<>();
		return d;
	}

	/** A player joined: remembered, and returns when they were here last (0 the first time). */
	static long onJoin(ServerPlayer p) {
		Person who = data().people.computeIfAbsent(p.getUUID(), k -> new Person());
		long before = who.last;
		long now = System.currentTimeMillis();
		who.name = p.getGameProfile().name();
		who.shown = shown(p);
		if (who.first == 0) who.first = now;
		who.last = now;
		who.seen = now;
		STORE.markDirty();
		return before;
	}

	static void onLeave(ServerPlayer p) {
		Person who = data().people.get(p.getUUID());
		if (who == null) return;
		who.last = System.currentTimeMillis();
		who.seen = who.last;
		who.shown = shown(p);
		STORE.markDirty();
	}

	/** Once a minute: who's here is "last seen" now (so a crash doesn't lose it). */
	static void tick(MinecraftServer server) {
		long now = System.currentTimeMillis();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Person who = data().people.get(p.getUUID());
			if (who == null) continue;
			who.last = now;
			who.seen = now;
			who.shown = shown(p);
		}
		STORE.markDirty();
	}

	private static String shown(ServerPlayer p) {
		String s = Nameplate.displayName(p).getString();
		return s.equals(p.getGameProfile().name()) ? "" : s;
	}

	/** Remembers a player who hasn't been online with Libreria (an import, or a test). */
	public static void note(UUID id, String name, long first, long last) {
		Person p = data().people.computeIfAbsent(id, k -> new Person());
		p.name = name;
		p.first = first;
		p.last = last;
		STORE.markDirty();
	}

	/** For the self-test: forgets someone again. */
	public static void forget(UUID id) {
		if (data().people.remove(id) != null) STORE.markDirty();
	}

	// ---------------------------------------------------------------- reading

	/** Everybody with a known name: who's here first (by name), then the most recently seen. */
	public static List<Known> all(MinecraftServer server) {
		List<Known> out = new ArrayList<>();
		for (Map.Entry<UUID, Person> e : data().people.entrySet()) {
			Known k = known(server, e.getKey(), e.getValue());
			if (k != null) out.add(k);
		}
		out.sort(Comparator.<Known>comparingInt(k -> k.online() != null ? 0 : 1)
				.thenComparing(k -> k.online() != null ? k.label().toLowerCase(Locale.ROOT) : "")
				.thenComparing(Comparator.comparingLong(Known::last).reversed()));
		return out;
	}

	public static @Nullable Known get(MinecraftServer server, UUID id) {
		Person p = data().people.get(id);
		return p == null ? null : known(server, id, p);
	}

	/** By name or nickname (any case). */
	public static @Nullable Known byName(MinecraftServer server, String name) {
		for (Known k : all(server)) {
			if (k.name().equalsIgnoreCase(name) || k.shown().equalsIgnoreCase(name)) return k;
		}
		return null;
	}

	private static @Nullable Known known(MinecraftServer server, UUID id, Person p) {
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		String name = online != null ? online.getGameProfile().name() : p.name;
		if (name == null || name.isEmpty()) return null;
		return new Known(id, name, online != null ? shown(online) : p.shown == null ? "" : p.shown, p.first, online != null
				? System.currentTimeMillis() : p.last, online);
	}

	/** How many players were here in the last {@code ms} milliseconds (who's online now counts too). */
	public static int activeWithin(MinecraftServer server, long ms) {
		long since = System.currentTimeMillis() - ms;
		int n = 0;
		for (Map.Entry<UUID, Person> e : data().people.entrySet()) {
			if (e.getValue().last >= since || server.getPlayerList().getPlayer(e.getKey()) != null) n++;
		}
		return n;
	}

	/** "3 days", "2 hours", "5 minutes": how long ago, in words. */
	public static String ago(long ms) {
		long s = Math.max(0, (System.currentTimeMillis() - ms) / 1000);
		if (s < 120) return "Just now";
		long m = s / 60, h = m / 60, d = h / 24;
		if (m < 60) return m + " minutes ago";
		if (h < 24) return h + (h == 1 ? " hour ago" : " hours ago");
		if (d == 1) return "Yesterday";
		if (d < 14) return d + " days ago";
		if (d < 60) return d / 7 + " weeks ago";
		if (d < 730) return d / 30 + " months ago";
		return d / 365 + " years ago";
	}

	// ---------------------------------------------------------------- at start

	/**
	 * Players from before Libreria: when they first played (from their advancement file) and their name (from
	 * Minecraft's usercache.json, or from the chronicle).
	 */
	static void seed(MinecraftServer server, @Nullable ChronicleHistory h) {
		Data d = data();
		Map<UUID, String> names = names(server);
		for (Chronicle.Entry e : Chronicle.data().entries) {
			if (e.who != null && e.name != null && !e.name.isEmpty()) names.putIfAbsent(e.who, e.name);
		}
		if (h != null) {
			for (Map.Entry<UUID, Long> e : h.seen.entrySet()) {
				Person p = d.people.computeIfAbsent(e.getKey(), k -> new Person());
				long t = e.getValue();
				if (t > 0 && (p.first == 0 || t < p.first)) p.first = t;
				// Last here: what Libreria saw itself; for someone it hasn't seen yet, the newest moment in their
				// advancements (they were here then, at least), or when they first came. Never the files' dates: those
				// change when the world is copied or backed up, or the server touches them, with nobody playing.
				if (p.seen > 0) {
					p.last = p.seen;
				} else {
					long newest = Math.max(h.newest.getOrDefault(e.getKey(), 0L), Math.max(t, 0));
					if (newest > 0) p.last = newest;
				}
			}
		}
		for (Map.Entry<UUID, Person> e : d.people.entrySet()) {
			if ((e.getValue().name == null || e.getValue().name.isEmpty()) && names.containsKey(e.getKey())) e.getValue().name = names.get(e.getKey());
		}
		STORE.markDirty();
	}

	/** Names from usercache.json (in the server's folder). */
	private static Map<UUID, String> names(MinecraftServer server) {
		Map<UUID, String> out = new HashMap<>();
		Path world = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize();
		for (Path f : new Path[] {Path.of("usercache.json"), world.getParent() == null ? null : world.getParent().resolve("usercache.json")}) {
			if (f == null || !Files.isRegularFile(f)) continue;
			try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
				JsonElement json = JsonParser.parseReader(r);
				if (!json.isJsonArray()) continue;
				for (JsonElement el : json.getAsJsonArray()) {
					if (!el.isJsonObject()) continue;
					var o = el.getAsJsonObject();
					try {
						out.putIfAbsent(UUID.fromString(o.get("uuid").getAsString()), o.get("name").getAsString());
					} catch (RuntimeException ignored) {
						// a broken line
					}
				}
			} catch (Exception e) {
				Libreria.LOG.warn("Could not read {}", f, e);
			}
			break;
		}
		return out;
	}
}
