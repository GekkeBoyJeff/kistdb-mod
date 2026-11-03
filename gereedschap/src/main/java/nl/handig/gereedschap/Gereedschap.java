package nl.handig.gereedschap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.handig.kern.Hud;
import nl.handig.kern.Module;
import nl.handig.kern.Setting;
import nl.handig.kern.Ui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gereedschap – een Handig-module:
 * automatisch je volgende tool pakken, een instelbare breekgrens (standaard mogen tools breken),
 * de durability in beeld tijdens minen/hakken/vechten, blokken en eten bijvullen, en waarschuwingen
 * voor bijna kapotte armor/elytra en een volle inventory.
 */
public final class Gereedschap implements ModInitializer {
	public static final Logger LOG = LoggerFactory.getLogger("Handig/Gereedschap");

	public static final Module MODULE = Module.register("gereedschap", "Gereedschap", Items.IRON_PICKAXE,
			"Je volgende tool automatisch pakken,", "durability in beeld en bijvullen.");
	public static final Setting.Toggle SWAP = MODULE.toggle("wissel", "Automatisch volgende tool", true, Items.IRON_PICKAXE,
			"Gaat je tool kapot (of bereikt hij je breekgrens),", "dan pak je de volgende uit je inventory.");
	public static final Setting.Choice KEEP = MODULE.choice("grens", "Breekgrens", Items.ANVIL, 0,
			new int[]{0, 1, 5, 10, 25, 50, 100},
			new String[]{"Uit – tools mogen breken", "Stop bij 1 over", "Stop bij 5 over", "Stop bij 10 over", "Stop bij 25 over", "Stop bij 50 over", "Stop bij 100 over"},
			"Tot hoe ver je een tool gebruikt.", "Bij de grens wissel je (of stopt het minen),", "zodat je dure tools kunt repareren.");
	public static final Setting.Toggle HUD = MODULE.toggle("hud", "Durability tijdens minen/hakken", true, Items.EXPERIENCE_BOTTLE,
			"Balk met de durability van je tool in de", "actiebalk zolang je mined, hakt of vecht.");
	public static final Setting.Toggle REFILL = MODULE.toggle("bijvullen", "Blokken en eten bijvullen", true, Items.BRICKS,
			"Is je stapel in je hand op, dan komt", "dezelfde stapel uit je inventory erin.");
	public static final Setting.Toggle ARMOR = MODULE.toggle("armor", "Waarschuwing armor en elytra", true, Items.ELYTRA,
			"Een melding als je armor of elytra", "bijna kapot is.");
	public static final Setting.Toggle FULL = MODULE.toggle("vol", "Melding: inventory vol", true, Items.CHEST,
			"Waarschuwing in de actiebalk als je", "inventory vol raakt tijdens het minen.");

	private record Watch(int slot, ItemStack item, int until) {
	}

	private static final class State {
		int hudUntil;
		int lastFullWarn = -1000;
		int lastKeepWarn = -1000;
		Watch watchMain;
		Watch watchOff;
		String brokenKind;
		ItemStack brokenRef = ItemStack.EMPTY;
		int brokenSlot = -1;
		final Map<EquipmentSlot, Integer> armorWarned = new HashMap<>();
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static int now;

