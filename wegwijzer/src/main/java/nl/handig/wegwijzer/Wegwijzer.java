package nl.handig.wegwijzer;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import nl.handig.kern.Guide;
import nl.handig.kern.Module;
import nl.handig.kern.PlayerStore;
import nl.handig.kern.Services;
import nl.handig.kern.Setting;
import nl.handig.kern.Ui;
import org.jetbrains.annotations.Nullable;

/** Wegwijzer – een Handig-module: een straal naar je sterfplek en je eigen waypoints (zonder teleporteren). */
public final class Wegwijzer implements ModInitializer {
	public static final int MAX_POINTS = 45;

	public static final Module MODULE = Module.register("wegwijzer", "Wegwijzer", Items.COMPASS,
			"Een straal naar je sterfplek", "en naar je eigen waypoints.");
	public static final Setting.Toggle DEATH_BEAM = MODULE.toggle("sterfplek", "Straal naar je sterfplek", true, Items.SKELETON_SKULL,
			"Na het respawnen wijst een straal je de weg", "terug naar waar je doodging.");
	public static final Setting.Toggle DEATH_MESSAGE = MODULE.toggle("bericht", "Coördinaten bij doodgaan", true, Items.PAPER,
			"Een bericht met waar je doodging", "(klik om de weg te laten wijzen).");

	/** Eén punt. */
	public static final class Point {
		public String name = "";
		public String dim = "minecraft:overworld";
		public int x;
		public int y;
		public int z;
		public String icon = "minecraft:compass";
		public long created;

		public BlockPos pos() {
			return new BlockPos(this.x, this.y, this.z);
		}

