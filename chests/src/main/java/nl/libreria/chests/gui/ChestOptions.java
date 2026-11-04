package nl.libreria.chests.gui;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.chests.Beam;
import nl.libreria.chests.Containers;
import nl.libreria.chests.Labels;
import nl.libreria.chests.Search;
import nl.libreria.chests.Store;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Window;
import nl.libreria.core.gui.Toast;

/**
 * One chest's options (shift-click the Sort button in a chest): may crafting and hotbar refills take from it, does it
 * get a label, is it searched. Taking from a chest only works on your own land, so nobody else's chest is ever used.
 */
public final class ChestOptions {
	private ChestOptions() {
	}

	public static void open(ServerPlayer player, ServerLevel level, BlockPos key) {
		Store.track(player.getUUID(), level, key, Containers.blockId(level, key));
		Store.PlayerData data = Store.get(player.getUUID());
		Store.Entry entry = data.find(Store.dimId(level), key);
		if (entry == null) return;
		player.closeContainer();
		String kind = Search.kind(entry).toLowerCase(Locale.ROOT);
		boolean own = Services.onOwnLand(player, level, key);
		boolean claims = Services.land().isPresent();

		long items = entry.items.values().stream().mapToLong(Integer::longValue).sum();
		Window w = Window.of(Ui.text(cap(kind) + " options", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD));
		w.item(icon(entry), Ui.text("At " + entry.x + " " + entry.y + " " + entry.z + " · " + Fmt.number(items)
				+ (items == 1 ? " item" : " items"), ChatFormatting.GRAY));
		if (own) {
			w.text(Ui.text("Supply chest: when you craft (recipe book) or a stack in your hotbar runs out, missing items come "
					+ "from here. Only chests on your own land can be supply chests.", ChatFormatting.GRAY));
			w.toggle("supply", Ui.text("Supply chest for crafting and refills", ChatFormatting.WHITE), entry.supply);
		} else {
			w.text(Ui.text(claims ? "Supply chest: only for chests on your own land. Claim this land first."
					: "Supply chest: needs land claims on this server.", ChatFormatting.DARK_GRAY));
		}
		w.toggle("label", Ui.text("Label above it (only you see it)", ChatFormatting.WHITE), !entry.noLabel);
		w.toggle("search", Ui.text("Include in your item search", ChatFormatting.WHITE), !entry.noIndex);
		w.button(Ui.text("✔ Save", ChatFormatting.GREEN), Ui.text("Keep these options", ChatFormatting.GRAY), (p, v) -> {
			Store.PlayerData d = Store.get(p.getUUID());
			Store.Entry e = d.find(Store.dimId(level), key);
			if (e == null) return;
			// Checked again: the land could have changed while the window was open
			e.supply = Services.onOwnLand(p, level, key) && v.bool("supply", e.supply);
			e.noLabel = !v.bool("label", !e.noLabel);
			e.noIndex = !v.bool("search", !e.noIndex);
			d.markDirty();
			Labels.refresh(p);
			Beam.flash(p, level, key, e.supply ? 0x55FF55 : 0xFFAA00, 30);
			Toast.show(p, icon(e), Toast.GOOD, Ui.text(cap(kind) + " saved", ChatFormatting.WHITE),
					Ui.text(summary(e), ChatFormatting.GRAY));
		});
		w.done().show(player);
	}

	private static String summary(Store.Entry e) {
		StringBuilder b = new StringBuilder(e.supply ? "Supply chest" : "Not a supply chest");
		if (e.noLabel) b.append(" · no label");
		if (e.noIndex) b.append(" · left out of searches");
		return b.toString();
	}

	/** The item it holds most of, or the chest itself. */
	public static ItemStack icon(Store.Entry e) {
		String best = null;
		int most = 0;
		for (var it : e.items.entrySet()) {
			if (it.getValue() > most) {
				most = it.getValue();
				best = it.getKey();
			}
		}
		if (best != null) {
			var item = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(best));
			if (item.isPresent()) return new ItemStack(item.get());
		}
		var block = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(e.block));
		return new ItemStack(block.orElse(Items.CHEST));
	}

	private static String cap(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
