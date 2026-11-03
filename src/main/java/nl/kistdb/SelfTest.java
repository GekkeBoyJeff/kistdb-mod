package nl.kistdb;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Alleen voor CI (-Dkistdb.selftest=true): bouwt een dubbele kist, leest en sorteert hem, en logt het resultaat. */
final class SelfTest {
	private SelfTest() {
	}

	static void run(MinecraftServer server) {
		List<String> fails = new ArrayList<>();
		try {
			ServerLevel level = server.overworld();
			BlockPos a = new BlockPos(8, 120, 8);
			BlockPos b = a.east();
			level.getChunk(a.getX() >> 4, a.getZ() >> 4);
			BlockState chest = Sounds.block("chest");
			// facing=north + type=left: de partner hoort oostelijk (+x) te liggen
			level.setBlock(a, chest.setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT), 3);
			level.setBlock(b, chest.setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT), 3);

			Container ca = (Container) level.getBlockEntity(a);
			Container cb = (Container) level.getBlockEntity(b);
			ca.setItem(0, new ItemStack(Items.STONE, 40));
			ca.setItem(5, new ItemStack(Items.DIRT, 10));
			ca.setItem(9, new ItemStack(Items.ENDER_PEARL, 10));
			cb.setItem(3, new ItemStack(Items.STONE, 40));
			cb.setItem(4, new ItemStack(Items.DIAMOND_SWORD));
			cb.setItem(6, new ItemStack(Items.ENDER_PEARL, 10));

			check(fails, "isStorage", Containers.isStorage(level.getBlockEntity(a)));
			check(fails, "partnerOf(a)==b", b.equals(Containers.partnerOf(level, a)));
			check(fails, "keyOf(b)==a", a.equals(Containers.keyOf(level, b)));
			List<ItemStack> read = Containers.read(level, a);
			check(fails, "read 6 stacks", read != null && read.size() == 6);
			check(fails, "summary stone=80", read != null && Integer.valueOf(80).equals(Containers.summarize(read).get("minecraft:stone")));
			check(fails, "boxOf 2 wide", Containers.boxOf(level, a).getXsize() == 2.0);

			Container both = new CompoundContainer(ca, cb);
			check(fails, "sort ok", Sorter.sortDirect(both));
			int stone = 0, pearls = 0, used = 0;
			for (int i = 0; i < both.getContainerSize(); i++) {
				ItemStack s = both.getItem(i);
				if (s.isEmpty()) continue;
				used++;
				if (s.is(Items.STONE)) stone += s.getCount();
				if (s.is(Items.ENDER_PEARL)) {
					pearls += s.getCount();
					check(fails, "pearl stack <= 16", s.getCount() <= 16);
				}
			}
			check(fails, "stone preserved", stone == 80);
			check(fails, "pearls preserved", pearls == 20);
			check(fails, "merged into 6 stacks (64+16 stone, 16+4 pearls, dirt, sword)", used == 6);
			check(fails, "first slot filled", !both.getItem(0).isEmpty());
			check(fails, "match diamond", Search.matches(Items.DIAMOND, "diamond"));
			check(fails, "match english name", Search.matches(Items.OAK_LOG, "oak log"));
			check(fails, "icon", !CraftButton.icon().isEmpty());

			level.setBlock(a, Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
		} catch (Throwable t) {
			KistDB.LOG.error("KISTDB-SELFTEST exception", t);
			fails.add("exception: " + t);
		}
		if (fails.isEmpty()) {
			KistDB.LOG.info("KISTDB-SELFTEST OK");
		} else {
			KistDB.LOG.error("KISTDB-SELFTEST FAIL {}", fails);
		}
	}

	private static void check(List<String> fails, String name, boolean ok) {
		if (!ok) fails.add(name);
	}
}
