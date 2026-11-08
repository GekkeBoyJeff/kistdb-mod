package nl.libreria.collector.mixin;

import java.util.Collection;
import net.minecraft.advancements.triggers.FishingRodHookedTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** What came up on the hook: for the Fished up collection. */
@Mixin(FishingRodHookedTrigger.class)
abstract class FishMixin {
	@Inject(method = "trigger", at = @At("HEAD"))
	private void libreria$fished(ServerPlayer player, ItemStack rod, FishingHook hook, Collection<ItemStack> items, CallbackInfo ci) {
		if (player != null && items != null) nl.libreria.collector.CollectorCollections.onFished(player, items);
	}
}
