package nl.libreria.glint;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Window;
import nl.libreria.core.gui.Toast;

/**
 * The particles window (quick menu → Effects): how much you see, and where. Four switches, one Save. The individual
 * effects (flames yes, smoke no…) are one click further, in {@link EffectsGui}.
 */
final class EffectsWindow {
	private EffectsWindow() {
	}

	static void open(ServerPlayer p) {
		Window w = Window.of(nl.libreria.core.Domain.YOU, "Effects");
		w.item(sample(p), Ui.text("Particles from enchanted gear. Higher enchantment levels give more, up to your maximum.",
				ChatFormatting.GRAY));
		w.text(Ui.text("First person shows the item in your hand; your armour only shows in third person (F5), so it never "
				+ "fills your view.", ChatFormatting.DARK_GRAY));
		w.choice("intensity", Ui.text("Intensity", ChatFormatting.WHITE), List.of("1", "2", "3"),
				List.of(Ui.text("Subtle", ChatFormatting.AQUA), Ui.text("Normal", ChatFormatting.AQUA), Ui.text("Rich", ChatFormatting.AQUA)),
				Integer.toString(GlintMod.INTENSITY.get(p)));
		w.toggle("first", Ui.text("First person: your weapon", ChatFormatting.WHITE), GlintMod.AURA_FIRST.get(p));
		w.toggle("third", Ui.text("Third person: your armour", ChatFormatting.WHITE), GlintMod.AURA_THIRD.get(p));
		w.toggle("others", Ui.text("On other players", ChatFormatting.WHITE), GlintMod.AURA.get(p));
		w.button(Ui.text("✔ Save", ChatFormatting.GREEN), Ui.text("Keep these settings", ChatFormatting.GRAY), (pl, v) -> {
			save(pl, v);
			toast(pl);
			nl.libreria.core.Start.domain(pl, nl.libreria.core.Domain.YOU);
		});
		w.button(Ui.text("Choose effects ▸", ChatFormatting.AQUA), Ui.text("Flames yes, smoke no: per effect", ChatFormatting.GRAY),
				(pl, v) -> {
					if (save(pl, v)) toast(pl);
					EffectsGui.open(pl);
				});
		// Back (and Escape) keep what you changed too
		w.bottom(Ui.text("◀ Back", ChatFormatting.WHITE), (pl, v) -> {
			if (save(pl, v)) toast(pl);
			nl.libreria.core.Start.domain(pl, nl.libreria.core.Domain.YOU);
		}).show(p);
	}

	/** Stores what was filled in; true when something changed. */
	private static boolean save(ServerPlayer pl, nl.libreria.core.dialog.Values v) {
		int level;
		try {
			level = Math.max(1, Math.min(3, Integer.parseInt(v.text("intensity", Integer.toString(GlintMod.INTENSITY.get(pl))))));
		} catch (NumberFormatException e) {
			level = GlintMod.INTENSITY.get(pl);
		}
		boolean first = v.bool("first", GlintMod.AURA_FIRST.get(pl)), third = v.bool("third", GlintMod.AURA_THIRD.get(pl)),
				others = v.bool("others", GlintMod.AURA.get(pl));
		boolean changed = level != GlintMod.INTENSITY.get(pl) || first != GlintMod.AURA_FIRST.get(pl) || third != GlintMod.AURA_THIRD.get(pl)
				|| others != GlintMod.AURA.get(pl);
		GlintMod.INTENSITY.set(pl, level);
		GlintMod.AURA_FIRST.set(pl, first);
		GlintMod.AURA_THIRD.set(pl, third);
		GlintMod.AURA.set(pl, others);
		return changed;
	}

	private static void toast(ServerPlayer pl) {
		Ui.sound(pl, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
		Toast.show(pl, new ItemStack(Items.BLAZE_POWDER), Toast.GOOD, Ui.text("Effects saved", ChatFormatting.WHITE),
				Ui.text(GlintMod.INTENSITY.display(pl) + " · " + where(pl), ChatFormatting.GRAY));
	}

	/** "first and third person · others", for the toast. */
	private static String where(ServerPlayer p) {
		StringBuilder b = new StringBuilder();
		if (GlintMod.AURA_FIRST.get(p)) b.append("first person");
		if (GlintMod.AURA_THIRD.get(p)) b.append(b.isEmpty() ? "" : ", ").append("third person");
		if (GlintMod.AURA.get(p)) b.append(b.isEmpty() ? "" : ", ").append("others");
		return b.isEmpty() ? "no particles" : b.toString();
	}

	/** A Fire Aspect sword, to show what this is about. */
	private static ItemStack sample(ServerPlayer p) {
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("fire_aspect")))
				.ifPresent(fire -> EnchantmentHelper.updateEnchantments(sword, m -> m.set(fire, 2)));
		sword.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Fire Aspect II").withStyle(s -> s.withItalic(false)));
		return sword;
	}
}
