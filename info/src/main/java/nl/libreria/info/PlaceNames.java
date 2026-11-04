package nl.libreria.info;

import java.util.Map;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Names for the places you find: every village, outpost, temple or ancient city gets its own name, the same for
 * everybody and every time (it comes from the world seed and where the place is). Villages sound like the land they
 * stand in: Eikendal on the plains, Duinloo in the desert, IJshof in the snow.
 */
final class PlaceNames {
	/** What a structure is called (English, like the rest of the interface) and which names it gets. */
	record Kind(String label, @Nullable String[] prefixes, @Nullable String[] suffixes) {
		boolean named() {
			return this.prefixes != null;
		}
	}

	private static final String[] VILLAGE = {"dal", "hoek", "veld", "dorp", "brug", "oord", "beek", "hof", "rade", "wolde", "horst",
			"loo", "stede", "haven", "ven", "burg", "akker", "weide"};

	private static Kind village(String... prefixes) {
		return new Kind("Village", prefixes, VILLAGE);
	}

	private static Kind named(String label, String[] prefixes, String... suffixes) {
		return new Kind(label, prefixes, suffixes);
	}

	private static Kind plain(String label) {
		return new Kind(label, null, null);
	}

	/** Structure id (without "minecraft:") → kind. Structures that aren't here (mineshafts, buried treasure) get no title. */
	private static final Map<String, Kind> KINDS = Map.ofEntries(
			Map.entry("village_plains", village("Eiken", "Linden", "Klaver", "Molen", "Heide", "Berken", "Wilgen", "Hazel", "Vlas", "Rogge", "Appel", "Koren")),
			Map.entry("village_desert", village("Zand", "Zonne", "Duin", "Gloed", "Amber", "Okker", "Dadel", "Stof")),
			Map.entry("village_savanna", village("Goud", "Leeuw", "Gras", "Kraal", "Stro", "Hooi")),
			Map.entry("village_snowy", village("IJs", "Sneeuw", "Vorst", "Rijp", "Hagel", "Wit", "Kristal")),
			Map.entry("village_taiga", village("Dennen", "Spar", "Mos", "Wolfs", "Varen", "Beren", "Lork")),
			Map.entry("pillager_outpost", named("Pillager Outpost", new String[] {"Raven", "Kraaien", "Wolfs", "Bijl", "Zwart", "Gier"}, "wacht", "horst", "schans")),
			Map.entry("ancient_city", named("Ancient City", new String[] {"Diep", "Duister", "Schim", "Stil", "Echo", "Zwijg"}, "moer", "gaard", "holt", "burcht", "kuil")),
			Map.entry("trial_chambers", named("Trial Chambers", new String[] {"Koper", "Wind", "Proef", "Kluis", "Tocht"}, "gewelf", "hallen", "kamers")),
			Map.entry("mansion", named("Woodland Mansion", new String[] {"Raven", "Donker", "Uilen", "Schemer"}, "hof", "slot", "stein")),
			Map.entry("monument", named("Ocean Monument", new String[] {"Zee", "Prisma", "Diep", "Golf", "Parel"}, "burcht", "tempel", "rots")),
			Map.entry("stronghold", named("Stronghold", new String[] {"Einder", "Oog", "Steen", "Grens"}, "burcht", "vest", "waard")),
			Map.entry("fortress", named("Nether Fortress", new String[] {"Vuur", "As", "Gloed", "Bloed", "Sintel"}, "vest", "burcht", "bolwerk")),
			Map.entry("bastion_remnant", named("Bastion", new String[] {"Goud", "Varkens", "Zwart", "Sintel"}, "bolwerk", "schans", "wal")),
			Map.entry("end_city", named("End City", new String[] {"Ster", "Hemel", "Leegte", "Purper"}, "spits", "torens", "stad")),
			Map.entry("desert_pyramid", named("Desert Temple", new String[] {"Zon", "Farao", "Scarabee", "Zand"}, "graf", "tempel", "piramide")),
			Map.entry("jungle_pyramid", named("Jungle Temple", new String[] {"Jade", "Slangen", "Liaan", "Mos"}, "tempel", "heiligdom", "graf")),
			Map.entry("trail_ruins", named("Trail Ruins", new String[] {"Leem", "Klei", "Scherf", "Stof"}, "veld", "heuvel", "spoor")),
			Map.entry("igloo", plain("Igloo")),
			Map.entry("swamp_hut", plain("Witch Hut")));

	private PlaceNames() {
	}

	/** The kind of a structure id like "village_plains" or "ruined_portal_desert", or null for no title. */
	static @Nullable Kind kind(String path) {
		Kind k = KINDS.get(path);
		if (k != null) return k;
		if (path.startsWith("ruined_portal")) return plain("Ruined Portal");
		if (path.startsWith("shipwreck")) return plain("Shipwreck");
		if (path.startsWith("ocean_ruin")) return named("Ocean Ruins", new String[] {"Zee", "Golf", "Wier", "Koraal"}, "stenen", "resten", "kust");
		return null;
	}

	/** The name of one place: always the same for the same seed. */
	static String name(Kind kind, long seed) {
		if (!kind.named()) return kind.label();
		Random r = new Random(seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L);
		String prefix = kind.prefixes()[r.nextInt(kind.prefixes().length)];
		String suffix = kind.suffixes()[r.nextInt(kind.suffixes().length)];
		// "Zand" + "dal" reads badly where the same letter meets: pick another ending
		for (int i = 0; i < 6 && Character.toLowerCase(prefix.charAt(prefix.length() - 1)) == suffix.charAt(0); i++) {
			suffix = kind.suffixes()[r.nextInt(kind.suffixes().length)];
		}
		return prefix + suffix;
	}
}
