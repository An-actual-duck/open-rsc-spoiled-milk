package com.openrsc.server.plugins.custom.myworld.skills.prayer;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Devotion;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.plugins.triggers.UseLocTrigger;

public final class DestroyOpposingBlessedObject implements UseLocTrigger {
	private static final int DEVOTION_CHANGE_PER_RESOURCE = 1;
	private static final int PRAYER_XP_MULTIPLIER = 5;

	@Override
	public boolean blockUseLoc(final Player player, final GameObject obj, final Item item) {
		if (item == null || item.getNoted()) {
			return false;
		}
		final PrayerCatalog.GodLine altarGod = PrayerCatalog.getGodLineForAltar(obj.getID(), obj.getX(), obj.getY());
		final PrayerCatalog.GodLine itemGod = getBlessedObjectGodLine(item.getCatalogId());
		return altarGod != null && itemGod != null && itemGod != altarGod;
	}

	@Override
	public void onUseLoc(final Player player, final GameObject obj, final Item item) {
		if (item == null || item.getNoted()) {
			return;
		}

		final PrayerCatalog.GodLine altarGod = PrayerCatalog.getGodLineForAltar(obj.getID(), obj.getX(), obj.getY());
		final PrayerCatalog.GodLine worshippedGod = player.getPrayerBook() == null
			? PrayerCatalog.getDefaultGodLine()
			: player.getPrayerBook();
		final PrayerCatalog.GodLine itemGod = getBlessedObjectGodLine(item.getCatalogId());
		if (altarGod == null || itemGod == null || itemGod == altarGod) {
			return;
		}

		if (altarGod != worshippedGod) {
			player.message("You must worship " + formatGodLine(altarGod) + " to destroy opposing blessed objects here.");
			return;
		}

		final int resourceCost = getBlessedObjectResourceCost(item.getCatalogId());
		final int productionXp = getBlessedObjectProductionXp(item.getCatalogId());
		if (resourceCost <= 0 || productionXp <= 0) {
			player.message("The altar does not recognise that blessed object.");
			return;
		}

		if (player.getCarriedItems().remove(item) == -1) {
			return;
		}

		final int devotionChange = resourceCost * DEVOTION_CHANGE_PER_RESOURCE;
		Devotion.addDevotionLevels(player, worshippedGod, devotionChange);
		Devotion.removeDevotionLevels(player, itemGod, devotionChange);
		player.incExp(Skill.PRAYER.id(), productionXp * PRAYER_XP_MULTIPLIER, true);
		player.message("The altar destroys the opposing blessed object.");
		player.message("You gain " + devotionChange + " devotion to " + formatGodLine(worshippedGod) + ".");
		player.message("You lose " + devotionChange + " devotion to " + formatGodLine(itemGod) + ".");
	}

	private PrayerCatalog.GodLine getBlessedObjectGodLine(final int itemId) {
		if (isZamorakKnightEquipment(itemId) || isZamorakBlessedWool(itemId) || isZamorakBlessedStaff(itemId)) {
			return PrayerCatalog.GodLine.ZAMORAK;
		}
		if (isSaradominKnightEquipment(itemId) || isSaradominBlessedWool(itemId) || isSaradominBlessedStaff(itemId)) {
			return PrayerCatalog.GodLine.SARADOMIN;
		}
		if (isGuthixKnightEquipment(itemId) || isGuthixBlessedWool(itemId) || isGuthixBlessedStaff(itemId)) {
			return PrayerCatalog.GodLine.GUTHIX;
		}
		return null;
	}

