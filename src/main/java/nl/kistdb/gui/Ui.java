package nl.kistdb.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;

/** Kleine hulpjes voor de menu's. */
final class Ui {
	private Ui() {
	}

	static MutableComponent text(String s, ChatFormatting color) {
		return Component.literal(s).withStyle(st -> st.withItalic(false).withColor(color));
	}

	static MutableComponent kv(String key, String value, ChatFormatting valueColor) {
		return text(key, ChatFormatting.GRAY).append(text(value, valueColor));
	}

	static GuiElementBuilder button(Item item, String name, ChatFormatting color) {
		return new GuiElementBuilder(item).setName(text(name, color));
	}

	static void click(ServerPlayer player) {
		nl.kistdb.Sounds.play(player, SoundEvents.UI_BUTTON_CLICK, 0.4f, 1f);
	}

	static String meters(double d) {
		return Math.round(d) + " m";
	}
}
