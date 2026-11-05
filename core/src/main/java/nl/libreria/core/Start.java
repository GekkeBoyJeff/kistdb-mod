package nl.libreria.core;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.dialog.Window;
import nl.libreria.core.gui.SettingsWindow;

/**
 * The start screen and its four domain pages. G (and the Libreria button in the pause menu) opens the start screen
 * straight from the data pack, so it's there instantly; {@code /l} and every "Back" open that very same screen. Its four
 * buttons lead to a page per {@link Domain}: the domain's buttons from every installed module, in a fixed order, and its
 * settings.
 */
public final class Start {
	static final ResourceKey<Dialog> START = ResourceKey.create(Registries.DIALOG, Identifier.fromNamespaceAndPath("libreria", "quick_menu"));

	private Start() {
	}

	/** The start screen: with the pack Libreria's own window, otherwise the same window G opens. */
	public static void open(ServerPlayer p) {
		if (nl.libreria.core.gui.CanvasWindow.available(p)) {
			StartWindow.open(p);
			return;
		}
		var dialogs = p.level().getServer().registryAccess().lookupOrThrow(Registries.DIALOG);
		var start = dialogs.get(START);
		if (start.isPresent()) {
			p.connection.send(new ClientboundShowDialogPacket(start.get()));
			return;
		}
		// Data pack switched off: build the same screen here
		Window w = Window.of(Ui.text("Libreria", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD));
		for (Domain d : Domain.values()) w.button(d.title(), Ui.text(d.blurb, ChatFormatting.GRAY), (pl, v) -> domain(pl, d));
		w.button("Settings", "Your own settings, by the same four places.", SettingsWindow::open);
		w.button("Getting started", "Keys, tips and links.", Welcome::guide);
		w.done().show(p);
	}

	/** One domain page: its buttons, a card for Progress and You, and its settings. */
	public static void domain(ServerPlayer p, Domain d) {
		Window w = Window.of(d);
		switch (d) {
			case YOU -> w.item(head(p), youCard(p), 170);
			case PROGRESS -> Services.progress().map(pr -> pr.card(p)).filter(l -> !l.isEmpty())
					.ifPresentOrElse(lines -> w.item(new ItemStack(Items.NETHER_STAR), join(lines), 190), () -> w.text(d.blurb));
			default -> w.text(d.blurb);
		}
		List<Module.HubEntry> entries = Module.hubEntries(d);
		if (entries.isEmpty()) w.text(Ui.text("Nothing here on this server yet.", ChatFormatting.DARK_GRAY));
		for (Module.HubEntry e : entries) {
			w.button(Ui.text(e.name(), ChatFormatting.WHITE), e.lore().isEmpty() ? null : Ui.text(String.join(" ", e.lore()), ChatFormatting.GRAY),
					(pl, v) -> e.action().accept(pl));
		}
		if (d == Domain.PLACES && Guide.has(p)) {
			w.button(Ui.text("Stop showing the way", ChatFormatting.RED), Ui.text("Removes the beam you're following.", ChatFormatting.GRAY),
					(pl, v) -> {
						Guide.clear(pl);
						Notify.whisper(pl, "Stopped showing the way", ChatFormatting.GRAY);
					});
		}
		if (SettingsWindow.has(d)) {
			w.button(Ui.text("⚙ Settings", ChatFormatting.GRAY), Ui.text("Your settings for " + d.label + ".", ChatFormatting.GRAY),
					(pl, v) -> SettingsWindow.open(pl, d, q -> domain(q, d)));
		}
		w.back(Start::open).show(p);
	}

	private static ItemStack head(ServerPlayer p) {
		return new GuiElementBuilder(Items.PLAYER_HEAD).setProfile(p.getGameProfile()).asStack();
	}

	/** Name, title and playtime. */
	private static Component youCard(ServerPlayer p) {
		MutableComponent c = Component.empty().append(Nameplate.displayName(p).copy().withStyle(s -> s.withBold(true).withItalic(false)));
		Services.progress().map(pr -> pr.title(p)).ifPresent(t -> c.append("\n").append(t));
		int hours = p.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME) / 72000;
		c.append("\n").append(Ui.text(hours + (hours == 1 ? " hour" : " hours") + " on this server", ChatFormatting.GRAY));
		return c;
	}

	private static Component join(List<Component> lines) {
		MutableComponent c = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) c.append("\n");
			c.append(lines.get(i));
		}
		return c;
	}
}
