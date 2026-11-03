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
			SelfTests.check(Catalog.all().size() > 70, "enough achievements: " + Catalog.all().size());
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
	}
}
