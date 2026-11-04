package nl.libreria.info;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import nl.libreria.core.Chronicle;
import nl.libreria.core.Guide;
import nl.libreria.core.Libreria;
import nl.libreria.core.ServerSetting;
import nl.libreria.core.Ui;
import nl.libreria.core.WorldStore;
import nl.libreria.core.dialog.Window;
import nl.libreria.core.gui.Markers;
import nl.libreria.core.gui.Toast;
import org.jetbrains.annotations.Nullable;

/**
 * Pets you don't lose. A wolf, cat, parrot, horse, donkey, mule or llama you tame gets a name of its own (no other pet
 * on the server has it; a name tag still wins). The Pets window shows where each one is or was last seen, with a beam
 * to it, and your land maps mark them. When one dies, you hear where and how, also if you were offline.
 */
final class Pets {
	static final ServerSetting.Toggle NAMES = InfoMod.MODULE.serverToggle("pet_names", "Pet names", Items.LEAD, true,
			"Tamed animals get a name of their own;", "no two pets share one. A name tag", "still wins.");

	/** A pet as last seen. */
	public static final class Pet {
		public UUID owner;
		public String ownerName = "";
		public String name = "";
		public String type = "";
		public String dim = "minecraft:overworld";
		public int x, y, z;
		public long seen;
		public boolean sitting;
	}

	/** A pet that died. */
	public static final class Lost {
		public UUID owner;
		public String name = "";
		public String type = "";
		public String dim = "minecraft:overworld";
		public int x, y, z;
		public long at;
		public String how = "";
	}

	public static final class Data {
		public Map<UUID, Pet> pets = new HashMap<>();
		public List<Lost> lost = new ArrayList<>();
		/** Owner → news for when they're back ("Biscuit died…"). */
		public Map<UUID, List<String>> news = new HashMap<>();
	}

	static final WorldStore<Data> STORE = WorldStore.create("libreria/pets.json", Data.class, Data::new);
	/** Marks pets that got their name from us (so a released name is only ever ours). */
	static final String TAG = "libreria_pet";
	private static final int PINK = 0xFFB0C8;

	private Pets() {
	}

	static Data data() {
		Data d = STORE.get();
		if (d.pets == null) d.pets = new HashMap<>();
		if (d.lost == null) d.lost = new ArrayList<>();
		if (d.news == null) d.news = new HashMap<>();
		return d;
	}

