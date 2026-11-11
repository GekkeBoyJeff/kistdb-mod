package nl.libreria.refresh.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What an item frame holds, for the frames of the map screen (only on a player's screen). */
@Mixin(ItemFrame.class)
public interface ItemFrameAccess {
	@Accessor("DATA_ITEM")
	static EntityDataAccessor<ItemStack> libreria$dataItem() {
		throw new AssertionError();
	}
}
