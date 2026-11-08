package nl.libreria.collector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import nl.libreria.core.Completion;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * The collector's collections for the Progress window: achievements, every item found, animals bred and tamed, the
 * villagers traded with, potions brewed, what came up on the hook, music discs played, enchantments and armour trims
 * held. And its counters for the top lists (points, zombies cured, villagers born…).
 */
public final class CollectorCollections {
	/** Animals a player can tame (what the server doesn't know falls away by itself). */
	static final List<String> TAMEABLE = List.of("wolf", "cat", "parrot", "horse", "donkey", "mule", "llama", "trader_llama", "nautilus");
	/** What can come up on a fishing hook: fish, junk and treasure. */
	static final List<String> FISHED = List.of("cod", "salmon", "tropical_fish", "pufferfish", "name_tag", "saddle", "bow", "fishing_rod",
			"enchanted_book", "nautilus_shell", "lily_pad", "leather_boots", "leather", "bone", "potion", "string", "bowl", "stick",
			"ink_sac", "tripwire_hook", "rotten_flesh", "bamboo");
	/** The workstation of each profession: its picture. */
	private static final java.util.Map<String, Item> WORKSTATION = java.util.Map.ofEntries(
			java.util.Map.entry("armorer", Items.BLAST_FURNACE), java.util.Map.entry("butcher", Items.SMOKER),
			java.util.Map.entry("cartographer", Items.CARTOGRAPHY_TABLE), java.util.Map.entry("cleric", Items.BREWING_STAND),
			java.util.Map.entry("farmer", Items.COMPOSTER), java.util.Map.entry("fisherman", Items.BARREL),
			java.util.Map.entry("fletcher", Items.FLETCHING_TABLE), java.util.Map.entry("leatherworker", Items.CAULDRON),
			java.util.Map.entry("librarian", Items.LECTERN), java.util.Map.entry("mason", Items.STONECUTTER),
			java.util.Map.entry("shepherd", Items.LOOM), java.util.Map.entry("toolsmith", Items.SMITHING_TABLE),
			java.util.Map.entry("weaponsmith", Items.GRINDSTONE));

	private CollectorCollections() {
	}

