package orsc;

import com.openrsc.client.model.Sprite;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.Renderer2DSettings;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.two.Fonts;
import orsc.multiclient.ClientPort;
import orsc.util.GenUtil;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.ByteArrayInputStream;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

import static orsc.Config.S_ZOOM_VIEW_TOGGLE;

public class ORSCApplet extends Applet implements ComponentListener, ImageObserver, ImageProducer, ClientPort {
	private static final long serialVersionUID = 1L;
	static {
		RendererRuntimeDefaults.apply();
	}

	private static final String DIRECT_FRAMEBUFFER_PROPERTY = "spoiledmilk.directFramebuffer";
	private static final String DIRECT_FRAMEBUFFER_ENV = "SPOILED_MILK_DIRECT_FRAMEBUFFER";
	private static final boolean DIRECT_FRAMEBUFFER_ENABLED =
		readBoolean(DIRECT_FRAMEBUFFER_PROPERTY, DIRECT_FRAMEBUFFER_ENV);
	private static final int MESSAGE_WHEEL_SCROLL_AREA_HEIGHT = 75;
	private static final CpuUsageSampler CPU_USAGE_SAMPLER = new CpuUsageSampler();
	private static final MemoryUsageSampler MEMORY_USAGE_SAMPLER = new MemoryUsageSampler();
	public static int globalLoadingPercent = 0;
	public static String globalLoadingState = "";
	private static mudclient mudclient;
	private final boolean m_hb = false;
	protected int resizeWidth;
	protected int resizeHeight;
	private Font createdbyFont = new Font("Helvetica", 1, 13);
	private Font copyrightFont2 = new Font("Helvetica", 0, 12);
	private Font loadingFont = new Font("TimesRoman", 0, 15);
	private Graphics loadingGraphics;
	private Image loadingLogo;
	private String loadingState = "Loading";
	boolean m_N = false;
	private String m_p = null;
	private int loadingPercent = 0;
	private int height = RenderSurfaceSettings.getHeight();
	private int width = RenderSurfaceSettings.getWidth();
	private DirectColorModel imageModel;
	private Image backingImage;
	private ImageConsumer imageProducer;
	private MouseHandler mouseHandler;
	private KeyHandler keyHandler;
	protected static ScaledWindow scaledWindow;
	private static BufferedImage game_image;
	private static Graphics2D g2dForGameImage;
	private BufferedImage directFramebufferImage;
	private int[] directFramebufferPixels;
	private int directFramebufferWidth;
	private int directFramebufferHeight;
	private BufferedImage renderer2DUiBaseImage;
	private int[] renderer2DUiBasePixels;
	private int renderer2DUiBaseWidth;
	private int renderer2DUiBaseHeight;

	public void seedInitialLoadingFrame() {
		applyConfiguredRenderSurfaceSize();
		presentLoadingFrame("Loading...", 0, 126);
	}

	public MouseHandler getMouseHandler() {
		return mouseHandler;
	}

	public KeyHandler getKeyHandler() {
		return keyHandler;
	}

	boolean isWorldEditorShortcutMode() {
		return mudclient != null && mudclient.worldEditorInterface != null
			&& mudclient.worldEditorInterface.isKeyboardShortcutMode();
	}

	private static int getClientMouseX(MouseEvent e) {
		return e.getX() - mudclient.screenOffsetX;
	}

	private static int getClientMouseY(MouseEvent e) {
		return e.getY() - mudclient.screenOffsetY;
	}

	private static final class CpuUsageSampler {
		private static final long SAMPLE_INTERVAL_NANOS = 500_000_000L;

		private final com.sun.management.OperatingSystemMXBean operatingSystemBean;
		private long lastWallNanos;
		private long lastProcessCpuNanos;
		private String lastProcessCpuPercent = "n/a";

		private CpuUsageSampler() {
			java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
			if (bean instanceof com.sun.management.OperatingSystemMXBean) {
				this.operatingSystemBean = (com.sun.management.OperatingSystemMXBean) bean;
				this.lastWallNanos = System.nanoTime();
				this.lastProcessCpuNanos = Math.max(0L, this.operatingSystemBean.getProcessCpuTime());
			} else {
				this.operatingSystemBean = null;
				this.lastWallNanos = 0L;
				this.lastProcessCpuNanos = 0L;
			}
		}

		private synchronized String sampleProcessCpuPercent() {
			if (operatingSystemBean == null) {
				return "n/a";
			}

			long now = System.nanoTime();
			if (now - lastWallNanos < SAMPLE_INTERVAL_NANOS) {
				return lastProcessCpuPercent;
			}

			long processCpuNanos = operatingSystemBean.getProcessCpuTime();
			if (processCpuNanos < 0L) {
				lastProcessCpuPercent = "n/a";
				lastWallNanos = now;
				return lastProcessCpuPercent;
			}

			long wallDelta = now - lastWallNanos;
			long cpuDelta = processCpuNanos - lastProcessCpuNanos;
			if (wallDelta > 0L && cpuDelta >= 0L) {
				lastProcessCpuPercent = formatPercent((cpuDelta * 100.0D) / wallDelta);
			}
			lastWallNanos = now;
			lastProcessCpuNanos = processCpuNanos;
			return lastProcessCpuPercent;
		}

		private static String formatPercent(double value) {
			if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0D) {
				return "n/a";
			}

