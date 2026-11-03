package nl.libreria.comfort;

import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Module;
import nl.libreria.core.SelfTests;
import nl.libreria.core.Setting;
import org.jetbrains.annotations.Nullable;

/** ComfortMod – een Libreria-module: dubbele deuren samen openen, zitten op trappen en platen, rechtsklik-oogsten. */
public final class ComfortMod implements ModInitializer {
	public static final Module MODULE = Module.register("comfort", "Gemak", Items.OAK_DOOR,
			"Dubbele deuren, zitten op trappen", "en rechtsklik-oogsten.");
	public static final Setting.Toggle DOORS = MODULE.toggle("deuren", "Dubbele deuren samen", true, Items.OAK_DOOR,
			"Open je één deur van een dubbele deur,", "dan gaat de andere mee. Sneak = alleen deze.");
	public static final Setting.Toggle SIT = MODULE.toggle("zitten", "Zitten op trappen en platen", true, Items.OAK_STAIRS,
			"Rechtsklik met lege handen op een trap of", "halve plaat om te zitten. Sneak = opstaan.");
	public static final Setting.Toggle HARVEST = MODULE.toggle("oogsten", "Rechtsklik oogsten", true, Items.WHEAT,
			"Rechtsklik op een rijp gewas (lege hand of", "schoffel): oogsten en meteen herplanten.");

