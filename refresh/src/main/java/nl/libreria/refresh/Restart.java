package nl.libreria.refresh;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * The refresh itself, at a restart. When it is planned, the list of chunks that go is written per world (again as the
 * server stops, with the land as it is then). At the next start, before the worlds are opened, those chunks are taken
 * out of the world's files (blocks, things and places), and the world gets a new layout for its structures. They come
 * back fresh when someone goes there.
 */
final class Restart {
	private Restart() {
	}

	private static final int MAGIC = 0x4C525032;

	private static Path planFile(Path dir, String id) {
		return dir.resolve("plan-" + Layout.safe(id) + ".bin");
	}

	/** Is reading this world finished (so how long people were near every chunk is known)? */
	static boolean ready(Scan.World w) {
		return w.queue.isEmpty();
	}

	/**
	 * Writes (again) the lists of what goes, for every world that takes part and has been read wholly; a world that
	 * isn't read wholly yet keeps the list it had. @return per world, how many chunks go
	 */
	static Map<String, Integer> write(MinecraftServer server) {
		Map<String, Integer> out = new HashMap<>();
		Path dir = Layout.dir(Scan.root(server));
		for (Scan.World w : Scan.WORLDS.values()) {
			String id = w.dim.identifier().toString();
			Plan.Ctx ctx = new Plan.Ctx(server, w.dim);
			Path f = planFile(dir, id);
			if (!ctx.on) {
				try {
					Files.deleteIfExists(f);
				} catch (IOException ignored) {
				}
				continue;
			}
			if (!ready(w)) continue;
			List<Long> going = Plan.going(ctx, w);
			try {
				writePlan(f, id, RefreshMod.MOVE.get(), going);
				out.put(id, going.size());
			} catch (IOException e) {
				RefreshMod.LOG.error("World refresh: can't write the plan for {}: {}", id, e.toString());
			}
		}
		return out;
	}

	static void writePlan(Path f, String id, boolean move, List<Long> going) throws IOException {
		Files.createDirectories(f.getParent());
		try (DataOutputStream o = new DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(f)))) {
			o.writeInt(MAGIC);
			o.writeUTF(id);
			o.writeBoolean(move);
			o.writeInt(going.size());
			for (long k : going) o.writeLong(k);
		}
	}

	/** Nothing planned any more. */
	static void cancel(MinecraftServer server) {
		Path dir = Layout.dir(Scan.root(server));
		try (var files = Files.list(dir)) {
			for (Path p : files.toList()) if (p.getFileName().toString().startsWith("plan-")) Files.deleteIfExists(p);
		} catch (IOException ignored) {
		}
	}

	/**
	 * At startup, before the worlds open: every planned world loses its chunks that go (blocks, things and places) and
	 * gets a new layout for its structures. Then the layouts (and the land kept from before) are read for the worlds to use.
	 */
	static void apply(MinecraftServer server) {
		Path root = Scan.root(server), dir = Layout.dir(root);
		Layout.read(dir);
		List<String> summary = new ArrayList<>();
		if (Files.isDirectory(dir)) {
			try (var files = Files.list(dir)) {
				for (Path p : files.toList()) {
					if (!p.getFileName().toString().startsWith("plan-")) continue;
					String done = applyOne(root, dir, p);
					if (done != null) summary.add(done);
					Files.deleteIfExists(p);
				}
			} catch (IOException e) {
				RefreshMod.LOG.error("World refresh: can't read the plans: {}", e.toString());
			}
		}
		if (!summary.isEmpty()) {
			try {
				Layout.save(dir);
				Files.writeString(dir.resolve("done.txt"), System.currentTimeMillis() + "\n" + String.join("\n", summary));
			} catch (IOException e) {
				RefreshMod.LOG.error("World refresh: can't save the new layouts: {}", e.toString());
			}
			Layout.read(dir);
		}
	}

	static String applyOne(Path root, Path dir, Path plan) throws IOException {
		String id;
		boolean move;
		Set<Long> going = new HashSet<>();
		try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(Files.newInputStream(plan)))) {
			if (in.readInt() != MAGIC) return null;
			id = in.readUTF();
			move = in.readBoolean();
			int n = in.readInt();
			for (int i = 0; i < n; i++) going.add(in.readLong());
		}
		ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, Identifier.parse(id));
		Path folder = DimensionType.getStorageFolder(dim, root);
		// Per region: which of its chunks go
		Map<Long, Set<Integer>> byRegion = new HashMap<>();
		for (long k : going) {
			int cx = Scan.kx(k), cz = Scan.kz(k);
			byRegion.computeIfAbsent(Scan.key(cx >> 5, cz >> 5), r -> new HashSet<>()).add((cx & 31) + (cz & 31) * 32);
		}
		int removed = 0;
		for (String sub : new String[] {"region", "entities", "poi"}) {
			for (var e : byRegion.entrySet()) {
				Path f = Region.file(folder.resolve(sub), Scan.kx(e.getKey()), Scan.kz(e.getKey()));
				int n = Region.remove(f, e.getValue()::contains);
				if (sub.equals("region")) removed += n;
			}
		}
		if (move) Layout.bump(id);
		// The land that stays (from before this refresh): no new structure may reach into it
		List<Long> kept = new ArrayList<>();
		for (Path f : Region.files(folder.resolve("region"))) {
			int[] rc = Region.coords(f);
			int[] head = Region.header(f);
			for (int i = 0; i < 1024; i++) if (head[i] != 0) kept.add(Scan.key(rc[0] * 32 + (i & 31), rc[1] * 32 + (i >> 5)));
		}
		Layout.saveKept(dir, id, kept);
		RefreshMod.LOG.info("World refresh: {} chunks of {} made anew (structures {}); {} stay", removed, id, move ? "move" : "stay", kept.size());
		return id + "\t" + removed + "\t" + kept.size() + "\t" + move;
	}
}
