package nl.libreria.info;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToLongFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Libreria;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import nl.libreria.core.WorldStore;
import nl.libreria.core.dialog.Window;
import nl.libreria.core.gui.Toast;

/**
 * Leaderboards as floating boards in the world (an admin places them): the top ten in time played, achievement
 * points, mobs defeated or distance walked. Scores of players who are offline are remembered from when they were last
 * online.
 */
public final class Leaderboards {
	/** What a board ranks. */
	public enum Kind {
		PLAYTIME("Time played", p -> p.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME)),
		POINTS("Achievement points", p -> Services.progress().map(pr -> (long) pr.points(p)).orElse(0L)),
		MOBS("Mobs defeated", p -> p.getStats().getValue(Stats.CUSTOM, Stats.MOB_KILLS)),
		WALKED("Distance walked", p -> (long) p.getStats().getValue(Stats.CUSTOM, Stats.WALK_ONE_CM)
				+ p.getStats().getValue(Stats.CUSTOM, Stats.SPRINT_ONE_CM));

		final String title;
		final ToLongFunction<ServerPlayer> score;

		Kind(String title, ToLongFunction<ServerPlayer> score) {
			this.title = title;
			this.score = score;
		}

		String format(long v) {
			return switch (this) {
				case PLAYTIME -> v / 72000 >= 1 ? v / 72000 + " h" : v / 1200 + " min";
				case WALKED -> v >= 100_000 ? String.format(java.util.Locale.ROOT, "%.1f km", v / 100_000.0) : v / 100 + " m";
				default -> Long.toString(v);
			};
		}
	}

	public static final class Board {
		public String dim = "minecraft:overworld";
		public double x, y, z;
		public String kind = Kind.PLAYTIME.name();
	}

	public static final class Score {
		public String name = "";
		public long value;
	}

	public static final class Data {
		public List<Board> boards = new ArrayList<>();
		/** Kind → player → last known score. */
		public Map<String, Map<UUID, Score>> scores = new HashMap<>();
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/leaderboards.json", Data.class, Data::new);

	private static final Map<Board, ElementHolder> SHOWN = new HashMap<>();
	private static final Map<Board, TextDisplayElement> TEXT = new HashMap<>();

	private Leaderboards() {
	}

	private static Data data() {
		Data d = STORE.get();
		if (d.boards == null) d.boards = new ArrayList<>();
		if (d.scores == null) d.scores = new HashMap<>();
		return d;
	}

	// ---------------------------------------------------------------- scores

	/** Remembers the scores of everybody online (every minute, and when someone leaves). */
	static void record(ServerPlayer p) {
		Data d = data();
		for (Kind k : Kind.values()) {
			Score s = new Score();
			s.name = p.getName().getString();
			s.value = k.score.applyAsLong(p);
			d.scores.computeIfAbsent(k.name(), x -> new HashMap<>()).put(p.getUUID(), s);
		}
		STORE.markDirty();
	}

	static List<Map.Entry<UUID, Score>> top(Kind k, int n) {
		Map<UUID, Score> m = data().scores.getOrDefault(k.name(), Map.of());
		List<Map.Entry<UUID, Score>> list = new ArrayList<>(m.entrySet());
		list.removeIf(e -> e.getValue().value <= 0);
		list.sort(Comparator.comparingLong((Map.Entry<UUID, Score> e) -> e.getValue().value).reversed());
		return list.subList(0, Math.min(n, list.size()));
	}

	// ---------------------------------------------------------------- boards

	static void tick(MinecraftServer server, int tick) {
		if (tick % 1200 == 0) for (ServerPlayer p : server.getPlayerList().getPlayers()) record(p);
		if (tick % 100 != 0) return;
		for (Board b : data().boards) {
			ServerLevel level = level(server, b.dim);
			if (level == null) continue;
			TextDisplayElement text = TEXT.get(b);
			if (text == null) {
				ElementHolder holder = new ElementHolder();
				text = new TextDisplayElement();
				text.setBillboardMode(Display.BillboardConstraints.CENTER);
				text.setBackground(0x90100A06);
				text.setLineWidth(220);
				text.setShadow(true);
				text.setBrightness(new Brightness(15, 15));
				text.setScale(new org.joml.Vector3f(1.0f));
				holder.addElement(text);
				ChunkAttachment.ofTicking(holder, level, new Vec3(b.x, b.y, b.z));
				SHOWN.put(b, holder);
				TEXT.put(b, text);
			}
			text.setText(render(b));
		}
	}

	private static Component render(Board b) {
		Kind k = kind(b);
		MutableComponent c = Component.literal("★ " + k.title + " ★").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		List<Map.Entry<UUID, Score>> top = top(k, 10);
		if (top.isEmpty()) c.append("\n").append(Ui.text("Nobody yet", ChatFormatting.GRAY));
		int[] colors = {0xFFD54F, 0xE0E0E0, 0xD39A5F};
		for (int i = 0; i < top.size(); i++) {
			Score s = top.get(i).getValue();
			int color = i < 3 ? colors[i] : 0xBDBDBD;
			c.append("\n").append(Component.literal((i + 1) + ". ").withStyle(st -> st.withColor(0x8A8A8A)))
					.append(Component.literal(s.name).withStyle(st -> st.withColor(color)))
					.append(Component.literal("  " + k.format(s.value)).withStyle(ChatFormatting.WHITE));
		}
		return c;
	}

	private static Kind kind(Board b) {
		try {
			return Kind.valueOf(b.kind);
		} catch (IllegalArgumentException e) {
			return Kind.PLAYTIME;
		}
	}

	private static ServerLevel level(MinecraftServer server, String dim) {
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim)));
	}

	/** Places a board two blocks in front of the admin, at eye height. */
	public static void place(ServerPlayer p, Kind k) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
		Vec3 at = p.getEyePosition().add(flat.scale(2.5));
		Board b = new Board();
		b.dim = p.level().dimension().identifier().toString();
		b.x = at.x;
		b.y = at.y;
		b.z = at.z;
		b.kind = k.name();
		for (ServerPlayer o : p.level().getServer().getPlayerList().getPlayers()) record(o);
		data().boards.add(b);
		STORE.saveNow();
		Libreria.LOG.info("{} placed a {} leaderboard at {} {} {}", p.getName().getString(), k.title, (int) b.x, (int) b.y, (int) b.z);
		tick(p.level().getServer(), 0);
		Toast.show(p, Items.GOLDEN_HELMET, Toast.GOOD, "Leaderboard placed", k.title + " · /leaderboard remove takes it away");
	}

	/** Removes the board nearest to the admin (within 8 blocks). */
	static void removeNearest(ServerPlayer p) {
		Board best = null;
		double bestD = 64;
		String dim = p.level().dimension().identifier().toString();
		for (Board b : data().boards) {
			if (!b.dim.equals(dim)) continue;
			double d = p.distanceToSqr(b.x, b.y, b.z);
			if (d < bestD) {
				bestD = d;
				best = b;
			}
		}
		if (best == null) {
			Toast.show(p, Items.BARRIER, Toast.WARN, "No leaderboard nearby", "Stand within 8 blocks of one.");
			return;
		}
		data().boards.remove(best);
		ElementHolder h = SHOWN.remove(best);
		TEXT.remove(best);
		if (h != null) h.destroy();
		STORE.saveNow();
		Toast.show(p, Items.GOLDEN_HELMET, Toast.INFO, "Leaderboard removed", kind(best).title);
	}

	static void clearAll() {
		SHOWN.values().forEach(ElementHolder::destroy);
		SHOWN.clear();
		TEXT.clear();
	}

	/** The admin window part: pick what the new board ranks. */
	static void placeWindow(ServerPlayer p) {
		Window w = Window.of("Place a leaderboard").text(Ui.text("It appears in front of you and shows the top ten. "
				+ "Everybody can see it.", ChatFormatting.GRAY));
		Map<Kind, String> tips = new LinkedHashMap<>();
		tips.put(Kind.PLAYTIME, "Who played the most");
		tips.put(Kind.POINTS, "Achievement points (Collector)");
		tips.put(Kind.MOBS, "Hostile and other mobs");
		tips.put(Kind.WALKED, "Walking and sprinting");
		for (var e : tips.entrySet()) {
			if (e.getKey() == Kind.POINTS && Services.progress().isEmpty()) continue;
			w.button(e.getKey().title, e.getValue(), pl -> {
				if (Ui.isOp(pl)) place(pl, e.getKey());
			});
		}
		w.button("Remove the nearest", "Within 8 blocks", pl -> {
			if (Ui.isOp(pl)) removeNearest(pl);
		});
		w.done().show(p);
	}
}
