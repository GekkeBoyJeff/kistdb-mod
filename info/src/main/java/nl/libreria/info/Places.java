package nl.libreria.info;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Guide;
import nl.libreria.core.PlayerStore;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Window;
import nl.libreria.core.gui.Caption;
import org.jetbrains.annotations.Nullable;

/**
 * Place names, quietly: walk into a village, a temple or another stretch of land and its name fades in, small, at the
 * top of the screen, and fades out again. Every village and temple and every stretch of forest, desert or sea has its
 * own name, never used twice ({@link Names}). Every place you find goes into your journal.
 */
public final class Places {
	/** One place in your journal. */
	public static final class Place {
		public String key = "";
		public String name = "";
		public String kind = "";
		public String dim = "minecraft:overworld";
		public int x, z;
		public long found;
	}

	public static final class Data {
		public List<Place> places = new ArrayList<>();
		public Set<String> biomes = new LinkedHashSet<>();
	}

	static final PlayerStore<Data> STORE = PlayerStore.create("libreria/journal", Data.class, Data::new, d -> {
		if (d.places == null) d.places = new ArrayList<>();
		if (d.biomes == null) d.biomes = new LinkedHashSet<>();
	});

	/** Biomes that are only a strip between others: no title (it would come and go all the time along a coast). */
	private static final Set<String> MINOR = Set.of("river", "frozen_river", "beach", "snowy_beach", "stony_shore", "the_void");
	/** Before a biome's name shows, you have to stay in it this long (ticks): no names while you skirt its edge. */
	private static final int BIOME_SETTLE = 50;
	/** The same name doesn't come back within this time (ticks). */
	private static final int AGAIN_AFTER = 20 * 60 * 10;

	/** Where a player was, to notice when that changes. */
	private static final class State {
		@Nullable String dim;
		@Nullable String place;
		@Nullable String biome;
		int biomeSince;
		boolean biomeShown;
		final Map<String, Integer> shownAt = new HashMap<>();
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private Places() {
	}

	// ---------------------------------------------------------------- noticing places

