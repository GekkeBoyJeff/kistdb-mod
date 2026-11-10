package nl.libreria.teleports;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Libreria;
import nl.libreria.core.Notify;
import nl.libreria.core.Players;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Window;
import nl.libreria.core.gui.Toast;

/**
 * For operators, from the server settings (Teleports › Players' homes &amp; access): everyone who has homes, extra
 * homes or no teleport asks. Per player: extra homes on top of the server's number (or fewer), whether they may ask
 * to teleport, and their homes (rename, go there, delete one or all).
 */
public final class TeleportsAdmin {
	private static final int EXTRA_MIN = -30, EXTRA_MAX = 50;

	private TeleportsAdmin() {
	}

	private static boolean allowed(ServerPlayer p) {
		if (Ui.isOp(p)) return true;
		Toast.show(p, Items.BARRIER, Toast.BAD, "Operators only", "Everyone's homes are for admins.");
		return false;
	}

	private static String name(MinecraftServer server, UUID id) {
		Players.Known k = Players.get(server, id);
		return k != null ? k.name() : "Someone";
	}

	private static String homes(int n) {
		return n == 1 ? "1 home" : n + " homes";
	}

	/** Everyone who has played here and has homes, extra homes or no asks; most homes first. */
	public static void players(ServerPlayer admin) {
		if (!allowed(admin)) return;
		MinecraftServer server = admin.level().getServer();
		record Row(UUID id, String name, TeleportsMod.Data d) {
		}
		List<Row> rows = new ArrayList<>();
		for (Players.Known k : Players.all(server)) {
			TeleportsMod.Data d = TeleportsMod.PLAYERS.get(k.id());
			if (d.homes.isEmpty() && d.extraHomes == 0 && !d.noAsks) continue;
			rows.add(new Row(k.id(), k.name(), d));
		}
		rows.sort(Comparator.comparingInt((Row r) -> -r.d().homes.size()).thenComparing(r -> r.name().toLowerCase(java.util.Locale.ROOT)));
		Window w = Window.path("Server settings", "Teleports", "Players").wide();
		w.text(Ui.text(rows.isEmpty() ? "Nobody has a home yet. To give someone extra homes, type their name."
				: "Everyone with homes, most first. Click someone for their homes, extra homes and whether they may ask to "
						+ "teleport; someone else: type their name.", ChatFormatting.GRAY));
		w.field("who", Ui.text("Someone else, by name", ChatFormatting.WHITE), "", 16);
		w.button(Ui.text("Open the name above ▸", ChatFormatting.AQUA), null, (p, v) -> {
			String n = v.text("who", "").strip();
			Players.Known k = n.isEmpty() ? null : Players.byName(server, n);
			if (k == null) {
				Toast.show(p, Items.BARRIER, Toast.WARN, "Nobody called " + (n.isEmpty() ? "that" : n), "Only players who were here before.");
				players(p);
				return;
			}
			player(p, k.id());
		});
		for (Row r : rows) {
			int max = TeleportsMod.maxHomes(r.id());
			MutableComponent label = Ui.text(r.name(), Asks.GOLD).append(Ui.text(" · " + r.d().homes.size() + " of " + homes(max), ChatFormatting.WHITE));
			if (r.d().noAsks) label.append(Ui.text(" · no asks", ChatFormatting.RED));
			w.button(label, null, (p, v) -> player(p, r.id()));
		}
		w.back(p -> nl.libreria.core.gui.AdminWindow.open(p, TeleportsMod.MODULE)).show(admin);
	}

