package nl.kistdb;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Geluid alleen voor één speler, en blokken op ID (los van constante-namen). */
public final class Sounds {
	private Sounds() {
	}

	public static void play(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		play(player, Holder.direct(sound), volume, pitch);
	}

	public static void play(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(sound, SoundSource.MASTER,
				player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
	}

	public static BlockState block(String id) {
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id));
		return block.defaultBlockState();
	}
}
