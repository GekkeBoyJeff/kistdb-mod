package nl.libreria.collector;

import static nl.libreria.collector.Catalog.crafted;
import static nl.libreria.collector.Catalog.custom;
import static nl.libreria.collector.Catalog.gathered;
import static nl.libreria.collector.Catalog.itemsOf;
import static nl.libreria.collector.Catalog.itemsWhere;
import static nl.libreria.collector.Catalog.killed;
import static nl.libreria.collector.Catalog.killedBy;
import static nl.libreria.collector.Catalog.ladder;
import static nl.libreria.collector.Catalog.mined;
import static nl.libreria.collector.Catalog.r;
import static nl.libreria.collector.Catalog.used;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import nl.libreria.collector.Achievement.Category;
import nl.libreria.core.Ui;

/**
 * More achievements, for everything else Minecraft counts: odd deaths, what you eat, throw and break, what you make
 * and place, and the far ends of the big ladders. Some are funny, some take a very long time; every one is a title.
 */
final class Extra {
	private static final Rarity C = Rarity.COMMON, U = Rarity.UNCOMMON, R = Rarity.RARE, E = Rarity.EPIC, L = Rarity.LEGENDARY,
			M = Rarity.MYTHIC;

	/** By name: not every tag still has a Java constant in 26.x. */
	private static final net.minecraft.tags.TagKey<Block> SMALL_FLOWERS = net.minecraft.tags.TagKey.create(
			net.minecraft.core.registries.Registries.BLOCK, net.minecraft.resources.Identifier.withDefaultNamespace("small_flowers"));

	private Extra() {
	}

	static void build() {
		corny();
		mining();
		combat();
		deaths();
		farming();
		exploring();
		making();
		placing();
		life();
	}

	private static void mining() {
		Category c = Category.MIJNBOUW;
		List<Block> ores = new ArrayList<>(), glass = new ArrayList<>(), leaves = new ArrayList<>(), flowers = new ArrayList<>();
		for (Block b : BuiltInRegistries.BLOCK) {
			String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
			if (id.endsWith("_ore") || id.equals("ancient_debris")) ores.add(b);
			if (id.endsWith("glass") || id.endsWith("glass_pane")) glass.add(b);
			if (b.defaultBlockState().is(BlockTags.LEAVES)) leaves.add(b);
			if (b.defaultBlockState().is(SMALL_FLOWERS)) flowers.add(b);
		}
		ladder(c, "erts.alles", Items.RAW_GOLD, "Mine %,d ore of any kind", mined(ores), 1, r(C, U, R, E, L),
				100, "Ore Nose", 1000, "Vein Seeker", 10000, "Strip Miner", 50000, "Ore Apocalypse", 200000, "The Mountain Is Hollow");
		ladder(c, "diepsteen", Items.COBBLED_DEEPSLATE, "Mine %,d deepslate", mined("deepslate", "cobbled_deepslate"), 1, r(C, U, R, L),
				1000, "Deep Digger", 10000, "Deepslate Devourer", 100000, "Into the Abyss", 500000, "Below Bedrock Spirit");
		ladder(c, "glasbreuk", Items.GLASS, "Break a pane of glass|Break %,d glass", mined(glass), 1, r(C, U, R),
				1, "Oops", 100, "Bull in a China Shop", 1000, "Window Smasher");
		ladder(c, "bladblazer", Items.OAK_LEAVES, "Clear %,d leaves", mined(leaves), 1, r(C, U, R),
				100, "Leaf Blower", 1000, "Autumn Comes Early", 10000, "Deforestation Department");
		ladder(c, "spinrag", Items.COBWEB, "Cut %,d cobwebs", mined("cobweb"), 1, r(C, U), 10, "Web Designer", 100, "Web Cutter");
		ladder(c, "plukker", Items.DANDELION, "Pick %,d flowers", mined(flowers), 1, r(C, U), 100, "Flower Picker", 1000, "Hay Fever");
		ladder(c, "kooibreker", Ui.item("spawner"), "Break a monster spawner|Break %,d monster spawners", mined("spawner"), 1, r(U, R, E),
				1, "Spawn Killer", 10, "Dungeon Wrecker", 50, "No More Monsters");
		ladder(c, "sneeuwschep", Items.SNOW_BLOCK, "Shovel %,d snow", mined("snow", "snow_block", "powder_snow"), 1, r(C, U),
				100, "Snow Shoveler", 1000, "Snowplough");
		ladder(c, "zandman", Items.SAND, "Dig %,d sand", mined("sand", "red_sand"), 1, r(C, U, R), 1000, "Sandman", 10000, "Desert Maker",
				50000, "Sahara Engineer");
		ladder(c, "grindgraver", Items.GRAVEL, "Dig %,d gravel", mined("gravel"), 1, r(C, U), 500, "Flint Seeker", 5000, "Gravel Grinder");
		ladder(c, "klei", Items.CLAY_BALL, "Dig %,d clay", mined("clay"), 1, r(C, U), 100, "Potter", 1000, "Clay Baron");
		ladder(c, "mos", Items.MOSS_BLOCK, "Pull up %,d moss", mined("moss_block", "moss_carpet", "pale_moss_block", "pale_moss_carpet"), 1,
				r(C, U), 100, "Moss Boss", 1000, "Rolling Stone");
		ladder(c, "druipsteen", Items.POINTED_DRIPSTONE, "Break %,d dripstone", mined("pointed_dripstone", "dripstone_block"), 1, r(C, U),
				100, "Stalactite Snapper", 1000, "Cave Dentist");
		ladder(c, "bamboe", Items.BAMBOO, "Cut %,d bamboo", mined("bamboo"), 1, r(C, U), 500, "Panda's Nightmare", 5000, "Bamboozler");
		ladder(c, "pompoenen", Items.PUMPKIN, "Smash %,d pumpkins and melons", mined("pumpkin", "carved_pumpkin", "melon"), 1, r(C, U),
				100, "Smashing", 1000, "Squash Champion");
		ladder(c, "koraalrif", Ui.item("brain_coral_block"), "Break %,d coral", mined(blocksWhere(id -> id.contains("coral"))), 1, r(U),
				64, "Reef Wrecker");
		ladder(c, "steenbakker", Items.BRICKS, "Mine %,d bricks of any kind", mined(blocksWhere(id -> id.endsWith("bricks"))), 1, r(C, U),
				100, "Wall Breaker", 1000, "Demolition Crew");
		ladder(c, "gesteente", Items.TUFF, "Mine %,d tuff and calcite", mined("tuff", "calcite"), 1, r(C, U), 500, "Rock Hound",
				5000, "Geologist's Hammer");
		ladder(c, "paddenstoel", Items.RED_MUSHROOM, "Break %,d mushrooms", mined("red_mushroom", "brown_mushroom", "red_mushroom_block",
				"brown_mushroom_block", "mushroom_stem"), 1, r(C), 100, "Shroom Stomper");
		ladder(c, "beddenbreker", Ui.item("red_bed"), "Break %,d beds", mined(blocksWhere(id -> id.endsWith("_bed"))), 1, r(C, U),
				10, "Bed Breaker", 100, "Sleepless Wrecker");
	}

