package nl.libreria.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A player's profile: who they are and what they did here, for anyone to look at (also when they're offline). The
 * lines come from the modules ({@link #add}); core gives playtime, since when, and monsters slain.
 */
public final class Profile {
	/** What a line says for one player, or null to leave the line out. */
	public interface Value {
		@Nullable String of(MinecraftServer server, UUID who, Stats stats);
	}

	/** {@code main}: a row of its own (four at most); otherwise in the tooltip of the card at the top. */
	public record Line(int order, Item icon, String label, Value value, boolean main) {
	}

	/** An achievement (or anything) shown big on a profile: picture, name, the colour of its rim and name, tooltip. */
	public record Shown(net.minecraft.world.item.ItemStack icon, String name, int rgb, List<net.minecraft.network.chat.Component> lore) {
	}

	/** Where the showcase comes from (the Collector): what someone shows, and choosing your own. */
	public interface Showcase {
		List<Shown> of(MinecraftServer server, UUID who);

		void choose(ServerPlayer viewer, int slot, java.util.function.Consumer<ServerPlayer> back);
	}

	private static final List<Line> LINES = new ArrayList<>();
	private static @Nullable Showcase showcase;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private Profile() {
	}

	/** A line in the tooltip of the profile's card, ordered by {@code order} (core: since 20, monsters 60). */
	public static void add(int order, Item icon, String label, Value value) {
		LINES.add(new Line(order, icon, label, value, false));
		LINES.sort(Comparator.comparingInt(Line::order));
	}

	/** A row of its own on every profile (core: playtime 10); four fit. */
	public static void main(int order, Item icon, String label, Value value) {
		LINES.add(new Line(order, icon, label, value, true));
		LINES.sort(Comparator.comparingInt(Line::order));
	}

	public static void showcase(Showcase s) {
		showcase = s;
	}

	public static @Nullable Showcase showcase() {
		return showcase;
	}

	public static List<Line> lines() {
		return List.copyOf(LINES);
	}

	static void init() {
		main(10, Items.CLOCK, "Playtime", (s, who, st) -> {
			long ticks = st.custom("play_time");
			long hours = ticks / 72000;
			if (hours >= 1) return Ui.n(hours) + (hours == 1 ? " hour" : " hours");
			long minutes = ticks / 1200;
			return minutes + (minutes == 1 ? " minute" : " minutes");
		});
		add(20, Items.COMPASS, "Here since", (s, who, st) -> {
			Players.Known k = Players.get(s, who);
			return k == null || k.first() <= 0 ? null : DATE.format(Instant.ofEpochMilli(k.first()).atZone(ZoneId.systemDefault()));
		});
		add(60, Items.IRON_SWORD, "Monsters slain", (s, who, st) -> Ui.n(st.killed(t -> t.getCategory() == MobCategory.MONSTER)));
	}

	// ---------------------------------------------------------------- statistics, also of players who aren't here

	/** Minecraft's statistics of a player: live when they're online, from their file when they're not. */
	public static final class Stats {
		private final @Nullable ServerPlayer online;
		private final @Nullable JsonObject file;

		private Stats(@Nullable ServerPlayer online, @Nullable JsonObject file) {
			this.online = online;
			this.file = file;
		}

		/** A "custom" statistic by its id without namespace ("play_time", "walk_one_cm"…). */
		public long custom(String id) {
			if (this.online != null) return Ui.customStat(this.online, id);
			return number(section("minecraft:custom"), "minecraft:" + id);
		}

		/** Kills of the kinds of mob that match. */
		public long killed(Predicate<EntityType<?>> which) {
			long n = 0;
			if (this.online != null) {
				for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
					if (which.test(t)) n += this.online.getStats().getValue(net.minecraft.stats.Stats.ENTITY_KILLED, t);
				}
				return n;
			}
			JsonObject killed = section("minecraft:killed");
			if (killed == null) return 0;
			for (var e : killed.entrySet()) {
				Identifier id = Identifier.tryParse(e.getKey());
				if (id == null) continue;
				var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
				if (type.isPresent() && which.test(type.get())) n += number(killed, e.getKey());
			}
			return n;
		}

		private @Nullable JsonObject section(String name) {
			if (this.file == null || !(this.file.get("stats") instanceof JsonObject stats)) return null;
			return stats.get(name) instanceof JsonObject o ? o : null;
		}

		private static long number(@Nullable JsonObject o, String key) {
			if (o == null) return 0;
			JsonElement e = o.get(key);
			return e != null && e.isJsonPrimitive() ? e.getAsLong() : 0;
		}
	}

	public static Stats stats(MinecraftServer server, UUID who) {
		ServerPlayer online = server.getPlayerList().getPlayer(who);
		if (online != null) return new Stats(online, null);
		Path world = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
		for (Path f : new Path[] {world.resolve("players").resolve("stats").resolve(who + ".json"), world.resolve("stats").resolve(who + ".json")}) {
			if (!Files.isRegularFile(f)) continue;
			try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
				JsonElement json = JsonParser.parseReader(r);
				if (json.isJsonObject()) return new Stats(null, json.getAsJsonObject());
			} catch (Exception e) {
				Libreria.LOG.warn("Could not read {}", f, e);
			}
		}
		return new Stats(null, null);
	}
}
