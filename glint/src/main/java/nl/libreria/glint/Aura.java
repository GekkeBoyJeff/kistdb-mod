package nl.libreria.glint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;

/**
 * Particles around players with enchanted gear: flames by a Fire Aspect sword, snow by Frost Walker boots… Real
 * particles, so plain clients see them. Every viewer decides for themselves whether and which ones they see.
 *
 * <p>How it stays calm, whatever the combination:
 * <ul>
 * <li>Every item gives off <b>one</b> effect: its most striking enchantment (the order of families.json). A sword with
 * Sharpness, Fire Aspect, Looting and Mending just burns.</li>
 * <li>Four armour pieces that share an effect form a <b>set</b>: one slow orbit around you instead of four clouds.</li>
 * <li>Otherwise armour gives at most two effects; pieces with the same effect count once.</li>
 * <li>Never more than three sources per player; busy particles (sparks, lava, ink…) come half as often.</li>
 * <li>Your own armour is placed just behind your camera: invisible in first person, visible in third person (F5).
 * The weapon in your hand stays where it is, so you do see that.</li>
 * </ul>
 */
final class Aura {
	/** Family → particle. Families without one (Unbreaking, Mending) are too common: that would only get busy. */
	static final Map<String, ParticleOptions> PARTICLES = Map.ofEntries(
			Map.entry("fire", ParticleTypes.SMALL_FLAME), Map.entry("lightning", ParticleTypes.ELECTRIC_SPARK),
			Map.entry("frost", ParticleTypes.SNOWFLAKE), Map.entry("soul", ParticleTypes.SOUL_FIRE_FLAME),
			Map.entry("holy", ParticleTypes.END_ROD), Map.entry("poison", ParticleTypes.ITEM_SLIME),
			Map.entry("tide", ParticleTypes.DOLPHIN), Map.entry("water", ParticleTypes.SPLASH),
			Map.entry("sculk", ParticleTypes.SCULK_SOUL), Map.entry("blast", ParticleTypes.SMOKE),
			Map.entry("sweep", ParticleTypes.CLOUD), Map.entry("feather", ParticleTypes.WHITE_ASH),
			Map.entry("loyalty", ParticleTypes.GLOW), Map.entry("sharp", ParticleTypes.CRIT),
			Map.entry("impale", ParticleTypes.NAUTILUS), Map.entry("heavy", ParticleTypes.REVERSE_PORTAL),
			Map.entry("breach", ParticleTypes.LAVA), Map.entry("power", ParticleTypes.ENCHANTED_HIT),
			Map.entry("multishot", ParticleTypes.FIREWORK), Map.entry("haste", ParticleTypes.WAX_OFF),
			Map.entry("wind", ParticleTypes.SMALL_GUST), Map.entry("infinity", ParticleTypes.WITCH),
			Map.entry("thorns", ParticleTypes.DAMAGE_INDICATOR), Map.entry("fireguard", ParticleTypes.FLAME),
			Map.entry("arrowguard", ParticleTypes.CRIT), Map.entry("protection", ParticleTypes.ENCHANT),
			Map.entry("silk", ParticleTypes.CHERRY_LEAVES), Map.entry("curse", ParticleTypes.SQUID_INK),
			Map.entry("luck", ParticleTypes.HAPPY_VILLAGER), Map.entry("sea", ParticleTypes.BUBBLE_POP));

	/** Particles that are loud to look at (big, bright or fast): half as often. */
	static final Set<String> BUSY = Set.of("breach", "multishot", "thorns", "sweep", "curse", "blast", "water", "wind", "sea");

	/** How often {@link #tick} runs, in ticks. Every source fires every few runs. */
	static final int RUN = 5;
	/** Never more than this many sources per player. */
	static final int MAX_SOURCES = 3;
	/** Armour on yourself stays at least this far behind your camera (blocks). */
	static final double BEHIND = 0.35;

	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** Where (part of the body height) each armour piece's particle appears. */
	private static final double[] ARMOUR_HEIGHT = {0.95, 0.7, 0.4, 0.05};

	enum Kind {
		/** The item in your main hand: weapon, tool or enchanted book. */
		HAND,
		/** All four armour pieces share an effect: one orbit. */
		SET,
		/** One armour effect, at the height of the piece(s) that give it. */
		ARMOUR,
		/** The item in your off hand. */
		OFFHAND
	}

	/** One thing that gives off particles. {@code every}: fires once per this many runs. */
	record Source(Kind kind, String family, double height, int every) {
	}

