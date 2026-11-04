package nl.libreria.core;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;

/** Verdeelt klikken in containerschermen: eerst de Libreria-knop, dan de klik-haken van modules. */
public final class ClickRouter {
	/** Een module die klikken wil afvangen (bv. ChestsMod: middelklik = sorteren). */
	public interface Hook {
		/** @return true als de klik is afgehandeld (vanilla negeert hem dan). */
		boolean onClick(ServerPlayer player, AbstractContainerMenu menu, int slot, ContainerInput input, int button);
	}

	private static final List<Hook> HOOKS = new ArrayList<>();

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

		// The edge button beside the inventory window: a click outside it with an empty hand
		if (menu == player.inventoryMenu && slot == -999 && input == ContainerInput.THROW && menu.getCarried().isEmpty()
				&& Hub.edgeActive(player)) {
			Hub.edgeClick(player, button);
			return true;
		}

		if (menu == player.inventoryMenu && Hub.isButton(slot) && Hub.active(player)) {
			// Putting a real item into the crafting grid (carrying one, a number key with something in that
			// hotbar slot, or dragging): vanilla does that, then the buttons step aside
			boolean carrying = !menu.getCarried().isEmpty();
			boolean swapIn = input == ContainerInput.SWAP && (button == 40 || button >= 0 && button < 9)
					&& !player.getInventory().getItem(button).isEmpty();
			if (slot != InventoryMenu.RESULT_SLOT && (carrying || swapIn || input == ContainerInput.QUICK_CRAFT)) {
				player.level().getServer().execute(() -> {
					if (!Hub.active(player)) Hub.remove(player);
				});
				return false;
			}
			boolean right = input == ContainerInput.PICKUP && button == 1;
			boolean act = !carrying && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE);
			resync(player);
			if (act) Hub.click(player, slot, right);
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
		} else if (Hub.active(player)) {
			Hub.send(player);
		}
	}
}
