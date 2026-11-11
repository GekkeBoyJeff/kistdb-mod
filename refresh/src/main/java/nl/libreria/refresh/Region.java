package nl.libreria.refresh;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minecraft's region files (r.X.Z.mca: 32 × 32 chunks). Which chunks one holds, and taking chunks out of one: what's
 * left is written again without the gaps (so the file gets smaller), an empty one goes. Only with the world closed.
 */
final class Region {
	private Region() {
	}

	static final int SECTOR = 4096;
	private static final Pattern NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

	/** A region file's place (in regions), or null when it isn't one. */
	static int[] coords(Path file) {
		Matcher m = NAME.matcher(file.getFileName().toString());
		return m.matches() ? new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : null;
	}

	static Path file(Path folder, int rx, int rz) {
		return folder.resolve("r." + rx + "." + rz + ".mca");
	}

	/** Its header: 1024 locations, then 1024 times (seconds); all 0 for a missing or broken file. */
	static int[] header(Path file) {
		int[] out = new int[2048];
		if (!Files.isRegularFile(file)) return out;
		try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
			if (ch.size() < 2 * SECTOR) return out;
			ByteBuffer b = ByteBuffer.allocate(2 * SECTOR);
			while (b.hasRemaining() && ch.read(b) > 0) {
			}
			b.flip();
			for (int i = 0; i < 2048 && b.remaining() >= 4; i++) out[i] = b.getInt();
		} catch (IOException e) {
			RefreshMod.LOG.warn("World refresh: can't read {}: {}", file, e.toString());
		}
		return out;
	}

	/** The region files in a folder. */
	static List<Path> files(Path folder) {
		List<Path> out = new ArrayList<>();
		if (!Files.isDirectory(folder)) return out;
		try (var s = Files.list(folder)) {
			s.filter(p -> coords(p) != null).forEach(out::add);
		} catch (IOException e) {
			RefreshMod.LOG.warn("World refresh: can't list {}: {}", folder, e.toString());
		}
		return out;
	}

	/**
	 * Takes the chunks {@code remove} says yes to (their index in the region: x + z × 32) out of the file, and writes
	 * the rest again without gaps. Chunks too big for the file (c.X.Z.mcc beside it) go too. An empty file is deleted.
	 * @return how many chunks were taken out
	 */
	static int remove(Path file, IntPredicate remove) throws IOException {
		int[] rc = coords(file);
		if (rc == null || !Files.isRegularFile(file)) return 0;
		int[] head = header(file);
		List<int[]> keep = new ArrayList<>();
		int removed = 0;
		for (int i = 0; i < 1024; i++) {
			int loc = head[i];
			if (loc == 0) continue;
			if (remove.test(i)) {
				removed++;
				int cx = rc[0] * 32 + (i & 31), cz = rc[1] * 32 + (i >> 5);
				Files.deleteIfExists(file.resolveSibling("c." + cx + "." + cz + ".mcc"));
			} else {
				keep.add(new int[] {i, loc >>> 8, loc & 0xFF, head[1024 + i]});
			}
		}
		if (removed == 0) return 0;
		if (keep.isEmpty()) {
			Files.delete(file);
			return removed;
		}
		keep.sort((a, b) -> Integer.compare(a[1], b[1]));
		Path tmp = file.resolveSibling(file.getFileName() + ".refresh");
		try (FileChannel in = FileChannel.open(file, StandardOpenOption.READ);
				FileChannel out = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
			ByteBuffer header = ByteBuffer.allocate(2 * SECTOR);
			int at = 2;
			ByteBuffer data = ByteBuffer.allocate(SECTOR);
			List<int[]> placed = new ArrayList<>();
			out.position(2L * SECTOR);
			for (int[] k : keep) {
				int count = k[2];
				long from = (long) k[1] * SECTOR;
				for (int s = 0; s < count; s++) {
					data.clear();
					int n = 0;
					while (data.hasRemaining()) {
						int r = in.read(data, from + (long) s * SECTOR + data.position());
						if (r <= 0) break;
						n += r;
					}
					// past the end of a short file: zeros
					while (data.hasRemaining()) data.put((byte) 0);
					data.flip();
					while (data.hasRemaining()) out.write(data);
				}
				placed.add(new int[] {k[0], at, count, k[3]});
				at += count;
			}
			int[] locs = new int[1024], times = new int[1024];
			for (int[] p : placed) {
				locs[p[0]] = (p[1] << 8) | (p[2] & 0xFF);
				times[p[0]] = p[3];
			}
			for (int v : locs) header.putInt(v);
			for (int v : times) header.putInt(v);
			header.flip();
			out.position(0);
			while (header.hasRemaining()) out.write(header);
		}
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		return removed;
	}
}
