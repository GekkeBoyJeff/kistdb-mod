package nl.libreria.core.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.Domain;
import nl.libreria.core.Guide;
import nl.libreria.core.Notify;
import nl.libreria.core.PlaceList;
import nl.libreria.core.PlaceList.Kind;
import nl.libreria.core.PlaceList.Place;
import nl.libreria.core.Start;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * "Your places" as a window of our own: the tabs on top (the kinds of place), then a list, nearest first, with each
 * place's name and how far and which way it is, so you don't have to point at every picture. Click a row: show the
 * way; right-click: details; Q: the extra thing some places have (like forgetting a death point).
 */
public final class PlacesWindow extends CanvasWindow {
	private static final int PER_PAGE = 8;
	/** Rows of cells for the list (the first row holds the tabs, the hotbar row the buttons). */
	private static final int[] ROWS = {1, 2, 3, 4, 5, 6, 7, 8};
	private static final String[] WINDS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
	private static final int TEXT = 0xFFFFFF, OTHER = 0x9AA0AC;

	private @Nullable String kind;
	private int page;

	private PlacesWindow(ServerPlayer p, @Nullable String kind, int page) {
		super(p);
		this.kind = kind;
		this.page = page;
	}

	public static void open(ServerPlayer p, @Nullable String kind, int page) {
		new PlacesWindow(p, kind, page).show();
	}

	// ---------------------------------------------------------------- what's shown

	private List<Place> all() {
		return PlaceList.all(this.player);
	}

	private List<Place> shown(List<Place> all) {
		String dim = dim();
		List<Place> out = new ArrayList<>();
		for (Place pl : all) if (this.kind == null || pl.kind.equals(this.kind)) out.add(pl);
		out.sort(Comparator.<Place>comparingInt(pl -> pl.dim.equals(dim) ? 0 : 1).thenComparingInt(pl -> pl.isDimmed() ? 1 : 0)
				.thenComparingDouble(pl -> pl.dim.equals(dim) ? pl.pos.distanceToSqr(this.player.position()) : 0)
				.thenComparing(pl -> pl.name));
		return out;
	}

	private String dim() {
		return this.player.level().dimension().identifier().toString();
	}

	private int pages(List<Place> shown) {
		return Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
	}

	/** The tabs: All, then one per kind (at most seven in all). */
	private List<Kind> tabs() {
		List<Kind> kinds = new ArrayList<>(PlaceList.kinds());
		return kinds.size() > 6 ? kinds.subList(0, 6) : kinds;
	}

