package nl.libreria.glint.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import nl.libreria.glint.EquipmentFx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** In creative mode the client sends whole items back: one of our armour assets in it is taken out again. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CreativeSlotMixin {
	@ModifyExpressionValue(method = "handleSetCreativeModeSlot", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/game/ServerboundSetCreativeModeSlotPacket;itemStack()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack libreria$clean(ItemStack stack) {
		return EquipmentFx.clean(stack);
	}
}
