package nl.libreria.glint;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
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
 * Proof of concept: particles and light of our own, server-side.
 *
 * <p>Particles: soft glowing sprites from the pack (white, coloured per particle) on item displays. The client moves
 * them (teleport interpolation), shrinks and turns them (transformation interpolation) by itself: one packet to
 * start, one to set where it goes, one to remove it. They always face you and are lit by themselves.
 *
 * <p>Light: a light block that only the players nearby are told about, where the wearer's head is. The client's own
 * lighting does the rest, so the ground and walls around you really light up. The world itself is never changed.
 */
final class Fx {
	private static final RandomSource R = RandomSource.create();

	/** One particle: a sprite and colour, from a spot (relative to the burst) to another, growing or shrinking, turning. */
	record P(String sprite, int rgb, Vec3 from, Vec3 to, float s0, float s1, float r0, float r1, int ticks, @Nullable Quaternionf base) {
		P(String sprite, int rgb, Vec3 from, Vec3 to, float s0, float s1, float r0, float r1, int ticks) {
			this(sprite, rgb, from, to, s0, s1, r0, r1, ticks, null);
		}

		Quaternionf turn(float r) {
			return (base == null ? new Quaternionf() : new Quaternionf(base)).rotateZ(r);
		}
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

	/** Shows a few particles around a spot to everyone who can see it. */
	static void emit(ServerLevel level, Vec3 at, List<ServerPlayer> viewers, List<P> ps) {
		if (ps.isEmpty() || viewers.isEmpty()) return;
		Burst b = new Burst();
		for (P p : ps) {
			ItemDisplayElement e = new ItemDisplayElement(sprite(p.sprite(), p.rgb()));
			e.setItemDisplayContext(ItemDisplayContext.FIXED);
			e.setBillboardMode(p.base() == null ? Display.BillboardConstraints.CENTER : Display.BillboardConstraints.FIXED);
			e.setBrightness(new Brightness(15, 15));
			e.setViewRange(0.6f);
			e.setOffset(p.from());
			e.setScale(new Vector3f(p.s0()));
			e.setLeftRotation(p.turn(p.r0()));
			e.setTeleportDuration(Math.min(59, p.ticks()));
			e.setInterpolationDuration(p.ticks());
			b.holder.addElement(e);
			b.els.add(e);
			b.ps.add(p);
			b.life = Math.max(b.life, p.ticks() + 2);
		}
		new ManualAttachment(b.holder, level, () -> at);
		for (ServerPlayer v : viewers) b.holder.startWatching(v);
		LIVE.add(b);
	}

	// ---------------------------------------------------------------- what someone wears (for the proof of concept)

	/** Per player: which look, and where their light is. */
	private static final class Wearer {
		String kind;
		@Nullable BlockPos light;
		int lightLevel;
		@Nullable ElementHolder ring;
		final List<ItemDisplayElement> runes = new ArrayList<>();
		int slash = -1;
	}

	private static final Map<UUID, Wearer> WEARERS = new HashMap<>();

	static void set(ServerPlayer p, String kind) {
		Wearer w = WEARERS.remove(p.getUUID());
		if (w != null) clear(p, w);
		if (kind.equals("off")) return;
		w = new Wearer();
		w.kind = kind;
		w.lightLevel = switch (kind) {
			case "fire" -> 13;
			case "soul" -> 10;
			case "frost" -> 7;
			case "runes" -> 8;
			default -> 0;
		};
		WEARERS.put(p.getUUID(), w);
	}

	/** A fiery slash in front of the player (what a Fire Aspect sword could do on a hit). */
	static void slash(ServerPlayer p) {
		Wearer w = WEARERS.get(p.getUUID());
		if (w != null) w.slash = 0;
	}

