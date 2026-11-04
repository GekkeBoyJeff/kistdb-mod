package nl.libreria.core.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Items;
import nl.libreria.core.Guide;
import nl.libreria.core.Module;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;

/**
 * The Libreria menu. On top: what you used last, your player card, Getting started, links and (for operators) the
 * server settings. Then one row per category (items, world, progress, you) and Settings / Close / Help at the bottom.
 */
public final class HubGui extends Menu {
	/** The last few buttons a player used, newest first. */
	public static final class Recent {
		public List<String> keys = new ArrayList<>();
	}

	static final nl.libreria.core.PlayerStore<Recent> RECENT = nl.libreria.core.PlayerStore.create("libreria/hub", Recent.class,
			Recent::new, r -> {
				if (r.keys == null) r.keys = new ArrayList<>();
			});
	private static final int RECENT_SHOWN = 3;

	private HubGui(ServerPlayer player) {
		super(player, 6, "Libreria");
		build();
	}

	public static void open(ServerPlayer player) {
		new HubGui(player).open();
	}

	private static String key(Module.HubEntry e) {
		return e.module().id + ":" + e.name();
	}

	private GuiElementBuilder entryButton(Module.HubEntry e) {
		GuiElementBuilder b = button(e.icon(), e.name(), e.color(), e.lore().toArray(String[]::new));
		actions(b, "Click: open");
		return b.setCallback((index, type, action, gui) -> {
			Ui.click(this.player);
			used(this.player, e);
			e.action().accept(this.player);
		});
	}

	/** Remembers that a button was used (it moves to the front of "Recently used"). */
	static void used(ServerPlayer p, Module.HubEntry e) {
		Recent r = RECENT.get(p);
		r.keys.remove(key(e));
		r.keys.addFirst(key(e));
		while (r.keys.size() > 8) r.keys.removeLast();
		RECENT.markDirty(p);
	}

	private List<Module.HubEntry> recent() {
		List<Module.HubEntry> out = new ArrayList<>();
		for (String k : RECENT.get(this.player).keys) {
			for (Module.HubEntry e : Module.allHubEntries()) {
				if (key(e).equals(k) && out.size() < RECENT_SHOWN) out.add(e);
			}
		}
		return out;
	}

	private void build() {
		// Header: your own player card
		GuiElementBuilder head = new GuiElementBuilder(Items.PLAYER_HEAD).setProfile(this.player.getGameProfile())
				.setName(title(this.player.getName().getString(), ChatFormatting.GOLD)).hideDefaultTooltip();
		int hours = this.player.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME) / 72000;
		head.addLoreLine(Ui.kv("Playtime: ", hours + (hours == 1 ? " hour" : " hours"), ChatFormatting.WHITE));
		Services.progress().ifPresent(p -> p.card(this.player).forEach(head::addLoreLine));
		this.setSlot(4, head);

		// Top row, left: what you used last
		List<Module.HubEntry> recent = recent();
		if (!recent.isEmpty()) {
			this.setSlot(0, header("light_gray", "Recently used", ChatFormatting.WHITE));
			for (int i = 0; i < recent.size(); i++) this.setSlot(1 + i, entryButton(recent.get(i)));
		}
		// Top row, right: getting started, links, and for operators the admin window
		this.setSlot(6, actions(button(Items.WRITABLE_BOOK, "Getting started", ChatFormatting.GOLD,
				"What's here and which keys to use."), "Click: open").setCallback((i, t, a, g) -> {
					Ui.click(this.player);
					this.close();
					nl.libreria.core.Welcome.guide(this.player);
				}));
		if (!nl.libreria.core.Links.present().isEmpty()) {
			this.setSlot(7, actions(button(Items.COMPASS, "Links", ChatFormatting.AQUA, "Discord, website, rules…"), "Click: open")
					.setCallback((i, t, a, g) -> {
						Ui.click(this.player);
						this.close();
						nl.libreria.core.Links.open(this.player);
					}));
		}
		if (Ui.isOp(this.player)) {
			this.setSlot(8, actions(button(Items.COMMAND_BLOCK, "Server settings", ChatFormatting.RED,
					"Operators only: house rules for", "everybody, links, leaderboards."), "Click: open").setCallback((i, t, a, g) -> {
						Ui.click(this.player);
						this.close();
						AdminWindow.open(this.player);
					}));
		}

		// One row per category
		Module.Category[] cats = Module.Category.values();
		int row = 1;
		for (Module.Category cat : cats) {
			List<Module.HubEntry> entries = new ArrayList<>();
			for (Module.HubEntry e : Module.allHubEntries()) if (e.category() == cat) entries.add(e);
			if (entries.isEmpty() || row > 4) continue;
			this.setSlot(row * 9, header(cat.pane, cat.label, cat.color));
			int[] slots = row(row, entries.size());
			for (int i = 0; i < slots.length; i++) this.setSlot(slots[i], entryButton(entries.get(i)));
			row++;
		}

		// Bottom bar
		if (Guide.has(this.player)) {
			this.setSlot(navRow(), action(button(Items.BARRIER, "Stop beam", ChatFormatting.RED, "Removes the beam you are following."),
					"Click: stop").setCallback((i, t, a, g) -> {
						Guide.clear(this.player);
						Ui.click(this.player);
						this.close();
					}));
		}
		this.setSlot(navRow() + 2, actions(button(Items.COMPARATOR, "Settings", ChatFormatting.WHITE,
				"Turn features on or off and", "tweak them, per module.", "", "Also: /libreria settings"), "Click: open")
				.setCallback((i, t, a, g) -> {
					Ui.click(this.player);
					SettingsWindow.open(this.player);
				}));
		closeButton();
		List<String> help = new ArrayList<>(List.of(
				"Keys:",
				"  G – quick menu (also in the pause menu)",
				"  E, then the magnifier – your items",
				"  E, then click beside it – sort",
				"  Sneak + F – land map",
				"",
				"Commands:",
				"  /libreria (or /lib) – this menu",
				"  /libreria settings – settings",
				"  /libreria start – getting started"));
		for (Module m : Module.all()) for (String c : m.commandHelp()) help.add("  " + c);
		help(help.toArray(String[]::new));
		finish();
	}
}
