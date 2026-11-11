package orsc.graphics.three;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import orsc.BrowserFrameSinkRegistry;

/** Executes the extracted actual mudclient roof block and real Scene methods.
 * The runner compiles a separately frozen pre-change Scene and roof block.
 * Does not start a browser, GPU, server or database.
 */
public final class BrowserRoofMembershipFixture {
	static int checks, nextKey = 100;
	static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
	static Field field(Class<?> owner, String name) throws Exception {
		Field f = owner.getDeclaredField(name); f.setAccessible(true); return f;
	}
	public static final class Grid {
		public RSModel[][] modelRoofGrid, modelWallGrid;
		Grid(int length) {
			modelRoofGrid = new RSModel[4][length]; modelWallGrid = new RSModel[4][length];
			for (int plane = 0; plane < 4; plane++) for (int n = 0; n < length; n++) {
				modelRoofGrid[plane][n] = model(Renderer3DModelKind.ROOF);
				modelWallGrid[plane][n] = model(Renderer3DModelKind.WALL);
			}
		}
	}
	public static final class Settings { public int C_LAST_ZOOM; }
	static RSModel model(Renderer3DModelKind kind) {
		RSModel m = ClericWellSpotClientTest.horizontal(24, -40 - nextKey % 11, -12345);
		m.key = nextKey++; m.setRenderer3DModelKind(kind); return m;
	}
	static void configure() {
		for (String flag : new String[]{"renderer3DGeometryCapture", "openglWorldMesh", "openglWorldMeshTexturedVisible",
			"openglWorldMeshTexturedStaticVisible", "openglWorldChunksTexturedVisible", "openglWorldChunksReplacementComposite",
			"openglWorldChunksTrustedReplacement", "openglWorldChunksResidentObjects", "skipLegacyWorldRaster",
			"browserEarlyFaceCull", "browserOverlayCoverage"}) System.setProperty("spoiledmilk." + flag, "true");
		System.setProperty("spoiledmilk.renderer3DVisibleWorld", "false");
	}
	static void hash(MessageDigest h, String text) throws Exception {
		h.update(text.getBytes("UTF-8")); h.update((byte)0);
	}
	static void roster(MessageDigest h, Scene scene) throws Exception {
		int count = field(Scene.class, "modelCount").getInt(scene);
		RSModel[] models = (RSModel[])field(Scene.class, "models").get(scene);
		int[] metadata = (int[])field(Scene.class, "m_jb").get(scene);
		hash(h, "count=" + count);
		for (int n = 0; n < count; n++) hash(h, (models[n] == null ? "null" : String.valueOf(models[n].key)) + ":" + metadata[n]);
	}
	static void publish(Scene scene, Grid grid) {
		for (int plane = 0; plane < 4; plane++) for (int n = 0; n < grid.modelRoofGrid[plane].length; n++) {
			scene.addModel(grid.modelRoofGrid[plane][n]); scene.addModel(grid.modelWallGrid[plane][n]);
		}
	}
	static void differential(String version, boolean requested) throws Exception {
		configure(); MessageDigest digest = MessageDigest.getInstance("SHA-256");
		boolean current = version.equals("current");
		int reused = 0, reconciled = 0, avoided = 0, frames = 0;
		for (int mode = 0; mode < 4; mode++) {
			BrowserFrameSinkRegistry.install(mode == 1 ? null : BrowserWallRoofOwnershipFixture.sink(mode == 2));
			BrowserWallRoofOwnershipFixture.View view = new BrowserWallRoofOwnershipFixture.View(mode != 3);
			Grid grid = new Grid(4); publish(view.s, grid);
			RoofMembershipDraw draw = new RoofMembershipDraw(view.s, grid, requested);
			RSModel replaced = grid.modelRoofGrid[0][0];
			RSModel transientModel = null;
			for (int frame = 0; frame < 120; frame++) {
				// Long stable runs plus roof option/coverage/plane and camera changes.
				int phase = frame / 15;
				draw.lastHeightOffset = phase % 4;
				draw.visibility = Renderer3DRoofVisibility.resolve(phase == 2, draw.lastHeightOffset, phase == 3);
				draw.osConfig.C_LAST_ZOOM = frame % 101;
				if (frame == 12) grid.modelRoofGrid[0][0] = model(Renderer3DModelKind.ROOF); // same row object
				if (frame == 13) grid.modelRoofGrid[0][0] = replaced;
				if (frame == 16) grid.modelRoofGrid[1][1] = grid.modelRoofGrid[1][0]; // duplicate target fallback
				if (frame == 18) grid.modelRoofGrid[1][1] = model(Renderer3DModelKind.ROOF);
				if (frame == 21) grid.modelRoofGrid[1][2] = null;
				if (frame == 25) grid.modelRoofGrid[1][2] = model(Renderer3DModelKind.ROOF);
				if (frame == 32) {
					transientModel = model(Renderer3DModelKind.GAME_OBJECT);
					view.s.addModel(transientModel);
				}
				if (frame == 34) view.s.removeModel(transientModel);
				if (frame == 37) { view.s.addModel(replaced); view.s.addModel(replaced); }
				if (frame == 40) view.s.removeModels(new RSModel[]{replaced}, 1);
				if (frame == 45) { view.s.removeAllGameObjects(false); publish(view.s, grid); }
				if (frame == 60) {
					grid.modelRoofGrid[0] = grid.modelRoofGrid[0].clone();
					grid.modelWallGrid[1] = grid.modelWallGrid[1].clone();
				}
				if (frame == 75) {
					view = new BrowserWallRoofOwnershipFixture.View(mode != 3);
					draw.scene = view.s; publish(view.s, grid);
				}
				if (frame == 90) {
					grid = new Grid(4); draw.world = grid;
					view.s.removeAllGameObjects(false); publish(view.s, grid);
				}
				if (frame == 100) view.s.forceLegacyWorldRasterOnce();
				// Transform mutations are not membership mutations and need no reconcile.
				if (transientModel != null) transientModel.addRotation(0, 1, 0);
				draw.draw(); roster(digest, view.s);
				hash(digest, draw.amountToZoom + ":" + draw.cameraZoom + ":" + draw.doCameraZoom);
				view.s.setRendererSignedWorldLevel(new int[]{-2,-1,0,1,10}[frame % 5]);
				BrowserWallRoofOwnershipFixture.Result result = view.draw(new int[]{256,210,0,511}[frame % 4],
					new int[]{192,160,0,383}[frame % 4], new int[]{20,100,600}[frame % 3],
					new int[]{640,768,900}[frame % 3], frame % 2 * 256, -12345, false);
				hash(digest, result.picks); hash(digest, result.anchors); hash(digest, result.faces); hash(digest, result.resident);
				ByteBuffer pixels = ByteBuffer.allocate(result.pixels.length * 4);
				for (int pixel : result.pixels) pixels.putInt(pixel); digest.update(pixels.array());
				frames++;
			}
			int[] work = draw.browserRoofMembership == null ? new int[7] : draw.browserRoofMembership.work();
			if (current && requested && mode == 0) check(work[2] > 50 && work[4] > 100, "No useful stable retention occurred");
			if (mode != 0 || !requested || !current) check(work[2] == 0, "Retention escaped private browser GPU ownership");
			reused += work[2]; reconciled += work[1]; avoided += work[4];
		}
		BrowserFrameSinkRegistry.install(null);
		StringBuilder hex = new StringBuilder(); for (byte b : digest.digest()) hex.append(String.format("%02x", b & 255));
		System.out.println("frames=" + frames + " reused=" + reused + " reconciled=" + reconciled + " avoidedRemovals=" + avoided);
		System.out.println(hex);
	}
	static void revisions() throws Exception {
		configure(); BrowserFrameSinkRegistry.install(BrowserWallRoofOwnershipFixture.sink(false));
		BrowserWallRoofOwnershipFixture.View view = new BrowserWallRoofOwnershipFixture.View(true);
		Scene s = view.s; int revision = s.modelMembershipRevision(); Object epoch = s.modelMembershipEpoch();
		s.addModel(null); s.removeModel(null); s.removeModel(model(Renderer3DModelKind.ROOF));
		s.removeModels(new RSModel[0], 0);
		check(revision == s.modelMembershipRevision() && epoch == s.modelMembershipEpoch(), "No-op changed membership authority");
		RSModel m = model(Renderer3DModelKind.ROOF); s.addModel(m);
		check(s.modelMembershipRevision() != revision, "Add did not change revision"); revision = s.modelMembershipRevision();
		s.removeModel(m); check(s.modelMembershipRevision() != revision, "Remove did not change revision");
		s.addModel(m); revision = s.modelMembershipRevision(); s.removeModels(new RSModel[]{m}, 1);
		check(s.modelMembershipRevision() != revision, "Batch remove did not change revision");
		revision = s.modelMembershipRevision(); s.removeAllGameObjects(false);
		check(s.modelMembershipRevision() != revision, "Reset did not change revision");
		Grid grid = new Grid(144); RoofMembershipDraw draw = new RoofMembershipDraw(s, grid, true);
		draw.draw(); draw.draw(); check(draw.browserRoofMembership.work()[2] == 1, "720-reference real bound not reusable");
		field(Scene.class, "modelMembershipRevision").setInt(s, Integer.MAX_VALUE); epoch = s.modelMembershipEpoch();
		s.addModel(m); check(s.modelMembershipRevision() == 0 && s.modelMembershipEpoch() != epoch, "Revision wrap aliases old ownership");
		draw.draw(); check(draw.browserRoofMembership.work()[2] == 1, "Epoch replacement failed to invalidate retention");
		// Duplicated roster/targets keep repeated literal semantics; they are not normalized.
		grid.modelRoofGrid[0][1] = grid.modelRoofGrid[0][0]; draw.draw(); draw.draw();
		check(draw.browserRoofMembership.work()[2] == 1, "Duplicate targets were cached");
		grid.modelRoofGrid[0][1] = null; s.removeModels(new RSModel[]{grid.modelRoofGrid[0][0]}, 1); draw.draw(); draw.draw();
		check(draw.browserRoofMembership.work()[2] == 2, "Null target prevented valid retention");
		BrowserFrameSinkRegistry.install(null);
		System.out.println("PASS " + checks + " roster revision/epoch/no-op/max720/duplicate/null checks");
	}
	static void edgeOutcomes(String version, boolean requested) throws Exception {
		configure(); BrowserFrameSinkRegistry.install(BrowserWallRoofOwnershipFixture.sink(false));
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		for (int variant = 0; variant < 8; variant++) {
			BrowserWallRoofOwnershipFixture.View view = new BrowserWallRoofOwnershipFixture.View(true);
			Grid grid = new Grid(variant == 0 ? 0 : variant == 1 ? 145 : 3);
			publish(view.s, grid); RoofMembershipDraw draw = new RoofMembershipDraw(view.s, grid, requested);
			draw.draw();
			if (variant == 2) grid.modelRoofGrid[1] = new RSModel[1];
			if (variant == 3) grid.modelWallGrid[2] = null;
			if (variant == 4) draw.lastHeightOffset = -1;
			if (variant == 5) draw.lastHeightOffset = 4;
			if (variant == 6) { grid.modelRoofGrid[0][0] = grid.modelRoofGrid[0][1]; view.s.addModel(grid.modelRoofGrid[0][1]); }
			if (variant == 7) { grid.modelRoofGrid[0] = new RSModel[3]; grid.modelRoofGrid[1] = new RSModel[3]; }
			for (int repeat = 0; repeat < 3; repeat++) {
				String outcome = "ok";
				try { draw.draw(); } catch (RuntimeException failure) { outcome = failure.getClass().getName(); }
				hash(digest, variant + ":" + outcome); roster(digest, view.s);
				hash(digest, draw.amountToZoom + ":" + draw.cameraZoom + ":" + draw.doCameraZoom);
			}
		}
		BrowserFrameSinkRegistry.install(null);
		StringBuilder hex = new StringBuilder(); for (byte b : digest.digest()) hex.append(String.format("%02x", b & 255));
		System.out.println("edgeOutcomes=24 empty/oversize/short/null/invalidplane/duplicate/nullreferences");
		System.out.println(hex);
	}
	public static void main(String[] args) throws Exception {
		if (args[0].equals("revisions")) revisions();
		else if (args[0].equals("edges")) edgeOutcomes(args[1], args[2].equals("true"));
		else differential(args[0], args[1].equals("true"));
	}
}
