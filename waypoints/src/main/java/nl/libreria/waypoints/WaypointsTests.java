package nl.libreria.waypoints;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.MinecraftServer;
import nl.libreria.core.Guide;
import nl.libreria.core.SelfTests;
import nl.libreria.waypoints.WaypointsMod.Point;
import nl.libreria.waypoints.WaypointsMod.Visibility;

/** Zelftests van WaypointsMod (alleen in de CI). */
final class WaypointsTests {
	private WaypointsTests() {
	}

	static void register() {
		SelfTests.register("waypoints.list", level -> {
			FakePlayer p = FakePlayer.get(level);
			p.setPos(100.5, 80, 100.5);
			clear(p.getUUID());
			try {
				SelfTests.check(WaypointsMod.add(p, "Home"), "set waypoint");
				SelfTests.check(WaypointsMod.find(p, "home") != null, "lookup is case-insensitive");
				SelfTests.check(WaypointsMod.own(p).size() == 1, "1 waypoint");
				SelfTests.check(WaypointsMod.add(p, "Home"), "same name = update");
				SelfTests.check(WaypointsMod.own(p).size() == 1, "still 1 waypoint");
				SelfTests.check(!WaypointsMod.create(p, "HOME", false), "the menu refuses a name you already use");
				SelfTests.check(WaypointsMod.go(p, "Home") && Guide.has(p), "beam to the waypoint");
				Guide.clear(p);
				SelfTests.check(WaypointsMod.remove(p, "HOME"), "remove");
				SelfTests.check(WaypointsMod.own(p).isEmpty(), "empty");
				SelfTests.check(!WaypointsMod.add(p, "   "), "empty name rejected");
				SelfTests.check(!WaypointsMod.add(p, "x".repeat(WaypointsMod.MAX_NAME + 1)), "too long name rejected");

				Point camp = null;
				for (int i = 0; i < WaypointsMod.MAX_POINTS; i++) SelfTests.check(WaypointsMod.add(p, "P" + i), "waypoint " + i);
				SelfTests.check(!WaypointsMod.add(p, "One too many"), "at most " + WaypointsMod.MAX_POINTS + " per player");
				SelfTests.check(WaypointsMod.add(p, "P3"), "moving one still works at the maximum");
				SelfTests.check(WaypointsMod.own(p).size() == WaypointsMod.MAX_POINTS, "still at the maximum");
				clear(p.getUUID());

				SelfTests.check(WaypointsMod.add(p, "Base") && WaypointsMod.add(p, "Camp"), "two waypoints");
				camp = WaypointsMod.findOwn(p.getUUID(), "Camp");
				SelfTests.check(camp != null && !WaypointsMod.rename(p, camp, "base"), "rename: names are unique per player");
				SelfTests.check(WaypointsMod.rename(p, camp, "Camp 2") && "Camp 2".equals(camp.name), "rename");
			} finally {
				Guide.clear(p);
				clear(p.getUUID());
			}
		});

		SelfTests.register("waypoints.visibility", level -> {
			MinecraftServer server = level.getServer();
			FakePlayer owner = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Owner"));
			FakePlayer other = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Other"));
			FakePlayer friend = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Friend"));
			owner.setPos(10.5, 70, 10.5);
			other.setPos(20.5, 70, 20.5);
			friend.setPos(30.5, 70, 30.5);
			try {
				SelfTests.check(WaypointsMod.add(owner, "Base"), "owner sets a waypoint");
				Point pt = WaypointsMod.findOwn(owner.getUUID(), "Base");
				SelfTests.check(pt != null && pt.visibility == Visibility.EVERYONE, "new waypoints are visible to everyone");
				SelfTests.check(pt.ownedBy(owner.getUUID()) && "Owner".equals(pt.ownerName), "owner and owner name are stored");
				SelfTests.check(WaypointsMod.visible(other).contains(pt), "others see a public waypoint");
				SelfTests.check(WaypointsMod.find(other, "base") == pt, "others can find it by name");
				SelfTests.check(!WaypointsMod.remove(other, "Base") && WaypointsMod.exists(pt), "others can't remove it");
				SelfTests.check(!WaypointsMod.setVisibility(other, "Base", Visibility.ONLY_ME) && pt.visibility == Visibility.EVERYONE,
						"others can't change it");

				SelfTests.check(WaypointsMod.setVisibility(owner, "Base", Visibility.ONLY_ME), "make it private");
				SelfTests.check(WaypointsMod.visible(owner).contains(pt), "the owner still sees a private waypoint");
				SelfTests.check(!WaypointsMod.visible(other).contains(pt) && WaypointsMod.find(other, "Base") == null,
						"others don't see a private waypoint");
				SelfTests.check(!WaypointsMod.go(other, "Base") && !Guide.has(other), "others can't beam to a private waypoint");
				SelfTests.check("only you".equals(WaypointsMod.visibilityText(pt, owner.getUUID())), "text: only you");

				WaypointsMod.setVisibility(server, pt, Visibility.SELECTED);
				SelfTests.check(!WaypointsMod.visible(friend).contains(pt), "selected, but no one picked: hidden");
				WaypointsMod.select(server, pt, friend.getUUID(), "Friend", true);
				SelfTests.check(WaypointsMod.visible(friend).contains(pt), "a selected player sees it");
				SelfTests.check(!WaypointsMod.visible(other).contains(pt), "a player who isn't selected doesn't");
				SelfTests.check("1 player".equals(WaypointsMod.visibilityText(pt, owner.getUUID())), "text: 1 player");
				SelfTests.check(WaypointsMod.visible(owner).contains(pt), "the owner sees it too");
				WaypointsMod.select(server, pt, friend.getUUID(), "Friend", false);
				SelfTests.check(!WaypointsMod.visible(friend).contains(pt), "taken away again: hidden");

				// Same name, different owners: your own waypoint wins
				SelfTests.check(WaypointsMod.add(other, "Base"), "names are only unique per player");
				SelfTests.check(WaypointsMod.setVisibility(owner, "Base", Visibility.EVERYONE), "make it public");
				Point found = WaypointsMod.find(other, "BASE");
				SelfTests.check(found != null && found.ownedBy(other.getUUID()), "your own waypoint wins a name clash");
				SelfTests.check(WaypointsMod.find(friend, "base") != null, "a third player finds one of them");

				// Xaero's Minimap share line
				String line = WaypointsMod.xaeroLine(pt);
				SelfTests.check(line.startsWith("xaero-waypoint:Base:B:10:70:10:") && line.endsWith(":false:0:Internal-overworld-waypoints"),
						"xaero line: " + line);
				Point t = new Point();
				t.name = "a:b";
				t.dim = "minecraft:the_nether";
				String nether = WaypointsMod.xaeroLine(t);
				SelfTests.check(nether.startsWith("xaero-waypoint:a-b:A:0:0:0:") && nether.endsWith(":false:0:Internal-the-nether-waypoints"),
						"xaero line (nether, no colons in the name): " + nether);
				SelfTests.check("the-end".equals(WaypointsMod.xaeroDim("minecraft:the_end"))
						&& "sky-lands-deep".equals(WaypointsMod.xaeroDim("mymod:sky_lands/deep")), "xaero dimension names");
			} finally {
				Guide.clear(other);
				clear(owner.getUUID());
				clear(other.getUUID());
				clear(friend.getUUID());
			}
		});

		SelfTests.register("waypoints.migration", level -> {
			FakePlayer p = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Oldtimer"));
			FakePlayer other = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Newbie"));
			try {
				SelfTests.check(WaypointsMod.add(p, "Farm"), "a waypoint in the new list");
				// Old style: waypoints in the player's own file
				Point farm = new Point();
				farm.name = "Farm";
				farm.x = 5;
				farm.y = 64;
				farm.z = -7;
				Point mine = new Point();
				mine.name = "Mine";
				WaypointsMod.PlayerData pd = WaypointsMod.PLAYERS.get(p);
				pd.points = new ArrayList<>(List.of(farm, mine));

				WaypointsMod.migrate(p);
				SelfTests.check(pd.points == null, "old list cleared");
				Point moved = WaypointsMod.findOwn(p.getUUID(), "Mine");
				SelfTests.check(moved != null && moved.ownedBy(p.getUUID()) && "Oldtimer".equals(moved.ownerName), "moved, with owner");
				SelfTests.check(moved.visibility == Visibility.EVERYONE && !moved.id.isEmpty(), "moved: visible to everyone, with an id");
				SelfTests.check(WaypointsMod.visible(other).contains(moved), "others see migrated waypoints");
				Point renamed = WaypointsMod.findOwn(p.getUUID(), "Farm (2)");
				SelfTests.check(renamed != null && renamed.x == 5 && renamed.z == -7, "name clash: renamed to Farm (2)");
				SelfTests.check(WaypointsMod.own(p).size() == 3, "3 waypoints after moving");
				WaypointsMod.migrate(p);
				SelfTests.check(WaypointsMod.own(p).size() == 3, "migrating twice changes nothing");
				SelfTests.check(WaypointsMod.deathPoint(p) == null, "no death point mixed in");
			} finally {
				clear(p.getUUID());
			}
		});
	}

	/** Alle waypoints van een (nep)speler weg. */
	private static void clear(UUID owner) {
		WaypointsMod.points().removeIf(pt -> pt.ownedBy(owner));
		WaypointsMod.STORE.markDirty();
	}
}
