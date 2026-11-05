package nl.libreria.collector;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import nl.libreria.collector.Achievement.Category;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * Secret achievements: hidden until you earn them (the advancements screen hides them too; the menu shows "???").
 * Silly deaths, odd kills, name tags with a story, strange places and poses, the time of day you play. Found by
 * watching what happens, not by statistics.
 */
final class Secrets {
	/**
	 * One secret: its id (without "geheim."), title, what it was for (shown once earned), picture, rarity, and the
	 * vague clue the menu gives while it's hidden.
	 */
	record Secret(String id, String name, String description, Item icon, Rarity rarity, String clue) {
	}

	/** The clue for the secrets that follow (set per group below). */
	private static String clue = "";

	static final Map<String, Secret> ALL = new LinkedHashMap<>();
	private static final Rarity U = Rarity.UNCOMMON, R = Rarity.RARE, E = Rarity.EPIC;

	private Secrets() {
	}

	private static void s(String id, String name, String description, Item icon, Rarity rarity) {
		ALL.put(id, new Secret(id, name, description, icon, rarity, clue));
	}

	static {
		// How you died
		clue = "Something about how you die.";
		s("zelfmoord.tnt", "Self-Destruct", "Get blown up by your own TNT", Items.TNT, U);
		s("cactus", "Cactus Hugger", "Hug a cactus a little too long", Items.CACTUS, U);
		s("bessen", "Berry Bad Idea", "Die in a sweet berry bush", Items.SWEET_BERRIES, R);
		s("emmer.lava", "Forgot the Bucket", "Burn in lava with a water bucket in your hand", Items.WATER_BUCKET, R);
		s("bed.ontploft", "Intentional Game Design", "Try to sleep in the Nether or the End", Ui.item("red_bed"), U);
		s("bliksem.dood", "Smitten", "Be struck down by lightning", Ui.item("lightning_rod"), E);
		s("aambeeld", "Anvil Headache", "Be flattened by a falling anvil", Items.ANVIL, R);
		s("stalactiet", "Watch Your Head", "Be hit by a falling stalactite", Items.POINTED_DRIPSTONE, R);
		s("stalagmiet", "Pointy End", "Fall onto a stalagmite", Items.DRIPSTONE_BLOCK, R);
		s("bevroren", "Brain Freeze", "Freeze to death in powder snow", Items.POWDER_SNOW_BUCKET, R);
		s("verdronken", "Forgot to Breathe", "Drown", Items.KELP, U);
		s("verhongerd", "Starving Artist", "Starve to death", Items.BOWL, U);
		s("vuurwerk.dood", "Going Out with a Bang", "Die by fireworks", Items.FIREWORK_ROCKET, R);
		s("muur.vlucht", "Splat", "Fly into a wall with an elytra", Items.ELYTRA, R);
		s("leegte", "Into the Void", "Fall out of the world", Items.ENDER_PEARL, U);
		s("stikken", "Wall Hugger", "Suffocate inside a block", Items.GRAVEL, U);
		s("magma", "The Floor Was Lava", "Die standing on magma", Items.MAGMA_BLOCK, U);
		s("geplet", "Sardine", "Be squashed by too many mobs at once", Items.COD, R);
		s("drakenadem", "Bad Breath", "Die in dragon's breath", Items.DRAGON_BREATH, R);
		s("doornen", "Prickly Personality", "Be killed by thorns", Items.ROSE_BUSH, R);
		s("emmer.val", "Bucket? What Bucket?", "Die from a fall with a water bucket in your hand", Items.WATER_BUCKET, R);
		s("niveaus.kwijt", "Easy Come, Easy Go", "Die with 30 levels or more", Items.EXPERIENCE_BOTTLE, U);
		s("snel.dood", "Back So Soon?", "Die within a minute of coming back", Items.SKELETON_SKULL, U);
		s("dure.uitvaart", "Expensive Funeral", "Die with a stack of diamonds on you", Items.DIAMOND, E);
		// What you named, petted, sheared or poked
		clue = "Something you do to a mob, a pet or the chat.";
		s("jeb", "Disco Sheep", "Name a sheep jeb_", Items.NAME_TAG, R);
		s("ondersteboven", "Upside Down", "Name a mob Dinnerbone or Grumm", Items.NAME_TAG, R);
		s("toast", "In Loving Memory", "Name a rabbit Toast", Items.RABBIT_FOOT, R);
		s("sneeuwman", "Snowman's Haircut", "Shear a snow golem", Items.SHEARS, U);
		s("koeiensoep", "Cow Soup", "Get stew from a mooshroom with a bowl", Items.MUSHROOM_STEW, U);
		s("geitenmelk", "Goat Latte", "Milk a goat", Items.MILK_BUCKET, U);
		s("papegaai", "Polly Want a Cookie?", "Offer a parrot a cookie (please don't)", Items.COOKIE, R);
		s("goede.hond", "Good Boy", "Pet your own wolf", Items.BONE, U);
		s("poes", "Purr-fect", "Pet your own cat", Items.STRING, U);
		s("koopman", "Window Shopping", "Talk to a wandering trader", Items.EMERALD, U);
		s("onbeleefd", "Rude!", "Hit a villager", Items.WOODEN_SWORD, U);
		s("gg", "Good Game", "Say gg in chat", Items.PAPER, U);
		s("hallo", "Hello, World", "Say something in chat for the first time", Items.WRITABLE_BOOK, U);
		// Where you've been
		clue = "Somewhere few people go.";
		s("dak.wereld", "Top of the World", "Stand at the build limit", Items.SCAFFOLDING, R);
		s("bodem", "Rock Bottom", "Reach the very bottom of the world", Items.BEDROCK, R);
		s("netherdak", "Above the Roof", "Stand on top of the Nether's ceiling", Items.NETHERRACK, E);
		s("ver.weg", "Are We There Yet?", "Go 10,000 blocks from the middle of the world", Items.COMPASS, R);
		s("horizon", "Beyond the Horizon", "Go 50,000 blocks from the middle of the world", Items.MAP, E);
		s("ruimte", "Space Program", "Fly higher than 1,000 blocks", Items.FIREWORK_ROCKET, E);
		s("supersonisch", "Supersonic", "Move faster than 60 blocks a second", Items.FEATHER, R);
		s("watersprong", "Cannonball!", "Fall 60 blocks into water and live", Items.WATER_BUCKET, R);
		// How you look and what you carry
		clue = "Something you wear, carry or do with your eyes.";
		s("draaierig", "Dizzy", "Spin around five times in three seconds", Items.COMPASS, U);
		s("sterren", "Stargazer", "Look straight up at the night sky for half a minute", Ui.item("spyglass"), R);
		s("navel", "Navel Gazer", "Stare at your feet for a whole minute", Items.LEATHER_BOOTS, U);
		s("brandend", "Burning Desire", "Be on fire for half a minute and live", Items.BLAZE_POWDER, R);
		s("rakelings", "Close Call", "Get down to half a heart and live", Items.GOLDEN_APPLE, U);
		s("bliksem", "Shocking!", "Be struck by lightning and live", Ui.item("lightning_rod"), E);
		s("mode", "Fashion Disaster", "Wear four pieces of armor, each of a different kind", Items.LEATHER_CHESTPLATE, U);
		s("goud.pak", "Bling Bling", "Wear a full set of gold armor", Items.GOLDEN_HELMET, U);
		s("pompoenhoofd", "Pumpkin Head", "Wear a pumpkin on your head for ten minutes", Items.CARVED_PUMPKIN, R);
		s("tijd.plaats", "Time and Place", "Hold a clock in one hand and a compass in the other", Items.CLOCK, U);
		s("aarde.rijk", "Dirt Rich", "Fill your whole inventory with dirt", Items.DIRT, R);
		s("snacklade", "Snack Drawer", "Fill your hotbar with food", Items.BREAD, U);
		s("wandelbom", "Walking Bomb", "Carry 64 TNT", Items.TNT, R);
		s("groot.brein", "Big Brain", "Reach level 100", Items.EXPERIENCE_BOTTLE, E);
		s("katten", "Crazy Cat Person", "Have ten of your cats around you", Items.COD, E);
		s("roedel", "Pack Leader", "Have ten of your wolves around you", Items.BONE, E);
		// When you play
		clue = "Something about when you play.";
		s("gras", "Touch Grass", "Play four hours in one go", Items.GRASS_BLOCK, R);
		s("nacht", "Should Be Sleeping", "Play between three and five at night", Ui.item("red_bed"), U);
		s("vroeg", "Early Bird", "Play between five and seven in the morning", Items.CLOCK, U);
		s("kerst", "Ho Ho Ho", "Play at Christmas", Items.SNOWBALL, R);
		s("halloween", "Spooky Season", "Play on Halloween", Items.JACK_O_LANTERN, R);
		s("nieuwjaar", "Fresh Start", "Play on New Year's Day", Items.FIREWORK_ROCKET, R);
		s("grap", "Fooled You", "Play on the first of April", Items.SLIME_BALL, R);
		// Doing a lot of one thing, fast
		clue = "Doing one thing a lot, or at an odd moment.";
		s("springen", "Can't Stop Jumping", "Jump 100 times in a minute", Items.RABBIT_FOOT, U);
		s("bel", "Ding Dong", "Ring a bell 30 times in a minute", Items.BELL, U);
		s("orkest", "One-Man Band", "Play 100 notes in a minute", Items.NOTE_BLOCK, U);
		s("hete.taart", "Hot Cake", "Eat cake while on fire", Items.CAKE, R);
		s("regenvis", "Rainy Day Fisher", "Catch something while it rains", Items.FISHING_ROD, U);
		// How you won
		clue = "An unusual way to win a fight.";
		s("draak.bed", "Sweet Dreams, Dragon", "Finish off the Ender Dragon with a bed", Items.DRAGON_HEAD, E);
		s("blote.vuist", "Bare-Knuckle Boxer", "Defeat a creeper with your bare hands", Items.GUNPOWDER, R);
		s("vismep", "Fish Slap", "Defeat a mob with a fish", Items.COD, R);
		s("sneeuwbal", "Snowball's Chance", "Defeat a mob with a snowball", Items.SNOWBALL, R);
		s("eitje", "Eggsecution", "Defeat a mob with an egg", Items.EGG, R);
		s("kip.ei", "Chicken or the Egg", "Defeat a chicken with an egg", Items.CHICKEN, E);
		s("van.boven", "Death From Above", "Defeat a mob while falling 20 blocks", Items.FEATHER, R);
		s("varkensruiter", "Pork Cavalry", "Defeat a mob while riding a pig", Items.SADDLE, E);
		s("vijf", "Five in a Row", "Defeat five mobs within five seconds", Items.IRON_SWORD, R);
		s("vuurwerk.kill", "Showtime", "Defeat a mob with a firework", Items.CROSSBOW, R);
	}

