package nl.libreria.claims;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import nl.libreria.core.Guide;
import nl.libreria.core.Libreria;
import nl.libreria.core.Notify;
import nl.libreria.core.Players;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Window;
import nl.libreria.core.gui.Toast;
import org.jetbrains.annotations.Nullable;

/**
 * Everyone's land, for operators (from the server settings: Claims › Players' land). Per player: how many chunks they
 * may claim and extra chunks on top (or fewer), and their land in pieces (chunks of theirs that touch are one piece):
 * rename a piece, be shown the way or go there, release a piece or all of their land. Every release asks first; the
 * owner gets a card when they're online, and everything is logged.
 */
public final class ClaimsAdmin {
	private static final int EXTRA_MIN = -64, EXTRA_MAX = 256;

	private ClaimsAdmin() {
	}

	/** One piece of someone's land: their chunks in one world that touch each other. */
	record Piece(UUID owner, String dim, List<ChunkPos> chunks, String name) {
		/** The chunk nearest the middle of the piece. */
		ChunkPos middle() {
			double x = 0, z = 0;
			for (ChunkPos c : this.chunks) {
				x += c.x();
				z += c.z();
			}
			double mx = x / this.chunks.size(), mz = z / this.chunks.size();
			return this.chunks.stream().min(Comparator.comparingDouble(c -> (c.x() - mx) * (c.x() - mx) + (c.z() - mz) * (c.z() - mz))).orElseThrow();
		}

		String title() {
			return this.name.isEmpty() ? "No name" : this.name;
		}

		String size() {
			return this.chunks.size() == 1 ? "1 chunk" : this.chunks.size() + " chunks";
		}
	}

	/** Someone with land (or with extra chunks): how much they hold, in how many pieces. */
	record Holder(UUID id, String name, int chunks, int pieces) {
	}

	// ---------------------------------------------------------------- reading the land

	/** All land of one player, in pieces, biggest first. */
	static List<Piece> pieces(UUID owner) {
		Map<String, Map<Long, ClaimsMod.Claim>> byDim = new LinkedHashMap<>();
		for (Map.Entry<String, ClaimsMod.Claim> e : ClaimsMod.STORE.get().claims.entrySet()) {
			if (!e.getValue().owner.equals(owner)) continue;
			String[] k = e.getKey().split("\\|");
			if (k.length != 3) continue;
			try {
				byDim.computeIfAbsent(k[0], d -> new HashMap<>()).put(pack(Integer.parseInt(k[1]), Integer.parseInt(k[2])), e.getValue());
			} catch (NumberFormatException ignored) {
				// a key we didn't write
			}
		}
		List<Piece> out = new ArrayList<>();
		for (Map.Entry<String, Map<Long, ClaimsMod.Claim>> d : byDim.entrySet()) {
			Map<Long, ClaimsMod.Claim> left = new HashMap<>(d.getValue());
			while (!left.isEmpty()) {
				long start = left.keySet().iterator().next();
				List<ChunkPos> chunks = new ArrayList<>();
				Map<String, Integer> names = new HashMap<>();
				ArrayDeque<Long> todo = new ArrayDeque<>();
				todo.add(start);
				ClaimsMod.Claim first = left.remove(start);
				if (first.area != null && !first.area.isEmpty()) names.merge(first.area, 1, Integer::sum);
				while (!todo.isEmpty()) {
					long at = todo.poll();
					ChunkPos c = new ChunkPos((int) (at >> 32), (int) at);
					chunks.add(c);
					for (ChunkPos n : ClaimsMod.neighbours(c)) {
						ClaimsMod.Claim cl = left.remove(pack(n.x(), n.z()));
						if (cl == null) continue;
						todo.add(pack(n.x(), n.z()));
						if (cl.area != null && !cl.area.isEmpty()) names.merge(cl.area, 1, Integer::sum);
					}
				}
				String name = names.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
				out.add(new Piece(owner, d.getKey(), chunks, name));
			}
		}
		out.sort(Comparator.comparingInt((Piece p) -> -p.chunks().size()).thenComparing(Piece::title));
		return out;
	}