	private static void combat() {
		Category c = Category.GEVECHT;
		ladder(c, "koeien", Items.BEEF, "Defeat %,d cows", killed("cow", "mooshroom"), 1, r(C, U, R), 10, "Burger Time", 100, "Steakhouse",
				1000, "Beef Baron");
		ladder(c, "kippen", Items.CHICKEN, "Defeat %,d chickens", killed("chicken"), 1, r(C, U, R), 10, "Chicken Nuggets", 100, "Fried Chicken",
				1000, "Poultry Panic");
		ladder(c, "varkens", Items.PORKCHOP, "Defeat %,d pigs", killed("pig"), 1, r(C, U, R), 10, "Bacon Bits", 100, "Pork Belly",
				1000, "Hog Heaven");
		ladder(c, "schapen", Items.MUTTON, "Defeat %,d sheep", killed("sheep"), 1, r(C, U), 10, "Lamb Chops", 100, "Mutton Master");
		ladder(c, "konijnen", Items.RABBIT, "Defeat %,d rabbits", killed("rabbit"), 1, r(C, U), 10, "Rabbit Season", 100, "Hare Today");
		ladder(c, "inktvissen", Items.INK_SAC, "Defeat %,d squid", killed("squid", "glow_squid"), 1, r(C, U), 10, "Calamari", 100, "Ink Spiller");
		ladder(c, "vissen", Items.COD, "Defeat %,d fish", killed("cod", "salmon", "tropical_fish", "pufferfish"), 1, r(C, U),
				10, "Fish Slapper", 100, "Gill Griller");
		ladder(c, "vleermuis", Ui.item("black_dye"), "Defeat a bat|Defeat %,d bats", killed("bat"), 1, r(U, R), 1, "Bat Swatter", 10, "Night Wings");
		ladder(c, "golems", Items.IRON_BLOCK, "Defeat an iron golem|Defeat %,d iron golems", killed("iron_golem"), 1, r(R, E),
				1, "Golem Breaker", 10, "Scrap Metal");
		ladder(c, "duel", Items.GOLDEN_SWORD, "Defeat another player|Defeat %,d players", custom("player_kills"), 1, r(U, R, E, L),
				1, "First Duel", 10, "Duelist", 100, "Arena Champion", 500, "Feared Name");
		ladder(c, "verwoester", Items.SADDLE, "Defeat a ravager|Defeat %,d ravagers", killed("ravager"), 1, r(R, E), 1, "Ravager Wrangler",
				10, "Ravager Ruin");
		ladder(c, "vexen", Items.IRON_SWORD, "Defeat %,d vexes", killed("vex"), 1, r(U, R), 10, "Vex Swatter", 100, "Pixie Popper");
		ladder(c, "ijsbeer", Items.SNOWBALL, "Defeat a polar bear|Defeat %,d polar bears", killed("polar_bear"), 1, r(U, R),
				1, "Polar Opposite", 10, "Arctic Hunter");
		ladder(c, "ruiters", Items.BONE, "Defeat a skeleton or zombie horse|Defeat %,d skeleton or zombie horses", killed("skeleton_horse",
				"zombie_horse"), 1, r(R, E), 1, "Horseman's Bane", 10, "Stable Cleaner");
		ladder(c, "dieren", Items.LEATHER, "Defeat %,d animals", Catalog.killedCategory(MobCategory.CREATURE), 1, r(C, U, R, E),
				100, "Butcher", 1000, "Carnivore", 10000, "Food Chain Apex", 50000, "Mass Extinction");
		ladder(c, "absorptie", Items.GOLDEN_APPLE, "Absorb %,d hearts of damage", custom("damage_absorbed"), 20, r(U, R, E),
				100, "Golden Gulp", 1000, "Absorbent", 10000, "Sponge of War");
		ladder(c, "weerstand", Items.NETHERITE_CHESTPLATE, "Resist %,d hearts of damage", custom("damage_resisted"), 20, r(U, R, E),
				100, "Tough Cookie", 1000, "Unbreakable", 10000, "Immovable Object");
		ladder(c, "zombievolk", Items.ZOMBIE_HEAD, "Defeat %,d zombie villagers", killed("zombie_villager"), 1, r(C, U, R),
				10, "Bad Neighbours", 100, "Village Cleanup", 1000, "Undead Mayor");
		ladder(c, "verzwolgen", Items.TRIDENT, "Defeat %,d drowned", killed("drowned"), 1, r(C, U, R), 10, "Deep Trouble Maker", 100, "Tide Turner",
				1000, "Poseidon's Bouncer");
		ladder(c, "woestijn", Items.SAND, "Defeat %,d husks", killed("husk"), 1, r(C, U, R), 10, "Mummy Wrapper", 100, "Desert Storm",
				1000, "Pharaoh's Curse");
		ladder(c, "ijsskelet", Items.ARROW, "Defeat %,d strays", killed("stray"), 1, r(C, U), 10, "Frostbite", 100, "Cold Shoulder");
		ladder(c, "moerasskelet", Items.BROWN_MUSHROOM, "Defeat %,d bogged", killed("bogged"), 1, r(U, R), 10, "Swamp Thing", 100, "Bog Standard");
		ladder(c, "grotspin", Items.FERMENTED_SPIDER_EYE, "Defeat %,d cave spiders", killed("cave_spider"), 1, r(C, U, R), 10, "Mineshaft Menace",
				100, "Poison Proof", 1000, "Spawner Camper");
		ladder(c, "mijt", Items.ENDER_EYE, "Defeat %,d endermites", killed("endermite"), 1, r(U, R), 10, "Mite Makes Right", 100, "Pearl Pest");
		ladder(c, "magma", Items.MAGMA_CREAM, "Defeat %,d magma cubes", killed("magma_cube"), 1, r(C, U, R), 10, "Lava Lamp", 100, "Hot Stuff",
				1000, "Magma Masher");
		ladder(c, "vechtersbaas", Items.GOLDEN_AXE, "Defeat %,d piglin brutes", killed("piglin_brute"), 1, r(U, R, E), 1, "Brute Squad",
				10, "Bastion Breaker", 100, "Brute Force");
		ladder(c, "duisterbos", Ui.item("pale_oak_log"), "Defeat %,d creakings", killed("creaking"), 1, r(E), 100, "Woodchipper");
	}

	/** Dying in silly (and less silly) ways: the statistic of what killed you. */
	private static void deaths() {
		Category c = Category.TIJD;
		ladder(c, "dood.creeper", Items.CREEPER_HEAD, "Be blown up by a creeper|Be blown up by creepers %,d times", killedBy("creeper"), 1,
				r(C, U, R), 1, "Surprise!", 10, "Creeper Snack", 50, "Professional Crater");
		ladder(c, "dood.zombie", Items.ROTTEN_FLESH, "Be eaten by a zombie|Be eaten by zombies %,d times", killedBy("zombie", "husk",
				"drowned", "zombie_villager"), 1, r(C, U), 1, "Brains!", 25, "Zombie Buffet");
		ladder(c, "dood.skelet", Items.ARROW, "Be shot by a skeleton|Be shot by skeletons %,d times", killedBy("skeleton", "stray", "bogged",
				"parched"), 1, r(C, U), 1, "Pincushion", 25, "Archery Target");
		ladder(c, "dood.enderman", Items.ENDER_PEARL, "Be killed by an enderman|Be killed by endermen %,d times", killedBy("enderman"), 1,
				r(C, U), 1, "Don't Look Back", 10, "Stared Too Long");
		ladder(c, "dood.spin", Items.SPIDER_EYE, "Be killed by a spider|Be killed by spiders %,d times", killedBy("spider", "cave_spider"), 1,
				r(C, U), 1, "Itsy Bitsy", 10, "Spider Food");
		ladder(c, "dood.warden", Items.SCULK_SENSOR, "Be killed by a warden", killedBy("warden"), 1, r(R), 1, "Should Have Whispered");
		ladder(c, "dood.draak", Items.DRAGON_BREATH, "Be killed by the Ender Dragon", killedBy("ender_dragon"), 1, r(U), 1, "Dragon Chow");
		ladder(c, "dood.wither", Items.WITHER_ROSE, "Be killed by the Wither", killedBy("wither", "wither_skull"), 1, r(U), 1, "Withered Away");
		ladder(c, "dood.blaze", Items.BLAZE_POWDER, "Be killed by a blaze", killedBy("blaze"), 1, r(C), 1, "Toasted");
		ladder(c, "dood.ghast", Items.FIRE_CHARGE, "Be killed by a ghast", killedBy("ghast"), 1, r(C), 1, "Return to Sender");
		ladder(c, "dood.heks", Items.SPLASH_POTION, "Be killed by a witch", killedBy("witch"), 1, r(C), 1, "Bad Potion");
		ladder(c, "dood.slijm", Items.SLIME_BALL, "Be killed by a slime or magma cube", killedBy("slime", "magma_cube"), 1, r(C), 1, "Sticky End");
		ladder(c, "dood.phantom", Items.PHANTOM_MEMBRANE, "Be killed by a phantom", killedBy("phantom"), 1, r(C), 1, "Should Have Slept");
		ladder(c, "dood.bij", Items.HONEYCOMB, "Be stung to death by a bee", killedBy("bee"), 1, r(U), 1, "Bee-trayed");
		ladder(c, "dood.wolf", Items.BONE, "Be killed by a wolf", killedBy("wolf"), 1, r(U), 1, "Bad Dog");
		ladder(c, "dood.golem", Items.POPPY, "Be killed by an iron golem", killedBy("iron_golem"), 1, r(U), 1, "Golem Justice");
		ladder(c, "dood.lama", Items.LEAD, "Be killed by a llama", killedBy("llama", "trader_llama", "llama_spit"), 1, r(R), 1, "Spat On");
		ladder(c, "dood.kogelvis", Items.PUFFERFISH, "Be killed by a pufferfish", killedBy("pufferfish"), 1, r(R), 1, "Spiky Surprise");
		ladder(c, "dood.bruut", Items.GOLDEN_AXE, "Be killed by a piglin brute", killedBy("piglin_brute"), 1, r(C), 1, "Out-Bruted");
		ladder(c, "dood.vex", Items.IRON_SWORD, "Be killed by a vex", killedBy("vex"), 1, r(C), 1, "Vexed");
		ladder(c, "dood.evoker", Items.TOTEM_OF_UNDYING, "Be killed by an evoker", killedBy("evoker", "evoker_fangs"), 1, r(C), 1, "Fanged");
		ladder(c, "dood.zilvervis", Items.STONE_BRICKS, "Be killed by a silverfish", killedBy("silverfish"), 1, r(U), 1, "Bugged Out");
		ladder(c, "dood.speler", Items.GOLDEN_SWORD, "Be killed by another player|Be killed by other players %,d times", killedBy("player"), 1,
				r(C, U), 1, "Backstabbed", 10, "Target Practice");
		ladder(c, "dood.wind", Items.WIND_CHARGE, "Be killed by a breeze", killedBy("breeze", "breeze_wind_charge"), 1, r(C), 1, "Blown Away");
		ladder(c, "dood.creaking", Ui.item("resin_clump"), "Be killed by a creaking", killedBy("creaking"), 1, r(U), 1, "Creaky Floorboard");
		ladder(c, "dood.verwoester", Items.SADDLE, "Be trampled by a ravager", killedBy("ravager"), 1, r(C), 1, "Trampled");
		ladder(c, "dood.hoglin", Items.PORKCHOP, "Be gored by a hoglin", killedBy("hoglin", "zoglin"), 1, r(C), 1, "Gored");
		ladder(c, "dood.ijsbeer", Items.COD, "Be hugged too hard by a polar bear", killedBy("polar_bear"), 1, r(U), 1, "Bear Hug");
		ladder(c, "dood.guardian", Items.PRISMARINE_CRYSTALS, "Be killed by a guardian", killedBy("guardian", "elder_guardian"), 1, r(C),
				1, "Laser Tag");
		ladder(c, "dood.shulker", Items.SHULKER_SHELL, "Be killed by a shulker", killedBy("shulker", "shulker_bullet"), 1, r(U), 1, "Homing In");
	}

