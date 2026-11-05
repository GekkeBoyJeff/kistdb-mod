package nl.libreria.comfort;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRules;
import nl.libreria.core.Libreria;
import nl.libreria.core.ServerSetting;
import nl.libreria.core.Ui;
import nl.libreria.core.WorldStore;
import nl.libreria.core.gui.TopCard;
import org.jetbrains.annotations.Nullable;

/**
 * Sleeping together, and time that runs instead of jumps. An admin sets how many players must sleep (the game's own
 * {@code playersSleepingPercentage}). Once enough are asleep, the night doesn't skip at once: the world clock runs
 * fast until morning, so you see it get light and anyone can leave their bed to stop there. A card at the top says
 * how many sleep, and while the time runs, what time it is and until when.
 *
 * <p>Napping (an admin's choice): in the day, the first click on a bed says it's too early and that a second click
 * naps until evening. That needs as many players in bed as at night, and can be done once every few days for the
 * whole world (so nobody speeds up their crops with it).
 */
final class Sleep {
	static final ServerSetting.Number PERCENT = ComfortMod.MODULE.serverNumber("sleep_percent", "Players needed to sleep",
			Ui.item("red_bed"), 100, 0, 100, 1, "%", "How many of the players in a world must", "sleep to skip the night (0: one is enough).");
	static final ServerSetting.Toggle FAST = ComfortMod.MODULE.serverToggle("sleep_fast", "Time runs while sleeping", Ui.item("clock"), true,
			"Instead of jumping to morning, the time", "runs fast while enough players sleep.", "Leave your bed to stop it there.");
	static final ServerSetting.Number SECONDS = ComfortMod.MODULE.serverNumber("sleep_seconds", "A night takes", Ui.item("clock"), 15, 5, 60, 5,
			"seconds", "How long a whole night takes while", "the time runs (a nap is shorter).");
	static final ServerSetting.Toggle NAP = ComfortMod.MODULE.serverToggle("nap", "Napping in the day", Ui.item("white_bed"), true,
			"Click a bed twice in the day to nap", "until evening (as many players in bed", "as at night).");
	static final ServerSetting.Number NAP_DAYS = ComfortMod.MODULE.serverNumber("nap_days", "Days between naps", Ui.item("white_bed"), 3, 1, 14, 1,
			"days", "A nap can be done once every this", "many days, for the whole world.");

	private static final String CHANNEL = "comfort.sleep", NAP_CHANNEL = "comfort.nap";
	/** The second click must come within this many ticks of the first. */
	private static final int SECOND_CLICK = 100;
	/** A nap stops this long before nightfall: the evening. */
	private static final int EVENING_BEFORE_NIGHT = 1000;
	/** A nap shorter than this (it's nearly evening already) isn't offered. */
	private static final int SHORTEST_NAP = 600;
	/** About how long a night is (in clock ticks): it takes the set seconds, a longer stretch takes as long. */
	private static final int NIGHT = 11500;

	/** Kept in the world: when the last nap ended (clock ticks), and whether we sped a clock up (to undo after a crash). */
	static final class Data {
		public long lastNap = Long.MIN_VALUE / 2;
		public boolean fast;
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/sleep.json", Data.class, Data::new);

	/** Per world while the time runs. */
	private static final class Run {
		boolean nap;
		long from, target;
		float rate;
	}

	private static final Map<ResourceKey<net.minecraft.world.level.Level>, Run> RUNS = new HashMap<>();
	/** Who clicked a bed once in the day, and when (server tick). */
	private static final Map<UUID, Integer> FIRST_CLICK = new HashMap<>();
	/** Who is napping (in bed in the day). */
	private static final Set<UUID> NAPPING = new HashSet<>();

	private Sleep() {
	}

	static void init() {
		PERCENT.onChange(() -> {
			MinecraftServer s = Libreria.server();
			if (s != null) apply(s);
		});
	}

	/** Puts the setting into the game rule when an admin changes it. */
	static void apply(MinecraftServer server) {
		server.getGameRules().set(GameRules.PLAYERS_SLEEPING_PERCENTAGE, PERCENT.get(), server);
	}

