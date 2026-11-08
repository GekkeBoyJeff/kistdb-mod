package nl.libreria.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * The collections Minecraft's own statistics already keep: everything you can eat or drink, everything you can craft,
 * the ores and treasures of the ground, every kind of monster, and the advancements. And counters for the top lists.
 */
final class CoreCollections {
	/** What the ground hides: the ores in stone and deepslate, the Nether's, and a few rare blocks. */
	private static final List<String> MINED = List.of("coal_ore", "deepslate_coal_ore", "copper_ore", "deepslate_copper_ore",
			"iron_ore", "deepslate_iron_ore", "gold_ore", "deepslate_gold_ore", "redstone_ore", "deepslate_redstone_ore", "lapis_ore",
			"deepslate_lapis_ore", "diamond_ore", "deepslate_diamond_ore", "emerald_ore", "deepslate_emerald_ore", "nether_gold_ore",
			"nether_quartz_ore", "ancient_debris", "raw_iron_block", "raw_copper_block", "gilded_blackstone", "amethyst_cluster",
			"obsidian", "crying_obsidian", "glowstone", "blue_ice", "pointed_dripstone", "sculk_sensor", "sculk_catalyst",
			"sculk_shrieker", "spawner");
	/** Monsters that never come in survival, or that no player can be said to defeat. */
	private static final Set<String> NOT_MONSTERS = Set.of("giant", "illusioner", "creaking");

	private CoreCollections() {
	}

	static String itemName(Item item) {
		return new ItemStack(item).getHoverName().getString();
	}

	static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/** The spawn egg of a kind of mob (its picture in a list), or {@code fallback}. */
	public static ItemStack egg(String entity, Item fallback) {
		Identifier id = Identifier.tryParse(entity.contains(":") ? entity : "minecraft:" + entity);
		if (id == null) return new ItemStack(fallback);
		Identifier egg = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg");
		return BuiltInRegistries.ITEM.getOptional(egg).map(ItemStack::new).orElseGet(() -> new ItemStack(fallback));
	}

	/** The keys of a statistic type that are above zero ("minecraft:bread"…). */
	static Set<String> counted(Profile.Stats stats, String type) {
		Set<String> out = new HashSet<>();
		for (Map.Entry<String, Long> e : stats.all(type).entrySet()) if (e.getValue() > 0) out.add(e.getKey());
		return out;
	}

	private static String times(long n) {
		return n == 1 ? "once" : n == 2 ? "twice" : Ui.n(n) + " times";
	}

