package nl.libreria.waystones;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * The square a village's waystone stands on, in the village's own materials: a paved square with a border, a raised
 * centre for the stone and its altar, lanterns on posts at the corners, benches along the sides and plants at the
 * back. Seven by seven where there's room, five by five where there's less. It goes on open, nearly flat ground near
 * the bell (never over a house, a field or water): low spots get a foundation, plants and snow make way.
 */
final class Plaza {
	enum Style {
		PLAINS, DESERT, SAVANNA, SNOWY, TAIGA;

		static Style of(String structure) {
			if (structure.contains("desert")) return DESERT;
			if (structure.contains("savanna")) return SAVANNA;
			if (structure.contains("snowy")) return SNOWY;
			if (structure.contains("taiga")) return TAIGA;
			return PLAINS;
		}
	}

	/** One village type's materials, and its waystone (light or dark stone, the colour of its runes). */
	record Palette(BlockState edge, BlockState pave, BlockState pave2, BlockState centre, BlockState centre2, BlockState post,
			BlockState bench, BlockState plant, BlockState foundation, boolean dark, DyeColor dye) {
	}

	static Palette palette(Style s) {
		return switch (s) {
			case PLAINS -> new Palette(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
					Blocks.ANDESITE.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(),
					Blocks.OAK_FENCE.defaultBlockState(), Blocks.OAK_STAIRS.defaultBlockState(), Blocks.POTTED_POPPY.defaultBlockState(),
					Blocks.COBBLESTONE.defaultBlockState(), false, DyeColor.PURPLE);
			case DESERT -> new Palette(Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
					Blocks.SANDSTONE.defaultBlockState(), nl.libreria.core.Ui.block("orange_terracotta"), Blocks.CHISELED_SANDSTONE.defaultBlockState(),
					Blocks.SANDSTONE_WALL.defaultBlockState(), Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState(), Blocks.POTTED_CACTUS.defaultBlockState(),
					Blocks.SANDSTONE.defaultBlockState(), false, DyeColor.CYAN);
			case SAVANNA -> new Palette(Blocks.ACACIA_PLANKS.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(),
					Blocks.COARSE_DIRT.defaultBlockState(), nl.libreria.core.Ui.block("orange_terracotta"), nl.libreria.core.Ui.block("yellow_terracotta"),
					Blocks.ACACIA_FENCE.defaultBlockState(), Blocks.ACACIA_STAIRS.defaultBlockState(), Blocks.POTTED_ACACIA_SAPLING.defaultBlockState(),
					Blocks.COARSE_DIRT.defaultBlockState(), false, DyeColor.ORANGE);
			case SNOWY -> new Palette(Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SNOW_BLOCK.defaultBlockState(),
					Blocks.POLISHED_DIORITE.defaultBlockState(), Blocks.PACKED_ICE.defaultBlockState(), Blocks.POLISHED_DIORITE.defaultBlockState(),
					Blocks.SPRUCE_FENCE.defaultBlockState(), Blocks.SPRUCE_STAIRS.defaultBlockState(), Blocks.POTTED_SPRUCE_SAPLING.defaultBlockState(),
					Blocks.STONE.defaultBlockState(), true, DyeColor.LIGHT_BLUE);
			case TAIGA -> new Palette(Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
					Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
					Blocks.SPRUCE_FENCE.defaultBlockState(), Blocks.SPRUCE_STAIRS.defaultBlockState(), Blocks.POTTED_FERN.defaultBlockState(),
					Blocks.COBBLESTONE.defaultBlockState(), true, DyeColor.LIME);
		};
	}

	private Plaza() {
	}

	/** A spot for a plaza: the waystone's lower block, the altar's side (towards the bell) and the size (3: 7×7, 2: 5×5). */
	record Site(BlockPos centre, Direction front, int radius) {
	}

	// ---------------------------------------------------------------- the place

	/** Where a cell of the square is: {@code a} to the right, {@code b} to the front (the altar's side). */
	static BlockPos at(BlockPos centre, Direction front, int a, int b) {
		return centre.relative(front.getClockWise(), a).relative(front, b);
	}