	/**
	 * What a player carries, per slot: the effects of each item, most striking first. Empty lists for nothing.
	 * {@code armour} is head, chest, legs, feet.
	 */
	record Gear(List<String> hand, boolean handBook, List<String> offhand, boolean offBook, List<List<String>> armour) {
	}

	private Aura() {
	}

	// ---------------------------------------------------------------- what to show

	static Gear gear(ServerPlayer p) {
		List<List<String>> armour = new ArrayList<>();
		for (EquipmentSlot s : ARMOUR) armour.add(families(p.getItemBySlot(s)));
		ItemStack main = p.getMainHandItem();
		ItemStack off = p.getOffhandItem();
		return new Gear(families(main), isBook(main), families(off), isBook(off), armour);
	}

	/**
	 * The calm mix for one viewer. {@code shown} says which effects the viewer has on; when the most striking effect
	 * of an item is off, its next one takes over.
	 */
	static List<Source> compose(Gear g, Predicate<String> shown) {
		List<Source> out = new ArrayList<>();
		// 1. The hand: what you hold is what people look at
		String hand = first(g.hand(), shown);
		if (hand != null) out.add(new Source(Kind.HAND, hand, 0.55, slow(hand, g.handBook() ? 4 : 2)));

		// 2. Armour: a full set with a shared effect becomes one orbit…
		String set = setFamily(g.armour(), shown);
		if (set != null) {
			out.add(new Source(Kind.SET, set, 0.5, 1));
		} else {
			// …otherwise at most two effects, each at the (average) height of the pieces that give it
			Map<String, List<Double>> byFamily = new LinkedHashMap<>();
			for (int i = 0; i < g.armour().size(); i++) {
				String f = first(g.armour().get(i), shown);
				if (f != null) byFamily.computeIfAbsent(f, k -> new ArrayList<>()).add(ARMOUR_HEIGHT[i]);
			}
			List<String> fams = new ArrayList<>(byFamily.keySet());
			// The most striking first; with equal standing, the one on more pieces
			fams.sort((a, b) -> {
				int r = Integer.compare(rank(a), rank(b));
				return r != 0 ? r : Integer.compare(byFamily.get(b).size(), byFamily.get(a).size());
			});
			int armour = 0;
			for (String f : fams) {
				if (armour == 2) break;
				if (f.equals(hand)) continue; // already shown by the hand
				armour++;
				double h = byFamily.get(f).stream().mapToDouble(Double::doubleValue).average().orElse(0.5);
				out.add(new Source(Kind.ARMOUR, f, h, slow(f, 4)));
			}
		}

		// 3. The off hand, only when it adds something new
		String off = first(g.offhand(), shown);
		if (off != null && out.stream().noneMatch(s -> s.family().equals(off))) {
			out.add(new Source(Kind.OFFHAND, off, 0.55, slow(off, 4)));
		}
		return out.size() > MAX_SOURCES ? new ArrayList<>(out.subList(0, MAX_SOURCES)) : out;
	}

	/** The most striking effect that is on all four armour pieces (and shown), or null. */
	static String setFamily(List<List<String>> armour, Predicate<String> shown) {
		if (armour.size() != 4 || armour.stream().anyMatch(List::isEmpty)) return null;
		for (String f : armour.getFirst()) {
			if (!shown.test(f)) continue;
			if (armour.stream().allMatch(piece -> piece.contains(f))) return f;
		}
		return null;
	}

	private static String first(List<String> fams, Predicate<String> shown) {
		for (String f : fams) if (shown.test(f)) return f;
		return null;
	}

	private static int slow(String family, int every) {
		return BUSY.contains(family) ? every * 2 : every;
	}

	// ---------------------------------------------------------------- sending

	static void tick(MinecraftServer server) {
		int run = server.getTickCount() / RUN;
		for (ServerPlayer wearer : server.getPlayerList().getPlayers()) {
			if (wearer.isSpectator() || wearer.isInvisible()) continue;
			Gear gear = gear(wearer);
			if (gear.hand().isEmpty() && gear.offhand().isEmpty() && gear.armour().stream().allMatch(List::isEmpty)) continue;
			ServerLevel level = wearer.level();
			// Stagger players, so a group doesn't pulse in step
			int phase = Math.floorMod(wearer.getUUID().hashCode(), 64);
			for (ServerPlayer viewer : level.players()) {
				if (viewer.distanceToSqr(wearer) > 32 * 32 || !GlintMod.AURA.get(viewer)) continue;
				boolean self = viewer == wearer;
				if (self && !GlintMod.AURA_SELF.get(viewer)) continue;
				for (Source s : compose(gear, f -> GlintMod.enabled(viewer, f))) {
					if (Math.floorMod(run + phase + s.kind().ordinal(), s.every()) != 0) continue;
					send(level, viewer, wearer, s, run, self);
				}
			}
		}
	}

