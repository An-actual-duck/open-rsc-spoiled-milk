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

	AccountEntryPreview(MudClientGraphics surface, int x, int y, int scale) {
		AccountEntryLayout form = new AccountEntryLayout(surface, 32, x, y, scale);
		registration = form.panel;
		form.label(0, -139, "Create Account", 5, true);
		form.label(0, -117, "Choose a username of 2 to 12 letters, numbers or spaces.", 1, true);
		username = form.field(0, -89, 420, 12, "Username", false);
		form.label(0, -58, "Use 4 to 20 letters or numbers for your password.", 1, true);
		password = form.field(-107, -29, 206, 20, "Password", true);
		repeatPassword = form.field(107, -29, 206, 20, "Confirm password", true);
		discord = form.field(0, 19, 420, 32, "Discord Username (optional)", false);
		form.label(0, 50, "This is not required but is necessary if you want to have", 1, true);
		form.label(0, 65, "access to account/password recovery.", 1, true);
		registrationStatus = form.label(0, 89, "", 4, true);
		registrationDetail = form.label(0, 106, "", 1, true);
		create = form.button(-72, 135, 134, 32, "Create Account", 4);
		cancelRegistration = form.button(72, 135, 134, 32, "Cancel", 4);

		AccountEntryLayout confirm = new AccountEntryLayout(surface, 12, x, y, scale);
		confirmation = confirm.panel;
		confirm.label(0, -28, "Are you sure you don't want to link a Discord account?", 4, true);
		continueWithoutDiscord = confirm.button(-61, 15, 110, 32, "Yes", 4);
		returnToRegistration = confirm.button(61, 15, 110, 32, "No", 4);

		AccountEntryLayout reset = new AccountEntryLayout(surface, 16, x, y, scale);
		recovery = reset.panel;
		reset.label(0, -81, "I forgot my password", 5, true);
		recoveryUsername = reset.field(0, -36, 310, 12, "Username", false);
		sendRecovery = reset.button(-61, 7, 110, 32, "Send", 4);
		backToLogin = reset.button(61, 7, 110, 32, "Back", 4);
		recoveryStatus = reset.label(0, 47, "", 1, true);
		reset.label(0, 66, "@yel@Preview only. Discord delivery is pending.", 1, true);
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
