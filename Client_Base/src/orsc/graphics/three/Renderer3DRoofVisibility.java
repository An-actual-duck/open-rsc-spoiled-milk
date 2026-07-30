package orsc.graphics.three;

/**
 * Per-frame roof visibility resolved from the saved roof option and the
 * player's current floor/coverage state.
 */
public enum Renderer3DRoofVisibility {
	VISIBLE(true, true, false),
	VISIBLE_ON_ACTIVE_FLOOR(true, false, true),
	HIDDEN_BY_SETTING(false, false, false),
	HIDDEN_INDOORS(false, false, false);

	private final boolean roofsVisible;
	private final boolean structuresAboveActiveFloorVisible;
	private final boolean roofsLimitedToActiveFloor;

	Renderer3DRoofVisibility(
		boolean roofsVisible,
		boolean structuresAboveActiveFloorVisible,
		boolean roofsLimitedToActiveFloor) {
		this.roofsVisible = roofsVisible;
		this.structuresAboveActiveFloorVisible = structuresAboveActiveFloorVisible;
		this.roofsLimitedToActiveFloor = roofsLimitedToActiveFloor;
	}

	public static Renderer3DRoofVisibility resolve(
		boolean hideRoofsSetting,
		int activePlane,
		boolean playerTileCovered) {
		if (hideRoofsSetting) {
			return HIDDEN_BY_SETTING;
		}
		if (playerTileCovered) {
			return HIDDEN_INDOORS;
		}
		return activePlane > 0 ? VISIBLE_ON_ACTIVE_FLOOR : VISIBLE;
	}

	public boolean areRoofsVisible() {
		return roofsVisible;
	}

	public boolean usesAutomaticRoofCameraZoom() {
		return this != HIDDEN_BY_SETTING;
	}

	public boolean isWorldChunkModelKindVisible(
		Renderer3DModelKind modelKind,
		int activePlane,
		int chunkPlane) {
		if (modelKind == Renderer3DModelKind.ROOF) {
			return roofsVisible
				&& (!roofsLimitedToActiveFloor || chunkPlane == activePlane);
		}
		if (modelKind == Renderer3DModelKind.WALL && chunkPlane > activePlane) {
			return structuresAboveActiveFloorVisible;
		}
		return true;
	}
}
