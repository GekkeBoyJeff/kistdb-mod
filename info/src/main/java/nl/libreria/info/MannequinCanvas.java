package nl.libreria.info;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.Domain;
import nl.libreria.core.Hud;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.Canvas;
import nl.libreria.core.gui.CanvasWindow;
import nl.libreria.core.gui.Glyphs;

/**
 * A mannequin's window in Libreria's own look. On top, how it stands: six stances, each a little picture (the one it
 * has now lit up). Then its head (where it looks) and its hands (what it does with what it holds). Below: the twelve
 * spots it holds things on (click one: it comes off), another look, which way it faces and which hand is its main one.
 */
final class MannequinCanvas extends CanvasWindow {
	static final int ACCENT = Domain.PROGRESS.rgb;
	private static final int TEXT = 0xFFFFFF, VALUE = 0x8FE3F0, NEUTRAL = Glyphs.neutral, OFF = 0x5A5E66;
	private static final String HINT = "‹›";

	private final Mannequin m;

	private MannequinCanvas(ServerPlayer p, Mannequin m) {
		super(p);
		this.m = m;
	}

	static boolean available(ServerPlayer p) {
		return CanvasWindow.available(p);
	}

	static void open(ServerPlayer p, Mannequin m) {
		new MannequinCanvas(p, m).show();
	}

	/** The stances in their two rows of three. */
	private static final Poses.Stance[] GRID = {Poses.Stance.STAND, Poses.Stance.SIT, Poses.Stance.LEAN, Poses.Stance.CROUCH, Poses.Stance.LIE,
			Poses.Stance.CRAWL};

	/** The spots, in the two rows they show in: what it wears and holds, then its back, hips and feet. */
	private static final Showcase.Spot[] SPOTS = Showcase.Spot.values();

	private static int spotCol(int i) {
		return i % 6;
	}

	private static int spotRow(int i) {
		return 6 + i / 6;
	}

	// ---------------------------------------------------------------- picture

	@Override
	protected Canvas draw() {
		Canvas c = new Canvas().panel();
		c.text("Mannequin", 8, 5, ACCENT);
		int x = 8 + Glyphs.width(Component.literal("Mannequin"));
		c.text(" › ", x, 5, Glyphs.muted);
		x += Glyphs.width(Component.literal(" › "));
		Poses.Stance now = Poses.stance(this.m);
		String where = Poses.where(this.m);
		c.text(Canvas.fit(now.doing + (where.isEmpty() ? "" : " " + where), 168 - x), x, 5, TEXT);
		// Six stances
		for (int i = 0; i < GRID.length; i++) {
			int col = (i % 3) * 3, row = (i / 3) * 2;
			boolean on = GRID[i] == now;
			c.card(col, row, 3, 2, on ? ACCENT : NEUTRAL);
			c.centred(GRID[i].verb, Canvas.cellX(col) + 27, Canvas.cellY(row + 1) + 4, on ? TEXT : 0xD8DCE4);
		}
		int left = Canvas.cellX(0) + Canvas.CARD_MARGIN + 6, right = Canvas.cellX(9) - Canvas.CARD_MARGIN - 6;
		int hw = Glyphs.width(Component.literal(HINT));
		// Its head
		c.card(0, 4, 9, 1, NEUTRAL);
		c.text("Head", left, Canvas.cellY(4) + 5, TEXT);
		c.text(HINT, right - hw, Canvas.cellY(4) + 5, Glyphs.muted);
		c.right(Poses.lookLabel(this.m), right - hw - 6, Canvas.cellY(4) + 5, VALUE);
		// Its hands
		boolean acts = Poses.acts(this.m).size() > 1;
		c.card(0, 5, 9, 1, acts ? NEUTRAL : OFF);
		c.text("Hands", left, Canvas.cellY(5) + 5, acts ? TEXT : Glyphs.muted);
		if (acts) {
			c.text(HINT, right - hw, Canvas.cellY(5) + 5, Glyphs.muted);
			c.right(Poses.handLabel(this.m), right - hw - 6, Canvas.cellY(5) + 5, VALUE);
		} else {
			c.right(Poses.handLabel(this.m), right, Canvas.cellY(5) + 5, Glyphs.muted);
		}
		// The twelve spots
		for (int i = 0; i < SPOTS.length; i++) c.card(spotCol(i), spotRow(i), 1, 1, Showcase.get(this.m, SPOTS[i]).isEmpty() ? OFF : NEUTRAL);
		// Another look
		c.card(6, 6, 3, 2, NEUTRAL);
		c.centred("New look", Canvas.cellX(6) + 27, Canvas.cellY(7) + 4, TEXT);
		// Turning, and its main hand
		c.card(0, 8, 4, 1, NEUTRAL);
		int l8 = Canvas.cellX(0) + Canvas.CARD_MARGIN + 6, r8 = Canvas.cellX(4) - Canvas.CARD_MARGIN - 6;
		c.text("Turn it", l8, Canvas.cellY(8) + 5, TEXT);
		c.text(HINT, r8 - hw, Canvas.cellY(8) + 5, Glyphs.muted);
		c.card(4, 8, 5, 1, NEUTRAL);
		boolean lefty = this.m.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT;
		c.centred(lefty ? "Left-handed" : "Right-handed", Canvas.cellX(4) + 45, Canvas.cellY(8) + 5, TEXT);
		// Done
		c.card(0, 9, 9, 1, NEUTRAL);
		c.centred("Done", Canvas.cellX(0) + 81, Canvas.cellY(9) + 5, TEXT);
		return c;
	}

