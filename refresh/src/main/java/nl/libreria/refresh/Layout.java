package nl.libreria.refresh;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import nl.libreria.refresh.mixin.StructureStateAccessor;

/**
 * Where structures go. Each refresh of a world (with "structures move" on) gives it a new layout: Minecraft places
 * villages, temples, cities and the rest by a seed, and the world's own seed plus the layout number makes another
 * one. The land itself comes from the world's seed as always, so what's made anew fits what stayed; strongholds keep
 * their own seed and stay where they were. And no new structure is made where it would reach into land kept from
 * before (it would stop at the edge).
 */
public final class Layout {
	private Layout() {
	}

	private static final Map<String, Integer> LAYOUT = new ConcurrentHashMap<>();
	private static final Map<String, LongOpenHashSet> KEPT = new ConcurrentHashMap<>();
	private static final long STEP = 0x9E3779B97F4A7C15L;

	static Path dir(Path root) {
		return root.resolve("libreria").resolve("refresh");
	}

	static String safe(String id) {
		return id.replace(':', '_').replace('/', '_');
	}

	static int layout(String id) {
		return LAYOUT.getOrDefault(id, 0);
	}

	static void bump(String id) {
		LAYOUT.merge(id, 1, Integer::sum);
	}

	static void read(Path dir) {
		LAYOUT.clear();
		KEPT.clear();
		Path f = dir.resolve("layout.json");
		if (Files.isRegularFile(f)) {
			try {
				Map<String, Integer> m = new Gson().fromJson(Files.readString(f), new TypeToken<Map<String, Integer>>() {
				}.getType());
				if (m != null) LAYOUT.putAll(m);
			} catch (IOException | RuntimeException e) {
				RefreshMod.LOG.warn("World refresh: can't read {}: {}", f, e.toString());
			}
		}
		if (!Files.isDirectory(dir)) return;
		try (var files = Files.list(dir)) {
			for (Path p : files.toList()) {
				String n = p.getFileName().toString();
				if (!n.startsWith("kept-") || !n.endsWith(".bin")) continue;
				try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(Files.newInputStream(p)))) {
					String id = in.readUTF();
					int count = in.readInt();
					LongOpenHashSet set = new LongOpenHashSet(count);
					for (int i = 0; i < count; i++) set.add(in.readLong());
					KEPT.put(id, set);
				}
			}
		} catch (IOException e) {
			RefreshMod.LOG.warn("World refresh: can't read the kept land: {}", e.toString());
		}
	}

	static void save(Path dir) throws IOException {
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("layout.json"), new Gson().toJson(new HashMap<>(LAYOUT)));
	}

	static void saveKept(Path dir, String id, java.util.Collection<Long> keys) throws IOException {
		Files.createDirectories(dir);
		try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(dir.resolve("kept-" + safe(id) + ".bin"))))) {
			out.writeUTF(id);
			out.writeInt(keys.size());
			for (long k : keys) out.writeLong(k);
		}
	}

	/** For the self-test: this world's kept land (null: none). */
	static void testKept(String id, java.util.Set<Long> keys) {
		if (keys == null) KEPT.remove(id);
		else KEPT.put(id, new LongOpenHashSet(keys));
	}

	/** A world's structures placed by its layout (from the mixin, as the world is made at startup). */
	public static void apply(ResourceKey<Level> dim, ChunkGeneratorStructureState state) {
		int n = layout(dim.identifier().toString());
		if (n <= 0) return;
		((StructureStateAccessor) (Object) state).libreria$setLevelSeed(state.getLevelSeed() + n * STEP);
		RefreshMod.LOG.info("World refresh: {} places its structures by layout {}", dim.identifier(), n);
	}

	/** Would a structure this big reach into land kept from before a refresh of this world? */
	public static boolean overlapsKept(ResourceKey<Level> dim, BoundingBox box) {
		LongOpenHashSet kept = KEPT.get(dim.identifier().toString());
		if (kept == null || kept.isEmpty()) return false;
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) if (kept.contains(Scan.key(cx, cz))) return true;
		}
		return false;
	}
}
