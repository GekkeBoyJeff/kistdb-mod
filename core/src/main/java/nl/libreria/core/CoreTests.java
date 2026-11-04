package nl.libreria.core;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Zelftests van de kern (alleen in de CI). */
final class CoreTests {
	private CoreTests() {
	}

	static void register() {
		SelfTests.register("core.settings", level -> {
			FakePlayer p = FakePlayer.get(level);
			boolean before = Libreria.HUB_BUTTON.get(p);
			Libreria.HUB_BUTTON.set(p, !before);
			SelfTests.check(Libreria.HUB_BUTTON.get(p) == !before, "toggle flips");
			Libreria.HUB_BUTTON.set(p, before);
			int idx = Libreria.GUIDE_SECONDS.index(p);
			Libreria.GUIDE_SECONDS.cycle(p, 1);
			SelfTests.check(Libreria.GUIDE_SECONDS.index(p) == (idx + 1) % Libreria.GUIDE_SECONDS.size(), "choice cycles");
			Libreria.GUIDE_SECONDS.cycle(p, -1);
			SelfTests.check(Libreria.GUIDE_SECONDS.get(p) == 90, "default is 90 s");
		});
		SelfTests.register("core.beam", level -> {
			FakePlayer p = FakePlayer.get(level);
			Guide.toPoint(p, level, new Vec3(10, 100, 10), new ItemStack(Items.COMPASS), Component.literal("Test"), 5);
			SelfTests.check(Guide.has(p), "beam active");
			Guide.clear(p);
			SelfTests.check(!Guide.has(p), "beam cleared");
			Guide.toBox(p, level, new AABB(0, 100, 0, 2, 101, 1), new ItemStack(Items.DIAMOND), Component.literal("Chest"), 3, 5);
			Guide.flash(p, level, new AABB(0, 100, 0, 1, 101, 1), 0xFF0000, 10);
			Guide.clear(p);
			Hud.show(p, "test", Component.literal("hud"), Hud.PRIO_INFO, 20);
			Hud.flash(p, Component.literal("flash"));
		});
	}
}
