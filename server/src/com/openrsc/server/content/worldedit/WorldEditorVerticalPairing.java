package com.openrsc.server.content.worldedit;

import com.openrsc.server.constants.SceneryId;

/**
 * Closed authoring convention for the generic vertical-travel scenery.
 *
 * <p>Only the six deliberately generic ladder/stair definitions participate.
 * Specialized quest, transport, and content-specific scenery must continue
 * through their established handlers.</p>
 */
public final class WorldEditorVerticalPairing {
	private WorldEditorVerticalPairing() { }

	public static Pairing find(final int sceneryId) {
		if (sceneryId == SceneryId.LADDER_GENERIC_UP.id()) {
			return new Pairing(
				SceneryId.LADDER_GENERIC_UP.id(),
				SceneryId.LADDER_GENERIC_DOWN.id(),
				1);
		}
		if (sceneryId == SceneryId.LADDER_GENERIC_DOWN.id()) {
			return new Pairing(
				SceneryId.LADDER_GENERIC_DOWN.id(),
				SceneryId.LADDER_GENERIC_UP.id(),
				-1);
		}
		if (sceneryId == SceneryId.STAIRS_WOODEN_GENERIC_UP.id()) {
			return new Pairing(
				SceneryId.STAIRS_WOODEN_GENERIC_UP.id(),
				SceneryId.STAIRS_WOODEN_GENERIC_DOWN.id(),
				1);
		}
		if (sceneryId == SceneryId.STAIRS_WOODEN_GENERIC_DOWN.id()) {
			return new Pairing(
				SceneryId.STAIRS_WOODEN_GENERIC_DOWN.id(),
				SceneryId.STAIRS_WOODEN_GENERIC_UP.id(),
				-1);
		}
		if (sceneryId == SceneryId.STAIRS_STONE_GENERIC_UP.id()) {
			return new Pairing(
				SceneryId.STAIRS_STONE_GENERIC_UP.id(),
				SceneryId.STAIRS_STONE_GENERIC_DOWN.id(),
				1);
		}
		if (sceneryId == SceneryId.STAIRS_STONE_GENERIC_DOWN.id()) {
			return new Pairing(
				SceneryId.STAIRS_STONE_GENERIC_DOWN.id(),
				SceneryId.STAIRS_STONE_GENERIC_UP.id(),
				-1);
		}
		return null;
	}

	public static final class Pairing {
		private final int sourceSceneryId;
		private final int inverseSceneryId;
		private final int levelDelta;

		private Pairing(
			final int sourceSceneryId,
			final int inverseSceneryId,
			final int levelDelta) {
			this.sourceSceneryId = sourceSceneryId;
			this.inverseSceneryId = inverseSceneryId;
			this.levelDelta = levelDelta;
		}

		public int getSourceSceneryId() {
			return sourceSceneryId;
		}

		public int getInverseSceneryId() {
			return inverseSceneryId;
		}

		public int getLevelDelta() {
			return levelDelta;
		}
	}
}
