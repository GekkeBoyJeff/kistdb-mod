package nl.libreria.core.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.Domain;
import nl.libreria.core.Keys;
import nl.libreria.core.Module;
import nl.libreria.core.Notify;
import nl.libreria.core.Setting;
import nl.libreria.core.Start;
import nl.libreria.core.Ui;

/**
 * Settings as a window of our own. The first page lists the four places (and, for operators, the server settings);
 * a place's page lists its groups with their settings: a switch you flip with a click, a choice you click through
 * (right-click goes back), and buttons to the screens that go with a group. A change counts at once; the tooltip of
 * each row says what it does.
 */
public final class SettingsCanvas extends CanvasWindow {
	private static final Identifier SWITCH_ON = Identifier.fromNamespaceAndPath("libreria", "gui/switch_on");
	private static final Identifier SWITCH_OFF = Identifier.fromNamespaceAndPath("libreria", "gui/switch_off");
	private static final int TEXT = 0xFFFFFF, MUTED = 0xB9AD95, VALUE = 0x8FE3F0, NEUTRAL = 0xC8A060;
	/** Rows for the list (the hotbar row is for Back and the page buttons). */
	private static final int LIST_ROWS = 9;

	/** null: the first page (the four places). */
	private final Domain domain;
	private final Consumer<ServerPlayer> back;
	private int page;

	private SettingsCanvas(ServerPlayer p, Domain domain, Consumer<ServerPlayer> back, int page) {
		super(p);
		this.domain = domain;
		this.back = back;
		this.page = page;
	}

	/** Settings: the four places. */
	public static void open(ServerPlayer p) {
		new SettingsCanvas(p, null, Start::open, 0).show();
	}

	/** One place's settings; Back runs {@code back}. */
	public static void open(ServerPlayer p, Domain d, Consumer<ServerPlayer> back) {
		new SettingsCanvas(p, d, back, 0).show();
	}

	// ---------------------------------------------------------------- what's on the page

	private sealed interface Row permits Header, Toggle, Choice, Link {
	}

	private record Header(String name) implements Row {
	}

	private record Toggle(Setting.Toggle setting) implements Row {
	}

	private record Choice(Setting.Choice setting) implements Row {
	}

	private record Link(ItemStack icon, String label, String about, int tint, Consumer<ServerPlayer> action) implements Row {
	}

	private List<Row> allRows() {
		List<Row> out = new ArrayList<>();
		if (this.domain == null) {
			for (Domain d : Domain.values()) {
				List<SettingsWindow.Section> sections = SettingsWindow.sections(d);
				if (sections.isEmpty()) continue;
				String about = String.join(" · ", sections.stream().map(SettingsWindow.Section::name).toList());
				out.add(new Link(new ItemStack(d.icon), d.label, about, d.rgb, p -> open(p, d, SettingsCanvas::open)));
			}
			if (Ui.isOp(this.player)) {
				out.add(new Link(new ItemStack(Items.COMMAND_BLOCK), "Server settings", "Operators: house rules for everybody, links, leaderboards.",
						0xFFAA33, AdminWindow::open));
			}
			return out;
		}
		for (SettingsWindow.Section s : SettingsWindow.sections(this.domain)) {
			out.add(new Header(s.name()));
			for (Setting x : s.settings()) {
				if (x instanceof Setting.Toggle t) out.add(new Toggle(t));
				else if (x instanceof Setting.Choice c) out.add(new Choice(c));
			}
			for (Module.SettingsButton b : s.buttons()) {
				out.add(new Link(new ItemStack(Items.COMPARATOR), b.label(), b.tooltip(), NEUTRAL, b.action()));
			}
		}
		return out;
	}

	/** The rows of this page (a header never ends a page). */
	private List<List<Row>> pages() {
		List<List<Row>> pages = new ArrayList<>();
		List<Row> cur = new ArrayList<>();
		for (Row r : allRows()) {
			if (cur.size() == LIST_ROWS || (r instanceof Header && cur.size() == LIST_ROWS - 1)) {
				pages.add(cur);
				cur = new ArrayList<>();
			}
			cur.add(r);
		}
		if (!cur.isEmpty() || pages.isEmpty()) pages.add(cur);
		return pages;
	}

