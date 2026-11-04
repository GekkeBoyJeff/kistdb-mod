package nl.libreria.chests;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import nl.libreria.core.Hud;
import nl.libreria.core.Ui;

/**
 * Hotbar refill from supply chests: when you use up the last block, food or arrow of a stack in your hotbar (or a tool
 * breaks) and your inventory has no more, a new stack comes from your supply chests. Only when it was really used up
 * (the game's "used"/"broken" statistics went up), never when you just moved or dropped it. Refilling from your own
 * inventory is the Tools module's job; without Tools, this class does that too.
 */
public final class Refill {
	/** What a hotbar slot held last tick, with the statistics at that moment. */
	private record Seen(Item item, ItemStack sample, int used, int broken) {
	}

	private static final Map<UUID, Seen[]> LAST = new HashMap<>();
	/** With the Tools module, refilling from the inventory is its job; this class only adds the supply chests. */
	private static final boolean TOOLS = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("libreria_tools");

	private Refill() {
	}

	static void tick(ServerPlayer p) {
		if (!ChestsMod.REFILL.get(p) || p.isSpectator() || p.isCreative()) {
			LAST.remove(p.getUUID());
			return;
		}
		Inventory inv = p.getInventory();
		Seen[] last = LAST.computeIfAbsent(p.getUUID(), k -> new Seen[9]);
		for (int i = 0; i < 9; i++) {
			ItemStack now = inv.getItem(i);
			Seen before = last[i];
			if (before != null && now.isEmpty() && usedUp(p, before)) refill(p, i, before);
			ItemStack after = inv.getItem(i);
			last[i] = after.isEmpty() ? null : new Seen(after.getItem(), after.copyWithCount(1),
					p.getStats().getValue(Stats.ITEM_USED.get(after.getItem())), p.getStats().getValue(Stats.ITEM_BROKEN.get(after.getItem())));
		}
	}

	private static boolean usedUp(ServerPlayer p, Seen s) {
		return p.getStats().getValue(Stats.ITEM_USED.get(s.item())) > s.used()
				|| p.getStats().getValue(Stats.ITEM_BROKEN.get(s.item())) > s.broken();
	}

	/** Same item, and for things that stack: the same item exactly (same name, same potion…). */
	private static boolean same(ItemStack candidate, Seen s) {
		if (!candidate.is(s.item())) return false;
		return !s.sample().isStackable() || ItemStack.isSameItemSameComponents(candidate, s.sample());
	}

	private static void refill(ServerPlayer p, int slot, Seen s) {
		Inventory inv = p.getInventory();
		// 1. From your own inventory (not the hotbar). The Tools module does this itself when it's installed.
		for (int i = 35; i >= 9; i--) {
			ItemStack st = inv.getItem(i);
			if (!st.isEmpty() && same(st, s)) {
				if (TOOLS) return;
				inv.setItem(slot, st);
				inv.setItem(i, ItemStack.EMPTY);
				inv.setChanged();
				Ui.sound(p, SoundEvents.ITEM_PICKUP, 0.3f, 1.6f);
				return;
			}
		}
		// 2. From your supply chests
		int max = s.sample().getMaxStackSize();
		ItemStack[] got = {ItemStack.EMPTY};
		int took = Supply.take(p, st -> same(st, s), max, part -> {
			if (got[0].isEmpty()) got[0] = part.copy();
			else got[0].grow(part.getCount());
			return ItemStack.EMPTY;
		});
		if (took > 0 && !got[0].isEmpty()) {
			inv.setItem(slot, got[0]);
			inv.setChanged();
			Ui.sound(p, SoundEvents.ITEM_PICKUP, 0.4f, 1.3f);
			Hud.flash(p, Component.literal("✔ Refilled from your supply chests: ").withStyle(ChatFormatting.AQUA)
					.append(got[0].getHoverName().copy().withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" ×" + got[0].getCount()).withStyle(ChatFormatting.GRAY)));
		}
	}

	static void forget(UUID id) {
		LAST.remove(id);
	}
}
