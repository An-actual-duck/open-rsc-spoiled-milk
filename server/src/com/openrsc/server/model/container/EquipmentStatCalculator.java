package com.openrsc.server.model.container;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.content.WoolRobeDefense;

/** Pure definition-table and arithmetic calculations used by {@link Equipment}. */
final class EquipmentStatCalculator {
	private static final int[] BLESSED_STAFF_HOLY_POWER = {
		8, 12, 16, 24, 28, 32, 40, 44, 48, 56
	};
	private EquipmentStatCalculator() {
	}

	static int displayedOffense(int rawOffense, int bonus, int penalty) {
		return rawOffense + Math.max(0, bonus) - penalty;
	}

	static int combatOffense(int displayedOffense) {
		return Math.max(0, displayedOffense);
	}

	static int armorPowerPenalty(int penalizedPieces, int penaltyPerPiece) {
		return penalizedPieces * penaltyPerPiece;
	}

	static int holyPowerForItem(final int itemId) {
		if (itemId >= ItemId.BLESSED_STAFF.id()
			&& itemId <= ItemId.BLESSED_BLOOD_STAFF.id()) {
			return blessedStaffHolyPower(itemId - ItemId.BLESSED_STAFF.id());
		}
		if (itemId >= ItemId.SARADOMIN_BLESSED_STAFF.id()
			&& itemId <= ItemId.SARADOMIN_BLESSED_BLOOD_STAFF.id()) {
			return blessedStaffHolyPower(itemId - ItemId.SARADOMIN_BLESSED_STAFF.id());
		}
		if (itemId >= ItemId.GUTHIX_BLESSED_STAFF.id()
			&& itemId <= ItemId.GUTHIX_BLESSED_BLOOD_STAFF.id()) {
			return blessedStaffHolyPower(itemId - ItemId.GUTHIX_BLESSED_STAFF.id());
		}
		switch (itemId) {
			case 1216: // ItemId.STAFF_OF_ZAMORAK (switch labels must be constants)
			case 1217: // ItemId.STAFF_OF_GUTHIX
			case 1218: // ItemId.STAFF_OF_SARADOMIN
				return 64;
			default:
				return 0;
		}
	}

	/**
	 * Holy Power follows the same current-skill-plus-equipment model as the
	 * other combat power values. Worship is internally retained as Prayer for
	 * skill/protocol compatibility.
	 */
	static int combinedHolyPower(final int worshipLevel, final int staffHolyPower) {
		return Math.max(0, worshipLevel) + Math.max(0, staffHolyPower);
	}

	private static int blessedStaffHolyPower(final int tierIndex) {
		return tierIndex >= 0 && tierIndex < BLESSED_STAFF_HOLY_POWER.length
			? BLESSED_STAFF_HOLY_POWER[tierIndex] : 0;
	}

	static int godEquipmentNaturalPrayerBonus(final int itemId) {
		switch (itemId) {
			case 430: // BLACK_MACE
			case 2157: // WHITE_MACE
			case 3119: // GREY_MACE
			case 433: // BLACK_KITE_SHIELD
			case 2162: // WHITE_KITE_SHIELD
			case 3124: // GREY_KITE_SHIELD
				return 5;
			default:
				return 0;
		}
	}

