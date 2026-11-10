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

/** For the screenshots (CI only): a waystone with a few others on the list, the windows, a village's waystone. */
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
	}

	/** Where the last new village put its waystone. */
	public static @Nullable BlockPos lastVillage() {
		return VillageWaystones.last;
	}

	// ---------------------------------------------------------------- mock-up of the second design (CI only)

	private static final List<eu.pb4.polymer.virtualentity.api.ElementHolder> MOCK = new ArrayList<>();

	/**
	 * The third design at {@code lower} (its altar towards {@code front}): light or dark stone, the colour of a dye,
	 * glowing or not; shown to {@code p} only.
	 */
	public static void mock(ServerPlayer p, BlockPos lower, Direction front, boolean dark, String dye, boolean glow, String label) {
		ServerLevel level = p.level();
		net.minecraft.world.item.DyeColor d = net.minecraft.world.item.DyeColor.byName(dye, net.minecraft.world.item.DyeColor.PURPLE);
		int rgb = WaystoneLook.shine(d);
		var stone = new eu.pb4.polymer.virtualentity.api.ElementHolder();
		var o = WaystoneLook.obelisk(stone, dark, rgb, glow);
		o.label().setText(nl.libreria.core.Ui.text(label, rgb));
		o.rune().setLeftRotation(new org.joml.Quaternionf().rotateY((float) Math.toRadians(30)));
		net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atCenterOf(lower);
		new eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment(stone, level, () -> at);
		stone.startWatching(p);
		var altar = new eu.pb4.polymer.virtualentity.api.ElementHolder();
		var pearl = WaystoneLook.altar(altar, dark, rgb, glow, front);
		pearl.setLeftRotation(new org.joml.Quaternionf().rotateY((float) Math.toRadians(30)));
		net.minecraft.world.phys.Vec3 at2 = net.minecraft.world.phys.Vec3.atCenterOf(lower.relative(front));
		new eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment(altar, level, () -> at2);
		altar.startWatching(p);
		MOCK.add(stone);
		MOCK.add(altar);
	}

	public static void clearMock() {
		for (var h : MOCK) h.destroy();
		MOCK.clear();
	}

	public static void clear(ServerPlayer p) {
		if (stone != null) WaystonesTests.clear(p.level(), stone);
		stone = null;
		for (String k : MADE) Stones.STORE.get().stones.remove(k);
		MADE.clear();
		Stones.STORE.markDirty();
	}
}
