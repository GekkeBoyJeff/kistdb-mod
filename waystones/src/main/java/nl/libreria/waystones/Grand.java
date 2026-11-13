package nl.libreria.waystones;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Libreria;
import nl.libreria.core.Notify;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * Making a grand waystone, and taking one down. Like the Wither, it's built: a waystone stands in the middle of a
 * 5 × 5 sculk floor (its altar in front says which way the shrine faces: the opening and the steps are there); a
 * Waystone Heart (a nether star and two diamonds) used on it is the last piece. The shrine rises out of the ground,
 * 5 × 5 and nine high over the floor: the floor becomes its foundation (nobody can break it). Leaving from a grand
 * waystone is free. Taken down (from its window, asked first), the waystone and the nether star come back; the
 * diamonds and the sculk don't.
 *
 * <p>Places in it are counted in the shrine's own way round: {@code i} across from left to right as you face it,
 * {@code j} from the front row (0) to the back, {@code k} the level, -1 the floor, 0 the waystone's own.
 */
final class Grand {
	/** The grand waystones raised just now: their drawing rises out of the ground first. */
	static final Set<String> RISING = new HashSet<>();
	/** Where the drawing goes, from the middle of the waystone's own block (blocks): the shrine (4x), the heart. */
	static final float STATIC_Y = 5.5f;
	static final float HEART_Y = 3.125f;
	/** How high it reaches above the floor. */
	static final int LEVELS = 9;

	private Grand() {
	}

	/** Where the place {@code i, j, k} of the shrine round {@code middle} (facing {@code front}) is. */
	static BlockPos at(BlockPos middle, Direction front, int i, int j, int k) {
		return middle.relative(front.getClockWise(), i - 2).relative(front.getOpposite(), j - 2).above(k);
	}

	/**
	 * Whether the shrine has stone in that place (else air, or on the plinth a place to walk): the floor and the plinth's
	 * pillars, balustrade (the opening in front) and pedestal; the balustrade two high so you go in by the opening; the
	 * pillars up to the ring over the arches; then the roof.
	 */
	static boolean solid(int i, int j, int k) {
		boolean corner = (i == 0 || i == 4) && (j == 0 || j == 4);
		boolean edge = i == 0 || i == 4 || j == 0 || j == 4;
		boolean middle = i == 2 && j == 2;
		boolean opening = i == 2 && j == 0;
		return switch (k) {
			case -1 -> true;
			case 0, 1 -> corner || middle || edge && !opening;
			case 2, 3 -> corner;
			case 4 -> edge;
			case 5 -> true;
			case 6 -> i >= 1 && i <= 3 && j >= 1 && j <= 3;
			case 7 -> middle;
			default -> false;
		};
	}

	/** {@link #solid} for a block of the shrine, from its place in the world ({@link GrandBlock#X}, {@link GrandBlock#Z}). */
	static boolean solidAt(Direction front, int x, int z, int k) {
		int dx = x - 2, dz = z - 2;
		Direction right = front.getClockWise(), back = front.getOpposite();
		int i = 2 + dx * right.getStepX() + dz * right.getStepZ();
		int j = 2 + dx * back.getStepX() + dz * back.getStepZ();
		return solid(i, j, k);
	}

	/** Every place of the shrine (the floor up), as {i, j, k}. */
	static List<int[]> places() {
		List<int[]> out = new ArrayList<>();
		for (int k = -1; k < LEVELS; k++) for (int j = 0; j < 5; j++) for (int i = 0; i < 5; i++) out.add(new int[] {i, j, k});
		return out;
	}

	// ---------------------------------------------------------------- making one

