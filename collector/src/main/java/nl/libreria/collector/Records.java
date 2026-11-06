package nl.libreria.collector;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.collector.Achievement.Category;
import nl.libreria.core.Chronicle;
import nl.libreria.core.Domain;
import nl.libreria.core.Nameplate;
import nl.libreria.core.Say;
import nl.libreria.core.Ui;
import nl.libreria.core.WorldStore;
import nl.libreria.core.gui.TopCard;
import org.jetbrains.annotations.Nullable;

/**
 * The server's records: the longest fall anyone survived, the longest flight, the fastest, the farthest out… Who holds
 * each, since when, and who held it before. A new holder is a moment in the chronicle. A record that's a real feat is a
 * trophy too (see {@link Trophies}): its holder can wear it before their title.
 */
public final class Records {
	public enum Kind {
		FALL("fall", "Fall", "Longest fall survived", "fell", Items.FEATHER, 23, false, 150),
		FLIGHT("flight", "Flight", "Longest elytra flight", "flew", Items.ELYTRA, 1000, false, 10000),
		SPEED("speed", "Speed", "Fastest", "moved at", Items.SUGAR, 30, false, 80),
		DISTANCE("distance", "Range", "Farthest from the middle of the world", "went", Items.COMPASS, 2000, false, 25000),
		ALIVE("alive", "Alive", "Longest time without dying", "stayed alive for", Items.TOTEM_OF_UNDYING, 5, false, 48),
		// Not a feat: being online long, or going down to the bottom (anyone gets there)
		SESSION("session", "Session", "Longest time online in one go", "played", Items.CLOCK, 3, false, Double.NaN),
		HIT("hit", "Hit", "Hardest hit", "hit for", Items.NETHERITE_SWORD, 10, false, 50),
		LEVELS("levels", "Levels", "Most levels at once", "reached level", Items.EXPERIENCE_BOTTLE, 30, false, 100),
		DEPTH("depth", "Depth", "Deepest point reached", "went down to", Items.RECOVERY_COMPASS, -50, true, Double.NaN);

		public final String id, label, about, verb;
		public final Item icon;
		/** The least that counts as a record (the most, for depth). */
		final double least;
		/** Lower is better (depth). */
		public final boolean lower;
		/** From what value the record is a trophy (NaN: never). */
		public final double trophy;

		Kind(String id, String label, String about, String verb, Item icon, double least, boolean lower, double trophy) {
			this.id = id;
			this.label = label;
			this.about = about;
			this.verb = verb;
			this.icon = icon;
			this.least = least;
			this.lower = lower;
			this.trophy = trophy;
		}

		public boolean hasTrophy() {
			return !Double.isNaN(this.trophy);
		}

		boolean counts(double v) {
			return this.lower ? v <= this.least : v >= this.least;
		}

		boolean beats(double v, double old) {
			if (this == FALL || this == HIT || this == SPEED) return v >= old + 1;
			return this.lower ? v < old : v > old;
		}

		/** "87 m", "1.4 km", "14 km", "68 m/s", "41 h", "24 hearts", "112", "y -63". */
		public String format(double v) {
			return switch (this) {
				case FALL -> Math.round(v) + " m";
				case FLIGHT, DISTANCE -> v >= 10000 ? Math.round(v / 1000) + " km" : v >= 1000 ? String.format(Locale.US, "%.1f km", v / 1000)
						: Math.round(v) + " m";
				case SPEED -> Math.round(v) + " m/s";
				case ALIVE, SESSION -> v >= 10 ? Math.round(v) + " h" : String.format(Locale.US, "%.1f h", v);
				case HIT -> String.format(Locale.US, "%.0f hearts", v);
				case LEVELS -> Long.toString(Math.round(v));
				case DEPTH -> "y " + Math.round(Math.floor(v));
			};
		}
	}

	public static final class Rec {
		public @Nullable UUID who;
		public String name = "";
		public double value;
		public long at;
		public @Nullable UUID before;
		public String beforeName = "";
		public double beforeValue;
		/** Since when the holder has it (improving your own record doesn't change this). */
		public long heldSince;
		/** Who the server was last told holds it, with what and when: a new holder goes to chat only now and then. */
		public @Nullable UUID toldWho;
		public String toldName = "";
		public double toldValue;
		public long toldAt;
		/** When each player last had taking this record counted (ms), so swapping it back and forth farms nothing. */
		public @Nullable Map<String, Long> counted;
	}

