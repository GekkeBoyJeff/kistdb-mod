package nl.libreria.teleports;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Domain;
import nl.libreria.core.Guide;
import nl.libreria.core.Notify;
import nl.libreria.core.PlaceList;
import nl.libreria.core.Players;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Window;
import nl.libreria.core.gui.Menu;
import org.jetbrains.annotations.Nullable;

/**
 * Homes: {@code /sethome [name]} (a home here; the same name again moves it), {@code /home} (your favourite home: the
 * first one, or the one you starred), {@code /home <name>}, {@code /delhome <name>}, and a tab under Land &amp; places ›
 * Your places: click a home to go there, right-click for more (show the way, make it your /home, rename, move it here,
 * delete). How many you may have is the server's (Homes per player) plus what an operator gave you.
 */
final class Homes {
	static final String KIND = "homes";

	private Homes() {
	}

	static void register() {
		PlaceList.kind(KIND, "Homes", Ui.item("red_bed"), 5)
				.about("Your homes. Click one to go there.")
				.empty("No homes yet. Stand where you", "live and click New home,", "or type /sethome.")
				.help("/sethome [name]: a home where you stand", "(the same name again moves it).", "/home: to your favourite home (★).",
						"/home <name>: to that one.", "/delhome <name>: delete it.")
				.action(Homes::setButton);
		PlaceList.addSource((p, out) -> {
			if (!TeleportsMod.MODULE.on() || !TeleportsMod.HOMES.get()) return;
			TeleportsMod.Data d = TeleportsMod.PLAYERS.get(p);
			for (TeleportsMod.Home h : d.homes) {
				boolean fav = h.name.equals(favourite(d));
				out.add(PlaceList.place(KIND, (fav ? "★ " : "") + h.name, new ItemStack(icon(h.dim)), h.dim, new Vec3(h.x, h.y, h.z))
						.lore(Ui.kv("At ", (int) Math.floor(h.x) + " " + (int) Math.floor(h.y) + " " + (int) Math.floor(h.z), ChatFormatting.WHITE),
								fav ? Ui.text("★ Your /home", 0xFFD966) : Ui.text("/home " + h.name, ChatFormatting.GRAY))
						.goAnywhere("go there", pl -> go(pl, h.name))
						.details("more (rename, delete…)", pl -> options(pl, h.name)));
			}
		});
	}

	/** Land &amp; places › Homes. */
	static void open(ServerPlayer p) {
		if (!TeleportsMod.HOMES.get()) {
			Notify.warn(p, Items.BARRIER, "No homes here", "Homes are turned off on this server.");
			return;
		}
		PlaceList.open(p, KIND);
	}

	private static Item icon(String dim) {
		if (dim.endsWith("the_nether")) return Ui.item("black_bed");
		if (dim.endsWith("the_end")) return Ui.item("purple_bed");
		return Ui.item("red_bed");
	}

	/** "✚ Set a home here" in the corner of the list: how many you have, and room for one more. */
	private static GuiElementBuilder setButton(ServerPlayer p) {
		TeleportsMod.Data d = TeleportsMod.PLAYERS.get(p);
		int max = TeleportsMod.maxHomes(p.getUUID());
		boolean room = d.homes.size() < max;
		String count = d.homes.size() + " of " + max + (max == 1 ? " home" : " homes");
		GuiElementBuilder b = room
				? Menu.button(Ui.item("red_bed"), "New home", ChatFormatting.GREEN, count, "A home where you stand now;", "you get to name it.")
				: Menu.button(Ui.item("red_bed"), "New home", ChatFormatting.GRAY, count, "No room: move or delete one.");
		Menu.actions(b, room ? "Click: name it and set it" : "Right-click a home: move it here");
		return b.setCallback((i, t, a, g) -> {
			if (room) nameWindow(p);
			else Notify.info(p, Ui.item("red_bed"), "All your homes are set", d.homes.size() + " of " + max + ": move or delete one first.");
		});
	}

	// ---------------------------------------------------------------- the rules

	/** The home /home goes to: the starred one, or the first. */
	static @Nullable String favourite(TeleportsMod.Data d) {
		if (d.homes.isEmpty()) return null;
		for (TeleportsMod.Home h : d.homes) if (h.name.equals(d.favourite)) return h.name;
		return d.homes.getFirst().name;
	}

	static @Nullable TeleportsMod.Home find(TeleportsMod.Data d, String name) {
		for (TeleportsMod.Home h : d.homes) if (h.name.equalsIgnoreCase(name.strip())) return h;
		return null;
	}

	/** A name a player can give: letters, digits, spaces and a few marks; 1 to 24 characters. */
	static @Nullable String clean(@Nullable String raw) {
		if (raw == null) return null;
		String s = raw.replace('§', ' ').replaceAll("[\\p{Cntrl}]", "").replaceAll("\\s+", " ").strip();
		if (s.isEmpty() || s.length() > 24) return null;
		return s;
	}

