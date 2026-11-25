package orsc;

import java.util.concurrent.atomic.AtomicBoolean;
import orsc.graphics.gui.Panel;
import orsc.graphics.two.MudClientGraphics;

/** Classic pre-login forms for the account entry preview. */
final class AccountEntryPreview {
	private static final String RECOVERY_RECEIPT = "If this account is linked to Discord, check your DMs for a recovery request.";

	enum RegistrationAction { NONE, CANCEL, CREATE }

	private final Panel registration;
	private final Panel confirmation;
	private final Panel recovery;
	private final int username;
	private final int password;
	private final int repeatPassword;
	private final int discord;
	private final int registrationStatus;
	private final int registrationDetail;
	private final int create;
	private final int cancelRegistration;
	private final int continueWithoutDiscord;
	private final int returnToRegistration;
	private final int recoveryUsername;
	private final int recoveryStatus;
	private final int sendRecovery;
	private final int backToLogin;
	private final AtomicBoolean escape = new AtomicBoolean();
	private boolean confirming;

	AccountEntryPreview(MudClientGraphics surface, int x, int y) {
		registration = new Panel(surface, 32);
		registration.addCenteredText(x, y - 139, "Create Account", 5, true);
		registration.addCenteredText(x, y - 117, "Choose a username of 2 to 12 letters, numbers or spaces.", 1, true);
		username = field(registration, x, y - 89, 420, 12, "Username", false);
		registration.addCenteredText(x, y - 58, "Use 4 to 20 letters or numbers for your password.", 1, true);
		password = field(registration, x - 107, y - 29, 206, 20, "Password", true);
		repeatPassword = field(registration, x + 107, y - 29, 206, 20, "Confirm password", true);
		discord = field(registration, x, y + 19, 420, 32, "Discord Username (optional)", false);
		registration.addCenteredText(x, y + 50, "This is not required but is necessary if you want to have", 1, true);
		registration.addCenteredText(x, y + 65, "access to account/password recovery.", 1, true);
		registrationStatus = registration.addCenteredText(x, y + 89, "", 4, true);
		registrationDetail = registration.addCenteredText(x, y + 106, "", 1, true);
		create = button(registration, x - 72, y + 135, 134, "Create Account");
		cancelRegistration = button(registration, x + 72, y + 135, 134, "Cancel");

		confirmation = new Panel(surface, 12);
		confirmation.addCenteredText(x, y - 28, "Are you sure you don't want to link a Discord account?", 4, true);
		continueWithoutDiscord = button(confirmation, x - 61, y + 15, 110, "Yes");
		returnToRegistration = button(confirmation, x + 61, y + 15, 110, "No");

		recovery = new Panel(surface, 16);
		recovery.addCenteredText(x, y - 81, "I forgot my password", 5, true);
		recoveryUsername = field(recovery, x, y - 36, 310, 12, "Username", false);
		sendRecovery = button(recovery, x - 61, y + 7, 110, "Send");
		backToLogin = button(recovery, x + 61, y + 7, 110, "Back");
		recoveryStatus = recovery.addCenteredText(x, y + 47, "", 1, true);
		recovery.addCenteredText(x, y + 66, "@yel@Preview only. Discord delivery is pending.", 1, true);
	}

	private static int field(Panel panel, int x, int y, int width, int maxLength, String label, boolean masked) {
		panel.addButtonBackground(x, y, width, 40);
		panel.addCenteredText(x, y - 9, label, 4, false);
		return panel.addCenteredTextEntry(x, y + 9, width - 16, maxLength, 20, 4, masked, false);
	}

	private static int button(Panel panel, int x, int y, int width, String label) {
		panel.addButtonBackground(x, y, width, 32);
		panel.addCenteredText(x, y, label, 4, false);
		return panel.addButton(x, y, width, 32);
	}

