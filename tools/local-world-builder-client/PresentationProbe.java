package orsc;

import java.awt.event.KeyEvent;
import java.util.Properties;
import localworldbuilder.LocalPresentationAgent;

/** Headless checks against the real unmodified packaged client; never runs its main. */
public final class PresentationProbe {
    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Properties empty = new Properties();
        require(!LocalPresentationAgent.isAdaptiveClient(empty), "ordinary JVM must be untouched");
        empty.setProperty("openrsc.worldBuilderAdaptiveMode", "true");
        require(!LocalPresentationAgent.isAdaptiveClient(empty), "server must be untouched");
        empty.setProperty("openrsc.worldBuilderMode", "true");
        empty.setProperty("openrsc.worldBuilderHost", "example.invalid");
        boolean rejected = false;
        try { LocalPresentationAgent.isAdaptiveClient(empty); }
        catch (IllegalStateException expected) { rejected = true; }
        require(rejected, "non-loopback must be rejected");

        System.setProperty("openrsc.worldBuilderMode", "true");
        System.setProperty("openrsc.worldBuilderAdaptiveMode", "true");
        System.setProperty("openrsc.worldBuilderHost", "127.0.0.1");
        System.setProperty("openrsc.worldBuilderPort", "43615");
        System.setProperty("openrsc.worldBuilderPreservationUi", "true");
        System.setProperty("spoiledmilk.openglPresenter", "false");
        System.setProperty("spoiledmilk.openglInput", "false");
        System.setProperty("spoiledmilk.openglPrimaryWindow", "false");
        System.setProperty("spoiledmilk.skipLegacyWorldRaster", "false");
        System.setProperty("spoiled_milk.opengl_native_ui_replace", "false");
        require(WorldBuilderUiProfile.isEnabled(), "original profile remains available");
        require(RenderSurfaceSettings.getWidth() == 640, "original fixed surface");
        require(ClientHotkeySettings.shouldSuppressFunctionKey(KeyEvent.VK_F6), "original renderer hotkey restriction");

        // The real launcher uses -jar, hence a single-file java.class.path.
        System.setProperty("java.class.path", args[0]);
        LocalPresentationAgent.premain(null, null);
        require("43615".equals(System.getProperty("openrsc.worldBuilderPort")), "port changed");
        require("true".equals(System.getProperty("openrsc.worldBuilderAdaptiveMode")), "binding mode changed");
        require(!WorldBuilderUiProfile.isEnabled(), "preservation policy was not released");
        require(RenderSurfaceSettings.getWidth() == 960 && RenderSurfaceSettings.getHeight() == 540,
                "widescreen surface was not restored");
        require(!ClientHotkeySettings.shouldSuppressFunctionKey(KeyEvent.VK_F6), "renderer hotkey remains disabled");
        RendererRuntimeDefaults.apply();
        for (String key : new String[] {"openglPresenter", "openglInput", "openglPrimaryWindow",
                "skipLegacyWorldRaster", "openglWorldMesh", "openglWorldChunksResidentObjects",
                "openglWorldSpritesVisible"}) {
            require("true".equals(System.getProperty("spoiledmilk." + key)), "renderer default missing: " + key);
        }
        require(System.getProperty("spoiled_milk.opengl_native_ui_replace") == null, "native UI still forced off");
        SpellbookLayoutSettings.setMode(SpellbookLayoutSettings.Mode.ICONS);
        require(!SpellbookLayoutSettings.usesTextLayout(), "icon UI remains suppressed");
        orsc.remastered.RemasteredSpriteSettings.setEnabled(true);
        require(orsc.remastered.RemasteredSpriteSettings.isEnabled(), "enhanced sprite toggle remains suppressed");
        require(Config.CLIENT_VERSION == 10048, "wire version changed");
        require("layered-world-placements-v5".equals(AdaptiveWorldBuilderClientSession.BLOCKED_VOID_PLACEMENT_ENCODING),
                "blocked-void admission changed");
        System.out.println("PASS packaged presentation controls, loopback scope and protocol constants");
    }
}
