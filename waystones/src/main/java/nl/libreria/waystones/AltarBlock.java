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
 * The altar in front of a waystone: the game's own smooth stone slab, with a small pedestal and bowl on it and a
 * pearl floating and turning above. It belongs to its waystone ({@link #FACING} points away from it): a click on it
 * is a click on the waystone, breaking it breaks the waystone.
 */
public final class AltarBlock extends Block implements PolymerBlock, BlockWithElementHolder {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final VoxelShape SLAB = Block.box(0, 0, 0, 16, 8, 16);
	static final Identifier ALTAR = Identifier.fromNamespaceAndPath("libreria", "waystone/altar");
	static final Identifier PEARL = Identifier.fromNamespaceAndPath("libreria", "waystone/pearl");

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
		return Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SLAB;
	}

	@Override
	public @Nullable ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
		return new Holder();
	}

	@Override
	public boolean tickElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
		return true;
	}

	/** The pedestal and bowl, and the pearl above them: it turns slowly and bobs a little. */
	static final class Holder extends ElementHolder {
		private final ItemDisplayElement pearl;
		private int ticks;

		Holder() {
			ItemDisplayElement altar = new ItemDisplayElement(WaystoneBlock.model(ALTAR));
			altar.setItemDisplayContext(ItemDisplayContext.NONE);
			altar.setViewRange(1f);
			this.addElement(altar);
			this.pearl = new ItemDisplayElement(WaystoneBlock.model(PEARL));
			this.pearl.setItemDisplayContext(ItemDisplayContext.NONE);
			this.pearl.setBrightness(new Brightness(15, 15));
			this.pearl.setViewRange(0.6f);
			this.pearl.setTranslation(new Vector3f(0, 0.42f, 0));
			this.addElement(this.pearl);
		}

		@Override
		protected void onTick() {
			if (this.ticks++ % 10 != 0) return;
			int step = this.ticks / 10;
			this.pearl.setLeftRotation(new Quaternionf().rotateY((float) Math.toRadians(step * 40 % 360)).rotateX((float) Math.toRadians(20)));
			this.pearl.setTranslation(new Vector3f(0, 0.42f + (float) Math.sin(step * 0.6) * 0.025f, 0));
			this.pearl.setInterpolationDuration(10);
			this.pearl.startInterpolation();
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
		if (stack.is(WaystonesMod.PEARL) && level instanceof ServerLevel sl && player instanceof ServerPlayer p) {
			BlockPos lower = WaystoneBlock.lowerOf(sl, pos, state);
			if (lower != null && PearlItem.bindAt(p, stack, sl, lower)) return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
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
				if (!player.isCreative()) popResource(level, lower, new ItemStack(WaystonesMod.WAYSTONE_ITEM));
				level.setBlock(lower.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(WaystonesMod.WAYSTONE_ITEM);
	}
}
