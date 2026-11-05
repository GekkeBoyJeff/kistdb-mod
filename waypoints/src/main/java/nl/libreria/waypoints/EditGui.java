package nl.libreria.waypoints;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Menu;
import nl.libreria.waypoints.WaypointsMod.Point;
import nl.libreria.waypoints.WaypointsMod.Visibility;

/** Je eigen waypoint bewerken: naam, icoon, wie het ziet (met spelerkeuze), naar Xaero sturen, verwijderen. */
final class EditGui extends Menu {
	private final Point pt;
	/** Where "Back" goes: the list it was opened from. */
	private final Runnable back;

	private EditGui(ServerPlayer player, Point pt, Runnable back) {
		super(player, 5, "Edit waypoint");
		this.pt = pt;
		this.back = back;
		rebuild();
	}

	static void open(ServerPlayer player, Point pt, Runnable back) {
		if (!valid(player, pt)) {
			back.run();
			return;
		}
		new EditGui(player, pt, back).open();
	}

	/** Bestaat het punt nog en is het van deze speler? Zo niet: melding. */
	static boolean valid(ServerPlayer player, Point pt) {
		if (WaypointsMod.exists(pt) && pt.ownedBy(player.getUUID())) return true;
		WaypointsMod.fail(player, "Waypoint gone", "It was deleted in the meantime.");
		return false;
	}

	/** Voert {@code r} uit als het punt nog geldig is, anders terug naar de lijst. */
	private void ifValid(Runnable r) {
		if (valid(this.player, this.pt)) r.run();
		else this.back.run();
	}

	private void backToList() {
		this.back.run();
	}

	private void rebuild() {
		for (int i = 0; i < this.rows * 9; i++) this.clearSlot(i);
		String title = "Edit · " + this.pt.name;
		// A new title re-opens the screen (cursor jumps), so only when it really changes
		if (!title.equals(this.plainTitle().getString())) this.setTitle(Component.literal(title));
		boolean xaero = WaypointsMod.XAERO.get(this.player);

		// Top: the waypoint itself
		this.setSlot(4, preview());

		// Middle: what you can change, delete last (on the right)
		List<GuiElementBuilder> buttons = new ArrayList<>();
		buttons.add(renameButton());
		buttons.add(iconButton());
		int visIndex = buttons.size();
		buttons.add(visibilityButton());
		buttons.add(playersButton());
		if (xaero) buttons.add(xaeroButton());
		buttons.add(deleteButton());
		int[] slots = row(2, buttons.size());
		for (int i = 0; i < slots.length; i++) this.setSlot(slots[i], buttons.get(i));
		// The current visibility, as a coloured "◀ value ▶" pane under its button
		this.setSlot(slots[visIndex] + 9, visibilityPane());

		back("Your places", this::backToList);
		closeButton();
		help("Change this waypoint here.",
				"Who can see it: everyone, only you,",
				"or only players you pick.",
				"Deleting needs a shift-click.",
				"Also: /wp private|public <name>");
		finish();
	}

	// ---------------------------------------------------------------- top

	private GuiElementBuilder preview() {
		boolean here = this.pt.dim.equals(WaypointsMod.dimOf(this.player.level()));
		GuiElementBuilder b = new GuiElementBuilder(this.pt.item()).setName(title(this.pt.name, ChatFormatting.YELLOW)).hideDefaultTooltip()
				.glow();
		b.addLoreLine(Ui.text("Yours", ChatFormatting.GREEN));
		b.addLoreLine(here ? Ui.kv("Distance: ", Ui.meters(WaypointsMod.distance(this.player, this.pt)), ChatFormatting.WHITE)
				: Ui.kv("In the ", WaypointsMod.dimName(this.pt.dim), ChatFormatting.GOLD));
		b.addLoreLine(Ui.kv("Location: ", this.pt.x + " " + this.pt.y + " " + this.pt.z, ChatFormatting.WHITE));
		b.addLoreLine(Ui.kv("Visible to: ", WaypointsMod.visibilityText(this.pt, this.player.getUUID()),
				WaypointsMod.visibilityColor(this.pt.visibility)));
		b.addLoreLine(Component.empty());
		if (here) action(b, "Click: show the way");
		else b.addLoreLine(Ui.text("In another dimension", ChatFormatting.DARK_GRAY));
		b.setCallback((i, t, a, g) -> ifValid(() -> {
			Ui.click(this.player);
			if (WaypointsMod.beam(this.player, this.pt)) this.close();
		}));
		return b;
	}

