package nl.libreria.info.mixin;

import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;
import nl.libreria.info.TradeStories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A trade was made: an offer that carried a story sells the plain thing from now on. */
@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerTradeMixin {
	@Inject(method = "notifyTrade", at = @At("HEAD"))
	private void libreria$traded(MerchantOffer offer, CallbackInfo ci) {
		TradeStories.traded(offer);
	}
}
