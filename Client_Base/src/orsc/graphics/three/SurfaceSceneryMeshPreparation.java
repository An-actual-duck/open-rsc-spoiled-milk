package orsc.graphics.three;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.GameObjectDef;
import com.openrsc.data.DataFileDecrypter;
import com.openrsc.data.DataOperations;
import orsc.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/** Sends frozen models or canonical placements to the isolated browser scenery mesher. */
public final class SurfaceSceneryMeshPreparation {
	private static final int MAX_MODELS = 512;
	private static final int MAX_BYTES = 4 * 1024 * 1024;
	private static final int PLACEMENTS = 0x53434e31;
	private static final int PLACEMENT_WORDS = 7;
	private static volatile Worker browserWorker;
	private static volatile WordWorker wordWorker;
	private static volatile int[] packingContext = new int[0];
	private static final boolean TRACE = !Boolean.getBoolean("spoiledmilk.browserPublicAlpha")
		&& Boolean.getBoolean("spoiledmilk.browserFrameStageTrace");
	private static final ArrayDeque<String> trace = new ArrayDeque<>();

	private SurfaceSceneryMeshPreparation() { }

	@FunctionalInterface
	public interface Worker {
		byte[] build(byte[] source);
	}

	@FunctionalInterface
	public interface WordWorker {
		int[] build(byte[] source, int[] packingContext);
	}

	public static void install(Worker worker) { browserWorker = worker; }
	public static void installWords(WordWorker worker) { wordWorker = worker; }
	public static boolean isWordWorkerAvailable() { return wordWorker != null; }

	public static void publishPackingContext(int[] context) { packingContext = context; }

	public static int definitionFlags() {
		return (Config.S_IMPROVED_ITEM_OBJECT_NAMES ? 1 : 0) | (Config.S_BATCH_PROGRESSION ? 2 : 0)
			| (Config.S_WANT_LEFTCLICK_WEBS ? 4 : 0) | (Config.S_WANT_CUSTOM_SPRITES ? 8 : 0);
	}

	public static boolean isAvailable() { return browserWorker != null; }

	public static boolean isTracing() { return TRACE; }

	private static synchronized void trace(String row) {
		if (trace.size() == 512) trace.removeFirst();
		trace.addLast(row);
	}

	public static synchronized List<String> traceSnapshot() { return new ArrayList<>(trace); }

