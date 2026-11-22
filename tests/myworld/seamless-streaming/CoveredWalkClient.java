package orsc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import orsc.graphics.gui.Panel;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.World;

/** Drives the normal native login and walk requests against a disposable server. */
public final class CoveredWalkClient {

	public static void main(String[] args) throws Exception {
		boolean live = args.length == 3 && "--live-surface".equals(args[2]);
		boolean longerRoute = live || args.length == 3 && "--outer-coverage".equals(args[2]);
		if ((args.length != 2 && !longerRoute) || (!"true".equals(args[1]) && !"false".equals(args[1]))) {
			throw new IllegalArgumentException("Expected the private server status path, preview flag, and optional outer route");
		}
		Path status = Paths.get(args[0]).toRealPath();
		boolean preview = Boolean.parseBoolean(args[1]);
		if (longerRoute && !preview) {
			throw new IllegalArgumentException("The outer coverage route requires the preview");
		}
		String password = System.getenv("SPOILED_MILK_PRIVATE_WALK_PASSWORD");
		Path run = status.getParent();
		if (password == null || !Files.isRegularFile(run.resolve("server/BROWSER-PRIVATE.json"))
			|| !Paths.get(".").toRealPath().equals(run.resolve("client"))
			|| !"127.0.0.1".equals(new String(Files.readAllBytes(Paths.get("Cache/ip.txt")), "UTF-8").trim())) {
			throw new IllegalArgumentException("Start the disposable preview and supply its test login");
		}
		try {
			run(status, preview, password, longerRoute, live);
			System.exit(0);
		} catch (Throwable failure) {
			failure.printStackTrace();
			System.exit(1);
		}
	}

	private static void run(Path status, boolean preview, String password, boolean longerRoute, boolean live) throws Exception {
		if (live) {
			for (String feature : new String[] {"persistentMovementPreview", "persistentSurfaceCollision", "surfaceSceneryCells",
				"rollingSurfaceWindow", "fixedSurfaceDraw", "expandedSurfaceDrawBorder", "surfaceSceneryDraw", "liveSurfaceHandoff"}) {
				System.setProperty("spoiledmilk." + feature, "true");
			}
		}
		OpenRSC.main(new String[0]);
		Field game = field(ORSCApplet.class, "mudclient");
		Field loginPanel = field(mudclient.class, "panelLogin");
		mudclient client = null;
		long deadline = deadline(60);
		while (System.nanoTime() < deadline) {
			client = (mudclient) game.get(null);
			if (client != null && loginPanel.get(client) != null) {
				break;
			}
			Thread.sleep(50);
		}
		if (client == null || loginPanel.get(client) == null) {
			throw new AssertionError("Native client did not reach login");
		}
		Method login = mudclient.class.getDeclaredMethod(
			"login", int.class, String.class, String.class, boolean.class);
		login.setAccessible(true);
		field(mudclient.class, "autoLoginTimeout").setInt(client, 3);
		login.invoke(client, -12, password, "browserdemo", false);

		LayeredSceneContextState context = (LayeredSceneContextState) field(
			PacketHandler.class, "layeredSceneContextState").get(field(mudclient.class, "packetHandler").get(client));
		Field appearanceVisible = field(mudclient.class, "showAppearanceChange");
		Field appearancePanel = field(mudclient.class, "panelAppearance");
		Field appearanceButton = field(mudclient.class, "controlButtonAppearanceAccept");
		Field clicked = field(Panel.class, "controlClicked");
		deadline = deadline(60);
		while (System.nanoTime() < deadline && (!client.isGameView()
			|| client.getLocalPlayer() == null || !context.hasContext()
			|| client.isAreaLoadPending() || client.shouldRetainLastPresentedFrame()
			|| client.getScene().getRenderer3DFrame() == null)) {
			if (appearanceVisible.getBoolean(client)) {
				boolean[] controls = (boolean[]) clicked.get(appearancePanel.get(client));
				controls[appearanceButton.getInt(client)] = true;
			}
			Thread.sleep(50);
		}
		if (!client.isGameView() || !context.hasContext() || client.shouldRetainLastPresentedFrame()
			|| client.getLocalPlayer() == null || client.getScene().getRenderer3DFrame() == null) {
			throw new AssertionError("Native client did not publish the initial layered scene");
		}
		int startX = client.getMidRegionBaseX() + client.getLocalPlayerX();
		int startZ = client.getMidRegionBaseZ() + client.getLocalPlayerZ();
		if (longerRoute) {
			if (startX != 239 || startZ != 609) {
				throw new AssertionError("Outer coverage route needs the private spawn at 239,609");
			}
			if (live) {
				ORSCharacter actor = client.getLocalPlayer();
				long travelDeadline = deadline(10);
				while (client.packetHandler.getPersistentMovementGeneration() == 0 && System.nanoTime() < travelDeadline) Thread.sleep(20);
				int generation = client.packetHandler.getPersistentMovementGeneration();
				int sequence = context.getSequence();
				int[][] legs = {{257, 609, 264}, {278, 609, 264}, {284, 609, 312}, {291, 597, 312},
					{322, 597, 312}, {291, 597, 264}, {284, 609, 264}, {252, 609, 264}};
				for (int[] leg : legs) walk(client, status, context, leg[0], leg[1], leg[2], false, true);
				int[] movement = client.packetHandler.getPersistentMovementReceipt();
				if (generation <= 0 || movement[0] != generation || movement[2] != 0 || client.getLocalPlayer() != actor
					|| client.getRollingSurfaceWindows() != 3 || context.getSequence() != sequence + 3) {
					throw new AssertionError("Native walking replaced authority or missed a verified crossing");
				}
				System.out.printf("PASS native rolling travel, generation %d, contexts %d/%d, rolls %d%n",
					generation, sequence, context.getSequence(), client.getRollingSurfaceWindows());
				return;
			}
			int startSequence = context.getSequence();
			int result = walk(client, status, context, 269, 609, 264, false, true);
			if (result != startSequence + 1) {
				throw new AssertionError("Outer coverage route did not publish exactly one new window");
			}
			System.out.printf("PASS outer coverage route reached 269,609, epochs %d/%d%n", startSequence, result);
			return;
		}
		int seam = (Math.floorDiv(startZ, World.SECTION_SIZE) + 1) * World.SECTION_SIZE;
		int targetX = chooseTarget(client, startX, seam + 2);
		int startSequence = context.getSequence();
		System.out.printf("ROUTE %d,%d to %d,%d over y=%d startSequence=%d%n",
			startX, startZ, targetX, seam + 2, seam, startSequence);
		int outward = walk(client, status, context, targetX, seam + 2, seam, preview, false);
		int returned = walk(client, status, context, startX, startZ, seam, preview, false);
		if (preview ? outward != startSequence || returned != startSequence
			: outward <= startSequence || returned <= outward) {
			throw new AssertionError("Unexpected context sequence across the verified two-way route");
		}
		System.out.printf("PASS %s server positions and native context across two crossings, epochs %d/%d/%d%n",
			preview ? "covered walk" : "control", startSequence, outward, returned);
	}

