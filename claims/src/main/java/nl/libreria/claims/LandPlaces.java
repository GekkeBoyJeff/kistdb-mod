package nl.libreria.claims;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.PlaceList;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Menu;

/**
 * Your land in "Your places" ({@link PlaceList}): one entry per piece of land (chunks that touch), by its name. Click =
 * show the way to its middle, right-click = give it a name. The land map is one click away in the top-left corner.
 */
final class LandPlaces {
	static final String KIND = "land";

	private LandPlaces() {
	}

	static void register() {
		PlaceList.kind(KIND, "Your land", Items.GRASS_BLOCK, 60)
				.about("Each piece of your land, by name.")
				.empty("Claim a chunk (/claim, or the land", "map) and your land shows up here.")
				.help("Right-click a piece: give it a name.", "Others see the name when they walk in.")
				.action(LandPlaces::mapButton);
		PlaceList.addSource((p, out) -> {
			for (Piece piece : pieces(p)) {
				ServerLevel level = p.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(piece.dim)));
				ChunkPos c = piece.middle;
				int x = c.x() * 16 + 8, z = c.z() * 16 + 8;
				int y = level != null && level.getChunkSource().getChunkNow(c.x(), c.z()) != null
						? level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : (int) p.getY();
				String name = piece.name.isEmpty() ? "Unnamed land" : piece.name;
				PlaceList.Place place = PlaceList.place(KIND, name, new ItemStack(piece.name.isEmpty() ? Items.GRASS_BLOCK : Items.NAME_TAG),
								piece.dim, new Vec3(x + 0.5, y, z + 0.5))
						.lore(Ui.text(piece.chunks + (piece.chunks == 1 ? " chunk" : " chunks"), ChatFormatting.GRAY));
				if (level != null) place.details(piece.name.isEmpty() ? "give it a name" : "rename it", pl -> ClaimsMod.nameWindow(pl, level, c));
				out.add(place);
			}
		});
	}

	/** One piece of land: its dimension, name, size and the chunk nearest its middle. */
	private record Piece(String dim, String name, int chunks, ChunkPos middle) {
	}

	/** Your claims, grouped into pieces of chunks that touch. */
	private static List<Piece> pieces(ServerPlayer p) {
		Map<String, Map<Long, ClaimsMod.Claim>> byDim = new HashMap<>();
		for (Map.Entry<String, ClaimsMod.Claim> e : ClaimsMod.STORE.get().claims.entrySet()) {
			if (!e.getValue().owner.equals(p.getUUID())) continue;
			String[] k = e.getKey().split("\\|");
			if (k.length != 3) continue;
			try {
				byDim.computeIfAbsent(k[0], d -> new HashMap<>()).put(pack(Integer.parseInt(k[1]), Integer.parseInt(k[2])), e.getValue());
			} catch (NumberFormatException ignored) {
				// a damaged key: skip it
			}
		}
		List<Piece> out = new ArrayList<>();
		for (Map.Entry<String, Map<Long, ClaimsMod.Claim>> d : byDim.entrySet()) {
			Map<Long, ClaimsMod.Claim> chunks = d.getValue();
			Set<Long> seen = new HashSet<>();
			for (Long start : chunks.keySet()) {
				if (!seen.add(start)) continue;
				List<Long> piece = new ArrayList<>();
				ArrayDeque<Long> todo = new ArrayDeque<>(List.of(start));
				while (!todo.isEmpty()) {
					long c = todo.poll();
					piece.add(c);
					int cx = unpackX(c), cz = unpackZ(c);
					for (long n : new long[] {pack(cx + 1, cz), pack(cx - 1, cz), pack(cx, cz + 1), pack(cx, cz - 1)}) {
						if (chunks.containsKey(n) && seen.add(n)) todo.add(n);
					}
				}
				String name = "";
				double sx = 0, sz = 0;
				for (long c : piece) {
					sx += unpackX(c);
					sz += unpackZ(c);
					String a = chunks.get(c).area;
					if (name.isEmpty() && a != null && !a.isEmpty()) name = a;
				}
				double mx = sx / piece.size(), mz = sz / piece.size();
				long middle = piece.getFirst();
				double best = Double.MAX_VALUE;
				for (long c : piece) {
					double dd = Math.pow(unpackX(c) - mx, 2) + Math.pow(unpackZ(c) - mz, 2);
					if (dd < best) {
						best = dd;
						middle = c;
					}
				}
				out.add(new Piece(d.getKey(), name, piece.size(), new ChunkPos(unpackX(middle), unpackZ(middle))));
			}
		}
		return out;
	}

	private static long pack(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	private static int unpackX(long c) {
		return (int) (c >> 32);
	}

	private static int unpackZ(long c) {
		return (int) c;
	}

	private static GuiElementBuilder mapButton(ServerPlayer p) {
		return Menu.actions(Menu.button(Items.FILLED_MAP, "Land map", ChatFormatting.GREEN,
				"Your land and others' from above:", "claim chunks, name your land.", "", "Key: {sneak} + {swap}"), "Click: open")
				.setCallback((i, t, a, g) -> {
					Ui.click(p);
					p.closeContainer();
					ClaimView.open(p);
				});
	}
}