	@Override
	public void onInitialize() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || sp.isSpectator()) {
				return InteractionResult.PASS;
			}
			ServerLevel level = (ServerLevel) world;
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (HARVEST.get(sp) && !sp.isShiftKeyDown() && harvest(sp, level, pos, state)) return InteractionResult.SUCCESS;
			if (SIT.get(sp) && trySit(sp, level, pos, state, hit)) return InteractionResult.SUCCESS;
			if (DOORS.get(sp) && !sp.isShiftKeyDown() && state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand()) {
				scheduleDoubleDoor(sp, level, pos);
			}
			return InteractionResult.PASS;
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> tickDoors());
		registerTests();
	}

	// ---------------------------------------------------------------- dubbele deuren

	private record PendingDoor(ServerPlayer player, ServerLevel level, BlockPos pos, BlockPos partner, boolean wasOpen) {
	}

	private static final List<PendingDoor> PENDING = new java.util.ArrayList<>();

	/** Onthoudt de klik; aan het eind van de tick (na vanilla) krijgt de partnerdeur dezelfde stand. */
	private static void scheduleDoubleDoor(ServerPlayer player, ServerLevel level, BlockPos pos) {
		BlockState before = level.getBlockState(pos);
		BlockPos partner = partnerDoor(level, pos, before);
		if (partner != null) PENDING.add(new PendingDoor(player, level, pos, partner, before.getValue(DoorBlock.OPEN)));
	}

	private static void tickDoors() {
		if (PENDING.isEmpty()) return;
		for (PendingDoor d : List.copyOf(PENDING)) {
			BlockState now = d.level().getBlockState(d.pos());
			if (!(now.getBlock() instanceof DoorBlock) || now.getValue(DoorBlock.OPEN) == d.wasOpen()) continue;
			BlockState ps = d.level().getBlockState(d.partner());
			if (ps.getBlock() instanceof DoorBlock pd && ps.getValue(DoorBlock.OPEN) != now.getValue(DoorBlock.OPEN)) {
				pd.setOpen(d.player(), d.level(), ps, d.partner(), now.getValue(DoorBlock.OPEN));
			}
		}
		PENDING.clear();
	}

	/** De andere deur van een dubbele deur: zelfde richting, tegengestelde scharnier, zelfde stand. */
	static @Nullable BlockPos partnerDoor(ServerLevel level, BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof DoorBlock)) return null;
		Direction facing = state.getValue(DoorBlock.FACING);
		DoorHingeSide hinge = state.getValue(DoorBlock.HINGE);
		Direction first = hinge == DoorHingeSide.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
		for (Direction side : List.of(first, first.getOpposite())) {
			BlockPos p = pos.relative(side);
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof DoorBlock d && d.type().canOpenByHand()
					&& s.getValue(DoorBlock.FACING) == facing
					&& s.getValue(DoorBlock.HINGE) != hinge
					&& s.getValue(DoorBlock.HALF) == state.getValue(DoorBlock.HALF)
					&& s.getValue(DoorBlock.OPEN) == state.getValue(DoorBlock.OPEN)) {
				return p;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- zitten

	private static boolean trySit(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state, BlockHitResult hit) {
		if (p.isShiftKeyDown() || p.isPassenger() || !p.getMainHandItem().isEmpty() || !p.getOffhandItem().isEmpty()) return false;
		Vec3 seat = seatPos(state, pos);
		if (seat == null) return false;
		// Niet vanaf ver weg of naar het plafond klimmen
		if (p.position().distanceTo(seat) > 3.5 || seat.y - 0.5 > p.getY() + 0.6) return false;
		// Hoofdruimte en niemand anders op deze plek
		if (level.getBlockState(pos.above()).isCollisionShapeFullBlock(level, pos.above())) return false;
		if (!level.getEntitiesOfClass(Seat.class, new net.minecraft.world.phys.AABB(pos)).isEmpty()) return false;
		Seat s = new Seat(level, seat, pos);
		level.addFreshEntity(s);
		p.startRiding(s, true, true);
		return true;
	}

	/** Zitpositie (onderkant-midden), of null als je hier niet kunt zitten. */
	static @Nullable Vec3 seatPos(BlockState state, BlockPos pos) {
		Vec3 c = Vec3.atBottomCenterOf(pos);
		if (state.getBlock() instanceof StairBlock) {
			if (state.getValue(StairBlock.HALF) != Half.BOTTOM) return null;
			Direction dir = state.getValue(StairBlock.FACING);
			// Iets naar voren (weg van de rugleuning)
			return new Vec3(c.x - dir.getStepX() * 0.25, c.y, c.z - dir.getStepZ() * 0.25);
		}
		if (state.getBlock() instanceof SlabBlock) {
			return state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM ? c : null;
		}
		return null;
	}

	/** Onzichtbare stoel; ruimt zichzelf op zodra niemand er meer op zit of de trap weg is. */
	static final class Seat extends AreaEffectCloud {
		private final BlockPos block;

		Seat(ServerLevel level, Vec3 pos, BlockPos block) {
			super(level, pos.x, pos.y, pos.z);
			this.block = block;
			this.setNoGravity(true);
			this.setPermanentlyInvulnerable(true);
			this.setInvisible(true);
			this.setWaitTime(0);
			this.setRadius(0);
			// Begrensd, zodat er na een herstart niets blijft hangen
			this.setDuration(24000);
		}

		@Override
		public void tick() {
			Entity rider = this.getFirstPassenger();
			if (rider == null || this.level().getBlockState(this.block).isAir()) {
				this.discard();
				return;
			}
			this.setDuration(24000);
			super.tick();
		}
	}

	// ---------------------------------------------------------------- oogsten

	/** Oogst een rijp gewas en plant het meteen opnieuw. @return true als er geoogst is */
	static boolean harvest(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state) {
		ItemStack hand = p.getMainHandItem();
		if (!hand.isEmpty() && !hand.is(ItemTags.HOES)) return false;
		BlockState replant;
		Block block = state.getBlock();
		if (block instanceof CropBlock crop) {
			// Fakkelbloem wordt bij rijpheid een bloem; die laten we met rust
			if (!crop.isMaxAge(state) || block == net.minecraft.world.level.block.Blocks.TORCHFLOWER_CROP) return false;
			replant = crop.getStateForAge(0);
		} else if (block instanceof NetherWartBlock) {
			if (state.getValue(NetherWartBlock.AGE) < NetherWartBlock.MAX_AGE) return false;
			replant = state.setValue(NetherWartBlock.AGE, 0);
		} else if (block instanceof CocoaBlock) {
			if (state.getValue(CocoaBlock.AGE) < CocoaBlock.MAX_AGE) return false;
			replant = state.setValue(CocoaBlock.AGE, 0);
		} else {
			return false;
		}
		List<ItemStack> drops = Block.getDrops(state, level, pos, null, p, hand);
		// Eén zaadje gaat terug de grond in
		ItemStack seed = new ItemStack(block.asItem());
		for (ItemStack d : drops) {
			if (!seed.isEmpty() && ItemStack.isSameItem(d, seed)) {
				d.shrink(1);
				break;
			}
		}
		level.setBlock(pos, replant, Block.UPDATE_ALL);
		level.levelEvent(null, 2001, pos, Block.getId(state));
		for (ItemStack d : drops) {
			if (!d.isEmpty()) Block.popResource(level, pos, d);
		}
		level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.8f, 1.0f);
		return true;
	}

	// ---------------------------------------------------------------- zelftests

	private static void registerTests() {
		SelfTests.register("gemak.oogsten", level -> {
			net.fabricmc.fabric.api.entity.FakePlayer p = net.fabricmc.fabric.api.entity.FakePlayer.get(level);
			p.getInventory().clearContent();
			BlockPos pos = new BlockPos(40, 120, 40);
			level.setBlock(pos.below(), nl.libreria.core.Ui.block("farmland"), 3);
			CropBlock wheat = (CropBlock) net.minecraft.world.level.block.Blocks.WHEAT;
			level.setBlock(pos, wheat.getStateForAge(wheat.getMaxAge()), 3);
			SelfTests.check(harvest(p, level, pos, level.getBlockState(pos)), "rijpe tarwe geoogst");
			SelfTests.check(wheat.getAge(level.getBlockState(pos)) == 0, "herplant op leeftijd 0");
			SelfTests.check(!harvest(p, level, pos, level.getBlockState(pos)), "onrijp: niets");
			level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(pos.below(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
		});
		SelfTests.register("gemak.zitten-en-deuren", level -> {
			BlockState stair = nl.libreria.core.Ui.block("oak_stairs");
			SelfTests.check(seatPos(stair, BlockPos.ZERO) != null, "op een trap kun je zitten");
			SelfTests.check(seatPos(stair.setValue(StairBlock.HALF, Half.TOP), BlockPos.ZERO) == null, "omgekeerde trap niet");
			SelfTests.check(seatPos(nl.libreria.core.Ui.block("stone"), BlockPos.ZERO) == null, "steen niet");
			BlockPos a = new BlockPos(50, 120, 50);
			BlockState door = nl.libreria.core.Ui.block("oak_door").setValue(DoorBlock.FACING, Direction.NORTH);
			BlockPos b = a.relative(Direction.NORTH.getClockWise());
			level.setBlock(a, door.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT), 2);
			level.setBlock(b, door.setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT), 2);
			SelfTests.check(b.equals(partnerDoor(level, a, level.getBlockState(a))), "partner van linker deur");
			SelfTests.check(a.equals(partnerDoor(level, b, level.getBlockState(b))), "partner van rechter deur");
			level.setBlock(a, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
			level.setBlock(b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
		});
	}
}
