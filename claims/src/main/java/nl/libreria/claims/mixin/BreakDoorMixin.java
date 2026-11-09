package nl.libreria.claims.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.DoorInteractGoal;
import nl.libreria.claims.Grief;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zombies and vindicators don't break the doors of a claim. */
@Mixin(BreakDoorGoal.class)
public abstract class BreakDoorMixin extends DoorInteractGoal {
	private BreakDoorMixin(Mob mob) {
		super(mob);
	}

	@Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
	private void libreria$notInClaims(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && this.mob.level() instanceof ServerLevel level && Grief.guarded(level, this.doorPos, this.mob)) {
			cir.setReturnValue(false);
		}
	}
}
