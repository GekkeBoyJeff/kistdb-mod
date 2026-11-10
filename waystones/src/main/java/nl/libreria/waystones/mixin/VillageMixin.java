package nl.libreria.waystones.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import nl.libreria.waystones.VillageHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After a structure placed its pieces in a chunk: a new village gets its waystone. */
@Mixin(StructureStart.class)
abstract class VillageMixin {
	@Inject(method = "placeInChunk", at = @At("RETURN"))
	private void libreria$waystone(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox box,
			ChunkPos chunk, CallbackInfo ci) {
		VillageHook.placed((StructureStart) (Object) this, level, box);
	}
}
