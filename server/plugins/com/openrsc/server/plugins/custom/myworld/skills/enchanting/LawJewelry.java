package com.openrsc.server.plugins.custom.myworld.skills.enchanting;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.content.production.ProductionRecipe;
import com.openrsc.server.content.production.ProductionSession;
import com.openrsc.server.content.production.ProductionStarter;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import com.openrsc.server.plugins.triggers.UseInvTrigger;
import com.openrsc.server.plugins.triggers.UseLocTrigger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.openrsc.server.plugins.Functions.delay;
import static com.openrsc.server.plugins.Functions.mes;
import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.validatebankpin;

public final class LawJewelry implements OpInvTrigger, UseInvTrigger, UseLocTrigger {

	private static final String DRAGONSTONE_AMULET_ITEM_UID_KEY = "production_context_item_uid";

	private enum Destination {
		CRAFTING_GUILD("Crafting Guild", 347, 599),
		MINING_GUILD("Mining Guild", 250, 537),
		RANGERS_GUILD("Rangers Guild", 496, 462),
		PRAYER_GUILD("Prayer Guild", 256, 471),
		FISHING_GUILD("Fishing Guild", 586, 525),
		COOKING_GUILD("Cooking Guild", 179, 489),
		HEROES_GUILD("Heroes' Guild", 372, 443),
		WIZARDS_GUILD("Wizards' Guild", 600, 757),
		CHAMPIONS_GUILD("Champions' Guild", 150, 552),
		LEGENDS_GUILD("Legends' Guild", 513, 552);

		private final String label;
		private final int x;
		private final int y;

		Destination(final String label, final int x, final int y) {
			this.label = label;
			this.x = x;
			this.y = y;
		}
	}

	private enum RuneAltarDestination {
		AIR(ItemId.AIR_RUNE.id(), 305, 593),
		MIND(ItemId.MIND_RUNE.id(), 296, 438),
		WATER(ItemId.WATER_RUNE.id(), 146, 684),
		EARTH(ItemId.EARTH_RUNE.id(), 61, 464),
		FIRE(ItemId.FIRE_RUNE.id(), 49, 633),
		BODY(ItemId.BODY_RUNE.id(), 258, 503),
		COSMIC(ItemId.COSMIC_RUNE.id(), 104, 3556),
		CHAOS(ItemId.CHAOS_RUNE.id(), 231, 375),
		NATURE(ItemId.NATURE_RUNE.id(), 391, 804),
		LAW(ItemId.LAW_RUNE.id(), 408, 534),
		DEATH(ItemId.DEATH_RUNE.id(), 150, 212),
		BLOOD(ItemId.BLOOD_RUNE.id(), 246, 102),
		SOUL(ItemId.SOUL_RUNE.id(), 610, 3599),
		LIFE(ItemId.LIFE_RUNE.id(), 282, 694);

		private final int runeItemId;
		private final int x;
		private final int y;

		RuneAltarDestination(final int runeItemId, final int x, final int y) {
			this.runeItemId = runeItemId;
			this.x = x;
			this.y = y;
		}

		private static RuneAltarDestination forRuneItem(final int itemId) {
			for (RuneAltarDestination destination : values()) {
				if (destination.runeItemId == itemId) {
					return destination;
				}
			}
			return null;
		}
	}

	@Override
	public boolean blockOpInv(final Player player, final Integer invIndex, final Item item, final String command) {
		return item != null && isLawChargeItem(item.getCatalogId())
			&& ("check".equalsIgnoreCase(command)
				|| (EnchantingItemEffects.isLawAmulet(item.getCatalogId()) && "teleport".equalsIgnoreCase(command)));
	}

