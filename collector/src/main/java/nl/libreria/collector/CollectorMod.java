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
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
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
	public static final Module MODULE = Module.register("collector", "Verzamelaar", Items.NETHER_STAR,
			"Prestaties zonder beloningen: verzamel", "items, fok dieren, speel uren. Kies een", "titel in de kleur van de zeldzaamheid.");
	public static final Setting.Toggle SHOW_TITLE = MODULE.toggle("titel", "Titel boven je naam", true, Items.NAME_TAG,
			"Je gekozen titel zweeft boven je naam,", "in de kleur van de zeldzaamheid.", "Kiezen: /titel");
	public static final Setting.Toggle BIG = MODULE.toggle("groot", "Grote melding in beeld", true, Items.FIREWORK_ROCKET,
			"Een titel midden in beeld bij een", "zeldzame (of betere) prestatie.");
	public static final Setting.Toggle OTHERS = MODULE.toggle("anderen", "Prestaties van anderen", true, Items.BELL,
			"Een chatbericht als iemand anders een", "ongewone (of betere) prestatie haalt.");

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
	}

	public static final PlayerStore<Data> STORE = PlayerStore.create("libreria/collector", Data.class, Data::new, d -> {
		if (d.items == null) d.items = new HashSet<>();
		if (d.earned == null) d.earned = new HashMap<>();
		if (d.bred == null) d.bred = new HashSet<>();
		if (d.title == null) d.title = "";
	});

	@Override
	public void onInitialize() {
		MODULE.hubEntry(Items.NETHER_STAR, "Prestaties", ChatFormatting.GOLD, AchievementsGui::open,
				"Wat je verzameld hebt, wat je nog mist,", "en je titel kiezen.");
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
		});
		Nameplate.addLine("collector.title", 10, CollectorMod::title);

		ServerLifecycleEvents.SERVER_STARTED.register(server -> Catalog.build());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int t = server.getTickCount();
			if (t % 20 == 7) scanAll(server);
			if (t % 100 == 13) checkAll(server);
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager baby) onVillagerAdded(level, baby);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
			for (String name : new String[] {"prestaties", "verzamelaar"}) {
				dispatcher.register(Commands.literal(name).executes(ctx -> {
					AchievementsGui.open(ctx.getSource().getPlayerOrException());
					return 1;
				}));
			}
			dispatcher.register(Commands.literal("titel").executes(ctx -> {
				AchievementsGui.openTitles(ctx.getSource().getPlayerOrException());
				return 1;
			}));
		});

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
		if (changed) STORE.markDirty(p);
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
		Data d = STORE.get(p);
		List<Achievement> fresh = new ArrayList<>();
		for (Achievement a : Catalog.all()) {
			if (d.earned.containsKey(a.id())) continue;
			if (a.current(p, d) >= a.target()) fresh.add(a);
		}
		if (fresh.isEmpty() && d.initialized) return;
		long now = System.currentTimeMillis();
		for (Achievement a : fresh) d.earned.put(a.id(), now);
		STORE.markDirty(p);
		if (!d.initialized) {
			// Eerste keer: wat je al gedaan had stilletjes toekennen, geen spam
			d.initialized = true;
			if (!fresh.isEmpty()) {
				p.sendSystemMessage(Ui.prefix("Verzamelaar").append(Ui.text("Je hebt al " + fresh.size()
						+ " prestaties op zak! Bekijk ze met /prestaties.", ChatFormatting.GOLD)));
			}
			return;
		}
		for (Achievement a : fresh) announce(p, a);
	}

	static void announce(ServerPlayer p, Achievement a) {
		Rarity r = a.rarity();
		boolean big = r.ordinal() >= Rarity.ZELDZAAM.ordinal();
		if (big && BIG.get(p)) {
			p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
			p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("✦ " + r.label + " ✦").withStyle(r.color)));
			p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(a.name()).withStyle(r.color, ChatFormatting.BOLD)));
		} else {
			Hud.flash(p, Component.literal("✦ Prestatie: ").withStyle(ChatFormatting.GRAY).append(Component.literal(a.name()).withStyle(r.color)));
		}
		if (r.ordinal() >= Rarity.EPISCH.ordinal()) Ui.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
		else Ui.sound(p, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.4f);

		MutableComponent chip = chip(a);
		p.sendSystemMessage(Ui.prefix("Verzamelaar").append(Ui.text("Prestatie behaald: ", ChatFormatting.GRAY)).append(chip));
		if (r.ordinal() >= Rarity.ONGEWOON.ordinal()) {
			Component msg = Ui.prefix("Verzamelaar").append(Nameplate.displayName(p).copy().withStyle(ChatFormatting.WHITE))
					.append(Ui.text(" behaalde ", ChatFormatting.GRAY)).append(chip);
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

	public static String date(long millis) {
		var d = Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
		return d.getDayOfMonth() + "-" + d.getMonthValue() + "-" + d.getYear();
	}
}
