package orsc.browser;

import java.io.*;
import java.net.*;

/** Private browser spike entry point. Not part of the desktop/client build. */
public final class BrowserBootstrap {
    private BrowserBootstrap() { }
    public static native void report(String message);
    public static native void reportState(String mode, int fps, int loadingPercent);
    public static native void reportLoop(String mode,boolean earlyFaceCull,boolean fullResidentOwnership);
    public static native void reportTelemetry(String names,double[] values);
    public static native void reportSpriteKeyPolicy(boolean requested,boolean enabled);
    public static native void reportPreparation(boolean enabled,boolean active,boolean animated);
    public static native void reportRegionPreparationDiagnostics(boolean available,double[] counters);
    public static native void reportCandidatePolicy(String name,boolean requested,boolean available,boolean enabled);
    public static native void reportBatchedModelWork(int[] counters);
    public static native void reportRepeatableState(String username,int[] counters);
    public static native void reportCombatState(String username,String names,int[] counters);
    public static native void reportModelPickBounds(boolean requested,boolean enabled);
    private static native int wsOpen();
    private static native byte[] wsRead(int socket, int maximum);
    private static native int wsAvailable(int socket);
    private static native void wsWrite(int socket, byte[] bytes);
    private static native void wsClose(int socket);
    private static native int[] awaitMiddleMouseEvent();
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
        final String fixture=System.getProperty("spoiledmilk.browserRepeatableFixture");
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
            orsc.BrowserWebGLBridge.install();
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
                    boolean modelPickReported=false,candidatePoliciesReported=false;
                    BenchmarkCandidatePolicies.RegionDiagnostics regionDiagnostics=new BenchmarkCandidatePolicies.RegionDiagnostics();
                    while (true) {
                        Object client = clientField.get(null);
                        if (client != null && webgl && Boolean.getBoolean("spoiledmilk.browserBenchmark")) {
                            orsc.mudclient actual=(orsc.mudclient)client;
                            if(fixedCamera==null){actual.setCameraZoomSetting(75);actual.cameraRotation=128;}
                            else {
                                int[] camera=fixedCamera.observe(actual);
                                if(combatWorkload==null)reportRepeatableState(actual.getUsername(),camera);
                                else reportCombatState(actual.getUsername(),BrowserCombatWorkload.NAMES,combatWorkload.observe(actual,camera));
                            }
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
                            if(webgl && Boolean.getBoolean("spoiledmilk.browserBenchmark")) {
                                if(!modelPickReported && observedScene!=null) {
                                    reportModelPickBounds(Boolean.getBoolean("spoiledmilk.browserModelPickBounds"),
                                        observedScene.isBrowserModelPickBoundsEnabled());
                                    modelPickReported=true;
                                }
                                if(!candidatePoliciesReported&&observedScene!=null) {
                                    String[][] policies={{"geometryTransfer","spoiledmilk.browserGeometryTransferReuse","orsc.BrowserWebGLBridge","isGeometryTransferReuseEnabled"},
                                        {"regionPreparation","spoiledmilk.browserRegionPreparation","orsc.graphics.three.World","isBrowserRegionPreparationEnabled"},
                                        {"descriptorWriting","spoiledmilk.browserResidentDescriptorWriting","orsc.mudclient","isBrowserResidentDescriptorWritingEnabled"},
                                        {"haloHeightLookup","spoiledmilk.browserHaloHeightLookup","orsc.graphics.three.World","isBrowserHaloHeightLookupEnabled"},
                                        {"chunkCoverage","spoiledmilk.browserPrimitiveChunkCoverage","orsc.NativeLayeredTerrainChunk","isBrowserPrimitiveChunkCoverageEnabled"},
                                        {"overlayDeltaTransport","spoiledmilk.browserOverlayDeltaTransport","orsc.BrowserWebGLBridge","isOverlayDeltaTransportEnabled"}};
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
                                orsc.graphics.two.GraphicsController surface=((orsc.mudclient)client).getSurface();
                                double[] regionCounts=regionDiagnostics.snapshot(((orsc.mudclient)client).getWorld());
                                reportRegionPreparationDiagnostics(regionCounts!=null,regionCounts==null?new double[0]:regionCounts);
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

    /** No receipt until Scene exists; a missing/invalid getter after startup stays fatal. */
    static int[] snapshotBatchedModelWork(Object scene,boolean requested) throws ReflectiveOperationException {
        if(!requested || scene==null)return null;
        Object work=scene.getClass().getMethod("getBrowserBatchedModelKernelWork").invoke(scene);
        if(!(work instanceof int[])||((int[])work).length!=14)throw new IllegalStateException("Invalid batched model work getter");
        return (int[])work;
    }

    static int privateFixturePort(String fixture) {
        return "renderer-combat-v1".equals(fixture)?43639:"repeatable-v1".equals(fixture)?43649:43629;
    }

    private static final class BrowserSocket extends SocketImpl {
        private int connection = -1;
        protected void create(boolean stream) throws IOException {
            if (!stream) throw new IOException("Only TCP-style game streams are supported");
        }
        protected void connect(String host, int port) throws IOException { connect(InetAddress.getByName(host), port); }
        protected void connect(InetAddress host, int port) throws IOException {
            if (!host.isLoopbackAddress() || port != privateFixturePort(System.getProperty("spoiledmilk.browserRepeatableFixture"))) throw new IOException("Private prototype permits only the selected loopback fixture port");
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
