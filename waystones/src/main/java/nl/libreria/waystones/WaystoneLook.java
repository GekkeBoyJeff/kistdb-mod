package nl.libreria.waystones;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * How a waystone looks (the third design): three blocks high, light or dark stone. Its body, an open frame with a
 * rune crystal floating and turning inside, a slimmer top with a capstone; the altar in front. Everything that shines
 * takes the colour of the dye it was given, and glows (full light) or not (the world's light).
 */
final class WaystoneLook {
	/** Where the floating rune turns: 1.25 blocks above the middle of the lower block (in the open frame). */
	static final float RUNE_Y = 1.25f;
	static final float RUNE_SCALE = 0.85f;
	static final float PEARL_Y = 0.4375f;

	private WaystoneLook() {
	}

	/** The colours of the dyes, a little brighter (they shine). */
	static int shine(DyeColor dye) {
		return switch (dye) {
			case WHITE -> 0xF4F6FF;
			case ORANGE -> 0xFF9A3C;
			case MAGENTA -> 0xF07CE6;
			case LIGHT_BLUE -> 0x7FD8FF;
			case YELLOW -> 0xFFE45C;
			case LIME -> 0xA6F04A;
			case PINK -> 0xFFA6C8;
			case GRAY -> 0x8C959C;
			case LIGHT_GRAY -> 0xC8CCD0;
			case CYAN -> 0x5FF2E0;
			case PURPLE -> 0xC79BF0;
			case BLUE -> 0x6C82FF;
			case BROWN -> 0xC08A5A;
			case GREEN -> 0x7FC24A;
			case RED -> 0xFF5A50;
			case BLACK -> 0x4A4A5A;
		};
	}

	static Identifier model(String path) {
		return Identifier.fromNamespaceAndPath("libreria", "waystone/" + path);
	}

	private static ItemStack stack(String path, int rgb) {
		ItemStack s = new ItemStack(Items.PAPER);
		s.set(DataComponents.ITEM_MODEL, model(path));
		if (rgb >= 0) s.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		return s;
	}

	private static ItemDisplayElement part(String path, int rgb, boolean lit, float range) {
		ItemDisplayElement e = new ItemDisplayElement(stack(path, rgb));
		e.setItemDisplayContext(ItemDisplayContext.NONE);
		if (lit) e.setBrightness(new Brightness(15, 15));
		e.setViewRange(range);
		return e;
	}

	/** The parts that change: the name above it and the floating rune. */
	record Obelisk(TextDisplayElement label, ItemDisplayElement rune) {
	}

	/** The obelisk, its holder at the middle of its lower block. */
	static Obelisk obelisk(ElementHolder h, boolean dark, int rgb, boolean glow) {
		String stone = dark ? "dark/" : "light/";
		h.addElement(part(stone + "low", -1, false, 1.5f));
		h.addElement(part("shine/low", rgb, glow, 1.5f));
		ItemDisplayElement top = part(stone + "top", -1, false, 1.5f);
		top.setTranslation(new Vector3f(0, 1, 0));
		h.addElement(top);
		ItemDisplayElement cap = part("shine/top", rgb, glow, 1.5f);
		cap.setTranslation(new Vector3f(0, 1, 0));
		h.addElement(cap);
		ItemDisplayElement rune = part("shine/rune", rgb, glow, 1.5f);
		rune.setTranslation(new Vector3f(0, RUNE_Y, 0));
		rune.setScale(new Vector3f(RUNE_SCALE));
		h.addElement(rune);
		TextDisplayElement label = new TextDisplayElement(Component.empty());
		label.setBillboardMode(Display.BillboardConstraints.CENTER);
		label.setShadow(true);
		label.setBackground(0);
		label.setBrightness(new Brightness(15, 15));
		label.setViewRange(0.25f);
		label.setScale(new Vector3f(0.7f));
		label.setTranslation(new Vector3f(0, 2.72f, 0));
		h.addElement(label);
		return new Obelisk(label, rune);
	}

	/** The altar, its rune towards {@code front}; returns the pearl (to turn it). */
	static ItemDisplayElement altar(ElementHolder h, boolean dark, int rgb, boolean glow, Direction front) {
		Quaternionf turn = new Quaternionf().rotateY((float) Math.toRadians(180 - front.toYRot()));
		ItemDisplayElement body = part((dark ? "dark/" : "light/") + "altar", -1, false, 1f);
		body.setLeftRotation(turn);
		h.addElement(body);
		ItemDisplayElement shine = part("shine/altar", rgb, glow, 1f);
		shine.setLeftRotation(turn);
		h.addElement(shine);
		ItemDisplayElement pearl = part("shine/pearl", rgb, glow, 0.6f);
		pearl.setTranslation(new Vector3f(0, PEARL_Y, 0));
		h.addElement(pearl);
		return pearl;
	}

	/** The floating rune and the pearl turn and bob a little (call every 10 ticks; {@code step} counts up). */
	static void turn(ItemDisplayElement e, float y, int step, float scale) {
		e.setLeftRotation(new Quaternionf().rotateY((float) Math.toRadians(step * 24 % 360)));
		e.setTranslation(new Vector3f(0, y + (float) Math.sin(step * 0.4) * 0.03f, 0));
		e.setScale(new Vector3f(scale));
		e.setInterpolationDuration(10);
		e.startInterpolation();
	}
}