	/** A name for a new home that isn't taken yet: Home, Home 2, Home 3… */
	static String freeName(TeleportsMod.Data d) {
		if (find(d, "Home") == null) return "Home";
		for (int i = 2; ; i++) if (find(d, "Home " + i) == null) return "Home " + i;
	}

	/** /sethome [name]: a home where you stand; a name you already have moves that home here. */
	static boolean set(ServerPlayer p, @Nullable String raw) {
		if (!allowed(p)) return false;
		TeleportsMod.Data d = TeleportsMod.PLAYERS.get(p);
		String name = raw == null || raw.isBlank() ? (d.homes.isEmpty() ? "Home" : favourite(d)) : clean(raw);
		if (name == null) {
			Notify.warn(p, Items.NAME_TAG, "Pick another name", "A home's name has 1 to 24 letters.");
			return false;
		}
		TeleportsMod.Home h = find(d, name);
		boolean moved = h != null;
		int max = TeleportsMod.maxHomes(p.getUUID());
		if (h == null) {
			if (d.homes.size() >= max) {
				Notify.warn(p, Ui.item("red_bed"), max == 0 ? "No homes on this server" : "All your homes are set",
						max == 0 ? "Ask an operator for one." : d.homes.size() + " of " + max + ": /delhome one, or /sethome <its name> to move it.");
				return false;
			}
			h = new TeleportsMod.Home();
			h.name = name;
			d.homes.add(h);
		}
		place(h, p);
		if (d.homes.size() == 1) d.favourite = h.name;
		TeleportsMod.PLAYERS.markDirty(p);
		boolean fav = h.name.equals(favourite(d));
		Notify.done(p, Ui.item("red_bed"), (moved ? h.name + " moved here" : "Home set: " + h.name),
				(fav ? "/home brings you here" : "/home " + h.name + " brings you here") + " · " + d.homes.size() + " of " + max);
		Ui.sound(p, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 0.4f, 1.4f);
		return true;
	}

	private static void place(TeleportsMod.Home h, ServerPlayer p) {
		h.dim = p.level().dimension().identifier().toString();
		h.x = p.getX();
		h.y = p.getY();
		h.z = p.getZ();
		h.yaw = p.getYRot();
		h.pitch = p.getXRot();
		h.set = System.currentTimeMillis();
	}

	/** /home [name]: to that home, or to your favourite one. */
	static boolean go(ServerPlayer p, @Nullable String name) {
		if (!allowed(p)) return false;
		TeleportsMod.Data d = TeleportsMod.PLAYERS.get(p);
		if (d.homes.isEmpty()) {
			Notify.info(p, Ui.item("red_bed"), "No home yet", "Stand where you live and type /sethome.");
			return false;
		}
		TeleportsMod.Home h = find(d, name == null || name.isBlank() ? favourite(d) : name);
		if (h == null) {
			Notify.warn(p, Ui.item("red_bed"), "No home called " + name, "Your homes: /homes.");
			return false;
		}
		ServerLevel level = Travel.level(p.level().getServer(), h.dim);
		if (level == null) {
			Notify.warn(p, Items.BARRIER, "That world is gone", h.name + " is in a world this server doesn't have.");
			return false;
		}
		return Travel.go(p, new Travel.Target(level, new Vec3(h.x, h.y, h.z), h.yaw, h.pitch), Ui.text(h.name, ChatFormatting.WHITE));
	}

	/** /delhome [name]: deletes it (without a name: asks which, in the list). */
	static boolean delete(ServerPlayer p, @Nullable String name) {
		if (!TeleportsMod.MODULE.on()) return false;
		TeleportsMod.Data d = TeleportsMod.PLAYERS.get(p);
		if (name == null || name.isBlank()) {
			if (d.homes.size() == 1) return delete(p, d.homes.getFirst().name);
			open(p);
			return false;
		}
		TeleportsMod.Home h = find(d, name);
		if (h == null) {
			Notify.warn(p, Ui.item("red_bed"), "No home called " + name, "Your homes: /homes.");
			return false;
		}
		d.homes.remove(h);
		if (h.name.equals(d.favourite)) d.favourite = d.homes.isEmpty() ? "" : d.homes.getFirst().name;
		TeleportsMod.PLAYERS.markDirty(p);
		String fav = favourite(d);
		Notify.done(p, Ui.item("red_bed"), h.name + " deleted", d.homes.isEmpty() ? "No homes left · /sethome for a new one"
				: "/home now brings you to " + fav);
		return true;
	}

	/** Makes it the home /home goes to. */
	static void star(ServerPlayer p, String name) {
		TeleportsMod.Data d = TeleportsMod.PLAYERS.get(p);
		TeleportsMod.Home h = find(d, name);
		if (h == null) return;
		d.favourite = h.name;
		TeleportsMod.PLAYERS.markDirty(p);
		Notify.done(p, Ui.item("red_bed"), "★ " + h.name, "/home brings you here now");
	}

