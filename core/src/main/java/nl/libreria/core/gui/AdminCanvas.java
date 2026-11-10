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
import nl.libreria.core.Libreria;
import nl.libreria.core.Links;
import nl.libreria.core.Module;
import nl.libreria.core.Notify;
import nl.libreria.core.ServerSetting;
import nl.libreria.core.Setting;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Window;
import org.jetbrains.annotations.Nullable;

/**
 * The server settings (operators) as a window of our own, in the same look as a player's settings. The first page:
 * every part of Libreria as a card (grey when it's turned off). A part's page: its house rules (a switch you flip, a
 * choice or a number you click through, a text you change in a small window) and the players' own settings (each
 * player chooses, starting at…, or one value for everybody), then the part's own screens. A change counts at once and
 * is logged; the tooltip of each row says what it does.
 */
public final class AdminCanvas extends CanvasWindow {
	private static final Identifier SWITCH_ON = Identifier.fromNamespaceAndPath("libreria", "gui/switch_on");
	private static final Identifier SWITCH_OFF = Identifier.fromNamespaceAndPath("libreria", "gui/switch_off");
	private static final int TEXT = 0xFFFFFF, MUTED = Glyphs.muted, VALUE = 0x8FE3F0, NEUTRAL = Glyphs.neutral, OFF = 0x5A5E66;
	/** The colour of the server settings (as on their card in Settings). */
	public static final int TINT = 0xFFAA33;
	private static final int LIST_ROWS = 9;
	private static final int ROOM = 18 * 8 - 2 * Canvas.CARD_MARGIN - 12;
	private static final int ROOM_FULL = 18 * 9 - 2 * Canvas.CARD_MARGIN - 12;

	/** null: the first page (every part). */
	private final @Nullable Module module;
	private int page;

	private AdminCanvas(ServerPlayer p, @Nullable Module module, int page) {
		super(p);
		this.module = module;
		this.page = page;
	}

	public static void open(ServerPlayer p) {
		new AdminCanvas(p, null, 0).show();
	}

	public static void open(ServerPlayer p, Module module) {
		new AdminCanvas(p, module, 0).show();
	}

	/** The parts that have something to set. */
	static List<Module> modules() {
		List<Module> out = new ArrayList<>();
		for (Module m : Module.all()) {
			if (m.serverSettings().isEmpty() && m.adminButtons().isEmpty() && m.settings().isEmpty()) continue;
			out.add(m);
		}
		return out;
	}

	private static int tint(Module m) {
		Domain d = m.domain();
		return d == null ? TINT : d.rgb;
	}

	// ---------------------------------------------------------------- a part's rows

	private sealed interface Row permits Header, Flag, Pick, Count, Words, Own, Link {
	}

	private record Header(String name) implements Row {
	}

	private record Flag(ServerSetting.Toggle s) implements Row {
	}

	private record Pick(ServerSetting.Choice s) implements Row {
	}

	private record Count(ServerSetting.Number s) implements Row {
	}

	private record Words(ServerSetting.Text s) implements Row {
	}

	/** A player's own setting: how the house handles it. */
	private record Own(Setting s) implements Row {
	}

	private record Link(String label, String about, Consumer<ServerPlayer> action) implements Row {
	}

	private List<Row> allRows() {
		List<Row> out = new ArrayList<>();
		Module m = this.module;
		if (m == null) return out;
		List<ServerSetting> rules = m.serverSettings();
		if (!rules.isEmpty()) out.add(new Header("House rules"));
		for (ServerSetting s : rules) {
			switch (s) {
				case ServerSetting.Toggle t -> out.add(new Flag(t));
				case ServerSetting.Choice c -> out.add(new Pick(c));
				case ServerSetting.Number n -> out.add(new Count(n));
				case ServerSetting.Text t -> out.add(new Words(t));
			}
		}
		if (!m.settings().isEmpty()) {
			out.add(new Header("Players' own settings"));
			for (Setting s : m.settings()) out.add(new Own(s));
		}
		if (!m.adminButtons().isEmpty()) {
			out.add(new Header("More"));
			for (Module.SettingsButton b : m.adminButtons()) out.add(new Link(b.label(), b.tooltip(), b.action()));
		}
		return out;
	}