	static final class Data {
		public Map<String, Rec> records = new HashMap<>();
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/records.json", Data.class, Data::new);
	/** When each player last got a card for beating their own record (ms), per record. */
	private static final Map<String, Long> TOLD = new HashMap<>();

	private Records() {
	}

	/**
	 * Players travelling together keep taking a record from each other (the farthest out, the longest flight). The
	 * server hears about a new holder at most once per {@link #ANNOUNCE_EVERY} per record, once the holder has had it
	 * for {@link #SETTLED} (or the change has been waiting twice as long). Then it says who has it now and who had it
	 * at the last announcement; the back and forth in between goes unsaid.
	 */
	static final long ANNOUNCE_EVERY = 15 * 60 * 1000L, SETTLED = 60 * 1000L;
	/** Taking a record counts for the "Set a server record" ladder once a day per record per player. */
	static final long COUNT_EVERY = 24 * 3600 * 1000L;
	/** The holder's own card: at most this often per record. */
	private static final long CARD_EVERY = 10 * 60 * 1000L;

	static Data data() {
		Data d = STORE.get();
		if (d.records == null) d.records = new HashMap<>();
		// Records from before announcements were spaced out: the server already heard about these
		for (Rec r : d.records.values()) {
			if (r.who != null && r.toldWho == null && r.toldAt == 0) {
				r.toldWho = r.who;
				r.toldName = r.name;
				r.toldValue = r.value;
				r.toldAt = r.at;
			}
		}
		return d;
	}

	/** Whether a record's new holder should be announced now. */
	static boolean due(Rec r, long now) {
		if (r.who == null || r.who.equals(r.toldWho)) return false;
		long since = now - r.toldAt;
		if (since < ANNOUNCE_EVERY) return false;
		return now - r.heldSince >= SETTLED || since >= 2 * ANNOUNCE_EVERY;
	}

	/** Whether taking this record counts for {@code who} now (and remembers that it did). */
	static boolean count(Rec r, UUID who, long now) {
		if (r.counted == null) r.counted = new HashMap<>();
		Long last = r.counted.get(who.toString());
		if (last != null && now - last < COUNT_EVERY) return false;
		r.counted.put(who.toString(), now);
		return true;
	}

	/** Every ten seconds: announce the records whose new holder has waited long enough. */
	static void tick(net.minecraft.server.MinecraftServer server) {
		long now = System.currentTimeMillis();
		for (Kind k : Kind.values()) {
			Rec r = data().records.get(k.id);
			if (r != null && due(r, now)) announce(server, k, r, now);
		}
	}

	private static void announce(net.minecraft.server.MinecraftServer server, Kind k, Rec r, long now) {
		UUID who = r.who;
		if (who == null) return;
		ServerPlayer p = server.getPlayerList().getPlayer(who);
		boolean taken = r.toldWho != null;
		String was = taken ? " (was " + r.toldName + ", " + k.format(r.toldValue) + ")" : "";
		// The chronicle keeps it; its own chat line would say the same as ours
		Chronicle.write(who, p != null ? p.getName().getString() : r.name, Chronicle.Kind.MILESTONE,
				"set a new record: " + k.about.toLowerCase(Locale.ROOT) + ", " + k.format(r.value) + was, taken, false);
		if (taken) {
			Component name = p != null ? Nameplate.displayName(p).copy() : Component.literal(r.name);
			Component line = Say.line(Domain.PROGRESS).append(name.copy().withStyle(ChatFormatting.WHITE))
					.append(Ui.text(" took the record ", ChatFormatting.GRAY)).append(Ui.text(k.about.toLowerCase(Locale.ROOT), ChatFormatting.GOLD))
					.append(Ui.text(": " + k.format(r.value) + was, ChatFormatting.GRAY));
			for (ServerPlayer o : server.getPlayerList().getPlayers()) if (!o.getUUID().equals(who)) o.sendSystemMessage(line);
		}
		r.toldWho = who;
		r.toldName = r.name;
		r.toldValue = r.value;
		r.toldAt = now;
		if (count(r, who, now)) {
			CollectorMod.Data cd = CollectorMod.STORE.get(who);
			cd.recordsTaken++;
			cd.recordsHeld.add(k.id);
			CollectorMod.STORE.markDirty(who);
			if (p != null) CollectorMod.check(p);
		}
		STORE.markDirty();
	}

