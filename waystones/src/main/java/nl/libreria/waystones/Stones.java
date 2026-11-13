package nl.libreria.waystones;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Hud;
import nl.libreria.core.Libreria;
import nl.libreria.core.Notify;
import nl.libreria.core.PlayerStore;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import nl.libreria.core.WorldStore;
import org.jetbrains.annotations.Nullable;

/**
 * Every waystone and who found which. A waystone is known by where its lower half stands. Village waystones are
 * registered the first time their chunk is loaded (they're placed while the world is generated), carrying the
 * village's name. You travel to the waystones you found (or, when the server says so, every village's), from a
 * waystone; it costs a little XP by distance (at most 1.5 levels), or nothing with an ender pearl.
 */
final class Stones {
	public static final class Stone {
		public String dim = "minecraft:overworld";
		public int x;
		public int y;
		public int z;
		public String facing = "south";
		public String name = "";
		public @Nullable UUID owner;
		public String ownerName = "";
		/** Placed with a new village. */
		public boolean village;
		/** Only the owner and the players they trust (on their land) may use it. */
		public boolean secret;
		/** On everyone's list, found or not (its owner, an operator, or for a village's who found it, shared it). */
		public boolean shared;
		public long made;
		/** Dark stone (else light). */
		public boolean dark;
		/** The dye it was given ("" = its stone's own colour: purple on light stone, cyan on dark). */
		public String dye = "";
		/** Its runes glow (a glow ink sac), or not (an ink sac; as a new one is). */
		public boolean glow;
		/** A grand waystone (a shrine 5 × 5 round it, see {@link Grand}): leaving from it is free. */
		public boolean grand;
		/** Who has it on their list (found it): id → name, so its land's owner can take someone off. */
		public Map<UUID, String> finders = new LinkedHashMap<>();
		/** On claimed land: its land's owner opened it to visitors (anyone may click it there). */
		public boolean visitors;

		DyeColor colour() {
			DyeColor d = this.dye == null || this.dye.isEmpty() ? null : DyeColor.byName(this.dye, null);
			return d != null ? d : this.dark ? DyeColor.CYAN : DyeColor.PURPLE;
		}

		int rgb() {
			return WaystoneLook.shine(colour());
		}

		BlockPos pos() {
			return new BlockPos(this.x, this.y, this.z);
		}

		String key() {
			return Stones.key(this.dim, this.x, this.y, this.z);
		}

		Direction front() {
			Direction d = Direction.byName(this.facing);
			return d == null || d.getAxis().isVertical() ? Direction.SOUTH : d;
		}
	}

	public static final class Data {
		public Map<String, Stone> stones = new LinkedHashMap<>();
		/** Waystones that went off every list: what they were, why and when (the newest few hundred). */
		public Map<String, Gone> gone = new LinkedHashMap<>();
		/** The players' finds were read once to know who has which waystone (from before {@link Stone#finders}). */
		public boolean findersRead;
	}

	/** A waystone that's gone: its name, why, when. */
	public static final class Gone {
		public String name = "";
		public boolean village;
		public String why = "";
		public long at;
	}

	/** How many gone waystones are remembered. */
	private static final int GONE_KEPT = 400;

	public static final class Found {
		public Set<String> found = new LinkedHashSet<>();
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/waystones.json", Data.class, Data::new);
	static final PlayerStore<Found> FOUND = PlayerStore.create("libreria/waystones", Found.class, Found::new, f -> {
		if (f.found == null) f.found = new LinkedHashSet<>();
	});
	/** Travelling costs at most this much XP (in levels). */
	static final double MAX_LEVELS = 1.5;

	private Stones() {
	}

	static String key(String dim, int x, int y, int z) {
		return dim + "|" + x + "|" + y + "|" + z;
	}

	static String dim(Level level) {
		return level.dimension().identifier().toString();
	}

	private static Map<String, Stone> all() {
		Data d = STORE.get();
		if (d.stones == null) d.stones = new LinkedHashMap<>();
		return d.stones;
	}

	/** (Self-tests: a waystone on the lists again, as if its chunk had been cleared outside the game.) */
	static void remember(Stone s) {
		all().put(s.key(), s);
		STORE.markDirty();
	}

	static @Nullable Stone byKey(String key) {
		return all().get(key);
	}

