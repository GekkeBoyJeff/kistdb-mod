package nl.libreria.chests;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Hud;
import nl.libreria.core.Ui;

/**
 * Snel opbergen: items uit je inventory (niet je hotbar) gaan naar kisten in de buurt die dat item al bevatten.
 * Alleen kisten uit je eigen ChestsMod en niet op noIndex.
 */
public final class QuickStack {
	private record Target(Store.Entry entry, List<Container> containers, double distance) {
	}

	private QuickStack() {
	}

	/** @return het aantal verplaatste items */
	public static int run(ServerPlayer player) {
		ServerLevel level = player.level();
		String dim = Store.dimId(level);
		Vec3 pos = player.position();
		int radius = ChestsMod.STACK_RADIUS.get(player);

		List<Target> targets = new ArrayList<>();
		for (Store.Entry e : new ArrayList<>(Store.get(player.getUUID()).entries)) {
			if (!e.dim.equals(dim) || e.noIndex) continue;
			BlockPos key = e.pos();
			double d = pos.distanceTo(Vec3.atCenterOf(key));
			if (d > radius || !Containers.isLoaded(level, key)) continue;
			List<Container> cs = Containers.containersOf(level, key);
			if (!cs.isEmpty()) targets.add(new Target(e, cs, d));
		}
		targets.sort(Comparator.comparingDouble(Target::distance));

		Inventory inv = player.getInventory();
		int moved = 0;
		Set<Target> used = new LinkedHashSet<>();
		for (int slot = 9; slot < 36; slot++) {
			ItemStack stack = inv.getItem(slot);
			if (stack.isEmpty()) continue;
			for (Target t : targets) {
				if (!contains(t.containers(), stack)) continue;
				int n = insert(t.containers(), stack);
				if (n > 0) {
					moved += n;
					used.add(t);
				}
				if (stack.isEmpty()) break;
			}
			inv.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
		}

		Store.PlayerData data = Store.get(player.getUUID());
		for (Target t : used) {
			Containers.refresh(level, t.entry(), data);
			Beam.flash(player, level, t.entry().pos(), 0x55FF55, 30);
		}
		if (moved > 0) {
			inv.setChanged();
			Ui.sound(player, SoundEvents.ITEM_PICKUP, 0.6f, 0.8f);
			Hud.flash(player, Component.literal("✔ Stored " + moved + (moved == 1 ? " item" : " items") + " in " + used.size() + (used.size() == 1 ? " chest" : " chests"))
					.withStyle(ChatFormatting.GREEN));
		} else if (targets.isEmpty()) {
			Hud.flash(player, Component.literal("No chests from your chest index within " + radius + " blocks").withStyle(ChatFormatting.GRAY));
		} else {
			Hud.flash(player, Component.literal("Nothing to stack: no nearby chest has these items yet").withStyle(ChatFormatting.GRAY));
		}
		return moved;
	}

	/** True if one of the containers already holds this item (same item and components). */
	static boolean contains(List<Container> cs, ItemStack stack) {
		for (Container c : cs) {
			for (int i = 0; i < c.getContainerSize(); i++) {
				if (ItemStack.isSameItemSameComponents(c.getItem(i), stack)) return true;
			}
		}
		return false;
	}

	/** Eerst aanvullen op bestaande stapels, dan lege slots. Past {@code stack} aan; geeft het aantal verplaatst. */
	static int insert(List<Container> cs, ItemStack stack) {
		int before = stack.getCount();
		for (Container c : cs) {
			for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
				ItemStack t = c.getItem(i);
				if (t.isEmpty() || !ItemStack.isSameItemSameComponents(t, stack)) continue;
				int room = Math.min(c.getMaxStackSize(), t.getMaxStackSize()) - t.getCount();
				if (room <= 0) continue;
				int n = Math.min(room, stack.getCount());
				t.grow(n);
				stack.shrink(n);
				c.setChanged();
			}
		}
		for (Container c : cs) {
			for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
				if (!c.getItem(i).isEmpty() || !c.canPlaceItem(i, stack)) continue;
				int n = Math.min(stack.getCount(), Math.min(c.getMaxStackSize(), stack.getMaxStackSize()));
				c.setItem(i, stack.split(n));
				c.setChanged();
			}
		}
		return before - stack.getCount();
	}
}
