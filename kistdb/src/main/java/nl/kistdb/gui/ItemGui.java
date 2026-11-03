package nl.kistdb.gui;

import nl.kistdb.Beam;

import nl.handig.kern.Ui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.kistdb.Search;
import nl.kistdb.Tracker;

/** Alle kisten waar één item in ligt, met aantal en afstand. */
final class ItemGui extends SimpleGui {
	private final Item item;
	private final String backFilter;
	private final boolean backByName;

	private ItemGui(ServerPlayer player, Item item, String backFilter, boolean backByName) {
		super(MenuType.GENERIC_9x6, player, false);
		this.item = item;
		this.backFilter = backFilter;
		this.backByName = backByName;
		rebuild();
	}

	static void open(ServerPlayer player, Item item, String backFilter, boolean backByName) {
		new ItemGui(player, item, backFilter, backByName).open();
	}

	private void rebuild() {
		ServerPlayer player = this.getPlayer();
		ItemStack sample = new ItemStack(this.item);
		List<Search.Hit> hits = Search.find(player, this.item);
		this.setTitle(Component.literal("Waar ligt: ").append(sample.getHoverName()));
		for (int i = 0; i < 45; i++) {
			if (i < hits.size()) {
				Search.Hit hit = hits.get(i);
				var e = hit.entry();
				GuiElementBuilder b = new GuiElementBuilder(Search.blockItem(e))
						.setCount(Math.max(1, Math.min(hit.count(), 64)))
						.setName(Ui.text(Search.kind(e) + " · " + Ui.meters(hit.distance()), ChatFormatting.YELLOW))
						.addLoreLine(Ui.kv("Aantal: ", String.valueOf(hit.count()), ChatFormatting.GREEN))
						.addLoreLine(Ui.kv("Plek: ", e.x + " " + e.y + " " + e.z, ChatFormatting.WHITE));
				if (!hit.live()) b.addLoreLine(Ui.kv("Laatst gezien: ", Search.ago(e.seen), ChatFormatting.DARK_GRAY));
				b.addLoreLine(Component.empty())
						.addLoreLine(Ui.text("Klik: straal hierheen", ChatFormatting.AQUA))
						.addLoreLine(Ui.text("Shift-klik: noIndex (uitsluiten)", ChatFormatting.RED));
				b.setCallback((index, type, action, gui) -> {
					if (type.shift) {
						Tracker.toggleNoIndex(player, player.level(), e.pos());
						rebuild();
					} else if (type.isLeft || type.isRight) {
						this.close();
						Beam.to(player, player.level(), e.pos(), sample, hit.count());
					}
				});
				this.setSlot(i, b.build());
			} else {
				this.clearSlot(i);
			}
		}
		for (int i = 45; i < 54; i++) this.clearSlot(i);
		this.setSlot(45, Ui.button(Items.ARROW, "Terug", ChatFormatting.WHITE)
				.setCallback((index, type, action, gui) -> {
					Ui.click(player);
					OverviewGui.open(player, this.backFilter, this.backByName);
				}).build());
		this.setSlot(49, GuiElementBuilder.from(sample.copyWithCount(1))
				.addLoreLine(Ui.kv("In ", hits.size() + (hits.size() == 1 ? " kist" : " kisten"), ChatFormatting.WHITE))
				.addLoreLine(Ui.text("Klik: straal naar de dichtstbijzijnde", ChatFormatting.AQUA))
				.setCallback((index, type, action, gui) -> {
					this.close();
					Locate.go(player, sample, hits);
				}).build());
	}
}