	/**
	 * At start: a value set in the admin window wins; otherwise the setting takes over what the world already has, so
	 * installing Libreria changes nothing by itself. A clock left running fast (the server stopped mid-night) is set
	 * back to normal.
	 */
	static void start(MinecraftServer server) {
		if (PERCENT.isDefault()) PERCENT.set(Math.max(0, Math.min(100, server.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE))));
		else apply(server);
		RUNS.clear();
		NAPPING.clear();
		Data d = STORE.get();
		if (d.fast) {
			for (ServerLevel level : server.getAllLevels()) clock(level).ifPresent(c -> server.clockManager().setRate(c, 1.0f));
			d.fast = false;
			STORE.markDirty();
		}
	}

	// ---------------------------------------------------------------- the clock

	static Optional<Holder<WorldClock>> clock(ServerLevel level) {
		return level.dimensionType().defaultClock();
	}

	/** The clock's total ticks (from what the clients would be told). */
	static long ticks(MinecraftServer server, Holder<WorldClock> clock) {
		ClockNetworkState s = server.clockManager().createFullSyncPacket().clockUpdates().get(clock);
		return s == null ? 0 : s.totalTicks();
	}

	/** When the clock next reaches a marker (without moving it there). */
	static long next(MinecraftServer server, Holder<WorldClock> clock, ResourceKey<ClockTimeMarker> marker) {
		ServerClockManager m = server.clockManager();
		long now = ticks(server, clock);
		m.moveToTimeMarker(clock, marker);
		long then = ticks(server, clock);
		m.setTotalTicks(clock, now);
		return then;
	}

	/** "06:00" for a clock time (tick 0 of a day is six in the morning). */
	static String clockText(long ticks) {
		long t = Math.floorMod(ticks, 24000L);
		long h = (t / 1000 + 6) % 24, m = (t % 1000) * 60 / 1000;
		return String.format(java.util.Locale.ROOT, "%02d:%02d", h, m);
	}

	/** Days (rounded up) until the next nap may be done, 0 if it can now. */
	static int napWaitDays(long now, long lastNap, int days) {
		// The time was set back (an admin): the wait starts over
		if (lastNap > now) return 0;
		long wait = lastNap + days * 24000L - now;
		return wait <= 0 ? 0 : (int) ((wait + 23999) / 24000);
	}

	// ---------------------------------------------------------------- napping

	/** Is this player napping (in bed in the day)? Then the game doesn't wake them up because it's light. */
	static boolean napping(ServerPlayer p) {
		return NAPPING.contains(p.getUUID());
	}

	/** Does time advance here at all (the game rule)? If not, sleeping is left to the game. */
	static boolean timeRuns(ServerLevel level) {
		return level.getGameRules().get(GameRules.ADVANCE_TIME) && clock(level).isPresent();
	}

	/** From the mixin in the world's tick: do we run the time here (so the game doesn't skip the night itself)? */
	static boolean handlesNight(ServerLevel level) {
		return timeRuns(level) && (FAST.get() || level.players().stream().anyMatch(Sleep::napping));
	}

	/**
	 * From the mixin in the player's tick: keep this player in bed even though the game would wake them because it's
	 * light (a nap, or the last bit of a night that runs past dawn).
	 */
	static boolean keepAsleep(net.minecraft.world.entity.player.Player p) {
		if (!(p instanceof ServerPlayer sp) || !sp.isSleeping()) return false;
		return napping(sp) || RUNS.containsKey(sp.level().dimension());
	}

	/** A night's rate: a whole night in the set seconds, a longer stretch (a day thunderstorm) in as many. */
	static float rate(long remaining, int seconds) {
		return Math.max(2f, Math.max(NIGHT, remaining) / (seconds * 20f));
	}

