package nl.libreria.collector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.ImpossibleTrigger;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import nl.libreria.collector.Achievement.Category;
import nl.libreria.collector.mixin.ServerAdvancementManagerAccessor;
import nl.libreria.core.Libreria;
import org.jetbrains.annotations.Nullable;

/**
 * Mirrors the Collector achievements as real advancements, so they show up in the vanilla advancements screen (L):
 * one tab per category, frames by rarity (task / goal / challenge), vanilla toasts, and a progress bar for collections.
 * Our own data stays the source of truth; the advancements are synced from it.
 */
final class Advancements {
	private static final String NS = "libreria";
	/** Collections with more items than this get a single criterion (no per-item bar) to keep player files small. */
	private static final int MAX_ITEM_CRITERIA = 100;
	private static final String DONE = "done";

	private static final Map<String, AdvancementHolder> BY_ID = new HashMap<>();
	private static final Map<Category, AdvancementHolder> ROOTS = new EnumMap<>(Category.class);

	private Advancements() {
	}

	static Identifier idOf(String achievementId) {
		return Identifier.fromNamespaceAndPath(NS, "collector/" + achievementId);
	}

	private static String itemCriterion(String item) {
		return "has_" + item;
	}

	private static boolean perItem(Achievement a) {
		return a.isCollection() && a.items().size() <= MAX_ITEM_CRITERIA;
	}

	// ---------------------------------------------------------------- building

	private static AdvancementType frame(Rarity r) {
		return switch (r) {
			case COMMON, UNCOMMON -> AdvancementType.TASK;
			case RARE, EPIC -> AdvancementType.GOAL;
			case LEGENDARY, MYTHIC -> AdvancementType.CHALLENGE;
		};
	}

	private static Item rootIcon(Category c) {
		return switch (c) {
			case VERZAMELEN -> Items.CHEST;
			case FOKKEN -> Items.WHEAT;
			case TIJD -> Items.CLOCK;
			case AVONTUUR -> Items.IRON_SWORD;
		};
	}

	private static String background(Category c) {
		return switch (c) {
			case VERZAMELEN -> "gui/advancements/backgrounds/stone";
			case FOKKEN -> "gui/advancements/backgrounds/husbandry";
			case TIJD -> "gui/advancements/backgrounds/end";
			case AVONTUUR -> "gui/advancements/backgrounds/adventure";
		};
	}

	private static List<AdvancementHolder> build() {
		BY_ID.clear();
		ROOTS.clear();
		List<AdvancementHolder> out = new ArrayList<>();
		ImpossibleTrigger.TriggerInstance never = new ImpossibleTrigger.TriggerInstance();
		for (Category c : Category.values()) {
			AdvancementHolder root = Advancement.Builder.advancement()
					.rootDisplay(rootIcon(c), Component.literal(c.label).withStyle(ChatFormatting.GOLD),
							Component.literal(c.hint + ". Earn achievements to unlock titles for above your name. Details: /achievements"),
							Identifier.withDefaultNamespace(background(c)), AdvancementType.TASK, false, false, false)
					.addCriterion(DONE, CriteriaTriggers.IMPOSSIBLE.createCriterion(never))
					.build(Identifier.fromNamespaceAndPath(NS, "collector/" + c.name().toLowerCase(java.util.Locale.ROOT)));
			ROOTS.put(c, root);
			out.add(root);
		}

		// Chains: same id prefix with numeric (or "alles") suffix, ordered by target, e.g. items.50 → items.150 → …
		Map<String, List<Achievement>> chains = new LinkedHashMap<>();
		for (Achievement a : Catalog.all()) {
			if (a.isCollection() && !a.id().startsWith("items.")) continue;
			int dot = a.id().lastIndexOf('.');
			String suffix = a.id().substring(dot + 1);
			if (dot > 0 && (suffix.chars().allMatch(Character::isDigit) || suffix.equals("alles"))) {
				chains.computeIfAbsent(a.id().substring(0, dot), k -> new ArrayList<>()).add(a);
			}
		}
		Map<String, String> parentOf = new HashMap<>();
		for (List<Achievement> chain : chains.values()) {
			chain.sort(Comparator.comparingLong(Achievement::target));
			for (int i = 1; i < chain.size(); i++) parentOf.put(chain.get(i).id(), chain.get(i - 1).id());
		}

		// Parents first, so every holder can point at its parent
		List<Achievement> ordered = new ArrayList<>(Catalog.all());
		ordered.sort(Comparator.comparingInt(a -> depth(a.id(), parentOf)));
		for (Achievement a : ordered) {
			Advancement.Builder b = Advancement.Builder.advancement();
			String parent = parentOf.get(a.id());
			b.parent(parent != null && BY_ID.containsKey(parent) ? BY_ID.get(parent) : ROOTS.get(a.category()));
			Rarity r = a.rarity();
			MutableComponent desc = Component.literal(a.description()).withStyle(ChatFormatting.GRAY)
					.append(Component.literal("\n" + r.label).withStyle(r.color))
					.append(Component.literal(" · title ✦ " + a.name() + " ✦").withStyle(ChatFormatting.DARK_GRAY));
			boolean secret = a.id().equals("fok.blauw") || a.id().equals("items.drakenei");
			b.display(a.icon(), Component.literal(a.name()).withStyle(r.color), desc, frame(r), true, false, secret);
			if (perItem(a)) {
				for (String item : a.items()) b.addCriterion(itemCriterion(item), CriteriaTriggers.IMPOSSIBLE.createCriterion(never));
				b.requirements(AdvancementRequirements.allOf(a.items().stream().map(Advancements::itemCriterion).collect(Collectors.toList())));
			} else {
				b.addCriterion(DONE, CriteriaTriggers.IMPOSSIBLE.createCriterion(never));
			}
			AdvancementHolder h = b.build(idOf(a.id()));
			BY_ID.put(a.id(), h);
			out.add(h);
		}
		return out;
	}

