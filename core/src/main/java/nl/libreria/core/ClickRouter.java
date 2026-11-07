package nl.libreria.core;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

/** Verdeelt klikken in containerschermen: eerst de Libreria-knop, dan de klik-haken van modules. */
public final class ClickRouter {
	/** Een module die klikken wil afvangen (bv. ChestsMod: dubbelklik op een leeg slot = sorteren). */
	public interface Hook {
		/** @return true als de klik is afgehandeld (vanilla negeert hem dan). */
		boolean onClick(ServerPlayer player, AbstractContainerMenu menu, int slot, ContainerInput input, int button);
	}

	/** A module that wants to know when a menu closes (a chest, or the player's own inventory screen). */
	public interface CloseHook {
		void onClose(ServerPlayer player, AbstractContainerMenu menu);
	}

	private static final List<Hook> HOOKS = new ArrayList<>();
	private static final List<CloseHook> CLOSE_HOOKS = new ArrayList<>();

	public static void registerClose(CloseHook hook) {
		CLOSE_HOOKS.add(hook);
	}

	/** Called from the mixin when a menu is closed (and so taken away), just before. */
	public static void closed(ServerPlayer player, AbstractContainerMenu menu) {
		for (CloseHook h : CLOSE_HOOKS) {
			try {
				h.onClose(player, menu);
			} catch (RuntimeException e) {
				Libreria.LOG.warn("Close hook failed", e);
			}
		}
	}

	private ClickRouter() {
	}

	public static void register(Hook hook) {
		HOOKS.add(hook);
	}

	/** Aangeroepen vanuit de mixin. */
	public static boolean handle(ServerPlayer player, ServerboundContainerClickPacket packet) {
		AbstractContainerMenu menu = player.containerMenu;
		if (packet.containerId() != menu.containerId || player.isSpectator()) return false;
		int slot = packet.slotNum();
		ContainerInput input = packet.containerInput();
		int button = packet.buttonNum();

		// The buttons beside the inventory window: a click outside it with an empty hand (left or right; a middle-click
		// the game only sends in creative)
		if (menu == player.inventoryMenu && slot == -999 && input == ContainerInput.THROW && menu.getCarried().isEmpty()
				&& Hub.edgeActive(player)) {
			Hub.edgeClick(player, button);
			return true;
		}

		for (Hook h : HOOKS) {
			if (h.onClick(player, menu, slot, input, button)) return true;
		}
		return false;
	}

	/** Stuurt de echte inhoud opnieuw (na een afgevangen klik), inclusief de Libreria-knop. */
	public static void resync(ServerPlayer player) {
		player.containerMenu.broadcastFullState();
		if (player.containerMenu != player.inventoryMenu) {
			player.inventoryMenu.broadcastChanges();
		}
	}
}