	/** One player: extra homes, teleport asks, their homes. */
	public static void player(ServerPlayer admin, UUID id) {
		if (!allowed(admin)) return;
		MinecraftServer server = admin.level().getServer();
		String name = name(server, id);
		TeleportsMod.Data d = TeleportsMod.PLAYERS.get(id);
		int max = TeleportsMod.maxHomes(id);
		Window w = Window.path("Teleports", "Players", name).wide();
		w.text(Ui.text(name + " has " + d.homes.size() + " of " + homes(max) + " (" + TeleportsMod.HOME_COUNT.get() + " for everyone"
				+ (d.extraHomes == 0 ? "" : ", " + (d.extraHomes > 0 ? "+" : "") + d.extraHomes + " from an operator") + ")."
				+ (d.homes.isEmpty() ? "" : " Click a home to rename it, go there or delete it."), ChatFormatting.GRAY));
		w.slider("extra", Ui.text("Extra homes for " + name, ChatFormatting.WHITE), EXTRA_MIN, EXTRA_MAX, 1, d.extraHomes);
		w.toggle("asks", Ui.text("May ask to teleport (/tpa)", ChatFormatting.WHITE), !d.noAsks);
		w.button(Ui.text("✔ Save", ChatFormatting.GREEN), Ui.text("Extra homes and teleport asks", ChatFormatting.GRAY), (p, v) -> {
			if (!allowed(p)) return;
			int extra = Math.max(EXTRA_MIN, Math.min(EXTRA_MAX, v.integer("extra", d.extraHomes)));
			boolean asks = v.bool("asks", !d.noAsks);
			if (extra != d.extraHomes || asks == d.noAsks) {
				d.extraHomes = extra;
				d.noAsks = !asks;
				TeleportsMod.PLAYERS.markDirty(id);
				Libreria.LOG.info("{} set {}'s extra homes to {} and teleport asks {}", p.getName().getString(), name, extra, asks ? "on" : "off");
				Toast.show(p, Items.ENDER_PEARL, Toast.GOOD, name + ": " + homes(TeleportsMod.maxHomes(id)), asks ? "May ask to teleport" : "No teleport asks");
			}
			player(p, id);
		});
		for (TeleportsMod.Home h : d.homes) {
			boolean fav = h.name.equals(Homes.favourite(d));
			w.button(Ui.text((fav ? "★ " : "") + h.name, ChatFormatting.WHITE).append(Ui.text(" · " + Homes.world(h.dim), ChatFormatting.GRAY)),
					Ui.text("At " + (int) Math.floor(h.x) + " " + (int) Math.floor(h.y) + " " + (int) Math.floor(h.z), ChatFormatting.GRAY),
					(p, v) -> home(p, id, h.name));
		}
		if (!d.homes.isEmpty()) {
			w.button(Ui.text("Delete all of " + name + "'s homes", ChatFormatting.RED), null, (p, v) -> {
				Window c = Window.path(name, "Delete all homes");
				c.text(Ui.text("Delete all " + homes(d.homes.size()) + " of " + name + "? This can't be undone.", ChatFormatting.GRAY));
				c.button(Ui.text("Delete all", ChatFormatting.RED), null, (q, vv) -> {
					if (!allowed(q)) return;
					int n = d.homes.size();
					d.homes.clear();
					d.favourite = "";
					TeleportsMod.PLAYERS.markDirty(id);
					Libreria.LOG.info("{} deleted all {} homes of {}", q.getName().getString(), n, name);
					tell(server, id, "An operator deleted your homes", homes(n) + " gone · /sethome for a new one");
					player(q, id);
				});
				c.cancel(q -> player(q, id)).show(p);
			});
		}
		w.back(TeleportsAdmin::players).show(admin);
	}

