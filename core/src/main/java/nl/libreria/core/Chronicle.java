package nl.libreria.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.dialog.Window;
import org.jetbrains.annotations.Nullable;

/**
 * The chronicle: the story of this world, written as it happens. Who arrived when, who was first in the Nether, who
 * slew the Wither, which village was found by whom, which sword earned a name, which wolf was tamed and which one was
 * lost. Modules write to it with {@link #write}; players read it in a window (quick menu, journal, {@code /chronicle}).
 * Kept in {@code <world>/libreria/chronicle.json}.
 */
public final class Chronicle {
	/** What a moment is about; the window can show only one kind. */
	public enum Kind {
		ARRIVAL, FIRST, BOSS, DISCOVERY, LEGEND, PET, ACHIEVEMENT, DEATH, MILESTONE, WORLD
	}

	/** One moment. {@code who} is who it's about (for "Only me"); {@code name} is shown in front of the text. */
	public static final class Entry {
		public long at;
		public int day;
		public String kind = Kind.WORLD.name();
		public @Nullable UUID who;
		public String name = "";
		public String text = "";
		public boolean major;
	}

	public static final class Data {
		public List<Entry> entries = new ArrayList<>();
		/** Things somebody did first (so the next one isn't "the first" any more). */
		public Set<String> firsts = new HashSet<>();
		/** Players that have arrived (their first join is written once). */
		public Set<UUID> arrived = new HashSet<>();
		/** Per player: the last playtime milestone written (hours). */
		public Map<UUID, Integer> hours = new HashMap<>();
		/** The last "the world turned N days old" written. */
		public int worldDays;
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/chronicle.json", Data.class, Data::new);

	public static final ServerSetting.Toggle ANNOUNCE = Libreria.MODULE.serverToggle("chronicle_chat", "Chronicle in chat",
			Items.WRITABLE_BOOK, true, "Big moments (a boss slain, a first on", "the server, a weapon earning its name)",
			"also show as one quiet line in chat.");

	/** Older small moments go when there are more than this; big ones stay. */
	private static final int MAX = 6000;
	private static final int PAGE = 60;
	private static final int[] HOURS = {10, 50, 100, 250, 500, 1000, 2000};
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

	private Chronicle() {
	}

	static Data data() {
		Data d = STORE.get();
		if (d.entries == null) d.entries = new ArrayList<>();
		if (d.firsts == null) d.firsts = new HashSet<>();
		if (d.arrived == null) d.arrived = new HashSet<>();
		if (d.hours == null) d.hours = new HashMap<>();
		return d;
	}

	// ---------------------------------------------------------------- writing

	/** Writes a moment about a player: "Jeffrey" + " slew the Wither". */
	public static void write(@Nullable ServerPlayer who, Kind kind, String text, boolean major) {
		write(who == null ? null : who.getUUID(), who == null ? "" : who.getName().getString(), kind, text, major);
	}

	/** Writes a moment. {@code name} may be empty (then {@code text} is the whole line). Never throws. */
	public static void write(@Nullable UUID who, String name, Kind kind, String text, boolean major) {
		try {
			MinecraftServer server = Libreria.server();
			Entry e = new Entry();
			e.at = System.currentTimeMillis();
			e.day = server == null ? 0 : day(server);
			e.kind = kind.name();
			e.who = who;
			e.name = name == null ? "" : name;
			e.text = text;
			e.major = major;
			Data d = data();
			d.entries.add(e);
			trim(d);
			STORE.markDirty();
			// One quiet line for everybody. Not for achievements (the achievement itself already says so in chat), and
			// not to the player it's about when they already got a card for it (a weapon that earned its name).
			if (major && server != null && ANNOUNCE.get() && kind != Kind.ACHIEVEMENT) {
				Component line = Say.line(Domain.PROGRESS).append(Ui.text("Chronicle · ", ChatFormatting.DARK_GRAY)).append(line(e, false));
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					if (kind == Kind.LEGEND && p.getUUID().equals(who)) continue;
					p.sendSystemMessage(line);
				}
			}
		} catch (RuntimeException ex) {
			Libreria.LOG.warn("Could not write to the chronicle: {}", text, ex);
		}
	}

	/** True only the first time anybody does {@code key} ("nether", "boss:wither"…); remembers it. */
	public static boolean firstTime(String key) {
		Data d = data();
		if (!d.firsts.add(key)) return false;
		STORE.markDirty();
		return true;
	}

