package nl.libreria.waystones;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The second design (one colourway: "amethyst" or "ender"): the obelisk with its lit runes and crystal, and the altar
 * with its lit pool, its rune (turned to face away from the stone) and a turning pearl. Each part is a picture from the
 * pack on a display; the lit parts at full light.
 */
final class WaystoneLook {
	private WaystoneLook() {
	}

	static Identifier model(String variant, String part) {
		return Identifier.fromNamespaceAndPath("libreria", "waystone/" + variant + "/" + part);
	}

	private static ItemDisplayElement part(String variant, String name, boolean lit, float range) {
		ItemDisplayElement e = new ItemDisplayElement(WaystoneBlock.model(model(variant, name)));
		e.setItemDisplayContext(ItemDisplayContext.NONE);
		if (lit) e.setBrightness(new Brightness(15, 15));
		e.setViewRange(range);
		return e;
	}

	/** The obelisk (at the centre of its lower block), with a name above it. */
	static TextDisplayElement obelisk(ElementHolder h, String variant, int labelRgb) {
		h.addElement(part(variant, "obelisk", false, 1.5f));
		h.addElement(part(variant, "obelisk_glow", true, 1.5f));
		TextDisplayElement label = new TextDisplayElement(Component.empty());
		label.setBillboardMode(Display.BillboardConstraints.CENTER);
		label.setShadow(true);
		label.setBackground(0);
		label.setBrightness(new Brightness(15, 15));
		label.setViewRange(0.25f);
		label.setScale(new Vector3f(0.7f));
		label.setTranslation(new Vector3f(0, 1.62f, 0));
		h.addElement(label);
		return label;
	}

	/** The altar (at the centre of its block), its rune towards {@code front}; returns the pearl, to turn it. */
	static ItemDisplayElement altar(ElementHolder h, String variant, Direction front) {
		Quaternionf turn = new Quaternionf().rotateY((float) Math.toRadians(180 - front.toYRot()));
		for (String name : new String[] {"altar", "altar_glow"}) {
			ItemDisplayElement e = part(variant, name, name.endsWith("glow"), 1f);
			e.setLeftRotation(turn);
			h.addElement(e);
		}
		ItemDisplayElement pearl = part(variant, "pearl", true, 0.6f);
		pearl.setTranslation(new Vector3f(0, PEARL_Y, 0));
		h.addElement(pearl);
		return pearl;
	}

	static final float PEARL_Y = 0.4375f;

	/** Turns the pearl a little further (call every 10 ticks; {@code step} counts up). */
	static void turn(ItemDisplayElement pearl, int step) {
		pearl.setLeftRotation(new Quaternionf().rotateY((float) Math.toRadians(step * 36 % 360)));
		pearl.setTranslation(new Vector3f(0, PEARL_Y + (float) Math.sin(step * 0.5) * 0.02f, 0));
		pearl.setInterpolationDuration(10);
		pearl.startInterpolation();
	}
}
