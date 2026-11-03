package nl.kistdb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/** Alles rond het herkennen en live uitlezen van kisten, tonnen en shulker boxes. */
public final class Containers {
	private Containers() {
	}

	/** Kist (ook gevangen en koperen kisten), ton of shulker box. Geen enderkist: die is per speler. */
	public static boolean isStorage(@Nullable BlockEntity be) {
		return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity;
	}

	/** De andere helft van een dubbele kist, of null. */
	public static @Nullable BlockPos partnerOf(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
			if (level.getBlockState(other).is(state.getBlock())) return other.immutable();
		}
		return null;
	}

	/** Een dubbele kist wordt bewaard onder de laagste van zijn twee posities, zodat beide helften dezelfde entry vinden. */
	public static BlockPos keyOf(Level level, BlockPos pos) {
		BlockPos other = partnerOf(level, pos);
		if (other != null && other.compareTo(pos) < 0) return other;
		return pos.immutable();
	}

	/** Het gebied van de container (beide helften bij een dubbele kist). */
	public static AABB boxOf(Level level, BlockPos key) {
		AABB box = new AABB(key);
		BlockPos other = partnerOf(level, key);
		if (other != null) box = box.minmax(new AABB(other));
		return box;
	}

	public static boolean isLoaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	public static String blockId(Level level, BlockPos pos) {
		return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
	}

	/** Leest de inhoud live (beide helften). null als het geen (geopende) container meer is. */
	public static @Nullable List<ItemStack> read(ServerLevel level, BlockPos key) {
		List<ItemStack> out = new ArrayList<>();
		if (!readInto(level, key, out)) return null;
		BlockPos other = partnerOf(level, key);
		if (other != null) readInto(level, other, out);
		return out;
	}

	private static boolean readInto(ServerLevel level, BlockPos pos, List<ItemStack> out) {
		BlockEntity be = level.getBlockEntity(pos);
		if (!isStorage(be)) return false;
		// Een nog niet geopende buitkist niet uitlezen: dat zou de buit genereren
		if (be instanceof RandomizableContainer r && r.getLootTable() != null) return true;
		Container c = (Container) be;
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (!s.isEmpty()) out.add(s.copy());
		}
		return true;
	}

	/** Vat stacks samen tot item-ID → aantal (voor de opgeslagen momentopname). */
	public static Map<String, Integer> summarize(List<ItemStack> stacks) {
		Map<String, Integer> map = new LinkedHashMap<>();
		for (ItemStack s : stacks) {
			map.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
		}
		return map;
	}

	/**
	 * Ververst de entry als de kist geladen is. Geeft false als de container weg is (entry mag dan weg).
	 * Bij een niet-geladen chunk blijft de momentopname staan.
	 */
	public static boolean refresh(ServerLevel level, Store.Entry entry, Store.PlayerData data) {
		BlockPos key = entry.pos();
		if (!isLoaded(level, key)) return true;
		List<ItemStack> stacks = read(level, key);
		if (stacks == null) return false;
		Map<String, Integer> now = summarize(stacks);
		if (!now.equals(entry.items)) {
			entry.items = now;
			data.markDirty();
		}
		entry.seen = System.currentTimeMillis();
		return true;
	}
}
