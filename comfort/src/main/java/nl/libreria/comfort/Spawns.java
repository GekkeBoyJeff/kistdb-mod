package nl.libreria.comfort;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Brightness;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Domain;
import nl.libreria.core.Notify;
import nl.libreria.core.Setting;
import nl.libreria.core.Ui;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Light levels and spawn spots, only for you: right-click a torch into the air (when nothing gets placed) and every
 * floor around you shows its light level, lying on the block. Where a monster can spawn there's a tile under the
 * number: red where it can at any time (no light at all), yellow where it can once it's night (under the open sky).
 * The same click again takes it away. Everything is virtual (only you see it) and follows you as you walk.
 */
final class Spawns {
	static final Setting.Toggle TORCH = ComfortMod.MODULE.toggle("spawns", "Light levels with a torch", true, Items.TORCH,
			"Right-click a torch into the air: the", "light level on every floor around you,", "red and yellow where monsters can spawn.",
			"The same click again: gone.").in(Domain.YOU, "Small comforts");

	/** How far around you (blocks sideways, and down and up). */
	static final int RADIUS = 12, DOWN = 6, UP = 4;
	/** At most this many marks (a cave can have many floors). */
	static final int MAX = 900;
	static final int RED = 0xFF4D4D, YELLOW = 0xFFE066, LIT = 0x9FE29F;

	/** What a floor is: lit (and how much), or where monsters spawn. */
	enum Kind {
		LIT, NIGHT, ALWAYS
	}

	record Spot(Kind kind, int light) {
	}

	private static final class View {
		ServerLevel level;
		Vec3 anchor;
		ElementHolder holder;
		final Map<BlockPos, Spot> spots = new HashMap<>();
		final Map<BlockPos, List<VirtualElement>> elements = new HashMap<>();
		BlockPos lastCenter;
	}

	private static final Map<UUID, View> VIEWS = new HashMap<>();

	private Spawns() {
	}

	/** Every torch: torch, soul torch, redstone torch, copper torch. */
	static boolean isTorch(Item item) {
		String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
		return path.equals("torch") || path.endsWith("_torch");
	}

