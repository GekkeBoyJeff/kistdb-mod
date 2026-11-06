package nl.libreria.chests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import nl.libreria.core.ClickRouter;
import nl.libreria.core.Hud;
import nl.libreria.core.Ui;
import nl.libreria.chests.gui.ChestOptions;

/**
 * A "⇅ Sort" button in every open chest, barrel, ender chest or shulker box. Same trick as the Libreria button in the
 * E screen: the server only tells the client there is an item in the last empty container slot; the real slot stays
 * empty. Clicking it with an empty cursor sorts (left) or deposits matching items (right); with an item on the cursor,
 * vanilla simply puts the item there and the button moves to the next empty slot. A full container has no button.
 */
public final class SortButton {
	/** What the client of one player currently shows. */
	private static final class Shown {
		final AbstractContainerMenu menu;
		int slot = -1;
		int stateId = -1;
		boolean dirty = true;

		Shown(AbstractContainerMenu menu) {
			this.menu = menu;
		}
	}

	private static final Map<UUID, Shown> SHOWN = new HashMap<>();

	private SortButton() {
	}

	/** Chests (all kinds, also double, trapped, copper and ender chests), barrels and shulker boxes. */
	static boolean supports(AbstractContainerMenu menu) {
		return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;
	}

	private static boolean allowed(ServerPlayer player) {
		return !player.isCreative() && !player.isSpectator() && ChestsMod.SORT_BUTTON.get(player);
	}

	/** Number of container slots at the start of the menu (before the player's own inventory). */
	static int containerSlots(AbstractContainerMenu menu) {
		int n = 0;
		while (n < menu.slots.size() && !(menu.slots.get(n).container instanceof Inventory)) n++;
		return n;
	}

	/** The last (bottom-right-most) empty container slot, or -1 if the container is full. */
	static int buttonSlot(AbstractContainerMenu menu) {
		for (int i = containerSlots(menu) - 1; i >= 0; i--) {
			if (!menu.getSlot(i).hasItem()) return i;
		}
		return -1;
	}

	// ---------------------------------------------------------------- showing

	/** Every tick: keep the button where it belongs (it moves when its slot is used or another slot frees up). */
	static void tick(ServerPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		UUID id = player.getUUID();
		Shown s = SHOWN.get(id);
		if (!supports(menu) || !allowed(player)) {
			if (s != null) {
				SHOWN.remove(id);
				// Same chest still open (e.g. switched to creative): take the button away
				if (s.menu == menu) hide(player, menu, s.slot);
			}
			return;
		}
		if (s == null || s.menu != menu) {
			s = new Shown(menu);
			SHOWN.put(id, s);
		}
		// Anything the server sent (stateId changed) may have overwritten the fake item, so send it again then
		if (s.dirty || s.stateId != menu.getStateId() || s.slot != buttonSlot(menu)) place(player, menu, s);
	}

	private static void place(ServerPlayer player, AbstractContainerMenu menu, Shown s) {
		int target = buttonSlot(menu);
		// The old spot is empty again on the server: show it as empty on the client too
		if (s.slot >= 0 && s.slot != target) hide(player, menu, s.slot);
		if (target >= 0) send(player, menu, target, icon(player, menu));
		s.slot = target;
		s.stateId = menu.getStateId();
		s.dirty = false;
	}

	private static void hide(ServerPlayer player, AbstractContainerMenu menu, int slot) {
		if (slot >= 0 && slot < containerSlots(menu) && !menu.getSlot(slot).hasItem()) send(player, menu, slot, ItemStack.EMPTY);
	}

