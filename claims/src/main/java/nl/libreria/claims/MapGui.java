package nl.libreria.claims;

import com.mojang.authlib.GameProfile;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.HubGui;
import nl.libreria.core.gui.Menu;

/**
 * Claims · Map: 9×5 chunks around you (north up). Free chunks show the block on top, so it looks like a real map;
 * lime = yours, cyan = you're trusted there, red = someone else's. The chunk you stand in shows your head.
 * Bottom row: back, pan buttons, budget, trusted players, show borders, close and help.
 */
public final class MapGui extends Menu {
	static final int COLS = 9, MAP_ROWS = 5;
	/** How far one click on a pan button moves the map, in chunks. */
	private static final int PAN_X = 4, PAN_Z = 2;

	private int panX, panZ;

	private MapGui(ServerPlayer player) {
		super(player, 6, "Claims · Map");
		build();
	}

	public static void open(ServerPlayer player) {
		new MapGui(player).open();
	}

	/** The menu without opening it (for the self-tests). */
	static MapGui create(ServerPlayer player) {
		return new MapGui(player);
	}

	private void pan(int dx, int dz) {
		Ui.click(this.player);
		this.panX += dx;
		this.panZ += dz;
		build();
	}

	private void build() {
		for (int i = 0; i < this.rows * 9; i++) this.clearSlot(i);
		ServerLevel level = this.player.level();
		ChunkPos here = this.player.chunkPosition();
		int have = ClaimsMod.owned(this.player.getUUID()), max = ClaimsMod.budget(this.player);
		List<String> next = ClaimsMod.nextChunkLines(this.player);

		for (int row = 0; row < MAP_ROWS; row++) {
			for (int col = 0; col < COLS; col++) {
				ChunkPos c = new ChunkPos(here.x() + this.panX + col - COLS / 2, here.z() + this.panZ + row - MAP_ROWS / 2);
				this.setSlot(row * 9 + col, cell(level, c, c.equals(here), have, max, next));
			}
		}
		nav(have, max, next);
		finish();
	}

	// ---------------------------------------------------------------- the map

	private GuiElementBuilder cell(ServerLevel level, ChunkPos c, boolean here, int have, int max, List<String> next) {
		ClaimsMod.Claim claim = ClaimsMod.claimAt(level, c);
		boolean mine = claim != null && claim.owner.equals(this.player.getUUID());
		boolean trusted = claim != null && ClaimsMod.trustedIn(this.player, claim);
		String state;
		ChatFormatting color;
		Item icon;
		if (claim == null) {
			state = "Free land";
			color = ChatFormatting.WHITE;
			icon = surface(level, c);
		} else if (mine) {
			state = "Your land";
			color = ChatFormatting.GREEN;
			icon = Ui.item("lime_concrete");
		} else {
			state = "Claimed by " + claim.ownerName;
			color = trusted ? ChatFormatting.AQUA : ChatFormatting.RED;
			icon = Ui.item(trusted ? "cyan_concrete" : "red_concrete");
		}

		GuiElementBuilder b;
		if (here) {
			b = head(this.player.getGameProfile()).setName(title("◉ You are here", ChatFormatting.YELLOW)).hideDefaultTooltip();
			b.addLoreLine(Ui.text(state, color));
		} else {
			b = new GuiElementBuilder(icon).setName(title(state, color)).hideDefaultTooltip();
		}
		b.addLoreLine(Ui.text("Chunk " + c.x() + ", " + c.z(), ChatFormatting.DARK_GRAY));

		if (claim == null) {
			if (have < max) {
				actions(b, "Click: claim (" + have + "/" + max + " used)");
			} else {
				b.addLoreLine(Component.empty());
				b.addLoreLine(Ui.text("Budget full (" + have + "/" + max + " used)", ChatFormatting.RED));
				for (String l : next) b.addLoreLine(Ui.text(l, ChatFormatting.GRAY));
			}
			b.setCallback((i, t, a, g) -> {
				if (t == ClickType.MOUSE_DOUBLE_CLICK) return;
				Ui.click(this.player);
				ClaimsMod.claim(this.player, level, c);
				build();
			});
		} else if (mine) {
			b.addLoreLine(Ui.text("Only you and trusted players", ChatFormatting.GRAY));
			b.addLoreLine(Ui.text("can build here.", ChatFormatting.GRAY));
			actions(b, "Shift-click: release");
			b.setCallback((i, t, a, g) -> {
				if (!t.shift) return;
				Ui.click(this.player);
				if (ClaimsMod.unclaim(this.player, level, c)) build();
			});
		} else if (trusted) {
			b.addLoreLine(Ui.text("You're trusted here:", ChatFormatting.AQUA));
			b.addLoreLine(Ui.text("you can build.", ChatFormatting.AQUA));
		} else {
			b.addLoreLine(Ui.text("Only " + claim.ownerName + " and their", ChatFormatting.GRAY));
			b.addLoreLine(Ui.text("friends can build here.", ChatFormatting.GRAY));
		}
		return b;
	}

