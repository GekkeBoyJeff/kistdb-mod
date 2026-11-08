package nl.libreria.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/** Een instelling per speler. Waarden worden opgeslagen in &lt;wereld&gt;/libreria/settings/&lt;uuid&gt;.json. */
public abstract sealed class Setting permits Setting.Toggle, Setting.Choice {
	/** Opgeslagen waarden: "module.sleutel" → getal (aan/uit = 1/0). */
	public static final class Data {
		public Map<String, Integer> values = new HashMap<>();
	}

	static final PlayerStore<Data> STORE = PlayerStore.create("libreria/settings", Data.class, Data::new, d -> {
		if (d.values == null) d.values = new HashMap<>();
	});

	public final Module module;
	public final String key;
	public final String label;
	public final Item icon;
	public final List<String> lore;
	/** Where players find it: the domain, and the group within it ("Search & sorting", "When you die"). */
	Domain domain;
	String section;

	Setting(Module module, String key, String label, Item icon, List<String> lore) {
		this.module = module;
		this.key = key;
		this.label = label;
		this.icon = icon;
		this.lore = lore;
		this.domain = module.domain;
		this.section = module.section;
	}

	public Domain domain() {
		return this.domain;
	}

	public String section() {
		return this.section;
	}

	public String fullKey() {
		return this.module.id + "." + this.key;
	}

	int raw(ServerPlayer player, int def) {
		Integer f = forced();
		if (f != null) return f;
		Integer v = STORE.get(player).values.get(fullKey());
		return v == null ? serverDefault(def) : v;
	}

	// ---------------------------------------------------------------- what the server decides (operators)

	private static Map<String, String> house() {
		ServerSetting.Data d = ServerSetting.STORE.get();
		if (d.values == null) d.values = new HashMap<>();
		return d.values;
	}

	/** The value an operator set for everybody (players can't change it then), or null: each player chooses. */
	public @org.jetbrains.annotations.Nullable Integer forced() {
		String v = house().get("force." + fullKey());
		try {
			return v == null ? null : Integer.valueOf(v);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Is this decided by the server for everybody? */
	public boolean isForced() {
		return forced() != null;
	}

	/** What players start with (an operator may have changed what the module ships with). */
	int serverDefault(int def) {
		String v = house().get("default." + fullKey());
		try {
			return v == null ? def : Integer.parseInt(v);
		} catch (NumberFormatException e) {
			return def;
		}
	}

	/** Operators: {@code forced} for everybody (null: each player chooses), and what players start with (null: as shipped). */
	public void house(@org.jetbrains.annotations.Nullable Integer forced, @org.jetbrains.annotations.Nullable Integer start) {
		Map<String, String> m = house();
		boolean changed = !java.util.Objects.equals(m.get("force." + fullKey()), forced == null ? null : forced.toString())
				|| !java.util.Objects.equals(m.get("default." + fullKey()), start == null ? null : start.toString());
		if (forced == null) m.remove("force." + fullKey());
		else m.put("force." + fullKey(), forced.toString());
		if (start == null) m.remove("default." + fullKey());
		else m.put("default." + fullKey(), start.toString());
		if (!changed) return;
		ServerSetting.STORE.markDirty();
		// Everybody online gets what changed (packs, buttons…)
		MinecraftServer server = Libreria.server();
		if (server != null) for (ServerPlayer p : server.getPlayerList().getPlayers()) for (var l : this.listeners) l.accept(p);
	}

	void setRaw(ServerPlayer player, int value) {
		if (isForced()) return;
		Integer old = STORE.get(player).values.put(fullKey(), value);
		STORE.markDirty(player);
		if (old == null || old != value) for (java.util.function.Consumer<ServerPlayer> l : this.listeners) l.accept(player);
	}

	private final java.util.List<java.util.function.Consumer<ServerPlayer>> listeners = new java.util.ArrayList<>();

	/** Wordt aangeroepen als een speler deze instelling verandert (bijvoorbeeld om een resourcepack te wisselen). */
	public void onChange(java.util.function.Consumer<ServerPlayer> listener) {
		this.listeners.add(listener);
	}

	int raw(java.util.UUID id, int def) {
		Integer f = forced();
		if (f != null) return f;
		Integer v = STORE.get(id).values.get(fullKey());
		return v == null ? serverDefault(def) : v;
	}

	/** Tekst van de huidige waarde, voor het menu. */
	public abstract String display(ServerPlayer player);

	public static final class Toggle extends Setting {
		public final boolean def;

		Toggle(Module module, String key, String label, Item icon, List<String> lore, boolean def) {
			super(module, key, label, icon, lore);
			this.def = def;
		}

		/** Shown somewhere else than its module's place in the settings. */
		public Toggle in(Domain domain, String section) {
			this.domain = domain;
			this.section = section;
			return this;
		}

		public boolean get(ServerPlayer player) {
			return this.module.on() && raw(player, this.def ? 1 : 0) != 0;
		}

		/** What players start with on this server. */
		public boolean start() {
			return serverDefault(this.def ? 1 : 0) != 0;
		}

		/** Ook bruikbaar voordat de speler echt in de wereld is (bijvoorbeeld tijdens het inloggen). */
		public boolean get(java.util.UUID id) {
			return this.module.on() && raw(id, this.def ? 1 : 0) != 0;
		}

		public void set(ServerPlayer player, boolean value) {
			setRaw(player, value ? 1 : 0);
		}

		@Override
		public String display(ServerPlayer player) {
			return get(player) ? "ON" : "OFF";
		}
	}

	public static final class Choice extends Setting {
		private final int[] values;
		private final String[] labels;
		private final int defIndex;

		Choice(Module module, String key, String label, Item icon, List<String> lore, int[] values, String[] labels, int defIndex) {
			super(module, key, label, icon, lore);
			if (values.length != labels.length || values.length == 0) throw new IllegalArgumentException("values/labels");
			this.values = values.clone();
			this.labels = labels.clone();
			this.defIndex = defIndex;
		}

		/** Shown somewhere else than its module's place in the settings. */
		public Choice in(Domain domain, String section) {
			this.domain = domain;
			this.section = section;
			return this;
		}

		/** De gekozen waarde. */
		public int get(ServerPlayer player) {
			return this.values[index(player)];
		}

		public int index(ServerPlayer player) {
			int v = raw(player, this.values[this.defIndex]);
			for (int i = 0; i < this.values.length; i++) {
				if (this.values[i] == v) return i;
			}
			return this.defIndex;
		}

		public void cycle(ServerPlayer player, int step) {
			int i = Math.floorMod(index(player) + step, this.values.length);
			setRaw(player, this.values[i]);
		}

		public void set(ServerPlayer player, int value) {
			setRaw(player, value);
		}

		public int size() {
			return this.values.length;
		}

		public String labelAt(int i) {
			return this.labels[i];
		}

		public int valueAt(int i) {
			return this.values[i];
		}

		public int defaultValue() {
			return this.values[this.defIndex];
		}

		/** What players start with on this server. */
		public int start() {
			return serverDefault(this.values[this.defIndex]);
		}

		@Override
		public String display(ServerPlayer player) {
			return this.labels[index(player)];
		}
	}
}
