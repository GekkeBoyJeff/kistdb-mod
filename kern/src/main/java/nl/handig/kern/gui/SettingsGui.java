package nl.handig.kern.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import nl.handig.kern.Module;
import nl.handig.kern.Setting;
import nl.handig.kern.Ui;

/** /opties: eerst de modules, dan per module de instellingen (klik = wijzigen, rechtsklik = terug bij keuzes). */
public final class SettingsGui {
	private SettingsGui() {
	}

	public static void open(ServerPlayer player) {
		new ModulesPage(player).open();
	}

	private static final class ModulesPage extends SimpleGui {
		ModulesPage(ServerPlayer player) {
			super(MenuType.GENERIC_9x3, player, false);
			this.setTitle(Component.literal("Handig – instellingen"));
			List<Module> modules = Module.all().stream().filter(m -> !m.settings().isEmpty()).toList();
			int[] order = {10, 11, 12, 13, 14, 15, 16, 1, 2, 3, 4, 5, 6, 7};
			for (int i = 0; i < modules.size() && i < order.length; i++) {
				Module m = modules.get(i);
				GuiElementBuilder b = Ui.button(m.icon, m.name, ChatFormatting.GOLD);
				for (String d : m.description) b.addLoreLine(Ui.text(d, ChatFormatting.GRAY));
				b.addLoreLine(Ui.text(m.settings().size() + (m.settings().size() == 1 ? " instelling" : " instellingen"), ChatFormatting.DARK_GRAY));
				b.setCallback((index, type, action, gui) -> {
					Ui.click(player);
					new ModulePage(player, m).open();
				});
				this.setSlot(order[i], b.build());
			}
			this.setSlot(22, Ui.button(Items.ARROW, "Naar het Handig-menu", ChatFormatting.WHITE)
					.setCallback((index, type, action, gui) -> HubGui.open(player)).build());
		}
	}

	private static final class ModulePage extends SimpleGui {
		private final Module module;

		ModulePage(ServerPlayer player, Module module) {
			super(MenuType.GENERIC_9x3, player, false);
			this.module = module;
			this.setTitle(Component.literal(module.name));
			rebuild();
		}

		private void rebuild() {
			ServerPlayer player = this.getPlayer();
			List<Setting> settings = this.module.settings();
			int[] order = {10, 11, 12, 13, 14, 15, 16, 1, 2, 3, 4, 5, 6, 7};
			for (int i = 0; i < settings.size() && i < order.length; i++) {
				this.setSlot(order[i], element(player, settings.get(i)).build());
			}
			this.setSlot(22, Ui.button(Items.ARROW, "Terug", ChatFormatting.WHITE)
					.setCallback((index, type, action, gui) -> SettingsGui.open(player)).build());
		}

		private GuiElementBuilder element(ServerPlayer player, Setting s) {
			GuiElementBuilder b = new GuiElementBuilder(s.icon).setName(Ui.text(s.label, ChatFormatting.WHITE));
			for (String l : s.lore) b.addLoreLine(Ui.text(l, ChatFormatting.GRAY));
			b.addLoreLine(Component.empty());
			if (s instanceof Setting.Toggle t) {
				boolean on = t.get(player);
				b.addLoreLine(Ui.kv("Nu: ", on ? "AAN" : "UIT", on ? ChatFormatting.GREEN : ChatFormatting.RED));
				b.addLoreLine(Ui.text("Klik om te wisselen", ChatFormatting.DARK_GRAY));
				if (on) b.glow();
				b.setCallback((index, type, action, gui) -> {
					t.set(player, !t.get(player));
					Ui.click(player);
					rebuild();
				});
			} else if (s instanceof Setting.Choice c) {
				int current = c.index(player);
				for (int i = 0; i < c.size(); i++) {
					boolean sel = i == current;
					b.addLoreLine(Ui.text((sel ? " ▶ " : "   ") + c.labelAt(i), sel ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
				}
				b.addLoreLine(Ui.text("Klik: volgende · Rechtsklik: vorige", ChatFormatting.DARK_GRAY));
				b.setCallback((index, type, action, gui) -> {
					c.cycle(player, type.isRight ? -1 : 1);
					Ui.click(player);
					rebuild();
				});
			}
			return b;
		}
	}
}
