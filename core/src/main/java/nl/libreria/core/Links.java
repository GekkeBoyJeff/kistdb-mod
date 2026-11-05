package nl.libreria.core;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundServerLinksPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import nl.libreria.core.dialog.Window;
import nl.libreria.core.gui.Toast;
import org.jetbrains.annotations.Nullable;

/**
 * The server's links (Discord, website, rules, live map, vote), set by an admin in the admin window. Players open
 * them from the quick menu (G); they're also sent as the game's own "server links", so the client knows them too.
 */
public final class Links {
	/** One kind of link. {@code known} is the game's own type for it, if it has one. */
	public record Kind(ServerSetting.Text setting, @Nullable ServerLinks.KnownLinkType known, String name, int color,
			String tooltip) {
	}

	public static final List<Kind> KINDS = new ArrayList<>();

	static {
		add("discord", "Discord", ServerLinks.KnownLinkType.COMMUNITY, 0xA9B6FF, Items.AMETHYST_SHARD,
				"Chat with everyone, also when you're not online.");
		add("website", "Website", ServerLinks.KnownLinkType.WEBSITE, 0x7FE3FF, Items.COMPASS, "The server's website.");
		add("rules", "Rules", ServerLinks.KnownLinkType.COMMUNITY_GUIDELINES, 0xFFC266, Items.WRITABLE_BOOK,
				"How we play together here.");
		add("map", "Live map", null, 0x8CF08C, Items.FILLED_MAP, "The whole world on a web map.");
		add("vote", "Vote", null, 0xF59BFF, Items.EMERALD, "Vote for the server on a server list.");
	}

	private static void add(String key, String name, @Nullable ServerLinks.KnownLinkType known, int color, Item icon,
			String tooltip) {
		ServerSetting.Text s = Libreria.MODULE.serverText("link_" + key, "Link: " + name, icon, "", 256,
				"Full address (https://…). Empty: no " + name + " button.");
		s.onChange(Links::resendAll);
		KINDS.add(new Kind(s, known, name, color, tooltip));
	}

	private Links() {
	}

	static void init() {
		// Loading the class registers the settings
	}

	/** A link as typed by an admin, if it is a proper web address. */
	public static @Nullable URI uri(String text) {
		if (text == null || text.isBlank()) return null;
		try {
			URI u = URI.create(text.strip());
			String scheme = u.getScheme();
			if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http")) || u.getHost() == null) return null;
			return u;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** The links that are set, in order. */
	public static List<Kind> present() {
		List<Kind> out = new ArrayList<>();
		for (Kind k : KINDS) if (uri(k.setting().get()) != null) out.add(k);
		return out;
	}

	/** The server's own links (from server.properties) followed by ours. */
	static ServerLinks merged(MinecraftServer server) {
		List<ServerLinks.Entry> entries = new ArrayList<>(server.serverLinks().entries());
		for (Kind k : present()) {
			URI u = uri(k.setting().get());
			entries.add(k.known() != null ? ServerLinks.Entry.knownType(k.known(), u)
					: ServerLinks.Entry.custom(Component.literal(k.name()), u));
		}
		return new ServerLinks(entries);
	}

	static void send(ServerPlayer player) {
		ServerLinks links = merged(player.level().getServer());
		if (!links.isEmpty()) player.connection.send(new ClientboundServerLinksPacket(links.untrust()));
	}

	private static void resendAll() {
		MinecraftServer server = Libreria.server();
		if (server == null) return;
		ClientboundServerLinksPacket packet = new ClientboundServerLinksPacket(merged(server).untrust());
		for (ServerPlayer p : server.getPlayerList().getPlayers()) p.connection.send(packet);
	}

	/** The links window (from the quick menu). */
	public static void open(ServerPlayer player) {
		List<Kind> links = present();
		if (links.isEmpty()) {
			Toast.show(player, Items.PAPER, Toast.INFO, "No links yet",
					Ui.isOp(player) ? "Add them in Settings › Server settings." : "This server hasn't set any links.");
			return;
		}
		Window w = Window.path("Help", "Links").text("The game asks before it opens a web page.");
		for (Kind k : links) {
			w.link(Component.literal(k.name()).withStyle(st -> st.withColor(k.color())), Ui.text(k.tooltip(), ChatFormatting.GRAY), k.setting().get().strip());
		}
		w.back(Welcome::guide).show(player);
	}
}
