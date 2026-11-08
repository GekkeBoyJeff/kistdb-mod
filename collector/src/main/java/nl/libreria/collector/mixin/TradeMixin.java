package nl.libreria.collector.mixin;

import net.minecraft.advancements.triggers.TradeTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A trade with a villager or the wandering trader: for the Traded with collection. */
@Mixin(TradeTrigger.class)
abstract class TradeMixin {
	@Inject(method = "trigger", at = @At("HEAD"))
	private void libreria$traded(ServerPlayer player, AbstractVillager trader, ItemStack stack, CallbackInfo ci) {
		if (player != null && trader != null) nl.libreria.collector.CollectorCollections.onTraded(player, trader);
	}
}
