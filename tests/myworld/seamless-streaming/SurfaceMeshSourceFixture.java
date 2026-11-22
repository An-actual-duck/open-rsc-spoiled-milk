package orsc.graphics.three;

import com.openrsc.client.entityhandling.EntityHandler;
import org.json.JSONObject;
import orsc.BrowserWebGLBridge;
import orsc.Config;
import orsc.NativeLayeredTerrainChunk;
import orsc.NativeLayeredTerrainSnapshot;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Checks the worker transport against the renderer using the same signed map. */
public final class SurfaceMeshSourceFixture {
	public static void main(String[] args) throws Exception {
		Path packageRoot = Paths.get(args[0]);
		byte[] manifestBytes = Files.readAllBytes(packageRoot.resolve("manifest.json"));
		String manifestHash = sha256(manifestBytes);
		JSONObject manifest = new JSONObject(new String(manifestBytes, StandardCharsets.UTF_8));
		Map<String, JSONObject> surface = new HashMap<>();
		for (Object value : manifest.getJSONArray("terrainSectors")) {
			JSONObject row = (JSONObject) value;
			if ("global".equals(row.getString("worldSpace")) && row.getInt("level") == 0) {
				surface.put(row.getInt("sectorX") + "," + row.getInt("sectorY"), row);
			}
		}
		int section = 48, centerX = 5, centerZ = 12, index = 0;
		NativeLayeredTerrainChunk[] chunks = new NativeLayeredTerrainChunk[49];
		for (int x = centerX - 3; x <= centerX + 3; x++) {
			for (int z = centerZ - 3; z <= centerZ + 3; z++) {
				JSONObject data = surface.get(x + "," + z);
				if (data == null) {
					chunks[index++] = NativeLayeredTerrainChunk.voidChunk(section, x, z);
					continue;
				}
				byte[] tiles = Files.readAllBytes(packageRoot.resolve(data.getString("path")));
				String hash = sha256(tiles);
				if (!hash.equals(data.getString("sha256"))) throw new AssertionError("Terrain source mismatch");
				chunks[index++] = NativeLayeredTerrainChunk.available(section, x, z, x, z,
					data.getString("encoding"), hash, tiles);
			}
		}
		NativeLayeredTerrainSnapshot field = new NativeLayeredTerrainSnapshot(
			NativeLayeredTerrainSnapshot.SYMMETRIC_STRUCTURE_PROTOCOL_VERSION, manifest.getString("packageId"),
			manifest.getString("packageVersion"), manifestHash, section, "global", 0, centerX, centerZ, 3, chunks);
		Config.S_WANT_CUSTOM_SPRITES = true;
		EntityHandler.load(true);
		World world = new World(null, null);
		Class<?> slice = Class.forName(World.class.getName() + "$TerrainPredictionSlice");
		Method local = World.class.getDeclaredMethod("buildPresentationGeometry", int.class, int.class, int.class,
			NativeLayeredTerrainSnapshot.class, int.class, int.class, boolean.class, boolean.class, slice);
		local.setAccessible(true);
		for (int[] cell : new int[][] {{-2, 0}, {2, -2}, {2, 2}}) {
			int x = centerX + cell[0], z = centerZ + cell[1];
			byte[] footprint = field.surfaceWorkerSource(x, z);
			if (!Arrays.equals(footprint, NativeLayeredTerrainSnapshot.readSurfaceWorkerSource(footprint).surfaceWorkerSource(x, z))) {
				throw new AssertionError("Worker terrain footprint was changed");
			}
			Object mesh = local.invoke(world, 0, x, z, field, 0, 0, true, false, null);
			Method fixed = mesh.getClass().getDeclaredMethod("toRenderer3DFixedSurfaceCell");
			Method legacy = mesh.getClass().getDeclaredMethod("toRenderer3DWorldPresentationCell", int.class, int.class);
			Method full = mesh.getClass().getDeclaredMethod("toRenderer3DWorldChunkMesh");
			fixed.setAccessible(true);
			legacy.setAccessible(true);
			full.setAccessible(true);
			Renderer3DWorldChunkFrame.ChunkMesh fixedCell = (Renderer3DWorldChunkFrame.ChunkMesh) fixed.invoke(mesh);
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
				output.writeObject(fixedCell);
				output.writeObject(legacy.invoke(mesh, cell[0], cell[1]));
			}
			if (!Arrays.equals(buffer.toByteArray(), world.buildSurfaceMeshPacket(footprint, 0, 0, cell[0], cell[1], true))) {
				throw new AssertionError("Worker changed the terrain/wall/roof meshes at " + x + "," + z);
			}
			int[] words = world.buildSurfaceDrawWordPacket(footprint, 0, 0, cell[0], cell[1], true,
				BrowserWebGLBridge::prepareWorkerSurfaceLighting);
			byte[] serialized = world.buildSurfaceMeshPacket(footprint, 0, 0, cell[0], cell[1], true,
				BrowserWebGLBridge::prepareWorkerSurfaceLighting);
			Renderer3DWorldChunkFrame.ChunkMesh[] primitive = World.readSurfaceDrawWords(words, x, z);
			buffer.reset();
			try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
				output.writeObject(primitive[0]);
				output.writeObject(primitive[1]);
			}
			if (!Arrays.equals(buffer.toByteArray(), serialized)) {
				throw new AssertionError("Primitive terrain changed the draw meshes or prepared lighting at " + x + "," + z);
			}
			try {
				World.readSurfaceDrawWords(Arrays.copyOf(words, words.length - 1), x, z);
				throw new AssertionError("Truncated terrain words were accepted");
			} catch (IllegalStateException rejected) {
				// A rejected product can be rebuilt through the normal terrain path.
			}
			if (fixedCell != null) verifyOcclusionCell(fixedCell, (Renderer3DWorldChunkFrame.ChunkMesh) full.invoke(mesh));
		}
		System.out.println("PASS signed fixed and compatibility meshes match across byte and primitive worker transports");
		verifyGameplayProduct(world, field.toAtomicActivationInnerWindow(), slice);
		verifySceneryProduct();
	}

	private static void verifyOcclusionCell(Renderer3DWorldChunkFrame.ChunkMesh cell, Renderer3DWorldChunkFrame.ChunkMesh full) {
		int size = World.SECTION_SIZE, axis = size * 3;
		int[] heights = new int[axis * axis];
		Arrays.fill(heights, TerrainOcclusion.UNKNOWN);
		for (int t = 0; t < full.getTriangleCount(); t++) {
			if (full.getTriangleModelKind(t) != Renderer3DModelKind.TERRAIN) continue;
			for (int c = 0; c < 3; c++) {
				int vertex = full.getIndex(t * 3 + c) * 3;
				int x = full.getVertexCoord(vertex), z = full.getVertexCoord(vertex + 2);
				if (x < 0 || z < 0 || x >= axis * 128 || z >= axis * 128 || x % 128 != 0 || z % 128 != 0) continue;
				int at = z / 128 * axis + x / 128, y = full.getVertexCoord(vertex + 1);
				heights[at] = heights[at] == TerrainOcclusion.UNKNOWN ? y : Math.min(heights[at], y);
			}
		}
		byte[] reference = TerrainOcclusion.build(heights, axis, axis);
		byte[] row = new byte[size * 4];
		for (int z = 0; z < size; z++) {
			cell.copySurfaceOcclusionRow(z, row, 0);
			int at = ((z + size) * axis + size) * 4;
			if (!Arrays.equals(row, Arrays.copyOfRange(reference, at, at + row.length))) {
				throw new AssertionError("Resident lighting differs at cell row " + z);
			}
		}
	}

	private static void verifySceneryProduct() {
		RSModel model = new RSModel(3, 1);
		model.setRenderer3DModelKind(Renderer3DModelKind.GAME_OBJECT);
		int a = model.insertVertex(31, 10, 20), b = model.insertVertex(80, -26, 28), c = model.insertVertex(54, 2, 100);
		model.insertFace(3, new int[]{a, b, c}, -5312, -5312, false);
		model.setTranslate(660, -25, 1265);
		model.addRotation(0, 32, 0);
		model.setDiffuseLightAndColor(-50, -10, -50, 48, 48, true, 117);
		RSModel[] models = {model};
		SurfaceSceneryMeshPreparation.install(source -> {
			try {
				return SurfaceSceneryMeshPreparation.buildPacket(source);
			} catch (java.io.IOException failure) {
				throw new IllegalStateException(failure);
			}
		});
		try {
			Renderer3DWorldChunkFrame.ChunkMesh detached = SurfaceSceneryMeshPreparation.prepare(5, 12, 640, 1216, models, 0, 0, 0);
			Renderer3DWorldChunkFrame.ChunkMesh direct = RSModel.buildRenderer3DObjectChunkMesh(0, 5, 12, 640, 1216,
				models, 1, Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS);
			if (detached == null || detached.getTriangleCount() == 0 || detached.getStorageSignature() != direct.getStorageSignature()) {
				throw new AssertionError("Worker changed the frozen scenery model mesh");
			}
			System.out.println("PASS frozen static scenery matches across the worker transport");
		} finally {
			SurfaceSceneryMeshPreparation.install(null);
		}
	}

	private static void verifyGameplayProduct(World world, NativeLayeredTerrainSnapshot terrain, Class<?> slice) throws Exception {
		Class<?> incoming = Class.forName(World.class.getName() + "$IncomingSurfaceCollision");
		Method build = World.class.getDeclaredMethod("prebuildNativeWorldModelProduct", NativeLayeredTerrainSnapshot.class,
			int.class, int.class, long.class, boolean.class, slice, boolean.class, incoming);
		Method receive = World.class.getDeclaredMethod("prepareDetachedSurfaceGameplay", NativeLayeredTerrainSnapshot.class,
			int.class, int.class, long.class, boolean.class, incoming);
		build.setAccessible(true);
		receive.setAccessible(true);
		World worker = new World(null, null);
		World.installSurfaceMeshWorker((source, x, z, dx, dz, roofs, sprites, gameplay) -> {
			try {
				return worker.buildSurfaceGameplayPacket(source, x, z, roofs);
			} catch (java.io.IOException failure) {
				throw new IllegalStateException(failure);
			}
		});
		try {
			Object direct = build.invoke(world, terrain, 0, 0, 1L, true, null, true, null);
			Object detached = receive.invoke(world, terrain, 0, 0, 1L, true, null);
			Field drawMesh = direct.getClass().getDeclaredField("drawMesh");
			drawMesh.setAccessible(true);
			if (detached == null || !Arrays.equals(gameplayProduct(direct), gameplayProduct(detached))
				|| ((Renderer3DWorldChunkFrame.ChunkMesh) drawMesh.get(direct)).getStorageSignature()
					!= ((Renderer3DWorldChunkFrame.ChunkMesh) drawMesh.get(detached)).getStorageSignature()) {
				throw new AssertionError("Worker changed gameplay collision, minimap or pick geometry");
			}
			System.out.println("PASS signed gameplay product matches across the worker transport");
			int x = terrain.getCurrentChunkX(), z = terrain.getCurrentChunkY();
			int[] words = worker.buildSurfaceGameplayWordPacket(terrain.surfaceWorkerSource(x, z), 0, 0, true);
			if (words.length == 0) throw new AssertionError("Signed gameplay product rejected by primitive encoding");
			World.installSurfaceGameplayWordWorker((source, offsetX, offsetZ, roofs, sprites) -> words);
			Object primitive = receive.invoke(world, terrain, 0, 0, 1L, true, null);
			if (world.getAcceptedSurfaceGameplayWordReplies() != 1 || primitive == null
				|| !Arrays.equals(gameplayProduct(direct), gameplayProduct(primitive))
				|| !Arrays.equals(meshBytes((Renderer3DWorldChunkFrame.ChunkMesh) drawMesh.get(detached)),
					meshBytes((Renderer3DWorldChunkFrame.ChunkMesh) drawMesh.get(primitive)))) {
				throw new AssertionError("Primitive reply changed collision, minimap, roof or pick geometry");
			}
			rejectGameplayWords(Arrays.copyOf(words, words.length - 1), terrain.scopeIdentity(), x, z);
			rejectGameplayWords(words, terrain.scopeIdentity().replaceFirst(".", "!"), x, z);
			System.out.println("PASS signed gameplay primitive product matches collision, minimap, roof and pick geometry");
		} finally {
			World.installSurfaceMeshWorker(null);
			World.installSurfaceGameplayWordWorker(null);
		}
	}

	private static void rejectGameplayWords(int[] words, String scope, int x, int z) throws Exception {
		try {
			World.readSurfaceGameplayWords(words, scope, 1L, x, z, true);
			throw new AssertionError("Invalid primitive gameplay envelope accepted");
		} catch (java.io.IOException expected) { }
	}

	private static byte[] meshBytes(Renderer3DWorldChunkFrame.ChunkMesh mesh) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
			output.writeObject(mesh);
		}
		return bytes.toByteArray();
	}

	private static byte[] gameplayProduct(Object result) throws Exception {
		Field prepared = result.getClass().getDeclaredField("localTerrain");
		prepared.setAccessible(true);
		Object local = prepared.get(result);
		Method write = local.getClass().getDeclaredMethod("writeProduct", DataOutput.class);
		write.setAccessible(true);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream output = new DataOutputStream(bytes)) {
			write.invoke(local, output);
		}
		return bytes.toByteArray();
	}

	private static String sha256(byte[] bytes) throws Exception {
		StringBuilder hash = new StringBuilder();
		for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
			hash.append(Character.forDigit((value >>> 4) & 15, 16)).append(Character.forDigit(value & 15, 16));
		}
		return hash.toString();
	}
}
