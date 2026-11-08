package nl.libreria.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Everything there is to complete, for the Progress window: collections (every food tasted, every monster defeated,
 * every story found…: a set of things, have / all) and counters (zombies cured, time played…: just a number, for the
 * top lists). Modules add their own; core adds what Minecraft's statistics already know ({@link CoreCollections}).
 *
 * <p>Every collection and counter works for players who aren't online too (statistics from their file, the modules'
 * stores by UUID), so anyone's progress can be looked at. For the top lists a snapshot of everybody's numbers is kept
 * ({@code libreria/completion.json}): fresh for who is online, from their last visit for who isn't.
 */
public final class Completion {
	/** The three tabs of the Progress window. */
	public enum Tab {
		COLLECT("Collect", "Things to have and to earn."), DO("Do", "Things to make, eat, mine and trade."),
		EXPLORE("Explore", "Places to see and creatures to meet.");

		public final String label;
		public final String about;

		Tab(String label, String about) {
			this.label = label;
			this.about = about;
		}
	}

	/** One thing to have done: its key (an item id, a story id…), its picture and its name. */
	public record Entry(String key, ItemStack icon, String name) {
	}

	/** Which keys someone has done. {@code stats} are live when they're online, read from their file when they're not. */
	public interface Done {
		Set<String> of(MinecraftServer server, UUID who, Profile.Stats stats);
	}

	/** An extra line for one entry's tooltip ("Eaten 12 times"), or null. */
	public interface Detail {
		@Nullable String of(Entry entry, Profile.Stats stats, boolean done);
	}

	/** A window of its own instead of the grid (the achievements, the lore book), for {@code viewer} looking at {@code who}. */
	public interface Opener {
		/** Returns false when it has nothing for this pair (then the grid opens). */
		boolean open(ServerPlayer viewer, UUID who, Consumer<ServerPlayer> back);
	}

	/** A set of things to have done. Made with {@link #add}; the setters return it, so it reads as one sentence. */
	public static final class Collection {
		public final String id;
		public final Tab tab;
		public final int order;
		public final Item icon;
		public final String name;
		private String about = "";
		private String yes = "Done", no = "Not yet";
		private Supplier<List<Entry>> source = List::of;
		private Done done = (s, w, st) -> Set.of();
		private @Nullable Detail detail;
		private @Nullable Opener own;
		private @Nullable List<Entry> cache;
		private Set<String> keys = Set.of();

		private Collection(String id, Tab tab, int order, Item icon, String name) {
			this.id = id;
			this.tab = tab;
			this.order = order;
			this.icon = icon;
			this.name = name;
		}

		/** What it is, one sentence (the tooltip of its row). */
		public Collection about(String about) {
			this.about = about;
			return this;
		}

		/** The words for one thing done and not done ("Tasted", "Not tasted yet"). */
		public Collection words(String yes, String no) {
			this.yes = yes;
			this.no = no;
			return this;
		}

		/** Everything there is (asked once the server runs, and again after /reload). */
		public Collection entries(Supplier<List<Entry>> source) {
			this.source = source;
			this.cache = null;
			return this;
		}

		public Collection done(Done done) {
			this.done = done;
			return this;
		}

		public Collection detail(Detail detail) {
			this.detail = detail;
			return this;
		}

		public Collection own(Opener own) {
			this.own = own;
			return this;
		}

		public String about() {
			return this.about;
		}

		public String yes() {
			return this.yes;
		}

		public String no() {
			return this.no;
		}

		public @Nullable Detail detail() {
			return this.detail;
		}

		public @Nullable Opener own() {
			return this.own;
		}

		/** Everything there is, in a fixed order. */
		public List<Entry> entries() {
			List<Entry> c = this.cache;
			if (c != null) return c;
			try {
				List<Entry> out = new ArrayList<>();
				Set<String> seen = new HashSet<>();
				for (Entry e : this.source.get()) if (e != null && seen.add(e.key())) out.add(e);
				c = List.copyOf(out);
			} catch (RuntimeException e) {
				Libreria.LOG.warn("Completion {}: could not list what there is", this.id, e);
				c = List.of();
			}
			Set<String> k = new HashSet<>();
			for (Entry e : c) k.add(e.key());
			this.keys = k;
			this.cache = c;
			return c;
		}

		public int total() {
			return entries().size();
		}

		/** The keys someone has done (only those that are in the collection). */
		public Set<String> done(MinecraftServer server, UUID who, Profile.Stats stats) {
			entries();
			try {
				Set<String> out = new LinkedHashSet<>();
				for (String k : this.done.of(server, who, stats)) if (this.keys.contains(k)) out.add(k);
				return out;
			} catch (RuntimeException e) {
				Libreria.LOG.warn("Completion {}: could not count for {}", this.id, who, e);
				return Set.of();
			}
		}

		public int have(MinecraftServer server, UUID who, Profile.Stats stats) {
			return done(server, who, stats).size();
		}
	}

