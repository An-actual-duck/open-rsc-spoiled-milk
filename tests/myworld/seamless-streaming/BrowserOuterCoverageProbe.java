package orsc.browser;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.GameObjectDef;
import orsc.ORSCApplet;
import orsc.NativeLayeredTerrainSnapshot;
import orsc.PacketHandler;
import orsc.BrowserWebGLBridge;
import orsc.Config;
import orsc.graphics.gui.Panel;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.RSModel;
import orsc.graphics.three.World;
import orsc.graphics.two.GraphicsController;
import orsc.mudclient;

/** Starts on the signed-map fixture and uses the normal client walk request. */
public final class BrowserOuterCoverageProbe {
	private static native int loginReady();
	private static native int worldReady();
	private static native int worldInputReady();
	private static native void holdOptionalTerrain(boolean hold);

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
		long deadline = System.currentTimeMillis() + 90_000;
		while (System.currentTimeMillis() < deadline) {
			client = (mudclient) game.get(null);
			if (client != null && loginPanel.get(client) != null && loginReady() == 1) break;
			Thread.sleep(100);
		}
		if (client == null || loginPanel.get(client) == null || loginReady() != 1) {
			throw new IllegalStateException("No private login screen (panel="
				+ (client != null && loginPanel.get(client) != null) + ", transportReady=" + (loginReady() == 1) + ")");
		}
		String password = System.getProperty("spoiledmilk.outerCoverageFixturePassword");
		if (password == null) throw new IllegalStateException("No disposable account configured");
		Field clicked = field(Panel.class, "controlClicked");
		submitLogin(client, loginPanel, clicked, password);
		long retryLoginAt = System.currentTimeMillis() + 5_000;