	static void init() {
		// ---------------- Do
		Completion.add("crafted", Completion.Tab.DO, 10, Items.CRAFTING_TABLE, "Crafted")
				.about("Everything the crafting table can make, made at least once.")
				.words("Crafted", "Not crafted yet")
				.entries(CoreCollections::craftable)
				.done((s, who, st) -> counted(st, "crafted"))
				.detail((e, st, done) -> done ? "Crafted " + Ui.n(st.stat("crafted", e.key())) : null);
		Completion.add("tasted", Completion.Tab.DO, 20, Items.COOKED_BEEF, "Tasted")
				.about("Every food and every drink, had at least once.")
				.words("Tasted", "Not tasted yet")
				.entries(CoreCollections::edible)
				.done((s, who, st) -> {
					Set<String> out = counted(st, "used");
					if (st.custom("eat_cake_slice") > 0) out.add("minecraft:cake");
					return out;
				})
				.detail((e, st, done) -> {
					if (!done) return null;
					long n = e.key().equals("minecraft:cake") ? st.custom("eat_cake_slice") : st.stat("used", e.key());
					return "Had " + times(n);
				});
		Completion.add("mined", Completion.Tab.DO, 30, Items.DIAMOND_PICKAXE, "Mined")
				.about("Every ore, in stone, deepslate and the Nether, and the rare blocks of the deep.")
				.words("Mined", "Not mined yet")
				.entries(() -> {
					List<Completion.Entry> out = new ArrayList<>();
					for (String b : MINED) {
						Identifier id = Identifier.withDefaultNamespace(b);
						if (!BuiltInRegistries.BLOCK.containsKey(id)) continue;
						Block block = BuiltInRegistries.BLOCK.getValue(id);
						Item item = block.asItem();
						out.add(new Completion.Entry(id.toString(), new ItemStack(item == Items.AIR ? Items.STONE : item), block.getName().getString()));
					}
					return out;
				})
				.done((s, who, st) -> counted(st, "mined"))
				.detail((e, st, done) -> done ? "Mined " + Ui.n(st.stat("mined", e.key())) : null);
		// ---------------- Explore
		Completion.add("defeated", Completion.Tab.EXPLORE, 30, Items.IRON_SWORD, "Defeated")
				.about("Every kind of monster, defeated at least once.")
				.words("Defeated", "Not defeated yet")
				.entries(() -> {
					List<Completion.Entry> out = new ArrayList<>();
					for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
						Identifier id = EntityType.getKey(t);
						if (t.getCategory() != MobCategory.MONSTER || NOT_MONSTERS.contains(id.getPath())) continue;
						out.add(new Completion.Entry(id.toString(), egg(id.toString(), Items.ZOMBIE_HEAD), t.getDescription().getString()));
					}
					out.sort(Comparator.comparing(Completion.Entry::name));
					return out;
				})
				.done((s, who, st) -> counted(st, "killed"))
				.detail((e, st, done) -> done ? "Defeated " + Ui.n(st.stat("killed", e.key())) : null);
		// ---------------- Collect
		Completion.add("advancements", Completion.Tab.COLLECT, 70, Items.KNOWLEDGE_BOOK, "Advancements")
				.about("Minecraft's own advancements (L), every one.")
				.words("Done", "Not done yet")
				.entries(CoreCollections::advancements)
				.done(CoreCollections::advancementsDone);

