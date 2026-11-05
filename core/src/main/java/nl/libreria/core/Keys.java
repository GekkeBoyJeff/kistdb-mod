package nl.libreria.core;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Keys in text as the player has them: {@code "{sneak} + {swap}"} becomes "Left Shift + F" for most players, and
 * "Left Control + F" for someone who put sneak on Control. The client fills in its own key (and in its own language),
 * so a button or tooltip always says exactly what to press. Every text made with {@link Ui#text} understands these.
 *
 * <p>Clicks in inventories ("Shift-click") are Minecraft's own and always use the Shift key itself, so they stay as
 * they are.
 */
public final class Keys {
	/** Placeholder → Minecraft's name of the key mapping. */
	public static final Map<String, String> MAPPINGS = Map.ofEntries(
			Map.entry("sneak", "key.sneak"),
			Map.entry("sprint", "key.sprint"),
			Map.entry("jump", "key.jump"),
			Map.entry("forward", "key.forward"),
			Map.entry("left", "key.left"),
			Map.entry("back", "key.back"),
			Map.entry("right", "key.right"),
			Map.entry("swap", "key.swapOffhand"),
			Map.entry("drop", "key.drop"),
			Map.entry("use", "key.use"),
			Map.entry("attack", "key.attack"),
			Map.entry("pick", "key.pickItem"),
			Map.entry("inventory", "key.inventory"),
			Map.entry("chat", "key.chat"),
			Map.entry("menu", "key.quickActions"));

	private static final Pattern TOKEN = Pattern.compile("\\{([a-z]+)}");

	private Keys() {
	}

	/** Does this text contain a key? */
	public static boolean has(String s) {
		return s.indexOf('{') >= 0 && TOKEN.matcher(s).find();
	}

	/** The key of one mapping ("sneak", "swap" …), as the player has it. */
	public static MutableComponent key(String name) {
		String mapping = MAPPINGS.get(name);
		return mapping == null ? Component.literal("{" + name + "}") : Component.keybind(mapping);
	}

	/** Text with its {@code {key}} placeholders as the player's own keys (unknown ones stay as they are). */
	public static MutableComponent text(String s) {
		MutableComponent out = Component.empty();
		Matcher m = TOKEN.matcher(s);
		int last = 0;
		while (m.find()) {
			if (!MAPPINGS.containsKey(m.group(1))) continue;
			if (m.start() > last) out.append(Component.literal(s.substring(last, m.start())));
			out.append(key(m.group(1)));
			last = m.end();
		}
		if (last < s.length()) out.append(Component.literal(s.substring(last)));
		return out;
	}

	/** The text without placeholders, for width measuring and logs: "{sneak}" reads as "Shift". */
	public static String plain(String s) {
		return s.replace("{sneak}", "Shift").replace("{swap}", "F").replace("{drop}", "Q").replace("{use}", "Right Button")
				.replace("{attack}", "Left Button").replace("{inventory}", "E").replace("{menu}", "G").replace("{sprint}", "Ctrl")
				.replace("{jump}", "Space").replace("{pick}", "Middle Button").replace("{chat}", "T")
				.replace("{forward}", "W").replace("{left}", "A").replace("{back}", "S").replace("{right}", "D");
	}
}
