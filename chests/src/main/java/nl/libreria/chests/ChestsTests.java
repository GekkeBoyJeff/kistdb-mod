package nl.libreria.chests;

import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import nl.libreria.core.SelfTests;
import nl.libreria.core.Ui;

/** Zelftests van ChestsMod (alleen in de CI, op een echte server). */
final class ChestsTests {
	private ChestsTests() {
	}

	private static BlockPos[] doubleChest(ServerLevel level, BlockPos a) {
		BlockPos b = a.east();
		level.getChunk(a.getX() >> 4, a.getZ() >> 4);
		BlockState chest = Ui.block("chest");
		// facing=north + type=left: de partner hoort oostelijk (+x) te liggen
		level.setBlock(a, chest.setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT), 3);
		level.setBlock(b, chest.setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT), 3);
		return new BlockPos[]{a, b};
	}

	private static void clear(ServerLevel level, BlockPos... pos) {
		for (BlockPos p : pos) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
	}

	static void register() {
		SelfTests.register("chests.supply", level -> {
			FakePlayer p = FakePlayer.get(level);
			BlockPos at = new BlockPos(40, 120, 40);
			level.getChunk(at.getX() >> 4, at.getZ() >> 4);
			level.setBlock(at, Ui.block("chest"), 3);
			Container c = (Container) level.getBlockEntity(at);
			c.setItem(0, new ItemStack(Items.OAK_LOG, 20));
			c.setItem(1, new ItemStack(Items.TORCH, 30));
			p.teleportTo(at.getX() + 2, at.getY(), at.getZ());
			Store.track(p.getUUID(), level, at, "minecraft:chest");
			Store.Entry e = Store.get(p.getUUID()).find(Store.dimId(level), at);
			SelfTests.check(e != null && !e.supply, "a new chest is no supply chest");
			e.supply = true;
			var before = nl.libreria.core.Services.land();
			try {
				// Someone else's land: never used
				nl.libreria.core.Services.provideLand((l, pos) -> java.util.UUID.randomUUID());
				SelfTests.check(Supply.chests(p).isEmpty(), "not on your land: not used");
				// Your land: used
				nl.libreria.core.Services.provideLand((l, pos) -> p.getUUID());
				SelfTests.check(Supply.chests(p).size() == 1, "on your land: used");
				SelfTests.check(Supply.available(p, st -> st.is(Items.OAK_LOG)) == 20, "20 logs available");
				List<ItemStack> got = new java.util.ArrayList<>();
				int took = Supply.take(p, st -> st.is(Items.OAK_LOG), 8, part -> {
					got.add(part.copy());
					return ItemStack.EMPTY;
				});
				SelfTests.check(took == 8 && got.stream().mapToInt(ItemStack::getCount).sum() == 8, "took 8 logs");
				SelfTests.check(c.getItem(0).getCount() == 12, "12 logs left in the chest");
				// No room: what didn't fit goes back
				took = Supply.take(p, st -> st.is(Items.TORCH), 30, part -> part.split(Math.max(0, part.getCount() - 5)));
				SelfTests.check(took == 5 && c.getItem(1).getCount() == 25, "only what fits is taken: " + took);
			} finally {
				before.ifPresent(nl.libreria.core.Services::provideLand);
				Store.remove(p.getUUID(), e);
				clear(level, at);
			}
		});
		SelfTests.register("chests.double-chest-sort", level -> {
			BlockPos[] ab = doubleChest(level, new BlockPos(8, 120, 8));
			BlockPos a = ab[0];
			BlockPos b = ab[1];
			Container ca = (Container) level.getBlockEntity(a);
			Container cb = (Container) level.getBlockEntity(b);
			ca.setItem(0, new ItemStack(Items.STONE, 40));
			ca.setItem(5, new ItemStack(Items.DIRT, 10));
			ca.setItem(9, new ItemStack(Items.ENDER_PEARL, 10));
			cb.setItem(3, new ItemStack(Items.STONE, 40));
			cb.setItem(4, new ItemStack(Items.DIAMOND_SWORD));
			cb.setItem(6, new ItemStack(Items.ENDER_PEARL, 10));

			SelfTests.check(Containers.isStorage(level.getBlockEntity(a)), "isStorage");
			SelfTests.check(b.equals(Containers.partnerOf(level, a)), "partnerOf(a)==b");
			SelfTests.check(a.equals(Containers.keyOf(level, b)), "keyOf(b)==a");
			List<ItemStack> read = Containers.read(level, a);
			SelfTests.check(read != null && read.size() == 6, "6 stacks read");
			SelfTests.check(Integer.valueOf(80).equals(Containers.summarize(read).get("minecraft:stone")), "80 stone");
			SelfTests.check(Containers.boxOf(level, a).getXsize() == 2.0, "outline is 2 wide");

			Container both = new CompoundContainer(ca, cb);
			SelfTests.check(Sorter.sortDirect(both), "sort succeeded");
			int stone = 0, pearls = 0, used = 0;
			for (int i = 0; i < both.getContainerSize(); i++) {
				ItemStack s = both.getItem(i);
				if (s.isEmpty()) continue;
				used++;
				if (s.is(Items.STONE)) stone += s.getCount();
				if (s.is(Items.ENDER_PEARL)) {
					pearls += s.getCount();
					SelfTests.check(s.getCount() <= 16, "pearls max 16");
				}
			}
			SelfTests.check(stone == 80 && pearls == 20, "nothing lost");
			SelfTests.check(used == 6, "merged into 6 stacks");
			SelfTests.check(!both.getItem(0).isEmpty(), "first slot filled");
			clear(level, a, b);
		});

		SelfTests.register("chests.quick-stack", level -> {
			FakePlayer p = FakePlayer.get(level);
			BlockPos[] ab = doubleChest(level, new BlockPos(20, 120, 8));
			BlockPos a = ab[0];
			Container ca = (Container) level.getBlockEntity(a);
			ca.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
			p.setPos(20.5, 121, 10.5);
			p.getInventory().clearContent();
			p.getInventory().setItem(12, new ItemStack(Items.COBBLESTONE, 30));
			p.getInventory().setItem(13, new ItemStack(Items.DIRT, 5));
			p.getInventory().setItem(2, new ItemStack(Items.COBBLESTONE, 7)); // hotbar: blijft staan

			Store.track(p.getUUID(), level, Containers.keyOf(level, a), "minecraft:chest");
			int moved = QuickStack.run(p);
			SelfTests.check(moved == 30, "30 moved, got " + moved);
			SelfTests.check(p.getInventory().getItem(12).isEmpty(), "slot 12 empty");
			SelfTests.check(p.getInventory().getItem(13).getCount() == 5, "dirt stays (not in the chest)");
			SelfTests.check(p.getInventory().getItem(2).getCount() == 7, "hotbar untouched");
			SelfTests.check(Integer.valueOf(40).equals(Containers.summarize(Containers.read(level, a)).get("minecraft:cobblestone")), "chest has 40");

			// Someone else's chest (placed by them) gets nothing, even though it's in your index
			BlockPos key = Containers.keyOf(level, a);
			ChestOwners.setPlacer(level, key, java.util.UUID.nameUUIDFromBytes("someone".getBytes()));
			p.getInventory().setItem(12, new ItemStack(Items.COBBLESTONE, 3));
			SelfTests.check(QuickStack.run(p) == 0 && p.getInventory().getItem(12).getCount() == 3, "someone else's chest is skipped");
			ChestOwners.setPlacer(level, key, p.getUUID());
			SelfTests.check(ChestOwners.isOwn(p, level, key), "a chest you placed is yours");
			ChestOwners.setPlacer(level, key, null);

			Store.Entry e = Store.get(p.getUUID()).find(Store.dimId(level), Containers.keyOf(level, a));
			SelfTests.check(e != null, "chest in index");
			e.noIndex = true;
			p.getInventory().setItem(12, new ItemStack(Items.COBBLESTONE, 3));
			SelfTests.check(QuickStack.run(p) == 0, "noIndex chest is skipped");
			Store.remove(p.getUUID(), e);
			p.getInventory().clearContent();
			clear(level, ab);
		});

		SelfTests.register("chests.sort-button", level -> {
			FakePlayer p = FakePlayer.get(level);
			p.getInventory().clearContent();
			SimpleContainer box = new SimpleContainer(27);
			ChestMenu menu = ChestMenu.threeRows(1, p.getInventory(), box);
			SelfTests.check(SortButton.containerSlots(menu) == 27, "27 container slots");
			SelfTests.check(SortButton.buttonSlot(menu) == 26, "empty chest: button in the last slot");
			box.setItem(26, new ItemStack(Items.STONE));
			box.setItem(25, new ItemStack(Items.DIRT));
			box.setItem(3, new ItemStack(Items.DIRT));
			// The player's own inventory (slots after the chest) never counts
			p.getInventory().setItem(35, new ItemStack(Items.STONE));
			SelfTests.check(SortButton.buttonSlot(menu) == 24, "partly filled: last empty slot is 24");
			for (int i = 0; i < 27; i++) box.setItem(i, new ItemStack(Items.COBBLESTONE));
			SelfTests.check(SortButton.buttonSlot(menu) == -1, "full chest: no button");
			box.setItem(10, ItemStack.EMPTY);
			SelfTests.check(SortButton.buttonSlot(menu) == 10, "only slot 10 free: button there");
			ShulkerBoxMenu shulker = new ShulkerBoxMenu(2, p.getInventory(), new SimpleContainer(27));
			SelfTests.check(SortButton.supports(shulker) && SortButton.buttonSlot(shulker) == 26, "shulker box: last slot");

			// Right-click on the button: deposit matching items, never from the hotbar
			box.clearContent();
			p.getInventory().clearContent();
			box.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
			p.getInventory().setItem(12, new ItemStack(Items.COBBLESTONE, 30));
			p.getInventory().setItem(13, new ItemStack(Items.DIRT, 5));
			p.getInventory().setItem(2, new ItemStack(Items.COBBLESTONE, 7));
			int moved = SortButton.deposit(p, box);
			SelfTests.check(moved == 30, "30 deposited, got " + moved);
			SelfTests.check(p.getInventory().getItem(12).isEmpty(), "slot 12 empty");
			SelfTests.check(p.getInventory().getItem(13).getCount() == 5, "dirt stays (not in the chest)");
			SelfTests.check(p.getInventory().getItem(2).getCount() == 7, "hotbar untouched");
			SelfTests.check(box.getItem(0).is(Items.COBBLESTONE) && box.getItem(0).getCount() == 40, "chest has 40");
			SelfTests.check(SortButton.buttonSlot(menu) == 26, "button back in the last slot");
			p.getInventory().clearContent();
		});
	}
}
