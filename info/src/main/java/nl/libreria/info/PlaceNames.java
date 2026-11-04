package nl.libreria.info;

import java.util.Map;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

/**
 * The words places are named from. Every village and temple, and every stretch of forest, desert or sea, gets an
 * English name built from these lists (Oakhaven, Ravenford, the Whispering Woods, Ashglen Dunes…); {@link Names}
 * makes sure no name is used twice. There are thousands of combinations per kind, enough for a whole world.
 */
final class PlaceNames {
	/** What a structure is called, and which word list its names come from ({@code null}: no own name). */
	record Kind(String label, @Nullable String[] stems, @Nullable String[] endings) {
		boolean named() {
			return this.stems != null;
		}
	}

	// ---------------------------------------------------------------- words

	/** First halves of names: nature, colours, animals, weather. */
	static final String[] STEMS = {"Ash", "Amber", "Alder", "Apple", "Aspen", "Bay", "Bear", "Birch", "Black", "Bramble", "Briar",
			"Bright", "Brook", "Cedar", "Clear", "Cliff", "Clover", "Cold", "Copper", "Crow", "Dawn", "Deer", "Dew", "Dove", "Dusk",
			"Eagle", "East", "Elder", "Elm", "Ember", "Fair", "Falcon", "Fern", "Fox", "Frost", "Glen", "Gold", "Gray", "Green",
			"Hare", "Hart", "Hawk", "Hazel", "Heather", "High", "Holly", "Honey", "Iron", "Ivy", "Juniper", "Kings", "Lark", "Linden",
			"Long", "Maple", "Marsh", "Meadow", "Mill", "Mist", "Moon", "Moss", "North", "Oak", "Old", "Owl", "Pine", "Plum",
			"Queens", "Rain", "Raven", "Red", "Reed", "River", "Rose", "Rowan", "Rush", "Sage", "Salt", "Silver", "Sky", "Snow",
			"South", "Sparrow", "Spring", "Star", "Stone", "Storm", "Summer", "Sun", "Swan", "Thorn", "Thistle", "West", "Wheat",
			"White", "Willow", "Wind", "Winter", "Wolf", "Wren", "Yew", "Cinder", "Hollow", "Bell", "Fallow", "Glimmer", "Hearth",
			"Kestrel", "Larch", "Lantern", "Nettle", "Pebble", "Quill", "Robin", "Sable", "Shale", "Thrush", "Tumble", "Wyvern"};

	/** Second halves of town names. */
	static final String[] TOWN = {"haven", "ford", "wick", "stead", "bury", "ton", "dale", "field", "brook", "bridge", "hollow",
			"moor", "vale", "crest", "mere", "fall", "hold", "gate", "watch", "rest", "reach", "wood", "ham", "stone", "port",
			"cross", "by", "thorpe", "worth", "minster", "hurst", "ridge", "combe", "well", "borough", "shire", "mouth", "ley"};

	/** Second halves of the names of areas (Ashglen Forest). */
	static final String[] AREA = {"glen", "moor", "fell", "reach", "vale", "mere", "hollow", "wold", "shaw", "combe", "holt",
			"march", "fen", "down", "cairn", "brae", "dell", "ridge", "wick", "heath"};

	/** For "the Whispering Woods". */
	static final String[] ADJECTIVES = {"Whispering", "Silent", "Golden", "Ancient", "Forgotten", "Hidden", "Shimmering",
			"Howling", "Lonely", "Endless", "Sleeping", "Wandering", "Misty", "Sunlit", "Moonlit", "Ashen", "Crimson", "Emerald",
			"Frozen", "Burning", "Verdant", "Withered", "Gentle", "Restless", "Shattered", "Sunken", "Weeping", "Gilded", "Starlit",
			"Quiet", "Wild", "Dreaming", "Painted", "Shrouded", "Windswept", "Thundering", "Glittering", "Drowsy", "Twilight",
			"Bitter", "Velvet", "Silver", "Amber", "Lost", "Hallowed", "Murmuring", "Rolling", "Tangled", "Fading", "Radiant"};

