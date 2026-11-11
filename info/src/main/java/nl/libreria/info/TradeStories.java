package nl.libreria.info;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import nl.libreria.core.Chronicle;
import nl.libreria.core.ItemView;
import nl.libreria.core.Libreria;
import nl.libreria.core.Pack;
import nl.libreria.core.ServerSetting;
import org.jetbrains.annotations.Nullable;

/**
 * Now and then a villager sells a piece of gear with a part of a story on it. When a player starts trading, every offer
 * the villager has not weighed yet gets one chance (only gear, only an offer nobody bought from yet); the story can be
 * read in the offer already. Whoever buys it first gets it, as a find of their own ("Bought from Gerrit the armorer");
 * after that the offer sells the plain thing again. A villager sells one story in its life at most (a new job doesn't
 * give it a new chance), and one born from breeding has a tenth of the chance: no farm of stories.
 *
 * <p>Every item with a part of a story (found or bought) shows a gold dot in the top left corner of its slot, for its
 * holder only (custom model data flag {@link #DOT}, drawn by the pack); a one-off text gets no dot.
 */
public final class TradeStories {
	private TradeStories() {
	}

	static final ServerSetting.Number CHANCE = InfoMod.MODULE.serverNumber("trade_stories", "Stories in villager trades", Items.EMERALD, 3, 0, 20, 1,
			"%", "A new trade of gear sometimes", "carries a part of a story. A", "villager sells one in its life", "at most; bred ones a tenth as often.");
	/** On an offer's item (and what was bought, until it reaches a player's inventory): which story part, sold by whom. */
	static final String TRADE = "libreria_found_trade";
	/** On the villager: how many of its offers had their chance, that it sold a story, that it was born from breeding. */
	static final String ROLLED = "libreria.trades_rolled.", HAD = "libreria.trade_story";
	public static final String BRED = "libreria.bred";
	/** The pack's story dot (see scripts/glint/generate.py). */
	static final int DOT = 3;

	static void init() {
		ItemView.add((viewer, menu, slot, stack) -> !ItemView.creative(viewer) && Pack.has(viewer) && isStory(stack) ? dotted(stack) : stack);
		ItemView.cleaner(TradeStories::undotted);
	}

	// ---------------------------------------------------------------- the chance

	/** A player starts trading with this villager: its offers it hasn't weighed yet get their chance. */
	public static void trading(Villager v, Player player) {
		if (!InfoMod.MODULE.on() || !Found.ENABLED.get() || !(player instanceof ServerPlayer p) || CHANCE.get() <= 0) return;
		try {
			double chance = CHANCE.get() / 100.0 * (v.entityTags().contains(BRED) ? 0.1 : 1);
			roll(v, p, chance, new Random(p.getRandom().nextLong() ^ System.nanoTime()));
		} catch (RuntimeException e) {
			Libreria.LOG.warn("Trade stories: weighing the offers of {} failed", v.getUUID(), e);
		}
	}

	/** The offers not weighed yet each get {@code chance} (until one carries a story). @return the offer that does now, or null */
	static @Nullable MerchantOffer roll(Villager v, @Nullable ServerPlayer p, double chance, Random r) {
		MerchantOffers offers = v.getOffers();
		int from = rolled(v);
		if (offers.size() <= from) return null;
		setRolled(v, offers.size());
		if (v.entityTags().contains(HAD)) return null;
		for (int i = from; i < offers.size(); i++) {
			MerchantOffer o = offers.get(i);
			ItemStack res = o.getResult();
			if (o.getUses() > 0 || Found.kindOf(res) == null || Found.has(res, Found.STAMP) || Found.has(res, TRADE)) continue;
			if (r.nextDouble() >= chance) continue;
			Found.Text t = Found.storyFor(Found.itemOf(res), new Found.Origin(Found.realm(v.level()), "trade", ""), r, p);
			if (t == null) continue;
			offer(res, t, seller(v), v.level().registryAccess());
			v.addTag(HAD);
			return o;
		}
		return null;
	}

	private static int rolled(Villager v) {
		for (String tag : v.entityTags()) {
			if (!tag.startsWith(ROLLED)) continue;
			try {
				return Integer.parseInt(tag.substring(ROLLED.length()));
			} catch (NumberFormatException e) {
				return 0;
			}
		}
		return 0;
	}

