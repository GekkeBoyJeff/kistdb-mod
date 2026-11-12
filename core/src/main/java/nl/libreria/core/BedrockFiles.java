package nl.libreria.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Before anything starts (so Geyser finds them when it does): puts Libreria's Bedrock pack and Geyser item mappings
 * (scripts/bedrock/generate.py) in Geyser's folders, when Geyser is on this server: {@code config/Geyser-Fabric/packs}
 * and {@code custom_mappings}. Always also in {@code config/libreria/bedrock}, for a Geyser elsewhere (a proxy, or
 * standalone): copy them into its folders there. Only written when they changed.
 */
public final class BedrockFiles implements PreLaunchEntrypoint {
	private static final Logger LOG = LoggerFactory.getLogger("Libreria");
	static final String PACK = "Libreria.mcpack", ITEMS = "libreria_items.json";

	@Override
	public void onPreLaunch() {
		try {
			install(FabricLoader.getInstance().getConfigDir(), FabricLoader.getInstance().isModLoaded("geyser-fabric"));
		} catch (RuntimeException e) {
			LOG.warn("Libreria: couldn't put the Bedrock pack in place", e);
		}
	}

	/** Puts the files in place under {@code config}; in Geyser's folders when it's here. True when Geyser's got them. */
	static boolean install(Path config, boolean geyserHere) {
		Path own = config.resolve("libreria").resolve("bedrock");
		write(own.resolve(PACK), PACK);
		write(own.resolve(ITEMS), ITEMS);
		Path geyser = config.resolve("Geyser-Fabric");
		if (!geyserHere && !Files.isDirectory(geyser)) return false;
		boolean a = write(geyser.resolve("packs").resolve(PACK), PACK);
		boolean b = write(geyser.resolve("custom_mappings").resolve(ITEMS), ITEMS);
		if (a || b) LOG.info("Libreria: the Bedrock pack and item mappings are in Geyser's folders ({}); Geyser loads them as it starts", geyser);
		return true;
	}

	/** Writes the resource {@code name} to {@code to} if it isn't there like that already; true when written. */
	private static boolean write(Path to, String name) {
		try (InputStream in = BedrockFiles.class.getResourceAsStream("/libreria/bedrock/" + name)) {
			if (in == null) return false;
			byte[] bytes = in.readAllBytes();
			if (Files.exists(to) && Arrays.equals(Files.readAllBytes(to), bytes)) return false;
			Files.createDirectories(to.getParent());
			Files.write(to, bytes);
			return true;
		} catch (IOException e) {
			LOG.warn("Libreria: couldn't write {}", to, e);
			return false;
		}
	}
}
