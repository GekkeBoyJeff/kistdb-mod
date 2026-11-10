package nl.libreria.waystones;

import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** The way in for the mixin (which lives in another package). */
public final class VillageHook {
	private VillageHook() {
	}

	public static void placed(StructureStart start, WorldGenLevel level, BoundingBox box) {
		try {
			VillageWaystones.placed(start, level, box);
		} catch (RuntimeException e) {
			nl.libreria.core.Libreria.LOG.warn("Waystones: no waystone for a village at {}", start.getChunkPos(), e);
		}
	}
}
