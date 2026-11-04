package nl.libreria.core.dialog;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import nl.libreria.core.Libreria;

/**
 * The plumbing behind {@link Window}s: which window each player has open, and routing what they click back to the
 * code that built it. A click arrives as a "custom click action" with the id {@code libreria:<name>}; the window's
 * own buttons use {@code libreria:window}, other code can listen to its own name with {@link #on}.
 */
public final class Dialogs {
	static final String NAMESPACE = "libreria";
	static final Identifier WINDOW = Identifier.fromNamespaceAndPath(NAMESPACE, "window");

	/** The last window shown to each player; only its buttons can be pressed. */
	private static final Map<UUID, Window> OPEN = new HashMap<>();
	private static final Map<String, BiConsumer<ServerPlayer, Values>> HANDLERS = new HashMap<>();

	private Dialogs() {
	}

	/** Listen to {@code libreria:<name>} clicks (for dialogs that live in a data pack, like the quick menu). */
	public static void on(String name, BiConsumer<ServerPlayer, Values> handler) {
		HANDLERS.put(name, handler);
	}

	static void opened(ServerPlayer player, Window window) {
		OPEN.put(player.getUUID(), window);
	}

	/** Closes whatever dialog the player has open. */
	public static void close(ServerPlayer player) {
		OPEN.remove(player.getUUID());
		player.connection.send(ClientboundClearDialogPacket.INSTANCE);
	}

	public static void forget(UUID player) {
		OPEN.remove(player);
	}

	/** From the mixin, on the server thread. Returns true when it was one of ours. */
	public static boolean dispatch(ServerPlayer player, Identifier id, Optional<Tag> payload) {
		if (!id.getNamespace().equals(NAMESPACE)) return false;
		CompoundTag tag = payload.orElse(null) instanceof CompoundTag c ? c : new CompoundTag();
		Values values = Values.of(tag);
		try {
			if (id.equals(WINDOW)) {
				Window w = OPEN.get(player.getUUID());
				int windowId = values.integer(Window.KEY_WINDOW, -1);
				int button = values.integer(Window.KEY_BUTTON, -1);
				// A click from a window that isn't the current one any more (or never existed): ignore
				if (w == null || w.id != windowId) return true;
				w.click(player, button, values);
				return true;
			}
			BiConsumer<ServerPlayer, Values> h = HANDLERS.get(id.getPath());
			if (h != null) h.accept(player, values);
		} catch (Exception e) {
			Libreria.LOG.error("Dialog action {} from {} failed", id, player.getName().getString(), e);
		}
		return true;
	}
}
