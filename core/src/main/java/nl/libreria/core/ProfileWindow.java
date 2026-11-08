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
 * A player's profile, as a window of our own: a card with their name, whether they're here and their title; their
 * showcase (three achievements, rim and name in the rarity's colour); then four rows (playtime, achievements, points,
 * records). The rest (since when, items, monsters…) is in the tooltip of the card. On your own profile a click on the
 * showcase lets you choose what's in it.
 */
public final class ProfileWindow extends CanvasWindow {
	/** The showcase, and the rows under it (without a showcase the rows start right under the card). */
	private static final int[] SHOW = {2, 3, 4};
	private static final int[] MAIN = {5, 6, 7, 8};
	private static final int[] ALL = {2, 3, 4, 5, 6, 7, 8};
	static final int ONLINE = 0x7CE07C;

	private final Players.Known who;
	private final Consumer<ServerPlayer> back;
	/** "Players" when opened from the list, "" when it's your own from You. */
	private final String via;
	private final List<String[]> facts = new ArrayList<>();
	private final List<ItemStack> icons = new ArrayList<>();
	private final List<Profile.Line> factLines = new ArrayList<>();
	private final List<String[]> more = new ArrayList<>();
	private final List<Profile.Shown> shown = new ArrayList<>();
	private final boolean showcase;

