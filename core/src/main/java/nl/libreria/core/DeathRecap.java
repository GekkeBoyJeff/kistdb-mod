package nl.libreria.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import nl.libreria.core.dialog.Window;

/**
 * After you respawn: a short window with what happened (the death message, where and how far away) and buttons the
 * modules add, like "Show the way to your grave". Instead of a pile of chat lines; a setting turns it off.
 */
public final class DeathRecap {
	public static final Setting.Toggle ENABLED = Libreria.MODULE.toggle("death_recap", "What happened, after dying", true, Items.SKELETON_SKULL,
			"After respawning: a window with how and", "where you died, and the way back.").in(Domain.PLACES, "When you die");

	private record Death(Component message, String dim, BlockPos pos) {
	}

	private static final Map<UUID, Death> LAST = new HashMap<>();
	/** Player → tick at which to show the window (a moment after respawning, so the world is there first). */
	private static final Map<UUID, Integer> PENDING = new HashMap<>();

	private DeathRecap() {
	}

	public static boolean enabled(ServerPlayer p) {
		return ENABLED.get(p);
	}

	public static void onDeath(ServerPlayer p) {
		LAST.put(p.getUUID(), new Death(p.getCombatTracker().getDeathMessage(), p.level().dimension().identifier().toString(),
				p.blockPosition()));
	}

	static void onRespawn(ServerPlayer p, int now) {
		if (ENABLED.get(p) && LAST.containsKey(p.getUUID())) PENDING.put(p.getUUID(), now + 15);
	}

	static void tick(MinecraftServer server, int now) {
		PENDING.entrySet().removeIf(e -> {
			if (e.getValue() > now) return false;
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p != null && p.isAlive()) open(p);
			return true;
		});
	}

	static void forget(UUID id) {
		LAST.remove(id);
		PENDING.remove(id);
	}

	/** The window itself. */
	public static void open(ServerPlayer p) {
		Death d = LAST.get(p.getUUID());
		if (d == null) return;
		Window w = Window.of(Ui.text("☠ You died", ChatFormatting.RED).withStyle(ChatFormatting.BOLD));
		w.text(Component.empty().append(d.message()).withStyle(ChatFormatting.WHITE));
		String where = "At " + d.pos().getX() + " " + d.pos().getY() + " " + d.pos().getZ() + " in " + dimension(d.dim());
		if (p.level().dimension().identifier().toString().equals(d.dim())) {
			where += " · " + Math.round(Math.sqrt(p.blockPosition().distSqr(d.pos()))) + " m from here";
		}
		w.text(Ui.text(where, ChatFormatting.GRAY));
		// The modules' buttons say the rest ("Show the way to your grave · 23 items wait there"); their chat notes
		// would only say the same thing twice here
		List<Services.DeathAction> actions = Services.deathActions(p);
		if (actions.isEmpty()) for (Component note : Services.deathNotes(p)) w.text(note);
		for (Services.DeathAction a : actions) w.button(a.label(), a.tooltip(), a.run());
		if (!p.level().dimension().identifier().toString().equals(d.dim())) {
			w.text(Ui.text("Go back to " + dimension(d.dim()) + " to be shown the way there.", ChatFormatting.DARK_GRAY));
		}
		w.done().show(p);
	}

	static String dimension(String dim) {
		return switch (dim) {
			case "minecraft:overworld" -> "the Overworld";
			case "minecraft:the_nether" -> "the Nether";
			case "minecraft:the_end" -> "the End";
			default -> dim.substring(dim.indexOf(':') + 1).replace('_', ' ');
		};
	}
}
