package orsc;

import com.openrsc.data.DataOperations;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import orsc.graphics.two.MudClientGraphics;

/** The signed-in password form. Password fields render only their masks. */
final class PasswordChangeWindow {
	private static final String TITLE = "Change Password";
	private static final String REQUIREMENTS = "Use 5-20 letters, numbers or underscores.";
	private static final String[] LABELS = {"Current password", "New password", "Confirm new password"};
	private static final int CURRENT = 0;
	private static final int NEW = 1;
	private static final int CONFIRM = 2;
	static final Submission CLOSE = new Submission(null, null);

	enum Reply {
		CHANGED("Your password was successfully changed!", "Password changed."),
		INCORRECT("No changes made, your current password did not match", "The current password was incorrect."),
		LENGTH("No changes made, your new password must be 4-20 characters.", REQUIREMENTS);

		private final String serverMessage;
		final String displayMessage;

		Reply(String serverMessage, String displayMessage) {
			this.serverMessage = serverMessage;
			this.displayMessage = displayMessage;
		}

		static Reply fromMessage(String message) {
			for (Reply reply : values()) {
				if (reply.serverMessage.equals(message)) return reply;
			}
			return null;
		}
	}

	static final class Submission {
		final String currentPassword;
		final String newPassword;

		Submission(String currentPassword, String newPassword) {
			this.currentPassword = currentPassword;
			this.newPassword = newPassword;
		}
	}

	private final ModalTextField[] fields = {passwordField(), passwordField(), passwordField()};
	private final String username;
	private int focus = CURRENT;
	private String error;
	private boolean submit;
	private boolean waiting;
	private boolean changed;
	private boolean closed;

	PasswordChangeWindow(String username, boolean waiting) {
		this.username = username.trim();
		this.waiting = waiting;
	}

	private static ModalTextField passwordField() {
		return new ModalTextField(20, key -> key != KeyEvent.CHAR_UNDEFINED && !Character.isISOControl(key));
	}

	synchronized void keyPressed(char key, int code, boolean shortcut, boolean shift) {
		if (closed) return;
		if (code == KeyEvent.VK_ESCAPE || key == 27) {
			closed = true;
			clearFields();
			return;
		}
		boolean enter = code == KeyEvent.VK_ENTER || key == '\r' || key == '\n';
		if (waiting || changed) {
			if (enter && changed) closed = true;
			return;
		}
		if (code == KeyEvent.VK_TAB || key == '\t') {
			focus = (focus + (shift ? fields.length - 1 : 1)) % fields.length;
		} else if (enter) {
			if (focus < CONFIRM) focus++;
			else submit = true;
		} else {
			fields[focus].keyPressed(key, code, shortcut);
			if (!shortcut && key != KeyEvent.CHAR_UNDEFINED) error = null;
		}
	}

	synchronized void receive(Reply reply) {
		if (!waiting) return;
		waiting = false;
		changed = reply == Reply.CHANGED;
		if (!changed) error = reply.displayMessage;
	}

	synchronized void clearFields() {
		for (ModalTextField field : fields) field.clear();
	}

