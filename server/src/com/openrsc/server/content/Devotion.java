package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.MessageType;

import java.util.function.BooleanSupplier;

public final class Devotion {
	private static final String CACHE_PREFIX = "devotion_";
	private static final String CACHE_SUFFIX = "_offerings";
	private static final String HALF_OFFERING_REMAINDER_SUFFIX = "_half_offering_remainder";
	private static final int OFFERINGS_PER_BONUS_XP = 10;
	public static final int OFFERINGS_PER_DEVOTION_LEVEL = OFFERINGS_PER_BONUS_XP;
	public static final int MAX_DEVOTION_LEVEL = DevotionHalfOfferingBalance.MAX_DEVOTION_LEVEL;
	public static final int MIN_DEVOTION_LEVEL = DevotionHalfOfferingBalance.MIN_DEVOTION_LEVEL;
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
		final int previousHalfOfferingUnits = getHalfOfferingUnits(player, godLine);
		final int previousDevotion = DevotionHalfOfferingBalance.getDisplayedLevel(previousHalfOfferingUnits);
		final int bonusXp = Math.max(0, Math.min(previousDevotion, MAX_DEVOTION_LEVEL));
		final int offeringHalfUnits = DevotionOfferingGain.getHalfOfferingUnits(
			hasBlessedSymbolEquipped(player, godLine),
			blackUnicornBonus,
			hasFullBlackUnicornSetEquipped(player));
		final int newHalfOfferingUnits = DevotionHalfOfferingBalance.adjust(
			previousHalfOfferingUnits,
			offeringHalfUnits);
		storeHalfOfferingUnits(player, godLine, newHalfOfferingUnits);
		ActionSender.sendDevotion(player);
		ActionSender.sendEquipmentStats(player);

