package nl.libreria.refresh;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import nl.libreria.core.Notify;
import nl.libreria.core.ServerSetting;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Window;
import nl.libreria.core.gui.AdminCanvas;
import nl.libreria.core.gui.AdminWindow;
import nl.libreria.core.gui.Canvas;
import nl.libreria.core.gui.CanvasWindow;
import nl.libreria.core.gui.Glyphs;

/**
 * Server settings › World refresh: how long people must have been near a chunk for it to stay, the room around claims,
 * whether structures move; then per world (click: its map; the switch: whether it takes part) how many chunks go; and
 * at the bottom, plan the refresh for the next restart (or cancel it).
 */
final class RefreshCanvas extends CanvasWindow {
	private static final Identifier SWITCH_ON = Identifier.fromNamespaceAndPath("libreria", "gui/switch_on");
	private static final Identifier SWITCH_OFF = Identifier.fromNamespaceAndPath("libreria", "gui/switch_off");
	private static final int TEXT = 0xFFFFFF, VALUE = 0x8FE3F0, MUTED = Glyphs.muted, NEUTRAL = Glyphs.neutral, GREEN = 0x6EC85A, DANGER = 0xE0605A;
	private static final int ROOM_FULL = 18 * 9 - 2 * Canvas.CARD_MARGIN - 12;

	private RefreshCanvas(ServerPlayer p) {
		super(p);
	}

	static void open(ServerPlayer p) {
		if (!Ui.isOp(p)) return;
		if (!CanvasWindow.available(p)) {
			dialog(p);
			return;
		}
		new RefreshCanvas(p).show();
	}

	private static int left(int col) {
		return Canvas.cellX(col) + Canvas.CARD_MARGIN + 6;
	}

	private static int right(int col) {
		return Canvas.cellX(col) - Canvas.CARD_MARGIN - 6;
	}

	/** One world: how many chunks of it go (or why we can't say yet). */
	static String worldLine(ServerPlayer p, ResourceKey<Level> dim) {
		Scan.World w = Scan.world(dim);
		if (w == null) return "not here";
		Plan.Ctx ctx = new Plan.Ctx(p.level().getServer(), dim);
		if (!ctx.on) return "takes no part";
		if (!Restart.ready(w)) return "reading… " + Math.round(w.progress() * 100) + "%";
		return Ui.n(Plan.count(ctx, w).go()) + " go";
	}

	/** Can the refresh be planned: some world takes part, and those are read wholly? */
	static boolean canPlan() {
		boolean any = false;
		for (Scan.World w : Scan.WORLDS.values()) {
			if (!Plan.dim(w.dim.identifier().toString()).on) continue;
			if (!Restart.ready(w)) return false;
			any = true;
		}
		return any;
	}

	@Override
	protected Canvas draw() {
		Canvas c = new Canvas().panel();
		c.text("Server settings", 8, 5, AdminCanvas.TINT);
		int x = 8 + Glyphs.width(Component.literal("Server settings"));
		c.text(" › ", x, 5, MUTED);
		x += Glyphs.width(Component.literal(" › "));
		c.text("World refresh", x, 5, TEXT);
		value(c, 0, "Stays if people were near", "for at least " + RefreshMod.NEAR.get() + " minute" + (RefreshMod.NEAR.get() == 1 ? "" : "s"));
		value(c, 2, "Room around claims", RefreshMod.ROOM.get() + " chunk" + (RefreshMod.ROOM.get() == 1 ? "" : "s"));
		boolean move = RefreshMod.MOVE.get();
		c.card(0, 4, 8, 2, move ? GREEN : NEUTRAL);
		int y1 = Canvas.cellY(4) + 2 + (18 * 2 - 4 - 18) / 2;
		c.text("Structures move", left(0), y1, TEXT);
		c.text(move ? "All but strongholds" : "They stay where they were", left(0), y1 + 10, VALUE);
		for (int i = 0; i < 3; i++) {
			ResourceKey<Level> dim = MapScreen.WORLD_KEYS[i];
			boolean on = Plan.dim(dim.identifier().toString()).on && Scan.world(dim) != null;
			int row = 6 + i;
			c.card(0, row, 8, 1, on ? AdminCanvas.TINT : NEUTRAL);
			c.text(MapScreen.WORLD_NAMES[i], left(0), Canvas.cellY(row) + 5, TEXT);
			c.text("›", right(8), Canvas.cellY(row) + 5, MUTED);
			c.right(worldLine(this.player, dim), right(8) - 8, Canvas.cellY(row) + 5, on ? VALUE : MUTED);
		}
		c.card(0, 9, 3, 1, NEUTRAL);
		c.centred("Back", Canvas.cellX(0) + 27, Canvas.cellY(9) + 5, TEXT);
		boolean planned = Plan.STORE.get().planned;
		c.card(3, 9, 6, 1, planned ? DANGER : canPlan() ? GREEN : NEUTRAL);
		c.centred(planned ? "Planned · cancel" : "Plan the refresh", Canvas.cellX(3) + 54, Canvas.cellY(9) + 5, canPlan() || planned ? TEXT : MUTED);
		return c;
	}

