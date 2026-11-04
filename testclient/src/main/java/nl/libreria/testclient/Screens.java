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
import net.minecraft.server.level.ServerPlayer;
import nl.libreria.claims.ClaimsMod;
import nl.libreria.collector.CollectorMod;
import nl.libreria.core.Module;
import nl.libreria.core.gui.HubGui;
import nl.libreria.core.gui.SettingsGui;
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
		ctx.getInput().resizeWindow(1280, 720);
		ctx.waitTicks(20);
		try (TestSingleplayerContext sp = ctx.worldBuilder()
				.adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
			TestServerContext server = sp.getServer();
			sp.getConnection().waitForChunksRender();
			ServerPlayer player = server.computeOnServer(s -> sp.getConnection().getServerPlayer());
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
				ctx.waitTicks(10);
				ctx.getInput().setCursorPos(2, 2);
				noToasts(ctx);
				ctx.waitTicks(2);
				ctx.takeScreenshot("inventory");
				ctx.setScreen(() -> null);
				ctx.waitTicks(5);
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

			// The chest search window (a dialog) and its results
			safe("search", () -> {
				server.runOnServer(s -> nl.libreria.chests.ChestSearch.open(player));
				ctx.waitTicks(10);
				noToasts(ctx);
				ctx.takeScreenshot("search_dialog");
				ctx.setScreen(() -> null);
				ctx.waitTicks(3);
				server.runOnServer(s -> nl.libreria.chests.ChestSearch.find(player, "q:o"));
				ctx.waitTicks(10);
				noToasts(ctx);
				ctx.takeScreenshot("search_results");
				ctx.setScreen(() -> null);
				ctx.waitTicks(3);
			});

			// The land map: camera flies up, claims are coloured plates
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
					ClaimsMod.openMap(player);
				});
				ctx.waitTicks(12);
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
				ctx.getInput().holdKeyFor(o2 -> o2.keyShift, 4);
				ctx.waitTicks(30);
				ctx.takeScreenshot("claim_view_closed");
			});

			// Every menu
			menu(ctx, server, player, "menu_hub", HubGui::open);
			menu(ctx, server, player, "menu_settings", SettingsGui::open);
			for (Module m : List.of(nl.libreria.core.Libreria.MODULE, CollectorMod.MODULE)) {
				menu(ctx, server, player, "menu_settings_" + m.id, p -> SettingsGui.open(p, m));
			}
			for (Module.HubEntry e : Module.allHubEntries()) {
				menu(ctx, server, player, "menu_" + e.module().id + "_" + e.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_"),
						e.action());
			}
		}
		LOG.info("LIBRERIA-SCREENSHOTS DONE");
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

	private static void safe(String what, Runnable r) {
		try {
			r.run();
		} catch (Throwable t) {
			LOG.error("Screenshot step '{}' failed", what, t);
		}
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

	private static void camera(ClientGameTestContext ctx, CameraType type) {
		ctx.runOnClient(c -> c.options.setCameraType(type));
		ctx.waitTick();
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
