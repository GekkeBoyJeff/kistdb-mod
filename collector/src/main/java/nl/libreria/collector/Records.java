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
 * each, since when, and who held it before. A new holder is a moment in the chronicle; holding one ever is an
 * achievement (a title).
 */
public final class Records {
	public enum Kind {
		FALL("fall", "Fall", "Longest fall survived", "fell", Items.FEATHER, 23, false, "Thrill Seeker"),
		FLIGHT("flight", "Flight", "Longest elytra flight", "flew", Items.ELYTRA, 1000, false, "Ace of the Skies"),
		SPEED("speed", "Speed", "Fastest", "moved at", Items.SUGAR, 30, false, "Speedster"),
		DISTANCE("distance", "Distance", "Farthest from the middle of the world", "went", Items.COMPASS, 2000, false, "Far Out"),
		ALIVE("alive", "No deaths", "Longest time without dying", "stayed alive for", Items.TOTEM_OF_UNDYING, 5, false, "Death-Defying"),
		SESSION("session", "Session", "Longest time online in one go", "played", Items.CLOCK, 3, false, "Marathon Gamer"),
		HIT("hit", "Hit", "Hardest hit", "hit for", Items.NETHERITE_SWORD, 10, false, "Heavyweight"),
		LEVELS("levels", "Levels", "Most levels at once", "reached level", Items.EXPERIENCE_BOTTLE, 30, false, "Know-It-Most"),
		DEPTH("depth", "Depth", "Deepest point reached", "went down to", Items.RECOVERY_COMPASS, -50, true, "Deep Thinker");

		public final String id, label, about, verb;
		public final Item icon;
		/** The least that counts as a record (the most, for depth). */
		final double least;
		/** Lower is better (depth). */
		final boolean lower;
		/** The title for having held it. */
		public final String title;

		Kind(String id, String label, String about, String verb, Item icon, double least, boolean lower, String title) {
			this.id = id;
			this.label = label;
			this.about = about;
			this.verb = verb;
			this.icon = icon;
			this.least = least;
			this.lower = lower;
			this.title = title;
		}

		boolean counts(double v) {
			return this.lower ? v <= this.least : v >= this.least;
		}

		boolean beats(double v, double old) {
			if (this == FALL || this == HIT || this == SPEED) return v >= old + 1;
			return this.lower ? v < old : v > old;
		}

		/** "87 m", "14.2 km", "68 m/s", "41 h", "24 hearts", "112", "y -63". */
		public String format(double v) {
			return switch (this) {
				case FALL -> Math.round(v) + " m";
				case FLIGHT, DISTANCE -> v >= 1000 ? String.format(Locale.US, "%.1f km", v / 1000) : Math.round(v) + " m";
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
	}

	static final class Data {
		public Map<String, Rec> records = new HashMap<>();
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/records.json", Data.class, Data::new);
	/** When each player last got a card for beating their own record (ms), per record. */
	private static final Map<String, Long> TOLD = new HashMap<>();

	private Records() {
	}

	static Data data() {
		Data d = STORE.get();
		if (d.records == null) d.records = new HashMap<>();
		return d;
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
		Rec n = new Rec();
		n.who = p.getUUID();
		n.name = Nameplate.displayName(p).getString();
		n.value = v;
		n.at = System.currentTimeMillis();
		if (r != null) {
			n.before = newHolder ? r.who : r.before;
			n.beforeName = newHolder ? r.name : r.beforeName;
			n.beforeValue = newHolder ? r.value : r.beforeValue;
		}
		d.records.put(k.id, n);
		STORE.markDirty();
		CollectorMod.Data cd = CollectorMod.STORE.get(p);
		if (newHolder) {
			cd.recordsTaken++;
			cd.recordsHeld.add(k.id);
			CollectorMod.STORE.markDirty(p);
			String was = r == null ? "" : " (was " + r.name + ", " + k.format(r.value) + ")";
			Chronicle.write(p, Chronicle.Kind.MILESTONE, "set a new record: " + k.about.toLowerCase(Locale.ROOT) + ", " + k.format(v) + was,
					r != null);
			card(p, "New record!", k.about + " · " + k.format(v), k);
			if (r != null) {
				Component line = Say.line(Domain.PROGRESS).append(Nameplate.displayName(p).copy().withStyle(ChatFormatting.WHITE))
						.append(Ui.text(" took the record ", ChatFormatting.GRAY)).append(Ui.text(k.about.toLowerCase(Locale.ROOT), ChatFormatting.GOLD))
						.append(Ui.text(": " + k.format(v) + was, ChatFormatting.GRAY));
				for (ServerPlayer o : p.level().getServer().getPlayerList().getPlayers()) if (o != p) o.sendSystemMessage(line);
			}
			CollectorMod.check(p);
		} else {
			// Beating your own: now and then a card, never a stream of them
			String key = p.getUUID() + "|" + k.id;
			long last = TOLD.getOrDefault(key, 0L);
			if (n.at - last > 10 * 60 * 1000L) {
				TOLD.put(key, n.at);
				card(p, "Your record, better", k.about + " · " + k.format(v), k);
			}
		}
	}

	private static void card(ServerPlayer p, String title, String sub, Kind k) {
		TopCard.whenFree(p, "record", new TopCard.Card(new ItemStack(k.icon), Domain.PROGRESS.rgb, Ui.text(title, Domain.PROGRESS.rgb),
				Ui.text(sub, ChatFormatting.GRAY), java.util.List.of()), 120, 60 * 20);
	}

	/** One achievement per record (for having held it) and a ladder for taking records. */
	static void build() {
		Category c = Category.SAMEN;
		for (Kind k : Kind.values()) {
			Catalog.add(new Achievement("record." + k.id, c, k.title, "Hold the record: " + k.about.toLowerCase(Locale.ROOT), k.icon, Rarity.EPIC,
					Catalog.goal(1, (p, d) -> d.recordsHeld.contains(k.id) ? 1 : 0), null));
		}
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