	private static int height(Row r) {
		return switch (r) {
			case Header h -> 1;
			case Flag f -> Glyphs.width(Component.literal(f.s().label)) > ROOM ? 2 : 1;
			case Pick p -> 2;
			case Count c -> 2;
			case Words w -> 2;
			case Own o -> 2;
			case Link l -> Glyphs.width(Component.literal(l.label())) > ROOM_FULL - 10 ? 2 : 1;
		};
	}

	private record Placed(Row row, int at, int height) {
	}

	/** Pages of rows 0–8, as in a player's settings: no card across the gap, no page that ends with a header. */
	private List<List<Placed>> pages() {
		List<List<Placed>> pages = new ArrayList<>();
		List<Placed> cur = new ArrayList<>();
		int at = 0;
		List<Row> all = allRows();
		for (int i = 0; i < all.size(); i++) {
			Row r = all.get(i);
			int h = height(r);
			if (h == 2 && at == 5) at = 6;
			if (r instanceof Header && i + 1 < all.size() && height(all.get(i + 1)) == 2 && at == 4) at = 6;
			if (r instanceof Header && at == 5) at = 6;
			int need = h + (r instanceof Header && i + 1 < all.size() ? height(all.get(i + 1)) : 0);
			if (at + need > LIST_ROWS && !cur.isEmpty()) {
				pages.add(cur);
				cur = new ArrayList<>();
				at = 0;
			}
			cur.add(new Placed(r, at, h));
			at += h;
		}
		if (!cur.isEmpty() || pages.isEmpty()) pages.add(cur);
		return pages;
	}

	private List<Placed> rows() {
		List<List<Placed>> pages = pages();
		this.page = Math.max(0, Math.min(this.page, pages.size() - 1));
		return pages.get(this.page);
	}

	/** The parts on the first page: two cards a row, rows 0–8. */
	private static final int[] MODULE_ROWS = {0, 1, 2, 3, 4, 5, 6, 7, 8};

	private int modulePages() {
		int per = MODULE_ROWS.length * 2;
		return Math.max(1, (modules().size() + per - 1) / per);
	}

	// ---------------------------------------------------------------- what players' settings say

	/** The house's handling of a player's own setting, as one of its options (0..), and the options' names. */
	private static List<String> ownOptions(Setting s) {
		List<String> out = new ArrayList<>();
		switch (s) {
			case Setting.Toggle t -> out.addAll(List.of("Each player · on at first", "Each player · off at first", "On for everybody", "Off for everybody"));
			case Setting.Choice c -> {
				for (int i = 0; i < c.size(); i++) out.add("Each player · " + c.labelAt(i) + " at first");
				for (int i = 0; i < c.size(); i++) out.add(c.labelAt(i) + " for everybody");
			}
		}
		return out;
	}

	private static int ownIndex(Setting s) {
		Integer f = s.forced();
		return switch (s) {
			case Setting.Toggle t -> f != null ? (f != 0 ? 2 : 3) : (t.start() ? 0 : 1);
			case Setting.Choice c -> {
				int n = c.size();
				int want = f != null ? f : c.start();
				int i = 0;
				for (int k = 0; k < n; k++) if (c.valueAt(k) == want) i = k;
				yield f != null ? n + i : i;
			}
		};
	}