	// ---------------------------------------------------------------- structures

	private static Kind named(String label, String[] stems, String... endings) {
		return new Kind(label, stems, endings);
	}

	private static Kind plain(String label) {
		return new Kind(label, null, null);
	}

	private static final String[] DARK = {"Umbra", "Echo", "Gloom", "Dread", "Murk", "Hush", "Shade", "Wraith", "Dusk", "Night",
			"Grim", "Mourn", "Void", "Sorrow", "Hollow"};
	private static final String[] SANDY = {"Sun", "Sand", "Dune", "Scarab", "Gilded", "Mirage", "Amber", "Ochre", "Saffron", "Dust"};
	private static final String[] JUNGLE = {"Jade", "Serpent", "Vine", "Moss", "Orchid", "Fern", "Canopy", "Parrot", "Ocelot"};
	private static final String[] SEA = {"Tide", "Coral", "Pearl", "Kelp", "Brine", "Wave", "Shell", "Prism", "Abyss", "Salt"};
	private static final String[] FIRE = {"Cinder", "Ash", "Ember", "Blaze", "Soot", "Brimstone", "Magma", "Scorch", "Ruin"};

	/** Structure id (without "minecraft:") → kind. Structures not here (mineshafts, buried treasure) get no title. */
	private static final Map<String, Kind> KINDS = Map.ofEntries(
			Map.entry("village_plains", named("Village", STEMS, TOWN)),
			Map.entry("village_desert", named("Village", SANDY, TOWN)),
			Map.entry("village_savanna", named("Village", STEMS, TOWN)),
			Map.entry("village_snowy", named("Village", STEMS, TOWN)),
			Map.entry("village_taiga", named("Village", STEMS, TOWN)),
			Map.entry("pillager_outpost", named("Pillager Outpost", new String[] {"Crow", "Raven", "Wolf", "Axe", "Black", "Vulture",
					"Grim", "Iron", "Scar"}, "watch", "hold", "spire", "tower", "post")),
			Map.entry("ancient_city", named("Ancient City", DARK, "deep", "hold", "halls", "vault", "reach", "spire", "gate")),
			Map.entry("trial_chambers", named("Trial Chambers", new String[] {"Copper", "Breeze", "Gale", "Trial", "Vault", "Key"},
					"vaults", "halls", "chambers", "works")),
			Map.entry("mansion", named("Woodland Mansion", new String[] {"Raven", "Dusk", "Owl", "Gloom", "Thorn", "Black"},
					"hall", "manor", "court", "house")),
			Map.entry("monument", named("Ocean Monument", SEA, "spire", "temple", "keep", "sanctum")),
			Map.entry("stronghold", named("Stronghold", new String[] {"Ender", "Eye", "Stone", "Silent", "Warden", "Rune"}, "keep",
					"hold", "fort", "bastion")),
			Map.entry("fortress", named("Nether Fortress", FIRE, "keep", "hold", "bastion", "walls", "fort")),
			Map.entry("bastion_remnant", named("Bastion", new String[] {"Gold", "Hog", "Piglin", "Gilded", "Soot", "Brute"}, "hold",
					"rampart", "bastion", "bulwark")),
			Map.entry("end_city", named("End City", new String[] {"Star", "Void", "Chorus", "Purpur", "Shulker", "Astral"}, "spire",
					"towers", "reach", "heights")),
			Map.entry("desert_pyramid", named("Desert Temple", SANDY, "tomb", "temple", "pyramid", "sanctum")),
			Map.entry("jungle_pyramid", named("Jungle Temple", JUNGLE, "temple", "shrine", "tomb", "sanctum")),
			Map.entry("trail_ruins", named("Trail Ruins", new String[] {"Clay", "Shard", "Dust", "Relic", "Brush", "Pottery"}, "field",
					"mound", "barrow", "digs")),
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
		if (path.startsWith("ocean_ruin")) return named("Ocean Ruins", SEA, "stones", "ruins", "reef", "shoals");
		return null;
	}

