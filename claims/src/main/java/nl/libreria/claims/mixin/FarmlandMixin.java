package nl.libreria.claims.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import nl.libreria.claims.Grief;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nothing tramples the farmland of a claim (except those who may build there); drying out still works. */
@Mixin(FarmlandBlock.class)
public abstract class FarmlandMixin {
	@Inject(method = "turnToBaseBlock", at = @At("HEAD"), cancellable = true)
	private void libreria$noTrampling(Entity entity, BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
		if (entity != null && level instanceof ServerLevel sl && Grief.guarded(sl, pos, entity)) ci.cancel();
	}
}
