package nl.libreria.claims;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Compass;

/**
 * A land map to hold: a real map item (nothing to install) showing the land around you, 128 blocks across, with every
 * claim drawn on it (yours green, where you may build cyan, someone else's red) and your waypoints, death point and
 * friends as markers. It follows you as you walk. The server draws it for whoever holds it; it has no saved map
 * behind it, so it costs nothing on disk.
 */
public final class LandMapItem {
	/** One id for every land map, far above real maps: every client gets its own picture under it. */
	static final MapId ID = new MapId(1_900_000_000);
	private static final int SIZE = 128;

	/** What each holder's client has right now. */
	private static final class Drawn {
		int cx = Integer.MIN_VALUE, cz;
		String dim = "";
		int claimsHash;
		int age;
	}

	private static final Map<UUID, Drawn> DRAWN = new HashMap<>();

	private LandMapItem() {
	}

	public static ItemStack create() {
		ItemStack st = new ItemStack(Items.FILLED_MAP);
		st.set(DataComponents.MAP_ID, ID);
		st.set(DataComponents.CUSTOM_NAME, Ui.text("Land map", ChatFormatting.GREEN));
		st.set(DataComponents.LORE, new ItemLore(List.of(
				Ui.text("Shows the claims around you.", ChatFormatting.GRAY),
				Ui.text("Green: yours · cyan: you may build", ChatFormatting.DARK_GRAY),
				Ui.text("Red: someone else's", ChatFormatting.DARK_GRAY))));
		return st;
	}

	static boolean is(ItemStack st) {
		return st.is(Items.FILLED_MAP) && ID.equals(st.get(DataComponents.MAP_ID));
	}

