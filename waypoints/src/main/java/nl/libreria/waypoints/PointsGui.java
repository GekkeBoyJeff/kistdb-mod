package nl.libreria.waypoints;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Ui;

/** Je waypoints als iconen: klik = straal, shift-klik of Q = verwijderen. */
final class PointsGui extends SimpleGui {
	private PointsGui(ServerPlayer player) {
		super(MenuType.GENERIC_9x6, player, false);
		rebuild();
	}

	static void open(ServerPlayer player) {
		new PointsGui(player).open();
	}

	private void rebuild() {
		ServerPlayer player = this.getPlayer();
		WaypointsMod.Data data = WaypointsMod.STORE.get(player);
		String dim = WaypointsMod.dimOf(player.level());
		Vec3 pos = player.position();
		List<WaypointsMod.Point> points = new ArrayList<>(data.points);
		// Eerst deze dimensie, dichtstbijzijnde eerst
		points.sort(Comparator.<WaypointsMod.Point>comparingInt(p -> p.dim.equals(dim) ? 0 : 1)
				.thenComparingDouble(p -> pos.distanceTo(Vec3.atCenterOf(p.pos()))));
		this.setTitle(Component.literal("Waypoints (" + points.size() + "/" + WaypointsMod.MAX_POINTS + ")"));
		for (int i = 0; i < 45; i++) {
			if (i >= points.size()) {
				this.clearSlot(i);
				continue;
			}
			WaypointsMod.Point pt = points.get(i);
			boolean here = pt.dim.equals(dim);
			GuiElementBuilder b = new GuiElementBuilder(pt.item())
					.setName(Ui.text(pt.name, here ? ChatFormatting.YELLOW : ChatFormatting.GRAY))
					.addLoreLine(Ui.kv("Location: ", pt.x + " " + pt.y + " " + pt.z, ChatFormatting.WHITE))
					.addLoreLine(here
							? Ui.kv("Distance: ", Ui.meters(pos.distanceTo(Vec3.atCenterOf(pt.pos()))), ChatFormatting.GREEN)
							: Ui.kv("In the ", WaypointsMod.dimName(pt.dim), ChatFormatting.GOLD))
					.addLoreLine(Component.empty())
					.addLoreLine(Ui.text(here ? "Click: beam here" : "Other dimension", here ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY))
					.addLoreLine(Ui.text("Shift-click or Q: remove", ChatFormatting.RED));
			b.setCallback((index, type, action, gui) -> {
				if (type.shift || type == ClickType.DROP || type == ClickType.CTRL_DROP) {
					WaypointsMod.STORE.get(player).points.remove(pt);
					WaypointsMod.STORE.markDirty(player);
					Ui.click(player);
					rebuild();
				} else if (type.isLeft || type.isRight) {
					if (WaypointsMod.beam(player, pt)) this.close();
				}
			});
			this.setSlot(i, b.build());
		}
		for (int i = 45; i < 54; i++) this.clearSlot(i);
		this.setSlot(48, Ui.button(Items.LODESTONE, "Set waypoint here", ChatFormatting.GREEN)
				.addLoreLine(Ui.text("Sets a waypoint where you're standing.", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("Also: /waypoint set <name>", ChatFormatting.DARK_GRAY))
				.setCallback((index, type, action, gui) -> {
					Ui.click(player);
					NameGui.open(player);
				}).build());
		if (data.death != null) {
			WaypointsMod.Point d = data.death;
			this.setSlot(50, Ui.button(Items.SKELETON_SKULL, "Your last death point", ChatFormatting.RED)
					.addLoreLine(Ui.kv("Location: ", d.x + " " + d.y + " " + d.z + " (" + WaypointsMod.dimName(d.dim) + ")", ChatFormatting.WHITE))
					.addLoreLine(Ui.text("Click: beam here", ChatFormatting.AQUA))
					.setCallback((index, type, action, gui) -> {
						if (WaypointsMod.beam(player, d)) this.close();
					}).build());
		}
	}

	/** NicknamesMod typen in het aambeeld-scherm. */
	private static final class NameGui extends AnvilInputGui {
		private NameGui(ServerPlayer player) {
			super(player, false);
			this.setTitle(Component.literal("Waypoint name"));
			this.setDefaultInputValue("Home");
			this.setSlot(1, Ui.button(Items.BARRIER, "Cancel", ChatFormatting.RED)
					.setCallback((index, type, action, gui) -> PointsGui.open(player)).build());
			this.setSlot(2, Ui.button(Items.LODESTONE, "Save", ChatFormatting.GREEN)
					.setCallback((index, type, action, gui) -> {
						WaypointsMod.add(player, this.getInput());
						PointsGui.open(player);
					}).build());
		}

		static void open(ServerPlayer player) {
			new NameGui(player).open();
		}
	}
}
