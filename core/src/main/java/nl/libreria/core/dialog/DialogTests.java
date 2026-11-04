package nl.libreria.core.dialog;

import java.util.Optional;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import nl.libreria.core.SelfTests;

/** Self-tests: a window builds, its button calls back with what was filled in, stale clicks are ignored. */
public final class DialogTests {
	private DialogTests() {
	}

	public static void register() {
		SelfTests.register("core.windows", level -> {
			FakePlayer p = FakePlayer.get(level);
			String[] got = {null};
			Window w = Window.of("Test").text("Hello").toggle("a", "A", true)
					.slider("n", Component.literal("N"), 0, 10, 1, 5)
					.field("t", Component.literal("T"), "x", 10)
					.button("Go", null, pl -> got[0] = "plain")
					.button(Component.literal("With values"), null, (pl, v) -> got[0] = v.bool("a", false) + "/" + v.integer("n", 0) + "/" + v.text("t", ""))
					.done();
			SelfTests.check(w.build() != null, "window builds");
			w.show(p);
			CompoundTag tag = new CompoundTag();
			tag.putInt(Window.KEY_WINDOW, w.id);
			tag.putInt(Window.KEY_BUTTON, 1);
			tag.putBoolean("a", true);
			tag.putFloat("n", 7f);
			tag.putString("t", "hi");
			Dialogs.dispatch(p, Dialogs.WINDOW, Optional.of(tag));
			SelfTests.check("true/7/hi".equals(got[0]), "button gets the values: " + got[0]);
			// A click from an older window does nothing
			got[0] = null;
			Window.of("Other").button("X", null, pl -> got[0] = "other").show(p);
			Dialogs.dispatch(p, Dialogs.WINDOW, Optional.of(tag));
			SelfTests.check(got[0] == null, "stale click ignored");
			// Text values for a checkbox work too, junk falls back
			CompoundTag t2 = new CompoundTag();
			t2.putString("b", "true");
			t2.putString("n", "nope");
			Values v = Values.of(t2);
			SelfTests.check(v.bool("b", false) && v.number("n", 3f) == 3f && !v.has("zzz"), "lenient values");
			// Notice windows (no buttons) build as well
			SelfTests.check(Window.of("Notice").text("Only text").build() != null, "notice builds");
		});
	}
}
