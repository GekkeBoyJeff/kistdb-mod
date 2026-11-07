package nl.libreria.info;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import nl.libreria.core.Chronicle;
import nl.libreria.core.Libreria;
import nl.libreria.core.ServerSetting;
import nl.libreria.core.WorldStore;
import org.jetbrains.annotations.Nullable;

/**
 * Found gear has a story. Anything that can be enchanted that comes out of a chest, a vault, the sea, the sand, a
 * piglin or a monster gets a text of its own when a player first takes it: funny, dark, corny or quietly part of the
 * world's lore, and never the same twice on one server. Some texts are a part of a longer story (the Lantern Keeper,
 * the Ninth Village…): those name the item and say which story and which part they are. Under it: where it was found,
 * on which day, by whom.
 *
 * <p>The texts live in {@code libreria/found/*.txt} (see the header of those files). What was found where is known
 * when the loot is made (the loot table, the dimension); the text is chosen then too when a player made the loot
 * happen (so it can be read in the chest already), else when it reaches a player's inventory.
 */
final class Found {
	static final ServerSetting.Toggle ENABLED = InfoMod.MODULE.serverToggle("found_stories", "Stories on found gear", net.minecraft.world.item.Items.CHEST,
			true, "Gear found in chests, vaults, the sea,", "the sand or on monsters gets a text", "of its own: never the same one twice,",
			"some of them parts of longer stories.");
	static final String STAMP = "libreria_found", PENDING = "libreria_found_pending";
	static final int TEXT = 0xC8AFEB, HEAD = 0xF0C86E, WHERE = 0x6E6E6E;
	static final nl.libreria.core.Tips.Tip TIP = nl.libreria.core.Tips.register("found_story", nl.libreria.core.Domain.ITEMS,
			net.minecraft.world.item.Items.WRITTEN_BOOK, "A part of a story", "Some found gear belongs to a longer story:",
			"find the other parts. Read them in Items › Lore book.");

	/** One text: a line, a part of a story, or one combination of a family. */
	record Text(String id, Set<String> kinds, String where, String text, @Nullable String name, @Nullable String story,
			@Nullable String title, int part, int parts) {
	}

	/** A family: one form with parts, every combination its own text. */
	record Family(String id, Set<String> kinds, String where, String form, List<String> slots, List<List<String>> options) {
		long size() {
			long n = 1;
			for (List<String> o : this.options) n *= o.size();
			return n;
		}
	}

	static final class Data {
		/** Text ids handed out on this server. */
		public Set<String> used = new HashSet<>();
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/found.json", Data.class, Data::new);

	/** One thing a player found: which text, on what, where and when. */
	static final class Find {
		public String id = "";
		public String item = "";
		public String name = "";
		public String where = "";
		public int day;
	}

	/** Which parts of which stories a player has had in their inventory, and what they found themselves. */
	static final class Seen {
		public Map<String, Set<Integer>> parts = new HashMap<>();
		/** "story:part" → "where|day|by", from the item when the part was first held. */
		public Map<String, String> partInfo = new HashMap<>();
		/** Newest last. */
		public List<Find> finds = new ArrayList<>();
	}

	static final int MAX_FINDS = 2000;

	static final nl.libreria.core.PlayerStore<Seen> SEEN = nl.libreria.core.PlayerStore.create("libreria/stories", Seen.class, Seen::new, d -> {
		if (d.parts == null) d.parts = new HashMap<>();
		if (d.partInfo == null) d.partInfo = new HashMap<>();
		if (d.finds == null) d.finds = new ArrayList<>();
	});

	/** Every text by its id (lines and story parts; family texts are made again from their id). */
	static final Map<String, Text> BY_ID = new HashMap<>();
	static final List<Text> LINES = new ArrayList<>(), PIECES = new ArrayList<>();
	static final List<Family> FAMILIES = new ArrayList<>();
	/** Story id → title and number of parts. */
	static final Map<String, Text> STORIES = new LinkedHashMap<>();

	static final Set<String> WEAPON = Set.of("sword", "axe", "bow", "crossbow", "trident", "mace", "spear");
	static final Set<String> ARMOUR = Set.of("helmet", "chestplate", "leggings", "boots");
	static final Set<String> TOOL = Set.of("pickaxe", "shovel", "hoe", "shears", "axe");