	/** A name for a structure, from its word lists. Later tries add "Old", "Upper"… for more room. */
	static String structure(Kind kind, Random r, int attempt) {
		String name = join(r, pick(r, kind.stems()), kind.endings());
		return attempt < 40 ? name : pick(r, new String[] {"Old ", "Upper ", "Lower ", "Little ", "Great ", "East ", "West ",
				"North ", "South "}) + name;
	}

	// ---------------------------------------------------------------- areas (biomes)

	/** The words for one kind of land: Woods, Dunes, Sea… */
	record Land(String[] features, int radius) {
	}

	private static Land land(int radius, String... features) {
		return new Land(features, radius);
	}

	/** Biome (without "minecraft:") → what an area of it is called. {@code radius}: how far one name reaches. */
	static Land land(String biome) {
		return switch (biome) {
			case "forest", "birch_forest", "old_growth_birch_forest", "flower_forest" -> land(350, "Woods", "Forest", "Wilds", "Weald", "Grove");
			case "dark_forest" -> land(350, "Darkwood", "Woods", "Thicket", "Shadows");
			case "pale_garden" -> land(300, "Garden", "Pale", "Hollows");
			case "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "snowy_taiga" -> land(380, "Pines", "Woods", "Taiga", "Spruces");
			case "jungle", "sparse_jungle", "bamboo_jungle" -> land(380, "Jungle", "Rainforest", "Canopy", "Tangle");
			case "plains", "sunflower_plains" -> land(450, "Fields", "Plains", "Downs", "Lea", "Grasslands");
			case "meadow" -> land(300, "Meadows", "Lea", "Pastures");
			case "cherry_grove" -> land(300, "Blossoms", "Groves", "Gardens");
			case "savanna", "savanna_plateau", "windswept_savanna" -> land(450, "Savanna", "Steppe", "Grasslands", "Plateau");
			case "desert" -> land(450, "Dunes", "Desert", "Sands", "Expanse", "Wastes");
			case "badlands", "eroded_badlands", "wooded_badlands" -> land(420, "Badlands", "Canyons", "Mesa", "Bluffs");
			case "windswept_hills", "windswept_gravelly_hills", "windswept_forest", "jagged_peaks", "frozen_peaks", "stony_peaks",
					"snowy_slopes", "grove" -> land(350, "Peaks", "Mountains", "Heights", "Crags", "Highlands", "Spires");
			case "snowy_plains", "ice_spikes" -> land(450, "Tundra", "Snowfields", "Frostlands", "Wastes");
			case "swamp", "mangrove_swamp" -> land(320, "Marsh", "Mire", "Bog", "Fen", "Swamp");
			case "mushroom_fields" -> land(300, "Isle", "Shroomlands");
			case "lush_caves" -> land(200, "Grotto", "Caverns", "Hollows");
			case "dripstone_caves" -> land(200, "Caves", "Caverns", "Hollows");
			case "deep_dark" -> land(220, "Depths", "Deep", "Dark");
			case "nether_wastes" -> land(300, "Wastes", "Cinderlands", "Barrens");
			case "crimson_forest" -> land(260, "Thicket", "Forest", "Tangle");
			case "warped_forest" -> land(260, "Forest", "Woods", "Glade");
			case "soul_sand_valley" -> land(260, "Valley", "Vale", "Hollow");
			case "basalt_deltas" -> land(260, "Deltas", "Flats", "Shelves");
			case "end_highlands", "end_midlands", "end_barrens", "small_end_islands", "the_end" -> land(400, "Isles", "Highlands", "Reach");
			default -> biome.contains("ocean") ? biome.startsWith("deep") ? land(700, "Deep", "Abyss", "Depths") : land(650, "Sea", "Waters", "Bay", "Sound")
					: land(350, "Lands", "Reaches");
		};
	}

	/** An area's name: "Ashglen Forest", "the Whispering Woods" or "Frost Dunes". */
	static String area(Land land, Random r, int attempt) {
		String feature = pick(r, land.features());
		int pattern = r.nextInt(100);
		String name;
		if (pattern < 50) name = join(r, pick(r, STEMS), AREA) + " " + feature;
		else if (pattern < 85) name = "the " + pick(r, ADJECTIVES) + " " + feature;
		else name = pick(r, STEMS) + " " + feature;
		return attempt < 40 ? name : pick(r, ADJECTIVES) + " " + join(r, pick(r, STEMS), AREA) + " " + feature;
	}

