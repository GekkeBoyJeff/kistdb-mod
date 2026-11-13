package nl.libreria.waystones;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import nl.libreria.core.Domain;
import nl.libreria.core.Notify;
import nl.libreria.core.PlaceList;
import nl.libreria.core.Players;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Window;

/** A waystone's settings (its owner, or an operator), and the operators' list of every waystone. */
final class WaystonesWindows {
	private WaystonesWindows() {
	}

	/** A name as typed: trimmed, no colour codes, at most 32 characters. */
	static String clean(String s) {
		String t = s.replaceAll("§.", "").strip();
		return t.length() > 32 ? t.substring(0, 32).strip() : t;
	}

	private static String where(Stones.Stone s) {
		return Stones.world(s.dim) + " · " + s.x + " " + s.y + " " + s.z;
	}

	private static String whose(Stones.Stone s) {
		return s.village ? "a village's" : s.owner == null ? "nobody's" : s.ownerName + "'s";
	}

	// ---------------------------------------------------------------- one waystone

	/** Shift + right-click on your waystone (or Settings in the list): its name, and open or private. */
	static void settings(ServerPlayer p, Stones.Stone s) {
		settings(p, s, false);
	}

	private static void settings(ServerPlayer p, Stones.Stone s, boolean fromAdmin) {
		boolean op = Ui.isOp(p);
		boolean names = Stones.owns(p, s) || op;
		if (!names && !Stones.mayShare(p, s)) return;
		if (WaystoneCanvas.available(p)) {
			WaystoneCanvas.one(p, s, fromAdmin);
			return;
		}
		Window w = fromAdmin ? Window.path("Server settings", "Waystones", s.name).wide() : Window.of(Domain.PLACES, "Waystones", s.name).wide();
		String made = s.village ? "Placed with its village." : s.owner == null ? "Placed by the server." : "Placed by " + s.ownerName
				+ (s.made > 0 ? ", " + Players.ago(s.made).toLowerCase(Locale.ROOT) : "") + ".";
		w.text(Ui.text(where(s) + ". " + made, ChatFormatting.GRAY));
		if (names) w.field("name", Ui.text("Name", ChatFormatting.WHITE), s.name, 32);
		w.toggle("shared", Ui.text("On everyone's list (also who never found it)", ChatFormatting.WHITE), s.shared);
		if (names && !s.village && s.owner != null) {
			w.toggle("secret", Ui.text("Private: only " + (Stones.owns(p, s) ? "you" : s.ownerName) + " and the people "
					+ (Stones.owns(p, s) ? "you trust" : "they trust"), ChatFormatting.WHITE), s.secret);
		}
		w.button(Ui.text("✔ Save", ChatFormatting.GREEN), null, (pl, v) -> {
			if (names) {
				String name = clean(v.text("name", s.name));
				if (!name.isEmpty()) s.name = name;
				if (!s.village && s.owner != null) s.secret = v.bool("secret", s.secret);
			}
			s.shared = v.bool("shared", s.shared);
			// on everyone's list is open to everyone: it can't be private as well
			if (s.shared) s.secret = false;
			Stones.STORE.markDirty();
			Notify.done(pl, WaystonesMod.WAYSTONE_ITEM, "Saved: " + s.name, s.secret ? "Private: only the owner and who they trust."
					: s.shared ? "On everyone's list." : "Open to everyone who found it.");
			if (fromAdmin) admin(pl);
		});
		if (s.grand && Stones.owns(p, s)) {
			w.button(Ui.text("Take it down", ChatFormatting.RED), Ui.text("The waystone and the nether star come back", ChatFormatting.GRAY), (pl, v) -> {
				Window c = Window.path(s.name, "Take it down");
				c.text(Ui.text("Take " + s.name + " down? The shrine and its floor go, and it's off everyone's list. You get the waystone and the "
						+ "nether star back; the diamonds and the sculk are gone.", ChatFormatting.GRAY));
				c.button(Ui.text("Take it down", ChatFormatting.RED), null, (q, vv) -> {
					if (s.grand && Grand.mayTakeDown(q, s)) Grand.takeDown(s, Stones.level(q.level().getServer(), s.dim), q);
				});
				c.cancel(q -> settings(q, s, fromAdmin)).show(pl);
			});
		}
		if (op) {
			w.button(Ui.text("Go there", ChatFormatting.WHITE), Ui.text("Free, as an operator", ChatFormatting.GRAY), (pl, v) -> {
				ServerLevel level = Stones.check(pl, s);
				if (level != null) Stones.go(pl, level, s);
			});
			w.button(Ui.text("Remove it", ChatFormatting.RED), Ui.text("Breaks the waystone and its altar", ChatFormatting.GRAY), (pl, v) -> {
				Window c = Window.path(s.name, "Remove");
				c.text(Ui.text("Remove " + s.name + " (" + whose(s) + ")? It's gone from everyone's list; nothing drops.", ChatFormatting.GRAY));
				c.button(Ui.text("Remove", ChatFormatting.RED), null, (q, vv) -> {
					remove(q, s);
					if (fromAdmin) admin(q);
				});
				c.cancel(q -> settings(q, s, fromAdmin)).show(pl);
			});
		}
		if (fromAdmin) w.back(WaystonesWindows::admin).show(p);
		else w.back(pl -> PlaceList.open(pl, WaystonePlaces.KIND)).show(p);
	}

	/** Takes the waystone away (and its record, even when its block is gone already). */
	static void remove(ServerPlayer p, Stones.Stone s) {
		ServerLevel level = Stones.level(p.level().getServer(), s.dim);
		if (s.grand) {
			Grand.takeDown(s, level, null);
			Notify.done(p, Items.BARRIER, "Removed: " + s.name, where(s));
			return;
		}
		if (level != null) Stones.removed(level, s.pos(), "removed by " + p.getName().getString());
		if (level != null && level.getBlockState(s.pos()).is(WaystonesMod.WAYSTONE)) {
			level.setBlock(s.pos().above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(s.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		Notify.done(p, Items.BARRIER, "Removed: " + s.name, where(s));
	}

	// ---------------------------------------------------------------- all of them (operators)

	static void admin(ServerPlayer p) {
		if (!Ui.isOp(p)) return;
		if (WaystoneCanvas.available(p)) {
			WaystoneCanvas.All.open(p, 0);
			return;
		}
		List<Stones.Stone> all = new ArrayList<>(Stones.list());
		all.sort(Comparator.comparing((Stones.Stone s) -> s.village).thenComparing(s -> s.name.toLowerCase(Locale.ROOT)));
		long villages = all.stream().filter(s -> s.village).count();
		Window w = Window.path("Server settings", "Waystones", "All waystones").wide();
		w.text(Ui.text(all.isEmpty() ? "No waystones yet. New villages get one by their bell; players can craft their own."
				: all.size() + (all.size() == 1 ? " waystone: " : " waystones: ") + (all.size() - villages) + " placed by players, " + villages
						+ " in villages. Click one to rename it, make it private, go there or remove it.", ChatFormatting.GRAY));
		for (Stones.Stone s : all) {
			MutableComponent label = Ui.text(s.name, 0xC79BF0).append(Ui.text(" · " + whose(s), ChatFormatting.WHITE));
			if (s.secret) label.append(Ui.text(" · private", ChatFormatting.GRAY));
			w.button(label, Ui.text(where(s), ChatFormatting.GRAY), (pl, v) -> settings(pl, s, true));
		}
		w.back(pl -> nl.libreria.core.gui.AdminWindow.open(pl, WaystonesMod.MODULE)).show(p);
	}
}
