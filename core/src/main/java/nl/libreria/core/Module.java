package nl.libreria.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * Een Libreria-module (bv. ChestsMod, ToolsMod). Een module registreert zichzelf bij het opstarten en krijgt
 * daarmee automatisch een plek in het instellingenmenu (/libreria settings) en in het Libreria-menu (knop in het E-scherm).
 */
public final class Module {
	private static final Map<String, Module> MODULES = new LinkedHashMap<>();

	public final String id;
	public final String name;
	public final Item icon;
	public final List<String> description;
	final List<Setting> settings = new ArrayList<>();
	final List<HubEntry> hubEntries = new ArrayList<>();

	/** Een knop in het Libreria-menu. */
	public record HubEntry(Module module, Category category, Item icon, String name, ChatFormatting color, List<String> lore,
			Consumer<ServerPlayer> action) {
	}

	/** Rows in the Libreria menu. */
	public enum Category {
		ITEMS("Items & chests", "orange", ChatFormatting.GOLD),
		WORLD("World & travel", "green", ChatFormatting.GREEN),
		PROGRESS("Progress", "yellow", ChatFormatting.YELLOW),
		YOU("You", "magenta", ChatFormatting.LIGHT_PURPLE);

		public final String label;
		public final String pane;
		public final ChatFormatting color;

		Category(String label, String pane, ChatFormatting color) {
			this.label = label;
			this.pane = pane;
			this.color = color;
		}

		/** Default row for a module's buttons. */
		static Category forModule(String id) {
			return switch (id) {
				case "chests", "graves", "tools" -> ITEMS;
				case "claims", "waypoints" -> WORLD;
				case "collector", "info" -> PROGRESS;
				default -> YOU;
			};
		}
	}

	final List<String> commands = new ArrayList<>();

	/** Command lines shown in the Help book of the Libreria menu, e.g. "/claim – claim this chunk". */
	public Module commands(String... lines) {
		this.commands.addAll(List.of(lines));
		return this;
	}

	public List<String> commandHelp() {
		return Collections.unmodifiableList(this.commands);
	}

	private Module(String id, String name, Item icon, List<String> description) {
		this.id = id;
		this.name = name;
		this.icon = icon;
		this.description = description;
	}

	public static Module register(String id, String name, Item icon, String... description) {
		Module m = new Module(id, name, icon, List.of(description));
		MODULES.put(id, m);
		return m;
	}

	/** Alle modules, met de algemene Libreria-instellingen altijd eerst. */
	public static List<Module> all() {
		List<Module> out = new ArrayList<>(MODULES.values());
		out.sort((a, b) -> Boolean.compare(!a.id.equals("libreria"), !b.id.equals("libreria")));
		return out;
	}

	public List<Setting> settings() {
		return Collections.unmodifiableList(this.settings);
	}

	/** Aan/uit-instelling, per speler. */
	public Setting.Toggle toggle(String key, String label, boolean def, Item icon, String... lore) {
		Setting.Toggle s = new Setting.Toggle(this, key, label, icon, List.of(lore), def);
		this.settings.add(s);
		return s;
	}

	/** Keuze uit vaste waarden, per speler. labels[i] hoort bij values[i]. */
	public Setting.Choice choice(String key, String label, Item icon, int defIndex, int[] values, String[] labels, String... lore) {
		Setting.Choice s = new Setting.Choice(this, key, label, icon, List.of(lore), values, labels, defIndex);
		this.settings.add(s);
		return s;
	}

	/** Voegt een knop toe aan het Libreria-menu. */
	public void hubEntry(Item icon, String name, ChatFormatting color, Consumer<ServerPlayer> action, String... lore) {
		this.hubEntries.add(new HubEntry(this, Category.forModule(this.id), icon, name, color, List.of(lore), action));
	}

	/** Same, but in a specific row of the menu. */
	public void hubEntry(Category category, Item icon, String name, ChatFormatting color, Consumer<ServerPlayer> action, String... lore) {
		this.hubEntries.add(new HubEntry(this, category, icon, name, color, List.of(lore), action));
	}

	public static List<HubEntry> allHubEntries() {
		List<HubEntry> out = new ArrayList<>();
		for (Module m : MODULES.values()) out.addAll(m.hubEntries);
		return out;
	}
}
