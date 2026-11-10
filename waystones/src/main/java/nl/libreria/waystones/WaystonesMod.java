package nl.libreria.waystones;

import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import nl.libreria.core.Domain;
import nl.libreria.core.Module;
import nl.libreria.core.Pack;
import nl.libreria.core.ServerSetting;
import nl.libreria.core.Tips;

/**
 * WaystonesMod – a Libreria module: waystones, a rune obelisk with a small altar in front. New villages get one by
 * their bell; you craft your own. Right-click one to find it, then travel from any waystone to every one you found:
 * a little XP by distance (at most 1.5 levels), or free with an ender pearl. Waystone pearls are bound to one
 * waystone (a whole stack at once) and take you there from anywhere. A player's waystone is open to everyone unless
 * its owner makes it private; a village's is found per player, or (a server setting) known to everyone at once.
 * The models and textures come with the server's resource pack.
 */
public final class WaystonesMod implements ModInitializer {
	// ---------------------------------------------------------------- blocks and items

	static final Identifier WAYSTONE_ID = Identifier.fromNamespaceAndPath("libreria", "waystone");
	static final Identifier ALTAR_ID = Identifier.fromNamespaceAndPath("libreria", "waystone_altar");
	static final Identifier PEARL_ID = Identifier.fromNamespaceAndPath("libreria", "waystone_pearl");

	public static final WaystoneBlock WAYSTONE = Registry.register(BuiltInRegistries.BLOCK, WAYSTONE_ID,
			new WaystoneBlock(stone().noOcclusion().setId(ResourceKey.create(Registries.BLOCK, WAYSTONE_ID))));
	public static final AltarBlock ALTAR = Registry.register(BuiltInRegistries.BLOCK, ALTAR_ID,
			new AltarBlock(stone().noOcclusion().setId(ResourceKey.create(Registries.BLOCK, ALTAR_ID))));
	/** Like obsidian in that pistons can't move it and explosions don't break it; as quick to mine as stone bricks. */
	private static BlockBehaviour.Properties stone() {
		return BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OBSIDIAN).mapColor(MapColor.STONE).strength(2.5f, 1200f)
				.sound(SoundType.STONE).noLootTable();
	}

	public static final Item WAYSTONE_ITEM = Registry.register(BuiltInRegistries.ITEM, WAYSTONE_ID,
			new PolymerBlockItem(WAYSTONE, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, WAYSTONE_ID)), Items.PAPER, true));
	public static final PearlItem PEARL = Registry.register(BuiltInRegistries.ITEM, PEARL_ID,
			new PearlItem(new Item.Properties().stacksTo(16).setId(ResourceKey.create(Registries.ITEM, PEARL_ID))));

	public static final Module MODULE = Module.register("waystones", "Waystones", WAYSTONE_ITEM,
			"Waystones in villages and your own:", "travel between the ones you found.").in(Domain.PLACES, "Travel by waystone");

	// ---------------------------------------------------------------- house rules (admin window)

	static final ServerSetting.Toggle VILLAGES = MODULE.serverToggle("villages", "Waystones in new villages", Items.BELL, true,
			"New villages get a waystone by", "their bell. Villages that are", "already there stay as they are.");
	static final ServerSetting.Choice KNOWN = MODULE.serverChoice("known", "Village waystones", Items.BELL, 0,
			new String[] {"Each finds them", "Everyone knows them"}, "Each finds them: a player right-", "clicks one once before going there.",
			"Everyone knows them: every village's", "waystone is on everyone's list.");
	static final ServerSetting.Number BLOCKS_PER_LEVEL = MODULE.serverNumber("blocks_per_level", "One XP level per", Items.EXPERIENCE_BOTTLE,
			1000, 0, 5000, 100, "blocks", "Travelling costs a level per so", "many blocks, never more than 1.5.",
			"An ender pearl: free. 0: always free.");
	static final ServerSetting.Toggle WORLDS = MODULE.serverToggle("worlds", "Travel between worlds", Items.OBSIDIAN, true,
			"Waystones may take you to one in", "another world (the Nether, the End).");
	static final ServerSetting.Toggle PEARLS = MODULE.serverToggle("pearls", "Waystone pearls", Items.ENDER_PEARL, true,
			"Pearls bound to a waystone take", "you there from anywhere.");

	static final Tips.Tip TIP = Tips.register("waystone", Domain.PLACES, WAYSTONE_ITEM, "Waystones",
			"Travel from here to every waystone you found.", "With an ender pearl it's free: right-click.");

	@Override
	public void onInitialize() {
		Pack.addAssets("libreria_waystones");
		MODULE.hubEntry(Domain.PLACES, 14, WAYSTONE_ITEM, "Waystones", p -> nl.libreria.core.PlaceList.open(p, WaystonePlaces.KIND),
				"The waystones you found. At a", "waystone: travel to any of them.");
		MODULE.adminButton("All waystones", "Every waystone: where, whose, rename, private, remove", WaystonesWindows::admin);
		MODULE.commands("Right-click a waystone – find it, travel from it",
				"Shift + right-click your waystone – its name, private or open",
				"Waystone pearl on a waystone – bind the stack to it", "Right-click a bound pearl – travel there");
		WaystonePlaces.register();
		VillageWaystones.register();

		// Breaking: a village's waystone only an operator, a player's its owner and the people they trust
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (!(player instanceof ServerPlayer p) || !(level instanceof net.minecraft.server.level.ServerLevel sl)) return true;
			net.minecraft.core.BlockPos lower = WaystoneBlock.lowerOf(sl, pos, state);
			if (lower == null) return true;
			Stones.Stone s = Stones.at(sl, lower);
			if (Stones.mayBreak(p, s)) return true;
			WaystoneBlock.refuse(p, s);
			return false;
		});
		ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> server.execute(() -> recipes(h.player)));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 == 11) WaystonePlaces.tick(server);
		});
		WaystonesTests.register();
	}

	/** The two recipes, in everyone's recipe book. */
	static void recipes(ServerPlayer p) {
		List<RecipeHolder<?>> all = new ArrayList<>();
		for (String id : new String[] {"waystone", "waystone_pearl"}) {
			p.level().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("libreria", id)))
					.ifPresent(all::add);
		}
		if (!all.isEmpty()) p.awardRecipes(all);
	}
}
