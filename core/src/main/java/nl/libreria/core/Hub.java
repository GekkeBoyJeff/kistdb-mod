package nl.libreria.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import nl.libreria.core.gui.HubGui;
import org.jetbrains.annotations.Nullable;

/**
 * De Libreria-knop in het E-scherm: als het 2x2-crafting-grid leeg is, toont de server in het uitvak een icoon.
 * Linksklik = de hoofdactie (bv. sorteren als ChestsMod er is), rechtsklik = het Libreria-menu.
 * Er wordt nooit iets gecraft: de klik wordt in {@link ClickRouter} afgevangen.
 */
public final class Hub {
	private static @Nullable Consumer<ServerPlayer> primary;
	private static String primaryLabel = "Libreria-menu";
	private static final List<String> HINTS = new ArrayList<>();
	private static @Nullable ItemStack icon;

	private Hub() {
	}

	/** Wat een linksklik op de knop doet (één module mag dit claimen, bv. ChestsMod: sorteren). */
	public static void setPrimary(String label, Consumer<ServerPlayer> action) {
		primary = action;
		primaryLabel = label;
		icon = null;
	}

	/** Extra regel in de tooltip van de knop, bv. "In een kist: middelklik = sorteren". */
	public static void addHint(String hint) {
		HINTS.add(hint);
		icon = null;
	}

	static ItemStack icon() {
		if (icon == null) {
			List<Component> lore = new ArrayList<>();
			lore.add(line("Linksklik: ", primaryLabel, ChatFormatting.YELLOW));
			lore.add(line("Rechtsklik: ", "Libreria-menu", ChatFormatting.AQUA));
			for (String h : HINTS) lore.add(Ui.text(h, ChatFormatting.GRAY));
			ItemStack s = new ItemStack(Items.HOPPER);
			s.set(DataComponents.CUSTOM_NAME, Component.literal("Libreria").withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GOLD).withBold(true)));
			s.set(DataComponents.LORE, new ItemLore(lore));
			s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			icon = s;
		}
		return icon;
	}

	private static Component line(String a, String b, ChatFormatting color) {
		return Ui.text(a, ChatFormatting.WHITE).append(Ui.text(b, color));
	}

	/** Actief = de speler wil de knop, speelt survival/adventure en het crafting-grid plus uitvak zijn echt leeg. */
	public static boolean active(ServerPlayer player) {
		if (player.isCreative() || player.isSpectator() || !Libreria.HUB_BUTTON.get(player)) return false;
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

	/** Haalt het icoon weg (stuurt de echte, lege inhoud). */
	static void remove(ServerPlayer player) {
		InventoryMenu menu = player.inventoryMenu;
		player.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), InventoryMenu.RESULT_SLOT, ItemStack.EMPTY));
	}

	static void click(ServerPlayer player, boolean right) {
		if (right || primary == null) {
			player.level().getServer().execute(() -> HubGui.open(player));
		} else {
			primary.accept(player);
		}
	}
}
