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

/** Endermen don't take blocks out of a claim (they reach about two blocks around them). */
@Mixin(targets = "net.minecraft.world.entity.monster.Enderman$EndermanTakeBlockGoal")
public abstract class EndermanTakeMixin {
	@Shadow
	@Final
	private Enderman enderman;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void libreria$notFromClaims(CallbackInfo ci) {
		if (this.enderman.level() instanceof ServerLevel level && Grief.nearClaim(level, this.enderman.blockPosition(), 3)) ci.cancel();
	}
}
