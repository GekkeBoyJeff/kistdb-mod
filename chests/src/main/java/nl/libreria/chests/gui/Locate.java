package nl.libreria.chests.gui;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import nl.libreria.chests.Beam;
import nl.libreria.chests.Search;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Toast;

/**
 * Lights up the nearest chest with an item and beams there, with a short card at the top saying how much you have and
 * where. Every chest with it: right-click the item in the search screen.
 */
public final class Locate {
	private Locate() {
	}

	public static void go(ServerPlayer player, ItemStack item, List<Search.Hit> hits) {
		if (hits.isEmpty()) {
			// None in your chests: what you carry of it still lights up
			nl.libreria.chests.Wanted.start(player, item);
			Toast.show(player, item, Toast.BAD, Component.empty().append(Ui.text("No ", ChatFormatting.WHITE))
							.append(item.getHoverName().copy().withStyle(ChatFormatting.WHITE)).append(Ui.text(" in your chests", ChatFormatting.WHITE)),
					Ui.text("Within " + (int) Search.radius() + " m. Open a chest once and Libreria remembers it.", ChatFormatting.GRAY));
			return;
		}
		Search.Hit near = hits.getFirst();
		Beam.to(player, player.level(), near.entry().pos(), item, near.count());
		long total = hits.stream().mapToLong(Search.Hit::count).sum();
		String where = Ui.meters(near.distance()) + " away, in a " + Search.kind(near.entry()).toLowerCase(Locale.ROOT)
				+ (hits.size() > 1 ? " · " + Fmt.count(hits.size(), "chest", "chests") + " in all" : "");
		Toast.show(player, item, Toast.INFO, Component.empty().append(Ui.text(Fmt.number(total) + " × ", ChatFormatting.GREEN))
				.append(item.getHoverName().copy().withStyle(ChatFormatting.WHITE)), Ui.text(where, ChatFormatting.GRAY));
	}
}
