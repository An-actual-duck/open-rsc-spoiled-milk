package orsc;

import java.awt.Rectangle;

/** Shared drawing and pointer geometry for the two trade stages. */
final class TradeWindowLayout {
	static final int OFFER_CAPACITY = 12;
	final Rectangle window, accept, decline;
	final Grid give, receive, inventory;
	final int padding, headerHeight, labelHeight, lineHeight, statusY, detailY;
	final int labelFont, bodyFont;
	final int firstColumn, secondColumn, columnWidth, reviewTop, reviewRows;
	final Rectangle reviewUp, reviewDown;

	TradeWindowLayout(int width, int bottom, int capacity, boolean review, int reviewItems,
					  GameInterfaceSettings.Size size, GameInterfaceSettings.TextSize text,
					  int labelLine, int bodyLine) {
		boolean compact = bottom < 560;
		padding = Math.min(size.pixels(12), compact ? 12 : 24);
		labelFont = text.labelFont;
		bodyFont = text.bodyFont;
		headerHeight = Math.max(Math.min(size.pixels(36), compact ? 44 : 60), bodyLine + padding);
		labelHeight = Math.max(Math.min(size.pixels(29), compact ? 34 : 48), labelLine + 5);
		lineHeight = Math.max(size.pixels(22), bodyLine + 2);
		int buttonHeight = Math.max(Math.min(size.pixels(36), compact ? 44 : 60), bodyLine + 10);
		int footer = 3 * padding + buttonHeight + lineHeight * (review ? 1 : 2);
		int margin = compact ? 8 : size.pixels(12);
		int inventoryColumns = compact ? (capacity + 4) / 5 : 5;
		int inventoryRows = (capacity + inventoryColumns - 1) / inventoryColumns;
		int totalColumns = inventoryColumns + (compact ? 8 : 4);
		int gutters = padding * (compact ? 4 : 3);
		int cellWidth = Math.min(size.pixels(70), (width - 2 * margin - gutters) / totalColumns);
		int gridLabels = compact ? labelHeight : 2 * labelHeight + padding;
		int gridRows = Math.max(inventoryRows, compact ? 3 : 6);
		int cellHeight = Math.max(1, Math.min(size.pixels(48),
			(bottom - 2 * margin - headerHeight - gridLabels - footer) / gridRows));
		int windowWidth = review ? Math.min(width - 2 * margin, size.pixels(720)) : cellWidth * totalColumns + gutters;
		reviewRows = Math.min(Math.max(1, reviewItems), Math.max(1,
			(bottom - 2 * margin - headerHeight - labelHeight - footer) / lineHeight));
		int contentHeight = review ? labelHeight + reviewRows * lineHeight : gridLabels + cellHeight * gridRows;
		int windowHeight = headerHeight + contentHeight + footer;
		window = new Rectangle((width - windowWidth) / 2, (bottom - windowHeight) / 2, windowWidth, windowHeight);
		firstColumn = window.x + padding;
		columnWidth = (windowWidth - 3 * padding) / 2;
		secondColumn = firstColumn + columnWidth + padding;
		int arrowWidth = size.pixels(31);
		reviewDown = new Rectangle(window.x + windowWidth - padding - arrowWidth, window.y + headerHeight,
			arrowWidth, labelHeight - 2);
		reviewUp = new Rectangle(reviewDown.x - arrowWidth - 3, reviewDown.y, arrowWidth, reviewDown.height);
		reviewTop = window.y + headerHeight + labelHeight;
		int top = reviewTop;
		give = new Grid(firstColumn, top, cellWidth, cellHeight, 4, OFFER_CAPACITY);
		int afterOffer = firstColumn + cellWidth * 4 + padding;
		receive = new Grid(compact ? afterOffer : firstColumn, compact ? top : top + cellHeight * 3 + padding + labelHeight,
			cellWidth, cellHeight, 4, OFFER_CAPACITY);
		int inventoryX = compact ? afterOffer + cellWidth * 4 + padding : afterOffer;
		inventory = new Grid(inventoryX, top, cellWidth, cellHeight, inventoryColumns, capacity);
		statusY = window.y + headerHeight + contentHeight + padding + lineHeight - 3;
		detailY = statusY + lineHeight;
		int buttonY = window.y + windowHeight - padding - buttonHeight;
		int buttonWidth = Math.min(size.pixels(152), (windowWidth - 3 * padding) / 2);
		accept = new Rectangle(window.x + windowWidth / 2 - padding / 2 - buttonWidth, buttonY, buttonWidth, buttonHeight);
		decline = new Rectangle(accept.x + buttonWidth + padding, buttonY, buttonWidth, buttonHeight);
	}

	static final class Grid {
		final int x, y, cellWidth, cellHeight, columns, capacity;

		Grid(int x, int y, int cellWidth, int cellHeight, int columns, int capacity) {
			this.x = x;
			this.y = y;
			this.cellWidth = cellWidth;
			this.cellHeight = cellHeight;
			this.columns = columns;
			this.capacity = capacity;
		}

		int x(int slot) { return x + slot % columns * cellWidth; }
		int y(int slot) { return y + slot / columns * cellHeight; }

		int slotAt(int mouseX, int mouseY) {
			if (mouseX < x || mouseX >= x + columns * cellWidth || mouseY < y) return -1;
			int slot = (mouseY - y) / cellHeight * columns + (mouseX - x) / cellWidth;
			return slot < capacity ? slot : -1;
		}
	}
}
