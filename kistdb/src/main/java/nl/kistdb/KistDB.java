package nl.kistdb;

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
import nl.handig.kern.ClickRouter;
import nl.handig.kern.Hub;
import nl.handig.kern.Module;
import nl.handig.kern.Services;
import nl.handig.kern.Setting;
import nl.kistdb.gui.ChestsGui;
import nl.kistdb.gui.OverviewGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KistDB – een Handig-module. Sorteren (knop in E, middelklik in kisten), een kisten-database per speler met
 * iconen-GUI, afstand en een lichtstraal, noIndex per kist en snel opbergen in kisten in de buurt.
 */
public final class KistDB implements ModInitializer {
	public static final Logger LOG = LoggerFactory.getLogger("KistDB");

	public static final Module MODULE = Module.register("kistdb", "KistDB", Items.CHEST,
			"Sorteren, je kisten doorzoeken met iconen", "en een straal naar de juiste kist.");
	public static final Setting.Toggle MIDDLE_CLICK = MODULE.toggle("middelklik", "Middelklik sorteert", true, Items.HOPPER,
			"Middelklik in een kist sorteert die kist,", "in je eigen inventory je inventory.");
	public static final Setting.Toggle DOUBLE_CLICK = MODULE.toggle("dubbelklik", "Dubbelklik op leeg slot sorteert", true, Items.HOPPER_MINECART,
			"Handig zonder middelste muisknop", "(bijvoorbeeld op een laptop).");
	public static final Setting.Toggle NEW_MESSAGE = MODULE.toggle("melding", "Melding bij een nieuwe kist", true, Items.PAPER,
			"\"Kist toegevoegd aan KistDB\" als je", "een kist voor het eerst opent.");
	public static final Setting.Toggle SNEAK_NOINDEX = MODULE.toggle("sneak", "Sneak + linksklik = noIndex", true, Items.BARRIER,
			"Met lege hand op een kist: sluit hem", "uit van zoeken (of zet hem weer aan).");
	public static final Setting.Choice STACK_RADIUS = MODULE.choice("opbergen", "Snel opbergen: afstand", Items.CHEST_MINECART, 1,
			new int[]{6, 10, 16, 24}, new String[]{"6 blokken", "10 blokken", "16 blokken", "24 blokken"},
			"Hoe ver kisten mogen staan bij snel opbergen.");

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(Store::init);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Store.saveAll());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Tracker.forget(handler.player);
			Store.unload(handler.player.getUUID());
		});
		ServerTickEvents.END_SERVER_TICK.register(KistDB::tick);
		UseBlockCallback.EVENT.register(Tracker::onUseBlock);
		AttackBlockCallback.EVENT.register(Tracker::onAttackBlock);
		CommandRegistrationCallback.EVENT.register(KistCommands::register);

		// In het Handig-menu en op de knop in het E-scherm
		Hub.setPrimary("inventory sorteren", Sorter::sortPlayer);
		Hub.addHint("In een kist: middelklik = sorteren");
		MODULE.hubEntry(Items.SPYGLASS, "Kisten zoeken", ChatFormatting.AQUA, p -> OverviewGui.open(p, ""),
				"Alles in je kisten, met iconen.", "Klik een item: straal naar de kist.");
		MODULE.hubEntry(Items.CHEST, "Mijn kisten", ChatFormatting.GOLD, ChestsGui::open,
				"Inhoud op afstand bekijken,", "straal ernaartoe, noIndex aan/uit.");
		MODULE.hubEntry(Items.CHEST_MINECART, "Snel opbergen", ChatFormatting.GREEN, QuickStack::run,
				"Items uit je inventory naar kisten in de", "buurt die dat item al hebben (niet je hotbar).");
		MODULE.hubEntry(Items.HOPPER, "Sorteer inventory", ChatFormatting.YELLOW, Sorter::sortPlayer,
				"Stapels samenvoegen en sorteren.", "Je hotbar blijft zoals hij is.");

		ClickRouter.register(KistDB::onClick);
		// Voor andere modules: "waar ligt dit item?" en het zoekmenu
		Services.provideItemFinder((player, item) -> {
			var hits = Search.find(player, item.getItem());
			nl.kistdb.gui.Locate.go(player, item, hits);
			return !hits.isEmpty();
		});
		Services.provideMenu(Services.MENU_CHESTS, p -> OverviewGui.open(p, ""));
		KistTests.register();
		LOG.info("KistDB geladen");
	}

	/** Middelklik (of dubbelklik op een leeg slot) sorteert de container of je inventory. */
	private static boolean onClick(ServerPlayer player, AbstractContainerMenu menu, int slot, ContainerInput input, int button) {
		if (slot < 0 || slot >= menu.slots.size()) return false;
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
		}
		if (tick % 1200 == 0) Store.saveDirty();
	}
}
