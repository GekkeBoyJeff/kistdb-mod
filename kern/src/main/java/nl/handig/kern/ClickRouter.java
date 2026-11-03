package nl.handig.kern;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;

/** Verdeelt klikken in containerschermen: eerst de Handig-knop, dan de klik-haken van modules. */
public final class ClickRouter {
	/** Een module die klikken wil afvangen (bv. KistDB: middelklik = sorteren). */
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

		if (menu == player.inventoryMenu && slot == InventoryMenu.RESULT_SLOT && Hub.active(player)) {
			boolean right = input == ContainerInput.PICKUP && button == 1;
			boolean act = input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE;
			if (act) Hub.click(player, right);
			resync(player);
			return true;
		}

		for (Hook h : HOOKS) {
			if (h.onClick(player, menu, slot, input, button)) return true;
		}
		return false;
	}

	/** Stuurt de echte inhoud opnieuw (na een afgevangen klik), inclusief de Handig-knop. */
	public static void resync(ServerPlayer player) {
		player.containerMenu.broadcastFullState();
		if (player.containerMenu != player.inventoryMenu) {
			player.inventoryMenu.broadcastChanges();
		} else if (Hub.active(player)) {
			Hub.send(player);
		}
	}
}
