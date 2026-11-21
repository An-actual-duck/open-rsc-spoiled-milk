package orsc.browser;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import orsc.ORSCApplet;
import orsc.BrowserWebGLBridge;
import orsc.graphics.gui.Panel;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.World;
import orsc.graphics.two.GraphicsController;
import orsc.mudclient;

/** Starts on the signed-map fixture and uses the normal client walk request. */
public final class BrowserOuterCoverageProbe {
	private static native int loginReady();
	private static native int worldReady();

	public static void run() {
		try {
			probe();
		} catch (Exception failure) {
			BrowserBootstrap.report("outer-coverage:failed:" + failure);
			failure.printStackTrace();
		}
	}

	private static void probe() throws Exception {
		Field game = field(ORSCApplet.class, "mudclient");
		Field loginPanel = field(mudclient.class, "panelLogin");
		mudclient client = null;
		long deadline = System.currentTimeMillis() + 60_000;
		while (System.currentTimeMillis() < deadline) {
			client = (mudclient) game.get(null);
			if (client != null && loginPanel.get(client) != null && loginReady() == 1) break;
			Thread.sleep(100);
		}
		if (client == null || loginPanel.get(client) == null || loginReady() != 1) {
			throw new IllegalStateException("No private login screen");
		}
		String password = System.getProperty("spoiledmilk.outerCoverageFixturePassword");
		if (password == null) throw new IllegalStateException("No disposable account configured");
		field(mudclient.class, "loginScreenNumber").setInt(client, 2);
		Thread.sleep(250);
		Panel panel = (Panel) loginPanel.get(client);
		Field clicked = field(Panel.class, "controlClicked");
		panel.setText(field(mudclient.class, "controlLoginUser").getInt(client), "browserdemo");
		panel.setText(field(mudclient.class, "controlLoginPass").getInt(client), password);
		field(mudclient.class, "enterPressed").setBoolean(client, true);

		Field appearanceVisible = field(mudclient.class, "showAppearanceChange");
		Field appearancePanel = field(mudclient.class, "panelAppearance");
		Field appearanceButton = field(mudclient.class, "controlButtonAppearanceAccept");
		deadline = System.currentTimeMillis() + 60_000;
		while (System.currentTimeMillis() < deadline) {
			if (appearanceVisible.getBoolean(client)) {
				boolean[] buttons = (boolean[]) clicked.get(appearancePanel.get(client));
				buttons[appearanceButton.getInt(client)] = true;
			}
			if (client.isGameView() && client.getLocalPlayer() != null && !client.isAreaLoadPending()
				&& !client.shouldRetainLastPresentedFrame() && worldReady() == 1) break;
			Thread.sleep(100);
		}
		if (worldReady() != 1) throw new IllegalStateException("Initial graphics did not become ready");
		if (worldX(client) != 239 || worldZ(client) != 609) throw new IllegalStateException("Wrong private spawn");
		if (Boolean.getBoolean("spoiledmilk.sustainedBrowserWalk")) {
			runSustainedWalk(client);
			return;
		}
		BrowserBootstrap.report("outer-coverage:walking:239,609->269,609");
		Method walk = mudclient.class.getDeclaredMethod("walkToArea", int.class, int.class, int.class,
			int.class, int.class, int.class, boolean.class, boolean.class);
		walk.setAccessible(true);
		int targetX = 269 - client.getMidRegionBaseX();
		int targetZ = 609 - client.getMidRegionBaseZ();
		walk.invoke(client, client.getLocalPlayerX(), client.getLocalPlayerZ(), targetX, targetZ,
			targetX, targetZ, false, false);
		Renderer3DFrame last = client.getScene().getRenderer3DFrame();
		long previous = System.currentTimeMillis();
		long maximumGap = 0;
		long approachGap = 0;
		int approachGapFromX = 239;
		int approachGapToX = 239;
		int previousFrameX = 239;
		long retainedSince = 0;
		long maximumHold = 0;
		int previousX = -1;
		boolean reportedUploadsBefore = false;
		deadline = previous + 55_000;
		while (System.currentTimeMillis() < deadline) {
			long now = System.currentTimeMillis();
			int x = worldX(client);
			Renderer3DFrame frame = client.getScene().getRenderer3DFrame();
			if (frame != null && frame != last) {
				last = frame;
				maximumGap = Math.max(maximumGap, now - previous);
				if (x >= 239 && x < 264 && now - previous > approachGap) {
					approachGap = now - previous;
					approachGapFromX = previousFrameX;
					approachGapToX = x;
				}
				previousFrameX = x;
				previous = now;
			}
			boolean retain = client.shouldRetainLastPresentedFrame();
			if (retain && retainedSince == 0) retainedSince = now;
			if (!retain && retainedSince != 0) {
				maximumHold = Math.max(maximumHold, now - retainedSince);
				retainedSince = 0;
			}
			if (x != previousX && x >= 260 && x <= 269 && worldZ(client) == 609) {
				BrowserBootstrap.report("outer-coverage:position:" + x + ":" + retain);
				if (x < 264) BrowserBootstrap.report("outer-coverage:preupload-before:" + x + ":"
					+ BrowserWebGLBridge.getPredictedTerrainReceipt());
				if (x == 263 && !reportedUploadsBefore) {
					reportedUploadsBefore = true;
					BrowserBootstrap.report("outer-coverage:upload-totals-before:" + BrowserWebGLBridge.getSceneUploadReceipt());
				}
			}
			previousX = x;
			if (x == 269 && worldZ(client) == 609 && !retain) {
				BrowserBootstrap.report("outer-coverage:preupload-final:" + BrowserWebGLBridge.getPredictedTerrainReceipt());
				BrowserBootstrap.report("outer-coverage:upload-totals-after:" + BrowserWebGLBridge.getSceneUploadReceipt());
				reportTerrainState(client.getWorld());
				BrowserBootstrap.report("outer-coverage:complete:javaFrameGap=" + maximumGap
					+ ":approachGap=" + approachGap + ":approachX=" + approachGapFromX + "->" + approachGapToX
					+ ":retained=" + maximumHold);
				return;
			}
			Thread.sleep(12);
		}
		throw new IllegalStateException("Route stopped at " + worldX(client) + "," + worldZ(client));
	}