		// ---------------- counters (top lists only)
		Completion.counter("playtime", 10, Items.CLOCK, "Time played", "Hours on this server.", (s, w, st) -> st.custom("play_time"),
				v -> v >= 72000 ? Ui.n(v / 72000) + " h" : v / 1200 + " min");
		Completion.counter("walked", 20, Items.LEATHER_BOOTS, "Distance walked", "Walking and sprinting.",
				(s, w, st) -> st.custom("walk_one_cm") + st.custom("sprint_one_cm"),
				v -> v >= 100_000 ? String.format(Locale.ROOT, "%.1f km", v / 100_000.0) : v / 100 + " m");
		Completion.counter("monsters", 30, Items.ZOMBIE_HEAD, "Monsters slain", "Every monster, of every kind.",
				(s, w, st) -> st.killed(t -> t.getCategory() == MobCategory.MONSTER), Ui::n);
		Completion.counter("mined_total", 40, Items.IRON_PICKAXE, "Blocks mined", "Every block broken with a tool or a hand.",
				(s, w, st) -> {
					long n = 0;
					for (long v : st.all("mined").values()) n += v;
					return n;
				}, Ui::n);
		Completion.counter("fish", 50, Items.COD, "Fish caught", "Everything on the hook.", (s, w, st) -> st.custom("fish_caught"), Ui::n);
		Completion.counter("trades", 60, Items.EMERALD, "Trades", "Trades with villagers and traders.",
				(s, w, st) -> st.custom("traded_with_villager"), Ui::n);
		Completion.counter("animals_bred", 70, Items.WHEAT, "Animals bred", "Every baby animal.", (s, w, st) -> st.custom("animals_bred"),
				Ui::n);
		Completion.counter("raids", 90, Items.CROSSBOW, "Raids won", "Villages saved from a raid.", (s, w, st) -> st.custom("raid_win"),
				Ui::n);
	}

	// ---------------------------------------------------------------- what there is

	/** Everything that can be eaten or drunk (and cake, eaten off the block). */
	private static List<Completion.Entry> edible() {
		List<Completion.Entry> out = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			if (!new ItemStack(item).has(DataComponents.CONSUMABLE)) continue;
			out.add(new Completion.Entry(id(item), new ItemStack(item), itemName(item)));
		}
		out.add(new Completion.Entry(id(Items.CAKE), new ItemStack(Items.CAKE), itemName(Items.CAKE)));
		return out;
	}

	/** Every result of the crafting table's recipes (not the special ones like dyeing). */
	private static List<Completion.Entry> craftable() {
		MinecraftServer server = Libreria.server();
		if (server == null) return List.of();
		var context = SlotDisplayContext.fromLevel(server.overworld());
		Set<Item> items = new java.util.LinkedHashSet<>();
		for (RecipeHolder<?> h : server.getRecipeManager().getRecipes()) {
			if (h.value().getType() != RecipeType.CRAFTING || h.value().isSpecial()) continue;
			for (RecipeDisplay d : h.value().display()) {
				ItemStack result = d.result().resolveForFirstStack(context);
				if (!result.isEmpty()) items.add(result.getItem());
			}
		}
		List<Completion.Entry> out = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) if (items.contains(item)) out.add(new Completion.Entry(id(item), new ItemStack(item), itemName(item)));
		return out;
	}

	/** Minecraft's own advancements that have a picture (the recipes and Libreria's own tabs are left out). */
	private static List<Completion.Entry> advancements() {
		MinecraftServer server = Libreria.server();
		if (server == null) return List.of();
		List<AdvancementHolder> all = new ArrayList<>();
		for (AdvancementHolder h : server.getAdvancements().getAllAdvancements()) {
			if (!h.id().getNamespace().equals("minecraft") || h.value().display().isEmpty()) continue;
			all.add(h);
		}
		all.sort(Comparator.comparing(h -> h.id().toString()));
		List<Completion.Entry> out = new ArrayList<>();
		for (AdvancementHolder h : all) {
			DisplayInfo d = h.value().display().get();
			ItemStack icon;
			try {
				icon = d.icon().create();
			} catch (RuntimeException e) {
				icon = new ItemStack(Items.KNOWLEDGE_BOOK);
			}
			out.add(new Completion.Entry(h.id().toString(), icon, d.title().getString()));
		}
		return out;
	}

	private static Set<String> advancementsDone(MinecraftServer server, UUID who, Profile.Stats stats) {
		Set<String> out = new HashSet<>();
		ServerPlayer p = server.getPlayerList().getPlayer(who);
		if (p != null) {
			for (AdvancementHolder h : server.getAdvancements().getAllAdvancements()) {
				if (h.id().getNamespace().equals("minecraft") && p.getAdvancements().getOrStartProgress(h).isDone()) out.add(h.id().toString());
			}
			return out;
		}
		JsonObject file = advancementFile(server, who);
		if (file == null) return out;
		for (Map.Entry<String, JsonElement> e : file.entrySet()) {
			if (e.getValue() instanceof JsonObject o && o.get("done") != null && o.get("done").isJsonPrimitive() && o.get("done").getAsBoolean()) {
				out.add(e.getKey().contains(":") ? e.getKey() : "minecraft:" + e.getKey());
			}
		}
		return out;
	}

	private static @Nullable JsonObject advancementFile(MinecraftServer server, UUID who) {
		Path world = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
		for (Path f : new Path[] {world.resolve("players").resolve("advancements").resolve(who + ".json"),
				world.resolve("advancements").resolve(who + ".json")}) {
			if (!Files.isRegularFile(f)) continue;
			try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
				JsonElement json = JsonParser.parseReader(r);
				if (json.isJsonObject()) return json.getAsJsonObject();
			} catch (Exception e) {
				Libreria.LOG.warn("Could not read {}", f, e);
			}
		}
		return null;
	}
}
