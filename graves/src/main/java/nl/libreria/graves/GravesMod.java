package nl.libreria.graves;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Guide;
import nl.libreria.core.Hud;
import nl.libreria.core.Domain;
import nl.libreria.core.Notify;
import nl.libreria.core.Say;
import nl.libreria.core.Module;
import nl.libreria.core.Services;
import nl.libreria.core.Setting;
import nl.libreria.core.Ui;
import nl.libreria.core.WorldStore;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * GravesMod – een Libreria-module. Ga je dood, dan komen je spullen in een graf op je sterfplek in plaats van op de grond.
 * Het graf is virtueel (een zwevende schedel met naambordje): niets in de wereld gaat kapot, explosies en lava doen
 * niets, en alleen jij kunt het openen. Je items komen terug in precies dezelfde vakken.
 * Per speler aan te zetten; standaard uit.
 */
public final class GravesMod implements ModInitializer {
	public static final Module MODULE = Module.register("graves", "Graves", Items.SKELETON_SKULL,
			"Your items go into a grave when you die", "instead of dropping. Off by default.").in(Domain.PLACES, "When you die");
	public static final Setting.Toggle ENABLED = MODULE.toggle("aan", "Keep your items in a grave", false, Items.SKELETON_SKULL,
			"Your items stay safe in a grave where", "you die. Only you can open it, and",
			"everything returns to the same slots.", "XP still drops as usual.");

	// ---------------------------------------------------------------- opslag

	public static final class Grave {
		public String id = "";
		public UUID owner = new UUID(0, 0);
		public String ownerName = "";
		public String dim = "minecraft:overworld";
		public double x, y, z;
		public long created;
		public List<Slot> items = new ArrayList<>();

		Vec3 pos() {
			return new Vec3(x, y, z);
		}

		int count() {
			int n = 0;
			for (Slot s : items) n += s.count;
			return n;
		}
	}

	public static final class Slot {
		public int slot;
		public int count;
		public JsonElement item;
	}

	public static final class Data {
		public Map<String, Grave> graves = new HashMap<>();
	}

	static final nl.libreria.core.Tips.Tip TIP_GRAVE = nl.libreria.core.Tips.register("grave", Domain.PLACES, Items.SKELETON_SKULL,
			"Your grave", "Your items are safe in a grave where you died.", "Right-click its skull: everything back in the same slots.");

	static final WorldStore<Data> STORE = WorldStore.create("libreria/graves.json", Data.class, Data::new);

	/** Zichtbare graven (alleen als de chunk geladen is). */
	private static final Map<String, ElementHolder> SHOWN = new HashMap<>();
	private static @Nullable MinecraftServer server;

