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
		public long made;

		BlockPos pos() {
			return new BlockPos(this.x, this.y, this.z);
		}

		String key() {
			return key(this.dim, this.x, this.y, this.z);
		}

		Direction front() {
			Direction d = Direction.byName(this.facing);
			return d == null || d.getAxis().isVertical() ? Direction.SOUTH : d;
		}
	}

	public static final class Data {
		public Map<String, Stone> stones = new LinkedHashMap<>();
	}

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
	static Stone placed(ServerPlayer p, ServerLevel level, BlockPos lower, BlockState state) {
		Stone s = ensure(level, lower, state);
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

	static void removed(Level level, BlockPos lower) {
		if (all().remove(key(dim(level), lower.getX(), lower.getY(), lower.getZ())) != null) STORE.markDirty();
	}

	// ---------------------------------------------------------------- who may

	static boolean owns(ServerPlayer p, Stone s) {
		return s.owner != null && s.owner.equals(p.getUUID());
	}

	/** May use it: it's open, or theirs, or they're trusted by its owner, or an operator. */
	static boolean mayUse(ServerPlayer p, Stone s) {
		if (!s.secret || owns(p, s) || Ui.isOp(p)) return true;
		return s.owner != null && Services.land().map(l -> l.trusts(s.owner, p.getUUID())).orElse(false);
	}

	/** May break it: a player's, its owner or someone they trust; a village's, only an operator. */
	static boolean mayBreak(ServerPlayer p, @Nullable Stone s) {
		if (Ui.isOp(p)) return true;
		if (s == null) return true;
		if (s.village) return false;
		return owns(p, s) || s.owner != null && Services.land().map(l -> l.trusts(s.owner, p.getUUID())).orElse(false);
	}

	/** On their list: found, theirs, or a village's when the server makes those known to everyone. */
	static boolean known(ServerPlayer p, Stone s) {
		if (owns(p, s) || FOUND.get(p).found.contains(s.key())) return true;
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
		if (!FOUND.get(p).found.add(s.key())) return false;
		FOUND.markDirty(p);
		return true;
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
		ServerLevel level = check(p, to);
		if (level == null) return false;
		if (!p.isCreative()) {
			if (withPearl) {
				if (!takePearl(p)) {
					Notify.info(p, Items.ENDER_PEARL, "No ender pearl", "With an ender pearl the trip is free; without, it costs a little XP.");
					return false;
				}
			} else {
				int cost = points(p, levels(p, to));
				if (cost > held(p)) {
					Notify.info(p, Items.EXPERIENCE_BOTTLE, "Not enough XP", "It takes " + levelsText(levels(p, to)) + ". With an ender pearl it's free (right-click).");
					return false;
				}
				if (cost > 0) p.giveExperiencePoints(-cost);
			}
		}
		go(p, level, to);
		return true;
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
		if (!level.getBlockState(to.pos()).is(WaystonesMod.WAYSTONE)) {
			if (all().remove(to.key()) != null) STORE.markDirty();
			Notify.warn(p, Items.BARRIER, "That waystone is gone", to.name + " isn't there any more.");
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

	/** Teleports there: in front of its altar, looking at the stone. */
	static void go(ServerPlayer p, ServerLevel level, Stone to) {
		Services.beforeTeleport(p);
		Vec3 at = arrival(level, to);
		ServerLevel from = p.level();
		Vec3 was = p.position();
		if (p.isPassenger()) p.stopRiding();
		p.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(), to.front().getOpposite().toYRot(), 10, false);
		p.fallDistance = 0;
		from.sendParticles(ParticleTypes.REVERSE_PORTAL, was.x, was.y + 1, was.z, 30, 0.3, 0.7, 0.3, 0.05);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 30, 0.3, 0.7, 0.3, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 0.9f);
		Hud.flash(p, Ui.text("✦ ", 0xC79BF0).append(Ui.text(to.name, ChatFormatting.WHITE)));
	}

	/** Where you arrive: in front of the altar, or else the first open spot around the stone. */
	static Vec3 arrival(ServerLevel level, Stone s) {
		BlockPos lower = s.pos();
		Direction f = s.front();
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

	// ---------------------------------------------------------------- a right-click on the stone

	/** Right-click on a waystone (or its altar): found, and the list of where you can go from here. */
	static void use(ServerPlayer p, ServerLevel level, BlockPos lower) {
		if (!WaystonesMod.MODULE.on()) return;
		BlockState state = level.getBlockState(lower);
		if (!state.is(WaystonesMod.WAYSTONE)) return;
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
		if (p.isShiftKeyDown() && (owns(p, s) || Ui.isOp(p))) {
			WaystonesWindows.settings(p, s);
			return;
		}
		WaystonePlaces.travelFrom(p, s);
	}
}