	/** Het bovenste blok midden in de chunk, als icoon. */
	private static Item surface(ServerLevel level, ChunkPos c) {
		int x = c.getMiddleBlockX(), z = c.getMiddleBlockZ();
		// Full glass blocks (not panes), so unknown land doesn't look like the menu's filler
		if (!level.hasChunk(c.x(), c.z())) return Ui.item("light_gray_stained_glass");
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		BlockState st = level.getBlockState(new BlockPos(x, y, z));
		if (st.getFluidState().is(Fluids.WATER) || st.getFluidState().is(Fluids.FLOWING_WATER)) return Ui.item("blue_stained_glass");
		if (st.getFluidState().is(Fluids.LAVA) || st.getFluidState().is(Fluids.FLOWING_LAVA)) return Ui.item("orange_stained_glass");
		Item item = st.getBlock().asItem();
		return item == Items.AIR ? Ui.item("light_gray_stained_glass") : item;
	}

	// ---------------------------------------------------------------- navigation bar

	private void nav(int have, int max, List<String> next) {
		int nav = navRow();
		back("the Libreria menu", () -> HubGui.open(this.player));

		GuiElementBuilder we = button(Items.ARROW, "◀ West · East ▶", ChatFormatting.WHITE, "Move the map " + PAN_X + " chunks.");
		actions(we, "Left-click: west", "Right-click: east");
		this.setSlot(nav + 1, we.setCallback((i, t, a, g) -> {
			if (t.isLeft) pan(-PAN_X, 0);
			else if (t.isRight) pan(PAN_X, 0);
		}));
		GuiElementBuilder ns = button(Items.ARROW, "▲ North · South ▼", ChatFormatting.WHITE, "Move the map " + PAN_Z + " chunks.");
		actions(ns, "Left-click: north", "Right-click: south");
		this.setSlot(nav + 2, ns.setCallback((i, t, a, g) -> {
			if (t.isLeft) pan(0, -PAN_Z);
			else if (t.isRight) pan(0, PAN_Z);
		}));
		if (this.panX != 0 || this.panZ != 0) {
			GuiElementBuilder center = button(Items.COMPASS, "◉ Back to you", ChatFormatting.YELLOW, "Center the map on the chunk", "you stand in.");
			actions(center, "Click: center");
			this.setSlot(nav + 3, center.setCallback((i, t, a, g) -> pan(-this.panX, -this.panZ)));
		}

		closeButton();
		this.setSlot(nav + 5, budgetItem(have, max, next));

		ClaimsMod.Owner o = ClaimsMod.owner(this.player.getUUID());
		GuiElementBuilder trust = button(Items.PLAYER_HEAD, "Trusted players (" + o.trusted.size() + ")", ChatFormatting.AQUA,
				"Friends who may build in", "all your land.");
		int listed = 0;
		for (String name : o.trusted.values()) {
			if (listed++ == 4) {
				trust.addLoreLine(Ui.text("…and " + (o.trusted.size() - 4) + " more", ChatFormatting.DARK_GRAY));
				break;
			}
			trust.addLoreLine(Ui.text("• " + name, ChatFormatting.WHITE));
		}
		trust.addLoreLine(Ui.kv("Doors for visitors: ", o.doors ? "on" : "off", o.doors ? ChatFormatting.GREEN : ChatFormatting.RED));
		actions(trust, "Click: manage");
		this.setSlot(nav + 6, trust.setCallback((i, t, a, g) -> {
			Ui.click(this.player);
			TrustGui.open(this.player);
		}));

		GuiElementBuilder show = button(Items.SPYGLASS, "Show borders", ChatFormatting.YELLOW,
				"Makes the claims around you", "glow in the world for " + ClaimsMod.SHOW_TICKS / 20 + " seconds:");
		show.addLoreLine(Ui.text("■ yours", ChatFormatting.GREEN));
		show.addLoreLine(Ui.text("■ trusted", ChatFormatting.AQUA));
		show.addLoreLine(Ui.text("■ someone else's", ChatFormatting.RED));
		actions(show, "Click: show (closes the menu)");
		this.setSlot(nav + 7, show.setCallback((i, t, a, g) -> {
			Ui.click(this.player);
			this.close();
			ClaimsMod.showNearby(this.player);
		}));

		// ? Help with a colored legend (the toolkit's help() only does gray lines)
		GuiElementBuilder help = new GuiElementBuilder(Items.BOOK).setName(Ui.text("? Help", ChatFormatting.YELLOW)).hideDefaultTooltip();
		help.addLoreLine(Ui.text("Each square is one chunk.", ChatFormatting.GRAY));
		help.addLoreLine(Ui.text("North is up. Your head is you.", ChatFormatting.GRAY));
		help.addLoreLine(Component.empty());
		help.addLoreLine(Ui.text("■ Your land", ChatFormatting.GREEN));
		help.addLoreLine(Ui.text("■ Trusted: you can build there", ChatFormatting.AQUA));
		help.addLoreLine(Ui.text("■ Someone else's land", ChatFormatting.RED));
		help.addLoreLine(Ui.text("■ Grass, sand, water…: free land", ChatFormatting.WHITE));
		help.addLoreLine(Component.empty());
		help.addLoreLine(Ui.text("Click free land to claim it.", ChatFormatting.GRAY));
		help.addLoreLine(Ui.text("Shift-click your land to release.", ChatFormatting.GRAY));
		help.addLoreLine(Ui.text("Quick: stand in a chunk, /claim", ChatFormatting.GRAY));
		this.setSlot(nav + 8, help);
	}

