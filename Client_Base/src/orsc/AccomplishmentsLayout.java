package orsc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Viewport, rows and scroll bounds for the Stats and Quests sidebar. */
final class AccomplishmentsLayout {
	static final int STATS = 0;
	static final int QUESTS = 1;
	static final int STATS_EXTRA_ROWS = 10;

	final int left;
	final int top;
	final int width;
	final int bottom;
	final int padding;
	final int tabHeight;
	final int tabBaseline;
	final int rowHeight;
	final int bodyHeight;
	final int bodyFont;
	final int labelFont;
	final int scrollbarWidth;
	final int statusWidth;
	final int[] skills;
	final int skillRows;
	final List<QuestRow> quests = new ArrayList<>();
	final int questContentHeight;
	private final int surfaceWidth;
	private final int anchor;
	private final boolean bottomLauncher;
	private final int chatRight;
	private final int chatEntryTop;
	private final GameInterfaceSettings.Size size;
	private final GameInterfaceSettings.TextSize text;

	AccomplishmentsLayout(int surfaceWidth, int anchor, boolean bottomLauncher, int chatRight, int chatEntryTop,
						  GameInterfaceSettings.Size size, GameInterfaceSettings.TextSize text, int bodyHeight,
						  int labelHeight, int minColumnWidth, int statusWidth, int[] skills, String[] questNames,
						  ToIntFunction<String> measure) {
		this.surfaceWidth = surfaceWidth;
		this.anchor = anchor;
		this.bottomLauncher = bottomLauncher;
		this.chatRight = chatRight;
		this.chatEntryTop = chatEntryTop;
		this.size = size;
		this.text = text;
		this.bodyHeight = bodyHeight;
		this.statusWidth = statusWidth;
		this.skills = skills;
		skillRows = (skills.length + 1) / 2;
		padding = size.pixels(8);
		bodyFont = text.bodyFont;
		labelFont = text.labelFont;
		scrollbarWidth = size.pixels(14);
		rowHeight = Math.max(size.pixels(24), bodyHeight + padding);
		tabHeight = Math.max(size.pixels(29), labelHeight + 2 * padding);
		tabBaseline = (tabHeight + labelHeight) / 2 - 2;
		width = Math.min(surfaceWidth - 2 * padding,
			Math.max(size.pixels(402), 2 * minColumnWidth + scrollbarWidth + 3 * padding));
		left = surfaceWidth - size.pixels(6) - width;
		int topLimit = bottomLauncher ? padding : anchor;
		int bottomLimit = bottomLauncher ? anchor - padding : chatEntryTop;
		if (left < chatRight) {
			bottomLimit = Math.min(bottomLimit, chatEntryTop - padding);
		}
		int height = Math.min(size.pixels(465), bottomLimit - topLimit);
		top = bottomLauncher ? bottomLimit - height : topLimit;
		bottom = top + height;

		int contentHeight = 0;
		int nameWidth = Math.max(1, questStatusRight() - statusWidth - bodyLeft() - 3 * padding);
		for (int id = 0; id < questNames.length; id++) {
			if (questNames[id] == null) continue;
			List<String> lines = ClericTextLayout.wrap(questNames[id], nameWidth, measure);
			int rowSize = Math.max(rowHeight, lines.size() * bodyHeight + padding);
			quests.add(new QuestRow(id, lines, contentHeight, rowSize));
			contentHeight += rowSize;
		}
		questContentHeight = contentHeight;
	}

	boolean matches(int surfaceWidth, int anchor, boolean bottomLauncher, int chatRight, int chatEntryTop,
					GameInterfaceSettings.Size size, GameInterfaceSettings.TextSize text) {
		return this.surfaceWidth == surfaceWidth && this.anchor == anchor && this.bottomLauncher == bottomLauncher
			&& this.chatRight == chatRight && this.chatEntryTop == chatEntryTop && this.size == size && this.text == text;
	}

	boolean contains(int x, int y) {
		return x >= left && x < left + width && y >= top && y < bottom;
	}

	int tabAt(int x, int y) {
		if (!contains(x, y) || y >= top + tabHeight) return -1;
		return x < left + width / 2 ? STATS : QUESTS;
	}

	int listTop(int tab) {
		return top + tabHeight + padding + (tab == QUESTS ? rowHeight : 0);
	}

	int listBottom() {
		return bottom - padding;
	}

	int bodyLeft() {
		return left + padding;
	}

	int scrollbarLeft() {
		return left + width - padding - scrollbarWidth;
	}

	int questStatusRight() {
		return scrollbarLeft() - 2 * padding;
	}

	int columnWidth() {
		return (scrollbarLeft() - bodyLeft() - padding) / 2;
	}

	int columnLeft(int column) {
		return bodyLeft() + column * columnWidth();
	}

	int maxScroll(int tab) {
		int content = tab == QUESTS ? questContentHeight : (skillRows + STATS_EXTRA_ROWS) * rowHeight;
		return Math.max(0, content - (listBottom() - listTop(tab)));
	}

	int clampScroll(int tab, int offset) {
		return Math.max(0, Math.min(offset, maxScroll(tab)));
	}

	int statsRowY(int row, int scroll) {
		return listTop(STATS) + row * rowHeight - scroll;
	}

	int statsRowAt(int x, int y, int scroll) {
		if (!inRows(x, y, STATS)) return -1;
		return (y - listTop(STATS) + scroll) / rowHeight;
	}

	int skillAt(int x, int y, int scroll) {
		int row = statsRowAt(x, y, scroll);
		if (row < 0 || row >= skillRows || x >= columnLeft(2)) return -1;
		int index = row + (x >= columnLeft(1) ? skillRows : 0);
		return index < skills.length ? skills[index] : -1;
	}

	int questAt(int x, int y, int scroll) {
		if (!inRows(x, y, QUESTS)) return -1;
		int offset = y - listTop(QUESTS) + scroll;
		for (QuestRow row : quests) {
			if (offset >= row.offset && offset < row.offset + row.height) return row.id;
		}
		return -1;
	}

	private boolean inRows(int x, int y, int tab) {
		return x >= bodyLeft() && x < scrollbarLeft() && y >= listTop(tab) && y < listBottom();
	}

	static final class QuestRow {
		final int id;
		final List<String> lines;
		final int offset;
		final int height;

		QuestRow(int id, List<String> lines, int offset, int height) {
			this.id = id;
			this.lines = lines;
			this.offset = offset;
			this.height = height;
		}
	}
}
