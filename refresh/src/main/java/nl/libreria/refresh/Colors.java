package nl.libreria.refresh;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * A chunk seen from above, as a map shows it: 16 × 16 map colours (with their shade), from a loaded chunk or from how
 * it is saved. Shading as on a real map: lighter where the ground rises northwards, darker where it falls; water darker
 * where it is deeper.
 */
final class Colors {
	private Colors() {
	}

	/** Nothing known (a chunk only partly made, or one without blocks). */
	static final byte UNKNOWN = 0;

	/** From a loaded chunk, into {@code out} at {@code off} (256 bytes, z rows of x). */
	static void fromChunk(LevelChunk chunk, byte[] out, int off) {
		int minY = chunk.getMinY();
		int[] heights = new int[256];
		int[] water = new int[256];
		MapColor[] colors = new MapColor[256];
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos((chunk.getPos().x() << 4) + x, y, (chunk.getPos().z() << 4) + z);
				MapColor mc = MapColor.NONE;
				int steps = 0;
				while (y >= minY && steps++ < 48) {
					pos.setY(y);
					BlockState st = chunk.getBlockState(pos);
					mc = st.getMapColor(chunk, pos);
					if (mc != MapColor.NONE) break;
					y--;
				}
				int depth = 0;
				if (mc == MapColor.WATER) {
					int wy = y - 1;
					while (wy >= minY && depth < 12) {
						pos.setY(wy);
						if (chunk.getBlockState(pos).getMapColor(chunk, pos) != MapColor.WATER) break;
						depth++;
						wy--;
					}
				}
				int i = z * 16 + x;
				heights[i] = y;
				water[i] = depth;
				colors[i] = mc;
			}
		}
		shade(colors, heights, water, out, off);
	}

	/** Map colour of a block by its name (as saved), remembered. */
	private static final Map<String, MapColor> BY_NAME = new ConcurrentHashMap<>();

	private static MapColor colorOf(String name) {
		return BY_NAME.computeIfAbsent(name, n -> {
			try {
				Block b = BuiltInRegistries.BLOCK.getValue(Identifier.parse(n));
				return b == null ? MapColor.NONE : b.defaultMapColor();
			} catch (RuntimeException e) {
				return MapColor.NONE;
			}
		});
	}

	/** One saved section: its palette (block names) and where each block is. */
	private record Section(String[] palette, long[] data, int bits) {
		String at(int x, int y, int z) {
			if (this.palette.length == 1 || this.data == null) return this.palette.length == 0 ? "minecraft:air" : this.palette[0];
			int i = (y & 15) * 256 + z * 16 + x;
			int per = 64 / this.bits;
			int word = i / per;
			if (word >= this.data.length) return "minecraft:air";
			int v = (int) ((this.data[word] >>> ((i % per) * this.bits)) & ((1L << this.bits) - 1));
			return v < this.palette.length ? this.palette[v] : "minecraft:air";
		}
	}

	/**
	 * From how a chunk is saved: its height map and the blocks there. False when it can't be read (a chunk only partly
	 * made has no height map yet): then {@code out} keeps {@link #UNKNOWN}.
	 */
	static boolean fromTag(CompoundTag tag, int minY, int height, byte[] out, int off) {
		long[] surface = surface(tag);
		if (surface == null || surface.length == 0) return false;
		Map<Integer, Section> sections = new java.util.HashMap<>();
		ListTag list = tag.getListOrEmpty("sections");
		for (int k = 0; k < list.size(); k++) {
			if (!(list.get(k) instanceof CompoundTag sec)) continue;
			int sy = sectionY(sec);
			CompoundTag states = sec.getCompoundOrEmpty("block_states");
			ListTag pal = states.getListOrEmpty("palette");
			if (sy == Integer.MIN_VALUE || pal.isEmpty()) continue;
			String[] names = new String[pal.size()];
			for (int p = 0; p < pal.size(); p++) names[p] = blockName(pal.get(p));
			long[] data = states.get("data") instanceof LongArrayTag d ? d.getAsLongArray() : null;
			int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(Math.max(1, names.length - 1)));
			sections.put(sy, new Section(names, data, bits));
		}
		int hbits = 32 - Integer.numberOfLeadingZeros(height);
		int per = 64 / hbits;
		int[] heights = new int[256];
		int[] water = new int[256];
		MapColor[] colors = new MapColor[256];
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				int i = z * 16 + x;
				int word = i / per;
				int v = word < surface.length ? (int) ((surface[word] >>> ((i % per) * hbits)) & ((1L << hbits) - 1)) : 0;
				int y = minY + v - 1;
				MapColor mc = MapColor.NONE;
				int steps = 0;
				while (y >= minY && steps++ < 48) {
					Section s = sections.get(Math.floorDiv(y, 16));
					if (s != null) {
						mc = colorOf(s.at(x, y, z));
						if (mc != MapColor.NONE) break;
					}
					y--;
				}
				int depth = 0;
				if (mc == MapColor.WATER) {
					int wy = y - 1;
					while (wy >= minY && depth < 12) {
						Section s = sections.get(Math.floorDiv(wy, 16));
						if (s == null || colorOf(s.at(x, wy, z)) != MapColor.WATER) break;
						depth++;
						wy--;
					}
				}
				heights[i] = y;
				water[i] = depth;
				colors[i] = mc;
			}
		}
		shade(colors, heights, water, out, off);
		return true;
	}

	/** The shade of each column, the way Minecraft's maps shade them. */
	private static void shade(MapColor[] colors, int[] heights, int[] water, byte[] out, int off) {
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				int i = z * 16 + x;
				MapColor mc = colors[i];
				if (mc == null || mc == MapColor.NONE) {
					out[off + i] = UNKNOWN;
					continue;
				}
				MapColor.Brightness b;
				if (mc == MapColor.WATER) {
					double d = water[i] * 0.1 + ((x + z) & 1) * 0.2;
					b = d < 0.5 ? MapColor.Brightness.HIGH : d > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
				} else {
					int above = z > 0 ? heights[i - 16] : heights[i];
					double d = (heights[i] - above) * 0.8 + (((x + z) & 1) - 0.5) * 0.4;
					b = d > 0.6 ? MapColor.Brightness.HIGH : d < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
				}
				out[off + i] = mc.getPackedId(b);
			}
		}
	}

	/** A block of a saved palette: {Name: …} (or just its name, with its properties after it). */
	private static String blockName(Tag t) {
		if (t instanceof CompoundTag e) return e.getStringOr("Name", "minecraft:air");
		if (t instanceof net.minecraft.nbt.StringTag st) {
			String v = st.value();
			int b = v.indexOf('[');
			return b < 0 ? v : v.substring(0, b);
		}
		return "minecraft:air";
	}

	/** A section's height (in sections), saved as a byte. */
	private static int sectionY(CompoundTag sec) {
		Tag t = sec.get("Y");
		if (t == null) return Integer.MIN_VALUE;
		if (t instanceof net.minecraft.nbt.ByteTag b) return b.value();
		if (t instanceof net.minecraft.nbt.IntTag i) return i.value();
		return sec.getIntOr("Y", Integer.MIN_VALUE);
	}

	/** For the self-test: what the first column of a saved chunk is made of. */
	static String debug(CompoundTag tag, int minY, int height) {
		long[] surface = surface(tag);
		if (surface == null) return "no height map";
		int hbits = 32 - Integer.numberOfLeadingZeros(height);
		int v = (int) (surface[0] & ((1L << hbits) - 1));
		ListTag list = tag.getListOrEmpty("sections");
		StringBuilder sb = new StringBuilder("v0=" + v + " y0=" + (minY + v - 1) + " longs=" + surface.length + " sections");
		for (int k = 0; k < list.size(); k++) {
			if (list.get(k) instanceof CompoundTag sec) {
				sb.append(' ').append(sectionY(sec)).append(sec.keySet());
				Tag bs = sec.get("block_states");
				if (bs instanceof CompoundTag b) {
					sb.append(b.keySet());
					Tag pal = b.get("palette");
					sb.append(pal == null ? "-" : pal.getClass().getSimpleName());
					if (pal instanceof ListTag l && !l.isEmpty()) sb.append('=').append(l.get(0).getClass().getSimpleName()).append(':').append(l.get(0));
				} else {
					sb.append(bs == null ? "no block_states" : bs.getClass().getSimpleName());
				}
			}
			if (k > 6) break;
		}
		return sb.toString();
	}

	/** The height map of the surface as saved (its name's case has changed over the years). */
	static long[] surface(CompoundTag tag) {
		CompoundTag maps = null;
		for (String k : tag.keySet()) if (k.equalsIgnoreCase("heightmaps") && tag.get(k) instanceof CompoundTag c) maps = c;
		if (maps == null) return null;
		long[] motion = null;
		for (String k : maps.keySet()) {
			if (k.equalsIgnoreCase("world_surface")) return longs(maps.get(k));
			if (k.equalsIgnoreCase("motion_blocking")) motion = longs(maps.get(k));
		}
		return motion;
	}

	/** A tag's long array, or null. */
	static long[] longs(Tag t) {
		return t instanceof LongArrayTag la ? la.getAsLongArray() : null;
	}
}
