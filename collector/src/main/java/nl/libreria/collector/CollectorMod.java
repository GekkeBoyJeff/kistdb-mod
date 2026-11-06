package nl.libreria.collector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import nl.libreria.core.Hud;
import nl.libreria.core.Domain;
import nl.libreria.core.Say;
import nl.libreria.core.Module;
import nl.libreria.core.Nameplate;
import nl.libreria.core.PlayerStore;
import nl.libreria.core.Services;
import nl.libreria.core.Setting;
import nl.libreria.core.Ui;
import nl.libreria.collector.gui.AchievementsGui;
import org.jetbrains.annotations.Nullable;

/**
 * CollectorMod – een Libreria-module. Prestaties zonder beloningen: verzamel items (x van x), fok dieren en dorpelingen,
 * speel uren. Elke prestatie heeft een zeldzaamheid; kies er een als titel en hij zweeft in die kleur boven je naam.
 */
public final class CollectorMod implements ModInitializer {
	public static final Module MODULE = Module.register("collector", "Collector", Items.NETHER_STAR,
			"Achievements without rewards: collect", "items, breed animals, rack up hours.", "Pick a title in its rarity's color.").in(Domain.PROGRESS, "Achievements");
	public static final Setting.Toggle SHOW_TITLE = MODULE.toggle("titel", "Title above your name", true, Items.NAME_TAG,
			"Your chosen title floats above your", "name, in the color of its rarity.", "Choose one: You › Title.").in(Domain.YOU, "Your name");
	public static final Setting.Toggle BIG = MODULE.toggle("groot", "Achievement banner", true, Items.FIREWORK_ROCKET,
			"A banner at the top of your screen and", "fireworks when you earn an achievement,", "or someone else a rare (or better) one.");
	public static final Setting.Toggle OTHERS = MODULE.toggle("anderen", "Others' achievements in chat", true, Items.BELL,
			"A chat line when someone else earns", "an achievement: hover it to see", "what it is and how rare.");

	static final nl.libreria.core.Tips.Tip TIP_FIRST = nl.libreria.core.Tips.register("achievement", Domain.PROGRESS, Items.NETHER_STAR,
			"Your first achievement", "Click [Use as title] in chat to wear it above your name.", "All of them: Progress › Achievements.");

	public static final class Data {
		/** Item-ID's (zonder "minecraft:") die je ooit in je inventory had. */
		public Set<String> items = new HashSet<>();
		/** Behaalde prestaties → moment (epoch-milliseconden). */
		public Map<String, Long> earned = new HashMap<>();
		/** Gefokte soorten (entity-ID's), plus "axolotl:blue". */
		public Set<String> bred = new HashSet<>();
		public int villagers;
		public int cured;
		/** "" = automatisch (hoogste), "-" = geen titel, anders een prestatie-ID. */
		public String title = "";
		public boolean initialized;
		/** {@link Catalog#signature()} at the last check: when it changes, new achievements are given quietly. */
		public int catalog;
		/** Secrets found (ids without "geheim."). */
		public Set<String> secrets = new HashSet<>();
		/** Up to three achievements on your profile ("" or missing = your rarest). */
		public List<String> showcase = new ArrayList<>();
		/** Records you have ever held, and how many times you took one. */
		public Set<String> recordsHeld = new HashSet<>();
		public int recordsTaken;
		/** The trophy you wear before your title (a record id), "" for none. */
		public String trophy = "";
		/** Chat messages sent. */
		public long chats;
		/** Days you played, the last one (yyyy-MM-dd), and days in a row (now and best). */
		public int daysPlayed;
		public String lastDay = "";
		public int streak;
		public int bestStreak;
	}

	public static final PlayerStore<Data> STORE = PlayerStore.create("libreria/collector", Data.class, Data::new, d -> {
		if (d.items == null) d.items = new HashSet<>();
		if (d.earned == null) d.earned = new HashMap<>();
		if (d.bred == null) d.bred = new HashSet<>();
		if (d.title == null) d.title = "";
		if (d.secrets == null) d.secrets = new HashSet<>();
		if (d.showcase == null) d.showcase = new ArrayList<>();
		if (d.recordsHeld == null) d.recordsHeld = new HashSet<>();
		if (d.lastDay == null) d.lastDay = "";
		if (d.trophy == null) d.trophy = "";
	});

