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
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Hotkeys;
import nl.libreria.core.Hud;
import nl.libreria.core.Hud.HotbarButton;
import nl.libreria.core.Pack;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.TopCard;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The land map. Your camera swoops back and up into a third-person view over your own head and looks down at the
 * real world around you. Claimed land stands there as see-through glass buildings with bright frames (green = yours,
 * blue = you may build there, red = someone else's), each with its name (or its owner's) on top. The chunk you look
 * at (under the crosshair) has a golden frame: that is what you claim (click) or release (right-click); jump names the
 * piece of your land under it. From there the camera is free: the mouse looks around (straight down too, for a view
 * from above), WASD flies, sneak goes up and sprint goes down, scroll flies closer or further along where you look;
 * it never goes into the ground or through walls. Q or the land map key (sneak + swap hands) takes you back. Getting
 * hurt closes it at once. With the pack a card at the top of the screen says whose land it is and how much you may
 * still claim.
 *
 * <p>Fully server-side: the camera is a virtual entity the client looks through, everything else is display
 * entities only you can see. While the camera is elsewhere the client doesn't send where it looks, except when it
 * rides something: so you sit on an invisible seat (only on your screen) and the mouse reaches the server. It also
 * sends its movement keys, clicks, the scroll wheel, jumping, sneaking and the swap / drop keys.
 */
public final class ClaimView {
	/** How far you can glide, in chunks from where you stand. */
	static final int RADIUS = 8;
	private static final int OPEN_TICKS = 28, CLOSE_TICKS = 16;
	/** Camera distance (zoom) and how steep it looks down. */
	private static final double MIN_D = 14, MAX_D = 90, START_D = 34;
	private static final float PITCH = 56f, MIN_PITCH = -70f, MAX_PITCH = 89.5f;
	/** How far the crosshair reaches to find the chunk you look at. */
	private static final double REACH = 260;
	/** Roofs float this high over the highest ground of a piece of land; the cursor box over free land is lower. */
	private static final int ROOF = 3, CURSOR_ROOF = 2;
	private static final float ROD = 0.14f, CURSOR_ROD = 0.26f;
	private static final Identifier FREEZE = Identifier.fromNamespaceAndPath("libreria", "claim_view");
	private static final Identifier EMPTY_MODEL = Identifier.fromNamespaceAndPath("libreria", "gui/empty");
	private static final Brightness FULL = new Brightness(15, 15);
	private static final String HUD = "claims.view";
	private static final String CARD = "claims.map";

	private enum Kind {
		FREE, MINE, TRUSTED, OTHER, DENIED;

		int color() {
			return switch (this) {
				case MINE -> 0x55FF55;
				case TRUSTED -> 0x55FFFF;
				case OTHER, DENIED -> 0xFF5555;
				case FREE -> 0xFFFFFF;
			};
		}

		String glass() {
			return switch (this) {
				case MINE -> "lime";
				case TRUSTED -> "light_blue";
				case OTHER, DENIED -> "red";
				case FREE -> "white";
			};
		}

		String model() {
			return switch (this) {
				case MINE -> "mine";
				case TRUSTED -> "trusted";
				default -> "other";
			};
		}
	}

	private static final int WALL = 0, ROOF_PIECE = 1, ROD_PIECE = 2;
	private static final int AXIS_X = 0, AXIS_Y = 1, AXIS_Z = 2;

	/**
	 * One piece of a glass building, standing at (x, z): a wall (along x or z, from {@code low} up to {@code high}), a
	 * roof (a {@code len} x {@code depth} square at {@code high}) or a rod (vertical from {@code low} to {@code high},
	 * or lying along x / z at {@code high}). Everything grows out of the ground at {@code base}. kind null = the cursor.
	 */
	private record Piece(int type, @Nullable Kind kind, int axis, float x, float z, float low, float high, float len, float depth, float base) {
	}

	/** A connected piece of land of one owner. */
	private record Region(ClaimsMod.Claim first, Kind kind, Set<Long> chunks, List<ChunkPos> list, int roof, int base) {
	}

	/** A change to a display that should start a few ticks from now (so the client first has the old state). */
	private record Anim(DisplayElement el, int at, int duration, boolean remove, @Nullable Runnable apply) {
	}

	private static final class View {
		final ServerPlayer p;
		final ServerLevel level;
		final Vec3 origin;
		final ElementHolder holder = new ElementHolder();
		final ItemDisplayElement cam = new ItemDisplayElement();
		/** The invisible seat you sit on while the map is open: riding, the client sends where the mouse turns. */
		final ItemDisplayElement seat = new ItemDisplayElement();
		final TextDisplayElement you = new TextDisplayElement();
		/** The buildings, and the golden box around the cursor chunk. */
		final Map<Piece, DisplayElement> pieces = new HashMap<>();
		final Map<Piece, DisplayElement> cursor = new HashMap<>();
		boolean pack;
		final List<TextDisplayElement> labels = new ArrayList<>();
		final List<Region> regions = new ArrayList<>();
		final Map<Long, Integer> highest = new HashMap<>();
		final List<Anim> anims = new ArrayList<>();
		final Vec3 eyeStart;
		final float yawStart, pitchStart;
		/** Where the camera starts looking (where you stand), how fast it flies now, and how high it is over the ground. */
		double curX, curZ, curY, velX, velY, velZ;
		double dist = START_D, targetDist = START_D;
		int age, closeAge = -1, lastSlot, deniedUntil = -1;
		boolean cameraSet, wasSwinging, wasJumping, sneakReleased, denied;
		/** Which way the view looks and how steeply (the mouse turns and tilts it), and the last rotation seen. */
		float yaw, pitch = PITCH, lastYaw, lastPitch;
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
			this.yaw = this.yawStart;
			this.lastYaw = this.yawStart;
			this.lastPitch = this.pitchStart;
		}

		/** Ahead on the screen = the way the view looks (where you faced when you opened it, turned by the mouse). */
		double fx() {
			return -Mth.sin(this.yaw * Mth.DEG_TO_RAD);
		}

