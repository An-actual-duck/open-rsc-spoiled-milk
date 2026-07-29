package com.openrsc.server.model.world.region;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionAuthoredPlacementDependencyInventory.DependencyKind;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionAuthoredPlacementDependencyInventory.PlacementDependency;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionAuthoredPlacementManifest;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionAuthoredReconstructionRecipe.ReconstructionPlacement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Exact detached final-live authored replay definition for one terrain-verified
 * packed source.
 *
 * <p>The plan retains stable authored order and typed primitive constructor
 * inputs only. It does not construct an entity, populate a Region, derive
 * collision, restore scheduler state, register storage, or retain a runtime
 * handle.</p>
 */
public final class LayeredPackedRegionAuthoredReplayPlan {
	private final long generation;
	private final long requirementsObservedAtTick;
	private final long observedAtTick;
	private final long residencyMirrorVersion;
	private final long authoredGeneration;
	private final int selectedSourceOrdinal;
	private final int packedRegionX;
	private final int packedRegionY;
	private final boolean authoredSourceDeclared;
	private final int manifestPlacementCount;
	private final int supersededPlacementCount;
	private final List<AuthoredReplayPlacement> placements;
	private final int sceneryPlacementCount;
	private final int boundaryPlacementCount;
	private final int npcSpawnPlacementCount;
	private final int groundItemSpawnPlacementCount;
	private final int harvestingSceneryPlacementCount;
	private final int crossSourcePlacementCount;
	private final int affectedSourceReferenceCount;
	private final String fingerprintSha256;

