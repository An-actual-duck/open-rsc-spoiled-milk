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
                return shadowSnapshotJson(snapshot);
            } catch (NoSuchMethodException olderCandidate) { /* Retain explicit non-atomic legacy diagnostics. */ }
            Method active = pass.getClass().getDeclaredMethod("isActive"); active.setAccessible(true);
            StringBuilder json = new StringBuilder("{\"coherent\":false,\"active\":" + active.invoke(pass));
            for (String name : new String[]{"nearCpuNanos", "farCpuNanos", "nearDrawCalls", "nearTriangles", "farDrawCalls", "farTriangles", "farRebuilt"}) {
                json.append(",\"").append(name).append("\":").append(field(pass, pass.getClass(), name));
            }
            return json.append('}').toString();
        } catch (NoSuchMethodException baseline) { return "{\"instrumentation\":false}"; }
    }
    private static String shadowSnapshotJson(long[] snapshot) {
        if(snapshot==null || snapshot.length<9) throw new IllegalArgumentException("Incomplete coherent shadow snapshot");
        StringBuilder json=new StringBuilder("{\"sequence\":"+snapshot[0]+",\"active\":"+(snapshot[1]!=0)+",\"farRebuilt\":"+(snapshot[2]!=0));
        String[] keys={"nearCpuNanos","farCpuNanos","nearDrawCalls","nearTriangles","farDrawCalls","farTriangles"};
        for(int i=0;i<keys.length;i++) json.append(",\"").append(keys[i]).append("\":").append(snapshot[i+3]);
        if(snapshot.length>=12) json.append(",\"farMapSize\":").append(snapshot[9])
            .append(",\"historyActive\":").append(snapshot[10]!=0).append(",\"blendPermille\":").append(snapshot[11]);
        return json.append(",\"coherent\":true}").toString();
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
            Method snapshot=window.getClass().getDeclaredMethod("diagnosticSurfaceSize"); snapshot.setAccessible(true);
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
    private static String combatVisuals(mudclient client) {
        int hits=0,effects=0,damaged=0;
        for(int i=0;i<client.getNpcCount();i++) {
            ORSCharacter a=client.getNpc(i);if(a==null)continue;
            if(a.floatingHitSplats.isActive())hits++;
            if(a.combatEffectTime>0||a.pendingCombatEffectType!=0)effects++;
            if(a.healthMax>0&&a.healthCurrent<a.healthMax)damaged++;
        }
        return "{\"activeHitSplatActors\":"+hits+",\"activeCombatEffectActors\":"+effects+",\"damagedHealthActors\":"+damaged+"}";
    }
    private static long renderedFrames()throws Exception {
        synchronized(RenderTelemetry.class){return ((Number)field(null,RenderTelemetry.class,"openGLFrames")).longValue();}
    }
    private static String ownWindowId;
    private static String nativeWindowCommand(String...args)throws Exception {
        Process command=new ProcessBuilder(args).redirectErrorStream(true).start();
        if(!command.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)){command.destroy();throw new IllegalStateException("Scoped WM command timed out");}
        String output=new String(readAll(command.getInputStream()),StandardCharsets.UTF_8).trim();
        if(command.exitValue()!=0)throw new IllegalStateException("Scoped WM command failed: "+output);
        return output;
    }
    private static byte[] readAll(java.io.InputStream in)throws Exception {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();
    }
    private static void visibleLifecycleStep(OpenGLFramePresenter presenter,int second,Path capture)throws Exception {
        if(second>7 || !Boolean.getBoolean("renderer.benchmark.visiblePreview"))return;
        Object controller=field(presenter,OpenGLFramePresenter.class,"windowController");
        String pid=java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        if(ownWindowId==null){String[] ids=nativeWindowCommand("xdotool","search","--onlyvisible","--pid",pid).split("\\s+");if(ids.length!=1)throw new IllegalStateException("Expected exactly one visible window owned by fixture PID");ownWindowId=ids[0];}
        if(!pid.equals(nativeWindowCommand("xdotool","getwindowpid",ownWindowId)))throw new IllegalStateException("Native window PID ownership changed");
        long before=renderedFrames();String action="resumed";
        if(second==0){OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.WINDOWED);action="windowed";}
        if(second==1){nativeWindowCommand("xdotool","windowsize",ownWindowId,"1100","700");action="resize";}
        if(second==2){OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.FULLSCREEN);action="fullscreen";}
        if(second==3){OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.BORDERLESS_FULLSCREEN);action="borderless";}
        if(second==4){OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.WINDOWED);action="windowed-again";}
        if(second==5){nativeWindowCommand("xdotool","windowminimize",ownWindowId);action="iconify";}
        if(second==6){nativeWindowCommand("xdotool","windowmap",ownWindowId);nativeWindowCommand("xdotool","windowactivate","--sync",ownWindowId);action="activate-restore";}
        Thread.sleep(800);
        long after=renderedFrames();
        Files.write(capture.resolve("window-events.jsonl"),("{\"action\":\""+action+"\",\"beforeFrames\":"+before+",\"afterFrames\":"+after+",\"surface\":"+surfaceState(presenter)+",\"flags\":"+windowState(presenter)+",\"gameObjects\":"+((mudclient)field(null,ORSCApplet.class,"mudclient")).getGameObjectInstanceCount()+"}\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        if(second!=5 && after<=before)throw new IllegalStateException("World rendering did not resume after "+action);
        if(second==7 && ((Number)windowFlags(controller)).intValue()!=1)throw new IllegalStateException("Game window did not resume visible/uniconified");
    }
    private static Object windowFlags(Object controller)throws Exception {
        Method m=controller.getClass().getDeclaredMethod("diagnosticWindowFlags");m.setAccessible(true);return m.invoke(controller);
    }
    private static void lifecycleStep(mudclient client,OpenGLFramePresenter presenter,int second,Path capture)throws Exception {
        visibleLifecycleStep(presenter,second,capture);
        if(second==5||second==10||second==15||second==18) {
            String motion=second==5?"same":second==10?"area":second==15?"level":"return";
            Files.write(capture.getParent().resolve("motion-request"),motion.getBytes(StandardCharsets.UTF_8));
        }
        if(second%12==0)RendererShadowSettings.setQuality(RendererShadowSettings.Quality.OFF);
        if(second%12==4)RendererShadowSettings.setQuality(RendererShadowSettings.Quality.LOW);
        if(second%12==8)RendererShadowSettings.setQuality(RendererShadowSettings.Quality.HIGH);
        if(second==20||second==40) {
            Files.write(capture.getParent().resolve("logout-request"),"Ordinary server logout confirmation requested\n".getBytes(StandardCharsets.UTF_8));
            long logoutDeadline=System.currentTimeMillis()+15000;
            while(System.currentTimeMillis()<logoutDeadline&&!field(client,mudclient.class,"currentViewMode").toString().equals("LOGIN"))Thread.sleep(100);
            Thread.sleep(2500);
            if(!field(client,mudclient.class,"currentViewMode").toString().equals("LOGIN"))throw new IllegalStateException("Logout did not return to login");
            Files.write(capture.resolve("lifecycle-events.jsonl"),("{\"event\":\"logout\",\"second\":"+second+",\"shadow\":"+shadowStats(presenter)+"}\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            set(client,mudclient.class,"autoLoginTimeout",1);
            Method login=mudclient.class.getDeclaredMethod("login",int.class,String.class,String.class,boolean.class);login.setAccessible(true);login.invoke(client,-12,"RendererProof13","renderdemo",false);
            long deadline=System.currentTimeMillis()+30000;
            while(System.currentTimeMillis()<deadline&&!field(client,mudclient.class,"currentViewMode").toString().equals("GAME"))Thread.sleep(100);
            if(!field(client,mudclient.class,"currentViewMode").toString().equals("GAME"))throw new IllegalStateException("Same-JVM relog failed");
            set(client,mudclient.class,"welcomeScreenShown",false);client.setShowDialogMessage(false);
            Files.write(capture.resolve("lifecycle-events.jsonl"),("{\"event\":\"relogin\",\"second\":"+second+"}\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        }
    }
    private static boolean farPollEveryFrame() throws Exception {
        try { return Boolean.TRUE.equals(field(null,Class.forName("orsc.OpenGLGpuTimer"),"FAR_QUERY_POLL_EVERY_FRAME")); }
        catch(ClassNotFoundException | NoSuchFieldException older) { return false; }
    }
    private static String roofState(Renderer3DFrame frame) {
        int roofTriangles=0,maxCovered=0;
        for(Renderer3DWorldChunkFrame.ChunkMesh chunk:frame.getWorldChunkFrame().getChunks()) {
            roofTriangles+=chunk.getRoofTriangles(); maxCovered=Math.max(maxCovered,chunk.getRoofCoveredTileCount());
        }
        return "{\"visibility\":\""+frame.getRoofVisibility()+"\",\"residentRoofTriangles\":"+roofTriangles+",\"maximumSourceCoveredTiles\":"+maxCovered+"}";
    }
    private static int onScreenActors(Renderer3DFrame frame) {
        int count=0;
        for(Renderer3DFrame.CharacterSprite actor:frame.getCharacterSprites()) {
            if(actor.isProjected() && actor.getDrawWidth()>0 && actor.getDrawHeight()>0
                && actor.getDrawX()<frame.getViewportWidth() && actor.getDrawY()<frame.getViewportHeight()
                && actor.getDrawX()+actor.getDrawWidth()>0 && actor.getDrawY()+actor.getDrawHeight()>0) count++;
        }
        return count; // Projection candidates; depth occlusion can still hide pixels.
    }
    private static void dumpTerrain(mudclient client, OpenGLFramePresenter presenter, Path capture) throws Exception {
        Renderer3DFrame frame=publishedFrame(presenter);
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
        writeFrameMetadata(frame,presenter,capture.resolve("terrain-frame.json"),exported);
        System.out.println("Exported immutable TERRAIN triangles="+exported);
    }
    /** Read an already-published frame under its existing publication lock.
     * Scene.getRenderer3DFrame can still be inside endScene, before chunks attach.
     * Chunk/texture data is immutable and not part of the released face-storage pool.
     */
    private static Renderer3DFrame tryPublishedFrame(OpenGLFramePresenter presenter) throws Exception {
        Object lock=field(presenter,OpenGLFramePresenter.class,"frameLock");
        synchronized(lock) {
            Object pending=field(presenter,OpenGLFramePresenter.class,"pendingFrame");
            if(pending!=null) {
                Renderer3DFrame frame=(Renderer3DFrame)field(pending,pending.getClass(),"renderer3DFrame");
                if(frame!=null && frame.getWorldChunkFrame().getChunkCount()>0) return frame;
            }
        }
        return null; // The renderer may already have consumed this ephemeral queue.
    }
    private static Renderer3DFrame publishedFrame(OpenGLFramePresenter presenter) throws Exception {
        long deadline=System.nanoTime()+3000000000L;
        while(System.nanoTime()<deadline) {
            Renderer3DFrame frame=tryPublishedFrame(presenter);
            if(frame!=null) return frame;
            Thread.sleep(1);
        }
        throw new IllegalStateException("No published immutable world frame for requested exact export");
    }
    private static String frameObservationsJson(Renderer3DFrame frame) {
        if(frame==null) return "\"publishedFrameAvailable\":false,\"publishedFrameUnavailableReason\":\"no-pending-published-frame\",\"roof\":null,\"onScreenProjectedActorCandidates\":null";
        return "\"publishedFrameAvailable\":true,\"publishedFrameUnavailableReason\":null,\"roof\":"+roofState(frame)
            +",\"onScreenProjectedActorCandidates\":"+onScreenActors(frame);
    }
    /** Exports verified native cutout materials, including all non-terrain kinds; no GL readback or mutation. */
    private static void dumpFences(mudclient client, OpenGLFramePresenter presenter, Path capture) throws Exception {
        Renderer3DFrame frame=publishedFrame(presenter);
        java.util.Set<Integer> textures=new java.util.TreeSet<Integer>();
        int exported=0,chunkIndex=0;
        Object renderer=field(presenter,OpenGLFramePresenter.class,"worldChunkRenderer");
        Method material=renderer.getClass().getDeclaredMethod("effectiveChunkTextureId",Renderer3DFrame.class,int.class,int.class); material.setAccessible(true);
        try(java.io.BufferedWriter writer=Files.newBufferedWriter(capture.resolve("fence-triangles.tsv"),StandardCharsets.UTF_8)) {
            writer.write("chunkIndex\tplane\tkind\tmaterialFamily\toriginWorldX\toriginWorldZ\tvertexOffsetX\tvertexOffsetZ\ttriangleIndex\ttexture\tfallbackColor\teffectiveTexture");
            for(int corner=0;corner<3;corner++) writer.write("\tx"+corner+"\ty"+corner+"\tz"+corner+"\tu"+corner+"\tv"+corner+"\tnx"+corner+"\tny"+corner+"\tnz"+corner);
            writer.write("\n");
            for(Renderer3DWorldChunkFrame.ChunkMesh chunk:frame.getWorldChunkFrame().getChunks()) {
                for(int t=0;t<chunk.getTriangleCount();t++) {
                    int texture=chunk.getTriangleTexture(t);
                    Renderer3DModelKind kind=chunk.getTriangleModelKind(t);
                    if(kind==Renderer3DModelKind.TERRAIN) continue;
                    int fallback=chunk.getTriangleFallbackColor(t);
                    int effective=((Number)material.invoke(renderer,frame,texture,fallback)).intValue();
                    if(effective!=10 && effective!=12) continue;
                    textures.add(effective);
                    StringBuilder row=new StringBuilder().append(chunkIndex).append('\t').append(chunk.getPlane()).append('\t').append(kind).append('\t').append(chunk.getTriangleMaterialFamily(t)).append('\t').append(chunk.getOriginWorldX()).append('\t').append(chunk.getOriginWorldZ()).append('\t').append(chunk.getVertexOffsetX()).append('\t').append(chunk.getVertexOffsetZ()).append('\t').append(t).append('\t').append(texture).append('\t').append(fallback).append('\t').append(effective);
                    for(int corner=0;corner<3;corner++) {
                        int v=chunk.getIndex(t*3+corner);
                        for(int axis=0;axis<3;axis++) row.append('\t').append(chunk.getVertexCoord(v*3+axis));
                        row.append('\t').append(chunk.getVertexTextureU(v)).append('\t').append(chunk.getVertexTextureV(v)).append('\t').append(chunk.getVertexNormalX(v)).append('\t').append(chunk.getVertexNormalY(v)).append('\t').append(chunk.getVertexNormalZ(v));
                    }
                    writer.write(row.append('\n').toString()); exported++;
                }
                chunkIndex++;
            }
        }
        if(exported==0) throw new IllegalStateException("No actual native wall caster triangles");
        try(java.io.BufferedWriter writer=Files.newBufferedWriter(capture.resolve("fence-textures.tsv"),StandardCharsets.UTF_8)) {
            writer.write("texture\twidth\theight\tsignature\tx\ty\trgba\n");
            for(int id:textures) {
                Renderer3DTextureData texture=frame.getTexture(id);
                if(texture==null) throw new IllegalStateException("Missing referenced native fence texture"+id);
                int[] pixels=texture.getRgbaPixels();
                for(int y=0;y<texture.getHeight();y++) for(int x=0;x<texture.getWidth();x++)
                    writer.write(id+"\t"+texture.getWidth()+"\t"+texture.getHeight()+"\t"+texture.getSignature()+"\t"+x+"\t"+y+"\t"+Integer.toUnsignedString(pixels[y*texture.getWidth()+x])+"\n");
            }
        }
        writeFrameMetadata(frame,presenter,capture.resolve("fence-frame.json"),-1);
        System.out.println("Exported actual native fence triangles="+exported+" textures="+textures);
    }
    private static void writeFrameMetadata(Renderer3DFrame frame, OpenGLFramePresenter presenter, Path destination, int exported) throws Exception {
        Renderer3DWorldChunkFrame chunks=frame.getWorldChunkFrame();
        StringBuilder metadata=new StringBuilder("{\"terrainTriangles\":"+exported+",\"residentTriangles\":"+chunks.getTotalTriangleCount()+",\"chunks\":"+chunks.getChunkCount()+",\"sunAzimuth\":"+RendererRemasterLightSettings.getAzimuthDegrees()+",\"sunElevation\":"+RendererRemasterLightSettings.getElevationDegrees());
        String[] methods={"getViewportWidth","getViewportHeight","getCenterX","getCenterY","getCameraOffsetX","getCameraOffsetY","getCameraOffsetZ","getCameraRotationX","getCameraRotationY","getCameraRotationZ","getPerspectiveShift","getNearPlane","getFogDistance","getFogStartDistance","getActivePlane"};
        for(String name:methods) metadata.append(",\"").append(name).append("\":").append(Renderer3DFrame.class.getMethod(name).invoke(frame));
        Object renderer=field(presenter,OpenGLFramePresenter.class,"worldChunkRenderer");
        for(String name:new String[]{"worldViewMatrix","projectionMatrix"}) {
            Method matrix=renderer.getClass().getDeclaredMethod(name,Renderer3DFrame.class); matrix.setAccessible(true);
            metadata.append(",\"").append(name).append("\":").append(java.util.Arrays.toString((float[])matrix.invoke(renderer,frame)));
        }
        Files.write(destination,metadata.append("}\n").toString().getBytes(StandardCharsets.UTF_8));
    }
    private static void dumpRoofMask(mudclient client,OpenGLFramePresenter presenter,Path capture) throws Exception {
        Object renderer=field(presenter,OpenGLFramePresenter.class,"worldChunkRenderer");
        Method diagnostics=renderer.getClass().getDeclaredMethod("geometryShadowDiagnostics"); diagnostics.setAccessible(true);
        Object shadows=diagnostics.invoke(renderer), mask=field(shadows,shadows.getClass(),"roofMask");
        Method pixels=mask.getClass().getDeclaredMethod("pixels"); pixels.setAccessible(true);
        java.nio.ByteBuffer rgba=(java.nio.ByteBuffer)pixels.invoke(mask);
        int width=((Number)field(mask,mask.getClass(),"width")).intValue(),height=((Number)field(mask,mask.getClass(),"height")).intValue();
        int minX=((Number)field(mask,mask.getClass(),"minX")).intValue(),minZ=((Number)field(mask,mask.getClass(),"minZ")).intValue(),covered=0;
        try(java.io.BufferedWriter writer=Files.newBufferedWriter(capture.resolve("roof-mask.tsv"),StandardCharsets.UTF_8)) {
            writer.write("tileX\ttileZ\tworldCenterX\tworldCenterZ\tred\n");
            if(rgba!=null) for(int z=0;z<height;z++) for(int x=0;x<width;x++) {
                int red=rgba.get((z*width+x)*4)&255; if(red>0) covered++;
                writer.write(x+"\t"+z+"\t"+(minX+x*128+64)+"\t"+(minZ+z*128+64)+"\t"+red+"\n");
            }
        }
        StringBuilder metadata=new StringBuilder("{");
        for(String name:new String[]{"plane","minX","minZ","width","height","signature"}) {
            if(metadata.length()>1) metadata.append(',');
            metadata.append('"').append(name).append("\":").append(field(mask,mask.getClass(),name));
        }
        Files.write(capture.resolve("roof-mask.json"),metadata.append(",\"coveredPixels\":").append(covered).append("}\n").toString().getBytes(StandardCharsets.UTF_8));
        writeFrameMetadata(publishedFrame(presenter),presenter,capture.resolve("roof-frame.json"),-1);
    }
    private static void applyDiagnosticFog() {
        RendererFogSettings.setMode("off".equals(System.getProperty("renderer.benchmark.fog","on")) ? RendererFogSettings.Mode.OFF : RendererFogSettings.Mode.ON);
    }
    private static void applyDiagnosticTone() {
        String tone=System.getProperty("spoiledmilk.openglTonePreview");
        if(tone!=null) RendererToneSettings.setMode(RendererToneSettings.Mode.from(tone));
    }
    private static void applyPreviewWindowMode() {
        if(Boolean.getBoolean("renderer.benchmark.visiblePreview"))
            OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.from(System.getProperty("spoiledmilk.openglWindowMode","borderless-fullscreen")));
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
        for(int warm=0;warm<Integer.getInteger("renderer.benchmark.warmupSeconds",15);warm++) {
            applyPreviewWindowMode(); applyDiagnosticTone(); applyDiagnosticFog(); Thread.sleep(1000);
        }
        applyPreviewWindowMode(); applyDiagnosticTone(); applyDiagnosticFog();
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
            if(phase.equals("lifecycle"))lifecycleStep(client,presenter,second,capture);
            if (phase.contains("camera")) client.cameraRotation = (rotation + second * 3) & 255;
            Renderer3DFrame sampledFrame=tryPublishedFrame(presenter);
            RendererDayNightCycle.Presentation light = RendererDayNightCycle.currentPresentation();
            String sample = "{\"seconds\":" + second + ",\"mode\":\"" + field(client, mudclient.class, "currentViewMode")
                + "\",\"players\":" + client.getPlayerCount() + ",\"npcs\":" + client.getNpcCount()
                + ",\"gpuFarPollEveryFrame\":" + farPollEveryFrame() + "," + frameObservationsJson(sampledFrame)
                + ",\"hideRoofs\":" + Config.C_HIDE_ROOFS + ",\"gameObjects\":" + client.getGameObjectInstanceCount() + ",\"wallObjects\":" + client.getWallObjectInstanceCount()
                + ",\"serverState\":" + new String(Files.readAllBytes(run.resolve("status.json")), StandardCharsets.UTF_8)
                + ",\"combatVisuals\":"+combatVisuals(client)+ ",\"activeProjectileActors\":" + activeProjectiles(client) + ",\"windowState\":" + windowState(presenter)
                + ",\"cameraRotation\":" + client.cameraRotation + ",\"zoomSetting\":" + osConfig.C_LAST_ZOOM
                + ",\"windowMode\":\"" + OpenGLWindowSettings.getMode() + "\",\"logicalWidth\":" + RenderSurfaceSettings.getWidth() + ",\"logicalHeight\":" + RenderSurfaceSettings.getHeight()
                + ",\"contentViewportWidth\":" + viewport.framebufferViewport().width + ",\"contentViewportHeight\":" + viewport.framebufferViewport().height + ",\"surface\":" + surfaceState(presenter)
                + ",\"sunAzimuth\":" + RendererRemasterLightSettings.getAzimuthDegrees() + ",\"sunElevation\":" + RendererRemasterLightSettings.getElevationDegrees()
                + ",\"fogMode\":\"" + RendererFogSettings.getMode() + "\",\"toneMode\":\"" + RendererToneSettings.getMode() + "\",\"brightness\":" + light.brightnessMultiplier + ",\"tone\":\"" + light.debugLabel
                + "\",\"lighting\":\"" + RendererLightingSettings.getMode() + "\",\"profile\":\"" + RendererProfileSettings.getMode()
                + "\",\"geometry\":\"" + RendererGeometrySettings.getMode() + "\",\"shadowQuality\":\"" + shadowQuality() + "\",\"geometryShadow\":" + shadowStats(presenter) + "}";
            Files.write(capture.resolve("client-samples.jsonl"), (sample + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Thread.sleep(1000);
        }
        System.out.println(RendererPerformanceDiagnostics.handleCommand("::pf stop " + phase));
        if (Boolean.getBoolean("renderer.benchmark.dumpRoofMask")) dumpRoofMask(client,presenter,capture);
        if (Boolean.getBoolean("renderer.benchmark.dumpTerrain")) dumpTerrain(client,presenter,capture);
        if (Boolean.getBoolean("renderer.benchmark.dumpFences")) dumpFences(client,presenter,capture);
        if (Boolean.getBoolean("renderer.benchmark.snapshot")) {
            Method request = OpenGLFramePresenter.class.getDeclaredMethod("requestFrameCaptureBurst");
            request.setAccessible(true); request.invoke(presenter);
            int captureFrames=Integer.getInteger("renderer.benchmark.captureFrames",12);
            if(captureFrames==1) {
                // Own test process, post-phase only: cap the existing volatile diagnostic burst.
                // A frame already in flight can yield2 completed images; wait for burst completion.
                set(presenter,OpenGLFramePresenter.class,"frameCaptureBurstRemaining",1);
            }
            long captureDeadline = System.currentTimeMillis() + 90000;
            Path index = capture.resolve("captures/capture-index.jsonl");
            while (System.currentTimeMillis() < captureDeadline) {
                if (Files.isRegularFile(index)) {
                    try (java.util.stream.Stream<String> lines = Files.lines(index)) {
                        if (lines.filter(line -> line.contains("\"status\":\"completed\"") || line.contains("\"status\":\"failed\"")).count() >= captureFrames
                            && new String(Files.readAllBytes(capture.resolve("events.jsonl")),StandardCharsets.UTF_8).contains("\"eventType\":\"capture.burst.completed\"")) break;
                    }
                }
                Thread.sleep(500);
            } // Strict captures are outside every timed performance phase.
        }
        if(phase.equals("lifecycle")) {
            RendererShadowSettings.setQuality(RendererShadowSettings.Quality.LOW);
            RendererProfileApplier.SettingsStore store=(RendererProfileApplier.SettingsStore)field(null,mudclient.class,"RENDERER_PROFILE_SETTINGS_STORE");
            RendererProfileApplier.persist(store,RendererProfileApplier.Change.SHADOWS,RendererProfileApplier.Change.WINDOW);
            java.util.Properties saved=new java.util.Properties();try(java.io.InputStream in=Files.newInputStream(clientRoot.resolve("clientSettings.conf"))){saved.load(in);}
            if(!"low".equals(saved.getProperty("shadow_quality")))throw new IllegalStateException("Actual client setting store did not persist selected shadow quality");
            Files.write(capture.resolve("SETTINGS-PERSISTED"),"Actual production settings store saved Low shadow preference\n".getBytes(StandardCharsets.UTF_8));
        }
        closeGpuSink();
        RendererDiagnosticSession.close();
        Files.write(capture.resolve("CLIENT-DONE"), "Completed private renderer phase\n".getBytes(StandardCharsets.UTF_8));
        System.exit(0);
    }
}