	private static void farming() {
		Category c = Category.FOKKEN;
		ladder(c, "eieren", Items.EGG, "Gather %,d eggs", gathered(itemsOf("egg", "blue_egg", "brown_egg")), 1, r(C, U, R), 100, "Egg Collector",
				1000, "Egg Tycoon", 10000, "Henhouse Overlord");
		ladder(c, "wolverzamel", Ui.item("white_wool"), "Gather %,d wool", gathered(itemsWhere(id -> id.endsWith("_wool"))), 1, r(C, U, R),
				64, "Woolgatherer", 640, "Wool Hoarder", 6400, "Sheep's Worst Nightmare");
		ladder(c, "veren", Items.FEATHER, "Gather %,d feathers", gathered(itemsOf("feather")), 1, r(C, U), 100, "Feather Duster", 1000, "Pillow Maker");
		ladder(c, "leer", Items.LEATHER, "Gather %,d leather", gathered(itemsOf("leather")), 1, r(C, U), 100, "Tanner", 1000, "Leather Lord");
		ladder(c, "honing", Items.HONEYCOMB, "Gather %,d honeycomb", gathered(itemsOf("honeycomb")), 1, r(U, R), 64, "Beekeeper", 640, "Queen Bee");
		ladder(c, "botten", Items.BONE, "Gather %,d bones", gathered(itemsOf("bone")), 1, r(C, U), 640, "Bone Pile", 6400, "Catacomb Keeper");
		ladder(c, "touw", Items.STRING, "Gather %,d string", gathered(itemsOf("string")), 1, r(C, U), 1000, "String Theory", 10000, "Cat's Cradle");
		ladder(c, "buskruit", Items.GUNPOWDER, "Gather %,d gunpowder", gathered(itemsOf("gunpowder")), 1, r(C, U, R), 640, "Powder Keg",
				6400, "Gunpowder Plot", 64000, "Blast Radius");
		ladder(c, "parels", Items.ENDER_PEARL, "Gather %,d ender pearls", gathered(itemsOf("ender_pearl")), 1, r(U, R), 100, "Pearl Diver",
				1000, "Pearly Gates");
		ladder(c, "staven", Items.BLAZE_ROD, "Gather %,d blaze rods", gathered(itemsOf("blaze_rod")), 1, r(U, R), 100, "Rod Collector",
				1000, "Fire Keeper");
		ladder(c, "slijmbal", Items.SLIME_BALL, "Gather %,d slime balls", gathered(itemsOf("slime_ball")), 1, r(C, U), 100, "Slimy Hands",
				1000, "Goo Guru");
		ladder(c, "smaragden", Items.EMERALD, "Gather %,d emeralds", gathered(itemsOf("emerald")), 1, r(U, R, E), 100, "Green Pockets",
				1000, "Emerald City", 10000, "Villager's Banker");
		ladder(c, "diamanten", Items.DIAMOND, "Gather %,d diamonds", gathered(itemsOf("diamond")), 1, r(R, E, L, M), 100, "Shiny!",
				1000, "Diamond Hands", 5000, "Diamond Dragon", 20000, "Carbon Emperor");
		ladder(c, "schroot", Items.NETHERITE_SCRAP, "Gather %,d netherite scrap", gathered(itemsOf("netherite_scrap")), 1, r(R, E),
				64, "Scrap Dealer", 640, "Scrapyard King");
		ladder(c, "klompjes", Items.GOLD_NUGGET, "Gather %,d gold nuggets", gathered(itemsOf("gold_nugget")), 1, r(C, U), 1000, "Nugget Hunter",
				10000, "Prospector's Luck");
		ladder(c, "ruwijzer", Items.RAW_IRON, "Gather %,d raw iron", gathered(itemsOf("raw_iron")), 1, r(C, U, R), 1000, "Iron Grip",
				10000, "Ironclad", 50000, "Iron Mountain");
		ladder(c, "steenkool", Items.COAL, "Gather %,d coal", gathered(itemsOf("coal")), 1, r(C, U), 1000, "Coal Collector", 10000, "Soot Lord");
		ladder(c, "stof", Items.REDSTONE, "Gather %,d redstone", gathered(itemsOf("redstone")), 1, r(C, U), 1000, "Dust Bunny", 10000, "Red Tide");
		ladder(c, "lazuur", Items.LAPIS_LAZULI, "Gather %,d lapis lazuli", gathered(itemsOf("lapis_lazuli")), 1, r(U, R), 1000, "Lapis Lover",
				10000, "Ultramarine");
		ladder(c, "appels", Items.APPLE, "Gather %,d apples", gathered(itemsOf("apple")), 1, r(C, U), 64, "Apple Picker", 640, "Orchard Keeper");
		ladder(c, "bessen", Items.SWEET_BERRIES, "Gather %,d berries", gathered(itemsOf("sweet_berries", "glow_berries")), 1, r(C, U),
				640, "Berry Picker", 6400, "Berry Baron");
		ladder(c, "kelp", Items.KELP, "Gather %,d kelp", gathered(itemsOf("kelp")), 1, r(C, U), 640, "Kelp Farmer", 6400, "Seaweed Shepherd");
		ladder(c, "bamboeoogst", Items.BAMBOO, "Gather %,d bamboo", gathered(itemsOf("bamboo")), 1, r(C, U), 1000, "Bamboo Baron",
				10000, "Scaffold Supplier");
		ladder(c, "cactus", Items.CACTUS, "Gather %,d cactus", gathered(itemsOf("cactus")), 1, r(C, U), 640, "Prickly", 6400, "Cactus Jack");
		ladder(c, "riet", Items.SUGAR_CANE, "Gather %,d sugar cane", gathered(itemsOf("sugar_cane")), 1, r(C, U, R), 1000, "Sugar Rush",
				10000, "Cane Empire", 50000, "Diabetic Empire");
		ladder(c, "wratten", Items.NETHER_WART, "Gather %,d nether wart", gathered(itemsOf("nether_wart")), 1, r(C, U), 1000, "Wart Farmer",
				10000, "Brewer's Best Friend");
		ladder(c, "aardappels", Items.POTATO, "Gather %,d potatoes", gathered(itemsOf("potato")), 1, r(C, U, R), 1000, "Couch Potato",
				10000, "Potato Famine Prevention", 50000, "Spud King");
		ladder(c, "wortels", Items.CARROT, "Gather %,d carrots", gathered(itemsOf("carrot")), 1, r(C, U), 1000, "Carrot Top", 10000, "Rabbit's Dream");
		ladder(c, "bieten", Items.BEETROOT, "Gather %,d beetroot", gathered(itemsOf("beetroot")), 1, r(C, U), 640, "Beet It", 6400, "Beetroot Baron");
		ladder(c, "tarwe", Items.WHEAT, "Gather %,d wheat", gathered(itemsOf("wheat")), 1, r(C, U, R), 1000, "Amber Waves", 10000, "Breadwinner",
				50000, "Grain Empire");
		ladder(c, "meloen", Items.MELON_SLICE, "Gather %,d melon slices", gathered(itemsOf("melon_slice")), 1, r(C, U), 1000, "Melon Collie",
				10000, "Melonaire");
		ladder(c, "pompoenoogst", Items.PUMPKIN, "Gather %,d pumpkins", gathered(itemsOf("pumpkin")), 1, r(C, U), 640, "Pumpkin Hoarder",
				6400, "Harvest Moon Keeper");
		ladder(c, "cacao", Items.COCOA_BEANS, "Gather %,d cocoa beans", gathered(itemsOf("cocoa_beans")), 1, r(C, U), 640, "Cocoa Cultivator",
				6400, "Chocolatier");
		ladder(c, "boeket", Items.POPPY, "Gather %,d flowers", gathered(itemsWhere(id -> Catalog.is(id, Catalog.tag("flowers")))), 1,
				r(C, U), 100, "Bouquet", 1000, "Flower Shop");
	}

