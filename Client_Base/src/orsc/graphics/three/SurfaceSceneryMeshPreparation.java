package orsc.graphics.three;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/** Transfers frozen static models to the isolated browser scenery mesher. */
public final class SurfaceSceneryMeshPreparation {
	private static final int MAX_MODELS = 512;
	private static final int MAX_BYTES = 4 * 1024 * 1024;
	private static volatile Worker browserWorker;

	private SurfaceSceneryMeshPreparation() { }

	@FunctionalInterface
	public interface Worker {
		byte[] build(byte[] source);
	}

	public static void install(Worker worker) { browserWorker = worker; }

	public static boolean isAvailable() { return browserWorker != null; }

	public static Renderer3DWorldChunkFrame.ChunkMesh prepare(int sectionX, int sectionZ, int originX, int originZ,
		RSModel[] models) {
		Worker worker = browserWorker;
		if (worker == null || models.length == 0 || models.length > MAX_MODELS) return null;
		try {
			byte[] request = encode(sectionX, sectionZ, originX, originZ, models);
			byte[] response = request.length <= MAX_BYTES ? worker.build(request) : null;
			if (response == null) return null;
			try (ObjectInputStream input = World.openSurfaceProduct(response, MAX_BYTES)) {
				Renderer3DWorldChunkFrame.ChunkMesh result = World.readSurfaceMesh(input, sectionX, sectionZ);
				if (result == null || result.getOriginWorldX() != originX || result.getOriginWorldZ() != originZ
					|| result.getChunkRole() != Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS || input.read() != -1) {
					throw new IOException("Detached scenery mesh identity");
				}
				return result;
			}
		} catch (IOException | ClassNotFoundException | IllegalStateException failed) {
			return null;
		}
	}

	private static byte[] encode(int sectionX, int sectionZ, int originX, int originZ, RSModel[] models) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		WholeRamData.Pool pool = new WholeRamData.Pool(MAX_BYTES);
		try (DataOutputStream output = new DataOutputStream(bytes)) {
			output.writeInt(sectionX);
			output.writeInt(sectionZ);
			output.writeInt(originX);
			output.writeInt(originZ);
			output.writeInt(models.length);
			for (RSModel model : models) output.writeInt(RSModel.capturePreparedState(model, pool));
			pool.write(output);
		}
		return bytes.toByteArray();
	}

	public static byte[] buildPacket(byte[] source) throws IOException {
		if (source.length > MAX_BYTES) throw new IOException("Detached scenery input bounds");
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(source))) {
			int sectionX = input.readInt(), sectionZ = input.readInt(), x = input.readInt(), z = input.readInt();
			int count = input.readInt();
			if (count < 1 || count > MAX_MODELS) throw new IOException("Detached scenery model count");
			int[] refs = new int[count];
			for (int i = 0; i < refs.length; i++) refs[i] = input.readInt();
			WholeRamData.Pool pool = WholeRamData.Pool.read(input);
			if (input.read() != -1) throw new IOException("Trailing detached scenery data");
			RSModel[] models = new RSModel[refs.length];
			for (int i = 0; i < refs.length; i++) models[i] = RSModel.instantiatePreparedState(refs[i], pool);
			Renderer3DWorldChunkFrame.ChunkMesh result = RSModel.buildRenderer3DObjectChunkMesh(0, sectionX, sectionZ, x, z,
				models, models.length, Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
				output.writeObject(result);
			}
			return bytes.toByteArray();
		}
	}
}
