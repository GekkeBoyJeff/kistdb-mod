package nl.libreria.core.mixin;

import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import nl.libreria.core.Hotkeys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pick block (middle-click) reaches {@link Hotkeys} first. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PickItemMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePickItemFromBlock", at = @At("HEAD"), cancellable = true)
	private void libreria$pick(ServerboundPickItemFromBlockPacket packet, CallbackInfo ci) {
		// Netty thread: vanilla hands the packet over to the server thread first, we act there
		if (!this.player.level().getServer().isSameThread()) return;
		if (Hotkeys.dispatchPick(this.player, packet.pos())) ci.cancel();
	}
}
