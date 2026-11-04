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
		@Nullable String landmark;
		int landmarkMissed;
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
				if (found.kind().named() && nl.libreria.core.Chronicle.firstTime("place:" + found.key())) {
					nl.libreria.core.Chronicle.write(p, nl.libreria.core.Chronicle.Kind.DISCOVERY, "discovered " + found.name() + ", "
							+ article(found.kind().label()) + " " + found.kind().label().toLowerCase(java.util.Locale.ROOT), false);
				}
				if (mode == 0 || (mode == 1 && isNew)) {
					show(p, st, found.key(), Component.literal(found.name()),
							Component.literal(found.kind().named() ? found.kind().label() + (isNew ? " · discovered" : "")
									: isNew ? "discovered" : ""), now, isNew);
				}
				return;
			}
		}
		if (st.place != null) return;
		// A peak, a cavern or a lake (looked for every two seconds)
		if (now % 40 < 10) {
			@Nullable Mark mark = landmarkAt(p, biome);
			String markKey = null;
			if (mark != null) {
				Names.Area area = Names.landmark(dim, mark.type(), mark.at().getX(), mark.at().getZ(), level.getSeed());
				markKey = "landmark|" + dim + "|" + area.biome + "|" + area.x + "," + area.z;
				if (!markKey.equals(st.landmark)) {
					String label = mark.type().label;
					boolean isNew = remember(p, markKey, area.name, label, new BlockPos(area.x, mark.at().getY(), area.z));
					if (nl.libreria.core.Chronicle.firstTime("place:" + markKey)) {
						nl.libreria.core.Chronicle.write(p, nl.libreria.core.Chronicle.Kind.DISCOVERY, "discovered " + area.name + ", "
								+ article(label) + " " + label.toLowerCase(java.util.Locale.ROOT), false);
					}
					if (mode == 0 || (mode == 1 && isNew)) {
						show(p, st, markKey, Component.literal(area.name), Component.literal(label + (isNew ? " · discovered" : "")), now, isNew);
						// No biome name right after it
						st.biome = biome;
						st.biomeShown = true;
					}
				}
			}
			// Stays "at the lake" until you're clearly away from it (no title again when you step back and forth)
			if (markKey != null || st.landmarkMissed++ >= 3) {
				st.landmark = markKey;
				st.landmarkMissed = 0;
			}
		}
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

	// ---------------------------------------------------------------- peaks, caverns and lakes

	/** A landmark and the spot that names it (a peak's summit, the middle of a lake). */
	record Mark(PlaceNames.Landmark type, BlockPos at) {
	}

	private static final Set<String> CAVE_BIOMES = Set.of("lush_caves", "dripstone_caves", "deep_dark");

	/** The peak you stand on, the big cavern you're in or the lake you're at, or null. Only in the Overworld. */
	static @Nullable Mark landmarkAt(ServerPlayer p, @Nullable String biome) {
		ServerLevel level = p.level();
		if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD || p.isSpectator()) return null;
		BlockPos pos = p.blockPosition();
		Integer ground = height(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		if (ground == null) return null;
		String path = biome == null ? "" : biome.substring(biome.indexOf(':') + 1);
		if (pos.getY() < ground - 15) {
			return !CAVE_BIOMES.contains(path) && cavern(level, pos) ? new Mark(PlaceNames.Landmark.CAVE, pos) : null;
		}
		if (pos.getY() < ground - 4) return null;
		if (ground >= level.getSeaLevel() + 50) {
			BlockPos summit = summit(level, pos, ground);
			if (summit != null) return new Mark(PlaceNames.Landmark.PEAK, summit);
		}
		if (!path.contains("ocean") && !path.contains("river") && !path.contains("beach") && !path.contains("swamp")
				&& !path.contains("shore")) {
			BlockPos middle = lake(level, pos);
			if (middle != null) return new Mark(PlaceNames.Landmark.LAKE, middle);
		}
		return null;
	}

	/** You're at the top (the highest point within 16 blocks) of land that falls away on (almost) every side. */
	private static @Nullable BlockPos summit(ServerLevel level, BlockPos pos, int ground) {
		int best = ground, bx = pos.getX(), bz = pos.getZ();
		for (int dx = -16; dx <= 16; dx += 4) {
			for (int dz = -16; dz <= 16; dz += 4) {
				Integer h = height(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX() + dx, pos.getZ() + dz);
				if (h == null) return null;
				if (h > best) {
					best = h;
					bx = pos.getX() + dx;
					bz = pos.getZ() + dz;
				}
			}
		}
		if (ground < best - 3) return null;
		int lower = 0;
		for (int i = 0; i < 16; i++) {
			double a = Math.PI * 2 * i / 16;
			Integer h = height(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx + (int) Math.round(Math.cos(a) * 40),
					bz + (int) Math.round(Math.sin(a) * 40));
			if (h == null) return null;
			if (h > best) return null;
			if (h <= best - 15) lower++;
		}
		return lower >= 12 ? new BlockPos(bx, best, bz) : null;
	}

	/** Mostly open space around you, deep underground: a big cave. */
	private static boolean cavern(ServerLevel level, BlockPos pos) {
		if (level.getChunkSource().getChunkNow((pos.getX() - 12) >> 4, (pos.getZ() - 12) >> 4) == null
				|| level.getChunkSource().getChunkNow((pos.getX() + 12) >> 4, (pos.getZ() + 12) >> 4) == null) return false;
		int open = 0, total = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -12; dx <= 12; dx += 4) {
			for (int dz = -12; dz <= 12; dz += 4) {
				for (int dy = -2; dy <= 10; dy += 4) {
					total++;
					if (level.getBlockState(m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz)).isAir()) open++;
				}
			}
		}
		return open >= total * 0.55;
	}

	/** Lots of water around you (a third of the ground within 24 blocks): the middle of that water, or null. */
	private static @Nullable BlockPos lake(ServerLevel level, BlockPos pos) {
		int water = 0, total = 0;
		long sx = 0, sz = 0;
		int surface = pos.getY();
		for (int dx = -24; dx <= 24; dx += 4) {
			for (int dz = -24; dz <= 24; dz += 4) {
				int x = pos.getX() + dx, z = pos.getZ() + dz;
				Integer top = height(level, Heightmap.Types.MOTION_BLOCKING, x, z);
				Integer floor = height(level, Heightmap.Types.OCEAN_FLOOR, x, z);
				if (top == null || floor == null) return null;
				total++;
				if (top > floor && level.getFluidState(new BlockPos(x, top - 1, z)).is(net.minecraft.tags.FluidTags.WATER)) {
					water++;
					sx += x;
					sz += z;
					surface = top - 1;
				}
			}
		}
		if (water < total * 0.35) return null;
		return new BlockPos((int) (sx / water), surface, (int) (sz / water));
	}

	private static @Nullable Integer height(ServerLevel level, Heightmap.Types type, int x, int z) {
		if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return null;
		return level.getHeight(type, x, z);
	}

	static String article(String word) {
		return "aeiouAEIOU".indexOf(word.charAt(0)) >= 0 ? "an" : "a";
	}

	/** The nearest place in this player's journal within 500 blocks (for "near Oakhaven"), or null. */
	static @Nullable String nearest(ServerPlayer p, String dim, int x, int z) {
		Place best = null;
		double bestD = 500 * 500;
		for (Place pl : STORE.get(p).places) {
			if (!pl.dim.equals(dim)) continue;
			double dx = pl.x - x, dz = pl.z - z, d = dx * dx + dz * dz;
			// Villages, temples, peaks and lakes before stretches of land
			if (pl.key.startsWith("area|")) d = d * 2 + 1;
			if (d < bestD) {
				bestD = d;
				best = pl;
			}
		}
		return best == null ? null : best.name;
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

	/** The journal in "Your places": everything you found, with the way there. */
	static void registerPlaces() {
		nl.libreria.core.PlaceList.kind("journal", "Journal", Items.WRITABLE_BOOK, 50)
				.about("Villages, temples, peaks, lakes and", "stretches of land you found.")
				.empty("Walk into a village, a temple or", "another stretch of land: its name", "appears at the top of your screen,",
						"and it's written down here.")
				.footer(Places::summary);
		nl.libreria.core.PlaceList.addSource((p, out) -> {
			for (Place pl : STORE.get(p).places) {
				out.add(nl.libreria.core.PlaceList.place("journal", pl.name, new ItemStack(icon(pl.kind)), pl.dim,
								new Vec3(pl.x + 0.5, p.getY(), pl.z + 0.5))
						.lore(Ui.text(pl.kind, ChatFormatting.GRAY), Ui.kv("Found ", ago(pl.found), ChatFormatting.GRAY))
						.go(pp -> beam(pp, pl)));
			}
		});
	}

	/** The journal (its tab in Your places). */
	public static void journal(ServerPlayer p) {
		nl.libreria.core.PlaceList.open(p, "journal");
	}

	/** "38 places · 21 of 64 biomes", at the bottom of the journal. */
	private static eu.pb4.sgui.api.elements.GuiElementBuilder summary(ServerPlayer p) {
		Data d = STORE.get(p);
		int biomesTotal = p.level().registryAccess().lookupOrThrow(Registries.BIOME).size();
		var b = nl.libreria.core.gui.Menu.button(Items.FILLED_MAP, "Your journal", ChatFormatting.GOLD,
				d.places.size() + (d.places.size() == 1 ? " place" : " places") + " found",
				d.biomes.size() + " of " + biomesTotal + " biomes visited");
		return nl.libreria.core.gui.Menu.actions(b, "Click: the chronicle of this world").setCallback((i, t, a, g) -> {
			Ui.click(p);
			nl.libreria.core.Chronicle.open(p);
		});
	}

	/** A picture per kind of place. */
	private static net.minecraft.world.item.Item icon(String kind) {
		net.minecraft.world.item.Item it = switch (kind) {
			case "Village" -> Items.BELL;
			case "Desert Temple" -> Ui.item("chiseled_sandstone");
			case "Jungle Temple" -> Ui.item("mossy_cobblestone");
			case "Woodland Mansion" -> Ui.item("dark_oak_log");
			case "Pillager Outpost" -> Items.CROSSBOW;
			case "Ocean Monument" -> Ui.item("prismarine");
			case "Stronghold" -> Ui.item("end_portal_frame");
			case "Ancient City" -> Ui.item("sculk");
			case "Trial Chambers" -> Ui.item("trial_key");
			case "Trail Ruins" -> Ui.item("brush");
			case "Nether Fortress" -> Ui.item("nether_bricks");
			case "Bastion" -> Ui.item("gilded_blackstone");
			case "End City" -> Ui.item("purpur_block");
			case "Peak" -> Ui.item("stone");
			case "Cavern" -> Ui.item("pointed_dripstone");
			case "Lake" -> Items.WATER_BUCKET;
			default -> Ui.item("oak_sapling");
		};
		return it == Items.AIR ? Items.FILLED_MAP : it;
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
