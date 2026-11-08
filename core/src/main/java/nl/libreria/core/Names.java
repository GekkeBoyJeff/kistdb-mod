package nl.libreria.core;

import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * How players are named: in chat and messages, and in the tab list. A module that renames players (nicknames) sets
 * these; core's own name hooks ask them first and then add what they add (a click that opens the profile, the title and
 * AFK in the tab list). One place, so two mods' hooks can't cut each other off: whichever of two such hooks ran first
 * used to win, so a nickname sometimes didn't show in chat, and a title never showed after a nickname.
 */
public final class Names {
	private static Function<ServerPlayer, Component> chat = p -> null;
	private static Function<ServerPlayer, Component> tab = p -> null;

	private Names() {
	}

	public static void provide(Function<ServerPlayer, Component> chatName, Function<ServerPlayer, Component> tabName) {
		chat = chatName;
		tab = tabName;
	}

	/** The name in chat and messages, if a module renames this player (null: Minecraft's own). */
	public static @Nullable Component chat(ServerPlayer p) {
		try {
			return chat.apply(p);
		} catch (RuntimeException e) {
			Libreria.LOG.debug("Chat name failed", e);
			return null;
		}
	}

	/** The name in the tab list, if a module renames this player (null: Minecraft's own). */
	public static @Nullable Component tab(ServerPlayer p) {
		try {
			return tab.apply(p);
		} catch (RuntimeException e) {
			Libreria.LOG.debug("Tab name failed", e);
			return null;
		}
	}
}
