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
 * Quick stack: items from your inventory (not your hotbar) go into chests nearby that already hold that item. Only
 * chests from your own chest index, not on noIndex, and only your own ({@link ChestOwners}): never someone else's.
 */
public final class QuickStack {
	record Target(@org.jetbrains.annotations.Nullable Store.Entry entry, BlockPos key, List<Container> containers, double distance) {
	}

	private QuickStack() {
	}

	/**
	 * Every chest, barrel and shulker box of yours within reach, nearest first. Found in the world itself (not only
	 * the chests in your index), so a chest you never opened, or one a hopper fills, counts too. Skipped: someone
	 * else's, unopened loot chests, and chests you left out of your index.
	 */
	static List<Target> targets(ServerPlayer player) {
		ServerLevel level = player.level();
		String dim = Store.dimId(level);
		Vec3 pos = player.position();
		int radius = ChestsMod.STACK_RADIUS.get(player);
		Store.PlayerData data = Store.get(player.getUUID());
		List<Target> targets = new ArrayList<>();
		Set<BlockPos> seen = new java.util.HashSet<>();
		int cx0 = ((int) Math.floor(pos.x) - radius) >> 4, cx1 = ((int) Math.floor(pos.x) + radius) >> 4;
		int cz0 = ((int) Math.floor(pos.z) - radius) >> 4, cz1 = ((int) Math.floor(pos.z) + radius) >> 4;
		for (int cx = cx0; cx <= cx1; cx++) {
			for (int cz = cz0; cz <= cz1; cz++) {
				var chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) continue;
				for (var be : new ArrayList<>(chunk.getBlockEntities().values())) {
					if (!Containers.isStorage(be)) continue;
					BlockPos key = Containers.keyOf(level, be.getBlockPos());
					if (!seen.add(key)) continue;
					double d = pos.distanceTo(Containers.boxOf(level, key).getCenter());
					if (d > radius) continue;
					if (!ChestOwners.isOwn(player, level, key)) continue;
					Store.Entry e = data.find(dim, key);
					if (e != null && e.noIndex) continue;
					List<Container> cs = Containers.containersOf(level, key);
					if (!cs.isEmpty()) targets.add(new Target(e, key, cs, d));
				}
			}
		}
		targets.sort(Comparator.comparingDouble(Target::distance));
		return targets;
	}

	/** @return het aantal verplaatste items */
	public static int run(ServerPlayer player) {
		ServerLevel level = player.level();
		int radius = ChestsMod.STACK_RADIUS.get(player);
		List<Target> targets = targets(player);
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
			// A chest of yours that wasn't in your index yet is from now on (so search finds what you put in it)
			Store.Entry e = t.entry();
			if (e == null) {
				Store.track(player.getUUID(), level, t.key(), Containers.blockId(level, t.key()));
				e = data.find(Store.dimId(level), t.key());
			}
			if (e != null) Containers.refresh(level, e, data);
			Beam.flash(player, level, t.key(), 0x55FF55, 30);
		}
		if (moved > 0) {
			inv.setChanged();
			Ui.sound(player, SoundEvents.ITEM_PICKUP, 0.6f, 0.8f);
			Hud.flash(player, Component.literal("✔ Stored " + moved + (moved == 1 ? " item" : " items") + " in " + used.size() + (used.size() == 1 ? " chest" : " chests"))
					.withStyle(ChatFormatting.GREEN));
		} else if (targets.isEmpty()) {
			Hud.flash(player, Component.literal("None of your own chests within " + radius + " blocks").withStyle(ChatFormatting.GRAY));
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
