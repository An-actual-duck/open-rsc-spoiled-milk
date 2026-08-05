package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;

/** Fixed healing values for manual offerings made in the full Black Unicorn set. */
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
		if (offering == ItemId.DEMON_ASH) {
			return 3;
		}
		if (offering == ItemId.DRAGON_BONES) {
			return 4;
		}
		return 0;
	}
}
