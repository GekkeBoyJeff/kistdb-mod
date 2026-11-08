package nl.libreria.chests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import nl.libreria.core.ClickRouter;
import nl.libreria.core.Guide;
import nl.libreria.core.ItemView;
import nl.libreria.core.Pack;
import org.jetbrains.annotations.Nullable;

/**
 * What you searched for lights up: in the chest it lies in (the rest of that chest dims), in your inventory and in your
 * hotbar. Only in what your client sees; the items stay as they are. With the resource pack a glow behind it and a
 * pulsing border (custom model data flags the item models read); without it the enchantment shimmer.
 * <p>
 * For a minute after the search (the minute waits while you're still following the way there), and over sooner once
 * you've seen it: you close a chest (or your inventory) it was in, or you click it.
 */
public final class Wanted {
	/** How long it stays lit, in ticks. */
	static final int TICKS = 60 * 20;
	/** The flags in the item models (see scripts/glint/generate.py). */
	static final List<Boolean> LIT = List.of(true), DIMMED = List.of(false, true);

	static final class Hunt {
		final Item item;
		long until;

		Hunt(Item item, long until) {
			this.item = item;
			this.until = until;
		}
	}

	static final Map<UUID, Hunt> HUNTS = new HashMap<>();

	private Wanted() {
	}

	static void init() {
		ItemView.add(Wanted::view);
		ItemView.cleaner(Wanted::clean);
		ClickRouter.registerClose(Wanted::closed);
		ClickRouter.register((player, menu, slot, input, button) -> {
			// You picked it up (or moved it): found
			if (slot >= 0 && slot < menu.slots.size() && looking(player, menu.getSlot(slot).getItem())) later(player);
			return false;
		});
	}

	/** You searched for this (and a chest with it lights up, or you carry it). */
	public static void start(ServerPlayer p, @Nullable ItemStack what) {
		if (!ChestsMod.MODULE.on()) return;
		if (what == null || what.isEmpty()) return;
		HUNTS.put(p.getUUID(), new Hunt(what.getItem(), now(p) + TICKS));
		ItemView.resend(p);
	}

	public static @Nullable Item of(ServerPlayer p) {
		Hunt h = HUNTS.get(p.getUUID());
		return h == null ? null : h.item;
	}

	static boolean looking(ServerPlayer p, ItemStack s) {
		Item i = of(p);
		return i != null && !s.isEmpty() && s.is(i);
	}

	static void end(ServerPlayer p) {
		if (HUNTS.remove(p.getUUID()) != null) ItemView.resend(p);
	}

	/** Over, and what your client shows told again once the click or close it came from is done. */
	private static void later(ServerPlayer p) {
		if (HUNTS.remove(p.getUUID()) == null) return;
		p.level().getServer().execute(() -> ItemView.resend(p));
	}

	static void forget(ServerPlayer p) {
		HUNTS.remove(p.getUUID());
	}

	private static long now(ServerPlayer p) {
		return p.level().getServer().getTickCount();
	}

	/** Every second: still on your way there (the beam is on), the minute waits; after it, it's over. */
	static void tick(ServerPlayer p) {
		Hunt h = HUNTS.get(p.getUUID());
		if (h == null) return;
		long now = now(p);
		if (Guide.has(p)) h.until = Math.max(h.until, now + TICKS);
		else if (now > h.until) end(p);
	}

	/** A chest (or your inventory) closes: if what you look for was in it, you've seen it. */
	static void closed(ServerPlayer p, AbstractContainerMenu menu) {
		Item item = of(p);
		if (item == null || !real(p, menu)) return;
		for (var slot : menu.slots) {
			if (slot.getItem().is(item)) {
				later(p);
				return;
			}
		}
	}

	/** Storage and your own inventory; not Libreria's own windows (their items are buttons). */
	static boolean real(ServerPlayer p, AbstractContainerMenu menu) {
		return menu == p.inventoryMenu || menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;
	}

	/** Slots before your own inventory: the chest's own. */
	static boolean chestSlot(AbstractContainerMenu menu, int slot) {
		return slot >= 0 && slot < menu.slots.size() && !(menu.getSlot(slot).container instanceof Inventory);
	}

	/** The chest itself (not your inventory below it) holds it. */
	static boolean chestHolds(AbstractContainerMenu menu, Item item) {
		for (var slot : menu.slots) if (!(slot.container instanceof Inventory) && slot.getItem().is(item)) return true;
		return false;
	}

	private static ItemStack view(ServerPlayer viewer, AbstractContainerMenu menu, int slot, ItemStack stack) {
		if (!ChestsMod.MODULE.on()) return stack;
		Hunt h = HUNTS.get(viewer.getUUID());
		if (h == null || ItemView.creative(viewer) || !real(viewer, menu)) return stack;
		return view(stack, h.item, menu, slot, Pack.has(viewer));
	}

	/** How one slot looks while you look for {@code item}. */
	static ItemStack view(ItemStack stack, Item item, AbstractContainerMenu menu, int slot, boolean pack) {
		if (stack.isEmpty()) return stack;
		if (stack.is(item)) {
			ItemStack out = stack.copy();
			if (pack) out.set(DataComponents.CUSTOM_MODEL_DATA, flags(stack, LIT));
			else out.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			return out;
		}
		// The rest of a chest it lies in goes darker (only with the pack: it's a picture over the item)
		if (pack && (menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu)) {
			if (chestSlot(menu, slot) && chestHolds(menu, item)) {
				ItemStack out = stack.copy();
				out.set(DataComponents.CUSTOM_MODEL_DATA, flags(stack, DIMMED));
				return out;
			}
		}
		return stack;
	}

	private static CustomModelData flags(ItemStack stack, List<Boolean> flags) {
		CustomModelData had = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		return had == null ? new CustomModelData(List.of(), flags, List.of(), List.of())
				: new CustomModelData(had.floats(), flags, had.strings(), had.colors());
	}

	/** What a creative client sends back: our flags out again. */
	static ItemStack clean(ItemStack stack) {
		CustomModelData had = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		if (had == null || !(had.flags().equals(LIT) || had.flags().equals(DIMMED))) return stack;
		ItemStack out = stack.copy();
		if (had.floats().isEmpty() && had.strings().isEmpty() && had.colors().isEmpty()) out.remove(DataComponents.CUSTOM_MODEL_DATA);
		else out.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(had.floats(), new ArrayList<>(), had.strings(), had.colors()));
		return out;
	}
}