	private static void exploring() {
		Category c = Category.ONTDEKKEN;
		long km = 100_000, m = 100;
		ladder(c, "ren", Items.SUGAR, "Sprint %,d km", custom("sprint_one_cm"), km, r(C, U, R, E), 10, "Sprinter", 100, "Speed Demon",
				1000, "Lightning Legs", 5000, "Faster Than Light");
		ladder(c, "opwater", Items.LILY_PAD, "Walk %,d km on water", custom("walk_on_water_one_cm"), km, r(U, R), 1, "Water Walker", 10, "Messiah Mode");
		ladder(c, "onderwater", Items.PRISMARINE, "Walk %,d km under water", custom("walk_under_water_one_cm"), km, r(U, R), 1, "Bottom Feeder",
				10, "Aquanaut");
		ladder(c, "nautilus", Items.NAUTILUS_SHELL, "Ride a nautilus %,d m", custom("nautilus_one_cm"), m, r(U, R), 100, "Shell Rider",
				1000, "Deep Sea Jockey");
		ladder(c, "teller", Items.COMPASS, "Travel %,d km in every way", custom("walk_one_cm", "sprint_one_cm", "crouch_one_cm", "swim_one_cm",
				"boat_one_cm", "horse_one_cm", "aviate_one_cm", "minecart_one_cm", "pig_one_cm", "strider_one_cm", "climb_one_cm",
				"walk_on_water_one_cm", "walk_under_water_one_cm", "happy_ghast_one_cm", "nautilus_one_cm"), km, r(U, R, E, L), 100, "Odometer",
				1000, "Road Warrior", 10000, "Frequent Traveller", 50000, "Interdimensional Commuter");
	}