	private LayeredPackedRegionAuthoredReplayPlan(
		final LayeredPackedRegionReloadRecipe reloadRecipe,
		final int sourceOrdinal,
		final LayeredPackedRegionIsolatedTerrainVerification verification) {
		LayeredPackedRegionReloadRecipe reload =
			Objects.requireNonNull(reloadRecipe, "reloadRecipe");
		LayeredPackedRegionIsolatedTerrainVerification terrain =
			Objects.requireNonNull(verification, "verification");
		if (sourceOrdinal < 0 || sourceOrdinal >= reload.getSourceCount()) {
			throw new IndexOutOfBoundsException(
				"Authored replay source ordinal is outside the reload recipe");
		}
		LayeredPackedRegionReloadRecipe.SourceRecipe source =
			reload.getSources().get(sourceOrdinal);
		if (!reload.isPointInTimeOnly()
			|| !reload.isDetachedDefinitionComplete()
			|| reload.isExecutableReload()
			|| reload.isRegionContainerCreated()
			|| reload.isSourceAbsencePerformed()
			|| reload.isSourceReconstructionPerformed()
			|| reload.isAuthoredReplayPerformed()
			|| reload.isCollisionRebuildPerformed()
			|| reload.isRuntimeHandleRetained()
			|| reload.isRegionRegistryMutated()
			|| reload.isResidencyMirrorMutated()
			|| reload.isVisibilityCacheMutated()
			|| reload.isArrivalGate()
			|| reload.isLifecycleAuthority()
			|| terrain.getGeneration() != reload.getGeneration()
			|| terrain.getRequirementsObservedAtTick()
				!= reload.getRequirementsObservedAtTick()
			|| terrain.getObservedAtTick() != reload.getObservedAtTick()
			|| terrain.getResidencyMirrorVersion()
				!= reload.getResidencyMirrorVersion()
			|| terrain.getAuthoredGeneration()
				!= reload.getAuthoredGeneration()
			|| terrain.getSourceOrdinal() != sourceOrdinal
			|| terrain.getPackedRegionX() != source.getPackedRegionX()
			|| terrain.getPackedRegionY() != source.getPackedRegionY()
			|| !terrain.isVerificationOnly()
			|| !terrain.isDisposableRegionConstructed()
			|| !terrain.isBlankContractMatchedBeforeApply()
			|| !terrain.isTerrainApplyPerformedOnDisposableRegion()
			|| !terrain.isAllTerrainTilesMatchedAfterApply()
			|| !terrain.isDynamicProductsAbsentAfterApply()
			|| !terrain.isEmptyEntityMembershipMatchedAfterApply()
			|| terrain.isExecutableReload()
			|| terrain.isUsableRegionContainerReturned()
			|| terrain.isRuntimeHandleRetained()
			|| terrain.isSourceAbsencePerformed()
			|| terrain.isSourceReconstructionPerformed()
			|| terrain.isAuthoredReplayPerformed()
			|| terrain.isDynamicCollisionRebuildPerformed()
			|| terrain.isActiveFamilyPreservationPerformed()
			|| terrain.isRegionRegistryMutated()
			|| terrain.isResidencyMirrorMutated()
			|| terrain.isVisibilityCacheMutated()
			|| terrain.isArrivalGate()
			|| terrain.isVisibilityReleased()
			|| terrain.isLifecycleAuthority()
			|| source.getManifestPlacementCount()
				- source.getSupersededPlacementCount()
					!= source.getAuthoredPlacementCount()) {
			throw new IllegalArgumentException(
				"Authored replay inputs do not share one verified inert source");
		}

		this.generation = reload.getGeneration();
		this.requirementsObservedAtTick =
			reload.getRequirementsObservedAtTick();
		this.observedAtTick = reload.getObservedAtTick();
		this.residencyMirrorVersion = reload.getResidencyMirrorVersion();
		this.authoredGeneration = reload.getAuthoredGeneration();
		this.selectedSourceOrdinal = sourceOrdinal;
		this.packedRegionX = source.getPackedRegionX();
		this.packedRegionY = source.getPackedRegionY();
		this.authoredSourceDeclared = source.isAuthoredSourceDeclared();
		this.manifestPlacementCount = source.getManifestPlacementCount();
		this.supersededPlacementCount = source.getSupersededPlacementCount();

		List<AuthoredReplayPlacement> copied =
			new ArrayList<AuthoredReplayPlacement>(
				source.getAuthoredPlacementCount());
		int scenery = 0;
		int boundaries = 0;
		int npcs = 0;
		int groundItems = 0;
		int harvesting = 0;
		int crossSource = 0;
		int affectedReferences = 0;
		int priorAuthoredOrdinal = 0;
		for (ReconstructionPlacement definition
			: source.getAuthoredPlacements()) {
			AuthoredReplayPlacement replay =
				AuthoredReplayPlacement.copyOf(
					Objects.requireNonNull(
						definition, "authoredReplayPlacement"),
					authoredGeneration, packedRegionX, packedRegionY);
			if (replay.getAuthoredSourceOrdinal() <= priorAuthoredOrdinal) {
				throw new IllegalArgumentException(
					"Authored replay order is not stable construction order");
			}
			priorAuthoredOrdinal = replay.getAuthoredSourceOrdinal();
			copied.add(replay);
			switch (replay.getConstructionKind()) {
				case SCENERY:
					scenery = Math.incrementExact(scenery);
					break;
				case BOUNDARY:
					boundaries = Math.incrementExact(boundaries);
					break;
				case NPC_SPAWN:
					npcs = Math.incrementExact(npcs);
					break;
				case GROUND_ITEM_SPAWN:
					groundItems = Math.incrementExact(groundItems);
					break;
				case HARVESTING_SCENERY:
					harvesting = Math.incrementExact(harvesting);
					break;
				default:
					throw new IllegalArgumentException(
						"Unknown authored replay family");
			}
			crossSource += replay.isCrossSource() ? 1 : 0;
			affectedReferences = Math.addExact(
				affectedReferences, replay.getAffectedSourceCount());
		}
		if (affectedReferences != source.getAffectedSourceReferenceCount()) {
			throw new IllegalArgumentException(
				"Authored replay dependency totals do not reconcile");
		}
		this.placements = Collections.unmodifiableList(copied);
		this.sceneryPlacementCount = scenery;
		this.boundaryPlacementCount = boundaries;
		this.npcSpawnPlacementCount = npcs;
		this.groundItemSpawnPlacementCount = groundItems;
		this.harvestingSceneryPlacementCount = harvesting;
		this.crossSourcePlacementCount = crossSource;
		this.affectedSourceReferenceCount = affectedReferences;
		this.fingerprintSha256 = fingerprint(copied);
	}

	public static LayeredPackedRegionAuthoredReplayPlan define(
		final LayeredPackedRegionReloadRecipe reloadRecipe,
		final int sourceOrdinal,
		final LayeredPackedRegionIsolatedTerrainVerification verification) {
		return new LayeredPackedRegionAuthoredReplayPlan(
			reloadRecipe, sourceOrdinal, verification);
	}

