package orsc;

/*
 * RENDERER-V2 OWNER: owns the GLFW window handle, mode transitions, persisted
 * windowed bounds, event/swap lifecycle, and best-effort native shutdown.
 */
final class OpenGLWindowController {
	interface Delegate {
		void contextCreated();

		void releaseInputState();

		void suppressKeysUntilRelease();

		void saveWindowSettings();

		void closeClient();

		void log(String message);
	}

	static final class SurfaceSize {
		final int framebufferWidth;
		final int framebufferHeight;
		final int windowWidth;
		final int windowHeight;

		SurfaceSize(int framebufferWidth, int framebufferHeight, int windowWidth, int windowHeight) {
			this.framebufferWidth = framebufferWidth;
			this.framebufferHeight = framebufferHeight;
			this.windowWidth = windowWidth;
			this.windowHeight = windowHeight;
		}
	}

	private final LwjglBindings gl;
	private final String title;
	private final boolean primaryWindow;
	private final Delegate delegate;

	private boolean glfwInitialized;
	private long window;
	private int windowWidth;
	private int windowHeight;
	private OpenGLWindowSettings.Mode appliedWindowMode;
	private int windowedX = OpenGLWindowSettings.getWindowedX();
	private int windowedY = OpenGLWindowSettings.getWindowedY();
	private int windowedWidth = OpenGLWindowSettings.getWindowedWidth();
	private int windowedHeight = OpenGLWindowSettings.getWindowedHeight();
	private boolean hasWindowedBounds = OpenGLWindowSettings.hasWindowedBounds();
	private boolean windowedBoundsDirty;
	private boolean shutdownPrepared;

	OpenGLWindowController(
		LwjglBindings gl,
		String title,
		boolean primaryWindow,
		Delegate delegate) {
		this.gl = gl;
		this.title = title;
		this.primaryWindow = primaryWindow;
		this.delegate = delegate;
	}

	boolean initializeGlfw() throws Exception {
		glfwInitialized = gl.glfwInit();
		return glfwInitialized;
	}

	void createWindow(int width, int height, boolean vsyncEnabled) throws Exception {
		gl.glfwDefaultWindowHints();
		gl.glfwWindowHint(gl.GLFW_VISIBLE, gl.GLFW_FALSE);
		gl.glfwWindowHint(gl.GLFW_RESIZABLE, gl.GLFW_TRUE);
		gl.glfwWindowHint(gl.GLFW_DECORATED, gl.GLFW_TRUE);

		window = gl.glfwCreateWindow(width, height, title, 0L, 0L);
		if (window == 0L) {
			throw new IllegalStateException("GLFW returned a null window handle");
		}

		windowWidth = width;
		windowHeight = height;
		gl.glfwMakeContextCurrent(window);
		gl.createCapabilities();
		delegate.contextCreated();
		gl.glfwSwapInterval(vsyncEnabled ? 1 : 0);
		delegate.log("OpenGL vsync: " + (vsyncEnabled ? "enabled" : "disabled") + ".");
		syncWindowMode(width, height);
		gl.glfwShowWindow(window);
	}

	long window() {
		return window;
	}

	boolean hasWindow() {
		return window != 0L;
	}

	boolean shouldClose() throws Exception {
		return gl.glfwWindowShouldClose(window);
	}

	void pollEvents() throws Exception {
		gl.glfwPollEvents();
	}

	void swapBuffers() throws Exception {
		gl.glfwSwapBuffers(window);
	}

	SurfaceSize prepareFrame(int targetWidth, int targetHeight) throws Exception {
		syncWindowMode(targetWidth, targetHeight);

		if (appliedWindowMode == OpenGLWindowSettings.Mode.WINDOWED
			&& !primaryWindow
			&& (targetWidth != windowWidth || targetHeight != windowHeight)) {
			gl.glfwSetWindowSize(window, targetWidth, targetHeight);
			windowWidth = targetWidth;
			windowHeight = targetHeight;
			recordWindowedBounds(windowedX, windowedY, windowWidth, windowHeight);
		}

		int[] framebufferWidth = new int[1];
		int[] framebufferHeight = new int[1];
		gl.glfwGetFramebufferSize(window, framebufferWidth, framebufferHeight);

		int[] actualWindowWidth = new int[1];
		int[] actualWindowHeight = new int[1];
		gl.glfwGetWindowSize(window, actualWindowWidth, actualWindowHeight);
		windowWidth = Math.max(1, actualWindowWidth[0]);
		windowHeight = Math.max(1, actualWindowHeight[0]);
		captureWindowedBounds(false);

		return new SurfaceSize(
			Math.max(1, framebufferWidth[0]),
			Math.max(1, framebufferHeight[0]),
			windowWidth,
			windowHeight);
	}

