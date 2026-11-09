package nl.libreria.info;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Domain;
import nl.libreria.core.Hud;
import nl.libreria.core.ServerSetting;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Window;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Stories shown off. A <b>mannequin</b> (crafted from an armour stand and a book: Minecraft's own mannequin, a player
 * without a player) wears a whole story: its four pieces of armour, a thing in each hand, and six spots of its own, two
 * on its back (crossed), two at its hips and two at its feet, so every story fits, also one of seven weapons. Stories of
 * many of one thing (the six swords of an oath) go on a {@link StoryWall}.
 *
 * <p>Right-click a mannequin with something: it takes it (armour onto its body, the rest into its hands, then onto its
 * back, its hips, at its feet). With an empty hand: the stories of what it holds, as a book. Sneak + empty hand: a window
 * to take things off. Hit one to pick it up, with all it holds. Where you may build only. Look at one and its card says
 * which story and how much of it is there.
 */
final class Showcase {
	static final ServerSetting.Toggle ON = InfoMod.MODULE.serverToggle("showcases", "Mannequins and story walls", Items.ARMOR_STAND, true,
			"An armour stand and a book make a", "mannequin; two planks of any wood, an item", "frame, a book and a gold nugget a story wall.",
			"They show off gear and open its stories as a book.");
	/** On the crafted item: "mannequin" or "wall". */
	static final String KEY = "libreria_showcase";

	// ---------------------------------------------------------------- the mannequin

	/** The six spots of a mannequin besides its armour and hands (empty stacks for empty spots). */
	static final AttachmentType<List<ItemStack>> EXTRA = AttachmentRegistry.create(Identifier.fromNamespaceAndPath("libreria", "showcase_extra"),
			b -> b.persistent(ItemStack.OPTIONAL_CODEC.listOf()));

	/** Every spot of a mannequin, in the order they fill and show in its window. */
	enum Spot {
		HEAD("Head", EquipmentSlot.HEAD), CHEST("Chest", EquipmentSlot.CHEST), LEGS("Legs", EquipmentSlot.LEGS), FEET("Feet", EquipmentSlot.FEET),
		MAIN("Right hand", EquipmentSlot.MAINHAND), OFF("Left hand", EquipmentSlot.OFFHAND), BACK_L("On its back", null),
		BACK_R("On its back", null), HIP_L("Left hip", null), HIP_R("Right hip", null), GROUND_L("At its feet", null),
		GROUND_R("At its feet", null);

		final String label;
		final @Nullable EquipmentSlot slot;

		Spot(String label, @Nullable EquipmentSlot slot) {
			this.label = label;
			this.slot = slot;
		}

		int extra() {
			return this.ordinal() - 6;
		}
	}

	static final int EXTRA_SPOTS = 6;

	/** One of ours (a mannequin made from the crafted item; mannequins made with commands stay as they are). */
	static boolean ours(Entity e) {
		return e instanceof Mannequin m && m.hasAttached(EXTRA);
	}

	static List<ItemStack> extras(Mannequin m) {
		List<ItemStack> out = new ArrayList<>(m.getAttachedOrElse(EXTRA, List.of()));
		while (out.size() < EXTRA_SPOTS) out.add(ItemStack.EMPTY);
		return out;
	}

	static ItemStack get(Mannequin m, Spot s) {
		return s.slot != null ? m.getItemBySlot(s.slot) : extras(m).get(s.extra());
	}

	static void set(Mannequin m, Spot s, ItemStack stack) {
		if (s.slot != null) {
			m.setItemSlot(s.slot, stack);
		} else {
			List<ItemStack> list = extras(m);
			list.set(s.extra(), stack);
			m.setAttached(EXTRA, List.copyOf(list));
		}
		show(m);
	}

	/** Everything it holds, spot by spot. */
	static List<ItemStack> all(Mannequin m) {
		List<ItemStack> out = new ArrayList<>();
		for (Spot s : Spot.values()) out.add(get(m, s));
		return out;
	}

