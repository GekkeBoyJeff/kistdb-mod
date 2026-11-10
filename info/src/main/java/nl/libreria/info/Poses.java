package nl.libreria.info;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * How a mannequin of ours stands, all with Minecraft's own means: its stance (standing, sitting, leaning in, crouching,
 * lying down, crawling), where its head looks, what its hands do with what they hold, and which way it faces.
 *
 * <p>It sits on an invisible marker armour stand (a "seat"): on the stair or slab under it as on a chair, else on the
 * ground. Leaning in is sitting while crouching. Lying down on a bed, it lies in the bed (as a villager does). Its hands
 * "use" what they hold, kept going while it is loaded: a bow drawn, a shield raised, a spyglass to its eye, a horn
 * blown, a trident raised to throw. A loaded crossbow aims by itself. Where it stood before it sat down or went to bed
 * is kept, so it stands up there again.
 */
final class Poses {
	private Poses() {
	}

	// ---------------------------------------------------------------- what is kept per mannequin

	/**
	 * Kept on a mannequin: where it stood (while it sits or lies in a bed), its head turned from its body (degrees, its
	 * right is +), and the hand that uses what it holds ("", "main" or "off").
	 */
	record Kept(Optional<Vec3> spot, float head, String hand) {
		static final Kept NONE = new Kept(Optional.empty(), 0, "");
		static final Codec<Kept> CODEC = RecordCodecBuilder.create(i -> i.group(Vec3.CODEC.optionalFieldOf("spot").forGetter(Kept::spot),
				Codec.FLOAT.optionalFieldOf("head", 0f).forGetter(Kept::head), Codec.STRING.optionalFieldOf("hand", "").forGetter(Kept::hand))
				.apply(i, Kept::new));

		Kept withSpot(@Nullable Vec3 v) {
			return new Kept(Optional.ofNullable(v), this.head, this.hand);
		}

		Kept withHead(float h) {
			return new Kept(this.spot, h, this.hand);
		}

		Kept withHand(String h) {
			return new Kept(this.spot, this.head, h);
		}
	}

	static final AttachmentType<Kept> KEPT = AttachmentRegistry.create(Identifier.fromNamespaceAndPath("libreria", "mannequin_pose"),
			b -> b.persistent(Kept.CODEC));

	static Kept kept(Mannequin m) {
		return m.getAttachedOrElse(KEPT, Kept.NONE);
	}

	private static void keep(Mannequin m, Kept k) {
		m.setAttached(KEPT, k);
	}

	// ---------------------------------------------------------------- stances

	enum Stance {
		STAND("Stand", "Standing"), SIT("Sit", "Sitting"), LEAN("Lean in", "Leaning in"), CROUCH("Crouch", "Crouching"), LIE("Lie down", "Lying down"),
		CRAWL("Crawl", "Crawling");

		/** On its button. */
		final String verb;
		/** What it is doing. */
		final String doing;

		Stance(String verb, String doing) {
			this.verb = verb;
			this.doing = doing;
		}

		String icon() {
			return "libreria:mannequin/pose_" + switch (this) {
				case STAND -> "stand";
				case SIT -> "sit";
				case LEAN -> "lean";
				case CROUCH -> "crouch";
				case LIE -> "lie";
				case CRAWL -> "crawl";
			};
		}
	}

	static Stance stance(Mannequin m) {
		if (seated(m)) return m.getPose() == Pose.CROUCHING ? Stance.LEAN : Stance.SIT;
		return switch (m.getPose()) {
			case CROUCHING -> Stance.CROUCH;
			case SLEEPING -> Stance.LIE;
			case SWIMMING -> Stance.CRAWL;
			default -> Stance.STAND;
		};
	}

	/** Standing up straight: what it carries on its back and hips shows there (else it lies before it). */
	static boolean upright(Mannequin m) {
		return m.getPose() == Pose.STANDING;
	}