	// ---------------------------------------------------------------- landmarks

	/** A peak, a cavern or a lake: found by the shape of the land, not by its biome. {@code radius}: how far one name reaches. */
	enum Landmark {
		PEAK("Peak", 96), CAVE("Cavern", 64), LAKE("Lake", 72);

		final String label;
		final int radius;

		Landmark(String label, int radius) {
			this.label = label;
			this.radius = radius;
		}
	}

	private static final String[] PEAK_ENDS = {"horn", "crown", "spire", "fang", "crest", "peak", "tor", "pike", "cap", "point"};
	private static final String[] PEAKS = {"Peak", "Spire", "Crown", "Horn", "Summit", "Pinnacle", "Tooth"};
	private static final String[] CAVE_ENDS = {"maw", "deep", "hollow", "delve", "warren", "vault", "gullet", "throat"};
	private static final String[] CAVES = {"Hollow", "Cavern", "Deep", "Grotto", "Halls", "Vault", "Maw", "Undercroft"};
	private static final String[] CAVE_WORDS = {"Echoing", "Dripping", "Silent", "Hollow", "Whispering", "Gaping", "Forgotten",
			"Sunless", "Glittering", "Weeping", "Sleeping", "Hungry", "Shivering", "Lightless", "Endless", "Murmuring", "Hidden"};
	private static final String[] LAKE_ENDS = {"mere", "water", "well", "pool", "tarn", "loch", "glass"};
	private static final String[] LAKES = {"Lake", "Mere", "Waters", "Pool", "Tarn"};

	/** "Mount Ravenfell", "Frostcrown", "the Echoing Hollow", "Lake Silvermere", "Mirrormere"… */
	static String landmark(Landmark type, Random r, int attempt) {
		int pattern = r.nextInt(100);
		String name = switch (type) {
			case PEAK -> pattern < 30 ? "Mount " + join(r, pick(r, STEMS), AREA)
					: pattern < 60 ? join(r, pick(r, STEMS), PEAK_ENDS)
					: pattern < 85 ? "the " + pick(r, ADJECTIVES) + " " + pick(r, PEAKS)
					: pick(r, STEMS) + " " + pick(r, PEAKS);
			case CAVE -> pattern < 45 ? "the " + pick(r, CAVE_WORDS) + " " + pick(r, CAVES)
					: pattern < 75 ? join(r, pick(r, STEMS), CAVE_ENDS) + " " + pick(r, new String[] {"Cavern", "Caves", "Deep"})
					: "the " + pick(r, ADJECTIVES) + " " + pick(r, CAVES);
			case LAKE -> pattern < 35 ? "Lake " + join(r, pick(r, STEMS), LAKE_ENDS)
					: pattern < 60 ? join(r, pick(r, STEMS), LAKE_ENDS)
					: pattern < 85 ? "the " + pick(r, ADJECTIVES) + " " + pick(r, LAKES)
					: pick(r, STEMS) + " " + pick(r, LAKES);
		};
		return attempt < 40 ? name : pick(r, new String[] {"Old ", "Great ", "Little ", "High ", "Low "}) + name.replaceFirst("^the ", "");
	}

	// ---------------------------------------------------------------- villagers