	/** The clue for a hidden secret achievement ("geheim.cactus"), or null if it isn't one. */
	static @Nullable String clue(String achievementId) {
		Secret s = achievementId.startsWith("geheim.") ? ALL.get(achievementId.substring("geheim.".length())) : null;
		return s == null ? null : s.clue();
	}

	/** Builds the secret achievements and the ladder for finding them. */
	static void build() {
		for (Secret s : ALL.values()) {
			Catalog.addSecret(new Achievement("geheim." + s.id(), Category.GEHEIM, s.name(), s.description(), s.icon(), s.rarity(),
					Catalog.goal(1, (p, d) -> d.secrets.contains(s.id()) ? 1 : 0), null));
		}
		Catalog.Counter found = new Catalog.Counter((p, d) -> d.secrets.stream().filter(ALL::containsKey).count());
		Catalog.ladder(Category.GEHEIM, "geheimen", Items.ENDER_EYE, "Find a secret|Find %,d secrets", found, 1,
				Catalog.r(Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC, Rarity.LEGENDARY, Rarity.MYTHIC),
				1, "Curious Cat", 5, "Snoop", 15, "Sleuth", 40, "Secret Keeper", ALL.size(), "Know-It-All");
	}

	/** Earned: kept, and the achievement comes with the next check (right away). */
	static void award(ServerPlayer p, String id) {
		if (p instanceof net.fabricmc.fabric.api.entity.FakePlayer || !ALL.containsKey(id)) return;
		CollectorMod.Data d = CollectorMod.STORE.get(p);
		if (d.secrets.add(id)) {
			CollectorMod.STORE.markDirty(p);
			CollectorMod.check(p);
		}
	}

