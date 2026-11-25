package orsc;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import orsc.graphics.two.Fonts;
import orsc.graphics.two.MudClientGraphics;

/** Player name, report reason and confirmation for the scaled in-game interface. */
final class ReportPlayerWindow {
	static final String TITLE = "Report Player";
	// The outgoing report packet uses these reasons in order, starting at one.
	private static final String[] REASONS = {
		"Buying or selling an account", "Encouraging rule-breaking",
		"Staff impersonation", "Macroing or use of bots",
		"Scamming", "Exploiting a bug",
		"Seriously offensive language", "Solicitation",
		"Disruptive behaviour", "Offensive account name",
		"Real-life threats", "Asking for or providing contact information",
		"Breaking real-world laws", "Advertising websites"
	};
	private static final int COLUMNS = 2;
	private static final int ROWS = REASONS.length / COLUMNS;
	static final Submission CANCEL = new Submission("", 0, false);

	static final class Submission {
		final String target;
		final int reason;
		final boolean mute;

		Submission(String target, int reason, boolean mute) {
			this.target = target;
			this.reason = reason;
			this.mute = mute;
		}
	}

	private final ModalTextField name = new ModalTextField(20, key -> Fonts.inputFilterChars.indexOf(key) >= 0);
	private final String muteLabel;
	private int selectedReason;
	private int firstRow;
	private int scroll;
	private boolean mute;
	private Submission result;
	private int reasonWidth;
	private int reasonFont = -1;
	private List<List<String>> reasonLines;
	private int longestReason;

	ReportPlayerWindow(String target, String muteLabel) {
		name.setValue(target);
		this.muteLabel = muteLabel;
	}

	synchronized void keyPressed(char key, int code, boolean shortcut) {
		if (result != null) return;
		switch (code) {
			case KeyEvent.VK_ESCAPE: result = CANCEL; break;
			case KeyEvent.VK_PAGE_UP: scroll -= ROWS; break;
			case KeyEvent.VK_PAGE_DOWN: scroll += ROWS; break;
			default:
				if (key == 27) result = CANCEL;
				else name.keyPressed(key, code, shortcut);
		}
	}

	synchronized void scroll(int amount) {
		scroll += amount;
	}

