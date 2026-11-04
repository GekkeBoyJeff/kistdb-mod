package nl.libreria.testclient;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;

/** CI only: always load the Libreria resource pack in the test client (a real player would accept it on join). */
public final class Init implements ModInitializer {
	@Override
	public void onInitialize() {
		PolymerResourcePackUtils.markAsRequired();
	}
}
