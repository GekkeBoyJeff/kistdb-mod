package nl.libreria.info;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Brightness;
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
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
import nl.libreria.core.Pack;
import nl.libreria.core.Services;
import nl.libreria.core.gui.Glyphs;
import nl.libreria.core.gui.TopCard;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Look info: a small floating card above the block, animal or player you are looking at (like Jade, without a client
 * mod). With the Libreria resource pack it is an "adventure" card: a dark leather panel with a bronze frame, the
 * thing's icon in a little slot, a gem in the colour of its grade (only rare things get a coloured gem), an ornamental
 * rule and rows with small pictures and pips. Without the pack it falls back to a plain dark card. Only within your
 * reach, hidden while a container menu is open, and only visible to you.
 */
final class Look {
	/** One detail row: a little picture, a label, an optional bar and a value. */
	record Row(String icon, String symbol, String label, double fraction, int barColor, int segments, Component value) {
		static Row text(String icon, String symbol, String label, Component value) {
			return new Row(icon, symbol, label, -1, 0, 0, value);
		}

		static Row bar(String icon, String symbol, String label, double fraction, int segments, Component value) {
			return new Row(icon, symbol, label, fraction, Look.barColor(fraction), segments, value);
		}

		String plain() {
			return (label + " " + value.getString()).trim();
		}
	}

	/** What the card shows. accent = colour of the grade gem (-1 = plain bronze). */
	record Card(ItemStack icon, int accent, Component name, Component subtitle, List<Row> rows) {
		String plain() {
			StringBuilder b = new StringBuilder(name.getString()).append('\n').append(subtitle.getString());
			for (Row r : rows) b.append('\n').append(r.plain());
			return b.toString();
		}
	}

	/** Where the card floats. hud = shown at the top of the screen instead (blocks and things that aren't alive). */
	record Target(Vec3 anchor, Card card, boolean hud) {
	}

	static final int BRONZE = 0xF2C36B;
	static final int RED = 0xFF5555, GREEN = 0x55FF55, YELLOW = 0xFFFF55, AQUA = 0x55FFFF, PINK = 0xFF55FF, GOLD = 0xFFAA00;

	/** Size of the card (text scale; 1 = name-tag size). */
	private static final float SCALE = 0.42f;
	/** One font pixel in blocks at scale 1. */
	private static final float PX = 0.025f;
	private static final Brightness FULL = new Brightness(15, 15);
	private static final float ICON_TILT = 0.5f;

	private static final class Tip {
		final ElementHolder holder = new ElementHolder();
		final TextDisplayElement panel = new TextDisplayElement();
		final TextDisplayElement text = new TextDisplayElement();
		final ItemDisplayElement icon = new ItemDisplayElement();
		ServerLevel level;
		Vec3 anchor = Vec3.ZERO;
		@Nullable String shown;
	}

	private static final Map<UUID, Tip> TIPS = new HashMap<>();
	/** Channel of the top-of-screen tooltip (a {@link TopCard}). */
	private static final String TOP = "info.look";

	private Look() {
	}

