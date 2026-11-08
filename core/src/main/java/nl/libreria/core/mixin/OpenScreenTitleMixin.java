package nl.libreria.core.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Minecraft's own screens are dark in the Libreria pack, and the game draws their titles in dark grey: the title the
 * server sends ("Chest", "Furnace", a chest's own name) gets a light colour, so it reads. Titles that already have a
 * colour or several parts (Libreria's own windows) are left as they are.
 */
@Mixin(ServerPlayer.class)
public abstract class OpenScreenTitleMixin {
	private static final int LIGHT = 0xE4E7EE;

	@ModifyArg(method = "openMenu", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/game/ClientboundOpenScreenPacket;<init>(ILnet/minecraft/world/inventory/MenuType;Lnet/minecraft/network/chat/Component;)V"),
			index = 2)
	private Component libreria$lightTitle(Component title) {
		if (title == null || !title.getSiblings().isEmpty() || title.getStyle().getColor() != null) return title;
		if (!(title.getContents() instanceof TranslatableContents) && !(title.getContents() instanceof PlainTextContents)) return title;
		return Component.empty().append(title).withStyle(s -> s.withColor(LIGHT));
	}
}
