package nl.libreria.core.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Libreria;
import nl.libreria.core.Pack;
import nl.libreria.core.Services;
import nl.libreria.core.Setting;
import org.jetbrains.annotations.Nullable;

/**
 * A compass bar at the top of the screen (with the Libreria pack), like in Skyrim: the directions turn as you turn,
 * and markers show where your waypoints, your land, your death point and your graves are. Look straight at a marker
 * and its name and distance appear next to it. Modules add markers with {@link #addSource}.
 *
 * <p>It is a boss bar whose title is the whole strip; the pack makes the bar itself invisible. The title always has
 * the same width, so it stays put while its contents move.
 */
public final class Compass {
	/** Something on the compass. {@code glyph}: diamond, house, skull, grave, target or person. */
	public record Marker(Vec3 pos, String glyph, int color, String name) {
	}

	/** Gives the markers for one player (only for their current world). */
	public interface Source {
		List<Marker> markers(ServerPlayer player);
	}

	public static final Setting.Choice MODE = Libreria.MODULE.choice("compass", "Compass bar", Items.COMPASS, 0,
			new int[]{0, 1, 2}, new String[]{"Always", "With a compass or map in hand", "Off"},
			"A compass at the top of your screen", "with your waypoints, land and death", "point on it (Libreria pack).");

	/** Pixels per degree: the strip shows 90° to either side. */
	private static final int HALF = 90, CENTER = 91;
	/** Within this many degrees of straight ahead, a marker shows its name and distance. */
	private static final double FOCUS = 7;
	private static final String[] POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

	private static final List<Source> SOURCES = new ArrayList<>();

	private static final class Bar {
		final ServerBossEvent event = new ServerBossEvent(UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.WHITE,
				BossEvent.BossBarOverlay.PROGRESS);
		boolean visible;
		String shown = "";
		float yaw = Float.NaN;
		int composedAt;
		List<Marker> markers = List.of();
		int markersAt = -1000;
	}

	private static final Map<UUID, Bar> BARS = new HashMap<>();

	private Compass() {
	}

	public static void addSource(Source source) {
		SOURCES.add(source);
	}

	public static void init() {
		// Loading the class registers the setting
	}

