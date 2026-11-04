package nl.libreria.info;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Services;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Look info: a small floating card above the block, animal or player you are looking at (like Jade, without a client
 * mod). The card has the thing's icon on the left, a bold coloured name, detail lines with little bars, and a thin
 * coloured frame (purple for blocks, green for animals, red for monsters, gold for players). Only within your reach,
 * hidden while a container menu is open, and only visible to you.
 */
final class Look {
	/** What the player looks at: where the card floats, its icon, frame colour and lines. */
	record Target(Vec3 anchor, ItemStack icon, int frame, List<Component> lines) {
	}

	// Frame colours (ARGB)
	static final int FRAME_BLOCK = 0xFF5A2DBE;
	static final int FRAME_ANIMAL = 0xFF2E9B57;
	static final int FRAME_MONSTER = 0xFFB0302A;
	static final int FRAME_PLAYER = 0xFFD9A21E;

	/** Size of the card (text scale; 1 = name-tag size). */
	private static final float SCALE = 0.42f;
	/** One font pixel in blocks at scale 1. */
	private static final float PX = 0.025f;

	private static final class Tip {
		final ElementHolder holder = new ElementHolder();
		final TextDisplayElement frame = new TextDisplayElement();
		final TextDisplayElement text = new TextDisplayElement();
		final ItemDisplayElement icon = new ItemDisplayElement();
		ServerLevel level;
		Vec3 anchor = Vec3.ZERO;
		@Nullable Component shown;
		int shownFrame;
		ItemStack shownIcon = ItemStack.EMPTY;
	}

	private static final Map<UUID, Tip> TIPS = new HashMap<>();

	private Look() {
	}

