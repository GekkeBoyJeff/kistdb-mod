package nl.libreria.collector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToLongBiFunction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import nl.libreria.core.Ui;
import nl.libreria.collector.Achievement.Category;

/**
 * Alle prestaties. Verzamelingen worden bij het starten opgebouwd uit het item-register (tags en naampatronen), zodat
 * nieuwe items uit een update vanzelf meetellen.
 */
public final class Catalog {
	/** Items die je in survival niet kunt krijgen, of maar één keer per wereld. */
	private static final Set<String> UNOBTAINABLE = Set.of("air", "barrier", "light", "debug_stick", "knowledge_book", "bedrock",
			"end_portal_frame", "reinforced_deepslate", "budding_amethyst", "spawner", "trial_spawner", "vault", "petrified_oak_slab",
			"player_head", "chorus_plant", "frogspawn", "farmland", "dirt_path", "suspicious_sand", "suspicious_gravel", "jigsaw",
			"structure_block", "structure_void", "test_block", "test_instance_block", "dragon_egg", "bundle_of_bundles");

	/** Dieren die je kunt fokken (wat de server niet kent, valt vanzelf weg). */
	static final List<String> BREEDABLE = List.of("cow", "mooshroom", "sheep", "pig", "chicken", "rabbit", "horse", "donkey", "mule",
			"llama", "cat", "wolf", "ocelot", "fox", "panda", "bee", "turtle", "axolotl", "goat", "frog", "strider", "hoglin", "sniffer",
			"camel", "armadillo");

	private static final Map<String, Achievement> BY_ID = new LinkedHashMap<>();
	private static List<String> allItems = List.of();

	private Catalog() {
	}

	public static List<Achievement> all() {
		return List.copyOf(BY_ID.values());
	}

	public static Achievement get(String id) {
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

	// ---------------------------------------------------------------- opbouwen

	static void build() {
		BY_ID.clear();
		List<String> items = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			String id = BuiltInRegistries.ITEM.getKey(item).getPath();
			if (obtainable(id)) items.add(id);
		}
		allItems = List.copyOf(items);

		buildCollections();
		buildBreeding();
		buildPlaytime();
		buildAdventure();
	}

	static boolean obtainable(String id) {
		return !UNOBTAINABLE.contains(id) && !id.endsWith("_spawn_egg") && !id.contains("command_block") && !id.startsWith("infested_");
	}

	private static void add(Achievement a) {
		BY_ID.put(a.id(), a);
	}

	private static Achievement.Goal goal(long target, ToLongBiFunction<ServerPlayer, CollectorMod.Data> current) {
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

	// ---------------------------------------------------------------- verzamelen

	private static void collection(String id, String name, String desc, Item icon, Rarity rarity, Predicate<String> match) {
		List<String> set = new ArrayList<>();
		for (String it : allItems) if (match.test(it)) set.add(it);
		if (set.size() < 2) return;
		List<String> list = List.copyOf(set);
		add(new Achievement("verzamel." + id, Category.VERZAMELEN, name, desc + " (" + list.size() + ")", icon, rarity,
				goal(list.size(), (p, d) -> list.stream().filter(d.items::contains).count()), list));
	}

	/** Item-tag op naam (niet elke tag heeft in 26.x nog een Java-constante). */
	private static net.minecraft.tags.TagKey<Item> tag(String name) {
		return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.withDefaultNamespace(name));
	}

