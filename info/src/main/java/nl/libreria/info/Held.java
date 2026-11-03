package nl.libreria.info;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Guide;
import nl.libreria.core.Hud;
import nl.libreria.core.Ui;

/** Kompas, herstelkompas en klok in je hand geven echte informatie. */
final class Held {
	static final String CHANNEL = "info.hand";
	private static final String[] DIRECTIONS = {"Zuid", "Zuidwest", "West", "Noordwest", "Noord", "Noordoost", "Oost", "Zuidoost"};

	private Held() {
	}

	static void tick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			List<Component> parts = new ArrayList<>();
			if (!p.isSpectator()) {
				for (ItemStack stack : new ItemStack[] {p.getMainHandItem(), p.getOffhandItem()}) {
					if (InfoMod.COMPASS.get(p) && stack.is(Items.COMPASS)) parts.add(compass(p, stack));
					else if (InfoMod.COMPASS.get(p) && stack.is(Items.RECOVERY_COMPASS)) parts.add(recovery(p));
					else if (InfoMod.CLOCK.get(p) && stack.is(Items.CLOCK)) parts.add(clock(p.level()));
				}
			}
			if (parts.isEmpty()) Hud.hide(p, CHANNEL);
			else Hud.show(p, CHANNEL, Look.join(parts), Hud.PRIO_HELD, 15);
		}
	}

	/** Windrichting waar je naar kijkt (Minecraft: yaw 0 = zuid, 90 = west). */
	static String direction(float yaw) {
		return DIRECTIONS[Math.floorMod(Math.round(Mth.wrapDegrees(yaw) / 45f), 8)];
	}

	static Component compass(ServerPlayer p, ItemStack stack) {
		LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
		if (tracker != null) {
			Optional<GlobalPos> target = tracker.target();
			if (target.isEmpty()) return Component.literal("⌖ Magneetsteen kwijt").withStyle(ChatFormatting.RED);
			return distanceTo(p, target.get(), "⌖ Magneetsteen", ChatFormatting.LIGHT_PURPLE);
		}
		BlockPos pos = p.blockPosition();
		Component biome = p.level().getBiome(pos).unwrapKey()
				.map(k -> (Component) Component.translatable(languageKey("biome", k.identifier())))
				.orElse(Component.literal("?"));
		return Component.empty()
				.append(Component.literal("◎ " + direction(p.getYRot())).withStyle(ChatFormatting.GOLD))
				.append(Component.literal("  " + pos.getX() + " " + pos.getY() + " " + pos.getZ()).withStyle(ChatFormatting.WHITE))
				.append(Component.literal("  ").append(biome.copy().withStyle(ChatFormatting.GREEN)));
	}

	static String languageKey(String prefix, Identifier id) {
		return prefix + "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
	}

	static Component recovery(ServerPlayer p) {
		Optional<GlobalPos> death = p.getLastDeathLocation();
		if (death.isEmpty()) return Component.literal("☠ Nog geen sterfplek").withStyle(ChatFormatting.GRAY);
		return distanceTo(p, death.get(), "☠ Sterfplek", ChatFormatting.RED);
	}

	private static Component distanceTo(ServerPlayer p, GlobalPos target, String label, ChatFormatting color) {
		if (!target.dimension().equals(p.level().dimension())) {
			return Component.literal(label + " – andere dimensie").withStyle(color);
		}
		Vec3 t = Vec3.atCenterOf(target.pos());
		return Component.empty()
				.append(Component.literal(label + " ").withStyle(color))
				.append(Component.literal(Ui.meters(p.position().distanceTo(t)) + " " + Guide.arrow(p, t)).withStyle(ChatFormatting.WHITE));
	}

	static Component clock(ServerLevel level) {
		if (level.dimension() != Level.OVERWORLD) {
			return Component.literal("⌚ De klok draait hier dol").withStyle(ChatFormatting.DARK_PURPLE);
		}
		long t = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return clockText(t, level.isThundering());
	}

	/** Tijd van de dag als tekst; 0 ticks = 06:00. Slapen kan vanaf 12542 tot 23460 (of bij onweer). */
	static Component clockText(long t, boolean thunder) {
		int hours = (int) ((t / 1000 + 6) % 24);
		int minutes = (int) (t % 1000 * 60 / 1000);
		boolean night = t >= 12542 && t < 23460;
		String until = t < 12542 ? "nacht over " + realTime(12542 - t)
				: night ? "dag over " + realTime(24000 - t) : "bijna dag";
		var out = Component.empty()
				.append(Component.literal(String.format("%s %02d:%02d", night ? "☾" : "☀", hours, minutes)).withStyle(ChatFormatting.YELLOW))
				.append(Component.literal("  " + until).withStyle(ChatFormatting.GRAY));
		if (night || thunder) out.append(Component.literal("  ✔ je kunt slapen").withStyle(ChatFormatting.AQUA));
		return out;
	}

	private static String realTime(long ticks) {
		long s = ticks / 20;
		return s >= 60 ? (s / 60) + " min" : s + " s";
	}
}
