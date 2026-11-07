package orsc;

import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Test-only client driver; all login/scene packets retain their ordinary paths. */
public final class RendererBenchmarkClient {
    private static java.io.BufferedWriter gpuWriter;
    private static Method gpuSinkSetter;
    private static final Object GPU_LOCK = new Object();

    private static void installGpuSink(Path capture) throws Exception {
        try {
            Class<?> timer = Class.forName("orsc.OpenGLGpuTimer");
            Class<?> sink = Class.forName("orsc.OpenGLGpuTimer$SampleSink");
            gpuSinkSetter = timer.getDeclaredMethod("setDiagnosticSink", sink); gpuSinkSetter.setAccessible(true);
            gpuWriter = Files.newBufferedWriter(capture.resolve("gpu-samples.csv"), StandardCharsets.UTF_8);
            gpuWriter.write("pass,sequence,sessionElapsedNanos,gpuNanos\n");
            Object proxy = Proxy.newProxyInstance(sink.getClassLoader(), new Class<?>[]{sink}, (object, method, arguments) -> {
                if (method.getName().equals("sample")) {
                    long anchor = ((Number) field(null, RendererDiagnosticSession.class, "startedNanos")).longValue();
                    synchronized (GPU_LOCK) {
                        if (gpuWriter != null) gpuWriter.write(arguments[0] + "," + arguments[1] + "," + (System.nanoTime() - anchor) + "," + arguments[2] + "\n");
                    }
                }
                return null;
            });
            gpuSinkSetter.invoke(null, proxy);
        } catch (ClassNotFoundException unavailable) {
            Files.write(capture.resolve("GPU-INSTRUMENTATION-UNAVAILABLE"), "Original baseline has no GPU query instrumentation\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void closeGpuSink() throws Exception {
        if (gpuSinkSetter != null) gpuSinkSetter.invoke(null, new Object[]{null});
        synchronized (GPU_LOCK) {
            if (gpuWriter != null) { gpuWriter.close(); gpuWriter = null; }
        }
    }

    private static String shadowStats(OpenGLFramePresenter presenter) throws Exception {
        try {
            Object renderer = field(presenter, OpenGLFramePresenter.class, "worldChunkRenderer");
            Method diagnostics = renderer.getClass().getDeclaredMethod("geometryShadowDiagnostics"); diagnostics.setAccessible(true);
            Object pass = diagnostics.invoke(renderer);
            if (pass == null) return "{\"active\":false}";
            try {
                Method snapshotMethod=pass.getClass().getDeclaredMethod("diagnosticSnapshot"); snapshotMethod.setAccessible(true);
                long[] snapshot=(long[]) snapshotMethod.invoke(pass);
                StringBuilder json=new StringBuilder("{\"sequence\":"+snapshot[0]+",\"active\":"+(snapshot[1]!=0)+",\"farRebuilt\":"+(snapshot[2]!=0));
                String[] keys={"nearCpuNanos","farCpuNanos","nearDrawCalls","nearTriangles","farDrawCalls","farTriangles"};
                for(int i=0;i<keys.length;i++) json.append(",\"").append(keys[i]).append("\":").append(snapshot[i+3]);
                return json.append(",\"coherent\":true}").toString();
            } catch (NoSuchMethodException olderCandidate) { /* Retain explicit non-atomic legacy diagnostics. */ }
            Method active = pass.getClass().getDeclaredMethod("isActive"); active.setAccessible(true);
            StringBuilder json = new StringBuilder("{\"coherent\":false,\"active\":" + active.invoke(pass));
            for (String name : new String[]{"nearCpuNanos", "farCpuNanos", "nearDrawCalls", "nearTriangles", "farDrawCalls", "farTriangles", "farRebuilt"}) {
                json.append(",\"").append(name).append("\":").append(field(pass, pass.getClass(), name));
            }
            return json.append('}').toString();
        } catch (NoSuchMethodException baseline) { return "{\"instrumentation\":false}"; }
    }
    private static Object field(Object owner, Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    private static void set(Object owner, Class<?> type, String name, Object value) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
    }
    private static String windowState(OpenGLFramePresenter presenter) throws Exception {
        Object window = field(presenter, OpenGLFramePresenter.class, "windowController");
        try {
            Method snapshot = window.getClass().getDeclaredMethod("diagnosticWindowFlags"); snapshot.setAccessible(true);
            return String.valueOf(snapshot.invoke(window));
        } catch (NoSuchMethodException unavailable) { return "null"; }
    }
    private static String shadowQuality() throws Exception {
        try {
            Class<?> settings=Class.forName("orsc.RendererShadowSettings");
            Method quality=settings.getDeclaredMethod("getQuality"); quality.setAccessible(true);
            return String.valueOf(quality.invoke(null));
        } catch (ClassNotFoundException baseline) { return "unavailable"; }
    }
    private static int activeProjectiles(mudclient client) {
        int count = 0;
        for (int i=0; i<client.getPlayerCount(); i++) {
            ORSCharacter actor=client.getPlayer(i);
            if (actor != null && (actor.projectileRange>0 || actor.projectileImpactTime>0)) count++;
        }
        for (int i=0; i<client.getNpcCount(); i++) {
            ORSCharacter actor=client.getNpc(i);
            if (actor != null && (actor.projectileRange>0 || actor.projectileImpactTime>0)) count++;
        }
        return count;
    }
    public static void main(String[] args) {
        try { runFixture(); }
        catch (Throwable failure) {
            failure.printStackTrace();
            try { closeGpuSink(); } catch (Exception ignored) { }
            RendererDiagnosticSession.close();
            System.exit(1); // Never leave a failed private game thread/window running.
        }
    }
    private static void runFixture() throws Exception {
        final Path clientRoot = Paths.get(".").toRealPath();
        final Path run = clientRoot.getParent();
        if (!Files.isRegularFile(run.resolve("RENDERER-BENCHMARK.json"))
                || (!Boolean.getBoolean("spoiledmilk.openglHiddenDiagnostic")
                    && !Boolean.getBoolean("renderer.benchmark.visiblePreview"))) {
            throw new IllegalStateException("Marked hidden fixture or explicit visible preview required");
        }
        if (!"127.0.0.1".equals(new String(Files.readAllBytes(clientRoot.resolve("Cache/ip.txt")), StandardCharsets.UTF_8).trim())
                || !"43639".equals(new String(Files.readAllBytes(clientRoot.resolve("Cache/port.txt")), StandardCharsets.UTF_8).trim())) {
            throw new IllegalStateException("Private renderer endpoint required");
        }
        Path capture = Paths.get(System.getProperty("spoiledmilk.rendererDiagnosticSessionDir")).toRealPath();
        installGpuSink(capture);
        OpenRSC.main(new String[0]);
        mudclient client = null;
        long deadline = System.currentTimeMillis() + 90000;
        while (System.currentTimeMillis() < deadline) {
            client = (mudclient) field(null, ORSCApplet.class, "mudclient");
            if (client != null && field(client, mudclient.class, "currentViewMode").toString().equals("LOGIN")) {
                // Login panels/data must finish construction before attempting normal authentication.
                if (field(client, mudclient.class, "panelLoginWelcome") != null) break;
            }
            Thread.sleep(500);
        }
        if (client == null) throw new IllegalStateException("Client did not initialize");
        Thread.sleep(4000);
        set(client, mudclient.class, "autoLoginTimeout", 1);
        Method login = mudclient.class.getDeclaredMethod("login", int.class, String.class, String.class, boolean.class);
        login.setAccessible(true); login.invoke(client, -12, "RendererProof13", "renderdemo", false);
        deadline = System.currentTimeMillis() + 90000;
        while (System.currentTimeMillis() < deadline
                && !field(client, mudclient.class, "currentViewMode").toString().equals("GAME")) Thread.sleep(500);
        if (!field(client, mudclient.class, "currentViewMode").toString().equals("GAME")) throw new IllegalStateException("Ordinary login failed");
        set(client, mudclient.class, "welcomeScreenShown", false);
        Thread.sleep(15000);
        client.setShowDialogMessage(false);
        Config.C_HIDE_LOGIN_BOX = true;
        client.setCameraZoomSetting(Integer.getInteger("renderer.benchmark.zoom", 75));
        int rotation = Integer.getInteger("renderer.benchmark.rotation", 128);
        client.cameraRotation = rotation;
        Thread.sleep(2000);
        int seconds = Integer.getInteger("renderer.benchmark.seconds", 60);
        String phase = System.getProperty("renderer.benchmark.phase", "dense-idle");
        OpenGLFramePresenter presenter = (OpenGLFramePresenter) field(ScaledWindow.getInstance(), ScaledWindow.class, "openGLFramePresenter");
        OpenGLViewportPresenter viewport = (OpenGLViewportPresenter) field(presenter, OpenGLFramePresenter.class, "viewportPresenter");
        System.out.println(RendererPerformanceDiagnostics.handleCommand("::pf start " + phase));
        for (int second = 0; second < seconds; second++) {
            client.setCameraZoomSetting(Integer.getInteger("renderer.benchmark.zoom", 75));
            if (phase.contains("camera")) client.cameraRotation = (rotation + second * 3) & 255;
            RendererDayNightCycle.Presentation light = RendererDayNightCycle.currentPresentation();
            String sample = "{\"seconds\":" + second + ",\"mode\":\"" + field(client, mudclient.class, "currentViewMode")
                + "\",\"players\":" + client.getPlayerCount() + ",\"npcs\":" + client.getNpcCount()
                + ",\"gameObjects\":" + client.getGameObjectInstanceCount() + ",\"wallObjects\":" + client.getWallObjectInstanceCount()
                + ",\"serverState\":" + new String(Files.readAllBytes(run.resolve("status.json")), StandardCharsets.UTF_8)
                + ",\"activeProjectileActors\":" + activeProjectiles(client) + ",\"windowState\":" + windowState(presenter)
                + ",\"cameraRotation\":" + client.cameraRotation + ",\"zoomSetting\":" + osConfig.C_LAST_ZOOM
                + ",\"logicalWidth\":" + RenderSurfaceSettings.getWidth() + ",\"logicalHeight\":" + RenderSurfaceSettings.getHeight()
                + ",\"framebufferWidth\":" + viewport.framebufferViewport().width + ",\"framebufferHeight\":" + viewport.framebufferViewport().height
                + ",\"sunAzimuth\":" + RendererRemasterLightSettings.getAzimuthDegrees() + ",\"sunElevation\":" + RendererRemasterLightSettings.getElevationDegrees()
                + ",\"brightness\":" + light.brightnessMultiplier + ",\"tone\":\"" + light.debugLabel
                + "\",\"lighting\":\"" + RendererLightingSettings.getMode() + "\",\"profile\":\"" + RendererProfileSettings.getMode()
                + "\",\"geometry\":\"" + RendererGeometrySettings.getMode() + "\",\"shadowQuality\":\"" + shadowQuality() + "\",\"geometryShadow\":" + shadowStats(presenter) + "}";
            Files.write(capture.resolve("client-samples.jsonl"), (sample + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Thread.sleep(1000);
        }
        System.out.println(RendererPerformanceDiagnostics.handleCommand("::pf stop " + phase));
        if (Boolean.getBoolean("renderer.benchmark.snapshot")) {
            Method request = OpenGLFramePresenter.class.getDeclaredMethod("requestFrameCaptureBurst");
            request.setAccessible(true); request.invoke(presenter);
            long captureDeadline = System.currentTimeMillis() + 90000;
            Path index = capture.resolve("captures/capture-index.jsonl");
            while (System.currentTimeMillis() < captureDeadline) {
                if (Files.isRegularFile(index)) {
                    try (java.util.stream.Stream<String> lines = Files.lines(index)) {
                        if (lines.filter(line -> line.contains("\"status\":\"completed\"") || line.contains("\"status\":\"failed\"")).count() >= 12) break;
                    }
                }
                Thread.sleep(500);
            } // Strict captures are outside every timed performance phase.
        }
        closeGpuSink();
        RendererDiagnosticSession.close();
        Files.write(capture.resolve("CLIENT-DONE"), "Completed private renderer phase\n".getBytes(StandardCharsets.UTF_8));
        System.exit(0);
    }
}
