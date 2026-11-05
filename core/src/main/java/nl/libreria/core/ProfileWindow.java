package nl.libreria.core;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.libreria.core.gui.Canvas;
import nl.libreria.core.gui.CanvasWindow;
import nl.libreria.core.gui.Glyphs;
import org.jetbrains.annotations.Nullable;

/**
 * A player's profile, as a window of our own: a card with their name, whether they're here and their title, then one
 * row per thing they did (playtime, achievements…), the value on the right.
 */
public final class ProfileWindow extends CanvasWindow {
	/** The rows under the card. */
	private static final int[] ROWS = {2, 3, 4, 5, 6, 7, 8};
	static final int ONLINE = 0x7CE07C;

	private final Players.Known who;
	private final Consumer<ServerPlayer> back;
	/** "Players" when opened from the list, "" when it's your own from You. */
	private final String via;
	private final List<String[]> facts = new ArrayList<>();
	private final List<ItemStack> icons = new ArrayList<>();

	private ProfileWindow(ServerPlayer p, Players.Known who, String via, Consumer<ServerPlayer> back) {
		super(p);
		this.who = who;
		this.via = via;
		this.back = back;
		MinecraftServer server = p.level().getServer();
		Profile.Stats stats = Profile.stats(server, who.id());
		for (Profile.Line l : Profile.lines()) {
			if (this.facts.size() >= ROWS.length) break;
			String v;
			try {
				v = l.value().of(server, who.id(), stats);
			} catch (RuntimeException e) {
				Libreria.LOG.warn("Profile line {} failed", l.label(), e);
				v = null;
			}
			if (v == null) continue;
			this.facts.add(new String[] {l.label(), v});
			this.icons.add(new ItemStack(l.icon()));
		}
	}

	/** Opens {@code who}'s profile for {@code viewer}; {@code via} is the window it came from ("Players", or ""). */
	public static void open(ServerPlayer viewer, UUID who, String via, Consumer<ServerPlayer> back) {
		Players.Known k = Players.get(viewer.level().getServer(), who);
		if (k == null) {
			Notify.warn(viewer, Items.PLAYER_HEAD, "Nobody by that name", "Only players who have been here have a profile.");
			return;
		}
		new ProfileWindow(viewer, k, via, back).show();
	}

	/** For the self-test: the picture of someone's profile, as JSON. */
	static String picture(ServerPlayer viewer, UUID who) {
		Players.Known k = Players.get(viewer.level().getServer(), who);
		return k == null ? "" : new ProfileWindow(viewer, k, "Players", q -> {
		}).pictureJson();
	}

	/** A player's head (their own skin when Minecraft knows it). */
	static ItemStack head(Players.Known k) {
		GuiElementBuilder b = new GuiElementBuilder(Items.PLAYER_HEAD);
		if (k.online() != null) b.setProfile(k.online().getGameProfile());
		else b.setProfile(k.id());
		return b.asStack();
	}

	private boolean own() {
		return this.who.id().equals(this.player.getUUID());
	}

	private @Nullable Component title() {
		return Services.progress().map(pr -> pr.titleOf(this.who.id())).orElse(null);
	}

	private String status() {
		if (this.who.online() == null) return this.who.last() > 0 ? Players.ago(this.who.last()) : "";
		return own() ? "Online (you)" : "Online";
	}

	@Override
	protected Canvas draw() {
		Canvas c = new Canvas().panel();
		// You › Players › Anna, or You › Your profile
		int x = 8;
		c.text(Domain.YOU.label, x, 5, Domain.YOU.rgb);
		x += Glyphs.width(Component.literal(Domain.YOU.label));
		String last = this.via.isEmpty() ? "Your profile" : this.who.label();
		if (!this.via.isEmpty()) {
			c.text(" › ", x, 5, Glyphs.muted);
			x += Glyphs.width(Component.literal(" › "));
			c.text(this.via, x, 5, Glyphs.muted);
			x += Glyphs.width(Component.literal(this.via));
		}
		c.text(" › ", x, 5, Glyphs.muted);
		x += Glyphs.width(Component.literal(" › "));
		c.text(Canvas.fit(last, 168 - x), x, 5, StartWindow.TEXT);

		// The card: name and whether they're here; their title in its colour
		int x0 = Canvas.cellX(0) + Canvas.CARD_MARGIN + 6, x1 = Canvas.cellX(9) - Canvas.CARD_MARGIN - 6;
		c.card(0, 0, 9, 2, Domain.YOU.rgb);
		String status = status();
		int sw = status.isEmpty() ? 0 : Glyphs.width(Component.literal(status)) + 8;
		if (!status.isEmpty()) c.right(status, x1, Canvas.cellY(0) + 9, this.who.online() != null ? ONLINE : StartWindow.MUTED);
		c.text(Canvas.fit(this.who.label(), x1 - x0 - sw), x0, Canvas.cellY(0) + 9, StartWindow.TEXT);
		Component title = title();
		int titleColor = title != null && title.getStyle().getColor() != null ? title.getStyle().getColor().getValue() : StartWindow.MUTED;
		String second = title != null ? title.getString().replace("✦", "").trim()
				: !this.who.shown().isEmpty() ? this.who.name() : "";
		if (!second.isEmpty()) c.text(Canvas.fit(second, x1 - x0), x0, Canvas.cellY(0) + 20, title != null ? titleColor : StartWindow.MUTED);

		// One row per thing they did
		int left = Canvas.cellX(1) + 4, width = Canvas.cellX(9) - Canvas.CARD_MARGIN - left;
		for (int i = 0; i < this.facts.size(); i++) {
			String[] f = this.facts.get(i);
			int row = ROWS[i];
			c.line(left, row, width, Domain.YOU.rgb);
			int y = Canvas.cellY(row) + 5;
			int vw = Glyphs.width(Component.literal(f[1]));
			c.right(Canvas.fit(f[1], width - 12 - 40), left + width - 6, y, StartWindow.MUTED);
			c.text(Canvas.fit(f[0], width - 12 - vw - 8), left + 6, y, StartWindow.TEXT);
		}
		c.card(0, 9, 9, 1, StartWindow.NEUTRAL);
		c.centred("Back", Canvas.cellX(0) + 81, Canvas.cellY(9) + 5, StartWindow.TEXT);
		return c;
	}

	@Override
	protected void fill() {
		List<Component> about = new ArrayList<>();
		if (!this.who.shown().isEmpty()) about.add(Ui.text("Real name: " + this.who.name(), ChatFormatting.GRAY));
		Component title = title();
		if (title != null) about.add(Component.empty().append(Ui.text("Title: ", ChatFormatting.GRAY)).append(title));
		about.add(Ui.text(this.who.online() != null ? "Here now" : "Last here: " + Players.ago(this.who.last()).toLowerCase(java.util.Locale.ROOT),
				ChatFormatting.GRAY));
		Component name = Ui.text(this.who.label(), Domain.YOU.rgb).withStyle(ChatFormatting.BOLD);
		area(0, 0, 9, 2, name, about, p -> {
		});
		for (int i = 0; i < this.facts.size(); i++) {
			String[] f = this.facts.get(i);
			Component label = Ui.text(f[0], Domain.YOU.rgb).withStyle(ChatFormatting.BOLD);
			List<Component> lore = List.of(Ui.text(f[1], ChatFormatting.WHITE));
			area(1, ROWS[i], 8, 1, label, lore, p -> {
			});
			icon(0, ROWS[i], this.icons.get(i), label, lore, p -> {
			});
		}
		area(0, 9, 9, 1, Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), List.of(), this.back);
	}
}
