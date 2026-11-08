package nl.libreria.comfort;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Rotations;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.Domain;
import nl.libreria.core.Notify;
import nl.libreria.core.Services;
import nl.libreria.core.Setting;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Values;
import nl.libreria.core.dialog.Window;

/**
 * Armour stands and item frames. Sneak + right-click a stand with an empty hand: a window with poses (applied at once,
 * the stand behind the window changes), its switches (arms, base plate, small, invisible, name), fine-tuning per part
 * with sliders, and copying a pose to another stand. Sneak + right-click an item frame with an empty hand: the frame
 * hides and only its item shows (again: it comes back); a hidden frame that loses its item shows again, so it can't get
 * lost on a wall. A hidden frame is locked: a right-click on it (or its item) goes through to the block it hangs on, so
 * the chest behind a label opens instead of the item turning. Only where you may build (on someone's land: when they
 * trust you).
 */
final class Stands {
	static final Setting.Toggle EDIT = ComfortMod.MODULE.toggle("stands", "Armour stands and frames", true, Items.ARMOR_STAND,
			"{sneak} + right-click an armour stand", "with an empty hand: pose it. The same", "on an item frame: hide the frame, so",
			"only its item shows; clicks go through", "it to the chest behind.").in(Domain.YOU, "Small comforts");

	/** A whole pose: head, body, left arm, right arm, left leg, right leg. */
	record Pose(Rotations head, Rotations body, Rotations leftArm, Rotations rightArm, Rotations leftLeg, Rotations rightLeg) {
		static Pose of(ArmorStand s) {
			return new Pose(s.getHeadPose(), s.getBodyPose(), s.getLeftArmPose(), s.getRightArmPose(), s.getLeftLegPose(), s.getRightLegPose());
		}

		void applyTo(ArmorStand s) {
			s.setHeadPose(this.head);
			s.setBodyPose(this.body);
			s.setLeftArmPose(this.leftArm);
			s.setRightArmPose(this.rightArm);
			s.setLeftLegPose(this.leftLeg);
			s.setRightLegPose(this.rightLeg);
		}

		/** Left and right swapped, as in a mirror. */
		Pose mirrored() {
			return new Pose(flip(this.head), flip(this.body), flip(this.rightArm), flip(this.leftArm), flip(this.rightLeg), flip(this.leftLeg));
		}

		private static Rotations flip(Rotations r) {
			return new Rotations(r.x(), -r.y(), -r.z());
		}
	}

	private static Rotations r(float x, float y, float z) {
		return new Rotations(x, y, z);
	}

	/** The poses in the window, in order. Standing is the game's own default. */
	static final Map<String, Pose> POSES = new LinkedHashMap<>();

	static {
		POSES.put("Standing", new Pose(r(0, 0, 0), r(0, 0, 0), r(-10, 0, -10), r(-15, 0, 10), r(-1, 0, -1), r(1, 0, 1)));
		POSES.put("Waving", new Pose(r(-5, 0, 0), r(0, 0, 0), r(-10, 0, -10), r(-160, 0, -10), r(-1, 0, -1), r(1, 0, 1)));
		POSES.put("Pointing", new Pose(r(0, -20, 0), r(0, 0, 0), r(10, 0, -10), r(-90, -20, 0), r(-1, 0, -1), r(1, 0, 1)));
		POSES.put("Saluting", new Pose(r(0, 0, 0), r(0, 0, 0), r(0, 0, -5), r(-120, 40, 50), r(-1, 0, -1), r(1, 0, 1)));
		POSES.put("Thinking", new Pose(r(15, 0, 0), r(0, 0, 0), r(-60, 40, 0), r(-120, 40, 0), r(-1, 0, -1), r(1, 0, 1)));
		POSES.put("Cheering", new Pose(r(-15, 0, 0), r(0, 0, 0), r(-170, 0, -15), r(-170, 0, 15), r(-1, 0, -1), r(1, 0, 1)));
		POSES.put("Walking", new Pose(r(0, 0, 0), r(0, 0, 0), r(30, 0, -5), r(-30, 0, 5), r(-25, 0, -1), r(25, 0, 1)));
		POSES.put("Sitting", new Pose(r(0, 0, 0), r(0, 0, 0), r(-40, 0, -10), r(-40, 0, 10), r(-90, -10, 0), r(-90, 10, 0)));
	}

