package nl.libreria.waystones;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.SelfTests;

/** Self-tests of waystones: placing, finding, the price, pearls, private ones, breaking, villages (CI only). */
final class WaystonesTests {
	private WaystonesTests() {
	}

	static BlockPos ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
	}

	/** As if {@code by} placed a waystone at {@code pos}, its altar towards {@code front}. */
	static Stones.Stone place(ServerLevel level, ServerPlayer by, BlockPos pos, Direction front) {
		BlockState st = WaystonesMod.WAYSTONE.defaultBlockState().setValue(WaystoneBlock.FACING, front);
		level.setBlock(pos, st, Block.UPDATE_ALL);
		WaystonesMod.WAYSTONE.setPlacedBy(level, pos, st, by, new ItemStack(WaystonesMod.WAYSTONE_ITEM));
		return Stones.at(level, pos);
	}

	static void clear(ServerLevel level, BlockPos lower) {
		level.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	private static boolean near(ServerPlayer p, BlockPos stone) {
		return p.position().distanceTo(Vec3.atBottomCenterOf(stone)) < 3.5 && p.level().getBlockState(p.blockPosition()).isAir();
	}

	static void register() {
		SelfTests.register("waystones.travel", level -> {
			FakePlayer mason = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Mason"));
			FakePlayer anna = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Anna"));
			BlockPos a = ground(level, 600, 600), b = ground(level, 800, 600);
			try {
				Stones.Stone sa = place(level, mason, a, Direction.SOUTH), sb = place(level, anna, b, Direction.NORTH);
				SelfTests.check(sa != null && sb != null && Stones.owns(mason, sa) && !Stones.owns(mason, sb), "a placed waystone is its placer's");
				SelfTests.check(level.getBlockState(a.above(2)).is(WaystonesMod.WAYSTONE) && level.getBlockState(a.above(2)).getValue(WaystoneBlock.PART) == 2
						&& WaystoneBlock.lowerOf(level, a.above(2), level.getBlockState(a.above(2))).equals(a), "three blocks tall");
				SelfTests.check(level.getBlockState(a.south()).is(WaystonesMod.ALTAR) && WaystoneBlock.lowerOf(level, a.south(), level.getBlockState(a.south())).equals(a),
						"its altar in front, and a click on the altar is a click on the stone");
				// Finding
				SelfTests.check(Stones.knownBy(mason).contains(sa) && !Stones.knownBy(mason).contains(sb), "you know your own, not someone else's yet");
				SelfTests.check(Stones.discover(mason, sb) && !Stones.discover(mason, sb) && Stones.knownBy(mason).contains(sb), "found once, then known");
				// The price: a level per 1000 blocks, at most 1.5; another world the most
				Vec3 at = Stones.arrival(level, sa);
				mason.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(), 0, 0, false);
				double l = Stones.levels(mason, sb);
				SelfTests.check(l > 0.15 && l < 0.25, "200 blocks: about 0.2 levels (" + l + ")");
				Stones.Stone far = new Stones.Stone();
				far.x = 600 + 9000;
				far.z = 600;
				Stones.Stone nether = new Stones.Stone();
				nether.dim = "minecraft:the_nether";
				SelfTests.check(Stones.levels(mason, far) == Stones.MAX_LEVELS && Stones.levels(mason, nether) == Stones.MAX_LEVELS, "never more than 1.5 levels");
				// Paying with XP
				mason.giveExperienceLevels(10);
				int before = Stones.held(mason);
				SelfTests.check(Stones.travel(mason, sb, false) && near(mason, b), "travels to the other waystone (" + mason.position() + ")");
				int paid = before - Stones.held(mason);
				SelfTests.check(paid > 0 && mason.experienceLevel >= 9, "for a little XP (" + paid + " points)");
				// With an ender pearl: free
				mason.getInventory().add(new ItemStack(Items.ENDER_PEARL));
				int xp = Stones.held(mason);
				SelfTests.check(Stones.travel(mason, sa, true) && near(mason, a), "with an ender pearl");
				SelfTests.check(mason.getInventory().countItem(Items.ENDER_PEARL) == 0 && Stones.held(mason) == xp, "the pearl is used, the XP stays");
				// Without either: not
				mason.giveExperienceLevels(-100);
				mason.experienceProgress = 0;
				SelfTests.check(!Stones.travel(mason, sb, false) && !Stones.travel(mason, sb, true) && near(mason, a), "no XP and no pearl: you stay");
				// Private: the owner (and who they trust) only
				sb.secret = true;
				SelfTests.check(!Stones.mayUse(mason, sb) && Stones.mayUse(anna, sb) && !Stones.knownBy(mason).contains(sb), "a private waystone");
				sb.secret = false;
				// Waystone pearls: a stack bound to one, used from anywhere
				ItemStack pearls = new ItemStack(WaystonesMod.PEARL, 3);
				SelfTests.check(PearlItem.bound(pearls) == null && PearlItem.bindAt(mason, pearls, level, b) && sb.key().equals(PearlItem.bound(pearls)),
						"pearls are bound to a waystone, the whole stack");
				SelfTests.check(!PearlItem.bindAt(mason, pearls, level, b), "bound to that one already");
				mason.setItemInHand(InteractionHand.MAIN_HAND, pearls);
				InteractionResult r = WaystonesMod.PEARL.use(level, mason, InteractionHand.MAIN_HAND);
				SelfTests.check(r == InteractionResult.SUCCESS && near(mason, b) && mason.getMainHandItem().getCount() == 2, "a bound pearl takes you there");
				// Breaking: its other half and altar go too, and it's off everyone's list
				SelfTests.check(Stones.mayBreak(anna, sb) && !Stones.mayBreak(mason, sb) && Stones.mayBreak(mason, sa), "only its owner may break it");
				clear(level, b);
				SelfTests.check(level.getBlockState(b.above()).isAir() && level.getBlockState(b.above(2)).isAir() && level.getBlockState(b.north()).isAir()
						&& Stones.at(level, b) == null, "broken: the parts above and the altar go, and its record");
				// (Anna: Mason's pearls wait a second after one was used)
				anna.setItemInHand(InteractionHand.MAIN_HAND, mason.getMainHandItem().copy());
				SelfTests.check(WaystonesMod.PEARL.use(level, anna, InteractionHand.MAIN_HAND) == InteractionResult.FAIL && anna.getMainHandItem().getCount() == 2,
						"a pearl of a broken waystone takes you nowhere");
			} finally {
				clear(level, a);
				clear(level, b);
			}
		});

		SelfTests.register("waystones.paint", level -> {
			FakePlayer mason = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Painter"));
			FakePlayer other = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Passerby"));
			BlockPos a = ground(level, 640, 680);
			try {
				Stones.Stone s = place(level, mason, a, Direction.SOUTH);
				SelfTests.check(!s.dark && s.colour() == net.minecraft.world.item.DyeColor.PURPLE && s.glow, "light stone, purple, glowing as it comes");
				ItemStack red = new ItemStack(nl.libreria.core.Ui.item("red_dye"), 2);
				SelfTests.check(Stones.paint(mason, level, a, red) && "red".equals(s.dye) && red.getCount() == 1, "a dye colours it (and is used)");
				SelfTests.check(Stones.paint(mason, level, a, red) && red.getCount() == 1, "the same dye again: nothing used");
				ItemStack ink = new ItemStack(Items.INK_SAC), glow = new ItemStack(Items.GLOW_INK_SAC);
				SelfTests.check(Stones.paint(mason, level, a, ink) && !s.glow && Stones.paint(mason, level, a, glow) && s.glow, "an ink sac: matte; a glow ink sac: it glows");
				ItemStack blue = new ItemStack(nl.libreria.core.Ui.item("blue_dye"));
				SelfTests.check(Stones.paint(other, level, a, blue) && "red".equals(s.dye) && blue.getCount() == 1, "someone else can't");
				SelfTests.check(!Stones.paint(mason, level, a, new ItemStack(Items.STICK)), "a stick is no paint");
				clear(level, a);
				BlockState st = WaystonesMod.WAYSTONE.defaultBlockState().setValue(WaystoneBlock.FACING, Direction.SOUTH);
				level.setBlock(a, st, Block.UPDATE_ALL);
				WaystonesMod.WAYSTONE.setPlacedBy(level, a, st, mason, new ItemStack(WaystonesMod.DARK_WAYSTONE_ITEM));
				Stones.Stone d = Stones.at(level, a);
				SelfTests.check(d != null && d.dark && d.colour() == net.minecraft.world.item.DyeColor.CYAN
						&& WaystoneBlock.item(level, a).is(WaystonesMod.DARK_WAYSTONE_ITEM), "a dark waystone: cyan as it comes, and drops as itself");
			} finally {
				clear(level, a);
			}
		});

		SelfTests.register("waystones.village", level -> {
			FakePlayer walker = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Walker"));
			BlockPos bell = ground(level, 1208, 1208);
			level.setBlock(bell, Blocks.BELL.defaultBlockState(), Block.UPDATE_ALL);
			BlockPos lower = null;
			try {
				VillageWaystones.last = null;
				VillageWaystones.Pending p = new VillageWaystones.Pending();
				p.x = bell.getX();
				p.y = bell.getY();
				p.z = bell.getZ();
				p.style = "village_desert";
				SelfTests.check(VillageWaystones.build(level, p) && VillageWaystones.last != null, "a waystone on a square by the bell");
				lower = VillageWaystones.last;
				BlockState st = level.getBlockState(lower);
				SelfTests.check(st.is(WaystonesMod.WAYSTONE) && st.getValue(WaystoneBlock.VILLAGE) && level.getBlockState(lower.above(2)).is(WaystonesMod.WAYSTONE),
						"a village's waystone, three blocks tall (" + lower + ")");
				BlockPos altar = lower.relative(st.getValue(WaystoneBlock.FACING));
				SelfTests.check(level.getBlockState(altar).is(WaystonesMod.ALTAR) && altar.distSqr(bell) < lower.distSqr(bell), "its altar faces the bell");
				SelfTests.check(level.getBlockState(lower.below()).is(Blocks.CHISELED_SANDSTONE) || level.getBlockState(lower.below()).is(Blocks.SMOOTH_SANDSTONE)
						|| level.getBlockState(lower.below()).is(nl.libreria.core.Ui.block("orange_terracotta").getBlock()), "on a desert square (" + level.getBlockState(lower.below()) + ")");
				Stones.Stone s = Stones.at(level, lower);
				SelfTests.check(s != null && s.village && s.owner == null && s.colour() == net.minecraft.world.item.DyeColor.CYAN && !s.dark,
						"in the village's look: light stone, cyan");
				SelfTests.check(!Stones.known(walker, s), "private to each player until they find it");
				SelfTests.check(Stones.discover(walker, s) && Stones.knownBy(walker).contains(s), "found: on their list");
				SelfTests.check(!Stones.mayBreak(walker, s), "only an operator may break a village's waystone");
				// Not on a house: a square never goes over anything built
				SelfTests.check(Plaza.fits(level, bell.east(20), Direction.WEST, 3) >= 0 && !Plaza.natural(Blocks.OAK_PLANKS.defaultBlockState())
						&& !Plaza.natural(Blocks.FARMLAND.defaultBlockState()) && Plaza.natural(Blocks.GRASS_BLOCK.defaultBlockState()), "open grass fits; planks and fields don't");
			} finally {
				if (lower != null) {
					clear(level, lower);
					for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int dy = -1; dy <= 3; dy++) {
						BlockPos q = lower.offset(dx, dy, dz);
						level.setBlock(q, dy < 0 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
					}
				}
				level.setBlock(bell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		});

		SelfTests.register("waystones.generated", level -> {
			// A real new village (the game's /place structure): its waystone and square come once that part is loaded
			int cx = 3000 >> 4, cz = 3000 >> 4;
			for (int x = cx - 7; x <= cx + 7; x++) for (int z = cz - 7; z <= cz + 7; z++) level.setChunkForced(x, z, true);
			try {
				for (int x = cx - 7; x <= cx + 7; x++) for (int z = cz - 7; z <= cz + 7; z++) level.getChunk(x, z);
				VillageWaystones.last = null;
				BlockPos at = ground(level, 3000, 3000);
				var server = level.getServer();
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level),
						"place structure minecraft:village_plains " + at.getX() + " " + at.getY() + " " + at.getZ());
				VillageWaystones.tick(server);
				BlockPos w = VillageWaystones.last;
				SelfTests.check(w != null && level.getBlockState(w).is(WaystonesMod.WAYSTONE) && level.getBlockState(w).getValue(WaystoneBlock.VILLAGE),
						"a new village has a waystone (" + w + ")");
				if (w != null) {
					SelfTests.check(level.getBlockState(w.above(2)).is(WaystonesMod.WAYSTONE)
							&& level.getBlockState(w.relative(level.getBlockState(w).getValue(WaystoneBlock.FACING))).is(WaystonesMod.ALTAR), "whole, with its altar");
					SelfTests.check(VillageWaystones.STORE.get().villages.isEmpty(), "and the village is done");
					clear(level, w);
				}
			} finally {
				for (int x = cx - 7; x <= cx + 7; x++) for (int z = cz - 7; z <= cz + 7; z++) level.setChunkForced(x, z, false);
			}
		});

		SelfTests.register("waystones.recipes", level -> {
			for (String id : new String[] {"waystone", "dark_waystone", "waystone_pearl"}) {
				SelfTests.check(level.getServer().getRecipeManager()
						.byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("libreria", id))).isPresent(), "the recipe " + id);
			}
		});
	}
}