	static @Nullable Stone at(Level level, BlockPos lower) {
		return all().get(key(dim(level), lower.getX(), lower.getY(), lower.getZ()));
	}

	static List<Stone> list() {
		return List.copyOf(all().values());
	}

	/** The waystone at {@code lower}: known already, or (a village's, placed while the world was made) written down now. */
	static Stone ensure(ServerLevel level, BlockPos lower, BlockState state) {
		Stone s = at(level, lower);
		if (s != null) return s;
		s = new Stone();
		s.dim = dim(level);
		s.x = lower.getX();
		s.y = lower.getY();
		s.z = lower.getZ();
		s.facing = state.hasProperty(WaystoneBlock.FACING) ? state.getValue(WaystoneBlock.FACING).getName() : "south";
		s.village = state.hasProperty(WaystoneBlock.VILLAGE) && state.getValue(WaystoneBlock.VILLAGE);
		s.made = System.currentTimeMillis();
		String place = Services.placeNames().map(p -> p.nameAt(level, lower)).orElse(null);
		s.name = place != null ? place : s.village ? "Village waystone" : "Waystone";
		all().put(s.key(), s);
		STORE.markDirty();
		return s;
	}

	/** A player placed one: theirs, named after them until they name it. */
	static Stone placed(ServerPlayer p, ServerLevel level, BlockPos lower, BlockState state, boolean dark) {
		Stone s = ensure(level, lower, state);
		s.dark = dark;
		s.owner = p.getUUID();
		s.ownerName = p.getName().getString();
		s.village = false;
		String place = Services.placeNames().map(n -> n.nameAt(level, lower)).orElse(null);
		s.name = place != null ? place : p.getName().getString() + "'s waystone";
		STORE.markDirty();
		FOUND.get(p).found.add(s.key());
		FOUND.markDirty(p);
		return s;
	}

	/** A waystone stands at {@code lower}: an ordinary one's lower block, or a grand one's own block. */
	static boolean standing(Level level, BlockPos lower) {
		BlockState st = level.getBlockState(lower);
		return st.is(WaystonesMod.WAYSTONE) || st.is(WaystonesMod.GRAND) && GrandBlock.isMiddle(st);
	}

	static void removed(Level level, BlockPos lower, String why) {
		forget(key(dim(level), lower.getX(), lower.getY(), lower.getZ()), why);
	}

	/** Off every list, with why (written down and in the log). */
	static void forget(String key, String why) {
		Stone s = all().remove(key);
		if (s == null) return;
		Data d = STORE.get();
		if (d.gone == null) d.gone = new LinkedHashMap<>();
		Gone g = new Gone();
		g.name = s.name;
		g.village = s.village;
		g.why = why;
		g.at = System.currentTimeMillis();
		d.gone.remove(key);
		d.gone.put(key, g);
		while (d.gone.size() > GONE_KEPT) d.gone.remove(d.gone.keySet().iterator().next());
		STORE.markDirty();
		Libreria.LOG.info("Waystones: {} ({}) is off every list: {}", s.name, key, why);
	}

	/** Why a waystone that was here is gone, or null when that isn't known. */
	static @Nullable Gone gone(String key) {
		Data d = STORE.get();
		return d.gone == null ? null : d.gone.get(key);
	}

	/** Chunks of a world made anew (the world refresh): the waystones that stood in them are gone, off every list. */
	static int chunksGone(String dimension, java.util.Set<Long> chunks) {
		List<String> going = new ArrayList<>();
		for (Stone s : all().values()) {
			long chunk = ((s.x >> 4) & 0xFFFFFFFFL) | ((long) (s.z >> 4) << 32);
			if (s.dim.equals(dimension) && chunks.contains(chunk)) going.add(s.key());
		}
		for (String k : going) forget(k, "its land was made anew (world refresh)");
		if (!going.isEmpty()) Libreria.LOG.info("Waystones: {} gone with the chunks made anew in {}", going.size(), dimension);
		return going.size();
	}

	// ---------------------------------------------------------------- who may

	/** The owner of the land it stands on (a claim), or null on free land. */
	static @Nullable UUID landOwner(ServerLevel level, Stone s) {
		return Services.land().filter(Services.Land::active).map(l -> l.ownerAt(level, s.pos())).orElse(null);
	}

