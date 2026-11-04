package nl.libreria.claims.mixin;

import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import nl.libreria.claims.ClaimsMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the land map is open: Q (drop) closes it, and drop / swap-hands never touch the real items in your hands. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MapKeysMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
	private void libreria$mapKeys(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		// Netty thread: vanilla hands the packet over to the server thread first, we act there
		if (!this.player.level().getServer().isSameThread()) return;
		if (ClaimsMod.onMapKey(this.player, packet.getAction())) ci.cancel();
	}
}
