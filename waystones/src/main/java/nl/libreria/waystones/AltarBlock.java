package nl.libreria.waystones;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The altar in front of a waystone (drawn by the pack, in its stone's look): a stepped base, a column with a rune, a
 * basin with a pool and a pearl floating and turning over it. It belongs to its waystone ({@link #FACING} points away
 * from it): a click on it is a click on the waystone, breaking it breaks the whole waystone.
 */
public final class AltarBlock extends Block implements PolymerBlock, BlockWithElementHolder {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	AltarBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return Blocks.BARRIER.defaultBlockState();
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return Blocks.STONE_BRICKS.defaultBlockState();
	}

	@Override
	public @Nullable ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
		return new Holder(level, pos, state);
	}

	@Override
	public boolean tickElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
		return true;
	}

	/** The altar in its stone's look, with the pearl turning over its pool; redrawn when the stone gets another colour. */
	static final class Holder extends ElementHolder {
		private final ServerLevel level;
		private final BlockPos stone;
		private final Direction front;
		private WaystoneBlock.@Nullable Look look;
		private @Nullable ItemDisplayElement pearl;
		private int ticks;

		Holder(ServerLevel level, BlockPos pos, BlockState state) {
			this.level = level;
			this.front = state.getValue(FACING);
			this.stone = pos.relative(this.front.getOpposite()).immutable();
			draw(WaystoneBlock.Look.of(level, this.stone));
		}

		private void draw(WaystoneBlock.Look look) {
			for (var e : new java.util.ArrayList<>(this.getElements())) this.removeElement(e);
			this.look = look;
			this.pearl = WaystoneLook.altar(this, look.dark(), look.rgb(), look.glow(), this.front);
		}

		@Override
		protected void onTick() {
			if (this.ticks++ % 10 != 0) return;
			if (this.pearl != null) WaystoneLook.turn(this.pearl, WaystoneLook.PEARL_Y, this.ticks / 10, 1f);
			if (this.ticks % 40 != 1) return;
			Stones.Stone s = Stones.at(this.level, this.stone);
			if (s == null) return;
			WaystoneBlock.Look now = new WaystoneBlock.Look(s.dark, s.rgb(), s.glow);
			if (!now.equals(this.look)) draw(now);
		}
	}

	// ---------------------------------------------------------------- it is its waystone

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer p) {
			BlockPos lower = WaystoneBlock.lowerOf(sl, pos, state);
			if (lower != null) Stones.use(p, sl, lower);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		return WaystoneBlock.useWith(stack, state, level, pos, player);
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter getter, BlockPos pos) {
		if (player instanceof ServerPlayer p && getter instanceof ServerLevel level) {
			BlockPos lower = WaystoneBlock.lowerOf(level, pos, state);
			if (lower != null && !Stones.mayBreak(p, Stones.at(level, lower))) return 0;
		}
		return super.getDestroyProgress(state, player, getter, pos);
	}

	/** Breaking the altar breaks its waystone (which drops as one waystone). */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel sl) {
			BlockPos lower = WaystoneBlock.lowerOf(sl, pos, state);
			if (lower != null) {
				if (!player.isCreative()) popResource(level, lower, WaystoneBlock.item(level, lower));
				level.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return level instanceof Level l ? WaystoneBlock.item(l, pos.relative(state.getValue(FACING).getOpposite())) : new ItemStack(WaystonesMod.WAYSTONE_ITEM);
	}
}
