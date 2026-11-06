package nl.libreria.collector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToLongBiFunction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import nl.libreria.collector.Achievement.Category;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * All achievements. Most come in ladders of grades (mine 100 stone → 1,000 → 10,000 → 50,000), each grade with its own
 * name, which is also a title. Everything counts from Minecraft's own statistics, so what you did before an achievement
 * existed counts too. Collections are built from the item registry, so items from an update join in by themselves.
 * Every target is reachable in normal survival play; the top grades take a long time, not luck.
 */
public final class Catalog {
	/** Items you can't get in survival, or only once per world. */
	private static final Set<String> UNOBTAINABLE = Set.of("air", "barrier", "light", "debug_stick", "knowledge_book", "bedrock",
			"end_portal_frame", "reinforced_deepslate", "budding_amethyst", "spawner", "trial_spawner", "vault", "petrified_oak_slab",
			"player_head", "chorus_plant", "frogspawn", "farmland", "dirt_path", "suspicious_sand", "suspicious_gravel", "jigsaw",
			"structure_block", "structure_void", "test_block", "test_instance_block", "dragon_egg", "bundle_of_bundles");

	/** Animals that can be bred (what the server doesn't know falls away by itself). */
	static final List<String> BREEDABLE = List.of("cow", "mooshroom", "sheep", "pig", "chicken", "rabbit", "horse", "donkey", "mule",
			"llama", "cat", "wolf", "ocelot", "fox", "panda", "bee", "turtle", "axolotl", "goat", "frog", "strider", "hoglin", "sniffer",
			"camel", "armadillo");

	private static final Rarity C = Rarity.COMMON, U = Rarity.UNCOMMON, R = Rarity.RARE, E = Rarity.EPIC, L = Rarity.LEGENDARY,
			M = Rarity.MYTHIC;

	private static final Map<String, Achievement> BY_ID = new LinkedHashMap<>();
	/** Achievement → the ladder it is a grade of (the same list for every grade). */
	private static final Map<String, List<Achievement>> LADDER = new HashMap<>();
	/** "custom:animals_bred" → the ladder that counts it (for the statistics menu: "next grade"). */
	private static final Map<String, List<Achievement>> BY_STAT = new HashMap<>();
	private static List<String> allItems = List.of();

	private Catalog() {
	}

	public static List<Achievement> all() {
		return List.copyOf(BY_ID.values());
	}

	public static @Nullable Achievement get(String id) {
		return BY_ID.get(id);
	}

	public static List<String> allItems() {
		return allItems;
	}

	public static List<Achievement> of(Category c) {
		List<Achievement> out = new ArrayList<>();
		for (Achievement a : BY_ID.values()) if (a.category() == c) out.add(a);
		return out;
	}

	/** The ladder an achievement is a grade of, lowest first; just the achievement itself if it has no grades. */
	public static List<Achievement> ladder(Achievement a) {
		List<Achievement> l = LADDER.get(a.id());
		return l == null ? List.of(a) : l;
	}

	/** "Grade 2 of 4", or null for an achievement without grades. */
	public static @Nullable String gradeText(Achievement a) {
		List<Achievement> l = LADDER.get(a.id());
		return l == null || l.size() < 2 ? null : "Grade " + (l.indexOf(a) + 1) + " of " + l.size();
	}

	// ---------------------------------------------------------------- counting, once per check

	/** Bumped at every check: a counter computes once per player per check, however many grades ask. */
	private static int generation;

	/** Start of a check (or a menu): counters work out their values again. */
	public static void fresh() {
		generation++;
	}

	/** A value from the statistics, shared by all grades of a ladder. */
	static final class Counter {
		private final ToLongBiFunction<ServerPlayer, CollectorMod.Data> f;
		private @Nullable UUID who;
		private int gen = -1;
		private long value;
		/** The one statistic it counts ("custom:fish_caught"), or null for sums and other counts. */
		@Nullable String key;

		Counter(ToLongBiFunction<ServerPlayer, CollectorMod.Data> f) {
			this.f = f;
		}

		long get(ServerPlayer p, CollectorMod.Data d) {
			if (this.gen == generation && p.getUUID().equals(this.who)) return this.value;
			this.value = this.f.applyAsLong(p, d);
			this.who = p.getUUID();
			this.gen = generation;
			return this.value;
		}
	}

	// ---------------------------------------------------------------- building

	static void build() {
		BY_ID.clear();
		SECRET.clear();
		BY_STAT.clear();
		LADDER.clear();
		DUPLICATES.clear();
		List<String> items = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			String id = BuiltInRegistries.ITEM.getKey(item).getPath();
			if (obtainable(id)) items.add(id);
		}
		allItems = List.copyOf(items);