	@Override
	public void onInitialize() {
		MODULE.hubEntry(Domain.PROGRESS, 10, Items.NETHER_STAR, "Achievements", AchievementsGui::open,
				"What you've done and what's still to do, in grades. Every one is within reach.");
		Goals.init();
		Secrets.init();
		profileLines();
		MODULE.hubEntry(Domain.PROGRESS, 25, Items.GOLD_BLOCK, "Records", nl.libreria.collector.gui.RecordsWindow::open,
				"The server's records: the longest fall survived, the longest flight, the fastest, the farthest out. Who holds them?");
		nl.libreria.core.DomainWindow.value("Records", p -> {
			int held = Records.held(p.getUUID());
			return held == 0 ? "" : held + " held";
		});
		MODULE.commands(Domain.PROGRESS, "/records – the server's records");
		MODULE.commands(Domain.YOU, "/showcase – choose the achievements on your profile");
		MODULE.hubEntry(Domain.YOU, 20, Items.NAME_TAG, "Title", AchievementsGui::openTitles,
				"Choose the title that floats above your name.");
		Services.provideMenu(Services.MENU_ACHIEVEMENTS, AchievementsGui::open);
		Services.provideProgress(new Services.Progress() {
			@Override
			public int points(ServerPlayer player) {
				return CollectorMod.points(STORE.get(player));
			}

			@Override
			public int bestRarity(ServerPlayer player) {
				Rarity r = best(STORE.get(player));
				return r == null ? -1 : r.ordinal();
			}

			@Override
			public @Nullable Component title(ServerPlayer player) {
				return CollectorMod.title(player);
			}

			@Override
			public @org.jetbrains.annotations.Nullable Component nextGrade(ServerPlayer player, String stat) {
				return CollectorMod.nextGrade(player, stat);
			}

			@Override
			public List<Component> card(ServerPlayer player) {
				return CollectorMod.card(player);
			}

			@Override
			public Services.CardData cardData(ServerPlayer player) {
				Data d = STORE.get(player);
				int earned = 0;
				for (String id : d.earned.keySet()) if (Catalog.get(id) != null) earned++;
				return new Services.CardData(CollectorMod.points(d), earned, Math.max(1, Catalog.all().size()),
						(int) Catalog.countAll(d), Catalog.allItems().size());
			}

			@Override
			public @Nullable Component titleOf(java.util.UUID player) {
				Data d = STORE.get(player);
				return withTrophy(player, d, titleAchievement(d));
			}

			@Override
			public int titleColor(ServerPlayer player) {
				Achievement a = titleAchievement(STORE.get(player));
				net.minecraft.network.chat.TextColor c = a == null ? null : net.minecraft.network.chat.TextColor.fromLegacyFormat(a.rarity().color);
				return c == null ? -1 : c.getValue();
			}
		});
		Nameplate.addLine("collector.title", 10, CollectorMod::title);

		// Build the catalogue once tags are loaded, then show everything in the vanilla advancements screen (L)
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Catalog.build();
			Advancements.inject(server);
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (!success) return;
			Catalog.build();
			Advancements.inject(server);
		});
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer p = handler.player;
			Trophies.migrate(p);
			Secrets.WATCH.put(p.getUUID(), new Secrets.Watch());
			day(p);
			scanNow(p);
			check(p);
			Advancements.sync(p);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int t = server.getTickCount();
			if (t % 20 == 7) scanAll(server);
			if (t % 100 == 13) checkAll(server);
			if (t % 100 == 41) Goals.tick(server);
			if (t % 200 == 59) Records.tick(server);
			Secrets.tick(server);
			if (t % 1200 == 77) for (ServerPlayer p : server.getPlayerList().getPlayers()) day(p);
			Banner.tick(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Banner.clear());
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Secrets.forget(handler.player.getUUID()));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager baby) onVillagerAdded(level, baby);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
			for (String name : new String[] {"achievements", "collection"}) {
				dispatcher.register(Commands.literal(name).executes(ctx -> {
					AchievementsGui.open(ctx.getSource().getPlayerOrException());
					return 1;
				}));
			}
			for (String name : new String[] {"goal", "goals"}) {
				dispatcher.register(Commands.literal(name).executes(ctx -> {
					nl.libreria.collector.gui.GoalWindow.open(ctx.getSource().getPlayerOrException());
					return 1;
				}));
			}
			dispatcher.register(Commands.literal("records").executes(ctx -> {
				nl.libreria.collector.gui.RecordsWindow.open(ctx.getSource().getPlayerOrException());
				return 1;
			}));
			dispatcher.register(Commands.literal("showcase").executes(ctx -> {
				ServerPlayer p = ctx.getSource().getPlayerOrException();
				nl.libreria.collector.gui.ShowcaseWindow.open(p, 0, q -> nl.libreria.core.ProfileWindow.open(q, q.getUUID(), "",
						r -> nl.libreria.core.Start.domain(r, Domain.YOU)));
				return 1;
			}));
			dispatcher.register(Commands.literal("titles").executes(ctx -> {
				AchievementsGui.openTitles(ctx.getSource().getPlayerOrException());
				return 1;
			}).then(Commands.literal("set").then(Commands.argument("achievement", com.mojang.brigadier.arguments.StringArgumentType.word())
					.suggests((ctx, b) -> {
						ServerPlayer p = ctx.getSource().getPlayer();
						if (p != null) for (String id : STORE.get(p).earned.keySet()) b.suggest(id);
						return b.buildFuture();
					})
					.executes(ctx -> {
						ServerPlayer p = ctx.getSource().getPlayerOrException();
						setTitle(p, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "achievement"));
						return 1;
					}))).then(Commands.literal("auto").executes(ctx -> {
						setTitle(ctx.getSource().getPlayerOrException(), "");
						return 1;
					})).then(Commands.literal("none").executes(ctx -> {
						setTitle(ctx.getSource().getPlayerOrException(), "-");
						return 1;
					})));
		});

		MODULE.commands("/achievements – your achievements", "L – the advancements screen has Libreria tabs too");
		MODULE.commands(Domain.YOU, "/titles – choose your title");
		CollectorTests.register();
	}

	/** A new day: days played, days in a row. */
	static void day(ServerPlayer p) {
		if (p instanceof net.fabricmc.fabric.api.entity.FakePlayer) return;
		Data d = STORE.get(p);
		java.time.LocalDate today = java.time.LocalDate.now();
		if (today.toString().equals(d.lastDay)) return;
		d.streak = today.minusDays(1).toString().equals(d.lastDay) ? d.streak + 1 : 1;
		d.bestStreak = Math.max(d.bestStreak, d.streak);
		d.daysPlayed++;
		d.lastDay = today.toString();
		STORE.markDirty(p);
	}

	/** Lines on everyone's profile: achievements, points, records (the main ones), items, server goals; and the showcase. */
	private static void profileLines() {
		nl.libreria.core.Profile.main(30, Items.NETHER_STAR, "Achievements", (s, who, st) -> {
			Data d = STORE.get(who);
			int earned = 0;
			for (String id : d.earned.keySet()) if (Catalog.get(id) != null) earned++;
			return Ui.n(earned);
		});
		nl.libreria.core.Profile.main(40, Items.GOLD_INGOT, "Points", (s, who, st) -> Ui.n(points(STORE.get(who))));
		nl.libreria.core.Profile.main(45, Items.GOLD_BLOCK, "Records", (s, who, st) -> {
			int held = Records.held(who);
			return held == 0 ? "none" : held + " held";
		});
		nl.libreria.core.Profile.showcase(new nl.libreria.core.Profile.Showcase() {
			@Override
			public List<nl.libreria.core.Profile.Shown> of(MinecraftServer server, java.util.UUID who) {
				List<nl.libreria.core.Profile.Shown> out = new ArrayList<>();
				for (Achievement a : showcase(STORE.get(who))) {
					net.minecraft.network.chat.TextColor c = net.minecraft.network.chat.TextColor.fromLegacyFormat(a.rarity().color);
					String grade = Catalog.gradeText(a);
					out.add(new nl.libreria.core.Profile.Shown(new ItemStack(a.icon()), a.name(), c == null ? 0xFFFFFF : c.getValue(),
							List.of(Component.literal(a.rarity().label + (grade == null ? "" : " · " + grade)).withStyle(a.rarity().color),
									Ui.text(a.description(), ChatFormatting.GRAY))));
				}
				return out;
			}

			@Override
			public void choose(ServerPlayer viewer, int slot, java.util.function.Consumer<ServerPlayer> back) {
				nl.libreria.collector.gui.ShowcaseWindow.open(viewer, slot, back);
			}
		});
		nl.libreria.core.Profile.add(50, Items.CHEST, "Items", (s, who, st) -> Ui.n(Catalog.countAll(STORE.get(who))) + " / "
				+ Ui.n(Catalog.allItems().size()));
		nl.libreria.core.Profile.add(70, Items.BELL, "Server goals", (s, who, st) -> {
			int helped = Goals.helpedCount(who), most = Goals.championCount(who);
			if (helped == 0 && Goals.past().isEmpty()) return null;
			return most > 0 ? helped + " · " + most + "x most" : helped + " helped";
		});
	}

	// ---------------------------------------------------------------- verzamelen

	private static void scanAll(MinecraftServer server) {
		if (Catalog.all().isEmpty()) return;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator() || p.isCreative()) continue;
			if (scan(p)) check(p);
		}
	}

	/** Voor het menu: eerst je inventory bijwerken zodat je meteen ziet wat je net hebt opgepakt. */
	public static void scanNow(ServerPlayer p) {
		if (!p.isSpectator() && !p.isCreative() && scan(p)) check(p);
	}

	/** Zet nieuwe items uit de inventory in je verzameling. @return true als er iets nieuws bij kwam */
	static boolean scan(ServerPlayer p) {
		Data d = STORE.get(p);
		boolean changed = false;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.isEmpty()) continue;
			if (d.items.add(BuiltInRegistries.ITEM.getKey(st.getItem()).getPath())) changed = true;
		}
		if (changed) {
			STORE.markDirty(p);
			Advancements.itemsChanged(p);
		}
		return changed;
	}

	// ---------------------------------------------------------------- fokken (vanuit de mixins)

	/** Aangeroepen voor elke gefokte baby (ook eieren van schildpad, kikker en snuffelaar). */
	public static void onBred(@Nullable ServerPlayer player, Animal parent, @Nullable AgeableMob child) {
		if (player == null) return;
		Data d = STORE.get(player);
		String species = BuiltInRegistries.ENTITY_TYPE.getKey(child != null ? child.getType() : parent.getType()).getPath();
		d.bred.add(species);
		if (child instanceof net.minecraft.world.entity.animal.axolotl.Axolotl ax
				&& ax.getVariant() == net.minecraft.world.entity.animal.axolotl.Axolotl.Variant.BLUE) {
			d.bred.add("axolotl:blue");
		}
		STORE.markDirty(player);
		check(player);
	}

	public static void onCured(@Nullable ServerPlayer player) {
		if (player == null) return;
		STORE.get(player).cured++;
		STORE.markDirty(player);
		check(player);
	}

	/**
	 * Dorpelingen fokken zonder speler. Een baby die precies bij een ouder verschijnt die net "moe" is (leeftijd 6000,
	 * de pauze na het fokken) is net geboren; spelers binnen 32 blokken krijgen hem op hun naam.
	 */
	private static void onVillagerAdded(ServerLevel level, Villager baby) {
		if (baby.getAge() != AgeableMob.BABY_START_AGE || baby.tickCount > 1) return;
		boolean parentNearby = false;
		for (Villager v : level.getEntitiesOfClass(Villager.class, baby.getBoundingBox().inflate(2.0))) {
			if (v != baby && v.getAge() == 6000) parentNearby = true;
		}
		if (!parentNearby) return;
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(baby.blockPosition()).inflate(32))) {
			if (p.isSpectator()) continue;
			STORE.get(p).villagers++;
			STORE.markDirty(p);
			check(p);
		}
	}

	// ---------------------------------------------------------------- prestaties behalen

	private static void checkAll(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) check(p);
	}

	static void check(ServerPlayer p) {
		if (Catalog.all().isEmpty()) return;
		Catalog.fresh();
		Data d = STORE.get(p);
		List<Achievement> fresh = new ArrayList<>();
		for (Achievement a : Catalog.all()) {
			if (d.earned.containsKey(a.id())) continue;
			if (a.current(p, d) >= a.target()) fresh.add(a);
		}
		boolean firstTime = !d.initialized, newCatalog = d.catalog != Catalog.signature();
		if (fresh.isEmpty() && !firstTime && !newCatalog) return;
		long now = System.currentTimeMillis();
		for (Achievement a : fresh) {
			d.earned.put(a.id(), now);
			Advancements.complete(p, a);
		}
		STORE.markDirty(p);
		if (firstTime || newCatalog) {
			// What you had already done is given quietly: no banners, fireworks or chat for each one
			d.initialized = true;
			d.catalog = Catalog.signature();
			if (!fresh.isEmpty()) {
				String what = fresh.size() == 1 ? " achievement" : " achievements";
				nl.libreria.core.Notify.info(p, Items.NETHER_STAR, firstTime
						? "You've already earned " + fresh.size() + what
						: fresh.size() + " new" + what + " for what you've done",
						"See them in Progress › Achievements.");
			}
			return;
		}
		for (Achievement a : fresh) announce(p, a);
		tellOthers(p, fresh);
	}

	/**
	 * Everyone else (with "others' achievements in chat" on) gets one chat line for what someone just earned: every
	 * achievement, each one in its rarity's colour, hover it for what it is. Several at once go on the same line.
	 */
	static void tellOthers(ServerPlayer p, List<Achievement> earned) {
		if (earned.isEmpty()) return;
		MutableComponent msg = Say.line(Domain.PROGRESS).append(Nameplate.displayName(p).copy().withStyle(ChatFormatting.WHITE))
				.append(Ui.text(" earned ", ChatFormatting.GRAY));
		for (int i = 0; i < earned.size(); i++) {
			if (i > 0) msg.append(Ui.text(i == earned.size() - 1 ? " and " : ", ", ChatFormatting.GRAY));
			msg.append(chip(earned.get(i)));
		}
		for (ServerPlayer other : p.level().getServer().getPlayerList().getPlayers()) {
			if (other != p && OTHERS.get(other)) other.sendSystemMessage(msg);
		}
	}

	/** For the screenshot test: announce a legendary achievement as if it was just earned. */
	public static void debugAnnounce(ServerPlayer p) {
		for (Achievement a : Catalog.all()) {
			if (a.rarity() == Rarity.LEGENDARY) {
				announce(p, a);
				return;
			}
		}
	}

	static void announce(ServerPlayer p, Achievement a) {
		Rarity r = a.rarity();
		// The whole server sees it: a banner at the top of the screen and fireworks
		Banner.add(p, a);
		if (r.ordinal() >= Rarity.EPIC.ordinal()) {
			nl.libreria.core.Chronicle.write(p, nl.libreria.core.Chronicle.Kind.ACHIEVEMENT, "earned the " + r.label.toLowerCase(java.util.Locale.ROOT)
					+ " achievement \"" + a.name() + "\"", r.ordinal() >= Rarity.LEGENDARY.ordinal());
		}
		if (!BIG.get(p)) {
			Hud.flash(p, Component.literal("✦ Achievement: ").withStyle(ChatFormatting.GRAY).append(Component.literal(a.name()).withStyle(r.color)));
		}
		if (r.ordinal() >= Rarity.EPIC.ordinal()) Ui.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
		else Ui.sound(p, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.4f);

		MutableComponent chip = chip(a);
		nl.libreria.core.Tips.once(p, TIP_FIRST);
		p.sendSystemMessage(Say.line(Domain.PROGRESS).append(Ui.text("Achievement earned: ", ChatFormatting.GRAY)).append(chip)
				.append(Component.literal("  "))
				.append(Component.literal("[Use as title]").withStyle(s -> s.withColor(ChatFormatting.AQUA).withBold(false)
						.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/titles set " + a.id()))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Show ").withStyle(ChatFormatting.GRAY)
								.append(Component.literal("✦ " + a.name() + " ✦").withStyle(a.rarity().color))
								.append(Component.literal(" above your name").withStyle(ChatFormatting.GRAY)))))));
	}

	/** [NicknamesMod] in rariteitskleur, met uitleg als je eroverheen beweegt. */
	public static MutableComponent chip(Achievement a) {
		Component hover = Component.empty()
				.append(Component.literal(a.name()).withStyle(a.rarity().color, ChatFormatting.BOLD))
				.append("\n").append(Component.literal(a.rarity().label).withStyle(a.rarity().color))
				.append("\n").append(Component.literal(a.description()).withStyle(ChatFormatting.GRAY))
				.append("\n").append(Component.literal("+" + a.rarity().points + (a.rarity().points == 1 ? " point" : " points"))
						.withStyle(ChatFormatting.DARK_GRAY));
		return Component.literal("[" + a.name() + "]").withStyle(s -> s.withColor(a.rarity().color)
				.withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	// ---------------------------------------------------------------- titel en punten

	public static int points(Data d) {
		int n = 0;
		for (String id : d.earned.keySet()) {
			Achievement a = Catalog.get(id);
			if (a != null) n += a.rarity().points;
		}
		return n;
	}

	static @Nullable Rarity best(Data d) {
		Rarity best = null;
		for (String id : d.earned.keySet()) {
			Achievement a = Catalog.get(id);
			if (a != null && (best == null || a.rarity().ordinal() > best.ordinal())) best = a.rarity();
		}
		return best;
	}

	/** Your earned achievements, rarest first (newest first within a rarity). */
	public static List<Achievement> earnedRarestFirst(Data d) {
		List<Map.Entry<String, Long>> es = new ArrayList<>(d.earned.entrySet());
		es.sort((x, y) -> {
			Achievement a = Catalog.get(x.getKey()), b = Catalog.get(y.getKey());
			int ra = a == null ? -1 : a.rarity().ordinal(), rb = b == null ? -1 : b.rarity().ordinal();
			if (ra != rb) return Integer.compare(rb, ra);
			return Long.compare(y.getValue(), x.getValue());
		});
		List<Achievement> out = new ArrayList<>();
		for (Map.Entry<String, Long> e : es) {
			Achievement a = Catalog.get(e.getKey());
			if (a != null) out.add(a);
		}
		return out;
	}

	/** The three on your profile: the ones you chose, or else your rarest. */
	public static List<Achievement> showcase(Data d) {
		List<Achievement> out = new ArrayList<>();
		for (String id : d.showcase) {
			Achievement a = Catalog.get(id);
			if (a != null && d.earned.containsKey(id) && !out.contains(a) && out.size() < 3) out.add(a);
		}
		if (!out.isEmpty()) return out;
		List<Achievement> rarest = earnedRarestFirst(d);
		return rarest.size() > 3 ? rarest.subList(0, 3) : rarest;
	}

	/** De titel-prestatie: gekozen, of automatisch de zeldzaamste (bij gelijkspel de nieuwste). */
	public static @Nullable Achievement titleAchievement(Data d) {
		if ("-".equals(d.title)) return null;
		if (!d.title.isEmpty() && d.earned.containsKey(d.title)) {
			Achievement a = Catalog.get(d.title);
			if (a != null) return a;
		}
		Achievement best = null;
		long bestAt = 0;
		for (Map.Entry<String, Long> e : d.earned.entrySet()) {
			Achievement a = Catalog.get(e.getKey());
			if (a == null) continue;
			if (best == null || a.rarity().ordinal() > best.rarity().ordinal()
					|| (a.rarity() == best.rarity() && e.getValue() > bestAt)) {
				best = a;
				bestAt = e.getValue();
			}
		}
		return best;
	}

	public static @Nullable Component title(ServerPlayer p) {
		if (!SHOW_TITLE.get(p)) return null;
		Data d = STORE.get(p);
		return withTrophy(p.getUUID(), d, titleAchievement(d));
	}

	/** The title line: the trophy you wear (if any), then "✦ title ✦" in the colour of its rarity. */
	static @Nullable Component withTrophy(java.util.UUID who, Data d, @Nullable Achievement a) {
		Records.Kind trophy = Trophies.worn(who, d);
		if (a == null && trophy == null) return null;
		MutableComponent out = Component.empty();
		if (trophy != null) out.append(Trophies.glyph(trophy));
		if (trophy != null && a != null) out.append(Component.literal(" "));
		if (a != null) out.append(Component.literal("✦ " + a.name() + " ✦").withStyle(a.rarity().color));
		return out;
	}

	/** Sets the title: an achievement id, "" for automatic (rarest) or "-" for none. */
	public static void setTitle(ServerPlayer p, String id) {
		Data d = STORE.get(p);
		if (!id.isEmpty() && !id.equals("-") && !d.earned.containsKey(id)) {
			nl.libreria.core.Notify.warn(p, Items.NAME_TAG, "Not earned yet", "Earn that achievement first, then wear it.");
			return;
		}
		d.title = id;
		STORE.markDirty(p);
		Ui.sound(p, SoundEvents.ARMOR_EQUIP_GOLD.value(), 0.8f, 1.2f);
		Component t = title(p);
		Component sub = !SHOW_TITLE.get(p) ? Ui.text("Hidden for now: Settings › You › Your name.", ChatFormatting.GRAY)
				: id.equals("-") ? Ui.text("Nothing above your name now.", ChatFormatting.GRAY)
				: t == null ? Ui.text("You have no achievements yet.", ChatFormatting.GRAY)
				: Component.empty().append(Ui.text(id.isEmpty() ? "Your rarest: " : "", ChatFormatting.GRAY)).append(t);
		nl.libreria.core.Notify.card(p, new net.minecraft.world.item.ItemStack(Items.NAME_TAG), nl.libreria.core.gui.Toast.GOOD,
				Ui.text(id.equals("-") ? "No title" : "Title chosen", ChatFormatting.WHITE), sub);
	}

	/** "Next grade: Angler · 37 / 100", or "✔ Every grade earned", for a statistic with a ladder. */
	static @org.jetbrains.annotations.Nullable Component nextGrade(ServerPlayer p, String stat) {
		List<Achievement> grades = Catalog.byStat(stat);
		if (grades.isEmpty()) return null;
		Data d = STORE.get(p);
		for (Achievement a : grades) {
			if (d.earned.containsKey(a.id())) continue;
			long now = a.current(p, d), target = a.target();
			return Component.empty().append(Ui.text("Next grade: ", ChatFormatting.GRAY))
					.append(Component.literal(a.name()).withStyle(a.rarity().color))
					.append(Ui.text(String.format(java.util.Locale.US, "  %,d / %,d ", now, target), ChatFormatting.GRAY))
					.append(Ui.bar(now / (double) Math.max(1, target), 8));
		}
		return Ui.text("✔ Every grade earned", ChatFormatting.GREEN);
	}

	/** Player card lines: points + achievements, completion bar, items collected. */
	public static List<Component> card(ServerPlayer p) {
		Data d = STORE.get(p);
		int total = Math.max(1, Catalog.all().size());
		int earned = 0;
		for (String id : d.earned.keySet()) if (Catalog.get(id) != null) earned++;
		int pct = (int) Math.floor(100.0 * earned / total);
		List<Component> out = new ArrayList<>();
		out.add(Component.literal("★ " + String.format(java.util.Locale.US, "%,d", points(d)) + " points").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(" · " + earned + "/" + total + " achievements").withStyle(ChatFormatting.GRAY)));
		out.add(Component.literal("Completion " + pct + "% ").withStyle(ChatFormatting.WHITE).append(Ui.bar(earned / (double) total, 10)));
		out.add(Component.literal("Items " + String.format(java.util.Locale.US, "%,d / %,d", Catalog.countAll(d), Catalog.allItems().size()))
				.withStyle(ChatFormatting.GRAY));
		return out;
	}

	/** yyyy-MM-dd, e.g. 2026-11-03. */
	public static String date(long millis) {
		return Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();
	}
}
