package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.MessageType;

public final class Devotion {
	private static final String CACHE_PREFIX = "devotion_";
	private static final String CACHE_SUFFIX = "_offerings";
	private static final String SYMBOL_BONUS_SUFFIX = "_symbol_bonus_toggle";
	private static final String BLACK_UNICORN_BONUS_SUFFIX = "_black_unicorn_bonus_toggle";
	private static final int OFFERINGS_PER_BONUS_XP = 10;
	public static final int OFFERINGS_PER_DEVOTION_LEVEL = OFFERINGS_PER_BONUS_XP;
	public static final int MAX_DEVOTION_LEVEL = 1000;
	public static final int MIN_DEVOTION_LEVEL = -1000;
	public static final int COMBAT_GROWTH_START_LEVEL = 250;
	private static final int MAX_OFFERINGS = MAX_DEVOTION_LEVEL * OFFERINGS_PER_DEVOTION_LEVEL;
	private static final int MIN_OFFERINGS = MIN_DEVOTION_LEVEL * OFFERINGS_PER_DEVOTION_LEVEL;
	private static final int DEVOTION_REQUIREMENT_PER_RESOURCE = 100;
	private static final int BLESSING_OFFERING_COST_PER_RESOURCE = OFFERINGS_PER_DEVOTION_LEVEL / 2;
	private static final int PRAYER_BONUS_GROWTH_MAX = 10;

	private Devotion() {
	}

	public static int recordOfferingAndGetPrayerXpBonus(final Player player) {
		return recordOfferingAndGetPrayerXpBonus(player, false);
	}

	public static int recordBlackUnicornOfferingAndGetPrayerXpBonus(final Player player) {
		return recordOfferingAndGetPrayerXpBonus(player, true);
	}

	public static void awardOfferingPrayerXpBonus(final Player player, final int skillId, final int devotionBonusXp) {
		if (player == null || devotionBonusXp <= 0 || skillId < 0 || player.isExperienceFrozen()) {
			return;
		}
		if (player.getWorld().getServer().getConfig().WANT_FATIGUE && player.getFatigue() >= player.MAX_FATIGUE) {
			return;
		}
		if (player.getConfig().WANT_OPENPK_POINTS) {
			player.addOpenPkPoints(devotionBonusXp);
			return;
		}
		player.getSkills().addExperience(skillId, devotionBonusXp);
	}

	private static int recordOfferingAndGetPrayerXpBonus(final Player player, final boolean blackUnicornBonus) {
		if (player == null || !player.getConfig().WANT_MYWORLD) {
			return 0;
		}

		final PrayerCatalog.GodLine godLine = player.getPrayerBook();
		final String cacheKey = getOfferingCacheKey(godLine);
		final int previousOfferings = player.getCache().hasKey(cacheKey) ? player.getCache().getInt(cacheKey) : 0;
		final int previousDevotion = getDevotionLevelFromOfferings(previousOfferings);
		final int bonusXp = Math.max(0, Math.min(previousDevotion, MAX_DEVOTION_LEVEL));
		final int offeringGain = getOfferingDevotionGain(player, godLine);
		final int blackUnicornOfferingGain = blackUnicornBonus ? getEveryOtherOfferingBonus(player, godLine, BLACK_UNICORN_BONUS_SUFFIX) : 0;
		final int newOfferings = clampOfferings((long) previousOfferings + offeringGain + blackUnicornOfferingGain);
		player.getCache().set(cacheKey, newOfferings);
		ActionSender.sendDevotion(player);
		ActionSender.sendEquipmentStats(player);

		final int newDevotion = getDevotionLevelFromOfferings(newOfferings);
		if (newDevotion > previousDevotion) {
			sendDevotionIncreaseMessage(player, godLine, newDevotion);
		}
		return bonusXp * 4;
	}

	public static int getOfferings(final Player player, final PrayerCatalog.GodLine godLine) {
		if (player == null || godLine == null) {
			return 0;
		}
		final String cacheKey = getOfferingCacheKey(godLine);
		final int offerings = player.getCache().hasKey(cacheKey) ? player.getCache().getInt(cacheKey) : 0;
		return clampOfferings(offerings);
	}

	public static int getDevotionLevel(final Player player, final PrayerCatalog.GodLine godLine) {
		return getDevotionLevelFromOfferings(getOfferings(player, godLine));
	}

	public static int getCurrentDevotionLevel(final Player player) {
		if (player == null) {
			return 0;
		}
		return getDevotionLevel(player, player.getPrayerBook());
	}

