package orsc;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import orsc.graphics.two.MudClientGraphics;

/** Scaled guide pages with shared drawing, tab, and scroll bounds. */
final class QuestGuideWindow {
	private static final String[] TABS = {"Overview", "Progress", "Rewards"};
	final int questId;
	private final String title;
	private final Section[] overview, rewards;
	private final String notStarted, inProgress, completed;
	private int page, firstRow;
	private int wrappedPage = -1, wrappedStage, wrappedWidth, wrappedFont;
	private List<Row> rows;

	QuestGuideWindow(int questId, String title, Section[] overview, String notStarted, String inProgress,
					 String completed, Section[] rewards) {
		this.questId = questId;
		this.title = title;
		this.overview = overview;
		this.notStarted = notStarted;
		this.inProgress = inProgress;
		this.completed = completed;
		this.rewards = rewards;
	}

	boolean draw(mudclient client, int bottom, int stage, int wheel) {
		MudClientGraphics surface = client.getSurface();
		GameInterfaceSettings.Size size = GameInterfaceSettings.size();
		GameInterfaceSettings.TextSize text = GameInterfaceSettings.textSize();
		int padding = size.pixels(12);
		int bodyLine = surface.fontHeight(text.bodyFont);
		int labelLine = surface.fontHeight(text.labelFont);
		int rowHeight = Math.max(size.pixels(27), bodyLine + 6);
		int margin = Math.max(6, padding / 2);
		int width = Math.min(client.getGameWidth() - 2 * margin, size.pixels(672));
		int headerHeight = Math.max(size.pixels(58), bodyLine + labelLine + padding);
		int tabHeight = Math.max(size.pixels(36), bodyLine + 8);
		int height = Math.min(bottom - 2 * margin, size.pixels(384));
		int x = (client.getGameWidth() - width) / 2;
		int y = (bottom - height) / 2;
		int tabsY = y + headerHeight;
		int contentY = tabsY + tabHeight + padding;
		int contentHeight = height - headerHeight - tabHeight - 2 * padding;
		int visible = Math.max(1, contentHeight / rowHeight);
		int scrollbarWidth = Math.max(9, size.pixels(15));
		int closeWidth = Math.max(size.pixels(32), labelLine + 8);
		Rectangle close = new Rectangle(x + width - padding - closeWidth, y + padding, closeWidth, closeWidth);
		int mouseX = client.getMouseX(), mouseY = client.getMouseY();
		boolean clicked = client.getMouseClick() == 1;
		if (clicked && close.contains(mouseX, mouseY)) return true;
		int tab = -1;
		if (mouseY >= tabsY && mouseY < tabsY + tabHeight && mouseX >= x && mouseX < x + width) {
			for (int index = 0; index < TABS.length; index++) {
				if (mouseX < x + width * (index + 1) / TABS.length) {
					tab = index;
					break;
				}
			}
		}
		if (clicked && tab >= 0 && tab != page) {
			page = tab;
			firstRow = 0;
		}

		int textWidth = width - 4 * padding - scrollbarWidth;
		updateRows(stage, textWidth, text.bodyFont, value -> surface.stringWidth(text.bodyFont, value));
		int maxScroll = Math.max(0, rows.size() - visible);
		firstRow = Math.max(0, Math.min(maxScroll, firstRow + wheel));
		int railX = x + width - padding - scrollbarWidth;
		int scrollHeight = visible * rowHeight;
		if (clicked && maxScroll > 0 && mouseX >= railX && mouseX < railX + scrollbarWidth
			&& mouseY >= contentY && mouseY < contentY + scrollHeight) {
			firstRow = (int) Math.round((mouseY - contentY) * (double) maxScroll / Math.max(1, scrollHeight - 1));
		}

		surface.drawBoxAlpha(x, y, width, height, 0x182333, 248);
		surface.drawVerticalGradient(x, y, width, headerHeight, 0x3B4C67, 0x28354A);
		surface.drawBoxBorder(x, width, y, height, 0x667993);
		String name = ContextMenuLayout.fitLabel(title, width - 3 * padding - closeWidth,
			value -> surface.stringWidth(text.bodyFont, value));
		int titleBaseline = y + (headerHeight - bodyLine - labelLine) / 2 + bodyLine - 3;
		surface.drawString(name, x + padding, titleBaseline, 0xFFFFFF, text.bodyFont);
		String progress = stage < 0 ? "Completed" : stage == 0 ? "Not started" : "In progress";
		int progressColor = stage < 0 ? 0x9FE0AC : stage == 0 ? 0xC4D0E0 : 0xFFDC82;
		surface.drawString(progress, x + padding, titleBaseline + labelLine, progressColor, text.labelFont);
		surface.drawBox(close.x, close.y, close.width, close.height,
			close.contains(mouseX, mouseY) ? 0x495E7A : 0x28354A);
		surface.drawBoxBorder(close.x, close.width, close.y, close.height, 0x667993);
		surface.drawColoredStringCentered(close.x + close.width / 2, "X", 0xFFFFFF, 0, text.labelFont,
			close.y + (close.height + labelLine) / 2 - 3);
		for (int index = 0; index < TABS.length; index++) {
			int left = x + width * index / TABS.length;
			int right = x + width * (index + 1) / TABS.length;
			surface.drawBox(left + 1, tabsY, right - left - 1, tabHeight,
				index == page ? 0x445774 : index == tab ? 0x334760 : 0x243246);
			if (index == page) surface.drawLineHoriz(left + 1, tabsY + tabHeight - 2, right - left - 1, 0xEACB78);
			surface.drawColoredStringCentered((left + right) / 2, TABS[index], index == page ? 0xFFDC82 : 0xD8E1EE,
				0, text.bodyFont, tabsY + (tabHeight + bodyLine) / 2 - 3);
		}
		for (int index = 0; index < visible && firstRow + index < rows.size(); index++) {
			Row row = rows.get(firstRow + index);
			int top = contentY + index * rowHeight;
			if (row.heading) surface.drawBox(x + padding, top, railX - x - padding - 3, rowHeight - 1, 0x28384D);
			surface.drawString(row.label, x + 2 * padding, top + (rowHeight + bodyLine) / 2 - 3,
				row.heading ? 0xFFDC82 : 0xE1E9F4, text.bodyFont);
		}
		if (maxScroll > 0) {
			int thumbHeight = Math.max(scrollbarWidth, scrollHeight * visible / rows.size());
			int thumbY = contentY + (scrollHeight - thumbHeight) * firstRow / maxScroll;
			surface.drawBox(railX, contentY, scrollbarWidth, scrollHeight, 0x283548);
			surface.drawBox(railX + 2, thumbY, scrollbarWidth - 4, thumbHeight, 0x8799B2);
		}
		return false;
	}