	private Found() {
	}

	static Data data() {
		Data d = STORE.get();
		if (d.used == null) d.used = new HashSet<>();
		return d;
	}

	// ---------------------------------------------------------------- what kind of thing

	/** The kind of an item ("sword", "boots", "rod"…), null for what gets no text. */
	static @Nullable String kindOf(ItemStack stack) {
		if (stack.isEmpty()) return null;
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		for (String k : new String[] {"sword", "axe", "spear", "helmet", "chestplate", "leggings", "boots", "pickaxe", "shovel", "hoe"}) {
			if (id.endsWith("_" + k)) return id.endsWith("_pickaxe") ? "pickaxe" : k;
		}
		return switch (id) {
			case "bow", "crossbow", "trident", "mace", "shield", "elytra", "shears", "brush" -> id;
			case "turtle_helmet" -> "helmet";
			case "fishing_rod" -> "rod";
			case "flint_and_steel" -> "flint";
			case "carrot_on_a_stick", "warped_fungus_on_a_stick" -> "stick";
			default -> null;
		};
	}

	static String noun(String kind, ItemStack stack) {
		return switch (kind) {
			case "rod" -> "fishing rod";
			case "flint" -> "flint and steel";
			case "stick" -> stack.getItemName().getString().toLowerCase(Locale.ROOT);
			default -> kind;
		};
	}

	static boolean fits(Set<String> kinds, String kind) {
		if (kinds.contains("*") || kinds.contains(kind)) return true;
		return kinds.contains("weapon") && WEAPON.contains(kind) || kinds.contains("armour") && ARMOUR.contains(kind)
				|| kinds.contains("tool") && TOOL.contains(kind);
	}

	private static boolean specific(Set<String> kinds, String kind) {
		return kinds.contains(kind);
	}

	// ---------------------------------------------------------------- loading

	static void load() {
		LINES.clear();
		PIECES.clear();
		FAMILIES.clear();
		STORIES.clear();
		Optional<Path> dir = FabricLoader.getInstance().getModContainer("libreria_info").flatMap(c -> c.findPath("libreria/found"));
		if (dir.isEmpty()) {
			Libreria.LOG.warn("Found: no texts in the mod");
			return;
		}
		try (Stream<Path> files = Files.list(dir.get())) {
			for (Path f : files.sorted().toList()) {
				String name = f.getFileName().toString();
				if (!name.endsWith(".txt")) continue;
				List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
				if (name.startsWith("arcs")) readStories(lines);
				else if (name.startsWith("families")) readFamilies(lines);
				else readLines(lines);
			}
		} catch (IOException e) {
			Libreria.LOG.error("Found: could not read the texts", e);
		}
		BY_ID.clear();
		for (Text t : LINES) BY_ID.put(t.id(), t);
		for (Text t : PIECES) BY_ID.put(t.id(), t);
		Libreria.LOG.info("Found: {} texts, {} story parts in {} stories, {} families ({} combinations)", LINES.size(), PIECES.size(),
				STORIES.size(), FAMILIES.size(), FAMILIES.stream().mapToLong(Family::size).sum());
	}

	/** "kinds[@where]" → the kinds and the where. */
	private static String[] head(String s) {
		s = s.trim();
		int at = s.indexOf('@');
		return at < 0 ? new String[] {s, ""} : new String[] {s.substring(0, at).trim(), s.substring(at + 1).trim()};
	}

	private static Set<String> kinds(String s) {
		Set<String> out = new HashSet<>();
		for (String k : s.split(",")) if (!k.isBlank()) out.add(k.trim().toLowerCase(Locale.ROOT));
		return out;
	}

	static void readLines(List<String> lines) {
		Set<String> seen = new HashSet<>();
		for (Text t : LINES) seen.add(t.id());
		for (String raw : lines) {
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) continue;
			String[] parts = line.split("\\|", 3);
			if (parts.length < 2) continue;
			String[] h = head(parts[0]);
			String text = parts[1].trim();
			String id = "t:" + hash(text);
			if (!seen.add(id)) continue;
			LINES.add(new Text(id, kinds(h[0]), h[1], text, parts.length > 2 && !parts[2].isBlank() ? parts[2].trim() : null, null, null, 0, 0));
		}
	}

