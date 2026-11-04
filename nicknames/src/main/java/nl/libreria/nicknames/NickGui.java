package nl.libreria.nicknames;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Menu;

/**
 * Pick a nickname: a preview of how you look on top, ✎ to type a name in an anvil, then a palette of colors (unlocked
 * ones as dyes, locked ones grey) and a Bold toggle with its lamp.
 */
final class NickGui extends Menu {
	/** With article, for "Unlock with a rare achievement". */
	private static final String[] RARITY = {"a common", "an uncommon", "a rare", "an epic", "a legendary", "a mythic"};
	private static final ChatFormatting[] RARITY_COLOR = {ChatFormatting.WHITE, ChatFormatting.GREEN, ChatFormatting.AQUA,
			ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD, ChatFormatting.RED};

	private NickGui(ServerPlayer p) {
		super(p, 6, "Nickname");
		build();
	}

	static void open(ServerPlayer p) {
		new NickGui(p).open();
	}

	private static void deny(ServerPlayer p) {
		Ui.sound(p, SoundEvents.NOTE_BLOCK_BASS, 0.5f, 0.7f);
	}

	private static Component unlockLine(int rarity) {
		return Ui.text("Unlock with " + RARITY[rarity] + " achievement", RARITY_COLOR[rarity]);
	}