	private void updateRows(int stage, int width, int font, ToIntFunction<String> measure) {
		if (wrappedPage == page && wrappedStage == stage && wrappedWidth == width && wrappedFont == font) return;
		wrappedPage = page;
		wrappedStage = stage;
		wrappedWidth = width;
		wrappedFont = font;
		Section[] sections = page == 0 ? overview : page == 2 ? rewards : new Section[] {progressSection(stage)};
		rows = new ArrayList<>();
		for (Section section : sections) {
			if (!rows.isEmpty()) rows.add(new Row("", false));
			rows.add(new Row(section.heading, true));
			for (String paragraph : section.details) {
				for (String line : ClericTextLayout.wrap(paragraph, width, measure)) rows.add(new Row(line, false));
			}
		}
	}

	private Section progressSection(int stage) {
		if (stage < 0) {
			return completed == null ? new Section("Quest complete") : new Section("Quest complete", completed);
		}
		String step = stage == 0 ? notStarted : inProgress;
		if (step != null) return new Section("Next step", step);
		return new Section("Walkthrough", "Detailed walkthrough unavailable.");
	}

	static final class Section {
		final String heading;
		final String[] details;

		Section(String heading, String... details) {
			this.heading = heading;
			this.details = details;
		}
	}

	private static final class Row {
		final String label;
		final boolean heading;

		Row(String label, boolean heading) {
			this.label = label;
			this.heading = heading;
		}
	}
}
