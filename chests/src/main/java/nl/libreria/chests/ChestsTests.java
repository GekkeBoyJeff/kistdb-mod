package nl.libreria.chests;

import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
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
		SelfTests.register("kistdb.dubbele-kist-en-sorteren", level -> {
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
			SelfTests.check(read != null && read.size() == 6, "6 stapels gelezen");
			SelfTests.check(Integer.valueOf(80).equals(Containers.summarize(read).get("minecraft:stone")), "80 steen");
			SelfTests.check(Containers.boxOf(level, a).getXsize() == 2.0, "omlijning 2 breed");

			Container both = new CompoundContainer(ca, cb);
			SelfTests.check(Sorter.sortDirect(both), "sorteren gelukt");
			int stone = 0, pearls = 0, used = 0;
			for (int i = 0; i < both.getContainerSize(); i++) {
				ItemStack s = both.getItem(i);
				if (s.isEmpty()) continue;
				used++;
				if (s.is(Items.STONE)) stone += s.getCount();
				if (s.is(Items.ENDER_PEARL)) {
					pearls += s.getCount();
					SelfTests.check(s.getCount() <= 16, "parels max 16");
				}
			}
			SelfTests.check(stone == 80 && pearls == 20, "niets kwijt");
			SelfTests.check(used == 6, "samengevoegd tot 6 stapels");
			SelfTests.check(!both.getItem(0).isEmpty(), "eerste slot gevuld");
			clear(level, a, b);
		});

		SelfTests.register("kistdb.snel-opbergen", level -> {
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
			SelfTests.check(moved == 30, "30 verplaatst, was " + moved);
			SelfTests.check(p.getInventory().getItem(12).isEmpty(), "slot 12 leeg");
			SelfTests.check(p.getInventory().getItem(13).getCount() == 5, "aarde blijft (zit niet in de kist)");
			SelfTests.check(p.getInventory().getItem(2).getCount() == 7, "hotbar ongemoeid");
			SelfTests.check(Integer.valueOf(40).equals(Containers.summarize(Containers.read(level, a)).get("minecraft:cobblestone")), "kist heeft 40");

			Store.Entry e = Store.get(p.getUUID()).find(Store.dimId(level), Containers.keyOf(level, a));
			SelfTests.check(e != null, "kist in database");
			e.noIndex = true;
			p.getInventory().setItem(12, new ItemStack(Items.COBBLESTONE, 3));
			SelfTests.check(QuickStack.run(p) == 0, "noIndex-kist wordt overgeslagen");
			Store.remove(p.getUUID(), e);
			p.getInventory().clearContent();
			clear(level, ab);
		});
	}
}
