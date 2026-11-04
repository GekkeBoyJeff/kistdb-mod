package nl.libreria.graves;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Menu;
import nl.libreria.core.gui.SettingsWindow;

/** "My graves": every grave with your items, newest first. Click one to get a beam to it. */
final class GravesGui extends Menu {
	private static final int PER_ROW = 7;

	private final List<GravesMod.Grave> graves;
	private final int perPage;
	private int page;

	private GravesGui(ServerPlayer player, List<GravesMod.Grave> graves) {
		super(player, graves.size() > PER_ROW ? 5 : 4, "Graves · My graves");
		this.graves = graves;
		this.perPage = graves.size() > PER_ROW ? 2 * PER_ROW : PER_ROW;
		rebuild();
	}

	static void open(ServerPlayer player) {
		List<GravesMod.Grave> mine = new ArrayList<>();
		for (GravesMod.Grave g : GravesMod.STORE.get().graves.values()) if (g.owner.equals(player.getUUID())) mine.add(g);
		mine.sort((a, b) -> Long.compare(b.created, a.created));
		new GravesGui(player, mine).open();
	}

	private void rebuild() {
		clearContent();
		for (int i = navRow(); i < navRow() + 9; i++) this.clearSlot(i);

		int pages = Math.max(1, (this.graves.size() + this.perPage - 1) / this.perPage);
		this.page = Math.max(0, Math.min(this.page, pages - 1));
		int from = this.page * this.perPage;
		for (int r = 0; r * PER_ROW < this.perPage; r++) {
			int start = from + r * PER_ROW;
			int n = Math.min(PER_ROW, this.graves.size() - start);
			if (n <= 0) break;
			int[] slots = row(1 + r, n);
			for (int i = 0; i < n; i++) this.setSlot(slots[i], element(this.graves.get(start + i), start + i == 0));
		}
		if (this.graves.isEmpty()) this.setSlot(13, emptyState());

		up();
		pages(this.page, pages, p -> {
			this.page = p;
			rebuild();
		});
		closeButton();
		// The on/off switch right here, so nobody has to look for it
		setting(navRow() + 6, GravesMod.ENABLED, this::rebuild);
		help("When you die, your items go into a",
				"grave there (if graves are on).",
				"Right-click its skull to get",
				"everything back in the same slots.",
				"Only you can open your graves.",
				"/grave beam – the way to the newest");
		finish();
	}

	private GuiElementBuilder element(GravesMod.Grave g, boolean newest) {
		boolean here = this.player.level().dimension().identifier().toString().equals(g.dim);
		String name = here ? "Grave · " + Ui.meters(this.player.position().distanceTo(g.pos())) : "Grave · " + dimension(g.dim);
		GuiElementBuilder b = button(icon(g.dim), name, ChatFormatting.YELLOW);
		if (newest) b.addLoreLine(Ui.text("Your newest grave", ChatFormatting.GOLD));
		b.addLoreLine(Ui.kv("Where: ", (int) Math.floor(g.x) + " " + (int) Math.floor(g.y) + " " + (int) Math.floor(g.z), ChatFormatting.WHITE));
		b.addLoreLine(Ui.kv("World: ", dimension(g.dim), ChatFormatting.WHITE));
		b.addLoreLine(Ui.kv("Items: ", String.valueOf(g.count()), ChatFormatting.GREEN));
		b.addLoreLine(Ui.kv("Died: ", ago(g.created), ChatFormatting.GRAY));
		if (here) {
			actions(b, "Click: show the way");
			b.setCallback((index, type, action, gui) -> {
				this.close();
				GravesMod.beam(this.player, g);
			});
		} else {
			b.addLoreLine(Component.empty());
			b.addLoreLine(Ui.text("Go to the " + dimension(g.dim) + " to be", ChatFormatting.DARK_GRAY));
			b.addLoreLine(Ui.text("shown the way here.", ChatFormatting.DARK_GRAY));
		}
		return b;
	}

	private GuiElementBuilder emptyState() {
		if (GravesMod.ENABLED.get(this.player)) {
			return button(Items.SKELETON_SKULL, "No graves – good!", ChatFormatting.GREEN,
					"Graves are on: if you die, your", "items wait here in a grave", "instead of dropping.");
		}
		GuiElementBuilder b = button(Items.SKELETON_SKULL, "No graves (graves are off)", ChatFormatting.GOLD,
				"Turn graves on and your items go", "into a grave when you die, instead",
				"of dropping on the ground.", "",
				"Switch it on below, or in Settings ›", "Land & places › When you die.");
		actions(b, "Click: open settings");
		return b.setCallback((index, type, action, gui) -> {
			Ui.click(this.player);
			SettingsWindow.open(this.player, GravesMod.MODULE);
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
