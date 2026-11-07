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

	private static Aura.Effect e(String family, int strength) {
		return new Aura.Effect(family, strength);
	}

	private static java.util.List<Aura.Effect> fx(String family, int strength) {
		return java.util.List.of(e(family, strength));
	}

	private static java.util.List<String> sorted(String... fams) {
		java.util.List<String> l = new java.util.ArrayList<>(java.util.List.of(fams));
		l.sort((a, b) -> Integer.compare(Aura.rank(a), Aura.rank(b)));
		return l;
	}

	static void register() {
		SelfTests.register("glint.pack", level -> {
			SelfTests.check(GlintMod.assetsAdded, "assets added to the resource pack");
			SelfTests.check(GlintMod.FAMILIES.size() >= 30, "30+ effect families: " + GlintMod.FAMILIES.size());
			for (String e : new String[] {"fire_aspect", "unbreaking", "feather_falling", "loyalty", "riptide", "mending"}) {
				SelfTests.check(GlintMod.BY_ENCHANT.containsKey(e), e + " has an effect");
			}
			SelfTests.check(GlintMod.OFF_PACKS.containsKey("fire") && GlintMod.OFF_PACKS.containsKey("books") && GlintMod.OFF_PACKS.containsKey("glass"),
					"off-packs ready: " + GlintMod.OFF_PACKS.keySet());
			// Glow maps for shader packs (LabPBR) next to the effect textures, kept out of the item atlas
			var mod = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("libreria_glint").orElseThrow();
			SelfTests.check(mod.findPath("assets/libreria/textures/item/fx/sword/diamond/sword/fire2_glow_s.png").isPresent()
					&& mod.findPath("assets/libreria/textures/entity/equipment/humanoid/fx/diamond_fireguard_1_s.png").isPresent(), "glow maps in the pack");
			SelfTests.check(mod.findPath("assets/minecraft/atlases/items.json").isPresent(), "glow maps kept out of the item atlas");
		});
		SelfTests.register("glint.toggle-and-particles", level -> {
			FakePlayer p = FakePlayer.get(level);
			GlintMod.setEnabled(p, "fire", false);
			SelfTests.check(!GlintMod.enabled(p, "fire"), "fire off");
			GlintMod.setEnabled(p, "fire", true);
			SelfTests.check(GlintMod.enabled(p, "fire"), "fire back on");
			ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
			var fire = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("fire_aspect")));
			EnchantmentHelper.updateEnchantments(sword, m -> m.set(fire, 2));
			SelfTests.check("fire".equals(Aura.familyOf(sword)), "Fire Aspect sword = flames: " + Aura.familyOf(sword));
			SelfTests.check(Aura.familyOf(new ItemStack(Items.DIAMOND_SWORD)) == null, "no enchantment: nothing");
		});
		SelfTests.register("glint.calm-mix", level -> {
			java.util.function.Predicate<String> all = f -> true;
			java.util.List<Aura.Effect> none = java.util.List.of();
			// A sword with lots on it just burns
			SelfTests.check(java.util.List.of("fire", "sharp", "luck").equals(sorted("sharp", "luck", "fire")), "most striking first");
			// Full Protection set: one orbit, no four clouds
			var protSet = java.util.List.of(fx("protection", 3), java.util.List.of(e("protection", 3), e("thorns", 2)),
					fx("protection", 2), java.util.List.of(e("protection", 3), e("feather", 3)));
			var mix = Aura.compose(new Aura.Gear(fx("fire", 3), false, none, false, protSet), all, 3);
			SelfTests.check(mix.size() == 2 && mix.get(1).kind() == Aura.Kind.SET && mix.get(1).family().equals("protection"),
					"sword + protection set = hand + orbit: " + mix);
			SelfTests.check(mix.get(1).strength() == 2, "a set is as strong as its weakest piece: " + mix);
			// Four different pieces: at most two armour effects, three sources in total
			var mixed = java.util.List.of(fx("water", 2), fx("blast", 2), fx("thorns", 2), fx("frost", 2));
			mix = Aura.compose(new Aura.Gear(fx("fire", 2), false, fx("holy", 2), false, mixed), all, 3);
			SelfTests.check(mix.size() <= Aura.MAX_SOURCES, "never more than three: " + mix);
			SelfTests.check(mix.stream().filter(x -> x.kind() == Aura.Kind.ARMOUR).count() == 2, "two armour effects: " + mix);
			// The same effect twice counts once (sword and boots both frost)
			mix = Aura.compose(new Aura.Gear(fx("frost", 2), false, none, false, java.util.List.of(none, none, none, fx("frost", 2))), all, 3);
			SelfTests.check(mix.size() == 1, "same effect once: " + mix);
			// An effect the viewer switched off gives way to the next one on that item
			mix = Aura.compose(new Aura.Gear(java.util.List.of(e("fire", 2), e("sharp", 2)), false, none, false,
					java.util.List.of(none, none, none, none)), f -> !f.equals("fire"), 3);
			SelfTests.check(mix.size() == 1 && mix.getFirst().family().equals("sharp"), "next effect takes over: " + mix);
			// Higher levels give more, the viewer's maximum caps it
			SelfTests.check(Aura.strength(1, 5) == 1 && Aura.strength(3, 5) == 2 && Aura.strength(5, 5) == 3 && Aura.strength(1, 1) == 2,
					"level → strength");
			var rich = Aura.compose(new Aura.Gear(fx("fire", 3), false, none, false, java.util.List.of(none, none, none, none)), all, 3);
			var weak = Aura.compose(new Aura.Gear(fx("fire", 1), false, none, false, java.util.List.of(none, none, none, none)), all, 3);
			var capped = Aura.compose(new Aura.Gear(fx("fire", 3), false, none, false, java.util.List.of(none, none, none, none)), all, 1);
			SelfTests.check(rich.getFirst().every() < weak.getFirst().every(), "Fire Aspect II more than I: " + rich + " " + weak);
			SelfTests.check(capped.getFirst().every() == weak.getFirst().every(), "Subtle caps it: " + capped);
			// Your own armour sits behind your camera
			var eye = new net.minecraft.world.phys.Vec3(0, 1.62, 0);
			var look = new net.minecraft.world.phys.Vec3(0, -0.8, 0.6).normalize();
			var p = Aura.behindCamera(eye, look, new net.minecraft.world.phys.Vec3(0, 0.1, 0));
			SelfTests.check(p.subtract(eye).dot(look) <= -Aura.BEHIND + 1e-6, "armour behind the camera, also looking down");
			// Enchanted books count with their stored enchantment
			ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
			var frost = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("frost_walker")));
			book.set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,
					net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
			EnchantmentHelper.updateEnchantments(book, m -> m.set(frost, 1));
			SelfTests.check("frost".equals(Aura.familyOf(book)), "enchanted book = its effect: " + Aura.familyOf(book));
		});
	}
}
