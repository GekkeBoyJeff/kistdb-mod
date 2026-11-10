package nl.libreria.claims.mixin;

import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import nl.libreria.claims.ClaimView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right-click on the land map. The game itself only hands a right-click on to the server when there's something in
 * your hand: on the land map it counts with an empty hand too.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class LandMapUseMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
	private void libreria$landMap(ServerboundUseItemPacket packet, CallbackInfo ci) {
		// Netty thread: vanilla hands the packet over to the server thread first, we act there
		if (!this.player.level().getServer().isSameThread()) return;
		if (ClaimView.rightClick(this.player, packet.getHand())) ci.cancel();
	}
}
