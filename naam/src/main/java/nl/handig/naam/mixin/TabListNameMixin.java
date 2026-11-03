package nl.handig.naam.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import nl.handig.naam.Naam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Je nickname in de tablijst. */
@Mixin(ServerPlayer.class)
abstract class TabListNameMixin {
	@Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
	private void handig$tab(CallbackInfoReturnable<Component> cir) {
		Component nick = Naam.tabName((ServerPlayer) (Object) this);
		if (nick != null) cir.setReturnValue(nick);
	}
}