	static void init() {
		// A torch used on nothing (in the air): vanilla does nothing then, so it's free
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp) || sp.isSpectator()) return InteractionResult.PASS;
			ItemStack stack = sp.getItemInHand(hand);
			if (!isTorch(stack.getItem()) || !TORCH.get(sp)) return InteractionResult.PASS;
			// only one hand counts (a torch in each hand would switch twice)
			if (hand == InteractionHand.OFF_HAND && isTorch(sp.getMainHandItem().getItem())) return InteractionResult.PASS;
			toggle(sp);
			return InteractionResult.SUCCESS;
		});
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> hide(handler.player.getUUID()));
	}

	static boolean shown(ServerPlayer p) {
		return VIEWS.containsKey(p.getUUID());
	}

	static void toggle(ServerPlayer p) {
		if (shown(p)) {
			hide(p.getUUID());
			Ui.sound(p, SoundEvents.CANDLE_EXTINGUISH, 0.6f, 1.2f);
			Notify.info(p, Items.TORCH, "Light levels hidden", "Right-click a torch into the air to see them again.");
			return;
		}
		View v = new View();
		VIEWS.put(p.getUUID(), v);
		refresh(p, v);
		Ui.sound(p, SoundEvents.FIRECHARGE_USE, 0.3f, 1.8f);
		Notify.info(p, Items.TORCH, "Light levels shown", "Red: monsters can spawn. Yellow: at night. Again: hide.");
	}

	static void hide(UUID id) {
		View v = VIEWS.remove(id);
		if (v != null && v.holder != null) v.holder.destroy();
	}

	static void tick(MinecraftServer server) {
		if (VIEWS.isEmpty() || server.getTickCount() % 10 != 3) return;
		for (UUID id : new ArrayList<>(VIEWS.keySet())) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null) {
				hide(id);
				continue;
			}
			refresh(p, VIEWS.get(id));
		}
	}

	/** What kind of floor {@code feet} stands on, or null when nothing can stand there. */
	static Spot spot(ServerLevel level, BlockPos feet) {
		BlockPos below = feet.below();
		BlockState floor = level.getBlockState(below);
		if (!floor.isFaceSturdy(level, below, Direction.UP)) return null;
		if (!free(level, feet) || !free(level, feet.above())) return null;
		int light = level.getBrightness(LightLayer.BLOCK, feet);
		if (light > 0) return new Spot(Kind.LIT, light);
		// glass, leaves, slabs on the bottom…: no monster spawns on them, so no tile (the number still shows)
		if (!floor.isValidSpawn(level, below, net.minecraft.world.entity.EntityTypes.ZOMBIE)) return new Spot(Kind.LIT, 0);
		return new Spot(level.getBrightness(LightLayer.SKY, feet) > 0 ? Kind.NIGHT : Kind.ALWAYS, 0);
	}

	private static boolean free(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return s.getCollisionShape(level, pos).isEmpty() && s.getFluidState().isEmpty();
	}

	/** Brings the marks up to date around the player: new floors get marks, changed ones new marks, gone ones go. */
	static void refresh(ServerPlayer p, View v) {
		ServerLevel level = (ServerLevel) p.level();
		BlockPos center = p.blockPosition();
		// another world, or walked far from where the marks hang: start over
		if (v.holder == null || v.level != level || v.anchor.distanceTo(p.position()) > 48) {
			if (v.holder != null) v.holder.destroy();
			v.spots.clear();
			v.elements.clear();
			v.level = level;
			v.anchor = Vec3.atBottomCenterOf(center);
			v.holder = new ElementHolder();
			Vec3 at = v.anchor;
			new ManualAttachment(v.holder, level, () -> at);
			v.holder.startWatching(p);
		}
		Map<BlockPos, Spot> now = new HashMap<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		outer:
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
				for (int dy = -DOWN; dy <= UP; dy++) {
					m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					Spot s = spot(level, m);
					if (s == null) continue;
					now.put(m.immutable(), s);
					if (now.size() >= MAX) break outer;
				}
			}
		}
		// gone or changed
		for (BlockPos pos : new ArrayList<>(v.spots.keySet())) {
			Spot was = v.spots.get(pos);
			if (was.equals(now.get(pos))) continue;
			for (VirtualElement e : v.elements.remove(pos)) v.holder.removeElement(e);
			v.spots.remove(pos);
		}
		// new
		for (var e : now.entrySet()) {
			if (v.spots.containsKey(e.getKey())) continue;
			List<VirtualElement> els = marks(v.anchor, e.getKey(), e.getValue());
			for (VirtualElement el : els) v.holder.addElement(el);
			v.elements.put(e.getKey(), els);
			v.spots.put(e.getKey(), e.getValue());
		}
		v.lastCenter = center;
	}

	/** The tile (where monsters spawn) and the number for one floor. */
	static List<VirtualElement> marks(Vec3 anchor, BlockPos feet, Spot s) {
		List<VirtualElement> out = new ArrayList<>(2);
		Vec3 at = new Vec3(feet.getX() + 0.5, feet.getY() + 0.005, feet.getZ() + 0.5).subtract(anchor);
		if (s.kind() != Kind.LIT) {
			BlockDisplayElement tile = new BlockDisplayElement(Ui.block(s.kind() == Kind.ALWAYS ? "red_stained_glass" : "yellow_stained_glass"));
			tile.setOffset(at);
			tile.setTranslation(new Vector3f(-0.3f, 0f, -0.3f));
			tile.setScale(new Vector3f(0.6f, 0.02f, 0.6f));
			tile.setBrightness(new Brightness(15, 15));
			tile.setViewRange(0.5f);
			tile.setDisplaySize(1.5f, 1f);
			out.add(tile);
		}
		// the number lies on the floor and turns to face you (readable from wherever you stand)
		TextDisplayElement n = new TextDisplayElement(Ui.text(Integer.toString(s.light()), s.kind() == Kind.LIT ? LIT : 0xFFFFFF));
		n.setOffset(at.add(0, 0.02, 0));
		n.setBillboardMode(Display.BillboardConstraints.VERTICAL);
		n.setLeftRotation(new Quaternionf().rotateX((float) -Math.PI / 2));
		n.setTranslation(new Vector3f(0f, 0f, 0.12f));
		n.setScale(new Vector3f(1.1f, 1.1f, 1.1f));
		n.setBackground(0);
		n.setShadow(true);
		n.setBrightness(new Brightness(15, 15));
		n.setViewRange(0.5f);
		n.setDisplaySize(1.5f, 1f);
		out.add(n);
		return out;
	}

	static int count(ServerPlayer p) {
		View v = VIEWS.get(p.getUUID());
		return v == null ? 0 : v.spots.size();
	}
}