	/**
	 * The heart used on the waystone at {@code lower}: it becomes grand when it stands in the middle of a 5 × 5 sculk floor
	 * with room above, and the player may (its owner, who they trust, an operator). The heart is used up then.
	 */
	static void raise(ServerPlayer p, ServerLevel level, BlockPos lower, ItemStack heart) {
		if (!WaystonesMod.MODULE.on()) return;
		BlockState st = level.getBlockState(lower);
		if (!st.is(WaystonesMod.WAYSTONE)) return;
		Stones.Stone s = Stones.ensure(level, lower, st);
		if (s.grand) return;
		if (!Stones.mayBreak(p, s)) {
			WaystoneBlock.refuse(p, s);
			return;
		}
		Direction front = st.getValue(WaystoneBlock.FACING);
		List<BlockPos> floor = missingFloor(level, lower, front);
		if (!floor.isEmpty()) {
			Notify.info(p, WaystonesMod.HEART, "The sculk floor isn't done", floor.size() + (floor.size() == 1 ? " block" : " blocks")
					+ " of the 5 × 5 round its feet " + (floor.size() == 1 ? "isn't" : "aren't") + " sculk yet (shown).");
			show(level, floor, true);
			return;
		}
		List<BlockPos> blocked = inTheWay(level, lower, front);
		if (!blocked.isEmpty()) {
			Notify.info(p, WaystonesMod.HEART, "No room for it to rise", "It's 5 × 5 and nine blocks high over the floor: "
					+ blocked.size() + (blocked.size() == 1 ? " block is" : " blocks are") + " in the way (shown).");
			show(level, blocked, false);
			return;
		}
		if (!p.isCreative()) heart.shrink(1);
		build(level, lower, front, s, true);
		Notify.card(p, new ItemStack(WaystonesMod.HEART), WaystoneCanvas.ACCENT, Ui.text("A grand waystone: ", ChatFormatting.WHITE)
				.append(Ui.text(s.name, WaystoneCanvas.ACCENT)), Ui.text("Leaving from here is free", ChatFormatting.GRAY));
		Libreria.LOG.info("{} raised the grand waystone {} at {}", p.getName().getString(), s.name, lower.toShortString());
	}

	/** The floor's blocks that aren't sculk yet (under the waystone and its altar anything will do). */
	static List<BlockPos> missingFloor(ServerLevel level, BlockPos lower, Direction front) {
		List<BlockPos> out = new ArrayList<>();
		for (int j = 0; j < 5; j++) {
			for (int i = 0; i < 5; i++) {
				if (i == 2 && (j == 2 || j == 1)) continue;
				BlockPos b = at(lower, front, i, j, -1);
				if (!level.getBlockState(b).is(Blocks.SCULK)) out.add(b);
			}
		}
		return out;
	}

	/** What's in the way above the floor (the waystone and its altar make way; grass and the like too). */
	static List<BlockPos> inTheWay(ServerLevel level, BlockPos lower, Direction front) {
		List<BlockPos> out = new ArrayList<>();
		for (int[] c : places()) {
			if (c[2] < 0) continue;
			BlockPos b = at(lower, front, c[0], c[1], c[2]);
			BlockState bs = level.getBlockState(b);
			if (c[0] == 2 && c[1] == 2 && c[2] <= 2 && bs.is(WaystonesMod.WAYSTONE)) continue;
			if (c[0] == 2 && c[1] == 1 && c[2] == 0 && bs.is(WaystonesMod.ALTAR)) continue;
			if (!bs.canBeReplaced() || !bs.getFluidState().isEmpty() && !bs.isAir()) out.add(b);
		}
		return out;
	}

	/** Little lights over the places that aren't right yet. */
	private static void show(ServerLevel level, List<BlockPos> where, boolean onTop) {
		for (BlockPos b : where) {
			level.sendParticles(ParticleTypes.WAX_ON, b.getX() + 0.5, b.getY() + (onTop ? 1.1 : 0.5), b.getZ() + 0.5, 5, 0.25, 0.1, 0.25, 0);
		}
	}

