package com.openrsc.server.plugins.custom.myworld.skills.prayer;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
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

		if (PrayerCatalog.getGodLineForAltar(obj.getID(), obj.getX(), obj.getY()) == null) {
			return;
		}

		final int productId = getBlessedStaffProduct(item.getCatalogId());
		if (productId == -1) {
			return;
		}

		final int requiredPrayerLevel = EnchantingItemEffects.getTemporaryEnchantingRequirementForTier(
			EnchantingItemEffects.getTierForBaseStaff(item.getCatalogId()));
		if (requiredPrayerLevel == -1 || player.getSkills().getLevel(Skill.PRAYER.id()) < requiredPrayerLevel) {
			player.message("You need a Prayer level of " + requiredPrayerLevel + " to bless this staff.");
			return;
		}

		if (player.getCarriedItems().remove(item) == -1) {
			return;
		}

		give(player, productId, 1);
		player.message("You bless the staff at the altar.");
	}

	private int getBlessedStaffProduct(final int itemId) {
		switch (itemId) {
			case 100: // ItemId.STAFF
				return ItemId.BLESSED_STAFF.id();
			case 2131: // ItemId.PINE_STAFF
				return ItemId.BLESSED_PINE_STAFF.id();
			case 1764: // ItemId.OAK_STAFF
				return ItemId.BLESSED_OAK_STAFF.id();
			case 1769: // ItemId.WILLOW_STAFF
				return ItemId.BLESSED_WILLOW_STAFF.id();
			case 2136: // ItemId.PALM_STAFF
				return ItemId.BLESSED_PALM_STAFF.id();
			case 1774: // ItemId.MAPLE_STAFF
				return ItemId.BLESSED_MAPLE_STAFF.id();
			case 1779: // ItemId.YEW_STAFF
				return ItemId.BLESSED_YEW_STAFF.id();
			case 2141: // ItemId.EBONY_STAFF
				return ItemId.BLESSED_EBONY_STAFF.id();
			case 1784: // ItemId.MAGIC_WOOD_STAFF
				return ItemId.BLESSED_MAGIC_STAFF.id();
			case 2146: // ItemId.BLOOD_STAFF
				return ItemId.BLESSED_BLOOD_STAFF.id();
			default:
				return -1;
		}
	}
}
