package nl.libreria.core;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * What a player wants to do, not which jar does it. Every button, setting, menu and chat line of Libreria belongs to
 * one of these three, so the whole thing reads as one product: the start screen shows these three, the settings follow
 * them, every menu carries its colour and emblem, and chat lines start with a ◆ in its colour. Module names stay
 * behind the scenes (only operators see them, in the server settings).
 */
public enum Domain {
	PLACES("Land & places", "land", 0x86DB7A, ChatFormatting.GREEN, "lime", Items.FILLED_MAP,
			"Your land, pings, graves, pets and the places you found."),
	PROGRESS("Progress", "progress", 0xFFCB52, ChatFormatting.GOLD, "yellow", Items.NETHER_STAR,
			"Everything there is to complete, and who did the most."),
	YOU("You", "me", 0xE39BFF, ChatFormatting.LIGHT_PURPLE, "magenta", Items.NAME_TAG,
			"Your name, title, items, chests and how things look.");

	/** What players read. */
	public final String label;
	/** Short word for commands ({@code /l land}) and the menu frame theme. */
	public final String word;
	/** Colour (0xRRGGBB) for text: chat diamond, window titles, labels. */
	public final int rgb;
	/** The nearest of the 16 old colours, for places that can't take any colour (glass panes). */
	public final ChatFormatting legacy;
	/** A darker shade of {@link #rgb}, readable on Minecraft's light grey menus. */
	public int dark() {
		return switch (this) {
			case PLACES -> 0x2D7428;
			case PROGRESS -> 0x936600;
			case YOU -> 0x7A3792;
		};
	}

	/** Stained glass colour for section headers in chest menus. */
	public final String pane;
	public final Item icon;
	/** One line: what is in it. */
	public final String blurb;

	Domain(String label, String word, int rgb, ChatFormatting legacy, String pane, Item icon, String blurb) {
		this.label = label;
		this.word = word;
		this.rgb = rgb;
		this.legacy = legacy;
		this.pane = pane;
		this.icon = icon;
		this.blurb = blurb;
	}

	/** The label in its colour. */
	public MutableComponent title() {
		return Ui.text(this.label, this.rgb);
	}

	/** Menu frame theme in the Libreria pack (the coloured line and emblem under a menu's title). */
	public String theme() {
		return switch (this) {
			case PLACES -> "places";
			case PROGRESS -> "progress";
			case YOU -> "you";
		};
	}

	public static List<Domain> all() {
		return List.of(values());
	}

	/** By its command word or label, or null ("items", the old page of your items and chests, is You now). */
	public static Domain byWord(String s) {
		for (Domain d : values()) if (d.word.equalsIgnoreCase(s) || d.name().equalsIgnoreCase(s)) return d;
		return "items".equalsIgnoreCase(s) ? YOU : null;
	}

	/** Where a module's things go unless it says otherwise. */
	static Domain forModule(String id) {
		return switch (id) {
			case "claims", "waypoints", "graves" -> PLACES;
			case "collector" -> PROGRESS;
			default -> YOU;
		};
	}
}
