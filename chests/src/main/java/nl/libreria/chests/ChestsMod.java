package nl.libreria.chests;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import nl.libreria.core.ClickRouter;
import nl.libreria.core.Hub;
import nl.libreria.core.Module;
import nl.libreria.core.Services;
import nl.libreria.core.Setting;
import nl.libreria.chests.gui.ChestsGui;
import nl.libreria.chests.gui.ItemSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChestsMod – een Libreria-module. Sorteren (knop in E, middelklik in kisten), een kisten-database per speler met
 * iconen-GUI, afstand en een lichtstraal, noIndex per kist en snel opbergen in kisten in de buurt.
 */
public final class ChestsMod implements ModInitializer {
	public static final Logger LOG = LoggerFactory.getLogger("Libreria Chests");

	public static final Module MODULE = Module.register("chests", "Chests", Items.CHEST,
			"Sorting, searching your chests by icon", "and a beam to the right chest.");
	public static final nl.libreria.core.ServerSetting.Number SEARCH_RADIUS = MODULE.serverNumber("search_radius", "Search range",
			Items.SPYGLASS, 256, 32, 512, 16, "blocks", "How far away chests still count when", "you search your items.");
	public static final Setting.Toggle SEARCH_BUTTON = MODULE.toggle("zoekknop", "Search button in your inventory", true, Items.SPYGLASS,
			"With the Libreria pack, the recipe book", "button in your inventory becomes a", "magnifying glass that opens your items.");
	public static final Setting.Toggle SORT_BUTTON = MODULE.toggle("sorteerknop", "Sort button in chests", true, Items.CHEST,
			"A ⇅ Sort button in the last empty", "slot of chests, barrels and boxes.", "Right-click: deposit matching items.");
	public static final Setting.Toggle MIDDLE_CLICK = MODULE.toggle("middelklik", "Middle-click sorts", true, Items.HOPPER,
			"Middle-click sorts the open chest,", "or your inventory if you click there.");
	public static final Setting.Toggle DOUBLE_CLICK = MODULE.toggle("dubbelklik", "Double-click empty slot sorts", true, Items.HOPPER_MINECART,
			"Handy without a middle mouse button", "(for example on a laptop).");
	public static final Setting.Toggle NEW_MESSAGE = MODULE.toggle("melding", "Message for new chests", true, Items.PAPER,
			"\"Added to your chest index\" when you", "open a chest for the first time.");
	public static final Setting.Toggle SNEAK_NOINDEX = MODULE.toggle("sneak", "Sneak + left-click = noIndex", true, Items.BARRIER,
			"With an empty hand on a chest: exclude", "it from search (or include it again).");
	public static final Setting.Choice STACK_RADIUS = MODULE.choice("opbergen", "Quick stack: range", Items.CHEST_MINECART, 1,
			new int[]{6, 10, 16, 24}, new String[]{"6 blocks", "10 blocks", "16 blocks", "24 blocks"},
			"How far chests can be for quick stack.");

