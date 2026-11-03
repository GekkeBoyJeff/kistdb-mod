package nl.libreria.collector;

import net.minecraft.ChatFormatting;

/** Zeldzaamheid van een prestatie: bepaalt de kleur van je titel en hoeveel punten hij waard is. */
public enum Rarity {
	GEWOON("Gewoon", ChatFormatting.WHITE, 1),
	ONGEWOON("Ongewoon", ChatFormatting.GREEN, 3),
	ZELDZAAM("Zeldzaam", ChatFormatting.AQUA, 8),
	EPISCH("Episch", ChatFormatting.LIGHT_PURPLE, 20),
	LEGENDARISCH("Legendarisch", ChatFormatting.GOLD, 50),
	MYTHISCH("Mythisch", ChatFormatting.RED, 120);

	public final String label;
	public final ChatFormatting color;
	public final int points;

	Rarity(String label, ChatFormatting color, int points) {
		this.label = label;
		this.color = color;
		this.points = points;
	}
}
