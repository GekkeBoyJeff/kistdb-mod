package nl.libreria.tools;

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
import nl.libreria.core.Hud;
import nl.libreria.core.Module;
import nl.libreria.core.Setting;
import nl.libreria.core.Ui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ToolsMod – een Libreria-module:
 * automatisch je volgende tool pakken, een instelbare breekgrens (standaard mogen tools breken),
 * de durability in beeld tijdens minen/hakken/vechten, blokken en eten bijvullen, en waarschuwingen
 * voor bijna kapotte armor/elytra en een volle inventory.
 */
public final class ToolsMod implements ModInitializer {
	public static final Logger LOG = LoggerFactory.getLogger("Libreria/Tools");

	public static final Module MODULE = Module.register("tools", "Tools", Items.IRON_PICKAXE,
			"Automatically grab your next tool,", "see durability and refill stacks.");
	public static final Setting.Toggle SWAP = MODULE.toggle("wissel", "Auto-swap to next tool", true, Items.IRON_PICKAXE,
			"When your tool breaks (or reaches your", "break limit), the next one from your", "inventory is put in your hand.");
	public static final Setting.Choice KEEP = MODULE.choice("grens", "Break limit", Items.ANVIL, 0,
			new int[]{0, 1, 5, 10, 25, 50, 100},
			new String[]{"Off – tools may break", "Stop at 1 left", "Stop at 5 left", "Stop at 10 left", "Stop at 25 left", "Stop at 50 left", "Stop at 100 left"},
			"How far you wear down a tool.", "At the limit you swap (or mining stops),", "so you can repair expensive tools.");
	public static final Setting.Toggle HUD = MODULE.toggle("hud", "Durability while mining/chopping", true, Items.EXPERIENCE_BOTTLE,
			"Shows your tool's durability bar in the", "action bar while you mine, chop or fight.");
	public static final Setting.Toggle REFILL = MODULE.toggle("bijvullen", "Refill blocks and food", true, Items.BRICKS,
			"When the stack in your hand runs out,", "the same stack from your inventory", "takes its place.");
	public static final Setting.Toggle ARMOR = MODULE.toggle("armor", "Armor and elytra warning", true, Items.ELYTRA,
			"A warning when your armor or elytra", "is about to break.");
	public static final Setting.Toggle FULL = MODULE.toggle("vol", "Alert: inventory full", true, Items.CHEST,
			"A warning in the action bar when your", "inventory fills up while mining.");

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
					Hud.show(sp, "tools.full", Component.literal("⚠ Your inventory is full").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), Hud.PRIO_WARNING, 60);
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
		ServerTickEvents.END_SERVER_TICK.register(ToolsMod::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUUID()));
		ToolsTests.register();
		LOG.info("Libreria Tools loaded");
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
			Hud.show(p, "tools.limit", Component.literal("⚠ ").withStyle(ChatFormatting.RED)
					.append(held.getHoverName().copy().withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" has " + Tools.remaining(held) + " left – break limit reached").withStyle(ChatFormatting.RED)), Hud.PRIO_WARNING, 50);
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
			Hud.show(p, "tools.out", Component.literal("Your ").withStyle(ChatFormatting.GRAY)
					.append(ref.getHoverName().copy().withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" broke – no replacement in your inventory").withStyle(ChatFormatting.GRAY)), Hud.PRIO_WARNING, 60);
			return false;
		}
		Tools.swap(inv, slot, r);
		announceSwap(p, inv.getItem(slot), ref);
		return true;
	}

	private static void announceSwap(ServerPlayer p, ItemStack now, ItemStack old) {
		MutableComponent msg = Component.literal("↻ ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
				.append(now.getHoverName().copy().withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" equipped (" + Tools.remaining(now) + " left)").withStyle(ChatFormatting.GRAY));
		Hud.show(p, "tools.swap", msg, Hud.PRIO_WARNING, 50);
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

	/** "⛏ Diamond Pickaxe ▮▮▮▮▮▮▮▯▯▯ 1203/1561 (+2 spare)" */
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
		if (spare > 0) line.append(Component.literal("  +" + spare + " spare").withStyle(ChatFormatting.DARK_AQUA));
		if (keep > 0) line.append(Component.literal("  limit " + keep).withStyle(ChatFormatting.DARK_GRAY));
		Hud.show(p, "tools.hud", line, Hud.PRIO_INFO, 25);
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
			Component msg = Ui.prefix("Tools").append(Component.literal("⚠ Your ").withStyle(ChatFormatting.GOLD))
					.append(st.getHoverName().copy().withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" is about to break: " + rem + " left").withStyle(ChatFormatting.GOLD));
			p.sendSystemMessage(msg);
			Hud.show(p, "tools.armor", Component.literal("⚠ ").withStyle(ChatFormatting.GOLD).append(st.getHoverName().copy())
					.append(Component.literal(" almost broken (" + rem + ")").withStyle(ChatFormatting.GOLD)), Hud.PRIO_WARNING, 60);
			Ui.sound(p, SoundEvents.ITEM_BREAK, 0.5f, 1.4f);
		}
	}
}