	// ---------------------------------------------------------------- watching

	/** What we follow per player from tick to tick. */
	static final class Watch {
		double lastFall;
		float lastYaw;
		final ArrayDeque<Float> turns = new ArrayDeque<>();
		int lookUp, lookDown, fire, pumpkin;
		double x, z;
		boolean seen;
		int respawnTick = -100000;
		final ArrayDeque<Integer> kills = new ArrayDeque<>();
		long joined = System.currentTimeMillis();
		int jumps = -1, bells = -1, notes = -1, cake = -1, fish = -1, hit = -1;
		int minuteTick;
		double flight;
		boolean flying;
		/** Where you were a second ago (NaN at first: no speed yet). */
		double sx = Double.NaN, sz = Double.NaN;
		boolean teleported;
	}

	static final Map<UUID, Watch> WATCH = new HashMap<>();

	static Watch watch(ServerPlayer p) {
		return WATCH.computeIfAbsent(p.getUUID(), k -> new Watch());
	}

	static void init() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			try {
				if (entity instanceof ServerPlayer p) beforeDeath(p, source);
			} catch (RuntimeException e) {
				nl.libreria.core.Libreria.LOG.warn("Secrets: could not look at a death", e);
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			try {
				if (entity instanceof ServerPlayer p) afterDeath(p, source);
				else onKill(entity, source);
			} catch (RuntimeException e) {
				nl.libreria.core.Libreria.LOG.warn("Secrets: could not look at a death", e);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			Watch w = watch(newPlayer);
			w.respawnTick = newPlayer.level().getServer().getTickCount();
			w.lastFall = 0;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp) {
				try {
					onUse(sp, entity);
				} catch (RuntimeException e) {
					nl.libreria.core.Libreria.LOG.warn("Secrets: could not look at a click", e);
				}
			}
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp && type(entity).equals("villager")) award(sp, "onbeleefd");
			return InteractionResult.PASS;
		});
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
			String text = message.signedContent().trim();
			award(sender, "hallo");
			if (text.equalsIgnoreCase("gg")) award(sender, "gg");
			CollectorMod.Data d = CollectorMod.STORE.get(sender);
			d.chats++;
			CollectorMod.STORE.markDirty(sender);
		});
	}

	static void forget(UUID id) {
		WATCH.remove(id);
	}

	private static String type(Entity e) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
	}

	private static String damage(DamageSource source) {
		return source.typeHolder().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
	}

	private static boolean holding(ServerPlayer p, Item item) {
		return p.getMainHandItem().is(item) || p.getOffhandItem().is(item);
	}

	/** Before the body is emptied: what you had on you. */
	private static void beforeDeath(ServerPlayer p, DamageSource source) {
		String type = damage(source);
		if (type.equals("lava") && holding(p, Items.WATER_BUCKET)) award(p, "emmer.lava");
		if (type.equals("fall") && holding(p, Items.WATER_BUCKET)) award(p, "emmer.val");
		if (p.experienceLevel >= 30) award(p, "niveaus.kwijt");
		if (p.getInventory().countItem(Items.DIAMOND) >= 64) award(p, "dure.uitvaart");
	}

	private static void afterDeath(ServerPlayer p, DamageSource source) {
		String type = damage(source);
		switch (type) {
			case "cactus" -> award(p, "cactus");
			case "sweet_berry_bush" -> award(p, "bessen");
			case "bad_respawn_point" -> award(p, "bed.ontploft");
			case "lightning_bolt" -> award(p, "bliksem.dood");
			case "falling_anvil" -> award(p, "aambeeld");
			case "falling_stalactite" -> award(p, "stalactiet");
			case "stalagmite" -> award(p, "stalagmiet");
			case "freeze" -> award(p, "bevroren");
			case "drown" -> award(p, "verdronken");
			case "starve" -> award(p, "verhongerd");
			case "fireworks" -> award(p, "vuurwerk.dood");
			case "fly_into_wall" -> award(p, "muur.vlucht");
			case "out_of_world" -> award(p, "leegte");
			case "in_wall" -> award(p, "stikken");
			case "hot_floor" -> award(p, "magma");
			case "cramming" -> award(p, "geplet");
			case "dragon_breath" -> award(p, "drakenadem");
			case "thorns" -> award(p, "doornen");
			default -> {
			}
		}
		if ((type.equals("explosion") || type.equals("player_explosion")) && source.getEntity() == p) award(p, "zelfmoord.tnt");
		Watch w = watch(p);
		if (p.level().getServer().getTickCount() - w.respawnTick < 60 * 20) award(p, "snel.dood");
	}

	private static void onKill(LivingEntity victim, DamageSource source) {
		String victimType = type(victim);
		if (victimType.equals("ender_dragon") && damage(source).equals("bad_respawn_point")) {
			ServerPlayer near = nearest(victim, 160);
			if (near != null) award(near, "draak.bed");
		}
		if (!(source.getEntity() instanceof ServerPlayer killer)) return;
		Entity direct = source.getDirectEntity();
		String directType = direct == null ? "" : type(direct);
		ItemStack hand = killer.getMainHandItem();
		if (direct == killer) {
			if (victimType.equals("creeper") && hand.isEmpty()) award(killer, "blote.vuist");
			String item = BuiltInRegistries.ITEM.getKey(hand.getItem()).getPath();
			if (item.equals("cod") || item.equals("salmon") || item.equals("tropical_fish") || item.equals("pufferfish")
					|| item.equals("cooked_cod") || item.equals("cooked_salmon")) award(killer, "vismep");
		}
		if (directType.equals("snowball")) award(killer, "sneeuwbal");
		if (directType.equals("egg")) {
			award(killer, "eitje");
			if (victimType.equals("chicken")) award(killer, "kip.ei");
		}
		if (directType.equals("firework_rocket")) award(killer, "vuurwerk.kill");
		if (killer.fallDistance >= 20) award(killer, "van.boven");
		if (killer.getVehicle() != null && type(killer.getVehicle()).equals("pig")) award(killer, "varkensruiter");
		// Five within five seconds (also a record)
		Watch w = watch(killer);
		int now = killer.level().getServer().getTickCount();
		w.kills.addLast(now);
		while (!w.kills.isEmpty() && now - w.kills.peekFirst() > 100) w.kills.removeFirst();
		if (w.kills.size() >= 5) award(killer, "vijf");
	}

	private static @Nullable ServerPlayer nearest(Entity e, double range) {
		ServerPlayer best = null;
		double bestD = range * range;
		for (ServerPlayer p : e.level().getServer().getPlayerList().getPlayers()) {
			if (p.level() != e.level() || p.isSpectator()) continue;
			double d = p.distanceToSqr(e);
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	private static void onUse(ServerPlayer p, Entity target) {
		ItemStack hand = p.getMainHandItem();
		String t = type(target);
		if (hand.is(Items.NAME_TAG) && hand.has(DataComponents.CUSTOM_NAME)) {
			String name = hand.getHoverName().getString();
			if (t.equals("sheep") && name.equals("jeb_")) award(p, "jeb");
			if (target instanceof LivingEntity && (name.equals("Dinnerbone") || name.equals("Grumm"))) award(p, "ondersteboven");
			if (t.equals("rabbit") && name.equals("Toast")) award(p, "toast");
		}
		if (t.equals("snow_golem") && hand.is(Items.SHEARS)) award(p, "sneeuwman");
		if (t.equals("mooshroom") && hand.is(Items.BOWL)) award(p, "koeiensoep");
		if (t.equals("goat") && hand.is(Items.BUCKET)) award(p, "geitenmelk");
		if (t.equals("parrot") && hand.is(Items.COOKIE)) award(p, "papegaai");
		if (t.equals("wandering_trader")) award(p, "koopman");
		if (hand.isEmpty() && target instanceof TamableAnimal pet && pet.isOwnedBy(p)) {
			if (t.equals("wolf")) award(p, "goede.hond");
			if (t.equals("cat")) award(p, "poes");
		}
	}

	/** Every tick: falls, turns, poses, speed, places, what you carry, the time. */
	static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p instanceof net.fabricmc.fabric.api.entity.FakePlayer || p.isSpectator()) continue;
			try {
				tick(server, p, tick);
			} catch (RuntimeException e) {
				nl.libreria.core.Libreria.LOG.warn("Secrets: could not follow {}", p.getName().getString(), e);
			}
		}
	}

	private static void tick(MinecraftServer server, ServerPlayer p, int tick) {
		Watch w = watch(p);
		boolean alive = p.isAlive() && !p.isDeadOrDying();
		// Landing: a fall into water, and the longest fall survived (a record)
		double fall = p.fallDistance;
		if (alive && w.lastFall >= 60 && p.isInWater()) award(p, "watersprong");
		if (alive && w.lastFall > 3 && fall < w.lastFall && (p.onGround() || p.isInWater())) Records.offer(p, Records.Kind.FALL, w.lastFall);
		w.lastFall = fall;
		// Spinning: degrees turned in the last three seconds
		float yaw = p.getYRot();
		if (w.seen) {
			w.turns.addLast(Math.abs(Mth.wrapDegrees(yaw - w.lastYaw)));
			if (w.turns.size() > 60) w.turns.removeFirst();
			float sum = 0;
			for (float f : w.turns) sum += f;
			if (sum >= 5 * 360) award(p, "draaierig");
		}
		w.lastYaw = yaw;
		// Looking up at the stars, down at your feet
		float pitch = p.getXRot();
		long day = server.overworld().getOverworldClockTime() % 24000L;
		boolean night = day > 13000 && day < 23000;
		boolean sky = p.level().canSeeSky(p.blockPosition().above());
		w.lookUp = pitch <= -80 && night && sky ? w.lookUp + 1 : 0;
		if (w.lookUp >= 30 * 20) award(p, "sterren");
		w.lookDown = pitch >= 80 ? w.lookDown + 1 : 0;
		if (w.lookDown >= 60 * 20) award(p, "navel");
		w.fire = p.isOnFire() && alive ? w.fire + 1 : 0;
		if (w.fire >= 30 * 20) award(p, "brandend");
		// Flight: how far, for the record
		if (p.isFallFlying()) {
			if (w.flying) w.flight += Math.sqrt((p.getX() - w.x) * (p.getX() - w.x) + (p.getZ() - w.z) * (p.getZ() - w.z));
			w.flying = true;
		} else if (w.flying) {
			Records.offer(p, Records.Kind.FLIGHT, w.flight);
			w.flight = 0;
			w.flying = false;
		}
		// The hardest hit: damage dealt this tick
		int hit = Ui.customStat(p, "damage_dealt");
		if (w.hit >= 0 && hit > w.hit) Records.offer(p, Records.Kind.HIT, (hit - w.hit) / 20.0);
		w.hit = hit;
		if ((tick + p.getId()) % 20 == 0) second(server, p, w, tick, alive);
		w.x = p.getX();
		w.z = p.getZ();
		w.seen = true;
	}

	/** Once a second. */
	private static void second(MinecraftServer server, ServerPlayer p, Watch w, int tick, boolean alive) {
		String dim = p.level().dimension().identifier().toString();
		double y = p.getY();
		boolean overworld = dim.equals("minecraft:overworld");
		if (overworld && y >= 319) award(p, "dak.wereld");
		if (overworld && y <= -62 && alive) award(p, "bodem");
		if (dim.equals("minecraft:the_nether") && y >= 128) award(p, "netherdak");
		if (y >= 1000) award(p, "ruimte");
		double far = Math.sqrt(p.getX() * p.getX() + p.getZ() * p.getZ());
		if (overworld) {
			if (far >= 10000) award(p, "ver.weg");
			if (far >= 50000) award(p, "horizon");
			Records.offer(p, Records.Kind.DISTANCE, far);
			Records.offer(p, Records.Kind.DEPTH, y);
		}
		if (alive && p.getHealth() <= 1.0f) award(p, "rakelings");
		DamageSource last = p.getLastDamageSource();
		if (alive && last != null && damage(last).equals("lightning_bolt")) award(p, "bliksem");
		// What you wear and hold
		String[] kinds = new String[4];
		int n = 0;
		boolean gold = true;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			String id = BuiltInRegistries.ITEM.getKey(p.getItemBySlot(slot).getItem()).getPath();
			String kind = material(id);
			if (kind == null) {
				gold = false;
				continue;
			}
			if (!kind.equals("golden")) gold = false;
			kinds[n++] = kind;
		}
		if (n == 4 && java.util.Arrays.stream(kinds).distinct().count() == 4) award(p, "mode");
		if (n == 4 && gold) award(p, "goud.pak");
		w.pumpkin = p.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN) ? w.pumpkin + 1 : 0;
		if (w.pumpkin >= 10 * 60) award(p, "pompoenhoofd");
		if ((p.getMainHandItem().is(Items.CLOCK) && p.getOffhandItem().is(Items.COMPASS))
				|| (p.getMainHandItem().is(Items.COMPASS) && p.getOffhandItem().is(Items.CLOCK))) award(p, "tijd.plaats");
		var inv = p.getInventory();
		boolean dirt = true, snacks = true;
		for (int i = 0; i < 36; i++) {
			ItemStack st = inv.getItem(i);
			if (!st.is(Items.DIRT)) dirt = false;
			if (i < 9 && !st.has(DataComponents.FOOD)) snacks = false;
		}
		if (dirt) award(p, "aarde.rijk");
		if (snacks) award(p, "snacklade");
		if (inv.countItem(Items.TNT) >= 64) award(p, "wandelbom");
		if (p.experienceLevel >= 100) award(p, "groot.brein");
		Records.offer(p, Records.Kind.LEVELS, p.experienceLevel);
		// Speed over the last second (a teleport doesn't count)
		if (w.seen && alive) {
			double moved = Math.sqrt((p.getX() - w.sx) * (p.getX() - w.sx) + (p.getZ() - w.sz) * (p.getZ() - w.sz));
			if (moved < 200 && !w.teleported) {
				if (moved >= 60) award(p, "supersonisch");
				Records.offer(p, Records.Kind.SPEED, moved);
			}
		}
		w.sx = p.getX();
		w.sz = p.getZ();
		w.teleported = false;
		// Pets around you
		if (tick % 100 == 0) {
			int cats = 0, wolves = 0;
			for (TamableAnimal pet : p.level().getEntitiesOfClass(TamableAnimal.class, new AABB(p.blockPosition()).inflate(16))) {
				if (!pet.isOwnedBy(p)) continue;
				String t = type(pet);
				if (t.equals("cat")) cats++;
				if (t.equals("wolf")) wolves++;
			}
			if (cats >= 10) award(p, "katten");
			if (wolves >= 10) award(p, "roedel");
		}
		// How long you've been here, and when
		long session = System.currentTimeMillis() - w.joined;
		if (session >= 4L * 3600 * 1000) award(p, "gras");
		Records.offer(p, Records.Kind.SESSION, session / 3_600_000.0);
		Records.offer(p, Records.Kind.ALIVE, Ui.customStat(p, "time_since_death") / 72000.0);
		LocalDateTime now = LocalDateTime.now();
		int hour = now.getHour(), month = now.getMonthValue(), date = now.getDayOfMonth();
		if (hour >= 3 && hour < 5) award(p, "nacht");
		if (hour >= 5 && hour < 7) award(p, "vroeg");
		if (month == 12 && date >= 24 && date <= 26) award(p, "kerst");
		if (month == 10 && date == 31) award(p, "halloween");
		if (month == 1 && date == 1) award(p, "nieuwjaar");
		if (month == 4 && date == 1) award(p, "grap");
		// Cake on fire, fishing in the rain (since the last second)
		int cake = Ui.customStat(p, "eat_cake_slice"), fish = Ui.customStat(p, "fish_caught");
		if (w.cake >= 0 && cake > w.cake && p.isOnFire()) award(p, "hete.taart");
		if (w.fish >= 0 && fish > w.fish && p.level().isRainingAt(p.blockPosition().above())) award(p, "regenvis");
		w.cake = cake;
		w.fish = fish;
		// A lot in one minute
		if (tick - w.minuteTick >= 60 * 20 || w.jumps < 0) {
			int jumps = Ui.customStat(p, "jump"), bells = Ui.customStat(p, "bell_ring"), notes = Ui.customStat(p, "play_noteblock");
			if (w.jumps >= 0) {
				if (jumps - w.jumps >= 100) award(p, "springen");
				if (bells - w.bells >= 30) award(p, "bel");
				if (notes - w.notes >= 100) award(p, "orkest");
			}
			w.jumps = jumps;
			w.bells = bells;
			w.notes = notes;
			w.minuteTick = tick;
		}
	}

	/** "leather", "golden"… for armor, or null. */
	private static @Nullable String material(String id) {
		if (id.equals("turtle_helmet")) return "turtle";
		for (String m : new String[] {"leather", "chainmail", "copper", "iron", "golden", "diamond", "netherite"}) {
			if (id.startsWith(m + "_") && (id.endsWith("_helmet") || id.endsWith("_chestplate") || id.endsWith("_leggings")
					|| id.endsWith("_boots"))) return m;
		}
		return null;
	}

	/** For the self-test: the secrets a player has, by id. */
	static List<String> of(ServerPlayer p) {
		return new ArrayList<>(CollectorMod.STORE.get(p).secrets);
	}
}
