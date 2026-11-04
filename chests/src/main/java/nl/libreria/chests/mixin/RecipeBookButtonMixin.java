package nl.libreria.chests.mixin;

import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import nl.libreria.chests.ChestSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The recipe book button in your own inventory is the one button outside the slots that tells the server it was
 * clicked. With the Libreria pack it is drawn as a magnifying glass and opens the chest search instead.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RecipeBookButtonMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleRecipeBookChangeSettingsPacket", at = @At("HEAD"), cancellable = true)
	private void libreria$searchButton(ServerboundRecipeBookChangeSettingsPacket packet, CallbackInfo ci) {
		// Vanilla first hands the packet over to the server thread; act there
		if (!this.player.level().getServer().isSameThread()) return;
		if (ChestSearch.onRecipeBookButton(this.player, packet.getBookType(), packet.isOpen(), packet.isFiltering())) ci.cancel();
	}
}