	private static int chooseTarget(mudclient client, int startX, int targetZ) {
		int localZ = targetZ - client.getMidRegionBaseZ();
		int[] pathX = new int[World.LOCAL_TILE_COUNT * World.LOCAL_TILE_COUNT];
		int[] pathZ = new int[pathX.length];
		for (int offset = 0; offset <= 16; offset++) {
			for (int direction : new int[]{1, -1}) {
				int x = startX + offset * direction;
				int localX = x - client.getMidRegionBaseX();
				if (!World.isLocalFaceTile(localX, localZ)) {
					continue;
				}
				int length = client.getWorld().findPath(pathX, pathZ, client.getLocalPlayerX(),
					client.getLocalPlayerZ(), localX, localX, localZ, localZ, false);
				if (length > 0 && length <= 25) {
					return x;
				}
			}
		}
		throw new AssertionError("No normal walk command can reach the next section from the test spawn");
	}

	private static int walk(mudclient client, Path status, LayeredSceneContextState context,
		int targetX, int targetZ, int seam, boolean preview, boolean crossX) throws Exception {
		Method walk = mudclient.class.getDeclaredMethod("walkToArea", int.class, int.class,
			int.class, int.class, int.class, int.class, boolean.class, boolean.class);
		walk.setAccessible(true);
		int x = targetX - client.getMidRegionBaseX();
		int z = targetZ - client.getMidRegionBaseZ();
		walk.invoke(client, client.getLocalPlayerX(), client.getLocalPlayerZ(), x, z, x, z, false, false);
		long deadline = deadline(50);
		int lastTile = Integer.MIN_VALUE;
		int changingCamera = 0;
		int cameraX = Integer.MIN_VALUE;
		int cameraZ = Integer.MIN_VALUE;
		int changingActor = 0;
		int actorX = Integer.MIN_VALUE;
		int actorZ = Integer.MIN_VALUE;
		int walkPhase = -1;
		int changingWalkPhases = 0;
		int freshFrames = 0;
		Renderer3DFrame previousFrame = null;
		Map<Integer, ORSCharacter> npcsBefore = null;
		int retainedNpcs = 0;
		int replacedNpcs = 0;
		int beforeSeam = (crossX ? targetX : targetZ) >= seam ? seam - 1 : seam;
		int afterSeam = beforeSeam == seam ? seam - 1 : seam;
		int retainedSamples = 0;
		Field cameraPositionX = field(mudclient.class, "cameraPositionX");
		Field cameraPositionZ = field(mudclient.class, "cameraPositionZ");
		long lastFrameNanos = System.nanoTime();
		long longestFrameGapNanos = 0;
		while (System.nanoTime() < deadline) {
			int tile = crossX ? client.getMidRegionBaseX() + client.getLocalPlayerX()
				: client.getMidRegionBaseZ() + client.getLocalPlayerZ();
			if (tile != lastTile && Math.abs(tile - seam) <= 3) {
				System.out.printf("SEAM %s=%d context=%d retain=%s%n", crossX ? "x" : "y", tile, context.getSequence(),
					client.shouldRetainLastPresentedFrame());
			}
			if (tile != lastTile && tile == beforeSeam) {
				npcsBefore = nearbyNpcs(client);
			}
			if (tile != lastTile && tile == afterSeam && npcsBefore != null) {
				for (Map.Entry<Integer, ORSCharacter> npc : nearbyNpcs(client).entrySet()) {
					ORSCharacter previous = npcsBefore.get(npc.getKey());
					if (previous == npc.getValue()) retainedNpcs++;
					else if (previous != null) replacedNpcs++;
				}
			}
			lastTile = tile;
			if (client.shouldRetainLastPresentedFrame()) {
				retainedSamples++;
			}
			int worldCameraX = cameraPositionX.getInt(client) + client.getMidRegionBaseX() * 128;
			int worldCameraZ = cameraPositionZ.getInt(client) + client.getMidRegionBaseZ() * 128;
			if (worldCameraX != cameraX || worldCameraZ != cameraZ) {
				cameraX = worldCameraX;
				cameraZ = worldCameraZ;
				changingCamera++;
			}
			ORSCharacter player = client.getLocalPlayer();
			if (player != null && (player.currentX != actorX || player.currentZ != actorZ)) {
				actorX = player.currentX;
				actorZ = player.currentZ;
				changingActor++;
			}
			if (player != null && player.stepFrame / 6 % 4 != walkPhase) {
				walkPhase = player.stepFrame / 6 % 4;
				changingWalkPhases++;
			}
			Renderer3DFrame frame = client.getScene().getRenderer3DFrame();
			if (frame != null && frame != previousFrame) {
				previousFrame = frame;
				freshFrames++;
				long now = System.nanoTime();
				longestFrameGapNanos = Math.max(longestFrameGapNanos, now - lastFrameNanos);
				lastFrameNanos = now;
			}
			if (client.getMidRegionBaseZ() + client.getLocalPlayerZ() == targetZ
				&& client.getMidRegionBaseX() + client.getLocalPlayerX() == targetX
				&& (!preview || !client.shouldRetainLastPresentedFrame())) {
				JSONObject server = new JSONObject(new String(Files.readAllBytes(status), "UTF-8"));
				if (server.optBoolean("online") && server.optInt("x") == targetX && server.optInt("y") == targetZ) {
					if (preview && (changingCamera < 4 || changingActor < 4 || changingWalkPhases < 4
						|| freshFrames < 4 || retainedSamples != 0 || retainedNpcs == 0 || replacedNpcs != 0)) {
						throw new AssertionError("Covered walk lost camera, actor or scene continuity");
					}
					System.out.printf("LEG server=%d,%d cameraSamples=%d actorSamples=%d walkPhases=%d frames=%d "
						+ "retainedSamples=%d sharedNpcInstances=%d replacedNpcInstances=%d context=%d maxJavaFrameGapMs=%d%n",
						targetX, targetZ, changingCamera, changingActor, changingWalkPhases, freshFrames,
						retainedSamples, retainedNpcs, replacedNpcs, context.getSequence(),
						TimeUnit.NANOSECONDS.toMillis(longestFrameGapNanos));
					return context.getSequence();
				}
			}
			Thread.sleep(12);
		}
		throw new AssertionError("Walk did not reach " + targetX + "," + targetZ + ", last seam-axis tile=" + lastTile);
	}

	private static Map<Integer, ORSCharacter> nearbyNpcs(mudclient client) {
		Map<Integer, ORSCharacter> nearby = new HashMap<>();
		ORSCharacter local = client.getLocalPlayer();
		if (local != null) {
			int count = client.getNpcCount();
			for (int i = 0; i < count; i++) {
				ORSCharacter npc = client.getNpc(i);
				if (npc != null && Math.abs(npc.currentX - local.currentX) <= 16 * 128
					&& Math.abs(npc.currentZ - local.currentZ) <= 16 * 128) {
					nearby.put(npc.serverIndex, npc);
				}
			}
		}
		return nearby;
	}

	private static Field field(Class<?> owner, String name) throws Exception {
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	private static long deadline(int seconds) {
		return System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
	}
}
