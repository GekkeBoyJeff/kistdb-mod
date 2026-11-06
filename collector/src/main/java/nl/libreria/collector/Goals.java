package nl.libreria.collector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import nl.libreria.collector.Catalog.Counter;
import nl.libreria.core.Chronicle;
import nl.libreria.core.Domain;
import nl.libreria.core.Hud;
import nl.libreria.core.Players;
import nl.libreria.core.Say;
import nl.libreria.core.Ui;
import nl.libreria.core.WorldStore;
import nl.libreria.core.gui.TopCard;
import org.jetbrains.annotations.Nullable;

/**
 * Server goals: one goal at a time for everybody together ("Mine 15,000 stone"), a week by default. What counts is
 * what each player does while the goal runs, read from Minecraft's own statistics. Reached in time: everyone who did
 * at least 1% gets the goal's achievement, the one who did the most its Champion achievement. Which goal comes next
 * is random, goals never reached before first, never two from the same corner of the game in a row. How big it is:
 * an amount per player for a week, times the players seen in the last two weeks, times the length and the size the
 * admin chose.
 */
public final class Goals {
	/** One goal that can come up. {@code what} has %s for the amount ("Mine %s stone"). */
	public record Kind(String id, String area, String name, String what, Item icon, Counter count, double perPlayer, long divisor,
			boolean hard) {
		/** "Mine 15,000 stone". */
		public String text(long target) {
			return String.format(Locale.ROOT, this.what, Ui.n(target / this.divisor));
		}

		/** Helping reach it: common (chopping logs together is no feat), uncommon for a hard one. */
		public Rarity helped() {
			return this.hard ? Rarity.UNCOMMON : Rarity.COMMON;
		}

		/** Doing the most for it: rare, epic for a hard one. */
		public Rarity champion() {
			return this.hard ? Rarity.EPIC : Rarity.RARE;
		}
	}

	public static final class Running {
		public String kind = "";
		public long started;
		public long ends;
		/** In the statistic's own units (centimetres for distances). */
		public long target;
		/** Each player's statistic when they were first seen during this goal. */
		public Map<UUID, Long> base = new HashMap<>();
		/** What each player did for it. */
		public Map<UUID, Long> done = new HashMap<>();
		/** Quarters announced (1 = 25%…). */
		public int step;
	}

	public static final class Past {
		public String kind = "";
		public long target;
		public long total;
		public long ended;
		public boolean reached;
		public Map<UUID, Long> done = new HashMap<>();
		public @Nullable UUID champion;
	}

	public static final class Data {
		public @Nullable Running now;
		public List<Past> past = new ArrayList<>();
		/** When the next goal may start (a short pause after one ends). */
		public long nextAt;
		public String lastArea = "";
		/** 2: statistics read correctly; a goal from before counts again from now (keeping what was done). */
		public int version;
	}

	private static final int VERSION = 2;

	static final WorldStore<Data> STORE = WorldStore.create("libreria/goals.json", Data.class, Data::new);

	public static final nl.libreria.core.ServerSetting.Toggle ON = CollectorMod.MODULE.serverToggle("goals", "Server goals", Items.BELL, true,
			"One goal for everybody together, like", "\"mine 15,000 stone\". Reached in time:", "achievements for all who helped.");
	public static final nl.libreria.core.ServerSetting.Number DAYS = CollectorMod.MODULE.serverNumber("goal_days", "Server goal length",
			Items.CLOCK, 7, 1, 14, 1, "days", "How long the server has for one goal.", "The amount grows with it.");
	public static final nl.libreria.core.ServerSetting.Number SIZE = CollectorMod.MODULE.serverNumber("goal_size", "Server goal size",
			Items.ANVIL, 100, 25, 300, 25, "%", "100% = a normal week's play per", "player; 50% easier, 200% harder.");
	public static final nl.libreria.core.Setting.Toggle BAR = CollectorMod.MODULE.toggle("goal_bar", "Server goal in the action bar", true,
			Items.BELL, "When you add to the server goal, the", "score shows above your hotbar now", "and then (only when it's free).");
	public static final nl.libreria.core.Setting.Toggle CARDS = CollectorMod.MODULE.toggle("goal_cards", "Server goal cards", true,
			Items.BELL, "A card when a goal starts, gets to", "a quarter, half, three quarters, and", "when it's reached (never over others).");

	/** A player counts as a helper with at least this share of the target. */
	static final double HELPER_SHARE = 0.01;
	private static final long DAY = 24L * 60 * 60 * 1000;
	/** The pause between two goals. */
	private static final long PAUSE = 10L * 60 * 1000;

	private static final Map<String, Kind> KINDS = new LinkedHashMap<>();
	/** When each player last saw the score in their action bar (server ticks). */
	private static final Map<UUID, Integer> SHOWN = new HashMap<>();

	private Goals() {
	}

	public static List<Kind> kinds() {
		return List.copyOf(KINDS.values());
	}

	public static @Nullable Kind kind(String id) {
		return KINDS.get(id);
	}

	static Data data() {
		Data d = STORE.get();
		if (d.past == null) d.past = new ArrayList<>();
		if (d.lastArea == null) d.lastArea = "";
		if (d.now != null) {
			if (d.now.base == null) d.now.base = new HashMap<>();
			if (d.now.done == null) d.now.done = new HashMap<>();
		}
		if (d.version < VERSION) {
			if (d.now != null) d.now.base.clear();
			d.version = VERSION;
			STORE.markDirty();
		}
		return d;
	}

	/** The row on Progress, the line when you come back, /goal. */
	static void init() {
		CollectorMod.MODULE.hubEntry(Domain.PROGRESS, 15, Items.BELL, "Server goal", nl.libreria.collector.gui.GoalWindow::open,
				"One goal for everybody together, like mining 15,000 stone. Reached in time: achievements for all who helped.");
		nl.libreria.core.DomainWindow.value("Server goal", p -> {
			Running r = now();
			return r == null ? "" : percent(r) + "%";
		});
		Chronicle.addAwayLine(p -> awayLine());
		CollectorMod.MODULE.commands(Domain.PROGRESS, "/goal – the server goal: how far, who did the most");
	}

