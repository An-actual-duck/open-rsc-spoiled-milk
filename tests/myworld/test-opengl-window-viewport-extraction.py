#!/usr/bin/env python3
"""Exercise the B07 OpenGL window and viewport ownership boundaries."""

from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap


ROOT = Path(__file__).resolve().parents[2]
PRESENTER = ROOT / "PC_Client/src/orsc/OpenGLFramePresenter.java"
VIEWPORT = ROOT / "PC_Client/src/orsc/OpenGLViewportPresenter.java"
WINDOW = ROOT / "PC_Client/src/orsc/OpenGLWindowController.java"
PRESENTATION_SETTINGS = ROOT / "Client_Base/src/orsc/OpenGLPresentationSettings.java"
WINDOW_SETTINGS = ROOT / "Client_Base/src/orsc/OpenGLWindowSettings.java"
MONITOR_MODE = ROOT / "PC_Client/src/orsc/MonitorMode.java"
RENDERER_3D_SETTINGS = ROOT / "Client_Base/src/orsc/graphics/three/Renderer3DSettings.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def verify_source_ownership() -> None:
    presenter = PRESENTER.read_text(encoding="utf-8")
    viewport = VIEWPORT.read_text(encoding="utf-8")
    window = WINDOW.read_text(encoding="utf-8")

    require("OpenGLWindowController windowController" in presenter, "presenter window delegate missing")
    require("OpenGLViewportPresenter viewportPresenter" in presenter, "presenter viewport delegate missing")
    for moved_symbol in (
        "gl.glfwInit()",
        "gl.glfwCreateWindow(",
        "gl.glfwDestroyWindow(",
        "gl.glfwTerminate()",
        "private void syncWindowMode(",
        "private void captureWindowedBounds(",
        "private static Viewport computeViewport(",
        "private static float computeTextSmoothingAlpha(",
    ):
        require(moved_symbol not in presenter, f"presenter still owns {moved_symbol}")

    require("SurfaceSize prepareFrame" in window, "window/frame sizing boundary missing")
    require("void shutdown(boolean presenterClosed)" in window, "window shutdown boundary missing")
    require("OpenGL window cleanup failure during" in window, "native cleanup diagnostics missing")
    require("OpenGL presenter cleanup failure during" in presenter, "resource cleanup diagnostics missing")
    readiness_enabled = "Renderer3DSettings.setOpenGLPresentationAvailable(true);"
    require(readiness_enabled in presenter, "OpenGL replacement readiness enable missing")
    require(
        presenter.index("initializeOpenGLResources();")
        < presenter.index(readiness_enabled)
        < presenter.index('log("OpenGL presenter active.");'),
        "OpenGL replacement readiness must follow successful initialization",
    )
    cleanup = presenter[presenter.index("private void cleanup()") : presenter.index("private static void logCleanupFailure")]
    disable = presenter[presenter.index("private void disable(") : presenter.index("@Override\n\tpublic void close()")]
    readiness_disabled = "Renderer3DSettings.setOpenGLPresentationAvailable(false);"
    require(readiness_disabled in cleanup, "cleanup must restore software replacement ownership")
    require(readiness_disabled in disable, "disable must restore software replacement ownership")
    require("static Viewport computeViewport" in viewport, "viewport layout owner missing")
    require("int mapMouseX(double cursorX)" in viewport, "viewport mouse mapping owner missing")

    render_frame = presenter[presenter.index("private void renderFrame(") : presenter.index("private OpenGLFrameCapture beginFrameCapture")]
    ordered_passes = (
        "clearFrameBackground(frame, framebufferViewport)",
        "drawTexturedQuad()",
        "drawWorldMesh(frame, worldReplacementComposite)",
        "drawWorldSprites(frame)",
        "drawSpriteOverlay(frame, worldReplacementComposite)",
        "drawRendererDebugOverlay(frame)",
        'captureLayer(frameCapture, "06-debug-overlay")',
        "finishFrameCapture(frameCapture, frame, worldReplacementComposite)",
        "windowController.swapBuffers()",
    )
    positions = [render_frame.index(symbol) for symbol in ordered_passes]
    require(positions == sorted(positions), "frame pass or capture ordering changed")


