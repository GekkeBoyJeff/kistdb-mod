package nl.libreria.collector;

import net.minecraft.ChatFormatting;

/** Zeldzaamheid van een prestatie: bepaalt de kleur van je titel en hoeveel punten hij waard is. */
public enum Rarity {
	COMMON("Common", ChatFormatting.WHITE, 1),
	UNCOMMON("Uncommon", ChatFormatting.GREEN, 3),
	RARE("Rare", ChatFormatting.AQUA, 8),
	EPIC("Epic", ChatFormatting.LIGHT_PURPLE, 20),
	LEGENDARY("Legendary", ChatFormatting.GOLD, 50),
	MYTHIC("Mythic", ChatFormatting.RED, 120);

	public final String label;
	public final ChatFormatting color;
	public final int points;

	Rarity(String label, ChatFormatting color, int points) {
		this.label = label;
		this.color = color;
		this.points = points;
	}
}