	private GuiElementBuilder budgetItem(int have, int max, List<String> next) {
		boolean full = have >= max;
		GuiElementBuilder b = button(Items.EMERALD, "Budget: " + have + " / " + max + " chunks", full ? ChatFormatting.GOLD : ChatFormatting.GREEN,
				ClaimsMod.budgetText(this.player));
		for (String l : ClaimsMod.growthLines()) b.addLoreLine(Ui.text(l, ChatFormatting.GRAY));
		b.addLoreLine(Component.empty());
		int hours = ClaimsMod.hours(this.player);
		b.addLoreLine(Ui.kv("Start: ", "+" + ClaimsMod.START.get(), ChatFormatting.WHITE));
		b.addLoreLine(Ui.kv("Playtime (" + hoursText(hours) + "): ", "+" + hours / ClaimsMod.HOURS.get(), ChatFormatting.WHITE));
		if (Services.progress().isPresent()) {
			int points = ClaimsMod.points(this.player);
			b.addLoreLine(Ui.kv("Achievements (" + points + " points): ", "+" + points / ClaimsMod.POINTS.get(), ChatFormatting.WHITE));
		}
		b.addLoreLine(Component.empty());
		if (full) b.addLoreLine(Ui.text("Your budget is full.", ChatFormatting.GOLD));
		for (String l : next) b.addLoreLine(Ui.text(l, full ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
		return b;
	}

	/** "1 hour" / "2 hours". */
	private static String hoursText(int n) {
		return n + (n == 1 ? " hour" : " hours");
	}

	/** A player head with that player's skin. */
	static GuiElementBuilder head(GameProfile profile) {
		GuiElementBuilder b = new GuiElementBuilder(Items.PLAYER_HEAD);
		// sgui ignores a profile with a name and id but no skin data (offline mode): fall back to the UUID
		return profile.properties().isEmpty() ? b.setProfile(profile.id()) : b.setProfile(profile);
	}

	// ---------------------------------------------------------------- trusted players

	/** Claims · Trusted players: who may build in all your land, and whether visitors may use doors. */
	static final class TrustGui extends Menu {
		private static final int PER_PAGE = 14;
		private int page;

		private record Entry(UUID id, String name, ServerPlayer online, boolean trusted) {
		}

		TrustGui(ServerPlayer player) {
			super(player, 6, "Claims · Trusted players");
			build();
		}

		static void open(ServerPlayer player) {
			new TrustGui(player).open();
		}

		/** Online players and offline trusted players: trusted first, then online, then by name. */
		private List<Entry> entries() {
			ClaimsMod.Owner o = ClaimsMod.owner(this.player.getUUID());
			var players = this.player.level().getServer().getPlayerList();
			List<Entry> out = new ArrayList<>();
			for (ServerPlayer other : players.getPlayers()) {
				if (other == this.player) continue;
				out.add(new Entry(other.getUUID(), other.getName().getString(), other, o.trusted.containsKey(other.getUUID())));
			}
			for (Map.Entry<UUID, String> e : o.trusted.entrySet()) {
				if (players.getPlayer(e.getKey()) == null) out.add(new Entry(e.getKey(), e.getValue(), null, true));
			}
			out.sort(Comparator.comparing((Entry e) -> !e.trusted()).thenComparing(e -> e.online() == null)
					.thenComparing(e -> e.name().toLowerCase(Locale.ROOT)));
			return out;
		}

		private void build() {
			for (int i = 0; i < this.rows * 9; i++) this.clearSlot(i);
			ClaimsMod.Owner o = ClaimsMod.owner(this.player.getUUID());
			this.setSlot(4, button(Items.NAME_TAG, "Trusted players (" + o.trusted.size() + ")", ChatFormatting.AQUA,
					"They can build, open chests", "and touch animals in all", "your land. Click a head below."));

			List<Entry> all = entries();
			int pageCount = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
			this.page = Math.max(0, Math.min(this.page, pageCount - 1));
			List<Entry> shown = all.subList(this.page * PER_PAGE, Math.min(all.size(), (this.page + 1) * PER_PAGE));
			int[] top = row(1, Math.min(7, shown.size()));
			int[] bottom = row(2, Math.max(0, shown.size() - 7));
			for (int i = 0; i < shown.size(); i++) this.setSlot(i < 7 ? top[i] : bottom[i - 7], entry(shown.get(i)));
			if (all.isEmpty()) {
				this.setSlot(13, button(Items.PAPER, "Nobody to trust yet", ChatFormatting.GRAY,
						"Friends show up here when", "they're online. Click their", "head to let them build."));
			}

			// Doors for visitors: icon with its lamp below, like the settings screens
			boolean on = o.doors;
			GuiElementBuilder door = button(Items.OAK_DOOR, "Doors for visitors", ChatFormatting.WHITE,
					"Others may use doors, trapdoors,", "gates, buttons, levers and bells", "in your land. Nothing else.");
			door.addLoreLine(Component.empty());
			door.addLoreLine(Ui.text(on ? "✔ On" : "✘ Off", on ? ChatFormatting.GREEN : ChatFormatting.RED));
			action(door, "Click: turn " + (on ? "off" : "on"));
			if (on) door.glow();
			this.setSlot(31, door.setCallback((i, t, a, g) -> flipDoors()));
			this.setSlot(40, lamp(on).setCallback((i, t, a, g) -> flipDoors()));

			back("the map", () -> MapGui.open(this.player));
			pages(this.page, pageCount, p -> {
				this.page = p;
				build();
			});
			closeButton();
			help("Trusted players can build, open", "chests and touch animals in", "all your land.", "",
					"Friends must be online to be", "trusted: /claim trust <name>");
			finish();
		}

		private GuiElementBuilder entry(Entry e) {
			GuiElementBuilder b = e.online() != null ? head(e.online().getGameProfile()) : new GuiElementBuilder(Items.PLAYER_HEAD).setProfile(e.id());
			b.setName(title(e.name() + (e.online() == null ? " (offline)" : ""), e.trusted() ? ChatFormatting.GREEN : ChatFormatting.WHITE))
					.hideDefaultTooltip();
			if (e.trusted()) {
				b.addLoreLine(Ui.text("✔ Trusted: can build in your land", ChatFormatting.GREEN));
				if (e.online() == null) {
					b.addLoreLine(Ui.text("To trust them again later,", ChatFormatting.DARK_GRAY));
					b.addLoreLine(Ui.text("they have to be online.", ChatFormatting.DARK_GRAY));
				}
				actions(b, "Shift-click: remove trust");
				b.setCallback((i, t, a, g) -> {
					if (!t.shift) return;
					Ui.click(this.player);
					ClaimsMod.trust(this.player, e.id(), e.name(), false);
					build();
				});
			} else {
				b.addLoreLine(Ui.text("Not trusted", ChatFormatting.GRAY));
				actions(b, "Click: trust");
				b.setCallback((i, t, a, g) -> {
					if (t == ClickType.MOUSE_DOUBLE_CLICK) return;
					Ui.click(this.player);
					ClaimsMod.trust(this.player, e.id(), e.name(), true);
					build();
				});
			}
			return b;
		}

		private void flipDoors() {
			ClaimsMod.Owner o = ClaimsMod.owner(this.player.getUUID());
			o.doors = !o.doors;
			ClaimsMod.STORE.markDirty();
			Ui.sound(this.player, SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, o.doors ? 1.4f : 0.7f);
			build();
		}
	}
}