	private List<Row> rows() {
		List<List<Row>> pages = pages();
		this.page = Math.max(0, Math.min(this.page, pages.size() - 1));
		return pages.get(this.page);
	}

	// ---------------------------------------------------------------- drawing

	@Override
	protected Canvas draw() {
		Canvas c = new Canvas().panel();
		int tint = this.domain == null ? NEUTRAL : this.domain.rgb;
		if (this.domain == null) {
			c.text("Libreria", 8, 5, MUTED);
			int x = 8 + Glyphs.width(Component.literal("Libreria"));
			c.text(" › ", x, 5, MUTED).text("Settings", x + Glyphs.width(Component.literal(" › ")), 5, TEXT);
		} else {
			c.text(this.domain.label, 8, 5, this.domain.rgb);
			int x = 8 + Glyphs.width(Component.literal(this.domain.label));
			c.text(" › ", x, 5, MUTED).text("Settings", x + Glyphs.width(Component.literal(" › ")), 5, TEXT);
		}
		List<Row> rows = rows();
		int left = Canvas.cellX(1) + Canvas.CARD_MARGIN + 5, right = Canvas.cellX(8) - Canvas.CARD_MARGIN - 5;
		for (int i = 0; i < rows.size(); i++) {
			int y = Canvas.cellY(i) + 5;
			switch (rows.get(i)) {
				case Header h -> c.text(h.name(), Canvas.cellX(0) + 3, y, tint);
				case Toggle t -> {
					c.card(1, i, 7, 1, tint);
					c.text(Canvas.fit(Keys.plain(t.setting().label), right - left), left, y, TEXT);
				}
				case Choice ch -> {
					c.card(1, i, 7, 1, tint);
					String raw = ch.setting().labelAt(ch.setting().index(this.player));
					// A value with keys ("{sneak} + {swap}") shows the player's own keys
					int vw = Glyphs.width(Keys.has(raw) ? Keys.text(raw) : Component.literal(raw));
					c.text(Canvas.fit(Keys.plain(ch.setting().label), right - left - vw - 8), left, y, TEXT);
					if (Keys.has(raw)) c.keys(raw, right - vw, y, VALUE);
					else c.text(raw, right - vw, y, VALUE);
					c.text("‹›", Canvas.cellX(8) + 4, y, MUTED);
				}
				case Link l -> {
					c.card(1, i, 7, 1, l.tint());
					c.text(Canvas.fit(l.label(), right - left), left, y, TEXT);
					c.text("›", Canvas.cellX(8) + 7, y, MUTED);
				}
			}
		}
		int pages = pages().size();
		if (pages > 1) {
			small(c, 0, 3, "Back");
			small(c, 3, 3, this.page > 0 ? "‹ Page " + this.page : "");
			small(c, 6, 3, this.page < pages - 1 ? "Page " + (this.page + 2) + " ›" : "");
		} else {
			small(c, 0, this.domain == null ? 9 : 4, "Back");
			if (this.domain != null) small(c, 4, 5, "Defaults");
		}
		return c;
	}

	private static void small(Canvas c, int col, int w, String label) {
		if (label.isEmpty()) return;
		c.card(col, 9, w, 1, NEUTRAL);
		c.centred(label, Canvas.cellX(col) + 9 * w, Canvas.cellY(9) + 5, TEXT);
	}

	// ---------------------------------------------------------------- cells

