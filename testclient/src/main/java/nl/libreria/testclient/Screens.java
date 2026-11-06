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
			menu(ctx, server, player, "own_places_waypoints", p -> nl.libreria.core.gui.PlacesWindow.open(p, "waypoints", 0));
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

			// Every menu (with a waypoint of our own, so Your places has one)
			run(server, "execute as @a at @s run waypoint set Home");
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