	private static void setOwn(ServerPlayer p, Setting s, int index) {
		switch (s) {
			case Setting.Toggle t -> {
				boolean shipped = t.def;
				switch (index) {
					case 0 -> t.house(null, shipped ? null : 1);
					case 1 -> t.house(null, shipped ? 0 : null);
					case 2 -> t.house(1, t.start() == shipped ? null : (t.start() ? 1 : 0));
					default -> t.house(0, t.start() == shipped ? null : (t.start() ? 1 : 0));
				}
			}
			case Setting.Choice c -> {
				int n = c.size();
				int shipped = c.defaultValue();
				if (index < n) {
					int v = c.valueAt(index);
					c.house(null, v == shipped ? null : v);
				} else {
					int start = c.start();
					c.house(c.valueAt(index - n), start == shipped ? null : start);
				}
			}
		}
		Libreria.LOG.info("{} set the players' setting {} to: {}", p.getName().getString(), s.fullKey(), ownOptions(s).get(index));
	}

	// ---------------------------------------------------------------- picture

	@Override
	protected Canvas draw() {
		Canvas c = new Canvas().panel();
		c.text("Settings", 8, 5, MUTED);
		int x = 8 + Glyphs.width(Component.literal("Settings"));
		c.text(" › ", x, 5, MUTED);
		x += Glyphs.width(Component.literal(" › "));
		if (this.module == null) {
			c.text("Server settings", x, 5, TINT);
			List<Module> all = modules();
			this.page = Math.max(0, Math.min(this.page, modulePages() - 1));
			int from = this.page * MODULE_ROWS.length * 2;
			for (int i = 0; i < MODULE_ROWS.length * 2 && from + i < all.size(); i++) {
				Module m = all.get(from + i);
				int col = i % 2 == 0 ? 0 : 5, row = MODULE_ROWS[i / 2], w = 4;
				c.card(col, row, w, 1, m.on() ? tint(m) : OFF);
				int left = Canvas.cellX(col) + Canvas.CARD_MARGIN + 6;
				c.text(Canvas.fit(m.name, 18 * w - 2 * Canvas.CARD_MARGIN - 20), left, Canvas.cellY(row) + 5, m.on() ? TEXT : MUTED);
				c.text("›", Canvas.cellX(col + w) - Canvas.CARD_MARGIN - 8, Canvas.cellY(row) + 5, MUTED);
			}
			int pages = modulePages();
			if (pages > 1) {
				small(c, 0, 3, "Back");
				small(c, 3, 3, this.page > 0 ? "‹ Page " + this.page : "");
				small(c, 6, 3, this.page < pages - 1 ? "Page " + (this.page + 2) + " ›" : "");
			} else {
				small(c, 0, 9, "Back");
			}
			return c;
		}
		Module m = this.module;
		c.text(Canvas.fit(m.name, 168 - x), x, 5, tint(m));
		int left = Canvas.cellX(0) + Canvas.CARD_MARGIN + 6;
		for (Placed pl : rows()) {
			int i = pl.at(), h = pl.height();
			int top = Canvas.cellY(i);
			int y1 = h == 1 ? top + 5 : top + 2 + (18 * h - 4 - 18) / 2, y2 = y1 + 10;
			switch (pl.row()) {
				case Header hd -> c.text(hd.name(), Canvas.cellX(0) + 3, top + 5, TINT);
				case Flag f -> {
					c.card(0, i, 8, h, f.s().get() ? tint(m) : NEUTRAL);
					lines(c, f.s().label, left, y1, y2, h, ROOM);
				}
				case Pick p -> value(c, i, h, p.s().label, p.s().options.get(p.s().get()), "‹›", tint(m), left, y1, y2);
				case Count n -> value(c, i, h, n.s().label, n.s().display(), "‹›", tint(m), left, y1, y2);
				case Words w -> value(c, i, h, w.s().label, w.s().isSet() ? w.s().get() : "Not set", "›", tint(m), left, y1, y2);
				case Own o -> value(c, i, h, Keys.plain(o.s().label), ownOptions(o.s()).get(ownIndex(o.s())), "‹›", tint(m), left, y1, y2);
				case Link l -> {
					c.card(0, i, 9, h, NEUTRAL);
					lines(c, l.label(), left, y1, y2, h, ROOM_FULL - 10);
					c.text("›", Canvas.cellX(9) - Canvas.CARD_MARGIN - 8, y1, MUTED);
				}
			}
		}
		int pages = pages().size();
		if (pages > 1) {
			small(c, 0, 3, "Back");
			small(c, 3, 3, this.page > 0 ? "‹ Page " + this.page : "");
			small(c, 6, 3, this.page < pages - 1 ? "Page " + (this.page + 2) + " ›" : "");
		} else {
			small(c, 0, 4, "Back");
			small(c, 4, 5, "Defaults");
		}
		return c;
	}

