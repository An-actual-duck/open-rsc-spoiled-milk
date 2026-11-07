package orsc;

import orsc.graphics.three.Renderer3DWorldChunkFrame;

/** All submitted vertices share the primary presentation basis. Cell origins
 * identify native halo/object residency; they must not translate vertices twice. */
final class GeometryShadowCoordinates {
	private GeometryShadowCoordinates() {}
	static int worldOffsetX(Renderer3DWorldChunkFrame frame) {
		return frame.getChunks().get(0).getLogicalWorldOffsetX();
	}
	static int worldOffsetZ(Renderer3DWorldChunkFrame frame) {
		return frame.getChunks().get(0).getLogicalWorldOffsetZ();
	}
}