	static void readStories(List<String> lines) {
		String story = null, title = null;
		List<String[]> parts = new ArrayList<>();
		List<String> raw = new ArrayList<>(lines);
		raw.add("@ end | end");
		for (String r : raw) {
			String line = r.strip();
			if (line.isEmpty() || line.startsWith("#")) continue;
			if (line.startsWith("@")) {
				if (story != null && !parts.isEmpty()) {
					for (int i = 0; i < parts.size(); i++) {
						String[] p = parts.get(i);
						String[] h = head(p[0]);
						PIECES.add(new Text("a:" + story + ":" + (i + 1), kinds(h[0]), h[1], p[2].trim(), p[1].trim(), story, title, i + 1,
								parts.size()));
					}
					STORIES.put(story, PIECES.get(PIECES.size() - 1));
				}
				String[] s = line.substring(1).split("\\|", 2);
				story = s[0].trim();
				title = s.length > 1 ? s[1].trim() : story;
				parts = new ArrayList<>();
				continue;
			}
			String[] p = line.split("\\|", 3);
			if (p.length == 3) parts.add(p);
		}
	}

	static void readFamilies(List<String> lines) {
		String id = null, form = null, where = "";
		Set<String> kinds = Set.of("*");
		List<String> slots = new ArrayList<>();
		List<List<String>> options = new ArrayList<>();
		List<String> raw = new ArrayList<>(lines);
		raw.add("% end | * | end");
		for (String r : raw) {
			String line = r.strip();
			if (line.isEmpty() || line.startsWith("#")) continue;
			if (line.startsWith("%")) {
				if (id != null && !slots.isEmpty()) FAMILIES.add(new Family(id, kinds, where, form, List.copyOf(slots), List.copyOf(options)));
				String[] p = line.substring(1).split("\\|", 3);
				id = p[0].trim();
				String[] h = head(p.length > 1 ? p[1] : "*");
				kinds = kinds(h[0]);
				where = h[1];
				form = p.length > 2 ? p[2].trim() : "";
				slots = new ArrayList<>();
				options = new ArrayList<>();
				continue;
			}
			int colon = line.indexOf(':');
			if (colon < 0 || id == null) continue;
			slots.add(line.substring(0, colon).trim());
			List<String> o = new ArrayList<>();
			for (String s : line.substring(colon + 1).split(";")) if (!s.isBlank()) o.add(s.trim());
			options.add(o);
		}
	}

