package nl.libreria.testclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CI only: mock-ups of features that aren't built yet, drawn in the real game (so they look as they would), for the
 * owner to choose from before anything is built. Nothing here ships.
 */
final class Mockups {
	private static final Logger LOG = LoggerFactory.getLogger("Libreria mockups");

	private Mockups() {
	}

	static void all(ClientGameTestContext ctx, TestServerContext server, ServerPlayer player) {
		BlockPos o = server.computeOnServer(s -> player.blockPosition());
		int x = o.getX(), y = o.getY(), z = o.getZ();
		ctx.runOnClient(mc -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
		run(server, "kill @e[type=!minecraft:player]", "clear @a", "time set midnight",
				f("fill %d %d %d %d %d %d minecraft:grass_block", x - 16, y - 1, z - 6, x + 16, y - 1, z + 24),
				f("fill %d %d %d %d %d %d minecraft:air", x - 16, y, z - 6, x + 16, y + 8, z + 24));
		try {
			spawnSpots(ctx, server, player, x, y, z);
		} catch (Throwable t) {
			LOG.error("mock spawn spots failed", t);
		}
		try {
			ping(ctx, server, player, x, y, z);
		} catch (Throwable t) {
			LOG.error("mock ping failed", t);
		}
		try {
			stands(ctx, server, player, x, y, z);
		} catch (Throwable t) {
			LOG.error("mock stands failed", t);
		}
		LOG.info("LIBRERIA-MOCKUPS DONE");
	}

	/**
	 * Spawn spots: a stone room with one torch, open towards you, grass around it at night. A red × where a monster
	 * can spawn at any time (no light at all), a yellow × where it can at night (under the sky).
	 */
	private static void spawnSpots(ClientGameTestContext ctx, TestServerContext server, ServerPlayer player, int x, int y, int z) {
		run(server, f("fill %d %d %d %d %d %d minecraft:stone_bricks hollow", x - 5, y - 1, z + 4, x + 5, y + 3, z + 12),
				f("fill %d %d %d %d %d %d minecraft:air", x - 4, y, z + 4, x + 4, y + 2, z + 4),
				f("setblock %d %d %d minecraft:torch", x + 3, y, z + 10),
				f("tp @a %d %d %d 0 22", x, y, z - 1),
				"item replace entity @a weapon.mainhand with minecraft:torch 16");
		ctx.waitTicks(40);
		List<String> marks = server.computeOnServer(s -> {
			ServerLevel level = (ServerLevel) player.level();
			List<String> out = new ArrayList<>();
			for (int dx = -12; dx <= 12; dx++) {
				for (int dz = -2; dz <= 16; dz++) {
					for (int dy = -2; dy <= 2; dy++) {
						BlockPos feet = new BlockPos(x + dx, y + dy, z + dz);
						if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) continue;
						if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) continue;
						if (level.getBrightness(LightLayer.BLOCK, feet) > 0) continue;
						boolean sky = level.getBrightness(LightLayer.SKY, feet) > 0;
						out.add(String.format(Locale.ROOT, "%d %d %d %s", feet.getX(), feet.getY(), feet.getZ(), sky ? "sky" : "dark"));
					}
				}
			}
			return out;
		});
		for (String m : marks) {
			String[] p = m.split(" ");
			int color = p[3].equals("sky") ? 0xFFE066 : 0xFF4D4D;
			run(server, String.format(Locale.ROOT,
					"summon minecraft:text_display %.2f %.2f %.2f {text:{text:\"×\",color:\"#%06X\"},background:0,brightness:{sky:15,block:15},"
							+ "transformation:{left_rotation:[0.7071f,0f,0f,0.7071f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,-0.32f],scale:[2.6f,2.6f,2.6f]},Tags:[\"mock\"]}",
					Integer.parseInt(p[0]) + 0.5, Integer.parseInt(p[1]) + 0.02, Integer.parseInt(p[2]) + 0.5, color));
		}
		LOG.info("LIBRERIA-MOCKUPS spawn spots: {}", marks.size());
		ctx.waitTicks(30);
		Screens.shot(ctx, "mock_spawn_spots");
		run(server, "kill @e[tag=mock]");
		run(server, "item replace entity @a weapon.mainhand with minecraft:air");
		// how the switch says it (a card at the top)
	}

	/** A ping: someone looked at a spot and pinged it; their friends are shown the way, with who and how far. */
	private static void ping(ClientGameTestContext ctx, TestServerContext server, ServerPlayer player, int x, int y, int z) {
		run(server, f("fill %d %d %d %d %d %d minecraft:air", x - 5, y - 1, z + 4, x + 5, y + 3, z + 12),
				f("fill %d %d %d %d %d %d minecraft:grass_block", x - 5, y - 1, z + 4, x + 5, y - 1, z + 12),
				f("fill %d %d %d %d %d %d minecraft:oak_log", x + 5, y, z + 18, x + 5, y + 3, z + 18),
				f("fill %d %d %d %d %d %d minecraft:oak_leaves", x + 3, y + 3, z + 16, x + 7, y + 5, z + 20),
				f("setblock %d %d %d minecraft:chest", x + 3, y, z + 18),
				f("tp @a %d %d %d 0 8", x, y, z - 1), "time set 1000");
		server.runOnServer(s -> {
			ServerLevel level = (ServerLevel) player.level();
			nl.libreria.core.Guide.toPoint(player, level, new Vec3(x + 3.5, y, z + 18.5), new ItemStack(Items.PLAYER_HEAD),
					Ui.text("Sam's ping", ChatFormatting.YELLOW), 30);
			nl.libreria.core.Notify.info(player, Items.SPYGLASS, "Sam pinged a spot", "20 m ahead · by the oak");
		});
		ctx.waitTicks(40);
		Screens.shot(ctx, "mock_ping");
		server.runOnServer(s -> nl.libreria.core.Guide.clear(player));
	}

	/** Armour stands: posed ones, and the window that poses them (sneak + right-click a stand). Item frames: invisible. */
	private static void stands(ClientGameTestContext ctx, TestServerContext server, ServerPlayer player, int x, int y, int z) {
		run(server, "kill @e[type=!minecraft:player]", f("fill %d %d %d %d %d %d minecraft:air", x - 8, y, z + 14, x + 8, y + 6, z + 22),
				f("tp @a %d %d %d 0 10", x, y, z - 1), "time set 1000");
		String[][] stands = {
				{"Waving", "Head:[0f,0f,0f],RightArm:[-160f,0f,-10f],LeftArm:[0f,0f,-8f]"},
				{"Pointing", "Head:[0f,-20f,0f],RightArm:[-90f,-20f,0f],LeftArm:[10f,0f,-10f]"},
				{"Thinking", "Head:[15f,0f,0f],RightArm:[-120f,40f,0f],LeftArm:[-60f,40f,0f]"},
				{"Cheering", "Head:[-15f,0f,0f],RightArm:[-170f,0f,15f],LeftArm:[-170f,0f,-15f]"}};
		for (int i = 0; i < stands.length; i++) {
			run(server, f("summon minecraft:armor_stand %d %d %d ", x - 3 + i * 2, y, z + 4)
					+ "{Rotation:[180f,0f],ShowArms:1b,NoBasePlate:1b,Pose:{" + stands[i][1] + "},equipment:{head:{id:\"minecraft:iron_helmet\"},"
					+ "chest:{id:\"minecraft:diamond_chestplate\"},mainhand:{id:\"minecraft:iron_sword\"}},CustomName:\"" + stands[i][0] + "\",CustomNameVisible:1b}");
		}
		ctx.waitTicks(30);
		Screens.shot(ctx, "mock_stands");
		// item frames on a wall: left as they are now, right invisible (only the item shows)
		run(server, "kill @e[type=minecraft:armor_stand]", f("fill %d %d %d %d %d %d minecraft:spruce_planks", x - 5, y, z + 6, x + 5, y + 3, z + 6),
				f("tp @a %d %d %d 0 5", x, y, z + 1));
		String[] items = {"diamond_sword", "golden_apple", "clock", "diamond_sword", "golden_apple", "clock"};
		for (int i = 0; i < items.length; i++) {
			int fx = i < 3 ? x + 4 - i : x - 2 - (i - 3);
			run(server, f("summon minecraft:item_frame %d %d %d ", fx, y + 1, z + 5)
					+ "{Facing:2b,Fixed:1b,Invisible:" + (i >= 3 ? "1b" : "0b") + ",Item:{id:\"minecraft:" + items[i] + "\",count:1}}");
		}
		ctx.waitTicks(30);
		Screens.shot(ctx, "mock_frames");
		run(server, "kill @e[type=minecraft:item_frame]", f("fill %d %d %d %d %d %d minecraft:air", x - 5, y, z + 6, x + 5, y + 3, z + 6),
				f("tp @a %d %d %d 0 10", x, y, z - 1));
		for (int i = 0; i < stands.length; i++) {
			run(server, f("summon minecraft:armor_stand %d %d %d ", x - 3 + i * 2, y, z + 4)
					+ "{Rotation:[180f,0f],ShowArms:1b,NoBasePlate:1b,Pose:{" + stands[i][1] + "},equipment:{head:{id:\"minecraft:iron_helmet\"},"
					+ "chest:{id:\"minecraft:diamond_chestplate\"},mainhand:{id:\"minecraft:iron_sword\"}}}");
		}
		ctx.waitTicks(10);

		// The window: presets (they apply at once, the stand behind it changes), the switches, and fine-tuning
		server.runOnServer(s -> {
			Window w = Window.of(nl.libreria.core.Domain.ITEMS, "Armour stand");
			w.item(new ItemStack(Items.ARMOR_STAND), Ui.text("Every click shows at once on the stand. Fine-tune each part, "
					+ "or copy this pose and give it to another stand.", ChatFormatting.GRAY));
			w.columns(2);
			for (String pose : new String[] {"Standing", "Waving", "Pointing", "Saluting", "Thinking", "Cheering", "Walking", "Sitting"})
				w.button(Ui.text(pose, ChatFormatting.WHITE), null, (p, v) -> {
				});
			String[][] sw = {{"Arms", "on"}, {"Base plate", "off"}, {"Small", "off"}, {"Invisible", "off"}};
			for (String[] t : sw)
				w.button(Ui.text(t[0] + ": ", ChatFormatting.WHITE).append(Ui.text(t[1], t[1].equals("on") ? ChatFormatting.GREEN : ChatFormatting.GRAY)), null, (p, v) -> {
				});
			w.button(Ui.text("Fine-tune ▸", ChatFormatting.AQUA), Ui.text("Head, arms, legs and body, one by one", ChatFormatting.GRAY), (p, v) -> {
			});
			w.button(Ui.text("Copy pose", ChatFormatting.AQUA), Ui.text("Then sneak + right-click another stand", ChatFormatting.GRAY), (p, v) -> {
			});
			w.bottom(Ui.text("✔ Done", ChatFormatting.GREEN), (p, v) -> {
			}).show(player);
		});
		ctx.waitTicks(15);
		ctx.getInput().setCursorPos(2, 2);
		Screens.shot(ctx, "mock_stand_window");
		Screens.closeAll(ctx, server, player);
		server.runOnServer(s -> {
			Window w = Window.of(nl.libreria.core.Domain.ITEMS, "Armour stand", "Fine-tune");
			w.choice("part", Ui.text("Part", ChatFormatting.WHITE), List.of("head", "body", "rarm", "larm", "rleg", "lleg"),
					List.of(Ui.text("Head", ChatFormatting.AQUA), Ui.text("Body", ChatFormatting.AQUA), Ui.text("Right arm", ChatFormatting.AQUA),
							Ui.text("Left arm", ChatFormatting.AQUA), Ui.text("Right leg", ChatFormatting.AQUA), Ui.text("Left leg", ChatFormatting.AQUA)), "rarm");
			w.slider("x", Ui.text("Forward / back", ChatFormatting.WHITE), -180, 180, 5, -160);
			w.slider("y", Ui.text("Turn", ChatFormatting.WHITE), -180, 180, 5, 0);
			w.slider("z", Ui.text("Out to the side", ChatFormatting.WHITE), -180, 180, 5, -10);
			w.columns(2);
			w.button(Ui.text("Apply", ChatFormatting.GREEN), null, (p, v) -> {
			});
			w.button(Ui.text("Mirror left ↔ right", ChatFormatting.AQUA), null, (p, v) -> {
			});
			w.bottom(Ui.text("◀ Back", ChatFormatting.WHITE), (p, v) -> {
			}).show(player);
		});
		ctx.waitTicks(15);
		ctx.getInput().setCursorPos(2, 2);
		Screens.shot(ctx, "mock_stand_finetune");
		Screens.closeAll(ctx, server, player);
	}

	private static String f(String fmt, Object... args) {
		return String.format(Locale.ROOT, fmt, args);
	}

	private static void run(TestServerContext server, String... commands) {
		for (String c : commands) {
			try {
				server.runCommand(c);
			} catch (Throwable t) {
				LOG.warn("Command failed: {}", c, t);
			}
		}
	}
}
