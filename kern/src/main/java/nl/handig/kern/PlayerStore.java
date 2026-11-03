package nl.handig.kern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Gegevens per speler, als JSON in &lt;wereld&gt;/&lt;map&gt;/&lt;uuid&gt;.json.
 * Laden bij gebruik, opslaan als er iets veranderd is (elke minuut, bij uitloggen en bij stoppen).
 */
public final class PlayerStore<T> {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final List<PlayerStore<?>> ALL = new ArrayList<>();

	private final String folder;
	private final Class<T> type;
	private final Supplier<T> factory;
	private final @Nullable Consumer<T> fixer;
	private final Map<UUID, T> loaded = new HashMap<>();
	private final Set<UUID> dirty = new HashSet<>();
	private @Nullable Path dir;

	private PlayerStore(String folder, Class<T> type, Supplier<T> factory, @Nullable Consumer<T> fixer) {
		this.folder = folder;
		this.type = type;
		this.factory = factory;
		this.fixer = fixer;
	}

	/** @param folder map relatief aan de wereldmap, bv. "handig/wegwijzer" */
	public static <T> PlayerStore<T> create(String folder, Class<T> type, Supplier<T> factory) {
		return create(folder, type, factory, null);
	}

	/** @param fixer wordt na het laden aangeroepen (bv. om lege velden te vullen) */
	public static <T> PlayerStore<T> create(String folder, Class<T> type, Supplier<T> factory, @Nullable Consumer<T> fixer) {
		PlayerStore<T> s = new PlayerStore<>(folder, type, factory, fixer);
		ALL.add(s);
		return s;
	}

	public T get(ServerPlayer player) {
		return get(player.getUUID());
	}

	public T get(UUID id) {
		return this.loaded.computeIfAbsent(id, this::read);
	}

	public void markDirty(ServerPlayer player) {
		this.dirty.add(player.getUUID());
	}

	public void markDirty(UUID id) {
		this.dirty.add(id);
	}

	private T read(UUID id) {
		if (this.dir != null) {
			Path file = this.dir.resolve(id + ".json");
			if (Files.exists(file)) {
				try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					T value = GSON.fromJson(r, this.type);
					if (value != null) {
						if (this.fixer != null) this.fixer.accept(value);
						return value;
					}
				} catch (Exception e) {
					Handig.LOG.error("Kan {} niet lezen", file, e);
				}
			}
		}
		T value = this.factory.get();
		if (this.fixer != null) this.fixer.accept(value);
		return value;
	}

	private void write(UUID id) {
		T value = this.loaded.get(id);
		if (value == null || this.dir == null) return;
		Path file = this.dir.resolve(id + ".json");
		Path tmp = this.dir.resolve(id + ".json.tmp");
		try {
			Files.createDirectories(this.dir);
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(value, w);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			this.dirty.remove(id);
		} catch (IOException e) {
			Handig.LOG.error("Kan {} niet opslaan", file, e);
		}
	}

	private void saveDirtyEntries() {
		for (UUID id : new ArrayList<>(this.dirty)) write(id);
	}

	static void initAll(MinecraftServer server) {
		Path root = server.getWorldPath(LevelResource.ROOT);
		for (PlayerStore<?> s : ALL) {
			s.dir = root.resolve(s.folder);
			s.loaded.clear();
			s.dirty.clear();
		}
	}

	static void saveDirtyAll() {
		for (PlayerStore<?> s : ALL) s.saveDirtyEntries();
	}

	static void unloadAll(UUID id) {
		for (PlayerStore<?> s : ALL) {
			if (s.dirty.contains(id)) s.write(id);
			s.loaded.remove(id);
		}
	}
}
