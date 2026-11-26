package orsc;

import com.openrsc.interfaces.misc.HiscoreData;
import java.awt.Rectangle;
import java.text.NumberFormat;
import java.util.Locale;
import orsc.graphics.two.MudClientGraphics;

/** Scaled view of the server's overall and per-skill rankings. */
final class HiscoresWindow {
	enum Action { NONE, CLOSE, GUIDE }

	private static final int TEXT = 0xE1E9F4;
	private static final int MUTED = 0xC4D0E0;
	private static final int GOLD = 0xFFDC82;
	private final NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.US);
	private final String title;
	private final boolean overall;
	private int firstRow;

	HiscoresWindow(String skill, int skillId) {
		overall = skillId == HiscoreData.OVERALL_ID;
		title = skill + " Hiscores";
	}

	Action draw(mudclient client, int bottom, int wheel) {
		MudClientGraphics surface = client.getSurface();
		GameInterfaceSettings.Size size = GameInterfaceSettings.size();
		GameInterfaceSettings.TextSize text = GameInterfaceSettings.textSize();
		int pad = size.pixels(12);
		int margin = Math.max(6, pad / 2);
		int width = Math.min(client.getGameWidth() - 2 * margin, size.pixels(844));
		int height = Math.min(bottom - 2 * margin, size.pixels(480));
		int x = (client.getGameWidth() - width) / 2;
		int y = (bottom - height) / 2;
		int labelLine = surface.fontHeight(text.labelFont);
		int titleLine = surface.fontHeight(text.bodyFont);
		int button = Math.max(size.pixels(32), labelLine + 8);
		int header = button + pad;
		int rowHeight = Math.max(size.pixels(30), labelLine + 8);
		int mouseX = client.getMouseX(), mouseY = client.getMouseY();
		boolean click = client.getMouseClick() == 1;

		Rectangle close = new Rectangle(x + width - pad - button, y + pad / 2, button, button);
		if (click && close.contains(mouseX, mouseY)) return Action.CLOSE;
		int guideWidth = Math.max(size.pixels(92), surface.stringWidth(text.labelFont, "Guide") + pad);
		Rectangle guide = new Rectangle(close.x - pad / 2 - guideWidth, close.y, guideWidth, button);
		if (!overall && click && guide.contains(mouseX, mouseY)) return Action.GUIDE;
		int titleRight = overall ? close.x : guide.x;
		SocialTextPrompt.drawFrame(surface, new Rectangle(x, y, width, height), header, pad,
			fit(surface, title, titleRight - x - 2 * pad, text.bodyFont), text.bodyFont, titleLine);
		SocialTextPrompt.drawButton(surface, close, "X", true, close.contains(mouseX, mouseY), text.labelFont, labelLine);
		if (!overall) {
			SocialTextPrompt.drawButton(surface, guide, "Guide", true, guide.contains(mouseX, mouseY), text.labelFont, labelLine);
		}

		client.requestHiscoresIfNeeded();
		HiscoreData data = client.getHiscoreData();
		int left = x + pad;
		int contentWidth = width - 2 * pad;
		int top = y + header + pad;
		int rail = Math.max(9, size.pixels(12));
		int font = text.labelFont;
		int rankX = left + pad;
		int nameX = rankX + Math.max(size.pixels(64), surface.stringWidth(font, "Rank") + pad);
		int xpRight = left + contentWidth - rail - pad;
		String levelTitle = overall ? "Total level" : "Level";
		int xpWidth = Math.max(size.pixels(140), surface.stringWidth(font, "99,999,999,999"));
		int levelRight = xpRight - xpWidth - pad;
		int nameWidth = levelRight - Math.max(size.pixels(88), surface.stringWidth(font, levelTitle)) - pad - nameX;
		int baseline = top + (rowHeight + labelLine) / 2 - 3;
		surface.drawBox(left, top, contentWidth, rowHeight, 0x28384D);
		surface.drawString("Rank", rankX, baseline, MUTED, font);
		surface.drawString("Player", nameX, baseline, MUTED, font);
		drawRight(surface, levelTitle, levelRight, baseline, MUTED, font);
		drawRight(surface, overall ? "Total XP" : "Experience", xpRight, baseline, MUTED, font);

		top += rowHeight;
		baseline += rowHeight;
		surface.drawBox(left, top, contentWidth, rowHeight, 0x394D60);
		if (!data.isLoaded()) {
			surface.drawString("Loading hiscores...", rankX, baseline, GOLD, font);
			return Action.NONE;
		}
		String ownRank = data.getOwnRank() > 0 ? rank(data.getOwnRank()) : "-";
		drawRow(surface, ownRank, "Your ranking", data.getOwnLevel(), data.getOwnExp(),
			rankX, nameX, nameWidth, levelRight, xpRight, baseline, GOLD, font);

		top += rowHeight;
		int count = Math.min(data.getCount(), HiscoreData.MAX_ENTRIES);
		if (count == 0) {
			surface.drawString("No players are ranked yet.", rankX, baseline + rowHeight, MUTED, font);
			return Action.NONE;
		}
		int visible = Math.max(1, (y + height - pad - top) / rowHeight);
		Rectangle list = new Rectangle(left, top, contentWidth, visible * rowHeight);
		int max = Math.max(0, count - visible);
		firstRow = SkillGuideWindow.scroll(surface, list, rail, visible, max,
			firstRow + (list.contains(mouseX, mouseY) ? wheel : 0), mouseX, mouseY, click);
		int rowWidth = contentWidth - (max > 0 ? rail : 0);
		for (int i = firstRow; i < Math.min(count, firstRow + visible); i++) {
			int rowY = top + (i - firstRow) * rowHeight;
			boolean own = i == data.getOwnListIndex();
			surface.drawBox(left, rowY, rowWidth, rowHeight, own ? 0x354D53 : (i % 2 == 0 ? 0x202E41 : 0x25344A));
			drawRow(surface, rank(i + 1), data.getName(i), data.getLevel(i), data.getExp(i),
				rankX, nameX, nameWidth, levelRight, xpRight, rowY + (rowHeight + labelLine) / 2 - 3,
				own ? GOLD : TEXT, font);
		}
		return Action.NONE;
	}

	private String rank(int rank) {
		return "#" + numbers.format(rank);
	}

	private void drawRow(MudClientGraphics surface, String rank, String name, int level, long xp,
						 int rankX, int nameX, int nameWidth, int levelRight, int xpRight,
						 int y, int color, int font) {
		surface.drawString(fit(surface, rank, nameX - rankX - 4, font), rankX, y, color, font);
		surface.drawString(fit(surface, name, nameWidth, font), nameX, y, color, font);
		drawRight(surface, numbers.format(level), levelRight, y, color, font);
		drawRight(surface, numbers.format(xp), xpRight, y, color, font);
	}

	private static void drawRight(MudClientGraphics surface, String text, int right, int y, int color, int font) {
		surface.drawString(text, right - surface.stringWidth(font, text), y, color, font);
	}

	private static String fit(MudClientGraphics surface, String text, int width, int font) {
		return ContextMenuLayout.fitLabel(text, width, value -> surface.stringWidth(font, value));
	}
}
