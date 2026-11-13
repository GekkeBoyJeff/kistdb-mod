package nl.libreria.waystones;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** For the screenshots (CI only): a waystone with a few others on the list, more looks, the windows, a village's square. */
public final class WaystonesDebug {
	private static final List<String> MADE = new ArrayList<>();
	private static @Nullable BlockPos stone;

	private WaystonesDebug() {
	}

	/** A waystone at {@code lower} as if the player placed it (altar towards {@code front}), and three others they found. */
	public static void scene(ServerPlayer p, BlockPos lower, Direction front) {
		ServerLevel level = p.level();
		Stones.Stone s = WaystonesTests.place(level, p, lower, front);
		s.name = "Riverside";
		stone = lower;
		String[][] others = {{"Oakbrook", "village", "820", "640"}, {"Anna's tower", "Anna", "-410", "1290"}, {"Cliffside", "Sam", "1900", "-300"}};
		for (String[] o : others) {
			Stones.Stone x = new Stones.Stone();
			x.dim = Stones.dim(level);
			x.x = lower.getX() + Integer.parseInt(o[2]);
			x.y = lower.getY();
			x.z = lower.getZ() + Integer.parseInt(o[3]);
			x.name = o[0];
			x.village = o[1].equals("village");
			if (!x.village) {
				x.owner = UUID.nameUUIDFromBytes(o[1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
				x.ownerName = o[1];
			}
			x.made = System.currentTimeMillis() - 3_600_000L;
			Stones.STORE.get().stones.put(x.key(), x);
			Stones.discover(p, x);
			MADE.add(x.key());
		}
		Stones.STORE.markDirty();
	}

	/** The list, as when you right-click the waystone. */
	public static void list(ServerPlayer p) {
		Stones.Stone s = stone == null ? null : Stones.at(p.level(), stone);
		if (s != null) WaystonePlaces.travelFrom(p, s);
	}

	public static void settings(ServerPlayer p) {
		Stones.Stone s = stone == null ? null : Stones.at(p.level(), stone);
		if (s != null) WaystonesWindows.settings(p, s);
	}

	/** Its window once it's on everyone's list (and back to open after). */
	public static void settingsShared(ServerPlayer p) {
		Stones.Stone s = stone == null ? null : Stones.at(p.level(), stone);
		if (s == null) return;
		s.shared = true;
		WaystonesWindows.settings(p, s);
		s.shared = false;
	}

	/** A crafting table window with a waystone, a red dye and a glow ink sac in the grid (the result: a red, glowing one). */
	public static void craftColour(ServerPlayer p) {
		var level = p.level();
		var at = p.blockPosition();
		p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new net.minecraft.world.inventory.CraftingMenu(id, inv,
				net.minecraft.world.inventory.ContainerLevelAccess.create(level, at)) {
			@Override
			public boolean stillValid(net.minecraft.world.entity.player.Player player) {
				return true;
			}
		}, net.minecraft.network.chat.Component.translatable("container.crafting")));
		if (!(p.containerMenu instanceof net.minecraft.world.inventory.CraftingMenu m)) return;
		m.getSlot(4).set(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLOW_INK_SAC));
		m.getSlot(5).set(new net.minecraft.world.item.ItemStack(WaystonesMod.WAYSTONE_ITEM));
		m.getSlot(6).set(new net.minecraft.world.item.ItemStack(nl.libreria.core.Ui.item("red_dye")));
		m.slotsChanged(m.getSlot(5).container);
		m.broadcastChanges();
	}

	public static void admin(ServerPlayer p) {
		WaystonesWindows.admin(p);
	}

	/** A stack of pearls bound to the waystone, and a waystone, in the hotbar. */
	public static void items(ServerPlayer p) {
		Stones.Stone s = stone == null ? null : Stones.at(p.level(), stone);
		if (s == null) return;
		ItemStack pearls = new ItemStack(WaystonesMod.PEARL, 8);
		PearlItem.bindAt(p, pearls, p.level(), stone);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, pearls);
		p.getInventory().add(new ItemStack(WaystonesMod.WAYSTONE_ITEM, 2));
		p.getInventory().add(new ItemStack(WaystonesMod.DARK_WAYSTONE_ITEM, 2));
	}

	/** Where the last new village put its waystone. */
	public static @Nullable BlockPos lastVillage() {
		return VillageWaystones.last;
	}

	/**
	 * Three more waystones beside the first (towards +x, three blocks apart): dark stone as it comes (cyan), light stone
	 * dyed orange, dark stone dyed lime and made matte (an ink sac).
	 */
	public static void colours(ServerPlayer p, BlockPos first, Direction front) {
		ServerLevel level = p.level();
		String[][] looks = {{"1", "", "1", "Nightfall"}, {"0", "orange", "1", "Sunstone"}, {"1", "lime", "0", "Mossgate"}};
		for (int i = 0; i < looks.length; i++) {
			BlockPos at = first.east(3 * (i + 1));
			ItemStack item = new ItemStack(looks[i][0].equals("1") ? WaystonesMod.DARK_WAYSTONE_ITEM : WaystonesMod.WAYSTONE_ITEM);
			net.minecraft.world.level.block.state.BlockState st = WaystonesMod.WAYSTONE.defaultBlockState().setValue(WaystoneBlock.FACING, front);
			level.setBlock(at, st, net.minecraft.world.level.block.Block.UPDATE_ALL);
			WaystonesMod.WAYSTONE.setPlacedBy(level, at, st, p, item);
			Stones.Stone s = Stones.at(level, at);
			if (s == null) continue;
			s.dye = looks[i][1];
			s.glow = looks[i][2].equals("1");
			s.name = looks[i][3];
			MADE.add(s.key());
			EXTRA.add(at);
		}
		Stones.STORE.markDirty();
	}

	private static final List<BlockPos> EXTRA = new ArrayList<>();

	/** New villages get their square now (their chunks are loaded): the next round of the village waystones. */
	public static void buildVillages(net.minecraft.server.MinecraftServer server) {
		VillageWaystones.tick(server);
	}

	// ---------------------------------------------------------------- grand waystones

	private static final List<BlockPos> GRAND = new ArrayList<>();
	private static @Nullable BlockPos grandOne;

	/** A 5 × 5 sculk floor in the ground round {@code lower}, the room above it cleared, and a waystone on it facing {@code front}. */
	public static Stones.Stone grandFloor(ServerPlayer p, BlockPos lower, Direction front, boolean dark) {
		ServerLevel level = p.level();
		for (int[] c : Grand.places()) {
			BlockPos b = Grand.at(lower, front, c[0], c[1], c[2]);
			level.setBlock(b, c[2] < 0 ? net.minecraft.world.level.block.Blocks.SCULK.defaultBlockState()
					: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
		}
		net.minecraft.world.level.block.state.BlockState st = WaystonesMod.WAYSTONE.defaultBlockState().setValue(WaystoneBlock.FACING, front);
		level.setBlock(lower, st, net.minecraft.world.level.block.Block.UPDATE_ALL);
		WaystonesMod.WAYSTONE.setPlacedBy(level, lower, st, p, new ItemStack(dark ? WaystonesMod.DARK_WAYSTONE_ITEM : WaystonesMod.WAYSTONE_ITEM));
		Stones.Stone s = Stones.at(level, lower);
		MADE.add(s.key());
		GRAND.add(lower);
		return s;
	}

	/** A grand waystone at {@code lower} (on its floor), in this look and name; {@code rise}: it comes up out of the ground now. */
	public static void grand(ServerPlayer p, BlockPos lower, Direction front, boolean dark, String dye, boolean glow, String name, boolean rise) {
		Stones.Stone s = grandFloor(p, lower, front, dark);
		s.dye = dye;
		s.glow = glow;
		s.name = name;
		Grand.build(p.level(), lower, front, s, rise);
		grandOne = lower;
	}

	/** The heart in the hand of the player, as they're about to use it. */
	public static void heartInHand(ServerPlayer p) {
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(WaystonesMod.HEART));
	}

	/** Uses the heart in their hand on the waystone at {@code lower} (as a right-click would). */
	public static void useHeart(ServerPlayer p, BlockPos lower) {
		ItemStack h = p.getMainHandItem();
		if (!h.is(WaystonesMod.HEART)) h = new ItemStack(WaystonesMod.HEART);
		Grand.raise(p, p.level(), lower, h);
		Stones.Stone s = Stones.at(p.level(), lower);
		if (s != null && s.grand) grandOne = lower;
	}

	/** The last grand waystone's window (theirs: with Take it down). */
	public static void grandSettings(ServerPlayer p) {
		Stones.Stone s = grandOne == null ? null : Stones.at(p.level(), grandOne);
		if (s != null) withFinders(s, () -> WaystonesWindows.settings(p, s));
	}

	/** Who has it on their list (as its land's owner sees it): two have it, one was taken off. */
	public static void grandFinders(ServerPlayer p) {
		Stones.Stone s = grandOne == null ? null : Stones.at(p.level(), grandOne);
		if (s != null) withFinders(s, () -> WaystoneCanvas.finders(p, s, false));
	}

	/** Anna and Bram found it, Tess was taken off, while the window is made (and gone after). */
	private static void withFinders(Stones.Stone s, Runnable open) {
		java.util.Map<java.util.UUID, String> finders = s.finders, off = s.takenOff;
		s.finders = new java.util.LinkedHashMap<>();
		s.takenOff = new java.util.LinkedHashMap<>();
		for (String n : new String[] {"Anna", "Bram"}) s.finders.put(java.util.UUID.nameUUIDFromBytes(n.getBytes()), n);
		s.takenOff.put(java.util.UUID.nameUUIDFromBytes("Tess".getBytes()), "Tess");
		try {
			open.run();
		} finally {
			s.finders = finders;
			s.takenOff = off;
		}
	}

	/** Asked before it's taken down. */
	public static void grandTakeDown(ServerPlayer p) {
		Stones.Stone s = grandOne == null ? null : Stones.at(p.level(), grandOne);
		if (s != null) WaystoneCanvas.askTakeDown(p, s);
	}

	/** The list, as when you right-click the last grand waystone (every trip free). */
	public static void grandList(ServerPlayer p) {
		Stones.Stone s = grandOne == null ? null : Stones.at(p.level(), grandOne);
		if (s != null) WaystonePlaces.travelFrom(p, s);
	}

	public static void clearGrand(ServerPlayer p) {
		for (BlockPos b : GRAND) {
			Stones.Stone s = Stones.at(p.level(), b);
			if (s != null && s.grand) Grand.takeDown(s, p.level(), null);
			else WaystonesTests.clear(p.level(), b);
			Stones.removed(p.level(), b, "debug");
		}
		GRAND.clear();
		grandOne = null;
	}

	public static void clear(ServerPlayer p) {
		if (stone != null) WaystonesTests.clear(p.level(), stone);
		for (BlockPos at : EXTRA) WaystonesTests.clear(p.level(), at);
		EXTRA.clear();
		stone = null;
		for (String k : MADE) Stones.STORE.get().stones.remove(k);
		MADE.clear();
		Stones.STORE.markDirty();
	}
}
