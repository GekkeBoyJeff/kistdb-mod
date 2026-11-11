package nl.libreria.info.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.trading.MerchantOffers;
import nl.libreria.info.TradeStories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The offers a player's client is told about: an item with a part of a story gets its dot (only in what they see). */
@Mixin(ServerPlayer.class)
public abstract class MerchantOffersViewMixin {
	@ModifyVariable(method = "sendMerchantOffers", at = @At("HEAD"), argsOnly = true)
	private MerchantOffers libreria$storyDots(MerchantOffers offers) {
		return TradeStories.forViewer((ServerPlayer) (Object) this, offers);
	}
}
