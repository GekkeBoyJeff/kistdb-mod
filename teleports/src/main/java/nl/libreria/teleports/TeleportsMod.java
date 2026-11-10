package nl.libreria.teleports;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import nl.libreria.core.Domain;
import nl.libreria.core.Module;
import nl.libreria.core.PlayerStore;
import nl.libreria.core.ServerSetting;
import nl.libreria.core.Setting;
import nl.libreria.core.Tips;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * TeleportsMod – a Libreria module: homes (/sethome, /home, /delhome, a list under Land &amp; places), asking to
 * teleport to a player or asking them over (/tpa, /tpahere, accept with a click), /back to where you were after a
 * teleport or dying, and /spawn. Ops set the rules (how many homes, between worlds, standing still first, the wait
 * between teleports) in the admin window, and give or take homes and access per player.
 */
public final class TeleportsMod implements ModInitializer {
	public static final Module MODULE = Module.register("teleports", "Teleports", Items.ENDER_PEARL,
			"Homes, asking to teleport to someone,", "back to where you were, and spawn.").in(Domain.PLACES, "Teleporting");

	/** Who may ask you: everyone, the people you trust on your land, nobody. */
	public static final Setting.Choice ASKS = MODULE.choice("asks", "Teleport asks from", Items.ENDER_PEARL, 0,
			new int[] {0, 1, 2}, new String[] {"Everyone", "People I trust", "Nobody"},
			"Who may ask to come to you, or ask", "you to come to them. People you trust:", "those you trust on your land.")
			.in(Domain.PLACES, "Teleporting");

	// ---------------------------------------------------------------- house rules (admin window)

	static final ServerSetting.Toggle TPA = MODULE.serverToggle("tpa", "Teleport asks (/tpa)", Items.ENDER_PEARL, true,
			"Players may ask to teleport to someone,", "or ask someone to come to them.");
	static final ServerSetting.Toggle HOMES = MODULE.serverToggle("homes", "Homes (/home)", Ui.item("red_bed"), true,
			"Players may set homes and teleport", "to them.");
	static final ServerSetting.Number HOME_COUNT = MODULE.serverNumber("homes_max", "Homes per player", Ui.item("red_bed"), 3, 0, 30, 1,
			"homes", "How many homes everyone may set.", "Give someone more under Players' homes.");
	static final ServerSetting.Toggle BACK = MODULE.serverToggle("back", "Back (/back)", Items.ENDER_PEARL, true,
			"After a teleport or dying: back to", "where you were.");
	static final ServerSetting.Toggle SPAWN = MODULE.serverToggle("spawn", "Spawn (/spawn)", Items.COMPASS, true,
			"Teleport to the world's spawn point.");
	static final ServerSetting.Toggle WORLDS = MODULE.serverToggle("worlds", "Between worlds", Items.OBSIDIAN, true,
			"Teleports may go to another world", "(the Nether, the End).");
	static final ServerSetting.Number WARMUP = MODULE.serverNumber("warmup", "Stand still first", Items.CLOCK, 0, 0, 10, 1, "seconds",
			"How long you stand still before a", "teleport; moving or getting hurt", "calls it off. 0: at once.");
	static final ServerSetting.Number COOLDOWN = MODULE.serverNumber("cooldown", "Wait between teleports", Items.CLOCK, 0, 0, 300, 5,
			"seconds", "How long after a teleport before", "the next one. 0: no wait.");
	static final ServerSetting.Number EXPIRE = MODULE.serverNumber("ask_seconds", "Asks run out after", Items.CLOCK, 60, 15, 300, 15,
			"seconds", "How long someone has to accept", "a teleport ask.");

	static final Tips.Tip TIP_HOME = Tips.register("home", Domain.PLACES, Ui.item("red_bed"), "Make it home",
			"/sethome here, /home to come back.", "All your homes: Land & places › Homes.");
	static final Tips.Tip TIP_ASK = Tips.register("tpa", Domain.PLACES, Items.ENDER_PEARL, "Teleport asks",
			"Click Accept in chat, or /tpaccept.", "Who may ask you: Settings › Land & places.");

	// ---------------------------------------------------------------- per player

	/** A home: its name and where it is. */
	public static final class Home {
		public String name = "Home";
		public String dim = "minecraft:overworld";
		public double x;
		public double y;
		public double z;
		public float yaw;
		public float pitch;
		public long set;
	}

	/** Where /back goes. */
	public static final class Spot {
		public String dim = "minecraft:overworld";
		public double x;
		public double y;
		public double z;
		public float yaw;
		public float pitch;
		/** Where you died (rather than where you teleported away from). */
		public boolean death;
		public long at;
	}

	public static final class Data {
		public List<Home> homes = new ArrayList<>();
		/** The home /home goes to ("" = the first). */
		public String favourite = "";
		public @Nullable Spot back;
		/** Homes an operator gave on top (or took, below zero). */
		public int extraHomes;
		/** An operator took away teleport asks. */
		public boolean noAsks;
	}

	static final PlayerStore<Data> PLAYERS = PlayerStore.create("libreria/teleports", Data.class, Data::new, d -> {
		if (d.homes == null) d.homes = new ArrayList<>();
		if (d.favourite == null) d.favourite = "";
	});

