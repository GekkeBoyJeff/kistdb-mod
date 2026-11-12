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
	static final Identifier DARK_ID = Identifier.fromNamespaceAndPath("libreria", "dark_waystone");
	static final Identifier GRAND_ID = Identifier.fromNamespaceAndPath("libreria", "grand_waystone");
	static final Identifier HEART_ID = Identifier.fromNamespaceAndPath("libreria", "waystone_heart");

	public static final WaystoneBlock WAYSTONE = Registry.register(BuiltInRegistries.BLOCK, WAYSTONE_ID,
			new WaystoneBlock(stone().noOcclusion().setId(ResourceKey.create(Registries.BLOCK, WAYSTONE_ID))));
	public static final AltarBlock ALTAR = Registry.register(BuiltInRegistries.BLOCK, ALTAR_ID,
			new AltarBlock(stone().noOcclusion().setId(ResourceKey.create(Registries.BLOCK, ALTAR_ID))));
	/** A grand waystone's blocks: nobody breaks them (it's taken down from its window), explosions neither; pistons can't move them. */
	public static final GrandBlock GRAND = Registry.register(BuiltInRegistries.BLOCK, GRAND_ID,
			new GrandBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1f, 3_600_000f).sound(SoundType.DEEPSLATE_BRICKS)
					.noLootTable().noOcclusion().isValidSpawn((s, l, p, e) -> false)
					.setId(ResourceKey.create(Registries.BLOCK, GRAND_ID))));
	/** Like obsidian in that pistons can't move it and explosions don't break it; as quick to mine as stone bricks. */
	private static BlockBehaviour.Properties stone() {
		return BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OBSIDIAN).mapColor(MapColor.STONE).strength(2.5f, 1200f)
				.sound(SoundType.STONE).noLootTable();
	}

	public static final Item WAYSTONE_ITEM = Registry.register(BuiltInRegistries.ITEM, WAYSTONE_ID,
			new PolymerBlockItem(WAYSTONE, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, WAYSTONE_ID)), Items.PAPER, true));
	/** The same waystone in dark stone. */
	public static final Item DARK_WAYSTONE_ITEM = Registry.register(BuiltInRegistries.ITEM, DARK_ID,
			new PolymerBlockItem(WAYSTONE, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, DARK_ID)), Items.PAPER, true));
	public static final PearlItem PEARL = Registry.register(BuiltInRegistries.ITEM, PEARL_ID,
			new PearlItem(new Item.Properties().stacksTo(16).setId(ResourceKey.create(Registries.ITEM, PEARL_ID))));

	/** The last piece of a grand waystone (a nether star and two diamonds). */
	public static final HeartItem HEART = Registry.register(BuiltInRegistries.ITEM, HEART_ID,
			new HeartItem(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC)
					.component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
					.component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
							heartLine("Use it on a waystone in the middle", 0xC79BF0), heartLine("of a 5 × 5 sculk floor: it rises", 0xC79BF0),
							heartLine("as a grand waystone.", 0xC79BF0), heartLine("Leaving from one is free.", 0x9AA0AC))))
					.setId(ResourceKey.create(Registries.ITEM, HEART_ID))));

	private static net.minecraft.network.chat.Component heartLine(String s, int rgb) {
		return net.minecraft.network.chat.Component.literal(s).withStyle(st -> st.withColor(rgb).withItalic(false));
	}

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
				"A dye on your waystone – its colour; a glow ink sac: it glows, an ink sac: matte",
				"Or in the crafting grid: a waystone with a dye (and a glow ink sac)",
				"Waystone pearl on a waystone – bind the stack to it", "Right-click a bound pearl – travel there",
				"A grand waystone: your waystone in the middle of a 5 × 5 sculk floor (in the ground), then a Waystone Heart on it",
				"Waystone Heart: a nether star between two diamonds – leaving from a grand waystone is free");
		WaystonePlaces.register();
		VillageWaystones.register();
		nl.libreria.core.Services.onChunksGone((server, dimension, chunks) -> Stones.chunksGone(dimension, chunks));

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
		// A grand waystone doesn't break (not even in creative): it's taken down from its window
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (!state.is(GRAND)) return true;
			if (player instanceof ServerPlayer p) unbreakable(p);
			return false;
		});
		net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
			if (player instanceof ServerPlayer p && level.getBlockState(pos).is(GRAND)) unbreakable(p);
			return net.minecraft.world.InteractionResult.PASS;
		});
		ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> server.execute(() -> recipes(h.player)));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 == 11) WaystonePlaces.tick(server);
			if (server.getTickCount() % 20 == 7) VillageWaystones.tick(server);
		});
		WaystonesTests.register();
	}

	private static final java.util.Map<java.util.UUID, Long> TOLD = new java.util.HashMap<>();

	/** Why a grand waystone doesn't break (said once in a while). */
	static void unbreakable(ServerPlayer p) {
		long now = System.currentTimeMillis();
		Long last = TOLD.get(p.getUUID());
		if (last != null && now - last < 8000) return;
		TOLD.put(p.getUUID(), now);
		nl.libreria.core.Notify.info(p, HEART, "A grand waystone doesn't break", "Its owner takes it down from its window: shift + right-click it.");
	}

	/** The recipes, in everyone's recipe book. */
	static void recipes(ServerPlayer p) {
		List<RecipeHolder<?>> all = new ArrayList<>();
		for (String id : new String[] {"waystone", "dark_waystone", "waystone_pearl", "waystone_heart"}) {
			p.level().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("libreria", id)))
					.ifPresent(all::add);
		}
		if (!all.isEmpty()) p.awardRecipes(all);
	}
}
