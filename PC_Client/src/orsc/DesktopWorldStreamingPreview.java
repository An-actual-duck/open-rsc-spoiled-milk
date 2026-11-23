package orsc;

/** Development opt-in for uploading checked incoming terrain on the native GL thread. */
final class DesktopWorldStreamingPreview {
	static final boolean ENABLED = Boolean.getBoolean("spoiledmilk.desktopWorldStreamingPreview");
	static final boolean TRACE = ENABLED && Boolean.getBoolean("spoiledmilk.desktopWorldStreamingTrace");

	private DesktopWorldStreamingPreview() {
	}
}