	/** Gives the player a land map, unless they already carry one. */
	static void give(ServerPlayer p) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (is(p.getInventory().getItem(i))) {
				nl.libreria.core.gui.Toast.show(p, Items.FILLED_MAP, nl.libreria.core.gui.Toast.INFO, "You have a land map",
						"Hold it to see the claims around you.");
				return;
			}
		}
		ItemStack map = create();
		if (!p.getInventory().add(map)) p.drop(map, false, true);
		nl.libreria.core.gui.Toast.show(p, Items.FILLED_MAP, nl.libreria.core.gui.Toast.GOOD, "Land map",
				"Hold it: claims around you, as you walk.");
		DRAWN.remove(p.getUUID());
	}

	/** Every few ticks: redraw for players holding one (the picture when they moved or claims changed, markers always). */
	static void tick(net.minecraft.server.MinecraftServer server, int tick) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			boolean holding = is(p.getMainHandItem()) || is(p.getOffhandItem());
			if (!holding) {
				// Still in a slot somewhere: the picture stays as it was; draw fresh when held again
				continue;
			}
			Drawn d = DRAWN.computeIfAbsent(p.getUUID(), k -> new Drawn());
			int cx = Mth.floor(p.getX()), cz = Mth.floor(p.getZ());
			String dim = p.level().dimension().identifier().toString();
			int hash = ClaimsMod.STORE.get().claims.hashCode();
			boolean redraw = !dim.equals(d.dim) || Math.abs(cx - d.cx) >= 4 || Math.abs(cz - d.cz) >= 4 || hash != d.claimsHash
					|| tick - d.age > 200;
			if (redraw) {
				d.cx = cx;
				d.cz = cz;
				d.dim = dim;
				d.claimsHash = hash;
				d.age = tick;
			}
			send(p, d, redraw);
		}
	}

	static void forget(UUID id) {
		DRAWN.remove(id);
	}

	private static void send(ServerPlayer p, Drawn d, boolean picture) {
		Optional<MapItemSavedData.MapPatch> patch = picture ? Optional.of(new MapItemSavedData.MapPatch(0, 0, SIZE, SIZE,
				draw(p.level(), p.getUUID(), d.cx - SIZE / 2, d.cz - SIZE / 2))) : Optional.empty();
		p.connection.send(new ClientboundMapItemDataPacket(ID, (byte) 0, true, Optional.of(decorations(p, d)), patch));
	}

	// ---------------------------------------------------------------- the picture

	/** The ground as a normal map shows it, with the claims drawn over it. */
	static byte[] draw(ServerLevel level, UUID viewer, int x0, int z0) {
		byte[] colors = new byte[SIZE * SIZE];
		int[] above = new int[SIZE];
		for (int x = 0; x < SIZE; x++) above[x] = surface(level, x0 + x, z0 - 1);
		for (int z = 0; z < SIZE; z++) {
			for (int x = 0; x < SIZE; x++) {
				int wx = x0 + x, wz = z0 + z;
				LevelChunk chunk = level.getChunkSource().getChunkNow(wx >> 4, wz >> 4);
				if (chunk == null) {
					above[x] = Integer.MIN_VALUE;
					continue;
				}
				int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, wx & 15, wz & 15);
				BlockPos pos = new BlockPos(wx, y, wz);
				BlockState st = chunk.getBlockState(pos);
				while (st.getMapColor(level, pos) == MapColor.NONE && pos.getY() > level.getMinY()) {
					pos = pos.below();
					st = chunk.getBlockState(pos);
				}
				MapColor mc = st.getMapColor(level, pos);
				// Shading like a real map: lighter where the ground rises (from north to south), darker where it falls
				MapColor.Brightness b = above[x] == Integer.MIN_VALUE || pos.getY() == above[x] ? MapColor.Brightness.NORMAL
						: pos.getY() > above[x] ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
				if (mc == MapColor.WATER) b = MapColor.Brightness.NORMAL;
				above[x] = pos.getY();
				colors[x + z * SIZE] = mc.getPackedId(b);
			}
		}
		claims(level, viewer, x0, z0, colors);
		return colors;
	}

	private static int surface(ServerLevel level, int wx, int wz) {
		LevelChunk chunk = level.getChunkSource().getChunkNow(wx >> 4, wz >> 4);
		return chunk == null ? Integer.MIN_VALUE : chunk.getHeight(Heightmap.Types.WORLD_SURFACE, wx & 15, wz & 15);
	}

	/** Who a chunk is to the viewer: 0 free, 1 theirs, 2 trusted there, 3 someone else's. */
	private static int kind(ServerLevel level, UUID viewer, int chunkX, int chunkZ) {
		ClaimsMod.Claim c = ClaimsMod.claimAt(level, new ChunkPos(chunkX, chunkZ));
		if (c == null) return 0;
		if (c.owner.equals(viewer)) return 1;
		return ClaimsMod.owner(c.owner).trusted.containsKey(viewer) ? 2 : 3;
	}

	private static final MapColor[] LINE = {null, MapColor.EMERALD, MapColor.DIAMOND, MapColor.COLOR_RED};
	private static final MapColor[] FILL = {null, MapColor.COLOR_LIGHT_GREEN, MapColor.COLOR_CYAN, MapColor.TERRACOTTA_RED};

	/** A bright line along the edge of each claim, and a light hatching inside. */
	private static void claims(ServerLevel level, UUID viewer, int x0, int z0, byte[] colors) {
		Map<Long, Integer> kinds = new HashMap<>();
		for (int z = 0; z < SIZE; z++) {
			for (int x = 0; x < SIZE; x++) {
				int wx = x0 + x, wz = z0 + z;
				int k = kinds.computeIfAbsent(pack(wx >> 4, wz >> 4), key -> kind(level, viewer, wx >> 4, wz >> 4));
				if (k == 0) continue;
				int lx = wx & 15, lz = wz & 15;
				boolean edge = (lx == 0 && kindAt(kinds, level, viewer, (wx >> 4) - 1, wz >> 4) != k)
						|| (lx == 15 && kindAt(kinds, level, viewer, (wx >> 4) + 1, wz >> 4) != k)
						|| (lz == 0 && kindAt(kinds, level, viewer, wx >> 4, (wz >> 4) - 1) != k)
						|| (lz == 15 && kindAt(kinds, level, viewer, wx >> 4, (wz >> 4) + 1) != k);
				if (edge) {
					colors[x + z * SIZE] = LINE[k].getPackedId(MapColor.Brightness.HIGH);
				} else if (Math.floorMod(wx + wz, 4) == 0) {
					colors[x + z * SIZE] = FILL[k].getPackedId(MapColor.Brightness.NORMAL);
				}
			}
		}
	}

	private static long pack(int cx, int cz) {
		return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
	}

	private static int kindAt(Map<Long, Integer> kinds, ServerLevel level, UUID viewer, int cx, int cz) {
		return kinds.computeIfAbsent(pack(cx, cz), key -> kind(level, viewer, cx, cz));
	}

	// ---------------------------------------------------------------- markers

	private static List<MapDecoration> decorations(ServerPlayer p, Drawn d) {
		List<MapDecoration> out = new ArrayList<>();
		// You, in the middle, pointing where you look
		out.add(deco(MapDecorationTypes.PLAYER, p.getX(), p.getZ(), d, p.getYRot(), null));
		List<Compass.Marker> markers = new ArrayList<>(Compass.markersFor(p));
		markers.addAll(ClaimsMod.friends(p));
		for (Compass.Marker m : markers) {
			Holder<MapDecorationType> type = switch (m.glyph()) {
				case "skull" -> MapDecorationTypes.RED_X;
				case "person" -> MapDecorationTypes.BLUE_MARKER;
				case "house" -> null; // your land is already on the map
				default -> MapDecorationTypes.TARGET_POINT;
			};
			if (type == null) continue;
			if (Math.abs(m.pos().x - d.cx) > SIZE / 2.0 || Math.abs(m.pos().z - d.cz) > SIZE / 2.0) continue;
			out.add(deco(type, m.pos().x, m.pos().z, d, 180, Component.literal(m.name())));
		}
		return out;
	}

	private static MapDecoration deco(Holder<MapDecorationType> type, double wx, double wz, Drawn d, float yaw, Component name) {
		byte x = (byte) Mth.clamp(Math.round((wx - d.cx) * 2), -127, 127);
		byte z = (byte) Mth.clamp(Math.round((wz - d.cz) * 2), -127, 127);
		byte rot = (byte) (Math.floorMod(Math.round(yaw * 16 / 360f), 16));
		return new MapDecoration(type, x, z, rot, Optional.ofNullable(name));
	}
}