	static boolean rename(ServerPlayer p, String old, String raw) {
		TeleportsMod.Data d = TeleportsMod.PLAYERS.get(p);
		TeleportsMod.Home h = find(d, old);
		String name = clean(raw);
		if (h == null) return false;
		if (name == null) {
			Notify.warn(p, Items.NAME_TAG, "Pick another name", "A home's name has 1 to 24 letters.");
			return false;
		}
		TeleportsMod.Home other = find(d, name);
		if (other != null && other != h) {
			Notify.warn(p, Items.NAME_TAG, "You have a " + other.name + " already", "Pick another name.");
			return false;
		}
		if (h.name.equals(d.favourite)) d.favourite = name;
		h.name = name;
		TeleportsMod.PLAYERS.markDirty(p);
		return true;
	}

	private static boolean allowed(ServerPlayer p) {
		if (!TeleportsMod.MODULE.on()) return false;
		if (!TeleportsMod.HOMES.get()) {
			Notify.warn(p, Items.BARRIER, "No homes here", "Homes are turned off on this server.");
			return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- windows

	/** Naming a new home (from the button in the list). */
	static void nameWindow(ServerPlayer p) {
		TeleportsMod.Data d = TeleportsMod.PLAYERS.get(p);
		Window w = Window.of(Domain.PLACES, "Homes", "A home here");
		w.text(Ui.text("A home where you stand now. /home " + (d.homes.isEmpty() ? "" : "<its name> ") + "brings you back here.",
				ChatFormatting.GRAY));
		w.field("name", Ui.text("Name", ChatFormatting.WHITE), freeName(d), 24);
		w.button(Ui.text("✔ Set it", ChatFormatting.GREEN), null, (pl, v) -> {
			String name = v.text("name", "").strip();
			if (set(pl, name.isEmpty() ? freeName(TeleportsMod.PLAYERS.get(pl)) : name)) PlaceList.open(pl, KIND);
		});
		w.cancel(pl -> PlaceList.open(pl, KIND)).show(p);
	}

	/** Right-click on a home: everything you can do with it. */
	static void options(ServerPlayer p, String name) {
		TeleportsMod.Data d = TeleportsMod.PLAYERS.get(p);
		TeleportsMod.Home h = find(d, name);
		if (h == null) {
			PlaceList.open(p, KIND);
			return;
		}
		boolean fav = h.name.equals(favourite(d));
		Window w = Window.of(Domain.PLACES, "Homes", h.name).wide();
		w.text(Ui.text("At " + (int) Math.floor(h.x) + " " + (int) Math.floor(h.y) + " " + (int) Math.floor(h.z) + " in the "
				+ world(h.dim) + ", set " + Players.ago(h.set).toLowerCase(Locale.ROOT) + "." + (fav ? " This is your /home." : ""), ChatFormatting.GRAY));
		w.field("name", Ui.text("Name", ChatFormatting.WHITE), h.name, 24);
		w.button(Ui.text("Go there", ChatFormatting.WHITE), null, (pl, v) -> go(pl, h.name));
		if (h.dim.equals(p.level().dimension().identifier().toString())) {
			w.button(Ui.text("Show the way", ChatFormatting.WHITE), Ui.text("A beam to it, without teleporting", ChatFormatting.GRAY),
					(pl, v) -> Guide.toPoint(pl, pl.level(), new Vec3(h.x, h.y, h.z), new ItemStack(icon(h.dim)), Component.literal(h.name), 0));
		}
		if (!fav) w.button(Ui.text("★ Make this my /home", 0xFFD966), null, (pl, v) -> {
			star(pl, h.name);
			PlaceList.open(pl, KIND);
		});
		w.button(Ui.text("✔ Save the name", ChatFormatting.GREEN), null, (pl, v) -> {
			if (rename(pl, h.name, v.text("name", h.name))) PlaceList.open(pl, KIND);
			else options(pl, h.name);
		});
		w.button(Ui.text("Move it here", ChatFormatting.WHITE), Ui.text("To where you stand now", ChatFormatting.GRAY), (pl, v) -> {
			set(pl, h.name);
			PlaceList.open(pl, KIND);
		});
		w.button(Ui.text("Delete", ChatFormatting.RED), null, (pl, v) -> {
			Window c = Window.of(Domain.PLACES, "Homes", h.name, "Delete");
			c.text(Ui.text("Delete " + h.name + "? You can set a new home any time.", ChatFormatting.GRAY));
			c.button(Ui.text("Delete", ChatFormatting.RED), null, (q, vv) -> {
				delete(q, h.name);
				PlaceList.open(q, KIND);
			});
			c.cancel(q -> options(q, h.name)).show(pl);
		});
		w.back(pl -> PlaceList.open(pl, KIND)).show(p);
	}

	static String world(String dim) {
		return switch (dim) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
		};
	}
}