		Field appearanceVisible = field(mudclient.class, "showAppearanceChange");
		Field appearancePanel = field(mudclient.class, "panelAppearance");
		Field appearanceButton = field(mudclient.class, "controlButtonAppearanceAccept");
		deadline = System.currentTimeMillis() + 90_000;
		while (System.currentTimeMillis() < deadline) {
			if (System.currentTimeMillis() >= retryLoginAt && loginReady() == 1) {
				BrowserBootstrap.report("outer-coverage:retrying-unsubmitted-login");
				submitLogin(client, loginPanel, clicked, password);
				retryLoginAt = Long.MAX_VALUE;
			}
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
		if (Boolean.getBoolean("spoiledmilk.browserProfilePanels")) profilePanels(client);
		if (Boolean.getBoolean("spoiledmilk.persistentMovementPreview")) {
			runPersistentMovementWalk(client);
			return;
		}
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

	private static void submitLogin(mudclient client, Field loginPanel, Field clicked, String password) throws Exception {
		field(mudclient.class, "loginScreenNumber").setInt(client, 2);
		Thread.sleep(250);
		Panel panel = (Panel) loginPanel.get(client);
		panel.setText(field(mudclient.class, "controlLoginUser").getInt(client), "browserdemo");
		panel.setText(field(mudclient.class, "controlLoginPass").getInt(client), password);
		boolean[] loginButtons = (boolean[]) clicked.get(panel);
		loginButtons[field(mudclient.class, "m_be").getInt(client)] = true;
	}

	private static void profilePanels(mudclient client) throws Exception {
		// The disposable account's first-login dialogs cover the side panels.
		field(mudclient.class, "showDialogMessage").setBoolean(client, false);
		field(mudclient.class, "showDialogServerMessage").setBoolean(client, false);
		Field pinnedTab = field(mudclient.class, "pinnedSideMenuTab");
		Field pinnedMap = field(mudclient.class, "minimapPinned");
		int savedTab = pinnedTab.getInt(client);
		int savedHover = client.showUiTab;
		boolean savedMap = pinnedMap.getBoolean(client);
		try {
			pinnedTab.setInt(client, 0);
			pinnedMap.setBoolean(client, false);
			client.showUiTab = 0;
			BrowserBootstrap.report("outer-coverage:ui-profile:closed");
			Thread.sleep(6000);
			pinnedTab.setInt(client, Config.INVENTORY_TAB);
			BrowserBootstrap.report("outer-coverage:ui-profile:inventory");
			BrowserBootstrap.report("outer-coverage:ui-profile-inventory-items:" + field(mudclient.class, "inventoryItemCount").getInt(client));
			Thread.sleep(6000);
			pinnedTab.setInt(client, 0);
			pinnedMap.setBoolean(client, true);
			BrowserBootstrap.report("outer-coverage:ui-profile:minimap");
			Thread.sleep(6000);
			BrowserBootstrap.report("outer-coverage:ui-profile:done");
		} finally {
			pinnedTab.setInt(client, savedTab);
			pinnedMap.setBoolean(client, savedMap);
			client.showUiTab = savedHover;
		}
		if (Boolean.getBoolean("spoiledmilk.browserProfileMinimapWalk")) {
			client.showUiTab = 0;
			pinnedTab.setInt(client, 0);
			pinnedMap.setBoolean(client, true);
		}
	}

	private static int worldX(mudclient client) { return client.getMidRegionBaseX() + client.getLocalPlayerX(); }
	private static int worldZ(mudclient client) { return client.getMidRegionBaseZ() + client.getLocalPlayerZ(); }

	private static void runPersistentMovementWalk(mudclient client) throws Exception {
		Method walk = fieldWalk();
		int[] projectionStart = Boolean.getBoolean("spoiledmilk.browserProjectionDemand")
			? projectionDemand(client) : null;
		long[] omittedStart = Boolean.getBoolean("spoiledmilk.browserResidentCameraCull")
			? omittedProjection(client) : null;
		if (Boolean.getBoolean("spoiledmilk.browserTravelTrace")) reportAnimatedScenery(client);
		walkLeg(client, walk, 257, 609);
		int[] before = client.packetHandler.getPersistentMovementReceipt();
		if (before[0] <= 0 || before[1] <= 0) throw new IllegalStateException("Private travel stream was not applied");
		if (Boolean.getBoolean("spoiledmilk.steadySurfaceWalk")) {
			verifySteadyWalking(client, walk, before);
			BrowserBootstrap.report("outer-coverage:complete:persistent-movement");
			return;
		}
		if (Boolean.getBoolean("spoiledmilk.retargetSurfaceWalk")) {
			verifyPreparedDirectionChange(client, walk, before);
			verifyDetachedGameplayInstalls(client, 1);
			BrowserBootstrap.report("outer-coverage:complete:persistent-movement");
			return;
		}
		if (Boolean.getBoolean("spoiledmilk.surfaceSceneryCells")) {
			verifyIncomingSceneryMutations(client, walk, before[3]);
		}
		if (Boolean.getBoolean("spoiledmilk.browserPresentationOnly")) {
			verifyUntracedTravel(client, walk, before, projectionStart, omittedStart);
			return;
		}
		boolean fixedDraw = Boolean.getBoolean("spoiledmilk.fixedSurfaceDraw");
		int[] previousDraws = fixedDraw ? awaitSurfaceDrawBuffers() : null;
		int[] liveOuterDraws = Boolean.getBoolean("spoiledmilk.expandedSurfaceDrawBorder")
			? awaitLiveOuterDrawBuffers(client, before[3]) : new int[0];
		boolean sceneryDraw = Boolean.getBoolean("spoiledmilk.surfaceSceneryDraw");
		boolean liveHandoff = Boolean.getBoolean("spoiledmilk.liveSurfaceHandoff");
		int[] liveScenery = sceneryDraw ? awaitOptionalSceneryDraws(client, before[3]) : new int[0];
		if (sceneryDraw) BrowserBootstrap.report("outer-coverage:scenery-draw:before-uploads:" + BrowserWebGLBridge.getSceneUploadReceipt());
		boolean rolling = Boolean.getBoolean("spoiledmilk.rollingSurfaceWindow");
		Map<RSModel, Long> overlapModels = rolling ? snapshotOverlapModels(client, false) : null;
		Map<RSModel, Long> overlapWalls = rolling ? snapshotOverlapModels(client, true) : null;
		Object actor = client.getLocalPlayer();
		if (liveHandoff) BrowserBootstrap.report("outer-coverage:terrain-cells-before-context="
			+ client.getWorld().getIncomingSurfaceDraws().size());
		int[] coverageBefore = liveHandoff ? BrowserWebGLBridge.getSurfaceCoverageReceipt() : null;
		if (liveHandoff && (coverageBefore.length == 0 || worldInputReady() != 1)) {
			throw new IllegalStateException("Installed area never became interactive");
		}
		walkLeg(client, walk, 278, 609);
		int afterScene = client.packetHandler.getPersistentMovementReceipt()[3];
		if (afterScene <= before[3]) throw new IllegalStateException("Route did not cross the terrain center boundary");
		if (liveHandoff) {
			int[] coverage = BrowserWebGLBridge.getSurfaceCoverageReceipt();
			if (coverage.length != 5 || coverage[0] != 1 || coverage[1] != coverageBefore[1]
				|| coverage[2] != coverage[3] || worldInputReady() != 1) {
				throw new IllegalStateException("Verified walking opened a load epoch or lacked coverage: "
					+ Arrays.toString(coverageBefore) + " -> " + Arrays.toString(coverage));
			}
			String terrain = client.packetHandler.getLayeredSceneContextDebugSummary();
			if (!terrain.contains("lastPayloads=0 lastReferences=9")) {
				throw new IllegalStateException("The ordinary context resent terrain already accepted from the prediction: " + terrain);
			}
			BrowserBootstrap.report("outer-coverage:live-handoff:terrain-references=9:payloads=0");
			BrowserBootstrap.report("outer-coverage:live-handoff:gpu-coverage=" + Arrays.toString(coverage)
				+ ":input=ready:prepared-pick-models=" + client.getAdoptedSurfaceInteractiveModels());
		}
		if (sceneryDraw) {
			int adopted = client.getAdoptedSurfaceSceneryDraws();
			int promoted = countMatchingSurfaceDrawBuffers(liveScenery, BrowserWebGLBridge.getSceneryDrawBuffers(false), true);
			if (adopted <= 0 || promoted <= 0) {
				throw new IllegalStateException("Prepared scenery did not become active. Adopted=" + adopted + " promoted=" + promoted);
			}
			BrowserBootstrap.report("outer-coverage:scenery-draw:after-uploads:" + BrowserWebGLBridge.getSceneUploadReceipt());
			BrowserBootstrap.report("outer-coverage:scenery-draw:adopted=" + adopted + ":gpu-promoted=" + promoted);
		}
		if (fixedDraw) {
			int[] activeDraws = awaitSurfaceDrawBuffers();
			compareSurfaceDrawBuffers(previousDraws, activeDraws);
			if (liveOuterDraws.length > 0) {
				int admitted = countMatchingSurfaceDrawBuffers(liveOuterDraws, activeDraws, true);
				if (admitted != liveOuterDraws.length / 3) {
					throw new IllegalStateException("Entering draw buffers were replaced at the gameplay rebase");
				}
				BrowserBootstrap.report("outer-coverage:fixed-terrain:outer-draws-promoted=" + admitted);
			}
		}
		if (rolling) {
			int objects = retainedModels(overlapModels, snapshotOverlapModels(client, false));
			int walls = retainedModels(overlapWalls, snapshotOverlapModels(client, true));
			if (client.getRollingSurfaceWindows() != 1 || objects == 0 || walls == 0 || client.getLocalPlayer() != actor) {
				throw new IllegalStateException("Did not retain overlap models and the actor on the rolling path: "
					+ client.getRollingSurfaceWindows() + "/" + objects + "/" + walls);
			}
			BrowserBootstrap.report("outer-coverage:rolled:retained=" + objects + "/" + walls);
			walkLeg(client, walk, 284, 609);
			walkLeg(client, walk, 291, 597);
			if (liveHandoff && Boolean.getBoolean("spoiledmilk.sustainedBrowserWalk")) {
				verifySurfaceWalkLeg(client, walk, 322, 597, coverageBefore[1], actor);
				walkLeg(client, walk, 291, 597);
			}
			walkLeg(client, walk, 284, 609);
		}
		if (liveHandoff && Boolean.getBoolean("spoiledmilk.sustainedBrowserWalk")) {
			verifySurfaceWalkLeg(client, walk, 252, 609, coverageBefore[1], actor);
		} else {
			walkLeg(client, walk, 252, 609);
		}
		int[] after = client.packetHandler.getPersistentMovementReceipt();
		if (after[0] != before[0] || after[1] <= before[1] || after[2] != 0) {
			throw new IllegalStateException("Unexpected travel receipts " + Arrays.toString(before) + " to " + Arrays.toString(after));
		}
		if (client.packetHandler.getLogicalPlayerX(0) != 252 || client.packetHandler.getLogicalPlayerY(0, 0) != 609) {
			throw new IllegalStateException("Logical position did not follow the persistent movement receipts");
		}
		if (Boolean.getBoolean("spoiledmilk.persistentSurfaceCollision")) {
			int staged = client.getWorld().getStagedSurfaceCollisionCells();
			if (staged <= 0) throw new IllegalStateException("No incoming collision cells were published");
			BrowserBootstrap.report("outer-coverage:incoming-collision-cells:" + staged
				+ ":before-geometry=" + client.getWorld().getEarlySurfaceCollisionCells());
		}
		BrowserBootstrap.report("outer-coverage:movement-generation:" + after[0] + ":applied=" + after[1]
			+ ":outside-coverage=" + after[2] + ":terrain-epochs=" + before[3] + "," + afterScene + "," + after[3]);
		verifyDetachedGameplayInstalls(client, Boolean.getBoolean("spoiledmilk.sustainedBrowserWalk") ? 3 : 2);
		BrowserBootstrap.report("outer-coverage:complete:persistent-movement");
	}

	private static int[] projectionDemand(mudclient client) throws ReflectiveOperationException {
		return (int[]) client.getScene().getClass().getMethod("getBrowserProjectionDemand").invoke(client.getScene());
	}

	private static long[] omittedProjection(mudclient client) throws ReflectiveOperationException {
		return (long[]) client.getScene().getClass().getMethod("getBrowserOmittedProjection").invoke(client.getScene());
	}

	private static void verifyUntracedTravel(mudclient client, Method walk, int[] before,
			int[] projectionStart, long[] omittedStart) throws Exception {
		Object actor = client.getLocalPlayer();
		int[][] destinations = {{278,609}, {284,609}, {291,597}, {322,597}, {291,597}, {284,609}, {252,609}};
		for (int[] destination : destinations) {
			walkLeg(client, walk, destination[0], destination[1]);
			if (client.getLocalPlayer() != actor || worldInputReady() != 1) {
				throw new IllegalStateException("Untraced walking lost the player or world input");
			}
		}
		BrowserBootstrap.report("outer-coverage:walking-window-ended");
		int[] after = client.packetHandler.getPersistentMovementReceipt();
		if (client.getRollingSurfaceWindows() != 3 || after[0] != before[0] || after[1] <= before[1]
			|| after[2] != 0 || after[3] != before[3] + 3) {
			throw new IllegalStateException("Unexpected untraced travel receipts "
				+ Arrays.toString(before) + " to " + Arrays.toString(after));
		}
		BrowserBootstrap.report("outer-coverage:movement-generation:" + after[0] + ":rolled=3");
		if (Boolean.getBoolean("spoiledmilk.browserPresentationRollTimings")) {
			long[] timing = (long[]) PacketHandler.class.getMethod("getSurfaceRollTiming").invoke(client.packetHandler);
			if (timing.length != 9) throw new IllegalStateException("Expected timestamps for three verified scene rolls");
			for (int word = 0; word < timing.length; word += 3) {
				if (timing[word] != before[3] + word / 3 + 1 || timing[word + 2] < timing[word + 1]) {
					throw new IllegalStateException("Invalid scene roll timing " + Arrays.toString(timing));
				}
				BrowserBootstrap.report("outer-coverage:roll-timing:" + timing[word] + "," + timing[word + 1] + "," + timing[word + 2]);
			}
		}
		verifyDetachedGameplayInstalls(client, 3);
		if (projectionStart != null) {
			int[] demand = projectionDemand(client);
			for (int n = 0; n < demand.length; n++) demand[n] -= projectionStart[n];
			if (demand.length != 8 || demand[0] == 0 || demand[2] == 0
				|| demand[3] != demand[4] + demand[5] + demand[6] || demand[7] > demand[3]) {
				throw new IllegalStateException("Invalid resident projection demand " + Arrays.toString(demand));
			}
			BrowserBootstrap.report("outer-coverage:projection-demand:samples=" + demand[0]
				+ ":projected=" + demand[1] + ":resident=" + demand[2] + ":rejected=" + demand[3]
				+ ":terrain=" + demand[4] + ":structures=" + demand[5] + ":scenery=" + demand[6]
				+ ":bounded=" + demand[7]);
		}
		if (omittedStart != null) {
			long[] omitted = omittedProjection(client);
			if (omitted[0] <= omittedStart[0] || omitted[1] <= omittedStart[1])
				throw new IllegalStateException("Resident camera culling did not skip projection work");
			BrowserBootstrap.report("outer-coverage:projection-omitted:models=" + (omitted[0] - omittedStart[0])
				+ ":vertices=" + (omitted[1] - omittedStart[1]));
		}
		if (Boolean.getBoolean("spoiledmilk.browserVerifyWorkerSceneryPlacements")) {
			int verified = client.getVerifiedWorkerSceneryMeshes(), mismatched = client.getMismatchedWorkerSceneryMeshes();
			if (verified == 0 || mismatched != 0) throw new IllegalStateException("Scenery mesh parity " + verified + "/" + mismatched
				+ " " + client.getFirstSceneryReuseMismatch());
			BrowserBootstrap.report("outer-coverage:placement-mesh-parity:" + verified);
			if (Boolean.getBoolean("spoiledmilk.browserReuseDetachedScenery")) {
				int reused = client.getVerifiedSceneryReuses();
				if (reused == 0) throw new IllegalStateException("No reused scenery mesh was verified");
				BrowserBootstrap.report("outer-coverage:reused-mesh-parity:" + reused);
			}
		}
		for (String row : orsc.graphics.three.SurfaceSceneryMeshPreparation.traceSnapshot()) {
			BrowserBootstrap.report("scenery-meshing:" + row);
		}
		BrowserBootstrap.report("outer-coverage:complete:persistent-movement");
	}

	private static void verifyDetachedGameplayInstalls(mudclient client, int expected) {
		if (!Boolean.getBoolean("spoiledmilk.browserSurfaceMeshWorker")) return;
		int installed = client.getWorld().getDetachedSurfaceGameplayInstalls();
		if (installed < expected) {
			throw new IllegalStateException("Expected " + expected + " gameplay products from the detached worker, installed " + installed);
		}
		BrowserBootstrap.report("outer-coverage:detached-gameplay-installs:" + installed);
		if (Boolean.getBoolean("spoiledmilk.browserSurfaceGameplayWords")) {
			int accepted = client.getWorld().getAcceptedSurfaceGameplayWordReplies();
			if (accepted < expected) throw new IllegalStateException("Expected " + expected + " primitive gameplay replies, accepted " + accepted);
			BrowserBootstrap.report("outer-coverage:terrain-gameplay-word-replies:" + accepted);
		}
		if (Boolean.getBoolean("spoiledmilk.browserPackedGameplayVertices")) {
			int packed;
			boolean current;
			try {
				packed = (int) World.class.getMethod("getInstalledPackedGameplayMeshes").invoke(client.getWorld());
				current = (boolean) World.class.getMethod("hasCurrentPackedGameplayMesh").invoke(client.getWorld());
			} catch (ReflectiveOperationException error) {
				throw new IllegalStateException("Packed gameplay mesh state is unavailable", error);
			}
			if (packed < expected || !current) {
				throw new IllegalStateException("Gameplay mesh did not stay packed through publication " + packed);
			}
			BrowserBootstrap.report("outer-coverage:packed-gameplay-meshes:" + packed + ":current=true");
		}
		if (Boolean.getBoolean("spoiledmilk.browserSurfaceTerrainWords")) {
			int preparedTerrain = client.getWorld().getAcceptedSurfaceTerrainWordReplies();
			if (preparedTerrain == 0) throw new IllegalStateException("No primitive terrain reply was accepted");
			BrowserBootstrap.report("outer-coverage:terrain-word-replies:" + preparedTerrain);
		}
		int scenery = client.getDetachedSurfaceSceneryMeshes();
		if (scenery <= 0) throw new IllegalStateException("No scenery mesh came back from the detached worker");
		BrowserBootstrap.report("outer-coverage:detached-scenery-meshes:" + scenery);
		if (Boolean.getBoolean("spoiledmilk.browserWorkerSceneryPlacements")) {
			int placements = client.getDetachedSceneryPlacementMeshes();
			if (placements == 0) throw new IllegalStateException("No worker mesh was built from placements");
			BrowserBootstrap.report("outer-coverage:placement-scenery-meshes:" + placements);
		}
		if (Boolean.getBoolean("spoiledmilk.browserReuseDetachedScenery")) {
			long[] reuse = client.getDetachedSceneryReuseReceipt();
			if (reuse.length != 2 || reuse[0] <= 0 || reuse[1] > 64L * 1024 * 1024) {
				throw new IllegalStateException("Detached scenery reuse " + Arrays.toString(reuse));
			}
			BrowserBootstrap.report("outer-coverage:scenery-mesh-reuse:" + reuse[0] + ":bytes=" + reuse[1]);
		}
		int lighting = BrowserWebGLBridge.getResidentTerrainLightingFields();
		if (lighting <= 0) throw new IllegalStateException("No terrain lighting field came from the resident cells");
		BrowserBootstrap.report("outer-coverage:resident-lighting-fields:" + lighting);
		if (Boolean.getBoolean("spoiledmilk.browserKeepRebasedModels")) {
			long[] work;
			try {
				work = (long[]) RSModel.class.getMethod("getBrowserRebaseWork").invoke(null);
			} catch (ReflectiveOperationException error) {
				throw new IllegalStateException("Cached model rebase counters are unavailable", error);
			}
			if (work[0] <= 0 || work[1] <= 0) throw new IllegalStateException("No model retained its transform across the rolls");
			BrowserBootstrap.report("outer-coverage:preserved-model-rebase:models=" + work[0] + ":vertices=" + work[1]);
		}
		if (!Boolean.getBoolean("spoiledmilk.browserTravelTrace")) return;
		int reused = client.getResidentPlacementPublicationsReused();
		if (reused <= 0) throw new IllegalStateException("Resident scenery was never reused between placement events");
		BrowserBootstrap.report("outer-coverage:resident-placement-publications-reused:" + reused);
		int rebased = client.getResidentFishingPoseRebases();
		if (rebased <= 0 && Boolean.getBoolean("spoiledmilk.sustainedBrowserWalk")) {
			throw new IllegalStateException("No resident ripple pose survived a rolling origin");
		}
		BrowserBootstrap.report("outer-coverage:resident-fishing-poses-rebased:" + rebased);
		BrowserBootstrap.report("outer-coverage:scenery-meshes-after-activation:" + client.getLateSurfaceSceneryMeshes());
		BrowserBootstrap.report("outer-coverage:shared-scenery-meshes-prepared:" + client.getRetainedSurfaceSceneryMeshes());
	}

	private static void verifyPreparedDirectionChange(mudclient client, Method walk, int[] movement) throws Exception {
		Field ready = field(PacketHandler.class, "readyPredictedLayeredTerrainStructureStage");
		awaitPreparedCenter(client, movement[3], ready, 5, 12);
		int targetZ = 574, targetX = -1;
		int[] pathX = new int[World.LOCAL_TILE_COUNT * World.LOCAL_TILE_COUNT], pathZ = new int[pathX.length];
		for (int candidate : new int[] {250, 243, 237, 230, 225}) {
			int localX = candidate - client.getMidRegionBaseX(), localZ = targetZ - client.getMidRegionBaseZ();
			int waypoints = client.getWorld().findPath(pathX, pathZ, client.getLocalPlayerX(), client.getLocalPlayerZ(),
				localX, localX, localZ, localZ, false);
			if (waypoints > 0) {
				targetX = candidate;
				break;
			}
		}
		if (targetX < 0) throw new IllegalStateException("The native surface route cannot reach the north strip");
		BrowserBootstrap.report("outer-coverage:prediction-ready:5,12");
		int x = Math.floorDiv(targetX, World.SECTION_SIZE);
		int[] before = awaitSurfaceDrawBuffers();
		boolean holdFringe = Boolean.getBoolean("spoiledmilk.fixtureHoldOptionalTerrain");
		if (holdFringe) {
			awaitLiveOuterDrawBuffers(client, movement[3]);
			holdOptionalTerrain(true);
		}
		try {
			walkLeg(client, walk, targetX, targetZ);
			awaitPreparedCenter(client, movement[3], ready, x, 11);
			int[] after = client.packetHandler.getPersistentMovementReceipt();
			if (after[0] != movement[0] || after[2] != 0 || after[3] != movement[3] || worldInputReady() != 1) {
				throw new IllegalStateException("Changing the prepared direction interrupted the installed world");
			}
			BrowserBootstrap.report("outer-coverage:prediction-retargeted:5,12->" + x + ",11");
			int[] coverage = BrowserWebGLBridge.getSurfaceCoverageReceipt();
			verifySurfaceWalkLeg(client, walk, targetX, 550, coverage[1], client.getLocalPlayer());
			if (holdFringe) {
				long deadline = System.currentTimeMillis() + 6_000;
				int[] draws = BrowserWebGLBridge.getSurfaceDrawBuffers();
				while (draws.length != 16 * 3 && System.currentTimeMillis() < deadline) {
					Thread.sleep(40);
					draws = BrowserWebGLBridge.getSurfaceDrawBuffers();
				}
				int overlap = countMatchingSurfaceDrawBuffers(before, draws, true);
				if (draws.length != 16 * 3 || overlap != 16) {
					throw new IllegalStateException("The diagonal failed to present its overlap while the fringe was held"
						+ " buffers=" + draws.length / 3 + " retained=" + overlap);
				}
				BrowserBootstrap.report("outer-coverage:diagonal:crossed-before-optional-terrain");
			}
		} finally {
			if (holdFringe) holdOptionalTerrain(false);
		}
		int retained = countMatchingSurfaceDrawBuffers(before, awaitSurfaceDrawBuffers(), true);
		if (retained != 16 || BrowserWebGLBridge.getSurfaceCoverageReceipt()[2] != 9) {
			throw new IllegalStateException("The corner did not preserve its 16 overlapping draws and nine gameplay sources");
		}
		BrowserBootstrap.report("outer-coverage:diagonal:overlap=16:gameplay-terrain=9");

	}

	private static void verifySteadyWalking(mudclient client, Method walk, int[] movement) throws Exception {
		awaitPreparedCenter(client, movement[3], field(PacketHandler.class, "readyPredictedLayeredTerrainStructureStage"), 5, 12);
		awaitLiveOuterDrawBuffers(client, movement[3]);
		awaitOptionalSceneryDraws(client, movement[3]);
		Object actor = client.getLocalPlayer();
		int[] terrain = awaitSurfaceDrawBuffers();
		BrowserBootstrap.report("outer-coverage:steady-walk-start:" + BrowserWebGLBridge.getSceneUploadReceipt());
		for (int destination : new int[]{239, 257, 239, 257}) {
			walkLeg(client, walk, destination, 609);
			int[] receipt = client.packetHandler.getPersistentMovementReceipt();
			if (receipt[0] != movement[0] || receipt[2] != 0 || receipt[3] != movement[3]
				|| client.getLocalPlayer() != actor || !Arrays.equals(terrain, awaitSurfaceDrawBuffers()) || worldInputReady() != 1) {
				throw new IllegalStateException("The steady walk changed the installed terrain or actor");
			}
		}
		BrowserBootstrap.report("outer-coverage:steady-walk-end:" + BrowserWebGLBridge.getSceneUploadReceipt());
	}

	private static void reportAnimatedScenery(mudclient client) throws Exception {
		Method animated = mudclient.class.getDeclaredMethod("isAnimatedResidentObjectChunkModel",
			orsc.graphics.three.Renderer3DModelKind.class, int.class);
		animated.setAccessible(true);
		Map<String, Integer> seen = new TreeMap<>();
		for (int i = 0; i < client.getGameObjectInstanceCount(); i++) {
			int id = client.getGameObjectInstanceID(i);
			if (!client.isGameObjectInstanceMaterialized(i)
				|| !(Boolean) animated.invoke(client, orsc.graphics.three.Renderer3DModelKind.GAME_OBJECT, id)) continue;
			GameObjectDef def = EntityHandler.getObjectDef(id);
			seen.merge(id + "/" + def.getObjectModel(), 1, Integer::sum);
		}
		BrowserBootstrap.report("outer-coverage:scenery-animation-models:" + seen);
	}

	private static void awaitPreparedCenter(mudclient client, int sequence, Field field, int x, int z) throws Exception {
		long deadline = System.currentTimeMillis() + 20_000;
		do {
			if (client.packetHandler.getPersistentMovementReceipt()[3] != sequence || worldInputReady() != 1) {
				throw new IllegalStateException("Prediction changed the installed authority");
			}
			NativeLayeredTerrainSnapshot terrain = (NativeLayeredTerrainSnapshot) field.get(client.packetHandler);
			if (terrain != null && terrain.getCurrentChunkX() == x && terrain.getCurrentChunkY() == z) return;
			Thread.sleep(50);
		} while (System.currentTimeMillis() < deadline);
		throw new IllegalStateException("The planned center was never prepared at " + x + "," + z);
	}

	private static void verifySurfaceWalkLeg(mudclient client, Method walk, int x, int z, int epoch, Object actor) throws Exception {
		int rolling = client.getRollingSurfaceWindows();
		int sequence = client.packetHandler.getPersistentMovementReceipt()[3];
		walkLeg(client, walk, x, z);
		long deadline = System.currentTimeMillis() + 6_000;
		int[] coverage = BrowserWebGLBridge.getSurfaceCoverageReceipt();
		while ((client.getRollingSurfaceWindows() == rolling || coverage.length != 5 || coverage[0] != 1)
			&& System.currentTimeMillis() < deadline) {
			Thread.sleep(40);
			coverage = BrowserWebGLBridge.getSurfaceCoverageReceipt();
		}
		if (client.getRollingSurfaceWindows() != rolling + 1
			|| client.packetHandler.getPersistentMovementReceipt()[3] != sequence + 1
			|| client.getLocalPlayer() != actor || coverage.length != 5 || coverage[0] != 1
			|| coverage[1] != epoch || coverage[2] != coverage[3] || worldInputReady() != 1) {
			throw new IllegalStateException("Repeated crossing did not keep the current world at " + x + "," + z
				+ " rolling=" + rolling + "->" + client.getRollingSurfaceWindows() + " coverage=" + Arrays.toString(coverage));
		}
		BrowserBootstrap.report("outer-coverage:live-handoff:repeated:" + x + "," + z + ":coverage=" + Arrays.toString(coverage));
	}

	private static int[] awaitOptionalSceneryDraws(mudclient client, int scene) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 15_000;
		do {
			if (client.packetHandler.getPersistentMovementReceipt()[3] != scene || client.shouldRetainLastPresentedFrame()) {
				throw new IllegalStateException("The installed area stopped presenting while scenery cells arrived");
			}
			int[] draws = BrowserWebGLBridge.getSceneryDrawBuffers(true);
			if (draws.length >= 3 && worldReady() == 1) {
				BrowserBootstrap.report("outer-coverage:scenery-draw:before-rebase=" + draws.length / 3);
				return draws;
			}
			Thread.sleep(50);
		} while (System.currentTimeMillis() < deadline);
		throw new IllegalStateException("No ready scenery cells joined the live view. Meshes prepared="
			+ client.getPreparedSurfaceSceneryDraws().size() + " " + BrowserWebGLBridge.getPredictedTerrainReceipt()
			+ " " + BrowserWebGLBridge.getSceneUploadReceipt()
			+ " publication counts=" + Arrays.toString(BrowserWebGLBridge.getSceneryPublicationCounts()));
	}

