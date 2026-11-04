package nl.libreria.comfort;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import nl.libreria.core.Libreria;
import nl.libreria.core.ServerSetting;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.TopCard;

/**
 * Sleeping together: an admin sets how many players must sleep to skip the night (the game's own
 * {@code playersSleepingPercentage}), and while someone sleeps everybody in that world sees a small card with how many
 * sleep and how many are needed.
 */
final class Sleep {
	static final ServerSetting.Number PERCENT = ComfortMod.MODULE.serverNumber("sleep_percent", "Players needed to sleep",
			Items.RED_BED, 100, 0, 100, 1, "%", "How many of the players in a world must", "sleep to skip the night (0: one is enough).");

	private static final String CHANNEL = "comfort.sleep";

	private Sleep() {
	}

	static void init() {
		PERCENT.onChange(() -> {
			MinecraftServer s = Libreria.server();
			if (s != null) apply(s);
		});
	}

	/** Puts the setting into the game rule when an admin changes it. */
	static void apply(MinecraftServer server) {
		server.getGameRules().set(GameRules.PLAYERS_SLEEPING_PERCENTAGE, PERCENT.get(), server);
	}

	/**
	 * At start: a value set in the admin window wins; otherwise the setting takes over what the world already has, so
	 * installing Libreria changes nothing by itself.
	 */
	static void start(MinecraftServer server) {
		if (PERCENT.isDefault()) PERCENT.set(Math.max(0, Math.min(100, server.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE))));
		else apply(server);
	}

	/** Twice a second: the card for everybody in a world where someone sleeps. */
	static void tick(MinecraftServer server) {
		int percent = server.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
		for (ServerLevel level : server.getAllLevels()) {
			List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();
			int sleeping = (int) players.stream().filter(ServerPlayer::isSleeping).count();
			int needed = Math.max(1, (int) Math.ceil(players.size() * Math.min(100, percent) / 100.0));
			for (ServerPlayer p : level.players()) {
				if (sleeping == 0 || players.size() < 2) {
					TopCard.hide(p, CHANNEL);
					continue;
				}
				boolean enough = sleeping >= needed;
				TopCard.show(p, CHANNEL, new TopCard.Card(new ItemStack(Items.RED_BED), enough ? 0x55FF55 : 0x9AA8FF,
						Ui.text(enough ? "Good night!" : "Sleeping: " + sleeping + " of " + needed, ChatFormatting.WHITE),
						Ui.text(enough ? "The night is skipped" : (needed - sleeping) + " more and the night is skipped", ChatFormatting.GRAY),
						List.of(TopCard.Row.bar("time", "Asleep", Math.min(1, sleeping / (double) needed), 0x9AA8FF, 10,
								Ui.text(sleeping + " / " + needed, ChatFormatting.WHITE)))), 45, 30);
			}
		}
	}
}
