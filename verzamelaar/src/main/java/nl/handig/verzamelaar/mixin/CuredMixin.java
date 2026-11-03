package nl.handig.verzamelaar.mixin;

import net.minecraft.advancements.criterion.CuredZombieVillagerTrigger;
import net.minecraft.server.level.ServerPlayer;
import nl.handig.verzamelaar.Verzamelaar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Een zombiedorpeling genezen. */
@Mixin(CuredZombieVillagerTrigger.class)
abstract class CuredMixin {
	@Inject(method = "trigger", at = @At("HEAD"))
	private void handig$cured(ServerPlayer player, net.minecraft.world.entity.monster.zombie.Zombie zombie,
			net.minecraft.world.entity.npc.villager.Villager villager, CallbackInfo ci) {
		Verzamelaar.onCured(player);
	}
}