	/** Just a number, for the top lists: zombies cured, time played… */
	public record Counter(String id, int order, Item icon, String name, String about, Count count, LongFunction<String> format) {
	}

	public interface Count {
		long of(MinecraftServer server, UUID who, Profile.Stats stats);
	}

	private static final List<Collection> COLLECTIONS = new ArrayList<>();
	private static final List<Counter> COUNTERS = new ArrayList<>();

	private Completion() {
	}

	/** A new collection on {@code tab}, at {@code order} within it (lower first). */
	public static Collection add(String id, Tab tab, int order, Item icon, String name) {
		Collection c = new Collection(id, tab, order, icon, name);
		COLLECTIONS.removeIf(o -> o.id.equals(id));
		COLLECTIONS.add(c);
		COLLECTIONS.sort(Comparator.comparingInt((Collection o) -> o.tab.ordinal()).thenComparingInt(o -> o.order));
		return c;
	}

	/** A counter for the top lists, at {@code order} (lower first). */
	public static Counter counter(String id, int order, Item icon, String name, String about, Count count, LongFunction<String> format) {
		Counter c = new Counter(id, order, icon, name, about, count, format);
		COUNTERS.removeIf(o -> o.id().equals(id));
		COUNTERS.add(c);
		COUNTERS.sort(Comparator.comparingInt(Counter::order));
		return c;
	}

	/** Every collection that has something in it, by tab and order. */
	public static List<Collection> all() {
		List<Collection> out = new ArrayList<>();
		for (Collection c : COLLECTIONS) if (c.total() > 0) out.add(c);
		return out;
	}

	public static List<Collection> of(Tab tab) {
		List<Collection> out = new ArrayList<>();
		for (Collection c : all()) if (c.tab == tab) out.add(c);
		return out;
	}

	public static @Nullable Collection get(String id) {
		for (Collection c : COLLECTIONS) if (c.id.equals(id)) return c;
		return null;
	}

	public static List<Counter> counters() {
		return List.copyOf(COUNTERS);
	}

	public static @Nullable Counter counter(String id) {
		for (Counter c : COUNTERS) if (c.id().equals(id)) return c;
		return null;
	}

	/** Lists everything again (after /reload: new recipes, advancements…). */
	static void reload() {
		for (Collection c : COLLECTIONS) c.cache = null;
	}

	// ---------------------------------------------------------------- one player's numbers

	/** How far someone is in one collection. */
	public record Score(int have, int total) {
		public double fraction() {
			return this.total <= 0 ? 0 : Math.min(1, this.have / (double) this.total);
		}

		public boolean complete() {
			return this.total > 0 && this.have >= this.total;
		}
	}

	/** Someone's numbers at a moment (kept for the top lists). */
	public static final class Snap {
		public String name = "";
		public long at;
		/** Collection → how many done. */
		public Map<String, Integer> have = new HashMap<>();
		/** Counter → its number. */
		public Map<String, Long> count = new HashMap<>();
	}

	public static final class Data {
		public Map<UUID, Snap> players = new HashMap<>();
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/completion.json", Data.class, Data::new);

	private static Data data() {
		Data d = STORE.get();
		if (d.players == null) d.players = new HashMap<>();
		return d;
	}

	/** Counts everything for {@code who} now and keeps it. */
	public static Snap snap(MinecraftServer server, UUID who) {
		Profile.Stats stats = Profile.stats(server, who);
		Snap s = new Snap();
		Players.Known k = Players.get(server, who);
		ServerPlayer online = server.getPlayerList().getPlayer(who);
		s.name = k != null ? k.label() : online != null ? online.getName().getString() : "?";
		s.at = System.currentTimeMillis();
		for (Collection c : all()) s.have.put(c.id, c.have(server, who, stats));
		for (Counter c : COUNTERS) {
			try {
				s.count.put(c.id(), c.count().of(server, who, stats));
			} catch (RuntimeException e) {
				Libreria.LOG.warn("Completion counter {} failed for {}", c.id(), who, e);
			}
		}
		data().players.put(who, s);
		STORE.markDirty();
		return s;
	}

	/** The kept numbers of {@code who}; counted again when they're online and the numbers are older than {@code fresh} ms. */
	public static Snap of(MinecraftServer server, UUID who, long fresh) {
		Snap s = data().players.get(who);
		boolean online = server.getPlayerList().getPlayer(who) != null;
		if (s == null || online && System.currentTimeMillis() - s.at > fresh) return snap(server, who);
		return s;
	}

	/** How far someone is overall: the average of every collection's share (so a small one counts as much as a big one). */
	public static double overall(Snap s) {
		double sum = 0;
		int n = 0;
		for (Collection c : all()) {
			int total = c.total();
			if (total <= 0) continue;
			sum += Math.min(1, s.have.getOrDefault(c.id, 0) / (double) total);
			n++;
		}
		return n == 0 ? 0 : sum / n;
	}

