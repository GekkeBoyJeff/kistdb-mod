package nl.libreria.info;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Brightness;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Hud;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import nl.libreria.core.WorldStore;
import nl.libreria.core.gui.Glyphs;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A <b>story wall</b> (two dark oak planks, an item frame, a book and a gold nugget): a board four blocks wide and two
 * high that hangs on a wall, with eight spots of a block each, so every thing on it can be looked at, taken and put back
 * on its own. Made for the stories that are many of one thing (the six swords of an oath, the spears of four sisters)
 * but it holds anything.
 *
 * <p>The first part of a story hung on it lays it out for that story: part 1 top left, then on in reading order, and the
 * parts that are missing show as dark shapes in their spots until they're found. Right-click a spot with something: it
 * hangs there (a part of its story: in its own spot). Right- or left-click a full spot: that one thing comes off. Sneak +
 * right-click: its stories as a book. Hit an empty one when nothing hangs on it: the board comes down. Look at a spot and
 * a white frame shows which one, and its card says what's there.
 */
final class StoryWall {
	static final int COLS = 4, ROWS = 2, SPOTS = COLS * ROWS;
	/** On the crafted item ({@link Showcase#KEY}). */
	static final String KIND = "wall";

	public static final class Wall {
		public String dim = "minecraft:overworld";
		/** The bottom-left spot (as you face it) in front of the wall. */
		public int x, y, z;
		/** Which way it faces (out of the wall). */
		public String facing = "south";
		/** The story it is laid out for, "" for none (yet). */
		public String story = "";
		/** The eight spots, top row first, left to right (items as JSON; an empty object for an empty spot). */
		public List<JsonElement> items = new ArrayList<>();
	}

	public static final class Data {
		public List<Wall> walls = new ArrayList<>();
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/story_walls.json", Data.class, Data::new);
	private static final Map<Wall, ElementHolder> SHOWN = new HashMap<>();

	private StoryWall() {
	}

	static Data data() {
		Data d = STORE.get();
		if (d.walls == null) d.walls = new ArrayList<>();
		return d;
	}

	// ---------------------------------------------------------------- where everything is

	static Direction facing(Wall w) {
		for (Direction d : Direction.values()) if (d.getAxis().isHorizontal() && d.getSerializedName().equals(w.facing)) return d;
		return Direction.SOUTH;
	}

	/** Your right, as you face the wall. */
	static Direction right(Direction facing) {
		return facing.getCounterClockWise();
	}

	static int col(int spot) {
		return spot % COLS;
	}

	static int row(int spot) {
		return spot / COLS;
	}

	/** The block in front of the wall where this spot is. */
	static BlockPos spot(Wall w, int spot) {
		Direction f = facing(w);
		return new BlockPos(w.x, w.y, w.z).relative(right(f), col(spot)).above(ROWS - 1 - row(spot));
	}

	/** The middle of the board, on the face of the wall. */
	static Vec3 middle(Wall w) {
		Direction f = facing(w), r = right(f);
		Vec3 corner = Vec3.atCenterOf(new BlockPos(w.x, w.y, w.z));
		return corner.add(r.getStepX() * (COLS - 1) / 2.0, (ROWS - 1) / 2.0, r.getStepZ() * (COLS - 1) / 2.0)
				.subtract(f.getStepX() * 0.5, 0, f.getStepZ() * 0.5);
	}

	/** A point on the board (x: to your right, y: up, z: out of the wall, from its middle) in the world. */
	static Vec3 at(Wall w, double x, double y, double z) {
		Direction f = facing(w), r = right(f);
		return middle(w).add(r.getStepX() * x + f.getStepX() * z, y, r.getStepZ() * x + f.getStepZ() * z);
	}

	/** Where the middle of a spot is on the board (x, y). */
	static double spotX(int spot) {
		return col(spot) - (COLS - 1) / 2.0;
	}

	static double spotY(int spot) {
		return (ROWS - 1) / 2.0 - row(spot);
	}

	/** The box you look at for a spot: a block wide and high, a little out from the wall. */
	static AABB box(Wall w, int spot) {
		Vec3 a = at(w, spotX(spot) - 0.48, spotY(spot) - 0.48, 0), b = at(w, spotX(spot) + 0.48, spotY(spot) + 0.48, 0.2);
		return new AABB(a, b);
	}

