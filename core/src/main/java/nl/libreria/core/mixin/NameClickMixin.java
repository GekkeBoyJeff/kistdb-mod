package nl.libreria.core.mixin;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Click a name in chat: that player's profile (instead of starting a /tell; shift-click still puts the name in chat). */
@Mixin(Player.class)
abstract class NameClickMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void libreria$profileClick(CallbackInfoReturnable<Component> cir) {
		if (!((Object) this instanceof ServerPlayer player)) return;
		Component name = cir.getReturnValue();
		if (name != null && name.getStyle().getClickEvent() instanceof ClickEvent.SuggestCommand tell && tell.command().startsWith("/tell ")) {
			cir.setReturnValue(name.copy().withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/profile " + player.getGameProfile().name()))));
		}
	}
}
