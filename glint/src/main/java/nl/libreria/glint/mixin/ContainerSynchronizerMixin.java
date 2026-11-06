package nl.libreria.glint.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import nl.libreria.glint.EquipmentFx;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Your own worn armour as you should see it (EquipmentFx.forOwner): only in what the server tells your client about
 * the four armour slots of your inventory (slots 5-8 of the inventory menu). The items themselves stay as they are.
 */
@Mixin(targets = "net/minecraft/server/level/ServerPlayer$1")
public abstract class ContainerSynchronizerMixin {
	@Shadow
	@Final
	private ServerPlayer this$0;

	@ModifyVariable(method = "sendSlotChange", at = @At("HEAD"), argsOnly = true)
	private ItemStack libreria$ownArmour(ItemStack stack, @Local(argsOnly = true) AbstractContainerMenu menu, @Local(argsOnly = true) int slot) {
		return menu instanceof InventoryMenu && slot >= 5 && slot <= 8 ? EquipmentFx.forOwner(this.this$0, stack) : stack;
	}

	@ModifyVariable(method = "sendInitialData", at = @At("HEAD"), argsOnly = true)
	private List<ItemStack> libreria$ownArmour(List<ItemStack> items, @Local(argsOnly = true) AbstractContainerMenu menu) {
		if (!(menu instanceof InventoryMenu) || items.size() < 9) return items;
		List<ItemStack> out = new ArrayList<>(items);
		for (int i = 5; i <= 8; i++) out.set(i, EquipmentFx.forOwner(this.this$0, items.get(i)));
		return out;
	}
}