	@Override
	protected void fill() {
		List<Row> rows = rows();
		for (int i = 0; i < rows.size(); i++) {
			int row = i;
			switch (rows.get(i)) {
				case Header h -> {
				}
				case Toggle t -> {
					Setting.Toggle s = t.setting();
					boolean on = s.get(this.player);
					Component name = Ui.text(s.label, ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
					List<Component> lore = lore(s.lore, on ? "On" : "Off", "Click: turn " + (on ? "off" : "on"));
					Consumer<ServerPlayer> flip = p -> {
						s.set(p, !s.get(p));
						Notify.whisper(p, Ui.text(s.label + ": " + (s.get(p) ? "on" : "off"), ChatFormatting.GRAY));
						refreshCells();
					};
					area(1, row, 7, 1, name, lore, flip);
					icon(0, row, new ItemStack(s.icon), name, lore, flip);
					ItemStack sw = new ItemStack(Items.PAPER);
					sw.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, on ? SWITCH_ON : SWITCH_OFF);
					icon(8, row, sw, name, lore, flip);
				}
				case Choice ch -> {
					Setting.Choice s = ch.setting();
					Component name = Ui.text(s.label, ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
					List<Component> lore = new ArrayList<>(lore(s.lore, null));
					lore.add(Component.empty());
					for (int k = 0; k < s.size(); k++) {
						boolean sel = k == s.index(this.player);
						lore.add(Ui.text((sel ? "▶ " : "   ") + s.labelAt(k), sel ? ChatFormatting.AQUA : ChatFormatting.GRAY));
					}
					lore.add(Component.empty());
					lore.add(Ui.text("▸ Click: next   Right-click: back", ChatFormatting.YELLOW));
					areaClick(1, row, 8, 1, name, lore, (p, rightClick) -> {
						s.cycle(p, rightClick ? -1 : 1);
						Notify.whisper(p, Ui.text(s.label + ": " + s.labelAt(s.index(p)), ChatFormatting.GRAY));
						redraw();
					});
					icon(0, row, new ItemStack(s.icon), name, lore, p -> {
						s.cycle(p, 1);
						redraw();
					});
				}
				case Link l -> {
					Component name = Ui.text(l.label(), l.tint()).withStyle(ChatFormatting.BOLD);
					List<Component> lore = lore(List.of(l.about()), null, "Click: open");
					area(1, row, 8, 1, name, lore, l.action());
					icon(0, row, l.icon(), name, lore, l.action());
				}
			}
		}
		int pages = pages().size();
		Component backName = Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
		if (pages > 1) {
			area(0, 9, 3, 1, backName, List.of(), this.back);
			if (this.page > 0) area(3, 9, 3, 1, Ui.text("Previous page", ChatFormatting.WHITE), List.of(), p -> turn(-1));
			if (this.page < pages - 1) area(6, 9, 3, 1, Ui.text("Next page", ChatFormatting.WHITE), List.of(), p -> turn(1));
		} else {
			area(0, 9, this.domain == null ? 9 : 4, 1, backName, List.of(), this.back);
			if (this.domain != null) {
				area(4, 9, 5, 1, Ui.text("Defaults", ChatFormatting.YELLOW).withStyle(ChatFormatting.BOLD),
						List.of(Ui.text("Every setting here back to how it was at the start.", ChatFormatting.GRAY)), p -> {
							for (SettingsWindow.Section s : SettingsWindow.sections(this.domain)) {
								for (Setting x : s.settings()) {
									if (x instanceof Setting.Toggle t) t.set(p, t.def);
									else if (x instanceof Setting.Choice ch) ch.set(p, ch.defaultValue());
								}
							}
							Notify.whisper(p, "Back to the defaults", ChatFormatting.GRAY);
							redraw();
						});
			}
		}
	}

	private void turn(int step) {
		this.page += step;
		redraw();
	}

	/** Only the items changed (a switch): no new picture, so the window doesn't blink. */
	private void refreshCells() {
		for (int i = 0; i < 90; i++) this.clearSlot(i);
		fill();
	}

	/** Text changed too (a choice, a page): new picture. */
	private void redraw() {
		refresh();
	}

	private static List<Component> lore(List<String> about, String state, String... actions) {
		List<Component> out = new ArrayList<>();
		if (state != null) out.add(Ui.text("Now: " + state, ChatFormatting.AQUA));
		for (String l : Canvas.wrap(String.join(" ", about), 180)) out.add(Ui.text(l, ChatFormatting.GRAY));
		if (actions.length > 0) out.add(Component.empty());
		for (String a : actions) out.add(Ui.text("▸ " + a, ChatFormatting.YELLOW));
		return out;
	}
}
