package nl.libreria.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;

/**
 * A server-wide setting: one value for everybody, changed by ops in the admin window ({@code /libreria admin})
 * without config files or a restart. Modules declare them with {@link Module#serverNumber}, {@link Module#serverToggle}
 * and {@link Module#serverText}; values live in {@code <world>/libreria/server.json}.
 *
 * <p>Player settings ({@link Setting}) are what a player chooses for themselves; these are the house rules.
 */
public abstract sealed class ServerSetting permits ServerSetting.Number, ServerSetting.Toggle, ServerSetting.Text {
	/** Stored values: "module.key" → value as text. */
	public static final class Data {
		public Map<String, String> values = new HashMap<>();
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/server.json", Data.class, Data::new);
	private static final List<ServerSetting> ALL = new ArrayList<>();

	public final Module module;
	public final String key;
	public final String label;
	public final Item icon;
	public final List<String> lore;
	private final List<Runnable> listeners = new ArrayList<>();

	ServerSetting(Module module, String key, String label, Item icon, List<String> lore) {
		this.module = module;
		this.key = key;
		this.label = label;
		this.icon = icon;
		this.lore = lore;
		ALL.add(this);
	}

	public static List<ServerSetting> all() {
		return Collections.unmodifiableList(ALL);
	}

	public String fullKey() {
		return this.module.id + "." + this.key;
	}

	/** Called after the value changed (e.g. to apply a game rule or resend the server links). */
	public void onChange(Runnable listener) {
		this.listeners.add(listener);
	}

	/** The value as text, as shown in the admin window. */
	public abstract String display();

	/** Back to the value the module ships with. */
	public abstract void reset();

	String raw() {
		Data d = STORE.get();
		if (d.values == null) d.values = new HashMap<>();
		return d.values.get(fullKey());
	}

	void setRaw(String value) {
		Data d = STORE.get();
		if (d.values == null) d.values = new HashMap<>();
		String old = value == null ? d.values.remove(fullKey()) : d.values.put(fullKey(), value);
		if (java.util.Objects.equals(old, value)) return;
		STORE.markDirty();
		for (Runnable l : this.listeners) l.run();
	}

	/** A whole number between {@code min} and {@code max}, shown as a slider. */
	public static final class Number extends ServerSetting {
		public final int def;
		public final int min;
		public final int max;
		public final int step;
		/** Shown after the number, e.g. "blocks" or "%". */
		public final String unit;

		Number(Module module, String key, String label, Item icon, List<String> lore, int def, int min, int max, int step, String unit) {
			super(module, key, label, icon, lore);
			if (min > max || def < min || def > max || step <= 0) throw new IllegalArgumentException(key + ": bad range");
			this.def = def;
			this.min = min;
			this.max = max;
			this.step = step;
			this.unit = unit;
		}

		public int get() {
			String r = raw();
			if (r == null) return this.def;
			try {
				return clamp(Integer.parseInt(r));
			} catch (NumberFormatException e) {
				return this.def;
			}
		}

		public void set(int value) {
			int v = clamp(value);
			setRaw(v == this.def ? null : Integer.toString(v));
		}

		private int clamp(int v) {
			v = Math.max(this.min, Math.min(this.max, v));
			// Snap to the slider's steps
			return this.min + Math.round((v - this.min) / (float) this.step) * this.step;
		}

		@Override
		public String display() {
			return get() + (this.unit.isEmpty() ? "" : " " + this.unit);
		}

		@Override
		public void reset() {
			setRaw(null);
		}
	}

	/** On or off. */
	public static final class Toggle extends ServerSetting {
		public final boolean def;

		Toggle(Module module, String key, String label, Item icon, List<String> lore, boolean def) {
			super(module, key, label, icon, lore);
			this.def = def;
		}

		public boolean get() {
			String r = raw();
			return r == null ? this.def : r.equals("true");
		}

		public void set(boolean value) {
			setRaw(value == this.def ? null : Boolean.toString(value));
		}

		@Override
		public String display() {
			return get() ? "On" : "Off";
		}

		@Override
		public void reset() {
			setRaw(null);
		}
	}

	/** A line of text, e.g. a link or a welcome line. Empty means "not set". */
	public static final class Text extends ServerSetting {
		public final String def;
		public final int maxLength;

		Text(Module module, String key, String label, Item icon, List<String> lore, String def, int maxLength) {
			super(module, key, label, icon, lore);
			this.def = def;
			this.maxLength = maxLength;
		}

		public String get() {
			String r = raw();
			return r == null ? this.def : r;
		}

		public boolean isSet() {
			return !get().isBlank();
		}

		public void set(String value) {
			String v = value == null ? "" : value.strip();
			if (v.length() > this.maxLength) v = v.substring(0, this.maxLength);
			setRaw(v.equals(this.def) ? null : v);
		}

		@Override
		public String display() {
			return isSet() ? get() : "—";
		}

		@Override
		public void reset() {
			setRaw(null);
		}
	}
}
