package nl.libreria.core;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * Keys a vanilla client sends to the server that modules may use as hotkeys: swap hands (F) and drop (Q / Ctrl+Q).
 * Both can be rebound by the player in Controls, and the server knows whether they sneak, so "sneak + F" is a free
 * combination. A handler that returns true takes the key over (vanilla doesn't swap or drop).
 */
public final class Hotkeys {
	public enum Key {
		SWAP_HANDS, DROP, DROP_STACK
	}

	public interface Handler {
		boolean onKey(ServerPlayer player, Key key, boolean sneaking);
	}

	private static final List<Handler> HANDLERS = new ArrayList<>();

	private Hotkeys() {
	}

	public static void register(Handler handler) {
		HANDLERS.add(handler);
	}

	/** From the mixin, on the server thread. */
	public static boolean dispatch(ServerPlayer player, ServerboundPlayerActionPacket.Action action) {
		Key key = switch (action) {
			case SWAP_ITEM_WITH_OFFHAND -> Key.SWAP_HANDS;
			case DROP_ITEM -> Key.DROP;
			case DROP_ALL_ITEMS -> Key.DROP_STACK;
			default -> null;
		};
		if (key == null || player.isSpectator()) return false;
		for (Handler h : HANDLERS) {
			if (h.onKey(player, key, player.isShiftKeyDown())) return true;
		}
		return false;
	}
}
