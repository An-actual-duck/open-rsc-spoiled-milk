package com.openrsc.server.content;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.util.rsc.MessageType;

public final class Devotion {
	private static final String CACHE_PREFIX = "devotion_";
	private static final String CACHE_SUFFIX = "_offerings";
	private static final int OFFERINGS_PER_BONUS_XP = 10;

	private Devotion() {
	}

	public static int recordOfferingAndGetPrayerXpBonus(final Player player) {
		if (player == null || !player.getConfig().WANT_MYWORLD) {
			return 0;
		}

		final PrayerCatalog.GodLine godLine = player.getPrayerBook();
		final String cacheKey = getOfferingCacheKey(godLine);
		final int previousOfferings = player.getCache().hasKey(cacheKey) ? player.getCache().getInt(cacheKey) : 0;
		final int bonusXp = previousOfferings / OFFERINGS_PER_BONUS_XP;
		final int newOfferings = previousOfferings + 1;
		player.getCache().set(cacheKey, newOfferings);

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
		return player.getCache().hasKey(cacheKey) ? player.getCache().getInt(cacheKey) : 0;
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
}
