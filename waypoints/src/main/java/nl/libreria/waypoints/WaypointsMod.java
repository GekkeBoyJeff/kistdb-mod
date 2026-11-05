package nl.libreria.waypoints;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Guide;
import nl.libreria.core.Libreria;
import nl.libreria.core.Domain;
import nl.libreria.core.Notify;
import nl.libreria.core.Say;
import nl.libreria.core.PlaceList;
import nl.libreria.core.Module;
import nl.libreria.core.PlayerStore;
import nl.libreria.core.Services;
import nl.libreria.core.Setting;
import nl.libreria.core.Ui;
import nl.libreria.core.WorldStore;
import org.jetbrains.annotations.Nullable;

/**
 * WaypointsMod – een Libreria-module: een straal naar je sterfplek en naar waypoints (zonder teleporteren).
 * Waypoints staan in één gedeelde opslag; de eigenaar kiest wie ze ziet (iedereen, alleen ik, gekozen spelers).
 * De sterfplek blijft privé, per speler.
 */
public final class WaypointsMod implements ModInitializer {
	public static final int MAX_POINTS = 45;
	public static final int MAX_NAME = 32;
	/** Hoeveel regels {@code /waypoint xaero all} hooguit stuurt. */
	static final int XAERO_MAX_LINES = 30;

	public static final Module MODULE = Module.register("waypoints", "Waypoints", Items.COMPASS,
			"Waypoints, also shared ones, and the", "way back to where you died.").in(Domain.PLACES, "Finding your way");
	public static final Setting.Toggle DEATH_BEAM = MODULE.toggle("sterfplek", "Show the way back after dying", true, Items.SKELETON_SKULL,
			"After respawning, a beam shows you", "the way back to where you died.").in(Domain.PLACES, "When you die");
	public static final Setting.Toggle DEATH_MESSAGE = MODULE.toggle("bericht", "Coordinates in chat after dying", true, Items.PAPER,
			"A chat line with where you died", "(click it to be shown the way).").in(Domain.PLACES, "When you die");
	public static final Setting.Toggle XAERO = MODULE.toggle("xaero", "Xaero's Minimap", false, Items.FILLED_MAP,
			"Only useful with the Xaero's Minimap", "client mod. Sends waypoints to it", "via chat, also ones shared with you.");

	static final nl.libreria.core.Tips.Tip TIP_DEATH = nl.libreria.core.Tips.register("death_point", Domain.PLACES, Items.SKELETON_SKULL,
			"The way back", "Follow the beam back to where you died.", "Lost it? Land & places › Death & graves.");

	/** Wie een waypoint ziet. */
	public enum Visibility {
		EVERYONE("Everyone"),
		ONLY_ME("Only me"),
		SELECTED("Selected players");

		public final String label;

		Visibility(String label) {
			this.label = label;
		}
	}

	/** Eén punt (een waypoint in de gedeelde opslag, of iemands sterfplek). */
	public static final class Point {
		public String id = "";
		public @Nullable UUID owner;
		public String ownerName = "";
		public String name = "";
		public String dim = "minecraft:overworld";
		public int x;
		public int y;
		public int z;
		public String icon = "minecraft:compass";
		public long created;
		public Visibility visibility = Visibility.EVERYONE;
		/** Bij {@link Visibility#SELECTED}: wie het mag zien (UUID → naam, zodat we offline spelers kunnen tonen). */
		public Map<UUID, String> selected = new LinkedHashMap<>();

		public BlockPos pos() {
			return new BlockPos(this.x, this.y, this.z);
		}

		public Item item() {
			Identifier id = this.icon == null ? null : Identifier.tryParse(this.icon);
			Item i = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
			return i == null || i == Items.AIR ? Items.COMPASS : i;
		}

		public boolean ownedBy(UUID id) {
			return id.equals(this.owner);
		}
	}

	/** De gedeelde opslag: alle waypoints van alle spelers. */
	public static final class Data {
		public List<Point> points = new ArrayList<>();
	}

