package nl.libreria.nicknames.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import nl.libreria.nicknames.NicknamesMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Je nickname overal waar Minecraft je "weergavenaam" gebruikt: chat, doodsberichten, /msg… */
@Mixin(Player.class)
abstract class DisplayNameMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void libreria$nick(CallbackInfoReturnable<Component> cir) {
		if ((Object) this instanceof ServerPlayer player) {
			Component nick = NicknamesMod.chatName(player);
			if (nick != null) cir.setReturnValue(nick);
		}
	}
}
