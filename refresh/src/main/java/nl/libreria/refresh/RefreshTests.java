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
		// A chunk's colours from how it is saved are the colours of the chunk itself
		SelfTests.register("refresh.colors", level -> {
			LevelChunk chunk = level.getChunk(3, 3);
			byte[] live = new byte[256], saved = new byte[256];
			Colors.fromChunk(chunk, live, 0);
			CompoundTag tag = SerializableChunkData.copyOf(level, chunk).write();
			boolean read = Colors.fromTag(tag, level.getMinY(), level.getHeight(), saved, 0);
			int same = 0, known = 0;
			for (int i = 0; i < 256; i++) {
				if (live[i] == saved[i]) same++;
				if (saved[i] != Colors.UNKNOWN) known++;
			}
			SelfTests.check(read && known > 200 && same >= 230, "saved colours read: " + known + " known, " + same + " the same");
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
			Scan.World w = Scan.world(Level.OVERWORLD);
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
