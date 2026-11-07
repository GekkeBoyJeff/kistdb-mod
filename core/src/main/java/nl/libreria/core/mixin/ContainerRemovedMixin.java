package nl.libreria.core.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import nl.libreria.core.ClickRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A menu closes (a chest, the inventory screen…): the close hooks of {@link ClickRouter} hear of it. */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerRemovedMixin {
	@Inject(method = "removed", at = @At("HEAD"))
	private void libreria$removed(Player player, CallbackInfo ci) {
		if (player instanceof ServerPlayer sp) ClickRouter.closed(sp, (AbstractContainerMenu) (Object) this);
	}
}