	synchronized Submission draw(mudclient client, int bottom) {
		if (closed) return CLOSE;

		MudClientGraphics surface = client.getSurface();
		GameInterfaceSettings.Size size = GameInterfaceSettings.size();
		GameInterfaceSettings.TextSize text = GameInterfaceSettings.textSize();
		int bodyLine = surface.fontHeight(text.bodyFont);
		int labelLine = surface.fontHeight(text.labelFont);
		int pad = size.pixels(12);
		int gap = size.pixels(6);
		int enoughForHint = surface.stringWidth(text.labelFont, REQUIREMENTS) + 2 * pad;
		int width = Math.min(client.getGameWidth() - 2 * pad, Math.max(size.pixels(460), enoughForHint));
		int header = Math.max(size.pixels(42), bodyLine + pad);
		int buttonHeight = Math.max(size.pixels(34), labelLine + gap);
		int fieldHeight = bodyLine + 2 * gap;
		int rowHeight = labelLine + gap + fieldHeight + pad;
		int height = header + 3 * pad + labelLine + buttonHeight;
		if (!waiting && !changed) height += fields.length * rowHeight;
		int x = (client.getGameWidth() - width) / 2;
		int y = Math.max(pad, (bottom - height) / 2);
		int left = x + pad;
		int right = x + width - pad;
		int buttonWidth = Math.max(size.pixels(92), surface.stringWidth(text.labelFont, "Change Password") + 2 * pad);
		Rectangle action = new Rectangle(right - buttonWidth, y + height - pad - buttonHeight, buttonWidth, buttonHeight);
		Rectangle cancel = new Rectangle(action.x - gap - buttonWidth, action.y, buttonWidth, buttonHeight);
		int mouseX = client.getMouseX();
		int mouseY = client.getMouseY();
		boolean clicked = client.getMouseClick() == 1;
		boolean overAction = action.contains(mouseX, mouseY);
		boolean overCancel = cancel.contains(mouseX, mouseY);

		SocialTextPrompt.drawFrame(surface, new Rectangle(x, y, width, height), header, pad, TITLE, text.bodyFont, bodyLine);
		if (waiting || changed) {
			if (clicked && overAction) return CLOSE;
			String status = waiting ? "Waiting for the server..." : Reply.CHANGED.displayMessage;
			surface.drawString(status, left, y + header + pad + labelLine, 0xFFFFFF, text.labelFont);
			SocialTextPrompt.drawButton(surface, action, "Close", true, overAction, text.labelFont, labelLine);
			return null;
		}
		if (clicked && overCancel) {
			clearFields();
			return CLOSE;
		}
		if (submit || clicked && overAction) {
			submit = false;
			Submission submission = validate();
			if (submission != null) {
				clearFields();
				focus = CURRENT;
				waiting = true;
				return submission;
			}
		}

		int top = y + header + pad;
		for (int i = 0; i < fields.length; i++) {
			int labelY = top + i * rowHeight + labelLine;
			Rectangle field = new Rectangle(left, labelY + gap, width - 2 * pad, fieldHeight);
			if (clicked && field.contains(mouseX, mouseY)) focus = i;
			surface.drawString(LABELS[i], left, labelY, 0xC4D0E0, text.labelFont);
			fields[i].drawMasked(client, field, text.bodyFont, gap, i == focus);
		}
		String hint = error == null ? REQUIREMENTS : error;
		hint = ContextMenuLayout.fitLabel(hint, width - 2 * pad, part -> surface.stringWidth(text.labelFont, part));
		surface.drawString(hint, left, top + fields.length * rowHeight + labelLine,
			error == null ? 0xC4D0E0 : 0xFFBC8D, text.labelFont);
		SocialTextPrompt.drawButton(surface, cancel, "Cancel", true, overCancel, text.labelFont, labelLine);
		SocialTextPrompt.drawButton(surface, action, "Change Password", true, overAction, text.labelFont, labelLine);
		return null;
	}

	private Submission validate() {
		String current = fields[CURRENT].value();
		String candidate = fields[NEW].value();
		if (current.isEmpty()) {
			focus = CURRENT;
			error = "Enter your current password.";
		} else if (candidate.length() < 5 || !DataOperations.addCharacters(candidate, candidate.length()).equals(candidate)) {
			focus = NEW;
			error = REQUIREMENTS;
		} else if (candidate.equalsIgnoreCase(username)) {
			focus = NEW;
			error = "Choose a password different from your username.";
		} else if (!fields[CONFIRM].value().equals(candidate)) {
			focus = CONFIRM;
			error = "New passwords do not match.";
		} else {
			error = null;
			return new Submission(current, candidate);
		}
		return null;
	}
}
