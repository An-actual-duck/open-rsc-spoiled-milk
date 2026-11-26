package orsc;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import orsc.graphics.two.MudClientGraphics;

/** A guide with top-level sections, section-specific topics and scrollable articles. */
public final class SkillGuideWindow {
	public enum Action { NONE, CLOSE, HISCORES }

	private final String title;
	private final List<String> sections;
	private final IntFunction<List<Topic>> loadSection;
	private int section = -1, topic, firstTopic, articleScroll;
	private List<Topic> topics;
	private List<WrappedArticle> articles;
	private int wrappedWidth, wrappedFont, wrappedPadding, wrappedIconSize;
	private boolean wrappedIcons;
	private int articleHeight;

	public SkillGuideWindow(String title, List<String> sections, IntFunction<List<Topic>> loadSection) {
		this.title = title;
		this.sections = sections;
		this.loadSection = loadSection;
	}

	public Action draw(mudclient client, int bottom, boolean showHiscores, int wheel) {
		MudClientGraphics surface = client.getSurface();
		GameInterfaceSettings.Size size = GameInterfaceSettings.size();
		GameInterfaceSettings.TextSize text = GameInterfaceSettings.textSize();
		int pad = size.pixels(12);
		int margin = Math.max(6, pad / 2);
		int width = Math.min(client.getGameWidth() - margin * 2, size.pixels(844));
		int height = Math.min(bottom - margin * 2, size.pixels(480));
		int x = (client.getGameWidth() - width) / 2;
		int y = (bottom - height) / 2;
		int bodyLine = surface.fontHeight(text.bodyFont);
		int labelLine = surface.fontHeight(text.labelFont);
		int tabHeight = Math.max(size.pixels(32), labelLine + 8);
		int headerHeight = tabHeight + pad;
		int mouseX = client.getMouseX(), mouseY = client.getMouseY();
		boolean click = client.getMouseClick() == 1;
		Rectangle close = new Rectangle(x + width - pad - tabHeight, y + pad / 2, tabHeight, tabHeight);
		int hiscoreWidth = Math.max(size.pixels(92), surface.stringWidth(text.labelFont, "Hiscores") + pad);
		Rectangle hiscores = new Rectangle(close.x - pad / 2 - hiscoreWidth, close.y, hiscoreWidth, tabHeight);
		if (click && close.contains(mouseX, mouseY)) return Action.CLOSE;
		if (click && showHiscores && hiscores.contains(mouseX, mouseY)) return Action.HISCORES;

		if (section < 0) selectSection(0);
		List<Rectangle> tabs = layoutSections(surface, x + pad, y + headerHeight, width - 2 * pad,
			tabHeight, pad, text.labelFont);
		for (int i = 0; i < tabs.size(); i++) {
			if (click && i != section && tabs.get(i).contains(mouseX, mouseY)) selectSection(i);
		}

		surface.drawBoxAlpha(x, y, width, height, InterfacePalette.PANEL, 248);
		surface.drawVerticalGradient(x, y, width, headerHeight, InterfacePalette.HEADER_LIGHT, InterfacePalette.HEADER_DARK);
		surface.drawBoxBorder(x, width, y, height, InterfacePalette.BORDER);
		int titleRight = showHiscores ? hiscores.x : close.x;
		String fittedTitle = fit(surface, title + " Guide", titleRight - x - 2 * pad, text.bodyFont);
		surface.drawString(fittedTitle, x + pad, y + (headerHeight + bodyLine) / 2 - 3, 0xFFFFFF, text.bodyFont);
		drawButton(surface, close, "X", text.labelFont, false, mouseX, mouseY);
		if (showHiscores) drawButton(surface, hiscores, "Hiscores", text.labelFont, false, mouseX, mouseY);
		for (int i = 0; i < tabs.size(); i++) {
			drawButton(surface, tabs.get(i), sections.get(i), text.labelFont, i == section, mouseX, mouseY);
		}

		int top = tabs.isEmpty() ? y + headerHeight : tabs.get(tabs.size() - 1).y + tabHeight + pad;
		int contentHeight = y + height - pad - top;
		if (contentHeight <= bodyLine) return Action.NONE;
		int navWidth = Math.min((width - 3 * pad) / 3, Math.max(size.pixels(178),
			surface.stringWidth(text.labelFont, "Other recipes") + 2 * pad));
		Rectangle navigation = new Rectangle(x + pad, top, navWidth, contentHeight);
		Rectangle page = new Rectangle(navigation.x + navigation.width + pad, top,
			width - navWidth - 3 * pad, contentHeight);
		int railWidth = Math.max(9, size.pixels(12));
		boolean overTopics = navigation.contains(mouseX, mouseY);
		drawTopics(surface, navigation, railWidth, tabHeight, text.labelFont, overTopics ? wheel : 0,
			mouseX, mouseY, click);
		drawPage(surface, page, railWidth, pad, text.bodyFont, bodyLine, size.pixels(30),
			page.contains(mouseX, mouseY) ? wheel : 0, mouseX, mouseY, click);
		return Action.NONE;
	}