	public static List<Past> past() {
		return List.copyOf(data().past);
	}

	public static @Nullable Running now() {
		Running r = data().now;
		return r != null && KINDS.containsKey(r.kind) ? r : null;
	}

	public static long total(Running r) {
		long n = 0;
		for (long v : r.done.values()) n += v;
		return n;
	}

	// ---------------------------------------------------------------- who helped (for the achievements)

	private static final class Earned {
		final Map<UUID, Set<String>> helped = new HashMap<>();
		final Map<UUID, Set<String>> champion = new HashMap<>();
		final Map<UUID, Integer> helpedCount = new HashMap<>();
		final Map<UUID, Integer> championCount = new HashMap<>();
		final Map<UUID, Set<String>> areas = new HashMap<>();
	}

	private static @Nullable Earned earned;
	private static int earnedFor = -1;
	private static @Nullable Data earnedData;

	private static Earned earned() {
		Data d = data();
		if (earned != null && earnedFor == d.past.size() && earnedData == d) return earned;
		Earned e = new Earned();
		for (Past p : d.past) {
			if (!p.reached) continue;
			Kind k = KINDS.get(p.kind);
			for (Map.Entry<UUID, Long> h : p.done.entrySet()) {
				if (!helper(h.getValue(), p.target)) continue;
				e.helped.computeIfAbsent(h.getKey(), x -> new HashSet<>()).add(p.kind);
				e.helpedCount.merge(h.getKey(), 1, Integer::sum);
				if (k != null) e.areas.computeIfAbsent(h.getKey(), x -> new HashSet<>()).add(k.area);
			}
			if (p.champion != null) {
				// The one who did the most always helped
				if (e.helped.computeIfAbsent(p.champion, x -> new HashSet<>()).add(p.kind)) {
					e.helpedCount.merge(p.champion, 1, Integer::sum);
					if (k != null) e.areas.computeIfAbsent(p.champion, x -> new HashSet<>()).add(k.area);
				}
				e.champion.computeIfAbsent(p.champion, x -> new HashSet<>()).add(p.kind);
				e.championCount.merge(p.champion, 1, Integer::sum);
			}
		}
		earned = e;
		earnedFor = d.past.size();
		earnedData = d;
		return e;
	}

	static boolean helper(long done, long target) {
		return done > 0 && done >= Math.max(1, (long) Math.ceil(target * HELPER_SHARE));
	}

	public static boolean helped(UUID who, String kind) {
		return earned().helped.getOrDefault(who, Set.of()).contains(kind);
	}

	public static boolean champion(UUID who, String kind) {
		return earned().champion.getOrDefault(who, Set.of()).contains(kind);
	}

	public static int helpedCount(UUID who) {
		return earned().helpedCount.getOrDefault(who, 0);
	}

	public static int championCount(UUID who) {
		return earned().championCount.getOrDefault(who, 0);
	}

	public static int areasHelped(UUID who) {
		return earned().areas.getOrDefault(who, Set.of()).size();
	}

	public static Set<String> areas() {
		Set<String> out = new HashSet<>();
		for (Kind k : KINDS.values()) out.add(k.area);
		return out;
	}

	// ---------------------------------------------------------------- running

