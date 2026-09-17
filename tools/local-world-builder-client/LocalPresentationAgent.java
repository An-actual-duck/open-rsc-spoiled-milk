package localworldbuilder;

import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Properties;

/** One-off startup settings adapter. No transformers, rewritten classes or file writes. */
public final class LocalPresentationAgent {
    public static final String CLIENT_SHA256 =
        "fccec5bb042594562e9496b8a3ac09e6c7029e5dbd73f0a2b8f26cfb0fb23479";
    private static final String PREFIX = "spoiledmilk.";
    private static final String[] FORCED_OFF = {
        "openglPresenter", "openglInput", "openglPrimaryWindow", "directFramebuffer",
        "skipLegacyWorldRaster", "renderer3DGeometryCapture", "renderer3DVisibleWorld",
        "openglWorldMesh", "openglWorldMeshVisible", "openglWorldMeshTexturedVisible",
        "openglWorldMeshTexturedStaticVisible", "openglWorldReplacementComposite",
        "openglWorldChunksTexturedVisible", "openglWorldChunksReplacementComposite",
        "openglWorldChunksTrustedReplacement", "openglWorldChunksResidentObjects",
        "openglWorldSpritesVisible", "openglWorldUiReplay"
    };
    private static final String[] FORCED_LEGACY_UI = {
        "opengl_sprite_overlay", "opengl_ui_base_frame", "opengl_native_ui_replace"
    };

    private LocalPresentationAgent() { }

    public static void premain(String options, Instrumentation unused) throws Exception {
        Properties properties = System.getProperties();
        if (!isAdaptiveClient(properties)) return; // Launcher and server are untouched.
        String classPath = properties.getProperty("java.class.path", "");
        Path client = Paths.get(classPath).toAbsolutePath().normalize();
        if (!"Open_RSC_Client.jar".equals(client.getFileName().toString())
            || !Files.isRegularFile(client) || Files.isSymbolicLink(client)) {
            throw new IllegalStateException("Local presentation requires the exact packaged client JAR");
        }
        if (!CLIENT_SHA256.equals(sha256(client))) {
            throw new IllegalStateException("Client changed: local presentation must be re-reviewed for this version");
        }
        restorePresentation(properties);
        System.out.println("[local-world-builder] Spoiled Milk renderer/UI enabled; packaged protocol unchanged.");
    }

    public static boolean isAdaptiveClient(Properties properties) {
        if (!"true".equals(properties.getProperty("openrsc.worldBuilderMode"))
            || !"true".equals(properties.getProperty("openrsc.worldBuilderAdaptiveMode"))) return false;
        if (!"127.0.0.1".equals(properties.getProperty("openrsc.worldBuilderHost"))) {
            throw new IllegalStateException("Local presentation is restricted to the loopback editor client");
        }
        return true;
    }

    public static void restorePresentation(Properties properties) {
        properties.setProperty("openrsc.worldBuilderPreservationUi", "false");
        // Let the unchanged RendererRuntimeDefaults apply the same defaults as Core.
        for (String key : FORCED_OFF) properties.remove(PREFIX + key);
        for (String key : FORCED_LEGACY_UI) properties.remove("spoiled_milk." + key);
        // Existing presentation preferences remain user-owned. With no saved
        // override, the packaged/Core default is Remaster. F6 restores the normal
        // renderer controls so an existing Classic choice can be changed there.
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] block = new byte[65536];
            int count;
            while ((count = input.read(block)) != -1) digest.update(block, 0, count);
        }
        StringBuilder text = new StringBuilder();
        for (byte value : digest.digest()) text.append(String.format("%02x", value & 255));
        return text.toString();
    }
}
