package orsc;

/** Fixed effective modes for the explicitly selected public browser alpha.
 * Stored preferences remain owned by the ordinary settings loaders/writers. */
final class BrowserPublicGraphicsPreset {
    private static final boolean ENABLED =
        "true".equals(System.getProperty("spoiledmilk.browserPublicAlpha"));
    private BrowserPublicGraphicsPreset() { }
    static boolean isEnabled() { return ENABLED; }
}
