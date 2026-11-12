package orsc.browser;

import java.io.*;
import java.net.*;

/** Private browser spike entry point. Not part of the desktop/client build. */
public final class BrowserBootstrap {
    private BrowserBootstrap() { }
    public static native void report(String message);
    public static native void reportState(String mode, int fps, int loadingPercent);
    public static native void reportStartupDiagnostics(String phase,String detail);
    public static native void reportLoop(String mode,boolean earlyFaceCull,boolean fullResidentOwnership);
    public static native void reportTelemetry(String names,double[] values);
    public static native void reportSpriteKeyPolicy(boolean requested,boolean enabled);
    public static native void reportOverlayRectPolicy(String requested,String enabled);
    public static native void reportPreparation(boolean enabled,boolean active,boolean animated);
    public static native void reportRegionPreparationDiagnostics(boolean available,double[] counters);
    public static native void reportCandidatePolicy(String name,boolean requested,boolean available,boolean enabled);
    public static native void reportBatchedModelWork(int[] counters);
    public static native void reportWholeZoneWork(int[] counters);
    public static native void reportStaticSceneWork(int[] counters);
    public static native void reportStaticIdentityWork(int[] counters);
    public static native void reportBaselineReuseWork(double[] counters);
    public static native void reportCloneTemplateWork(double[] counters);
    public static native void reportWholeRamWork(int[] counters);
    public static native void reportRegionHandoffTrace(String phases,double[] values);
    public static native void reportRepeatableState(String username,int[] counters);
    public static native void reportCombatState(String username,String names,int[] counters);
    public static native void reportModelPickBounds(boolean requested,boolean enabled);
    private static native int wsOpen();
    private static native byte[] wsRead(int socket, int maximum);
    private static native int wsAvailable(int socket);
    private static native void wsWrite(int socket, byte[] bytes);
    private static native void wsClose(int socket);
    private static native int[] awaitMiddleMouseEvent();
    private static native int[] syncDisplaySettings(int action);
    private static native void reportInputError(String message);

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "input-probe".equals(args[0])) {
            javax.swing.SwingUtilities.invokeAndWait(new Runnable() { public void run() {
                javax.swing.JFrame frame = new javax.swing.JFrame("Private coordinate probe");
                frame.setUndecorated(true);
                java.awt.Canvas canvas = new java.awt.Canvas();
                canvas.setPreferredSize(new java.awt.Dimension(960, 540));
                java.awt.event.MouseAdapter input = new java.awt.event.MouseAdapter() {
                    private int samples;
                    private void emit(java.awt.event.MouseEvent e) {
                        if(samples>=128)return;
                        samples++;
                        report("input-probe:" + e.getID() + ":" + e.getX() + ":" + e.getY() + ":" + e.getButton());
                    }
                    public void mousePressed(java.awt.event.MouseEvent e) { emit(e); }
                    public void mouseReleased(java.awt.event.MouseEvent e) { emit(e); }
                    public void mouseMoved(java.awt.event.MouseEvent e) { emit(e); }
                    public void mouseDragged(java.awt.event.MouseEvent e) { emit(e); }
                    public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) { emit(e); }
                };
                canvas.addMouseListener(input); canvas.addMouseMotionListener(input); canvas.addMouseWheelListener(input);
                frame.add(canvas); frame.pack(); frame.setLocation(0,0); frame.setVisible(true);
                report("input-probe:ready");
            }});
            return;
        }
        if (args.length > 0 && "mesh-probe".equals(args[0])) {
            try { report(MeshCompatibilityProbe.run()); }
            catch (Throwable error) { error.printStackTrace(); report("mesh-probe:blocked:" + error); }
            return;
        }
        if (args.length > 0 && "native-probe".equals(args[0])) {
            try {
                report("native-probe:start");
                boolean initialized = org.lwjgl.glfw.GLFW.glfwInit();
                report("native-probe:glfwInit=" + initialized);
                if (initialized) org.lwjgl.glfw.GLFW.glfwTerminate();
            } catch (Throwable error) {
                error.printStackTrace();
                report("native-probe:blocked:" + error);
            }
            return;
        }
        final boolean webgl=args.length>0 && "webgl".equals(args[0]);
        final boolean benchmark=Boolean.getBoolean("spoiledmilk.browserBenchmark");
        final BrowserOptimizedDefaults.Resolution optimizedDefaults=BrowserOptimizedDefaults.apply(webgl,benchmark,
            BrowserOptimizedDefaults.capabilities(BrowserBootstrap.class.getClassLoader()));
        final String fixture=System.getProperty("spoiledmilk.browserRepeatableFixture");
        final int privatePort=privateFixturePort(fixture,System.getProperty("spoiledmilk.browserPrivateLoopbackPort"));
        final boolean combat="renderer-combat-v1".equals(fixture);
        final boolean repeatable="repeatable-v1".equals(fixture)||combat;
        if(repeatable&&(!webgl||!Boolean.getBoolean("spoiledmilk.browserBenchmark")))throw new IllegalArgumentException("Repeatable camera is private benchmark only");
        final RepeatableCamera fixedCamera=repeatable?new RepeatableCamera():null;
        final BrowserCombatWorkload combatWorkload=combat?new BrowserCombatWorkload():null;
        report((webgl?"webgl-client":"software-client")+":copying-private-files");
        try (BufferedReader index = new BufferedReader(new FileReader("/app/bootstrap-files.txt"))) {
            String relative;
            while ((relative = index.readLine()) != null) {
                if (relative.startsWith("/") || relative.contains("..") || relative.contains("\\")) throw new IOException("Unsafe staged path");
                File destination = new File("/files", relative);
                destination.getParentFile().mkdirs();
                try (InputStream input = new FileInputStream("/app/" + relative); OutputStream output = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[32768]; int count;
                    while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
                }
            }
        }
        if((Boolean.getBoolean("spoiledmilk.browserBaselineReuse")||Boolean.getBoolean("spoiledmilk.browserCloneTemplates")) && (!webgl
            || !Boolean.getBoolean("spoiledmilk.browserBenchmark") || !Boolean.getBoolean("spoiledmilk.browserWholeRam")
            || !Boolean.getBoolean("spoiledmilk.browserWholeZone") || !Boolean.getBoolean("spoiledmilk.browserStaticSceneReuse")))
            throw new IllegalStateException("Baseline reuse requires private whole-RAM/static-scene WebGL mode");
        if(Boolean.getBoolean("spoiledmilk.browserWholeRam")) {
            if(!webgl||!Boolean.getBoolean("spoiledmilk.browserBenchmark")||!Boolean.getBoolean("spoiledmilk.browserWholeZone"))throw new IllegalStateException("Whole-RAM delivery requires private whole-zone WebGL mode");
            java.util.HashSet<String> expected=new java.util.HashSet<String>(java.util.Arrays.asList("Cache/whole-ram/manifest.json","Cache/whole-ram/source.bin","Cache/whole-ram/prepared.bin"));
            try(BufferedReader index=new BufferedReader(new FileReader("/app/bootstrap-whole-ram-files.txt"))){String relative;while((relative=index.readLine())!=null){if(!expected.remove(relative))throw new IOException("Unexpected/duplicate whole-RAM staged path");File destination=new File("/files",relative);destination.getParentFile().mkdirs();try(InputStream input=new FileInputStream("/app/"+relative);OutputStream output=new FileOutputStream(destination)){byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);}}}
            if(!expected.isEmpty())throw new IOException("Incomplete whole-RAM delivery roster");
        }
        // Explicit test-only software presentation. Desktop defaults are untouched.
        String[] off = {"openglPresenter", "openglInput", "openglPrimaryWindow",
            "openglWorldMesh", "openglWorldMeshTexturedVisible",
            "openglWorldMeshTexturedStaticVisible", "openglWorldChunksVisible",
            "openglWorldChunksResidentObjects", "openglWorldChunksSpatialCull",
            "openglWorldSpritesVisible", "skipLegacyWorldRaster", "modernClientLoop"};
        for (String name : off) System.setProperty("spoiledmilk." + name, "false");
        if(webgl) {
            // Private WebGL entrypoint opts into the verified backend. Explicit
            // comparative javaProperties still win; desktop/software defaults do not change.
            String[][] defaults={{"browserDepthSource","gpu"},{"browserEarlyFaceCull","true"},
                {"browserOwnsWallsAndRoofs","true"},{"browserOverlayCoverage","true"},
                {"browserSpriteCache","cached"},{"browserLoop","modern"}};
            for(String[] setting:defaults)if(System.getProperty("spoiledmilk."+setting[0])==null)
                System.setProperty("spoiledmilk."+setting[0],setting[1]);
            String[] on={"renderer3DGeometryCapture","openglWorldMesh","openglWorldMeshTexturedVisible","openglWorldMeshTexturedStaticVisible","openglWorldReplacementComposite","openglWorldChunksTexturedVisible","openglWorldChunksReplacementComposite","openglWorldChunksTrustedReplacement","openglWorldChunksResidentObjects","openglWorldSpritesVisible","skipLegacyWorldRaster","modernClientLoop"};
            for(String name:on)System.setProperty("spoiledmilk."+name,"true");
            System.setProperty("spoiledmilk.renderer3DVisibleWorld","false");
            // No native presenter or GLFW initialization; only the browser sink owns GPU submission.
            if(Boolean.getBoolean("spoiledmilk.browserWholeZone")){
                boolean[] actual=BenchmarkCandidatePolicies.inspect("orsc.BrowserWebGLBridge","isWholeZoneEnabled");
                if(!actual[0]||!actual[1])throw new IllegalStateException("Requested whole-zone mode is unsupported by this client JAR");
            }
            if(Boolean.getBoolean("spoiledmilk.browserWholeRam")&&!orsc.BrowserWebGLBridge.isWholeRamEnabled())throw new IllegalStateException("Whole-RAM requires explicit benchmark and whole-zone mode");
            if(Boolean.getBoolean("spoiledmilk.browserBaselineReuse")) {
                boolean[] actual=BenchmarkCandidatePolicies.inspect("orsc.PacketHandler","isBrowserBaselineReuseEnabled");
                if(!actual[0]||!actual[1])throw new IllegalStateException("Requested baseline reuse is unsupported by this client JAR");
            }
            if(Boolean.getBoolean("spoiledmilk.browserCloneTemplates")){
                boolean[] actual=BenchmarkCandidatePolicies.inspect("orsc.mudclient","isBrowserCloneTemplatesEnabled");
                if(!actual[0]||!actual[1])throw new IllegalStateException("Requested clone templates unsupported by client JAR");
            }
            orsc.BrowserWebGLBridge.install();
            reportOverlayRectPolicy(System.getProperty("spoiledmilk.browserOverlayRectMode","off"),orsc.BrowserWebGLBridge.getOverlayRectMode());
            reportSpriteKeyPolicy(Boolean.parseBoolean(System.getProperty("spoiledmilk.browserSpriteKeyLimbs", "true")),orsc.BrowserWebGLBridge.isSpriteKeyLimbsEnabled());
        }
        System.setProperty("spoiledmilk.directFramebuffer", "true");
        System.setProperty("spoiledmilk.browserBorderlessDisplay", "true");
        System.setProperty("user.dir", "/files");
        Socket.setSocketImplFactory(new SocketImplFactory() {
            public SocketImpl createSocketImpl() { return new BrowserSocket(); }
        });
        report((webgl?"webgl-client":"software-client")+":start; networking=loopback-websocket");
        orsc.OpenRSC.main(new String[0]);
        final BrowserStartupDiagnostics startupDiagnostics=webgl&&!benchmark?new BrowserStartupDiagnostics(new BrowserStartupDiagnostics.Sink(){
            public void report(String phase,String detail){reportStartupDiagnostics(phase,detail);}
        }):null;
        if(startupDiagnostics!=null){startupDiagnostics.installChainedHandler();reportStartupDiagnostics("route",String.valueOf(privatePort));startupDiagnostics.sample("main-returned");}
        if(webgl) {
            Thread pointer = new Thread(new Runnable() {
                public void run() {
                    try {
                        while(true) {
                            int[] event=awaitMiddleMouseEvent(); // waits without polling while idle
                            if(event!=null&&event.length==4&&event[0]==-1)break;
                            orsc.BrowserMiddleMouseInput.dispatch(event);
                        }
                    } catch(Exception error) {
                        reportInputError("Middle-button relay stopped: "+error);
                    } finally {
                        try {orsc.BrowserMiddleMouseInput.dispatch(new int[]{0,0,0,0});}
                        catch(Exception cleanupError) {reportInputError("Middle-button cleanup failed: "+cleanupError);}
                    }
                }
            },"Browser middle-button input");
            pointer.setDaemon(true);pointer.start();
        }
        final boolean inputDiagnostics=Boolean.getBoolean("spoiledmilk.browserInputDiagnostics");
        Thread observer = new Thread(new Runnable() {
            public void run() {
                try {
                    java.lang.reflect.Field clientField = orsc.ORSCApplet.class.getDeclaredField("mudclient"); clientField.setAccessible(true);
                    java.lang.reflect.Field modeField = orsc.mudclient.class.getDeclaredField("currentViewMode"); modeField.setAccessible(true);
                    java.lang.reflect.Field fpsField = orsc.mudclient.class.getDeclaredField("FPS"); fpsField.setAccessible(true);
                    java.lang.reflect.Field loopField = orsc.mudclient.class.getDeclaredField("selectedClientLoopMode"); loopField.setAccessible(true);
                    int inputDiagnosticSamples=0;
                    double lastHandoffRevision=-1;
                    boolean modelPickReported=false,candidatePoliciesReported=false,optimizedDefaultsReported=false;
                    BenchmarkCandidatePolicies.RegionDiagnostics regionDiagnostics=new BenchmarkCandidatePolicies.RegionDiagnostics();
                    BenchmarkCandidatePolicies.TerrainQueryDiagnostics terrainDiagnostics=new BenchmarkCandidatePolicies.TerrainQueryDiagnostics();
                    while (true) {
                        if(startupDiagnostics!=null)startupDiagnostics.sample("observer");
                        orsc.BrowserDisplaySettings.update(syncDisplaySettings(orsc.BrowserDisplaySettings.takeRequest()));
                        Object client = clientField.get(null);
                        // Only an explicit repeatable fixture owns the camera. Benchmark
                        // optimizations also serve normal exploration and must not reset it.
                        if (client != null && fixedCamera != null) {
                            orsc.mudclient actual=(orsc.mudclient)client;
                            int[] camera=fixedCamera.observe(actual);
                            if(combatWorkload==null)reportRepeatableState(actual.getUsername(),camera);
                            else reportCombatState(actual.getUsername(),BrowserCombatWorkload.NAMES,combatWorkload.observe(actual,camera));
                        }
                        if (client != null) {
                            if(inputDiagnostics && inputDiagnosticSamples<64) {
                                inputDiagnosticSamples++;
                                orsc.mudclient actual=(orsc.mudclient)client;
                                java.lang.reflect.Field login=orsc.mudclient.class.getDeclaredField("loginScreenNumber");login.setAccessible(true);
                                report("input-state:"+login.getInt(client)+":"+actual.mouseX+":"+actual.mouseY+":"+actual.screenOffsetX+":"+actual.screenOffsetY+":"+actual.getGameWidth()+":"+actual.getGameHeight());
                            }
                            if(Boolean.getBoolean("spoiledmilk.browserProfile"))reportTelemetry(orsc.BrowserTelemetryData.names(),orsc.BrowserTelemetryData.snapshot());
                            // The client exists before its Scene during startup. Capture once so
                            // every optional Scene observation sees the same lifecycle state.
                            orsc.graphics.three.Scene observedScene=((orsc.mudclient)client).getScene();
                            reportLoop(String.valueOf(loopField.get(client)),observedScene!=null&&observedScene.isEarlyResidentFaceCullEnabled(),observedScene!=null&&observedScene.isBrowserFullResidentOwnershipEnabled());
                            if(webgl&&!benchmark){
                                if(!optimizedDefaultsReported&&observedScene!=null){
                                    String[] names={"batchedModelKernel","terrainQueryIndex"};
                                    for(int n=0;n<2;n++){
                                        boolean[] actual=BrowserOptimizedDefaults.actual(observedScene,optimizedDefaults,n);
                                        reportCandidatePolicy(names[n],optimizedDefaults.requested[n],actual[0],actual[1]);
                                    }
                                    optimizedDefaultsReported=true;
                                }
                                double[] normalTerrain=terrainDiagnostics.append(((orsc.mudclient)client).getWorld(),null);
                                if(normalTerrain.length==8)reportRegionPreparationDiagnostics(false,normalTerrain);
                            }
                            if(webgl && Boolean.getBoolean("spoiledmilk.browserBenchmark")) {
                                if(!modelPickReported && observedScene!=null) {
                                    reportModelPickBounds(Boolean.getBoolean("spoiledmilk.browserModelPickBounds"),
                                        observedScene.isBrowserModelPickBoundsEnabled());
                                    modelPickReported=true;
                                }
                                if(!candidatePoliciesReported&&observedScene!=null) {
                                    String[][] policies={{"geometryTransfer","spoiledmilk.browserGeometryTransferReuse","orsc.BrowserWebGLBridge","isGeometryTransferReuseEnabled"},
                                        {"wholeZone","spoiledmilk.browserWholeZone","orsc.BrowserWebGLBridge","isWholeZoneEnabled"},
                                        {"wholeRam","spoiledmilk.browserWholeRam","orsc.BrowserWebGLBridge","isWholeRamEnabled"},
                                        {"baselineReuse","spoiledmilk.browserBaselineReuse","orsc.PacketHandler","isBrowserBaselineReuseEnabled"},
                                        {"cloneTemplates","spoiledmilk.browserCloneTemplates","orsc.mudclient","isBrowserCloneTemplatesEnabled"},
                                        {"regionBuild","spoiledmilk.browserRegionBuild","orsc.graphics.three.BrowserRegionBuild","isEnabled"},
                                        {"regionLoadProfile","spoiledmilk.browserLoadProfile","orsc.BrowserWebGLBridge","isLoadProfileEnabled"},
                                        {"regionPreparation","spoiledmilk.browserRegionPreparation","orsc.graphics.three.World","isBrowserRegionPreparationEnabled"},
                                        {"descriptorWriting","spoiledmilk.browserResidentDescriptorWriting","orsc.mudclient","isBrowserResidentDescriptorWritingEnabled"},
                                        {"haloHeightLookup","spoiledmilk.browserHaloHeightLookup","orsc.graphics.three.World","isBrowserHaloHeightLookupEnabled"},
                                        {"chunkCoverage","spoiledmilk.browserPrimitiveChunkCoverage","orsc.NativeLayeredTerrainChunk","isBrowserPrimitiveChunkCoverageEnabled"},
                                        {"overlayDeltaTransport","spoiledmilk.browserOverlayDeltaTransport","orsc.BrowserWebGLBridge","isOverlayDeltaTransportEnabled"},
                                        {"terrainQueryIndex","spoiledmilk.browserTerrainQueryIndex","orsc.NativeLayeredTerrainSnapshot","isBrowserTerrainQueryIndexEnabled"}};
                                    for(String[] policy:policies) {
                                        boolean requested=Boolean.getBoolean(policy[1]);boolean[] actual=BenchmarkCandidatePolicies.inspect(policy[2],policy[3]);
                                        reportCandidatePolicy(policy[0],requested,actual[0],actual[1]);
                                        if(requested&&!actual[0])throw new IllegalStateException("Requested candidate is absent from this fixture JAR: "+policy[0]);
                                    }
                                    boolean projectionRequested=Boolean.getBoolean("spoiledmilk.browserSceneryProjectionWork");
                                    boolean[] projectionActual=BenchmarkCandidatePolicies.inspectInstance(observedScene,"isBrowserSceneryProjectionWorkEnabled");
                                    reportCandidatePolicy("sceneryProjection",projectionRequested,projectionActual[0],projectionActual[1]);
                                    if(projectionRequested&&!projectionActual[0])throw new IllegalStateException("Requested candidate is absent from this fixture JAR: sceneryProjection");
                                    boolean batchRequested=Boolean.getBoolean("spoiledmilk.browserBatchedModelKernel");
                                    boolean[] batchActual=BenchmarkCandidatePolicies.inspectInstance(observedScene,"isBrowserBatchedModelKernelEnabled");
                                    reportCandidatePolicy("batchedModelKernel",batchRequested,batchActual[0],batchActual[1]);
                                    if(batchRequested&&!batchActual[0])throw new IllegalStateException("Requested candidate is absent from this fixture JAR: batchedModelKernel");
                                    candidatePoliciesReported=true;
                                }
                                int[] batchWork=snapshotBatchedModelWork(observedScene,Boolean.getBoolean("spoiledmilk.browserBatchedModelKernel"));
                                if(batchWork!=null)reportBatchedModelWork(batchWork);
                                if(Boolean.getBoolean("spoiledmilk.browserWholeZone")){reportWholeZoneWork(snapshotWholeZoneWork());reportStaticSceneWork(snapshotStaticSceneWork());reportStaticIdentityWork(snapshotStaticIdentityWork(client));reportBaselineReuseWork(snapshotBaselineReuseWork(client));reportCloneTemplateWork(snapshotCloneTemplateWork(client));}
                                if(Boolean.getBoolean("spoiledmilk.browserWholeRam"))reportWholeRamWork(snapshotWholeRamWork());
                                if(Boolean.getBoolean("spoiledmilk.browserLoadProfile")){
                                    double[] trace=snapshotRegionHandoffTrace();
                                    if(trace[2]!=lastHandoffRevision){
                                        String phases=(String)Class.forName("orsc.BrowserWebGLBridge").getMethod("getRegionHandoffTracePhases").invoke(null);
                                        reportRegionHandoffTrace(phases,trace);lastHandoffRevision=trace[2];
                                    }
                                }
                                orsc.graphics.two.GraphicsController surface=((orsc.mudclient)client).getSurface();
                                double[] regionCounts=regionDiagnostics.snapshot(((orsc.mudclient)client).getWorld());
                                reportRegionPreparationDiagnostics(regionCounts!=null,terrainDiagnostics.append(((orsc.mudclient)client).getWorld(),regionCounts));
                                reportPreparation(orsc.graphics.two.GraphicsController.isBrowserFramePreparationEnabled(),
                                    surface!=null && surface.isBrowserFramePreparationActive(),
                                    Boolean.parseBoolean(System.getProperty("spoiledmilk.browserAnimatedBufferReuse","true")));
                            }
                            reportState(String.valueOf(modeField.get(client)), fpsField.getInt(null), orsc.ORSCApplet.globalLoadingPercent);
                        }
                        Thread.sleep(1000);
                    }
                } catch (Throwable error) { report("observer:stopped:" + error); }
            }
        }, "Private browser proof observer");
        observer.setDaemon(true); observer.start();
    }

    static double[] snapshotRegionHandoffTrace() throws ReflectiveOperationException {
        Object work=Class.forName("orsc.BrowserWebGLBridge").getMethod("getRegionHandoffTrace").invoke(null);
        if(!(work instanceof double[]))throw new IllegalStateException("Invalid region-handoff trace getter");
        double[] v=(double[])work;
        if(v.length<8||v[0]!=1||v[1]!=1||v[4]<0||v[4]>512||v[5]!=12||v.length!=8+(int)v[4]*12)
            throw new IllegalStateException("Invalid bounded region-handoff trace schema");
        return v.clone();
    }

    /** No receipt until Scene exists; a missing/invalid getter after startup stays fatal. */
    static int[] snapshotWholeRamWork() throws ReflectiveOperationException {
        Object work=Class.forName("orsc.BrowserWebGLBridge").getMethod("getWholeRamWork").invoke(null);
        if(!(work instanceof int[])||((int[])work).length!=0&&((int[])work).length!=18)throw new IllegalStateException("Invalid whole-RAM work getter");return ((int[])work).clone();
    }

    static double[] snapshotCloneTemplateWork(Object client)throws ReflectiveOperationException{
        Object raw;
        try{raw=client.getClass().getMethod("getBrowserCloneTemplateWork").invoke(client);}
        catch(NoSuchMethodException missing){if(!Boolean.getBoolean("spoiledmilk.browserCloneTemplates"))return new double[0];throw missing;}
        if(!(raw instanceof long[]))throw new IllegalStateException("Invalid clone-template work getter");
        long[] v=(long[])raw;if(v.length!=0&&v.length!=18)throw new IllegalStateException("Invalid clone-template counters");
        double[] result=new double[v.length];for(int i=0;i<v.length;i++){if(v[i]<0||v[i]>9007199254740991L)throw new IllegalStateException("Clone-template counter bounds");result[i]=v[i];}return result;
    }

    static double[] snapshotBaselineReuseWork(Object client) throws ReflectiveOperationException {
        Object packet=client.getClass().getField("packetHandler").get(client);
        if(packet==null)return new double[0];
        Object work;
        try { work=packet.getClass().getMethod("getBrowserBaselineReuseWork").invoke(packet); }
        catch(NoSuchMethodException missing) { if(!Boolean.getBoolean("spoiledmilk.browserBaselineReuse"))return new double[0];throw missing; }
        if(!(work instanceof long[])||((long[])work).length!=10)throw new IllegalStateException("Invalid baseline reuse work getter");
        long[] values=(long[])work;double[] result=new double[10];
        for(int i=0;i<10;i++){if(values[i]<0||values[i]>9007199254740991L)throw new IllegalStateException("Invalid baseline reuse counter");result[i]=values[i];}
        return result;
    }

    static int[] snapshotStaticIdentityWork(Object client) throws ReflectiveOperationException {
        Object work;
        try{work=client.getClass().getMethod("getBrowserStaticIdentityWork").invoke(client);}
        catch(NoSuchMethodException missing){if(!Boolean.getBoolean("spoiledmilk.browserStaticSceneReuse"))return new int[0];throw missing;}
        if(!(work instanceof int[])||((int[])work).length!=8||((int[])work)[0]!=1)throw new IllegalStateException("Invalid static-identity work getter");
        return ((int[])work).clone();
    }

    static int[] snapshotStaticSceneWork() throws ReflectiveOperationException {
        Object work;
        try{work=Class.forName("orsc.BrowserWebGLBridge").getMethod("getStaticSceneReuseWork").invoke(null);}
        catch(NoSuchMethodException missing){if(!Boolean.getBoolean("spoiledmilk.browserStaticSceneReuse"))return new int[0];throw missing;}
        if(!(work instanceof int[])||((int[])work).length!=0&&(((int[])work).length!=10||((int[])work)[0]!=1))throw new IllegalStateException("Invalid static-scene work getter");
        return ((int[])work).clone();
    }

    static int[] snapshotWholeZoneWork() throws ReflectiveOperationException {
        Object work=Class.forName("orsc.BrowserWebGLBridge").getMethod("getWholeZoneWork").invoke(null);
        if(!(work instanceof int[])||((int[])work).length!=0&&((int[])work).length!=9)throw new IllegalStateException("Invalid whole-zone work getter");
        return ((int[])work).clone();
    }

    static int[] snapshotBatchedModelWork(Object scene,boolean requested) throws ReflectiveOperationException {
        if(!requested || scene==null)return null;
        Object work=scene.getClass().getMethod("getBrowserBatchedModelKernelWork").invoke(scene);
        if(!(work instanceof int[])||((int[])work).length!=14)throw new IllegalStateException("Invalid batched model work getter");
        return (int[])work;
    }

    static int privateFixturePort(String fixture) {
        return "renderer-combat-v1".equals(fixture)?43639:"repeatable-v1".equals(fixture)?43649:43629;
    }

    static int privateFixturePort(String fixture,String stagedPort) {
        int expected=privateFixturePort(fixture);
        if(stagedPort==null)return expected;
        if(!"43639".equals(stagedPort)&&!"43649".equals(stagedPort))throw new IllegalArgumentException("Invalid staged private loopback port");
        int port=Integer.parseInt(stagedPort);
        if(fixture!=null&&(!"renderer-combat-v1".equals(fixture)&&!"repeatable-v1".equals(fixture)||expected!=port))throw new IllegalArgumentException("Staged private loopback port differs from fixture");
        return port;
    }
    static void validatePrivateConnection(InetAddress host,int port,String fixture,String stagedPort)throws IOException {
        final int expected;try{expected=privateFixturePort(fixture,stagedPort);}catch(IllegalArgumentException invalid){throw new IOException(invalid);}
        if(!host.isLoopbackAddress()||port!=expected)throw new IOException("Private prototype permits only the selected loopback fixture port");
    }
    private static final class BrowserSocket extends SocketImpl {
        private int connection = -1;
        protected void create(boolean stream) throws IOException {
            if (!stream) throw new IOException("Only TCP-style game streams are supported");
        }
        protected void connect(String host, int port) throws IOException { connect(InetAddress.getByName(host), port); }
        protected void connect(InetAddress host, int port) throws IOException {
            validatePrivateConnection(host,port,System.getProperty("spoiledmilk.browserRepeatableFixture"),System.getProperty("spoiledmilk.browserPrivateLoopbackPort"));
            address = host; this.port = port; connection = wsOpen();
            if (connection < 0) throw new IOException("Private WebSocket connection failed");
        }
        protected void connect(SocketAddress target, int timeout) throws IOException {
            InetSocketAddress address = (InetSocketAddress) target; connect(address.getAddress(), address.getPort());
        }
        protected InputStream getInputStream() { return new InputStream() {
            public int available() { return wsAvailable(connection); }
            public int read() throws IOException {
                byte[] value = wsRead(connection, 1); return value.length == 0 ? -1 : value[0] & 255;
            }
            public int read(byte[] target, int offset, int length) throws IOException {
                if (length == 0) return 0;
                byte[] value = wsRead(connection, length);
                if (value.length == 0) return -1;
                System.arraycopy(value, 0, target, offset, value.length); return value.length;
            }
            public void close() { BrowserSocket.this.close(); }
        }; }
        protected OutputStream getOutputStream() { return new OutputStream() {
            public void write(int value) { wsWrite(connection, new byte[]{(byte)value}); }
            public void write(byte[] bytes, int offset, int length) {
                byte[] copy = new byte[length]; System.arraycopy(bytes, offset, copy, 0, length); wsWrite(connection, copy);
            }
            public void close() { BrowserSocket.this.close(); }
        }; }
        protected int available() { return wsAvailable(connection); }
        protected void close() { if (connection >= 0) { wsClose(connection); connection = -1; } }
        protected void bind(InetAddress host, int port) throws IOException { throw new IOException("No listening sockets"); }
        protected void listen(int backlog) throws IOException { throw new IOException("No listening sockets"); }
        protected void accept(SocketImpl socket) throws IOException { throw new IOException("No listening sockets"); }
        protected void sendUrgentData(int value) throws IOException { throw new IOException("No urgent data"); }
        public void setOption(int option, Object value) { }
        public Object getOption(int option) { return option == SocketOptions.SO_TIMEOUT ? Integer.valueOf(30000) : Boolean.FALSE; }
    }
}
