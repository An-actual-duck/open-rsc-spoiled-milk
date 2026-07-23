package com.openrsc.server.model.container;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * Pure layout and persistence rules for bank item pins.
 *
 * <p>This class deliberately knows only catalog IDs and source-list indexes.
 * Real {@link Item} ownership remains in {@link Bank}; an empty pinned slot is
 * represented by {@code sourceIndex == -1}, never by a zero-quantity item.</p>
 */
public final class BankPinLayout {
	public static final String CACHE_PREFIX = "v1|";

	private BankPinLayout() {
	}

	public static Layout build(List<Integer> catalogIds, Map<Integer, Integer> requestedPins,
							   int maxSlots) {
		final NavigableMap<Integer, Integer> pins = normalize(catalogIds, requestedPins, maxSlots);
		final List<Slot> slots = new ArrayList<>();
		final Set<Integer> usedSources = new HashSet<>();

		for (int slotIndex = 0; slotIndex < displaySize(catalogIds, pins); slotIndex++) {
			final Integer pinnedCatalogId = pins.get(slotIndex);
			if (pinnedCatalogId != null) {
				final int sourceIndex = findUnusedSource(catalogIds, usedSources, pinnedCatalogId);
				if (sourceIndex >= 0) {
					usedSources.add(sourceIndex);
				}
				slots.add(new Slot(pinnedCatalogId, sourceIndex, true));
				continue;
			}

			final int sourceIndex = firstUnusedSource(catalogIds.size(), usedSources);
			if (sourceIndex >= 0) {
				usedSources.add(sourceIndex);
				slots.add(new Slot(catalogIds.get(sourceIndex), sourceIndex, false));
			}
		}
		return new Layout(slots, pins);
	}

	public static NavigableMap<Integer, Integer> parse(String serialized) {
		final NavigableMap<Integer, Integer> parsed = new TreeMap<>();
		if (serialized == null || !serialized.startsWith(CACHE_PREFIX)) {
			return parsed;
		}
		final String entries = serialized.substring(CACHE_PREFIX.length());
		if (entries.isEmpty()) {
			return parsed;
		}
		for (String entry : entries.split(",")) {
			final String[] pair = entry.split(":", -1);
			if (pair.length != 2) {
				continue;
			}
			try {
				parsed.put(Integer.parseInt(pair[0]), Integer.parseInt(pair[1]));
			} catch (NumberFormatException ignored) {
				// Invalid cache fragments are ignored and normalized on load.
			}
		}
		return parsed;
	}

	public static String serialize(Map<Integer, Integer> pins) {
		final StringBuilder serialized = new StringBuilder(CACHE_PREFIX);
		boolean first = true;
		for (Map.Entry<Integer, Integer> entry : new TreeMap<>(pins).entrySet()) {
			if (!first) {
				serialized.append(',');
			}
			serialized.append(entry.getKey()).append(':').append(entry.getValue());
			first = false;
		}
		return serialized.toString();
	}