	static void tick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			int mode = InfoMod.LOOK.get(p);
			// A chest, furnace… is open (your own inventory screen is invisible to the server)
			boolean menuOpen = p.containerMenu != p.inventoryMenu;
			Target t = mode == 0 || p.isSpectator() || menuOpen ? null : target(p, mode == 2);
			show(p, t);
		}
	}

	// ---------------------------------------------------------------- drawing the card

	private static Tip create(ServerPlayer p, Vec3 anchor) {
		Tip tip = new Tip();
		tip.level = p.level();
		tip.anchor = anchor;
		for (TextDisplayElement t : new TextDisplayElement[] {tip.frame, tip.text}) {
			t.setBillboardMode(Display.BillboardConstraints.CENTER);
			t.setLineWidth(400);
			t.setSeeThrough(true);
			t.setTeleportDuration(2);
			t.setShadow(true);
			t.setTextAlignment(Display.TextDisplay.Align.LEFT);
		}
		// The frame: same text, but invisible, on a coloured background that is a bit larger
		tip.frame.setTextOpacity((byte) 8);
		tip.text.setBackground(0xF0100010);
		tip.icon.setBillboardMode(Display.BillboardConstraints.CENTER);
		tip.icon.setItemDisplayContext(ItemDisplayContext.GUI);
		tip.icon.setTeleportDuration(2);
		tip.holder.addElement(tip.frame);
		tip.holder.addElement(tip.text);
		tip.holder.addElement(tip.icon);
		new ManualAttachment(tip.holder, tip.level, () -> tip.anchor);
		tip.holder.startWatching(p);
		return tip;
	}

	private static void show(ServerPlayer p, @Nullable Target t) {
		Tip tip = TIPS.get(p.getUUID());
		if (t == null) {
			if (tip != null && tip.shown != null) {
				tip.text.setText(Component.empty());
				tip.text.setBackground(0);
				tip.frame.setText(Component.empty());
				tip.frame.setBackground(0);
				tip.icon.setItem(ItemStack.EMPTY);
				tip.shown = null;
				tip.shownIcon = ItemStack.EMPTY;
				tip.holder.tick();
			}
			return;
		}
		if (tip == null || tip.level != p.level() || tip.holder.getAttachment() == null) {
			if (tip != null) tip.holder.destroy();
			tip = create(p, t.anchor());
			TIPS.put(p.getUUID(), tip);
		}
		tip.anchor = t.anchor();
		Component text = join(t.lines());
		boolean hasIcon = !t.icon().isEmpty();
		if (!Objects.equals(text, tip.shown) || tip.shownFrame != t.frame() || !ItemStack.isSameItemSameComponents(tip.shownIcon, t.icon())) {
			layout(tip, t, text, hasIcon);
			tip.shown = text;
			tip.shownFrame = t.frame();
			tip.shownIcon = t.icon().copy();
		}
		tip.holder.tick();
	}

	/** Size the frame around the text and put the icon on the left, vertically centred. */
	private static void layout(Tip tip, Target t, Component text, boolean hasIcon) {
		int widthPx = 0;
		for (Component line : t.lines()) widthPx = Math.max(widthPx, width(line));
		int lines = t.lines().size();
		// Room on the left for the icon: indent every line with spaces (a space is 4 px)
		int iconPx = hasIcon ? 22 : 0;
		widthPx += iconPx;
		int heightPx = lines * 10 + 1;
		float w = (widthPx + 2) * PX * SCALE, h = heightPx * PX * SCALE;
		Component padded = hasIcon ? indent(t.lines(), iconPx) : text;

		tip.text.setText(padded);
		tip.text.setBackground(0xF0100010);
		tip.text.setScale(new Vector3f(SCALE));
		tip.text.setTranslation(new Vector3f(0, 0, 0));

		// Frame: 2 px border on every side, slightly behind the card
		float border = 2 * PX * SCALE;
		tip.frame.setText(padded);
		tip.frame.setBackground(t.frame());
		tip.frame.setScale(new Vector3f(SCALE * (w + 2 * border) / w, SCALE * (h + 2 * border) / h, SCALE));
		tip.frame.setTranslation(new Vector3f(0, -border, -0.01f));

		if (hasIcon) {
			float iconSize = 16 * PX * SCALE * 1.25f;
			tip.icon.setItem(t.icon());
			tip.icon.setScale(new Vector3f(iconSize, iconSize, 0.001f));
			// Text is centred on the anchor: the left edge is at -w/2
			tip.icon.setTranslation(new Vector3f(-w / 2 + iconPx * PX * SCALE / 2 + PX * SCALE, h - Math.min(h / 2, 6 * PX * SCALE * 1.6f), 0.01f));
		} else {
			tip.icon.setItem(ItemStack.EMPTY);
		}
	}

	/** Every line starts with enough spaces to clear the icon. */
	private static Component indent(List<Component> lines, int px) {
		String pad = " ".repeat(Math.max(1, px / 4));
		MutableComponent out = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) out.append("\n");
			out.append(Component.literal(pad)).append(lines.get(i));
		}
		return out;
	}

	/** Rough width of a line in font pixels (Minecraft's default font). */
	static int width(Component c) {
		String s = c.getString();
		boolean bold = c.getStyle().isBold() || c.getSiblings().stream().anyMatch(x -> x.getStyle().isBold());
		int w = 0;
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			int cw;
			if ("il!.,:;|'".indexOf(ch) >= 0) cw = 2;
			else if ("I[]t ".indexOf(ch) >= 0) cw = 4;
			else if ("fk\"()*<>{}".indexOf(ch) >= 0) cw = 5;
			else if (ch == '@' || ch == '~') cw = 7;
			else if (ch > 0x2000) cw = 8; // symbols like ❤ ▮ ✔
			else cw = 6;
			w += cw + (bold ? 1 : 0);
		}
		return w;
	}

	static void forget(UUID id) {
		Tip tip = TIPS.remove(id);
		if (tip != null) tip.holder.destroy();
	}

	static void clearAll() {
		TIPS.values().forEach(t -> t.holder.destroy());
		TIPS.clear();
	}

	// ---------------------------------------------------------------- what are you looking at?

	/** The thing within reach that the player looks at, or null. */
	static @Nullable Target target(ServerPlayer p, boolean all) {
		ServerLevel level = p.level();
		Vec3 eye = p.getEyePosition();
		Vec3 dir = p.getViewVector(1.0f);
		// "Within the range of what I can place": the block interaction range
		double range = p.blockInteractionRange();
		BlockHitResult bh = level.clip(new ClipContext(eye, eye.add(dir.scale(range)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		double blockDistSq = bh.getType() == HitResult.Type.MISS ? range * range : bh.getLocation().distanceToSqr(eye);
		AABB box = p.getBoundingBox().expandTowards(dir.scale(range)).inflate(1.0);
		EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, eye, eye.add(dir.scale(range)), box,
				e -> !e.isSpectator() && e.isPickable() && !e.isInvisible(), blockDistSq);
		if (eh != null) {
			Entity e = eh.getEntity();
			List<Component> lines = describeEntity(p, e, all);
			if (lines == null) return null;
			// Above the head; for players also above their name tag and title
			double up = e instanceof Player ? 1.25 : 0.4;
			int frame = e instanceof Player ? FRAME_PLAYER : e instanceof Enemy ? FRAME_MONSTER : FRAME_ANIMAL;
			return new Target(e.position().add(0, e.getBbHeight() + up, 0), iconOf(e), frame, lines);
		}
		if (bh.getType() != HitResult.Type.BLOCK) return null;
		BlockPos pos = bh.getBlockPos();
		List<Component> lines = describeBlock(p, level, pos, all);
		if (lines == null) return null;
		BlockState st = level.getBlockState(pos);
		Item item = st.getBlock().asItem();
		return new Target(anchorFor(level, pos, bh, eye), item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item), FRAME_BLOCK, lines);
	}

	/** Above the block if there is room, otherwise just in front of the face you're looking at. */
	private static Vec3 anchorFor(ServerLevel level, BlockPos pos, BlockHitResult hit, Vec3 eye) {
		BlockState st = level.getBlockState(pos);
		double top = st.getShape(level, pos).isEmpty() ? 0.5 : st.getShape(level, pos).max(net.minecraft.core.Direction.Axis.Y);
		BlockPos above = pos.above();
		if (!level.getBlockState(above).isCollisionShapeFullBlock(level, above)) {
			return new Vec3(pos.getX() + 0.5, pos.getY() + top + 0.25, pos.getZ() + 0.5);
		}
		Vec3 towardsEye = eye.subtract(hit.getLocation()).normalize().scale(0.6);
		return hit.getLocation().add(towardsEye).add(0, 0.2, 0);
	}

	private static ItemStack iconOf(Entity e) {
		if (e instanceof ServerPlayer other) {
			return new GuiElementBuilder(Items.PLAYER_HEAD).setProfile(other.getGameProfile()).asStack();
		}
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
		return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg"))
				.map(ItemStack::new).orElse(ItemStack.EMPTY);
	}

	// ---------------------------------------------------------------- players and animals

	static @Nullable List<Component> describeEntity(ServerPlayer viewer, Entity e, boolean all) {
		List<Component> lines = new ArrayList<>();
		if (e instanceof ServerPlayer other) {
			// Players: who they are and how far they got (no health)
			lines.add(other.getDisplayName().copy().withStyle(ChatFormatting.BOLD));
			Services.progress().ifPresent(pr -> {
				Component title = pr.title(other);
				if (title != null) lines.add(title);
				lines.addAll(pr.card(other));
			});
			return lines;
		}
		ChatFormatting nameColor = e instanceof Enemy ? ChatFormatting.RED : ChatFormatting.GREEN;
		if (!(e instanceof LivingEntity living)) {
			if (!all) return null;
			lines.add(e.getDisplayName().copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
			return lines;
		}
		lines.add(e.getDisplayName().copy().withStyle(nameColor, ChatFormatting.BOLD));
		lines.add(hearts(living.getHealth(), living.getMaxHealth()));
		if (e instanceof AgeableMob mob) {
			int age = mob.getAge();
			if (age < 0) lines.add(Component.literal("✿ Baby · grows up in " + minutes(-age)).withStyle(ChatFormatting.AQUA));
			else if (e instanceof Animal a && a.isInLove()) lines.add(Component.literal("❤ In love").withStyle(ChatFormatting.LIGHT_PURPLE));
			else if (e instanceof Animal && age > 0) lines.add(Component.literal("⌛ Can breed again in " + minutes(age)).withStyle(ChatFormatting.GRAY));
		}
		if (e instanceof AbstractHorse horse && InfoMod.HORSE.get(viewer) && !horse.isBaby()) {
			lines.add(Component.literal("» Speed ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(Horses.fmt(Horses.speed(horse)) + " m/s").withStyle(ChatFormatting.WHITE)));
			lines.add(Component.literal("⤒ Jump ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(Horses.fmt(Horses.jump(horse)) + " blocks").withStyle(ChatFormatting.WHITE)));
		}
		return lines;
	}

	/** "❤ ▮▮▮▮▮▮▯▯ 7/10". */
	static MutableComponent hearts(float health, float max) {
		double f = max <= 0 ? 0 : health / max;
		return Component.literal("❤ ").withStyle(ChatFormatting.RED)
				.append(bar(f, 8))
				.append(Component.literal(" " + Horses.fmt(health / 2.0) + "/" + Horses.fmt(max / 2.0)).withStyle(ChatFormatting.WHITE));
	}

	/** Coloured bar without the grey "empty" part looking too heavy. */
	static MutableComponent bar(double fraction, int segments) {
		fraction = Math.max(0, Math.min(1, fraction));
		int full = (int) Math.round(fraction * segments);
		ChatFormatting color = fraction > 0.66 ? ChatFormatting.GREEN : fraction > 0.33 ? ChatFormatting.YELLOW : ChatFormatting.RED;
		return Component.literal("▮".repeat(full)).withStyle(color).append(Component.literal("▮".repeat(segments - full)).withStyle(ChatFormatting.DARK_GRAY));
	}

	private static MutableComponent row(String label, double fraction, String value, ChatFormatting valueColor) {
		return Component.literal(label + " ").withStyle(ChatFormatting.GRAY).append(bar(fraction, 8))
				.append(Component.literal(" " + value).withStyle(valueColor));
	}

	private static String minutes(int ticks) {
		int s = ticks / 20;
		return s >= 60 ? (s / 60) + " min" : s + " s";
	}

	// ---------------------------------------------------------------- blocks

	static @Nullable List<Component> describeBlock(ServerPlayer p, ServerLevel level, BlockPos pos, boolean all) {
		BlockState st = level.getBlockState(pos);
		if (st.isAir()) return null;
		Block block = st.getBlock();
		List<Component> details = new ArrayList<>();

		// Crop growth
		if (block instanceof CropBlock || block instanceof StemBlock || block instanceof SweetBerryBushBlock
				|| block instanceof NetherWartBlock || block instanceof CocoaBlock || block == net.minecraft.world.level.block.Blocks.PITCHER_CROP) {
			IntegerProperty age = intProperty(st, "age");
			if (age != null) {
				int v = st.getValue(age);
				int max = age.getPossibleValues().get(age.getPossibleValues().size() - 1);
				if (v >= max && !(block instanceof StemBlock)) details.add(Component.literal("✔ Ripe – ready to harvest").withStyle(ChatFormatting.GREEN));
				else details.add(row("Growth", (double) v / max, Math.round(100.0 * v / max) + "%", ChatFormatting.YELLOW));
			}
		}
		// Honey
		if (block instanceof BeehiveBlock) {
			int honey = st.getValue(BeehiveBlock.HONEY_LEVEL);
			details.add(honey >= BeehiveBlock.MAX_HONEY_LEVELS
					? Component.literal("✔ Full of honey").withStyle(ChatFormatting.GOLD)
					: row("Honey", (double) honey / BeehiveBlock.MAX_HONEY_LEVELS, honey + "/" + BeehiveBlock.MAX_HONEY_LEVELS, ChatFormatting.YELLOW));
		}
		// Composter
		if (block == net.minecraft.world.level.block.Blocks.COMPOSTER) {
			IntegerProperty lvl = intProperty(st, "level");
			if (lvl != null) {
				int v = st.getValue(lvl);
				details.add(v >= 8 ? Component.literal("✔ Bone meal ready").withStyle(ChatFormatting.GREEN)
						: row("Compost", v / 7.0, v + "/7", ChatFormatting.YELLOW));
			}
		}
		// Redstone signal
		IntegerProperty power = intProperty(st, "power");
		if (power != null) {
			int v = st.getValue(power);
			details.add(Component.literal("⚡ Power ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(String.valueOf(v)).withStyle(v > 0 ? ChatFormatting.RED : ChatFormatting.DARK_GRAY)));
		}
		// How full a chest, furnace, hopper… is
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof Container c && c.getContainerSize() > 0) {
			int used = 0, items = 0;
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (!s.isEmpty()) {
					used++;
					items += s.getCount();
				}
			}
			details.add(row("Slots", (double) used / c.getContainerSize(), used + "/" + c.getContainerSize(),
					used >= c.getContainerSize() ? ChatFormatting.GOLD : ChatFormatting.WHITE));
			if (items > 0) details.add(Component.literal("▣ " + items + (items == 1 ? " item" : " items")).withStyle(ChatFormatting.GRAY));
		}

		if (details.isEmpty() && !all) return null;
		List<Component> lines = new ArrayList<>();
		lines.add(block.getName().copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
		lines.addAll(details);
		return lines;
	}

	static @Nullable IntegerProperty intProperty(BlockState st, String name) {
		for (Property<?> prop : st.getProperties()) {
			if (prop instanceof IntegerProperty ip && ip.getName().equals(name)) return ip;
		}
		return null;
	}

	static Component join(List<Component> lines) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) out.append("\n");
			out.append(lines.get(i));
		}
		return out;
	}
}