	private static String hash(String s) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8))).substring(0, 12);
		} catch (Exception e) {
			return Integer.toHexString(s.hashCode());
		}
	}

	// ---------------------------------------------------------------- choosing

	/** Where a find comes from, for the texts that are about a place: "nether", "bastion", "fishing"… */
	record Origin(String realm, String place, String label) {
		boolean matches(String where) {
			return where.isEmpty() || where.equals(this.realm) || where.equals(this.place);
		}
	}

	/** A text for this kind of thing from there, not handed out before (and now handed out). */
	static @Nullable Text pick(String kind, Origin from, Random r) {
		return pick(kind, from, r, null);
	}

	/**
	 * The same, for a finder: a one-off text is never handed out twice on the server, but a part of a story can be
	 * found by everyone (so everyone can collect a whole story): only the parts this finder already has are left out,
	 * and stories they've started come first.
	 */
	static @Nullable Text pick(String kind, Origin from, Random r, @Nullable ServerPlayer finder) {
		Data d = data();
		Set<String> have = finder == null ? null : partsOf(finder);
		double roll = r.nextDouble();
		Text t = null;
		if (roll < 0.14) t = piece(kind, from, d, r, have);
		if (t == null && roll < 0.28) t = family(kind, from, d, r);
		if (t == null) t = line(kind, from, d, r);
		if (t == null) t = piece(kind, from, d, r, have);
		if (t == null) t = family(kind, from, d, r);
		if (t != null) {
			d.used.add(t.id());
			STORE.markDirty();
		}
		return t;
	}

	private static @Nullable Text line(String kind, Origin from, Data d, Random r) {
		List<Text> pool = new ArrayList<>();
		List<Double> weights = new ArrayList<>();
		double total = 0;
		for (Text t : LINES) {
			if (d.used.contains(t.id()) || !fits(t.kinds(), kind) || !from.matches(t.where())) continue;
			double w = (t.where().isEmpty() ? 1 : 6) * (specific(t.kinds(), kind) ? 2.5 : 1);
			pool.add(t);
			weights.add(w);
			total += w;
		}
		return weighted(pool, weights, total, r);
	}

	/** The story parts ("a:story:part") a player has: held once, or handed to them. */
	static Set<String> partsOf(ServerPlayer p) {
		Seen seen = SEEN.get(p);
		Set<String> out = new HashSet<>();
		for (var e : seen.parts.entrySet()) for (int part : e.getValue()) out.add("a:" + e.getKey() + ":" + part);
		for (Find f : seen.finds) if (f.id != null && f.id.startsWith("a:")) out.add(f.id);
		return out;
	}

	/** May this part be handed out: without a finder once per server, with one once per finder. */
	static boolean allowed(Text t, Data d, @Nullable Set<String> have) {
		return have == null ? !d.used.contains(t.id()) : !have.contains(t.id());
	}

	private static @Nullable Text piece(String kind, Origin from, Data d, Random r, @Nullable Set<String> have) {
		// Stories that already started on this server come first: the parts should find each other; the finder's own
		// stories even more
		Set<String> started = new HashSet<>(), mine = new HashSet<>();
		for (String u : d.used) if (u.startsWith("a:")) started.add(u.substring(2, u.lastIndexOf(':')));
		if (have != null) for (String u : have) mine.add(u.substring(2, u.lastIndexOf(':')));
		List<Text> pool = new ArrayList<>();
		List<Double> weights = new ArrayList<>();
		double total = 0;
		for (Text t : PIECES) {
			if (!allowed(t, d, have) || !fits(t.kinds(), kind) || !from.matches(t.where())) continue;
			double w = (mine.contains(t.story()) ? 6 : started.contains(t.story()) ? 3 : 1) * (t.where().isEmpty() ? 1 : 4);
			pool.add(t);
			weights.add(w);
			total += w;
		}
		return weighted(pool, weights, total, r);
	}

	private static @Nullable Text family(String kind, Origin from, Data d, Random r) {
		List<Family> fits = new ArrayList<>();
		for (Family f : FAMILIES) if (fits(f.kinds(), kind) && from.matches(f.where())) fits.add(f);
		for (int attempt = 0; attempt < 24 && !fits.isEmpty(); attempt++) {
			Family f = fits.get(r.nextInt(fits.size()));
			StringBuilder id = new StringBuilder("f:").append(f.id());
			String text = f.form();
			for (int i = 0; i < f.slots().size(); i++) {
				List<String> o = f.options().get(i);
				int pick = r.nextInt(o.size());
				id.append(':').append(pick);
				text = text.replace("{" + f.slots().get(i) + "}", o.get(pick));
			}
			if (!d.used.contains(id.toString())) return new Text(id.toString(), f.kinds(), f.where(), text, null, null, null, 0, 0);
		}
		return null;
	}

	/** The text with this id: a line, a story part, or a family's combination made again. */
	static @Nullable Text byId(String id) {
		Text t = BY_ID.get(id);
		if (t != null || !id.startsWith("f:")) return t;
		String[] p = id.split(":");
		for (Family f : FAMILIES) {
			if (!f.id().equals(p[1]) || p.length != 2 + f.slots().size()) continue;
			String text = f.form();
			try {
				for (int i = 0; i < f.slots().size(); i++) {
					List<String> o = f.options().get(i);
					int pick = Integer.parseInt(p[2 + i]);
					if (pick < 0 || pick >= o.size()) return null;
					text = text.replace("{" + f.slots().get(i) + "}", o.get(pick));
				}
			} catch (NumberFormatException e) {
				return null;
			}
			return new Text(id, f.kinds(), f.where(), text, null, null, null, 0, 0);
		}
		return null;
	}

	private static @Nullable Text weighted(List<Text> pool, List<Double> weights, double total, Random r) {
		if (pool.isEmpty()) return null;
		double x = r.nextDouble() * total;
		for (int i = 0; i < pool.size(); i++) {
			x -= weights.get(i);
			if (x <= 0) return pool.get(i);
		}
		return pool.get(pool.size() - 1);
	}

	// ---------------------------------------------------------------- where from

	private static final Map<String, String[]> PLACES = new LinkedHashMap<>();

	static {
		// loot table path (start) → place tag, how the tooltip says it
		place("chests/bastion", "bastion", "in a bastion");
		place("chests/nether_bridge", "fortress", "in a Nether fortress");
		place("chests/ruined_portal", "ruined_portal", "by a ruined portal");
		place("chests/end_city", "end_city", "in an End city");
		place("chests/ancient_city", "ancient_city", "in an ancient city");
		place("chests/stronghold", "stronghold", "in a stronghold");
		place("chests/woodland_mansion", "mansion", "in a woodland mansion");
		place("chests/pillager_outpost", "outpost", "in a pillager outpost");
		place("chests/shipwreck", "shipwreck", "in a shipwreck");
		place("chests/underwater_ruin", "shipwreck", "in an ocean ruin");
		place("chests/buried_treasure", "buried", "in buried treasure");
		place("chests/desert_pyramid", "desert", "in a desert pyramid");
		place("chests/jungle_temple", "jungle", "in a jungle temple");
		place("chests/igloo", "igloo", "under an igloo");
		place("chests/abandoned_mineshaft", "mineshaft", "in a mineshaft");
		place("chests/simple_dungeon", "dungeon", "in a dungeon");
		place("chests/trial_chambers", "trial", "in a trial chamber");
		place("chests/village", "village", "in a village");
		place("chests/spawn_bonus_chest", "village", "in the bonus chest");
		place("gameplay/fishing", "fishing", "Fished up");
		place("gameplay/piglin_bartering", "barter", "Bartered from a piglin");
		place("archaeology", "archaeology", "Brushed out of the ground");
		place("equipment", "trial", "From a trial chamber");
	}

	private static void place(String prefix, String tag, String label) {
		PLACES.put(prefix, new String[] {tag, label});
	}

	static String realm(Level level) {
		ResourceKey<Level> dim = level.dimension();
		if (dim == Level.NETHER) return "nether";
		if (dim == Level.END) return "end";
		return "overworld";
	}

	/** Where loot from this table, made in this level, comes from (null: not loot we tell stories about). */
	static @Nullable Origin origin(String table, Level level) {
		for (Map.Entry<String, String[]> e : PLACES.entrySet()) {
			if (table.startsWith(e.getKey())) return new Origin(realm(level), e.getValue()[0], e.getValue()[1]);
		}
		if (table.startsWith("chests/")) return new Origin(realm(level), "", "in a chest");
		return null;
	}

	// ---------------------------------------------------------------- marking loot, and stories when it's taken

	/**
	 * Loot was made: everything in it that gets a story gets it now when a player made it happen (opened the chest,
	 * fished, brushed, opened the vault), so it's there to read in the chest already. Without a player (a piglin's
	 * barter, a hopper) it remembers where it came from and gets its story when a player takes it.
	 */
	static void onLoot(String table, LootContext context, List<ItemStack> drops) {
		if (!ENABLED.get()) return;
		Origin from = origin(table, context.getLevel());
		if (from == null) return;
		ServerPlayer finder = finder(context);
		for (ItemStack s : drops) {
			if (kindOf(s) == null || has(s, STAMP)) continue;
			pend(s, from);
			if (finder != null) stamp(finder, s);
		}
	}

	/** Who made this loot happen: the player who opened or brushed, or whose hook it was. */
	static @Nullable ServerPlayer finder(LootContext context) {
		var e = context.getOptional(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY);
		if (e instanceof ServerPlayer p && !(p instanceof net.fabricmc.fabric.api.entity.FakePlayer)) return p;
		if (e instanceof net.minecraft.world.entity.projectile.FishingHook hook && hook.getPlayerOwner() instanceof ServerPlayer p) return p;
		var killer = context.getOptional(net.minecraft.world.level.storage.loot.parameters.LootContextParams.LAST_DAMAGE_PLAYER);
		return killer instanceof ServerPlayer p && !(p instanceof net.fabricmc.fabric.api.entity.FakePlayer) ? p : null;
	}

	/** A monster dies, killed by a player: what it might drop gets its story now, as found by that player. */
	static void onDeath(LivingEntity entity, @Nullable ServerPlayer killer) {
		if (!ENABLED.get() || !(entity instanceof Mob mob) || entity instanceof Player) return;
		String mobName = entity.getType().getDescription().getString().toLowerCase(Locale.ROOT);
		String article = "aeiou".indexOf(mobName.isEmpty() ? 'x' : mobName.charAt(0)) >= 0 ? "an " : "a ";
		Origin from = new Origin(realm(entity.level()), "mob", "Dropped by " + article + mobName);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack s = mob.getItemBySlot(slot);
			if (kindOf(s) == null || has(s, STAMP) || has(s, PENDING)) continue;
			pend(s, from);
			if (killer != null && !(killer instanceof net.fabricmc.fabric.api.entity.FakePlayer)) stamp(killer, s);
		}
	}

	static boolean has(ItemStack s, String key) {
		CustomData cd = s.get(DataComponents.CUSTOM_DATA);
		return cd != null && cd.copyTag().contains(key);
	}

	private static void pend(ItemStack s, Origin from) {
		CompoundTag t = new CompoundTag();
		t.putString("realm", from.realm());
		t.putString("place", from.place());
		t.putString("label", from.label());
		CustomData.update(DataComponents.CUSTOM_DATA, s, root -> root.put(PENDING, t));
	}

	/** Every two seconds: what a player took and still waits for its story gets one now. */
	static void tick(MinecraftServer server) {
		if (!ENABLED.get()) return;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p instanceof net.fabricmc.fabric.api.entity.FakePlayer) continue;
			var inv = p.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				if (s.isEmpty()) continue;
				if (has(s, PENDING)) stamp(p, s);
				String piece = pieceOf(s);
				if (piece != null) saw(p, piece, s);
			}
		}
	}

	/** Gives a waiting item its story: the text, maybe a name, and where, when and by whom it was found. */
	static void stamp(ServerPlayer p, ItemStack s) {
		CompoundTag pending = s.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty(PENDING);
		CustomData.update(DataComponents.CUSTOM_DATA, s, root -> root.remove(PENDING));
		String kind = kindOf(s);
		if (kind == null || has(s, STAMP)) return;
		Origin from = new Origin(pending.getStringOr("realm", "overworld"), pending.getStringOr("place", ""), pending.getStringOr("label", "Found"));
		Random r = new Random(p.getRandom().nextLong() ^ System.nanoTime());
		Text t = pick(kind, from, r, p);
		if (t == null) return;
		int day = Chronicle.day(p.level().getServer());
		write(s, t, from.label(), day, p.getName().getString(), p.level().registryAccess());
		remember(p, s, t, from.label(), day);
		if (t.story() != null) nl.libreria.core.Tips.once(p, TIP);
	}

	/** Into the finder's lore book. */
	static void remember(ServerPlayer p, ItemStack s, Text t, String label, int day) {
		Seen seen = SEEN.get(p);
		Find f = new Find();
		f.id = t.id();
		f.item = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
		f.name = t.name() == null ? "" : t.name();
		f.where = where(label);
		f.day = day;
		seen.finds.add(f);
		while (seen.finds.size() > MAX_FINDS) seen.finds.remove(0);
		SEEN.markDirty(p);
	}

	static void write(ItemStack s, Text t, String label, int day, String by, net.minecraft.core.RegistryAccess access) {
		String kind = kindOf(s);
		String noun = kind == null ? "thing" : noun(kind, s);
		List<Component> lines = new ArrayList<>();
		if (t.story() != null) {
			lines.add(Lore.line("✦ " + t.title() + " · part " + t.part() + " of " + t.parts(), HEAD, false));
		}
		for (String l : Lore.wrap(t.text().replace("{item}", noun), 40)) lines.add(Lore.line(l, TEXT, true));
		String where = where(label);
		lines.add(Lore.line(where + " · day " + day + " · " + by, WHERE, false));
		Lore.set(s, Lore.FOUND, lines);
		CompoundTag stamp = new CompoundTag();
		stamp.putString("id", t.id());
		stamp.putInt("day", day);
		stamp.putString("by", by);
		stamp.putString("where", where);
		if (t.story() != null) {
			stamp.putString("story", t.story());
			stamp.putInt("part", t.part());
		}
		CustomData.update(DataComponents.CUSTOM_DATA, s, root -> root.put(STAMP, stamp));
		if (t.name() != null && s.get(DataComponents.CUSTOM_NAME) == null) {
			TextColor colour = TextColor.fromLegacyFormat(Weapons.quality(s, access).color);
			s.set(DataComponents.CUSTOM_NAME, Component.literal(t.name()).withStyle(st -> st.withItalic(false).withColor(colour)));
		}
	}

	/** A player has (had) this story part ("lantern:3") in their inventory. */
	static void saw(ServerPlayer p, String piece, ItemStack stack) {
		int colon = piece.lastIndexOf(':');
		String story = piece.substring(0, colon);
		int part = Integer.parseInt(piece.substring(colon + 1));
		Seen s = SEEN.get(p);
		if (s.parts.computeIfAbsent(story, k -> new HashSet<>()).add(part)) {
			CompoundTag st = stack.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty(STAMP);
			s.partInfo.put(piece, st.getStringOr("where", "Found") + "|" + st.getIntOr("day", 0) + "|" + st.getStringOr("by", ""));
			SEEN.markDirty(p);
		}
	}

	static void provide() {
		nl.libreria.core.Services.provideStories(new nl.libreria.core.Services.Stories() {
			@Override
			public Map<String, String> titles() {
				Map<String, String> out = new LinkedHashMap<>();
				for (var e : STORIES.entrySet()) out.put(e.getKey(), e.getValue().title());
				return out;
			}

			@Override
			public int parts(String story) {
				Text last = STORIES.get(story);
				return last == null ? 0 : last.parts();
			}

			@Override
			public int held(ServerPlayer player, String story) {
				Set<Integer> s = SEEN.get(player).parts.get(story);
				return s == null ? 0 : s.size();
			}

			@Override
			public int pieces(ServerPlayer player) {
				int n = 0;
				for (Set<Integer> s : SEEN.get(player).parts.values()) n += s.size();
				return n;
			}
		});
	}

	/** "Found in a bastion" for "in a bastion"; "Fished up" stays. */
	static String where(String label) {
		return label.startsWith("in ") || label.startsWith("by ") || label.startsWith("under ") ? "Found " + label : label;
	}

	/** The story part an item carries ("lantern:3"), or null. */
	static @Nullable String pieceOf(ItemStack s) {
		CustomData cd = s.get(DataComponents.CUSTOM_DATA);
		if (cd == null) return null;
		CompoundTag t = cd.copyTag().getCompoundOrEmpty(STAMP);
		String story = t.getStringOr("story", "");
		return story.isEmpty() ? null : story + ":" + t.getIntOr("part", 0);
	}

	static void init() {
		load();
		provide();
		net.fabricmc.fabric.api.loot.v3.LootTableEvents.MODIFY_DROPS.register((entry, context, drops) -> {
			try {
				entry.unwrapKey().ifPresent(key -> onLoot(key.identifier().getPath(), context, drops));
			} catch (RuntimeException e) {
				Libreria.LOG.debug("Found: loot failed", e);
			}
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			try {
				if (source.getEntity() instanceof ServerPlayer killer) onDeath(entity, killer);
			} catch (RuntimeException e) {
				Libreria.LOG.debug("Found: death failed", e);
			}
			return true;
		});
		LoreBook.init();
	}

	/** For the self-test and the screenshots: every story's parts, how many lines and families there are. */
	static String summary() {
		return LINES.size() + " texts, " + PIECES.size() + " story parts, " + STORIES.size() + " stories, " + FAMILIES.size() + " families";
	}

	static Map<String, Integer> partsPerStory() {
		Map<String, Integer> out = new HashMap<>();
		for (Text t : PIECES) out.merge(t.story(), 1, Integer::sum);
		return out;
	}
}
