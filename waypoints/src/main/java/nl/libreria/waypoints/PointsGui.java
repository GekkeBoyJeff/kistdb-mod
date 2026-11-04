package nl.libreria.waypoints;

import com.mojang.authlib.GameProfile;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.HubGui;
import nl.libreria.core.gui.Menu;
import nl.libreria.waypoints.WaypointsMod.Point;

/**
 * De waypoint-lijst: tabbladen bovenaan (alles / van mij / gedeeld), klik = straal, rechtsklik op je eigen punt =
 * bewerken. Bovenaan ook "nieuw waypoint hier" en je laatste sterfplek.
 */
public final class PointsGui extends Menu {
	/** Filters bovenaan de lijst. */
	enum Tab {
		ALL("All", "All"),
		MINE("Mine", "Mine"),
		SHARED("Shared with me / public", "Shared");

		final String label;
		final String shortLabel;

		Tab(String label, String shortLabel) {
			this.label = label;
			this.shortLabel = shortLabel;
		}
	}

	private static final int FIRST = 9;
	private static final int PER_PAGE = 36;

	private Tab tab;
	private int page;

	private PointsGui(ServerPlayer player, Tab tab, int page) {
		super(player, 6, "Waypoints");
		this.tab = tab;
		this.page = page;
		rebuild();
	}

	public static void open(ServerPlayer player) {
		open(player, Tab.ALL, 0);
	}

	static void open(ServerPlayer player, Tab tab, int page) {
		new PointsGui(player, tab, page).open();
	}

	private void rebuild() {
		for (int i = 0; i < this.rows * 9; i++) this.clearSlot(i);
		UUID me = this.player.getUUID();
		boolean xaero = WaypointsMod.XAERO.get(this.player);
		List<Point> visible = WaypointsMod.visible(this.player);
		List<Point> mine = new ArrayList<>();
		List<Point> shared = new ArrayList<>();
		for (Point pt : visible) (pt.ownedBy(me) ? mine : shared).add(pt);
		List<Point> shown = WaypointsMod.sorted(this.player, switch (this.tab) {
			case ALL -> visible;
			case MINE -> mine;
			case SHARED -> shared;
		});
		int pages = Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
		this.page = Math.max(0, Math.min(this.page, pages - 1));
		String title = "Waypoints · " + this.tab.shortLabel + " ("
				+ (this.tab == Tab.MINE ? mine.size() + "/" + WaypointsMod.MAX_POINTS : String.valueOf(shown.size())) + ")";
		// A new title re-opens the screen (cursor jumps), so only when it really changes
		if (this.getTitle() == null || !title.equals(this.getTitle().getString())) this.setTitle(Component.literal(title));

		// Top row: ✚ new (left), the three filter tabs (middle), last death point (right)
		newButton(mine.size());
		tab(3, Tab.ALL, new GuiElementBuilder(Items.COMPASS), visible.size(), "Every waypoint you can see.");
		tab(4, Tab.MINE, head(new GuiElementBuilder(Items.PLAYER_HEAD), this.player.getGameProfile()), mine.size(),
				"Waypoints you made yourself.");
		tab(5, Tab.SHARED, new GuiElementBuilder(Items.ENDER_EYE), shared.size(), "Public waypoints of others,", "and ones shared with you.");
		deathButton();

		// The waypoints themselves
		int from = this.page * PER_PAGE;
		for (int i = 0; i < PER_PAGE && from + i < shown.size(); i++) {
			this.setSlot(FIRST + i, entry(shown.get(from + i), me, xaero));
		}
		if (shown.isEmpty()) emptyState(mine.size());

		// Navigation bar
		back("the Libreria menu", () -> HubGui.open(this.player));
		pages(this.page, pages, p -> {
			this.page = p;
			rebuild();
		});
		closeButton();
		if (xaero) {
			this.setSlot(navRow() + 7, actions(button(Items.FILLED_MAP, "Send all to Xaero's Minimap", ChatFormatting.AQUA,
					"Puts every waypoint you can see", "in chat (nearest " + WaypointsMod.XAERO_MAX_LINES + "), so Xaero's",
					"Minimap can add them.", "", "Also: /waypoint xaero all"), "Click: send")
					.setCallback((i, t, a, g) -> {
						Ui.click(this.player);
						this.close();
						WaypointsMod.xaeroAll(this.player);
					}));
		}
		List<String> help = new ArrayList<>(List.of(
				"Click a waypoint: beam to it.",
				"Right-click your own: rename, icon",
				"and who can see it.",
				"✚ saves the spot you're standing on.",
				"Also: /wp set <name>, /wp go <name>"));
		if (xaero) help.add("Q on a waypoint: Xaero's Minimap.");
		help(help.toArray(String[]::new));
		finish();
	}

