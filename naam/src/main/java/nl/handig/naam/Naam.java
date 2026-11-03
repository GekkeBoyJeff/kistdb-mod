package nl.handig.naam;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import nl.handig.kern.Module;
import nl.handig.kern.Nameplate;
import nl.handig.kern.PlayerStore;
import nl.handig.kern.Services;
import nl.handig.kern.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * Naam – een Handig-module voor nicknames. Je nickname staat boven je hoofd, in de tablijst en in de chat; je echte
 * naam staat in de hover. Kleuren ontgrendel je met prestaties: hoe zeldzamer je beste prestatie, hoe meer kleuren.
 */
public final class Naam implements ModInitializer {
	public static final Module MODULE = Module.register("naam", "Naam", Items.NAME_TAG,
			"Een nickname boven je hoofd, in de", "tablijst en in de chat. Kleuren", "ontgrendel je met prestaties.");

	/** Kleur + de zeldzaamheid (0 = gewoon … 5 = mythisch) die je nodig hebt. */
	public record Color(String key, String label, @Nullable ChatFormatting format, int rarity, String dye) {
	}

	public static final List<Color> COLORS = List.of(
			new Color("white", "Wit", ChatFormatting.WHITE, 0, "white_dye"),
			new Color("gray", "Grijs", ChatFormatting.GRAY, 0, "light_gray_dye"),
			new Color("green", "Groen", ChatFormatting.GREEN, 1, "lime_dye"),
			new Color("dark_green", "Donkergroen", ChatFormatting.DARK_GREEN, 1, "green_dye"),
			new Color("yellow", "Geel", ChatFormatting.YELLOW, 1, "yellow_dye"),
			new Color("aqua", "Lichtblauw", ChatFormatting.AQUA, 2, "light_blue_dye"),
			new Color("dark_aqua", "Turkoois", ChatFormatting.DARK_AQUA, 2, "cyan_dye"),
			new Color("blue", "Blauw", ChatFormatting.BLUE, 2, "blue_dye"),
			new Color("light_purple", "Roze", ChatFormatting.LIGHT_PURPLE, 3, "magenta_dye"),
			new Color("dark_purple", "Paars", ChatFormatting.DARK_PURPLE, 3, "purple_dye"),
			new Color("gold", "Goud", ChatFormatting.GOLD, 4, "orange_dye"),
			new Color("red", "Rood", ChatFormatting.RED, 4, "red_dye"),
			new Color("dark_red", "Donkerrood", ChatFormatting.DARK_RED, 4, "brown_dye"),
			new Color("rainbow", "Regenboog", null, 5, "prismarine_crystals"));
	/** Vetgedrukt kan vanaf episch. */
	public static final int BOLD_RARITY = 3;

	private static final ChatFormatting[] RAINBOW = {ChatFormatting.RED, ChatFormatting.GOLD, ChatFormatting.YELLOW, ChatFormatting.GREEN,
			ChatFormatting.AQUA, ChatFormatting.BLUE, ChatFormatting.LIGHT_PURPLE};

	public static final class Data {
		public String nick = "";
		public String color = "white";
		public boolean bold;
	}

	public static final PlayerStore<Data> STORE = PlayerStore.create("handig/naam", Data.class, Data::new, d -> {
		if (d.nick == null) d.nick = "";
		if (d.color == null) d.color = "white";
	});

