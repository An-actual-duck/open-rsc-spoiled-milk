package orsc.browser;

import java.io.*;
import java.net.*;

/** Private browser spike entry point. Not part of the desktop/client build. */
public final class BrowserBootstrap {
    private BrowserBootstrap() { }
    public static native void report(String message);
    public static native void reportState(String mode, int fps, int loadingPercent);
    public static native void reportLoop(String mode);
    public static native void reportTelemetry(String names,double[] values);
    private static native int wsOpen();
    private static native byte[] wsRead(int socket, int maximum);
    private static native int wsAvailable(int socket);
    private static native void wsWrite(int socket, byte[] bytes);
    private static native void wsClose(int socket);

    public static void main(String[] args) throws Exception {
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
            String[] on={"renderer3DGeometryCapture","openglWorldMesh","openglWorldMeshTexturedVisible","openglWorldMeshTexturedStaticVisible","openglWorldReplacementComposite","openglWorldChunksTexturedVisible","openglWorldChunksReplacementComposite","openglWorldChunksTrustedReplacement","openglWorldChunksResidentObjects","openglWorldSpritesVisible","skipLegacyWorldRaster","modernClientLoop"};
            for(String name:on)System.setProperty("spoiledmilk."+name,"true");
            System.setProperty("spoiledmilk.renderer3DVisibleWorld","false");
            // No native presenter or GLFW initialization; only the browser sink owns GPU submission.
            orsc.BrowserWebGLBridge.install();
        }
        System.setProperty("spoiledmilk.directFramebuffer", "true");
        System.setProperty("user.dir", "/files");
        Socket.setSocketImplFactory(new SocketImplFactory() {
            public SocketImpl createSocketImpl() { return new BrowserSocket(); }
        });
        report((webgl?"webgl-client":"software-client")+":start; networking=loopback-websocket");
        orsc.OpenRSC.main(new String[0]);
        Thread observer = new Thread(new Runnable() {
            public void run() {
                try {
                    java.lang.reflect.Field clientField = orsc.ORSCApplet.class.getDeclaredField("mudclient"); clientField.setAccessible(true);
                    java.lang.reflect.Field modeField = orsc.mudclient.class.getDeclaredField("currentViewMode"); modeField.setAccessible(true);
                    java.lang.reflect.Field fpsField = orsc.mudclient.class.getDeclaredField("FPS"); fpsField.setAccessible(true);
                    java.lang.reflect.Field loopField = orsc.mudclient.class.getDeclaredField("selectedClientLoopMode"); loopField.setAccessible(true);
                    while (true) {
                        Object client = clientField.get(null);
                        if (client != null && webgl && Boolean.getBoolean("spoiledmilk.browserBenchmark")) {
                            orsc.mudclient actual=(orsc.mudclient)client;actual.setCameraZoomSetting(75);actual.cameraRotation=128;
                        }
                        if (client != null) {
                            if(Boolean.getBoolean("spoiledmilk.browserProfile"))reportTelemetry(orsc.BrowserTelemetryData.names(),orsc.BrowserTelemetryData.snapshot());
                            reportLoop(String.valueOf(loopField.get(client)));
                            reportState(String.valueOf(modeField.get(client)), fpsField.getInt(null), orsc.ORSCApplet.globalLoadingPercent);
                        }
                        Thread.sleep(1000);
                    }
                } catch (Throwable error) { report("observer:stopped:" + error); }
            }
        }, "Private browser proof observer");
        observer.setDaemon(true); observer.start();
    }

    private static final class BrowserSocket extends SocketImpl {
        private int connection = -1;
        protected void create(boolean stream) throws IOException {
            if (!stream) throw new IOException("Only TCP-style game streams are supported");
        }
        protected void connect(String host, int port) throws IOException { connect(InetAddress.getByName(host), port); }
        protected void connect(InetAddress host, int port) throws IOException {
            if (!host.isLoopbackAddress() || port != 43629) throw new IOException("Private prototype permits only 127.0.0.1:43629");
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
