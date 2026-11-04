package nl.libreria.chests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import nl.libreria.core.Hud;
import nl.libreria.core.Ui;

/**
 * Crafting from supply chests: you click a recipe in the recipe book (crafting table or your inventory) and what you
 * miss comes out of your supply chests into your inventory first. Then the game places the recipe as always.
 */
public final class CraftSupply {
	private CraftSupply() {
	}

	/** From the mixin, on the server thread, before the game places the recipe. */
	public static void beforePlace(ServerPlayer p, ServerboundPlaceRecipePacket packet) {
		if (!ChestsMod.CRAFT.get(p) || p.isSpectator() || p.isCreative()) return;
		if (p.containerMenu.containerId != packet.containerId() || !(p.containerMenu instanceof AbstractCraftingMenu menu)) return;
		RecipeManager.ServerDisplayInfo info = p.level().getServer().getRecipeManager().getRecipeFromDisplay(packet.recipe());
		if (info == null) return;
		RecipeHolder<?> holder = info.parent();
		if (!(holder.value() instanceof CraftingRecipe recipe)) return;
		var placement = recipe.placementInfo();
		if (placement.isImpossibleToPlace()) return;
		List<Ingredient> ingredients = placement.ingredients();
		if (ingredients.isEmpty() || ingredients.size() > menu.getGridWidth() * menu.getGridHeight()) return;

		int times = packet.useMaxItems() ? timesForStack(p, info) : 1;
		// What you have: inventory plus what lies in the grid (the game puts that back first)
		Map<Item, Integer> pool = new HashMap<>();
		Inventory inv = p.getInventory();
		for (int i = 0; i < 36; i++) count(pool, inv.getItem(i));
		for (Slot s : menu.getInputGridSlots()) count(pool, s.getItem());

		// Per ingredient, how many are missing
		List<Ingredient> missing = new ArrayList<>();
		List<Integer> missingCount = new ArrayList<>();
		for (Ingredient ing : ingredients) {
			int need = times;
			for (Map.Entry<Item, Integer> e : pool.entrySet()) {
				if (need == 0) break;
				if (e.getValue() <= 0 || !ing.test(new ItemStack(e.getKey()))) continue;
				int use = Math.min(need, e.getValue());
				e.setValue(e.getValue() - use);
				need -= use;
			}
			if (need > 0) {
				missing.add(ing);
				missingCount.add(need);
			}
		}
		if (missing.isEmpty()) return;

		int took = 0;
		for (int i = 0; i < missing.size(); i++) {
			Ingredient ing = missing.get(i);
			took += Supply.take(p, ing::test, missingCount.get(i), part -> {
				inv.add(part);
				return part;
			});
		}
		if (took > 0) {
			inv.setChanged();
			Ui.sound(p, SoundEvents.ITEM_PICKUP, 0.5f, 1.3f);
			Hud.flash(p, Component.literal("✔ " + took + (took == 1 ? " item" : " items") + " from your supply chests")
					.withStyle(ChatFormatting.AQUA));
		}
	}

	/** Shift-click in the recipe book: enough for one full stack of the result (at most 64 crafts). */
	private static int timesForStack(ServerPlayer p, RecipeManager.ServerDisplayInfo info) {
		try {
			ItemStack result = info.display().display().result()
					.resolveForFirstStack(net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(p.level()));
			if (result.isEmpty()) return 1;
			return Math.max(1, Math.min(64, result.getMaxStackSize() / Math.max(1, result.getCount())));
		} catch (RuntimeException e) {
			return 1;
		}
	}

	private static void count(Map<Item, Integer> pool, ItemStack st) {
		if (!st.isEmpty()) pool.merge(st.getItem(), st.getCount(), Integer::sum);
	}
}
