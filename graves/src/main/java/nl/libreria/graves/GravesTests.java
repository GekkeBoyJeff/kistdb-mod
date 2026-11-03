package nl.libreria.graves;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.SelfTests;

/** Zelftest: spullen gaan het graf in en komen terug in dezelfde vakken. */
final class GravesTests {
	private GravesTests() {
	}

	static void register() {
		SelfTests.register("graves.store-and-restore", level -> {
			FakePlayer p = FakePlayer.get(level);
			p.getInventory().clearContent();
			p.snapTo(70.5, 120, 70.5);
			GravesMod.ENABLED.set(p, true);
			p.getInventory().setItem(3, new ItemStack(Items.DIAMOND, 7));
			p.getInventory().setItem(20, new ItemStack(Items.IRON_PICKAXE));
			SelfTests.check(GravesMod.intoGrave(p, level), "everything went into the grave");
			SelfTests.check(p.getInventory().getItem(3).isEmpty(), "inventory is empty after dying");
			GravesMod.Grave g = GravesMod.newestOf(p);
			SelfTests.check(g != null && g.count() == 8, "grave holds 8 items");
			SelfTests.check(GravesMod.restore(p, g) == 8, "8 items returned");
			SelfTests.check(p.getInventory().getItem(3).is(Items.DIAMOND) && p.getInventory().getItem(3).getCount() == 7, "diamonds in slot 3");
			SelfTests.check(p.getInventory().getItem(20).is(Items.IRON_PICKAXE), "pickaxe in slot 20");
			GravesMod.STORE.get().graves.remove(g.id);
			Vec3 v = GravesMod.safeSpot(level, new Vec3(5, level.getMinY() - 40, 5));
			SelfTests.check(v.y >= level.getMinY() + 1, "void: grave above the bottom");
			GravesMod.ENABLED.set(p, false);
			p.getInventory().clearContent();
			SelfTests.check(!GravesMod.intoGrave(p, level), "off: vanilla drops");
		});
	}
}
