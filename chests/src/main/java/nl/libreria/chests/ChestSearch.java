package nl.libreria.chests;

import java.util.List;
import net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.world.inventory.RecipeBookType;
import nl.libreria.chests.gui.Locate;
import nl.libreria.chests.gui.ItemSearch;
import nl.libreria.core.Pack;
import nl.libreria.core.Ui;

/**
 * The ways into "Your items" (see {@link ItemSearch}): the search button in your inventory (where the recipe book
 * button is; the pack draws a magnifying glass there), /chest and /chest find.
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

	/**
	 * The recipe book button in your inventory was clicked (from the mixin). With the pack it is the search button:
	 * open your items and keep the recipe book closed. Returns true when it was handled.
	 */
	public static boolean onRecipeBookButton(ServerPlayer p, RecipeBookType type, boolean open, boolean filtering) {
		if (type != RecipeBookType.CRAFTING || !open || p.containerMenu != p.inventoryMenu) return false;
		if (!ChestsMod.SEARCH_BUTTON.get(p) || !Pack.has(p)) return false;
		// Tell the client the book is still closed, so the button keeps working as a button
		RecipeBookSettings settings = new RecipeBookSettings();
		settings.setFiltering(RecipeBookType.CRAFTING, filtering);
		p.connection.send(new ClientboundRecipeBookSettingsPacket(settings));
		Ui.click(p);
		ItemSearch.open(p, "", true);
		return true;
	}

	/**
	 * From the search screen: the recipe book of your inventory, open the next time you press E (the server can't open
	 * your inventory itself). Clicking the magnifying glass there closes the book again, the next click searches.
	 */
	public static void openRecipeBook(ServerPlayer p) {
		var book = p.getRecipeBook();
		boolean filtering = book.getBookSettings().isFiltering(RecipeBookType.CRAFTING);
		book.setBookSetting(RecipeBookType.CRAFTING, true, filtering);
		RecipeBookSettings settings = new RecipeBookSettings();
		settings.setOpen(RecipeBookType.CRAFTING, true);
		settings.setFiltering(RecipeBookType.CRAFTING, filtering);
		p.connection.send(new ClientboundRecipeBookSettingsPacket(settings));
		p.closeContainer();
		nl.libreria.core.gui.Toast.show(p, net.minecraft.world.item.Items.KNOWLEDGE_BOOK, nl.libreria.core.gui.Toast.INFO,
				"Recipe book ready", "Press {inventory}: it's open in your inventory");
	}
}
