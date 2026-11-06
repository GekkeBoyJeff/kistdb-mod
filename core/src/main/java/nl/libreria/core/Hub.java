package nl.libreria.core;

import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The buttons beside the inventory window. The Libreria pack draws them (in Minecraft's own button style) on the left
 * edge of your inventory: deposit over search. The inventory screen has only one real button (the recipe book, which
 * stays the recipe book), so these work by what does reach the server: a click outside the window with nothing in
 * your hand, which vanilla ignores. The game doesn't say where, so it's the mouse button that counts: left and right
 * can each do something else (a middle-click the game only sends in creative). The crafting grid is left alone.
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

	/** @param button 0 left, 1 right */
	static void edgeClick(ServerPlayer player, int button) {
		Consumer<ServerPlayer> action = button == 1 && edgeRight != null ? edgeRight : edgeLeft;
		if (action == null) return;
		Ui.click(player);
		action.accept(player);
	}
}
