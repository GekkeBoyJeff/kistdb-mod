package nl.handig.glans;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import eu.pb4.polymer.autohost.api.AutoHostUtils;
import eu.pb4.polymer.autohost.api.ResourcePackDataProvider;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import nl.handig.kern.Module;
import nl.handig.kern.PlayerStore;
import nl.handig.kern.Setting;
import nl.handig.kern.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * Glans – een Handig-module. Geanimeerde enchant-effecten op wapens, gereedschap en harnas (vlammen bij Fire Aspect,
 * bubbels bij Respiration…), een eigen plaatje per betoverd boek, en deeltjes rond spelers met betoverde spullen.
 *
 * <p>Het plaatjes-deel is een resourcepack dat de server zelf bouwt en meestuurt (via Polymer): spelers krijgen bij
 * het joinen één keer de vraag om het te gebruiken, installeren hoeven ze niets. Per effect uitzetten werkt met kleine
 * "uit"-pakketjes die over het hoofdpakket heen gaan.
 */
public final class Glans implements ModInitializer {
	public static final Module MODULE = Module.register("glans", "Glans", Items.ENCHANTED_BOOK,
			"Enchant-effecten op je spullen, eigen", "plaatjes per betoverd boek en deeltjes", "rond spelers. Zonder installeren.");
	public static final Setting.Toggle PACK = MODULE.toggle("pakket", "Glans-plaatjes", true, Items.ENCHANTED_BOOK,
			"Effecten op items en eigen boek-plaatjes.", "Uitzetten = het pakket wordt weggehaald", "(je scherm laadt heel even opnieuw).");
	public static final Setting.Toggle BOOKS = MODULE.toggle("boeken", "Eigen plaatjes voor boeken", true, Items.BOOK,
			"Elk betoverd boek een eigen kaft", "met een symbool, per betovering.");
	public static final Setting.Toggle AURA = MODULE.toggle("deeltjes", "Deeltjes rond spelers", true, Items.BLAZE_POWDER,
			"Vlammen, sneeuw, zielenvuur… rond", "spelers met betoverde spullen.");
	public static final Setting.Toggle AURA_SELF = MODULE.toggle("deeltjes_zelf", "Deeltjes ook bij jezelf", false, Items.GLOWSTONE_DUST,
			"Zie je eigen deeltjes ook (kan in", "de eerste persoon wat druk zijn).");

	/** Een effect-familie uit families.json (gegenereerd door tools/glans/generate.py). */
	public record Family(String id, String group, String name, String enchants, int items, List<String> ids) {
	}

	public static final Map<String, Family> FAMILIES = new LinkedHashMap<>();
	/** Betovering (zonder "minecraft:") → familie. */
	public static final Map<String, String> BY_ENCHANT = new HashMap<>();

	public static final class Data {
		public Set<String> off = new HashSet<>();
	}

	public static final PlayerStore<Data> STORE = PlayerStore.create("handig/glans", Data.class, Data::new, d -> {
		if (d.off == null) d.off = new HashSet<>();
	});

	/** Gehoste "uit"-pakketjes: familie (of "boeken") → hash. */
	static final Map<String, String> OFF_PACKS = new LinkedHashMap<>();
	static boolean assetsAdded;

