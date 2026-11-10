package nl.libreria.waystones;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.util.ArrayList;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import nl.libreria.core.Notify;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * The waystone: three blocks tall ({@link #PART} 0 at the bottom). Players see barriers (the right size to bump into
 * and click), with the stone drawn on them from the resource pack (see {@link WaystoneLook}): light or dark, its shine
 * in the colour of the dye it was given, glowing or not, and its name above it. Its altar stands in front of it
 * ({@link #FACING}); placed by a player it is theirs, placed with a village it says so ({@link #VILLAGE}).
 */
public final class WaystoneBlock extends Block implements PolymerBlock, BlockWithElementHolder {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);
	public static final BooleanProperty VILLAGE = BooleanProperty.create("village");

	WaystoneBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(PART, 0).setValue(VILLAGE, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PART, VILLAGE);
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
		return state.getValue(PART) == 0 ? new Holder(level, pos, state) : null;
	}

	@Override
	public boolean tickElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
		return state.getValue(PART) == 0;
	}

	static ItemStack model(net.minecraft.resources.Identifier model) {
		ItemStack s = new ItemStack(net.minecraft.world.item.Items.PAPER);
		s.set(DataComponents.ITEM_MODEL, model);
		return s;
	}

	/** How the stone at {@code lower} looks now: light or dark, the colour, glowing. */
	record Look(boolean dark, int rgb, boolean glow) {
		static Look of(ServerLevel level, BlockPos lower) {
			Stones.Stone s = Stones.at(level, lower);
			return s == null ? new Look(false, WaystoneLook.shine(net.minecraft.world.item.DyeColor.PURPLE), true) : new Look(s.dark, s.rgb(), s.glow);
		}
	}

	/** The obelisk, its floating rune turning, its name above it; redrawn when it gets another colour. */
	static final class Holder extends ElementHolder {
		private final ServerLevel level;
		private final BlockPos pos;
		private final BlockState state;
		private @Nullable Look look;
		private @Nullable TextDisplayElement label;
		private @Nullable ItemDisplayElement rune;
		private String shown = "";
		private int ticks;

		Holder(ServerLevel level, BlockPos pos, BlockState state) {
			this.level = level;
			this.pos = pos.immutable();
			this.state = state;
			draw(Look.of(level, this.pos));
			Stones.Stone s = Stones.at(level, this.pos);
			if (s != null) name(s);
		}

		private void draw(Look look) {
			for (var e : new ArrayList<>(this.getElements())) this.removeElement(e);
			this.look = look;
			WaystoneLook.Obelisk o = WaystoneLook.obelisk(this, look.dark(), look.rgb(), look.glow());
			this.label = o.label();
			this.rune = o.rune();
			this.shown = "";
		}

		private void name(Stones.Stone s) {
			String key = s.name + "|" + s.rgb();
			if (key.equals(this.shown) || this.label == null) return;
			this.shown = key;
			this.label.setText(Ui.text(s.name, s.rgb()));
		}

		@Override
		protected void onTick() {
			if (this.ticks++ % 10 != 0) return;
			if (this.rune != null) WaystoneLook.turn(this.rune, WaystoneLook.RUNE_Y, this.ticks / 10, WaystoneLook.RUNE_SCALE);
			if (this.ticks % 40 != 1) return;
			// One placed while the world was made, or with /setblock: written down once it's here
			Stones.Stone s = Stones.at(this.level, this.pos);
			if (s == null && this.level.getBlockState(this.pos).is(WaystonesMod.WAYSTONE)) s = Stones.ensure(this.level, this.pos, this.state);
			if (s == null) return;
			Look now = new Look(s.dark, s.rgb(), s.glow);
			if (!now.equals(this.look)) draw(now);
			name(s);
			if (s.glow && !this.getWatchingPlayers().isEmpty() && this.ticks % 120 == 1) {
				this.level.sendParticles(ParticleTypes.ENCHANT, this.pos.getX() + 0.5, this.pos.getY() + 1.75, this.pos.getZ() + 0.5, 6, 0.2, 0.25, 0.2, 0.5);
			}
		}
	}

	// ---------------------------------------------------------------- placing and breaking

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
		Level level = ctx.getLevel();
		BlockPos pos = ctx.getClickedPos();
		Direction front = ctx.getHorizontalDirection().getOpposite();
		if (pos.getY() + 2 > level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(ctx) || !level.getBlockState(pos.above(2)).canBeReplaced(ctx)) {
			if (ctx.getPlayer() instanceof ServerPlayer p) {
				Notify.info(p, WaystonesMod.WAYSTONE_ITEM, "No room for a waystone", "It's three blocks tall: it needs room above.");
			}
			return null;
		}
		if (!level.getBlockState(pos.relative(front)).canBeReplaced(ctx)) {
			if (ctx.getPlayer() instanceof ServerPlayer p) {
				Notify.info(p, WaystonesMod.WAYSTONE_ITEM, "No room for its altar", "A waystone needs one free block in front of it, towards you.");
			}
			return null;
		}
		return this.defaultBlockState().setValue(FACING, front);
	}

	/** Puts a whole waystone at {@code lower} (its three parts and its altar towards {@code front}). */
	static void put(Level level, BlockPos lower, Direction front, boolean village, int flags) {
		BlockState st = WaystonesMod.WAYSTONE.defaultBlockState().setValue(FACING, front).setValue(VILLAGE, village);
		level.setBlock(lower, st.setValue(PART, 0), flags);
		level.setBlock(lower.above(), st.setValue(PART, 1), flags);
		level.setBlock(lower.above(2), st.setValue(PART, 2), flags);
		level.setBlock(lower.relative(front), WaystonesMod.ALTAR.defaultBlockState().setValue(AltarBlock.FACING, front), flags);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		if (!(level instanceof ServerLevel sl)) return;
		Direction front = state.getValue(FACING);
		level.setBlock(pos.above(), state.setValue(PART, 1), Block.UPDATE_ALL);
		level.setBlock(pos.above(2), state.setValue(PART, 2), Block.UPDATE_ALL);
		level.setBlock(pos.relative(front), WaystonesMod.ALTAR.defaultBlockState().setValue(AltarBlock.FACING, front), Block.UPDATE_ALL);
		boolean dark = stack.is(WaystonesMod.DARK_WAYSTONE_ITEM);
		if (placer instanceof ServerPlayer p) {
			Stones.Stone s = Stones.placed(p, sl, pos, state, dark);
			Component custom = stack.get(DataComponents.CUSTOM_NAME);
			if (custom != null && !custom.getString().isBlank()) {
				s.name = WaystonesWindows.clean(custom.getString());
				Stones.STORE.markDirty();
			}
			Notify.done(p, dark ? WaystonesMod.DARK_WAYSTONE_ITEM : WaystonesMod.WAYSTONE_ITEM, "Waystone placed: " + s.name,
					"Everyone can travel here. A dye colours it; shift + right-click: name, private.");
		} else {
			Stones.ensure(sl, pos, state).dark = dark;
		}
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos other,
			BlockState otherState, RandomSource random) {
		int part = state.getValue(PART);
		boolean ok;
		if (dir == Direction.UP && part < 2) ok = otherState.is(this) && otherState.getValue(PART) == part + 1;
		else if (dir == Direction.DOWN && part > 0) ok = otherState.is(this) && otherState.getValue(PART) == part - 1;
		else return state;
		return ok ? state : Blocks.AIR.defaultBlockState();
	}

	/** The waystone item for the stone at {@code lower}: light or dark. */
	static ItemStack item(Level level, BlockPos lower) {
		Stones.Stone s = Stones.at(level, lower);
		return new ItemStack(s != null && s.dark ? WaystonesMod.DARK_WAYSTONE_ITEM : WaystonesMod.WAYSTONE_ITEM);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel && !player.isCreative()) {
			BlockPos lower = pos.below(state.getValue(PART));
			popResource(level, lower, item(level, lower));
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
		if (state.getValue(PART) == 0 && !level.getBlockState(pos).is(this)) {
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
		return level instanceof Level l ? item(l, pos.below(state.getValue(PART))) : new ItemStack(WaystonesMod.WAYSTONE_ITEM);
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
		return useWith(stack, state, level, pos, player);
	}

	/** An item on the stone or its altar: pearls are bound to it, a dye or ink sac changes how it looks. */
	static InteractionResult useWith(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player) {
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer p) {
			BlockPos lower = lowerOf(sl, pos, state);
			if (lower != null) {
				if (stack.is(WaystonesMod.PEARL) && PearlItem.bindAt(p, stack, sl, lower)) return InteractionResult.SUCCESS;
				if (Stones.paint(p, sl, lower, stack)) return InteractionResult.SUCCESS;
			}
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/** Where the waystone stands (its lowest part) for a click on any of its parts (or its altar); null for other blocks. */
	static @Nullable BlockPos lowerOf(Level level, BlockPos pos, BlockState state) {
		if (state.is(WaystonesMod.WAYSTONE)) return pos.below(state.getValue(PART));
		if (state.is(WaystonesMod.ALTAR)) {
			BlockPos lower = pos.relative(state.getValue(AltarBlock.FACING).getOpposite());
			BlockState s = level.getBlockState(lower);
			return s.is(WaystonesMod.WAYSTONE) && s.getValue(PART) == 0 ? lower : null;
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
