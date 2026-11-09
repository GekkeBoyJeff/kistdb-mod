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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
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
import net.minecraft.world.item.component.CustomData;
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
 * A <b>story wall</b>: pieces that hang on a wall (two planks, an item frame, a book and a gold nugget make two; in every
 * kind of wood), each one column of two spots of a block, one above the other. Pieces of the same wood hung side by side
 * join into one board, up to four columns (eight spots), with one frame round it and one brass plate over it; taking a
 * piece out of the middle splits it again. Every thing on it can be looked at, taken and put back on its own. Made for
 * the stories that are many of one thing (the six swords of an oath, the spears of four sisters) but it holds anything.
 *
 * <p>The first part of a story hung on a board lays it out for that story: part 1 top left, then on in reading order, and
 * the parts that are missing show as dark shapes in their spots until they're found; when boards join, the parts move to
 * their own spots. Right-click a spot with something: it hangs there (a part of its story: in its own spot). Right- or
 * left-click a full spot: that one thing comes off. Sneak + right-click: its stories as a book. A name tag with a name:
 * the plate says that instead of the story's title (an unnamed one puts the title back). Hit an empty spot of an empty
 * piece: that piece comes down. Look at a spot and a white frame shows which one, and its card says what's there.
 */
final class StoryWall {
	static final int ROWS = 2, MAX_COLS = 4, MAX_SPOTS = ROWS * MAX_COLS;
	/** On the crafted item ({@link Showcase#KEY}). */
	static final String KIND = "wall";

	public static final class Wall {
		public String dim = "minecraft:overworld";
		/** The bottom spot of its leftmost column (as you face it), in front of the wall. */
		public int x, y, z;
		/** Which way it faces (out of the wall). */
		public String facing = "south";
		/** The story it is laid out for, "" for none (yet). */
		public String story = "";
		/** What it's made of: a kind of wood ("dark_oak", "cherry"…). */
		public String wood = "dark_oak";
		/** Its own name on the plate; "" for the title of its story. */
		public String name = "";
		/** How many pieces (columns) it is, 1 to 4; 0 in boards from before pieces (four columns, the top row first). */
		public int cols;
		/** Column by column from the left, the top spot first (items as JSON; an empty object for an empty spot). */
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
		for (Wall w : d.walls) {
			if (w.cols > 0) continue;
			// A board of before the pieces: four columns, its spots row by row
			List<JsonElement> old = w.items == null ? List.of() : w.items, now = new ArrayList<>();
			for (int c = 0; c < MAX_COLS; c++) {
				for (int r = 0; r < ROWS; r++) {
					int i = r * MAX_COLS + c;
					now.add(i < old.size() ? old.get(i) : new JsonObject());
				}
			}
			w.items = now;
			w.cols = MAX_COLS;
			if (w.name == null) w.name = "";
			STORE.markDirty();
		}
		return d;
	}

	// ---------------------------------------------------------------- the woods