	// ---------------------------------------------------------------- buttons

	private GuiElementBuilder renameButton() {
		GuiElementBuilder b = button(Items.NAME_TAG, "Rename", ChatFormatting.GOLD, "Give this waypoint a new name.");
		b.addLoreLine(Ui.kv("Now: ", this.pt.name, ChatFormatting.WHITE));
		actions(b, "Click: type a new name");
		return b.setCallback((i, t, a, g) -> ifValid(() -> {
			Ui.click(this.player);
			WaypointPlaces.NameGui.open(this.player, "Rename waypoint", this.pt.name, input -> {
				if (!valid(this.player, this.pt)) {
					backToList();
					return true;
				}
				if (!WaypointsMod.rename(this.player, this.pt, input)) return false;
				EditGui.open(this.player, this.pt, this.back);
				return true;
			}, () -> EditGui.open(this.player, this.pt, this.back));
		}));
	}

	private GuiElementBuilder iconButton() {
		ItemStack held = this.player.getMainHandItem();
		Item shown = held.isEmpty() ? Items.ITEM_FRAME : held.getItem();
		GuiElementBuilder b = button(shown, "Icon from your hand", ChatFormatting.GOLD,
				"Use the item in your hand", "as the icon of this waypoint.");
		b.addLoreLine(held.isEmpty() ? Ui.text("Your hand is empty.", ChatFormatting.RED)
				: Ui.kv("In your hand: ", held.getHoverName().getString(), ChatFormatting.WHITE));
		if (held.isEmpty()) {
			b.addLoreLine(Component.empty());
			b.addLoreLine(Ui.text("Hold an item, then open this", ChatFormatting.GRAY));
			b.addLoreLine(Ui.text("screen again.", ChatFormatting.GRAY));
		} else {
			actions(b, "Click: use it as icon");
		}
		return b.setCallback((i, t, a, g) -> ifValid(() -> {
			Ui.click(this.player);
			if (WaypointsMod.iconFromHand(this.player, this.pt)) {
				Ui.sound(this.player, SoundEvents.ITEM_PICKUP, 0.6f, 1.2f);
			}
			rebuild();
		}));
	}

	private GuiElementBuilder visibilityButton() {
		Visibility v = this.pt.visibility;
		Item icon = switch (v) {
			case EVERYONE -> Items.BEACON;
			case ONLY_ME -> Items.ENDER_CHEST;
			case SELECTED -> Items.PLAYER_HEAD;
		};
		GuiElementBuilder b = button(icon, "Who can see it", ChatFormatting.AQUA, "Choose who sees this waypoint", "in their list.");
		b.addLoreLine(Component.empty());
		for (Visibility o : Visibility.values()) {
			boolean sel = o == v;
			b.addLoreLine(Ui.text((sel ? "▶ " : "   ") + o.label, sel ? WaypointsMod.visibilityColor(o) : ChatFormatting.DARK_GRAY));
		}
		b.addLoreLine(Component.empty());
		for (String l : explain(v)) b.addLoreLine(Ui.text(l, ChatFormatting.GRAY));
		actions(b, "Left-click: next", "Right-click: previous");
		return b.setCallback((i, type, a, g) -> ifValid(() -> cycle(type.isRight ? -1 : 1)));
	}

	private GuiElementBuilder visibilityPane() {
		Visibility v = this.pt.visibility;
		String color = switch (v) {
			case EVERYONE -> "lime";
			case ONLY_ME -> "orange";
			case SELECTED -> "light_blue";
		};
		GuiElementBuilder b = new GuiElementBuilder(Ui.item(color + "_stained_glass_pane"))
				.setName(title("◀ " + v.label + " ▶", WaypointsMod.visibilityColor(v))).hideDefaultTooltip();
		for (String l : explain(v)) b.addLoreLine(Ui.text(l, ChatFormatting.GRAY));
		b.addLoreLine(Component.empty());
		action(b, "Left-click: next");
		action(b, "Right-click: previous");
		return b.setCallback((i, type, a, g) -> ifValid(() -> cycle(type.isRight ? -1 : 1)));
	}

