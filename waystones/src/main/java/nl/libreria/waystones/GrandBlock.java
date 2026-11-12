package nl.libreria.waystones;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import nl.libreria.core.Notify;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A grand waystone's blocks: 5 × 5 and ten high (the sculk floor it was made on, then nine above), where its shrine has
 * stone (see {@link Grand#solid}). {@link #X}, {@link #Y}, {@link #Z} say where in it a block is (2, 1, 2: the
 * waystone's own block, the middle of the plinth, which carries the drawing). Players see sculk for the floor, slabs
 * under the plinth where you walk (so you step onto it) and barriers for the rest, with the shrine drawn over them from
 * the resource pack. Nobody can break it: it's taken down from its window.
 */
public final class GrandBlock extends Block implements PolymerBlock, BlockWithElementHolder {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final IntegerProperty X = IntegerProperty.create("x", 0, 4);
	public static final IntegerProperty Y = IntegerProperty.create("y", 0, 9);
	public static final IntegerProperty Z = IntegerProperty.create("z", 0, 4);
	public static final BooleanProperty DARK = BooleanProperty.create("dark");

	private static final VoxelShape SLAB = Block.box(0, 0, 0, 16, 8, 16);

	GrandBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(X, 2).setValue(Y, 1).setValue(Z, 2)
				.setValue(DARK, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, X, Y, Z, DARK);
	}

	/** The block of the waystone itself (where the shrine is drawn from, and what it's known by). */
	static boolean isMiddle(BlockState s) {
		return s.getValue(X) == 2 && s.getValue(Y) == 1 && s.getValue(Z) == 2;
	}

	/** The waystone's own block, from any of the shrine's. */
	static BlockPos middleOf(BlockPos pos, BlockState s) {
		return pos.offset(2 - s.getValue(X), 1 - s.getValue(Y), 2 - s.getValue(Z));
	}

	/** Where you walk on the plinth: half a block high (a slab), so you step onto it. */
	static boolean walkOn(BlockState s) {
		return s.getValue(Y) == 1 && !Grand.solidAt(s.getValue(FACING), s.getValue(X), s.getValue(Z), 0);
	}

	// ---------------------------------------------------------------- what players see

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		if (state.getValue(Y) == 0) return Blocks.SCULK.defaultBlockState();
		if (walkOn(state)) {
			return (state.getValue(DARK) ? Blocks.POLISHED_BLACKSTONE_SLAB : Blocks.SMOOTH_STONE_SLAB).defaultBlockState().setValue(SlabBlock.TYPE,
					SlabType.BOTTOM);
		}
		if (nl.libreria.core.Bedrock.is(context)) return bedrock(state);
		return Blocks.BARRIER.defaultBlockState();
	}

	/**
	 * For Bedrock players (their game can't draw the shrine): the shrine in real blocks of its stone, where it has stone.
	 * Pillars, balustrade and ring in bricks, the pedestal carved with amethyst on top (the heart's place), the cornice
	 * smooth, the roof in its roof's colour.
	 */
	static BlockState bedrock(BlockState state) {
		boolean dark = state.getValue(DARK);
		int k = state.getValue(Y) - 1;
		boolean middle = state.getValue(X) == 2 && state.getValue(Z) == 2;
		if (middle && k == 1) return Blocks.AMETHYST_BLOCK.defaultBlockState();
		if (middle && k == 0) return (dark ? Blocks.CHISELED_DEEPSLATE : Blocks.CHISELED_STONE_BRICKS).defaultBlockState();
		if (k <= 4) return (dark ? Blocks.DEEPSLATE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState();
		if (k == 5) return (dark ? Blocks.POLISHED_BLACKSTONE : Blocks.SMOOTH_STONE).defaultBlockState();
		return (dark ? Blocks.OXIDIZED_CUT_COPPER : Blocks.DEEPSLATE_TILES).defaultBlockState();
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return state.getValue(Y) == 0 ? Blocks.SCULK.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return walkOn(state) ? SLAB : Shapes.block();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return walkOn(state) ? SLAB : Shapes.block();
	}

	@Override
	public @Nullable ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
		return isMiddle(state) ? new Holder(level, pos, state) : null;
	}

	@Override
	public boolean tickElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
		return isMiddle(state);
	}

	// ---------------------------------------------------------------- it doesn't break

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter getter, BlockPos pos) {
		return 0;
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(WaystonesMod.HEART);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
		// Gone some other way than being taken down (a command): it's off every list
		if (isMiddle(state) && !level.getBlockState(pos).is(this)) {
			Stones.Stone s = Stones.at(level, pos);
			if (s != null && s.grand) Stones.removed(level, pos);
		}
		super.affectNeighborsAfterRemoval(state, level, pos, moved);
	}

	// ---------------------------------------------------------------- using it

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer p) Stones.use(p, sl, middleOf(pos, state));
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer p) {
			InteractionResult r = withItem(p, sl, middleOf(pos, state), stack);
			if (r != null) return r;
			// On the plinth or the floor you can put things (a lantern, a carpet)
			if (state.getValue(Y) <= 1 && stack.getItem() instanceof BlockItem) return InteractionResult.PASS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/** An item used on the shrine or its heart: pearls bound to it, a dye or an ink sac its look; null for anything else. */
	static @Nullable InteractionResult withItem(ServerPlayer p, ServerLevel level, BlockPos middle, ItemStack stack) {
		if (stack.is(WaystonesMod.HEART)) {
			Notify.info(p, WaystonesMod.HEART, "This is a grand waystone already", "Use the heart on an ordinary waystone on a sculk floor.");
			return InteractionResult.SUCCESS;
		}
		if (stack.is(WaystonesMod.PEARL) && PearlItem.bindAt(p, stack, level, middle)) return InteractionResult.SUCCESS;
		if (Stones.paint(p, level, middle, stack)) return InteractionResult.SUCCESS;
		return null;
	}

	// ---------------------------------------------------------------- the drawing

	/**
	 * The shrine (its stone, and what shines in the colour of its dye), its heart turning over the pedestal with three
	 * shards circling round it, its name above the roof; redrawn when it gets another colour. Raised just now, it comes
	 * up out of the ground first and the heart appears after.
	 */
	static final class Holder extends ElementHolder {
		/** How far below its place the shrine starts as it rises (blocks), and how long the rise takes (ticks). */
		private static final float DEPTH = 10.5f;
		private static final int RISE = 90;
		/** The name: above the roof's crystal. */
		private static final float LABEL_Y = 9.0f;

		private final ServerLevel level;
		private final BlockPos pos;
		private final Direction front;
		private WaystoneBlock.@Nullable Look look;
		private final List<ItemDisplayElement> body = new ArrayList<>();
		private @Nullable ItemDisplayElement heart;
		private @Nullable ItemDisplayElement shards;
		private @Nullable TextDisplayElement label;
		private String shown = "";
		private int ticks;
		/** Ticks into rising (-1: standing). */
		private int rising = -1;

		Holder(ServerLevel level, BlockPos pos, BlockState state) {
			this.level = level;
			this.pos = pos.immutable();
			this.front = state.getValue(FACING);
			Stones.Stone s = Stones.at(level, this.pos);
			if (s != null && Grand.RISING.remove(s.key())) this.rising = 0;
			draw(WaystoneBlock.Look.of(level, this.pos));
			if (s != null) name(s);
		}

		/** The drawing's front (its steps and opening) towards {@link #front} (an item display shows a model turned half round). */
		private Quaternionf turn() {
			return new Quaternionf().rotateY((float) Math.toRadians(-this.front.toYRot()));
		}

		private void draw(WaystoneBlock.Look look) {
			for (var e : new ArrayList<>(this.getElements())) this.removeElement(e);
			this.body.clear();
			this.look = look;
			boolean up = this.rising >= 0;
			for (String part : new String[] {look.dark() ? "dark" : "light", "shine"}) {
				boolean shines = part.equals("shine");
				ItemDisplayElement e = WaystoneLook.part("grand/" + part, shines ? look.rgb() : -1, shines && look.glow(), 4f);
				e.setScale(new Vector3f(4));
				e.setLeftRotation(turn());
				e.setTranslation(new Vector3f(0, Grand.STATIC_Y - (up ? DEPTH : 0), 0));
				this.addElement(e);
				this.body.add(e);
			}
			this.heart = WaystoneLook.part("grand/heart", look.rgb(), look.glow(), 2f);
			this.shards = WaystoneLook.part("grand/shards", look.rgb(), look.glow(), 2f);
			for (ItemDisplayElement e : new ItemDisplayElement[] {this.heart, this.shards}) {
				e.setTranslation(new Vector3f(0, Grand.HEART_Y, 0));
				e.setScale(new Vector3f(up ? 0.001f : 1f));
				this.addElement(e);
			}
			TextDisplayElement label = new TextDisplayElement(Component.empty());
			label.setBillboardMode(Display.BillboardConstraints.CENTER);
			label.setShadow(true);
			label.setBackground(0);
			label.setBrightness(new Brightness(15, 15));
			label.setViewRange(0.5f);
			label.setScale(new Vector3f(1.1f));
			label.setTranslation(new Vector3f(0, LABEL_Y, 0));
			this.addElement(label);
			this.label = label;
			this.shown = "";
			// A click on the heart is a click on the waystone
			InteractionElement click = new InteractionElement(new VirtualElement.InteractionHandler() {
				@Override
				public void interact(ServerPlayer player, InteractionHand hand, Vec3 at, boolean secondary) {
					if (hand != InteractionHand.MAIN_HAND) return;
					if (withItem(player, Holder.this.level, Holder.this.pos, player.getItemInHand(hand)) != null) return;
					Stones.use(player, Holder.this.level, Holder.this.pos);
				}
			});
			click.setSize(1.1f, 2.5f);
			click.setOffset(new Vec3(0, Grand.HEART_Y - 1.25, 0));
			this.addElement(click);
		}

		/** Not for Bedrock players: their game can't draw these (they see real blocks instead). */
		@Override
		public boolean startWatching(net.minecraft.server.network.ServerGamePacketListenerImpl player) {
			if (nl.libreria.core.Bedrock.is(player.getPlayer())) return false;
			return super.startWatching(player);
		}

		private void name(Stones.Stone s) {
			String key = s.name + "|" + s.rgb();
			if (key.equals(this.shown) || this.label == null) return;
			this.shown = key;
			this.label.setText(Ui.text(s.name, s.rgb()));
		}

		@Override
		protected void onTick() {
			this.ticks++;
			if (this.rising >= 0) {
				rise();
				return;
			}
			if (this.ticks % 10 == 0) {
				int step = this.ticks / 10;
				if (this.heart != null) WaystoneLook.turn(this.heart, Grand.HEART_Y, step, 1f);
				if (this.shards != null) {
					this.shards.setLeftRotation(new Quaternionf().rotateY((float) Math.toRadians(-step * 18 % 360)));
					this.shards.setTranslation(new Vector3f(0, Grand.HEART_Y + (float) Math.sin(step * 0.3 + 1) * 0.04f, 0));
					this.shards.setInterpolationDuration(10);
					this.shards.startInterpolation();
				}
			}
			if (this.ticks % 40 != 1) return;
			Stones.Stone s = Stones.at(this.level, this.pos);
			if (s == null) return;
			WaystoneBlock.Look now = new WaystoneBlock.Look(s.dark, s.rgb(), s.glow);
			if (!now.equals(this.look)) draw(now);
			name(s);
			if (s.glow && !this.getWatchingPlayers().isEmpty() && this.ticks % 120 == 1) {
				this.level.sendParticles(ParticleTypes.ENCHANT, this.pos.getX() + 0.5, this.pos.getY() + 3.6, this.pos.getZ() + 0.5, 10, 0.4, 0.8, 0.4, 0.6);
			}
		}

		/** Coming up out of the ground, then the heart. */
		private void rise() {
			int t = this.rising++;
			double x = this.pos.getX() + 0.5, y = this.pos.getY(), z = this.pos.getZ() + 0.5;
			if (t == 2) {
				for (ItemDisplayElement e : this.body) {
					e.setTranslation(new Vector3f(0, Grand.STATIC_Y, 0));
					e.setInterpolationDuration(RISE);
					e.startInterpolation();
				}
				this.level.playSound(null, x, y, z, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 2f, 0.6f);
			}
			if (t >= 2 && t < RISE + 2) {
				if (t % 3 == 0) {
					this.level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.2, z, 6, 2.2, 0.1, 2.2, 0.02);
					this.level.sendParticles(ParticleTypes.CLOUD, x, y + 0.1, z, 4, 2.6, 0.05, 2.6, 0.01);
				}
				if (t % 15 == 2) this.level.playSound(null, x, y, z, SoundEvents.DEEPSLATE_BRICKS_PLACE, SoundSource.BLOCKS, 1.5f, 0.5f);
			}
			if (t == RISE + 6) {
				for (ItemDisplayElement e : new ItemDisplayElement[] {this.heart, this.shards}) {
					if (e == null) continue;
					e.setScale(new Vector3f(1f));
					e.setInterpolationDuration(20);
					e.startInterpolation();
				}
				this.level.playSound(null, x, y + 3, z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5f, 1.2f);
				this.level.playSound(null, x, y + 3, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2f, 0.8f);
				this.level.sendParticles(ParticleTypes.END_ROD, x, y + Grand.HEART_Y + 0.5, z, 40, 0.3, 0.8, 0.3, 0.08);
			}
			if (t >= RISE + 30) this.rising = -1;
		}
	}
}
