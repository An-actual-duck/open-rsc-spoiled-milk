package orsc;

/**
 * A fixed rune-altar visual location in both authoritative logical space and
 * the legacy packed-Y compatibility projection.
 *
 * Altar scenery definitions predate layered maps and therefore record plane 3
 * underground coordinates as {@code 3 * 944 + logicalY}. Native layered
 * scenes instead expose logical Y and a signed level. Keeping both projections
 * on one typed anchor prevents an underground owner from being compared or
 * drawn with the wrong representation.
 */
final class AltarVisualAnchor {
	static final String GLOBAL_WORLD_SPACE = "global";
	static final int LEGACY_LEVEL_STRIDE = 944;
	private static final int LEGACY_PLANE_COUNT = 4;

	private final String worldSpace;
	private final int level;
	private final int logicalX;
	private final int logicalY;
	private final int legacyX;
	private final int legacyY;

	private AltarVisualAnchor(
		String worldSpace,
		int level,
		int logicalX,
		int logicalY,
		int legacyX,
		int legacyY) {
		this.worldSpace = worldSpace;
		this.level = level;
		this.logicalX = logicalX;
		this.logicalY = logicalY;
		this.legacyX = legacyX;
		this.legacyY = legacyY;
	}

	static AltarVisualAnchor globalFromLegacy(int packedX, int packedY) {
		if (packedX < 0 || packedX > Short.MAX_VALUE) {
			throw new IllegalArgumentException(
				"Altar X is outside the legacy range: " + packedX);
		}
		int maxPackedY = LEGACY_LEVEL_STRIDE * LEGACY_PLANE_COUNT - 1;
		if (packedY < 0 || packedY > maxPackedY) {
			throw new IllegalArgumentException(
				"Altar Y is outside the legacy range: " + packedY);
		}
		int legacyPlane = Math.floorDiv(packedY, LEGACY_LEVEL_STRIDE);
		return new AltarVisualAnchor(
			GLOBAL_WORLD_SPACE,
			levelForLegacyPlane(legacyPlane),
			packedX,
			Math.floorMod(packedY, LEGACY_LEVEL_STRIDE),
			packedX,
			packedY);
	}

	boolean matchesOwner(
		String activeWorldSpace,
		int activeLevel,
		int projectedX,
		int projectedY,
		boolean nativeLayeredProjection) {
		return worldSpace.equals(activeWorldSpace)
			&& level == activeLevel
			&& projectedX == projectedX(nativeLayeredProjection)
			&& projectedY == projectedY(nativeLayeredProjection);
	}

	int projectedX(boolean nativeLayeredProjection) {
		return nativeLayeredProjection ? logicalX : legacyX;
	}

	int projectedY(boolean nativeLayeredProjection) {
		return nativeLayeredProjection ? logicalY : legacyY;
	}

	String getWorldSpace() {
		return worldSpace;
	}

	int getLevel() {
		return level;
	}

	int getLogicalX() {
		return logicalX;
	}

	int getLogicalY() {
		return logicalY;
	}

	int getLegacyX() {
		return legacyX;
	}

	int getLegacyY() {
		return legacyY;
	}

	private static int levelForLegacyPlane(int plane) {
		switch (plane) {
			case 0:
				return 0;
			case 1:
				return 1;
			case 2:
				return 2;
			case 3:
				return -1;
			default:
				throw new IllegalArgumentException(
					"Unsupported legacy altar plane: " + plane);
		}
	}
}
