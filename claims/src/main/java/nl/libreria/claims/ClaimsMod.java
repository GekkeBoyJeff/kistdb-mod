package nl.libreria.claims;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import nl.libreria.core.WorldStore;
import org.jetbrains.annotations.Nullable;

/**
 * ClaimsMod – een Libreria-module om chunks te claimen. Hoeveel je mag claimen groeit met je speeltijd en (met CollectorMod)
 * je prestatiepunten. In een geclaimde chunk kunnen alleen jij en wie je vertrouwt bouwen, kisten openen en dieren
 * aanraken. Deuren, knoppen en hendels mogen bezoekers standaard wel gebruiken (per eigenaar in te stellen).
 */
public final class ClaimsMod implements ModInitializer {
	public static final Module MODULE = Module.register("claims", "Claims", Items.GOLDEN_SHOVEL,
			"Claim chunks from a map. Your budget", "grows with playtime and achievements.");

	/** Budget: basis + per speeluur + per prestatiepunt. */
	static final int BASE = 4;
	static final int HOURS_PER_CHUNK = 2;
	static final int POINTS_PER_CHUNK = 20;
	static final int MAX = 400;

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
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/claims.json", Data.class, Data::new);
	/** Waar elke speler het laatst was (eigenaar-UUID of null), voor de "je betreedt…"-melding. */
	private static final Map<UUID, String> LAST = new HashMap<>();

