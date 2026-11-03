package nl.handig.info;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import nl.handig.kern.SelfTests;

/** Zelftests voor de Info-module (draaien alleen met -Dhandig.selftest=true). */
final class InfoTests {
	private InfoTests() {
	}

	static void register() {
		SelfTests.register("info.kijk-info", level -> {
			FakePlayer p = FakePlayer.get(level);
			BlockPos pos = new BlockPos(60, 120, 60);
			CropBlock wheat = (CropBlock) Blocks.WHEAT;
			level.setBlock(pos, wheat.getStateForAge(wheat.getMaxAge()), 2);
			Component c = Look.describeBlock(p, level, pos, false);
			SelfTests.check(c != null && c.getString().contains("rijp"), "rijpe tarwe: " + (c == null ? "null" : c.getString()));
			level.setBlock(pos, wheat.getStateForAge(3), 2);
			c = Look.describeBlock(p, level, pos, false);
			SelfTests.check(c != null && c.getString().contains("groei"), "groeiende tarwe");
			level.setBlock(pos, Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5), 2);
			c = Look.describeBlock(p, level, pos, false);
			SelfTests.check(c != null && c.getString().contains("honing"), "vol bijennest");
			level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
			SelfTests.check(Look.describeBlock(p, level, pos, false) == null, "gewone steen: niets in 'interessant'");
			SelfTests.check(Look.describeBlock(p, level, pos, true) != null, "gewone steen: wel in 'alles'");
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		});
		SelfTests.register("info.kompas-klok-paard", level -> {
			SelfTests.check(Held.direction(0).equals("Zuid"), "yaw 0 = zuid");
			SelfTests.check(Held.direction(180).equals("Noord"), "yaw 180 = noord");
			SelfTests.check(Held.direction(-90).equals("Oost"), "yaw -90 = oost");
			SelfTests.check(Held.direction(135).equals("Noordwest"), "yaw 135 = noordwest");
			SelfTests.check(Held.clockText(0, false).getString().contains("06:00"), "tick 0 = 06:00");
			SelfTests.check(Held.clockText(18000, false).getString().contains("00:00"), "tick 18000 = middernacht");
			SelfTests.check(Held.clockText(18000, false).getString().contains("slapen"), "'s nachts kun je slapen");
			SelfTests.check(!Held.clockText(6000, false).getString().contains("slapen"), "'s middags niet");
			double top = Horses.jumpHeight(1.0);
			SelfTests.check(top > 5.1 && top < 5.4, "maximale sprong ~5,3 blokken: " + top);
			SelfTests.check(StatsGui.time(20L * 60 * 90).equals("1 u 30 min"), "tijdnotatie");
			SelfTests.check(StatsGui.dist(250_000).equals("2,5 km"), "afstandnotatie: " + StatsGui.dist(250_000));
		});
	}
}
