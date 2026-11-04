package nl.libreria.collector.mixin;

import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerAdvancementManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Collector add its achievements to the server's advancement list. */
@Mixin(ServerAdvancementManager.class)
public interface ServerAdvancementManagerAccessor {
	@Accessor("advancements")
	Map<Identifier, AdvancementHolder> libreria$getAdvancements();

	@Mutable
	@Accessor("advancements")
	void libreria$setAdvancements(Map<Identifier, AdvancementHolder> advancements);
}
