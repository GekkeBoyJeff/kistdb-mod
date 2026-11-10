package nl.libreria.teleports;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import nl.libreria.core.Domain;
import nl.libreria.core.Players;
import nl.libreria.core.ProfileWindow;
import nl.libreria.core.Start;
import nl.libreria.core.Ui;
import nl.libreria.core.dialog.Window;
import nl.libreria.core.gui.Canvas;
import nl.libreria.core.gui.CanvasWindow;
import nl.libreria.core.gui.Glyphs;
import org.jetbrains.annotations.Nullable;

/**
 * Land &amp; places › Teleport to a player. At the top the asks waiting for you (click: accept, right-click: say no),
 * then everyone else who's here (click: ask to go to them, right-click: ask them to come to you; click again to take
 * an ask back). The bottom row: back, and who may ask you.
 */
final class TeleportWindow extends CanvasWindow {
	private static final int[] ROWS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
	private static final int TEXT = 0xFFFFFF, ASK = 0xFFD966;
	private int page;

	/** One row: an ask for you, or someone you can ask. */
	private record Row(ServerPlayer who, @Nullable Asks.Ask ask, @Nullable Asks.Ask sent) {
	}

	private TeleportWindow(ServerPlayer p) {
		super(p);
	}

	static void open(ServerPlayer p) {
		if (!TeleportsMod.TPA.get()) {
			nl.libreria.core.Notify.warn(p, net.minecraft.world.item.Items.BARRIER, "No teleport asks here", "They're turned off on this server.");
			return;
		}
		if (!CanvasWindow.available(p)) {
			dialog(p);
			return;
		}
		new TeleportWindow(p).show();
	}

	private List<Row> rows() {
		List<Row> out = new ArrayList<>();
		for (Asks.Ask a : Asks.waitingFor(this.player).reversed()) out.add(new Row(a.from(), a, null));
		List<ServerPlayer> others = new ArrayList<>(this.player.level().getServer().getPlayerList().getPlayers());
		others.removeIf(o -> o == this.player || out.stream().anyMatch(r -> r.who() == o));
		others.sort(java.util.Comparator.comparing(o -> o.getName().getString().toLowerCase(java.util.Locale.ROOT)));
		for (ServerPlayer o : others) out.add(new Row(o, null, Asks.sent(this.player, o)));
		return out;
	}

	private String status(Row r) {
		if (r.ask() != null) return r.ask().here() ? "asks you over" : "wants to come";
		if (r.sent() != null) return "asked · " + Math.max(1, (r.sent().until() - System.currentTimeMillis() + 999) / 1000) + " s";
		if (r.who().level() != this.player.level()) return Homes.world(r.who().level().dimension().identifier().toString());
		return Ui.meters(this.player.position().distanceTo(r.who().position()));
	}

	private String whoMayAsk() {
		return TeleportsMod.ASKS.labelAt(TeleportsMod.ASKS.index(this.player));
	}

	@Override
	protected Canvas draw() {
		List<Row> all = rows();
		int pages = Math.max(1, (all.size() + ROWS.length - 1) / ROWS.length);
		this.page = Math.max(0, Math.min(this.page, pages - 1));
		Canvas c = new Canvas().panel();
		int x = 8;
		c.text(Domain.PLACES.label, x, 5, Domain.PLACES.rgb);
		x += Glyphs.width(Component.literal(Domain.PLACES.label));
		c.text(" › ", x, 5, Glyphs.muted);
		c.text("Teleport", x + Glyphs.width(Component.literal(" › ")), 5, TEXT);

		int left = Canvas.cellX(1) + 4, width = Canvas.cellX(9) - Canvas.CARD_MARGIN - left;
		int from = this.page * ROWS.length;
		if (all.isEmpty()) {
			c.centred("Nobody else is here right now.", Canvas.cellX(4) + 9, Canvas.cellY(1) + 5, Glyphs.muted);
		}
		for (int i = 0; i < ROWS.length && from + i < all.size(); i++) {
			Row r = all.get(from + i);
			int row = ROWS[i];
			c.line(left, row, width, r.ask() != null ? ASK : Domain.PLACES.rgb);
			int y = Canvas.cellY(row) + 5;
			String s = status(r);
			int sw = Glyphs.width(Component.literal(s));
			c.right(s, left + width - 6, y, r.ask() != null ? ASK : Glyphs.muted);
			c.text(Canvas.fit(r.who().getName().getString(), width - 12 - sw - 8), left + 6, y, r.ask() != null ? ASK : TEXT);
		}
		small(c, 0, 3, "Back");
		if (pages > 1) {
			if (this.page > 0) small(c, 3, 1, "‹");
			if (this.page < pages - 1) small(c, 4, 1, "›");
		}
		small(c, pages > 1 ? 5 : 4, pages > 1 ? 4 : 5, "Who may ask");
		return c;
	}