	/** Every kind of wood a story wall is made of: its planks for the board, its stripped wood for the frame. */
	static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak",
			"bamboo", "crimson", "warped");
	/** On the crafted item, next to {@link Showcase#KEY}: its wood. */
	static final String WOOD = "libreria_wood";

	static String wood(@Nullable String wood) {
		return wood != null && WOODS.contains(wood) ? wood : "dark_oak";
	}

	static BlockState planks(String wood) {
		return block(wood + "_planks", Blocks.DARK_OAK_PLANKS);
	}

	static BlockState frame(String wood) {
		String id = switch (wood) {
			case "bamboo" -> "stripped_bamboo_block";
			case "crimson", "warped" -> "stripped_" + wood + "_hyphae";
			default -> "stripped_" + wood + "_wood";
		};
		return block(id, Blocks.STRIPPED_DARK_OAK_WOOD);
	}

	private static BlockState block(String id, net.minecraft.world.level.block.Block fallback) {
		return BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(id)).orElse(fallback).defaultBlockState();
	}

	/** "Dark Oak" for "dark_oak". */
	static String woodName(String wood) {
		StringBuilder b = new StringBuilder();
		for (String w : wood.split("_")) b.append(b.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		return b.toString();
	}

	/** A piece of story wall of this wood as an item, as its recipe makes it. */
	static ItemStack item(String wood) {
		wood = wood(wood);
		ItemStack s = new ItemStack(Items.ITEM_FRAME);
		CompoundTag tag = new CompoundTag();
		tag.putString(Showcase.KEY, KIND);
		tag.putString(WOOD, wood);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		s.set(DataComponents.ITEM_NAME, Component.literal(woodName(wood) + " Story Wall"));
		s.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
				Ui.text("A piece for a wall: two spots, one", ChatFormatting.GRAY), Ui.text("above the other. Pieces of one wood", ChatFormatting.GRAY),
				Ui.text("side by side join, up to eight spots.", ChatFormatting.GRAY))));
		return s;
	}

	static String woodOf(ItemStack s) {
		CustomData cd = s.get(DataComponents.CUSTOM_DATA);
		return wood(cd == null ? null : cd.copyTag().getStringOr(WOOD, ""));
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

	static int cols(Wall w) {
		return Math.max(1, Math.min(MAX_COLS, w.cols));
	}

	static int spots(Wall w) {
		return cols(w) * ROWS;
	}

	static int col(int spot) {
		return spot / ROWS;
	}

	/** 0 = the top. */
	static int row(int spot) {
		return spot % ROWS;
	}

	static BlockPos origin(Wall w) {
		return new BlockPos(w.x, w.y, w.z);
	}

	/** The block in front of the wall where this spot is. */
	static BlockPos spot(Wall w, int spot) {
		return origin(w).relative(right(facing(w)), col(spot)).above(ROWS - 1 - row(spot));
	}

	/** The middle of the board, on the face of the wall. */
	static Vec3 middle(Wall w) {
		Direction f = facing(w), r = right(f);
		double half = (cols(w) - 1) / 2.0;
		return Vec3.atCenterOf(origin(w)).add(r.getStepX() * half, (ROWS - 1) / 2.0, r.getStepZ() * half).subtract(f.getStepX() * 0.5, 0,
				f.getStepZ() * 0.5);
	}

	/** A point on the board (x: to your right, y: up, z: out of the wall, from its middle) in the world. */
	static Vec3 at(Wall w, double x, double y, double z) {
		Direction f = facing(w), r = right(f);
		return middle(w).add(r.getStepX() * x + f.getStepX() * z, y, r.getStepZ() * x + f.getStepZ() * z);
	}

	/** Where the middle of a spot is on the board (x, y). */
	static double spotX(Wall w, int spot) {
		return col(spot) - (cols(w) - 1) / 2.0;
	}

	static double spotY(int spot) {
		return (ROWS - 1) / 2.0 - row(spot);
	}

	/** The spot of part {@code part} of the story it's laid out for: in reading order, the top row first. */
	static int home(Wall w, int part) {
		int i = part - 1, c = i % cols(w), r = i / cols(w);
		return c * ROWS + r;
	}

	/** Which part's own spot this is. */
	static int partAt(Wall w, int spot) {
		return row(spot) * cols(w) + col(spot) + 1;
	}

	/** The box you look at for a spot: a block wide and high, a little out from the wall. */
	static AABB box(Wall w, int spot) {
		return new AABB(at(w, spotX(w, spot) - 0.48, spotY(spot) - 0.48, 0), at(w, spotX(w, spot) + 0.48, spotY(spot) + 0.48, 0.2));
	}

	static @Nullable ServerLevel level(MinecraftServer server, Wall w) {
		return server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, Identifier.parse(w.dim)));
	}

	static String dim(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

	/** How far along the wall a block is, to your right (columns of one board are one apart). */
	static int across(BlockPos pos, Direction facing) {
		Direction r = right(facing);
		return pos.getX() * r.getStepX() + pos.getZ() * r.getStepZ();
	}

	/** How far out of the wall a block is (the spots of one board are all as far). */
	static int depth(BlockPos pos, Direction facing) {
		return pos.getX() * facing.getStepX() + pos.getZ() * facing.getStepZ();
	}

	// ---------------------------------------------------------------- what hangs on it

	static List<ItemStack> items(ServerLevel level, Wall w) {
		var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		List<ItemStack> out = new ArrayList<>();
		for (int i = 0; i < spots(w); i++) {
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
		if (!w.story.isEmpty() && items.stream().noneMatch(s -> w.story.equals(storyOf(s)))) w.story = "";
		STORE.markDirty();
		show(level, w);
	}

	static String storyOf(ItemStack s) {
		return Stand.stamp(s).getStringOr("story", "");
	}

	/** How many parts the story it is laid out for has (0: none, or more than it has spots). */
	static int laidOut(Wall w) {
		if (w.story.isEmpty()) return 0;
		Found.Text t = Found.STORIES.get(w.story);
		return t == null || t.parts() > spots(w) ? 0 : t.parts();
	}

	/** The parts of its story that hang on it. */
	static Set<Integer> present(Wall w, List<ItemStack> items) {
		Set<Integer> out = new TreeSet<>();
		for (ItemStack s : items) if (!s.isEmpty() && w.story.equals(storyOf(s))) out.add(Stand.stamp(s).getIntOr("part", 0));
		return out;
	}

	/** Kept free for a part of its story that isn't here yet. */
	static boolean kept(Wall w, List<ItemStack> items, int spot) {
		int part = partAt(w, spot);
		return part <= laidOut(w) && items.get(spot).isEmpty() && !present(w, items).contains(part);
	}

	/** The part of the story a thing is, if it's a part of a story that fits on a board: [story, part]. */
	static @Nullable Object[] part(ItemStack s) {
		CompoundTag st = Stand.stamp(s);
		String story = st.getStringOr("story", "");
		int part = st.getIntOr("part", 0);
		Found.Text t = story.isEmpty() ? null : Found.STORIES.get(story);
		if (t == null || t.parts() > MAX_SPOTS || part < 1 || part > t.parts()) return null;
		return new Object[] {story, part};
	}

	/** Where this thing goes when you click {@code clicked} with it, or -1 when there's no room. */
	static int spotFor(Wall w, List<ItemStack> items, ItemStack s, int clicked) {
		Object[] part = part(s);
		if (part != null) {
			String story = (String) part[0];
			// The first part of a story on an empty board lays it out for that story
			if (w.story.isEmpty() && items.stream().allMatch(ItemStack::isEmpty)) w.story = story;
			if (story.equals(w.story) && laidOut(w) > 0) {
				int home = home(w, (int) part[1]);
				if (items.get(home).isEmpty()) return home;
			}
		}
		if (items.get(clicked).isEmpty() && !kept(w, items, clicked)) return clicked;
		for (int i = 0; i < items.size(); i++) if (items.get(i).isEmpty() && !kept(w, items, i)) return i;
		return -1;
	}

	/** Every part of its story to its own spot, where that's free (after boards joined or a story was laid out anew). */
	static List<ItemStack> arrange(Wall w, List<ItemStack> items) {
		if (laidOut(w) == 0) return items;
		List<ItemStack> out = new ArrayList<>(items);
		for (int i = 0; i < out.size(); i++) {
			ItemStack s = out.get(i);
			if (s.isEmpty() || !w.story.equals(storyOf(s))) continue;
			int part = Stand.stamp(s).getIntOr("part", 0);
			if (part < 1 || part > laidOut(w)) continue;
			int home = home(w, part);
			if (home == i) continue;
			ItemStack there = out.get(home);
			// Its spot is empty, or holds something that isn't its story's own there: they swap
			if (there.isEmpty() || !(w.story.equals(storyOf(there)) && Stand.stamp(there).getIntOr("part", 0) == part)) {
				out.set(home, s);
				out.set(i, there);
				i = -1;
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- hanging pieces up, taking them down

	/**
	 * Hangs a piece where this player clicked a wall: the block they clicked and the one above it or below it (whichever
	 * is nearer to where they clicked; level with a piece of the same wood beside it), flat against it, when there's a
	 * flat wall there with room in front of it. Beside a board of the same wood it joins it.
	 */
	static @Nullable Wall place(ServerPlayer p, ServerLevel level, BlockHitResult hit, String wood) {
		Direction f = hit.getDirection();
		if (f.getAxis().isVertical()) {
			Hud.flash(p, Ui.text("Hang it on a wall: right-click the side of a block", ChatFormatting.GRAY));
			return null;
		}
		BlockPos front = hit.getBlockPos().relative(f);
		double up = hit.getLocation().y - hit.getBlockPos().getY();
		BlockPos bottom = up >= 0.5 ? front : front.below();
		// Level with a board beside it (same wood), if one hangs a row higher or lower
		for (BlockPos b : new BlockPos[] {front, front.below()}) {
			if (neighbour(level, b, f, wood(wood), -1) != null || neighbour(level, b, f, wood(wood), 1) != null) {
				bottom = b;
				break;
			}
		}
		for (BlockPos s : new BlockPos[] {bottom, bottom.above()}) {
			if (!Services.mayBuild(p, level, s)) return null;
		}
		Wall w = attach(level, bottom, f, wood);
		if (w == null) Hud.flash(p, Ui.text("No room: it needs a flat wall 2 high, with nothing in front", ChatFormatting.GRAY));
		return w;
	}

	/** Is there room for a piece with its bottom spot at {@code bottom}? */
	static boolean room(ServerLevel level, BlockPos bottom, Direction f) {
		for (BlockPos s : new BlockPos[] {bottom, bottom.above()}) {
			BlockPos back = s.relative(f.getOpposite());
			BlockState there = level.getBlockState(s);
			if (!level.getBlockState(back).isFaceSturdy(level, back, f) || !(there.canBeReplaced() || there.getCollisionShape(level, s).isEmpty())
					|| wallAt(level, s) != null) {
				return false;
			}
		}
		return true;
	}

	/** The board of this wood whose end column is right beside a piece at {@code bottom}: to its left (-1) or right (1). */
	static @Nullable Wall neighbour(ServerLevel level, BlockPos bottom, Direction f, String wood, int side) {
		String dim = dim(level);
		int a = across(bottom, f), d = depth(bottom, f);
		for (Wall w : data().walls) {
			if (!w.dim.equals(dim) || facing(w) != f || !w.wood.equals(wood) || w.y != bottom.getY() || depth(origin(w), f) != d) continue;
			int left = across(origin(w), f), rightmost = left + cols(w) - 1;
			if (side < 0 && rightmost == a - 1 || side > 0 && left == a + 1) return w;
		}
		return null;
	}

	/** A new piece with its bottom spot at {@code bottom}, joined to the boards beside it where they're of its wood and there's room. */
	static @Nullable Wall attach(ServerLevel level, BlockPos bottom, Direction f, String wood) {
		wood = wood(wood);
		if (!room(level, bottom, f)) return null;
		Wall left = neighbour(level, bottom, f, wood, -1), right = neighbour(level, bottom, f, wood, 1);
		if (left != null && right != null && cols(left) + 1 + cols(right) > MAX_COLS) {
			// Not both: the one it fits with, the left one first
			if (cols(left) + 1 > MAX_COLS) left = null;
			else right = null;
		}
		if (left != null && cols(left) + 1 > MAX_COLS) left = null;
		if (right != null && cols(right) + 1 > MAX_COLS) right = null;
		List<ItemStack> items = new ArrayList<>();
		Wall w;
		if (left != null) {
			w = left;
			items.addAll(items(level, left));
		} else {
			w = new Wall();
			w.dim = dim(level);
			w.facing = f.getSerializedName();
			w.wood = wood;
			w.x = bottom.getX();
			w.y = bottom.getY();
			w.z = bottom.getZ();
			w.cols = 0;
			data().walls.add(w);
		}
		items.add(ItemStack.EMPTY);
		items.add(ItemStack.EMPTY);
		int cols = (left != null ? cols(left) : 0) + 1;
		if (right != null) {
			items.addAll(items(level, right));
			cols += cols(right);
			if (w.story.isEmpty()) w.story = right.story;
			if (w.name.isEmpty()) w.name = right.name;
			hide(right);
			data().walls.remove(right);
		}
		hide(w);
		w.cols = cols;
		setItems(level, w, arrange(w, items));
		return w;
	}

	static @Nullable Wall wallAt(ServerLevel level, BlockPos pos) {
		String dim = dim(level);
		for (Wall w : data().walls) {
			if (!w.dim.equals(dim)) continue;
			for (int i = 0; i < spots(w); i++) if (spot(w, i).equals(pos)) return w;
		}
		return null;
	}

	/**
	 * One piece (column) comes down: what hung on it drops ({@code dropItems}), and the piece itself ({@code dropPiece});
	 * out of the middle, the board is two boards after.
	 */
	static void removeColumn(ServerLevel level, Wall w, int col, boolean dropPiece, boolean dropItems) {
		List<ItemStack> items = items(level, w);
		Vec3 at = at(w, col - (cols(w) - 1) / 2.0, -0.5, 0.6);
		if (dropItems) for (int r = 0; r < ROWS; r++) if (!items.get(col * ROWS + r).isEmpty()) Showcase.drop(level, at, items.get(col * ROWS + r));
		if (dropPiece) Showcase.drop(level, at, item(w.wood));
		int cols = cols(w);
		hide(w);
		if (cols == 1) {
			data().walls.remove(w);
			STORE.markDirty();
			return;
		}
		if (col == 0) {
			// The leftmost piece: the board starts one further
			BlockPos o = origin(w).relative(right(facing(w)), 1);
			w.x = o.getX();
			w.y = o.getY();
			w.z = o.getZ();
			w.cols = cols - 1;
			setItems(level, w, arrange(w, new ArrayList<>(items.subList(ROWS, items.size()))));
			return;
		}
		if (col < cols - 1) {
			// Out of the middle: what's right of it is a board of its own
			Wall right = new Wall();
			right.dim = w.dim;
			right.facing = w.facing;
			right.wood = w.wood;
			right.story = w.story;
			right.name = w.name;
			BlockPos o = origin(w).relative(right(facing(w)), col + 1);
			right.x = o.getX();
			right.y = o.getY();
			right.z = o.getZ();
			right.cols = cols - col - 1;
			data().walls.add(right);
			setItems(level, right, arrange(right, new ArrayList<>(items.subList((col + 1) * ROWS, items.size()))));
		}
		w.cols = col;
		setItems(level, w, arrange(w, new ArrayList<>(items.subList(0, col * ROWS))));
	}

	/** The whole board comes down: what hung on it drops, and its pieces when {@code dropPieces}. */
	static void remove(ServerLevel level, Wall w, boolean dropPieces) {
		Vec3 at = at(w, 0, -0.5, 0.6);
		for (ItemStack s : items(level, w)) if (!s.isEmpty()) Showcase.drop(level, at, s);
		if (dropPieces) {
			ItemStack pieces = item(w.wood);
			pieces.setCount(cols(w));
			Showcase.drop(level, at, pieces);
		}
		data().walls.remove(w);
		STORE.markDirty();
		hide(w);
	}

	static void hide(Wall w) {
		ElementHolder h = SHOWN.remove(w);
		if (h != null) h.destroy();
	}

	/** Right-click on a spot. */
	static void use(ServerPlayer p, Wall w, int spot) {
		ServerLevel level = p.level();
		if (!dim(level).equals(w.dim) || !data().walls.contains(w) || spot >= spots(w)) return;
		ItemStack hand = p.getMainHandItem();
		List<ItemStack> items = items(level, w);
		if (p.isShiftKeyDown() && hand.isEmpty()) {
			Showcase.read(p, "story wall", items);
			return;
		}
		if (!Services.mayBuild(p, level, spot(w, spot))) {
			if (hand.isEmpty()) Showcase.read(p, "story wall", items);
			else Hud.flash(p, Ui.text("You can't change what hangs here", ChatFormatting.GRAY));
			return;
		}
		if (hand.is(Items.NAME_TAG)) {
			name(p, w, hand);
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
		hang(p, w, spot);
	}

	/** A name tag with a name: the plate says that; one without: the story's title again. */
	static void name(ServerPlayer p, Wall w, ItemStack tag) {
		Component custom = tag.get(DataComponents.CUSTOM_NAME);
		if (custom == null) {
			if (w.name.isEmpty()) {
				Hud.flash(p, Ui.text("Name the tag on an anvil first: the plate will say that", ChatFormatting.GRAY));
				return;
			}
			w.name = "";
			Hud.flash(p, Ui.text("The plate has its story's title again", ChatFormatting.GRAY));
		} else {
			w.name = custom.getString().strip();
			if (!p.hasInfiniteMaterials()) tag.shrink(1);
			Hud.flash(p, Ui.text("The plate now says \"" + w.name + "\"", ChatFormatting.GRAY));
		}
		STORE.markDirty();
		show(p.level(), w);
		Ui.sound(p, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.8f, 1.2f);
	}

	/** Hangs what this player holds, where they clicked (or in its own spot, for a part of the story it's laid out for). */
	static void hang(ServerPlayer p, Wall w, int spot) {
		ServerLevel level = p.level();
		ItemStack hand = p.getMainHandItem();
		List<ItemStack> items = items(level, w);
		if (hand.isEmpty()) return;
		boolean wasEmpty = items.stream().allMatch(ItemStack::isEmpty);
		int to = spotFor(w, items, hand, spot);
		if (to < 0) {
			Hud.flash(p, Ui.text("No room: the empty spots are kept for its story's other parts", ChatFormatting.GRAY));
			return;
		}
		items.set(to, p.hasInfiniteMaterials() ? hand.copyWithCount(1) : hand.split(1));
		// The first part of a story on an empty board: what was there before moves aside (nothing was)
		setItems(level, w, wasEmpty ? items : arrange(w, items));
		Ui.sound(p, SoundEvents.ITEM_FRAME_ADD_ITEM, 0.9f, 1.0f);
		Showcase.shown(p, items);
	}

	/** Left-click on a spot: that thing comes off; an empty piece comes down. */
	static void hit(ServerPlayer p, Wall w, int spot) {
		ServerLevel level = p.level();
		if (!dim(level).equals(w.dim) || !data().walls.contains(w) || spot >= spots(w) || !Services.mayBuild(p, level, spot(w, spot))) return;
		List<ItemStack> items = items(level, w);
		if (!items.get(spot).isEmpty()) {
			take(p, w, spot);
			return;
		}
		int col = col(spot);
		boolean empty = true;
		for (int r = 0; r < ROWS; r++) empty &= items.get(col * ROWS + r).isEmpty();
		if (empty) {
			removeColumn(level, w, col, !p.hasInfiniteMaterials(), true);
			Ui.sound(p, SoundEvents.WOOD_BREAK, 1f, 1f);
			return;
		}
		Hud.flash(p, Ui.text("Take what hangs on this piece off first, then hit it to take it down", ChatFormatting.GRAY));
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
	/**
	 * How far out from the wall the board's elements are anchored (they are drawn back against it): a display takes its
	 * light from the block it stands in, and in the wall that's dark.
	 */
	private static final float OUT = 0.5f;
	private static final int TITLE_COLOUR = 0x3A2508;

	private static BlockDisplayElement slab(BlockState state, float x, float y, float z, float w, float h, float d, float yaw) {
		BlockDisplayElement e = new BlockDisplayElement(state);
		e.setTranslation(new Vector3f(x, y, z - OUT));
		e.setScale(new Vector3f(w, h, d));
		e.setYaw(yaw);
		e.setViewRange(0.8f);
		return e;
	}

	/** Shows (again) a board and what hangs on it. */
	static void show(ServerLevel level, Wall w) {
		Direction f = facing(w);
		float yaw = f.toYRot();
		int cols = cols(w), spots = spots(w);
		ElementHolder holder = SHOWN.get(w);
		if (holder != null && holder.getElements().size() != elements(cols)) {
			hide(w);
			holder = null;
		}
		if (holder == null) {
			holder = new ElementHolder();
			for (int i = 0; i < spots; i++) {
				ItemDisplayElement e = new ItemDisplayElement();
				e.setItemDisplayContext(ItemDisplayContext.FIXED);
				e.setViewRange(0.8f);
				e.setYaw(yaw);
				e.setTranslation(new Vector3f((float) spotX(w, i), (float) spotY(i), RIM - 0.02f - OUT));
				e.setScale(new Vector3f(0.78f));
				holder.addElement(e);
			}
			// The brass plate with its title, over the top of the frame
			holder.addElement(slab(Blocks.GOLD_BLOCK.defaultBlockState(), -0.5f, ROWS / 2f, RIM - 0.02f, 1f, 0.3f, 0.04f, yaw));
			TextDisplayElement title = new TextDisplayElement();
			title.setYaw(yaw);
			title.setBackground(0);
			title.setShadow(false);
			title.setViewRange(0.6f);
			holder.addElement(title);
			// The board: planks of its wood, framed and parted into its spots with the same wood, stripped
			BlockState board = planks(wood(w.wood)), rim = frame(wood(w.wood));
			float hw = cols / 2f, hh = ROWS / 2f;
			holder.addElement(slab(board, -hw, -hh, 0, cols, ROWS, DEPTH, yaw));
			holder.addElement(slab(rim, -hw, hh - FRAME, 0, cols, FRAME, RIM, yaw));
			holder.addElement(slab(rim, -hw, -hh, 0, cols, FRAME, RIM, yaw));
			holder.addElement(slab(rim, -hw, -hh, 0, FRAME, ROWS, RIM, yaw));
			holder.addElement(slab(rim, hw - FRAME, -hh, 0, FRAME, ROWS, RIM, yaw));
			for (int c = 1; c < cols; c++) holder.addElement(slab(rim, c - hw - BAR / 2, -hh, 0, BAR, ROWS, RIM - 0.01f, yaw));
			for (int r = 1; r < ROWS; r++) holder.addElement(slab(rim, -hw, r - hh - BAR / 2, 0, cols, BAR, RIM - 0.01f, yaw));
			// What you click: a box over each spot
			Vec3 anchor = at(w, 0, 0, OUT);
			for (int i = 0; i < spots; i++) {
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
				click.setResponse(true);
				click.setOffset(at(w, spotX(w, i), spotY(i) - 0.46, 0.04).subtract(anchor));
				holder.addElement(click);
			}
			ChunkAttachment.ofTicking(holder, level, anchor);
			SHOWN.put(w, holder);
		}
		List<ItemStack> items = items(level, w);
		int laid = laidOut(w);
		Set<Integer> here = present(w, items);
		Map<Integer, String> kinds = laid > 0 ? kinds(w.story) : Map.of();
		for (int i = 0; i < spots; i++) {
			ItemDisplayElement e = (ItemDisplayElement) holder.getElements().get(i);
			ItemStack s = items.get(i);
			int part = partAt(w, i);
			if (s.isEmpty() && part <= laid && !here.contains(part) && kinds.containsKey(part)) {
				// A part not found yet: its shape, dark
				e.setItem(new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(kinds.get(part))).orElse(Items.AIR)));
				e.setBrightness(new Brightness(0, 0));
			} else {
				e.setItem(s.copy());
				e.setBrightness(null);
			}
			// A shield is drawn small in a frame: as big as the rest
			e.setScale(new Vector3f(e.getItem().is(Items.SHIELD) ? 1.1f : 0.78f));
		}
		title(w, items, (BlockDisplayElement) holder.getElements().get(spots), (TextDisplayElement) holder.getElements().get(spots + 1));
		holder.tick();
	}

	/** How many elements a board of this many columns has (see {@link #show}). */
	private static int elements(int cols) {
		return cols * ROWS * 2 + 2 + 5 + (cols - 1) + (ROWS - 1);
	}

	/** The plate: its own name, else its story's title (else the story most of it is from), on as many lines as it needs. */
	private static void title(Wall w, List<ItemStack> items, BlockDisplayElement plate, TextDisplayElement title) {
		String story = !w.story.isEmpty() ? w.story : most(items);
		Found.Text info = story == null ? null : Found.STORIES.get(story);
		String text = !w.name.isEmpty() ? w.name : info == null ? "Story Wall" : info.title();
		Style style = Style.EMPTY.withColor(TITLE_COLOUR);
		float room = cols(w) - 0.24f;
		float scale = 0.42f;
		List<String> lines = wrap(text, style, room / (0.025f * scale));
		while (lines.size() > 2 && scale > 0.2f) {
			scale *= 0.85f;
			lines = wrap(text, style, room / (0.025f * scale));
		}
		int widest = 0;
		for (String l : lines) widest = Math.max(widest, Glyphs.width(Component.literal(l).withStyle(style)));
		float textW = widest * 0.025f * scale, lineH = 0.25f * scale;
		float plateW = Math.max(0.7f, Math.min(cols(w) - 0.1f, textW + 0.3f)), plateH = lines.size() * lineH + 0.14f, plateY = ROWS / 2f - 0.03f;
		plate.setTranslation(new Vector3f(-plateW / 2, plateY, RIM - 0.02f - OUT));
		plate.setScale(new Vector3f(plateW, plateH, 0.04f));
		title.setText(Component.literal(String.join("\n", lines)).withStyle(style));
		title.setLineWidth(10000);
		title.setScale(new Vector3f(scale));
		title.setTranslation(new Vector3f(0, plateY + 0.07f - 0.01f, RIM + 0.025f - OUT));
	}

	/** Words into lines no wider than {@code px}. */
	static List<String> wrap(String text, Style style, float px) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+")) {
			String tryLine = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && Glyphs.width(Component.literal(tryLine).withStyle(style)) > px) {
				out.add(line.toString());
				line.setLength(0);
				line.append(word);
			} else {
				line.setLength(0);
				line.append(tryLine);
			}
		}
		if (!line.isEmpty()) out.add(line.toString());
		return out;
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
			String story = storyOf(s);
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
			for (int i = 0; i < spots(w); i++) {
				Optional<Vec3> on = box(w, i).clip(from, to);
				if (on.isEmpty()) continue;
				double d = on.get().distanceTo(from);
				if (best == null || d < best.distance()) best = new Hit(w, i, d);
			}
		}
		return best;
	}

	/** Where the card of a spot floats without the resource pack: over the board, above its column. */
	static Vec3 anchor(Wall w, int spot) {
		return at(w, spotX(w, spot), ROWS / 2.0 + 0.55, 0.3);
	}

	/** The card of a spot: what hangs there and which story it's from; a missing part; or an empty spot. */
	static Look.Card card(ServerLevel level, Wall w, int spot) {
		List<ItemStack> items = items(level, w);
		ItemStack s = items.get(spot);
		ItemStack icon = item(w.wood);
		if (s.isEmpty()) {
			if (kept(w, items, spot)) {
				Found.Text info = Found.STORIES.get(w.story);
				return new Look.Card(icon, Found.HEAD, Component.literal("???").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD),
						Component.literal("Part " + partAt(w, spot) + " of " + info.parts() + " · " + info.title()).withStyle(ChatFormatting.GRAY),
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
		int cols;
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
		if (fr.wall == hit.wall() && fr.spot == hit.spot() && fr.cols == cols(hit.wall())) return;
		fr.wall = hit.wall();
		fr.spot = hit.spot();
		fr.cols = cols(hit.wall());
		fr.at = at(hit.wall(), spotX(hit.wall(), hit.spot()), spotY(hit.spot()), OUT);
		float yaw = facing(hit.wall()).toYRot(), half = 0.5f - BAR / 2, t = 0.035f, z = RIM + 0.005f;
		float[][] bars = {{-half, half - t, 2 * half, t}, {-half, -half, 2 * half, t}, {-half, -half, t, 2 * half}, {half - t, -half, t, 2 * half}};
		for (int i = 0; i < 4; i++) {
			BlockDisplayElement bar = (BlockDisplayElement) fr.holder.getElements().get(i);
			bar.setBlockState(WHITE);
			bar.setYaw(yaw);
			bar.setTranslation(new Vector3f(bars[i][0], bars[i][1], z - OUT));
			bar.setScale(new Vector3f(bars[i][2], bars[i][3], 0.015f));
		}
		fr.holder.tick();
	}

	static void forget(UUID id) {
		Frame fr = FRAMES.remove(id);
		if (fr != null) fr.holder.destroy();
	}

	// ---------------------------------------------------------------- now and then

	/**
	 * Every two seconds: boards show where they are loaded (nothing is kept loaded for them); a piece whose wall is gone
	 * (dug out, blown up) comes down, with what hung on it.
	 */
	static void tick(MinecraftServer server) {
		for (Wall w : new ArrayList<>(data().walls)) {
			if (!data().walls.contains(w)) continue;
			ServerLevel level = level(server, w);
			if (level == null) continue;
			if (!level.isLoaded(origin(w)) || !level.isLoaded(spot(w, spots(w) - 1))) continue;
			Direction f = facing(w);
			int gone = -1;
			for (int i = 0; i < spots(w) && gone < 0; i++) {
				BlockPos back = spot(w, i).relative(f.getOpposite());
				if (!level.getBlockState(back).isFaceSturdy(level, back, f)) gone = col(i);
			}
			if (gone >= 0) {
				removeColumn(level, w, gone, true, true);
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

	/**
	 * A board of {@code cols} pieces on the wall behind {@code origin} (its bottom-left spot), facing {@code facing}, with a
	 * story on it but part {@code skip}.
	 */
	static Wall debug(ServerLevel level, BlockPos origin, Direction facing, String story, int skip, String wood, int cols) {
		Wall w = new Wall();
		w.wood = wood(wood);
		w.dim = dim(level);
		w.facing = facing.getSerializedName();
		w.x = origin.getX();
		w.y = origin.getY();
		w.z = origin.getZ();
		w.story = story;
		w.cols = cols;
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < spots(w); i++) items.add(ItemStack.EMPTY);
		for (ItemStack s : Showcase.storyItems(level, story)) {
			int part = Stand.stamp(s).getIntOr("part", 0);
			if (part >= 1 && part <= spots(w) && part != skip) items.set(home(w, part), s);
		}
		data().walls.add(w);
		setItems(level, w, items);
		return w;
	}

	/** Which spots hold something (for the self-test). */
	static Set<Integer> full(ServerLevel level, Wall w) {
		Set<Integer> out = new TreeSet<>();
		List<ItemStack> items = items(level, w);
		for (int i = 0; i < items.size(); i++) if (!items.get(i).isEmpty()) out.add(i);
		return out;
	}
}
