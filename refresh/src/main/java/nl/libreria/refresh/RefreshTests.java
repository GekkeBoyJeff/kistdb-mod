package nl.libreria.refresh;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import nl.libreria.core.SelfTests;

/** Self-tests of the world refresh: region files, reading a saved chunk's colours, what stays and goes (CI only). */
final class RefreshTests {
	private RefreshTests() {
	}

	static void register() {
		// A region file: three chunks; one taken out (the rest moves up, the file shrinks); then the others (it goes)
		SelfTests.register("refresh.region", level -> {
			Path dir = Files.createTempDirectory("libreria-refresh");
			Path file = Region.file(dir, 0, 0);
			RegionStorageInfo info = new RegionStorageInfo("test", Level.OVERWORLD, "region");
			try (RegionFile rf = new RegionFile(info, file, dir, false)) {
				for (int i = 0; i < 3; i++) {
					CompoundTag t = new CompoundTag();
					t.putInt("n", i);
					t.putLongArray("fill", new long[600 + i * 900]);
					try (DataOutputStream out = rf.getChunkDataOutputStream(new ChunkPos(i, 1))) {
						NbtIo.write(t, out);
					}
				}
			}
			int[] head = Region.header(file);
			int there = 0;
			for (int i = 0; i < 1024; i++) if (head[i] != 0) there++;
			SelfTests.check(there == 3, "three chunks in it: " + there);
			long before = Files.size(file);
			int removed = Region.remove(file, i -> i == 1 + 32);
			SelfTests.check(removed == 1 && Files.size(file) < before, "one out, smaller: " + before + " → " + Files.size(file));
			try (RegionFile rf = new RegionFile(info, file, dir, false)) {
				SelfTests.check(!rf.hasChunk(new ChunkPos(1, 1)) && rf.hasChunk(new ChunkPos(0, 1)) && rf.hasChunk(new ChunkPos(2, 1)), "the right one gone");
				for (int i : new int[] {0, 2}) {
					try (DataInputStream in = rf.getChunkDataInputStream(new ChunkPos(i, 1))) {
						CompoundTag t = NbtIo.read(in);
						SelfTests.check(t.getIntOr("n", -1) == i, "chunk " + i + " still reads: " + t.getIntOr("n", -1));
					}
				}
			}
			Region.remove(file, i -> true);
			SelfTests.check(!Files.exists(file), "empty: the file goes");
		});
		// The restart step, on a world of its own: a plan takes two of three chunks out (blocks, things, places), the
		// world gets a new layout, the chunk left is remembered as kept land
		SelfTests.register("refresh.restart", level -> {
			Path root = Files.createTempDirectory("libreria-refresh-world");
			String id = "libreria:refresh_test";
			var dim = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.Identifier.parse(id));
			Path folder = net.minecraft.world.level.dimension.DimensionType.getStorageFolder(dim, root);
			for (String sub : new String[] {"region", "entities", "poi"}) {
				Path d = folder.resolve(sub);
				Files.createDirectories(d);
				try (RegionFile rf = new RegionFile(new RegionStorageInfo("test", dim, sub), Region.file(d, -1, 0), d, false)) {
					for (int i = 0; i < 3; i++) {
						CompoundTag t = new CompoundTag();
						t.putInt("n", i);
						try (DataOutputStream out = rf.getChunkDataOutputStream(new ChunkPos(-32 + i, 5))) {
							NbtIo.write(t, out);
						}
					}
				}
			}
			Path dir = Layout.dir(root);
			Restart.writePlan(dir.resolve("plan-test.bin"), id, true, java.util.List.of(Scan.key(-32, 5), Scan.key(-31, 5)));
			int before = Layout.layout(id);
			String done = Restart.applyOne(root, dir, dir.resolve("plan-test.bin"));
			int left = 0;
			for (String sub : new String[] {"region", "entities", "poi"}) {
				int[] head = Region.header(Region.file(folder.resolve(sub), -1, 0));
				for (int i = 0; i < 1024; i++) if (head[i] != 0) left++;
			}
			SelfTests.check(done != null && left == 3, "two of three chunks out of blocks, things and places: " + left + " left, " + done);
			SelfTests.check(Layout.layout(id) == before + 1, "a new layout for its structures");
			SelfTests.check(Files.isRegularFile(dir.resolve("kept-" + Layout.safe(id) + ".bin")), "the land that stayed is remembered");
		});
		// A chunk's colours from how it is saved are the colours of the chunk itself
		SelfTests.register("refresh.colors", level -> {
			LevelChunk chunk = level.getChunk(3, 3);
			byte[] live = new byte[256], saved = new byte[256];
			Colors.fromChunk(chunk, live, 0);
			CompoundTag tag = SerializableChunkData.copyOf(level, chunk).write();
			boolean read = Colors.fromTag(tag, level.getMinY(), level.getHeight(), saved, 0);
			// Saved, a block has the colour of its kind (a log's bark or top, a bed's foot or head aren't saved apart): the
			// columns with such a block on top (a village of the flat world's own may stand here) don't count
			int same = 0, known = 0, fair = 0;
			for (int i = 0; i < 256; i++) {
				if (saved[i] != Colors.UNKNOWN) known++;
				int x = i & 15, z = i >> 4;
				net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos((chunk.getPos().x() << 4) + x,
						chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z), (chunk.getPos().z() << 4) + z);
				net.minecraft.world.level.block.state.BlockState top = chunk.getBlockState(pos);
				for (int steps = 0; steps < 48 && pos.getY() >= level.getMinY() && top.getMapColor(chunk, pos) == net.minecraft.world.level.material.MapColor.NONE; steps++) {
					pos.move(0, -1, 0);
					top = chunk.getBlockState(pos);
				}
				if (top.getMapColor(chunk, pos) != top.getBlock().defaultMapColor()) continue;
				fair++;
				if (live[i] == saved[i]) same++;
			}
			SelfTests.check(read && known > 200 && fair >= 64 && same >= fair * 230 / 256, "saved colours read: " + known + " known, " + same + " of " + fair
					+ " the same; keys " + tag.keySet()
					+ (tag.get("Heightmaps") instanceof CompoundTag hm ? " heightmaps " + hm.keySet() : "") + " sections " + tag.getListOrEmpty("sections").size()
					+ " status " + tag.getStringOr("Status", "?") + " · " + Colors.debug(tag, level.getMinY(), level.getHeight()));
		});
		// As the server stops, the list is worked out again: it only gets shorter
		SelfTests.register("refresh.stop", level -> {
			java.util.List<Long> now = java.util.List.of(1L, 2L, 3L, 4L);
			java.util.List<Long> left = Restart.onlyPlanned(now, java.util.Set.of(1L, 2L, 9L), java.util.Set.of(4L));
			SelfTests.check(left.equals(java.util.List.of(1L, 2L, 4L)), "land walked on since planning stays (3), let go by hand comes on (4): " + left);
			try {
				Path f = Files.createTempFile("libreria-plan", ".bin");
				Restart.writePlan(f, "minecraft:overworld", true, java.util.List.of(5L, 6L));
				SelfTests.check(java.util.Set.of(5L, 6L).equals(Restart.readPlan(f)) && Restart.readPlan(f.resolveSibling("none.bin")) == null,
						"a written list read back");
				Files.deleteIfExists(f);
			} catch (java.io.IOException e) {
				throw new RuntimeException(e);
			}
		});
		// What stays and what goes
		SelfTests.register("refresh.plan", level -> {
			Scan.World w = new Scan.World(Level.OVERWORLD, Path.of("."), Path.of("."), level.getMinY(), level.getHeight());
			Scan.Reg r = w.reg(1000, 1000, true);
			int base = 32000;
			for (int i = 0; i < 4; i++) r.state[i] = Scan.MADE;
			r.near[0] = 20 * 60 * 15;
			r.near[1] = 20 * 60 * 2;
			Plan.Dim d = Plan.dim(Level.OVERWORLD.identifier().toString());
			boolean wasOn = d.on;
			int near = RefreshMod.NEAR.get();
			try {
				RefreshMod.NEAR.set(10);
				d.on = true;
				Plan.Ctx ctx = new Plan.Ctx(level.getServer(), Level.OVERWORLD);
				SelfTests.check(ctx.why(w, base, base) == Plan.Why.NEAR, "15 minutes near: stays");
				SelfTests.check(ctx.why(w, base + 1, base) == Plan.Why.GO, "2 minutes near: goes");
				SelfTests.check(ctx.why(w, base + 5, base) == Plan.Why.NOT_MADE, "never made");
				Plan.pick(ctx.id, Scan.key(base + 1, base), true);
				Plan.pick(ctx.id, Scan.key(base, base), false);
				ctx = new Plan.Ctx(level.getServer(), Level.OVERWORLD);
				SelfTests.check(ctx.why(w, base + 1, base) == Plan.Why.KEPT && ctx.why(w, base, base) == Plan.Why.LET_GO, "picked by hand");
				Plan.Count n = Plan.count(ctx, w);
				SelfTests.check(n.stay() == 1 && n.go() == 3, "counted: " + n);
				SelfTests.check(Plan.going(ctx, w).size() == 3, "three go");
				d.on = false;
				ctx = new Plan.Ctx(level.getServer(), Level.OVERWORLD);
				SelfTests.check(ctx.why(w, base + 2, base) == Plan.Why.OFF && Plan.going(ctx, w).isEmpty(), "a world that takes no part: nothing goes");
				Plan.Ctx end = new Plan.Ctx(level.getServer(), Level.END);
				Scan.World ew = new Scan.World(Level.END, Path.of("."), Path.of("."), 0, 256);
				ew.reg(0, 0, true).state[3 + 3 * 32] = Scan.MADE;
				SelfTests.check(end.why(ew, 3, 3) == Plan.Why.CENTRE && !end.pickable(Plan.Why.CENTRE), "the End's middle always stays");
			} finally {
				Plan.pick(Level.OVERWORLD.identifier().toString(), Scan.key(base + 1, base), null);
				Plan.pick(Level.OVERWORLD.identifier().toString(), Scan.key(base, base), null);
				d.on = wasOn;
				RefreshMod.NEAR.set(near);
			}
		});
		// The worlds are being read; new structures keep out of land kept from before
		SelfTests.register("refresh.scan", level -> {
			Scan.start(level.getServer());
			level.getChunk(5, 5);
			Scan.World w = Scan.world(Level.OVERWORLD);
			Scan.refreshLoaded(level);
			SelfTests.check(w != null && !w.chunks().isEmpty(), "the overworld is read: " + (w == null ? 0 : w.chunks().size()) + " chunks");
			Layout.testKept(Level.OVERWORLD.identifier().toString(), java.util.Set.of(Scan.key(40, 40)));
			try {
				SelfTests.check(Layout.overlapsKept(Level.OVERWORLD, new net.minecraft.world.level.levelgen.structure.BoundingBox(630, 0, 630, 660, 80, 660)),
						"a structure over kept land is left out");
				SelfTests.check(!Layout.overlapsKept(Level.OVERWORLD, new net.minecraft.world.level.levelgen.structure.BoundingBox(700, 0, 700, 720, 80, 720)),
						"one beside it is made");
			} finally {
				Layout.testKept(Level.OVERWORLD.identifier().toString(), null);
			}
		});
	}
}
