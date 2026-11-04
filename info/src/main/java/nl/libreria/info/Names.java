package nl.libreria.info;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import nl.libreria.core.WorldStore;
import org.jetbrains.annotations.Nullable;

/**
 * The world's name book: which place, area and villager has which name, so no name is ever given twice. A place's
 * name is picked the first time anybody comes there, from its own seed (so it's the same name it would always get),
 * skipping names that are taken. Kept in {@code <world>/libreria/names.json}.
 */
final class Names {
	/** A named stretch of one biome: the name reaches {@code radius} blocks around where it was first found. */
	public static final class Area {
		public String dim = "minecraft:overworld";
		public String biome = "";
		public int x, z, radius;
		public String name = "";
	}

	public static final class Data {
		/** Place key → name (villages, temples…). */
		public Map<String, String> places = new HashMap<>();
		public List<Area> areas = new ArrayList<>();
		/** Villager → name, for the villagers that got theirs from us. */
		public Map<UUID, String> villagers = new HashMap<>();
		/** Every name of a place or area in use (lower case), to never give one twice. */
		public Set<String> taken = new HashSet<>();
		/** Every villager name in use (lower case). */
		public Set<String> villagersTaken = new HashSet<>();
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/names.json", Data.class, Data::new);

	private Names() {
	}

	static Data data() {
		Data d = STORE.get();
		if (d.places == null) d.places = new HashMap<>();
		if (d.areas == null) d.areas = new ArrayList<>();
		if (d.villagers == null) d.villagers = new HashMap<>();
		if (d.taken == null) d.taken = new HashSet<>();
		if (d.villagersTaken == null) d.villagersTaken = new HashSet<>();
		return d;
	}

	/** Tries names from the seed until one is free; {@code gen} gets the random source and the attempt number. */
	private static String fresh(Set<String> taken, long seed, BiFunction<Random, Integer, String> gen) {
		Random r = new Random(seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L);
		String name = null;
		for (int attempt = 0; attempt < 400; attempt++) {
			name = gen.apply(r, attempt);
			if (!taken.contains(name.toLowerCase(java.util.Locale.ROOT))) break;
		}
		// After 400 tries (only in a world with tens of thousands of names) a number makes it unique
		String base = name;
		for (int n = 2; taken.contains(name.toLowerCase(java.util.Locale.ROOT)); n++) name = base + " " + roman(n);
		taken.add(name.toLowerCase(java.util.Locale.ROOT));
		STORE.markDirty();
		return name;
	}

	/** The name of a structure (the same every time; picked the first time). */
	static String place(String key, PlaceNames.Kind kind, long seed) {
		if (!kind.named()) return kind.label();
		Data d = data();
		String name = d.places.get(key);
		if (name == null) {
			name = fresh(d.taken, seed, (r, attempt) -> PlaceNames.structure(kind, r, attempt));
			d.places.put(key, name);
		}
		return name;
	}

	/** The area of {@code biome} you're in: an existing one nearby, or a new one with a fresh name. */
	static Area area(String dim, String biome, int x, int z, long seed) {
		Data d = data();
		Area best = null;
		double bestD = Double.MAX_VALUE;
		for (Area a : d.areas) {
			if (!a.dim.equals(dim) || !a.biome.equals(biome)) continue;
			double dx = a.x - x, dz = a.z - z, dd = dx * dx + dz * dz;
			if (dd <= (double) a.radius * a.radius && dd < bestD) {
				best = a;
				bestD = dd;
			}
		}
		if (best != null) return best;
		String path = biome.substring(biome.indexOf(':') + 1);
		PlaceNames.Land land = PlaceNames.land(path);
		Area a = new Area();
		a.dim = dim;
		a.biome = biome;
		a.x = x;
		a.z = z;
		a.radius = land.radius();
		a.name = fresh(d.taken, seed ^ ((long) x << 32) ^ (z & 0xFFFFFFFFL) ^ biome.hashCode(), (r, attempt) -> PlaceNames.area(land, r, attempt));
		d.areas.add(a);
		return a;
	}

	/** A new villager name, not used by any other villager. */
	static String villager(UUID id) {
		Data d = data();
		String have = d.villagers.get(id);
		if (have != null) return have;
		String name = fresh(d.villagersTaken, id.getMostSignificantBits() ^ id.getLeastSignificantBits(), (r, attempt) -> PlaceNames.villager(r));
		d.villagers.put(id, name);
		return name;
	}

	/** A named villager died: its name is free again. */
	static void releaseVillager(UUID id) {
		Data d = data();
		String name = d.villagers.remove(id);
		if (name != null) {
			d.villagersTaken.remove(name.toLowerCase(java.util.Locale.ROOT));
			STORE.markDirty();
		}
	}

	static @Nullable String villagerName(UUID id) {
		return data().villagers.get(id);
	}

	private static String roman(int n) {
		String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n < r.length ? r[n] : Integer.toString(n);
	}
}
