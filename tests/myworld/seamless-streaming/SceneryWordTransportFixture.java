package orsc.graphics.three;

import java.util.Arrays;
import orsc.graphics.three.Renderer3DWorldChunkFrame.ChunkMesh;

public final class SceneryWordTransportFixture {
	public static void main(String[] args) {
		RSModel model = new RSModel(4, 1);
		int[] corners = {model.insertVertex(73, -32, 385), model.insertVertex(200, -15, 386),
			model.insertVertex(200, 48, 512), model.insertVertex(73, 42, 512)};
		model.insertFace(corners.length, corners, 3, -128, false);
		model.setRenderer3DModelKind(Renderer3DModelKind.GAME_OBJECT);
		model.setRenderer3DMaterialFamily(Renderer3DMaterialFamily.EMISSIVE);
		model.setRenderer3DGlowEmitter(0xff661c, 256, 90);
		model.setDiffuseLightAndColor(-50, -10, -50, 48, 48, true, 117);
		ChunkMesh original = RSModel.buildRenderer3DObjectChunkMesh(0, 5, 12, 30720, 73728,
			new RSModel[] {model}, 1, Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS);
		long revision = 0x8a02c5340f9bba3aL;
		int[] words = original.writeDetachedSceneryWords(revision);
		ChunkMesh received = read(words, revision);
		if (received.getVertexCount() == 0
			|| !SurfaceSceneryMeshPreparation.sameGeometry(original, received)) throw new AssertionError("Scenery mesh round trip");
		ChunkMesh shifted = received.rebaseStaticObjectPresentation(6, 12, -6144, 0);
		if (shifted.getVertexCoord(0) != received.getVertexCoord(0) - 6144
			|| shifted.getStorageSignature() != received.getStorageSignature()) throw new AssertionError("Scenery rebase");
		if (received.reuseStaticObjectPresentation(6, 12, -6144, 0) != null) {
			throw new AssertionError("Glow emitter without exact midpoint data was reused across bases");
		}
		model.clearRenderer3DGlowEmitter();
		ChunkMesh unlit = RSModel.buildRenderer3DObjectChunkMesh(0, 5, 12, 30720, 73728,
			new RSModel[] {model}, 1, Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS);
		ChunkMesh reused = unlit.reuseStaticObjectPresentation(6, 12, -6144, -6144);
		if (reused.getLogicalWorldOffsetX() != unlit.getOriginWorldX() || reused.getVertexCoord(0) != shifted.getVertexCoord(0)
			|| reused.getShadowCaster(0).getBaseZ0() != (385 + 512 - 2 * 6144) / 2
			|| reused.rebaseStaticObjectPresentation(7, 12, -6144, 0).getLogicalWorldOffsetX() != unlit.getOriginWorldX() + 6144) {
			throw new AssertionError("Scenery reuse must preserve fresh lighting coordinates through a later roll");
		}
		refuse(() -> read(words, revision + 1));
		refuse(() -> ChunkMesh.readDetachedSceneryWords(words, 5, 12, 30721, 73728, revision));
		refuse(() -> read(Arrays.copyOf(words, words.length - 1), revision));
		int[] inconsistent = words.clone();
		inconsistent[7] = Integer.MAX_VALUE;
		refuse(() -> read(inconsistent, revision));
		System.out.println("PASS exact scenery words, retained rebase, stale identity and bounded shape rejection");
	}

	private static ChunkMesh read(int[] words, long revision) {
		return ChunkMesh.readDetachedSceneryWords(words, 5, 12, 30720, 73728, revision);
	}

	private static void refuse(Runnable read) {
		try {
			read.run();
			throw new AssertionError("Malformed scenery words were accepted");
		} catch (IllegalStateException expected) {
			// The caller can retry through the normal scenery builder.
		}
	}
}
