package nl.libreria.graves;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.PlaceList;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Menu;

/**
 * Your graves in "Your places" ({@link PlaceList}), under "Death &amp; graves" next to your death point: newest first in
 * the lore, click = show the way. The on/off switch for graves sits in the bottom row of that tab.
 */
final class GravePlaces {
	static final String KIND = "death";

	private GravePlaces() {
	}

	static void register() {
		PlaceList.kind(KIND, "Death & graves", Items.SKELETON_SKULL, 30)
				.about("Where you last died, and your graves.")
				.help("When you die, your items go into a", "grave there (if graves are on).", "Right-click its skull to get",
						"everything back in the same slots.", "Only you can open your graves.")
				.footer(GravePlaces::switchButton);
		PlaceList.addSource((p, out) -> {
			GravesMod.Grave newest = GravesMod.newestOf(p);
			for (GravesMod.Grave g : GravesMod.STORE.get().graves.values()) {
				if (!g.owner.equals(p.getUUID())) continue;
				out.add(PlaceList.place(KIND, g == newest ? "Your newest grave" : "Grave", new ItemStack(icon(g.dim)), g.dim, g.pos())
						.lore(Ui.kv("At ", (int) Math.floor(g.x) + " " + (int) Math.floor(g.y) + " " + (int) Math.floor(g.z), ChatFormatting.WHITE),
								Ui.kv("Items: ", String.valueOf(g.count()), ChatFormatting.GREEN),
								Ui.kv("Died: ", ago(g.created), ChatFormatting.GRAY))
						.go(pl -> GravesMod.beam(pl, g)));
			}
		});
	}

	/** Your graves (the Death &amp; graves tab). */
	static void open(ServerPlayer p) {
		PlaceList.open(p, KIND);
	}

	/** Graves on or off, right in the list, so nobody has to look for it. */
	private static GuiElementBuilder switchButton(ServerPlayer p) {
		boolean on = GravesMod.ENABLED.get(p);
		GuiElementBuilder b = Menu.button(Items.SKELETON_SKULL, on ? "Graves: on" : "Graves: off", on ? ChatFormatting.GREEN : ChatFormatting.GOLD,
				on ? "When you die, your items wait in" : "Turn it on and your items go into",
				on ? "a grave instead of dropping." : "a grave when you die, instead of", on ? "" : "dropping on the ground.");
		if (on) b.glow();
		Menu.actions(b, "Click: turn " + (on ? "off" : "on"));
		return b.setCallback((i, t, a, g) -> {
			GravesMod.ENABLED.set(p, !on);
			Ui.sound(p, SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, on ? 0.7f : 1.4f);
			PlaceList.open(p, KIND);
		});
	}

	private static Item icon(String dim) {
		if (dim.endsWith("the_nether")) return Items.WITHER_SKELETON_SKULL;
		if (dim.endsWith("the_end")) return Items.DRAGON_HEAD;
		return Items.SKELETON_SKULL;
	}

	/** "minecraft:the_nether" → "Nether". */
	static String dimension(String dim) {
		return switch (dim) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> {
				Identifier id = Identifier.tryParse(dim);
				String path = (id == null ? dim : id.getPath()).replace('_', ' ');
				yield path.isEmpty() ? dim : path.substring(0, 1).toUpperCase(Locale.ROOT) + path.substring(1);
			}
		};
	}

	/** "just now", "5 min ago", "2 h ago", "3 days ago". */
	static String ago(long millis) {
		if (millis <= 0) return "unknown";
		long s = Math.max(0, (System.currentTimeMillis() - millis) / 1000);
		if (s < 60) return "just now";
		if (s < 3600) return (s / 60) + " min ago";
		if (s < 86400) return (s / 3600) + " h ago";
		long d = s / 86400;
		return d + (d == 1 ? " day ago" : " days ago");
	}

}
