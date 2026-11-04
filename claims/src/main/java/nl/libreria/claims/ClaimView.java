package nl.libreria.claims;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Hud;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The land map: your camera flies up and looks straight down on the real world around you. Claimed chunks are
 * coloured glass plates (green = yours, blue = you're trusted, red = someone else's) with the owner's name on them.
 * The crosshair is the cursor: move the mouse to glide over the land, left-click claims the chunk under it, right-click
 * releases it, scroll zooms, sneak flies back down. Getting hurt closes it at once. Fully server-side: the camera is a
 * virtual entity the client looks through, the plates are display entities only you can see.
 */
final class ClaimView {
	/** How far you can look, in chunks from where you stand. */
	static final int RADIUS = 8;
	private static final int OPEN_TICKS = 26, CLOSE_TICKS = 14;
	private static final double MIN_H = 18, MAX_H = 90, START_H = 34;
	private static final Identifier FREEZE = Identifier.fromNamespaceAndPath("libreria", "claim_view");
	private static final Brightness FULL = new Brightness(15, 15);
	private static final String HUD = "claims.view";

	private enum Kind {
		FREE, MINE, TRUSTED, OTHER;

		Block glass() {
			return switch (this) {
				case MINE -> ClaimView.glass("lime");
				case TRUSTED -> ClaimView.glass("light_blue");
				case OTHER -> ClaimView.glass("red");
				case FREE -> ClaimView.glass("white");
			};
		}

		int color() {
			return switch (this) {
				case MINE -> 0x55FF55;
				case TRUSTED -> 0x55FFFF;
				case OTHER -> 0xFF5555;
				case FREE -> 0xFFFFFF;
			};
		}
	}

	private static final class View {
		final ServerPlayer p;
		final ServerLevel level;
		final Vec3 origin;
		final ElementHolder holder = new ElementHolder();
		final ItemDisplayElement cam = new ItemDisplayElement();
		final BlockDisplayElement cursor = new BlockDisplayElement(glass("white").defaultBlockState());
		final Map<Long, BlockDisplayElement> plates = new HashMap<>();
		final List<TextDisplayElement> labels = new ArrayList<>();
		final Map<Long, Integer> tops = new HashMap<>();
		final Vec3 eyeStart;
		final float yawStart, pitchStart;
		double baseY;
		/** Cursor in world x/z, and the mouse reference it is measured from. */
		double baseX, baseZ, curX, curZ;
		float refYaw;
		double height = START_H;
		int age, closeAge = -1, lastSlot;
		boolean cameraSet, wasSwinging, sneakReleased;
		@Nullable ChunkPos cursorChunk;
		Vec3 camPos;
		/** Plates that were just spawned small; they grow once the client has them. */
		final List<BlockDisplayElement> pending = new ArrayList<>();
		final Map<BlockDisplayElement, Integer> pendingDelay = new HashMap<>();

		View(ServerPlayer p) {
			this.p = p;
			this.level = p.level();
			this.origin = p.position();
			this.eyeStart = p.getEyePosition();
			this.yawStart = p.getYRot();
			this.pitchStart = p.getXRot();
			this.camPos = this.eyeStart;
		}
	}

	private static final Map<UUID, View> VIEWS = new HashMap<>();

