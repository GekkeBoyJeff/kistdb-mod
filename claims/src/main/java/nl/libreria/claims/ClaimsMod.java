package nl.libreria.claims;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import nl.libreria.core.Guide;
import nl.libreria.core.Hud;
import nl.libreria.core.Module;
import nl.libreria.core.ServerSetting;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import nl.libreria.core.WorldStore;
import org.jetbrains.annotations.Nullable;

/**
 * ClaimsMod – een Libreria-module om chunks te claimen. Hoeveel je mag claimen groeit met je speeltijd en (met CollectorMod)
 * je prestatiepunten. In een geclaimde chunk kunnen alleen jij en wie je vertrouwt bouwen, kisten openen en dieren
 * aanraken. Deuren, knoppen en hendels mogen bezoekers standaard wel gebruiken (per eigenaar in te stellen).
 * <p>
 * Quick start for players: stand in your base and type {@code /claim}. {@code /claim map} shows the land around you.
 */
public final class ClaimsMod implements ModInitializer {
	public static final Module MODULE = Module.register("claims", "Claims", Items.GOLDEN_SHOVEL,
			"Protect your base: only you and", "your friends can build there.")
			.commands(
					"/claim – claim the chunk you're in",
					"/claim map – map of your land",
					"/claim show – see nearby borders",
					"/claim info – owner and budget",
					"/claim trust <name> – add a friend",
					"/claim release – give a chunk up");

	/** The land map key: sneak + swap hands (F), swap hands alone, or none. */
	public static final nl.libreria.core.Setting.Choice MAP_KEY = MODULE.choice("kaarttoets", "Land map key", Items.FILLED_MAP, 0,
			new int[] {0, 1, 2}, new String[] {"Sneak + swap hands (F)", "Swap hands (F)", "Off"},
			"Opens and closes the land map.", "Swap hands is F unless you changed", "it in Controls.");
	public static final nl.libreria.core.Setting.Toggle MAP_BUTTON = MODULE.toggle("kaartknop", "Land map button", true, Items.MAP,
			"A land map button right of your", "hotbar, with its key (Libreria pack).");

	/** Budget: a start, plus one per so many hours played and per so many achievement points, up to a maximum. */
	static final ServerSetting.Number START = MODULE.serverNumber("budget_start", "Chunks to start with", Items.GRASS_BLOCK,
			4, 0, 64, 1, "chunks", "Every player can claim this many", "chunks right away.");
	static final ServerSetting.Number HOURS = MODULE.serverNumber("budget_hours", "Hours per extra chunk", Items.CLOCK,
			2, 1, 24, 1, "hours", "One more chunk per this many", "hours played.");
	static final ServerSetting.Number POINTS = MODULE.serverNumber("budget_points", "Points per extra chunk", Items.GOLD_INGOT,
			20, 5, 200, 5, "points", "One more chunk per this many", "achievement points.");
	static final ServerSetting.Number LIMIT = MODULE.serverNumber("budget_max", "Most chunks per player", Items.BEACON,
			400, 16, 1024, 8, "chunks", "Nobody gets more than this.");
	static final int TICKS_PER_HOUR = 72000;

	/** /claim show: radius in chunks and how long the borders glow. */
	static final int SHOW_RADIUS = 3;
	static final int SHOW_TICKS = 200;
	/** Border colors: yours, trusted, someone else's, the free chunk you stand in. */
	static final int GREEN = 0x55FF55, CYAN = 0x55FFFF, RED = 0xFF5555, YELLOW = 0xFFFF55;
	/** "Ask them to /claim trust you" at most once per minute. */
	private static final long HINT_MS = 60_000;
	/** The one-time tip comes a few seconds after joining, so it isn't lost among the join messages. */
	private static final int TIP_DELAY = 100;

	public static final class Claim {
		public UUID owner = new UUID(0, 0);
		public String ownerName = "";
		public long since;
	}

	public static final class Owner {
		public Map<UUID, String> trusted = new LinkedHashMap<>();
		/** Bezoekers mogen deuren, luiken, hekken, knoppen, hendels en bellen gebruiken. */
		public boolean doors = true;
	}

