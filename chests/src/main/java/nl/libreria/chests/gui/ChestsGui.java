package nl.libreria.chests.gui;

import nl.libreria.chests.Beam;

import nl.libreria.core.Ui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.chests.Search;
import nl.libreria.chests.Store;
import nl.libreria.chests.Tracker;

/** "Mijn kisten": alle containers in ChestsMod (ook noIndex), dichtstbijzijnde eerst. */
public final class ChestsGui extends SimpleGui {
	private int page;

	private ChestsGui(ServerPlayer player) {
		super(MenuType.GENERIC_9x6, player, false);
		rebuild();
	}

	public static void open(ServerPlayer player) {
		new ChestsGui(player).open();
	}

	private void rebuild() {
		ServerPlayer player = this.getPlayer();
		List<Search.Box> boxes = Search.boxes(player, true);
		int pages = Math.max(1, (boxes.size() + 44) / 45);
		this.page = Math.min(this.page, pages - 1);
		this.setTitle(Component.literal("My chests (" + boxes.size() + ")" + (pages > 1 ? " " + (this.page + 1) + "/" + pages : "")));
		for (int i = 0; i < 45; i++) {
			int idx = this.page * 45 + i;
			if (idx < boxes.size()) {
				this.setSlot(i, element(boxes.get(idx)).build());
			} else {
				this.clearSlot(i);
			}
		}
		for (int i = 45; i < 54; i++) this.clearSlot(i);
		this.setSlot(45, Ui.button(Items.ARROW, "Back", ChatFormatting.WHITE)
				.setCallback((index, type, action, gui) -> {
					Ui.click(player);
					OverviewGui.open(player, "");
				}).build());
		if (this.page > 0) {
			this.setSlot(48, Ui.button(Items.ARROW, "Previous page", ChatFormatting.WHITE)
					.setCallback((index, type, action, gui) -> {
						this.page--;
						rebuild();
					}).build());
		}
		this.setSlot(49, Ui.button(Items.BOOK, "Help", ChatFormatting.AQUA)
				.addLoreLine(Ui.text("Click: beam to the chest", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("Right-click: view contents", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("Shift-click: toggle noIndex", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("Q (drop): forget", ChatFormatting.GRAY))
				.build());
		if (this.page < pages - 1) {
			this.setSlot(50, Ui.button(Items.ARROW, "Next page", ChatFormatting.WHITE)
					.setCallback((index, type, action, gui) -> {
						this.page++;
						rebuild();
					}).build());
		}
	}

	private GuiElementBuilder element(Search.Box box) {
		ServerPlayer player = this.getPlayer();
		Store.Entry e = box.entry();
		int kinds = box.counts().size();
		long total = box.counts().values().stream().mapToLong(Integer::longValue).sum();
		GuiElementBuilder b = new GuiElementBuilder(Search.blockItem(e))
				.setName(Ui.text(Search.kind(e) + " · " + Ui.meters(box.distance()), e.noIndex ? ChatFormatting.RED : ChatFormatting.YELLOW))
				.addLoreLine(Ui.kv("Location: ", e.x + " " + e.y + " " + e.z, ChatFormatting.WHITE))
				.addLoreLine(Ui.kv("Contents: ", total + " items, " + kinds + " types", ChatFormatting.GREEN));
		if (!box.live()) b.addLoreLine(Ui.kv("Last seen: ", Search.ago(e.seen), ChatFormatting.DARK_GRAY));
		b.addLoreLine(e.noIndex
				? Ui.text("noIndex: ON (skipped)", ChatFormatting.RED)
				: Ui.text("noIndex: off", ChatFormatting.DARK_GREEN));
		if (!e.noIndex) b.glow();
		b.setCallback((index, type, action, gui) -> {
			if (type == eu.pb4.sgui.api.ClickType.DROP || type == eu.pb4.sgui.api.ClickType.CTRL_DROP) {
				Store.remove(player.getUUID(), e);
				Tracker.actionbar(player, Ui.text("Chest forgotten", ChatFormatting.GRAY));
				rebuild();
			} else if (type.shift) {
				Tracker.toggleNoIndex(player, player.level(), e.pos());
				rebuild();
			} else if (type.isRight) {
				Ui.click(player);
				ContentsGui.open(player, box);
			} else if (type.isLeft) {
				this.close();
				Beam.to(player, player.level(), e.pos(), null, 0);
			}
		});
		return b;
	}

	/** Alleen-lezen weergave van de inhoud van één kist. */
	static final class ContentsGui extends SimpleGui {
		private ContentsGui(ServerPlayer player, Search.Box box) {
			super(MenuType.GENERIC_9x6, player, false);
			Store.Entry e = box.entry();
			this.setTitle(Component.literal(Search.kind(e) + " at " + e.x + " " + e.y + " " + e.z + (box.live() ? "" : " (last seen)")));
			List<ItemStack> stacks = box.stacks();
			for (int i = 0; i < 54 && i < stacks.size(); i++) {
				ItemStack s = stacks.get(i);
				this.setSlot(i, GuiElementBuilder.from(s.copy())
						.setCallback((index, type, action, gui) -> {
							this.close();
							Beam.to(player, player.level(), e.pos(), s, s.getCount());
						}).build());
			}
			this.setSlot(53, Ui.button(Items.ARROW, "Back", ChatFormatting.WHITE)
					.setCallback((index, type, action, gui) -> ChestsGui.open(player)).build());
		}

		static void open(ServerPlayer player, Search.Box box) {
			new ContentsGui(player, box).open();
		}
	}
}
