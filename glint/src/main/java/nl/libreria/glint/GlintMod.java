package nl.libreria.glint;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import eu.pb4.polymer.autohost.api.AutoHostUtils;
import eu.pb4.polymer.autohost.api.ResourcePackDataProvider;
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
import nl.libreria.core.Module;
import nl.libreria.core.Pack;
import nl.libreria.core.PlayerStore;
import nl.libreria.core.Setting;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * GlintMod – een Libreria-module. Geanimeerde enchant-effecten op wapens, gereedschap en harnas (vlammen bij Fire Aspect,
 * bubbels bij Respiration…), een eigen plaatje per betoverd boek, en deeltjes rond spelers met betoverde spullen.
 *
 * <p>Het plaatjes-deel is een resourcepack dat de server zelf bouwt en meestuurt (via Polymer): spelers krijgen bij
 * het joinen één keer de vraag om het te gebruiken, installeren hoeven ze niets. Per effect uitzetten werkt met kleine
 * "uit"-pakketjes die over het hoofdpakket heen gaan.
 */
public final class GlintMod implements ModInitializer {
	public static final Module MODULE = Module.register("glint", "Glint", Items.ENCHANTED_BOOK,
			"Enchant effects and rarity frames on", "your items, custom book covers and", "particles around players. No install.").in(nl.libreria.core.Domain.YOU, "Effects");
	public static final Setting.Toggle FRAMES = MODULE.toggle("frames", "Rarity frames", true, Items.ITEM_FRAME,
			"A light border around uncommon, rare", "and epic items in your inventory;", "rare and epic ones sparkle.");
	public static final Setting.Toggle BOOKS = MODULE.toggle("books", "Custom book textures", true, Items.BOOK,
			"Every enchanted book gets its own cover", "with a symbol for its enchantment.");
	public static final Setting.Choice INTENSITY = MODULE.choice("intensity", "Particle intensity", Items.AMETHYST_SHARD, 1,
			new int[]{1, 2, 3}, new String[]{"Subtle", "Normal", "Rich"},
			"The most particles you see. Within it,", "higher enchantment levels give more.");
	public static final Setting.Toggle AURA_FIRST = MODULE.toggle("particles_self", "First person: your weapon", true, Items.GLOWSTONE_DUST,
			"Particles of the enchanted item in", "your hand, as you look around.");
	public static final Setting.Toggle AURA_THIRD = MODULE.toggle("particles_third", "Third person: your armour", true, Items.DIAMOND_CHESTPLATE,
			"Your armour's particles, only visible", "in third person (F5): they never", "fill your own view.");
	public static final Setting.Toggle AURA = MODULE.toggle("particles", "On other players", true, Items.BLAZE_POWDER,
			"Flames, snow, soul fire… around", "other players with enchanted gear.");

	/** Een effect-familie uit families.json (gegenereerd door tools/glans/generate.py). */
	public record Family(String id, String group, String name, String enchants, int items, List<String> ids) {
	}

	public static final Map<String, Family> FAMILIES = new LinkedHashMap<>();
	/** Betovering (zonder "minecraft:") → familie. */
	public static final Map<String, String> BY_ENCHANT = new HashMap<>();

	public static final class Data {
		public Set<String> off = new HashSet<>();
	}

	public static final PlayerStore<Data> STORE = PlayerStore.create("libreria/glint", Data.class, Data::new, d -> {
		if (d.off == null) d.off = new HashSet<>();
	});

	/** Gehoste "uit"-pakketjes: familie (of "books") → hash. */
	static final Map<String, String> OFF_PACKS = new LinkedHashMap<>();
	static boolean assetsAdded;

