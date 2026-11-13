package nl.libreria.waystones;

import eu.pb4.polymer.core.api.item.PolymerItem;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import nl.libreria.core.Notify;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * A waystone pearl. Crafted it belongs to no waystone; used on a waystone the whole stack is bound to that one
 * (so different stacks go to different waystones). Right-click a bound pearl anywhere: it takes you there, free of
 * XP, and is used up.
 */
public final class PearlItem extends Item implements PolymerItem {
	static final String KEY = "libreria_waystone";

	PearlItem(Properties properties) {
		super(properties);
	}

	@Override
	public Item getPolymerItem(ItemStack stack, @Nullable PacketContext context) {
		return Items.PAPER;
	}

	/** The waystone this stack is bound to, or null. */
	static @Nullable String bound(ItemStack stack) {
		String k = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr(KEY, "");
		return k.isEmpty() ? null : k;
	}

	/** Binds the whole stack to the waystone at {@code lower}. False when it was bound to that one already. */
	static boolean bindAt(ServerPlayer p, ItemStack stack, ServerLevel level, BlockPos lower) {
		if (!WaystonesMod.MODULE.on()) return false;
		Stones.Stone s = Stones.ensure(level, lower, level.getBlockState(lower));
		if (s.key().equals(bound(stack))) {
			lore(stack, s);
			return false;
		}
		if (Stones.refused(p, s)) return true;
		Stones.discover(p, s);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(KEY, s.key()));
		lore(stack, s);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		level.playSound(null, lower, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 1.4f);
		int n = stack.getCount();
		Notify.done(p, WaystonesMod.PEARL, (n == 1 ? "Pearl" : n + " pearls") + " bound to " + s.name,
				"Right-click " + (n == 1 ? "it" : "one") + " anywhere to travel here.");
		return true;
	}

	/** "Goes to …" under the name, with the waystone's name as it is now. */
	static void lore(ItemStack stack, Stones.Stone s) {
		stack.set(DataComponents.LORE, new ItemLore(List.of(line("Goes to " + s.name, 0xC79BF0),
				line(Stones.world(s.dim) + " · " + s.x + " " + s.y + " " + s.z, 0x9AA0AC), line("Right-click: travel there", 0xFFE066))));
	}

	private static Component line(String s, int rgb) {
		return Ui.text(s, rgb).withStyle(st -> st.withItalic(false));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(player instanceof ServerPlayer p) || !WaystonesMod.MODULE.on()) return InteractionResult.PASS;
		String key = bound(stack);
		if (key == null) {
			Notify.info(p, WaystonesMod.PEARL, "Not bound to a waystone yet", "Right-click a waystone with these pearls to bind them to it.");
			return InteractionResult.FAIL;
		}
		if (!WaystonesMod.PEARLS.get()) {
			Notify.warn(p, WaystonesMod.PEARL, "Waystone pearls are off", "This server turned them off for now.");
			return InteractionResult.FAIL;
		}
		if (p.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
		Stones.Stone s = Stones.byKey(key);
		if (s == null) {
			Notify.warn(p, Items.BARRIER, "That waystone is gone", "Bind these pearls to another waystone.");
			return InteractionResult.FAIL;
		}
		ServerLevel to = Stones.check(p, s);
		if (to == null) return InteractionResult.FAIL;
		// With animals on your lead: two pearls
		var animals = nl.libreria.core.Leashed.along(p);
		int take = Stones.times(animals);
		if (!p.isCreative() && stack.getCount() < take) {
			Notify.info(p, WaystonesMod.PEARL, "Two pearls", "With " + nl.libreria.core.Leashed.words(animals) + " on your lead it takes two.");
			return InteractionResult.FAIL;
		}
		lore(stack, s);
		p.getCooldowns().addCooldown(stack, 20);
		if (!p.isCreative()) stack.shrink(take);
		Stones.go(p, to, s, animals);
		return InteractionResult.SUCCESS;
	}
}