		final int newDevotion = DevotionHalfOfferingBalance.getDisplayedLevel(newHalfOfferingUnits);
		if (newDevotion > previousDevotion) {
			sendDevotionIncreaseMessage(player, godLine, newDevotion);
		}
		return bonusXp * 4;
	}

	public static int getOfferings(final Player player, final PrayerCatalog.GodLine godLine) {
		if (player == null || godLine == null) {
			return 0;
		}
		return DevotionHalfOfferingBalance.getWholeOfferings(getHalfOfferingUnits(player, godLine));
	}

	public static int getHalfOfferingUnits(final Player player, final PrayerCatalog.GodLine godLine) {
		if (player == null || godLine == null) {
			return 0;
		}
		final String offeringKey = getOfferingCacheKey(godLine);
		final int wholeOfferings = player.getCache().hasKey(offeringKey)
			? clampOfferings(player.getCache().getInt(offeringKey))
			: 0;
		final String remainderKey = getHalfOfferingRemainderCacheKey(godLine);
		int remainder = player.getCache().hasKey(remainderKey)
			? player.getCache().getInt(remainderKey)
			: 0;
		if (remainder < -1 || remainder > 1) {
			player.getCache().remove(remainderKey);
			remainder = 0;
		}
		return DevotionHalfOfferingBalance.fromStoredParts(wholeOfferings, remainder);
	}

	public static int getDevotionLevel(final Player player, final PrayerCatalog.GodLine godLine) {
		return DevotionHalfOfferingBalance.getDisplayedLevel(getHalfOfferingUnits(player, godLine));
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
		storeHalfOfferingUnits(
			player,
			godLine,
			clampedDevotionLevel * DevotionHalfOfferingBalance.HALF_UNITS_PER_DEVOTION_LEVEL);
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
		adjustDevotionHalfOfferingUnits(
			player,
			godLine,
			(int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE,
				(long) devotionLevels * DevotionHalfOfferingBalance.HALF_UNITS_PER_DEVOTION_LEVEL)));
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
		final int actualHalfUnitChange = adjustDevotionHalfOfferingUnits(
			player,
			godLine,
			(int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE,
				(long) offerings * DevotionHalfOfferingBalance.HALF_UNITS_PER_OFFERING)));
		return actualHalfUnitChange / DevotionHalfOfferingBalance.HALF_UNITS_PER_OFFERING;
	}

	/** Adjusts exact half-offering units and returns the signed applied change. */
	public static int adjustDevotionHalfOfferingUnits(final Player player,
			final PrayerCatalog.GodLine godLine, final int halfOfferingUnits) {
		if (player == null || godLine == null || halfOfferingUnits == 0
			|| !player.getConfig().WANT_MYWORLD) {
			return 0;
		}
		synchronized (player) {
			final int previous = getHalfOfferingUnits(player, godLine);
			final int updated = DevotionHalfOfferingBalance.adjust(previous, halfOfferingUnits);
			storeHalfOfferingUnits(player, godLine, updated);
			notifyBalanceChanged(player, previous, updated);
			return updated - previous;
		}
	}

	/**
	 * Pays one atomic Cleric-production cost without crossing the exact minimum.
	 * Reaching the minimum is permitted, but spending while already there is not.
	 */
	public static boolean trySpendDevotionHalfOfferingUnits(final Player player,
			final PrayerCatalog.GodLine godLine, final int costHalfOfferingUnits) {
		return trySpendDevotionHalfOfferingUnits(
			player, godLine, costHalfOfferingUnits, () -> true);
	}

	/** Returns whether the exact cost can be paid without crossing the minimum. */
	public static boolean canSpendDevotionHalfOfferingUnits(final Player player,
			final PrayerCatalog.GodLine godLine, final int costHalfOfferingUnits) {
		if (player == null || godLine == null || costHalfOfferingUnits <= 0
			|| !player.getConfig().WANT_MYWORLD) {
			return false;
		}
		synchronized (player) {
			return DevotionHalfOfferingBalance.canSpendAboveMinimum(
				getHalfOfferingUnits(player, godLine), costHalfOfferingUnits);
		}
	}

	/**
	 * Pays an exact cost only after the supplied state change commits. A rejected
	 * state change leaves Devotion untouched, avoiding observable deduct/refund
	 * side effects such as transient prayer deactivation.
	 */
	public static boolean trySpendDevotionHalfOfferingUnits(final Player player,
			final PrayerCatalog.GodLine godLine, final int costHalfOfferingUnits,
			final BooleanSupplier stateChange) {
		if (player == null || godLine == null || costHalfOfferingUnits <= 0
			|| stateChange == null || !player.getConfig().WANT_MYWORLD) {
			return false;
		}
		synchronized (player) {
			final int previous = getHalfOfferingUnits(player, godLine);
			if (!DevotionHalfOfferingBalance.canSpendAboveMinimum(previous, costHalfOfferingUnits)
				|| !stateChange.getAsBoolean()) {
				return false;
			}
			final int updated = previous - costHalfOfferingUnits;
			storeHalfOfferingUnits(player, godLine, updated);
			notifyBalanceChanged(player, previous, updated);
			return true;
		}
	}

	public static String formatExactDevotion(final Player player,
			final PrayerCatalog.GodLine godLine) {
		return DevotionHalfOfferingBalance.format(getHalfOfferingUnits(player, godLine));
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

	private static boolean hasFullBlackUnicornSetEquipped(final Player player) {
		return player != null
			&& player.getCarriedItems() != null
			&& player.getCarriedItems().getEquipment() != null
			&& player.getCarriedItems().getEquipment().hasFullBlackUnicornHideSet();
	}

	private static String getOfferingCacheKey(final PrayerCatalog.GodLine godLine) {
		final PrayerCatalog.GodLine safeGodLine = godLine == null ? PrayerCatalog.getDefaultGodLine() : godLine;
		return CACHE_PREFIX + safeGodLine.name().toLowerCase() + CACHE_SUFFIX;
	}

	private static String getHalfOfferingRemainderCacheKey(final PrayerCatalog.GodLine godLine) {
		final PrayerCatalog.GodLine safeGodLine = godLine == null ? PrayerCatalog.getDefaultGodLine() : godLine;
		return CACHE_PREFIX + safeGodLine.name().toLowerCase() + HALF_OFFERING_REMAINDER_SUFFIX;
	}

	private static void storeHalfOfferingUnits(final Player player,
			final PrayerCatalog.GodLine godLine, final int exactHalfOfferingUnits) {
		final int clamped = DevotionHalfOfferingBalance.clampHalfUnits(exactHalfOfferingUnits);
		player.getCache().set(
			getOfferingCacheKey(godLine),
			DevotionHalfOfferingBalance.getWholeOfferings(clamped));
		final String remainderKey = getHalfOfferingRemainderCacheKey(godLine);
		final int remainder = DevotionHalfOfferingBalance.getHalfOfferingRemainder(clamped);
		if (remainder == 0) {
			player.getCache().remove(remainderKey);
		} else {
			player.getCache().set(remainderKey, remainder);
		}
	}

	private static void notifyBalanceChanged(final Player player, final int previous, final int updated) {
		ActionSender.sendDevotion(player);
		ActionSender.sendEquipmentStats(player);
		if (updated < previous) {
			player.getPrayers().deactivateOverflowingPrayers();
		}
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
				"Your devotion to " + formatGodLine(godLine) + " grows. Future offerings grant +" + newDevotion + " Worship XP."
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
