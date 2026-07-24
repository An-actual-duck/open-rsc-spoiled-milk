package orsc;

import java.awt.event.MouseEvent;

/**
 * Tracks desktop middle-button orbit gestures independently from gameplay
 * mouse-button state.
 */
final class DesktopMiddleMouseOrbit {
	private boolean active;
	private int previousX;
	private int previousY;
	private int deltaX;
	private int deltaY;

	boolean begin(int button, int x, int y) {
		if (button != MouseEvent.BUTTON2) {
			return false;
		}
		active = true;
		previousX = x;
		previousY = y;
		deltaX = 0;
		deltaY = 0;
		return true;
	}

	boolean update(int x, int y) {
		if (!active) {
			deltaX = 0;
			deltaY = 0;
			return false;
		}
		deltaX = x - previousX;
		deltaY = y - previousY;
		previousX = x;
		previousY = y;
		return true;
	}

	boolean end(int button) {
		if (button != MouseEvent.BUTTON2) {
			return false;
		}
		cancel();
		return true;
	}

	void cancel() {
		active = false;
		deltaX = 0;
		deltaY = 0;
	}

	boolean isActive() {
		return active;
	}

	int getDeltaX() {
		return deltaX;
	}

	int getDeltaY() {
		return deltaY;
	}
}
