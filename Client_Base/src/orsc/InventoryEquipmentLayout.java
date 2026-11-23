package orsc;

/** Draw and pointer bounds for the preview sidebar's Inventory and Equipment tabs. */
final class InventoryEquipmentLayout {
	static final int COLUMNS = 5;
	static final int EQUIPMENT = 1;
	static final int INVENTORY = 0;
	private static final int EQUIPMENT_ART_HEIGHT = 202;

	final int left;
	final int top;
	final int width;
	final int padding;
	final int gridTop;
	final int cellWidth;
	final int cellHeight;
	final int rows;
	final int tabsTop;
	final int tabsHeight;
	final int tabBaseline;
	final int equipmentArtHeight;
	final int equipmentLeft;
	final int equipmentTop;
	final int statsTop;
	final int statRowHeight;
	final int bodyFont;
	final int labelFont;
	private final int slots;
	private final int launcherY;
	private final int surfaceWidth;
	private final int chatRight;
	private final int chatEntryTop;
	private final GameInterfaceSettings.Size size;
	private final GameInterfaceSettings.TextSize text;
	private final boolean bottomLauncher;
	private final boolean hasEquipment;

	InventoryEquipmentLayout(int surfaceWidth, int surfaceHeight, int launcherY, boolean bottomLauncher,
							 int chatRight, int chatEntryTop, GameInterfaceSettings.Size size,
							 GameInterfaceSettings.TextSize text, int bodyHeight, int labelHeight,
							 int minimumStatsWidth, int slots, boolean hasEquipment) {
		this.surfaceWidth = surfaceWidth;
		this.launcherY = launcherY;
		this.bottomLauncher = bottomLauncher;
		this.chatRight = chatRight;
		this.chatEntryTop = chatEntryTop;
		this.size = size;
		this.text = text;
		this.slots = slots;
		this.hasEquipment = hasEquipment;
		bodyFont = text.bodyFont;
		labelFont = text.labelFont;
		padding = size.pixels(6);
		rows = (slots + COLUMNS - 1) / COLUMNS;
		cellWidth = Math.max(size.pixels(52), (minimumStatsWidth + COLUMNS - 1) / COLUMNS);
		width = COLUMNS * cellWidth + 2 * padding;
		left = surfaceWidth - width - size.pixels(6);

		int topLimit = bottomLauncher ? size.pixels(6) : launcherY + 32;
		int bottom = bottomLauncher ? launcherY - size.pixels(4) : surfaceHeight - padding;
		if (left < chatRight) {
			bottom = Math.min(bottom, chatEntryTop - padding);
		}
		tabsHeight = hasEquipment ? Math.max(size.pixels(29), labelHeight + 2 * padding) : 0;
		int available = Math.max(rows + 2 * padding, bottom - topLimit - tabsHeight);
		cellHeight = Math.max(1, Math.min(size.pixels(38), (available - 2 * padding) / rows));
		statRowHeight = bodyHeight + size.pixels(3);
		equipmentArtHeight = Math.max(1,
			Math.min(size.pixels(EQUIPMENT_ART_HEIGHT), available - 4 * statRowHeight - 3 * padding));
		int inventoryHeight = rows * cellHeight + 2 * padding;
		int equipmentHeight = equipmentArtHeight + 4 * statRowHeight + 3 * padding;
		int contentHeight = hasEquipment ? Math.max(inventoryHeight, equipmentHeight) : inventoryHeight;

		top = bottomLauncher ? bottom - tabsHeight - contentHeight : topLimit;
		gridTop = top + padding;
		tabsTop = top + contentHeight;
		tabBaseline = tabsTop + (tabsHeight + labelHeight) / 2 - 2;
		equipmentLeft = left + (width - equipmentPixels(245)) / 2;
		equipmentTop = top + padding;
		statsTop = equipmentTop + equipmentArtHeight + padding;
	}

	boolean matches(int surfaceWidth, int launcherY, boolean bottomLauncher, int chatRight, int chatEntryTop,
					GameInterfaceSettings.Size size, GameInterfaceSettings.TextSize text, boolean hasEquipment) {
		return this.surfaceWidth == surfaceWidth && this.launcherY == launcherY && this.bottomLauncher == bottomLauncher
			&& this.chatRight == chatRight && this.chatEntryTop == chatEntryTop && this.size == size
			&& this.text == text && this.hasEquipment == hasEquipment;
	}

	int bottom() {
		return tabsTop + tabsHeight;
	}

	boolean contains(int x, int y) {
		return x >= left && x < left + width && y >= top && y < bottom();
	}

	int cellX(int slot) {
		return left + padding + slot % COLUMNS * cellWidth;
	}

	int cellY(int slot) {
		return gridTop + slot / COLUMNS * cellHeight;
	}

	int inventorySlotAt(int x, int y) {
		if (x < cellX(0) || x >= cellX(0) + COLUMNS * cellWidth || y < gridTop
			|| y >= gridTop + rows * cellHeight) return -1;
		int slot = (y - gridTop) / cellHeight * COLUMNS + (x - cellX(0)) / cellWidth;
		return slot < slots ? slot : -1;
	}

	int tabAt(int x, int y) {
		if (!hasEquipment || !contains(x, y) || y < tabsTop) return -1;
		return x < left + width / 2 ? EQUIPMENT : INVENTORY;
	}

	int equipmentPixels(int baseline) {
		return (equipmentArtHeight * baseline + EQUIPMENT_ART_HEIGHT / 2) / EQUIPMENT_ART_HEIGHT;
	}

	int equipmentSlotAt(int[] slotX, int[] slotY, int slots, int x, int y) {
		for (int slot = 0; slot < slots; slot++) {
			int left = equipmentLeft + equipmentPixels(slotX[slot]);
			int top = equipmentTop + equipmentPixels(slotY[slot]);
			if (x >= left && x < left + equipmentPixels(EquipmentSlotHoverTarget.SLOT_WIDTH)
				&& y >= top && y < top + equipmentPixels(EquipmentSlotHoverTarget.SLOT_HEIGHT)) return slot;
		}
		return -1;
	}
}
