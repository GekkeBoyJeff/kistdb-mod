package nl.libreria.tools.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import nl.libreria.tools.ToolsMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Merkt op dat een uitgeruste tool breekt (mining, vechten, alles), zodat de volgende tool klaargelegd wordt. */
@Mixin(ServerPlayer.class)
public abstract class ItemBrokenMixin {
	@Inject(method = "onEquippedItemBroken", at = @At("HEAD"))
	private void libreria$onBroken(ItemStack stack, EquipmentSlot slot, CallbackInfo ci) {
		ToolsMod.onBroken((ServerPlayer) (Object) this, stack, slot);
	}
}
