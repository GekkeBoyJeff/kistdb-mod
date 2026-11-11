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
		// (the mock-ups of before were chosen and built: spawnSpots, ping, stands)
		try {
			grandWaystones(ctx, server, player, x, y, z);
		} catch (Throwable t) {
			LOG.error("mock grand waystones failed", t);
		}
		LOG.info("LIBRERIA-MOCKUPS DONE");
	}

	// ---------------------------------------------------------------- grand waystones (Nov 2026)

	/**
	 * A grand waystone, three ways, 5 × 5 and tall, built from real blocks (displays for what floats): the Spire (light
	 * stone, purple runes, a floating gem), the Gate (blackstone, cyan runes, a rune tablet floating in an arch) and the
	 * Lighthouse (a hollow tower with a beam of light). And how one is made: the foundation you build round a waystone.
	 */
	private static void grandWaystones(ClientGameTestContext ctx, TestServerContext server, ServerPlayer player, int x, int y, int z) {
		int gz = z + 16;
		run(server, "kill @e[type=!minecraft:player]", "clear @a", "time set 1000", "weather clear",
				f("fill %d %d %d %d %d %d minecraft:grass_block", x - 24, y - 1, z - 6, x + 24, y - 1, z + 30),
				f("fill %d %d %d %d %d %d minecraft:air", x - 24, y, z - 6, x + 24, y + 12, z + 30),
				f("fill %d %d %d %d %d %d minecraft:air", x - 24, y + 13, z + 6, x + 24, y + 24, z + 30));
		spire(server, x - 12, y, gz);
		gate(server, x, y, gz);
		lighthouse(server, x + 12, y, gz);
		// All three, by day and at night
		view(server, x + 0.5, y + 6, z - 8.5, x + 0.5, y + 6, gz + 0.5);
		ctx.waitTicks(60);
		Screens.shot(ctx, "mock_grand_all_day");
		run(server, "time set 18000");
		ctx.waitTicks(40);
		Screens.shot(ctx, "mock_grand_all_night");
		// Each up close, at dusk
		run(server, "time set 12800");
		int[] cx = {x - 12, x, x + 12};
		String[] names = {"spire", "gate", "lighthouse"};
		for (int i = 0; i < 3; i++) {
			view(server, cx[i] + 7.5, y + 2.5, gz - 8.5, cx[i] + 0.5, y + 6.5, gz + 0.5);
			ctx.waitTicks(40);
			Screens.shot(ctx, "mock_grand_" + names[i]);
		}
		// How it's made: the foundation round a waystone (and the heart in your hand), by day
		int fx = x, fz = z + 4;
		run(server, "time set 1000", f("fill %d %d %d %d %d %d minecraft:air", x - 24, y, gz - 4, x + 24, y + 24, gz + 4),
				"kill @e[type=minecraft:block_display]");
		foundation(server, fx, y, fz);
		server.runOnServer(sv -> nl.libreria.waystones.WaystonesDebug.scene(player, new BlockPos(fx, y + 1, fz), net.minecraft.core.Direction.NORTH));
		run(server, "item replace entity @a weapon.mainhand with minecraft:nether_star");
		view(server, fx + 5.5, y + 3.5, fz - 6.5, fx + 0.5, y + 1.5, fz + 0.5);
		ctx.waitTicks(40);
		Screens.shot(ctx, "mock_grand_foundation");
		run(server, "item replace entity @a weapon.mainhand with minecraft:air");
	}

	/** The player stands at (ax, ay, az) and looks at (tx, ty, tz). */
	private static void view(TestServerContext server, double ax, double ay, double az, double tx, double ty, double tz) {
		double dx = tx - ax, dy = ty - (ay + 1.62), dz = tz - az;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		run(server, f("tp @a %.2f %.2f %.2f %.1f %.1f", ax, ay, az, yaw, pitch));
	}

	/**
	 * Builds layers of a 5 × 5 plan (centred on cx, cz; each layer five rows of five letters, north first), bottom up
	 * from y, the letters looked up in {@code key} (a space: nothing).
	 */
	private static void layers(TestServerContext server, int cx, int y, int cz, java.util.Map<Character, String> key, String[][] plan) {
		for (int l = 0; l < plan.length; l++) {
			for (int r = 0; r < 5; r++) {
				String row = plan[l][r];
				for (int c = 0; c < 5; c++) {
					char ch = row.charAt(c);
					if (ch == ' ') continue;
					String block = key.get(ch);
					if (block == null) continue;
					run(server, f("setblock %d %d %d %s", cx - 2 + c, y + l, cz - 2 + r, block));
				}
			}
		}
	}

	/** A floating block (a display): its middle at (x, y, z), scaled, turned about an axis by an angle (degrees). */
	private static void floating(TestServerContext server, double x, double y, double z, String block, float sx, float sy, float sz,
			float ax, float ay, float az, float degrees) {
		org.joml.Quaternionf q = new org.joml.Quaternionf().rotateAxis((float) Math.toRadians(degrees), new org.joml.Vector3f(ax, ay, az).normalize());
		org.joml.Vector3f centre = new org.joml.Vector3f(sx / 2, sy / 2, sz / 2).rotate(q).negate();
		run(server, f("summon minecraft:block_display %.3f %.3f %.3f {block_state:{Name:\"%s\"},brightness:{sky:15,block:15},view_range:4f,"
				+ "transformation:{left_rotation:[%ff,%ff,%ff,%ff],right_rotation:[0f,0f,0f,1f],translation:[%ff,%ff,%ff],scale:[%ff,%ff,%ff]}}",
				x, y, z, block, q.x, q.y, q.z, q.w, centre.x, centre.y, centre.z, sx, sy, sz));
	}

	/** The Spire: light stone on a stepped base, purple runes on four sides, a tall needle, a gem floating on top. */
	private static void spire(TestServerContext server, int cx, int y, int cz) {
		java.util.Map<Character, String> k = java.util.Map.ofEntries(java.util.Map.entry('S', "minecraft:stone_bricks"),
				java.util.Map.entry('C', "minecraft:chiseled_stone_bricks"), java.util.Map.entry('M', "minecraft:smooth_stone"),
				java.util.Map.entry('d', "minecraft:smooth_stone_slab"), java.util.Map.entry('u', "minecraft:stone_brick_slab[type=top]"),
				java.util.Map.entry('W', "minecraft:stone_brick_wall"), java.util.Map.entry('E', "minecraft:end_rod"),
				java.util.Map.entry('F', "minecraft:pearlescent_froglight"), java.util.Map.entry('A', "minecraft:amethyst_block"),
				java.util.Map.entry('X', "minecraft:amethyst_cluster[facing=up]"), java.util.Map.entry('Q', "minecraft:quartz_pillar"));
		layers(server, cx, y, cz, k, new String[][] {
				{"CSSSC", "SMMMS", "SMMMS", "SMMMS", "CSSSC"},
				{"WdddW", "dSCSd", "dCSCd", "dSCSd", "WdddW"},
				{"W   W", " CFC ", " FSF ", " CFC ", "W   W"},
				{"E   E", " SAS ", " ASA ", " SAS ", "E   E"},
				{"     ", " CuC ", " uSu ", " CuC ", "     "},
				{"     ", "     ", "  Q  ", "     ", "     "},
				{"     ", "     ", "  Q  ", "     ", "     "},
				{"     ", "     ", "  F  ", "     ", "     "},
				{"     ", "     ", "  C  ", "     ", "     "},
				{"     ", "     ", "  A  ", "     ", "     "},
				{"     ", "     ", "  X  ", "     ", "     "}});
		floating(server, cx + 0.5, y + 12.4, cz + 0.5, "minecraft:amethyst_block", 0.75f, 0.75f, 0.75f, 1, 0, 1, 45);
		floating(server, cx + 0.5, y + 12.4, cz + 0.5, "minecraft:pearlescent_froglight", 0.35f, 0.35f, 0.35f, 1, 0, 1, 45);
	}

	/** The Gate: blackstone pillars and an arch, cyan lanterns, a rune tablet floating over a lodestone in the middle. */
	private static void gate(TestServerContext server, int cx, int y, int cz) {
		java.util.Map<Character, String> k = java.util.Map.ofEntries(java.util.Map.entry('B', "minecraft:polished_blackstone_bricks"),
				java.util.Map.entry('K', "minecraft:chiseled_polished_blackstone"), java.util.Map.entry('P', "minecraft:polished_blackstone"),
				java.util.Map.entry('G', "minecraft:gilded_blackstone"), java.util.Map.entry('L', "minecraft:lodestone"),
				java.util.Map.entry('w', "minecraft:polished_blackstone_brick_wall"), java.util.Map.entry('s', "minecraft:soul_lantern"),
				java.util.Map.entry('h', "minecraft:soul_lantern[hanging=true]"), java.util.Map.entry('O', "minecraft:crying_obsidian"),
				java.util.Map.entry('e', "minecraft:polished_blackstone_brick_stairs[facing=west,half=top]"),
				java.util.Map.entry('f', "minecraft:polished_blackstone_brick_stairs[facing=east,half=top]"),
				java.util.Map.entry('b', "minecraft:polished_blackstone_slab[type=bottom]"), java.util.Map.entry('R', "minecraft:sea_lantern"));
		layers(server, cx, y, cz, k, new String[][] {
				{"KBBBK", "BPPPB", "BPPPB", "BPPPB", "KBBBK"},
				{"w   w", "B   B", "K L K", "B   B", "w   w"},
				{"s   s", "B   B", "R   R", "B   B", "s   s"},
				{"     ", "B   B", "K   K", "B   B", "     "},
				{"     ", "b   b", "B   B", "b   b", "     "},
				{"     ", "     ", "R   R", "     ", "     "},
				{"     ", "     ", "B   B", "     ", "     "},
				{"     ", "     ", "Ke fK", "     ", "     "},
				{"     ", "     ", "BGOGB", "     ", "     "},
				{"     ", "     ", "bbKbb", "     ", "     "},
				{"     ", "     ", "  R  ", "     ", "     "}});
		run(server, f("setblock %d %d %d minecraft:soul_lantern[hanging=true]", cx, y + 7, cz));
		// The tablet: dark stone with a line of cyan light, floating over the lodestone
		floating(server, cx + 0.5, y + 3.6, cz + 0.5, "minecraft:polished_deepslate", 1.1f, 1.8f, 0.22f, 0, 1, 0, 0);
		floating(server, cx + 0.5, y + 3.6, cz + 0.5, "minecraft:sea_lantern", 0.14f, 1.3f, 0.26f, 0, 1, 0, 0);
		floating(server, cx + 0.5, y + 3.9, cz + 0.5, "minecraft:sea_lantern", 0.7f, 0.12f, 0.26f, 0, 1, 0, 0);
	}

	/** The Lighthouse: a hollow tower with windows and rune bands, a crown, and a beam of light in its colour. */
	private static void lighthouse(TestServerContext server, int cx, int y, int cz) {
		java.util.Map<Character, String> k = java.util.Map.ofEntries(java.util.Map.entry('S', "minecraft:stone_bricks"),
				java.util.Map.entry('C', "minecraft:chiseled_stone_bricks"), java.util.Map.entry('M', "minecraft:smooth_stone"),
				java.util.Map.entry('g', "minecraft:purple_stained_glass"), java.util.Map.entry('F', "minecraft:pearlescent_froglight"),
				java.util.Map.entry('A', "minecraft:amethyst_block"), java.util.Map.entry('W', "minecraft:stone_brick_wall"),
				java.util.Map.entry('E', "minecraft:end_rod"), java.util.Map.entry('d', "minecraft:smooth_stone_slab"),
				java.util.Map.entry('D', "minecraft:oak_door[half=lower,facing=north]"), java.util.Map.entry('U', "minecraft:oak_door[half=upper,facing=north]"));
		String ring = "SSSSS|S   S|S   S|S   S|SSSSS";
		String rune = "CSFSC|S   S|F   F|S   S|CSFSC";
		String win = "SSgSS|S   S|g   g|S   S|SSgSS";
		String band = "CCCCC|C   C|C   C|C   C|CCCCC";
		layers(server, cx, y, cz, k, new String[][] {
				{"CMMMC", "MMMMM", "MMAMM", "MMMMM", "CMMMC"},
				"SSDSS|S   S|S   S|S   S|SSSSS".split("\\|"), "SSUSS|S   S|S   S|S   S|SSSSS".split("\\|"),
				band.split("\\|"), win.split("\\|"), ring.split("\\|"), rune.split("\\|"), win.split("\\|"), band.split("\\|"),
				{"SSSSS", "SMMMS", "SMAMS", "SMMMS", "SSSSS"},
				{"W   W", "     ", "  A  ", "     ", "W   W"},
				{"E   E", "     ", "  F  ", "     ", "E   E"}});
		// The beam, in the runes' colour, from the crown up
		floating(server, cx + 0.5, y + 12 + 24, cz + 0.5, "minecraft:purple_stained_glass", 0.45f, 48f, 0.45f, 0, 1, 0, 0);
	}

	/** The foundation you build round a waystone before it becomes grand: a 5 × 5 floor, four corner posts with lights. */
	private static void foundation(TestServerContext server, int cx, int y, int cz) {
		java.util.Map<Character, String> k = java.util.Map.ofEntries(java.util.Map.entry('S', "minecraft:stone_bricks"),
				java.util.Map.entry('C', "minecraft:chiseled_stone_bricks"), java.util.Map.entry('M', "minecraft:smooth_stone"),
				java.util.Map.entry('A', "minecraft:amethyst_block"), java.util.Map.entry('W', "minecraft:stone_brick_wall"),
				java.util.Map.entry('E', "minecraft:end_rod"));
		layers(server, cx, y, cz, k, new String[][] {
				{"CSSSC", "SMMMS", "SMAMS", "SMMMS", "CSSSC"},
				{"W   W", "     ", "     ", "     ", "W   W"},
				{"E   E", "     ", "     ", "     ", "E   E"}});
	}

	/**
	 * Spawn spots: a stone room with one torch, open towards you, grass around it at night. A red × where a monster
	 * can spawn at any time (no light at all), a yellow × where it can at night (under the sky).
	 */
	private static void spawnSpots(ClientGameTestContext ctx, TestServerContext server, ServerPlayer player, int x, int y, int z) {
		// Inside: a closed stone room with one torch in a corner (no sky: red where it's dark enough)
		run(server, f("fill %d %d %d %d %d %d minecraft:stone_bricks hollow", x - 2, y - 1, z - 2, x + 17, y + 4, z + 17),
				f("setblock %d %d %d minecraft:torch", x - 1, y, z - 1),
				f("tp @a %.1f %d %.1f -45 28", x + 0.5, y, z + 0.5),
				"item replace entity @a weapon.mainhand with minecraft:torch 16");
		marks(ctx, server, player, x, y, z, -1, 17, -1, 17);
		Screens.shot(ctx, "mock_spawn_spots_inside");
		clearTiles(server);
		run(server, f("fill %d %d %d %d %d %d minecraft:air", x - 2, y, z - 2, x + 17, y + 4, z + 17),
				f("fill %d %d %d %d %d %d minecraft:grass_block", x - 2, y - 1, z - 2, x + 17, y - 1, z + 17));
		// Outside at night: yellow where monsters can spawn once it's dark; a redstone torch (weaker light) keeps a
		// patch round it free
		run(server, "time set midnight", f("setblock %d %d %d minecraft:redstone_torch", x + 1, y, z + 7),
				f("tp @a %.1f %d %.1f 0 38", x + 0.5, y + 4, z - 2.5));
		marks(ctx, server, player, x, y, z, -12, 12, -4, 22);
		Screens.shot(ctx, "mock_spawn_spots_outside");
		clearTiles(server);
		run(server, f("setblock %d %d %d minecraft:air", x + 1, y, z + 7),
				"item replace entity @a weapon.mainhand with minecraft:air");
	}

	/** Small glowing tiles where a monster could spawn: red always (no sky), yellow at night (under the sky). */
	private static void marks(ClientGameTestContext ctx, TestServerContext server, ServerPlayer player, int x, int y, int z, int x0, int x1, int z0, int z1) {
		ctx.waitTicks(30);
		List<String> marks = server.computeOnServer(s -> {
			ServerLevel level = (ServerLevel) player.level();
			List<String> out = new ArrayList<>();
			for (int dx = x0; dx <= x1; dx++) {
				for (int dz = z0; dz <= z1; dz++) {
					for (int dy = -2; dy <= 2; dy++) {
						BlockPos feet = new BlockPos(x + dx, y + dy, z + dz);
						if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) continue;
						if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) continue;
						int light = level.getBrightness(LightLayer.BLOCK, feet);
						boolean sky = level.getBrightness(LightLayer.SKY, feet) > 0;
						out.add(String.format(Locale.ROOT, "%d %d %d %s %d", feet.getX(), feet.getY(), feet.getZ(), light > 0 ? "lit" : sky ? "sky" : "dark", light));
					}
				}
			}
			return out;
		});
		// per viewer, as the real thing would be: virtual tiles (Polymer elements) only this player sees
		server.runOnServer(sv -> {
			ServerLevel level = (ServerLevel) player.level();
			for (String m : marks) {
				String[] p = m.split(" ");
				Vec3 at = new Vec3(Integer.parseInt(p[0]) + 0.5, Integer.parseInt(p[1]) + 0.005, Integer.parseInt(p[2]) + 0.5);
				var holder = new eu.pb4.polymer.virtualentity.api.ElementHolder();
				boolean dark = !p[3].equals("lit");
				if (dark) {
					var e = new eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement(
							Ui.block(p[3].equals("sky") ? "yellow_stained_glass" : "red_stained_glass"));
					e.setTranslation(new org.joml.Vector3f(-0.3f, 0f, -0.3f));
					e.setScale(new org.joml.Vector3f(0.6f, 0.02f, 0.6f));
					e.setBrightness(new net.minecraft.util.Brightness(15, 15));
					e.setDisplaySize(1.5f, 1f);
					holder.addElement(e);
				}
				// the block light level, lying on the block: white on a tile, pale green where it's lit
				for (float sign : new float[] {1f, -1f}) {
					var t = new eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement(
							Ui.text(p[4], dark ? 0xFFFFFF : 0x9FE29F));
					t.setBackground(0);
					t.setLeftRotation(new org.joml.Quaternionf().rotateX(sign * (float) Math.PI / 2));
					t.setTranslation(new org.joml.Vector3f(0f, 0.03f, sign * -0.12f));
					t.setScale(new org.joml.Vector3f(1.1f, 1.1f, 1.1f));
					t.setBrightness(new net.minecraft.util.Brightness(15, 15));
					t.setDisplaySize(1.5f, 1f);
					holder.addElement(t);
				}
				new eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment(holder, level, () -> at);
				holder.startWatching(player);
				TILES.add(holder);
			}
		});
		LOG.info("LIBRERIA-MOCKUPS spawn spots: {}", marks.size());
		ctx.waitTicks(40);
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
			Window w = Window.of(nl.libreria.core.Domain.YOU, "Armour stand");
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
			Window w = Window.of(nl.libreria.core.Domain.YOU, "Armour stand", "Fine-tune");
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

	private static final List<eu.pb4.polymer.virtualentity.api.ElementHolder> TILES = new ArrayList<>();

	private static void clearTiles(TestServerContext server) {
		server.runOnServer(sv -> {
			for (var h : TILES) h.destroy();
			TILES.clear();
		});
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
