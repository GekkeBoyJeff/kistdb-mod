package nl.kistdb.gui;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import nl.kistdb.Beams;
import nl.kistdb.Search;

/** Straal naar de dichtstbijzijnde kist + een lijstje in de chat. */
public final class Locate {
	private Locate() {
	}

	public static void go(ServerPlayer player, ItemStack item, List<Search.Hit> hits) {
		if (hits.isEmpty()) {
			player.sendSystemMessage(prefix().append(Ui.text("Niet gevonden in je kisten in de buurt.", ChatFormatting.RED)));
			return;
		}
		Search.Hit near = hits.getFirst();
		Beams.show(player, player.level(), near.entry().pos(), item, near.count(), 90);

		long total = hits.stream().mapToLong(Search.Hit::count).sum();
		player.sendSystemMessage(prefix()
				.append(Ui.text(total + "× ", ChatFormatting.GREEN))
				.append(item.getHoverName().copy().withStyle(ChatFormatting.WHITE))
				.append(Ui.text(" in " + hits.size() + (hits.size() == 1 ? " kist:" : " kisten:"), ChatFormatting.GRAY)));
		int max = Math.min(5, hits.size());
		for (int i = 0; i < max; i++) {
			player.sendSystemMessage(line(hits.get(i), i == 0));
		}
		if (hits.size() > max) {
			player.sendSystemMessage(Ui.text("   … en nog " + (hits.size() - max) + " (zie /kist)", ChatFormatting.DARK_GRAY));
		}
	}

	private static MutableComponent line(Search.Hit hit, boolean target) {
		var e = hit.entry();
		MutableComponent c = Ui.text(target ? " ➜ " : " • ", target ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
				.append(Ui.text(hit.count() + "× ", ChatFormatting.GREEN))
				.append(Ui.text(Search.kind(e).toLowerCase() + " op ", ChatFormatting.GRAY))
				.append(Ui.text(Ui.meters(hit.distance()), ChatFormatting.YELLOW))
				.append(Ui.text("  (" + e.x + " " + e.y + " " + e.z + ")", ChatFormatting.GRAY));
		if (!hit.live()) {
			c.append(Ui.text("  laatst gezien " + Search.ago(e.seen), ChatFormatting.DARK_GRAY));
		}
		if (!target) {
			c.withStyle(st -> st
					.withClickEvent(new ClickEvent.RunCommand("/kist straal " + e.x + " " + e.y + " " + e.z))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal("Klik: straal naar deze kist"))));
		}
		return c;
	}

	static MutableComponent prefix() {
		return Ui.text("[KistDB] ", ChatFormatting.GOLD);
	}
}
