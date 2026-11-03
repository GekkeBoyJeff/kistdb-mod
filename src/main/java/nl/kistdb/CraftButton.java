package nl.kistdb;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * De "knop" in het E-scherm: als het 2x2-crafting-grid leeg is, laat de server in het uitvak een icoon zien.
 * Klikken daarop wordt in {@link ClickHandler} afgevangen (er wordt dus nooit iets gecraft).
 */
public final class CraftButton {
	private static ItemStack icon;

	private CraftButton() {
	}

	static ItemStack icon() {
		if (icon == null) {
			ItemStack s = new ItemStack(Items.HOPPER);
			s.set(DataComponents.CUSTOM_NAME, Component.literal("KistDB").withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GOLD).withBold(true)));
			s.set(DataComponents.LORE, new ItemLore(List.of(
					line("Linksklik: ", "inventory sorteren", ChatFormatting.YELLOW),
					line("Rechtsklik: ", "kisten zoeken", ChatFormatting.AQUA),
					line("In een kist: ", "middelklik = sorteren", ChatFormatting.GRAY))));
			s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			icon = s;
		}
		return icon;
	}

	private static Component line(String a, String b, ChatFormatting color) {
		return Component.literal(a).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.WHITE))
				.append(Component.literal(b).withStyle(st -> st.withItalic(false).withColor(color)));
	}

	/** Actief = survival/adventure en het crafting-grid plus uitvak zijn echt leeg. */
	public static boolean active(ServerPlayer player) {
		if (player.isCreative() || player.isSpectator()) return false;
		InventoryMenu menu = player.inventoryMenu;
		for (int i = InventoryMenu.RESULT_SLOT; i < InventoryMenu.CRAFT_SLOT_END; i++) {
			if (!menu.getSlot(i).getItem().isEmpty()) return false;
		}
		return true;
	}

	/** Stuurt het icoon (alleen naar de client; de server-slot blijft leeg). */
	public static void send(ServerPlayer player) {
		InventoryMenu menu = player.inventoryMenu;
		player.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), InventoryMenu.RESULT_SLOT, icon()));
	}

	static void refresh(ServerPlayer player) {
		if (player.containerMenu == player.inventoryMenu && active(player)) {
			send(player);
		}
	}
}
