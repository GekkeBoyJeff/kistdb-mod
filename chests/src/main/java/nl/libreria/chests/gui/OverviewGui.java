package nl.libreria.chests.gui;

import nl.libreria.core.Guide;

import nl.libreria.core.Ui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import nl.libreria.chests.Search;
import nl.libreria.chests.Sorter;

/** Overzicht van alle items in je kisten in de buurt, als iconen. Klik = straal naar de dichtstbijzijnde kist. */
public final class OverviewGui extends SimpleGui {
	private static final int PER_PAGE = 45;
	private final String filter;
	private boolean byName;
	private int page;

	private OverviewGui(ServerPlayer player, String filter, boolean byName) {
		super(MenuType.GENERIC_9x6, player, false);
		this.filter = filter == null ? "" : filter;
		this.byName = byName;
		rebuild();
	}

	public static void open(ServerPlayer player, String filter) {
		new OverviewGui(player, filter, false).open();
	}

	static void open(ServerPlayer player, String filter, boolean byName) {
		new OverviewGui(player, filter, byName).open();
	}

	private void rebuild() {
		ServerPlayer player = this.getPlayer();
		List<Search.Agg> list = Search.aggregate(player, this.filter, this.byName);
		int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
		this.page = Math.min(this.page, pages - 1);

		String title = this.filter.isEmpty() ? "KistDB – je kisten" : "KistDB – \"" + this.filter + "\"";
		if (pages > 1) title += " (" + (this.page + 1) + "/" + pages + ")";
		this.setTitle(Component.literal(title));

		for (int i = 0; i < PER_PAGE; i++) {
			int idx = this.page * PER_PAGE + i;
			if (idx < list.size()) {
				this.setSlot(i, element(list.get(idx)).build());
			} else {
				this.clearSlot(i);
			}
		}
		if (list.isEmpty()) {
			this.setSlot(22, Ui.button(Items.BARRIER, this.filter.isEmpty() ? "Nog niets gevonden" : "Geen items gevonden", ChatFormatting.RED)
					.addLoreLine(Ui.text(this.filter.isEmpty() ? "Open een kist, ton of shulker box:" : "Probeer een andere zoekterm", ChatFormatting.GRAY))
					.addLoreLine(Ui.text(this.filter.isEmpty() ? "die komt dan vanzelf in KistDB." : "(Engelse naam of item-ID)", ChatFormatting.GRAY))
					.build());
		}

		// Onderste rij
		for (int i = 45; i < 54; i++) this.clearSlot(i);
		if (this.page > 0) {
			this.setSlot(45, Ui.button(Items.ARROW, "Vorige pagina", ChatFormatting.WHITE)
					.setCallback((index, type, action, gui) -> {
						this.page--;
						Ui.click(player);
						rebuild();
					}).build());
		}
		this.setSlot(46, Ui.button(Items.NAME_TAG, "Zoeken op naam…", ChatFormatting.YELLOW)
				.addLoreLine(Ui.text(this.filter.isEmpty() ? "Typ bv. diamond of oak" : "Nu: \"" + this.filter + "\"", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("Rechtsklik: zoekterm wissen", ChatFormatting.DARK_GRAY))
				.setCallback((index, type, action, gui) -> {
					Ui.click(player);
					if (type.isRight) {
						open(player, "", this.byName);
					} else {
						SearchInputGui.open(player, this.filter);
					}
				}).build());
		this.setSlot(47, Ui.button(Items.COMPARATOR, this.byName ? "Sortering: A–Z" : "Sortering: meeste eerst", ChatFormatting.WHITE)
				.addLoreLine(Ui.text("Klik om te wisselen", ChatFormatting.GRAY))
				.setCallback((index, type, action, gui) -> {
					this.byName = !this.byName;
					this.page = 0;
					Ui.click(player);
					rebuild();
				}).build());
		this.setSlot(48, Ui.button(Items.CHEST, "Mijn kisten", ChatFormatting.GOLD)
				.addLoreLine(Ui.text("Alle kisten in KistDB: inhoud bekijken,", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("straal ernaartoe, noIndex aan/uit", ChatFormatting.GRAY))
				.setCallback((index, type, action, gui) -> {
					Ui.click(player);
					ChestsGui.open(player);
				}).build());
		this.setSlot(49, Ui.button(Items.BOOK, "Uitleg", ChatFormatting.AQUA)
				.addLoreLine(Ui.text("• Open een kist: hij komt in KistDB", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("• Klik een item: straal naar de kist", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("• Rechtsklik een item: alle kisten", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("• Sneak + linksklik op een kist (lege hand):", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("   noIndex aan/uit", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("• In een kist: middelklik = sorteren", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("• Commando: /kist", ChatFormatting.GRAY))
				.build());
		this.setSlot(50, Ui.button(Items.HOPPER, "Sorteer inventory", ChatFormatting.YELLOW)
				.setCallback((index, type, action, gui) -> {
					Sorter.sortPlayer(player);
				}).build());
		if (Guide.has(player)) {
			this.setSlot(51, Ui.button(Items.BARRIER, "Straal uitzetten", ChatFormatting.RED)
					.setCallback((index, type, action, gui) -> {
						Guide.clear(player);
						Ui.click(player);
						rebuild();
					}).build());
		}
		if (this.page < pages - 1) {
			this.setSlot(53, Ui.button(Items.ARROW, "Volgende pagina", ChatFormatting.WHITE)
					.setCallback((index, type, action, gui) -> {
						this.page++;
						Ui.click(player);
						rebuild();
					}).build());
		}
	}

	private GuiElementBuilder element(Search.Agg agg) {
		ServerPlayer player = this.getPlayer();
		Search.Hit near = agg.nearest();
		int shown = (int) Math.max(1, Math.min(agg.total(), 99));
		GuiElementBuilder b = GuiElementBuilder.from(agg.sample().copyWithCount(shown))
				.addLoreLine(Ui.kv("Totaal: ", String.valueOf(agg.total()), ChatFormatting.GREEN))
				.addLoreLine(Ui.kv("In ", agg.hits().size() + (agg.hits().size() == 1 ? " kist" : " kisten"), ChatFormatting.WHITE)
						.append(Ui.text(" · dichtstbij " + Ui.meters(near.distance()), ChatFormatting.YELLOW)))
				.addLoreLine(Component.empty())
				.addLoreLine(Ui.text("Klik: straal naar de dichtstbijzijnde", ChatFormatting.AQUA))
				.addLoreLine(Ui.text("Rechtsklik: alle kisten met dit item", ChatFormatting.AQUA));
		b.setCallback((index, type, action, gui) -> {
			if (type.isRight) {
				Ui.click(player);
				ItemGui.open(player, agg.item(), this.filter, this.byName);
			} else if (type.isLeft) {
				this.close();
				Locate.go(player, agg.sample(), agg.hits());
			}
		});
		return b;
	}
}
