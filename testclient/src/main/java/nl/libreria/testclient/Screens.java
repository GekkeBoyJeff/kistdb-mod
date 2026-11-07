package nl.libreria.testclient;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import nl.libreria.claims.ClaimsMod;
import nl.libreria.collector.CollectorMod;
import nl.libreria.core.Module;
import nl.libreria.core.gui.SettingsWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CI only: starts a real client, builds a small scene and takes screenshots of everything Libreria draws (tooltips,
 * name tag, inventory frames, menus), so the look can be checked without a person playing. Every part is wrapped so one
 * failure still gives the other screenshots.
 */
public final class Screens implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("Libreria screenshots");

	@Override
	public void runTest(ClientGameTestContext ctx) {
		// (the shader run walks on a real server, in NetworkTest: in singleplayer there is no pack host to download from)
		if (SHADERS) return;
		ctx.getInput().resizeWindow(1280, 720);
		ctx.waitTicks(20);
		try (TestSingleplayerContext sp = ctx.worldBuilder()
				.adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			TestServerContext server = sp.getServer();
			sp.getConnection().waitForChunksRender();
			ServerPlayer player = server.computeOnServer(s -> sp.getConnection().getServerPlayer());
			scene(ctx, server, player);
		}
		LOG.info("LIBRERIA-SCREENSHOTS DONE");
	}

	/** The shader run (the "shaders" workflow puts Iris, Sodium and a shader pack in): only the shader walk. */
	static final boolean SHADERS = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris");

	/**
	 * With a shader pack (Iris): the effects on a sword in your hand and on armour, by day and at night (the glow maps
	 * are read as LabPBR emission when the shader pack is set to).
	 */
	static void shaders(ClientGameTestContext ctx, TestServerContext server, ServerPlayer player) {
		BlockPos o = server.computeOnServer(s -> player.blockPosition());
		int x = o.getX(), y = o.getY(), z = o.getZ();
		if ("1".equals(System.getenv("EUPHORIA"))) ctx.runOnClient(mc -> useEuphoria());
		ctx.waitTicks(40);
		LOG.info("LIBRERIA-SHADERS shader pack in use: {} ({})", irisInUse(), irisPack());
		run(server, "time set noon", "weather clear",
				f("fill %d %d %d %d %d %d minecraft:grass_block", x - 8, y - 1, z - 8, x + 8, y - 1, z + 8),
				f("fill %d %d %d %d %d %d minecraft:air", x - 8, y, z - 8, x + 8, y + 6, z + 8),
				f("tp @a %d %d %d 0 0", x, y, z), "clear @a",
				"item replace entity @a weapon.mainhand with minecraft:diamond_sword[enchantments={sharpness:5,fire_aspect:2,looting:3}]");
		ctx.runOnClient(mc -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
		// no armour, plain diamond (the game's own look), then armour with effects
		String[][] row = {{"", "", ""}, {"diamond", "", ""}, {"diamond", "fire_protection:4", ""}, {"golden", "blast_protection:4", ""},
				{"netherite", "protection:4", "ward"}};
		// (left to right as you look: effects with a trim, gold, diamond with fire, plain diamond, no armour)
		String[] slots = {"head", "chest", "legs", "feet"};
		String[] pieces = {"helmet", "chestplate", "leggings", "boots"};
		for (int i = 0; i < row.length; i++) {
			StringBuilder eq = new StringBuilder();
			for (int k = 0; k < 4 && !row[i][0].isEmpty(); k++) {
				String trim = row[i][2].isEmpty() ? "" : ",\"minecraft:trim\":{pattern:\"minecraft:" + row[i][2] + "\",material:\"minecraft:gold\"}";
				String ench = row[i][1].isEmpty() ? "" : "\"minecraft:enchantments\":{" + row[i][1] + "}";
				eq.append(k == 0 ? "" : ",").append(slots[k]).append(":{id:\"minecraft:").append(row[i][0]).append("_").append(pieces[k])
						.append("\",components:{").append(ench).append(ench.isEmpty() && !trim.isEmpty() ? trim.substring(1) : trim).append("}}");
			}
			run(server, "summon minecraft:mannequin " + (x - 4 + i * 2) + " " + y + " " + (z + 5) + " {Rotation:[180f,0f],hide_description:1b,immovable:1b,equipment:{" + eq + "}}");
		}
		String[] probe = {"libreria:textures/entity/equipment/humanoid/fx/diamond_fireguard_1_s.png", "libreria:textures/item/fx/sword/diamond/sword/fire2_glow_s.png"};
		// The glow maps are in the main pack: what you see depends only on the shader pack's own settings (the
		// shaders workflow runs this with several)
		safe("shaders", () -> {
			camera(ctx, CameraType.FIRST_PERSON);
			ctx.getInput().lookAt(0, 10);
			ctx.waitTicks(60);
			String loaded = ctx.computeOnClient(mc -> hasAll(mc, probe));
			LOG.info("LIBRERIA-SHADERS glow maps loaded {} · shader pack in use {}", loaded, irisInUse());
			noToasts(ctx);
			ctx.takeScreenshot("shaders_day");
			run(server, "time set midnight");
			ctx.waitTicks(30);
			noToasts(ctx);
			ctx.takeScreenshot("shaders_night");
			// a few frames apart: does the glow move (pulse, flicker, run along) with shaders too?
			for (int i = 0; i < 8; i++) {
				ctx.waitTicks(3);
				ctx.takeScreenshot("shaders_anim_" + i);
			}
			// the same sword standing still before you (as in a menu: only its texture parts, no aura), frames apart
			run(server, "item replace entity @a weapon.mainhand with minecraft:air",
					f("summon minecraft:item_display %d %.1f %d {billboard:\"center\",item_display:\"gui\",brightness:{sky:0,block:0},"
							+ "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[1.6f,1.6f,1.6f]},"
							+ "item:{id:\"minecraft:diamond_sword\",components:{\"minecraft:enchantments\":{sharpness:5,fire_aspect:2,looting:3}}}}",
							x, y + 1.6, z + 2));
			ctx.getInput().lookAt(new BlockPos(x, y + 1, z + 2));
			ctx.waitTicks(20);
			for (int i = 0; i < 8; i++) {
				ctx.waitTicks(3);
				ctx.takeScreenshot("shaders_still_" + i);
			}
			run(server, "kill @e[type=minecraft:item_display]");
			camera(ctx, CameraType.THIRD_PERSON_BACK);
			ctx.waitTicks(20);
			ctx.takeScreenshot("shaders_night_third");
			camera(ctx, CameraType.FIRST_PERSON);
		});
		LOG.info("LIBRERIA-SHADERS DONE");
	}

	/** A screenshot without toasts in the way (for {@link Mockups}). */
	static void shot(ClientGameTestContext ctx, String name) {
		noToasts(ctx);
		ctx.waitTicks(2);
		ctx.takeScreenshot(name);
	}

	/** Closes whatever window is open (for {@link Mockups}). */
	static void closeAll(ClientGameTestContext ctx, TestServerContext server, ServerPlayer player) {
		close(ctx, server, player);
	}

	private static String hasAll(net.minecraft.client.Minecraft mc, String[] ids) {
		StringBuilder b = new StringBuilder();
		for (String id : ids) b.append(mc.getResourceManager().getResource(net.minecraft.resources.Identifier.parse(id)).isPresent() ? "Y" : "n");
		return b.toString();
	}

	/**
	 * The Euphoria Patcher makes "<pack> + EuphoriaPatches_<version>" in shaderpacks at launch: that pack, with the
	 * options the workflow wrote for it, through Iris' own config (looked up by name: no Iris at compile time).
	 */
	private static void useEuphoria() {
		try {
			java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
			java.nio.file.Path patched = null, opts = null;
			try (var files = java.nio.file.Files.list(dir)) {
				for (java.nio.file.Path f : files.toList()) {
					String n = f.getFileName().toString();
					if (!n.contains("EuphoriaPatches")) continue;
					if (n.endsWith(".txt")) opts = f;
					else patched = f;
				}
			}
			LOG.info("LIBRERIA-SHADERS euphoria: {} (options {})", patched, opts);
			if (patched == null) return;
			String name = patched.getFileName().toString();
			java.nio.file.Path want = dir.resolve(name + ".txt");
			if (opts != null && !opts.equals(want)) java.nio.file.Files.copy(opts, want, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
			Object cfg = iris.getMethod("getIrisConfig").invoke(null);
			cfg.getClass().getMethod("setShaderPackName", String.class).invoke(cfg, name);
			cfg.getClass().getMethod("save").invoke(cfg);
			iris.getMethod("reload").invoke(null);
		} catch (Throwable t) {
			LOG.warn("LIBRERIA-SHADERS could not switch to Euphoria Patches", t);
		}
	}

	private static String irisPack() {
		try {
			Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
			Object cfg = iris.getMethod("getIrisConfig").invoke(null);
			return String.valueOf(cfg.getClass().getMethod("getShaderPackName").invoke(cfg));
		} catch (Throwable t) {
			return "? " + t;
		}
	}

	private static String irisInUse() {
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object inst = api.getMethod("getInstance").invoke(null);
			return String.valueOf(api.getMethod("isShaderPackInUse").invoke(inst));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return "? " + e;
		}
	}

	/**
	 * The whole walk through Libreria: the scene, then every menu, card and effect. Also run against a real dedicated
	 * server by {@link NetworkTest}, where every packet really goes over the network (in singleplayer they don't).
	 */
	static void scene(ClientGameTestContext ctx, TestServerContext server, ServerPlayer player) {
		{
			BlockPos o = server.computeOnServer(s -> player.blockPosition());
			int x = o.getX(), y = o.getY(), z = o.getZ();

			// The scene: a grass platform with wheat, a chest, a cow and a creeper
			run(server, "time set noon", "weather clear",
					f("fill %d %d %d %d %d %d minecraft:grass_block", x - 8, y - 1, z - 8, x + 8, y - 1, z + 8),
					f("fill %d %d %d %d %d %d minecraft:air", x - 8, y, z - 8, x + 8, y + 6, z + 8),
					f("setblock %d %d %d minecraft:farmland", x + 2, y - 1, z + 2),
					f("setblock %d %d %d minecraft:wheat[age=4]", x + 2, y, z + 2),
					f("setblock %d %d %d minecraft:chest", x - 2, y, z + 2),
					f("item replace block %d %d %d container.0 with minecraft:diamond 20", x - 2, y, z + 2),
					f("item replace block %d %d %d container.1 with minecraft:oak_log 64", x - 2, y, z + 2),
					f("item replace block %d %d %d container.2 with minecraft:oak_log 64", x - 2, y, z + 2),
					f("item replace block %d %d %d container.3 with minecraft:oak_log 64", x - 2, y, z + 2),
					f("item replace block %d %d %d container.5 with minecraft:cobblestone 40", x - 2, y, z + 2),
					f("item replace block %d %d %d container.9 with minecraft:diamond 7", x - 2, y, z + 2),
					f("summon minecraft:cow %d %d %d {NoAI:1b,Rotation:[180f,0f]}", x + 3, y, z - 2),
					f("summon minecraft:creeper %d %d %d {NoAI:1b,Rotation:[180f,0f]}", x - 3, y, z - 2),
					f("tp @a %d %d %d 0 0", x, y, z));
			// A title for the name tag (an epic one)
			server.runOnServer(s -> {
				CollectorMod.STORE.get(player).earned.put("fok.500", System.currentTimeMillis());
				CollectorMod.setTitle(player, "fok.500");
			});
			ctx.waitTicks(40);

			// Tooltips
			look(ctx, "tooltip_wheat", new BlockPos(x + 2, y, z + 2));
			look(ctx, "tooltip_chest", new BlockPos(x - 2, y, z + 2));
			look(ctx, "tooltip_cow", new BlockPos(x + 3, y, z - 2));
			look(ctx, "tooltip_creeper", new BlockPos(x - 3, y, z - 2));
			// Up close (a block away): the card hangs beside the cow, not through it
			safe("tooltip_cow_close", () -> {
				run(server, f("tp @a %d %d %d", x + 3, y, z - 1));
				ctx.waitTicks(5);
				ctx.getInput().lookAt(new BlockPos(x + 3, y, z - 2));
				ctx.waitTicks(16);
				ctx.takeScreenshot("tooltip_cow_close");
				run(server, f("tp @a %d %d %d", x, y, z));
				ctx.waitTicks(5);
			});

			// Own name tag in third person
			safe("nameplate", () -> {
				ctx.getInput().lookAt(0, 0);
				camera(ctx, CameraType.THIRD_PERSON_BACK);
				ctx.waitTicks(30);
				ctx.takeScreenshot("nameplate_back");
				camera(ctx, CameraType.THIRD_PERSON_FRONT);
				ctx.waitTicks(20);
				ctx.takeScreenshot("nameplate_front");
				camera(ctx, CameraType.FIRST_PERSON);
			});
			// Effects: every tool and weapon with its enchantments as texture parts and auras, layered by the client
			// from the item's own enchantments (no item is changed): in the hotbar, in your hand (first and third person) and
			// on the ground; no enchantment glint on them, the glint stays on an item with only Unbreaking
			safe("effects_tools", () -> {
				ctx.getInput().lookAt(0, 0);
				ctx.runOnClient(mc -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
				run(server, "time set midnight", "clear @a");
				String[][] tools = {
						{"diamond_sword", "sharpness:5,fire_aspect:2,looting:3,knockback:2,sweeping_edge:3"},
						{"netherite_sword", "smite:5,fire_aspect:2"}, {"iron_sword", "bane_of_arthropods:4,sweeping_edge:2"},
						{"diamond_pickaxe", "efficiency:5,fortune:3"}, {"golden_axe", "efficiency:4,sharpness:5"}, {"iron_shovel", "efficiency:3,silk_touch:1"},
						{"diamond_spear", "lunge:3,sharpness:5,fire_aspect:2,looting:3"}, {"mace", "density:5,wind_burst:3,fire_aspect:2"},
						{"bow", "power:5,punch:2,flame:1,infinity:1"}, {"crossbow", "quick_charge:3,multishot:1"}, {"trident", "loyalty:3,channeling:1,impaling:5"},
						{"fishing_rod", "lure:3,luck_of_the_sea:3"}, {"wooden_hoe", "efficiency:5,fortune:3"}, {"shears", "efficiency:5"},
						{"stone_sword", "unbreaking:3"}};
				for (int i = 0; i < 9; i++)
					run(server, "item replace entity @a hotbar." + i + " with minecraft:" + tools[i][0] + "[enchantments={" + tools[i][1] + "}]");
				ctx.waitTicks(10);
				ctx.takeScreenshot("fxtool_hotbar");
				for (String[] t : java.util.Arrays.copyOf(tools, 5)) {
					run(server, "item replace entity @a weapon.mainhand with minecraft:" + t[0] + "[enchantments={" + t[1] + "}]");
					camera(ctx, CameraType.FIRST_PERSON);
					ctx.waitTicks(12);
					ctx.takeScreenshot("fxtool_" + t[0] + "_first");
					camera(ctx, CameraType.THIRD_PERSON_FRONT);
					ctx.waitTicks(12);
					ctx.takeScreenshot("fxtool_" + t[0] + "_third");
				}
				camera(ctx, CameraType.FIRST_PERSON);
				run(server, "clear @a");
				// on the ground, in a row in front of you
				for (int i = 0; i < 8; i++)
					run(server, "summon minecraft:item " + (x - 3.5 + i) + " " + (y + 0.2) + " " + (z + 3) + " {PickupDelay:32767,Age:-32768,NoGravity:1b,Motion:[0d,0d,0d],Item:{id:\"minecraft:" + tools[i][0]
							+ "\",count:1,components:{\"minecraft:enchantments\":{" + tools[i][1].replace(":", ":").replace(",", ",") + "}}}}");
				ctx.getInput().lookAt(0, 30);
				ctx.waitTicks(15);
				ctx.takeScreenshot("fxtool_ground");
				run(server, "kill @e[type=minecraft:item]");
				ctx.getInput().lookAt(0, 0);
				ctx.runOnClient(mc -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.FULL));
			});
			// Effects: worn armour seen by someone else: every material with its enchantment's glow, and trimmed
			// pieces with a seam of light round the trim in the colour of their enchantment
			safe("effects_armour", () -> {
				ctx.getInput().lookAt(0, 12);
				ctx.runOnClient(mc -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
				camera(ctx, CameraType.FIRST_PERSON);
				run(server, "time set midnight", "clear @a", "kill @e[type=minecraft:mannequin]");
				server.runOnServer(sv -> LOG.info("LIBRERIA-ARMOURFX pack {} others {}", nl.libreria.core.Pack.has(player), nl.libreria.glint.GlintMod.AURA.get(player)));
				// material, enchantment, trim pattern (or none)
				String[][][] rows = {
						{{"diamond", "fire_protection:4", ""}, {"iron", "protection:4", ""}, {"golden", "blast_protection:4", ""}, {"netherite", "projectile_protection:4", ""}, {"leather", "thorns:3", ""}},
						{{"chainmail", "protection:2", ""}, {"copper", "mending:1", ""}, {"diamond", "feather_falling:4", ""}, {"iron", "binding_curse:1", ""}, {"golden", "fire_protection:1", ""}},
						{{"diamond", "fire_protection:4", "coast"}, {"netherite", "protection:4", "ward"}, {"iron", "blast_protection:3", "sentry"}, {"golden", "projectile_protection:4", "wild"}, {"diamond", "thorns:3", "rib"}}};
				String[] slots = {"head", "chest", "legs", "feet"};
				String[] pieces = {"helmet", "chestplate", "leggings", "boots"};
				for (int r = 0; r < rows.length; r++) {
					run(server, "tp @e[type=minecraft:mannequin] ~ -100 ~", "kill @e[type=minecraft:mannequin]");
					for (int i = 0; i < rows[r].length; i++) {
						String[] m = rows[r][i];
						StringBuilder eq = new StringBuilder();
						for (int k = 0; k < 4; k++) {
							String mat = m[0].equals("golden") ? "golden" : m[0];
							String trim = m[2].isEmpty() ? "" : ",\"minecraft:trim\":{pattern:\"minecraft:" + m[2] + "\",material:\"minecraft:gold\"}";
							eq.append(k == 0 ? "" : ",").append(slots[k]).append(":{id:\"minecraft:").append(mat).append("_").append(pieces[k])
									.append("\",components:{\"minecraft:enchantments\":{").append(m[1]).append("}").append(trim).append("}}");
						}
						run(server, "summon minecraft:mannequin " + (x - 4 + i * 2) + " " + y + " " + (z + 5) + " {Rotation:[180f,0f],hide_description:1b,immovable:1b,equipment:{" + eq + "}}");
					}
					ctx.waitTicks(25);
					ctx.takeScreenshot("fxarmour_row" + r);
				}
				run(server, "time set noon");
				ctx.waitTicks(10);
				ctx.takeScreenshot("fxarmour_row2_day");
				// elytras from behind: Mending, Unbreaking, a curse
				run(server, "time set midnight", "tp @e[type=minecraft:mannequin] ~ -100 ~", "kill @e[type=minecraft:mannequin]");
				String[] wings = {"mending:1", "unbreaking:3", "binding_curse:1", "mending:1,unbreaking:3"};
				for (int i = 0; i < wings.length; i++)
					run(server, "summon minecraft:mannequin " + (x - 3 + i * 2) + " " + y + " " + (z + 5) + " {Rotation:[0f,0f],hide_description:1b,immovable:1b,equipment:{chest:{id:\"minecraft:elytra\",components:{\"minecraft:enchantments\":{" + wings[i] + "}}}}}");
				ctx.waitTicks(25);
				ctx.takeScreenshot("fxarmour_elytra");
				run(server, "time set midnight", "kill @e[type=minecraft:mannequin]");
				ctx.getInput().lookAt(0, 0);
				ctx.runOnClient(mc -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.FULL));
			});
			// Effects: armour sets from the armour you wear (all four pieces sharing an enchantment), Thorns as
			// spikes flying off you
			safe("effects_sets", () -> {
				ctx.getInput().lookAt(0, 0);
				ctx.runOnClient(mc -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
				run(server, "time set midnight", "clear @a", "kill @e[type=minecraft:mannequin]");
				String[][] sets = {{"fire_protection:4", "fire4"}, {"protection:4", "protection4"}, {"blast_protection:4", "blast4"},
						{"projectile_protection:4", "projectile4"}, {"thorns:3", "thorns3"}, {"mending:1", "mending"}, {"binding_curse:1", "binding"},
						{"vanishing_curse:1", "vanishing"}, {"protection:4,thorns:3", "protection4_thorns"}};
				// loose pieces (no set): each glows with its own enchantment, on you too in third person
				run(server, "item replace entity @a armor.head with minecraft:iron_helmet[enchantments={binding_curse:1}]",
						"item replace entity @a armor.chest with minecraft:diamond_chestplate[enchantments={blast_protection:4}]",
						"item replace entity @a armor.legs with minecraft:golden_leggings[enchantments={projectile_protection:4}]",
						"item replace entity @a armor.feet with minecraft:netherite_boots[enchantments={frost_walker:2,unbreaking:3}]");
				camera(ctx, CameraType.THIRD_PERSON_FRONT);
				ctx.waitTicks(30);
				ctx.takeScreenshot("fxown_front");
				camera(ctx, CameraType.THIRD_PERSON_BACK);
				ctx.waitTicks(15);
				ctx.takeScreenshot("fxown_back");
				for (String[] k : sets) {
					wearMaterial(server, "diamond", k[0]);
					camera(ctx, CameraType.THIRD_PERSON_FRONT);
					ctx.waitTicks(40);
					server.runOnServer(sv -> LOG.info("LIBRERIA-SET {} -> {}", k[1], nl.libreria.glint.GlintMod.debugSet(player)));
					ctx.takeScreenshot("fxset_" + k[1] + "_front");
					if (k[1].startsWith("thorns")) {
						for (int f = 1; f < 4; f++) {
							ctx.waitTicks(4);
							ctx.takeScreenshot("fxset_" + k[1] + "_front_" + f);
						}
					}
					camera(ctx, CameraType.THIRD_PERSON_BACK);
					ctx.waitTicks(15);
					ctx.takeScreenshot("fxset_" + k[1] + "_back");
				}
				camera(ctx, CameraType.FIRST_PERSON);
				run(server, "clear @a");
				ctx.runOnClient(mc -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.FULL));
			});
			// Seamless glass: a wall of glass, stained glass, panes and tinted glass (with a stone block in the middle of
			// the glass), then the same with the setting off (the vanilla look comes back)
			safe("glass", () -> {
				ctx.getInput().lookAt(0, 8);
				ctx.runOnClient(mc -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
				camera(ctx, CameraType.FIRST_PERSON);
				run(server, "time set noon", "clear @a",
						f("fill %d %d %d %d %d %d minecraft:glass", x - 6, y, z + 6, x - 3, y + 3, z + 6),
						f("setblock %d %d %d minecraft:stone", x - 5, y + 2, z + 6),
						f("fill %d %d %d %d %d %d minecraft:light_blue_stained_glass", x - 2, y, z + 6, x - 1, y + 3, z + 6),
						f("fill %d %d %d %d %d %d minecraft:glass_pane", x, y, z + 6, x + 2, y + 3, z + 6),
						f("fill %d %d %d %d %d %d minecraft:red_stained_glass_pane", x + 3, y, z + 6, x + 4, y + 3, z + 6),
						f("fill %d %d %d %d %d %d minecraft:tinted_glass", x + 5, y, z + 6, x + 6, y + 3, z + 6));
				ctx.waitTicks(30);
				ctx.takeScreenshot("glass_seamless");
				ctx.getInput().lookAt(-25, 20);
				ctx.waitTicks(5);
				ctx.takeScreenshot("glass_seamless_angle");
				ctx.getInput().lookAt(0, 8);
				// switching it off sends the off-pack: "Yes" to it, like a player (the network walk already says yes)
				boolean here = network == null;
				if (here) NetworkTest.acceptServerPack();
				server.runOnServer(sv -> nl.libreria.glint.GlintMod.GLASS.set(player, false));
				ctx.waitTicks(200);
				ctx.takeScreenshot("glass_off");
				server.runOnServer(sv -> nl.libreria.glint.GlintMod.GLASS.set(player, true));
				ctx.waitTicks(100);
				if (here) NetworkTest.stopAccepting();
				run(server, f("fill %d %d %d %d %d %d minecraft:air", x - 6, y, z + 6, x + 6, y + 3, z + 6));
				ctx.getInput().lookAt(0, 0);
				ctx.runOnClient(mc -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.FULL));
			});
			// Your own armour's particles in third person: a set (one orbit), and ordinary Protection armour
			safe("armour_third", () -> {
				run(server, "item replace entity @a armor.head with minecraft:diamond_helmet[enchantments={fire_protection:4}]",
						"item replace entity @a armor.chest with minecraft:diamond_chestplate[enchantments={fire_protection:4}]",
						"item replace entity @a armor.legs with minecraft:diamond_leggings[enchantments={fire_protection:4}]",
						"item replace entity @a armor.feet with minecraft:diamond_boots[enchantments={fire_protection:4}]");
				ctx.getInput().lookAt(0, 0);
				camera(ctx, CameraType.THIRD_PERSON_BACK);
				ctx.waitTicks(40);
				ctx.takeScreenshot("armour_third_set");
				server.runOnServer(s -> LOG.info("AURA-DEBUG set: {}", nl.libreria.glint.GlintMod.debugAura(player)));
				run(server, "item replace entity @a armor.head with minecraft:diamond_helmet[enchantments={protection:4,unbreaking:3,mending:1}]",
						"item replace entity @a armor.chest with minecraft:diamond_chestplate[enchantments={protection:4,unbreaking:3,mending:1}]",
						"item replace entity @a armor.legs with minecraft:diamond_leggings[enchantments={protection:3,unbreaking:3}]",
						"item replace entity @a armor.feet with minecraft:diamond_boots[enchantments={feather_falling:4,protection:4}]");
				ctx.waitTicks(40);
				ctx.takeScreenshot("armour_third_protection");
				ctx.waitTicks(9);
				ctx.takeScreenshot("armour_third_protection_2");
				server.runOnServer(s -> LOG.info("AURA-DEBUG protection: {}", nl.libreria.glint.GlintMod.debugAura(player)));
				camera(ctx, CameraType.FIRST_PERSON);
				ctx.waitTicks(20);
				ctx.takeScreenshot("armour_first_person");
				run(server, "item replace entity @a armor.head with minecraft:air", "item replace entity @a armor.chest with minecraft:air",
						"item replace entity @a armor.legs with minecraft:air", "item replace entity @a armor.feet with minecraft:air");
			});

			// Found gear: a village weaponsmith's chest, opened by the player (in survival): the gear in it has its story
			// already, readable in the chest
			safe("found_chest", () -> {
				run(server, "clear @a", "time set noon", "gamemode survival @a");
				int slot = -1;
				for (int attempt = 0; attempt < 10 && slot < 0; attempt++) {
					run(server, f("setblock %d %d %d minecraft:air", x + 1, y, z + 2),
							f("setblock %d %d %d minecraft:chest[facing=north]{LootTable:\"minecraft:chests/village/village_weaponsmith\",LootTableSeed:%dL}",
									x + 1, y, z + 2, 4242 + attempt));
					ctx.waitTicks(3);
					ctx.getInput().lookAt(new BlockPos(x + 1, y, z + 2));
					ctx.waitTicks(2);
					String hit = ctx.computeOnClient(mc -> String.valueOf(mc.hitResult == null ? null : mc.hitResult.getType()));
					key(ctx, "pressKey", ctx.computeOnClient(mc -> mc.options.keyUse));
					ctx.waitTicks(12);
					final int cx = x + 1, cy = y, cz = z + 2;
					String menu = server.computeOnServer(sv -> {
						String open = player.containerMenu.getClass().getSimpleName();
						// Not opened by the click (the crosshair missed?): opened as the game does when you click it
						if (!(player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu)
								&& player.level().getBlockEntity(new BlockPos(cx, cy, cz)) instanceof net.minecraft.world.MenuProvider mp) player.openMenu(mp);
						return open + " → " + player.containerMenu.getClass().getSimpleName() + " · " + player.gameMode.getGameModeForPlayer();
					});
					ctx.waitTicks(6);
					String items = server.computeOnServer(sv -> {
						if (!(player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu m)) return "-";
						StringBuilder b = new StringBuilder();
						for (int i = 0; i < m.getContainer().getContainerSize(); i++) {
							var it = m.getContainer().getItem(i);
							if (!it.isEmpty()) b.append(i).append('=').append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it.getItem()).getPath())
									.append(it.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA) == null ? "" : "*").append(' ');
						}
						return b.toString();
					});
					LOG.info("LIBRERIA-FOUND chest {}: crosshair {}, menu {}, items {}", attempt, hit, menu, items);
					slot = server.computeOnServer(sv -> {
						if (!(player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu m)) return -1;
						for (int i = 0; i < m.getContainer().getContainerSize(); i++) {
							var cd = m.getContainer().getItem(i).get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
							if (cd != null && cd.copyTag().contains("libreria_found")) return i;
						}
						return -1;
					});
					LOG.info("LIBRERIA-FOUND chest {}: gear with a story in slot {}", attempt, slot);
					if (slot < 0) close(ctx, server, player);
				}
				if (slot >= 0) {
					double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
					int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
					int h = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
					int left = (w - 176) / 2, top = (h - 168) / 2;
					ctx.getInput().setCursorPos((left + 16 + 18 * (slot % 9)) * scale, (top + 26 + 18 * (slot / 9)) * scale);
					ctx.waitTicks(6);
					noToasts(ctx);
					ctx.takeScreenshot("found_in_chest");
					close(ctx, server, player);
				}
				run(server, f("setblock %d %d %d minecraft:air", x + 1, y, z + 2), "kill @e[type=minecraft:item]", "clear @a");
				ctx.getInput().lookAt(0, 0);
			});

			// Light levels: a torch right-clicked into the air (nothing gets placed) shows the light level on every floor
			// around you, red and yellow tiles where monsters can spawn; the same click again hides them
			safe("light_levels", () -> {
				ctx.runOnClient(mc -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
				camera(ctx, CameraType.FIRST_PERSON);
				run(server, "time set midnight", "clear @a", "item replace entity @a weapon.mainhand with minecraft:torch 16",
						f("fill %d %d %d %d %d %d minecraft:stone_bricks hollow", x + 3, y - 1, z + 3, x + 9, y + 3, z + 9),
						f("fill %d %d %d %d %d %d minecraft:air", x + 3, y, z + 4, x + 3, y + 1, z + 6),
						f("setblock %d %d %d minecraft:torch", x - 4, y, z + 6));
				ctx.waitTicks(10);
				ctx.getInput().lookAt(0, -80);
				ctx.waitTicks(3);
				key(ctx, "pressKey", ctx.computeOnClient(mc -> mc.options.keyUse));
				ctx.waitTicks(25);
				ctx.getInput().lookAt(-30, 35);
				ctx.waitTicks(15);
				noToasts(ctx);
				ctx.takeScreenshot("light_levels");
				ctx.getInput().lookAt(0, -80);
				ctx.waitTicks(3);
				key(ctx, "pressKey", ctx.computeOnClient(mc -> mc.options.keyUse));
				ctx.waitTicks(10);
				run(server, f("fill %d %d %d %d %d %d minecraft:air", x + 3, y - 1, z + 3, x + 9, y + 3, z + 9),
						f("fill %d %d %d %d %d %d minecraft:grass_block", x + 3, y - 1, z + 3, x + 9, y - 1, z + 9),
						f("setblock %d %d %d minecraft:air", x - 4, y, z + 6), "clear @a", "time set noon");
				ctx.getInput().lookAt(0, 0);
				ctx.runOnClient(mc -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.FULL));
			});
			// Pings: sneak + Q at a far spot (the item stays in your hand): your own beam to it
			safe("ping", () -> {
				run(server, "clear @a", "item replace entity @a weapon.mainhand with minecraft:iron_sword",
						f("fill %d %d %d %d %d %d minecraft:oak_log", x + 2, y, z + 20, x + 2, y + 3, z + 20));
				ctx.waitTicks(5);
				ctx.getInput().lookAt(new BlockPos(x + 2, y + 1, z + 20));
				ctx.waitTicks(3);
				key(ctx, "holdKey", ctx.computeOnClient(mc -> mc.options.keyShift));
				ctx.waitTicks(3);
				key(ctx, "pressKey", ctx.computeOnClient(mc -> mc.options.keyDrop));
				ctx.waitTicks(3);
				key(ctx, "releaseKey", ctx.computeOnClient(mc -> mc.options.keyShift));
				ctx.waitTicks(20);
				boolean kept = server.computeOnServer(sv -> player.getMainHandItem().is(net.minecraft.world.item.Items.IRON_SWORD));
				LOG.info("LIBRERIA-PING sword still in hand: {}", kept);
				noToasts(ctx);
				ctx.takeScreenshot("ping_own");
				run(server, "execute as @a run ping clear", f("fill %d %d %d %d %d %d minecraft:air", x + 2, y, z + 20, x + 2, y + 3, z + 20), "clear @a");
				server.runOnServer(sv -> nl.libreria.core.Guide.clear(player));
				ctx.getInput().lookAt(0, 0);
			});
			// A search lights up what you looked for: in the chest it lies in (the rest dims) and in your inventory
			safe("search_highlight", () -> {
				run(server, "clear @a", "gamemode survival @a", f("setblock %d %d %d minecraft:air", x + 1, y, z + 2),
						f("setblock %d %d %d minecraft:chest[facing=north]", x + 1, y, z + 2),
						f("item replace block %d %d %d container.0 with minecraft:diamond 20", x + 1, y, z + 2),
						f("item replace block %d %d %d container.1 with minecraft:oak_log 64", x + 1, y, z + 2),
						f("item replace block %d %d %d container.2 with minecraft:cobblestone 40", x + 1, y, z + 2),
						f("item replace block %d %d %d container.10 with minecraft:diamond 7", x + 1, y, z + 2),
						f("item replace block %d %d %d container.12 with minecraft:iron_ingot 30", x + 1, y, z + 2),
						f("item replace block %d %d %d container.20 with minecraft:golden_apple 3", x + 1, y, z + 2),
						"give @a minecraft:bread 12", "give @a minecraft:diamond 3", "give @a minecraft:torch 32");
				server.runOnServer(sv -> nl.libreria.chests.Wanted.start(player, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND)));
				ctx.waitTicks(5);
				ctx.getInput().lookAt(new BlockPos(x + 1, y, z + 2));
				ctx.waitTicks(3);
				key(ctx, "pressKey", ctx.computeOnClient(mc -> mc.options.keyUse));
				ctx.waitTicks(12);
				ctx.getInput().setCursorPos(2, 2);
				noToasts(ctx);
				ctx.takeScreenshot("search_chest");
				close(ctx, server, player);
				ctx.waitTicks(5);
				net.minecraft.world.item.Item stillChest = server.computeOnServer(sv -> nl.libreria.chests.Wanted.of(player));
				LOG.info("LIBRERIA-WANTED after closing the chest: {}", String.valueOf(stillChest));
				// Looked for something you carry: your inventory
				server.runOnServer(sv -> nl.libreria.chests.Wanted.start(player, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND)));
				ctx.waitTicks(5);
				key(ctx, "pressKey", ctx.computeOnClient(mc -> mc.options.keyInventory));
				ctx.waitTicks(10);
				ctx.getInput().setCursorPos(2, 2);
				noToasts(ctx);
				ctx.takeScreenshot("search_inventory");
				key(ctx, "pressKey", ctx.computeOnClient(mc -> mc.options.keyInventory));
				ctx.waitTicks(5);
				net.minecraft.world.item.Item stillInventory = server.computeOnServer(sv -> nl.libreria.chests.Wanted.of(player));
				LOG.info("LIBRERIA-WANTED after closing the inventory: {}", String.valueOf(stillInventory));
				run(server, "clear @a", f("setblock %d %d %d minecraft:air", x + 1, y, z + 2));
				ctx.getInput().lookAt(0, 0);
			});
			// Armour stands: sneak + right-click with an empty hand opens the window; item frames: sneak + right-click hides
			// the frame (only the item shows)
			safe("stands", () -> {
				run(server, "clear @a", "kill @e[type=minecraft:armor_stand]", "kill @e[type=minecraft:item_frame]",
						f("summon minecraft:armor_stand %d %d %d {Rotation:[180f,0f],ShowArms:1b,equipment:{chest:{id:\"minecraft:diamond_chestplate\"},head:{id:\"minecraft:iron_helmet\"}}}", x, y, z + 3));
				ctx.waitTicks(10);
				ctx.getInput().lookAt(new BlockPos(x, y + 1, z + 3));
				ctx.waitTicks(3);
				key(ctx, "holdKey", ctx.computeOnClient(mc -> mc.options.keyShift));
				ctx.waitTicks(2);
				key(ctx, "pressKey", ctx.computeOnClient(mc -> mc.options.keyUse));
				ctx.waitTicks(3);
				key(ctx, "releaseKey", ctx.computeOnClient(mc -> mc.options.keyShift));
				ctx.waitTicks(15);
				ctx.getInput().setCursorPos(2, 2);
				noToasts(ctx);
				ctx.takeScreenshot("stand_window");
				close(ctx, server, player);
				run(server, "kill @e[type=minecraft:armor_stand]",
						f("fill %d %d %d %d %d %d minecraft:spruce_planks", x - 2, y, z + 3, x + 2, y + 2, z + 3));
				// hung as a player would (a summoned frame has no wall block in 26.3)
				server.runOnServer(sv -> {
					ServerLevel lv = (ServerLevel) player.level();
					var frame = new net.minecraft.world.entity.decoration.ItemFrame(lv, new BlockPos(x, y + 1, z + 2), net.minecraft.core.Direction.NORTH);
					frame.setItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
					lv.addFreshEntity(frame);
				});
				ctx.waitTicks(10);
				ctx.getInput().lookAt(new BlockPos(x, y + 1, z + 2));
				ctx.waitTicks(3);
				key(ctx, "holdKey", ctx.computeOnClient(mc -> mc.options.keyShift));
				ctx.waitTicks(2);
				String target = ctx.computeOnClient(mc -> String.valueOf(mc.hitResult == null ? null : mc.hitResult.getType()));
				key(ctx, "pressKey", ctx.computeOnClient(mc -> mc.options.keyUse));
				LOG.info("LIBRERIA-FRAMES crosshair on: {}", target);
				ctx.waitTicks(3);
				key(ctx, "releaseKey", ctx.computeOnClient(mc -> mc.options.keyShift));
				ctx.waitTicks(10);
				boolean hidden = server.computeOnServer(sv -> !((ServerLevel) player.level()).getEntitiesOfClass(
						net.minecraft.world.entity.decoration.ItemFrame.class, player.getBoundingBox().inflate(8), fr -> fr.isInvisible()).isEmpty());
				LOG.info("LIBRERIA-FRAMES hidden: {}", hidden);
				String seen = ctx.computeOnClient(mc -> {
					StringBuilder b = new StringBuilder("eye " + mc.player.getEyePosition() + " look " + mc.player.getViewVector(1f));
					if (mc.hitResult != null) b.append(" hit ").append(mc.hitResult.getType()).append(" at ").append(mc.hitResult.getLocation());
					for (var en : mc.level.entitiesForRendering()) {
						if (en instanceof net.minecraft.world.entity.decoration.ItemFrame fr) b.append(" | frame ").append(fr.getBoundingBox())
								.append(" pickable ").append(fr.isPickable());
					}
					return b.toString();
				});
				LOG.info("LIBRERIA-FRAMES client: {}", seen);
				ctx.takeScreenshot("frame_hidden");
				// Hidden, it's locked: a right-click on it opens the chest it hangs on (a label on a chest)
				if (!hidden) {
					server.runOnServer(sv -> ((ServerLevel) player.level()).getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class,
							player.getBoundingBox().inflate(8)).forEach(fr -> fr.setInvisible(true)));
				}
				// (a chest under a solid block doesn't open: room above it)
				run(server, f("setblock %d %d %d minecraft:chest[facing=north]", x, y + 1, z + 3), f("setblock %d %d %d minecraft:air", x, y + 2, z + 3));
				ctx.waitTicks(5);
				ctx.getInput().lookAt(new BlockPos(x, y + 1, z + 2));
				ctx.waitTicks(3);
				String on = ctx.computeOnClient(mc -> String.valueOf(mc.hitResult == null ? null : mc.hitResult.getType()));
				key(ctx, "pressKey", ctx.computeOnClient(mc -> mc.options.keyUse));
				ctx.waitTicks(10);
				String menu = server.computeOnServer(sv -> player.containerMenu.getClass().getSimpleName());
				int rotation = server.computeOnServer(sv -> ((ServerLevel) player.level()).getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class,
						player.getBoundingBox().inflate(8)).stream().mapToInt(net.minecraft.world.entity.decoration.ItemFrame::getRotation).sum());
				LOG.info("LIBRERIA-FRAMES click through (crosshair on {}): menu {}, rotation {}", on, menu, rotation);
				noToasts(ctx);
				ctx.takeScreenshot("frame_click_through");
				close(ctx, server, player);
				run(server, "kill @e[type=minecraft:item_frame]", f("fill %d %d %d %d %d %d minecraft:air", x - 2, y, z + 3, x + 2, y + 2, z + 3));
				ctx.getInput().lookAt(0, 0);
			});
			// A stand wearing four parts of one story: looking at it shows the whole story beside it, from a few steps and
			// from up close
			safe("stand_lore", () -> {
				run(server, "kill @e[type=minecraft:armor_stand]", f("summon minecraft:armor_stand %d %d %d {Rotation:[180f,0f],ShowArms:1b}", x, y, z + 3));
				ctx.waitTicks(5);
				server.runOnServer(sv -> {
					for (var st : ((ServerLevel) player.level()).getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,
							player.getBoundingBox().inflate(8))) nl.libreria.info.InfoMod.debugStand(st);
				});
				ctx.waitTicks(10);
				ctx.getInput().lookAt(new BlockPos(x, y + 1, z + 3));
				ctx.waitTicks(16);
				noToasts(ctx);
				ctx.takeScreenshot("stand_lore");
				run(server, f("tp @a %d %d %d", x, y, z + 2));
				ctx.waitTicks(5);
				ctx.getInput().lookAt(new BlockPos(x, y + 1, z + 3));
				ctx.waitTicks(16);
				ctx.takeScreenshot("stand_lore_close");
				run(server, "kill @e[type=minecraft:armor_stand]", f("tp @a %d %d %d", x, y, z));
				ctx.waitTicks(5);
				ctx.getInput().lookAt(0, 0);
			});


			// Tooltips: found gear with its text (a part of a story, named), what each enchantment does and what it adds
			// to the attack damage; a book; a pickaxe with its owner's words; then the same sword with the details off
			safe("tooltips", () -> {
				run(server, "clear @a");
				server.runOnServer(sv -> nl.libreria.info.InfoMod.debugFound(player));
				ctx.waitTicks(10);
				ctx.getInput().pressKey(o2 -> o2.keyInventory);
				ctx.waitTicks(20);
				noToasts(ctx);
				double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
				int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
				int h = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
				int left = (w - 176) / 2, top = (h - 166) / 2;
				String[] names = {"sword", "mace", "boots", "book", "pickaxe", "magma", "potion", "ominous"};
				for (int i = 0; i < names.length; i++) {
					ctx.getInput().setCursorPos((left + 16 + 18 * i) * scale, (top + 150) * scale);
					ctx.waitTicks(4);
					ctx.takeScreenshot("tooltip_" + names[i]);
				}
				server.runOnServer(sv -> nl.libreria.info.InfoMod.setDetails(player, false));
				ctx.waitTicks(10);
				ctx.getInput().setCursorPos((left + 16) * scale, (top + 150) * scale);
				ctx.waitTicks(4);
				ctx.takeScreenshot("tooltip_sword_details_off");
				server.runOnServer(sv -> nl.libreria.info.InfoMod.setDetails(player, true));
				ctx.getInput().setCursorPos(2, 2);
				ctx.getInput().pressKey(o2 -> o2.keyInventory);
				ctx.waitTicks(5);
				// Writing on what's in your hand (what sneak + right-click on an anvil opens)
				server.runOnServer(sv -> nl.libreria.info.InfoMod.debugDescribe(player));
				ctx.waitTicks(15);
				ctx.takeScreenshot("describe_window");
				// The lore book: the stories you know a part of, one story to read, and everything you found
				ctx.waitTicks(45);
				for (String page : new String[] {"index", "story", "finds"}) {
					server.runOnServer(sv -> nl.libreria.info.InfoMod.debugLoreBook(player, page));
					ctx.waitTicks(12);
					ctx.takeScreenshot("lorebook_" + page);
					if (page.equals("story")) {
						// a part found, and one still missing
						for (int to : new int[] {1, 2}) {
							String screen = ctx.computeOnClient(mc -> {
								if (mc.screen == null) return "none";
								for (var m : mc.screen.getClass().getMethods()) {
									if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class && m.getName().toLowerCase(java.util.Locale.ROOT).contains("page")) {
										try {
											m.invoke(mc.screen, to);
											return mc.screen.getClass().getSimpleName() + "." + m.getName();
										} catch (ReflectiveOperationException e) {
											return "failed " + e;
										}
									}
								}
								return mc.screen.getClass().getSimpleName() + " (no page method)";
							});
							LOG.info("LIBRERIA-LOREBOOK page {}: {}", to, screen);
							ctx.waitTicks(4);
							ctx.takeScreenshot("lorebook_story_page" + (to + 1));
						}
					}
				}
				ctx.setScreen(() -> null);
				ctx.waitTicks(5);
			});

			// Items of every rarity in the inventory and hotbar
			run(server, "clear @a",
					"give @a minecraft:diamond_sword[enchantments={sharpness:5}]",
					"give @a minecraft:netherite_sword[enchantments={fire_aspect:2}]",
					"give @a minecraft:bow[enchantments={flame:1}]",
					"give @a minecraft:elytra",
					"give @a minecraft:totem_of_undying",
					"give @a minecraft:nether_star",
					"give @a minecraft:enchanted_golden_apple 3",
					"give @a minecraft:dragon_egg",
					"give @a minecraft:diamond_pickaxe",
					"give @a minecraft:beacon",
					"give @a minecraft:trident",
					"give @a minecraft:heart_of_the_sea",
					"give @a minecraft:enchanted_book[stored_enchantments={mending:1}]",
					"give @a minecraft:mace",
					"give @a minecraft:experience_bottle 16",
					"give @a minecraft:stone 64",
					"give @a minecraft:oak_log 32",
					"give @a minecraft:bread 12",
					"give @a minecraft:diamond_chestplate[enchantments={protection:4}]",
					"give @a minecraft:music_disc_pigstep",
					"give @a minecraft:wither_skeleton_skull");
			ctx.waitTicks(20);
			safe("hotbar", () -> ctx.takeScreenshot("hotbar"));
			safe("inventory", () -> {
				ctx.getInput().pressKey(o2 -> o2.keyInventory);
				// The buttons in the crafting grid come with the next refresh (once a second)
				ctx.waitTicks(25);
				ctx.getInput().setCursorPos(2, 2);
				noToasts(ctx);
				ctx.waitTicks(2);
				ctx.takeScreenshot("inventory");
				// Where things are in the inventory screen, in window pixels
				double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
				int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
				int h = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
				int left = (w - 176) / 2, top = (h - 166) / 2;
				// The buttons on the window's left edge (a click outside the window): left deposits
				ctx.getInput().setCursorPos((left - 14) * scale, (top + 61) * scale);
				ctx.waitTicks(3);
				ctx.getInput().pressKey(o2 -> o2.keyAttack);
				ctx.waitTicks(10);
				ctx.getInput().setCursorPos(2, 2);
				ctx.waitTicks(2);
				ctx.takeScreenshot("inventory_deposit");
				// Sorting: a double-click on an empty slot (the bottom row of the inventory)
				ctx.getInput().setCursorPos((left + 16) * scale, (top + 128) * scale);
				ctx.waitTicks(3);
				ctx.getInput().pressKey(o2 -> o2.keyAttack);
				ctx.getInput().pressKey(o2 -> o2.keyAttack);
				ctx.waitTicks(10);
				ctx.getInput().setCursorPos(2, 2);
				ctx.waitTicks(2);
				ctx.takeScreenshot("inventory_sorted");
				// The recipe book is the recipe book again
				ctx.getInput().setCursorPos((left + 114) * scale, (top + 70) * scale);
				ctx.waitTicks(3);
				ctx.getInput().pressKey(o2 -> o2.keyAttack);
				ctx.waitTicks(10);
				ctx.getInput().setCursorPos(2, 2);
				ctx.waitTicks(2);
				ctx.takeScreenshot("inventory_recipe_book");
				// Book open: the window sits further right (as the game places it); the same button closes the book
				int left2 = w < 379 ? left : 177 + (w - 176 - 200) / 2;
				ctx.getInput().setCursorPos((left2 + 114) * scale, (top + 70) * scale);
				ctx.waitTicks(3);
				ctx.getInput().pressKey(o2 -> o2.keyAttack);
				ctx.waitTicks(10);
				// The magnifying glass under the arrows: a right-click opens your items
				ctx.getInput().setCursorPos((left - 14) * scale, (top + 80) * scale);
				ctx.waitTicks(3);
				ctx.getInput().pressKey(o2 -> o2.keyUse);
				ctx.waitTicks(15);
				ctx.getInput().setCursorPos(2, 2);
				noToasts(ctx);
				ctx.takeScreenshot("items_from_button");
				close(ctx, server, player);
			});

			// A chest with the sort button
			safe("chest", () -> {
				ctx.getInput().lookAt(new BlockPos(x - 2, y, z + 2));
				ctx.waitTicks(5);
				ctx.getInput().pressKey(o2 -> o2.keyUse);
				ctx.waitTicks(20);
				ctx.getInput().setCursorPos(2, 2);
				noToasts(ctx);
				ctx.waitTicks(2);
				ctx.takeScreenshot("chest_sort_button");
				close(ctx, server, player);
			});

			// Achievements in the advancements screen (L)
			safe("advancements", () -> {
				ctx.getInput().pressKey(o2 -> o2.keyAdvancements);
				ctx.waitTicks(20);
				ctx.takeScreenshot("advancements");
				ctx.setScreen(() -> null);
				ctx.waitTicks(5);
			});

			// "Your items": everything on you and in your chests, then the live search for "o"
			safe("items", () -> {
				server.runOnServer(s -> nl.libreria.chests.gui.ItemSearch.open(player, ""));
				ctx.waitTicks(10);
				ctx.getInput().setCursorPos(2, 2);
				noToasts(ctx);
				ctx.takeScreenshot("items_window");
				close(ctx, server, player);
				// The whole way: open, type "dia" (results change while typing), click the first one
				server.runOnServer(s -> nl.libreria.chests.gui.ItemSearch.open(player, ""));
				ctx.waitTicks(10);
				typeText(ctx, "dia");
				ctx.waitTicks(10);
				ctx.getInput().setCursorPos(2, 2);
				noToasts(ctx);
				ctx.takeScreenshot("items_search");
				double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
				int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
				int h = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
				int left = (w - 176) / 2, top = (h - 166) / 2;
				ctx.getInput().setCursorPos((left + 16) * scale, (top + 92) * scale);
				ctx.waitTicks(3);
				ctx.takeScreenshot("items_hover");
				ctx.getInput().pressKey(o2 -> o2.keyAttack);
				ctx.waitTicks(30);
				ctx.takeScreenshot("items_located");
				close(ctx, server, player);
			});

			// An achievement for everyone: the banner at the top and fireworks
			safe("achievement", () -> {
				ctx.getInput().lookAt(0, -35);
				ctx.waitTicks(5);
				server.runOnServer(s -> nl.libreria.collector.CollectorMod.debugAnnounce(player));
				ctx.waitTicks(12);
				noToasts(ctx);
				ctx.takeScreenshot("achievement_banner");
				ctx.waitTicks(14);
				noToasts(ctx);
				ctx.takeScreenshot("achievement_fireworks");
				ctx.waitTicks(60);
			});

			// A hill with trees in the claimed land, so the buildings stand on uneven ground
			int cx = x >> 4, cz = z >> 4;
			int hx = cx * 16 + 8, hz = (cz - 1) * 16 + 6;
			int[][] hill = {{9, 0}, {7, 1}, {6, 2}, {4, 3}, {3, 4}, {1, 5}};
			for (int[] layer : hill) {
				int r = layer[0], up = layer[1];
				run(server, f("fill %d %d %d %d %d %d minecraft:grass_block", hx - r, y - 1 + up, hz - r + 2, hx + r - 2, y - 1 + up, hz + r));
			}
			run(server, f("place feature minecraft:oak %d %d %d", hx - 4, y + 3, hz + 3),
					f("place feature minecraft:birch %d %d %d", (cx + 2) * 16 + 6, y, cz * 16 + 9),
					f("place feature minecraft:oak %d %d %d", (cx + 2) * 16 + 11, y, cz * 16 + 20));
			for (int i = 0; i < 5; i++) {
				run(server, f("fill %d %d %d %d %d %d minecraft:grass_block", (cx + 2) * 16 + 3 * i, y - 1, cz * 16 + 12,
						(cx + 2) * 16 + 3 * i + 2, y - 1 + i, cz * 16 + 31));
			}
			ctx.waitTicks(10);

			// The land map button right of the hotbar, the quick menu (G) and the pause menu entry
			safe("hotbar_button", () -> {
				ctx.getInput().lookAt(0, 0);
				ctx.waitTicks(30);
				noToasts(ctx);
				ctx.takeScreenshot("hotbar_button");
			});
			safe("quick_menu", () -> {
				run(server, "dialog show @a libreria:quick_menu");
				ctx.waitTicks(10);
				ctx.getInput().setCursorPos(2, 2);
				ctx.takeScreenshot("quick_menu");
				ctx.setScreen(() -> null);
				ctx.waitTicks(3);
				ctx.setScreen(() -> new net.minecraft.client.gui.screens.PauseScreen(true));
				ctx.waitTicks(5);
				ctx.takeScreenshot("pause_menu");
				ctx.setScreen(() -> null);
				ctx.waitTicks(3);
			});

			// The land map: camera flies up, claims are glass buildings
			safe("claim_view", () -> {
				server.runOnServer(s -> {
					var lvl = player.level();
					var here = player.chunkPosition();
					java.util.UUID steve = java.util.UUID.nameUUIDFromBytes("Steve".getBytes());
					java.util.UUID alex = java.util.UUID.nameUUIDFromBytes("Alex".getBytes());
					ClaimsMod.debugClaim(lvl, here, player.getUUID(), player.getName().getString());
					ClaimsMod.debugClaim(lvl, new net.minecraft.world.level.ChunkPos(here.x(), here.z() - 1), player.getUUID(), player.getName().getString());
					ClaimsMod.debugClaim(lvl, new net.minecraft.world.level.ChunkPos(here.x() + 2, here.z()), steve, "Steve");
					ClaimsMod.debugClaim(lvl, new net.minecraft.world.level.ChunkPos(here.x() + 2, here.z() + 1), steve, "Steve");
					ClaimsMod.debugClaim(lvl, new net.minecraft.world.level.ChunkPos(here.x() - 2, here.z() + 1), alex, "Alex");
				});
				// Open it with its key: sneak + swap hands
				ctx.getInput().holdKey(o2 -> o2.keyShift);
				ctx.waitTicks(2);
				ctx.getInput().pressKey(o2 -> o2.keySwapOffhand);
				ctx.waitTicks(3);
				ctx.getInput().releaseKey(o2 -> o2.keyShift);
				ctx.waitTicks(9);
				ctx.takeScreenshot("claim_view_opening");
				ctx.waitTicks(40);
				noToasts(ctx);
				ctx.takeScreenshot("claim_view");
				ctx.getInput().holdKeyFor(o2 -> o2.keyRight, 25);
				ctx.waitTicks(10);
				ctx.takeScreenshot("claim_view_moved");
				ctx.getInput().pressKey(o2 -> o2.keyAttack);
				ctx.waitTicks(7);
				ctx.takeScreenshot("claim_view_claimed");
				ctx.getInput().scroll(-3);
				ctx.waitTicks(20);
				ctx.takeScreenshot("claim_view_zoomed");
				// The mouse turns the view (a quarter turn to the right) and tilts it
				float[] before = new float[1];
				server.runOnServer(s -> before[0] = player.getYRot());
				boolean riding = ctx.computeOnClient(mc -> mc.player != null && mc.player.isPassenger());
				// Turning the client's own player is what the mouse does (here without a real mouse)
				float startYaw = ctx.computeOnClient(mc -> mc.player.getYRot());
				float startPitch = ctx.computeOnClient(mc -> mc.player.getXRot());
				ctx.getInput().lookAt(startYaw + 70, startPitch + 15);
				ctx.waitTicks(15);
				ctx.takeScreenshot("claim_view_turned");
				server.runOnServer(s -> LOG.info("LIBRERIA-MAP-TURN riding {} yaw {} -> {}", riding, before[0], player.getYRot()));
				ctx.getInput().lookAt(startYaw, startPitch);
				ctx.waitTicks(10);
				// Over to Steve's sloped land (east = left on the screen)
				ctx.getInput().scroll(2);
				ctx.getInput().holdKeyFor(o2 -> o2.keyLeft, 45);
				ctx.waitTicks(25);
				ctx.takeScreenshot("claim_view_hills");
				// Q closes it (Escape never reaches the server)
				ctx.getInput().pressKey(o2 -> o2.keyDrop);
				ctx.waitTicks(30);
				ctx.takeScreenshot("claim_view_closed");
			});

			// The land map to hold, and the compass bar with a marker straight ahead
			safe("land_map_item", () -> {
				net.minecraft.world.item.ItemStack[] before = new net.minecraft.world.item.ItemStack[2];
				server.runOnServer(s -> {
					before[0] = player.getMainHandItem().copy();
					before[1] = player.getOffhandItem().copy();
					player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, nl.libreria.claims.LandMapItem.create());
					player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
				});
				ctx.getInput().lookAt(0, 30);
				ctx.waitTicks(40);
				ctx.takeScreenshot("land_map_item");
				server.runOnServer(s -> {
					player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, before[0]);
					player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, before[1]);
				});
				ctx.getInput().lookAt(0, 0);
				ctx.waitTicks(10);
				ctx.takeScreenshot("top_of_screen");
				// A place name fading in at the top: first of everyone, then one someone else found first
				server.runOnServer(s -> {
					nl.libreria.core.gui.TopCard.forget(player.getUUID());
					nl.libreria.core.gui.Caption.show(player, net.minecraft.network.chat.Component.literal("Oakhaven"),
							net.minecraft.network.chat.Component.literal("Village"),
							List.of(new nl.libreria.core.gui.Caption.Part("first discovery!", nl.libreria.core.gui.Caption.GOLD)));
				});
				ctx.waitTicks(6);
				ctx.takeScreenshot("place_title_fading_in");
				ctx.waitTicks(30);
				ctx.takeScreenshot("place_title");
				server.runOnServer(s -> nl.libreria.core.gui.Caption.show(player, net.minecraft.network.chat.Component.literal("Ashglen Forest"),
						net.minecraft.network.chat.Component.literal("Forest"),
						List.of(new nl.libreria.core.gui.Caption.Part("found by ", nl.libreria.core.gui.Caption.SUB),
								new nl.libreria.core.gui.Caption.Part("Anna", nl.libreria.core.gui.Caption.MAIN))));
				ctx.waitTicks(36);
				ctx.takeScreenshot("place_title_found_by");
				// Then into the journal (one of them someone else found first)
				server.runOnServer(s -> {
					nl.libreria.info.Places.remember(player, "screens|village", "Oakhaven", "Village", player.blockPosition().offset(120, 0, -40));
					nl.libreria.info.Places.remember(player, "screens|temple", "Sunhollow", "Desert Temple", player.blockPosition().offset(-900, 0, 300));
					nl.libreria.info.Places.debugFirst("screens|village", java.util.UUID.nameUUIDFromBytes("libreria-anna".getBytes()), "Anna");
					nl.libreria.info.Places.debugFirst("screens|temple", player.getUUID(), player.getName().getString());
				});
				// A villager with its own name, three blocks ahead
				run(server, "execute as @a at @s run summon minecraft:villager ^ ^ ^3");
				ctx.waitTicks(40);
				ctx.takeScreenshot("villager_name");
				run(server, "kill @e[type=minecraft:villager]");
				ctx.waitTicks(3);
			});

			// Windows (dialogs): settings, effects, admin, links, getting started
			// The admin window is for operators (the /op command only exists on dedicated servers)
			server.runOnServer(s -> s.getPlayerList().op(player.nameAndId()));
			ctx.waitTicks(5);
			menu(ctx, server, player, "window_settings", SettingsWindow::open);
			for (nl.libreria.core.Domain d : nl.libreria.core.Domain.values()) {
				menu(ctx, server, player, "window_settings_" + d.word, p -> SettingsWindow.open(p, d, SettingsWindow::open));
			}
			menu(ctx, server, player, "window_settings_section",
					p -> SettingsWindow.open(p, SettingsWindow.sections(nl.libreria.core.Domain.PLACES).getFirst(), SettingsWindow::open));
			menu(ctx, server, player, "window_effects",
					p -> nl.libreria.core.Services.menu(nl.libreria.core.Services.MENU_EFFECTS).ifPresent(open -> open.accept(p)));
			menu(ctx, server, player, "window_admin", nl.libreria.core.gui.AdminWindow::open);
			for (Module m : List.of(nl.libreria.core.Libreria.MODULE, nl.libreria.claims.ClaimsMod.MODULE)) {
				menu(ctx, server, player, "window_admin_" + m.id, p -> nl.libreria.core.gui.AdminWindow.open(p, m));
			}
			server.runOnServer(s -> {
				nl.libreria.core.Links.KINDS.get(0).setting().set("https://discord.gg/example");
				nl.libreria.core.Links.KINDS.get(2).setting().set("https://example.org/rules");
			});
			menu(ctx, server, player, "window_links", nl.libreria.core.Links::open);
			menu(ctx, server, player, "window_start", nl.libreria.core.Welcome::guide);
			// Libreria's own start window (drawn by the pack), then with the mouse on the Items card
			menu(ctx, server, player, "own_start", nl.libreria.core.StartWindow::open);
			for (var d : nl.libreria.core.Domain.values()) {
				menu(ctx, server, player, "own_domain_" + d.word, p -> nl.libreria.core.DomainWindow.open(p, d));
			}
			menu(ctx, server, player, "own_settings", nl.libreria.core.gui.SettingsCanvas::open);
			menu(ctx, server, player, "own_places", p -> nl.libreria.core.gui.PlacesWindow.open(p, null, 0));
			menu(ctx, server, player, "own_places_death", p -> nl.libreria.core.gui.PlacesWindow.open(p, "death", 0));
			// The journal, with the mouse on the first two places (who found them first)
			safe("own_journal_hover", () -> {
				server.runOnServer(s -> nl.libreria.core.gui.PlacesWindow.open(player, "journal", 0));
				ctx.waitTicks(12);
				noToasts(ctx);
				double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
				int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
				int h = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
				int left = (w - 176) / 2, top = (h - 222) / 2;
				for (int row = 1; row <= 2; row++) {
					ctx.getInput().setCursorPos((left + 7 + 18 * 3) * scale, (top + 17 + 18 * row + 8) * scale);
					ctx.waitTicks(5);
					ctx.takeScreenshot("own_journal_hover_" + row);
				}
				ctx.getInput().setCursorPos(2, 2);
				close(ctx, server, player);
			});
			menu(ctx, server, player, "own_chests", p -> nl.libreria.chests.gui.ChestsWindow.open(p, () -> {
			}));
			// Profiles, the players, and a server goal with a few people on it
			java.util.UUID anna = java.util.UUID.nameUUIDFromBytes("libreria-anna".getBytes()), bram = java.util.UUID.nameUUIDFromBytes(
					"libreria-bram".getBytes()), sanne = java.util.UUID.nameUUIDFromBytes("libreria-sanne".getBytes());
			server.runOnServer(s -> {
				long now = System.currentTimeMillis(), day = 24L * 3600 * 1000;
				nl.libreria.core.Players.note(anna, "Anna", now - 40 * day, now - 2 * 3600 * 1000L);
				nl.libreria.core.Players.note(bram, "Bram", now - 30 * day, now - 3 * day);
				nl.libreria.core.Players.note(sanne, "Sanne", now - 20 * day, now - 20 * 3600 * 1000L);
				var r = nl.libreria.collector.Goals.startNow(s, "mine.stone");
				if (r != null) {
					// About a quarter of the way
					r.done.put(anna, r.target * 10 / 100);
					r.done.put(player.getUUID(), r.target * 8 / 100);
					r.done.put(bram, r.target * 4 / 100);
					r.done.put(sanne, r.target * 2 / 100);
				}
				nl.libreria.core.Guide.clear(player);
				// Records, and a few achievements for the showcase (one of them a secret)
				String me = player.getName().getString();
				nl.libreria.collector.Records.debugSet(nl.libreria.collector.Records.Kind.FALL, player.getUUID(), me, 87, null, 0);
				nl.libreria.collector.Records.debugSet(nl.libreria.collector.Records.Kind.FLIGHT, anna, "Anna", 14200, "Bram", 9800);
				nl.libreria.collector.Records.debugSet(nl.libreria.collector.Records.Kind.SPEED, bram, "Bram", 68, null, 0);
				nl.libreria.collector.Records.debugSet(nl.libreria.collector.Records.Kind.DISTANCE, player.getUUID(), me, 31400, "Sanne", 12000);
				nl.libreria.collector.Records.debugSet(nl.libreria.collector.Records.Kind.ALIVE, sanne, "Sanne", 41, null, 0);
				nl.libreria.collector.Records.debugSet(nl.libreria.collector.Records.Kind.SESSION, anna, "Anna", 6.5, null, 0);
				nl.libreria.collector.Records.debugSet(nl.libreria.collector.Records.Kind.LEVELS, player.getUUID(), me, 112, null, 0);
				nl.libreria.collector.Records.debugSet(nl.libreria.collector.Records.Kind.DEPTH, bram, "Bram", -64, null, 0);
				var cd = nl.libreria.collector.CollectorMod.STORE.get(player);
				for (String id : new String[] {"items.drakenei", "geheim.cactus", "baas.draak", "tijd.1"}) {
					if (nl.libreria.collector.Catalog.get(id) != null) cd.earned.putIfAbsent(id, now);
				}
				cd.secrets.add("cactus");
				cd.recordsHeld.add("fall");
			});
			menu(ctx, server, player, "own_domain_me_new", p -> nl.libreria.core.DomainWindow.open(p, nl.libreria.core.Domain.YOU));
			menu(ctx, server, player, "own_domain_progress_goal", p -> nl.libreria.core.DomainWindow.open(p, nl.libreria.core.Domain.PROGRESS));
			menu(ctx, server, player, "own_profile", p -> nl.libreria.core.ProfileWindow.open(p, p.getUUID(), "", q -> {
			}));
			menu(ctx, server, player, "own_profile_other", p -> nl.libreria.core.ProfileWindow.open(p, anna, "Players",
					nl.libreria.core.PlayersWindow::open));
			menu(ctx, server, player, "own_players", nl.libreria.core.PlayersWindow::open);
			menu(ctx, server, player, "own_showcase", p -> nl.libreria.collector.gui.ShowcaseWindow.open(p, 1, q -> {
			}));
			menu(ctx, server, player, "own_records", nl.libreria.collector.gui.RecordsWindow::open);
			// A trophy: Range (31 km) is a feat and it's ours. Its emblem in Records, the tooltip, and on the name tag
			safe("trophy", () -> {
				server.runOnServer(s -> nl.libreria.collector.gui.RecordsWindow.open(player));
				ctx.waitTicks(10);
				double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
				int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
				int h = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
				ctx.getInput().setCursorPos(((w - 176) / 2 + 16) * scale, ((h - 222) / 2 + 18 + 18 * 3 + 8) * scale);
				ctx.waitTicks(4);
				ctx.takeScreenshot("trophy_records_hover");
				ctx.getInput().pressKey(o2 -> o2.keyAttack);
				ctx.waitTicks(6);
				ctx.takeScreenshot("trophy_worn_card");
				close(ctx, server, player);
				ctx.getInput().lookAt(0, 0);
				camera(ctx, CameraType.THIRD_PERSON_FRONT);
				ctx.waitTicks(30);
				ctx.takeScreenshot("trophy_nameplate");
				camera(ctx, CameraType.FIRST_PERSON);
			});
			// Picking a grade: four of the mining ladder earned, wearing the second
			safe("grades", () -> {
				server.runOnServer(s -> {
					var cd = nl.libreria.collector.CollectorMod.STORE.get(player);
					long at = System.currentTimeMillis();
					for (String id : new String[] {"mijn.1000", "mijn.10000", "mijn.50000", "mijn.200000"}) cd.earned.putIfAbsent(id, at);
					nl.libreria.collector.CollectorMod.setTitle(player, "mijn.10000");
					var first = nl.libreria.collector.Catalog.get("mijn.1000");
					if (first != null) nl.libreria.collector.gui.GradesWindow.open(player, nl.libreria.collector.Catalog.ladder(first), "Mining", q -> {
					});
				});
				ctx.waitTicks(12);
				double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
				int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
				int h = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
				ctx.getInput().setCursorPos(((w - 176) / 2 + 100) * scale, ((h - 222) / 2 + 18 + 18 * 3 + 8) * scale);
				ctx.waitTicks(4);
				ctx.takeScreenshot("grades_window");
				close(ctx, server, player);
			});
			// Looking at someone up close: where the card about them is now
			safe("look_close", () -> {
				run(server, f("tp @a %d %d %d 0 0", x, y, z), "kill @e[type=minecraft:villager]",
						f("summon minecraft:villager %d.5 %d %d.6 {NoAI:1b,Rotation:[180f,0f],CustomName:\"Ada\"}", x, y, z + 1));
				ctx.waitTicks(10);
				ctx.getInput().lookAt(new BlockPos(x, y + 1, z + 1));
				ctx.waitTicks(20);
				ctx.takeScreenshot("look_close");
				// and from a few steps back, where the card fits
				run(server, f("tp @a %d %d %d 0 0", x, y, z - 3));
				ctx.waitTicks(6);
				ctx.getInput().lookAt(new BlockPos(x, y + 1, z + 1));
				ctx.waitTicks(20);
				ctx.takeScreenshot("look_far");
				run(server, "kill @e[type=minecraft:villager]", f("tp @a %d %d %d 0 0", x, y, z));
			});
			menu(ctx, server, player, "own_secrets", p -> nl.libreria.collector.gui.AchievementsGui.openCategory(p,
					nl.libreria.collector.Achievement.Category.GEHEIM));
			menu(ctx, server, player, "own_goal", nl.libreria.collector.gui.GoalWindow::open);
			safe("goal_bar", () -> {
				server.runOnServer(s -> nl.libreria.core.Hud.show(player, "goal", net.minecraft.network.chat.Component.empty()
						.append(nl.libreria.core.Ui.text("Stone Age  ", net.minecraft.ChatFormatting.GOLD))
						.append(nl.libreria.core.Ui.text("11,890 / 12,500", net.minecraft.ChatFormatting.WHITE))
						.append(nl.libreria.core.Ui.text("  · you 3,960", net.minecraft.ChatFormatting.GRAY)), 1, 60));
				ctx.waitTicks(6);
				ctx.takeScreenshot("goal_bar");
			});
			safe("away_card", () -> {
				server.runOnServer(s -> {
					// Nothing else waiting at the top, so the card shows right away
					nl.libreria.core.gui.TopCard.forget(player.getUUID());
					boolean cards = nl.libreria.collector.Goals.CARDS.get(player);
					nl.libreria.collector.Goals.CARDS.set(player, false);
					nl.libreria.core.Chronicle.write(anna, "Anna", nl.libreria.core.Chronicle.Kind.BOSS, "slew the Wither", true);
					nl.libreria.core.Chronicle.write(bram, "Bram", nl.libreria.core.Chronicle.Kind.FIRST, "was the first to reach the End", true);
					nl.libreria.collector.Goals.CARDS.set(player, cards);
					nl.libreria.core.Chronicle.welcomeBack(player, System.currentTimeMillis() - 3L * 24 * 3600 * 1000);
				});
				ctx.waitTicks(30);
				ctx.takeScreenshot("away_card");
				server.runOnServer(s -> nl.libreria.core.gui.TopCard.hide(player, "away"));
			});
			server.runOnServer(s -> nl.libreria.collector.Goals.endNow(s, true));
			menu(ctx, server, player, "own_goal_past", nl.libreria.collector.gui.GoalWindow::openPast);
			for (var d : nl.libreria.core.Domain.values()) {
				menu(ctx, server, player, "own_settings_" + d.word, p -> nl.libreria.core.gui.SettingsCanvas.open(p, d, q -> {
				}));
			}
			safe("own_start_hover", () -> {
				server.runOnServer(s -> nl.libreria.core.StartWindow.open(player));
				ctx.waitTicks(12);
				noToasts(ctx);
				double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
				int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
				int h = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
				int left = (w - 176) / 2, top = (h - 222) / 2;
				ctx.getInput().setCursorPos((left + 30) * scale, (top + 40) * scale);
				ctx.waitTicks(5);
				ctx.takeScreenshot("own_start_hover");
				ctx.getInput().setCursorPos(2, 2);
				close(ctx, server, player);
			});
			menu(ctx, server, player, "window_commands", nl.libreria.core.Welcome::commands);
			menu(ctx, server, player, "window_tips", nl.libreria.core.Tips::all);
			safe("tip", () -> {
				server.runOnServer(s -> nl.libreria.core.Tips.show(player, nl.libreria.core.Tips.all(nl.libreria.core.Domain.ITEMS).getFirst()));
				ctx.waitTicks(8);
				ctx.takeScreenshot("tip");
				server.runOnServer(s -> nl.libreria.core.gui.TopCard.hide(player, "tip"));
			});
			menu(ctx, server, player, "window_journal", nl.libreria.info.Places::journal);
			// A sword that just earned its name: the toast, then its tooltip in the inventory
			safe("weapon", () -> {
				server.runOnServer(s -> nl.libreria.info.InfoMod.debugLegend(player));
				ctx.waitTicks(10);
				ctx.takeScreenshot("weapon_named_toast");
				ctx.waitTicks(60);
				ctx.getInput().pressKey(o2 -> o2.keyInventory);
				ctx.waitTicks(10);
				double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
				int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
				int h = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
				int left = (w - 176) / 2, top = (h - 166) / 2;
				ctx.getInput().setCursorPos((left + 16) * scale, (top + 150) * scale);
				ctx.waitTicks(5);
				ctx.takeScreenshot("weapon_tooltip");
				ctx.getInput().setCursorPos(2, 2);
				close(ctx, server, player);
			});
			// A wolf, tamed: it gets its name, then the Pets window and the chronicle
			safe("pet", () -> {
				run(server, "execute as @a at @s run summon minecraft:wolf ^ ^ ^3");
				ctx.waitTicks(5);
				server.runOnServer(s -> {
					for (var t : player.level().getEntitiesOfClass(net.minecraft.world.entity.TamableAnimal.class,
							player.getBoundingBox().inflate(8))) {
						t.tame(player);
						t.setOrderedToSit(true);
					}
				});
				ctx.waitTicks(30);
				ctx.takeScreenshot("pet_tamed");
			});
			menu(ctx, server, player, "window_pets", nl.libreria.core.Services.menu(nl.libreria.core.Services.MENU_PETS).orElse(p -> {
			}));
			menu(ctx, server, player, "window_chronicle", nl.libreria.core.Chronicle::open);
			// The mining achievements, one icon per ladder, with the grades in the tooltip
			safe("achievements_mining", () -> {
				server.runOnServer(s -> nl.libreria.collector.gui.AchievementsGui.openCategory(player,
						nl.libreria.collector.Achievement.Category.MIJNBOUW));
				ctx.waitTicks(12);
				noToasts(ctx);
				double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
				int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
				int h = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
				int left = (w - 176) / 2, top = (h - 222) / 2;
				ctx.getInput().setCursorPos((left + 16) * scale, (top + 26) * scale);
				ctx.waitTicks(5);
				ctx.takeScreenshot("achievements_mining");
				ctx.getInput().setCursorPos(2, 2);
				close(ctx, server, player);
			});
			safe("welcome_card", () -> {
				server.runOnServer(s -> nl.libreria.core.Welcome.card(player));
				ctx.waitTicks(10);
				ctx.takeScreenshot("welcome_card");
				server.runOnServer(s -> nl.libreria.core.gui.TopCard.hide(player, "welcome"));
			});
			menu(ctx, server, player, "window_death_recap", p -> {
				nl.libreria.core.DeathRecap.onDeath(p);
				nl.libreria.core.DeathRecap.open(p);
			});
			safe("leaderboard_and_tab", () -> {
				server.runOnServer(s -> nl.libreria.info.Leaderboards.place(player, nl.libreria.info.Leaderboards.Kind.PLAYTIME));
				ctx.waitTicks(30);
				// (The tab list itself doesn't show in single player with one player, so no picture of it here)
				ctx.takeScreenshot("leaderboard");
			});
			safe("toast", () -> {
				server.runOnServer(s -> nl.libreria.core.gui.Toast.show(player, net.minecraft.world.item.Items.GOLDEN_SHOVEL,
						nl.libreria.core.gui.Toast.GOOD, "Chunk claimed", "12 of 40 chunks · /claim release to undo"));
				ctx.waitTicks(8);
				ctx.takeScreenshot("toast");
				server.runOnServer(s -> nl.libreria.core.gui.Toast.hide(player));
			});

			// Sleeping: a nap in the day (the first click, the time running, the wait after), then a night that runs
			safe("sleep", () -> {
				BlockPos foot = new BlockPos(x + 1, y, z - 2), head = new BlockPos(x + 1, y, z - 3);
				run(server, "difficulty peaceful", "gamerule advance_time true", "weather clear", "time set 4000",
						f("setblock %d %d %d minecraft:red_bed[facing=north,part=head]", head.getX(), head.getY(), head.getZ()),
						f("setblock %d %d %d minecraft:red_bed[facing=north,part=foot]", foot.getX(), foot.getY(), foot.getZ()),
						f("tp @a %d %d %d 180 20", x, y, z));
				ctx.waitTicks(10);
				server.runOnServer(s -> {
					nl.libreria.core.gui.TopCard.forget(player.getUUID());
					nl.libreria.comfort.ComfortMod.debugBed(player, foot);
				});
				ctx.waitTicks(20);
				LOG.info("SLEEP-DEBUG first click at {}", (Object) server.computeOnServer(s -> nl.libreria.comfort.ComfortMod.debugClock(player)));
				ctx.takeScreenshot("nap_first_click");
				server.runOnServer(s -> nl.libreria.comfort.ComfortMod.debugBed(player, foot));
				ctx.waitTicks(60);
				LOG.info("SLEEP-DEBUG napping {}", (Object) server.computeOnServer(s -> player.isSleeping() + " at " + nl.libreria.comfort.ComfortMod.debugClock(player)));
				ctx.takeScreenshot("nap_running");
				ctx.waitTicks(80);
				LOG.info("SLEEP-DEBUG napping {}", (Object) server.computeOnServer(s -> player.isSleeping() + " at " + nl.libreria.comfort.ComfortMod.debugClock(player)));
				ctx.takeScreenshot("nap_running_2");
				ctx.waitTicks(140);
				LOG.info("SLEEP-DEBUG after the nap: sleeping {}", (Object) server.computeOnServer(s -> player.isSleeping() + " at " + nl.libreria.comfort.ComfortMod.debugClock(player)));
				ctx.takeScreenshot("nap_done");
				server.runOnServer(s -> nl.libreria.comfort.ComfortMod.debugBed(player, foot));
				ctx.waitTicks(20);
				ctx.takeScreenshot("nap_wait");
				// The night
				run(server, "time set 14000");
				ctx.waitTicks(10);
				LOG.info("SLEEP-DEBUG lie down: {}", (Object) server.computeOnServer(s -> nl.libreria.comfort.ComfortMod.debugSleep(player, head)));
				ctx.waitTicks(70);
				LOG.info("SLEEP-DEBUG night {}", (Object) server.computeOnServer(s -> player.isSleeping() + " at " + nl.libreria.comfort.ComfortMod.debugClock(player)));
				ctx.takeScreenshot("sleep_running");
				ctx.waitTicks(120);
				ctx.takeScreenshot("sleep_running_2");
				ctx.waitTicks(200);
				LOG.info("SLEEP-DEBUG morning: sleeping {}", (Object) server.computeOnServer(s -> player.isSleeping() + " at " + nl.libreria.comfort.ComfortMod.debugClock(player)));
				ctx.takeScreenshot("sleep_morning");
				run(server, "difficulty normal", "time set noon",
						f("setblock %d %d %d minecraft:air", head.getX(), head.getY(), head.getZ()),
						f("setblock %d %d %d minecraft:air", foot.getX(), foot.getY(), foot.getZ()));
			});

			// Every menu
			ctx.waitTicks(5);
			close(ctx, server, player);
			menu(ctx, server, player, "window_admin_root", nl.libreria.core.gui.AdminWindow::open);
			for (nl.libreria.core.Domain d : nl.libreria.core.Domain.values()) {
				menu(ctx, server, player, "start_" + d.word, p -> nl.libreria.core.Start.domain(p, d));
			}
			menu(ctx, server, player, "places_land", p -> nl.libreria.core.PlaceList.open(p, "land"));
			for (Module.HubEntry e : Module.allHubEntries()) {
				// The land map isn't a menu (and would stay open behind the others)
				if (e.name().equals("Land map")) continue;
				menu(ctx, server, player, "menu_" + e.module().id + "_" + e.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_"),
						e.action());
			}
		}
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

	/** Set by {@link NetworkTest}: after every step, check the client is still connected. */
	static ClientGameTestContext network;
	static String lostAfter;

	private static void safe(String what, Runnable r) {
		if (lostAfter != null) return;
		try {
			r.run();
		} catch (Throwable t) {
			LOG.error("Screenshot step '{}' failed", what, t);
		}
		if (network != null) NetworkTest.check(network, what);
	}

	private static void look(ClientGameTestContext ctx, String name, BlockPos at) {
		safe(name, () -> {
			ctx.getInput().lookAt(at);
			ctx.waitTicks(16);
			ctx.takeScreenshot(name);
		});
	}

	/** Advancement toasts would cover the right side of the screenshots. */
	private static void noToasts(ClientGameTestContext ctx) {
		ctx.runOnClient(c -> {
			// Found by type, so a renamed getter doesn't matter
			try {
				for (Object owner : new Object[] {c, c.gui}) {
					for (var m : owner.getClass().getMethods()) {
						if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().contains("Toast")) {
							Object tm = m.invoke(owner);
							tm.getClass().getMethod("clear").invoke(tm);
							return;
						}
					}
				}
				LOG.warn("Could not find the toast manager");
			} catch (ReflectiveOperationException | RuntimeException e) {
				LOG.warn("Could not clear toasts: {}", e.toString());
			}
		});
	}

	/** Four pieces of one material (leather … netherite), each with the same enchantments ("thorns:3"). */
	private static void wearMaterial(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, String mat, String ench) {
		String[] slots = {"head", "chest", "legs", "feet"};
		String[] items = {"helmet", "chestplate", "leggings", "boots"};
		for (int i = 0; i < 4; i++)
			run(server, "item replace entity @a armor." + slots[i] + " with minecraft:" + mat + "_" + items[i] + "[enchantments={" + ench + "}]");
	}

	/** Diamond armour with a trim on every piece; with asset, our layers (a seam of light round the trim) on top of diamond. */
	private static void wearTrim(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, String pattern, String material,
			String asset) {
		String[] slots = {"head", "chest", "legs", "feet"};
		String[] items = {"helmet", "chestplate", "leggings", "boots"};
		for (int i = 0; i < 4; i++) {
			String eq = asset == null ? "" : ",equippable={slot:\"" + slots[i] + "\",asset_id:\"" + asset + "\"}";
			run(server, "item replace entity @a armor." + slots[i] + " with minecraft:diamond_" + items[i] + "[trim={pattern:\"minecraft:" + pattern
					+ "\",material:\"minecraft:" + material + "\"}" + eq + "]");
		}
	}

	/** A diamond sword with one of the effect models, without the enchantment glint (the effect takes its place). */
	private static String sword(String id) {
		return "minecraft:diamond_sword[item_model=\"libreria:fx/sword_" + id + "\",enchantment_glint_override=false]";
	}

	/** Four diamond pieces with the same protection at a level; extras: thorns (chest), feather/frost/soul (boots). */
	private static void wearSet(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, String prot, int lvl, String extras) {
		String chest = extras.contains("thorns") ? "," + "thorns:3" : "";
		String boots = (extras.contains("feather") ? ",feather_falling:4" : "") + (extras.contains("frost") ? ",frost_walker:2" : "")
				+ (extras.contains("soul") ? ",soul_speed:3" : "");
		run(server, "item replace entity @a armor.head with minecraft:diamond_helmet[enchantments={" + prot + ":" + lvl + "}]",
				"item replace entity @a armor.chest with minecraft:diamond_chestplate[enchantments={" + prot + ":" + lvl + chest + "}]",
				"item replace entity @a armor.legs with minecraft:diamond_leggings[enchantments={" + prot + ":" + lvl + "}]",
				"item replace entity @a armor.feet with minecraft:diamond_boots[enchantments={" + prot + ":" + lvl + boots + "}]");
	}

	private static void camera(ClientGameTestContext ctx, CameraType type) {
		ctx.runOnClient(c -> c.options.setCameraType(type));
		ctx.waitTick();
	}

	/** Presses, holds or releases a key (TestInput.pressKey/holdKey/releaseKey with a KeyMapping, looked up by name). */
	private static void key(ClientGameTestContext ctx, String what, net.minecraft.client.KeyMapping mapping) {
		try {
			Object input = ctx.getInput();
			input.getClass().getMethod(what, net.minecraft.client.KeyMapping.class).invoke(input, mapping);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOG.warn("Could not {} {}: {}", what, mapping.getName(), e.toString());
		}
	}

	/** Moves the mouse by this many pixels (TestInput.moveCursor, looked up by name). */
	private static void moveMouse(ClientGameTestContext ctx, double dx, double dy) {
		try {
			Object input = ctx.getInput();
			input.getClass().getMethod("moveCursor", double.class, double.class).invoke(input, dx, dy);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOG.warn("Could not move the mouse: {}", e.toString());
		}
	}

	/** Types into the focused text field (TestInput.typeChars, looked up by name so a renamed API can't break the build). */
	private static void typeText(ClientGameTestContext ctx, String text) {
		try {
			Object input = ctx.getInput();
			input.getClass().getMethod("typeChars", String.class).invoke(input, text);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOG.warn("Could not type {}: {}", text, e.toString());
		}
	}

	private static void close(ClientGameTestContext ctx, TestServerContext server, ServerPlayer player) {
		server.runOnServer(s -> player.closeContainer());
		ctx.waitTicks(3);
		ctx.setScreen(() -> null);
		ctx.waitTicks(3);
	}

	private static void menu(ClientGameTestContext ctx, TestServerContext server, ServerPlayer player, String name, Consumer<ServerPlayer> open) {
		safe(name, () -> {
			server.runOnServer(s -> open.accept(player));
			ctx.waitTicks(12);
			ctx.getInput().setCursorPos(2, 2);
			noToasts(ctx);
			ctx.waitTicks(2);
			ctx.takeScreenshot(name);
			close(ctx, server, player);
		});
	}
}