	private static void making() {
		Category c = Category.MAKEN;
		ladder(c, "werkbanken", Items.CRAFTING_TABLE, "Craft %,d crafting tables", crafted(itemsOf("crafting_table")), 1, r(C, U),
				10, "Table Setter", 64, "Why So Many Tables?");
		ladder(c, "stokjes", Items.STICK, "Craft %,d sticks", crafted(itemsOf("stick")), 1, r(C, U), 1000, "Stick Figure", 10000, "Stick Empire");
		ladder(c, "fakkels", Items.TORCH, "Craft %,d torches", crafted(itemsOf("torch", "soul_torch", "copper_torch")), 1, r(C, U, R),
				64, "Torch Maker", 640, "Torch Factory", 6400, "Second Sun");
		ladder(c, "brood", Items.BREAD, "Bake %,d bread", crafted(itemsOf("bread")), 1, r(C, U, R), 64, "Baker", 640, "Master Baker",
				6400, "Bread Overlord");
		ladder(c, "taarten", Items.CAKE, "Bake a cake|Bake %,d cakes", crafted(itemsOf("cake")), 1, r(C, U), 1, "Patissier", 64, "Cake Architect");
		ladder(c, "koekjes", Items.COOKIE, "Bake %,d cookies", crafted(itemsOf("cookie")), 1, r(C, U), 64, "Cookie Cutter", 640, "Cookie Crumbler");
		ladder(c, "pastei", Items.PUMPKIN_PIE, "Bake %,d pumpkin pies", crafted(itemsOf("pumpkin_pie")), 1, r(C, U), 16, "Pie Maker",
				160, "Pie in the Sky");
		ladder(c, "gouden", Items.GOLDEN_APPLE, "Make %,d golden apples", crafted(itemsOf("golden_apple")), 1, r(U, R), 10, "Golden Touch",
				100, "Orchard of Gold");
		ladder(c, "goudwortel", Items.GOLDEN_CARROT, "Make %,d golden carrots", crafted(itemsOf("golden_carrot")), 1, r(U, R), 64, "Carrot Gilder",
				640, "Night Vision Supplier");
		ladder(c, "baken.maak", Items.BEACON, "Make a beacon|Make %,d beacons", crafted(itemsOf("beacon")), 1, r(E, L), 1, "Lighthouse Keeper",
				10, "Pyramid Scheme");
		ladder(c, "netheriet", Items.NETHERITE_INGOT, "Make a netherite ingot|Make %,d netherite ingots", crafted(itemsOf("netherite_ingot")), 1,
				r(R, E, L), 1, "Netherite Forger", 10, "Netherite Smith", 64, "Netherite Mogul");
		ladder(c, "diamantsmid", Items.DIAMOND_PICKAXE, "Make %,d diamond tools and armor", crafted(itemsWhere(id -> id.startsWith("diamond_")
				&& Catalog.has(id, net.minecraft.core.component.DataComponents.MAX_DAMAGE))), 1, r(U, R), 10, "Diamond Smith", 100, "Diamond Wardrobe");
		ladder(c, "tnt.maak", Items.TNT, "Make %,d TNT", crafted(itemsOf("tnt")), 1, r(U, R, E), 10, "Demolitionist", 100, "Mad Bomber",
				1000, "Weapon of Mass Excavation");
		ladder(c, "vuurwerk", Items.FIREWORK_ROCKET, "Make %,d firework rockets", crafted(itemsOf("firework_rocket")), 1, r(U, R, E),
				64, "Pyrotechnician", 640, "New Year's Eve", 6400, "Sky Painter");
		ladder(c, "pijlen", Items.ARROW, "Make %,d arrows", crafted(itemsOf("arrow", "spectral_arrow", "tipped_arrow")), 1, r(C, U),
				640, "Fletcher", 6400, "Arrow Storm Supplier");
		ladder(c, "papier", Items.PAPER, "Make %,d paper", crafted(itemsOf("paper")), 1, r(C, U), 640, "Paper Pusher", 6400, "Bureaucrat");
		ladder(c, "boeken", Items.BOOK, "Make %,d books", crafted(itemsOf("book")), 1, r(U, R), 100, "Author", 1000, "Bestseller");
		ladder(c, "boekenkast", Items.BOOKSHELF, "Make %,d bookshelves", crafted(itemsOf("bookshelf", "chiseled_bookshelf")), 1, r(U, R),
				15, "Enchanting Corner", 100, "Grand Library");
		ladder(c, "bedden.maak", Ui.item("red_bed"), "Make %,d beds", crafted(itemsWhere(id -> id.endsWith("_bed"))), 1, r(C, U), 16, "Bed Salesman",
				100, "Furniture Store");
		ladder(c, "kisten.maak", Items.CHEST, "Make %,d chests and barrels", crafted(itemsOf("chest", "barrel", "trapped_chest")), 1, r(C, U, R),
				20, "Storage Problem", 100, "Organised Chaos", 1000, "Warehouse");
		ladder(c, "trechters", Items.HOPPER, "Make %,d hoppers", crafted(itemsOf("hopper")), 1, r(U, R), 64, "Hopper Hoarder", 640, "Item Sorter");
		ladder(c, "zuigers", Items.PISTON, "Make %,d pistons", crafted(itemsOf("piston", "sticky_piston")), 1, r(U, R), 64, "Piston Pusher",
				640, "Flying Machine Pilot");
		ladder(c, "waarnemers", Items.OBSERVER, "Make %,d observers", crafted(itemsOf("observer")), 1, r(U), 64, "Watcher");
		ladder(c, "lijsten", Items.ITEM_FRAME, "Make %,d item frames", crafted(itemsOf("item_frame", "glow_item_frame")), 1, r(C, U), 64, "Framed",
				640, "Gallery Owner");
		ladder(c, "standaard", Items.ARMOR_STAND, "Make %,d armor stands", crafted(itemsOf("armor_stand")), 1, r(C), 10, "Mannequin Maker");
		ladder(c, "lantaarns", Items.LANTERN, "Make %,d lanterns", crafted(itemsWhere(id -> id.endsWith("lantern"))), 1, r(C, U), 64, "Lamplighter",
				640, "Light Pollution");
		ladder(c, "glasblazer", Items.GLASS, "Make %,d glass", crafted(itemsOf("glass")), 1, r(C, U, R), 1000, "Glassblower", 10000, "Crystal Palace",
				50000, "Glass Cathedral");
		ladder(c, "ijzerwerk", Items.IRON_INGOT, "Make %,d iron ingots", crafted(itemsOf("iron_ingot")), 1, r(U, R, E), 640, "Ironworks",
				6400, "Steel Mill", 64000, "Industrial Revolution");
		ladder(c, "goudsmid", Items.GOLD_INGOT, "Make %,d gold ingots", crafted(itemsOf("gold_ingot")), 1, r(U, R), 640, "Gold Refinery",
				6400, "Fort Knox");
		ladder(c, "kopergieter", Items.COPPER_INGOT, "Make %,d copper ingots", crafted(itemsOf("copper_ingot")), 1, r(U, R), 640, "Copper Foundry",
				6400, "Wire Tycoon");
		ladder(c, "mortel", Ui.item("white_concrete_powder"), "Make %,d concrete powder", crafted(itemsWhere(id -> id.endsWith("_concrete_powder"))), 1,
				r(U, R), 640, "Mortar Mixer", 6400, "Concrete Empire");
		ladder(c, "trappen", Items.OAK_STAIRS, "Make %,d stairs", crafted(itemsWhere(id -> id.endsWith("_stairs"))), 1, r(C, U), 640, "Step by Step",
				6400, "Stairway Builder");
		ladder(c, "platen.maak", Items.OAK_SLAB, "Make %,d slabs", crafted(itemsWhere(id -> id.endsWith("_slab"))), 1, r(C, U), 640, "Half Measures",
				6400, "Halfway There");
		ladder(c, "vaandels", Ui.item("white_banner"), "Make %,d banners", crafted(itemsWhere(id -> id.endsWith("_banner"))), 1, r(C, U), 10, "Flag Bearer",
				100, "Vexillologist");
		ladder(c, "kompas", Items.COMPASS, "Make %,d compasses", crafted(itemsOf("compass", "recovery_compass")), 1, r(C), 10, "Never Lost");
		ladder(c, "klokken", Items.CLOCK, "Make %,d clocks", crafted(itemsOf("clock")), 1, r(C), 10, "Clockmaker");
		ladder(c, "boten.maak", Items.OAK_BOAT, "Make %,d boats", crafted(itemsWhere(id -> id.endsWith("_boat") || id.endsWith("_raft"))), 1,
				r(C, U), 10, "Boat Builder", 100, "Shipyard");
		ladder(c, "rails.maak", Items.RAIL, "Make %,d rails", crafted(itemsOf("rail", "powered_rail", "detector_rail", "activator_rail")), 1,
				r(U, R), 640, "Rail Baron", 6400, "Transcontinental");
		ladder(c, "verf", Ui.item("red_dye"), "Make %,d dyes", crafted(itemsWhere(id -> id.endsWith("_dye"))), 1, r(C, U), 640, "Colour Me Impressed",
				6400, "Rainbow Factory");
		ladder(c, "kaarsen.maak", Ui.item("candle"), "Make %,d candles", crafted(itemsWhere(id -> id.equals("candle") || id.endsWith("_candle"))), 1,
				r(C), 64, "Candle Maker");
		ladder(c, "dozen", Items.SHULKER_BOX, "Make %,d shulker boxes", crafted(itemsWhere(id -> id.endsWith("shulker_box"))), 1, r(R, E),
				10, "Box Maker", 100, "Infinite Storage");
		ladder(c, "kristal", Items.END_CRYSTAL, "Make %,d end crystals", crafted(itemsOf("end_crystal")), 1, r(E), 4, "Dragon Revival Kit");
		ladder(c, "anker", Items.RESPAWN_ANCHOR, "Make a respawn anchor", crafted(itemsOf("respawn_anchor")), 1, r(R), 1, "Anchors Aweigh");
		ladder(c, "zeilsteen", Items.LODESTONE, "Make a lodestone", crafted(itemsOf("lodestone")), 1, r(R), 1, "True North");
		ladder(c, "conduit", Items.CONDUIT, "Make a conduit", crafted(itemsOf("conduit")), 1, r(E), 1, "Ocean's Heart");
		ladder(c, "sjabloon", Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, "Copy %,d smithing templates", crafted(itemsWhere(id ->
				id.endsWith("_smithing_template"))), 1, r(R, E), 10, "Template Copier", 64, "Forge Archivist");
		ladder(c, "tovertafel", Items.ENCHANTING_TABLE, "Make an enchanting table", crafted(itemsOf("enchanting_table")), 1, r(U), 1, "Magic Desk");
		ladder(c, "jukebox", Items.JUKEBOX, "Make a jukebox", crafted(itemsOf("jukebox")), 1, r(U), 1, "Party Starter");
		ladder(c, "kaartenmaker", Items.MAP, "Make %,d maps", crafted(itemsOf("map")), 1, r(U, R), 10, "Atlas Maker", 100, "Map Room");
		ladder(c, "soep", Items.MUSHROOM_STEW, "Cook %,d stews and soups", crafted(itemsOf("mushroom_stew", "rabbit_stew", "beetroot_soup",
				"suspicious_stew")), 1, r(C, U), 64, "Soup Kitchen", 640, "Souper Star");
		ladder(c, "steak", Items.COOKED_BEEF, "Cook %,d meat", crafted(itemsOf("cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton",
				"cooked_rabbit", "cooked_cod", "cooked_salmon")), 1, r(C, U, R), 100, "Grill Master", 1000, "Barbecue King", 10000, "Smoke Signal");
	}

