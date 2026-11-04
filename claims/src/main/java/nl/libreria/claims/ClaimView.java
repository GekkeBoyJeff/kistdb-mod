package nl.libreria.claims;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Hud;
import nl.libreria.core.Pack;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The land map. Your camera swoops back and up into a third-person view over your own head and looks down at the
 * real world around you. Claimed chunks are coloured glass plates with a glowing edge (green = yours, blue = you may
 * build there, red = someone else's), each piece of land has a floating name, and a small marker shows where you
 * stand. The chunk in the middle of the screen is the cursor: WASD glides over the land (sprint = faster), click
 * claims it (with a little celebration), right-click releases it, scroll zooms, sneak flies back down. Getting hurt
 * closes it at once.
 *
 * <p>Fully server-side: the camera is a virtual entity the client looks through, everything else is display
 * entities only you can see. While the camera is elsewhere the client doesn't send where it looks, but it does send
 * its movement keys, clicks, the scroll wheel and sneaking, which is all the map needs.
 */
final class ClaimView {
	/** How far you can glide, in chunks from where you stand. */
	static final int RADIUS = 8;
	private static final int OPEN_TICKS = 28, CLOSE_TICKS = 16;
	/** Camera distance (zoom) and how steep it looks down. */
	private static final double MIN_D = 16, MAX_D = 96, START_D = 44;
	private static final float PITCH = 56f;
	private static final Identifier FREEZE = Identifier.fromNamespaceAndPath("libreria", "claim_view");
	private static final Identifier EMPTY_MODEL = Identifier.fromNamespaceAndPath("libreria", "gui/empty");
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

	/** A change to a display that should start a few ticks from now (so the client first has the old state). */
	private record Anim(DisplayElement el, int at, Vector3f translation, Vector3f scale, int duration, boolean remove) {
	}

	private static final class View {
		final ServerPlayer p;
		final ServerLevel level;
		final Vec3 origin;
		final ElementHolder holder = new ElementHolder();
		final ItemDisplayElement cam = new ItemDisplayElement();
		final BlockDisplayElement cursor = new BlockDisplayElement(glass("white").defaultBlockState());
		final TextDisplayElement you = new TextDisplayElement();
		final Map<Long, BlockDisplayElement> plates = new HashMap<>();
		final Map<Long, Kind> kinds = new HashMap<>();
		final List<TextDisplayElement> labels = new ArrayList<>();
		final Map<Long, Integer> tops = new HashMap<>();
		final List<Anim> anims = new ArrayList<>();
		final Vec3 eyeStart;
		final float yawStart, pitchStart;
		/** Cursor (the point in the middle of the screen) and its glide speed. */
		double curX, curZ, curY, velX, velZ;
		double dist = START_D, targetDist = START_D;
		int age, closeAge = -1, lastSlot;
		boolean cameraSet, wasSwinging, sneakReleased;
		@Nullable ChunkPos cursorChunk;
		Vec3 camPos;
		float camPitch;

		View(ServerPlayer p) {
			this.p = p;
			this.level = p.level();
			this.origin = p.position();
			this.eyeStart = p.getEyePosition();
			this.yawStart = p.getYRot();
			this.pitchStart = p.getXRot();
			this.camPos = this.eyeStart;
			this.camPitch = this.pitchStart;
		}

		/** Ahead on the screen = the way you were facing when you opened the map. */
		double fx() {
			return -Mth.sin(this.yawStart * Mth.DEG_TO_RAD);
		}

		double fz() {
			return Mth.cos(this.yawStart * Mth.DEG_TO_RAD);
		}
	}

	private static final Map<UUID, View> VIEWS = new HashMap<>();

	private ClaimView() {
	}

	static Block glass(String color) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(color + "_stained_glass"));
	}

	static boolean isOpen(ServerPlayer p) {
		return VIEWS.containsKey(p.getUUID());
	}

	// ---------------------------------------------------------------- open and close

	static void open(ServerPlayer p) {
		if (isOpen(p)) return;
		if (p.isPassenger() || p.isSleeping() || p.isSpectator() || p.isFallFlying()) {
			ClaimsMod.msg(p, "Land on your feet first, then open the land map.", ChatFormatting.GRAY);
			return;
		}
		p.closeContainer();
		View v = new View(p);
		VIEWS.put(p.getUUID(), v);
		Services.setCamera(p.getUUID(), true);

		ChunkPos center = p.chunkPosition();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				ChunkPos c = new ChunkPos(center.x() + dx, center.z() + dz);
				v.tops.put(key(c), surface(v.level, c));
			}
		}
		v.curX = p.getX();
		v.curZ = p.getZ();
		v.curY = p.getY();
		v.lastSlot = p.getInventory().getSelectedSlot();
		v.sneakReleased = !p.isShiftKeyDown();

		// The camera: an invisible display entity the client looks through
		v.cam.setItem(ItemStack.EMPTY);
		v.cam.setTeleportDuration(2);
		v.cam.setOffset(v.eyeStart.subtract(v.origin));
		v.cam.setYaw(v.yawStart);
		v.cam.setPitch(v.pitchStart);
		v.holder.addElement(v.cam);

		// The cursor: a glowing frame on the chunk in the middle of the screen
		v.cursor.setGlowing(true);
		v.cursor.setGlowColorOverride(0xFFFFFF);
		v.cursor.setBrightness(FULL);
		v.cursor.setTeleportDuration(2);
		v.cursor.setViewRange(8f);
		v.cursor.setTranslation(new Vector3f(8, 0, 8));
		v.cursor.setScale(new Vector3f(0.01f, 0.14f, 0.01f));
		v.holder.addElement(v.cursor);

		// "You": a little marker over your head, so you can find yourself from up high
		v.you.setText(Component.literal("▼").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
				.append(Component.literal("\nYou").withStyle(ChatFormatting.YELLOW)));
		v.you.setBillboardMode(Display.BillboardConstraints.CENTER);
		v.you.setBrightness(FULL);
		v.you.setShadow(true);
		v.you.setBackground(0);
		v.you.setViewRange(8f);
		v.you.setOffset(new Vec3(0, p.getBbHeight() + 0.6, 0));
		v.you.setScale(new Vector3f(youScale(v)));
		v.holder.addElement(v.you);

		buildPlates(v, true);
		new ManualAttachment(v.holder, v.level, () -> v.origin);
		v.holder.startWatching(p);

		// Stand still while the map is open (transient: never saved)
		freeze(p, true);

		Ui.sound(p, SoundEvents.ELYTRA_FLYING, 0.22f, 1.7f);
		Ui.sound(p, SoundEvents.BOOK_PAGE_TURN, 0.8f, 0.8f);
		p.connection.send(new ClientboundSetTitlesAnimationPacket(6, 34, 12));
		p.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
		p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("◆ Land map ◆").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		emptyHands(p);
	}

	/** Back to normal: instantly (when you get hurt) or by swooping the camera back into your head first. */
	static void close(ServerPlayer p, boolean instant) {
		View v = VIEWS.get(p.getUUID());
		if (v == null) return;
		if (instant || !v.cameraSet) {
			finish(v);
			return;
		}
		if (v.closeAge < 0) {
			v.closeAge = 0;
			Ui.sound(p, SoundEvents.ELYTRA_FLYING, 0.18f, 2.0f);
			for (BlockDisplayElement plate : v.plates.values()) animate(v, plate, 0, shrunk(), smallScale(), 7, false);
			for (TextDisplayElement label : v.labels) label.setScale(new Vector3f(0.01f));
			v.you.setScale(new Vector3f(0.01f));
			animate(v, v.cursor, 0, new Vector3f(8, 0, 8), new Vector3f(0.01f, 0.14f, 0.01f), 6, false);
		}
	}

	private static void finish(View v) {
		VIEWS.remove(v.p.getUUID());
		Services.setCamera(v.p.getUUID(), false);
		v.holder.destroy();
		freeze(v.p, false);
		if (!v.p.hasDisconnected()) {
			v.p.connection.send(VirtualEntityUtils.createClientboundSetCameraPacket(v.p.getId()));
			Hud.hide(v.p, HUD);
			// Show the real hotbar again
			v.p.inventoryMenu.sendAllDataToRemote();
		}
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

	/**
	 * Only on your own screen: nothing in your hands, so no sword or arm hangs in front of the map. With the pack the
	 * main hand holds an invisible item (an empty hand would still show your arm).
	 */
	private static void emptyHands(ServerPlayer p) {
		var menu = p.inventoryMenu;
		ItemStack hand = ItemStack.EMPTY;
		if (Pack.has(p)) {
			hand = new ItemStack(Items.PAPER);
			hand.set(DataComponents.ITEM_MODEL, EMPTY_MODEL);
		}
		for (int slot = 36; slot <= 44; slot++) {
			p.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), slot, hand.copy()));
		}
		p.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), 45, ItemStack.EMPTY));
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
			runAnims(v);

			if (v.closeAge >= 0) {
				v.closeAge++;
				double t = ease(Math.min(1, v.closeAge / (double) CLOSE_TICKS));
				Vec3 eye = p.getEyePosition();
				moveCamera(v, lerp(v.camPos, eye, t), v.yawStart, Mth.lerp((float) t, v.camPitch, v.pitchStart));
				v.holder.tick();
				if (v.closeAge >= CLOSE_TICKS) finish(v);
				continue;
			}

			input(v);

			ChunkPos cc = new ChunkPos(Mth.floor(v.curX) >> 4, Mth.floor(v.curZ) >> 4);
			if (!cc.equals(v.cursorChunk)) {
				v.cursorChunk = cc;
				placeCursor(v, cc, true);
			} else if (v.age % 14 == 0) {
				breathe(v);
			}
			// The point the camera looks at follows the ground under the cursor, gently
			v.curY = Mth.lerp(0.15, v.curY, top(v, cc));
			v.dist = Mth.lerp(0.3, v.dist, v.targetDist);

			Vec3 look = new Vec3(v.curX, v.curY, v.curZ);
			if (v.age <= OPEN_TICKS) {
				// Swoop: out of your head, back and up, while tilting down
				double t = ease(v.age / (double) OPEN_TICKS);
				Vec3 from = lerp(p.getEyePosition(), look, t);
				float pitch = Mth.lerp((float) t, v.pitchStart, PITCH);
				Vec3 pos = cameraFor(v, from, v.dist * t, pitch);
				moveCamera(v, pos, v.yawStart, pitch);
				v.camPos = pos;
				v.camPitch = pitch;
			} else {
				Vec3 pos = cameraFor(v, look, v.dist, PITCH);
				moveCamera(v, pos, v.yawStart, PITCH);
				v.camPos = pos;
				v.camPitch = PITCH;
			}
			status(v);
			v.holder.tick();
		}
	}

	/** Where the camera hangs to look at {@code look} from {@code dist} away, {@code pitch} degrees down. */
	private static Vec3 cameraFor(View v, Vec3 look, double dist, float pitch) {
		double horizontal = dist * Mth.cos(pitch * Mth.DEG_TO_RAD);
		double up = dist * Mth.sin(pitch * Mth.DEG_TO_RAD);
		Vec3 pos = new Vec3(look.x - v.fx() * horizontal, look.y + up, look.z - v.fz() * horizontal);
		// Never inside a hill
		int ground = v.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(pos.x), Mth.floor(pos.z));
		return pos.y < ground + 1 ? new Vec3(pos.x, ground + 1, pos.z) : pos;
	}

	/** WASD glides over the land (relative to the screen), sprint goes faster, scroll zooms, sneak goes back. */
	private static void input(View v) {
		ServerPlayer p = v.p;
		var in = p.getLastClientInput();
		double speed = Math.max(0.5, v.dist / 46.0) * (in.sprint() ? 1.9 : 1.0);
		double ahead = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
		double side = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
		if (ahead != 0 && side != 0) {
			ahead *= 0.7071;
			side *= 0.7071;
		}
		// Right of the screen: the forward direction turned a quarter
		double rx = -v.fz(), rz = v.fx();
		double wantX = (v.fx() * ahead + rx * side) * speed;
		double wantZ = (v.fz() * ahead + rz * side) * speed;
		// Snappy start, soft stop
		double k = ahead != 0 || side != 0 ? 0.5 : 0.3;
		v.velX = Mth.lerp(k, v.velX, wantX);
		v.velZ = Mth.lerp(k, v.velZ, wantZ);
		double max = RADIUS * 16 + 8;
		v.curX = Mth.clamp(v.curX + v.velX, v.origin.x - max, v.origin.x + max);
		v.curZ = Mth.clamp(v.curZ + v.velZ, v.origin.z - max, v.origin.z + max);

		// Scroll = zoom (the hotbar slot changes)
		int slot = p.getInventory().getSelectedSlot();
		if (slot != v.lastSlot) {
			int d = slot - v.lastSlot;
			if (d > 4) d -= 9;
			if (d < -4) d += 9;
			v.targetDist = Mth.clamp(v.targetDist * Math.pow(1.18, d), MIN_D, MAX_D);
			v.lastSlot = slot;
			for (TextDisplayElement l : v.labels) l.setScale(new Vector3f(labelScale(v)));
			v.you.setScale(new Vector3f(youScale(v)));
			emptyHands(p);
		}
		// Click = claim (an arm swing reaches the server)
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
		ChunkPos c = v.cursorChunk;
		if (c == null) return;
		boolean changed = claim ? ClaimsMod.claim(v.p, v.level, c) : ClaimsMod.unclaim(v.p, v.level, c);
		if (!changed) {
			Ui.sound(v.p, SoundEvents.VILLAGER_NO, 0.5f, 1.2f);
			placeCursor(v, c, false);
			return;
		}
		buildPlates(v, false);
		placeCursor(v, c, false);
		double y = top(v, c) + 0.6;
		if (claim) {
			edgeParticles(v, c, y, ParticleTypes.HAPPY_VILLAGER);
			v.level.sendParticles(v.p, ParticleTypes.TOTEM_OF_UNDYING, true, true, c.getMinBlockX() + 8, y + 1, c.getMinBlockZ() + 8,
					60, 2.5, 0.6, 2.5, 0.45);
			Ui.sound(v.p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.2f);
		} else {
			edgeParticles(v, c, y, ParticleTypes.POOF);
			Ui.sound(v.p, SoundEvents.CANDLE_EXTINGUISH, 1f, 0.8f);
		}
	}

	/** Particles along the four edges of a chunk. */
	private static void edgeParticles(View v, ChunkPos c, double y, ParticleOptions type) {
		int x0 = c.getMinBlockX(), z0 = c.getMinBlockZ();
		for (int i = 0; i <= 16; i += 2) {
			for (double[] pt : new double[][] {{x0 + i, z0}, {x0 + i, z0 + 16}, {x0, z0 + i}, {x0 + 16, z0 + i}}) {
				v.level.sendParticles(v.p, type, true, true, pt[0], y, pt[1], 1, 0.05, 0.1, 0.05, 0.01);
			}
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

	private static Vector3f shrunk() {
		return new Vector3f(8, 0, 8);
	}

	private static Vector3f smallScale() {
		return new Vector3f(0.01f, 0.1f, 0.01f);
	}

	private static Vector3f full() {
		return new Vector3f(0.12f, 0, 0.12f);
	}

	private static Vector3f fullScale() {
		return new Vector3f(15.76f, 0.1f, 15.76f);
	}

	/** Coloured plates (with a glowing edge) on every claimed chunk around you, and the names. */
	private static void buildPlates(View v, boolean opening) {
		ChunkPos center = new ChunkPos(Mth.floor(v.origin.x) >> 4, Mth.floor(v.origin.z) >> 4);
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				ChunkPos c = new ChunkPos(center.x() + dx, center.z() + dz);
				Kind kind = kind(v, c);
				long key = key(c);
				BlockDisplayElement plate = v.plates.get(key);
				if (kind == Kind.FREE) {
					if (plate != null) {
						v.plates.remove(key);
						v.kinds.remove(key);
						// Fold away, then gone
						animate(v, plate, v.age + 1, shrunk(), smallScale(), 6, false);
						animate(v, plate, v.age + 8, shrunk(), smallScale(), 0, true);
					}
					continue;
				}
				if (plate == null) {
					plate = new BlockDisplayElement(kind.glass().defaultBlockState());
					plate.setBrightness(FULL);
					plate.setViewRange(8f);
					plate.setGlowing(true);
					plate.setGlowColorOverride(kind.color());
					plate.setOffset(new Vec3(c.getMinBlockX(), top(v, c) + 0.06, c.getMinBlockZ()).subtract(v.origin));
					plate.setTranslation(shrunk());
					plate.setScale(smallScale());
					v.holder.addElement(plate);
					v.plates.put(key, plate);
					// Pop in: a wave from where you stand when opening, a little bounce when you claim
					int at = v.age + 1 + (opening ? 4 + (Math.abs(dx) + Math.abs(dz)) * 2 : 0);
					if (opening) {
						animate(v, plate, at, full(), fullScale(), 8, false);
					} else {
						animate(v, plate, at, new Vector3f(-0.9f, 0, -0.9f), new Vector3f(17.8f, 0.1f, 17.8f), 5, false);
						animate(v, plate, at + 5, full(), fullScale(), 4, false);
					}
				} else if (v.kinds.get(key) != kind) {
					plate.setBlockState(kind.glass().defaultBlockState());
					plate.setGlowColorOverride(kind.color());
				}
				v.kinds.put(key, kind);
			}
		}
		labels(v, center);
	}

	private static void animate(View v, DisplayElement el, int at, Vector3f translation, Vector3f scale, int duration, boolean remove) {
		v.anims.add(new Anim(el, at, translation, scale, duration, remove));
	}

	private static void runAnims(View v) {
		for (Iterator<Anim> it = v.anims.iterator(); it.hasNext(); ) {
			Anim a = it.next();
			if (a.at() > v.age) continue;
			it.remove();
			if (a.remove()) {
				v.holder.removeElement(a.el());
				continue;
			}
			a.el().setInterpolationDuration(a.duration());
			a.el().setTranslation(a.translation());
			a.el().setScale(a.scale());
			a.el().startInterpolation();
		}
	}

	/** One floating name per connected piece of land of the same owner. */
	private static void labels(View v, ChunkPos center) {
		for (TextDisplayElement l : v.labels) v.holder.removeElement(l);
		v.labels.clear();
		Set<Long> done = new HashSet<>();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				ChunkPos start = new ChunkPos(center.x() + dx, center.z() + dz);
				ClaimsMod.Claim first = ClaimsMod.claimAt(v.level, start);
				if (first == null || !done.add(key(start))) continue;
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
				String size = group.size() == 1 ? "1 chunk" : group.size() + " chunks";
				MutableComponent text = (kind == Kind.MINE ? Component.literal("Your land")
						: Component.literal(first.ownerName)).withStyle(s -> s.withColor(kind.color()).withBold(true));
				text.append(Component.literal("\n" + size + (kind == Kind.TRUSTED ? " · you may build" : "")).withStyle(ChatFormatting.GRAY));
				TextDisplayElement label = new TextDisplayElement(text);
				label.setBillboardMode(Display.BillboardConstraints.CENTER);
				label.setBrightness(FULL);
				label.setViewRange(8f);
				label.setShadow(true);
				label.setBackground(0x70100A06);
				label.setScale(new Vector3f(labelScale(v)));
				label.setOffset(new Vec3(mid.getMinBlockX() + 8, top(v, mid) + 2.5, mid.getMinBlockZ() + 8).subtract(v.origin));
				v.holder.addElement(label);
				v.labels.add(label);
			}
		}
	}

	/** The glowing frame on the chunk in the middle of the screen; it slides over and breathes softly. */
	private static void placeCursor(View v, ChunkPos c, boolean tick) {
		Kind kind = kind(v, c);
		v.cursor.setOffset(new Vec3(c.getMinBlockX(), top(v, c) + 0.12, c.getMinBlockZ()).subtract(v.origin));
		v.cursor.setGlowColorOverride(kind == Kind.FREE ? 0xFFF2B0 : kind.color());
		v.cursor.setInterpolationDuration(3);
		v.cursor.setTranslation(new Vector3f(0.05f, 0, 0.05f));
		v.cursor.setScale(new Vector3f(15.9f, 0.16f, 15.9f));
		v.cursor.startInterpolation();
		if (tick) Ui.sound(v.p, SoundEvents.UI_BUTTON_CLICK.value(), 0.06f, 1.9f);
	}

	private static void breathe(View v) {
		boolean in = (v.age / 14) % 2 == 0;
		float m = in ? 0.6f : 0.05f;
		v.cursor.setInterpolationDuration(14);
		v.cursor.setTranslation(new Vector3f(m, 0, m));
		v.cursor.setScale(new Vector3f(16 - 2 * m, 0.16f, 16 - 2 * m));
		v.cursor.startInterpolation();
	}

	/** What's in the middle of the screen, in the action bar. */
	private static void status(View v) {
		ChunkPos c = v.cursorChunk;
		if (c == null) return;
		ClaimsMod.Claim cl = ClaimsMod.claimAt(v.level, c);
		Kind kind = kind(v, c);
		int used = ClaimsMod.owned(v.p.getUUID()), max = ClaimsMod.budget(v.p);
		MutableComponent line = Component.empty();
		switch (kind) {
			case FREE -> line.append(Component.literal("Free land").withStyle(ChatFormatting.WHITE))
					.append(Component.literal(used < max ? "  ·  click to claim" : "  ·  your budget is full")
							.withStyle(used < max ? ChatFormatting.YELLOW : ChatFormatting.RED));
			case MINE -> line.append(Component.literal("Your land").withStyle(ChatFormatting.GREEN))
					.append(Component.literal("  ·  right-click to release").withStyle(ChatFormatting.YELLOW));
			case TRUSTED -> line.append(Component.literal(cl.ownerName + "'s land · you may build").withStyle(ChatFormatting.AQUA));
			case OTHER -> line.append(Component.literal(cl.ownerName + "'s land").withStyle(ChatFormatting.RED));
		}
		line.append(Component.literal("   " + used + "/" + max + " chunks").withStyle(ChatFormatting.GRAY));
		line.append(Component.literal("   WASD move · scroll zoom · sneak back").withStyle(ChatFormatting.DARK_GRAY));
		Hud.show(v.p, HUD, line, Hud.PRIO_INFO, 10);
	}

	// ---------------------------------------------------------------- maths

	/** Names stay readable when you zoom out. */
	private static float labelScale(View v) {
		return (float) Math.max(1.5, v.targetDist / 11.0);
	}

	private static float youScale(View v) {
		return (float) Math.max(1.2, v.targetDist / 16.0);
	}

	private static long key(ChunkPos c) {
		return ((long) c.x() << 32) ^ (c.z() & 0xFFFFFFFFL);
	}

	private static double ease(double t) {
		return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
	}

	private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
		return new Vec3(Mth.lerp(t, a.x, b.x), Mth.lerp(t, a.y, b.y), Mth.lerp(t, a.z, b.z));
	}
}
