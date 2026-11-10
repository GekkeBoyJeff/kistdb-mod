package nl.libreria.core;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.gui.PlacesMenu;
import org.jetbrains.annotations.Nullable;

/**
 * "Your places": one list for everything you can be shown the way to, whichever module knows about it. Waypoints,
 * your death point and graves, your pets, the places in your journal and your pieces of land all show up the same way:
 * an icon, a name, how far and which way, then <b>click = show the way</b> and <b>right-click = details</b>, with a tab
 * per kind at the top.
 *
 * <pre>{@code
 * PlaceList.kind("pets", "Your pets", Items.LEAD, 50).about("Tamed animals and where they are.");
 * PlaceList.addSource((player, out) -> {
 *     for (Pet pet : petsOf(player)) out.add(PlaceList.place("pets", pet.name, egg(pet), pet.dim, pet.pos()).lore(...));
 * });
 * }</pre>
 */
public final class PlaceList {
	/** A tab: what kind of place, how it's called, and what's special about it. */
	public static final class Kind {
		public final String id;
		public final String label;
		public final Item icon;
		public final int order;
		List<String> about = List.of();
		List<String> empty = List.of();
		List<String> help = List.of();
		@Nullable Function<ServerPlayer, GuiElementBuilder> action, footer;

		Kind(String id, String label, Item icon, int order) {
			this.id = id;
			this.label = label;
			this.icon = icon;
			this.order = order;
		}

		/** Lines on the tab. */
		public Kind about(String... lines) {
			this.about = List.of(lines);
			return this;
		}

		/** What the list says when there's nothing of this kind yet. */
		public Kind empty(String... lines) {
			this.empty = List.of(lines);
			return this;
		}

		/** Extra lines for the "? Help" book while this tab is open. */
		public Kind help(String... lines) {
			this.help = List.of(lines);
			return this;
		}

		/** A button in the top-left corner while this tab (or All) is open, e.g. "✚ New waypoint here". */
		public Kind action(Function<ServerPlayer, GuiElementBuilder> button) {
			this.action = button;
			return this;
		}

		/** A button in the bottom row while this tab is open, e.g. the graves on/off switch. */
		public Kind footer(Function<ServerPlayer, GuiElementBuilder> button) {
			this.footer = button;
			return this;
		}

		public List<String> aboutLines() {
			return this.about;
		}

		public List<String> emptyLines() {
			return this.empty;
		}

		public List<String> helpLines() {
			return this.help;
		}

		public @Nullable Function<ServerPlayer, GuiElementBuilder> actionButton() {
			return this.action;
		}

		public @Nullable Function<ServerPlayer, GuiElementBuilder> footerButton() {
			return this.footer;
		}
	}

	/** One place in the list. Build it with {@link #place} and the methods below. */
	public static final class Place {
		public final String kind;
		public final String name;
		public final ItemStack icon;
		public final String dim;
		public final Vec3 pos;
		final List<Component> lore = new ArrayList<>();
		@Nullable Consumer<ServerPlayer> go, details, drop;
		String detailsLabel = "details";
		@Nullable String dropLabel;
		boolean dimmed;
		String goLabel = "show the way";
		boolean anywhere;

		Place(String kind, String name, ItemStack icon, String dim, Vec3 pos) {
			this.kind = kind;
			this.name = name;
			this.icon = icon;
			this.dim = dim;
			this.pos = pos;
		}

		/** Extra lines under the distance (who made it, what's in it, when…). */
		public Place lore(Component... lines) {
			Collections.addAll(this.lore, lines);
			return this;
		}

		/** What "show the way" does, when it's more than a beam to {@link #pos} (a pet that walks around). */
		public Place go(Consumer<ServerPlayer> go) {
			this.go = go;
			return this;
		}

		/**
		 * A click does something else than showing the way ({@code label}: "go there"), and works from another
		 * dimension too (a home you teleport to).
		 */
		public Place goAnywhere(String label, Consumer<ServerPlayer> go) {
			this.go = go;
			this.goLabel = label;
			this.anywhere = true;
			return this;
		}

		/** Right-click: e.g. "edit", "see inside". */
		public Place details(String label, Consumer<ServerPlayer> details) {
			this.detailsLabel = label;
			this.details = details;
			return this;
		}

		/** Q (drop key) on it: e.g. "send to Xaero's Minimap". */
		public Place drop(String label, Consumer<ServerPlayer> drop) {
			this.dropLabel = label;
			this.drop = drop;
			return this;
		}

		/** Shown greyed (a pet that died, a grave of long ago). */
		public Place dimmed() {
			this.dimmed = true;
			return this;
		}

		public List<Component> loreLines() {
			return this.lore;
		}

		public @Nullable Consumer<ServerPlayer> goAction() {
			return this.go;
		}

		public @Nullable Consumer<ServerPlayer> detailsAction() {
			return this.details;
		}

		public String detailsLabel() {
			return this.detailsLabel;
		}

		public @Nullable Consumer<ServerPlayer> dropAction() {
			return this.drop;
		}

		public @Nullable String dropLabel() {
			return this.dropLabel;
		}

		public boolean isDimmed() {
			return this.dimmed;
		}

		/** What a click does, for the tooltip ("show the way", "go there"). */
		public String goLabel() {
			return this.goLabel;
		}

		/** Does a click work from another dimension too? */
		public boolean anywhere() {
			return this.anywhere;
		}
	}

	/** Adds a player's places of the kinds a module knows. */
	public interface Source {
		void add(ServerPlayer player, List<Place> out);
	}

	private static final Map<String, Kind> KINDS = new LinkedHashMap<>();
	private static final List<Source> SOURCES = new ArrayList<>();

	private PlaceList() {
	}

	/** Registers a tab (or returns the one that's there: two modules may both add to "death"). */
	public static Kind kind(String id, String label, Item icon, int order) {
		return KINDS.computeIfAbsent(id, k -> new Kind(id, label, icon, order));
	}

	public static void addSource(Source s) {
		SOURCES.add(s);
	}

	public static Place place(String kind, String name, ItemStack icon, String dim, Vec3 pos) {
		return new Place(kind, name, icon, dim, pos);
	}

	/** The tabs, in order. */
	public static List<Kind> kinds() {
		List<Kind> out = new ArrayList<>(KINDS.values());
		out.sort(java.util.Comparator.comparingInt(k -> k.order));
		return out;
	}

	public static @Nullable Kind kind(String id) {
		return KINDS.get(id);
	}

	/** Every place of this player (each module's errors stay in that module). */
	public static List<Place> all(ServerPlayer p) {
		List<Place> out = new ArrayList<>();
		for (Source s : SOURCES) {
			try {
				s.add(p, out);
			} catch (RuntimeException e) {
				Libreria.LOG.warn("A source of Your places failed", e);
			}
		}
		return out;
	}

	public static boolean any() {
		return !KINDS.isEmpty();
	}

	/** Opens "Your places", on all tabs. */
	public static void open(ServerPlayer p) {
		PlacesMenu.open(p, null);
	}

	/** Back to the tab and page the player was on (for "Back" from a place's own screen). */
	public static void reopen(ServerPlayer p) {
		PlacesMenu.reopen(p);
	}

	/** Opens it on one tab ("waypoints", "death", "pets", "journal", "land"). */
	public static void open(ServerPlayer p, @Nullable String kind) {
		PlacesMenu.open(p, kind);
	}
}
