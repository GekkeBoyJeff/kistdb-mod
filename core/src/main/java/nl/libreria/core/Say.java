package nl.libreria.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * The one voice of Libreria in chat. Every line starts with a small ◆ in the colour of its domain (no "[Claims]",
 * "[Waypoints]"…), and clickable things look the same everywhere: an aqua {@code [Show the way]} with a hover line.
 *
 * <p>Chat is for what you may need later: coordinates, a button, a line someone else will read. Anything you only
 * need to see now goes to a card at the top ({@link nl.libreria.core.gui.Toast}), the action bar ({@link Hud#flash})
 * or the place caption ({@link nl.libreria.core.gui.Caption}); see {@link Notify}.
 */
public final class Say {
	private Say() {
	}

	/** The start of a line: "◆ " in the domain's colour. */
	public static MutableComponent line(Domain d) {
		return Ui.text("◆ ", d.rgb);
	}

	public static MutableComponent line(Domain d, String text, ChatFormatting color) {
		return line(d).append(Ui.text(text, color));
	}

	public static void to(ServerPlayer p, Domain d, Component message) {
		p.sendSystemMessage(line(d).append(message));
	}

	public static void to(ServerPlayer p, Domain d, String text, ChatFormatting color) {
		to(p, d, Ui.text(text, color));
	}

	/** A clickable {@code [Label]} that runs a command. */
	public static MutableComponent run(String label, String command, String hover) {
		return chip(label, new ClickEvent.RunCommand(command), hover);
	}

	/** A clickable {@code [Label]} that puts a command in the chat box, to finish typing. */
	public static MutableComponent suggest(String label, String command, String hover) {
		return chip(label, new ClickEvent.SuggestCommand(command), hover);
	}

	private static MutableComponent chip(String label, ClickEvent click, String hover) {
		return Component.literal("[" + label + "]").withStyle(s -> s.withColor(ChatFormatting.AQUA).withItalic(false).withBold(false)
				.withClickEvent(click).withHoverEvent(new HoverEvent.ShowText(Component.literal(hover).withStyle(ChatFormatting.GRAY))));
	}

	/** An underlined command inside a sentence, clickable. */
	public static MutableComponent command(String text, String command, String hover) {
		return Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.YELLOW).withUnderlined(true).withItalic(false)
				.withClickEvent(new ClickEvent.SuggestCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover).withStyle(ChatFormatting.GRAY))));
	}

	/** "x y z" in white, for a place. */
	public static MutableComponent coords(int x, int y, int z) {
		return Ui.text(x + " " + y + " " + z, ChatFormatting.WHITE);
	}
}
