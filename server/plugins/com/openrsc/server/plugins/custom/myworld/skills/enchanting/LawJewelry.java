package com.openrsc.server.plugins.custom.myworld.skills.enchanting;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import com.openrsc.server.plugins.triggers.UseInvTrigger;
import com.openrsc.server.plugins.triggers.UseLocTrigger;

import static com.openrsc.server.plugins.Functions.delay;
import static com.openrsc.server.plugins.Functions.mes;
import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.validatebankpin;

public final class LawJewelry implements OpInvTrigger, UseInvTrigger, UseLocTrigger {

	private enum Destination {
		CRAFTING_GUILD("Crafting Guild", 347, 599),
		MINING_GUILD("Mining Guild", 250, 537),
		WOODCUTTING_GUILD("Woodcutting Guild", 560, 473),
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

	@Override
	public boolean blockOpInv(final Player player, final Integer invIndex, final Item item, final String command) {
		return item != null && isLawChargeItem(item.getCatalogId())
			&& ("check".equalsIgnoreCase(command)
				|| (EnchantingItemEffects.isLawAmulet(item.getCatalogId()) && "use".equalsIgnoreCase(command)));
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
		if (!EnchantingItemEffects.isLawAmulet(item.getCatalogId()) || !"use".equalsIgnoreCase(command)) {
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
			player.message("Your law amulet is out of charges.");
			player.message("Recharge it at the law altar.");
			return;
		}

		final Destination[] destinations = getDestinationsForTier(EnchantingItemEffects.getLawAmuletTier(item.getCatalogId()));
		if (destinations == null) {
			player.message("Nothing interesting happens.");
			return;
		}

		player.message("Where would you like to teleport to?");
		final int option = multi(player,
			destinations[0].label,
			destinations[1].label,
			"Nowhere");
		if (option < 0 || option > 2 || option == 2) {
			return;
		}

		final Destination destination = destinations[option];
		player.teleport(destination.x, destination.y, true);
		setRemainingCharges(player, item, charges - 1);
		player.message("Your law amulet now has " + formatCharges(charges - 1) + " remaining.");
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

		setRemainingCharges(player, item, maxCharges);
		mes("You hold the jewelry against the altar.");
		delay();
		mes("Law energy flows back into it.");
		delay();
		player.message("It is fully recharged.");
	}

	private boolean isTeleportBlocked(final Player player) {
		return player.getLocation().wildernessLevel() >= Constants.GLORY_TELEPORT_LIMIT
			|| player.getLocation().isInFisherKingRealm()
			|| player.getLocation().isInsideGrandTreeGround()
			|| (player.getLocation().inModRoom() && !player.isAdmin());
	}

	private int getRemainingCharges(final Player player, final Item item) {
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
				return new Destination[] {Destination.WOODCUTTING_GUILD, Destination.PRAYER_GUILD};
			case 3:
				return new Destination[] {Destination.FISHING_GUILD, Destination.COOKING_GUILD};
			case 4:
				return new Destination[] {Destination.HEROES_GUILD, Destination.WIZARDS_GUILD};
			case 5:
				return new Destination[] {Destination.CHAMPIONS_GUILD, Destination.LEGENDS_GUILD};
			default:
				return null;
		}
	}
}