	@Override
	public void onInitialize() {
		MODULE.hubEntry(Domain.PLACES, 11, Ui.item("red_bed"), "Homes", Homes::open,
				"Your homes: click one to go there.", "/sethome, /home, /delhome.");
		MODULE.hubEntry(Domain.PLACES, 12, Items.ENDER_PEARL, "Teleport to a player", TeleportWindow::open,
				"Ask to go to someone, or ask them over;", "asks for you to accept. /tpa, /tpahere.");
		MODULE.hubEntry(Domain.PLACES, 13, Items.ENDER_EYE, "Back to where you were", p -> Travel.back(p),
				"Where you were before your last teleport,", "or where you died. /back.");
		MODULE.adminButton("Players' homes & access", "Everyone's homes, extra homes, who may ask to teleport", TeleportsAdmin::players);
		MODULE.commands("/home [name] – to your home (your favourite without a name)", "/sethome [name] – a home here",
				"/delhome <name> – delete a home", "/homes – all your homes", "/tpa <player> – ask to go to someone",
				"/tpahere <player> – ask someone to come to you", "/tpaccept, /tpdeny – answer an ask", "/back – back to where you were",
				"/spawn – to the world's spawn");
		Homes.register();

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!MODULE.on()) return;
			Travel.tick(server);
			if (server.getTickCount() % 20 == 3) Asks.tick(server);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (MODULE.on() && entity instanceof ServerPlayer p) Travel.rememberBack(p, Travel.Why.DEATH);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (MODULE.on() && entity instanceof ServerPlayer p && taken > 0) Travel.hurt(p);
		});
		// The first night in a bed: a good moment to learn about homes
		EntitySleepEvents.START_SLEEPING.register((entity, pos) -> {
			if (MODULE.on() && HOMES.get() && entity instanceof ServerPlayer p) Tips.once(p, TIP_HOME);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Travel.forget(handler.player.getUUID());
			Asks.forget(handler.player);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
			var on = MODULE.available();
			dispatcher.register(Commands.literal("home").requires(on)
					.executes(c -> Homes.go(c.getSource().getPlayerOrException(), null) ? 1 : 0)
					.then(Commands.argument("name", StringArgumentType.greedyString()).suggests(TeleportsMod::homeNames)
							.executes(c -> Homes.go(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "name")) ? 1 : 0)));
			dispatcher.register(Commands.literal("homes").requires(on).executes(c -> {
				Homes.open(c.getSource().getPlayerOrException());
				return 1;
			}));
			dispatcher.register(Commands.literal("sethome").requires(on)
					.executes(c -> Homes.set(c.getSource().getPlayerOrException(), null) ? 1 : 0)
					.then(Commands.argument("name", StringArgumentType.greedyString()).suggests(TeleportsMod::homeNames)
							.executes(c -> Homes.set(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "name")) ? 1 : 0)));
			dispatcher.register(Commands.literal("delhome").requires(on)
					.executes(c -> Homes.delete(c.getSource().getPlayerOrException(), null) ? 1 : 0)
					.then(Commands.argument("name", StringArgumentType.greedyString()).suggests(TeleportsMod::homeNames)
							.executes(c -> Homes.delete(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "name")) ? 1 : 0)));
			dispatcher.register(Commands.literal("tpa").requires(on)
					.executes(c -> {
						TeleportWindow.open(c.getSource().getPlayerOrException());
						return 1;
					})
					.then(Commands.argument("player", EntityArgument.player())
							.executes(c -> Asks.ask(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player"), false) ? 1 : 0)));
			dispatcher.register(Commands.literal("tpahere").requires(on)
					.then(Commands.argument("player", EntityArgument.player())
							.executes(c -> Asks.ask(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player"), true) ? 1 : 0)));
			dispatcher.register(Commands.literal("tpaccept").requires(on)
					.executes(c -> Asks.answer(c.getSource().getPlayerOrException(), null, true) ? 1 : 0)
					.then(Commands.argument("player", StringArgumentType.word()).suggests(TeleportsMod::askers)
							.executes(c -> Asks.answer(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "player"), true) ? 1 : 0)));
			dispatcher.register(Commands.literal("tpdeny").requires(on)
					.executes(c -> Asks.answer(c.getSource().getPlayerOrException(), null, false) ? 1 : 0)
					.then(Commands.argument("player", StringArgumentType.word()).suggests(TeleportsMod::askers)
							.executes(c -> Asks.answer(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "player"), false) ? 1 : 0)));
			dispatcher.register(Commands.literal("tpcancel").requires(on)
					.executes(c -> Asks.cancel(c.getSource().getPlayerOrException(), null) ? 1 : 0)
					.then(Commands.argument("player", StringArgumentType.word())
							.executes(c -> Asks.cancel(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "player")) ? 1 : 0)));
			dispatcher.register(Commands.literal("back").requires(on).executes(c -> Travel.back(c.getSource().getPlayerOrException()) ? 1 : 0));
			dispatcher.register(Commands.literal("spawn").requires(on).executes(c -> Travel.spawn(c.getSource().getPlayerOrException()) ? 1 : 0));
		});
		TeleportsTests.register();
	}

	private static CompletableFuture<Suggestions> homeNames(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
		ServerPlayer p = ctx.getSource().getPlayer();
		if (p != null) {
			String typed = b.getRemaining().toLowerCase(java.util.Locale.ROOT);
			for (Home h : PLAYERS.get(p).homes) if (h.name.toLowerCase(java.util.Locale.ROOT).startsWith(typed)) b.suggest(h.name);
		}
		return b.buildFuture();
	}

	private static CompletableFuture<Suggestions> askers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
		ServerPlayer p = ctx.getSource().getPlayer();
		if (p != null) for (String name : Asks.askerNames(p)) b.suggest(name);
		return b.buildFuture();
	}

	/** How many homes this player may have: the same for everyone, and what an operator gave on top. */
	static int maxHomes(java.util.UUID id) {
		return Math.max(0, HOME_COUNT.get() + PLAYERS.get(id).extraHomes);
	}
}
