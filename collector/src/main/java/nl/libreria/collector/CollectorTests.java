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
		SelfTests.register("verzamelaar.catalogus", level -> {
			if (Catalog.all().isEmpty()) Catalog.build();
			SelfTests.check(Catalog.allItems().size() > 1000, "meer dan 1000 verzamelbare items: " + Catalog.allItems().size());
			SelfTests.check(!Catalog.allItems().contains("bedrock") && !Catalog.allItems().contains("pig_spawn_egg"), "onverkrijgbaar eruit");
			Achievement wool = Catalog.get("verzamel.wol");
			SelfTests.check(wool != null && wool.items().size() == 16, "16 kleuren wol");
			SelfTests.check(Catalog.get("verzamel.platen") != null, "muziekplaten-verzameling");
			SelfTests.check(Catalog.get("soort.alles") != null && Catalog.get("soort.alles").target() >= 20, "Ark van Noach heeft 20+ soorten");
			SelfTests.check(Catalog.get("proef.alles") != null, "Fijnproever bestaat");
			SelfTests.check(Catalog.all().size() > 70, "genoeg prestaties: " + Catalog.all().size());
		});
		SelfTests.register("verzamelaar.verzamelen-en-titel", level -> {
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
			SelfTests.check(CollectorMod.scan(p), "scan vindt nieuwe items");
			CollectorMod.check(p);
			SelfTests.check(d.earned.containsKey("verzamel.wol"), "alle wol = prestatie");
			SelfTests.check(CollectorMod.points(d) >= Rarity.ONGEWOON.points, "punten geteld");
			Achievement t = CollectorMod.titleAchievement(d);
			SelfTests.check(t != null && t.rarity().ordinal() >= Rarity.ONGEWOON.ordinal(), "automatische titel = zeldzaamste");
			d.title = "-";
			SelfTests.check(CollectorMod.titleAchievement(d) == null, "geen titel");
			d.title = "";
			p.getInventory().clearContent();
			p.getInventory().setItem(0, new ItemStack(Items.DRAGON_EGG));
			CollectorMod.scan(p);
			CollectorMod.check(p);
			SelfTests.check(d.earned.containsKey("items.drakenei"), "drakenei = mythisch");
			SelfTests.check(CollectorMod.titleAchievement(d).rarity() == Rarity.MYTHISCH, "titel wordt nu mythisch");
			p.getInventory().clearContent();
		});
	}
}
