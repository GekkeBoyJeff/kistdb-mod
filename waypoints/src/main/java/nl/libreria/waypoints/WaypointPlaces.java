package nl.libreria.waypoints;

import com.mojang.authlib.GameProfile;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.PlaceList;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Menu;
import nl.libreria.waypoints.WaypointsMod.Point;

/**
 * Waypoints and your death point in "Your places" ({@link PlaceList}): two tabs (your own, and shared ones) plus the
 * death point under "Death &amp; graves". Click = show the way, right-click your own = edit, Q = Xaero's Minimap.
 * The "✚ New waypoint here" button sits in the top-left corner.
 */
final class WaypointPlaces {
	static final String OWN = "waypoints", SHARED = "shared", DEATH = "death";

	private WaypointPlaces() {
	}

	static void register() {
		PlaceList.kind(OWN, "Your waypoints", Items.COMPASS, 10)
				.about("Places you saved yourself.")
				.empty("Click ✚ at the top left to save", "the spot where you stand.")
				.help("✚ saves the spot you stand on.", "Right-click your own: rename, icon", "and who can see it.",
						"Also: /wp set <name>, /wp go <name>")
				.action(WaypointPlaces::newButton)
				.footer(WaypointPlaces::xaeroButton);
		PlaceList.kind(SHARED, "Shared waypoints", Items.ENDER_EYE, 20)
				.about("Public waypoints of others,", "and ones shared with you.")
				.empty("Public waypoints of other players", "and ones they share with you", "show up here.")
				.action(WaypointPlaces::newButton)
				.footer(WaypointPlaces::xaeroButton);
		PlaceList.kind(DEATH, "Death & graves", Items.SKELETON_SKULL, 30)
				.about("Where you last died, and your graves.")
				.empty("Nothing here: you haven't died", "since Libreria was added.");
		PlaceList.addSource((p, out) -> {
			UUID me = p.getUUID();
			boolean xaero = WaypointsMod.XAERO.get(p);
			for (Point pt : WaypointsMod.visible(p)) {
				boolean own = pt.ownedBy(me);
				PlaceList.Place place = PlaceList.place(own ? OWN : SHARED, pt.name, new ItemStack(pt.item()), pt.dim, Vec3.atBottomCenterOf(pt.pos()))
						.lore(own ? Ui.text("Yours", ChatFormatting.GREEN) : Ui.kv("By ", pt.ownerName.isEmpty() ? "someone" : pt.ownerName, ChatFormatting.AQUA),
								Ui.kv("At ", pt.x + " " + pt.y + " " + pt.z, ChatFormatting.WHITE),
								Ui.kv("Visible to: ", WaypointsMod.visibilityText(pt, me), WaypointsMod.visibilityColor(pt.visibility)))
						.go(pl -> WaypointsMod.beam(pl, pt));
				if (own) place.details("edit", pl -> EditGui.open(pl, pt, () -> PlaceList.reopen(pl)));
				if (xaero) place.drop("send to Xaero's Minimap", pl -> WaypointsMod.sendXaero(pl, pt));
				out.add(place);
			}
			Point death = WaypointsMod.deathPoint(p);
			if (death != null) {
				out.add(PlaceList.place(DEATH, "Death point", new ItemStack(Items.SKELETON_SKULL), death.dim, Vec3.atBottomCenterOf(death.pos()))
						.lore(Ui.kv("At ", death.x + " " + death.y + " " + death.z, ChatFormatting.WHITE),
								Ui.text("Where you last died. Only you see it.", ChatFormatting.GRAY))
						.go(pl -> WaypointsMod.beam(pl, death)));
			}
		});
	}

	/** Your waypoints. */
	static void open(ServerPlayer p) {
		PlaceList.open(p, OWN);
	}

	// ---------------------------------------------------------------- buttons

	private static GuiElementBuilder newButton(ServerPlayer p) {
		int mine = WaypointsMod.own(p.getUUID()).size();
		boolean full = mine >= WaypointsMod.MAX_POINTS;
		GuiElementBuilder b = Menu.button(Items.LODESTONE, "✚ New waypoint here", ChatFormatting.GREEN,
				"Saves the spot where you stand.", "Everyone can see it at first;", "right-click it later to change.", "",
				"Yours: " + mine + "/" + WaypointsMod.MAX_POINTS);
		if (full) {
			b.addLoreLine(Component.empty());
			b.addLoreLine(Ui.text("✘ That's the maximum.", ChatFormatting.RED));
			b.addLoreLine(Ui.text("Delete one to make room.", ChatFormatting.GRAY));
		} else {
			Menu.actions(b, "Click: choose a name");
		}
		return b.setCallback((i, t, a, g) -> {
			Ui.click(p);
			if (full) WaypointsMod.fail(p, "Too many waypoints", "You have " + WaypointsMod.MAX_POINTS + ". Delete one first.");
			else askName(p);
		});
	}

	private static GuiElementBuilder xaeroButton(ServerPlayer p) {
		if (!WaypointsMod.XAERO.get(p)) return null;
		return Menu.actions(Menu.button(Items.FILLED_MAP, "Send all to Xaero's Minimap", ChatFormatting.AQUA,
				"Puts every waypoint you can see", "in chat (nearest " + WaypointsMod.XAERO_MAX_LINES + "), so Xaero's",
				"Minimap can add them.", "", "Also: /waypoint xaero all"), "Click: send").setCallback((i, t, a, g) -> {
					Ui.click(p);
					p.closeContainer();
					WaypointsMod.xaeroAll(p);
				});
	}

	/** Asks a name in the anvil and makes the waypoint here; asks again when the name doesn't work. */
	static void askName(ServerPlayer p) {
		NameGui.open(p, "Name your waypoint", defaultName(p), input -> {
			if (!WaypointsMod.create(p, input, false)) return false;
			PlaceList.reopen(p);
			return true;
		}, () -> PlaceList.reopen(p));
	}

	private static String defaultName(ServerPlayer p) {
		if (WaypointsMod.findOwn(p, "Home") == null) return "Home";
		for (int n = 2; ; n++) {
			if (WaypointsMod.findOwn(p, "Waypoint " + n) == null) return "Waypoint " + n;
		}
	}

	/** A player head with skin: from the profile's textures, or (offline mode) by name. */
	static GuiElementBuilder head(GuiElementBuilder b, GameProfile profile) {
		if (!profile.properties().isEmpty()) return b.setProfile(profile);
		return b.setProfile(profile.name());
	}

	/** Typing a name in the anvil screen. {@code save} returns false when the name didn't work (then it asks again). */
	static final class NameGui extends AnvilInputGui {
		interface Save {
			boolean save(String input);
		}

		private NameGui(ServerPlayer player, String title, String initial, Save save, Runnable cancel) {
			super(player, false);
			this.setTitle(Component.literal(title));
			this.setDefaultInputValue(initial);
			this.setSlot(1, Menu.actions(Menu.button(Items.BARRIER, "Cancel", ChatFormatting.RED, "Go back without saving."), "Click: cancel")
					.setCallback((index, type, action, gui) -> {
						Ui.click(player);
						cancel.run();
					}));
			this.setSlot(2, Menu.actions(Menu.button(Items.NAME_TAG, "Save", ChatFormatting.GREEN,
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