	/** Where it is, in a few words ("on a chair", "in a bed", "on the ground"), for its window. */
	static String where(Mannequin m) {
		Stance s = stance(m);
		if (s == Stance.SIT || s == Stance.LEAN) {
			Entity seat = m.getVehicle();
			BlockState under = seat == null ? null : m.level().getBlockState(BlockPos.containing(seat.position().subtract(0, 0.25, 0)));
			if (under != null && under.getBlock() instanceof StairBlock) return "on a stair";
			if (under != null && under.getBlock() instanceof SlabBlock) return "on a slab";
			return "on the ground";
		}
		if (s == Stance.LIE) return m.isSleeping() ? "in a bed" : "on the ground";
		if (s == Stance.CRAWL && m.isInWater()) return "swimming";
		return "";
	}

	/**
	 * It takes this stance where it is. Sitting: on the stair or bottom slab it stands on (facing away from a stair's
	 * back), else on the ground. Lying down: in the bed it stands on, else on the ground. It stands up where it stood.
	 * @return a few words about why not, or null when it did
	 */
	static @Nullable String set(Mannequin m, Stance to) {
		track(m);
		Stance now = stance(m);
		if (now == to) return null;
		ServerLevel level = (ServerLevel) m.level();
		// Back to where it stood first (off the seat, out of the bed)
		Vec3 spot = kept(m).spot().orElse(null);
		float yaw = m.getYRot();
		if (seated(m) || m.isSleeping()) {
			leave(m);
			if (spot != null) m.setPos(spot);
			keep(m, kept(m).withSpot(null));
		}
		switch (to) {
			case SIT, LEAN -> {
				Vec3 at = m.position();
				Seat seat = seatAt(level, at, yaw);
				ArmorStand stand = seatEntity(level, seat.pos(), seat.yaw());
				if (stand == null) return "There's no room to sit";
				turnTo(m, seat.yaw());
				keep(m, kept(m).withSpot(at));
				if (!m.startRiding(stand, true, true)) {
					stand.discard();
					keep(m, kept(m).withSpot(null));
					return "It can't sit here";
				}
				// (getting on something makes it stand: lean in after, and once more for the players, see leanAgain)
				m.setPose(to == Stance.LEAN ? Pose.CROUCHING : Pose.STANDING);
				if (to == Stance.LEAN) AGAIN.add(m);
			}
			case LIE -> {
				BlockPos bed = bedAt(level, m.blockPosition());
				Vec3 at = m.position();
				if (bed != null) {
					BlockState b = level.getBlockState(bed);
					level.setBlock(bed, b.setValue(BedBlock.OCCUPIED, true), 3);
					keep(m, kept(m).withSpot(at));
					m.setNoGravity(true);
					m.setPose(Pose.SLEEPING);
					m.setPos(bed.getX() + 0.5, bed.getY() + 0.6875, bed.getZ() + 0.5);
					m.setSleepingPos(bed);
				} else {
					m.setPose(Pose.SLEEPING);
				}
				m.refreshDimensions();
			}
			case CROUCH -> m.setPose(Pose.CROUCHING);
			case CRAWL -> m.setPose(Pose.SWIMMING);
			default -> m.setPose(Pose.STANDING);
		}
		m.refreshDimensions();
		look(m);
		Showcase.show(m);
		return null;
	}

	/** Off its seat or out of its bed (staying where it is: the caller puts it back). */
	private static void leave(Mannequin m) {
		Entity v = m.getVehicle();
		if (v != null) {
			m.stopRiding();
			if (isSeat(v)) v.discard();
		}
		if (m.isSleeping()) {
			BlockPos bed = m.getSleepingPos().orElse(null);
			if (bed != null) {
				BlockState b = m.level().getBlockState(bed);
				if (b.getBlock() instanceof BedBlock) m.level().setBlock(bed, b.setValue(BedBlock.OCCUPIED, false), 3);
			}
			m.clearSleepingPos();
			m.setNoGravity(false);
		}
	}

	/** When it's taken down: off its seat, out of its bed. */
	static void takenDown(Mannequin m) {
		leave(m);
	}

	/** Where it is really (for what drops when it's taken down): where it stood, if it sits or lies in a bed. */
	static Vec3 standingSpot(Mannequin m) {
		return kept(m).spot().orElse(m.position());
	}

	// ---------------------------------------------------------------- seats

	static final String SEAT_TAG = "libreria_mannequin_seat";

	static boolean isSeat(Entity e) {
		return e instanceof ArmorStand a && a.entityTags().contains(SEAT_TAG);
	}

	static boolean seated(Mannequin m) {
		return m.getVehicle() != null && isSeat(m.getVehicle());
	}

