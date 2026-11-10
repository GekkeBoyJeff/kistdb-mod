package nl.libreria.teleports;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.Domain;
import nl.libreria.core.Notify;
import nl.libreria.core.Say;
import nl.libreria.core.Services;
import nl.libreria.core.Tips;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * Asking to teleport: {@code /tpa <player>} asks to go to them, {@code /tpahere <player>} asks them to come to you. They
 * get a card at the top and a chat line with Accept and Deny (or {@code /tpaccept}, {@code /tpdeny}); the one who asked
 * can {@code /tpcancel}. An ask runs out after a while (a server setting). Whoever moves follows the server's rules
 * ({@link Travel}). Each player chooses who may ask them; an operator can take asking away from someone.
 */
final class Asks {
	/** {@code from} asked {@code to}: to go to them, or ({@code here}) for them to come over. */
	record Ask(ServerPlayer from, ServerPlayer to, boolean here, long until) {
		/** Who moves when it's accepted, and to whom. */
		ServerPlayer mover() {
			return this.here ? this.to : this.from;
		}

		ServerPlayer host() {
			return this.here ? this.from : this.to;
		}
	}

	/** Per player: the asks waiting for them, oldest first. */
	private static final Map<UUID, LinkedHashMap<UUID, Ask>> FOR = new HashMap<>();
	static final int GOLD = 0xFFD966;

	private Asks() {
	}

	/** {@code from} asks {@code to}. False (with why) when it can't. */
	static boolean ask(ServerPlayer from, ServerPlayer to, boolean here) {
		if (!TeleportsMod.MODULE.on()) return false;
		String why = whyNot(from, to);
		if (why != null) {
			Notify.warn(from, Items.ENDER_PEARL, "Can't ask " + to.getName().getString(), why);
			return false;
		}
		long until = System.currentTimeMillis() + TeleportsMod.EXPIRE.get() * 1000L;
		Ask a = new Ask(from, to, here, until);
		FOR.computeIfAbsent(to.getUUID(), k -> new LinkedHashMap<>()).put(from.getUUID(), a);
		String them = to.getName().getString(), me = from.getName().getString();
		int secs = TeleportsMod.EXPIRE.get();
		// To them: a card at the top, and a chat line with the buttons
		MutableComponent title = Ui.text(me, GOLD).append(Ui.text(here ? " asks you to come over" : " wants to come to you", ChatFormatting.WHITE));
		Notify.card(to, new ItemStack(Items.ENDER_PEARL), GOLD, title, Ui.text("Accept in chat or /tpaccept · " + secs + " s", ChatFormatting.GRAY));
		to.sendSystemMessage(Say.line(Domain.PLACES).append(Ui.text(me + (here ? " asks you to come to them " : " wants to teleport to you "),
				ChatFormatting.WHITE)).append(Say.run("Accept", "/tpaccept " + me, here ? "Teleport to " + me : me + " teleports to you"))
				.append(Ui.text(" ", ChatFormatting.WHITE)).append(Say.run("Deny", "/tpdeny " + me, "Say no")));
		Ui.sound(to, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6f, 1.5f);
		Tips.once(to, TeleportsMod.TIP_ASK);
		// To you: that it went out, and how to take it back
		from.sendSystemMessage(Say.line(Domain.PLACES).append(Ui.text((here ? "You asked " + them + " to come to you" : "You asked to go to " + them)
				+ " · " + secs + " s ", ChatFormatting.GRAY)).append(Say.run("Cancel", "/tpcancel " + them, "Take the ask back")));
		Ui.sound(from, SoundEvents.NOTE_BLOCK_PLING.value(), 0.4f, 1.6f);
		return true;
	}

	/** Why {@code from} can't ask {@code to} right now, or null when they can. */
	static @Nullable String whyNot(ServerPlayer from, ServerPlayer to) {
		if (!TeleportsMod.TPA.get()) return "Teleport asks are turned off on this server.";
		if (from == to) return "That's you.";
		if (TeleportsMod.PLAYERS.get(from).noAsks && !Ui.isOp(from)) return "An operator turned teleport asks off for you.";
		int who = TeleportsMod.ASKS.get(to);
		if (who == 2) return to.getName().getString() + " doesn't take teleport asks.";
		if (who == 1 && !Services.land().map(l -> l.trusts(to.getUUID(), from.getUUID())).orElse(false)) {
			return to.getName().getString() + " only takes asks from people they trust.";
		}
		return null;
	}

