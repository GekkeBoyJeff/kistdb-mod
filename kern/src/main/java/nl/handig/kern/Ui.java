package nl.handig.kern;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

/** Gedeelde opmaak, geluiden en kleine hulpjes, zodat alle modules er hetzelfde uitzien. */
public final class Ui {
	private Ui() {
	}

	/** Tekst zonder het standaard-cursief van item-namen en lore. */
	public static MutableComponent text(String s, ChatFormatting color) {
		return Component.literal(s).withStyle(st -> st.withItalic(false).withColor(color));
	}

	public static MutableComponent kv(String key, String value, ChatFormatting valueColor) {
		return text(key, ChatFormatting.GRAY).append(text(value, valueColor));
	}

	/** "[Handig] " of "[KistDB] " in goud, voor chatberichten. */
	public static MutableComponent prefix(String name) {
		return text("[" + name + "] ", ChatFormatting.GOLD);
	}

	public static GuiElementBuilder button(Item item, String name, ChatFormatting color) {
		return new GuiElementBuilder(item).setName(text(name, color));
	}

	public static String meters(double d) {
		return Math.round(d) + " m";
	}

	/** Gekleurde balk, bv. ▮▮▮▮▮▮▯▯▯▯ voor 60%. */
	public static MutableComponent bar(double fraction, int segments) {
		fraction = Math.max(0, Math.min(1, fraction));
		int full = (int) Math.round(fraction * segments);
		ChatFormatting color = fraction > 0.5 ? ChatFormatting.GREEN : fraction > 0.2 ? ChatFormatting.YELLOW : ChatFormatting.RED;
		return Component.literal("▮".repeat(full)).withStyle(color)
				.append(Component.literal("▯".repeat(segments - full)).withStyle(ChatFormatting.DARK_GRAY));
	}

	// ---- geluid (alleen voor die speler) ----

	public static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		sound(player, Holder.direct(sound), volume, pitch);
	}

	public static void sound(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(sound, SoundSource.MASTER,
				player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
	}

	public static void click(ServerPlayer player) {
		sound(player, SoundEvents.UI_BUTTON_CLICK, 0.4f, 1f);
	}

	/** Blok op ID (los van constante-namen die per versie kunnen wijzigen). */
	public static BlockState block(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)).defaultBlockState();
	}

	/** Item op ID; gekleurde varianten (rood bed, grijze kleurstof…) hebben in 26.x geen eigen constante meer. */
	public static net.minecraft.world.item.Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
	}
}