def run_java_fixture(name: str, sources: dict[str, str], project_sources: list[Path]) -> str:
    javac = shutil.which("javac")
    java = shutil.which("java")
    require(javac is not None and java is not None, "Java compiler/runtime are required")
    with tempfile.TemporaryDirectory(prefix=f"{name}-") as directory:
        temp = Path(directory)
        source_paths = []
        for filename, source in sources.items():
            path = temp / filename
            path.write_text(textwrap.dedent(source), encoding="utf-8")
            source_paths.append(path)
        result = subprocess.run(
            [
                javac,
                "-source",
                "8",
                "-target",
                "8",
                "-d",
                str(temp),
                *[str(path) for path in project_sources],
                *[str(path) for path in source_paths],
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        require(result.returncode == 0, f"{name} fixture compile failed:\n{result.stderr}")
        result = subprocess.run(
            [java, "-cp", str(temp), f"orsc.{name}"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        require(result.returncode == 0, f"{name} fixture failed:\n{result.stderr}")
        return result.stdout.strip()


def verify_viewport_behavior() -> None:
    fixture = r"""
        package orsc;

        public final class ViewportFixture {
            private static void require(boolean condition, String message) {
                if (!condition) throw new AssertionError(message);
            }

            private static void viewport(
                    OpenGLViewportPresenter.Viewport actual,
                    int x, int y, int width, int height,
                    String message) {
                require(actual.x == x && actual.y == y
                        && actual.width == width && actual.height == height,
                    message + ": " + actual.x + "," + actual.y + " "
                        + actual.width + "x" + actual.height);
            }

            public static void main(String[] args) {
                viewport(OpenGLViewportPresenter.computeViewport(
                        OpenGLPresentationSettings.ScaleMode.ASPECT_FIT,
                        1920, 1080, 800, 600),
                    240, 0, 1440, 1080, "4:3 aspect fit");
                viewport(OpenGLViewportPresenter.computeViewport(
                        OpenGLPresentationSettings.ScaleMode.ASPECT_FIT,
                        1920, 1080, 960, 540),
                    0, 0, 1920, 1080, "16:9 aspect fit");
                viewport(OpenGLViewportPresenter.computeViewport(
                        OpenGLPresentationSettings.ScaleMode.INTEGER_FIT,
                        1000, 700, 320, 200),
                    20, 50, 960, 600, "integer fit");
                viewport(OpenGLViewportPresenter.computeViewport(
                        OpenGLPresentationSettings.ScaleMode.STRETCH,
                        1000, 700, 800, 600),
                    0, 0, 1000, 700, "debug stretch");

                OpenGLPresentationSettings.setScaleMode(
                    OpenGLPresentationSettings.ScaleMode.STRETCH);
                OpenGLViewportPresenter primary =
                    new OpenGLViewportPresenter(true, 800, 600);
                primary.update(3840, 2160, 1920, 1080, 800, 600);
                viewport(primary.drawViewport(), 240, 0, 1440, 1080,
                    "primary window forces aspect fit");
                viewport(primary.framebufferViewport(), 480, 0, 2880, 2160,
                    "HiDPI framebuffer viewport");
                require(primary.mapMouseX(240.0) == 0, "left bar mapping");
                require(primary.mapMouseX(960.0) == 400, "center x mapping");
                require(primary.mapMouseX(1800.0) == 799, "right clamp mapping");
                require(primary.mapMouseY(540.0) == 300, "center y mapping");
                require(primary.mapMouseY(-20.0) == 0, "top clamp mapping");
                require(primary.textSmoothingAlpha() > 0.0f,
                    "fractional HiDPI scale should smooth text");

                OpenGLViewportPresenter mirror =
                    new OpenGLViewportPresenter(false, 800, 600);
                mirror.update(1600, 1200, 1600, 1200, 800, 600);
                viewport(mirror.drawViewport(), 0, 0, 1600, 1200,
                    "mirror honors stretch");
                require(mirror.textSmoothingAlpha() == 0.0f,
                    "exact integer scale should stay crisp");
                System.out.println("viewport-ok");
            }
        }
    """
    output = run_java_fixture(
        "ViewportFixture",
        {"ViewportFixture.java": fixture},
        [PRESENTATION_SETTINGS, VIEWPORT],
    )
    require(output == "viewport-ok", "viewport fixture did not complete")


def verify_renderer_replacement_readiness() -> None:
    world_editor_settings = r"""
        package orsc;

        public final class WorldEditorBuildSettings {
            private WorldEditorBuildSettings() {}
            public static boolean isEnabled() { return false; }
        }
    """
    fixture = r"""
        package orsc;

        import orsc.graphics.three.Renderer3DSettings;

        public final class RendererReadinessFixture {
            private static void require(boolean condition, String message) {
                if (!condition) throw new AssertionError(message);
            }

            private static void configureReplacementFeatures() {
                System.setProperty("spoiledmilk.openglWorldMesh", "true");
                System.setProperty("spoiledmilk.openglWorldMeshTexturedVisible", "true");
                System.setProperty("spoiledmilk.openglWorldMeshTexturedStaticVisible", "true");
                System.setProperty("spoiledmilk.openglWorldReplacementComposite", "true");
                System.setProperty("spoiledmilk.skipLegacyWorldRaster", "true");
                System.setProperty("spoiledmilk.openglWorldChunksTexturedVisible", "true");
                System.setProperty("spoiledmilk.openglWorldChunksReplacementComposite", "true");
                System.setProperty("spoiledmilk.openglWorldChunksTrustedReplacement", "true");
                System.setProperty("spoiledmilk.openglWorldChunksResidentObjects", "true");
            }

            private static void requireSoftwareOwnership(String phase) {
                require(!Renderer3DSettings.canSkipLegacyWorldRaster(),
                    phase + " must preserve legacy world raster");
                require(!Renderer3DSettings.canSkipProjectedWorldCapture(),
                    phase + " must preserve projected world capture");
                require(!Renderer3DSettings.canUseResidentObjectChunks(),
                    phase + " must preserve software object ownership");
            }

            public static void main(String[] args) {
                configureReplacementFeatures();
                requireSoftwareOwnership("configured but unavailable OpenGL");

                Renderer3DSettings.setOpenGLPresentationAvailable(true);
                require(Renderer3DSettings.canSkipLegacyWorldRaster(),
                    "ready OpenGL may replace legacy world raster");
                require(Renderer3DSettings.canSkipProjectedWorldCapture(),
                    "ready OpenGL may own trusted projected world capture");
                require(Renderer3DSettings.canUseResidentObjectChunks(),
                    "ready OpenGL may own resident objects");

                Renderer3DSettings.setOpenGLPresentationAvailable(false);
                requireSoftwareOwnership("OpenGL cleanup or failure");
                System.out.println("readiness-ok");
            }
        }
    """
    output = run_java_fixture(
        "RendererReadinessFixture",
        {
            "WorldEditorBuildSettings.java": world_editor_settings,
            "RendererReadinessFixture.java": fixture,
        },
        [RENDERER_3D_SETTINGS],
    )
    require(output == "readiness-ok", "renderer readiness fixture did not complete")


def verify_window_behavior() -> None:
    bindings = r"""
        package orsc;

        final class LwjglBindings {
            final int GLFW_FALSE = 0;
            final int GLFW_TRUE = 1;
            final int GLFW_VISIBLE = 2;
            final int GLFW_FOCUSED = 5;
            final int GLFW_ICONIFIED = 6;
            int windowAttribQueries;
            int visible = GLFW_TRUE;
            int iconified = GLFW_FALSE;
            final int GLFW_RESIZABLE = 3;
            final int GLFW_DECORATED = 4;
            final int GLFW_DONT_CARE = -1;
            long monitor;
            int refreshRate;
            int monitorChanges;
            int monitorRefreshRate = 75;
            boolean monitorAvailable = true;
            int x;
            int y;
            int width;
            int height;
            int decorated = GLFW_TRUE;
            int framebufferScale = 2;
            int primaryMonitorQueries;
            int destroyCalls;
            int terminateCalls;
            int pollCalls;
            int swapCalls;
            boolean destroyFailure;
            boolean terminateFailure;

            boolean glfwInit() { return true; }
            void glfwTerminate() throws Exception {
                terminateCalls++;
                if (terminateFailure) throw new Exception("terminate marker");
            }
            void glfwDefaultWindowHints() {}
            void glfwWindowHint(int hint, int value) {}
            long glfwCreateWindow(int width, int height, String title, long monitor, long share) {
                this.width = width;
                this.height = height;
                return 77L;
            }
            void glfwMakeContextCurrent(long window) {}
            void createCapabilities() {}
            void glfwSwapInterval(int interval) {}
            int glfwGetWindowAttrib(long window, int attribute) {
                windowAttribQueries++;
                return attribute == GLFW_VISIBLE ? visible : iconified;
            }
            void glfwShowWindow(long window) {}
            void glfwHideWindow(long window) {}
            boolean glfwWindowShouldClose(long window) { return false; }
            void glfwPollEvents() { pollCalls++; }
            void glfwSwapBuffers(long window) { swapCalls++; }
            void glfwDestroyWindow(long window) throws Exception {
                destroyCalls++;
                if (destroyFailure) throw new Exception("destroy marker");
            }
            void glfwSetWindowSize(long window, int width, int height) {
                this.width = width;
                this.height = height;
            }
            void glfwGetWindowPos(long window, int[] x, int[] y) {
                x[0] = this.x;
                y[0] = this.y;
            }
            void glfwSetWindowPos(long window, int x, int y) {
                this.x = x;
                this.y = y;
            }
            void glfwSetWindowAttrib(long window, int attribute, int value) {
                if (attribute == GLFW_DECORATED) decorated = value;
            }
            void glfwSetWindowMonitor(long window, long monitor, int x, int y,
                    int width, int height, int refreshRate) {
                this.monitor = monitor;
                this.refreshRate = refreshRate;
                this.x = monitor == 0L ? x : 100;
                this.y = monitor == 0L ? y : 50;
                this.width = width;
                this.height = height;
                monitorChanges++;
            }
            MonitorMode getPrimaryMonitorMode() {
                primaryMonitorQueries++;
                return monitorAvailable
                    ? new MonitorMode(99L, 100, 50, 1920, 1080, monitorRefreshRate)
                    : new MonitorMode(100, 50, 1920, 1080);
            }
            void glfwGetWindowSize(long window, int[] width, int[] height) {
                width[0] = this.width;
                height[0] = this.height;
            }
            void glfwGetFramebufferSize(long window, int[] width, int[] height) {
                width[0] = this.width * framebufferScale;
                height[0] = this.height * framebufferScale;
            }
        }
    """
    fixture = r"""
        package orsc;

        public final class WindowFixture {
            private static final class Delegate implements OpenGLWindowController.Delegate {
                int contexts;
                int releases;
                int suppressions;
                int saves;
                int closes;
                final StringBuilder logs = new StringBuilder();

                public void contextCreated() { contexts++; }
                public void releaseInputState() { releases++; }
                public void suppressKeysUntilRelease() { suppressions++; }
                public void saveWindowSettings() { saves++; }
                public void closeClient() { closes++; }
                public void log(String message) { logs.append(message).append('\n'); }
            }

            private static void require(boolean condition, String message) {
                if (!condition) throw new AssertionError(message);
            }

            public static void main(String[] args) throws Exception {
                OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.WINDOWED);
                OpenGLWindowSettings.setWindowedBounds(120, 130, 800, 600);
                LwjglBindings gl = new LwjglBindings();
                Delegate delegate = new Delegate();
                OpenGLWindowController controller =
                    new OpenGLWindowController(gl, "fixture", true, delegate);
                require(controller.initializeGlfw(), "GLFW init");
                controller.createWindow(512, 346, false);
                require(gl.x == 120 && gl.y == 130 && gl.width == 800 && gl.height == 600,
                    "saved windowed bounds restore");
                require(gl.decorated == gl.GLFW_TRUE, "windowed decorations");
                require(delegate.contexts == 1 && delegate.releases == 1
                        && delegate.suppressions == 1,
                    "initial window lifecycle callbacks");

                OpenGLWindowController.SurfaceSize surface = controller.prepareFrame(960, 540);
                require(surface.windowWidth == 800 && surface.windowHeight == 600,
                    "primary window keeps user size");
                require(controller.diagnosticWindowFlags() == -1 && gl.windowAttribQueries == 0,
                    "normal client avoids diagnostic attribute queries");
                RendererDiagnosticSession.enabled = true;
                controller.prepareFrame(960, 540);
                require(controller.diagnosticWindowFlags() == 1, "visible diagnostic snapshot");
                gl.iconified = gl.GLFW_TRUE;
                controller.prepareFrame(960, 540);
                require(controller.diagnosticWindowFlags() == 3, "iconified diagnostic snapshot");
                RendererDiagnosticSession.enabled = false;
                require(surface.framebufferWidth == 1600 && surface.framebufferHeight == 1200,
                    "framebuffer size query");

                gl.x = 140;
                gl.y = 150;
                gl.width = 900;
                gl.height = 700;
                OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.BORDERLESS_FULLSCREEN);
                surface = controller.prepareFrame(960, 540);
                require(gl.x == 100 && gl.y == 50 && gl.width == 1920 && gl.height == 1080,
                    "primary monitor borderless bounds");
                require(gl.decorated == gl.GLFW_FALSE, "borderless decorations");
                require(delegate.saves == 1, "moved window bounds persisted before borderless");
                require(gl.primaryMonitorQueries > 0, "primary monitor selection queried");

                OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.WINDOWED);
                controller.prepareFrame(960, 540);
                require(gl.x == 140 && gl.y == 150 && gl.width == 900 && gl.height == 700,
                    "windowed bounds restored after borderless");
                require(gl.decorated == gl.GLFW_TRUE, "restored decorations");

                OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.FULLSCREEN);
                surface = controller.prepareFrame(960, 540);
                require(gl.monitor == 99L && gl.refreshRate == 75,
                    "fullscreen attaches the primary monitor at its desktop refresh rate");
                require(surface.windowWidth == 1920 && surface.windowHeight == 1080
                        && surface.framebufferWidth == 3840 && surface.framebufferHeight == 2160,
                    "fullscreen reports monitor bounds and HiDPI framebuffer");
                require(gl.decorated == gl.GLFW_FALSE, "fullscreen has no decorations");
                OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.WINDOWED);
                controller.prepareFrame(960, 540);
                require(gl.monitor == 0L && gl.x == 140 && gl.y == 150
                        && gl.width == 900 && gl.height == 700
                        && gl.decorated == gl.GLFW_TRUE,
                    "leaving fullscreen detaches the monitor and restores windowed bounds");
                OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.BORDERLESS_FULLSCREEN);
                controller.prepareFrame(960, 540);
                OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.FULLSCREEN);
                controller.prepareFrame(960, 540);
                require(gl.monitor == 99L, "borderless to fullscreen attaches monitor");
                OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.BORDERLESS_FULLSCREEN);
                controller.prepareFrame(960, 540);
                require(gl.monitor == 0L && gl.decorated == gl.GLFW_FALSE
                        && gl.width == 1920 && gl.height == 1080,
                    "fullscreen to borderless detaches monitor without decorations");
                OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.WINDOWED);
                controller.prepareFrame(960, 540);
                require(gl.x == 140 && gl.y == 150 && gl.width == 900 && gl.height == 700,
                    "three-mode round trip preserves original window bounds");
                require(delegate.contexts == 1,
                    "mode transitions retain the same OpenGL context");
                gl.monitorRefreshRate = 0;
                OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.FULLSCREEN);
                controller.prepareFrame(960, 540);
                require(gl.refreshRate == gl.GLFW_DONT_CARE,
                    "unknown desktop refresh rate does not request an artificial video mode");
                OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.WINDOWED);
                controller.prepareFrame(960, 540);
                gl.monitorRefreshRate = 75;

                gl.x = 200;
                gl.y = 210;
                gl.width = 1000;
                gl.height = 720;
                controller.prepareFrame(960, 540);
                controller.swapBuffers();
                controller.pollEvents();
                require(gl.swapCalls == 1 && gl.pollCalls == 1, "swap/event forwarding");
                controller.prepareForShutdown();
                require(delegate.saves == 2, "resized bounds persisted on shutdown");
                controller.shutdown(true);
                controller.shutdown(true);
                require(gl.destroyCalls == 1 && gl.terminateCalls == 1,
                    "idempotent window shutdown");
                require(delegate.closes == 0, "programmatic close must not close client twice");

                OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.FULLSCREEN);
                LwjglBindings startupGl = new LwjglBindings();
                Delegate startupDelegate = new Delegate();
                OpenGLWindowController startup =
                    new OpenGLWindowController(startupGl, "fullscreen startup", true, startupDelegate);
                require(startup.initializeGlfw(), "fullscreen startup GLFW init");
                startup.createWindow(512, 346, false);
                require(startupGl.monitor == 99L, "saved fullscreen applies at startup");
                startup.shutdown(true);

                OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.FULLSCREEN);
                LwjglBindings unavailableGl = new LwjglBindings();
                unavailableGl.monitorAvailable = false;
                Delegate unavailableDelegate = new Delegate();
                OpenGLWindowController unavailable =
                    new OpenGLWindowController(unavailableGl, "no monitor", true, unavailableDelegate);
                require(unavailable.initializeGlfw(), "no-monitor GLFW init");
                unavailable.createWindow(512, 346, false);
                require(OpenGLWindowSettings.getMode()
                        == OpenGLWindowSettings.Mode.BORDERLESS_FULLSCREEN
                        && unavailableGl.monitorChanges == 0,
                    "missing monitor falls back to borderless without invalid native attachment");
                require(unavailableDelegate.saves == 1,
                    "unavailable fullscreen fallback is saved");
                unavailable.shutdown(true);

                OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.WINDOWED);
                LwjglBindings failingGl = new LwjglBindings();
                Delegate failingDelegate = new Delegate();
                OpenGLWindowController failing =
                    new OpenGLWindowController(failingGl, "failure", true, failingDelegate);
                require(failing.initializeGlfw(), "failure fixture GLFW init");
                failing.createWindow(512, 346, false);
                failingGl.destroyFailure = true;
                failingGl.terminateFailure = true;
                failing.shutdown(false);
                require(failingDelegate.closes == 1, "native window close forwards client close");
                require(failingDelegate.logs.toString().contains("destroy window")
                        && failingDelegate.logs.toString().contains("destroy marker")
                        && failingDelegate.logs.toString().contains("terminate GLFW")
                        && failingDelegate.logs.toString().contains("terminate marker"),
                    "cleanup failures are diagnosed and shutdown continues");
                System.out.println("window-ok");
            }
        }
    """
    output = run_java_fixture(
        "WindowFixture",
        {
            "LwjglBindings.java": bindings,
            "WindowFixture.java": fixture,
            "RendererDiagnosticSession.java": "package orsc; final class RendererDiagnosticSession { static boolean enabled; static boolean isEnabled() { return enabled; } }",
        },
        [WINDOW_SETTINGS, MONITOR_MODE, WINDOW],
    )
    require(output == "window-ok", "window fixture did not complete")

def verify_window_mode_settings() -> None:
    fixture = r"""
        package orsc;
        import java.util.Properties;
        public final class WindowModeSettingsFixture {
            private static void require(boolean condition, String message) {
                if (!condition) throw new AssertionError(message);
            }
            public static void main(String[] args) {
                require(OpenGLWindowSettings.getMode()
                    == OpenGLWindowSettings.Mode.BORDERLESS_FULLSCREEN,
                    "fresh installation defaults to borderless");
                require(OpenGLWindowSettings.Mode.from(null)
                    == OpenGLWindowSettings.Mode.BORDERLESS_FULLSCREEN,
                    "missing mode defaults to borderless");
                require(OpenGLWindowSettings.Mode.from("borderless")
                    == OpenGLWindowSettings.Mode.BORDERLESS_FULLSCREEN
                    && OpenGLWindowSettings.Mode.from("borderless-fullscreen")
                    == OpenGLWindowSettings.Mode.BORDERLESS_FULLSCREEN,
                    "existing borderless settings remain compatible");
                OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.WINDOWED);
                require(OpenGLWindowSettings.cycleMode()
                    == OpenGLWindowSettings.Mode.FULLSCREEN, "windowed to fullscreen");
                require(OpenGLWindowSettings.cycleMode()
                    == OpenGLWindowSettings.Mode.BORDERLESS_FULLSCREEN, "fullscreen to borderless");
                require(OpenGLWindowSettings.cycleMode()
                    == OpenGLWindowSettings.Mode.WINDOWED, "borderless to windowed");
                Properties saved = new Properties();
                for (OpenGLWindowSettings.Mode mode : OpenGLWindowSettings.Mode.values()) {
                    OpenGLWindowSettings.setMode(mode);
                    OpenGLWindowSettings.saveToClientSettings(saved);
                    require(mode.id.equals(saved.getProperty("opengl_window_mode")), "saved mode");
                    OpenGLWindowSettings.setMode(null);
                    OpenGLWindowSettings.loadFromClientSettings(saved);
                    require(OpenGLWindowSettings.getMode() == mode, "restored mode");
                }
                require(OpenGLWindowSettings.Mode.from("fullscreen")
                    == OpenGLWindowSettings.Mode.FULLSCREEN,
                    "fullscreen is a distinct persisted selection");
                System.out.println("settings-ok");
            }
        }
    """
    output = run_java_fixture(
        "WindowModeSettingsFixture",
        {"WindowModeSettingsFixture.java": fixture},
        [WINDOW_SETTINGS],
    )
    require(output == "settings-ok", "window-mode settings fixture did not complete")


def main() -> None:
    verify_source_ownership()
    verify_viewport_behavior()
    verify_renderer_replacement_readiness()
    verify_window_behavior()
    verify_window_mode_settings()
    print("PASS: OpenGL window lifecycle and viewport presentation have focused owners")


if __name__ == "__main__":
    main()
