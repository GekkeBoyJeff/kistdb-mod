package nl.libreria.info;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.Completion;

/**
 * Info's collections for the Progress window: the stories found gear tells (the lore book), the biomes you have been
 * in, and the kinds of place (village, temple, stronghold…) you found.
 */
final class InfoCollections {
	/** Biomes that are only a strip between others: the journal leaves them out, so they aren't to find either. */
	private static final Set<String> MINOR = Set.of("river", "frozen_river", "beach", "snowy_beach", "stony_shore", "the_void");
	/** A picture for a biome, by a word in its name (the first that fits). */
	private static final List<Map.Entry<String, Item>> BIOME_PICTURES = List.of(Map.entry("warm_ocean", Items.BRAIN_CORAL),
			Map.entry("lukewarm", Items.SEAGRASS), Map.entry("frozen_ocean", Items.ICE), Map.entry("cold_ocean", Items.COD),
			Map.entry("ocean", Items.KELP), Map.entry("mushroom", Items.RED_MUSHROOM_BLOCK), Map.entry("cherry", Items.CHERRY_SAPLING),
			Map.entry("pale", Items.PALE_OAK_SAPLING), Map.entry("mangrove", Items.MANGROVE_PROPAGULE), Map.entry("bamboo", Items.BAMBOO),
			Map.entry("jungle", Items.JUNGLE_SAPLING), Map.entry("savanna", Items.ACACIA_SAPLING), Map.entry("badlands", Items.RED_SAND),
			Map.entry("desert", Items.SAND), Map.entry("ice_spikes", Items.PACKED_ICE), Map.entry("snowy", Items.SNOW_BLOCK),
			Map.entry("frozen_peaks", Items.ICE), Map.entry("jagged", Items.SNOWBALL), Map.entry("stony_peaks", Items.CALCITE),
			Map.entry("grove", Items.POWDER_SNOW_BUCKET), Map.entry("meadow", Items.CORNFLOWER), Map.entry("birch", Items.BIRCH_SAPLING),
			Map.entry("dark_forest", Items.DARK_OAK_SAPLING), Map.entry("flower", Items.ALLIUM), Map.entry("sunflower", Items.SUNFLOWER),
			Map.entry("pine", Items.PODZOL), Map.entry("spruce", Items.SPRUCE_SAPLING), Map.entry("taiga", Items.SPRUCE_SAPLING),
			Map.entry("swamp", Items.LILY_PAD), Map.entry("windswept", Items.GRAVEL), Map.entry("deep_dark", Items.SCULK),
			Map.entry("dripstone", Items.POINTED_DRIPSTONE), Map.entry("lush", Items.MOSS_BLOCK), Map.entry("sulfur", Items.YELLOW_DYE),
			Map.entry("crimson", Items.CRIMSON_FUNGUS), Map.entry("warped", Items.WARPED_FUNGUS), Map.entry("soul_sand", Items.SOUL_SAND),
			Map.entry("basalt", Items.BASALT), Map.entry("nether", Items.NETHERRACK), Map.entry("end_highlands", Items.CHORUS_FLOWER),
			Map.entry("end", Items.END_STONE), Map.entry("forest", Items.OAK_SAPLING), Map.entry("plains", Items.GRASS_BLOCK));
	/** A picture for a kind of place. */
	private static final Map<String, Item> PLACE_PICTURES = Map.ofEntries(Map.entry("Village", Items.BELL),
			Map.entry("Pillager Outpost", Items.CROSSBOW), Map.entry("Ancient City", Items.ECHO_SHARD),
			Map.entry("Trial Chambers", Items.TRIAL_KEY), Map.entry("Woodland Mansion", Items.TOTEM_OF_UNDYING),
			Map.entry("Ocean Monument", Items.PRISMARINE_SHARD), Map.entry("Stronghold", Items.ENDER_EYE),
			Map.entry("Nether Fortress", Items.NETHER_BRICKS), Map.entry("Bastion", Items.GILDED_BLACKSTONE),
			Map.entry("End City", Items.PURPUR_BLOCK), Map.entry("Desert Temple", Items.CHISELED_SANDSTONE),
			Map.entry("Jungle Temple", Items.MOSSY_COBBLESTONE), Map.entry("Trail Ruins", Items.BRUSH), Map.entry("Igloo", Items.SNOW_BLOCK),
			Map.entry("Witch Hut", Items.CAULDRON), Map.entry("Ruined Portal", Items.CRYING_OBSIDIAN),
			Map.entry("Shipwreck", Items.OAK_BOAT), Map.entry("Ocean Ruins", Items.SUSPICIOUS_SAND));

