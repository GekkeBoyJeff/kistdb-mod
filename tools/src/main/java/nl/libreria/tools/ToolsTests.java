package nl.libreria.tools;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.SelfTests;

/** Zelftests van ToolsMod (alleen in de CI). */
final class ToolsTests {
	private ToolsTests() {
	}

	private static ItemStack worn(net.minecraft.world.item.Item item, int remaining) {
		ItemStack s = new ItemStack(item);
		s.setDamageValue(s.getMaxDamage() - remaining);
		return s;
	}

	static void register() {
		SelfTests.register("gereedschap.soorten", level -> {
			SelfTests.check("pickaxe".equals(Tools.kind(new ItemStack(Items.DIAMOND_PICKAXE))), "houweel");
			SelfTests.check("axe".equals(Tools.kind(new ItemStack(Items.IRON_AXE))), "bijl");
			SelfTests.check("minecraft:shears".equals(Tools.kind(new ItemStack(Items.SHEARS))), "schaar");
			SelfTests.check(Tools.kind(new ItemStack(Items.STONE)) == null, "steen is geen tool");
			SelfTests.check(Tools.remaining(worn(Items.IRON_PICKAXE, 7)) == 7, "durability");
		});

		SelfTests.register("gereedschap.breekgrens-en-wissel", level -> {
			FakePlayer p = FakePlayer.get(level);
			Inventory inv = p.getInventory();
			inv.clearContent();
			inv.setSelectedSlot(0);
			inv.setItem(0, worn(Items.IRON_PICKAXE, 3));
			inv.setItem(20, worn(Items.STONE_PICKAXE, 100));
			inv.setItem(21, worn(Items.DIAMOND_PICKAXE, 2));

			// Grens 5: de diamanten (2 over) telt niet, de stenen wel
			int r = Tools.findReplacement(inv, "pickaxe", inv.getItem(0), 5, 0);
			SelfTests.check(r == 20, "vervanger slot 20, was " + r);
			// Grens 0 (mag breken): de diamanten is sterker en wint
			int r0 = Tools.findReplacement(inv, "pickaxe", new ItemStack(Items.GOLDEN_PICKAXE), 0, 0);
			SelfTests.check(r0 == 21, "sterkste wint bij grens 0, was " + r0);
			SelfTests.check(Tools.spares(inv, "pickaxe", 5, 0) == 1, "1 reserve boven de grens");

			ToolsMod.KEEP.set(p, 5);
			ToolsMod.SWAP.set(p, true);
			SelfTests.check(ToolsMod.guard(p), "guard wisselt en laat door");
			SelfTests.check(inv.getItem(0).is(Items.STONE_PICKAXE), "stenen houweel in de hand");
			SelfTests.check(inv.getItem(20).is(Items.IRON_PICKAXE), "ijzeren houweel weggelegd");

			inv.clearContent();
			inv.setItem(0, worn(Items.IRON_PICKAXE, 3));
			SelfTests.check(!ToolsMod.guard(p), "geen vervanger: blokkeren");
			ToolsMod.KEEP.set(p, 0);
			SelfTests.check(ToolsMod.guard(p), "grens uit: mag gewoon");

			// Gebroken tool: de volgende komt in de hand
			inv.clearContent();
			inv.setItem(30, worn(Items.IRON_AXE, 200));
			ToolsMod.onBroken(p, new ItemStack(Items.DIAMOND_AXE), EquipmentSlot.MAINHAND);
			SelfTests.check(ToolsMod.restock(p, "axe", new ItemStack(Items.DIAMOND_AXE), 0), "restock gelukt");
			SelfTests.check(inv.getItem(0).is(Items.IRON_AXE) && inv.getItem(30).isEmpty(), "bijl verplaatst");

			// Bijvullen: lege hand-slot krijgt dezelfde stapel
			inv.clearContent();
			inv.setItem(15, new ItemStack(Items.COBBLESTONE, 64));
			SelfTests.check(ToolsMod.refill(p, 0, new ItemStack(Items.COBBLESTONE)), "bijvullen gelukt");
			SelfTests.check(inv.getItem(0).getCount() == 64 && inv.getItem(15).isEmpty(), "stapel in de hand");
			inv.clearContent();
		});
	}
}
