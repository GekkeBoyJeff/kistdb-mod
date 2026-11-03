package nl.libreria.core.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import nl.libreria.core.Guide;
import nl.libreria.core.Module;
import nl.libreria.core.Ui;

/** Het Libreria-menu: één knop per functie van alle geïnstalleerde modules, plus Instellingen. */
public final class HubGui extends SimpleGui {
	private HubGui(ServerPlayer player) {
		super(MenuType.GENERIC_9x4, player, false);
		this.setTitle(Component.literal("Libreria"));
		List<Module.HubEntry> entries = Module.allHubEntries();
		// Netjes gecentreerd in de bovenste drie rijen
		int[] order = {10, 11, 12, 13, 14, 15, 16, 1, 2, 3, 4, 5, 6, 7, 19, 20, 21, 22, 23, 24, 25};
		for (int i = 0; i < entries.size() && i < order.length; i++) {
			Module.HubEntry e = entries.get(i);
			GuiElementBuilder b = Ui.button(e.icon(), e.name(), e.color());
			for (String l : e.lore()) b.addLoreLine(Ui.text(l, ChatFormatting.GRAY));
			b.addLoreLine(Ui.text(e.module().name, ChatFormatting.DARK_GRAY));
			b.setCallback((index, type, action, gui) -> {
				Ui.click(player);
				this.close();
				e.action().accept(player);
			});
			this.setSlot(order[i], b.build());
		}
		this.setSlot(31, Ui.button(Items.COMPARATOR, "Instellingen", ChatFormatting.WHITE)
				.addLoreLine(Ui.text("Per module aan/uit en afstellen", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("Ook: /opties", ChatFormatting.DARK_GRAY))
				.setCallback((index, type, action, gui) -> {
					Ui.click(player);
					SettingsGui.open(player);
				}).build());
		if (Guide.has(player)) {
			this.setSlot(35, Ui.button(Items.BARRIER, "Straal uitzetten", ChatFormatting.RED)
					.setCallback((index, type, action, gui) -> {
						Guide.clear(player);
						Ui.click(player);
						this.close();
					}).build());
		}
	}

	public static void open(ServerPlayer player) {
		new HubGui(player).open();
	}
}