	private List<String> explain(Visibility v) {
		return switch (v) {
			case EVERYONE -> List.of("Everyone: all players see it.");
			case ONLY_ME -> List.of("Only me: nobody else sees it.");
			case SELECTED -> {
				int n = this.pt.selected.size();
				yield List.of("Selected: only you and the", "players you pick.",
						n == 0 ? "No one picked yet." : n + (n == 1 ? " player picked." : " players picked."));
			}
		};
	}

	private void cycle(int dir) {
		Visibility[] all = Visibility.values();
		Visibility next = all[Math.floorMod(this.pt.visibility.ordinal() + dir, all.length)];
		WaypointsMod.setVisibility(server(), this.pt, next);
		Ui.click(this.player);
		rebuild();
	}

	private GuiElementBuilder playersButton() {
		boolean selected = this.pt.visibility == Visibility.SELECTED;
		GuiElementBuilder b = button(Items.PLAYER_HEAD, "Pick players", selected ? ChatFormatting.AQUA : ChatFormatting.GRAY,
				"Only the players you pick", "(and you) will see it.");
		WaypointPlaces.head(b, this.player.getGameProfile());
		b.addLoreLine(Component.empty());
		if (this.pt.selected.isEmpty()) {
			b.addLoreLine(Ui.text("No one picked yet.", ChatFormatting.DARK_GRAY));
		} else {
			int shown = 0;
			for (String name : this.pt.selected.values()) {
				if (shown++ == 4) {
					b.addLoreLine(Ui.text("+ " + (this.pt.selected.size() - 4) + " more", ChatFormatting.DARK_GRAY));
					break;
				}
				b.addLoreLine(Ui.text("• " + name, ChatFormatting.WHITE));
			}
		}
		if (!selected) {
			b.addLoreLine(Component.empty());
			b.addLoreLine(Ui.text("Switches \"Who can see it\"", ChatFormatting.GRAY));
			b.addLoreLine(Ui.text("to Selected players.", ChatFormatting.GRAY));
		}
		actions(b, "Click: choose players");
		return b.setCallback((i, t, a, g) -> ifValid(() -> {
			Ui.click(this.player);
			WaypointsMod.setVisibility(server(), this.pt, Visibility.SELECTED);
			new PickGui(this.player, this.pt, this.back, 0).open();
		}));
	}

	private GuiElementBuilder xaeroButton() {
		GuiElementBuilder b = button(Items.FILLED_MAP, "Send to Xaero's Minimap", ChatFormatting.AQUA,
				"Puts a line in chat that", "Xaero's Minimap can add", "as a waypoint.", "", "Also: /waypoint xaero <name>");
		actions(b, "Click: send");
		return b.setCallback((i, t, a, g) -> ifValid(() -> {
			Ui.click(this.player);
			this.close();
			WaypointsMod.sendXaero(this.player, this.pt);
		}));
	}

	private GuiElementBuilder deleteButton() {
		GuiElementBuilder b = button(Items.LAVA_BUCKET, "Delete", ChatFormatting.RED,
				"Removes this waypoint for", "everyone. This can't be undone.");
		actions(b, "Shift-click: delete");
		return b.setCallback((i, type, a, g) -> ifValid(() -> {
			if (!type.shift) {
				Ui.sound(this.player, SoundEvents.NOTE_BLOCK_BASS, 0.5f, 0.7f);
				nl.libreria.core.Notify.whisper(this.player, "Shift-click to delete " + this.pt.name, ChatFormatting.GRAY);
				return;
			}
			WaypointsMod.delete(this.pt);
			Ui.sound(this.player, SoundEvents.ITEM_PICKUP, 0.6f, 0.6f);
			WaypointsMod.done(this.player, "Waypoint deleted", this.pt.name);
			backToList();
		}));
	}

	private MinecraftServer server() {
		return this.player.level().getServer();
	}

	// ---------------------------------------------------------------- player picker