	private static void value(Canvas c, int row, String label, String value) {
		c.card(0, row, 9, 2, NEUTRAL);
		int y1 = Canvas.cellY(row) + 2 + (18 * 2 - 4 - 18) / 2;
		c.text(label, left(0), y1, TEXT);
		int hw = Glyphs.width(Component.literal("‹›"));
		c.text("‹›", right(9) + 1 - hw, y1 + 10, MUTED);
		c.text(Canvas.fit(value, ROOM_FULL - hw - 8), left(0), y1 + 10, VALUE);
	}

	private static Component bold(String s) {
		return Ui.text(s, ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
	}

	private static Component grey(String s) {
		return Ui.text(s, ChatFormatting.GRAY);
	}

	private static Component hint(String s) {
		return Ui.text("▸ " + s, ChatFormatting.YELLOW);
	}

	private void number(int row, ServerSetting.Number s, List<Component> about) {
		List<Component> lore = new ArrayList<>();
		lore.add(Ui.text("Now: " + s.display(), ChatFormatting.AQUA));
		lore.addAll(about);
		lore.add(Component.empty());
		lore.add(hint("Click: more · right-click: less"));
		lore.add(hint("Shift: ten steps at once"));
		areaTyped(0, row, 9, 2, bold(s.label), lore, (p, type) -> {
			if (!Ui.isOp(p) || !(type.isLeft || type.isRight)) return;
			Ui.click(p);
			int steps = type.shift ? 10 : 1;
			s.set(s.get() + (type.isRight ? -steps : steps) * s.step);
			RefreshMod.LOG.info("{} set {} to {}", p.getName().getString(), s.label, s.display());
			refresh();
		});
	}

	@Override
	protected void fill() {
		number(0, RefreshMod.NEAR, List.of(grey("Minecraft counts per chunk how long"), grey("someone was near (within the"), grey("simulation distance). Where people"),
				grey("spent this long, the land stays.")));
		number(2, RefreshMod.ROOM, List.of(grey("Around every claim, this many chunks"), grey("stay too, so a base keeps its garden.")));
		Component moveName = bold("Structures move");
		List<Component> moveLore = List.of(Ui.text("Now: " + (RefreshMod.MOVE.get() ? "on" : "off"), ChatFormatting.AQUA),
				grey("What's made anew gets its villages,"), grey("temples and cities in new places."), grey("Strongholds stay where they are."),
				Component.empty(), hint("Click: turn " + (RefreshMod.MOVE.get() ? "off" : "on")));
		Consumer<ServerPlayer> flip = p -> {
			if (!Ui.isOp(p)) return;
			RefreshMod.MOVE.set(!RefreshMod.MOVE.get());
			refresh();
		};
		area(0, 4, 8, 2, moveName, moveLore, flip);
		area(8, 5, 1, 1, moveName, moveLore, flip);
		ItemStack sw = new ItemStack(Items.PAPER);
		sw.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, RefreshMod.MOVE.get() ? SWITCH_ON : SWITCH_OFF);
		icon(8, 4, sw, moveName, moveLore, flip);
		for (int i = 0; i < 3; i++) {
			ResourceKey<Level> dim = MapScreen.WORLD_KEYS[i];
			String id = dim.identifier().toString();
			boolean exists = Scan.world(dim) != null;
			boolean on = Plan.dim(id).on;
			Component name = bold(MapScreen.WORLD_NAMES[i]);
			List<Component> lore = new ArrayList<>();
			lore.add(Ui.text(worldLine(this.player, dim), ChatFormatting.AQUA));
			if (i == 2) lore.add(grey("The main island always stays."));
			lore.add(Component.empty());
			lore.add(hint("Click: its map"));
			lore.add(hint("Right-click: take part or not"));
			int row = 6 + i;
			areaClick(0, row, 8, 1, name, lore, (p, rightClick) -> {
				if (!exists) return;
				if (rightClick) {
					toggle(id);
					refresh();
				} else {
					MapScreen.open(p, dim);
				}
			});
			List<Component> swLore = List.of(Ui.text(on ? "Takes part" : "Takes no part", ChatFormatting.AQUA), Component.empty(), hint("Click: " + (on ? "leave it out" : "take part")));
			ItemStack s2 = new ItemStack(Items.PAPER);
			s2.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, on ? SWITCH_ON : SWITCH_OFF);
			icon(8, row, s2, name, swLore, p -> {
				if (!exists) return;
				toggle(id);
				refresh();
			});
		}
		area(0, 9, 3, 1, bold("Back"), List.of(), p -> AdminWindow.open(p));
		boolean planned = Plan.STORE.get().planned;
		if (planned) {
			area(3, 9, 6, 1, Ui.text("Planned", 0xE0605A).withStyle(ChatFormatting.BOLD), List.of(grey("At the next restart it happens."), Component.empty(),
					hint("Click: cancel it")), p -> {
						if (!Ui.isOp(p)) return;
						Plan.STORE.get().planned = false;
						Plan.STORE.markDirty();
						Restart.cancel(p.level().getServer());
						Notify.whisper(p, grey("World refresh cancelled"));
						refresh();
					});
		} else {
			List<Component> lore = new ArrayList<>();
			if (!canPlan()) {
				lore.add(grey("Turn on a world first (its switch),"));
				lore.add(grey("and wait till it is read wholly."));
			} else {
				lore.add(grey("You're asked first; it happens at"));
				lore.add(grey("the next restart."));
				lore.add(Component.empty());
				lore.add(hint("Click: plan it"));
			}
			area(3, 9, 6, 1, bold("Plan the refresh"), lore, p -> {
				if (canPlan()) confirm(p);
			});
		}
	}

	private static void toggle(String id) {
		Plan.Dim d = Plan.dim(id);
		d.on = !d.on;
		Plan.STORE.markDirty();
	}

	/** Asked first: what happens, per world. */
	private static void confirm(ServerPlayer p) {
		Window w = Window.path("World refresh", "Plan");
		StringBuilder sb = new StringBuilder("At the next restart:");
		for (int i = 0; i < 3; i++) {
			ResourceKey<Level> dim = MapScreen.WORLD_KEYS[i];
			Scan.World sw = Scan.world(dim);
			if (sw == null || !Plan.dim(dim.identifier().toString()).on) continue;
			Plan.Count n = Plan.count(new Plan.Ctx(p.level().getServer(), dim), sw);
			sb.append("\n").append(MapScreen.WORLD_NAMES[i]).append(": ").append(Ui.n(n.go())).append(" chunks are made anew, ").append(Ui.n(n.stay())).append(" stay.");
		}
		sb.append("\n\n").append(RefreshMod.MOVE.get() ? "Their structures go to new places (strongholds stay). " : "Structures stay where they were. ");
		sb.append("Claims and what's around them stay as they are. Nothing is saved of what goes. Restart the server when it suits you; "
				+ "until then you can still change it.");
		w.text(Ui.text(sb.toString(), ChatFormatting.GRAY));
		w.button(Ui.text("✔ Plan it", ChatFormatting.GREEN), null, (q, v) -> {
			if (!Ui.isOp(q)) return;
			var counts = Restart.write(q.level().getServer());
			Plan.STORE.get().planned = true;
			Plan.STORE.markDirty();
			RefreshMod.LOG.info("{} planned a world refresh: {}", q.getName().getString(), counts);
			Notify.whisper(q, Ui.text("World refresh planned for the next restart", ChatFormatting.GREEN));
			open(q);
		});
		w.cancel(RefreshCanvas::open).show(p);
	}

	/** Without the pack: the same in a plain window. */
	private static void dialog(ServerPlayer p) {
		Window w = Window.path("Settings", "Server settings", "World refresh");
		w.text(Ui.text("The map needs the server's resource pack. Here: what stays, and plan or cancel the refresh.", ChatFormatting.GRAY));
		for (int i = 0; i < 3; i++) {
			ResourceKey<Level> dim = MapScreen.WORLD_KEYS[i];
			String id = dim.identifier().toString();
			w.button(Ui.text(MapScreen.WORLD_NAMES[i] + ": " + worldLine(p, dim), ChatFormatting.WHITE), Ui.text("Click: take part or not", ChatFormatting.GRAY), (q, v) -> {
				toggle(id);
				dialog(q);
			});
		}
		if (Plan.STORE.get().planned) {
			w.button(Ui.text("Cancel the planned refresh", ChatFormatting.RED), null, (q, v) -> {
				Plan.STORE.get().planned = false;
				Plan.STORE.markDirty();
				Restart.cancel(q.level().getServer());
				dialog(q);
			});
		} else if (canPlan()) {
			w.button(Ui.text("Plan the refresh", ChatFormatting.GREEN), null, (q, v) -> confirm(q));
		}
		w.back(AdminWindow::open).show(p);
	}
}