	@Override
	public void onInitialize() {
		loadFamilies();
		assetsAdded = Pack.addAssets("libreria_glint");

		MODULE.hubEntry(nl.libreria.core.Domain.YOU, 30, Items.BLAZE_POWDER, "Effects", EffectsWindow::open,
				"Particles from enchanted gear: how many you see, and where.");
		MODULE.settingsButton("Choose effects", "Flames yes, smoke no: per effect", EffectsGui::open);
		nl.libreria.core.Services.provideMenu(nl.libreria.core.Services.MENU_EFFECTS, EffectsWindow::open);

		// Vroeg, zodat de pakketjes klaar zijn voordat de eerste speler inlogt
		ServerLifecycleEvents.SERVER_STARTING.register(GlintMod::hostOffPacks);
		AutoHostUtils.SEND_RESOURCE_PACK_COLLECTOR.register((provider, context, consumer) -> {
			UUID id = playerId(context);
			if (id == null || !Pack.ENABLED.get(id)) return;
			for (String pack : disabledPacks(id)) {
				consumer.accept(provider.createProperties(context, packUuid(pack), packId(pack), OFF_PACKS.get(pack)));
			}
		});
		// The main pack lives in core; when it comes back, the effects that are off must be switched off again
		Pack.afterPush(p -> {
			for (String pack : disabledPacks(p.getUUID())) setOffPack(p, pack, true);
		});
		Pack.beforePop(p -> {
			for (String pack : OFF_PACKS.keySet()) p.connection.send(new ClientboundResourcePackPopPacket(Optional.of(packUuid(pack))));
		});
		BOOKS.onChange(p -> setOffPack(p, "books", !BOOKS.get(p)));
		FRAMES.onChange(p -> setOffPack(p, "frames", !FRAMES.get(p)));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % Aura.RUN == 0) Aura.tick(server);
		});

		GlintTests.register();
	}

	// ---------------------------------------------------------------- families

	private static void loadFamilies() {
		var container = FabricLoader.getInstance().getModContainer("libreria_glint").orElseThrow();
		Path json = container.findPath("glint/families.json").orElseThrow();
		try (Reader r = Files.newBufferedReader(json, StandardCharsets.UTF_8)) {
			Map<String, JsonObject> raw = new Gson().fromJson(r, new TypeToken<LinkedHashMap<String, JsonObject>>() {}.getType());
			for (var e : raw.entrySet()) {
				JsonObject o = e.getValue();
				List<String> ids = new ArrayList<>();
				o.getAsJsonArray("ids").forEach(x -> ids.add(x.getAsString()));
				Family f = new Family(e.getKey(), o.get("group").getAsString(), o.get("name").getAsString(),
						o.get("enchantments").getAsString(), o.get("items").getAsInt(), ids);
				FAMILIES.put(f.id(), f);
				for (String id : ids) BY_ENCHANT.put(id, f.id());
			}
		} catch (IOException ex) {
			throw new IllegalStateException("Glint: families.json is missing", ex);
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

	/** De "uit"-pakketjes uit de mod naar de serveromgeving kopiëren en via Polymer laten hosten. */
	static void hostOffPacks(MinecraftServer server) {
		OFF_PACKS.clear();
		var container = FabricLoader.getInstance().getModContainer("libreria_glint").orElseThrow();
		Path dir = FabricLoader.getInstance().getGameDir().resolve("polymer/libreria_glint");
		List<String> packs = new ArrayList<>(FAMILIES.keySet());
		packs.add("books");
		packs.add("frames");
		try {
			Files.createDirectories(dir);
			for (String pack : packs) {
				Optional<Path> src = container.findPath("glint/off/" + pack + ".zip");
				if (src.isEmpty()) {
					nl.libreria.core.Libreria.LOG.warn("Glint: off-pack {} not found in the mod", pack);
					continue;
				}
				Path dst = dir.resolve(pack + ".zip");
				Files.copy(src.get(), dst, StandardCopyOption.REPLACE_EXISTING);
				String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(dst)));
				AutoHostUtils.registerHostedFile(packId(pack), dst);
				OFF_PACKS.put(pack, hash);
			}
			nl.libreria.core.Libreria.LOG.info("Glint: {} off-packs ready", OFF_PACKS.size());
		} catch (Exception e) {
			nl.libreria.core.Libreria.LOG.error("Glint: could not prepare the off-packs", e);
		}
	}

	static Identifier packId(String pack) {
		return Identifier.fromNamespaceAndPath("libreria", "glint_off_" + pack);
	}

	static UUID packUuid(String pack) {
		return UUID.nameUUIDFromBytes(("libreria:glint_off_" + pack).getBytes(StandardCharsets.UTF_8));
	}

	private static List<String> disabledPacks(UUID player) {
		List<String> out = new ArrayList<>();
		Data d = STORE.get(player);
		for (String fam : FAMILIES.keySet()) if (d.off.contains(fam) && OFF_PACKS.containsKey(fam)) out.add(fam);
		if (!BOOKS.get(player) && OFF_PACKS.containsKey("books")) out.add("books");
		if (!FRAMES.get(player) && OFF_PACKS.containsKey("frames")) out.add("frames");
		return out;
	}

	private static @Nullable UUID playerId(PacketContext context) {
		var profile = context.get(PacketContext.GAME_PROFILE);
		return profile == null ? null : profile.id();
	}

	/** Een "uit"-pakketje toevoegen of weghalen terwijl de speler online is. */
	static void setOffPack(ServerPlayer p, String pack, boolean apply) {
		String hash = OFF_PACKS.get(pack);
		if (hash == null || !Pack.ENABLED.get(p)) return;
		if (apply) {
			ResourcePackDataProvider provider = ResourcePackDataProvider.getActive();
			if (provider == null || !provider.isReady(p.connection.getPacketContext())) return;
			var info = provider.createProperties(p.connection.getPacketContext(), packUuid(pack), packId(pack), hash);
			p.connection.send(new ClientboundResourcePackPushPacket(info.id(), info.url(), info.hash(), false, Optional.empty()));
		} else {
			p.connection.send(new ClientboundResourcePackPopPacket(Optional.of(packUuid(pack))));
		}
	}


	static Component familyLabel(Family f) {
		return Component.literal(f.name()).withStyle(ChatFormatting.LIGHT_PURPLE);
	}
}
