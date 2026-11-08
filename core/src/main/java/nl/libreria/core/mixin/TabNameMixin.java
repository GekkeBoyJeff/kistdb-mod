package nl.libreria.core.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import nl.libreria.core.Tab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Titles and "AFK" after the names in the tab list. Runs after other mods (nicknames) changed the name. */
@Mixin(value = ServerPlayer.class, priority = 1100)
public abstract class TabNameMixin {
	@Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
	private void libreria$afk(CallbackInfoReturnable<Component> cir) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		Component name = Tab.name(self, cir.getReturnValue());
		if (name != cir.getReturnValue()) cir.setReturnValue(name);
	}
}
