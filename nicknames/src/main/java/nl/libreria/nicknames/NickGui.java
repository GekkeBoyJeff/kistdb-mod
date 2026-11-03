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
	private static final String[] RARITY = {"gewone", "ongewone", "zeldzame", "epische", "legendarische", "mythische"};
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
				.addLoreLine(Ui.text(d.nick.isEmpty() ? "Je hebt nog geen nickname." : "Echte naam: " + p.getName().getString(), ChatFormatting.GRAY)));
		this.setSlot(2, Ui.button(Items.ANVIL, d.nick.isEmpty() ? "Nickname kiezen" : "Nickname wijzigen", ChatFormatting.GREEN)
				.addLoreLine(Ui.text("Of typ: /nick <naam>", ChatFormatting.DARK_GRAY))
				.setCallback((i, t, a, g) -> {
					Ui.click(p);
					new NameInput(p).open();
				}));
		this.setSlot(6, Ui.button(Items.BARRIER, "Gewone naam", ChatFormatting.RED)
				.addLoreLine(Ui.text("Nickname weghalen.", ChatFormatting.GRAY))
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
			if (ok) b.addLoreLine(Ui.text(chosen ? "Gekozen" : "Klik: deze kleur", chosen ? ChatFormatting.GREEN : ChatFormatting.AQUA));
			else b.addLoreLine(Component.literal("Ontgrendel met een " + RARITY[c.rarity()] + " prestatie").withStyle(RARITY_COLOR[c.rarity()]));
			if (chosen) b.glow();
			if (ok) b.setCallback((x, t, a, g) -> {
				NicknamesMod.setColor(p, c);
				Ui.click(p);
				open(p);
			});
			this.setSlot(18 + i, b);
		}
		boolean boldOk = unlocked >= NicknamesMod.BOLD_RARITY;
		this.setSlot(33, Ui.button(boldOk ? Items.ANVIL : Items.IRON_BARS, (d.bold ? "✔ " : "") + "Vetgedrukt",
						boldOk ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY)
				.addLoreLine(boldOk ? Ui.text("Klik: aan/uit", ChatFormatting.AQUA)
						: Component.literal("Ontgrendel met een epische prestatie").withStyle(ChatFormatting.LIGHT_PURPLE))
				.setCallback((x, t, a, g) -> {
					if (!boldOk) return;
					NicknamesMod.setBold(p, !d.bold);
					Ui.click(p);
					open(p);
				}));
		this.setSlot(27, Ui.button(Items.ARROW, "Terug naar Libreria", ChatFormatting.GRAY).setCallback((x, t, a, g) -> HubGui.open(p)));
		Services.menu(Services.MENU_ACHIEVEMENTS).ifPresent(m -> this.setSlot(35,
				Ui.button(Items.NETHER_STAR, "Prestaties", ChatFormatting.GOLD)
						.addLoreLine(Ui.text("Haal er meer voor nieuwe kleuren.", ChatFormatting.GRAY))
						.setCallback((x, t, a, g) -> m.accept(p))));
	}

	private static final class NameInput extends AnvilInputGui {
		NameInput(ServerPlayer p) {
			super(p, false);
			NicknamesMod.Data d = NicknamesMod.STORE.get(p);
			this.setTitle(Component.literal("Typ je nickname"));
			this.setDefaultInputValue(d.nick.isEmpty() ? p.getName().getString() : d.nick);
			this.setSlot(1, Ui.button(Items.BARRIER, "Annuleren", ChatFormatting.RED).setCallback((i, t, a, g) -> NickGui.open(p)));
			this.setSlot(2, Ui.button(Items.NAME_TAG, "Opslaan", ChatFormatting.GREEN).setCallback((i, t, a, g) -> {
				NicknamesMod.set(p, this.getInput());
				NickGui.open(p);
			}));
		}
	}
}
