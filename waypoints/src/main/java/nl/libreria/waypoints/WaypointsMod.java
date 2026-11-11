package nl.libreria.waypoints;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Domain;
import nl.libreria.core.Guide;
import nl.libreria.core.Module;
import nl.libreria.core.Notify;
import nl.libreria.core.PlaceList;
import nl.libreria.core.PlayerStore;
import nl.libreria.core.Say;
import nl.libreria.core.ServerSetting;
import nl.libreria.core.Services;
import nl.libreria.core.Setting;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * WaypointsMod – een Libreria-module: pings (kijk naar een plek, sneak + Q of sneak + middelklik: iedereen in de buurt
 * ziet hem even, met wie en hoe ver) en de weg terug naar waar je stierf. Pings vervangen de oude waypoints.
 */
public final class WaypointsMod implements ModInitializer {
	public static final Module MODULE = Module.register("waypoints", "Pings", Items.SPYGLASS,
			"Ping a spot for everyone near you,", "and the way back to where you died.").in(Domain.PLACES, "Finding your way");
	public static final Setting.Choice PING_KEY = MODULE.choice("ping_key", "Ping with", Items.SPYGLASS, 0,
			new int[] {0, 1, 2, 3}, new String[] {"{sneak} + {drop} or {pick}", "{sneak} + {drop}", "{sneak} + {pick}", "Off"},
			"Look at a spot and ping it: everyone", "near you sees it for 30 seconds.", "{drop} works at any distance, {pick}",
			"(pick block) within reach.").in(Domain.PLACES, "Pinging");
	public static final Setting.Toggle SEE_PINGS = MODULE.toggle("pings_see", "Pings from others", true, Items.ENDER_EYE,
			"See where others ping (a marker on the", "spot, a card, and the way there if", "you're not being shown one already).").in(Domain.PLACES, "Pinging");
	/** How a ping looks on the spot, the same for everybody (see {@link PingMarks}). */
	public static final ServerSetting.Choice PING_LOOK = MODULE.serverChoice("ping_look", "Pings look like", Items.SPYGLASS, 0,
			new String[] {"A pillar of light", "A diamond"}, "How a ping shows on the spot, the same for",
			"everybody: a short pillar of light with a", "gem above it, or a diamond with a line down",
			"and rings on the ground. Either one shows", "through walls, with who, what and how far.");
	public static final Setting.Toggle DEATH_BEAM = MODULE.toggle("sterfplek", "Show the way back after dying", true, Items.SKELETON_SKULL,
			"After respawning, a beam shows you", "the way back to where you died.").in(Domain.PLACES, "When you die");
	public static final Setting.Toggle DEATH_MESSAGE = MODULE.toggle("bericht", "Coordinates in chat after dying", true, Items.PAPER,
			"A chat line with where you died", "(click it to be shown the way).").in(Domain.PLACES, "When you die");

	static final nl.libreria.core.Tips.Tip TIP_DEATH = nl.libreria.core.Tips.register("death_point", Domain.PLACES, Items.SKELETON_SKULL,
			"The way back", "Follow the beam back to where you died.", "Lost it? Land & places › Death & graves.");
	static final nl.libreria.core.Tips.Tip TIP_PING = nl.libreria.core.Tips.register("ping", Domain.PLACES, Items.SPYGLASS,
			"Ping a spot", "Look at it and press {sneak} + {drop}:", "everyone near you sees where.", "The same again at your ping: it goes.");

	/** Where someone died (private, per player). */
	public static final class Point {
		public String name = "Death point";
		public String dim = "minecraft:overworld";
		public int x;
		public int y;
		public int z;
		public String icon = "minecraft:skeleton_skull";
		public long created;

		public BlockPos pos() {
			return new BlockPos(this.x, this.y, this.z);
		}

		public Item item() {
			Identifier id = this.icon == null ? null : Identifier.tryParse(this.icon);
			Item i = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
			return i == null || i == Items.AIR ? Items.SKELETON_SKULL : i;
		}
	}

	/** Per player: the death point. (Waypoints of earlier versions in these files are left alone.) */
	public static final class PlayerData {
		public @Nullable Point death;
	}

	static final PlayerStore<PlayerData> PLAYERS = PlayerStore.create("libreria/waypoints", PlayerData.class, PlayerData::new);

