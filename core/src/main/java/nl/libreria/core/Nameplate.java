package nl.libreria.core;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Tekst boven het hoofd van spelers, gedeeld door modules: CollectorMod zet er je titel in, NicknamesMod je nickname. Werkt met
 * gewone clients via een virtueel tekst-display dat op de speler "meerijdt". Als een module de naam vervangt, wordt
 * het gewone naamplaatje verborgen (via een scoreboard-team) en tekenen we de naam zelf.
 */
public final class Nameplate {
	public interface Line {
		@Nullable Component get(ServerPlayer player);
	}

	private record Provider(String key, int order, Line line) {
	}

	private static final List<Provider> LINES = new ArrayList<>();
	private static @Nullable Line nameOverride;
	private static final String HIDE_TEAM = "libreria_nicknames";

	private static final class Plate {
		final ElementHolder holder = new ElementHolder();
		final TextDisplayElement text = new TextDisplayElement();
		@Nullable Component last;
		@Nullable SelfAwareAttachment attachment;
		int entityId;
		boolean replacesName;
		boolean sneaking;
	}

	private static final Map<UUID, Plate> PLATES = new HashMap<>();

	private Nameplate() {
	}

	/** Regel boven de naam; lagere {@code order} staat dichter bij de naam. */
	public static void addLine(String key, int order, Line line) {
		LINES.removeIf(p -> p.key().equals(key));
		LINES.add(new Provider(key, order, line));
		LINES.sort(Comparator.comparingInt(Provider::order));
	}

	/** Vervangt de getoonde naam (null terug = gewone naam). Er is er maar één; de laatste wint. */
	public static void setName(Line name) {
		nameOverride = name;
	}

	/** De naam zoals modules hem willen tonen (nickname), of de gewone naam. */
	public static Component displayName(ServerPlayer player) {
		Component c = nameOverride == null ? null : nameOverride.get(player);
		return c != null ? c : player.getName();
	}

	static void tick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) update(server, p);
	}

	private static void update(MinecraftServer server, ServerPlayer p) {
		List<Component> above = new ArrayList<>();
		for (int i = LINES.size() - 1; i >= 0; i--) {
			Component c = LINES.get(i).line().get(p);
			if (c != null) above.add(c);
		}
		Component nick = nameOverride == null ? null : nameOverride.get(p);
		boolean replaces = nick != null && setHidden(server, p, true);
		if (nick == null || !replaces) setHidden(server, p, false);

		MutableComponent text = Component.empty();
		for (int i = 0; i < above.size(); i++) {
			if (i > 0) text.append("\n");
			text.append(above.get(i));
		}
		if (nick != null) {
			if (!above.isEmpty()) text.append("\n");
			text.append(nick);
		}
		boolean empty = above.isEmpty() && nick == null;
		Plate plate = PLATES.get(p.getUUID());
		if (empty) {
			if (plate != null) {
				plate.holder.destroy();
				PLATES.remove(p.getUUID());
			}
			return;
		}
		// Onzichtbaar of toeschouwer: niets tonen, net als het gewone naamplaatje
		Component shown = p.isInvisible() || p.isSpectator() ? Component.empty() : text;
		// Na respawnen is de speler een nieuwe entity: opnieuw vastmaken
		if (plate == null || plate.holder.getAttachment() == null || plate.entityId != p.getId()) {
			if (plate != null) plate.holder.destroy();
			plate = new Plate();
			plate.entityId = p.getId();
			plate.text.setBillboardMode(Display.BillboardConstraints.CENTER);
			plate.text.setSeeThrough(true);
			plate.text.setViewRange(1.0f);
			plate.holder.addElement(plate.text);
			plate.attachment = new SelfAwareAttachment(plate.holder, p);
			VirtualEntityUtils.addVirtualPassenger(p, plate.text.getEntityId());
			PLATES.put(p.getUUID(), plate);
		}
		// Third person: also show the plate to its owner (setting, default on)
		boolean self = Libreria.OWN_PLATE.get(p);
		if (plate.attachment != null && plate.attachment.showSelf != self) {
			plate.attachment.showSelf = self;
			if (self) {
				plate.holder.startWatching(p);
				// Resend the passenger list so the owner's client attaches the freshly spawned text
				VirtualEntityUtils.removeVirtualPassenger(p, plate.text.getEntityId());
				VirtualEntityUtils.addVirtualPassenger(p, plate.text.getEntityId());
			} else {
				plate.holder.stopWatching(p);
			}
		}
		boolean sneak = p.isShiftKeyDown();
		if (plate.replacesName != replaces || plate.last == null || plate.sneaking != sneak) {
			// Vervangt het de naam, dan op naamhoogte; anders erboven
			plate.text.setTranslation(new Vector3f(0, replaces ? 0.25f : 0.55f, 0));
			// Sneaken: net als vanilla niet door muren heen en wat doffer
			plate.text.setSeeThrough(!sneak);
			plate.text.setTextOpacity((byte) (sneak ? 0x80 : 0xFF));
			plate.replacesName = replaces;
			plate.sneaking = sneak;
		}
		if (!Objects.equals(plate.last, shown)) {
			plate.text.setText(shown);
			plate.last = shown;
		}
	}

	/** Zet de speler wel/niet in het team dat het gewone naamplaatje verbergt. @return of het naamplaatje nu verborgen is */
	private static boolean setHidden(MinecraftServer server, ServerPlayer p, boolean hide) {
		ServerScoreboard sb = server.getScoreboard();
		String name = p.getScoreboardName();
		PlayerTeam current = sb.getPlayersTeam(name);
		if (!hide) {
			if (current != null && current.getName().equals(HIDE_TEAM)) sb.removePlayerFromTeam(name, current);
			return false;
		}
		// Zit de speler al in een ander team (van een andere mod of /team), dan blijven we daar af
		if (current != null && !current.getName().equals(HIDE_TEAM)) return false;
		if (current != null) return true;
		PlayerTeam team = sb.getPlayerTeam(HIDE_TEAM);
		if (team == null) {
			team = sb.addPlayerTeam(HIDE_TEAM);
			team.setNameTagVisibility(Team.Visibility.NEVER);
			// Anders zien teamgenoten elkaar half als ze onzichtbaar zijn
			team.setSeeFriendlyInvisibles(false);
		}
		sb.addPlayerToTeam(name, team);
		return true;
	}

	/** Like a normal entity attachment, but the owner may watch too (vanilla never shows you your own name). */
	static final class SelfAwareAttachment extends EntityAttachment {
		private final ServerPlayer owner;
		boolean showSelf;

		SelfAwareAttachment(ElementHolder holder, ServerPlayer owner) {
			super(holder, owner, true);
			this.owner = owner;
			this.attach();
		}

		@Override
		public void updateCurrentlyTracking(java.util.Collection<net.minecraft.server.network.ServerGamePacketListenerImpl> current) {
			// Let Polymer manage everyone else; the owner is handled by showSelf
			List<net.minecraft.server.network.ServerGamePacketListenerImpl> others = new ArrayList<>();
			for (var c : current) if (c != this.owner.connection) others.add(c);
			super.updateCurrentlyTracking(others);
		}
	}

	static void forget(UUID id) {
		Plate plate = PLATES.remove(id);
		if (plate != null) plate.holder.destroy();
	}

	static void clearAll() {
		PLATES.values().forEach(p -> p.holder.destroy());
		PLATES.clear();
	}
}
