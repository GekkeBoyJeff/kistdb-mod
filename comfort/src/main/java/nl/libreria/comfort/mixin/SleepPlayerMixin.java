package nl.libreria.comfort.mixin;

import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import nl.libreria.comfort.SleepHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A napping player (or one sleeping while the time runs past dawn) isn't woken because it's light. */
@Mixin(Player.class)
public abstract class SleepPlayerMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/attribute/BedRule;canSleep(Lnet/minecraft/world/level/Level;)Z"))
	private boolean libreria$canStillSleep(BedRule rule, Level level) {
		return rule.canSleep(level) || SleepHooks.keepAsleep((Player) (Object) this);
	}
}
