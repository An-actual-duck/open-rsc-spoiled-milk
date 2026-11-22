package orsc;

import java.util.ArrayDeque;
import orsc.graphics.three.Renderer3DSettings;

/** Retains browser streaming diagnostics without scheduling a writer in the Java VM. */
public final class BrowserStreamingLog {
	private static final boolean ENABLED = Boolean.getBoolean("spoiledmilk.browserStreamingLog")
		&& !Boolean.getBoolean("spoiledmilk.browserPublicAlpha");
	private static final int CAPACITY = 128;
	private static final ArrayDeque<String> pending = ENABLED ? new ArrayDeque<>(CAPACITY) : null;
	private static long dropped;

	private BrowserStreamingLog() {}

	public static boolean append(String message) {
		if (!ENABLED || !Renderer3DSettings.isBrowserPresentationAvailable()) return false;
		synchronized (pending) {
			if (pending.size() == CAPACITY) {
				pending.removeFirst();
				dropped++;
			}
			pending.addLast(message);
		}
		return true;
	}

	public static String drain() {
		if (!ENABLED) return null;
		synchronized (pending) {
			if (pending.isEmpty()) return null;
			String lines = dropped == 0 ? String.join("\n", pending)
				: "Browser streaming log dropped " + dropped + " earlier entries\n" + String.join("\n", pending);
			pending.clear();
			dropped = 0;
			return lines;
		}
	}
}