	/** A right-click on a bed. Returns null to let the game handle it. */
	static @Nullable InteractionResult useBed(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof AbstractBedBlock bed) || p.isSleeping()) return null;
		// Sneaking with something in your hand places it, as always
		if (p.isShiftKeyDown() && !p.getMainHandItem().isEmpty()) return null;
		BedRule rule = bed.getBedRule(level, pos);
		// Only "too early": not where beds explode, and not when you can sleep anyway
		if (rule.canSleep(level) || rule.destroyOnUse() || !NAP.get() || !timeRuns(level)) return null;
		// The head of the bed (where you lie)
		BlockPos head = pos;
		BlockState headState = state;
		if (state.hasProperty(BlockStateProperties.BED_PART) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
				&& state.getValue(BlockStateProperties.BED_PART) != BedPart.HEAD) {
			head = pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
			headState = level.getBlockState(head);
			if (!(headState.getBlock() instanceof AbstractBedBlock)) return null;
		}
		MinecraftServer server = level.getServer();
		Holder<WorldClock> clock = clock(level).get();
		int now = server.getTickCount();
		Integer first = FIRST_CLICK.get(p.getUUID());
		boolean second = first != null && now - first <= SECOND_CLICK;
		if (!second) {
			// What the game does on a click in the day (too far, in the way, monsters; the spawn point is set), but
			// instead of "you can only sleep at night" the card says a second click naps
			var tried = p.startSleepInBed(bed, headState, rule, head);
			var problem = tried.left().orElse(null);
			if (tried.right().isPresent()) return InteractionResult.SUCCESS;
			if (problem == net.minecraft.world.entity.player.Player.BedSleepingProblem.TOO_FAR_AWAY
					|| problem == net.minecraft.world.entity.player.Player.BedSleepingProblem.OBSTRUCTED
					|| problem == net.minecraft.world.entity.player.Player.BedSleepingProblem.NOT_SAFE) {
				if (problem.message() != null) p.sendOverlayMessage(problem.message());
				return InteractionResult.SUCCESS;
			}
		}
		long ticks = ticks(server, clock);
		int wait = napWaitDays(ticks, STORE.get().lastNap, NAP_DAYS.get());
		long target = next(server, clock, ClockTimeMarkers.NIGHT) - EVENING_BEFORE_NIGHT;
		Component title = Ui.text("Too early to sleep", ChatFormatting.WHITE);
		if (wait > 0) {
			FIRST_CLICK.remove(p.getUUID());
			card(p, title, Ui.text("You can nap again in " + wait + (wait == 1 ? " day" : " days"), ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		if (target - ticks < SHORTEST_NAP) {
			FIRST_CLICK.remove(p.getUUID());
			card(p, Ui.text("Almost evening", ChatFormatting.WHITE), Ui.text("Wait a little and sleep when it's dark", ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		if (!second) {
			FIRST_CLICK.put(p.getUUID(), now);
			card(p, title, Ui.text("Right-click again to nap until evening", ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		FIRST_CLICK.remove(p.getUUID());
		// Lie down like at night (the game still checks distance, room and monsters), with a rule that allows it now
		BedRule napRule = new BedRule(BedRule.Rule.ALWAYS, rule.canSetSpawn(), false, false, Optional.empty());
		NAPPING.add(p.getUUID());
		var result = p.startSleepInBed(bed, headState, napRule, head);
		result.ifLeft(problem -> {
			NAPPING.remove(p.getUUID());
			if (problem.message() != null) p.sendOverlayMessage(problem.message());
		});
		TopCard.hide(p, NAP_CHANNEL);
		return InteractionResult.SUCCESS;
	}

	private static void card(ServerPlayer p, Component title, Component sub) {
		TopCard.show(p, NAP_CHANNEL, new TopCard.Card(new ItemStack(Ui.item("white_bed")), 0x9AA8FF, title, sub, List.of()), 46, 120);
	}

	// ---------------------------------------------------------------- every tick

	static void tick(MinecraftServer server) {
		int percent = server.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
		NAPPING.removeIf(id -> {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			return p == null || !p.isSleeping();
		});
		for (ServerLevel level : server.getAllLevels()) {
			List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();
			List<ServerPlayer> asleep = players.stream().filter(ServerPlayer::isSleeping).toList();
			int needed = Math.max(1, (int) Math.ceil(players.size() * Math.min(100, percent) / 100.0));
			// Settled in: a second in bed (the game itself waits five, by then the screen is dark)
			long settled = asleep.stream().filter(p -> p.getSleepTimer() >= 20).count();
			boolean enough = !asleep.isEmpty() && asleep.size() >= needed && settled >= needed;
			boolean nap = !asleep.isEmpty() && asleep.stream().allMatch(Sleep::napping);
			Run run = RUNS.get(level.dimension());
			if (timeRuns(level) && (FAST.get() || nap || run != null)) {
				Holder<WorldClock> c = clock(level).get();
				ServerClockManager m = server.clockManager();
				if (run == null && enough) {
					run = new Run();
					run.nap = nap;
					run.from = ticks(server, c);
					run.target = nap ? next(server, c, ClockTimeMarkers.NIGHT) - EVENING_BEFORE_NIGHT
							: next(server, c, ClockTimeMarkers.WAKE_UP_FROM_SLEEP);
					run.rate = rate(run.target - run.from, SECONDS.get());
					if (run.target > run.from) {
						m.setRate(c, run.rate);
						RUNS.put(level.dimension(), run);
						STORE.get().fast = true;
						STORE.markDirty();
					}
				} else if (run != null) {
					long now = ticks(server, c);
					if (!enough) {
						// Someone got up: the time stays where it is
						stop(server, level, c, run, false);
					} else if (now + run.rate >= run.target) {
						m.setTotalTicks(c, run.target);
						stop(server, level, c, run, true);
						for (ServerPlayer p : asleep) p.stopSleepInBed(false, true);
						if (!run.nap && level.getGameRules().get(GameRules.ADVANCE_WEATHER) && level.isRaining()) level.resetWeatherCycle();
					}
				}
			}
			if (server.getTickCount() % 10 == 3) cards(level, players, asleep.size(), needed, RUNS.get(level.dimension()), clock(level).orElse(null), server);
		}
	}

	private static void stop(MinecraftServer server, ServerLevel level, Holder<WorldClock> clock, Run run, boolean done) {
		server.clockManager().setRate(clock, 1.0f);
		RUNS.remove(level.dimension());
		Data d = STORE.get();
		d.fast = !RUNS.isEmpty();
		if (done && run.nap) d.lastNap = run.target;
		STORE.markDirty();
		NAPPING.removeIf(id -> {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			return p == null || p.level() == level;
		});
		if (done) for (ServerPlayer p : level.players()) TopCard.hide(p, CHANNEL);
	}

	/** The card for everybody in a world where someone sleeps. */
	private static void cards(ServerLevel level, List<ServerPlayer> players, int sleeping, int needed, @Nullable Run run,
			@Nullable Holder<WorldClock> clock, MinecraftServer server) {
		for (ServerPlayer p : level.players()) {
			if (sleeping == 0 || (players.size() < 2 && run == null)) {
				TopCard.hide(p, CHANNEL);
				continue;
			}
			TopCard.Card card;
			if (run != null && clock != null) {
				long now = ticks(server, clock);
				double f = Math.max(0, Math.min(1, (now - run.from) / (double) Math.max(1, run.target - run.from)));
				card = new TopCard.Card(new ItemStack(Ui.item(run.nap ? "white_bed" : "red_bed")), 0x9AA8FF,
						Ui.text(run.nap ? "Napping until evening" : "Sleeping until morning", ChatFormatting.WHITE),
						Ui.text(p.isSleeping() ? "Leave the bed to stop here" : sleeping + (sleeping == 1 ? " player sleeps" : " players sleep"),
								ChatFormatting.GRAY),
						List.of(TopCard.Row.bar("time", "Time", f, 0x9AA8FF, 10,
								Ui.text(clockText(now) + " → " + clockText(run.target), ChatFormatting.WHITE))));
			} else {
				boolean enough = sleeping >= needed;
				card = new TopCard.Card(new ItemStack(Ui.item("red_bed")), enough ? 0x55FF55 : 0x9AA8FF,
						Ui.text(enough ? "Good night!" : "Sleeping: " + sleeping + " of " + needed, ChatFormatting.WHITE),
						Ui.text(enough ? (FAST.get() ? "The night is about to pass" : "The night is skipped")
								: (needed - sleeping) + " more and the night passes", ChatFormatting.GRAY),
						List.of(TopCard.Row.bar("time", "Asleep", Math.min(1, sleeping / (double) needed), 0x9AA8FF, 10,
								Ui.text(sleeping + " / " + needed, ChatFormatting.WHITE))));
			}
			TopCard.show(p, CHANNEL, card, 45, 30);
		}
	}

	static void forget(UUID id) {
		FIRST_CLICK.remove(id);
		NAPPING.remove(id);
	}
}
