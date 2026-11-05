package nl.libreria.chests;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import nl.libreria.chests.gui.Locate;
import nl.libreria.chests.gui.ItemSearch;

/**
 * The ways into "Your items" (see {@link ItemSearch}): the button beside your inventory (right-click, the magnifying
 * glass under the deposit arrows), /chest and /chest find.
 */
public final class ChestSearch {
	private ChestSearch() {
	}

	/** Your items. */
	public static void open(ServerPlayer p) {
		ItemSearch.open(p, "");
	}

	/** /chest find &lt;text&gt;: exactly one kind in your chests lights it up at once, otherwise the list of matches. */
	public static int find(ServerPlayer p, String raw) {
		String q = raw.startsWith("q:") ? raw.substring(2) : raw;
		q = q.trim();
		if (!q.isEmpty()) {
			List<Search.Stock> found = Search.stock(p, q);
			if (found.size() == 1 && !found.getFirst().hits().isEmpty()) {
				Search.Stock s = found.getFirst();
				Locate.go(p, s.sample(), s.hits());
				return 1;
			}
		}
		ItemSearch.open(p, q);
		return 1;
	}

	/** From the button beside your inventory (right-click): your items, if that's on. */
	public static boolean fromInventory(ServerPlayer p) {
		if (!ChestsMod.SEARCH_BUTTON.get(p)) return false;
		ItemSearch.open(p, "", true);
		return true;
	}
}
