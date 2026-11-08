package nl.libreria.core;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;

/**
 * How the server looks in the multiplayer list: Libreria's icon when the server has none of its own, and two lines of
 * text in its colours. An operator's own text (admin window) wins; with nothing set, a server that still says
 * Minecraft's default "A Minecraft Server" gets Libreria's lines, any other server keeps its own.
 */
final class ServerList {
	static final ServerSetting.Text MOTD = Libreria.MODULE.serverText("motd", "Server list text", Items.OAK_SIGN, "", 120,
			"Two lines in the multiplayer list, split by |.", "& makes colours: &6gold, &7grey, &l bold.",
			"Empty: Libreria's, unless the server has its own.");
	private static final String VANILLA = "A Minecraft Server";
	/** The text the server had itself (server.properties), to go back to when the operator's text is cleared. */
	private static String own;

	private ServerList() {
	}

	static void init() {
		icon();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			own = server.getMotd();
			apply(server);
		});
		MOTD.onChange(() -> {
			MinecraftServer s = Libreria.server();
			if (s != null) apply(s);
		});
	}

	/** Libreria's lines, with the server's name from the tab list title when there is one. */
	static String libreria() {
		String name = Tab.TITLE.isSet() && !Tab.TITLE.get().isBlank() ? Tab.TITLE.get() : "Libreria";
		return "§6◆ §e§l" + name + "§r §6◆\n§7Survival, together · §fG§7 opens the menu";
	}

	/** {@code &6Gold | &7grey} as the server list shows it. */
	static String text(String raw) {
		String t = raw.replace(" | ", "\n").replace("|", "\n");
		return t.replaceAll("&([0-9a-fk-or])", "§$1");
	}

	private static void apply(MinecraftServer server) {
		String motd;
		if (MOTD.isSet() && !MOTD.get().isBlank()) motd = text(MOTD.get());
		else if (own == null || own.isBlank() || own.equals(VANILLA)) motd = libreria();
		else motd = own;
		if (!motd.equals(server.getMotd())) server.setMotd(motd);
	}

	/** Puts Libreria's icon next to the server in the list, when the server has no icon of its own. */
	private static void icon() {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) return;
		Path file = FabricLoader.getInstance().getGameDir().resolve("server-icon.png");
		if (Files.exists(file)) return;
		try (InputStream in = ServerList.class.getResourceAsStream("/libreria/server-icon.png")) {
			if (in != null) Files.copy(in, file);
			Libreria.LOG.info("Libreria: wrote a server icon ({})", file);
		} catch (Exception e) {
			Libreria.LOG.warn("Libreria: could not write the server icon", e);
		}
	}
}
