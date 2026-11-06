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
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Every item the server tells a player's client about in their menus goes through {@link ItemView}: what only that
 * player should see of it (their own armour's glow, a tooltip that explains its enchantments). The items themselves stay
 * as they are.
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
}
