package com.openrsc.server.plugins.custom.myworld.skills.prayer;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Devotion;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.plugins.triggers.UseLocTrigger;

import static com.openrsc.server.plugins.Functions.give;

public final class BlessedStaffs implements UseLocTrigger {

	@Override
	public boolean blockUseLoc(final Player player, final GameObject obj, final Item item) {
		return item != null
			&& !item.getNoted()
			&& PrayerCatalog.getGodLineForAltar(obj.getID(), obj.getX(), obj.getY()) != null
			&& getBlessedStaffProduct(item.getCatalogId()) != -1;
	}

	@Override
	public void onUseLoc(final Player player, final GameObject obj, final Item item) {
		if (item == null || item.getNoted()) {
			return;
		}

		final PrayerCatalog.GodLine godLine = PrayerCatalog.getGodLineForAltar(obj.getID(), obj.getX(), obj.getY());
		if (godLine == null) {
			return;
		}

		final int productId = getBlessedStaffProduct(godLine, item.getCatalogId());
		if (productId == -1) {
			return;
		}

		final int requiredPrayerLevel = EnchantingItemEffects.getTemporaryEnchantingRequirementForTier(
			EnchantingItemEffects.getTierForBaseStaff(item.getCatalogId()));
		if (requiredPrayerLevel == -1 || player.getSkills().getLevel(Skill.PRAYER.id()) < requiredPrayerLevel) {
			player.message("You need a Prayer level of " + requiredPrayerLevel + " to bless this staff.");
			return;
		}

		final int resourceCost = getStaffResourceCost(item.getCatalogId());
		final int devotionRequirement = Devotion.getDevotionRequirementForResourceCost(resourceCost);
		final int currentDevotion = Devotion.getDevotionLevel(player, godLine);
		if (devotionRequirement > 0 && currentDevotion < devotionRequirement) {
			player.message("You need " + devotionRequirement + " devotion to " + formatGodLine(godLine) + " to bless this staff.");
			player.message("Your current devotion to " + formatGodLine(godLine) + " is " + currentDevotion + ".");
			return;
		}

		if (player.getCarriedItems().remove(item) == -1) {
			return;
		}

		give(player, productId, 1);
		final int prayerXp = Devotion.getBlessingPrayerXp(player, godLine, getStaffCraftingXp(item.getCatalogId()));
		if (prayerXp > 0) {
			player.incExp(Skill.PRAYER.id(), prayerXp, true);
		}
		player.message("You bless the staff at the altar.");
	}

	private String formatGodLine(final PrayerCatalog.GodLine godLine) {
		final String lower = godLine.name().toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private int getStaffResourceCost(final int itemId) {
		final int tier = EnchantingItemEffects.getTierForBaseStaff(itemId);
		return tier > 0 ? tier : 0;
	}

	private int getStaffCraftingXp(final int itemId) {
		switch (itemId) {
			case 100: // ItemId.STAFF
				return 12;
			case 2131: // ItemId.PINE_STAFF
				return 20;
			case 1764: // ItemId.OAK_STAFF
				return 28;
			case 1769: // ItemId.WILLOW_STAFF
				return 37;
			case 2136: // ItemId.PALM_STAFF
				return 46;
			case 1774: // ItemId.MAPLE_STAFF
				return 57;
			case 1779: // ItemId.YEW_STAFF
				return 68;
			case 2141: // ItemId.EBONY_STAFF
				return 80;
			case 1784: // ItemId.MAGIC_WOOD_STAFF
				return 92;
			case 2146: // ItemId.BLOOD_STAFF
				return 120;
			default:
				return 0;
		}
	}

	private int getBlessedStaffProduct(final int itemId) {
		return getBlessedStaffProduct(PrayerCatalog.GodLine.ZAMORAK, itemId);
	}

	private int getBlessedStaffProduct(final PrayerCatalog.GodLine godLine, final int itemId) {
		final int tierIndex = getBaseStaffTierIndex(itemId);
		if (tierIndex == -1) {
			return -1;
		}
		if (godLine == PrayerCatalog.GodLine.ZAMORAK) {
			return ItemId.BLESSED_STAFF.id() + tierIndex;
		}
		if (godLine == PrayerCatalog.GodLine.SARADOMIN) {
			return ItemId.SARADOMIN_BLESSED_STAFF.id() + tierIndex;
		}
		if (godLine == PrayerCatalog.GodLine.GUTHIX) {
			return ItemId.GUTHIX_BLESSED_STAFF.id() + tierIndex;
		}
		return -1;
	}

	private int getBaseStaffTierIndex(final int itemId) {
		switch (itemId) {
			case 100: // ItemId.STAFF
				return 0;
			case 2131: // ItemId.PINE_STAFF
				return 1;
			case 1764: // ItemId.OAK_STAFF
				return 2;
			case 1769: // ItemId.WILLOW_STAFF
				return 3;
			case 2136: // ItemId.PALM_STAFF
				return 4;
			case 1774: // ItemId.MAPLE_STAFF
				return 5;
			case 1779: // ItemId.YEW_STAFF
				return 6;
			case 2141: // ItemId.EBONY_STAFF
				return 7;
			case 1784: // ItemId.MAGIC_WOOD_STAFF
				return 8;
			case 2146: // ItemId.BLOOD_STAFF
				return 9;
			default:
				return -1;
		}
	}
}
