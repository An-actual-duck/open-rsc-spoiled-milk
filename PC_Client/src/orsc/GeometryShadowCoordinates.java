package orsc;

import orsc.graphics.three.Renderer3DWorldChunkFrame;

/** Converts presentation-local vertices, not object-cell identity, to world space. */
final class GeometryShadowCoordinates {
	private GeometryShadowCoordinates() {}
	static int worldOffsetX(Renderer3DWorldChunkFrame frame, Renderer3DWorldChunkFrame.ChunkMesh chunk) {
		return coordinateAnchor(frame, chunk).getLogicalWorldOffsetX();
	}
	static int worldOffsetZ(Renderer3DWorldChunkFrame frame, Renderer3DWorldChunkFrame.ChunkMesh chunk) {
		return coordinateAnchor(frame, chunk).getLogicalWorldOffsetZ();
	}
	private static Renderer3DWorldChunkFrame.ChunkMesh coordinateAnchor(
		Renderer3DWorldChunkFrame frame, Renderer3DWorldChunkFrame.ChunkMesh chunk) {
		// Object origins identify resident cells. Their raw vertices already
		// include that cell's presentation-local translation. World meshes use
		// their own origin; objects use the primary presentation-world anchor.
		return chunk.isObjectChunk() ? frame.getChunks().get(0) : chunk;
	}
}
