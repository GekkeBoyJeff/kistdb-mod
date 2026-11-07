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
	public static final Module MODULE = Module.register("comfort", "Comfort", Items.OAK_DOOR,
			"Double doors, sitting on stairs,", "right-click harvesting, and nights", "that run instead of jump.").in(nl.libreria.core.Domain.YOU, "Small comforts");
	public static final Setting.Toggle DOORS = MODULE.toggle("deuren", "Double doors together", true, Items.OAK_DOOR,
			"Open one door of a double door and", "the other one opens with it.", "Hold {sneak}: only this one.");
	public static final Setting.Toggle SIT = MODULE.toggle("zitten", "Sit on stairs and slabs", true, Items.OAK_STAIRS,
			"Right-click a stair or bottom slab with", "empty hands to sit. {sneak}: stand up.");
	public static final Setting.Toggle HARVEST = MODULE.toggle("oogsten", "Right-click harvesting", true, Items.WHEAT,
			"Right-click a ripe crop (empty hand or", "hoe) to harvest and replant it at once.");

	@Override
	public void onInitialize() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || sp.isSpectator()) {
				return InteractionResult.PASS;
			}
			ServerLevel level = (ServerLevel) world;
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof net.minecraft.world.level.block.AbstractBedBlock) {
				InteractionResult bed = Sleep.useBed(sp, level, pos, state);
				if (bed != null) return bed;
			}
			if (HARVEST.get(sp) && !sp.isShiftKeyDown() && harvest(sp, level, pos, state)) return InteractionResult.SUCCESS;
			if (SIT.get(sp) && trySit(sp, level, pos, state, hit)) return InteractionResult.SUCCESS;
			if (DOORS.get(sp) && !sp.isShiftKeyDown() && state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand()) {
				scheduleDoubleDoor(sp, level, pos);
			}
			return InteractionResult.PASS;
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickDoors();
			Sleep.tick(server);
			Spawns.tick(server);
		});
		Sleep.init();
		Stands.init();
		Spawns.init();
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(Sleep::start);
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Sleep.forget(handler.player.getUUID()));
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
		// Counts as harvesting it by hand (statistics, achievements, server goals)
		p.awardStat(net.minecraft.stats.Stats.BLOCK_MINED.get(block));
		level.levelEvent(null, 2001, pos, Block.getId(state));
		for (ItemStack d : drops) {
			if (!d.isEmpty()) Block.popResource(level, pos, d);
		}
		level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.8f, 1.0f);
		return true;
	}

	// ---------------------------------------------------------------- zelftests

	/** For the screenshots: a right-click on a bed, as if the player did it. */
	public static void debugBed(ServerPlayer p, BlockPos pos) {
		ServerLevel level = (ServerLevel) p.level();
		Sleep.useBed(p, level, pos, level.getBlockState(pos));
	}

	/** For the screenshots: lie down in a bed at night (as a click would). */
	public static String debugSleep(ServerPlayer p, BlockPos pos) {
		ServerLevel level = (ServerLevel) p.level();
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof net.minecraft.world.level.block.AbstractBedBlock bed)) return "no bed";
		return String.valueOf(p.startSleepInBed(bed, state, bed.getBedRule(level, pos), pos));
	}

	/** For the screenshots: the clock now, as text. */
	public static String debugClock(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		return Sleep.clock(level).map(c -> Sleep.clockText(Sleep.ticks(level.getServer(), c)) + " (" + Sleep.ticks(level.getServer(), c) + ")")
				.orElse("no clock");
	}

	private static void registerTests() {
		SelfTests.register("comfort.spawns", level -> {
			BlockPos feet = new BlockPos(44, 121, 40);
			var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
			level.setBlock(feet, air, 3);
			level.setBlock(feet.above(), air, 3);
			level.setBlock(feet.below(), nl.libreria.core.Ui.block("stone"), 3);
			Spawns.Spot s = Spawns.spot(level, feet);
			SelfTests.check(s != null && s.light() == 0 && s.kind() != Spawns.Kind.LIT, "dark stone under the sky: monsters can spawn: " + s);
			SelfTests.check(Spawns.marks(Vec3.ZERO, feet, s).size() == 2, "a tile and a number");
			level.setBlock(feet.below(), nl.libreria.core.Ui.block("glass"), 3);
			Spawns.Spot g = Spawns.spot(level, feet);
			SelfTests.check(g != null && g.kind() == Spawns.Kind.LIT && g.light() == 0, "glass: no monster spawns there (only the number): " + g);
			SelfTests.check(Spawns.marks(Vec3.ZERO, feet, g).size() == 1, "only the number");
			level.setBlock(feet.below(), air, 3);
			SelfTests.check(Spawns.spot(level, feet) == null, "nothing to stand on: no mark");
			SelfTests.check(Spawns.isTorch(Items.TORCH) && Spawns.isTorch(Items.SOUL_TORCH) && Spawns.isTorch(Items.REDSTONE_TORCH)
					&& !Spawns.isTorch(Items.STICK), "torches count, sticks don't");
		});
		SelfTests.register("comfort.stands", level -> {
			var s = net.minecraft.world.entity.EntityTypes.ARMOR_STAND.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			SelfTests.check(Stands.POSES.size() == 8 && Stands.POSES.containsKey("Standing"), "eight poses: " + Stands.POSES.keySet());
			Stands.POSES.get("Waving").applyTo(s);
			SelfTests.check(s.getRightArmPose().x() == -160f, "waving: the right arm up");
			Stands.Pose.of(s).mirrored().applyTo(s);
			SelfTests.check(s.getLeftArmPose().x() == -160f && s.getRightArmPose().x() == -10f, "mirrored: now the left arm");
			SelfTests.check(Stands.get(Stands.Pose.of(s), "larm").x() == -160f && Stands.get(s, "larm").x() == -160f, "a part by name");
			Stands.setSmall(s, true);
			SelfTests.check(s.isSmall(), "small");
			Stands.setSmall(s, false);
			SelfTests.check(!s.isSmall(), "big again");
			SelfTests.check(Stands.wrap(350f) == -10f && Stands.wrap(-200f) == 160f && Stands.wrap(90f) == 90f, "angles -180..180");
			// an item frame: sneak + right-click with an empty hand hides the frame
			BlockPos wall = new BlockPos(48, 121, 44);
			level.setBlock(wall, nl.libreria.core.Ui.block("stone"), 3);
			var frame = new net.minecraft.world.entity.decoration.ItemFrame(level, wall.north(), net.minecraft.core.Direction.NORTH);
			frame.setItem(new ItemStack(Items.DIAMOND_SWORD));
			level.addFreshEntity(frame);
			var fp = net.fabricmc.fabric.api.entity.FakePlayer.get(level);
			fp.getInventory().clearContent();
			fp.setPos(48.5, 121, 41.5);
			fp.setShiftKeyDown(true);
			try {
				var r = net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(fp, level, InteractionHand.MAIN_HAND, frame, null);
				SelfTests.check(r.consumesAction() && frame.isInvisible(), "sneak + right-click hides the frame: " + r);
				// Hidden, it's locked: a click goes through to the chest behind; the item doesn't turn, nothing is placed
				fp.setShiftKeyDown(false);
				level.setBlock(wall, nl.libreria.core.Ui.block("chest"), 3);
				int turned = frame.getRotation();
				fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE));
				var through = net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(fp, level, InteractionHand.MAIN_HAND, frame, null);
				SelfTests.check(through.consumesAction() && frame.getRotation() == turned && frame.getItem().is(Items.DIAMOND_SWORD) && frame.isInvisible(),
						"locked: the item stays as it is: " + through + ", rotation " + frame.getRotation());
				var opened = Stands.through(fp, level, frame);
				SelfTests.check(opened.consumesAction(), "the chest behind is used: " + opened);
				SelfTests.check(level.getBlockState(wall.north()).isAir() && fp.getMainHandItem().getCount() == 1, "and nothing is placed where it hangs");
				var off = net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(fp, level, InteractionHand.OFF_HAND, frame, null);
				SelfTests.check(off.consumesAction() && frame.getRotation() == turned, "not with the other hand either");
				fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				SelfTests.check(Stands.locked(frame), "locked while it holds something");
				frame.setItem(ItemStack.EMPTY);
				SelfTests.check(!Stands.locked(frame), "empty: not locked");
			} finally {
				frame.discard();
				level.setBlock(wall, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
			}
		});
		SelfTests.register("comfort.sleep", level -> {
			SelfTests.check("06:00".equals(Sleep.clockText(0)), "tick 0 is six in the morning");
			SelfTests.check("18:00".equals(Sleep.clockText(12000)), "12000 is six in the evening");
			SelfTests.check("15:30".equals(Sleep.clockText(24000 * 5 + 9500)), "any day: 15:30");
			SelfTests.check(Sleep.napWaitDays(100_000, 100_000, 3) == 3, "just napped: three days");
			SelfTests.check(Sleep.napWaitDays(100_000 + 24000 * 2 + 1, 100_000, 3) == 1, "a bit over two days later: one more");
			SelfTests.check(Sleep.napWaitDays(100_000 + 24000 * 3, 100_000, 3) == 0, "three days later: now");
			SelfTests.check(Sleep.napWaitDays(50_000, 100_000, 3) == 0, "time set back: now");
			SelfTests.check(Math.abs(Sleep.rate(11500, 15) * 15 * 20 - 11500) < 1, "a night takes the set seconds");
			SelfTests.check(Sleep.rate(23000, 15) > Sleep.rate(11500, 15), "a longer stretch goes faster");
			SelfTests.check(Sleep.rate(500, 15) == Sleep.rate(11500, 15), "a short stretch at the night's rate");
			var clock = Sleep.clock(level);
			SelfTests.check(clock.isPresent(), "the overworld has a clock");
			if (clock.isPresent()) {
				var server = level.getServer();
				long before = Sleep.ticks(server, clock.get());
				long night = Sleep.next(server, clock.get(), net.minecraft.world.clock.ClockTimeMarkers.NIGHT);
				long morning = Sleep.next(server, clock.get(), net.minecraft.world.clock.ClockTimeMarkers.WAKE_UP_FROM_SLEEP);
				SelfTests.check(Sleep.ticks(server, clock.get()) == before, "looking ahead leaves the clock where it was");
				SelfTests.check(night > before && night - before <= 24000, "nightfall is ahead, within a day: " + before + " -> " + night);
				SelfTests.check(morning > before && morning - before <= 24000, "morning is ahead, within a day: " + before + " -> " + morning);
				org.slf4j.LoggerFactory.getLogger("Libreria comfort").info("SLEEP-CLOCK now {} night {} morning {}", before, night, morning);
			}
		});
		SelfTests.register("comfort.harvest", level -> {
			net.fabricmc.fabric.api.entity.FakePlayer p = net.fabricmc.fabric.api.entity.FakePlayer.get(level);
			p.getInventory().clearContent();
			BlockPos pos = new BlockPos(40, 120, 40);
			level.setBlock(pos.below(), nl.libreria.core.Ui.block("farmland"), 3);
			CropBlock wheat = (CropBlock) net.minecraft.world.level.block.Blocks.WHEAT;
			level.setBlock(pos, wheat.getStateForAge(wheat.getMaxAge()), 3);
			SelfTests.check(harvest(p, level, pos, level.getBlockState(pos)), "ripe wheat harvested");
			SelfTests.check(wheat.getAge(level.getBlockState(pos)) == 0, "replanted at age 0");
			SelfTests.check(!harvest(p, level, pos, level.getBlockState(pos)), "unripe: nothing");
			level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(pos.below(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
		});
		SelfTests.register("comfort.sit-and-doors", level -> {
			BlockState stair = nl.libreria.core.Ui.block("oak_stairs");
			SelfTests.check(seatPos(stair, BlockPos.ZERO) != null, "you can sit on a stair");
			SelfTests.check(seatPos(stair.setValue(StairBlock.HALF, Half.TOP), BlockPos.ZERO) == null, "not on an upside-down stair");
			SelfTests.check(seatPos(nl.libreria.core.Ui.block("stone"), BlockPos.ZERO) == null, "not on stone");
			BlockPos a = new BlockPos(50, 120, 50);
			BlockState door = nl.libreria.core.Ui.block("oak_door").setValue(DoorBlock.FACING, Direction.NORTH);
			BlockPos b = a.relative(Direction.NORTH.getClockWise());
			level.setBlock(a, door.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT), 2);
			level.setBlock(b, door.setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT), 2);
			SelfTests.check(b.equals(partnerDoor(level, a, level.getBlockState(a))), "partner of left door");
			SelfTests.check(a.equals(partnerDoor(level, b, level.getBlockState(b))), "partner of right door");
			level.setBlock(a, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
			level.setBlock(b, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
		});
	}
}
