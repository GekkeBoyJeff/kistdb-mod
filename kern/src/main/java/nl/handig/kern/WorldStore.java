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
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Eén gedeeld JSON-bestand per wereld (graven, claims…), naast de per-speler {@link PlayerStore}. Wordt lui geladen
 * bij het eerste {@link #get()} en samen met de rest opgeslagen.
 */
public final class WorldStore<T> {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final List<WorldStore<?>> ALL = new ArrayList<>();
	private static @Nullable Path root;

	private final String file;
	private final Class<T> type;
	private final Supplier<T> factory;
	private @Nullable T value;
	private boolean dirty;

	private WorldStore(String file, Class<T> type, Supplier<T> factory) {
		this.file = file;
		this.type = type;
		this.factory = factory;
	}

	/** @param file pad binnen de wereldmap, bijvoorbeeld {@code "handig/graven.json"} */
	public static <T> WorldStore<T> create(String file, Class<T> type, Supplier<T> factory) {
		WorldStore<T> s = new WorldStore<>(file, type, factory);
		ALL.add(s);
		return s;
	}

	public T get() {
		if (this.value == null) {
			T loaded = null;
			if (root != null) {
				Path p = root.resolve(this.file);
				if (Files.exists(p)) {
					try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
						loaded = GSON.fromJson(r, this.type);
					} catch (Exception e) {
						Handig.LOG.error("Kan {} niet lezen; begin leeg", p, e);
					}
				}
			}
			this.value = loaded != null ? loaded : this.factory.get();
		}
		return this.value;
	}

	public void markDirty() {
		this.dirty = true;
	}

	/** Meteen wegschrijven (voor dingen die je niet kwijt wilt, zoals een graf). */
	public void saveNow() {
		this.dirty = true;
		write();
	}

	private void write() {
		if (!this.dirty || this.value == null || root == null) return;
		Path p = root.resolve(this.file);
		Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
		try {
			Files.createDirectories(p.getParent());
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(this.value, w);
			}
			Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
			this.dirty = false;
		} catch (IOException e) {
			Handig.LOG.error("Kan {} niet opslaan", p, e);
		}
	}

	static void initAll(MinecraftServer server) {
		root = server.getWorldPath(LevelResource.ROOT);
		for (WorldStore<?> s : ALL) {
			s.value = null;
			s.dirty = false;
		}
	}

	static void saveAll() {
		for (WorldStore<?> s : ALL) s.write();
	}
}