	public static void setDevotionLevel(final Player player, final PrayerCatalog.GodLine godLine, final int devotionLevel) {
		if (player == null || godLine == null || !player.getConfig().WANT_MYWORLD) {
			return;
		}
		final int clampedDevotionLevel = clampDevotionLevel(devotionLevel);
		player.getCache().set(getOfferingCacheKey(godLine), clampedDevotionLevel * OFFERINGS_PER_DEVOTION_LEVEL);
		ActionSender.sendDevotion(player);
		ActionSender.sendEquipmentStats(player);
		player.getPrayers().deactivateOverflowingPrayers();
	}

	public static void addDevotionLevels(final Player player, final PrayerCatalog.GodLine godLine, final int devotionLevels) {
		if (player == null || godLine == null || devotionLevels <= 0 || !player.getConfig().WANT_MYWORLD) {
			return;
		}
		adjustDevotionLevels(player, godLine, devotionLevels);
	}

	public static void removeDevotionLevels(final Player player, final PrayerCatalog.GodLine godLine, final int devotionLevels) {
		if (player == null || godLine == null || devotionLevels <= 0 || !player.getConfig().WANT_MYWORLD) {
			return;
		}
		adjustDevotionLevels(player, godLine, -devotionLevels);
	}

	public static void addDevotionOfferings(final Player player, final PrayerCatalog.GodLine godLine, final int offerings) {
		if (player == null || godLine == null || offerings <= 0 || !player.getConfig().WANT_MYWORLD) {
			return;
		}
		adjustDevotionOfferings(player, godLine, offerings);
	}

	public static void removeDevotionOfferings(final Player player, final PrayerCatalog.GodLine godLine, final int offerings) {
		if (player == null || godLine == null || offerings <= 0 || !player.getConfig().WANT_MYWORLD) {
			return;
		}
		adjustDevotionOfferings(player, godLine, -offerings);
	}

	public static void adjustDevotionLevels(final Player player, final PrayerCatalog.GodLine godLine, final int devotionLevels) {
		if (player == null || godLine == null || devotionLevels == 0 || !player.getConfig().WANT_MYWORLD) {
			return;
		}
		final String cacheKey = getOfferingCacheKey(godLine);
		final int previousOfferings = player.getCache().hasKey(cacheKey) ? player.getCache().getInt(cacheKey) : 0;
		final int newOfferings = clampOfferings((long) previousOfferings + ((long) devotionLevels * OFFERINGS_PER_DEVOTION_LEVEL));
		player.getCache().set(cacheKey, newOfferings);
		ActionSender.sendDevotion(player);
		ActionSender.sendEquipmentStats(player);
		if (newOfferings < previousOfferings) {
			player.getPrayers().deactivateOverflowingPrayers();
		}
	}

	/**
	 * Adjusts stored offering units and returns the signed change that survived
	 * clamping. One displayed Devotion level is {@link #OFFERINGS_PER_DEVOTION_LEVEL}
	 * offering units.
	 */
	public static int adjustDevotionOfferings(final Player player, final PrayerCatalog.GodLine godLine, final int offerings) {
		if (player == null || godLine == null || offerings == 0 || !player.getConfig().WANT_MYWORLD) {
			return 0;
		}
		final String cacheKey = getOfferingCacheKey(godLine);
		final int previousOfferings = player.getCache().hasKey(cacheKey) ? player.getCache().getInt(cacheKey) : 0;
		final int newOfferings = clampOfferings((long) previousOfferings + offerings);
		player.getCache().set(cacheKey, newOfferings);
		ActionSender.sendDevotion(player);
		ActionSender.sendEquipmentStats(player);
		if (newOfferings < previousOfferings) {
			player.getPrayers().deactivateOverflowingPrayers();
		}
		return newOfferings - previousOfferings;
	}

	public static int getDevotionRequirementForResourceCost(final int resourceCost) {
		return resourceCost > 0 ? clampPositiveInt((long) resourceCost * DEVOTION_REQUIREMENT_PER_RESOURCE) : 0;
	}

	public static int getBlessingOfferingCostForResourceCost(final int resourceCost) {
		return resourceCost > 0 ? clampPositiveInt((long) resourceCost * BLESSING_OFFERING_COST_PER_RESOURCE) : 0;
	}

	public static int getBlessingPrayerXp(final Player player, final PrayerCatalog.GodLine godLine, final int basePrayerXp) {
		if (player == null || godLine == null || basePrayerXp <= 0) {
			return 0;
		}
		final int devotionLevel = getDevotionLevel(player, godLine);
		final double scaledXp = basePrayerXp * ((100.0D + devotionLevel) / 100.0D);
		if (scaledXp <= 0.0D) {
			return 0;
		}
		return scaledXp >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.ceil(scaledXp);
	}