	private static void placing() {
		Category c = Category.MAKEN;
		ladder(c, "aarde.leg", Items.DIRT, "Place %,d dirt", used(itemsOf("dirt", "coarse_dirt", "grass_block")), 1, r(C, U), 1000,
				"Dirt House Architect", 10000, "Mud Hut Mogul");
		ladder(c, "kei.leg", Items.COBBLESTONE, "Place %,d cobblestone", used(itemsOf("cobblestone", "cobbled_deepslate")), 1, r(C, U, R),
				1000, "Cobble Wall", 10000, "Cobble Monster", 100000, "Grey Everything");
		ladder(c, "glas.leg", Items.GLASS_PANE, "Place %,d glass", used(itemsWhere(id -> id.endsWith("glass") || id.endsWith("glass_pane"))), 1,
				r(U, R), 1000, "Glazier", 10000, "Glass Tower");
		ladder(c, "wol.leg", Ui.item("pink_wool"), "Place %,d wool", used(itemsWhere(id -> id.endsWith("_wool"))), 1, r(U, R), 1000, "Fluffy Builder",
				10000, "Pixel Artist");
		ladder(c, "beton.leg", Ui.item("gray_concrete"), "Place %,d concrete", used(itemsWhere(id -> id.endsWith("_concrete"))), 1, r(U, R, E),
				1000, "Modernist", 5000, "Brutalist", 50000, "City Planner");
		ladder(c, "tnt.leg", Items.TNT, "Place %,d TNT", used(itemsOf("tnt")), 1, r(U, R, E), 10, "Kaboom", 100, "Crater Maker",
				1000, "Terraformer by Force");
		ladder(c, "borden.leg", Items.OAK_SIGN, "Place %,d signs", used(itemsWhere(id -> id.endsWith("_sign"))), 1, r(C, U), 100, "Sign Painter",
				1000, "Road Signs Department");
		ladder(c, "bloemen.leg", Items.POPPY, "Plant %,d flowers", used(itemsWhere(id -> Catalog.is(id, Catalog.tag("flowers")))), 1,
				r(C, U), 100, "Flower Planter", 1000, "Botanical Garden");
		ladder(c, "deuren.leg", Items.OAK_DOOR, "Place %,d doors", used(itemsWhere(id -> id.endsWith("_door"))), 1, r(C, U), 64, "Doorman",
				640, "Too Many Doors");
		ladder(c, "lantaarn.leg", Items.LANTERN, "Place %,d lanterns", used(itemsWhere(id -> id.endsWith("lantern"))), 1, r(C, U), 100,
				"Lantern Festival Host", 1000, "City of Lights");
		ladder(c, "hout.leg", Items.OAK_PLANKS, "Place %,d planks", used(itemsWhere(id -> id.endsWith("_planks"))), 1, r(C, U, R), 1000,
				"Carpenter's Apprentice", 10000, "Timber Framer", 100000, "Wooden Wonder");
		ladder(c, "lava.leg", Items.LAVA_BUCKET, "Pour %,d buckets of lava", used(itemsOf("lava_bucket")), 1, r(U, R), 10, "The Floor Is Lava",
				100, "Volcano Maker");
		ladder(c, "water.leg", Items.WATER_BUCKET, "Pour %,d buckets of water", used(itemsOf("water_bucket")), 1, r(C, U), 100, "Plumber",
				1000, "Hydro Engineer");
		ladder(c, "aansteker", Items.FLINT_AND_STEEL, "Light %,d fires", used(itemsOf("flint_and_steel", "fire_charge")), 1, r(C, U, R),
				10, "Fire Starter", 100, "Pyromaniac", 1000, "Firebug");
	}