	void startRegistration() {
		confirming = false;
		escape.set(false);
		registration.setText(username, "");
		registration.setText(password, "");
		registration.setText(repeatPassword, "");
		registration.setText(discord, "");
		registration.setFocus(username);
		showRegistrationStatus("", "");
	}

	void startRecovery(String loginUsername) {
		escape.set(false);
		recovery.setText(recoveryUsername, loginUsername);
		recovery.setText(recoveryStatus, "");
		recovery.setFocus(recoveryUsername);
	}

	void keyPressed(int screen, int key, int keyCode) {
		if (key == 27 || keyCode == 27) {
			escape.set(true);
		} else if (screen == 1 && !confirming) {
			registration.keyPress(key);
		} else if (screen == 4) {
			recovery.keyPress(key);
		}
	}

	void drawRegistration() {
		(confirming ? confirmation : registration).drawPanel();
	}

	void drawRecovery() {
		recovery.drawPanel();
	}

	RegistrationAction updateRegistration(int x, int y, int down, int click, boolean enter, boolean acceptsEmptyEmail) {
		boolean cancelled = escape.getAndSet(false);
		if (confirming) {
			confirmation.handleMouse(x, y, down, click);
			if (confirmation.isClicked(continueWithoutDiscord)) {
				confirming = false;
				return RegistrationAction.CREATE;
			}
			if (confirmation.isClicked(returnToRegistration) || cancelled) {
				confirming = false;
				registration.setFocus(discord);
			}
			return RegistrationAction.NONE;
		}
		registration.handleMouse(x, y, down, click);
		if (registration.isClicked(cancelRegistration) || cancelled) return RegistrationAction.CANCEL;
		if (registration.isClicked(username)) {
			registration.setFocus(password);
			return RegistrationAction.NONE;
		}
		if (registration.isClicked(password)) {
			registration.setFocus(repeatPassword);
			return RegistrationAction.NONE;
		}
		if (registration.isClicked(repeatPassword)) {
			registration.setFocus(discord);
			return RegistrationAction.NONE;
		}
		if (registration.isClicked(create) || registration.isClicked(discord) || enter) {
			if (!username().matches("[A-Za-z0-9 ]{2,12}")) {
				showRegistrationStatus("Choose a username of 2 to 12 characters.", "");
				registration.setFocus(username);
			} else if (!password().matches("[A-Za-z0-9]{4,20}")) {
				showRegistrationStatus("Use 4 to 20 letters or numbers for your password.", "");
				registration.setFocus(password);
			} else if (!password().equals(repeatPassword())) {
				showRegistrationStatus("The passwords do not match.", "");
				registration.setFocus(repeatPassword);
			} else if (!acceptsEmptyEmail) {
				showRegistrationStatus("Account creation here is pending.", "This server still requires legacy registration.");
			} else if (!registration.getControlText(discord).trim().isEmpty()) {
				showRegistrationStatus("Discord linking is pending in this preview.", "Leave Discord Username blank to create an account now.");
				registration.setFocus(discord);
			} else {
				showRegistrationStatus("", "");
				confirming = true;
			}
		}
		return RegistrationAction.NONE;
	}

	boolean updateRecovery(int x, int y, int down, int click, boolean enter) {
		recovery.handleMouse(x, y, down, click);
		if (recovery.isClicked(backToLogin) || escape.getAndSet(false)) return true;
		if (recovery.isClicked(sendRecovery) || recovery.isClicked(recoveryUsername) || enter) {
			String user = recovery.getControlText(recoveryUsername).trim();
			recovery.setText(recoveryStatus, user.isEmpty() ? "Enter your game username." : RECOVERY_RECEIPT);
		}
		return false;
	}

	String username() {
		return registration.getControlText(username).trim();
	}

	String password() {
		return registration.getControlText(password);
	}

	String repeatPassword() {
		return registration.getControlText(repeatPassword);
	}

	void showRegistrationStatus(String status, String detail) {
		registration.setText(registrationStatus, status);
		registration.setText(registrationDetail, detail);
	}
}
