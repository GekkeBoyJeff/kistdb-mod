package nl.libreria.core.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import nl.libreria.core.Hud;
import nl.libreria.core.Pack;
import nl.libreria.core.Services;
import org.jetbrains.annotations.Nullable;

/**
 * A place name, quietly: one small line of text at the very top of the screen ("~ Oakhaven · Village ~") that fades
 * in, stays a few seconds and fades out. No panel, no sound. It shares the top line with the other cards: a toast or
 * the land map stays in front of it. Without the pack it goes to the action bar.
 */
public final class Caption {
	/** How long it stays, and how long it takes to fade in and out (ticks). */
	static final int TICKS = 100, FADE_IN = 14, FADE_OUT = 26;
	private static final String CHANNEL = "caption";
	/** Above the look-at card, below toasts, banners and the land map. */
	private static final int PRIORITY = 20;

	/** A bit of text after the kind, in its own colour ("first discovery!" in gold). */
	public record Part(String text, int rgb) {
	}

	private record Shown(Component name, @Nullable Component kind, java.util.List<Part> extra, int start) {
	}

	private static final Map<UUID, Shown> SHOWN = new HashMap<>();

	private Caption() {
	}

	public static void show(ServerPlayer p, Component name, @Nullable Component kind) {
		show(p, name, kind, java.util.List.of());
	}

	/** "~ Ashglen Forest · Forest · first discovery! ~": extra parts after the kind, each in its own colour. */
	public static void show(ServerPlayer p, Component name, @Nullable Component kind, java.util.List<Part> extra) {
		if (!Pack.has(p)) {
			Hud.show(p, CHANNEL, line(name, kind, extra, 1), Hud.PRIO_INFO, 70);
			return;
		}
		SHOWN.put(p.getUUID(), new Shown(name, kind, java.util.List.copyOf(extra), p.level().getServer().getTickCount()));
		draw(p, SHOWN.get(p.getUUID()), 0);
	}

	/** Every other tick while fading, a few times a second otherwise. */
	public static void tick(MinecraftServer server, int now) {
		SHOWN.entrySet().removeIf(e -> {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			Shown s = e.getValue();
			int age = now - s.start();
			if (p == null || age > TICKS) {
				if (p != null) TopCard.hide(p, CHANNEL);
				return true;
			}
			boolean fading = age <= FADE_IN || age >= TICKS - FADE_OUT;
			if (fading ? age % 2 == 0 : age % 10 == 0) draw(p, s, age);
			return false;
		});
	}

	private static void draw(ServerPlayer p, Shown s, int age) {
		if (Services.inCamera(p)) return;
		double alpha = age < FADE_IN ? age / (double) FADE_IN
				: age > TICKS - FADE_OUT ? Math.max(0, (TICKS - age) / (double) FADE_OUT) : 1;
		TopCard.showText(p, CHANNEL, line(s.name(), s.kind(), s.extra(), alpha), PRIORITY, TICKS - age + 2);
	}

	/** "~ Name · Kind ~". Text has no transparency: it fades from a dim grey to warm white. */
	static MutableComponent line(Component name, @Nullable Component kind, double alpha) {
		return line(name, kind, java.util.List.of(), alpha);
	}

	static MutableComponent line(Component name, @Nullable Component kind, java.util.List<Part> extra, double alpha) {
		int main = mix(0x3C3A36, 0xF2E6C8, alpha), dot = mix(0x3C3A36, 0x8A8A8A, alpha), sub = mix(0x3C3A36, 0xB8B2A6, alpha);
		MutableComponent text = Component.empty()
				.append(Component.literal("~ ").withStyle(st -> st.withColor(dot)))
				.append(name.copy().withStyle(st -> st.withColor(main)));
		if (kind != null && !kind.getString().isEmpty()) {
			text.append(Component.literal("  ·  ").withStyle(st -> st.withColor(dot))).append(kind.copy().withStyle(st -> st.withColor(sub)));
		}
		if (!extra.isEmpty()) {
			text.append(Component.literal("  ·  ").withStyle(st -> st.withColor(dot)));
			for (Part part : extra) {
				int rgb = mix(0x3C3A36, part.rgb(), alpha);
				text.append(Component.literal(part.text()).withStyle(st -> st.withColor(rgb)));
			}
		}
		return text.append(Component.literal(" ~").withStyle(st -> st.withColor(dot)));
	}

	/** The colours of the caption, for parts: the name's warm white, the kind's grey. */
	public static final int MAIN = 0xF2E6C8, SUB = 0xB8B2A6, GOLD = 0xFFCB52;

	private static int mix(int from, int to, double t) {
		int r = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (r << 16) | (g << 8) | b;
	}

	public static void forget(UUID id) {
		SHOWN.remove(id);
	}
}
