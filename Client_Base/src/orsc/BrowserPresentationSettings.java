package orsc;

/** Browser-only display requests. The outer page owns fullscreen and its user gesture. */
public final class BrowserPresentationSettings {
    public static final int FIT_TO_TAB = 1;
    public static final int FULLSCREEN = 2;
    private static volatile boolean available;
    private static volatile boolean fitToTab = true;
    private static volatile boolean fullscreen;
    private static int pending;

    private BrowserPresentationSettings() { }

    public static boolean isAvailable() { return available; }
    public static boolean isFitToTab() { return fitToTab; }
    public static boolean isFullscreen() { return fullscreen; }

    /** Called only by the browser bootstrap; never inferred from desktop renderer settings. */
    public static synchronized void update(int[] state) {
        if (state == null || state.length != 3) throw new IllegalArgumentException("Browser display state");
        for (int value : state) if (value != 0 && value != 1) throw new IllegalArgumentException("Browser display flag");
        available = state[0] == 1;
        fitToTab = state[1] == 1;
        fullscreen = state[2] == 1;
        if (!available) pending = 0;
    }

    public static synchronized void request(int action) {
        if (available && action >= FIT_TO_TAB && action <= FULLSCREEN && pending == 0) pending = action;
    }

    public static synchronized int takeRequest() {
        int result = pending;
        pending = 0;
        return result;
    }
}