	/** Small things hang at the hips first; long ones go on the back. */
	static boolean small(ItemStack s) {
		String kind = Found.kindOf(s);
		return kind == null || kind.equals("shears") || kind.equals("flint") || kind.equals("brush") || kind.equals("stick");
	}

	/** Where a mannequin puts this, or null when there's no room. */
	static @Nullable Spot spotFor(Mannequin m, ItemStack s) {
		EquipmentSlot worn = m.getEquipmentSlotForItem(s);
		List<Spot> order = new ArrayList<>();
		if (worn.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
			for (Spot sp : Spot.values()) if (sp.slot == worn) order.add(sp);
			// A second one of the same (two pairs of boots): at its feet
			order.add(Spot.GROUND_L);
			order.add(Spot.GROUND_R);
		} else if (s.is(Items.SHIELD)) {
			order.addAll(List.of(Spot.OFF, Spot.MAIN, Spot.BACK_L, Spot.BACK_R, Spot.HIP_L, Spot.HIP_R, Spot.GROUND_L, Spot.GROUND_R));
		} else if (small(s)) {
			order.addAll(List.of(Spot.MAIN, Spot.OFF, Spot.HIP_L, Spot.HIP_R, Spot.BACK_L, Spot.BACK_R, Spot.GROUND_L, Spot.GROUND_R));
		} else {
			order.addAll(List.of(Spot.MAIN, Spot.OFF, Spot.BACK_L, Spot.BACK_R, Spot.HIP_L, Spot.HIP_R, Spot.GROUND_L, Spot.GROUND_R));
		}
		for (Spot sp : order) if (get(m, sp).isEmpty()) return sp;
		return null;
	}

	/** A mannequin of ours where this player clicked, facing them. */
	static @Nullable Mannequin placeMannequin(ServerPlayer p, ServerLevel level, BlockPos at) {
		float yaw = net.minecraft.util.Mth.wrapDegrees(p.getYRot() + 180);
		// Facing you, square to the world (as a placed armour stand does)
		yaw = Math.round(yaw / 45f) * 45f;
		double x = at.getX() + 0.5, y = at.getY(), z = at.getZ() + 0.5;
		Mannequin m = summon(level, x, y, z, yaw);
		if (m == null) return null;
		m.setAttached(EXTRA, List.copyOf(extras(m)));
		show(m);
		return m;
	}

