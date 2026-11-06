package nl.libreria.glint;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Proof of concept: an armour set's aura round its wearer (see scripts/glint/body_aura.py), made to fit from every angle.
 *
 * <ul>
 * <li>Every viewer gets a card of their own, drawn for the angle they see the wearer from: front or back, three-quarter,
 * side, and crouching. It turns with the wearer: the server picks the card from the wearer's body and where the viewer
 * stands, every tick.</li>
 * <li>The card rides on the wearer as a passenger (like the name plate), so the client keeps it exactly on them: no lag
 * when walking or running.</li>
 * <li>The aura is filled in from the body's core, so wherever the body isn't (an arm swinging in) it reaches right up to
 * it: no gap, no rim. The card turns to face its viewer (billboard), the body hides the middle.</li>
 * <li>Off while swimming, gliding or sleeping (the shape doesn't fit those), and when invisible.</li>
 * </ul>
 */
final class BodyAura {
	private static final class View {
		ElementHolder holder;
		ItemDisplayElement el;
		ViewerAttachment attachment;
		String model;
		int entityId;
		int age;
		boolean linked;
		float turned = Float.NaN;
	}

	private static final class State {
		String id;
		final Map<UUID, View> views = new HashMap<>();
	}

	private static final Map<UUID, State> ON = new HashMap<>();

	/** The wearer (a player or anything that looks like one) wears this aura ("fire_4"); null takes it off. */
	static void wear(LivingEntity e, String id) {
		if (id == null) {
			clear(e.getUUID());
			return;
		}
		ON.computeIfAbsent(e.getUUID(), k -> new State()).id = id;
	}

	static void clear(UUID id) {
		State s = ON.remove(id);
		if (s != null) s.views.values().forEach(v -> v.holder.destroy());
	}

	/**
	 * Which card this viewer gets: from the wearer's body and where the viewer stands. A card per 15 degrees of the
	 * angle between where the body faces and where the viewer is (s0 seen from the front or the back, s5 from the side),
	 * c0..c5 for a crouching body, and c1m..c5m for one crouching that's seen from its other side (it leans the other
	 * way on your screen).
	 */
	static String view(LivingEntity e, ServerPlayer v) {
		boolean crouch = e.getPose() == Pose.CROUCHING;
		if (v == e) return crouch ? "c0" : "s0";
		double dx = v.getX() - e.getX(), dz = v.getZ() - e.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1e-3) return crouch ? "c0" : "s0";
		float yaw = e.yBodyRot * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
		double c = Math.min(1, Math.abs(fx * dx + fz * dz) / len);
		int step = Math.min(5, (int) (Math.toDegrees(Math.acos(c)) / 15));
		if (!crouch) return "s" + step;
		// which way the body leans as this viewer sees it: to their right or their left
		double cross = fx * dz - fz * dx;
		return "c" + step + (step > 0 && cross > 0 ? "m" : "");
	}

	/**
	 * How far each card sits behind the body's middle, away from its viewer (blocks; scripts/glint/body_aura.py works
	 * them out): behind the arm and leg on the far side, so the card is never drawn over a part of you they can see.
	 */
	private static final Map<String, Float> DEPTH = Map.ofEntries(
			Map.entry("s0", 0.15f), Map.entry("s1", 0.234f), Map.entry("s2", 0.307f), Map.entry("s3", 0.363f),
			Map.entry("s4", 0.398f), Map.entry("s5", 0.41f), Map.entry("c0", 0.314f), Map.entry("c1", 0.318f),
			Map.entry("c1m", 0.318f), Map.entry("c2", 0.372f), Map.entry("c2m", 0.372f), Map.entry("c3", 0.409f),
			Map.entry("c3m", 0.409f), Map.entry("c4", 0.422f), Map.entry("c4m", 0.422f), Map.entry("c5", 0.422f),
			Map.entry("c5m", 0.422f));

	private static Vector3f place(LivingEntity e, String view) {
		// your own card faces your camera (billboard): it rides at the top of the body, its feet on the ground as the
		// body's height changes, and behind you
		return new Vector3f(0, (float) (0.70 - e.getBbHeight()), -DEPTH.getOrDefault(view, 0.3f));
	}

	/**
	 * Someone else's card for one viewer is turned to face where that viewer stands (a billboard faces the way their
	 * camera looks instead, so off to the side of their screen a card set behind the body would slide out from
	 * behind it), and set behind the body along the line from them.
	 */
	private static void face(View vw, LivingEntity e, ServerPlayer v, String view) {
		double dx = v.getX() - e.getX(), dz = v.getZ() - e.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1e-3) {
			float yaw = e.yBodyRot * Mth.DEG_TO_RAD;
			dx = -Mth.sin(yaw);
			dz = Mth.cos(yaw);
			len = 1;
		}
		dx /= len;
		dz /= len;
		// the same turn a billboard makes for a camera looking from there at the body
		float turn = (float) (Math.PI - Math.atan2(dx, -dz));
		float back = DEPTH.getOrDefault(view, 0.3f);
		Vector3f at = new Vector3f((float) (-dx * back), (float) (0.70 - e.getBbHeight()), (float) (-dz * back));
		boolean moved = Float.isNaN(vw.turned) || Math.abs(Mth.wrapDegrees((turn - vw.turned) * Mth.RAD_TO_DEG)) > 1.5f;
		if (moved) {
			vw.el.setLeftRotation(new Quaternionf().rotationY(turn));
			vw.turned = turn;
		}
		vw.el.setTranslation(at);
		vw.el.startInterpolationIfDirty();
	}

	static boolean hidden(LivingEntity e) {
		return e.isSwimming() || e.isFallFlying() || e.isSleeping() || e.isInvisible() || e.isDeadOrDying();
	}

	static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, State>> it = ON.entrySet().iterator(); it.hasNext();) {
			var en = it.next();
			State s = en.getValue();
			LivingEntity e = find(server, en.getKey());
			if (e == null || e.isRemoved()) {
				s.views.values().forEach(v -> v.holder.destroy());
				it.remove();
				continue;
			}
			List<UUID> seen = new ArrayList<>();
			if (!hidden(e)) {
				for (ServerPlayer v : ((ServerLevel) e.level()).players()) {
					if (v.distanceToSqr(e) > 64 * 64) continue;
					seen.add(v.getUUID());
					String look = view(e, v);
					String model = s.id + "__" + look;
					View vw = s.views.get(v.getUUID());
					if (vw != null && vw.entityId != e.getId()) {
						vw.holder.destroy();
						s.views.remove(v.getUUID());
						vw = null;
					}
					if (vw == null) {
						vw = create(e, v, model);
						s.views.put(v.getUUID(), vw);
					} else if (!model.equals(vw.model)) {
						vw.el.setItem(stack(model));
						vw.model = model;
					}
					if (v == e) vw.el.setTranslation(place(e, look));
					else face(vw, e, v, look);
					if (!vw.linked && vw.age++ >= 1) {
						ClientboundSetPassengersPacket link = new ClientboundSetPassengersPacket(e);
						v.connection.send(link);
						vw.linked = true;
					}
				}
			}
			for (Iterator<Map.Entry<UUID, View>> vi = s.views.entrySet().iterator(); vi.hasNext();) {
				var ve = vi.next();
				if (!seen.contains(ve.getKey())) {
					ve.getValue().holder.destroy();
					vi.remove();
				}
			}
		}
	}

	private static View create(LivingEntity e, ServerPlayer viewer, String model) {
		View vw = new View();
		vw.holder = new ElementHolder();
		vw.el = new ItemDisplayElement(stack(model));
		vw.el.setItemDisplayContext(ItemDisplayContext.FIXED);
		// your own card faces your camera (the server doesn't know where it is); others' are turned to face you (face)
		vw.el.setBillboardMode(viewer == e ? Display.BillboardConstraints.VERTICAL : Display.BillboardConstraints.FIXED);
		vw.el.setInterpolationDuration(viewer == e ? 0 : 2);
		vw.el.setBrightness(new Brightness(15, 15));
		vw.el.setViewRange(1.0f);
		vw.el.setScale(new Vector3f(1.875f));
		String look = model.substring(model.lastIndexOf("__") + 2);
		if (viewer == e) vw.el.setTranslation(place(e, look));
		vw.el.setSendPositionUpdates(false);
		vw.holder.addPassengerElement(vw.el);
		vw.attachment = new ViewerAttachment(vw.holder, e, viewer);
		// your own card: you never track yourself, so it's shown to you by hand
		if (viewer == e) vw.holder.startWatching(viewer);
		vw.model = model;
		vw.entityId = e.getId();
		return vw;
	}

	private static ItemStack stack(String model) {
		ItemStack st = new ItemStack(Items.PAPER);
		st.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("libreria", "fx/body/" + model));
		return st;
	}

	private static LivingEntity find(MinecraftServer server, UUID id) {
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		if (p != null) return p;
		for (ServerLevel level : server.getAllLevels()) {
			Entity e = level.getEntity(id);
			if (e instanceof LivingEntity le) return le;
		}
		return null;
	}

	/** Like an entity attachment, but only one viewer may watch: each viewer has a card drawn for their own angle. */
	static final class ViewerAttachment extends EntityAttachment {
		private final ServerPlayer viewer;

		ViewerAttachment(ElementHolder holder, Entity entity, ServerPlayer viewer) {
			super(holder, entity, true);
			this.viewer = viewer;
			this.attach();
		}

		@Override
		public void updateCurrentlyTracking(Collection<ServerGamePacketListenerImpl> current) {
			List<ServerGamePacketListenerImpl> mine = new ArrayList<>();
			for (var c : current) if (c == this.viewer.connection) mine.add(c);
			super.updateCurrentlyTracking(mine);
		}
	}
}
