package nl.libreria.core.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.CraftingTableBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The Libreria pack turns the inventory's "Crafting" title into the sort button beside the window. The crafting
 * table uses the same text for its title, so it gets its own ("Crafting Table") instead.
 */
@Mixin(CraftingTableBlock.class)
public abstract class CraftingTableTitleMixin {
	@ModifyArg(method = "getMenuProvider", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/SimpleMenuProvider;<init>(Lnet/minecraft/world/inventory/MenuConstructor;Lnet/minecraft/network/chat/Component;)V"),
			index = 1, require = 0)
	private Component libreria$title(Component title) {
		return Component.translatable("block.minecraft.crafting_table");
	}
}
