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
import nl.libreria.core.Domain;
import nl.libreria.core.Module;
import nl.libreria.core.Services;
import nl.libreria.core.Setting;
import nl.libreria.chests.gui.ChestsGui;
import nl.libreria.chests.gui.ItemSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChestsMod – een Libreria-module. Sorteren (knop in kisten, dubbelklik op een leeg slot), een kisten-database per speler met
 * iconen-GUI, afstand en een lichtstraal, noIndex per kist en snel opbergen in kisten in de buurt.
 */
public final class ChestsMod implements ModInitializer {
	public static final Logger LOG = LoggerFactory.getLogger("Libreria Chests");

	public static final Module MODULE = Module.register("chests", "Chests", Items.CHEST,
			"Sorting, searching your chests by icon", "and showing the way to the right chest.").in(Domain.ITEMS, "Search & sorting");
	public static final nl.libreria.core.ServerSetting.Number SEARCH_RADIUS = MODULE.serverNumber("search_radius", "Search range",
			Items.SPYGLASS, 256, 32, 512, 16, "blocks", "How far away chests still count when", "you search your items.");
	public static final Setting.Toggle SEARCH_BUTTON = MODULE.toggle("zoekknop", "Right-click beside your inventory searches", true, Items.SPYGLASS,
			"The magnifying glass beside your", "inventory: a right-click opens your", "items. Off: a right-click sorts.");
	public static final Setting.Toggle SORT_BUTTON = MODULE.toggle("sorteerknop", "Sort button in chests", true, Items.CHEST,
			"A ⇅ Sort button in the last empty", "slot of chests, barrels and boxes.", "Right-click: deposit matching items.");
	public static final Setting.Toggle DOUBLE_CLICK = MODULE.toggle("dubbelklik", "Double-click empty slot sorts", true, Items.HOPPER_MINECART,
			"Handy without a middle mouse button", "(for example on a laptop).");
	public static final Setting.Toggle NEW_MESSAGE = MODULE.toggle("melding", "Message for new chests", true, Items.PAPER,
			"\"Added to your chests\" when you open", "a chest for the first time.");
	public static final Setting.Toggle SNEAK_NOINDEX = MODULE.toggle("sneak", "{sneak} + {attack} skips a chest", true, Items.BARRIER,
			"With an empty hand on a chest: leave", "it out of search (or take it back in).");
	public static final Setting.Choice STACK_RADIUS = MODULE.choice("opbergen", "Quick stack range", Items.CHEST_MINECART, 1,
			new int[]{6, 10, 16, 24}, new String[]{"6 blocks", "10 blocks", "16 blocks", "24 blocks"},
			"How far your chests can be for quick stack.").in(Domain.ITEMS, "Quick stack & supply chests");

	public static final Setting.Toggle LABELS = MODULE.toggle("labels", "Labels above your chests", true, Items.ITEM_FRAME,
			"A small picture above each of your", "chests: what it holds most of.", "Only you see them.",
			"Off: only above the chests you give", "one in their options ({sneak}-click Sort).");
	public static final Setting.Toggle CRAFT = MODULE.toggle("craft_supply", "Craft from supply chests", true, Items.CRAFTING_TABLE,
			"Clicking a recipe in the recipe book", "takes what you miss from your", "supply chests.").in(Domain.ITEMS, "Quick stack & supply chests");
	public static final Setting.Toggle REFILL = MODULE.toggle("refill", "Refill from supply chests", true, Items.BUNDLE,
			"A used-up stack in your hotbar that your", "inventory has no more of is refilled", "from your supply chests.").in(Domain.ITEMS, "Quick stack & supply chests");
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
			Wanted.forget(handler.player);
			Store.unload(handler.player.getUUID());
		});
		ServerTickEvents.END_SERVER_TICK.register(ChestsMod::tick);
		UseBlockCallback.EVENT.register(Tracker::onUseBlock);
		AttackBlockCallback.EVENT.register(Tracker::onAttackBlock);
		CommandRegistrationCallback.EVENT.register(ChestCommands::register);

		// In het Libreria-menu en op de knop in het E-scherm
		// The buttons beside your inventory window (any click outside the window, with an empty hand: the game doesn't
		// say where, so the mouse button decides). Left: your items into your nearby chests. Right: search.
		java.util.function.Consumer<ServerPlayer> sort = p -> {
			if (Sorter.sortPlayer(p)) nl.libreria.core.Notify.whisper(p, "✔ Your inventory is sorted", net.minecraft.ChatFormatting.GREEN);
			else nl.libreria.core.Notify.whisper(p, "Your inventory is too full to sort", net.minecraft.ChatFormatting.GRAY);
		};
		Hub.setEdgeButton(QuickStack::run, p -> {
			if (!ChestSearch.fromInventory(p)) sort.accept(p);
		});
		MODULE.hubEntry(Domain.ITEMS, 10, Items.SPYGLASS, "Your items", ChestSearch::open,
				"Everything on you and in your chests, searchable. Click one and its chest lights up.",
				"Also: {inventory}, then right-click beside it.");
		MODULE.hubEntry(Domain.ITEMS, 20, Items.CHEST, "Your chests", ChestsGui::open,
				"See inside your chests from afar, find your way to them, leave one out of search.");
		MODULE.hubEntry(Domain.ITEMS, 30, Items.CHEST_MINECART, "Quick stack", QuickStack::run,
				"Puts what you carry into your nearby chests that already hold it (not your hotbar).",
				"Also: click the button beside your inventory.");
		MODULE.hubEntry(Domain.ITEMS, 40, Items.HOPPER, "Sort inventory", p -> {
			if (Sorter.sortPlayer(p)) nl.libreria.core.Notify.whisper(p, "✔ Your inventory is sorted", net.minecraft.ChatFormatting.GREEN);
			else nl.libreria.core.Notify.whisper(p, "Your inventory is too full to sort", net.minecraft.ChatFormatting.GRAY);
		},
				"Merges and sorts stacks; your hotbar stays as it is.",
				"Also: left-click the button beside your inventory.");

		MODULE.commands(
				"/chest – all your items (searchable)",
				"/chest find <text> – find an item by name",
				"/chest search <item> – find an item",
				"/chest where <item> – all its chests",
				"/chest sort – sort your inventory",
				"/chest noindex – leave a chest out of search, or take it back",
				"/chest stop – stop showing the way");

		ClickRouter.register(ChestsMod::onClick);
		Wanted.init();
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

	/**
	 * De ⇅-knop in kisten; dubbelklik op een leeg slot sorteert de container of je inventory. (Middelklik kan niet: in
	 * survival stuurt de game die in een venster niet naar de server.)
	 */
	private static boolean onClick(ServerPlayer player, AbstractContainerMenu menu, int slot, ContainerInput input, int button) {
		if (slot < 0 || slot >= menu.slots.size()) return false;
		if (SortButton.supports(menu) && SortButton.onClick(player, menu, slot, input, button)) return true;
		boolean supported = menu == player.inventoryMenu || menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;
		if (!supported) return false;
		boolean doubleEmpty = input == ContainerInput.PICKUP_ALL && menu.getCarried().isEmpty()
				&& !menu.getSlot(slot).hasItem() && DOUBLE_CLICK.get(player);
		if (!doubleEmpty) return false;

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
			if ((tick + player.getId()) % 20 == 0) {
				Labels.tick(player);
				Wanted.tick(player);
			}
		}
		if (tick % 1200 == 0) Store.saveDirty();
	}
}