	public static int getDevotionGrowthBonus(final Player player, final PrayerCatalog.GodLine godLine, final int maxGrowthBonus) {
		if (player == null || godLine == null || maxGrowthBonus <= 0) {
			return 0;
		}
		final int devotionLevel = getDevotionLevel(player, godLine);
		if (devotionLevel <= COMBAT_GROWTH_START_LEVEL) {
			return 0;
		}
		final int growthRange = MAX_DEVOTION_LEVEL - COMBAT_GROWTH_START_LEVEL;
		final int growthProgress = Math.min(devotionLevel - COMBAT_GROWTH_START_LEVEL, growthRange);
		return Math.min(maxGrowthBonus, (int) Math.floor(maxGrowthBonus * (growthProgress / (double) growthRange)));
	}

	public static int getPrayerBonusGrowth(final Player player, final PrayerCatalog.GodLine godLine) {
		return getDevotionGrowthBonus(player, godLine, PRAYER_BONUS_GROWTH_MAX);
	}

	private static int getOfferingDevotionGain(final Player player, final PrayerCatalog.GodLine godLine) {
		if (!hasBlessedSymbolEquipped(player, godLine)) {
			return 1;
		}
		return 1 + getEveryOtherOfferingBonus(player, godLine, SYMBOL_BONUS_SUFFIX);
	}

	private static int getEveryOtherOfferingBonus(final Player player, final PrayerCatalog.GodLine godLine, final String suffix) {
		final PrayerCatalog.GodLine safeGodLine = godLine == null ? PrayerCatalog.getDefaultGodLine() : godLine;
		final String cacheKey = CACHE_PREFIX + safeGodLine.name().toLowerCase() + suffix;
		final boolean bonusThisOffering = !player.getCache().hasKey(cacheKey) || !player.getCache().getBoolean(cacheKey);
		player.getCache().store(cacheKey, bonusThisOffering);
		return bonusThisOffering ? 1 : 0;
	}

	private static boolean hasBlessedSymbolEquipped(final Player player, final PrayerCatalog.GodLine godLine) {
		if (player == null || player.getCarriedItems() == null || player.getCarriedItems().getEquipment() == null || godLine == null) {
			return false;
		}
		if (godLine == PrayerCatalog.GodLine.SARADOMIN) {
			return player.getCarriedItems().getEquipment().hasEquipped(ItemId.HOLY_SYMBOL_OF_SARADOMIN.id());
		}
		if (godLine == PrayerCatalog.GodLine.ZAMORAK) {
			return player.getCarriedItems().getEquipment().hasEquipped(ItemId.UNHOLY_SYMBOL_OF_ZAMORAK.id());
		}
		if (godLine == PrayerCatalog.GodLine.GUTHIX) {
			return player.getCarriedItems().getEquipment().hasEquipped(ItemId.GUTHIX_SYMBOL.id());
		}
		return false;
	}

	private static int getDevotionLevelFromOfferings(final int offerings) {
		return clampDevotionLevel(offerings / OFFERINGS_PER_DEVOTION_LEVEL);
	}

	private static String getOfferingCacheKey(final PrayerCatalog.GodLine godLine) {
		final PrayerCatalog.GodLine safeGodLine = godLine == null ? PrayerCatalog.getDefaultGodLine() : godLine;
		return CACHE_PREFIX + safeGodLine.name().toLowerCase() + CACHE_SUFFIX;
	}

	private static String formatGodLine(final PrayerCatalog.GodLine godLine) {
		final PrayerCatalog.GodLine safeGodLine = godLine == null ? PrayerCatalog.getDefaultGodLine() : godLine;
		final String lower = safeGodLine.name().toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private static void sendDevotionIncreaseMessage(final Player player, final PrayerCatalog.GodLine godLine, final int newDevotion) {
		if (newDevotion > 0) {
			player.playerServerMessage(
				MessageType.QUEST,
				"Your devotion to " + formatGodLine(godLine) + " grows. Future offerings grant +" + newDevotion + " Prayer XP."
			);
			return;
		}
		if (newDevotion == 0) {
			player.playerServerMessage(
				MessageType.QUEST,
				"Your devotion to " + formatGodLine(godLine) + " recovers to neutral."
			);
			return;
		}
		player.playerServerMessage(
			MessageType.QUEST,
			"Your devotion to " + formatGodLine(godLine) + " recovers. Current devotion: " + newDevotion + "."
		);
	}

	private static int clampOfferings(final long offerings) {
		return (int) Math.max(MIN_OFFERINGS, Math.min(MAX_OFFERINGS, offerings));
	}

	private static int clampDevotionLevel(final long devotionLevel) {
		return (int) Math.max(MIN_DEVOTION_LEVEL, Math.min(MAX_DEVOTION_LEVEL, devotionLevel));
	}

	private static int clampPositiveInt(final long value) {
		return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
	}
}
