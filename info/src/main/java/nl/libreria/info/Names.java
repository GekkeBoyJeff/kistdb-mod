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
		/** Every name a weapon earned (lower case); a weapon's name is never given again. */
		public Set<String> weaponsTaken = new HashSet<>();
		/** Every pet name in use (lower case). */
		public Set<String> petsTaken = new HashSet<>();
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
		if (d.weaponsTaken == null) d.weaponsTaken = new HashSet<>();
		if (d.petsTaken == null) d.petsTaken = new HashSet<>();
		return d;
	}

	/**
	 * Tries names from the seed until one is free; {@code gen} gets the random source and the attempt number. This
	 * never fails and never hangs: when the pools run dry (only in a world with tens of thousands of names) it numbers
	 * the last try ("Ashford II", "Ashford III"…), and if a generator throws or gives nothing it uses {@code fallback}.
	 */
	static String fresh(Set<String> taken, long seed, String fallback, BiFunction<Random, Integer, String> gen) {
		Random r = new Random(seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L);
		String name = null;
		for (int attempt = 0; attempt < 400; attempt++) {
			String tried;
			try {
				tried = gen.apply(r, attempt);
			} catch (RuntimeException e) {
				nl.libreria.core.Libreria.LOG.warn("Name generator failed; using \"{}\"", fallback, e);
				break;
			}
			if (tried == null || tried.isBlank()) break;
			name = tried.strip();
			if (!taken.contains(key(name))) break;
		}
		if (name == null) name = fallback;
		String base = name;
		for (int n = 2; taken.contains(key(name)); n++) name = base + " " + roman(n);
		taken.add(key(name));
		STORE.markDirty();
		return name;
	}

	private static String key(String name) {
		return name.toLowerCase(java.util.Locale.ROOT);
	}

	/** The name of a structure (the same every time; picked the first time). */
	static String place(String key, PlaceNames.Kind kind, long seed) {
		if (!kind.named()) return kind.label();
		Data d = data();
		String name = d.places.get(key);
		if (name == null) {
			name = fresh(d.taken, seed, kind.label(), (r, attempt) -> PlaceNames.structure(kind, r, attempt));
			d.places.put(key, name);
		}
		return name;
	}

	/** The area of {@code biome} you're in: an existing one nearby, or a new one with a fresh name. */
	static Area area(String dim, String biome, int x, int z, long seed) {
		String path = biome.substring(biome.indexOf(':') + 1);
		PlaceNames.Land land = PlaceNames.land(path);
		return nearby(dim, biome, x, z, land.radius(), seed ^ biome.hashCode(), title(path), (r, attempt) -> PlaceNames.area(land, r, attempt));
	}

	/** The peak, cavern or lake you're at: one already named nearby, or a new one. */
	static Area landmark(String dim, PlaceNames.Landmark type, int x, int z, long seed) {
		return nearby(dim, "libreria:" + type.name().toLowerCase(java.util.Locale.ROOT), x, z, type.radius, seed ^ type.ordinal() * 7919L,
				type.label, (r, attempt) -> PlaceNames.landmark(type, r, attempt));
	}

	private static Area nearby(String dim, String key, int x, int z, int radius, long seed, String fallback,
			BiFunction<Random, Integer, String> gen) {
		Data d = data();
		Area best = null;
		double bestD = Double.MAX_VALUE;
		for (Area a : d.areas) {
			if (!a.dim.equals(dim) || !a.biome.equals(key)) continue;
			double dx = a.x - x, dz = a.z - z, dd = dx * dx + dz * dz;
			if (dd <= (double) a.radius * a.radius && dd < bestD) {
				best = a;
				bestD = dd;
			}
		}
		if (best != null) return best;
		Area a = new Area();
		a.dim = dim;
		a.biome = key;
		a.x = x;
		a.z = z;
		a.radius = radius;
		a.name = fresh(d.taken, seed ^ ((long) x << 32) ^ (z & 0xFFFFFFFFL), fallback, gen);
		d.areas.add(a);
		return a;
	}

	/** A new villager name, not used by any other villager. */
	static String villager(UUID id) {
		Data d = data();
		String have = d.villagers.get(id);
		if (have != null) return have;
		String name = fresh(d.villagersTaken, id.getMostSignificantBits() ^ id.getLeastSignificantBits(), "Villager", (r, attempt) -> PlaceNames.villager(r));
		d.villagers.put(id, name);
		return name;
	}

	/** A name for a weapon that earned one (never given twice, also not after the weapon is gone). */
	static String weapon(UUID id, Weapons.Kind kind) {
		return fresh(data().weaponsTaken, id.getMostSignificantBits() ^ id.getLeastSignificantBits(), "Nameless",
				(r, attempt) -> Weapons.name(kind, r, attempt));
	}

	/** A name for a pet nobody else's pet has. */
	static String pet(UUID id) {
		return fresh(data().petsTaken, id.getMostSignificantBits() * 31 + id.getLeastSignificantBits(), "Buddy",
				(r, attempt) -> PetNames.name(r, attempt));
	}

	/** A pet died: its name is free again. */
	static void releasePet(String name) {
		if (data().petsTaken.remove(name.toLowerCase(java.util.Locale.ROOT))) STORE.markDirty();
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

	/** "dark_forest" → "Dark Forest". */
	private static String title(String path) {
		StringBuilder b = new StringBuilder();
		for (String w : path.split("_")) {
			if (w.isEmpty()) continue;
			if (!b.isEmpty()) b.append(' ');
			b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return b.isEmpty() ? "The Wilds" : b.toString();
	}

	private static String roman(int n) {
		String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV",
				"XVI", "XVII", "XVIII", "XIX", "XX"};
		return n < r.length ? r[n] : Integer.toString(n);
	}
}
