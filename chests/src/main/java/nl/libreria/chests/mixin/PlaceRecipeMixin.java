package nl.libreria.chests.mixin;

import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import nl.libreria.chests.CraftSupply;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Before the game places a recipe from the recipe book, missing items come from the player's supply chests. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlaceRecipeMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePlaceRecipe", at = @At("HEAD"))
	private void libreria$supply(ServerboundPlaceRecipePacket packet, CallbackInfo ci) {
		// Netty thread: vanilla hands the packet over to the server thread first, we act there
		if (!this.player.level().getServer().isSameThread()) return;
		CraftSupply.beforePlace(this.player, packet);
	}
}
