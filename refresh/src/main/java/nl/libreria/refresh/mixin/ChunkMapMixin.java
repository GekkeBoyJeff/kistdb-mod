package nl.libreria.refresh.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import nl.libreria.refresh.Layout;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A world that was refreshed places its structures anew: see {@link Layout}. */
@Mixin(ChunkMap.class)
abstract class ChunkMapMixin {
	@Shadow
	@Final
	private ServerLevel level;
	@Shadow
	@Final
	private ChunkGeneratorStructureState chunkGeneratorState;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void libreria$layout(CallbackInfo ci) {
		Layout.apply(this.level.dimension(), this.chunkGeneratorState);
	}
}