		double fz() {
			return Mth.cos(this.yaw * Mth.DEG_TO_RAD);
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
			nl.libreria.core.Notify.warn(p, net.minecraft.world.item.Items.FILLED_MAP, "Land first", "Stand on the ground, then open the land map.");
			return;
		}
		p.closeContainer();
		View v = new View(p);
		VIEWS.put(p.getUUID(), v);
		Services.setCamera(p.getUUID(), true);

		v.curX = p.getX();
		v.curZ = p.getZ();
		v.curY = p.getY();
		v.targetDist = START_D;
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

		// The seat, right where you stand
		v.seat.setItem(ItemStack.EMPTY);
		v.holder.addElement(v.seat);

		buildings(v, true);
		new ManualAttachment(v.holder, v.level, () -> v.origin);
		v.holder.startWatching(p);
		p.connection.send(VirtualEntityUtils.createClientboundSetPassengersPacket(v.seat.getEntityId(), new int[] {p.getId()}));

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
			Hud.hide(p, HUD);
			TopCard.hide(p, CARD);
			Ui.sound(p, SoundEvents.ELYTRA_FLYING, 0.18f, 2.0f);
			// The buildings sink into the ground
			for (Map.Entry<Piece, DisplayElement> e : v.pieces.entrySet()) lower(v, e.getKey(), e.getValue(), 0, 7);
			for (Map.Entry<Piece, DisplayElement> e : v.cursor.entrySet()) lower(v, e.getKey(), e.getValue(), 0, 4);
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
			TopCard.hide(v.p, CARD);
			// Show the real hotbar again
			v.p.inventoryMenu.sendAllDataToRemote();
		}
	}

	/** The land changed (a name): rebuild what the open map shows. */
	static void refresh(ServerPlayer p) {
		View v = VIEWS.get(p.getUUID());
		if (v == null || v.closeAge >= 0) return;
		buildings(v, false);
		if (v.cursorChunk != null) placeCursor(v, v.cursorChunk);
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
		// Something in the hand (invisible with the pack): the client only sends a right-click with an item in hand
		ItemStack hand = new ItemStack(Items.PAPER);
		if (Pack.has(p)) hand.set(DataComponents.ITEM_MODEL, EMPTY_MODEL);
		for (int slot = 36; slot <= 44; slot++) {
			p.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), slot, hand.copy()));
		}
		p.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), 45, ItemStack.EMPTY));
	}

	// ---------------------------------------------------------------- keys

	/**
	 * Swap hands and drop (from {@link Hotkeys}). While the map is open they take you back (drop and swap would act on
	 * the real items you can't see). Otherwise the land map key opens it.
	 */
	static boolean onKey(ServerPlayer p, Hotkeys.Key key, boolean sneaking) {
		if (!ClaimsMod.MODULE.on()) return false;
		View v = VIEWS.get(p.getUUID());
		if (v != null) {
			emptyHands(p);
			// {swap} alone (the map key being sneak + swap): the coloured land off or on again; anything else goes back
			if (key == Hotkeys.Key.SWAP_HANDS && !sneaking && ClaimsMod.MAP_KEY.get(p) == 0) {
				toggleColours(p);
				return true;
			}
			close(p, false);
			return true;
		}
		if (key != Hotkeys.Key.SWAP_HANDS) return false;
		int mode = ClaimsMod.MAP_KEY.get(p);
		if (mode == 2 || mode == 0 && !sneaking) return false;
		open(p);
		return true;
	}

	/** The coloured glass on the land map off or on (a player setting; an operator may have set it for everybody). */
	static void toggleColours(ServerPlayer p) {
		if (ClaimsMod.COLOURS.isForced()) {
			Hud.flash(p, Component.literal("Land colours are set for everybody on this server").withStyle(ChatFormatting.GRAY));
			return;
		}
		boolean on = !ClaimsMod.COLOURS.get(p);
		ClaimsMod.COLOURS.set(p, on);
		Hud.flash(p, Component.literal(on ? "Land colours on" : "Land colours off · only the names").withStyle(ChatFormatting.YELLOW));
		Ui.sound(p, SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, on ? 1.3f : 1.0f);
	}

	/**
	 * A right-click packet (also with an empty hand: the client sends one for the land map's hidden item). True when the
	 * land map is open: then it's the map's, and nothing else happens.
	 */
	public static boolean rightClick(ServerPlayer p) {
		if (!isOpen(p)) return false;
		// (only the main hand holds something on the client while the map is open: one packet per click)
		onUse(p);
		emptyHands(p);
		return true;
	}

	/** Right-click while the map is open. */
	static boolean onUse(ServerPlayer p) {
		View v = VIEWS.get(p.getUUID());
		if (v == null) return false;
		if (v.age > OPEN_TICKS && v.closeAge < 0) clickClaim(v, false);
		return true;
	}

	/** The button right of the hotbar: the land map and its key. */
	static @Nullable HotbarButton hotbarButton(ServerPlayer p) {
		if (!ClaimsMod.MAP_BUTTON.get(p) || p.isSpectator() || isOpen(p)) return null;
		int mode = ClaimsMod.MAP_KEY.get(p);
		if (mode == 2) return null;
		MutableComponent key = Component.empty();
		int width = nl.libreria.core.gui.Glyphs.width(Component.literal("F"));
		if (mode == 0) {
			key.append(Component.literal(nl.libreria.core.gui.Glyphs.g("hb.shift")));
			width += nl.libreria.core.gui.Glyphs.advance("hb.shift");
		}
		key.append(Component.keybind("key.swapOffhand"));
		return new HotbarButton("hb.map", key, width);
	}

	// ---------------------------------------------------------------- every tick

	static void tick(MinecraftServer server) {
		if (!ClaimsMod.MODULE.on()) return;
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
				// Back into your head, looking the way the map looked (the mouse turned you too)
				moveCamera(v, lerp(v.camPos, eye, t), Mth.rotLerp((float) t, v.yaw, p.getYRot()), Mth.lerp((float) t, v.camPitch, p.getXRot()));
				v.holder.tick();
				if (v.closeAge >= CLOSE_TICKS) finish(v);
				continue;
			}

			input(v);

			if (v.age <= OPEN_TICKS) {
				// Swoop: out of your head, back and up over it, while tilting down
				double t = ease(v.age / (double) OPEN_TICKS);
				Vec3 look = new Vec3(v.curX, v.curY, v.curZ);
				Vec3 from = lerp(p.getEyePosition(), look, t);
				float pitch = Mth.lerp((float) t, v.pitchStart, v.pitch);
				Vec3 pos = cameraFor(v, from, START_D * t, pitch);
				moveCamera(v, pos, v.yaw, pitch);
				v.camPos = pos;
				v.camPitch = pitch;
			} else {
				moveCamera(v, v.camPos, v.yaw, v.pitch);
				v.camPitch = v.pitch;
			}
			// How high over the ground: labels and the marker over your head grow with it
			v.targetDist = Mth.clamp(v.camPos.y - ground(v, Mth.floor(v.camPos.x), Mth.floor(v.camPos.z)), MIN_D, MAX_D);

			// The chunk under the crosshair
			ChunkPos cc = v.age > OPEN_TICKS ? aimed(v) : new ChunkPos(Mth.floor(v.curX) >> 4, Mth.floor(v.curZ) >> 4);
			boolean deniedOver = v.denied && v.age >= v.deniedUntil;
			if (!java.util.Objects.equals(cc, v.cursorChunk) || deniedOver) {
				if (cc != null && !cc.equals(v.cursorChunk)) Ui.sound(p, SoundEvents.UI_BUTTON_CLICK.value(), 0.06f, 1.9f);
				v.cursorChunk = cc;
				v.denied = false;
				if (cc != null) placeCursor(v, cc);
				else sync(v, v.cursor, new HashSet<>(), false, 0);
			}
			if (v.age % 6 == 0) motes(v);
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

	/**
	 * The mouse looks around (up, down, straight down), WASD flies (ahead = where the screen looks), sneak goes up and
	 * sprint goes down, scroll flies closer or further along where you look. Never into the ground or through walls,
	 * and not further than the map reaches.
	 */
	private static void input(View v) {
		ServerPlayer p = v.p;
		var in = p.getLastClientInput();
		float yaw = p.getYRot(), pitch = p.getXRot();
		float turn = Mth.wrapDegrees(yaw - v.lastYaw), tilt = pitch - v.lastPitch;
		v.lastYaw = yaw;
		v.lastPitch = pitch;
		boolean free = v.age > OPEN_TICKS;
		if (free) {
			v.yaw += turn;
			v.pitch = Mth.clamp(v.pitch + tilt, MIN_PITCH, MAX_PITCH);
		}
		// Faster the higher you are
		double speed = Mth.clamp(v.targetDist / 22.0, 0.45, 3.2);
		double ahead = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
		double side = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
		// (sneak only counts once it was let go after opening: the land map key is sneak + swap hands)
		if (!p.isShiftKeyDown()) v.sneakReleased = true;
		double up = (p.isShiftKeyDown() && v.sneakReleased ? 1 : 0) - (in.sprint() ? 1 : 0);
		if (ahead != 0 && side != 0) {
			ahead *= 0.7071;
			side *= 0.7071;
		}
		// Right of the screen: the forward direction turned a quarter
		double rx = -v.fz(), rz = v.fx();
		double wantX = (v.fx() * ahead + rx * side) * speed;
		double wantZ = (v.fz() * ahead + rz * side) * speed;
		double wantY = up * speed * 0.8;
		// Snappy start, soft stop
		v.velX = Mth.lerp(ahead != 0 || side != 0 ? 0.5 : 0.3, v.velX, wantX);
		v.velZ = Mth.lerp(ahead != 0 || side != 0 ? 0.5 : 0.3, v.velZ, wantZ);
		v.velY = Mth.lerp(up != 0 ? 0.5 : 0.3, v.velY, wantY);

		// Scroll = fly closer or further along where you look (the hotbar slot changes)
		Vec3 dolly = Vec3.ZERO;
		int slot = p.getInventory().getSelectedSlot();
		if (slot != v.lastSlot) {
			int d = slot - v.lastSlot;
			if (d > 4) d -= 9;
			if (d < -4) d += 9;
			v.lastSlot = slot;
			emptyHands(p);
			if (free) dolly = look(v).scale(-d * Math.max(2.5, v.targetDist * 0.18));
		}
		if (free) {
			v.camPos = fly(v, v.camPos, new Vec3(v.velX, v.velY, v.velZ).add(dolly));
			for (TextDisplayElement l : v.labels) l.setScale(new Vector3f(labelScale(v)));
			v.you.setScale(new Vector3f(youScale(v)));
		}
		// Click = claim (an arm swing reaches the server)
		boolean swinging = p.isSwinging();
		if (swinging && !v.wasSwinging && free) clickClaim(v, true);
		v.wasSwinging = swinging;
		// Jump = name the piece of your land you look at
		boolean jump = in.jump();
		if (jump && !v.wasJumping && free && v.cursorChunk != null) {
			ClaimsMod.Claim cl = ClaimsMod.claimAt(v.level, v.cursorChunk);
			if (cl != null && cl.owner.equals(p.getUUID())) ClaimsMod.nameWindow(p, v.level, v.cursorChunk);
			else Ui.sound(p, SoundEvents.VILLAGER_NO, 0.4f, 1.2f);
		}
		v.wasJumping = jump;
	}

	/** Which way the camera looks. */
	private static Vec3 look(View v) {
		float y = v.yaw * Mth.DEG_TO_RAD, x = v.pitch * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(y) * Mth.cos(x), -Mth.sin(x), Mth.cos(y) * Mth.cos(x));
	}

	/**
	 * The camera moved by {@code by}, a step at a time, each way on its own, stopping short of anything solid (so it
	 * slides along a wall), never below the ground, and within the map.
	 */
	private static Vec3 fly(View v, Vec3 from, Vec3 by) {
		double len = by.length();
		if (len < 1.0e-4) return from;
		int steps = Math.max(1, (int) Math.ceil(len / 0.4));
		Vec3 step = by.scale(1.0 / steps);
		Vec3 at = from;
		double max = RADIUS * 16 + 8;
		for (int i = 0; i < steps; i++) {
			double x = Mth.clamp(at.x + step.x, v.origin.x - max, v.origin.x + max);
			if (!solid(v, x, at.y, at.z)) at = new Vec3(x, at.y, at.z);
			double y = Mth.clamp(at.y + step.y, v.level.getMinY() + 1, v.level.getMaxY() + 48);
			if (!solid(v, at.x, y, at.z)) at = new Vec3(at.x, y, at.z);
			double z = Mth.clamp(at.z + step.z, v.origin.z - max, v.origin.z + max);
			if (!solid(v, at.x, at.y, z)) at = new Vec3(at.x, at.y, z);
		}
		return at;
	}

	/** Is there something solid within a little of this spot (the camera keeps a bit of room around it)? */
	private static boolean solid(View v, double x, double y, double z) {
		for (double dx : new double[] {-0.3, 0.3}) {
			for (double dy : new double[] {-0.3, 0.3}) {
				for (double dz : new double[] {-0.3, 0.3}) {
					BlockPos b = BlockPos.containing(x + dx, y + dy, z + dz);
					if (!v.level.getBlockState(b).getCollisionShape(v.level, b).isEmpty()) return true;
				}
			}
		}
		return false;
	}

	/** The chunk under the crosshair: where the line of sight first meets the ground (or water), within the map. */
	private static @Nullable ChunkPos aimed(View v) {
		Vec3 to = v.camPos.add(look(v).scale(REACH));
		BlockHitResult hit = v.level.clip(new ClipContext(v.camPos, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, v.p));
		if (hit.getType() == HitResult.Type.MISS) return null;
		ChunkPos c = new ChunkPos(Mth.floor(hit.getLocation().x) >> 4, Mth.floor(hit.getLocation().z) >> 4);
		ChunkPos home = new ChunkPos(Mth.floor(v.origin.x) >> 4, Mth.floor(v.origin.z) >> 4);
		return Math.abs(c.x() - home.x()) <= RADIUS && Math.abs(c.z() - home.z()) <= RADIUS ? c : null;
	}

	private static void clickClaim(View v, boolean claim) {
		ChunkPos c = v.cursorChunk;
		if (c == null) return;
		boolean changed = claim ? ClaimsMod.claim(v.p, v.level, c) : ClaimsMod.unclaim(v.p, v.level, c);
		if (!changed) {
			// The golden box flashes red for a moment
			Ui.sound(v.p, SoundEvents.VILLAGER_NO, 0.5f, 1.2f);
			v.denied = true;
			v.deniedUntil = v.age + 12;
			placeCursor(v, c);
			return;
		}
		buildings(v, false);
		placeCursor(v, c);
		double y = highest(v, c) + 1.2;
		Region r = regionOf(v, c);
		if (claim) {
			double top = r != null ? r.roof() : y;
			edgeParticles(v, c, top, ParticleTypes.HAPPY_VILLAGER);
			v.level.sendParticles(v.p, ParticleTypes.TOTEM_OF_UNDYING, true, true, c.getMinBlockX() + 8, top, c.getMinBlockZ() + 8,
					70, 3, 0.8, 3, 0.45);
			Ui.sound(v.p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.2f);
			Ui.sound(v.p, SoundEvents.PLAYER_LEVELUP, 0.35f, 1.6f);
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

	// ---------------------------------------------------------------- the land

	private static void moveCamera(View v, Vec3 pos, float yaw, float pitch) {
		v.cam.setOffset(pos.subtract(v.origin));
		v.cam.setYaw(yaw);
		v.cam.setPitch(pitch);
	}

	private static Kind kind(View v, @Nullable ClaimsMod.Claim cl) {
		if (cl == null) return Kind.FREE;
		if (cl.owner.equals(v.p.getUUID())) return Kind.MINE;
		return ClaimsMod.trustedIn(v.p, cl) ? Kind.TRUSTED : Kind.OTHER;
	}

	private static int ground(View v, int x, int z) {
		return v.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
	}

	/** Highest ground in a chunk (every third block), so a roof floats over all of it. */
	private static int highest(View v, ChunkPos c) {
		return v.highest.computeIfAbsent(key(c), k -> {
			int h = v.level.getMinY();
			for (int x = 0; x < 16; x += 3) {
				for (int z = 0; z < 16; z += 3) h = Math.max(h, ground(v, c.getMinBlockX() + x, c.getMinBlockZ() + z));
			}
			for (int i = 0; i < 16; i += 5) {
				h = Math.max(h, ground(v, c.getMinBlockX() + 15, c.getMinBlockZ() + i));
				h = Math.max(h, ground(v, c.getMinBlockX() + i, c.getMinBlockZ() + 15));
			}
			return h;
		});
	}

	/** Lowest ground along one side of a chunk (where a wall must reach down to). */
	private static int lowestAlong(View v, ChunkPos c, int side) {
		int x0 = c.getMinBlockX(), z0 = c.getMinBlockZ();
		int low = Integer.MAX_VALUE;
		for (int i = 0; i < 16; i++) {
			int x = switch (side) {
				case 1 -> x0 + 15;
				case 3 -> x0;
				default -> x0 + i;
			};
			int z = switch (side) {
				case 0 -> z0;
				case 2 -> z0 + 15;
				default -> z0 + i;
			};
			low = Math.min(low, ground(v, x, z));
		}
		return low;
	}

	private static ChunkPos neighbour(ChunkPos c, int side) {
		return switch (side) {
			case 0 -> new ChunkPos(c.x(), c.z() - 1); // north
			case 1 -> new ChunkPos(c.x() + 1, c.z()); // east
			case 2 -> new ChunkPos(c.x(), c.z() + 1); // south
			default -> new ChunkPos(c.x() - 1, c.z()); // west
		};
	}

	/** Every connected piece of land of one owner around you. */
	private static List<Region> regions(View v) {
		ChunkPos center = new ChunkPos(Mth.floor(v.origin.x) >> 4, Mth.floor(v.origin.z) >> 4);
		List<Region> out = new ArrayList<>();
		Set<Long> done = new HashSet<>();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				ChunkPos start = new ChunkPos(center.x() + dx, center.z() + dz);
				ClaimsMod.Claim first = ClaimsMod.claimAt(v.level, start);
				if (first == null || !done.add(key(start))) continue;
				List<ChunkPos> group = new ArrayList<>();
				Set<Long> keys = new HashSet<>();
				ArrayDeque<ChunkPos> todo = new ArrayDeque<>();
				todo.add(start);
				while (!todo.isEmpty()) {
					ChunkPos c = todo.poll();
					group.add(c);
					keys.add(key(c));
					for (int side = 0; side < 4; side++) {
						ChunkPos n = neighbour(c, side);
						if (Math.abs(n.x() - center.x()) > RADIUS || Math.abs(n.z() - center.z()) > RADIUS) continue;
						ClaimsMod.Claim nc = ClaimsMod.claimAt(v.level, n);
						if (nc != null && nc.owner.equals(first.owner) && done.add(key(n))) todo.add(n);
					}
				}
				int roof = Integer.MIN_VALUE, base = Integer.MAX_VALUE;
				for (ChunkPos c : group) {
					roof = Math.max(roof, highest(v, c));
					for (int side = 0; side < 4; side++) base = Math.min(base, lowestAlong(v, c, side));
				}
				out.add(new Region(first, kind(v, first), keys, group, roof + ROOF, base - 2));
			}
		}
		return out;
	}

	/** The name of a piece of land ("" if it has none). */
	private static String name(View v, Region r) {
		for (ChunkPos c : r.list()) {
			ClaimsMod.Claim cl = ClaimsMod.claimAt(v.level, c);
			if (cl != null && cl.area != null && !cl.area.isEmpty()) return cl.area;
		}
		return "";
	}

	private static @Nullable Region regionOf(View v, ChunkPos c) {
		for (Region r : v.regions) {
			if (r.chunks().contains(key(c))) return r;
		}
		return null;
	}

	/**
	 * Everyone's land as glass buildings: a wall along every outer edge (from under the ground up to the roof), a flat
	 * roof over every chunk, and bright rods along the roof's edges and down every corner. Kept pieces stay, new ones
	 * grow out of the ground, gone ones sink back in.
	 */
	private static void buildings(View v, boolean opening) {
		v.regions.clear();
		v.regions.addAll(regions(v));
		Set<Piece> want = new HashSet<>();
		// colours off: the buildings sink into the ground, the names stay
		if (ClaimsMod.COLOURS.get(v.p)) for (Region r : v.regions) building(v, r, want);
		sync(v, v.pieces, want, opening, 9);
		labels(v);
	}

	private static void building(View v, Region r, Set<Piece> out) {
		Kind kind = r.kind();
		float roof = r.roof(), base = r.base();
		for (ChunkPos c : r.list()) {
			int x0 = c.getMinBlockX(), z0 = c.getMinBlockZ();
			if (v.pack) out.add(new Piece(ROOF_PIECE, kind, AXIS_Y, x0 + 8, z0 + 8, roof, roof, 16, 16, base));
			for (int side = 0; side < 4; side++) {
				if (r.chunks().contains(key(neighbour(c, side)))) continue;
				float low = lowestAlong(v, c, side) - 1;
				boolean alongX = side == 0 || side == 2;
				float line = switch (side) {
					case 0 -> z0;
					case 1 -> x0 + 16;
					case 2 -> z0 + 16;
					default -> x0;
				};
				// Walls a hair inside the border, so two neighbours' walls don't fight
				float inside = side == 0 || side == 3 ? 0.03f : -0.03f;
				float mid = (alongX ? x0 : z0) + 8;
				if (alongX) {
					out.add(new Piece(WALL, kind, AXIS_X, mid, line + inside, low, roof, 16, 0, low));
					out.add(new Piece(ROD_PIECE, kind, AXIS_X, mid, line, roof, roof, 16, ROD, base));
				} else {
					out.add(new Piece(WALL, kind, AXIS_Z, line + inside, mid, low, roof, 16, 0, low));
					out.add(new Piece(ROD_PIECE, kind, AXIS_Z, line, mid, roof, roof, 16, ROD, base));
				}
				// A rod down each end where the outline turns (not where it runs on straight into the next chunk)
				for (int end = 0; end < 2; end++) {
					ChunkPos along = alongX ? new ChunkPos(c.x() + (end == 0 ? -1 : 1), c.z()) : new ChunkPos(c.x(), c.z() + (end == 0 ? -1 : 1));
					boolean straight = r.chunks().contains(key(along)) && !r.chunks().contains(key(neighbour(along, side)));
					if (straight) continue;
					float ex = alongX ? x0 + (end == 0 ? 0 : 16) : line;
					float ez = alongX ? line : z0 + (end == 0 ? 0 : 16);
					out.add(new Piece(ROD_PIECE, kind, AXIS_Y, ex, ez, Math.min(low, base + 2), roof, 0, ROD, Math.min(low, base + 2)));
				}
			}
		}
	}

	/**
	 * The golden box around the chunk in the middle of the screen: inside the building's walls on claimed land (and
	 * just over its roof), a lower box on free land. Red for a moment when a click can't be done.
	 */
	private static void placeCursor(View v, ChunkPos c) {
		Set<Piece> want = new HashSet<>();
		Region r = regionOf(v, c);
		float top = r != null ? r.roof() + 0.06f : highest(v, c) + CURSOR_ROOF;
		float low = Integer.MAX_VALUE;
		for (int side = 0; side < 4; side++) low = Math.min(low, lowestAlong(v, c, side) - 1);
		Kind kind = v.denied ? Kind.DENIED : null;
		int x0 = c.getMinBlockX(), z0 = c.getMinBlockZ();
		float in = 0.09f;
		if (v.pack) want.add(new Piece(ROOF_PIECE, kind, AXIS_Y, x0 + 8, z0 + 8, top, top, 16 - 2 * in, 16 - 2 * in, low));
		for (int side = 0; side < 4; side++) {
			boolean alongX = side == 0 || side == 2;
			float line = switch (side) {
				case 0 -> z0 + in;
				case 1 -> x0 + 16 - in;
				case 2 -> z0 + 16 - in;
				default -> x0 + in;
			};
			if (alongX) {
				want.add(new Piece(WALL, kind, AXIS_X, x0 + 8, line, low, top, 16 - 2 * in, 0, low));
				want.add(new Piece(ROD_PIECE, kind, AXIS_X, x0 + 8, line, top, top, 16 - 2 * in, CURSOR_ROD, low));
			} else {
				want.add(new Piece(WALL, kind, AXIS_Z, line, z0 + 8, low, top, 16 - 2 * in, 0, low));
				want.add(new Piece(ROD_PIECE, kind, AXIS_Z, line, z0 + 8, top, top, 16 - 2 * in, CURSOR_ROD, low));
			}
		}
		for (float[] corner : new float[][] {{x0 + in, z0 + in}, {x0 + 16 - in, z0 + in}, {x0 + in, z0 + 16 - in}, {x0 + 16 - in, z0 + 16 - in}}) {
			want.add(new Piece(ROD_PIECE, kind, AXIS_Y, corner[0], corner[1], low, top, 0, CURSOR_ROD, low));
		}
		sync(v, v.cursor, want, false, 4);
	}

	/** Make the shown pieces match {@code want}: new ones grow out of the ground, gone ones sink and disappear. */
	private static void sync(View v, Map<Piece, DisplayElement> shown, Set<Piece> want, boolean wave, int grow) {
		for (Iterator<Map.Entry<Piece, DisplayElement>> it = shown.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Piece, DisplayElement> e = it.next();
			if (want.contains(e.getKey())) continue;
			lower(v, e.getKey(), e.getValue(), v.age + 1, Math.max(3, grow - 3));
			v.anims.add(new Anim(e.getValue(), v.age + grow, 0, true, null));
			it.remove();
		}
		for (Piece s : want) {
			if (shown.containsKey(s)) continue;
			DisplayElement el = element(v, s);
			if (el == null) continue;
			v.holder.addElement(el);
			shown.put(s, el);
			int delay = 1;
			if (wave) delay += 4 + (int) (Math.hypot(s.x() - v.origin.x, s.z() - v.origin.z) / 6);
			raise(v, s, el, v.age + delay, grow);
		}
	}

	/** A piece of a building, still sunk into the ground (it grows with {@link #raise}); null = not drawn without the pack. */
	private static @Nullable DisplayElement element(View v, Piece s) {
		DisplayElement el;
		if (v.pack) {
			String name = s.kind() == null ? "cursor" : s.kind().model();
			String type = switch (s.type()) {
				case WALL -> "wall_";
				case ROOF_PIECE -> "roof_";
				default -> "rod_";
			};
			ItemDisplayElement item = new ItemDisplayElement();
			ItemStack stack = new ItemStack(Items.PAPER);
			stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("libreria", "claim/" + type + name));
			item.setItem(stack);
			item.setItemDisplayContext(ItemDisplayContext.NONE);
			el = item;
		} else {
			if (s.type() == ROOF_PIECE) return null;
			String color = s.kind() == null ? "yellow" : s.type() == ROD_PIECE ? "white" : s.kind().glass();
			el = new BlockDisplayElement(glass(color).defaultBlockState());
		}
		el.setBrightness(FULL);
		el.setViewRange(8f);
		el.setOffset(new Vec3(s.x(), s.base(), s.z()).subtract(v.origin));
		shape(v, s, el, false);
		return el;
	}

	/**
	 * Translation, rotation and size of a piece: {@code up} = standing where it belongs, otherwise flat on the
	 * ground at its base (walls and standing rods with no height, roofs and lying rods sunk to the base).
	 */
	private static void shape(View v, Piece s, DisplayElement el, boolean up) {
		float h = s.high() - s.low();
		boolean standing = s.type() == WALL || s.type() == ROD_PIECE && s.axis() == AXIS_Y;
		float y = standing ? (up ? (s.low() + s.high()) / 2 - s.base() : 0) : (up ? s.high() - s.base() : 0);
		float height = standing ? (up ? Math.max(h, 0.01f) : 0.01f) : 1;
		if (!v.pack) {
			// A glass block display: its corner is at the origin
			float t = s.type() == ROD_PIECE ? s.depth() : 0.06f;
			float sy = standing ? height : t;
			Vector3f size = switch (s.axis()) {
				case AXIS_X -> new Vector3f(s.len(), sy, t);
				case AXIS_Z -> new Vector3f(t, sy, s.len());
				default -> new Vector3f(t, sy, t);
			};
			float bottom = standing ? (up ? s.low() - s.base() : 0) : (up ? s.high() - s.base() : 0) - t / 2;
			el.setScale(size);
			el.setTranslation(new Vector3f(-size.x / 2, bottom, -size.z / 2));
			return;
		}
		Quaternionf rot = new Quaternionf();
		Vector3f scale;
		switch (s.type()) {
			case WALL -> {
				if (s.axis() == AXIS_Z) rot.rotateY((float) Math.PI / 2);
				scale = new Vector3f(s.len(), height, 1);
			}
			case ROOF_PIECE -> scale = new Vector3f(s.len(), 1, s.depth());
			default -> {
				if (s.axis() == AXIS_X) rot.rotateZ((float) Math.PI / 2);
				if (s.axis() == AXIS_Z) rot.rotateX((float) Math.PI / 2);
				scale = new Vector3f(s.depth(), s.axis() == AXIS_Y ? height : s.len(), s.depth());
			}
		}
		el.setLeftRotation(rot);
		el.setScale(scale);
		el.setTranslation(new Vector3f(0, y, 0));
	}

	private static void raise(View v, Piece s, DisplayElement el, int at, int duration) {
		v.anims.add(new Anim(el, at, duration, false, () -> shape(v, s, el, true)));
	}

	private static void lower(View v, Piece s, DisplayElement el, int at, int duration) {
		v.anims.add(new Anim(el, at, duration, false, () -> shape(v, s, el, false)));
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
			a.el().startInterpolation();
		}
	}

	/** One floating name over every building, with its size. */
	private static void labels(View v) {
		for (TextDisplayElement l : v.labels) v.holder.removeElement(l);
		v.labels.clear();
		for (Region r : v.regions) {
			double ax = 0, az = 0;
			for (ChunkPos c : r.list()) {
				ax += c.x();
				az += c.z();
			}
			ax /= r.list().size();
			az /= r.list().size();
			ChunkPos mid = r.list().getFirst();
			double best = Double.MAX_VALUE;
			for (ChunkPos c : r.list()) {
				double d = (c.x() - ax) * (c.x() - ax) + (c.z() - az) * (c.z() - az);
				if (d < best) {
					best = d;
					mid = c;
				}
			}
			Kind kind = r.kind();
			int n = r.list().size();
			String size = n == 1 ? "1 chunk" : n + " chunks";
			String area = name(v, r);
			String whose = kind == Kind.MINE ? "Your land" : r.first().ownerName;
			MutableComponent text = Component.literal(area.isEmpty() ? whose : area).withStyle(s -> s.withColor(kind.color()).withBold(true));
			text.append(Component.literal("\n" + (area.isEmpty() ? "" : (kind == Kind.MINE ? "yours" : r.first().ownerName) + " · ") + size
					+ (kind == Kind.TRUSTED ? " · you may build" : "")).withStyle(ChatFormatting.GRAY));
			TextDisplayElement label = new TextDisplayElement(text);
			label.setBillboardMode(Display.BillboardConstraints.CENTER);
			label.setBrightness(FULL);
			label.setViewRange(8f);
			label.setShadow(true);
			label.setBackground(0x70100A06);
			label.setScale(new Vector3f(labelScale(v)));
			label.setOffset(new Vec3(mid.getMinBlockX() + 8, r.roof() + 1.4, mid.getMinBlockZ() + 8).subtract(v.origin));
			v.holder.addElement(label);
			v.labels.add(label);
		}
		// Waypoints, your death point, graves… and friends who want to be seen, as small flags in the view
		double reach = (RADIUS + 1) * 16;
		List<nl.libreria.core.gui.Markers.Marker> markers = new ArrayList<>(nl.libreria.core.gui.Markers.forPlayer(v.p));
		markers.addAll(ClaimsMod.friends(v.p));
		for (nl.libreria.core.gui.Markers.Marker m : markers) {
			if (Math.abs(m.pos().x - v.origin.x) > reach || Math.abs(m.pos().z - v.origin.z) > reach) continue;
			MutableComponent text = Component.empty();
			if (v.pack) text.append(nl.libreria.core.gui.Glyphs.ui(nl.libreria.core.gui.Glyphs.g("mark." + m.glyph()) + " ", m.color()));
			text.append(Component.literal(m.name()).withStyle(s -> s.withColor(m.color())));
			TextDisplayElement label = new TextDisplayElement(text);
			label.setBillboardMode(Display.BillboardConstraints.CENTER);
			label.setBrightness(FULL);
			label.setViewRange(8f);
			label.setShadow(true);
			label.setBackground(0x50000000);
			label.setScale(new Vector3f(labelScale(v)));
			ChunkPos column = new ChunkPos(Mth.floor(m.pos().x) >> 4, Mth.floor(m.pos().z) >> 4);
			double y = Math.max(m.pos().y + 2.5, highest(v, column) + 2.0);
			label.setOffset(new Vec3(m.pos().x, y, m.pos().z).subtract(v.origin));
			v.holder.addElement(label);
			v.labels.add(label);
		}
	}

	/** Soft coloured motes rising inside the buildings near the middle of the screen. */
	private static void motes(View v) {
		if (v.cursorChunk == null) return;
		java.util.Random r = new java.util.Random(v.age * 31L + v.p.getId());
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				ChunkPos c = new ChunkPos(v.cursorChunk.x() + dx, v.cursorChunk.z() + dz);
				Region region = regionOf(v, c);
				if (region == null || r.nextInt(3) != 0) continue;
				double x = c.getMinBlockX() + r.nextDouble() * 16, z = c.getMinBlockZ() + r.nextDouble() * 16;
				double ground = ground(v, Mth.floor(x), Mth.floor(z));
				double y = ground + 0.4 + r.nextDouble() * Math.max(0.5, region.roof() - ground - 0.8);
				v.level.sendParticles(v.p, new DustParticleOptions(region.kind().color(), 1.4f), true, true, x, y, z, 1, 0, 0.05, 0, 0);
			}
		}
	}

	/**
	 * What's in the middle of the screen. With the pack: a card at the top (whose land, how much you may still claim,
	 * what a click does) and the keys in the action bar. Without it: one line in the action bar.
	 */
	private static void status(View v) {
		ChunkPos c = v.cursorChunk;
		if (v.pack) {
			MutableComponent keys = Component.literal("Mouse").withStyle(ChatFormatting.WHITE)
					.append(Component.literal(" look  ·  ").withStyle(ChatFormatting.GRAY))
					.append(nl.libreria.core.Keys.text("{forward}{left}{back}{right}").withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" fly  ·  ").withStyle(ChatFormatting.GRAY))
					.append(nl.libreria.core.Keys.key("sneak").withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" up  ·  ").withStyle(ChatFormatting.GRAY))
					.append(nl.libreria.core.Keys.key("sprint").withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" down  ·  ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal("Scroll").withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" closer  ·  ").withStyle(ChatFormatting.GRAY))
					.append(nl.libreria.core.Keys.key("drop").withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" back").withStyle(ChatFormatting.GRAY));
			if (ClaimsMod.MAP_KEY.get(v.p) == 0 && !ClaimsMod.COLOURS.isForced()) {
				keys.append(Component.literal("  ·  ").withStyle(ChatFormatting.GRAY))
						.append(nl.libreria.core.Keys.key("swap").withStyle(ChatFormatting.WHITE))
						.append(Component.literal(" colours").withStyle(ChatFormatting.GRAY));
			}
			Hud.show(v.p, HUD, keys, Hud.PRIO_INFO, 10);
		}
		if (c == null) {
			// Looking at the sky (or past the map): nothing to claim there
			if (v.pack) TopCard.hide(v.p, CARD);
			return;
		}
		ClaimsMod.Claim cl = ClaimsMod.claimAt(v.level, c);
		Kind kind = kind(v, cl);
		int used = ClaimsMod.owned(v.p.getUUID()), max = ClaimsMod.budget(v.p);
		boolean room = used < max;
		if (v.pack) {
			Region r = regionOf(v, c);
			int size = r == null ? 0 : r.list().size();
			String area = r == null ? "" : name(v, r);
			Component name = switch (kind) {
				case FREE -> Component.literal("Free land").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
				case MINE -> Component.literal(area.isEmpty() ? "Your land" : area).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
				default -> Component.literal(area.isEmpty() ? cl.ownerName + "'s land" : area).withStyle(s -> s.withColor(kind.color()).withBold(true));
			};
			String chunks = size == 1 ? "1 chunk" : size + " chunks";
			Component subtitle = Component.literal(switch (kind) {
				case FREE -> "Nobody's yet · chunk " + c.x() + ", " + c.z();
				case MINE -> (area.isEmpty() ? chunks + " together" : "Yours · " + chunks) + " · since " + ago(cl.since);
				case TRUSTED -> (area.isEmpty() ? "" : cl.ownerName + "'s · ") + "You may build here · " + chunks;
				default -> (area.isEmpty() ? "Protected · " : cl.ownerName + "'s · ") + chunks + " · since " + ago(cl.since);
			}).withStyle(ChatFormatting.GRAY);
			List<TopCard.Row> rows = new ArrayList<>();
			rows.add(TopCard.Row.bar("flag", "Your land", max == 0 ? 0 : used / (double) max, room ? 0x55FF55 : 0xFF5555, 10,
					Component.literal(used + " / " + max).withStyle(room ? ChatFormatting.WHITE : ChatFormatting.RED)));
			// What it takes to be allowed one more chunk, and how far along you are
			rows.add(TopCard.Row.bar("time", "Next chunk", ClaimsMod.nextChunkFraction(v.p), 0x55CCFF, 10,
					Component.literal(ClaimsMod.nextChunkShort(v.p)).withStyle(ChatFormatting.WHITE)));
			Component action = switch (kind) {
				case FREE -> room ? Component.literal("Click to claim it").withStyle(ChatFormatting.YELLOW)
						: Component.literal("Your land budget is full").withStyle(ChatFormatting.RED);
				case MINE -> Component.literal("Right-click: give it up  ·  Jump: " + (area.isEmpty() ? "name it" : "rename it"))
						.withStyle(ChatFormatting.YELLOW);
				case TRUSTED -> Component.literal("Ask " + cl.ownerName + " to change it").withStyle(ChatFormatting.GRAY);
				default -> Component.literal("Not yours to change").withStyle(ChatFormatting.GRAY);
			};
			rows.add(TopCard.Row.text("mouse", "", action));
			int accent = kind == Kind.FREE ? -1 : kind.color();
			TopCard.show(v.p, CARD, new TopCard.Card(new ItemStack(Items.FILLED_MAP), accent, name, subtitle, rows), 100);
			return;
		}
		MutableComponent line = Component.empty();
		switch (kind) {
			case FREE -> line.append(Component.literal("Free land").withStyle(ChatFormatting.WHITE))
					.append(Component.literal(room ? "  ·  click: claim" : "  ·  your budget is full")
							.withStyle(room ? ChatFormatting.YELLOW : ChatFormatting.RED));
			case MINE -> line.append(Component.literal(cl.area == null || cl.area.isEmpty() ? "Your land" : cl.area).withStyle(ChatFormatting.GREEN))
					.append(Component.literal("  ·  right-click: release  ·  jump: name").withStyle(ChatFormatting.YELLOW));
			case TRUSTED -> line.append(Component.literal(cl.ownerName + "'s land · you may build").withStyle(ChatFormatting.AQUA));
			default -> line.append(Component.literal(cl.ownerName + "'s land").withStyle(ChatFormatting.RED));
		}
		line.append(Component.literal("   " + used + "/" + max + " chunks").withStyle(ChatFormatting.GRAY));
		if (v.age < 120) line.append(Component.literal("   mouse look · WASD fly · sneak up · sprint down · Q back"
				+ (ClaimsMod.MAP_KEY.get(v.p) == 0 && !ClaimsMod.COLOURS.isForced() ? " · F colours" : "")).withStyle(ChatFormatting.DARK_GRAY));
		Hud.show(v.p, HUD, line, Hud.PRIO_INFO, 10);
	}

	private static String ago(long millis) {
		if (millis <= 0) return "long ago";
		long minutes = (System.currentTimeMillis() - millis) / 60_000;
		if (minutes < 60) return minutes <= 1 ? "just now" : minutes + " min ago";
		long hours = minutes / 60;
		if (hours < 48) return hours + " h ago";
		return hours / 24 + " days ago";
	}

	// ---------------------------------------------------------------- maths

	/** Names stay readable when you zoom out. */
	private static float labelScale(View v) {
		return (float) Math.max(1.5, v.targetDist / 9.0);
	}

	private static float youScale(View v) {
		return (float) Math.max(1.5, v.targetDist / 10.0);
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