	/** The whole board (for where its card goes). */
	static AABB board(Wall w) {
		return new AABB(at(w, -COLS / 2.0, -ROWS / 2.0, 0), at(w, COLS / 2.0, ROWS / 2.0 + 0.2, 0.2));
	}

	static @Nullable ServerLevel level(MinecraftServer server, Wall w) {
		return server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, Identifier.parse(w.dim)));
	}

	static String dim(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

	// ---------------------------------------------------------------- what hangs on it

	static List<ItemStack> items(ServerLevel level, Wall w) {
		var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		List<ItemStack> out = new ArrayList<>();
		for (int i = 0; i < SPOTS; i++) {
			JsonElement j = i < w.items.size() ? w.items.get(i) : null;
			ItemStack s = j == null || j.isJsonNull() || (j.isJsonObject() && j.getAsJsonObject().isEmpty()) ? ItemStack.EMPTY
					: ItemStack.CODEC.parse(ops, j).result().orElse(ItemStack.EMPTY);
			out.add(s);
		}
		return out;
	}

	static void setItems(ServerLevel level, Wall w, List<ItemStack> items) {
		var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		w.items = new ArrayList<>();
		for (ItemStack s : items) {
			w.items.add(s.isEmpty() ? new JsonObject() : ItemStack.CODEC.encodeStart(ops, s).result().orElse(new JsonObject()));
		}
		// Laid out for a story only while a part of it hangs here
		if (!w.story.isEmpty() && items.stream().noneMatch(s -> w.story.equals(Stand.stamp(s).getStringOr("story", "")))) w.story = "";
		STORE.markDirty();
		show(level, w);
	}

	/** How many parts the story it is laid out for has (0: none, or more than fit). */
	static int laidOut(Wall w) {
		if (w.story.isEmpty()) return 0;
		Found.Text t = Found.STORIES.get(w.story);
		return t == null || t.parts() > SPOTS ? 0 : t.parts();
	}

	/** Kept free for a part of its story that isn't here yet. */
	static boolean kept(Wall w, List<ItemStack> items, int spot) {
		return spot < laidOut(w) && items.get(spot).isEmpty();
	}

	/** The part of the story a thing is, if it's a part of a story that fits on a wall: [story, part]. */
	static @Nullable Object[] part(ItemStack s) {
		CompoundTag st = Stand.stamp(s);
		String story = st.getStringOr("story", "");
		int part = st.getIntOr("part", 0);
		Found.Text t = story.isEmpty() ? null : Found.STORIES.get(story);
		if (t == null || t.parts() > SPOTS || part < 1 || part > t.parts()) return null;
		return new Object[] {story, part};
	}

	/** Where this thing goes when you click {@code clicked} with it, or -1 when there's no room. */
	static int spotFor(Wall w, List<ItemStack> items, ItemStack s, int clicked) {
		Object[] part = part(s);
		if (part != null) {
			String story = (String) part[0];
			int home = (int) part[1] - 1;
			// The first part of a story on an empty board lays it out for that story
			if (w.story.isEmpty() && items.stream().allMatch(ItemStack::isEmpty)) w.story = story;
			if (story.equals(w.story) && items.get(home).isEmpty()) return home;
		}
		if (items.get(clicked).isEmpty() && !kept(w, items, clicked)) return clicked;
		for (int i = 0; i < SPOTS; i++) if (items.get(i).isEmpty() && !kept(w, items, i)) return i;
		return -1;
	}

	// ---------------------------------------------------------------- hanging one up, taking it down

	/**
	 * Hangs a new board where this player clicked a wall: centred on the spot they clicked, flat against it, as long as
	 * there's a flat wall four blocks wide and two high there with room in front of it.
	 */
	static @Nullable Wall place(ServerPlayer p, ServerLevel level, BlockHitResult hit) {
		Direction f = hit.getDirection();
		if (f.getAxis().isVertical()) {
			Hud.flash(p, Ui.text("Hang it on a wall: right-click the side of a block", ChatFormatting.GRAY));
			return null;
		}
		Direction r = right(f);
		BlockPos clicked = hit.getBlockPos();
		Vec3 in = hit.getLocation().subtract(Vec3.atLowerCornerOf(clicked));
		double across = in.x * r.getStepX() + in.z * r.getStepZ();
		if (across < 0) across += 1;
		// Its middle at the edge of the block nearest to where you clicked
		int left = across >= 0.5 ? -1 : -2, bottom = in.y >= 0.5 ? 0 : -1;
		Wall w = new Wall();
		w.dim = dim(level);
		w.facing = f.getSerializedName();
		BlockPos origin = clicked.relative(f).relative(r, left).above(bottom);
		w.x = origin.getX();
		w.y = origin.getY();
		w.z = origin.getZ();
		for (int i = 0; i < SPOTS; i++) {
			BlockPos s = spot(w, i), back = s.relative(f.getOpposite());
			BlockState there = level.getBlockState(s);
			if (!level.getBlockState(back).isFaceSturdy(level, back, f) || !(there.canBeReplaced() || there.getCollisionShape(level, s).isEmpty())
					|| wallAt(level, s) != null) {
				Hud.flash(p, Ui.text("No room: it needs a flat wall 4 wide and 2 high, with nothing in front", ChatFormatting.GRAY));
				return null;
			}
			if (!Services.mayBuild(p, level, s)) return null;
		}
		List<ItemStack> empty = new ArrayList<>();
		for (int i = 0; i < SPOTS; i++) empty.add(ItemStack.EMPTY);
		data().walls.add(w);
		setItems(level, w, empty);
		return w;
	}

	static @Nullable Wall wallAt(ServerLevel level, BlockPos pos) {
		String dim = dim(level);
		for (Wall w : data().walls) {
			if (!w.dim.equals(dim)) continue;
			for (int i = 0; i < SPOTS; i++) if (spot(w, i).equals(pos)) return w;
		}
		return null;
	}

	/** The board comes down: what hung on it drops, and the board itself when {@code dropItself}. */
	static void remove(ServerLevel level, Wall w, boolean dropItself) {
		Vec3 at = at(w, 0, -0.5, 0.6);
		for (ItemStack s : items(level, w)) if (!s.isEmpty()) Showcase.drop(level, at, s);
		if (dropItself) Showcase.drop(level, at, Showcase.item(KIND));
		data().walls.remove(w);
		STORE.markDirty();
		ElementHolder h = SHOWN.remove(w);
		if (h != null) h.destroy();
	}

	/** Right-click on a spot. */
	static void use(ServerPlayer p, Wall w, int spot) {
		ServerLevel level = p.level();
		if (!dim(level).equals(w.dim) || !data().walls.contains(w)) return;
		ItemStack hand = p.getMainHandItem();
		List<ItemStack> items = items(level, w);
		if (p.isShiftKeyDown() && hand.isEmpty()) {
			Showcase.read(p, "story wall", items);
			return;
		}
		if (!Services.mayBuild(p, level, spot(w, spot))) {
			if (hand.isEmpty()) Showcase.read(p, "story wall", items);
			return;
		}
		if (!items.get(spot).isEmpty()) {
			take(p, w, spot);
			return;
		}
		if (hand.isEmpty()) {
			Hud.flash(p, Ui.text(kept(w, items, spot) ? "Kept for a part of its story that hasn't been found yet"
					: "Right-click with something to hang it here", ChatFormatting.GRAY));
			return;
		}
		int to = spotFor(w, items, hand, spot);
		if (to < 0) {
			Hud.flash(p, Ui.text("No room: the empty spots are kept for its story's other parts", ChatFormatting.GRAY));
			return;
		}
		items.set(to, p.hasInfiniteMaterials() ? hand.copyWithCount(1) : hand.split(1));
		setItems(level, w, items);
		Ui.sound(p, SoundEvents.ITEM_FRAME_ADD_ITEM, 0.9f, 1.0f);
	}

	/** Left-click on a spot: that thing comes off; on an empty board, the board comes down. */
	static void hit(ServerPlayer p, Wall w, int spot) {
		ServerLevel level = p.level();
		if (!dim(level).equals(w.dim) || !data().walls.contains(w) || !Services.mayBuild(p, level, spot(w, spot))) return;
		List<ItemStack> items = items(level, w);
		if (!items.get(spot).isEmpty()) {
			take(p, w, spot);
			return;
		}
		if (items.stream().allMatch(ItemStack::isEmpty)) {
			remove(level, w, !p.hasInfiniteMaterials());
			Ui.sound(p, SoundEvents.WOOD_BREAK, 1f, 1f);
			return;
		}
		Hud.flash(p, Ui.text("Take everything off it first, then hit it to take it down", ChatFormatting.GRAY));
	}

	static void take(ServerPlayer p, Wall w, int spot) {
		ServerLevel level = p.level();
		List<ItemStack> items = items(level, w);
		ItemStack s = items.get(spot);
		if (s.isEmpty()) return;
		Showcase.give(p, s);
		items.set(spot, ItemStack.EMPTY);
		setItems(level, w, items);
		Ui.sound(p, SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.9f, 1.0f);
	}

	// ---------------------------------------------------------------- what it looks like

	private static final float DEPTH = 0.06f, FRAME = 0.08f, BAR = 0.06f, RIM = 0.11f;
	/** The parts of a board, in the holder: the eight things first. */
	private static final int THINGS = 0, PLATE = SPOTS, TITLE = SPOTS + 1;

	private static BlockDisplayElement slab(BlockState state, float x, float y, float z, float w, float h, float d, float yaw) {
		BlockDisplayElement e = new BlockDisplayElement(state);
		e.setTranslation(new Vector3f(x, y, z));
		e.setScale(new Vector3f(w, h, d));
		e.setYaw(yaw);
		e.setViewRange(0.8f);
		return e;
	}

	/** Shows (again) a board and what hangs on it. */
	static void show(ServerLevel level, Wall w) {
		Direction f = facing(w);
		float yaw = f.toYRot();
		ElementHolder holder = SHOWN.get(w);
		if (holder == null) {
			holder = new ElementHolder();
			for (int i = 0; i < SPOTS; i++) {
				ItemDisplayElement e = new ItemDisplayElement();
				e.setItemDisplayContext(ItemDisplayContext.FIXED);
				e.setViewRange(0.8f);
				e.setYaw(yaw);
				e.setTranslation(new Vector3f((float) spotX(i), (float) spotY(i), RIM - 0.02f));
				e.setScale(new Vector3f(0.78f));
				holder.addElement(e);
			}
			// The brass plate with its title, over the top of the frame
			holder.addElement(slab(Blocks.GOLD_BLOCK.defaultBlockState(), -0.8f, ROWS / 2f - 0.12f, RIM - 0.02f, 1.6f, 0.32f, 0.04f, yaw));
			TextDisplayElement title = new TextDisplayElement();
			title.setYaw(yaw);
			title.setBackground(0);
			title.setShadow(false);
			title.setLineWidth(1000);
			title.setViewRange(0.6f);
			holder.addElement(title);
			// The board: dark oak, framed and parted into eight spots with stripped dark oak
			BlockState board = Blocks.DARK_OAK_PLANKS.defaultBlockState(), rim = Blocks.STRIPPED_DARK_OAK_WOOD.defaultBlockState();
			float hw = COLS / 2f, hh = ROWS / 2f;
			holder.addElement(slab(board, -hw, -hh, 0, COLS, ROWS, DEPTH, yaw));
			holder.addElement(slab(rim, -hw, hh - FRAME, 0, COLS, FRAME, RIM, yaw));
			holder.addElement(slab(rim, -hw, -hh, 0, COLS, FRAME, RIM, yaw));
			holder.addElement(slab(rim, -hw, -hh, 0, FRAME, ROWS, RIM, yaw));
			holder.addElement(slab(rim, hw - FRAME, -hh, 0, FRAME, ROWS, RIM, yaw));
			for (int c = 1; c < COLS; c++) holder.addElement(slab(rim, c - hw - BAR / 2, -hh, 0, BAR, ROWS, RIM - 0.01f, yaw));
			for (int r = 1; r < ROWS; r++) holder.addElement(slab(rim, -hw, r - hh - BAR / 2, 0, COLS, BAR, RIM - 0.01f, yaw));
			// What you click: a box over each spot
			Vec3 mid = middle(w);
			for (int i = 0; i < SPOTS; i++) {
				final int spot = i;
				InteractionElement click = new InteractionElement(new VirtualElement.InteractionHandler() {
					@Override
					public void interact(ServerPlayer player, InteractionHand hand, Vec3 pos, boolean secondary) {
						if (hand == InteractionHand.MAIN_HAND && !Showcase.again(player)) use(player, w, spot);
					}

					@Override
					public void attack(ServerPlayer player) {
						if (!Showcase.again(player)) hit(player, w, spot);
					}
				});
				click.setSize(0.92f, 0.92f);
				click.setOffset(at(w, spotX(i), spotY(i) - 0.46, 0.04).subtract(mid));
				holder.addElement(click);
			}
			ChunkAttachment.ofTicking(holder, level, mid);
			SHOWN.put(w, holder);
		}
		List<ItemStack> items = items(level, w);
		int laid = laidOut(w);
		Map<Integer, String> kinds = laid > 0 ? kinds(w.story) : Map.of();
		for (int i = 0; i < SPOTS; i++) {
			ItemDisplayElement e = (ItemDisplayElement) holder.getElements().get(THINGS + i);
			ItemStack s = items.get(i);
			if (s.isEmpty() && i < laid && kinds.containsKey(i + 1)) {
				// A part not found yet: its shape, dark
				e.setItem(new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(kinds.get(i + 1))).orElse(Items.AIR)));
				e.setBrightness(new Brightness(0, 0));
			} else {
				e.setItem(s.copy());
				e.setBrightness(null);
			}
		}
		// The title on the plate: the story it's laid out for, else the one most of what hangs here belongs to
		String story = !w.story.isEmpty() ? w.story : most(items);
		Found.Text info = story == null ? null : Found.STORIES.get(story);
		Component text = Component.literal(info == null ? "Story Wall" : info.title()).withStyle(st -> st.withColor(0x3A2508));
		float scale = 0.42f;
		float width = Glyphs.width(text) * 0.025f * scale;
		if (width > COLS - 0.6f) {
			scale *= (COLS - 0.6f) / width;
			width = COLS - 0.6f;
		}
		BlockDisplayElement plate = (BlockDisplayElement) holder.getElements().get(PLATE);
		float plateW = Math.max(1.2f, width + 0.3f), plateH = 0.25f * scale + 0.16f;
		plate.setTranslation(new Vector3f(-plateW / 2, ROWS / 2f - plateH / 2 - 0.02f, RIM - 0.02f));
		plate.setScale(new Vector3f(plateW, plateH, 0.04f));
		TextDisplayElement title = (TextDisplayElement) holder.getElements().get(TITLE);
		title.setText(text);
		title.setScale(new Vector3f(scale));
		title.setTranslation(new Vector3f(0, ROWS / 2f - plateH / 2 - 0.02f + (plateH - 0.25f * scale) / 2 - 0.01f, RIM + 0.025f));
		holder.tick();
	}

	/** The kind of thing each part of a story is (part → item id). */
	static Map<Integer, String> kinds(String story) {
		Map<Integer, String> out = new HashMap<>();
		for (Found.Text t : Found.PIECES) if (story.equals(t.story()) && !t.kinds().isEmpty()) out.put(t.part(), t.kinds().iterator().next());
		return out;
	}

	static @Nullable String most(List<ItemStack> items) {
		Map<String, Integer> count = new LinkedHashMap<>();
		for (ItemStack s : items) {
			String story = Stand.stamp(s).getStringOr("story", "");
			if (!story.isEmpty() && Found.STORIES.containsKey(story)) count.merge(story, 1, Integer::sum);
		}
		return count.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
	}

	// ---------------------------------------------------------------- what you look at

	record Hit(Wall wall, int spot, double distance) {
	}

	/** The spot of a board this ray hits first, if any. */
	static @Nullable Hit hit(ServerLevel level, Vec3 from, Vec3 to) {
		String dim = dim(level);
		Hit best = null;
		for (Wall w : data().walls) {
			if (!w.dim.equals(dim) || middle(w).distanceToSqr(from) > 100) continue;
			for (int i = 0; i < SPOTS; i++) {
				Optional<Vec3> on = box(w, i).clip(from, to);
				if (on.isEmpty()) continue;
				double d = on.get().distanceTo(from);
				if (best == null || d < best.distance()) best = new Hit(w, i, d);
			}
		}
		return best;
	}

	/** The card of a spot: what hangs there and which story it's from; a missing part; or an empty spot. */
	static Look.Card card(ServerLevel level, Wall w, int spot) {
		List<ItemStack> items = items(level, w);
		ItemStack s = items.get(spot);
		ItemStack icon = Showcase.item(KIND);
		if (s.isEmpty()) {
			if (kept(w, items, spot)) {
				Found.Text info = Found.STORIES.get(w.story);
				return new Look.Card(icon, Found.HEAD, Component.literal("???").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD),
						Component.literal("Part " + (spot + 1) + " of " + info.parts() + " · " + info.title()).withStyle(ChatFormatting.GRAY),
						List.of(Look.Row.text(Look.INDENT, " ", "", Component.literal("Not found yet: its spot is kept for it").withStyle(ChatFormatting.GRAY))));
			}
			return new Look.Card(icon, -1, Component.literal("Empty spot").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
					Ui.text("Right-click with something to hang it here", ChatFormatting.GRAY), List.of());
		}
		if (Found.has(s, Found.STAMP)) Found.fix(null, s, level.registryAccess());
		CompoundTag stamp = Stand.stamp(s);
		String story = stamp.getStringOr("story", "");
		Found.Text info = story.isEmpty() ? null : Found.STORIES.get(story);
		Component name = s.getHoverName().copy().withStyle(st -> st.withItalic(false).withBold(true).withColor(info != null ? Found.HEAD : 0xFFFFFF));
		List<Look.Row> rows = new ArrayList<>();
		String where = stamp.getStringOr("where", "");
		if (!where.isEmpty()) {
			int day = stamp.getIntOr("day", 0);
			String by = stamp.getStringOr("by", "");
			rows.add(Look.Row.text(Look.INDENT, " ", "", Component.literal(where + (day > 0 ? " · day " + day : "") + (by.isEmpty() ? "" : " · " + by))
					.withStyle(ChatFormatting.GRAY)));
		}
		rows.add(Look.Row.text(Look.INDENT, " ", "", Ui.text(info != null ? "{sneak} + right-click: read its story" : "Right-click: take it down",
				ChatFormatting.DARK_GRAY)));
		Component sub = info != null ? Component.literal("Part " + stamp.getIntOr("part", 0) + " of " + info.parts() + " · " + info.title())
				.withStyle(ChatFormatting.GRAY) : Component.literal("On a story wall").withStyle(ChatFormatting.GRAY);
		return new Look.Card(s, info != null ? Found.HEAD : -1, name, sub, rows);
	}

	// ---------------------------------------------------------------- the frame round the spot you look at

	private static final class Frame {
		final ElementHolder holder = new ElementHolder();
		Vec3 at = Vec3.ZERO;
		ServerLevel level;
		@Nullable Wall wall;
		int spot = -1;
	}

	private static final Map<UUID, Frame> FRAMES = new HashMap<>();
	private static final BlockState WHITE = BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace("white_concrete"))
			.map(net.minecraft.world.level.block.Block::defaultBlockState).orElse(Blocks.SNOW_BLOCK.defaultBlockState());

	/** Shows this player a white frame round the spot they look at (only them), or hides it. */
	static void frame(ServerPlayer p, @Nullable Hit hit) {
		Frame fr = FRAMES.get(p.getUUID());
		if (hit == null) {
			if (fr != null && fr.wall != null) {
				fr.holder.getElements().forEach(e -> ((BlockDisplayElement) e).setBlockState(Blocks.AIR.defaultBlockState()));
				fr.wall = null;
				fr.holder.tick();
			}
			return;
		}
		if (fr == null || fr.level != p.level() || fr.holder.getAttachment() == null) {
			if (fr != null) fr.holder.destroy();
			Frame made = new Frame();
			made.level = p.level();
			for (int i = 0; i < 4; i++) {
				BlockDisplayElement bar = new BlockDisplayElement(Blocks.AIR.defaultBlockState());
				bar.setBrightness(new Brightness(15, 15));
				bar.setViewRange(0.5f);
				made.holder.addElement(bar);
			}
			new ManualAttachment(made.holder, made.level, () -> made.at);
			made.holder.startWatching(p);
			FRAMES.put(p.getUUID(), made);
			fr = made;
		}
		if (fr.wall == hit.wall() && fr.spot == hit.spot()) return;
		fr.wall = hit.wall();
		fr.spot = hit.spot();
		fr.at = at(hit.wall(), spotX(hit.spot()), spotY(hit.spot()), 0);
		float yaw = facing(hit.wall()).toYRot(), half = 0.5f - BAR / 2, t = 0.035f, z = RIM + 0.005f;
		float[][] bars = {{-half, half - t, 2 * half, t}, {-half, -half, 2 * half, t}, {-half, -half, t, 2 * half}, {half - t, -half, t, 2 * half}};
		for (int i = 0; i < 4; i++) {
			BlockDisplayElement bar = (BlockDisplayElement) fr.holder.getElements().get(i);
			bar.setBlockState(WHITE);
			bar.setYaw(yaw);
			bar.setTranslation(new Vector3f(bars[i][0], bars[i][1], z));
			bar.setScale(new Vector3f(bars[i][2], bars[i][3], 0.015f));
		}
		fr.holder.tick();
	}

	static void forget(UUID id) {
		Frame fr = FRAMES.remove(id);
		if (fr != null) fr.holder.destroy();
	}

	// ---------------------------------------------------------------- now and then

	/** Every two seconds: boards show where they are loaded; a board whose wall is gone (dug out, blown up) comes down. */
	static void tick(MinecraftServer server) {
		for (Wall w : new ArrayList<>(data().walls)) {
			ServerLevel level = level(server, w);
			if (level == null) continue;
			BlockPos origin = new BlockPos(w.x, w.y, w.z);
			if (!level.isLoaded(origin) || !level.isLoaded(spot(w, COLS - 1))) continue;
			Direction f = facing(w);
			boolean holds = true;
			for (int i = 0; i < SPOTS && holds; i++) {
				BlockPos back = spot(w, i).relative(f.getOpposite());
				holds = level.getBlockState(back).isFaceSturdy(level, back, f);
			}
			if (!holds) {
				remove(level, w, true);
				continue;
			}
			if (!SHOWN.containsKey(w)) show(level, w);
		}
	}

	static void clearAll() {
		SHOWN.values().forEach(ElementHolder::destroy);
		SHOWN.clear();
		FRAMES.values().forEach(f -> f.holder.destroy());
		FRAMES.clear();
	}

	// ---------------------------------------------------------------- for the screenshots and the self-test

	/** A board on the wall behind {@code origin} (its bottom-left spot), facing {@code facing}, with a story on it but part {@code skip}. */
	static Wall debug(ServerLevel level, BlockPos origin, Direction facing, String story, int skip) {
		Wall w = new Wall();
		w.dim = dim(level);
		w.facing = facing.getSerializedName();
		w.x = origin.getX();
		w.y = origin.getY();
		w.z = origin.getZ();
		w.story = story;
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < SPOTS; i++) items.add(ItemStack.EMPTY);
		for (ItemStack s : Showcase.storyItems(level, story)) {
			int part = Stand.stamp(s).getIntOr("part", 0);
			if (part >= 1 && part <= SPOTS && part != skip) items.set(part - 1, s);
		}
		data().walls.add(w);
		setItems(level, w, items);
		return w;
	}

	/** Which spots hold what (for the self-test): "" for empty. */
	static Set<Integer> full(ServerLevel level, Wall w) {
		Set<Integer> out = new TreeSet<>();
		List<ItemStack> items = items(level, w);
		for (int i = 0; i < SPOTS; i++) if (!items.get(i).isEmpty()) out.add(i);
		return out;
	}
}
