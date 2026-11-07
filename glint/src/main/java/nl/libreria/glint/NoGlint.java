package nl.libreria.glint;

import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import nl.libreria.core.ItemView;
import nl.libreria.core.Pack;

/**
 * A tool or weapon whose enchantments show as its effect has no enchantment glint: the effect takes its place. Without
 * shaders the item shader already leaves the glint out; a shader pack replaces that shader, so the glint came back. Now
 * the client is simply told the item has no glint ({@code enchantment_glint_override}), which every shader pack
 * respects: in your inventory and hand, and in other players' hands. Only in what the client sees; the item itself
 * stays as it is (and creative mode gets it back as it was).
 */
public final class NoGlint {
	/** These keep the glint: they have only a quiet mark of their own. */
	static final Set<String> QUIET = Set.of("unbreaking", "mending", "vanishing_curse");

	private NoGlint() {
	}

	/** A tool or weapon the effects draw on (the kinds in tools_fx.py). */
	static boolean tool(ItemStack s) {
		String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		if (id.endsWith("_sword") || id.endsWith("_axe") || id.endsWith("_pickaxe") || id.endsWith("_shovel") || id.endsWith("_hoe")
				|| id.endsWith("_spear")) {
			return true;
		}
		return switch (id) {
			case "shears", "bow", "crossbow", "trident", "mace", "fishing_rod" -> true;
			default -> false;
		};
	}

	/** Whether this item shows an effect instead of the glint. */
	static boolean loud(ItemStack s) {
		if (s.isEmpty() || !tool(s)) return false;
		ItemEnchantments ench = s.get(DataComponents.ENCHANTMENTS);
		if (ench == null || ench.isEmpty()) return false;
		for (var h : ench.keySet()) {
			String id = h.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
			if (!QUIET.contains(id)) return true;
		}
		return false;
	}

	/** The item as a client with the pack should see it. */
	public static ItemStack view(ItemStack s) {
		if (!loud(s) || s.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) return s;
		ItemStack out = s.copy();
		out.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
		return out;
	}

	/** An item a creative client sent back: the override we added taken out again. */
	static ItemStack clean(ItemStack s) {
		if (!Boolean.FALSE.equals(s.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) || !loud(s)) return s;
		ItemStack out = s.copy();
		out.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
		return out;
	}

	static void init() {
		ItemView.add((viewer, menu, slot, stack) -> !viewer.isCreative() && Pack.has(viewer) ? view(stack) : stack);
		ItemView.cleaner(NoGlint::clean);
	}
}