	private int getBlessedObjectResourceCost(final int itemId) {
		final int staffTier = getBlessedStaffTier(itemId);
		if (staffTier > 0) {
			return staffTier;
		}
		switch (itemId) {
			case 423: // BLACK_DAGGER
			case 424: // BLACK_SHORT_SWORD
			case 430: // BLACK_MACE
			case 2151: // WHITE_DAGGER
			case 2152: // WHITE_SHORT_SWORD
			case 2157: // WHITE_MACE
			case 3113: // GREY_DAGGER
			case 3114: // GREY_SHORT_SWORD
			case 3119: // GREY_MACE
			case 3137: // ZAMORAK_WOOL_HAT
			case 3142: // SARADOMIN_WOOL_HAT
			case 3147: // GUTHIX_WOOL_HAT
				return 1;
			case 425: // BLACK_LONG_SWORD
			case 427: // BLACK_SCIMITAR
			case 230: // LARGE_BLACK_HELMET
			case 3131: // BLACK_GAUNTLETS
			case 3132: // BLACK_GREAVES
			case 2153: // WHITE_LONG_SWORD
			case 2155: // WHITE_SCIMITAR
			case 2158: // LARGE_WHITE_HELMET
			case 3133: // WHITE_GAUNTLETS
			case 3134: // WHITE_GREAVES
			case 3115: // GREY_LONG_SWORD
			case 3117: // GREY_SCIMITAR
			case 3120: // LARGE_GREY_HELMET
			case 3135: // GREY_GAUNTLETS
			case 3136: // GREY_GREAVES
			case 3140: // ZAMORAK_WOOL_GLOVES
			case 3141: // ZAMORAK_WOOL_BOOTS
			case 3145: // SARADOMIN_WOOL_GLOVES
			case 3146: // SARADOMIN_WOOL_BOOTS
			case 3150: // GUTHIX_WOOL_GLOVES
			case 3151: // GUTHIX_WOOL_BOOTS
				return 2;
			case 426: // BLACK_2_HANDED_SWORD
			case 429: // BLACK_BATTLE_AXE
			case 433: // BLACK_KITE_SHIELD
			case 248: // BLACK_PLATE_MAIL_LEGS
			case 2154: // WHITE_2_HANDED_SWORD
			case 2156: // WHITE_BATTLE_AXE
			case 2162: // WHITE_KITE_SHIELD
			case 2164: // WHITE_PLATE_MAIL_LEGS
			case 3116: // GREY_2_HANDED_SWORD
			case 3118: // GREY_BATTLE_AXE
			case 3124: // GREY_KITE_SHIELD
			case 3126: // GREY_PLATE_MAIL_LEGS
			case 3139: // ZAMORAK_WOOL_ROBE_BOTTOM
			case 3144: // SARADOMIN_WOOL_ROBE_BOTTOM
			case 3149: // GUTHIX_WOOL_ROBE_BOTTOM
				return 3;
			case 196: // BLACK_PLATE_MAIL_BODY
			case 2163: // WHITE_PLATE_MAIL_BODY
			case 3125: // GREY_PLATE_MAIL_BODY
			case 3138: // ZAMORAK_WOOL_ROBE_TOP
			case 3143: // SARADOMIN_WOOL_ROBE_TOP
			case 3148: // GUTHIX_WOOL_ROBE_TOP
				return 4;
			default:
				return 0;
		}
	}

	private int getBlessedObjectProductionXp(final int itemId) {
		final int staffTier = getBlessedStaffTier(itemId);
		if (staffTier > 0) {
			return getStaffProductionXp(staffTier);
		}
		if (isBlessedWool(itemId)) {
			return getBlessedObjectResourceCost(itemId) * 6;
		}
		return getBlessedObjectResourceCost(itemId) * 150;
	}

	private int getStaffProductionXp(final int staffTier) {
		switch (staffTier) {
			case 1:
				return 12;
			case 2:
				return 20;
			case 3:
				return 28;
			case 4:
				return 37;
			case 5:
				return 46;
			case 6:
				return 57;
			case 7:
				return 68;
			case 8:
				return 80;
			case 9:
				return 92;
			case 10:
				return 120;
			default:
				return 0;
		}
	}

	private int getBlessedStaffTier(final int itemId) {
		if (isZamorakBlessedStaff(itemId)) {
			return itemId - ItemId.BLESSED_STAFF.id() + 1;
		}
		if (isSaradominBlessedStaff(itemId)) {
			return itemId - ItemId.SARADOMIN_BLESSED_STAFF.id() + 1;
		}
		if (isGuthixBlessedStaff(itemId)) {
			return itemId - ItemId.GUTHIX_BLESSED_STAFF.id() + 1;
		}
		return 0;
	}