	@Override
	public void onInitialize() {
		GravePlaces.register();
		MODULE.hubEntry(Domain.PLACES, 60, Items.SKELETON_SKULL, "Death & graves", GravesMod::listGraves,
				"Where you last died and your graves; click one to be shown the way. Graves on or off is there too.");
		MODULE.commands(
				"/grave – your graves",
				"/grave beam – show the way to the newest");
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			SHOWN.values().forEach(ElementHolder::destroy);
			SHOWN.clear();
			server = null;
		});
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (s.getTickCount() % 20 == 0) refreshDisplays(s);
		});

		// Na respawnen: waar ligt je graf?
		Services.addDeathNote(GravesMod::deathNote);
		Services.addDeathAction(p -> {
			Grave g = newestOf(p);
			if (g == null || System.currentTimeMillis() - g.created > 60_000) return null;
			if (!g.dim.equals(p.level().dimension().identifier().toString())) return null;
			return new Services.DeathAction("Show the way to your grave", g.count() + (g.count() == 1 ? " item waits there" : " items wait there"),
					pl -> beam(pl, g));
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				Grave fresh = newestOf(newPlayer);
				if (fresh != null && System.currentTimeMillis() - fresh.created < 60_000) nl.libreria.core.Tips.once(newPlayer, TIP_GRAVE);
			}
			if (alive || FabricLoader.getInstance().isModLoaded("libreria_waypoints") || nl.libreria.core.DeathRecap.enabled(newPlayer)) return;
			Component note = deathNote(newPlayer);
			if (note != null) newPlayer.sendSystemMessage(note);
		});

		nl.libreria.core.gui.Markers.addSource(p -> {
			List<nl.libreria.core.gui.Markers.Marker> out = new ArrayList<>();
			String dim = p.level().dimension().identifier().toString();
			for (Grave g : STORE.get().graves.values()) {
				if (g.owner.equals(p.getUUID()) && g.dim.equals(dim)) {
					out.add(new nl.libreria.core.gui.Markers.Marker(g.pos(), "grave", 0xD0D0D0, "Your grave"));
				}
			}
			return out;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
				Commands.literal("grave").executes(ctx -> {
					listGraves(ctx.getSource().getPlayerOrException());
					return 1;
				}).then(Commands.literal("beam").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					Grave g = newestOf(p);
					if (g == null) Notify.info(p, Items.SKELETON_SKULL, "No grave", ENABLED.get(p)
							? "You don't have a grave right now."
							: "Graves are off: turn them on in Your graves.");
					else beam(p, g);
					return 1;
				}))));

		GravesTests.register();
	}

	// ---------------------------------------------------------------- doodgaan

	/** Aangeroepen door de mixin vlak voordat vanilla items laat vallen. @return true als alles in een graf ging */
	public static boolean intoGrave(ServerPlayer player, ServerLevel level) {
		if (!ENABLED.get(player) || level.getGameRules().get(GameRules.KEEP_INVENTORY)) return false;
		Inventory inv = player.getInventory();
		var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		List<Slot> slots = new ArrayList<>();
		List<Integer> taken = new ArrayList<>();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.isEmpty()) continue;
			// Vloek van verdwijning: verdwijnt, net als in vanilla
			if (EnchantmentHelper.has(st, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				inv.removeItemNoUpdate(i);
				continue;
			}
			var encoded = ItemStack.CODEC.encodeStart(ops, st).result();
			if (encoded.isEmpty()) continue; // laat vanilla dit ene item laten vallen
			Slot s = new Slot();
			s.slot = i;
			s.count = st.getCount();
			s.item = encoded.get();
			slots.add(s);
			taken.add(i);
		}
		if (slots.isEmpty()) return false;

		Grave g = new Grave();
		g.id = UUID.randomUUID().toString().substring(0, 8);
		g.owner = player.getUUID();
		g.ownerName = player.getName().getString();
		g.dim = level.dimension().identifier().toString();
		Vec3 at = safeSpot(level, player.position());
		g.x = at.x;
		g.y = at.y;
		g.z = at.z;
		g.created = System.currentTimeMillis();
		g.items = slots;
		for (int i : taken) inv.removeItemNoUpdate(i);
		// Alles wat overblijft (kon niet bewaard worden) laat vanilla straks gewoon vallen: daarom niet annuleren
		boolean all = true;
		for (int i = 0; i < inv.getContainerSize(); i++) if (!inv.getItem(i).isEmpty()) all = false;
		STORE.get().graves.put(g.id, g);
		STORE.saveNow();
		show(level, g);
		return all;
	}

	/** Boven de leegte of onder de wereld: een plek waar je wél kunt komen. */
	static Vec3 safeSpot(ServerLevel level, Vec3 pos) {
		double minY = level.getMinY() + 1;
		double y = Math.max(pos.y, minY);
		y = Math.min(y, level.getMaxY() - 1);
		if (pos.y < level.getMinY()) {
			// Gevallen in de leegte: op het hoogste blok in deze kolom, als dat er is
			int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(pos.x), (int) Math.floor(pos.z));
			if (top > level.getMinY()) y = top;
		}
		return new Vec3(Math.floor(pos.x) + 0.5, Math.floor(y), Math.floor(pos.z) + 0.5);
	}

	// ---------------------------------------------------------------- terugpakken

	/** Geeft de items terug (zelfde vakken waar mogelijk). @return aantal teruggegeven items */
	static int restore(ServerPlayer player, Grave g) {
		var ops = player.level().registryAccess().createSerializationContext(JsonOps.INSTANCE);
		Inventory inv = player.getInventory();
		int given = 0;
		List<ItemStack> leftovers = new ArrayList<>();
		for (Slot s : g.items) {
			ItemStack st = ItemStack.CODEC.parse(ops, s.item).result().orElse(ItemStack.EMPTY);
			if (st.isEmpty()) continue;
			given += st.getCount();
			if (s.slot >= 0 && s.slot < inv.getContainerSize() && inv.getItem(s.slot).isEmpty()) inv.setItem(s.slot, st);
			else leftovers.add(st);
		}
		for (ItemStack st : leftovers) {
			if (!inv.add(st) && !st.isEmpty()) {
				player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), st));
			}
		}
		player.containerMenu.broadcastChanges();
		return given;
	}

	static void open(ServerPlayer player, Grave g) {
		boolean owner = player.getUUID().equals(g.owner);
		if (!owner) {
			Hud.flash(player, Component.literal("☠ This is " + g.ownerName + "'s grave").withStyle(ChatFormatting.GRAY));
			return;
		}
		int n = restore(player, g);
		STORE.get().graves.remove(g.id);
		STORE.saveNow();
		hide(g.id);
		Ui.sound(player, SoundEvents.ARMOR_EQUIP_GENERIC, 1f, 1f);
		Hud.flash(player, Component.literal("✔ Got " + items(n) + " back from your grave")
				.withStyle(ChatFormatting.GREEN));
	}

	/** "1 item" / "5 items". */
	private static String items(int n) {
		return n + (n == 1 ? " item" : " items");
	}

	// ---------------------------------------------------------------- zichtbaar maken

	private static void refreshDisplays(MinecraftServer s) {
		Data data = STORE.get();
		// Weggehaalde graven en ongeladen chunks opruimen
		Iterator<Map.Entry<String, ElementHolder>> it = SHOWN.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, ElementHolder> e = it.next();
			if (!data.graves.containsKey(e.getKey()) || e.getValue().getAttachment() == null) {
				e.getValue().destroy();
				it.remove();
			}
		}
		for (Grave g : data.graves.values()) {
			if (SHOWN.containsKey(g.id)) continue;
			ServerLevel level = levelOf(s, g.dim);
			if (level != null && level.isLoaded(BlockPos.containing(g.pos()))) show(level, g);
		}
	}

	private static void show(ServerLevel level, Grave g) {
		hide(g.id);
		ElementHolder holder = new ElementHolder();
		ItemDisplayElement skull = new ItemDisplayElement(new ItemStack(Items.SKELETON_SKULL));
		skull.setScale(new Vector3f(0.7f));
		skull.setTranslation(new Vector3f(0, 0.35f, 0));
		skull.setBillboardMode(Display.BillboardConstraints.VERTICAL);
		holder.addElement(skull);

		TextDisplayElement label = new TextDisplayElement(Component.empty()
				.append(Component.literal("☠ " + g.ownerName).withStyle(ChatFormatting.RED))
				.append(Component.literal("\n" + items(g.count()) + " · right-click").withStyle(ChatFormatting.GRAY)));
		label.setTranslation(new Vector3f(0, 0.95f, 0));
		label.setBillboardMode(Display.BillboardConstraints.CENTER);
		label.setViewRange(0.4f);
		holder.addElement(label);

		String id = g.id;
		InteractionElement click = new InteractionElement(new VirtualElement.InteractionHandler() {
			@Override
			public void interact(ServerPlayer player, InteractionHand hand, Vec3 pos, boolean secondary) {
				if (hand == InteractionHand.MAIN_HAND) clicked(player, id);
			}

			@Override
			public void attack(ServerPlayer player) {
				clicked(player, id);
			}
		});
		click.setSize(0.7f, 0.75f);
		holder.addElement(click);

		ChunkAttachment.ofTicking(holder, level, g.pos());
		SHOWN.put(g.id, holder);
	}

	private static void clicked(ServerPlayer player, String id) {
		Grave g = STORE.get().graves.get(id);
		if (g != null) open(player, g);
	}

	private static void hide(String id) {
		ElementHolder h = SHOWN.remove(id);
		if (h != null) h.destroy();
	}

	// ---------------------------------------------------------------- berichten en straal

	static @Nullable Grave newestOf(ServerPlayer p) {
		Grave best = null;
		for (Grave g : STORE.get().graves.values()) {
			if (g.owner.equals(p.getUUID()) && (best == null || g.created > best.created)) best = g;
		}
		return best;
	}

	static @Nullable Component deathNote(ServerPlayer p) {
		Grave g = newestOf(p);
		if (g == null || System.currentTimeMillis() - g.created > 60_000) return null;
		return Say.line(Domain.PLACES).append(Ui.text("Your items are safe in your grave ", ChatFormatting.GRAY))
				.append(Say.run("Show the way", "/grave beam", "A beam to your grave"));
	}

	static void beam(ServerPlayer p, Grave g) {
		ServerLevel level = levelOf(p.level().getServer(), g.dim);
		if (level != p.level()) {
			Notify.warn(p, Items.SKELETON_SKULL, "In another dimension",
					"Your grave is in the " + GravePlaces.dimension(g.dim) + ". Go there first.");
			return;
		}
		Guide.toPoint(p, level, g.pos().add(0, 0.5, 0), new ItemStack(Items.SKELETON_SKULL), Component.literal("Your grave"), 0);
	}

	/** "Your graves" (menu): location, items and age of each grave; click one to beam to it. */
	static void listGraves(ServerPlayer p) {
		GravePlaces.open(p);
	}

	static @Nullable ServerLevel levelOf(@Nullable MinecraftServer s, String dim) {
		if (s == null) return null;
		Identifier id = Identifier.tryParse(dim);
		return id == null ? null : s.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}
}
