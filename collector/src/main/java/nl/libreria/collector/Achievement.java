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

	public enum Category {
		VERZAMELEN("Verzamelen", "Items die je ooit in je inventory had"),
		FOKKEN("Fokken", "Dieren en dorpelingen"),
		TIJD("Speeltijd", "Tijd op de server"),
		AVONTUUR("Avontuur", "Mijnen, vechten, reizen…");

		public final String label;
		public final String hint;

		Category(String label, String hint) {
			this.label = label;
			this.hint = hint;
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
