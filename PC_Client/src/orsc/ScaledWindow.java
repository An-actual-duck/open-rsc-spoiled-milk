package orsc;

import orsc.graphics.Renderer2DFrame;
import orsc.graphics.Renderer2DSettings;
import orsc.graphics.three.Renderer3DFrame;
import orsc.util.Utils;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import javax.swing.*;

import static orsc.OpenRSC.applet;
import static orsc.OpenRSC.jframe;

/**
 * This class is responsible for rendering all output from the applet onto the screen, which it
 * receives via a {@link BufferedImage} from the {@link ORSCApplet#draw()} method.
 * All window interactions are then forwarded to the applet within {@link OpenRSC}.
 * <p>
 * Code adapted from <a href="https://github.com/RSCPlus/rscplus">RSCPlus</a>
 */
public class ScaledWindow extends JFrame implements WindowListener, FocusListener, ComponentListener,
	MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {

	private static final String GPU_PRESENTER_PROPERTY = "spoiledmilk.gpuPresenter";
	private static final String GPU_PRESENTER_ENV = "SPOILED_MILK_GPU_PRESENTER";
	private static final boolean GPU_PRESENTER_ENABLED = readBoolean(GPU_PRESENTER_PROPERTY, GPU_PRESENTER_ENV);
	private static final String OPENGL_PRESENTER_PROPERTY = "spoiledmilk.openglPresenter";
	private static final String OPENGL_PRESENTER_ENV = "SPOILED_MILK_OPENGL_PRESENTER";
	private static final boolean OPENGL_PRESENTER_ENABLED =
		readBoolean(OPENGL_PRESENTER_PROPERTY, OPENGL_PRESENTER_ENV);
	private static final String OPENGL_INPUT_PROPERTY = "spoiledmilk.openglInput";
	private static final String OPENGL_INPUT_ENV = "SPOILED_MILK_OPENGL_INPUT";
	private static final boolean OPENGL_INPUT_ENABLED = readBoolean(OPENGL_INPUT_PROPERTY, OPENGL_INPUT_ENV);
	private static final String OPENGL_PRIMARY_WINDOW_PROPERTY = "spoiledmilk.openglPrimaryWindow";
	private static final String OPENGL_PRIMARY_WINDOW_ENV = "SPOILED_MILK_OPENGL_PRIMARY_WINDOW";
	private static final boolean OPENGL_PRIMARY_WINDOW_ENABLED =
		OPENGL_PRESENTER_ENABLED && readBoolean(OPENGL_PRIMARY_WINDOW_PROPERTY, OPENGL_PRIMARY_WINDOW_ENV);
	private static ScaledWindow instance = null;
	private static boolean initialRender = true;
	private static int javaVersion = 0;
	private static boolean isMacOS = false;
	private static boolean shouldRealign = false;
	private int frameWidth = 0;
	private int frameHeight = 0;
	private ScaledViewport scaledViewport;
	private int viewportWidth = 0;
	private int viewportHeight = 0;
	private static final int PRESENTATION_BUFFER_COUNT = 3;
	private final BufferedImage[] presentationBuffers = new BufferedImage[PRESENTATION_BUFFER_COUNT];
	private final OpenGLFramePresenter openGLFramePresenter =
		OPENGL_PRESENTER_ENABLED
			? new OpenGLFramePresenter(
				Config.WINDOW_TITLE,
				OPENGL_PRIMARY_WINDOW_ENABLED || OPENGL_INPUT_ENABLED,
				OPENGL_PRIMARY_WINDOW_ENABLED)
			: null;
	private int nextPresentationBufferIndex = 0;
	private int presentationBufferWidth = 0;
	private int presentationBufferHeight = 0;
	private int presentationBufferType = BufferedImage.TYPE_INT_RGB;

	public static boolean isOpenGLPrimaryWindowEnabled() {
		return OPENGL_PRIMARY_WINDOW_ENABLED;
	}

	/** Private constructor to ensure singleton nature */
	private ScaledWindow() {
		try {
			// Set System L&F as the default
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (UnsupportedLookAndFeelException e) {
			System.out.println("Unable to set L&F: Unsupported look and feel");
		} catch (ClassNotFoundException e) {
			System.out.println("Unable to set L&F: Class not found");
		} catch (InstantiationException e) {
			System.out.println("Unable to set L&F: Class object cannot be instantiated");
		} catch (IllegalAccessException e) {
			System.out.println("Unable to set L&F: Illegal access exception");
		}

		System.out.println("Creating scaled window");

		/* Initialize the contents of the frame. */
		try {
			SwingUtilities.invokeAndWait(() -> {
				javaVersion = Utils.getJavaVersion();

				runInit();
			});
		} catch (InvocationTargetException e) {
			System.out.println("There was a thread-related error while setting up the scaled window!");
			e.printStackTrace();
		} catch (InterruptedException e) {
			System.out.println(
				"There was a thread-related error while setting up the scaled window! The window may not be initialized properly!");
			e.printStackTrace();
		}
	}

	private void runInit() {
		// Set window properties
		setBackground(Color.black);
		setFocusTraversalKeysEnabled(false);

		// Add window listeners
		addWindowListener(this);
		addComponentListener(this);
		addFocusListener(this);
		addKeyListener(this);

		// Enable macOS fullscreen button, if possible
		isMacOS = Utils.isMacOS();

		if (isMacOS) {
			try {
				Class util = Class.forName("com.apple.eawt.FullScreenUtilities");
				Class params[] = new Class[] {Window.class, Boolean.TYPE};
				@SuppressWarnings("unchecked")
				Method method = util.getMethod("setWindowCanFullScreen", params);
				method.invoke(util, this, true);
			} catch (Exception ignored) {
			}
		}

		// Set minimum size to applet size
		setMinimumSize(RenderSurfaceSettings.getDimensions());

		// Default icon, will be overridden later
		setIconImage(Utils.getImage("icon.png").getImage());

		// Initialize scaled view
		scaledViewport = new ScaledViewport();

		scaledViewport.addMouseListener(this);
		scaledViewport.addMouseMotionListener(this);
		scaledViewport.addMouseWheelListener(this);

		scaledViewport.setSize(getSize());
		scaledViewport.setBackground(Color.black);
		scaledViewport.revalidate();
		scaledViewport.repaint();
		scaledViewport.setVisible(true);

		add(scaledViewport);

		pack();
		revalidate();
		repaint();

		// Determine maximum scalar that will fit the screen, plus one
		Dimension maxEffectiveWindowSize = getMaximumEffectiveWindowSize();
		int maxRenderingScalar = 1;
		for (int i = 6; i >= 1; i--) {
			float width = RenderSurfaceSettings.getWidth() * i;
			float height = RenderSurfaceSettings.getHeight() * i;

			if (width <= maxEffectiveWindowSize.width && height <= maxEffectiveWindowSize.height) {
				maxRenderingScalar = i + 1;
				break;
			}
		}

		LegacySoftwareScalingSettings.configureAllowedScalars(maxRenderingScalar);
	}

	/**
	 * Keep track of frame dimensions internally to avoid possible thread-safety issues when needing
	 * to invoke a method that uses the frame size, immediately after setting it.
	 *
	 * <p>NOTE: Must <i>always</i> call setMinimumSize before invoking this method
	 */
	@Override
	public void setSize(int width, int height) {
		super.setSize(width, height);

		frameWidth = width;
		frameHeight = height;
	}

	/** Sets a flag to align the window after resizing the applet */
	public void setWindowRealignmentIntent(boolean flag) {
		shouldRealign = flag;
	}

	/**
	 * Centers the window or pins it to the top of the screen, if the custom size exactly matches the
	 * available space.
	 */
	private void alignWindow() {
		Rectangle currentScreenBounds = getGraphicsConfiguration().getDevice().getDefaultConfiguration().getBounds();

		int x = ((currentScreenBounds.width - frameWidth) / 2) + currentScreenBounds.x;
		int y = ((currentScreenBounds.height - frameHeight) / 2) + currentScreenBounds.y;

		// Set the window location
		setLocation(x, y);
	}

	/**
	 * Used to determine the user's maximum effective window size, taking the window's insets into
	 * consideration.
	 */
	public Dimension getMaximumEffectiveWindowSize() {
		Dimension maximumWindowSize = getMaximumWindowSize();

		// Subtract
		int windowWidth = maximumWindowSize.width - getWindowWidthInsets();
		int windowHeight = maximumWindowSize.height - getWindowHeightInsets();

		if (Utils.isModernWindowsOS()) {
			windowWidth += 16;
			windowHeight += 8;
		}

		return new Dimension(windowWidth, windowHeight);
	}

	/** Used to determine the user's maximum window size */
	public Dimension getMaximumWindowSize() {
		GraphicsConfiguration graphicsConfiguration = getGraphicsConfiguration().getDevice().getDefaultConfiguration();
		Rectangle screenBounds = graphicsConfiguration.getBounds();
		Insets screenInsets = getToolkit().getScreenInsets(graphicsConfiguration);

		// Subtract the operating system insets from the current display's max bounds
		int maxWidth = screenBounds.width - screenInsets.left - screenInsets.right;
		int maxHeight = screenBounds.height - screenInsets.top - screenInsets.bottom;

		return new Dimension(maxWidth, maxHeight);
	}

	/** Opens the window */
	public void launchScaledWindow() {
		if (OPENGL_PRIMARY_WINDOW_ENABLED) {
			return;
		}

		setLocationRelativeTo(null);
		setVisible(true);
	}

	/**
	 * Sets the {@link BufferedImage} that the window should display,
	 * from {@link ORSCApplet#draw()}
	 */
	public void setGameImage(BufferedImage gameImage) {
		setGameImage(gameImage, Renderer2DFrame.EMPTY);
	}

	/**
	 * Sets the {@link BufferedImage} and optional renderer-v2 2D command snapshot
	 * that the window should display, from {@link ORSCApplet#draw()}.
	 */
	public void setGameImage(BufferedImage gameImage, Renderer2DFrame renderer2DFrame) {
		setGameImage(gameImage, renderer2DFrame, null);
	}

	/**
	 * Sets the {@link BufferedImage}, optional renderer-v2 2D command snapshot,
	 * and optional pre-UI OpenGL base frame that the window should display.
	 */
	public void setGameImage(
		BufferedImage gameImage,
		Renderer2DFrame renderer2DFrame,
		BufferedImage renderer2DUiBaseImage) {
		setGameImage(gameImage, renderer2DFrame, renderer2DUiBaseImage, null);
	}

	/**
	 * Sets the {@link BufferedImage}, optional renderer-v2 2D command snapshot,
	 * optional pre-UI OpenGL base frame, and optional renderer-v2 world mesh
	 * that the window should display.
	 */
	public void setGameImage(
		BufferedImage gameImage,
		Renderer2DFrame renderer2DFrame,
		BufferedImage renderer2DUiBaseImage,
		Renderer3DFrame renderer3DFrame) {
		setGameImage(gameImage, renderer2DFrame, renderer2DUiBaseImage, renderer3DFrame, null);
	}

	public void setGameImage(
		BufferedImage gameImage,
		Renderer2DFrame renderer2DFrame,
		BufferedImage renderer2DUiBaseImage,
		Renderer3DFrame renderer3DFrame,
		String[] rendererDebugOverlayLines) {
		if (gameImage == null) {
			if (renderer3DFrame != null) {
				renderer3DFrame.release();
			}
			return;
		}
		if (renderer2DFrame == null) {
			renderer2DFrame = Renderer2DFrame.EMPTY;
		}

		boolean telemetryEnabled = RenderTelemetry.isEnabled();
		long setImageStart = RenderTelemetry.now();
		long sourceCopyNanos = 0L;
		long paintImmediateNanos = 0L;
		boolean repaintRequested = false;
		boolean paintImmediateRequested = false;

		viewportWidth = gameImage.getWidth();
		viewportHeight = gameImage.getHeight();

		if (initialRender) {
			if (!OPENGL_PRIMARY_WINDOW_ENABLED) {
				// Set the window size for the scalar (will be realigned in the method)
				resizeWindowToScalar();
			}
			initialRender = false;
		}

		boolean useRenderer2DUiBaseImage = shouldUseRenderer2DUiBaseImage(renderer2DUiBaseImage, renderer2DFrame);
		BufferedImage openGLImage = useRenderer2DUiBaseImage
			? renderer2DUiBaseImage
			: gameImage;

		if (OPENGL_PRIMARY_WINDOW_ENABLED && openGLFramePresenter != null) {
			openGLFramePresenter.present(
				openGLImage,
				1.0f,
				ScalingAlgorithm.INTEGER_SCALING,
				renderer2DFrame,
				renderer3DFrame,
				rendererDebugOverlayLines);
			if (telemetryEnabled) {
				RenderTelemetry.recordSetGameImage(
					RenderTelemetry.elapsedSince(setImageStart),
					0L,
					0L,
					false,
					false);
			}
			return;
		}

		long sourceCopyStart = RenderTelemetry.now();
		BufferedImage presentationImage = copyFrameToPresentationBuffer(gameImage);
		sourceCopyNanos = RenderTelemetry.elapsedSince(sourceCopyStart);

		if (openGLFramePresenter != null) {
			openGLFramePresenter.present(
				useRenderer2DUiBaseImage
					? renderer2DUiBaseImage
					: presentationImage,
				LegacySoftwareScalingSettings.getRenderingScalar(),
				LegacySoftwareScalingSettings.getScalingAlgorithm(),
				renderer2DFrame,
				renderer3DFrame);
		} else if (renderer3DFrame != null) {
			renderer3DFrame.release();
		}

		scaledViewport.setViewportImage(presentationImage);

		int repaintWidth = LegacySoftwareScalingSettings.getRenderingScalar() == 1.0f
			? viewportWidth
			: LegacySoftwareScalingSettings.scaleDimension(viewportWidth);
		int repaintHeight = LegacySoftwareScalingSettings.getRenderingScalar() == 1.0f
			? viewportHeight
			: LegacySoftwareScalingSettings.scaleDimension(viewportHeight);
		scaledViewport.repaint(0, 0, repaintWidth, repaintHeight);
		repaintRequested = true;

		if (telemetryEnabled) {
			RenderTelemetry.recordSetGameImage(
				RenderTelemetry.elapsedSince(setImageStart),
				sourceCopyNanos,
				paintImmediateNanos,
				repaintRequested,
				paintImmediateRequested);
		}
	}

	private BufferedImage copyFrameToPresentationBuffer(BufferedImage gameImage) {
		int width = gameImage.getWidth();
		int height = gameImage.getHeight();
		int imageType = getConcreteImageType(gameImage);
		BufferedImage target = getNextPresentationBuffer(width, height, imageType);

		Graphics2D g2d = target.createGraphics();
		g2d.drawImage(gameImage, 0, 0, null);
		g2d.dispose();

		return target;
	}

	private BufferedImage getNextPresentationBuffer(int width, int height, int imageType) {
		if (presentationBufferWidth != width
			|| presentationBufferHeight != height
			|| presentationBufferType != imageType) {
			Arrays.fill(presentationBuffers, null);
			presentationBufferWidth = width;
			presentationBufferHeight = height;
			presentationBufferType = imageType;
			nextPresentationBufferIndex = 0;
		}

		BufferedImage currentImage = scaledViewport.getViewportImage();
		BufferedImage paintingImage = scaledViewport.getPaintingImage();
		for (int i = 0; i < presentationBuffers.length; i++) {
			int index = (nextPresentationBufferIndex + i) % presentationBuffers.length;
			BufferedImage buffer = presentationBuffers[index];
			if (buffer != null && (buffer == currentImage || buffer == paintingImage)) {
				continue;
			}
			if (buffer == null) {
				buffer = new BufferedImage(width, height, imageType);
				presentationBuffers[index] = buffer;
				RenderTelemetry.recordImageAllocation("presentation-buffer", width, height, imageType);
			}
			nextPresentationBufferIndex = (index + 1) % presentationBuffers.length;
			return buffer;
		}

		RenderTelemetry.recordImageAllocation("presentation-overflow-buffer", width, height, imageType);
		return new BufferedImage(width, height, imageType);
	}

	private boolean shouldUseRenderer2DUiBaseImage(
		BufferedImage renderer2DUiBaseImage,
		Renderer2DFrame renderer2DFrame) {
		return renderer2DUiBaseImage != null
			&& Renderer2DSettings.canPresentUiBaseFrame()
			&& renderer2DFrame != null
			&& renderer2DFrame.getCaptureStats().isNativeUiBaseEligible();
	}

	public boolean isViewportLoaded() {
		return scaledViewport.isViewportImageLoaded();
	}

	public int getWindowWidthInsets() {
		return getInsets().left + getInsets().right;
	}

	public int getWindowHeightInsets() {
		return getInsets().top + getInsets().bottom;
	}

	/** Resizes the window size for the scalar */
	public void resizeWindowToScalar() {
		if (OPENGL_PRIMARY_WINDOW_ENABLED) {
			return;
		}

		Dimension minimumWindowSizeForScalar = getMinimumWindowSizeForScalar();

		if (!getSize().equals(minimumWindowSizeForScalar)) {
			// Update the window size as necessary, which will in turn
			// invoke the componentResized listener on this JFrame
			setWindowRealignmentIntent(true);

			setMinimumSize(minimumWindowSizeForScalar);
			setSize(minimumWindowSizeForScalar);
		} else {
			// Resize the viewport if the actual window size didn't change, since
			// the componentResized listener won't get triggered in that case.
			// e.g. size set to 1024x692, then scale x2 turned on
			setMinimumSize(minimumWindowSizeForScalar);
			resizeApplet();
		}
	}

	/** Determines the smallest window size for the scalar, including insets */
	private Dimension getMinimumWindowSizeForScalar() {
		Dimension minimumViewPortSizeForScalar = getMinimumViewportSizeForScalar();

		int frameWidth = minimumViewPortSizeForScalar.width + getWindowWidthInsets();
		int frameHeight = minimumViewPortSizeForScalar.height + getWindowHeightInsets();

		return new Dimension(frameWidth, frameHeight);
	}

	/** Determines the minimum window size for the applet based on the scalar */
	public Dimension getMinimumViewportSizeForScalar() {
		int renderWidth = viewportWidth > 0 ? viewportWidth : RenderSurfaceSettings.getWidth();
		int renderHeight = viewportHeight > 0 ? viewportHeight : RenderSurfaceSettings.getHeight();
		return new Dimension(
			LegacySoftwareScalingSettings.scaleDimension(renderWidth),
			LegacySoftwareScalingSettings.scaleDimension(renderHeight));
	}

	/** Resizes the applet contained within {@link OpenRSC} */
	private void resizeApplet() {
		if (OPENGL_PRIMARY_WINDOW_ENABLED) {
			return;
		}

		if (LegacySoftwareScalingSettings.getRenderingScalar() == 0.0f || !isViewportLoaded()) {
			return;
		}

		int newWidth = LegacySoftwareScalingSettings.unscaleCoordinate(scaledViewport.getWidth());
		int newHeight = LegacySoftwareScalingSettings.unscaleCoordinate(scaledViewport.getHeight());

		if (applet != null) {
			applet.setSize(newWidth, newHeight);
			applet.resizeMudclient(newWidth, newHeight);
		}

		if (shouldRealign) {
			setWindowRealignmentIntent(false);
			alignWindow();
		}
	}

	/** Resizes the mudclient if its dimensions don't match the current frame size */
	public void validateAppletSize() {
		if (OPENGL_PRIMARY_WINDOW_ENABLED) {
			return;
		}

		if (applet == null) return;

		int newWidth = LegacySoftwareScalingSettings.unscaleCoordinate(scaledViewport.getWidth());
		int newHeight = LegacySoftwareScalingSettings.unscaleCoordinate(scaledViewport.getHeight());

		if (applet.getWidth() != newWidth || applet.getHeight() != newHeight) {
			applet.setSize(newWidth, newHeight);
			applet.resizeMudclient(newWidth, newHeight);
		}
	}

	/*
	 * WindowListener methods - forward to Game.java
	 */

	@Override
	public void windowClosed(WindowEvent e) {
		if (openGLFramePresenter != null) {
			openGLFramePresenter.close();
		}
		jframe.dispatchEvent(new WindowEvent(jframe, WindowEvent.WINDOW_CLOSED));
	}

	@Override
	public void windowClosing(WindowEvent e) {
		if (openGLFramePresenter != null) {
			openGLFramePresenter.close();
		}
		jframe.dispatchEvent(new WindowEvent(jframe, WindowEvent.WINDOW_CLOSING));
	}

	@Override
	public void windowOpened(WindowEvent e) {}

	@Override
	public void windowDeactivated(WindowEvent e) {}

	@Override
	public void windowActivated(WindowEvent e) {}

	@Override
	public void windowDeiconified(WindowEvent e) {}

	@Override
	public void windowIconified(WindowEvent e) {}

	/*
	 * FocusListener methods - forward to Game.java
	 */

	@Override
	public void focusGained(FocusEvent e) {}

	@Override
	public void focusLost(FocusEvent e) {
		if (applet.getKeyHandler() == null
			|| LegacySoftwareScalingSettings.getRenderingScalar() == 0.0f) return;

		applet.resetArrowKeys();
	}

	/*
	 * ComponentListener methods
	 */

	@Override
	public void componentResized(ComponentEvent e) {
		if (!OPENGL_PRIMARY_WINDOW_ENABLED) {
			resizeApplet();
		}

		frameWidth = e.getComponent().getWidth();
		frameHeight = e.getComponent().getHeight();
	}

	@Override
	public void componentMoved(ComponentEvent e) {}

	@Override
	public void componentShown(ComponentEvent e) {}

	@Override
	public void componentHidden(ComponentEvent e) {}

	/*
	 * MouseListener, MouseMotionListener, and MouseWheelListener methods
	 * - forward to Client.handler_mouse
	 */

	@Override
	public void mouseClicked(MouseEvent e) {
		if (applet.getMouseHandler() == null
			|| LegacySoftwareScalingSettings.getRenderingScalar() == 0.0f) return;

		applet.getMouseHandler().mouseClicked(mapMouseEvent(e));
	}

	@Override
	public void mousePressed(MouseEvent e) {
		if (applet.getMouseHandler() == null
			|| LegacySoftwareScalingSettings.getRenderingScalar() == 0.0f) return;

		applet.getMouseHandler().mousePressed(mapMouseEvent(e));
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		if (applet.getMouseHandler() == null
			|| LegacySoftwareScalingSettings.getRenderingScalar() == 0.0f) return;

		applet.getMouseHandler().mouseReleased(mapMouseEvent(e));
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		if (applet.getMouseHandler() == null
			|| LegacySoftwareScalingSettings.getRenderingScalar() == 0.0f) return;

		applet.getMouseHandler().mouseEntered(mapMouseEvent(e));
	}

	@Override
	public void mouseExited(MouseEvent e) {
		if (applet.getMouseHandler() == null
			|| LegacySoftwareScalingSettings.getRenderingScalar() == 0.0f) return;

		applet.getMouseHandler().mouseExited(mapMouseEvent(e));
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		if (applet.getMouseHandler() == null
			|| LegacySoftwareScalingSettings.getRenderingScalar() == 0.0f) return;

		applet.getMouseHandler().mouseDragged(mapMouseEvent(e));
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		if (applet.getMouseHandler() == null
			|| LegacySoftwareScalingSettings.getRenderingScalar() == 0.0f) return;

		applet.getMouseHandler().mouseMoved(mapMouseEvent(e));
	}

	private static MouseEvent mapMouseEvent(MouseEvent e) {
		Component mouseEventSource = (Component) e.getSource();
		int mouseEventId = e.getID();
		long mouseEventWhen = e.getWhen();
		int mouseEventModifiers = e.getModifiers();
		int mappedMouseEventX = LegacySoftwareScalingSettings.unscaleCoordinate(e.getX());
		int mappedMouseEventY = LegacySoftwareScalingSettings.unscaleCoordinate(e.getY());
		int mouseEventXOnScreen = e.getXOnScreen();
		int mouseEventYOnScreen = e.getYOnScreen();
		int mouseEventClickCount = e.getClickCount();
		boolean mouseEventPopupTrigger = e.isPopupTrigger();
		int mouseEventButton = e.getButton();

		return new MouseEvent(
			mouseEventSource,
			mouseEventId,
			mouseEventWhen,
			mouseEventModifiers,
			mappedMouseEventX,
			mappedMouseEventY,
			mouseEventXOnScreen,
			mouseEventYOnScreen,
			mouseEventClickCount,
			mouseEventPopupTrigger,
			mouseEventButton);
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {
		if (applet.getMouseHandler() == null
			|| LegacySoftwareScalingSettings.getRenderingScalar() == 0.0f) return;

		applet.getMouseHandler().mouseWheelMoved(mapMouseWheelEvent(e));
	}

	private static MouseWheelEvent mapMouseWheelEvent(MouseWheelEvent e) {
		Component mouseWheelEventSource = (Component) e.getSource();
		int mouseWheelEventId = e.getID();
		long mouseWheelEventWhen = e.getWhen();
		int mouseWheelEventModifiers = e.getModifiers();
		int mappedMouseWheelEventX = LegacySoftwareScalingSettings.unscaleCoordinate(e.getX());
		int mappedMouseWheelEventY = LegacySoftwareScalingSettings.unscaleCoordinate(e.getY());
		int mouseWheelEventXOnScreen = e.getXOnScreen();
		int mouseWheelEventYOnScreen = e.getYOnScreen();
		int mouseWheelEventClickCount = e.getClickCount();
		boolean mouseWheelEventPopupTrigger = e.isPopupTrigger();
		int mouseWheelEventScrollType = e.getScrollType();
		int mouseWheelEventScrollAmount = e.getScrollAmount();
		int mouseWheelEventWheelRotation = e.getWheelRotation();
		double mouseWheelEventPreciseWheelRotation = e.getPreciseWheelRotation();

		return new MouseWheelEvent(
			mouseWheelEventSource,
			mouseWheelEventId,
			mouseWheelEventWhen,
			mouseWheelEventModifiers,
			mappedMouseWheelEventX,
			mappedMouseWheelEventY,
			mouseWheelEventXOnScreen,
			mouseWheelEventYOnScreen,
			mouseWheelEventClickCount,
			mouseWheelEventPopupTrigger,
			mouseWheelEventScrollType,
			mouseWheelEventScrollAmount,
			mouseWheelEventWheelRotation,
			mouseWheelEventPreciseWheelRotation);
	}

	/*
	 * KeyListener methods - forward to Client.handler_keyboard
	 */

	@Override
	public void keyTyped(KeyEvent e) {
		if (applet.getKeyHandler() == null
			|| LegacySoftwareScalingSettings.getRenderingScalar() == 0.0f) return;

		applet.getKeyHandler().keyTyped(e);
	}

	@Override
	public void keyPressed(KeyEvent e) {
		if (applet.getMouseHandler() == null
			|| LegacySoftwareScalingSettings.getRenderingScalar() == 0.0f) return;

		applet.getKeyHandler().keyPressed(e);
	}

	@Override
	public void keyReleased(KeyEvent e) {
		if (applet.getMouseHandler() == null
			|| LegacySoftwareScalingSettings.getRenderingScalar() == 0.0f) return;

		applet.getKeyHandler().keyReleased(e);
	}

	/**
	 * All possible types of scaling supported by the client
	 */
	public enum ScalingAlgorithm {
		INTEGER_SCALING,
		BILINEAR_INTERPOLATION,
		BICUBIC_INTERPOLATION
	}

	/**
	 * @return The {@link BufferedImage} type based on the current {@link ScalingAlgorithm}
	 */
	public static int getBufferedImageType() {
		if (LegacySoftwareScalingSettings.getScalingAlgorithm() == ScalingAlgorithm.INTEGER_SCALING) {
			return BufferedImage.TYPE_INT_RGB;
		} else if (LegacySoftwareScalingSettings.getScalingAlgorithm()
			== ScalingAlgorithm.BILINEAR_INTERPOLATION) {
			return BufferedImage.TYPE_3BYTE_BGR;
		} else if (LegacySoftwareScalingSettings.getScalingAlgorithm()
			== ScalingAlgorithm.BICUBIC_INTERPOLATION) {
			return BufferedImage.TYPE_3BYTE_BGR;
		}

		return BufferedImage.TYPE_INT_RGB;
	}

	private static int getConcreteImageType(BufferedImage image) {
		int imageType = image.getType();
		return imageType == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_INT_RGB : imageType;
	}

	private static Object getInterpolationHint() {
		// Frame scaling must stay pixel-crisp. Font smoothing is owned by
		// renderer-v2 glyph replay so sprites, terrain, and UI art do not blur.
		return RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
	}

	private static boolean readBoolean(String propertyName, String envName) {
		String value = System.getProperty(propertyName);
		if (value == null || value.trim().isEmpty()) {
			value = System.getenv(envName);
		}
		if (value == null) {
			return false;
		}

		value = value.trim();
		return "true".equalsIgnoreCase(value)
			|| "1".equals(value)
			|| "yes".equalsIgnoreCase(value)
			|| "on".equalsIgnoreCase(value);
	}

	/**
	 * Gets the scaled window instance. It makes one if one doesn't exist.
	 *
	 * @return The scaled window instance
	 */
	public static ScaledWindow getInstance() {
		if (instance == null) {
			synchronized (ScaledWindow.class) {
				instance = new ScaledWindow();
			}
		}
		return instance;
	}

	/*
	 * Image rendering
	 */

	/** JPanel used for rendering the game viewport, with scaling capabilities */
	private static class ScaledViewport extends JPanel {
		BufferedImage interpolationBackground = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
		private volatile BufferedImage viewportImage;
		private volatile BufferedImage paintingImage;
		private VolatileImage gpuPresentationImage;
		private int gpuPresentationWidth;
		private int gpuPresentationHeight;

		int previousWidth = 0;
		int previousHeight = 0;

		int newWidth;
		int newHeight;

		public ScaledViewport() {
			super();
			setOpaque(true);
			setBackground(Color.black);
		}

		/** Provides the game image to the viewport */
		public void setViewportImage(BufferedImage gameImage) {
			viewportImage = gameImage;
		}

		public BufferedImage getViewportImage() {
			return viewportImage;
		}

		public BufferedImage getPaintingImage() {
			return paintingImage;
		}

		/** Ensures the viewport image has been set */
		public boolean isViewportImageLoaded() {
			return viewportImage != null;
		}

		@Override
		protected void paintComponent(Graphics g) {
			BufferedImage paintImage = viewportImage;
			if (paintImage == null
				|| getInstance().viewportWidth == 0
				|| getInstance().viewportHeight == 0) {
				return;
			}
			paintingImage = paintImage;

			boolean telemetryEnabled = RenderTelemetry.isEnabled();
			long paintStart = RenderTelemetry.now();
			long scaleNanos = 0L;
			boolean nearestScale = false;
			boolean interpolationScale = false;

			try {
				// Do not perform any scaling operations at a 1.0x scalar
				if (LegacySoftwareScalingSettings.getRenderingScalar() == 1.0f) {
					if (GPU_PRESENTER_ENABLED) {
						long scaleStart = RenderTelemetry.now();
						drawGpuPresentedImage(g, paintImage, paintImage.getWidth(), paintImage.getHeight());
						scaleNanos = RenderTelemetry.elapsedSince(scaleStart);
						RenderTelemetry.recordGpuPresenter(scaleNanos);
					} else {
						g.drawImage(paintImage, 0, 0, null);
					}
					return;
				}

				newWidth = LegacySoftwareScalingSettings.scaleDimension(paintImage.getWidth());
				newHeight = LegacySoftwareScalingSettings.scaleDimension(paintImage.getHeight());

				if (LegacySoftwareScalingSettings.getScalingAlgorithm()
					== ScalingAlgorithm.INTEGER_SCALING) {
					// Workaround for direct drawImage warping which seems to only affect macOS on JDK 19
					if (isMacOS && javaVersion >= 19) {
						g.setClip(0, 0, newWidth, newHeight);
					}

					long scaleStart = RenderTelemetry.now();
					if (GPU_PRESENTER_ENABLED) {
						drawGpuPresentedImage(g, paintImage, newWidth, newHeight);
						RenderTelemetry.recordGpuPresenter(RenderTelemetry.elapsedSince(scaleStart));
					} else {
						g.drawImage(paintImage, 0, 0, newWidth, newHeight, null);
					}
					scaleNanos = RenderTelemetry.elapsedSince(scaleStart);
					nearestScale = true;
				} else {
					if (GPU_PRESENTER_ENABLED) {
						long scaleStart = RenderTelemetry.now();
						drawGpuPresentedImage(g, paintImage, newWidth, newHeight);
						scaleNanos = RenderTelemetry.elapsedSince(scaleStart);
						RenderTelemetry.recordGpuPresenter(scaleNanos);
					} else {
						if (interpolationBackground == null) {
							return;
						}

						// Reset image background when the window properties have changed
						if (previousWidth != newWidth || previousHeight != newHeight) {
							interpolationBackground = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_3BYTE_BGR);
							RenderTelemetry.recordImageAllocation(
								"interpolation-background",
								newWidth,
								newHeight,
								BufferedImage.TYPE_3BYTE_BGR);

							previousWidth = newWidth;
							previousHeight = newHeight;
						}

						long scaleStart = RenderTelemetry.now();
						Graphics2D g2d = interpolationBackground.createGraphics();
						try {
							g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, getInterpolationHint());
							g2d.drawImage(paintImage, 0, 0, newWidth, newHeight, null);
						} finally {
							g2d.dispose();
						}
						scaleNanos = RenderTelemetry.elapsedSince(scaleStart);
						RenderTelemetry.recordSmoothScale(scaleNanos);

						// Draw the interpolation-scaled image
						g.drawImage(interpolationBackground, 0, 0, null);
					}
					interpolationScale = true;
				}
			} finally {
				paintingImage = null;

				if (telemetryEnabled) {
					RenderTelemetry.recordViewportPaint(
						RenderTelemetry.elapsedSince(paintStart),
						scaleNanos,
						nearestScale,
						interpolationScale);
			}
		}
	}

		private void drawGpuPresentedImage(Graphics g, BufferedImage paintImage, int width, int height) {
			ensureGpuPresentationImage(width, height);

			do {
				int validation = gpuPresentationImage.validate(getGraphicsConfiguration());
				if (validation == VolatileImage.IMAGE_INCOMPATIBLE) {
					recreateGpuPresentationImage(width, height);
				}

				Graphics2D gpuGraphics = gpuPresentationImage.createGraphics();
				try {
					gpuGraphics.setComposite(AlphaComposite.Src);
					gpuGraphics.setColor(Color.black);
					gpuGraphics.fillRect(0, 0, width, height);
					gpuGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, getInterpolationHint());
					gpuGraphics.drawImage(paintImage, 0, 0, width, height, null);
				} finally {
					gpuGraphics.dispose();
				}

				g.drawImage(gpuPresentationImage, 0, 0, null);
			} while (gpuPresentationImage.contentsLost());
		}

		private void ensureGpuPresentationImage(int width, int height) {
			if (gpuPresentationImage == null
				|| gpuPresentationWidth != width
				|| gpuPresentationHeight != height) {
				recreateGpuPresentationImage(width, height);
			}
		}

		private void recreateGpuPresentationImage(int width, int height) {
			GraphicsConfiguration graphicsConfiguration = getGraphicsConfiguration();
			if (graphicsConfiguration == null) {
				graphicsConfiguration = GraphicsEnvironment
					.getLocalGraphicsEnvironment()
					.getDefaultScreenDevice()
					.getDefaultConfiguration();
			}

			gpuPresentationImage = graphicsConfiguration.createCompatibleVolatileImage(width, height, Transparency.OPAQUE);
			gpuPresentationWidth = width;
			gpuPresentationHeight = height;
			RenderTelemetry.recordImageAllocation("gpu-presentation-image", width, height, BufferedImage.TYPE_INT_RGB);
		}

	}
}
