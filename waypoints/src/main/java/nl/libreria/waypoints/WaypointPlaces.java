package nl.libreria.waypoints;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.PlaceList;
import nl.libreria.core.Ui;
import nl.libreria.waypoints.WaypointsMod.Point;

/** Your death point in "Your places" ({@link PlaceList}), under "Death &amp; graves": click = show the way back. */
final class WaypointPlaces {
	static final String DEATH = "death";

	private WaypointPlaces() {
	}

	static void register() {
		PlaceList.kind(DEATH, "Death & graves", Items.SKELETON_SKULL, 30)
				.about("Where you last died, and your graves.")
				.empty("Nothing here: you haven't died", "since Libreria was added.");
		PlaceList.addSource((p, out) -> {
			Point death = WaypointsMod.deathPoint(p);
			if (death != null) {
				out.add(PlaceList.place(DEATH, "Death point", new ItemStack(Items.SKELETON_SKULL), death.dim, Vec3.atBottomCenterOf(death.pos()))
						.lore(Ui.kv("At ", death.x + " " + death.y + " " + death.z, ChatFormatting.WHITE),
								Ui.text("Where you last died. Only you see it.", ChatFormatting.GRAY))
						.go(pl -> WaypointsMod.beam(pl, death)));
			}
		});
	}
}
