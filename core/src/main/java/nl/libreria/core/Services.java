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

		/** Colour (0xRRGGBB) of the rarity of the player's title, or -1. */
		default int titleColor(ServerPlayer player) {
			return -1;
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

	private static @Nullable Progress progress;

	public static void provideProgress(Progress p) {
		progress = p;
	}

	public static Optional<Progress> progress() {
		return Optional.ofNullable(progress);
	}
}
