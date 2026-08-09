package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;

/**
 * Player-facing Worship XP values for successful offerings.
 *
 * <p>Skill experience is stored in quarter-XP units. The values returned by
 * this class are therefore converted only at the final award boundary. Keeping
 * the multipliers in integer percentages makes the offering and Devotion-bonus
 * paths use the same exact rule.</p>
 */
public final class OfferingExperience {
	public static final int BASE_XP = 10;
	public static final int INTERNAL_XP_UNITS_PER_XP = 4;

	private OfferingExperience() {
	}

	public static int getDisplayedExperience(final int itemId) {
		return scaleDisplayedExperience(BASE_XP, itemId);
	}

	public static int getInternalExperience(final int itemId) {
		return getDisplayedExperience(itemId) * INTERNAL_XP_UNITS_PER_XP;
	}

	public static int scaleDisplayedExperience(final int displayedExperience, final int itemId) {
		if (displayedExperience <= 0) {
			return 0;
		}
		return (int) Math.min(Integer.MAX_VALUE,
			((long) displayedExperience * getMultiplierPercent(itemId)) / 100L);
	}

	public static int getMultiplierPercent(final int itemId) {
		final ItemId item = ItemId.getById(itemId);
		if (item == null) {
			return 0;
		}
		switch (item) {
			case BONES:
			case ASHES:
				return 100;
			case BIG_BONES:
				return 120;
			case BAT_BONES:
				return 130;
			case DEMON_ASH:
				return 140;
			case DRAGON_BONES:
				return 160;
			default:
				return 0;
		}
	}
}
