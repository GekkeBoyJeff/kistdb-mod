package nl.libreria.chests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/** De kisten-database: per speler een JSON-bestand in &lt;wereld&gt;/libreria/chests/&lt;uuid&gt;.json. */
public final class Store {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<UUID, PlayerData> LOADED = new HashMap<>();
	private static @Nullable Path dir;

	private Store() {
	}

	/** Eén container (een dubbele kist is één entry, opgeslagen onder de laagste van de twee posities). */
	public static final class Entry {
		public String dim = "";
		public int x;
		public int y;
		public int z;
		public boolean noIndex;
		/** Blok-ID, bv. minecraft:chest (voor het icoon in de GUI). */
		public String block = "minecraft:chest";
		/** Laatst geziene inhoud: item-ID → aantal. Wordt live ververst zodra de kist geladen is. */
		public Map<String, Integer> items = new LinkedHashMap<>();
		/** Tijdstip (ms) waarop de inhoud het laatst gelezen is. */
		public long seen;

		public BlockPos pos() {
			return new BlockPos(this.x, this.y, this.z);
		}

		public boolean is(String dim, BlockPos pos) {
			return this.x == pos.getX() && this.y == pos.getY() && this.z == pos.getZ() && this.dim.equals(dim);
		}
	}

	public static final class PlayerData {
		public List<Entry> entries = new ArrayList<>();
		transient boolean dirty;

		public @Nullable Entry find(String dim, BlockPos pos) {
			for (Entry e : this.entries) {
				if (e.is(dim, pos)) return e;
			}
			return null;
		}

		public void markDirty() {
			this.dirty = true;
		}
	}

	public static String dimId(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

	static void init(MinecraftServer server) {
		Path world = server.getWorldPath(LevelResource.ROOT);
		dir = world.resolve("libreria/chests");
		try {
			// Data from the old standalone KistDB mod moves along automatically
			Path old = world.resolve("kistdb");
			if (Files.isDirectory(old) && !Files.exists(dir)) {
				Files.createDirectories(dir.getParent());
				Files.move(old, dir);
			}
			Files.createDirectories(dir);
		} catch (IOException e) {
			ChestsMod.LOG.error("Could not create folder {}", dir, e);
		}
	}

	public static PlayerData get(UUID id) {
		return LOADED.computeIfAbsent(id, Store::read);
	}

	/** Zorgt dat de container in de database van de speler staat. Geeft true als hij nieuw is. */
	public static boolean track(UUID player, ServerLevel level, BlockPos key, String blockId) {
		PlayerData data = get(player);
		String dim = dimId(level);
		Entry e = data.find(dim, key);
		if (e != null) {
			if (!e.block.equals(blockId)) {
				e.block = blockId;
				data.markDirty();
			}
			return false;
		}
		e = new Entry();
		e.dim = dim;
		e.x = key.getX();
		e.y = key.getY();
		e.z = key.getZ();
		e.block = blockId;
		data.entries.add(e);
		data.markDirty();
		return true;
	}

	public static void remove(UUID player, Entry entry) {
		PlayerData data = get(player);
		if (data.entries.remove(entry)) data.markDirty();
	}

	private static PlayerData read(UUID id) {
		if (dir != null) {
			Path file = dir.resolve(id + ".json");
			if (Files.exists(file)) {
				try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					PlayerData data = GSON.fromJson(r, PlayerData.class);
					if (data != null) {
						if (data.entries == null) data.entries = new ArrayList<>();
						for (Entry e : data.entries) {
							if (e.items == null) e.items = new LinkedHashMap<>();
						}
						return data;
					}
				} catch (Exception e) {
					ChestsMod.LOG.error("Could not read {}", file, e);
				}
			}
		}
		return new PlayerData();
	}

	private static void write(UUID id, PlayerData data) {
		if (dir == null) return;
		Path file = dir.resolve(id + ".json");
		Path tmp = dir.resolve(id + ".json.tmp");
		try {
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(data, w);
			}
			Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			data.dirty = false;
		} catch (IOException e) {
			ChestsMod.LOG.error("Could not save {}", file, e);
		}
	}

	static void saveDirty() {
		LOADED.forEach((id, data) -> {
			if (data.dirty) write(id, data);
		});
	}

	static void saveAll() {
		LOADED.forEach(Store::write);
	}

	static void unload(UUID id) {
		PlayerData data = LOADED.remove(id);
		if (data != null && data.dirty) write(id, data);
	}
}