	@Override
	public void onInitialize() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || sp.isSpectator() || sp.isCreative()) return InteractionResult.PASS;
			state(sp).hudUntil = now + 50;
			return guard(sp) ? InteractionResult.PASS : InteractionResult.FAIL;
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || sp.isCreative()) return true;
			return guard(sp);
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return;
			state(sp).hudUntil = now + 50;
			afterUse(sp);
			if (FULL.get(sp) && sp.getInventory().getFreeSlot() == -1) {
				State s = state(sp);
				if (now - s.lastFullWarn > 200) {
					s.lastFullWarn = now;
					Hud.show(sp, "gereedschap.vol", Component.literal("⚠ Je inventory is vol").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), Hud.PRIO_WARNING, 60);
					Ui.sound(sp, SoundEvents.NOTE_BLOCK_BASS, 0.5f, 0.7f);
				}
			}
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || sp.isSpectator() || sp.isCreative()) return InteractionResult.PASS;
			if (Tools.kind(sp.getMainHandItem()) == null) return InteractionResult.PASS;
			state(sp).hudUntil = now + 50;
			return guard(sp) ? InteractionResult.PASS : InteractionResult.FAIL;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || sp.isSpectator()) return InteractionResult.PASS;
			watchStack(sp, hand);
			if (hand == InteractionHand.MAIN_HAND && !sp.isCreative() && Tools.kind(sp.getMainHandItem()) != null && !guard(sp)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || sp.isSpectator()) return InteractionResult.PASS;
			watchStack(sp, hand);
			if (hand == InteractionHand.MAIN_HAND && !sp.isCreative() && Tools.kind(sp.getMainHandItem()) != null && !guard(sp)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Gereedschap::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUUID()));
		GereedschapTests.register();
		LOG.info("Handig Gereedschap geladen");
	}

	private static State state(ServerPlayer p) {
		return STATES.computeIfAbsent(p.getUUID(), k -> new State());
	}

	/**
	 * Mag de tool in je hand nog gebruikt worden? Zit hij op de breekgrens, dan wordt (als dat aan staat) gewisseld.
	 * @return false = actie blokkeren (geen vervanger en de grens is bereikt)
	 */
	static boolean guard(ServerPlayer p) {
		int keep = KEEP.get(p);
		ItemStack held = p.getMainHandItem();
		String kind = Tools.kind(held);
		if (keep <= 0 || kind == null || Tools.usable(held, keep)) return true;
		Inventory inv = p.getInventory();
		int sel = inv.getSelectedSlot();
		if (SWAP.get(p)) {
			int r = Tools.findReplacement(inv, kind, held, keep, sel);
			if (r >= 0) {
				Tools.swap(inv, sel, r);
				announceSwap(p, inv.getItem(sel), held);
				return true;
			}
		}
		State s = state(p);
		if (now - s.lastKeepWarn > 40) {
			s.lastKeepWarn = now;
			Hud.show(p, "gereedschap.grens", Component.literal("⚠ ").withStyle(ChatFormatting.RED)
					.append(held.getHoverName().copy().withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" heeft nog " + Tools.remaining(held) + " – breekgrens bereikt").withStyle(ChatFormatting.RED)), Hud.PRIO_WARNING, 50);
			Ui.sound(p, SoundEvents.NOTE_BLOCK_BASS, 0.5f, 0.6f);
		}
		return false;
	}

	/** Na een gebruik: staat de tool nu op de grens, dan alvast wisselen voor het volgende blok. */
	private static void afterUse(ServerPlayer p) {
		int keep = KEEP.get(p);
		ItemStack held = p.getMainHandItem();
		if (keep > 0 && Tools.kind(held) != null && !Tools.usable(held, keep) && SWAP.get(p)) {
			guard(p);
		}
	}

	/** Aangeroepen vanuit de mixin als een uitgeruste tool breekt. */
	public static void onBroken(ServerPlayer p, ItemStack broken, EquipmentSlot slot) {
		if (slot != EquipmentSlot.MAINHAND || p.isCreative()) return;
		String kind = Tools.kind(broken);
		if (kind == null) return;
		State s = state(p);
		s.brokenKind = kind;
		s.brokenRef = broken.copy();
		s.brokenSlot = p.getInventory().getSelectedSlot();
	}

	/** Na het breken (volgende tick): de volgende tool in de hand leggen. */
	static boolean restock(ServerPlayer p, String kind, ItemStack ref, int slot) {
		Inventory inv = p.getInventory();
		if (!inv.getItem(slot).isEmpty()) return false;
		int r = Tools.findReplacement(inv, kind, ref, KEEP.get(p), slot);
		if (r < 0) {
			Hud.show(p, "gereedschap.op", Component.literal("Je ").withStyle(ChatFormatting.GRAY)
					.append(ref.getHoverName().copy().withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" is kapot – geen vervanger in je inventory").withStyle(ChatFormatting.GRAY)), Hud.PRIO_WARNING, 60);
			return false;
		}
		Tools.swap(inv, slot, r);
		announceSwap(p, inv.getItem(slot), ref);
		return true;
	}

	private static void announceSwap(ServerPlayer p, ItemStack now, ItemStack old) {
		MutableComponent msg = Component.literal("↻ ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
				.append(now.getHoverName().copy().withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" gepakt (" + Tools.remaining(now) + " over)").withStyle(ChatFormatting.GRAY));
		Hud.show(p, "gereedschap.wissel", msg, Hud.PRIO_WARNING, 50);
		Ui.sound(p, SoundEvents.ARMOR_EQUIP_GENERIC, 0.6f, 1.3f);
	}

	/** Onthoud de stapel in de hand, zodat we hem kunnen bijvullen als hij op raakt (bouwen, eten, gooien). */
	private static void watchStack(ServerPlayer p, InteractionHand hand) {
		if (!REFILL.get(p) || p.isCreative()) return;
		ItemStack held = p.getItemInHand(hand);
		if (held.isEmpty() || !held.isStackable()) return;
		Inventory inv = p.getInventory();
		int slot = hand == InteractionHand.MAIN_HAND ? inv.getSelectedSlot() : Inventory.SLOT_OFFHAND;
		Watch w = new Watch(slot, held.copyWithCount(1), now + 45);
		if (hand == InteractionHand.MAIN_HAND) state(p).watchMain = w;
		else state(p).watchOff = w;
	}

	/** Vult een lege slot bij met dezelfde stapel. */
	static boolean refill(ServerPlayer p, int slot, ItemStack ref) {
		Inventory inv = p.getInventory();
		if (!inv.getItem(slot).isEmpty()) return false;
		int from = Tools.findRefill(inv, ref, slot);
		if (from < 0) return false;
		inv.setItem(slot, inv.getItem(from));
		inv.setItem(from, ItemStack.EMPTY);
		inv.setChanged();
		return true;
	}

	private static Watch checkWatch(ServerPlayer p, Watch w) {
		if (w == null) return null;
		if (now > w.until()) return null;
		ItemStack cur = p.getInventory().getItem(w.slot());
		if (cur.isEmpty()) {
			if (refill(p, w.slot(), w.item())) Ui.sound(p, SoundEvents.ITEM_PICKUP, 0.3f, 1.6f);
			return null;
		}
		if (!ItemStack.isSameItemSameComponents(cur, w.item())) return null;
		return w;
	}

	private static void tick(MinecraftServer server) {
		now = server.getTickCount();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			State s = STATES.get(p.getUUID());
			if (s != null) {
				if (s.brokenKind != null) {
					if (SWAP.get(p)) restock(p, s.brokenKind, s.brokenRef, s.brokenSlot);
					s.brokenKind = null;
				}
				s.watchMain = checkWatch(p, s.watchMain);
				s.watchOff = checkWatch(p, s.watchOff);
				if (now % 5 == 0 && s.hudUntil > now && HUD.get(p)) showDurability(p);
			}
			if (now % 40 == 0 && ARMOR.get(p)) checkArmor(p);
		}
	}

	/** "⛏ Diamanten houweel ▮▮▮▮▮▮▮▯▯▯ 1203/1561 (+2 reserve)" */
	private static void showDurability(ServerPlayer p) {
		ItemStack held = p.getMainHandItem();
		String kind = Tools.kind(held);
		if (kind == null) return;
		int max = held.getMaxDamage();
		int rem = Tools.remaining(held);
		int keep = KEEP.get(p);
		MutableComponent line = Component.literal(icon(kind) + " ").withStyle(ChatFormatting.GOLD)
				.append(held.getHoverName().copy().withStyle(ChatFormatting.WHITE))
				.append(Component.literal("  "))
				.append(Ui.bar((double) rem / max, 10))
				.append(Component.literal("  " + rem + "/" + max).withStyle(rem * 5 < max ? ChatFormatting.RED : ChatFormatting.GRAY));
		int spare = Tools.spares(p.getInventory(), kind, keep, p.getInventory().getSelectedSlot());
		if (spare > 0) line.append(Component.literal("  +" + spare + " reserve").withStyle(ChatFormatting.DARK_AQUA));
		if (keep > 0) line.append(Component.literal("  grens " + keep).withStyle(ChatFormatting.DARK_GRAY));
		Hud.show(p, "gereedschap.hud", line, Hud.PRIO_INFO, 25);
	}

	private static String icon(String kind) {
		return switch (kind) {
			case "pickaxe" -> "⛏";
			case "axe" -> "⚒";
			case "sword" -> "⚔";
			case "shovel", "hoe" -> "⚒";
			default -> "✦";
		};
	}

	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private static void checkArmor(ServerPlayer p) {
		State s = state(p);
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack st = p.getItemBySlot(slot);
			if (st.isEmpty() || !st.isDamageableItem()) {
				s.armorWarned.remove(slot);
				continue;
			}
			int rem = Tools.remaining(st);
			int limit = Math.max(10, st.getMaxDamage() / 20);
			if (rem > limit) {
				s.armorWarned.remove(slot);
				continue;
			}
			Integer warned = s.armorWarned.get(slot);
			// Opnieuw waarschuwen bij de helft van de vorige waarschuwing (bv. 20 → 10 → 5)
			if (warned != null && rem > warned / 2) continue;
			s.armorWarned.put(slot, rem);
			Component msg = Ui.prefix("Handig").append(Component.literal("⚠ Je ").withStyle(ChatFormatting.GOLD))
					.append(st.getHoverName().copy().withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" is bijna kapot: nog " + rem + " over").withStyle(ChatFormatting.GOLD));
			p.sendSystemMessage(msg);
			Hud.show(p, "gereedschap.armor", Component.literal("⚠ ").withStyle(ChatFormatting.GOLD).append(st.getHoverName().copy())
					.append(Component.literal(" bijna kapot (" + rem + ")").withStyle(ChatFormatting.GOLD)), Hud.PRIO_WARNING, 60);
			Ui.sound(p, SoundEvents.ITEM_BREAK, 0.5f, 1.4f);
		}
	}
}