	/** Per speler: alleen de (privé) sterfplek, plus oude waypoints die nog naar de gedeelde opslag moeten. */
	public static final class PlayerData {
		/** Waypoints uit de tijd dat ze per speler werden opgeslagen; worden bij het eerste gebruik verhuisd. */
		public @Nullable List<Point> points;
		public @Nullable Point death;
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/waypoints.json", Data.class, Data::new);
	static final PlayerStore<PlayerData> PLAYERS = PlayerStore.create("libreria/waypoints", PlayerData.class, PlayerData::new);

	/** Welke geladen {@link Data} al is nagekeken op lege velden (wordt na een herstart opnieuw geladen). */
	private static @Nullable Data checked;

	private static final int[] XAERO_COLORS = {10, 11, 12, 13, 14, 9, 6, 3, 2, 5};

	@Override
	public void onInitialize() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) onDeath(p);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) onRespawn(newPlayer);
		});
		// Only when you can actually be shown the way (not when you died in another dimension)
		Services.addDeathAction(p -> deathPoint(p) == null || !deathPoint(p).dim.equals(dimOf(p.level())) ? null
				: new Services.DeathAction("Show the way back", "A beam to where you died", WaypointsMod::goDeath));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			// /waypoint (or /wp) opens the list; the sub-commands work on a waypoint by name
			for (String cmd : new String[] {"waypoint", "wp"}) {
				dispatcher.register(Commands.literal(cmd)
						.executes(c -> {
							WaypointPlaces.open(c.getSource().getPlayerOrException());
							return 1;
						})
						.then(named("set", null, WaypointsMod::add))
						.then(named("remove", WaypointsMod::ownNames, WaypointsMod::remove))
						.then(named("go", WaypointsMod::visibleNames, WaypointsMod::go))
						.then(named("private", WaypointsMod::ownNames, (p, n) -> setVisibility(p, n, Visibility.ONLY_ME)))
						.then(named("public", WaypointsMod::ownNames, (p, n) -> setVisibility(p, n, Visibility.EVERYONE)))
						.then(named("xaero", p -> {
							List<String> names = new ArrayList<>(visibleNames(p));
							names.add(0, "all");
							return names;
						}, WaypointsMod::xaero)));
			}
			dispatcher.register(Commands.literal("deathpoint").executes(c -> goDeath(c.getSource().getPlayerOrException()) ? 1 : 0));
		});
		// Wie Xaero aanzet, krijgt meteen uitleg en een knop om alles te sturen
		XAERO.onChange(p -> {
			if (!XAERO.get(p)) return;
			Say.to(p, Domain.PLACES, Ui.text("Xaero's Minimap is on: waypoint lines in chat can be added to your minimap. ",
					ChatFormatting.GRAY).append(Say.run("Send all now", "/waypoint xaero all",
							"Every waypoint you can see (the nearest " + XAERO_MAX_LINES + ")")));
		});
		MODULE.commands("/waypoint – your waypoints (or /wp)",
				"/waypoint set <name> – add one here",
				"/waypoint go <name> – show the way to one",
				"/waypoint private|public <name> – who sees it",
				"/waypoint remove <name> – delete one",
				"/deathpoint – show the way to where you died");
		WaypointPlaces.register();
		MODULE.hubEntry(Domain.PLACES, 50, Items.COMPASS, "Your waypoints", WaypointPlaces::open,
				"Your waypoints and the ones shared with you. Click one to be shown the way.");
		// Graves adds the same tab with its graves in it; without graves, the death point gets the button
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("libreria_graves")) {
			MODULE.hubEntry(Domain.PLACES, 60, Items.SKELETON_SKULL, "Death point", p -> PlaceList.open(p, WaypointPlaces.DEATH),
					"Where you last died; click it to be shown the way back.");
		}
		Services.provideMenu(Services.MENU_WAYPOINTS, WaypointPlaces::open);
		nl.libreria.core.gui.Markers.addSource(WaypointsMod::compassMarkers);
		WaypointsTests.register();
	}

	/** {@code /waypoint <literal> <name>}, met naam-suggesties als {@code names} niet null is. */
	private static LiteralArgumentBuilder<CommandSourceStack> named(String literal, @Nullable Function<ServerPlayer, List<String>> names,
			BiPredicate<ServerPlayer, String> run) {
		RequiredArgumentBuilder<CommandSourceStack, String> arg = Commands.argument("name", StringArgumentType.greedyString())
				.executes(c -> run.test(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "name")) ? 1 : 0);
		if (names != null) {
			arg.suggests((c, b) -> {
				ServerPlayer p = c.getSource().getPlayer();
				return SharedSuggestionProvider.suggest(p == null ? List.<String>of() : names.apply(p), b);
			});
		}
		return Commands.literal(literal).then(arg);
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

	/** Waypoints you can see in this world (yours aqua, others' blue), and your death point (red). */
	static List<nl.libreria.core.gui.Markers.Marker> compassMarkers(ServerPlayer p) {
		List<nl.libreria.core.gui.Markers.Marker> out = new ArrayList<>();
		String dim = dimOf(p.level());
		for (Point pt : visible(p)) {
			if (!pt.dim.equals(dim)) continue;
			Vec3 at = Vec3.atCenterOf(pt.pos());
			if (at.distanceToSqr(p.position()) > 4000.0 * 4000.0) continue;
			out.add(new nl.libreria.core.gui.Markers.Marker(at, "diamond", pt.ownedBy(p.getUUID()) ? 0x55FFFF : 0xA9B6FF, pt.name));
		}
		Point death = PLAYERS.get(p).death;
		if (death != null && death.dim.equals(dim)) {
			out.add(new nl.libreria.core.gui.Markers.Marker(Vec3.atCenterOf(death.pos()), "skull", 0xFF6B6B, "Death point"));
		}
		return out;
	}

	/** Didn't work: a card with why. */
	static void fail(ServerPlayer p, String title, String why) {
		Notify.warn(p, Items.COMPASS, title, why);
	}

	/** Done: a card. */
	static void done(ServerPlayer p, String title, String what) {
		Notify.done(p, Items.COMPASS, title, what);
	}

	static double distance(ServerPlayer p, Point pt) {
		return p.position().distanceTo(Vec3.atCenterOf(pt.pos()));
	}

	private static String newId() {
		return UUID.randomUUID().toString();
	}

	private static void fix(Point pt) {
		if (pt.id == null || pt.id.isEmpty()) pt.id = newId();
		if (pt.ownerName == null) pt.ownerName = "";
		if (pt.name == null) pt.name = "";
		if (pt.dim == null) pt.dim = "minecraft:overworld";
		if (pt.icon == null) pt.icon = "minecraft:compass";
		if (pt.visibility == null) pt.visibility = Visibility.EVERYONE;
		if (pt.selected == null) pt.selected = new LinkedHashMap<>();
	}

	/** Alle waypoints van alle spelers (de gedeelde lijst zelf). */
	static List<Point> points() {
		Data d = STORE.get();
		if (d != checked) {
			if (d.points == null) d.points = new ArrayList<>();
			d.points.removeIf(Objects::isNull);
			for (Point pt : d.points) fix(pt);
			checked = d;
		}
		return d.points;
	}

	static boolean exists(Point pt) {
		return points().contains(pt);
	}

	/** Wie wat ziet: je eigen punten, punten voor iedereen, en gekozen-spelers-punten waar jij bij staat. */
	static boolean canSee(Point pt, UUID viewer) {
		if (pt.ownedBy(viewer)) return true;
		return switch (pt.visibility == null ? Visibility.EVERYONE : pt.visibility) {
			case EVERYONE -> true;
			case ONLY_ME -> false;
			case SELECTED -> pt.selected != null && pt.selected.containsKey(viewer);
		};
	}

	/** Alles wat deze speler ziet (eigen punten en die van anderen). */
	static List<Point> visible(ServerPlayer p) {
		migrate(p);
		UUID id = p.getUUID();
		List<Point> out = new ArrayList<>();
		for (Point pt : points()) if (canSee(pt, id)) out.add(pt);
		return out;
	}

	static List<Point> own(ServerPlayer p) {
		migrate(p);
		return own(p.getUUID());
	}

	static List<Point> own(UUID owner) {
		List<Point> out = new ArrayList<>();
		for (Point pt : points()) if (pt.ownedBy(owner)) out.add(pt);
		return out;
	}

	/** Eerst deze dimensie (dichtstbijzijnde eerst), daarna de rest per dimensie, op naam. */
	static List<Point> sorted(ServerPlayer p, List<Point> list) {
		String dim = dimOf(p.level());
		Vec3 pos = p.position();
		List<Point> out = new ArrayList<>(list);
		out.sort((a, b) -> {
			boolean ha = a.dim.equals(dim);
			boolean hb = b.dim.equals(dim);
			if (ha != hb) return ha ? -1 : 1;
			int c = ha ? Double.compare(pos.distanceTo(Vec3.atCenterOf(a.pos())), pos.distanceTo(Vec3.atCenterOf(b.pos())))
					: a.dim.compareTo(b.dim);
			return c != 0 ? c : a.name.compareToIgnoreCase(b.name);
		});
		return out;
	}

	static @Nullable Point findOwn(UUID owner, String name) {
		String n = name.trim();
		for (Point pt : points()) {
			if (pt.ownedBy(owner) && pt.name.equalsIgnoreCase(n)) return pt;
		}
		return null;
	}

	static @Nullable Point findOwn(ServerPlayer p, String name) {
		migrate(p);
		return findOwn(p.getUUID(), name);
	}

	/** Een punt dat je ziet; bij dezelfde naam gaat je eigen punt voor, daarna het dichtstbijzijnde. */
	static @Nullable Point find(ServerPlayer p, String name) {
		Point own = findOwn(p, name);
		if (own != null) return own;
		String n = name.trim();
		for (Point pt : sorted(p, visible(p))) {
			if (pt.name.equalsIgnoreCase(n)) return pt;
		}
		return null;
	}

	private static List<String> ownNames(ServerPlayer p) {
		List<String> out = new ArrayList<>();
		for (Point pt : own(p)) out.add(pt.name);
		return out;
	}

	private static List<String> visibleNames(ServerPlayer p) {
		Set<String> seen = new HashSet<>();
		List<String> out = new ArrayList<>();
		for (Point pt : sorted(p, visible(p))) {
			if (seen.add(pt.name.toLowerCase(Locale.ROOT))) out.add(pt.name);
		}
		return out;
	}

	/** Online spelers die dit punt nu zien. */
	static Set<UUID> viewersOnline(MinecraftServer server, Point pt) {
		Set<UUID> out = new HashSet<>();
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (canSee(pt, other.getUUID())) out.add(other.getUUID());
		}
		return out;
	}

	/** "everyone" / "only you" / "3 players", vanuit wie kijkt. */
	static String visibilityText(Point pt, UUID viewer) {
		boolean own = pt.ownedBy(viewer);
		return switch (pt.visibility) {
			case EVERYONE -> "everyone";
			case ONLY_ME -> own ? "only you" : "only " + pt.ownerName;
			case SELECTED -> {
				int n = pt.selected.size();
				if (own) yield n == 0 ? "only you" : n + (n == 1 ? " player" : " players");
				yield n <= 1 ? "you" : "you + " + (n - 1) + (n == 2 ? " other" : " others");
			}
		};
	}

	static ChatFormatting visibilityColor(Visibility v) {
		return switch (v) {
			case EVERYONE -> ChatFormatting.GREEN;
			case ONLY_ME -> ChatFormatting.GOLD;
			case SELECTED -> ChatFormatting.AQUA;
		};
	}

	/** Naam opschonen en controleren (1–32 tekens); null = afgekeurd (met melding). */
	private static @Nullable String cleanName(ServerPlayer p, @Nullable String name) {
		String n = name == null ? "" : name.replace("§", "").trim();
		if (n.isEmpty() || n.length() > MAX_NAME) {
			fail(p, n.isEmpty() ? "No name" : "Name too long", "Use a name of 1 to " + MAX_NAME + " characters.");
			return null;
		}
		return n;
	}

	private static String uniqueName(UUID owner, String name) {
		if (findOwn(owner, name) == null) return name;
		for (int i = 2; ; i++) {
			String suffix = " (" + i + ")";
			String n = (name.length() + suffix.length() > MAX_NAME ? name.substring(0, MAX_NAME - suffix.length()) : name) + suffix;
			if (findOwn(owner, n) == null) return n;
		}
	}

	// ---------------------------------------------------------------- joining and migration

	private static void onJoin(ServerPlayer p) {
		migrate(p);
		// Namen bijwerken (spelers kunnen hun naam veranderen)
		String name = p.getName().getString();
		UUID id = p.getUUID();
		boolean changed = false;
		for (Point pt : points()) {
			if (pt.ownedBy(id) && !name.equals(pt.ownerName)) {
				pt.ownerName = name;
				changed = true;
			}
			String sel = pt.selected.get(id);
			if (sel != null && !sel.equals(name)) {
				pt.selected.put(id, name);
				changed = true;
			}
		}
		if (changed) STORE.markDirty();
	}

	/** Oude per-speler-waypoints naar de gedeelde opslag (eigenaar = deze speler, zichtbaar voor iedereen). */
	static void migrate(ServerPlayer p) {
		PlayerData pd = PLAYERS.get(p);
		if (pd.points == null) return;
		UUID id = p.getUUID();
		String owner = p.getName().getString();
		List<Point> shared = points();
		int moved = 0;
		for (Point old : pd.points) {
			if (old == null) continue;
			fix(old);
			old.id = newId();
			old.owner = id;
			old.ownerName = owner;
			old.visibility = Visibility.EVERYONE;
			old.selected = new LinkedHashMap<>();
			String n = old.name.replace("§", "").trim();
			if (n.isEmpty()) n = "Waypoint";
			if (n.length() > MAX_NAME) n = n.substring(0, MAX_NAME);
			old.name = uniqueName(id, n);
			shared.add(old);
			moved++;
		}
		pd.points = null;
		// Eerst de gedeelde lijst wegschrijven, pas daarna mag het spelerbestand ze kwijtraken
		if (moved > 0) STORE.saveNow();
		PLAYERS.markDirty(p);
		if (moved > 0) {
			Libreria.LOG.info("Moved {} waypoint(s) of {} to the shared waypoint list", moved, owner);
			Say.to(p, Domain.PLACES, Ui.text("Waypoints can be shared now! Your " + moved
					+ (moved == 1 ? " waypoint is" : " waypoints are") + " visible to everyone. Change that in ", ChatFormatting.GRAY)
					.append(Say.run("Your waypoints", "/waypoint", "Open your waypoints"))
					.append(Ui.text(" (right-click one) or with /waypoint private <name>.", ChatFormatting.GRAY)));
		}
	}

	// ---------------------------------------------------------------- death point (private, per player)

	private static void onDeath(ServerPlayer p) {
		PlayerData d = PLAYERS.get(p);
		Point death = new Point();
		death.name = "Death point";
		death.owner = p.getUUID();
		death.ownerName = p.getName().getString();
		death.visibility = Visibility.ONLY_ME;
		death.dim = dimOf(p.level());
		BlockPos pos = p.blockPosition();
		death.x = pos.getX();
		death.y = Math.max(p.level().getMinY() + 1, pos.getY());
		death.z = pos.getZ();
		death.icon = "minecraft:skeleton_skull";
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
			fail(p, "In another world", point.name + " is in the " + dimName(point.dim) + ".");
			return false;
		}
		Guide.toPoint(p, p.level(), Vec3.atBottomCenterOf(point.pos()), new ItemStack(point.item()), Component.literal(point.name), 0);
		return true;
	}

	// ---------------------------------------------------------------- changing waypoints

	/** {@code /waypoint set}: nieuw punt hier (zichtbaar voor iedereen), of je eigen punt met die naam hierheen verplaatsen. */
	static boolean add(ServerPlayer p, String name) {
		return create(p, name, true);
	}

	/**
	 * Waypoint op de plek van de speler. Bestaat er al een eigen punt met die naam, dan wordt dat verplaatst
	 * ({@code moveExisting}) of geweigerd.
	 */
	static boolean create(ServerPlayer p, String name, boolean moveExisting) {
		String n = cleanName(p, name);
		if (n == null) return false;
		migrate(p);
		UUID id = p.getUUID();
		Point existing = findOwn(id, n);
		if (existing != null && !moveExisting) {
			fail(p, "Name taken", "You already have a waypoint called " + existing.name + ".");
			return false;
		}
		if (existing == null && own(id).size() >= MAX_POINTS) {
			fail(p, "Too many waypoints", "You have " + MAX_POINTS + ". Remove one first.");
			return false;
		}
		Point pt = existing != null ? existing : new Point();
		BlockPos pos = p.blockPosition();
		pt.name = n;
		pt.dim = dimOf(p.level());
		pt.x = pos.getX();
		pt.y = pos.getY();
		pt.z = pos.getZ();
		if (existing == null) {
			pt.id = newId();
			pt.owner = id;
			pt.ownerName = p.getName().getString();
			pt.visibility = Visibility.EVERYONE;
			Item below = p.level().getBlockState(pos.below()).getBlock().asItem();
			pt.icon = below == Items.AIR ? "minecraft:compass" : BuiltInRegistries.ITEM.getKey(below).toString();
			pt.created = System.currentTimeMillis();
			points().add(pt);
		}
		STORE.markDirty();
		MutableComponent m = Say.line(Domain.PLACES).append(Ui.text(existing == null ? "Waypoint set: " : "Waypoint moved: ", ChatFormatting.GRAY))
				.append(Ui.text(n, ChatFormatting.WHITE)).append(Ui.text(" at " + pt.x + " " + pt.y + " " + pt.z, ChatFormatting.GRAY));
		if (existing == null) {
			m.append(Ui.text(" · visible to everyone ", ChatFormatting.GRAY))
					.append(Say.run("Make private", "/waypoint private " + n, "Only you will see this waypoint"));
		}
		p.sendSystemMessage(m);
		if (existing == null) shareWithNewViewers(p.level().getServer(), pt, Set.of());
		return true;
	}

	static boolean remove(ServerPlayer p, String name) {
		Point pt = findOwn(p, name);
		if (pt == null) {
			fail(p, find(p, name) != null ? "Not your waypoint" : "No such waypoint",
					find(p, name) != null ? "You can only remove your own waypoints." : "Check the name in Your waypoints.");
			return false;
		}
		delete(pt);
		done(p, "Waypoint removed", pt.name);
		return true;
	}

	static void delete(Point pt) {
		points().remove(pt);
		STORE.markDirty();
	}

	static boolean go(ServerPlayer p, String name) {
		Point pt = find(p, name);
		if (pt == null) {
			fail(p, "No such waypoint", "Check the name in Your waypoints.");
			return false;
		}
		return beam(p, pt);
	}

	static boolean rename(ServerPlayer p, Point pt, String name) {
		if (!pt.ownedBy(p.getUUID())) return false;
		String n = cleanName(p, name);
		if (n == null) return false;
		Point other = findOwn(p.getUUID(), n);
		if (other != null && other != pt) {
			fail(p, "Name taken", "You already have a waypoint called " + other.name + ".");
			return false;
		}
		String old = pt.name;
		pt.name = n;
		STORE.markDirty();
		if (!old.equals(n)) done(p, "Waypoint renamed", old + " → " + n);
		return true;
	}

	/** Het item in je hand als icoon. */
	static boolean iconFromHand(ServerPlayer p, Point pt) {
		ItemStack held = p.getMainHandItem();
		if (held.isEmpty()) {
			fail(p, "Nothing in your hand", "Hold the item you want as its icon.");
			return false;
		}
		pt.icon = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
		STORE.markDirty();
		return true;
	}

	static void setVisibility(MinecraftServer server, Point pt, Visibility v) {
		if (pt.visibility == v) return;
		Set<UUID> before = viewersOnline(server, pt);
		pt.visibility = v;
		STORE.markDirty();
		shareWithNewViewers(server, pt, before);
	}

	/** {@code /waypoint private|public <name>} (alleen eigen punten). */
	static boolean setVisibility(ServerPlayer p, String name, Visibility v) {
		Point pt = findOwn(p, name);
		if (pt == null) {
			fail(p, find(p, name) != null ? "Not your waypoint" : "No such waypoint",
					find(p, name) != null ? "You can only change your own waypoints." : "Check the name in Your waypoints.");
			return false;
		}
		setVisibility(p.level().getServer(), pt, v);
		done(p, pt.name, v == Visibility.EVERYONE ? "Now visible to everyone." : "Now only visible to you.");
		return true;
	}

	/** Een speler aan- of uitzetten bij "gekozen spelers". */
	static void select(MinecraftServer server, Point pt, UUID id, String name, boolean on) {
		if (pt.ownedBy(id)) return;
		Set<UUID> before = viewersOnline(server, pt);
		if (on) pt.selected.put(id, name);
		else pt.selected.remove(id);
		STORE.markDirty();
		shareWithNewViewers(server, pt, before);
	}

	// ---------------------------------------------------------------- Xaero's Minimap (optional)

	/**
	 * Een regel die Xaero's Minimap in de chat herkent en als "waypoint toevoegen" aanbiedt:
	 * {@code xaero-waypoint:<naam>:<letter>:<x>:<y>:<z>:<kleur>:false:0:Internal-<dim>-waypoints}.
	 */
	static String xaeroLine(Point pt) {
		String name = pt.name.replace(':', '-').trim();
		if (name.isEmpty()) name = "Waypoint";
		String initial = name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
		int color = XAERO_COLORS[Math.floorMod(pt.name.toLowerCase(Locale.ROOT).hashCode(), XAERO_COLORS.length)];
		return "xaero-waypoint:" + name + ":" + initial + ":" + pt.x + ":" + pt.y + ":" + pt.z + ":" + color
				+ ":false:0:Internal-" + xaeroDim(pt.dim) + "-waypoints";
	}

	/** "minecraft:the_nether" → "the-nether"; eigen dimensies: het pad met {@code _} en {@code /} als {@code -}. */
	static String xaeroDim(String dim) {
		Identifier id = Identifier.tryParse(dim);
		String path = id != null ? id.getPath() : dim;
		return path.replace('_', '-').replace('/', '-').replace(':', '-');
	}

	static void sendXaero(ServerPlayer to, Point pt) {
		to.sendSystemMessage(Component.literal(xaeroLine(pt)));
	}

	/** Spelers die het punt nu wel (en eerst niet) zien en Xaero aan hebben, krijgen de regel automatisch. */
	static void shareWithNewViewers(MinecraftServer server, Point pt, Set<UUID> before) {
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			UUID id = other.getUUID();
			if (before.contains(id) || !canSee(pt, id) || !XAERO.get(other)) continue;
			if (!pt.ownedBy(id)) {
				Say.to(other, Domain.PLACES, pt.ownerName + (pt.visibility == Visibility.EVERYONE ? " added a public waypoint: "
						: " shared a waypoint with you: ") + pt.name, ChatFormatting.GRAY);
			}
			sendXaero(other, pt);
		}
	}

	/** {@code /waypoint xaero <name|all>}. */
	static boolean xaero(ServerPlayer p, String name) {
		if (name.trim().equalsIgnoreCase("all")) return xaeroAll(p) > 0;
		Point pt = find(p, name);
		if (pt == null) {
			fail(p, "No such waypoint", "Check the name in Your waypoints.");
			return false;
		}
		sendXaero(p, pt);
		return true;
	}

	/** Alle punten die je ziet (de dichtstbijzijnde {@value #XAERO_MAX_LINES}). */
	static int xaeroAll(ServerPlayer p) {
		List<Point> list = sorted(p, visible(p));
		if (list.isEmpty()) {
			Notify.info(p, Items.COMPASS, "No waypoints yet", "There are no waypoints you can see.");
			return 0;
		}
		int n = Math.min(XAERO_MAX_LINES, list.size());
		for (int i = 0; i < n; i++) sendXaero(p, list.get(i));
		done(p, "Sent to Xaero's Minimap", n + (n == 1 ? " waypoint" : " waypoints")
				+ (list.size() > n ? " (the nearest " + n + " of " + list.size() + ")" : ""));
		return n;
	}
}
