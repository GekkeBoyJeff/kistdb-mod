package nl.libreria.glint;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Proof of concept: particles and light of our own, server-side, per effect and per grade.
 *
 * <p>Particles: soft glowing sprites from the pack (white, coloured per particle) on item displays. The client moves
 * them (teleport interpolation), shrinks and turns them (transformation interpolation) by itself: a packet to start,
 * one per step, one to remove it. They face you and are lit by themselves.
 *
 * <p>Light: a light block that only the players nearby are told about. The client's own lighting does the rest, so the
 * ground and walls around you really light up. The world itself is never changed.
 *
 * <p>Each effect has its own motif, and the grade decides how much of it there is: grade I a hint, the highest grade a
 * signature of its own (a rune circle under you, flames round your ankles, snow falling around you, a pillar of light).
 */
final class Fx {
	private static final RandomSource R = RandomSource.create();

	// ---------------------------------------------------------------- particles

	/** A state of a particle: where (relative to its burst), how big, turned how far. */
	record S(Vec3 pos, float scale, float rot) {
	}

	/** A particle: a sprite and colour going through states, {@code step} ticks apart. */
	record P(String sprite, int rgb, int step, List<S> states, @Nullable Quaternionf base) {
		Quaternionf turn(float r) {
			return (base == null ? new Quaternionf() : new Quaternionf(base)).rotateZ(r);
		}

		int life() {
			return step * (states.size() - 1);
		}
	}

	static P line(String sprite, int rgb, Vec3 from, Vec3 to, float s0, float s1, float r0, float r1, int ticks) {
		return new P(sprite, rgb, ticks, List.of(new S(from, s0, r0), new S(to, s1, r1)), null);
	}

	/** Appears, flares up and goes out where it is (a sparkle). */
	static P pop(String sprite, int rgb, Vec3 at, float peak, int ticks, float spin) {
		int h = Math.max(1, ticks / 2);
		return new P(sprite, rgb, h, List.of(new S(at, 0.01f, 0), new S(at, peak, spin * 0.5f), new S(at, 0.01f, spin)), null);
	}

	private static final class Burst {
		final ElementHolder holder = new ElementHolder();
		final List<ItemDisplayElement> els = new ArrayList<>();
		final List<P> ps = new ArrayList<>();
		/** -1: made this tick (the client sees it first, the move comes a tick later). */
		int age = -1, life;
	}

	private static final List<Burst> LIVE = new ArrayList<>();

