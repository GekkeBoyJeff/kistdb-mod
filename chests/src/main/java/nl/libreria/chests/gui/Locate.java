package nl.libreria.chests.gui;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import nl.libreria.chests.Beam;
import nl.libreria.chests.Search;
import nl.libreria.core.Ui;

/** Beam to the nearest chest with an item, plus a short clickable list in chat. */
public final class Locate {
	private static final int SHOWN = 5;

	private Locate() {
	}

	public static void go(ServerPlayer player, ItemStack item, List<Search.Hit> hits) {
		if (hits.isEmpty()) {
			player.sendSystemMessage(Ui.prefix("Chests")
					.append(Ui.text("No ", ChatFormatting.RED))
					.append(item.getHoverName().copy().withStyle(ChatFormatting.WHITE))
					.append(Ui.text(" in your chests within " + (int) Search.radius() + " m.", ChatFormatting.RED))
					.append(Ui.text(" Open a chest to add it to your index.", ChatFormatting.GRAY)));
			return;
		}
		Search.Hit near = hits.getFirst();
		Beam.to(player, player.level(), near.entry().pos(), item, near.count());

		long total = hits.stream().mapToLong(Search.Hit::count).sum();
		player.sendSystemMessage(Ui.prefix("Chests")
				.append(Ui.text(Fmt.number(total) + "× ", ChatFormatting.GREEN))
				.append(item.getHoverName().copy().withStyle(ChatFormatting.WHITE))
				.append(Ui.text(" in " + Fmt.count(hits.size(), "chest", "chests") + " – beaming to the nearest ", ChatFormatting.GRAY))
				.append(link("[stop]", ChatFormatting.RED, "/chest stop", "Click: stop the beam")));
		int max = Math.min(SHOWN, hits.size());
		for (int i = 0; i < max; i++) {
			player.sendSystemMessage(line(hits.get(i), i == 0));
		}
		if (hits.size() > max) {
			String id = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
			player.sendSystemMessage(Ui.text("   … and " + (hits.size() - max) + " more ", ChatFormatting.DARK_GRAY)
					.append(link("[show all]", ChatFormatting.AQUA, "/chest where " + id, "Click: list every chest with it")));
		}
	}

	private static MutableComponent line(Search.Hit hit, boolean target) {
		var e = hit.entry();
		MutableComponent c = Ui.text(target ? " ➜ " : " • ", target ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)
				.append(Ui.text(Fmt.number(hit.count()) + "× ", ChatFormatting.GREEN))
				.append(Ui.text("in a " + Search.kind(e).toLowerCase(Locale.ROOT) + ", ", ChatFormatting.GRAY))
				.append(Ui.text(Ui.meters(hit.distance()), ChatFormatting.YELLOW))
				.append(Ui.text("  (" + Fmt.where(e) + ")", ChatFormatting.GRAY));
		if (!hit.live()) {
			c.append(Ui.text("  last seen " + Search.ago(e.seen), ChatFormatting.DARK_GRAY));
		}
		if (target) {
			c.append(Ui.text("  ← beam", ChatFormatting.AQUA));
		} else {
			c.append(Ui.text("  ", ChatFormatting.GRAY))
					.append(link("[beam]", ChatFormatting.AQUA, "/chest beam " + e.x + " " + e.y + " " + e.z, "Click: beam to this chest"));
		}
		return c;
	}

	/** Clickable chat text that runs a command. */
	private static MutableComponent link(String text, ChatFormatting color, String command, String hover) {
		return Component.literal(text).withStyle(st -> st.withItalic(false).withColor(color)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}
}
