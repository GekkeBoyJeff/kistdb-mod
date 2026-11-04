package nl.libreria.core;

import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The button beside the inventory window. The Libreria pack draws it (in Minecraft's own button style) on the left
 * edge of your inventory, on the row of the search button. The inventory screen has only one real button (the recipe
 * book one, which the pack turns into the search button), so this one works by what does reach the server: a click
 * outside the window with nothing in your hand, which vanilla ignores. Left and right click can do different things.
 * The crafting grid is left alone.
 */
public final class Hub {
	private static @Nullable Consumer<ServerPlayer> edgeLeft, edgeRight;

	private Hub() {
	}

	public static void setEdgeButton(Consumer<ServerPlayer> left, @Nullable Consumer<ServerPlayer> right) {
		edgeLeft = left;
		edgeRight = right;
	}

	static boolean edgeActive(ServerPlayer player) {
		return edgeLeft != null && !player.isCreative() && !player.isSpectator() && Libreria.HUB_BUTTON.get(player) && Pack.has(player);
	}

	static void edgeClick(ServerPlayer player, int button) {
		Consumer<ServerPlayer> action = button == 1 && edgeRight != null ? edgeRight : edgeLeft;
		if (action == null) return;
		Ui.click(player);
		action.accept(player);
	}
}