	private record Seat(Vec3 pos, float yaw) {
	}

	/** Where it sits down from standing at {@code at}: the top of a stair or bottom slab under it, else the ground. */
	private static Seat seatAt(ServerLevel level, Vec3 at, float yaw) {
		BlockPos feet = BlockPos.containing(at.x, at.y + 0.01, at.z);
		for (BlockPos p : new BlockPos[] {feet, feet.below()}) {
			BlockState s = level.getBlockState(p);
			Vec3 c = Vec3.atBottomCenterOf(p);
			if (s.getBlock() instanceof StairBlock && s.getValue(StairBlock.HALF) == Half.BOTTOM) {
				Direction back = s.getValue(StairBlock.FACING);
				// a little forward, away from its back, facing away from it
				return new Seat(new Vec3(c.x - back.getStepX() * 0.2, p.getY() + 0.5, c.z - back.getStepZ() * 0.2), back.getOpposite().toYRot());
			}
			if (s.getBlock() instanceof SlabBlock && s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
				return new Seat(new Vec3(c.x, p.getY() + 0.5, c.z), yaw);
			}
		}
		return new Seat(at, yaw);
	}

	/** A seat: an invisible marker armour stand, saved with the world (with the mannequin on it). */
	private static @Nullable ArmorStand seatEntity(ServerLevel level, Vec3 at, float yaw) {
		ArmorStand a = net.minecraft.world.entity.EntityTypes.ARMOR_STAND.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		if (a == null) return null;
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("Marker", true);
		tag.putBoolean("Invisible", true);
		tag.putBoolean("NoGravity", true);
		tag.putBoolean("Invulnerable", true);
		tag.putBoolean("Silent", true);
		a.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag));
		a.addTag(SEAT_TAG);
		a.snapTo(at.x, at.y, at.z, yaw, 0);
		a.setYBodyRot(yaw);
		a.setYHeadRot(yaw);
		return level.addFreshEntity(a) ? a : null;
	}

	/** The head of a free bed at or under {@code feet}, or null. */
	private static @Nullable BlockPos bedAt(ServerLevel level, BlockPos feet) {
		for (BlockPos p : new BlockPos[] {feet, feet.below()}) {
			BlockState s = level.getBlockState(p);
			if (!(s.getBlock() instanceof BedBlock)) continue;
			BlockPos head = s.getValue(BedBlock.PART) == BedPart.HEAD ? p : p.relative(s.getValue(BedBlock.FACING));
			BlockState h = level.getBlockState(head);
			if (!(h.getBlock() instanceof BedBlock) || h.getValue(BedBlock.OCCUPIED)) return null;
			return head;
		}
		return null;
	}

	// ---------------------------------------------------------------- turning, and its head

	/** Faces {@code yaw} (its seat too, which decides how a sitting one is drawn). */
	static void turnTo(Mannequin m, float yaw) {
		yaw = Mth.wrapDegrees(yaw);
		m.setYRot(yaw);
		m.setYBodyRot(yaw);
		if (seated(m)) {
			Entity seat = m.getVehicle();
			seat.setYRot(yaw);
			if (seat instanceof ArmorStand a) a.setYBodyRot(yaw);
		}
		look(m);
	}

	/** Turns it 45 degrees (+: to its right). False when it can't (lying in a bed). */
	static boolean turn(Mannequin m, int dir) {
		if (m.isSleeping()) return false;
		turnTo(m, Math.round((m.getYRot() + 45 * dir) / 45f) * 45f);
		Showcase.show(m);
		return true;
	}

	/** Where it faces, in words. */
	static String facing(Mannequin m) {
		String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
		int i = Math.floorMod(Math.round(m.getYRot() / 45f), 8);
		return names[i];
	}

	/** Where its head can look: turned (its right +) and tilted (down +). */
	enum Look {
		AHEAD("Looking ahead", 0, 0), UP("Looking up", 0, -40), DOWN("Looking down", 0, 35), LEFT("Looking left", -50, 0), RIGHT("Looking right", 50, 0);

		final String label;
		final float yaw, pitch;

		Look(String label, float yaw, float pitch) {
			this.label = label;
			this.yaw = yaw;
			this.pitch = pitch;
		}
	}

	/** Its head as one of the looks, or null when it was turned towards someone. */
	static @Nullable Look look(Mannequin m, boolean exact) {
		float h = kept(m).head(), x = m.getXRot();
		for (Look l : Look.values()) if (Math.abs(l.yaw - h) < 1 && Math.abs(l.pitch - x) < 1) return l;
		return exact ? null : Look.AHEAD;
	}

	static String lookLabel(Mannequin m) {
		Look l = look(m, true);
		return l != null ? l.label : "Looking your way";
	}

	static void setLook(Mannequin m, float yaw, float pitch) {
		track(m);
		keep(m, kept(m).withHead(Mth.clamp(yaw, -50, 50)));
		m.setXRot(Mth.clamp(pitch, -60, 60));
		look(m);
	}

	/** Its head towards this player (as far as a head turns). */
	static void lookAt(Mannequin m, ServerPlayer p) {
		Vec3 eye = m.getEyePosition(), to = p.getEyePosition().subtract(eye);
		float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
		float pitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
		setLook(m, Mth.wrapDegrees(yaw - m.getYRot()), pitch);
	}

	/**
	 * Puts its head where it is kept. Only the players see it turned: for Minecraft it looks straight ahead, because a
	 * player who first sees a mannequin takes its body to face where its head does (a turned head would turn all of it).
	 * So the turn goes to the players after Minecraft's own updates, each tick it changes and to whoever sees it anew.
	 */
	static void look(Mannequin m) {
		m.setYHeadRot(m.getYRot());
		HEADS.add(m);
	}

	/** Sitting and crouching: leaning in. */
	static boolean leaning(Mannequin m) {
		return seated(m) && m.getPose() == Pose.CROUCHING;
	}

	/** Leaning in, to be sent again (see {@link #leanAgain}). */
	private static final Set<Mannequin> AGAIN = new HashSet<>();

	/**
	 * A player's game makes whatever gets on something stand up straight, also when it is told who sits where after it
	 * was told how it sits. So once the players know it sits, it leans in again: changed and changed back, it goes out
	 * anew with Minecraft's next update.
	 */
	private static void leanAgain() {
		if (AGAIN.isEmpty()) return;
		for (Mannequin m : AGAIN) {
			if (m.isRemoved() || !leaning(m)) continue;
			m.setPose(Pose.STANDING);
			m.setPose(Pose.CROUCHING);
		}
		AGAIN.clear();
	}

	/** Mannequins whose turned heads go out to the players at the end of this tick. */
	private static final Set<Mannequin> HEADS = new HashSet<>();

	private static void sendHeads() {
		if (HEADS.isEmpty()) return;
		for (Mannequin m : HEADS) {
			if (m.isRemoved() || !(m.level() instanceof ServerLevel level)) continue;
			var packet = new net.minecraft.network.protocol.game.ClientboundRotateHeadPacket(m, Mth.packDegrees(m.getYRot() + kept(m).head()));
			for (ServerPlayer p : level.players()) if (p.distanceToSqr(m) < 256 * 256) p.connection.send(packet);
		}
		HEADS.clear();
	}

	// ---------------------------------------------------------------- what its hands do

	/** What a hand can do with what it holds: the way Minecraft shows a player using it. */
	static @Nullable String act(ItemStack s) {
		if (s.isEmpty()) return null;
		ItemUseAnimation a = s.getUseAnimation();
		if (a == ItemUseAnimation.BOW) return "Drawing the bow";
		if (a == ItemUseAnimation.BLOCK) return "Raising the shield";
		if (a == ItemUseAnimation.SPYGLASS) return "Using the spyglass";
		if (a == ItemUseAnimation.TOOT_HORN) return "Blowing the horn";
		if (a == ItemUseAnimation.TRIDENT) return "Ready to throw";
		return null;
	}

	/** The things it can do with its hands now: null (just holding) and each hand that holds something to use. */
	static List<InteractionHand> acts(Mannequin m) {
		List<InteractionHand> out = new ArrayList<>();
		out.add(null);
		for (InteractionHand h : InteractionHand.values()) if (act(m.getItemInHand(h)) != null) out.add(h);
		return out;
	}

	static @Nullable InteractionHand hand(Mannequin m) {
		return switch (kept(m).hand()) {
			case "main" -> InteractionHand.MAIN_HAND;
			case "off" -> InteractionHand.OFF_HAND;
			default -> null;
		};
	}

	static String handLabel(Mannequin m) {
		InteractionHand h = hand(m);
		String a = h == null ? null : act(m.getItemInHand(h));
		if (a != null) return a;
		ItemStack main = m.getItemInHand(InteractionHand.MAIN_HAND), off = m.getItemInHand(InteractionHand.OFF_HAND);
		if (main.isEmpty() && off.isEmpty()) return "Empty-handed";
		return "Just holding";
	}

	/** Its hand {@code h} uses what it holds (null: it just holds things). */
	static void setHand(Mannequin m, @Nullable InteractionHand h) {
		track(m);
		if (m.isUsingItem()) m.stopUsingItem();
		keep(m, kept(m).withHand(h == null ? "" : h == InteractionHand.MAIN_HAND ? "main" : "off"));
		if (h != null) keepUsing(m);
	}

	/**
	 * Keeps its hand using what it holds. Minecraft stops after the thing's use time (a minute for a spyglass, seven
	 * seconds for a horn), so it starts again before that; the stop goes out first in the same moment, so nobody sees it.
	 */
	private static void keepUsing(Mannequin m) {
		InteractionHand h = hand(m);
		if (h == null) return;
		if (act(m.getItemInHand(h)) == null) {
			keep(m, kept(m).withHand(""));
			if (m.isUsingItem()) m.stopUsingItem();
			return;
		}
		if (m.isUsingItem() && m.getUsedItemHand() == h && m.getUseItemRemainingTicks() > 60) return;
		if (m.isUsingItem()) {
			m.stopUsingItem();
			var dirty = m.getEntityData().packDirty();
			if (dirty != null && !dirty.isEmpty()) {
				var packet = new ClientboundSetEntityDataPacket(m.getId(), dirty);
				for (ServerPlayer p : ((ServerLevel) m.level()).players()) if (p.distanceToSqr(m) < 160 * 160) p.connection.send(packet);
			}
		}
		m.startUsingItem(h);
	}

	// ---------------------------------------------------------------- loaded ones, kept up

	private static final Set<Mannequin> LOADED = new HashSet<>();
	private static final Set<ArmorStand> SEATS = new HashSet<>();

	/** Kept up from now on (one placed just now was loaded before it was one of ours). */
	static void track(Mannequin m) {
		if (!m.isRemoved()) LOADED.add(m);
	}

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (e instanceof Mannequin m && Showcase.ours(m)) {
				LOADED.add(m);
				look(m);
			} else if (e instanceof ArmorStand a && isSeat(a)) {
				SEATS.add(a);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
			if (e instanceof Mannequin m) LOADED.remove(m);
			else if (e instanceof ArmorStand a) SEATS.remove(a);
		});
		// Someone sees it anew: its head turned for them too, and leaning in (after Minecraft showed it them)
		net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register((e, player) -> {
			if (e instanceof Mannequin m) {
				if (kept(m).head() != 0) HEADS.add(m);
				if (leaning(m)) AGAIN.add(m);
			} else if (isSeat(e) && e.getFirstPassenger() instanceof Mannequin m && leaning(m)) {
				AGAIN.add(m);
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			sendHeads();
			leanAgain();
		});
	}

	/** Every second: hands kept using, empty seats gone, a seat or bed that went away (it stands up where it stood). */
	static void tick(MinecraftServer server) {
		for (var it = SEATS.iterator(); it.hasNext();) {
			ArmorStand a = it.next();
			if (a.isRemoved()) {
				it.remove();
				continue;
			}
			if (a.getPassengers().isEmpty()) {
				a.discard();
				it.remove();
			}
		}
		for (var it = LOADED.iterator(); it.hasNext();) {
			Mannequin m = it.next();
			if (m.isRemoved()) {
				it.remove();
				continue;
			}
			Kept k = kept(m);
			if (k.spot().isPresent()) {
				boolean lost = !seated(m) && !m.isSleeping();
				// what it sat on (a stair, a slab, the ground) is gone
				if (seated(m) && m.level().getBlockState(BlockPos.containing(m.getVehicle().position().subtract(0, 0.25, 0))).isAir()) lost = true;
				if (lost) {
					leave(m);
					m.setNoGravity(false);
					m.setPos(k.spot().get());
					m.setPose(Pose.STANDING);
					m.refreshDimensions();
					keep(m, k.withSpot(null));
					Showcase.show(m);
				}
			}
			if (!k.hand().isEmpty()) keepUsing(m);
		}
	}

	// ---------------------------------------------------------------- for the screenshots

	/** A plain mannequin of ours (no gear) at the middle of {@code at} plus {@code dy}, facing {@code yaw}, wearing skin {@code skin}. */
	private static @Nullable Mannequin person(ServerLevel level, BlockPos at, double dy, float yaw, int skin) {
		Mannequin m = Showcase.summon(level, at.getX() + 0.5, at.getY() + dy, at.getZ() + 0.5, yaw);
		if (m == null) return null;
		m.setAttached(Showcase.EXTRA, List.copyOf(Showcase.extras(m)));
		Showcase.wear(m, skin);
		Showcase.show(m);
		return m;
	}

	/**
	 * For the screenshots, a row facing north from {@code o} (x from +6 down to -7). "stances": standing, sitting on a
	 * stair, leaning in on a stair, sitting on the ground, crouching, lying in a bed, crawling. "hands": six standing ones
	 * (their things are given by commands, then {@link #debugUse}). "heads": looking ahead, up, down, left, right.
	 */
	static void debugScene(ServerLevel level, BlockPos o, String scene) {
		var oak = net.minecraft.world.level.block.Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
		switch (scene) {
			case "stances" -> {
				level.setBlock(o.offset(4, 0, 0), oak, 3);
				level.setBlock(o.offset(2, 0, 0), net.minecraft.world.level.block.Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH), 3);
				var bed = nl.libreria.core.Ui.block("red_bed").setValue(BedBlock.FACING, Direction.WEST);
				level.setBlock(o.offset(-5, 0, 0), bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
				level.setBlock(o.offset(-4, 0, 0), bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
				String[] st = {"stand", "sit", "lean", "sit", "crouch", "lie", "crawl"};
				int[] dx = {6, 4, 2, 0, -2, -4, -7}, dy = {0, 1, 1, 0, 0, 0, 0};
				for (int i = 0; i < st.length; i++) {
					Mannequin m = person(level, o.offset(dx[i], 0, 0), dy[i], 180, 101 + i * 173);
					if (m != null) set(m, Stance.valueOf(st[i].toUpperCase(Locale.ROOT)));
				}
			}
			case "hands" -> {
				int[] dx = {5, 3, 1, -1, -3, -5};
				for (int i = 0; i < dx.length; i++) person(level, o.offset(dx[i], 0, 0), 0, 180, 211 + i * 157);
			}
			case "heads" -> {
				int[] dx = {4, 2, 0, -2, -4};
				for (int i = 0; i < dx.length; i++) {
					Mannequin m = person(level, o.offset(dx[i], 0, 0), 0, 180, 307 + i * 131);
					Look l = Look.values()[i];
					if (m != null) setLook(m, l.yaw, l.pitch);
				}
			}
			default -> {
			}
		}
	}

	/** For the screenshots: every mannequin of ours near {@code o} uses what it holds (the main hand first). */
	static void debugUse(ServerLevel level, BlockPos o) {
		for (Mannequin m : level.getEntitiesOfClass(Mannequin.class, new net.minecraft.world.phys.AABB(o).inflate(9, 3, 3))) {
			if (!Showcase.ours(m)) continue;
			var acts = acts(m);
			if (acts.size() > 1) setHand(m, acts.get(1));
			Showcase.show(m);
		}
	}

	/** For the screenshots: the mannequin of ours nearest {@code at}. */
	static @Nullable Mannequin debugNearest(ServerLevel level, BlockPos at) {
		Mannequin best = null;
		double d = 9;
		for (Mannequin m : level.getEntitiesOfClass(Mannequin.class, new net.minecraft.world.phys.AABB(at).inflate(2))) {
			if (Showcase.ours(m) && m.position().distanceToSqr(Vec3.atBottomCenterOf(at)) < d) {
				d = m.position().distanceToSqr(Vec3.atBottomCenterOf(at));
				best = m;
			}
		}
		return best;
	}
}
