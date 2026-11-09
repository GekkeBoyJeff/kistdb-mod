package nl.libreria.claims.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import nl.libreria.claims.Grief;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fire doesn't burn claimed blocks away, and doesn't spread into a claim. */
@Mixin(FireBlock.class)
public abstract class FireMixin {
	@Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
	private void libreria$noBurning(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
		if (level instanceof ServerLevel sl && Grief.claimed(sl, pos)) ci.cancel();
	}

	@Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
	private void libreria$noSpreading(LevelReader level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if (level instanceof ServerLevel sl && Grief.claimed(sl, pos)) cir.setReturnValue(0);
	}
}
