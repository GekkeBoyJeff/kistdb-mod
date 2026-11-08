package nl.libreria.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Zo werken modules samen zonder van elkaar afhankelijk te zijn: een module biedt iets aan, een andere gebruikt het
 * als het er is. Voorbeeld: ChestsMod biedt "zoek dit item in je kisten" aan; CollectorMod gebruikt dat als je op een
 * item klikt. Zonder ChestsMod verschijnt die knop gewoon niet.
 */
public final class Services {
	/** Zoekt een item in de kisten van de speler en wijst de weg. */
	public interface ItemFinder {
		/** @return true als het item ergens gevonden is */
		boolean find(ServerPlayer player, ItemStack item);
	}

	/** Bekende menu's die andere modules kunnen openen. */
	public static final String MENU_ACHIEVEMENTS = "collector.achievements";
	public static final String MENU_STATS = "info.stats";
	public static final String MENU_WAYPOINTS = "waypoints.list";
	public static final String MENU_CHESTS = "chests.search";
	public static final String MENU_LAND_MAP = "claims.map";
	public static final String MENU_EFFECTS = "glint.effects";
	public static final String MENU_JOURNAL = "info.journal";
	public static final String MENU_PETS = "info.pets";
	public static final String MENU_GOAL = "collector.goal";
	public static final String MENU_RECORDS = "collector.records";
	/** Not a menu but an action: sort the player's inventory. */
	public static final String ACTION_SORT = "chests.sort";

	private static @Nullable ItemFinder itemFinder;
	private static final Map<String, Consumer<ServerPlayer>> MENUS = new HashMap<>();

	private Services() {
	}

	public static void provideItemFinder(ItemFinder finder) {
		itemFinder = finder;
	}

	public static Optional<ItemFinder> itemFinder() {
		return Optional.ofNullable(itemFinder);
	}

	public static void provideMenu(String key, Consumer<ServerPlayer> opener) {
		MENUS.put(key, opener);
	}

	public static Optional<Consumer<ServerPlayer>> menu(String key) {
		return Optional.ofNullable(MENUS.get(key));
	}

	// ---------------------------------------------------------------- land (ClaimsMod)

	/** Who owns land; ClaimsMod provides it. Chests use it: crafting and refilling only take from your own land. */
	public interface Land {
		/** The owner of the chunk at {@code pos}, or null for free land. */
		@Nullable java.util.UUID ownerAt(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos);

		/** May this player build (and change stands, frames…) here: free land, their own, or land where they're trusted. */
		default boolean mayBuild(ServerPlayer player, net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
			java.util.UUID owner = ownerAt(level, pos);
			return owner == null || owner.equals(player.getUUID());
		}
	}

	private static @Nullable Land land;

	public static void provideLand(Land l) {
		land = l;
	}

	public static Optional<Land> land() {
		return Optional.ofNullable(land);
	}

	/** May {@code player} build at {@code pos}? Always, when there are no claims on this server. */
	public static boolean mayBuild(ServerPlayer player, net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
		return land == null || land.mayBuild(player, level, pos);
	}

	/** Is {@code pos} on land that {@code player} claimed? False when there are no claims on this server. */
	public static boolean onOwnLand(ServerPlayer player, net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
		return land != null && player.getUUID().equals(land.ownerAt(level, pos));
	}

	// ---------------------------------------------------------------- sterfbericht

	/** Extra regel in het bericht na respawnen (bijvoorbeeld "je spullen liggen in je graf"). */
	public interface DeathNote {
		@Nullable Component note(ServerPlayer player);
	}

	private static final List<DeathNote> DEATH_NOTES = new ArrayList<>();

	public static void addDeathNote(DeathNote note) {
		DEATH_NOTES.add(note);
	}

	public static List<Component> deathNotes(ServerPlayer player) {
		List<Component> out = new ArrayList<>();
		for (DeathNote n : DEATH_NOTES) {
			Component c = n.note(player);
			if (c != null) out.add(c);
		}
		return out;
	}

	/** A button in the death recap window, e.g. "Beam to my grave". */
	public record DeathAction(String label, String tooltip, Consumer<ServerPlayer> run) {
	}

	public interface DeathActions {
		@Nullable DeathAction action(ServerPlayer player);
	}

	private static final List<DeathActions> DEATH_ACTIONS = new ArrayList<>();