	@Override
	public void onInitialize() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!WaypointsMod.MODULE.on()) return;
			if (entity instanceof ServerPlayer p) onDeath(p);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!WaypointsMod.MODULE.on()) return;
			if (!alive) onRespawn(newPlayer);
		});
		// Only when you can actually be shown the way (not when you died in another dimension)
		Services.addDeathAction(p -> deathPoint(p) == null || !deathPoint(p).dim.equals(dimOf(p.level())) ? null
				: new Services.DeathAction("Show the way back", "A beam to where you died", WaypointsMod::goDeath));
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			dispatcher.register(Commands.literal("deathpoint").requires(MODULE.available()).executes(c -> goDeath(c.getSource().getPlayerOrException()) ? 1 : 0));
			// /ping: ping where you look; /ping show <player>: the way to their ping; /ping clear: take yours away
			dispatcher.register(Commands.literal("ping").requires(MODULE.available())
					.executes(c -> Pings.ping(c.getSource().getPlayerOrException()) ? 1 : 0)
					.then(Commands.literal("show").then(Commands.argument("player", EntityArgument.player())
							.executes(c -> Pings.show(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player").getUUID()) ? 1 : 0)))
					.then(Commands.literal("clear").executes(c -> {
						Pings.clear(c.getSource().getPlayerOrException().getUUID());
						return 1;
					})));
		});
		MODULE.commands("/ping – ping the spot you look at (or sneak + drop key)",
				"/ping clear – take your ping away",
				"/deathpoint – show the way to where you died");
		Pings.init();
		nl.libreria.core.Pack.addAssets("libreria_waypoints");
		PING_LOOK.onChange(PingMarks::rebuild);
		ServerTickEvents.END_SERVER_TICK.register(Pings::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Pings.clear(handler.player.getUUID());
			PingMarks.forgetViewer(handler.player.getUUID());
		});
		WaypointPlaces.register();
		// Graves adds the same tab with its graves in it; without graves, the death point gets the button
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("libreria_graves")) {
			MODULE.hubEntry(Domain.PLACES, 60, Items.SKELETON_SKULL, "Death point", p -> PlaceList.open(p, WaypointPlaces.DEATH),
					"Where you last died; click it to be shown the way back.");
		}
		nl.libreria.core.gui.Markers.addSource(WaypointsMod::compassMarkers);
		WaypointsTests.register();
	}

	// ---------------------------------------------------------------- helpers

	static String dimName(String dim) {
		return switch (dim) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> dim;
		};
	}

	static String dimOf(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

	/** Pings you see in this world, and your death point (red). */
	static List<nl.libreria.core.gui.Markers.Marker> compassMarkers(ServerPlayer p) {
		List<nl.libreria.core.gui.Markers.Marker> out = new ArrayList<>(Pings.markers(p));
		Point death = PLAYERS.get(p).death;
		if (death != null && death.dim.equals(dimOf(p.level()))) {
			out.add(new nl.libreria.core.gui.Markers.Marker(Vec3.atCenterOf(death.pos()), "skull", 0xFF6B6B, "Death point"));
		}
		return out;
	}

	// ---------------------------------------------------------------- death point (private, per player)

	private static void onDeath(ServerPlayer p) {
		PlayerData d = PLAYERS.get(p);
		Point death = new Point();
		death.dim = dimOf(p.level());
		BlockPos pos = p.blockPosition();
		death.x = pos.getX();
		death.y = Math.max(p.level().getMinY() + 1, pos.getY());
		death.z = pos.getZ();
		death.created = System.currentTimeMillis();
		d.death = death;
		PLAYERS.markDirty(p);
	}

	static @Nullable Point deathPoint(ServerPlayer p) {
		return PLAYERS.get(p).death;
	}

	private static void onRespawn(ServerPlayer p) {
		Point death = deathPoint(p);
		if (death == null) return;
		if (DEATH_MESSAGE.get(p) && !nl.libreria.core.DeathRecap.enabled(p)) {
			MutableComponent msg = Say.line(Domain.PLACES).append(Ui.text("You died at ", ChatFormatting.GRAY))
					.append(Say.coords(death.x, death.y, death.z))
					.append(Ui.text(" (" + dimName(death.dim) + ") ", ChatFormatting.GRAY))
					.append(Say.run("Show the way", "/deathpoint", "A beam back to where you died"));
			// Andere modules (zoals GravesMod) voegen hier hun regel toe
			for (Component note : Services.deathNotes(p)) msg.append("\n").append(note);
			p.sendSystemMessage(msg);
		} else if (!nl.libreria.core.DeathRecap.enabled(p)) {
			for (Component note : Services.deathNotes(p)) p.sendSystemMessage(note);
		}
		if (DEATH_BEAM.get(p) && dimOf(p.level()).equals(death.dim)) {
			p.level().getServer().execute(() -> {
				beam(p, death);
				nl.libreria.core.Tips.once(p, TIP_DEATH);
			});
		}
	}

	static boolean goDeath(ServerPlayer p) {
		Point death = deathPoint(p);
		if (death == null) {
			Notify.info(p, Items.SKELETON_SKULL, "No death point yet", "You haven't died since Libreria was added.");
			return false;
		}
		return beam(p, death);
	}

	/** Straal naar een punt (alleen in dezelfde dimensie). */
	static boolean beam(ServerPlayer p, Point point) {
		if (!dimOf(p.level()).equals(point.dim)) {
			Notify.warn(p, Items.COMPASS, "In another world", point.name + " is in the " + dimName(point.dim) + ".");
			return false;
		}
		Guide.toPoint(p, p.level(), Vec3.atBottomCenterOf(point.pos()), new ItemStack(point.item()), Component.literal(point.name), 0);
		return true;
	}

	/** For the screenshots: someone else (a stand-in player) pings a spot, as if they pressed the key. */
	public static void debugPing(ServerLevel level, String name, Vec3 at, String what) {
		net.fabricmc.fabric.api.entity.FakePlayer fake = net.fabricmc.fabric.api.entity.FakePlayer.get(level,
				new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes(("ping:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
		Pings.forgetCooldown(fake.getUUID());
		Pings.pingAt(fake, level, at, Component.literal(what));
		Guide.clear(fake);
		PingMarks.forgetViewer(fake.getUUID());
	}

	/** For the screenshots: take every ping away. */
	public static void debugClearPings() {
		for (UUID owner : new ArrayList<>(Pings.ACTIVE.keySet())) Pings.clear(owner);
	}

	static @Nullable UUID uuid(@Nullable String s) {
		try {
			return s == null ? null : UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
