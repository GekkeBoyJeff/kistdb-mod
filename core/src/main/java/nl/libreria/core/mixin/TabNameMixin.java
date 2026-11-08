package nl.libreria.core.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import nl.libreria.core.Tab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A nickname ({@link nl.libreria.core.Names}), then the title and "AFK" after it in the tab list. */
@Mixin(value = ServerPlayer.class, priority = 1100)
public abstract class TabNameMixin {
	@Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
	private void libreria$afk(CallbackInfoReturnable<Component> cir) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		Component nick = nl.libreria.core.Names.tab(self);
		Component name = Tab.name(self, nick != null ? nick : cir.getReturnValue());
		if (name != cir.getReturnValue()) cir.setReturnValue(name);
	}
}
