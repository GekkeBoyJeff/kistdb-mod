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

	/** What each key does, in words (for places that can't show the player's own key). */
	private static final Map<String, String> ACTIONS = Map.ofEntries(
			Map.entry("sneak", "Sneak"), Map.entry("sprint", "Sprint"), Map.entry("jump", "Jump"),
			Map.entry("swap", "Swap hands"), Map.entry("drop", "Drop"), Map.entry("use", "Use"), Map.entry("attack", "Attack"),
			Map.entry("pick", "Pick block"), Map.entry("inventory", "Inventory"), Map.entry("chat", "Chat"),
			Map.entry("menu", "Quick actions"), Map.entry("forward", "Forward"), Map.entry("left", "Left"),
			Map.entry("back", "Back"), Map.entry("right", "Right"));

	/**
	 * The text with the keys as what they do ("Sneak + Swap hands"), never a key name that could be wrong for this
	 * player: for pictures drawn by the server, where the client can't fill in its own key.
	 */
	public static String plain(String s) {
		Matcher m = TOKEN.matcher(s);
		StringBuilder out = new StringBuilder();
		while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(ACTIONS.getOrDefault(m.group(1), m.group())));
		m.appendTail(out);
		return out.toString();
	}
}