	@Override
	public void onOpInv(final Player player, final Integer invIndex, final Item item, final String command) {
		if (item == null || !isLawChargeItem(item.getCatalogId())) {
			return;
		}
		if ("check".equalsIgnoreCase(command)) {
			showRemainingCharges(player, item);
			return;
		}
		if (!EnchantingItemEffects.isLawAmulet(item.getCatalogId()) || !"teleport".equalsIgnoreCase(command)) {
			return;
		}

		if (isTeleportBlocked(player)) {
			player.message("A mysterious force blocks your teleport!");
			if (player.getLocation().wildernessLevel() >= Constants.GLORY_TELEPORT_LIMIT) {
				player.message("You can't use this teleport after level 30 wilderness");
			}
			return;
		}

		final int charges = getRemainingCharges(player, item);
		if (charges <= 0) {
			player.message("Your law Bangel is out of charges.");
			player.message("Recharge it at the law altar.");
			return;
		}

		final int tier = EnchantingItemEffects.getLawAmuletTier(item.getCatalogId());
		if (tier == 5) {
			showRuneAltarTeleportInterface(player, item);
			return;
		}
		final Destination[] destinations = getDestinationsForTier(tier);
		if (destinations == null) {
			player.message("Nothing interesting happens.");
			return;
		}

		player.message("Where would you like to teleport to?");
		final String[] options = new String[destinations.length + 1];
		for (int i = 0; i < destinations.length; i++) {
			options[i] = destinations[i].label;
		}
		options[destinations.length] = "Nowhere";
		final int option = multi(player, options);
		if (option < 0 || option >= destinations.length) {
			return;
		}

		final Destination destination = destinations[option];
		player.teleport(destination.x, destination.y, true);
		setRemainingCharges(player, item, charges - 1);
		player.message("Your law Bangel now has " + formatCharges(charges - 1) + " remaining.");
	}

	private void showRuneAltarTeleportInterface(final Player player, final Item amulet) {
		final List<ProductionRecipe> destinations = new ArrayList<>();
		for (RuneAltarDestination destination : RuneAltarDestination.values()) {
			destinations.add(new ProductionRecipe(destination.runeItemId, 0, 1, 1, true, true));
		}
		final ProductionSession session = new ProductionSession(
			ProductionSession.TYPE_TELEPORT_DESTINATION,
			"Choose a rune altar",
			amulet.getCatalogId(),
			destinations);
		player.setAttribute(DRAGONSTONE_AMULET_ITEM_UID_KEY, amulet.getItemId());
		player.setAttribute("production_session", session);
		player.setAttribute("production_starter", (ProductionStarter) LawJewelry::teleportToRuneAltar);
		ActionSender.showProductionInterface(player, session);
	}

	private static boolean teleportToRuneAltar(final Player player, final ProductionSession session,
		final int runeItemId, final int quantity) {
		if (session == null || !session.isType(ProductionSession.TYPE_TELEPORT_DESTINATION)) {
			return false;
		}
		final RuneAltarDestination destination = RuneAltarDestination.forRuneItem(runeItemId);
		if (destination == null) {
			player.message("That altar is not available.");
			return false;
		}

		final long itemUid = player.getAttribute(DRAGONSTONE_AMULET_ITEM_UID_KEY, -1L);
		player.removeAttribute(DRAGONSTONE_AMULET_ITEM_UID_KEY);
		Item amulet = null;
		for (Item inventoryItem : player.getCarriedItems().getInventory().getItems()) {
			if (inventoryItem.getItemId() == itemUid
				&& EnchantingItemEffects.getLawAmuletTier(inventoryItem.getCatalogId()) == 5) {
				amulet = inventoryItem;
				break;
			}
		}
		if (amulet == null) {
			player.message("You no longer have that Bangel.");
			return true;
		}

		final LawJewelry lawJewelry = new LawJewelry();
		if (lawJewelry.isTeleportBlocked(player)) {
			player.message("A mysterious force blocks your teleport!");
			if (player.getLocation().wildernessLevel() >= Constants.GLORY_TELEPORT_LIMIT) {
				player.message("You can't use this teleport after level 30 wilderness");
			}
			return true;
		}
		final int charges = lawJewelry.getRemainingCharges(player, amulet);
		if (charges <= 0) {
			player.message("Your law Bangel is out of charges.");
			player.message("Recharge it at the law altar.");
			return true;
		}

		player.teleport(destination.x, destination.y, true);
		lawJewelry.setRemainingCharges(player, amulet, charges - 1);
		player.message("Your law Bangel now has " + lawJewelry.formatCharges(charges - 1) + " remaining.");
		return true;
	}

