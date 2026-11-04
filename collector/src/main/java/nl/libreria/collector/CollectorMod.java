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
			"Achievements without rewards: collect", "items, breed animals, rack up hours.", "Pick a title in its rarity's color.");
	public static final Setting.Toggle SHOW_TITLE = MODULE.toggle("titel", "Title above your name", true, Items.NAME_TAG,
			"Your chosen title floats above your", "name, in the color of its rarity.", "Choose one: /titles");
	public static final Setting.Toggle BIG = MODULE.toggle("groot", "Achievement banner", true, Items.FIREWORK_ROCKET,
			"A banner at the top of your screen and", "fireworks when you earn an achievement,", "or someone else a rare (or better) one.");
	public static final Setting.Toggle OTHERS = MODULE.toggle("anderen", "Others' achievements", true, Items.BELL,
			"A chat message when someone else earns", "a rare (or better) achievement.");

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
	}

	public static final PlayerStore<Data> STORE = PlayerStore.create("libreria/collector", Data.class, Data::new, d -> {
		if (d.items == null) d.items = new HashSet<>();
		if (d.earned == null) d.earned = new HashMap<>();
		if (d.bred == null) d.bred = new HashSet<>();
		if (d.title == null) d.title = "";
	});

	@Override
	public void onInitialize() {
		MODULE.hubEntry(Items.NETHER_STAR, "Achievements", ChatFormatting.GOLD, AchievementsGui::open,
				"See what you've collected and what's", "still missing, and pick your title.");
		MODULE.hubEntry(nl.libreria.core.Module.Category.YOU, Items.NAME_TAG, "Title", ChatFormatting.LIGHT_PURPLE,
				AchievementsGui::openTitles, "Choose the title that floats", "above your name.");
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
			scanNow(p);
			check(p);
			Advancements.sync(p);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int t = server.getTickCount();
			if (t % 20 == 7) scanAll(server);
			if (t % 100 == 13) checkAll(server);
			Banner.tick(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Banner.clear());
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

		MODULE.commands("/achievements – your achievements", "/titles – choose your title", "L (advancements) – Libreria tabs");
		CollectorTests.register();
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
				p.sendSystemMessage(Ui.prefix("Collector").append(Ui.text(firstTime
						? "You've already earned " + fresh.size() + what + "! See them with /achievements."
						: "There are new achievements, and you had already done enough for " + fresh.size() + what
								+ ". See them with /achievements.", ChatFormatting.GOLD)));
			}
			return;
		}
		for (Achievement a : fresh) announce(p, a);
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
		p.sendSystemMessage(Ui.prefix("Collector").append(Ui.text("Achievement earned: ", ChatFormatting.GRAY)).append(chip)
				.append(Component.literal("  "))
				.append(Component.literal("[Use as title]").withStyle(s -> s.withColor(ChatFormatting.AQUA).withBold(true)
						.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/titles set " + a.id()))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Show ").withStyle(ChatFormatting.GRAY)
								.append(Component.literal("✦ " + a.name() + " ✦").withStyle(a.rarity().color))
								.append(Component.literal(" above your name").withStyle(ChatFormatting.GRAY)))))));
		// Others hear about the rare ones (with this many achievements, every common one would be a lot of chat)
		if (r.ordinal() >= Rarity.RARE.ordinal()) {
			Component msg = Ui.prefix("Collector").append(Nameplate.displayName(p).copy().withStyle(ChatFormatting.WHITE))
					.append(Ui.text(" earned ", ChatFormatting.GRAY)).append(chip);
			for (ServerPlayer other : p.level().getServer().getPlayerList().getPlayers()) {
				if (other != p && OTHERS.get(other)) other.sendSystemMessage(msg);
			}
		}
	}

	/** [NicknamesMod] in rariteitskleur, met uitleg als je eroverheen beweegt. */
	public static MutableComponent chip(Achievement a) {
		Component hover = Component.empty()
				.append(Component.literal(a.name()).withStyle(a.rarity().color, ChatFormatting.BOLD))
				.append("\n").append(Component.literal(a.rarity().label).withStyle(a.rarity().color))
				.append("\n").append(Component.literal(a.description()).withStyle(ChatFormatting.GRAY));
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
		Achievement a = titleAchievement(STORE.get(p));
		if (a == null) return null;
		return Component.literal("✦ " + a.name() + " ✦").withStyle(a.rarity().color);
	}

	/** Sets the title: an achievement id, "" for automatic (rarest) or "-" for none. */
	public static void setTitle(ServerPlayer p, String id) {
		Data d = STORE.get(p);
		if (!id.isEmpty() && !id.equals("-") && !d.earned.containsKey(id)) {
			p.sendSystemMessage(Ui.prefix("Collector").append(Ui.text("You haven't earned that achievement yet.", ChatFormatting.RED)));
			return;
		}
		d.title = id;
		STORE.markDirty(p);
		Ui.sound(p, SoundEvents.ARMOR_EQUIP_GOLD.value(), 0.8f, 1.2f);
		Component t = title(p);
		p.sendSystemMessage(Ui.prefix("Collector").append(id.equals("-") ? Ui.text("You no longer show a title.", ChatFormatting.GRAY)
				: Ui.text(id.isEmpty() ? "Automatic title (your rarest): " : "Your title is now ", ChatFormatting.GRAY)
						.append(t == null ? Component.literal("none") : t)));
		if (!SHOW_TITLE.get(p)) {
			p.sendSystemMessage(Ui.text("Titles are hidden in your settings (Libreria settings → Collector).", ChatFormatting.DARK_GRAY));
		}
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
