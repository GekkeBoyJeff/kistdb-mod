package nl.libreria.chests;

import nl.libreria.core.Ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Samenvoegen + sorteren. Volgorde: registervolgorde (groepeert stenen, hout, ertsen …), dan naam. */
public final class Sorter {
	private static final Comparator<ItemStack> ORDER = Comparator
			.<ItemStack>comparingInt(s -> BuiltInRegistries.ITEM.getId(s.getItem()))
			.thenComparing(s -> s.getHoverName().getString())
			.thenComparingInt(s -> s.getComponentsPatch().hashCode());

	private Sorter() {
	}

	/** Sorteert slots 9-35 van de speler (hotbar, armor en offhand blijven staan). */
	public static boolean sortPlayer(ServerPlayer player) {
		Inventory inv = player.getInventory();
		boolean ok = sort(27, i -> inv.getItem(9 + i), (stack, i) -> inv.setItem(9 + i, stack));
		if (ok) {
			inv.setChanged();
			feedback(player);
		}
		return ok;
	}

	/** Sorteert alle slots van één container binnen een menu (bij een dubbele kist alle 54). */
	public static boolean sortContainer(ServerPlayer player, AbstractContainerMenu menu, Container container) {
		List<Slot> slots = new ArrayList<>();
		for (Slot s : menu.slots) {
			if (s.container == container) slots.add(s);
		}
		if (slots.isEmpty()) return false;
		boolean ok = sort(slots.size(), i -> slots.get(i).getItem(), (stack, i) -> slots.get(i).set(stack));
		if (ok) {
			container.setChanged();
			feedback(player);
		}
		return ok;
	}

	/** Sorteert een container rechtstreeks (zonder menu), bv. voor tests. */
	static boolean sortDirect(Container container) {
		boolean ok = sort(container.getContainerSize(), container::getItem, (stack, i) -> container.setItem(i, stack));
		if (ok) container.setChanged();
		return ok;
	}

	private static boolean sort(int size, IntFunction<ItemStack> get, ObjIntConsumer<ItemStack> set) {
		// 1. samenvoegen (zelfde item + zelfde componenten)
		List<ItemStack> merged = new ArrayList<>();
		long total = 0;
		outer:
		for (int i = 0; i < size; i++) {
			ItemStack s = get.apply(i);
			if (s.isEmpty()) continue;
			total += s.getCount();
			for (ItemStack m : merged) {
				if (ItemStack.isSameItemSameComponents(m, s)) {
					m.setCount(m.getCount() + s.getCount());
					continue outer;
				}
			}
			merged.add(s.copy());
		}
		// 2. sorteren
		merged.sort(ORDER);
		// 3. weer opsplitsen in volle stapels
		List<ItemStack> out = new ArrayList<>();
		long check = 0;
		for (ItemStack m : merged) {
			int left = m.getCount();
			int max = Math.max(1, m.getMaxStackSize());
			while (left > 0) {
				int n = Math.min(left, max);
				out.add(m.copyWithCount(n));
				check += n;
				left -= n;
			}
		}
		// Veiligheid: past het niet of klopt het totaal niet, dan niets veranderen
		if (out.size() > size || check != total) return false;
		for (int i = 0; i < size; i++) {
			set.accept(i < out.size() ? out.get(i) : ItemStack.EMPTY, i);
		}
		return true;
	}

	private static void feedback(ServerPlayer player) {
		Ui.sound(player, SoundEvents.ITEM_PICKUP, 0.5f, 1.4f);
	}
}
