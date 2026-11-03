package nl.kistdb.mixin;

import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import nl.kistdb.ClickHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerGamePacketListenerImpl.class, priority = 900)
public abstract class ContainerClickMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleContainerClick",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V", shift = At.Shift.AFTER),
			cancellable = true)
	private void kistdb$handleClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
		if (ClickHandler.handle(this.player, packet)) {
			ci.cancel();
		}
	}
}
