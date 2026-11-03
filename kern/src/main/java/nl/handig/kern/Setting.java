package nl.handig.kern;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/** Een instelling per speler. Waarden worden opgeslagen in &lt;wereld&gt;/handig/opties/&lt;uuid&gt;.json. */
public abstract sealed class Setting permits Setting.Toggle, Setting.Choice {
	/** Opgeslagen waarden: "module.sleutel" → getal (aan/uit = 1/0). */
	public static final class Data {
		public Map<String, Integer> values = new HashMap<>();
	}

	static final PlayerStore<Data> STORE = PlayerStore.create("handig/opties", Data.class, Data::new, d -> {
		if (d.values == null) d.values = new HashMap<>();
	});

	public final Module module;
	public final String key;
	public final String label;
	public final Item icon;
	public final List<String> lore;

	Setting(Module module, String key, String label, Item icon, List<String> lore) {
		this.module = module;
		this.key = key;
		this.label = label;
		this.icon = icon;
		this.lore = lore;
	}

	public String fullKey() {
		return this.module.id + "." + this.key;
	}

	int raw(ServerPlayer player, int def) {
		Integer v = STORE.get(player).values.get(fullKey());
		return v == null ? def : v;
	}

	void setRaw(ServerPlayer player, int value) {
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
		Integer v = STORE.get(id).values.get(fullKey());
		return v == null ? def : v;
	}

	/** Tekst van de huidige waarde, voor het menu. */
	public abstract String display(ServerPlayer player);

	public static final class Toggle extends Setting {
		public final boolean def;

		Toggle(Module module, String key, String label, Item icon, List<String> lore, boolean def) {
			super(module, key, label, icon, lore);
			this.def = def;
		}

		public boolean get(ServerPlayer player) {
			return raw(player, this.def ? 1 : 0) != 0;
		}

		/** Ook bruikbaar voordat de speler echt in de wereld is (bijvoorbeeld tijdens het inloggen). */
		public boolean get(java.util.UUID id) {
			return raw(id, this.def ? 1 : 0) != 0;
		}

		public void set(ServerPlayer player, boolean value) {
			setRaw(player, value ? 1 : 0);
		}

		@Override
		public String display(ServerPlayer player) {
			return get(player) ? "AAN" : "UIT";
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

		@Override
		public String display(ServerPlayer player) {
			return this.labels[index(player)];
		}
	}
}
