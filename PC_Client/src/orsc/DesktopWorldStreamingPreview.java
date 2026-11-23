package orsc;

/** Development opt-in for uploading checked incoming terrain on the native GL thread. */
final class DesktopWorldStreamingPreview {
	private static final boolean STREAMING = Boolean.getBoolean("spoiledmilk.desktopWorldStreaming");
	static final boolean ENABLED = STREAMING || Boolean.getBoolean("spoiledmilk.desktopWorldStreamingPreview");
	static final boolean TRACE = ENABLED && Boolean.getBoolean("spoiledmilk.desktopWorldStreamingTrace");

	private DesktopWorldStreamingPreview() {
	}

	static void configure() {
		if (!STREAMING) return;
		for (String option : new String[] {
			"persistentMovementPreview", "persistentSurfaceCollision", "surfaceSceneryCells",
			"rollingSurfaceWindow", "fixedSurfaceDraw", "expandedSurfaceDrawBorder",
			"surfaceSceneryDraw", "liveSurfaceHandoff"
		}) {
			System.setProperty("spoiledmilk." + option, "true");
		}
	}
}
