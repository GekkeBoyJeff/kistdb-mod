package nl.libreria.waystones;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import nl.libreria.core.WorldStore;
import org.jetbrains.annotations.Nullable;

/**
 * A waystone on its own square in every new village. While the world is made, the chunk with the village's bell notes
 * where the bell is and what kind of village it is. Once that part of the village is fully there (its chunks
 * loaded, every house standing), the square is laid on the best open, nearly flat spot by the bell, in the village's
 * own materials, and the waystone goes on it (light or dark stone, the village's colour). Without room for a square,
 * the stone stands on its own by the bell. Villages that were there already aren't touched.
 */
final class VillageWaystones {
	/** A new village's bell, waiting for its square. */
	public static final class Pending {
		public String dim = "minecraft:overworld";
		public int x;
		public int y;
		public int z;
		public String style = "plains";
	}

	public static final class Data {
		public List<Pending> villages = new ArrayList<>();
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/waystone_villages.json", Data.class, Data::new);
	/** Noted while the world is made (on its own threads), taken in on the server's. */
	private static final ConcurrentLinkedQueue<Pending> NEW = new ConcurrentLinkedQueue<>();
	/** How far around the bell the chunks must be there (the square looks up to 15 blocks away). */
	private static final int AROUND = 2;
	/** Where the last village's waystone went (for the tests). */
	static volatile @Nullable BlockPos last;

	private VillageWaystones() {
	}

	static void register() {
		// The hook is mixin/VillageMixin (after a structure placed its pieces in a chunk)
	}

	/** A structure placed its pieces in one chunk ({@code box}): a village's town centre with its bell is noted. */
	static void placed(StructureStart start, WorldGenLevel level, BoundingBox box) {
		if (!WaystonesMod.MODULE.on() || !WaystonesMod.VILLAGES.get() || start.getPieces().isEmpty()) return;
		var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		if (!structures.wrapAsHolder(start.getStructure()).is(StructureTags.VILLAGE)) return;
		BlockPos bell = bell(level, start.getPieces().getFirst().getBoundingBox(), box);
		if (bell == null) return;
		Identifier id = structures.getKey(start.getStructure());
		Pending p = new Pending();
		p.dim = level.getLevel().dimension().identifier().toString();
		p.x = bell.getX();
		p.y = bell.getY();
		p.z = bell.getZ();
		p.style = id == null ? "plains" : id.getPath();
		NEW.add(p);
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

	/** Every second: new villages written down; those whose part around the bell is there now get their square. */
	static void tick(MinecraftServer server) {
		Data d = STORE.get();
		if (d.villages == null) d.villages = new ArrayList<>();
		boolean changed = false;
		for (Pending p; (p = NEW.poll()) != null; ) {
			d.villages.add(p);
			changed = true;
		}
		for (Pending p : new ArrayList<>(d.villages)) {
			ServerLevel level = Stones.level(server, p.dim);
			if (level != null && !loaded(level, p.x, p.z)) continue;
			d.villages.remove(p);
			changed = true;
			if (level != null) build(level, p);
		}
		if (changed) STORE.markDirty();
	}

	private static boolean loaded(ServerLevel level, int x, int z) {
		int cx = x >> 4, cz = z >> 4;
		for (int dx = -AROUND; dx <= AROUND; dx++) {
			for (int dz = -AROUND; dz <= AROUND; dz++) {
				if (level.getChunkSource().getChunkNow(cx + dx, cz + dz) == null) return false;
			}
		}
		return true;
	}

	/** The square and the waystone for one village (its part around the bell is loaded). */
	static boolean build(ServerLevel level, Pending p) {
		if (!WaystonesMod.MODULE.on() || !WaystonesMod.VILLAGES.get()) return false;
		BlockPos bell = new BlockPos(p.x, p.y, p.z);
		Plaza.Palette pal = Plaza.palette(Plaza.Style.of(p.style));
		Plaza.Site site = Plaza.find(level, bell);
		BlockPos lower;
		Direction front;
		if (site != null) {
			Plaza.build(level, site, pal);
			lower = site.centre();
			front = site.front();
		} else {
			Spot s = spot(level, bell);
			if (s == null) return false;
			lower = s.lower();
			front = s.front();
		}
		WaystoneBlock.put(level, lower, front, true, Block.UPDATE_ALL);
		Stones.Stone stone = Stones.ensure(level, lower, level.getBlockState(lower));
		stone.village = true;
		stone.dark = pal.dark();
		stone.dye = pal.dye().getName();
		Stones.STORE.markDirty();
		last = lower;
		return true;
	}

	private record Spot(BlockPos lower, Direction front) {
	}

	/** No room for a square: the open spot nearest to four or five blocks from the bell for the stone alone. */
	private static @Nullable Spot spot(ServerLevel level, BlockPos bell) {
		Spot best = null;
		double bestScore = Double.MAX_VALUE;
		for (int dx = -7; dx <= 7; dx++) {
			for (int dz = -7; dz <= 7; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 < 9 || d2 > 49) continue;
				Direction f = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
				for (int y = bell.getY() + 1; y >= bell.getY() - 6; y--) {
					BlockPos lower = new BlockPos(bell.getX() + dx, y, bell.getZ() + dz);
					if (!fits(level, lower, f)) continue;
					double score = Math.abs(Math.sqrt(d2) - 4.5) + Math.abs(y - bell.getY()) * 0.15;
					if (score < bestScore) {
						bestScore = score;
						best = new Spot(lower, f);
					}
					break;
				}
			}
		}
		return best;
	}

	private static boolean fits(ServerLevel level, BlockPos lower, Direction f) {
		BlockPos altar = lower.relative(f);
		for (BlockPos q : new BlockPos[] {lower, lower.above(), lower.above(2), altar, altar.above()}) if (!open(level, q)) return false;
		return level.getBlockState(lower.below()).isFaceSturdy(level, lower.below(), Direction.UP)
				&& level.getBlockState(altar.below()).isFaceSturdy(level, altar.below(), Direction.UP) && !level.getBlockState(lower.below()).is(Blocks.BELL);
	}

	private static boolean open(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return s.canBeReplaced() && s.getFluidState().isEmpty();
	}
}