	static void init() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((e, level) -> safely(() -> {
			// Pets that were already tamed: written down (and named) quietly
			if (owner(e) != null) see(e, false);
		}));
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> safely(() -> {
			if (owner(e) != null && data().pets.containsKey(e.getUUID())) see(e, false);
		}));
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((e, source) -> safely(() -> died(e, source)));
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((h, s, server) -> safely(() -> news(h.player)));
		Markers.addSource(p -> {
			List<Markers.Marker> out = new ArrayList<>();
			String dim = p.level().dimension().identifier().toString();
			for (Map.Entry<UUID, Pet> e : data().pets.entrySet()) {
				Pet pet = e.getValue();
				if (p.getUUID().equals(pet.owner) && pet.dim.equals(dim)) {
					out.add(new Markers.Marker(new Vec3(pet.x + 0.5, pet.y, pet.z + 0.5), "paw", PINK, pet.name));
				}
			}
			return out;
		});
	}

	private static void safely(Runnable r) {
		try {
			r.run();
		} catch (RuntimeException ex) {
			Libreria.LOG.warn("Pets: something went wrong", ex);
		}
	}

	/** The owner of a tamed animal, or null if it isn't one. */
	static @Nullable UUID owner(Entity e) {
		if (!(e instanceof TamableAnimal) && !(e instanceof AbstractHorse)) return null;
		if (e instanceof TamableAnimal t && !t.isTame()) return null;
		if (e instanceof AbstractHorse h && !h.isTamed()) return null;
		var ref = ((OwnableEntity) e).getOwnerReference();
		return ref == null ? null : ref.getUUID();
	}

	/** Every second: newly tamed animals; every five seconds also where all pets are. */
	static void tick(MinecraftServer server, int tick) {
		boolean positions = tick % 100 < 20;
		Data d = data();
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity e : level.getAllEntities()) {
				if (!(e instanceof TamableAnimal) && !(e instanceof AbstractHorse)) continue;
				try {
					UUID owner = owner(e);
					if (owner == null) continue;
					boolean known = d.pets.containsKey(e.getUUID());
					if (!known) see(e, true);
					else if (positions) see(e, false);
				} catch (RuntimeException ex) {
					Libreria.LOG.warn("Pets: could not follow {}", e.getUUID(), ex);
				}
			}
		}
	}

	/** Writes down where a pet is; {@code justTamed}: say so to its owner and the chronicle. */
	static void see(Entity e, boolean justTamed) {
		UUID owner = owner(e);
		if (owner == null) return;
		Data d = data();
		if (!e.hasCustomName() && !e.entityTags().contains(TAG) && NAMES.get()) {
			e.setCustomName(Component.literal(Names.pet(e.getUUID())));
			e.addTag(TAG);
		}
		Pet pet = d.pets.computeIfAbsent(e.getUUID(), k -> new Pet());
		pet.owner = owner;
		ServerPlayer op = e.level().getServer().getPlayerList().getPlayer(owner);
		if (op != null) pet.ownerName = op.getName().getString();
		pet.name = e.hasCustomName() ? e.getCustomName().getString() : e.getType().getDescription().getString();
		pet.type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
		pet.dim = e.level().dimension().identifier().toString();
		BlockPos at = e.blockPosition();
		pet.x = at.getX();
		pet.y = at.getY();
		pet.z = at.getZ();
		pet.seen = System.currentTimeMillis();
		pet.sitting = e instanceof TamableAnimal t && t.isOrderedToSit();
		STORE.markDirty();
		if (justTamed && op != null) {
			String kind = e.getType().getDescription().getString();
			Toast.show(op, egg(pet.type), Toast.GOOD, Ui.text("You tamed " + article(kind) + " " + kind.toLowerCase(java.util.Locale.ROOT), ChatFormatting.WHITE),
					Ui.text("Its name is " + pet.name + ". Find it any time in Pets.", ChatFormatting.GRAY));
			Chronicle.write(op, Chronicle.Kind.PET, "tamed " + article(kind) + " " + kind.toLowerCase(java.util.Locale.ROOT) + ": " + pet.name, false);
		}
	}

	private static void died(LivingEntity e, DamageSource source) {
		Data d = data();
		Pet pet = d.pets.remove(e.getUUID());
		if (pet == null) return;
		STORE.markDirty();
		if (e.entityTags().contains(TAG)) Names.releasePet(pet.name);
		String kind = e.getType().getDescription().getString().toLowerCase(java.util.Locale.ROOT);
		String how = source.getLocalizedDeathMessage(e).getString();
		BlockPos at = e.blockPosition();
		Lost lost = new Lost();
		lost.owner = pet.owner;
		lost.name = pet.name;
		lost.type = pet.type;
		lost.dim = e.level().dimension().identifier().toString();
		lost.x = at.getX();
		lost.y = at.getY();
		lost.z = at.getZ();
		lost.at = System.currentTimeMillis();
		lost.how = how;
		d.lost.add(lost);
		while (d.lost.size() > 300) d.lost.removeFirst();
		String where = at.getX() + ", " + at.getY() + ", " + at.getZ() + (lost.dim.equals("minecraft:overworld") ? "" : " in " + Places.dimension(lost.dim));
		ServerPlayer op = e.level().getServer().getPlayerList().getPlayer(pet.owner);
		if (op != null) {
			String near = Places.nearest(op, lost.dim, at.getX(), at.getZ());
			Toast.show(op, egg(pet.type), Toast.BAD, Ui.text(pet.name + " has died", ChatFormatting.WHITE),
					Ui.text(how + (near == null ? "" : " · near " + near), ChatFormatting.GRAY));
			op.sendSystemMessage(Ui.prefix("Pets").append(Ui.text(pet.name + ", your " + kind + ", died at " + where
					+ (near == null ? "" : ", near " + near) + ".", ChatFormatting.GRAY)));
			Ui.sound(op, SoundEvents.NOTE_BLOCK_BASS, 0.6f, 0.7f);
		} else {
			d.news.computeIfAbsent(pet.owner, k -> new ArrayList<>()).add(pet.name + ", your " + kind + ", died while you were away ("
					+ how + ") at " + where + ".");
		}
		Chronicle.write(pet.owner, "", Chronicle.Kind.PET, how + (pet.ownerName.isEmpty() ? "" : " (" + pet.ownerName + "'s " + kind + ")"), false);
	}

	/** On join: what happened to your pets while you were away. */
	private static void news(ServerPlayer p) {
		List<String> list = data().news.remove(p.getUUID());
		if (list == null || list.isEmpty()) return;
		STORE.markDirty();
		for (String line : list) p.sendSystemMessage(Ui.prefix("Pets").append(Ui.text(line, ChatFormatting.GRAY)));
	}

	// ---------------------------------------------------------------- the window

	static void open(ServerPlayer p) {
		Data d = data();
		String dim = p.level().dimension().identifier().toString();
		List<Map.Entry<UUID, Pet>> mine = new ArrayList<>();
		for (Map.Entry<UUID, Pet> e : d.pets.entrySet()) if (p.getUUID().equals(e.getValue().owner)) mine.add(e);
		mine.sort(Comparator.comparingDouble(e -> e.getValue().dim.equals(dim) ? dist2(p, e.getValue()) : 1e18 + e.getValue().name.hashCode()));
		Window w = Window.of(Ui.text("Pets", ChatFormatting.LIGHT_PURPLE).withStyle(ChatFormatting.BOLD));
		w.text(Ui.text(mine.isEmpty() ? "You have no pets yet. Tame a wolf with bones, a cat with fish, a parrot with seeds or a horse "
				+ "by riding it: it gets a name of its own and shows up here." : mine.size() + (mine.size() == 1 ? " pet" : " pets")
				+ " · the nearest first", ChatFormatting.GRAY));
		MinecraftServer server = p.level().getServer();
		int shown = 0;
		for (Map.Entry<UUID, Pet> e : mine) {
			if (shown++ >= 24) break;
			Pet pet = e.getValue();
			Entity live = live(server, pet, e.getKey());
			MutableComponent text = Ui.text(pet.name, ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD)
					.append(Ui.text("  " + typeName(pet.type), ChatFormatting.GRAY)).append(Component.literal("\n"))
					.append(Ui.text(where(p, pet, live, dim), ChatFormatting.GRAY));
			w.item(egg(pet.type), text);
		}
		int buttons = 0;
		for (Map.Entry<UUID, Pet> e : mine) {
			Pet pet = e.getValue();
			if (!pet.dim.equals(dim) || buttons >= 6) continue;
			buttons++;
			UUID id = e.getKey();
			w.button("Show " + pet.name, typeName(pet.type) + " · " + Math.round(Math.sqrt(dist2(p, pet))) + " m", pp -> show(pp, id));
		}
		List<Lost> gone = new ArrayList<>();
		for (int i = d.lost.size() - 1; i >= 0 && gone.size() < 5; i--) if (p.getUUID().equals(d.lost.get(i).owner)) gone.add(d.lost.get(i));
		if (!gone.isEmpty()) {
			w.text(Ui.text("Lost", ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD));
			StringBuilder b = new StringBuilder();
			for (Lost l : gone) {
				if (!b.isEmpty()) b.append('\n');
				b.append(l.how).append("  ·  ").append(l.x).append(", ").append(l.y).append(", ").append(l.z)
						.append(l.dim.equals("minecraft:overworld") ? "" : " (" + Places.dimension(l.dim) + ")");
			}
			w.text(Ui.text(b.toString(), ChatFormatting.DARK_GRAY));
		}
		w.done().show(p);
	}

	private static @Nullable Entity live(MinecraftServer server, Pet pet, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			if (!level.dimension().identifier().toString().equals(pet.dim)) continue;
			return level.getEntity(id);
		}
		return null;
	}

	private static String where(ServerPlayer p, Pet pet, @Nullable Entity live, String dim) {
		String state = pet.sitting ? "sitting" : "";
		if (!pet.dim.equals(dim)) return join(state, "in " + Places.dimension(pet.dim));
		double m = Math.sqrt(dist2(p, pet));
		String far = (m >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f km", m / 1000) : Math.round(m) + " m") + " "
				+ Guide.arrow(p, new Vec3(pet.x + 0.5, pet.y, pet.z + 0.5));
		String seen = live != null ? "" : "last seen " + ago(pet.seen);
		String near = Places.nearest(p, pet.dim, pet.x, pet.z);
		return join(join(state, far), join(near == null ? "" : "near " + near, seen));
	}

	private static String join(String a, String b) {
		return a.isEmpty() ? b : b.isEmpty() ? a : a + " · " + b;
	}

	private static void show(ServerPlayer p, UUID id) {
		Pet pet = data().pets.get(id);
		if (pet == null) return;
		Entity live = live(p.level().getServer(), pet, id);
		Vec3 at = live != null ? live.position() : new Vec3(pet.x + 0.5, pet.y, pet.z + 0.5);
		ServerLevel level = p.level();
		if (live == null && level.getChunkSource().getChunkNow(pet.x >> 4, pet.z >> 4) != null) {
			at = new Vec3(at.x, Math.max(pet.y, level.getHeight(Heightmap.Types.MOTION_BLOCKING, pet.x, pet.z) - 1), at.z);
		}
		Guide.toPoint(p, level, at, egg(pet.type), Component.literal(pet.name), 0);
	}

	private static double dist2(ServerPlayer p, Pet pet) {
		double dx = pet.x + 0.5 - p.getX(), dz = pet.z + 0.5 - p.getZ();
		return dx * dx + dz * dz;
	}

	static ItemStack egg(String type) {
		var item = Ui.item(type + "_spawn_egg");
		return new ItemStack(item == Items.AIR ? Items.LEAD : item);
	}

	private static String typeName(String type) {
		return BuiltInRegistries.ENTITY_TYPE.getOptional(net.minecraft.resources.Identifier.withDefaultNamespace(type))
				.map(t -> t.getDescription().getString()).orElse(type);
	}

	private static String article(String word) {
		return "aeiou".indexOf(Character.toLowerCase(word.charAt(0))) >= 0 ? "an" : "a";
	}

	private static String ago(long ms) {
		long min = Math.max(0, (System.currentTimeMillis() - ms) / 60000);
		if (min < 60) return min <= 1 ? "just now" : min + " min ago";
		long h = min / 60;
		if (h < 48) return h + " h ago";
		return h / 24 + " days ago";
	}
}
