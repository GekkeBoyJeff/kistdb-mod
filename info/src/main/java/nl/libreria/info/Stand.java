package nl.libreria.info;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.gui.Glyphs;
import org.jetbrains.annotations.Nullable;

/**
 * The look card of an armour stand: the stories of everything it wears, read from the gear itself (so a set put on show
 * tells its whole story, like a player's card tells how far they got). An invisible stand counts too, as long as it
 * wears something.
 */
final class Stand {
	/** Top to bottom, then what it holds. */
	static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
			EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

	private Stand() {
	}

	static boolean dressed(ArmorStand st) {
		for (EquipmentSlot slot : SLOTS) if (!st.getItemBySlot(slot).isEmpty()) return true;
		return false;
	}

	/** Has words of its own: a found story, or what a player wrote on it. */
	static boolean told(ItemStack s) {
		return !Lore.get(s, Lore.FOUND).isEmpty() || !Lore.get(s, Lore.OWN).isEmpty();
	}

	static @Nullable Look.Card card(ArmorStand st, boolean all) {
		List<ItemStack> items = new ArrayList<>();
		for (EquipmentSlot slot : SLOTS) items.add(st.getItemBySlot(slot));
		Component own = st.hasCustomName() ? st.getCustomName() : null;
		Look.Card card = card(items, own, "Armour stand", new ItemStack(Items.ARMOR_STAND), st.level().registryAccess());
		if (card != null || !all) return card;
		return new Look.Card(new ItemStack(Items.ARMOR_STAND), -1,
				Component.literal(st.getDisplayName().getString()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
				Component.literal(dressed(st) ? "Nothing on it has a story" : "Entity").withStyle(ChatFormatting.GRAY), List.of());
	}

	/**
	 * The card of whatever shows things off (an armour stand, a mannequin, a pedestal): with one story on it, its title,
	 * how many of its parts are there, what the story is about and the names of its parts ("???" for the missing ones);
	 * with more, a line per story. Never the words of each thing: those are for its book. Null when nothing on it has
	 * words of its own.
	 */
	static @Nullable Look.Card card(List<ItemStack> items, @Nullable Component own, String kind, ItemStack icon,
			net.minecraft.core.RegistryAccess access) {
		Map<String, Set<Integer>> stories = new LinkedHashMap<>();
		List<ItemStack> loose = new ArrayList<>();
		for (ItemStack s : items) {
			if (s.isEmpty()) continue;
			// (a part stamped earlier gets its right text first)
			if (Found.has(s, Found.STAMP)) Found.fix(null, s, access);
			CompoundTag stamp = stamp(s);
			String story = stamp.getStringOr("story", "");
			if (!story.isEmpty() && Found.STORIES.containsKey(story)) {
				stories.computeIfAbsent(story, k -> new java.util.TreeSet<>()).add(stamp.getIntOr("part", 0));
			} else if (told(s)) {
				loose.add(s);
			}
		}
		if (stories.isEmpty() && loose.isEmpty()) return null;
		int budget = Math.max(6, Glyphs.maxPanel - 3);
		List<Look.Row> rows = new ArrayList<>();
		if (stories.size() == 1 && loose.isEmpty()) {
			var e = stories.entrySet().iterator().next();
			Found.Text info = Found.STORIES.get(e.getKey());
			Component name = Component.literal("✦ " + info.title()).withStyle(st -> st.withColor(Found.HEAD).withBold(true));
			Component sub = Component.literal((own == null ? "" : own.getString() + " · ") + e.getValue().size() + " of " + info.parts() + " parts")
					.withStyle(ChatFormatting.GRAY);
			List<Look.Row> names = names(e.getKey(), e.getValue(), info.parts());
			String intro = Found.INTROS.get(e.getKey());
			if (intro != null) {
				List<String> lines = Lore.wrap(intro, 44);
				int room = Math.max(2, budget - names.size() - 1);
				if (lines.size() > room) {
					lines = new ArrayList<>(lines.subList(0, room));
					lines.set(room - 1, lines.get(room - 1).replaceAll("[\\s,.;:!?]+$", "") + "…");
				}
				for (String l : lines) rows.add(Look.Row.text(Look.INDENT, " ", "", Lore.line(l, Found.TEXT, true)));
			}
			rows.addAll(names);
			return new Look.Card(icon, Found.HEAD, name, sub, rows.size() > budget ? new ArrayList<>(rows.subList(0, budget)) : rows);
		}
		Component name = own != null ? own.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
				: Component.literal(kind).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
		int things = loose.size();
		for (Set<Integer> parts : stories.values()) things += parts.size();
		Component sub = Component.literal((stories.isEmpty() ? "" : stories.size() + (stories.size() == 1 ? " story · " : " stories · "))
				+ things + (things == 1 ? " thing with a story" : " things with a story")).withStyle(ChatFormatting.GRAY);
		for (var e : stories.entrySet()) {
			Found.Text info = Found.STORIES.get(e.getKey());
			rows.add(Look.Row.text("star", "✦", "", Component.literal(info.title()).withStyle(st -> st.withColor(Found.HEAD))
					.append(Component.literal("  " + e.getValue().size() + " of " + info.parts()).withStyle(ChatFormatting.GRAY))));
		}
		for (ItemStack s : loose) {
			MutableComponent head = s.getHoverName().copy();
			if (head.getStyle().getColor() == null) head = head.withStyle(ChatFormatting.WHITE);
			rows.add(Look.Row.text("chest", "▪", "", Component.empty().append(head.withStyle(st -> st.withItalic(false)))));
		}
		return new Look.Card(icon, Found.HEAD, name, sub, rows.size() > budget ? new ArrayList<>(rows.subList(0, budget)) : rows);
	}

	/** The names of a story's parts, a few to a row: the ones here in gold, "???" for the others. */
	static List<Look.Row> names(String story, Set<Integer> here, int count) {
		List<Look.Row> out = new ArrayList<>();
		Map<Integer, String> byPart = new java.util.HashMap<>();
		for (Found.Text t : Found.PIECES) if (story.equals(t.story())) byPart.put(t.part(), t.name());
		MutableComponent row = Component.empty();
		int width = 0;
		for (int i = 1; i <= count; i++) {
			boolean has = here.contains(i);
			String n = has ? byPart.getOrDefault(i, "?") : "???";
			if (width > 0 && width + n.length() + 3 > 44) {
				out.add(Look.Row.text("star", "✦", "", row));
				row = Component.empty();
				width = 0;
			}
			if (width > 0) row.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
			row.append(Component.literal(n).withStyle(has ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
			width += n.length() + 3;
		}
		if (width > 0) out.add(Look.Row.text("star", "✦", "", row));
		return out;
	}

	/** The found story of an item, wrapped again; from what's written on it if the text itself is gone. */
	static List<String> text(ItemStack s, int wrap) {
		CompoundTag stamp = stamp(s);
		String id = stamp.getStringOr("id", "");
		Found.Text t = id.isEmpty() ? null : Found.byId(id);
		if (t != null) {
			String kind = Found.kindOf(s);
			return Lore.wrap(t.text().replace("{item}", kind == null ? "thing" : Found.noun(kind, s)), wrap);
		}
		List<Component> lines = Lore.get(s, Lore.FOUND);
		List<String> out = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			String l = lines.get(i).getString();
			if (i == 0 && l.startsWith("✦ ")) continue;
			if (i == lines.size() - 1 && l.contains(" · day ")) continue;
			out.add(l);
		}
		return out;
	}

	static CompoundTag stamp(ItemStack s) {
		CustomData cd = s.get(DataComponents.CUSTOM_DATA);
		return cd == null ? new CompoundTag() : cd.copyTag().getCompoundOrEmpty(Found.STAMP);
	}

	/**
	 * Where the card goes: always beside the stand (the card is tall), its middle at the stand's middle but never into
	 * the floor and never under the hotbar, pulled in front of the stand (its arms and gear never poke through), and small
	 * enough to fit on your screen from up close.
	 */
	static Look.Target target(Vec3 eye, Vec3 dir, Entity e, Look.Card card, boolean pack) {
		// What it holds sticks out of its box (a sword, a rod): the card stays clear of that too
		double reach = e instanceof net.minecraft.world.entity.LivingEntity le && (!le.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()
				|| !le.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty() || Showcase.ours(e)) ? 0.6 : 0;
		return target(eye, dir, e.position(), e.getBoundingBox(), reach, card, pack);
	}

	/** The same for anything standing at {@code at} and filling {@code box} (a pedestal with its things). */
	static Look.Target target(Vec3 eye, Vec3 dir, Vec3 at, net.minecraft.world.phys.AABB box, double reach, Look.Card card, boolean pack) {
		float[] d = Look.dims(card, pack);
		double mid = box.minY + box.getYsize() * 0.55;
		double by = box.getXsize() / 2 + 0.3;
		// How far the card hangs from your eye (beside the stand, pulled towards you), and as far as the stand
		Vec3 spot = Look.beside(eye, at, box, mid);
		double dist = Math.max(0.35, eye.distanceTo(spot));
		double near = Math.max(0.35, Look.hang(eye, at, box, mid, by, reach).distanceTo(eye));
		// At most about 33 degrees tall and 37 wide: even a step away the whole story fits, above your hand (the text
		// stays readable)
		float size = (float) Math.max(0.08, Math.min(1.0, Math.min(0.6 * near / d[1], 0.75 * near / d[0])));
		// How tall it looks, measured at the stand
		double tall = d[1] * size * dist / near;
		double bottom = Math.max(box.minY + 0.02, mid - tall / 2);
		Vec3 pos = Look.keepOnScreen(eye, dir, Look.hang(eye, at, box, bottom, by, reach), d[1] * size, Look.DOWN_TALL);
		return new Look.Target(pos, card, false, size, 1);
	}

	/** Where a story part goes on a stand: its armour slot, or null for something held. */
	static @Nullable EquipmentSlot worn(String item) {
		return item.endsWith("helmet") ? EquipmentSlot.HEAD : item.endsWith("chestplate") ? EquipmentSlot.CHEST
				: item.endsWith("leggings") ? EquipmentSlot.LEGS : item.endsWith("boots") ? EquipmentSlot.FEET : null;
	}

	/**
	 * For the screenshots and the self-test: a stand wearing one story (a knight's). {@code whole}: all of it, the two
	 * things held too (with arms); else only its armour.
	 */
	static void showcase(ArmorStand st, boolean whole) {
		String story = Found.STORIES.containsKey("odric") ? "odric" : null;
		if (story == null) {
			for (var e : Found.STORIES.entrySet()) {
				if (e.getValue().parts() >= 4) {
					story = e.getKey();
					break;
				}
			}
		}
		if (story == null) return;
		String[] where = {"in a stronghold", "in a village", "Dropped by a zombie", "in an End city", "in a trial chamber", "in a village"};
		int k = 0;
		for (Found.Text t : Found.PIECES) {
			if (!story.equals(t.story())) continue;
			String id = t.kinds().iterator().next();
			EquipmentSlot slot = worn(id);
			if (slot == null) {
				if (!whole) continue;
				slot = st.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
				st.setShowArms(true);
			}
			ItemStack gear = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(
					net.minecraft.resources.Identifier.withDefaultNamespace(id)).orElse(Items.LEATHER_BOOTS));
			Found.write(gear, t, where[k % where.length], 3 + 4 * k, k % 2 == 0 ? "Anna" : "Jeff", st.level().registryAccess());
			st.setItemSlot(slot, gear);
			k++;
		}
	}
}
