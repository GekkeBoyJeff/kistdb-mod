package nl.handig.kern;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * De actiebalk (tekst boven de hotbar) gedeeld tussen modules. Elke module schrijft op een eigen kanaal met een
 * prioriteit; de hoogste actieve regel wordt getoond. Zo overschrijven de straal, de durability-balk en meldingen
 * elkaar niet.
 */
public final class Hud {
	/** Vaste prioriteiten zodat modules consistent zijn. */
	public static final int PRIO_INFO = 10;
	public static final int PRIO_GUIDE = 50;
	public static final int PRIO_WARNING = 80;
	public static final int PRIO_FLASH = 100;

	private record Line(Component text, int priority, int until) {
	}

	private static final class State {
		final Map<String, Line> lines = new HashMap<>();
		@Nullable String shown;
		@Nullable Component shownText;
		int lastSend = -1000;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
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

	public static void hide(ServerPlayer player, String channel) {
		State s = STATES.get(player.getUUID());
		if (s != null && s.lines.remove(channel) != null) update(player, s, false);
	}

	static void tick(MinecraftServer server, int tick) {
		now = tick;
		if (STATES.isEmpty()) return;
		Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, State> e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null) {
				it.remove();
				continue;
			}
			State s = e.getValue();
			s.lines.values().removeIf(l -> l.until() <= now);
			if (s.lines.isEmpty() && s.shown == null) {
				it.remove();
				continue;
			}
			update(p, s, true);
		}
	}

	private static void update(ServerPlayer player, State s, boolean periodic) {
		String best = null;
		Line bestLine = null;
		for (Map.Entry<String, Line> e : s.lines.entrySet()) {
			if (e.getValue().until() <= now) continue;
			if (bestLine == null || e.getValue().priority() > bestLine.priority()) {
				best = e.getKey();
				bestLine = e.getValue();
			}
		}
		if (bestLine == null) {
			s.shown = null;
			s.shownText = null;
			return;
		}
		boolean changed = !best.equals(s.shown) || !bestLine.text().equals(s.shownText);
		// De actiebalk vervaagt na ~3 s; elke seconde opnieuw sturen houdt hem zichtbaar
		if (changed || (periodic && now - s.lastSend >= 20)) {
			player.connection.send(new ClientboundSetActionBarTextPacket(bestLine.text()));
			s.shown = best;
			s.shownText = bestLine.text();
			s.lastSend = now;
		}
	}

	static void forget(UUID id) {
		STATES.remove(id);
	}
}
