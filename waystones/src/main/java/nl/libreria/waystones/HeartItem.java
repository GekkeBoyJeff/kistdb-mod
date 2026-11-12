package nl.libreria.waystones;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import nl.libreria.core.Notify;
import org.jetbrains.annotations.Nullable;

/**
 * The Waystone Heart (a nether star and two diamonds): used on a waystone that stands in the middle of a 5 × 5 sculk
 * floor, it's the last piece and the waystone rises as a grand waystone (see {@link Grand}). On anything else it says so.
 */
public final class HeartItem extends Item implements PolymerItem {
	HeartItem(Properties properties) {
		super(properties);
	}

	@Override
	public Item getPolymerItem(ItemStack stack, @Nullable PacketContext context) {
		return Items.PAPER;
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		if (ctx.getPlayer() instanceof ServerPlayer p) {
			Notify.info(p, this, "Use it on a waystone", "One in the middle of a 5 × 5 sculk floor: it rises as a grand waystone.");
		}
		return InteractionResult.FAIL;
	}
}
