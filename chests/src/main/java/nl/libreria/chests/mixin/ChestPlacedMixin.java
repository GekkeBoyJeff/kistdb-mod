package nl.libreria.chests.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import nl.libreria.chests.ChestOwners;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remembers who placed a chest, barrel or other container (quick stack only fills your own). */
@Mixin(BlockItem.class)
public abstract class ChestPlacedMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void libreria$placed(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (!cir.getReturnValue().consumesAction()) return;
		if (context.getPlayer() instanceof ServerPlayer p && context.getLevel() instanceof ServerLevel level) {
			ChestOwners.placed(p, level, context.getClickedPos());
		}
	}
}