	static final String[] FIRST = {"Alden", "Ada", "Agnes", "Alaric", "Alma", "Ambrose", "Anselm", "Ardith", "Arlo", "Astrid",
			"Aubrey", "Barnaby", "Basil", "Beatrix", "Bede", "Benedict", "Bertram", "Blythe", "Bram", "Brielle", "Brynn", "Cadence",
			"Caspian", "Cecily", "Cedric", "Clara", "Clement", "Clemency", "Colette", "Cormac", "Cressida", "Dagny", "Darius",
			"Delphine", "Dorian", "Edith", "Edmund", "Elara", "Elias", "Elowen", "Emeric", "Enid", "Esme", "Evander", "Ezra", "Fenna",
			"Finnian", "Flora", "Florian", "Freya", "Gideon", "Giles", "Greta", "Griffin", "Gwendolyn", "Hadley", "Hamish", "Hattie",
			"Hollis", "Hugo", "Ida", "Ignatius", "Imogen", "Ingrid", "Isolde", "Jasper", "Jocelyn", "Jonah", "Jory", "Juniper",
			"Kestrel", "Lenora", "Leopold", "Linnea", "Lorcan", "Lucian", "Mabel", "Mae", "Magnus", "Maren", "Marigold", "Matilda",
			"Merritt", "Milo", "Mirabel", "Nell", "Niall", "Odette", "Odo", "Ophelia", "Orin", "Oswin", "Otto", "Percival", "Petra",
			"Philippa", "Quentin", "Rhiannon", "Roland", "Rosalind", "Rowan", "Sabine", "Silas", "Soren", "Sybil", "Tamsin",
			"Tobias", "Ulric", "Una", "Valen", "Vera", "Wendell", "Wilhelmina", "Winifred", "Wystan", "Yara", "Yorick", "Zara",
			"Zephyr", "Augustin", "Bryony", "Corwin", "Dulcie", "Eamon", "Fergus", "Hester", "Lysander", "Mortimer", "Posy"};

	static final String[] LAST = {"Thorne", "Ashdown", "Blackwood", "Bramble", "Brightwater", "Briarwood", "Brookes", "Carver",
			"Cobb", "Copperfield", "Crane", "Crowley", "Dale", "Dunmore", "Eastwood", "Ellery", "Fairweather", "Fenwick", "Fletcher",
			"Flint", "Fox", "Gale", "Goodfellow", "Greaves", "Greenleaf", "Hale", "Harrow", "Hawthorne", "Hayward", "Heath",
			"Holloway", "Honeywell", "Ironside", "Kettle", "Kingsley", "Lark", "Linwood", "Lockwood", "Marsh", "Mayhew",
			"Merriweather", "Millward", "Moss", "Northcott", "Oakes", "Oakley", "Underhill", "Pemberton", "Pennywhistle", "Pike",
			"Puddlefoot", "Quickfoot", "Reed", "Ridley", "Rookwood", "Rowe", "Sallow", "Sawyer", "Shepherd", "Silverton", "Slate",
			"Smith", "Sparrow", "Stonebrook", "Stout", "Summerfield", "Swift", "Tanner", "Tatterfield", "Thatcher", "Thistle",
			"Thornbury", "Tinker", "Toller", "Trent", "Underwood", "Vale", "Wainwright", "Warren", "Weaver", "Westbrook", "Whitlock",
			"Wick", "Wilder", "Willoughby", "Windham", "Winterbourne", "Woodward", "Wren", "Yarrow", "Yeoman", "Ambergate",
			"Ashcombe", "Barley", "Bellweather", "Buckland", "Cinderby", "Dewhurst", "Elmstead", "Farrow", "Fernsby", "Gildersleeve",
			"Hearthstone", "Inglewood", "Juniper", "Kestrel", "Larkspur", "Mallory", "Nettlefield", "Orchard", "Penhallow", "Quarry",
			"Ravensworth", "Saltmarsh", "Teasdale", "Umber", "Whitmore", "Applegate", "Bracken", "Candlewick", "Draper", "Haddock"};

	static String villager(Random r) {
		return pick(r, FIRST) + " " + pick(r, LAST);
	}

	// ---------------------------------------------------------------- helpers

	private static String pick(Random r, String[] words) {
		return words[r.nextInt(words.length)];
	}

	/** "Raven" + "ford" = Ravenford. Endings that start with the letter the stem ends on (Ash + haven) are skipped. */
	static String join(Random r, String stem, String[] endings) {
		String ending = pick(r, endings);
		char last = Character.toLowerCase(stem.charAt(stem.length() - 1));
		for (int i = 0; i < 8 && ending.charAt(0) == last; i++) ending = pick(r, endings);
		return stem + ending;
	}
}
