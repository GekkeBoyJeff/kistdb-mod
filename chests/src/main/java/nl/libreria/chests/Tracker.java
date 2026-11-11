package nl.libreria.chests;

import nl.libreria.core.Hud;

import nl.libreria.core.Guide;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

/** Houdt bij welke kisten een speler opent, en wisselt noIndex met sneak + linksklik. */
public final class Tracker {
	private record Open(String dim, BlockPos key, int tick) {
	}

	private static final Map<UUID, Open> OPEN = new HashMap<>();
	private static final Map<UUID, Integer> TOGGLE_COOLDOWN = new HashMap<>();

	private Tracker() {
	}

	static InteractionResult onUseBlock(Player player, Level world, InteractionHand hand, BlockHitResult hit) {
		if (!ChestsMod.MODULE.on()) return InteractionResult.PASS;
		if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || sp.isSpectator()) {
			return InteractionResult.PASS;
		}
		// Vanilla opent de kist niet als je sneakt met iets in je hand
		if (sp.isSecondaryUseActive() && (!sp.getMainHandItem().isEmpty() || !sp.getOffhandItem().isEmpty())) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockEntity be = world.getBlockEntity(pos);
		if (!Containers.isStorage(be)) return InteractionResult.PASS;

		ServerLevel level = (ServerLevel) world;
		BlockPos key = Containers.keyOf(level, pos);
		Guide.arrivedAt(sp, level, pos);
		// Only chests on your own land are in your item search: no search that sends you a thousand blocks away
		if (!Search.counts(sp, level, key)) {
			nl.libreria.core.Tips.once(sp, TIP_OWN_LAND);
			return InteractionResult.PASS;
		}
		boolean isNew = Store.track(sp.getUUID(), level, key, Containers.blockId(level, key));
		OPEN.put(sp.getUUID(), new Open(Store.dimId(level), key, level.getServer().getTickCount()));

		Store.Entry entry = Store.get(sp.getUUID()).find(Store.dimId(level), key);
		if (entry != null && entry.noIndex) {
			actionbar(sp, Component.literal("✘ ").withStyle(ChatFormatting.RED)
					.append(Component.literal("Left out of your item search").withStyle(ChatFormatting.GRAY)));
		} else if (isNew && ChestsMod.NEW_MESSAGE.get(sp)) {
			actionbar(sp, Component.literal("✔ ").withStyle(ChatFormatting.GREEN)
					.append(Component.literal("Added to your chests").withStyle(ChatFormatting.GRAY)));
		}
		// The first chest you ever open: how to sort it and what the ⇅ button does
		if (isNew) nl.libreria.core.Tips.once(sp, TIP_CHEST);
		return InteractionResult.PASS;
	}

	static InteractionResult onAttackBlock(Player player, Level world, InteractionHand hand, BlockPos pos, Direction direction) {
		if (!ChestsMod.MODULE.on()) return InteractionResult.PASS;
		if (world.isClientSide() || !(player instanceof ServerPlayer sp) || sp.isSpectator()) return InteractionResult.PASS;
		if (!sp.isShiftKeyDown() || !sp.getMainHandItem().isEmpty() || !ChestsMod.SNEAK_NOINDEX.get(sp)) return InteractionResult.PASS;
		if (!Containers.isStorage(world.getBlockEntity(pos))) return InteractionResult.PASS;

		ServerLevel level = (ServerLevel) world;
		int now = level.getServer().getTickCount();
		Integer until = TOGGLE_COOLDOWN.get(sp.getUUID());
		if (until != null && now < until) return InteractionResult.SUCCESS;
		TOGGLE_COOLDOWN.put(sp.getUUID(), now + 10);

		BlockPos key = Containers.keyOf(level, pos);
		toggleNoIndex(sp, level, key);
		return InteractionResult.SUCCESS;
	}

	/** Zet noIndex om voor deze container en geeft visuele feedback. */
	public static void toggleNoIndex(ServerPlayer sp, ServerLevel level, BlockPos key) {
		Store.track(sp.getUUID(), level, key, Containers.blockId(level, key));
		Store.PlayerData data = Store.get(sp.getUUID());
		Store.Entry entry = data.find(Store.dimId(level), key);
		if (entry == null) return;
		entry.noIndex = !entry.noIndex;
		data.markDirty();
		if (entry.noIndex) {
			Beam.flash(sp, level, key, 0xFF5555, 40);
			actionbar(sp, Component.literal("✘ ").withStyle(ChatFormatting.RED)
					.append(Component.literal("This chest is left out of your item search").withStyle(ChatFormatting.GRAY)));
		} else {
			Beam.flash(sp, level, key, 0x55FF55, 40);
			actionbar(sp, Component.literal("✔ ").withStyle(ChatFormatting.GREEN)
					.append(Component.literal("This chest is in your item search again").withStyle(ChatFormatting.GRAY)));
		}
	}

	/** Na het sluiten van een kist de momentopname bijwerken (voor als de chunk later niet geladen is). */
	static void tick(ServerPlayer player, int tick) {
		Open open = OPEN.get(player.getUUID());
		if (open == null || tick <= open.tick() + 1) return;
		if (player.containerMenu != player.inventoryMenu) return;
		OPEN.remove(player.getUUID());
		ServerLevel level = player.level();
		if (!Store.dimId(level).equals(open.dim())) return;
		Store.PlayerData data = Store.get(player.getUUID());
		Store.Entry entry = data.find(open.dim(), open.key());
		if (entry != null && !Containers.refresh(level, entry, data)) {
			Store.remove(player.getUUID(), entry);
		}
	}

	/** The chest this player has open right now (its key position), or null. */
	public static @org.jetbrains.annotations.Nullable BlockPos openChest(ServerPlayer player) {
		Open open = OPEN.get(player.getUUID());
		if (open == null || player.containerMenu == player.inventoryMenu || !Store.dimId(player.level()).equals(open.dim())) return null;
		return open.key();
	}

	static final nl.libreria.core.Tips.Tip TIP_OWN_LAND = nl.libreria.core.Tips.register("chests_own_land", nl.libreria.core.Domain.YOU,
			net.minecraft.world.item.Items.FILLED_MAP, "Item search: your own land", "Only chests in your own claims are in",
			"your item search: it never sends you far.");
	static final nl.libreria.core.Tips.Tip TIP_CHEST = nl.libreria.core.Tips.register("chest", nl.libreria.core.Domain.YOU,
			net.minecraft.world.item.Items.CHEST, "Sorting a chest", "Double-click an empty slot to sort it.",
			"The Sort button: right-click puts your matching items in.");

	static void forget(ServerPlayer player) {
		OPEN.remove(player.getUUID());
		TOGGLE_COOLDOWN.remove(player.getUUID());
	}

	public static void actionbar(ServerPlayer player, Component text) {
		Hud.flash(player, text);
	}
}
