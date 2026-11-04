package nl.libreria.core.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import nl.libreria.core.Chronicle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tells the chronicle when a player completes an advancement (the Nether, a challenge…). */
@Mixin(PlayerAdvancements.class)
public abstract class AdvancementMixin {
	@Shadow
	private ServerPlayer player;

	@Unique
	private boolean libreria$wasDone;

	@Shadow
	public abstract AdvancementProgress getOrStartProgress(AdvancementHolder holder);

	@Inject(method = "award", at = @At("HEAD"))
	private void libreria$before(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
		this.libreria$wasDone = getOrStartProgress(holder).isDone();
	}

	@Inject(method = "award", at = @At("RETURN"))
	private void libreria$after(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
		if (!this.libreria$wasDone && cir.getReturnValueZ() && getOrStartProgress(holder).isDone()) {
			Chronicle.onAdvancement(this.player, holder);
		}
	}
}
