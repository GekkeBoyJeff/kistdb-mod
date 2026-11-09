package nl.libreria.claims.mixin;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import nl.libreria.claims.Grief;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Explosions and claims: the blocks it would break (and set fire to) in a claim stay, and what belongs to the land
 * (animals, pets, villagers, armour stands, mannequins, item frames, paintings) sits it out.
 */
@Mixin(ServerExplosion.class)
public abstract class ExplosionMixin {
	@Inject(method = "calculateExplodedPositions", at = @At("RETURN"), cancellable = true)
	private void libreria$claimsStay(CallbackInfoReturnable<List<BlockPos>> cir) {
		List<BlockPos> all = cir.getReturnValue();
		List<BlockPos> keep = Grief.explosion((Explosion) (Object) this, all);
		if (keep != all) cir.setReturnValue(keep);
	}

	@Redirect(method = "hurtEntities", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;ignoreExplosion(Lnet/minecraft/world/level/Explosion;)Z"))
	private boolean libreria$shelter(Entity entity, Explosion explosion) {
		return entity.ignoreExplosion(explosion) || Grief.shelters(entity, explosion);
	}
}
