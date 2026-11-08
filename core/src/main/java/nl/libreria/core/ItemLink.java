package nl.libreria.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Shift + right-click an item in your own inventory: it shows up in chat for everyone, as {@code <Jeffie> [Sharp Blade]}
 * in the colour of its rarity, and hovering it shows the real item (its enchantments, name, what's in a shulker box).
 *
 * <p>Vanilla does exactly the same for Shift + right-click as for Shift + left-click (move the stack), so nothing is
 * lost; a middle-click the client only sends in creative. Only slots of your own inventory: in a chest, Shift +
 * right-click on the chest's slots still moves items.
 */
public final class ItemLink {
	public static final ServerSetting.Toggle ON = Libreria.MODULE.serverToggle("item_links", "Items in chat", Items.ITEM_FRAME, true,
			"Shift + right-click an item in your", "inventory: it shows up in chat, and", "hovering it shows the item itself.");
	public static final Setting.Toggle MINE = Libreria.MODULE.toggle("item_link", "Shift + right-click: show in chat", true,
			Items.ITEM_FRAME, "Shift + right-click an item in your", "inventory and everyone sees it in chat.",
			"Off: Shift + right-click moves it, as", "in vanilla.").in(Domain.YOU, "Small comforts");

	/** Time between two items in chat, against spam. */
	static final long PAUSE_MS = 5000;

	static final Map<UUID, Long> LAST = new HashMap<>();
	/** How many items were shown in chat since the server started (for the tests). */
	public static int shown;

	private ItemLink() {
	}

	static void init() {
		ClickRouter.register(ItemLink::onClick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.player.getUUID()));
	}

	static boolean onClick(ServerPlayer player, AbstractContainerMenu menu, int slotIndex, ContainerInput input, int button) {
		if (input != ContainerInput.QUICK_MOVE || button != 1) return false;
		if (slotIndex < 0 || slotIndex >= menu.slots.size()) return false;
		// Libreria's own windows decide for themselves what their clicks do
		if (menu.getClass().getName().startsWith("eu.pb4.sgui")) return false;
		Slot slot = menu.getSlot(slotIndex);
		if (slot.container != player.getInventory() || !slot.hasItem()) return false;
		if (!ON.get() || !MINE.get(player)) return false;

		long now = System.currentTimeMillis();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < PAUSE_MS) {
			long wait = (PAUSE_MS - (now - last) + 999) / 1000;
			Hud.flash(player, Ui.text("Wait " + wait + (wait == 1 ? " second" : " seconds") + " to show another item", ChatFormatting.GRAY));
		} else {
			LAST.put(player.getUUID(), now);
			MinecraftServer server = player.level().getServer();
			server.getPlayerList().broadcastSystemMessage(line(player.getDisplayName(), slot.getItem()), false);
			shown++;
			Ui.click(player);
			Hud.flash(player, Ui.text("✔ Shown in chat", ChatFormatting.GREEN));
		}
		// The client already moved the stack on its own screen: put it back
		ClickRouter.resync(player);
		return true;
	}

	/** {@code <name> [Item] ×64}, as a chat line. The item part is Minecraft's own: rarity colour, hover = the item. */
	public static MutableComponent line(Component name, ItemStack stack) {
		MutableComponent what = Component.empty().append(stack.getDisplayName());
		if (stack.getCount() > 1) what.append(Ui.text(" ×" + stack.getCount(), ChatFormatting.GRAY));
		return Component.translatable("chat.type.text", name, what);
	}
}
