package nl.libreria.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import nl.libreria.core.gui.Caption;
import nl.libreria.core.gui.Toast;
import org.jetbrains.annotations.Nullable;

/**
 * How Libreria tells a player something. Four kinds, each with its own place on the screen, so the same kind of news
 * always shows up in the same spot:
 *
 * <ul>
 *   <li><b>whisper</b> – the action bar, about two seconds: direct feedback on what you just did ("12 items put away");</li>
 *   <li><b>card</b> – a card at the top: something that happened to you ("Chunk claimed", "Rex died");</li>
 *   <li><b>place</b> – a quiet line at the top: where you are ("Oakhaven · Village", "Jeff's farm");</li>
 *   <li><b>announcement</b> – a banner for everybody, only for rare moments (the collector's banner).</li>
 * </ul>
 *
 * Chat ({@link Say}) only carries what you may need later: coordinates, clickable buttons, other people's news.
 */
public final class Notify {
	private Notify() {
	}

	public static void whisper(ServerPlayer p, Component text) {
		Hud.flash(p, text);
	}

	public static void whisper(ServerPlayer p, String text, ChatFormatting color) {
		Hud.flash(p, Ui.text(text, color));
	}

	/** Good news: done, saved, claimed. */
	public static void done(ServerPlayer p, Item icon, String title, String subtitle) {
		Toast.show(p, icon, Toast.GOOD, title, subtitle);
	}

	/** Neutral news. */
	public static void info(ServerPlayer p, Item icon, String title, String subtitle) {
		Toast.show(p, icon, Toast.INFO, title, subtitle);
	}

	/** Something didn't work, with why and what to do. */
	public static void warn(ServerPlayer p, Item icon, String title, String subtitle) {
		Toast.show(p, icon, Toast.WARN, title, subtitle);
	}

	/** Something bad happened to you or yours. */
	public static void bad(ServerPlayer p, Item icon, String title, String subtitle) {
		Toast.show(p, icon, Toast.BAD, title, subtitle);
	}

	public static void card(ServerPlayer p, ItemStack icon, int accent, Component title, Component subtitle) {
		Toast.show(p, icon, accent, title, subtitle);
	}

	/** Where you are. */
	public static void place(ServerPlayer p, Component name, @Nullable Component kind) {
		Caption.show(p, name, kind);
	}
}
