package nl.libreria.core.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Libreria;

/**
 * Places worth marking on a map: your waypoints, your death point, your graves, your land, the beam you follow.
 * Modules add a {@link Source}; the land maps (3D and the map item) draw what they give.
 */
public final class Markers {
	/** One marker. {@code glyph}: diamond, house, skull, grave, target or person (a small white picture, tinted). */
	public record Marker(Vec3 pos, String glyph, int color, String name) {
	}

	/** Gives the markers for one player (only for their current world). */
	public interface Source {
		List<Marker> markers(ServerPlayer player);
	}

	private static final List<Source> SOURCES = new ArrayList<>();

	private Markers() {
	}

	public static void addSource(Source source) {
		SOURCES.add(source);
	}

	/** Every marker for this player right now. */
	public static List<Marker> forPlayer(ServerPlayer p) {
		List<Marker> out = new ArrayList<>();
		for (Source s : SOURCES) {
			try {
				out.addAll(s.markers(p));
			} catch (RuntimeException e) {
				Libreria.LOG.warn("Marker source failed", e);
			}
		}
		return out;
	}
}
