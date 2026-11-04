package nl.libreria.core.mixin;

import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import nl.libreria.core.dialog.Dialogs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Buttons in Libreria windows ({@code libreria:*} custom click actions) go to {@link Dialogs}. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class CustomClickActionMixin {
	@Shadow
	@Final
	protected MinecraftServer server;

	@Inject(method = "handleCustomClickAction", at = @At("HEAD"), cancellable = true)
	private void libreria$window(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
		// Netty thread: vanilla hands the packet over to the server thread first, we act there
		if (!this.server.isSameThread()) return;
		if (!((Object) this instanceof ServerGamePacketListenerImpl game)) return;
		if (Dialogs.dispatch(game.player, packet.id(), packet.payload())) ci.cancel();
	}
}
