package nl.libreria.core;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Players on Minecraft: Bedrock Edition (phones, consoles, Windows), who join through Geyser. Their game can't use the
 * Java resource pack: no windows drawn from the pack's pictures, no models on display entities. So for them Libreria
 * acts as if they have no pack ({@link Pack#has} is false): its windows become dialogs, which Geyser shows as Bedrock's
 * own forms (made for touch and controllers), and blocks drawn by the pack show as real blocks.
 *
 * <p>Found through Geyser's API (Geyser on this server) or Floodgate's (Geyser elsewhere, Floodgate here), whichever is
 * installed; with neither, nobody is a Bedrock player. Nothing changes for Java players.
 */
public final class Bedrock {
	private static final Map<UUID, Boolean> KNOWN = new ConcurrentHashMap<>();
	/** (For the tests and screenshots: Java players treated as Bedrock players.) */
	private static final Set<UUID> PRETEND = ConcurrentHashMap.newKeySet();
	private static volatile boolean looked;
	private static @Nullable Object geyser;
	private static @Nullable Method geyserIs;
	private static @Nullable Object floodgate;
	private static @Nullable Method floodgateIs;

	private Bedrock() {
	}

	static void init() {
		ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> KNOWN.put(h.player.getUUID(), ask(h.player.getUUID())));
		ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> KNOWN.remove(h.player.getUUID()));
		// Say once whether Bedrock players can be recognised here (a moment after the start: Geyser starts with the server)
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() != 100 || !installed()) return;
			Libreria.LOG.info("LIBRERIA-BEDROCK Geyser/Floodgate installed; recognised: {}", supported());
		});
	}

	public static boolean is(@Nullable ServerPlayer p) {
		return p != null && is(p.getUUID());
	}

	public static boolean is(@Nullable UUID id) {
		if (id == null) return false;
		if (PRETEND.contains(id)) return true;
		return KNOWN.computeIfAbsent(id, Bedrock::ask);
	}

	/** For the client a packet goes to (a block's look, for example); false when it's not known who that is. */
	public static boolean is(@Nullable PacketContext context) {
		if (context == null) return false;
		try {
			var profile = context.get(PacketContext.GAME_PROFILE);
			return profile != null && is(profile.id());
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Whether Geyser or Floodgate is here (so Bedrock players can join at all). */
	public static boolean supported() {
		find();
		return geyserIs != null || floodgateIs != null;
	}

	/** (Tests and screenshots: treat this Java player as a Bedrock player, or not any more.) */
	public static void pretend(UUID id, boolean bedrock) {
		if (bedrock) PRETEND.add(id);
		else PRETEND.remove(id);
	}

	private static boolean ask(UUID id) {
		find();
		try {
			if (geyserIs != null && Boolean.TRUE.equals(geyserIs.invoke(geyser, id))) return true;
			if (floodgateIs != null && Boolean.TRUE.equals(floodgateIs.invoke(floodgate, id))) return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			Libreria.LOG.debug("Bedrock check failed for {}", id, e);
		}
		return false;
	}

	/** Geyser's and Floodgate's API, if they're installed (looked up once they're up). */
	private static synchronized void find() {
		if (looked) return;
		try {
			Class<?> c = Class.forName("org.geysermc.geyser.api.GeyserApi");
			Object api = c.getMethod("api").invoke(null);
			if (api != null) {
				geyser = api;
				geyserIs = c.getMethod("isBedrockPlayer", UUID.class);
			}
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			geyserIs = null;
		}
		try {
			Class<?> c = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
			Object api = c.getMethod("getInstance").invoke(null);
			if (api != null) {
				floodgate = api;
				floodgateIs = c.getMethod("isFloodgatePlayer", UUID.class);
			}
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			floodgateIs = null;
		}
		// Not up yet (asked too early): look again next time
		if (geyserIs == null && floodgateIs == null && !installed()) {
			looked = true;
			return;
		}
		looked = geyserIs != null || floodgateIs != null;
		if (looked) Libreria.LOG.info("Bedrock players: recognised through {}", geyserIs != null ? "Geyser" : "Floodgate");
	}

	// ---------------------------------------------------------------- text without the pack's pictures

	/**
	 * Text as a Bedrock player can read it: the pack's pictures in it (letters of a font of ours, or private-use ones)
	 * left out, since their game has neither (it would show a box, or one of its own button pictures). Everything else
	 * stays as it is (colours, keys, translations); text without pictures comes back as it was.
	 */
	public static net.minecraft.network.chat.Component clean(net.minecraft.network.chat.Component text) {
		return pictures(text, net.minecraft.network.chat.Style.EMPTY) ? rebuild(text, net.minecraft.network.chat.Style.EMPTY) : text;
	}

	private static boolean picture(net.minecraft.network.chat.Style style) {
		return style.getFont() instanceof net.minecraft.network.chat.FontDescription.Resource r && r.id().getNamespace().equals("libreria");
	}

	private static boolean privateUse(String s) {
		return s.codePoints().anyMatch(cp -> Character.getType(cp) == Character.PRIVATE_USE);
	}

	private static boolean pictures(net.minecraft.network.chat.Component c, net.minecraft.network.chat.Style parent) {
		net.minecraft.network.chat.Style style = c.getStyle().applyTo(parent);
		if (c.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents t && !t.text().isEmpty()
				&& (picture(style) || privateUse(t.text()))) return true;
		for (net.minecraft.network.chat.Component sib : c.getSiblings()) if (pictures(sib, style)) return true;
		return false;
	}

	private static net.minecraft.network.chat.MutableComponent rebuild(net.minecraft.network.chat.Component c, net.minecraft.network.chat.Style parent) {
		net.minecraft.network.chat.Style style = c.getStyle().applyTo(parent);
		net.minecraft.network.chat.MutableComponent out;
		if (c.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents t) {
			StringBuilder b = new StringBuilder();
			if (!picture(style)) t.text().codePoints().filter(cp -> Character.getType(cp) != Character.PRIVATE_USE).forEach(b::appendCodePoint);
			out = net.minecraft.network.chat.Component.literal(b.toString());
		} else {
			out = net.minecraft.network.chat.MutableComponent.create(c.getContents());
		}
		out.setStyle(c.getStyle());
		for (net.minecraft.network.chat.Component sib : c.getSiblings()) out.append(rebuild(sib, style));
		return out;
	}

	private static boolean installed() {
		var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
		return loader.isModLoaded("geyser-fabric") || loader.isModLoaded("floodgate");
	}
}