	/** May open it to visitors (or close it): its land's owner, an operator. */
	static boolean mayOpenToVisitors(ServerPlayer p, Stone s) {
		if (!(p.level() instanceof ServerLevel)) return false;
		ServerLevel level = level(p.level().getServer(), s.dim);
		UUID owner = level == null ? null : landOwner(level, s);
		return owner != null && (owner.equals(p.getUUID()) || Ui.isOp(p));
	}

	/** For claims: a waystone (its stone, altar or shrine) its land's owner opened to visitors. */
	static boolean openToVisitors(ServerLevel level, BlockPos pos) {
		BlockState st = level.getBlockState(pos);
		BlockPos lower = st.is(WaystonesMod.GRAND) ? GrandBlock.middleOf(pos, st) : WaystoneBlock.lowerOf(level, pos, st);
		if (lower == null) return false;
		Stone s = at(level, lower);
		return s != null && s.visitors;
	}

	static boolean owns(ServerPlayer p, Stone s) {
		return s.owner != null && s.owner.equals(p.getUUID());
	}

	/** May use it: it's open, or theirs, or they're trusted by its owner, or an operator. */
	static boolean mayUse(ServerPlayer p, Stone s) {
		if (!s.secret || owns(p, s) || Ui.isOp(p)) return true;
		return s.owner != null && Services.land().map(l -> l.trusts(s.owner, p.getUUID())).orElse(false);
	}

	/** May put it on everyone's list (or take it off): its owner, an operator, and for a village's who found it. */
	static boolean mayShare(ServerPlayer p, Stone s) {
		if (owns(p, s) || Ui.isOp(p)) return true;
		return s.village && FOUND.get(p).found.contains(s.key());
	}

	/** May break it: a player's, its owner or someone they trust; a village's, only an operator. */
	static boolean mayBreak(ServerPlayer p, @Nullable Stone s) {
		if (Ui.isOp(p)) return true;
		if (s == null) return true;
		if (s.village) return false;
		return owns(p, s) || s.owner != null && Services.land().map(l -> l.trusts(s.owner, p.getUUID())).orElse(false);
	}

	/** On their list: found, theirs, shared with everyone, or a village's when the server makes those known to everyone. */
	static boolean known(ServerPlayer p, Stone s) {
		if (owns(p, s) || s.shared || FOUND.get(p).found.contains(s.key())) return true;
		return s.village && WaystonesMod.KNOWN.get() == 1;
	}

	/** The waystones this player can travel to, nearest first (this world before others). */
	static List<Stone> knownBy(ServerPlayer p) {
		List<Stone> out = new ArrayList<>();
		for (Stone s : all().values()) if (known(p, s) && mayUse(p, s)) out.add(s);
		String here = dim(p.level());
		out.sort(Comparator.comparingDouble(s -> s.dim.equals(here) ? p.position().distanceToSqr(s.x + 0.5, s.y, s.z + 0.5) : Double.MAX_VALUE));
		return out;
	}

	/** Found now: true the first time. */
	static boolean discover(ServerPlayer p, Stone s) {
		if (!(s.owner != null && s.owner.equals(p.getUUID()))) {
			if (s.finders == null) s.finders = new LinkedHashMap<>();
			if (!p.getName().getString().equals(s.finders.put(p.getUUID(), p.getName().getString()))) STORE.markDirty();
		}
		if (!FOUND.get(p).found.add(s.key())) return false;
		FOUND.markDirty(p);
		return true;
	}

	/** Takes someone's find of this waystone away: off their list (until they find it again). Nobody else's changes. */
	static boolean unfind(MinecraftServer server, Stone s, UUID who) {
		boolean was = s.finders != null && s.finders.remove(who) != null;
		Found f = FOUND.get(who);
		if (f.found.remove(s.key())) {
			FOUND.markDirty(who);
			was = true;
		}
		if (was) STORE.markDirty();
		return was;
	}