	/** Minecraft's own mannequin, made as the summon command makes one: unmoving, and unhurt by anything but our own hit. */
	static @Nullable Mannequin summon(ServerLevel level, double x, double y, double z, float yaw) {
		Mannequin m = net.minecraft.world.entity.EntityTypes.MANNEQUIN.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		if (m == null) return null;
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("immovable", true);
		tag.putBoolean("Invulnerable", true);
		m.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag));
		m.snapTo(x, y, z, yaw, 0);
		m.setYBodyRot(yaw);
		m.setYHeadRot(yaw);
		return level.addFreshEntity(m) ? m : null;
	}

	/** Puts what this player holds on the mannequin. */
	static void dress(ServerPlayer p, Mannequin m) {
		ItemStack hand = p.getMainHandItem();
		Spot spot = spotFor(m, hand);
		if (spot == null) {
			Hud.flash(p, Ui.text("It's full: take something off first ({sneak} + right-click)", ChatFormatting.GRAY));
			return;
		}
		set(m, spot, p.hasInfiniteMaterials() ? hand.copyWithCount(1) : hand.split(1));
		Ui.sound(p, SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.8f, 1.0f);
	}

	/** The window to take things off a mannequin: a button per thing it holds. */
	static void window(ServerPlayer p, Mannequin m) {
		Window w = Window.of(Domain.PROGRESS, "Mannequin");
		w.item(new ItemStack(Items.ARMOR_STAND), Ui.text("Click something to take it off. Right-click the mannequin with "
				+ "something to put it on; with an empty hand, read its stories.", ChatFormatting.GRAY));
		w.columns(2);
		boolean any = false;
		for (Spot s : Spot.values()) {
			ItemStack st = get(m, s);
			if (st.isEmpty()) continue;
			any = true;
			w.button(Component.empty().append(st.getHoverName().copy().withStyle(st2 -> st2.withItalic(false))), Ui.text(s.label, ChatFormatting.GRAY),
					(pl, v) -> {
						if (!usable(pl, m) || get(m, s).isEmpty()) return;
						give(pl, get(m, s));
						set(m, s, ItemStack.EMPTY);
						Ui.click(pl);
						window(pl, m);
					});
		}
		if (!any) w.item(new ItemStack(Items.BARRIER), Ui.text("It holds nothing yet.", ChatFormatting.GRAY));
		w.bottom(Ui.text("✔ Done", ChatFormatting.GREEN), (pl, v) -> {
		}).show(p);
	}

	static boolean usable(ServerPlayer p, Entity e) {
		return e.isAlive() && e.level() == p.level() && e.distanceTo(p) < 10 && Services.mayBuild(p, (ServerLevel) p.level(), e.blockPosition());
	}

	/** Into the player's inventory, or at their feet when it's full. */
	static void give(ServerPlayer p, ItemStack s) {
		ItemStack copy = s.copy();
		if (!p.getInventory().add(copy) && !copy.isEmpty()) drop((ServerLevel) p.level(), p.position(), copy);
	}

	/** Hit: it comes down, with everything it held. */
	static void pickUp(ServerPlayer p, Mannequin m) {
		ServerLevel level = (ServerLevel) m.level();
		for (ItemStack s : all(m)) if (!s.isEmpty()) drop(level, m.position(), s);
		if (!p.hasInfiniteMaterials()) drop(level, m.position(), item("mannequin"));
		ElementHolder h = HOLDERS.remove(m.getUUID());
		if (h != null) h.destroy();
		m.discard();
		Ui.sound(p, SoundEvents.ARMOR_STAND_BREAK, 0.8f, 1.0f);
	}

	static void drop(ServerLevel level, Vec3 at, ItemStack s) {
		ItemEntity e = new ItemEntity(level, at.x, at.y + 0.5, at.z, s.copy());
		e.setDefaultPickUpDelay();
		level.addFreshEntity(e);
	}

	// ---------------------------------------------------------------- what the extra spots look like

	private static final Map<UUID, ElementHolder> HOLDERS = new HashMap<>();

	/**
	 * Where the six extra spots show, on the mannequin (x: its left, y: up, z: in front; it faces +z), and how each thing
	 * lies there: crossed on the back, hanging at the hips, lying at its feet.
	 */
	static void place(ItemDisplayElement e, Spot s, boolean upright) {
		float tilt = upright ? (float) Math.toRadians(45) : 0f;
		switch (s) {
			case BACK_L -> pose(e, -0.02f, 1.32f, -0.21f, new Quaternionf().rotateZ(tilt + (float) Math.toRadians(45)), 0.85f);
			case BACK_R -> pose(e, 0.02f, 1.32f, -0.23f, new Quaternionf().rotateZ(tilt - (float) Math.toRadians(45)), 0.85f);
			case HIP_L -> pose(e, 0.34f, 0.8f, 0.02f, new Quaternionf().rotateY((float) Math.toRadians(90)).rotateZ(tilt + (float) Math.PI), 0.5f);
			case HIP_R -> pose(e, -0.34f, 0.8f, 0.02f, new Quaternionf().rotateY((float) Math.toRadians(90)).rotateZ(tilt + (float) Math.PI), 0.5f);
			case GROUND_L -> pose(e, 0.3f, 0.03f, 0.38f, new Quaternionf().rotateX((float) Math.toRadians(-90)).rotateZ((float) Math.toRadians(20)), 0.55f);
			case GROUND_R -> pose(e, -0.3f, 0.03f, 0.38f, new Quaternionf().rotateX((float) Math.toRadians(-90)).rotateZ((float) Math.toRadians(-20)), 0.55f);
			default -> {
			}
		}
	}

	private static void pose(ItemDisplayElement e, float x, float y, float z, Quaternionf rot, float scale) {
		e.setTranslation(new Vector3f(x, y, z));
		e.setLeftRotation(rot);
		e.setScale(new Vector3f(scale));
	}

	/** Long things drawn on a slant (a sword, a bow): stood upright on the back. */
	static boolean slanted(ItemStack s) {
		String kind = Found.kindOf(s);
		return kind != null && Set.of("sword", "axe", "pickaxe", "shovel", "hoe", "spear", "trident", "rod", "stick", "bow", "mace").contains(kind);
	}

	/** Shows (again) what is in the mannequin's own six spots. */
	static void show(Mannequin m) {
		if (!ours(m)) return;
		ElementHolder holder = HOLDERS.get(m.getUUID());
		if (holder == null) {
			holder = new ElementHolder();
			for (int i = 0; i < EXTRA_SPOTS; i++) {
				ItemDisplayElement e = new ItemDisplayElement();
				e.setItemDisplayContext(ItemDisplayContext.FIXED);
				e.setViewRange(0.8f);
				holder.addElement(e);
			}
			EntityAttachment.ofTicking(holder, m);
			HOLDERS.put(m.getUUID(), holder);
		}
		List<ItemStack> extra = extras(m);
		for (int i = 0; i < EXTRA_SPOTS; i++) {
			ItemDisplayElement e = (ItemDisplayElement) holder.getElements().get(i);
			ItemStack s = extra.get(i);
			e.setItem(s.copy());
			e.setYaw(m.getYRot());
			Spot spot = Spot.values()[6 + i];
			place(e, spot, (spot == Spot.BACK_L || spot == Spot.BACK_R) && !slanted(s));
		}
		holder.tick();
	}

	// ---------------------------------------------------------------- the crafted items

	/** A mannequin or (dark oak) story wall as an item ("mannequin" / "wall"), as the recipe makes it. */
	static ItemStack item(String what) {
		if (!what.equals("mannequin")) return StoryWall.item("dark_oak");
		ItemStack s = new ItemStack(Items.ARMOR_STAND);
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, "mannequin");
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		s.set(DataComponents.ITEM_NAME, Component.literal("Mannequin"));
		s.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(Ui.text("Dress it in a whole story: armour,", ChatFormatting.GRAY),
				Ui.text("both hands, its back, hips and feet.", ChatFormatting.GRAY), Ui.text("Right-click it: read its stories.", ChatFormatting.GRAY))));
		return s;
	}

	static @Nullable String kindOf(ItemStack s) {
		CustomData cd = s.get(DataComponents.CUSTOM_DATA);
		if (cd == null) return null;
		String k = cd.copyTag().getStringOr(KEY, "");
		return k.isEmpty() ? null : k;
	}

	/** The stories of these things: story → the parts of it among them. */
	static Map<String, Set<Integer>> stories(List<ItemStack> items) {
		Map<String, Set<Integer>> out = new LinkedHashMap<>();
		for (ItemStack s : items) {
			if (s.isEmpty()) continue;
			CompoundTag st = Stand.stamp(s);
			String story = st.getStringOr("story", "");
			if (!story.isEmpty() && Found.STORIES.containsKey(story)) out.computeIfAbsent(story, k -> new TreeSet<>()).add(st.getIntOr("part", 0));
		}
		return out;
	}

	static void read(ServerPlayer p, String what, List<ItemStack> items) {
		Map<String, Set<Integer>> stories = stories(items);
		if (stories.isEmpty()) {
			Hud.flash(p, Ui.text(items.stream().allMatch(ItemStack::isEmpty) ? "Nothing on it yet: right-click it with something"
					: "Nothing on it has a story", ChatFormatting.GRAY));
			return;
		}
		LoreBook.showcase(p, what, stories);
	}

	// ---------------------------------------------------------------- events

	private static final Map<UUID, Long> LAST_TICK = new HashMap<>();

	/** The client sends a right-click on an entity twice (at a spot, and on it): act once a tick. */
	static boolean again(ServerPlayer p) {
		long tick = p.level().getServer().getTickCount();
		Long last = LAST_TICK.put(p.getUUID(), tick);
		return last != null && last == tick;
	}

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mannequin m && ours(m)) show(m);
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			ElementHolder h = HOLDERS.remove(entity.getUUID());
			if (h != null) h.destroy();
		});
		// Placing one
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp) || sp.isSpectator() || !ON.get() || !InfoMod.MODULE.on())
				return InteractionResult.PASS;
			ServerLevel level = (ServerLevel) world;
			ItemStack held = sp.getItemInHand(hand);
			String what = kindOf(held);
			if (what == null) return InteractionResult.PASS;
			if (what.equals(StoryWall.KIND)) {
				if (hand != InteractionHand.MAIN_HAND || StoryWall.place(sp, level, hit, StoryWall.woodOf(held)) == null) return InteractionResult.FAIL;
				if (!sp.hasInfiniteMaterials()) held.shrink(1);
				Ui.sound(sp, SoundEvents.WOOD_PLACE, 1f, 0.9f);
				return InteractionResult.SUCCESS;
			}
			BlockPos at = level.getBlockState(hit.getBlockPos()).canBeReplaced() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
			if (!Services.mayBuild(sp, level, at) || !level.getBlockState(at).canBeReplaced() || !level.getBlockState(at.above()).canBeReplaced()) {
				return InteractionResult.FAIL;
			}
			if (placeMannequin(sp, level, at) == null) return InteractionResult.FAIL;
			if (!sp.hasInfiniteMaterials()) held.shrink(1);
			Ui.sound(sp, SoundEvents.ARMOR_STAND_PLACE, 1f, 1f);
			return InteractionResult.SUCCESS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp) || sp.isSpectator() || !(entity instanceof Mannequin m) || !ours(m))
				return InteractionResult.PASS;
			if (hand != InteractionHand.MAIN_HAND || again(sp)) return InteractionResult.SUCCESS;
			boolean empty = sp.getMainHandItem().isEmpty();
			if (empty && !sp.isShiftKeyDown()) {
				read(sp, "mannequin", all(m));
				return InteractionResult.SUCCESS;
			}
			if (!Services.mayBuild(sp, (ServerLevel) world, m.blockPosition())) {
				if (empty) read(sp, "mannequin", all(m));
				return InteractionResult.SUCCESS;
			}
			if (empty) window(sp, m);
			else dress(sp, m);
			return InteractionResult.SUCCESS;
		});
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp) || !(entity instanceof Mannequin m) || !ours(m)) return InteractionResult.PASS;
			if (Services.mayBuild(sp, (ServerLevel) world, m.blockPosition())) pickUp(sp, m);
			return InteractionResult.SUCCESS;
		});
	}

	static @Nullable Look.Card mannequinCard(Mannequin m) {
		Look.Card card = Stand.card(all(m), m.hasCustomName() ? m.getCustomName() : null, "Mannequin", item("mannequin"), m.level().registryAccess());
		if (card != null) return card;
		return new Look.Card(item("mannequin"), -1, Component.literal("Mannequin").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
				Ui.text("Right-click it with a story's gear", ChatFormatting.GRAY), List.of());
	}

	// ---------------------------------------------------------------- for the screenshots and the self-test

	/** A mannequin wearing a whole story (all of its parts, wherever they fit). */
	static @Nullable Mannequin debugMannequin(ServerLevel level, BlockPos at, float yaw, String story) {
		Mannequin m = summon(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw);
		if (m == null) return null;
		m.setAttached(EXTRA, List.copyOf(extras(m)));
		for (ItemStack s : storyItems(level, story)) {
			Spot spot = spotFor(m, s);
			if (spot != null) set(m, spot, s);
		}
		show(m);
		return m;
	}

	/** Every part of a story, as found items. */
	static List<ItemStack> storyItems(ServerLevel level, String story) {
		List<ItemStack> out = new ArrayList<>();
		int k = 0;
		for (Found.Text t : Found.PIECES) {
			if (!story.equals(t.story())) continue;
			String id = t.kinds().iterator().next();
			ItemStack s = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(id))
					.orElse(Items.STICK));
			Found.write(s, t, k % 2 == 0 ? "in a shipwreck" : "in a village", 3 + 4 * k, k % 2 == 0 ? "Anna" : "Jeff", level.registryAccess());
			out.add(s);
			k++;
		}
		return out;
	}
}
