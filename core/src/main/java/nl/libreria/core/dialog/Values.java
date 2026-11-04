package nl.libreria.core.dialog;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * What a player filled in in a {@link Window}: one value per input key. The client sends these, so every reader
 * falls back to a default instead of trusting the shape.
 */
public final class Values {
	private final CompoundTag tag;

	Values(CompoundTag tag) {
		this.tag = tag;
	}

	public static Values of(CompoundTag tag) {
		return new Values(tag == null ? new CompoundTag() : tag);
	}

	public boolean has(String key) {
		return this.tag.get(key) != null;
	}

	/** A checkbox (sent as a byte, or as the text "true"/"false"). */
	public boolean bool(String key, boolean def) {
		Tag t = this.tag.get(key);
		if (t instanceof NumericTag n) return n.byteValue() != 0;
		if (t instanceof StringTag s) return s.value().equalsIgnoreCase("true");
		return def;
	}

	/** A text field or the id of the chosen option. */
	public String text(String key, String def) {
		Tag t = this.tag.get(key);
		return t instanceof StringTag s ? s.value() : def;
	}

	/** A slider (sent as a float). */
	public float number(String key, float def) {
		Tag t = this.tag.get(key);
		if (t instanceof NumericTag n) return n.floatValue();
		if (t instanceof StringTag s) {
			try {
				return Float.parseFloat(s.value());
			} catch (NumberFormatException e) {
				return def;
			}
		}
		return def;
	}

	public int integer(String key, int def) {
		return Math.round(number(key, def));
	}

	@Override
	public String toString() {
		return this.tag.toString();
	}
}
