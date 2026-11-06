package nl.libreria.info;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import nl.libreria.core.ItemView;
import nl.libreria.core.Setting;
import org.jetbrains.annotations.Nullable;

/**
 * What enchantments do, in the tooltip: under every enchantment a short line ("Every hit does 3 more damage"), and next
 * to the attack damage what the enchantments add to it, in yellow ("(+3)", "(+12.5 vs undead)"); an enchantment that
 * gives an attribute of its own (Depth Strider, Efficiency…) gets a line of its own there. Only in what the player's own
 * client sees (see {@link ItemView}): the items stay as they are. A setting per player, on by default.
 */
final class Details {
	static final Setting.Toggle ON = InfoMod.MODULE.toggle("enchant_details", "Enchantment details", true, Items.ENCHANTED_BOOK,
			"Under every enchantment in a tooltip,", "what it does; next to the attack", "damage, what they add to it.");
	/** Marks the tooltip lines that are only for one player's eyes (taken out again when creative mode sends an item back). */
	static final String MARK = "libreria:view";
	static final int EXPLAIN = 0x7A7A7A, BONUS = 0xFFFF55;
	private static final DecimalFormat N = new DecimalFormat("#.##");
	private static final Map<String, IntFunction<String>> WHAT = new HashMap<>();
	/** Enchantment → what it adds to the attack damage at a level ("+3", "+12.5 vs undead"). */
	private static final Map<String, IntFunction<String>> DAMAGE = new HashMap<>();

	private Details() {
	}

	private static String n(double v) {
		return N.format(v);
	}

	static {
		w("protection", l -> "All damage taken " + n(4 * l) + "% lower (most kinds).");
		w("fire_protection", l -> "Fire damage " + n(8 * l) + "% lower; you burn " + n(15 * l) + "% shorter.");
		w("blast_protection", l -> "Explosion damage " + n(8 * l) + "% lower; " + n(15 * l) + "% less knockback from them.");
		w("projectile_protection", l -> "Arrow and projectile damage " + n(8 * l) + "% lower.");
		w("feather_falling", l -> "Fall damage " + n(12 * l) + "% lower.");
		w("respiration", l -> "Your air underwater lasts about " + (l + 1) + " times longer.");
		w("aqua_affinity", l -> "Mine underwater as fast as on land.");
		w("thorns", l -> n(15 * l) + "% chance to hurt who hits you (1 to 5).");
		w("depth_strider", l -> l >= 3 ? "Walk as fast underwater as on land." : "Walk " + n(33.3 * l) + "% faster underwater.");
		w("frost_walker", l -> "Water under you freezes as you walk; safe on magma.");
		w("binding_curse", l -> "Can't be taken off until it breaks (or you die).");
		w("vanishing_curse", l -> "Gone when you die: it doesn't drop.");
		w("soul_speed", l -> "Faster on soul sand and soul soil.");
		w("swift_sneak", l -> "Sneak " + n((0.3 + 0.15 * l) / 0.3) + " times as fast.");
		w("sharpness", l -> "Every hit does " + n(0.5 * l + 0.5) + " more damage.");
		w("smite", l -> "Does " + n(2.5 * l) + " more damage to the undead.");
		w("bane_of_arthropods", l -> "Does " + n(2.5 * l) + " more to spiders and bugs, and slows them.");
		w("knockback", l -> "Knocks what you hit back further (+" + l + ").");
		w("fire_aspect", l -> "Sets what you hit on fire for " + (4 * l) + " seconds.");
		w("looting", l -> "Mobs drop more (up to " + l + " extra), rare drops more often.");
		w("sweeping_edge", l -> "Sweep attacks do " + n(100.0 * l / (l + 1)) + "% of the hit's damage.");
		w("efficiency", l -> "Mines faster (+" + (l * l + 1) + " mining speed).");
		w("silk_touch", l -> "Blocks drop themselves: glass, ores, grass…");
		w("unbreaking", l -> "Wears out about " + (l + 1) + " times slower (armour less).");
		w("fortune", l -> "More from ores and crops (up to " + (l + 1) + " times).");
		w("power", l -> "Arrows do " + (25 * (l + 1)) + "% more damage.");
		w("punch", l -> "Arrows knock back further (+" + l + ").");
		w("flame", l -> "Arrows set what they hit on fire.");
		w("infinity", l -> "Doesn't use up normal arrows (you need one).");
		w("luck_of_the_sea", l -> "More treasure and less junk when fishing.");
		w("lure", l -> "Fish bite " + (5 * l) + " seconds sooner.");
		w("loyalty", l -> "Comes back to you after a throw" + (l > 1 ? ", faster." : "."));
		w("impaling", l -> "Does " + n(2.5 * l) + " more damage to sea creatures.");
		w("riptide", l -> "Thrown in water or rain, it takes you along.");
		w("channeling", l -> "Calls down lightning on what it hits in a storm.");
		w("multishot", l -> "Shoots three arrows for the price of one.");
		w("quick_charge", l -> "Loads " + n(0.25 * l) + " seconds faster.");
		w("piercing", l -> "Arrows go through " + l + " more " + (l == 1 ? "target" : "targets") + ".");
		w("density", l -> "Hits from a fall do " + n(0.5 * l) + " more per block fallen.");
		w("breach", l -> "Ignores " + (15 * l) + "% of the target's armour.");
		w("wind_burst", l -> "A hit from a fall launches you up again.");
		w("lunge", l -> "A charged jab lunges you forward (costs hunger).");
		w("mending", l -> "XP you pick up repairs it first.");
		DAMAGE.put("sharpness", l -> "+" + n(0.5 * l + 0.5));
		DAMAGE.put("smite", l -> "+" + n(2.5 * l) + " vs undead");
		DAMAGE.put("bane_of_arthropods", l -> "+" + n(2.5 * l) + " vs bugs");
		DAMAGE.put("impaling", l -> "+" + n(2.5 * l) + " vs sea creatures");
		DAMAGE.put("density", l -> "+" + n(0.5 * l) + " per block fallen");
	}

