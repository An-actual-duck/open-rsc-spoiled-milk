package orsc;

/** Product-defined terrain overlay values that do not consume TileDef IDs. */
public final class WorldBuilderTerrainOverlay {
	/**
	 * Uses the tile's ordinary ground colour while contributing full terrain
	 * collision. Raw overlay 255 was reserved from the definition-ID domain for
	 * this purpose.
	 */
	public static final int BLOCKING_BASE_COLOR = 255;

	private WorldBuilderTerrainOverlay() {
	}

	public static boolean usesBaseColor(int rawOverlay) {
		return rawOverlay == 0 || rawOverlay == BLOCKING_BASE_COLOR;
	}

	public static boolean isBlockingBaseColor(int rawOverlay) {
		return rawOverlay == BLOCKING_BASE_COLOR;
	}
}
