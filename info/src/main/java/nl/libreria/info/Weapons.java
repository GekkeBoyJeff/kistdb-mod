package nl.libreria.info;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import nl.libreria.core.Chronicle;
import nl.libreria.core.Libreria;
import nl.libreria.core.ServerSetting;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Toast;
import org.jetbrains.annotations.Nullable;

/**
 * Weapons with a story. A sword, axe, bow, crossbow, trident, mace or spear keeps count of what it felled, in its
 * tooltip. After enough foes (or one boss) it earns a name of its own, never given twice (Duskfang, Winter's Mercy…),
 * unless its owner already named it: a name you gave stays. The colour of a named weapon's name shows how good its
 * enchantments are, from white (none) through green, aqua and purple to gold (everything it can have, maxed).
 */
final class Weapons {
	static final ServerSetting.Toggle ENABLED = InfoMod.MODULE.serverToggle("weapon_stories", "Weapons with a story", Items.IRON_SWORD,
			true, "Weapons count what they felled; after", "enough foes (or one boss) they earn a", "name of their own. A name a player",
			"gave a weapon is never changed.");
	static final ServerSetting.Number KILLS = InfoMod.MODULE.serverNumber("weapon_name_kills", "Foes for a weapon name", Items.NAME_TAG,
			150, 10, 2000, 10, "foes", "How many hostile mobs (or players) a", "weapon has to fell to earn a name.",
			"Slaying a boss earns one straight away.");

	/** The weapon's story, in its custom data. */
	static final String KEY = "libreria_story";
	/** Bosses, with how the tooltip names them. */
	static final Map<String, String> BOSSES = Map.of("ender_dragon", "the Ender Dragon", "wither", "the Wither", "warden",
			"a Warden", "elder_guardian", "an Elder Guardian");

	/** What kind of weapon: decides the words its name is made of. */
	enum Kind {
		SWORD("fang", "brand", "edge", "bane", "reaver", "song", "light", "blade", "thorn", "wrath", "bite", "splinter", "render",
				"whisper", "fall", "shard"),
		AXE("cleaver", "splitter", "hewer", "maw", "bite", "render", "sunder", "cleft", "fell", "gnaw"),
		BOW("string", "whisper", "flight", "reach", "song", "wing", "eye", "quill", "call", "strike", "dart", "gaze"),
		TRIDENT("tide", "surge", "crest", "call", "fang", "spire", "wake", "deep"),
		MACE("fall", "maul", "breaker", "crusher", "quake", "knell", "toll", "drop"),
		SPEAR("point", "lance", "spire", "thorn", "reach", "tusk", "needle");

		final String[] endings;

		Kind(String... endings) {
			this.endings = endings;
		}
	}

	private static final String[] STEMS = {"Dusk", "Dawn", "Ash", "Ember", "Frost", "Storm", "Grim", "Night", "Star", "Moon",
			"Sun", "Iron", "Blood", "Thorn", "Wyrm", "Raven", "Shadow", "Gale", "Rune", "Bone", "Dread", "Wraith", "Hollow", "Oath",
			"Wolf", "Hawk", "Flame", "Thunder", "Winter", "Gloom", "Void", "Glimmer", "Bright", "Silver", "Gold", "Ruin", "Bitter",
			"Cinder", "Hallow", "Sorrow", "Vigil", "Echo", "Tide", "Gloam", "Fell", "Wild", "Stone", "Sky", "Rime", "Hex", "Crow",
			"Briar", "Venom", "Pale", "Sable", "Scarlet", "Brine", "Spite", "Mourn", "Quiet", "Last", "Widow", "Orphan", "Kestrel",
			"Lantern", "Vesper", "Morrow", "Hearth", "Comet", "Tempest", "Shiver", "Grief", "Rook", "Marrow", "Rust", "Gilt"};
	private static final String[] OWNERS = {"Winter", "Raven", "Widow", "Dragon", "Wolf", "Storm", "Night", "Moon", "Sun", "King",
			"Queen", "Hunter", "Wanderer", "Ghost", "Sailor", "Giant", "Serpent", "Shepherd", "Exile", "Stranger", "Witch", "Warden",
			"Crow", "Fox", "Saint", "Tyrant", "Orphan", "Heron", "Smith", "Pilgrim"};
	private static final String[] NOUNS = {"Mercy", "Kiss", "Edge", "Whisper", "Wrath", "Lament", "Promise", "Vow", "Grief", "Fury",
			"Reach", "Toll", "Bite", "Song", "Oath", "Gift", "Due", "Answer", "Last Word", "Farewell", "Judgement", "Embrace"};
	/** Famous names from books and games: never these. */
	private static final Set<String> TAKEN_ELSEWHERE = Set.of("ashbringer", "stormbringer", "oathkeeper", "frostmourne",
			"doomhammer", "soulreaver", "lightbringer", "dawnbringer", "shadowfang", "sunfire", "widow's kiss", "dragon's bane",
			"thunderfury", "starfall", "nightfall", "moonfall", "sting", "orcrist");

