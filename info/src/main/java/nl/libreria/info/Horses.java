package nl.libreria.info;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import nl.libreria.core.Ui;

/** Paardwaarden omgerekend naar iets begrijpelijks: meter per seconde en blokken springhoogte. */
final class Horses {
	// Bereik van een gewoon paard in vanilla, voor de balkjes
	private static final double SPEED_MIN = 4.74, SPEED_MAX = 14.23;
	private static final double JUMP_MIN = 1.09, JUMP_MAX = 5.29;
	private static final double HEALTH_MIN = 15, HEALTH_MAX = 30;

	private Horses() {
	}

	/** Topsnelheid in blokken (meter) per seconde. */
	static double speed(AbstractHorse horse) {
		return horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * 42.16;
	}

	/** Maximale springhoogte in blokken. */
	static double jump(AbstractHorse horse) {
		return jumpHeight(horse.getAttributeValue(Attributes.JUMP_STRENGTH));
	}

	/** Bekende benadering van de springhoogte uit de sprongkracht. */
	static double jumpHeight(double j) {
		return -0.1817584952 * j * j * j + 3.689713992 * j * j + 2.128599134 * j - 0.343930367;
	}

	static String fmt(double d) {
		if (Math.abs(d - Math.round(d)) < 0.05) return Long.toString(Math.round(d));
		return String.format(Locale.ROOT, "%.1f", d);
	}

	static void sendCard(ServerPlayer player, AbstractHorse horse) {
		double speed = speed(horse), jump = jump(horse), health = horse.getMaxHealth();
		MutableComponent msg = nl.libreria.core.Say.line(nl.libreria.core.Domain.YOU)
				.append(Component.literal("━━ ").withStyle(ChatFormatting.DARK_GRAY))
				.append(horse.getDisplayName().copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
				.append(Component.literal(" ━━").withStyle(ChatFormatting.DARK_GRAY));
		msg.append("\n").append(row("Speed  ", speed, SPEED_MIN, SPEED_MAX, fmt(speed) + " m/s"));
		msg.append("\n").append(row("Jump    ", jump, JUMP_MIN, JUMP_MAX, fmt(jump) + " blocks"));
		msg.append("\n").append(row("Health  ", health, HEALTH_MIN, HEALTH_MAX, fmt(health / 2.0) + " ❤"));
		String state = horse.isBaby() ? "foal – still growing" : horse.isTamed() ? "tamed" : "wild – not tamed yet";
		msg.append("\n").append(Component.literal(state).withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(msg);
	}

	private static Component row(String label, double value, double min, double max, String text) {
		double f = Math.max(0, Math.min(1, (value - min) / (max - min)));
		return Component.empty()
				.append(Component.literal(label).withStyle(ChatFormatting.GRAY))
				.append(Ui.bar(f, 10))
				.append(Component.literal(" " + text + " ").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(grade(f)).withStyle(f > 0.8 ? ChatFormatting.GOLD : f > 0.5 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
	}

	private static String grade(double f) {
		if (f > 0.9) return "exceptional";
		if (f > 0.7) return "good";
		if (f > 0.4) return "average";
		return "poor";
	}
}