	public static final Setting.Toggle LABELS = MODULE.toggle("labels", "Labels above your chests", true, Items.ITEM_FRAME,
			"A small picture above each of your", "chests: what it holds most of.", "Only you see them.");
	public static final Setting.Toggle CRAFT = MODULE.toggle("craft_supply", "Craft from supply chests", true, Items.CRAFTING_TABLE,
			"Clicking a recipe in the recipe book", "takes what you miss from your", "supply chests.");
	public static final Setting.Toggle REFILL = MODULE.toggle("refill", "Refill your hotbar", true, Items.BUNDLE,
			"A used-up stack in your hotbar is", "refilled from your inventory, then", "from your supply chests.");
	public static final nl.libreria.core.ServerSetting.Number SUPPLY_RANGE = MODULE.serverNumber("supply_range", "Supply chest range",
			Items.CHEST, 24, 4, 64, 4, "blocks", "How near supply chests must be for", "crafting and hotbar refills.");

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(Store::init);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Labels.clearAll();
			Store.saveAll();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Tracker.forget(handler.player);
			SortButton.forget(handler.player);
			Labels.forget(handler.player.getUUID());
			Refill.forget(handler.player.getUUID());
			Store.unload(handler.player.getUUID());
		});
		ServerTickEvents.END_SERVER_TICK.register(ChestsMod::tick);
		UseBlockCallback.EVENT.register(Tracker::onUseBlock);
		AttackBlockCallback.EVENT.register(Tracker::onAttackBlock);
		CommandRegistrationCallback.EVENT.register(ChestCommands::register);

		// In het Libreria-menu en op de knop in het E-scherm
		// The sort button beside your inventory window (left click sorts, right click quick-stacks into nearby chests)
		Hub.setEdgeButton(Sorter::sortPlayer, QuickStack::run);
		MODULE.hubEntry(Items.SPYGLASS, "Your items", ChatFormatting.AQUA, ChestSearch::open,
				"Everything on you and in your chests,", "searchable; click one and its chest", "lights up. Also: the magnifying glass", "in your inventory.");
		MODULE.hubEntry(Items.CHEST, "My chests", ChatFormatting.GOLD, ChestsGui::open,
				"See inside your chests from afar,", "beam to them, skip them in search.");
		MODULE.hubEntry(Items.CHEST_MINECART, "Quick stack", ChatFormatting.GREEN, QuickStack::run,
				"Puts inventory items into nearby chests", "that already have them (not your hotbar).");
		MODULE.hubEntry(Items.HOPPER, "Sort inventory", ChatFormatting.YELLOW, Sorter::sortPlayer,
				"Merges and sorts stacks.", "Your hotbar stays as it is.");

		MODULE.commands(
				"/chest – all your items (searchable)",
				"/chest find <text> – find an item by name",
				"/chest search <item> – find an item",
				"/chest where <item> – all its chests",
				"/chest sort – sort your inventory",
				"/chest noindex – skip/unskip a chest",
				"/chest stop – stop the beam");

		ClickRouter.register(ChestsMod::onClick);
		// Voor andere modules: "waar ligt dit item?" en het zoekmenu
		Services.provideItemFinder((player, item) -> {
			var hits = Search.find(player, item.getItem());
			nl.libreria.chests.gui.Locate.go(player, item, hits);
			return !hits.isEmpty();
		});
		Services.provideMenu(Services.MENU_CHESTS, p -> ItemSearch.open(p, ""));
		Services.provideMenu(Services.ACTION_SORT, Sorter::sortPlayer);
		ChestsTests.register();
		LOG.info("Libreria Chests loaded");
	}

	/** De ⇅-knop in kisten; middelklik (of dubbelklik op een leeg slot) sorteert de container of je inventory. */
	private static boolean onClick(ServerPlayer player, AbstractContainerMenu menu, int slot, ContainerInput input, int button) {
		if (slot < 0 || slot >= menu.slots.size()) return false;
		if (SortButton.supports(menu) && SortButton.onClick(player, menu, slot, input, button)) return true;
		boolean supported = menu == player.inventoryMenu || menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;
		if (!supported) return false;
		boolean middle = input == ContainerInput.CLONE && !player.hasInfiniteMaterials() && MIDDLE_CLICK.get(player);
		boolean doubleEmpty = input == ContainerInput.PICKUP_ALL && menu.getCarried().isEmpty()
				&& !menu.getSlot(slot).hasItem() && DOUBLE_CLICK.get(player);
		if (!middle && !doubleEmpty) return false;

		Slot clicked = menu.getSlot(slot);
		if (clicked.container instanceof Inventory) {
			Sorter.sortPlayer(player);
		} else if (menu != player.inventoryMenu) {
			Sorter.sortContainer(player, menu, clicked.container);
		} else {
			return false;
		}
		ClickRouter.resync(player);
		return true;
	}

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Tracker.tick(player, tick);
			SortButton.tick(player);
			Refill.tick(player);
			if ((tick + player.getId()) % 20 == 0) Labels.tick(player);
		}
		if (tick % 1200 == 0) Store.saveDirty();
	}
}