	/** From the core tick: every tick while you turn, a few times a second otherwise. */
	public static void tick(MinecraftServer server, int tick) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Bar bar = BARS.computeIfAbsent(p.getUUID(), k -> {
				Bar b = new Bar();
				b.event.setProgress(0f);
				return b;
			});
			if (!wanted(p)) {
				hide(p, bar);
				continue;
			}
			boolean turned = Float.isNaN(bar.yaw) || Math.abs(Math.toDegrees(Math.atan2(Math.sin(Math.toRadians(p.getYRot() - bar.yaw)),
					Math.cos(Math.toRadians(p.getYRot() - bar.yaw))))) > 0.4;
			if (!turned && tick - bar.composedAt < 5) continue;
			if (tick - bar.markersAt >= 20) {
				bar.markers = markers(p);
				bar.markersAt = tick;
			}
			bar.yaw = p.getYRot();
			bar.composedAt = tick;
			Component title = compose(p, bar.markers);
			String key = title.getString() + title.hashCode();
			if (!key.equals(bar.shown)) {
				bar.event.setName(title);
				bar.shown = key;
			}
			if (!bar.visible) {
				bar.event.addPlayer(p);
				bar.visible = true;
			}
		}
	}

	private static boolean wanted(ServerPlayer p) {
		if (!Pack.has(p) || p.isSpectator() || Services.inCamera(p)) return false;
		return switch (MODE.get(p)) {
			case 0 -> true;
			case 1 -> holdsCompass(p);
			default -> false;
		};
	}

	private static boolean holdsCompass(ServerPlayer p) {
		for (var st : new net.minecraft.world.item.ItemStack[] {p.getMainHandItem(), p.getOffhandItem()}) {
			if (st.is(Items.COMPASS) || st.is(Items.RECOVERY_COMPASS) || st.is(Items.FILLED_MAP) || st.is(Items.MAP)) return true;
		}
		return false;
	}

	private static void hide(ServerPlayer p, Bar bar) {
		if (!bar.visible) return;
		bar.event.removePlayer(p);
		bar.visible = false;
		bar.yaw = Float.NaN;
	}

	/** Takes the bar away now (e.g. when a module takes over the camera). */
	public static void hide(ServerPlayer p) {
		Bar bar = BARS.get(p.getUUID());
		if (bar != null) hide(p, bar);
	}

	public static void forget(UUID id) {
		Bar bar = BARS.remove(id);
		if (bar != null) bar.event.removeAllPlayers();
	}

	public static void clearAll() {
		BARS.values().forEach(b -> b.event.removeAllPlayers());
		BARS.clear();
	}

	/** Every marker for this player right now (also used by the land maps). */
	public static List<Marker> markersFor(ServerPlayer p) {
		return markers(p);
	}

	private static List<Marker> markers(ServerPlayer p) {
		List<Marker> out = new ArrayList<>();
		for (Source s : SOURCES) {
			try {
				out.addAll(s.markers(p));
			} catch (RuntimeException e) {
				Libreria.LOG.warn("Compass marker source failed", e);
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- drawing

	/** One thing on the strip: where its left edge goes, and how wide it is (its advance). */
	private record Piece(int x, Component text, int width) {
	}

	/** Which way the player looks, 0 = north, 90 = east. */
	public static double heading(float yaw) {
		return Math.floorMod((int) Math.round(yaw * 10) + 1800, 3600) / 10.0;
	}

	/** Direction from {@code from} to {@code to}, 0 = north, 90 = east. */
	public static double bearing(Vec3 from, Vec3 to) {
		double deg = Math.toDegrees(Math.atan2(to.x - from.x, -(to.z - from.z)));
		return deg < 0 ? deg + 360 : deg;
	}

	/** -180..180: how far {@code bearing} is to the right of {@code heading}. */
	public static double delta(double bearing, double heading) {
		double d = (bearing - heading) % 360;
		if (d < -180) d += 360;
		if (d >= 180) d -= 360;
		return d;
	}

	/** The whole strip as one title. */
	public static Component compose(ServerPlayer p, List<Marker> markers) {
		double heading = heading(p.getYRot());
		List<Piece> pieces = new ArrayList<>();
		// Directions and ticks every 15°
		for (int deg = 0; deg < 360; deg += 15) {
			double d = delta(deg, heading);
			if (Math.abs(d) > HALF - 3) continue;
			int x = CENTER + (int) Math.round(d);
			int color = fade(d, deg % 90 == 0 ? (deg == 0 ? 0xFFB43C : 0xF0F0F0) : 0xB4B4B4);
			if (deg % 45 == 0) {
				Component c = Component.literal(POINTS[deg / 45]).withStyle(s -> s.withColor(color));
				int w = Glyphs.width(c);
				pieces.add(new Piece(x - w / 2, c, w));
			} else {
				pieces.add(new Piece(x, Glyphs.ui(Glyphs.g("compass.tick"), fade(d, 0x8C8C8C)), Glyphs.advance("compass.tick")));
			}
		}
		// Markers, the nearest drawn last (on top)
		Vec3 eye = p.position();
		@Nullable Marker focus = null;
		double focusD = FOCUS;
		List<Marker> sorted = new ArrayList<>(markers);
		sorted.sort(Comparator.comparingDouble((Marker m) -> m.pos().distanceToSqr(eye)).reversed());
		for (Marker m : sorted) {
			double d = delta(bearing(eye, m.pos()), heading);
			if (Math.abs(d) > HALF - 4) continue;
			int x = CENTER + (int) Math.round(d);
			pieces.add(new Piece(x - 3, Glyphs.ui(Glyphs.g("compass." + m.glyph()), fade(d, m.color())), Glyphs.advance("compass." + m.glyph())));
			if (Math.abs(d) < focusD) {
				focus = m;
				focusD = Math.abs(d);
			}
		}
		if (focus != null) {
			double d = delta(bearing(eye, focus.pos()), heading);
			int x = CENTER + (int) Math.round(d);
			long dist = Math.round(Math.sqrt(focus.pos().distanceToSqr(eye)));
			Component label = Component.literal(focus.name() + " ").withStyle(s -> s.withColor(0xFFFFFF))
					.append(Component.literal(dist + " m").withStyle(s -> s.withColor(0xFFE68A)));
			int w = Glyphs.width(label);
			// Right of the marker, or left when it would run off the strip
			int lx = x + 6 + w <= CENTER * 2 - 4 ? x + 6 : x - 5 - w;
			pieces.add(new Piece(lx, label, w));
		}
		pieces.sort(Comparator.comparingInt(Piece::x));

		int adv = Glyphs.advance("compass.bar");
		MutableComponent out = Glyphs.ui(Glyphs.g("compass.bar") + Glyphs.space(-adv), 0xFFFFFF);
		int cursor = 0;
		for (Piece piece : pieces) {
			int gap = piece.x() - cursor;
			if (gap != 0) out.append(Glyphs.ui(Glyphs.space(gap)));
			out.append(piece.text());
			cursor = piece.x() + piece.width();
		}
		// End exactly where the strip ends, so the title's width never changes
		if (adv - cursor != 0) out.append(Glyphs.ui(Glyphs.space(adv - cursor)));
		return out;
	}

	/** Fainter towards the ends of the strip. */
	private static int fade(double d, int rgb) {
		double t = Math.max(0, (Math.abs(d) - 55) / (HALF - 55));
		double k = 1 - 0.65 * Math.min(1, t);
		int r = (int) (((rgb >> 16) & 0xFF) * k), g = (int) (((rgb >> 8) & 0xFF) * k), b = (int) ((rgb & 0xFF) * k);
		return (r << 16) | (g << 8) | b;
	}
}
