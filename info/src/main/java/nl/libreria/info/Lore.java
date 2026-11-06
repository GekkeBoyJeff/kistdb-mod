package nl.libreria.info;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * An item's tooltip lines that are ours, in a fixed order after whatever else is there: what it was found as
 * ({@link Found}), the owner's own description ({@link Describe}), and last the weapon's story ({@link Weapons}, which
 * manages its own lines at the very end). Each part can be replaced without touching the others.
 */
final class Lore {
	static final String KEY = "libreria_lore";
	static final String FOUND = "found", OWN = "own";
	private static final String[] ORDER = {FOUND, OWN};

	private Lore() {
	}

	/** Replaces one part of the tooltip lines ({@code lines} empty: takes it away). */
	static void set(ItemStack stack, String part, List<Component> lines) {
		ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		List<Component> all = lore.lines();
		CompoundTag counts = counts(stack);
		int story = Math.min(Weapons.story(stack).getIntOr("lore", 0), all.size());
		int ours = 0;
		for (String p : ORDER) ours += counts.getIntOr(p, 0);
		ours = Math.min(ours, all.size() - story);
		int external = all.size() - story - ours;
		List<Component> out = new ArrayList<>(all.subList(0, external));
		int at = external;
		for (String p : ORDER) {
			int n = Math.min(counts.getIntOr(p, 0), all.size() - story - at);
			List<Component> mine = all.subList(at, at + n);
			at += n;
			if (p.equals(part)) {
				out.addAll(lines);
				counts.putInt(p, lines.size());
			} else {
				out.addAll(mine);
			}
		}
		out.addAll(all.subList(all.size() - story, all.size()));
		if (out.size() > 200) out = out.subList(0, 200);
		if (out.isEmpty()) stack.remove(DataComponents.LORE);
		else stack.set(DataComponents.LORE, new ItemLore(List.copyOf(out)));
		CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(KEY, counts));
	}

	/** The lines of one part, as they are now. */
	static List<Component> get(ItemStack stack, String part) {
		List<Component> all = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
		CompoundTag counts = counts(stack);
		int story = Math.min(Weapons.story(stack).getIntOr("lore", 0), all.size());
		int ours = 0;
		for (String p : ORDER) ours += counts.getIntOr(p, 0);
		ours = Math.min(ours, all.size() - story);
		int at = all.size() - story - ours;
		for (String p : ORDER) {
			int n = Math.min(counts.getIntOr(p, 0), all.size() - story - at);
			if (p.equals(part)) return List.copyOf(all.subList(at, at + n));
			at += n;
		}
		return List.of();
	}

	private static CompoundTag counts(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompoundOrEmpty(KEY);
	}

	/** One tooltip line in a colour, not in the italic purple of lore. */
	static MutableComponent line(String text, int rgb, boolean italic) {
		return Component.literal(text).withStyle(Style.EMPTY.withColor(rgb).withItalic(italic));
	}

	/** Text broken into lines of about {@code width} characters, at spaces. */
	static List<String> wrap(String text, int width) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+")) {
			if (line.length() > 0 && line.length() + 1 + word.length() > width) {
				out.add(line.toString());
				line.setLength(0);
			}
			if (line.length() > 0) line.append(' ');
			line.append(word);
		}
		if (line.length() > 0) out.add(line.toString());
		return out;
	}

	static String cap(String s) {
		return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
	}
}