	@Override
	public void onInitialize() {
		MODULE.hubEntry(Items.FILLED_MAP, "Claims (map)", ChatFormatting.GREEN, MapGui::open,
				"Map of the chunks around you.", "Click to claim or release.");

		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) ->
				!(player instanceof ServerPlayer sp) || allowed(sp, world, pos, Action.BUILD));
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			BlockPos pos = hit.getBlockPos();
			BlockState st = world.getBlockState(pos);
			Action action = isSwitch(st) ? Action.SWITCH : Action.BUILD;
			if (!allowed(sp, world, pos, action)) return deny(sp);
			// Plaatsen tegen een blok aan kan net over de grens vallen
			if (!sp.getItemInHand(hand).isEmpty() && !allowed(sp, world, pos.relative(hit.getDirection()), Action.BUILD)) return deny(sp);
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			if (!(sp.getItemInHand(hand).getItem() instanceof BucketItem)) return InteractionResult.PASS;
			BlockHitResult hit = world.clip(new ClipContext(sp.getEyePosition(), sp.getEyePosition().add(sp.getViewVector(1f).scale(sp.blockInteractionRange())),
					ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, sp));
			if (hit.getType() == HitResult.Type.BLOCK && (!allowed(sp, world, hit.getBlockPos(), Action.BUILD)
					|| !allowed(sp, world, hit.getBlockPos().relative(hit.getDirection()), Action.BUILD))) {
				return deny(sp);
			}
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp) || !protectedEntity(entity)) return InteractionResult.PASS;
			return allowed(sp, world, entity.blockPosition(), Action.BUILD) ? InteractionResult.PASS : deny(sp);
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp) || !protectedEntity(entity)) return InteractionResult.PASS;
			// Handelen met dorpelingen mag altijd
			if (entity instanceof AbstractVillager) return InteractionResult.PASS;
			return allowed(sp, world, entity.blockPosition(), Action.BUILD) ? InteractionResult.PASS : deny(sp);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 != 0) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) enterMessage(p);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
				Commands.literal("claim").executes(ctx -> {
					MapGui.open(ctx.getSource().getPlayerOrException());
					return 1;
				}).then(Commands.literal("here").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					claim(p, p.level(), p.chunkPosition());
					return 1;
				})).then(Commands.literal("release").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					unclaim(p, p.level(), p.chunkPosition());
					return 1;
				})).then(Commands.literal("admin").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					if (!ctx.getSource().getServer().getPlayerList().isOp(p.nameAndId())) {
						msg(p, "Operators only.", ChatFormatting.RED);
						return 0;
					}
					msg(p, Admin.toggle(p) ? "Admin mode on: you can build anywhere." : "Admin mode off.", ChatFormatting.GOLD);
					return 1;
				})).then(Commands.literal("trust").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					ServerPlayer other = ctx.getSource().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(ctx, "player"));
					if (other == null) p.sendSystemMessage(Ui.prefix("Claims").append(Ui.text("That player isn't online.", ChatFormatting.RED)));
					else trust(p, other.getUUID(), other.getName().getString(), true);
					return 1;
				}))).then(Commands.literal("untrust").then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					String name = StringArgumentType.getString(ctx, "player");
					Owner o = owner(p.getUUID());
					o.trusted.entrySet().removeIf(e -> e.getValue().equalsIgnoreCase(name));
					STORE.markDirty();
					p.sendSystemMessage(Ui.prefix("Claims").append(Ui.text(name + " can no longer build in your claims.", ChatFormatting.GRAY)));
					return 1;
				})))));

		ClaimsTests.register();
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

	// ---------------------------------------------------------------- budget

	static int hours(ServerPlayer p) {
		return p.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME) / 72000;
	}

	static int points(ServerPlayer p) {
		return Services.progress().map(pr -> pr.points(p)).orElse(0);
	}

	static int budget(ServerPlayer p) {
		return Math.min(MAX, BASE + hours(p) / HOURS_PER_CHUNK + points(p) / POINTS_PER_CHUNK);
	}

	// ---------------------------------------------------------------- claimen

	static boolean claim(ServerPlayer p, ServerLevel level, ChunkPos c) {
		Claim existing = claimAt(level, c);
		if (existing != null) {
			msg(p, existing.owner.equals(p.getUUID()) ? "This chunk is already yours." : "This chunk belongs to " + existing.ownerName + ".",
					ChatFormatting.GRAY);
			return false;
		}
		int have = owned(p.getUUID()), max = budget(p);
		if (have >= max) {
			msg(p, "Your budget is used up (" + have + "/" + max + "). Play more or earn achievements for extra chunks.", ChatFormatting.RED);
			return false;
		}
		Claim cl = new Claim();
		cl.owner = p.getUUID();
		cl.ownerName = p.getName().getString();
		cl.since = System.currentTimeMillis();
		STORE.get().claims.put(key(level, c), cl);
		STORE.saveNow();
		showBorder(p, level, c, 0x55FF55);
		Ui.sound(p, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.5f, 1.6f);
		Hud.flash(p, Component.literal("✔ Chunk claimed (" + (have + 1) + "/" + max + ")").withStyle(ChatFormatting.GREEN));
		LAST.remove(p.getUUID());
		return true;
	}

	static boolean unclaim(ServerPlayer p, ServerLevel level, ChunkPos c) {
		Claim existing = claimAt(level, c);
		if (existing == null || !existing.owner.equals(p.getUUID())) {
			msg(p, "This chunk isn't yours.", ChatFormatting.GRAY);
			return false;
		}
		STORE.get().claims.remove(key(level, c));
		STORE.saveNow();
		showBorder(p, level, c, 0xFF5555);
		Hud.flash(p, Component.literal("Chunk released").withStyle(ChatFormatting.GOLD));
		LAST.remove(p.getUUID());
		return true;
	}

	static void trust(ServerPlayer p, UUID other, String name, boolean on) {
		Owner o = owner(p.getUUID());
		if (on) o.trusted.put(other, name);
		else o.trusted.remove(other);
		STORE.markDirty();
		msg(p, on ? name + " can now build in all your chunks." : name + " can no longer build in your claims.", on ? ChatFormatting.GREEN : ChatFormatting.GRAY);
	}

	/** Laat de rand van een chunk even oplichten (alleen voor jou). */
	static void showBorder(ServerPlayer p, ServerLevel level, ChunkPos c, int color) {
		double y = Math.floor(p.getY());
		AABB box = new AABB(c.getMinBlockX(), y - 1, c.getMinBlockZ(), c.getMaxBlockX() + 1, y + 4, c.getMaxBlockZ() + 1);
		Guide.flash(p, level, box, color, 100);
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

	private static InteractionResult deny(ServerPlayer p) {
		Claim c = claimAt(p.level(), p.chunkPosition());
		Hud.flash(p, Component.literal("✋ This land belongs to " + (c != null ? c.ownerName : "someone else")).withStyle(ChatFormatting.RED));
		// De client denkt soms dat het gelukt is: alles opnieuw sturen
		p.containerMenu.broadcastFullState();
		return InteractionResult.FAIL;
	}

	private static void enterMessage(ServerPlayer p) {
		Claim c = claimAt(p.level(), p.chunkPosition());
		String now = c == null ? "" : c.owner.toString();
		String before = LAST.put(p.getUUID(), now);
		if (before == null || before.equals(now)) return;
		if (c == null) Hud.show(p, "claims", Component.literal("☘ Wilderness").withStyle(ChatFormatting.DARK_GREEN), Hud.PRIO_WARNING, 40);
		else Hud.show(p, "claims", Component.literal("⚑ Claimed by " + c.ownerName).withStyle(
				c.owner.equals(p.getUUID()) ? ChatFormatting.GREEN : ChatFormatting.GOLD), Hud.PRIO_WARNING, 40);
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
