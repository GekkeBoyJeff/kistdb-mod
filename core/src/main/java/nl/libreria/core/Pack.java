package nl.libreria.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.autohost.api.ResourcePackDataProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * The Libreria resource pack. The server builds it itself (via Polymer) from the assets of every Libreria module and
 * offers it when you join, so players never install anything. It makes menus, tooltips and items prettier; everything
 * also works without it (then you get the plain look). Modules check {@link #has(ServerPlayer)} before using anything
 * from the pack.
 */
public final class Pack {
	public static final Setting.Toggle ENABLED = Libreria.MODULE.toggle("pack", "Libreria resource pack", true, Items.PAINTING,
			"Prettier menus and tooltips, rarity", "frames and enchant effects. Turning it", "off removes the pack (screen reloads).");

	private static final List<Consumer<ServerPlayer>> AFTER_PUSH = new ArrayList<>();
	private static final List<Consumer<ServerPlayer>> BEFORE_POP = new ArrayList<>();
	private static final String MESSAGE = "This server uses a small resource pack for nicer menus, tooltips and item effects. "
			+ "You don't need to install anything. Rather not? Choose 'No', or turn it off later in /libreria settings.";

	private Pack() {
	}

	/** Does this player have the pack loaded (and not turned off)? */
	public static boolean has(ServerPlayer p) {
		return ENABLED.get(p) && PolymerResourcePackUtils.hasMainPack(p.connection);
	}

	/** Adds the assets of a mod (its jar's assets/ folder) to the pack. */
	public static boolean addAssets(String modId) {
		return PolymerResourcePackUtils.addModAssets(modId);
	}

	/** Runs after the main pack was sent to a player again (e.g. to re-send extra packs). */
	public static void afterPush(Consumer<ServerPlayer> hook) {
		AFTER_PUSH.add(hook);
	}

	/** Runs just before the main pack is taken away from a player. */
	public static void beforePop(Consumer<ServerPlayer> hook) {
		BEFORE_POP.add(hook);
	}

	static void init() {
		addAssets(Libreria.MOD_ID);
		ensureAutoHostConfig();
		// Players who turned the pack off get it taken away again right after joining
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (!ENABLED.get(handler.player)) server.execute(() -> setMain(handler.player, false));
		});
		ENABLED.onChange(p -> setMain(p, ENABLED.get(p)));
	}

	/** Turns on Polymer's auto-host (off by default) with an English question for the player. */
	private static void ensureAutoHostConfig() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("polymer/auto-host.json");
		try {
			JsonObject o;
			if (Files.exists(file)) {
				o = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				// Only update the question we wrote ourselves in an older version
				String old = o.has("message") ? o.get("message").getAsString() : "";
				if (!old.contains("enchant effects and custom book textures")) return;
			} else {
				o = new JsonObject();
				o.addProperty("enabled", true);
				o.addProperty("required", false);
			}
			o.addProperty("message", MESSAGE);
			Files.createDirectories(file.getParent());
			Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(o));
		} catch (IOException | RuntimeException e) {
			Libreria.LOG.warn("Libreria: could not write {}", file, e);
		}
	}

	/** Sends or removes the main pack for one player. */
	static void setMain(ServerPlayer p, boolean on) {
		if (!on) {
			for (Consumer<ServerPlayer> hook : BEFORE_POP) hook.accept(p);
			p.connection.send(new ClientboundResourcePackPopPacket(Optional.of(PolymerResourcePackUtils.getMainUuid())));
			return;
		}
		ResourcePackDataProvider provider = ResourcePackDataProvider.getActive();
		if (provider == null || !provider.isReady(p.connection.getPacketContext())) {
			p.sendSystemMessage(Ui.prefix("Libreria").append(Ui.text("The pack isn't ready yet; please log in again.", ChatFormatting.GRAY)));
			return;
		}
		for (var info : provider.getProperties(p.connection.getPacketContext())) {
			p.connection.send(new ClientboundResourcePackPushPacket(info.id(), info.url(), info.hash(), false, Optional.empty()));
		}
		for (Consumer<ServerPlayer> hook : AFTER_PUSH) hook.accept(p);
	}
}
