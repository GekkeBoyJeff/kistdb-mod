package nl.kistdb;

import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import nl.kistdb.gui.OverviewGui;

/** Vangt klikken af: het sorteer-icoon in het E-scherm, en middelklik / dubbelklik-op-leeg-slot in kisten. */
public final class ClickHandler {
	private ClickHandler() {
	}

	/** @return true als KistDB de klik heeft afgehandeld (vanilla moet hem dan negeren). */
	public static boolean handle(ServerPlayer player, ServerboundContainerClickPacket packet) {
		AbstractContainerMenu menu = player.containerMenu;
		if (packet.containerId() != menu.containerId || player.isSpectator()) return false;
		int slot = packet.slotNum();
		ContainerInput input = packet.containerInput();
		int button = packet.buttonNum();

		// 1) Het icoon in het crafting-uitvak van het E-scherm
		if (menu == player.inventoryMenu && slot == InventoryMenu.RESULT_SLOT && CraftButton.active(player)) {
			boolean openGui = false;
			if (input == ContainerInput.PICKUP && button == 1) {
				openGui = true;
			} else if (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE) {
				Sorter.sortPlayer(player);
			}
			resync(player);
			CraftButton.send(player);
			if (openGui) {
				player.level().getServer().execute(() -> OverviewGui.open(player, ""));
			}
			return true;
		}

		if (slot < 0 || slot >= menu.slots.size()) return false;
		boolean supported = menu == player.inventoryMenu || menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;
		if (!supported) return false;

		// 2) Middelklik (in survival) of dubbelklik op een leeg slot met lege cursor = sorteren
		boolean middle = input == ContainerInput.CLONE && !player.hasInfiniteMaterials();
		boolean doubleEmpty = input == ContainerInput.PICKUP_ALL && menu.getCarried().isEmpty() && !menu.getSlot(slot).hasItem();
		if (!middle && !doubleEmpty) return false;

		Slot clicked = menu.getSlot(slot);
		if (clicked.container instanceof Inventory) {
			Sorter.sortPlayer(player);
		} else if (menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu) {
			Sorter.sortContainer(player, menu, clicked.container);
		} else {
			return false;
		}
		resync(player);
		if (menu == player.inventoryMenu && CraftButton.active(player)) CraftButton.send(player);
		return true;
	}

	private static void resync(ServerPlayer player) {
		player.containerMenu.broadcastFullState();
		if (player.containerMenu != player.inventoryMenu) {
			player.inventoryMenu.broadcastChanges();
		}
	}
}
