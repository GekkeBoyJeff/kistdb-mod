package nl.libreria.waystones;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import nl.libreria.core.Players;
import nl.libreria.core.Ui;

/**
 * /waystones check &lt;player&gt; (operators): what became of someone's waystones. How many they found, how many are
 * still there, and for those that are gone, why and when (as far as that was written down). And whether this is the
 * account they usually play on: a Bedrock account (through Floodgate) has a list of its own.
 */
final class WaystoneCheck {
	private WaystoneCheck() {
	}

	/** Bedrock players who come in through Floodgate get an id that starts with zeros. */
	static boolean floodgate(UUID id) {
		return id.getMostSignificantBits() == 0;
	}

	static List<Component> lines(MinecraftServer server, String name) {
		List<Component> out = new ArrayList<>();
		Players.Known k = Players.byName(server, name);
		if (k == null) {
			out.add(Ui.text("Nobody called " + name + " has played here.", ChatFormatting.RED));
			return out;
		}
		Stones.Found f = Stones.FOUND.get(k.id());
		List<String> there = new ArrayList<>(), gone = new ArrayList<>();
		for (String key : f.found) (Stones.byKey(key) != null ? there : gone).add(key);
		int own = 0;
		for (Stones.Stone s : Stones.list()) if (k.id().equals(s.owner)) own++;
		out.add(Ui.text("◆ ", 0xC79BF0).append(Ui.text("Waystones of " + k.name(), ChatFormatting.WHITE))
				.append(Ui.text(floodgate(k.id()) ? "  (Bedrock account)" : "", ChatFormatting.AQUA)));
		out.add(Ui.text("Found " + f.found.size() + ": " + there.size() + " still there, " + gone.size() + " gone. Placed: " + own + ".",
				ChatFormatting.GRAY));
		if (WaystonesMod.KNOWN.get() == 1) out.add(Ui.text("Every village's waystone is on everyone's list (Village waystones: everyone knows them).",
				ChatFormatting.GRAY));
		// Taken off their list by a land's owner (it's still there; they can give it back)
		List<String> off = new ArrayList<>();
		for (Stones.Stone s : Stones.list()) if (s.takenOff != null && s.takenOff.containsKey(k.id())) off.add(s.name);
		if (!off.isEmpty()) out.add(Ui.text("Taken off by its land's owner: " + String.join(", ", off) + ".", ChatFormatting.YELLOW));
		SimpleDateFormat when = new SimpleDateFormat("d MMM HH:mm", Locale.ENGLISH);
		int shown = 0;
		for (String key : gone) {
			if (shown++ == 12) {
				out.add(Ui.text("… and " + (gone.size() - 12) + " more", ChatFormatting.GRAY));
				break;
			}
			Stones.Gone g = Stones.gone(key);
			MutableComponent line = Ui.text("  ✗ ", ChatFormatting.RED);
			if (g != null) {
				line.append(Ui.text(g.name, ChatFormatting.WHITE)).append(Ui.text(" (" + place(key) + "): " + g.why + ", " + when.format(new Date(g.at)),
						ChatFormatting.GRAY));
			} else {
				line.append(Ui.text(place(key), ChatFormatting.WHITE)).append(Ui.text(": gone before Libreria wrote down why", ChatFormatting.GRAY));
			}
			out.add(line);
		}
		// The same person on another account: Bedrock (Floodgate puts a dot before the name) and Java
		for (Players.Known other : Players.all(server)) {
			if (other.id().equals(k.id())) continue;
			String a = other.name().replaceFirst("^\\.", ""), b = k.name().replaceFirst("^\\.", "");
			if (!a.equalsIgnoreCase(b)) continue;
			out.add(Ui.text("Also plays as " + other.name() + (floodgate(other.id()) ? " (Bedrock)" : "") + ": found "
					+ Stones.FOUND.get(other.id()).found.size() + " there. Each account has a list of its own.", ChatFormatting.YELLOW));
		}
		return out;
	}

	/** "Overworld 120 64 -300" from a waystone's key. */
	static String place(String key) {
		String[] p = key.split("\\|");
		if (p.length < 4) return key;
		return Stones.world(p[0]) + " " + p[1] + " " + p[2] + " " + p[3];
	}
}
