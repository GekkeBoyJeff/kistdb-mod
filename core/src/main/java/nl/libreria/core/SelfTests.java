package nl.libreria.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Zelftests die modules registreren. Ze draaien alleen met -Dlibreria.selftest=true (in de CI, op een echte server)
 * en loggen "LIBRERIA-SELFTEST OK" of "LIBRERIA-SELFTEST FAIL".
 */
public final class SelfTests {
	public interface Test {
		void run(ServerLevel level) throws Exception;
	}

	private static final Map<String, Test> TESTS = new LinkedHashMap<>();

	private SelfTests() {
	}

	public static void register(String name, Test test) {
		TESTS.put(name, test);
	}

	public static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}

	public static boolean enabled() {
		return Boolean.getBoolean("libreria.selftest");
	}

	static void runAll(MinecraftServer server) {
		List<String> fails = new ArrayList<>();
		ServerLevel level = server.overworld();
		for (Map.Entry<String, Test> t : TESTS.entrySet()) {
			try {
				t.getValue().run(level);
				Libreria.LOG.info("LIBRERIA-SELFTEST {} OK", t.getKey());
			} catch (Throwable e) {
				Libreria.LOG.error("LIBRERIA-SELFTEST {} FAIL: {}", t.getKey(), e.toString(), e);
				fails.add(t.getKey() + ": " + e);
			}
		}
		if (fails.isEmpty()) {
			Libreria.LOG.info("LIBRERIA-SELFTEST OK ({} tests)", TESTS.size());
		} else {
			Libreria.LOG.error("LIBRERIA-SELFTEST FAIL {}", fails);
		}
	}
}
