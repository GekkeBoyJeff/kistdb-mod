package nl.libreria.core;

import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import nl.libreria.core.dialog.Dialogs;
import nl.libreria.core.gui.AdminWindow;
import nl.libreria.core.gui.SettingsWindow;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Libreria – de kern. Andere Libreria-mods (ChestsMod, ToolsMod, WaypointsMod, Oogst) bouwen hierop:
 * instellingen per speler (/libreria settings), het Libreria-menu met de knop in het E-scherm, de gedeelde actiebalk
 * en de straal die de weg wijst.
 */
public final class Libreria implements ModInitializer {
	public static final String MOD_ID = "libreria";
	public static final Logger LOG = LoggerFactory.getLogger("Libreria");

	public static final Module MODULE = Module.register("libreria", "General", Items.HOPPER,
			"The start screen, beams, cards and the resource pack.").in(Domain.YOU, "What you see");
	public static final Setting.Toggle HUB_BUTTON = MODULE.toggle("knop", "Buttons beside your inventory", true, Items.HOPPER,
			"Left-click beside your inventory: your", "items into your nearby chests.", "Right-click: search your items.")
			.in(Domain.YOU, "Search & sorting");
	public static final Setting.Toggle OWN_PLATE = MODULE.toggle("eigen_naam", "Your own name tag", true, Items.NAME_TAG,
			"See your own title and nickname above", "your head in third person (F5).").in(Domain.YOU, "Your name");
	public static final Setting.Choice GUIDE_SECONDS = MODULE.choice("straal", "How long a beam stays", Items.SPECTRAL_ARROW, 1,
			new int[]{45, 90, 180, 300}, new String[]{"45 seconds", "90 seconds", "3 minutes", "5 minutes"},
			"When Libreria shows you the way. The", "beam also stops once you arrive.").in(Domain.PLACES, "Finding your way");


	private static @Nullable MinecraftServer server;
	private static boolean placesButton;

	/** The running server, or null between worlds. */
	public static @Nullable MinecraftServer server() {
		return server;
	}