	/** Rebuilds the whole menu in place (after every change, so the preview is always right). */
	private void build() {
		clearContent();
		ServerPlayer p = this.player;
		NicknamesMod.Data d = NicknamesMod.STORE.get(p);
		int unlocked = NicknamesMod.unlocked(p);
		String real = p.getName().getString();
		boolean hasNick = !d.nick.isEmpty();
		NicknamesMod.Color current = NicknamesMod.colorOf(p, d);
		boolean boldOk = unlocked >= NicknamesMod.BOLD_RARITY;
		boolean bold = d.bold && boldOk;
		// The text the color and bold previews use
		String sample = hasNick ? d.nick : real;
		boolean achievements = Services.menu(Services.MENU_ACHIEVEMENTS).isPresent();

		// ---- Top: the big preview, with "change" and "real name" on either side
		MutableComponent look = hasNick ? NicknamesMod.styled(p) : Ui.text(real, ChatFormatting.WHITE);
		GuiElementBuilder preview = new GuiElementBuilder(Items.NAME_TAG).glow().hideDefaultTooltip()
				.setName(Component.empty().append(look));
		if (hasNick) {
			preview.addLoreLine(Ui.text("This is how everyone sees you.", ChatFormatting.GRAY));
			preview.addLoreLine(Component.empty());
			preview.addLoreLine(Ui.text("Tab list: ", ChatFormatting.GRAY).append(NicknamesMod.tabName(p)));
			preview.addLoreLine(Ui.kv("Real name: ", real, ChatFormatting.WHITE));
			preview.addLoreLine(Ui.text("Color: ", ChatFormatting.GRAY).append(NicknamesMod.paint(current.label(), current, false)));
			preview.addLoreLine(Ui.kv("Bold: ", bold ? "On" : "Off", bold ? ChatFormatting.GREEN : ChatFormatting.RED));
			actions(preview, "Click: change your nickname");
		} else {
			preview.addLoreLine(Ui.text("You don't have a nickname yet,", ChatFormatting.GRAY));
			preview.addLoreLine(Ui.text("so everyone sees your real name.", ChatFormatting.GRAY));
			actions(preview, "Click: choose a nickname");
		}
		preview.setCallback((i, t, a, g) -> {
			Ui.click(p);
			new NameInput(p).open();
		});
		this.setSlot(4, preview);

		GuiElementBuilder change = button(Items.ANVIL, hasNick ? "✎ Change nickname" : "✎ Choose a nickname", ChatFormatting.GREEN,
				"Type a name in an anvil screen:", "2–16 letters, numbers, spaces", "and _ . - '");
		change.addLoreLine(Component.empty());
		change.addLoreLine(Ui.text("Or type: /nick <name>", ChatFormatting.DARK_GRAY));
		action(change, "Click: type a name");
		change.setCallback((i, t, a, g) -> {
			Ui.click(p);
			new NameInput(p).open();
		});
		this.setSlot(2, change);

		GuiElementBuilder realName = new GuiElementBuilder(Items.PLAYER_HEAD).setProfile(p.getGameProfile()).hideDefaultTooltip()
				.setName(title("Use my real name", hasNick ? ChatFormatting.RED : ChatFormatting.GRAY));
		if (hasNick) {
			realName.addLoreLine(Ui.text("Removes your nickname, so", ChatFormatting.GRAY));
			realName.addLoreLine(Ui.text("everyone sees " + real + " again.", ChatFormatting.GRAY));
			realName.addLoreLine(Component.empty());
			realName.addLoreLine(Ui.text("Or type: /nick reset", ChatFormatting.DARK_GRAY));
			action(realName, "Click: remove nickname");
			realName.setCallback((i, t, a, g) -> {
				Ui.click(p);
				NicknamesMod.set(p, "");
				build();
			});
		} else {
			realName.addLoreLine(Ui.text("✔ You're using your real name.", ChatFormatting.GREEN));
		}
		this.setSlot(6, realName);

		// ---- Colors: two centred rows, labelled on both sides
		List<NicknamesMod.Color> colors = NicknamesMod.COLORS;
		int first = Math.min(7, colors.size());
		int[] top = row(1, first);
		int[] second = row(2, Math.max(0, Math.min(7, colors.size() - first)));
		for (int r = 1; r <= (second.length > 0 ? 2 : 1); r++) {
			for (int col : new int[] {0, 8}) {
				GuiElementBuilder h = header("magenta", "Colors", ChatFormatting.LIGHT_PURPLE);
				h.addLoreLine(Ui.text("Colors and bold only show", ChatFormatting.GRAY));
				h.addLoreLine(Ui.text("on your nickname.", ChatFormatting.GRAY));
				this.setSlot(r * 9 + col, h);
			}
		}
		for (int i = 0; i < colors.size() && i < 14; i++) {
			NicknamesMod.Color c = colors.get(i);
			this.setSlot(i < 7 ? top[i] : second[i - 7], swatch(c, c.rarity() <= unlocked, c == current, sample, bold));
		}

		// ---- Style: Bold (lamp below) and, when there are achievements, how to unlock more
		int boldSlot = achievements ? 30 : 31;
		if (boldOk) {
			GuiElementBuilder b = button(Ui.item(bold ? "glow_ink_sac" : "ink_sac"), "Bold", ChatFormatting.WHITE,
					"Makes your nickname bold.");
			b.addLoreLine(Ui.text("Preview: ", ChatFormatting.GRAY).append(NicknamesMod.paint(sample, current, true)));
			b.addLoreLine(Component.empty());
			b.addLoreLine(Ui.text(bold ? "✔ On" : "✘ Off", bold ? ChatFormatting.GREEN : ChatFormatting.RED));
			action(b, "Click: turn " + (bold ? "off" : "on"));
			if (bold) b.glow();
			Runnable flip = () -> {
				NicknamesMod.setBold(p, !bold);
				Ui.sound(p, SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, bold ? 0.7f : 1.4f);
				build();
			};
			b.setCallback((i, t, a, g) -> flip.run());
			this.setSlot(boldSlot, b);
			this.setSlot(boldSlot + 9, lamp(bold).setCallback((i, t, a, g) -> flip.run()));
		} else {
			GuiElementBuilder b = button(Items.IRON_BARS, "✖ Bold", ChatFormatting.DARK_GRAY, "Makes your nickname bold.");
			b.addLoreLine(Component.empty());
			b.addLoreLine(Ui.text("Locked", ChatFormatting.RED));
			b.addLoreLine(unlockLine(NicknamesMod.BOLD_RARITY));
			b.setCallback((i, t, a, g) -> deny(p));
			this.setSlot(boldSlot, b);
			this.setSlot(boldSlot + 9, new GuiElementBuilder(Ui.item("light_gray_stained_glass_pane"))
					.setName(Ui.text("✖ Locked", ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.BOLD))
					.addLoreLine(unlockLine(NicknamesMod.BOLD_RARITY)).hideDefaultTooltip()
					.setCallback((i, t, a, g) -> deny(p)));
		}

		Services.menu(Services.MENU_ACHIEVEMENTS).ifPresent(openAchievements -> {
			long open = colors.stream().filter(c -> c.rarity() <= unlocked).count();
			GuiElementBuilder star = button(Items.NETHER_STAR, "Achievements", ChatFormatting.GOLD,
					"Earn achievements to unlock", "more colors. The rarer the", "achievement, the more colors.");
			star.addLoreLine(Component.empty());
			star.addLoreLine(Ui.kv("Colors unlocked: ", open + " of " + colors.size(), ChatFormatting.WHITE));
			star.addLoreLine(Ui.kv("Bold: ", boldOk ? "unlocked" : "locked", boldOk ? ChatFormatting.GREEN : ChatFormatting.RED));
			action(star, "Click: open Achievements");
			star.glow().setCallback((i, t, a, g) -> {
				Ui.click(p);
				openAchievements.accept(p);
			});
			this.setSlot(32, star);
		});

		// ---- Navigation bar
		up();
		closeButton();
		List<String> help = new ArrayList<>(List.of(
				"Your nickname shows above your head,",
				"in the tab list and in chat.",
				"Your real name shows in the tab list",
				"and when someone hovers it in chat."));
		if (achievements) help.add("Rarer achievements unlock colors.");
		help.add("Commands: /nick <name>, /nick reset");
		help(help.toArray(String[]::new));
		finish();
	}