	private static void send(ServerLevel level, ServerPlayer viewer, ServerPlayer wearer, Source s, int run, boolean self) {
		ParticleOptions particle = PARTICLES.get(s.family());
		Vec3 base = wearer.position();
		double h = wearer.getBbHeight();
		Vec3 look = wearer.getViewVector(1f);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 right = new Vec3(-flat.z, 0, flat.x).scale(0.35);
		switch (s.kind()) {
			case HAND, OFFHAND -> {
				// A little in front of the body, on the side of that hand
				Vec3 pos = base.add(0, h * s.height(), 0).add(flat.scale(0.4)).add(s.kind() == Kind.HAND ? right : right.reverse());
				level.sendParticles(viewer, particle, false, false, pos.x, pos.y, pos.z, 1, 0.12, 0.08, 0.12, 0.01);
			}
			case ARMOUR -> {
				Vec3 pos = base.add(0, h * s.height(), 0);
				if (self) {
					pos = behindCamera(wearer.getEyePosition(), look, pos.subtract(flat.scale(0.3)));
					level.sendParticles(viewer, particle, false, false, pos.x, pos.y, pos.z, 1, 0.04, 0.04, 0.04, 0.005);
				} else {
					level.sendParticles(viewer, particle, false, false, pos.x, pos.y, pos.z, 1, 0.15, 0.1, 0.15, 0.01);
				}
			}
			case SET -> {
				// Two interleaved spirals: one point per run, slowly going round and up and down the body
				double angle = Math.toRadians(run * 40.0 + (run % 2) * 180.0);
				double y = h * (0.5 + 0.42 * Math.sin(run * 0.23));
				Vec3 pos = base.add(Math.cos(angle) * 0.6, y, Math.sin(angle) * 0.6);
				// On yourself only the half behind your camera (the front half is hidden by your body in F5 anyway)
				if (self && pos.subtract(wearer.getEyePosition()).dot(look) > -BEHIND) return;
				level.sendParticles(viewer, particle, false, false, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
			}
		}
	}

	/**
	 * Moves a point to at least {@link #BEHIND} blocks behind the plane of the camera. A first-person camera can't see
	 * anything behind itself; a third-person camera (a few blocks further back) can.
	 */
	static Vec3 behindCamera(Vec3 eye, Vec3 look, Vec3 pos) {
		double d = pos.subtract(eye).dot(look);
		return d > -BEHIND ? pos.subtract(look.scale(d + BEHIND)) : pos;
	}

	// ---------------------------------------------------------------- effects per item

	/** The most striking effect on an item (the order of families.json), or null. */
	static String familyOf(ItemStack st) {
		List<String> f = families(st);
		return f.isEmpty() ? null : f.getFirst();
	}

	/** Every effect on an item that has a particle, most striking first. Enchanted books use their stored ones. */
	static List<String> families(ItemStack st) {
		List<String> out = new ArrayList<>();
		if (st.isEmpty()) return out;
		ItemEnchantments ench = st.get(DataComponents.ENCHANTMENTS);
		if (ench == null || ench.isEmpty()) ench = st.get(DataComponents.STORED_ENCHANTMENTS);
		if (ench == null || ench.isEmpty()) return out;
		for (Holder<Enchantment> h : ench.keySet()) {
			String id = h.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
			String fam = GlintMod.BY_ENCHANT.get(id);
			if (fam != null && PARTICLES.containsKey(fam) && !out.contains(fam)) out.add(fam);
		}
		out.sort((a, b) -> Integer.compare(rank(a), rank(b)));
		return out;
	}

	private static boolean isBook(ItemStack st) {
		return st.is(net.minecraft.world.item.Items.ENCHANTED_BOOK);
	}

	private static final List<String> ORDER = new ArrayList<>();

	/** Place in families.json: lower = more striking. */
	static int rank(String family) {
		if (ORDER.isEmpty()) ORDER.addAll(GlintMod.FAMILIES.keySet());
		int i = ORDER.indexOf(family);
		return i < 0 ? Integer.MAX_VALUE : i;
	}
}