	/** Client only: the server slot itself stays empty. */
	private static void send(ServerPlayer player, AbstractContainerMenu menu, int slot, ItemStack stack) {
		player.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), slot, stack));
	}

	static void forget(ServerPlayer player) {
		SHOWN.remove(player.getUUID());
	}

	private static String kind(AbstractContainerMenu menu) {
		if (menu instanceof ShulkerBoxMenu) return "box";
		Container c = menu.getSlot(0).container;
		if (c instanceof BarrelBlockEntity) return "barrel";
		if (c instanceof PlayerEnderChestContainer) return "ender chest";
		return "chest";
	}

	static ItemStack icon(ServerPlayer player, AbstractContainerMenu menu) {
		String kind = kind(menu);
		List<Component> lore = new ArrayList<>();
		lore.add(Ui.text("▸ Click: sort this " + kind, ChatFormatting.YELLOW));
		lore.add(Ui.text("▸ Right-click: deposit matching items", ChatFormatting.YELLOW));
		if (!(menu.getSlot(0).container instanceof PlayerEnderChestContainer)) {
			lore.add(Ui.text("▸ Shift-click: options (supply chest, label)", ChatFormatting.YELLOW));
		}
		lore.add(Component.empty());
		lore.add(Ui.text("Deposit: adds items it already has", ChatFormatting.GRAY));
		lore.add(Ui.text("from your inventory, not the hotbar.", ChatFormatting.GRAY));
		if (ChestsMod.DOUBLE_CLICK.get(player)) lore.add(Ui.text("Double-click an empty slot also sorts.", ChatFormatting.GRAY));
		lore.add(Ui.text("Hide it: Settings › Items › Search & sorting", ChatFormatting.DARK_GRAY));
		ItemStack s = new ItemStack(Items.HOPPER);
		s.set(DataComponents.CUSTOM_NAME, Ui.text("⇅ Sort", ChatFormatting.GOLD).withStyle(ChatFormatting.BOLD));
		s.set(DataComponents.LORE, new ItemLore(lore));
		s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return s;
	}

	// ---------------------------------------------------------------- clicking

	/** Called from the chests click hook. @return true if the click was the button (vanilla then ignores it) */
	static boolean onClick(ServerPlayer player, AbstractContainerMenu menu, int slot, ContainerInput input, int button) {
		Shown s = SHOWN.get(player.getUUID());
		if (s == null || s.menu != menu) return false;
		// Whatever happens with this click, put the button back right after it
		s.dirty = true;
		if (slot != s.slot || slot < 0) return false;
		if (input != ContainerInput.PICKUP && input != ContainerInput.QUICK_MOVE) return false;
		// Carrying something, or the slot really has an item: a normal click, vanilla handles it
		if (!menu.getCarried().isEmpty() || menu.getSlot(slot).hasItem()) return false;

		Ui.click(player);
		Container container = menu.getSlot(slot).container;
		if (input == ContainerInput.QUICK_MOVE) {
			// Shift-click: this chest's options (supply chest, label, search)
			BlockPos key = Tracker.openChest(player);
			if (key != null) {
				ChestOptions.open(player, player.level(), key);
				return true;
			}
		}
		if (button == 1) {
			int moved = deposit(player, container);
			if (moved > 0) {
				Ui.sound(player, SoundEvents.ITEM_PICKUP, 0.6f, 0.8f);
				Hud.flash(player, Component.literal("✔ Deposited " + moved + (moved == 1 ? " item" : " items"))
						.withStyle(ChatFormatting.GREEN));
			} else {
				Hud.flash(player, Component.literal("Nothing to deposit: this " + kind(menu) + " has none of your items")
						.withStyle(ChatFormatting.GRAY));
			}
		} else if (!Sorter.sortContainer(player, menu, container)) {
			Hud.flash(player, Component.literal("Couldn't sort this " + kind(menu)).withStyle(ChatFormatting.GRAY));
		}
		ClickRouter.resync(player);
		place(player, menu, s);
		return true;
	}

	/**
	 * Moves items from the player's main inventory (not the hotbar) into {@code container}, but only items it already
	 * holds. @return the number of items moved
	 */
	static int deposit(ServerPlayer player, Container container) {
		Inventory inv = player.getInventory();
		List<Container> target = List.of(container);
		int moved = 0;
		for (int slot = 9; slot < 36; slot++) {
			ItemStack stack = inv.getItem(slot);
			if (stack.isEmpty() || !QuickStack.contains(target, stack)) continue;
			moved += QuickStack.insert(target, stack);
			inv.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
		}
		if (moved > 0) inv.setChanged();
		return moved;
	}
}
