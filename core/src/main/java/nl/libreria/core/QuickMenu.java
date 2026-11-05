package nl.libreria.core;

import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import nl.libreria.core.dialog.Dialogs;
import nl.libreria.core.gui.SettingsWindow;

/**
 * The start screen (key G, and the Libreria button in the pause menu) is a dialog in the data pack
 * ({@code data/libreria/dialog/quick_menu.json}), so it opens instantly. Its buttons send {@code libreria:quick} with
 * where to go: one of the four domains ({@code items}, {@code land}, {@code progress}, {@code me}), the settings or
 * Help. Other dialogs may send a menu key too ({@code map}, {@code waypoints}…).
 */
public final class QuickMenu {
	private QuickMenu() {
	}

	static void init() {
		Dialogs.on("quick", (player, values) -> open(player, values.text("go", "")));
	}

	static void open(ServerPlayer player, String what) {
		Domain d = Domain.byWord(what);
		if (d != null) {
			Start.domain(player, d);
			return;
		}
		switch (what) {
			case "settings" -> SettingsWindow.open(player);
			case "start" -> Welcome.guide(player);
			case "links" -> Links.open(player);
			case "chronicle" -> Chronicle.open(player);
			case "menu", "" -> Start.open(player);
			default -> {
				Consumer<ServerPlayer> opener = Services.menu(key(what)).orElse(null);
				if (opener != null) opener.accept(player);
				else Notify.warn(player, Items.BARRIER, "Not on this server", "That part of Libreria isn't installed here.");
			}
		}
	}

	private static String key(String what) {
		return switch (what) {
			case "map" -> Services.MENU_LAND_MAP;
			case "search" -> Services.MENU_CHESTS;
			case "sort" -> Services.ACTION_SORT;
			case "waypoints" -> Services.MENU_WAYPOINTS;
			case "achievements" -> Services.MENU_ACHIEVEMENTS;
			case "effects" -> Services.MENU_EFFECTS;
			case "journal" -> Services.MENU_JOURNAL;
			case "pets" -> Services.MENU_PETS;
			default -> what;
		};
	}
}