	/** The parts you can fine-tune. */
	static final String[][] PARTS = {{"head", "Head"}, {"body", "Body"}, {"rarm", "Right arm"}, {"larm", "Left arm"}, {"rleg", "Right leg"},
			{"lleg", "Left leg"}};

	/** A copied pose per player, to give to another stand. */
	private static final Map<UUID, Pose> COPIED = new HashMap<>();
	/** The client sends a right-click on an entity twice (at a spot, and on it): act once a tick. */
	private static final Map<UUID, Long> LAST_TICK = new HashMap<>();

	private Stands() {
	}

	static void init() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp) || sp.isSpectator()) return InteractionResult.PASS;
			// A hidden frame is locked: the click goes through it to the block behind (sneak + empty hand still shows it)
			if (entity instanceof ItemFrame locked && locked(locked) && !(sp.isShiftKeyDown() && sp.getMainHandItem().isEmpty() && EDIT.get(sp))) {
				long tick = sp.level().getServer().getTickCount();
				Long last = LAST_TICK.put(sp.getUUID(), tick);
				if (hand == InteractionHand.MAIN_HAND && (last == null || last != tick)) through(sp, (ServerLevel) world, locked);
				return InteractionResult.SUCCESS;
			}
			if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			if (!(entity instanceof ArmorStand) && !(entity instanceof ItemFrame)) return InteractionResult.PASS;
			if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) nl.libreria.core.Libreria.LOG.info("Stands: {} sneak {} empty {} hit {}", entity.getType(),
					sp.isShiftKeyDown(), sp.getMainHandItem().isEmpty(), hit != null);
			if (!sp.isShiftKeyDown() || !sp.getMainHandItem().isEmpty() || !EDIT.get(sp)) return InteractionResult.PASS;
			// someone else's land: let the land protection say no
			if (!Services.mayBuild(sp, (ServerLevel) world, entity.blockPosition())) return InteractionResult.PASS;
			long tick = sp.level().getServer().getTickCount();
			Long last = LAST_TICK.put(sp.getUUID(), tick);
			if (last != null && last == tick) return InteractionResult.SUCCESS;
			if (entity instanceof ArmorStand stand) {
				if (stand.isMarker()) return InteractionResult.PASS;
				open(sp, stand);
				return InteractionResult.SUCCESS;
			}
			ItemFrame frame = (ItemFrame) entity;
			if (frame.getItem().isEmpty()) return InteractionResult.PASS;
			frame.setInvisible(!frame.isInvisible());
			Ui.sound(sp, SoundEvents.ITEM_FRAME_ROTATE_ITEM, 0.6f, frame.isInvisible() ? 1.4f : 0.9f);
			nl.libreria.core.Hud.flash(sp, Ui.text(frame.isInvisible() ? "Frame hidden: only the item shows" : "Frame shown again",
					ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		});
		// A hidden frame that loses its item shows again (else it's an invisible thing on the wall nobody finds)
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || !(entity instanceof ItemFrame frame) || !frame.isInvisible()) return InteractionResult.PASS;
			world.getServer().execute(() -> {
				if (frame.isAlive() && frame.getItem().isEmpty()) frame.setInvisible(false);
			});
			return InteractionResult.PASS;
		});
	}

	/** A hidden frame with something in it: clicks go through it. */
	static boolean locked(ItemFrame frame) {
		return frame.isInvisible() && !frame.getItem().isEmpty();
	}

	/**
	 * A right-click on the block a frame hangs on, as if the frame weren't there: the chest opens, the door turns, the
	 * button is pressed. Only what the block does itself, never what's in your hand (a block in your hand isn't placed
	 * where the frame hangs). Through the game's own way, so land protection still has its say.
	 */
	static InteractionResult through(ServerPlayer sp, ServerLevel level, ItemFrame frame) {
		net.minecraft.core.Direction face = frame.getDirection();
		net.minecraft.core.BlockPos behind = frame.getPos().relative(face.getOpposite());
		net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atCenterOf(behind)
				.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
		var hit = new net.minecraft.world.phys.BlockHitResult(at, face, behind, false);
		return sp.gameMode.useItemOn(sp, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
	}

	/** Can this player still work on this stand (it's there, near, and theirs to change)? */
	static boolean usable(ServerPlayer p, ArmorStand stand) {
		return stand.isAlive() && stand.level() == p.level() && stand.distanceTo(p) < 10
				&& Services.mayBuild(p, (ServerLevel) p.level(), stand.blockPosition());
	}

	private static MutableComponent onOff(String label, boolean on) {
		return Ui.text(label + ": ", ChatFormatting.WHITE).append(Ui.text(on ? "on" : "off", on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
	}

	/** The window: poses, switches, fine-tune, copy and paste. Every click applies at once and opens it again. */
	static void open(ServerPlayer p, ArmorStand stand) {
		if (!usable(p, stand)) return;
		Window w = Window.of(Domain.YOU, "Armour stand");
		w.item(new ItemStack(Items.ARMOR_STAND), Ui.text("Every click shows at once on the stand. Fine-tune each part, "
				+ "or copy this pose and give it to another stand.", ChatFormatting.GRAY));
		w.columns(2);
		for (var e : POSES.entrySet()) {
			w.button(Ui.text(e.getKey(), ChatFormatting.WHITE), null, (pl, v) -> change(pl, stand, s -> e.getValue().applyTo(s)));
		}
		w.button(onOff("Arms", stand.showArms()), null, (pl, v) -> change(pl, stand, s -> s.setShowArms(!s.showArms())));
		w.button(onOff("Base plate", stand.showBasePlate()), null, (pl, v) -> change(pl, stand, s -> s.setNoBasePlate(s.showBasePlate())));
		w.button(onOff("Small", stand.isSmall()), null, (pl, v) -> change(pl, stand, s -> setSmall(s, !s.isSmall())));
		w.button(onOff("Invisible", stand.isInvisible()), Ui.text("Only its gear shows", ChatFormatting.GRAY),
				(pl, v) -> change(pl, stand, s -> s.setInvisible(!s.isInvisible())));
		if (stand.hasCustomName()) {
			w.button(onOff("Name above it", stand.isCustomNameVisible()), null,
					(pl, v) -> change(pl, stand, s -> s.setCustomNameVisible(!s.isCustomNameVisible())));
		}
		w.button(Ui.text("Mirror", ChatFormatting.AQUA), Ui.text("Left and right swapped", ChatFormatting.GRAY),
				(pl, v) -> change(pl, stand, s -> Pose.of(s).mirrored().applyTo(s)));
		w.button(Ui.text("Fine-tune ▸", ChatFormatting.AQUA), Ui.text("Head, arms, legs and body, one by one", ChatFormatting.GRAY),
				(pl, v) -> fineTune(pl, stand, "rarm"));
		w.button(Ui.text("Copy pose", ChatFormatting.AQUA), Ui.text("Then sneak + right-click another stand, and Paste", ChatFormatting.GRAY),
				(pl, v) -> {
					COPIED.put(pl.getUUID(), Pose.of(stand));
					Notify.done(pl, Items.ARMOR_STAND, "Pose copied", "Open another stand and click Paste pose.");
					open(pl, stand);
				});
		Pose copied = COPIED.get(p.getUUID());
		if (copied != null) {
			w.button(Ui.text("Paste pose", ChatFormatting.GREEN), Ui.text("The pose you copied", ChatFormatting.GRAY),
					(pl, v) -> change(pl, stand, copied::applyTo));
		}
		w.bottom(Ui.text("✔ Done", ChatFormatting.GREEN), (pl, v) -> {
		}).show(p);
	}

	/** Changes the stand (when still allowed), a click, and the window again. */
	private static void change(ServerPlayer p, ArmorStand stand, java.util.function.Consumer<ArmorStand> what) {
		if (!usable(p, stand)) {
			Notify.warn(p, Items.ARMOR_STAND, "Can't reach that stand", "It's gone, too far, or not yours to change.");
			return;
		}
		what.accept(stand);
		Ui.click(p);
		open(p, stand);
	}

	/** Small isn't public on the stand: its flag in the synced data (the game sizes the stand from it). */
	static void setSmall(ArmorStand s, boolean small) {
		byte flags = s.getEntityData().get(ArmorStand.DATA_CLIENT_FLAGS);
		flags = small ? (byte) (flags | ArmorStand.CLIENT_FLAG_SMALL) : (byte) (flags & ~ArmorStand.CLIENT_FLAG_SMALL);
		s.getEntityData().set(ArmorStand.DATA_CLIENT_FLAGS, flags);
	}

	static Rotations get(ArmorStand s, String part) {
		return switch (part) {
			case "head" -> s.getHeadPose();
			case "body" -> s.getBodyPose();
			case "larm" -> s.getLeftArmPose();
			case "rleg" -> s.getRightLegPose();
			case "lleg" -> s.getLeftLegPose();
			default -> s.getRightArmPose();
		};
	}

	static void set(ArmorStand s, String part, Rotations r) {
		switch (part) {
			case "head" -> s.setHeadPose(r);
			case "body" -> s.setBodyPose(r);
			case "larm" -> s.setLeftArmPose(r);
			case "rleg" -> s.setRightLegPose(r);
			case "lleg" -> s.setLeftLegPose(r);
			default -> s.setRightArmPose(r);
		}
	}

	private static String partName(String part) {
		for (String[] p : PARTS) if (p[0].equals(part)) return p[1];
		return part;
	}

	/** Sliders for one part; the part buttons switch (keeping what you set), Apply shows it. */
	static void fineTune(ServerPlayer p, ArmorStand stand, String part) {
		if (!usable(p, stand)) return;
		Rotations now = get(stand, part);
		Window w = Window.of(Domain.YOU, "Armour stand", partName(part));
		w.slider("x", Ui.text("Forward / back", ChatFormatting.WHITE), -180, 180, 5, wrap(now.x()));
		w.slider("y", Ui.text("Turn", ChatFormatting.WHITE), -180, 180, 5, wrap(now.y()));
		w.slider("z", Ui.text("Out to the side", ChatFormatting.WHITE), -180, 180, 5, wrap(now.z()));
		w.columns(2);
		w.button(Ui.text("Apply", ChatFormatting.GREEN), null, (pl, v) -> {
			apply(pl, stand, part, v);
			fineTune(pl, stand, part);
		});
		w.button(Ui.text("Reset this part", ChatFormatting.AQUA), null, (pl, v) -> {
			if (usable(pl, stand)) set(stand, part, get(POSES.get("Standing"), part));
			fineTune(pl, stand, part);
		});
		for (String[] other : PARTS) {
			if (other[0].equals(part)) continue;
			w.button(Ui.text(other[1], ChatFormatting.WHITE), null, (pl, v) -> {
				apply(pl, stand, part, v);
				fineTune(pl, stand, other[0]);
			});
		}
		w.bottom(Ui.text("◀ Back", ChatFormatting.WHITE), (pl, v) -> {
			apply(pl, stand, part, v);
			open(pl, stand);
		}).show(p);
	}

	private static void apply(ServerPlayer p, ArmorStand stand, String part, Values v) {
		if (!usable(p, stand)) return;
		Rotations now = get(stand, part);
		set(stand, part, new Rotations((float) v.number("x", wrap(now.x())), (float) v.number("y", wrap(now.y())), (float) v.number("z", wrap(now.z()))));
	}

	/** -180..180 (the game keeps angles as they were set, e.g. 350). */
	static float wrap(float a) {
		float w = a % 360f;
		if (w > 180) w -= 360;
		if (w < -180) w += 360;
		return w;
	}

	/** One part of a pose. */
	static Rotations get(Pose pose, String part) {
		return switch (part) {
			case "head" -> pose.head();
			case "body" -> pose.body();
			case "larm" -> pose.leftArm();
			case "rleg" -> pose.rightLeg();
			case "lleg" -> pose.leftLeg();
			default -> pose.rightArm();
		};
	}

	static List<String> poseNames() {
		return List.copyOf(POSES.keySet());
	}
}
