package nl.kistdb;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
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
		boolean isNew = Store.track(sp.getUUID(), level, key, Containers.blockId(level, key));
		OPEN.put(sp.getUUID(), new Open(Store.dimId(level), key, level.getServer().getTickCount()));
		Beams.arrivedAt(sp, level, key);

		Store.Entry entry = Store.get(sp.getUUID()).find(Store.dimId(level), key);
		if (entry != null && entry.noIndex) {
			actionbar(sp, Component.literal("noIndex").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
					.append(Component.literal(" – deze kist wordt overgeslagen bij zoeken").withStyle(ChatFormatting.GRAY)));
		} else if (isNew) {
			actionbar(sp, Component.literal("✔ ").withStyle(ChatFormatting.GREEN)
					.append(Component.literal("Kist toegevoegd aan KistDB").withStyle(ChatFormatting.GRAY)));
		}
		return InteractionResult.PASS;
	}

	static InteractionResult onAttackBlock(Player player, Level world, InteractionHand hand, BlockPos pos, Direction direction) {
		if (world.isClientSide() || !(player instanceof ServerPlayer sp) || sp.isSpectator()) return InteractionResult.PASS;
		if (!sp.isShiftKeyDown() || !sp.getMainHandItem().isEmpty()) return InteractionResult.PASS;
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
			Beams.flash(sp, level, key, 0xFF5555, 40);
			actionbar(sp, Component.literal("noIndex AAN").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
					.append(Component.literal(" – deze kist wordt overgeslagen bij zoeken").withStyle(ChatFormatting.GRAY)));
		} else {
			Beams.flash(sp, level, key, 0x55FF55, 40);
			actionbar(sp, Component.literal("noIndex UIT").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
					.append(Component.literal(" – deze kist wordt weer doorzocht").withStyle(ChatFormatting.GRAY)));
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

	static void forget(ServerPlayer player) {
		OPEN.remove(player.getUUID());
		TOGGLE_COOLDOWN.remove(player.getUUID());
	}

	public static void actionbar(ServerPlayer player, Component text) {
		player.connection.send(new ClientboundSetActionBarTextPacket(text));
	}
}