	private static void life() {
		Category c = Category.TIJD;
		ladder(c, "vertrek", Items.OAK_DOOR, "Leave the game %,d times", custom("leave_game"), 1, r(C, U, R), 10, "See You Later",
				100, "Rage Quitter", 1000, "Commitment Issues");
		ladder(c, "laten.vallen", Items.DIRT, "Drop %,d items", custom("drop"), 1, r(C, U, R), 100, "Butterfingers", 1000, "Litterbug",
				10000, "Landfill");
		ladder(c, "sluiptijd", Items.LEATHER_HELMET, "Sneak for %,d hour|Sneak for %,d hours", custom("sneak_time"), 72000, r(U, R),
				1, "Shady Business", 10, "Professional Lurker");
		// What you eat
		ladder(c, "rot", Items.ROTTEN_FLESH, "Eat %,d rotten flesh", used(itemsOf("rotten_flesh")), 1, r(C, U, R), 10, "Desperate Times",
				100, "Zombie Diet", 1000, "Iron Stomach");
		ladder(c, "spinoog", Items.SPIDER_EYE, "Eat %,d spider eyes", used(itemsOf("spider_eye")), 1, r(U, R), 10, "Crunchy", 100, "Eight Legs Good");
		ladder(c, "kogelvis", Items.PUFFERFISH, "Eat a pufferfish|Eat %,d pufferfish", used(itemsOf("pufferfish")), 1, r(U, R), 1,
				"Living Dangerously", 10, "Poison Taster");
		ladder(c, "goudappel", Items.GOLDEN_APPLE, "Eat %,d golden apples", used(itemsOf("golden_apple")), 1, r(U, R), 10, "Golden Glutton",
				100, "Midas Muncher");
		ladder(c, "notch", Items.ENCHANTED_GOLDEN_APPLE, "Eat an enchanted golden apple|Eat %,d enchanted golden apples",
				used(itemsOf("enchanted_golden_apple")), 1, r(E, L), 1, "God Apple Taster", 10, "Divine Diet");
		ladder(c, "stoof", Items.SUSPICIOUS_STEW, "Eat %,d suspicious stews", used(itemsOf("suspicious_stew")), 1, r(C, U), 5, "Mystery Meal",
				50, "Stew Roulette");
		ladder(c, "giftig", Items.POISONOUS_POTATO, "Eat a poisonous potato|Eat %,d poisonous potatoes", used(itemsOf("poisonous_potato")), 1,
				r(C, U), 1, "Bad Idea", 64, "Potato Gambler");
		ladder(c, "chorus", Items.CHORUS_FRUIT, "Eat %,d chorus fruit", used(itemsOf("chorus_fruit")), 1, r(U, R), 64, "Chorus Line",
				640, "Here, There, Everywhere");
		ladder(c, "koekjesmonster", Items.COOKIE, "Eat %,d cookies", used(itemsOf("cookie")), 1, r(C, U), 100, "Cookie Muncher",
				1000, "Crumbs Everywhere");
		ladder(c, "taart.eet", Items.PUMPKIN_PIE, "Eat %,d pumpkin pies", used(itemsOf("pumpkin_pie")), 1, r(C, U), 64, "Pie Eyed", 640, "Pie Fiend");
		ladder(c, "honingfles", Items.HONEY_BOTTLE, "Drink %,d honey bottles", used(itemsOf("honey_bottle")), 1, r(C, U), 64, "Honey Badger",
				640, "Sticky Situation");
		ladder(c, "melk", Items.MILK_BUCKET, "Drink %,d buckets of milk", used(itemsOf("milk_bucket")), 1, r(C, U), 10, "Milk Mustache",
				100, "Lactose Tolerant");
		ladder(c, "kelp.eet", Items.DRIED_KELP, "Eat %,d dried kelp", used(itemsOf("dried_kelp")), 1, r(C, U), 100, "Sea Snacker", 1000,
				"Kelp Muncher");
		ladder(c, "goudwortel.eet", Items.GOLDEN_CARROT, "Eat %,d golden carrots", used(itemsOf("golden_carrot")), 1, r(U, R), 100,
				"Carrot Connoisseur", 1000, "Golden Gourmet");
		ladder(c, "biefstuk", Items.COOKED_BEEF, "Eat %,d steaks", used(itemsOf("cooked_beef")), 1, r(C, U, R), 100, "Steak Night",
				1000, "Meat Lover", 10000, "Walking Barbecue");
		ladder(c, "brood.eet", Items.BREAD, "Eat %,d bread", used(itemsOf("bread")), 1, r(C, U), 100, "Bread Lover", 1000, "Carb Loader");
		ladder(c, "soep.eet", Items.BEETROOT_SOUP, "Eat %,d stews and soups", used(itemsOf("mushroom_stew", "rabbit_stew", "beetroot_soup")), 1,
				r(C, U), 10, "Soup's On", 100, "Stew Master");
		// What you drink, throw and use
		ladder(c, "drankjes", Items.POTION, "Drink %,d potions", used(itemsOf("potion")), 1, r(C, U, R), 10, "Potion Sipper", 100, "Potion Addict",
				1000, "Walking Apothecary");
		ladder(c, "totem", Items.TOTEM_OF_UNDYING, "Cheat death with a totem|Cheat death with a totem %,d times", used(itemsOf("totem_of_undying")),
				1, r(E, L, M), 1, "Cheated Death", 10, "Death Dodger", 50, "Death Can Wait");
		ladder(c, "parelwerp", Items.ENDER_PEARL, "Throw %,d ender pearls", used(itemsOf("ender_pearl")), 1, r(C, U, R), 10, "Blink",
				100, "Teleporter", 1000, "Quantum Leaper");
		ladder(c, "sneeuwbal", Items.SNOWBALL, "Throw %,d snowballs", used(itemsOf("snowball")), 1, r(C, U), 64, "Snowball Thrower", 640, "Blizzard");
		ladder(c, "eiworp", Items.EGG, "Throw %,d eggs", used(itemsOf("egg", "blue_egg", "brown_egg")), 1, r(C, U), 64, "Egg Thrower",
				640, "Chicken Launcher");
		ladder(c, "raket", Items.FIREWORK_ROCKET, "Launch %,d firework rockets", used(itemsOf("firework_rocket")), 1, r(U, R, E), 64, "Rocket Man",
				1000, "Fireworks Factory", 10000, "Booster Junkie");
		ladder(c, "naamgever", Items.NAME_TAG, "Use a name tag|Use %,d name tags", used(itemsOf("name_tag")), 1, r(C, U), 1, "Name Giver",
				10, "Godparent");
		ladder(c, "lijn", Items.LEAD, "Use a lead %,d times", used(itemsOf("lead")), 1, r(C, U), 10, "Dog Walker", 100, "Leash Law");
		ladder(c, "kijker", Items.SPYGLASS, "Look through a spyglass %,d times", used(itemsOf("spyglass")), 1, r(C, U), 100, "Look-out",
				1000, "Birdwatcher");
		ladder(c, "hoorn", Items.GOAT_HORN, "Blow a goat horn %,d times", used(itemsOf("goat_horn")), 1, r(U), 10, "Horn Blower");
		ladder(c, "kwast", Items.BRUSH, "Brush %,d times", used(itemsOf("brush")), 1, r(U, R), 100, "Dust Buster", 1000, "Professor of Dust");
		ladder(c, "boog", Items.BOW, "Shoot a bow %,d times", used(itemsOf("bow")), 1, r(C, U, R), 100, "Archer", 1000, "Robin Hood",
				10000, "Arrow Storm");
		ladder(c, "kruisboog", Items.CROSSBOW, "Shoot a crossbow %,d times", used(itemsOf("crossbow")), 1, r(U, R), 100, "Bolt Thrower",
				1000, "Crossbow Commando");
		ladder(c, "drietand", Items.TRIDENT, "Use a trident %,d times", used(itemsOf("trident")), 1, r(U, R), 100, "Poseidon's Pupil",
				1000, "Sea God");
		ladder(c, "knots", Items.MACE, "Swing a mace %,d times", used(itemsOf("mace")), 1, r(U, R), 100, "Bonk", 1000, "Smash Hit");
		ladder(c, "zwaard", Items.IRON_SWORD, "Swing a sword %,d times", used(itemsWhere(id -> id.endsWith("_sword"))), 1, r(C, U, R),
				1000, "Sword Swinger", 10000, "Blademaster", 100000, "Whirlwind");
		ladder(c, "schoffel", Items.IRON_HOE, "Use a hoe %,d times", used(itemsWhere(id -> id.endsWith("_hoe"))), 1, r(C, U), 100, "Hoe Down",
				1000, "Hoe-ly Moly");
		ladder(c, "schep", Items.IRON_SHOVEL, "Use a shovel %,d times", used(itemsWhere(id -> id.endsWith("_shovel"))), 1, r(C, U), 1000, "Spade Work",
				10000, "Human Excavator");
		ladder(c, "bijl", Items.IRON_AXE, "Use an axe %,d times", used(itemsWhere(id -> id.endsWith("_axe"))), 1, r(C, U), 1000, "Axe Grinder",
				10000, "Paul Bunyan");
		// Small things around the house
		ladder(c, "wassen", Items.LEATHER_CHESTPLATE, "Wash leather armor %,d times", custom("clean_armor"), 1, r(C), 10, "Laundry Day");
		ladder(c, "vaandelwas", Ui.item("white_banner"), "Wash a banner", custom("clean_banner"), 1, r(C), 1, "Banner Wash");
		ladder(c, "doosbleek", Items.SHULKER_BOX, "Wash a shulker box", custom("clean_shulker_box"), 1, r(U), 1, "Bleach");
		ladder(c, "ketel", Items.CAULDRON, "Use a cauldron %,d times", custom("use_cauldron", "fill_cauldron"), 1, r(C, U), 10, "Bath Time",
				100, "Witch's Kitchen");
		ladder(c, "valstrik", Items.TRAPPED_CHEST, "Spring %,d trapped chests", custom("trigger_trapped_chest"), 1, r(U), 10, "Booby Trapped");
		ladder(c, "onheil", Items.OMINOUS_BOTTLE, "Bring a bad omen to a village|Bring bad omens to villages %,d times", custom("raid_trigger"), 1,
				r(U, R), 1, "Bad Omen", 10, "Trouble Magnet");
		ladder(c, "ton", Items.BARREL, "Open a barrel %,d times", custom("open_barrel"), 1, r(C, U), 100, "Barrel Roll", 1000, "Over a Barrel");
		ladder(c, "trechterkijk", Items.HOPPER, "Look into hoppers, droppers and dispensers %,d times", custom("inspect_hopper", "inspect_dropper",
				"inspect_dispenser"), 1, r(C), 100, "Redstone Inspector");
		ladder(c, "lessenaar", Items.LECTERN, "Read at a lectern %,d times", custom("interact_with_lectern"), 1, r(C), 10, "Bookworm");
		ladder(c, "kampvuur", Items.CAMPFIRE, "Cook on a campfire %,d times", custom("interact_with_campfire"), 1, r(C), 10, "Campfire Stories");
		ladder(c, "stemmen", Items.NOTE_BLOCK, "Tune a note block %,d times", custom("tune_noteblock"), 1, r(C), 100, "Piano Tuner");
		ladder(c, "kroeg", Items.ENDER_CHEST, "Open an ender chest %,d times", custom("open_enderchest"), 1, r(R), 1000, "Void Regular");
	}