	@Override
	public void onInitialize() {
		loadFamilies();
		assetsAdded = PolymerResourcePackUtils.addModAssets("handig_glans");
		ensureAutoHostConfig();

		MODULE.hubEntry(Items.BLAZE_POWDER, "Glans-effecten", ChatFormatting.LIGHT_PURPLE, EffectsGui::open,
				"Kies welke enchant-effecten je ziet.");

		ServerLifecycleEvents.SERVER_STARTED.register(Glans::hostOffPacks);
		AutoHostUtils.SEND_RESOURCE_PACK_COLLECTOR.register((provider, context, consumer) -> {
			UUID id = playerId(context);
			if (id == null || !PACK.get(id)) return;
			for (String pack : disabledPacks(id)) {
				consumer.accept(provider.createProperties(context, packUuid(pack), packId(pack), OFF_PACKS.get(pack)));
			}
		});
		// Wie Glans uit heeft staan, krijgt het hoofdpakket na het inloggen meteen weer weggehaald
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (!PACK.get(handler.player)) server.execute(() -> setMainPack(handler.player, false));
		});

		PACK.onChange(p -> setMainPack(p, PACK.get(p)));
		BOOKS.onChange(p -> setOffPack(p, "boeken", !BOOKS.get(p)));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 == 0) Aura.tick(server);
		});

		GlansTests.register();
	}

	// ---------------------------------------------------------------- families

	private static void loadFamilies() {
		var container = FabricLoader.getInstance().getModContainer("handig_glans").orElseThrow();
		Path json = container.findPath("glans/families.json").orElseThrow();
		try (Reader r = Files.newBufferedReader(json, StandardCharsets.UTF_8)) {
			Map<String, JsonObject> raw = new Gson().fromJson(r, new TypeToken<LinkedHashMap<String, JsonObject>>() {}.getType());
			for (var e : raw.entrySet()) {
				JsonObject o = e.getValue();
				List<String> ids = new ArrayList<>();
				o.getAsJsonArray("ids").forEach(x -> ids.add(x.getAsString()));
				Family f = new Family(e.getKey(), o.get("groep").getAsString(), o.get("naam").getAsString(),
						o.get("betoveringen").getAsString(), o.get("items").getAsInt(), ids);
				FAMILIES.put(f.id(), f);
				for (String id : ids) BY_ENCHANT.put(id, f.id());
			}
		} catch (IOException ex) {
			throw new IllegalStateException("Glans: families.json ontbreekt", ex);
		}
	}

	public static boolean enabled(ServerPlayer p, String family) {
		return !STORE.get(p).off.contains(family);
	}

	public static void setEnabled(ServerPlayer p, String family, boolean on) {
		Data d = STORE.get(p);
		boolean changed = on ? d.off.remove(family) : d.off.add(family);
		if (!changed) return;
		STORE.markDirty(p);
		setOffPack(p, family, !on);
	}

	// ---------------------------------------------------------------- pakketjes

	/** Zet de autohost van Polymer aan (die staat standaard uit), met een Nederlandse vraag aan de speler. */
	private static void ensureAutoHostConfig() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("polymer/auto-host.json");
		if (Files.exists(file)) return;
		JsonObject o = new JsonObject();
		o.addProperty("enabled", true);
		o.addProperty("required", false);
		o.addProperty("message", "Deze server laat enchant-effecten en eigen boek-plaatjes zien met een klein resourcepack. "
				+ "Je hoeft niets te installeren. Liever niet? Kies 'Nee', of zet het later uit via /handig.");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(o));
		} catch (IOException e) {
			nl.handig.kern.Handig.LOG.warn("Glans: kon {} niet schrijven", file, e);
		}
	}

	/** De "uit"-pakketjes uit de mod naar de serveromgeving kopiëren en via Polymer laten hosten. */
	static void hostOffPacks(MinecraftServer server) {
		OFF_PACKS.clear();
		var container = FabricLoader.getInstance().getModContainer("handig_glans").orElseThrow();
		Path dir = FabricLoader.getInstance().getGameDir().resolve("polymer/handig_glans");
		List<String> packs = new ArrayList<>(FAMILIES.keySet());
		packs.add("boeken");
		try {
			Files.createDirectories(dir);
			for (String pack : packs) {
				Optional<Path> src = container.findPath("glans/uit/" + pack + ".zip");
				if (src.isEmpty()) continue;
				Path dst = dir.resolve(pack + ".zip");
				Files.copy(src.get(), dst, StandardCopyOption.REPLACE_EXISTING);
				String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(dst)));
				AutoHostUtils.registerHostedFile(packId(pack), dst);
				OFF_PACKS.put(pack, hash);
			}
		} catch (Exception e) {
			nl.handig.kern.Handig.LOG.error("Glans: kon de uit-pakketjes niet klaarzetten", e);
		}
	}

	static Identifier packId(String pack) {
		return Identifier.fromNamespaceAndPath("handig", "glans_uit_" + pack);
	}

	static UUID packUuid(String pack) {
		return UUID.nameUUIDFromBytes(("handig:glans_uit_" + pack).getBytes(StandardCharsets.UTF_8));
	}

	private static List<String> disabledPacks(UUID player) {
		List<String> out = new ArrayList<>();
		Data d = STORE.get(player);
		for (String fam : FAMILIES.keySet()) if (d.off.contains(fam) && OFF_PACKS.containsKey(fam)) out.add(fam);
		if (!BOOKS.get(player) && OFF_PACKS.containsKey("boeken")) out.add("boeken");
		return out;
	}

	private static @Nullable UUID playerId(PacketContext context) {
		var profile = context.get(PacketContext.GAME_PROFILE);
		return profile == null ? null : profile.id();
	}

	/** Een "uit"-pakketje toevoegen of weghalen terwijl de speler online is. */
	static void setOffPack(ServerPlayer p, String pack, boolean apply) {
		String hash = OFF_PACKS.get(pack);
		if (hash == null || !PACK.get(p)) return;
		if (apply) {
			ResourcePackDataProvider provider = ResourcePackDataProvider.getActive();
			if (provider == null || !provider.isReady(p.connection.getPacketContext())) return;
			var info = provider.createProperties(p.connection.getPacketContext(), packUuid(pack), packId(pack), hash);
			p.connection.send(new ClientboundResourcePackPushPacket(info.id(), info.url(), info.hash(), false, Optional.empty()));
		} else {
			p.connection.send(new ClientboundResourcePackPopPacket(Optional.of(packUuid(pack))));
		}
	}

	/** Het hoofdpakket (met alle Glans-plaatjes) aan of uit voor één speler. */
	static void setMainPack(ServerPlayer p, boolean on) {
		ResourcePackDataProvider provider = ResourcePackDataProvider.getActive();
		if (!on) {
			for (String pack : OFF_PACKS.keySet()) p.connection.send(new ClientboundResourcePackPopPacket(Optional.of(packUuid(pack))));
			p.connection.send(new ClientboundResourcePackPopPacket(Optional.of(PolymerResourcePackUtils.getMainUuid())));
			return;
		}
		if (provider == null || !provider.isReady(p.connection.getPacketContext())) {
			p.sendSystemMessage(Ui.prefix("Glans").append(Ui.text("Het pakket is nog niet klaar; log opnieuw in.", ChatFormatting.GRAY)));
			return;
		}
		for (var info : provider.getProperties(p.connection.getPacketContext())) {
			p.connection.send(new ClientboundResourcePackPushPacket(info.id(), info.url(), info.hash(), false, Optional.empty()));
		}
		for (String pack : disabledPacks(p.getUUID())) setOffPack(p, pack, true);
	}

	static Component familyLabel(Family f) {
		return Component.literal(f.name()).withStyle(ChatFormatting.LIGHT_PURPLE);
	}
}
