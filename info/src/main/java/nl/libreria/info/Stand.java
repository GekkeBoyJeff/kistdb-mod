package nl.libreria.info;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
		List<ItemStack> told = new ArrayList<>();
		for (EquipmentSlot slot : SLOTS) {
			ItemStack s = st.getItemBySlot(slot);
			if (s.isEmpty()) continue;
			// (a story part from before stories were tied to their items gets its right text first)
			if (Found.has(s, Found.STAMP)) Found.fix(null, s, st.level().registryAccess());
			if (told(s)) told.add(s);
		}
		Component own = st.hasCustomName() ? st.getCustomName() : null;
		if (told.isEmpty()) {
			if (!all) return null;
			return new Look.Card(new ItemStack(Items.ARMOR_STAND), -1,
					Component.literal(st.getDisplayName().getString()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
					Component.literal(dressed(st) ? "Nothing on it has a story" : "Entity").withStyle(ChatFormatting.GRAY), List.of());
		}
		// One story all over (a set put together): the story is the name of the card
		Set<String> stories = new LinkedHashSet<>();
		boolean allParts = true;
		for (ItemStack s : told) {
			String story = stamp(s).getStringOr("story", "");
			if (story.isEmpty()) allParts = false;
			else stories.add(story);
		}
		boolean oneStory = allParts && stories.size() == 1;
		Component name, sub;
		if (oneStory) {
			String story = stories.iterator().next();
			Found.Text info = Found.STORIES.get(story);
			name = Component.literal("✦ " + (info == null ? story : info.title())).withStyle(s -> s.withColor(Found.HEAD).withBold(true));
			String parts = told.size() + (info == null ? "" : " of " + info.parts()) + " parts";
			sub = Component.literal((own == null ? "" : own.getString() + " · ") + parts).withStyle(ChatFormatting.GRAY);
		} else {
			name = own != null ? own.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
					: Component.literal("Armour stand").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
			sub = Component.literal(told.size() == 1 ? "1 thing with a story" : told.size() + " things with a story").withStyle(ChatFormatting.GRAY);
		}
		return new Look.Card(new ItemStack(Items.ARMOR_STAND), Found.HEAD, name, sub, fit(told, oneStory));
	}

	/** Where a piece was found: on a line of its own, behind its name, or left out. */
	static final int OWN_LINE = 1, BEHIND_NAME = 2, LEFT_OUT = 0;

	/**
	 * As many rows as the card can hold: first everything, then where behind the name and wider lines, then without
	 * where, then shorter texts.
	 */
	static List<Look.Row> fit(List<ItemStack> told, boolean oneStory) {
		int budget = Math.max(6, Glyphs.maxPanel - 3);
		int[][] tries = {{40, OWN_LINE, 99}, {50, BEHIND_NAME, 99}, {50, LEFT_OUT, 99}, {50, LEFT_OUT, 3}, {50, LEFT_OUT, 2}, {50, LEFT_OUT, 1}};
		List<Look.Row> rows = List.of();
		for (int[] t : tries) {
			rows = rows(told, oneStory, t[0], t[1], t[2]);
			if (rows.size() <= budget) return rows;
		}
		return new ArrayList<>(rows.subList(0, budget));
	}

	static List<Look.Row> rows(List<ItemStack> told, boolean oneStory, int wrap, int where, int maxText) {
		List<Look.Row> out = new ArrayList<>();
		for (ItemStack s : told) {
			CompoundTag stamp = stamp(s);
			String story = stamp.getStringOr("story", "");
			int part = stamp.getIntOr("part", 0);
			MutableComponent head = s.getHoverName().copy();
			if (head.getStyle().getColor() == null) head = head.withStyle(ChatFormatting.WHITE);
			head = Component.empty().append(head.withStyle(st -> st.withItalic(false)));
			if (!story.isEmpty()) {
				Found.Text info = Found.STORIES.get(story);
				String what = oneStory ? "part " + part : (info == null ? story : info.title()) + " · part " + part;
				head.append(Component.literal("  " + what).withStyle(st -> st.withColor(Found.HEAD)));
			}
			String at = stamp.getStringOr("where", "");
			if (where == BEHIND_NAME && !at.isEmpty()) {
				String place = at.startsWith("Found ") ? at.substring(6) : at.substring(0, 1).toLowerCase(java.util.Locale.ROOT) + at.substring(1);
				head.append(Component.literal("  " + place + ", day " + stamp.getIntOr("day", 0)).withStyle(st -> st.withColor(Found.WHERE)));
			}
			out.add(Look.Row.text(story.isEmpty() ? "chest" : "star", story.isEmpty() ? "▪" : "✦", "", head));
			List<String> text = text(s, wrap);
			if (text.size() > maxText) {
				text = new ArrayList<>(text.subList(0, maxText));
				text.set(maxText - 1, text.get(maxText - 1).replaceAll("[\\s,.;:!?]+$", "") + "…");
			}
			for (String l : text) out.add(Look.Row.text(Look.INDENT, " ", "", Lore.line(l, Found.TEXT, true)));
			for (Component w : Lore.get(s, Lore.OWN)) out.add(Look.Row.text(Look.INDENT, " ", "", w.copy()));
			if (where == OWN_LINE && !at.isEmpty()) {
				String line = at + " · day " + stamp.getIntOr("day", 0) + (stamp.getStringOr("by", "").isEmpty() ? "" : " · " + stamp.getStringOr("by", ""));
				out.add(Look.Row.text(Look.INDENT, " ", "", Lore.line(line, Found.WHERE, false)));
			}
		}
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
		float[] d = Look.dims(card, pack);
		double mid = e.getY() + e.getBbHeight() * 0.55;
		double by = e.getBbWidth() / 2 + 0.3;
		// What it holds sticks out of its box (a sword, a rod): the card stays clear of that too
		double reach = e instanceof ArmorStand st && (!st.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()
				|| !st.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) ? 0.6 : 0;
		// How far the card hangs from your eye (beside the stand, pulled towards you), and as far as the stand
		Vec3 spot = Look.beside(eye, e, mid);
		double dist = Math.max(0.35, eye.distanceTo(spot));
		double near = Math.max(0.35, Look.hang(eye, e, mid, by, reach).distanceTo(eye));
		// At most about 33 degrees tall and 37 wide: even a step away the whole story fits, above your hand (the text
		// stays readable)
		float size = (float) Math.max(0.08, Math.min(1.0, Math.min(0.6 * near / d[1], 0.75 * near / d[0])));
		// How tall it looks, measured at the stand
		double tall = d[1] * size * dist / near;
		double bottom = Math.max(e.getY() + 0.02, mid - tall / 2);
		Vec3 at = Look.keepOnScreen(eye, dir, Look.hang(eye, e, bottom, by, reach), d[1] * size, Look.DOWN_TALL);
		return new Look.Target(at, card, false, size, 1);
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
