package nl.libreria.chests;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import nl.libreria.core.Services;

/**
 * Supply chests: chests on your own land that you marked as such (shift-click the Sort button in the chest). When you
 * craft from the recipe book or a stack in your hotbar runs out, missing items come from them. A chest that isn't on
 * your land is never used, so nobody else's chest is touched.
 */
public final class Supply {
	/** Light cyan flash on the chests something was taken from. */
	private static final int FLASH = 0x55FFFF;

	private Supply() {
	}

	/** This player's supply chests in range (this world, loaded, still on their own land), nearest first. */
	static List<Store.Entry> chests(ServerPlayer p) {
		ServerLevel level = p.level();
		String dim = Store.dimId(level);
		double range = ChestsMod.SUPPLY_RANGE.get();
		List<Store.Entry> out = new ArrayList<>();
		for (Store.Entry e : Store.get(p.getUUID()).entries) {
			if (!e.supply || !e.dim.equals(dim)) continue;
			BlockPos pos = e.pos();
			if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > range * range) continue;
			if (!Containers.isLoaded(level, pos) || !Services.onOwnLand(p, level, pos)) continue;
			out.add(e);
		}
		out.sort(Comparator.comparingDouble(e -> p.distanceToSqr(e.x + 0.5, e.y + 0.5, e.z + 0.5)));
		return out;
	}

	/** How many items matching {@code test} the supply chests hold. */
	static int available(ServerPlayer p, Predicate<ItemStack> test) {
		int n = 0;
		for (Store.Entry e : chests(p)) {
			for (Container c : Containers.containersOf(p.level(), e.pos())) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					ItemStack st = c.getItem(i);
					if (!st.isEmpty() && test.test(st)) n += st.getCount();
				}
			}
		}
		return n;
	}

	/**
	 * Takes up to {@code count} items matching {@code test} out of the supply chests and hands them to {@code deliver},
	 * which returns what didn't fit (that goes back into the chest, and taking stops). The chests used flash briefly.
	 *
	 * @return the number of items taken
	 */
	static int take(ServerPlayer p, Predicate<ItemStack> test, int count, UnaryOperator<ItemStack> deliver) {
		if (count <= 0) return 0;
		ServerLevel level = p.level();
		int taken = 0;
		Set<Store.Entry> used = new LinkedHashSet<>();
		outer:
		for (Store.Entry e : chests(p)) {
			for (Container c : Containers.containersOf(level, e.pos())) {
				for (int i = 0; i < c.getContainerSize() && taken < count; i++) {
					ItemStack st = c.getItem(i);
					if (st.isEmpty() || !test.test(st)) continue;
					int n = Math.min(st.getCount(), count - taken);
					ItemStack part = st.split(n);
					ItemStack left = deliver.apply(part);
					int moved = n - (left == null ? 0 : left.getCount());
					if (left != null && !left.isEmpty()) {
						// No room: put the rest back and stop
						if (st.isEmpty()) st = left;
						else st.grow(left.getCount());
					}
					c.setItem(i, st.isEmpty() ? ItemStack.EMPTY : st);
					c.setChanged();
					if (moved > 0) {
						taken += moved;
						used.add(e);
					}
					if (left != null && !left.isEmpty()) break outer;
				}
				if (taken >= count) break outer;
			}
		}
		Store.PlayerData data = Store.get(p.getUUID());
		for (Store.Entry e : used) {
			Containers.refresh(level, e, data);
			Beam.flash(p, level, e.pos(), FLASH, 16);
		}
		return taken;
	}
}
