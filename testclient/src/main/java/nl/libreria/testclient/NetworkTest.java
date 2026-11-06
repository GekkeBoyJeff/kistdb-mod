package nl.libreria.testclient;

import java.lang.reflect.Proxy;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CI only: the same walk through Libreria as {@link Screens}, but against a real dedicated server with a real client
 * connection. In singleplayer packets are handed over in memory and never written or read, so a packet a client can't
 * read ("Network Protocol Error" for players) only shows up here. After every step it checks the client is still
 * connected; the first time it isn't, it logs the step and takes a screenshot of the disconnect screen. The client log
 * then has the packet that failed.
 */
public final class NetworkTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("Libreria network test");

	@Override
	public void runTest(ClientGameTestContext ctx) {
		ctx.waitTicks(20);
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer()) {
			var connection = server.connect();
			connection.waitForChunksRender();
			ctx.waitTicks(40);
			ServerPlayer player = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst());
			Screens.network = ctx;
			Screens.lostAfter = null;
			check(ctx, "joining");
			if (Screens.lostAfter == null) Screens.scene(withoutScreenshots(ctx), server, player);
			LOG.info("LIBRERIA-NETWORK {}", Screens.lostAfter == null ? "OK: still connected after the whole walk" : "DISCONNECTED after " + Screens.lostAfter);
			if (connection instanceof AutoCloseable c) c.close();
		} catch (Throwable t) {
			LOG.error("LIBRERIA-NETWORK FAILED to run", t);
		} finally {
			Screens.network = null;
		}
	}

	/** Called after every step: is the client still connected? */
	static void check(ClientGameTestContext ctx, String step) {
		if (Screens.lostAfter != null) return;
		boolean gone = ctx.computeOnClient(mc -> mc.getConnection() == null || mc.player == null);
		if (!gone) return;
		Screens.lostAfter = step;
		LOG.error("LIBRERIA-NETWORK DISCONNECTED after step '{}' (the reason and the packet are in the client log above)", step);
		try {
			ctx.takeScreenshot("network_disconnected");
		} catch (Throwable ignored) {
		}
	}

	/** The scene's screenshots are for the look; here only the connection matters, so they're skipped. */
	private static ClientGameTestContext withoutScreenshots(ClientGameTestContext ctx) {
		return (ClientGameTestContext) Proxy.newProxyInstance(NetworkTest.class.getClassLoader(), new Class<?>[]{ClientGameTestContext.class},
				(proxy, method, args) -> {
					if (method.getName().equals("takeScreenshot")) return null;
					try {
						return method.invoke(ctx, args);
					} catch (java.lang.reflect.InvocationTargetException e) {
						throw e.getCause();
					}
				});
	}
}
