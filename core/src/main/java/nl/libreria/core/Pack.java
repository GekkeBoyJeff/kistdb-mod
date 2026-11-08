package nl.libreria.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Libreria resource pack. The server builds it itself (via Polymer) from the assets of every Libreria module and
 * sends it when you join, so players never install anything. It is required: the menus, cards and tooltips are drawn
 * with it, so a player who says no can't join. Modules still check {@link #has(ServerPlayer)} before using anything
 * from the pack (the moment between joining and the pack being loaded, and clients that failed to load it).
 */
public final class Pack {
	private static final String MESSAGE = "This server needs its small resource pack: the menus, cards, tooltips and item effects "
			+ "are drawn with it. It loads by itself, there is nothing to install.";
	/** Why you can't join without it, and how to fix that. */
	private static final String DISCONNECT = "This server needs its resource pack for its menus and cards. Join again and choose "
			+ "'Proceed'. Still no luck? In the server list, click Edit on this server and set Server Resource Packs to Enabled.";
	/** Questions we wrote in older versions (when the pack could be turned down): replaced by {@link #MESSAGE}. */
	private static final String[] OLD_MESSAGES = {"enchant effects and custom book textures", "Rather not? Choose 'No'"};

	private Pack() {
	}

	/** Does this player have the pack loaded? */
	public static boolean has(ServerPlayer p) {
		return PolymerResourcePackUtils.hasMainPack(p.connection);
	}

	/** Adds the assets of a mod (its jar's assets/ folder) to the pack. */
	public static boolean addAssets(String modId) {
		return PolymerResourcePackUtils.addModAssets(modId);
	}

	static void init() {
		addAssets(Libreria.MOD_ID);
		// Required: Polymer sends it as such, and whoever says no (or can't load it) is told why and can't join
		PolymerResourcePackUtils.markAsRequired();
		ensureAutoHostConfig();
	}

	/** Turns on Polymer's auto-host (off by default), required, with an English question for the player. */
	private static void ensureAutoHostConfig() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("polymer/auto-host.json");
		try {
			JsonObject o;
			if (Files.exists(file)) {
				o = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				// Only replace the question we wrote ourselves in an older version
				String old = o.has("message") && o.get("message").isJsonPrimitive() ? o.get("message").getAsString() : "";
				for (String m : OLD_MESSAGES) if (old.contains(m)) o.addProperty("message", MESSAGE);
			} else {
				o = new JsonObject();
				o.addProperty("enabled", true);
				o.addProperty("message", MESSAGE);
			}
			o.addProperty("required", true);
			o.addProperty("mod_override", true);
			// Polymer's own line ("couldn't be applied" and the pack's hash and address) tells a player nothing they can do
			boolean ownLine = !o.has("disconnect_message") || o.get("disconnect_message").toString().contains("multiplayer.texturePrompt.failure");
			if (ownLine) {
				o.addProperty("disconnect_message", DISCONNECT);
				o.addProperty("informative_disconnect", false);
			}
			Files.createDirectories(file.getParent());
			Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(o));
		} catch (IOException | RuntimeException e) {
			Libreria.LOG.warn("Libreria: could not write {}", file, e);
		}
	}
}
