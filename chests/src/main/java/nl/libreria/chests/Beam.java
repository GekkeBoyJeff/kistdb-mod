package nl.libreria.chests;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import nl.libreria.core.Guide;
import org.jetbrains.annotations.Nullable;

/** Straal en omlijning voor kisten (via de gedeelde straal van de kern). */
public final class Beam {
	private Beam() {
	}

	/** Straal naar de container op {@code key}; {@code item} mag null zijn. */
	public static void to(ServerPlayer player, ServerLevel level, BlockPos key, @Nullable ItemStack item, int count) {
		Component what = item != null ? item.getHoverName() : Component.literal(kindAt(level, key));
		// What you look for lights up in that chest, and wherever you carry it
		if (item != null) Wanted.start(player, item);
		Guide.toBox(player, level, Containers.boxOf(level, key), item, what, count, 0);
	}

	public static void flash(ServerPlayer player, ServerLevel level, BlockPos key, int color, int ticks) {
		Guide.flash(player, level, Containers.boxOf(level, key), color, ticks);
	}

	private static String kindAt(ServerLevel level, BlockPos key) {
		String id = Containers.blockId(level, key);
		if (id.contains("barrel")) return "Barrel";
		if (id.contains("shulker")) return "Shulker box";
		return "Chest";
	}
}
