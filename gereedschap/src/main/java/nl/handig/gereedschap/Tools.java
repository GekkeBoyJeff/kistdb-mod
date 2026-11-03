package nl.handig.gereedschap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Pure logica rond tools (zonder events), zodat het ook in de zelftests te testen is. */
public final class Tools {
	private Tools() {
	}

	/** Soort tool: houweel, bijl, schep, schoffel, zwaard; anders het item zelf (bv. schaar, hengel). null = geen tool. */
	public static @Nullable String kind(ItemStack s) {
		if (s.isEmpty() || !s.isDamageableItem()) return null;
		if (s.is(ItemTags.PICKAXES)) return "pickaxe";
		if (s.is(ItemTags.AXES)) return "axe";
		if (s.is(ItemTags.SHOVELS)) return "shovel";
		if (s.is(ItemTags.HOES)) return "hoe";
		if (s.is(ItemTags.SWORDS)) return "sword";
		return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
	}

	public static int remaining(ItemStack s) {
		return s.getMaxDamage() - s.getDamageValue();
	}

	/** Mag deze tool nog gebruikt worden met deze breekgrens (0 = mag breken)? */
	public static boolean usable(ItemStack s, int keep) {
		return keep <= 0 || remaining(s) > keep;
	}

	/**
	 * Zoekt de beste vervanger in de inventory (slots 0-35, behalve {@code exclude}).
	 * Voorkeur: zelfde betoveringen (silk touch blijft silk touch), dan hetzelfde item, dan de sterkste, dan de meeste durability.
	 * @return slot of -1
	 */
	public static int findReplacement(Inventory inv, String kind, ItemStack ref, int keep, int exclude) {
		int best = -1;
		long bestScore = Long.MIN_VALUE;
		for (int i = 0; i < 36; i++) {
			if (i == exclude) continue;
			ItemStack c = inv.getItem(i);
			if (!kind.equals(kind(c)) || !usable(c, keep) || remaining(c) <= 0) continue;
			long score = 0;
			if (!ref.isEmpty() && c.getEnchantments().equals(ref.getEnchantments())) score += 1L << 40;
			if (!ref.isEmpty() && c.getItem() == ref.getItem()) score += 1L << 39;
			score += ((long) c.getMaxDamage()) << 16;
			score += remaining(c);
			if (score > bestScore) {
				bestScore = score;
				best = i;
			}
		}
		return best;
	}

	/** Wisselt twee slots. */
	public static void swap(Inventory inv, int a, int b) {
		ItemStack x = inv.getItem(a);
		ItemStack y = inv.getItem(b);
		inv.setItem(a, y);
		inv.setItem(b, x);
		inv.setChanged();
	}

	/** Aantal andere bruikbare tools van dezelfde soort. */
	public static int spares(Inventory inv, String kind, int keep, int exclude) {
		int n = 0;
		for (int i = 0; i < 36; i++) {
			if (i == exclude) continue;
			ItemStack c = inv.getItem(i);
			if (kind.equals(kind(c)) && usable(c, keep) && remaining(c) > 0) n++;
		}
		return n;
	}

	/** Zoekt dezelfde stapel (zelfde item + componenten) om bij te vullen; eerst buiten de hotbar. @return slot of -1 */
	public static int findRefill(Inventory inv, ItemStack ref, int exclude) {
		for (int pass = 0; pass < 2; pass++) {
			int from = pass == 0 ? 9 : 0;
			int to = pass == 0 ? 36 : 9;
			for (int i = from; i < to; i++) {
				if (i == exclude) continue;
				ItemStack c = inv.getItem(i);
				if (!c.isEmpty() && ItemStack.isSameItemSameComponents(c, ref)) return i;
			}
		}
		return -1;
	}
}
