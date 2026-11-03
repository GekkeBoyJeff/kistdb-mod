package nl.handig.kern;

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
 * als het er is. Voorbeeld: KistDB biedt "zoek dit item in je kisten" aan; Verzamelaar gebruikt dat als je op een
 * item klikt. Zonder KistDB verschijnt die knop gewoon niet.
 */
public final class Services {
	/** Zoekt een item in de kisten van de speler en wijst de weg. */
	public interface ItemFinder {
		/** @return true als het item ergens gevonden is */
		boolean find(ServerPlayer player, ItemStack item);
	}

	/** Bekende menu's die andere modules kunnen openen. */
	public static final String MENU_ACHIEVEMENTS = "verzamelaar.prestaties";
	public static final String MENU_STATS = "info.statistieken";
	public static final String MENU_WAYPOINTS = "wegwijzer.punten";
	public static final String MENU_CHESTS = "kistdb.zoeken";

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

	// ---------------------------------------------------------------- voortgang (Verzamelaar)

	/** Voortgang van een speler; Verzamelaar levert dit, Gebied en Naam gebruiken het. */
	public interface Progress {
		/** Prestatiepunten (zeldzamere prestaties tellen zwaarder). */
		int points(ServerPlayer player);

		/** Hoogste zeldzaamheid die de speler ooit heeft gehaald, 0 (gewoon) t/m 5 (mythisch), of -1 voor nog niets. */
		int bestRarity(ServerPlayer player);

		/** Gekozen titel voor boven je naam, of null. */
		@Nullable Component title(ServerPlayer player);
	}

	private static @Nullable Progress progress;

	public static void provideProgress(Progress p) {
		progress = p;
	}

	public static Optional<Progress> progress() {
		return Optional.ofNullable(progress);
	}
}
