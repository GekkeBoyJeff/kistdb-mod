package nl.libreria.refresh;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * What exists of each world, chunk by chunk: whether it was made (wholly, or only partly at the edge of what people
 * saw), how long people were near it (Minecraft keeps that per chunk) and what it looks like from above. Read once in
 * the background from how the world is saved, a few chunks at a time through Minecraft's own reading, and kept up to
 * date whenever a chunk is put away; remembered between restarts in a small file per region.
 */
final class Scan {
	private Scan() {
	}

	/** Not there; there but not read yet; only partly made; made. */
	static final byte NONE = 0, THERE = 1, PART = 2, MADE = 3;

	/** One region (32 × 32 chunks) of a world. */
	static final class Reg {
		final int rx, rz;
		/** Per chunk (x + z × 32): when it was saved as we read it (seconds), its state, ticks people were near, its size on disk (sectors). */
		final int[] stamp = new int[1024];
		final byte[] state = new byte[1024];
		final long[] near = new long[1024];
		final short[] sectors = new short[1024];
		/** 256 map colours per chunk, or null while none is known. */
		volatile byte[] colors;
		volatile boolean dirty;

		Reg(int rx, int rz) {
			this.rx = rx;
			this.rz = rz;
		}

		synchronized byte[] colors() {
			if (this.colors == null) this.colors = new byte[1024 * 256];
			return this.colors;
		}
	}

	/** A world: its regions, and where it is saved. */
	static final class World {
		final ResourceKey<Level> dim;
		final Path folder, cache;
		final int minY, height;
		final Map<Long, Reg> regions = new ConcurrentHashMap<>();
		/** Chunks still to read, and how many there were to read. */
		final ArrayDeque<long[]> queue = new ArrayDeque<>();
		int total;

		World(ResourceKey<Level> dim, Path folder, Path cache, int minY, int height) {
			this.dim = dim;
			this.folder = folder;
			this.cache = cache;
			this.minY = minY;
			this.height = height;
		}

		Reg reg(int rx, int rz, boolean create) {
			long k = key(rx, rz);
			Reg r = this.regions.get(k);
			if (r == null && create) {
				r = new Reg(rx, rz);
				this.regions.put(k, r);
			}
			return r;
		}

		/** How a chunk is: {@link #NONE} … {@link #MADE}. */
		byte state(int cx, int cz) {
			Reg r = reg(cx >> 5, cz >> 5, false);
			return r == null ? NONE : r.state[(cx & 31) + (cz & 31) * 32];
		}

		long near(int cx, int cz) {
			Reg r = reg(cx >> 5, cz >> 5, false);
			return r == null ? 0 : r.near[(cx & 31) + (cz & 31) * 32];
		}

		/** The map colour at block {@code (bx, bz)} of chunk {@code (cx, cz)}, or {@link Colors#UNKNOWN}. */
		byte color(int cx, int cz, int bx, int bz) {
			Reg r = reg(cx >> 5, cz >> 5, false);
			if (r == null) return Colors.UNKNOWN;
			byte[] c = r.colors;
			return c == null ? Colors.UNKNOWN : c[((cx & 31) + (cz & 31) * 32) * 256 + bz * 16 + bx];
		}

		/** How far reading the world has come (0 – 1). */
		double progress() {
			return this.total == 0 ? 1 : 1 - this.queue.size() / (double) this.total;
		}

		/** Every chunk that exists, as keys. */
		List<Long> chunks() {
			List<Long> out = new ArrayList<>();
			for (Reg r : this.regions.values()) {
				for (int i = 0; i < 1024; i++) if (r.state[i] != NONE) out.add(key(r.rx * 32 + (i & 31), r.rz * 32 + (i >> 5)));
			}
			return out;
		}
	}

	static final Map<ResourceKey<Level>, World> WORLDS = new LinkedHashMap<>();
	private static final AtomicInteger IN_FLIGHT = new AtomicInteger();
	private static final int MAX_IN_FLIGHT = 24;
	private static @Nullable ExecutorService exec;
	private static int lastSave;

	/** Chunk (or region) x and z as one number: x in the low 32 bits, z in the high (as claims give them). */
	static long key(int x, int z) {
		return (x & 0xFFFFFFFFL) | ((long) z << 32);
	}

	static int kx(long key) {
		return (int) key;
	}

	static int kz(long key) {
		return (int) (key >> 32);
	}