	private static int worldX(mudclient client) { return client.getMidRegionBaseX() + client.getLocalPlayerX(); }
	private static int worldZ(mudclient client) { return client.getMidRegionBaseZ() + client.getLocalPlayerZ(); }

	private static void runSustainedWalk(mudclient client) throws Exception {
		Method walk = mudclient.class.getDeclaredMethod("walkToArea", int.class, int.class, int.class,
			int.class, int.class, int.class, boolean.class, boolean.class);
		walk.setAccessible(true);
		int[][] destinations = {{284, 609}, {291, 597}, {322, 597}, {291, 597}, {284, 609}, {255, 609}};
		for (int[] destination : destinations) {
			int x = destination[0], z = destination[1];
			BrowserBootstrap.report("outer-coverage:walking:" + worldX(client) + "," + worldZ(client) + "->" + x + "," + z);
			int localX = x - client.getMidRegionBaseX(), localZ = z - client.getMidRegionBaseZ();
			walk.invoke(client, client.getLocalPlayerX(), client.getLocalPlayerZ(), localX, localZ, localX, localZ, false, false);
			long deadline = System.currentTimeMillis() + 48_000;
			while ((worldX(client) != x || worldZ(client) != z || worldReady() != 1) && System.currentTimeMillis() < deadline) {
				Thread.sleep(75L);
			}
			if (worldX(client) != x || worldZ(client) != z || worldReady() != 1) {
				throw new IllegalStateException("Sustained walk ended at " + worldX(client) + "," + worldZ(client)
					+ " while walking to " + x + "," + z);
			}
			BrowserBootstrap.report("outer-coverage:arrived:" + x + "," + z);
		}
		Thread.sleep(8_000L);
		reportTerrainState(client.getWorld());
		BrowserBootstrap.report("outer-coverage:complete:sustained");
	}

	private static void reportTerrainState(World world) throws ReflectiveOperationException {
		int[][] elevations = (int[][]) field(World.class, "tileElevationCache").get(world);
		GraphicsController graphics = (GraphicsController) field(World.class, "minimapGraphics").get(world);
		BrowserBootstrap.report("outer-coverage:terrain-state:collision=" + Arrays.deepHashCode(world.collisionFlags)
			+ ":elevation=" + Arrays.deepHashCode(elevations)
			+ ":minimap=" + Arrays.hashCode(graphics.minimapSprite.getPixels()));
	}

	private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}
}