	public static byte[] archiveDigest(byte[] archive) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(archive);
		} catch (NoSuchAlgorithmException unavailable) {
			throw new IllegalStateException(unavailable);
		}
	}

	public static void traceAdoption(long at, long nanos, long cell, boolean hit) {
		if (TRACE) trace("adopt," + at + "," + cell + "," + nanos + "," + hit);
	}

	public static void tracePlacementRefusal(long at, long cell, int objectId) {
		if (TRACE) trace("placement-refused," + at + "," + cell + "," + objectId);
	}

	public static Renderer3DWorldChunkFrame.ChunkMesh prepare(int sectionX, int sectionZ, int originX, int originZ,
		RSModel[] models, long cell, long startedAt, long modelNanos) {
		Worker worker = browserWorker;
		if (worker == null || models.length == 0 || models.length > MAX_MODELS) return null;
		long phase = TRACE ? System.nanoTime() : 0;
		long encodeNanos = 0, bridgeNanos = 0, decodeNanos = 0;
		int sent = 0, received = 0;
		boolean success = false;
		try {
			byte[] request = encode(sectionX, sectionZ, originX, originZ, models);
			sent = request.length;
			if (TRACE) {
				encodeNanos = System.nanoTime() - phase;
				phase = System.nanoTime();
			}
			byte[] response = request.length <= MAX_BYTES ? worker.build(request) : null;
			if (TRACE) {
				bridgeNanos = System.nanoTime() - phase;
				phase = System.nanoTime();
			}
			if (response == null) return null;
			received = response.length;
			try (ObjectInputStream input = World.openSurfaceProduct(response, MAX_BYTES)) {
				Renderer3DWorldChunkFrame.ChunkMesh result = World.readSurfaceMesh(input, sectionX, sectionZ);
				if (result == null || result.getOriginWorldX() != originX || result.getOriginWorldZ() != originZ
					|| result.getChunkRole() != Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS || input.read() != -1) {
					throw new IOException("Detached scenery mesh identity");
				}
				success = true;
				return result;
			}
		} catch (IOException | ClassNotFoundException | IllegalStateException failed) {
			return null;
		} finally {
			if (TRACE) {
				if (received != 0) decodeNanos = System.nanoTime() - phase;
				trace("legacy," + startedAt + "," + cell + "," + models.length + "," + modelNanos + ","
					+ encodeNanos + "," + bridgeNanos + "," + decodeNanos + "," + sent + "," + received + "," + success);
			}
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

	public static Renderer3DWorldChunkFrame.ChunkMesh preparePlacements(int sectionX, int sectionZ, int x, int z,
		int baseX, int baseZ, int scale, long revision, byte[] digest, int[] placements,
		long cell, long at, long started) {
		Worker worker = browserWorker;
		int count = placements.length / PLACEMENT_WORDS;
		if (worker == null || count < 1 || count > MAX_MODELS) return null;
		long beforeBridge = 0, afterBridge = 0;
		int sent = 0, received = 0;
		boolean success = false;
		WordWorker words = wordWorker;
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(82 + placements.length * Integer.BYTES);
			try (DataOutputStream output = new DataOutputStream(bytes)) {
				output.writeInt(PLACEMENTS);
				output.writeInt(sectionX);
				output.writeInt(sectionZ);
				output.writeInt(x);
				output.writeInt(z);
				output.writeInt(baseX);
				output.writeInt(baseZ);
				output.writeInt(scale);
				output.writeLong(revision);
				output.write(digest);
				int flags = definitionFlags();
				for (int bit = 0; bit < 4; bit++) output.writeBoolean((flags & (1 << bit)) != 0);
				output.writeInt(count);
				for (int word : placements) output.writeInt(word);
			}
			byte[] request = bytes.toByteArray();
			sent = request.length;
			if (TRACE) beforeBridge = System.nanoTime();
			if (words != null) {
				int[] response = words.build(request, packingContext);
				if (TRACE) afterBridge = System.nanoTime();
				if (response == null || response.length == 0) return null;
				received = response.length * Integer.BYTES;
				Renderer3DWorldChunkFrame.ChunkMesh mesh = Renderer3DWorldChunkFrame.ChunkMesh.readDetachedSceneryWords(
					response, sectionX, sectionZ, x, z, revision);
				success = true;
				return mesh;
			}
			byte[] response = worker.build(request);
			if (TRACE) afterBridge = System.nanoTime();
			if (response == null || response.length == 0) return null;
			received = response.length;
			try (ObjectInputStream input = World.openSurfaceProduct(response, MAX_BYTES)) {
				if (input.readLong() != revision) return null;
				Renderer3DWorldChunkFrame.ChunkMesh result = World.readSurfaceMesh(input, sectionX, sectionZ);
				if (result == null || result.getOriginWorldX() != x || result.getOriginWorldZ() != z
					|| result.getChunkRole() != Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS || input.read() != -1) return null;
				success = true;
				return result;
			}
		} catch (IOException | ClassNotFoundException | IllegalStateException failed) {
			return null;
		} finally {
			if (TRACE) trace("placements," + at + "," + cell + "," + count + ",0,"
				+ (beforeBridge == 0 ? 0 : beforeBridge - started) + "," + (afterBridge == 0 ? 0 : afterBridge - beforeBridge)
				+ "," + (received == 0 ? 0 : System.nanoTime() - afterBridge) + "," + sent + "," + received + "," + success);
		}
	}

	/** Lives in the isolated scenery worker. The archive and source models are read-only inputs to each build. */
	public static final class PlacementMesher {
		private static final int MAX_TEMPLATES = 64;
		private final Map<String, RSModel> templates = new LinkedHashMap<>(MAX_TEMPLATES, 0.75F, true);
		private byte[] archive, digest;
		private int definitionFlags = -1;

		public byte[] build(byte[] source, String modelPath) throws IOException {
			if (source.length < Integer.BYTES) throw new IOException("Detached scenery header");
			try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(source))) {
				if (input.readInt() != PLACEMENTS) return buildPacket(source);
				try {
					PlacementProduct result = buildPlacements(input, modelPath);
					return writeMesh(result.mesh, result.revision);
				} catch (IOException | RuntimeException unavailable) {
					// A refused placement request can be retried with the original model packet.
					return new byte[0];
				}
			}
		}

		public int[] buildWords(byte[] source, String modelPath, ToIntFunction<Renderer3DWorldChunkFrame.ChunkMesh> pack) {
			try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(source))) {
				if (input.readInt() != PLACEMENTS) return new int[0];
				PlacementProduct result = buildPlacements(input, modelPath);
				return result.mesh.writeDetachedSceneryWords(result.revision, pack.applyAsInt(result.mesh));
			} catch (IOException | RuntimeException unavailable) {
				return new int[0];
			}
		}

		private PlacementProduct buildPlacements(DataInputStream input, String modelPath) throws IOException {
			int sectionX = input.readInt(), sectionZ = input.readInt(), x = input.readInt(), z = input.readInt();
			int baseX = input.readInt(), baseZ = input.readInt(), scale = input.readInt();
			long revision = input.readLong();
			byte[] expected = new byte[32];
			input.readFully(expected);
			int flags = 0;
			for (int bit = 0; bit < 4; bit++) if (input.readBoolean()) flags |= 1 << bit;
			int count = input.readInt();
			if (count < 1 || count > MAX_MODELS || scale != 128 || input.available() != count * PLACEMENT_WORDS * Integer.BYTES) {
				throw new IOException("Detached scenery placements");
			}
			if (archive == null) loadArchive(modelPath);
			if (!Arrays.equals(expected, digest)) throw new IOException("Detached scenery model archive changed");
			if (definitionFlags < 0) {
				Config.S_IMPROVED_ITEM_OBJECT_NAMES = (flags & 1) != 0;
				Config.S_BATCH_PROGRESSION = (flags & 2) != 0;
				Config.S_WANT_LEFTCLICK_WEBS = (flags & 4) != 0;
				Config.S_WANT_CUSTOM_SPRITES = (flags & 8) != 0;
				EntityHandler.load(true);
				definitionFlags = flags;
			}
			if (definitionFlags != flags) throw new IOException("Detached scenery definitions changed");
			RSModel[] models = new RSModel[count];
			for (int index = 0; index < count; index++) {
				int kind = input.readInt(), id = input.readInt();
				int localX = input.readInt() - baseX, localZ = input.readInt() - baseZ;
				int direction = input.readInt(), elevation1 = input.readInt(), elevation2 = input.readInt();
				if (kind == Renderer3DModelKind.WALL_OBJECT.ordinal()) {
					models[index] = StaticSceneryModels.wallObject(id, localX, localZ, direction, scale, index, elevation1, elevation2);
				} else if (kind == Renderer3DModelKind.GAME_OBJECT.ordinal()) {
					GameObjectDef def = EntityHandler.getObjectDef(id);
					if (def == null || def.modelID < 0) throw new IOException("Detached scenery object definition");
					if (elevation1 != World.PRESENTATION_ELEVATION_UNAVAILABLE) {
						RSModel model = sourceModel(EntityHandler.getModelName(def.modelID)).clone();
						models[index] = StaticSceneryModels.gameObject(model, def, id, localX, localZ, direction, scale, index, elevation1);
					}
				} else {
					throw new IOException("Detached scenery placement kind");
				}
			}
			return new PlacementProduct(mesh(sectionX, sectionZ, x, z, models), revision);
		}

		private void loadArchive(String path) throws IOException {
			try (DataInputStream input = new DataInputStream(DataOperations.streamFromPath(path))) {
				int unpacked = input.readUnsignedShort() << 8 | input.readUnsignedByte();
				int packed = input.readUnsignedShort() << 8 | input.readUnsignedByte();
				if (packed < 2 || unpacked < 2 || packed > MAX_BYTES || unpacked > MAX_BYTES) throw new IOException("Model archive bounds");
				byte[] encoded = new byte[packed];
				input.readFully(encoded);
				byte[] decoded = encoded;
				if (unpacked != packed) {
					decoded = new byte[unpacked];
					DataFileDecrypter.unpackData(decoded, unpacked, encoded, packed, 0);
				}
				digest = archiveDigest(decoded);
				archive = decoded;
			}
		}

		private RSModel sourceModel(String name) throws IOException {
			RSModel model = templates.get(name);
			if (model != null) return model;
			int offset = DataOperations.getDataFileOffset(name + ".ob3", archive);
			int length = DataOperations.getDataFileLength(name + ".ob3", archive);
			if (offset == 0 && StaticSceneryModels.isAirGlyphPlaceholder(name)) {
				model = StaticSceneryModels.createAirGlyphPlaceholder();
			} else {
				if (offset == 0 || length <= 0 || length > 48 * 1024) throw new IOException("Detached scenery model unavailable");
				model = new RSModel(archive, offset, true);
			}
			model = StaticSceneryModels.normalizeLoadedModel(name, model);
			model.m_cb = name.equals("giantcrystal");
			if (templates.size() == MAX_TEMPLATES) templates.remove(templates.keySet().iterator().next());
			templates.put(name, model);
			return model;
		}
	}

	private static final class PlacementProduct {
		final Renderer3DWorldChunkFrame.ChunkMesh mesh;
		final long revision;
		PlacementProduct(Renderer3DWorldChunkFrame.ChunkMesh mesh, long revision) {
			this.mesh = mesh;
			this.revision = revision;
		}
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
			return writeMesh(mesh(sectionX, sectionZ, x, z, models), null);
		}
	}

	private static Renderer3DWorldChunkFrame.ChunkMesh mesh(int sectionX, int sectionZ, int x, int z, RSModel[] models) {
		return RSModel.buildRenderer3DObjectChunkMesh(0, sectionX, sectionZ, x, z, models, models.length,
			Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS);
	}

	private static byte[] writeMesh(Renderer3DWorldChunkFrame.ChunkMesh result, Long revision) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
			if (revision != null) output.writeLong(revision);
			output.writeObject(result);
		}
		return bytes.toByteArray();
	}

	public static boolean sameGeometry(Renderer3DWorldChunkFrame.ChunkMesh left, Renderer3DWorldChunkFrame.ChunkMesh right) {
		try {
			return Arrays.equals(writeMesh(left, null), writeMesh(right, null));
		} catch (IOException failed) {
			return false;
		}
	}
}
