package nl.libreria.refresh.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import nl.libreria.refresh.Layout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A new structure that would reach into land kept from before the refresh isn't made at all: else it would stop at the
 * edge of what was kept (half a village beside someone's house).
 */
@Mixin(ChunkGenerator.class)
abstract class StructureSkipMixin {
	@WrapOperation(method = "tryGenerateStructure", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/StructureStart;isValid()Z"),
			require = 0)
	private boolean libreria$skip(StructureStart start, Operation<Boolean> original, @Local(argsOnly = true) ResourceKey<Level> dimension) {
		boolean valid = original.call(start);
		return valid && !Layout.overlapsKept(dimension, start.getBoundingBox());
	}
}
