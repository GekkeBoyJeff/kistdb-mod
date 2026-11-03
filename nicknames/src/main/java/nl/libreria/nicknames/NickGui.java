package nl.libreria.nicknames;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.HubGui;

/** Nickname kiezen: naam typen in een aambeeld, kleur kiezen uit wat je hebt ontgrendeld. */
final class NickGui extends SimpleGui {
	/** Met lidwoord, voor "Unlock with a rare achievement". */
	private static final String[] RARITY = {"a common", "an uncommon", "a rare", "an epic", "a legendary", "a mythic"};
	private static final ChatFormatting[] RARITY_COLOR = {ChatFormatting.WHITE, ChatFormatting.GREEN, ChatFormatting.AQUA,
			ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD, ChatFormatting.RED};

	private NickGui(ServerPlayer p) {
		super(MenuType.GENERIC_9x4, p, false);
		this.setTitle(Component.literal("Nickname"));
		build();
	}

	static void open(ServerPlayer p) {
		new NickGui(p).open();
	}

	private void build() {
		ServerPlayer p = this.getPlayer();
		NicknamesMod.Data d = NicknamesMod.STORE.get(p);
		int unlocked = NicknamesMod.unlocked(p);
		Component preview = d.nick.isEmpty() ? Component.literal(p.getName().getString()).withStyle(ChatFormatting.WHITE) : NicknamesMod.styled(p);

		this.setSlot(4, new GuiElementBuilder(Items.NAME_TAG).glow()
				.setName(Component.empty().append(preview))
				.addLoreLine(Ui.text(d.nick.isEmpty() ? "You don't have a nickname yet." : "Real name: " + p.getName().getString(), ChatFormatting.GRAY)));
		this.setSlot(2, Ui.button(Items.ANVIL, d.nick.isEmpty() ? "Choose nickname" : "Change nickname", ChatFormatting.GREEN)
				.addLoreLine(Ui.text("Or type: /nick <name>", ChatFormatting.DARK_GRAY))
				.setCallback((i, t, a, g) -> {
					Ui.click(p);
					new NameInput(p).open();
				}));
		this.setSlot(6, Ui.button(Items.BARRIER, "Regular name", ChatFormatting.RED)
				.addLoreLine(Ui.text("Remove your nickname.", ChatFormatting.GRAY))
				.setCallback((i, t, a, g) -> {
					NicknamesMod.set(p, "");
					open(p);
				}));

		for (int i = 0; i < NicknamesMod.COLORS.size(); i++) {
			NicknamesMod.Color c = NicknamesMod.COLORS.get(i);
			boolean ok = c.rarity() <= unlocked;
			boolean chosen = c.key().equals(d.color);
			GuiElementBuilder b = new GuiElementBuilder(ok ? Ui.item(c.dye()) : Ui.item("gray_dye"))
					.setName(ok ? NicknamesMod.paint(c.label(), c, false) : Component.literal("✖ " + c.label()).withStyle(ChatFormatting.DARK_GRAY))
					.hideDefaultTooltip();
			if (ok) b.addLoreLine(Ui.text(chosen ? "Selected" : "Click: use this color", chosen ? ChatFormatting.GREEN : ChatFormatting.AQUA));
			else b.addLoreLine(Component.literal("Unlock with " + RARITY[c.rarity()] + " achievement").withStyle(RARITY_COLOR[c.rarity()]));
			if (chosen) b.glow();
			if (ok) b.setCallback((x, t, a, g) -> {
				NicknamesMod.setColor(p, c);
				Ui.click(p);
				open(p);
			});
			this.setSlot(18 + i, b);
		}
		boolean boldOk = unlocked >= NicknamesMod.BOLD_RARITY;
		this.setSlot(33, Ui.button(boldOk ? Items.ANVIL : Items.IRON_BARS, (d.bold ? "✔ " : "") + "Bold",
						boldOk ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY)
				.addLoreLine(boldOk ? Ui.text("Click: on/off", ChatFormatting.AQUA)
						: Component.literal("Unlock with an epic achievement").withStyle(ChatFormatting.LIGHT_PURPLE))
				.setCallback((x, t, a, g) -> {
					if (!boldOk) return;
					NicknamesMod.setBold(p, !d.bold);
					Ui.click(p);
					open(p);
				}));
		this.setSlot(27, Ui.button(Items.ARROW, "Back to Libreria", ChatFormatting.GRAY).setCallback((x, t, a, g) -> HubGui.open(p)));
		Services.menu(Services.MENU_ACHIEVEMENTS).ifPresent(m -> this.setSlot(35,
				Ui.button(Items.NETHER_STAR, "Achievements", ChatFormatting.GOLD)
						.addLoreLine(Ui.text("Earn more to unlock new colors.", ChatFormatting.GRAY))
						.setCallback((x, t, a, g) -> m.accept(p))));
	}

	private static final class NameInput extends AnvilInputGui {
		NameInput(ServerPlayer p) {
			super(p, false);
			NicknamesMod.Data d = NicknamesMod.STORE.get(p);
			this.setTitle(Component.literal("Type your nickname"));
			this.setDefaultInputValue(d.nick.isEmpty() ? p.getName().getString() : d.nick);
			this.setSlot(1, Ui.button(Items.BARRIER, "Cancel", ChatFormatting.RED).setCallback((i, t, a, g) -> NickGui.open(p)));
			this.setSlot(2, Ui.button(Items.NAME_TAG, "Save", ChatFormatting.GREEN).setCallback((i, t, a, g) -> {
				NicknamesMod.set(p, this.getInput());
				NickGui.open(p);
			}));
		}
	}
}