	static ItemStack sprite(String name, int rgb) {
		ItemStack s = new ItemStack(Items.PAPER);
		s.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("libreria", "fx/p/" + name));
		s.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(rgb)));
		return s;
	}

	/**
	 * While true, sprites are the far-only copies: the shader fades them out within 3 blocks of the camera. An armour
	 * set's particles are made that way, so you see your set on yourself in third person (and others see it on you),
	 * but it never fills your screen in first person.
	 */
	static boolean far;

	/** While set: the colour a set's sprites are pulled towards (its armour's material), and how far (0..1). */
	static int tint = -1;
	static float tintBy;

	static int mixRgb(int a, int b, float t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private static ItemDisplayElement element(String sprite, int rgb, boolean fixed) {
		if (far && tint >= 0) rgb = mixRgb(rgb, tint, tintBy);
		ItemDisplayElement e = new ItemDisplayElement(sprite(far ? "far_" + sprite : sprite, rgb));
		e.setItemDisplayContext(ItemDisplayContext.FIXED);
		e.setBillboardMode(fixed ? Display.BillboardConstraints.FIXED : Display.BillboardConstraints.CENTER);
		e.setBrightness(new Brightness(15, 15));
		e.setViewRange(0.6f);
		return e;
	}

	/** Shows particles around a spot to everyone who can see it. */
	static void emit(ServerLevel level, Vec3 at, List<ServerPlayer> viewers, List<P> ps) {
		if (ps.isEmpty() || viewers.isEmpty()) return;
		Burst b = new Burst();
		for (P p : ps) {
			S s0 = p.states().getFirst();
			ItemDisplayElement e = element(p.sprite(), p.rgb(), p.base() != null);
			e.setOffset(s0.pos());
			e.setScale(new Vector3f(s0.scale()));
			e.setLeftRotation(p.turn(s0.rot()));
			e.setTeleportDuration(Math.min(59, p.step()));
			e.setInterpolationDuration(p.step());
			b.holder.addElement(e);
			b.els.add(e);
			b.ps.add(p);
			b.life = Math.max(b.life, p.life() + 2);
		}
		new ManualAttachment(b.holder, level, () -> at);
		for (ServerPlayer v : viewers) b.holder.startWatching(v);
		LIVE.add(b);
	}

	// ---------------------------------------------------------------- what someone wears or swings

	/** Things that stay on a wearer: a ring of sprites around them, a circle on the ground, flames at the feet. */
	private static final class Ring {
		final ElementHolder holder = new ElementHolder();
		final List<ItemDisplayElement> els = new ArrayList<>();
	}

	private static final class Wearer {
		String kind;
		int grade;
		/** For an armour set: protection, fire, blast or projectile, and what the pieces add (thorns, feather, frost, soul). */
		String set = "";
		/** The armour's material (leather … netherite): an accent of its own in the set's effect. */
		String material = "diamond";
		final java.util.Set<String> extras = new java.util.HashSet<>();
		@Nullable BlockPos light;
		int lightLevel;
		final Map<String, Ring> rings = new HashMap<>();
	}

	private static final Map<UUID, Wearer> WEARERS = new HashMap<>();

	/** "aegis:3", "ember:4", … or "off". */
	static void set(ServerPlayer p, String spec) {
		Wearer w = WEARERS.remove(p.getUUID());
		if (w != null) clear(p, w);
		if (spec.equals("off")) return;
		String[] parts = spec.split(":");
		w = new Wearer();
		w.kind = parts[0];
		if (w.kind.equals("set")) {  // "set:fire:4:thorns,soul"
			w.set = parts[1];
			w.grade = Integer.parseInt(parts[2]);
			if (parts.length > 3) for (String x : parts[3].split(",")) if (!x.isEmpty()) w.extras.add(x);
			if (parts.length > 4) w.material = parts[4];
			w.lightLevel = switch (w.set) {
				case "fire" -> w.extras.contains("frost") ? 4 + w.grade : 4 + w.grade * 2;
				case "protection" -> w.grade >= 3 ? 5 : 0;
				default -> 0;
			};
			if (w.extras.contains("soul")) w.lightLevel = Math.max(w.lightLevel, 6);
		} else {
			w.grade = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
			w.lightLevel = lightFor(w.kind, w.grade);
		}
		WEARERS.put(p.getUUID(), w);
	}

	private static int lightFor(String kind, int g) {
		return switch (kind) {
			case "blade_fire" -> 6 + g * 2;
			case "blade_smite" -> g >= 3 ? 4 + g : 0;
			case "ember" -> new int[]{0, 0, 7, 10, 13}[g];
			case "soul" -> new int[]{0, 0, 5, 8, 11}[g];
			case "aegis" -> new int[]{0, 0, 0, 6, 9}[g];
			case "frost" -> new int[]{0, 0, 0, 4, 7}[g];
			case "tide" -> new int[]{0, 0, 0, 6, 6}[g];
			default -> 0;
		};
	}

	private static void clear(ServerPlayer p, Wearer w) {
		if (w.light != null) restoreLight(p, w.light);
		w.rings.values().forEach(r -> r.holder.destroy());
		w.rings.clear();
	}

	static List<ServerPlayer> viewers(ServerPlayer p) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer o : p.level().players()) {
			if (o.distanceToSqr(p) < 48 * 48) out.add(o);
		}
		return out;
	}

	/** For the screenshots: where the light of this player is now (or null). */
	static @Nullable BlockPos lightOf(ServerPlayer p) {
		Wearer w = WEARERS.get(p.getUUID());
		return w == null ? null : w.light;
	}

	static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Wearer>> it = WEARERS.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null) {
				e.getValue().rings.values().forEach(r -> r.holder.destroy());
				it.remove();
				continue;
			}
			wear(p, e.getValue(), server.getTickCount());
		}
		for (Iterator<Burst> it = LIVE.iterator(); it.hasNext();) {
			Burst b = it.next();
			b.age++;
			for (int i = 0; i < b.els.size(); i++) {
				P p = b.ps.get(i);
				if (b.age < 1 || b.age % p.step() != 0) continue;
				int k = b.age / p.step();
				if (k >= p.states().size()) continue;
				S s = p.states().get(k);
				ItemDisplayElement e = b.els.get(i);
				e.setOffset(s.pos());
				e.setScale(new Vector3f(s.scale()));
				e.setLeftRotation(p.turn(s.rot()));
				e.startInterpolation();
			}
			b.holder.tick();
			if (b.age > b.life) {
				b.holder.destroy();
				it.remove();
			}
		}
		tickFlashes(server);
		tickPillars();
		tickShafts();
	}

	private static int pick(int... colours) {
		return colours[R.nextInt(colours.length)];
	}

	private static float rnd(float a, float b) {
		return a + R.nextFloat() * (b - a);
	}

	private static boolean every(int tick, int n) {
		return n > 0 && tick % n == 0;
	}

	/** A spot on the body: around the middle, at a height (0 feet, 1 head). */
	private static Vec3 onBody(ServerPlayer p, float y0, float y1, float r) {
		double a = R.nextDouble() * Math.PI * 2;
		return new Vec3(Math.cos(a) * r, p.getBbHeight() * rnd(y0, y1), Math.sin(a) * r);
	}

	private static void wear(ServerPlayer p, Wearer w, int tick) {
		ServerLevel level = (ServerLevel) p.level();
		List<ServerPlayer> viewers = viewers(p);
		List<P> ps = new ArrayList<>();
		int g = w.grade;
		switch (w.kind) {
			case "aegis" -> aegis(p, w, level, viewers, tick, g, ps);
			case "ember" -> ember(p, w, level, viewers, tick, g, ps);
			case "frost" -> frost(p, w, level, viewers, tick, g, ps);
			case "soul" -> soul(p, w, level, viewers, tick, g, ps);
			case "feather" -> feather(p, w, level, viewers, tick, g, ps);
			case "thorns" -> thorns(p, w, level, viewers, tick, g, ps);
			case "tide" -> tide(p, w, level, viewers, tick, g, ps);
			case "curse" -> curse(p, w, level, viewers, tick, ps);
			case "blade_fire", "blade_sharp", "blade_smite", "blade_bane" -> {
				// Others see it on the sword in your hand; you (in first person) see it on the sword at the bottom right
				// of your screen. The server can't tell which view you use, so yours goes where first person has it
				List<ServerPlayer> others = new ArrayList<>(viewers);
				others.remove(p);
				List<P> mine = new ArrayList<>();
				blade(p, w, tick, g, ps, t -> blade(p, t), 1f);
				blade(p, w, tick, g, mine, t -> bladeFp(p, t), 0.45f);
				if (!ps.isEmpty()) emit(level, p.position(), others, ps);
				if (!mine.isEmpty()) emit(level, p.position(), List.of(p), mine);
				ps.clear();
			}
			case "vanish" -> vanish(p, w, level, viewers, tick, ps);
			case "set" -> {
				far = true;
				// the material pulls the colours its way: gold warmer and brighter, netherite dark and red, copper
				// towards verdigris, leather earthy; iron, chainmail and diamond keep the set's own colours
				switch (w.material) {
					case "golden" -> { tint = 0xFFD45A; tintBy = 0.35f; }
					case "netherite" -> { tint = 0x6A1E26; tintBy = 0.4f; }
					case "copper" -> { tint = 0x4FC8A8; tintBy = 0.3f; }
					case "leather" -> { tint = 0xC08A5A; tintBy = 0.3f; }
					default -> tint = -1;
				}
				try {
					armourSet(p, w, level, viewers, tick, g, ps);
					materialTouch(p, w, tick, ps);
					if (!ps.isEmpty()) emit(level, p.position(), viewers, ps);
				} finally {
					far = false;
					tint = -1;
				}
				ps.clear();
			}
			default -> {
			}
		}
		if (!ps.isEmpty()) emit(level, p.position(), viewers, ps);
		light(p, w, viewers, tick);
	}

	// ---------------------------------------------------------------- armour, per motif

	/**
	 * Protection, "Aegis": arcane blue and violet. I: now and then a sparkle on you. II: more, and motes that drift up.
	 * III: two runes circle your middle, a little light. IV: four runes, a turning rune circle under your feet, light.
	 */
	private static void aegis(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, int tick, int g, List<P> ps) {
		int[] twinkle = {0, 14, 8, 6, 4};
		if (every(tick, twinkle[g])) ps.add(pop("twinkle", pick(0xDCE6FF, 0xB9C8FF, 0xD9C6FF), onBody(p, 0.1f, 0.95f, 0.36f),
				rnd(0.4f, 0.6f) + g * 0.05f, 8, rnd(-0.6f, 0.6f)));
		if (g >= 2 && every(tick, g >= 4 ? 3 : 5)) {
			Vec3 from = onBody(p, 0.0f, 0.6f, rnd(0.35f, 0.6f));
			ps.add(line("mote", pick(0x9FB4FF, 0xC2A6FF), from, from.add(0, rnd(0.8f, 1.3f), 0), rnd(0.35f, 0.5f), 0.02f, 0, 0, 28));
		}
		if (g >= 3) orbit(p, w, level, viewers, tick, "runes", g >= 4 ? 4 : 2, 0.8, 0.52, g >= 4 ? 0.46f : 0.38f, 80,
				new String[]{"rune_a", "rune_b", "rune_c", "rune_d"}, new int[]{0x9FB4FF, 0xC2A6FF});
		if (g >= 4) ground(p, w, level, viewers, tick, "circle", 0xA9B8FF, 2.2f, 160);
	}

	/**
	 * Fire Protection, "Ember". I: a single ember now and then. II: steady embers and sparks, a warm light. III: more,
	 * bigger, with heat haze. IV: flames lick round your feet, embers all the time, a bright light.
	 */
	private static void ember(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, int tick, int g, List<P> ps) {
		int[] ember = {0, 9, 4, 2, 1};
		if (every(tick, ember[g])) {
			Vec3 from = onBody(p, 0.05f, 0.95f, 0.42f);
			ps.add(line("ember", pick(0xFFE9A8, 0xFFB64A, 0xFF8A2E, 0xFF5A1C), from,
					from.add(rnd(-0.18f, 0.18f), rnd(0.6f, 1.15f) + g * 0.1f, rnd(-0.18f, 0.18f)), rnd(0.35f, 0.5f) + g * 0.06f, 0.04f, 0, 0,
					16 + R.nextInt(14)));
		}
		if (g >= 2 && every(tick, g >= 4 ? 2 : 4)) ps.add(pop("twinkle", 0xFFF1C2, onBody(p, 0.2f, 0.9f, 0.4f), rnd(0.35f, 0.5f), 6, 0.8f));
		if (g >= 3 && every(tick, 5)) {
			Vec3 from = new Vec3(rnd(-0.15f, 0.15f), p.getBbHeight() * rnd(0.25f, 0.6f), rnd(-0.15f, 0.15f));
			ps.add(line("haze", pick(0xFF9A3C, 0xFF7A2A), from, from.add(0, 0.5, 0), 1.2f, 1.6f, 0, 0, 22));
		}
		if (g >= 4) flames(p, w, level, viewers, tick, "flames", 7, 0.55, new int[]{0xFF9A3C, 0xFFB64A, 0xFF6A1F});
	}

	/**
	 * Frost. I: a cold glint at your feet. II: snowflakes that drift and turn. III: more, a cold mist round your feet.
	 * IV: snow falls around you and a ring of ice turns at your feet.
	 */
	private static void frost(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, int tick, int g, List<P> ps) {
		if (every(tick, g >= 3 ? 4 : 9)) ps.add(pop("twinkle", 0xCFEFFF, onBody(p, 0.0f, g >= 2 ? 0.9f : 0.25f, 0.38f), rnd(0.35f, 0.5f), 8, 0.8f));
		int[] flakes = {0, 0, 5, 3, 2};
		if (every(tick, flakes[g])) {
			Vec3 from = new Vec3(rnd(-0.6f, 0.6f), p.getBbHeight() * rnd(0.6f, 1.05f), rnd(-0.6f, 0.6f));
			ps.add(line("flake", pick(0xF2FAFF, 0xBFE6FF, 0x8FD0FF), from, from.add(rnd(-0.2f, 0.2f), -rnd(0.8f, 1.2f), rnd(-0.2f, 0.2f)),
					rnd(0.22f, 0.3f), 0.1f, 0, rnd(-2.2f, 2.2f), 30 + R.nextInt(10)));
		}
		if (g >= 3 && every(tick, 6)) {
			Vec3 from = new Vec3(rnd(-0.4f, 0.4f), 0.1, rnd(-0.4f, 0.4f));
			ps.add(line("haze", 0xBFE6FF, from, from.add(rnd(-0.3f, 0.3f), 0.15, rnd(-0.3f, 0.3f)), 0.9f, 1.4f, 0, 0, 30));
		}
		if (g >= 4) {
			// snow from above, in a wider circle
			if (every(tick, 1)) {
				double a = R.nextDouble() * Math.PI * 2, r = rnd(0.7f, 1.8f);
				Vec3 from = new Vec3(Math.cos(a) * r, p.getBbHeight() + rnd(0.6f, 1.2f), Math.sin(a) * r);
				ps.add(line("flake", pick(0xFFFFFF, 0xDDF3FF), from, from.add(rnd(-0.3f, 0.3f), -from.y + 0.05, rnd(-0.3f, 0.3f)),
						rnd(0.16f, 0.24f), 0.1f, 0, rnd(-2.5f, 2.5f), 40));
			}
			ground(p, w, level, viewers, tick, "icering", 0xCFEFFF, 1.6f, 240);
		}
	}

	/**
	 * Soul Speed, "Soul". I: a wisp now and then from your feet. II: steady wisps, a pale light. III: they spiral up, with
	 * a blue haze. IV: blue soul flames at your feet and a brighter light.
	 */
	private static void soul(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, int tick, int g, List<P> ps) {
		int[] wisp = {0, 10, 4, 2, 2};
		if (every(tick, wisp[g])) {
			double a = R.nextDouble() * Math.PI * 2;
			Vec3 from = new Vec3(Math.cos(a) * 0.5, 0.05, Math.sin(a) * 0.5);
			double up = p.getBbHeight() * (g >= 3 ? rnd(0.8f, 1.1f) : rnd(0.4f, 0.7f));
			double turn = g >= 3 ? 1.6 : 0.6;
			Vec3 mid = new Vec3(Math.cos(a + turn / 2) * 0.42, up / 2, Math.sin(a + turn / 2) * 0.42);
			Vec3 to = new Vec3(Math.cos(a + turn) * 0.24, up, Math.sin(a + turn) * 0.24);
			ps.add(new P("wisp", pick(0x8FF8FF, 0x4FDDF5, 0xB6F2FF), 13, List.of(new S(from, rnd(0.45f, 0.6f), 0), new S(mid, 0.4f, 0),
					new S(to, 0.05f, 0)), null));
		}
		if (g >= 3 && every(tick, 6)) ps.add(line("haze", 0x3FCFEA, new Vec3(0, 0.3, 0), new Vec3(0, 1.0, 0), 1.1f, 1.5f, 0, 0, 26));
		if (g >= 3 && every(tick, 4)) ps.add(pop("twinkle", 0xC8FBFF, onBody(p, 0.1f, 0.9f, 0.4f), rnd(0.35f, 0.5f), 8, 0.7f));
		if (g >= 4) flames(p, w, level, viewers, tick, "soulflames", 6, 0.5, new int[]{0x4FDDF5, 0x8FF8FF, 0x2FB5E0});
	}

	/**
	 * Feather Falling, "Feather". I: a feather drifts off now and then. II: more, with soft wind motes. III: feathers
	 * circle your ankles. IV: more feathers circling, and a gust ring at your feet now and then.
	 */
	private static void feather(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, int tick, int g, List<P> ps) {
		int[] f = {0, 14, 8, 6, 4};
		if (every(tick, f[g])) {
			Vec3 from = onBody(p, 0.0f, 0.3f, 0.38f);
			ps.add(new P("feather", pick(0xFFFFFF, 0xF2EEE6), 15, List.of(new S(from, 0.34f, 0), new S(from.add(rnd(-0.4f, 0.4f), 0.35, rnd(-0.4f,
					0.4f)), 0.36f, 0.9f), new S(from.add(rnd(-0.6f, 0.6f), -0.05, rnd(-0.6f, 0.6f)), 0.05f, -0.4f)), null));
		}
		if (g >= 2 && every(tick, 5)) {
			Vec3 from = onBody(p, 0.0f, 0.4f, 0.5f);
			ps.add(line("mote", 0xF6F4EE, from, from.add(rnd(-0.5f, 0.5f), 0.3, rnd(-0.5f, 0.5f)), 0.25f, 0.0f, 0, 0, 18));
		}
		if (g >= 3) orbit(p, w, level, viewers, tick, "feathers", g >= 4 ? 5 : 3, 0.6, 0.12, 0.36f, 60, new String[]{"feather"},
				new int[]{0xFFFFFF, 0xF2EEE6});
		if (g >= 4 && every(tick, 30)) {
			Quaternionf flat = new Quaternionf().rotateX(-Mth.HALF_PI);
			ps.add(new P("ring", 0xF6F4EE, 10, List.of(new S(new Vec3(0, 0.05, 0), 0.4f, 0), new S(new Vec3(0, 0.05, 0), 2.4f, 0.3f)), flat));
		}
	}

	/**
	 * Thorns. I: a thorn pricks out of you now and then. II: more, dark red. III: thorns circle your knees, and a red
	 * spark now and then.
	 */
	private static void thorns(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, int tick, int g, List<P> ps) {
		int[] t = {0, 12, 6, 4, 4};
		if (every(tick, t[g])) ps.add(pop("thorn", pick(0xB3263A, 0x8C1C2E, 0x5E8C3A), onBody(p, 0.1f, 0.9f, 0.4f), rnd(0.4f, 0.55f), 10,
				rnd(-0.8f, 0.8f)));
		if (g >= 2 && every(tick, 7)) ps.add(pop("twinkle", 0xFF6A6A, onBody(p, 0.1f, 0.9f, 0.4f), 0.35f, 6, 0.5f));
		if (g >= 3) orbit(p, w, level, viewers, tick, "thorns", 6, 0.62, 0.28, 0.42f, 100, new String[]{"thorn"},
				new int[]{0xB3263A, 0x5E8C3A});
	}

	/** Respiration and Depth Strider, "Tide". I: a bubble rises now and then. II: more. III: a ring of bubbles and light. */
	private static void tide(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, int tick, int g, List<P> ps) {
		int[] b = {0, 10, 5, 2, 2};
		if (every(tick, b[g])) {
			Vec3 from = onBody(p, 0.0f, 0.9f, 0.42f);
			ps.add(new P("bubble", pick(0xBFF3FF, 0x8FE3FF, 0xE6FBFF), 12, List.of(new S(from, rnd(0.2f, 0.32f), 0),
					new S(from.add(rnd(-0.15f, 0.15f), 0.5, rnd(-0.15f, 0.15f)), 0.3f, 0), new S(from.add(rnd(-0.25f, 0.25f), 1.0, rnd(-0.25f, 0.25f)),
							0.02f, 0)), null));
		}
		if (g >= 3) orbit(p, w, level, viewers, tick, "bubbles", 8, 0.7, 0.45, 0.22f, 70, new String[]{"bubble"}, new int[]{0xBFF3FF});
	}

	// ---------------------------------------------------------------- armour sets

	/**
	 * Four pieces with the same protection: one effect instead of four, that flows (things circle and rise in one
	 * motion). Thin lines and small sprites at the outline, so you still see who's wearing it. The pieces' own
	 * enchantments are woven in: Thorns adds barbs to the circle, Feather Falling feathers, and the boots decide what
	 * happens at your feet (Frost Walker: ice, Soul Speed: soul fire).
	 *
	 * <ul>
	 * <li>Protection, "Ward": a rune circle under you, one then two thin rings of light turning round you like a
	 * gyroscope, light rising along them; at IV a shimmer of the shield on you now and then.</li>
	 * <li>Fire Protection, "Ember mantle": small flames at your feet, embers spiralling up round you in one stream,
	 * two from II, heat rising from III, sparks at IV.</li>
	 * <li>Blast Protection, "Bulwark": heavy smoke curling at your feet, shards of stone circling and tumbling round
	 * your middle from II, sparks from III, a dark ring on the ground at IV.</li>
	 * <li>Projectile Protection, "Windward": wind lines sweeping round you, from II a leaf caught in it, at III at
	 * three heights, at IV a ring of wind at your feet.</li>
	 * </ul>
	 */
	private static void armourSet(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, int tick, int g, List<P> ps) {
		boolean frost = w.extras.contains("frost"), soul = w.extras.contains("soul");
		// Particles that may never come between you and whoever looks at you (flames, smoke): each viewer gets them all
		// except the ones on the side facing them, so they frame you instead of covering you
		List<P> frame = new ArrayList<>();
		double h = p.getBbHeight();
		int feet = soul ? 0x5FE6FF : frost ? 0xBFE6FF : 0;
		switch (w.set) {
			case "protection" -> {
				// Ward: a rune circle, thin rings of light turning round you like a gyroscope, light rising along them,
				// runes circling between the rings from III, the shield shimmering on you at IV
				int main = feet != 0 ? feet : 0x9FB4FF, accent = 0xC2A6FF;
				ground(p, w, level, viewers, tick, "circle", main, 1.45f + 0.15f * g, 200);
				if (g >= 2) spinRing(p, w, level, viewers, tick, "ward1", main, 1.75f, 0.5, 16, 110);
				if (g >= 3) spinRing(p, w, level, viewers, tick, "ward2", accent, 1.95f, 0.56, -26, -150);
				if (g >= 3) orbit(p, w, level, viewers, tick, "wardrunes", g >= 4 ? 4 : 2, 0.62, 0.76, 0.2f, 90,
						new String[]{"rune_a", "rune_b", "rune_c", "rune_d"}, new int[]{main, accent});
				if (every(tick, new int[]{0, 7, 5, 3, 2}[g])) ps.add(helix("mote", pick(main, accent, 0xE6ECFF), tick * 0.31, 0.6, 0.4, 0.08, h * 1.1,
						0.5, 36, 0.14f, 0.02f));
				if (g >= 4 && every(tick, 8)) ps.add(pop("hex", pick(main, accent), onBody(p, 0.3f, 0.85f, 0.36f), rnd(0.3f, 0.4f), 10, 0));
			}
			case "fire" -> {
				// Ember mantle: a ring of fire on a glow at your feet; flames licking up round your legs (never between you
				// and whoever looks: see frame); embers streaming up round you with a tail, one stream per grade from II;
				// at IV a mantle of flames on your shoulders and behind your neck, and heat rising off it
				int[] fire = soul ? new int[]{0x5FE6FF, 0x3FC6E8, 0x9FF4FF} : frost ? new int[]{0xBFE6FF, 0x8FD0FF, 0xFFFFFF}
						: new int[]{0xFF9A3C, 0xFFB64A, 0xFF6A1F};
				int[] embers = soul ? new int[]{0x9FF4FF, 0x5FE6FF} : frost ? new int[]{0xFFFFFF, 0xBFE6FF}
						: new int[]{0xFFE9A8, 0xFFB64A, 0xFF8A2E};
				feetFlames(p, w, level, viewers, tick, "flames", 6 + g, 0.47, 0.3f + 0.05f * g, fire);
				ground(p, w, level, viewers, tick, "pool", fire[0], 1.25f + 0.15f * g, 400);
				int lick = new int[]{0, 0, 2, 1, 1}[g];
				if (lick > 0 && every(tick, lick)) {
					for (int k = 0; k < (g >= 4 ? 2 : 1); k++) {
						frame.add(helix("flame", pick(fire), R.nextDouble() * Math.PI * 2, 0.47, 0.36, 0.05, h * new double[]{0, 0, 0.45, 0.6, 0.75}[g],
								0.3, 14, 0.38f + 0.04f * g, 0.1f));
					}
				}
				int streams = new int[]{0, 1, 1, 2, 3}[g];
				if (every(tick, new int[]{0, 5, 3, 2, 2}[g])) {
					for (int k = 0; k < streams; k++) {
						double a0 = tick * 0.22 + k * Math.PI * 2 / streams;
						int c = pick(embers);
						for (int t = 0; t < 4; t++) {
							ps.add(helix("ember", c, a0 - t * 0.18, 0.58, 0.3, 0.12 - t * 0.05, h * (1.3 - t * 0.07), 0.8, 30,
									new float[]{0.36f, 0.24f, 0.16f, 0.1f}[t], 0.03f));
						}
					}
				}
				if (g >= 3 && every(tick, 5)) {
					Vec3 from = new Vec3(rnd(-0.2f, 0.2f), h * rnd(0.55f, 0.8f), rnd(-0.2f, 0.2f));
					frame.add(line("haze", fire[0], from, from.add(0, 0.6, 0), 0.7f, 1.1f, 0, 0, 20));
				}
				if (g >= 4) mantle(p, w, level, viewers, tick, "mantle", fire);
				if (g >= 4 && every(tick, 4)) ps.add(pop("twinkle", soul ? 0xCFFAFF : frost ? 0xFFFFFF : 0xFFF1C2, onBody(p, 0.2f, 1.05f, 0.45f),
						rnd(0.22f, 0.3f), 6, 0.8f));
			}
			case "blast" -> {
				// Bulwark: heavy smoke curling at your feet, shards of stone circling and tumbling round your middle from II,
				// from III a thump now and then (a ring of dust running out over the ground) and sparks, at IV a dark ring
				int dust = feet != 0 ? feet : 0xB8B8C4;
				if (every(tick, g >= 3 ? 2 : 3)) {
					double a = R.nextDouble() * Math.PI * 2;
					Vec3 from = new Vec3(Math.cos(a) * 0.42, 0.08, Math.sin(a) * 0.42);
					int smoke = feet != 0 ? feet : pick(0xA4A4B0, 0x8C8C98, 0xB8B8C2);
					frame.add(new P("smoke", smoke, 10, List.of(new S(from, 0.35f, 0), new S(from.add(Math.cos(a) * 0.2, 0.2, Math.sin(a) * 0.2), 0.8f, 0.6f),
							new S(from.add(Math.cos(a) * 0.34, 0.34, Math.sin(a) * 0.34), 1.05f, 1.2f), new S(from.add(Math.cos(a) * 0.42, 0.44, Math.sin(a) * 0.42), 0.02f, 1.6f)), null));
				}
				if (g >= 2) shards(p, w, level, viewers, tick, "shards", g >= 4 ? 6 : g >= 3 ? 4 : 3, 0.78, 0.47, new int[]{0x8E8E9A, 0xA8A8B4, 0x74747E});
				if (g >= 3 && every(tick, g >= 4 ? 30 : 45)) {
					Quaternionf flat = new Quaternionf().rotateX(-Mth.HALF_PI);
					Vec3 at = new Vec3(0, 0.06, 0);
					ps.add(new P("thinring", dust, 2, List.of(new S(at, 0.4f, 0), new S(at, 1.3f, 0), new S(at, 2.1f, 0), new S(at, 2.7f, 0),
							new S(at, 3.1f, 0)), flat));
					for (int k = 0; k < 8; k++) {
						double a = k * Math.PI / 4 + rnd(-0.2f, 0.2f);
						Vec3 from = new Vec3(Math.cos(a) * 0.4, 0.1, Math.sin(a) * 0.4);
						ps.add(line("smoke", pick(0xA4A4B0, 0xB8B8C2), from, from.add(Math.cos(a) * 0.9, 0.15, Math.sin(a) * 0.9), 0.4f, 0.02f, 0, 1f, 16));
					}
				}
				if (g >= 3 && every(tick, 6)) ps.add(pop("twinkle", 0xFFA040, onBody(p, 0.35f, 0.6f, 0.78f), rnd(0.2f, 0.28f), 6, 0.6f));
				if (g >= 4) ground(p, w, level, viewers, tick, "crater", feet != 0 ? feet : 0x52525C, 2.3f, 260);
			}
			case "projectile" -> {
				// Windward: arcs of wind sweeping round you (more heights per grade), leaves caught in it from II, dust
				// swirling off your feet from III, at IV speed lines flying past and a ring of wind on the ground
				int wind = feet != 0 ? feet : 0xD8F5E6;
				spinRing(p, w, level, viewers, tick, "gust1", wind, 1.8f, 0.3, 8, 34, "thinarc");
				if (g >= 2) spinRing(p, w, level, viewers, tick, "gust2", 0xF2FFF8, 1.65f, 0.62, -12, -28, "thinarc");
				if (g >= 3) spinRing(p, w, level, viewers, tick, "gust3", 0xA8E6C8, 1.5f, 0.88, 6, 40, "thinarc");
				if (g >= 2 && every(tick, g >= 4 ? 6 : 9)) ps.add(helix("feather", pick(0xE8F7EE, 0xC8EBD6, 0xB6E3A8), tick * 0.5, 0.62, 0.7, 0.25,
						h * 1.0, 1.2, 39, 0.28f, 0.24f));
				if (g >= 3 && every(tick, 3)) ps.add(helix("mote", pick(0xE8F2EC, 0xCFE3D8), tick * 0.6, 0.35, 0.8, 0.05, 0.45, 0.6, 15, 0.13f, 0.02f));
				if (g >= 4 && every(tick, 7)) {
					double a = R.nextDouble() * Math.PI * 2, y = h * rnd(0.2f, 0.9f);
					Vec3 from = new Vec3(Math.cos(a) * 0.8, y, Math.sin(a) * 0.8);
					Vec3 to = new Vec3(Math.cos(a + 1.3) * 0.8, y + 0.05, Math.sin(a + 1.3) * 0.8);
					ps.add(line("streak", 0xF2FFF8, from, to, 0.55f, 0.3f, 0, 0, 6));
				}
				if (g >= 4) ground(p, w, level, viewers, tick, "windring", wind, 2.0f, 90);
			}
			case "thorns" -> {
				// Briar (Thorns on all four): two vines climbing round you in a double spiral (a steady stream of leaf
				// green), thorns standing out on them, a rose-red glint where one bites, bramble round your feet
				int vines = g >= 2 ? 2 : 1;
				for (int k = 0; k < vines; k++) {
					double a0 = tick * 0.21 + k * Math.PI;
					ps.add(helix("mote", pick(0x4E8A32, 0x3F7A2A, 0x6AA040), a0, 0.52, 0.4, 0.02, h * (0.7 + 0.15 * g), 0.9, 30, 0.17f, 0.1f));
					if (every(tick, 4)) ps.add(helix("thorn", pick(0x2F5A22, 0x9C2236), a0, 0.53, 0.41, 0.02, h * (0.7 + 0.15 * g), 0.9, 30,
							0.24f, 0.16f));
				}
				if (every(tick, new int[]{0, 10, 7, 4}[Math.min(3, g)])) ps.add(pop("twinkle", pick(0xFF4A5A, 0xE02A40), onBody(p, 0.1f, 0.85f, 0.48f),
						0.3f, 8, 0.5f));
				ground(p, w, level, viewers, tick, "bramble", 0x4E8A32, 1.9f, 300);
				if (g >= 3) spinRing(p, w, level, viewers, tick, "briarring", 0x3F7A2A, 1.6f, 0.04, 0, 240);
			}
			case "mending" -> {
				// Renewal (Mending on all four): orbs of experience drifting in to you from all round, bright green and
				// gold, and going in with a soft flash; a ring of green light turning slowly round your middle
				if (every(tick, 2)) {
					double a = R.nextDouble() * Math.PI * 2;
					double y = h * rnd(0.15f, 0.95f);
					int c = pick(0xB8FF6A, 0xD8FF6A, 0xF0E060);
					ps.add(helix("glow", c, a, 1.4, 0.2, y, h * 0.55, -0.35, 24, 0.3f, 0.08f));
					ps.add(helix("mote", 0xFFFFE0, a, 1.4, 0.2, y, h * 0.55, -0.35, 24, 0.12f, 0.03f));
				}
				if (every(tick, 10)) ps.add(pop("twinkle", 0xE8FFB0, new Vec3(rnd(-0.2f, 0.2f), h * rnd(0.4f, 0.7f), rnd(-0.2f, 0.2f)), 0.45f, 8, 0.5f));
				spinRing(p, w, level, viewers, tick, "renewal", 0x9CF060, 1.7f, 0.45, 6, 160);
				ground(p, w, level, viewers, tick, "pool", 0x6AD040, 1.6f, 400);
			}
			case "binding" -> {
				// Shackled (Binding on all four): heavy chains circling round your legs, violet smoke seeping off you and
				// sinking, a dark rune circle under you; dim violet, never bright: a curse takes light away
				orbit(p, w, level, viewers, tick, "chains", 5, 0.58, 0.22, 0.42f, 150, new String[]{"chain"}, new int[]{0xB48CFF, 0x8A64D0});
				if (every(tick, 2)) {
					Vec3 from = onBody(p, 0.3f, 0.9f, 0.34f);
					frame.add(line("smoke", pick(0x8A5AC8, 0x6A44A0, 0x9A70D8), from, from.add(rnd(-0.15f, 0.15f), -rnd(0.4f, 0.7f), rnd(-0.15f, 0.15f)),
							rnd(0.4f, 0.55f), 0.8f, 0, rnd(-0.5f, 0.5f), 26));
				}
				if (every(tick, 8)) ps.add(pop("twinkle", 0xC8A8FF, onBody(p, 0.1f, 0.9f, 0.42f), 0.28f, 8, 0.6f));
				ground(p, w, level, viewers, tick, "circle", 0x5A3A90, 1.9f, -260);
			}
			case "vanishing" -> {
				// Fading (Vanishing on all four): you come apart at the edges: pale flakes of you drift off and wink out,
				// a ghostly haze clings to your outline, a glint now and then that shrinks to nothing
				if (every(tick, 1)) {
					for (int k = 0; k < 2; k++) {
						Vec3 from = onBody(p, 0.05f, 1.0f, 0.36f);
						Vec3 away = new Vec3(from.x * 1.2, rnd(0.15f, 0.45f), from.z * 1.2);
						ps.add(line("mote", pick(0xF0E8FF, 0xD8C8FF, 0xFFFFFF), from, from.add(away), rnd(0.2f, 0.3f), 0.01f, 0, 0, 22));
					}
				}
				if (every(tick, 4)) {
					Vec3 from = onBody(p, 0.1f, 0.9f, 0.32f);
					frame.add(line("haze", 0xC8B8F0, from, from.add(from.x * 0.4, 0.3, from.z * 0.4), 0.5f, 0.8f, 0, 0, 20));
				}
				if (every(tick, 6)) ps.add(pop("twinkle", 0xF0E8FF, onBody(p, 0.1f, 1.0f, 0.42f), 0.36f, 10, 0.6f));
			}
			default -> {
			}
		}
		if (!frame.isEmpty()) {
			for (ServerPlayer v : viewers) {
				// your own camera: behind you (third person's usual view)
				Vec3 d = v == p ? p.getLookAngle().scale(-1) : v.position().subtract(p.position());
				double va = Math.atan2(d.z, d.x);
				List<P> mine = new ArrayList<>();
				for (P q : frame) {
					Vec3 at = q.states().get(q.states().size() / 2).pos();
					if (at.horizontalDistanceSqr() < 0.01) {
						mine.add(q);
						continue;
					}
					double diff = Math.abs(Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(at.z, at.x) - va)));
					if (diff > 55) mine.add(q);
				}
				emit(level, p.position(), List.of(v), mine);
			}
		}
		// what the pieces add, in the same motion
		if (w.extras.contains("thorns")) {
			orbit(p, w, level, viewers, tick, "barbs", 3, 0.66, 0.4, 0.3f, 130, new String[]{"thorn"}, new int[]{0x9C2236, 0x5E8C3A});
			if (every(tick, 11)) ps.add(pop("twinkle", 0xFF6A6A, onBody(p, 0.2f, 0.8f, 0.5f), 0.22f, 6, 0.5f));
		}
		if (w.extras.contains("feather") && every(tick, 12)) {
			double a = R.nextDouble() * Math.PI * 2;
			Vec3 from = new Vec3(Math.cos(a) * 0.5, h * 0.55, Math.sin(a) * 0.5);
			ps.add(new P("feather", pick(0xFFFFFF, 0xEDEDED), 12, List.of(new S(from, 0.24f, 0.3f), new S(from.add(0.15, -0.3, 0.05), 0.27f, -0.4f),
					new S(from.add(-0.05, -0.62, 0.12), 0.27f, 0.4f), new S(from.add(0.1, -0.92, 0.0), 0.02f, -0.2f)), null));
		}
		if (frost && every(tick, 6)) {
			Vec3 from = new Vec3(rnd(-0.55f, 0.55f), h * rnd(0.5f, 1.0f), rnd(-0.55f, 0.55f));
			ps.add(line("flake", pick(0xF2FAFF, 0xBFE6FF), from, from.add(rnd(-0.15f, 0.15f), -rnd(0.7f, 1.0f), rnd(-0.15f, 0.15f)), 0.16f, 0.06f,
					0, rnd(-2f, 2f), 30));
		}
		if (soul && every(tick, 8)) {
			Vec3 from = onBody(p, 0.0f, 0.1f, 0.4f);
			ps.add(line("wisp", pick(0x5FE6FF, 0x9FF4FF), from, from.add(rnd(-0.1f, 0.1f), rnd(0.6f, 0.9f), rnd(-0.1f, 0.1f)), 0.22f, 0.04f, 0, 0, 26));
		}
	}

	/**
	 * What the armour's material adds to its set: chainmail a chain link glinting, copper a green spark (it conducts),
	 * iron a steel glint, gold golden motes rising, diamond a crisp sparkle, netherite dark smoke and red sparks at
	 * your feet, leather a little warm dust.
	 */
	private static void materialTouch(ServerPlayer p, Wearer w, int tick, List<P> ps) {
		switch (w.material) {
			case "chainmail" -> {
				if (every(tick, 9)) ps.add(pop("chain", 0xC8CCD6, onBody(p, 0.25f, 0.85f, 0.42f), 0.3f, 10, rnd(-0.5f, 0.5f)));
			}
			case "copper" -> {
				if (every(tick, 8)) ps.add(pop("spark", pick(0x6FE8C0, 0xE8A060), onBody(p, 0.2f, 0.9f, 0.42f), 0.28f, 6, 0.6f));
			}
			case "iron" -> {
				if (every(tick, 10)) ps.add(pop("twinkle", 0xE8ECF4, onBody(p, 0.2f, 0.9f, 0.4f), 0.26f, 8, 0.5f));
			}
			case "golden" -> {
				if (every(tick, 4)) {
					Vec3 from = onBody(p, 0.1f, 0.6f, 0.45f);
					ps.add(line("mote", pick(0xFFE27A, 0xFFF2B0), from, from.add(0, rnd(0.6f, 0.9f), 0), 0.16f, 0.02f, 0, 0, 24));
				}
				if (every(tick, 7)) ps.add(pop("twinkle", 0xFFE9A0, onBody(p, 0.2f, 0.95f, 0.42f), 0.3f, 8, 0.6f));
			}
			case "diamond" -> {
				if (every(tick, 8)) ps.add(pop("twinkle", pick(0xCFFAFF, 0xFFFFFF), onBody(p, 0.2f, 0.95f, 0.42f), 0.3f, 8, 0.8f));
			}
			case "netherite" -> {
				if (every(tick, 4)) {
					double a = R.nextDouble() * Math.PI * 2;
					Vec3 from = new Vec3(Math.cos(a) * 0.4, 0.05, Math.sin(a) * 0.4);
					ps.add(line("smoke", pick(0x2A2226, 0x3A2A2E), from, from.add(0, 0.35, 0), 0.4f, 0.8f, 0, 0.8f, 22));
				}
				if (every(tick, 6)) ps.add(pop("spark", 0xFF5030, onBody(p, 0.0f, 0.3f, 0.45f), 0.24f, 6, 0.6f));
			}
			case "leather" -> {
				if (every(tick, 12)) ps.add(pop("mote", 0xD8B888, onBody(p, 0.05f, 0.5f, 0.4f), 0.16f, 10, 0));
			}
			default -> {
			}
		}
	}

	/** A particle that goes round and up you along a helix (one motion: a stream of these looks like a spiral). */
	static P helix(String sprite, int rgb, double a0, double r0, double r1, double y0, double y1, double turns, int ticks, float s0, float s1) {
		int n = Math.max(2, ticks / 3);
		List<S> st = new ArrayList<>();
		for (int k = 0; k <= n; k++) {
			double t = (double) k / n;
			double a = a0 + turns * Math.PI * 2 * t, r = r0 + (r1 - r0) * t;
			float sc = k == 0 ? 0.01f : (float) (s0 + (s1 - s0) * t);
			st.add(new S(new Vec3(Math.cos(a) * r, y0 + (y1 - y0) * t, Math.sin(a) * r), sc, 0));
		}
		return new P(sprite, rgb, 3, st, null);
	}

	/** A thin ring of light round you, tilted, turning slowly about the up axis (a gyroscope's ring). */
	private static void spinRing(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, int tick, String key, int rgb, float size,
			double height, float tiltDeg, int period) {
		spinRing(p, w, level, viewers, tick, key, rgb, size, height, tiltDeg, period, "thinring");
	}

	private static void spinRing(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, int tick, String key, int rgb, float size,
			double height, float tiltDeg, int period, String sprite) {
		Ring r = w.rings.get(key);
		if (r == null) {
			r = new Ring();
			ItemDisplayElement e = element(sprite, rgb, true);
			e.setScale(new Vector3f(size));
			e.setOffset(new Vec3(0, p.getBbHeight() * height, 0));
			e.setInterpolationDuration(Math.abs(period) < 60 ? 3 : 5);
			e.setLeftRotation(ringTurn(tick, tiltDeg, period));
			r.holder.addElement(e);
			r.els.add(e);
			show(p, w, level, viewers, key, r);
		}
		int step = Math.abs(period) < 60 ? 3 : 5;
		if (tick % step == 0) {
			ItemDisplayElement e = r.els.getFirst();
			e.setLeftRotation(ringTurn(tick + step, tiltDeg, period));
			e.startInterpolation();
		}
		r.holder.tick();
	}

	private static Quaternionf ringTurn(int tick, float tiltDeg, int period) {
		float a = (float) (tick * (Math.PI * 2 / period));
		return new Quaternionf().rotateY(a).rotateX(-Mth.HALF_PI + tiltDeg * Mth.DEG_TO_RAD);
	}

	/** Small flames round your feet (like {@link #flames}, but a size of your choosing and a set's colours). */
	private static void feetFlames(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, int tick, String key, int n, double radius,
			float size, int[] colours) {
		Ring r = w.rings.get(key);
		if (r == null) {
			r = new Ring();
			for (int i = 0; i < n; i++) {
				ItemDisplayElement e = element("flame", colours[i % colours.length], false);
				double a = i * Math.PI * 2 / n;
				e.setOffset(new Vec3(Math.cos(a) * radius, size * 0.42, Math.sin(a) * radius));
				e.setScale(new Vector3f(size));
				e.setInterpolationDuration(4);
				r.holder.addElement(e);
				r.els.add(e);
			}
			show(p, w, level, viewers, key, r);
		}
		if (tick % 4 == 0) {
			for (ItemDisplayElement e : r.els) {
				float s = size * rnd(0.75f, 1.2f);
				e.setScale(new Vector3f(s * 0.85f, s, s));
				e.setTranslation(new Vector3f(0, (s - size) * 0.4f, 0));
				e.startInterpolation();
			}
		}
		r.holder.tick();
	}

	/**
	 * The top of the ember mantle: flames on top of both shoulders and three behind your neck, rising and flickering,
	 * turning with your body. From the front they rise round your head; from behind they sit on your shoulders.
	 */
	private static void mantle(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, int tick, String key, int[] colours) {
		double[][] spots = {{0.3, -0.02, 0}, {-0.3, -0.02, 0}, {0.13, 0.2, 0.07}, {-0.13, 0.2, 0.07}, {0, 0.24, 0.14}};
		Ring r = w.rings.get(key);
		if (r == null) {
			r = new Ring();
			for (int i = 0; i < spots.length; i++) {
				ItemDisplayElement e = element("flame", colours[i % colours.length], false);
				e.setScale(new Vector3f(0.4f));
				e.setTeleportDuration(2);
				e.setInterpolationDuration(3);
				e.setOffset(onShoulders(p, spots[i]));
				r.holder.addElement(e);
				r.els.add(e);
			}
			ItemDisplayElement glow = element("haze", colours[0], false);
			glow.setScale(new Vector3f(1.3f));
			glow.setTeleportDuration(2);
			glow.setOffset(onShoulders(p, new double[]{0, 0.18, 0.1}));
			r.holder.addElement(glow);
			r.els.add(glow);
			show(p, w, level, viewers, key, r);
		}
		for (int i = 0; i < r.els.size(); i++) {
			ItemDisplayElement e = r.els.get(i);
			boolean isGlow = i == spots.length;
			e.setOffset(onShoulders(p, isGlow ? new double[]{0, 0.18, 0.1} : spots[i]));
			if (!isGlow && tick % 3 == 0) {
				float sc = rnd(0.32f, 0.5f) * (i >= 2 ? 1.1f : 1f);
				e.setScale(new Vector3f(sc * 0.85f, sc, sc));
				e.setTranslation(new Vector3f(0, sc * 0.4f, 0));
				e.startInterpolation();
			}
		}
		r.holder.tick();
	}

	/** A spot on your shoulders: {right, up from the top of the arms, back}, turning with your body. */
	private static Vec3 onShoulders(ServerPlayer p, double[] at) {
		float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		Vec3 back = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw));
		return right.scale(at[0]).add(back.scale(at[2])).add(0, p.getBbHeight() * 0.835 + at[1], 0);
	}

	/** Shards of stone circling your middle, bobbing and tumbling. */
	private static void shards(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, int tick, String key, int n, double radius,
			double height, int[] colours) {
		Ring r = w.rings.get(key);
		if (r == null) {
			r = new Ring();
			for (int i = 0; i < n; i++) {
				ItemDisplayElement e = element("shard", colours[i % colours.length], false);
				e.setScale(new Vector3f(0.26f + 0.04f * (i % 3)));
				e.setTeleportDuration(3);
				e.setInterpolationDuration(3);
				e.setOffset(orbitSpot(p, i, n, tick, radius, height, 140));
				r.holder.addElement(e);
				r.els.add(e);
			}
			show(p, w, level, viewers, key, r);
		}
		if (tick % 3 == 0) {
			for (int i = 0; i < r.els.size(); i++) {
				ItemDisplayElement e = r.els.get(i);
				Vec3 at = orbitSpot(p, i, n, tick + 3, radius, height, 140);
				e.setOffset(at.add(0, Math.sin((tick + i * 17) * 0.09) * 0.08, 0));
				e.setLeftRotation(new Quaternionf().rotateZ((tick + 3) * 0.05f * (i % 2 == 0 ? 1 : -1) + i));
				e.startInterpolation();
			}
		}
		r.holder.tick();
	}

	/**
	 * Curse of Binding: something holds on to you. Dark violet smoke seeps off you, chain links flicker up and fade, a
	 * violet glint now and then, and a pool of shadow under your feet. No light: a curse takes it away.
	 */
	private static void curse(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, int tick, List<P> ps) {
		if (every(tick, 3)) {
			Vec3 from = onBody(p, 0.1f, 0.8f, 0.3f);
			ps.add(line("smoke", pick(0x4A2A6B, 0x3A2150, 0x5B3384), from, from.add(rnd(-0.2f, 0.2f), rnd(0.4f, 0.7f), rnd(-0.2f, 0.2f)),
					rnd(0.55f, 0.75f), 1.1f, 0, rnd(-0.5f, 0.5f), 30));
		}
		if (every(tick, 5)) {
			// a chain flickers up with a violet glow behind it
			Vec3 at = onBody(p, 0.3f, 0.8f, 0.4f);
			ps.add(pop("haze", 0x7A44C2, at, 0.9f, 14, 0));
			ps.add(pop("chain", pick(0xC4A2FF, 0xA77DF0), at, rnd(0.6f, 0.75f), 14, rnd(-0.6f, 0.6f)));
		}
		if (every(tick, 4)) ps.add(pop("twinkle", 0xD6B4FF, onBody(p, 0.1f, 0.9f, 0.4f), rnd(0.35f, 0.5f), 8, 0.8f));
		ground(p, w, level, viewers, tick, "shadow", 0x1A0F24, 1.8f, 400);
	}

	/**
	 * Curse of Vanishing: you're fading at the edges. Pale motes come off you and wink out, now and then a glint that
	 * shrinks to nothing.
	 */
	private static void vanish(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, int tick, List<P> ps) {
		if (every(tick, 1)) {
			Vec3 from = onBody(p, 0.05f, 0.95f, 0.42f);
			ps.add(line("mote", pick(0xE9DDFF, 0xC9B6F2, 0xFFFFFF), from, from.add(rnd(-0.35f, 0.35f), rnd(0.1f, 0.5f), rnd(-0.35f, 0.35f)),
					rnd(0.45f, 0.6f), 0.0f, 0, 0, 14 + R.nextInt(8)));
		}
		if (every(tick, 7)) ps.add(pop("twinkle", 0xF1E8FF, onBody(p, 0.1f, 0.9f, 0.4f), 0.4f, 10, 1.2f));
		if (every(tick, 5)) {
			Vec3 from = onBody(p, 0.2f, 0.8f, 0.2f);
			ps.add(line("haze", 0x9C86D8, from, from.add(0, 0.3, 0), 0.9f, 0.1f, 0, 0, 16));
		}
	}

	// ---------------------------------------------------------------- a sword in your hand

	/** Where the sword is in your right hand, for a spot along the blade (0 the hilt, 1 the tip), relative to you. */
	static Vec3 blade(ServerPlayer p, double t) {
		float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 look = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		Vec3 hand = right.scale(0.38).add(0, 0.68, 0).add(look.scale(0.08));
		Vec3 dir = look.scale(0.72).add(0, 0.55, 0).normalize();
		return hand.add(dir.scale(0.15 + t * 0.7));
	}

	/**
	 * Around the blade while you hold it. Fire: embers come off the blade and now and then a little flame licks up it,
	 * with a warm light at your hand. Sharpness: now and then a glint runs from the hilt to the tip. Smite: golden motes
	 * rise off the blade, a soft light.
	 */
	/** Where the sword is on your own screen in first person, for a spot along the blade (0 the hilt, 1 the tip). */
	static Vec3 bladeFp(ServerPlayer p, double t) {
		Vec3 fwd = p.getViewVector(1.0f);
		float yaw = p.getYRot() * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		Vec3 up = right.cross(fwd).normalize();
		Vec3 eye = new Vec3(0, p.getEyeHeight(), 0);
		return eye.add(fwd.scale(0.72 + 0.2 * t)).add(right.scale(0.74 - 0.1 * t)).add(up.scale(-0.5 + 0.8 * t));
	}

	private static void blade(ServerPlayer p, Wearer w, int tick, int g, List<P> out, java.util.function.DoubleFunction<Vec3> at, float k) {
		List<P> ps = new ArrayList<>();
		bladeFx(p, w, tick, g, ps, at);
		// the same, smaller (on your own screen it's close by)
		for (P q : ps) {
			List<S> st = new ArrayList<>();
			Vec3 o = q.states().getFirst().pos();
			for (S x : q.states()) st.add(new S(o.add(x.pos().subtract(o).scale(k)), x.scale() * k, x.rot()));
			out.add(new P(q.sprite(), q.rgb(), q.step(), st, q.base()));
		}
	}

	private static void bladeFx(ServerPlayer p, Wearer w, int tick, int g, List<P> ps, java.util.function.DoubleFunction<Vec3> blade) {
		switch (w.kind) {
			case "blade_fire" -> {
				if (every(tick, g >= 2 ? 2 : 4)) {
					Vec3 at = blade.apply(rnd(0.1f, 1.0f));
					ps.add(line("ember", pick(0xFFE9A8, 0xFFB64A, 0xFF7A2A), at, at.add(rnd(-0.08f, 0.08f), rnd(0.3f, 0.55f), rnd(-0.08f, 0.08f)),
							rnd(0.22f, 0.32f), 0.02f, 0, 0, 12 + R.nextInt(8)));
				}
				if (every(tick, g >= 2 ? 5 : 9)) {
					Vec3 at = blade.apply(rnd(0.2f, 0.9f));
					ps.add(new P("flame", pick(0xFF9A3C, 0xFFB64A), 4, List.of(new S(at, 0.05f, 0), new S(at.add(0, 0.08, 0), rnd(0.22f, 0.3f), 0),
							new S(at.add(0, 0.2, 0), 0.04f, 0)), null));
				}
				if (every(tick, 6)) ps.add(pop("haze", 0xFF8A2E, blade.apply(0.5), 0.6f, 12, 0));
			}
			case "blade_sharp" -> {
				// a glint that runs along the edge: sparkles that light up one after the other, hilt to tip
				int every = g >= 5 ? 24 : g >= 3 ? 36 : 50;
				if (tick % every == 0) {
					for (int k = 0; k < 5; k++) {
						Vec3 at = blade.apply(0.2 + k * 0.2);
						List<S> st = new ArrayList<>();
						for (int q = 0; q < k; q++) st.add(new S(at, 0.01f, 0));
						st.add(new S(at, 0.32f + g * 0.02f, 0.4f));
						st.add(new S(at, 0.01f, 0.8f));
						ps.add(new P("twinkle", 0xF2F7FF, 1, st, null));
					}
				}
			}
			case "blade_smite" -> {
				// Radiance: a soft white-gold light. A slow twinkle at the tip; pale motes that drift up; at III a glow
				// round the blade; at V little lights that run up and down it
				if (every(tick, g >= 3 ? 14 : 30)) ps.add(pop("twinkle", 0xFFF6DA, blade.apply(1.0), 0.32f + g * 0.03f, 10, 0.5f));
				if (g >= 2 && every(tick, g >= 4 ? 4 : 8)) {
					Vec3 at = blade.apply(rnd(0.2f, 1.0f));
					ps.add(line("mote", pick(0xFFF2C8, 0xFFFFFF, 0xFFE6A0), at, at.add(rnd(-0.06f, 0.06f), rnd(0.25f, 0.45f), rnd(-0.06f, 0.06f)),
							rnd(0.2f, 0.28f), 0.02f, 0, 0, 22));
				}
				if (g >= 3 && every(tick, 10)) ps.add(pop("haze", 0xFFE6A0, blade.apply(0.6), 0.55f + g * 0.05f, 16, 0));
				if (g >= 5 && every(tick, 16)) {
					for (int k = 0; k < 4; k++) {
						Vec3 at = blade.apply(0.25 + k * 0.25);
						List<S> st = new ArrayList<>();
						for (int q = 0; q < k * 2; q++) st.add(new S(at, 0.01f, 0));
						st.add(new S(at, 0.26f, 0.3f));
						st.add(new S(at, 0.01f, 0.6f));
						ps.add(new P("twinkle", 0xFFFBEA, 1, st, null));
					}
				}
			}
			case "blade_bane" -> {
				// Venom: green drops that run off the blade and fall; more per grade, then bubbles and a green mist
				int[] drip = {0, 30, 16, 10, 6, 4};
				if (every(tick, drip[Math.min(5, g)])) {
					Vec3 at = blade.apply(rnd(0.3f, 0.9f));
					ps.add(new P("mote", pick(0x7EE85C, 0x5BCB3E, 0xA6F27E), 6, List.of(new S(at, 0.16f, 0), new S(at.add(0, -0.12, 0), 0.22f, 0),
							new S(at.add(0, -0.55, 0), 0.12f, 0), new S(at.add(0, -0.7, 0), 0.01f, 0)), null));
				}
				if (g >= 3 && every(tick, 12)) ps.add(pop("haze", 0x4FB83A, blade.apply(0.6), 0.6f, 16, 0));
				if (g >= 4 && every(tick, 8)) {
					Vec3 at = blade.apply(rnd(0.3f, 1.0f));
					ps.add(line("bubble", 0xA6F27E, at, at.add(0, rnd(0.2f, 0.35f), 0), 0.16f, 0.02f, 0, 0, 14));
				}
				if (g >= 5 && every(tick, 8)) {
					Vec3 at = blade.apply(rnd(0.3f, 0.9f));
					ps.add(line("smoke", 0x3E8C2C, at, at.add(rnd(-0.1f, 0.1f), 0.2, rnd(-0.1f, 0.1f)), 0.3f, 0.6f, 0, 0.4f, 24));
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- things that stay on a wearer

	/** Shows a ring that's ready (its sprites added) to the players nearby, following the wearer. */
	private static Ring show(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, String key, Ring r) {
		new ManualAttachment(r.holder, level, p::position);
		for (ServerPlayer v : viewers) r.holder.startWatching(v);
		w.rings.put(key, r);
		return r;
	}

	/** Sprites circling round you at a height (0 feet, 1 head). */
	private static void orbit(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, int tick, String key, int n,
			double radius, double height, float scale, int period, String[] sprites, int[] colours) {
		Ring r = w.rings.get(key);
		if (r == null) {
			r = new Ring();
			for (int i = 0; i < n; i++) {
				// a soft glow behind each, so it reads as light
				ItemDisplayElement glow = element("haze", colours[i % colours.length], false);
				glow.setScale(new Vector3f(scale * 2.2f));
				glow.setTeleportDuration(3);
				glow.setOffset(orbitSpot(p, i, n, tick, radius, height, period));
				r.holder.addElement(glow);
				r.els.add(glow);
				ItemDisplayElement e = element(sprites[i % sprites.length], colours[i % colours.length], false);
				e.setScale(new Vector3f(scale));
				e.setTeleportDuration(3);
				e.setOffset(orbitSpot(p, i, n, tick, radius, height, period));
				r.holder.addElement(e);
				r.els.add(e);
			}
			show(p, w, level, viewers, key, r);
		}
		if (tick % 3 == 0) {
			for (int i = 0; i < r.els.size(); i++) r.els.get(i).setOffset(orbitSpot(p, i / 2, n, tick + 3, radius, height, period));
		}
		r.holder.tick();
	}

	private static Vec3 orbitSpot(ServerPlayer p, int i, int n, int tick, double radius, double height, int period) {
		double a = tick * (Math.PI * 2 / period) + i * Math.PI * 2 / n;
		return new Vec3(Math.cos(a) * radius, p.getBbHeight() * height + Math.sin(a * 2) * 0.05, Math.sin(a) * radius);
	}

	/** A circle flat on the ground under you, slowly turning. */
	private static void ground(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, int tick, String key, int rgb, float size,
			int period) {
		Ring r = w.rings.get(key);
		if (r == null) {
			r = new Ring();
			ItemDisplayElement e = element(key.equals("icering") ? "ring" : key.equals("shadow") ? "haze" : key.equals("pool") ? "glow"
					: key.equals("crater") || key.equals("windring") || key.equals("bramble") ? "thinring" : "circle", rgb, true);
			e.setScale(new Vector3f(size));
			e.setOffset(new Vec3(0, 0.04, 0));
			e.setInterpolationDuration(10);
			e.setLeftRotation(new Quaternionf().rotateX(-Mth.HALF_PI));
			r.holder.addElement(e);
			r.els.add(e);
			// a pool of glow under it
			ItemDisplayElement pool = element("haze", rgb, true);
			pool.setScale(new Vector3f(size * 1.25f));
			pool.setOffset(new Vec3(0, 0.03, 0));
			pool.setLeftRotation(new Quaternionf().rotateX(-Mth.HALF_PI));
			r.holder.addElement(pool);
			show(p, w, level, viewers, key, r);
		}
		if (tick % 10 == 0) {
			float a = (float) (tick * (Math.PI * 2 / period));
			ItemDisplayElement e = r.els.getFirst();
			e.setLeftRotation(new Quaternionf().rotateX(-Mth.HALF_PI).rotateZ(a + (float) (Math.PI * 2 / period * 10)));
			e.startInterpolation();
		}
		r.holder.tick();
	}

	/** Flame tongues (an animated sprite) round your feet, each flickering in size. */
	private static void flames(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, int tick, String key, int n, double radius,
			int[] colours) {
		Ring r = w.rings.get(key);
		if (r == null) {
			r = new Ring();
			for (int i = 0; i < n; i++) {
				ItemDisplayElement e = element("flame", colours[i % colours.length], false);
				double a = i * Math.PI * 2 / n;
				e.setOffset(new Vec3(Math.cos(a) * radius, 0.22, Math.sin(a) * radius));
				e.setScale(new Vector3f(0.5f));
				e.setInterpolationDuration(4);
				r.holder.addElement(e);
				r.els.add(e);
			}
			ItemDisplayElement glow = element("haze", colours[0], false);
			glow.setScale(new Vector3f(2.2f));
			glow.setOffset(new Vec3(0, 0.35, 0));
			r.holder.addElement(glow);
			show(p, w, level, viewers, key, r);
		}
		if (tick % 4 == 0) {
			for (ItemDisplayElement e : r.els) {
				float s = rnd(0.38f, 0.62f);
				e.setScale(new Vector3f(s * 0.85f, s, s));
				e.setTranslation(new Vector3f(0, (s - 0.5f) * 0.4f, 0));
				e.startInterpolation();
			}
		}
		r.holder.tick();
	}

	// ---------------------------------------------------------------- weapons: on a hit

	/** "sharpness:3", "fire_aspect:2", "smite:5", "knockback:2", "looting:3": a hit on something in front of you. */
	static void hit(ServerPlayer p, String spec) {
		String[] parts = spec.split(":");
		String kind = parts[0];
		int g = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
		ServerLevel level = (ServerLevel) p.level();
		List<ServerPlayer> viewers = viewers(p);
		float yaw = p.getYRot() * Mth.DEG_TO_RAD;
		Vec3 look = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 side = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
		Vec3 at = p.position().add(look.scale(1.4)).add(0, 1.0, 0);
		Quaternionf facing = new Quaternionf().rotateY(-yaw + Mth.PI);
		List<P> ps = new ArrayList<>();
		switch (kind) {
			case "sharpness" -> {
				// A steel slash: bigger and brighter per grade; III+ a second arc; V a spark burst and a flash
				float size = 1.2f + g * 0.25f;
				Quaternionf rot = new Quaternionf(facing).rotateZ(0.55f);
				ps.add(new P("arc", 0xDDE9FF, 5, List.of(new S(Vec3.ZERO, size * 0.4f, 0), new S(Vec3.ZERO, size, -0.9f)), rot));
				ps.add(new P("arc", 0xFFFFFF, 4, List.of(new S(Vec3.ZERO, size * 0.3f, 0), new S(Vec3.ZERO, size * 0.8f, -0.9f)), rot));
				if (g >= 3) {
					Quaternionf rot2 = new Quaternionf(facing).rotateZ(-0.7f);
					ps.add(new P("arc", 0xBFD4FF, 5, List.of(new S(Vec3.ZERO, size * 0.3f, 0), new S(Vec3.ZERO, size * 0.85f, 0.9f)), rot2));
				}
				for (int k = 0; k < g * 2; k++) ps.add(pop("twinkle", 0xF2F7FF, new Vec3(rnd(-0.6f, 0.6f), rnd(-0.4f, 0.4f), rnd(-0.6f, 0.6f)),
						rnd(0.4f, 0.6f), 6 + R.nextInt(4), 0.8f));
				if (g >= 5) {
					sparks(ps, 12, 0xE6F0FF, side, 1.2);
					flash(level, at, viewers, 10, 4);
				}
			}
			case "swing_fire", "swing_sharp", "swing_smite", "swing_bane" -> {
				// The blade's tip, through the swing: from up at your right, down across your front. Sparks light up along
				// that path one after the other (the blade drawing them); more and bigger per grade, and from III a flash
				// where it lands
				int c = switch (kind) {
					case "swing_fire" -> 0xFFB24A;
					case "swing_smite" -> 0xFFF0C0;
					case "swing_bane" -> 0x7EE85C;
					default -> 0xE6EEFF;
				};
				String spark = kind.equals("swing_fire") ? "ember" : kind.equals("swing_bane") ? "mote" : "twinkle";
				int top = kind.equals("swing_fire") ? 2 : 5;
				float f = g / (float) top;
				Vec3 shoulder = side.scale(-0.36).add(0, 1.38, 0);
				Vec3 base = p.position().subtract(at);
				int n = 4 + Math.round(f * 6);
				// the arc as others see it (round your body), and as you see it (across your screen, top right to bottom left)
				List<P> arc = new ArrayList<>(), arcFp = new ArrayList<>();
				Vec3 fwd = p.getViewVector(1.0f), rightV = side.scale(-1), upV = rightV.cross(fwd).normalize();
				Vec3 eye = new Vec3(0, p.getEyeHeight(), 0);
				for (int k = 0; k < n; k++) {
					double s0 = k / (double) (n - 1);
					double a = Math.toRadians(65 - s0 * 110);
					Vec3 tip = shoulder.add(look.scale(Math.cos(a) * 1.05)).add(0, Math.sin(a) * 0.9, 0).add(side.scale(-0.35 + s0 * 0.6));
					float sz = 0.2f + f * 0.16f;
					arc.add(new P(spark, k % 2 == 0 ? 0xFFFFFF : c, 1, sweepStates(base.add(tip), look, k, n, sz), null));
					Vec3 fp = eye.add(fwd.scale(0.85)).add(rightV.scale(0.7 - 0.62 * s0)).add(upV.scale(0.3 - 0.6 * s0));
					arcFp.add(new P(spark, k % 2 == 0 ? 0xFFFFFF : c, 1, sweepStates(fp, fwd.scale(0.4), k, n, sz * 0.5f), null));
				}
				List<ServerPlayer> others = new ArrayList<>(viewers);
				others.remove(p);
				emit(level, at, others, arc);
				emit(level, p.position(), List.of(p), arcFp);
				if (f >= 0.5f) {
					ps.add(pop("twinkle", 0xFFFFFF, Vec3.ZERO, 0.45f + f * 0.25f, 4, 0.4f));
					for (int k = 0; k < 2 + Math.round(f * 8); k++) {
						Vec3 d = side.scale(rnd(-0.5f, 0.5f)).add(look.scale(rnd(0.0f, 0.3f))).add(0, rnd(-0.2f, 0.4f), 0);
						ps.add(line(spark, pick(c, 0xFFFFFF), Vec3.ZERO, d, rnd(0.18f, 0.26f), 0.01f, 0, 0, 5 + R.nextInt(4)));
					}
				}
				switch (kind) {
					case "swing_fire" -> flash(level, at, viewers, 8 + g * 2, 4);
					case "swing_smite" -> smiteHit(ps, level, at, viewers, g, facing, look, side);
					case "swing_bane" -> {
						// green drops splash off the target
						for (int k = 0; k < 2 + g * 2; k++) {
							Vec3 out = new Vec3(rnd(-0.6f, 0.6f), rnd(0.1f, 0.6f), rnd(-0.6f, 0.6f));
							ps.add(new P("mote", pick(0x7EE85C, 0xA6F27E, 0x4FB83A), 4, List.of(new S(Vec3.ZERO, 0.12f, 0), new S(out, 0.24f, 0),
									new S(out.add(out.x * 0.3, -0.9, out.z * 0.3), 0.08f, 0)), null));
						}
						if (g >= 5) {
							Quaternionf flat = new Quaternionf().rotateX(-Mth.HALF_PI);
							Vec3 foot = new Vec3(0, -0.95, 0);
							ps.add(new P("smoke", 0x3E8C2C, 10, List.of(new S(foot, 0.5f, 0), new S(foot, 2.6f, 0.6f), new S(foot, 2.8f, 1.0f)), flat));
						}
					}
					default -> {
					}
				}
			}
			case "trail_old" -> {
				// A blade's trail: a thin crescent that sweeps through, with two fainter ones a tick behind it
				int c = g >= 9 ? 0xFFB24A : 0xE6EEFF;
				float sz = 0.8f + Math.min(g, 5) * 0.06f;
				Vec3 mid = look.scale(-0.3).add(0, -0.15, 0);
				Quaternionf rot = new Quaternionf(facing).rotateX(0.35f);
				for (int k = 0; k < 3; k++) {
					List<S> st = new ArrayList<>();
					for (int t = 0; t < k; t++) st.add(new S(mid, 0.01f, 1.3f));
					float peak = sz * (1 - k * 0.12f);
					st.add(new S(mid, peak, 1.3f));
					st.add(new S(mid, peak, 0.2f));
					st.add(new S(mid, peak * 0.9f, -0.9f));
					st.add(new S(mid, 0.01f, -1.3f));
					ps.add(new P("thinarc", k == 0 ? 0xFFFFFF : c, 1, st, rot));
				}
				for (int k = 0; k < g; k++) ps.add(pop("twinkle", 0xF2F7FF, side.scale(rnd(-0.5f, 0.5f)).add(look.scale(-0.2)).add(0, rnd(-0.3f, 0.2f), 0),
						rnd(0.25f, 0.4f), 4, 0.6f));
			}
			case "trail" -> {
				// A blade's trail: a thin crescent that sweeps through over a few ticks, a fainter one just behind it
				int c = g >= 9 ? 0xFFB24A : 0xE6EEFF;
				float sz = 0.9f + Math.min(g, 5) * 0.05f;
				Vec3 mid = look.scale(-0.3).add(0, -0.15, 0);
				Quaternionf rot = new Quaternionf(facing).rotateX(0.35f);
				ps.add(new P("thinarc", 0xFFFFFF, 2, List.of(new S(mid, sz * 0.7f, 1.4f), new S(mid, sz, 0.5f), new S(mid, sz, -0.4f),
						new S(mid, 0.02f, -1.2f)), rot));
				ps.add(new P("thinarc", c, 2, List.of(new S(mid, 0.02f, 1.6f), new S(mid, sz * 0.9f, 1.0f), new S(mid, sz * 0.85f, 0.1f),
						new S(mid, 0.02f, -0.8f)), rot));
				for (int k = 0; k < g; k++) ps.add(pop("twinkle", 0xF2F7FF, side.scale(rnd(-0.5f, 0.5f)).add(look.scale(-0.2)).add(0, rnd(-0.3f, 0.2f), 0),
						rnd(0.25f, 0.4f), 6, 0.6f));
			}
			case "impact" -> {
				// No arc: where the blade lands, a sharp flash, a small ring and sparks
				int c = g >= 9 ? 0xFFB24A : 0xE6EEFF;
				int lvl = g >= 9 ? g - 8 : g;
				ps.add(pop("twinkle", 0xFFFFFF, Vec3.ZERO, 0.7f + lvl * 0.06f, 4, 0.4f));
				ps.add(new P("ring", c, 2, List.of(new S(Vec3.ZERO, 0.15f, 0), new S(Vec3.ZERO, 0.6f + lvl * 0.05f, 0.2f), new S(Vec3.ZERO, 0.75f, 0.3f)),
						facing));
				for (int k = 0; k < 4 + lvl * 2; k++) {
					Vec3 d = side.scale(rnd(-0.7f, 0.7f)).add(look.scale(rnd(0.0f, 0.4f))).add(0, rnd(-0.3f, 0.5f), 0);
					ps.add(line("ember", pick(c, 0xFFFFFF), Vec3.ZERO, d, rnd(0.2f, 0.3f), 0.01f, 0, 0, 5 + R.nextInt(4)));
				}
			}
			case "streaks" -> {
				// Speed lines through the hit, the way the blade went, and a glint where it lands
				int c = g >= 9 ? 0xFFB24A : 0xE6EEFF;
				int lvl = g >= 9 ? g - 8 : g;
				for (int k = 0; k < 2 + lvl; k++) {
					Vec3 off = new Vec3(0, rnd(-0.35f, 0.35f), 0).add(look.scale(rnd(-0.2f, 0.2f)));
					Quaternionf rot = new Quaternionf(facing).rotateZ(0.35f + rnd(-0.08f, 0.08f));
					ps.add(new P("streak", k == 0 ? 0xFFFFFF : c, 2, List.of(new S(off.add(side.scale(0.55)), 0.2f, 0),
							new S(off, 0.7f + lvl * 0.04f, 0), new S(off.add(side.scale(-0.55)), 0.15f, 0)), rot));
				}
				ps.add(pop("twinkle", 0xFFFFFF, Vec3.ZERO, 0.55f, 4, 0.5f));
			}
			case "fire_aspect" -> {
				float size = g >= 2 ? 2.4f : 1.8f;
				Quaternionf rot = new Quaternionf(facing).rotateZ(0.5f);
				ps.add(new P("arc", 0xFFB24A, 5, List.of(new S(Vec3.ZERO, size * 0.4f, 0), new S(Vec3.ZERO, size, -0.9f)), rot));
				ps.add(new P("arc", 0xFFF0C8, 4, List.of(new S(Vec3.ZERO, size * 0.3f, 0), new S(Vec3.ZERO, size * 0.8f, -0.9f)), rot));
				sparks(ps, g >= 2 ? 16 : 8, 0xFF9A3C, side, 1.0);
				if (g >= 2) {
					for (int k = 0; k < 6; k++) {
						Vec3 f = new Vec3(rnd(-0.4f, 0.4f), rnd(-0.5f, 0.0f), rnd(-0.4f, 0.4f));
						ps.add(line("flame", pick(0xFF9A3C, 0xFF6A1F), f, f.add(0, rnd(0.4f, 0.8f), 0), rnd(0.5f, 0.8f), 0.05f, 0, 0, 12));
					}
				}
				flash(level, at, viewers, g >= 2 ? 13 : 9, 6);
			}
			case "smite" -> {
				// Golden: a ring of light; III+ sparkles rising; V a pillar of light and a bright flash
				Quaternionf flat = new Quaternionf().rotateX(-Mth.HALF_PI);
				Vec3 foot = new Vec3(0, -0.95, 0);
				ps.add(new P("ring", 0xFFE08A, 8, List.of(new S(foot, 0.3f, 0), new S(foot, 1.2f + g * 0.3f, 0.3f)), flat));
				ps.add(new P("glow", 0xFFD25A, 5, List.of(new S(Vec3.ZERO, 0.4f, 0), new S(Vec3.ZERO, 1.2f + g * 0.15f, 0), new S(Vec3.ZERO, 0.05f, 0)),
						null));
				if (g >= 3) {
					for (int k = 0; k < g * 3; k++) {
						Vec3 f = new Vec3(rnd(-0.6f, 0.6f), rnd(-0.9f, 0.3f), rnd(-0.6f, 0.6f));
						ps.add(line("twinkle", pick(0xFFF2C2, 0xFFE08A), f, f.add(0, rnd(0.6f, 1.2f), 0), rnd(0.35f, 0.5f), 0.02f, 0, 1.2f, 16));
					}
				}
				if (g >= 5) pillar(level, at.add(0, -1.0, 0), viewers);
				flash(level, at, viewers, g >= 5 ? 15 : 8 + g, g >= 5 ? 10 : 5);
			}
			case "knockback" -> {
				// A shock wave that pushes out from the hit: one ring, two at II, and dust
				for (int k = 0; k < g; k++) {
					ps.add(new P("ring", 0xE8E2D6, 3 + k, List.of(new S(Vec3.ZERO, 0.3f, 0), new S(look.scale(0.4 + k * 0.3), 1.4f + k * 0.8f, 0),
							new S(look.scale(0.6 + k * 0.4), 2.2f + k * 1.0f, 0)), facing));
				}
				for (int k = 0; k < 6 * g; k++) {
					Vec3 d = look.scale(rnd(0.3f, 1.0f)).add(side.scale(rnd(-0.8f, 0.8f))).add(0, rnd(-0.3f, 0.4f), 0);
					ps.add(line("mote", 0xD8D0C0, Vec3.ZERO, d, 0.3f, 0.0f, 0, 0, 10));
				}
			}
			case "looting" -> {
				// Coins and gold sparkles that burst out and fall
				int n = new int[]{0, 5, 9, 14}[Math.min(3, g)];
				for (int k = 0; k < n; k++) {
					Vec3 out = new Vec3(rnd(-0.8f, 0.8f), rnd(0.4f, 1.0f), rnd(-0.8f, 0.8f));
					Vec3 down = out.add(out.x * 0.4, -1.4, out.z * 0.4);
					ps.add(new P("coin", pick(0xFFD24A, 0xFFE58A, 0xF2B530), 8, List.of(new S(Vec3.ZERO, 0.2f, 0), new S(out, 0.32f, 2.5f),
							new S(down, 0.2f, 5.0f)), null));
				}
				for (int k = 0; k < n; k++) ps.add(pop("twinkle", 0xFFF2C2, new Vec3(rnd(-0.7f, 0.7f), rnd(-0.2f, 0.8f), rnd(-0.7f, 0.7f)), 0.45f, 8, 1f));
			}
			default -> {
			}
		}
		emit(level, at, viewers, ps);
	}

	/** A spark of a swing: dark until its turn (the blade gets there), then it flares and fades. */
	private static List<S> sweepStates(Vec3 pos, Vec3 push, int k, int n, float sz) {
		List<S> st = new ArrayList<>();
		for (int q = 0; q < k * 6 / n; q++) st.add(new S(pos, 0.01f, 0));
		st.add(new S(pos, sz, 0.3f));
		st.add(new S(pos.add(push.scale(0.08)).add(0, -0.04, 0), sz * 0.55f, 0.6f));
		st.add(new S(pos, 0.01f, 0.9f));
		return st;
	}

	/** Which way Smite lands (proof of concept: to compare). */
	static String smiteStyle = "sunburst";

	/**
	 * Where Smite lands. "beam": a ring on the ground and, at V, a shaft of light. "halo": a soft ring of light round
	 * the target that widens and fades. "sunburst": short rays out from the hit. "glow": the target glows for a moment,
	 * and specks of light drift up off it.
	 */
	private static void smiteHit(List<P> ps, ServerLevel level, Vec3 at, List<ServerPlayer> viewers, int g, Quaternionf facing, Vec3 look, Vec3 side) {
		int gold = 0xFFF0C0;
		switch (smiteStyle) {
			case "halo" -> {
				if (g >= 3) {
					ps.add(new P("ring", gold, 3, List.of(new S(Vec3.ZERO, 0.3f, 0), new S(Vec3.ZERO, 0.9f + g * 0.08f, 0.2f),
							new S(Vec3.ZERO, 1.2f + g * 0.08f, 0.3f), new S(Vec3.ZERO, 0.02f, 0.4f)), facing));
					ps.add(pop("haze", 0xFFE6A0, Vec3.ZERO, 0.9f + g * 0.1f, 10, 0));
				}
				if (g >= 5) {
					ps.add(new P("ring", 0xFFFFFF, 3, List.of(new S(Vec3.ZERO, 0.01f, 0), new S(Vec3.ZERO, 0.4f, 0), new S(Vec3.ZERO, 1.0f, 0.2f),
							new S(Vec3.ZERO, 0.02f, 0.3f)), facing));
					for (int k = 0; k < 6; k++) {
						Vec3 f = side.scale(rnd(-0.5f, 0.5f)).add(0, rnd(-0.6f, 0.2f), 0);
						ps.add(line("mote", pick(gold, 0xFFFFFF), f, f.add(0, rnd(0.5f, 0.9f), 0), 0.22f, 0.01f, 0, 0, 18));
					}
				}
				if (g >= 3) flash(level, at, viewers, 5 + g, 5);
			}
			case "sunburst" -> {
				if (g >= 3) {
					int rays = g >= 5 ? 10 : 7;
					for (int k = 0; k < rays; k++) {
						float a = (float) (k * Math.PI * 2 / rays + rnd(-0.12f, 0.12f));
						Vec3 dir = side.scale(Math.cos(a)).add(0, Math.sin(a), 0);
						Quaternionf rot = new Quaternionf(facing).rotateZ(a);
						float len = rnd(0.45f, 0.7f) + (g >= 5 ? 0.15f : 0);
						ps.add(new P("streak", k % 2 == 0 ? 0xFFFFFF : gold, 2, List.of(new S(dir.scale(0.15), 0.15f, 0), new S(dir.scale(0.4), len, 0),
								new S(dir.scale(0.7), 0.05f, 0)), rot));
					}
					ps.add(pop("twinkle", 0xFFFFFF, Vec3.ZERO, 0.6f + g * 0.04f, 4, 0.3f));
					ps.add(pop("haze", 0xFFE6A0, Vec3.ZERO, 0.8f, 6, 0));
					flash(level, at, viewers, 5 + g, 4);
				}
			}
			case "glow" -> {
				if (g >= 3) {
					// a soft glow that lingers a moment where it hit
					ps.add(new P("haze", 0xFFE6A0, 5, List.of(new S(Vec3.ZERO, 0.3f, 0), new S(Vec3.ZERO, 1.1f + g * 0.08f, 0), new S(Vec3.ZERO, 1.0f, 0),
							new S(Vec3.ZERO, 0.7f, 0), new S(Vec3.ZERO, 0.02f, 0)), null));
					int n = g >= 5 ? 12 : 6;
					for (int k = 0; k < n; k++) {
						Vec3 f = side.scale(rnd(-0.45f, 0.45f)).add(look.scale(rnd(-0.2f, 0.2f))).add(0, rnd(-0.8f, 0.3f), 0);
						List<S> st = new ArrayList<>();
						for (int q = 0; q < k / 2; q++) st.add(new S(f, 0.01f, 0));
						st.add(new S(f, 0.22f, 0));
						st.add(new S(f.add(0, 0.3, 0), 0.2f, 0));
						st.add(new S(f.add(0, 0.7, 0), 0.01f, 0));
						ps.add(new P(k % 3 == 0 ? "twinkle" : "mote", pick(gold, 0xFFFFFF), 4, st, null));
					}
					flash(level, at, viewers, 6 + g, 12);
				}
			}
			default -> {
				if (g >= 3) {
					Quaternionf flat = new Quaternionf().rotateX(-Mth.HALF_PI);
					Vec3 foot = new Vec3(0, -0.95, 0);
					ps.add(new P("ring", gold, 6, List.of(new S(foot, 0.3f, 0), new S(foot, 1.0f + g * 0.25f, 0.3f), new S(foot, 1.3f + g * 0.3f, 0.4f)),
							flat));
					ps.add(new P("haze", 0xFFE6A0, 8, List.of(new S(foot, 0.4f, 0), new S(foot, 2.0f, 0), new S(foot, 0.1f, 0)), flat));
				}
				if (g >= 5) shaft(level, at.add(0, -1.0, 0), viewers, 0xFFF0C8);
				if (g >= 3) flash(level, at, viewers, 6 + g, 6);
			}
		}
	}

	private static void sparks(List<P> ps, int n, int rgb, Vec3 side, double spread) {
		for (int k = 0; k < n; k++) {
			Vec3 d = side.scale(rnd(-1, 1) * spread).add(rnd(-0.3f, 0.3f), rnd(-0.2f, 0.7f), rnd(-0.3f, 0.3f));
			ps.add(line("ember", pick(rgb, 0xFFF1C2), Vec3.ZERO, d, rnd(0.35f, 0.5f), 0.02f, 0, 0, 8 + R.nextInt(6)));
		}
	}

	// ---------------------------------------------------------------- a pillar of light

	private record Pillar(ElementHolder holder, BlockDisplayElement beam, int[] age) {
	}

	private static final List<Pillar> PILLARS = new ArrayList<>();

	private static void pillar(ServerLevel level, Vec3 at, List<ServerPlayer> viewers) {
		ElementHolder h = new ElementHolder();
		BlockDisplayElement e = new BlockDisplayElement(nl.libreria.core.Ui.block("yellow_stained_glass"));
		e.setBrightness(new Brightness(15, 15));
		e.setViewRange(0.8f);
		e.setScale(new Vector3f(0.05f, 0.2f, 0.05f));
		e.setTranslation(new Vector3f(-0.025f, 0, -0.025f));
		e.setInterpolationDuration(4);
		e.setDisplaySize(2, 8);
		h.addElement(e);
		new ManualAttachment(h, level, () -> at);
		for (ServerPlayer v : viewers) h.startWatching(v);
		PILLARS.add(new Pillar(h, e, new int[]{-1}));
	}

	private static void tickPillars() {
		for (Iterator<Pillar> it = PILLARS.iterator(); it.hasNext();) {
			Pillar p = it.next();
			int a = ++p.age()[0];
			if (a == 1) {
				p.beam().setScale(new Vector3f(0.5f, 6f, 0.5f));
				p.beam().setTranslation(new Vector3f(-0.25f, 0, -0.25f));
				p.beam().startInterpolation();
			} else if (a == 10) {
				p.beam().setInterpolationDuration(8);
				p.beam().setScale(new Vector3f(0.02f, 7f, 0.02f));
				p.beam().setTranslation(new Vector3f(-0.01f, 0, -0.01f));
				p.beam().startInterpolation();
			}
			p.holder().tick();
			if (a > 20) {
				p.holder().destroy();
				it.remove();
			}
		}
	}

	/** A soft shaft of light from above (Smite at its highest): a tall glowing sprite that turns to face you. */
	private static void shaft(ServerLevel level, Vec3 at, List<ServerPlayer> viewers, int rgb) {
		ElementHolder h = new ElementHolder();
		ItemDisplayElement e = element("beam", rgb, false);
		e.setBillboardMode(Display.BillboardConstraints.VERTICAL);
		e.setScale(new Vector3f(0.2f, 6f, 1f));
		e.setTranslation(new Vector3f(0, 3f, 0));
		e.setInterpolationDuration(4);
		e.setDisplaySize(2, 8);
		h.addElement(e);
		new ManualAttachment(h, level, () -> at);
		for (ServerPlayer v : viewers) h.startWatching(v);
		SHAFTS.add(new Shaft(h, e, new int[]{-1}));
	}

	private record Shaft(ElementHolder holder, ItemDisplayElement beam, int[] age) {
	}

	private static final List<Shaft> SHAFTS = new ArrayList<>();

	private static void tickShafts() {
		for (Iterator<Shaft> it = SHAFTS.iterator(); it.hasNext();) {
			Shaft s = it.next();
			int a = ++s.age()[0];
			if (a == 1) {
				s.beam().setScale(new Vector3f(1.6f, 7f, 1f));
				s.beam().startInterpolation();
			} else if (a == 9) {
				s.beam().setInterpolationDuration(10);
				s.beam().setScale(new Vector3f(0.05f, 7.5f, 1f));
				s.beam().startInterpolation();
			}
			s.holder().tick();
			if (a > 22) {
				s.holder().destroy();
				it.remove();
			}
		}
	}

	// ---------------------------------------------------------------- light

	private static void light(ServerPlayer p, Wearer w, List<ServerPlayer> viewers, int tick) {
		if (w.lightLevel <= 0) return;
		ServerLevel level = (ServerLevel) p.level();
		BlockPos at = BlockPos.containing(p.getX(), p.getY() + p.getBbHeight() * 0.6, p.getZ());
		if (at.equals(w.light) && tick % 40 != 0) return;
		if (w.light != null && !w.light.equals(at)) restoreLight(p, w.light);
		BlockState lit = lightState(level, at, w.lightLevel);
		if (lit == null) {
			w.light = null;
			return;
		}
		ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(at, lit);
		for (ServerPlayer v : viewers) v.connection.send(packet);
		w.light = at;
	}

	/** A light block for this spot, if the spot is free (air, or still water). */
	private static @Nullable BlockState lightState(ServerLevel level, BlockPos at, int lvl) {
		BlockState real = level.getBlockState(at);
		boolean water = real.is(Blocks.WATER) && real.getFluidState().is(Fluids.WATER) && real.getFluidState().isSource();
		if (!real.isAir() && !water) return null;
		return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lvl).setValue(LightBlock.WATERLOGGED, water);
	}

	private static void restoreLight(ServerPlayer p, BlockPos pos) {
		ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(p.level(), pos);
		for (ServerPlayer v : viewers(p)) v.connection.send(packet);
	}

	private record Flash(ServerLevel level, BlockPos pos, List<ServerPlayer> viewers, int[] left) {
	}

	private static final List<Flash> FLASHES = new ArrayList<>();

	/** A short burst of light where something was hit. */
	private static void flash(ServerLevel level, Vec3 at, List<ServerPlayer> viewers, int lvl, int ticks) {
		BlockPos pos = BlockPos.containing(at);
		BlockState lit = lightState(level, pos, lvl);
		if (lit == null) return;
		ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(pos, lit);
		for (ServerPlayer v : viewers) v.connection.send(packet);
		FLASHES.add(new Flash(level, pos, viewers, new int[]{ticks}));
	}

	private static void tickFlashes(MinecraftServer server) {
		for (Iterator<Flash> it = FLASHES.iterator(); it.hasNext();) {
			Flash f = it.next();
			if (--f.left()[0] > 0) continue;
			ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(f.level(), f.pos());
			for (ServerPlayer v : f.viewers()) v.connection.send(packet);
			it.remove();
		}
	}
}