	void prepareForShutdown() {
		if (shutdownPrepared) {
			return;
		}
		shutdownPrepared = true;
		try {
			captureWindowedBounds(false);
			persistWindowedBoundsIfDirty();
		} catch (Throwable t) {
			logCleanupFailure("persist windowed bounds", t);
		}
	}

	void shutdown(boolean presenterClosed) {
		boolean closeClient = glfwInitialized && window != 0L && !presenterClosed && primaryWindow;
		prepareForShutdown();
		try {
			if (window != 0L) {
				gl.glfwDestroyWindow(window);
			}
		} catch (Throwable t) {
			logCleanupFailure("destroy window", t);
		} finally {
			window = 0L;
		}

		try {
			if (glfwInitialized) {
				gl.glfwTerminate();
			}
		} catch (Throwable t) {
			logCleanupFailure("terminate GLFW", t);
		} finally {
			glfwInitialized = false;
		}

		if (closeClient) {
			delegate.closeClient();
		}
	}

	private void syncWindowMode(int targetWidth, int targetHeight) throws Exception {
		OpenGLWindowSettings.Mode desiredMode = OpenGLWindowSettings.getMode();
		if (desiredMode == appliedWindowMode) {
			return;
		}

		delegate.releaseInputState();
		delegate.suppressKeysUntilRelease();
		if (desiredMode == OpenGLWindowSettings.Mode.FULLSCREEN) {
			if (!enterFullscreen()) {
				desiredMode = OpenGLWindowSettings.Mode.BORDERLESS_FULLSCREEN;
				OpenGLWindowSettings.setMode(desiredMode);
				enterBorderlessFullscreen();
				delegate.saveWindowSettings();
			}
		} else if (desiredMode == OpenGLWindowSettings.Mode.BORDERLESS_FULLSCREEN) {
			enterBorderlessFullscreen();
		} else {
			enterWindowedMode(targetWidth, targetHeight);
		}
		appliedWindowMode = desiredMode;
	}

	private void enterBorderlessFullscreen() throws Exception {
		captureWindowedBounds(true);
		MonitorMode monitorMode = gl.getPrimaryMonitorMode();
		gl.glfwSetWindowAttrib(window, gl.GLFW_DECORATED, gl.GLFW_FALSE);
		if (appliedWindowMode == OpenGLWindowSettings.Mode.FULLSCREEN) {
			gl.glfwSetWindowMonitor(window, 0L, monitorMode.x, monitorMode.y,
				monitorMode.width, monitorMode.height, gl.GLFW_DONT_CARE);
		}
		gl.glfwSetWindowPos(window, monitorMode.x, monitorMode.y);
		gl.glfwSetWindowSize(window, monitorMode.width, monitorMode.height);
		windowWidth = monitorMode.width;
		windowHeight = monitorMode.height;
		delegate.log(
			"OpenGL window mode: borderless fullscreen "
				+ monitorMode.width
				+ "x"
				+ monitorMode.height
				+ ".");
	}

	private boolean enterFullscreen() throws Exception {
		MonitorMode monitorMode = gl.getPrimaryMonitorMode();
		if (monitorMode.monitor == 0L) {
			delegate.log("Fullscreen unavailable without a monitor; using Borderless.");
			return false;
		}
		captureWindowedBounds(true);
		gl.glfwSetWindowAttrib(window, gl.GLFW_DECORATED, gl.GLFW_FALSE);
		int refreshRate = monitorMode.refreshRate > 0 ? monitorMode.refreshRate : gl.GLFW_DONT_CARE;
		gl.glfwSetWindowMonitor(window, monitorMode.monitor, 0, 0,
			monitorMode.width, monitorMode.height, refreshRate);
		windowWidth = monitorMode.width;
		windowHeight = monitorMode.height;
		delegate.log("OpenGL window mode: fullscreen "
			+ windowWidth + "x" + windowHeight
			+ (monitorMode.refreshRate > 0 ? " at " + monitorMode.refreshRate + " Hz."
				: " using desktop refresh rate."));
		return true;
	}