	private List<Rectangle> layoutSections(MudClientGraphics surface, int x, int y, int width,
										   int height, int pad, int font) {
		List<Rectangle> tabs = new ArrayList<>();
		for (int start = 0; start < sections.size();) {
			int used = 0;
			int end = start;
			while (end < sections.size()) {
				int next = Math.min(width, surface.stringWidth(font, sections.get(end)) + pad * 2);
				if (used + next > width) break;
				used += next;
				end++;
			}
			int left = x;
			for (int i = start; i < end; i++) {
				int extra = width - used;
				int tabWidth = Math.min(width, surface.stringWidth(font, sections.get(i)) + pad * 2)
					+ extra * (i - start + 1) / (end - start) - extra * (i - start) / (end - start);
				tabs.add(new Rectangle(left, y, tabWidth, height));
				left += tabWidth;
			}
			y += height + 2;
			start = end;
		}
		return tabs;
	}

	private void selectSection(int index) {
		section = index;
		topics = loadSection.apply(index);
		firstTopic = 0;
		selectTopic(0);
	}

	private void selectTopic(int index) {
		topic = index;
		articleScroll = 0;
		articles = null;
	}

	private void drawTopics(MudClientGraphics surface, Rectangle bounds, int railWidth, int rowHeight,
							int font, int wheel, int mouseX, int mouseY, boolean click) {
		surface.drawBox(bounds.x, bounds.y, bounds.width, bounds.height, InterfacePalette.ROW_ALTERNATE);
		int visible = Math.max(1, bounds.height / rowHeight);
		int max = Math.max(0, topics.size() - visible);
		int buttonWidth = bounds.width - (max > 0 ? railWidth : 0);
		firstTopic = scroll(surface, bounds, railWidth, visible, max, firstTopic + wheel,
			mouseX, mouseY, click);
		for (int row = 0; row < visible && firstTopic + row < topics.size(); row++) {
			int index = firstTopic + row;
			Rectangle button = new Rectangle(bounds.x, bounds.y + row * rowHeight, buttonWidth,
				Math.min(rowHeight - 1, bounds.height));
			if (click && index != topic && button.contains(mouseX, mouseY)) selectTopic(index);
			drawButton(surface, button, topics.get(index).label, font, index == topic, mouseX, mouseY);
		}
	}

	private void drawPage(MudClientGraphics surface, Rectangle page, int railWidth, int pad,
						  int font, int lineHeight, int iconSize, int wheel, int mouseX, int mouseY, boolean click) {
		surface.drawBox(page.x, page.y, page.width, page.height, InterfacePalette.ROW_ALTERNATE);
		int heading = Math.max(lineHeight + 8, pad * 3);
		surface.drawBox(page.x, page.y, page.width, heading, InterfacePalette.ROW);
		Topic current = topics.get(topic);
		surface.drawString(fit(surface, current.label, page.width - 2 * pad, font), page.x + pad,
			page.y + (heading + lineHeight) / 2 - 3, 0xFFDC82, font);
		Rectangle body = new Rectangle(page.x, page.y + heading, page.width, page.height - heading);
		if (body.height <= 0) return;
		int available = body.width - railWidth - 2 * pad;
		int rowPad = Math.max(4, pad / 2);
		boolean hasRoomForIcon = available >= iconSize * 3;
		updateArticles(current, Math.max(1, available), font,
			lineHeight, pad, rowPad, hasRoomForIcon, iconSize, value -> surface.stringWidth(font, value));
		articleScroll = scroll(surface, body, railWidth, body.height, Math.max(0, articleHeight - body.height),
			articleScroll + wheel * lineHeight, mouseX, mouseY, click);
		surface.setClip(body.x, body.x + body.width - railWidth, body.y + body.height, body.y);
		try {
			for (WrappedArticle row : articles) {
				int top = body.y + row.top - articleScroll;
				if (top + row.height <= body.y) continue;
				if (top >= body.y + body.height) break;
				surface.drawLineHoriz(body.x + pad, top + row.height - 1, available, InterfacePalette.CONTROL_HOVER);
				int textX = body.x + pad;
				if (hasRoomForIcon && row.article.icon != null) {
					row.article.icon.draw(textX, top + (row.height - iconSize) / 2, iconSize);
				}
				int textHeight = (row.heading.size() + row.details.size()) * lineHeight;
				int baseline = top + (row.height - textHeight) / 2 + lineHeight - 3;
				if (!row.article.level.isEmpty()) {
					int levelX = textX + row.indent - row.levelWidth;
					surface.drawString(row.article.level, levelX, baseline, 0xFFDC82, font);
				}
				for (String line : row.heading) {
					surface.drawString(line, textX + row.indent, baseline, 0xFFDC82, font);
					baseline += lineHeight;
				}
				for (String line : row.details) {
					surface.drawString(line, textX + row.indent, baseline, InterfacePalette.TEXT, font);
					baseline += lineHeight;
				}
			}
		} finally {
			surface.clearClip();
		}
	}

