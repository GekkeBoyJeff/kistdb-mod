package nl.libreria.comfort.mixin;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import nl.libreria.comfort.SleepHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** When the time runs while sleeping (or for a nap), the game doesn't jump to morning itself. */
@Mixin(ServerLevel.class)
public abstract class SleepLevelMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/players/SleepStatus;areEnoughDeepSleeping(ILjava/util/List;)Z"))
	private boolean libreria$deepSleeping(SleepStatus status, int percent, List<ServerPlayer> players) {
		if (SleepHooks.handlesNight((ServerLevel) (Object) this)) return false;
		return status.areEnoughDeepSleeping(percent, players);
	}
}