	/** /tpaccept, /tpdeny [name]: the newest ask (or the one from that player). */
	static boolean answer(ServerPlayer p, @Nullable String name, boolean yes) {
		Ask a = find(p, name);
		if (a == null) {
			Notify.info(p, Items.ENDER_PEARL, name == null ? "No asks for you" : "No ask from " + name, "Asks run out after " + TeleportsMod.EXPIRE.get() + " seconds.");
			return false;
		}
		remove(a);
		String asker = a.from().getName().getString();
		if (!yes) {
			Notify.whisper(p, Ui.text("You said no to " + asker, ChatFormatting.GRAY));
			Notify.info(a.from(), Items.ENDER_PEARL, p.getName().getString() + " said no", "To your teleport ask.");
			return true;
		}
		if (a.from().hasDisconnected()) {
			Notify.info(p, Items.ENDER_PEARL, asker + " is gone", "They left the game.");
			return false;
		}
		ServerPlayer host = a.host();
		Notify.done(a.from(), Items.ENDER_PEARL, p.getName().getString() + " said yes", a.here() ? "They're on their way" : "Here you go");
		return Travel.go(a.mover(), new Travel.Target(host.level(), host.position(), host.getYRot(), host.getXRot()),
				Ui.text(host.getName().getString(), GOLD));
	}

	/** /tpcancel [name]: takes your ask back. */
	static boolean cancel(ServerPlayer p, @Nullable String name) {
		Ask mine = null;
		for (Map<UUID, Ask> m : FOR.values()) {
			Ask a = m.get(p.getUUID());
			if (a != null && (name == null || a.to().getName().getString().equalsIgnoreCase(name)) && (mine == null || a.until() > mine.until())) mine = a;
		}
		if (mine == null) {
			Notify.info(p, Items.ENDER_PEARL, "No ask to take back", "You haven't asked anyone.");
			return false;
		}
		remove(mine);
		Notify.whisper(p, Ui.text("Ask to " + mine.to().getName().getString() + " taken back", ChatFormatting.GRAY));
		return true;
	}

	private static @Nullable Ask find(ServerPlayer p, @Nullable String name) {
		Map<UUID, Ask> m = FOR.get(p.getUUID());
		if (m == null || m.isEmpty()) return null;
		Ask found = null;
		for (Ask a : m.values()) {
			if (name != null && !a.from().getName().getString().equalsIgnoreCase(name)) continue;
			found = a;
		}
		return found;
	}

	private static void remove(Ask a) {
		Map<UUID, Ask> m = FOR.get(a.to().getUUID());
		if (m != null) {
			m.remove(a.from().getUUID());
			if (m.isEmpty()) FOR.remove(a.to().getUUID());
		}
	}

	/** The asks waiting for this player (newest last). */
	static List<Ask> waitingFor(ServerPlayer p) {
		Map<UUID, Ask> m = FOR.get(p.getUUID());
		return m == null ? List.of() : List.copyOf(m.values());
	}

	/** The ask this player sent to {@code to}, if it's still waiting. */
	static @Nullable Ask sent(ServerPlayer p, ServerPlayer to) {
		Map<UUID, Ask> m = FOR.get(to.getUUID());
		return m == null ? null : m.get(p.getUUID());
	}

	static List<String> askerNames(ServerPlayer p) {
		List<String> out = new ArrayList<>();
		for (Ask a : waitingFor(p)) out.add(a.from().getName().getString());
		return out;
	}

	/** Once a second: asks that ran out go, and the one who asked hears it. */
	static void tick(MinecraftServer server) {
		if (FOR.isEmpty()) return;
		long now = System.currentTimeMillis();
		for (Map<UUID, Ask> m : List.copyOf(FOR.values())) {
			for (Ask a : List.copyOf(m.values())) {
				if (a.until() > now) continue;
				remove(a);
				if (!a.from().hasDisconnected()) {
					Notify.whisper(a.from(), Ui.text("Your teleport ask to " + a.to().getName().getString() + " ran out", ChatFormatting.GRAY));
				}
			}
		}
	}

	/** Someone left: their asks (both ways) go. */
	static void forget(ServerPlayer p) {
		FOR.remove(p.getUUID());
		for (Map<UUID, Ask> m : FOR.values()) m.remove(p.getUUID());
		FOR.values().removeIf(Map::isEmpty);
	}

	static void clearAll() {
		FOR.clear();
	}

	/** For the screenshots: {@code from} (a stand-in) asks {@code to}, without the checks. */
	static void debugAsk(ServerPlayer from, ServerPlayer to, boolean here) {
		FOR.computeIfAbsent(to.getUUID(), k -> new LinkedHashMap<>())
				.put(from.getUUID(), new Ask(from, to, here, System.currentTimeMillis() + TeleportsMod.EXPIRE.get() * 1000L));
	}

	static Component nameOf(ServerPlayer p) {
		return Ui.text(p.getName().getString(), GOLD);
	}
}
