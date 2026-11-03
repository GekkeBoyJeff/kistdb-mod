package nl.libreria.collector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
		collection("wol", "Wolhandelaar", "Alle kleuren wol", Ui.item("white_wool"), Rarity.ONGEWOON, id -> id.endsWith("_wool"));
		collection("kleurstof", "Regenboog", "Alle kleurstoffen", Ui.item("red_dye"), Rarity.ONGEWOON, id -> id.endsWith("_dye"));
		collection("glas", "Glazenier", "Alle kleuren gebrandschilderd glas", Ui.item("light_blue_stained_glass"), Rarity.ONGEWOON,
				id -> id.endsWith("_stained_glass"));
		collection("beton", "Betonmolen", "Alle kleuren beton", Ui.item("orange_concrete"), Rarity.ONGEWOON, id -> id.endsWith("_concrete"));
		collection("bedden", "Bedden-paleis", "Alle kleuren bedden", Ui.item("red_bed"), Rarity.ONGEWOON, id -> id.endsWith("_bed"));
		collection("kaarsen", "Kaarslicht", "Alle kaarsen", Ui.item("candle"), Rarity.ONGEWOON, id -> id.equals("candle") || id.endsWith("_candle"));
		collection("zaden", "Zaaier", "Alle zaden", Items.WHEAT_SEEDS, Rarity.ONGEWOON,
				id -> id.endsWith("_seeds") || id.equals("pitcher_pod"));
		collection("stammen", "Houthakker", "Alle soorten stammen", Items.OAK_LOG, Rarity.ONGEWOON,
				id -> is(id, ItemTags.LOGS) && !id.startsWith("stripped_") && !id.endsWith("_wood") && !id.endsWith("_hyphae"));
		collection("glazuur", "Pottenbakker", "Alle geglazuurde terracotta", Ui.item("magenta_glazed_terracotta"), Rarity.ZELDZAAM,
				id -> id.endsWith("_glazed_terracotta"));
		collection("bloemen", "Bloemist", "Alle bloemen", Items.POPPY, Rarity.ZELDZAAM, id -> is(id, tag("flowers")));
		collection("boompjes", "Kweker", "Alle jonge boompjes", Items.OAK_SAPLING, Rarity.ZELDZAAM, id -> is(id, ItemTags.SAPLINGS));
		collection("erts", "Geoloog", "Alle ertsen (met zijdezacht)", Items.DIAMOND_ORE, Rarity.ZELDZAAM,
				id -> id.endsWith("_ore") || id.equals("ancient_debris"));
		collection("mineralen", "Schatgraver", "Alle edelstenen en metalen", Items.DIAMOND, Rarity.ZELDZAAM, id -> Set.of("coal", "charcoal",
				"raw_iron", "iron_ingot", "raw_copper", "copper_ingot", "raw_gold", "gold_ingot", "redstone", "lapis_lazuli", "diamond",
				"emerald", "quartz", "amethyst_shard", "netherite_scrap", "netherite_ingot", "echo_shard", "resin_brick").contains(id));
		collection("emmers", "Emmerdrager", "Alle soorten emmers", Items.WATER_BUCKET, Rarity.ZELDZAAM,
				id -> id.equals("bucket") || id.endsWith("_bucket"));
		collection("bundels", "Inpakker", "Alle bundels", Items.BUNDLE, Rarity.ZELDZAAM, id -> id.endsWith("bundle"));
		collection("koraal", "Rifduiker", "Alle koraal", Ui.item("tube_coral"), Rarity.ZELDZAAM, id -> id.contains("coral"));
		collection("banier", "Vaandeldrager", "Alle banierpatronen", Items.FLOWER_BANNER_PATTERN, Rarity.ZELDZAAM,
				id -> id.endsWith("_banner_pattern"));
		collection("eten", "Voorraadkast", "Alles wat je kunt eten", Items.BREAD, Rarity.ZELDZAAM, id -> has(id, DataComponents.FOOD));
		collection("diamant", "Diamanten uitrusting", "Al het diamanten gereedschap en harnas", Items.DIAMOND_CHESTPLATE, Rarity.ZELDZAAM,
				id -> id.startsWith("diamond_") && has(id, DataComponents.MAX_DAMAGE));
		collection("tuig", "Ghastruiter", "Alle kleuren tuigen", Ui.item("white_harness"), Rarity.EPISCH, id -> id.endsWith("_harness"));
		collection("shulker", "Doosjesmens", "Alle kleuren shulkerdozen", Items.SHULKER_BOX, Rarity.EPISCH, id -> id.endsWith("shulker_box"));
		collection("scherven", "Archeoloog", "Alle aardewerkscherven", Items.ARCHER_POTTERY_SHERD, Rarity.EPISCH,
				id -> id.endsWith("_pottery_sherd"));
		collection("netherite", "Netherite uitrusting", "Al het netherite gereedschap en harnas", Items.NETHERITE_CHESTPLATE, Rarity.EPISCH,
				id -> id.startsWith("netherite_") && has(id, DataComponents.MAX_DAMAGE));
		collection("platen", "Platenbaas", "Alle muziekplaten", Items.MUSIC_DISC_CAT, Rarity.LEGENDARISCH, id -> id.startsWith("music_disc_"));
		collection("sjablonen", "Smid", "Alle smeedsjablonen", Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, Rarity.LEGENDARISCH,
				id -> id.endsWith("_smithing_template"));
		collection("koppen", "Koppensneller", "Alle mobkoppen", Items.CREEPER_HEAD, Rarity.LEGENDARISCH, id -> Set.of("skeleton_skull",
				"wither_skeleton_skull", "zombie_head", "creeper_head", "piglin_head", "dragon_head").contains(id));
		collection("schatten", "Schatkamer", "De zeldzaamste schatten", Items.NETHER_STAR, Rarity.LEGENDARISCH, id -> Set.of("elytra",
				"nether_star", "totem_of_undying", "heart_of_the_sea", "trident", "beacon", "enchanted_golden_apple", "heavy_core", "mace",
				"conduit", "sniffer_egg", "recovery_compass", "ominous_trial_key", "dragon_breath").contains(id));

		// Mijlpalen over alle items heen
		int all = allItems.size();
		long[] steps = {50, 150, 300, 500, 800, 1100};
		Rarity[] rarities = {Rarity.GEWOON, Rarity.ONGEWOON, Rarity.ZELDZAAM, Rarity.EPISCH, Rarity.LEGENDARISCH, Rarity.LEGENDARISCH};
		String[] roman = {"I", "II", "III", "IV", "V", "VI"};
		for (int i = 0; i < steps.length; i++) {
			if (steps[i] >= all) break;
			long target = steps[i];
			add(new Achievement("items." + target, Category.VERZAMELEN, "Verzamelaar " + roman[i], target + " verschillende items",
					Items.CHEST, rarities[i], goal(target, (p, d) -> countAll(d)), null));
		}
		add(new Achievement("items.alles", Category.VERZAMELEN, "Item Completionist", "Elk item dat er is (" + all + ")",
				Items.NETHER_STAR, Rarity.MYTHISCH, goal(all, (p, d) -> countAll(d)), allItems));
		add(new Achievement("items.drakenei", Category.VERZAMELEN, "Hoeder van het ei", "Houd het enige drakenei vast",
				Items.DRAGON_EGG, Rarity.MYTHISCH, goal(1, (p, d) -> d.items.contains("dragon_egg") ? 1 : 0), null));
	}

	public static long countAll(CollectorMod.Data d) {
		long n = 0;
		for (String id : allItems) if (d.items.contains(id)) n++;
		return n;
	}

	// ---------------------------------------------------------------- fokken

	private static void buildBreeding() {
		ToLongBiFunction<ServerPlayer, CollectorMod.Data> bred = (p, d) -> p.getStats().getValue(Stats.CUSTOM, Stats.ANIMALS_BRED);
		add(new Achievement("fok.1", Category.FOKKEN, "Eerste kroost", "Fok een dier", Items.WHEAT, Rarity.GEWOON, goal(1, bred), null));
		add(new Achievement("fok.25", Category.FOKKEN, "Boerderij", "Fok 25 dieren", Items.HAY_BLOCK, Rarity.ONGEWOON, goal(25, bred), null));
		add(new Achievement("fok.100", Category.FOKKEN, "Fokker", "Fok 100 dieren", Items.GOLDEN_CARROT, Rarity.ZELDZAAM, goal(100, bred), null));
		add(new Achievement("fok.500", Category.FOKKEN, "Veehouder", "Fok 500 dieren", Items.LEAD, Rarity.EPISCH, goal(500, bred), null));
		add(new Achievement("fok.2000", Category.FOKKEN, "Dierenimperium", "Fok 2000 dieren", Items.GOLDEN_APPLE, Rarity.LEGENDARISCH,
				goal(2000, bred), null));

		List<String> species = new ArrayList<>();
		for (String s : BREEDABLE) {
			if (BuiltInRegistries.ENTITY_TYPE.containsKey(Identifier.withDefaultNamespace(s))) species.add(s);
		}
		ToLongBiFunction<ServerPlayer, CollectorMod.Data> kinds = (p, d) -> species.stream().filter(d.bred::contains).count();
		add(new Achievement("soort.5", Category.FOKKEN, "Dierenvriend", "Fok 5 verschillende soorten", Items.BONE, Rarity.ONGEWOON, goal(5, kinds), null));
		add(new Achievement("soort.12", Category.FOKKEN, "Dierentuin", "Fok 12 verschillende soorten", Items.EGG, Rarity.EPISCH, goal(12, kinds), null));
		add(new Achievement("soort.alles", Category.FOKKEN, "Ark van Noach", "Fok elke soort die je kunt fokken (" + species.size() + ")",
				Items.OAK_BOAT, Rarity.LEGENDARISCH, goal(species.size(), kinds), null));
		add(new Achievement("fok.muilezel", Category.FOKKEN, "Kruising", "Fok een muilezel (paard + ezel)", Items.CHEST, Rarity.ZELDZAAM,
				goal(1, (p, d) -> d.bred.contains("mule") ? 1 : 0), null));
		add(new Achievement("fok.blauw", Category.FOKKEN, "Eén op twaalfhonderd", "Fok een blauwe axolotl", Items.AXOLOTL_BUCKET, Rarity.MYTHISCH,
				goal(1, (p, d) -> d.bred.contains("axolotl:blue") ? 1 : 0), null));

		ToLongBiFunction<ServerPlayer, CollectorMod.Data> villagers = (p, d) -> d.villagers;
		add(new Achievement("dorp.1", Category.FOKKEN, "Ooievaar", "Er wordt een dorpeling geboren terwijl jij in de buurt bent",
				Items.EMERALD, Rarity.ONGEWOON, goal(1, villagers), null));
		add(new Achievement("dorp.10", Category.FOKKEN, "Babyboom", "10 dorpelingen geboren in jouw buurt", Ui.item("white_bed"),
				Rarity.ZELDZAAM, goal(10, villagers), null));
		add(new Achievement("dorp.50", Category.FOKKEN, "Burgemeester", "50 dorpelingen geboren in jouw buurt", Items.BELL,
				Rarity.EPISCH, goal(50, villagers), null));
		ToLongBiFunction<ServerPlayer, CollectorMod.Data> cured = (p, d) -> d.cured;
		add(new Achievement("genees.1", Category.FOKKEN, "Dokter", "Genees een zombiedorpeling", Items.GOLDEN_APPLE, Rarity.ZELDZAAM,
				goal(1, cured), null));
		add(new Achievement("genees.10", Category.FOKKEN, "Wonderdokter", "Genees 10 zombiedorpelingen", Items.BREWING_STAND, Rarity.EPISCH,
				goal(10, cured), null));
	}

	// ---------------------------------------------------------------- speeltijd

	private static void buildPlaytime() {
		ToLongBiFunction<ServerPlayer, CollectorMod.Data> hours = (p, d) -> p.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME) / 72000L;
		Object[][] steps = {
				{1, "Welkom!", Rarity.GEWOON, Items.OAK_DOOR},
				{10, "Vaste gast", Rarity.ONGEWOON, Items.CAMPFIRE},
				{50, "Stamgast", Rarity.ZELDZAAM, Items.LANTERN},
				{100, "Inwoner", Rarity.EPISCH, Ui.item("red_bed")},
				{250, "Veteraan", Rarity.LEGENDARISCH, Items.SHIELD},
				{500, "Legende", Rarity.MYTHISCH, Items.TOTEM_OF_UNDYING},
				{1000, "Onsterfelijk", Rarity.MYTHISCH, Items.NETHER_STAR}};
		for (Object[] s : steps) {
			int h = (Integer) s[0];
			add(new Achievement("tijd." + h, Category.TIJD, (String) s[1], h + (h == 1 ? " uur" : " uur") + " op de server gespeeld",
					(Item) s[3], (Rarity) s[2], goal(h, hours), null));
		}
		ToLongBiFunction<ServerPlayer, CollectorMod.Data> alive = (p, d) -> p.getStats().getValue(Stats.CUSTOM, Stats.TIME_SINCE_DEATH) / 72000L;
		add(new Achievement("leven.10", Category.TIJD, "Overlever", "10 uur zonder dood te gaan", Items.GOLDEN_APPLE, Rarity.ZELDZAAM,
				goal(10, alive), null));
		add(new Achievement("leven.50", Category.TIJD, "Ongrijpbaar", "50 uur zonder dood te gaan", Items.ENCHANTED_GOLDEN_APPLE,
				Rarity.LEGENDARISCH, goal(50, alive), null));
	}

	// ---------------------------------------------------------------- avontuur

	private static ToLongBiFunction<ServerPlayer, CollectorMod.Data> custom(Identifier stat) {
		return (p, d) -> p.getStats().getValue(Stats.CUSTOM, stat);
	}

	private static ToLongBiFunction<ServerPlayer, CollectorMod.Data> killed(String entity) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(entity));
		return (p, d) -> p.getStats().getValue(Stats.ENTITY_KILLED, type);
	}

	private static void steps(String id, String[] names, String unit, Item icon, ToLongBiFunction<ServerPlayer, CollectorMod.Data> f,
			long[] targets, Rarity[] rarities, long divisor) {
		for (int i = 0; i < targets.length; i++) {
			long t = targets[i];
			add(new Achievement(id + "." + t, Category.AVONTUUR, names[i], String.format(unit, t), icon, rarities[i],
					goal(t, (p, d) -> f.applyAsLong(p, d) / divisor), null));
		}
	}

	private static void buildAdventure() {
		ToLongBiFunction<ServerPlayer, CollectorMod.Data> mined = (p, d) -> {
			long n = 0;
			for (Block b : BuiltInRegistries.BLOCK) n += p.getStats().getValue(Stats.BLOCK_MINED, b);
			return n;
		};
		Rarity[] four = {Rarity.GEWOON, Rarity.ONGEWOON, Rarity.ZELDZAAM, Rarity.EPISCH};
		Rarity[] three = {Rarity.ONGEWOON, Rarity.ZELDZAAM, Rarity.EPISCH};
		steps("mijn", new String[] {"Gravertje", "Mijnwerker", "Tunnelbouwer", "Bergverzetter"}, "Mijn %d blokken", Items.IRON_PICKAXE,
				mined, new long[] {1000, 10000, 50000, 200000}, four, 1);
		Block dia = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("diamond_ore"));
		Block deepDia = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("deepslate_diamond_ore"));
		steps("diamant", new String[] {"Diamanten!", "Diamantjager", "Diamantkoning"}, "Mijn %d diamanterts", Items.DIAMOND,
				(p, d) -> (long) p.getStats().getValue(Stats.BLOCK_MINED, dia) + p.getStats().getValue(Stats.BLOCK_MINED, deepDia),
				new long[] {1, 100, 1000}, new Rarity[] {Rarity.GEWOON, Rarity.ZELDZAAM, Rarity.LEGENDARISCH}, 1);
		steps("monster", new String[] {"Monsterjager", "Ongediertebestrijder", "Slachter"}, "Versla %d mobs", Items.IRON_SWORD,
				custom(Stats.MOB_KILLS), new long[] {100, 1000, 10000}, three, 1);
		steps("loop", new String[] {"Wandelaar", "Reiziger", "Wereldreiziger"}, "Loop %d km", Items.LEATHER_BOOTS,
				(p, d) -> (long) p.getStats().getValue(Stats.CUSTOM, Stats.WALK_ONE_CM) + p.getStats().getValue(Stats.CUSTOM, Stats.SPRINT_ONE_CM),
				new long[] {10, 100, 1000}, new Rarity[] {Rarity.ONGEWOON, Rarity.ZELDZAAM, Rarity.LEGENDARISCH}, 100_000);
		steps("vlieg", new String[] {"Vogelvrij", "Piloot"}, "Vlieg %d km met een elytra", Items.ELYTRA, custom(Stats.AVIATE_ONE_CM),
				new long[] {10, 500}, new Rarity[] {Rarity.EPISCH, Rarity.LEGENDARISCH}, 100_000);
		steps("vis", new String[] {"Hengelaar", "Visser", "Oude zeebonk"}, "Vang %d keer iets", Items.FISHING_ROD, custom(Stats.FISH_CAUGHT),
				new long[] {10, 100, 1000}, three, 1);
		steps("handel", new String[] {"Klant", "Handelaar", "Koopman"}, "Handel %d keer met dorpelingen", Items.EMERALD,
				custom(Stats.TRADED_WITH_VILLAGER), new long[] {10, 100, 1000}, three, 1);
		steps("tover", new String[] {"Leerling-tovenaar", "Tovenaar"}, "Betover %d items", Items.ENCHANTING_TABLE, custom(Stats.ENCHANT_ITEM),
				new long[] {10, 100}, new Rarity[] {Rarity.ONGEWOON, Rarity.EPISCH}, 1);
		steps("slaap", new String[] {"Slaapkop", "Winterslaap"}, "Slaap %d keer", Ui.item("red_bed"), custom(Stats.SLEEP_IN_BED),
				new long[] {10, 100}, new Rarity[] {Rarity.GEWOON, Rarity.ZELDZAAM}, 1);
		steps("taart", new String[] {"Zoetekauw"}, "Eet %d taartpunten", Items.CAKE, custom(Stats.EAT_CAKE_SLICE),
				new long[] {50}, new Rarity[] {Rarity.ONGEWOON}, 1);
		steps("spring", new String[] {"Kangoeroe", "Stuiterbal"}, "Spring %d keer", Items.RABBIT_FOOT, custom(Stats.JUMP),
				new long[] {10000, 100000}, new Rarity[] {Rarity.ONGEWOON, Rarity.ZELDZAAM}, 1);
		steps("raid", new String[] {"Held van het dorp", "Beschermheer"}, "Win %d overvallen", Items.BELL, custom(Stats.RAID_WIN),
				new long[] {1, 10}, new Rarity[] {Rarity.ZELDZAAM, Rarity.LEGENDARISCH}, 1);
		steps("dood", new String[] {"Pechvogel", "Kat met negen levens"}, "Ga %d keer dood", Items.SKELETON_SKULL, custom(Stats.DEATHS),
				new long[] {10, 100}, new Rarity[] {Rarity.GEWOON, Rarity.ONGEWOON}, 1);

		add(new Achievement("baas.draak", Category.AVONTUUR, "Drakendoder", "Versla de Enderdraak", Items.DRAGON_HEAD, Rarity.LEGENDARISCH,
				goal(1, killed("ender_dragon")), null));
		add(new Achievement("baas.wither", Category.AVONTUUR, "Witherjager", "Versla de Wither", Items.NETHER_STAR, Rarity.LEGENDARISCH,
				goal(1, killed("wither")), null));
		add(new Achievement("baas.warden", Category.AVONTUUR, "Doodstil", "Versla een Warden", Items.SCULK_SHRIEKER, Rarity.MYTHISCH,
				goal(1, killed("warden")), null));
		add(new Achievement("baas.guardian", Category.AVONTUUR, "Tempelridder", "Versla een oudste guardian", Items.PRISMARINE_SHARD,
				Rarity.EPISCH, goal(1, killed("elder_guardian")), null));

		// Fijnproever: van alles wat eetbaar is een keer gegeten
		List<Item> food = new ArrayList<>();
		for (String id : allItems) if (has(id, DataComponents.FOOD)) food.add(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id)));
		if (food.size() > 5) {
			List<String> ids = food.stream().map(i -> BuiltInRegistries.ITEM.getKey(i).getPath()).toList();
			add(new Achievement("proef.alles", Category.AVONTUUR, "Fijnproever", "Eet alles wat eetbaar is (" + food.size() + ")",
					Items.GOLDEN_CARROT, Rarity.EPISCH, new Achievement.Goal() {
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