	private static void small(Canvas c, int col, int w, String label) {
		c.card(col, 9, w, 1, Glyphs.neutral);
		c.centred(Canvas.fit(label, 18 * w - 8), Canvas.cellX(col) + 9 * w, Canvas.cellY(9) + 5, TEXT);
	}

	@Override
	protected void fill() {
		List<Row> all = rows();
		int pages = Math.max(1, (all.size() + ROWS.length - 1) / ROWS.length);
		int from = this.page * ROWS.length;
		for (int i = 0; i < ROWS.length && from + i < all.size(); i++) {
			Row r = all.get(from + i);
			String name = r.who().getName().getString();
			Component title = Ui.text(name, r.ask() != null ? ASK : Domain.PLACES.rgb).withStyle(ChatFormatting.BOLD);
			List<Component> lore = new ArrayList<>();
			lore.add(Ui.text(status(r), ChatFormatting.GRAY));
			lore.add(Component.empty());
			if (r.ask() != null) {
				lore.add(Ui.text(r.ask().here() ? name + " asks you to come to them." : name + " wants to come to you.", ChatFormatting.WHITE));
				lore.add(Ui.text("▸ Click: yes", ChatFormatting.GREEN));
				lore.add(Ui.text("▸ Right-click: no", ChatFormatting.RED));
			} else if (r.sent() != null) {
				lore.add(Ui.text("▸ Click: take your ask back", ChatFormatting.YELLOW));
			} else {
				lore.add(Ui.text("▸ Click: ask to go to " + name, ChatFormatting.YELLOW));
				lore.add(Ui.text("▸ Right-click: ask " + name + " to come to you", ChatFormatting.YELLOW));
			}
			java.util.function.BiConsumer<ServerPlayer, Boolean> act = (p, right) -> {
				if (r.ask() != null) {
					this.close();
					Asks.answer(p, name, !right);
				} else if (r.sent() != null) {
					Asks.cancel(p, name);
					refresh();
				} else {
					Asks.ask(p, r.who(), right);
					refresh();
				}
			};
			areaClick(1, ROWS[i], 8, 1, title, lore, act);
			Players.Known k = Players.get(this.player.level().getServer(), r.who().getUUID());
			ItemStack head = k != null ? ProfileWindow.head(k) : new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
			iconTyped(0, ROWS[i], head, title, lore, (p, type) -> act.accept(p, type.isRight));
		}
		area(0, 9, 3, 1, Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), List.of(), p -> Start.domain(p, Domain.PLACES));
		if (pages > 1) {
			if (this.page > 0) area(3, 9, 1, 1, Ui.text("Previous page", ChatFormatting.WHITE), List.of(), p -> turn(-1));
			if (this.page < pages - 1) area(4, 9, 1, 1, Ui.text("Next page", ChatFormatting.WHITE), List.of(), p -> turn(1));
		}
		area(pages > 1 ? 5 : 4, 9, pages > 1 ? 4 : 5, 1, Ui.text("Who may ask you: " + whoMayAsk(), ChatFormatting.WHITE),
				List.of(Ui.text("Everyone, the people you trust on", ChatFormatting.GRAY), Ui.text("your land, or nobody.", ChatFormatting.GRAY),
						Component.empty(), Ui.text("▸ Click: change", ChatFormatting.YELLOW)), p -> {
					if (TeleportsMod.ASKS.isForced()) {
						nl.libreria.core.Notify.info(p, net.minecraft.world.item.Items.ENDER_PEARL, "Set for everybody", "An operator decides who may ask here.");
						return;
					}
					TeleportsMod.ASKS.cycle(p, 1);
					refresh();
				});
	}

	private void turn(int step) {
		this.page += step;
		refresh();
	}

	/** Without the pack: the same as a window with buttons. */
	private static void dialog(ServerPlayer p) {
		Window w = Window.of(Domain.PLACES, "Teleport to a player").wide();
		for (Asks.Ask a : Asks.waitingFor(p).reversed()) {
			String name = a.from().getName().getString();
			w.button(Ui.text("✔ " + name + (a.here() ? " asks you over" : " wants to come"), ChatFormatting.GREEN), null, (q, v) -> Asks.answer(q, name, true));
			w.button(Ui.text("✖ Say no to " + name, ChatFormatting.RED), null, (q, v) -> Asks.answer(q, name, false));
		}
		for (ServerPlayer o : p.level().getServer().getPlayerList().getPlayers()) {
			if (o == p) continue;
			w.button(Ui.text("Go to " + o.getName().getString(), ChatFormatting.WHITE), null, (q, v) -> Asks.ask(q, o, false));
			w.button(Ui.text("Ask " + o.getName().getString() + " over", ChatFormatting.WHITE), null, (q, v) -> Asks.ask(q, o, true));
		}
		w.back(q -> Start.domain(q, Domain.PLACES)).show(p);
	}
}
