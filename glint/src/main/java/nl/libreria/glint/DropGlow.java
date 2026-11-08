package nl.libreria.glint;

import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import nl.libreria.core.ServerSetting;
import org.jetbrains.annotations.Nullable;

/**
 * Uncommon, rare and epic items lying on the ground glow in the colour of their rarity (yellow, light blue, purple), so
 * a good drop is never missed. Minecraft's own glow, coloured by a team per rarity; only items, nothing else is in
 * these teams.
 */
final class DropGlow {
	static final ServerSetting.Toggle ON = GlintMod.MODULE.serverToggle("drop_glow", "Rare items on the ground glow", Items.GLOWSTONE_DUST,
			true, "Uncommon, rare and epic items on the", "ground glow in their rarity's colour", "(also seen through walls nearby).");
	private static final Map<Rarity, String> TEAMS = Map.of(Rarity.UNCOMMON, "libreria_drop_uncommon", Rarity.RARE, "libreria_drop_rare",
			Rarity.EPIC, "libreria_drop_epic");
	private static final Map<Rarity, ChatFormatting> COLOURS = Map.of(Rarity.UNCOMMON, ChatFormatting.YELLOW, Rarity.RARE, ChatFormatting.AQUA,
			Rarity.EPIC, ChatFormatting.LIGHT_PURPLE);

	private DropGlow() {
	}

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item) apply(level.getServer(), item);
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item) leave(level.getServer().getScoreboard(), item);
		});
		// A stack that changed (merged, or a mod changed it), and the switch: the items around players looked at again
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 != 17) return;
			for (var p : server.getPlayerList().getPlayers()) {
				for (ItemEntity item : p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(64))) apply(server, item);
			}
		});
	}

	private static boolean on() {
		return GlintMod.MODULE.on() && ON.get();
	}

	/** The rarity an item on the ground glows for, or null for none. */
	static @Nullable Rarity of(ItemEntity item) {
		var stack = item.getItem();
		if (stack.isEmpty()) return null;
		Rarity r = stack.getOrDefault(net.minecraft.core.component.DataComponents.RARITY, Rarity.COMMON);
		// Enchanted: a step rarer (as Minecraft shows its name)
		if (stack.isEnchanted()) r = r == Rarity.EPIC ? Rarity.EPIC : r == Rarity.RARE ? Rarity.EPIC : Rarity.RARE;
		return r == Rarity.COMMON ? null : r;
	}

	static void apply(MinecraftServer server, ItemEntity item) {
		Scoreboard board = server.getScoreboard();
		Rarity r = on() ? of(item) : null;
		String name = item.getScoreboardName();
		PlayerTeam now = board.getPlayersTeam(name);
		if (r == null) {
			if (now != null && now.getName().startsWith("libreria_drop_")) {
				board.removePlayerFromTeam(name, now);
				item.setGlowingTag(false);
			}
			return;
		}
		PlayerTeam team = team(board, r);
		if (now != team) board.addPlayerToTeam(name, team);
		if (!item.hasGlowingTag()) item.setGlowingTag(true);
	}

	private static void leave(Scoreboard board, ItemEntity item) {
		PlayerTeam now = board.getPlayersTeam(item.getScoreboardName());
		if (now != null && now.getName().startsWith("libreria_drop_")) board.removePlayerFromTeam(item.getScoreboardName(), now);
	}

	private static PlayerTeam team(Scoreboard board, Rarity r) {
		String name = TEAMS.get(r);
		PlayerTeam t = board.getPlayerTeam(name);
		if (t == null) {
			t = board.addPlayerTeam(name);
			t.setColor(COLOURS.get(r));
		}
		return t;
	}
}
