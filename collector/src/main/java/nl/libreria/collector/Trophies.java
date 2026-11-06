package nl.libreria.collector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * Trophies: a record that is a real feat (the longest fall is 150 m or more, say) makes its holder the one player with
 * that trophy. You wear one, as a small gold emblem before your title. It's yours while the record is: whoever beats
 * it has the trophy from then on. Records that aren't a feat (the time online in one go, the deepest point) have none.
 */
public final class Trophies {
	private static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("libreria", "trophy"));

	private Trophies() {
	}

	/** Is this value a feat (worth a trophy) for this record? */
	public static boolean isFeat(Records.Kind k, double v) {
		return k.hasTrophy() && (k.lower ? v <= k.trophy : v >= k.trophy);
	}

	/** Does this player have the trophy of this record (they hold the record, and it's a feat)? */
	public static boolean has(UUID who, Records.Kind k) {
		Records.Rec r = Records.get(k);
		return r != null && who.equals(r.who) && isFeat(k, r.value);
	}

	/** The trophies a player has now. */
	public static List<Records.Kind> of(UUID who) {
		List<Records.Kind> out = new ArrayList<>();
		for (Records.Kind k : Records.Kind.values()) if (has(who, k)) out.add(k);
		return out;
	}

	/** The trophy a player wears (chosen, and still theirs), or null. */
	public static @Nullable Records.Kind worn(UUID who, CollectorMod.Data d) {
		if (d.trophy == null || d.trophy.isEmpty()) return null;
		for (Records.Kind k : Records.Kind.values()) {
			if (k.id.equals(d.trophy)) return has(who, k) ? k : null;
		}
		return null;
	}

	/** The emblem as a character of the trophy font (white: it's gold already). */
	public static MutableComponent glyph(Records.Kind k) {
		return Component.literal(String.valueOf((char) (0xE000 + k.ordinal()))).withStyle(s -> s.withFont(FONT).withColor(0xFFFFFF)
				.withBold(false).withItalic(false));
	}

	/** The emblem as an item (for the Records window). */
	public static ItemStack item(Records.Kind k) {
		ItemStack s = new ItemStack(Items.GOLD_NUGGET);
		s.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("libreria", "trophy/" + k.id));
		return s;
	}

	/** Wear this trophy, or take it off if you're wearing it. */
	public static void toggle(ServerPlayer p, Records.Kind k) {
		CollectorMod.Data d = CollectorMod.STORE.get(p);
		if (!has(p.getUUID(), k)) return;
		boolean off = k.id.equals(d.trophy);
		d.trophy = off ? "" : k.id;
		CollectorMod.STORE.markDirty(p);
		Ui.sound(p, SoundEvents.ARMOR_EQUIP_GOLD.value(), 0.8f, off ? 0.9f : 1.3f);
		nl.libreria.core.Notify.card(p, item(k), nl.libreria.core.gui.Toast.GOOD,
				Ui.text(off ? "Trophy off" : "Trophy on", ChatFormatting.WHITE),
				off ? Ui.text("Nothing before your title now.", ChatFormatting.GRAY)
						: Component.empty().append(glyph(k)).append(Ui.text(" " + k.about + ", before your title", ChatFormatting.GRAY)));
	}

	/**
	 * Once, for players from before trophies: a record title (from the old "hold the record" achievements) becomes
	 * the trophy of that record if it's still theirs and a feat; the title goes back to automatic.
	 */
	static void migrate(ServerPlayer p) {
		CollectorMod.Data d = CollectorMod.STORE.get(p);
		if (d.title == null || !d.title.startsWith("record.")) return;
		String id = d.title.substring("record.".length());
		d.title = "";
		for (Records.Kind k : Records.Kind.values()) {
			if (k.id.equals(id) && has(p.getUUID(), k)) d.trophy = k.id;
		}
		CollectorMod.STORE.markDirty(p);
	}

	/** "150 m", "48 h": from what a record is a trophy. */
	public static String bar(Records.Kind k) {
		return k.format(k.trophy);
	}
}