	private static boolean is(String id, net.minecraft.tags.TagKey<Item> tag) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
		return new ItemStack(item).is(tag);
	}

	private static boolean has(String id, net.minecraft.core.component.DataComponentType<?> type) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
		return new ItemStack(item).has(type);
	}

	private static void buildCollections() {
		collection("wol", "Wool Merchant", "Every color of wool", Ui.item("white_wool"), Rarity.UNCOMMON, id -> id.endsWith("_wool"));
		collection("kleurstof", "Over the Rainbow", "Every dye", Ui.item("red_dye"), Rarity.UNCOMMON, id -> id.endsWith("_dye"));
		collection("glas", "Window Shopper", "Every color of stained glass", Ui.item("light_blue_stained_glass"), Rarity.UNCOMMON,
				id -> id.endsWith("_stained_glass"));
		collection("beton", "Cement Mixer", "Every color of concrete", Ui.item("orange_concrete"), Rarity.UNCOMMON, id -> id.endsWith("_concrete"));
		collection("bedden", "Bed Showroom", "Every color of bed", Ui.item("red_bed"), Rarity.UNCOMMON, id -> id.endsWith("_bed"));
		collection("kaarsen", "Candlelight", "Every candle", Ui.item("candle"), Rarity.UNCOMMON, id -> id.equals("candle") || id.endsWith("_candle"));
		collection("zaden", "Seed Bank", "Every kind of seed", Items.WHEAT_SEEDS, Rarity.UNCOMMON,
				id -> id.endsWith("_seeds") || id.equals("pitcher_pod"));
		collection("stammen", "Lumberjack", "Every kind of log", Items.OAK_LOG, Rarity.UNCOMMON,
				id -> is(id, ItemTags.LOGS) && !id.startsWith("stripped_") && !id.endsWith("_wood") && !id.endsWith("_hyphae"));
		collection("glazuur", "Glazed and Confused", "Every glazed terracotta", Ui.item("magenta_glazed_terracotta"), Rarity.RARE,
				id -> id.endsWith("_glazed_terracotta"));
		collection("bloemen", "Florist", "Every flower", Items.POPPY, Rarity.RARE, id -> is(id, tag("flowers")));
		collection("boompjes", "Tree Nursery", "Every sapling", Items.OAK_SAPLING, Rarity.RARE, id -> is(id, ItemTags.SAPLINGS));
		collection("erts", "Geologist", "Every ore (with Silk Touch)", Items.DIAMOND_ORE, Rarity.RARE,
				id -> id.endsWith("_ore") || id.equals("ancient_debris"));
		collection("mineralen", "Prospector", "Every gem and metal", Items.DIAMOND, Rarity.RARE, id -> Set.of("coal", "charcoal",
				"raw_iron", "iron_ingot", "raw_copper", "copper_ingot", "raw_gold", "gold_ingot", "redstone", "lapis_lazuli", "diamond",
				"emerald", "quartz", "amethyst_shard", "netherite_scrap", "netherite_ingot", "echo_shard", "resin_brick").contains(id));
		collection("emmers", "Bucket List", "Every kind of bucket", Items.WATER_BUCKET, Rarity.RARE,
				id -> id.equals("bucket") || id.endsWith("_bucket"));
		collection("bundels", "Bundle of Joy", "Every bundle", Items.BUNDLE, Rarity.RARE, id -> id.endsWith("bundle"));
		collection("koraal", "Reef Diver", "Every coral", Ui.item("tube_coral"), Rarity.RARE, id -> id.contains("coral"));
		collection("banier", "Standard Bearer", "Every banner pattern", Items.FLOWER_BANNER_PATTERN, Rarity.RARE,
				id -> id.endsWith("_banner_pattern"));
		collection("eten", "Full Pantry", "Everything you can eat", Items.BREAD, Rarity.RARE, id -> has(id, DataComponents.FOOD));
		collection("diamant", "Full Diamond", "All diamond tools and armor", Items.DIAMOND_CHESTPLATE, Rarity.RARE,
				id -> id.startsWith("diamond_") && has(id, DataComponents.MAX_DAMAGE));
		collection("tuig", "Ghast Rider", "Every color of harness", Ui.item("white_harness"), Rarity.EPIC, id -> id.endsWith("_harness"));
		collection("shulker", "Thinking Inside the Box", "Every color of shulker box", Items.SHULKER_BOX, Rarity.EPIC,
				id -> id.endsWith("shulker_box"));
		collection("scherven", "Archaeologist", "Every pottery sherd", Items.ARCHER_POTTERY_SHERD, Rarity.EPIC,
				id -> id.endsWith("_pottery_sherd"));
		collection("netherite", "Full Netherite", "All netherite tools and armor", Items.NETHERITE_CHESTPLATE, Rarity.EPIC,
				id -> id.startsWith("netherite_") && has(id, DataComponents.MAX_DAMAGE));
		collection("platen", "Disc Jockey", "Every music disc", Items.MUSIC_DISC_CAT, Rarity.LEGENDARY, id -> id.startsWith("music_disc_"));
		collection("sjablonen", "Master Smith", "Every smithing template", Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, Rarity.LEGENDARY,
				id -> id.endsWith("_smithing_template"));
		collection("koppen", "Headhunter", "Every mob head", Items.CREEPER_HEAD, Rarity.LEGENDARY, id -> Set.of("skeleton_skull",
				"wither_skeleton_skull", "zombie_head", "creeper_head", "piglin_head", "dragon_head").contains(id));
		collection("schatten", "Dragon's Hoard", "The rarest treasures", Items.NETHER_STAR, Rarity.LEGENDARY, id -> Set.of("elytra",
				"nether_star", "totem_of_undying", "heart_of_the_sea", "trident", "beacon", "enchanted_golden_apple", "heavy_core", "mace",
				"conduit", "sniffer_egg", "recovery_compass", "ominous_trial_key", "dragon_breath").contains(id));

		// Mijlpalen over alle items heen
		int all = allItems.size();
		long[] steps = {50, 150, 300, 500, 800, 1100};
		Rarity[] rarities = {Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC, Rarity.LEGENDARY, Rarity.LEGENDARY};
		String[] roman = {"I", "II", "III", "IV", "V", "VI"};
		for (int i = 0; i < steps.length; i++) {
			if (steps[i] >= all) break;
			long target = steps[i];
			add(new Achievement("items." + target, Category.VERZAMELEN, "Collector " + roman[i],
					String.format(Locale.US, "%,d different items", target),
					Items.CHEST, rarities[i], goal(target, (p, d) -> countAll(d)), null));
		}
		add(new Achievement("items.alles", Category.VERZAMELEN, "Item Completionist", String.format(Locale.US, "Every item there is (%,d)", all),
				Items.NETHER_STAR, Rarity.MYTHIC, goal(all, (p, d) -> countAll(d)), allItems));
		add(new Achievement("items.drakenei", Category.VERZAMELEN, "Keeper of the Egg", "Hold the one and only dragon egg",
				Items.DRAGON_EGG, Rarity.MYTHIC, goal(1, (p, d) -> d.items.contains("dragon_egg") ? 1 : 0), null));
	}

	public static long countAll(CollectorMod.Data d) {
		long n = 0;
		for (String id : allItems) if (d.items.contains(id)) n++;
		return n;
	}

	// ---------------------------------------------------------------- fokken

	private static void buildBreeding() {
		ToLongBiFunction<ServerPlayer, CollectorMod.Data> bred = (p, d) -> p.getStats().getValue(Stats.CUSTOM, Stats.ANIMALS_BRED);
		add(new Achievement("fok.1", Category.FOKKEN, "Baby Steps", "Breed an animal", Items.WHEAT, Rarity.COMMON, goal(1, bred), null));
		add(new Achievement("fok.25", Category.FOKKEN, "Old MacDonald", "Breed 25 animals", Items.HAY_BLOCK, Rarity.UNCOMMON, goal(25, bred), null));
		add(new Achievement("fok.100", Category.FOKKEN, "Breeder", "Breed 100 animals", Items.GOLDEN_CARROT, Rarity.RARE, goal(100, bred), null));
		add(new Achievement("fok.500", Category.FOKKEN, "Rancher", "Breed 500 animals", Items.LEAD, Rarity.EPIC, goal(500, bred), null));
		add(new Achievement("fok.2000", Category.FOKKEN, "Animal Empire", "Breed 2,000 animals", Items.GOLDEN_APPLE, Rarity.LEGENDARY,
				goal(2000, bred), null));

		List<String> species = new ArrayList<>();
		for (String s : BREEDABLE) {
			if (BuiltInRegistries.ENTITY_TYPE.containsKey(Identifier.withDefaultNamespace(s))) species.add(s);
		}
		ToLongBiFunction<ServerPlayer, CollectorMod.Data> kinds = (p, d) -> species.stream().filter(d.bred::contains).count();
		add(new Achievement("soort.5", Category.FOKKEN, "Animal Lover", "Breed 5 different species", Items.BONE, Rarity.UNCOMMON, goal(5, kinds), null));
		add(new Achievement("soort.12", Category.FOKKEN, "Zookeeper", "Breed 12 different species", Items.EGG, Rarity.EPIC, goal(12, kinds), null));
		add(new Achievement("soort.alles", Category.FOKKEN, "Noah's Ark", "Breed every species that can be bred (" + species.size() + ")",
				Items.OAK_BOAT, Rarity.LEGENDARY, goal(species.size(), kinds), null));
		add(new Achievement("fok.muilezel", Category.FOKKEN, "Crossbreed", "Breed a mule (horse + donkey)", Items.CHEST, Rarity.RARE,
				goal(1, (p, d) -> d.bred.contains("mule") ? 1 : 0), null));
		add(new Achievement("fok.blauw", Category.FOKKEN, "One in Twelve Hundred", "Breed a blue axolotl", Items.AXOLOTL_BUCKET, Rarity.MYTHIC,
				goal(1, (p, d) -> d.bred.contains("axolotl:blue") ? 1 : 0), null));

		ToLongBiFunction<ServerPlayer, CollectorMod.Data> villagers = (p, d) -> d.villagers;
		add(new Achievement("dorp.1", Category.FOKKEN, "Special Delivery", "A villager is born while you're nearby",
				Items.EMERALD, Rarity.UNCOMMON, goal(1, villagers), null));
		add(new Achievement("dorp.10", Category.FOKKEN, "Baby Boom", "10 villagers born near you", Ui.item("white_bed"),
				Rarity.RARE, goal(10, villagers), null));
		add(new Achievement("dorp.50", Category.FOKKEN, "Mayor", "50 villagers born near you", Items.BELL,
				Rarity.EPIC, goal(50, villagers), null));
		ToLongBiFunction<ServerPlayer, CollectorMod.Data> cured = (p, d) -> d.cured;
		add(new Achievement("genees.1", Category.FOKKEN, "Village Doctor", "Cure a zombie villager", Items.GOLDEN_APPLE, Rarity.RARE,
				goal(1, cured), null));
		add(new Achievement("genees.10", Category.FOKKEN, "Miracle Worker", "Cure 10 zombie villagers", Items.BREWING_STAND, Rarity.EPIC,
				goal(10, cured), null));
	}

	// ---------------------------------------------------------------- speeltijd

	private static void buildPlaytime() {
		ToLongBiFunction<ServerPlayer, CollectorMod.Data> hours = (p, d) -> p.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME) / 72000L;
		Object[][] steps = {
				{1, "Welcome!", Rarity.COMMON, Items.OAK_DOOR},
				{10, "Returning Guest", Rarity.UNCOMMON, Items.CAMPFIRE},
				{50, "Regular", Rarity.RARE, Items.LANTERN},
				{100, "Resident", Rarity.EPIC, Ui.item("red_bed")},
				{250, "Veteran", Rarity.LEGENDARY, Items.SHIELD},
				{500, "Legend", Rarity.MYTHIC, Items.TOTEM_OF_UNDYING},
				{1000, "Immortal", Rarity.MYTHIC, Items.NETHER_STAR}};
		for (Object[] s : steps) {
			int h = (Integer) s[0];
			add(new Achievement("tijd." + h, Category.TIJD, (String) s[1],
					String.format(Locale.US, h == 1 ? "Play %,d hour on the server" : "Play %,d hours on the server", h),
					(Item) s[3], (Rarity) s[2], goal(h, hours), null));
		}
		ToLongBiFunction<ServerPlayer, CollectorMod.Data> alive = (p, d) -> p.getStats().getValue(Stats.CUSTOM, Stats.TIME_SINCE_DEATH) / 72000L;
		add(new Achievement("leven.10", Category.TIJD, "Survivor", "Survive 10 hours without dying", Items.GOLDEN_APPLE, Rarity.RARE,
				goal(10, alive), null));
		add(new Achievement("leven.50", Category.TIJD, "Untouchable", "Survive 50 hours without dying", Items.ENCHANTED_GOLDEN_APPLE,
				Rarity.LEGENDARY, goal(50, alive), null));
	}

	// ---------------------------------------------------------------- avontuur

	private static ToLongBiFunction<ServerPlayer, CollectorMod.Data> custom(Identifier stat) {
		return (p, d) -> p.getStats().getValue(Stats.CUSTOM, stat);
	}

	private static ToLongBiFunction<ServerPlayer, CollectorMod.Data> killed(String entity) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(entity));
		return (p, d) -> p.getStats().getValue(Stats.ENTITY_KILLED, type);
	}

	/** {@code unit} is a format with %,d; "singular|plural" picks the left part when the target is 1. */
	private static void steps(String id, String[] names, String unit, Item icon, ToLongBiFunction<ServerPlayer, CollectorMod.Data> f,
			long[] targets, Rarity[] rarities, long divisor) {
		int bar = unit.indexOf('|');
		for (int i = 0; i < targets.length; i++) {
			long t = targets[i];
			String fmt = bar < 0 ? unit : t == 1 ? unit.substring(0, bar) : unit.substring(bar + 1);
			add(new Achievement(id + "." + t, Category.AVONTUUR, names[i], String.format(Locale.US, fmt, t), icon, rarities[i],
					goal(t, (p, d) -> f.applyAsLong(p, d) / divisor), null));
		}
	}

	private static void buildAdventure() {
		ToLongBiFunction<ServerPlayer, CollectorMod.Data> mined = (p, d) -> {
			long n = 0;
			for (Block b : BuiltInRegistries.BLOCK) n += p.getStats().getValue(Stats.BLOCK_MINED, b);
			return n;
		};
		Rarity[] four = {Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC};
		Rarity[] three = {Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC};
		steps("mijn", new String[] {"Little Digger", "Miner", "Tunnel Vision", "Mountain Mover"}, "Mine %,d blocks", Items.IRON_PICKAXE,
				mined, new long[] {1000, 10000, 50000, 200000}, four, 1);
		Block dia = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("diamond_ore"));
		Block deepDia = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("deepslate_diamond_ore"));
		steps("diamant", new String[] {"Diamonds!", "Diamond Hunter", "Diamond King"}, "Mine your first diamond ore|Mine %,d diamond ore",
				Items.DIAMOND,
				(p, d) -> (long) p.getStats().getValue(Stats.BLOCK_MINED, dia) + p.getStats().getValue(Stats.BLOCK_MINED, deepDia),
				new long[] {1, 100, 1000}, new Rarity[] {Rarity.COMMON, Rarity.RARE, Rarity.LEGENDARY}, 1);
		steps("monster", new String[] {"Monster Hunter", "Pest Control", "Slayer"}, "Defeat %,d mobs", Items.IRON_SWORD,
				custom(Stats.MOB_KILLS), new long[] {100, 1000, 10000}, three, 1);
		steps("loop", new String[] {"Hiker", "Traveler", "Globetrotter"}, "Walk %,d km", Items.LEATHER_BOOTS,
				(p, d) -> (long) p.getStats().getValue(Stats.CUSTOM, Stats.WALK_ONE_CM) + p.getStats().getValue(Stats.CUSTOM, Stats.SPRINT_ONE_CM),
				new long[] {10, 100, 1000}, new Rarity[] {Rarity.UNCOMMON, Rarity.RARE, Rarity.LEGENDARY}, 100_000);
		steps("vlieg", new String[] {"Free as a Bird", "Ace Pilot"}, "Fly %,d km with an elytra", Items.ELYTRA, custom(Stats.AVIATE_ONE_CM),
				new long[] {10, 500}, new Rarity[] {Rarity.EPIC, Rarity.LEGENDARY}, 100_000);
		steps("vis", new String[] {"Angler", "Fisherman", "Old Sea Dog"}, "Reel something in %,d times", Items.FISHING_ROD,
				custom(Stats.FISH_CAUGHT), new long[] {10, 100, 1000}, three, 1);
		steps("handel", new String[] {"Customer", "Haggler", "Tycoon"}, "Trade with villagers %,d times", Items.EMERALD,
				custom(Stats.TRADED_WITH_VILLAGER), new long[] {10, 100, 1000}, three, 1);
		steps("tover", new String[] {"Sorcerer's Apprentice", "Archmage"}, "Enchant %,d items", Items.ENCHANTING_TABLE,
				custom(Stats.ENCHANT_ITEM), new long[] {10, 100}, new Rarity[] {Rarity.UNCOMMON, Rarity.EPIC}, 1);
		steps("slaap", new String[] {"Sleepyhead", "Hibernator"}, "Sleep in a bed %,d times", Ui.item("red_bed"), custom(Stats.SLEEP_IN_BED),
				new long[] {10, 100}, new Rarity[] {Rarity.COMMON, Rarity.RARE}, 1);
		steps("taart", new String[] {"Sweet Tooth"}, "Eat %,d slices of cake", Items.CAKE, custom(Stats.EAT_CAKE_SLICE),
				new long[] {50}, new Rarity[] {Rarity.UNCOMMON}, 1);
		steps("spring", new String[] {"Kangaroo", "Bouncy Ball"}, "Jump %,d times", Items.RABBIT_FOOT, custom(Stats.JUMP),
				new long[] {10000, 100000}, new Rarity[] {Rarity.UNCOMMON, Rarity.RARE}, 1);
		steps("raid", new String[] {"Hero of the Village", "Protector"}, "Win a raid|Win %,d raids", Items.BELL, custom(Stats.RAID_WIN),
				new long[] {1, 10}, new Rarity[] {Rarity.RARE, Rarity.LEGENDARY}, 1);
		steps("dood", new String[] {"Accident Prone", "Nine Lives"}, "Die %,d times", Items.SKELETON_SKULL, custom(Stats.DEATHS),
				new long[] {10, 100}, new Rarity[] {Rarity.COMMON, Rarity.UNCOMMON}, 1);

		add(new Achievement("baas.draak", Category.AVONTUUR, "Dragonslayer", "Defeat the Ender Dragon", Items.DRAGON_HEAD, Rarity.LEGENDARY,
				goal(1, killed("ender_dragon")), null));
		add(new Achievement("baas.wither", Category.AVONTUUR, "Wither Hunter", "Defeat the Wither", Items.NETHER_STAR, Rarity.LEGENDARY,
				goal(1, killed("wither")), null));
		add(new Achievement("baas.warden", Category.AVONTUUR, "Dead Silent", "Defeat a Warden", Items.SCULK_SHRIEKER, Rarity.MYTHIC,
				goal(1, killed("warden")), null));
		add(new Achievement("baas.guardian", Category.AVONTUUR, "Knight Templar", "Defeat an Elder Guardian", Items.PRISMARINE_SHARD,
				Rarity.EPIC, goal(1, killed("elder_guardian")), null));

		// Fijnproever: van alles wat eetbaar is een keer gegeten
		List<Item> food = new ArrayList<>();
		for (String id : allItems) if (has(id, DataComponents.FOOD)) food.add(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id)));
		if (food.size() > 5) {
			List<String> ids = food.stream().map(i -> BuiltInRegistries.ITEM.getKey(i).getPath()).toList();
			add(new Achievement("proef.alles", Category.AVONTUUR, "Gourmet", "Eat everything edible (" + food.size() + ")",
					Items.GOLDEN_CARROT, Rarity.EPIC, new Achievement.Goal() {
						@Override
						public long current(ServerPlayer p, CollectorMod.Data d) {
							long n = 0;
							for (Item i : food) if (p.getStats().getValue(Stats.ITEM_USED, i) > 0) n++;
							return n;
						}

						@Override
						public long target() {
							return food.size();
						}

						@Override
						public boolean has(ServerPlayer p, CollectorMod.Data d, String item) {
							Item i = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(item));
							return p.getStats().getValue(Stats.ITEM_USED, i) > 0;
						}
					}, ids));
		}
	}
}