	/** A card over the full width: the label, under it the value, and a hint on the right of the value line. */
	private static void value(Canvas c, int row, int h, String label, String value, String hint, int tint, int left, int y1, int y2) {
		c.card(0, row, 9, h, tint);
		c.text(Canvas.fit(label, ROOM_FULL), left, y1, TEXT);
		int hw = Glyphs.width(Component.literal(hint));
		c.text(hint, Canvas.cellX(9) - Canvas.CARD_MARGIN - 5 - hw, y2, MUTED);
		c.text(Canvas.fit(value, ROOM_FULL - hw - 8), left, y2, VALUE);
	}

	private static void lines(Canvas c, String label, int left, int y1, int y2, int h, int room) {
		if (h == 1) {
			c.text(Canvas.fit(label, room), left, y1, TEXT);
			return;
		}
		List<String> ls = Canvas.wrap(label, room);
		c.text(Canvas.fit(ls.get(0), room), left, y1, TEXT);
		if (ls.size() > 1) c.text(Canvas.fit(String.join(" ", ls.subList(1, ls.size())), room), left, y2, TEXT);
	}

	private static void small(Canvas c, int col, int w, String label) {
		if (label.isEmpty()) return;
		c.card(col, 9, w, 1, NEUTRAL);
		c.centred(label, Canvas.cellX(col) + 9 * w, Canvas.cellY(9) + 5, TEXT);
	}

	// ---------------------------------------------------------------- cells

