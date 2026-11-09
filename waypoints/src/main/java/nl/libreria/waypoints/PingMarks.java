package nl.libreria.waypoints;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * What a ping looks like on the spot, drawn for each player who sees it (only they get these virtual entities), in the
 * look the server picked ({@link WaypointsMod#PING_LOOK}): a short pillar of light with a gem above it, or a diamond with
 * a line down to the ground and rings around its foot. The gem or diamond and its label (who, what, how far) show
 * through walls and keep about the same size on screen however far away the ping is; past the player's view distance
 * they're drawn on the way there, so a far ping is still easy to find. The textures are the font {@code libreria:ping}
 * ({@code scripts/ping/gen.py}).
 */
final class PingMarks {
	static final int PILLAR = 0, DIAMOND = 1;

	private static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("libreria", "ping"));
	private static final char DIAMOND_GLYPH = '', GEM = '', COLUMN = '', LINE = '', RING = '', PULSE = '';
	/** Beyond this distance the marker grows with the distance, so it keeps its size on screen. */
	private static final double KEEP = 10;
	/** Up close it stays this size (a gem of about half a block). */
	private static final float MIN_SCALE = 0.6f;
	/** Half the height of the gem or diamond at scale 1 (the glyph is 0.8 of a block). */
	private static final float HALF = 0.4f;
	/** How high the gem or diamond floats above the spot. */
	private static final double PILLAR_TOP = 2.45, DIAMOND_TOP = 1.6;
	private static final int LABEL_BACKGROUND = 0xB4141820;
	/** The label next to the gem: a little bigger than plain text in the world, so it reads from afar. */
	private static final float LABEL_SIZE = 1.8f;
	/** The ping nearest your crosshair within this angle gets the whole label; the others only their distance. */
	private static final double FOCUS_COS = Math.cos(Math.toRadians(20));
	/** The last second fades out in four steps. */
	private static final int FADE_TICKS = 20;
	private static final int[] FADE = {200, 150, 100, 55};
	private static final int PULSE_EVERY = 40;

	private static final class Mark {
		final ServerPlayer viewer;
		final Pings.Ping ping;
		final int look;
		final Vec3 top;
		final ElementHolder site = new ElementHolder();
		final ElementHolder sign = new ElementHolder();
		TextDisplayElement mark;
		TextDisplayElement label;
		@Nullable TextDisplayElement pillar, line, ring, pulse;
		Vec3 signAt;
		@Nullable Vec3 lastEye;
		int age;
		float scale = -1;
		int metres = -1;
		/** 2 = who, what and how far; 1 = how far; 0 = no label (it would cover another one). */
		int show = 2;
		int shown = -1;
		int fade = -1;

		Mark(ServerPlayer viewer, Pings.Ping ping, int look) {
			this.viewer = viewer;
			this.ping = ping;
			this.look = look;
			this.top = ping.at().add(0, look == PILLAR ? PILLAR_TOP : DIAMOND_TOP, 0);
			this.signAt = this.top;
		}

		void destroy() {
			this.site.destroy();
			this.sign.destroy();
		}
	}

	/** Viewer → owner of the ping → what that viewer sees of it. */
	private static final Map<UUID, Map<UUID, Mark>> MARKS = new HashMap<>();

	private PingMarks() {
	}

	/** Draws this ping for this player (in place of what they saw of that owner's ping before). */
	static void show(ServerPlayer viewer, Pings.Ping ping) {
		remove(viewer.getUUID(), ping.owner());
		if (viewer.level() != ping.level()) return;
		Mark m = new Mark(viewer, ping, WaypointsMod.PING_LOOK.get());
		build(m);
		MARKS.computeIfAbsent(viewer.getUUID(), k -> new HashMap<>()).put(ping.owner(), m);
	}

	static boolean has(ServerPlayer viewer, UUID owner) {
		Map<UUID, Mark> map = MARKS.get(viewer.getUUID());
		return map != null && map.containsKey(owner);
	}

	/** The look this player sees that ping in, or -1. */
	static int lookOf(ServerPlayer viewer, UUID owner) {
		Map<UUID, Mark> map = MARKS.get(viewer.getUUID());
		Mark m = map == null ? null : map.get(owner);
		return m == null ? -1 : m.look;
	}

	static void remove(UUID viewer, UUID owner) {
		Map<UUID, Mark> map = MARKS.get(viewer);
		if (map == null) return;
		Mark m = map.remove(owner);
		if (m != null) m.destroy();
		if (map.isEmpty()) MARKS.remove(viewer);
	}

	/** That owner's ping is gone: for everyone. */
	static void removeOwner(UUID owner) {
		for (UUID viewer : new ArrayList<>(MARKS.keySet())) remove(viewer, owner);
	}

	static void forgetViewer(UUID viewer) {
		Map<UUID, Mark> map = MARKS.remove(viewer);
		if (map != null) map.values().forEach(Mark::destroy);
	}

	static void clearAll() {
		MARKS.values().forEach(map -> map.values().forEach(Mark::destroy));
		MARKS.clear();
	}

	/** The server's look changed: everyone sees the pings that are up in the new look at once. */
	static void rebuild() {
		List<Mark> all = new ArrayList<>();
		MARKS.values().forEach(map -> all.addAll(map.values()));
		clearAll();
		for (Mark m : all) show(m.viewer, m.ping);
	}

	// ---------------------------------------------------------------- drawing

	private static void build(Mark m) {
		Vec3 at = m.ping.at();
		if (m.look == PILLAR) {
			// a soft column of light that grows out of the ground and turns to face you
			m.pillar = glyph(COLUMN, Display.BillboardConstraints.VERTICAL);
			m.pillar.setScale(new Vector3f(1, 0.001f, 1));
			m.site.addElement(m.pillar);
		} else {
			m.line = glyph(LINE, Display.BillboardConstraints.VERTICAL);
			m.line.setScale(new Vector3f(1, 0.001f, 1));
			m.site.addElement(m.line);
			m.ring = flat(RING);
			m.site.addElement(m.ring);
			m.pulse = flat(PULSE);
			m.site.addElement(m.pulse);
		}
		new ManualAttachment(m.site, m.ping.level(), () -> at);
		m.site.startWatching(m.viewer);

		// the gem or diamond and the label: through walls, and they keep their size on screen
		m.mark = glyph(m.look == PILLAR ? GEM : DIAMOND_GLYPH, Display.BillboardConstraints.CENTER);
		m.mark.setSeeThrough(true);
		m.mark.setScale(new Vector3f(0.001f));
		m.mark.setTeleportDuration(2);
		m.sign.addElement(m.mark);
		m.label = new TextDisplayElement(Component.empty());
		m.label.setBillboardMode(Display.BillboardConstraints.CENTER);
		m.label.setSeeThrough(true);
		m.label.setShadow(true);
		m.label.setBackground(LABEL_BACKGROUND);
		m.label.setBrightness(new Brightness(15, 15));
		m.label.setViewRange(8f);
		m.label.setScale(new Vector3f(0.001f));
		m.label.setTeleportDuration(2);
		m.sign.addElement(m.label);
		place(m);
		label(m);
		new ManualAttachment(m.sign, m.ping.level(), () -> m.signAt);
		m.sign.startWatching(m.viewer);
	}

	private static TextDisplayElement glyph(char c, Display.BillboardConstraints billboard) {
		TextDisplayElement e = new TextDisplayElement(Component.literal(String.valueOf(c)).withStyle(s -> s.withFont(FONT).withColor(0xFFFFFF)));
		e.setBillboardMode(billboard);
		e.setBackground(0);
		e.setShadow(false);
		e.setBrightness(new Brightness(15, 15));
		e.setViewRange(8f);
		return e;
	}

	/** Lying on the ground, facing up. */
	private static TextDisplayElement flat(char c) {
		TextDisplayElement e = glyph(c, Display.BillboardConstraints.VERTICAL);
		e.setOffset(new Vec3(0, 0.03, 0));
		e.setLeftRotation(new Quaternionf().rotateX((float) -Math.PI / 2));
		e.setScale(new Vector3f(0.001f));
		e.setViewRange(4f);
		return e;
	}

	/**
	 * Where the gem goes for this viewer and how big. Within their view distance it's on the spot, scaled up with the
	 * distance; further away it's on the line towards the spot, at the edge of what they can see, scaled to look the same.
	 */
	private static float place(Mark m) {
		Vec3 eye = m.viewer.getEyePosition();
		// The client draws it a tick or two later: a little ahead of where the player is going
		Vec3 ahead = m.lastEye == null ? eye : eye.add(eye.subtract(m.lastEye).scale(2));
		m.lastEye = eye;
		Vec3 d = m.top.subtract(ahead);
		double dist = Math.max(0.01, d.length());
		double reach = reach(m.viewer);
		double place = Math.min(dist, reach);
		m.signAt = dist <= reach ? m.top : ahead.add(d.scale(place / dist));
		return (float) (Math.max(MIN_SCALE, dist / KEEP) * place / dist);
	}

	/** How far this player sees entities drawn: a little short of their (and the server's) view distance. */
	private static double reach(ServerPlayer viewer) {
		int chunks = Math.min(viewer.requestedViewDistance(), viewer.level().getServer().getPlayerList().getViewDistance());
		return Math.max(32, (chunks - 2) * 16);
	}

	private static void resize(Mark m, float s, int ticks) {
		m.scale = s;
		m.mark.setScale(new Vector3f(s));
		m.mark.setInterpolationDuration(ticks);
		m.mark.startInterpolation();
		m.label.setScale(new Vector3f(s * LABEL_SIZE));
		// in the label's own (screen-facing) space: straight up, just above the gem
		m.label.setTranslation(new Vector3f(0, (HALF + 0.08f) * s, 0));
		m.label.setInterpolationDuration(ticks);
		m.label.startInterpolation();
	}

	/** Who, what and how far for the ping you look at; only how far for the others (so labels don't pile up). */
	private static void label(Mark m) {
		int metres = (int) Math.round(m.viewer.position().distanceTo(m.ping.at()));
		if (metres == m.metres && m.show == m.shown) return;
		m.metres = metres;
		m.shown = m.show;
		m.label.setText(labelText(m, m.show, metres));
		m.label.setBackground(background(m));
	}

	private static Component labelText(Mark m, int show, int metres) {
		if (show == 0) return Component.empty();
		if (show == 1) return Ui.text(" " + Ui.meters(metres) + " ", 0xD8DCE6);
		boolean own = m.ping.owner().equals(m.viewer.getUUID());
		MutableComponent t = Ui.text(" " + (own ? "Your ping" : m.ping.ownerName()), Pings.COLOR);
		t.append(Ui.text(" · ", 0x8A8F9C));
		t.append(m.ping.what().copy().withStyle(ChatFormatting.WHITE));
		t.append(Ui.text("  " + Ui.meters(metres) + " ", 0xB8BCC8));
		return t;
	}

	private static int background(Mark m) {
		if (m.show == 0) return 0;
		int alpha = (LABEL_BACKGROUND >>> 24) * (m.fade < 0 ? 255 : FADE[m.fade]) / 255;
		return alpha << 24 | (LABEL_BACKGROUND & 0xFFFFFF);
	}

	/** A box on the player's screen, in radians from their crosshair (x to the right, y up). */
	private record Box(double x0, double y0, double x1, double y1) {
		boolean hits(Box o) {
			return this.x0 < o.x1 && o.x0 < this.x1 && this.y0 < o.y1 && o.y0 < this.y1;
		}
	}

	/**
	 * Which label each of this player's pings gets. The one nearest their crosshair (within {@link #FOCUS_COS}) gets the
	 * whole label, the others only their distance, and a label that would cover another label or a gem isn't shown.
	 */
	private static void layout(Map<UUID, Mark> map) {
		ServerPlayer viewer = map.values().iterator().next().viewer;
		Vec3 eye = viewer.getEyePosition();
		Vec3 look = viewer.getLookAngle();
		// the screen's right and up, as the player sees it
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		Mark best = null;
		double bestCos = FOCUS_COS;
		List<Mark> order = new ArrayList<>();
		Map<Mark, double[]> at = new HashMap<>();
		for (Mark m : map.values()) {
			Vec3 to = m.signAt.subtract(eye);
			double len = to.length();
			if (len < 1e-3) continue;
			Vec3 dir = to.scale(1 / len);
			double ahead = dir.dot(look);
			if (ahead <= 0.05) {
				m.show = 0;
				continue;
			}
			// where it is on screen, and how deep (sizes shrink with the depth)
			at.put(m, new double[] {dir.dot(right) / ahead, dir.dot(up) / ahead, len * ahead});
			order.add(m);
			if (ahead > bestCos) {
				bestCos = ahead;
				best = m;
			}
		}
		Mark focused = best;
		order.sort((a, b) -> a == focused ? -1 : b == focused ? 1 : Double.compare(a.metres, b.metres));
		List<Box> taken = new ArrayList<>();
		// every gem takes its own room
		for (Mark m : order) {
			double[] c = at.get(m);
			double half = HALF * m.scale / c[2];
			taken.add(new Box(c[0] - half, c[1] - half, c[0] + half, c[1] + half));
		}
		for (Mark m : order) {
			double[] c = at.get(m);
			int want = m == focused ? 2 : 1;
			// a text pixel of the label, on screen; the label sits just above the gem
			double px = 0.025 * LABEL_SIZE * m.scale / c[2];
			double width = labelText(m, want, Math.max(0, m.metres)).getString().length() * 6 * px;
			double bottom = c[1] + (HALF + 0.08) * m.scale / c[2];
			Box box = new Box(c[0] - width / 2, bottom, c[0] + width / 2, bottom + 10 * px);
			boolean free = m == focused || taken.stream().noneMatch(box::hits);
			m.show = free ? want : 0;
			if (free) taken.add(box);
		}
	}

	private static void animate(Mark m, int ticksLeft) {
		if (m.age == 1) {
			// in: the gem pops up, the light grows out of the ground
			if (m.pillar != null) grow(m.pillar, new Vector3f(1, 1, 1), 8);
			if (m.line != null) grow(m.line, new Vector3f(1, 1, 1), 6);
			if (m.ring != null) grow(m.ring, new Vector3f(1, 1, 1), 6);
			return;
		}
		if (m.pillar != null && m.age % 30 == 2) {
			// the light breathes: a little narrower, then wide again
			boolean narrow = (m.age / 30) % 2 == 0;
			grow(m.pillar, new Vector3f(narrow ? 0.8f : 1f, 1, narrow ? 0.8f : 1f), 30);
		}
		if (m.pulse != null && ticksLeft > PULSE_EVERY) {
			int phase = (m.age - 2) % PULSE_EVERY;
			// a thin ring runs out from the foot of the line and fades
			switch (phase) {
				case 0 -> {
					m.pulse.setTextOpacity((byte) 220);
					grow(m.pulse, new Vector3f(1, 1, 1), 0);
				}
				case 1 -> grow(m.pulse, new Vector3f(2.8f, 2.8f, 2.8f), 22);
				case 8 -> m.pulse.setTextOpacity((byte) 150);
				case 14 -> m.pulse.setTextOpacity((byte) 90);
				case 19 -> m.pulse.setTextOpacity((byte) 45);
				case 23 -> grow(m.pulse, new Vector3f(0.001f), 0);
				default -> {
				}
			}
		}
		if (ticksLeft <= FADE_TICKS) {
			int step = Math.min(FADE.length - 1, (FADE_TICKS - ticksLeft) / 5);
			if (step != m.fade) {
				m.fade = step;
				byte a = (byte) FADE[step];
				m.mark.setTextOpacity(a);
				m.label.setTextOpacity(a);
				m.label.setBackground(background(m));
				for (TextDisplayElement e : new TextDisplayElement[] {m.pillar, m.line, m.ring}) {
					if (e != null) e.setTextOpacity(a);
				}
			}
		}
	}

	private static void grow(TextDisplayElement e, Vector3f scale, int ticks) {
		e.setScale(scale);
		e.setInterpolationDuration(ticks);
		e.startInterpolation();
	}

	static void tick(MinecraftServer server) {
		if (MARKS.isEmpty()) return;
		long now = System.currentTimeMillis();
		for (Iterator<Map<UUID, Mark>> maps = MARKS.values().iterator(); maps.hasNext(); ) {
			Map<UUID, Mark> map = maps.next();
			if (server.getTickCount() % 3 == 0 && !map.isEmpty()) layout(map);
			for (Iterator<Mark> it = map.values().iterator(); it.hasNext(); ) {
				Mark m = it.next();
				ServerPlayer v = m.viewer;
				if (v.isRemoved() || v.hasDisconnected() || v.level() != m.ping.level() || m.ping.until() < now
						|| Pings.ACTIVE.get(m.ping.owner()) != m.ping) {
					m.destroy();
					it.remove();
					continue;
				}
				m.age++;
				float want = place(m);
				if (m.age == 1) {
					resize(m, want, 4);
				} else if (m.age > 5 && m.age % 2 == 0 && Math.abs(want - m.scale) > m.scale * 0.03f) {
					resize(m, want, 3);
				}
				if (m.age % 5 == 0 || m.show != m.shown) label(m);
				animate(m, (int) ((m.ping.until() - now) / 50));
				m.site.tick();
				m.sign.tick();
			}
			if (map.isEmpty()) maps.remove();
		}
	}
}
