package nl.libreria.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import nl.libreria.core.ItemView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** In creative mode the client sends whole items back: what was only for its eyes ({@link ItemView}) comes out again. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CreativeSlotMixin {
	@ModifyExpressionValue(method = "handleSetCreativeModeSlot", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/game/ServerboundSetCreativeModeSlotPacket;itemStack()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack libreria$clean(ItemStack stack) {
		return ItemView.clean(stack);
	}
}