	@Override
	protected void fill() {
		if (!Ui.isOp(this.player)) return;
		Component backName = Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
		if (this.module == null) {
			List<Module> all = modules();
			int from = this.page * MODULE_ROWS.length * 2;
			for (int i = 0; i < MODULE_ROWS.length * 2 && from + i < all.size(); i++) {
				Module m = all.get(from + i);
				int col = i % 2 == 0 ? 0 : 5, row = MODULE_ROWS[i / 2];
				List<Component> lore = new ArrayList<>();
				lore.add(m.on() ? Ui.text("Turned on", ChatFormatting.GREEN) : Ui.text("Turned off", ChatFormatting.RED));
				for (ServerSetting s : m.serverSettings()) lore.add(Ui.kv(s.label + ": ", s.display(), ChatFormatting.WHITE));
				if (!m.settings().isEmpty()) lore.add(Ui.text(m.settings().size() + " players' settings", ChatFormatting.GRAY));
				for (Module.SettingsButton b : m.adminButtons()) lore.add(Ui.text(b.label(), ChatFormatting.GRAY));
				lore.add(Component.empty());
				lore.add(Ui.text("▸ Click: open", ChatFormatting.YELLOW));
				area(col, row, 4, 1, Ui.text(m.name, tint(m)).withStyle(ChatFormatting.BOLD), lore, p -> open(p, m));
			}
			int pages = modulePages();
			if (pages > 1) {
				area(0, 9, 3, 1, backName, List.of(), SettingsWindow::open);
				if (this.page > 0) area(3, 9, 3, 1, Ui.text("Previous page", ChatFormatting.WHITE), List.of(), p -> turn(-1));
				if (this.page < pages - 1) area(6, 9, 3, 1, Ui.text("Next page", ChatFormatting.WHITE), List.of(), p -> turn(1));
			} else {
				area(0, 9, 9, 1, backName, List.of(), SettingsWindow::open);
			}
			return;
		}
		Module m = this.module;
		for (Placed pl : rows()) {
			int row = pl.at(), h = pl.height();
			switch (pl.row()) {
				case Header hd -> {
				}
				case Flag f -> {
					ServerSetting.Toggle s = f.s();
					Component name = Ui.text(s.label, ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
					List<Component> lore = lore(s.lore, s.get() ? "On" : "Off", "Click: turn " + (s.get() ? "off" : "on"));
					Consumer<ServerPlayer> flip = p -> {
						if (!Ui.isOp(p)) return;
						s.set(!s.get());
						changed(p, s);
						refresh();
					};
					area(0, row, 8, h, name, lore, flip);
					if (h == 2) area(8, row + 1, 1, 1, name, lore, flip);
					ItemStack sw = new ItemStack(Items.PAPER);
					sw.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, s.get() ? SWITCH_ON : SWITCH_OFF);
					icon(8, row, sw, name, lore, flip);
				}
				case Pick pk -> {
					ServerSetting.Choice s = pk.s();
					List<Component> lore = new ArrayList<>(lore(s.lore, null));
					lore.add(Component.empty());
					for (int k = 0; k < s.options.size(); k++) {
						boolean sel = k == s.get();
						lore.add(Ui.text((sel ? "▶ " : "   ") + s.options.get(k), sel ? ChatFormatting.AQUA : ChatFormatting.GRAY));
					}
					lore.add(Component.empty());
					lore.add(Ui.text("▸ Click: next   Right-click: back", ChatFormatting.YELLOW));
					areaClick(0, row, 9, h, Ui.text(s.label, ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), lore, (p, right) -> {
						if (!Ui.isOp(p)) return;
						int n = s.options.size();
						s.set(Math.floorMod(s.get() + (right ? -1 : 1), n));
						changed(p, s);
						refresh();
					});
				}
				case Count cn -> {
					ServerSetting.Number s = cn.s();
					List<Component> lore = lore(s.lore, s.display(), "Click: +" + s.step + "   Right-click: −" + s.step,
							"With Shift: ten steps at once", "Range: " + s.min + " to " + s.max + (s.unit.isEmpty() ? "" : " " + s.unit));
					areaTyped(0, row, 9, h, Ui.text(s.label, ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), lore, (p, type) -> {
						if (!Ui.isOp(p) || !(type.isLeft || type.isRight)) return;
						Ui.click(p);
						int steps = type.shift ? 10 : 1;
						s.set(s.get() + (type.isRight ? -steps : steps) * s.step);
						changed(p, s);
						refresh();
					});
				}
				case Words wd -> {
					ServerSetting.Text s = wd.s();
					List<Component> lore = lore(s.lore, s.isSet() ? s.get() : "not set", "Click: change it");
					area(0, row, 9, h, Ui.text(s.label, ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), lore, p -> text(p, m, s, this.page));
				}
				case Own o -> {
					Setting s = o.s();
					List<String> options = ownOptions(s);
					int now = ownIndex(s);
					List<Component> lore = new ArrayList<>(lore(List.of("A setting of each player's own. Each player chooses it, "
							+ "starting at what you pick; or one value for everybody (then it's no longer in their settings)."), null));
					lore.add(Component.empty());
					for (int k = 0; k < options.size(); k++) {
						lore.add(Ui.text((k == now ? "▶ " : "   ") + options.get(k), k == now ? ChatFormatting.AQUA : ChatFormatting.GRAY));
					}
					lore.add(Component.empty());
					lore.add(Ui.text("▸ Click: next   Right-click: back", ChatFormatting.YELLOW));
					areaClick(0, row, 9, h, Ui.text(Keys.plain(s.label), ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), lore, (p, right) -> {
						if (!Ui.isOp(p)) return;
						setOwn(p, s, Math.floorMod(ownIndex(s) + (right ? -1 : 1), options.size()));
						Notify.whisper(p, Ui.text(Keys.plain(s.label) + ": " + options.get(ownIndex(s)), ChatFormatting.GRAY));
						refresh();
					});
				}
				case Link l -> area(0, row, 9, h, Ui.text(l.label(), ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD),
						lore(List.of(l.about()), null, "Click: open"), p -> {
							if (Ui.isOp(p)) l.action().accept(p);
						});
			}
		}
		int pages = pages().size();
		if (pages > 1) {
			area(0, 9, 3, 1, backName, List.of(), AdminCanvas::open);
			if (this.page > 0) area(3, 9, 3, 1, Ui.text("Previous page", ChatFormatting.WHITE), List.of(), p -> turn(-1));
			if (this.page < pages - 1) area(6, 9, 3, 1, Ui.text("Next page", ChatFormatting.WHITE), List.of(), p -> turn(1));
		} else {
			area(0, 9, 4, 1, backName, List.of(), AdminCanvas::open);
			area(4, 9, 5, 1, Ui.text("Defaults", ChatFormatting.YELLOW).withStyle(ChatFormatting.BOLD),
					List.of(Ui.text("Every setting here back to what the part ships with.", ChatFormatting.GRAY)), p -> {
						if (!Ui.isOp(p)) return;
						for (ServerSetting s : m.serverSettings()) s.reset();
						for (Setting st : m.settings()) st.house(null, null);
						Libreria.LOG.info("{} reset the server settings of {}", p.getName().getString(), m.name);
						Notify.whisper(p, m.name + ": back to the defaults", ChatFormatting.GRAY);
						refresh();
					});
		}
	}

