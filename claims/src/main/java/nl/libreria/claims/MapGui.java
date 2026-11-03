package nl.libreria.claims;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import nl.libreria.core.Ui;
import nl.libreria.core.gui.HubGui;

/**
 * Een kaart van 9×5 chunks rond je positie (noord = boven). Vrije chunks tonen het blok dat er bovenop ligt, zodat
 * het echt op een kaartje lijkt; groen = van jou, blauwgroen = je mag er bouwen, rood = van een ander.
 */
final class MapGui extends SimpleGui {
	private final int panX, panZ;

	private MapGui(ServerPlayer p, int panX, int panZ) {
		super(MenuType.GENERIC_9x6, p, false);
		this.panX = panX;
		this.panZ = panZ;
		build();
	}

	static void open(ServerPlayer p) {
		new MapGui(p, 0, 0).open();
	}

	private void reopen(int px, int pz) {
		new MapGui(this.getPlayer(), px, pz).open();
	}

	private void build() {
		ServerPlayer p = this.getPlayer();
		ServerLevel level = p.level();
		ChunkPos here = p.chunkPosition();
		int have = ClaimsMod.owned(p.getUUID()), max = ClaimsMod.budget(p);
		this.setTitle(Component.literal("Gebied – " + have + "/" + max + " chunks"));

		for (int row = 0; row < 5; row++) {
			for (int col = 0; col < 9; col++) {
				ChunkPos c = new ChunkPos(here.x() + panX + col - 4, here.z() + panZ + row - 2);
				this.setSlot(row * 9 + col, cell(p, level, c, c.equals(here), have, max));
			}
		}

		this.setSlot(45, Ui.button(Items.ARROW, "Terug naar Libreria", ChatFormatting.GRAY).setCallback((i, t, a, g) -> HubGui.open(p)));
		GuiElementBuilder budget = Ui.button(Items.EMERALD, "Budget: " + have + " / " + max + " chunks", ChatFormatting.GREEN)
				.addLoreLine(Ui.kv("Basis: ", "+" + ClaimsMod.BASE, ChatFormatting.WHITE))
				.addLoreLine(Ui.kv("Speeltijd (" + ClaimsMod.hours(p) + " uur): ", "+" + ClaimsMod.hours(p) / ClaimsMod.HOURS_PER_CHUNK, ChatFormatting.WHITE));
		nl.libreria.core.Services.progress().ifPresent(pr -> budget.addLoreLine(Ui.kv("Prestaties (" + ClaimsMod.points(p) + " punten): ",
				"+" + ClaimsMod.points(p) / ClaimsMod.POINTS_PER_CHUNK, ChatFormatting.WHITE)));
		int nextHours = ClaimsMod.HOURS_PER_CHUNK - ClaimsMod.hours(p) % ClaimsMod.HOURS_PER_CHUNK;
		budget.addLoreLine(Component.empty())
				.addLoreLine(Ui.text("+1 chunk per " + ClaimsMod.HOURS_PER_CHUNK + " uur speeltijd"
						+ (nl.libreria.core.Services.progress().isPresent() ? " en per " + ClaimsMod.POINTS_PER_CHUNK + " prestatiepunten" : ""), ChatFormatting.GRAY))
				.addLoreLine(Ui.text("Volgende chunk over hooguit " + nextHours + " uur", ChatFormatting.DARK_GRAY));
		this.setSlot(46, budget);

		ClaimsMod.Owner o = ClaimsMod.owner(p.getUUID());
		this.setSlot(47, Ui.button(Items.PLAYER_HEAD, "Vertrouwde spelers (" + o.trusted.size() + ")", ChatFormatting.AQUA)
				.addLoreLine(Ui.text("Wie in al je chunks mag bouwen.", ChatFormatting.GRAY))
				.setCallback((i, t, a, g) -> {
					Ui.click(p);
					new TrustGui(p).open();
				}));
		this.setSlot(48, Ui.button(Items.OAK_DOOR, "Deuren voor bezoekers: " + (o.doors ? "aan" : "uit"), o.doors ? ChatFormatting.GREEN : ChatFormatting.RED)
				.addLoreLine(Ui.text("Mogen anderen deuren, luiken, hekken,", ChatFormatting.GRAY))
				.addLoreLine(Ui.text("knoppen en hendels in je gebied gebruiken?", ChatFormatting.GRAY))
				.setCallback((i, t, a, g) -> {
					o.doors = !o.doors;
					ClaimsMod.STORE.markDirty();
					Ui.click(p);
					reopen(panX, panZ);
				}));
		this.setSlot(49, Ui.button(Items.SPYGLASS, "Rand van deze chunk tonen", ChatFormatting.YELLOW)
				.setCallback((i, t, a, g) -> {
					ClaimsMod.showBorder(p, level, here, 0xFFFF55);
					this.close();
				}));
		this.setSlot(50, Ui.button(Items.ARROW, "◀ West", ChatFormatting.WHITE).setCallback((i, t, a, g) -> reopen(panX - 4, panZ)));
		this.setSlot(51, Ui.button(Items.ARROW, "▲ Noord", ChatFormatting.WHITE).setCallback((i, t, a, g) -> reopen(panX, panZ - 2)));
		this.setSlot(52, Ui.button(Items.ARROW, "▼ Zuid", ChatFormatting.WHITE).setCallback((i, t, a, g) -> reopen(panX, panZ + 2)));
		this.setSlot(53, Ui.button(Items.ARROW, "▶ Oost", ChatFormatting.WHITE).setCallback((i, t, a, g) -> reopen(panX + 4, panZ)));
	}

