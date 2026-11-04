package nl.libreria.core;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.gui.Compass;

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
		nl.libreria.core.dialog.DialogTests.register();
		SelfTests.register("core.server-settings", level -> {
			ServerSetting.Text discord = Links.KINDS.getFirst().setting();
			discord.set("https://discord.gg/abc");
			SelfTests.check(discord.get().equals("https://discord.gg/abc") && Links.present().size() >= 1, "link stored");
			SelfTests.check(Links.uri("javascript:alert(1)") == null && Links.uri("discord.gg/x") == null
					&& Links.uri("https://example.org") != null, "only web addresses");
			discord.reset();
			SelfTests.check(!discord.isSet(), "reset clears it");
			SelfTests.check(ServerSetting.all().size() >= 6, "settings registered: " + ServerSetting.all().size());
		});
		SelfTests.register("core.compass", level -> {
			// Minecraft yaw: 0 = south, 90 = west, -90 = east, 180 = north
			SelfTests.check(Compass.heading(0f) == 180 && Compass.heading(90f) == 270 && Compass.heading(-90f) == 90
					&& Compass.heading(180f) == 0, "headings");
			Vec3 o = Vec3.ZERO;
			SelfTests.check(Math.round(Compass.bearing(o, new Vec3(0, 0, -10))) == 0 && Math.round(Compass.bearing(o, new Vec3(10, 0, 0))) == 90,
					"bearings");
			SelfTests.check(Compass.delta(10, 350) == 20 && Compass.delta(350, 10) == -20, "turn the short way");
			FakePlayer p = FakePlayer.get(level);
			p.setYRot(37f);
			int empty = nl.libreria.core.gui.Glyphs.width(Compass.compose(p, java.util.List.of()));
			int full = nl.libreria.core.gui.Glyphs.width(Compass.compose(p, java.util.List.of(
					new Compass.Marker(p.position().add(5, 0, 5), "diamond", 0x55FFFF, "Home"),
					new Compass.Marker(p.position().add(-40, 0, 3), "skull", 0xFF5555, "Death point"))));
			SelfTests.check(empty == full && empty == nl.libreria.core.gui.Glyphs.advance("compass.bar"),
					"the strip always has the same width: " + empty + " / " + full);
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
