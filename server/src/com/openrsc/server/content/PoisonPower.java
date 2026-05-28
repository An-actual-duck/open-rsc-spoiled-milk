package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;

public final class PoisonPower {

	private PoisonPower() {
	}

	public static boolean isPoisonWeapon(final int itemId) {
		return getWeaponTier(itemId) > 0;
	}

	public static int getWeaponMaxPoisonPower(final int itemId) {
		final int tier = getWeaponTier(itemId);
		return tier <= 0 ? 0 : tier * 10;
	}

	public static int getWeaponAppliedPoisonPower(final int itemId) {
		final int tier = getWeaponTier(itemId);
		return tier <= 0 ? 0 : tier * 4;
	}

	private static int getWeaponTier(final int itemId) {
		final ItemId item = ItemId.getById(itemId);
		if (item == null) {
			return -1;
		}
		final String name = item.name();
		if (!name.startsWith("POISON")) {
			return -1;
		}
		if (name.contains("CROSSBOW_BOLTS")) {
			return 1;
		}
		if (name.contains("TIN")) {
			return 1;
		}
		if (name.contains("COPPER")) {
			return 2;
		}
		if (name.contains("BRONZE")) {
			return 3;
		}
		if (name.contains("IRON")) {
			return 4;
		}
		if (name.contains("BLACK")) {
			return 5;
		}
		if (name.contains("STEEL")) {
			return 5;
		}
		if (name.contains("MITHRIL")) {
			return 6;
		}
		if (name.contains("TITAN_STEEL")) {
			return 7;
		}
		if (name.contains("ADAMANTITE")) {
			return 8;
		}
		if (name.contains("ORICHALCUM")) {
			return 9;
		}
		if (name.contains("RUNE")) {
			return 10;
		}
		if (name.contains("DRAGON")) {
			return 11;
		}
		return -1;
	}
}