		public Item item() {
			Item i = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.tryParse(this.icon));
			return i == Items.AIR ? Items.COMPASS : i;
		}
	}

	public static final class Data {
		public List<Point> points = new ArrayList<>();
		public @Nullable Point death;
	}

	static final PlayerStore<Data> STORE = PlayerStore.create("handig/wegwijzer", Data.class, Data::new, d -> {
		if (d.points == null) d.points = new ArrayList<>();
	});

	@Override
	public void onInitialize() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) onDeath(p);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) onRespawn(newPlayer);
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			dispatcher.register(Commands.literal("punt")
					.executes(c -> {
						PointsGui.open(c.getSource().getPlayerOrException());
						return 1;
					})
					.then(Commands.literal("zet").then(Commands.argument("naam", StringArgumentType.greedyString())
							.executes(c -> add(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "naam")) ? 1 : 0)))
					.then(Commands.literal("weg").then(Commands.argument("naam", StringArgumentType.greedyString())
							.executes(c -> remove(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "naam")) ? 1 : 0)))
					.then(Commands.literal("ga").then(Commands.argument("naam", StringArgumentType.greedyString())
							.executes(c -> go(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "naam")) ? 1 : 0))));
			dispatcher.register(Commands.literal("sterfplek").executes(c -> goDeath(c.getSource().getPlayerOrException()) ? 1 : 0));
		});
		MODULE.hubEntry(Items.COMPASS, "Waypoints", ChatFormatting.AQUA, PointsGui::open,
				"Je eigen punten met een straal ernaartoe.", "Ook: /punt zet <naam>");
		MODULE.hubEntry(Items.SKELETON_SKULL, "Naar je sterfplek", ChatFormatting.RED, Wegwijzer::goDeath,
				"Een straal naar waar je het laatst doodging.");
		Services.provideMenu(Services.MENU_WAYPOINTS, PointsGui::open);
		WegwijzerTests.register();
	}

	static String dimName(String dim) {
		return switch (dim) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> dim;
		};
	}

	static String dimOf(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

	private static void onDeath(ServerPlayer p) {
		Data d = STORE.get(p);
		Point death = new Point();
		death.name = "Sterfplek";
		death.dim = dimOf(p.level());
		BlockPos pos = p.blockPosition();
		death.x = pos.getX();
		death.y = Math.max(p.level().getMinY() + 1, pos.getY());
		death.z = pos.getZ();
		death.icon = "minecraft:skeleton_skull";
		death.created = System.currentTimeMillis();
		d.death = death;
		STORE.markDirty(p);
	}

	private static void onRespawn(ServerPlayer p) {
		Point death = STORE.get(p).death;
		if (death == null) return;
		if (DEATH_MESSAGE.get(p)) {
			MutableComponent msg = Ui.prefix("Wegwijzer").append(Ui.text("Je ging dood op ", ChatFormatting.GRAY))
					.append(Ui.text(death.x + " " + death.y + " " + death.z, ChatFormatting.WHITE))
					.append(Ui.text(" (" + dimName(death.dim) + ") ", ChatFormatting.GRAY))
					.append(Ui.text("[Wijs de weg]", ChatFormatting.AQUA).withStyle(st -> st
							.withClickEvent(new ClickEvent.RunCommand("/sterfplek"))
							.withHoverEvent(new HoverEvent.ShowText(Component.literal("Straal naar je sterfplek")))));
			// Andere modules (zoals Graf) voegen hier hun regel toe
			for (Component note : nl.handig.kern.Services.deathNotes(p)) msg.append("\n").append(note);
			p.sendSystemMessage(msg);
		} else {
			for (Component note : nl.handig.kern.Services.deathNotes(p)) p.sendSystemMessage(note);
		}
		if (DEATH_BEAM.get(p) && dimOf(p.level()).equals(death.dim)) {
			p.level().getServer().execute(() -> beam(p, death));
		}
	}

	static boolean goDeath(ServerPlayer p) {
		Point death = STORE.get(p).death;
		if (death == null) {
			p.sendSystemMessage(Ui.prefix("Wegwijzer").append(Ui.text("Je bent nog niet doodgegaan (sinds de mod er is).", ChatFormatting.GRAY)));
			return false;
		}
		return beam(p, death);
	}

	/** Straal naar een punt (alleen in dezelfde dimensie). */
	static boolean beam(ServerPlayer p, Point point) {
		if (!dimOf(p.level()).equals(point.dim)) {
			p.sendSystemMessage(Ui.prefix("Wegwijzer").append(Ui.text(point.name + " ligt in de " + dimName(point.dim) + ".", ChatFormatting.GRAY)));
			return false;
		}
		Guide.toPoint(p, p.level(), Vec3.atBottomCenterOf(point.pos()), new ItemStack(point.item()), Component.literal(point.name), 0);
		return true;
	}

	static @Nullable Point find(ServerPlayer p, String name) {
		String n = name.trim().toLowerCase(Locale.ROOT);
		for (Point pt : STORE.get(p).points) {
			if (pt.name.toLowerCase(Locale.ROOT).equals(n)) return pt;
		}
		return null;
	}

	static boolean add(ServerPlayer p, String name) {
		String n = name.trim();
		if (n.isEmpty() || n.length() > 32) {
			p.sendSystemMessage(Ui.prefix("Wegwijzer").append(Ui.text("Geef een naam van 1 tot 32 tekens.", ChatFormatting.RED)));
			return false;
		}
		Data d = STORE.get(p);
		Point existing = find(p, n);
		if (existing == null && d.points.size() >= MAX_POINTS) {
			p.sendSystemMessage(Ui.prefix("Wegwijzer").append(Ui.text("Je hebt al " + MAX_POINTS + " punten. Haal er eerst een weg.", ChatFormatting.RED)));
			return false;
		}
		Point pt = existing != null ? existing : new Point();
		pt.name = n;
		pt.dim = dimOf(p.level());
		BlockPos pos = p.blockPosition();
		pt.x = pos.getX();
		pt.y = pos.getY();
		pt.z = pos.getZ();
		Item below = p.level().getBlockState(pos.below()).getBlock().asItem();
		pt.icon = below == Items.AIR ? "minecraft:compass" : BuiltInRegistries.ITEM.getKey(below).toString();
		pt.created = System.currentTimeMillis();
		if (existing == null) d.points.add(pt);
		STORE.markDirty(p);
		p.sendSystemMessage(Ui.prefix("Wegwijzer").append(Ui.text((existing == null ? "Punt " : "Punt bijgewerkt: "), ChatFormatting.GRAY))
				.append(Ui.text(n, ChatFormatting.WHITE)).append(Ui.text(" op " + pt.x + " " + pt.y + " " + pt.z, ChatFormatting.GRAY)));
		return true;
	}

	static boolean remove(ServerPlayer p, String name) {
		Point pt = find(p, name);
		if (pt == null) {
			p.sendSystemMessage(Ui.prefix("Wegwijzer").append(Ui.text("Geen punt met die naam.", ChatFormatting.RED)));
			return false;
		}
		STORE.get(p).points.remove(pt);
		STORE.markDirty(p);
		p.sendSystemMessage(Ui.prefix("Wegwijzer").append(Ui.text("Punt " + pt.name + " verwijderd.", ChatFormatting.GRAY)));
		return true;
	}

	static boolean go(ServerPlayer p, String name) {
		Point pt = find(p, name);
		if (pt == null) {
			p.sendSystemMessage(Ui.prefix("Wegwijzer").append(Ui.text("Geen punt met die naam.", ChatFormatting.RED)));
			return false;
		}
		return beam(p, pt);
	}
}
