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
import net.minecraft.world.item.Items;
import nl.libreria.core.Domain;
import nl.libreria.core.Guide;
import nl.libreria.core.Notify;
import nl.libreria.core.PlaceList;
import nl.libreria.core.PlaceList.Kind;
import nl.libreria.core.PlaceList.Place;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * The "Your places" menu (see {@link PlaceList}): tabs on top, the places nearest first, and every place works the same:
 * click = show the way, right-click = details, Q = the extra thing some places have.
 */
public final class PlacesMenu extends Menu {
	private static final int FIRST = 9, PER_PAGE = 36;
	private static final String[] WINDS = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};

	private @Nullable String kind;
	private int page;

	private PlacesMenu(ServerPlayer player, @Nullable String kind, int page) {
		super(player, 6, label(kind));
		this.kind = kind;
		this.page = page;
		rebuild();
	}

	@Override
	protected Domain domain() {
		return Domain.PLACES;
	}

	public static void open(ServerPlayer player, @Nullable String kind) {
		if (kind != null && PlaceList.kind(kind) == null) kind = null;
		new PlacesMenu(player, kind, 0).open();
	}

	private static String label(@Nullable String kind) {
		Kind k = kind == null ? null : PlaceList.kind(kind);
		return k == null ? "Your places" : k.label;
	}

	private void rebuild() {
		for (int i = 0; i < this.rows * 9; i++) this.clearSlot(i);
		List<Place> all = PlaceList.all(this.player);
		Map<String, Integer> counts = new HashMap<>();
		for (Place pl : all) counts.merge(pl.kind, 1, Integer::sum);
		List<Place> shown = new ArrayList<>();
		for (Place pl : all) if (this.kind == null || pl.kind.equals(this.kind)) shown.add(pl);
		String dim = this.player.level().dimension().identifier().toString();
		shown.sort(Comparator.<Place>comparingInt(pl -> pl.dim.equals(dim) ? 0 : 1).thenComparingInt(pl -> pl.isDimmed() ? 1 : 0)
				.thenComparingDouble(pl -> pl.dim.equals(dim) ? pl.pos.distanceToSqr(this.player.position()) : 0)
				.thenComparing(pl -> pl.name));

		int pages = Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
		this.page = Math.max(0, Math.min(this.page, pages - 1));
		String title = label(this.kind) + " (" + shown.size() + ")";
		// A new title re-opens the screen (the cursor jumps), so only when it really changes
		if (!title.equals(this.plainTitle().getString())) this.setTitle(Component.literal(title));

		// Top row: the tab's own action (left), the tabs (middle), "stop" (right)
		Kind current = this.kind == null ? null : PlaceList.kind(this.kind);
		Kind actionKind = current;
		if (actionKind == null || actionKind.actionButton() == null) {
			actionKind = null;
			for (Kind k : PlaceList.kinds()) {
				if (k.actionButton() != null && (current == null || k == current)) {
					actionKind = k;
					break;
				}
			}
		}
		if (actionKind != null && actionKind.actionButton() != null) {
			GuiElementBuilder b = actionKind.actionButton().apply(this.player);
			if (b != null) this.setSlot(0, b);
		}
		tabs(counts, all.size());
		if (Guide.has(this.player)) {
			this.setSlot(8, actions(button(Ui.item("spectral_arrow"), "Stop showing the way", ChatFormatting.RED, "Removes the beam you're following."),
					"Click: stop").setCallback((i, t, a, g) -> {
						Ui.click(this.player);
						Guide.clear(this.player);
						rebuild();
					}));
		}

		int from = this.page * PER_PAGE;
		for (int i = 0; i < PER_PAGE && from + i < shown.size(); i++) this.setSlot(FIRST + i, element(shown.get(from + i), dim));
		if (shown.isEmpty()) this.setSlot(FIRST + 13, emptyState(current));

		up();
		pages(this.page, pages, p -> {
			this.page = p;
			rebuild();
		});
		closeButton();
		if (current != null && current.footerButton() != null) {
			GuiElementBuilder b = current.footerButton().apply(this.player);
			if (b != null) this.setSlot(navRow() + 6, b);
		}
		List<String> help = new ArrayList<>(List.of(
				"Everything you can be shown the",
				"way to, nearest first.",
				"",
				"Click a place: show the way.",
				"Right-click: details (where there are).",
				"Tabs at the top: one kind only."));
		if (current != null && !current.helpLines().isEmpty()) {
			help.add("");
			help.addAll(current.helpLines());
		}
		help(help.toArray(String[]::new));
		finish();
	}

	// ---------------------------------------------------------------- tabs

	private void tabs(Map<String, Integer> counts, int total) {
		List<Kind> kinds = PlaceList.kinds();
		int n = Math.min(7, kinds.size() + 1);
		int start = 1 + (7 - n) / 2;
		tab(start, null, Ui.item("recovery_compass"), "All", total, List.of("Every place, nearest first."));
		for (int i = 0; i + 1 < n; i++) {
			Kind k = kinds.get(i);
			tab(start + 1 + i, k.id, k.icon, k.label, counts.getOrDefault(k.id, 0), k.aboutLines());
		}
	}

	private void tab(int slot, @Nullable String id, net.minecraft.world.item.Item icon, String label, int count, List<String> about) {
		boolean active = java.util.Objects.equals(this.kind, id);
		GuiElementBuilder b = new GuiElementBuilder(icon).setName(title(label, active ? ChatFormatting.GREEN
				: count == 0 ? ChatFormatting.GRAY : ChatFormatting.WHITE)).hideDefaultTooltip();
		for (String l : about) b.addLoreLine(Ui.text(l, ChatFormatting.GRAY));
		b.addLoreLine(Ui.text(count + (count == 1 ? " place" : " places"), ChatFormatting.DARK_GRAY));
		b.addLoreLine(Component.empty());
		if (active) {
			b.addLoreLine(Ui.text("✔ Showing now", ChatFormatting.GREEN));
			b.glow();
		} else {
			action(b, "Click: show these");
		}
		this.setSlot(slot, b.setCallback((i, type, a, g) -> {
			if (active) return;
			Ui.click(this.player);
			this.kind = id;
			this.page = 0;
			rebuild();
		}));
	}

	// ---------------------------------------------------------------- places

	private GuiElementBuilder element(Place pl, String dim) {
		boolean here = pl.dim.equals(dim);
		GuiElementBuilder b = GuiElementBuilder.from(pl.icon.isEmpty() ? new net.minecraft.world.item.ItemStack(Items.MAP) : pl.icon.copyWithCount(1))
				.setName(title(pl.name, pl.isDimmed() ? ChatFormatting.DARK_GRAY : here ? ChatFormatting.WHITE : ChatFormatting.GRAY))
				.hideDefaultTooltip();
		Kind k = PlaceList.kind(pl.kind);
		if (this.kind == null && k != null) b.addLoreLine(Ui.text(k.label, Domain.PLACES.rgb));
		b.addLoreLine(here ? Ui.text(distance(pl), ChatFormatting.WHITE) : Ui.kv("In the ", dimension(pl.dim), ChatFormatting.GOLD));
		for (Component l : pl.loreLines()) b.addLoreLine(l);
		b.addLoreLine(Component.empty());
		if (here) action(b, "Click: show the way");
		else b.addLoreLine(Ui.text("In another dimension", ChatFormatting.DARK_GRAY));
		if (pl.detailsAction() != null) action(b, "Right-click: " + pl.detailsLabel());
		if (pl.dropAction() != null) action(b, "Q: " + pl.dropLabel());
		return b.setCallback((index, type, a, g) -> click(pl, type, here));
	}

	private void click(Place pl, ClickType type, boolean here) {
		if (pl.dropAction() != null && (type == ClickType.DROP || type == ClickType.CTRL_DROP)) {
			Ui.click(this.player);
			this.close();
			pl.dropAction().accept(this.player);
			return;
		}
		if (pl.detailsAction() != null && secondary(type)) {
			Ui.click(this.player);
			pl.detailsAction().accept(this.player);
			return;
		}
		if (!type.isLeft && !type.isRight) return;
		Ui.click(this.player);
		if (!here) {
			Notify.warn(this.player, Items.COMPASS, "In another dimension", pl.name + " is in the " + dimension(pl.dim) + ".");
			return;
		}
		this.close();
		Consumer<ServerPlayer> go = pl.goAction();
		if (go != null) go.accept(this.player);
		else Guide.toPoint(this.player, this.player.level(), pl.pos, pl.icon.copyWithCount(1), Component.literal(pl.name), 0);
	}

	private GuiElementBuilder emptyState(@Nullable Kind k) {
		if (k != null && !k.emptyLines().isEmpty()) {
			return button(k.icon, "Nothing here yet", ChatFormatting.WHITE, k.emptyLines().toArray(String[]::new));
		}
		return button(Items.MAP, "No places yet", ChatFormatting.WHITE, "Save a waypoint, tame a pet or find", "a village: it shows up here.");
	}

	/** "120 m · north-east". */
	private String distance(Place pl) {
		double dx = pl.pos.x - this.player.getX(), dz = pl.pos.z - this.player.getZ();
		double d = Math.sqrt(dx * dx + dz * dz + Math.pow(pl.pos.y - this.player.getY(), 2));
		if (d < 8) return "Right here";
		String far = d >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f km", d / 1000) : Math.round(d) + " m";
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		int i = (int) Math.floorMod(Math.round(angle / 45.0), 8);
		return far + " · " + WINDS[i];
	}

	public static String dimension(String dim) {
		return switch (dim) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> dim.substring(dim.indexOf(':') + 1).replace('_', ' ');
		};
	}
}
