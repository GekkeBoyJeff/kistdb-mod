package nl.libreria.info;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
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
 * Look info: a small floating tooltip above the block, animal or player you are looking at (like Jade, but without a
 * client mod). Only for things within your reach, and hidden while a container menu is open. Only you see your own
 * tooltip.
 */
final class Look {
	/** What the player looks at: where the tooltip floats and what it says. */
	record Target(Vec3 anchor, List<Component> lines) {
	}

	private static final class Tip {
		final ElementHolder holder = new ElementHolder();
		final TextDisplayElement text = new TextDisplayElement();
		ServerLevel level;
		Vec3 anchor = Vec3.ZERO;
		@Nullable Component shown;
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

	private static void show(ServerPlayer p, @Nullable Target t) {
		Tip tip = TIPS.get(p.getUUID());
		if (t == null) {
			if (tip != null && tip.shown != null) {
				tip.text.setText(Component.empty());
				tip.text.setBackground(0);
				tip.shown = null;
				tip.holder.tick();
			}
			return;
		}
		if (tip == null || tip.level != p.level() || tip.holder.getAttachment() == null) {
			if (tip != null) tip.holder.destroy();
			tip = new Tip();
			Tip fresh = tip;
			fresh.level = p.level();
			fresh.anchor = t.anchor();
			fresh.text.setBillboardMode(Display.BillboardConstraints.CENTER);
			fresh.text.setScale(new Vector3f(0.55f));
			fresh.text.setLineWidth(220);
			fresh.text.setSeeThrough(true);
			fresh.text.setTeleportDuration(2);
			fresh.holder.addElement(fresh.text);
			new ManualAttachment(fresh.holder, fresh.level, () -> fresh.anchor);
			fresh.holder.startWatching(p);
			TIPS.put(p.getUUID(), fresh);
		}
		tip.anchor = t.anchor();
		Component text = join(t.lines());
		if (!Objects.equals(text, tip.shown)) {
			tip.text.setText(text);
			// Vanilla tooltip colours: very dark purple, mostly opaque
			tip.text.setBackground(0xD0100010);
			tip.shown = text;
		}
		tip.holder.tick();
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
			double up = e instanceof Player ? 1.05 : 0.45;
			return new Target(e.position().add(0, e.getBbHeight() + up, 0), lines);
		}
		if (bh.getType() != HitResult.Type.BLOCK) return null;
		BlockPos pos = bh.getBlockPos();
		List<Component> lines = describeBlock(p, level, pos, all);
		if (lines == null) return null;
		return new Target(anchorFor(level, pos, bh, eye), lines);
	}

	/** Above the block if there is room, otherwise just in front of the face you're looking at. */
	private static Vec3 anchorFor(ServerLevel level, BlockPos pos, BlockHitResult hit, Vec3 eye) {
		BlockState st = level.getBlockState(pos);
		double top = st.getShape(level, pos).isEmpty() ? 0.5 : st.getShape(level, pos).max(net.minecraft.core.Direction.Axis.Y);
		BlockPos above = pos.above();
		if (!level.getBlockState(above).isCollisionShapeFullBlock(level, above)) {
			return new Vec3(pos.getX() + 0.5, pos.getY() + top + 0.3, pos.getZ() + 0.5);
		}
		Vec3 towardsEye = eye.subtract(hit.getLocation()).normalize().scale(0.6);
		return hit.getLocation().add(towardsEye).add(0, 0.25, 0);
	}

	// ---------------------------------------------------------------- players and animals

	static @Nullable List<Component> describeEntity(ServerPlayer viewer, Entity e, boolean all) {
		List<Component> lines = new ArrayList<>();
		if (e instanceof ServerPlayer other) {
			// Players: who they are and how far they got (no health)
			lines.add(other.getDisplayName().copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
			Services.progress().ifPresent(pr -> {
				Component title = pr.title(other);
				if (title != null) lines.add(title);
				lines.addAll(pr.card(other));
			});
			return lines;
		}
		if (!(e instanceof LivingEntity living)) {
			if (!all) return null;
			lines.add(e.getDisplayName().copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
			return lines;
		}
		lines.add(e.getDisplayName().copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
		lines.add(hearts(living.getHealth(), living.getMaxHealth()));
		if (e instanceof AgeableMob mob) {
			int age = mob.getAge();
			if (age < 0) lines.add(Component.literal("Baby · grows up in " + minutes(-age)).withStyle(ChatFormatting.AQUA));
			else if (e instanceof Animal a && a.isInLove()) lines.add(Component.literal("❤ In love").withStyle(ChatFormatting.LIGHT_PURPLE));
			else if (e instanceof Animal && age > 0) lines.add(Component.literal("Can breed again in " + minutes(age)).withStyle(ChatFormatting.GRAY));
		}
		if (e instanceof AbstractHorse horse && InfoMod.HORSE.get(viewer) && !horse.isBaby()) {
			lines.add(Component.literal("» " + Horses.fmt(Horses.speed(horse)) + " m/s  ⤒ " + Horses.fmt(Horses.jump(horse)) + " blocks")
					.withStyle(ChatFormatting.GREEN));
		}
		return lines;
	}

	static MutableComponent hearts(float health, float max) {
		double f = max <= 0 ? 0 : health / max;
		ChatFormatting c = f > 0.6 ? ChatFormatting.GREEN : f > 0.3 ? ChatFormatting.YELLOW : ChatFormatting.RED;
		return Component.literal("❤ " + Horses.fmt(health / 2.0) + " / " + Horses.fmt(max / 2.0)).withStyle(c);
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
				if (v >= max && !(block instanceof StemBlock)) details.add(Component.literal("✔ Ripe").withStyle(ChatFormatting.GREEN));
				else details.add(Component.literal("Growth " + Math.round(100.0 * v / max) + "%").withStyle(ChatFormatting.YELLOW));
			}
		}
		// Honey
		if (block instanceof BeehiveBlock) {
			int honey = st.getValue(BeehiveBlock.HONEY_LEVEL);
			details.add(honey >= BeehiveBlock.MAX_HONEY_LEVELS
					? Component.literal("✔ Full of honey").withStyle(ChatFormatting.GOLD)
					: Component.literal("Honey " + honey + "/" + BeehiveBlock.MAX_HONEY_LEVELS).withStyle(ChatFormatting.YELLOW));
		}
		// Composter
		if (block == net.minecraft.world.level.block.Blocks.COMPOSTER) {
			IntegerProperty lvl = intProperty(st, "level");
			if (lvl != null) {
				int v = st.getValue(lvl);
				details.add(v >= 8 ? Component.literal("✔ Bone meal ready").withStyle(ChatFormatting.GREEN)
						: Component.literal("Compost " + v + "/7").withStyle(ChatFormatting.YELLOW));
			}
		}
		// Redstone signal
		IntegerProperty power = intProperty(st, "power");
		if (power != null) {
			int v = st.getValue(power);
			details.add(Component.literal("⚡ Power " + v).withStyle(v > 0 ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
		}
		// How full a chest, furnace, hopper… is
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof Container c && c.getContainerSize() > 0) {
			int used = 0;
			for (int i = 0; i < c.getContainerSize(); i++) if (!c.getItem(i).isEmpty()) used++;
			details.add(Component.literal("▣ " + used + "/" + c.getContainerSize() + " slots used")
					.withStyle(used >= c.getContainerSize() ? ChatFormatting.GOLD : ChatFormatting.GRAY));
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