	private GuiElementBuilder cell(ServerPlayer p, ServerLevel level, ChunkPos c, boolean here, int have, int max) {
		ClaimsMod.Claim claim = ClaimsMod.claimAt(level, c);
		GuiElementBuilder b;
		if (claim == null) {
			b = new GuiElementBuilder(surface(level, c)).setName(Ui.text(here ? "◉ Hier sta je – vrij" : "Vrij", ChatFormatting.WHITE));
			b.addLoreLine(Ui.text("Chunk " + c.x() + ", " + c.z(), ChatFormatting.DARK_GRAY));
			b.addLoreLine(have < max ? Ui.text("Klik: claimen (" + (have + 1) + "/" + max + ")", ChatFormatting.GREEN)
					: Ui.text("Je budget is op", ChatFormatting.RED));
			b.setCallback((i, t, a, g) -> {
				if (ClaimsMod.claim(p, level, c)) reopen(panX, panZ);
			});
		} else if (claim.owner.equals(p.getUUID())) {
			b = new GuiElementBuilder(Ui.item("lime_concrete")).setName(Ui.text(here ? "◉ Hier sta je – jouw chunk" : "Jouw chunk", ChatFormatting.GREEN));
			b.addLoreLine(Ui.text("Chunk " + c.x() + ", " + c.z(), ChatFormatting.DARK_GRAY));
			b.addLoreLine(Ui.text("Shift-klik: vrijgeven", ChatFormatting.GOLD));
			b.setCallback((i, t, a, g) -> {
				if (t.shift && ClaimsMod.unclaim(p, level, c)) reopen(panX, panZ);
			});
		} else {
			boolean trusted = ClaimsMod.owner(claim.owner).trusted.containsKey(p.getUUID());
			b = new GuiElementBuilder(Ui.item(trusted ? "cyan_concrete" : "red_concrete"))
					.setName(Ui.text((here ? "◉ " : "") + "Gebied van " + claim.ownerName, trusted ? ChatFormatting.AQUA : ChatFormatting.RED));
			b.addLoreLine(Ui.text("Chunk " + c.x() + ", " + c.z(), ChatFormatting.DARK_GRAY));
			if (trusted) b.addLoreLine(Ui.text("Je mag hier bouwen", ChatFormatting.AQUA));
		}
		if (here) b.glow();
		return b.hideDefaultTooltip();
	}

	/** Het bovenste blok midden in de chunk, als icoon. */
	private static Item surface(ServerLevel level, ChunkPos c) {
		int x = c.getMiddleBlockX(), z = c.getMiddleBlockZ();
		if (!level.hasChunk(c.x(), c.z())) return Ui.item("light_gray_stained_glass_pane");
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		BlockState st = level.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
		if (st.getFluidState().is(Fluids.WATER) || st.getFluidState().is(Fluids.FLOWING_WATER)) return Ui.item("blue_stained_glass");
		if (st.getFluidState().is(Fluids.LAVA) || st.getFluidState().is(Fluids.FLOWING_LAVA)) return Ui.item("orange_stained_glass");
		Item item = st.getBlock().asItem();
		return item == Items.AIR ? Ui.item("light_gray_stained_glass_pane") : item;
	}

	/** Spelers die in al je chunks mogen bouwen. */
	private static final class TrustGui extends SimpleGui {
		TrustGui(ServerPlayer p) {
			super(MenuType.GENERIC_9x3, p, false);
			this.setTitle(Component.literal("Vertrouwde spelers"));
			ClaimsMod.Owner o = ClaimsMod.owner(p.getUUID());
			int slot = 0;
			for (ServerPlayer other : p.level().getServer().getPlayerList().getPlayers()) {
				if (other == p || slot >= 18) continue;
				boolean on = o.trusted.containsKey(other.getUUID());
				UUID id = other.getUUID();
				String name = other.getName().getString();
				GuiElementBuilder b = new GuiElementBuilder(Items.PLAYER_HEAD)
						.setName(Ui.text(name, on ? ChatFormatting.GREEN : ChatFormatting.WHITE))
						.addLoreLine(Ui.text(on ? "✔ Mag bouwen in je gebied – klik: intrekken" : "Klik: laten meebouwen", on ? ChatFormatting.GREEN : ChatFormatting.AQUA))
						.setCallback((i, t, a, g) -> {
							ClaimsMod.trust(p, id, name, !on);
							new TrustGui(p).open();
						});
				if (on) b.glow();
				this.setSlot(slot++, b);
			}
			// Vertrouwde spelers die nu niet online zijn
			for (Map.Entry<UUID, String> e : o.trusted.entrySet()) {
				if (slot >= 18) break;
				if (p.level().getServer().getPlayerList().getPlayer(e.getKey()) != null) continue;
				UUID id = e.getKey();
				String name = e.getValue();
				this.setSlot(slot++, new GuiElementBuilder(Items.SKELETON_SKULL).glow()
						.setName(Ui.text(name + " (offline)", ChatFormatting.GREEN))
						.addLoreLine(Ui.text("Klik: intrekken", ChatFormatting.GOLD))
						.setCallback((i, t, a, g) -> {
							ClaimsMod.trust(p, id, name, false);
							new TrustGui(p).open();
						}));
			}
			if (slot == 0) this.setSlot(4, Ui.button(Items.PAPER, "Niemand online om te vertrouwen", ChatFormatting.GRAY));
			this.setSlot(22, Ui.button(Items.ARROW, "Terug naar de kaart", ChatFormatting.GRAY).setCallback((i, t, a, g) -> MapGui.open(p)));
		}
	}
}