	/** A text setting (a link): typed in a small window, then back here. */
	private static void text(ServerPlayer p, Module m, ServerSetting.Text s, int page) {
		Window w = Window.path("Server settings", m.name, s.label);
		if (!s.lore.isEmpty()) w.text(Ui.text(String.join(" ", s.lore), ChatFormatting.GRAY));
		w.field("v", Ui.text(s.label, ChatFormatting.WHITE), s.get(), s.maxLength);
		w.button(Ui.text("✔ Save", ChatFormatting.GREEN), null, (pl, v) -> {
			if (!Ui.isOp(pl)) return;
			String t = v.text("v", s.get()).strip();
			if (s.key.startsWith("link_") && !t.isEmpty() && Links.uri(t) == null) {
				Toast.show(pl, Items.BARRIER, Toast.WARN, "Not saved: " + s.label, "That has to be a full https:// address.");
				text(pl, m, s, page);
				return;
			}
			s.set(t);
			changed(pl, s);
			new AdminCanvas(pl, m, page).show();
		});
		w.cancel(pl -> new AdminCanvas(pl, m, page).show()).show(p);
	}

	private static void changed(ServerPlayer p, ServerSetting s) {
		Libreria.LOG.info("{} set {} to {}", p.getName().getString(), s.fullKey(), s.display());
		Notify.whisper(p, Ui.text(s.label + ": " + s.display(), ChatFormatting.GRAY));
	}

	private void turn(int step) {
		this.page += step;
		refresh();
	}

	private static List<Component> lore(List<String> about, @Nullable String state, String... actions) {
		List<Component> out = new ArrayList<>();
		if (state != null) out.add(Ui.text("Now: " + state, ChatFormatting.AQUA));
		for (String l : Canvas.wrap(String.join(" ", about), 180)) out.add(Ui.text(l, ChatFormatting.GRAY));
		if (actions.length > 0) out.add(Component.empty());
		for (String a : actions) out.add(Ui.text("▸ " + a, ChatFormatting.YELLOW));
		return out;
	}
}
