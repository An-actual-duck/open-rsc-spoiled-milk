package orsc;

import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import orsc.graphics.three.*;

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
    private static String surfaceState(OpenGLFramePresenter presenter) throws Exception {
        Object window=field(presenter,OpenGLFramePresenter.class,"windowController");
        try {
            Method snapshot=window.getClass().getDeclaredMethod("diagnosticSurfaceSnapshot"); snapshot.setAccessible(true);
            int[] size=(int[])snapshot.invoke(window);
            return "{\"framebufferWidth\":"+size[0]+",\"framebufferHeight\":"+size[1]+",\"windowWidth\":"+size[2]+",\"windowHeight\":"+size[3]+"}";
        } catch(NoSuchMethodException unavailable) { return "null"; }
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
    private static int onScreenActors(mudclient client) throws Exception {
        Scene scene=(Scene)field(client,mudclient.class,"scene");
        Renderer3DFrame frame=scene.getRenderer3DFrame();
        if(frame==null) return 0;
        int count=0;
        for(Renderer3DFrame.CharacterSprite actor:frame.getCharacterSprites()) {
            if(actor.isProjected() && actor.getDrawWidth()>0 && actor.getDrawHeight()>0
                && actor.getDrawX()<frame.getViewportWidth() && actor.getDrawY()<frame.getViewportHeight()
                && actor.getDrawX()+actor.getDrawWidth()>0 && actor.getDrawY()+actor.getDrawHeight()>0) count++;
        }
        return count; // Projection candidates; depth occlusion can still hide pixels.
    }
    private static void dumpTerrain(mudclient client, OpenGLFramePresenter presenter, Path capture) throws Exception {
        Scene scene=(Scene)field(client,mudclient.class,"scene");
        Renderer3DFrame frame=scene.getRenderer3DFrame();
        Renderer3DWorldChunkFrame chunks=frame.getWorldChunkFrame();
        if(chunks==null || chunks.getChunkCount()==0) throw new IllegalStateException("No immutable resident terrain to export");
        int exported=0, chunkIndex=0;
        try(java.io.BufferedWriter writer=Files.newBufferedWriter(capture.resolve("terrain-triangles.tsv"),StandardCharsets.UTF_8)) {
            writer.write("chunkIndex\tplane\tchunkRole\toriginWorldX\toriginWorldZ\tvertexOffsetX\tvertexOffsetZ\tlogicalOffsetX\tlogicalOffsetZ\ttriangleIndex\ttexture\tfallbackColor");
            for(int corner=0;corner<3;corner++) writer.write("\tx"+corner+"\ty"+corner+"\tz"+corner+"\tu"+corner+"\tv"+corner+"\tnx"+corner+"\tny"+corner+"\tnz"+corner);
            writer.write("\n");
            for(Renderer3DWorldChunkFrame.ChunkMesh chunk:chunks.getChunks()) {
                for(int triangle=0;triangle<chunk.getTriangleCount();triangle++) {
                    if(chunk.getTriangleModelKind(triangle)!=Renderer3DModelKind.TERRAIN) continue;
                    StringBuilder row=new StringBuilder().append(chunkIndex).append('\t').append(chunk.getPlane()).append('\t').append(chunk.getChunkRole()).append('\t').append(chunk.getOriginWorldX()).append('\t').append(chunk.getOriginWorldZ()).append('\t').append(chunk.getVertexOffsetX()).append('\t').append(chunk.getVertexOffsetZ()).append('\t').append(chunk.getLogicalWorldOffsetX()).append('\t').append(chunk.getLogicalWorldOffsetZ()).append('\t').append(triangle).append('\t').append(chunk.getTriangleTexture(triangle)).append('\t').append(chunk.getTriangleFallbackColor(triangle));
                    for(int corner=0;corner<3;corner++) {
                        int vertex=chunk.getIndex(triangle*3+corner);
                        for(int axis=0;axis<3;axis++) row.append('\t').append(chunk.getVertexCoord(vertex*3+axis));
                        row.append('\t').append(chunk.getVertexTextureU(vertex)).append('\t').append(chunk.getVertexTextureV(vertex)).append('\t').append(chunk.getVertexNormalX(vertex)).append('\t').append(chunk.getVertexNormalY(vertex)).append('\t').append(chunk.getVertexNormalZ(vertex));
                    }
                    writer.write(row.append('\n').toString()); exported++;
                }
                chunkIndex++;
            }
        }
        StringBuilder metadata=new StringBuilder("{\"terrainTriangles\":"+exported+",\"residentTriangles\":"+chunks.getTotalTriangleCount()+",\"chunks\":"+chunks.getChunkCount()+",\"sunAzimuth\":"+RendererRemasterLightSettings.getAzimuthDegrees()+",\"sunElevation\":"+RendererRemasterLightSettings.getElevationDegrees());
        String[] methods={"getViewportWidth","getViewportHeight","getCenterX","getCenterY","getCameraOffsetX","getCameraOffsetY","getCameraOffsetZ","getCameraRotationX","getCameraRotationY","getCameraRotationZ","getPerspectiveShift","getNearPlane","getFogDistance","getFogStartDistance","getActivePlane"};
        for(String name:methods) metadata.append(",\"").append(name).append("\":").append(Renderer3DFrame.class.getMethod(name).invoke(frame));
        Object renderer=field(presenter,OpenGLFramePresenter.class,"worldChunkRenderer");
        for(String name:new String[]{"worldViewMatrix","projectionMatrix"}) {
            Method matrix=renderer.getClass().getDeclaredMethod(name,Renderer3DFrame.class); matrix.setAccessible(true);
            metadata.append(",\"").append(name).append("\":").append(java.util.Arrays.toString((float[])matrix.invoke(renderer,frame)));
        }
        Files.write(capture.resolve("terrain-frame.json"),metadata.append("}\n").toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("Exported immutable TERRAIN triangles="+exported);
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
        Thread.sleep(Integer.getInteger("renderer.benchmark.warmupSeconds",15)*1000L);
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
            if(Files.exists(run.resolve("FIXTURE-FAILED"))) throw new IllegalStateException("Private fixture setup failed; aborting measurement");
            client.setCameraZoomSetting(Integer.getInteger("renderer.benchmark.zoom", 75));
            if (phase.contains("camera")) client.cameraRotation = (rotation + second * 3) & 255;
            RendererDayNightCycle.Presentation light = RendererDayNightCycle.currentPresentation();
            String sample = "{\"seconds\":" + second + ",\"mode\":\"" + field(client, mudclient.class, "currentViewMode")
                + "\",\"players\":" + client.getPlayerCount() + ",\"npcs\":" + client.getNpcCount()
                + ",\"hideRoofs\":" + Config.C_HIDE_ROOFS + ",\"gameObjects\":" + client.getGameObjectInstanceCount() + ",\"wallObjects\":" + client.getWallObjectInstanceCount()
                + ",\"serverState\":" + new String(Files.readAllBytes(run.resolve("status.json")), StandardCharsets.UTF_8)
                + ",\"onScreenProjectedActorCandidates\":" + onScreenActors(client)
                + ",\"activeProjectileActors\":" + activeProjectiles(client) + ",\"windowState\":" + windowState(presenter)
                + ",\"cameraRotation\":" + client.cameraRotation + ",\"zoomSetting\":" + osConfig.C_LAST_ZOOM
                + ",\"logicalWidth\":" + RenderSurfaceSettings.getWidth() + ",\"logicalHeight\":" + RenderSurfaceSettings.getHeight()
                + ",\"contentViewportWidth\":" + viewport.framebufferViewport().width + ",\"contentViewportHeight\":" + viewport.framebufferViewport().height + ",\"surface\":" + surfaceState(presenter)
                + ",\"sunAzimuth\":" + RendererRemasterLightSettings.getAzimuthDegrees() + ",\"sunElevation\":" + RendererRemasterLightSettings.getElevationDegrees()
                + ",\"brightness\":" + light.brightnessMultiplier + ",\"tone\":\"" + light.debugLabel
                + "\",\"lighting\":\"" + RendererLightingSettings.getMode() + "\",\"profile\":\"" + RendererProfileSettings.getMode()
                + "\",\"geometry\":\"" + RendererGeometrySettings.getMode() + "\",\"shadowQuality\":\"" + shadowQuality() + "\",\"geometryShadow\":" + shadowStats(presenter) + "}";
            Files.write(capture.resolve("client-samples.jsonl"), (sample + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Thread.sleep(1000);
        }
        System.out.println(RendererPerformanceDiagnostics.handleCommand("::pf stop " + phase));
        if (Boolean.getBoolean("renderer.benchmark.dumpTerrain")) dumpTerrain(client,presenter,capture);
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
