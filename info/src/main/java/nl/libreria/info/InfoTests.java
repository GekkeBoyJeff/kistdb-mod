package nl.libreria.info;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import nl.libreria.core.SelfTests;

/** Zelftests voor de InfoMod-module (draaien alleen met -Dlibreria.selftest=true). */
final class InfoTests {
	private InfoTests() {
	}

	static void register() {
		SelfTests.register("info.look-info", level -> {
			FakePlayer p = FakePlayer.get(level);
			BlockPos pos = new BlockPos(60, 120, 60);
			CropBlock wheat = (CropBlock) Blocks.WHEAT;
			level.setBlock(pos, wheat.getStateForAge(wheat.getMaxAge()), 2);
			String c = text(Look.describeBlock(p, level, pos, false));
			SelfTests.check(c.contains("ripe"), "ripe wheat: " + c);
			level.setBlock(pos, wheat.getStateForAge(3), 2);
			c = text(Look.describeBlock(p, level, pos, false));
			SelfTests.check(c.contains("growth"), "growing wheat");
			level.setBlock(pos, Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5), 2);
			c = text(Look.describeBlock(p, level, pos, false));
			SelfTests.check(c.contains("honey"), "full bee nest");
			level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
			SelfTests.check(Look.describeBlock(p, level, pos, false) == null, "plain stone: nothing in 'interesting'");
			SelfTests.check(Look.describeBlock(p, level, pos, true) != null, "plain stone: shown in 'everything'");
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		});
		SelfTests.register("info.compass-clock-horse", level -> {
			SelfTests.check(Held.direction(0).equals("South"), "yaw 0 = south");
			SelfTests.check(Held.direction(180).equals("North"), "yaw 180 = north");
			SelfTests.check(Held.direction(-90).equals("East"), "yaw -90 = east");
			SelfTests.check(Held.direction(135).equals("Northwest"), "yaw 135 = northwest");
			SelfTests.check(Held.clockText(0, false).getString().contains("06:00"), "tick 0 = 06:00");
			SelfTests.check(Held.clockText(18000, false).getString().contains("00:00"), "tick 18000 = midnight");
			SelfTests.check(Held.clockText(18000, false).getString().contains("sleep"), "you can sleep at night");
			SelfTests.check(!Held.clockText(6000, false).getString().contains("sleep"), "not at noon");
			double top = Horses.jumpHeight(1.0);
			SelfTests.check(top > 5.1 && top < 5.4, "max jump ~5.3 blocks: " + top);
			SelfTests.check(StatsGui.time(20L * 60 * 90).equals("1 h 30 min"), "time format");
			SelfTests.check(StatsGui.dist(250_000).equals("2.5 km"), "distance format: " + StatsGui.dist(250_000));
		});
	}

	/** All tooltip lines as one lower-case string ("" if there is no tooltip). */
	private static String text(java.util.List<Component> lines) {
		if (lines == null) return "";
		StringBuilder b = new StringBuilder();
		for (Component c : lines) b.append(c.getString()).append('\n');
		return b.toString().toLowerCase(java.util.Locale.ROOT);
	}
}
