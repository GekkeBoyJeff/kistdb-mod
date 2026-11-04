package nl.libreria.chests;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import nl.libreria.core.Services;
import nl.libreria.core.WorldStore;
import org.jetbrains.annotations.Nullable;

/**
 * Whose chest is whose, for quick stacking: your items only ever go into your own chests. A chest is yours when it
 * stands on your land; never when it stands on someone else's land (also not when they trust you); on free land when
 * you placed it (or when nobody is known to have placed it, as with chests from before this was kept).
 */
public final class ChestOwners {
	public static final class Data {
		/** "dimension|x|y|z" → who placed the container there. */
		public Map<String, UUID> placed = new HashMap<>();
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/chest_owners.json", Data.class, Data::new);

	private ChestOwners() {
	}

	private static String key(ServerLevel level, BlockPos pos) {
		return Store.dimId(level) + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
	}

	/** A player placed a block (from the mixin): if it holds items, it's theirs. */
	public static void placed(ServerPlayer p, ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof Container)) return;
		Data d = STORE.get();
		if (d.placed == null) d.placed = new HashMap<>();
		d.placed.put(key(level, pos), p.getUUID());
		STORE.markDirty();
	}

	/** For the self-test. */
	static void setPlacer(ServerLevel level, BlockPos pos, @Nullable UUID who) {
		Data d = STORE.get();
		if (d.placed == null) d.placed = new HashMap<>();
		if (who == null) d.placed.remove(key(level, pos));
		else d.placed.put(key(level, pos), who);
	}

	static @Nullable UUID placer(ServerLevel level, BlockPos pos) {
		Data d = STORE.get();
		return d.placed == null ? null : d.placed.get(key(level, pos));
	}

	/** May {@code p}'s items be put away in the chest at {@code pos}? */
	public static boolean isOwn(ServerPlayer p, ServerLevel level, BlockPos pos) {
		UUID landOwner = Services.land().map(l -> l.ownerAt(level, pos)).orElse(null);
		if (landOwner != null) return landOwner.equals(p.getUUID());
		UUID who = placer(level, pos);
		return who == null || who.equals(p.getUUID());
	}
}