	public static @Nullable Rec get(Kind k) {
		return data().records.get(k.id);
	}

	/** How many records this player holds now. */
	public static int held(UUID who) {
		int n = 0;
		for (Rec r : data().records.values()) if (who.equals(r.who)) n++;
		return n;
	}

	/** A value a player just reached: a record if it beats the one there is (and is worth one at all). */
	static void offer(ServerPlayer p, Kind k, double v) {
		if (p instanceof net.fabricmc.fabric.api.entity.FakePlayer || p.isCreative() || p.isSpectator() || !k.counts(v)) return;
		Data d = data();
		Rec r = d.records.get(k.id);
		if (r != null && !k.beats(v, r.value)) return;
		boolean newHolder = r == null || !p.getUUID().equals(r.who);
		long now = System.currentTimeMillis();
		Rec n = new Rec();
		n.who = p.getUUID();
		n.name = Nameplate.displayName(p).getString();
		n.value = v;
		n.at = now;
		n.heldSince = newHolder ? now : r.heldSince;
		if (r != null) {
			n.before = newHolder ? r.who : r.before;
			n.beforeName = newHolder ? r.name : r.beforeName;
			n.beforeValue = newHolder ? r.value : r.beforeValue;
			n.toldWho = r.toldWho;
			n.toldName = r.toldName;
			n.toldValue = r.toldValue;
			n.toldAt = r.toldAt;
			n.counted = r.counted;
		}
		d.records.put(k.id, n);
		STORE.markDirty();
		// The holder's own card: now and then, never a stream of them while two players keep passing each other
		String key = p.getUUID() + "|" + k.id;
		long last = TOLD.getOrDefault(key, 0L);
		if (now - last > CARD_EVERY) {
			TOLD.put(key, now);
			if (newHolder) card(p, "New record!", k.about + " · " + k.format(v), k, Trophies.isFeat(k, v));
			else card(p, "Your record, better", k.about + " · " + k.format(v), k, Trophies.isFeat(k, v) && !Trophies.isFeat(k, r.value));
		}
		// The very first holder of a record: written down and counted right away (nobody to tell it was taken from)
		if (r == null) announce(p.level().getServer(), k, n, now);
		// Whoever had it loses the trophy (their plate shows it the next time it updates); the server hears of the
		// new holder in tick(), spaced out
	}

	private static void card(ServerPlayer p, String title, String sub, Kind k, boolean trophy) {
		java.util.List<TopCard.Row> rows = trophy ? java.util.List.of(TopCard.Row.text("star", "Trophy",
				Component.empty().append(Trophies.glyph(k)).append(Ui.text(" yours: wear it in Records", ChatFormatting.WHITE)))) : java.util.List.of();
		TopCard.whenFree(p, "record", new TopCard.Card(trophy ? Trophies.item(k) : new ItemStack(k.icon), Domain.PROGRESS.rgb,
				Ui.text(title, Domain.PROGRESS.rgb), Ui.text(sub, ChatFormatting.GRAY), rows), 120, 60 * 20);
	}

	/** A ladder for taking records (holding one is a trophy, not a title). */
	static void build() {
		Category c = Category.SAMEN;
		Catalog.Counter taken = new Catalog.Counter((p, d) -> d.recordsTaken);
		Catalog.ladder(c, "records", Items.GOLD_BLOCK, "Set a server record|Set a server record %,d times", taken, 1,
				Catalog.r(Rarity.RARE, Rarity.EPIC, Rarity.LEGENDARY), 1, "Record Breaker", 5, "Hall of Famer", 20, "Living Legend");
	}

	/** For the screenshots: a record as if someone set it (and someone else had it before, if beforeName isn't null). */
	public static void debugSet(Kind k, UUID who, String name, double v, @Nullable String beforeName, double beforeValue) {
		Rec n = new Rec();
		n.who = who;
		n.name = name;
		n.value = v;
		n.at = System.currentTimeMillis() - 3L * 24 * 3600 * 1000;
		if (beforeName != null) {
			n.before = UUID.nameUUIDFromBytes(beforeName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			n.beforeName = beforeName;
			n.beforeValue = beforeValue;
		}
		data().records.put(k.id, n);
		STORE.markDirty();
	}

	/** For the self-test: forget a record again. */
	static void forget(Kind k) {
		data().records.remove(k.id);
		STORE.markDirty();
	}
}
