package nl.libreria.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * A Libreria module (ChestsMod, ToolsMod…): what an operator installs. Players never see the module itself: its
 * buttons and settings land in one of the three {@link Domain}s (Land &amp; places, Progress, You), in a group
 * with a player-friendly name. A module registers itself at startup and gets its place in the start screen, the
 * settings and (for operators) the server settings for free.
 */
public final class Module {
	private static final Map<String, Module> MODULES = new LinkedHashMap<>();

	public final String id;
	/** The module's name, for operators (server settings). */
	public final String name;
	public final Item icon;
	public final List<String> description;
	/** Where its settings go unless a setting says otherwise. */
	Domain domain;
	String section;
	final List<Setting> settings = new ArrayList<>();
	final List<HubEntry> hubEntries = new ArrayList<>();

	/**
	 * A button on a domain page of the start screen. {@code order} keeps the page the same whatever order the modules
	 * load in (lower first).
	 */
	public record HubEntry(Module module, Domain domain, int order, Item icon, String name, List<String> lore,
			Consumer<ServerPlayer> action) {
	}

	/** One line of the command help, under the domain it belongs to. */
	public record Command(Domain domain, String line) {
	}

	final List<Command> commands = new ArrayList<>();

	/** Command lines for the help ("/claim – claim this chunk"), under this module's domain. */
	public Module commands(String... lines) {
		return commands(this.domain, lines);
	}

	/** Command lines under a given domain (a module whose commands belong to different places). */
	public Module commands(Domain domain, String... lines) {
		for (String l : lines) this.commands.add(new Command(domain, l));
		return this;
	}

	public List<Command> commandHelp() {
		return Collections.unmodifiableList(this.commands);
	}

	private Module(String id, String name, Item icon, List<String> description) {
		this.id = id;
		this.name = name;
		this.icon = icon;
		this.description = description;
		this.domain = Domain.forModule(id);
		this.section = name;
	}

	/** Where this module's settings show up for players, e.g. {@code in(Domain.YOU, "Tools")}. */
	public Module in(Domain domain, String section) {
		this.domain = domain;
		this.section = section;
		return this;
	}

	public Domain domain() {
		return this.domain;
	}

	public static Module register(String id, String name, Item icon, String... description) {
		Module m = new Module(id, name, icon, List.of(description));
		MODULES.put(id, m);
		// Every part but the core can be switched off for everybody (a bug, or not wanted here)
		if (!id.equals("libreria")) {
			m.enabled = m.serverToggle("on", "Turned on", icon, true, "Off: " + name + " stops for everybody", "(its buttons, commands and what it does)",
					"until it is turned on again.");
			m.enabled.onChange(() -> {
				var server = Libreria.server();
				if (server != null) for (var p : server.getPlayerList().getPlayers()) server.getCommands().sendCommands(p);
			});
		}
		return m;
	}

	private ServerSetting.Toggle enabled;

	/** Is this part of Libreria turned on (an operator can turn any part but the core off for everybody)? */
	public boolean on() {
		return this.enabled == null || this.enabled.get();
	}

	/** {@code action}, or (while this part is turned off) a short card that says so. */
	public Consumer<ServerPlayer> gate(Consumer<ServerPlayer> action) {
		return p -> {
			if (on()) action.accept(p);
			else Notify.warn(p, net.minecraft.world.item.Items.BARRIER, "Turned off", this.name + " is turned off on this server.");
		};
	}

	/** For a command: only there while this part is turned on. */
	public java.util.function.Predicate<net.minecraft.commands.CommandSourceStack> available() {
		return src -> on();
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

	/** A button on this module's settings window for settings that need their own screen (e.g. which effects you see). */
	public record SettingsButton(String label, String tooltip, Consumer<ServerPlayer> action) {
	}

	final List<SettingsButton> settingsButtons = new ArrayList<>();

	public void settingsButton(String label, String tooltip, Consumer<ServerPlayer> action) {
		this.settingsButtons.add(new SettingsButton(label, tooltip, action));
	}

	public List<SettingsButton> settingsButtons() {
		return Collections.unmodifiableList(this.settingsButtons);
	}

	/** A button on this module's page in the admin window (e.g. "Place a leaderboard here"). Operators only. */
	final List<SettingsButton> adminButtons = new ArrayList<>();

	public void adminButton(String label, String tooltip, Consumer<ServerPlayer> action) {
		this.adminButtons.add(new SettingsButton(label, tooltip, action));
	}

	public List<SettingsButton> adminButtons() {
		return Collections.unmodifiableList(this.adminButtons);
	}

	// ---------------------------------------------------------------- server-wide settings (admin window)

	final List<ServerSetting> serverSettings = new ArrayList<>();

	public List<ServerSetting> serverSettings() {
		return Collections.unmodifiableList(this.serverSettings);
	}

	/** A server-wide number (slider in the admin window). */
	public ServerSetting.Number serverNumber(String key, String label, Item icon, int def, int min, int max, int step, String unit,
			String... lore) {
		ServerSetting.Number s = new ServerSetting.Number(this, key, label, icon, List.of(lore), def, min, max, step, unit);
		this.serverSettings.add(s);
		return s;
	}

	/** A server-wide on/off switch. */
	public ServerSetting.Toggle serverToggle(String key, String label, Item icon, boolean def, String... lore) {
		ServerSetting.Toggle s = new ServerSetting.Toggle(this, key, label, icon, List.of(lore), def);
		this.serverSettings.add(s);
		return s;
	}

	/** A server-wide choice of a few options (a button that cycles through them in the admin window). */
	public ServerSetting.Choice serverChoice(String key, String label, Item icon, int def, String[] options, String... lore) {
		ServerSetting.Choice s = new ServerSetting.Choice(this, key, label, icon, List.of(lore), def, List.of(options));
		this.serverSettings.add(s);
		return s;
	}

	/** A server-wide line of text (a link, a welcome line…). */
	public ServerSetting.Text serverText(String key, String label, Item icon, String def, int maxLength, String... lore) {
		ServerSetting.Text s = new ServerSetting.Text(this, key, label, icon, List.of(lore), def, maxLength);
		this.serverSettings.add(s);
		return s;
	}

	/** A button on a domain page of the start screen. */
	public void hubEntry(Domain domain, int order, Item icon, String name, Consumer<ServerPlayer> action, String... lore) {
		this.hubEntries.add(new HubEntry(this, domain, order, icon, name, List.of(lore), action));
	}

	public static List<HubEntry> allHubEntries() {
		List<HubEntry> out = new ArrayList<>();
		for (Module m : MODULES.values()) out.addAll(m.hubEntries);
		out.sort(java.util.Comparator.comparingInt(HubEntry::order));
		return out;
	}

	/** The buttons of one domain page, in order. */
	public static List<HubEntry> hubEntries(Domain domain) {
		List<HubEntry> out = new ArrayList<>();
		for (HubEntry e : allHubEntries()) if (e.domain() == domain && e.module().on()) out.add(e);
		return out;
	}

	/** Every player setting of every module, in module order. */
	public static List<Setting> allSettings() {
		List<Setting> out = new ArrayList<>();
		for (Module m : all()) out.addAll(m.settings);
		return out;
	}
}
