package orsc;

/** Finite terrain comparison choices; deliberately not a permanent player menu yet. */
final class RendererTerrainDefinitionSettings {
    enum Mode {
        SUN_ONLY(0), CLASSIC_RELIEF(1), RELIEF_AO(2), RELIEF_CURVATURE(3);
        final int shaderId;
        Mode(int id) { shaderId = id; }
    }
    private static volatile Mode mode = parse(System.getProperty("spoiledmilk.terrainDefinition", "relief-ao"));
    private RendererTerrainDefinitionSettings() { }
    static Mode getMode() { return mode; }
    static void setMode(Mode next) { mode = next == null ? Mode.RELIEF_AO : next; }
    private static Mode parse(String value) {
        if (value == null) return Mode.RELIEF_AO;
        switch (value.trim().toLowerCase(java.util.Locale.ROOT).replace('_', '-')) {
            case "sun-only": return Mode.SUN_ONLY;
            case "classic-relief": return Mode.CLASSIC_RELIEF;
            case "relief-ao": return Mode.RELIEF_AO;
            case "relief-curvature": return Mode.RELIEF_CURVATURE;
            default: throw new IllegalArgumentException("Unknown terrain definition comparison: " + value);
        }
    }
}