	public static final class Data {
		/** "dimensie|chunkX|chunkZ" → claim */
		public Map<String, Claim> claims = new HashMap<>();
		public Map<UUID, Owner> owners = new HashMap<>();
		/** Players who already got the one-time "how to claim" tip. */
		public Set<UUID> tipped = new HashSet<>();
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/claims.json", Data.class, Data::new);
	/** Waar elke speler het laatst was (eigenaar-UUID of null), voor de "je betreedt…"-melding. */
	private static final Map<UUID, String> LAST = new HashMap<>();
	/** When each player last got the "ask them to trust you" hint in chat. */
	private static final Map<UUID, Long> HINTED = new HashMap<>();
	/** Server tick at which a player gets the one-time tip. */
	private static final Map<UUID, Integer> TIP_AT = new HashMap<>();

	@Override
	public void onInitialize() {
		MODULE.hubEntry(Items.FILLED_MAP, "Land map", ChatFormatting.GREEN, ClaimView::open,
				"Fly up and see your land and others'", "from above. Click chunks to claim them.", "",
				"Key: sneak + swap hands (F)", "Or stand in your base: /claim");
		MODULE.hubEntry(Items.NAME_TAG, "Trusted players", ChatFormatting.AQUA, MapGui.TrustGui::open,
				"Who may build in your land, and", "whether visitors may use doors.");

		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
			if (!(player instanceof ServerPlayer sp) || allowed(sp, world, pos, Action.BUILD)) return true;
			deny(sp, pos);
			return false;
		});
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			if (ClaimView.isOpen(sp)) return InteractionResult.FAIL;
			BlockPos pos = hit.getBlockPos();
			BlockState st = world.getBlockState(pos);
			Action action = isSwitch(st) ? Action.SWITCH : Action.BUILD;
			if (!allowed(sp, world, pos, action)) return deny(sp, pos);
			// Plaatsen tegen een blok aan kan net over de grens vallen
			BlockPos next = pos.relative(hit.getDirection());
			if (!sp.getItemInHand(hand).isEmpty() && !allowed(sp, world, next, Action.BUILD)) return deny(sp, next);
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			// Land map open: right-click releases the chunk under the crosshair, nothing else happens
			if (ClaimView.isOpen(sp)) {
				if (hand == net.minecraft.world.InteractionHand.MAIN_HAND) ClaimView.onUse(sp);
				return InteractionResult.FAIL;
			}
			if (!(sp.getItemInHand(hand).getItem() instanceof BucketItem)) return InteractionResult.PASS;
			BlockHitResult hit = world.clip(new ClipContext(sp.getEyePosition(), sp.getEyePosition().add(sp.getViewVector(1f).scale(sp.blockInteractionRange())),
					ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, sp));
			if (hit.getType() == HitResult.Type.BLOCK) {
				if (!allowed(sp, world, hit.getBlockPos(), Action.BUILD)) return deny(sp, hit.getBlockPos());
				BlockPos next = hit.getBlockPos().relative(hit.getDirection());
				if (!allowed(sp, world, next, Action.BUILD)) return deny(sp, next);
			}
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp) || !protectedEntity(entity)) return InteractionResult.PASS;
			return allowed(sp, world, entity.blockPosition(), Action.BUILD) ? InteractionResult.PASS : deny(sp, entity.blockPosition());
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp) || !protectedEntity(entity)) return InteractionResult.PASS;
			// Handelen met dorpelingen mag altijd
			if (entity instanceof AbstractVillager) return InteractionResult.PASS;
			return allowed(sp, world, entity.blockPosition(), Action.BUILD) ? InteractionResult.PASS : deny(sp, entity.blockPosition());
		});

		ServerTickEvents.END_SERVER_TICK.register(ClaimView::tick);
		// The land map key (sneak + swap hands), and its button right of the hotbar
		nl.libreria.core.Hotkeys.register(ClaimView::onKey);
		Hud.setHotbarButton(ClaimView::hotbarButton);
		nl.libreria.core.Services.provideMenu(nl.libreria.core.Services.MENU_LAND_MAP, ClaimsMod::openMap);
		nl.libreria.core.Services.provideLand((level, pos) -> {
			Claim c = claimAt(level, new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
			return c == null ? null : c.owner;
		});
		// Getting hurt closes the land map at once
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer sp && ClaimView.isOpen(sp)) ClaimView.close(sp, true);
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> ClaimView.closeAll());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTickCount();
			if (tick % 10 != 0) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				enterMessage(p);
				Integer at = TIP_AT.get(p.getUUID());
				if (at != null && tick >= at) {
					TIP_AT.remove(p.getUUID());
					tip(p);
				}
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			UUID id = handler.player.getUUID();
			if (!STORE.get().tipped.contains(id)) TIP_AT.put(id, server.getTickCount() + TIP_DELAY);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ClaimView.forget(handler.player);
			UUID id = handler.player.getUUID();
			LAST.remove(id);
			HINTED.remove(id);
			TIP_AT.remove(id);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
				Commands.literal("claim").executes(ctx -> claimHere(ctx.getSource().getPlayerOrException()))
						.then(Commands.literal("here").executes(ctx -> claimHere(ctx.getSource().getPlayerOrException())))
						.then(Commands.literal("map").executes(ctx -> {
							ClaimView.open(ctx.getSource().getPlayerOrException());
							return 1;
						}))
						.then(Commands.literal("release").executes(ctx -> releaseHere(ctx.getSource().getPlayerOrException())))
						.then(Commands.literal("show").executes(ctx -> {
							showNearby(ctx.getSource().getPlayerOrException());
							return 1;
						}))
						.then(Commands.literal("info").executes(ctx -> {
							info(ctx.getSource().getPlayerOrException());
							return 1;
						}))
						.then(Commands.literal("admin").executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							if (!ctx.getSource().getServer().getPlayerList().isOp(p.nameAndId())) {
								msg(p, "Operators only.", ChatFormatting.RED);
								return 0;
							}
							msg(p, Admin.toggle(p) ? "Admin mode on: you can build anywhere." : "Admin mode off.", ChatFormatting.GOLD);
							return 1;
						}))
						.then(Commands.literal("trust").executes(ctx -> {
							MapGui.TrustGui.open(ctx.getSource().getPlayerOrException());
							return 1;
						}).then(Commands.argument("player", StringArgumentType.word()).suggests(ClaimsMod::suggestOnline).executes(ctx ->
								trustCommand(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "player")))))
						.then(Commands.literal("untrust").executes(ctx -> {
							MapGui.TrustGui.open(ctx.getSource().getPlayerOrException());
							return 1;
						}).then(Commands.argument("player", StringArgumentType.word()).suggests(ClaimsMod::suggestTrusted).executes(ctx ->
								untrustCommand(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "player")))))));

		ClaimsTests.register();
	}

	/** For the screenshot test: put a claim of someone else down. */
	public static void debugClaim(ServerLevel level, ChunkPos c, UUID owner, String name) {
		Claim cl = new Claim();
		cl.owner = owner;
		cl.ownerName = name;
		cl.since = System.currentTimeMillis();
		STORE.get().claims.put(key(level, c), cl);
	}

	/** For the screenshot test. */
	public static void openMap(ServerPlayer p) {
		ClaimView.open(p);
	}

	// ---------------------------------------------------------------- opslag

	static String key(Level level, ChunkPos c) {
		return level.dimension().identifier() + "|" + c.x() + "|" + c.z();
	}

	static @Nullable Claim claimAt(Level level, ChunkPos c) {
		return STORE.get().claims.get(key(level, c));
	}

	static Owner owner(UUID id) {
		return STORE.get().owners.computeIfAbsent(id, k -> new Owner());
	}

	static int owned(UUID id) {
		int n = 0;
		for (Claim c : STORE.get().claims.values()) if (c.owner.equals(id)) n++;
		return n;
	}

	/** Can this player build in that claim (owner or trusted)? Only for colors and texts; protection uses {@link #allowed}. */
	static boolean trustedIn(ServerPlayer p, Claim c) {
		return !c.owner.equals(p.getUUID()) && owner(c.owner).trusted.containsKey(p.getUUID());
	}

	// ---------------------------------------------------------------- budget

	static int playTicks(ServerPlayer p) {
		return p.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME);
	}

	static int hours(ServerPlayer p) {
		return playTicks(p) / TICKS_PER_HOUR;
	}

	static int points(ServerPlayer p) {
		return Services.progress().map(pr -> pr.points(p)).orElse(0);
	}

	static int budget(ServerPlayer p) {
		return Math.min(LIMIT.get(), START.get() + hours(p) / HOURS.get() + points(p) / POINTS.get());
	}

	/** "You can claim 6 chunks (3 used)." */
	static String budgetText(ServerPlayer p) {
		int max = budget(p);
		return "You can claim " + max + (max == 1 ? " chunk" : " chunks") + " (" + owned(p.getUUID()) + " used).";
	}

	/** "+1 per 2 hours played" and (with Collector) "+1 per 20 achievement points", one line each. */
	static List<String> growthLines() {
		List<String> out = new ArrayList<>();
		out.add("+1 per " + (HOURS.get() == 1 ? "hour" : HOURS.get() + " hours") + " played");
		if (Services.progress().isPresent()) out.add("+1 per " + POINTS.get() + " achievement points");
		return out;
	}

	/** "+1 per 2 hours played, +1 per 20 achievement points." */
	static String growthSentence() {
		return String.join(", ", growthLines()) + ".";
	}

	/** What earns the next chunk, in short lines: "Next chunk: play 1 h 20 min more", "or earn 7 more achievement points". */
	static List<String> nextChunkLines(ServerPlayer p) {
		if (budget(p) >= LIMIT.get()) return List.of("You've reached the maximum", "of " + LIMIT.get() + " chunks");
		long perChunk = (long) HOURS.get() * TICKS_PER_HOUR;
		long left = perChunk - playTicks(p) % perChunk;
		int minutes = (int) Math.max(1, (left + 1199) / 1200);
		List<String> out = new ArrayList<>();
		out.add("Next chunk: play " + duration(minutes) + " more");
		if (Services.progress().isPresent()) {
			int need = POINTS.get() - points(p) % POINTS.get();
			out.add("or earn " + need + " more achievement point" + (need == 1 ? "" : "s"));
		}
		return out;
	}

	/** "Next chunk: play 1 h 20 min more or earn 7 more achievement points." */
	static String nextChunkSentence(ServerPlayer p) {
		return String.join(" ", nextChunkLines(p)) + ".";
	}

	/** "45 min", "2 h", "1 h 20 min". */
	static String duration(int minutes) {
		int h = minutes / 60, m = minutes % 60;
		if (h == 0) return m + " min";
		return m == 0 ? h + " h" : h + " h " + m + " min";
	}

	// ---------------------------------------------------------------- claimen

	static boolean claim(ServerPlayer p, ServerLevel level, ChunkPos c) {
		Claim existing = claimAt(level, c);
		if (existing != null) {
			if (existing.owner.equals(p.getUUID())) {
				msg(p, "This chunk is already yours.", ChatFormatting.GRAY);
			} else {
				p.sendSystemMessage(Ui.prefix("Claims").append(Ui.text("This chunk belongs to " + existing.ownerName + ". ", ChatFormatting.GOLD))
						.append(Ui.text("Free land has no colour on the land map. ", ChatFormatting.GRAY)).append(mapButton()));
			}
			return false;
		}
		int have = owned(p.getUUID()), max = budget(p);
		if (have >= max) {
			p.sendSystemMessage(Ui.prefix("Claims").append(Ui.text("Your budget is full. ", ChatFormatting.RED))
					.append(Ui.text(budgetText(p) + " " + growthSentence(), ChatFormatting.GRAY))
					.append("\n").append(Ui.text(nextChunkSentence(p) + " Or release a chunk you don't need. ", ChatFormatting.YELLOW))
					.append(mapButton()));
			return false;
		}
		Claim cl = new Claim();
		cl.owner = p.getUUID();
		cl.ownerName = p.getName().getString();
		cl.since = System.currentTimeMillis();
		STORE.get().claims.put(key(level, c), cl);
		STORE.saveNow();
		showBorder(p, level, c, GREEN);
		Ui.sound(p, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.5f, 1.6f);
		LAST.remove(p.getUUID());
		return true;
	}

	static boolean unclaim(ServerPlayer p, ServerLevel level, ChunkPos c) {
		Claim existing = claimAt(level, c);
		if (existing == null || !existing.owner.equals(p.getUUID())) {
			msg(p, existing == null ? "This chunk isn't claimed." : "This chunk belongs to " + existing.ownerName + ", not you.", ChatFormatting.GRAY);
			return false;
		}
		STORE.get().claims.remove(key(level, c));
		STORE.saveNow();
		showBorder(p, level, c, RED);
		LAST.remove(p.getUUID());
		return true;
	}

	/** /claim and /claim here: claim the chunk you stand in, or open the map if it's already yours. */
	static int claimHere(ServerPlayer p) {
		ServerLevel level = p.level();
		ChunkPos c = p.chunkPosition();
		Claim existing = claimAt(level, c);
		if (existing != null && existing.owner.equals(p.getUUID())) {
			ClaimView.open(p);
			return 1;
		}
		if (!claim(p, level, c)) return 0;
		toast(p, true);
		return 1;
	}

	/** "Chunk claimed / released" at the top of the screen, with how much of the budget is used. */
	static void toast(ServerPlayer p, boolean claimed) {
		int used = owned(p.getUUID()), max = budget(p);
		nl.libreria.core.gui.Toast.show(p, new net.minecraft.world.item.ItemStack(claimed ? Items.GOLDEN_SHOVEL : Items.WOODEN_SHOVEL),
				claimed ? nl.libreria.core.gui.Toast.GOOD : nl.libreria.core.gui.Toast.WARN,
				Ui.text(claimed ? "Chunk claimed" : "Chunk released", ChatFormatting.WHITE),
				Ui.text(claimed ? "Its borders glow for a moment · /claim release to undo" : "Free land again", ChatFormatting.GRAY),
				nl.libreria.core.gui.TopCard.Row.bar("flag", "Budget", max == 0 ? 1 : used / (double) max, 0x55FF55, 10,
						Ui.text(used + " of " + max, ChatFormatting.WHITE)));
	}

	/** /claim release: give up the chunk you stand in. */
	static int releaseHere(ServerPlayer p) {
		if (!unclaim(p, p.level(), p.chunkPosition())) return 0;
		toast(p, false);
		return 1;
	}

	static void trust(ServerPlayer p, UUID other, String name, boolean on) {
		Owner o = owner(p.getUUID());
		if (on) o.trusted.put(other, name);
		else o.trusted.remove(other);
		STORE.markDirty();
		msg(p, on ? name + " can now build in all your land." : name + " can no longer build in your land.", on ? ChatFormatting.GREEN : ChatFormatting.GRAY);
		// Let the friend know (only the good news)
		ServerPlayer friend = on ? p.level().getServer().getPlayerList().getPlayer(other) : null;
		if (friend != null && friend != p) msg(friend, p.getName().getString() + " trusted you: you can now build in their land.", ChatFormatting.GREEN);
	}

	private static int trustCommand(ServerPlayer p, String name) {
		ServerPlayer other = p.level().getServer().getPlayerList().getPlayerByName(name);
		if (other == null) {
			msg(p, name + " isn't online. Friends must be online to be trusted.", ChatFormatting.RED);
			return 0;
		}
		if (other == p) {
			msg(p, "That's you! You can always build in your own land.", ChatFormatting.GRAY);
			return 0;
		}
		trust(p, other.getUUID(), other.getName().getString(), true);
		return 1;
	}

	private static int untrustCommand(ServerPlayer p, String name) {
		Owner o = owner(p.getUUID());
		for (Map.Entry<UUID, String> e : o.trusted.entrySet()) {
			if (e.getValue().equalsIgnoreCase(name)) {
				trust(p, e.getKey(), e.getValue(), false);
				return 1;
			}
		}
		msg(p, name + " isn't on your trusted list.", ChatFormatting.GRAY);
		return 0;
	}

	private static CompletableFuture<Suggestions> suggestOnline(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
		String typed = b.getRemainingLowerCase();
		ServerPlayer self = ctx.getSource().getPlayer();
		for (ServerPlayer other : ctx.getSource().getServer().getPlayerList().getPlayers()) {
			String n = other.getName().getString();
			if (other != self && n.toLowerCase(Locale.ROOT).startsWith(typed)) b.suggest(n);
		}
		return b.buildFuture();
	}

	private static CompletableFuture<Suggestions> suggestTrusted(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
		String typed = b.getRemainingLowerCase();
		ServerPlayer self = ctx.getSource().getPlayer();
		if (self != null) {
			for (String n : owner(self.getUUID()).trusted.values()) if (n.toLowerCase(Locale.ROOT).startsWith(typed)) b.suggest(n);
		}
		return b.buildFuture();
	}

	// ---------------------------------------------------------------- borders and info

	/** Laat de rand van een chunk even oplichten (alleen voor jou). */
	static void showBorder(ServerPlayer p, ServerLevel level, ChunkPos c, int color) {
		showBorder(p, level, c, color, 100);
	}

	static void showBorder(ServerPlayer p, ServerLevel level, ChunkPos c, int color, int ticks) {
		double y = Math.floor(p.getY());
		AABB box = new AABB(c.getMinBlockX(), y - 1, c.getMinBlockZ(), c.getMaxBlockX() + 1, y + 4, c.getMaxBlockZ() + 1);
		Guide.flash(p, level, box, color, ticks);
	}

	/** /claim show: the borders of all claims within {@link #SHOW_RADIUS} chunks glow for a while. */
	static void showNearby(ServerPlayer p) {
		ServerLevel level = p.level();
		ChunkPos here = p.chunkPosition();
		int mine = 0, trusted = 0, others = 0;
		for (int dx = -SHOW_RADIUS; dx <= SHOW_RADIUS; dx++) {
			for (int dz = -SHOW_RADIUS; dz <= SHOW_RADIUS; dz++) {
				ChunkPos c = new ChunkPos(here.x() + dx, here.z() + dz);
				Claim cl = claimAt(level, c);
				if (cl == null) continue;
				int color;
				if (cl.owner.equals(p.getUUID())) {
					color = GREEN;
					mine++;
				} else if (trustedIn(p, cl)) {
					color = CYAN;
					trusted++;
				} else {
					color = RED;
					others++;
				}
				showBorder(p, level, c, color, SHOW_TICKS);
			}
		}
		boolean hereFree = claimAt(level, here) == null;
		if (hereFree) showBorder(p, level, here, YELLOW, SHOW_TICKS);
		Ui.sound(p, net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4f, 1.4f);

		int total = mine + trusted + others;
		MutableComponent m = Ui.prefix("Claims");
		if (total == 0) {
			m.append(Ui.text("No claims within " + SHOW_RADIUS + " chunks of you. The yellow box is the free chunk you're in: ", ChatFormatting.GRAY))
					.append(chatButton("Claim it", ChatFormatting.GREEN, new ClickEvent.RunCommand("/claim"), "Claim the chunk you stand in"));
		} else {
			m.append(Ui.text("Showing " + total + (total == 1 ? " claim" : " claims") + " near you for " + SHOW_TICKS / 20 + " seconds: ", ChatFormatting.GRAY));
			List<MutableComponent> legend = new ArrayList<>();
			if (mine > 0) legend.add(Ui.text("■ yours (" + mine + ")", ChatFormatting.GREEN));
			if (trusted > 0) legend.add(Ui.text("■ trusted (" + trusted + ")", ChatFormatting.AQUA));
			if (others > 0) legend.add(Ui.text("■ others (" + others + ")", ChatFormatting.RED));
			if (hereFree) legend.add(Ui.text("■ free chunk you're in", ChatFormatting.YELLOW));
			m.append(row(legend.toArray(MutableComponent[]::new)));
		}
		p.sendSystemMessage(m);
	}

	/** /claim info: who owns this chunk, your budget and how it grows. */
	static void info(ServerPlayer p) {
		ChunkPos c = p.chunkPosition();
		Claim cl = claimAt(p.level(), c);
		MutableComponent m = Ui.prefix("Claims").append(Ui.text("This chunk (" + c.x() + ", " + c.z() + "): ", ChatFormatting.GRAY));
		if (cl == null) m.append(Ui.text("free land", ChatFormatting.WHITE));
		else if (cl.owner.equals(p.getUUID())) m.append(Ui.text("your land", ChatFormatting.GREEN));
		else if (trustedIn(p, cl)) m.append(Ui.text(cl.ownerName + "'s land (you're trusted)", ChatFormatting.AQUA));
		else m.append(Ui.text(cl.ownerName + "'s land", ChatFormatting.RED));
		m.append("\n").append(Ui.text(budgetText(p) + " " + growthSentence(), ChatFormatting.GRAY));
		m.append("\n").append(Ui.text(nextChunkSentence(p), ChatFormatting.GRAY));
		Owner o = owner(p.getUUID());
		m.append("\n").append(Ui.text("Trusted players: " + (o.trusted.isEmpty() ? "none yet" : String.join(", ", o.trusted.values())), ChatFormatting.GRAY));
		List<MutableComponent> buttons = new ArrayList<>();
		if (cl == null) buttons.add(chatButton("Claim it", ChatFormatting.GREEN, new ClickEvent.RunCommand("/claim"), "Claim the chunk you stand in"));
		buttons.add(mapButton());
		buttons.add(chatButton("Show borders", ChatFormatting.YELLOW, new ClickEvent.RunCommand("/claim show"), "Show the claims around you"));
		buttons.add(trustButton());
		m.append("\n").append(row(buttons.toArray(MutableComponent[]::new)));
		p.sendSystemMessage(m);
	}

	/** One-time tip for players who never claimed anything. */
	private static void tip(ServerPlayer p) {
		if (!STORE.get().tipped.add(p.getUUID())) return;
		STORE.markDirty();
		if (owned(p.getUUID()) > 0) return;
		p.sendSystemMessage(Ui.prefix("Claims").append(Ui.text("Protect your base: stand inside it and type ", ChatFormatting.WHITE))
				.append(command("/claim", new ClickEvent.SuggestCommand("/claim"), "Click: put /claim in your chat"))
				.append(Ui.text(". Use ", ChatFormatting.WHITE))
				.append(command("/claim map", new ClickEvent.RunCommand("/claim map"), "Click: open the map"))
				.append(Ui.text(" to see your land.", ChatFormatting.WHITE)));
	}

	// ---------------------------------------------------------------- chat buttons

	/** "[Label]" that runs or suggests a command when clicked. */
	static MutableComponent chatButton(String label, ChatFormatting color, ClickEvent click, String hover) {
		return Component.literal("[" + label + "]").withStyle(s -> s.withColor(color).withBold(false).withItalic(false)
				.withClickEvent(click).withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}

	/** An underlined command in a sentence, clickable. */
	private static MutableComponent command(String text, ClickEvent click, String hover) {
		return Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.YELLOW).withUnderlined(true)
				.withClickEvent(click).withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}

	static MutableComponent mapButton() {
		return chatButton("Map", ChatFormatting.AQUA, new ClickEvent.RunCommand("/claim map"), "Open the claims map");
	}

	static MutableComponent trustButton() {
		return chatButton("Trust a friend", ChatFormatting.LIGHT_PURPLE, new ClickEvent.SuggestCommand("/claim trust "),
				"Type their name after /claim trust");
	}

	/** Components side by side, separated by spaces. */
	private static MutableComponent row(MutableComponent... parts) {
		MutableComponent m = Component.empty();
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) m.append(" ");
			m.append(parts[i]);
		}
		return m;
	}

	// ---------------------------------------------------------------- bescherming

	enum Action {BUILD, SWITCH}

	/** Mag deze speler hier dit doen? */
	static boolean allowed(ServerPlayer p, Level level, BlockPos pos, Action action) {
		Claim c = claimAt(level, ChunkPos.containing(pos));
		if (c == null || c.owner.equals(p.getUUID())) return true;
		Owner o = owner(c.owner);
		if (o.trusted.containsKey(p.getUUID())) return true;
		if (action == Action.SWITCH && o.doors) return true;
		return p.level().getServer().getPlayerList().isOp(p.nameAndId()) && Admin.bypass(p);
	}

	static boolean isSwitch(BlockState st) {
		var b = st.getBlock();
		return b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock || b instanceof ButtonBlock
				|| b instanceof LeverBlock || b instanceof BellBlock;
	}

	/** Dieren, dorpelingen, harnasstandaards, lijsten… maar geen monsters of spelers. */
	static boolean protectedEntity(Entity e) {
		return !(e instanceof Enemy) && !(e instanceof Player);
	}

	/** "✋ This land belongs to X" in the action bar, plus (at most once a minute) how to get access, in chat. */
	private static InteractionResult deny(ServerPlayer p, BlockPos pos) {
		Claim c = claimAt(p.level(), ChunkPos.containing(pos));
		String who = c != null ? c.ownerName : "someone else";
		Hud.flash(p, Component.literal("✋ This land belongs to " + who).withStyle(ChatFormatting.RED));
		long now = System.currentTimeMillis();
		Long last = HINTED.get(p.getUUID());
		if (last == null || now - last >= HINT_MS) {
			HINTED.put(p.getUUID(), now);
			String me = p.getName().getString();
			p.sendSystemMessage(Ui.prefix("Claims").append(Ui.text("✋ This land belongs to " + who + ". ", ChatFormatting.RED))
					.append(Component.literal("Ask them to /claim trust you.").withStyle(s -> s.withColor(ChatFormatting.GRAY)
							.withHoverEvent(new HoverEvent.ShowText(Component.literal("They type: /claim trust " + me))))));
		}
		// De client denkt soms dat het gelukt is: alles opnieuw sturen
		p.containerMenu.broadcastFullState();
		return InteractionResult.FAIL;
	}

	private static void enterMessage(ServerPlayer p) {
		Claim c = claimAt(p.level(), p.chunkPosition());
		String now = c == null ? "" : c.owner.toString();
		String before = LAST.put(p.getUUID(), now);
		if (before == null || before.equals(now)) return;
		if (c == null) {
			Hud.show(p, "claims", Component.literal("☘ Wilderness").withStyle(ChatFormatting.DARK_GREEN), Hud.PRIO_WARNING, 40);
			return;
		}
		boolean mine = c.owner.equals(p.getUUID());
		ChatFormatting color = mine ? ChatFormatting.GREEN : trustedIn(p, c) ? ChatFormatting.AQUA : ChatFormatting.GOLD;
		Hud.show(p, "claims", Component.literal("⚑ " + (mine ? "Your land" : c.ownerName + "'s land")).withStyle(color), Hud.PRIO_WARNING, 40);
	}

	static void msg(ServerPlayer p, String text, ChatFormatting color) {
		p.sendSystemMessage(Ui.prefix("Claims").append(Ui.text(text, color)));
	}

	/** Operators kunnen met /claim admin tijdelijk overal bouwen. */
	static final class Admin {
		private static final java.util.Set<UUID> ON = new java.util.HashSet<>();

		static boolean bypass(ServerPlayer p) {
			return ON.contains(p.getUUID());
		}

		static boolean toggle(ServerPlayer p) {
			if (!ON.remove(p.getUUID())) {
				ON.add(p.getUUID());
				return true;
			}
			return false;
		}
	}
}
