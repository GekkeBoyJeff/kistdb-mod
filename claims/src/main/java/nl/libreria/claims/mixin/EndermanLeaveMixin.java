package nl.libreria.claims.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Enderman;
import nl.libreria.claims.Grief;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Endermen don't drop their block into a claim either. */
@Mixin(targets = "net.minecraft.world.entity.monster.Enderman$EndermanLeaveBlockGoal")
public abstract class EndermanLeaveMixin {
	@Shadow
	@Final
	private Enderman enderman;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void libreria$notIntoClaims(CallbackInfo ci) {
		if (this.enderman.level() instanceof ServerLevel level && Grief.nearClaim(level, this.enderman.blockPosition(), 2)) ci.cancel();
	}
}