	public static Rearrangement rearrange(List<Integer> catalogIds,
										 Map<Integer, Integer> requestedPins,
										 int maxSlots,
										 int from,
										 int to,
										 boolean insert) {
		final List<Slot> slots = new ArrayList<>(
			build(catalogIds, requestedPins, maxSlots).getSlots());
		if (from < 0 || to < 0 || from >= slots.size() || to >= slots.size() || from == to) {
			return null;
		}
		if (insert) {
			final Slot moved = slots.remove(from);
			slots.add(to, moved);
		} else {
			Collections.swap(slots, from, to);
		}

		final List<Integer> sourceOrder = new ArrayList<>();
		final NavigableMap<Integer, Integer> reorderedPins = new TreeMap<>();
		for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
			final Slot slot = slots.get(slotIndex);
			if (slot.getSourceIndex() >= 0) {
				sourceOrder.add(slot.getSourceIndex());
			}
			if (slot.isPinned()) {
				reorderedPins.put(slotIndex, slot.getCatalogId());
			}
		}
		return new Rearrangement(sourceOrder, reorderedPins);
	}

	public static NavigableMap<Integer, Integer> shiftAfterRemoval(
		Map<Integer, Integer> pins, int removedSlot) {
		final NavigableMap<Integer, Integer> shifted = new TreeMap<>();
		for (Map.Entry<Integer, Integer> entry : pins.entrySet()) {
			final int slot = entry.getKey();
			shifted.put(slot > removedSlot ? slot - 1 : slot, entry.getValue());
		}
		return shifted;
	}

	private static NavigableMap<Integer, Integer> normalize(List<Integer> catalogIds,
														 Map<Integer, Integer> requestedPins,
														 int maxSlots) {
		final NavigableMap<Integer, Integer> pins = new TreeMap<>();
		final Set<Integer> seenCatalogIds = new HashSet<>();
		for (Map.Entry<Integer, Integer> entry : new TreeMap<>(requestedPins).entrySet()) {
			final int slot = entry.getKey();
			final int catalogId = entry.getValue();
			if (slot < 0 || slot >= maxSlots || catalogId < 0 || !seenCatalogIds.add(catalogId)) {
				continue;
			}
			pins.put(slot, catalogId);
		}

		boolean changed;
		do {
			changed = false;
			final int size = displaySize(catalogIds, pins);
			for (Integer slot : new ArrayList<>(pins.keySet())) {
				if (slot >= size) {
					pins.remove(slot);
					changed = true;
				}
			}
		} while (changed);
		return pins;
	}

	private static int displaySize(List<Integer> catalogIds, Map<Integer, Integer> pins) {
		int emptyPins = 0;
		for (Integer catalogId : pins.values()) {
			if (!catalogIds.contains(catalogId)) {
				emptyPins++;
			}
		}
		return catalogIds.size() + emptyPins;
	}

	private static int findUnusedSource(List<Integer> catalogIds, Set<Integer> usedSources,
										int catalogId) {
		for (int index = 0; index < catalogIds.size(); index++) {
			if (!usedSources.contains(index) && catalogIds.get(index) == catalogId) {
				return index;
			}
		}
		return -1;
	}

	private static int firstUnusedSource(int sourceCount, Set<Integer> usedSources) {
		for (int index = 0; index < sourceCount; index++) {
			if (!usedSources.contains(index)) {
				return index;
			}
		}
		return -1;
	}

	public static final class Slot {
		private final int catalogId;
		private final int sourceIndex;
		private final boolean pinned;

		private Slot(int catalogId, int sourceIndex, boolean pinned) {
			this.catalogId = catalogId;
			this.sourceIndex = sourceIndex;
			this.pinned = pinned;
		}

		public int getCatalogId() {
			return catalogId;
		}

		public int getSourceIndex() {
			return sourceIndex;
		}

		public boolean isPinned() {
			return pinned;
		}
	}

	public static final class Layout {
		private final List<Slot> slots;
		private final NavigableMap<Integer, Integer> pins;

		private Layout(List<Slot> slots, NavigableMap<Integer, Integer> pins) {
			this.slots = Collections.unmodifiableList(slots);
			this.pins = Collections.unmodifiableNavigableMap(new TreeMap<>(pins));
		}

		public List<Slot> getSlots() {
			return slots;
		}

		public NavigableMap<Integer, Integer> getPins() {
			return pins;
		}
	}

	public static final class Rearrangement {
		private final List<Integer> sourceOrder;
		private final NavigableMap<Integer, Integer> pins;

		private Rearrangement(List<Integer> sourceOrder, NavigableMap<Integer, Integer> pins) {
			this.sourceOrder = Collections.unmodifiableList(sourceOrder);
			this.pins = Collections.unmodifiableNavigableMap(new TreeMap<>(pins));
		}

		public List<Integer> getSourceOrder() {
			return sourceOrder;
		}

		public NavigableMap<Integer, Integer> getPins() {
			return pins;
		}
	}
}