	public static void addDeathAction(DeathActions a) {
		DEATH_ACTIONS.add(a);
	}

	public static List<DeathAction> deathActions(ServerPlayer player) {
		List<DeathAction> out = new ArrayList<>();
		for (DeathActions a : DEATH_ACTIONS) {
			DeathAction act = a.action(player);
			if (act != null) out.add(act);
		}
		return out;
	}

	// ---------------------------------------------------------------- voortgang (CollectorMod)

	/** Voortgang van een speler; CollectorMod levert dit, ClaimsMod en NicknamesMod gebruiken het. */
	public interface Progress {
		/** Prestatiepunten (zeldzamere prestaties tellen zwaarder). */
		int points(ServerPlayer player);

		/** Hoogste zeldzaamheid die de speler ooit heeft gehaald, 0 (gewoon) t/m 5 (mythisch), of -1 voor nog niets. */
		int bestRarity(ServerPlayer player);

		/** Gekozen titel voor boven je naam, of null. */
		@Nullable Component title(ServerPlayer player);

		/**
		 * A short "player card": title, points, achievements and completion. Shown in the Libreria menu header and in
		 * the look-at tooltip when you look at another player.
		 */
		default List<Component> card(ServerPlayer player) {
			return List.of();
		}

		/** The numbers behind the card (for drawing it with pictures), or null. */
		default @Nullable CardData cardData(ServerPlayer player) {
			return null;
		}

		/**
		 * The next grade of the achievement ladder that counts this statistic ("custom:fish_caught"): its name and how
		 * far you are, for the statistics menu. Null when no ladder counts it.
		 */
		default @Nullable Component nextGrade(ServerPlayer player, String stat) {
			return null;
		}

		/** Colour (0xRRGGBB) of the rarity of the player's title, or -1. */
		default int titleColor(ServerPlayer player) {
			return -1;
		}

		/** The title of any player (also offline), in its rarity's colour, for their profile; null for none. */
		default @Nullable Component titleOf(java.util.UUID player) {
			return null;
		}
	}

	/** Points, achievements earned of total, items collected of total. */
	public record CardData(int points, int earned, int total, int items, int itemsTotal) {
	}

	/** Players whose camera a module has taken over (e.g. the claims map): no look-at tooltip for them. */
	private static final java.util.Set<java.util.UUID> CAMERA = new java.util.HashSet<>();

	public static void setCamera(java.util.UUID player, boolean busy) {
		if (busy) CAMERA.add(player);
		else CAMERA.remove(player);
	}

	public static boolean inCamera(ServerPlayer player) {
		return CAMERA.contains(player.getUUID());
	}

	/** What a player discovered (Info's journal): for exploring achievements. */
	public interface Discoveries {
		/** Places in the journal: villages, temples, peaks, lakes, stretches of land. */
		int places(ServerPlayer player);

		/** Different biomes visited. */
		int biomes(ServerPlayer player);

		/** Places of one kind ("Village", "Peak", "Lake", "Cavern", "Ancient City"…). */
		int ofKind(ServerPlayer player, String kind);

		/** Different kinds of structure found. */
		int structureKinds(ServerPlayer player);

		/** How many kinds of structure there are to find. */
		int structureKindsTotal();
	}

	/** The stories found gear tells in parts (Info): which there are, and how many parts of each a player has held. */
	public interface Stories {
		/** Story id → its title, in a fixed order. */
		java.util.Map<String, String> titles();

		/** How many parts a story has. */
		int parts(String story);

		/** How many different parts of a story this player has had in their inventory. */
		int held(ServerPlayer player, String story);

		/** Story parts held, all stories together. */
		int pieces(ServerPlayer player);
	}

	private static @Nullable Stories stories;

	public static void provideStories(Stories s) {
		stories = s;
	}

	public static Optional<Stories> stories() {
		return Optional.ofNullable(stories);
	}

	private static @Nullable Discoveries discoveries;

	public static void provideDiscoveries(Discoveries d) {
		discoveries = d;
	}

	public static Optional<Discoveries> discoveries() {
		return Optional.ofNullable(discoveries);
	}

	private static @Nullable Progress progress;

	public static void provideProgress(Progress p) {
		progress = p;
	}

	public static Optional<Progress> progress() {
		return Optional.ofNullable(progress);
	}
}
