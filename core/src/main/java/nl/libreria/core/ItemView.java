package nl.libreria.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Items as one player's client should see them, without changing the items: your own armour with its glow, a tooltip
 * that explains its enchantments. Only in what the server tells that player's client about their open menus (inventory,
 * chests…); the item itself stays as it is. Modules add a {@link Transform}; in creative mode the client sends whole
 * items back, so every module that adds something also says how to take it out again ({@link #cleaner}).
 */
public final class ItemView {
	/** What a viewer sees of one slot ({@code slot} is the menu's slot index, -1 for the item on the cursor). */
	@FunctionalInterface
	public interface Transform {
		ItemStack apply(ServerPlayer viewer, AbstractContainerMenu menu, int slot, ItemStack stack);
	}

	@FunctionalInterface
	public interface Cleaner {
		ItemStack clean(ItemStack stack);
	}

	private static final List<Transform> TRANSFORMS = new CopyOnWriteArrayList<>();
	private static final List<Cleaner> CLEANERS = new CopyOnWriteArrayList<>();
	private static final Map<UUID, GameType> MODES = new HashMap<>();

	private ItemView() {
	}

	public static void add(Transform t) {
		TRANSFORMS.add(t);
	}

	public static void cleaner(Cleaner c) {
		CLEANERS.add(c);
	}

	/** Called by the container synchronizer for every item it sends. Never throws; never changes {@code stack}. */
	public static ItemStack forViewer(ServerPlayer viewer, AbstractContainerMenu menu, int slot, ItemStack stack) {
		if (stack.isEmpty() || TRANSFORMS.isEmpty()) return stack;
		ItemStack out = stack;
		for (Transform t : TRANSFORMS) {
			try {
				out = t.apply(viewer, menu, slot, out);
			} catch (Throwable e) {
				Libreria.LOG.debug("ItemView transform failed", e);
			}
		}
		return out;
	}

	public static List<ItemStack> forViewer(ServerPlayer viewer, AbstractContainerMenu menu, List<ItemStack> items) {
		if (TRANSFORMS.isEmpty()) return items;
		List<ItemStack> out = new ArrayList<>(items.size());
		for (int i = 0; i < items.size(); i++) out.add(forViewer(viewer, menu, i, items.get(i)));
		return out;
	}

	/** An item a client sent back (creative mode): everything that was only for that client's eyes taken out. */
	public static ItemStack clean(ItemStack stack) {
		ItemStack out = stack;
		for (Cleaner c : CLEANERS) {
			try {
				out = c.clean(out);
			} catch (Throwable e) {
				Libreria.LOG.debug("ItemView cleaner failed", e);
			}
		}
		return out;
	}

	/** Tells a player's client about everything in their inventory and open menu again (after what they see changed). */
	public static void resend(ServerPlayer p) {
		p.inventoryMenu.sendAllDataToRemote();
		if (p.containerMenu != p.inventoryMenu) p.containerMenu.sendAllDataToRemote();
	}

	/** Whether a viewer is in creative mode (their client handles their items itself there: nothing extra is shown). */
	public static boolean creative(ServerPlayer p) {
		return p.isCreative();
	}

	static void init() {
		// Into or out of creative mode: the inventory once more, as that mode should show it
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 != 3) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				GameType now = p.gameMode.getGameModeForPlayer(), before = MODES.put(p.getUUID(), now);
				if (before != null && before != now && (before == GameType.CREATIVE || now == GameType.CREATIVE)) resend(p);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MODES.remove(handler.player.getUUID()));
	}
}
