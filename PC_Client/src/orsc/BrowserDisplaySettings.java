package orsc;

import javax.swing.JFrame;

/** Browser presentation only. Does not enable native OpenGL or change render resolution. */
final class BrowserDisplaySettings {
	private BrowserDisplaySettings() { }
	static boolean isBorderless() { return Boolean.getBoolean("spoiledmilk.browserBorderlessDisplay"); }
	static void configure(JFrame frame) {
		if (isBorderless()) { frame.setUndecorated(true); frame.setResizable(false); }
	}
}
