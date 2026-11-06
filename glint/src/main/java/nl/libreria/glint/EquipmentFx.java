package nl.libreria.glint;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.phys.AABB;
import nl.libreria.core.Pack;
import org.jetbrains.annotations.Nullable;

/**
 * Worn armour's effects, for whoever looks: a piece with an enchantment glows with it (embers for Fire Protection,
 * runes for Protection, rime for Frost Walker…, stronger per level), and a trimmed piece gets a seam of light round its
 * trim in that colour instead. The resource pack has an equipment asset per material, effect and level (and per trim
 * pattern); this picks it per piece.
 *
 * <p>Nothing about the item changes: only the copy of it in the equipment packet someone else receives (the armour of
 * a player, mob or armour stand near them) says to draw it with our asset. That only happens for a viewer who has the
 * pack loaded, has effects on other players on and that effect not switched off; for everyone else the packet is
 * vanilla. When any of that changes for a viewer, the armour around them is sent to them again.
 */
public final class EquipmentFx {
	/** Enchantment → effect family, most striking first: a piece shows the first one it has. */
	private static final String[][] ORDER = {
			{"fire_protection", "fireguard"}, {"blast_protection", "blast"}, {"projectile_protection", "arrowguard"},
			{"protection", "protection"}, {"frost_walker", "frost"}, {"soul_speed", "soul"}, {"respiration", "water"},
			{"aqua_affinity", "water"}, {"depth_strider", "water"}, {"swift_sneak", "sculk"}, {"thorns", "thorns"},
			{"feather_falling", "feather"}, {"mending", "mending"}, {"binding_curse", "curse"}, {"vanishing_curse", "curse"}};
	/** The armour materials the pack has layers for (vanilla equipment assets). */
	private static final Set<String> MATERIALS = Set.of("leather", "chainmail", "copper", "iron", "gold", "diamond", "netherite",
			"turtle_scute");
	/** The trim patterns the pack has seams for (scripts/glint/trims_fx.py). */
	private static final Set<String> PATTERNS = Set.of("bolt", "coast", "dune", "eye", "flow", "host", "raiser", "rib", "sentry",
			"shaper", "silence", "snout", "spire", "tide", "vex", "ward", "wayfinder", "wild");
	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** What one viewer sees: effects on at all, and the families they switched off. Read while packets are written. */
	record Prefs(boolean on, Set<String> off) {
	}

	private static final Prefs OFF = new Prefs(false, Set.of());
	private static final Map<UUID, Prefs> VIEWERS = new ConcurrentHashMap<>();
	/** (equippable, asset) → the equippable with that asset. */
	private static final Map<Pair<Equippable, String>, Equippable> CACHE = new ConcurrentHashMap<>();
	private static volatile @Nullable RegistryOps<JsonElement> ops;

	private EquipmentFx() {
	}

