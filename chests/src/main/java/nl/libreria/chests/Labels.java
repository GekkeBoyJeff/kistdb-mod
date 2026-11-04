package nl.libreria.chests;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nl.libreria.chests.gui.ChestOptions;
import org.joml.Vector3f;

/**
 * A small floating picture above each of your chests: what it holds most of. Only you see your own labels, only for
 * chests near you, and they update when the chest's contents change. Turn them off in the settings, or per chest in its
 * options (shift-click the Sort button).
 */
public final class Labels {
	private static final double RANGE = 14;
	private static final int MAX = 40;

	/** One label: the picture it shows and the display holder. */
	private record Shown(String icon, ElementHolder holder) {
	}

	private static final Map<UUID, Map<String, Shown>> SHOWN = new HashMap<>();

	private Labels() {
	}

	static void tick(ServerPlayer p) {
		Map<String, Shown> shown = SHOWN.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
		if (!ChestsMod.LABELS.get(p) || p.isSpectator()) {
			clear(shown);
			return;
		}
		ServerLevel level = p.level();
		String dim = Store.dimId(level);
		List<Store.Entry> near = new ArrayList<>();
		for (Store.Entry e : Store.get(p.getUUID()).entries) {
			if (e.noLabel || !e.dim.equals(dim) || e.items.isEmpty()) continue;
			if (p.distanceToSqr(e.x + 0.5, e.y + 0.5, e.z + 0.5) > RANGE * RANGE) continue;
			if (!Containers.isLoaded(level, e.pos())) continue;
			near.add(e);
		}
		near.sort(Comparator.comparingDouble(e -> p.distanceToSqr(e.x + 0.5, e.y + 0.5, e.z + 0.5)));
		if (near.size() > MAX) near = near.subList(0, MAX);

		Map<String, Store.Entry> wanted = new HashMap<>();
		for (Store.Entry e : near) wanted.put(key(e), e);
		// Away: gone, out of range, or showing something else now
		for (Iterator<Map.Entry<String, Shown>> it = shown.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, Shown> s = it.next();
			Store.Entry e = wanted.get(s.getKey());
			if (e == null || !iconId(e).equals(s.getValue().icon())) {
				s.getValue().holder().destroy();
				it.remove();
			}
		}
		for (Store.Entry e : near) {
			String k = key(e);
			if (shown.containsKey(k)) continue;
			shown.put(k, new Shown(iconId(e), show(p, level, e)));
		}
	}

	private static ElementHolder show(ServerPlayer p, ServerLevel level, Store.Entry e) {
		AABB box = Containers.boxOf(level, e.pos());
		Vec3 at = new Vec3((box.minX + box.maxX) / 2, box.maxY + 0.32, (box.minZ + box.maxZ) / 2);
		ItemStack icon = ChestOptions.icon(e);
		ElementHolder holder = new ElementHolder();
		ItemDisplayElement item = new ItemDisplayElement(icon);
		item.setBillboardMode(Display.BillboardConstraints.CENTER);
		item.setScale(new Vector3f(0.42f, 0.42f, 0.42f));
		item.setBrightness(new Brightness(15, 15));
		item.setViewRange(0.5f);
		holder.addElement(item);
		new ManualAttachment(holder, level, () -> at);
		holder.startWatching(p);
		return holder;
	}

	/** Right away, e.g. after the options of a chest changed. */
	public static void refresh(ServerPlayer p) {
		Map<String, Shown> shown = SHOWN.get(p.getUUID());
		if (shown != null) clear(shown);
		tick(p);
	}

	private static void clear(Map<String, Shown> shown) {
		for (Shown s : shown.values()) s.holder().destroy();
		shown.clear();
	}

	static void forget(UUID id) {
		Map<String, Shown> shown = SHOWN.remove(id);
		if (shown != null) clear(shown);
	}

	static void clearAll() {
		for (Map<String, Shown> m : SHOWN.values()) clear(m);
		SHOWN.clear();
	}

	private static String key(Store.Entry e) {
		return e.dim + "|" + e.x + "," + e.y + "," + e.z;
	}

	private static String iconId(Store.Entry e) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ChestOptions.icon(e).getItem()).toString();
	}
}
