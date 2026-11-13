package nl.libreria.waystones;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.PlaceList;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Menu;
import org.jetbrains.annotations.Nullable;

/**
 * Land &amp; places › Waystones: every waystone you found. Opened from a waystone (by right-clicking it), a click on
 * one travels there for a little XP, a right-click for an ender pearl; opened from the menu anywhere else, a click
 * shows the way (travelling starts at a waystone).
 */
final class WaystonePlaces {
	static final String KIND = "waystones";
	/** How near the waystone you opened the list from you have to stay to travel. */
	private static final double NEAR = 6;

	/** The waystone a player opened the list at. */
	private record At(String key, long since) {
	}

	private static final Map<UUID, At> AT = new HashMap<>();

	private WaystonePlaces() {
	}

	static void register() {
		PlaceList.kind(KIND, "Waystones", WaystonesMod.WAYSTONE_ITEM, 8)
				.about("Waystones you found. At a waystone:", "click one to travel there.")
				.empty("Right-click a waystone to find it.", "New villages have one by the bell;", "or craft your own.")
				.help("At a waystone, click one: travel there", "for a little XP (at most 1.5 levels).",
						"Right-click: free, for an ender pearl.", "From a grand waystone: always free.", "Waystone pearls: bound to one waystone,",
						"they take you there from anywhere.")
				.action(WaystonePlaces::settingsButton);
		PlaceList.addSource((p, out) -> {
			if (!WaystonesMod.MODULE.on()) return;
			Stones.Stone from = at(p);
			var animals = from != null ? nl.libreria.core.Leashed.along(p) : java.util.List.<net.minecraft.world.entity.Mob>of();
			int times = Stones.times(animals);
			String with = animals.isEmpty() ? "" : ", with " + nl.libreria.core.Leashed.words(animals);
			for (Stones.Stone s : Stones.knownBy(p)) {
				if (from != null && from.key().equals(s.key())) continue;
				PlaceList.Place pl = PlaceList.place(KIND, s.name, new ItemStack(s.grand ? WaystonesMod.HEART : WaystonesMod.WAYSTONE_ITEM), s.dim,
						new Vec3(s.x + 0.5, s.y, s.z + 0.5))
						.lore(about(p, s));
				if (from != null) {
					double levels = from.grand ? 0 : Stones.levels(p, s) * times;
					String price = p.isCreative() || levels <= 0 ? "travel there (free" + with + ")" : "travel there (" + Stones.levelsText(levels) + with + ")";
					pl.goAnywhere(price, q -> {
						AT.remove(q.getUUID());
						Stones.travel(q, s, false, from);
					});
					if (!p.isCreative() && levels > 0) pl.details(times == 1 ? "for an ender pearl (free)" : "for two ender pearls (free" + with + ")", q -> {
						q.closeContainer();
						AT.remove(q.getUUID());
						Stones.travel(q, s, true);
					});
				} else if (Stones.owns(p, s) || Ui.isOp(p)) {
					pl.details("name, who can use it", q -> WaystonesWindows.settings(q, s));
				} else if (Stones.mayShare(p, s)) {
					pl.details("on everyone's list or not", q -> WaystonesWindows.settings(q, s));
				}
				out.add(pl);
			}
		});
	}

	/** Who it belongs to, and whether it's open. */
	private static Component[] about(ServerPlayer p, Stones.Stone s) {
		Component whose = s.grand ? (Stones.owns(p, s) ? Ui.text("Your grand waystone", 0xC79BF0)
				: s.owner != null ? Ui.kv("A grand waystone by ", s.ownerName, ChatFormatting.WHITE) : Ui.text("A grand waystone", 0xC79BF0))
				: s.village ? Ui.text("A village's waystone", ChatFormatting.GRAY)
				: Stones.owns(p, s) ? Ui.text("Yours", 0xC79BF0)
				: s.owner != null ? Ui.kv("Made by ", s.ownerName, ChatFormatting.WHITE) : Ui.text("A waystone", ChatFormatting.GRAY);
		if (s.secret) return new Component[] {whose, Ui.text("Private: its owner and who they trust", ChatFormatting.GRAY)};
		if (s.shared) return new Component[] {whose, Ui.text("On everyone's list", ChatFormatting.GRAY)};
		return new Component[] {whose};
	}

	/** Right-clicked a waystone: the list, to travel from here. */
	static void travelFrom(ServerPlayer p, Stones.Stone s) {
		AT.put(p.getUUID(), new At(s.key(), System.currentTimeMillis()));
		PlaceList.open(p, KIND);
	}

	/** The waystone the player is at (opened the list from, and still standing by). */
	static @Nullable Stones.Stone at(ServerPlayer p) {
		At a = AT.get(p.getUUID());
		Stones.Stone s = a == null ? null : near(p, a);
		if (s == null) AT.remove(p.getUUID());
		return s;
	}

	private static @Nullable Stones.Stone near(ServerPlayer p, At a) {
		Stones.Stone s = Stones.byKey(a.key());
		if (s == null || !s.dim.equals(Stones.dim(p.level())) || p.position().distanceTo(Vec3.atBottomCenterOf(s.pos())) > NEAR) return null;
		return s;
	}

	/** Walked away from the waystone: travelling ends (until the next right-click on one). */
	static void tick(MinecraftServer server) {
		AT.entrySet().removeIf(e -> {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			return p == null || near(p, e.getValue()) == null;
		});
	}

	/** At your own waystone: its settings, from the list. */
	private static @Nullable GuiElementBuilder settingsButton(ServerPlayer p) {
		Stones.Stone s = at(p);
		if (s == null || !Stones.mayShare(p, s)) return null;
		boolean names = Stones.owns(p, s) || Ui.isOp(p);
		GuiElementBuilder b = Menu.button(Items.NAME_TAG, "Settings", ChatFormatting.WHITE, s.name,
				names ? "Its name, and who may use it." : "On everyone's list or not.");
		Menu.actions(b, "Click: change them");
		return b.setCallback((i, t, a, g) -> WaystonesWindows.settings(p, s));
	}
}