	private static void clear(ServerPlayer p, Wearer w) {
		if (w.light != null) restoreLight(p, w.light);
		if (w.ring != null) w.ring.destroy();
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
				if (e.getValue().ring != null) e.getValue().ring.destroy();
				it.remove();
				continue;
			}
			wear(p, e.getValue(), server.getTickCount());
		}
		for (Iterator<Burst> it = LIVE.iterator(); it.hasNext();) {
			Burst b = it.next();
			b.age++;
			if (b.age == 1) {
				// a tick after it appeared: where it goes, the client does the rest
				for (int i = 0; i < b.els.size(); i++) {
					ItemDisplayElement e = b.els.get(i);
					P p = b.ps.get(i);
					e.setOffset(p.to());
					e.setScale(new Vector3f(p.s1()));
					e.setLeftRotation(p.turn(p.r1()));
					e.startInterpolation();
				}
			}
			b.holder.tick();
			if (b.age > b.life) {
				b.holder.destroy();
				it.remove();
			}
		}
	}

	private static int pick(int... colours) {
		return colours[R.nextInt(colours.length)];
	}

	private static float rnd(float a, float b) {
		return a + R.nextFloat() * (b - a);
	}

	/** A spot on the body: around the middle, at a height (0 feet, 1 head). */
	private static Vec3 onBody(ServerPlayer p, float y0, float y1, float r) {
		double a = R.nextDouble() * Math.PI * 2;
		return new Vec3(Math.cos(a) * r, p.getBbHeight() * rnd(y0, y1), Math.sin(a) * r);
	}

	private static void wear(ServerPlayer p, Wearer w, int tick) {
		ServerLevel level = (ServerLevel) p.level();
		List<ServerPlayer> viewers = viewers(p);
		Vec3 at = p.position();
		List<P> ps = new ArrayList<>();
		switch (w.kind) {
			case "fire" -> {
				// Embers that rise off you and burn out, now and then a spark, and a warm haze around you
				if (R.nextFloat() < 0.55f) {
					Vec3 from = onBody(p, 0.05f, 0.95f, 0.42f);
					int t = 16 + R.nextInt(14);
					ps.add(new P("ember", pick(0xFFE9A8, 0xFFB64A, 0xFF8A2E, 0xFF5A1C), from,
							from.add(rnd(-0.18f, 0.18f), rnd(0.6f, 1.15f), rnd(-0.18f, 0.18f)), rnd(0.2f, 0.32f), 0.02f, 0, 0, t));
				}
				if (tick % 3 == 0) {
					Vec3 from = onBody(p, 0.2f, 0.9f, 0.36f);
					ps.add(new P("spark", 0xFFF1C2, from, from.add(rnd(-0.25f, 0.25f), rnd(0.15f, 0.45f), rnd(-0.25f, 0.25f)),
							rnd(0.16f, 0.24f), 0.0f, 0, rnd(-1.2f, 1.2f), 8 + R.nextInt(5)));
				}
				if (tick % 5 == 0) {
					Vec3 from = new Vec3(rnd(-0.15f, 0.15f), p.getBbHeight() * rnd(0.25f, 0.6f), rnd(-0.15f, 0.15f));
					ps.add(new P("haze", pick(0xFF9A3C, 0xFF7A2A), from, from.add(0, 0.5, 0), 1.1f, 1.5f, 0, 0, 22));
				}
			}
			case "frost" -> {
				// Snowflakes that drift down and turn, cold glints on you
				if (tick % 2 == 0) {
					Vec3 from = new Vec3(rnd(-0.6f, 0.6f), p.getBbHeight() * rnd(0.85f, 1.25f), rnd(-0.6f, 0.6f));
					float spin = rnd(-2.2f, 2.2f);
					ps.add(new P("flake", pick(0xF2FAFF, 0xBFE6FF, 0x8FD0FF), from,
							from.add(rnd(-0.2f, 0.2f), -rnd(0.9f, 1.4f), rnd(-0.2f, 0.2f)), rnd(0.14f, 0.2f), 0.07f, 0, spin, 30 + R.nextInt(10)));
				}
				if (tick % 4 == 1) {
					Vec3 from = onBody(p, 0.1f, 0.95f, 0.34f);
					ps.add(new P("spark", 0xCFEFFF, from, from, 0.02f, 0.22f, 0, 0.8f, 6));
				}
			}
			case "soul" -> {
				// Wisps that rise from your feet and fade, a pale blue haze
				if (tick % 2 == 0) {
					double a = R.nextDouble() * Math.PI * 2;
					Vec3 from = new Vec3(Math.cos(a) * 0.5, 0.05, Math.sin(a) * 0.5);
					Vec3 to = new Vec3(Math.cos(a + 1.1) * 0.22, p.getBbHeight() * rnd(0.7f, 1.0f), Math.sin(a + 1.1) * 0.22);
					ps.add(new P("wisp", pick(0x8FF8FF, 0x4FDDF5, 0xB6F2FF), from, to, rnd(0.2f, 0.28f), 0.04f, 0, 0, 24 + R.nextInt(8)));
				}
				if (tick % 6 == 0) {
					Vec3 from = new Vec3(0, 0.3, 0);
					ps.add(new P("haze", 0x3FCFEA, from, from.add(0, 0.7, 0), 1.0f, 1.4f, 0, 0, 26));
				}
			}
			case "runes" -> runes(p, w, level, viewers, tick);
			default -> {
			}
		}
		if (w.slash >= 0) {
			slashTick(p, w, level, viewers);
		}
		if (!ps.isEmpty()) emit(level, at, viewers, ps);
		light(p, w, viewers, tick);
	}

	/** Four runes that circle around your middle, each with a soft glow behind it. */
	private static void runes(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers, int tick) {
		if (w.ring == null) {
			w.ring = new ElementHolder();
			String[] names = {"rune_a", "rune_b", "rune_c", "rune_d"};
			for (int i = 0; i < 4; i++) {
				ItemDisplayElement e = new ItemDisplayElement(sprite(names[i], i % 2 == 0 ? 0x9FB4FF : 0xC2A6FF));
				e.setItemDisplayContext(ItemDisplayContext.FIXED);
				e.setBillboardMode(Display.BillboardConstraints.CENTER);
				e.setBrightness(new Brightness(15, 15));
				e.setViewRange(0.6f);
				e.setScale(new Vector3f(0.24f));
				e.setTeleportDuration(3);
				e.setOffset(ringSpot(p, i, tick));
				w.ring.addElement(e);
				w.runes.add(e);
			}
			new ManualAttachment(w.ring, level, p::position);
			for (ServerPlayer v : viewers) w.ring.startWatching(v);
		}
		if (tick % 3 == 0) {
			for (int i = 0; i < w.runes.size(); i++) w.runes.get(i).setOffset(ringSpot(p, i, tick + 3));
		}
		w.ring.tick();
		if (tick % 7 == 0) {
			int i = R.nextInt(4);
			Vec3 from = ringSpot(p, i, tick);
			emit(level, p.position(), viewers, List.of(new P("spark", 0xD6DEFF, from, from.add(0, 0.25, 0), 0.18f, 0.0f, 0, 1.0f, 10)));
		}
	}

	private static Vec3 ringSpot(ServerPlayer p, int i, int tick) {
		double a = tick * (Math.PI * 2 / 80) + i * Math.PI / 2;
		return new Vec3(Math.cos(a) * 0.78, p.getBbHeight() * 0.52 + Math.sin(a * 2) * 0.05, Math.sin(a) * 0.78);
	}

	/** The slash: an arc that sweeps and widens in front of you, with embers flung off its edge. */
	private static void slashTick(ServerPlayer p, Wearer w, ServerLevel level, List<ServerPlayer> viewers) {
		if (w.slash == 0) {
			float yaw = p.getYRot() * Mth.DEG_TO_RAD;
			Vec3 look = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
			Vec3 at = p.getEyePosition().add(look.scale(1.1)).add(0, -0.35, 0);
			Quaternionf rot = new Quaternionf().rotateY(-yaw + Mth.PI).rotateZ(0.5f);
			emit(level, at, viewers, List.of(new P("arc", 0xFFB24A, Vec3.ZERO, Vec3.ZERO, 0.5f, 1.7f, 0, -0.9f, 5, rot),
					new P("arc", 0xFFF0C8, Vec3.ZERO, Vec3.ZERO, 0.35f, 1.4f, 0, -0.9f, 4, rot)));
			List<P> embers = new ArrayList<>();
			for (int k = 0; k < 10; k++) {
				double t = (k / 9.0 - 0.5) * 2.2;
				Vec3 from = new Vec3(Math.cos(t) * 0.8, Math.sin(t) * 0.3, 0);
				Vec3 dir = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
				Vec3 side = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
				Vec3 f = side.scale(from.x).add(0, from.y, 0);
				embers.add(new P("ember", pick(0xFFE9A8, 0xFFB64A, 0xFF7A2A), f, f.add(dir.scale(rnd(0.4f, 0.9f))).add(0, rnd(0.1f, 0.5f), 0),
						rnd(0.12f, 0.2f), 0.0f, 0, 0, 10 + R.nextInt(8)));
			}
			emit(level, at, viewers, embers);
		}
		w.slash++;
		if (w.slash > 12) w.slash = -1;
	}

	// ---------------------------------------------------------------- light

	private static void light(ServerPlayer p, Wearer w, List<ServerPlayer> viewers, int tick) {
		if (w.lightLevel <= 0) return;
		ServerLevel level = (ServerLevel) p.level();
		BlockPos at = BlockPos.containing(p.getX(), p.getY() + p.getBbHeight() * 0.6, p.getZ());
		BlockState real = level.getBlockState(at);
		boolean water = real.is(Blocks.WATER) && real.getFluidState().is(Fluids.WATER) && real.getFluidState().isSource();
		boolean free = real.isAir() || water;
		if (at.equals(w.light) && tick % 40 != 0) return;
		if (w.light != null && !w.light.equals(at)) restoreLight(p, w.light);
		if (!free) {
			w.light = null;
			return;
		}
		BlockState lit = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, w.lightLevel).setValue(LightBlock.WATERLOGGED, water);
		ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(at, lit);
		for (ServerPlayer v : viewers) v.connection.send(packet);
		w.light = at;
	}

	private static void restoreLight(ServerPlayer p, BlockPos pos) {
		ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(p.level(), pos);
		for (ServerPlayer v : viewers(p)) v.connection.send(packet);
	}
}