	/** Spelers kiezen die het punt zien: online spelers als hoofden, plus wie al gekozen is (ook offline). */
	private static final class PickGui extends Menu {
		private static final int FIRST = 9;
		private static final int PER_PAGE = 36;

		private final Point pt;
		private final Runnable back;
		private int page;

		PickGui(ServerPlayer player, Point pt, Runnable back, int page) {
			super(player, 6, "Who sees this waypoint");
			this.pt = pt;
			this.back = back;
			this.page = page;
			rebuild();
		}

		private record Candidate(UUID id, String name, @org.jetbrains.annotations.Nullable ServerPlayer online) {
		}

		private void rebuild() {
			for (int i = 0; i < this.rows * 9; i++) this.clearSlot(i);
			MinecraftServer server = this.player.level().getServer();
			List<Candidate> list = new ArrayList<>();
			List<ServerPlayer> online = new ArrayList<>(server.getPlayerList().getPlayers());
			online.sort((a, b) -> a.getName().getString().compareToIgnoreCase(b.getName().getString()));
			for (ServerPlayer other : online) {
				if (other.getUUID().equals(this.player.getUUID())) continue;
				list.add(new Candidate(other.getUUID(), other.getName().getString(), other));
			}
			List<Candidate> offline = new ArrayList<>();
			for (Map.Entry<UUID, String> e : this.pt.selected.entrySet()) {
				if (server.getPlayerList().getPlayer(e.getKey()) == null) offline.add(new Candidate(e.getKey(), e.getValue(), null));
			}
			offline.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
			list.addAll(offline);

			int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
			this.page = Math.max(0, Math.min(this.page, pages - 1));

			// Top: which waypoint this is about
			int n = this.pt.selected.size();
			GuiElementBuilder info = button(this.pt.item(), this.pt.name, ChatFormatting.YELLOW,
					"Who may see this waypoint?", "Click a head to add or remove", "that player.");
			info.addLoreLine(Component.empty());
			info.addLoreLine(Ui.text(n == 0 ? "No one picked yet: only you" : n + (n == 1 ? " player picked" : " players picked"),
					ChatFormatting.AQUA));
			if (n == 0) info.addLoreLine(Ui.text("can see it now.", ChatFormatting.AQUA));
			this.setSlot(4, info);

			int from = this.page * PER_PAGE;
			for (int i = 0; i < PER_PAGE && from + i < list.size(); i++) this.setSlot(FIRST + i, head(list.get(from + i)));
			if (list.isEmpty()) {
				this.setSlot(FIRST + 13, button(Items.PAPER, "No other players online", ChatFormatting.WHITE,
						"Players show up here while", "they're online. Players you", "picked stay picked when", "they log off."));
			}

			back("editing " + this.pt.name, () -> EditGui.open(this.player, this.pt, this.back));
			pages(this.page, pages, p -> {
				this.page = p;
				rebuild();
			});
			closeButton();
			help("Click a head to let that player",
					"see this waypoint, or to take",
					"it away again.",
					"Shown: players who are online,",
					"plus everyone you already picked.");
			finish();
		}

		private GuiElementBuilder head(Candidate c) {
			boolean on = this.pt.selected.containsKey(c.id());
			GuiElementBuilder b = new GuiElementBuilder(Items.PLAYER_HEAD)
					.setName(title((on ? "✔ " : "") + c.name(), on ? ChatFormatting.GREEN : ChatFormatting.WHITE)).hideDefaultTooltip();
			if (c.online() != null) WaypointPlaces.head(b, c.online().getGameProfile());
			else b.setProfile(c.name());
			b.addLoreLine(Ui.text(c.online() != null ? "Online" : "Offline", c.online() != null ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
			b.addLoreLine(Ui.text(on ? "Can see this waypoint." : "Can't see this waypoint.", ChatFormatting.GRAY));
			actions(b, on ? "Click: take it away" : "Click: let them see it");
			if (on) b.glow();
			b.setCallback((i, t, a, g) -> {
				if (!EditGui.valid(this.player, this.pt)) {
					this.back.run();
					return;
				}
				Ui.click(this.player);
				WaypointsMod.select(this.player.level().getServer(), this.pt, c.id(), c.name(), !on);
				rebuild();
			});
			return b;
		}
	}
}