	static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> ops = RegistryOps.create(JsonOps.INSTANCE, s.registryAccess()));
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			ops = null;
			VIEWERS.clear();
			CACHE.clear();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> VIEWERS.remove(handler.player.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 == 0) refreshAll(server);
		});
		// Before the pack goes, the armour around you goes back to vanilla (without the pack our assets don't exist)
		Pack.beforePop(p -> {
			VIEWERS.put(p.getUUID(), OFF);
			resend(p);
		});
	}

	private static void refreshAll(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) refresh(p);
	}

	/** Takes this viewer's settings again; when they changed, the armour around them is sent again. */
	static void refresh(ServerPlayer p) {
		boolean on = Pack.has(p) && GlintMod.AURA.get(p);
		Prefs now = on ? new Prefs(true, Set.copyOf(new HashSet<>(GlintMod.STORE.get(p).off))) : OFF;
		Prefs before = VIEWERS.put(p.getUUID(), now);
		if (before == null ? now.on() : !before.equals(now)) resend(p);
	}

	/** Sends the armour of everything near this viewer again (as they should see it now). */
	static void resend(ServerPlayer viewer) {
		AABB box = viewer.getBoundingBox().inflate(128);
		for (LivingEntity e : viewer.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != viewer)) {
			List<Pair<EquipmentSlot, ItemStack>> slots = new ArrayList<>();
			for (EquipmentSlot s : ARMOUR) {
				ItemStack st = e.getItemBySlot(s);
				if (!st.isEmpty()) slots.add(Pair.of(s, st.copy()));
			}
			if (!slots.isEmpty()) viewer.connection.send(new ClientboundSetEquipmentPacket(e.getId(), slots));
		}
	}

	/**
	 * The piece as the player this packet is being written for should see it (the stack itself, or a copy with our
	 * equipment asset). Never throws: a packet that fails to be written would disconnect them.
	 */
	public static ItemStack forViewer(ItemStack stack) {
		try {
			String asset = assetFor(stack);
			if (asset == null) return stack;
			Equippable eq = stack.get(DataComponents.EQUIPPABLE);
			Equippable mod = CACHE.computeIfAbsent(Pair.of(eq, asset), k -> withAsset(k.getFirst(), k.getSecond()));
			if (mod == null) return stack;
			ItemStack copy = stack.copy();
			copy.set(DataComponents.EQUIPPABLE, mod);
			return copy;
		} catch (Throwable t) {
			return stack;
		}
	}

	private static @Nullable String assetFor(ItemStack stack) {
		if (stack.isEmpty() || ops == null) return null;
		PacketContext ctx = PacketContext.get();
		if (ctx == null) return null;
		var profile = ctx.get(PacketContext.GAME_PROFILE);
		if (profile == null) return null;
		Prefs prefs = VIEWERS.getOrDefault(profile.id(), OFF);
		if (!prefs.on()) return null;
		Equippable eq = stack.get(DataComponents.EQUIPPABLE);
		if (eq == null || eq.assetId().isEmpty()) return null;
		EquipmentSlot slot = eq.slot();
		if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET) return null;
		var key = eq.assetId().get().identifier();
		if (!key.getNamespace().equals("minecraft") || !MATERIALS.contains(key.getPath())) return null;
		ItemEnchantments ench = stack.get(DataComponents.ENCHANTMENTS);
		if (ench == null || ench.isEmpty()) return null;
		String family = null;
		int tier = 0;
		for (String[] f : ORDER) {
			if (prefs.off().contains(f[1])) continue;
			int level = levelOf(ench, f[0]);
			if (level <= 0) continue;
			family = f[1];
			tier = Aura.strength(level, maxOf(ench, f[0]));
			break;
		}
		if (family == null) return null;
		ArmorTrim trim = stack.get(DataComponents.TRIM);
		if (trim != null) {
			String pattern = trim.pattern().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
			if (PATTERNS.contains(pattern)) return "libreria:fxtrim_" + key.getPath() + "_" + family + "_" + pattern;
		}
		return "libreria:fx_" + key.getPath() + "_" + family + "_" + tier;
	}

	private static int levelOf(ItemEnchantments ench, String id) {
		for (var en : ench.entrySet()) {
			Holder<Enchantment> h = en.getKey();
			if (h.unwrapKey().map(k -> k.identifier().getPath().equals(id) && k.identifier().getNamespace().equals("minecraft")).orElse(false))
				return en.getIntValue();
		}
		return 0;
	}

	private static int maxOf(ItemEnchantments ench, String id) {
		for (var en : ench.entrySet()) {
			Holder<Enchantment> h = en.getKey();
			if (h.unwrapKey().map(k -> k.identifier().getPath().equals(id)).orElse(false)) return h.value().getMaxLevel();
		}
		return 1;
	}

	/** The same equippable with another equipment asset (through its codec, so every other field stays as it was). */
	private static @Nullable Equippable withAsset(Equippable eq, String asset) {
		var o = ops;
		if (o == null) return null;
		JsonElement json = Equippable.CODEC.encodeStart(o, eq).getOrThrow();
		json.getAsJsonObject().addProperty("asset_id", asset);
		return Equippable.CODEC.parse(o, json).getOrThrow();
	}
}
