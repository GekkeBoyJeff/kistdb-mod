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
 * real world around you. Every piece of claimed land is fenced by a see-through curtain that follows the ground and
 * has coloured motes floating over it (green = yours, blue = you may build there, red = someone else's), a floating
 * name with its size, and a small marker shows where you stand. The chunk in the middle of the screen is the cursor: WASD glides over the land (sprint = faster), click
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
	private static final double MIN_D = 14, MAX_D = 90, START_D = 32;
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

	/**
	 * One flat piece of the map, in world coordinates: a fill is the parallelogram A, B, C (fourth corner B + C - A),
	 * a triangle the triangle A, B, C, a curtain the strip from its foot A-B up to C = A + its height. kind null =
	 * the cursor.
	 */
	private record Piece(int type, @Nullable Kind kind, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
			float cz) {
		Vector3f a() {
			return new Vector3f(this.ax, this.ay, this.az);
		}

		Vector3f b() {
			return new Vector3f(this.bx, this.by, this.bz);
		}

		Vector3f c() {
			return new Vector3f(this.cx, this.cy, this.cz);
		}
	}

	private static final int QUAD = 0, TRI = 1, WALL = 2;
	/** The map's skin is a grid of 4x4 blocks (four cells to a chunk side). */
	private static final int CELL = 4;
	/** How far the skin floats over the ground: claimed land, and the cursor a bit higher so they never fight. */
	private static final float LIFT = 0.06f, CURSOR_LIFT = 0.16f;
	// The models as the client draws them inside a display entity: centred and turned half a turn around y. A flat
	// square (and the triangle cut from it: corners FLAT_A, FLAT_B, FLAT_C), a standing square, and a whole block.
	private static final Vector3f FLAT_A = new Vector3f(0.5f, 0, 0.5f), FLAT_B = new Vector3f(-0.5f, 0, 0.5f),
			FLAT_C = new Vector3f(0.5f, 0, -0.5f), UP = new Vector3f(0, 1, 0);
	private static final Vector3f WALL_A = new Vector3f(0.5f, -0.5f, 0), WALL_B = new Vector3f(-0.5f, -0.5f, 0),
			WALL_C = new Vector3f(0.5f, 0.5f, 0), ACROSS = new Vector3f(0, 0, 1);
	private static final Vector3f CUBE_A = new Vector3f(0, 0, 0), CUBE_B = new Vector3f(1, 0, 0), CUBE_C = new Vector3f(0, 1, 0);

	/** A change to a display that should start a few ticks from now (so the client first has the old state). */
	private record Anim(DisplayElement el, int at, @Nullable Vector3f translation, @Nullable Vector3f scale, int duration, boolean remove,
			@Nullable Runnable apply) {
	}

	private static final class View {
		final ServerPlayer p;
		final ServerLevel level;
		final Vec3 origin;
		final ElementHolder holder = new ElementHolder();
		final ItemDisplayElement cam = new ItemDisplayElement();
		final TextDisplayElement you = new TextDisplayElement();
		/** Border curtains of claimed land, and the ones around the cursor chunk. */
		final Map<Piece, DisplayElement> walls = new HashMap<>();
		final Map<Piece, DisplayElement> cursorWalls = new HashMap<>();
		/** The see-through skin lying on claimed land, and on the cursor chunk. */
		final Map<Piece, DisplayElement> fills = new HashMap<>();
		final Map<Piece, DisplayElement> cursorFills = new HashMap<>();
		boolean pack;
		final List<TextDisplayElement> labels = new ArrayList<>();
		final Map<Long, Integer> tops = new HashMap<>();
		/** Height of the skin at each grid corner. */
		final Map<Long, Float> corners = new HashMap<>();
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

		v.pack = Pack.has(p);

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
			// The curtains sink into the ground
			for (Map.Entry<Piece, DisplayElement> e : v.walls.entrySet()) lower(v, e.getKey(), e.getValue(), 0, 7);
			for (Map.Entry<Piece, DisplayElement> e : v.cursorWalls.entrySet()) lower(v, e.getKey(), e.getValue(), 0, 5);
			for (Map.Entry<Piece, DisplayElement> e : v.fills.entrySet()) lower(v, e.getKey(), e.getValue(), 0, 6);
			for (Map.Entry<Piece, DisplayElement> e : v.cursorFills.entrySet()) lower(v, e.getKey(), e.getValue(), 0, 4);
			for (TextDisplayElement label : v.labels) label.setScale(new Vector3f(0.01f));
			v.you.setScale(new Vector3f(0.01f));
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
			}
			if (v.age % 5 == 0) motes(v);
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

	/**
	 * Q while the map is open closes it (Escape can't: the pause menu opens on your own computer and the server never
	 * hears of it). Dropping or swapping hands would act on the real items you can't see, so those are ignored.
	 */
	static boolean onAction(ServerPlayer p, net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action action) {
		View v = VIEWS.get(p.getUUID());
		if (v == null) return false;
		switch (action) {
			case DROP_ITEM, DROP_ALL_ITEMS -> {
				emptyHands(p);
				close(p, false);
				return true;
			}
			case SWAP_ITEM_WITH_OFFHAND -> {
				emptyHands(p);
				return true;
			}
			default -> {
				return false;
			}
		}
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
		double y = top(v, c) + 1.2;
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

	/**
	 * Everyone's land around you: a see-through skin lying over the ground (it bends smoothly over hills, no steps),
	 * fenced by curtains that are bright at the foot and fade upwards. Only the outside edge of each piece of land
	 * gets a curtain, so you see whole areas, not tiles. Kept pieces stay, new ones grow, gone ones shrink away.
	 */
	private static void buildPlates(View v, boolean opening) {
		ChunkPos center = new ChunkPos(Mth.floor(v.origin.x) >> 4, Mth.floor(v.origin.z) >> 4);
		Set<Piece> want = new HashSet<>();
		Set<Piece> fills = new HashSet<>();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				ChunkPos c = new ChunkPos(center.x() + dx, center.z() + dz);
				ClaimsMod.Claim cl = ClaimsMod.claimAt(v.level, c);
				if (cl == null) continue;
				Kind kind = kind(v, c);
				for (int side = 0; side < 4; side++) {
					ChunkPos n = neighbour(c, side);
					ClaimsMod.Claim nc = ClaimsMod.claimAt(v.level, n);
					if (nc != null && nc.owner.equals(cl.owner)) continue;
					edge(v, c, side, kind, 3.2f, want);
				}
				if (v.pack) fill(v, c, kind, fills);
			}
		}
		sync(v, v.walls, want, opening);
		sync(v, v.fills, fills, opening);
		labels(v, center);
	}

	/**
	 * Height of the skin at a grid corner (block coordinates, multiples of {@link #CELL}): about the highest ground
	 * around it, ignoring a lone tree trunk or pole, so the skin lies over the land instead of cutting through it.
	 */
	private static float corner(View v, int x, int z) {
		return v.corners.computeIfAbsent(key(x, z), k -> {
			int[] h = new int[36];
			int i = 0;
			for (int dx = -3; dx < 3; dx++) {
				for (int dz = -3; dz < 3; dz++) h[i++] = v.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + dx, z + dz);
			}
			java.util.Arrays.sort(h);
			return (float) h[33];
		});
	}

	/**
	 * The skin on one chunk: flat rectangles where the land is flat, a tilted square where it slopes evenly, and two
	 * triangles (folded along the higher diagonal) where it bends. Neighbouring pieces share their corners exactly,
	 * so it is one smooth surface.
	 */
	private static void fill(View v, ChunkPos c, @Nullable Kind kind, Set<Piece> out) {
		float lift = kind == null ? CURSOR_LIFT : LIFT;
		int x0 = c.getMinBlockX(), z0 = c.getMinBlockZ();
		int n = 16 / CELL;
		float[][] h = new float[n + 1][n + 1];
		for (int i = 0; i <= n; i++) {
			for (int j = 0; j <= n; j++) h[i][j] = corner(v, x0 + i * CELL, z0 + j * CELL) + lift;
		}
		boolean[][] used = new boolean[n][n];
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				if (used[i][j]) continue;
				float a = h[i][j], b = h[i + 1][j], cc = h[i][j + 1], d = h[i + 1][j + 1];
				float x = x0 + i * CELL, z = z0 + j * CELL;
				if (flat(h, i, j)) {
					// As big a flat rectangle as the land allows at this height
					int w = 1;
					while (i + w < n && !used[i + w][j] && flat(h, i + w, j) && h[i + w][j] == a) w++;
					int rows = 1;
					grow:
					while (j + rows < n) {
						for (int k = i; k < i + w; k++) {
							if (used[k][j + rows] || !flat(h, k, j + rows) || h[k][j + rows] != a) break grow;
						}
						rows++;
					}
					for (int k = i; k < i + w; k++) {
						for (int l = j; l < j + rows; l++) used[k][l] = true;
					}
					out.add(new Piece(QUAD, kind, x, a, z, x + w * CELL, a, z, x, a, z + rows * CELL));
					continue;
				}
				used[i][j] = true;
				if (a + d == b + cc) {
					out.add(new Piece(QUAD, kind, x, a, z, x + CELL, b, z, x, cc, z + CELL));
				} else if (a + d > b + cc) {
					out.add(new Piece(TRI, kind, x, a, z, x + CELL, b, z, x + CELL, d, z + CELL));
					out.add(new Piece(TRI, kind, x, a, z, x + CELL, d, z + CELL, x, cc, z + CELL));
				} else {
					out.add(new Piece(TRI, kind, x + CELL, b, z, x, cc, z + CELL, x, a, z));
					out.add(new Piece(TRI, kind, x + CELL, b, z, x + CELL, d, z + CELL, x, cc, z + CELL));
				}
			}
		}
	}

	private static boolean flat(float[][] h, int i, int j) {
		return h[i][j] == h[i + 1][j] && h[i][j] == h[i][j + 1] && h[i][j] == h[i + 1][j + 1];
	}

	private static ChunkPos neighbour(ChunkPos c, int side) {
		return switch (side) {
			case 0 -> new ChunkPos(c.x(), c.z() - 1); // north
			case 1 -> new ChunkPos(c.x() + 1, c.z()); // east
			case 2 -> new ChunkPos(c.x(), c.z() + 1); // south
			default -> new ChunkPos(c.x() - 1, c.z()); // west
		};
	}

	/**
	 * One side of a chunk as curtain strips standing on the skin's edge: one slanted strip per grid cell, so the
	 * curtain climbs and dips with the land without steps or gaps.
	 */
	private static void edge(View v, ChunkPos c, int side, @Nullable Kind kind, float height, Set<Piece> out) {
		float lift = kind == null ? CURSOR_LIFT : LIFT;
		int x0 = c.getMinBlockX(), z0 = c.getMinBlockZ();
		boolean alongX = side == 0 || side == 2;
		int fixed = switch (side) {
			case 0 -> z0;
			case 1 -> x0 + 16;
			case 2 -> z0 + 16;
			default -> x0;
		};
		// The border line itself, nudged a hair inwards so two neighbours' curtains don't fight
		float line = fixed + (side == 0 || side == 3 ? 0.04f : -0.04f);
		int from = alongX ? x0 : z0;
		for (int i = 0; i < 16; i += CELL) {
			int p = from + i, q = from + i + CELL;
			float ay = (alongX ? corner(v, p, fixed) : corner(v, fixed, p)) + lift;
			float by = (alongX ? corner(v, q, fixed) : corner(v, fixed, q)) + lift;
			if (alongX) out.add(new Piece(WALL, kind, p, ay, line, q, by, line, p, ay + height, line));
			else out.add(new Piece(WALL, kind, line, ay, p, line, by, q, line, ay + height, p));
		}
	}

	/** Make the shown pieces match {@code want}: new ones grow, gone ones shrink and disappear. */
	private static void sync(View v, Map<Piece, DisplayElement> shown, Set<Piece> want, boolean wave) {
		for (Iterator<Map.Entry<Piece, DisplayElement>> it = shown.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Piece, DisplayElement> e = it.next();
			if (want.contains(e.getKey())) continue;
			lower(v, e.getKey(), e.getValue(), v.age + 1, 5);
			animate(v, e.getValue(), v.age + 7, null, null, 0, true);
			it.remove();
		}
		for (Piece s : want) {
			if (shown.containsKey(s)) continue;
			DisplayElement el = element(v, s);
			v.holder.addElement(el);
			shown.put(s, el);
			int delay = 1;
			if (wave) delay += 4 + (int) (Math.hypot(s.ax() - v.origin.x, s.az() - v.origin.z) / 6);
			raise(v, s, el, v.age + delay, s.kind() == null ? 3 : 7);
		}
	}

	/** A piece of the map, still shrunk to nothing (it grows with {@link #raise}). */
	private static DisplayElement element(View v, Piece s) {
		DisplayElement el;
		if (v.pack) {
			ItemDisplayElement item = new ItemDisplayElement();
			String model = (s.type() == WALL ? "claim/wall_" : s.type() == TRI ? "claim/tri_" : "claim/fill_") + wallName(s.kind());
			ItemStack stack = new ItemStack(Items.PAPER);
			stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("libreria", model));
			item.setItem(stack);
			item.setItemDisplayContext(net.minecraft.world.item.ItemDisplayContext.NONE);
			el = item;
		} else {
			el = new BlockDisplayElement(glass(s.kind() == null ? "yellow" : switch (s.kind()) {
				case MINE -> "lime";
				case TRUSTED -> "light_blue";
				case OTHER -> "red";
				case FREE -> "white";
			}).defaultBlockState());
		}
		el.setBrightness(FULL);
		el.setViewRange(8f);
		el.setOffset(new Vec3(s.ax(), s.ay(), s.az()).subtract(v.origin));
		shrunk(v, s).applyTo(el);
		return el;
	}

	private static String wallName(@Nullable Kind kind) {
		if (kind == null) return "cursor";
		return switch (kind) {
			case MINE -> "mine";
			case TRUSTED -> "trusted";
			default -> "other";
		};
	}

	/** The model bent exactly onto the piece (relative to the piece's corner A, where the display stands). */
	private static Affine shape(View v, Piece s) {
		Vector3f a = new Vector3f(), b = s.b().sub(s.a()), c = s.c().sub(s.a());
		if (s.type() == WALL) {
			Vector3f across = new Vector3f(b.z, 0, -b.x).normalize();
			if (!v.pack) return Affine.fit(CUBE_A, CUBE_B, CUBE_C, ACROSS, a, b, c, across.mul(0.05f));
			return Affine.fit(WALL_A, WALL_B, WALL_C, ACROSS, a, b, c, across);
		}
		Vector3f normal = new Vector3f(b).cross(c).normalize();
		return Affine.fit(FLAT_A, FLAT_B, FLAT_C, UP, a, b, c, normal);
	}

	/** Shrunk to nothing around the middle of a curtain's foot or of a fill, where it grows from and shrinks to. */
	private static Affine shrunk(View v, Piece s) {
		Vector3f anchor;
		if (s.type() == WALL) anchor = v.pack ? new Vector3f(0, -0.5f, 0) : new Vector3f(0.5f, 0, 0);
		else if (s.type() == TRI) anchor = new Vector3f(FLAT_A).add(FLAT_B).add(FLAT_C).div(3);
		else anchor = new Vector3f();
		return shape(v, s).scaledAbout(anchor, 0.02f);
	}

	private static void raise(View v, Piece s, DisplayElement el, int at, int duration) {
		v.anims.add(new Anim(el, at, null, null, duration, false, () -> shape(v, s).applyTo(el)));
	}

	private static void lower(View v, Piece s, DisplayElement el, int at, int duration) {
		v.anims.add(new Anim(el, at, null, null, duration, false, () -> shrunk(v, s).applyTo(el)));
	}

	private static void animate(View v, DisplayElement el, int at, @Nullable Vector3f translation, @Nullable Vector3f scale, int duration, boolean remove) {
		v.anims.add(new Anim(el, at, translation, scale, duration, remove, null));
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
			if (a.apply() != null) a.apply().run();
			if (a.translation() != null) a.el().setTranslation(a.translation());
			if (a.scale() != null) a.el().setScale(a.scale());
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

	/** A low golden curtain around the chunk in the middle of the screen. */
	private static void placeCursor(View v, ChunkPos c, boolean tick) {
		Set<Piece> want = new HashSet<>();
		for (int side = 0; side < 4; side++) edge(v, c, side, null, 1.8f, want);
		sync(v, v.cursorWalls, want, false);
		Set<Piece> fill = new HashSet<>();
		if (v.pack) fill(v, c, null, fill);
		sync(v, v.cursorFills, fill, false);
		if (tick) Ui.sound(v.p, SoundEvents.UI_BUTTON_CLICK.value(), 0.06f, 1.9f);
	}

	/** Soft coloured motes floating over claimed land near the middle of the screen, so whole areas read as claimed. */
	private static void motes(View v) {
		if (v.cursorChunk == null) return;
		java.util.Random r = new java.util.Random(v.age * 31L + v.p.getId());
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				ChunkPos c = new ChunkPos(v.cursorChunk.x() + dx, v.cursorChunk.z() + dz);
				Kind kind = kind(v, c);
				if (kind == Kind.FREE) continue;
				double x = c.getMinBlockX() + r.nextDouble() * 16, z = c.getMinBlockZ() + r.nextDouble() * 16;
				double y = v.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) + 0.4 + r.nextDouble() * 2.2;
				v.level.sendParticles(v.p, new net.minecraft.core.particles.DustParticleOptions(kind.color(), 1.6f), true, true, x, y, z, 1, 0, 0.05, 0, 0);
			}
		}
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
		line.append(Component.literal("   WASD move · scroll zoom · sneak or Q back").withStyle(ChatFormatting.DARK_GRAY));
		Hud.show(v.p, HUD, line, Hud.PRIO_INFO, 10);
	}

	// ---------------------------------------------------------------- maths

	/** Names stay readable when you zoom out. */
	private static float labelScale(View v) {
		return (float) Math.max(2.0, v.targetDist / 6.0);
	}

	private static float youScale(View v) {
		return (float) Math.max(1.5, v.targetDist / 10.0);
	}

	private static long key(ChunkPos c) {
		return key(c.x(), c.z());
	}

	private static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	private static double ease(double t) {
		return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
	}

	private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
		return new Vec3(Mth.lerp(t, a.x, b.x), Mth.lerp(t, a.y, b.y), Mth.lerp(t, a.z, b.z));
	}
}
