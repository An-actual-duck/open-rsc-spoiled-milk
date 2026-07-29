package com.openrsc.server.model.world.region;

import com.openrsc.server.util.rsc.CollisionFlag;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Detached exact static-tile input for initializing one isolated packed
 * Region.
 *
 * <p>Each canonical tile record copies archive-derived metadata and the
 * current static terrain collision result from an already resident source.
 * Dynamic object collision, blocking-scenery counts, and dynamic projectile
 * counts are used only to subtract their contribution from the observed
 * traversal/projectile result; they are never retained in this plan.</p>
 *
 * <p>This is an inert definition. It cannot load an archive, allocate or
 * mutate tile storage, initialize a Region, replay authored objects, rebuild
 * dynamic collision, preserve entities, register a Region, or release
 * visibility.</p>
 */
public final class LayeredPackedRegionTerrainInitializationPlan {
	private final long generation;
	private final long requirementsObservedAtTick;
	private final long observedAtTick;
	private final long residencyMirrorVersion;
	private final long authoredGeneration;
	private final int sourceOrdinal;
	private final int packedRegionX;
	private final int packedRegionY;
	private final int sideTileCount;
	private final List<TerrainTileInput> tiles;
	private final int terrainBlockedTileCount;
	private final int terrainCollisionMaskTileCount;
	private final int terrainProjectileBlockedTileCount;
	private final int sealedBaseTraversalTileCount;
	private final String fingerprintSha256;

	private LayeredPackedRegionTerrainInitializationPlan(
		final LayeredPackedRegionBlankContainerPlan containerPlan,
		final List<TerrainTileInput> tileInputs) {
		LayeredPackedRegionBlankContainerPlan checked =
			Objects.requireNonNull(containerPlan, "containerPlan");
		List<TerrainTileInput> supplied =
			Objects.requireNonNull(tileInputs, "tileInputs");
		if (!checked.isDetachedConstructionContract()
			|| !checked.isConstructionDefinitionComplete()
			|| checked.isExecutableConstruction()
			|| checked.isRegionContainerCreated()
			|| checked.isLifecycleAuthority()
			|| supplied.size() != checked.getContainerTileSlotCount()) {
			throw new IllegalArgumentException(
				"Terrain initialization inputs do not match one blank container");
		}
		this.generation = checked.getGeneration();
		this.requirementsObservedAtTick =
			checked.getRequirementsObservedAtTick();
		this.observedAtTick = checked.getObservedAtTick();
		this.residencyMirrorVersion = checked.getResidencyMirrorVersion();
		this.authoredGeneration = checked.getAuthoredGeneration();
		this.sourceOrdinal = checked.getSourceOrdinal();
		this.packedRegionX = checked.getPackedRegionX();
		this.packedRegionY = checked.getPackedRegionY();
		this.sideTileCount = checked.getContainerSideTileCount();

		List<TerrainTileInput> copied =
			new ArrayList<TerrainTileInput>(supplied.size());
		int terrainBlocked = 0;
		int terrainCollision = 0;
		int terrainProjectile = 0;
		int sealedBase = 0;
		for (int ordinal = 0; ordinal < supplied.size(); ordinal++) {
			TerrainTileInput input = Objects.requireNonNull(
				supplied.get(ordinal), "tileInputs[" + ordinal + "]");
			int expectedX = ordinal / sideTileCount;
			int expectedY = ordinal % sideTileCount;
			if (input.getLocalX() != expectedX
				|| input.getLocalY() != expectedY) {
				throw new IllegalArgumentException(
					"Terrain tile inputs are not in canonical source order");
			}
			copied.add(input);
			terrainBlocked += input.isTerrainBlocked() ? 1 : 0;
			terrainCollision +=
				input.getTerrainCollisionMask() != 0 ? 1 : 0;
			terrainProjectile +=
				input.isStaticProjectileBlocked() ? 1 : 0;
			sealedBase += input.hasSealedBaseTraversal() ? 1 : 0;
		}
		this.tiles = Collections.unmodifiableList(copied);
		this.terrainBlockedTileCount = terrainBlocked;
		this.terrainCollisionMaskTileCount = terrainCollision;
		this.terrainProjectileBlockedTileCount = terrainProjectile;
		this.sealedBaseTraversalTileCount = sealedBase;
		this.fingerprintSha256 = fingerprint(copied);
	}

