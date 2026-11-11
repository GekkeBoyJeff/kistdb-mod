package nl.libreria.info.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import nl.libreria.info.TradeStories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A villager's offers get their chance of a story as a player starts trading; a villager born from breeding is marked. */
@Mixin(Villager.class)
public abstract class VillagerTradeMixin {
	@Inject(method = "startTrading", at = @At("HEAD"))
	private void libreria$tradeStories(Player player, CallbackInfo ci) {
		TradeStories.trading((Villager) (Object) this, player);
	}

	@Inject(method = "getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/npc/villager/Villager;",
			at = @At("RETURN"))
	private void libreria$bred(ServerLevel level, AgeableMob partner, CallbackInfoReturnable<Villager> cir) {
		Villager child = cir.getReturnValue();
		if (child != null) child.addTag(TradeStories.BRED);
	}
}
