package nl.libreria.chests;

import nl.libreria.core.Guide;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nl.libreria.chests.gui.ItemGui;
import nl.libreria.chests.gui.Locate;

/** /chest – voor iedereen (geen op-rechten nodig). Help-regels staan in ChestsMod (MODULE.commands). */
public final class ChestCommands {
	private ChestCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
		dispatcher.register(Commands.literal("chest")
				.executes(c -> {
					ChestSearch.open(c.getSource().getPlayerOrException());
					return 1;
				})
				.then(Commands.literal("find")
						.executes(c -> {
							ChestSearch.open(c.getSource().getPlayerOrException());
							return 1;
						})
						.then(Commands.argument("text", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
								.executes(c -> ChestSearch.find(c.getSource().getPlayerOrException(),
										com.mojang.brigadier.arguments.StringArgumentType.getString(c, "text")))))
				.then(Commands.literal("search")
						.then(Commands.argument("item", ItemArgument.item(context))
								.executes(ChestCommands::find)))
				.then(Commands.literal("where")
						.then(Commands.argument("item", ItemArgument.item(context))
								.executes(ChestCommands::where)))
				.then(Commands.literal("sort")
						.executes(c -> Sorter.sortPlayer(c.getSource().getPlayerOrException()) ? 1 : 0))
				.then(Commands.literal("stop")
						.executes(c -> {
							Guide.clear(c.getSource().getPlayerOrException());
							return 1;
						}))
				.then(Commands.literal("noindex")
						.executes(ChestCommands::noIndex))
				.then(Commands.literal("beam")
						.then(Commands.argument("pos", BlockPosArgument.blockPos())
								.executes(ChestCommands::beamTo))));
	}

	private static int find(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		ItemStack query = ItemArgument.getItem(c, "item").createItemStack(1);
		Locate.go(p, query, Search.find(p, query.getItem()));
		return 1;
	}

	/** The "Where is" menu for one item: every chest that has it. */
	private static int where(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		ItemGui.open(p, ItemArgument.getItem(c, "item").createItemStack(1).getItem());
		return 1;
	}

	private static int noIndex(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		ServerLevel level = p.level();
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(6));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		if (hit.getType() != HitResult.Type.BLOCK || !Containers.isStorage(level.getBlockEntity(hit.getBlockPos()))) {
			p.sendSystemMessage(Component.literal("[Chests] ").withStyle(ChatFormatting.GOLD)
					.append(Component.literal("Look at a chest, barrel or shulker box.").withStyle(ChatFormatting.RED)));
			return 0;
		}
		Tracker.toggleNoIndex(p, level, Containers.keyOf(level, hit.getBlockPos()));
		return 1;
	}

	private static int beamTo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		BlockPos pos = BlockPosArgument.getBlockPos(c, "pos");
		ServerLevel level = p.level();
		Store.Entry e = Store.get(p.getUUID()).find(Store.dimId(level), Containers.keyOf(level, pos));
		if (e == null) {
			p.sendSystemMessage(Component.literal("[Chests] ").withStyle(ChatFormatting.GOLD)
					.append(Component.literal("That chest isn't in your chest index.").withStyle(ChatFormatting.RED)));
			return 0;
		}
		Beam.to(p, level, e.pos(), null, 0);
		return 1;
	}
}
