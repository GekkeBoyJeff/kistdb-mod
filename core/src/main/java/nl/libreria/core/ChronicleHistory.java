package nl.libreria.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * What happened before the chronicle was there (or while it wasn't loaded), read from the players' own files: the
 * advancements (with the time each was done), and the statistics (bosses slain, deaths). So the chronicle doesn't call
 * somebody "the first to find diamonds" when somebody else found them a month earlier.
 */
final class ChronicleHistory {
	/** Per story advancement ("story/mine_diamond"): who did it, and when (epoch ms, -1 when the file doesn't say). */
	final Map<String, Map<UUID, Long>> done = new HashMap<>();
	/** Everybody with a file, and the earliest moment in it (about when they first joined; -1 when unknown). */
	final Map<UUID, Long> seen = new HashMap<>();
	/** Per boss ("wither"): who slew one. */
	final Map<String, Set<UUID>> slain = new HashMap<>();
	/** Who has died at least once. */
	final Set<UUID> died = new HashSet<>();
	/** How many player files were read. */
	int files;

	private static final List<DateTimeFormatter> TIMES = List.of(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT),
			DateTimeFormatter.ISO_OFFSET_DATE_TIME);

	/** Reads {@code <world>/players/advancements} and {@code /stats} (26.x), or {@code <world>/advancements} and {@code /stats}. */
	static ChronicleHistory read(Path world, Set<String> story, Set<String> bosses) {
		ChronicleHistory h = new ChronicleHistory();
		for (Path dir : List.of(world.resolve("players").resolve("advancements"), world.resolve("advancements"))) h.advancements(dir, story);
		for (Path dir : List.of(world.resolve("players").resolve("stats"), world.resolve("stats"))) h.stats(dir, bosses);
		return h;
	}

	private void advancements(Path dir, Set<String> story) {
		each(dir, (who, json) -> {
			long earliest = Long.MAX_VALUE;
			for (Map.Entry<String, JsonElement> e : json.entrySet()) {
				if (!e.getValue().isJsonObject()) continue;
				JsonObject adv = e.getValue().getAsJsonObject();
				long latest = -1;
				if (adv.get("criteria") instanceof JsonObject criteria) {
					for (Map.Entry<String, JsonElement> c : criteria.entrySet()) {
						long t = time(c.getValue());
						if (t < 0) continue;
						earliest = Math.min(earliest, t);
						latest = Math.max(latest, t);
					}
				}
				String id = e.getKey().startsWith("minecraft:") ? e.getKey().substring("minecraft:".length()) : null;
				boolean isDone = adv.get("done") != null && adv.get("done").isJsonPrimitive() && adv.get("done").getAsBoolean();
				// Done = when the last criterion was met
				if (id != null && isDone && story.contains(id)) this.done.computeIfAbsent(id, k -> new HashMap<>()).merge(who, latest, ChronicleHistory::min);
			}
			this.seen.merge(who, earliest == Long.MAX_VALUE ? -1 : earliest, ChronicleHistory::min);
		});
	}

	private void stats(Path dir, Set<String> bosses) {
		each(dir, (who, json) -> {
			this.seen.putIfAbsent(who, -1L);
			if (!(json.get("stats") instanceof JsonObject stats)) return;
			if (stats.get("minecraft:killed") instanceof JsonObject killed) {
				for (String boss : bosses) {
					JsonElement n = killed.get("minecraft:" + boss);
					if (n != null && n.isJsonPrimitive() && n.getAsInt() > 0) this.slain.computeIfAbsent(boss, k -> new HashSet<>()).add(who);
				}
			}
			if (stats.get("minecraft:custom") instanceof JsonObject custom) {
				JsonElement n = custom.get("minecraft:deaths");
				if (n != null && n.isJsonPrimitive() && n.getAsInt() > 0) this.died.add(who);
			}
		});
	}

	/** Somebody other than {@code who} did it more than a minute before {@code at}. */
	static boolean earlier(Map<UUID, Long> times, UUID who, long at) {
		for (Map.Entry<UUID, Long> t : times.entrySet()) {
			if (!t.getKey().equals(who) && t.getValue() >= 0 && t.getValue() < at - 60_000L) return true;
		}
		return false;
	}

	private interface Reading {
		void read(UUID who, JsonObject json);
	}

	private void each(Path dir, Reading reading) {
		if (!Files.isDirectory(dir)) return;
		try (Stream<Path> list = Files.list(dir)) {
			for (Path f : (Iterable<Path>) list::iterator) {
				String name = f.getFileName().toString();
				if (!name.endsWith(".json")) continue;
				UUID who;
				try {
					who = UUID.fromString(name.substring(0, name.length() - 5));
				} catch (IllegalArgumentException e) {
					continue;
				}
				try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
					JsonElement json = JsonParser.parseReader(r);
					if (json.isJsonObject()) {
						reading.read(who, json.getAsJsonObject());
						this.files++;
					}
				} catch (IOException | RuntimeException e) {
					Libreria.LOG.warn("Chronicle: could not read {}", f, e);
				}
			}
		} catch (IOException e) {
			Libreria.LOG.warn("Chronicle: could not list {}", dir, e);
		}
	}

	private static long time(JsonElement e) {
		if (e == null || !e.isJsonPrimitive()) return -1;
		String s = e.getAsString();
		for (DateTimeFormatter f : TIMES) {
			try {
				return OffsetDateTime.parse(s, f).toInstant().toEpochMilli();
			} catch (RuntimeException ignored) {
				// the next format
			}
		}
		return -1;
	}

	/** The earliest of two times, where -1 (unknown) only wins when there's nothing else. */
	private static Long min(Long a, Long b) {
		if (a < 0) return b;
		if (b < 0) return a;
		return Math.min(a, b);
	}
}