	private static long pack(int x, int z) {
		return (long) x << 32 | (z & 0xFFFFFFFFL);
	}

	/** The piece of this owner's land in that world that holds this chunk, or null when it's gone. */
	static @Nullable Piece piece(UUID owner, String dim, ChunkPos any) {
		for (Piece p : pieces(owner)) {
			if (p.dim().equals(dim) && p.chunks().contains(any)) return p;
		}
		return null;
	}

	/** Everyone with land, and everyone an operator gave extra chunks; most land first. */
	static List<Holder> holders() {
		Map<UUID, Integer> chunks = new HashMap<>();
		Map<UUID, String> names = new HashMap<>();
		for (ClaimsMod.Claim c : ClaimsMod.STORE.get().claims.values()) {
			chunks.merge(c.owner, 1, Integer::sum);
			if (c.ownerName != null && !c.ownerName.isEmpty()) names.put(c.owner, c.ownerName);
		}
		Set<UUID> all = new HashSet<>(chunks.keySet());
		if (ClaimsMod.STORE.get().extra != null) all.addAll(ClaimsMod.STORE.get().extra.keySet());
		List<Holder> out = new ArrayList<>();
		for (UUID id : all) {
			String name = names.containsKey(id) ? names.get(id) : nameOf(id);
			out.add(new Holder(id, name, chunks.getOrDefault(id, 0), chunks.containsKey(id) ? pieces(id).size() : 0));
		}
		out.sort(Comparator.comparingInt((Holder h) -> -h.chunks()).thenComparing(h -> h.name().toLowerCase()));
		return out;
	}

	static String nameOf(UUID id) {
		MinecraftServer server = Libreria.server();
		Players.Known k = server == null ? null : Players.get(server, id);
		if (k != null) return k.name();
		for (ClaimsMod.Claim c : ClaimsMod.STORE.get().claims.values()) {
			if (c.owner.equals(id) && c.ownerName != null && !c.ownerName.isEmpty()) return c.ownerName;
		}
		return "Someone";
	}

