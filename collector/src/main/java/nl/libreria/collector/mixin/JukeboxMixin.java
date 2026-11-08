package nl.libreria.collector.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.JukeboxBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A disc put into an empty jukebox (before it is taken from the hand): for the Music discs collection. */
@Mixin(JukeboxPlayable.class)
abstract class JukeboxMixin {
	@Inject(method = "tryInsertIntoJukebox", at = @At("HEAD"))
	private static void libreria$played(Level level, BlockPos pos, ItemStack stack, Player player, CallbackInfoReturnable<InteractionResult> cir) {
		if (!(player instanceof ServerPlayer sp) || level.isClientSide()) return;
		var state = level.getBlockState(pos);
		if (state.getBlock() instanceof JukeboxBlock && !state.getValue(JukeboxBlock.HAS_RECORD)) {
			nl.libreria.collector.CollectorCollections.onDisc(sp, stack.copy());
		}
	}
}