	synchronized Submission draw(mudclient client, int bottom) {
		if (result != null) return result;
		MudClientGraphics surface = client.getSurface();
		GameInterfaceSettings.Size size = GameInterfaceSettings.size();
		GameInterfaceSettings.TextSize text = GameInterfaceSettings.textSize();
		int bodyLine = surface.fontHeight(text.bodyFont);
		int labelLine = surface.fontHeight(text.labelFont);
		int pad = size.pixels(12);
		int gap = size.pixels(6);
		int width = Math.min(client.getGameWidth() - 2 * pad, size.pixels(730));
		int inner = width - 2 * pad;
		int columnWidth = (inner - gap) / COLUMNS;
		wrapReasons(surface, text.labelFont, columnWidth - 2 * gap);

		int header = Math.max(size.pixels(42), bodyLine + pad);
		int fieldHeight = bodyLine + 2 * gap;
		int reasonHeader = Math.max(size.pixels(28), labelLine + gap);
		int rowHeight = Math.max(size.pixels(30), longestReason * labelLine + gap);
		int muteHeight = muteLabel == null ? 0 : Math.max(size.pixels(28), labelLine + gap) + gap;
		int buttonHeight = Math.max(size.pixels(34), labelLine + gap);
		int chrome = header + 4 * pad + 2 * gap + labelLine + fieldHeight + reasonHeader
			+ labelLine + muteHeight + buttonHeight;
		int visibleRows = Math.min(ROWS, Math.max(1, (bottom - 2 * pad - chrome) / (rowHeight + gap)));
		int height = chrome + visibleRows * (rowHeight + gap);
		int x = (client.getGameWidth() - width) / 2;
		int y = Math.max(pad, (bottom - height) / 2);
		int left = x + pad;
		int right = x + width - pad;
		int labelY = y + header + pad + labelLine;
		Rectangle field = new Rectangle(left, labelY + gap, inner, fieldHeight);
		int reasonHeaderY = field.y + field.height + pad;
		int reasonsY = reasonHeaderY + reasonHeader + gap;
		int selectionY = reasonsY + visibleRows * (rowHeight + gap) + labelLine;
		int buttonsY = selectionY + muteHeight + pad;
		int buttonWidth = Math.max(size.pixels(112), surface.stringWidth(text.labelFont, "Send Report") + 2 * gap);
		Rectangle send = new Rectangle(right - buttonWidth, buttonsY, buttonWidth, buttonHeight);
		Rectangle cancel = new Rectangle(send.x - gap - buttonWidth, buttonsY, buttonWidth, buttonHeight);
		int mouseX = client.getMouseX();
		int mouseY = client.getMouseY();
		boolean clicked = client.getMouseClick() == 1;
		if (clicked && cancel.contains(mouseX, mouseY)) return result = CANCEL;

		SocialTextPrompt.drawFrame(surface, new Rectangle(x, y, width, height), header, pad,
			TITLE, text.bodyFont, bodyLine);
		surface.drawString("Player name", left, labelY, 0xC4D0E0, text.labelFont);
		name.draw(client, field, 1, text.bodyFont, gap);
		surface.drawString("Reason", left, reasonHeaderY + (reasonHeader + labelLine) / 2 - 3, 0xC4D0E0, text.labelFont);

		int lastRow = ROWS - visibleRows;
		firstRow = Math.max(0, Math.min(lastRow, firstRow + scroll));
		scroll = 0;
		if (lastRow > 0) {
			int arrowWidth = Math.max(size.pixels(32), surface.stringWidth(text.labelFont, ">") + 2 * gap);
			Rectangle next = new Rectangle(right - arrowWidth, reasonHeaderY, arrowWidth, reasonHeader);
			Rectangle previous = new Rectangle(next.x - gap - arrowWidth, reasonHeaderY, arrowWidth, reasonHeader);
			boolean overNext = next.contains(mouseX, mouseY);
			boolean overPrevious = previous.contains(mouseX, mouseY);
			if (clicked && overNext) firstRow = Math.min(lastRow, firstRow + visibleRows);
			if (clicked && overPrevious) firstRow = Math.max(0, firstRow - visibleRows);
			SocialTextPrompt.drawButton(surface, previous, "<", firstRow > 0, overPrevious, text.labelFont, labelLine);
			SocialTextPrompt.drawButton(surface, next, ">", firstRow < lastRow, overNext, text.labelFont, labelLine);
		}

		for (int row = 0; row < visibleRows; row++) {
			for (int column = 0; column < COLUMNS; column++) {
				int reason = (firstRow + row) * COLUMNS + column + 1;
				Rectangle bounds = new Rectangle(left + column * (columnWidth + gap),
					reasonsY + row * (rowHeight + gap), columnWidth, rowHeight);
				boolean hovered = bounds.contains(mouseX, mouseY);
				if (clicked && hovered) selectedReason = reason;
				drawReason(surface, bounds, reasonLines.get(reason - 1), reason == selectedReason, hovered,
					text.labelFont, labelLine, gap);
			}
		}

		if (selectedReason > 0) {
			String selection = ContextMenuLayout.fitLabel("Selected: " + REASONS[selectedReason - 1], inner,
				part -> surface.stringWidth(text.labelFont, part));
			surface.drawString(selection, left, selectionY, 0xFFDC82, text.labelFont);
		}
		if (muteLabel != null) {
			Rectangle muteButton = new Rectangle(left, selectionY + gap,
				surface.stringWidth(text.labelFont, "[X] " + muteLabel) + 2 * gap, muteHeight - gap);
			boolean hovered = muteButton.contains(mouseX, mouseY);
			if (clicked && hovered) mute = !mute;
			SocialTextPrompt.drawButton(surface, muteButton, (mute ? "[X] " : "[ ] ") + muteLabel,
				true, hovered, text.labelFont, labelLine);
		}
		String target = name.value().trim();
		boolean canSend = !target.isEmpty() && selectedReason > 0;
		if (clicked && canSend && send.contains(mouseX, mouseY)) {
			return result = new Submission(target, selectedReason, mute);
		}
		SocialTextPrompt.drawButton(surface, cancel, "Cancel", true, cancel.contains(mouseX, mouseY), text.labelFont, labelLine);
		SocialTextPrompt.drawButton(surface, send, "Send Report", canSend, send.contains(mouseX, mouseY),
			text.labelFont, labelLine);
		return null;
	}

	private void wrapReasons(MudClientGraphics surface, int font, int width) {
		if (reasonWidth == width && reasonFont == font) return;
		reasonWidth = width;
		reasonFont = font;
		reasonLines = new ArrayList<>(REASONS.length);
		longestReason = 1;
		for (String reason : REASONS) {
			List<String> lines = ClericTextLayout.wrap(reason, width, part -> surface.stringWidth(font, part));
			reasonLines.add(lines);
			longestReason = Math.max(longestReason, lines.size());
		}
	}

	private static void drawReason(MudClientGraphics surface, Rectangle bounds, List<String> lines,
								   boolean selected, boolean hovered, int font, int line, int padding) {
		surface.drawBox(bounds.x, bounds.y, bounds.width, bounds.height,
			selected ? 0x4C526D : hovered ? 0x465873 : 0x2B394E);
		surface.drawBoxBorder(bounds.x, bounds.width, bounds.y, bounds.height, selected ? 0xD2B873 : 0x516679);
		int baseline = bounds.y + (bounds.height - lines.size() * line) / 2 + line - 3;
		for (String part : lines) {
			surface.drawString(part, bounds.x + padding, baseline, selected ? 0xFFDC82 : 0xFFFFFF, font);
			baseline += line;
		}
	}
}