	@Override
	public boolean blockUseInv(final Player player, final Integer invIndex, final Item item1, final Item item2) {
		return item1 != null && item2 != null
			&& (EnchantingItemEffects.isLawRing(item1.getCatalogId()) || EnchantingItemEffects.isLawRing(item2.getCatalogId()));
	}

	@Override
	public void onUseInv(final Player player, final Integer invIndex, final Item item1, final Item item2) {
		if (item1 == null || item2 == null) {
			return;
		}

		final Item ring = EnchantingItemEffects.isLawRing(item1.getCatalogId()) ? item1
			: EnchantingItemEffects.isLawRing(item2.getCatalogId()) ? item2 : null;
		final Item target = ring == item1 ? item2 : item1;
		if (ring == null || target == null) {
			return;
		}

		if (EnchantingItemEffects.isLawRing(target.getCatalogId())) {
			player.message("Use the ring on an item you want to bank.");
			return;
		}

		final int charges = getRemainingCharges(player, ring);
		if (charges <= 0) {
			player.message("Your law ring is out of charges.");
			player.message("Recharge it at the law altar.");
			return;
		}

		if (!validatebankpin(player, null)) {
			return;
		}

		final Item bankedItem = new Item(target.getCatalogId(), target.getAmount(), target.getNoted());
		if (!player.getBank().canHold(bankedItem)) {
			player.message("You don't have room in your bank for that.");
			return;
		}

		if (player.getCarriedItems().remove(new Item(target.getCatalogId(), target.getAmount(), target.getNoted(), target.getItemId())) == -1) {
			return;
		}
		if (!player.getBank().add(bankedItem, false)) {
			player.getCarriedItems().getInventory().add(target);
			player.message("You fail to send the item to your bank.");
			return;
		}

		setRemainingCharges(player, ring, charges - 1);
		player.message("Your law ring sends the " + target.getDef(player.getWorld()).getName() + " to your bank.");
		player.message("Your law ring now has " + formatCharges(charges - 1) + " remaining.");
	}

	@Override
	public boolean blockUseLoc(final Player player, final GameObject obj, final Item item) {
		return item != null
			&& normalizeLawAltarId(obj.getID()) == EnchantingItemEffects.LAW_ALTAR
			&& isLawChargeItem(item.getCatalogId());
	}

	@Override
	public void onUseLoc(final Player player, final GameObject obj, final Item item) {
		if (item == null || normalizeLawAltarId(obj.getID()) != EnchantingItemEffects.LAW_ALTAR) {
			return;
		}
		final int maxCharges = EnchantingItemEffects.getLawItemMaxCharges(item.getCatalogId());
		if (maxCharges <= 0) {
			return;
		}
		if (getRemainingCharges(player, item) >= maxCharges) {
			player.message("That item is already fully charged.");
			return;
		}

		if (EnchantingItemEffects.isLawAmulet(item.getCatalogId()) && !chargeLawRunesForAmuletRecharge(player, item, maxCharges)) {
			return;
		}
		if (EnchantingItemEffects.isLawBankingItem(item.getCatalogId()) && !chargeLawRunesForBankingRecharge(player, item, maxCharges)) {
			return;
		}

		setRemainingCharges(player, item, maxCharges);
		mes("You hold the jewelry against the altar.");
		delay();
		mes("Law energy flows back into it.");
		delay();
		player.message("It is fully recharged.");
	}

	private boolean chargeLawRunesForAmuletRecharge(final Player player, final Item item, final int maxCharges) {
		final int tier = EnchantingItemEffects.getLawAmuletTier(item.getCatalogId());
		if (tier <= 0) {
			return false;
		}
		final int missingCharges = maxCharges - getRemainingCharges(player, item);
		final int requiredRunes = missingCharges * 5 * tier;
		if (requiredRunes <= 0) {
			return true;
		}
		if (player.getCarriedItems().getInventory().countId(ItemId.LAW_RUNE.id(), Optional.of(false)) < requiredRunes) {
			player.message("You need " + requiredRunes + " law runes to recharge this Bangel.");
			return false;
		}
		return player.getCarriedItems().remove(new Item(ItemId.LAW_RUNE.id(), requiredRunes)) != -1;
	}

