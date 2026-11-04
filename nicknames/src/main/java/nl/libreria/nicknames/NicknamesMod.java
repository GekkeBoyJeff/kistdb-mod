package nl.libreria.nicknames;

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
import nl.libreria.core.Module;
import nl.libreria.core.Nameplate;
import nl.libreria.core.PlayerStore;
import nl.libreria.core.Services;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * NicknamesMod – een Libreria-module voor nicknames. Je nickname staat boven je hoofd, in de tablijst en in de chat; je echte
 * naam staat in de hover. Kleuren ontgrendel je met prestaties: hoe zeldzamer je beste prestatie, hoe meer kleuren.
 */
public final class NicknamesMod implements ModInitializer {
	public static final Module MODULE = Module.register("nicknames", "Nicknames", Items.NAME_TAG,
			"A nickname above your head, in the", "tab list and in chat. Unlock colors", "with achievements.")
			.commands("/nick – choose a nickname", "/nick <name> – set it right away", "/nick reset – use your real name");

	/** Kleur + de zeldzaamheid (0 = gewoon … 5 = mythisch) die je nodig hebt. */
	public record Color(String key, String label, @Nullable ChatFormatting format, int rarity, String dye) {
	}

	public static final List<Color> COLORS = List.of(
			new Color("white", "White", ChatFormatting.WHITE, 0, "white_dye"),
			new Color("gray", "Gray", ChatFormatting.GRAY, 0, "light_gray_dye"),
			new Color("green", "Green", ChatFormatting.GREEN, 1, "lime_dye"),
			new Color("dark_green", "Dark Green", ChatFormatting.DARK_GREEN, 1, "green_dye"),
			new Color("yellow", "Yellow", ChatFormatting.YELLOW, 1, "yellow_dye"),
			new Color("aqua", "Light Blue", ChatFormatting.AQUA, 2, "light_blue_dye"),
			new Color("dark_aqua", "Turquoise", ChatFormatting.DARK_AQUA, 2, "cyan_dye"),
			new Color("blue", "Blue", ChatFormatting.BLUE, 2, "blue_dye"),
			new Color("light_purple", "Pink", ChatFormatting.LIGHT_PURPLE, 3, "magenta_dye"),
			new Color("dark_purple", "Purple", ChatFormatting.DARK_PURPLE, 3, "purple_dye"),
			new Color("gold", "Gold", ChatFormatting.GOLD, 4, "orange_dye"),
			new Color("red", "Red", ChatFormatting.RED, 4, "red_dye"),
			new Color("dark_red", "Dark Red", ChatFormatting.DARK_RED, 4, "brown_dye"),
			new Color("rainbow", "Rainbow", null, 5, "prismarine_crystals"));
	/** Vetgedrukt kan vanaf episch. */
	public static final int BOLD_RARITY = 3;

	private static final ChatFormatting[] RAINBOW = {ChatFormatting.RED, ChatFormatting.GOLD, ChatFormatting.YELLOW, ChatFormatting.GREEN,
			ChatFormatting.AQUA, ChatFormatting.BLUE, ChatFormatting.LIGHT_PURPLE};

	public static final class Data {
		public String nick = "";
		public String color = "white";
		public boolean bold;
	}

	public static final PlayerStore<Data> STORE = PlayerStore.create("libreria/nicknames", Data.class, Data::new, d -> {
		if (d.nick == null) d.nick = "";
		if (d.color == null) d.color = "white";
	});

	@Override
	public void onInitialize() {
		MODULE.hubEntry(Items.NAME_TAG, "Nickname", ChatFormatting.LIGHT_PURPLE, NickGui::open,
				"Pick a nickname and a color.", "More colors with rarer achievements.");
		Nameplate.setName(NicknamesMod::plateName);

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
				Commands.literal("nick").executes(ctx -> {
					NickGui.open(ctx.getSource().getPlayerOrException());
					return 1;
				}).then(Commands.literal("reset").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					set(p, "");
					return 1;
				})).then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					set(p, StringArgumentType.getString(ctx, "name"));
					return 1;
				}))));

		NicknamesTests.register();
	}

	// ---------------------------------------------------------------- regels

	/** Beste zeldzaamheid van de speler; zonder CollectorMod is alles vrij. */
	public static int unlocked(ServerPlayer p) {
		return Services.progress().map(pr -> Math.max(0, pr.bestRarity(p))).orElse(5);
	}

	/** @return null als de naam mag, anders de reden */
	public static @Nullable String validate(ServerPlayer p, String nick) {
		String n = nick.strip();
		if (n.length() < 2) return "At least 2 characters.";
		if (n.length() > 16) return "At most 16 characters.";
		if (!n.matches("[\\p{L}\\p{N} _.\\-']+")) return "Only letters, numbers, spaces and _ . - '";
		String lower = n.toLowerCase(Locale.ROOT);
		for (ServerPlayer other : p.level().getServer().getPlayerList().getPlayers()) {
			if (other == p) continue;
			if (other.getName().getString().toLowerCase(Locale.ROOT).equals(lower)) return "That's another player's name.";
			Data od = STORE.get(other);
			if (od.nick.toLowerCase(Locale.ROOT).equals(lower)) return "That nickname is already taken.";
		}
		return null;
	}

	static void set(ServerPlayer p, String nick) {
		Data d = STORE.get(p);
		if (nick.isBlank()) {
			d.nick = "";
			STORE.markDirty(p);
			p.sendSystemMessage(Ui.prefix("Nicknames").append(Ui.text("You're back to your regular name.", ChatFormatting.GRAY)));
			refresh(p);
			return;
		}
		String why = validate(p, nick);
		if (why != null) {
			p.sendSystemMessage(Ui.prefix("Nicknames").append(Ui.text(why, ChatFormatting.RED)));
			return;
		}
		d.nick = nick.strip();
		STORE.markDirty(p);
		p.sendSystemMessage(Ui.prefix("Nicknames").append(Ui.text("You're now known as ", ChatFormatting.GRAY)).append(styled(p)));
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
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Real name: " + real).withStyle(ChatFormatting.GRAY)))
				.withClickEvent(new ClickEvent.SuggestCommand("/tell " + real + " "))
				.withInsertion(real));
	}

	public static @Nullable Component tabName(ServerPlayer p) {
		MutableComponent nick = styled(p);
		if (nick == null) return null;
		return nick.append(Component.literal(" (" + p.getName().getString() + ")").withStyle(ChatFormatting.DARK_GRAY));
	}
}
