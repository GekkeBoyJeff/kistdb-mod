package nl.libreria.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Items;

/**
 * The tab list: a header with the server's name and who's online, a footer with your own time here and your ping;
 * after each name the player's title in its colour, and a grey "AFK" for players who are away ({@link Afk}).
 */
public final class Tab {
	public static final ServerSetting.Toggle ENABLED = Libreria.MODULE.serverToggle("tab", "Tab list header and footer", Items.OAK_HANGING_SIGN,
			true, "Server name and who's online above the", "tab list; your time here and ping below.");
	public static final ServerSetting.Text TITLE = Libreria.MODULE.serverText("tab_title", "Tab list title", Items.NAME_TAG, "", 48,
			"The server's name at the top of the", "tab list. Empty: the server's MOTD.");

	public static final ServerSetting.Toggle TITLES = Libreria.MODULE.serverToggle("tab_titles", "Titles in the tab list", Items.NAME_TAG,
			true, "Each player's title after their name", "in the tab list, in its colour.");

	/** What each player's tab list name was last sent as, to send it again only when it changed. */
	private static final java.util.Map<java.util.UUID, String> SENT = new java.util.HashMap<>();

	private Tab() {
	}

	static void init() {
		// Loading the class registers the settings
		ENABLED.onChange(() -> {
			MinecraftServer s = Libreria.server();
			if (s != null && !ENABLED.get()) {
				for (ServerPlayer p : s.getPlayerList().getPlayers()) p.connection.send(new ClientboundTabListPacket(Component.empty(), Component.empty()));
			}
		});
	}

	/** From the core tick, every two seconds. */
	static void tick(MinecraftServer server) {
		// Names (title, away) that changed go out to everybody
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Component shown = p.getTabListDisplayName();
			String now = shown == null ? "" : shown.getString() + "|" + shown.hashCode();
			if (!now.equals(SENT.put(p.getUUID(), now))) {
				server.getPlayerList().broadcastAll(new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket(
						net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, p));
			}
		}
		SENT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		if (!ENABLED.get()) return;
		int online = server.getPlayerCount();
		int away = Afk.awayCount();
		String title = TITLE.isSet() ? TITLE.get()
				: server.getMotd().lines().findFirst().orElse("Minecraft").replaceAll("§.", "").replace("◆", "").strip();
		MutableComponent header = Component.literal("\n").append(Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
				.append("\n").append(Ui.text(online + (online == 1 ? " player" : " players") + " online"
						+ (away > 0 ? " · " + away + " away" : ""), ChatFormatting.GRAY)).append("\n");
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			int ticks = p.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME);
			MutableComponent footer = Component.literal("\n").append(Ui.kv("Your time here: ", time(ticks), ChatFormatting.WHITE))
					.append(Ui.text("   ·   ", ChatFormatting.DARK_GRAY)).append(Ui.kv("Ping: ", p.connection.latency() + " ms", ChatFormatting.WHITE))
					.append("\n").append(Ui.text("Libreria: ", ChatFormatting.DARK_GRAY))
					.append(Component.keybind("key.quickActions").withStyle(ChatFormatting.GRAY)).append("\n");
			p.connection.send(new ClientboundTabListPacket(header, footer));
		}
	}

	static String time(int ticks) {
		long minutes = ticks / 1200L;
		if (minutes < 60) return minutes + " min";
		long hours = minutes / 60;
		return hours < 48 ? hours + " h " + minutes % 60 + " min" : hours / 24 + " days " + hours % 24 + " h";
	}

	/**
	 * The name in the tab list (as other mods made it, null: the plain name), with the title after it in its colour and
	 * " AFK" when the player is away; null when there's nothing to add (called from a mixin).
	 */
	public static @org.jetbrains.annotations.Nullable Component name(ServerPlayer p, @org.jetbrains.annotations.Nullable Component name) {
		Component title = TITLES.get() ? Services.progress().map(pr -> pr.title(p)).orElse(null) : null;
		boolean away = Afk.isAway(p);
		if ((title == null || title.getString().isBlank()) && !away) return name;
		MutableComponent out = Component.empty().append(name == null ? p.getName() : name);
		if (title != null && !title.getString().isBlank()) out.append(Component.literal("  ")).append(title);
		if (away) out.append(Component.literal(" AFK").withStyle(ChatFormatting.DARK_GRAY));
		return out;
	}
}
