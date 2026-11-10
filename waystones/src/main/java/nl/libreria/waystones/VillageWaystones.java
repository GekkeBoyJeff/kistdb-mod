package nl.libreria.waystones;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

/**
 * A waystone in every new village: while the world is made, as soon as the chunk with the village's bell is built,
 * a waystone goes on the open ground a few blocks from the bell, its altar facing the bell. Only villages made from
 * now on: one that's there already isn't touched. The waystone is written down (with the village's name) once its
 * chunk is loaded ({@link WaystoneBlock}).
 */
final class VillageWaystones {
	private static final int NEAREST = 3, FARTHEST = 7;
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	/** Where the last village's waystone went (for the tests). */
	static volatile @Nullable BlockPos last;

	private VillageWaystones() {
	}

	static void register() {
		// The hook is mixin/VillageMixin (after a structure placed its pieces in a chunk)
	}

	/** A structure placed its pieces in one chunk ({@code box}): a village's town centre with its bell gets a waystone. */
	static void placed(StructureStart start, WorldGenLevel level, BoundingBox box) {
		if (!WaystonesMod.MODULE.on() || !WaystonesMod.VILLAGES.get() || start.getPieces().isEmpty()) return;
		if (!level.registryAccess().lookupOrThrow(Registries.STRUCTURE).wrapAsHolder(start.getStructure()).is(StructureTags.VILLAGE)) return;
		BlockPos bell = bell(level, start.getPieces().getFirst().getBoundingBox(), box);
		if (bell == null) return;
		place(level, bell, box);
	}

	/** The bell of the town centre, if it is in this chunk. */
	static @Nullable BlockPos bell(WorldGenLevel level, BoundingBox centre, BoundingBox box) {
		int x0 = Math.max(centre.minX(), box.minX()), x1 = Math.min(centre.maxX(), box.maxX());
		int z0 = Math.max(centre.minZ(), box.minZ()), z1 = Math.min(centre.maxZ(), box.maxZ());
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = centre.minY(); y <= centre.maxY(); y++) {
					if (level.getBlockState(m.set(x, y, z)).is(Blocks.BELL)) return m.immutable();
				}
			}
		}
		return null;
	}

	/** The open spot nearest to four or five blocks from the bell (in this chunk); the waystone there. */
	static boolean place(WorldGenLevel level, BlockPos bell, BoundingBox box) {
		BlockPos best = null;
		Direction bestFacing = Direction.SOUTH;
		double bestScore = Double.MAX_VALUE;
		for (int dx = -FARTHEST; dx <= FARTHEST; dx++) {
			for (int dz = -FARTHEST; dz <= FARTHEST; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 < NEAREST * NEAREST || d2 > FARTHEST * FARTHEST) continue;
				// The altar towards the bell
				Direction f = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
				for (int y = bell.getY() + 1; y >= bell.getY() - 6; y--) {
					BlockPos lower = new BlockPos(bell.getX() + dx, y, bell.getZ() + dz);
					if (!fits(level, lower, f, box)) continue;
					double score = Math.abs(Math.sqrt(d2) - 4.5) + Math.abs(y - bell.getY()) * 0.15 + (open(level, lower.relative(f, 2)) ? 0 : 2);
					if (score < bestScore) {
						bestScore = score;
						best = lower;
						bestFacing = f;
					}
					break;
				}
			}
		}
		if (best == null) return false;
		BlockState stone = WaystonesMod.WAYSTONE.defaultBlockState().setValue(WaystoneBlock.FACING, bestFacing).setValue(WaystoneBlock.VILLAGE, true);
		level.setBlock(best, stone.setValue(WaystoneBlock.HALF, DoubleBlockHalf.LOWER), FLAGS);
		level.setBlock(best.above(), stone.setValue(WaystoneBlock.HALF, DoubleBlockHalf.UPPER), FLAGS);
		level.setBlock(best.relative(bestFacing), WaystonesMod.ALTAR.defaultBlockState().setValue(AltarBlock.FACING, bestFacing), FLAGS);
		last = best;
		// The top of a tall plant that stood where the altar is now
		BlockPos above = best.relative(bestFacing).above();
		if (!level.getBlockState(above).isAir()) level.setBlock(above, Blocks.AIR.defaultBlockState(), FLAGS);
		return true;
	}

	/** The stone (two blocks) and its altar fit here: open, on solid ground, in this chunk. */
	private static boolean fits(WorldGenLevel level, BlockPos lower, Direction f, BoundingBox box) {
		BlockPos upper = lower.above(), altar = lower.relative(f);
		if (!box.isInside(lower) || !box.isInside(upper) || !box.isInside(altar) || !box.isInside(lower.below()) || !box.isInside(altar.below())) {
			return false;
		}
		if (!open(level, lower) || !open(level, upper) || !open(level, altar) || !open(level, altar.above())) return false;
		return level.getBlockState(lower.below()).isFaceSturdy(level, lower.below(), Direction.UP)
				&& level.getBlockState(altar.below()).isFaceSturdy(level, altar.below(), Direction.UP)
				&& !level.getBlockState(lower.below()).is(Blocks.BELL);
	}

	/** Air or a plant you walk through (grass, flowers, snow layer), not water. */
	private static boolean open(WorldGenLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return s.canBeReplaced() && s.getFluidState().isEmpty();
	}
}
