package nl.libreria.glint;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import nl.libreria.core.SelfTests;

/** Zelftests: families geladen, uit-pakketjes gehost, aan/uit per speler, deeltje per betovering. */
final class GlintTests {
	private GlintTests() {
	}

	static void register() {
		SelfTests.register("glans.pakket", level -> {
			SelfTests.check(GlintMod.assetsAdded, "assets aan het resourcepack toegevoegd");
			SelfTests.check(GlintMod.FAMILIES.size() >= 30, "30+ effect-families: " + GlintMod.FAMILIES.size());
			for (String e : new String[] {"fire_aspect", "unbreaking", "feather_falling", "loyalty", "riptide", "mending"}) {
				SelfTests.check(GlintMod.BY_ENCHANT.containsKey(e), e + " heeft een effect");
			}
			SelfTests.check(GlintMod.OFF_PACKS.containsKey("fire") && GlintMod.OFF_PACKS.containsKey("books"),
					"uit-pakketjes klaar: " + GlintMod.OFF_PACKS.keySet());
		});
		SelfTests.register("glans.aan-uit-en-deeltjes", level -> {
			FakePlayer p = FakePlayer.get(level);
			GlintMod.setEnabled(p, "fire", false);
			SelfTests.check(!GlintMod.enabled(p, "fire"), "vuur uit");
			GlintMod.setEnabled(p, "fire", true);
			SelfTests.check(GlintMod.enabled(p, "fire"), "vuur weer aan");
			ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
			var fire = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("fire_aspect")));
			EnchantmentHelper.updateEnchantments(sword, m -> m.set(fire, 2));
			SelfTests.check("fire".equals(Aura.familyOf(sword)), "Fire Aspect-zwaard = vlammen: " + Aura.familyOf(sword));
			SelfTests.check(Aura.familyOf(new ItemStack(Items.DIAMOND_SWORD)) == null, "zonder betovering: niets");
		});
	}
}