	/**
	 * Once: who has which waystone, read from every player's finds (from before that was kept on the waystone itself),
	 * so its land's owner sees everyone who has it on their list.
	 */
	static void readFinds(MinecraftServer server) {
		Data d = STORE.get();
		if (d.findersRead) return;
		java.nio.file.Path dir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("libreria").resolve("waystones");
		Map<UUID, String> names = new java.util.HashMap<>();
		for (var k : nl.libreria.core.Players.all(server)) names.put(k.id(), k.name());
		int marked = 0;
		if (java.nio.file.Files.isDirectory(dir)) {
			try (var files = java.nio.file.Files.list(dir)) {
				for (java.nio.file.Path f : files.filter(f -> f.getFileName().toString().endsWith(".json")).toList()) {
					UUID who;
					try {
						who = UUID.fromString(f.getFileName().toString().replace(".json", ""));
					} catch (IllegalArgumentException e) {
						continue;
					}
					try (var r = java.nio.file.Files.newBufferedReader(f)) {
						Found found = new com.google.gson.Gson().fromJson(r, Found.class);
						if (found == null || found.found == null) continue;
						for (String k : found.found) {
							Stone s = all().get(k);
							if (s == null || who.equals(s.owner)) continue;
							if (s.finders == null) s.finders = new LinkedHashMap<>();
							if (s.finders.putIfAbsent(who, names.getOrDefault(who, "?")) == null) marked++;
						}
					} catch (Exception e) {
						Libreria.LOG.warn("Waystones: couldn't read {}", f, e);
					}
				}
			} catch (java.io.IOException e) {
				Libreria.LOG.warn("Waystones: couldn't read the players' finds", e);
				return;
			}
		}
		d.findersRead = true;
		STORE.markDirty();
		if (marked > 0) Libreria.LOG.info("Waystones: {} finds read (who has which waystone on their list)", marked);
	}

	// ---------------------------------------------------------------- the price

	/** What going there costs, in levels: a level per so many blocks, at most {@link #MAX_LEVELS}; another world: the most. */
	static double levels(ServerPlayer p, Stone to) {
		int per = WaystonesMod.BLOCKS_PER_LEVEL.get();
		if (per <= 0) return 0;
		if (!to.dim.equals(dim(p.level()))) return MAX_LEVELS;
		double dist = Math.sqrt(p.position().distanceToSqr(to.x + 0.5, to.y, to.z + 0.5));
		return Math.min(MAX_LEVELS, dist / per);
	}

	/** Those levels in XP points, at the player's own level (whatever a level is worth to them now). */
	static int points(ServerPlayer p, double levels) {
		if (levels <= 0) return 0;
		return Math.max(1, (int) Math.ceil(levels * Math.max(7, p.getXpNeededForNextLevel())));
	}

	/** All the XP points a player holds. */
	static int held(ServerPlayer p) {
		int l = p.experienceLevel;
		long below = l <= 16 ? (long) l * l + 6L * l : l <= 31 ? (long) (2.5 * l * l - 40.5 * l + 360) : (long) (4.5 * l * l - 162.5 * l + 2220);
		return (int) Math.min(Integer.MAX_VALUE, below + Math.round(p.experienceProgress * p.getXpNeededForNextLevel()));
	}

	static String levelsText(double levels) {
		if (levels <= 0) return "free";
		String s = String.format(java.util.Locale.ROOT, "%.1f", levels);
		if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
		return s + (s.equals("1") ? " level" : " levels");
	}

	// ---------------------------------------------------------------- travelling

	/**
	 * Travels to {@code to}: paying with an ender pearl (free of XP), or with XP. False (with why) when it can't.
	 */
	static boolean travel(ServerPlayer p, Stone to, boolean withPearl) {
		return travel(p, to, withPearl, null);
	}

	/** Travels from the waystone {@code from} (null: not at one): leaving from a grand waystone is free. */
	static boolean travel(ServerPlayer p, Stone to, boolean withPearl, @Nullable Stone from) {
		ServerLevel level = check(p, to);
		if (level == null) return false;
		boolean free = from != null && from.grand && !withPearl;
		List<net.minecraft.world.entity.Mob> animals = nl.libreria.core.Leashed.along(p);
		int times = times(animals);
		if (!p.isCreative() && !free) {
			if (withPearl) {
				if (pearls(p) < times) {
					Notify.info(p, Items.ENDER_PEARL, times == 1 ? "No ender pearl" : "Two ender pearls",
							times == 1 ? "With an ender pearl the trip is free; without, it costs a little XP."
									: "With " + nl.libreria.core.Leashed.words(animals) + " on your lead it takes two.");
					return false;
				}
				for (int i = 0; i < times; i++) takePearl(p);
			} else {
				double levels = levels(p, to) * times;
				int cost = points(p, levels);
				if (cost > held(p)) {
					Notify.info(p, Items.EXPERIENCE_BOTTLE, "Not enough XP", "It takes " + levelsText(levels)
							+ (times > 1 ? " with " + nl.libreria.core.Leashed.words(animals) : "") + ". With an ender pearl it's free (right-click).");
					return false;
				}
				if (cost > 0) p.giveExperiencePoints(-cost);
			}
		}
		go(p, level, to, animals);
		return true;
	}