			int rounded = (int) Math.round(Math.min(value, 9999.9D));
			return Integer.toString(rounded) + "%";
		}
	}

	private static final class MemoryUsageSampler {
		private static final long SAMPLE_INTERVAL_NANOS = 500_000_000L;

		private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
		private final java.util.List<BufferPoolMXBean> bufferPoolBeans =
			ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
		private final java.util.List<GarbageCollectorMXBean> garbageCollectorBeans =
			ManagementFactory.getGarbageCollectorMXBeans();
		private long lastSampleNanos;
		private long lastCollectionCount;
		private long lastCollectionTimeMillis;
		private MemoryUsageSnapshot lastSnapshot;

		private synchronized MemoryUsageSnapshot sample() {
			long now = System.nanoTime();
			if (lastSnapshot != null && now - lastSampleNanos < SAMPLE_INTERVAL_NANOS) {
				return lastSnapshot;
			}

			MemoryUsage heap = memoryBean.getHeapMemoryUsage();
			MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
			BufferPoolUsage direct = bufferPoolUsage("direct");
			BufferPoolUsage mapped = bufferPoolUsage("mapped");
			long collectionCount = totalCollectionCount();
			long collectionTimeMillis = totalCollectionTimeMillis();
			long recentCollectionCount = lastSnapshot == null || collectionCount < lastCollectionCount
				? 0L
				: collectionCount - lastCollectionCount;
			long recentCollectionTimeMillis = lastSnapshot == null || collectionTimeMillis < lastCollectionTimeMillis
				? 0L
				: collectionTimeMillis - lastCollectionTimeMillis;

			lastCollectionCount = collectionCount;
			lastCollectionTimeMillis = collectionTimeMillis;
			lastSampleNanos = now;
			lastSnapshot = new MemoryUsageSnapshot(
				"memory heap u/c/max " + formatMemoryUsage(heap)
					+ " | nonheap u/c/max " + formatMemoryUsage(nonHeap),
				"memory direct u/cap/count " + direct.summary()
					+ " | mapped " + mapped.summary()
					+ " | gc total/recent " + collectionCount + "/" + collectionTimeMillis
					+ "ms " + recentCollectionCount + "/" + recentCollectionTimeMillis + "ms");
			return lastSnapshot;
		}

		private BufferPoolUsage bufferPoolUsage(String name) {
			for (BufferPoolMXBean bean : bufferPoolBeans) {
				if (bean != null && name.equalsIgnoreCase(bean.getName())) {
					return new BufferPoolUsage(bean.getMemoryUsed(), bean.getTotalCapacity(), bean.getCount());
				}
			}
			return BufferPoolUsage.UNKNOWN;
		}

		private long totalCollectionCount() {
			long total = 0L;
			for (GarbageCollectorMXBean bean : garbageCollectorBeans) {
				long count = bean == null ? -1L : bean.getCollectionCount();
				if (count >= 0L) {
					total += count;
				}
			}
			return total;
		}

		private long totalCollectionTimeMillis() {
			long total = 0L;
			for (GarbageCollectorMXBean bean : garbageCollectorBeans) {
				long time = bean == null ? -1L : bean.getCollectionTime();
				if (time >= 0L) {
					total += time;
				}
			}
			return total;
		}

		private static String formatMemoryUsage(MemoryUsage usage) {
			if (usage == null) {
				return "n/a";
			}
			return formatMegabytes(usage.getUsed())
				+ "/" + formatMegabytes(usage.getCommitted())
				+ "/" + formatMegabytes(usage.getMax());
		}

		private static String formatMegabytes(long bytes) {
			if (bytes < 0L) {
				return "n/a";
			}
			if (bytes >= 1024L * 1024L * 1024L) {
				return String.format("%.2fg", bytes / (1024.0D * 1024.0D * 1024.0D));
			}
			return String.format("%.1fm", bytes / (1024.0D * 1024.0D));
		}
	}

	private static final class BufferPoolUsage {
		static final BufferPoolUsage UNKNOWN = new BufferPoolUsage(-1L, -1L, -1L);

		private final long memoryUsedBytes;
		private final long totalCapacityBytes;
		private final long count;

		private BufferPoolUsage(long memoryUsedBytes, long totalCapacityBytes, long count) {
			this.memoryUsedBytes = memoryUsedBytes;
			this.totalCapacityBytes = totalCapacityBytes;
			this.count = count;
		}

		private String summary() {
			return MemoryUsageSampler.formatMegabytes(memoryUsedBytes)
				+ "/" + MemoryUsageSampler.formatMegabytes(totalCapacityBytes)
				+ "/" + (count < 0L ? "n/a" : Long.toString(count));
		}
	}

	private static final class MemoryUsageSnapshot {
		private final String heapLine;
		private final String bufferAndGcLine;

		private MemoryUsageSnapshot(String heapLine, String bufferAndGcLine) {
			this.heapLine = heapLine;
			this.bufferAndGcLine = bufferAndGcLine;
		}
	}

	void addMouseClick(int button, int x, int y) {
		try {
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "e.Q(" + x + ',' + "dummy" + ',' + button + ',' + y + ')');
		}
	}

	private void drawCenteredString(Font var1, String str, int y, int x, Graphics g) {
		try {
			FontMetrics metrics = getFontMetrics(var1);
			g.setFont(var1);
			g.drawString(str, x - metrics.stringWidth(str) / 2, y + metrics.getHeight() / 4);
		} catch (RuntimeException var9) {
			throw GenUtil.makeThrowable(var9,
				"e.LE(" + (var1 != null ? "{...}" : "null") + ',' + (str != null ? "{...}" : "null") + ',' + y + ','
					+ true + ',' + x + ',' + (g != null ? "{...}" : "null") + ')');
		}
	}

	public final boolean drawLoading(int var1) {
		try {
			presentLoadingFrame("Loading...", 0, var1 ^ 103);
			Graphics var2 = this.getGraphics();
			if (var2 != null) {
				this.loadingGraphics = scaledWindow.getGraphics();
				this.loadingGraphics.translate(mudclient.screenOffsetX, mudclient.screenOffsetY);
				this.loadingGraphics.setColor(Color.black);
				this.loadingGraphics.fillRect(0, 0, this.width, this.height);
				this.drawLoadingScreen("Loading...", 0, var1 ^ 103);
				return true;
			}
			return game_image != null;
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "e.ME(" + var1 + ')');
		}
	}

	@Override
	public boolean isDisplayable() {
		return super.isDisplayable();
	}

	private void drawLoadingScreen(String state, int percent, int var3) {
		try {
			try {
				int x = (this.width - 281) / 2;
				int y = (this.height - 148) / 2;
				this.loadingGraphics.setColor(Color.black);
				this.loadingGraphics.fillRect(0, 0, this.width, this.height);
				if (!this.m_hb) this.loadingGraphics.drawImage(this.loadingLogo, x, y, this);

				x += 2;
				this.loadingPercent = percent;
				y += 90;
				this.loadingState = state;
				if (var3 <= 97) mouseHandler.mouseReleased(null);

				this.loadingGraphics.setColor(new Color(132, 132, 132));
				if (this.m_hb) this.loadingGraphics.setColor(new Color(220, 0, 0));

				this.loadingGraphics.drawRect(x - 2, y - 2, 280, 23);
				this.loadingGraphics.fillRect(x, y, percent * 277 / 100, 20);
				this.loadingGraphics.setColor(new Color(198, 198, 198));
				if (this.m_hb) this.loadingGraphics.setColor(new Color(255, 255, 255));

				this.drawCenteredString(this.loadingFont, state, 10 + y, 138 + x, this.loadingGraphics);

				if (!this.m_hb) {
					this.drawCenteredString(this.createdbyFont, "Powered by Open RSC", 30 + y,
						x + 138, this.loadingGraphics);
					this.drawCenteredString(this.createdbyFont, "We support open source development.", y + 44, x + 138,
						this.loadingGraphics);
				} else {
					this.loadingGraphics.setColor(new Color(132, 132, 152));
					this.drawCenteredString(this.copyrightFont2, "We support open source development.", this.height - 20,
						138 + x, this.loadingGraphics);
				}

				if (null != this.m_p) {
					this.loadingGraphics.setColor(Color.white);
					this.drawCenteredString(this.createdbyFont, this.m_p, y - 120, x + 138, this.loadingGraphics);
				}
			} catch (Exception ignored) {
			}
		} catch (RuntimeException var7) {
			throw GenUtil.makeThrowable(var7,
				"e.FE(" + (state != null ? "{...}" : "null") + ',' + percent + ',' + var3 + ')');
		}
	}

	private void presentLoadingFrame(String state, int percent, int var3) {
		ensureLoadingGameImage();
		if (g2dForGameImage == null) {
			return;
		}

		drawLoadingScreen(g2dForGameImage, state, percent, var3);
		scaledWindow.setGameImage(game_image);
	}

	private void ensureLoadingGameImage() {
		if (game_image != null
			&& game_image.getWidth() == this.width
			&& game_image.getHeight() == this.height
			&& g2dForGameImage != null) {
			return;
		}

		int imageType = ScaledWindow.getBufferedImageType();
		game_image = new BufferedImage(this.width, this.height, imageType);
		RenderTelemetry.recordImageAllocation("loading-game-image", this.width, this.height, imageType);
		g2dForGameImage = game_image.createGraphics();
	}

	private void drawLoadingScreen(Graphics g, String state, int percent, int var3) {
		int x = (this.width - 281) / 2;
		int y = (this.height - 148) / 2;
		g.setColor(Color.black);
		g.fillRect(0, 0, this.width, this.height);
		if (!this.m_hb && this.loadingLogo != null) {
			g.drawImage(this.loadingLogo, x, y, this);
		}

		x += 2;
		this.loadingPercent = percent;
		y += 90;
		this.loadingState = state;
		if (var3 <= 97) {
			mouseHandler.mouseReleased(null);
		}

		g.setColor(new Color(132, 132, 132));
		if (this.m_hb) {
			g.setColor(new Color(220, 0, 0));
		}

		g.drawRect(x - 2, y - 2, 280, 23);
		g.fillRect(x, y, percent * 277 / 100, 20);
		g.setColor(new Color(198, 198, 198));
		if (this.m_hb) {
			g.setColor(new Color(255, 255, 255));
		}

		this.drawCenteredString(this.loadingFont, state, 10 + y, 138 + x, g);

		if (!this.m_hb) {
			this.drawCenteredString(this.createdbyFont, "Powered by Open RSC", 30 + y, x + 138, g);
			this.drawCenteredString(this.createdbyFont, "We support open source development.", y + 44, x + 138, g);
		} else {
			g.setColor(new Color(132, 132, 152));
			this.drawCenteredString(this.copyrightFont2, "We support open source development.", this.height - 20, 138 + x, g);
		}

		if (this.m_p != null) {
			g.setColor(Color.white);
			this.drawCenteredString(this.createdbyFont, this.m_p, y - 120, x + 138, g);
		}
	}

	@Override
	public final void paint(Graphics var1) {
		try {
			if (mudclient != null) {
				mudclient.rendering = true;
				if (mudclient.getGameState() == 2 && this.loadingLogo != null)
					this.drawLoadingScreen(this.loadingState, this.loadingPercent, 126);
			}
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "e.paint(" + (var1 != null ? "{...}" : "null") + ')');
		}
	}

	boolean reposition() {
		return false;
	}

	public final void showLoadingProgress(int percent, String state) {
		try {
			try {
				int x = (this.width - 281) / 2;
				x += 2;
				int y = (this.height - 148) / 2;
				this.loadingState = state;
				this.loadingPercent = percent;
				y += 90;
				int progress = percent * 277 / 100;
				this.loadingGraphics.setColor(new Color(132, 132, 132));
				if (this.m_hb) this.loadingGraphics.setColor(new Color(220, 0, 0));
				this.loadingGraphics.fillRect(x, y, progress, 20);
				this.loadingGraphics.setColor(Color.black);
				this.loadingGraphics.fillRect(progress + x, y, 277 - progress, 20);
				this.loadingGraphics.setColor(new Color(198, 198, 198));
				if (this.m_hb) this.loadingGraphics.setColor(new Color(255, 255, 255));
				this.drawCenteredString(this.loadingFont, state, 10 + y, 138 + x, this.loadingGraphics);
			} catch (Exception ignored) {
			}
			presentLoadingFrame(state, percent, 126);
		} catch (RuntimeException var8) {
			throw GenUtil.makeThrowable(var8, "e.EE(" + percent + ',' + (state != null ? "{...}" : "null") + ')');
		}
	}

	@Override
	public final void init() {
		try {
			mudclient = new mudclient(this);
			mudclient.packetHandler = new PacketHandler(mudclient);
			loadLogo();

			mouseHandler = new MouseHandler();
			keyHandler = new KeyHandler();

			this.addMouseListener(mouseHandler);
			this.addMouseMotionListener(mouseHandler);
			this.addKeyListener(keyHandler);
			this.setFocusTraversalKeysEnabled(false);
			this.addComponentListener(this);
			this.addMouseWheelListener(mouseHandler);
		} catch (RuntimeException var2) {
			throw GenUtil.makeThrowable(var2, "client.init()");
		}
	}

	public void loadLogo() {
		// Leaving this blank
	}

	private void startApplet() {
		try {
			System.out.println("Started applet");
			applyConfiguredRenderSurfaceSize();
			mudclient.startMainThread();
		} catch (RuntimeException var12) {
			throw GenUtil.makeThrowable(var12, "e.OE(" + 346 + ',' + Config.CLIENT_VERSION + ',' + 12 + ',' + 512 + ')');
		}
		try {
			// Don't load Discord on ARM
			if (!System.getProperty("os.arch").contains("aarch64")) {
				Discord.InitalizeDiscord();
			}
		} catch (Exception e) { }
	}

	@Override
	public final void stop() {
		try {
			stopSoundPlayer();
			try {
				mudclient.clientBaseThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				System.exit(0);
			}
		} catch (RuntimeException var2) {
			throw GenUtil.makeThrowable(var2, "e.stop()");
		}
	}

	@Override
	public final void update(Graphics var1) {
		try {
			this.paint(var1);
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "e.update(" + (var1 != null ? "{...}" : "null") + ')');
		}
	}

	private void updateControlShiftState(InputEvent var1) {
		try {
			int mod = var1.getModifiers();
			if (mudclient == null)
				return;
			mudclient.controlPressed = (mod & Event.CTRL_MASK) != 0;
			mudclient.shiftPressed = (mod & Event.SHIFT_MASK) != 0;
		} catch (RuntimeException e) {
			throw GenUtil.makeThrowable(e, "e.SE(" + (var1 != null ? "{...}" : "null") + ',' + "dummy" + ')');
		}
	}

	public final void start() {
		try {
			if (mudclient.threadState >= 0) {
				mudclient.threadState = 0;
			}
			startApplet();
		} catch (RuntimeException var2) {
			throw GenUtil.makeThrowable(var2, "e.start()");
		}
	}

	@Override
	public void componentShown(ComponentEvent e) {
	}

	void resizeMudclient(int width, int height) {
		mudclient.resizeWidth = width;
		mudclient.resizeHeight = height;
	}

	void resetArrowKeys() {
		mudclient.keyUp = false;
		mudclient.keyDown = false;
		mudclient.keyLeft = false;
		mudclient.keyRight = false;
	}

	@Override
	public void componentResized(ComponentEvent e) {
		if (ScaledWindow.isOpenGLPrimaryWindowEnabled()) {
			return;
		}

		mudclient.resizeWidth = e.getComponent().getWidth();
		mudclient.resizeHeight = e.getComponent().getHeight();
	}

	@Override
	public void componentMoved(ComponentEvent e) {
	}

	@Override
	public void componentHidden(ComponentEvent e) {
	}

	@Override
	public void initListeners() {
	}

	@Override
	public void crashed() {
	}

	@Override
	public void drawLoadingError() {
		Graphics g = this.getGraphics();
		if (g != null) {
			g.translate(mudclient.screenOffsetX, mudclient.screenOffsetY);
			g.setColor(Color.black);
			g.fillRect(0, 0, 512, 356);
			g.setFont(new Font("Helvetica", 1, 16));
			g.setColor(Color.yellow);
			byte var3 = 35;
			g.drawString("Sorry, an error has occured whilst loading " + Config.getServerNameWelcome(), 30, var3);
			g.setColor(Color.white);
			int var6 = var3 + 50;
			g.drawString("To fix this try the following (in order):", 30, var6);
			g.setColor(Color.white);
			var6 += 50;
			g.setFont(new Font("Helvetica", 1, 12));
			g.drawString("1: Try closing ALL open web-browser windows, and reloading", 30, var6);
			var6 += 30;
			g.drawString("2: Try clearing your web-browsers cache from tools->internet options", 30, var6);
			var6 += 30;
			g.drawString("3: Try using a different game-world", 30, var6);
			var6 += 30;
			g.drawString("4: Try rebooting your computer", 30, var6);
			var6 += 30;
			g.drawString("5: Try selecting a different version of Java from the play-game menu", 30, var6);
		}
	}

	@Override
	public void drawOutOfMemoryError() {
		Graphics g = this.getGraphics();
		if (null != g) {
			g.translate(mudclient.screenOffsetX, mudclient.screenOffsetY);
			g.setColor(Color.black);
			g.fillRect(0, 0, 512, 356);
			g.setFont(new Font("Helvetica", 1, 20));
			g.setColor(Color.white);
			g.drawString("Error - out of memory!", 50, 50);
			g.drawString("Close ALL unnecessary programs", 50, 100);
			g.drawString("and windows before loading the game", 50, 150);
			g.drawString(Config.getServerName() + " needs about 48meg of spare RAM", 50, 200);
		}
	}

	@Override
	public void drawTextBox(String line2, byte var2, String line1) {
		Graphics g = this.getGraphics();
		if (null != g) {
			g.translate(mudclient.screenOffsetX, mudclient.screenOffsetY);
			Font font = new Font("Helvetica", 1, 15);
			short width = 512;
			g.setColor(Color.black);
			short height = 344;
			g.fillRect(width / 2 - 140, height / 2 - 25, 280, 50);
			g.setColor(Color.white);
			g.drawRect(width / 2 - 140, height / 2 - 25, 280, 50);
			this.drawCenteredString(font, line1, height / 2 - 10, width / 2, g);
			this.drawCenteredString(font, line2, 10 + height / 2, width / 2, g);
		}
	}

	@Override
	public void initGraphics() {
		int width = mudclient.getSurface().width2;
		int height = mudclient.getSurface().height2;
		if (width > 1 && height > 1) {
			if (DIRECT_FRAMEBUFFER_ENABLED) {
				bindDirectFramebuffer(width, height);
				return;
			}

			this.imageModel = new DirectColorModel(32, 16711680, '\uff00', 255);
			this.backingImage = createImage(this);
			this.commitToImage(true);
			prepareImage(this.backingImage, this);
			this.commitToImage(true);
			prepareImage(this.backingImage, this);
			this.commitToImage(true);
			prepareImage(this.backingImage, this);
		}
	}

	private synchronized void commitToImage(boolean var1) {
		try {
			if (null != this.imageProducer) {
				this.imageProducer.setPixels(0, 0, mudclient.getSurface().width2, mudclient.getSurface().height2,
					this.imageModel, mudclient.getSurface().pixelData, 0, mudclient.getSurface().width2);
				this.imageProducer.imageComplete(2);
			}
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "ua.CA(" + true + ')');
		}
	}

	@Override
	public void addConsumer(ImageConsumer arg0) {
		try {
			this.imageProducer = arg0;
			arg0.setDimensions(mudclient.getSurface().width2, mudclient.getSurface().height2);
			arg0.setProperties(null);
			arg0.setColorModel(this.imageModel);
			arg0.setHints(14);
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "ua.addConsumer(" + (arg0 != null ? "{...}" : "null") + ')');
		}
	}

	@Override
	public boolean isConsumer(ImageConsumer arg0) {
		return this.imageProducer == arg0;
	}

	@Override
	public void removeConsumer(ImageConsumer arg0) {
		if (this.imageProducer == arg0) this.imageProducer = null;
	}

	@Override
	public void requestTopDownLeftRightResend(ImageConsumer arg0) {
		try {
			System.out.println("TDLR");
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3,
				"ua.requestTopDownLeftRightResend(" + (arg0 != null ? "{...}" : "null") + ')');
		}
	}

	@Override
	public void startProduction(ImageConsumer arg0) {
		this.addConsumer(arg0);
	}

	public final void draw() {
		if (mudclient != null
			&& mudclient.shouldRetainLastPresentedFrame()) {
			RenderTelemetry.recordBoundaryPresentationRetention();
			return;
		}
		boolean telemetryEnabled = RenderTelemetry.isEnabled();
		long frameStart = RenderTelemetry.now();
		long commitNanos = 0L;

		long scalarResizeNanos = 0L;
		// Re-scale when needed
		if (LegacySoftwareScalingSettings.hasPendingScalarChange()) {
			long resizeStart = RenderTelemetry.now();
			float scalar = LegacySoftwareScalingSettings.getPendingRenderingScalar();
			updateRenderingScalarAndResize(scalar, mudclient.getGameWidth(), mudclient.getGameHeight());
			scalarResizeNanos = RenderTelemetry.elapsedSince(resizeStart);
		}

		long backingCopyNanos = 0L;
		BufferedImage frameImage;
		String framePath;
		if (DIRECT_FRAMEBUFFER_ENABLED) {
			frameImage = getDirectFramebufferImage();
			framePath = "direct-framebuffer";
		} else {
			long commitStart = RenderTelemetry.now();
			this.commitToImage(true);
			commitNanos = RenderTelemetry.elapsedSince(commitStart);

			long backingCopyStart = RenderTelemetry.now();
			g2dForGameImage.drawImage(this.backingImage, 0, 0, null);
			backingCopyNanos = RenderTelemetry.elapsedSince(backingCopyStart);

			frameImage = game_image;
			framePath = "legacy-image-producer";
		}

		// Forward the image to be drawn by ScaledWindow.java
		String[] rendererDebugOverlayLines = rendererDebugOverlayLines(frameImage);
		if (!ScaledWindow.isOpenGLPrimaryWindowEnabled()) {
			drawRendererDebugOverlay(frameImage, rendererDebugOverlayLines);
		}
		BufferedImage renderer2DUiBaseImage = getRenderer2DUiBaseImage();
		Renderer2DFrame renderer2DFrame = mudclient.getSurface().consumeRenderer2DFrame();
		Renderer3DFrame renderer3DFrame = getRenderer3DFrame();
		long presentStart = RenderTelemetry.now();
		scaledWindow.setGameImage(
			frameImage,
			renderer2DFrame,
			renderer2DUiBaseImage,
			renderer3DFrame,
			rendererDebugOverlayLines);
		long presentNanos = RenderTelemetry.elapsedSince(presentStart);

		if (telemetryEnabled) {
			RenderTelemetry.recordFrame(
				RenderTelemetry.elapsedSince(frameStart),
				commitNanos,
				scalarResizeNanos,
				backingCopyNanos,
				presentNanos,
				frameImage.getWidth(),
				frameImage.getHeight(),
				LegacySoftwareScalingSettings.getRenderingScalar(),
				LegacySoftwareScalingSettings.getScalingAlgorithm(),
				framePath);
		}
	}

	/** Updates the rendering scalar and resizes the window accordingly */
	private static void updateRenderingScalarAndResize(float scalar, int newWidth, int newHeight) {
		int imageType = ScaledWindow.getBufferedImageType();

		// Reset the game image with the current type to ensure that affineOp
		// scaling will always have matching source and destination types
		if (!DIRECT_FRAMEBUFFER_ENABLED) {
			game_image = new BufferedImage(newWidth, newHeight, imageType);
			RenderTelemetry.recordImageAllocation("game-image-rescale", newWidth, newHeight, imageType);
		}
		LegacySoftwareScalingSettings.applyPendingScalar(scalar);

		// Resize window only after it has begun rendering the game image,
		// (ie. not the loading screen)
		if (!ScaledWindow.isOpenGLPrimaryWindowEnabled() && scaledWindow.isViewportLoaded()) {
			scaledWindow.resizeWindowToScalar();
		}
	}

	@Override
	public void close() {
		stop();
	}

	@Override
	public String getCacheLocation() {
		return "../OpenRSC/";
	}

	@Override
	public Sprite getBattery(int level) {
		// This would be needed to be implemented if was desired to display Battery Status Icon
		return null;
	}

	@Override
	public int getBatteryPercent() {
		// This would be needed to be implemented if was desired to display Battery Percent
		return 100;
	}

	@Override
	public boolean getBatteryCharging() {
		// This would be needed to be implemented if was desired to display Battery Charging
		return false;
	}

	@Override
	public Sprite getConnectivity(int level) {
		// This would be needed to be implemented if was desired to display Network Connectivity Status Icon
		return null;
	}

	@Override
	public String getConnectivityText() {
		// This would be needed to be implemented if was desired to display Network Connectivity Status Text
		return null;
	}

	@Override
	public void resized() {
		int newWidth = mudclient.getSurface().width2;
		int newHeight = mudclient.getSurface().height2;
		this.width = newWidth;
		this.height = newHeight;

		if (DIRECT_FRAMEBUFFER_ENABLED) {
			bindDirectFramebuffer(newWidth, newHeight);
			return;
		}

		if (imageProducer != null) {
			imageProducer.setDimensions(newWidth, newHeight);
		}
		initGraphics();

		game_image = new BufferedImage(newWidth, newHeight, ScaledWindow.getBufferedImageType());
		RenderTelemetry.recordImageAllocation("game-image-resize", newWidth, newHeight, ScaledWindow.getBufferedImageType());
		g2dForGameImage = game_image.createGraphics();
	}

	private void applyConfiguredRenderSurfaceSize() {
		this.width = RenderSurfaceSettings.getWidth();
		this.height = RenderSurfaceSettings.getHeight();
	}

	private BufferedImage getDirectFramebufferImage() {
		int width = mudclient.getSurface().width2;
		int height = mudclient.getSurface().height2;
		int[] pixels = mudclient.getSurface().pixelData;

		if (directFramebufferImage == null
			|| directFramebufferPixels != pixels
			|| directFramebufferWidth != width
			|| directFramebufferHeight != height) {
			bindDirectFramebuffer(width, height);
		}

		return directFramebufferImage;
	}

	private BufferedImage getRenderer2DUiBaseImage() {
		if (!Renderer2DSettings.canPresentUiBaseFrame() || !mudclient.getSurface().hasRenderer2DUiBaseFrame()) {
			return null;
		}

		int width = mudclient.getSurface().getRenderer2DUiBaseWidth();
		int height = mudclient.getSurface().getRenderer2DUiBaseHeight();
		int[] pixels = mudclient.getSurface().getRenderer2DUiBasePixels();
		if (pixels == null || pixels.length < width * height) {
			return null;
		}

		if (renderer2DUiBaseImage == null
			|| renderer2DUiBasePixels != pixels
			|| renderer2DUiBaseWidth != width
			|| renderer2DUiBaseHeight != height) {
			renderer2DUiBaseImage = createDirectFramebufferImage(width, height, pixels);
			renderer2DUiBasePixels = pixels;
			renderer2DUiBaseWidth = width;
			renderer2DUiBaseHeight = height;
		}
		return renderer2DUiBaseImage;
	}

	private Renderer3DFrame getRenderer3DFrame() {
		if (mudclient == null || !mudclient.isRenderer3DWorldReady() || mudclient.getScene() == null) {
			return null;
		}

		return mudclient.getScene().getRenderer3DFrame();
	}

	private void bindDirectFramebuffer(int width, int height) {
		int[] pixels = mudclient.getSurface().pixelData;
		directFramebufferImage = createDirectFramebufferImage(width, height, pixels);
		directFramebufferPixels = pixels;
		directFramebufferWidth = width;
		directFramebufferHeight = height;
		game_image = directFramebufferImage;
		g2dForGameImage = null;
	}

	private static BufferedImage createDirectFramebufferImage(int width, int height, int[] pixels) {
		if (pixels == null || pixels.length < width * height) {
			throw new IllegalArgumentException("Cannot bind direct framebuffer to an undersized pixel buffer");
		}

		int redMask = 0x00FF0000;
		int greenMask = 0x0000FF00;
		int blueMask = 0x000000FF;
		DirectColorModel colorModel = new DirectColorModel(32, redMask, greenMask, blueMask);
		DataBufferInt dataBuffer = new DataBufferInt(pixels, pixels.length);
		SinglePixelPackedSampleModel sampleModel =
			new SinglePixelPackedSampleModel(
				DataBuffer.TYPE_INT,
				width,
				height,
				width,
				new int[] {redMask, greenMask, blueMask});
		WritableRaster raster = Raster.createWritableRaster(sampleModel, dataBuffer, new Point(0, 0));
		return new BufferedImage(colorModel, raster, false, null);
	}

	private String[] rendererDebugOverlayLines(BufferedImage frameImage) {
		if (!RendererDebugSettings.isOverlayEnabled() || frameImage == null) {
			return null;
		}

		RenderTelemetry.Snapshot telemetry = RenderTelemetry.snapshot();
		return overlayLines(frameImage, telemetry);
	}

	private void drawRendererDebugOverlay(BufferedImage frameImage, String[] lines) {
		if (lines == null || lines.length == 0 || frameImage == null) {
			return;
		}

		Graphics2D g = frameImage.createGraphics();
		try {
			g.setFont(new Font("Monospaced", Font.PLAIN, 13));
			FontMetrics metrics = g.getFontMetrics();
			int maxWidth = 0;
			for (String line : lines) {
				maxWidth = Math.max(maxWidth, metrics.stringWidth(line));
			}

			int padding = 6;
			int lineHeight = metrics.getHeight();
			int boxWidth = Math.min(frameImage.getWidth(), maxWidth + padding * 2);
			int boxHeight = Math.min(frameImage.getHeight(), lines.length * lineHeight + padding * 2);

			g.setComposite(AlphaComposite.SrcOver.derive(0.76f));
			g.setColor(Color.black);
			g.fillRect(6, 6, boxWidth, boxHeight);
			g.setComposite(AlphaComposite.SrcOver);
			g.setColor(new Color(70, 170, 255));
			g.drawRect(6, 6, boxWidth, boxHeight);

			int y = 6 + padding + metrics.getAscent();
			for (int i = 0; i < lines.length; i++) {
				g.setColor(i == 0 ? new Color(190, 230, 255) : Color.white);
				g.drawString(lines[i], 6 + padding, y);
				y += lineHeight;
			}
		} finally {
			g.dispose();
		}
	}

	private String[] overlayLines(BufferedImage frameImage, RenderTelemetry.Snapshot telemetry) {
		boolean openGLPrimaryWindow = ScaledWindow.isOpenGLPrimaryWindowEnabled();
		String cpuUsage = CPU_USAGE_SAMPLER.sampleProcessCpuPercent();
		MemoryUsageSnapshot memoryUsage = MEMORY_USAGE_SAMPLER.sample();
		String displayedFps = openGLPrimaryWindow
			? RenderTelemetry.observedOpenGLFps(mudclient.getCurrentFPS())
			: String.valueOf(mudclient.getCurrentFPS());
		String rendererLine = openGLPrimaryWindow
			? "renderer " + RendererProfileSettings.getMode().id
				+ " | aspect " + RenderSurfaceSettings.getDebugAspectLabel()
				+ " | fps " + displayedFps
				+ " | proc cpu " + cpuUsage
			: "scale " + LegacySoftwareScalingSettings.getRenderingScalar() + " "
				+ LegacySoftwareScalingSettings.getScalingAlgorithm()
				+ " | fps " + displayedFps
				+ " | proc cpu " + cpuUsage;
		String openGLLine = telemetry.enabled
			? "opengl frames/dropped " + telemetry.openGLFrames
				+ "/" + telemetry.openGLDroppedFrames
				+ " | render " + telemetry.openGLRenderAverageMs + "ms"
			: "opengl telemetry unavailable";
		String graphicsLine = "lighting " + RendererLightingSettings.getMode().id
			+ " | geometry " + RendererGeometrySettings.getMode().id
			+ " | fog " + RendererFogSettings.getMode().id
			+ " | tone " + RendererDayNightCycle.debugSummary();
		String shadingLine = RendererReliefSettings.debugSummary()
			+ " | " + RendererColorDiagnosticSettings.debugSummary();
		String tuningKeysLine = ClientHotkeySettings.showDeveloperFunctionKeyHints()
			? "F7 t/o " + RendererReliefSettings.getTerrainLevel() + "/"
				+ RendererReliefSettings.getObjectLevel()
				+ " | F8 d/c " + RendererColorDiagnosticSettings.getDimnessLevel() + "/"
				+ RendererColorDiagnosticSettings.getContrastLevel()
				+ " | F10 g/s " + RendererColorDiagnosticSettings.getGammaLevel() + "/"
				+ RendererColorDiagnosticSettings.getSaturationLevel()
			: "";
		PacketHandler activePacketHandler = mudclient == null ? null : mudclient.packetHandler;
		String[] sceneBaselineLines = activePacketHandler == null
			? new String[] { "sceneBase unavailable", "", "" }
			: activePacketHandler.getSceneBaselineDebugSummaryLines();
		String[] movementSnapshotLines = activePacketHandler == null
			? new String[] { "move snap unavailable", "move cache unavailable" }
			: activePacketHandler.getMovementSnapshotDebugSummaryLines();
		String layeredTerrainDeliveryLine = activePacketHandler == null
			? "layer terrain unavailable"
			: activePacketHandler.getLayeredTerrainDeliveryDebugSummaryLine();
		if (RendererDebugSettings.getMode() == RendererDebugSettings.Mode.SIMPLE) {
			return new String[] {
				"Renderer v2 Perf HUD",
				rendererLine,
				openGLLine,
				graphicsLine,
				shadingLine,
				tuningKeysLine,
				"Ctrl+F6 expanded"
			};
		}
		return new String[] {
			"Renderer v2 Perf HUD",
			rendererLine,
			graphicsLine,
			shadingLine,
			tuningKeysLine,
			memoryUsage.heapLine,
			memoryUsage.bufferAndGcLine,
			telemetry.enabled
				? "frame avg/max " + telemetry.frameAverageMs + "/" + telemetry.frameMaxMs
					+ "ms | scene " + telemetry.sceneAverageMs
					+ "ms | present " + telemetry.setImageAverageMs + "ms"
				: "telemetry disabled",
			telemetry.enabled
				? "recent frame/scene/gl " + telemetry.recentFrameSummary
				: "",
			telemetry.enabled
				? "gl frames/dropped " + telemetry.openGLFrames
					+ "/" + telemetry.openGLDroppedFrames + " | snap/up/render "
					+ telemetry.recentOpenGLTimingSummary
				: "",
			telemetry.enabled
				? "world load avg/max/recent " + RenderTelemetry.worldSectionLoadSummary()
				: "",
			telemetry.enabled
				? "loop recent total/sleep/update/repo/draw " + telemetry.recentClientLoopSummary
				: "",
			telemetry.enabled
				? "chunks c/t " + telemetry.openGLWorldChunkAverage
					+ "/" + telemetry.openGLWorldChunkTriangleAverage
					+ " | vis c/d/cull " + telemetry.openGLWorldChunkVisibilityAverage
				: "",
			telemetry.enabled
				? "material families " + RenderTelemetry.worldMaterialFamilySummary()
				: "",
			telemetry.enabled
				? "chunk req/up/reuse/evict " + telemetry.openGLWorldChunkRequestedAverage
					+ "/" + telemetry.openGLWorldChunkUploadAverage
					+ "/" + telemetry.openGLWorldChunkReuseAverage
					+ "/" + telemetry.openGLWorldChunkEvictAverage
					+ " | reason " + telemetry.openGLWorldChunkUploadReason
				: "",
			telemetry.enabled
				? "resident req/active/fallback " + telemetry.openGLResidentChunkReplacementRequestedAverage
					+ "/" + telemetry.openGLResidentChunkReplacementActiveAverage
					+ "/" + telemetry.openGLResidentChunkReplacementFallbackAverage
					+ " | reason " + telemetry.openGLResidentChunkReplacementReason
				: "",
			telemetry.enabled
				? "shadow mask build/upload " + telemetry.openGLRemasterShadowMaskTimingAverageMs
					+ "ms | cache " + telemetry.openGLRemasterShadowMaskCacheAverage
					+ " | reason " + telemetry.openGLRemasterShadowMaskReason
				: "",
			telemetry.enabled
				? "world split chunk/proj/chdraw " + telemetry.openGLWorldChunkUploadPhaseAverageMs
					+ "/" + telemetry.openGLWorldProjectedMeshPhaseAverageMs
					+ "/" + telemetry.openGLWorldChunkDrawPhaseAverageMs + "ms"
				: "",
			telemetry.enabled
				? "entity c/d/cull " + telemetry.openGLWorldEntityVisibilityAverage
					+ " | sprite cap/static/vis " + telemetry.spriteOverlayCapturedAverage
					+ "/" + telemetry.spriteOverlayStaticReplayAverage
					+ "/" + telemetry.spriteOverlayVisibleReplayAverage
					+ " | owner exact/fallback/unmatched " + RenderTelemetry.worldSpriteOwnershipSummary()
				: "",
			layeredTerrainDeliveryLine,
			sceneBaselineLines[0],
			movementSnapshotLines[0],
			telemetry.enabled
				? "2d cap cur/max/drop@limit " + RenderTelemetry.renderer2DCommandLimitSummary()
				: "",
			telemetry.enabled
				? "world faces t/w/r/go/wo/o " + telemetry.worldGeometryTerrainFaceAverage
					+ "/" + telemetry.worldGeometryWallFaceAverage
					+ "/" + telemetry.worldGeometryRoofFaceAverage
					+ "/" + telemetry.worldGeometryGameObjectFaceAverage
					+ "/" + telemetry.worldGeometryWallObjectFaceAverage
					+ "/" + telemetry.worldGeometryOtherFaceAverage
					+ " | depth f/t/p " + telemetry.worldDepthFaceAverage
					+ "/" + telemetry.worldDepthTriangleAverage
					+ "/" + telemetry.worldDepthPixelWriteAverage
					+ " | c/a/r " + telemetry.worldDepthCullAverage
				: "",
			"F6 closes debug | Ctrl+F6 simple"
		};
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

	@Override
	public Sprite getSpriteFromByteArray(ByteArrayInputStream byteArrayInputStream) {
		try {
			BufferedImage image = ImageIO.read(byteArrayInputStream);
			int captchaWidth = image.getWidth();
			int captchaHeight = image.getHeight();

			int[] pixels = new int[image.getWidth() * image.getHeight()];
			for (int y = 0; y < image.getHeight(); y++)
				for (int x = 0; x < image.getWidth(); x++) {
					int rgb = image.getRGB(x, y);
					pixels[x + y * image.getWidth()] = rgb;
				}

			Sprite sprite = new Sprite(pixels, captchaWidth, captchaHeight);
			sprite.setSomething(captchaWidth, captchaHeight);
			sprite.setShift(0, 0);
			sprite.setRequiresShift(false);
			return sprite;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void drawKeyboard() {
	}

	public void closeKeyboard() {
	}

	@Override
	public void playSound(byte[] soundData, int offset, int dataLength) {
		// Maintained mobile compatibility hook. Desktop playback uses soundPlayer.
	}

	@Override
	public void stopSoundPlayer() {
		soundPlayer.stopAllSounds();
	}

	public void setTitle(String title) {
	}

	public void setIconImage(String serverName) {

	}

	public class MouseHandler implements MouseListener, MouseMotionListener, MouseWheelListener {
		private final DesktopMiddleMouseOrbit middleMouseOrbit = new DesktopMiddleMouseOrbit();

		@Override
		public final void mouseClicked(MouseEvent var1) {
			try {
				updateControlShiftState(var1);
				if (var1.getButton() == MouseEvent.BUTTON2) {
					var1.consume();
				}
			} catch (RuntimeException var3) {
				throw GenUtil.makeThrowable(var3, "e.mouseClicked(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}

		@Override
		public final synchronized void mousePressed(MouseEvent var1) {
			try {
				updateControlShiftState(var1);
				int clientMouseX = getClientMouseX(var1);
				int clientMouseY = getClientMouseY(var1);
				if (middleMouseOrbit.begin(var1.getButton(), clientMouseX, clientMouseY)) {
					mudclient.mouseX = clientMouseX;
					mudclient.mouseY = clientMouseY;
					mudclient.mouseLastProcessedX = clientMouseX;
					mudclient.mouseLastProcessedY = clientMouseY;
					mudclient.currentMouseButtonDown = 0;
					var1.consume();
					return;
				}
				mudclient.mouseX = clientMouseX;
				mudclient.mouseY = clientMouseY;

				if (!SwingUtilities.isRightMouseButton(var1)) mudclient.currentMouseButtonDown = 1;
				else mudclient.currentMouseButtonDown = 2;

				mudclient.lastMouseButtonDown = mudclient.currentMouseButtonDown;
				mudclient.lastMouseAction = 0;
				mudclient.addMouseClick(mudclient.currentMouseButtonDown, mudclient.mouseX, mudclient.mouseY);
			} catch (RuntimeException var3) {
				throw GenUtil.makeThrowable(var3, "e.mousePressed(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}

		@Override
		public final synchronized void mouseReleased(MouseEvent var1) {
			try {
				if (var1 == null) {
					middleMouseOrbit.cancel();
					mudclient.mouseLastProcessedX = 0;
					mudclient.mouseLastProcessedY = 0;
					mudclient.currentMouseButtonDown = 0;
					return;
				}
				updateControlShiftState(var1);
				mudclient.mouseX = getClientMouseX(var1);
				mudclient.mouseY = getClientMouseY(var1);
				if (middleMouseOrbit.end(var1.getButton())) {
					mudclient.mouseLastProcessedX = 0;
					mudclient.mouseLastProcessedY = 0;
					mudclient.currentMouseButtonDown = 0;
					var1.consume();
					return;
				}
				mudclient.currentMouseButtonDown = 0;
			} catch (RuntimeException var3) {
				throw GenUtil.makeThrowable(var3, "e.mouseReleased(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}

		@Override
		public final void mouseEntered(MouseEvent var1) {
			try {
				updateControlShiftState(var1);
			} catch (RuntimeException var3) {
				throw GenUtil.makeThrowable(var3, "e.mouseEntered(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}

		@Override
		public final void mouseExited(MouseEvent var1) {
			try {
				updateControlShiftState(var1);
				if (middleMouseOrbit.isActive()) {
					mudclient.currentMouseButtonDown = 0;
				}
			} catch (RuntimeException var3) {
				throw GenUtil.makeThrowable(var3, "e.mouseExited(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}

		@Override
		public final synchronized void mouseDragged(MouseEvent var1) {
			try {
				updateControlShiftState(var1);
				mudclient.mouseX = getClientMouseX(var1);
				mudclient.mouseY = getClientMouseY(var1);

				if (middleMouseOrbit.update(mudclient.mouseX, mudclient.mouseY)) {
					applyMiddleMouseOrbit(middleMouseOrbit.getDeltaX(), middleMouseOrbit.getDeltaY());
					mudclient.mouseLastProcessedX = mudclient.mouseX;
					mudclient.mouseLastProcessedY = mudclient.mouseY;
					mudclient.currentMouseButtonDown = 0;
					var1.consume();
					return;
				}
				if (SwingUtilities.isRightMouseButton(var1)) mudclient.currentMouseButtonDown = 2;
				else mudclient.currentMouseButtonDown = 1;
			} catch (RuntimeException var3) {
				throw GenUtil.makeThrowable(var3, "e.mouseDragged(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}

		private void applyMiddleMouseOrbit(int deltaX, int deltaY) {
			if (deltaY != 0) {
				applyMiddleMouseVerticalDrag(deltaY);
			}

			int horizontalDistance = deltaX / 2;
			if (horizontalDistance == 0) {
				return;
			}

			// "Unset" retains normal desktop orbit; only mode 2 inverts yaw.
			int direction = osConfig.C_SWIPE_TO_ROTATE_MODE == 2 ? -1 : 1;
			if (!mudclient.getOptionCameraModeAuto()) {
				float viewportScale = getWidth() / (float) mudclient.getGameWidth();
				if (viewportScale <= 0.0F) {
					viewportScale = 1.0F;
				}
				float clientDistance = horizontalDistance / viewportScale;
				mudclient.cameraRotation = 255 & mudclient.cameraRotation
					+ (int) (direction * clientDistance);
			} else if (direction * horizontalDistance < 0) {
				mudclient.keyLeft = true;
			} else {
				mudclient.keyRight = true;
			}
		}

		private void applyMiddleMouseVerticalDrag(int deltaY) {
			if (mudclient.isInFirstPersonView() || DesktopMiddleMouseSettings.usesTilt()) {
				// Pitch limits and the third-person tilt option are owned by mudclient.
				mudclient.adjustCameraPitch(-deltaY);
				return;
			}

			int verticalDistance = deltaY / 2;
			if (verticalDistance == 0
				|| !(S_ZOOM_VIEW_TOGGLE || mudclient.getLocalPlayer().isStaff())) {
				return;
			}
			int direction = osConfig.C_SWIPE_TO_ZOOM_MODE == 2 ? -1 : 1;
			mudclient.adjustCameraZoomSetting(direction * verticalDistance);
		}

		@Override
		public final synchronized void mouseMoved(MouseEvent var1) {
			try {
				updateControlShiftState(var1);
				mudclient.mouseX = var1.getX() - mudclient.screenOffsetX;
				mudclient.mouseY = var1.getY() - mudclient.screenOffsetY;
				mudclient.lastMouseAction = 0;
				mudclient.currentMouseButtonDown = 0;
			} catch (RuntimeException var3) {
				throw GenUtil.makeThrowable(var3, "e.mouseMoved(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}

		@Override
		public final synchronized void mouseWheelMoved(MouseWheelEvent e) {
			updateControlShiftState(e);

			int mouseX = getClientMouseX(e);
			int mouseY = getClientMouseY(e);
			boolean touchedMessagePanelArea = mudclient.getGameHeight() - mouseY <= MESSAGE_WHEEL_SCROLL_AREA_HEIGHT;

			boolean scrollableMessagePanel = mudclient.hasScroll(mudclient.messageTabSelected) && touchedMessagePanelArea;
			boolean mayBeScrollable = mudclient.isMouseOverOpenUiTabPanel(mouseX, mouseY);
			boolean zoomable = !scrollableMessagePanel && !mayBeScrollable;


			// Disables zoom while visible
			boolean inScrollable = (Config.S_SPAWN_AUCTION_NPCS && mudclient.auctionHouse.isVisible() || mudclient.onlineList.isVisible() || Config.S_WANT_SKILL_MENUS && mudclient.skillGuideInterface.isVisible()
				|| Config.S_WANT_QUEST_MENUS && mudclient.questGuideInterface.isVisible() || mudclient.clan.getClanInterface().isVisible() || mudclient.experienceConfigInterface.isVisible()
				|| mudclient.ironmanInterface.isVisible() || mudclient.achievementInterface.isVisible() || Config.S_WANT_SKILL_MENUS && mudclient.doSkillInterface.isVisible()
				|| Config.S_ITEMS_ON_DEATH_MENU && mudclient.lostOnDeathInterface.isVisible() || mudclient.territorySignupInterface.isVisible()
				|| mudclient.isShowDialogBank());

			if (!inScrollable && zoomable && (S_ZOOM_VIEW_TOGGLE || mudclient.getLocalPlayer().isStaff())) {
				e.consume();
				final int zoomIncrement = 10;
				int zoomAmount = e.getWheelRotation() * zoomIncrement;
				mudclient.adjustCameraZoomSetting(zoomAmount);
			}

			if (inScrollable || !zoomable) {
				e.consume();
				mudclient.runScroll(e.getWheelRotation());
			}
		}
	}

	public class KeyHandler implements KeyListener {

		@Override
		public final void keyTyped(KeyEvent var1) {
			try {
				updateControlShiftState(var1);
			} catch (RuntimeException var3) {
				throw GenUtil.makeThrowable(var3, "e.keyTyped(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}

		@Override
		public final synchronized void keyPressed(KeyEvent var1) {
			try {
				updateControlShiftState(var1);
				char keyChar = var1.getKeyChar();
				int keyCode = var1.getKeyCode();
				if (ClientHotkeySettings.shouldSuppressFunctionKey(keyCode)) {
					return;
				}
				boolean hitInputFilter = false;
				mudclient.handleDesktopKeyPress((byte) 126, (int) keyChar, keyCode);
				mudclient.lastMouseAction = 0;

				if (keyCode == 112) mudclient.interlace = !mudclient.interlace;
				if (keyCode == 113) Config.C_SIDE_MENU_OVERLAY = !Config.C_SIDE_MENU_OVERLAY;
				if (keyCode == KeyEvent.VK_F3) mudclient.setCameraZoomSetting(75);
				if (keyCode == KeyEvent.VK_F4) mudclient.toggleFirstPersonView();
				if (keyCode == KeyEvent.VK_HOME) mudclient.resetCameraNorth();
				if (keyCode == KeyEvent.VK_F6 && var1.isControlDown()) mudclient.toggleRendererDebugOverlayMode();
				else if (keyCode == KeyEvent.VK_F6) mudclient.toggleRendererDebugOverlay(); // renderer overlay
				if (keyCode == KeyEvent.VK_F7 && var1.isShiftDown()) mudclient.cycleRendererObjectReliefDiagnostic();
				else if (keyCode == KeyEvent.VK_F7) mudclient.cycleRendererTerrainReliefDiagnostic();
				if (keyCode == KeyEvent.VK_F8 && var1.isControlDown()) mudclient.markMovementStutterObserved();
				else if (keyCode == KeyEvent.VK_F8 && var1.isShiftDown()) mudclient.cycleRendererContrastDiagnostic();
				else if (keyCode == KeyEvent.VK_F8) mudclient.cycleRendererDimnessDiagnostic();
				if (keyCode == KeyEvent.VK_F10 && var1.isShiftDown()) mudclient.cycleRendererSaturationDiagnostic();
				else if (keyCode == KeyEvent.VK_F10) mudclient.cycleRendererGammaDiagnostic();
				if (keyCode == 39) mudclient.keyRight = true;
				if (keyCode == 37) mudclient.keyLeft = true;
				if (keyCode == 13 || keyCode == 10) mudclient.enterPressed = true;
				if (keyCode == KeyEvent.VK_UP) mudclient.keyUp = true;
				if (keyCode == KeyEvent.VK_DOWN) mudclient.keyDown = true;
				if (keyCode == KeyEvent.VK_PAGE_DOWN) mudclient.pageDown = true;
				if (keyCode == KeyEvent.VK_PAGE_UP) mudclient.pageUp = true;

				for (int var5 = 0; var5 < Fonts.inputFilterChars.length(); ++var5)
					if (Fonts.inputFilterChars.charAt(var5) == keyChar) {
						hitInputFilter = true;
						break;
					}

				if (hitInputFilter && !mudclient.shouldSuppressWorldEditorChatInput(keyCode) && mudclient.inputTextCurrent.length() < 20)
					mudclient.inputTextCurrent = mudclient.inputTextCurrent + keyChar;

				if (hitInputFilter && !mudclient.shouldSuppressWorldEditorChatInput(keyCode) && mudclient.chatMessageInput.length() < 80 && !mudclient.getIsSleeping())
					mudclient.chatMessageInput = mudclient.chatMessageInput + keyChar;

				// Backspace
				if (keyChar == '\b' && !mudclient.shouldSuppressWorldEditorChatInput(keyCode) && mudclient.inputTextCurrent.length() > 0)
					mudclient.inputTextCurrent = mudclient.inputTextCurrent.substring(0,
						mudclient.inputTextCurrent.length() - 1);

				// Backspace
				if (keyChar == '\b' && !mudclient.shouldSuppressWorldEditorChatInput(keyCode) && mudclient.chatMessageInput.length() > 0)
					mudclient.chatMessageInput = mudclient.chatMessageInput.substring(0,
						mudclient.chatMessageInput.length() - 1);

				if ((keyChar == '\n' || keyChar == '\r') && !mudclient.shouldSuppressWorldEditorChatInput(keyCode)) {
					mudclient.inputTextFinal = mudclient.inputTextCurrent;
					mudclient.chatMessageInputCommit = mudclient.chatMessageInput;
				}
			} catch (RuntimeException var6) {
				throw GenUtil.makeThrowable(var6, "e.keyPressed(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}

		@Override
		public final synchronized void keyReleased(KeyEvent var1) {
			try {
				updateControlShiftState(var1);
				char c = var1.getKeyChar();
				int keyCode = var1.getKeyCode();

				if (keyCode == 39) mudclient.keyRight = false;
				if (keyCode == 37) mudclient.keyLeft = false;
				if (keyCode == KeyEvent.VK_UP) mudclient.keyUp = false;
				if (keyCode == KeyEvent.VK_DOWN) mudclient.keyDown = false;
				if (keyCode == KeyEvent.VK_PAGE_DOWN) mudclient.pageDown = false;
				if (keyCode == KeyEvent.VK_PAGE_UP) mudclient.pageUp = false;

				if (keyCode == KeyEvent.VK_ALT) {
					mudclient.mouseLastProcessedX = 0;
					mudclient.mouseLastProcessedY = 0;
				}
			} catch (RuntimeException var4) {
				throw GenUtil.makeThrowable(var4, "e.keyReleased(" + (var1 != null ? "{...}" : "null") + ')');
			}
		}
	}
}