	static int godEquipmentResourceCost(final int itemId) {
		switch (itemId) {
			case 423: // BLACK_DAGGER
			case 565: // POISONED_BLACK_DAGGER
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
			case 3229: // BLACK_SPEAR
			case 3230: // WHITE_SPEAR
			case 3231: // GREY_SPEAR
			case 3140: // ZAMORAK_WOOL_GLOVES
			case 3141: // ZAMORAK_WOOL_BOOTS
			case 3145: // SARADOMIN_WOOL_GLOVES
			case 3146: // SARADOMIN_WOOL_BOOTS
			case 3150: // GUTHIX_WOOL_GLOVES
			case 3151: // GUTHIX_WOOL_BOOTS
				return 2;
			case 426: // BLACK_2_HANDED_SWORD
			case 429: // BLACK_BATTLE_AXE
			case 432: // BLACK_SQUARE_SHIELD
			case 433: // BLACK_KITE_SHIELD
			case 248: // BLACK_PLATE_MAIL_LEGS
			case 2154: // WHITE_2_HANDED_SWORD
			case 2156: // WHITE_BATTLE_AXE
			case 2161: // WHITE_SQUARE_SHIELD
			case 2162: // WHITE_KITE_SHIELD
			case 2164: // WHITE_PLATE_MAIL_LEGS
			case 3116: // GREY_2_HANDED_SWORD
			case 3118: // GREY_BATTLE_AXE
			case 3123: // GREY_SQUARE_SHIELD
			case 3124: // GREY_KITE_SHIELD
			case 3126: // GREY_PLATE_MAIL_LEGS
			case 3232: // BLACK_SCYTHE
			case 3233: // WHITE_SCYTHE
			case 3234: // GREY_SCYTHE
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

	static int godEquipmentTargetWeaponAim(final int itemId) {
		switch (itemId) {
			case 423: case 565: case 2151: case 3113:
				return 15;
			case 424: case 2152: case 3114:
				return 25;
			case 425: case 2153: case 3115:
				return 31;
			case 426: case 2154: case 3116:
				return 44;
			case 427: case 2155: case 3117:
				return 28;
			case 429: case 2156: case 3118:
				return 30;
			case 430: case 2157: case 3119:
				return 24;
			case 3229: case 3230: case 3231:
				return 24;
			case 3232: case 3233: case 3234:
				return 99;
			default:
				return 0;
		}
	}

	static int godEquipmentTargetWeaponPower(final int itemId) {
		switch (itemId) {
			case 423: case 565: case 2151: case 3113:
				return 15;
			case 424: case 2152: case 3114:
				return 25;
			case 425: case 2153: case 3115:
				return 31;
			case 426: case 2154: case 3116:
				return 44;
			case 427: case 2155: case 3117:
				return 28;
			case 429: case 2156: case 3118:
				return 41;
			case 430: case 2157: case 3119:
				return 18;
			case 3229: case 3230: case 3231:
				return 14;
			case 3232: case 3233: case 3234:
				return 99;
			default:
				return 0;
		}
	}

	static int godEquipmentTargetArmour(final int itemId) {
		switch (itemId) {
			case 230: case 2158: case 3120:
				return 19;
			case 196: case 2163: case 3125:
				return 63;
			case 248: case 2164: case 3126:
				return 31;
			case 432: case 2161: case 3123:
				return 29;
			case 433: case 2162: case 3124:
				return 24;
			case 3131: case 3133: case 3135:
			case 3132: case 3134: case 3136:
				return 12;
			default:
				return 0;
		}
	}

	static int godEquipmentTargetMeleeOffense(final int itemId) {
		switch (itemId) {
			case 423: case 565: case 2151: case 3113:
				return 15;
			case 424: case 2152: case 3114:
				return 32;
			case 425: case 2153: case 3115:
				return 60;
			case 426: case 2154: case 3116:
				return 130;
			case 427: case 2155: case 3117:
				return 31;
			case 429: case 2156: case 3118:
				return 62;
			case 430: case 2157: case 3119:
				return 40;
			case 3229: case 3230: case 3231:
				return 38;
			case 3232: case 3233: case 3234:
				return 99;
			default:
				return 0;
		}
	}

	static int godEquipmentTargetMeleeDefense(final int itemId) {
		switch (itemId) {
			case 230: case 2158: case 3120:
			case 432: case 2161: case 3123:
			case 3131: case 3133: case 3135:
			case 3132: case 3134: case 3136:
				return 12;
			case 196: case 2163: case 3125:
				return 30;
			case 248: case 2164: case 3126:
			case 433: case 2162: case 3124:
				return 18;
			default:
				return 0;
		}
	}

	static int godEquipmentTargetRangedDefense(final int itemId) {
		switch (itemId) {
			case 230: case 2158: case 3120:
			case 432: case 2161: case 3123:
			case 3131: case 3133: case 3135:
			case 3132: case 3134: case 3136:
				return 4;
			case 196: case 2163: case 3125:
				return 10;
			case 248: case 2164: case 3126:
				return 6;
			default:
				return 0;
		}
	}

	static int godEquipmentTargetMagicDefense(final int itemId) {
		switch (itemId) {
			case 433: case 2162: case 3124:
				return 6;
			default:
				return 0;
		}
	}

	static int godEquipmentTargetMagic(final int itemId) {
		return 0;
	}

	static int blessedWoolBaseMagicDefense(final int itemId) {
		switch (itemId) {
			case 3137: case 3142: case 3147:
				return 1;
			case 3138: case 3143: case 3148:
				return 4;
			case 3139: case 3144: case 3149:
				return 3;
			case 3140: case 3141: case 3145:
			case 3146: case 3150: case 3151:
				return 2;
			default:
				return 0;
		}
	}

	static int blessedWoolTargetMagicDefense(final int itemId) {
		final int resourceCost = godEquipmentResourceCost(itemId);
		return resourceCost > 0
			? Math.max(blessedWoolBaseMagicDefense(itemId),
				WoolRobeDefense.budget(9, resourceCost))
			: 0;
	}

	static int legacyRangedOffense(final int catalogId) {
		switch (ItemId.getById(catalogId)) {
			case BRONZE_THROWING_DART:
			case POISONED_BRONZE_THROWING_DART:
			case BRONZE_ARROWS:
			case POISON_BRONZE_ARROWS:
				return 15;
			case IRON_THROWING_DART:
			case POISONED_IRON_THROWING_DART:
				return 17;
			case IRON_ARROWS:
			case POISON_IRON_ARROWS:
			case CROSSBOW_BOLTS:
			case POISON_CROSSBOW_BOLTS:
				return 20;
			case STEEL_THROWING_DART:
			case POISONED_STEEL_THROWING_DART:
				return 22;
			case STEEL_ARROWS:
			case POISON_STEEL_ARROWS:
			case MITHRIL_THROWING_DART:
			case POISONED_MITHRIL_THROWING_DART:
			case BRONZE_THROWING_KNIFE:
			case POISONED_BRONZE_THROWING_KNIFE:
				return 25;
			case ADAMANTITE_THROWING_DART:
			case POISONED_ADAMANTITE_THROWING_DART:
				return 27;
			case RUNE_THROWING_DART:
			case POISONED_RUNE_THROWING_DART:
			case MITHRIL_ARROWS:
			case POISON_MITHRIL_ARROWS:
			case IRON_THROWING_KNIFE:
			case POISONED_IRON_THROWING_KNIFE:
				return 30;
			case ADAMANTITE_ARROWS:
			case POISON_ADAMANTITE_ARROWS:
			case STEEL_THROWING_KNIFE:
			case POISONED_STEEL_THROWING_KNIFE:
			case BLACK_THROWING_KNIFE:
			case POISONED_BLACK_THROWING_KNIFE:
				return 35;
			case RUNE_ARROWS:
			case POISON_RUNE_ARROWS:
			case MITHRIL_THROWING_KNIFE:
			case POISONED_MITHRIL_THROWING_KNIFE:
				return 40;
			case ADAMANTITE_THROWING_KNIFE:
			case POISONED_ADAMANTITE_THROWING_KNIFE:
				return 45;
			case RUNE_THROWING_KNIFE:
			case POISONED_RUNE_THROWING_KNIFE:
			case DRAGON_ARROWS:
			case POISON_DRAGON_ARROWS:
			case DRAGON_BOLTS:
			case POISON_DRAGON_BOLTS:
				return 50;
			case SHORTBOW:
				return 14;
			case LONGBOW:
				return 20;
			case CROSSBOW:
			case PHOENIX_CROSSBOW:
				return 22;
			default:
				return 0;
		}
	}
}
