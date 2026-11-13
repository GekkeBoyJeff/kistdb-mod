package nl.libreria.refresh;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import nl.libreria.core.Services;
import nl.libreria.core.WorldStore;
import org.jetbrains.annotations.Nullable;

/**
 * What stays and what goes, per world. A chunk stays when it is claimed (always), in the middle of the End (the main
 * island and the empty ring around it), where a gateway of the main island leads to (the way back from the outer
 * islands), when you kept it by hand, within the room around a claim, or when people were near it long enough. You can
 * let any chunk go by hand except claims and the middle of the End. A world only takes part when it is turned on.
 */
final class Plan {
	private Plan() {
	}

	/** Why a chunk stays or goes. */
	enum Why {
		NOT_MADE(true, "never made yet"), CLAIM(true, "claimed"), CENTRE(true, "the middle of the End"), GATEWAY(true, "where a gateway leads"),
		KEPT(true, "kept by you"), ROOM(true, "close to a claim"), NEAR(true, "people were near"), OFF(true, "this world takes no part"),
		PLACE(true, "a place someone found"),
		GO(false, "goes"), LET_GO(false, "let go by you");

		final boolean stays;
		final String words;

		Why(boolean stays, String words) {
			this.stays = stays;
			this.words = words;
		}
	}

	/** Saved: per world, whether it takes part and what was picked by hand; and whether a refresh waits for the restart. */
	static final class Data {
		Map<String, Dim> dims = new LinkedHashMap<>();
		boolean planned;
		/** When the last refresh happened, and what it did ("Overworld: 3 907 chunks"), for the operators. */
		long lastDone;
		String lastSummary = "";
	}

	static final class Dim {
		boolean on;
		List<Long> keep = new ArrayList<>();
		List<Long> letGo = new ArrayList<>();
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/refresh.json", Data.class, Data::new);

	/** The End: within this many blocks of its middle it always stays. */
	static final int END_CENTRE = 1000;

	/** What's worked out per world (claims and what's around them, the way back from the outer islands). */
	static final class Ctx {
		final ResourceKey<Level> dim;
		final String id;
		final Map<Long, String> claims;
		final Set<Long> room = new HashSet<>();
		final Set<Long> gateways = new HashSet<>();
		/** Kept by another module (a waystone someone found…), with why. */
		final Map<Long, String> places;
		final Set<Long> keep, letGo;
		final boolean end, on;
		final long nearTicks;

		Ctx(MinecraftServer server, ResourceKey<Level> dim) {
			this.dim = dim;
			this.id = dim.identifier().toString();
			this.claims = Services.land().map(l -> l.claimedChunks(this.id)).orElse(Map.of());
			int r = RefreshMod.ROOM.get();
			for (long c : this.claims.keySet()) {
				int cx = Scan.kx(c), cz = Scan.kz(c);
				for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) this.room.add(Scan.key(cx + dx, cz + dz));
			}
			this.end = dim.equals(Level.END);
			if (this.end) gateways(server, this.gateways);
			this.places = Services.kept(server, this.id);
			Dim d = dim(this.id);
			this.keep = new HashSet<>(d.keep);
			this.letGo = new HashSet<>(d.letGo);
			this.on = d.on;
			this.nearTicks = RefreshMod.NEAR.get() * 60L * 20L;
		}

		Why why(Scan.World w, int cx, int cz) {
			if (w.state(cx, cz) == Scan.NONE) return Why.NOT_MADE;
			long k = Scan.key(cx, cz);
			if (this.claims.containsKey(k)) return Why.CLAIM;
			if (this.end && (long) cx * cx + (long) cz * cz <= (long) (END_CENTRE / 16) * (END_CENTRE / 16)) return Why.CENTRE;
			if (this.gateways.contains(k)) return Why.GATEWAY;
			if (this.letGo.contains(k)) return this.on ? Why.LET_GO : Why.OFF;
			if (this.keep.contains(k)) return Why.KEPT;
			if (this.places.containsKey(k)) return Why.PLACE;
			if (this.room.contains(k)) return Why.ROOM;
			if (w.near(cx, cz) >= this.nearTicks && this.nearTicks > 0) return Why.NEAR;
			return this.on ? Why.GO : Why.OFF;
		}

		/** May this chunk be picked by hand? (Claims and the middle of the End always stay.) */
		boolean pickable(Why why) {
			return why != Why.CLAIM && why != Why.CENTRE && why != Why.NOT_MADE && why != Why.GATEWAY;
		}

		/** Who claimed it, or null. */
		@Nullable String owner(int cx, int cz) {
			return this.claims.get(Scan.key(cx, cz));
		}
	}

	static Dim dim(String id) {
		return STORE.get().dims.computeIfAbsent(id, k -> new Dim());
	}

	/** Where the gateways around the End's main island lead (and the chunks right around): the way back home. */
	private static void gateways(MinecraftServer server, Set<Long> out) {
		ServerLevel end = server.getLevel(Level.END);
		if (end == null) return;
		for (int cx = -7; cx <= 7; cx++) {
			for (int cz = -7; cz <= 7; cz++) {
				var chunk = end.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					if (Scan.world(Level.END) == null || Scan.world(Level.END).state(cx, cz) == Scan.NONE) continue;
					chunk = end.getChunk(cx, cz);
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (!(be instanceof TheEndGatewayBlockEntity)) continue;
					var tag = be.saveWithoutMetadata(end.registryAccess());
					if (tag.get("exit_portal") == null) continue;
					BlockPos exit = BlockPos.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("exit_portal")).result().orElse(null);
					if (exit == null) continue;
					for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) out.add(Scan.key((exit.getX() >> 4) + dx, (exit.getZ() >> 4) + dz));
				}
			}
		}
	}

	/** How many chunks of a world stay and go, and about how many bytes going frees (blocks; things and places a bit more). */
	record Count(int stay, int go, long bytes) {
	}

	static Count count(Ctx ctx, Scan.World w) {
		int stay = 0, go = 0;
		long bytes = 0;
		for (Scan.Reg r : w.regions.values()) {
			for (int i = 0; i < 1024; i++) {
				if (r.state[i] == Scan.NONE) continue;
				Why why = ctx.why(w, r.rx * 32 + (i & 31), r.rz * 32 + (i >> 5));
				if (why.stays) {
					stay++;
				} else {
					go++;
					// (a chunk only seen loaded so far: about three sectors)
					bytes += (r.sectors[i] > 0 ? r.sectors[i] : 3) * (long) Region.SECTOR;
				}
			}
		}
		return new Count(stay, go, bytes);
	}

	/** The chunks that go, in a world that takes part. */
	static List<Long> going(Ctx ctx, Scan.World w) {
		List<Long> out = new ArrayList<>();
		if (!ctx.on) return out;
		for (Scan.Reg r : w.regions.values()) {
			for (int i = 0; i < 1024; i++) {
				if (r.state[i] == Scan.NONE) continue;
				int cx = r.rx * 32 + (i & 31), cz = r.rz * 32 + (i >> 5);
				if (!ctx.why(w, cx, cz).stays) out.add(Scan.key(cx, cz));
			}
		}
		return out;
	}

	/** Kept or let go by hand (or back to what the rules say, when {@code to} is null). */
	static void pick(String id, long key, @Nullable Boolean stays) {
		Dim d = dim(id);
		d.keep.remove((Long) key);
		d.letGo.remove((Long) key);
		if (stays != null) (stays ? d.keep : d.letGo).add(key);
		STORE.markDirty();
	}
}
