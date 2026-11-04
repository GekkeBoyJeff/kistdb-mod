package nl.libreria.core.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import nl.libreria.core.Afk;
import nl.libreria.core.Tab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** "AFK" after the names of players who are away. Runs after other mods (nicknames) changed the name. */
@Mixin(value = ServerPlayer.class, priority = 1100)
public abstract class TabNameMixin {
	@Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
	private void libreria$afk(CallbackInfoReturnable<Component> cir) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		if (Afk.isAway(self)) cir.setReturnValue(Tab.name(self, cir.getReturnValue()));
	}
}