	@Override
	public void onInitialize() {
		Pack.init();
		ItemView.init();
		Links.init();
		Welcome.init();
		Afk.init();
		Tab.init();
		ServerList.init();
		nl.libreria.core.gui.Markers.addSource(p -> {
			var m = Guide.marker(p);
			return m == null ? java.util.List.of() : java.util.List.of(m);
		});
		QuickMenu.init();
		Tips.init();

		Chronicle.init();
		Profile.init();
		Completion.init();
		MODULE.hubEntry(Domain.YOU, 1, Items.PLAYER_HEAD, "Your profile", p -> ProfileWindow.open(p, p.getUUID(), "", q -> Start.domain(q, Domain.YOU)),
				"What others see when they look you up: playtime, achievements and more.");
		// Your title on the right of "Your profile"
		DomainWindow.value("Your profile", p -> Services.progress().map(pr -> pr.title(p))
				.map(t -> t == null ? "" : t.getString().replace("✦", "").trim()).orElse(""));
		MODULE.hubEntry(Domain.YOU, 2, Items.BOOK, "Players", PlayersWindow::open,
				"Everybody who has played here. Click someone: their profile.");
		MODULE.commands(Domain.YOU, "/profile <name> – someone's profile", "/players – everybody who has played here",
				"{sneak} + right-click a player – their profile");
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && hand == net.minecraft.world.InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp
					&& entity instanceof ServerPlayer target && sp.isShiftKeyDown() && sp.getMainHandItem().isEmpty()) {
				ProfileWindow.open(sp, target.getUUID(), "Players", PlayersWindow::open);
				return net.minecraft.world.InteractionResult.SUCCESS;
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
		// Opslag zo vroeg mogelijk klaarzetten, zodat modules ze in hun eigen SERVER_STARTED al kunnen lezen
		ServerLifecycleEvents.SERVER_STARTING.register(s -> {
			server = s;
			// "Your places" only when a module puts places in it (waypoints, graves, pets, journal, land)
			if (PlaceList.any() && !placesButton) {
				placesButton = true;
				MODULE.hubEntry(Domain.PLACES, 10, Items.COMPASS, "Your places", PlaceList::open,
						"Everything you can be shown the way to: graves, pets, the journal, your land.");
				MODULE.commands(Domain.PLACES, "/places – everything you can be shown the way to");
			}
			PlayerStore.initAll(s);
			WorldStore.initAll(s);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				DeathRecap.onDeath(p);
				Nameplate.onDeath(p);
			}
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) DeathRecap.onRespawn(newPlayer, newPlayer.level().getServer().getTickCount());
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
			long before = Players.onJoin(handler.player);
			Chronicle.welcomeBack(handler.player, before);
			Links.send(handler.player);
			Welcome.onJoin(handler.player, s.getTickCount());
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Players.seed(server, Chronicle.seed(server));
			if (SelfTests.enabled()) server.execute(() -> SelfTests.runAll(server));
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Guide.clearAll();
			Nameplate.clearAll();
			nl.libreria.core.gui.TopCard.clearAll();
			PlayerStore.saveDirtyAll();
			WorldStore.saveAll();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			Players.onLeave(handler.player);
			Guide.clear(handler.player);
			Guide.clearFlashes(id);
			Hud.forget(id);
			nl.libreria.core.gui.TopCard.forget(id);
			nl.libreria.core.gui.Caption.forget(id);
			Dialogs.forget(id);
			Welcome.forget(id);
			Tips.forget(id);
			nl.libreria.core.gui.PlacesMenu.forget(id);
			Afk.forget(id);
			DeathRecap.forget(id);
			Nameplate.forget(id);
			PlayerStore.unloadAll(id);
		});
		ServerTickEvents.END_SERVER_TICK.register(Libreria::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			dispatcher.register(Commands.literal("places").executes(c -> {
				PlaceList.open(c.getSource().getPlayerOrException());
				return 1;
			}));
			dispatcher.register(Commands.literal("chronicle").executes(c -> {
				Chronicle.open(c.getSource().getPlayerOrException());
				return 1;
			}));
			dispatcher.register(Commands.literal("players").executes(c -> {
				PlayersWindow.open(c.getSource().getPlayerOrException());
				return 1;
			}));
			dispatcher.register(Commands.literal("profile").executes(c -> {
				ServerPlayer p = c.getSource().getPlayerOrException();
				ProfileWindow.open(p, p.getUUID(), "", q -> Start.domain(q, Domain.YOU));
				return 1;
			}).then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word()).suggests((c, b) -> {
				MinecraftServer s = c.getSource().getServer();
				for (Players.Known k : Players.all(s)) b.suggest(k.name());
				return b.buildFuture();
			}).executes(c -> {
				ServerPlayer p = c.getSource().getPlayerOrException();
				String name = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "player");
				Players.Known k = Players.byName(c.getSource().getServer(), name);
				if (k == null) {
					Notify.warn(p, Items.PLAYER_HEAD, "Nobody called " + name, "Only players who have been here have a profile.");
					return 0;
				}
				ProfileWindow.open(p, k.id(), "Players", PlayersWindow::open);
				return 1;
			})));
			// /l (or /lib, /libreria) opens the start screen; /l land|progress|me a domain page, /l top the top lists
			for (String name : new String[] {"l", "lib", "libreria"}) {
				var root = Commands.literal(name).executes(c -> {
					Start.open(c.getSource().getPlayerOrException());
					return 1;
				});
				for (Domain d : Domain.values()) {
					root.then(Commands.literal(d.word).executes(c -> {
						Start.domain(c.getSource().getPlayerOrException(), d);
						return 1;
					}));
				}
				// What was Items is under You now
				root.then(Commands.literal("items").executes(c -> {
					Start.domain(c.getSource().getPlayerOrException(), Domain.YOU);
					return 1;
				}));
				root.then(Commands.literal("top").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					TopListsWindow.open(p, ProgressWindow::open);
					return 1;
				}));
				dispatcher.register(root.then(Commands.literal("settings").executes(c -> {
					SettingsWindow.open(c.getSource().getPlayerOrException());
					return 1;
				})).then(Commands.literal("admin").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					if (!Ui.isOp(p)) {
						Notify.warn(p, Items.BARRIER, "Operators only", "The server settings are for admins.");
						return 0;
					}
					AdminWindow.open(p);
					return 1;
				})).then(Commands.literal("links").executes(c -> {
					Links.open(c.getSource().getPlayerOrException());
					return 1;
				})).then(Commands.literal("chronicle").executes(c -> {
					Chronicle.open(c.getSource().getPlayerOrException());
					return 1;
				})).then(Commands.literal("help").executes(c -> {
					Welcome.commands(c.getSource().getPlayerOrException());
					return 1;
				})).then(Commands.literal("start").executes(c -> {
					Welcome.guide(c.getSource().getPlayerOrException());
					return 1;
				})));
			}
		});
		CoreTests.register();
		LOG.info("Libreria core loaded");
	}

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		Guide.tick(server, tick);
		if (tick % 10 == 3) Nameplate.tick(server);
		Hud.tick(server, tick);
		nl.libreria.core.gui.TopCard.tick(server, tick);
		Welcome.tick(server, tick);
		if (tick % 20 == 7) Afk.tick(server, tick);
		if (tick % 1200 == 600) Chronicle.tick(server);
		if (tick % 1200 == 300) Players.tick(server);
		if (tick % 10 == 4) Tips.tick(server);
		DeathRecap.tick(server, tick);
		if (tick % 40 == 11) Tab.tick(server);
		nl.libreria.core.gui.Caption.tick(server, tick);
		if (tick % 1200 == 0) {
			PlayerStore.saveDirtyAll();
			WorldStore.saveAll();
		}
	}
}