	private static String cap(String id) {
		String s = id.replace('_', ' ');
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	private static String itemName(Item item) {
		return new ItemStack(item).getHoverName().getString();
	}

	private static @Nullable MinecraftServer server() {
		return nl.libreria.core.Libreria.server();
	}

	private static ItemStack egg(String entity, Item fallback) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(entity + "_spawn_egg")).map(ItemStack::new)
				.orElseGet(() -> new ItemStack(fallback));
	}

	private static List<Completion.Entry> animals(List<String> ids, Item fallback) {
		List<Completion.Entry> out = new ArrayList<>();
		for (String id : ids) {
			var type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.withDefaultNamespace(id));
			if (type.isEmpty()) continue;
			out.add(new Completion.Entry(id, egg(id, fallback), type.get().getDescription().getString()));
		}
		return out;
	}

	private static List<Completion.Entry> items(List<String> ids) {
		List<Completion.Entry> out = new ArrayList<>();
		for (String id : ids) {
			var item = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(id));
			if (item.isEmpty()) continue;
			out.add(new Completion.Entry(id, new ItemStack(item.get()), itemName(item.get())));
		}
		return out;
	}

	static void init() {
		// ---------------- Collect
		Completion.add("achievements", Completion.Tab.COLLECT, 10, Items.NETHER_STAR, "Achievements")
				.about("Libreria's achievements: collect, breed, explore, play. Every one is within reach.")
				.words("Earned", "Not earned yet")
				.version(() -> Catalog.all().size() * 31 + Catalog.signature())
				.entries(() -> {
					List<Completion.Entry> out = new ArrayList<>();
					for (Achievement a : Catalog.all()) {
						String name = Catalog.isSecret(a) ? "A secret" : a.name();
						out.add(new Completion.Entry(a.id(), new ItemStack(a.icon()), name));
					}
					return out;
				})
				.done((s, who, st) -> new HashSet<>(CollectorMod.STORE.get(who).earned.keySet()))
				.detail((e, st, done) -> {
					Achievement a = Catalog.get(e.key());
					if (a == null) return null;
					if (Catalog.isSecret(a)) return done ? a.name() : Catalog.secretClue(a);
					return a.description();
				})
				.own((viewer, who, back) -> {
					if (!viewer.getUUID().equals(who)) return false;
					nl.libreria.collector.gui.AchievementsGui.open(viewer);
					return true;
				});
		Completion.add("items", Completion.Tab.COLLECT, 20, Items.BUNDLE, "Items found")
				.about("Every item there is, had in your inventory at least once.")
				.words("Found", "Not found yet")
				.version(() -> Catalog.allItems().size())
				.entries(() -> items(Catalog.allItems()))
				.done((s, who, st) -> new HashSet<>(CollectorMod.STORE.get(who).items));
		Completion.add("enchantments", Completion.Tab.COLLECT, 40, Items.ENCHANTED_BOOK, "Enchantments")
				.about("Every enchantment, on a tool, a weapon, armour or a book in your inventory.")
				.words("Had", "Not had yet")
				.entries(CollectorCollections::enchantments)
				.done((s, who, st) -> new HashSet<>(CollectorMod.STORE.get(who).enchantments));
		Completion.add("discs", Completion.Tab.COLLECT, 50, Items.MUSIC_DISC_CAT, "Music discs")
				.about("Every record, played in a jukebox.")
				.words("Played", "Not played yet")
				.entries(() -> {
					List<Completion.Entry> out = new ArrayList<>();
					for (Item item : BuiltInRegistries.ITEM) {
						if (!new ItemStack(item).has(DataComponents.JUKEBOX_PLAYABLE)) continue;
						String id = BuiltInRegistries.ITEM.getKey(item).getPath();
						String song = id.startsWith("music_disc_") ? cap(id.substring("music_disc_".length())) : cap(id);
						out.add(new Completion.Entry(id, new ItemStack(item), "Music Disc · " + song));
					}
					return out;
				})
				.done((s, who, st) -> new HashSet<>(CollectorMod.STORE.get(who).discs));
		Completion.add("trims", Completion.Tab.COLLECT, 60, Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, "Armour trims")
				.about("Every armour trim, on a piece of armour in your inventory.")
				.words("Had", "Not had yet")
				.entries(CollectorCollections::trims)
				.done((s, who, st) -> new HashSet<>(CollectorMod.STORE.get(who).trims));
		// ---------------- Do
		Completion.add("brewed", Completion.Tab.DO, 40, Items.BREWING_STAND, "Brewed")
				.about("Every potion, taken out of a brewing stand.")
				.words("Brewed", "Not brewed yet")
				.entries(CollectorCollections::potions)
				.done((s, who, st) -> new HashSet<>(CollectorMod.STORE.get(who).brewed));
		Completion.add("fished", Completion.Tab.DO, 50, Items.FISHING_ROD, "Fished up")
				.about("Every fish, every bit of junk and every treasure that can come up on the hook.")
				.words("Caught", "Not caught yet")
				.entries(() -> items(FISHED))
				.done((s, who, st) -> new HashSet<>(CollectorMod.STORE.get(who).fished));
		Completion.add("traded", Completion.Tab.DO, 60, Items.EMERALD, "Traded with")
				.about("A trade with every kind of villager, and with the wandering trader.")
				.words("Traded", "Not traded yet")
				.entries(() -> {
					List<Completion.Entry> out = new ArrayList<>();
					for (String p : WORKSTATION.keySet().stream().sorted().toList()) {
						if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(Identifier.withDefaultNamespace(p))) continue;
						out.add(new Completion.Entry(p, new ItemStack(WORKSTATION.get(p)), cap(p)));
					}
					out.add(new Completion.Entry("wandering_trader", egg("wandering_trader", Items.EMERALD), "Wandering Trader"));
					return out;
				})
				.done((s, who, st) -> new HashSet<>(CollectorMod.STORE.get(who).traded));
		// ---------------- Explore
		Completion.add("bred", Completion.Tab.EXPLORE, 40, Items.WHEAT, "Bred")
				.about("A baby of every kind of animal that can have one.")
				.words("Bred", "Not bred yet")
				.entries(() -> animals(Catalog.BREEDABLE, Items.EGG))
				.done((s, who, st) -> new HashSet<>(CollectorMod.STORE.get(who).bred));
		Completion.add("tamed", Completion.Tab.EXPLORE, 50, Items.BONE, "Tamed")
				.about("Every animal that can be tamed, tamed by you.")
				.words("Tamed", "Not tamed yet")
				.entries(() -> animals(TAMEABLE, Items.LEAD))
				.done((s, who, st) -> new HashSet<>(CollectorMod.STORE.get(who).tamed));

		// ---------------- counters
		Completion.counter("points", 15, Items.GOLD_INGOT, "Achievement points", "Every achievement, by its rarity.",
				(s, who, st) -> CollectorMod.points(CollectorMod.STORE.get(who)), Ui::n);
		Completion.counter("cured", 75, Items.GOLDEN_APPLE, "Zombies cured", "Zombie villagers made villagers again.",
				(s, who, st) -> CollectorMod.STORE.get(who).cured, Ui::n);
		Completion.counter("villagers", 80, Items.BELL, "Villagers born", "Villager babies born near you.",
				(s, who, st) -> CollectorMod.STORE.get(who).villagers, Ui::n);
		Completion.counter("streak", 95, Items.CLOCK, "Days in a row", "The most days in a row you played here.",
				(s, who, st) -> Math.max(CollectorMod.STORE.get(who).bestStreak, CollectorMod.STORE.get(who).streak), Ui::n);
		Completion.counter("records", 100, Items.GOLD_BLOCK, "Records held", "Server records held right now.",
				(s, who, st) -> Records.held(who), Ui::n);
	}

	private static List<Completion.Entry> enchantments() {
		MinecraftServer server = server();
		if (server == null) return List.of();
		List<Completion.Entry> out = new ArrayList<>();
		server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(h -> {
			String id = h.key().identifier().toString();
			ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
			out.add(new Completion.Entry(id, book, h.value().description().getString()));
		});
		out.sort(java.util.Comparator.comparing(Completion.Entry::name));
		return out;
	}

	private static List<Completion.Entry> trims() {
		MinecraftServer server = server();
		if (server == null) return List.of();
		List<Completion.Entry> out = new ArrayList<>();
		server.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).listElements().forEach(h -> {
			Identifier id = h.key().identifier();
			Item template = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_armor_trim_smithing_template"))
					.orElse(Items.SMITHING_TABLE);
			out.add(new Completion.Entry(id.toString(), new ItemStack(template), h.value().description().getString()));
		});
		out.sort(java.util.Comparator.comparing(Completion.Entry::name));
		return out;
	}

	/** Every potion that can be brewed (water and luck can't), as a potion bottle. */
	private static List<Completion.Entry> potions() {
		List<Completion.Entry> out = new ArrayList<>();
		for (Potion p : BuiltInRegistries.POTION) {
			Identifier id = BuiltInRegistries.POTION.getKey(p);
			if (id == null || id.getPath().equals("water") || id.getPath().equals("luck")) continue;
			Holder<Potion> h = BuiltInRegistries.POTION.wrapAsHolder(p);
			ItemStack bottle = PotionContents.createItemStack(Items.POTION, h);
			String name = bottle.getHoverName().getString();
			if (id.getPath().startsWith("long_")) name += " (long)";
			else if (id.getPath().startsWith("strong_")) name += " II";
			out.add(new Completion.Entry(id.getPath(), bottle, name));
		}
		return out;
	}

	// ---------------------------------------------------------------- noticing (from the mixins and the inventory scan)

	private static void note(@Nullable ServerPlayer p, Function<CollectorMod.Data, Set<String>> set, @Nullable String key) {
		if (p == null || key == null || key.isEmpty()) return;
		if (set.apply(CollectorMod.STORE.get(p)).add(key)) CollectorMod.STORE.markDirty(p);
	}

	public static void onTamed(ServerPlayer p, Animal animal) {
		note(p, d -> d.tamed, EntityType.getKey(animal.getType()).getPath());
	}

	public static void onTraded(ServerPlayer p, AbstractVillager trader) {
		String key;
		if (trader instanceof Villager v) key = v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse(null);
		else key = EntityType.getKey(trader.getType()).getPath();
		note(p, d -> d.traded, key);
	}

	public static void onBrewed(ServerPlayer p, PotionContents contents) {
		contents.potion().flatMap(Holder::unwrapKey).ifPresent(k -> note(p, d -> d.brewed, k.identifier().getPath()));
	}

	public static void onFished(ServerPlayer p, Collection<ItemStack> items) {
		for (ItemStack st : items) {
			if (st.isEmpty()) continue;
			String id = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
			if (FISHED.contains(id)) note(p, d -> d.fished, id);
		}
	}

	public static void onDisc(ServerPlayer p, ItemStack disc) {
		if (disc.isEmpty() || !disc.has(DataComponents.JUKEBOX_PLAYABLE)) return;
		note(p, d -> d.discs, BuiltInRegistries.ITEM.getKey(disc.getItem()).getPath());
	}

	/** From the inventory scan: the enchantments and the armour trim on a stack. @return true if something was new */
	static boolean scan(CollectorMod.Data d, ItemStack st) {
		boolean changed = false;
		for (var type : List.of(DataComponents.ENCHANTMENTS, DataComponents.STORED_ENCHANTMENTS)) {
			ItemEnchantments ench = st.get(type);
			if (ench == null || ench.isEmpty()) continue;
			for (var h : ench.keySet()) {
				var key = h.unwrapKey();
				if (key.isPresent() && d.enchantments.add(key.get().identifier().toString())) changed = true;
			}
		}
		ArmorTrim trim = st.get(DataComponents.TRIM);
		if (trim != null) {
			var key = trim.pattern().unwrapKey();
			if (key.isPresent() && d.trims.add(key.get().identifier().toString())) changed = true;
		}
		return changed;
	}

	static String lower(String s) {
		return s.toLowerCase(Locale.ROOT);
	}
}
