package com.openrsc.server.model.container;

import com.openrsc.server.model.Cache;

import java.util.ArrayList;
import java.util.List;

/** Per-account bank view preferences stored separately from the bank's items and slots. */
public final class BankFilterTabs {
	public static final String CLIENT_ATTRIBUTE = "bank_filter_tabs_client";
	public static final int LIMIT = 6;
	private static final String KEY = "bank_filter_tab_v1_";
	private static final String DEFAULT = "0";
	private static final int TAG_MASK = (1 << 18) - 1;
	private static final int MAX_QUERY = 30;

	private BankFilterTabs() {
	}

	public static String[] load(Cache cache) {
		List<String> tabs = new ArrayList<>();
		for (int slot = 0; slot < LIMIT; slot++) {
			if (!cache.hasKey(KEY + slot) && slot > 0) break;
			String normalized = null;
			if (cache.hasKey(KEY + slot)) {
				try {
					normalized = normalize(cache.getString(KEY + slot));
				} catch (IllegalArgumentException ignored) {
					// An invalid preference never prevents the bank from opening.
				}
			}
			tabs.add(normalized == null ? DEFAULT : normalized);
		}
		return tabs.toArray(new String[0]);
	}

	public static void save(Cache cache, int slot, String value) {
		if (slot < 0 || slot >= LIMIT || slot > load(cache).length) return;
		String normalized = normalize(value);
		if (normalized != null) cache.store(KEY + slot, normalized);
	}

	private static String normalize(String value) {
		if (DEFAULT.equals(value)) return DEFAULT;
		if (value == null || value.length() > 42) return null;
		String[] parts = value.split("\\|", 4);
		if (parts.length != 4 || !"1".equals(parts[0]) || !("0".equals(parts[1]) || "1".equals(parts[1]))
			|| parts[3].length() > MAX_QUERY) return null;
		for (int i = 0; i < parts[3].length(); i++) {
			if (Character.isISOControl(parts[3].charAt(i))) return null;
		}
		try {
			int tags = Integer.parseInt(parts[2], 16);
			if (tags < 0 || tags > TAG_MASK) return null;
			return "1|" + parts[1] + "|" + Integer.toHexString(tags) + "|" + parts[3].trim();
		} catch (NumberFormatException ignored) {
			return null;
		}
	}
}