	private InfoCollections() {
	}

	static void init() {
		Completion.add("stories", Completion.Tab.COLLECT, 30, Items.WRITABLE_BOOK, "Stories")
				.about("The stories found gear tells, in parts. A story counts once you found one of its parts.")
				.words("Found", "Not found yet")
				.entries(InfoCollections::stories)
				.done((s, who, st) -> {
					Set<String> out = new HashSet<>();
					for (var e : Found.SEEN.get(who).parts.entrySet()) if (!e.getValue().isEmpty()) out.add(e.getKey());
					return out;
				})
				.detail((e, st, done) -> {
					Found.Text last = Found.STORIES.get(e.key());
					return last == null ? null : last.parts() + (last.parts() == 1 ? " part" : " parts");
				})
				.own((viewer, who, back) -> {
					if (!viewer.getUUID().equals(who)) return false;
					LoreBook.open(viewer);
					return true;
				});
		Completion.add("biomes", Completion.Tab.EXPLORE, 10, Items.FILLED_MAP, "Biomes")
				.about("Every biome of the Overworld, the Nether and the End, stood in for a moment.")
				.words("Been there", "Not been there yet")
				.entries(InfoCollections::biomes)
				.done((s, who, st) -> new HashSet<>(Places.STORE.get(who).biomes));
		Completion.add("places", Completion.Tab.EXPLORE, 20, Items.COMPASS, "Places found")
				.about("Every kind of place: a village, a temple, a stronghold, an ancient city…")
				.words("Found", "Not found yet")
				.entries(() -> {
					List<Completion.Entry> out = new ArrayList<>();
					for (String kind : PlaceNames.structureLabels()) {
						out.add(new Completion.Entry(kind, new ItemStack(PLACE_PICTURES.getOrDefault(kind, Items.COMPASS)), kind));
					}
					return out;
				})
				.done((s, who, st) -> {
					Set<String> out = new HashSet<>();
					for (Places.Place p : Places.STORE.get(who).places) out.add(p.kind);
					return out;
				});
	}

	/** Every story, with the item its first part is on as its picture. */
	private static List<Completion.Entry> stories() {
		List<Completion.Entry> out = new ArrayList<>();
		for (var e : Found.STORIES.entrySet()) {
			Found.Text t = e.getValue();
			Item item = Items.WRITTEN_BOOK;
			Found.Text first = Found.BY_ID.get("a:" + e.getKey() + ":1");
			for (String k : (first != null ? first : t).kinds()) {
				var it = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(k));
				if (it.isPresent()) {
					item = it.get();
					break;
				}
			}
			out.add(new Completion.Entry(e.getKey(), new ItemStack(item), t.title() == null ? e.getKey() : t.title()));
		}
		return out;
	}

	private static List<Completion.Entry> biomes() {
		MinecraftServer server = nl.libreria.core.Libreria.server();
		if (server == null) return List.of();
		List<Completion.Entry> out = new ArrayList<>();
		server.registryAccess().lookupOrThrow(Registries.BIOME).listElements().forEach(h -> {
			Identifier id = h.key().identifier();
			if (MINOR.contains(id.getPath())) return;
			Item picture = Items.GRASS_BLOCK;
			for (var m : BIOME_PICTURES) {
				if (id.getPath().contains(m.getKey())) {
					picture = m.getValue();
					break;
				}
			}
			String name = Component.translatable("biome." + id.getNamespace() + "." + id.getPath()).getString();
			out.add(new Completion.Entry(id.toString(), new ItemStack(picture), name));
		});
		out.sort(java.util.Comparator.comparing(Completion.Entry::name));
		return out;
	}
}
