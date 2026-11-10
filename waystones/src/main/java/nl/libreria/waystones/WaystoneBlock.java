package nl.libreria.waystones;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import nl.libreria.core.Notify;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The waystone: two blocks tall. Players see a barrier (the right size to bump into and click) with the obelisk and
 * its glowing runes drawn on it from the resource pack, and the waystone's name above. Its altar stands in front of
 * it ({@link #FACING}); placed by a player it is theirs, placed with a village it says so ({@link #VILLAGE}).
 */
public final class WaystoneBlock extends Block implements PolymerBlock, BlockWithElementHolder {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
	public static final BooleanProperty VILLAGE = BooleanProperty.create("village");

	static final Identifier OBELISK = Identifier.fromNamespaceAndPath("libreria", "waystone/obelisk");
	static final Identifier RUNES = Identifier.fromNamespaceAndPath("libreria", "waystone/runes");
	static final int RUNE_RGB = 0xC79BF0;

	WaystoneBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(HALF, DoubleBlockHalf.LOWER)
				.setValue(VILLAGE, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HALF, VILLAGE);
	}

	// ---------------------------------------------------------------- what players see

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
		return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new Holder(level, pos, state) : null;
	}

	@Override
	public boolean tickElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
		return state.getValue(HALF) == DoubleBlockHalf.LOWER;
	}

	static ItemStack model(Identifier model) {
		ItemStack s = new ItemStack(Items.PAPER);
		s.set(DataComponents.ITEM_MODEL, model);
		return s;
	}

	/** The obelisk, its runes (lit), and its name above it; and now and then a few sparks of enchanting. */
	static final class Holder extends ElementHolder {
		private final ServerLevel level;
		private final BlockPos pos;
		private final BlockState state;
		private final TextDisplayElement label;
		private String shown = "";
		private int ticks;

		Holder(ServerLevel level, BlockPos pos, BlockState state) {
			this.level = level;
			this.pos = pos.immutable();
			this.state = state;
			ItemDisplayElement stone = new ItemDisplayElement(model(OBELISK));
			stone.setItemDisplayContext(ItemDisplayContext.NONE);
			stone.setViewRange(1.5f);
			this.addElement(stone);
			ItemDisplayElement runes = new ItemDisplayElement(model(RUNES));
			runes.setItemDisplayContext(ItemDisplayContext.NONE);
			runes.setBrightness(new Brightness(15, 15));
			runes.setViewRange(1.5f);
			this.addElement(runes);
			this.label = new TextDisplayElement(Component.empty());
			this.label.setBillboardMode(Display.BillboardConstraints.CENTER);
			this.label.setShadow(true);
			this.label.setBackground(0x50000000);
			this.label.setBrightness(new Brightness(15, 15));
			this.label.setViewRange(0.3f);
			this.label.setScale(new Vector3f(0.8f));
			this.label.setTranslation(new Vector3f(0, 1.82f, 0));
			this.addElement(this.label);
			Stones.Stone s = Stones.at(level, pos);
			if (s != null) name(s.name);
		}

		private void name(String name) {
			if (name.equals(this.shown)) return;
			this.shown = name;
			this.label.setText(Ui.text(name, RUNE_RGB));
		}

		@Override
		protected void onTick() {
			if (this.ticks++ % 40 != 0) return;
			// A village's waystone was placed while the world was made: written down (with the village's name) once it's loaded
			Stones.Stone s = Stones.at(this.level, this.pos);
			if (s == null && this.level.getBlockState(this.pos).is(WaystonesMod.WAYSTONE)) s = Stones.ensure(this.level, this.pos, this.state);
			if (s != null) name(s.name);
			if (!this.getWatchingPlayers().isEmpty() && this.ticks % 120 == 1) {
				this.level.sendParticles(ParticleTypes.ENCHANT, this.pos.getX() + 0.5, this.pos.getY() + 1.4, this.pos.getZ() + 0.5, 5, 0.25, 0.5, 0.25, 0.6);
			}
		}
	}

	// ---------------------------------------------------------------- placing and breaking

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
		Level level = ctx.getLevel();
		BlockPos pos = ctx.getClickedPos();
		Direction front = ctx.getHorizontalDirection().getOpposite();
		if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(ctx)) return null;
		if (!level.getBlockState(pos.relative(front)).canBeReplaced(ctx)) {
			if (ctx.getPlayer() instanceof ServerPlayer p) {
				Notify.info(p, WaystonesMod.WAYSTONE_ITEM, "No room for its altar", "A waystone needs one free block in front of it, towards you.");
			}
			return null;
		}
		return this.defaultBlockState().setValue(FACING, front);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		if (!(level instanceof ServerLevel sl)) return;
		Direction front = state.getValue(FACING);
		level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
		level.setBlock(pos.relative(front), WaystonesMod.ALTAR.defaultBlockState().setValue(AltarBlock.FACING, front), Block.UPDATE_ALL);
		if (placer instanceof ServerPlayer p) {
			Stones.Stone s = Stones.placed(p, sl, pos, state);
			Component custom = stack.get(DataComponents.CUSTOM_NAME);
			if (custom != null && !custom.getString().isBlank()) {
				s.name = WaystonesWindows.clean(custom.getString());
				Stones.STORE.markDirty();
			}
			Notify.done(p, WaystonesMod.WAYSTONE_ITEM, "Waystone placed: " + s.name,
					"Everyone can travel here. Shift + right-click: its name, or make it private.");
		} else {
			Stones.ensure(sl, pos, state);
		}
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos other,
			BlockState otherState, RandomSource random) {
		DoubleBlockHalf half = state.getValue(HALF);
		if (dir.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (dir == Direction.UP)) {
			boolean partner = otherState.is(this) && otherState.getValue(HALF) != half;
			return partner ? state : Blocks.AIR.defaultBlockState();
		}
		return state;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel && !player.isCreative()) {
			BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
			popResource(level, lower, new ItemStack(WaystonesMod.WAYSTONE_ITEM));
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
		if (state.getValue(HALF) == DoubleBlockHalf.LOWER && !level.getBlockState(pos).is(this)) {
			Stones.removed(level, pos);
			BlockPos altar = pos.relative(state.getValue(FACING));
			BlockState a = level.getBlockState(altar);
			if (a.is(WaystonesMod.ALTAR) && a.getValue(AltarBlock.FACING) == state.getValue(FACING)) {
				level.setBlock(altar, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		super.affectNeighborsAfterRemoval(state, level, pos, moved);
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter getter, BlockPos pos) {
		if (player instanceof ServerPlayer p && getter instanceof ServerLevel level) {
			BlockPos lower = lowerOf(level, pos, state);
			if (lower != null && !Stones.mayBreak(p, Stones.at(level, lower))) return 0;
		}
		return super.getDestroyProgress(state, player, getter, pos);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(WaystonesMod.WAYSTONE_ITEM);
	}

	// ---------------------------------------------------------------- using it

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer p) {
			BlockPos lower = lowerOf(sl, pos, state);
			if (lower != null) Stones.use(p, sl, lower);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (stack.is(WaystonesMod.PEARL) && level instanceof ServerLevel sl && player instanceof ServerPlayer p) {
			BlockPos lower = lowerOf(sl, pos, state);
			if (lower != null && PearlItem.bindAt(p, stack, sl, lower)) return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/** Where the waystone stands (its lower half) for a click on any of its parts (or its altar); null for other blocks. */
	static @Nullable BlockPos lowerOf(Level level, BlockPos pos, BlockState state) {
		if (state.is(WaystonesMod.WAYSTONE)) return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.immutable() : pos.below();
		if (state.is(WaystonesMod.ALTAR)) {
			BlockPos lower = pos.relative(state.getValue(AltarBlock.FACING).getOpposite());
			BlockState s = level.getBlockState(lower);
			return s.is(WaystonesMod.WAYSTONE) && s.getValue(HALF) == DoubleBlockHalf.LOWER ? lower : null;
		}
		return null;
	}

	/** Why you can't break it. */
	static void refuse(ServerPlayer p, @Nullable Stones.Stone s) {
		if (s == null || s.village) {
			Notify.warn(p, WaystonesMod.WAYSTONE_ITEM, "This waystone belongs to the village", "Only an operator can move it.");
		} else {
			Notify.warn(p, WaystonesMod.WAYSTONE_ITEM, s.name + " is " + s.ownerName + "'s", "Only they and the people they trust can break it.");
		}
	}
}
