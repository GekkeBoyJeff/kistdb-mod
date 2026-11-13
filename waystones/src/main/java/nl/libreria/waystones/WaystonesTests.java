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
		SelfTests.register("waystones.finders", level -> {
			FakePlayer finder = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Finder"));
			FakePlayer other = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Wanderer"));
			FakePlayer owner = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Keeper"));
			BlockPos a = ground(level, 1600, 600);
			try {
				Stones.Stone s = place(level, owner, a, Direction.SOUTH);
				SelfTests.check(s != null && s.finders.isEmpty(), "its placer isn't one of who found it");
				Stones.discover(finder, s);
				Stones.discover(other, s);
				SelfTests.check(s.finders.size() == 2 && "Finder".equals(s.finders.get(finder.getUUID())), "who found it is kept on the waystone: " + s.finders);
				// Its land's owner takes one of them off: only theirs goes
				SelfTests.check(Stones.unfind(level.getServer(), s, other.getUUID()) && !Stones.known(other, s) && Stones.known(finder, s)
						&& s.finders.size() == 1, "taken off one list, the other stays");
				SelfTests.check(!Stones.discover(other, s) && !Stones.known(other, s) && !Stones.mayUse(other, s) && s.takenOff.containsKey(other.getUUID()),
						"taken off: they can't find it again by themselves");
				SelfTests.check(Stones.giveBack(level.getServer(), s, other.getUUID()) && Stones.known(other, s) && Stones.mayUse(other, s)
						&& s.takenOff.isEmpty() && s.finders.size() == 2, "given back: on their list right away, no need to find it again");
				// What became of someone's waystones
				nl.libreria.core.Players.note(finder.getUUID(), "Finder", System.currentTimeMillis(), System.currentTimeMillis());
				Stones.FOUND.get(finder).found.add("minecraft:overworld|1|2|3");
				Stones.Stone ghost = new Stones.Stone();
				ghost.x = 1;
				ghost.y = 2;
				ghost.z = 3;
				ghost.name = "Ghostford";
				Stones.remember(ghost);
				Stones.forget(ghost.key(), "its land was made anew (world refresh)");
				String said = String.join("\n", WaystoneCheck.lines(level.getServer(), "Finder").stream().map(c -> c.getString()).toList());
				SelfTests.check(said.contains("Found 2: 1 still there, 1 gone") && said.contains("Ghostford") && said.contains("world refresh"),
						"/waystones check says what became of them: " + said);
			} finally {
				Stones.removed(level, a, "self-test");
				clear(level, a);
				nl.libreria.core.Players.forget(finder.getUUID());
			}
		});
		SelfTests.register("waystones.restore", level -> {
			FakePlayer owner = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Rook"));
			BlockPos a = ground(level, 1800, 600);
			try {
				Stones.Stone s = place(level, owner, a, Direction.SOUTH);
				s.name = "Rookhaven";
				s.dye = "red";
				// Its chunk cleared outside the game: the blocks go, but it's still on the lists
				Stones.removed(level, a, "self-test");
				level.setBlock(a.above(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.setBlock(a.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.setBlock(a, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.setBlock(a.south(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				Stones.remember(s);
				SelfTests.check(!Stones.standing(level, a), "gone from the world, still on the lists");
				SelfTests.check(Stones.check(owner, s) == level && Stones.standing(level, a) && level.getBlockState(a.south()).is(WaystonesMod.ALTAR)
						&& Stones.at(level, a) == s && "red".equals(s.dye) && "Rookhaven".equals(s.name), "put back as it was when someone travels there");
				// Something else stands there now: nobody goes, nothing is paid, it stays on the lists
				Stones.removed(level, a, "self-test");
				clear(level, a);
				level.setBlock(a.south(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.setBlock(a.above(), Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
				Stones.remember(s);
				SelfTests.check(Stones.check(owner, s) == null && Stones.byKey(s.key()) == s && !Stones.standing(level, a), "a spot taken by something else: no trip");
			} finally {
				Stones.removed(level, a, "self-test");
				for (int i = 0; i < 3; i++) level.setBlock(a.above(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.setBlock(a.south(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		});
		SelfTests.register("waystones.visitors", level -> {
			FakePlayer owner = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Landlord"));
			FakePlayer guest = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Guest"));
			BlockPos a = ground(level, 2400, 600);
			try {
				Class.forName("nl.libreria.claims.ClaimsMod").getMethod("debugClaim", ServerLevel.class, net.minecraft.world.level.ChunkPos.class, UUID.class,
						String.class).invoke(null, level, net.minecraft.world.level.ChunkPos.containing(a), owner.getUUID(), "Landlord");
			} catch (ReflectiveOperationException e) {
				return; // no claims on this server: nothing to open
			}
			try {
				Stones.Stone s = place(level, owner, a, Direction.SOUTH);
				var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(a.above()), Direction.SOUTH, a.above(), false);
				java.util.function.Supplier<net.minecraft.world.InteractionResult> click = () -> net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT
						.invoker().interact(guest, level, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
				SelfTests.check(Stones.mayOpenToVisitors(owner, s) && !Stones.mayOpenToVisitors(guest, s), "its land's owner may open it to visitors");
				SelfTests.check(click.get() == net.minecraft.world.InteractionResult.FAIL, "on someone's land a visitor can't click it");
				s.visitors = true;
				SelfTests.check(click.get() == net.minecraft.world.InteractionResult.PASS, "opened to visitors: they can");
				guest.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
				SelfTests.check(click.get() == net.minecraft.world.InteractionResult.FAIL, "but they can't build against it");
				// Its window: the switch, and who has it on their list (take off, give back)
				Stones.discover(guest, s);
				String one = new WaystoneCanvas.One(owner, s, false).pictureJson();
				SelfTests.check(one.contains("Open to visitors") && one.contains("1 player"), "its window has the switch and who has it: " + one.length());
				String list = new WaystoneCanvas.Finders(owner, s, false).pictureJson();
				SelfTests.check(list.contains("Guest") && list.contains("Take off"), "who has it: Guest, to take off");
				Stones.unfind(level.getServer(), s, guest.getUUID());
				list = new WaystoneCanvas.Finders(owner, s, false).pictureJson();
				SelfTests.check(list.contains("Guest") && list.contains("Give back") && WaystoneCanvas.listsLabel(s, owner).contains("1 taken off"),
						"taken off: still in the list, to give back");
			} finally {
				Stones.removed(level, a, "self-test");
				clear(level, a);
			}
		});
		SelfTests.register("waystones.animals", level -> {
			FakePlayer rider = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Herder"));
			BlockPos a = ground(level, 2000, 600), b = ground(level, 2200, 600);
			net.minecraft.world.entity.animal.Animal cow = null;
			net.minecraft.world.entity.Mob zombie = null;
			try {
				rider.teleportTo(level, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, java.util.Set.of(), 0, 0, false);
				cow = (net.minecraft.world.entity.animal.Animal) net.minecraft.world.entity.EntityTypes.COW.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				cow.setPos(a.getX() + 2.5, a.getY(), a.getZ() + 0.5);
				level.addFreshEntity(cow);
				cow.setLeashedTo(rider, true);
				zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				zombie.setPos(a.getX() - 2.5, a.getY(), a.getZ() + 0.5);
				level.addFreshEntity(zombie);
				zombie.setLeashedTo(rider, true);
				var along = nl.libreria.core.Leashed.along(rider);
				SelfTests.check(along.size() == 1 && along.getFirst() == cow && Stones.times(along) == 2, "a cow on your lead comes along (a zombie doesn't), and the trip costs twice");
				rider.teleportTo(level, b.getX() + 0.5, b.getY(), b.getZ() + 0.5, java.util.Set.of(), 0, 0, false);
				nl.libreria.core.Leashed.bring(rider, along);
				SelfTests.check(cow.position().distanceTo(rider.position()) < 3 && cow.getLeashHolder() == rider, "the cow is there too, on the lead (" + cow.position() + ")");
				SelfTests.check(Stones.times(java.util.List.of()) == 1, "alone: the usual price");
			} finally {
				if (cow != null) cow.discard();
				if (zombie != null) zombie.discard();
			}
		});
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
				SelfTests.check(!s.dark && s.colour() == net.minecraft.world.item.DyeColor.PURPLE && !s.glow, "light stone, purple, matte as it comes");
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
				// Coloured in the crafting grid: a waystone with a dye and a glow ink sac; placed, it takes that look; broken, it drops it again
				var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 1, java.util.List.of(new ItemStack(WaystonesMod.WAYSTONE_ITEM),
						new ItemStack(nl.libreria.core.Ui.item("red_dye")), new ItemStack(Items.GLOW_INK_SAC)));
				var recipe = level.recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level);
				SelfTests.check(recipe.isPresent(), "a recipe for a waystone, a red dye and a glow ink sac");
				if (recipe.isPresent()) {
					ItemStack crafted = recipe.get().value().assemble(input);
					ItemStack expected = new ItemStack(WaystonesMod.WAYSTONE_ITEM);
					Stones.lookOn(expected, "red", true, false);
					SelfTests.check(ItemStack.isSameItemSameComponents(crafted, expected), "it makes the same item a broken red glowing one drops: "
							+ crafted.getComponentsPatch() + " / " + expected.getComponentsPatch());
					Stones.Stone fresh = new Stones.Stone();
					Stones.lookFrom(crafted, fresh);
					SelfTests.check("red".equals(fresh.dye) && fresh.glow, "placed: red, glowing");
				}
				var plain = net.minecraft.world.item.crafting.CraftingInput.of(2, 1, java.util.List.of(new ItemStack(WaystonesMod.DARK_WAYSTONE_ITEM),
						new ItemStack(nl.libreria.core.Ui.item("lime_dye"))));
				var lime = level.recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, plain, level);
				Stones.Stone limeStone = new Stones.Stone();
				if (lime.isPresent()) Stones.lookFrom(lime.get().value().assemble(plain), limeStone);
				SelfTests.check(lime.isPresent() && lime.get().value().assemble(plain).is(WaystonesMod.DARK_WAYSTONE_ITEM) && "lime".equals(limeStone.dye) && !limeStone.glow,
						"a dark one with a lime dye: lime, matte");
			} finally {
				clear(level, a);
			}
		});

		SelfTests.register("waystones.village", level -> {
			FakePlayer walker = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Walker"));
			BlockPos bell = ground(level, 1208, 1208);
			// Open grass all around (the flat world may have a village of its own here)
			for (int dx = -26; dx <= 26; dx++) {
				for (int dz = -26; dz <= 26; dz++) {
					BlockPos c = bell.offset(dx, 0, dz);
					level.getChunk(c.getX() >> 4, c.getZ() >> 4);
					for (int dy = -3; dy <= 10; dy++) {
						level.setBlock(c.above(dy), dy < -1 ? Blocks.DIRT.defaultBlockState() : dy == -1 ? Blocks.GRASS_BLOCK.defaultBlockState()
								: Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
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
				SelfTests.check(!Stones.mayShare(walker, s), "who hasn't found it can't share it");
				SelfTests.check(Stones.discover(walker, s) && Stones.knownBy(walker).contains(s), "found: on their list");
				// Its chunk made anew (the world refresh): off every list (checked on a copy, the real one stays for the rest)
				Stones.Stone copy = new Stones.Stone();
				copy.dim = s.dim;
				copy.x = s.x + 4096;
				copy.y = s.y;
				copy.z = s.z;
				copy.village = true;
				Stones.STORE.get().stones.put(copy.key(), copy);
				Stones.discover(walker, copy);
				SelfTests.check(Stones.knownBy(walker).contains(copy), "another one, found");
				long chunk = ((copy.x >> 4) & 0xFFFFFFFFL) | ((long) (copy.z >> 4) << 32);
				nl.libreria.core.Services.chunksGone(level.getServer(), copy.dim, java.util.Set.of(chunk, chunk + 1));
				SelfTests.check(!Stones.knownBy(walker).contains(copy) && Stones.knownBy(walker).contains(s), "its chunk made anew: it's gone, the other stays");
				// Who found a village's waystone may put it on everyone's list: then it's there for who never found it too
				FakePlayer stranger = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Stranger"));
				SelfTests.check(Stones.mayShare(walker, s) && !Stones.mayShare(stranger, s), "who found it may share it, a stranger not");
				s.shared = true;
				SelfTests.check(Stones.known(stranger, s) && Stones.knownBy(stranger).contains(s), "shared: on everyone's list");
				s.shared = false;
				SelfTests.check(!Stones.known(stranger, s), "not shared any more: off the stranger's list");
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
					clear(level, w);
				}
			} finally {
				for (int x = cx - 7; x <= cx + 7; x++) for (int z = cz - 7; z <= cz + 7; z++) level.setChunkForced(x, z, false);
			}
		});

		SelfTests.register("waystones.grand", level -> {
			FakePlayer mason = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Mason"));
			BlockPos lower = ground(level, 1000, 600), other = ground(level, 1300, 600);
			Direction south = Direction.SOUTH;
			try {
				for (int dx = -3; dx <= 3; dx++) {
					for (int dz = -3; dz <= 3; dz++) {
						for (int dy = 0; dy <= 10; dy++) level.setBlock(lower.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
						level.setBlock(lower.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
					}
				}
				Stones.Stone s = place(level, mason, lower, south), o = place(level, mason, other, Direction.NORTH);
				ItemStack heart = new ItemStack(WaystonesMod.HEART, 2);
				Grand.raise(mason, level, lower, heart);
				SelfTests.check(!s.grand && heart.getCount() == 2 && level.getBlockState(lower).is(WaystonesMod.WAYSTONE),
						"without a sculk floor it stays an ordinary waystone, the heart unused");
				SelfTests.check(Grand.missingFloor(level, lower, south).size() == 23, "23 blocks of sculk round its feet (not under it or its altar)");
				for (int j = 0; j < 5; j++) {
					for (int i = 0; i < 5; i++) {
						if (i == 2 && (j == 2 || j == 1)) continue;
						level.setBlock(Grand.at(lower, south, i, j, -1), Blocks.SCULK.defaultBlockState(), Block.UPDATE_ALL);
					}
				}
				Grand.raise(mason, level, lower, heart);
				SelfTests.check(s.grand && heart.getCount() == 1, "on a sculk floor it becomes a grand waystone; the heart is used");
				BlockState mid = level.getBlockState(lower);
				SelfTests.check(mid.is(WaystonesMod.GRAND) && GrandBlock.isMiddle(mid) && Stones.at(level, lower) == s && Stones.standing(level, lower),
						"the waystone's own block is the shrine's middle; it's still on the lists");
				BlockPos roof = Grand.at(lower, south, 2, 2, 7);
				SelfTests.check(level.getBlockState(roof).is(WaystonesMod.GRAND) && GrandBlock.middleOf(roof, level.getBlockState(roof)).equals(lower),
						"the roof knows where the middle is");
				SelfTests.check(Grand.at(lower, south, 2, 0, 0).equals(lower.south(2)) && level.getBlockState(lower.below()).is(WaystonesMod.GRAND),
						"the opening faces where its altar did; the floor is part of it");
				SelfTests.check(level.getBlockState(Grand.at(lower, south, 2, 2, 2)).isAir() && GrandBlock.walkOn(level.getBlockState(Grand.at(lower, south, 2, 1, 0)))
						&& GrandBlock.walkOn(level.getBlockState(Grand.at(lower, south, 2, 0, 0))) && !GrandBlock.walkOn(level.getBlockState(Grand.at(lower, south, 0, 0, 0))),
						"the heart's place is open; the opening and inside are half a block to step onto; a pillar isn't");
				SelfTests.check(WaystonesMod.GRAND.getDestroyProgress(level.getBlockState(roof), mason, level, roof) == 0, "nobody breaks it");
				BlockState seen = GrandBlock.bedrock(level.getBlockState(roof));
				SelfTests.check(!seen.is(Blocks.BARRIER) && seen.isCollisionShapeFullBlock(level, roof), "Bedrock players see its roof as real blocks (" + seen + ")");
				// Leaving from it is free; from an ordinary one it isn't
				mason.giveExperienceLevels(-100);
				mason.experienceProgress = 0;
				Vec3 at = Stones.arrival(level, s);
				mason.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(), 0, 0, false);
				SelfTests.check(Math.abs(at.y - (lower.getY() + 0.5)) < 0.01 && at.distanceTo(Vec3.atBottomCenterOf(lower)) < 1.2,
						"you arrive inside, on the plinth before the pedestal (" + at + ")");
				SelfTests.check(Stones.travel(mason, o, false, s) && near(mason, other), "leaving from a grand waystone is free, without XP");
				SelfTests.check(!Stones.travel(mason, s, false, o), "from an ordinary one it costs XP");
				// Taken down: gone, floor and all; the waystone and the nether star come back
				Grand.takeDown(s, level, mason);
				SelfTests.check(level.getBlockState(lower).isAir() && level.getBlockState(lower.below()).isAir() && level.getBlockState(roof).isAir()
						&& Stones.at(level, lower) == null, "taken down: the shrine and its floor are gone, and it's off the lists");
				SelfTests.check(mason.getInventory().countItem(Items.NETHER_STAR) == 1 && mason.getInventory().countItem(WaystonesMod.WAYSTONE_ITEM) == 1,
						"the waystone and the nether star come back");
			} finally {
				for (int dx = -3; dx <= 3; dx++) {
					for (int dz = -3; dz <= 3; dz++) for (int dy = -1; dy <= 10; dy++) level.setBlock(lower.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
				Stones.removed(level, lower, "self-test");
				clear(level, other);
				Stones.removed(level, other, "self-test");
			}
		});

		SelfTests.register("waystones.recipes", level -> {
			for (String id : new String[] {"waystone", "dark_waystone", "waystone_pearl", "waystone_heart"}) {
				SelfTests.check(level.getServer().getRecipeManager()
						.byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("libreria", id))).isPresent(), "the recipe " + id);
			}
		});
	}
}