	static String world(String dim) {
		return switch (dim) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
		};
	}

	private static @Nullable ServerLevel level(MinecraftServer server, String dim) {
		for (ServerLevel l : server.getAllLevels()) {
			if (l.dimension().identifier().toString().equals(dim)) return l;
		}
		return null;
	}

	private static String chunks(int n) {
		return n == 1 ? "1 chunk" : n + " chunks";
	}

	// ---------------------------------------------------------------- changing it

	/** Renames a piece ("" takes the name off). */
	static void rename(Piece p, String name) {
		for (ChunkPos c : p.chunks()) {
			ClaimsMod.Claim cl = ClaimsMod.STORE.get().claims.get(p.dim() + "|" + c.x() + "|" + c.z());
			if (cl != null) cl.area = name;
		}
		ClaimsMod.STORE.saveNow();
		changed();
	}

	/** Releases a piece: its chunks are free land again. */
	static int release(Piece p) {
		int n = 0;
		for (ChunkPos c : p.chunks()) {
			if (ClaimsMod.STORE.get().claims.remove(p.dim() + "|" + c.x() + "|" + c.z()) != null) n++;
		}
		ClaimsMod.STORE.saveNow();
		changed();
		return n;
	}

	/** Releases all of someone's land. */
	static int releaseAll(UUID owner) {
		int n = 0;
		for (var it = ClaimsMod.STORE.get().claims.values().iterator(); it.hasNext(); ) {
			if (it.next().owner.equals(owner)) {
				it.remove();
				n++;
			}
		}
		ClaimsMod.STORE.saveNow();
		changed();
		return n;
	}

	/** Land maps that are open, and the "you walk into…" lines, follow the change. */
	private static void changed() {
		ClaimsMod.forgetEntered();
		MinecraftServer server = Libreria.server();
		if (server != null) for (ServerPlayer p : server.getPlayerList().getPlayers()) ClaimView.refresh(p);
	}

	/** The owner hears it on a card when they're online. */
	private static void tell(UUID owner, String title, String subtitle) {
		MinecraftServer server = Libreria.server();
		ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(owner);
		if (p != null) Notify.warn(p, Items.GOLDEN_SHOVEL, title, subtitle);
	}

	// ---------------------------------------------------------------- windows

	private static boolean allowed(ServerPlayer p) {
		if (Ui.isOp(p)) return true;
		Toast.show(p, Items.BARRIER, Toast.BAD, "Operators only", "Everyone's land is for admins.");
		return false;
	}

	/** Everyone with land, most first. */
	public static void players(ServerPlayer admin) {
		if (!allowed(admin)) return;
		List<Holder> holders = holders();
		Window w = Window.path("Server settings", "Claims", "Players' land");
		w.text(Ui.text(holders.isEmpty() ? "Nobody has claimed land yet. Give someone extra chunks by their name below."
				: "Everyone who has land, most first. Click someone for their land and how much they may claim.", ChatFormatting.GRAY));
		for (Holder h : holders) {
			int budget = ClaimsMod.budget(h.id());
			MutableComponent label = Ui.text(h.name(), 0xFFD966).append(Ui.text(" · " + h.chunks() + " of " + chunks(budget)
					+ (h.pieces() > 0 ? " · " + h.pieces() + (h.pieces() == 1 ? " piece" : " pieces") : ""), ChatFormatting.WHITE));
			int extra = ClaimsMod.extra(h.id());
			String tip = extra == 0 ? "Their land, and how much they may claim" : (extra > 0 ? "+" : "") + extra + " chunks from an operator";
			w.button(label, Ui.text(tip, ChatFormatting.GRAY), (p, v) -> player(p, h.id()));
		}
		w.field("who", Ui.text("Someone else (their name)", ChatFormatting.WHITE), "", 16);
		w.button(Ui.text("Open their land ▸", ChatFormatting.AQUA), Ui.text("For someone who has no land yet", ChatFormatting.GRAY), (p, v) -> {
			String name = v.text("who", "").strip();
			Players.Known k = name.isEmpty() ? null : Players.byName(p.level().getServer(), name);
			if (k == null) {
				Toast.show(p, Items.BARRIER, Toast.WARN, "Nobody called " + (name.isEmpty() ? "that" : name), "Only players who were here before.");
				players(p);
				return;
			}
			player(p, k.id());
		});
		w.back(p -> nl.libreria.core.gui.AdminWindow.open(p, ClaimsMod.MODULE)).show(admin);
	}

	/** One player: how much they may claim (extra chunks on top), and their land in pieces. */
	public static void player(ServerPlayer admin, UUID id) {
		if (!allowed(admin)) return;
		String name = nameOf(id);
		List<Piece> pieces = pieces(id);
		int owned = ClaimsMod.owned(id), extra = ClaimsMod.extra(id);
		Window w = Window.path("Claims", "Players' land", name);
		w.text(Ui.text(budgetSentence(admin.level().getServer(), id, name), ChatFormatting.GRAY));
		w.slider("extra", Ui.text("Extra chunks for " + name, ChatFormatting.WHITE), EXTRA_MIN, EXTRA_MAX, 1, extra);
		w.button(Ui.text("✔ Save", ChatFormatting.GREEN), Ui.text("Give " + name + " these extra chunks", ChatFormatting.GRAY), (p, v) -> {
			if (!allowed(p)) return;
			int now = Math.max(EXTRA_MIN, Math.min(EXTRA_MAX, v.integer("extra", extra)));
			if (now != ClaimsMod.extra(id)) {
				ClaimsMod.setExtra(id, now);
				Libreria.LOG.info("{} set the extra chunks of {} to {}", p.getName().getString(), name, now);
				Toast.show(p, Items.GOLDEN_SHOVEL, Toast.GOOD, name + " may claim " + chunks(ClaimsMod.budget(id)),
						(now > 0 ? "+" : "") + now + " from you · " + owned + " in use");
			}
			player(p, id);
		});
		w.text(Ui.text(pieces.isEmpty() ? name + " has no land." : name + "'s land: " + pieces.size() + (pieces.size() == 1 ? " piece" : " pieces")
				+ " (chunks that touch are one piece).", ChatFormatting.GRAY));
		for (Piece pc : pieces) {
			ChunkPos m = pc.middle();
			MutableComponent label = Ui.text(pc.title(), pc.name().isEmpty() ? 0x9A9EA8 : 0xFFFFFF)
					.append(Ui.text(" · " + pc.size() + " · ", ChatFormatting.WHITE)).append(Ui.text(world(pc.dim()), ChatFormatting.GRAY));
			w.button(label, Ui.text("Around " + (m.getMinBlockX() + 8) + ", " + (m.getMinBlockZ() + 8) + " · rename, go there, release",
					ChatFormatting.GRAY), (p, v) -> piece(p, id, pc.dim(), pc.chunks().getFirst()));
		}
		if (!pieces.isEmpty()) {
			w.button(Ui.text("Release all of " + name + "'s land", ChatFormatting.RED), Ui.text(chunks(owned) + " become free land", ChatFormatting.GRAY),
					(p, v) -> confirmAll(p, id));
		}
		w.back(ClaimsAdmin::players).show(admin);
	}

	/** "Anna may claim 18 chunks: 4 to start, +9 for 18 hours played, +5 for 200 points, +10 from you." */
	static String budgetSentence(MinecraftServer server, UUID id, String name) {
		int extra = ClaimsMod.extra(id);
		String from = extra == 0 ? "" : ", " + (extra > 0 ? "+" : "") + extra + " from an operator";
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		if (online == null) {
			return name + " may claim " + chunks(ClaimsMod.budget(id)) + ": " + ClaimsMod.earned(id) + " earned when last here" + from
					+ ". In use: " + ClaimsMod.owned(id) + ".";
		}
		int hours = ClaimsMod.hours(online), points = ClaimsMod.points(online);
		int byHours = hours / ClaimsMod.HOURS.get(), byPoints = points / ClaimsMod.POINTS.get();
		StringBuilder s = new StringBuilder(name + " may claim " + chunks(ClaimsMod.budget(online)) + ": " + ClaimsMod.START.get() + " to start, +"
				+ byHours + " for " + hours + (hours == 1 ? " hour" : " hours") + " played");
		if (nl.libreria.core.Services.progress().isPresent()) s.append(", +").append(byPoints).append(" for ").append(points).append(" points");
		if (ClaimsMod.START.get() + byHours + byPoints > ClaimsMod.LIMIT.get()) s.append(" (at most ").append(ClaimsMod.LIMIT.get()).append(")");
		return s.append(from).append(". In use: ").append(ClaimsMod.owned(id)).append(".").toString();
	}

	/** One piece: rename it, be shown the way or go there, release it. */
	public static void piece(ServerPlayer admin, UUID owner, String dim, ChunkPos any) {
		if (!allowed(admin)) return;
		Piece pc = piece(owner, dim, any);
		String name = nameOf(owner);
		if (pc == null) {
			Toast.show(admin, Items.BARRIER, Toast.WARN, "That land is gone", "It changed while the window was open.");
			player(admin, owner);
			return;
		}
		ChunkPos m = pc.middle();
		int x = m.getMinBlockX() + 8, z = m.getMinBlockZ() + 8;
		Window w = Window.path("Players' land", name, pc.title());
		w.text(Ui.text((pc.chunks().size() == 1 ? "1 chunk" : pc.chunks().size() + " chunks that touch each other") + ", around " + x + ", " + z
				+ " in the " + world(dim) + ". " + name + "'s, " + (pc.name().isEmpty() ? "without a name." : "called " + pc.name() + "."), ChatFormatting.GRAY));
		w.field("name", Ui.text("Name", ChatFormatting.WHITE), pc.name(), 32);
		w.button(Ui.text("✔ Save the name", ChatFormatting.GREEN), Ui.text("Empty: no name", ChatFormatting.GRAY), (p, v) -> {
			if (!allowed(p)) return;
			String raw = v.text("name", "").strip();
			String clean = raw.isEmpty() ? "" : ClaimsMod.cleanName(raw);
			if (clean == null) {
				Toast.show(p, Items.NAME_TAG, Toast.WARN, "Pick another name", "A name needs 2 to 32 letters.");
				piece(p, owner, dim, any);
				return;
			}
			Piece now = piece(owner, dim, any);
			if (now != null && !clean.equals(now.name())) {
				rename(now, clean);
				Libreria.LOG.info("{} renamed {}'s land at {}, {} ({}) to \"{}\"", p.getName().getString(), name, x, z, dim, clean);
				Toast.show(p, Items.NAME_TAG, Toast.GOOD, clean.isEmpty() ? "Name taken off" : "Called " + clean, name + "'s land · " + now.size());
				tell(owner, clean.isEmpty() ? "Your land lost its name" : "Your land is called " + clean, "An operator changed it");
			}
			piece(p, owner, dim, any);
		});
		ServerLevel level = level(admin.level().getServer(), dim);
		if (level != null && level == admin.level()) {
			w.button(Ui.text("Show the way", ChatFormatting.WHITE), Ui.text("A beam to it", ChatFormatting.GRAY), (p, v) -> {
				double y = p.getY();
				Guide.toBox(p, level, new AABB(m.getMinBlockX(), y - 1, m.getMinBlockZ(), m.getMinBlockX() + 16, y + 4, m.getMinBlockZ() + 16), null,
						Ui.text(pc.title(), ChatFormatting.WHITE), 0, 0);
			});
		}
		if (level != null) {
			w.button(Ui.text("Go there", ChatFormatting.WHITE), Ui.text("On the ground in its middle", ChatFormatting.GRAY), (p, v) -> {
				if (!allowed(p)) return;
				p.teleportTo(level, x + 0.5, groundAt(level, x, z), z + 0.5, Set.of(), p.getYRot(), p.getXRot(), false);
			});
		}
		w.button(Ui.text("Release this piece (" + pc.size() + ")", ChatFormatting.RED), Ui.text("It becomes free land", ChatFormatting.GRAY),
				(p, v) -> confirmPiece(p, owner, dim, any));
		w.back(p -> player(p, owner)).show(admin);
	}

	/** Where to stand at x, z: on top of the ground; in the Nether the highest open floor under the roof. */
	private static int groundAt(ServerLevel level, int x, int z) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (!level.dimension().identifier().toString().equals("minecraft:the_nether")) return top;
		for (int y = Math.min(top, level.getMinY() + 120); y > level.getMinY() + 1; y--) {
			net.minecraft.core.BlockPos feet = new net.minecraft.core.BlockPos(x, y, z);
			net.minecraft.core.BlockPos floor = feet.below();
			if (open(level, feet) && open(level, feet.above()) && !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) return y;
		}
		return top;
	}

	private static boolean open(ServerLevel level, net.minecraft.core.BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
	}

	private static void confirmPiece(ServerPlayer admin, UUID owner, String dim, ChunkPos any) {
		if (!allowed(admin)) return;
		Piece pc = piece(owner, dim, any);
		if (pc == null) {
			player(admin, owner);
			return;
		}
		String name = nameOf(owner);
		Window w = Window.path(name, pc.title(), "Release");
		w.text(Ui.text("Release " + (pc.name().isEmpty() ? "this piece of " + name + "'s land" : pc.name()) + "? Its " + pc.size()
				+ " become free land again, and " + name + " has them back to claim (here or elsewhere). This can't be undone.", ChatFormatting.GRAY));
		w.button(Ui.text("Release", ChatFormatting.RED), null, (p, v) -> {
			if (!allowed(p)) return;
			Piece now = piece(owner, dim, any);
			if (now != null) {
				int n = release(now);
				Libreria.LOG.info("{} released {} chunks of {}'s land ({}) in {}", p.getName().getString(), n, name, now.title(), dim);
				Toast.show(p, Items.GOLDEN_SHOVEL, Toast.GOOD, "Released " + chunks(n), name + "'s " + (now.name().isEmpty() ? "land" : now.name()));
				Ui.sound(p, SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.6f, 0.9f);
				tell(owner, "An operator released " + (now.name().isEmpty() ? "some of your land" : now.name()), chunks(n) + " are free land again");
			}
			player(p, owner);
		});
		w.cancel(p -> piece(p, owner, dim, any)).show(admin);
	}

	private static void confirmAll(ServerPlayer admin, UUID owner) {
		if (!allowed(admin)) return;
		String name = nameOf(owner);
		int owned = ClaimsMod.owned(owner);
		int count = pieces(owner).size();
		Window w = Window.path("Players' land", name, "Release all");
		w.text(Ui.text("Release all of " + name + "'s land? " + chunks(owned) + " in " + count + (count == 1 ? " piece" : " pieces")
				+ " become free land again. Their trusted players and extra chunks stay. This can't be undone.", ChatFormatting.GRAY));
		w.button(Ui.text("Release all", ChatFormatting.RED), null, (p, v) -> {
			if (!allowed(p)) return;
			int n = releaseAll(owner);
			Libreria.LOG.info("{} released all {} chunks of {}'s land", p.getName().getString(), n, name);
			Toast.show(p, Items.GOLDEN_SHOVEL, Toast.GOOD, "Released " + chunks(n), "All of " + name + "'s land");
			Ui.sound(p, SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.6f, 0.9f);
			tell(owner, "An operator released all your land", chunks(n) + " are free land again");
			player(p, owner);
		});
		w.cancel(p -> player(p, owner)).show(admin);
	}

	// ---------------------------------------------------------------- for the screenshots and tests

	public static final UUID DEBUG_ANNA = UUID.nameUUIDFromBytes("claims:Anna".getBytes(java.nio.charset.StandardCharsets.UTF_8));

	/** Land for the admin windows' screenshots: Anna with three pieces (one in the Nether), Bram with one, Cas with two. */
	public static void debugLand(ServerLevel level) {
		UUID bram = UUID.nameUUIDFromBytes("claims:Bram".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		UUID cas = UUID.nameUUIDFromBytes("claims:Cas".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		for (int i = 0; i < 8; i++) put(level, new ChunkPos(40 + i % 4, -20 + i / 4), DEBUG_ANNA, "Anna", "Oakhaven Farm");
		for (int i = 0; i < 3; i++) put(level, new ChunkPos(-30 + i, 5), DEBUG_ANNA, "Anna", "");
		ServerLevel nether = level(level.getServer(), "minecraft:the_nether");
		if (nether != null) put(nether, new ChunkPos(3, 1), DEBUG_ANNA, "Anna", "Lava Post");
		for (int i = 0; i < 6; i++) put(level, new ChunkPos(60 + i % 3, 30 + i / 3), bram, "Bram", "Bramble Keep");
		put(level, new ChunkPos(-60, -60), cas, "Cas", "");
		put(level, new ChunkPos(-70, -40), cas, "Cas", "");
		ClaimsMod.setExtra(DEBUG_ANNA, 6);
	}

	/** For the screenshots: Anna's biggest piece. */
	public static void debugPiece(ServerPlayer admin) {
		List<Piece> pieces = pieces(DEBUG_ANNA);
		if (!pieces.isEmpty()) piece(admin, DEBUG_ANNA, pieces.getFirst().dim(), pieces.getFirst().chunks().getFirst());
	}

	private static void put(ServerLevel level, ChunkPos c, UUID owner, String name, String area) {
		ClaimsMod.debugClaim(level, c, owner, name);
		ClaimsMod.Claim cl = ClaimsMod.claimAt(level, c);
		if (cl != null) cl.area = area;
	}
}
