package nl.libreria.refresh;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.GenericEntityElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Hud;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Glyphs;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The refresh map, full screen. Your camera leaves your head and looks at a big screen of maps, alone in the dark (only
 * you see it; you stand still meanwhile): every chunk of a world from above, the ones that go darkened with red
 * stripes, claims framed green, what you kept framed yellow. Where you look is the pointer (the screen follows your
 * mouse, and pushing past its edge moves the map); WASD moves the map, scrolling zooms (from a chunk a pixel to a block
 * a pixel), holding right-click paints chunks to stay or go, left-click does a whole region (32 × 32 chunks). Tabs on
 * top switch between the worlds, sneak (or Done) takes you back.
 *
 * <p>The screen is 5 × 3 maps in invisible glow item frames, drawn by the server into map pictures that exist only
 * for you, with text displays for the words and dark panels around.
 */
final class MapScreen {
	private MapScreen() {
	}

	static final int TW = 5, TH = 3, W = TW * 128, H = TH * 128;
	/** From the camera to the screen (blocks), how far the view may turn from its middle, text size. */
	private static final double DIST = 2.14;
	private static final float MAX_YAW = 47f, MAX_PITCH = 33f, TEXT = 0.35f;
	private static final double PX = 0.025 * TEXT;
	private static final int[] ZOOMS = {1, 2, 4, 8, 16};
	private static final Identifier FREEZE = Identifier.fromNamespaceAndPath("libreria", "refresh_map");
	private static final Brightness FULL = new Brightness(15, 15);
	private static final String HUD = "refresh.map";
	private static final int BG = 0xD0101218, CHIP = 0xC02A2D36, CHIP_ON = 0xD0A06A10;
	private static int nextMap = 1_950_000_000;

	/** An item frame only one player sees: a map hanging on nothing, facing {@code facing}. */
	static final class Frame extends GenericEntityElement {
		private final Direction facing;

		Frame(Direction facing, ItemStack map) {
			this.facing = facing;
			this.setInvisible(true);
			this.syncedData.set(nl.libreria.refresh.mixin.ItemFrameAccess.libreria$dataItem(), map);
		}

		@Override
		protected EntityType<? extends Entity> getEntityType() {
			return EntityTypes.GLOW_ITEM_FRAME;
		}

		@Override
		protected Packet<ClientGamePacketListener> createSpawnPacket(ServerPlayer player) {
			Vec3 pos = this.getCurrentPos();
			return new ClientboundAddEntityPacket(this.getEntityId(), this.getUuid(), pos.x, pos.y, pos.z, 0, this.facing.toYRot(), this.getEntityType(),
					this.facing.get3DDataValue(), Vec3.ZERO, this.facing.toYRot());
		}
	}

	/** Something on the screen to click: its place on the screen (blocks from the middle: right, up). */
	private record Button(double u0, double u1, double v0, double v1, Runnable action) {
		boolean has(double u, double v) {
			return u >= this.u0 && u <= this.u1 && v >= this.v0 && v <= this.v1;
		}
	}

	private static final class View {
		final ServerPlayer p;
		final ServerLevel level;
		final Vec3 origin;
		final Direction face, right;
		final Vec3 middle, camPos;
		final ElementHolder holder = new ElementHolder();
		final ItemDisplayElement cam = new ItemDisplayElement(), seat = new ItemDisplayElement();
		final int[] mapIds = new int[TW * TH];
		final byte[][] sent = new byte[TW * TH][];
		final TextDisplayElement title = new TextDisplayElement(), legend = new TextDisplayElement(), sums = new TextDisplayElement(),
				done = new TextDisplayElement(), takes = new TextDisplayElement();
		final List<TextDisplayElement> tabs = new ArrayList<>();
		final List<Button> buttons = new ArrayList<>();
		ResourceKey<Level> dim;
		Plan.Ctx ctx;
		Plan.Count count;
		/** The middle of the screen, in chunks; pixels per chunk. */
		double cx, cz;
		int zoom = 2;
		float yaw, pitch, lastYaw, lastPitch;
		int age, lastSlot, lastPaint = -100, lastDraw = -100, lastCount = -100;
		boolean cameraSet, sneakReleased, dirty = true;
		/** Painting: chunks to stay (true) or go; the last chunk painted. */
		@Nullable Boolean paintStays;
		long lastPainted = Long.MIN_VALUE;
		long hover = Long.MIN_VALUE;
		double pu, pv;

		View(ServerPlayer p, ResourceKey<Level> dim) {
			this.p = p;
			this.level = p.level();
			this.origin = p.position();
			this.face = Direction.fromYRot(p.getYRot());
			this.right = this.face.getClockWise();
			this.dim = dim;
			// High above you, clear of the land: the screen and the camera before it
			int top = this.level.getMinY();
			BlockPos at = p.blockPosition();
			for (int dx = -7; dx <= 7; dx++) {
				for (int dz = -7; dz <= 7; dz++) top = Math.max(top, this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX() + dx, at.getZ() + dz));
			}
			int y = Math.min(top + 14, this.level.getMaxY() - 8);
			BlockPos base = new BlockPos(at.getX(), y, at.getZ()).relative(this.face, 3);
			this.middle = Vec3.atCenterOf(base).add(step(this.face).scale(0.46875));
			this.camPos = this.middle.subtract(step(this.face).scale(DIST));
		}