	static Path root(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT);
	}

	static @Nullable World world(ResourceKey<Level> dim) {
		return WORLDS.get(dim);
	}

	// ---------------------------------------------------------------- start, stop

	static void start(MinecraftServer server) {
		WORLDS.clear();
		exec = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "Libreria world refresh");
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY);
			return t;
		});
		Path root = root(server);
		for (ServerLevel lv : server.getAllLevels()) {
			ResourceKey<Level> dim = lv.dimension();
			Path folder = DimensionType.getStorageFolder(dim, root);
			Path cache = root.resolve("libreria").resolve("refresh").resolve("cache").resolve(dim.identifier().toString().replace(':', '_').replace('/', '_'));
			World w = new World(dim, folder, cache, lv.getMinY(), lv.getHeight());
			load(w);
			WORLDS.put(dim, w);
			headers(w);
		}
	}

	static void stop(MinecraftServer server) {
		// What's loaded now is the freshest
		for (ServerLevel lv : server.getAllLevels()) refreshLoaded(lv);
		if (exec != null) {
			exec.shutdown();
			try {
				exec.awaitTermination(3, TimeUnit.SECONDS);
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
		}
		saveAll();
	}

	/**
	 * Which chunks the region files hold now: new ones and ones saved since we read them go in the queue to be read,
	 * ones that are gone are forgotten.
	 */
	static void headers(World w) {
		w.queue.clear();
		java.util.Set<Long> seen = new java.util.HashSet<>();
		for (Path f : Region.files(w.folder.resolve("region"))) {
			int[] rc = Region.coords(f);
			int[] head = Region.header(f);
			Reg r = w.reg(rc[0], rc[1], true);
			seen.add(key(rc[0], rc[1]));
			for (int i = 0; i < 1024; i++) {
				int loc = head[i];
				if (loc == 0) {
					if (r.state[i] != NONE) forget(r, i);
					continue;
				}
				r.sectors[i] = (short) (loc & 0xFF);
				if (r.state[i] == NONE) r.state[i] = THERE;
				if (head[1024 + i] != r.stamp[i] || r.state[i] == THERE) w.queue.add(new long[] {key(rc[0] * 32 + (i & 31), rc[1] * 32 + (i >> 5)), head[1024 + i]});
			}
		}
		for (Reg r : w.regions.values()) {
			if (seen.contains(key(r.rx, r.rz))) continue;
			for (int i = 0; i < 1024; i++) if (r.state[i] != NONE) forget(r, i);
		}
		w.total = w.queue.size();
	}

	private static void forget(Reg r, int i) {
		r.state[i] = NONE;
		r.stamp[i] = 0;
		r.near[i] = 0;
		r.sectors[i] = 0;
		byte[] c = r.colors;
		if (c != null) java.util.Arrays.fill(c, i * 256, i * 256 + 256, Colors.UNKNOWN);
		r.dirty = true;
	}

	// ---------------------------------------------------------------- reading

	/** Every tick: a few more chunks read (loaded ones straight from the world), and every five minutes a save. */
	static void tick(MinecraftServer server) {
		if (exec == null) return;
		for (World w : WORLDS.values()) {
			ServerLevel lv = server.getLevel(w.dim);
			if (lv == null) continue;
			while (IN_FLIGHT.get() < MAX_IN_FLIGHT && !w.queue.isEmpty()) {
				long[] item = w.queue.poll();
				int cx = kx(item[0]), cz = kz(item[0]);
				LevelChunk loaded = lv.getChunkSource().getChunkNow(cx, cz);
				if (loaded != null) {
					live(lv, loaded);
					continue;
				}
				int stamp = (int) item[1];
				IN_FLIGHT.incrementAndGet();
				ExecutorService ex = exec;
				lv.getChunkSource().chunkMap.read(new ChunkPos(cx, cz)).whenCompleteAsync((opt, err) -> {
					try {
						if (err == null && opt != null && opt.isPresent()) read(w, cx, cz, opt.get(), stamp);
					} catch (RuntimeException e) {
						RefreshMod.LOG.debug("World refresh: chunk {} {}: {}", cx, cz, e.toString());
					} finally {
						IN_FLIGHT.decrementAndGet();
					}
				}, ex);
			}
		}
		int now = server.getTickCount();
		if (now - lastSave > 6000) {
			lastSave = now;
			saveAll();
		}
	}

	/** A chunk as saved. */
	static void read(World w, int cx, int cz, CompoundTag tag, int stamp) {
		Reg r = w.reg(cx >> 5, cz >> 5, true);
		int i = (cx & 31) + (cz & 31) * 32;
		String status = tag.getStringOr("Status", "");
		boolean made = status.equals("minecraft:full") || status.equals("full");
		r.near[i] = tag.getLongOr("InhabitedTime", 0L);
		if (made && Colors.fromTag(tag, w.minY, w.height, r.colors(), i * 256)) {
			r.state[i] = MADE;
		} else {
			r.state[i] = made ? MADE : PART;
		}
		r.stamp[i] = stamp;
		r.dirty = true;
	}

	/** A loaded chunk, straight from the world (also when it's put away). */
	static void live(ServerLevel lv, LevelChunk chunk) {
		World w = WORLDS.get(lv.dimension());
		if (w == null) return;
		int cx = chunk.getPos().x(), cz = chunk.getPos().z();
		Reg r = w.reg(cx >> 5, cz >> 5, true);
		int i = (cx & 31) + (cz & 31) * 32;
		Colors.fromChunk(chunk, r.colors(), i * 256);
		r.near[i] = chunk.getInhabitedTime();
		r.state[i] = MADE;
		// (saved just after this: don't read it again for that)
		r.stamp[i] = (int) (System.currentTimeMillis() / 1000) + 120;
		r.dirty = true;
	}

	/** The chunks loaded now, per world (Minecraft only lists those a player sees). */
	private static final Map<ResourceKey<Level>, Map<Long, LevelChunk>> LOADED = new ConcurrentHashMap<>();

	static void loaded(ServerLevel lv, LevelChunk c) {
		LOADED.computeIfAbsent(lv.dimension(), k -> new ConcurrentHashMap<>()).put(key(c.getPos().x(), c.getPos().z()), c);
	}

	static void unloaded(ServerLevel lv, LevelChunk c) {
		live(lv, c);
		Map<Long, LevelChunk> m = LOADED.get(lv.dimension());
		if (m != null) m.remove(key(c.getPos().x(), c.getPos().z()));
	}

	/** Every chunk loaded now, straight from the world (when its map opens). */
	static void refreshLoaded(ServerLevel lv) {
		Map<Long, LevelChunk> m = LOADED.get(lv.dimension());
		if (m != null) for (LevelChunk c : new ArrayList<>(m.values())) live(lv, c);
	}

	// ---------------------------------------------------------------- remembering

	private static final int MAGIC = 0x4C524631;

	static void saveAll() {
		for (World w : WORLDS.values()) {
			for (Reg r : w.regions.values()) {
				if (!r.dirty) continue;
				r.dirty = false;
				try {
					Files.createDirectories(w.cache);
					Path f = w.cache.resolve("r." + r.rx + "." + r.rz + ".bin");
					Path tmp = w.cache.resolve("r." + r.rx + "." + r.rz + ".tmp");
					try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))) {
						out.writeInt(MAGIC);
						for (int i = 0; i < 1024; i++) {
							out.writeInt(r.stamp[i]);
							out.writeByte(r.state[i]);
							out.writeLong(r.near[i]);
							out.writeShort(r.sectors[i]);
						}
						byte[] c = r.colors;
						out.writeBoolean(c != null);
						if (c != null) out.write(c);
					}
					Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					RefreshMod.LOG.warn("World refresh: can't save the map of {}: {}", w.dim.identifier(), e.toString());
				}
			}
		}
	}

	private static void load(World w) {
		if (!Files.isDirectory(w.cache)) return;
		try (var files = Files.list(w.cache)) {
			for (Path f : files.toList()) {
				String n = f.getFileName().toString();
				if (!n.startsWith("r.") || !n.endsWith(".bin")) continue;
				String[] p = n.substring(2, n.length() - 4).split("\\.");
				if (p.length != 2) continue;
				Reg r = w.reg(Integer.parseInt(p[0]), Integer.parseInt(p[1]), true);
				try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new GZIPInputStream(Files.newInputStream(f))))) {
					if (in.readInt() != MAGIC) continue;
					for (int i = 0; i < 1024; i++) {
						r.stamp[i] = in.readInt();
						r.state[i] = in.readByte();
						r.near[i] = in.readLong();
						r.sectors[i] = in.readShort();
					}
					if (in.readBoolean()) in.readFully(r.colors());
				} catch (IOException | RuntimeException e) {
					RefreshMod.LOG.debug("World refresh: map cache {}: {}", f, e.toString());
				}
			}
		} catch (IOException | NumberFormatException e) {
			RefreshMod.LOG.warn("World refresh: can't read the map cache of {}: {}", w.dim.identifier(), e.toString());
		}
	}
}