	private ProfileWindow(ServerPlayer p, Players.Known who, String via, Consumer<ServerPlayer> back) {
		super(p);
		this.who = who;
		this.via = via;
		this.back = back;
		MinecraftServer server = p.level().getServer();
		Profile.Stats stats = Profile.stats(server, who.id());
		Profile.Showcase sc = Profile.showcase();
		this.showcase = sc != null;
		if (sc != null) {
			try {
				this.shown.addAll(sc.of(server, who.id()));
			} catch (RuntimeException e) {
				Libreria.LOG.warn("Profile showcase failed", e);
			}
		}
		int room = this.showcase ? MAIN.length : ALL.length;
		for (Profile.Line l : Profile.lines()) {
			String v;
			try {
				v = l.value().of(server, who.id(), stats);
			} catch (RuntimeException e) {
				Libreria.LOG.warn("Profile line {} failed", l.label(), e);
				v = null;
			}
			if (v == null) continue;
			if (l.main() && this.facts.size() < room) {
				this.facts.add(new String[] {l.label(), v});
				this.icons.add(new ItemStack(l.icon()));
				this.factLines.add(l);
			} else {
				this.more.add(new String[] {l.label(), v});
			}
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

	private int[] factRows() {
		return this.showcase ? MAIN : ALL;
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
		String second = title != null ? title.getString().replace("✦", "").trim() : !this.who.shown().isEmpty() ? this.who.name() : "";
		if (!second.isEmpty()) c.text(Canvas.fit(second, x1 - x0), x0, Canvas.cellY(0) + 20, title != null ? titleColor : StartWindow.MUTED);

		int left = Canvas.cellX(1) + 4, width = Canvas.cellX(9) - Canvas.CARD_MARGIN - left;
		// The showcase: rim and name in the rarity's colour (on your own profile an empty place says what to do)
		if (this.showcase) {
			for (int i = 0; i < SHOW.length; i++) {
				int row = SHOW[i], y = Canvas.cellY(row) + 5;
				if (i < this.shown.size()) {
					Profile.Shown s = this.shown.get(i);
					c.line(left, row, width, s.rgb());
					c.text(Canvas.fit(s.name(), width - 12), left + 6, y, s.rgb());
				} else if (own()) {
					c.line(left, row, width, 0x6E7480);
					c.text("Choose an achievement", left + 6, y, StartWindow.MUTED);
				}
			}
		}
		// The main rows
		int[] rows = factRows();
		for (int i = 0; i < this.facts.size(); i++) {
			String[] f = this.facts.get(i);
			int row = rows[i];
			c.line(left, row, width, Domain.YOU.rgb);
			int y = Canvas.cellY(row) + 5;
			int vw = Glyphs.width(Component.literal(f[1]));
			c.right(Canvas.fit(f[1], width - 12 - 40), left + width - 6, y, StartWindow.MUTED);
			c.text(Canvas.fit(f[0], width - 12 - vw - 8), left + 6, y, StartWindow.TEXT);
		}
		c.card(0, 9, 4, 1, StartWindow.NEUTRAL);
		c.centred("Back", Canvas.cellX(0) + 36, Canvas.cellY(9) + 5, StartWindow.TEXT);
		c.card(4, 9, 5, 1, StartWindow.NEUTRAL);
		c.centred("Progress", Canvas.cellX(4) + 45, Canvas.cellY(9) + 5, StartWindow.TEXT);
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
		if (!this.more.isEmpty()) about.add(Component.empty());
		for (String[] m : this.more) {
			about.add(Component.empty().append(Ui.text(m[0] + ": ", ChatFormatting.GRAY)).append(Ui.text(m[1], ChatFormatting.WHITE)));
		}
		Component name = Ui.text(this.who.label(), Domain.YOU.rgb).withStyle(ChatFormatting.BOLD);
		area(0, 0, 9, 2, name, about, p -> {
		});
		Profile.Showcase sc = Profile.showcase();
		if (sc != null) {
			Consumer<ServerPlayer> reopen = q -> open(q, this.who.id(), this.via, this.back);
			for (int i = 0; i < SHOW.length; i++) {
				int slot = i;
				Consumer<ServerPlayer> choose = own() ? q -> sc.choose(q, slot, reopen) : q -> {
				};
				if (i < this.shown.size()) {
					Profile.Shown s = this.shown.get(i);
					List<Component> lore = new ArrayList<>(s.lore());
					if (own()) {
						lore.add(Component.empty());
						lore.add(Ui.text("▸ Click: choose another", ChatFormatting.YELLOW));
					}
					Component label = Ui.text(s.name(), s.rgb()).withStyle(ChatFormatting.BOLD);
					area(1, SHOW[i], 8, 1, label, lore, choose);
					icon(0, SHOW[i], s.icon(), label, lore, choose);
				} else if (own()) {
					List<Component> lore = List.of(Ui.text("Three achievements on your profile,", ChatFormatting.GRAY),
							Ui.text("for everyone to see.", ChatFormatting.GRAY), Component.empty(), Ui.text("▸ Click: choose", ChatFormatting.YELLOW));
					area(0, SHOW[i], 9, 1, Ui.text("Your showcase", Domain.YOU.rgb).withStyle(ChatFormatting.BOLD), lore, choose);
				}
			}
		}
		int[] rows = factRows();
		for (int i = 0; i < this.facts.size(); i++) {
			String[] f = this.facts.get(i);
			Component label = Ui.text(f[0], Domain.YOU.rgb).withStyle(ChatFormatting.BOLD);
			Profile.Line line = this.factLines.get(i);
			Consumer<ServerPlayer> go = null;
			if (line.click() != null) {
				try {
					go = line.click().on(this.player, this.who.id());
				} catch (RuntimeException e) {
					Libreria.LOG.warn("Profile row {} failed", line.label(), e);
				}
			}
			List<Component> lore = new ArrayList<>(List.of(Ui.text(f[1], ChatFormatting.WHITE)));
			if (go != null) {
				lore.add(Component.empty());
				lore.add(Ui.text("▸ Click: " + line.hint(), ChatFormatting.YELLOW));
			}
			Consumer<ServerPlayer> click = go != null ? go : p -> {
			};
			area(1, rows[i], 8, 1, label, lore, click);
			icon(0, rows[i], this.icons.get(i), label, lore, click);
		}
		area(0, 9, 4, 1, Ui.text("Back", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD), List.of(), this.back);
		Consumer<ServerPlayer> reopen = q -> open(q, this.who.id(), this.via, this.back);
		area(4, 9, 5, 1, Ui.text("Progress", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD),
				List.of(Ui.text(own() ? "Everything you can complete." : "How far " + this.who.label() + " is.", ChatFormatting.GRAY)),
				q -> ProgressWindow.open(q, this.who.id(), "Profile", reopen));
	}
}
