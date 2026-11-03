package nl.libreria.collector.mixin;

import net.minecraft.advancements.triggers.BredAnimalsTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import nl.libreria.collector.CollectorMod;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dezelfde plek waar vanilla de "fok"-advancement afvuurt; vangt ook eieren (schildpad, kikker, snuffelaar). */
@Mixin(BredAnimalsTrigger.class)
abstract class BredAnimalsMixin {
	@Inject(method = "trigger", at = @At("HEAD"))
	private void libreria$bred(ServerPlayer player, Animal parent, Animal partner, @Nullable AgeableMob child, CallbackInfo ci) {
		CollectorMod.onBred(player, parent, child);
	}
}
