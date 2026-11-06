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

	/** Particles that are tiny (enchanting glyphs, ash, wax): a few at a time, or you'd never see them. */
	static final Set<String> TINY = Set.of("protection", "feather", "haste");

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

	/**
	 * One effect of one item. {@code strength} 1 (a hint), 2 (clear) or 3 (rich) follows the enchantment level: Sharpness
	 * I is a whiff, Sharpness V a steady glow. Enchantments with only one level count as 2.
	 */
	record Effect(String family, int strength) {
	}

	/**
	 * One thing that gives off particles. {@code every}: fires once per this many runs; {@code strength} after the
	 * viewer's maximum.
	 */
	record Source(Kind kind, String family, double height, int every, int strength) {
	}

	/**
	 * What a player carries, per slot: the effects of each item, most striking first. Empty lists for nothing.
	 * {@code armour} is head, chest, legs, feet.
	 */
	record Gear(List<Effect> hand, boolean handBook, List<Effect> offhand, boolean offBook, List<List<Effect>> armour) {
		boolean isEmpty() {
			return this.hand.isEmpty() && this.offhand.isEmpty() && this.armour.stream().allMatch(List::isEmpty);
		}
	}

	private Aura() {
	}

	// ---------------------------------------------------------------- what to show

	/**
	 * What a player's gear gives off here. Leaves out what shows another way now: a tool or weapon carries its own
	 * effect (texture parts and auras, drawn by the item itself), and a full armour set has its set effect (Fx).
	 */
	static Gear gear(ServerPlayer p) {
		boolean set = Fx.hasSet(p);
		List<List<Effect>> armour = new ArrayList<>();
		for (EquipmentSlot s : ARMOUR) armour.add(set ? List.of() : effects(p.getItemBySlot(s)));
		ItemStack main = p.getMainHandItem();
		ItemStack off = p.getOffhandItem();
		return new Gear(drawnByItself(main) ? List.of() : effects(main), isBook(main), drawnByItself(off) ? List.of() : effects(off),
				isBook(off), armour);
	}

	/** The tools and weapons whose enchantments the item shows itself (scripts/glint/tools_fx.py). */
	private static final java.util.Set<String> SELF_DRAWN = java.util.Set.of("sword", "axe", "pickaxe", "shovel", "hoe", "shears",
			"spear", "bow", "crossbow", "trident", "mace", "fishing_rod");

	static boolean drawnByItself(ItemStack st) {
		if (st.isEmpty()) return false;
		String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
		for (String k : SELF_DRAWN) if (path.equals(k) || path.endsWith("_" + k)) return true;
		return false;
	}

	/**
	 * The calm mix for one viewer. {@code shown} says which effects the viewer has on (when the most striking effect of
	 * an item is off, its next one takes over); {@code max} is the viewer's maximum strength (1–3).
	 */
	static List<Source> compose(Gear g, Predicate<String> shown, int max) {
		List<Source> out = new ArrayList<>();
		// 1. The hand: what you hold is what people look at
		Effect hand = first(g.hand(), shown);
		if (hand != null) {
			int st = Math.min(max, hand.strength());
			out.add(new Source(Kind.HAND, hand.family(), 0.55, slow(hand.family(), handEvery(st) * (g.handBook() ? 2 : 1)), st));
		}

		// 2. Armour: a full set with a shared effect becomes one orbit…
		Effect set = setEffect(g.armour(), shown);
		if (set != null) {
			int st = Math.min(max, set.strength());
			out.add(new Source(Kind.SET, set.family(), 0.5, slow(set.family(), st == 1 ? 2 : 1), st));
		} else {
			// …otherwise at most two effects, each at the (average) height of the pieces that give it
			Map<String, List<Double>> heights = new LinkedHashMap<>();
			Map<String, Integer> strength = new LinkedHashMap<>();
			for (int i = 0; i < g.armour().size(); i++) {
				Effect e = first(g.armour().get(i), shown);
				if (e == null) continue;
				heights.computeIfAbsent(e.family(), k -> new ArrayList<>()).add(ARMOUR_HEIGHT[i]);
				strength.merge(e.family(), e.strength(), Math::max);
			}
			List<String> fams = new ArrayList<>(heights.keySet());
			// The most striking first; with equal standing, the one on more pieces
			fams.sort((a, b) -> {
				int r = Integer.compare(rank(a), rank(b));
				return r != 0 ? r : Integer.compare(heights.get(b).size(), heights.get(a).size());
			});
			int armour = 0;
			for (String f : fams) {
				if (armour == 2) break;
				if (hand != null && f.equals(hand.family())) continue; // already shown by the hand
				armour++;
				double h = heights.get(f).stream().mapToDouble(Double::doubleValue).average().orElse(0.5);
				int st = Math.min(max, strength.get(f));
				out.add(new Source(Kind.ARMOUR, f, h, slow(f, armourEvery(st)), st));
			}
		}

		// 3. The off hand, only when it adds something new
		Effect off = first(g.offhand(), shown);
		if (off != null && out.stream().noneMatch(s -> s.family().equals(off.family()))) {
			int st = Math.min(max, off.strength());
			out.add(new Source(Kind.OFFHAND, off.family(), 0.55, slow(off.family(), handEvery(st) * 2 * (g.offBook() ? 2 : 1)), st));
		}
		return out.size() > MAX_SOURCES ? new ArrayList<>(out.subList(0, MAX_SOURCES)) : out;
	}

	/** Hand: strength 1 once a second, 2 twice, 3 four times (runs are a quarter second). */
	private static int handEvery(int strength) {
		return strength >= 3 ? 1 : strength == 2 ? 2 : 4;
	}

	/** Armour is calmer than the hand: every two seconds, every second, twice a second. */
	private static int armourEvery(int strength) {
		return strength >= 3 ? 2 : strength == 2 ? 4 : 8;
	}

	/**
	 * The most striking effect that is on all four armour pieces (and shown), or null. A set is as strong as its weakest
	 * piece.
	 */
	static Effect setEffect(List<List<Effect>> armour, Predicate<String> shown) {
		if (armour.size() != 4 || armour.stream().anyMatch(List::isEmpty)) return null;
		for (Effect e : armour.getFirst()) {
			if (!shown.test(e.family())) continue;
			int weakest = Integer.MAX_VALUE;
			boolean everywhere = true;
			for (List<Effect> piece : armour) {
				Effect match = piece.stream().filter(x -> x.family().equals(e.family())).findFirst().orElse(null);
				if (match == null) {
					everywhere = false;
					break;
				}
				weakest = Math.min(weakest, match.strength());
			}
			if (everywhere) return new Effect(e.family(), weakest);
		}
		return null;
	}

	private static Effect first(List<Effect> effects, Predicate<String> shown) {
		for (Effect e : effects) if (shown.test(e.family())) return e;
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
			if (gear.isEmpty()) continue;
			ServerLevel level = wearer.level();
			// Stagger players, so a group doesn't pulse in step
			int phase = Math.floorMod(wearer.getUUID().hashCode(), 64);
			for (ServerPlayer viewer : level.players()) {
				if (viewer.distanceToSqr(wearer) > 32 * 32) continue;
				boolean self = viewer == wearer;
				boolean first = self && GlintMod.AURA_FIRST.get(viewer);
				boolean third = self && GlintMod.AURA_THIRD.get(viewer);
				if (self ? !first && !third : !GlintMod.AURA.get(viewer)) continue;
				int max = GlintMod.INTENSITY.get(viewer);
				for (Source s : compose(gear, f -> GlintMod.enabled(viewer, f), max)) {
					if (Math.floorMod(run + phase + s.kind().ordinal(), s.every()) != 0) continue;
					boolean worn = s.kind() == Kind.ARMOUR || s.kind() == Kind.SET;
					if (self && worn && !third) continue;
					// Your own hand: where it is in first person; when only third person is on, behind the camera too
					View view = !self ? View.OTHERS : worn || !first ? View.THIRD_PERSON : View.FIRST_PERSON;
					send(level, viewer, wearer, s, run, view);
				}
			}
		}
	}

	/** Who looks: someone else, you in first person (the hand as it is), or you in third person only. */
	enum View {
		OTHERS, FIRST_PERSON, THIRD_PERSON
	}

	/** For the screenshot test: particles sent per view (others, first person, third person). */
	static final int[] SENT = new int[3];

	private static void send(ServerLevel level, ServerPlayer viewer, ServerPlayer wearer, Source s, int run, View view) {
		SENT[view.ordinal()]++;
		ParticleOptions particle = PARTICLES.get(s.family());
		Vec3 base = wearer.position();
		double h = wearer.getBbHeight();
		Vec3 look = wearer.getViewVector(1f);
		Vec3 eye = wearer.getEyePosition();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 right = new Vec3(-flat.z, 0, flat.x).scale(0.35);
		boolean hidden = view == View.THIRD_PERSON;
		// Tiny particles come a few at a time; your own armour in third person a little fuller, as it's further away
		int count = (TINY.contains(s.family()) ? 3 : 1) + (hidden ? 1 : 0);
		switch (s.kind()) {
			case HAND, OFFHAND -> {
				// A little in front of the body, on the side of that hand
				Vec3 pos = base.add(0, h * s.height(), 0).add(flat.scale(0.4)).add(s.kind() == Kind.HAND ? right : right.reverse());
				if (hidden) pos = behindCamera(eye, look, pos);
				double spread = hidden ? 0.04 : 0.12;
				level.sendParticles(viewer, particle, false, false, pos.x, pos.y, pos.z, 1, spread, spread * 0.7, spread, 0.01);
			}
			case ARMOUR -> {
				Vec3 pos = base.add(0, h * s.height(), 0);
				if (hidden) {
					// Just behind your back: between you and a third-person camera, behind a first-person one
					pos = behindCamera(eye, look, pos.subtract(flat.scale(0.3)));
					level.sendParticles(viewer, particle, false, false, pos.x, pos.y, pos.z, count, 0.2, 0.1, 0.06, 0.005);
				} else {
					level.sendParticles(viewer, particle, false, false, pos.x, pos.y, pos.z, count, 0.15, 0.1, 0.15, 0.01);
				}
			}
			case SET -> {
				// A slow orbit going round and up and down the body; rich sets have a second point opposite
				int points = s.strength() >= 3 ? 2 : 1;
				for (int i = 0; i < points; i++) {
					double angle = Math.toRadians(run * 40.0 + i * 180.0);
					double y = h * (0.5 + 0.42 * Math.sin(run * 0.23 + i * Math.PI));
					Vec3 pos = base.add(Math.cos(angle) * 0.6, y, Math.sin(angle) * 0.6);
					// On yourself the whole orbit is pressed to just behind your camera: there a first-person camera
					// can't see it, and a third-person one sees it go round your back
					if (hidden) pos = behindCamera(eye, look, pos);
					if (count == 1) level.sendParticles(viewer, particle, false, false, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
					else level.sendParticles(viewer, particle, false, false, pos.x, pos.y, pos.z, count, 0.06, 0.06, 0.06, 0.005);
				}
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
		List<Effect> f = effects(st);
		return f.isEmpty() ? null : f.getFirst().family();
	}

	/**
	 * Every effect on an item that has a particle, most striking first, with its strength. Enchanted books use their
	 * stored enchantments. Two enchantments of one family (Fire Aspect and Flame) count once, at the stronger level.
	 */
	static List<Effect> effects(ItemStack st) {
		List<Effect> out = new ArrayList<>();
		if (st.isEmpty()) return out;
		ItemEnchantments ench = st.get(DataComponents.ENCHANTMENTS);
		if (ench == null || ench.isEmpty()) ench = st.get(DataComponents.STORED_ENCHANTMENTS);
		if (ench == null || ench.isEmpty()) return out;
		Map<String, Integer> best = new LinkedHashMap<>();
		for (var entry : ench.entrySet()) {
			Holder<Enchantment> h = entry.getKey();
			String id = h.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
			String fam = GlintMod.BY_ENCHANT.get(id);
			if (fam == null || !PARTICLES.containsKey(fam)) continue;
			best.merge(fam, strength(entry.getIntValue(), h.value().getMaxLevel()), Math::max);
		}
		best.forEach((f, st2) -> out.add(new Effect(f, st2)));
		out.sort((a, b) -> Integer.compare(rank(a.family()), rank(b.family())));
		return out;
	}

	/** Level → strength 1–3: the lowest level a hint, the highest level rich; one-level enchantments clear (2). */
	static int strength(int level, int maxLevel) {
		if (maxLevel <= 1) return 2;
		double f = (Math.min(level, maxLevel) - 1) / (double) (maxLevel - 1);
		return 1 + (int) Math.round(2 * Math.max(0, f));
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
