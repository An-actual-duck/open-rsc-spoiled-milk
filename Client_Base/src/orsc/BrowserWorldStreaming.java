package orsc;

/** Browser startup selection, fixed for the lifetime of one client runtime. */
public final class BrowserWorldStreaming {
	private static final boolean ENABLED = Boolean.getBoolean("spoiledmilk.browserWorldStreaming");

	private BrowserWorldStreaming() {}

	public static boolean enabled() {
		return ENABLED;
	}

	public static boolean allowsSurfaceFeatures() {
		return ENABLED || !Boolean.getBoolean("spoiledmilk.browserPublicAlpha");
	}
}
