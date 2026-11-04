package nl.libreria.collector;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.SelfTests;
import nl.libreria.core.Ui;

/** Zelftests: catalogus, verzamelen, titel en punten. */
final class CollectorTests {
	private CollectorTests() {
	}

	static void register() {
		SelfTests.register("collector.catalog", level -> {
			if (Catalog.all().isEmpty()) Catalog.build();
			SelfTests.check(Catalog.allItems().size() > 1000, "more than 1000 collectible items: " + Catalog.allItems().size());
			SelfTests.check(!Catalog.allItems().contains("bedrock") && !Catalog.allItems().contains("pig_spawn_egg"), "unobtainable items left out");
			Achievement wool = Catalog.get("verzamel.wol");
			SelfTests.check(wool != null && wool.items().size() == 16, "16 colors of wool");
			SelfTests.check(Catalog.get("verzamel.platen") != null, "music disc collection");
			SelfTests.check(Catalog.get("soort.alles") != null && Catalog.get("soort.alles").target() >= 20, "Noah's Ark has 20+ species");
			SelfTests.check(Catalog.get("proef.alles") != null, "Gourmet exists");
			SelfTests.check(Catalog.all().size() > 330, "lots of achievements: " + Catalog.all().size());
			SelfTests.check(Catalog.DUPLICATES.isEmpty(), "every id and name once: " + Catalog.DUPLICATES);
			// Everything that existed before still does (what players earned stays theirs)
			for (String id : new String[] {"verzamel.wol", "items.50", "items.alles", "items.drakenei", "fok.1", "fok.2000", "soort.alles",
					"fok.muilezel", "fok.blauw", "dorp.50", "genees.10", "tijd.1", "tijd.1000", "leven.10", "leven.50", "mijn.1000",
					"mijn.200000", "diamant.1", "diamant.1000", "monster.100", "monster.10000", "loop.10", "loop.1000", "vlieg.10", "vlieg.500",
					"vis.10", "vis.1000", "handel.10", "handel.1000", "tover.10", "tover.100", "slaap.10", "slaap.100", "taart.50",
					"spring.10000", "spring.100000", "raid.1", "raid.10", "dood.10", "dood.100", "baas.draak", "baas.wither", "baas.warden",
					"baas.guardian", "proef.alles"}) {
				SelfTests.check(Catalog.get(id) != null, "still there: " + id);
			}
			// Ladders: rising targets, rarity never drops, a name per grade
			Achievement stone = Catalog.get("steen.1000");
			SelfTests.check(stone != null && Catalog.ladder(stone).size() == 4 && "Grade 2 of 4".equals(Catalog.gradeText(stone)),
					"stone ladder: " + (stone == null ? null : Catalog.gradeText(stone)));
			java.util.Map<Achievement.Category, Integer> per = new java.util.EnumMap<>(Achievement.Category.class);
			long points = 0;
			for (Achievement a : Catalog.all()) {
				per.merge(a.category(), 1, Integer::sum);
				points += a.rarity().points;
				SelfTests.check(a.target() > 0, "target above zero: " + a.id());
				java.util.List<Achievement> l = Catalog.ladder(a);
				int i = l.indexOf(a);
				SelfTests.check(i >= 0, "in its own ladder: " + a.id());
				if (i > 0) {
					SelfTests.check(l.get(i - 1).target() < a.target(), "targets rise: " + a.id());
					SelfTests.check(l.get(i - 1).rarity().ordinal() <= a.rarity().ordinal(), "rarity never drops: " + a.id());
				}
			}
			for (Achievement.Category c : Achievement.Category.values()) {
				SelfTests.check(per.getOrDefault(c, 0) >= 10, "category " + c.label + " has achievements: " + per.get(c));
			}
			nl.libreria.core.Libreria.LOG.info("Collector: {} achievements {}, {} points in all", Catalog.all().size(), per, points);
			SelfTests.check(Catalog.get("plekken.10") != null && Catalog.get("bouwwerken.5") != null, "exploring achievements from the journal");
			// A counter is worked out once per check, not once per grade
			Catalog.fresh();
			Achievement raid = Catalog.get("raid.1");
			SelfTests.check(raid != null && raid.description().equals("Win a raid"), "singular description: " + (raid == null ? null : raid.description()));
			Achievement mine = Catalog.get("mijn.10000");
			SelfTests.check(mine != null && mine.description().equals("Mine 10,000 blocks"), "English number format: "
					+ (mine == null ? null : mine.description()));
			SelfTests.check(CollectorMod.date(0L).matches("\\d{4}-\\d{2}-\\d{2}"), "date as yyyy-MM-dd: " + CollectorMod.date(0L));
		});
		SelfTests.register("collector.collect-and-title", level -> {
			if (Catalog.all().isEmpty()) Catalog.build();
			FakePlayer p = FakePlayer.get(level);
			CollectorMod.Data d = CollectorMod.STORE.get(p);
			d.items.clear();
			d.earned.clear();
			d.initialized = true;
			d.catalog = Catalog.signature();
			d.title = "";
			p.getInventory().clearContent();
			Achievement wool = Catalog.get("verzamel.wol");
			for (int i = 0; i < wool.items().size(); i++) p.getInventory().setItem(i, new ItemStack(Ui.item(wool.items().get(i))));
			SelfTests.check(CollectorMod.scan(p), "scan finds new items");
			CollectorMod.check(p);
			SelfTests.check(d.earned.containsKey("verzamel.wol"), "all wool = achievement");
			SelfTests.check(CollectorMod.points(d) >= Rarity.UNCOMMON.points, "points counted");
			Achievement t = CollectorMod.titleAchievement(d);
			SelfTests.check(t != null && t.rarity().ordinal() >= Rarity.UNCOMMON.ordinal(), "automatic title = rarest");
			d.title = "-";
			SelfTests.check(CollectorMod.titleAchievement(d) == null, "no title");
			d.title = "";
			p.getInventory().clearContent();
			p.getInventory().setItem(0, new ItemStack(Items.DRAGON_EGG));
			CollectorMod.scan(p);
			CollectorMod.check(p);
			SelfTests.check(d.earned.containsKey("items.drakenei"), "dragon egg = mythic");
			SelfTests.check(CollectorMod.titleAchievement(d).rarity() == Rarity.MYTHIC, "title is now mythic");
			p.getInventory().clearContent();
		});
		SelfTests.register("collector.quiet-new-achievements", level -> {
			if (Catalog.all().isEmpty()) Catalog.build();
			FakePlayer p = FakePlayer.get(level);
			CollectorMod.Data d = CollectorMod.STORE.get(p);
			d.items.clear();
			d.earned.clear();
			d.initialized = true;
			d.catalog = 12345;
			p.getInventory().clearContent();
			p.getInventory().setItem(0, new ItemStack(Items.DRAGON_EGG));
			CollectorMod.scan(p);
			CollectorMod.check(p);
			SelfTests.check(d.earned.containsKey("items.drakenei") && d.catalog == Catalog.signature(),
					"new achievements you already did are given quietly");
			p.getInventory().clearContent();
			d.earned.clear();
		});
		SelfTests.register("collector.advancements", level -> {
			if (Catalog.all().isEmpty()) Catalog.build();
			if (Advancements.holder("verzamel.wol") == null) Advancements.inject(level.getServer());
			var mgr = level.getServer().getAdvancements();
			SelfTests.check(mgr.get(Advancements.idOf("verzamel.wol")) != null, "wool collection is an advancement");
			SelfTests.check(mgr.tree().get(Advancements.idOf("verzamel.wol")) != null, "and it's in the advancement tree");
			SelfTests.check(mgr.get(net.minecraft.resources.Identifier.fromNamespaceAndPath("libreria", "collector/verzamelen")) != null, "Collecting tab exists");
		});
	}
}