		Vec3 screen(double u, double v, double out) {
			return this.middle.add(step(this.right).scale(u)).add(0, v, 0).subtract(step(this.face).scale(out));
		}
	}

	private static Vec3 step(Direction d) {
		return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
	}

	private static final Map<UUID, View> VIEWS = new HashMap<>();

	static boolean isOpen(ServerPlayer p) {
		return VIEWS.containsKey(p.getUUID());
	}

	// ---------------------------------------------------------------- open, close

	static void open(ServerPlayer p, ResourceKey<Level> dim) {
		if (isOpen(p) || !Ui.isOp(p)) return;
		if (p.isPassenger() || p.isSleeping() || p.isSpectator() || p.isFallFlying()) {
			nl.libreria.core.Notify.warn(p, Items.FILLED_MAP, "Land first", "Stand on the ground, then open the map.");
			return;
		}
		if (Scan.world(dim) == null) return;
		p.closeContainer();
		View v = new View(p, dim);
		VIEWS.put(p.getUUID(), v);
		Services.setCamera(p.getUUID(), true);
		v.lastSlot = p.getInventory().getSelectedSlot();
		v.sneakReleased = !p.isShiftKeyDown();
		v.lastYaw = p.getYRot();
		v.lastPitch = p.getXRot();
		center(v, p);
		Vec3 o = v.origin;

		v.cam.setItem(ItemStack.EMPTY);
		v.cam.setTeleportDuration(2);
		v.cam.setOffset(v.camPos.subtract(o));
		v.cam.setYaw(v.face.toYRot());
		v.cam.setPitch(0);
		v.holder.addElement(v.cam);
		v.seat.setItem(ItemStack.EMPTY);
		v.holder.addElement(v.seat);

		// Dark all around: behind the screen and on every other side of the camera
		Vec3 f = step(v.face), r = step(v.right);
		darkPanel(v, v.middle.add(f.scale(0.05)), r, 30, 30);
		darkPanel(v, v.camPos.subtract(f.scale(6)), r, 30, 30);
		darkPanel(v, v.camPos.add(r.scale(7)), f, 30, 30);
		darkPanel(v, v.camPos.subtract(r.scale(7)), f, 30, 30);
		darkFloor(v, v.camPos.add(0, 6, 0));
		darkFloor(v, v.camPos.subtract(0, 6, 0));

		// The maps
		for (int i = 0; i < TW; i++) {
			for (int j = 0; j < TH; j++) {
				int t = i + (TH - 1 - j) * TW;
				v.mapIds[t] = nextMap++;
				ItemStack map = new ItemStack(Items.FILLED_MAP);
				map.set(DataComponents.MAP_ID, new MapId(v.mapIds[t]));
				Frame fr = new Frame(v.face.getOpposite(), map);
				Vec3 c = v.screen(i - (TW - 1) / 2.0, j - (TH - 1) / 2.0, 0).subtract(f.scale(0.46875));
				fr.setOffset(c.subtract(o));
				fr.setInteractionHandler(new VirtualElement.InteractionHandler() {
					@Override
					public void interact(ServerPlayer player, InteractionHand hand, Vec3 pos, boolean secondary) {
						if (hand == InteractionHand.MAIN_HAND) rightClick(player);
					}

					@Override
					public void attack(ServerPlayer player) {
						leftClick(player);
					}
				});
				v.holder.addElement(fr);
			}
		}
		for (TextDisplayElement t : List.of(v.title, v.legend, v.sums, v.done, v.takes)) text(v, t);
		for (int k = 0; k < 3; k++) {
			TextDisplayElement t = new TextDisplayElement();
			text(v, t);
			v.tabs.add(t);
		}
		new ManualAttachment(v.holder, v.level, () -> v.origin);
		v.holder.startWatching(p);
		p.connection.send(VirtualEntityUtils.createClientboundSetPassengersPacket(v.seat.getEntityId(), new int[] {p.getId()}));
		freeze(p, true);
		emptyHands(p);
		ServerLevel shown = p.level().getServer().getLevel(dim);
		if (shown != null) Scan.refreshLoaded(shown);
		Ui.sound(p, SoundEvents.BOOK_PAGE_TURN, 0.8f, 0.8f);
		rebuild(v);
	}

	private static final Identifier EMPTY_MODEL = Identifier.fromNamespaceAndPath("libreria", "gui/empty");

	/** Only on your own screen: nothing in your hands (an invisible item: an empty hand still shows your arm). */
	private static void emptyHands(ServerPlayer p) {
		var menu = p.inventoryMenu;
		ItemStack hand = new ItemStack(Items.PAPER);
		hand.set(DataComponents.ITEM_MODEL, EMPTY_MODEL);
		hand.set(DataComponents.CUSTOM_NAME, Component.empty());
		for (int slot = 36; slot <= 44; slot++) {
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), slot, hand.copy()));
		}
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), 45, ItemStack.EMPTY));
	}

	private static void darkPanel(View v, Vec3 at, Vec3 along, float w, float h) {
		BlockDisplayElement b = new BlockDisplayElement(Ui.block("black_concrete"));
		boolean x = Math.abs(along.x) > 0.5;
		// a thin wall standing across {@code along}'s other axis
		Vector3f scale = x ? new Vector3f(w, h, 0.02f) : new Vector3f(0.02f, h, w);
		b.setScale(scale);
		b.setTranslation(new Vector3f(-scale.x / 2, -scale.y / 2, -scale.z / 2));
		b.setBrightness(new Brightness(0, 0));
		b.setViewRange(4f);
		b.setOffset(at.subtract(v.origin));
		v.holder.addElement(b);
	}

	private static void darkFloor(View v, Vec3 at) {
		BlockDisplayElement b = new BlockDisplayElement(Ui.block("black_concrete"));
		Vector3f scale = new Vector3f(30, 0.02f, 30);
		b.setScale(scale);
		b.setTranslation(new Vector3f(-15, -0.01f, -15));
		b.setBrightness(new Brightness(0, 0));
		b.setViewRange(4f);
		b.setOffset(at.subtract(v.origin));
		v.holder.addElement(b);
	}

	private static void text(View v, TextDisplayElement t) {
		t.setBillboardMode(Display.BillboardConstraints.FIXED);
		t.setYaw(v.face.getOpposite().toYRot());
		t.setBrightness(FULL);
		t.setScale(new Vector3f(TEXT));
		t.setLineWidth(600);
		t.setShadow(false);
		t.setBackground(BG);
		t.setTextAlignment(Display.TextDisplay.Align.LEFT);
		t.setViewRange(4f);
		v.holder.addElement(t);
	}

	/** The map starts with you in the middle (or the world's middle, for another world). */
	private static void center(View v, ServerPlayer p) {
		if (p.level().dimension().equals(v.dim)) {
			v.cx = p.getX() / 16.0;
			v.cz = p.getZ() / 16.0;
		} else {
			v.cx = 0;
			v.cz = 0;
		}
	}

	static void close(ServerPlayer p) {
		View v = VIEWS.get(p.getUUID());
		if (v != null) finish(v, true);
	}

	private static void finish(View v, boolean back) {
		VIEWS.remove(v.p.getUUID());
		Services.setCamera(v.p.getUUID(), false);
		v.holder.destroy();
		freeze(v.p, false);
		if (!v.p.hasDisconnected()) {
			v.p.connection.send(VirtualEntityUtils.createClientboundSetCameraPacket(v.p.getId()));
			Hud.hide(v.p, HUD);
			// Your real hotbar again
			v.p.inventoryMenu.sendAllDataToRemote();
			if (back) RefreshCanvas.open(v.p);
		}
	}

	/** For the screenshots: closed, without going back to the settings. */
	static void closeQuietly(ServerPlayer p) {
		View v = VIEWS.get(p.getUUID());
		if (v != null) finish(v, false);
	}

	/** For the screenshots: zoom {@code index} (0: a chunk a pixel … 4: a block a pixel), around the middle. */
	static void debugZoom(ServerPlayer p, int index) {
		View v = VIEWS.get(p.getUUID());
		if (v == null) return;
		v.zoom = Mth.clamp(index, 0, ZOOMS.length - 1);
		v.dirty = true;
	}

	static void debugWorld(ServerPlayer p, ResourceKey<Level> dim) {
		View v = VIEWS.get(p.getUUID());
		if (v != null) switchTo(v, dim);
	}

	static void closeAll() {
		for (View v : new ArrayList<>(VIEWS.values())) finish(v, false);
	}

	private static void freeze(ServerPlayer p, boolean on) {
		for (var attr : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
			AttributeInstance inst = p.getAttribute(attr);
			if (inst == null) continue;
			inst.removeModifier(FREEZE);
			if (on) inst.addTransientModifier(new AttributeModifier(FREEZE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		// The screen is a little further than you reach: reach it from the camera
		AttributeInstance reach = p.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
		if (reach != null) {
			reach.removeModifier(FREEZE);
			if (on) reach.addTransientModifier(new AttributeModifier(FREEZE, 8.0, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	// ---------------------------------------------------------------- every tick

	static void tick(MinecraftServer server) {
		for (View v : new ArrayList<>(VIEWS.values())) {
			ServerPlayer p = v.p;
			if (p.hasDisconnected() || p.isRemoved() || p.level() != v.level || p.isDeadOrDying()) {
				finish(v, false);
				continue;
			}
			v.age++;
			if (!v.cameraSet && v.age >= 2) {
				p.connection.send(VirtualEntityUtils.createClientboundSetCameraPacket(v.cam.getEntityId()));
				v.cameraSet = true;
			}
			if (p.position().distanceToSqr(v.origin) > 0.36) {
				p.teleportTo(v.level, v.origin.x, v.origin.y, v.origin.z, Set.of(), p.getYRot(), p.getXRot(), false);
			}
			input(v);
			if (v.sneakReleased && p.isShiftKeyDown()) {
				finish(v, true);
				continue;
			}
			pointer(v);
			if (v.age - v.lastCount > 40) {
				v.lastCount = v.age;
				recount(v);
			}
			if (v.dirty && v.age - v.lastDraw >= 2) {
				v.lastDraw = v.age;
				v.dirty = false;
				draw(v);
			}
			status(v);
			v.holder.tick();
		}
	}

	/** The mouse turns the view (within the screen; past its edge the map moves), WASD moves the map, scroll zooms. */
	private static void input(View v) {
		ServerPlayer p = v.p;
		var in = p.getLastClientInput();
		float yaw = p.getYRot(), pitch = p.getXRot();
		float turn = Mth.wrapDegrees(yaw - v.lastYaw), tilt = pitch - v.lastPitch;
		v.lastYaw = yaw;
		v.lastPitch = pitch;
		if (!p.isShiftKeyDown()) v.sneakReleased = true;
		if (v.age > 2) {
			float ny = v.yaw + turn, np = v.pitch + tilt;
			// Pushing past the edge: the map moves that way
			double perDeg = DIST * Math.PI / 180 * 128 / zoom(v);
			if (Math.abs(ny) > MAX_YAW) {
				v.cx += (ny - Math.signum(ny) * MAX_YAW) * perDeg * 2.2;
				v.dirty = true;
			}
			if (Math.abs(np) > MAX_PITCH) {
				v.cz += (np - Math.signum(np) * MAX_PITCH) * perDeg * 2.2;
				v.dirty = true;
			}
			v.yaw = Mth.clamp(ny, -MAX_YAW, MAX_YAW);
			v.pitch = Mth.clamp(np, -MAX_PITCH, MAX_PITCH);
		}
		v.cam.setYaw(v.face.toYRot() + v.yaw);
		v.cam.setPitch(v.pitch);
		double speed = 6.0 / zoom(v);
		double dx = ((in.right() ? 1 : 0) - (in.left() ? 1 : 0)) * speed, dz = ((in.backward() ? 1 : 0) - (in.forward() ? 1 : 0)) * speed;
		if (dx != 0 || dz != 0) {
			v.cx += dx;
			v.cz += dz;
			v.dirty = true;
		}
		int slot = p.getInventory().getSelectedSlot();
		if (slot != v.lastSlot) {
			int d = slot - v.lastSlot;
			if (d > 4) d -= 9;
			if (d < -4) d += 9;
			v.lastSlot = slot;
			emptyHands(p);
			int z = Mth.clamp(v.zoom - Integer.signum(d), 0, ZOOMS.length - 1);
			if (z != v.zoom) {
				// Zoom around the pointer: the chunk under it stays under it
				double px = (v.pu + TW / 2.0) * 128, py = (TH / 2.0 - v.pv) * 128;
				double wx = v.cx + (px - W / 2.0) / zoom(v), wz = v.cz + (py - H / 2.0) / zoom(v);
				v.zoom = z;
				v.cx = wx - (px - W / 2.0) / zoom(v);
				v.cz = wz - (py - H / 2.0) / zoom(v);
				v.dirty = true;
				Ui.sound(p, SoundEvents.UI_BUTTON_CLICK.value(), 0.15f, 1.4f + 0.1f * z);
			}
		}
	}

	private static int zoom(View v) {
		return ZOOMS[v.zoom];
	}

	/** Where the view looks on the screen (blocks from its middle), and the chunk there. */
	private static void pointer(View v) {
		float yr = (v.face.toYRot() + v.yaw) * Mth.DEG_TO_RAD, xr = v.pitch * Mth.DEG_TO_RAD;
		Vec3 dir = new Vec3(-Mth.sin(yr) * Mth.cos(xr), -Mth.sin(xr), Mth.cos(yr) * Mth.cos(xr));
		Vec3 f = step(v.face), r = step(v.right);
		double along = dir.dot(f);
		if (along <= 0.01) return;
		Vec3 hit = v.camPos.add(dir.scale(DIST / along)).subtract(v.middle);
		v.pu = hit.dot(r);
		v.pv = hit.y;
		long h = chunkAt(v, v.pu, v.pv);
		if (h != v.hover) {
			v.hover = h;
			v.dirty = true;
		}
	}

	private static long chunkAt(View v, double u, double vv) {
		double px = (u + TW / 2.0) * 128, py = (TH / 2.0 - vv) * 128;
		if (px < 0 || py < 0 || px >= W || py >= H) return Long.MIN_VALUE;
		return Scan.key(Mth.floor(v.cx + (px - W / 2.0) / zoom(v)), Mth.floor(v.cz + (py - H / 2.0) / zoom(v)));
	}

	// ---------------------------------------------------------------- clicks

	private static void rightClick(ServerPlayer p) {
		View v = VIEWS.get(p.getUUID());
		if (v == null || v.age < 4) return;
		if (button(v)) return;
		long c = v.hover;
		if (c == Long.MIN_VALUE) return;
		Scan.World w = Scan.world(v.dim);
		if (w == null) return;
		// A new stroke: it paints the opposite of the first chunk
		boolean fresh = v.age - v.lastPaint > 7;
		v.lastPaint = v.age;
		if (fresh) {
			Plan.Why why = v.ctx.why(w, Scan.kx(c), Scan.kz(c));
			if (!v.ctx.pickable(why)) {
				Hud.flash(p, Ui.text(Character.toUpperCase(why.words.charAt(0)) + why.words.substring(1) + ": it always stays", ChatFormatting.GRAY));
				v.paintStays = null;
				return;
			}
			v.paintStays = !why.stays;
			v.lastPainted = Long.MIN_VALUE;
		}
		if (v.paintStays == null) return;
		// Along the way from the last chunk painted (the pointer moves between clicks)
		int x1 = Scan.kx(c), z1 = Scan.kz(c);
		int x0 = v.lastPainted == Long.MIN_VALUE ? x1 : Scan.kx(v.lastPainted), z0 = v.lastPainted == Long.MIN_VALUE ? z1 : Scan.kz(v.lastPainted);
		int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
		boolean any = false;
		for (int s = 0; s <= n; s++) {
			int x = n == 0 ? x1 : Math.round(x0 + (x1 - x0) * s / (float) n), z = n == 0 ? z1 : Math.round(z0 + (z1 - z0) * s / (float) n);
			any |= paint(v, w, x, z, v.paintStays);
		}
		v.lastPainted = c;
		if (any) {
			changed(v);
			Ui.sound(p, SoundEvents.UI_BUTTON_CLICK.value(), 0.08f, v.paintStays ? 1.6f : 1.2f);
		}
	}

	/** Left-click: a button, or the whole region under the pointer (most of it stays: all of it goes, else all stays). */
	private static void leftClick(ServerPlayer p) {
		View v = VIEWS.get(p.getUUID());
		if (v == null || v.age < 4) return;
		if (button(v)) return;
		long c = v.hover;
		Scan.World w = Scan.world(v.dim);
		if (c == Long.MIN_VALUE || w == null) return;
		int rx = Scan.kx(c) >> 5, rz = Scan.kz(c) >> 5;
		int stay = 0, total = 0;
		for (int i = 0; i < 1024; i++) {
			int x = rx * 32 + (i & 31), z = rz * 32 + (i >> 5);
			Plan.Why why = v.ctx.why(w, x, z);
			if (why == Plan.Why.NOT_MADE || !v.ctx.pickable(why)) continue;
			total++;
			if (why.stays) stay++;
		}
		if (total == 0) return;
		boolean to = stay * 2 < total;
		boolean any = false;
		for (int i = 0; i < 1024; i++) any |= paint(v, w, rx * 32 + (i & 31), rz * 32 + (i >> 5), to);
		if (any) {
			changed(v);
			Ui.sound(p, SoundEvents.BOOK_PAGE_TURN, 0.5f, to ? 1.4f : 1.0f);
			Hud.flash(p, Ui.text("A whole region " + (to ? "stays" : "goes"), to ? ChatFormatting.GREEN : ChatFormatting.RED));
		}
	}

	/** One chunk to stay or go (when it may be picked). @return whether it changed */
	private static boolean paint(View v, Scan.World w, int x, int z, boolean stays) {
		Plan.Why why = v.ctx.why(w, x, z);
		if (!v.ctx.pickable(why) || why.stays == stays) return false;
		long k = Scan.key(x, z);
		// Back to the rules when they already say so; else picked by hand
		v.ctx.keep.remove(k);
		v.ctx.letGo.remove(k);
		Plan.Why rule = v.ctx.why(w, x, z);
		if (rule.stays == stays) {
			Plan.pick(v.ctx.id, k, null);
		} else {
			Plan.pick(v.ctx.id, k, stays);
			(stays ? v.ctx.keep : v.ctx.letGo).add(k);
		}
		return true;
	}

	private static void changed(View v) {
		v.dirty = true;
		v.lastCount = -100;
	}

	private static boolean button(View v) {
		for (Button b : v.buttons) {
			if (b.has(v.pu, v.pv)) {
				Ui.click(v.p);
				b.action().run();
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- the picture

	static final String[] WORLD_NAMES = {"Overworld", "Nether", "End"};
	@SuppressWarnings("unchecked")
	static final ResourceKey<Level>[] WORLD_KEYS = new ResourceKey[] {Level.OVERWORLD, Level.NETHER, Level.END};

	static String worldName(ResourceKey<Level> dim) {
		for (int i = 0; i < WORLD_KEYS.length; i++) if (WORLD_KEYS[i].equals(dim)) return WORLD_NAMES[i];
		return dim.identifier().getPath();
	}

	private static void recount(View v) {
		Scan.World w = Scan.world(v.dim);
		if (w == null) return;
		v.ctx = new Plan.Ctx(v.p.level().getServer(), v.dim);
		v.lastCount = v.age;
		v.count = Plan.count(v.ctx, w);
		words(v);
	}

	/** Everything new: the rules, the counts, the words, the picture. */
	private static void rebuild(View v) {
		recount(v);
		v.dirty = true;
	}

	private static void switchTo(View v, ResourceKey<Level> dim) {
		if (dim.equals(v.dim) || Scan.world(dim) == null) return;
		v.dim = dim;
		ServerLevel shown = v.p.level().getServer().getLevel(dim);
		if (shown != null) Scan.refreshLoaded(shown);
		center(v, v.p);
		v.zoom = 2;
		rebuild(v);
	}

	private static final byte DARK = MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.LOWEST);
	private static final byte RED = MapColor.COLOR_RED.getPackedId(MapColor.Brightness.HIGH);
	private static final byte CLAIM = MapColor.EMERALD.getPackedId(MapColor.Brightness.HIGH);
	private static final byte KEPT = MapColor.GOLD.getPackedId(MapColor.Brightness.HIGH);
	private static final byte HOVER = MapColor.SNOW.getPackedId(MapColor.Brightness.HIGH);
	private static final byte YOU = MapColor.SNOW.getPackedId(MapColor.Brightness.HIGH);
	private static final byte UNREAD = MapColor.COLOR_GRAY.getPackedId(MapColor.Brightness.LOW);

	/** A packed map colour, darker. */
	private static byte darker(byte c) {
		int id = (c & 0xFF) >> 2;
		if (id == 0) return DARK;
		return MapColor.byId(id).getPackedId(MapColor.Brightness.LOWEST);
	}

	private static void draw(View v) {
		Scan.World w = Scan.world(v.dim);
		if (w == null || v.ctx == null) return;
		int z = zoom(v);
		byte[] px = new byte[W * H];
		Map<Long, Plan.Why> whys = new HashMap<>();
		double ox = v.cx - W / 2.0 / z, oz = v.cz - H / 2.0 / z;
		int hx = Scan.kx(v.hover), hz = Scan.kz(v.hover);
		boolean hovering = v.hover != Long.MIN_VALUE;
		for (int y = 0; y < H; y++) {
			double wz = oz + y / (double) z;
			int cz = Mth.floor(wz);
			int bz = Mth.clamp((int) ((wz - cz) * 16), 0, 15);
			for (int x = 0; x < W; x++) {
				double wx = ox + x / (double) z;
				int cx = Mth.floor(wx);
				int bx = Mth.clamp((int) ((wx - cx) * 16), 0, 15);
				long k = Scan.key(cx, cz);
				Plan.Why why = whys.computeIfAbsent(k, kk -> v.ctx.why(w, cx, cz));
				byte c;
				if (why == Plan.Why.NOT_MADE) {
					c = DARK;
				} else {
					c = w.color(cx, cz, z >= 16 ? bx : Math.min(15, bx + 8 / z), z >= 16 ? bz : Math.min(15, bz + 8 / z));
					if (c == Colors.UNKNOWN) c = w.state(cx, cz) == Scan.THERE ? UNREAD : DARK;
					if (!why.stays) {
						c = darker(c);
						if (Math.floorMod(Mth.floor(wx * z) + Mth.floor(wz * z), 5) == 0) c = RED;
					}
				}
				// Frames: claims, what you kept, the chunk under the pointer
				int ex = Mth.floor(wx * z) - cx * z, ez = Mth.floor(wz * z) - cz * z;
				boolean edge = z >= 4 && (ex == 0 || ez == 0 || ex == z - 1 || ez == z - 1);
				if (why == Plan.Why.CLAIM && (z < 4 || edge && border(v, w, whys, cx, cz, ex, ez, z, Plan.Why.CLAIM))) c = CLAIM;
				if (why == Plan.Why.KEPT && (z < 4 || edge && border(v, w, whys, cx, cz, ex, ez, z, Plan.Why.KEPT))) c = KEPT;
				if (hovering && cx == hx && cz == hz && (z < 4 || edge)) c = HOVER;
				// Regions (every 32 chunks): a faint line
				if (z >= 2 && (Math.floorMod(cx, 32) == 0 && ex == 0 || Math.floorMod(cz, 32) == 0 && ez == 0) && c != CLAIM && c != KEPT && c != HOVER) {
					c = darker(c);
				}
				px[x + y * W] = c;
			}
		}
		// You, where you stand (in this world)
		if (v.p.level().dimension().equals(v.dim)) {
			int yx = (int) Math.round((v.p.getX() / 16.0 - ox) * z), yz = (int) Math.round((v.p.getZ() / 16.0 - oz) * z);
			for (int d = -2; d <= 2; d++) {
				dot(px, yx + d, yz, YOU);
				dot(px, yx, yz + d, YOU);
			}
		}
		send(v, px);
	}

	/** Is this edge of a chunk the edge of its group (claims, kept)? */
	private static boolean border(View v, Scan.World w, Map<Long, Plan.Why> whys, int cx, int cz, int ex, int ez, int z, Plan.Why kind) {
		int nx = cx, nz = cz;
		if (ex == 0) nx--;
		else if (ex == z - 1) nx++;
		else if (ez == 0) nz--;
		else nz++;
		int fx = nx, fz = nz;
		return whys.computeIfAbsent(Scan.key(nx, nz), kk -> v.ctx.why(w, fx, fz)) != kind;
	}

	private static void dot(byte[] px, int x, int y, byte c) {
		if (x >= 0 && y >= 0 && x < W && y < H) px[x + y * W] = c;
	}

	/** The tiles that changed, to this player. */
	private static void send(View v, byte[] px) {
		for (int t = 0; t < TW * TH; t++) {
			int tx = t % TW, ty = t / TW;
			byte[] tile = new byte[128 * 128];
			for (int y = 0; y < 128; y++) System.arraycopy(px, (ty * 128 + y) * W + tx * 128, tile, y * 128, 128);
			if (Arrays.equals(tile, v.sent[t])) continue;
			v.sent[t] = tile;
			v.p.connection.send(new ClientboundMapItemDataPacket(new MapId(v.mapIds[t]), (byte) 0, true, Optional.empty(),
					Optional.of(new MapItemSavedData.MapPatch(0, 0, 128, 128, tile))));
		}
	}

	// ---------------------------------------------------------------- words

	private static MutableComponent t(String s, int rgb) {
		return Component.literal(s).withStyle(st -> st.withColor(rgb));
	}

	private static final int WHITE = 0xFFFFFF, GREY = 0xC8CCD4, MUTED = 0x969CA8, ACCENT = 0xFFAA33, GREEN = 0x8CD25A, REDT = 0xE66E64, YELLOW = 0xFAEE4D;

	/** A text on the screen, its top left at (u, v) (blocks from the middle), or its top right when {@code right}. */
	private static void place(View v, TextDisplayElement el, Component text, double u, double vv, boolean right) {
		el.setText(text);
		List<Component> lines = new ArrayList<>();
		for (String line : text.getString().split("\n")) lines.add(Component.literal(line));
		int width = Glyphs.width(lines);
		int height = lines.size() * 10;
		double w = width * PX, h = height * PX;
		double cu = right ? u - w / 2 : u + w / 2;
		el.setOffset(v.screen(cu, vv - h, 0.03).subtract(v.origin));
	}

	private static double widthOf(String s) {
		return Glyphs.width(Component.literal(s)) * PX;
	}

	private static void words(View v) {
		v.buttons.clear();
		String world = worldName(v.dim);
		Plan.Ctx c = v.ctx;
		Scan.World w = Scan.world(v.dim);
		// (the bottom of the screen is the hotbar's: everything sits at the top)
		double left = -TW / 2.0 + 0.06, top = TH / 2.0 - 0.06, rightEdge = TW / 2.0 - 0.06;
		// Top left: what this is, what stays, the keys
		MutableComponent title = t("World refresh", ACCENT).append(t(" › ", MUTED)).append(t(world, WHITE)).append(t("\n", WHITE));
		title.append(t("Stays: claims, " + RefreshMod.ROOM.get() + " chunk" + (RefreshMod.ROOM.get() == 1 ? "" : "s") + " around them", GREY));
		title.append(t(RefreshMod.NEAR.get() > 0 ? "\nand where people were near " + RefreshMod.NEAR.get() + "+ min" : "", GREY));
		if (v.dim.equals(Level.END)) title.append(t("\nand the main island", GREY));
		title.append(t("\n\nWASD", WHITE)).append(t(": move the map", GREY));
		title.append(t("\nScroll", WHITE)).append(t(": zoom", GREY));
		title.append(t("\nRight-click", WHITE)).append(t(" (hold): stay / go", GREY));
		title.append(t("\nLeft-click", WHITE)).append(t(": a whole region", GREY));
		title.append(t("\nSneak", WHITE)).append(t(": done", GREY));
		place(v, v.title, title, left, top, false);
		// Top middle: the worlds, then the sums, then whether it takes part and done
		double tabsWidth = 0;
		for (String n : WORLD_NAMES) tabsWidth += widthOf(n) + 2 * PX;
		tabsWidth += 2 * 6 * PX;
		double tu = -tabsWidth / 2;
		for (int i = 0; i < 3; i++) {
			ResourceKey<Level> dim = WORLD_KEYS[i];
			boolean on = dim.equals(v.dim);
			boolean exists = Scan.world(dim) != null;
			TextDisplayElement el = v.tabs.get(i);
			el.setBackground(on ? CHIP_ON : CHIP);
			place(v, el, t(WORLD_NAMES[i], on ? WHITE : exists ? GREY : MUTED), tu, top, false);
			double tw = widthOf(WORLD_NAMES[i]) + 2 * PX;
			if (exists && !on) v.buttons.add(new Button(tu - PX, tu + tw, top - 11 * PX, top + PX, () -> switchTo(v, dim)));
			tu += tw + 6 * PX;
		}
		MutableComponent sums = Component.empty();
		if (w != null && !Restart.ready(w)) sums.append(t("Reading the world… " + Math.round(w.progress() * 100) + "%\n", YELLOW));
		if (!c.on) {
			sums.append(t("This world takes no part", MUTED));
		} else if (v.count != null) {
			sums.append(t(Ui.n(v.count.stay()) + " stay", GREEN)).append(t("  ·  ", MUTED)).append(t(Ui.n(v.count.go()) + " go", REDT))
					.append(t("\nabout " + mb(v.count.bytes()) + " less on disk", GREY));
		}
		int sumLines = sums.getString().split("\n").length;
		double sumsTop = top - 14 * PX;
		placeCentred(v, v.sums, sums, 0, sumsTop);
		double chipsTop = sumsTop - sumLines * 10 * PX - 5 * PX;
		String takesText = c.on ? "Takes part: yes" : "Takes part: no", doneText = "Done";
		double tw2 = widthOf(takesText) + 2 * PX, dw = widthOf(doneText) + 2 * PX, gap = 6 * PX;
		double cu = -(tw2 + gap + dw) / 2;
		v.takes.setBackground(c.on ? CHIP_ON : CHIP);
		place(v, v.takes, t(takesText, WHITE), cu, chipsTop, false);
		v.buttons.add(new Button(cu - PX, cu + tw2, chipsTop - 11 * PX, chipsTop + PX, () -> {
			Plan.Dim d = Plan.dim(c.id);
			d.on = !d.on;
			Plan.STORE.markDirty();
			rebuild(v);
		}));
		double du = cu + tw2 + gap;
		v.done.setBackground(CHIP);
		place(v, v.done, t(doneText, WHITE), du, chipsTop, false);
		v.buttons.add(new Button(du - PX, du + dw, chipsTop - 11 * PX, chipsTop + PX, () -> finish(v, true)));
		// Top right: what the colours mean
		MutableComponent legend = t("■ ", 0x8CC446).append(t("Stays\n", WHITE)).append(t("■ ", 0x993333)).append(t("Goes (made anew)\n", WHITE))
				.append(t("□ ", 0x00D93A)).append(t("Claim\n", WHITE)).append(t("□ ", YELLOW)).append(t("Kept by you\n", WHITE))
				.append(t("■ ", 0x3C3C40)).append(t("Not made yet", WHITE));
		place(v, v.legend, legend, rightEdge, top, true);
	}

	/** A text centred on {@code u}, its top at {@code vv}. */
	private static void placeCentred(View v, TextDisplayElement el, Component text, double u, double vv) {
		el.setText(text);
		List<Component> lines = new ArrayList<>();
		for (String line : text.getString().split("\n")) lines.add(Component.literal(line));
		el.setTextAlignment(Display.TextDisplay.Align.CENTER);
		el.setOffset(v.screen(u, vv - lines.size() * 10 * PX, 0.03).subtract(v.origin));
	}

	private static String mb(long bytes) {
		double mb = bytes / (1024.0 * 1024.0);
		return mb < 10 ? String.format(Locale.ROOT, "%.1f MB", mb) : Ui.n((int) Math.round(mb)) + " MB";
	}

	/** Under the hotbar: the chunk under the pointer. */
	private static void status(View v) {
		if (v.age % 4 != 0) return;
		Scan.World w = Scan.world(v.dim);
		if (w == null || v.ctx == null) return;
		Component line;
		if (v.hover == Long.MIN_VALUE) {
			line = t("Point at the map", MUTED);
		} else {
			int x = Scan.kx(v.hover), z = Scan.kz(v.hover);
			Plan.Why why = v.ctx.why(w, x, z);
			MutableComponent m = t("Chunk " + x + ", " + z, WHITE);
			if (why == Plan.Why.NOT_MADE) {
				m.append(t("  ·  never made yet", MUTED));
			} else {
				long min = w.near(x, z) / 1200;
				m.append(t("  ·  people near for " + (min >= 120 ? Ui.n((int) (min / 60)) + " h" : min + " min"), GREY));
				String owner = v.ctx.owner(x, z);
				String reason = owner != null ? owner + "'s claim" : why.words;
				m.append(t("  ·  ", MUTED)).append(t(why.stays ? "stays" : "goes", why.stays ? GREEN : REDT));
				if (why != Plan.Why.GO) m.append(t(" (" + reason + ")", MUTED));
			}
			line = m;
		}
		Hud.show(v.p, HUD, line, Hud.PRIO_WARNING, 10);
	}
}
