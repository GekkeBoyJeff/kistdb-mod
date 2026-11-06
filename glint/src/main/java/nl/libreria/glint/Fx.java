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

	private static ItemDisplayElement element(String sprite, int rgb, boolean fixed) {
		ItemDisplayElement e = new ItemDisplayElement(sprite(sprite, rgb));
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
		w.grade = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
		w.lightLevel = lightFor(w.kind, w.grade);
		WEARERS.put(p.getUUID(), w);
	}

	private static int lightFor(String kind, int g) {
		return switch (kind) {
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
			case "vanish" -> vanish(p, w, level, viewers, tick, ps);
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
			ItemDisplayElement e = element(key.equals("icering") ? "ring" : key.equals("shadow") ? "haze" : "circle", rgb, true);
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