	private boolean chargeLawRunesForBankingRecharge(final Player player, final Item item, final int maxCharges) {
		final int missingCharges = maxCharges - getRemainingCharges(player, item);
		final int requiredRunes = getLawBankingRechargeRuneCost(missingCharges);
		if (requiredRunes <= 0) {
			return true;
		}
		if (player.getCarriedItems().getInventory().countId(ItemId.LAW_RUNE.id(), Optional.of(false)) < requiredRunes) {
			player.message("You need " + requiredRunes + " law runes to recharge this jewelry.");
			return false;
		}
		return player.getCarriedItems().remove(new Item(ItemId.LAW_RUNE.id(), requiredRunes)) != -1;
	}

	private int getLawBankingRechargeRuneCost(final int missingCharges) {
		if (missingCharges <= 0) {
			return 0;
		}
		return Math.max(10, (missingCharges + 9) / 10);
	}

	private boolean isTeleportBlocked(final Player player) {
		return player.getLocation().wildernessLevel() >= Constants.GLORY_TELEPORT_LIMIT
			|| player.getLocation().isInFisherKingRealm()
			|| player.getLocation().isInsideGrandTreeGround()
			|| (player.getLocation().inModRoom() && !player.isAdmin());
	}

	private int getRemainingCharges(final Player player, final Item item) {
		if (EnchantingItemEffects.isLawBankingItem(item.getCatalogId())) {
			return EnchantingItemEffects.getLawBankingItemCharges(player, item);
		}
		final int maxCharges = EnchantingItemEffects.getLawItemMaxCharges(item.getCatalogId());
		if (maxCharges <= 0) {
			return 0;
		}
		int charges = item.getItemStatus().getDurability();
		if (charges > maxCharges) {
			charges = maxCharges;
			setRemainingCharges(player, item, charges);
		}
		return charges;
	}

	private void setRemainingCharges(final Player player, final Item item, final int charges) {
		if (EnchantingItemEffects.isLawBankingItem(item.getCatalogId())) {
			EnchantingItemEffects.setLawBankingItemCharges(player, item, charges);
			return;
		}
		item.getItemStatus().setDurability(Math.max(charges, 0));
	}

	private void showRemainingCharges(final Player player, final Item item) {
		final int maxCharges = EnchantingItemEffects.getLawItemMaxCharges(item.getCatalogId());
		player.message("It has " + formatCharges(getRemainingCharges(player, item)) + " remaining.");
		player.message("It can hold " + formatCharges(maxCharges) + ".");
	}

	private boolean isLawChargeItem(final int itemId) {
		return EnchantingItemEffects.getLawItemMaxCharges(itemId) > 0;
	}

	private String formatCharges(final int charges) {
		return charges + " charge" + (charges == 1 ? "" : "s");
	}

	private int normalizeLawAltarId(final int objectId) {
		if (objectId == EnchantingItemEffects.LAW_ALTAR) {
			return objectId;
		}
		if (objectId == EnchantingItemEffects.LAW_ALTAR + 1) {
			return EnchantingItemEffects.LAW_ALTAR;
		}
		return -1;
	}

	private Destination[] getDestinationsForTier(final int tier) {
		switch (tier) {
			case 1:
				return new Destination[] {Destination.CRAFTING_GUILD, Destination.MINING_GUILD};
			case 2:
				return new Destination[] {Destination.COOKING_GUILD, Destination.PRAYER_GUILD};
			case 3:
				return new Destination[] {
					Destination.FISHING_GUILD,
					Destination.RANGERS_GUILD,
					Destination.WIZARDS_GUILD
				};
			case 4:
				return new Destination[] {
					Destination.HEROES_GUILD,
					Destination.CHAMPIONS_GUILD,
					Destination.LEGENDS_GUILD
				};
			default:
				return null;
		}
	}
}
