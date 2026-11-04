package nl.libreria.chests;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Zoekt in de kisten-database van één speler. */
public final class Search {
	/** Kisten verder weg dan dit worden niet meegenomen. */
	/** How far chests count (server setting, in blocks). */
	public static double radius() {
		return ChestsMod.SEARCH_RADIUS.get();
	}

	private Search() {
	}

	/** Eén container waar een item in ligt. */
	public record Hit(Store.Entry entry, int count, double distance, boolean live) {
	}

	/** Eén soort item over alle kisten heen. */
	public record Agg(Item item, ItemStack sample, long total, List<Hit> hits) {
		public Hit nearest() {
			return this.hits.getFirst();
		}
	}

	/** Een container met (live of laatst geziene) inhoud. */
	public record Box(Store.Entry entry, double distance, boolean live, Map<Item, Integer> counts, List<ItemStack> stacks) {
	}

	/** Alle containers van de speler in deze dimensie, dichtstbijzijnde eerst. Live gelezen waar mogelijk. */
	public static List<Box> boxes(ServerPlayer player, boolean includeNoIndex) {
		ServerLevel level = player.level();
		String dim = Store.dimId(level);
		Vec3 pos = player.position();
		Store.PlayerData data = Store.get(player.getUUID());
		List<Box> out = new ArrayList<>();
		for (Store.Entry e : new ArrayList<>(data.entries)) {
			if (!e.dim.equals(dim) || (e.noIndex && !includeNoIndex)) continue;
			BlockPos key = e.pos();
			double dist = pos.distanceTo(Vec3.atCenterOf(key));
			if (dist > radius()) continue;
			Map<Item, Integer> counts = new LinkedHashMap<>();
			List<ItemStack> stacks = new ArrayList<>();
			boolean live = Containers.isLoaded(level, key);
			if (live) {
				List<ItemStack> read = Containers.read(level, key);
				if (read == null) {
					// kist is weg
					Store.remove(player.getUUID(), e);
					continue;
				}
				stacks = read;
				for (ItemStack s : read) counts.merge(s.getItem(), s.getCount(), Integer::sum);
				Map<String, Integer> summary = Containers.summarize(read);
				if (!summary.equals(e.items)) {
					e.items = summary;
					data.markDirty();
				}
				e.seen = System.currentTimeMillis();
			} else {
				for (Map.Entry<String, Integer> it : e.items.entrySet()) {
					Item item = itemById(it.getKey());
					if (item != Items.AIR) {
						counts.merge(item, it.getValue(), Integer::sum);
						stacks.add(new ItemStack(item, Math.min(it.getValue(), item.getDefaultMaxStackSize())));
					}
				}
			}
			out.add(new Box(e, dist, live, counts, stacks));
		}
		out.sort(Comparator.comparingDouble(Box::distance));
		return out;
	}

	/** Alle items over alle kisten, gefilterd op naam/ID. byName = A-Z, anders meeste eerst. */
	public static List<Agg> aggregate(ServerPlayer player, String filter, boolean byName) {
		String f = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
		Map<Item, List<Hit>> hits = new LinkedHashMap<>();
		Map<Item, Long> totals = new LinkedHashMap<>();
		for (Box box : boxes(player, false)) {
			for (Map.Entry<Item, Integer> it : box.counts().entrySet()) {
				if (!f.isEmpty() && !matches(it.getKey(), f)) continue;
				hits.computeIfAbsent(it.getKey(), k -> new ArrayList<>()).add(new Hit(box.entry(), it.getValue(), box.distance(), box.live()));
				totals.merge(it.getKey(), (long) it.getValue(), Long::sum);
			}
		}
		List<Agg> out = new ArrayList<>();
		hits.forEach((item, list) -> {
			list.sort(Comparator.comparingDouble(Hit::distance));
			out.add(new Agg(item, new ItemStack(item), totals.get(item), list));
		});
		if (byName) {
			out.sort(Comparator.comparing(a -> a.sample().getHoverName().getString().toLowerCase(Locale.ROOT)));
		} else {
			out.sort(Comparator.comparingLong(Agg::total).reversed());
		}
		return out;
	}

	/** One kind of item: how many you carry, how many are in your chests and which ones (nearest first). */
	public record Stock(Item item, ItemStack sample, int carried, long stored, List<Hit> hits) {
		public long total() {
			return this.carried + this.stored;
		}

		/** 0 when you carry it, otherwise the distance to the nearest chest with it. */
		public double nearest() {
			if (this.carried > 0) return 0;
			return this.hits.isEmpty() ? Double.MAX_VALUE : this.hits.getFirst().distance();
		}
	}

	/** Everything you carry (hotbar, inventory, off hand) and everything in your chests nearby, per kind of item. */
	public static List<Stock> stock(ServerPlayer player, String filter) {
		String f = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
		Map<Item, Integer> carried = new LinkedHashMap<>();
		var inv = player.getInventory();
		for (int i = 0; i < 36; i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty()) carried.merge(s.getItem(), s.getCount(), Integer::sum);
		}
		ItemStack off = player.getOffhandItem();
		if (!off.isEmpty()) carried.merge(off.getItem(), off.getCount(), Integer::sum);
		Map<Item, Agg> stored = new LinkedHashMap<>();
		for (Agg a : aggregate(player, "", false)) stored.put(a.item(), a);
		java.util.Set<Item> all = new java.util.LinkedHashSet<>(stored.keySet());
		all.addAll(carried.keySet());
		List<Stock> out = new ArrayList<>();
		for (Item item : all) {
			if (!f.isEmpty() && !matches(item, f)) continue;
			Agg a = stored.get(item);
			out.add(new Stock(item, new ItemStack(item), carried.getOrDefault(item, 0), a == null ? 0 : a.total(), a == null ? List.of() : a.hits()));
		}
		return out;
	}

	/** Alle kisten met dit item, dichtstbijzijnde eerst. */
	public static List<Hit> find(ServerPlayer player, Item item) {
		List<Hit> out = new ArrayList<>();
		for (Box box : boxes(player, false)) {
			Integer c = box.counts().get(item);
			if (c != null && c > 0) out.add(new Hit(box.entry(), c, box.distance(), box.live()));
		}
		return out;
	}

	/** Match op item-ID ("diamond", "minecraft:oak_log") of de Engelse naam (de server kent geen Nederlandse namen). */
	public static boolean matches(Item item, String lowerFilter) {
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		if (id.contains(lowerFilter)) return true;
		String name = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
		return name.contains(lowerFilter) || id.replace('_', ' ').contains(lowerFilter);
	}

	public static Item itemById(String id) {
		Identifier rl = Identifier.tryParse(id);
		if (rl == null) return Items.AIR;
		return BuiltInRegistries.ITEM.getValue(rl);
	}

	public static Item blockItem(Store.Entry e) {
		Identifier rl = Identifier.tryParse(e.block);
		if (rl == null) return Items.CHEST;
		Item item = BuiltInRegistries.BLOCK.getValue(rl).asItem();
		return item == Items.AIR ? Items.CHEST : item;
	}

	/** "just now", "5 min ago", "2 h ago" … */
	public static String ago(long millis) {
		if (millis <= 0) return "unknown";
		long s = (System.currentTimeMillis() - millis) / 1000;
		if (s < 60) return "just now";
		if (s < 3600) return (s / 60) + " min ago";
		if (s < 86400) return (s / 3600) + " h ago";
		return (s / 86400) + " d ago";
	}

	public static String kind(Store.Entry e) {
		if (e.block.contains("barrel")) return "Barrel";
		if (e.block.contains("shulker")) return "Shulker box";
		return "Chest";
	}
}