	@Override
	public void onInitialize() {
		MODULE.hubEntry(Items.NAME_TAG, "Nickname", ChatFormatting.LIGHT_PURPLE, NickGui::open,
				"Kies een nickname en een kleur.", "Meer kleuren met zeldzamere prestaties.");
		Nameplate.setName(Naam::plateName);

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
				Commands.literal("nick").executes(ctx -> {
					NickGui.open(ctx.getSource().getPlayerOrException());
					return 1;
				}).then(Commands.literal("reset").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					set(p, "");
					return 1;
				})).then(Commands.argument("naam", StringArgumentType.greedyString()).executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					set(p, StringArgumentType.getString(ctx, "naam"));
					return 1;
				}))));

		NaamTests.register();
	}

	// ---------------------------------------------------------------- regels

	/** Beste zeldzaamheid van de speler; zonder Verzamelaar is alles vrij. */
	public static int unlocked(ServerPlayer p) {
		return Services.progress().map(pr -> pr.bestRarity(p)).orElse(5);
	}

	/** @return null als de naam mag, anders de reden */
	public static @Nullable String validate(ServerPlayer p, String nick) {
		String n = nick.strip();
		if (n.length() < 2) return "Minstens 2 tekens.";
		if (n.length() > 16) return "Hooguit 16 tekens.";
		if (!n.matches("[\\p{L}\\p{N} _.\\-']+")) return "Alleen letters, cijfers, spaties en _ . - '";
		String lower = n.toLowerCase(Locale.ROOT);
		for (ServerPlayer other : p.level().getServer().getPlayerList().getPlayers()) {
			if (other == p) continue;
			if (other.getName().getString().toLowerCase(Locale.ROOT).equals(lower)) return "Dat is de naam van een andere speler.";
			Data od = STORE.get(other);
			if (od.nick.toLowerCase(Locale.ROOT).equals(lower)) return "Die nickname is al in gebruik.";
		}
		return null;
	}

	static void set(ServerPlayer p, String nick) {
		Data d = STORE.get(p);
		if (nick.isBlank()) {
			d.nick = "";
			STORE.markDirty(p);
			p.sendSystemMessage(Ui.prefix("Naam").append(Ui.text("Je gebruikt weer je gewone naam.", ChatFormatting.GRAY)));
			refresh(p);
			return;
		}
		String why = validate(p, nick);
		if (why != null) {
			p.sendSystemMessage(Ui.prefix("Naam").append(Ui.text(why, ChatFormatting.RED)));
			return;
		}
		d.nick = nick.strip();
		STORE.markDirty(p);
		p.sendSystemMessage(Ui.prefix("Naam").append(Ui.text("Je heet nu ", ChatFormatting.GRAY)).append(styled(p)));
		refresh(p);
	}

	static void setColor(ServerPlayer p, Color c) {
		if (c.rarity() > unlocked(p)) return;
		STORE.get(p).color = c.key();
		STORE.markDirty(p);
		refresh(p);
	}

	static void setBold(ServerPlayer p, boolean bold) {
		if (bold && unlocked(p) < BOLD_RARITY) return;
		STORE.get(p).bold = bold;
		STORE.markDirty(p);
		refresh(p);
	}

	/** Tablijst bij iedereen bijwerken (naamplaatje en chat volgen vanzelf). */
	static void refresh(ServerPlayer p) {
		p.level().getServer().getPlayerList().broadcastAll(
				new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, p));
	}

	// ---------------------------------------------------------------- tonen

	static Color colorOf(ServerPlayer p, Data d) {
		for (Color c : COLORS) {
			// Kleur die je (niet meer) mag gebruiken valt terug op wit
			if (c.key().equals(d.color) && c.rarity() <= unlocked(p)) return c;
		}
		return COLORS.get(0);
	}

	/** Alleen de opgemaakte nickname (of null zonder nickname). */
	public static @Nullable MutableComponent styled(ServerPlayer p) {
		Data d = STORE.get(p);
		if (d.nick.isEmpty()) return null;
		return paint(d.nick, colorOf(p, d), d.bold && unlocked(p) >= BOLD_RARITY);
	}

	static MutableComponent paint(String text, Color c, boolean bold) {
		MutableComponent out;
		if (c.format() == null) {
			out = Component.empty();
			int i = 0;
			for (int cp : text.codePoints().toArray()) {
				String ch = new String(Character.toChars(cp));
				out.append(Component.literal(ch).withStyle(RAINBOW[i++ % RAINBOW.length]));
			}
		} else {
			out = Component.literal(text).withStyle(c.format());
		}
		if (bold) out.withStyle(ChatFormatting.BOLD);
		return out;
	}

	private static @Nullable Component plateName(ServerPlayer p) {
		return styled(p);
	}

	/** Voor chat en andere berichten: nickname met je echte naam in de hover en klik = privébericht. */
	public static @Nullable Component chatName(ServerPlayer p) {
		MutableComponent nick = styled(p);
		if (nick == null) return null;
		String real = p.getName().getString();
		return Component.empty().append(nick).withStyle(s -> s
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Echte naam: " + real).withStyle(ChatFormatting.GRAY)))
				.withClickEvent(new ClickEvent.SuggestCommand("/tell " + real + " "))
				.withInsertion(real));
	}

	public static @Nullable Component tabName(ServerPlayer p) {
		MutableComponent nick = styled(p);
		if (nick == null) return null;
		return nick.append(Component.literal(" (" + p.getName().getString() + ")").withStyle(ChatFormatting.DARK_GRAY));
	}
}
