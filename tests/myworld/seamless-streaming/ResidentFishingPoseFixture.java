package orsc;

import orsc.graphics.three.RSModel;
import orsc.graphics.three.Renderer3DModelKind;
import orsc.graphics.three.Renderer3DWorldChunkFrame;
import orsc.graphics.three.World;

import java.util.HashSet;
import java.util.Set;

/** A repeated phase must reproduce the exact mesh before it can share a browser buffer. */
public final class ResidentFishingPoseFixture {
	public static void main(String[] args) {
		RSModel model = new RSModel(3, 1).withFishingSpotClarityOverlay();
		model.setRenderer3DModelKind(Renderer3DModelKind.GAME_OBJECT);
		model.setTranslate(768, -40, 1152);
		int basis = model.getFishingPoseBasisVersion();
		long[] poses = new long[24];
		Set<Long> distinct = new HashSet<>();
		for (int frame = 0; frame < 48; frame++) {
			int phase = (frame + 7) % poses.length;
			if (!model.animateFishingSpotClarityOverlay(frame, 7) || model.getFishingPose() != phase
				|| model.getFishingPoseBasisVersion() != basis) {
				throw new IllegalStateException("Ripple frame changed its stable basis at phase " + phase);
			}
			long signature = mesh(model).getStorageSignature();
			if (frame < poses.length) {
				poses[phase] = signature;
				distinct.add(signature);
			} else if (poses[phase] != signature) {
				throw new IllegalStateException("Phase " + phase + " produced different vertices, indices or materials");
			}
		}
		if (distinct.size() <= 1) throw new IllegalStateException("Ripple did not animate");
		Renderer3DWorldChunkFrame.ChunkMesh carried = mesh(model);
		carried.markResidentFishingPose();
		int section = World.SECTION_SIZE * 128;
		model.rebaseWorldOrigin(-section, section);
		if (model.getFishingPoseBasisVersion() != basis) throw new IllegalStateException("World rebase changed the pose basis");
		Renderer3DWorldChunkFrame.ChunkMesh rebase = carried.rebaseStaticObjectPresentation(5, 11, -section, section);
		Renderer3DWorldChunkFrame.ChunkMesh direct = mesh(model);
		if (rebase.getStorageSignature() != carried.getStorageSignature() || !rebase.isResidentFishingPose()) {
			throw new IllegalStateException("World rebase replaced immutable pose storage");
		}
		for (int i = 0; i < direct.getVertexCount() * 3; i++) {
			if (direct.getVertexCoord(i) != rebase.getVertexCoord(i)) throw new IllegalStateException("Rebased pose moved in world space");
		}
		long before = mesh(model).getStorageSignature();
		model.addRotation(0, 32, 0);
		if (model.getFishingPoseBasisVersion() == basis || mesh(model).getStorageSignature() == before) {
			throw new IllegalStateException("A placed model change could reuse the wrong pose");
		}
		System.out.println("Verified two complete ripple cycles and placement invalidation");
	}

	private static Renderer3DWorldChunkFrame.ChunkMesh mesh(RSModel model) {
		return RSModel.buildRenderer3DObjectChunkMesh(0, 4, 12, 0, 0, new RSModel[]{model}, 1,
			Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS);
	}
}