	private static void trim(Data d) {
		if (d.entries.size() <= MAX) return;
		// Drop the oldest small moments first; big ones only when there are no small ones left
		int over = d.entries.size() - MAX;
		var it = d.entries.iterator();
		while (over > 0 && it.hasNext()) {
			if (!it.next().major) {
				it.remove();
				over--;
			}
		}
		while (d.entries.size() > MAX) d.entries.removeFirst();
	}

	/** The world's day, as the F3 screen counts it (day 1 is the first). */
	public static int day(MinecraftServer server) {
		return (int) (server.overworld().getOverworldClockTime() / 24000L) + 1;
	}

	// ---------------------------------------------------------------- what core writes itself

	/** Bosses, with how a sentence names them. */
	private static final Map<String, String> BOSSES = Map.of("ender_dragon", "the Ender Dragon", "wither", "the Wither",
			"warden", "a Warden", "elder_guardian", "an Elder Guardian");

	static void init() {
		Libreria.MODULE.hubEntry(Domain.PROGRESS, 30, Items.LECTERN, "Chronicle", Chronicle::open,
				"The story of this world: who came first, who slew what, which weapon earned a name.");
		Libreria.MODULE.commands(Domain.PROGRESS, "/chronicle – the story of this world");
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> onJoin(handler.player));
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			try {
				if (entity instanceof ServerPlayer victim) onPlayerDeath(victim, source);
				else onDeath(entity, source);
			} catch (RuntimeException e) {
				Libreria.LOG.warn("Chronicle: could not note a death", e);
			}
		});
	}

	/** Advancements worth a line, with the sentence for everyone and the one for the very first. */
	private static final Map<String, String[]> STORY = Map.of(
			"story/enter_the_nether", new String[] {"reached the Nether", "was the first to reach the Nether"},
			"story/enter_the_end", new String[] {"reached the End", "was the first to reach the End"},
			"story/mine_diamond", new String[] {"found diamonds", "was the first to find diamonds"},
			"nether/obtain_ancient_debris", new String[] {"found ancient debris", "was the first to find ancient debris"},
			"end/elytra", new String[] {"found an elytra", "was the first to find an elytra"},
			"end/enter_end_gateway", new String[] {"escaped the End island", "was the first to escape the End island"},
			"adventure/hero_of_the_village", new String[] {"defended a village from a raid", "was the first to defend a village from a raid"},
			"story/cure_zombie_villager", new String[] {"cured a zombie villager", "was the first to cure a zombie villager"},
			"nether/netherite_armor", new String[] {"wears a full set of netherite", "was the first in full netherite"});
	/** Already written another way (the boss itself). */
	private static final Set<String> SKIP = Set.of("end/kill_dragon", "nether/summon_wither", "end/root", "story/root",
			"nether/root", "adventure/root", "husbandry/root");

	private static final String FIRST_ARRIVAL = "was the first to arrive in this world", ARRIVAL_TEXT = "arrived in the world";

	/**
	 * At start: what the players' own files say happened before the chronicle was there (or while it wasn't loaded).
	 * Those things aren't "the first" any more, and a moment that called somebody the first while somebody else did it
	 * earlier becomes an ordinary one.
	 */
	static @Nullable ChronicleHistory seed(MinecraftServer server) {
		try {
			ChronicleHistory h = ChronicleHistory.read(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT),
					STORY.keySet(), BOSSES.keySet());
			int fixed = apply(data(), h);
			Libreria.LOG.info("Chronicle: read {} player files; {} moment(s) no longer called a first", h.files, fixed);
			return h;
		} catch (RuntimeException e) {
			Libreria.LOG.warn("Chronicle: could not read the players' history", e);
			return null;
		}
	}

	// ---------------------------------------------------------------- while you were away

	public static final Setting.Toggle AWAY = Libreria.MODULE.toggle("away_card", "While you were away", true, Items.WRITABLE_BOOK,
			"Back after a while: a card with what", "happened in the chronicle meanwhile.").in(Domain.PROGRESS, "Chronicle");
	/** Away at least this long before there's a card. */
	static final long AWAY_MS = 30 * 60 * 1000L;
	private static final List<java.util.function.Function<ServerPlayer, Component>> AWAY_LINES = new ArrayList<>();

	/** One more line for the card, e.g. the server goal ("Server goal Stone Age: 24%"); null = nothing to say. */
	public static void addAwayLine(java.util.function.Function<ServerPlayer, Component> line) {
		AWAY_LINES.add(line);
	}

	/** What others did since {@code since}, newest first, the big moments first. */
	static List<Entry> since(UUID who, long since) {
		List<Entry> out = new ArrayList<>();
		for (Entry e : data().entries) if (e.at > since && !who.equals(e.who)) out.add(e);
		out.sort(java.util.Comparator.<Entry>comparingInt(e -> e.major ? 0 : 1).thenComparing(java.util.Comparator.comparingLong((Entry e) -> e.at).reversed()));
		return out;
	}

	/** Takes moments out again (for self-tests that wrote some). */
	public static void forget(java.util.function.Predicate<Entry> which) {
		if (data().entries.removeIf(which)) STORE.markDirty();
	}

	/** A player came back: if they were away a while and others did things, a card (and a line in chat). */
	public static void welcomeBack(ServerPlayer p, long last) {
		try {
			long now = System.currentTimeMillis();
			if (last <= 0 || now - last < AWAY_MS || !AWAY.get(p)) return;
			List<Entry> news = since(p.getUUID(), last);
			if (news.isEmpty()) return;
			List<nl.libreria.core.gui.TopCard.Row> rows = new ArrayList<>();
			for (Entry e : news) {
				if (rows.size() >= 2) break;
				// Without the ✦ (the card's font has no such picture: a star in front instead), cut when too long
				String text = (e.name.isEmpty() ? "" : e.name + (e.text.startsWith("'") ? "" : " ")) + e.text;
				if (text.length() > 44) text = text.substring(0, 42).stripTrailing() + "...";
				MutableComponent v = Component.empty();
				if (!e.name.isEmpty()) {
					v.append(Ui.text(text.substring(0, Math.min(text.length(), e.name.length())), ChatFormatting.YELLOW));
					v.append(Ui.text(text.substring(Math.min(text.length(), e.name.length())), e.major ? ChatFormatting.WHITE : ChatFormatting.GRAY));
				} else {
					v.append(Ui.text(text, e.major ? ChatFormatting.WHITE : ChatFormatting.GRAY));
				}
				rows.add(nl.libreria.core.gui.TopCard.Row.text(e.major ? "star" : "flag", "", v));
			}
			for (var f : AWAY_LINES) {
				Component extra = f.apply(p);
				if (extra != null) rows.add(nl.libreria.core.gui.TopCard.Row.text("trophy", "", extra));
			}
			String away = Players.ago(last).replace(" ago", "").toLowerCase(Locale.ROOT);
			String n = news.size() == 1 ? "1 moment" : news.size() + " moments";
			Component title = Ui.text("While you were away", Domain.PROGRESS.rgb);
			Component sub = Ui.text((away.equals("yesterday") ? "A day" : cap(away)) + " · " + n + " in the chronicle", ChatFormatting.GRAY);
			nl.libreria.core.gui.TopCard.whenFree(p, "away", new nl.libreria.core.gui.TopCard.Card(new ItemStack(Items.WRITABLE_BOOK),
					Domain.PROGRESS.rgb, title, sub, rows), 240, 3 * 60 * 20);
			p.sendSystemMessage(Say.line(Domain.PROGRESS).append(Ui.text("While you were away: " + n + " in the chronicle. ", ChatFormatting.GRAY))
					.append(Component.literal("[Read]").withStyle(st -> st.withColor(ChatFormatting.AQUA)
							.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/chronicle"))
							.withHoverEvent(new HoverEvent.ShowText(Ui.text("Open the chronicle", ChatFormatting.GRAY))))));
		} catch (RuntimeException e) {
			Libreria.LOG.warn("Chronicle: could not make the away card", e);
		}
	}

	private static String cap(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** Brings {@code d} in line with {@code h}; returns how many moments were corrected. */
	static int apply(Data d, ChronicleHistory h) {
		int before = d.firsts.size() + d.arrived.size();
		h.done.forEach((adv, who) -> {
			if (!who.isEmpty()) d.firsts.add("adv:" + adv);
		});
		h.slain.forEach((boss, who) -> {
			if (!who.isEmpty()) d.firsts.add("boss:" + boss);
		});
		for (UUID u : h.died) d.firsts.add("death:" + u);
		d.arrived.addAll(h.seen.keySet());
		int fixed = 0;
		for (Entry e : d.entries) {
			if (!e.major || e.who == null) continue;
			if (Kind.FIRST.name().equals(e.kind)) {
				for (Map.Entry<String, String[]> s : STORY.entrySet()) {
					if (!s.getValue()[1].equals(e.text)) continue;
					if (!ChronicleHistory.earlier(h.done.getOrDefault(s.getKey(), Map.of()), e.who, e.at)) continue;
					e.text = s.getValue()[0];
					e.major = false;
					fixed++;
				}
			} else if (Kind.ARRIVAL.name().equals(e.kind) && FIRST_ARRIVAL.equals(e.text) && ChronicleHistory.earlier(h.seen, e.who, e.at)) {
				e.text = ARRIVAL_TEXT;
				e.major = false;
				fixed++;
			}
		}
		if (fixed > 0 || d.firsts.size() + d.arrived.size() != before) STORE.markDirty();
		return fixed;
	}

	/** A player completed an advancement (from the mixin). Never throws. */
	public static void onAdvancement(ServerPlayer p, net.minecraft.advancements.AdvancementHolder holder) {
		try {
			var id = holder.id();
			if (!id.getNamespace().equals("minecraft") || SKIP.contains(id.getPath())) return;
			String[] story = STORY.get(id.getPath());
			if (story != null) {
				// Not the first when somebody online did it already (before the chronicle was there, say)
				boolean already = false;
				MinecraftServer server = p.level().getServer();
				if (server != null) {
					for (ServerPlayer o : server.getPlayerList().getPlayers()) {
						if (o != p && o.getAdvancements().getOrStartProgress(holder).isDone()) already = true;
					}
				}
				boolean first = firstTime("adv:" + id.getPath()) && !already;
				write(p, Kind.FIRST, first ? story[1] : story[0], first);
				return;
			}
			var display = holder.value().display().orElse(null);
			if (display == null) return;
			String title = display.title().getString();
			switch (display.type()) {
				case CHALLENGE -> write(p, Kind.ACHIEVEMENT, "completed the challenge \"" + title + "\"", true);
				case GOAL -> write(p, Kind.ACHIEVEMENT, "reached the goal \"" + title + "\"", false);
				default -> {
				}
			}
		} catch (RuntimeException e) {
			Libreria.LOG.warn("Chronicle: could not note an advancement", e);
		}
	}

	private static void onPlayerDeath(ServerPlayer victim, net.minecraft.world.damagesource.DamageSource source) {
		String message = source.getLocalizedDeathMessage(victim).getString();
		if (source.getEntity() instanceof ServerPlayer killer && killer != victim) {
			write(victim.getUUID(), "", Kind.DEATH, message, false);
		} else if (firstTime("death:" + victim.getUUID())) {
			write(victim.getUUID(), "", Kind.DEATH, message + ", for the first time", false);
		}
	}

	private static void onDeath(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
		String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
		String boss = BOSSES.get(id);
		if (boss == null) return;
		ServerPlayer killer = source.getEntity() instanceof ServerPlayer sp ? sp : null;
		if (killer == null && entity.level() instanceof net.minecraft.server.level.ServerLevel level) {
			// The dragon often dies to a bed or an arrow from far: the nearest player gets the honour
			double best = 128 * 128;
			for (ServerPlayer p : level.players()) {
				double d = p.distanceToSqr(entity);
				if (d < best && !p.isSpectator()) {
					best = d;
					killer = p;
				}
			}
		}
		if (killer == null) return;
		boolean first = firstTime("boss:" + id);
		boolean big = first || !id.equals("elder_guardian");
		write(killer, Kind.BOSS, first ? "was the first to slay " + boss : "slew " + boss, big);
	}

	static void onJoin(ServerPlayer p) {
		Data d = data();
		if (d.arrived.add(p.getUUID())) {
			STORE.markDirty();
			boolean first = d.arrived.size() == 1;
			write(p, Kind.ARRIVAL, first ? FIRST_ARRIVAL : ARRIVAL_TEXT, first);
		}
	}

	/** Once a minute: playtime milestones and the age of the world. */
	static void tick(MinecraftServer server) {
		Data d = data();
		int days = day(server);
		int mark = days / 100 * 100;
		if (mark >= 100 && mark > d.worldDays) {
			d.worldDays = mark;
			STORE.markDirty();
			write(null, "", Kind.WORLD, "The world turned " + mark + " days old", true);
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			int hours = p.getStats().getValue(net.minecraft.stats.Stats.CUSTOM, net.minecraft.stats.Stats.PLAY_TIME) / 72000;
			int had = d.hours.getOrDefault(p.getUUID(), 0);
			int reached = had;
			for (int h : HOURS) if (hours >= h) reached = h;
			if (reached > had) {
				d.hours.put(p.getUUID(), reached);
				STORE.markDirty();
				// Only write the milestone just passed (not every one at once for a long-time player on day one)
				write(p, Kind.MILESTONE, "has spent " + reached + " hours in the world", reached >= 100);
			}
		}
	}

	// ---------------------------------------------------------------- reading

	/** Which moments the window shows. */
	public enum Filter {
		ALL("Everything"), MINE("Only me"), BIG("Big moments");

		final String label;

		Filter(String label) {
			this.label = label;
		}
	}

	public static void open(ServerPlayer p) {
		open(p, Filter.ALL, 0);
	}

	static void open(ServerPlayer p, Filter filter, int page) {
		Data d = data();
		List<Entry> shown = new ArrayList<>();
		for (int i = d.entries.size() - 1; i >= 0; i--) {
			Entry e = d.entries.get(i);
			if (filter == Filter.MINE && !p.getUUID().equals(e.who)) continue;
			if (filter == Filter.BIG && !e.major) continue;
			shown.add(e);
		}
		int pages = Math.max(1, (shown.size() + PAGE - 1) / PAGE);
		int pg = Math.max(0, Math.min(page, pages - 1));
		MinecraftServer server = p.level().getServer();
		Window w = Window.of(Domain.PROGRESS, "Chronicle");
		w.text(Ui.text("Day " + day(server) + " of this world · " + d.entries.size() + (d.entries.size() == 1 ? " moment" : " moments")
				+ " written down" + (pages > 1 ? " · page " + (pg + 1) + " of " + pages : ""), ChatFormatting.GRAY));
		if (shown.isEmpty()) {
			w.text(Ui.text(filter == Filter.MINE ? "Nothing about you yet. Explore, slay something big, tame a wolf: it's all written down here."
					: "Nothing yet. The story of this world is written here as it happens.", ChatFormatting.GRAY));
		}
		// Grouped by day, newest first
		int day = Integer.MIN_VALUE;
		MutableComponent block = null;
		for (Entry e : shown.subList(pg * PAGE, Math.min(shown.size(), (pg + 1) * PAGE))) {
			if (e.day != day) {
				if (block != null) w.text(block);
				day = e.day;
				w.text(Ui.text(e.day > 0 ? "Day " + e.day : "Before the first day", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD));
				block = null;
			}
			MutableComponent l = line(e, true);
			block = block == null ? Component.empty().append(l) : block.append(Component.literal("\n")).append(l);
		}
		if (block != null) w.text(block);
		for (Filter f : Filter.values()) {
			if (f == filter) continue;
			w.button(f.label, null, pp -> open(pp, f, 0));
		}
		if (pg > 0) w.button("◀ Newer", null, pp -> open(pp, filter, pg - 1));
		if (pg < pages - 1) w.button("Older ▶", null, pp -> open(pp, filter, pg + 1));
		w.up(Domain.PROGRESS).show(p);
	}

	/** "Jeffrey slew the Wither", the name in yellow; big moments in white, others grey. */
	static MutableComponent line(Entry e, boolean hover) {
		MutableComponent out = Component.empty();
		if (e.major) out.append(Ui.text("✦ ", ChatFormatting.GOLD));
		if (!e.name.isEmpty()) {
			out.append(Ui.text(e.name, ChatFormatting.YELLOW));
			// "Jeffrey" + "'s sword…" without a space
			if (!e.text.startsWith("'")) out.append(Ui.text(" ", ChatFormatting.GRAY));
		}
		out.append(Ui.text(e.text, e.major ? ChatFormatting.WHITE : ChatFormatting.GRAY));
		if (hover && e.at > 0) {
			String when = WHEN.format(Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault()));
			out.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Ui.text(when, ChatFormatting.GRAY))));
		}
		return out;
	}
}