	private static final Map<Item, Integer> POTENTIAL = new HashMap<>();

	private Weapons() {
	}

	static void init() {
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			try {
				if (ENABLED.get()) onKill(entity, source);
			} catch (RuntimeException e) {
				Libreria.LOG.warn("Weapon story failed", e);
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(s -> POTENTIAL.clear());
	}

	/** Every two seconds: names of weapons follow their enchantments (an anvil or a grindstone changes them). */
	static void tick(MinecraftServer server) {
		if (!ENABLED.get()) return;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			var inv = p.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack st = inv.getItem(i);
				if (!st.isEmpty() && kind(st) != null) refresh(st, server.registryAccess());
			}
		}
	}

	static @Nullable Kind kind(ItemStack stack) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (id.endsWith("_sword")) return Kind.SWORD;
		if (id.endsWith("_axe")) return Kind.AXE;
		if (id.endsWith("_spear")) return Kind.SPEAR;
		return switch (id) {
			case "bow", "crossbow" -> Kind.BOW;
			case "trident" -> Kind.TRIDENT;
			case "mace" -> Kind.MACE;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- counting

	private static void onKill(LivingEntity victim, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer killer) || victim == killer) return;
		if (!(victim instanceof Enemy) && !(victim instanceof Player)) return;
		ItemStack weapon = source.getWeaponItem();
		if (weapon == null || weapon.isEmpty() || kind(weapon) == null) return;
		Entity direct = source.getDirectEntity();
		// Melee: the stack in your hand. A thrown trident: the trident itself, that comes back. An arrow: find the bow.
		ItemStack real = direct == killer || direct instanceof ThrownTrident ? weapon : findBow(killer, weapon);
		if (real == null) return;
		String what = victim instanceof Player ? "player" : BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).getPath();
		count(killer, real, what);
	}

	/** The bow or crossbow an arrow came from: the copy the arrow carries tells which. */
	private static @Nullable ItemStack findBow(ServerPlayer p, ItemStack copy) {
		String id = story(copy).getStringOr("id", "");
		List<ItemStack> candidates = new ArrayList<>();
		candidates.add(p.getMainHandItem());
		candidates.add(p.getOffhandItem());
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) candidates.add(inv.getItem(i));
		for (ItemStack st : candidates) {
			if (st.isEmpty() || !st.is(copy.getItem())) continue;
			if (story(st).getStringOr("id", "").equals(id)) return st;
		}
		return null;
	}

	/** Adds one felled {@code what} to the weapon's story; it may earn a name. */
	static void count(ServerPlayer killer, ItemStack stack, String what) {
		Kind kind = kind(stack);
		if (kind == null) return;
		CompoundTag st = story(stack);
		if (st.getStringOr("id", "").isEmpty()) st.putString("id", UUID.randomUUID().toString());
		CompoundTag kills = st.getCompoundOrEmpty("kills");
		kills.putInt(what, kills.getIntOr(what, 0) + 1);
		st.put("kills", kills);
		int total = st.getIntOr("total", 0) + 1;
		st.putInt("total", total);
		boolean boss = BOSSES.containsKey(what);
		if (!st.getBooleanOr("legend", false) && (total >= KILLS.get() || boss)) earn(killer, stack, st, kind, boss ? what : null);
		save(stack, st);
		refresh(stack, killer.level().registryAccess());
	}

	/** The moment a weapon becomes a legend: a name (unless it has one), a toast, the chronicle. */
	private static void earn(ServerPlayer p, ItemStack stack, CompoundTag st, Kind kind, @Nullable String boss) {
		MinecraftServer server = p.level().getServer();
		st.putBoolean("legend", true);
		st.putString("by", p.getName().getString());
		st.putInt("day", Chronicle.day(server));
		String item = stack.getItemName().getString();
		String how = boss != null ? "after slaying " + BOSSES.get(boss) : "after " + st.getIntOr("total", 0) + " foes";
		Component own = stack.get(DataComponents.CUSTOM_NAME);
		if (own == null) {
			UUID id = UUID.fromString(st.getStringOr("id", UUID.randomUUID().toString()));
			String name = Names.weapon(id, kind);
			st.putString("name", name);
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withItalic(false)));
			Toast.show(p, stack.copy(), Toast.GOOD, Ui.text("Your " + item + " earned a name", ChatFormatting.GOLD),
					Ui.text(name + " · " + how, ChatFormatting.WHITE));
			Chronicle.write(p.getUUID(), "", Chronicle.Kind.LEGEND, p.getName().getString() + "'s " + item + " earned a name " + how + ": " + name, true);
		} else {
			String name = own.getString();
			Toast.show(p, stack.copy(), Toast.GOOD, Ui.text(name + " became a legend", ChatFormatting.GOLD), Ui.text(how, ChatFormatting.WHITE));
			Chronicle.write(p.getUUID(), "", Chronicle.Kind.LEGEND, p.getName().getString() + "'s " + item + " \"" + name + "\" became a legend " + how, true);
		}
		Ui.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.1f);
	}

	static CompoundTag story(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompoundOrEmpty(KEY);
	}

	private static void save(ItemStack stack, CompoundTag st) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(KEY, st));
	}

	// ---------------------------------------------------------------- how it looks

	/** Brings the tooltip lines and the colour of the name up to date (does nothing when they already are). */
	static void refresh(ItemStack stack, RegistryAccess access) {
		CompoundTag st = story(stack);
		Component own = stack.get(DataComponents.CUSTOM_NAME);
		if (own != null) {
			TextColor want = TextColor.fromLegacyFormat(quality(stack, access).color);
			if (!want.equals(own.getStyle().getColor())) stack.set(DataComponents.CUSTOM_NAME, own.copy().withStyle(s -> s.withColor(want)));
		}
		if (st.isEmpty()) return;
		List<Component> ours = lines(stack, st, access);
		ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		int had = Math.min(st.getIntOr("lore", 0), lore.lines().size());
		List<Component> all = new ArrayList<>(lore.lines().subList(0, lore.lines().size() - had));
		all.addAll(ours);
		if (!all.equals(lore.lines())) {
			stack.set(DataComponents.LORE, new ItemLore(all));
			if (had != ours.size()) {
				st.putInt("lore", ours.size());
				save(stack, st);
			}
		}
	}

	/** The tooltip: what it felled, the bosses, and how it got its name. */
	static List<Component> lines(ItemStack stack, CompoundTag st, RegistryAccess access) {
		List<Component> out = new ArrayList<>();
		int total = st.getIntOr("total", 0);
		Quality q = quality(stack, access);
		out.add(line("Foes felled ", ChatFormatting.GRAY).append(line(Integer.toString(total), ChatFormatting.WHITE))
				.append(line("  ·  ", ChatFormatting.DARK_GRAY)).append(line(q.label, q.color)));
		CompoundTag kills = st.getCompoundOrEmpty("kills");
		List<Map.Entry<String, Integer>> list = new ArrayList<>();
		for (String k : kills.keySet()) if (!BOSSES.containsKey(k)) list.add(Map.entry(k, kills.getIntOr(k, 0)));
		list.sort((a, b) -> b.getValue() - a.getValue());
		if (!list.isEmpty()) {
			MutableComponent most = Component.empty();
			for (int i = 0; i < Math.min(3, list.size()); i++) {
				if (i > 0) most.append(line(" · ", ChatFormatting.DARK_GRAY));
				most.append(line(mobName(list.get(i).getKey()) + " " + list.get(i).getValue(), ChatFormatting.GRAY));
			}
			out.add(most);
		}
		List<String> bosses = new ArrayList<>();
		for (String b : BOSSES.keySet()) {
			int n = kills.getIntOr(b, 0);
			if (n > 0) bosses.add(BOSSES.get(b) + (n > 1 ? " ×" + n : ""));
		}
		if (!bosses.isEmpty()) out.add(line("Slew " + String.join(", ", bosses), ChatFormatting.LIGHT_PURPLE));
		if (st.getBooleanOr("legend", false)) {
			String by = st.getStringOr("by", "");
			int day = st.getIntOr("day", 0);
			out.add(line((st.contains("name") ? "Earned its name" : "A legend") + (day > 0 ? " on day " + day : "")
					+ (by.isEmpty() ? "" : ", in " + by + "'s hands"), ChatFormatting.GOLD));
		} else if (stack.get(DataComponents.CUSTOM_NAME) == null) {
			int left = Math.max(1, KILLS.get() - total);
			out.add(line(left + " more to earn a name", ChatFormatting.DARK_GRAY));
		}
		return out;
	}

	private static MutableComponent line(String s, ChatFormatting color) {
		return Ui.text(s, color);
	}

	private static String mobName(String id) {
		if (id.equals("player")) return "Players";
		var type = BuiltInRegistries.ENTITY_TYPE.getOptional(net.minecraft.resources.Identifier.withDefaultNamespace(id));
		return type.map(t -> t.getDescription().getString()).orElse(id);
	}

	// ---------------------------------------------------------------- quality

	/** How good a weapon's enchantments are. */
	enum Quality {
		PLAIN("Plain", ChatFormatting.WHITE), FINE("Fine", ChatFormatting.GREEN), RARE("Rare", ChatFormatting.AQUA),
		EPIC("Epic", ChatFormatting.LIGHT_PURPLE), LEGENDARY("Legendary", ChatFormatting.GOLD);

		final String label;
		final ChatFormatting color;

		Quality(String label, ChatFormatting color) {
			this.label = label;
			this.color = color;
		}
	}

	/**
	 * Each enchantment counts for how close it is to its highest level (Sharpness V = 1, Sharpness III = 0.6); a curse
	 * takes one off. That is compared with the most enchantments the weapon can carry at once (a sword: 7).
	 */
	static Quality quality(ItemStack stack, RegistryAccess access) {
		ItemEnchantments ench = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		if (ench.isEmpty()) return Quality.PLAIN;
		double score = 0;
		for (var e : ench.entrySet()) {
			Holder<Enchantment> h = e.getKey();
			if (h.is(EnchantmentTags.CURSE)) score -= 1;
			else score += Math.min(1.0, e.getIntValue() / (double) Math.max(1, h.value().getMaxLevel()));
		}
		double q = score / potential(stack, access);
		if (q <= 0) return Quality.PLAIN;
		if (q < 0.35) return Quality.FINE;
		if (q < 0.6) return Quality.RARE;
		if (q < 0.9) return Quality.EPIC;
		return Quality.LEGENDARY;
	}

	/** The most enchantments (no curses) this item can carry together. */
	static int potential(ItemStack stack, RegistryAccess access) {
		return POTENTIAL.computeIfAbsent(stack.getItem(), it -> {
			List<Holder<Enchantment>> fits = new ArrayList<>();
			access.lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(h -> {
				if (!h.is(EnchantmentTags.CURSE) && h.value().canEnchant(stack)) fits.add(h);
			});
			int n = Math.min(fits.size(), 16);
			int best = 0;
			// Small sets: try every combination (Riptide or Loyalty and Channeling…)
			for (int mask = 1; mask < (1 << n); mask++) {
				int size = Integer.bitCount(mask);
				if (size <= best) continue;
				boolean ok = true;
				for (int i = 0; i < n && ok; i++) {
					if ((mask & (1 << i)) == 0) continue;
					for (int j = i + 1; j < n && ok; j++) {
						if ((mask & (1 << j)) != 0 && !Enchantment.areCompatible(fits.get(i), fits.get(j))) ok = false;
					}
				}
				if (ok) best = size;
			}
			return Math.max(1, best);
		});
	}

	// ---------------------------------------------------------------- names

	/** A weapon name: "Duskfang", "Winter's Mercy" or "Grimbite". */
	static String name(Kind kind, Random r, int attempt) {
		for (int i = 0; i < 12; i++) {
			String name;
			int pattern = r.nextInt(100);
			if (pattern < 70) name = PlaceNames.join(r, STEMS[r.nextInt(STEMS.length)], kind.endings);
			else name = OWNERS[r.nextInt(OWNERS.length)] + "'s " + NOUNS[r.nextInt(NOUNS.length)];
			if (!TAKEN_ELSEWHERE.contains(name.toLowerCase(java.util.Locale.ROOT))) return name;
		}
		return STEMS[r.nextInt(STEMS.length)] + kind.endings[0];
	}
}
