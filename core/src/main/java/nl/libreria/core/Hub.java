package nl.libreria.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import nl.libreria.core.gui.HubGui;
import org.jetbrains.annotations.Nullable;

/**
 * Buttons in your inventory screen (E). While the 2x2 crafting grid is empty the server shows things in it that only
 * your screen sees: the Libreria menu in the output slot, and module buttons (sort, quick stack …) in the grid, right
 * above the search button. Nothing is ever crafted: clicks are caught in {@link ClickRouter}. Putting a real item
 * into the grid still works; the buttons step aside until the grid is empty again.
 */
public final class Hub {
	/** A button in the crafting grid. {@code slot} = 1..4 (top left, top right, bottom left, bottom right). */
	private record Button(int slot, String model, Item fallback, String name, ChatFormatting color, List<String> lore,
			Consumer<ServerPlayer> action) {
	}

	private static @Nullable Consumer<ServerPlayer> primary;
	private static String primaryLabel = "Libreria menu";
	private static final List<String> HINTS = new ArrayList<>();
	private static final Map<Integer, Button> BUTTONS = new TreeMap<>();
	/** Cached pictures: [0] = without the resource pack, [1] = with it. */
	private static final ItemStack[] ICON = new ItemStack[2];
	private static final Map<Integer, ItemStack[]> BUTTON_ICONS = new TreeMap<>();

	private Hub() {
	}

	/** What a left-click on the Libreria button does (one module may claim it). Default: the Libreria menu. */
	public static void setPrimary(String label, Consumer<ServerPlayer> action) {
		primary = action;
		primaryLabel = label;
		ICON[0] = ICON[1] = null;
	}

	/** An extra line in the Libreria button's tooltip, e.g. "In chests: middle-click = sort". */
	public static void addHint(String hint) {
		HINTS.add(hint);
		ICON[0] = ICON[1] = null;
	}

	/**
	 * A button in the crafting grid. {@code model} = the button's picture in the Libreria resource pack (an item
	 * model like {@code libreria:gui/btn_sort}), {@code fallback} = the item shown without the pack.
	 */
	public static void addButton(int gridSlot, String model, Item fallback, String name, ChatFormatting color, Consumer<ServerPlayer> action,
			String... lore) {
		if (gridSlot < InventoryMenu.CRAFT_SLOT_START || gridSlot >= InventoryMenu.CRAFT_SLOT_END) {
			throw new IllegalArgumentException("Crafting grid slots are 1..4");
		}
		BUTTONS.put(gridSlot, new Button(gridSlot, model, fallback, name, color, List.of(lore), action));
		BUTTON_ICONS.remove(gridSlot);
	}

	static ItemStack icon(boolean pack) {
		int i = pack ? 1 : 0;
		if (ICON[i] == null) {
			List<Component> lore = new ArrayList<>();
			if (primary == null) {
				lore.add(Ui.text("Click: settings, waypoints, land map …", ChatFormatting.GRAY));
			} else {
				lore.add(line("Left-click: ", primaryLabel, ChatFormatting.YELLOW));
				lore.add(line("Right-click: ", "Libreria menu", ChatFormatting.AQUA));
			}
			for (String h : HINTS) lore.add(Ui.text(h, ChatFormatting.GRAY));
			ItemStack s = new ItemStack(pack ? Items.PAPER : Items.HOPPER);
			if (pack) {
				s.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("libreria", "gui/btn_menu"));
			} else {
				s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			s.set(DataComponents.CUSTOM_NAME, Component.literal("Libreria menu").withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GOLD).withBold(true)));
			s.set(DataComponents.LORE, new ItemLore(lore));
			ICON[i] = s;
		}
		return ICON[i];
	}

	private static ItemStack buttonIcon(Button b, boolean pack) {
		ItemStack[] cached = BUTTON_ICONS.computeIfAbsent(b.slot(), k -> new ItemStack[2]);
		int i = pack ? 1 : 0;
		if (cached[i] == null) {
			ItemStack s = new ItemStack(pack ? Items.PAPER : b.fallback());
			if (pack) s.set(DataComponents.ITEM_MODEL, Identifier.parse(b.model()));
			s.set(DataComponents.CUSTOM_NAME, Component.literal(b.name()).withStyle(st -> st.withItalic(false).withColor(b.color()).withBold(true)));
			List<Component> lore = new ArrayList<>();
			for (String l : b.lore()) lore.add(Ui.text(l, ChatFormatting.GRAY));
			s.set(DataComponents.LORE, new ItemLore(lore));
			cached[i] = s;
		}
		return cached[i];
	}

	private static Component line(String a, String b, ChatFormatting color) {
		return Ui.text(a, ChatFormatting.WHITE).append(Ui.text(b, color));
	}

	/** Active = the player wants the buttons, plays survival/adventure, and the crafting grid and output are really empty. */
	public static boolean active(ServerPlayer player) {
		if (player.isCreative() || player.isSpectator() || !Libreria.HUB_BUTTON.get(player)) return false;
		InventoryMenu menu = player.inventoryMenu;
		for (int i = InventoryMenu.RESULT_SLOT; i < InventoryMenu.CRAFT_SLOT_END; i++) {
			if (!menu.getSlot(i).getItem().isEmpty()) return false;
		}
		return true;
	}

	/** Is there a button (or the Libreria menu) in this slot of the inventory screen? */
	static boolean isButton(int slot) {
		return slot == InventoryMenu.RESULT_SLOT || BUTTONS.containsKey(slot);
	}

	/** Shows the buttons (only on the player's screen; the server's slots stay empty). */
	public static void send(ServerPlayer player) {
		InventoryMenu menu = player.inventoryMenu;
		boolean pack = Pack.has(player);
		player.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), InventoryMenu.RESULT_SLOT, icon(pack)));
		for (Button b : BUTTONS.values()) {
			player.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), b.slot(), buttonIcon(b, pack)));
		}
	}

	/** Takes the buttons away again: sends what's really in the output slot and the grid. */
	static void remove(ServerPlayer player) {
		InventoryMenu menu = player.inventoryMenu;
		for (int i = InventoryMenu.RESULT_SLOT; i < InventoryMenu.CRAFT_SLOT_END; i++) {
			player.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), i, menu.getSlot(i).getItem().copy()));
		}
	}

	static void click(ServerPlayer player, int slot, boolean right) {
		Button b = BUTTONS.get(slot);
		if (b != null) {
			Ui.click(player);
			b.action().accept(player);
		} else if (right || primary == null) {
			player.level().getServer().execute(() -> HubGui.open(player));
		} else {
			primary.accept(player);
		}
	}
}
