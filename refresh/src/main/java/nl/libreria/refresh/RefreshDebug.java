package nl.libreria.refresh;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** For the screenshots (CI). */
public final class RefreshDebug {
	private RefreshDebug() {
	}

	private static ResourceKey<Level> key(String id) {
		return ResourceKey.create(Registries.DIMENSION, Identifier.parse(id));
	}

	/** The overworld takes part, and a village of the screenshots is kept by hand. */
	public static void prepare(ServerPlayer p) {
		Plan.dim("minecraft:overworld").on = true;
		int cx = ((int) p.getX() + 600) >> 4, cz = (int) p.getZ() >> 4;
		for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) Plan.pick("minecraft:overworld", Scan.key(cx + dx, cz + dz), true);
		Plan.STORE.markDirty();
	}

	/** How far reading the worlds has come (the least of them). */
	public static double progress() {
		double least = 1;
		for (Scan.World w : Scan.WORLDS.values()) least = Math.min(least, w.progress());
		return least;
	}

	public static void window(ServerPlayer p) {
		RefreshCanvas.open(p);
	}

	public static void map(ServerPlayer p, String dim, int zoom) {
		MapScreen.open(p, key(dim));
		MapScreen.debugZoom(p, zoom);
	}

	public static void zoom(ServerPlayer p, int zoom) {
		MapScreen.debugZoom(p, zoom);
	}

	public static void brush(ServerPlayer p, boolean painting, boolean stays, boolean region, boolean stroke) {
		MapScreen.debugBrush(p, painting, stays, region, stroke);
	}

	public static void brush(ServerPlayer p, boolean painting, boolean stays, boolean region, boolean stroke) {
		MapScreen.debugBrush(p, painting, stays, region, stroke);
	}

	public static void world(ServerPlayer p, String dim) {
		MapScreen.debugWorld(p, key(dim));
	}

	public static void close(ServerPlayer p) {
		MapScreen.closeQuietly(p);
	}
}