	private static Block glass(String color) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(color + "_stained_glass"));
	}

	private ClaimView() {
	}

	static boolean isOpen(ServerPlayer p) {
		return VIEWS.containsKey(p.getUUID());
	}

	// ---------------------------------------------------------------- open and close

	static void open(ServerPlayer p) {
		if (isOpen(p)) return;
		if (p.isPassenger() || p.isSleeping() || p.isSpectator()) {
			ClaimsMod.msg(p, "Get off / out first, then open the land map.", ChatFormatting.GRAY);
			return;
		}
		p.closeContainer();
		View v = new View(p);
		VIEWS.put(p.getUUID(), v);
		Services.setCamera(p.getUUID(), true);

		// Ground height per chunk (the plates lie on top of each chunk) and the highest point for the camera
		ChunkPos center = p.chunkPosition();
		int top = (int) Math.floor(p.getY());
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				ChunkPos c = new ChunkPos(center.x() + dx, center.z() + dz);
				int h = surface(v.level, c);
				v.tops.put(key(c), h);
				if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) top = Math.max(top, h);
			}
		}
		v.baseY = Math.min(top, p.getY() + 40);
		v.baseX = v.curX = p.getX();
		v.baseZ = v.curZ = p.getZ();
		v.lastSlot = p.getInventory().getSelectedSlot();
		v.sneakReleased = !p.isShiftKeyDown();

		// The camera: an invisible display entity the client looks through
		v.cam.setItem(net.minecraft.world.item.ItemStack.EMPTY);
		v.cam.setTeleportDuration(2);
		v.cam.setOffset(v.eyeStart.subtract(v.origin));
		v.cam.setYaw(v.yawStart);
		v.cam.setPitch(v.pitchStart);
		v.holder.addElement(v.cam);

		v.cursor.setGlowing(true);
		v.cursor.setGlowColorOverride(0xFFFFFF);
		v.cursor.setBrightness(FULL);
		v.cursor.setTeleportDuration(2);
		v.cursor.setViewRange(6f);
		v.cursor.setScale(new Vector3f(0.01f, 0.12f, 0.01f));
		v.cursor.setTranslation(new Vector3f(8, 0, 8));
		v.cursor.setOffset(Vec3.ZERO);
		v.holder.addElement(v.cursor);

		buildPlates(v, true);
		new ManualAttachment(v.holder, v.level, () -> v.origin);
		v.holder.startWatching(p);

		// Stand still while the map is open (transient: never saved)
		freeze(p, true);
		// Pitch 0 so the mouse has room to move both ways
		p.teleportTo(v.level, p.getX(), p.getY(), p.getZ(), Set.of(), p.getYRot(), 0f, false);
		v.refYaw = p.getYRot();

		Ui.sound(p, SoundEvents.ELYTRA_FLYING, 0.25f, 1.6f);
		p.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 12));
		p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Land map").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Mouse: move · Left-click: claim · Right-click: release · Scroll: zoom · Sneak: back")
				.withStyle(ChatFormatting.GRAY)));
	}

	/** Back to normal. Instant (when you get hurt) or by flying the camera back down first. */
	static void close(ServerPlayer p, boolean instant) {
		View v = VIEWS.get(p.getUUID());
		if (v == null) return;
		if (instant || !v.cameraSet) {
			finish(v);
			return;
		}
		if (v.closeAge < 0) {
			v.closeAge = 0;
			Ui.sound(p, SoundEvents.ELYTRA_FLYING, 0.2f, 1.9f);
			// The plates fold away
			for (BlockDisplayElement plate : v.plates.values()) shrink(plate);
			for (TextDisplayElement label : v.labels) label.setScale(new Vector3f(0.01f));
		}
	}

	private static void finish(View v) {
		VIEWS.remove(v.p.getUUID());
		Services.setCamera(v.p.getUUID(), false);
		if (!v.p.hasDisconnected()) {
			v.p.connection.send(VirtualEntityUtils.createClientboundSetCameraPacket(v.p.getId()));
			Hud.hide(v.p, HUD);
		}
		v.holder.destroy();
		freeze(v.p, false);
	}

	static void forget(ServerPlayer p) {
		View v = VIEWS.get(p.getUUID());
		if (v != null) finish(v);
	}

	static void closeAll() {
		for (View v : new ArrayList<>(VIEWS.values())) finish(v);
	}

	private static void freeze(ServerPlayer p, boolean on) {
		for (var attr : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
			AttributeInstance inst = p.getAttribute(attr);
			if (inst == null) continue;
			inst.removeModifier(FREEZE);
			if (on) inst.addTransientModifier(new AttributeModifier(FREEZE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	// ---------------------------------------------------------------- every tick

	static void tick(MinecraftServer server) {
		for (View v : new ArrayList<>(VIEWS.values())) {
			ServerPlayer p = v.p;
			if (p.hasDisconnected() || p.isRemoved() || p.level() != v.level || p.isDeadOrDying()) {
				finish(v);
				continue;
			}
			v.age++;
			// The camera entity must exist on the client before we look through it
			if (!v.cameraSet && v.age >= 2) {
				p.connection.send(VirtualEntityUtils.createClientboundSetCameraPacket(v.cam.getEntityId()));
				v.cameraSet = true;
			}
			// Pushed away (knockback, water…)? Back to where you stood
			if (p.position().distanceToSqr(v.origin) > 0.36) {
				p.teleportTo(v.level, v.origin.x, v.origin.y, v.origin.z, Set.of(), p.getYRot(), p.getXRot(), false);
			}

			if (v.closeAge >= 0) {
				v.closeAge++;
				double t = ease(Math.min(1, v.closeAge / (double) CLOSE_TICKS));
				Vec3 eye = p.getEyePosition();
				moveCamera(v, lerp(v.camPos, eye, t), lerpAngle(180f, p.getYRot(), (float) t), Mth.lerp((float) t, 90f, p.getXRot()));
				v.holder.tick();
				if (v.closeAge >= CLOSE_TICKS) finish(v);
				continue;
			}

			if (!v.pending.isEmpty() && v.age >= 1) {
				for (BlockDisplayElement plate : v.pending) grow(plate, v.pendingDelay.getOrDefault(plate, 0));
				v.pending.clear();
				v.pendingDelay.clear();
			}
			input(v);

			ChunkPos cc = new ChunkPos(Mth.floor(v.curX) >> 4, Mth.floor(v.curZ) >> 4);
			if (!cc.equals(v.cursorChunk)) {
				v.cursorChunk = cc;
				placeCursor(v, cc);
			}
			Vec3 target = new Vec3(v.curX, v.baseY + v.height, v.curZ);
			if (v.age <= OPEN_TICKS) {
				double t = ease(v.age / (double) OPEN_TICKS);
				moveCamera(v, lerp(v.eyeStart, target, t), lerpAngle(v.yawStart, 180f, (float) t), Mth.lerp((float) t, v.pitchStart, 90f));
			} else {
				moveCamera(v, target, 180f, 90f);
			}
			v.camPos = target;
			status(v);
			v.holder.tick();
		}
	}

	/** The mouse moves the cursor: turning right = east, looking down = south (north is up on the map). */
	private static void input(View v) {
		ServerPlayer p = v.p;
		double k = v.height / 150.0;
		float dyaw = Mth.wrapDegrees(p.getYRot() - v.refYaw);
		float dpitch = p.getXRot();
		v.curX = v.baseX + dyaw * k;
		v.curZ = v.baseZ + dpitch * k;
		// Keep within reach of where you stand
		double max = RADIUS * 16 + 8;
		v.curX = Mth.clamp(v.curX, v.origin.x - max, v.origin.x + max);
		v.curZ = Mth.clamp(v.curZ, v.origin.z - max, v.origin.z + max);
		// Turned far: take this as the new reference (yaw is free, pitch is put back to level)
		if (Math.abs(dyaw) > 70) {
			v.baseX = v.curX;
			v.refYaw = p.getYRot();
		}
		if (Math.abs(dpitch) > 55) {
			v.baseX = v.curX;
			v.baseZ = v.curZ;
			v.refYaw = p.getYRot();
			p.teleportTo(v.level, v.origin.x, v.origin.y, v.origin.z, Set.of(), p.getYRot(), 0f, false);
		}

		// Scroll = zoom (the hotbar slot changes)
		int slot = p.getInventory().getSelectedSlot();
		if (slot != v.lastSlot) {
			int d = slot - v.lastSlot;
			if (d > 4) d -= 9;
			if (d < -4) d += 9;
			v.height = Mth.clamp(v.height + d * 6, MIN_H, MAX_H);
			v.lastSlot = slot;
		}
		// Left-click = claim (an arm swing reaches the server)
		boolean swinging = p.isSwinging();
		if (swinging && !v.wasSwinging && v.age > OPEN_TICKS) clickClaim(v, true);
		v.wasSwinging = swinging;
		// Sneak = back
		if (!p.isShiftKeyDown()) v.sneakReleased = true;
		else if (v.sneakReleased) close(p, false);
	}

	/** Right-click while the map is open (from UseItemCallback). */
	static boolean onUse(ServerPlayer p) {
		View v = VIEWS.get(p.getUUID());
		if (v == null) return false;
		if (v.age > OPEN_TICKS && v.closeAge < 0) clickClaim(v, false);
		return true;
	}

	private static void clickClaim(View v, boolean claim) {
		if (v.cursorChunk == null) return;
		boolean changed = claim ? ClaimsMod.claim(v.p, v.level, v.cursorChunk) : ClaimsMod.unclaim(v.p, v.level, v.cursorChunk);
		if (changed) {
			buildPlates(v, false);
			placeCursor(v, v.cursorChunk);
		}
	}

	// ---------------------------------------------------------------- drawing

	private static void moveCamera(View v, Vec3 pos, float yaw, float pitch) {
		v.cam.setOffset(pos.subtract(v.origin));
		v.cam.setYaw(yaw);
		v.cam.setPitch(pitch);
	}

	private static Kind kind(View v, ChunkPos c) {
		ClaimsMod.Claim cl = ClaimsMod.claimAt(v.level, c);
		if (cl == null) return Kind.FREE;
		if (cl.owner.equals(v.p.getUUID())) return Kind.MINE;
		return ClaimsMod.trustedIn(v.p, cl) ? Kind.TRUSTED : Kind.OTHER;
	}

	private static int top(View v, ChunkPos c) {
		return v.tops.computeIfAbsent(key(c), k -> surface(v.level, c));
	}

	/** Highest ground in a chunk (a few samples), so the plate lies on top of it. */
	private static int surface(ServerLevel level, ChunkPos c) {
		int h = level.getMinY();
		for (int x = 1; x < 16; x += 7) {
			for (int z = 1; z < 16; z += 7) {
				h = Math.max(h, level.getHeight(Heightmap.Types.MOTION_BLOCKING, c.getMinBlockX() + x, c.getMinBlockZ() + z));
			}
		}
		return h;
	}

	/** Coloured plates on every claimed chunk around you, plus one name label per owner's piece of land. */
	private static void buildPlates(View v, boolean animate) {
		ChunkPos center = new ChunkPos(Mth.floor(v.origin.x) >> 4, Mth.floor(v.origin.z) >> 4);
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				ChunkPos c = new ChunkPos(center.x() + dx, center.z() + dz);
				Kind kind = kind(v, c);
				long key = key(c);
				BlockDisplayElement plate = v.plates.get(key);
				if (kind == Kind.FREE) {
					if (plate != null) {
						v.holder.removeElement(plate);
						v.plates.remove(key);
					}
					continue;
				}
				if (plate == null) {
					plate = new BlockDisplayElement(kind.glass().defaultBlockState());
					plate.setBrightness(FULL);
					plate.setViewRange(6f);
					plate.setOffset(new Vec3(c.getMinBlockX(), top(v, c) + 0.06, c.getMinBlockZ()).subtract(v.origin));
					shrink(plate);
					v.holder.addElement(plate);
					v.plates.put(key, plate);
					// Pop in with a wave from where you stand (once the client has the small plate)
					v.pending.add(plate);
					v.pendingDelay.put(plate, animate ? 6 + (Math.abs(dx) + Math.abs(dz)) * 2 : 0);
				} else {
					plate.setBlockState(kind.glass().defaultBlockState());
				}
			}
		}
		labels(v, center);
	}

	private static void shrink(BlockDisplayElement plate) {
		plate.setInterpolationDuration(6);
		plate.setTranslation(new Vector3f(8, 0, 8));
		plate.setScale(new Vector3f(0.01f, 0.1f, 0.01f));
		plate.startInterpolation();
	}

	private static void grow(BlockDisplayElement plate, int delay) {
		plate.setStartInterpolation(delay);
		plate.setInterpolationDuration(8);
		plate.setTranslation(new Vector3f(0.12f, 0, 0.12f));
		plate.setScale(new Vector3f(15.76f, 0.1f, 15.76f));
	}

	/** One flat name per connected piece of land of the same owner, lying on its middle chunk. */
	private static void labels(View v, ChunkPos center) {
		for (TextDisplayElement l : v.labels) v.holder.removeElement(l);
		v.labels.clear();
		Set<Long> done = new HashSet<>();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				ChunkPos start = new ChunkPos(center.x() + dx, center.z() + dz);
				ClaimsMod.Claim first = ClaimsMod.claimAt(v.level, start);
				if (first == null || !done.add(key(start))) continue;
				// Flood fill this owner's connected chunks (within view)
				List<ChunkPos> group = new ArrayList<>();
				ArrayDeque<ChunkPos> todo = new ArrayDeque<>();
				todo.add(start);
				while (!todo.isEmpty()) {
					ChunkPos c = todo.poll();
					group.add(c);
					for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
						ChunkPos n = new ChunkPos(c.x() + d[0], c.z() + d[1]);
						if (Math.abs(n.x() - center.x()) > RADIUS || Math.abs(n.z() - center.z()) > RADIUS) continue;
						ClaimsMod.Claim nc = ClaimsMod.claimAt(v.level, n);
						if (nc != null && nc.owner.equals(first.owner) && done.add(key(n))) todo.add(n);
					}
				}
				double ax = 0, az = 0;
				for (ChunkPos c : group) {
					ax += c.x();
					az += c.z();
				}
				ax /= group.size();
				az /= group.size();
				ChunkPos mid = group.get(0);
				double best = Double.MAX_VALUE;
				for (ChunkPos c : group) {
					double d = (c.x() - ax) * (c.x() - ax) + (c.z() - az) * (c.z() - az);
					if (d < best) {
						best = d;
						mid = c;
					}
				}
				Kind kind = kind(v, mid);
				MutableComponent text = kind == Kind.MINE ? Component.literal("Your land").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
						: Component.literal(first.ownerName).withStyle(s -> s.withColor(kind.color()).withBold(true));
				if (kind == Kind.TRUSTED) text.append(Component.literal("\n(you may build)").withStyle(ChatFormatting.AQUA));
				TextDisplayElement label = new TextDisplayElement(text);
				label.setBillboardMode(Display.BillboardConstraints.FIXED);
				label.setBrightness(FULL);
				label.setViewRange(6f);
				label.setShadow(true);
				label.setBackground(0x60000000);
				// Lying flat, readable from above with north up
				label.setLeftRotation(new Quaternionf().rotateX((float) -Math.PI / 2));
				label.setScale(new Vector3f(5f));
				label.setOffset(new Vec3(mid.getMinBlockX() + 8, top(v, mid) + 0.25, mid.getMinBlockZ() + 8).subtract(v.origin));
				v.holder.addElement(label);
				v.labels.add(label);
			}
		}
	}

	/** The glowing frame on the chunk under the crosshair. */
	private static void placeCursor(View v, ChunkPos c) {
		Kind kind = kind(v, c);
		v.cursor.setOffset(new Vec3(c.getMinBlockX(), top(v, c) + 0.1, c.getMinBlockZ()).subtract(v.origin));
		v.cursor.setGlowColorOverride(kind.color());
		v.cursor.setInterpolationDuration(3);
		v.cursor.setTranslation(new Vector3f(0.05f, 0, 0.05f));
		v.cursor.setScale(new Vector3f(15.9f, 0.14f, 15.9f));
		v.cursor.startInterpolation();
		Ui.sound(v.p, SoundEvents.UI_BUTTON_CLICK.value(), 0.08f, 1.8f);
	}

	/** What's under the crosshair, in the action bar. */
	private static void status(View v) {
		ChunkPos c = v.cursorChunk;
		if (c == null) return;
		ClaimsMod.Claim cl = ClaimsMod.claimAt(v.level, c);
		Kind kind = kind(v, c);
		MutableComponent line = Component.literal("Chunk " + c.x() + ", " + c.z() + "  ").withStyle(ChatFormatting.GRAY);
		switch (kind) {
			case FREE -> line.append(Component.literal("Free").withStyle(ChatFormatting.WHITE))
					.append(Component.literal("  ·  left-click: claim").withStyle(ChatFormatting.YELLOW));
			case MINE -> line.append(Component.literal("Yours").withStyle(ChatFormatting.GREEN))
					.append(Component.literal("  ·  right-click: release").withStyle(ChatFormatting.YELLOW));
			case TRUSTED -> line.append(Component.literal(cl.ownerName + " (you may build)").withStyle(ChatFormatting.AQUA));
			case OTHER -> line.append(Component.literal(cl.ownerName).withStyle(ChatFormatting.RED));
		}
		line.append(Component.literal("  ·  " + ClaimsMod.owned(v.p.getUUID()) + "/" + ClaimsMod.budget(v.p) + " used").withStyle(ChatFormatting.GRAY));
		Hud.show(v.p, HUD, line, Hud.PRIO_INFO, 10);
	}

	// ---------------------------------------------------------------- maths

	private static long key(ChunkPos c) {
		return ((long) c.x() << 32) ^ (c.z() & 0xFFFFFFFFL);
	}

	private static double ease(double t) {
		return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
	}

	private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
		return new Vec3(Mth.lerp(t, a.x, b.x), Mth.lerp(t, a.y, b.y), Mth.lerp(t, a.z, b.z));
	}

	private static float lerpAngle(float a, float b, float t) {
		return a + Mth.wrapDegrees(b - a) * t;
	}
}
