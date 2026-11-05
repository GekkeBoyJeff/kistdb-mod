package nl.libreria.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import nl.libreria.core.gui.Glyphs;
import org.jetbrains.annotations.Nullable;

/**
 * De actiebalk (tekst boven de hotbar) gedeeld tussen modules. Elke module schrijft op een eigen kanaal met een
 * prioriteit; de hoogste actieve regel wordt getoond. Zo overschrijven de straal, de durability-balk en meldingen
 * elkaar niet.
 *
 * <p>With the Libreria pack the action bar also draws a button right of the hotbar (see {@link #setHotbarButton}):
 * a font that moves its pictures 50 px down puts them next to the hotbar, while the text itself stays centred.
 */
public final class Hud {
	/** Vaste prioriteiten zodat modules consistent zijn. */
	/** Waar je naar kijkt (kijk-info): het laagst, alles mag eroverheen. */
	public static final int PRIO_LOOK = 5;
	/** Doorlopende info, zoals de durability-balk tijdens het hakken. */
	public static final int PRIO_INFO = 10;
	/** Info over wat je bewust vasthoudt (kompas, klok). */
	public static final int PRIO_HELD = 15;
	public static final int PRIO_GUIDE = 50;
	public static final int PRIO_WARNING = 80;
	public static final int PRIO_FLASH = 100;

	/**
	 * A button right of the hotbar: {@code picture} = a glyph of the libreria:hotbar font (like "hb.map"), {@code key}
	 * = the key that works it (drawn in its corner, e.g. a keybind component), {@code keyWidth} = its width in pixels
	 * as far as the server can tell.
	 */
	public record HotbarButton(String picture, Component key, int keyWidth) {
	}

	private record Line(Component text, int priority, int until) {
	}

	private static final class State {
		final Map<String, Line> lines = new HashMap<>();
		@Nullable Component shownText;
		int lastSend = -1000;
	}

	private static final FontDescription HOTBAR = new FontDescription.Resource(Identifier.fromNamespaceAndPath("libreria", "hotbar"));
	/** Left edge of the hotbar button, from the middle of the screen (the hotbar is 182 px wide). */
	private static final int BUTTON_X = 95;

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static @Nullable Function<ServerPlayer, HotbarButton> hotbarButton;
	private static int now;

	private Hud() {
	}

	/** Toont tekst op een kanaal, {@code ticks} lang. Opnieuw aanroepen werkt de tekst bij. */
	public static void show(ServerPlayer player, String channel, Component text, int priority, int ticks) {
		State s = STATES.computeIfAbsent(player.getUUID(), k -> new State());
		s.lines.put(channel, new Line(text, priority, now + ticks));
		update(player, s, false);
	}

	/** Korte melding met de hoogste prioriteit (ongeveer 2 seconden). */
	public static void flash(ServerPlayer player, Component text) {
		show(player, "flash", text, PRIO_FLASH, 40);
	}

	/** True when nothing of ours is in the action bar now (so something unimportant may show). */
	public static boolean idle(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		if (s == null) return true;
		for (Line l : s.lines.values()) if (l.until() > now) return false;
		return true;
	}

	public static void hide(ServerPlayer player, String channel) {
		State s = STATES.get(player.getUUID());
		if (s != null && s.lines.remove(channel) != null) update(player, s, false);
	}

	/** The button right of the hotbar (one module may claim it); null for a player = no button. */
	public static void setHotbarButton(Function<ServerPlayer, HotbarButton> provider) {
		hotbarButton = provider;
	}

	/** Redraw now (e.g. after a setting changed). */
	public static void refresh(ServerPlayer player) {
		update(player, STATES.computeIfAbsent(player.getUUID(), k -> new State()), false);
	}

	static void tick(MinecraftServer server, int tick) {
		now = tick;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			State s = STATES.get(p.getUUID());
			if (s == null) {
				if (hotbarButton == null || tick % 10 != p.getId() % 10) continue;
				s = new State();
				STATES.put(p.getUUID(), s);
			}
			s.lines.values().removeIf(l -> l.until() <= now);
			update(p, s, true);
			if (s.lines.isEmpty() && s.shownText == null) STATES.remove(p.getUUID());
		}
	}

	private static void update(ServerPlayer player, State s, boolean periodic) {
		Line best = null;
		for (Line l : s.lines.values()) {
			if (l.until() <= now) continue;
			if (best == null || l.priority() > best.priority()) best = l;
		}
		HotbarButton button = hotbarButton != null && Pack.has(player) ? hotbarButton.apply(player) : null;
		Component text;
		if (button != null) {
			Component message = best == null ? Component.empty() : best.text();
			text = Component.empty().append(message).append(hotbarSegment(button, best == null ? 0 : Glyphs.width(message)));
		} else if (best != null) {
			text = best.text();
		} else {
			// Taken away on purpose (not run out): clear it at once instead of letting it fade for 3 seconds
			if (!periodic && s.shownText != null) player.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
			s.shownText = null;
			return;
		}
		boolean changed = !text.equals(s.shownText);
		// The action bar fades after ~3 s; sending it again every second keeps it there
		if (changed || (periodic && now - s.lastSend >= 20)) {
			player.connection.send(new ClientboundSetActionBarTextPacket(text));
			s.shownText = text;
			s.lastSend = now;
		}
	}

	/**
	 * The hotbar button, drawn after the centred message: from the message's end over to the right of the hotbar,
	 * slot, picture, key, and back again so the line keeps the message's width (and the message stays centred).
	 */
	private static MutableComponent hotbarSegment(HotbarButton b, int messageWidth) {
		int start = BUTTON_X - (messageWidth - messageWidth / 2);
		int keyX = 20 - b.keyWidth();
		String pictures = Glyphs.space(start) + Glyphs.g("hb.slot") + Glyphs.space(3 - Glyphs.advance("hb.slot")) + Glyphs.g(b.picture())
				+ Glyphs.space(keyX - 3 - Glyphs.advance(b.picture()));
		MutableComponent out = Component.literal(pictures).withStyle(st -> st.withFont(HOTBAR).withColor(0xFFFFFF).withoutShadow()
				.withBold(false).withItalic(false));
		out.append(b.key().copy().withStyle(st -> st.withFont(HOTBAR).withColor(0xFFFFFF).withBold(false).withItalic(false)));
		out.append(Component.literal(Glyphs.space(-(start + 20))).withStyle(st -> st.withFont(HOTBAR)));
		return out;
	}

	static void forget(UUID id) {
		STATES.remove(id);
	}
}
