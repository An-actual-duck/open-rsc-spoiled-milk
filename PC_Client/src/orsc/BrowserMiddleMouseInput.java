package orsc;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/** Browser-only adapter into the existing BUTTON2 camera handler. Never installed by desktop. */
public final class BrowserMiddleMouseInput {
	private BrowserMiddleMouseInput() { }

	/** Packet: kind(cancel=0,press=1,drag=2,release=3), game x/y, shift/ctrl/alt/meta bits. */
	public static MouseEvent event(Component source, int[] packet) {
		return event(source, packet, 0, 0);
	}

	static MouseEvent event(Component source, int[] packet, int offsetX, int offsetY) {
		validate(packet);
		if (packet[0] == 0) return null;
		int modifiers = packet[0] == 3 ? 0 : InputEvent.BUTTON2_DOWN_MASK;
		if ((packet[3] & 1) != 0) modifiers |= InputEvent.SHIFT_DOWN_MASK;
		if ((packet[3] & 2) != 0) modifiers |= InputEvent.CTRL_DOWN_MASK;
		if ((packet[3] & 4) != 0) modifiers |= InputEvent.ALT_DOWN_MASK;
		if ((packet[3] & 8) != 0) modifiers |= InputEvent.META_DOWN_MASK;
		int id = packet[0] == 1 ? MouseEvent.MOUSE_PRESSED
			: packet[0] == 2 ? MouseEvent.MOUSE_DRAGGED : MouseEvent.MOUSE_RELEASED;
		return new MouseEvent(source, id, System.currentTimeMillis(), modifiers,
			Math.addExact(packet[1], offsetX), Math.addExact(packet[2], offsetY),
			0, false, packet[0] == 2 ? MouseEvent.NOBUTTON : MouseEvent.BUTTON2);
	}

	public static void dispatch(int[] packet) throws Exception {
		validate(packet);
		final int[] copy = packet.clone();
		Runnable action = new Runnable() {
			public void run() {
				OpenRSC applet = OpenRSC.applet;
				if (applet == null || applet.getMouseHandler() == null) return;
				MouseEvent event = event(applet, copy, applet.browserMouseOffsetX(), applet.browserMouseOffsetY());
				if (copy[0] == 0 || copy[0] == 3) applet.getMouseHandler().mouseReleased(event);
				else if (copy[0] == 1) applet.getMouseHandler().mousePressed(event);
				else applet.getMouseHandler().mouseDragged(event);
			}
		};
		if (EventQueue.isDispatchThread()) action.run();
		else EventQueue.invokeAndWait(action);
	}

	private static void validate(int[] packet) {
		if (packet == null || packet.length != 4 || packet[0] < 0 || packet[0] > 3
			|| packet[1] < -65536 || packet[1] > 65536 || packet[2] < -65536 || packet[2] > 65536
			|| packet[3] < 0 || packet[3] > 15) throw new IllegalArgumentException("Invalid browser middle-mouse event");
	}
}
