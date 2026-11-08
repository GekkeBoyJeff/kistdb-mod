package nl.libreria.collector.mixin;

import net.minecraft.advancements.triggers.BrewedPotionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A potion taken out of a brewing stand: for the Brewed collection. */
@Mixin(BrewedPotionTrigger.class)
abstract class BrewMixin {
	@Inject(method = "trigger", at = @At("HEAD"))
	private void libreria$brewed(ServerPlayer player, PotionContents contents, CallbackInfo ci) {
		if (player != null && contents != null) nl.libreria.collector.CollectorCollections.onBrewed(player, contents);
	}
}