		buildCollections();
		buildMining();
		buildCombat();
		buildFarming();
		buildExploring();
		buildCrafting();
		buildLife();
		Extra.build();
		Goals.build();
		buildTogether();
		Records.build();
		Secrets.build();
		// (+ a version: bumped when existing achievements start counting differently, so what players already
		// did is given quietly, with one card, instead of a banner for each)
		signature = BY_ID.keySet().hashCode() * 31 + COUNTING_VERSION;
	}

	private static int signature;
	/** 2: custom statistics (play time, distances, trades…) are read by their registered object. */
	private static final int COUNTING_VERSION = 2;

	/** Changes when achievements are added or removed (players then get what they already did, quietly). */
	public static int signature() {
		return signature;
	}

	static boolean obtainable(String id) {
		return !UNOBTAINABLE.contains(id) && !id.endsWith("_spawn_egg") && !id.contains("command_block") && !id.startsWith("infested_");
	}

	/** Ids or names given twice while building (a mistake in this file; the self-test reports it). */
	static final List<String> DUPLICATES = new ArrayList<>();

	/** Secret achievements: hidden until earned. */
	private static final java.util.Set<String> SECRET = new java.util.HashSet<>();

	static void addSecret(Achievement a) {
		add(a);
		SECRET.add(a.id());
	}

	public static boolean isSecret(Achievement a) {
		return SECRET.contains(a.id());
	}

	/** The vague clue for a secret while it's hidden ("Something about how you die."). */
	public static String secretClue(Achievement a) {
		String c = Secrets.clue(a.id());
		return c == null || c.isEmpty() ? "Keep playing: you'll stumble on it." : c;
	}

	static void add(Achievement a) {
		if (BY_ID.containsKey(a.id())) DUPLICATES.add(a.id());
		for (Achievement b : BY_ID.values()) if (b.name().equals(a.name())) DUPLICATES.add(a.name());
		BY_ID.put(a.id(), a);
	}

	static Achievement.Goal goal(long target, ToLongBiFunction<ServerPlayer, CollectorMod.Data> current) {
		return new Achievement.Goal() {
			@Override
			public long current(ServerPlayer player, CollectorMod.Data data) {
				return current.applyAsLong(player, data);
			}

			@Override
			public long target() {
				return target;
			}
		};
	}

	/**
	 * A ladder: {@code steps} is target, name, target, name… lowest first. Ids are {@code id.<target>}. {@code unit} is a
	 * format with %,d; "singular|plural" uses the left part when the target is 1. {@code divisor} turns the statistic
	 * into the unit (centimetres → km). Nothing is added if {@code value} is null (that part of the game isn't there).
	 */
	static void ladder(Category c, String id, Item icon, String unit, @Nullable Counter value, long divisor, Rarity[] rarities,
			Object... steps) {
		if (value == null) return;
		int bar = unit.indexOf('|');
		List<Achievement> grades = new ArrayList<>();
		for (int i = 0; i + 1 < steps.length; i += 2) {
			long t = ((Number) steps[i]).longValue();
			String name = (String) steps[i + 1];
			String fmt = bar < 0 ? unit : t == 1 ? unit.substring(0, bar) : unit.substring(bar + 1);
			Rarity r = rarities[Math.min(i / 2, rarities.length - 1)];
			Achievement a = new Achievement(id + "." + t, c, name, String.format(Locale.US, fmt, t), icon, r,
					goal(t, (p, d) -> value.get(p, d) / divisor), null);
			add(a);
			grades.add(a);
		}
		chain(grades);
		if (value.key != null && divisor == 1 && grades.size() > 1) BY_STAT.put(value.key, List.copyOf(grades));
	}

	/** The ladder of grades that counts one statistic ("custom:fish_caught"), or an empty list. */
	public static List<Achievement> byStat(String key) {
		return BY_STAT.getOrDefault(key, List.of());
	}

	/** Makes these achievements one ladder (lowest target first). */
	static void chain(List<Achievement> grades) {
		if (grades.size() < 2) return;
		List<Achievement> list = List.copyOf(grades);
		for (Achievement a : list) LADDER.put(a.id(), list);
	}

	static Rarity[] r(Rarity... rs) {
		return rs;
	}

	// ---------------------------------------------------------------- what counts

	static Identifier mc(String path) {
		return Identifier.withDefaultNamespace(path);
	}

	/** A statistic from the "custom" list (play time, distances…), or null if this version has no such statistic. */
	static @Nullable Counter custom(String... stats) {
		List<Identifier> ids = new ArrayList<>();
		// The registered object itself: Minecraft keeps these statistics by object, not by name
		for (String s : stats) {
			Identifier key = nl.libreria.core.Ui.customStatKey(s);
			if (key != null) ids.add(key);
		}
		if (ids.isEmpty()) return null;
		Counter c = new Counter((p, d) -> {
			long n = 0;
			for (Identifier id : ids) n += p.getStats().getValue(Stats.CUSTOM, id);
			return n;
		});
		if (ids.size() == 1) c.key = "custom:" + ids.getFirst().getPath();
		return c;
	}

	static List<Block> blocks(String... ids) {
		List<Block> out = new ArrayList<>();
		for (String id : ids) BuiltInRegistries.BLOCK.getOptional(mc(id)).ifPresent(out::add);
		return out;
	}

	static @Nullable Counter mined(List<Block> blocks) {
		if (blocks.isEmpty()) return null;
		List<Block> list = List.copyOf(blocks);
		return new Counter((p, d) -> {
			long n = 0;
			for (Block b : list) n += p.getStats().getValue(Stats.BLOCK_MINED, b);
			return n;
		});
	}

	static @Nullable Counter mined(String... ids) {
		return mined(blocks(ids));
	}

	/** Kills of every kind of mob in a category (monsters, animals…). */
	static Counter killedCategory(net.minecraft.world.entity.MobCategory category) {
		List<EntityType<?>> types = new ArrayList<>();
		for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) if (t.getCategory() == category) types.add(t);
		return new Counter((p, d) -> {
			long n = 0;
			for (EntityType<?> t : types) n += p.getStats().getValue(Stats.ENTITY_KILLED, t);
			return n;
		});
	}

	/** Times these mobs killed you. */
	static @Nullable Counter killedBy(String... ids) {
		List<EntityType<?>> types = new ArrayList<>();
		for (String id : ids) BuiltInRegistries.ENTITY_TYPE.getOptional(mc(id)).ifPresent(types::add);
		if (types.isEmpty()) return null;
		return new Counter((p, d) -> {
			long n = 0;
			for (EntityType<?> t : types) n += p.getStats().getValue(Stats.ENTITY_KILLED_BY, t);
			return n;
		});
	}

	/** Made: crafted, smelted (taken out of a furnace), traded for… as Minecraft counts "crafted". */
	static @Nullable Counter crafted(List<Item> items) {
		if (items.isEmpty()) return null;
		List<Item> list = List.copyOf(items);
		return new Counter((p, d) -> {
			long n = 0;
			for (Item i : list) n += p.getStats().getValue(Stats.ITEM_CRAFTED, i);
			return n;
		});
	}

	static @Nullable Counter killed(String... ids) {
		List<EntityType<?>> types = new ArrayList<>();
		for (String id : ids) BuiltInRegistries.ENTITY_TYPE.getOptional(mc(id)).ifPresent(types::add);
		if (types.isEmpty()) return null;
		return new Counter((p, d) -> {
			long n = 0;
			for (EntityType<?> t : types) n += p.getStats().getValue(Stats.ENTITY_KILLED, t);
			return n;
		});
	}

	static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(mc(id));
	}

	static List<Item> itemsWhere(Predicate<String> match) {
		List<Item> out = new ArrayList<>();
		for (String id : allItems) if (match.test(id)) out.add(item(id));
		return out;
	}

	static List<Item> itemsOf(String... ids) {
		Set<String> want = Set.of(ids);
		return itemsWhere(want::contains);
	}

	/** Times used: eaten, placed, thrown, swung… */
	static @Nullable Counter used(List<Item> items) {
		if (items.isEmpty()) return null;
		List<Item> list = List.copyOf(items);
		return new Counter((p, d) -> {
			long n = 0;
			for (Item i : list) n += p.getStats().getValue(Stats.ITEM_USED, i);
			return n;
		});
	}

	/** Picked up minus dropped: what you really gathered (dropping and picking up again doesn't count). */
	static @Nullable Counter gathered(List<Item> items) {
		if (items.isEmpty()) return null;
		List<Item> list = List.copyOf(items);
		return new Counter((p, d) -> {
			long n = 0;
			for (Item i : list) n += p.getStats().getValue(Stats.ITEM_PICKED_UP, i) - p.getStats().getValue(Stats.ITEM_DROPPED, i);
			return Math.max(0, n);
		});
	}

	private static @Nullable Counter discovered(java.util.function.ToIntBiFunction<Services.Discoveries, ServerPlayer> f) {
		Services.Discoveries disc = Services.discoveries().orElse(null);
		if (disc == null) return null;
		return new Counter((p, d) -> f.applyAsInt(disc, p));
	}

	/** Item tag by name (not every tag still has a Java constant in 26.x). */
	static net.minecraft.tags.TagKey<Item> tag(String name) {
		return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, mc(name));
	}

	static boolean is(String id, net.minecraft.tags.TagKey<Item> tag) {
		return new ItemStack(item(id)).is(tag);
	}

	static boolean has(String id, net.minecraft.core.component.DataComponentType<?> type) {
		return new ItemStack(item(id)).has(type);
	}

	// ---------------------------------------------------------------- collecting

	private static void collection(String id, String name, String desc, Item icon, Rarity rarity, Predicate<String> match) {
		List<String> set = new ArrayList<>();
		for (String it : allItems) if (match.test(it)) set.add(it);
		if (set.size() < 2) return;
		List<String> list = List.copyOf(set);
		add(new Achievement("verzamel." + id, Category.VERZAMELEN, name, desc + " (" + list.size() + ")", icon, rarity,
				goal(list.size(), (p, d) -> list.stream().filter(d.items::contains).count()), list));
	}

	private static Predicate<String> oneOf(String... ids) {
		Set<String> set = Set.of(ids);
		return set::contains;
	}

	private static void buildCollections() {
		// Colours
		collection("wol", "Wool Merchant", "Every color of wool", Ui.item("white_wool"), U, id -> id.endsWith("_wool"));
		collection("tapijt", "Rug Collector", "Every carpet", Ui.item("red_carpet"), U, id -> id.endsWith("_carpet"));
		collection("kleurstof", "Over the Rainbow", "Every dye", Ui.item("red_dye"), U, id -> id.endsWith("_dye"));
		collection("glas", "Window Shopper", "Every color of stained glass", Ui.item("light_blue_stained_glass"), U,
				id -> id.endsWith("_stained_glass"));
		collection("ruiten", "Pane Collector", "Every stained glass pane", Ui.item("lime_stained_glass_pane"), U,
				id -> id.endsWith("_stained_glass_pane"));
		collection("beton", "Cement Mixer", "Every color of concrete", Ui.item("orange_concrete"), U, id -> id.endsWith("_concrete"));
		collection("poeder", "Powder Room", "Every color of concrete powder", Ui.item("pink_concrete_powder"), U,
				id -> id.endsWith("_concrete_powder"));
		collection("terracotta", "Earthenware", "Every terracotta", Items.TERRACOTTA, U,
				id -> id.endsWith("terracotta") && !id.endsWith("_glazed_terracotta"));
		collection("bedden", "Bed Showroom", "Every color of bed", Ui.item("red_bed"), U, id -> id.endsWith("_bed"));
		collection("kaarsen", "Candlelight", "Every candle", Ui.item("candle"), U, id -> id.equals("candle") || id.endsWith("_candle"));
		collection("vlaggen", "Flag Collector", "Every color of banner", Ui.item("blue_banner"), U,
				id -> id.endsWith("_banner") && !id.endsWith("_banner_pattern"));
		collection("glazuur", "Glazed and Confused", "Every glazed terracotta", Ui.item("magenta_glazed_terracotta"), R,
				id -> id.endsWith("_glazed_terracotta"));
		collection("shulker", "Thinking Inside the Box", "Every color of shulker box", Items.SHULKER_BOX, E,
				id -> id.endsWith("shulker_box"));
		collection("tuig", "Ghast Rider", "Every color of harness", Ui.item("white_harness"), E, id -> id.endsWith("_harness"));

		// Wood and building
		collection("stammen", "Lumberjack", "Every kind of log", Items.OAK_LOG, U,
				id -> is(id, ItemTags.LOGS) && !id.startsWith("stripped_") && !id.endsWith("_wood") && !id.endsWith("_hyphae"));
		collection("planken", "Carpenter", "Every kind of planks", Items.OAK_PLANKS, U, id -> id.endsWith("_planks"));
		collection("deuren", "Door to Door", "Every door", Items.OAK_DOOR, U, id -> id.endsWith("_door") && !id.endsWith("_trapdoor"));
		collection("luiken", "Trapdoor Tinkerer", "Every trapdoor", Items.OAK_TRAPDOOR, U, id -> id.endsWith("_trapdoor"));
		collection("hekken", "Picket Perfect", "Every fence", Items.OAK_FENCE, U, id -> id.endsWith("_fence"));
		collection("borden", "Signpost", "Every sign", Items.OAK_SIGN, U, id -> id.endsWith("_sign") && !id.endsWith("_hanging_sign"));
		collection("hangborden", "Hanging Around", "Every hanging sign", Items.OAK_HANGING_SIGN, U, id -> id.endsWith("_hanging_sign"));
		collection("boten", "Shipwright", "Every boat and raft", Items.OAK_BOAT, R, id -> id.endsWith("_boat") || id.endsWith("_raft"));
		collection("koper", "Patina Collector", "Every copper block, old and new, waxed or not", Ui.item("copper_block"), E,
				id -> id.contains("copper") && !id.contains("ore") && !id.startsWith("raw_") && !id.equals("copper_ingot")
						&& !id.equals("copper_nugget") && item(id) instanceof BlockItem);
		collection("werkbanken", "Workshop", "Every workstation", Items.CRAFTING_TABLE, R, oneOf("crafting_table", "furnace", "blast_furnace",
				"smoker", "cartography_table", "fletching_table", "smithing_table", "stonecutter", "loom", "grindstone", "barrel", "composter",
				"lectern", "brewing_stand", "cauldron", "anvil", "enchanting_table", "crafter"));
		collection("licht", "Let There Be Light", "Every kind of light", Items.LANTERN, R, oneOf("torch", "soul_torch", "copper_torch",
				"lantern", "soul_lantern", "copper_lantern", "sea_lantern", "glowstone", "shroomlight", "jack_o_lantern", "end_rod",
				"ochre_froglight", "verdant_froglight", "pearlescent_froglight", "redstone_lamp", "campfire", "soul_campfire", "beacon"));
		collection("redstone", "Contraption Maker", "Every redstone part", Items.REDSTONE, R, oneOf("redstone", "redstone_torch", "repeater",
				"comparator", "observer", "piston", "sticky_piston", "hopper", "dropper", "dispenser", "lever", "daylight_detector", "target",
				"tripwire_hook", "note_block", "redstone_lamp", "redstone_block", "crafter", "sculk_sensor", "calibrated_sculk_sensor",
				"lightning_rod", "tnt", "stone_button", "stone_pressure_plate"));
		collection("rails", "All Aboard", "Every kind of rail", Items.RAIL, U, oneOf("rail", "powered_rail", "detector_rail", "activator_rail"));

		// Nature
		collection("zaden", "Seed Bank", "Every kind of seed", Items.WHEAT_SEEDS, U, id -> id.endsWith("_seeds") || id.equals("pitcher_pod"));
		collection("bloemen", "Florist", "Every flower", Items.POPPY, R, id -> is(id, tag("flowers")));
		collection("boompjes", "Tree Nursery", "Every sapling", Items.OAK_SAPLING, R, id -> is(id, ItemTags.SAPLINGS));
		collection("bladeren", "Leaf Peeper", "Every kind of leaves", Items.OAK_LEAVES, R, id -> is(id, ItemTags.LEAVES));
		collection("paddenstoelen", "Fungus Among Us", "Every mushroom and fungus", Items.RED_MUSHROOM, U, oneOf("red_mushroom",
				"brown_mushroom", "crimson_fungus", "warped_fungus", "red_mushroom_block", "brown_mushroom_block", "mushroom_stem"));
		collection("koraal", "Reef Diver", "Every coral", Ui.item("tube_coral"), R, id -> id.contains("coral"));
		collection("kikkerlicht", "Froglight Trio", "Every froglight", Ui.item("ochre_froglight"), R, id -> id.endsWith("_froglight"));
		collection("eieren", "Egg Hunt", "Every kind of egg", Items.EGG, R, oneOf("egg", "blue_egg", "brown_egg", "turtle_egg", "sniffer_egg"));

		// Treasures and gear
		collection("erts", "Geologist", "Every ore (with Silk Touch)", Items.DIAMOND_ORE, R, id -> id.endsWith("_ore") || id.equals("ancient_debris"));
		collection("mineralen", "Prospector", "Every gem and metal", Items.DIAMOND, R, oneOf("coal", "charcoal", "raw_iron", "iron_ingot",
				"raw_copper", "copper_ingot", "raw_gold", "gold_ingot", "redstone", "lapis_lazuli", "diamond", "emerald", "quartz",
				"amethyst_shard", "netherite_scrap", "netherite_ingot", "echo_shard", "resin_brick"));
		collection("emmers", "Bucket List", "Every kind of bucket", Items.WATER_BUCKET, R, id -> id.equals("bucket") || id.endsWith("_bucket"));
		collection("bundels", "Bundle of Joy", "Every bundle", Items.BUNDLE, R, id -> id.endsWith("bundle"));
		collection("banier", "Standard Bearer", "Every banner pattern", Items.FLOWER_BANNER_PATTERN, R, id -> id.endsWith("_banner_pattern"));
		collection("eten", "Full Pantry", "Everything you can eat", Items.BREAD, R, id -> has(id, DataComponents.FOOD));
		collection("buit", "Loot Goblin", "Every monster drop", Items.ROTTEN_FLESH, R, oneOf("rotten_flesh", "bone", "string", "spider_eye",
				"gunpowder", "ender_pearl", "slime_ball", "blaze_rod", "ghast_tear", "magma_cream", "phantom_membrane", "prismarine_shard",
				"prismarine_crystals", "shulker_shell", "breeze_rod", "ink_sac", "glow_ink_sac", "wither_skeleton_skull", "totem_of_undying"));
		collection("leer", "Full Leather", "All leather armor", Items.LEATHER_CHESTPLATE, C,
				id -> id.startsWith("leather_") && has(id, DataComponents.MAX_DAMAGE));
		collection("malien", "Chain Gang", "All chainmail armor", Items.CHAINMAIL_CHESTPLATE, U,
				id -> id.startsWith("chainmail_") && has(id, DataComponents.MAX_DAMAGE));
		collection("koperuitrusting", "Full Copper", "All copper tools and armor", Items.COPPER_INGOT, U,
				id -> id.startsWith("copper_") && has(id, DataComponents.MAX_DAMAGE));
		collection("ijzer", "Full Iron", "All iron tools and armor", Items.IRON_CHESTPLATE, U,
				id -> id.startsWith("iron_") && has(id, DataComponents.MAX_DAMAGE));
		collection("goud", "Gilded", "All golden tools and armor", Items.GOLDEN_CHESTPLATE, R,
				id -> id.startsWith("golden_") && has(id, DataComponents.MAX_DAMAGE));
		collection("diamant", "Full Diamond", "All diamond tools and armor", Items.DIAMOND_CHESTPLATE, R,
				id -> id.startsWith("diamond_") && has(id, DataComponents.MAX_DAMAGE));
		collection("netherite", "Full Netherite", "All netherite tools and armor", Items.NETHERITE_CHESTPLATE, E,
				id -> id.startsWith("netherite_") && has(id, DataComponents.MAX_DAMAGE));
		collection("paardentuig", "Knight's Stable", "Every horse armor", Items.IRON_HORSE_ARMOR, R, id -> id.endsWith("_horse_armor"));
		collection("scherven", "Archaeologist", "Every pottery sherd", Items.ARCHER_POTTERY_SHERD, E, id -> id.endsWith("_pottery_sherd"));
		collection("platen", "Disc Jockey", "Every music disc", Items.MUSIC_DISC_CAT, L, id -> id.startsWith("music_disc_"));
		collection("sjablonen", "Master Smith", "Every smithing template", Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, L,
				id -> id.endsWith("_smithing_template"));
		collection("koppen", "Headhunter", "Every mob head", Items.CREEPER_HEAD, L, oneOf("skeleton_skull", "wither_skeleton_skull",
				"zombie_head", "creeper_head", "piglin_head", "dragon_head"));
		collection("schatten", "Dragon's Hoard", "The rarest treasures", Items.NETHER_STAR, L, oneOf("elytra", "nether_star",
				"totem_of_undying", "heart_of_the_sea", "trident", "beacon", "enchanted_golden_apple", "heavy_core", "mace", "conduit",
				"sniffer_egg", "recovery_compass", "ominous_trial_key", "dragon_breath"));

		// Milestones over every item
		int all = allItems.size();
		long[] steps = {25, 50, 150, 300, 500, 800, 1100};
		Rarity[] rarities = {C, C, U, R, E, L, L};
		String[] names = {"Pack Rat", "Collector I", "Collector II", "Collector III", "Collector IV", "Collector V", "Collector VI"};
		List<Achievement> ladder = new ArrayList<>();
		for (int i = 0; i < steps.length; i++) {
			if (steps[i] >= all) break;
			long target = steps[i];
			Achievement a = new Achievement("items." + target, Category.VERZAMELEN, names[i], String.format(Locale.US, "%,d different items", target),
					Items.CHEST, rarities[i], goal(target, (p, d) -> countAll(d)), null);
			add(a);
			ladder.add(a);
		}
		Achievement every = new Achievement("items.alles", Category.VERZAMELEN, "Item Completionist",
				String.format(Locale.US, "Every item there is (%,d)", all), Items.NETHER_STAR, M, goal(all, (p, d) -> countAll(d)), allItems);
		add(every);
		ladder.add(every);
		chain(ladder);
		add(new Achievement("items.drakenei", Category.VERZAMELEN, "Keeper of the Egg", "Hold the one and only dragon egg",
				Items.DRAGON_EGG, M, goal(1, (p, d) -> d.items.contains("dragon_egg") ? 1 : 0), null));
	}

	public static long countAll(CollectorMod.Data d) {
		long n = 0;
		for (String id : allItems) if (d.items.contains(id)) n++;
		return n;
	}

	// ---------------------------------------------------------------- mining

	private static void buildMining() {
		Category c = Category.MIJNBOUW;
		List<Block> everything = new ArrayList<>();
		BuiltInRegistries.BLOCK.forEach(everything::add);
		ladder(c, "mijn", Items.IRON_PICKAXE, "Mine %,d blocks", mined(everything), 1, r(C, U, R, E, L, M),
				1000, "Little Digger", 10000, "Miner", 50000, "Tunnel Vision", 200000, "Mountain Mover", 500000, "World Eater",
				1000000, "Planet Cracker");
		ladder(c, "steen", Items.COBBLESTONE, "Mine %,d stone", mined("stone", "deepslate", "cobblestone", "cobbled_deepslate", "granite",
				"diorite", "andesite", "tuff", "calcite", "dripstone_block", "smooth_basalt", "mossy_cobblestone"), 1, r(C, U, R, E, L),
				100, "Pebble Picker", 1000, "Rock Breaker", 10000, "Quarryman", 50000, "Mountain Carver", 250000, "Bedrock Botherer");
		ladder(c, "grond", Items.DIRT, "Dig %,d dirt, sand and gravel", mined("dirt", "grass_block", "coarse_dirt", "rooted_dirt", "podzol",
				"mycelium", "sand", "red_sand", "gravel", "clay", "mud", "muddy_mangrove_roots", "soul_sand", "soul_soil", "snow_block"), 1,
				r(C, U, R, E), 100, "Dirt Digger", 1000, "Trench Digger", 10000, "Earthmover", 50000, "Landscaper");
		List<Block> logs = new ArrayList<>();
		for (Block b : BuiltInRegistries.BLOCK) if (b.defaultBlockState().is(BlockTags.LOGS)) logs.add(b);
		ladder(c, "hout", Items.IRON_AXE, "Chop %,d logs", mined(logs), 1, r(C, U, R, E, L, M),
				64, "Twig Snapper", 500, "Woodcutter", 2500, "Timber!", 10000, "Forest Feller", 25000, "Deforester", 100000, "Last Tree Standing");
		ladder(c, "kolen", Items.COAL, "Mine %,d coal ore", mined("coal_ore", "deepslate_coal_ore"), 1, r(C, U, R),
				10, "Sooty Hands", 100, "Coal Miner", 1000, "Coal Baron");
		ladder(c, "ijzer", Items.RAW_IRON, "Mine %,d iron ore", mined("iron_ore", "deepslate_iron_ore"), 1, r(C, U, R),
				10, "Rust Finder", 100, "Ironmonger", 1000, "Iron Lord");
		ladder(c, "koper", Items.RAW_COPPER, "Mine %,d copper ore", mined("copper_ore", "deepslate_copper_ore"), 1, r(C, U, R),
				10, "Penny Pincher", 100, "Coppersmith", 1000, "Copper King");
		ladder(c, "goud", Items.RAW_GOLD, "Mine %,d gold ore", mined("gold_ore", "deepslate_gold_ore", "nether_gold_ore"), 1, r(U, R, E),
				10, "Gold Digger", 100, "Gold Rush", 1000, "Midas Touch");
		ladder(c, "redstone", Items.REDSTONE, "Mine %,d redstone ore", mined("redstone_ore", "deepslate_redstone_ore"), 1, r(C, U, R),
				10, "Red Dust", 100, "Redstone Miner", 1000, "Circuit Lord");
		ladder(c, "lapis", Items.LAPIS_LAZULI, "Mine %,d lapis ore", mined("lapis_ore", "deepslate_lapis_ore"), 1, r(C, U, R),
				10, "Blue Fingers", 100, "Lapis Hoarder", 500, "Azure Baron");
		ladder(c, "smaragd", Items.EMERALD, "Mine your first emerald ore|Mine %,d emerald ore", mined("emerald_ore", "deepslate_emerald_ore"),
				1, r(U, R, E), 1, "Lucky Strike", 10, "Emerald Eye", 50, "Emerald Emperor");
		ladder(c, "diamant", Items.DIAMOND, "Mine your first diamond ore|Mine %,d diamond ore", mined("diamond_ore", "deepslate_diamond_ore"),
				1, r(C, U, R, E, L, M), 1, "Diamonds!", 25, "Sparkle Seeker", 100, "Diamond Hunter", 500, "Diamond Baron", 1000, "Diamond King",
				5000, "Diamond Deity");
		ladder(c, "puin", Items.ANCIENT_DEBRIS, "Mine your first ancient debris|Mine %,d ancient debris", mined("ancient_debris"), 1,
				r(U, R, E, L, M), 1, "Ancient Find", 16, "Debris Digger", 64, "Netherite Miner", 256, "Lord of the Depths", 1024, "Netherite Tycoon");
		ladder(c, "kwarts", Items.QUARTZ, "Mine %,d nether quartz ore", mined("nether_quartz_ore"), 1, r(C, U, R),
				10, "Quartz Picker", 100, "Quartz Miner", 1000, "Crystal Baron");
		ladder(c, "obsidiaan", Items.OBSIDIAN, "Mine %,d obsidian", mined("obsidian", "crying_obsidian"), 1, r(C, U, E),
				14, "Portal Builder", 100, "Obsidian Miner", 1000, "Obsidian Overlord");
		ladder(c, "amethist", Items.AMETHYST_SHARD, "Break %,d amethyst clusters", mined("amethyst_cluster"), 1, r(C, U, R),
				10, "Geode Cracker", 100, "Crystal Gardener", 500, "Amethyst Sage");
		ladder(c, "netherrack", Items.NETHERRACK, "Mine %,d netherrack", mined("netherrack"), 1, r(C, U, R),
				100, "Nether Digger", 1000, "Netherrack Ripper", 10000, "Hell Tunneler");
		ladder(c, "aslaag", Items.BASALT, "Mine %,d blackstone, basalt and magma", mined("blackstone", "basalt", "magma_block"), 1, r(C, U),
				100, "Ash Walker", 1000, "Basalt Breaker");
		ladder(c, "endsteen", Items.END_STONE, "Mine %,d end stone", mined("end_stone"), 1, r(U, R, E),
				100, "End Prospector", 1000, "Void Miner", 10000, "End Excavator");
		ladder(c, "sculk", Items.SCULK, "Mine %,d sculk", mined("sculk", "sculk_vein", "sculk_catalyst", "sculk_sensor", "sculk_shrieker",
				"calibrated_sculk_sensor"), 1, r(U, R), 64, "Quiet Steps", 500, "Sculk Harvester");
		ladder(c, "gloeisteen", Items.GLOWSTONE, "Mine %,d glowstone", mined("glowstone"), 1, r(C, U), 64, "Glow Getter", 500, "Lantern Maker");
		ladder(c, "ijs", Items.PACKED_ICE, "Mine %,d ice", mined("ice", "packed_ice", "blue_ice"), 1, r(C, U), 64, "Ice Breaker", 640, "Frost Miner");
		List<Block> terracotta = new ArrayList<>();
		for (Block b : BuiltInRegistries.BLOCK) {
			String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
			if (id.endsWith("terracotta") && !id.endsWith("glazed_terracotta")) terracotta.add(b);
		}
		ladder(c, "mesa", Items.TERRACOTTA, "Mine %,d terracotta", mined(terracotta), 1, r(U, R), 100, "Canyon Carver", 1000, "Mesa Miner");
	}

	// ---------------------------------------------------------------- combat

	private static void buildCombat() {
		Category c = Category.GEVECHT;
		ladder(c, "monster", Items.IRON_SWORD, "Defeat your first mob|Defeat %,d mobs", custom("mob_kills"), 1, r(C, U, R, E, L, M),
				10, "First Blood", 100, "Monster Hunter", 1000, "Pest Control", 10000, "Slayer", 50000, "Unstoppable", 150000, "Extinction Event");
		ladder(c, "zombie", Items.ROTTEN_FLESH, "Defeat %,d zombies (husks and drowned too)", killed("zombie", "zombie_villager", "husk", "drowned"),
				1, r(C, U, R, E), 10, "Zombie Swatter", 100, "Zombie Hunter", 1000, "Zombie Slayer", 5000, "Bane of the Undead");
		ladder(c, "skelet", Items.BONE, "Defeat %,d skeletons (strays and bogged too)", killed("skeleton", "stray", "bogged", "parched"), 1,
				r(C, U, R, E), 10, "Bone Breaker", 100, "Skeleton Hunter", 1000, "Bone Collector", 5000, "Ossuary Keeper");
		ladder(c, "creeper", Items.GUNPOWDER, "Defeat %,d creepers", killed("creeper"), 1, r(C, U, R, E),
				10, "Defuser", 100, "Creeper Hunter", 1000, "Bomb Squad", 5000, "Creeper Nemesis");
		ladder(c, "spin", Items.STRING, "Defeat %,d spiders", killed("spider", "cave_spider"), 1, r(C, U, R),
				10, "Bug Squasher", 100, "Spider Hunter", 1000, "Arachnophobe");
		ladder(c, "enderman", Items.ENDER_PEARL, "Defeat %,d endermen", killed("enderman"), 1, r(C, U, R, E),
				10, "Eye Contact", 100, "Enderman Hunter", 1000, "Pearl Farmer", 10000, "Ender Reaper");
		ladder(c, "slijm", Items.SLIME_BALL, "Defeat %,d slimes and magma cubes", killed("slime", "magma_cube"), 1, r(C, U, R),
				10, "Slime Popper", 100, "Slime Hunter", 1000, "Sticky Fingers");
		ladder(c, "heks", Items.GLASS_BOTTLE, "Defeat your first witch|Defeat %,d witches", killed("witch"), 1, r(C, U, R),
				1, "Hex Breaker", 10, "Witch Hunter", 100, "Inquisitor");
		ladder(c, "blaze", Items.BLAZE_ROD, "Defeat %,d blazes", killed("blaze"), 1, r(U, R, E), 10, "Fire Fighter", 100, "Blaze Hunter",
				1000, "Blaze Runner");
		ladder(c, "witherskelet", Items.WITHER_SKELETON_SKULL, "Defeat %,d wither skeletons", killed("wither_skeleton"), 1, r(U, R, E),
				10, "Skull Seeker", 100, "Wither Bane", 1000, "Black Bones");
		ladder(c, "ghast", Items.GHAST_TEAR, "Defeat your first ghast|Defeat %,d ghasts", killed("ghast"), 1, r(C, U, R),
				1, "Ghastbuster", 10, "Ghast Hunter", 100, "Sky Sweeper");
		ladder(c, "guardian", Items.PRISMARINE_SHARD, "Defeat %,d guardians", killed("guardian"), 1, r(U, R, E),
				10, "Fish Fryer", 100, "Guardian Hunter", 1000, "Monument Raider");
		ladder(c, "phantom", Items.PHANTOM_MEMBRANE, "Defeat your first phantom|Defeat %,d phantoms", killed("phantom"), 1, r(C, U, R),
				1, "Night Watch", 10, "Phantom Hunter", 100, "Insomniac");
		ladder(c, "illager", Items.CROSSBOW, "Defeat %,d illagers (pillagers, vindicators, evokers, ravagers)", killed("pillager", "vindicator",
				"evoker", "illusioner", "ravager"), 1, r(U, R, E), 10, "Peacekeeper", 100, "Illager Hunter", 1000, "Raid Breaker");
		ladder(c, "evoker", Items.TOTEM_OF_UNDYING, "Defeat your first evoker|Defeat %,d evokers", killed("evoker"), 1, r(U, R, E),
				1, "Totem Taker", 10, "Spellbreaker", 50, "Evoker's End");
		ladder(c, "shulker", Items.SHULKER_SHELL, "Defeat %,d shulkers", killed("shulker"), 1, r(U, R, E),
				10, "Box Opener", 100, "Shulker Hunter", 1000, "Shell Collector");
		ladder(c, "piglin", Items.GOLD_NUGGET, "Defeat %,d piglins (zombified too)", killed("piglin", "piglin_brute", "zombified_piglin"), 1,
				r(C, U, R, E), 10, "Pork Chopper", 100, "Piglin Hunter", 1000, "Gold Farmer", 10000, "Nether Butcher");
		ladder(c, "hoglin", Items.COOKED_PORKCHOP, "Defeat %,d hoglins and zoglins", killed("hoglin", "zoglin"), 1, r(C, U, R),
				10, "Boar Hunter", 100, "Tusk Taker", 1000, "Hog Wild");
		ladder(c, "breeze", Items.BREEZE_ROD, "Defeat %,d breezes", killed("breeze"), 1, r(U, R), 10, "Wind Breaker", 100, "Storm Chaser");
		ladder(c, "ongedierte", Items.STONE_BRICKS, "Defeat %,d silverfish and endermites", killed("silverfish", "endermite"), 1, r(C, U),
				10, "Exterminator", 100, "Pest Purger");
		ladder(c, "creaking", Ui.item("creaking_heart"), "Defeat your first creaking|Defeat %,d creakings", killed("creaking"), 1, r(U, R),
				1, "Heartbreaker", 10, "Pale Gardener");

		// Bosses
		boss("baas.draak", "Dragonslayer", "Defeat the Ender Dragon", Items.DRAGON_HEAD, L, "ender_dragon", 5, "Wyrmbane", L);
		boss("baas.wither", "Wither Hunter", "Defeat the Wither", Items.NETHER_STAR, L, "wither", 5, "Wither Breaker", L);
		Achievement withers = BY_ID.get("baas.wither.5");
		if (withers != null) {
			Counter k = killed("wither");
			Achievement star = new Achievement("baas.wither.25", Category.GEVECHT, "Star Forger", "Defeat the Wither 25 times",
					Items.BEACON, L, goal(25, (p, d) -> k.get(p, d)), null);
			add(star);
			chain(List.of(BY_ID.get("baas.wither"), withers, star));
		}
		boss("baas.guardian", "Knight Templar", "Defeat an Elder Guardian", Items.PRISMARINE_SHARD, E, "elder_guardian", 3,
				"Monument Conqueror", L);
		Counter warden = killed("warden");
		if (warden != null) {
			add(new Achievement("baas.warden", c, "Dead Silent", "Defeat a Warden", Items.SCULK_SHRIEKER, M, goal(1, (p, d) -> warden.get(p, d)), null));
		}
		ladder(c, "raid", Items.BELL, "Win a raid|Win %,d raids", custom("raid_win"), 1, r(R, E, L),
				1, "Hero of the Village", 3, "Village Guardian", 10, "Protector");

		// Damage, in hearts (the statistic is in tenths of a health point)
		ladder(c, "schade", Items.NETHERITE_SWORD, "Deal %,d hearts of damage", custom("damage_dealt"), 20, r(U, R, E, L, M),
				1000, "Bruiser", 10000, "Heavy Hitter", 100000, "Juggernaut", 500000, "Walking Apocalypse", 2000000, "End of Days");
		ladder(c, "schild", Items.SHIELD, "Block %,d hearts with a shield", custom("damage_blocked_by_shield"), 20, r(C, U, R),
				100, "Shield Bearer", 1000, "Bulwark", 10000, "Living Wall");
		ladder(c, "incasseren", Items.IRON_CHESTPLATE, "Take %,d hearts of damage", custom("damage_taken"), 20, r(U, R, E),
				1000, "Punching Bag", 10000, "Thick Skin", 100000, "Human Shield");
	}

	/** A boss: one kill, then a second grade with more. */
	private static void boss(String id, String name, String desc, Item icon, Rarity rarity, String entity, int more, String moreName,
			Rarity moreRarity) {
		Counter k = killed(entity);
		if (k == null) return;
		Achievement first = new Achievement(id, Category.GEVECHT, name, desc, icon, rarity, goal(1, (p, d) -> k.get(p, d)), null);
		Achievement again = new Achievement(id + "." + more, Category.GEVECHT, moreName, desc + " " + more + " times", icon, moreRarity,
				goal(more, (p, d) -> k.get(p, d)), null);
		add(first);
		add(again);
		chain(List.of(first, again));
	}

	// ---------------------------------------------------------------- farming and animals

	private static void buildFarming() {
		Category c = Category.FOKKEN;
		ladder(c, "fok", Items.WHEAT, "Breed an animal|Breed %,d animals", custom("animals_bred"), 1, r(C, U, R, E, L, M),
				1, "Baby Steps", 25, "Old MacDonald", 100, "Breeder", 500, "Rancher", 2000, "Animal Empire", 10000, "Overpopulation");

		List<String> species = new ArrayList<>();
		for (String s : BREEDABLE) if (BuiltInRegistries.ENTITY_TYPE.containsKey(mc(s))) species.add(s);
		Counter kinds = new Counter((p, d) -> species.stream().filter(d.bred::contains).count());
		Achievement s5 = new Achievement("soort.5", c, "Animal Lover", "Breed 5 different species", Items.BONE, U, goal(5, (p, d) -> kinds.get(p, d)), null);
		Achievement s12 = new Achievement("soort.12", c, "Zookeeper", "Breed 12 different species", Items.EGG, E, goal(12, (p, d) -> kinds.get(p, d)), null);
		Achievement sAll = new Achievement("soort.alles", c, "Noah's Ark", "Breed every species that can be bred (" + species.size() + ")",
				Items.OAK_BOAT, L, goal(species.size(), (p, d) -> kinds.get(p, d)), null);
		add(s5);
		add(s12);
		add(sAll);
		chain(List.of(s5, s12, sAll));
		add(new Achievement("fok.muilezel", c, "Crossbreed", "Breed a mule (horse + donkey)", Items.CHEST, R,
				goal(1, (p, d) -> d.bred.contains("mule") ? 1 : 0), null));
		add(new Achievement("fok.blauw", c, "One in Twelve Hundred", "Breed a blue axolotl", Items.AXOLOTL_BUCKET, M,
				goal(1, (p, d) -> d.bred.contains("axolotl:blue") ? 1 : 0), null));

		ladder(c, "dorp", Items.EMERALD, "A villager is born while you're nearby|%,d villagers born near you", new Counter((p, d) -> d.villagers),
				1, r(U, R, E), 1, "Special Delivery", 10, "Baby Boom", 50, "Mayor");
		ladder(c, "genees", Items.GOLDEN_APPLE, "Cure a zombie villager|Cure %,d zombie villagers", new Counter((p, d) -> d.cured), 1,
				r(R, E, L), 1, "Village Doctor", 10, "Miracle Worker", 50, "Plague Doctor");

		ladder(c, "oogst", Items.WHEAT, "Gather %,d crops", gathered(itemsOf("wheat", "carrot", "potato", "beetroot", "nether_wart",
				"cocoa_beans", "sweet_berries", "glow_berries", "melon_slice", "pumpkin", "sugar_cane", "torchflower", "pitcher_plant")), 1,
				r(C, U, R, E, L), 100, "Green Thumb", 1000, "Farmhand", 10000, "Harvester", 50000, "Breadbasket", 250000, "Agricultural Revolution");
		ladder(c, "vis", Items.FISHING_ROD, "Reel something in|Reel something in %,d times", custom("fish_caught"), 1, r(C, U, R, E, L, M),
				1, "Hooked", 10, "Angler", 100, "Fisherman", 1000, "Old Sea Dog", 10000, "Ocean Emptier", 50000, "Sea Has No Fish Left");
		ladder(c, "scheren", Items.SHEARS, "Use shears %,d times", used(itemsOf("shears")), 1, r(C, U, R),
				10, "Snip Snip", 100, "Wool Gatherer", 1000, "Master Shearer");
		ladder(c, "beendermeel", Items.BONE_MEAL, "Use %,d bone meal", used(itemsOf("bone_meal")), 1, r(C, U), 64, "Fertilizer", 1000, "Grow Master");
		ladder(c, "planten", Items.OAK_SAPLING, "Plant %,d saplings", used(itemsWhere(id -> is(id, ItemTags.SAPLINGS))), 1, r(C, U, R),
				10, "Tree Hugger", 100, "Reforester", 1000, "Forest Maker");
		ladder(c, "handel", Items.EMERALD, "Trade with a villager|Trade with villagers %,d times", custom("traded_with_villager"), 1,
				r(C, U, R, E, L, M), 1, "First Deal", 10, "Customer", 100, "Haggler", 1000, "Tycoon", 10000, "Merchant Prince", 50000, "Market Mogul");
		ladder(c, "praten", Items.BOOK, "Talk to villagers %,d times", custom("talked_to_villager"), 1, r(C, U), 10, "Small Talk", 100, "Village Gossip");
	}

	// ---------------------------------------------------------------- exploring

	private static void buildExploring() {
		Category c = Category.ONTDEKKEN;
		long km = 100_000, m = 100;
		ladder(c, "loop", Items.LEATHER_BOOTS, "Walk %,d km", custom("walk_one_cm", "sprint_one_cm"), km, r(C, U, R, L, M),
				1, "First Steps", 10, "Hiker", 100, "Traveler", 1000, "Globetrotter", 10000, "Never Stops Walking");
		ladder(c, "zwem", Items.TURTLE_HELMET, "Swim %,d km", custom("swim_one_cm"), km, r(C, U, R), 1, "Paddler", 10, "Swimmer",
				100, "Channel Crosser");
		ladder(c, "boot", Items.OAK_BOAT, "Row %,d km by boat", custom("boat_one_cm"), km, r(C, U, R), 5, "Rower", 50, "Sailor", 250, "Admiral");
		ladder(c, "paard", Items.SADDLE, "Ride %,d km on horseback", custom("horse_one_cm"), km, r(C, U, R), 5, "Rider", 50, "Cavalier",
				250, "Long Rider");
		ladder(c, "vlieg", Items.ELYTRA, "Fly %,d km with an elytra", custom("aviate_one_cm"), km, r(U, R, E, L, M),
				1, "First Flight", 10, "Free as a Bird", 100, "Sky Captain", 500, "Ace Pilot", 5000, "Sky Sovereign");
		ladder(c, "mijnkar", Items.MINECART, "Ride %,d km by minecart", custom("minecart_one_cm"), km, r(C, U, R), 1, "Rail Rider",
				10, "Conductor", 100, "Iron Horse");
		ladder(c, "strider", Items.WARPED_FUNGUS_ON_A_STICK, "Ride a strider %,d m", custom("strider_one_cm"), m, r(U, R),
				100, "Lava Surfer", 1000, "Strider Jockey");
		ladder(c, "varken", Items.CARROT_ON_A_STICK, "Ride a pig %,d m", custom("pig_one_cm"), m, r(U, R), 100, "Pig Rider", 1000, "Bacon Express");
		ladder(c, "spookrit", Ui.item("white_harness"), "Fly %,d km on a happy ghast", custom("happy_ghast_one_cm"), km, r(U, R),
				1, "Cloud Rider", 10, "Sky Sailor");
		ladder(c, "klim", Items.LADDER, "Climb %,d m", custom("climb_one_cm"), m, r(C, U, R, E), 100, "Climber", 1000, "Ladder Legend",
				10000, "Human Fly", 100000, "Ladder to Heaven");
		ladder(c, "sluip", Items.LEATHER_LEGGINGS, "Sneak %,d m", custom("crouch_one_cm"), m, r(U, R, E), 1000, "Sneaky", 10000, "Ninja",
				100000, "Shadow Walker");
		ladder(c, "val", Items.FEATHER, "Fall %,d m in total", custom("fall_one_cm"), m, r(C, U, R, E), 1000, "Gravity Check",
				10000, "Gravity's Favourite", 100000, "Skydiver Without a Parachute", 1000000, "Professional Faller");

		// With the Info module: what your journal holds
		ladder(c, "plekken", Items.FILLED_MAP, "Find %,d places (villages, temples, peaks, lands…)", discovered((dd, p) -> dd.places(p)), 1,
				r(C, U, R, E, L), 10, "Wanderer", 50, "Explorer", 150, "Pathfinder", 400, "Cartographer", 1000, "World Walker");
		ladder(c, "biomen", Items.GRASS_BLOCK, "Visit %,d different biomes", discovered((dd, p) -> dd.biomes(p)), 1, r(C, U, R, L),
				10, "Tourist", 25, "Sightseer", 40, "Naturalist", 55, "Adventuring Legend");
		ladder(c, "dorpen", Items.BELL, "Find your first village|Find %,d villages", discovered((dd, p) -> dd.ofKind(p, "Village")), 1,
				r(C, U, R, E), 1, "Neighbour", 5, "Village Visitor", 25, "Village Hopper", 100, "Ambassador");
		ladder(c, "toppen", Items.STONE, "Stand on a mountain peak|Stand on %,d mountain peaks", discovered((dd, p) -> dd.ofKind(p, "Peak")), 1,
				r(C, U, R), 1, "Hilltopper", 10, "Mountaineer", 50, "Peak Bagger");
		ladder(c, "meren", Items.WATER_BUCKET, "Find a lake|Find %,d lakes", discovered((dd, p) -> dd.ofKind(p, "Lake")), 1, r(C, U, R),
				1, "Lakeside", 10, "Lake Hopper", 50, "Lord of the Lakes");
		ladder(c, "grotten", Items.TORCH, "Find a big cavern|Find %,d big caverns", discovered((dd, p) -> dd.ofKind(p, "Cavern")), 1, r(C, U, R),
				1, "Spelunker", 10, "Cave Diver", 50, "Deep Delver");
		Services.discoveries().ifPresent(disc -> {
			int total = disc.structureKindsTotal();
			ladder(c, "bouwwerken", Items.SPYGLASS, "Find %,d different kinds of structure", discovered((dd, p) -> dd.structureKinds(p)), 1,
					r(U, R, L), 5, "Curious", 10, "Seasoned Explorer", total, "Seen It All");
			find(c, "vondst.stad", "Hush", "Find an ancient city", Items.ECHO_SHARD, R, "Ancient City");
			find(c, "vondst.villa", "House Guest", "Find a woodland mansion", Items.DARK_OAK_DOOR, R, "Woodland Mansion");
			find(c, "vondst.endstad", "Void Tourist", "Find an end city", Items.PURPUR_BLOCK, E, "End City");
			find(c, "vondst.proef", "Trial and Error", "Find trial chambers", Ui.item("copper_block"), U, "Trial Chambers");
		});
		buildStories(c);
	}

	/** The stories found gear tells (Info): every part of one held, one achievement each; and a ladder. */
	private static void buildStories(Category c) {
		Services.Stories st = Services.stories().orElse(null);
		if (st == null || st.titles().isEmpty()) return;
		ladder(c, "verhaal.delen", Items.WRITABLE_BOOK, "Find a part of a story|Find %,d parts of stories", new Counter((p, d) -> st.pieces(p)), 1,
				r(U, R, E), 1, "Once Upon a Time", 10, "Page Turner", 50, "Bookworm");
		int total = st.titles().size();
		for (var e : st.titles().entrySet()) {
			String story = e.getKey();
			int parts = st.parts(story);
			add(new Achievement("verhaal." + story, c, e.getValue(), "Hold every part of the story \"" + e.getValue() + "\" (" + parts + ")",
					Items.WRITTEN_BOOK, E, goal(parts, (p, d) -> st.held(p, story)), null));
		}
		Counter complete = new Counter((p, d) -> {
			long n = 0;
			for (String story : st.titles().keySet()) if (st.held(p, story) >= st.parts(story)) n++;
			return n;
		});
		ladder(c, "verhalen", Items.BOOKSHELF, "Know a whole story|Know %,d whole stories", complete, 1, r(R, E, L, M),
				1, "Storyteller", 5, "Lorekeeper", 15, "Keeper of Tales", total, "Every Story Told");
	}

	private static void find(Category c, String id, String name, String desc, Item icon, Rarity r, String kind) {
		Counter n = discovered((dd, p) -> dd.ofKind(p, kind));
		if (n == null) return;
		add(new Achievement(id, c, name, desc, icon, r, goal(1, (p, d) -> n.get(p, d)), null));
	}

	// ---------------------------------------------------------------- crafting and building

	private static void buildCrafting() {
		Category c = Category.MAKEN;
		List<Item> every = itemsWhere(id -> true);
		Counter crafted = new Counter((p, d) -> {
			long n = 0;
			for (Item i : every) n += p.getStats().getValue(Stats.ITEM_CRAFTED, i);
			return n;
		});
		ladder(c, "maak", Items.CRAFTING_TABLE, "Craft %,d items", crafted, 1, r(C, U, R, E, L),
				100, "Tinkerer", 1000, "Craftsman", 10000, "Artisan", 100000, "Master Crafter", 1000000, "One-Person Factory");
		ladder(c, "bouw", Items.BRICKS, "Place %,d blocks", used(itemsWhere(id -> item(id) instanceof BlockItem)), 1, r(C, U, R, E, L),
				500, "Bricklayer", 5000, "Builder", 50000, "Architect", 200000, "Master Builder", 1000000, "World Builder");
		ladder(c, "fakkel", Items.TORCH, "Place %,d torches", used(itemsOf("torch", "soul_torch", "copper_torch")), 1, r(C, U, R),
				64, "Torchbearer", 640, "Light Bringer", 5000, "Sunmaker");
		ladder(c, "spoor", Items.RAIL, "Lay %,d rails", used(itemsOf("rail", "powered_rail", "detector_rail", "activator_rail")), 1, r(C, U),
				64, "Track Layer", 1000, "Railway Builder");
		ladder(c, "draad", Items.REDSTONE, "Lay %,d redstone dust", used(itemsOf("redstone")), 1, r(C, U, R),
				64, "Wire Puller", 1000, "Engineer", 10000, "Redstone Wizard");
		ladder(c, "tover", Items.ENCHANTING_TABLE, "Enchant an item|Enchant %,d items", custom("enchant_item"), 1, r(C, U, E, L),
				1, "Spark of Magic", 10, "Sorcerer's Apprentice", 100, "Archmage", 500, "Grand Enchanter");
		ladder(c, "aambeeld", Items.ANVIL, "Use an anvil %,d times", custom("interact_with_anvil"), 1, r(C, U), 10, "Anvil Apprentice",
				100, "Anvil Veteran");
		ladder(c, "brouw", Items.BREWING_STAND, "Use a brewing stand %,d times", custom("interact_with_brewingstand"), 1, r(C, U, R),
				10, "Potion Dabbler", 100, "Alchemist", 1000, "Grand Alchemist");
		ladder(c, "oven", Items.FURNACE, "Use a furnace, smoker or blast furnace %,d times", custom("interact_with_furnace",
				"interact_with_smoker", "interact_with_blast_furnace"), 1, r(C, U, R), 10, "Stoker", 100, "Smelter", 1000, "Forgemaster");
		ladder(c, "smid", Items.SMITHING_TABLE, "Use a smithing table %,d times", custom("interact_with_smithing_table"), 1, r(U, R),
				10, "Smith's Apprentice", 100, "Armourer");
		ladder(c, "werkbank", Items.CRAFTING_TABLE, "Open a crafting table %,d times", custom("interact_with_crafting_table"), 1, r(C, U),
				100, "Bench Warmer", 1000, "Workbench Wizard");
		ladder(c, "steenhouwer", Items.STONECUTTER, "Use a stonecutter %,d times", custom("interact_with_stonecutter"), 1, r(C), 100, "Mason");
		ladder(c, "weefgetouw", Items.LOOM, "Use a loom %,d times", custom("interact_with_loom"), 1, r(C), 10, "Weaver");
		ladder(c, "kaarten", Items.CARTOGRAPHY_TABLE, "Use a cartography table %,d times", custom("interact_with_cartography_table"), 1,
				r(C), 10, "Mapmaker");
		ladder(c, "slijpsteen", Items.GRINDSTONE, "Use a grindstone %,d times", custom("interact_with_grindstone"), 1, r(C), 10, "Disenchanter");
		ladder(c, "baken", Items.BEACON, "Open a beacon", custom("interact_with_beacon"), 1, r(R), 1, "Beacon of Hope");
		ladder(c, "noot", Items.NOTE_BLOCK, "Play %,d notes on a note block", custom("play_noteblock"), 1, r(C, U), 100, "Musician", 1000, "Composer");
		ladder(c, "plaat", Items.JUKEBOX, "Play %,d records", custom("play_record"), 1, r(C, U), 10, "DJ", 100, "Party Animal");
		ladder(c, "pot", Items.FLOWER_POT, "Pot %,d plants", custom("pot_flower"), 1, r(C, U), 10, "Plant Parent", 100, "Gardener");
		ladder(c, "bel", Items.BELL, "Ring a bell %,d times", custom("bell_ring"), 1, r(C, U), 10, "Bell Ringer", 100, "Town Crier");
		ladder(c, "kist", Items.CHEST, "Open %,d chests", custom("open_chest"), 1, r(C, U, R), 100, "Nosy", 1000, "Hoarder", 10000, "Treasurer");
		ladder(c, "enderkist", Items.ENDER_CHEST, "Open an ender chest %,d times", custom("open_enderchest"), 1, r(C, U),
				10, "Pocket Dimension", 100, "Void Banker");
		ladder(c, "shulkerdoos", Items.SHULKER_BOX, "Open a shulker box %,d times", custom("open_shulker_box"), 1, r(U, R),
				10, "Unboxing", 100, "Box Hoarder");
		ladder(c, "doel", Items.TARGET, "Hit a target block %,d times", custom("target_hit"), 1, r(C, U), 10, "Sharpshooter", 100, "Marksman");
	}

	// ---------------------------------------------------------------- together (server goals)

	private static void buildTogether() {
		Category c = Category.SAMEN;
		for (Goals.Kind k : Goals.kinds()) {
			String id = "samen." + k.id();
			Rarity help = k.helped(), champ = k.champion();
			Achievement helped = new Achievement(id, c, k.name(), "Help the server reach the server goal " + k.name(), k.icon(), help,
					goal(1, (p, d) -> Goals.helped(p.getUUID(), k.id()) ? 1 : 0), null);
			// The next grade of the same goal: helped, and did the most
			Achievement best = new Achievement(id + ".kampioen", c, k.name() + " Champion", "Do the most for the server goal " + k.name(), k.icon(),
					champ, goal(2, (p, d) -> (Goals.helped(p.getUUID(), k.id()) ? 1 : 0) + (Goals.champion(p.getUUID(), k.id()) ? 1 : 0)), null);
			add(helped);
			add(best);
			chain(List.of(helped, best));
		}
		if (Goals.kinds().isEmpty()) return;
		Counter helped = new Counter((p, d) -> Goals.helpedCount(p.getUUID()));
		ladder(c, "samen.geholpen", Items.BELL, "Help reach a server goal|Help reach %,d server goals", helped, 1, r(C, U, R, E, L, M),
				1, "Team Player", 5, "Helping Hand", 10, "Pillar of the Server", 25, "Heart of the Server", 50, "Soul of the Server",
				100, "The Server Itself");
		Counter champs = new Counter((p, d) -> Goals.championCount(p.getUUID()));
		ladder(c, "samen.kampioen", Items.GOLDEN_HELMET, "Do the most for a server goal|Do the most for %,d server goals", champs, 1,
				r(R, E, L, M), 1, "Champion", 3, "Triple Champion", 10, "Grand Champion", 25, "Undisputed");
		int areas = Goals.areas().size();
		add(new Achievement("samen.overal", c, "Jack of All Trades", "Help reach a server goal in every corner of the game (" + areas + ")",
				Items.COMPASS, E, goal(areas, (p, d) -> Goals.areasHelped(p.getUUID())), null));
	}

	// ---------------------------------------------------------------- playtime and life

	private static void buildLife() {
		Category c = Category.TIJD;
		Counter hours = custom("play_time");
		ladder(c, "tijd", Items.CLOCK, "Play %,d hour on the server|Play %,d hours on the server", hours, 72000, r(C, C, U, U, R, E, L, M, M, M),
				1, "Welcome!", 5, "Getting Comfy", 10, "Returning Guest", 25, "Settler", 50, "Regular", 100, "Resident", 250, "Veteran",
				500, "Legend", 1000, "Immortal", 2500, "Part of the Furniture");
		ladder(c, "leven", Items.GOLDEN_APPLE, "Survive %,d hours without dying", custom("time_since_death"), 72000, r(U, R, E, L, M),
				2, "Careful", 10, "Survivor", 25, "Still Standing", 50, "Untouchable", 100, "Death Who?");
		List<Item> food = itemsWhere(id -> has(id, DataComponents.FOOD));
		ladder(c, "eten", Items.BREAD, "Eat %,d times", used(food), 1, r(C, U, R, E, L), 100, "Snacker", 1000, "Foodie", 10000, "Bottomless Pit",
				50000, "Black Hole Stomach", 200000, "Eats the World");
		ladder(c, "taart", Items.CAKE, "Eat a slice of cake|Eat %,d slices of cake", custom("eat_cake_slice"), 1, r(C, U, R),
				1, "Cake Day", 50, "Sweet Tooth", 500, "Cake Monster");
		ladder(c, "slaap", Ui.item("red_bed"), "Sleep in a bed %,d times", custom("sleep_in_bed"), 1, r(C, R, E),
				10, "Sleepyhead", 100, "Hibernator", 365, "A Year of Dreams");
		ladder(c, "spring", Items.RABBIT_FOOT, "Jump %,d times", custom("jump"), 1, r(C, U, R, E), 1000, "Hop Skip", 10000, "Kangaroo",
				100000, "Bouncy Ball", 1000000, "Pogo Stick");
		List<Item> breakable = itemsWhere(id -> has(id, DataComponents.MAX_DAMAGE));
		Counter broken = breakable.isEmpty() ? null : new Counter((p, d) -> {
			long n = 0;
			for (Item i : breakable) n += p.getStats().getValue(Stats.ITEM_BROKEN, i);
			return n;
		});
		ladder(c, "kapot", Items.STONE_PICKAXE, "Wear out %,d tools, weapons or armor", broken, 1, r(C, U), 10, "Wear and Tear",
				100, "Tool Breaker");
		ladder(c, "dood", Items.SKELETON_SKULL, "Die %,d times", custom("deaths"), 1, r(C, U, R, E, L), 10, "Accident Prone", 100, "Nine Lives",
				500, "Respawn Enjoyer", 1000, "Death's Best Customer", 5000, "Frequent Dier");

		// Tasting: different foods eaten, up to everything
		if (food.size() > 5) {
			List<String> ids = food.stream().map(i -> BuiltInRegistries.ITEM.getKey(i).getPath()).toList();
			Counter tasted = new Counter((p, d) -> {
				long n = 0;
				for (Item i : food) if (p.getStats().getValue(Stats.ITEM_USED, i) > 0) n++;
				return n;
			});
			Achievement.Goal everything = new Achievement.Goal() {
				@Override
				public long current(ServerPlayer p, CollectorMod.Data d) {
					return tasted.get(p, d);
				}

				@Override
				public long target() {
					return food.size();
				}

				@Override
				public boolean has(ServerPlayer p, CollectorMod.Data d, String item) {
					return p.getStats().getValue(Stats.ITEM_USED, item(item)) > 0;
				}
			};
			Achievement t10 = new Achievement("proef.10", c, "Taste Tester", "Eat 10 different foods", Items.APPLE, C,
					goal(10, (p, d) -> tasted.get(p, d)), null);
			Achievement t25 = new Achievement("proef.25", c, "Food Critic", "Eat 25 different foods", Items.COOKED_BEEF, U,
					goal(25, (p, d) -> tasted.get(p, d)), null);
			Achievement all = new Achievement("proef.alles", c, "Gourmet", "Eat everything edible (" + food.size() + ")", Items.GOLDEN_CARROT,
					E, everything, ids);
			add(t10);
			add(t25);
			add(all);
			chain(List.of(t10, t25, all));
		}
	}
}