	private static int depth(String id, Map<String, String> parentOf) {
		int d = 0;
		for (String p = parentOf.get(id); p != null && d < 50; p = parentOf.get(p)) d++;
		return d;
	}

	// ---------------------------------------------------------------- injecting into the server

	/** Adds (or replaces) our advancements in the server's advancement manager, then resyncs online players. */
	static void inject(MinecraftServer server) {
		if (Catalog.all().isEmpty()) return;
		try {
			List<AdvancementHolder> ours = build();
			ServerAdvancementManager mgr = server.getAdvancements();
			ServerAdvancementManagerAccessor acc = (ServerAdvancementManagerAccessor) mgr;
			Map<Identifier, AdvancementHolder> map = new LinkedHashMap<>(acc.libreria$getAdvancements());
			Set<Identifier> old = map.keySet().stream().filter(id -> id.getNamespace().equals(NS)).collect(Collectors.toSet());
			map.keySet().removeAll(old);
			for (AdvancementHolder h : ours) map.put(h.id(), h);
			acc.libreria$setAdvancements(map);
			if (!old.isEmpty()) mgr.tree().remove(old);
			mgr.tree().addAll(ours);
			mgr.tree().repositionNodes();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				p.getAdvancements().reload(mgr);
				sync(p);
			}
			Libreria.LOG.info("Collector: {} achievements added to the advancements screen", ours.size() - ROOTS.size());
		} catch (Throwable t) {
			Libreria.LOG.error("Collector: could not add the achievements to the advancements screen", t);
		}
	}

	// ---------------------------------------------------------------- syncing progress

	/** Brings the player's advancements in line with their Collector data (on join, after a reload). */
	static void sync(ServerPlayer p) {
		if (p instanceof net.fabricmc.fabric.api.entity.FakePlayer) return;
		if (BY_ID.isEmpty()) return;
		PlayerAdvancements adv = p.getAdvancements();
		for (AdvancementHolder root : ROOTS.values()) adv.award(root, DONE);
		CollectorMod.Data d = CollectorMod.STORE.get(p);
		for (Achievement a : Catalog.all()) {
			if (d.earned.containsKey(a.id())) complete(p, a);
			else if (perItem(a)) items(p, a, d);
		}
	}

	/** Marks an achievement as done (shows the vanilla toast if it wasn't yet). */
	static void complete(ServerPlayer p, Achievement a) {
		if (p instanceof net.fabricmc.fabric.api.entity.FakePlayer) return;
		AdvancementHolder h = BY_ID.get(a.id());
		if (h == null) return;
		PlayerAdvancements adv = p.getAdvancements();
		AdvancementProgress progress = adv.getOrStartProgress(h);
		if (progress.isDone()) return;
		List<String> remaining = new ArrayList<>();
		progress.getRemainingCriteria().forEach(remaining::add);
		for (String c : remaining) adv.award(h, c);
	}

	/** Ticks off the items of a collection the player already has (moves the progress bar). */
	static void items(ServerPlayer p, Achievement a, CollectorMod.Data d) {
		if (p instanceof net.fabricmc.fabric.api.entity.FakePlayer) return;
		AdvancementHolder h = BY_ID.get(a.id());
		if (h == null || !perItem(a)) return;
		PlayerAdvancements adv = p.getAdvancements();
		AdvancementProgress progress = adv.getOrStartProgress(h);
		if (progress.isDone()) return;
		for (String item : a.items()) {
			if (!a.has(p, d, item)) continue;
			var crit = progress.getCriterion(itemCriterion(item));
			if (crit != null && !crit.isDone()) adv.award(h, itemCriterion(item));
		}
	}

	/** After new items were collected: update every collection's progress bar. */
	static void itemsChanged(ServerPlayer p) {
		if (p instanceof net.fabricmc.fabric.api.entity.FakePlayer) return;
		CollectorMod.Data d = CollectorMod.STORE.get(p);
		for (Achievement a : Catalog.all()) if (perItem(a) && !d.earned.containsKey(a.id())) items(p, a, d);
	}

	static @Nullable AdvancementHolder holder(String achievementId) {
		return BY_ID.get(achievementId);
	}
}