	static LayeredPackedRegionTerrainInitializationPlan define(
		final LayeredPackedRegionBlankContainerPlan containerPlan,
		final List<TerrainTileInput> tileInputs) {
		return new LayeredPackedRegionTerrainInitializationPlan(
			containerPlan, tileInputs);
	}

	static LayeredPackedRegionTerrainInitializationPlan
		defineFromResidentTileStates(
			final LayeredPackedRegionBlankContainerPlan containerPlan,
			final LayeredPackedRegionSourceLifecycleBoundary boundary,
			final List<LayeredTileState> residentTileStates) {
		LayeredPackedRegionBlankContainerPlan checked =
			Objects.requireNonNull(containerPlan, "containerPlan");
		LayeredPackedRegionSourceLifecycleBoundary checkedBoundary =
			Objects.requireNonNull(boundary, "boundary");
		List<LayeredTileState> states =
			Objects.requireNonNull(
				residentTileStates, "residentTileStates");
		if (!checkedBoundary.isRegionLifecycleBoundaryHeld()
			|| checkedBoundary.getGeneration() != checked.getGeneration()
			|| checkedBoundary.getRequirementsObservedAtTick()
				!= checked.getRequirementsObservedAtTick()
			|| checkedBoundary.getResidencyMirrorVersion()
				!= checked.getResidencyMirrorVersion()
			|| checked.getSourceOrdinal() < 0
			|| checked.getSourceOrdinal()
				>= checkedBoundary.getSelectedSourceCount()
			|| checkedBoundary.getSelectedSources()
				.get(checked.getSourceOrdinal()).getPackedRegionX()
					!= checked.getPackedRegionX()
			|| checkedBoundary.getSelectedSources()
				.get(checked.getSourceOrdinal()).getPackedRegionY()
					!= checked.getPackedRegionY()
			|| states.size() != checked.getContainerTileSlotCount()) {
			throw new IllegalArgumentException(
				"Resident terrain states do not match the active source");
		}
		List<TerrainTileInput> inputs =
			new ArrayList<TerrainTileInput>(states.size());
		for (int ordinal = 0; ordinal < states.size(); ordinal++) {
			inputs.add(TerrainTileInput.fromLayeredState(
				ordinal / checked.getContainerSideTileCount(),
				ordinal % checked.getContainerSideTileCount(),
				Objects.requireNonNull(
					states.get(ordinal),
					"residentTileStates[" + ordinal + "]")));
		}
		return define(checked, inputs);
	}