	private static int[] awaitLiveOuterDrawBuffers(mudclient client, int scene) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 15_000;
		do {
			if (client.packetHandler.getPersistentMovementReceipt()[3] != scene || client.shouldRetainLastPresentedFrame()) {
				throw new IllegalStateException("A scene change blocked publication of optional draw cells");
			}
			int[] draws = BrowserWebGLBridge.getOptionalSurfaceDrawBuffers();
			if (draws.length == 5 * 3 && worldReady() == 1) {
				BrowserBootstrap.report("outer-coverage:fixed-terrain:outer-draws-before-rebase=5");
				return draws;
			}
			Thread.sleep(50);
		} while (System.currentTimeMillis() < deadline);
		throw new IllegalStateException("Optional cells did not reach live frames with the current area ready");
	}

	private static int[] awaitSurfaceDrawBuffers() throws InterruptedException {
		long deadline = System.currentTimeMillis() + 15_000;
		do {
			int[] draws = BrowserWebGLBridge.getSurfaceDrawBuffers();
			if (draws.length == 25 * 3) return draws;
			Thread.sleep(50);
		} while (System.currentTimeMillis() < deadline);
		throw new IllegalStateException("Fixed terrain cells did not reach the browser draw list");
	}

	private static void compareSurfaceDrawBuffers(int[] before, int[] after) {
		int common = countMatchingSurfaceDrawBuffers(before, after, false);
		int retained = countMatchingSurfaceDrawBuffers(before, after, true);
		int expected = Boolean.getBoolean("spoiledmilk.expandedSurfaceDrawBorder") ? 20 : 10;
		if (common != 20 || retained < expected) {
			throw new IllegalStateException("Expected a translated 5-by-5 GPU field with resident overlap, got " + retained + "/" + common);
		}
		BrowserBootstrap.report("outer-coverage:fixed-terrain:gpu-buffers-retained=" + retained + "/" + common);
	}

	private static int countMatchingSurfaceDrawBuffers(int[] before, int[] after, boolean requireSameBuffer) {
		int count = 0;
		for (int i = 0; i < before.length; i += 3) {
			for (int j = 0; j < after.length; j += 3) {
				if (before[i] != after[j] || before[i + 1] != after[j + 1]) continue;
				if (!requireSameBuffer || before[i + 2] == after[j + 2]) count++;
			}
		}
		return count;
	}

	private static Map<RSModel, Long> snapshotOverlapModels(mudclient client, boolean wall) {
		Map<RSModel, Long> models = new IdentityHashMap<>();
		int count = wall ? client.getWallObjectInstanceCount() : client.getGameObjectInstanceCount();
		for (int i = 0; i < count; i++) {
			int x = client.getMidRegionBaseX() + (wall ? client.getWallObjectInstanceX(i) : client.getGameObjectInstanceX(i));
			int z = client.getMidRegionBaseZ() + (wall ? client.getWallObjectInstanceZ(i) : client.getGameObjectInstanceZ(i));
			if (x < 192 || x >= 288 || z < 528 || z >= 672) continue;
			RSModel model = wall ? client.getWallObjectInstanceModel(i) : client.getGameObjectInstanceModel(i);
			boolean materialized = wall ? client.isWallObjectInstanceMaterialized(i) : client.isGameObjectInstanceMaterialized(i);
			if (model != null && materialized) {
				if (model.key != i + (wall ? 20000 : 0)) throw new IllegalStateException("Stale scenery pick key");
				models.put(model, ((long)x << 32) | (z & 0xffffffffL));
			}
		}
		return models;
	}

	private static int retainedModels(Map<RSModel, Long> first, Map<RSModel, Long> second) {
		int retained = 0;
		for (Map.Entry<RSModel, Long> entry : first.entrySet()) {
			if (entry.getValue().equals(second.get(entry.getKey()))) retained++;
		}
		return retained;
	}

	private static void verifyIncomingSceneryMutations(mudclient client, Method walk, int scene) throws Exception {
		World world = client.getWorld();
		int initial = awaitIncomingScenery(client, scene, 0, false);
		int[] counts = new int[3];
		for (int i = 0; i < counts.length; i++) counts[i] = world.getIncomingSurfaceSceneryCount(6, 11 + i);
		walkLeg(client, walk, 258, 609);
		int added = awaitIncomingScenery(client, scene, initial, true);
		walkLeg(client, walk, 259, 609);
		int removed = awaitIncomingScenery(client, scene, added, false);
		BrowserBootstrap.report("outer-coverage:scenery-before-scene-change:cells=" + Arrays.toString(counts)
			+ ":anvil-revisions=" + initial + "," + added + "," + removed);
	}

	private static int awaitIncomingScenery(mudclient client, int scene, int previous, boolean present) throws Exception {
		World world = client.getWorld();
		long deadline = System.currentTimeMillis() + 25_000;
		while (System.currentTimeMillis() < deadline) {
			int revision = world.getIncomingSurfaceSceneryRevision(6, 12);
			int flags = world.getIncomingSurfaceCollisionFlags(305, 620);
			if (client.packetHandler.getPersistentMovementReceipt()[3] != scene || client.shouldRetainLastPresentedFrame()) {
				throw new IllegalStateException("Scene changed or held while the incoming cell was publishing");
			}
			if (revision > previous && flags >= 0
				&& world.getIncomingSurfaceSceneryRevision(6, 11) > 0 && world.getIncomingSurfaceSceneryRevision(6, 13) > 0
				&& world.getIncomingSurfaceSceneryObjectCount(305, 620, 50) == (present ? 1 : 0)
				&& ((flags & 64) != 0) == present) return revision;
			Thread.sleep(100);
		}
		throw new IllegalStateException("Missing incoming scenery update after revision " + previous + " expecting anvil=" + present
			+ " counts=" + world.getIncomingSurfaceSceneryCount(6, 11) + "," + world.getIncomingSurfaceSceneryCount(6, 12)
			+ "," + world.getIncomingSurfaceSceneryCount(6, 13) + " flags=" + world.getIncomingSurfaceCollisionFlags(305, 620));
	}

	private static void runSustainedWalk(mudclient client) throws Exception {
		Method walk = fieldWalk();
		int[][] destinations = {{284, 609}, {291, 597}, {322, 597}, {291, 597}, {284, 609}, {255, 609}};
		for (int[] destination : destinations) walkLeg(client, walk, destination[0], destination[1]);
		Thread.sleep(8_000L);
		reportTerrainState(client.getWorld());
		BrowserBootstrap.report("outer-coverage:complete:sustained");
	}

	private static Method fieldWalk() throws Exception {
		Method walk = mudclient.class.getDeclaredMethod("walkToArea", int.class, int.class, int.class,
			int.class, int.class, int.class, boolean.class, boolean.class);
		walk.setAccessible(true);
		return walk;
	}

	private static void walkLeg(mudclient client, Method walk, int x, int z) throws Exception {
		BrowserBootstrap.report("outer-coverage:walking:" + worldX(client) + "," + worldZ(client) + "->" + x + "," + z);
		int localX = x - client.getMidRegionBaseX(), localZ = z - client.getMidRegionBaseZ();
		walk.invoke(client, client.getLocalPlayerX(), client.getLocalPlayerZ(), localX, localZ, localX, localZ, false, false);
		long deadline = System.currentTimeMillis() + 48_000;
		while ((worldX(client) != x || worldZ(client) != z || worldReady() != 1) && System.currentTimeMillis() < deadline) {
			Thread.sleep(75L);
		}
		if (worldX(client) != x || worldZ(client) != z || worldReady() != 1) {
			throw new IllegalStateException("Walk ended at " + worldX(client) + "," + worldZ(client)
				+ " while walking to " + x + "," + z);
		}
		BrowserBootstrap.report("outer-coverage:arrived:" + x + "," + z);
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
