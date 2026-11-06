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

	/** Which card this viewer gets: from the wearer's body and where the viewer stands. */
	static String view(LivingEntity e, ServerPlayer v) {
		boolean crouch = e.getPose() == Pose.CROUCHING;
		if (v == e) return crouch ? "crouch_front" : "front";
		double dx = v.getX() - e.getX(), dz = v.getZ() - e.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1e-3) return crouch ? "crouch_front" : "front";
		float yaw = e.yBodyRot * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
		double c = (fx * dx + fz * dz) / len;
		double ac = Math.abs(c);
		if (crouch) {
			if (ac > 0.7) return "crouch_front";
			// which way the body leans as this viewer sees it: to their right or their left
			double cross = fx * dz - fz * dx;
			return cross > 0 ? "crouch_side" : "crouch_side_m";
		}
		return ac > 0.866 ? "front" : ac < 0.5 ? "side" : "quarter";
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
					String model = s.id + "__" + view(e, v);
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
					// the card rides at the top of the body: keep its feet on the ground as the body's height changes
					vw.el.setTranslation(new Vector3f(0, (float) (0.70 - e.getBbHeight()), 0));
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
		vw.el.setBillboardMode(Display.BillboardConstraints.VERTICAL);
		vw.el.setBrightness(new Brightness(15, 15));
		vw.el.setViewRange(1.0f);
		vw.el.setScale(new Vector3f(1.875f));
		vw.el.setTranslation(new Vector3f(0, (float) (0.70 - e.getBbHeight()), 0));
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
