package nl.libreria.teleports.mixin;

import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.LookAt;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import nl.libreria.teleports.Travel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An operator's /tp of a player: /back takes them to where they were before it. */
@Mixin(TeleportCommand.class)
public abstract class TeleportCommandMixin {
	@Inject(method = "performTeleport", at = @At("HEAD"))
	private static void libreria$rememberBack(CommandSourceStack source, Entity entity, ServerLevel level, double x, double y, double z,
			Set<Relative> relatives, float yaw, float pitch, LookAt lookAt, CallbackInfo ci) {
		if (entity instanceof ServerPlayer p) Travel.rememberBack(p, Travel.Why.TELEPORT);
	}
}