	public long getGeneration() { return generation; }
	public long getRequirementsObservedAtTick() {
		return requirementsObservedAtTick;
	}
	public long getObservedAtTick() { return observedAtTick; }
	public long getResidencyMirrorVersion() { return residencyMirrorVersion; }
	public long getAuthoredGeneration() { return authoredGeneration; }
	public int getSourceOrdinal() { return sourceOrdinal; }
	public int getPackedRegionX() { return packedRegionX; }
	public int getPackedRegionY() { return packedRegionY; }
	public int getSideTileCount() { return sideTileCount; }
	public int getTileCount() { return tiles.size(); }
	public List<TerrainTileInput> getTiles() { return tiles; }
	public int getTerrainBlockedTileCount() {
		return terrainBlockedTileCount;
	}
	public int getTerrainCollisionMaskTileCount() {
		return terrainCollisionMaskTileCount;
	}
	public int getTerrainProjectileBlockedTileCount() {
		return terrainProjectileBlockedTileCount;
	}
	public int getSealedBaseTraversalTileCount() {
		return sealedBaseTraversalTileCount;
	}
	public String getFingerprintSha256() { return fingerprintSha256; }

	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedTerrainDefinition() { return true; }
	public boolean isTerrainInputDefinitionComplete() { return true; }
	public boolean isDynamicObjectStateExcluded() { return true; }
	public boolean isBlockingSceneryStateExcluded() { return true; }
	public boolean isDynamicProjectileStateExcluded() { return true; }
	public boolean isArchiveReloadPerformed() { return false; }
	public boolean isTileStorageAllocated() { return false; }
	public boolean isRegionContainerCreated() { return false; }
	public boolean isTerrainApplyPerformed() { return false; }
	public boolean isAuthoredReplayPerformed() { return false; }
	public boolean isDynamicCollisionRebuildPerformed() { return false; }
	public boolean isActiveFamilyPreservationPerformed() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isRegionRegistryMutated() { return false; }
	public boolean isResidencyMirrorMutated() { return false; }
	public boolean isVisibilityCacheMutated() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isVisibilityReleased() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	private static String fingerprint(final List<TerrainTileInput> inputs) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (TerrainTileInput input : inputs) {
				input.updateDigest(digest);
			}
			StringBuilder result = new StringBuilder(64);
			for (byte value : digest.digest()) {
				result.append(String.format("%02x", value & 0xff));
			}
			return result.toString();
		} catch (NoSuchAlgorithmException unavailable) {
			throw new IllegalStateException(
				"SHA-256 is unavailable for terrain input identity",
				unavailable);
		}
	}

	/** Static terrain-only value copied from one canonical resident tile. */
	public static final class TerrainTileInput {
		private final int localX;
		private final int localY;
		private final int staticTraversalMask;
		private final short diagonalWallValue;
		private final byte horizontalWallValue;
		private final byte overlay;
		private final byte verticalWallValue;
		private final byte elevation;
		private final boolean staticProjectileBlocked;
		private final boolean terrainBlocked;
		private final int terrainCollisionMask;
		private final boolean terrainOverlayProjectileBlocked;
		private final int terrainWallProjectileCount;
		private final int sealedBaseTraversalMask;

		private TerrainTileInput(
			final int localX,
			final int localY,
			final int observedTraversalMask,
			final short diagonalWallValue,
			final byte horizontalWallValue,
			final byte overlay,
			final byte verticalWallValue,
			final byte elevation,
			final boolean originalProjectileAllowed,
			final boolean terrainBlocked,
			final int blockingSceneryCount,
			final int terrainCollisionMask,
			final int[] dynamicCollisionCounts,
			final boolean terrainOverlayProjectileBlocked,
			final int terrainWallProjectileCount) {
			if (localX < 0 || localY < 0) {
				throw new IllegalArgumentException(
					"Terrain tile local coordinates must not be negative");
			}
			this.localX = localX;
			this.localY = localY;
			this.diagonalWallValue = diagonalWallValue;
			this.horizontalWallValue = horizontalWallValue;
			this.overlay = overlay;
			this.verticalWallValue = verticalWallValue;
			this.elevation = elevation;
			this.terrainBlocked = terrainBlocked;
			this.terrainCollisionMask = terrainCollisionMask;
			this.terrainOverlayProjectileBlocked =
				terrainOverlayProjectileBlocked;
			this.terrainWallProjectileCount =
				terrainWallProjectileCount;
			this.staticProjectileBlocked =
				originalProjectileAllowed;

			int staticMask = observedTraversalMask & 0xff;
			int[] dynamicCounts = Objects.requireNonNull(
				dynamicCollisionCounts, "dynamicCollisionCounts");
			for (int bit = 0; bit < dynamicCounts.length; bit++) {
				int flag = 1 << bit;
				if (dynamicCounts[bit] > 0
					&& (terrainCollisionMask & flag) == 0) {
					staticMask &= ~flag;
				}
			}
			if (blockingSceneryCount > 0
				&& !terrainBlocked
				&& (terrainCollisionMask
					& CollisionFlag.FULL_BLOCK_C) == 0) {
				staticMask &= ~CollisionFlag.FULL_BLOCK_C;
			}
			this.staticTraversalMask = staticMask;
			int explainedMask = terrainCollisionMask
				| (terrainBlocked ? CollisionFlag.FULL_BLOCK_C : 0);
			this.sealedBaseTraversalMask =
				staticTraversalMask & ~explainedMask;
		}

		static TerrainTileInput fromLegacy(
			final int localX,
			final int localY,
			final TileValue observed) {
			TileValue tile = Objects.requireNonNull(observed, "observed");
			return new TerrainTileInput(
				localX, localY, tile.traversalMask, tile.diagWallVal,
				tile.horizontalWallVal, tile.overlay, tile.verticalWallVal,
				tile.elevation, tile.originalProjectileAllowed,
				tile.isTerrainBlocked(), tile.getBlockingSceneryCount(),
				tile.getTerrainCollisionMask(),
				tile.getDynamicCollisionCounts(),
				tile.isTerrainOverlayProjectileBlocked(),
				tile.getTerrainWallProjectileCount());
		}

		static TerrainTileInput fromLayeredState(
			final int localX,
			final int localY,
			final LayeredTileState observed) {
			LayeredTileState tile =
				Objects.requireNonNull(observed, "observed");
			return new TerrainTileInput(
				localX, localY, tile.getTraversalMask(),
				tile.getDiagonalWallValue(),
				tile.getHorizontalWallValue(), tile.getOverlay(),
				tile.getVerticalWallValue(), tile.getElevation(),
				tile.isOriginalProjectileAllowed(),
				tile.isTerrainBlocked(), tile.getBlockingSceneryCount(),
				tile.getTerrainCollisionMask(),
				tile.getDynamicCollisionCounts(),
				tile.isTerrainOverlayProjectileBlocked(),
				tile.getTerrainWallProjectileCount());
		}

		public int getLocalX() { return localX; }
		public int getLocalY() { return localY; }
		public int getStaticTraversalMask() { return staticTraversalMask; }
		public short getDiagonalWallValue() { return diagonalWallValue; }
		public byte getHorizontalWallValue() { return horizontalWallValue; }
		public byte getOverlay() { return overlay; }
		public byte getVerticalWallValue() { return verticalWallValue; }
		public byte getElevation() { return elevation; }
		public boolean isStaticProjectileBlocked() {
			return staticProjectileBlocked;
		}
		public boolean isTerrainBlocked() { return terrainBlocked; }
		public int getTerrainCollisionMask() {
			return terrainCollisionMask;
		}
		public boolean isTerrainOverlayProjectileBlocked() {
			return terrainOverlayProjectileBlocked;
		}
		public int getTerrainWallProjectileCount() {
			return terrainWallProjectileCount;
		}
		public int getSealedBaseTraversalMask() {
			return sealedBaseTraversalMask;
		}
		public boolean hasSealedBaseTraversal() {
			return sealedBaseTraversalMask != 0;
		}

		private void updateDigest(final MessageDigest digest) {
			updateInt(digest, localX);
			updateInt(digest, localY);
			updateInt(digest, staticTraversalMask);
			updateInt(digest, diagonalWallValue);
			updateInt(digest, horizontalWallValue);
			updateInt(digest, overlay);
			updateInt(digest, verticalWallValue);
			updateInt(digest, elevation);
			digest.update((byte) (staticProjectileBlocked ? 1 : 0));
			digest.update((byte) (terrainBlocked ? 1 : 0));
			updateInt(digest, terrainCollisionMask);
			digest.update(
				(byte) (terrainOverlayProjectileBlocked ? 1 : 0));
			updateInt(digest, terrainWallProjectileCount);
			updateInt(digest, sealedBaseTraversalMask);
		}

		private static void updateInt(
			final MessageDigest digest,
			final int value) {
			digest.update((byte) (value >>> 24));
			digest.update((byte) (value >>> 16));
			digest.update((byte) (value >>> 8));
			digest.update((byte) value);
		}
	}
}