	/** What a trip costs times what it would alone: twice with animals on your lead. */
	static int times(List<?> animals) {
		return animals.isEmpty() ? 1 : 2;
	}

	/** Ender pearls in the inventory. */
	private static int pearls(ServerPlayer p) {
		int n = 0;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(Items.ENDER_PEARL)) n += inv.getItem(i).getCount();
		return n;
	}

	/** The world of {@code to} when this player may go there now; null (and why) when not. */
	static @Nullable ServerLevel check(ServerPlayer p, Stone to) {
		if (!WaystonesMod.MODULE.on()) return null;
		ServerLevel level = level(p.level().getServer(), to.dim);
		if (level == null) {
			Notify.warn(p, Items.BARRIER, "That world is gone", to.name + " is in a world this server doesn't have.");
			return null;
		}
		if (level != p.level() && !WaystonesMod.WORLDS.get() && !Ui.isOp(p)) {
			Notify.warn(p, Items.OBSIDIAN, "Not between worlds", "Waystones only reach their own world here.");
			return null;
		}
		if (!standing(level, to.pos()) && !restore(level, to)) {
			// Gone without anybody taking it away here (the host cleared its chunk), and something else stands there now
			Notify.warn(p, Items.BARRIER, to.name + " is blocked", "Its spot is taken by something else now, so you stay here (nothing paid).");
			return null;
		}
		if (!mayUse(p, to)) {
			Notify.warn(p, Items.BARRIER, to.name + " is private", "Only " + to.ownerName + " and the people they trust can use it.");
			return null;
		}
		return level;
	}

