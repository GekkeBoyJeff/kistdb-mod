package nl.libreria.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import nl.libreria.core.dialog.Dialogs;
import nl.libreria.core.gui.HubGui;
import nl.libreria.core.gui.SettingsWindow;
import nl.libreria.core.gui.Toast;

/**
 * The quick menu (key G, and the Libreria button in the pause menu) is a dialog in the data pack
 * ({@code data/libreria/dialog/quick_menu.json}), so it opens instantly. Its buttons send {@code libreria:quick} with
 * the name of what to open; this class opens it, or says kindly that it isn't on this server.
 */
public final class QuickMenu {
	/** Button name → what it opens. Modules fill most of these in through {@link Services#provideMenu}. */
	private static final Map<String, String> SERVICES = new LinkedHashMap<>();

	static {
		SERVICES.put("map", Services.MENU_LAND_MAP);
		SERVICES.put("items", Services.MENU_CHESTS);
		SERVICES.put("sort", Services.ACTION_SORT);
		SERVICES.put("waypoints", Services.MENU_WAYPOINTS);
		SERVICES.put("achievements", Services.MENU_ACHIEVEMENTS);
		SERVICES.put("effects", Services.MENU_EFFECTS);
	}

	private QuickMenu() {
	}

	static void init() {
		Dialogs.on("quick", (player, values) -> open(player, values.text("go", "")));
	}

	static void open(ServerPlayer player, String what) {
		switch (what) {
			case "settings" -> SettingsWindow.open(player);
			case "links" -> Links.open(player);
			case "start" -> Welcome.guide(player);
			case "menu" -> HubGui.open(player);
			default -> {
				String key = SERVICES.get(what);
				Consumer<ServerPlayer> opener = key == null ? null : Services.menu(key).orElse(null);
				if (opener != null) opener.accept(player);
				else Toast.show(player, Items.BARRIER, Toast.WARN, "Not on this server", "That part of Libreria isn't installed here.");
			}
		}
	}
}