	static void tick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			int mode = InfoMod.LOOK.get(p);
			// A chest, furnace… is open (your own inventory screen is invisible to the server)
			boolean menuOpen = p.containerMenu != p.inventoryMenu;
			Target t = mode == 0 || p.isSpectator() || menuOpen || Services.inCamera(p) ? null : target(p, mode == 2);
			boolean top = t != null && t.hud() && Pack.has(p);
			show(p, top ? null : t);
			showTop(p, top ? t.card() : null);
		}
	}

	// ---------------------------------------------------------------- drawing the card

	private static Tip create(ServerPlayer p, Vec3 anchor) {
		Tip tip = new Tip();
		tip.level = p.level();
		tip.anchor = anchor;
		for (TextDisplayElement t : new TextDisplayElement[] {tip.panel, tip.text}) {
			t.setBillboardMode(Display.BillboardConstraints.CENTER);
			t.setLineWidth(1000);
			t.setSeeThrough(false);
			t.setTeleportDuration(2);
			t.setBrightness(FULL);
			t.setBackground(0);
		}
		tip.icon.setBillboardMode(Display.BillboardConstraints.CENTER);
		tip.icon.setItemDisplayContext(ItemDisplayContext.GUI);
		tip.icon.setTeleportDuration(2);
		tip.icon.setBrightness(FULL);
		tip.holder.addElement(tip.panel);
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
				tip.panel.setText(Component.empty());
				tip.panel.setBackground(0);
				tip.icon.setItem(ItemStack.EMPTY);
				tip.shown = null;
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
		boolean pack = Pack.has(p);
		Card c = t.card();
		String key = pack + "|" + c.accent() + "|" + c.name().getString() + "|" + c.subtitle().getString() + "|" + rowsKey(c.rows())
				+ "|" + BuiltInRegistries.ITEM.getKey(c.icon().getItem()) + c.icon().getComponentsPatch();
		if (!Objects.equals(key, tip.shown)) {
			if (pack) layoutPack(tip, c);
			else layoutPlain(tip, c);
			tip.shown = key;
		}
		tip.holder.tick();
	}

	private static String rowsKey(List<Row> rows) {
		StringBuilder b = new StringBuilder();
		for (Row r : rows) b.append(r.icon()).append(r.label()).append(r.fraction()).append(r.value().getString()).append(';');
		return b.toString();
	}

	/** With the resource pack: panel + gem + slot + rule + rows with pictures. */
	private static void layoutPack(Tip tip, Card c) {
		boolean hasIcon = !c.icon().isEmpty();
		int indent = hasIcon ? 24 : 0;
		List<MutableComponent> rows = new ArrayList<>();
		for (Row r : c.rows()) rows.add(packRow(r));

		// Exact width of the content
		int nameW = indent + Glyphs.width(c.name());
		int subW = indent + Glyphs.width(c.subtitle());
		int innerW = Math.max(Math.max(nameW, subW), Glyphs.width(rows)) + 2;
		innerW = Math.max(innerW, 64);
		int gemX = innerW / 2 - 3;

		List<Component> lines = new ArrayList<>();
		int gem = c.accent() < 0 ? BRONZE : c.accent();
		MutableComponent first = Component.empty()
				.append(Glyphs.ui(Glyphs.space(gemX) + Glyphs.g("gem") + Glyphs.space(-(gemX + Glyphs.advance("gem"))), gem));
		if (hasIcon) first.append(Glyphs.ui(Glyphs.g("slot") + Glyphs.space(indent - Glyphs.advance("slot"))));
		first.append(c.name());
		lines.add(first);
		lines.add(Component.empty().append(Glyphs.ui(Glyphs.space(indent))).append(c.subtitle()));
		if (!rows.isEmpty()) {
			// The rule is exactly as wide as the card, which also pins the text box to the panel
			lines.add(Glyphs.divider(innerW));
			lines.addAll(rows);
		}
		int n = lines.size();

		tip.text.setText(join(lines));
		tip.text.setTextAlignment(Display.TextDisplay.Align.LEFT);
		tip.text.setShadow(true);
		tip.text.setBackground(0);
		tip.text.setTextOpacity((byte) -1);
		tip.text.setScale(new Vector3f(SCALE));
		tip.text.setTranslation(new Vector3f(0, 0, 0));

		tip.panel.setText(Glyphs.panel(n, innerW));
		tip.panel.setTextAlignment(Display.TextDisplay.Align.CENTER);
		tip.panel.setShadow(false);
		tip.panel.setBackground(0);
		tip.panel.setTextOpacity((byte) -1);
		tip.panel.setScale(new Vector3f(SCALE));
		tip.panel.setTranslation(new Vector3f(0, 0, -0.012f));

		if (hasIcon) {
			// The slot spans the first two lines; the text box is centred: its left edge is at 1 - innerW/2
			int contentH = n * Glyphs.lineHeight - 1;
			float s = PX * SCALE;
			float x = (1 - innerW / 2f + 10) * s;
			float y = (contentH - 9) * s;
			float size = 16 * s;
			tip.icon.setItem(c.icon());
			// Tilted back a little so the light from above reaches it (a flat icon facing you looks dark otherwise)
			tip.icon.setLeftRotation(new Quaternionf().rotateX(-ICON_TILT));
			tip.icon.setScale(new Vector3f(size, size / (float) Math.cos(ICON_TILT), 0.001f));
			tip.icon.setTranslation(new Vector3f(x, y, 0.06f));
		} else {
			tip.icon.setItem(ItemStack.EMPTY);
		}
	}

	private static MutableComponent packRow(Row r) {
		MutableComponent out = Component.empty().append(Glyphs.icon(r.icon()));
		if (!r.label().isEmpty()) out.append(Component.literal(r.label() + " ").withStyle(ChatFormatting.GRAY));
		if (r.fraction() >= 0) {
			int full = (int) Math.round(Math.max(0, Math.min(1, r.fraction())) * r.segments());
			out.append(Glyphs.pips(full, r.segments(), r.barColor())).append(Component.literal(" "));
		}
		return out.append(r.value());
	}

	/** Without the pack: a plain dark card with a thin bronze (or grade coloured) frame. */
	private static void layoutPlain(Tip tip, Card c) {
		boolean hasIcon = !c.icon().isEmpty();
		List<Component> lines = new ArrayList<>();
		lines.add(c.name());
		lines.add(c.subtitle());
		for (Row r : c.rows()) {
			MutableComponent line = Component.literal(r.symbol() + " ").withStyle(s -> s.withColor(r.barColor() == 0 ? 0xAAAAAA : r.barColor()));
			if (!r.label().isEmpty()) line.append(Component.literal(r.label() + " ").withStyle(ChatFormatting.GRAY));
			if (r.fraction() >= 0) line.append(bar(r.fraction(), r.segments())).append(Component.literal(" "));
			lines.add(line.append(r.value()));
		}
		int iconPx = hasIcon ? 22 : 0;
		int widthPx = 0;
		for (Component l : lines) widthPx = Math.max(widthPx, Glyphs.width(l));
		widthPx += iconPx;
		int heightPx = lines.size() * Glyphs.lineHeight + 1;
		float w = (widthPx + 2) * PX * SCALE, h = heightPx * PX * SCALE;
		Component padded = hasIcon ? indent(lines, iconPx) : join(lines);

		tip.text.setText(padded);
		tip.text.setTextAlignment(Display.TextDisplay.Align.LEFT);
		tip.text.setShadow(true);
		tip.text.setBackground(0xF0201510);
		tip.text.setTextOpacity((byte) -1);
		tip.text.setScale(new Vector3f(SCALE));
		tip.text.setTranslation(new Vector3f(0, 0, 0));

		// Frame: same text, invisible, on a slightly larger coloured background behind the card
		float border = 2 * PX * SCALE;
		tip.panel.setText(padded);
		tip.panel.setTextAlignment(Display.TextDisplay.Align.LEFT);
		tip.panel.setShadow(false);
		tip.panel.setTextOpacity((byte) 8);
		tip.panel.setBackground(0xFF000000 | (c.accent() < 0 ? 0xA8803C : c.accent()));
		tip.panel.setScale(new Vector3f(SCALE * (w + 2 * border) / w, SCALE * (h + 2 * border) / h, SCALE));
		tip.panel.setTranslation(new Vector3f(0, -border, -0.012f));

		if (hasIcon) {
			float iconSize = 16 * PX * SCALE * 1.25f;
			tip.icon.setItem(c.icon());
			tip.icon.setScale(new Vector3f(iconSize, iconSize, 0.001f));
			tip.icon.setTranslation(new Vector3f(-w / 2 + iconPx * PX * SCALE / 2 + PX * SCALE, h - Math.min(h / 2, 6 * PX * SCALE * 1.6f), 0.012f));
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

	static void forget(UUID id) {
		Tip tip = TIPS.remove(id);
		if (tip != null) tip.holder.destroy();
	}

	static void clearAll() {
		TIPS.values().forEach(t -> t.holder.destroy());
		TIPS.clear();
	}

	// ---------------------------------------------------------------- the card at the top of the screen

	private static void showTop(ServerPlayer p, @Nullable Card c) {
		if (c == null) {
			TopCard.hide(p, TOP);
			return;
		}
		List<TopCard.Row> rows = new ArrayList<>();
		for (Row r : c.rows()) rows.add(new TopCard.Row(r.icon(), r.label(), r.fraction(), r.barColor(), r.segments(), r.value()));
		TopCard.show(p, TOP, new TopCard.Card(c.icon(), c.accent(), c.name(), c.subtitle(), rows), 0);
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
			Card card = describeEntity(p, e, all);
			if (card == null) return null;
			// Above the head. The vanilla name tag (players, and any mob with its own name such as a
			// named villager) floats half a block above the head while you look at it, so the card
			// goes above that; players also get room for a scoreboard line under their name.
			double up = e instanceof Player ? 1.25 : e.hasCustomName() ? 0.9 : 0.4;
			Vec3 above = e.position().add(0, e.getBbHeight() + up, 0);
			return new Target(fits(eye, dir, above) ? above : beside(eye, e), card, !(e instanceof LivingEntity));
		}
		if (bh.getType() != HitResult.Type.BLOCK) return null;
		BlockPos pos = bh.getBlockPos();
		Card card = describeBlock(p, level, pos, all);
		if (card == null) return null;
		return new Target(anchorFor(level, pos, bh, eye), card, true);
	}

	/**
	 * Would a card here still be on your screen? Up close, the spot above someone's head is above the top of the screen
	 * (the screen reaches about 35 degrees up and down from where you look; the card needs a little room too).
	 */
	static boolean fits(Vec3 eye, Vec3 dir, Vec3 at) {
		Vec3 to = at.subtract(eye);
		// up and down is what runs out first (the screen is wider than it is high)
		double look = Math.atan2(dir.y, Math.hypot(dir.x, dir.z)), there = Math.atan2(to.y, Math.hypot(to.x, to.z));
		return Math.abs(Math.toDegrees(there - look)) <= 26;
	}

	/** Beside someone, at head height, on your right: for when the spot above their head is off your screen. */
	static Vec3 beside(Vec3 eye, Entity e) {
		double dx = e.getX() - eye.x, dz = e.getZ() - eye.z;
		double len = Math.max(1.0e-4, Math.sqrt(dx * dx + dz * dz));
		// your right, as you face them
		double rx = -dz / len, rz = dx / len;
		double out = e.getBbWidth() / 2 + 0.45;
		return new Vec3(e.getX() + rx * out, e.getEyeY() - 0.2, e.getZ() + rz * out);
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

	static @Nullable Card describeEntity(ServerPlayer viewer, Entity e, boolean all) {
		List<Row> rows = new ArrayList<>();
		if (e instanceof ServerPlayer other) {
			// Players: who they are and how far they got (no health)
			Component name = other.getDisplayName().copy().withStyle(ChatFormatting.BOLD);
			var pr = Services.progress();
			Component title = pr.map(x -> x.title(other)).orElse(null);
			int accent = pr.map(x -> x.titleColor(other)).orElse(-1);
			Component sub = title != null ? title : Component.literal("Player").withStyle(ChatFormatting.GRAY);
			Services.CardData d = pr.map(x -> x.cardData(other)).orElse(null);
			if (d != null) {
				rows.add(Row.text("star", "★", "", Component.literal(String.format(Locale.US, "%,d", d.points()) + " points").withStyle(ChatFormatting.GOLD)));
				rows.add(new Row("trophy", "✦", "Achievements", d.earned() / (double) d.total(), GOLD, 10,
						Component.literal(d.earned() + "/" + d.total()).withStyle(ChatFormatting.WHITE)));
				rows.add(Row.text("stack", "▣", "Items", Component.literal(String.format(Locale.US, "%,d / %,d", d.items(), d.itemsTotal()))
						.withStyle(ChatFormatting.WHITE)));
			}
			return new Card(iconOf(e), accent, name, sub, rows);
		}
		boolean hostile = e instanceof Enemy;
		if (!(e instanceof LivingEntity living)) {
			if (!all) return null;
			Item item = e.getPickResult() == null ? Items.AIR : e.getPickResult().getItem();
			return new Card(item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item), -1,
					Component.literal(e.getDisplayName().getString()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
					Component.literal("Entity").withStyle(ChatFormatting.GRAY), rows);
		}
		Component name = Component.literal(e.getDisplayName().getString()).withStyle(hostile ? ChatFormatting.RED : ChatFormatting.GREEN, ChatFormatting.BOLD);
		Component sub = Component.literal(hostile ? "Hostile" : "Passive").withStyle(hostile ? ChatFormatting.DARK_RED : ChatFormatting.DARK_GREEN);
		float hp = living.getHealth(), max = living.getMaxHealth();
		rows.add(new Row("heart", "❤", "", max <= 0 ? 0 : hp / max, RED, 8,
				Component.literal(Horses.fmt(hp / 2.0) + "/" + Horses.fmt(max / 2.0)).withStyle(ChatFormatting.WHITE)));
		if (e instanceof AgeableMob mob) {
			int age = mob.getAge();
			if (age < 0) rows.add(Row.text("baby", "✿", "Baby", Component.literal("· grows up in " + minutes(-age)).withStyle(ChatFormatting.AQUA)));
			else if (e instanceof Animal a && a.isInLove()) rows.add(Row.text("love", "❤", "", Component.literal("In love").withStyle(ChatFormatting.LIGHT_PURPLE)));
			else if (e instanceof Animal && age > 0) rows.add(Row.text("time", "⌛", "Breed again in", Component.literal(minutes(age)).withStyle(ChatFormatting.WHITE)));
		}
		if (e instanceof AbstractHorse horse && InfoMod.HORSE.get(viewer) && !horse.isBaby()) {
			rows.add(Row.text("speed", "»", "Speed", Component.literal(Horses.fmt(Horses.speed(horse)) + " m/s").withStyle(ChatFormatting.WHITE)));
			rows.add(Row.text("jump", "⤒", "Jump", Component.literal(Horses.fmt(Horses.jump(horse)) + " blocks").withStyle(ChatFormatting.WHITE)));
		}
		return new Card(iconOf(e), -1, name, sub, rows);
	}

	/** Old-style bar for the card without the pack. */
	static MutableComponent bar(double fraction, int segments) {
		fraction = Math.max(0, Math.min(1, fraction));
		int full = (int) Math.round(fraction * segments);
		int color = barColor(fraction);
		return Component.literal("▮".repeat(full)).withStyle(s -> s.withColor(color))
				.append(Component.literal("▮".repeat(segments - full)).withStyle(ChatFormatting.DARK_GRAY));
	}

	static int barColor(double fraction) {
		return fraction > 0.66 ? GREEN : fraction > 0.33 ? YELLOW : RED;
	}

	private static String minutes(int ticks) {
		int s = ticks / 20;
		return s >= 60 ? (s / 60) + " min" : s + " s";
	}

	// ---------------------------------------------------------------- blocks

	static @Nullable Card describeBlock(ServerPlayer p, ServerLevel level, BlockPos pos, boolean all) {
		BlockState st = level.getBlockState(pos);
		if (st.isAir()) return null;
		Block block = st.getBlock();
		List<Row> rows = new ArrayList<>();
		String kind = null;

		// Crop growth
		if (block instanceof CropBlock || block instanceof StemBlock || block instanceof SweetBerryBushBlock
				|| block instanceof NetherWartBlock || block instanceof CocoaBlock || block == Blocks.PITCHER_CROP) {
			kind = "Crop";
			IntegerProperty age = intProperty(st, "age");
			if (age != null) {
				int v = st.getValue(age);
				int max = age.getPossibleValues().get(age.getPossibleValues().size() - 1);
				if (v >= max && !(block instanceof StemBlock)) {
					rows.add(new Row("check", "✔", "", -1, GREEN, 0, Component.literal("Ripe – ready to harvest").withStyle(ChatFormatting.GREEN)));
				} else {
					rows.add(Row.bar("sprout", "✿", "Growth", (double) v / max, 8,
							Component.literal(Math.round(100.0 * v / max) + "%").withStyle(ChatFormatting.YELLOW)));
				}
			}
		}
		// Honey
		if (block instanceof BeehiveBlock) {
			kind = "Bees";
			int honey = st.getValue(BeehiveBlock.HONEY_LEVEL);
			rows.add(honey >= BeehiveBlock.MAX_HONEY_LEVELS
					? new Row("check", "✔", "", -1, GOLD, 0, Component.literal("Full of honey").withStyle(ChatFormatting.GOLD))
					: Row.bar("drop", "◆", "Honey", (double) honey / BeehiveBlock.MAX_HONEY_LEVELS, BeehiveBlock.MAX_HONEY_LEVELS,
							Component.literal(honey + "/" + BeehiveBlock.MAX_HONEY_LEVELS).withStyle(ChatFormatting.YELLOW)));
		}
		// Composter
		if (block == Blocks.COMPOSTER) {
			kind = "Farming";
			IntegerProperty lvl = intProperty(st, "level");
			if (lvl != null) {
				int v = st.getValue(lvl);
				rows.add(v >= 8 ? new Row("check", "✔", "", -1, GREEN, 0, Component.literal("Bone meal ready").withStyle(ChatFormatting.GREEN))
						: Row.bar("sprout", "✿", "Compost", v / 7.0, 7, Component.literal(v + "/7").withStyle(ChatFormatting.YELLOW)));
			}
		}
		// Redstone signal
		IntegerProperty power = intProperty(st, "power");
		if (power != null) {
			if (kind == null) kind = "Redstone";
			int v = st.getValue(power);
			rows.add(Row.text("bolt", "⚡", "Power", Component.literal(String.valueOf(v)).withStyle(v > 0 ? ChatFormatting.RED : ChatFormatting.DARK_GRAY)));
		}
		// How full a chest, furnace, hopper… is
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof Container c && c.getContainerSize() > 0) {
			if (kind == null) kind = "Storage";
			int used = 0, items = 0;
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (!s.isEmpty()) {
					used++;
					items += s.getCount();
				}
			}
			int size = c.getContainerSize();
			Row slots = Row.bar("chest", "▣", "Slots", (double) used / size, 8,
					Component.literal(used + "/" + size).withStyle(used >= size ? ChatFormatting.GOLD : ChatFormatting.WHITE));
			// A full chest is good news, not a warning: keep the pips neutral
			rows.add(new Row(slots.icon(), slots.symbol(), slots.label(), slots.fraction(), used >= size ? GOLD : AQUA, 8, slots.value()));
			if (items > 0) rows.add(Row.text("stack", "▣", "Items", Component.literal(String.format(Locale.US, "%,d", items)).withStyle(ChatFormatting.WHITE)));
		}

		if (rows.isEmpty() && !all) return null;
		// Crops: show what you harvest, not the seeds
		Item item = block == Blocks.WHEAT ? Items.WHEAT : block == Blocks.BEETROOTS ? Items.BEETROOT : block.asItem();
		ItemStack icon = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
		int grade = icon.isEmpty() ? 0 : icon.getOrDefault(DataComponents.RARITY, Rarity.COMMON).ordinal();
		int accent = gradeColor(grade);
		// Literal (the server's English) so the card's width is known to the pixel on every client
		Component name = Component.literal(block.getName().getString()).withStyle(s -> s.withBold(true).withColor(accent < 0 ? 0xFFFFFF : accent));
		Component sub;
		if (grade > 0) {
			sub = Component.literal("◆ " + gradeName(grade)).withStyle(s -> s.withColor(accent));
		} else {
			String tool = tool(st);
			sub = Component.literal(kind != null ? kind + (tool.isEmpty() ? "" : " · " + tool) : (tool.isEmpty() ? "Block" : tool))
					.withStyle(ChatFormatting.GRAY);
		}
		return new Card(icon, accent, name, sub, rows);
	}

	/** Grade (vanilla rarity) colour, or -1 for common things. */
	static int gradeColor(int grade) {
		return switch (grade) {
			case 1 -> YELLOW;
			case 2 -> AQUA;
			case 3 -> PINK;
			default -> -1;
		};
	}

	static String gradeName(int grade) {
		return switch (grade) {
			case 1 -> "Uncommon";
			case 2 -> "Rare";
			case 3 -> "Epic";
			default -> "Common";
		};
	}

	/** "Pickaxe · iron or better", "Axe", "" (anything works). */
	static String tool(BlockState st) {
		String kind = st.is(BlockTags.MINEABLE_WITH_PICKAXE) ? "Pickaxe" : st.is(BlockTags.MINEABLE_WITH_AXE) ? "Axe"
				: st.is(BlockTags.MINEABLE_WITH_SHOVEL) ? "Shovel" : st.is(BlockTags.MINEABLE_WITH_HOE) ? "Hoe" : "";
		if (kind.isEmpty() || !st.requiresCorrectToolForDrops()) return kind;
		String tier = st.is(BlockTags.NEEDS_DIAMOND_TOOL) ? "diamond" : st.is(BlockTags.NEEDS_IRON_TOOL) ? "iron"
				: st.is(BlockTags.NEEDS_STONE_TOOL) ? "stone" : "";
		return tier.isEmpty() ? kind : kind + " · " + tier + "+";
	}

	static @Nullable IntegerProperty intProperty(BlockState st, String name) {
		for (Property<?> prop : st.getProperties()) {
			if (prop instanceof IntegerProperty ip && ip.getName().equals(name)) return ip;
		}
		return null;
	}

	static Component join(List<? extends Component> lines) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) out.append("\n");
			out.append(lines.get(i));
		}
		return out;
	}
}
