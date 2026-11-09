package nl.libreria.waypoints;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Guide;
import nl.libreria.core.SelfTests;

/** Zelftests van pings en de sterfplek (alleen in de CI). */
final class WaypointsTests {
	private WaypointsTests() {
	}

	static void register() {
		SelfTests.register("waypoints.pings", level -> {
			FakePlayer a = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Pinger"));
			FakePlayer b = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Friend"));
			a.setPos(100.5, 80, 100.5);
			b.setPos(110.5, 80, 100.5);
			try {
				Vec3 spot = new Vec3(100.5, 80, 80.5);
				SelfTests.check(Pings.pingAt(a, level, spot, Component.literal("Oak Log")), "a ping");
				Pings.Ping ping = Pings.ACTIVE.get(a.getUUID());
				SelfTests.check(ping != null && ping.at().equals(spot) && "Pinger".equals(ping.ownerName()), "the ping is stored with who and where");
				SelfTests.check(PingMarks.has(a, a.getUUID()), "your own ping shows on the spot for you");
				PingMarks.show(b, ping);
				SelfTests.check(PingMarks.has(b, a.getUUID()) && PingMarks.lookOf(b, a.getUUID()) == WaypointsMod.PING_LOOK.get(),
						"and for someone else, in the server's look");
				for (int i = 0; i < 8; i++) PingMarks.tick(level.getServer());
				SelfTests.check(PingMarks.has(b, a.getUUID()), "it stays while the ping is up");
				int look = WaypointsMod.PING_LOOK.get();
				try {
					WaypointsMod.PING_LOOK.set(PingMarks.DIAMOND);
					SelfTests.check(PingMarks.lookOf(b, a.getUUID()) == PingMarks.DIAMOND && "A diamond".equals(WaypointsMod.PING_LOOK.display()),
							"another look in the server settings applies at once (" + PingMarks.lookOf(b, a.getUUID()) + ")");
					for (int i = 0; i < 8; i++) PingMarks.tick(level.getServer());
					WaypointsMod.PING_LOOK.set(PingMarks.PILLAR);
					SelfTests.check(PingMarks.lookOf(b, a.getUUID()) == PingMarks.PILLAR, "and back");
					WaypointsMod.PING_LOOK.set(7);
					SelfTests.check(WaypointsMod.PING_LOOK.get() == WaypointsMod.PING_LOOK.def, "an option that isn't there: the default");
				} finally {
					WaypointsMod.PING_LOOK.set(look);
				}
				SelfTests.check(!Pings.pingAt(a, level, spot.add(1, 0, 0), Component.literal("Stone")), "not twice in a moment");
				SelfTests.check(Pings.ACTIVE.get(a.getUUID()).at().equals(spot), "the first ping stays");
				SelfTests.check(Pings.show(b, a.getUUID()) && Guide.has(b), "someone else is shown the way to it");
				SelfTests.check(Pings.markers(b).stream().anyMatch(m -> m.name().equals("Pinger's ping")), "on the map for others");
				SelfTests.check("north".equals(Pings.direction(a, spot)), "the direction: north is -z (" + Pings.direction(a, spot) + ")");
				SelfTests.check("east".equals(Pings.direction(a, new Vec3(130.5, 80, 100.5))), "east is +x");
				Pings.clear(a.getUUID());
				SelfTests.check(!Pings.ACTIVE.containsKey(a.getUUID()) && !Pings.show(b, a.getUUID()), "cleared: gone for everyone");
				SelfTests.check(!PingMarks.has(a, a.getUUID()) && !PingMarks.has(b, a.getUUID()), "and off the spot");
				SelfTests.check(Pings.SECONDS == 30 && Pings.REACH >= 128, "30 seconds, far enough to ping a hill");
				// on top of a block; on the side of a trunk: in front of it, on the ground
				net.minecraft.core.BlockPos ground = new net.minecraft.core.BlockPos(140, 70, 140);
				level.setBlockAndUpdate(ground, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
				for (int i = 1; i <= 6; i++) level.setBlockAndUpdate(ground.above(i), i <= 4
						? net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(ground.north(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
				for (int i = 1; i <= 4; i++) level.setBlockAndUpdate(ground.north().above(i), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				Vec3 top = Pings.spotOn(level, ground.above(4), net.minecraft.core.Direction.UP);
				Vec3 side = Pings.spotOn(level, ground.above(2), net.minecraft.core.Direction.NORTH);
				SelfTests.check(top.equals(Vec3.atBottomCenterOf(ground.above(5))), "on top of the trunk (" + top + ")");
				SelfTests.check(side.equals(Vec3.atBottomCenterOf(ground.north().above())), "in front of its side, on the ground (" + side + ")");
			} finally {
				Guide.clear(a);
				Guide.clear(b);
				Pings.clear(a.getUUID());
				PingMarks.forgetViewer(a.getUUID());
				PingMarks.forgetViewer(b.getUUID());
			}
		});
		SelfTests.register("waypoints.death", level -> {
			FakePlayer p = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "Nobody"));
			SelfTests.check(WaypointsMod.deathPoint(p) == null && !WaypointsMod.goDeath(p), "no death point yet: nothing to show");
		});
	}
}
