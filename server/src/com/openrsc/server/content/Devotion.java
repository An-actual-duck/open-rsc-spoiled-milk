package com.openrsc.server.content;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.MessageType;

public final class Devotion {
	private static final String CACHE_PREFIX = "devotion_";
	private static final String CACHE_SUFFIX = "_offerings";
	private static final int OFFERINGS_PER_BONUS_XP = 10;
	public static final int OFFERINGS_PER_DEVOTION_LEVEL = OFFERINGS_PER_BONUS_XP;
	public static final int MAX_DEVOTION_LEVEL = 1000;
	public static final int COMBAT_GROWTH_START_LEVEL = 500;
	private static final int MAX_OFFERINGS = MAX_DEVOTION_LEVEL * OFFERINGS_PER_DEVOTION_LEVEL;
	private static final int DEVOTION_REQUIREMENT_PER_RESOURCE = 50;
	private static final int PRAYER_BONUS_GROWTH_MAX = 10;

	private Devotion() {
	}

	public static int recordOfferingAndGetPrayerXpBonus(final Player player) {
		if (player == null || !player.getConfig().WANT_MYWORLD) {
			return 0;
		}

		final PrayerCatalog.GodLine godLine = player.getPrayerBook();
		final String cacheKey = getOfferingCacheKey(godLine);
		final int previousOfferings = player.getCache().hasKey(cacheKey) ? player.getCache().getInt(cacheKey) : 0;
		final int bonusXp = Math.min(previousOfferings / OFFERINGS_PER_BONUS_XP, MAX_DEVOTION_LEVEL);
		final int newOfferings = Math.min(previousOfferings + 1, MAX_OFFERINGS);
		player.getCache().set(cacheKey, newOfferings);
		ActionSender.sendDevotion(player);

		if (newOfferings % OFFERINGS_PER_BONUS_XP == 0) {
			final int nextBonusXp = newOfferings / OFFERINGS_PER_BONUS_XP;
			player.playerServerMessage(
				MessageType.QUEST,
				"Your devotion to " + formatGodLine(godLine) + " grows. Future offerings grant +" + nextBonusXp + " Prayer XP."
			);
		}
		return bonusXp;
	}

	public static int getOfferings(final Player player, final PrayerCatalog.GodLine godLine) {
		if (player == null || godLine == null) {
			return 0;
		}
		final String cacheKey = getOfferingCacheKey(godLine);
		final int offerings = player.getCache().hasKey(cacheKey) ? player.getCache().getInt(cacheKey) : 0;
		return clamp(offerings, 0, MAX_OFFERINGS);
	}

	public static int getDevotionLevel(final Player player, final PrayerCatalog.GodLine godLine) {
		return Math.min(getOfferings(player, godLine) / OFFERINGS_PER_DEVOTION_LEVEL, MAX_DEVOTION_LEVEL);
	}

	public static int getCurrentDevotionLevel(final Player player) {
		if (player == null) {
			return 0;
		}
		return getDevotionLevel(player, player.getPrayerBook());
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

	public static void adjustDevotionLevels(final Player player, final PrayerCatalog.GodLine godLine, final int devotionLevels) {
		if (player == null || godLine == null || devotionLevels == 0 || !player.getConfig().WANT_MYWORLD) {
			return;
		}
		final String cacheKey = getOfferingCacheKey(godLine);
		final int previousOfferings = player.getCache().hasKey(cacheKey) ? player.getCache().getInt(cacheKey) : 0;
		player.getCache().set(cacheKey, clamp(previousOfferings + (devotionLevels * OFFERINGS_PER_DEVOTION_LEVEL), 0, MAX_OFFERINGS));
		ActionSender.sendDevotion(player);
	}

	public static int getDevotionRequirementForResourceCost(final int resourceCost) {
		return resourceCost > 0 ? resourceCost * DEVOTION_REQUIREMENT_PER_RESOURCE : 0;
	}

	public static int getBlessingPrayerXp(final Player player, final PrayerCatalog.GodLine godLine, final int basePrayerXp) {
		if (player == null || godLine == null || basePrayerXp <= 0) {
			return 0;
		}
		final int devotionLevel = getDevotionLevel(player, godLine);
		return (int) Math.ceil(basePrayerXp * (100 + devotionLevel) / 100.0D);
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

	private static String getOfferingCacheKey(final PrayerCatalog.GodLine godLine) {
		final PrayerCatalog.GodLine safeGodLine = godLine == null ? PrayerCatalog.getDefaultGodLine() : godLine;
		return CACHE_PREFIX + safeGodLine.name().toLowerCase() + CACHE_SUFFIX;
	}

	private static String formatGodLine(final PrayerCatalog.GodLine godLine) {
		final PrayerCatalog.GodLine safeGodLine = godLine == null ? PrayerCatalog.getDefaultGodLine() : godLine;
		final String lower = safeGodLine.name().toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private static int clamp(final int value, final int min, final int max) {
		return Math.max(min, Math.min(max, value));
	}
}