	/** A share as a whole percentage, never 100 before everything is done. */
	public static int percent(double fraction) {
		int p = (int) Math.floor(fraction * 100);
		return fraction < 1 ? Math.min(99, p) : 100;
	}

	/** How many collections someone has complete. */
	public static int complete(Snap s) {
		int n = 0;
		for (Collection c : all()) if (c.total() > 0 && s.have.getOrDefault(c.id, 0) >= c.total()) n++;
		return n;
	}

	// ---------------------------------------------------------------- top lists

	/** The board of everything together. */
	public static final String OVERALL = "overall";

	/** One place in a top list: who, their number (‰ for {@link #OVERALL}) and the most there is (0 for counters). */
	public record Place(UUID who, String name, long value, int total) {
	}

	/** Every board in order: everything, the collections, the counters. */
	public static List<String> boards() {
		List<String> out = new ArrayList<>();
		out.add(OVERALL);
		for (Collection c : all()) out.add(c.id);
		for (Counter c : COUNTERS) out.add(c.id());
		return out;
	}

	public static String boardName(String board) {
		if (OVERALL.equals(board)) return "Everything";
		Collection c = get(board);
		if (c != null) return c.name;
		Counter k = counter(board);
		return k != null ? k.name() : board;
	}

	public static Item boardIcon(String board) {
		if (OVERALL.equals(board)) return net.minecraft.world.item.Items.NETHER_STAR;
		Collection c = get(board);
		if (c != null) return c.icon;
		Counter k = counter(board);
		return k != null ? k.icon() : net.minecraft.world.item.Items.PAPER;
	}

	/** Everybody on a board, the best first (who is online with fresh numbers). Ties keep the same order every time. */
	public static List<Place> ranking(MinecraftServer server, String board) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) of(server, p.getUUID(), 30_000);
		Collection c = get(board);
		Counter k = counter(board);
		List<Place> out = new ArrayList<>();
		for (Map.Entry<UUID, Snap> e : data().players.entrySet()) {
			Snap s = e.getValue();
			Players.Known known = Players.get(server, e.getKey());
			String name = known != null ? known.label() : s.name;
			long v;
			int total = 0;
			if (OVERALL.equals(board)) {
				v = Math.round(overall(s) * 1000);
				total = 1000;
			} else if (c != null) {
				v = s.have.getOrDefault(c.id, 0);
				total = c.total();
			} else if (k != null) {
				v = s.count.getOrDefault(k.id(), 0L);
			} else {
				continue;
			}
			out.add(new Place(e.getKey(), name, v, total));
		}
		out.sort(Comparator.comparingLong(Place::value).reversed().thenComparing(p -> p.name().toLowerCase(java.util.Locale.ROOT)));
		return out;
	}

	/** {@code who}'s place on a board (1 = best; the same number shares a place), or 0 when they aren't on it. */
	public static int rank(List<Place> ranking, UUID who) {
		long mine = -1;
		for (Place p : ranking) if (p.who().equals(who)) mine = p.value();
		if (mine < 0) return 0;
		int better = 0;
		for (Place p : ranking) if (p.value() > mine) better++;
		return better + 1;
	}

	/** A number as a board shows it: "38/41", "41%", "12 h". */
	public static String show(String board, long value, int total) {
		if (OVERALL.equals(board)) return percent(value / 1000.0) + "%";
		if (get(board) != null) return Ui.n(value) + "/" + Ui.n(total);
		Counter k = counter(board);
		return k != null ? k.format().apply(value) : Ui.n(value);
	}

	// ---------------------------------------------------------------- keeping the numbers

	/** Players to count once (everybody known, at start, so the lists are complete from the first day). */
	private static final Deque<UUID> BACKLOG = new ArrayDeque<>();

	static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			reload();
			BACKLOG.clear();
			for (Players.Known k : Players.all(server)) if (!data().players.containsKey(k.id())) BACKLOG.add(k.id());
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, ok) -> reload());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			try {
				snap(server, handler.player.getUUID());
			} catch (RuntimeException e) {
				Libreria.LOG.warn("Completion: could not count {} on leaving", handler.player.getName().getString(), e);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int t = server.getTickCount();
			// One player who isn't counted yet every half second, then everybody online once a minute (spread out)
			if (t % 10 == 3 && !BACKLOG.isEmpty()) {
				UUID id = BACKLOG.poll();
				try {
					snap(server, id);
				} catch (RuntimeException e) {
					Libreria.LOG.warn("Completion: could not count {}", id, e);
				}
			}
			List<ServerPlayer> online = server.getPlayerList().getPlayers();
			if (online.isEmpty() || t % 20 != 11) return;
			int slot = (t / 20) % 60;
			for (int i = 0; i < online.size(); i++) {
				if (i % 60 != slot) continue;
				try {
					snap(server, online.get(i).getUUID());
				} catch (RuntimeException e) {
					Libreria.LOG.warn("Completion: could not count {}", online.get(i).getName().getString(), e);
				}
			}
		});
		CoreCollections.init();
	}
}
