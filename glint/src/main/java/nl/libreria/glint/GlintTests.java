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
			SelfTests.check(GlintMod.OFF_PACKS.containsKey("fire") && GlintMod.OFF_PACKS.containsKey("books"),
					"off-packs ready: " + GlintMod.OFF_PACKS.keySet());
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
			java.util.List<String> none = java.util.List.of();
			// A sword with lots on it just burns
			SelfTests.check(java.util.List.of("fire", "sharp", "luck").equals(sorted("sharp", "luck", "fire")), "most striking first");
			// Full Protection set: one orbit, no four clouds
			var protSet = java.util.List.of(java.util.List.of("protection"), java.util.List.of("protection", "thorns"),
					java.util.List.of("protection"), java.util.List.of("protection", "feather"));
			var mix = Aura.compose(new Aura.Gear(java.util.List.of("fire"), false, none, false, protSet), all);
			SelfTests.check(mix.size() == 2 && mix.get(1).kind() == Aura.Kind.SET && mix.get(1).family().equals("protection"),
					"sword + protection set = hand + orbit: " + mix);
			// Four different pieces: at most two armour effects, three sources in total
			var mixed = java.util.List.of(java.util.List.of("water"), java.util.List.of("blast"), java.util.List.of("thorns"),
					java.util.List.of("frost"));
			mix = Aura.compose(new Aura.Gear(java.util.List.of("fire"), false, java.util.List.of("holy"), false, mixed), all);
			SelfTests.check(mix.size() <= Aura.MAX_SOURCES, "never more than three: " + mix);
			SelfTests.check(mix.stream().filter(x -> x.kind() == Aura.Kind.ARMOUR).count() == 2, "two armour effects: " + mix);
			// The same effect twice counts once (sword and boots both frost)
			mix = Aura.compose(new Aura.Gear(java.util.List.of("frost"), false, none, false,
					java.util.List.of(none, none, none, java.util.List.of("frost"))), all);
			SelfTests.check(mix.size() == 1, "same effect once: " + mix);
			// An effect the viewer switched off gives way to the next one on that item
			mix = Aura.compose(new Aura.Gear(java.util.List.of("fire", "sharp"), false, none, false, java.util.List.of(none, none, none, none)),
					f -> !f.equals("fire"));
			SelfTests.check(mix.size() == 1 && mix.getFirst().family().equals("sharp"), "next effect takes over: " + mix);
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
