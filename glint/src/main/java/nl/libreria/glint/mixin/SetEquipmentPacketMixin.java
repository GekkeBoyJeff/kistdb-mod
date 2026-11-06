package nl.libreria.glint.mixin;

import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.item.ItemStack;
import nl.libreria.glint.EquipmentFx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Worn armour as the player receiving this packet should see it: with its effect's equipment asset (EquipmentFx). */
@Mixin(ClientboundSetEquipmentPacket.class)
public abstract class SetEquipmentPacketMixin {
	@ModifyArg(method = "write", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/codec/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V"), index = 1)
	private Object libreria$armourFx(Object stack) {
		return stack instanceof ItemStack s ? EquipmentFx.forViewer(s) : stack;
	}
}