	private static boolean takePearl(ServerPlayer p) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(Items.ENDER_PEARL)) {
				s.shrink(1);
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- put back

	/**
	 * A waystone that's on the lists but whose block isn't there: nobody took it away here (that would have taken it off
	 * every list), so its chunk was cleared outside the game (the host clears chunks nobody uses much). It's put back as
	 * it was, from what's kept of it (place, look, name, owner). False, and nothing changes, when something else
	 * stands on its spot now.
	 */
	static boolean restore(ServerLevel level, Stone s) {
		BlockPos lower = s.pos();
		Direction f = s.front();
		if (s.grand) {
			for (int[] c : Grand.places()) {
				BlockPos b = Grand.at(lower, f, c[0], c[1], c[2]);
				BlockState st = level.getBlockState(b);
				// the floor sinks into the ground anyway; above it only what's loose (grass, flowers, snow) may go
				if (c[2] < 0 ? level.getBlockEntity(b) != null : !(st.canBeReplaced() || st.is(WaystonesMod.GRAND))) return false;
			}
			Grand.build(level, lower, f, s, false);
		} else {
			for (BlockPos b : new BlockPos[] {lower, lower.above(), lower.above(2), lower.relative(f)}) {
				BlockState st = level.getBlockState(b);
				if (!st.canBeReplaced() || !st.getFluidState().isEmpty()) return false;
			}
			WaystoneBlock.put(level, lower, f, s.village, net.minecraft.world.level.block.Block.UPDATE_ALL);
		}
		Libreria.LOG.info("Waystones: {} ({}) put back: its chunk had been cleared", s.name, s.key());
		return true;
	}

	/** Chunks that loaded (world and chunk), to look at their waystones a moment later. */
	private static final java.util.Queue<Object[]> LOADED = new java.util.concurrent.ConcurrentLinkedQueue<>();

	/** A chunk loaded: its waystones are looked at a moment later (put back if their chunk was cleared). */
	static void chunkLoaded(ServerLevel level, int cx, int cz) {
		LOADED.add(new Object[] {dim(level), cx, cz});
	}

	/** (Every second) the waystones in the chunks that loaded: still standing, or put back. */
	static void lookAtLoaded(MinecraftServer server) {
		if (LOADED.isEmpty()) return;
		Set<String> chunks = new java.util.HashSet<>();
		for (Object[] c; (c = LOADED.poll()) != null;) chunks.add(c[0] + "|" + c[1] + "|" + c[2]);
		for (Stone s : List.copyOf(all().values())) {
			if (!chunks.contains(s.dim + "|" + (s.x >> 4) + "|" + (s.z >> 4))) continue;
			ServerLevel level = level(server, s.dim);
			if (level == null || !level.hasChunk(s.x >> 4, s.z >> 4)) continue;
			if (!standing(level, s.pos())) restore(level, s);
		}
	}

	/** Teleports there: in front of its altar, looking at the stone (with the animals on your lead). */
	static void go(ServerPlayer p, ServerLevel level, Stone to) {
		go(p, level, to, nl.libreria.core.Leashed.along(p));
	}

	static void go(ServerPlayer p, ServerLevel level, Stone to, List<net.minecraft.world.entity.Mob> animals) {
		// (No /back from here: going back where you came from for free would make the price pointless)
		Vec3 at = arrival(level, to);
		ServerLevel from = p.level();
		Vec3 was = p.position();
		if (p.isPassenger()) p.stopRiding();
		p.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(), to.front().getOpposite().toYRot(), 10, false);
		p.fallDistance = 0;
		nl.libreria.core.Leashed.bring(p, animals);
		from.sendParticles(ParticleTypes.REVERSE_PORTAL, was.x, was.y + 1, was.z, 30, 0.3, 0.7, 0.3, 0.05);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 30, 0.3, 0.7, 0.3, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 0.9f);
		Hud.flash(p, Ui.text("✦ ", 0xC79BF0).append(Ui.text(to.name, ChatFormatting.WHITE)));
	}

	/** Where you arrive: in front of the altar, or else the first open spot around the stone. */
	static Vec3 arrival(ServerLevel level, Stone s) {
		BlockPos lower = s.pos();
		Direction f = s.front();
		// a grand one: inside, on the plinth before its pedestal
		BlockPos inside = lower.relative(f);
		if (s.grand && level.getBlockState(inside).is(WaystonesMod.GRAND) && open(level, inside.above()) && open(level, inside.above(2))) {
			return Vec3.atBottomCenterOf(inside).add(0, 0.5, 0);
		}
		List<BlockPos> tries = new ArrayList<>();
		tries.add(lower.relative(f, 2));
		tries.add(lower.relative(f).relative(f.getClockWise()));
		tries.add(lower.relative(f).relative(f.getCounterClockWise()));
		tries.add(lower.relative(f.getClockWise()));
		tries.add(lower.relative(f.getCounterClockWise()));
		tries.add(lower.relative(f.getOpposite()));
		for (BlockPos t : tries) {
			for (int dy = 0; dy <= 2; dy++) {
				BlockPos feet = t.above(dy);
				if (open(level, feet) && open(level, feet.above()) && !open(level, feet.below())) return Vec3.atBottomCenterOf(feet);
			}
		}
		return Vec3.atBottomCenterOf(lower.above(2));
	}

	private static boolean open(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
	}

	static @Nullable ServerLevel level(MinecraftServer server, String dim) {
		for (ServerLevel l : server.getAllLevels()) if (dim(l).equals(dim)) return l;
		return null;
	}

	static String world(String dim) {
		return switch (dim) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
		};
	}

	// ---------------------------------------------------------------- its colour

	/** On a waystone item: the look it takes when placed (a dye, glowing runes), from the crafting grid or a broken one. */
	static final String LOOK = "libreria_waystone_look";

	/**
	 * A waystone item with this look: what it takes when placed, its colour on the item's runes, and a line saying it.
	 * The colouring recipes (scripts/waystones/dye_recipes.py) make exactly this item.
	 */
	static void lookOn(ItemStack stack, @Nullable String dye, boolean glow, boolean dark) {
		DyeColor own = dark ? DyeColor.CYAN : DyeColor.PURPLE;
		DyeColor c = dye == null || dye.isEmpty() ? own : DyeColor.byName(dye, own);
		net.minecraft.nbt.CompoundTag look = new net.minecraft.nbt.CompoundTag();
		look.putBoolean("glow", glow);
		if (dye != null && !dye.isEmpty()) look.putString("dye", dye);
		net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, root -> root.put(LOOK, look));
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
				new net.minecraft.world.item.component.CustomModelData(List.of(), List.of(), List.of(), List.of(WaystoneLook.shine(c))));
		stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
				net.minecraft.network.chat.Component.literal("Runes: " + c.getName().replace('_', ' ') + (glow ? ", glowing" : ""))
						.withStyle(st -> st.withColor(ChatFormatting.GRAY).withItalic(false)))));
	}

	/** A placed waystone takes the look its item carried. */
	static void lookFrom(ItemStack stack, Stone s) {
		net.minecraft.world.item.component.CustomData cd = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		if (cd == null) return;
		net.minecraft.nbt.CompoundTag look = cd.copyTag().getCompoundOrEmpty(LOOK);
		if (look.isEmpty()) return;
		s.dye = look.getStringOr("dye", "");
		s.glow = look.getBooleanOr("glow", false);
		STORE.markDirty();
	}

	/**
	 * A dye, a glow ink sac or an ink sac used on a waystone: its colour, glowing runes, or matte ones (as on a sign).
	 * True when the item was one of those (used up when it changed something).
	 */
	static boolean paint(ServerPlayer p, ServerLevel level, BlockPos lower, ItemStack stack) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		boolean glowInk = stack.is(Items.GLOW_INK_SAC), ink = stack.is(Items.INK_SAC);
		DyeColor dye = id.endsWith("_dye") ? DyeColor.byName(id.substring(0, id.length() - 4), null) : null;
		if (dye == null && !glowInk && !ink) return false;
		if (!WaystonesMod.MODULE.on()) return true;
		Stone s = ensure(level, lower, level.getBlockState(lower));
		if (!mayBreak(p, s)) {
			Notify.warn(p, WaystonesMod.WAYSTONE_ITEM, s.village ? "This waystone belongs to the village" : s.name + " is " + s.ownerName + "'s",
					s.village ? "Only an operator can change how it looks." : "Only they and the people they trust can change how it looks.");
			return true;
		}
		net.minecraft.sounds.SoundEvent sound;
		if (dye != null) {
			if (dye == s.colour()) return true;
			s.dye = dye.getName();
			sound = SoundEvents.DYE_USE;
		} else if (glowInk) {
			if (s.glow) return true;
			s.glow = true;
			sound = SoundEvents.GLOW_INK_SAC_USE;
		} else {
			if (!s.glow) return true;
			s.glow = false;
			sound = SoundEvents.INK_SAC_USE;
		}
		if (!p.isCreative()) stack.shrink(1);
		STORE.markDirty();
		level.playSound(null, lower.above(), sound, SoundSource.BLOCKS, 1f, 1f);
		return true;
	}

	// ---------------------------------------------------------------- a right-click on the stone

	/** Right-click on a waystone (or its altar): found, and the list of where you can go from here. */
	static void use(ServerPlayer p, ServerLevel level, BlockPos lower) {
		if (!WaystonesMod.MODULE.on()) return;
		BlockState state = level.getBlockState(lower);
		if (!standing(level, lower)) return;
		Stone s = ensure(level, lower, state);
		if (!mayUse(p, s)) {
			Notify.warn(p, Items.BARRIER, s.name + " is private", "Only " + s.ownerName + " and the people they trust can use it.");
			return;
		}
		if (discover(p, s)) {
			Notify.card(p, new ItemStack(WaystonesMod.WAYSTONE_ITEM), 0xC79BF0, Ui.text("Waystone found: ", ChatFormatting.WHITE).append(Ui.text(s.name, 0xC79BF0)),
					Ui.text("You can travel here from any waystone you know", ChatFormatting.GRAY));
			level.playSound(null, lower, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1f, 1.2f);
			Libreria.LOG.debug("{} found the waystone {}", p.getName().getString(), s.name);
			nl.libreria.core.Tips.once(p, WaystonesMod.TIP);
		}
		if (p.isShiftKeyDown() && (mayShare(p, s) || mayOpenToVisitors(p, s))) {
			WaystonesWindows.settings(p, s);
			return;
		}
		WaystonePlaces.travelFrom(p, s);
	}
}