	/** Ground the square may be laid on, and what may be cleared above it (nothing anyone built). */
	static boolean natural(BlockState s) {
		if (s.isAir() || s.canBeReplaced()) return s.getFluidState().isEmpty();
		return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL)
				|| s.is(Blocks.DIRT_PATH) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE)
				|| s.is(Blocks.TERRACOTTA) || s.is(Blocks.CLAY) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.MUD);
	}

	private static boolean open(BlockState s) {
		return (s.isAir() || s.canBeReplaced()) && s.getFluidState().isEmpty();
	}

	/**
	 * The best spot near {@code bell}: open, nearly flat ground 5–12 blocks away, nothing built on it, no water; the
	 * bigger square first. Null when there is none (then the stone stands on its own).
	 */
	static @Nullable Site find(LevelAccessor level, BlockPos bell) {
		Site best = null;
		double bestScore = Double.MAX_VALUE;
		for (int radius = 3; radius >= 2 && best == null; radius--) {
			for (int dx = -12; dx <= 12; dx++) {
				for (int dz = -12; dz <= 12; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d < radius + 3 || d > 12) continue;
					Direction front = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
					int x = bell.getX() + dx, z = bell.getZ() + dz;
					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					double score = fits(level, new BlockPos(x, y, z), front, radius);
					if (score < 0) continue;
					score += Math.abs(d - (radius + 5));
					if (score < bestScore) {
						bestScore = score;
						best = new Site(new BlockPos(x, y, z), front, radius);
					}
				}
			}
		}
		return best;
	}

	/** How well a square fits with its centre at {@code centre} (lower is better), or -1 when it doesn't. */
	static double fits(LevelAccessor level, BlockPos centre, Direction front, int radius) {
		int y0 = centre.getY();
		double rough = 0;
		int paths = 0;
		for (int a = -radius; a <= radius; a++) {
			for (int b = -radius; b <= radius; b++) {
				BlockPos c = at(centre, front, a, b);
				int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
				if (Math.abs(h - y0) > 2) return -1;
				rough += Math.abs(h - y0);
				// The ground under the square: something natural (a foundation fills a dip of up to two)
				BlockState ground = level.getBlockState(c.atY(Math.min(h, y0) - 1));
				if (!natural(ground) || !ground.getFluidState().isEmpty()) return -1;
				if (ground.is(Blocks.DIRT_PATH)) paths++;
				// Room above it: four blocks (the stone needs three), nothing built
				for (int y = Math.min(h, y0); y <= y0 + 4; y++) {
					BlockState s = level.getBlockState(c.atY(y));
					if (y < y0 ? !natural(s) : !open(s) && !(y < h && natural(s))) return -1;
					if (!s.getFluidState().isEmpty()) return -1;
				}
			}
		}
		// Flat and touching the village's paths is best
		return rough + (6 - Math.min(paths, 6)) * 0.5;
	}

	// ---------------------------------------------------------------- building it

	private static int hash(BlockPos p) {
		int h = p.getX() * 73428767 ^ p.getZ() * 912931;
		return (h ^ (h >>> 13)) & 0x7FFFFFFF;
	}

	private static final int FLAGS = Block.UPDATE_CLIENTS;

	/** Lays the square for the waystone at {@code site} (the stone itself and its altar go on it after). */
	static void build(LevelAccessor level, Site site, Palette p) {
		BlockPos centre = site.centre();
		Direction front = site.front(), right = front.getClockWise();
		int r = site.radius(), y0 = centre.getY();
		for (int a = -r; a <= r; a++) {
			for (int b = -r; b <= r; b++) {
				BlockPos c = at(centre, front, a, b);
				int ring = Math.max(Math.abs(a), Math.abs(b));
				// The ground: border, paving (two kinds, mixed), the raised centre
				BlockState ground;
				if (ring == r && !(b == r && Math.abs(a) <= 1)) ground = p.edge();
				else if (ring <= 1) ground = (a == 0 && b == 0) || (a == 0 && b == 1) ? p.centre2() : p.centre();
				else ground = hash(c) % 3 == 0 ? p.pave2() : p.pave();
				level.setBlock(c.atY(y0 - 1), ground, FLAGS);
				// A foundation down to the ground (at most four)
				for (int y = y0 - 2; y >= y0 - 5; y--) {
					BlockState s = level.getBlockState(c.atY(y));
					if (!open(s) && s.getFluidState().isEmpty()) break;
					level.setBlock(c.atY(y), p.foundation(), FLAGS);
				}
				// Room above
				for (int y = y0; y <= y0 + 4; y++) {
					if (!level.getBlockState(c.atY(y)).isAir()) level.setBlock(c.atY(y), Blocks.AIR.defaultBlockState(), FLAGS);
				}
			}
		}
		// Lanterns on posts at the corners
		for (int sa : new int[] {-r, r}) {
			for (int sb : new int[] {-r, r}) {
				BlockPos c = at(centre, front, sa, sb);
				level.setBlock(c.atY(y0), p.post(), FLAGS);
				level.setBlock(c.atY(y0 + 1), p.post(), FLAGS);
				level.setBlock(c.atY(y0 + 2), Blocks.LANTERN.defaultBlockState(), FLAGS);
			}
		}
		if (r >= 3) {
			// Benches along the sides, their backs outward
			for (int b = -1; b <= 1; b++) {
				level.setBlock(at(centre, front, -r, b).atY(y0), p.bench().setValue(StairBlock.FACING, right.getOpposite()), FLAGS);
				level.setBlock(at(centre, front, r, b).atY(y0), p.bench().setValue(StairBlock.FACING, right), FLAGS);
			}
			// Plants at the back
			level.setBlock(at(centre, front, -2, -r).atY(y0), p.plant(), FLAGS);
			level.setBlock(at(centre, front, 2, -r).atY(y0), p.plant(), FLAGS);
		}
	}
}
