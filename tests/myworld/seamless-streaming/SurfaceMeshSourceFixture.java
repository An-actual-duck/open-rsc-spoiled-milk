package orsc.graphics.three;

import com.openrsc.client.entityhandling.EntityHandler;
import org.json.JSONObject;
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
			fixed.setAccessible(true);
			legacy.setAccessible(true);
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
				output.writeObject(fixed.invoke(mesh));
				output.writeObject(legacy.invoke(mesh, cell[0], cell[1]));
			}
			if (!Arrays.equals(buffer.toByteArray(), world.buildSurfaceMeshPacket(footprint, 0, 0, cell[0], cell[1], true))) {
				throw new AssertionError("Worker changed the terrain/wall/roof meshes at " + x + "," + z);
			}
		}
		System.out.println("PASS signed fixed and compatibility meshes match across the worker transport");
		verifyGameplayProduct(world, field.toAtomicActivationInnerWindow(), slice);
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
		} finally {
			World.installSurfaceMeshWorker(null);
		}
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