	private boolean isZamorakKnightEquipment(final int itemId) {
		switch (itemId) {
			case 423: // BLACK_DAGGER
			case 424: // BLACK_SHORT_SWORD
			case 425: // BLACK_LONG_SWORD
			case 426: // BLACK_2_HANDED_SWORD
			case 427: // BLACK_SCIMITAR
			case 429: // BLACK_BATTLE_AXE
			case 430: // BLACK_MACE
			case 230: // LARGE_BLACK_HELMET
			case 433: // BLACK_KITE_SHIELD
			case 196: // BLACK_PLATE_MAIL_BODY
			case 248: // BLACK_PLATE_MAIL_LEGS
			case 3131: // BLACK_GAUNTLETS
			case 3132: // BLACK_GREAVES
				return true;
			default:
				return false;
		}
	}

	private boolean isSaradominKnightEquipment(final int itemId) {
		switch (itemId) {
			case 2151: // WHITE_DAGGER
			case 2152: // WHITE_SHORT_SWORD
			case 2153: // WHITE_LONG_SWORD
			case 2154: // WHITE_2_HANDED_SWORD
			case 2155: // WHITE_SCIMITAR
			case 2156: // WHITE_BATTLE_AXE
			case 2157: // WHITE_MACE
			case 2158: // LARGE_WHITE_HELMET
			case 2162: // WHITE_KITE_SHIELD
			case 2163: // WHITE_PLATE_MAIL_BODY
			case 2164: // WHITE_PLATE_MAIL_LEGS
			case 3133: // WHITE_GAUNTLETS
			case 3134: // WHITE_GREAVES
				return true;
			default:
				return false;
		}
	}

	private boolean isGuthixKnightEquipment(final int itemId) {
		switch (itemId) {
			case 3113: // GREY_DAGGER
			case 3114: // GREY_SHORT_SWORD
			case 3115: // GREY_LONG_SWORD
			case 3116: // GREY_2_HANDED_SWORD
			case 3117: // GREY_SCIMITAR
			case 3118: // GREY_BATTLE_AXE
			case 3119: // GREY_MACE
			case 3120: // LARGE_GREY_HELMET
			case 3124: // GREY_KITE_SHIELD
			case 3125: // GREY_PLATE_MAIL_BODY
			case 3126: // GREY_PLATE_MAIL_LEGS
			case 3135: // GREY_GAUNTLETS
			case 3136: // GREY_GREAVES
				return true;
			default:
				return false;
		}
	}

	private boolean isZamorakBlessedStaff(final int itemId) {
		return itemId >= ItemId.BLESSED_STAFF.id() && itemId <= ItemId.BLESSED_BLOOD_STAFF.id();
	}

	private boolean isSaradominBlessedStaff(final int itemId) {
		return itemId >= ItemId.SARADOMIN_BLESSED_STAFF.id() && itemId <= ItemId.SARADOMIN_BLESSED_BLOOD_STAFF.id();
	}

	private boolean isGuthixBlessedStaff(final int itemId) {
		return itemId >= ItemId.GUTHIX_BLESSED_STAFF.id() && itemId <= ItemId.GUTHIX_BLESSED_BLOOD_STAFF.id();
	}

	private boolean isBlessedWool(final int itemId) {
		return isZamorakBlessedWool(itemId) || isSaradominBlessedWool(itemId) || isGuthixBlessedWool(itemId);
	}

	private boolean isZamorakBlessedWool(final int itemId) {
		return itemId >= ItemId.ZAMORAK_WOOL_HAT.id() && itemId <= ItemId.ZAMORAK_WOOL_BOOTS.id();
	}

	private boolean isSaradominBlessedWool(final int itemId) {
		return itemId >= ItemId.SARADOMIN_WOOL_HAT.id() && itemId <= ItemId.SARADOMIN_WOOL_BOOTS.id();
	}

	private boolean isGuthixBlessedWool(final int itemId) {
		return itemId >= ItemId.GUTHIX_WOOL_HAT.id() && itemId <= ItemId.GUTHIX_WOOL_BOOTS.id();
	}

	private String formatGodLine(final PrayerCatalog.GodLine godLine) {
		final String lower = godLine.name().toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
