package com.openrsc.interfaces.misc;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;

/** A saved bank view. These bit positions are part of the stored format. */
final class BankFilterSettings {
	static final int LIMIT = 6;
	static final BankFilterSettings ALL = new BankFilterSettings("", EnumSet.noneOf(BankItemTag.class), false);
	private static final BankItemTag[] STORED_TAGS = {
		BankItemTag.MINING_SMITHING, BankItemTag.CRAFTING_LEATHER, BankItemTag.CRAFTING_JEWELRY,
		BankItemTag.CRAFTING_WOOD, BankItemTag.CRAFTING_OTHER, BankItemTag.ENCHANTING, BankItemTag.PRAYER,
		BankItemTag.HERBLAW, BankItemTag.COOKING, BankItemTag.FOOD, BankItemTag.TOOLS, BankItemTag.RARE_DROPS,
		BankItemTag.ARMOUR, BankItemTag.MAGIC, BankItemTag.MELEE, BankItemTag.RANGED, BankItemTag.JEWELRY,
		BankItemTag.QUEST_ITEMS
	};

	final String query;
	final EnumSet<BankItemTag> tags;
	final boolean andMode;

	BankFilterSettings(String query, EnumSet<BankItemTag> tags, boolean andMode) {
		this.query = query.trim().toLowerCase(Locale.ENGLISH);
		this.tags = tags.clone();
		this.andMode = andMode;
	}

	String encode() {
		int flags = 0;
		for (int i = 0; i < STORED_TAGS.length; i++) {
			if (tags.contains(STORED_TAGS[i])) flags |= 1 << i;
		}
		return "1|" + (andMode ? "1" : "0") + "|" + Integer.toHexString(flags) + "|" + query;
	}

	static BankFilterSettings decode(String rule) {
		String[] parts = rule.split("\\|", 4);
		if (parts.length != 4 || !"1".equals(parts[0])) return null;
		try {
			int flags = Integer.parseInt(parts[2], 16);
			EnumSet<BankItemTag> tags = EnumSet.noneOf(BankItemTag.class);
			for (int i = 0; i < STORED_TAGS.length; i++) {
				if ((flags & 1 << i) != 0) tags.add(STORED_TAGS[i]);
			}
			return new BankFilterSettings(parts[3], tags, "1".equals(parts[1]));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	boolean isActive() {
		return !query.isEmpty() || !tags.isEmpty();
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof BankFilterSettings)) return false;
		BankFilterSettings settings = (BankFilterSettings) other;
		return andMode == settings.andMode && query.equals(settings.query) && tags.equals(settings.tags);
	}

	@Override
	public int hashCode() {
		return Objects.hash(query, tags, andMode);
	}
}
