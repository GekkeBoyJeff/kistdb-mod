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
			SelfTests.check(stone != null && Catalog.ladder(stone).size() == 5 && "Grade 2 of 5".equals(Catalog.gradeText(stone)),
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
		SelfTests.register("collector.custom-stats", level -> {
			// Statistics like play time and distances are found by name too (Minecraft keeps them by object)
			if (Catalog.all().isEmpty()) Catalog.build();
			FakePlayer p = FakePlayer.get(level);
			var stat = net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.JUMP);
			int before = p.getStats().getValue(stat);
			p.getStats().setValue(p, stat, before + 7);
			Catalog.fresh();
			Catalog.Counter jumps = Catalog.custom("jump");
			long counted = jumps == null ? -1 : jumps.get(p, CollectorMod.STORE.get(p));
			int byName = nl.libreria.core.Ui.customStat(p, "jump");
			p.getStats().setValue(p, stat, before);
			SelfTests.check(counted == before + 7, "an achievement counts jumps: " + counted + " of " + (before + 7));
			SelfTests.check(byName == before + 7, "a statistic by name: " + byName);
			SelfTests.check(nl.libreria.core.Ui.customStat(p, "no_such_statistic") == 0, "an unknown one is 0");
		});
		SelfTests.register("collector.server-goals", level -> {
			if (Catalog.all().isEmpty()) Catalog.build();
			SelfTests.check(Goals.kinds().size() >= 100, "many goals: " + Goals.kinds().size());
			java.util.Set<String> areas = new java.util.HashSet<>();
			for (Goals.Kind k : Goals.kinds()) {
				areas.add(k.area());
				SelfTests.check(k.text(1234 * k.divisor()).contains("1,234"), "the amount in the text: " + k.id() + " " + k.text(1234 * k.divisor()));
				SelfTests.check(k.perPlayer() > 0, "an amount per player: " + k.id());
				SelfTests.check(Catalog.get("samen." + k.id()) != null && Catalog.get("samen." + k.id() + ".kampioen") != null,
						"an achievement and a champion one: " + k.id());
				SelfTests.check(Catalog.get("samen." + k.id() + ".kampioen").rarity().ordinal() > Catalog.get("samen." + k.id()).rarity().ordinal(),
						"the champion one is rarer: " + k.id());
			}
			SelfTests.check(areas.size() >= 6, "goals from every corner: " + areas);
			// Sizes: per player per week, times players and days, rounded nicely
			SelfTests.check(Goals.nice(1234) == 1250 && Goals.nice(37) == 35 && Goals.nice(7.4) == 7 && Goals.nice(0.4) == 1,
					"friendly numbers: " + Goals.nice(1234) + " " + Goals.nice(37));
			Goals.Kind stone = Goals.kind("mine.stone");
			SelfTests.check(stone != null && Goals.target(stone, 5, 7, 100) == 12500 && Goals.target(stone, 5, 3, 100) == 5400
					&& Goals.target(stone, 5, 7, 200) == 25000, "stone for five: " + (stone == null ? null : Goals.target(stone, 5, 7, 100)));
			Goals.Kind walk = Goals.kind("travel.walk");
			SelfTests.check(walk != null && walk.text(Goals.target(walk, 1, 7, 100)).equals("Walk 30 km"), "distances in km: "
					+ (walk == null ? null : walk.text(Goals.target(walk, 1, 7, 100))));
			// The end of a goal: who helped (1% or more), who did the most
			net.minecraft.server.MinecraftServer server = level.getServer();
			Goals.Data d = Goals.data();
			Goals.Running before = d.now;
			long nextBefore = d.nextAt;
			String areaBefore = d.lastArea;
			int pastBefore = d.past.size();
			java.util.UUID a = java.util.UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
					b = java.util.UUID.fromString("00000000-0000-0000-0000-0000000000b2"),
					c = java.util.UUID.fromString("00000000-0000-0000-0000-0000000000c3");
			Goals.Running r = new Goals.Running();
			r.kind = "mine.stone";
			r.target = 1000;
			r.ends = System.currentTimeMillis() + 60_000;
			r.done.put(a, 700L);
			r.done.put(b, 295L);
			r.done.put(c, 5L);
			d.now = r;
			Goals.end(server, d, true, System.currentTimeMillis());
			SelfTests.check(d.past.size() == pastBefore + 1 && a.equals(d.past.getLast().champion), "a past goal with its champion");
			SelfTests.check(Goals.helped(a, "mine.stone") && Goals.helped(b, "mine.stone") && !Goals.helped(c, "mine.stone"),
					"helpers: 1% or more");
			SelfTests.check(Goals.champion(a, "mine.stone") && !Goals.champion(b, "mine.stone"), "one champion");
			SelfTests.check(Goals.helpedCount(a) >= 1 && Goals.championCount(a) >= 1 && Goals.areasHelped(a) >= 1, "counted for the ladders");
			d.past.removeLast();
			d.now = before;
			d.nextAt = nextBefore;
			nl.libreria.core.Chronicle.forget(e -> e.text.startsWith("Together the server reached Stone Age"));
			SelfTests.check(!Goals.helped(a, "mine.stone"), "forgotten again");
			// Never the same corner twice in a row
			d.lastArea = "mine";
			for (int i = 0; i < 20; i++) {
				Goals.Kind k = Goals.pick(d);
				SelfTests.check(k != null && !k.area().equals("mine"), "not mining again: " + (k == null ? null : k.id()));
			}
			d.lastArea = areaBefore;
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
		SelfTests.register("collector.secrets", level -> {
			if (Catalog.all().isEmpty()) Catalog.build();
			SelfTests.check(Secrets.ALL.size() >= 60, "lots of secrets: " + Secrets.ALL.size());
			for (Secrets.Secret s : Secrets.ALL.values()) {
				Achievement a = Catalog.get("geheim." + s.id());
				SelfTests.check(a != null && Catalog.isSecret(a) && a.category() == Achievement.Category.GEHEIM, "secret is an achievement: " + s.id());
				SelfTests.check(!s.clue().isEmpty(), "secret has a clue: " + s.id());
			}
			SelfTests.check("Something about how you die.".equals(Catalog.secretClue(Catalog.get("geheim.cactus"))), "clue for the cactus");
			SelfTests.check(!Catalog.isSecret(Catalog.get("verzamel.wol")), "ordinary achievements aren't secret");
			FakePlayer p = FakePlayer.get(level);
			CollectorMod.Data d = CollectorMod.STORE.get(p);
			d.secrets.clear();
			d.earned.clear();
			d.initialized = true;
			d.catalog = Catalog.signature();
			Secrets.award(p, "cactus");
			SelfTests.check(d.secrets.isEmpty(), "a fake player finds no secrets");
			d.secrets.add("cactus");
			CollectorMod.check(p);
			SelfTests.check(d.earned.containsKey("geheim.cactus"), "found secret = earned");
			SelfTests.check(d.earned.containsKey("geheimen.1"), "first secret: Curious Cat");
			SelfTests.check(!d.earned.containsKey("geheim.bessen"), "the others stay hidden");
			d.secrets.clear();
			d.earned.clear();
		});
		SelfTests.register("collector.records", level -> {
			if (Catalog.all().isEmpty()) Catalog.build();
			SelfTests.check("87 m".equals(Records.Kind.FALL.format(87.2)), "fall: " + Records.Kind.FALL.format(87.2));
			SelfTests.check("14 km".equals(Records.Kind.FLIGHT.format(14200)) && "1.5 km".equals(Records.Kind.FLIGHT.format(1490)),
					"flight: " + Records.Kind.FLIGHT.format(14200) + ", " + Records.Kind.FLIGHT.format(1490));
			SelfTests.check("2.5 h".equals(Records.Kind.SESSION.format(2.5)) && "41 h".equals(Records.Kind.ALIVE.format(41.2)), "hours");
			SelfTests.check("y -64".equals(Records.Kind.DEPTH.format(-63.4)), "depth: " + Records.Kind.DEPTH.format(-63.4));
			SelfTests.check(Records.Kind.DEPTH.beats(-60, -55) && !Records.Kind.DEPTH.beats(-50, -55), "deeper beats depth");
			SelfTests.check(!Records.Kind.FALL.beats(30.5, 30) && Records.Kind.FALL.beats(31, 30), "a fall must be a block longer");
			SelfTests.check(Records.Kind.FLIGHT.beats(1201, 1200), "farther flight beats");
			SelfTests.check(!Records.Kind.FALL.counts(5) && Records.Kind.FALL.counts(40), "a short fall isn't a record");
			Records.Rec before = Records.get(Records.Kind.FALL);
			Records.offer(FakePlayer.get(level), Records.Kind.FALL, 999);
			SelfTests.check(Records.get(Records.Kind.FALL) == before, "a fake player sets no records");
			for (Records.Kind k : Records.Kind.values()) {
				SelfTests.check(Catalog.get("record." + k.id) == null, "holding " + k.id + " is no title any more");
			}
			SelfTests.check(Trophies.isFeat(Records.Kind.FALL, 150) && !Trophies.isFeat(Records.Kind.FALL, 149), "a fall is a trophy from 150 m");
			SelfTests.check(Trophies.isFeat(Records.Kind.ALIVE, 48) && !Trophies.isFeat(Records.Kind.ALIVE, 41), "alive: from 48 hours");
			SelfTests.check(!Records.Kind.SESSION.hasTrophy() && !Records.Kind.DEPTH.hasTrophy(), "no trophy for a long session or the bottom");
			// Only the holder has it, and only while they hold it
			java.util.UUID anna = java.util.UUID.nameUUIDFromBytes("trophy-anna".getBytes()), bram = java.util.UUID.nameUUIDFromBytes("trophy-bram".getBytes());
			Records.Rec keep = Records.get(Records.Kind.LEVELS);
			Records.debugSet(Records.Kind.LEVELS, anna, "Anna", 120, null, 0);
			SelfTests.check(Trophies.has(anna, Records.Kind.LEVELS) && !Trophies.has(bram, Records.Kind.LEVELS), "the holder has the trophy");
			CollectorMod.Data dd = new CollectorMod.Data();
			dd.trophy = "levels";
			SelfTests.check(Trophies.worn(anna, dd) == Records.Kind.LEVELS, "and can wear it");
			Records.debugSet(Records.Kind.LEVELS, bram, "Bram", 130, "Anna", 120);
			SelfTests.check(Trophies.worn(anna, dd) == null && Trophies.has(bram, Records.Kind.LEVELS), "beaten: the trophy goes to the new holder");
			Records.debugSet(Records.Kind.LEVELS, bram, "Bram", 60, null, 0);
			SelfTests.check(!Trophies.has(bram, Records.Kind.LEVELS), "a record below the bar is no trophy");
			if (keep == null) Records.forget(Records.Kind.LEVELS);
			else Records.debugSet(Records.Kind.LEVELS, keep.who, keep.name, keep.value, keep.before == null ? null : keep.beforeName, keep.beforeValue);
			SelfTests.check(CollectorMod.withTrophy(anna, dd, null) == null, "no trophy and no title: no title line");
			SelfTests.check(Catalog.get("records.1") != null && Catalog.get("records.20") != null, "record breaker ladder");
		});
		SelfTests.register("collector.showcase", level -> {
			if (Catalog.all().isEmpty()) Catalog.build();
			FakePlayer p = FakePlayer.get(level);
			CollectorMod.Data d = CollectorMod.STORE.get(p);
			d.earned.clear();
			d.showcase.clear();
			SelfTests.check(CollectorMod.showcase(d).isEmpty(), "nothing earned: empty showcase");
			d.earned.put("fok.1", 1L);
			d.earned.put("items.drakenei", 2L);
			d.earned.put("mijn.1000", 3L);
			d.earned.put("tijd.1", 4L);
			java.util.List<Achievement> auto = CollectorMod.showcase(d);
			SelfTests.check(auto.size() == 3 && auto.get(0).id().equals("items.drakenei"), "automatic: rarest first, three of them");
			d.showcase.add("tijd.1");
			d.showcase.add("verzamel.wol");
			java.util.List<Achievement> chosen = CollectorMod.showcase(d);
			SelfTests.check(chosen.size() == 1 && chosen.get(0).id().equals("tijd.1"), "chosen: only earned ones, in your order");
			d.earned.clear();
			d.showcase.clear();
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
