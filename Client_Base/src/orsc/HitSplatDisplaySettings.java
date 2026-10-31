package orsc;

import java.util.Properties;

/** Local presentation preference, shared by all character and renderer paths. */
final class HitSplatDisplaySettings {
    static final String PROPERTY_KEY = "hit_splat_style";
    static final int OPTION_ACTION = 66;
    private static volatile FloatingHitSplatRenderer.Style style = FloatingHitSplatRenderer.Style.BACKGROUND;

    private HitSplatDisplaySettings() { }

    static FloatingHitSplatRenderer.Style getStyle() { return style; }

    static void setStyle(FloatingHitSplatRenderer.Style next) {
        style = next == null ? FloatingHitSplatRenderer.Style.BACKGROUND : next;
    }

    static void cycleStyle() {
        style = style == FloatingHitSplatRenderer.Style.BACKGROUND
            ? FloatingHitSplatRenderer.Style.TEXT : FloatingHitSplatRenderer.Style.BACKGROUND;
    }

    static String optionLabel() {
        return "@whi@Hit number style - " + (style == FloatingHitSplatRenderer.Style.BACKGROUND
            ? "@gre@Background" : "@cya@Text only");
    }

    static void loadFromClientSettings(Properties properties) {
        if (properties == null) return;
        String value = properties.getProperty(PROPERTY_KEY, "background").trim();
        style = "text".equalsIgnoreCase(value) ? FloatingHitSplatRenderer.Style.TEXT
            : FloatingHitSplatRenderer.Style.BACKGROUND;
    }

    static void saveToClientSettings(Properties properties) {
        if (properties != null) properties.setProperty(PROPERTY_KEY,
            style == FloatingHitSplatRenderer.Style.TEXT ? "text" : "background");
    }
}
