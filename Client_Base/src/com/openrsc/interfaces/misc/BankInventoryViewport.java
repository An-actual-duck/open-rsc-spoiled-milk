package com.openrsc.interfaces.misc;

final class BankInventoryViewport {
	static final int COLUMNS = 10;
	static final int SLOT_WIDTH = 49;
	static final int SLOT_HEIGHT = 34;
	static final int VISIBLE_ROWS = 3;
	static final int CONTENT_WIDTH = COLUMNS * SLOT_WIDTH;
	static final int CONTENT_HEIGHT = VISIBLE_ROWS * SLOT_HEIGHT;
	static final int VIEWPORT_HEIGHT = 104;

	private BankInventoryViewport() { }

	static int rowCount(int capacity) {
		return Math.max(0, (capacity + COLUMNS - 1) / COLUMNS);
	}

	static int maxScrollRow(int capacity) {
		return Math.max(0, rowCount(capacity) - VISIBLE_ROWS);
	}

	static int clampScrollRow(int scrollRow, int capacity) {
		return Math.max(0, Math.min(scrollRow, maxScrollRow(capacity)));
	}

	static int scrollBy(int scrollRow, int delta, int capacity) {
		if (delta == 0) return clampScrollRow(scrollRow, capacity);
		return clampScrollRow(scrollRow + (delta > 0 ? 1 : -1), capacity);
	}

	static int slotAt(int mouseX, int mouseY, int left, int top,
		int scrollRow, int capacity) {
		int relativeX = mouseX - left;
		int relativeY = mouseY - top;
		if (relativeX < 0 || relativeX >= CONTENT_WIDTH
			|| relativeY < 0 || relativeY >= CONTENT_HEIGHT) {
			return -1;
		}
		int slot = (clampScrollRow(scrollRow, capacity) + relativeY / SLOT_HEIGHT)
			* COLUMNS + relativeX / SLOT_WIDTH;
		return slot < capacity ? slot : -1;
	}
}
