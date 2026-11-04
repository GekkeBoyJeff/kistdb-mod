package nl.libreria.collector;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * Eén prestatie. Voortgang = {@code goal.current(...)} van {@code goal.target()}; behaald zodra current ≥ target.
 * Verzamel-prestaties hebben ook de lijst items ({@link #items}), zodat je in het menu ziet wat je nog mist.
 */
public record Achievement(String id, Category category, String name, String description, Item icon, Rarity rarity,
		Goal goal, @Nullable List<String> items) {

	/**
	 * The tabs. The enum names are stored nowhere but in the advancement ids of the tab roots, so they can be renamed
	 * freely; what players earned is kept by achievement id.
	 */
	public enum Category {
		VERZAMELEN("Collecting", "Items you ever had in your inventory", "chest", "gui/advancements/backgrounds/stone"),
		MIJNBOUW("Mining", "Stone, ore and everything you dig", "iron_pickaxe", "block/deepslate"),
		GEVECHT("Combat", "Monsters, bosses and raids", "iron_sword", "gui/advancements/backgrounds/nether"),
		FOKKEN("Farming & animals", "Crops, breeding, fishing and villagers", "wheat", "gui/advancements/backgrounds/husbandry"),
		ONTDEKKEN("Exploring", "Travel, places and the far corners", "compass", "gui/advancements/backgrounds/adventure"),
		MAKEN("Crafting & building", "What you make, place and use", "crafting_table", "block/oak_planks"),
		TIJD("Playtime & life", "Time here, eating, sleeping, staying alive", "clock", "gui/advancements/backgrounds/end");

		public final String label;
		public final String hint;
		/** Item id of the tab's icon. */
		public final String icon;
		/** Texture behind the tab in the advancements screen (L). */
		public final String background;

		Category(String label, String hint, String icon, String background) {
			this.label = label;
			this.hint = hint;
			this.icon = icon;
			this.background = background;
		}
	}

	public interface Goal {
		long current(ServerPlayer player, CollectorMod.Data data);

		long target();

		/** Telt dit item als "verzameld" voor deze prestatie? Standaard: ooit in je inventory gehad. */
		default boolean has(ServerPlayer player, CollectorMod.Data data, String item) {
			return data.items.contains(item);
		}
	}

	public boolean has(ServerPlayer player, CollectorMod.Data data, String item) {
		return this.goal.has(player, data, item);
	}

	public long current(ServerPlayer player, CollectorMod.Data data) {
		return Math.min(this.goal.current(player, data), this.goal.target());
	}

	public long target() {
		return this.goal.target();
	}

	public boolean isCollection() {
		return this.items != null;
	}
}