	/** Puns and days: what you eat, place and use, how often you come back, how much you talk. */
	private static void corny() {
		Category life = Category.TIJD, farm = Category.FOKKEN, make = Category.MAKEN, mine = Category.MIJNBOUW;
		ladder(life, "dagen", Items.CLOCK, "Play on %,d different days", new Catalog.Counter((p, d) -> d.daysPlayed), 1, r(C, U, R, E),
				5, "Frequent Flyer", 30, "Season Ticket Holder", 100, "Mortgage Holder", 365, "Year-Rounder");
		ladder(life, "reeks", Items.SUNFLOWER, "Play %,d days in a row", new Catalog.Counter((p, d) -> d.bestStreak), 1, r(U, R, E, L),
				3, "Habit Forming", 7, "Creature of Habit", 30, "Can't Stay Away", 100, "Lives Here Now");
		ladder(life, "praatjes", Items.WRITABLE_BOOK, "Send %,d chat messages", new Catalog.Counter((p, d) -> d.chats), 1, r(C, U, R),
				100, "Chatterbox", 1000, "Motormouth", 10000, "Talk of the Town");
		// Food puns
		ladder(life, "meloen.eet", Items.MELON_SLICE, "Eat %,d melon slices", used(itemsOf("melon_slice")), 1, r(C, U), 64, "One in a Melon",
				640, "Melon-choly");
		ladder(life, "appel.eet", Items.APPLE, "Eat %,d apples", used(itemsOf("apple")), 1, r(C, U), 64, "Apple of My Eye", 640, "Core Values");
		ladder(life, "biet.eet", Items.BEETROOT, "Eat %,d beetroots", used(itemsOf("beetroot")), 1, r(C, U), 64, "Can't Beet This", 640, "Beet Poet");
		ladder(life, "bes.eet", Items.SWEET_BERRIES, "Eat %,d sweet berries", used(itemsOf("sweet_berries")), 1, r(C, U), 64, "Berry Good",
				640, "Berry Much So");
		ladder(life, "gloeibes.eet", Items.GLOW_BERRIES, "Eat %,d glow berries", used(itemsOf("glow_berries")), 1, r(C, U), 64, "Light Snack",
				640, "Bright Idea");
		ladder(life, "aardappel.eet", Items.BAKED_POTATO, "Eat %,d baked potatoes", used(itemsOf("baked_potato")), 1, r(C, U), 64, "Hot Potato",
				640, "Spud-tacular");
		ladder(life, "kip.eet", Items.COOKED_CHICKEN, "Eat %,d cooked chicken", used(itemsOf("cooked_chicken")), 1, r(C, U), 64, "Chicken Dinner",
				640, "Wing It");
		ladder(life, "varken.eet", Items.COOKED_PORKCHOP, "Eat %,d porkchops", used(itemsOf("cooked_porkchop", "porkchop")), 1, r(C, U), 64,
				"Pigging Out", 640, "Hamming It Up");
		ladder(life, "schaap.eet", Items.COOKED_MUTTON, "Eat %,d mutton", used(itemsOf("cooked_mutton", "mutton")), 1, r(C, U), 64, "Ewe Hungry?",
				640, "Lamb-bition");
		ladder(life, "vis.eet", Items.COOKED_SALMON, "Eat %,d fish", used(itemsOf("cooked_cod", "cooked_salmon", "cod", "salmon")), 1, r(C, U),
				64, "Something Fishy", 640, "Living Off the Sea");
		ladder(life, "wortel.eet", Items.CARROT, "Eat %,d carrots", used(itemsOf("carrot")), 1, r(C, U), 64, "Carrot Cruncher", 640, "Rabbit Food");
		ladder(life, "tropisch.eet", Items.TROPICAL_FISH, "Eat %,d tropical fish", used(itemsOf("tropical_fish")), 1, r(U), 10, "Exotic Taste");
		ladder(life, "oogjes", Items.ENDER_EYE, "Throw %,d eyes of ender", used(itemsOf("ender_eye")), 1, r(U, R), 12, "Eye Spy", 100, "Stronghold Seeker");
		ladder(life, "wind", Items.WIND_CHARGE, "Throw %,d wind charges", used(itemsOf("wind_charge")), 1, r(C, U), 100, "Windbag", 1000, "Full of Hot Air");
		ladder(life, "plons", Items.SPLASH_POTION, "Throw %,d splash potions", used(itemsOf("splash_potion", "lingering_potion")), 1, r(C, U),
				64, "Splash Zone", 640, "Making Waves");
		ladder(life, "ervaring", Items.EXPERIENCE_BOTTLE, "Throw %,d bottles o' enchanting", used(itemsOf("experience_bottle")), 1, r(C, U),
				64, "Quick Study", 640, "Crammer");
		// Gathering puns
		ladder(farm, "paddenstoel.pluk", Items.BROWN_MUSHROOM, "Gather %,d mushrooms and fungi", gathered(itemsOf("red_mushroom", "brown_mushroom",
				"crimson_fungus", "warped_fungus")), 1, r(C, U), 64, "Fun Guy", 640, "Spore Lord");
		ladder(farm, "vangst", Items.SALMON, "Reel in %,d fish", gathered(itemsOf("cod", "salmon", "tropical_fish", "pufferfish")), 1, r(C, U, R),
				10, "Reel Talk", 100, "Hook, Line and Sinker", 1000, "Fishy Business");
		ladder(mine, "gneis", Items.GRANITE, "Mine %,d granite, diorite and andesite", mined("granite", "diorite", "andesite"), 1, r(C, U, R),
				100, "Gneiss Work", 1000, "Rock Solid", 10000, "Boulder Than Ever");
		// Building puns
		ladder(make, "tapijt.leg", Ui.item("red_carpet"), "Place %,d carpets", used(itemsWhere(id -> id.endsWith("_carpet"))), 1, r(C, U),
				100, "Rug Life", 1000, "Wall-to-Wall");
		ladder(make, "hek.leg", Items.OAK_FENCE, "Place %,d fences", used(itemsWhere(id -> id.endsWith("_fence"))), 1, r(C, U), 100, "On the Fence",
				1000, "Good Fences, Good Neighbours");
		ladder(make, "ruit.leg", Items.GLASS_PANE, "Place %,d glass panes", used(itemsWhere(id -> id.endsWith("glass_pane"))), 1, r(C, U),
				100, "Window Dresser", 1000, "Pane in the Neck");
		ladder(make, "ladder.leg", Items.LADDER, "Place %,d ladders", used(itemsOf("ladder")), 1, r(C, U), 100, "Climbing the Ladder",
				1000, "Social Climber");
		ladder(make, "steiger.leg", Items.SCAFFOLDING, "Place %,d scaffolding", used(itemsOf("scaffolding")), 1, r(C, U), 100, "Scaffold Hopper",
				1000, "Under Construction");
		ladder(make, "ketting.leg", Ui.item("iron_chain"), "Place %,d chains", used(itemsWhere(id -> id.endsWith("chain"))), 1, r(C, U), 64,
				"Chain Reaction", 640, "Chained Up");
		ladder(make, "plaat.leg", Items.STONE_SLAB, "Place %,d slabs", used(itemsWhere(id -> id.endsWith("_slab"))), 1, r(C, U), 1000, "Slab Happy",
				10000, "Half-Baked Builder");
		ladder(make, "blad.leg", Items.OAK_LEAVES, "Place %,d leaves", used(itemsWhere(id -> id.endsWith("_leaves"))), 1, r(C, U), 100,
				"Turning Over a New Leaf", 1000, "Treehouse Architect");
		ladder(make, "hooi.leg", Items.HAY_BLOCK, "Place %,d hay bales", used(itemsOf("hay_block")), 1, r(C, U), 64, "Hit the Hay", 640, "Haystack");
		ladder(make, "honing.leg", Items.HONEY_BLOCK, "Place %,d honey blocks", used(itemsOf("honey_block")), 1, r(U), 16, "Hive Mind");
		ladder(make, "lijst.leg", Items.ITEM_FRAME, "Hang %,d item frames", used(itemsOf("item_frame", "glow_item_frame")), 1, r(C, U), 64,
				"Frame of Mind", 640, "Framed for Life");
		ladder(make, "standaard.leg", Items.ARMOR_STAND, "Place %,d armor stands", used(itemsOf("armor_stand")), 1, r(C, U), 10, "Stand-up Comedian",
				100, "Standing Ovation");
		ladder(make, "schilderij.leg", Items.PAINTING, "Hang %,d paintings", used(itemsOf("painting")), 1, r(C, U), 10, "Gallery Wall",
				100, "Curator");
		ladder(make, "bed.leg", Ui.item("red_bed"), "Place %,d beds", used(itemsWhere(id -> id.endsWith("_bed"))), 1, r(C, U), 16, "Bedtime Stories",
				160, "Bed and Breakfast");
		ladder(make, "trap.leg", Items.OAK_STAIRS, "Place %,d stairs", used(itemsWhere(id -> id.endsWith("_stairs"))), 1, r(C, U), 1000,
				"Stair-ing Contest", 10000, "Stairway Legend");
		ladder(make, "boot.leg", Items.OAK_BOAT, "Put %,d boats in the water", used(itemsWhere(id -> id.endsWith("_boat") || id.endsWith("_raft"))),
				1, r(C, U), 10, "Boatload", 100, "Fleet Admiral");
		ladder(make, "kar.leg", Items.MINECART, "Place %,d minecarts", used(itemsWhere(id -> id.endsWith("minecart"))), 1, r(C, U), 10, "Cart Wheel",
				100, "Rush Hour");
	}

	private static List<Block> blocksWhere(java.util.function.Predicate<String> match) {
		List<Block> out = new ArrayList<>();
		for (Block b : BuiltInRegistries.BLOCK) if (match.test(BuiltInRegistries.BLOCK.getKey(b).getPath())) out.add(b);
		return out;
	}
}
