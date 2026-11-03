package nl.libreria.info;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
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
import nl.libreria.core.Hud;
import org.jetbrains.annotations.Nullable;

/** Kijk-info: een regel boven de hotbar over het blok of dier waar je naar kijkt (Jade-light, zonder client-mod). */
final class Look {
	static final String CHANNEL = "info.look";
	private static final ChatFormatting SEP_COLOR = ChatFormatting.DARK_GRAY;

	private Look() {
	}

	static void tick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			int mode = InfoMod.LOOK.get(p);
			Component line = mode == 0 || p.isSpectator() ? null : describe(p, mode == 2);
			if (line == null) Hud.hide(p, CHANNEL);
			else Hud.show(p, CHANNEL, line, Hud.PRIO_LOOK, 8);
		}
	}

	/** De regel voor wat de speler nu aankijkt, of null als er niets interessants is. */
	static @Nullable Component describe(ServerPlayer p, boolean all) {
		ServerLevel level = p.level();
		Vec3 eye = p.getEyePosition();
		Vec3 dir = p.getViewVector(1.0f);
		double blockRange = p.blockInteractionRange() + 1.0;
		double entityRange = Math.max(p.entityInteractionRange() + 2.0, 6.0);
		BlockHitResult bh = level.clip(new ClipContext(eye, eye.add(dir.scale(blockRange)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		double blockDistSq = bh.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : bh.getLocation().distanceToSqr(eye);
		Vec3 end = eye.add(dir.scale(entityRange));
		AABB box = p.getBoundingBox().expandTowards(dir.scale(entityRange)).inflate(1.0);
		EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, eye, end, box,
				e -> !e.isSpectator() && e.isPickable() && !e.isInvisible(), Math.min(blockDistSq, entityRange * entityRange));
		if (eh != null) return describeEntity(p, eh.getEntity());
		if (bh.getType() == HitResult.Type.BLOCK) return describeBlock(p, level, bh.getBlockPos(), all);
		return null;
	}

	// ---------------------------------------------------------------- dieren

	static @Nullable Component describeEntity(ServerPlayer p, Entity e) {
		if (!(e instanceof LivingEntity living)) return null;
		List<Component> parts = new ArrayList<>();
		parts.add(e.getDisplayName().copy().withStyle(ChatFormatting.WHITE));
		parts.add(hearts(living.getHealth(), living.getMaxHealth()));
		if (e instanceof AgeableMob mob) {
			int age = mob.getAge();
			if (age < 0) parts.add(Component.literal("baby · grows up in " + minutes(-age)).withStyle(ChatFormatting.AQUA));
			else if (e instanceof Animal a && a.isInLove()) parts.add(Component.literal("❤ in love").withStyle(ChatFormatting.LIGHT_PURPLE));
			else if (e instanceof Animal && age > 0) parts.add(Component.literal("can breed again in " + minutes(age)).withStyle(ChatFormatting.GRAY));
		}
		if (e instanceof AbstractHorse horse && InfoMod.HORSE.get(p) && !horse.isBaby()) {
			parts.add(Component.literal("» " + Horses.fmt(Horses.speed(horse)) + " m/s").withStyle(ChatFormatting.GREEN));
			parts.add(Component.literal("⤒ " + Horses.fmt(Horses.jump(horse)) + " blocks").withStyle(ChatFormatting.GREEN));
		}
		return join(parts);
	}

	static MutableComponent hearts(float health, float max) {
		double f = max <= 0 ? 0 : health / max;
		ChatFormatting c = f > 0.6 ? ChatFormatting.GREEN : f > 0.3 ? ChatFormatting.YELLOW : ChatFormatting.RED;
		return Component.literal("❤ " + Horses.fmt(health / 2.0) + "/" + Horses.fmt(max / 2.0)).withStyle(c);
	}

	private static String minutes(int ticks) {
		int s = ticks / 20;
		return s >= 60 ? (s / 60) + " min" : s + " s";
	}

	// ---------------------------------------------------------------- blokken

	static @Nullable Component describeBlock(ServerPlayer p, ServerLevel level, BlockPos pos, boolean all) {
		BlockState st = level.getBlockState(pos);
		if (st.isAir()) return null;
		Block block = st.getBlock();
		List<Component> details = new ArrayList<>();

		// Groei van gewassen
		if (block instanceof CropBlock || block instanceof StemBlock || block instanceof SweetBerryBushBlock
				|| block instanceof NetherWartBlock || block instanceof CocoaBlock || block == net.minecraft.world.level.block.Blocks.PITCHER_CROP) {
			IntegerProperty age = intProperty(st, "age");
			if (age != null) {
				int v = st.getValue(age);
				int max = age.getPossibleValues().get(age.getPossibleValues().size() - 1);
				if (v >= max && !(block instanceof StemBlock)) details.add(Component.literal("✔ ripe").withStyle(ChatFormatting.GREEN));
				else details.add(Component.literal("growth " + Math.round(100.0 * v / max) + "%").withStyle(ChatFormatting.YELLOW));
			}
		}
		// Honing
		if (block instanceof BeehiveBlock) {
			int honey = st.getValue(BeehiveBlock.HONEY_LEVEL);
			details.add(honey >= BeehiveBlock.MAX_HONEY_LEVELS
					? Component.literal("✔ full of honey").withStyle(ChatFormatting.GOLD)
					: Component.literal("honey " + honey + "/" + BeehiveBlock.MAX_HONEY_LEVELS).withStyle(ChatFormatting.YELLOW));
		}
		// Compostbak
		if (block == net.minecraft.world.level.block.Blocks.COMPOSTER) {
			IntegerProperty lvl = intProperty(st, "level");
			if (lvl != null) {
				int v = st.getValue(lvl);
				details.add(v >= 8 ? Component.literal("✔ bone meal ready").withStyle(ChatFormatting.GREEN)
						: Component.literal("compost " + v + "/7").withStyle(ChatFormatting.YELLOW));
			}
		}
		// Redstone-signaal
		IntegerProperty power = intProperty(st, "power");
		if (power != null) {
			int v = st.getValue(power);
			details.add(Component.literal("⚡ " + v).withStyle(v > 0 ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
		}
		// Hoe vol een kist, oven, trechter… is
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof Container c && c.getContainerSize() > 0) {
			int used = 0;
			for (int i = 0; i < c.getContainerSize(); i++) if (!c.getItem(i).isEmpty()) used++;
			details.add(Component.literal("▣ " + used + "/" + c.getContainerSize() + " slots")
					.withStyle(used >= c.getContainerSize() ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		}
		// Verkeerd gereedschap: alleen als je gereedschap vasthoudt dat dit blok niet laat vallen
		ItemStack hand = p.getMainHandItem();
		if (st.requiresCorrectToolForDrops() && hand.has(DataComponents.TOOL) && !hand.isCorrectToolForDrops(st)) {
			details.add(Component.literal("⚠ needs a better tool").withStyle(ChatFormatting.RED));
		}

		if (details.isEmpty() && !all) return null;
		List<Component> parts = new ArrayList<>();
		parts.add(block.getName().copy().withStyle(ChatFormatting.WHITE));
		parts.addAll(details);
		return join(parts);
	}

	static @Nullable IntegerProperty intProperty(BlockState st, String name) {
		for (Property<?> prop : st.getProperties()) {
			if (prop instanceof IntegerProperty ip && ip.getName().equals(name)) return ip;
		}
		return null;
	}

	static Component join(List<Component> parts) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) out.append(Component.literal("  ·  ").withStyle(SEP_COLOR));
			out.append(parts.get(i));
		}
		return out;
	}
}
