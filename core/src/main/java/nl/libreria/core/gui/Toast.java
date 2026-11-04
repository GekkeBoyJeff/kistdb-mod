package nl.libreria.core.gui;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import nl.libreria.core.Ui;

/**
 * A short notification card at the top of the screen, instead of a line in chat: "Chunk claimed", "Beaming to your
 * chest"… It goes away by itself after a few seconds; a newer toast replaces an older one. Cards with a higher
 * priority (the land map, an achievement banner) stay on top of it.
 */
public final class Toast {
	/** Accent colours (the gem on the card). */
	public static final int GOOD = 0x55FF55, INFO = 0x55FFFF, WARN = 0xFFAA00, BAD = 0xFF5555;

	static final String CHANNEL = "toast";
	static final int PRIORITY = 40;
	private static final int TICKS = 70;

	private Toast() {
	}

	public static void show(ServerPlayer player, Item icon, int accent, String title, String subtitle) {
		show(player, new ItemStack(icon), accent, Ui.text(title, ChatFormatting.WHITE), Ui.text(subtitle, ChatFormatting.GRAY));
	}

	public static void show(ServerPlayer player, ItemStack icon, int accent, Component title, Component subtitle) {
		TopCard.show(player, CHANNEL, new TopCard.Card(icon, accent, title, subtitle, List.of()), PRIORITY, TICKS);
	}

	/** A toast with a row of extra info (for example "Budget 12 of 40"). */
	public static void show(ServerPlayer player, ItemStack icon, int accent, Component title, Component subtitle, TopCard.Row row) {
		TopCard.show(player, CHANNEL, new TopCard.Card(icon, accent, title, subtitle, List.of(row)), PRIORITY, TICKS + 20);
	}

	public static void hide(ServerPlayer player) {
		TopCard.hide(player, CHANNEL);
	}
}