	/**
	 * Puts the shrine round the waystone at {@code lower}: its blocks (the floor, the plinth, the rest; the waystone and
	 * its altar make way), the waystone written down as grand. {@code rise}: its drawing comes up out of the ground.
	 */
	static void build(ServerLevel level, BlockPos lower, Direction front, Stones.Stone s, boolean rise) {
		s.grand = true;
		Stones.STORE.markDirty();
		if (rise) RISING.add(s.key());
		else RISING.remove(s.key());
		// Who stands where it rises steps out to the front
		AABB box = new AABB(lower.getX() - 2, lower.getY(), lower.getZ() - 2, lower.getX() + 3, lower.getY() + LEVELS, lower.getZ() + 3);
		for (ServerPlayer q : level.players()) {
			if (!q.getBoundingBox().intersects(box)) continue;
			Vec3 out = outside(level, lower, front);
			q.teleportTo(out.x, out.y, out.z);
		}
		BlockState base = WaystonesMod.GRAND.defaultBlockState().setValue(GrandBlock.FACING, front).setValue(GrandBlock.DARK, s.dark);
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		for (int[] c : places()) {
			BlockPos b = at(lower, front, c[0], c[1], c[2]);
			if (b.equals(lower)) continue;
			BlockState now = level.getBlockState(b);
			boolean walk = c[2] == 0;
			if (solid(c[0], c[1], c[2]) || walk) {
				level.setBlock(b, base.setValue(GrandBlock.X, b.getX() - lower.getX() + 2).setValue(GrandBlock.Y, c[2] + 1)
						.setValue(GrandBlock.Z, b.getZ() - lower.getZ() + 2), flags);
			} else if (!now.isAir()) {
				level.setBlock(b, Blocks.AIR.defaultBlockState(), flags);
			}
		}
		// The waystone's own block last: it carries the drawing (which knows by now it's rising)
		level.setBlock(lower, base, Block.UPDATE_ALL);
		if (!rise) return;
		double x = lower.getX() + 0.5, z = lower.getZ() + 0.5;
		level.playSound(null, x, lower.getY(), z, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.8f, 1.4f);
	}

	/** Where someone standing in its way goes: in front, outside, on the ground. */
	static Vec3 outside(ServerLevel level, BlockPos lower, Direction front) {
		BlockPos t = lower.relative(front, 4);
		for (int dy = -1; dy <= 3; dy++) {
			BlockPos feet = t.above(dy);
			if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
					&& !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) {
				return Vec3.atBottomCenterOf(feet);
			}
		}
		return Vec3.atBottomCenterOf(t.above());
	}

	// ---------------------------------------------------------------- taking one down

	/** May take it down from its window (and get the waystone and the nether star): its owner, or an operator. */
	static boolean mayTakeDown(ServerPlayer p, Stones.Stone s) {
		return s.grand && (Stones.owns(p, s) || Ui.isOp(p));
	}

	/**
	 * Takes the shrine down, floor and all, and the waystone off every list. {@code to}: who gets the waystone and the
	 * nether star back (null: nothing comes back, as an operator removing it).
	 */
	static void takeDown(Stones.Stone s, @Nullable ServerLevel level, @Nullable ServerPlayer to) {
		ItemStack stone = level != null ? WaystoneBlock.item(level, s.pos()) : new ItemStack(s.dark ? WaystonesMod.DARK_WAYSTONE_ITEM : WaystonesMod.WAYSTONE_ITEM);
		if (level != null) {
			BlockPos lower = s.pos();
			Direction front = s.front();
			// off every list first, with why (taking its blocks away would say "broken")
			Stones.removed(level, lower, "taken down by " + (to != null ? to.getName().getString() : "an operator"));
			int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
			for (int[] c : places()) {
				BlockPos b = at(lower, front, c[0], c[1], c[2]);
				if (b.equals(lower) || !level.getBlockState(b).is(WaystonesMod.GRAND)) continue;
				level.setBlock(b, Blocks.AIR.defaultBlockState(), flags);
			}
			if (level.getBlockState(lower).is(WaystonesMod.GRAND)) level.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			double x = lower.getX() + 0.5, z = lower.getZ() + 0.5;
			level.sendParticles(ParticleTypes.CLOUD, x, lower.getY() + 2, z, 60, 2, 2.5, 2, 0.02);
			level.playSound(null, x, lower.getY() + 1, z, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 2f, 0.6f);
		}
		s.grand = false;
		RISING.remove(s.key());
		if (to != null) {
			for (ItemStack back : new ItemStack[] {stone, new ItemStack(Items.NETHER_STAR)}) {
				if (!to.getInventory().add(back) && !back.isEmpty()) {
					to.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(to.level(), to.getX(), to.getY(), to.getZ(), back));
				}
			}
			Notify.done(to, Items.NETHER_STAR, "Taken down: " + s.name, "The waystone and the nether star are back in your inventory.");
		}
	}
}