	private static void setRolled(Villager v, int n) {
		for (String tag : List.copyOf(v.entityTags())) if (tag.startsWith(ROLLED)) v.removeTag(tag);
		v.addTag(ROLLED + n);
	}

	/** "Gerrit the armorer" (a villager without a name: "an armorer"). */
	static String seller(Villager v) {
		String job = v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath().replace('_', ' ')).orElse("villager");
		if ("none".equals(job) || "nitwit".equals(job)) job = "villager";
		Component name = v.getCustomName();
		if (name != null && !name.getString().isBlank()) return name.getString() + " the " + job;
		return ("aeiou".indexOf(job.charAt(0)) >= 0 ? "an " : "a ") + job;
	}

	// ---------------------------------------------------------------- the offer, the sale

	/** The offer's item carries the story: readable in the offer, sold by this villager (who buys it, isn't known yet). */
	static void offer(ItemStack res, Found.Text t, String seller, net.minecraft.core.RegistryAccess access) {
		String kind = Found.kindOf(res);
		String noun = kind == null ? "thing" : Found.noun(kind, res);
		List<Component> lines = new ArrayList<>();
		lines.add(Lore.line("✦ " + t.title() + " · part " + t.part() + " of " + t.parts(), Found.HEAD, false));
		for (String l : Lore.wrap(t.text().replace("{item}", noun), 40)) lines.add(Lore.line(l, Found.TEXT, true));
		lines.add(Lore.line("Sold by " + seller, Found.WHERE, false));
		Lore.set(res, Lore.FOUND, lines);
		CompoundTag tag = new CompoundTag();
		tag.putString("id", t.id());
		tag.putString("label", "Bought from " + seller);
		if (t.name() != null && res.get(DataComponents.CUSTOM_NAME) == null) {
			TextColor colour = TextColor.fromLegacyFormat(Weapons.quality(res, access).color);
			res.set(DataComponents.CUSTOM_NAME, Component.literal(t.name()).withStyle(st -> st.withItalic(false).withColor(colour)));
			tag.putBoolean("named", true);
		}
		CustomData.update(DataComponents.CUSTOM_DATA, res, root -> root.put(TRADE, tag));
	}

	/** A trade was made: an offer that carried a story sells the plain thing from now on (the story went with the buyer). */
	public static void traded(MerchantOffer offer) {
		ItemStack res = offer.getResult();
		if (!Found.has(res, TRADE)) return;
		CompoundTag tag = res.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty(TRADE);
		CustomData.update(DataComponents.CUSTOM_DATA, res, root -> root.remove(TRADE));
		Lore.set(res, Lore.FOUND, List.of());
		if (Lore.get(res, Lore.OWN).isEmpty()) CustomData.update(DataComponents.CUSTOM_DATA, res, root -> root.remove(Lore.KEY));
		if (tag.getBooleanOr("named", false)) res.remove(DataComponents.CUSTOM_NAME);
	}

	/** What was bought reached a player's inventory: their find now, bought on this day by them. */
	static void bought(ServerPlayer p, ItemStack s) {
		CompoundTag tag = s.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty(TRADE);
		CustomData.update(DataComponents.CUSTOM_DATA, s, root -> root.remove(TRADE));
		Found.Text t = Found.byId(tag.getStringOr("id", ""));
		if (t == null || t.story() == null || Found.has(s, Found.STAMP)) return;
		String label = tag.getStringOr("label", "Bought from a villager");
		int day = Chronicle.day(p.level().getServer());
		Found.write(s, t, label, day, p.getName().getString(), p.level().registryAccess());
		Found.remember(p, s, t, label, day);
		Found.data().stories.add(t.story());
		Found.STORE.markDirty();
		nl.libreria.core.Tips.once(p, Found.TIP);
	}

	// ---------------------------------------------------------------- the dot

	/** A part of a story (found, bought, or still for sale): not a one-off text. */
	static boolean isStory(ItemStack s) {
		if (s.isEmpty() || !s.has(DataComponents.CUSTOM_DATA)) return false;
		if (Found.pieceOf(s) != null) return true;
		if (!Found.has(s, TRADE)) return false;
		return s.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty(TRADE).getStringOr("id", "").startsWith("a:");
	}

	static ItemStack dotted(ItemStack stack) {
		CustomModelData had = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		List<Boolean> flags = new ArrayList<>(had == null ? List.of() : had.flags());
		while (flags.size() <= DOT) flags.add(false);
		if (flags.get(DOT)) return stack;
		flags.set(DOT, true);
		ItemStack out = stack.copy();
		out.set(DataComponents.CUSTOM_MODEL_DATA, had == null ? new CustomModelData(List.of(), flags, List.of(), List.of())
				: new CustomModelData(had.floats(), flags, had.strings(), had.colors()));
		return out;
	}

	/** What a creative client sends back: the dot out again. */
	static ItemStack undotted(ItemStack stack) {
		CustomModelData had = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		if (had == null || had.flags().size() <= DOT || !had.flags().get(DOT)) return stack;
		List<Boolean> flags = new ArrayList<>(had.flags());
		flags.set(DOT, false);
		while (!flags.isEmpty() && !flags.get(flags.size() - 1)) flags.remove(flags.size() - 1);
		ItemStack out = stack.copy();
		if (flags.isEmpty() && had.floats().isEmpty() && had.strings().isEmpty() && had.colors().isEmpty()) out.remove(DataComponents.CUSTOM_MODEL_DATA);
		else out.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(had.floats(), flags, had.strings(), had.colors()));
		return out;
	}

	/** The offers as one player's client sees them: a story's item with its dot. */
	public static MerchantOffers forViewer(ServerPlayer viewer, MerchantOffers offers) {
		if (!Pack.has(viewer)) return offers;
		boolean any = false;
		for (MerchantOffer o : offers) any |= isStory(o.getResult());
		if (!any) return offers;
		MerchantOffers out = new MerchantOffers();
		for (MerchantOffer o : offers) {
			MerchantOffer c = o.copy();
			if (isStory(c.getResult())) {
				ItemStack d = dotted(c.getResult());
				c.getResult().set(DataComponents.CUSTOM_MODEL_DATA, d.get(DataComponents.CUSTOM_MODEL_DATA));
			}
			out.add(c);
		}
		return out;
	}

	// ---------------------------------------------------------------- for the screenshots

	/**
	 * A villager with an offer carrying a story, before the player (its trade window open): {@code item} ("iron_boots")
	 * or any item a story is told on. @return the villager, or null
	 */
	public static @Nullable Villager debugVillager(ServerPlayer p, @Nullable String item) {
		ServerLevel level = p.level();
		Villager v = net.minecraft.world.entity.EntityTypes.VILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		if (v == null) return null;
		var job = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION
				.get(net.minecraft.world.entity.npc.villager.VillagerProfession.ARMORER);
		job.ifPresent(h -> v.setVillagerData(v.getVillagerData().withProfession(h)));
		net.minecraft.world.phys.Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2.5));
		v.setPos(at.x, p.getY(), at.z);
		v.setNoAi(true);
		level.addFreshEntity(v);
		String on = item != null ? item : Found.anyStoryItem();
		if (on == null) return v;
		var it = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(on));
		MerchantOffers offers = new MerchantOffers();
		offers.add(new MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 1), new ItemStack(Items.WHEAT), 16, 2, 0.05f));
		offers.add(new MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 9), new ItemStack(it), 12, 5, 0.2f));
		offers.add(new MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 4), new ItemStack(Items.IRON_HELMET), 12, 5, 0.2f));
		v.setOffers(offers);
		roll(v, p, 1.0, new Random(7));
		return v;
	}

	/** For the screenshots: the story offer bought, as a trade would (the item in the player's inventory, the offer plain). */
	public static void debugBuy(ServerPlayer p, Villager v) {
		for (MerchantOffer o : v.getOffers()) {
			if (!Found.has(o.getResult(), TRADE)) continue;
			ItemStack got = o.assemble();
			o.increaseUses();
			traded(o);
			int slot = p.getInventory().getFreeSlot();
			if (slot < 0) return;
			p.getInventory().setItem(slot, got);
			bought(p, got);
			return;
		}
	}

	public static String describe(ItemStack s) {
		return s.getHoverName().getString() + " " + String.join(" / ", Lore.get(s, Lore.FOUND).stream().map(Component::getString).toList())
				.toLowerCase(Locale.ROOT);
	}
}
