package nl.libreria.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.dialog.Window;
import nl.libreria.core.gui.TopCard;

/**
 * A quiet hello for players who are new here: a card at the top of the screen for a few seconds, a little while after
 * they joined. Never a popup, never chat. The "Getting started" window (quick menu, G) explains the rest with the
 * player's own keys.
 */
public final class Welcome {
	public static final class Data {
		public boolean seen;
	}

	static final PlayerStore<Data> STORE = PlayerStore.create("libreria/welcome", Data.class, Data::new);

	public static final ServerSetting.Text LINE = Libreria.MODULE.serverText("welcome", "Welcome line", Items.OAK_SIGN, "", 80,
			"Shown under \"Welcome, <name>!\" on the", "card new players see. Empty: a default line.");

	private static final String CHANNEL = "welcome";
	/** Wait a bit after joining, so the card doesn't land in the middle of the loading screen. */
	private static final int DELAY = 120, SHOWN = 220;

	/** Player → tick at which to show the card. */
	private static final Map<UUID, Integer> PENDING = new HashMap<>();

	private Welcome() {
	}

	static void init() {
		// Loading the class registers the welcome line setting
	}

	static void onJoin(ServerPlayer player, int now) {
		if (!STORE.get(player).seen) PENDING.put(player.getUUID(), now + DELAY);
	}

	static void forget(UUID id) {
		PENDING.remove(id);
	}

	static void tick(MinecraftServer server, int now) {
		for (Iterator<Map.Entry<UUID, Integer>> it = PENDING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Integer> e = it.next();
			if (e.getValue() > now) continue;
			it.remove();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p != null) card(p);
		}
	}

	/** The card itself (also used by the self-tests and screenshots). */
	public static void card(ServerPlayer p) {
		STORE.get(p).seen = true;
		STORE.markDirty(p);
		String line = LINE.isSet() ? LINE.get() : "A few handy extras here. No mods needed.";
		List<TopCard.Row> rows = List.of(
				TopCard.Row.text("key", "Quick menu", key("key.quickActions")),
				TopCard.Row.text("chest", "Your items", Ui.text("E, then the magnifier", ChatFormatting.WHITE)),
				TopCard.Row.text("star", "Getting started", Ui.text("in the quick menu", ChatFormatting.WHITE)));
		TopCard.show(p, CHANNEL, new TopCard.Card(new ItemStack(Items.WRITABLE_BOOK), 0xFFAA00,
				Ui.text("Welcome, " + p.getName().getString() + "!", ChatFormatting.GOLD), Ui.text(line, ChatFormatting.GRAY), rows),
				30, SHOWN);
	}

	private static MutableComponent key(String mapping) {
		return key(mapping, ChatFormatting.WHITE);
	}

	private static MutableComponent key(String mapping, ChatFormatting color) {
		return Component.keybind(mapping).withStyle(s -> s.withColor(color).withItalic(false));
	}

	/** "Getting started": what's here and how to reach it, with the player's own keys. */
	public static void guide(ServerPlayer p) {
		Window w = Window.of(Ui.text("Getting started", ChatFormatting.GOLD).withStyle(ChatFormatting.BOLD));
		w.text(Ui.text("Everything works with a normal Minecraft: nothing to install.", ChatFormatting.GRAY));
		w.text(line("Quick menu", key("key.quickActions", ChatFormatting.YELLOW), "everything below, one key away. Also in the pause menu."));
		w.text(line("Your items", key("key.inventory", ChatFormatting.YELLOW), "then the magnifier: search your inventory and your chests."));
		w.text(line("Sort", key("key.inventory", ChatFormatting.YELLOW), "then click beside the window: sorts; right-click puts items away in chests."));
		w.text(line("Land map", Component.empty().append(key("key.sneak", ChatFormatting.YELLOW)).append(" + ")
				.append(key("key.swapOffhand", ChatFormatting.YELLOW)),
				"claim land and see whose land is where."));
		w.text(line("Look at things", Component.literal("—"), "a card at the top tells you what you're looking at."));
		w.text(Ui.text("Change anything you like in Settings: it only changes things for you.", ChatFormatting.GRAY));
		Services.menu(Services.MENU_CHESTS).ifPresent(o -> w.button("Your items", "Search everything you own", o));
		Services.menu(Services.MENU_LAND_MAP).ifPresent(o -> w.button("Land map", "Claim land", o));
		Services.menu(Services.MENU_WAYPOINTS).ifPresent(o -> w.button("Waypoints", "Save places and find them back", o));
		w.button("Settings", "Your own settings", nl.libreria.core.gui.SettingsWindow::open);
		w.done().show(p);
	}

	private static Component line(String what, Component key, String text) {
		return Component.empty().append(Ui.text(what, ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD))
				.append(Ui.text("  ", ChatFormatting.GRAY)).append(Component.empty().append(key).withStyle(ChatFormatting.YELLOW))
				.append(Ui.text("  " + text, ChatFormatting.GRAY));
	}
}
