package nl.libreria.info;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.Notify;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Window;

/**
 * Your own words on your gear: hold it, sneak and right-click an anvil, and write up to three lines that show in its
 * tooltip under your name. Costs a level, like renaming does. Writing nothing takes the description off again (free).
 */
final class Describe {
	static final int MAX = 120, OWN = 0xF2F2F2, BY = 0x6E6E6E;
	static final nl.libreria.core.Tips.Tip TIP = nl.libreria.core.Tips.register("describe", nl.libreria.core.Domain.YOU, Items.ANVIL,
			"Write on your gear", "Hold it, sneak and right-click an anvil:", "your own words in its tooltip, for a level.");

	private Describe() {
	}

	static void init() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!InfoMod.MODULE.on()) return net.minecraft.world.InteractionResult.PASS;
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer p)) return InteractionResult.PASS;
			if (!world.getBlockState(hit.getBlockPos()).is(BlockTags.ANVIL)) return InteractionResult.PASS;
			ItemStack held = p.getMainHandItem();
			if (held.isEmpty()) return InteractionResult.PASS;
			if (!p.isShiftKeyDown()) {
				nl.libreria.core.Tips.once(p, TIP);
				return InteractionResult.PASS;
			}
			open(p);
			return InteractionResult.SUCCESS;
		});
	}

	static void open(ServerPlayer p) {
		ItemStack held = p.getMainHandItem();
		String what = held.getHoverName().getString();
		String now = text(held);
		Window w = Window.of(Ui.text("Describe: " + what, ChatFormatting.WHITE));
		w.text(Ui.text("Up to three lines, under your name in its tooltip. It costs a level; writing nothing takes it off for free.",
				ChatFormatting.GRAY));
		w.area("text", Component.literal("Description"), now, MAX, 3);
		w.button(Ui.text("Write it", ChatFormatting.GREEN), Ui.text("Costs 1 level", ChatFormatting.GRAY), (pl, v) -> write(pl, held, v.text("text", "")));
		w.cancel();
		w.show(p);
	}

	/** What the owner wrote, as plain text again (to fill the box). */
	static String text(ItemStack s) {
		List<Component> lines = Lore.get(s, Lore.OWN);
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < lines.size() - 1; i++) b.append(i == 0 ? "" : " ").append(lines.get(i).getString());
		String t = b.toString().trim();
		if (t.startsWith("\"")) t = t.substring(1);
		if (t.endsWith("\"")) t = t.substring(0, t.length() - 1);
		return t;
	}

	/** Plain text only: no line breaks or control characters, spaces squeezed, at most {@link #MAX} characters. */
	static String clean(String raw) {
		StringBuilder b = new StringBuilder();
		for (char c : raw.toCharArray()) {
			if (Character.isISOControl(c) || c == '§') b.append(' ');
			else b.append(c);
		}
		String t = b.toString().replaceAll("\\s+", " ").trim();
		return t.length() > MAX ? t.substring(0, MAX).trim() : t;
	}

	static void write(ServerPlayer p, ItemStack was, String raw) {
		ItemStack held = p.getMainHandItem();
		if (held != was || held.isEmpty()) {
			Notify.warn(p, Items.ANVIL, "Not in your hand any more", "Hold the item you want to describe.");
			return;
		}
		String text = clean(raw);
		if (text.isEmpty()) {
			Lore.set(held, Lore.OWN, List.of());
			Notify.info(p, Items.ANVIL, "Description taken off", held.getHoverName().getString());
			return;
		}
		if (!p.isCreative() && p.experienceLevel < 1) {
			Notify.warn(p, Items.EXPERIENCE_BOTTLE, "You need a level", "Writing on gear costs 1 level.");
			return;
		}
		if (!p.isCreative()) p.giveExperienceLevels(-1);
		List<Component> lines = new ArrayList<>();
		List<String> wrapped = Lore.wrap("\"" + text + "\"", 40);
		for (String l : wrapped) lines.add(Lore.line(l, OWN, true));
		lines.add(Lore.line("  ~ " + p.getName().getString(), BY, false));
		Lore.set(held, Lore.OWN, lines);
		Ui.sound(p, SoundEvents.ANVIL_USE, 0.6f, 1.2f);
		Notify.info(p, Items.ANVIL, "Written on it", held.getHoverName().getString());
	}
}