	// ---------------------------------------------------------------- cells

	private static Component bold(String s) {
		return Ui.text(s, ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD);
	}

	private static Component grey(String s) {
		return Ui.text(s, ChatFormatting.GRAY);
	}

	private static Component hint(String s) {
		return Ui.text("▸ " + s, ChatFormatting.YELLOW);
	}

	private static String about(Poses.Stance s) {
		return switch (s) {
			case STAND -> "Up straight, as you placed it.";
			case SIT -> "On the stair or slab under it, as on a chair (a stair: its back behind it); else on the ground.";
			case LEAN -> "Sitting, bent forward with its elbows on its knees.";
			case CROUCH -> "Low, as when you sneak.";
			case LIE -> "On its back. On a bed, it lies in it.";
			case CRAWL -> "Face down, as when you crawl; in water it swims.";
		};
	}

	/** It may be changed by this player (near, and allowed to build there); else the window closes. */
	private boolean ok(ServerPlayer p) {
		if (Showcase.usable(p, this.m)) return true;
		p.closeContainer();
		return false;
	}

	@Override
	protected void fill() {
		Mannequin m = this.m;
		Poses.Stance now = Poses.stance(m);
		for (int i = 0; i < GRID.length; i++) {
			Poses.Stance s = GRID[i];
			int col = (i % 3) * 3, row = (i / 3) * 2;
			List<Component> lore = new ArrayList<>();
			for (String line : Canvas.wrap(about(s), 170)) lore.add(grey(line));
			lore.add(Component.empty());
			lore.add(s == now ? Ui.text("✔ Now", ChatFormatting.GREEN) : hint("Click: " + s.verb.toLowerCase(java.util.Locale.ROOT)));
			Consumer<ServerPlayer> go = p -> {
				if (!ok(p)) return;
				String why = Poses.set(m, s);
				if (why != null) Hud.flash(p, grey(why));
				refresh();
			};
			area(col, row, 3, 2, bold(s.verb), lore, go);
			ItemStack pic = new ItemStack(Items.PAPER);
			pic.set(DataComponents.ITEM_MODEL, Identifier.parse(s.icon()));
			icon(col + 1, row, pic, bold(s.verb), lore, go);
		}
		// Its head: click on, right-click back, shift-click towards you
		List<Component> head = List.of(Ui.text("Now: " + Poses.lookLabel(m).toLowerCase(java.util.Locale.ROOT), ChatFormatting.AQUA), grey("Up, down, to its left or right."),
				Component.empty(), hint("Click: next · right-click: back"), hint("Shift-click: towards you"));
		areaTyped(0, 4, 9, 1, bold("Head"), head, (p, type) -> {
			if (!(type.isLeft || type.isRight) || !ok(p)) return;
			Ui.click(p);
			if (type.shift) {
				Poses.lookAt(m, p);
			} else {
				Poses.Look l = Poses.look(m, false);
				int n = Poses.Look.values().length;
				Poses.Look next = Poses.Look.values()[Math.floorMod(l.ordinal() + (type.isRight ? -1 : 1), n)];
				// from "towards you", a click goes back to ahead
				if (Poses.look(m, true) == null) next = Poses.Look.AHEAD;
				Poses.setLook(m, next.yaw, next.pitch);
			}
			refresh();
		});
		// Its hands
		var acts = Poses.acts(m);
		List<Component> hands = new ArrayList<>();
		hands.add(Ui.text("Now: " + Poses.handLabel(m).toLowerCase(java.util.Locale.ROOT), ChatFormatting.AQUA));
		hands.add(grey("A bow, a shield, a spyglass, a goat"));
		hands.add(grey("horn or a trident in a hand: it uses"));
		hands.add(grey("it. A loaded crossbow aims by itself."));
		if (acts.size() > 1) {
			hands.add(Component.empty());
			hands.add(hint("Click: next · right-click: back"));
		}
		areaClick(0, 5, 9, 1, bold("Hands"), hands, (p, right) -> {
			if (!ok(p) || acts.size() < 2) return;
			int i = Math.max(0, acts.indexOf(Poses.hand(m)));
			InteractionHand next = acts.get(Math.floorMod(i + (right ? -1 : 1), acts.size()));
			Poses.setHand(m, next);
			refresh();
		});
		// The spots: click one, it comes off
		for (int i = 0; i < SPOTS.length; i++) {
			Showcase.Spot s = SPOTS[i];
			ItemStack st = Showcase.get(m, s);
			if (st.isEmpty()) {
				area(spotCol(i), spotRow(i), 1, 1, bold(s.label), List.of(grey("Nothing here. Right-click the"), grey("mannequin with something: it")
						, grey("puts it where it fits.")), p -> {
						});
				continue;
			}
			List<Component> lore = List.of(Ui.text(s.label, ChatFormatting.GRAY), Component.empty(), hint("Click: take it off"));
			icon(spotCol(i), spotRow(i), st, Component.empty().append(st.getHoverName().copy().withStyle(x -> x.withItalic(false))), lore, p -> {
				if (!ok(p)) return;
				Showcase.takeOff(p, m, s);
				refresh();
			});
		}
		// Another look
		List<Component> look = List.of(grey("Another person: one of " + Ui.n(Showcase.SKINS.size()) + "."), Component.empty(), hint("Click: someone else"));
		Consumer<ServerPlayer> wear = p -> {
			if (!ok(p)) return;
			Showcase.wear(m, -1);
			refresh();
		};
		area(6, 6, 3, 2, bold("New look"), look, wear);
		ItemStack face = new ItemStack(Items.PLAYER_HEAD);
		face.set(DataComponents.PROFILE, m.getProfile());
		icon(7, 6, face, bold("New look"), look, wear);
		// Turning
		List<Component> turn = m.isSleeping() ? List.of(grey("It lies along its bed."))
				: List.of(Ui.text("Facing " + Poses.facing(m), ChatFormatting.AQUA), Component.empty(), hint("Click: a turn to its right"),
						hint("Right-click: to its left"));
		areaClick(0, 8, 4, 1, bold("Turn it"), turn, (p, right) -> {
			if (!ok(p)) return;
			Poses.turn(m, right ? -1 : 1);
			refresh();
		});
		boolean lefty = m.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT;
		area(4, 8, 5, 1, bold(lefty ? "Left-handed" : "Right-handed"), List.of(grey("Its main hand: the one a bow or a"), grey("sword is held in."), Component.empty(),
				hint("Click: the other hand")), p -> {
					if (!ok(p)) return;
					Showcase.handed(m, !lefty);
					refresh();
				});
		area(0, 9, 9, 1, bold("Done"), List.of(), ServerPlayer::closeContainer);
	}
}
