package nl.libreria.glint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;

/**
 * Deeltjes rond spelers met betoverde spullen: vlammetjes bij een Fire Aspect-zwaard in de hand, sneeuw bij Frost
 * Walker-laarzen… Werkt met gewone clients (echte deeltjes). Elke kijker beslist zelf of hij ze ziet en welke.
 */
final class Aura {
	/** Familie → deeltje. Families zonder deeltje (Unbreaking, Mending) zijn te gewoon; dat wordt alleen maar druk. */
	static final Map<String, ParticleOptions> PARTICLES = Map.ofEntries(
			Map.entry("fire", ParticleTypes.SMALL_FLAME), Map.entry("lightning", ParticleTypes.ELECTRIC_SPARK),
			Map.entry("frost", ParticleTypes.SNOWFLAKE), Map.entry("soul", ParticleTypes.SOUL_FIRE_FLAME),
			Map.entry("holy", ParticleTypes.END_ROD), Map.entry("poison", ParticleTypes.ITEM_SLIME),
			Map.entry("tide", ParticleTypes.DOLPHIN), Map.entry("water", ParticleTypes.SPLASH),
			Map.entry("sculk", ParticleTypes.SCULK_SOUL), Map.entry("blast", ParticleTypes.SMOKE),
			Map.entry("sweep", ParticleTypes.CLOUD), Map.entry("feather", ParticleTypes.WHITE_ASH),
			Map.entry("loyalty", ParticleTypes.GLOW), Map.entry("sharp", ParticleTypes.CRIT),
			Map.entry("impale", ParticleTypes.NAUTILUS), Map.entry("heavy", ParticleTypes.REVERSE_PORTAL),
			Map.entry("breach", ParticleTypes.LAVA), Map.entry("power", ParticleTypes.ENCHANTED_HIT),
			Map.entry("multishot", ParticleTypes.FIREWORK), Map.entry("haste", ParticleTypes.WAX_OFF),
			Map.entry("wind", ParticleTypes.SMALL_GUST), Map.entry("infinity", ParticleTypes.WITCH),
			Map.entry("thorns", ParticleTypes.DAMAGE_INDICATOR), Map.entry("fireguard", ParticleTypes.FLAME),
			Map.entry("arrowguard", ParticleTypes.CRIT), Map.entry("protection", ParticleTypes.ENCHANT),
			Map.entry("silk", ParticleTypes.CHERRY_LEAVES), Map.entry("curse", ParticleTypes.SQUID_INK),
			Map.entry("luck", ParticleTypes.HAPPY_VILLAGER), Map.entry("sea", ParticleTypes.BUBBLE_POP));

	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
			EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** Hoogte (deel van de lichaamslengte) waar het deeltje per vak verschijnt. */
	private static final double[] HEIGHT = {0.55, 0.95, 0.7, 0.4, 0.05};

	private record Puff(String family, Vec3 pos) {
	}

	private Aura() {
	}

	static void tick(MinecraftServer server) {
		for (ServerPlayer wearer : server.getPlayerList().getPlayers()) {
			if (wearer.isSpectator() || wearer.isInvisible()) continue;
			List<Puff> puffs = puffs(wearer);
			if (puffs.isEmpty()) continue;
			ServerLevel level = wearer.level();
			for (ServerPlayer viewer : level.players()) {
				if (viewer.distanceToSqr(wearer) > 32 * 32 || !GlintMod.AURA.get(viewer)) continue;
				if (viewer == wearer && !GlintMod.AURA_SELF.get(viewer)) continue;
				for (Puff puff : puffs) {
					if (!GlintMod.enabled(viewer, puff.family())) continue;
					Vec3 v = puff.pos();
					level.sendParticles(viewer, PARTICLES.get(puff.family()), false, false, v.x, v.y, v.z, 1, 0.15, 0.1, 0.15, 0.01);
				}
			}
		}
	}

	/** Welke deeltjes deze speler nu uitstraalt: hooguit één per vak, en hooguit drie in totaal. */
	static List<Puff> puffs(ServerPlayer p) {
		List<Puff> out = new ArrayList<>();
		Vec3 base = p.position();
		double h = p.getBbHeight();
		// De hand zit een stukje voor en opzij van het lichaam
		Vec3 look = p.getViewVector(1f);
		Vec3 side = new Vec3(-look.z, 0, look.x).normalize().scale(0.35);
		for (int i = 0; i < SLOTS.length && out.size() < 3; i++) {
			ItemStack st = p.getItemBySlot(SLOTS[i]);
			String fam = familyOf(st);
			if (fam == null) continue;
			Vec3 pos = base.add(0, h * HEIGHT[i], 0);
			if (SLOTS[i] == EquipmentSlot.MAINHAND) pos = pos.add(look.x * 0.4, 0, look.z * 0.4).add(side);
			out.add(new Puff(fam, pos));
		}
		return out;
	}

	/** De meest opvallende familie op een item (zelfde volgorde als in het pakket). */
	static String familyOf(ItemStack st) {
		if (st.isEmpty()) return null;
		ItemEnchantments ench = st.get(DataComponents.ENCHANTMENTS);
		if (ench == null || ench.isEmpty()) return null;
		String best = null;
		int bestRank = Integer.MAX_VALUE;
		List<String> order = new ArrayList<>(GlintMod.FAMILIES.keySet());
		for (Holder<Enchantment> h : ench.keySet()) {
			String id = h.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
			String fam = GlintMod.BY_ENCHANT.get(id);
			if (fam == null || !PARTICLES.containsKey(fam)) continue;
			int rank = order.indexOf(fam);
			if (rank < bestRank) {
				best = fam;
				bestRank = rank;
			}
		}
		return best;
	}
}