	// ---------------------------------------------------------------- top row

	private void newButton(int mineCount) {
		boolean full = mineCount >= WaypointsMod.MAX_POINTS;
		GuiElementBuilder b = button(Items.LODESTONE, "✚ New waypoint here", ChatFormatting.GREEN,
				"Saves the spot where you stand.", "Everyone can see it at first;", "right-click it later to change.", "",
				"Yours: " + mineCount + "/" + WaypointsMod.MAX_POINTS);
		if (full) {
			b.addLoreLine(Component.empty());
			b.addLoreLine(Ui.text("✘ That's the maximum.", ChatFormatting.RED));
			b.addLoreLine(Ui.text("Delete one to make room.", ChatFormatting.GRAY));
		} else {
			actions(b, "Click: choose a name");
		}
		b.setCallback((i, t, a, g) -> {
			Ui.click(this.player);
			if (full) WaypointsMod.msg(this.player, "You already have " + WaypointsMod.MAX_POINTS + " waypoints. Delete one first.",
					ChatFormatting.RED);
			else askName();
		});
		this.setSlot(0, b);
	}

	/** Naam vragen in het aambeeld en het punt hier maken; bij een fout opnieuw vragen. */
	private void askName() {
		Tab back = this.tab == Tab.SHARED ? Tab.ALL : this.tab;
		int backPage = this.page;
		NameGui.open(this.player, "Name your waypoint", defaultName(), input -> {
			if (!WaypointsMod.create(this.player, input, false)) return false;
			PointsGui.open(this.player, back, backPage);
			return true;
		}, () -> PointsGui.open(this.player, this.tab, this.page));
	}

	private String defaultName() {
		if (WaypointsMod.findOwn(this.player, "Home") == null) return "Home";
		for (int n = 2; ; n++) {
			if (WaypointsMod.findOwn(this.player, "Waypoint " + n) == null) return "Waypoint " + n;
		}
	}

	private void tab(int slot, Tab t, GuiElementBuilder icon, int count, String... description) {
		boolean active = this.tab == t;
		icon.setName(title(t.label, active ? ChatFormatting.GREEN : ChatFormatting.WHITE)).hideDefaultTooltip();
		for (String d : description) icon.addLoreLine(Ui.text(d, ChatFormatting.GRAY));
		icon.addLoreLine(Ui.text(count + (count == 1 ? " waypoint" : " waypoints"), ChatFormatting.DARK_GRAY));
		icon.addLoreLine(Component.empty());
		if (active) {
			icon.addLoreLine(Ui.text("✔ Showing now", ChatFormatting.GREEN));
			icon.glow();
		} else {
			action(icon, "Click: show these");
		}
		icon.setCallback((i, type, a, g) -> {
			if (this.tab == t) return;
			Ui.click(this.player);
			this.tab = t;
			this.page = 0;
			rebuild();
		});
		this.setSlot(slot, icon);
	}

	private void deathButton() {
		Point d = WaypointsMod.deathPoint(this.player);
		if (d == null) return;
		boolean here = d.dim.equals(WaypointsMod.dimOf(this.player.level()));
		GuiElementBuilder b = button(Items.SKELETON_SKULL, "☠ Last death point", ChatFormatting.RED, "Only you can see this one.");
		b.addLoreLine(Ui.kv("Location: ", d.x + " " + d.y + " " + d.z, ChatFormatting.WHITE));
		b.addLoreLine(here ? Ui.kv("Distance: ", Ui.meters(WaypointsMod.distance(this.player, d)), ChatFormatting.WHITE)
				: Ui.kv("In the ", WaypointsMod.dimName(d.dim), ChatFormatting.GOLD));
		b.addLoreLine(Component.empty());
		if (here) action(b, "Click: beam to it");
		else b.addLoreLine(Ui.text("No beam: other dimension", ChatFormatting.DARK_GRAY));
		b.setCallback((i, t, a, g) -> {
			Ui.click(this.player);
			if (WaypointsMod.beam(this.player, d)) this.close();
		});
		this.setSlot(8, b);
	}

	// ---------------------------------------------------------------- entries