	public long getGeneration() { return generation; }
	public long getRequirementsObservedAtTick() {
		return requirementsObservedAtTick;
	}
	public long getObservedAtTick() { return observedAtTick; }
	public long getResidencyMirrorVersion() { return residencyMirrorVersion; }
	public long getAuthoredGeneration() { return authoredGeneration; }
	public int getSelectedSourceOrdinal() { return selectedSourceOrdinal; }
	public int getPackedRegionX() { return packedRegionX; }
	public int getPackedRegionY() { return packedRegionY; }
	public boolean isAuthoredSourceDeclared() {
		return authoredSourceDeclared;
	}
	public boolean isExactEmptyReplay() { return placements.isEmpty(); }
	public int getManifestPlacementCount() {
		return manifestPlacementCount;
	}
	public int getSupersededPlacementCount() {
		return supersededPlacementCount;
	}
	public List<AuthoredReplayPlacement> getPlacements() {
		return placements;
	}
	public int getPlacementCount() { return placements.size(); }
	public int getSceneryPlacementCount() {
		return sceneryPlacementCount;
	}
	public int getBoundaryPlacementCount() {
		return boundaryPlacementCount;
	}
	public int getNpcSpawnPlacementCount() {
		return npcSpawnPlacementCount;
	}
	public int getGroundItemSpawnPlacementCount() {
		return groundItemSpawnPlacementCount;
	}
	public int getHarvestingSceneryPlacementCount() {
		return harvestingSceneryPlacementCount;
	}
	public int getAuthoredObjectPlacementCount() {
		return sceneryPlacementCount + boundaryPlacementCount
			+ harvestingSceneryPlacementCount;
	}
	public int getCrossSourcePlacementCount() {
		return crossSourcePlacementCount;
	}
	public int getAffectedSourceReferenceCount() {
		return affectedSourceReferenceCount;
	}
	public String getFingerprintSha256() { return fingerprintSha256; }

	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedReplayDefinition() { return true; }
	public boolean isReplayDefinitionComplete() { return true; }
	public boolean isTerrainVerificationRequiredAndMatched() { return true; }
	public boolean isExecutableReplay() { return false; }
	public boolean isRegionContainerReturned() { return false; }
	public boolean isAuthoredSceneryMembershipApplied() { return false; }
	public boolean isNpcMembershipApplied() { return false; }
	public boolean isGroundItemMembershipApplied() { return false; }
	public boolean isCollisionDerived() { return false; }
	public boolean isSchedulerStateRestored() { return false; }
	public boolean isSourceAbsencePerformed() { return false; }
	public boolean isSourceReconstructionPerformed() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isRegionRegistryMutated() { return false; }
	public boolean isResidencyMirrorMutated() { return false; }
	public boolean isVisibilityCacheMutated() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isVisibilityReleased() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	private static String fingerprint(
		final List<AuthoredReplayPlacement> definitions) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (AuthoredReplayPlacement definition : definitions) {
				definition.updateDigest(digest);
			}
			StringBuilder result = new StringBuilder(64);
			for (byte value : digest.digest()) {
				result.append(String.format("%02x", value & 0xff));
			}
			return result.toString();
		} catch (NoSuchAlgorithmException unavailable) {
			throw new IllegalStateException(
				"SHA-256 is unavailable for authored replay identity",
				unavailable);
		}
	}

	/** One typed immutable constructor definition in stable authored order. */
	public static final class AuthoredReplayPlacement {
		private static final int NA =
			LayeredPackedRegionAuthoredPlacementManifest.NOT_APPLICABLE;

		private final long authoredGeneration;
		private final int sourcePackedRegionX;
		private final int sourcePackedRegionY;
		private final int authoredSourceOrdinal;
		private final ConstructionKind constructionKind;
		private final int authoredDefinitionId;
		private final int constructedEntityId;
		private final int packedX;
		private final int packedY;
		private final int permanentObjectId;
		private final int direction;
		private final int objectType;
		private final String objectOwner;
		private final int npcMinimumX;
		private final int npcMaximumX;
		private final int npcMinimumY;
		private final int npcMaximumY;
		private final int itemAmount;
		private final int itemRespawnTime;
		private final int itemNoted;
		private final DependencyKind dependencyKind;
		private final int minimumPackedX;
		private final int maximumPackedX;
		private final int minimumPackedY;
		private final int maximumPackedY;
		private final int minimumPackedRegionX;
		private final int maximumPackedRegionX;
		private final int minimumPackedRegionY;
		private final int maximumPackedRegionY;
		private final int affectedSourceCount;
		private final boolean crossSource;

		private AuthoredReplayPlacement(
			final ReconstructionPlacement reconstruction,
			final long expectedGeneration,
			final int expectedPackedRegionX,
			final int expectedPackedRegionY) {
			AuthoredPlacement placement =
				Objects.requireNonNull(
					reconstruction.getPlacement(), "placement");
			PlacementDependency dependency =
				Objects.requireNonNull(
					reconstruction.getDependency(), "dependency");
			if (placement.getIdentity().getGeneration() != expectedGeneration
				|| placement.getIdentity().getPackedRegionX()
					!= expectedPackedRegionX
				|| placement.getIdentity().getPackedRegionY()
					!= expectedPackedRegionY
				|| placement.getSourceOrdinal()
					!= dependency.getSourceOrdinal()
				|| placement.getKind() != dependency.getKind()
				|| placement.getPackedX() / Constants.REGION_SIZE
					!= expectedPackedRegionX
				|| placement.getPackedY() / Constants.REGION_SIZE
					!= expectedPackedRegionY) {
				throw new IllegalArgumentException(
					"Authored replay placement identity is inconsistent");
			}
			validateDependency(placement, dependency);
			validateFamily(placement, dependency);
			this.authoredGeneration = expectedGeneration;
			this.sourcePackedRegionX = expectedPackedRegionX;
			this.sourcePackedRegionY = expectedPackedRegionY;
			this.authoredSourceOrdinal = placement.getSourceOrdinal();
			this.constructionKind = placement.getKind();
			this.authoredDefinitionId = placement.getAuthoredDefinitionId();
			this.constructedEntityId = placement.getConstructedEntityId();
			this.packedX = placement.getPackedX();
			this.packedY = placement.getPackedY();
			this.permanentObjectId = placement.getPermanentObjectId();
			this.direction = placement.getDirection();
			this.objectType = placement.getObjectType();
			this.objectOwner = placement.getObjectOwner();
			this.npcMinimumX = placement.getNpcMinimumX();
			this.npcMaximumX = placement.getNpcMaximumX();
			this.npcMinimumY = placement.getNpcMinimumY();
			this.npcMaximumY = placement.getNpcMaximumY();
			this.itemAmount = placement.getItemAmount();
			this.itemRespawnTime = placement.getItemRespawnTime();
			this.itemNoted = placement.getItemNoted();
			this.dependencyKind = dependency.getDependencyKind();
			this.minimumPackedX = dependency.getMinimumPackedX();
			this.maximumPackedX = dependency.getMaximumPackedX();
			this.minimumPackedY = dependency.getMinimumPackedY();
			this.maximumPackedY = dependency.getMaximumPackedY();
			this.minimumPackedRegionX =
				dependency.getMinimumPackedRegionX();
			this.maximumPackedRegionX =
				dependency.getMaximumPackedRegionX();
			this.minimumPackedRegionY =
				dependency.getMinimumPackedRegionY();
			this.maximumPackedRegionY =
				dependency.getMaximumPackedRegionY();
			this.affectedSourceCount = dependency.getAffectedSourceCount();
			this.crossSource = dependency.isCrossSource();
		}

		private static AuthoredReplayPlacement copyOf(
			final ReconstructionPlacement reconstruction,
			final long expectedGeneration,
			final int expectedPackedRegionX,
			final int expectedPackedRegionY) {
			return new AuthoredReplayPlacement(
				reconstruction, expectedGeneration,
				expectedPackedRegionX, expectedPackedRegionY);
		}

		private static void validateDependency(
			final AuthoredPlacement placement,
			final PlacementDependency dependency) {
			long width = (long) dependency.getMaximumPackedRegionX()
				- dependency.getMinimumPackedRegionX() + 1L;
			long height = (long) dependency.getMaximumPackedRegionY()
				- dependency.getMinimumPackedRegionY() + 1L;
			boolean expectedCrossSource =
				dependency.getMinimumPackedRegionX()
						!= placement.getIdentity().getPackedRegionX()
					|| dependency.getMaximumPackedRegionX()
						!= placement.getIdentity().getPackedRegionX()
					|| dependency.getMinimumPackedRegionY()
						!= placement.getIdentity().getPackedRegionY()
					|| dependency.getMaximumPackedRegionY()
						!= placement.getIdentity().getPackedRegionY();
			if (dependency.getMinimumPackedX() > placement.getPackedX()
				|| dependency.getMaximumPackedX() < placement.getPackedX()
				|| dependency.getMinimumPackedY() > placement.getPackedY()
				|| dependency.getMaximumPackedY() < placement.getPackedY()
				|| dependency.getMinimumPackedRegionX()
					> placement.getIdentity().getPackedRegionX()
				|| dependency.getMaximumPackedRegionX()
					< placement.getIdentity().getPackedRegionX()
				|| dependency.getMinimumPackedRegionY()
					> placement.getIdentity().getPackedRegionY()
				|| dependency.getMaximumPackedRegionY()
					< placement.getIdentity().getPackedRegionY()
				|| Math.multiplyExact(width, height)
					!= dependency.getAffectedSourceCount()
				|| dependency.isCrossSource() != expectedCrossSource) {
				throw new IllegalArgumentException(
					"Authored replay dependency envelope is inconsistent");
			}
		}

		private static void validateFamily(
			final AuthoredPlacement placement,
			final PlacementDependency dependency) {
			boolean objectScalarsAbsent =
				placement.getPermanentObjectId() == NA
					&& placement.getDirection() == NA
					&& placement.getObjectType() == NA
					&& placement.getObjectOwner() == null;
			boolean npcScalarsAbsent =
				placement.getNpcMinimumX() == NA
					&& placement.getNpcMaximumX() == NA
					&& placement.getNpcMinimumY() == NA
					&& placement.getNpcMaximumY() == NA;
			boolean itemScalarsAbsent =
				placement.getItemAmount() == NA
					&& placement.getItemRespawnTime() == NA
					&& placement.getItemNoted() == NA;
			switch (placement.getKind()) {
				case SCENERY:
				require(
					placement.getAuthoredDefinitionId()
							== placement.getConstructedEntityId()
						&& placement.getPermanentObjectId() >= 0
						&& placement.getDirection() >= 0
						&& placement.getObjectType() == 0
						&& npcScalarsAbsent && itemScalarsAbsent
						&& dependency.getDependencyKind()
							== DependencyKind.OBJECT_FOOTPRINT,
					"scenery");
				break;
				case BOUNDARY:
				require(
					placement.getAuthoredDefinitionId()
							== placement.getConstructedEntityId()
						&& placement.getPermanentObjectId() >= 0
						&& placement.getDirection() >= 0
						&& placement.getObjectType() == 1
						&& npcScalarsAbsent && itemScalarsAbsent
						&& dependency.getDependencyKind()
							== DependencyKind.OBJECT_FOOTPRINT,
					"boundary");
				break;
				case NPC_SPAWN:
				require(
					placement.getAuthoredDefinitionId()
							== placement.getConstructedEntityId()
						&& objectScalarsAbsent && itemScalarsAbsent
						&& placement.getNpcMinimumX() >= 0
						&& placement.getNpcMinimumX()
							<= placement.getPackedX()
						&& placement.getNpcMaximumX()
							>= placement.getPackedX()
						&& placement.getNpcMinimumY() >= 0
						&& placement.getNpcMinimumY()
							<= placement.getPackedY()
						&& placement.getNpcMaximumY()
							>= placement.getPackedY()
						&& dependency.getDependencyKind()
							== DependencyKind.NPC_ROAMING,
					"NPC spawn");
				break;
				case GROUND_ITEM_SPAWN:
				require(
					placement.getAuthoredDefinitionId()
							== placement.getConstructedEntityId()
						&& objectScalarsAbsent && npcScalarsAbsent
						&& placement.getItemAmount() >= 0
						&& placement.getItemRespawnTime() >= 0
						&& placement.getItemNoted() >= 0
						&& dependency.getDependencyKind()
							== DependencyKind.ANCHOR_ONLY,
					"ground-item spawn");
				break;
				case HARVESTING_SCENERY:
				require(
					placement.getPermanentObjectId() >= 0
						&& placement.getDirection() >= 0
						&& placement.getObjectType() == 0
						&& npcScalarsAbsent
						&& placement.getItemAmount() >= 0
						&& placement.getItemRespawnTime() >= 0
						&& placement.getItemNoted() >= 0
						&& dependency.getDependencyKind()
							== DependencyKind.OBJECT_FOOTPRINT,
					"harvesting scenery");
				break;
				default:
					throw new IllegalArgumentException(
						"Unknown authored replay family");
			}
		}

		private static void require(
			final boolean condition,
			final String family) {
			if (!condition) {
				throw new IllegalArgumentException(
					"Invalid " + family + " authored replay constructor");
			}
		}

		public long getAuthoredGeneration() { return authoredGeneration; }
		public int getSourcePackedRegionX() {
			return sourcePackedRegionX;
		}
		public int getSourcePackedRegionY() {
			return sourcePackedRegionY;
		}
		public int getAuthoredSourceOrdinal() {
			return authoredSourceOrdinal;
		}
		public ConstructionKind getConstructionKind() {
			return constructionKind;
		}
		public int getAuthoredDefinitionId() {
			return authoredDefinitionId;
		}
		public int getConstructedEntityId() {
			return constructedEntityId;
		}
		public int getPackedX() { return packedX; }
		public int getPackedY() { return packedY; }
		public int getPermanentObjectId() { return permanentObjectId; }
		public int getDirection() { return direction; }
		public int getObjectType() { return objectType; }
		public String getObjectOwner() { return objectOwner; }
		public int getNpcMinimumX() { return npcMinimumX; }
		public int getNpcMaximumX() { return npcMaximumX; }
		public int getNpcMinimumY() { return npcMinimumY; }
		public int getNpcMaximumY() { return npcMaximumY; }
		public int getItemAmount() { return itemAmount; }
		public int getItemRespawnTime() { return itemRespawnTime; }
		public int getItemNoted() { return itemNoted; }
		public DependencyKind getDependencyKind() { return dependencyKind; }
		public int getMinimumPackedX() { return minimumPackedX; }
		public int getMaximumPackedX() { return maximumPackedX; }
		public int getMinimumPackedY() { return minimumPackedY; }
		public int getMaximumPackedY() { return maximumPackedY; }
		public int getMinimumPackedRegionX() {
			return minimumPackedRegionX;
		}
		public int getMaximumPackedRegionX() {
			return maximumPackedRegionX;
		}
		public int getMinimumPackedRegionY() {
			return minimumPackedRegionY;
		}
		public int getMaximumPackedRegionY() {
			return maximumPackedRegionY;
		}
		public int getAffectedSourceCount() {
			return affectedSourceCount;
		}
		public boolean isCrossSource() { return crossSource; }

		private void updateDigest(final MessageDigest digest) {
			updateLong(digest, authoredGeneration);
			updateInt(digest, sourcePackedRegionX);
			updateInt(digest, sourcePackedRegionY);
			updateInt(digest, authoredSourceOrdinal);
			updateInt(digest, constructionKind.ordinal());
			updateInt(digest, authoredDefinitionId);
			updateInt(digest, constructedEntityId);
			updateInt(digest, packedX);
			updateInt(digest, packedY);
			updateInt(digest, permanentObjectId);
			updateInt(digest, direction);
			updateInt(digest, objectType);
			updateString(digest, objectOwner);
			updateInt(digest, npcMinimumX);
			updateInt(digest, npcMaximumX);
			updateInt(digest, npcMinimumY);
			updateInt(digest, npcMaximumY);
			updateInt(digest, itemAmount);
			updateInt(digest, itemRespawnTime);
			updateInt(digest, itemNoted);
			updateInt(digest, dependencyKind.ordinal());
			updateInt(digest, minimumPackedX);
			updateInt(digest, maximumPackedX);
			updateInt(digest, minimumPackedY);
			updateInt(digest, maximumPackedY);
			updateInt(digest, minimumPackedRegionX);
			updateInt(digest, maximumPackedRegionX);
			updateInt(digest, minimumPackedRegionY);
			updateInt(digest, maximumPackedRegionY);
			updateInt(digest, affectedSourceCount);
			digest.update((byte) (crossSource ? 1 : 0));
		}

		private static void updateString(
			final MessageDigest digest,
			final String value) {
			if (value == null) {
				updateInt(digest, -1);
				return;
			}
			byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
			updateInt(digest, bytes.length);
			digest.update(bytes);
		}

		private static void updateLong(
			final MessageDigest digest,
			final long value) {
			updateInt(digest, (int) (value >>> 32));
			updateInt(digest, (int) value);
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
