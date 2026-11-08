package nl.libreria.info;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;
import nl.libreria.core.Libreria;
import nl.libreria.core.ServerSetting;

/**
 * Every villager gets a name of its own (Alden Thorne, Mirabel Hayward…), and no two villagers share one. The name
 * shows when you look at the villager and on its trading screen. Rename one with a name tag and that name wins, as
 * with any other mob. When a named villager dies, its name is free again.
 */
final class VillagerNames {
	static final ServerSetting.Toggle ENABLED = InfoMod.MODULE.serverToggle("villager_names", "Villager names", Items.NAME_TAG, true,
			"Every villager gets a name of its own;", "no two share one. Players can still", "rename them with a name tag.");
	/** Marks villagers that got their name from us (so we never rename them again). */
	static final String TAG = "libreria_named";

	private VillagerNames() {
	}

	static void init() {
		// Both run inside chunk loading and combat: a naming hiccup is logged and the villager stays nameless, it
		// never takes the server down
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!InfoMod.MODULE.on()) return;
			if (!(entity instanceof Villager v)) return;
			try {
				if (!ENABLED.get() || v.hasCustomName() || v.entityTags().contains(TAG)) return;
				name(v);
			} catch (RuntimeException e) {
				Libreria.LOG.warn("Could not name villager {}", v.getUUID(), e);
			}
		});
		// Villagers in chunks that are already loaded when an admin turns names on get theirs right away (the rest
		// as their chunk loads)
		ENABLED.onChange(() -> {
			var server = Libreria.server();
			if (server == null || !ENABLED.get()) return;
			for (ServerLevel level : server.getAllLevels())
				for (Entity e : level.getAllEntities())
					if (e instanceof Villager v && !v.hasCustomName() && !v.entityTags().contains(TAG)) {
						try {
							name(v);
						} catch (RuntimeException ex) {
							Libreria.LOG.warn("Could not name villager {}", v.getUUID(), ex);
						}
					}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!InfoMod.MODULE.on()) return;
			if (!(entity instanceof Villager v)) return;
			try {
				if (v.entityTags().contains(TAG)) Names.releaseVillager(v.getUUID());
			} catch (RuntimeException e) {
				Libreria.LOG.warn("Could not free the name of villager {}", v.getUUID(), e);
			}
		});
	}

	static void name(Villager v) {
		v.setCustomName(Component.literal(Names.villager(v.getUUID())));
		v.addTag(TAG);
	}
}
