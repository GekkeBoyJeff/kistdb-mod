package nl.libreria.graves.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import nl.libreria.graves.GravesMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vlak voordat vanilla je spullen laat vallen: als je graf aan staat, gaan ze in je graf. */
@Mixin(Player.class)
abstract class PlayerDropMixin {
	@Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
	private void libreria$grave(ServerLevel level, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && GravesMod.intoGrave(player, level)) ci.cancel();
	}
}