	/** One home of someone: rename it, go there, delete it. */
	static void home(ServerPlayer admin, UUID id, String homeName) {
		if (!allowed(admin)) return;
		MinecraftServer server = admin.level().getServer();
		String name = name(server, id);
		TeleportsMod.Data d = TeleportsMod.PLAYERS.get(id);
		TeleportsMod.Home h = Homes.find(d, homeName);
		if (h == null) {
			player(admin, id);
			return;
		}
		Window w = Window.path("Players", name, h.name).wide();
		w.text(Ui.text(name + "'s home at " + (int) Math.floor(h.x) + " " + (int) Math.floor(h.y) + " " + (int) Math.floor(h.z) + " in the "
				+ Homes.world(h.dim) + ", set " + Players.ago(h.set).toLowerCase(java.util.Locale.ROOT) + ".", ChatFormatting.GRAY));
		w.field("name", Ui.text("Name", ChatFormatting.WHITE), h.name, 24);
		w.button(Ui.text("✔ Save the name", ChatFormatting.GREEN), null, (p, v) -> {
			String clean = Homes.clean(v.text("name", h.name));
			if (clean != null && !clean.equals(h.name) && Homes.find(d, clean) == null) {
				if (h.name.equals(d.favourite)) d.favourite = clean;
				Libreria.LOG.info("{} renamed {}'s home {} to {}", p.getName().getString(), name, h.name, clean);
				h.name = clean;
				TeleportsMod.PLAYERS.markDirty(id);
			}
			home(p, id, h.name);
		});
		w.button(Ui.text("Go there", ChatFormatting.WHITE), null, (p, v) -> {
			ServerLevel level = Travel.level(server, h.dim);
			if (level != null) Travel.now(p, new Travel.Target(level, new Vec3(h.x, h.y, h.z), h.yaw, h.pitch), Ui.text(name + "'s " + h.name, Asks.GOLD));
		});
		w.button(Ui.text("Delete", ChatFormatting.RED), null, (p, v) -> {
			if (!allowed(p)) return;
			d.homes.remove(h);
			if (h.name.equals(d.favourite)) d.favourite = d.homes.isEmpty() ? "" : d.homes.getFirst().name;
			TeleportsMod.PLAYERS.markDirty(id);
			Libreria.LOG.info("{} deleted {}'s home {}", p.getName().getString(), name, h.name);
			tell(server, id, "An operator deleted your home " + h.name, "/sethome for a new one");
			player(p, id);
		});
		w.back(p -> player(p, id)).show(admin);
	}

	private static void tell(MinecraftServer server, UUID id, String title, String sub) {
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		if (p != null) Notify.warn(p, Ui.item("red_bed"), title, sub);
	}

	// ---------------------------------------------------------------- for the screenshots

	/** For the screenshots: a few homes for this player (and for a stand-in, Anna). */
	public static void debugHomes(ServerPlayer p) {
		TeleportsMod.Data d = TeleportsMod.PLAYERS.get(p);
		d.homes.clear();
		String[][] homes = {{"Home", "minecraft:overworld", "40", "-60", "120"}, {"Farm", "minecraft:overworld", "-900", "-60", "300"},
				{"Nether hub", "minecraft:the_nether", "12", "70", "-8"}};
		for (String[] h : homes) {
			TeleportsMod.Home home = new TeleportsMod.Home();
			home.name = h[0];
			home.dim = h[1];
			home.x = Double.parseDouble(h[2]);
			home.y = Double.parseDouble(h[3]);
			home.z = Double.parseDouble(h[4]);
			home.set = System.currentTimeMillis() - 3 * 86_400_000L;
			d.homes.add(home);
		}
		d.favourite = "Home";
		d.extraHomes = 2;
		TeleportsMod.PLAYERS.markDirty(p);
	}

	/** For the screenshots: a stand-in asks this player to let them come over. */
	public static void debugAsk(ServerPlayer to, String from) {
		var fake = net.fabricmc.fabric.api.entity.FakePlayer.get(to.level(), new com.mojang.authlib.GameProfile(
				UUID.nameUUIDFromBytes(("tp:" + from).getBytes(java.nio.charset.StandardCharsets.UTF_8)), from));
		fake.setPos(to.getX() + 40, to.getY(), to.getZ());
		Asks.ask(fake, to, false);
	}

	/** For the screenshots: the window of teleport asks. */
	public static void debugWindow(ServerPlayer p) {
		TeleportWindow.open(p);
	}

	/** For the screenshots: the homes list. */
	public static void debugHomesWindow(ServerPlayer p) {
		Homes.open(p);
	}

	public static void debugClear() {
		Asks.clearAll();
	}
}