	/** Every 5 seconds (from the collector's tick). */
	static void tick(MinecraftServer server) {
		if (KINDS.isEmpty()) return;
		Data d = data();
		long now = System.currentTimeMillis();
		if (!ON.get()) return;
		Running r = d.now;
		if (r != null && !KINDS.containsKey(r.kind)) {
			// That goal is gone (a module left): start over
			d.now = null;
			r = null;
		}
		if (r == null) {
			if (now >= d.nextAt) start(server, d, now);
			return;
		}
		Kind k = KINDS.get(r.kind);
		Catalog.fresh();
		int tick = server.getTickCount();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p instanceof net.fabricmc.fabric.api.entity.FakePlayer) continue;
			long value = k.count().get(p, CollectorMod.STORE.get(p));
			long had = r.done.getOrDefault(p.getUUID(), 0L);
			Long base = r.base.get(p.getUUID());
			if (base == null || p.isCreative() || p.isSpectator()) {
				// First seen during this goal, or in creative: only what comes after counts
				r.base.put(p.getUUID(), value - had);
				continue;
			}
			long done = Math.max(had, value - base);
			if (done > had) {
				r.done.put(p.getUUID(), done);
				STORE.markDirty();
				score(p, r, k, done, tick);
			}
		}
		long total = total(r);
		int step = (int) Math.min(3, total * 4 / Math.max(1, r.target));
		if (total >= r.target) {
			end(server, d, true, now);
		} else if (now >= r.ends) {
			end(server, d, false, now);
		} else if (step > r.step) {
			r.step = step;
			STORE.markDirty();
			String pct = (step * 25) + "%";
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				card(p, "Server goal: " + pct, k.name() + " · " + Ui.n(total / k.divisor()) + " / " + Ui.n(r.target / k.divisor()), k);
			}
		}
	}

	/** Now and then in the action bar: where the goal stands and what you did (only when nothing else is there). */
	private static void score(ServerPlayer p, Running r, Kind k, long mine, int tick) {
		if (!BAR.get(p) || !Hud.idle(p)) return;
		Integer last = SHOWN.get(p.getUUID());
		if (last != null && tick - last < 20 * 20) return;
		SHOWN.put(p.getUUID(), tick);
		long total = total(r);
		Component text = Component.empty().append(Ui.text(k.name() + "  ", ChatFormatting.GOLD))
				.append(Ui.text(Ui.n(total / k.divisor()) + " / " + Ui.n(r.target / k.divisor()), ChatFormatting.WHITE))
				.append(Ui.text("  · you " + Ui.n(mine / k.divisor()), ChatFormatting.GRAY));
		Hud.show(p, "goal", text, 1, 50);
	}

	private static void card(ServerPlayer p, String title, String sub, Kind k) {
		if (!CARDS.get(p)) return;
		TopCard.whenFree(p, "goal", new TopCard.Card(new ItemStack(k.icon()), Domain.PROGRESS.rgb, Ui.text(title, Domain.PROGRESS.rgb),
				Ui.text(sub, ChatFormatting.GRAY), List.of()), 120, 60 * 20);
	}

	static void start(MinecraftServer server, Data d, long now) {
		Kind k = pick(d);
		if (k == null) return;
		Running r = new Running();
		r.kind = k.id();
		r.started = now;
		int days = Math.max(1, DAYS.get());
		r.ends = now + days * DAY;
		r.target = target(k, Players.activeWithin(server, 14 * DAY), days, SIZE.get());
		Catalog.fresh();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p instanceof net.fabricmc.fabric.api.entity.FakePlayer) continue;
			r.base.put(p.getUUID(), k.count().get(p, CollectorMod.STORE.get(p)));
		}
		d.now = r;
		d.lastArea = k.area();
		STORE.markDirty();
		String text = k.text(r.target) + ", together";
		Chronicle.write(null, "", Chronicle.Kind.WORLD, "A new server goal began: " + k.name() + " (" + lower(k.text(r.target)) + ")", false);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			card(p, "New server goal: " + k.name(), text + " · " + days + (days == 1 ? " day" : " days"), k);
			p.sendSystemMessage(Say.line(Domain.PROGRESS).append(Ui.text("New server goal: ", ChatFormatting.GRAY))
					.append(Ui.text(k.name(), ChatFormatting.GOLD)).append(Ui.text(" – " + text + ". ", ChatFormatting.GRAY))
					.append(Component.literal("[See]").withStyle(s -> s.withColor(ChatFormatting.AQUA)
							.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/goal")))));
		}
	}

	/** Random; never the same corner twice in a row; goals never reached before come first. */
	static @Nullable Kind pick(Data d) {
		Set<String> reached = new HashSet<>();
		for (Past p : d.past) if (p.reached) reached.add(p.kind);
		String lastKind = d.past.isEmpty() ? "" : d.past.getLast().kind;
		List<Kind> fresh = new ArrayList<>(), any = new ArrayList<>();
		for (Kind k : KINDS.values()) {
			if (k.area().equals(d.lastArea) || k.id().equals(lastKind)) continue;
			any.add(k);
			if (!reached.contains(k.id())) fresh.add(k);
		}
		List<Kind> from = !fresh.isEmpty() ? fresh : !any.isEmpty() ? any : new ArrayList<>(KINDS.values());
		if (from.isEmpty()) return null;
		return from.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(from.size()));
	}

	/** Per player per week × players × days/7 × size%, rounded to a friendly number, in the statistic's units. */
	static long target(Kind k, int players, int days, int sizePercent) {
		double amount = k.perPlayer() * Math.max(1, players) * days / 7.0 * sizePercent / 100.0;
		return nice(amount) * k.divisor();
	}

	/** 1–9 as they are, then 5s, 10s, 50s, 100s, 500s… */
	static long nice(double x) {
		if (x <= 1) return 1;
		long step = x < 10 ? 1 : x < 100 ? 5 : x < 1000 ? 10 : x < 5000 ? 50 : x < 20000 ? 100 : x < 100000 ? 500 : 1000;
		return Math.max(1, Math.round(x / step) * step);
	}

	static void end(MinecraftServer server, Data d, boolean reached, long now) {
		Running r = d.now;
		if (r == null) return;
		Kind k = KINDS.get(r.kind);
		Past p = new Past();
		p.kind = r.kind;
		p.target = r.target;
		p.total = total(r);
		p.ended = now;
		p.reached = reached;
		p.done = new HashMap<>(r.done);
		if (reached) {
			long best = 0;
			for (Map.Entry<UUID, Long> e : r.done.entrySet()) {
				if (e.getValue() > best) {
					best = e.getValue();
					p.champion = e.getKey();
				}
			}
		}
		d.past.add(p);
		d.now = null;
		d.nextAt = now + PAUSE;
		STORE.markDirty();
		if (k == null) return;
		String name = p.champion == null ? null : name(server, p.champion);
		if (reached) {
			Chronicle.write(null, "", Chronicle.Kind.WORLD, "Together the server reached " + k.name() + " (" + lower(k.text(p.target)) + ")"
					+ (name == null ? "" : "; " + name + " did the most"), true);
		} else {
			Chronicle.write(null, "", Chronicle.Kind.WORLD, "The server goal " + k.name() + " ran out at "
					+ (p.total * 100 / Math.max(1, p.target)) + "%", false);
		}
		for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
			if (reached) card(pl, "Server goal reached!", k.name() + (name == null ? "" : " · most: " + name), k);
			else card(pl, "Server goal ran out", k.name() + " · " + (p.total * 100 / Math.max(1, p.target)) + "%", k);
			// The achievements right away for who's here
			CollectorMod.check(pl);
		}
	}

	private static String lower(String s) {
		return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
	}

	static @Nullable String name(MinecraftServer server, UUID id) {
		Players.Known k = Players.get(server, id);
		return k == null ? null : k.label();
	}

	/** "Stone Age: 24%", for the card when you come back. */
	static @Nullable Component awayLine() {
		Running r = now();
		if (r == null) return null;
		Kind k = KINDS.get(r.kind);
		return Component.empty().append(Ui.text("Server goal ", ChatFormatting.GRAY)).append(Ui.text(k.name(), ChatFormatting.GOLD))
				.append(Ui.text(": " + percent(r) + "%", ChatFormatting.GRAY));
	}

	public static int percent(Running r) {
		return (int) Math.min(100, total(r) * 100 / Math.max(1, r.target));
	}

	/** Ends the running goal now (for the screenshots and tests). */
	public static void endNow(MinecraftServer server, boolean reached) {
		end(server, data(), reached, System.currentTimeMillis());
	}

	/** For the self-test and the screenshots: start a goal of this kind now. */
	public static Running startNow(MinecraftServer server, String kind) {
		Data d = data();
		d.now = null;
		String area = d.lastArea;
		Kind k = KINDS.get(kind);
		d.lastArea = "";
		Map<String, Kind> only = new LinkedHashMap<>(KINDS);
		KINDS.clear();
		KINDS.put(kind, k);
		start(server, d, System.currentTimeMillis());
		KINDS.clear();
		KINDS.putAll(only);
		if (d.now == null) d.lastArea = area;
		return d.now;
	}

	// ---------------------------------------------------------------- the goals

	/** All goals that can come up (built with the catalogue, once tags are loaded). */
	static void build() {
		KINDS.clear();
		List<Block> logs = new ArrayList<>(), leaves = new ArrayList<>(), everything = new ArrayList<>(), ores = new ArrayList<>();
		for (Block b : BuiltInRegistries.BLOCK) {
			everything.add(b);
			if (b.defaultBlockState().is(BlockTags.LOGS)) logs.add(b);
			if (b.defaultBlockState().is(BlockTags.LEAVES)) leaves.add(b);
			String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
			if (id.endsWith("_ore") || id.equals("ancient_debris")) ores.add(b);
		}
		List<Block> terracotta = new ArrayList<>();
		for (Block b : everything) {
			String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
			if (id.endsWith("terracotta") && !id.endsWith("glazed_terracotta")) terracotta.add(b);
		}
		long km = 100_000, m = 100;
		String mine = "mine", fight = "fight", farm = "farm", travel = "travel", make = "make", build = "build", life = "life";

		// Mining
		k(mine, "stone", "Stone Age", "Mine %s stone", Items.COBBLESTONE, Catalog.mined("stone", "deepslate", "cobblestone", "cobbled_deepslate",
				"granite", "diorite", "andesite", "tuff", "calcite", "dripstone_block", "smooth_basalt", "mossy_cobblestone"), 2500, 1, false);
		k(mine, "deepslate", "Deep Dive", "Mine %s deepslate", Items.COBBLED_DEEPSLATE, Catalog.mined("deepslate", "cobbled_deepslate"), 800, 1, false);
		k(mine, "earth", "Earthworks", "Dig %s dirt, sand and gravel", Items.DIRT, Catalog.mined("dirt", "grass_block", "coarse_dirt",
				"rooted_dirt", "podzol", "mycelium", "sand", "red_sand", "gravel", "clay", "mud", "soul_sand", "soul_soil"), 1200, 1, false);
		k(mine, "all", "Dig Deep", "Mine %s blocks of any kind", Items.IRON_PICKAXE, Catalog.mined(everything), 6000, 1, false);
		k(mine, "ores", "Ore Rush", "Mine %s ore of any kind", Items.RAW_IRON, Catalog.mined(ores), 300, 1, false);
		k(mine, "coal", "Coal Rush", "Mine %s coal ore", Items.COAL, Catalog.mined("coal_ore", "deepslate_coal_ore"), 100, 1, false);
		k(mine, "iron", "Iron Age", "Mine %s iron ore", Items.RAW_IRON, Catalog.mined("iron_ore", "deepslate_iron_ore"), 60, 1, false);
		k(mine, "copper", "Copper Craze", "Mine %s copper ore", Items.RAW_COPPER, Catalog.mined("copper_ore", "deepslate_copper_ore"), 70, 1, false);
		k(mine, "gold", "Gold Fever", "Mine %s gold ore", Items.RAW_GOLD, Catalog.mined("gold_ore", "deepslate_gold_ore", "nether_gold_ore"), 30, 1, false);
		k(mine, "redstone", "Seeing Red", "Mine %s redstone ore", Items.REDSTONE, Catalog.mined("redstone_ore", "deepslate_redstone_ore"), 40, 1, false);
		k(mine, "lapis", "Feeling Blue", "Mine %s lapis ore", Items.LAPIS_LAZULI, Catalog.mined("lapis_ore", "deepslate_lapis_ore"), 20, 1, false);
		k(mine, "diamond", "Diamond Fever", "Mine %s diamond ore", Items.DIAMOND, Catalog.mined("diamond_ore", "deepslate_diamond_ore"), 8, 1, true);
		k(mine, "emerald", "Emerald Hunt", "Mine %s emerald ore", Items.EMERALD, Catalog.mined("emerald_ore", "deepslate_emerald_ore"), 3, 1, true);
		k(mine, "quartz", "Quartz Quarry", "Mine %s nether quartz ore", Items.QUARTZ, Catalog.mined("nether_quartz_ore"), 120, 1, false);
		k(mine, "debris", "Debris Delvers", "Mine %s ancient debris", Items.ANCIENT_DEBRIS, Catalog.mined("ancient_debris"), 4, 1, true);
		k(mine, "netherrack", "Nether Tunnels", "Mine %s netherrack", Items.NETHERRACK, Catalog.mined("netherrack"), 1500, 1, false);
		k(mine, "basalt", "Basalt Breakers", "Mine %s blackstone, basalt and magma", Items.BASALT, Catalog.mined("blackstone", "basalt",
				"magma_block"), 400, 1, false);
		k(mine, "endstone", "Void Quarry", "Mine %s end stone", Items.END_STONE, Catalog.mined("end_stone"), 400, 1, true);
		k(mine, "obsidian", "Obsidian Rush", "Mine %s obsidian", Items.OBSIDIAN, Catalog.mined("obsidian", "crying_obsidian"), 20, 1, true);
		k(mine, "amethyst", "Crystal Clear", "Break %s amethyst clusters", Items.AMETHYST_SHARD, Catalog.mined("amethyst_cluster"), 15, 1, false);
		k(mine, "ice", "Cold Snap", "Mine %s ice", Items.PACKED_ICE, Catalog.mined("ice", "packed_ice", "blue_ice"), 64, 1, false);
		k(mine, "terracotta", "Badlands Dig", "Mine %s terracotta", Items.TERRACOTTA, Catalog.mined(terracotta), 150, 1, false);
		k(mine, "glowstone", "Glow Up", "Mine %s glowstone", Items.GLOWSTONE, Catalog.mined("glowstone"), 64, 1, false);
		k(mine, "logs", "Lumber Week", "Chop %s logs", Items.IRON_AXE, Catalog.mined(logs), 400, 1, false);
		k(mine, "leaves", "Leaf It Out", "Clear %s leaves", Items.OAK_LEAVES, Catalog.mined(leaves), 500, 1, false);
		k(mine, "sculk", "Silent Steps", "Mine %s sculk", Items.SCULK, Catalog.mined("sculk", "sculk_vein", "sculk_catalyst", "sculk_sensor",
				"sculk_shrieker", "calibrated_sculk_sensor"), 64, 1, true);
		k(mine, "sand", "Sandstorm", "Dig %s sand", Items.SAND, Catalog.mined("sand", "red_sand", "suspicious_sand"), 400, 1, false);
		k(mine, "gravel", "Gravel Pit", "Dig %s gravel", Items.GRAVEL, Catalog.mined("gravel", "suspicious_gravel"), 300, 1, false);

		// Fighting
		k(fight, "monsters", "Monster Sweep", "Defeat %s monsters", Items.IRON_SWORD, Catalog.killedCategory(MobCategory.MONSTER), 250, 1, false);
		k(fight, "zombies", "Undead Cleanup", "Defeat %s zombies, husks and drowned", Items.ROTTEN_FLESH, Catalog.killed("zombie",
				"zombie_villager", "husk", "drowned"), 50, 1, false);
		k(fight, "skeletons", "Bone Zone", "Defeat %s skeletons, strays and bogged", Items.BONE, Catalog.killed("skeleton", "stray",
				"bogged", "parched"), 50, 1, false);
		k(fight, "creepers", "Creeper Patrol", "Defeat %s creepers", Items.GUNPOWDER, Catalog.killed("creeper"), 20, 1, false);
		k(fight, "spiders", "Arachnophobia", "Defeat %s spiders", Items.STRING, Catalog.killed("spider", "cave_spider"), 30, 1, false);
		k(fight, "endermen", "Ender Hunt", "Defeat %s endermen", Items.ENDER_PEARL, Catalog.killed("enderman"), 15, 1, false);
		k(fight, "witches", "Witch Hunt", "Defeat %s witches", Items.GLASS_BOTTLE, Catalog.killed("witch"), 6, 1, false);
		k(fight, "slimes", "Slime Time", "Defeat %s slimes and magma cubes", Items.SLIME_BALL, Catalog.killed("slime", "magma_cube"), 30, 1, false);
		k(fight, "blazes", "Blaze of Glory", "Defeat %s blazes", Items.BLAZE_ROD, Catalog.killed("blaze"), 25, 1, true);
		k(fight, "witherskeletons", "Skull Hunters", "Defeat %s wither skeletons", Items.WITHER_SKELETON_SKULL, Catalog.killed("wither_skeleton"),
				12, 1, true);
		k(fight, "ghasts", "Ghast Hunt", "Defeat %s ghasts", Items.GHAST_TEAR, Catalog.killed("ghast"), 4, 1, true);
		k(fight, "guardians", "Ocean Guard", "Defeat %s guardians", Items.PRISMARINE_SHARD, Catalog.killed("guardian", "elder_guardian"), 15, 1, true);
		k(fight, "phantoms", "Night Shift", "Defeat %s phantoms", Items.PHANTOM_MEMBRANE, Catalog.killed("phantom"), 6, 1, false);
		k(fight, "illagers", "Illager Purge", "Defeat %s illagers", Items.CROSSBOW, Catalog.killed("pillager", "vindicator", "evoker",
				"illusioner", "ravager"), 12, 1, true);
		k(fight, "piglins", "Pigstep", "Defeat %s piglins", Items.GOLD_NUGGET, Catalog.killed("piglin", "piglin_brute", "zombified_piglin"), 25, 1, false);
		k(fight, "hoglins", "Boar War", "Defeat %s hoglins and zoglins", Items.COOKED_PORKCHOP, Catalog.killed("hoglin", "zoglin"), 10, 1, true);
		k(fight, "pests", "Bug Hunt", "Defeat %s silverfish and endermites", Items.STONE_BRICKS, Catalog.killed("silverfish", "endermite"), 20, 1, false);
		k(fight, "breezes", "Breeze Blockers", "Defeat %s breezes", Items.BREEZE_ROD, Catalog.killed("breeze"), 6, 1, true);
		k(fight, "shulkers", "Shell Game", "Defeat %s shulkers", Items.SHULKER_SHELL, Catalog.killed("shulker"), 10, 1, true);
		k(fight, "damage", "Heavy Hitters", "Deal %s hearts of damage", Items.NETHERITE_SWORD, Catalog.custom("damage_dealt"), 1500, 20, false);
		k(fight, "raids", "Village Defenders", "Win %s raids", Items.BELL, Catalog.custom("raid_win"), 0.5, 1, true);
		k(fight, "drowned", "Deep Trouble", "Defeat %s drowned", Items.TRIDENT, Catalog.killed("drowned"), 20, 1, false);
		k(fight, "shield", "Shield Wall", "Block %s hearts with a shield", Items.SHIELD, Catalog.custom("damage_blocked_by_shield"), 60, 20, false);

		// Farming and animals
		k(farm, "crops", "Harvest Festival", "Gather %s crops", Items.WHEAT, Catalog.gathered(Catalog.itemsOf("wheat", "carrot", "potato",
				"beetroot", "nether_wart", "cocoa_beans", "sweet_berries", "glow_berries", "melon_slice", "pumpkin", "sugar_cane")), 800, 1, false);
		k(farm, "wheat", "Golden Fields", "Gather %s wheat", Items.WHEAT, Catalog.gathered(Catalog.itemsOf("wheat")), 300, 1, false);
		k(farm, "roots", "Spud Squad", "Gather %s potatoes and carrots", Items.POTATO, Catalog.gathered(Catalog.itemsOf("potato", "carrot")), 300, 1, false);
		k(farm, "cane", "Sweet Cane", "Gather %s sugar cane", Items.SUGAR_CANE, Catalog.gathered(Catalog.itemsOf("sugar_cane")), 200, 1, false);
		k(farm, "melons", "Pumpkin Patch", "Gather %s melon slices and pumpkins", Items.PUMPKIN, Catalog.gathered(Catalog.itemsOf("melon_slice",
				"pumpkin")), 200, 1, false);
		k(farm, "wart", "Wart Hogs", "Gather %s nether wart", Items.NETHER_WART, Catalog.gathered(Catalog.itemsOf("nether_wart")), 150, 1, false);
		k(farm, "breed", "Spring Fever", "Breed %s animals", Items.WHEAT_SEEDS, Catalog.custom("animals_bred"), 40, 1, false);
		k(farm, "fish", "Fishing Contest", "Reel something in %s times", Items.FISHING_ROD, Catalog.custom("fish_caught"), 25, 1, false);
		k(farm, "trade", "Market Week", "Trade with villagers %s times", Items.EMERALD, Catalog.custom("traded_with_villager"), 30, 1, false);
		k(farm, "shear", "Shearing Season", "Use shears %s times", Items.SHEARS, Catalog.used(Catalog.itemsOf("shears")), 40, 1, false);
		k(farm, "bonemeal", "Grow Wild", "Use %s bone meal", Items.BONE_MEAL, Catalog.used(Catalog.itemsOf("bone_meal")), 100, 1, false);
		k(farm, "saplings", "Plant a Forest", "Plant %s saplings", Items.OAK_SAPLING, Catalog.used(Catalog.itemsWhere(id -> Catalog.is(id,
				ItemTags.SAPLINGS))), 50, 1, false);
		k(farm, "eggs", "Easter Hunt", "Gather %s eggs", Items.EGG, Catalog.gathered(Catalog.itemsOf("egg", "blue_egg", "brown_egg")), 64, 1, false);
		k(farm, "wool", "Woolly Week", "Gather %s wool", Ui.item("white_wool"), Catalog.gathered(Catalog.itemsWhere(id -> id.endsWith("_wool"))), 64, 1, false);
		k(farm, "honey", "Busy Bees", "Gather %s honeycomb", Items.HONEYCOMB, Catalog.gathered(Catalog.itemsOf("honeycomb")), 20, 1, false);
		k(farm, "cocoa", "Chocolate Factory", "Gather %s cocoa beans", Items.COCOA_BEANS, Catalog.gathered(Catalog.itemsOf("cocoa_beans")), 64, 1, false);
		k(farm, "apples", "An Apple a Day", "Gather %s apples", Items.APPLE, Catalog.gathered(Catalog.itemsOf("apple")), 15, 1, false);
		k(farm, "leather", "Tannery", "Gather %s leather", Items.LEATHER, Catalog.gathered(Catalog.itemsOf("leather")), 30, 1, false);
		k(farm, "talk", "Village Chatter", "Talk to villagers %s times", Items.BOOK, Catalog.custom("talked_to_villager"), 40, 1, false);
		k(farm, "kelp", "Kelp Harvest", "Gather %s kelp", Items.KELP, Catalog.gathered(Catalog.itemsOf("kelp")), 200, 1, false);
		k(farm, "bamboo", "Bamboo Grove", "Gather %s bamboo", Items.BAMBOO, Catalog.gathered(Catalog.itemsOf("bamboo")), 200, 1, false);
		k(farm, "berries", "Berry Picking", "Gather %s sweet and glow berries", Items.SWEET_BERRIES, Catalog.gathered(Catalog.itemsOf("sweet_berries",
				"glow_berries")), 150, 1, false);

		// Travel
		k(travel, "walk", "Long March", "Walk %s km", Items.LEATHER_BOOTS, Catalog.custom("walk_one_cm", "sprint_one_cm"), 30, km, false);
		k(travel, "sprint", "Speed Week", "Sprint %s km", Items.SUGAR, Catalog.custom("sprint_one_cm"), 10, km, false);
		k(travel, "swim", "Big Swim", "Swim %s km", Items.TURTLE_HELMET, Catalog.custom("swim_one_cm"), 2, km, false);
		k(travel, "boat", "Regatta", "Row %s km by boat", Items.OAK_BOAT, Catalog.custom("boat_one_cm"), 8, km, false);
		k(travel, "horse", "Ride Out", "Ride %s km on horseback", Items.SADDLE, Catalog.custom("horse_one_cm"), 8, km, false);
		k(travel, "elytra", "Air Show", "Fly %s km with an elytra", Items.ELYTRA, Catalog.custom("aviate_one_cm"), 30, km, true);
		k(travel, "minecart", "Rail Days", "Ride %s km by minecart", Items.MINECART, Catalog.custom("minecart_one_cm"), 4, km, false);
		k(travel, "climb", "Up and Up", "Climb %s m", Items.LADDER, Catalog.custom("climb_one_cm"), 200, m, false);
		k(travel, "sneak", "Stealth Mission", "Sneak %s m", Items.LEATHER_LEGGINGS, Catalog.custom("crouch_one_cm"), 300, m, false);
		k(travel, "fall", "Free Fall", "Fall %s m in total", Items.FEATHER, Catalog.custom("fall_one_cm"), 300, m, false);
		k(travel, "strider", "Lava Cruise", "Ride a strider %s m", Items.WARPED_FUNGUS_ON_A_STICK, Catalog.custom("strider_one_cm"), 100, m, true);
		k(travel, "ghast", "Cloud Nine", "Fly %s km on a happy ghast", Ui.item("white_wool"), Catalog.custom("happy_ghast_one_cm"), 3, km, true);
		k(travel, "underwater", "Seabed Stroll", "Walk %s m under water", Items.PRISMARINE, Catalog.custom("walk_under_water_one_cm"), 200, m, false);
		k(travel, "onwater", "Walk on Water", "Walk %s m on water", Items.LILY_PAD, Catalog.custom("walk_on_water_one_cm"), 300, m, false);
		k(travel, "pig", "Pig Race", "Ride a pig %s m", Items.CARROT_ON_A_STICK, Catalog.custom("pig_one_cm"), 50, m, true);
		k(travel, "jump", "Hop to It", "Jump %s times", Items.RABBIT_FOOT, Catalog.custom("jump"), 2500, 1, false);

		// Making
		List<Item> every = Catalog.itemsWhere(id -> true);
		k(make, "craft", "Workshop Week", "Craft %s items", Items.CRAFTING_TABLE, Catalog.crafted(every), 1200, 1, false);
		k(make, "torches", "Light the Way", "Craft %s torches", Items.TORCH, Catalog.crafted(Catalog.itemsOf("torch", "soul_torch", "copper_torch")),
				200, 1, false);
		k(make, "bread", "Bake Sale", "Bake %s bread", Items.BREAD, Catalog.crafted(Catalog.itemsOf("bread")), 40, 1, false);
		k(make, "cake", "Let Them Eat Cake", "Bake %s cakes", Items.CAKE, Catalog.crafted(Catalog.itemsOf("cake")), 3, 1, false);
		k(make, "cookies", "Cookie Jar", "Bake %s cookies", Items.COOKIE, Catalog.crafted(Catalog.itemsOf("cookie")), 64, 1, false);
		k(make, "iron", "Smeltdown", "Make %s iron ingots", Items.IRON_INGOT, Catalog.crafted(Catalog.itemsOf("iron_ingot")), 64, 1, false);
		k(make, "gold", "Gold Standard", "Make %s gold ingots", Items.GOLD_INGOT, Catalog.crafted(Catalog.itemsOf("gold_ingot")), 32, 1, false);
		k(make, "copper", "Copper Works", "Make %s copper ingots", Items.COPPER_INGOT, Catalog.crafted(Catalog.itemsOf("copper_ingot")), 64, 1, false);
		k(make, "glass", "Glassworks", "Make %s glass", Items.GLASS, Catalog.crafted(Catalog.itemsOf("glass")), 128, 1, false);
		k(make, "bricks", "Brick by Brick", "Make %s bricks", Items.BRICK, Catalog.crafted(Catalog.itemsOf("brick")), 128, 1, false);
		k(make, "arrows", "Quiver Full", "Craft %s arrows", Items.ARROW, Catalog.crafted(Catalog.itemsOf("arrow")), 128, 1, false);
		k(make, "fireworks", "Fireworks Night", "Craft %s firework rockets", Items.FIREWORK_ROCKET, Catalog.crafted(Catalog.itemsOf("firework_rocket")),
				64, 1, false);
		k(make, "rails", "Railroad Works", "Craft %s rails", Items.RAIL, Catalog.crafted(Catalog.itemsOf("rail", "powered_rail", "detector_rail",
				"activator_rail")), 64, 1, false);
		k(make, "enchant", "Enchanted Evening", "Enchant %s items", Items.ENCHANTING_TABLE, Catalog.custom("enchant_item"), 6, 1, false);
		k(make, "brew", "Potion Party", "Use a brewing stand %s times", Items.BREWING_STAND, Catalog.custom("interact_with_brewingstand"), 15, 1, false);
		k(make, "anvil", "Anvil Chorus", "Use an anvil %s times", Items.ANVIL, Catalog.custom("interact_with_anvil"), 10, 1, false);
		k(make, "smith", "Upgrade Week", "Use a smithing table %s times", Items.SMITHING_TABLE, Catalog.custom("interact_with_smithing_table"), 4, 1,
				true);
		k(make, "stonecutter", "Masonry", "Use a stonecutter %s times", Items.STONECUTTER, Catalog.custom("interact_with_stonecutter"), 20, 1, false);
		k(make, "shelves", "Library Week", "Craft %s bookshelves", Items.BOOKSHELF, Catalog.crafted(Catalog.itemsOf("bookshelf")), 8, 1, false);
		k(make, "paper", "Paperwork", "Craft %s paper", Items.PAPER, Catalog.crafted(Catalog.itemsOf("paper")), 64, 1, false);
		k(make, "concrete", "Concrete Jungle", "Craft %s concrete powder", Ui.item("gray_concrete_powder"), Catalog.crafted(Catalog.itemsWhere(id ->
				id.endsWith("_concrete_powder"))), 128, 1, false);
		k(make, "stairs", "Stairway", "Craft %s stairs", Items.OAK_STAIRS, Catalog.crafted(Catalog.itemsWhere(id -> id.endsWith("_stairs"))), 64, 1,
				false);
		k(make, "storage", "Storage Week", "Craft %s chests and barrels", Items.CHEST, Catalog.crafted(Catalog.itemsOf("chest", "barrel")), 16, 1,
				false);
		k(make, "dyes", "Paint the Town", "Craft %s dyes", Ui.item("red_dye"), Catalog.crafted(Catalog.itemsWhere(id -> id.endsWith("_dye"))), 64, 1, false);
		k(make, "furnace", "Stoke the Fires", "Use a furnace, smoker or blast furnace %s times", Items.FURNACE, Catalog.custom(
				"interact_with_furnace", "interact_with_smoker", "interact_with_blast_furnace"), 40, 1, false);

		// Building
		k(build, "place", "Builders' Week", "Place %s blocks", Items.BRICKS, Catalog.used(Catalog.itemsWhere(id -> Catalog.item(id) instanceof BlockItem)),
				2500, 1, false);
		k(build, "torches", "Lights On", "Place %s torches", Items.TORCH, Catalog.used(Catalog.itemsOf("torch", "soul_torch", "copper_torch")), 150, 1,
				false);
		k(build, "redstone", "Wired Up", "Lay %s redstone dust", Items.REDSTONE, Catalog.used(Catalog.itemsOf("redstone")), 100, 1, false);
		k(build, "rails", "Track Week", "Lay %s rails", Items.RAIL, Catalog.used(Catalog.itemsOf("rail", "powered_rail", "detector_rail",
				"activator_rail")), 64, 1, false);
		k(build, "glass", "Glass House", "Place %s glass", Items.GLASS, Catalog.used(Catalog.itemsWhere(id -> id.endsWith("glass")
				|| id.endsWith("glass_pane"))), 200, 1, false);
		k(build, "wool", "Soft Touch", "Place %s wool and carpet", Ui.item("red_wool"), Catalog.used(Catalog.itemsWhere(id -> id.endsWith("_wool")
				|| id.endsWith("_carpet"))), 150, 1, false);
		k(build, "concrete", "Concrete Plans", "Place %s concrete", Ui.item("white_concrete"), Catalog.used(Catalog.itemsWhere(id ->
				id.endsWith("_concrete"))), 300, 1, false);
		k(build, "planks", "Woodwork", "Place %s planks", Items.OAK_PLANKS, Catalog.used(Catalog.itemsWhere(id -> id.endsWith("_planks"))), 400, 1,
				false);
		k(build, "stonebricks", "Stone Masons", "Place %s stone bricks", Items.STONE_BRICKS, Catalog.used(Catalog.itemsWhere(id ->
				id.endsWith("stone_bricks") || id.endsWith("deepslate_bricks") || id.equals("bricks"))), 300, 1, false);
		k(build, "flowers", "Flower Power", "Plant %s flowers", Items.POPPY, Catalog.used(Catalog.itemsWhere(id -> Catalog.is(id, Catalog.tag("flowers")))),
				40, 1, false);
		k(build, "lanterns", "Lantern Festival", "Place %s lanterns", Items.LANTERN, Catalog.used(Catalog.itemsWhere(id -> id.endsWith("lantern"))),
				32, 1, false);

		// Life
		k(life, "eat", "Feast", "Eat %s times", Items.COOKED_BEEF, Catalog.used(Catalog.itemsWhere(id -> Catalog.has(id,
				net.minecraft.core.component.DataComponents.FOOD))), 120, 1, false);
		k(life, "cake", "Cake Party", "Eat %s slices of cake", Items.CAKE, Catalog.custom("eat_cake_slice"), 14, 1, false);
		k(life, "sleep", "Good Night", "Sleep in a bed %s times", Ui.item("red_bed"), Catalog.custom("sleep_in_bed"), 5, 1, false);
		k(life, "notes", "Concert Week", "Play %s notes on a note block", Items.NOTE_BLOCK, Catalog.custom("play_noteblock"), 150, 1, false);
		k(life, "records", "Disco Night", "Play %s records", Items.JUKEBOX, Catalog.custom("play_record"), 5, 1, false);
		k(life, "bells", "Bell Week", "Ring a bell %s times", Items.BELL, Catalog.custom("bell_ring"), 20, 1, false);
		k(life, "pots", "Garden Party", "Pot %s plants", Items.FLOWER_POT, Catalog.custom("pot_flower"), 8, 1, false);
		k(life, "chests", "Open House", "Open %s chests", Items.CHEST, Catalog.custom("open_chest"), 150, 1, false);
		k(life, "targets", "Bullseye", "Hit a target block %s times", Items.TARGET, Catalog.custom("target_hit"), 20, 1, false);
		k(life, "hours", "Marathon", "Play %s hours", Items.CLOCK, Catalog.custom("play_time"), 10, 72000, false);
		k(life, "hurt", "Tough Crowd", "Take %s hearts of damage", Items.IRON_CHESTPLATE, Catalog.custom("damage_taken"), 300, 20, false);
		k(life, "snowballs", "Snowball Fight", "Throw %s snowballs", Items.SNOWBALL, Catalog.used(Catalog.itemsOf("snowball")), 64, 1, false);
		k(life, "pearls", "Teleport Party", "Throw %s ender pearls", Items.ENDER_PEARL, Catalog.used(Catalog.itemsOf("ender_pearl")), 16, 1, false);
		k(life, "eggtoss", "Egg Toss", "Throw %s eggs", Items.EGG, Catalog.used(Catalog.itemsOf("egg", "blue_egg", "brown_egg")), 32, 1, false);
		k(life, "rockets", "Light the Sky", "Launch %s firework rockets", Items.FIREWORK_ROCKET, Catalog.used(Catalog.itemsOf("firework_rocket")), 64, 1,
				false);
		k(life, "bow", "Archery Contest", "Shoot a bow %s times", Items.BOW, Catalog.used(Catalog.itemsOf("bow")), 100, 1, false);
		k(life, "crossbow", "Bolts Away", "Shoot a crossbow %s times", Items.CROSSBOW, Catalog.used(Catalog.itemsOf("crossbow")), 40, 1, false);
		k(life, "milk", "Milk Run", "Drink %s buckets of milk", Items.MILK_BUCKET, Catalog.used(Catalog.itemsOf("milk_bucket")), 8, 1, false);
		k(life, "potions", "Bottoms Up", "Drink %s potions", Items.POTION, Catalog.used(Catalog.itemsOf("potion")), 15, 1, false);
		k(life, "fishing", "Catch of the Day", "Reel in %s fish", Items.COD, Catalog.gathered(Catalog.itemsOf("cod", "salmon", "tropical_fish",
				"pufferfish")), 20, 1, false);
	}

	private static void k(String area, String id, String name, String what, Item icon, @Nullable Counter count, double perPlayer, long divisor,
			boolean hard) {
		if (count == null) return;
		KINDS.put(area + "." + id, new Kind(area + "." + id, area, name, what, icon, count, perPlayer, divisor, hard));
	}
}