	private static void w(String id, IntFunction<String> what) {
		WHAT.put(id, what);
	}

	/** What an enchantment does at a level, or null for one we don't know (from a data pack). */
	static @Nullable String what(Holder<Enchantment> h, int level) {
		String id = h.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		IntFunction<String> f = WHAT.get(id);
		return f == null ? null : f.apply(level);
	}

	static void init() {
		ItemView.add((viewer, menu, slot, stack) -> view(viewer, stack));
		ItemView.cleaner(Details::clean);
		ON.onChange(ItemView::resend);
	}

	private static MutableComponent ours() {
		return Component.empty().withStyle(Style.EMPTY.withItalic(false).withInsertion(MARK));
	}

	private static MutableComponent ours(Component c) {
		return ours().append(c);
	}

	private static MutableComponent explain(String text) {
		return ours(Component.literal("  " + text).withStyle(Style.EMPTY.withColor(EXPLAIN)));
	}

	/** The enchantments in the order the game shows them: the tooltip order first, then the rest. */
	static List<Object[]> ordered(ItemEnchantments ench, RegistryAccess access) {
		List<Object[]> out = new ArrayList<>();
		Set<Holder<Enchantment>> done = new LinkedHashSet<>();
		var tag = access.lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.TOOLTIP_ORDER);
		if (tag.isPresent()) {
			for (Holder<Enchantment> h : tag.get()) {
				int lv = ench.getLevel(h);
				if (lv > 0 && done.add(h)) out.add(new Object[] {h, lv});
			}
		}
		for (var e : ench.entrySet()) {
			if (done.add(e.getKey())) out.add(new Object[] {e.getKey(), e.getIntValue()});
		}
		return out;
	}

	/** The item as this player should see it: with the explanations, or as it is. */
	static ItemStack view(ServerPlayer viewer, ItemStack stack) {
		if (stack.isEmpty() || viewer.isCreative() || !ON.get(viewer)) return stack;
		TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
		if (display.hideTooltip()) return stack;
		ItemEnchantments ench = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		boolean doEnch = !ench.isEmpty() && display.shows(DataComponents.ENCHANTMENTS);
		boolean doStored = !stored.isEmpty() && display.shows(DataComponents.STORED_ENCHANTMENTS);
		if (!doEnch && !doStored) return stack;
		RegistryAccess access = viewer.level().registryAccess();
		List<Component> before = new ArrayList<>();
		for (ItemEnchantments list : new ItemEnchantments[] {doStored ? stored : ItemEnchantments.EMPTY, doEnch ? ench : ItemEnchantments.EMPTY}) {
			for (Object[] e : ordered(list, access)) {
				@SuppressWarnings("unchecked")
				Holder<Enchantment> h = (Holder<Enchantment>) e[0];
				int lv = (Integer) e[1];
				before.add(ours(Enchantment.getFullname(h, lv)));
				String what = what(h, lv);
				if (what != null) before.add(explain(what));
			}
		}
		List<Component> after = doEnch && display.shows(DataComponents.ATTRIBUTE_MODIFIERS) ? attributes(viewer, stack, ench, access) : null;
		ItemStack out = stack.copy();
		List<Component> lore = new ArrayList<>(before);
		lore.addAll(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
		if (after != null) lore.addAll(after);
		if (lore.size() > 250) lore = lore.subList(0, 250);
		out.set(DataComponents.LORE, new ItemLore(List.copyOf(lore)));
		TooltipDisplay hidden = display;
		if (doEnch) hidden = hidden.withHidden(DataComponents.ENCHANTMENTS, true);
		if (doStored) hidden = hidden.withHidden(DataComponents.STORED_ENCHANTMENTS, true);
		if (after != null) hidden = hidden.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true);
		out.set(DataComponents.TOOLTIP_DISPLAY, hidden);
		return out;
	}

	/**
	 * The "When in Main Hand:" part as the game writes it, with what the enchantments add: "(+3)" after the attack damage,
	 * and the attributes enchantments give ("+1 Water Movement Efficiency (Depth Strider)"). Null when there is nothing to
	 * add (then the game's own part stays).
	 */
	static @Nullable List<Component> attributes(ServerPlayer viewer, ItemStack stack, ItemEnchantments ench, RegistryAccess access) {
		List<String> damage = new ArrayList<>();
		for (Object[] e : ordered(ench, access)) {
			@SuppressWarnings("unchecked")
			Holder<Enchantment> h = (Holder<Enchantment>) e[0];
			IntFunction<String> f = DAMAGE.get(h.unwrapKey().map(k -> k.identifier().getPath()).orElse(""));
			if (f != null) damage.add(f.apply((Integer) e[1]));
		}
		// What enchantments add as attributes of their own, once each, with whose they are
		Map<String, Object[]> extra = new LinkedHashMap<>();
		for (EquipmentSlotGroup group : EquipmentSlotGroup.values()) {
			EnchantmentHelper.forEachModifier(stack, group, (attr, mod) -> {
				String key = attr.getRegisteredName() + "|" + mod.id();
				extra.putIfAbsent(key, new Object[] {attr, mod, group});
			});
		}
		if (damage.isEmpty() && extra.isEmpty()) return null;
		ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		List<Component> out = new ArrayList<>();
		boolean damageShown = false;
		EquipmentSlotGroup firstGroup = null;
		int firstEnd = -1;
		for (EquipmentSlotGroup group : EquipmentSlotGroup.values()) {
			List<Component> lines = new ArrayList<>();
			boolean[] added = {false};
			mods.forEach(group, (attr, mod, disp) -> {
				if (disp.type() == ItemAttributeModifiers.Display.hidden().type()) return;
				List<Component> mine = new ArrayList<>();
				disp.apply(mine::add, viewer, attr, mod);
				if (!damage.isEmpty() && !added[0] && attr.is(Attributes.ATTACK_DAMAGE) && mod.is(net.minecraft.resources.Identifier.withDefaultNamespace("base_attack_damage")) && !mine.isEmpty()) {
					Component last = mine.remove(mine.size() - 1);
					mine.add(Component.empty().append(last).append(Component.literal(" (" + String.join(", ", damage) + ")")
							.withStyle(Style.EMPTY.withColor(BONUS))));
					added[0] = true;
				}
				lines.addAll(mine);
			});
			if (lines.isEmpty()) continue;
			damageShown |= added[0];
			out.add(ours(CommonComponents.EMPTY));
			out.add(ours(Component.translatable("item.modifiers." + group.getSerializedName()).withStyle(ChatFormatting.GRAY)));
			for (Component c : lines) out.add(ours(c));
			if (firstGroup == null) {
				firstGroup = group;
				firstEnd = out.size();
			}
		}
		if (!extra.isEmpty()) {
			List<Component> lines = new ArrayList<>();
			for (Object[] x : extra.values()) {
				@SuppressWarnings("unchecked")
				Holder<Attribute> attr = (Holder<Attribute>) x[0];
				AttributeModifier mod = (AttributeModifier) x[1];
				List<Component> mine = new ArrayList<>();
				ItemAttributeModifiers.Display.attributeModifiers().apply(mine::add, viewer, attr, mod);
				String from = whose(mod, access);
				for (Component c : mine) {
					lines.add(ours(from == null ? c : Component.empty().append(c).append(Component.literal(" (" + from + ")")
							.withStyle(Style.EMPTY.withColor(BONUS)))));
				}
			}
			if (firstGroup == null) {
				EquipmentSlotGroup g = (EquipmentSlotGroup) extra.values().iterator().next()[2];
				out.add(ours(CommonComponents.EMPTY));
				out.add(ours(Component.translatable("item.modifiers." + g.getSerializedName()).withStyle(ChatFormatting.GRAY)));
				out.addAll(lines);
			} else {
				out.addAll(firstEnd, lines);
			}
		}
		if (!damageShown && extra.isEmpty()) return null;
		return out;
	}

	/** "Depth Strider" for the modifier "minecraft:enchantment.depth_strider/feet". */
	static @Nullable String whose(AttributeModifier mod, RegistryAccess access) {
		String path = mod.id().getPath();
		if (!path.startsWith("enchantment.")) return null;
		String id = path.substring("enchantment.".length());
		int slash = id.indexOf('/');
		if (slash >= 0) id = id.substring(0, slash);
		var key = net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, net.minecraft.resources.Identifier.fromNamespaceAndPath(
				mod.id().getNamespace(), id));
		return access.lookupOrThrow(Registries.ENCHANTMENT).get(key).map(h -> h.value().description().getString()).orElse(null);
	}

	/** An item a creative client sent back: our lines and hidden parts out again. */
	static ItemStack clean(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) return stack;
		List<Component> keep = new ArrayList<>();
		for (Component c : lore.lines()) if (!MARK.equals(c.getStyle().getInsertion())) keep.add(c);
		if (keep.size() == lore.lines().size()) return stack;
		ItemStack out = stack.copy();
		if (keep.isEmpty()) out.remove(DataComponents.LORE);
		else out.set(DataComponents.LORE, new ItemLore(keep));
		TooltipDisplay d = out.get(DataComponents.TOOLTIP_DISPLAY);
		if (d != null) {
			for (DataComponentType<?> t : List.<DataComponentType<?>>of(DataComponents.ENCHANTMENTS, DataComponents.STORED_ENCHANTMENTS,
					DataComponents.ATTRIBUTE_MODIFIERS)) d = d.withHidden(t, false);
			if (d.equals(TooltipDisplay.DEFAULT)) out.remove(DataComponents.TOOLTIP_DISPLAY);
			else out.set(DataComponents.TOOLTIP_DISPLAY, d);
		}
		return out;
	}
}
