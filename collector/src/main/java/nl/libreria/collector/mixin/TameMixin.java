package nl.libreria.collector.mixin;

import net.minecraft.advancements.triggers.TameAnimalTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An animal tamed (where vanilla fires its advancement): for the Tamed collection. */
@Mixin(TameAnimalTrigger.class)
abstract class TameMixin {
	@Inject(method = "trigger", at = @At("HEAD"))
	private void libreria$tamed(ServerPlayer player, Animal animal, CallbackInfo ci) {
		if (player != null && animal != null) nl.libreria.collector.CollectorCollections.onTamed(player, animal);
	}
}
