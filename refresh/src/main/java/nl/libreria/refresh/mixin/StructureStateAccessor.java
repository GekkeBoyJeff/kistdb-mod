package nl.libreria.refresh.mixin;

import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The seed structures are placed with (strongholds use a seed of their own, which stays). */
@Mixin(ChunkGeneratorStructureState.class)
public interface StructureStateAccessor {
	@Accessor("levelSeed")
	@Mutable
	void libreria$setLevelSeed(long seed);
}
