package nl.libreria.claims.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import nl.libreria.claims.Grief;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lava doesn't flow into a claim from outside it. Water flows where it likes (placing it still needs permission). */
@Mixin(FlowingFluid.class)
public abstract class FluidMixin {
	@Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
	private void libreria$noFlowingIn(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState fluid, CallbackInfo ci) {
		if (fluid.is(FluidTags.LAVA) && level instanceof ServerLevel sl && Grief.flowsIn(sl, pos.relative(direction.getOpposite()), pos)) ci.cancel();
	}
}
