package nl.libreria.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import nl.libreria.core.ItemView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every item the server tells a player's client about in their menus goes through {@link ItemView}: what only that
 * player should see of it (their own armour's glow, a tooltip that explains its enchantments). The items themselves stay
 * as they are.
 *
 * <p>While one of our windows is open that hides your inventory (its rows are ours, drawn over with invisible items),
 * your real inventory is not sent: the client keeps it in the same place, so a real item would show through in the
 * window (a pickaxe on the Back button). The window sends the inventory again once it closes.
 */
@Mixin(targets = "net/minecraft/server/level/ServerPlayer$1")
public abstract class ContainerSynchronizerMixin {
	@Shadow
	@Final
	private ServerPlayer this$0;

	@ModifyVariable(method = "sendSlotChange", at = @At("HEAD"), argsOnly = true)
	private ItemStack libreria$view(ItemStack stack, @Local(argsOnly = true) AbstractContainerMenu menu, @Local(argsOnly = true) int slot) {
		return ItemView.forViewer(this.this$0, menu, slot, stack);
	}

	@ModifyVariable(method = "sendInitialData", at = @At("HEAD"), argsOnly = true)
	private List<ItemStack> libreria$view(List<ItemStack> items, @Local(argsOnly = true) AbstractContainerMenu menu) {
		return ItemView.forViewer(this.this$0, menu, items);
	}

	@ModifyVariable(method = "sendCarriedChange", at = @At("HEAD"), argsOnly = true)
	private ItemStack libreria$viewCarried(ItemStack stack, @Local(argsOnly = true) AbstractContainerMenu menu) {
		return ItemView.forViewer(this.this$0, menu, -1, stack);
	}

	@Inject(method = "sendSlotChange", at = @At("HEAD"), cancellable = true)
	private void libreria$hiddenSlot(CallbackInfo ci, @Local(argsOnly = true) AbstractContainerMenu menu) {
		if (this.libreria$hidden(menu)) ci.cancel();
	}

	@Inject(method = "sendInitialData", at = @At("HEAD"), cancellable = true)
	private void libreria$hiddenAll(CallbackInfo ci, @Local(argsOnly = true) AbstractContainerMenu menu) {
		if (this.libreria$hidden(menu)) ci.cancel();
	}

	@Inject(method = "sendCarriedChange", at = @At("HEAD"), cancellable = true)
	private void libreria$hiddenCarried(CallbackInfo ci, @Local(argsOnly = true) AbstractContainerMenu menu) {
		if (this.libreria$hidden(menu)) ci.cancel();
	}

	/** Your inventory, while a window that draws over it is open. */
	private boolean libreria$hidden(AbstractContainerMenu menu) {
		ServerPlayer p = this.this$0;
		return menu == p.inventoryMenu && p.containerMenu != menu && p.containerMenu instanceof eu.pb4.sgui.api.containerwrappers.SlotBasedWrapperMenu w
				&& w.getBackingGui().isIncludingPlayer();
	}
}