	/** One color of the palette: its dye when unlocked (glowing when selected), a grey dye when locked. */
	private GuiElementBuilder swatch(NicknamesMod.Color c, boolean ok, boolean chosen, String sample, boolean bold) {
		ServerPlayer p = this.player;
		if (!ok) {
			GuiElementBuilder b = new GuiElementBuilder(Ui.item("gray_dye")).hideDefaultTooltip()
					.setName(Ui.text("✖ " + c.label(), ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.BOLD));
			b.addLoreLine(Ui.text("Locked", ChatFormatting.RED));
			b.addLoreLine(unlockLine(c.rarity()));
			b.addLoreLine(Component.empty());
			b.addLoreLine(Ui.text("Preview: ", ChatFormatting.DARK_GRAY).append(NicknamesMod.paint(sample, c, bold)));
			b.setCallback((i, t, a, g) -> deny(p));
			return b;
		}
		GuiElementBuilder b = new GuiElementBuilder(Ui.item(c.dye())).hideDefaultTooltip()
				.setName(Component.empty().append(NicknamesMod.paint(c.label(), c, true)));
		b.addLoreLine(Ui.text("Preview: ", ChatFormatting.GRAY).append(NicknamesMod.paint(sample, c, bold)));
		if (chosen) {
			b.addLoreLine(Component.empty());
			b.addLoreLine(Ui.text("✔ Selected", ChatFormatting.GREEN));
			b.glow();
			b.setCallback((i, t, a, g) -> Ui.click(p));
		} else {
			actions(b, "Click: use this color");
			b.setCallback((i, t, a, g) -> {
				NicknamesMod.setColor(p, c);
				Ui.click(p);
				build();
			});
		}
		return b;
	}

	/**
	 * Anvil screen to type a name (anvil screens can't use {@link Menu}). Left: the text field, middle: ◀ Cancel,
	 * right: ✔ Save, which shows live how the name will look, or why it isn't allowed.
	 */
	private static final class NameInput extends AnvilInputGui {
		NameInput(ServerPlayer p) {
			super(p, false);
			NicknamesMod.Data d = NicknamesMod.STORE.get(p);
			this.setTitle(Component.literal("✎ Type your nickname"));
			this.setDefaultInputValue(d.nick.isEmpty() ? p.getName().getString() : d.nick);
			this.setSlot(1, Menu.action(Menu.button(Items.ARROW, "◀ Cancel", ChatFormatting.WHITE, "Keep your current name."), "Click: go back")
					.setCallback((i, t, a, g) -> {
						Ui.click(p);
						NickGui.open(p);
					}));
			updateSave(this.getInput());
		}

		@Override
		public void onInput(String input) {
			updateSave(input);
		}

		private void updateSave(String input) {
			ServerPlayer p = this.getPlayer();
			String why = NicknamesMod.validate(p, input);
			if (why != null) {
				this.setSlot(2, Menu.button(Items.BARRIER, "✖ Can't use this name", ChatFormatting.RED, why, "",
						"Allowed: 2–16 letters, numbers,", "spaces and _ . - '")
						.setCallback((i, t, a, g) -> deny(p)));
				return;
			}
			NicknamesMod.Data d = NicknamesMod.STORE.get(p);
			boolean bold = d.bold && NicknamesMod.unlocked(p) >= NicknamesMod.BOLD_RARITY;
			GuiElementBuilder save = Menu.button(Items.NAME_TAG, "✔ Save", ChatFormatting.GREEN, "You'll be known as:");
			save.addLoreLine(NicknamesMod.paint(input.strip(), NicknamesMod.colorOf(p, d), bold));
			Menu.actions(save, "Click: save");
			this.setSlot(2, save.setCallback((i, t, a, g) -> {
				Ui.click(p);
				NicknamesMod.set(p, this.getInput());
				NickGui.open(p);
			}));
		}
	}
}