	private void updateArticles(Topic current, int width, int font, int lineHeight, int pad, int rowPad, boolean icons,
								int iconSize, ToIntFunction<String> measure) {
		if (articles != null && width == wrappedWidth && font == wrappedFont && icons == wrappedIcons
			&& pad == wrappedPadding && iconSize == wrappedIconSize) return;
		wrappedWidth = width;
		wrappedFont = font;
		wrappedIcons = icons;
		wrappedPadding = pad;
		wrappedIconSize = iconSize;
		articles = new ArrayList<>();
		articleHeight = 0;
		int levelColumn = 0;
		for (Article article : current.articles) {
			if (!article.level.isEmpty()) levelColumn = Math.max(levelColumn, measure.applyAsInt(article.level) + pad);
		}
		for (Article article : current.articles) {
			boolean hasIcon = icons && article.icon != null;
			int levelWidth = article.level.isEmpty() ? 0 : levelColumn;
			int indent = (hasIcon ? iconSize + pad : 0) + levelWidth;
			int textWidth = Math.max(1, width - indent);
			List<String> heading = ClericTextLayout.wrap(article.title, textWidth, measure);
			List<String> details = new ArrayList<>();
			for (String detail : article.paragraphs) details.addAll(ClericTextLayout.wrap(detail, textWidth, measure));
			int rowHeight = 2 * rowPad + Math.max(hasIcon ? iconSize : 0,
				(heading.size() + details.size()) * lineHeight);
			articles.add(new WrappedArticle(article, heading, details, articleHeight, rowHeight, indent, levelWidth));
			articleHeight += rowHeight;
		}
	}

	static int scroll(MudClientGraphics surface, Rectangle bounds, int width, int visible, int max, int offset,
							  int mouseX, int mouseY, boolean click) {
		if (max == 0) return 0;
		int x = bounds.x + bounds.width - width;
		if (click && mouseX >= x && bounds.contains(mouseX, mouseY)) {
			offset = (mouseY - bounds.y) * max / Math.max(1, bounds.height - 1);
		}
		offset = Math.max(0, Math.min(offset, max));
		int thumb = Math.min(bounds.height, Math.max(width, bounds.height * visible / (visible + max)));
		int top = bounds.y + (bounds.height - thumb) * offset / max;
		surface.drawBox(x, bounds.y, width, bounds.height, InterfacePalette.ROW);
		surface.drawBox(x + 2, top, width - 4, thumb, InterfacePalette.SCROLL_THUMB);
		return offset;
	}

	private static void drawButton(MudClientGraphics surface, Rectangle button, String label, int font,
								   boolean selected, int mouseX, int mouseY) {
		surface.drawBox(button.x, button.y, button.width - 1, button.height,
			selected ? InterfacePalette.CONTROL_HOVER : button.contains(mouseX, mouseY) ? InterfacePalette.CONTROL : InterfacePalette.ROW_ALTERNATE);
		if (selected) {
			surface.drawLineHoriz(button.x, button.y + button.height - 2, button.width - 1, 0xEACB78);
		}
		surface.drawColoredStringCentered(button.x + button.width / 2, fit(surface, label, button.width - 6, font),
			selected ? 0xFFDC82 : InterfacePalette.TEXT, 0, font,
			button.y + (button.height + surface.fontHeight(font)) / 2 - 3);
	}

	private static String fit(MudClientGraphics surface, String label, int width, int font) {
		return ContextMenuLayout.fitLabel(label, width, value -> surface.stringWidth(font, value));
	}

	public static final class Topic {
		final String label;
		final List<Article> articles;

		public Topic(String label, List<Article> articles) {
			this.label = label;
			this.articles = articles;
		}
	}

	public static final class Article {
		final String title, level;
		final List<String> paragraphs;
		final Icon icon;

		public Article(String title, List<String> paragraphs) {
			this(title, paragraphs, "", null);
		}

		public static Article unlock(String detail, String level, Icon icon) {
			String label = level.isEmpty() ? "" : "Lvl " + level;
			return new Article("", Collections.singletonList(detail), label, icon);
		}

		private Article(String title, List<String> paragraphs, String level, Icon icon) {
			this.title = title;
			this.paragraphs = paragraphs;
			this.level = level;
			this.icon = icon;
		}
	}

	@FunctionalInterface
	public interface Icon {
		void draw(int x, int y, int size);
	}

	private static final class WrappedArticle {
		final Article article;
		final List<String> heading, details;
		final int top, height, indent, levelWidth;

		WrappedArticle(Article article, List<String> heading, List<String> details, int top, int height, int indent, int levelWidth) {
			this.article = article;
			this.heading = heading;
			this.details = details;
			this.top = top;
			this.height = height;
			this.indent = indent;
			this.levelWidth = levelWidth;
		}
	}
}
