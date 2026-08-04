package com.openrsc.server.content.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic mixed-status selection without altering gameplay state. */
public final class ActiveStatusInventory {
	public static final int MAX_COLLECTED = 64;
	public static final int MAX_VISIBLE = 32;

	private static final List<String> AUTHORED_ORDER = Collections.unmodifiableList(Arrays.asList(
		"cleric:healing_pulses",
		"cleric:protection",
		"potion:brawn",
		"potion:deftness",
		"cleric:fervor",
		"cleric:rally",
		"potion:stat_reduction_protection",
		"cleric:thorns",
		"cleric:zeal",
		"potion:magic_resistance",
		"potion:melee_resistance",
		"potion:poison_protection",
		"potion:ranged_resistance",
		"potion:regeneration",
		"cleric:respite",
		"potion:insight",
		"potion:insight_skills",
		"potion:luck",
		"potion:notation",
		"potion:skiller",
		"potion:speed",
		"potion:warrior"));
	private static final Map<String, Integer> ORDER_BY_KEY;

	static {
		Map<String, Integer> order = new HashMap<String, Integer>();
		for (int index = 0; index < AUTHORED_ORDER.size(); index++) {
			if (order.put(AUTHORED_ORDER.get(index), index) != null) {
				throw new IllegalStateException("Duplicate active-status priority key");
			}
		}
		ORDER_BY_KEY = Collections.unmodifiableMap(order);
	}

	private final List<ActiveStatusEntry> visible;
	private final int totalCount;

	private ActiveStatusInventory(List<ActiveStatusEntry> visible, int totalCount) {
		this.visible = visible;
		this.totalCount = totalCount;
	}

	public static ActiveStatusInventory select(List<ActiveStatusEntry> entries) {
		if (entries == null || entries.size() > MAX_COLLECTED) {
			throw new IllegalArgumentException("Active-status inventory exceeds the 64-entry authority bound");
		}
		ArrayList<ActiveStatusEntry> ordered = new ArrayList<ActiveStatusEntry>(entries.size());
		Set<String> keys = new HashSet<String>();
		for (ActiveStatusEntry entry : entries) {
			if (entry == null || !keys.add(entry.getStableKey())) {
				throw new IllegalArgumentException("Active-status entries require unique stable keys");
			}
			ordered.add(entry);
		}
		Collections.sort(ordered, new Comparator<ActiveStatusEntry>() {
			@Override
			public int compare(ActiveStatusEntry left, ActiveStatusEntry right) {
				int leftOrder = authoredOrder(left.getStableKey());
				int rightOrder = authoredOrder(right.getStableKey());
				if (leftOrder != rightOrder) {
					return leftOrder < rightOrder ? -1 : 1;
				}
				return left.getStableKey().compareTo(right.getStableKey());
			}
		});
		int visibleCount = Math.min(MAX_VISIBLE, ordered.size());
		return new ActiveStatusInventory(Collections.unmodifiableList(
			new ArrayList<ActiveStatusEntry>(ordered.subList(0, visibleCount))), ordered.size());
	}

	private static int authoredOrder(String stableKey) {
		Integer order = ORDER_BY_KEY.get(stableKey);
		return order == null ? Integer.MAX_VALUE : order;
	}

	public static List<String> getAuthoredOrder() {
		return AUTHORED_ORDER;
	}

	public List<ActiveStatusEntry> getVisible() {
		return visible;
	}

	public int getTotalCount() {
		return totalCount;
	}

	public int getOverflowCount() {
		return totalCount - visible.size();
	}
}
