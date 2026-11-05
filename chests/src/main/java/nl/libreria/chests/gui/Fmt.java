package nl.libreria.chests.gui;

import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import nl.libreria.chests.Store;
import nl.libreria.core.Guide;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Menu;

/** Small shared bits for the chests menus: numbers, distances, the black toolbar row. */
final class Fmt {
	private Fmt() {
	}

	/** 12345 → "12,345". */
	static String number(long n) {
		return String.format(Locale.US, "%,d", n);
	}

	/** "1 chest" / "3 chests". */
	static String count(long n, String one, String many) {
		return number(n) + " " + (n == 1 ? one : many);
	}

	/** "12 m ↗" – the arrow points the way you were looking when you opened the menu. */
	static String distance(ServerPlayer player, Store.Entry e, double distance) {
		return Ui.meters(distance) + "  " + Guide.arrow(player, Vec3.atCenterOf(e.pos()));
	}

	static String where(Store.Entry e) {
		return e.x + " " + e.y + " " + e.z;
	}

	/** Fills the still-empty slots of one row with black glass, so a toolbar stands apart from the content. */
	static void toolbar(SimpleGui gui, int row) {
		// With the pack, empty slots melt into the panel (no dark squares between the buttons)
		if (nl.libreria.core.Pack.has(gui.getPlayer())) return;
		for (int i = row * 9; i < row * 9 + 9; i++) {
			if (gui.getGuiElement(i) == null) gui.setSlot(i, Menu.pane("black"));
		}
	}

	/** Clears the whole bottom row (before rebuilding page buttons that may no longer apply). */
	static void clearRow(SimpleGui gui, int row) {
		for (int i = row * 9; i < row * 9 + 9; i++) gui.clearSlot(i);
	}
}
