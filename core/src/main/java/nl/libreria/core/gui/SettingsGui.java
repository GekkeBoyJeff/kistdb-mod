package nl.libreria.core.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import nl.libreria.core.Module;
import nl.libreria.core.Setting;
import nl.libreria.core.Ui;

/** Settings: first pick a module, then flip its switches (icon on top, a green/red lamp underneath). */
public final class SettingsGui {
	private SettingsGui() {
	}

	public static void open(ServerPlayer player) {
		new ModulesPage(player).open();
	}

	/** Opens one module's settings directly (Back goes to the list of all settings). */
	public static void open(ServerPlayer player, Module module) {
		new ModulePage(player, module).open();
	}

	private static final class ModulesPage extends Menu {
		ModulesPage(ServerPlayer player) {
			super(player, 5, "Settings");
			List<Module> modules = Module.all().stream().filter(m -> !m.settings().isEmpty()).toList();
			// Up to 14 modules in two centred rows
			int first = Math.min(7, modules.size());
			int[] top = row(1, first);
			int[] bottom = row(2, modules.size() - first);
			for (int i = 0; i < modules.size() && i < 14; i++) {
				Module m = modules.get(i);
				GuiElementBuilder b = button(m.icon, m.name, ChatFormatting.GOLD, m.description.toArray(String[]::new));
				long on = m.settings().stream().filter(s -> s instanceof Setting.Toggle t && t.get(player)).count();
				long toggles = m.settings().stream().filter(s -> s instanceof Setting.Toggle).count();
				b.addLoreLine(net.minecraft.network.chat.Component.empty());
				b.addLoreLine(Ui.text(m.settings().size() + (m.settings().size() == 1 ? " setting" : " settings")
						+ (toggles > 0 ? " · " + on + "/" + toggles + " on" : ""), ChatFormatting.DARK_GRAY));
				action(b, "Click: open");
				b.setCallback((index, type, action, gui) -> {
					Ui.click(player);
					new ModulePage(player, m).open();
				});
				this.setSlot(i < 7 ? top[i] : bottom[i - 7], b);
			}
			back("the Libreria menu", () -> HubGui.open(player));
			closeButton();
			help("Every player has their own settings.", "Nothing here affects other players.");
			finish();
		}
	}

	private static final class ModulePage extends Menu {
		private final Module module;

		ModulePage(ServerPlayer player, Module module) {
			super(player, Math.min(6, module.settings().size() > 7 ? 6 : 4), module.name + " · Settings");
			this.module = module;
			rebuild();
		}

		private void rebuild() {
			clearContent();
			List<Setting> settings = this.module.settings();
			int first = Math.min(7, settings.size());
			int[] top = row(1, first);
			int[] second = row(3, Math.max(0, Math.min(7, settings.size() - first)));
			for (int i = 0; i < settings.size() && i < 14; i++) {
				int slot = i < 7 ? top[i] : second[i - 7];
				// Each setting: icon, with its lamp (or ◀ value ▶) directly below
				setting(slot, settings.get(i), this::rebuild);
			}
			back("all settings", () -> SettingsGui.open(this.player));
			closeButton();
			help("Click a setting or the lamp below it", "to change it. Changes apply at once.");
			finish();
		}
	}
}
