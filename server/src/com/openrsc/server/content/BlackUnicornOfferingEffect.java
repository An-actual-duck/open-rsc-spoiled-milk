package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;

/** Pure healing values and bounds for offerings made in the full Black Unicorn set. */
public final class BlackUnicornOfferingEffect {
	private BlackUnicornOfferingEffect() {
	}

	public static int getHealing(final int itemId) {
		final ItemId offering = ItemId.getById(itemId);
		if (offering == ItemId.BONES) {
			return 1;
		}
		if (offering == ItemId.BIG_BONES) {
			return 2;
		}
		if (offering == ItemId.BAT_BONES) {
			return 2;
		}
		if (offering == ItemId.DEMON_ASH) {
			return 3;
		}
		if (offering == ItemId.DRAGON_BONES) {
			return 4;
		}
		return 0;
	}

	public static int getRequestedHealing(final int itemId, final int amount) {
		if (amount <= 0) {
			return 0;
		}
		return (int) Math.min(Integer.MAX_VALUE, (long) getHealing(itemId) * amount);
	}

	public static int calculateHealing(final int itemId, final int amount,
			final boolean hasFullSet, final int currentHits, final int healingMaximumHits) {
		if (!hasFullSet || currentHits >= healingMaximumHits) {
			return 0;
		}
		final int requestedHealing = getRequestedHealing(itemId, amount);
		return Math.min(requestedHealing, Math.max(0, healingMaximumHits - currentHits));
	}
}