	/** "120 m  NE", "Right here", or the dimension it is in. */
	private String where(Place pl) {
		if (!pl.dim.equals(dim())) return PlacesMenu.dimension(pl.dim);
		double dx = pl.pos.x - this.player.getX(), dz = pl.pos.z - this.player.getZ();
		double d = Math.sqrt(dx * dx + dz * dz + Math.pow(pl.pos.y - this.player.getY(), 2));
		if (d < 8) return "Right here";
		String far = d >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f km", d / 1000) : Math.round(d) + " m";
		int i = (int) Math.floorMod(Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0), 8);
		return far + "  " + WINDS[i];
	}

	// ---------------------------------------------------------------- picture

	@Override
	protected Canvas draw() {
		List<Place> all = all();
		List<Place> shown = shown(all);
		int pages = pages(shown);
		this.page = Math.max(0, Math.min(this.page, pages - 1));
		PlacesMenu.remember(this.player, this.kind, this.page);

		Canvas c = new Canvas().panel();
		// "Land & places › Your places", or with a kind chosen "Your places › Waypoints": three parts don't fit the width
		int x = 8;
		Kind current = this.kind == null ? null : PlaceList.kind(this.kind);
		String first = current == null ? Domain.PLACES.label : "Your places";
		String last = current == null ? "Your places" : current.label;
		c.text(first, x, 5, Domain.PLACES.rgb);
		x += Glyphs.width(Component.literal(first));
		c.text(" › ", x, 5, Glyphs.muted);
		x += Glyphs.width(Component.literal(" › "));
		c.text(Canvas.fit(last, 168 - x), x, 5, TEXT);
		// Under the active tab, a short line in the colour of the place
		int active = tabIndex();
		c.picture("sm.bar", Canvas.cellX(active) + 5, Canvas.cellY(0) + 11, Domain.PLACES.rgb);
		int countRight = Guide.has(this.player) ? Canvas.cellX(8) - 3 : 168 - 3;
		c.right(Integer.toString(shown.size()), countRight, Canvas.cellY(0) + 5, Glyphs.muted);

		int from = this.page * PER_PAGE;
		int left = Canvas.cellX(1) + 4, width = Canvas.cellX(9) - 2 - left;
		for (int i = 0; i < PER_PAGE && from + i < shown.size(); i++) {
			Place pl = shown.get(from + i);
			int row = ROWS[i];
			boolean here = pl.dim.equals(dim());
			c.line(left, row, width, pl.isDimmed() || !here ? 0x8A909C : Domain.PLACES.rgb);
			int y = Canvas.cellY(row) + 5;
			String far = where(pl);
			int fw = Glyphs.width(Component.literal(far));
			c.text(Canvas.fit(pl.name, width - 12 - fw - 8), left + 6, y, pl.isDimmed() || !here ? OTHER : TEXT);
			c.right(far, left + width - 6, y, Glyphs.muted);
		}
		if (shown.isEmpty()) {
			List<String> lines = current != null && !current.emptyLines().isEmpty() ? current.emptyLines()
					: List.of("Tame a pet, claim land or find", "a village: it shows up here.");
			int y = Canvas.cellY(3);
			c.centred(current != null ? "Nothing here yet" : "No places yet", 88, y, TEXT);
			for (String l : lines) c.centred(l, 88, y += 11, Glyphs.muted);
		}

		// Bottom: Back, the pages, and the tab's own button
		small(c, 0, 3, "Back");
		if (pages > 1) {
			if (this.page > 0) small(c, 3, 1, "‹");
			c.centred((this.page + 1) + "/" + pages, Canvas.cellX(4) + 9, Canvas.cellY(9) + 5, Glyphs.muted);
			if (this.page < pages - 1) small(c, 5, 1, "›");
		}
		String extra = extraLabel(current);
		if (extra != null) small(c, 6, 3, extra);
		return c;
	}

	private static void small(Canvas c, int col, int w, String label) {
		c.card(col, 9, w, 1, Glyphs.neutral);
		c.centred(Canvas.fit(label, 18 * w - 10), Canvas.cellX(col) + 9 * w, Canvas.cellY(9) + 5, TEXT);
	}

	private int tabIndex() {
		if (this.kind == null) return 0;
		List<Kind> tabs = tabs();
		for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).id.equals(this.kind)) return i + 1;
		return 0;
	}

	/** The tab's own button (like "+ New" for waypoints), or the footer one; null if there is none. */
	private @Nullable GuiElementBuilder extra(@Nullable Kind current) {
		Kind k = current;
		if (k == null) {
			for (Kind x : PlaceList.kinds()) {
				if (x.actionButton() != null) {
					k = x;
					break;
				}
			}
		}
		if (k == null) return null;
		if (k.actionButton() != null) return k.actionButton().apply(this.player);
		if (k.footerButton() != null) return k.footerButton().apply(this.player);
		return null;
	}

	private @Nullable String extraLabel(@Nullable Kind current) {
		GuiElementBuilder b = extra(current);
		if (b == null) return null;
		String name = Canvas.clean(b.asStack().getHoverName().getString()).replaceAll("^[^\\p{L}]+", "");
		return name.startsWith("New ") ? "+ New" : name;
	}

	// ---------------------------------------------------------------- cells

	@Override
	protected void fill() {
		List<Place> all = all();
		List<Place> shown = shown(all);
		int pages = pages(shown);
		Map<String, Integer> counts = new HashMap<>();
		for (Place pl : all) counts.merge(pl.kind, 1, Integer::sum);

		// Tabs
		tab(0, null, new ItemStack(Items.RECOVERY_COMPASS), "All", all.size(), List.of("Every place, nearest first."));
		List<Kind> tabs = tabs();
		for (int i = 0; i < tabs.size(); i++) {
			Kind k = tabs.get(i);
			tab(i + 1, k.id, new ItemStack(k.icon), k.label, counts.getOrDefault(k.id, 0), k.aboutLines());
		}
		if (Guide.has(this.player)) {
			Component name = Ui.text("Stop showing the way", ChatFormatting.RED).withStyle(ChatFormatting.BOLD);
			icon(8, 0, new ItemStack(Items.SPECTRAL_ARROW), name, List.of(Ui.text("Removes the beam you're following.", ChatFormatting.GRAY),
					Component.empty(), Ui.text("▸ Click: stop", ChatFormatting.YELLOW)), p -> {
						Guide.clear(p);
						Notify.whisper(p, "Stopped showing the way", ChatFormatting.GRAY);
						refresh();
					});
		}

		// The list
		int from = this.page * PER_PAGE;
		for (int i = 0; i < PER_PAGE && from + i < shown.size(); i++) {
			Place pl = shown.get(from + i);
			boolean here = pl.dim.equals(dim());
			Component name = Ui.text(pl.name, pl.isDimmed() ? ChatFormatting.DARK_GRAY : here ? ChatFormatting.WHITE : ChatFormatting.GRAY)
					.withStyle(ChatFormatting.BOLD);
			List<Component> lore = lore(pl, here);
			iconTyped(0, ROWS[i], pl.icon.isEmpty() ? new ItemStack(Items.MAP) : pl.icon, name, lore, (p, type) -> click(pl, type, here));
			areaTyped(1, ROWS[i], 8, 1, name, lore, (p, type) -> click(pl, type, here));
		}

		// Bottom
		area(0, 9, 3, 1, Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD),
				List.of(Ui.text("To " + Domain.PLACES.label + ".", ChatFormatting.GRAY)), p -> Start.domain(p, Domain.PLACES));
		if (pages > 1) {
			if (this.page > 0) area(3, 9, 1, 1, Ui.text("Previous page", ChatFormatting.WHITE), List.of(), p -> turn(-1));
			if (this.page < pages - 1) area(5, 9, 1, 1, Ui.text("Next page", ChatFormatting.WHITE), List.of(), p -> turn(1));
		}
		GuiElementBuilder extra = extra(this.kind == null ? null : PlaceList.kind(this.kind));
		if (extra != null) areaFrom(6, 9, 3, extra);
	}

	private void tab(int col, @Nullable String id, ItemStack icon, String label, int count, List<String> about) {
		boolean active = java.util.Objects.equals(this.kind, id);
		Component name = Ui.text(label, active ? ChatFormatting.GREEN : count == 0 ? ChatFormatting.GRAY : ChatFormatting.WHITE)
				.withStyle(ChatFormatting.BOLD);
		List<Component> lore = new ArrayList<>();
		for (String l : about) lore.add(Ui.text(l, ChatFormatting.GRAY));
		lore.add(Ui.text(count + (count == 1 ? " place" : " places"), ChatFormatting.DARK_GRAY));
		lore.add(Component.empty());
		lore.add(active ? Ui.text("✔ Showing now", ChatFormatting.GREEN) : Ui.text("▸ Click: show these", ChatFormatting.YELLOW));
		icon(col, 0, icon, name, lore, p -> {
			if (active) return;
			this.kind = id;
			this.page = 0;
			refresh();
		});
	}

	private List<Component> lore(Place pl, boolean here) {
		List<Component> out = new ArrayList<>();
		Kind k = PlaceList.kind(pl.kind);
		if (this.kind == null && k != null) out.add(Ui.text(k.label, Domain.PLACES.rgb));
		out.add(here ? Ui.text(PlacesMenu.distance(this.player, pl), ChatFormatting.WHITE)
				: Ui.kv("In the ", PlacesMenu.dimension(pl.dim), ChatFormatting.GOLD));
		out.addAll(pl.loreLines());
		out.add(Component.empty());
		if (here) out.add(Ui.text("▸ Click: show the way", ChatFormatting.YELLOW));
		else out.add(Ui.text("In another dimension", ChatFormatting.DARK_GRAY));
		if (pl.detailsAction() != null) out.add(Ui.text("▸ Right-click: " + pl.detailsLabel(), ChatFormatting.YELLOW));
		if (pl.dropAction() != null) out.add(Ui.text("▸ {drop}: " + pl.dropLabel(), ChatFormatting.YELLOW));
		return out;
	}

	private void click(Place pl, ClickType type, boolean here) {
		if (pl.dropAction() != null && (type == ClickType.DROP || type == ClickType.CTRL_DROP)) {
			Ui.click(this.player);
			this.close();
			pl.dropAction().accept(this.player);
			return;
		}
		if (pl.detailsAction() != null && Menu.secondary(type)) {
			Ui.click(this.player);
			pl.detailsAction().accept(this.player);
			return;
		}
		if (!type.isLeft && !type.isRight) return;
		Ui.click(this.player);
		if (!here) {
			Notify.warn(this.player, Items.COMPASS, "In another dimension", pl.name + " is in the " + PlacesMenu.dimension(pl.dim) + ".");
			return;
		}
		this.close();
		Consumer<ServerPlayer> go = pl.goAction();
		if (go != null) go.accept(this.player);
		else Guide.toPoint(this.player, this.player.level(), pl.pos, pl.icon.copyWithCount(1), Component.literal(pl.name), 0);
	}

	private void turn(int step) {
		this.page += step;
		refresh();
	}
}
