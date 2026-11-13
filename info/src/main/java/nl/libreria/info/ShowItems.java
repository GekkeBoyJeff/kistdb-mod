package nl.libreria.info;

import eu.pb4.polymer.core.api.item.PolymerItem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * The mannequin and the story walls as items of their own ({@code libreria:mannequin}, {@code libreria:story_wall_oak}…),
 * like every other Libreria item: in /give and the recipe book under that name. A player's game sees Minecraft's own
 * armour stand and item frame (nothing to install). What they are is in their custom data, as on the ones made before
 * these existed (an item frame or armour stand with that mark), so both kinds keep working the same.
 */
final class ShowItems {
	private ShowItems() {
	}

	/** An item that looks like a vanilla one to the player's game, with a name of its own. */
	static final class Shown extends Item implements PolymerItem {
		private final Item looks;
		private final Identifier model;
		private final Component name;

		Shown(Properties properties, Item looks, Identifier model, Component name) {
			super(properties);
			this.looks = looks;
			this.model = model;
			this.name = name;
		}

		@Override
		public Item getPolymerItem(ItemStack stack, @Nullable PacketContext context) {
			return this.looks;
		}

		/** What the player's game draws: Minecraft's own armour stand or item frame. */
		@Override
		public @Nullable Identifier getPolymerItemModel(ItemStack stack, @Nullable PacketContext context, HolderLookup.Provider lookup) {
			return this.model;
		}

		/** Its name in the player's game (the item's own would be a translation key nobody has). */
		@Override
		public void modifyBasePolymerItemStack(ItemStack out, ItemStack stack, @Nullable PacketContext context, HolderLookup.Provider lookup) {
			out.set(DataComponents.ITEM_NAME, this.name);
		}

		@Override
		public Component getName(ItemStack stack) {
			return this.name;
		}
	}

	static final Shown MANNEQUIN = register("mannequin", Items.ARMOR_STAND, "armor_stand", 16, mark("mannequin", null), "Mannequin",
			List.of(Ui.text("Dress it in a whole story: armour,", ChatFormatting.GRAY), Ui.text("both hands, its back, hips and feet.", ChatFormatting.GRAY),
					Ui.text("Right-click it: read its stories.", ChatFormatting.GRAY)));

	/** Wood → its story wall. */
	static final Map<String, Shown> WALLS = new LinkedHashMap<>();

	static {
		for (String wood : StoryWall.WOODS) {
			WALLS.put(wood, register("story_wall_" + wood, Items.ITEM_FRAME, "item_frame", 64, mark(StoryWall.KIND, wood),
					StoryWall.woodName(wood) + " Story Wall",
					List.of(Ui.text("A piece for a wall: two spots, one", ChatFormatting.GRAY), Ui.text("above the other. Pieces of one wood", ChatFormatting.GRAY),
							Ui.text("side by side join, up to eight spots.", ChatFormatting.GRAY))));
		}
	}

	/** (Loads the items: called once at start.) */
	static void init() {
	}

	private static CompoundTag mark(String kind, @Nullable String wood) {
		CompoundTag tag = new CompoundTag();
		tag.putString(Showcase.KEY, kind);
		if (wood != null) tag.putString(StoryWall.WOOD, wood);
		return tag;
	}

	private static Shown register(String id, Item looks, String model, int stack, CompoundTag mark, String name, List<Component> lore) {
		Identifier key = Identifier.fromNamespaceAndPath("libreria", id);
		List<Component> lines = lore.stream().map(c -> (Component) c.copy().withStyle(st -> st.withItalic(false))).toList();
		Item.Properties props = new Item.Properties().stacksTo(stack).setId(ResourceKey.create(Registries.ITEM, key))
				.component(DataComponents.CUSTOM_DATA, CustomData.of(mark))
				.component(DataComponents.LORE, new ItemLore(lines));
		return Registry.register(BuiltInRegistries.ITEM, key, new Shown(props, looks, Identifier.withDefaultNamespace(model), Component.literal(name)));
	}
}
