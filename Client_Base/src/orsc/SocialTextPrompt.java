package orsc;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import orsc.graphics.two.Fonts;
import orsc.graphics.two.MudClientGraphics;

/** The focused editor and modal layout for name entry and private messages. */
final class SocialTextPrompt {
	enum Result { OPEN, SUBMIT, CANCEL }

	enum Action {
		ADD_FRIEND("Add Friend", "Add", 20),
		PRIVATE_MESSAGE("Private Message", "Send", 80),
		CLAN_INVITE("Invite to Clan", "Invite", 20),
		PARTY_INVITE("Invite to Party", "Invite", 20);

		final String title;
		final String submitLabel;
		final int limit;

		Action(String title, String submitLabel, int limit) {
			this.title = title;
			this.submitLabel = submitLabel;
			this.limit = limit;
		}
	}

	final Action action;
	final String recipient;
	private final ModalTextField editor;
	private Result result = Result.OPEN;

	SocialTextPrompt(Action action, String recipient) {
		this.action = action;
		this.recipient = recipient;
		editor = new ModalTextField(action.limit, key -> Fonts.inputFilterChars.indexOf(key) >= 0);
	}

	synchronized String value() {
		return editor.value();
	}

	private boolean canSubmit() {
		return !editor.value().trim().isEmpty();
	}

	synchronized void keyPressed(char key, int code, boolean shortcut) {
		if (result != Result.OPEN) return;
		if (code == KeyEvent.VK_ESCAPE || key == 27) {
			result = Result.CANCEL;
			return;
		}
		if (code == KeyEvent.VK_ENTER || key == '\r' || key == '\n') {
			if (canSubmit()) result = Result.SUBMIT;
			return;
		}
		editor.keyPressed(key, code, shortcut);
	}

	synchronized Result draw(mudclient client, int bottom) {
		if (result != Result.OPEN) return result;
		MudClientGraphics surface = client.getSurface();
		GameInterfaceSettings.Size size = GameInterfaceSettings.size();
		GameInterfaceSettings.TextSize style = GameInterfaceSettings.textSize();
		int bodyLine = surface.fontHeight(style.bodyFont);
		int labelLine = surface.fontHeight(style.labelFont);
		int pad = size.pixels(12);
		int width = Math.min(client.getGameWidth() - 2 * pad, size.pixels(recipient == null ? 420 : 608));
		int header = Math.max(size.pixels(46), bodyLine + 2 * pad);
		int buttonHeight = Math.max(size.pixels(34), labelLine + pad);
		int availableRows = (bottom - header - labelLine - buttonHeight - 7 * pad) / bodyLine;
		int rows = Math.min(recipient == null ? 2 : 3, Math.max(1, availableRows));
		int fieldHeight = rows * bodyLine + 2 * pad;
		int height = header + labelLine + fieldHeight + buttonHeight + 4 * pad;
		int x = (client.getGameWidth() - width) / 2;
		int y = Math.max(pad, (bottom - height) / 2);
		Rectangle field = new Rectangle(x + pad, y + header + labelLine + pad, width - 2 * pad, fieldHeight);
		int buttonLabelWidth = Math.max(surface.stringWidth(style.labelFont, "Cancel"),
			surface.stringWidth(style.labelFont, action.submitLabel));
		int buttonWidth = Math.min((field.width - pad) / 2, Math.max(size.pixels(88), buttonLabelWidth + 2 * pad));
		int actionsY = field.y + field.height + pad;
		Rectangle submit = new Rectangle(x + width - pad - buttonWidth, actionsY, buttonWidth, buttonHeight);
		Rectangle cancel = new Rectangle(submit.x - pad - buttonWidth, actionsY, buttonWidth, buttonHeight);
		int mouseX = client.getMouseX();
		int mouseY = client.getMouseY();
		boolean clicked = client.getMouseClick() == 1;
		boolean enabled = canSubmit();
		if (clicked && cancel.contains(mouseX, mouseY)) return result = Result.CANCEL;
		if (clicked && enabled && submit.contains(mouseX, mouseY)) return result = Result.SUBMIT;

		drawFrame(surface, new Rectangle(x, y, width, height), header, pad, action.title, style.bodyFont, bodyLine);
		String label = recipient == null ? "Player name" : "To " + recipient;
		label = ContextMenuLayout.fitLabel(label, field.width - pad, part -> surface.stringWidth(style.labelFont, part));
		surface.drawString(label, field.x, field.y - Math.max(5, pad / 2), 0xC4D0E0, style.labelFont);
		editor.draw(client, field, rows, style.bodyFont, pad);
		drawButton(surface, cancel, "Cancel", true, cancel.contains(mouseX, mouseY), style.labelFont, labelLine);
		drawButton(surface, submit, action.submitLabel, enabled, submit.contains(mouseX, mouseY),
			style.labelFont, labelLine);
		return Result.OPEN;
	}

	static void drawFrame(MudClientGraphics surface, Rectangle bounds, int header, int pad, String title,
						  int font, int line) {
		surface.drawBoxAlpha(bounds.x, bounds.y, bounds.width, bounds.height, 0x182333, 246);
		surface.drawVerticalGradient(bounds.x, bounds.y, bounds.width, header, 0x3B4C67, 0x28354A);
		surface.drawBoxBorder(bounds.x, bounds.width, bounds.y, bounds.height, 0x667993);
		surface.drawString(title, bounds.x + pad, bounds.y + (header + line) / 2 - 3, 0xFFFFFF, font);
	}

	static void drawButton(MudClientGraphics surface, Rectangle bounds, String label, boolean enabled,
								   boolean hover, int font, int line) {
		surface.drawBox(bounds.x, bounds.y, bounds.width, bounds.height,
			!enabled ? 0x253042 : hover ? 0x465873 : 0x34425A);
		surface.drawBoxBorder(bounds.x, bounds.width, bounds.y, bounds.height, 0x667993);
		surface.drawColoredStringCentered(bounds.x + bounds.width / 2, label,
			enabled ? 0xFFFFFF : 0x929FB1, 0, font, bounds.y + (bounds.height + line) / 2 - 3);
	}
}