	private GuiElementBuilder entry(Point pt, UUID me, boolean xaero) {
		boolean own = pt.ownedBy(me);
		boolean here = pt.dim.equals(WaypointsMod.dimOf(this.player.level()));
		GuiElementBuilder b = new GuiElementBuilder(pt.item()).setName(title(pt.name, here ? ChatFormatting.YELLOW : ChatFormatting.GRAY))
				.hideDefaultTooltip();
		b.addLoreLine(own ? Ui.text("Yours", ChatFormatting.GREEN)
				: Ui.kv("by ", pt.ownerName.isEmpty() ? "someone" : pt.ownerName, ChatFormatting.AQUA));
		b.addLoreLine(here ? Ui.kv("Distance: ", Ui.meters(WaypointsMod.distance(this.player, pt)), ChatFormatting.WHITE)
				: Ui.kv("In the ", WaypointsMod.dimName(pt.dim), ChatFormatting.GOLD));
		b.addLoreLine(Ui.kv("Location: ", pt.x + " " + pt.y + " " + pt.z, ChatFormatting.WHITE));
		b.addLoreLine(Ui.kv("Visible to: ", WaypointsMod.visibilityText(pt, me), WaypointsMod.visibilityColor(pt.visibility)));
		b.addLoreLine(Component.empty());
		if (here) action(b, "Click: beam to it");
		else b.addLoreLine(Ui.text("No beam: other dimension", ChatFormatting.DARK_GRAY));
		if (own) action(b, "Right-click: edit");
		if (xaero) action(b, "Q: send to Xaero's Minimap");
		b.setCallback((i, type, a, g) -> {
			if (xaero && (type == ClickType.DROP || type == ClickType.CTRL_DROP || type == ClickType.MOUSE_MIDDLE)) {
				Ui.click(this.player);
				this.close();
				WaypointsMod.sendXaero(this.player, pt);
				return;
			}
			if (own && secondary(type)) {
				Ui.click(this.player);
				EditGui.open(this.player, pt, this.tab, this.page);
				return;
			}
			if (type.isLeft || type.isRight) {
				Ui.click(this.player);
				if (WaypointsMod.beam(this.player, pt)) this.close();
			}
		});
		return b;
	}

	private void emptyState(int mineCount) {
		GuiElementBuilder b;
		if (this.tab == Tab.SHARED) {
			b = button(Items.MAP, "Nothing shared yet", ChatFormatting.WHITE,
					"Public waypoints of other players", "and ones they share with you", "show up here.");
		} else {
			b = button(Items.MAP, this.tab == Tab.MINE ? "You have no waypoints yet" : "No waypoints yet", ChatFormatting.WHITE,
					"Click ✚ at the top left to add one", "where you are standing.");
			if (mineCount < WaypointsMod.MAX_POINTS) {
				actions(b, "Click: add one here");
				b.setCallback((i, t, a, g) -> {
					Ui.click(this.player);
					askName();
				});
			}
		}
		this.setSlot(FIRST + 13, b);
	}

	// ---------------------------------------------------------------- shared bits

	/** Spelerhoofd met skin: met textures uit het profiel, anders (offline-modus) op naam. */
	static GuiElementBuilder head(GuiElementBuilder b, GameProfile profile) {
		if (!profile.properties().isEmpty()) return b.setProfile(profile);
		return b.setProfile(profile.name());
	}

	/** Een naam typen in het aambeeld-scherm. {@code save} geeft false terug als de naam niet goed was (dan opnieuw). */
	static final class NameGui extends AnvilInputGui {
		interface Save {
			boolean save(String input);
		}

		private NameGui(ServerPlayer player, String title, String initial, Save save, Runnable cancel) {
			super(player, false);
			this.setTitle(Component.literal(title));
			this.setDefaultInputValue(initial);
			this.setSlot(1, actions(button(Items.BARRIER, "Cancel", ChatFormatting.RED, "Go back without saving."), "Click: cancel")
					.setCallback((index, type, action, gui) -> {
						Ui.click(player);
						cancel.run();
					}));
			this.setSlot(2, actions(button(Items.NAME_TAG, "Save", ChatFormatting.GREEN,
					"Type a name above (max " + WaypointsMod.MAX_NAME + ").", "Each of your waypoints needs", "its own name."), "Click: save")
					.setCallback((index, type, action, gui) -> {
						Ui.click(player);
						String input = this.getInput();
						if (!save.save(input)) NameGui.open(player, title, input, save, cancel);
					}));
		}

		static void open(ServerPlayer player, String title, String initial, Save save, Runnable cancel) {
			new NameGui(player, title, initial, save, cancel).open();
		}
	}
}
