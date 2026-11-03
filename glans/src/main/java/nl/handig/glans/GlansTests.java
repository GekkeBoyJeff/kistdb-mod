package nl.handig.glans;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import nl.handig.kern.SelfTests;

/** Zelftests: families geladen, uit-pakketjes gehost, aan/uit per speler, deeltje per betovering. */
final class GlansTests {
	private GlansTests() {
	}

	static void register() {
		SelfTests.register("glans.pakket", level -> {
			SelfTests.check(Glans.assetsAdded, "assets aan het resourcepack toegevoegd");
			SelfTests.check(Glans.FAMILIES.size() >= 30, "30+ effect-families: " + Glans.FAMILIES.size());
			for (String e : new String[] {"fire_aspect", "unbreaking", "feather_falling", "loyalty", "riptide", "mending"}) {
				SelfTests.check(Glans.BY_ENCHANT.containsKey(e), e + " heeft een effect");
			}
			SelfTests.check(Glans.OFF_PACKS.containsKey("vuur") && Glans.OFF_PACKS.containsKey("boeken"),
					"uit-pakketjes klaar: " + Glans.OFF_PACKS.keySet());
		});
		SelfTests.register("glans.aan-uit-en-deeltjes", level -> {
			FakePlayer p = FakePlayer.get(level);
			Glans.setEnabled(p, "vuur", false);
			SelfTests.check(!Glans.enabled(p, "vuur"), "vuur uit");
			Glans.setEnabled(p, "vuur", true);
			SelfTests.check(Glans.enabled(p, "vuur"), "vuur weer aan");
			ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
			var fire = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("fire_aspect")));
			EnchantmentHelper.updateEnchantments(sword, m -> m.set(fire, 2));
			SelfTests.check("vuur".equals(Aura.familyOf(sword)), "Fire Aspect-zwaard = vlammen: " + Aura.familyOf(sword));
			SelfTests.check(Aura.familyOf(new ItemStack(Items.DIAMOND_SWORD)) == null, "zonder betovering: niets");
		});
	}
}