	private void enterWindowedMode(int targetWidth, int targetHeight) throws Exception {
		int restoreWidth = hasWindowedBounds ? windowedWidth : targetWidth;
		int restoreHeight = hasWindowedBounds ? windowedHeight : targetHeight;
		int restoreX = windowedX;
		int restoreY = windowedY;
		MonitorMode monitorMode = gl.getPrimaryMonitorMode();
		if (!hasWindowedBounds || isEffectivelyFullscreen(restoreWidth, restoreHeight, monitorMode)) {
			restoreWidth = Math.max(1, targetWidth);
			restoreHeight = Math.max(1, targetHeight);
			restoreX = monitorMode.x + Math.max(0, (monitorMode.width - restoreWidth) / 2);
			restoreY = monitorMode.y + Math.max(0, (monitorMode.height - restoreHeight) / 2);
		}

		boolean leavingScreenMode = appliedWindowMode != null
			&& appliedWindowMode != OpenGLWindowSettings.Mode.WINDOWED;
		if (leavingScreenMode) {
			gl.glfwHideWindow(window);
		}
		gl.glfwSetWindowAttrib(window, gl.GLFW_DECORATED, gl.GLFW_TRUE);
		if (appliedWindowMode == OpenGLWindowSettings.Mode.FULLSCREEN) {
			gl.glfwSetWindowMonitor(window, 0L, restoreX, restoreY,
				Math.max(1, restoreWidth), Math.max(1, restoreHeight), gl.GLFW_DONT_CARE);
		}
		gl.glfwSetWindowSize(window, Math.max(1, restoreWidth), Math.max(1, restoreHeight));
		gl.glfwSetWindowPos(window, restoreX, restoreY);
		if (leavingScreenMode) {
			gl.glfwShowWindow(window);
		}
		windowWidth = Math.max(1, restoreWidth);
		windowHeight = Math.max(1, restoreHeight);
		windowedX = restoreX;
		windowedY = restoreY;
		windowedWidth = windowWidth;
		windowedHeight = windowHeight;
		hasWindowedBounds = true;
		recordWindowedBounds(windowedX, windowedY, windowedWidth, windowedHeight);
		persistWindowedBoundsIfDirty();
		delegate.log("OpenGL window mode: windowed " + windowWidth + "x" + windowHeight + ".");
	}

	private void captureWindowedBounds(boolean persist) throws Exception {
		if (window == 0L || appliedWindowMode != OpenGLWindowSettings.Mode.WINDOWED) {
			return;
		}

		int[] x = new int[1];
		int[] y = new int[1];
		int[] width = new int[1];
		int[] height = new int[1];
		gl.glfwGetWindowPos(window, x, y);
		gl.glfwGetWindowSize(window, width, height);
		MonitorMode monitorMode = gl.getPrimaryMonitorMode();
		if (isEffectivelyFullscreen(width[0], height[0], monitorMode)) {
			return;
		}
		recordWindowedBounds(x[0], y[0], Math.max(1, width[0]), Math.max(1, height[0]));
		if (persist) {
			persistWindowedBoundsIfDirty();
		}
	}

	private void recordWindowedBounds(int x, int y, int width, int height) {
		windowedX = x;
		windowedY = y;
		windowedWidth = Math.max(1, width);
		windowedHeight = Math.max(1, height);
		hasWindowedBounds = true;
		if (OpenGLWindowSettings.setWindowedBounds(windowedX, windowedY, windowedWidth, windowedHeight)) {
			windowedBoundsDirty = true;
		}
	}

	private void persistWindowedBoundsIfDirty() {
		if (!windowedBoundsDirty) {
			return;
		}
		delegate.saveWindowSettings();
		windowedBoundsDirty = false;
	}

	private static boolean isEffectivelyFullscreen(
		int width,
		int height,
		MonitorMode monitorMode) {
		return width >= monitorMode.width - 32 && height >= monitorMode.height - 96;
	}

	private void logCleanupFailure(String operation, Throwable failure) {
		String message = failure.getMessage();
		delegate.log(
			"OpenGL window cleanup failure during "
				+ operation
				+ ": "
				+ failure.getClass().getName()
				+ (message == null || message.isEmpty() ? "" : ": " + message));
	}
}