	/** Twice a second per player. */
	static void tick(MinecraftServer server, int now) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator()) continue;
			try {
				tick(p, now);
			} catch (RuntimeException e) {
				nl.libreria.core.Libreria.LOG.warn("Place names failed for {}", p.getName().getString(), e);
			}
		}
	}

	private static void tick(ServerPlayer p, int now) {
		State st = STATES.computeIfAbsent(p.getUUID(), k -> new State());
		ServerLevel level = p.level();
		String dim = level.dimension().identifier().toString();
		int mode = InfoMod.PLACES.get(p);
		@Nullable Found found = structureAt(level, p.blockPosition());
		String biome = biomeAt(level, p.blockPosition());

		// Just logged in: remember where you are, without a title
		if (st.dim == null) {
			st.dim = dim;
			st.place = found == null ? null : found.key();
			st.biome = biome;
			st.biomeShown = true;
			return;
		}
		// Another world
		if (!dim.equals(st.dim)) {
			st.dim = dim;
			st.place = found == null ? null : found.key();
			st.biome = biome;
			st.biomeSince = now;
			st.biomeShown = true;
			if (mode != 2) show(p, st, "dim:" + dim, Component.literal(dimension(dim)), null, now, true);
			return;
		}
		// A place: a village, a temple…
		String key = found == null ? null : found.key();
		if (!Objects.equals(key, st.place)) {
			st.place = key;
			if (found != null) {
				boolean isNew = remember(p, found.key(), found.name(), found.kind().label(), found.at());
				if (mode == 0 || (mode == 1 && isNew)) {
					show(p, st, found.key(), Component.literal(found.name()),
							Component.literal(found.kind().named() ? found.kind().label() + (isNew ? " · discovered" : "")
									: isNew ? "discovered" : ""), now, isNew);
				}
				return;
			}
		}
		if (st.place != null) return;
		// A biome, once you've been in it a moment
		if (biome == null || MINOR.contains(Identifier.parse(biome).getPath())) return;
		if (!biome.equals(st.biome)) {
			st.biome = biome;
			st.biomeSince = now;
			st.biomeShown = false;
		} else if (!st.biomeShown && now - st.biomeSince >= BIOME_SETTLE) {
			st.biomeShown = true;
			Data d = STORE.get(p);
			if (d.biomes.add(biome)) STORE.markDirty(p);
			// The stretch of this biome you're in has its own name (Ashglen Forest, the Whispering Woods…)
			Names.Area area = Names.area(dim, biome, p.getBlockX(), p.getBlockZ(), level.getSeed());
			String areaKey = "area|" + dim + "|" + biome + "|" + area.x + "," + area.z;
			boolean isNew = remember(p, areaKey, area.name, biomeName(biome), new BlockPos(area.x, p.getBlockY(), area.z));
			if (mode == 0 || (mode == 1 && isNew)) {
				Identifier id = Identifier.parse(biome);
				MutableComponent kind = Component.translatable("biome." + id.getNamespace() + "." + id.getPath());
				if (isNew) kind.append(" · discovered");
				show(p, st, areaKey, Component.literal(area.name), kind, now, isNew);
			}
		}
	}

	private static void show(ServerPlayer p, State st, String key, Component name, @Nullable Component kind, int now, boolean force) {
		Integer last = st.shownAt.get(key);
		if (!force && last != null && now - last < AGAIN_AFTER) return;
		st.shownAt.put(key, now);
		Caption.show(p, name, kind == null || kind.getString().isEmpty() ? null : kind);
	}

	static void forget(UUID id) {
		STATES.remove(id);
	}

	/** A structure you stand in. */
	record Found(String key, String name, PlaceNames.Kind kind, BlockPos at) {
	}

	static @Nullable Found structureAt(ServerLevel level, BlockPos pos) {
		var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		var manager = level.structureManager();
		Found best = null;
		for (Structure s : manager.getAllStructuresAt(pos).keySet()) {
			Identifier id = registry.getKey(s);
			if (id == null) continue;
			PlaceNames.Kind kind = PlaceNames.kind(id.getPath());
			if (kind == null) continue;
			StructureStart start = manager.getStructureAt(pos, s);
			if (start == null || !start.isValid()) continue;
			var c = start.getChunkPos();
			long seed = level.getSeed() ^ ((long) c.x() << 32) ^ (c.z() & 0xFFFFFFFFL) ^ id.hashCode();
			String key = level.dimension().identifier() + "|" + id + "|" + c.x() + "," + c.z();
			var box = start.getBoundingBox();
			BlockPos at = new BlockPos((box.minX() + box.maxX()) / 2, box.minY(), (box.minZ() + box.maxZ()) / 2);
			Found f = new Found(key, Names.place(key, kind, seed), kind, at);
			// Named places before plain ones (a ruined portal inside a village is still the village)
			if (best == null || (!best.kind().named() && kind.named())) best = f;
		}
		return best;
	}

	private static @Nullable String biomeAt(ServerLevel level, BlockPos pos) {
		Holder<Biome> b = level.getBiome(pos);
		return b.unwrapKey().map(k -> k.identifier().toString()).orElse(null);
	}

	/** "dark_forest" → "Dark Forest" (for the journal, which stores plain text). */
	static String biomeName(String biome) {
		String path = biome.substring(biome.indexOf(':') + 1);
		StringBuilder b = new StringBuilder();
		for (String w : path.split("_")) {
			if (w.isEmpty()) continue;
			if (!b.isEmpty()) b.append(' ');
			b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return b.toString();
	}

	static String dimension(String dim) {
		return switch (dim) {
			case "minecraft:overworld" -> "The Overworld";
			case "minecraft:the_nether" -> "The Nether";
			case "minecraft:the_end" -> "The End";
			default -> dim.substring(dim.indexOf(':') + 1).replace('_', ' ');
		};
	}

	// ---------------------------------------------------------------- the journal

	/** Adds a place to the journal. @return true if it's new for this player */
	public static boolean remember(ServerPlayer p, String key, String name, String kind, BlockPos at) {
		Data d = STORE.get(p);
		for (Place pl : d.places) if (pl.key.equals(key)) return false;
		Place pl = new Place();
		pl.key = key;
		pl.name = name;
		pl.kind = kind;
		pl.dim = p.level().dimension().identifier().toString();
		pl.x = at.getX();
		pl.z = at.getZ();
		pl.found = System.currentTimeMillis();
		d.places.add(pl);
		STORE.markDirty(p);
		return true;
	}

	/** The journal window: what you found, grouped by kind, with a beam to the nearest places. */
	public static void journal(ServerPlayer p) {
		Data d = STORE.get(p);
		int biomesTotal = p.level().registryAccess().lookupOrThrow(Registries.BIOME).size();
		Window w = Window.of(Ui.text("Journal", ChatFormatting.GOLD).withStyle(ChatFormatting.BOLD));
		w.text(Ui.text(d.places.size() + (d.places.size() == 1 ? " place" : " places") + " found · " + d.biomes.size() + " of "
				+ biomesTotal + " biomes", ChatFormatting.GRAY));
		if (d.places.isEmpty()) {
			w.text(Ui.text("Nothing yet. Walk into a village, a temple or another stretch of land: its name appears at the "
					+ "top of your screen, and it's written down here.", ChatFormatting.GRAY));
		}
		String dim = p.level().dimension().identifier().toString();
		Map<String, List<Place>> byKind = new LinkedHashMap<>();
		List<Place> sorted = new ArrayList<>(d.places);
		sorted.sort(Comparator.comparingLong((Place pl) -> pl.found).reversed());
		for (Place pl : sorted) byKind.computeIfAbsent(pl.kind, k -> new ArrayList<>()).add(pl);
		for (Map.Entry<String, List<Place>> e : byKind.entrySet()) {
			w.text(Ui.text(e.getKey() + "  (" + e.getValue().size() + ")", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD));
			StringBuilder lines = new StringBuilder();
			for (Place pl : e.getValue()) {
				if (!lines.isEmpty()) lines.append('\n');
				lines.append(pl.name).append("  ·  ").append(where(p, pl, dim)).append("  ·  ").append(ago(pl.found));
			}
			w.text(Ui.text(lines.toString(), ChatFormatting.GRAY));
		}
		// A beam to the nearest few in this world
		List<Place> here = new ArrayList<>();
		for (Place pl : d.places) if (pl.dim.equals(dim)) here.add(pl);
		here.sort(Comparator.comparingDouble(pl -> dist2(p, pl)));
		for (Place pl : here.subList(0, Math.min(6, here.size()))) {
			w.button("Beam: " + pl.name, pl.kind + " · " + Math.round(Math.sqrt(dist2(p, pl))) + " m", pp -> beam(pp, pl));
		}
		w.done().show(p);
	}

	private static double dist2(ServerPlayer p, Place pl) {
		double dx = pl.x + 0.5 - p.getX(), dz = pl.z + 0.5 - p.getZ();
		return dx * dx + dz * dz;
	}

	private static String where(ServerPlayer p, Place pl, String dim) {
		if (!pl.dim.equals(dim)) return dimension(pl.dim);
		double m = Math.sqrt(dist2(p, pl));
		return m >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f km", m / 1000) : Math.round(m) + " m";
	}

	private static String ago(long ms) {
		long min = Math.max(0, (System.currentTimeMillis() - ms) / 60000);
		if (min < 60) return min <= 1 ? "just now" : min + " min ago";
		long h = min / 60;
		if (h < 48) return h + " h ago";
		return h / 24 + " days ago";
	}

	private static void beam(ServerPlayer p, Place pl) {
		ServerLevel level = p.level();
		int y = level.getChunkSource().getChunkNow(pl.x >> 4, pl.z >> 4) != null
				? level.getHeight(Heightmap.Types.MOTION_BLOCKING, pl.x, pl.z) : (int) p.getY();
		Guide.toPoint(p, level, new Vec3(pl.x + 0.5, y, pl.z + 0.5), new ItemStack(Items.FILLED_MAP), Component.literal(pl.name), 0);
	}
}
