package com.openrsc.server.model.world.region;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;
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
 * Inert reverse-order definition for detaching an exact resident authored
 * object set.
 *
 * <p>The plan is available only after the complete runtime authored-object
 * census matches the disposable transactional reconstruction baseline. It
 * copies authored identities and constructor scalars in reverse stable
 * construction order, but deliberately does not look up or retain a runtime
 * object, read shared collision tiles, enter a scheduler boundary, or execute
 * a collision/object transaction.</p>
 *
 * <p>Scheduler correlation, active-family preservation, a rollback contract,
 * an arrival/visibility gate, and a fresh atomic runtime revalidation remain
 * mandatory before any future consumer could become executable.</p>
 */
public final class LayeredPackedRegionAuthoredObjectDetachmentPlan {
	private final long generation;
	private final long requirementsObservedAtTick;
	private final long recipeObservedAtTick;
	private final long runtimeObservedAtTick;
	private final long residencyMirrorVersion;
	private final long authoredGeneration;
	private final List<SourcePlan> sources;
	private final long authoredObjectCount;
	private final long playerCountAtObservation;
	private final long npcCountAtObservation;
	private final long identitylessDynamicObjectCount;
	private final long groundItemCountAtObservation;
	private final String runtimeComparisonFingerprintSha256;
	private final String fingerprintSha256;

	private LayeredPackedRegionAuthoredObjectDetachmentPlan(
		final LayeredPackedRegionReloadRecipe reloadRecipe,
		final LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison
			runtimeComparison) {
		LayeredPackedRegionReloadRecipe recipe =
			Objects.requireNonNull(reloadRecipe, "reloadRecipe");
		LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison comparison =
			Objects.requireNonNull(runtimeComparison, "runtimeComparison");
		if (!recipe.isPointInTimeOnly()
			|| !recipe.isDetachedDefinitionComplete()
			|| recipe.isExecutableReload()
			|| recipe.isSourceAbsencePerformed()
			|| recipe.isSourceReconstructionPerformed()
			|| recipe.isRuntimeHandleRetained()
			|| recipe.isLifecycleAuthority()
			|| !comparison.isPointInTimeOnly()
			|| !comparison.isDetachedSummaryOnly()
			|| !comparison.areAllSourcesExactBaselineMatches()
			|| comparison.isSharedCollisionTileComparisonPerformed()
			|| comparison.isSchedulerCorrelationPerformed()
			|| comparison.isRuntimeHandleRetained()
			|| comparison.isRuntimeMutationPerformed()
			|| comparison.isLifecycleAuthority()
			|| comparison.getGeneration() != recipe.getGeneration()
			|| comparison.getRequirementsObservedAtTick()
				!= recipe.getRequirementsObservedAtTick()
			|| comparison.getRecipeObservedAtTick() != recipe.getObservedAtTick()
			|| comparison.getResidencyMirrorVersion()
				!= recipe.getResidencyMirrorVersion()
			|| comparison.getSourceCount() != recipe.getSourceCount()) {
			throw new IllegalArgumentException(
				"Authored-object detachment inputs are not one exact inert baseline");
		}
		this.generation = recipe.getGeneration();
		this.requirementsObservedAtTick =
			recipe.getRequirementsObservedAtTick();
		this.recipeObservedAtTick = recipe.getObservedAtTick();
		this.runtimeObservedAtTick = comparison.getRuntimeObservedAtTick();
		this.residencyMirrorVersion = recipe.getResidencyMirrorVersion();
		this.authoredGeneration = recipe.getAuthoredGeneration();
		this.runtimeComparisonFingerprintSha256 =
			comparison.getFingerprintSha256();

		List<SourcePlan> planned =
			new ArrayList<SourcePlan>(recipe.getSourceCount());
		long objects = 0L;
		long players = 0L;
		long npcs = 0L;
		long dynamics = 0L;
		long groundItems = 0L;
		for (int sourceOrdinal = 0;
			sourceOrdinal < recipe.getSourceCount(); sourceOrdinal++) {
			SourcePlan source = new SourcePlan(
				recipe.getSources().get(sourceOrdinal),
				comparison.getSources().get(sourceOrdinal),
				sourceOrdinal, authoredGeneration);
			planned.add(source);
			objects = Math.addExact(
				objects, (long) source.getObjectCount());
			players = Math.addExact(
				players, (long) source.getPlayerCountAtObservation());
			npcs = Math.addExact(
				npcs, (long) source.getNpcCountAtObservation());
			dynamics = Math.addExact(
				dynamics,
				(long) source.getIdentitylessDynamicObjectCount());
			groundItems = Math.addExact(
				groundItems,
				(long) source.getGroundItemCountAtObservation());
		}
		if (objects != comparison.getExpectedAuthoredObjectCount()
			|| dynamics != comparison.getIdentitylessDynamicObjectCount()) {
			throw new IllegalArgumentException(
				"Authored-object detachment aggregate counts drifted");
		}
		this.sources = Collections.unmodifiableList(planned);
		this.authoredObjectCount = objects;
		this.playerCountAtObservation = players;
		this.npcCountAtObservation = npcs;
		this.identitylessDynamicObjectCount = dynamics;
		this.groundItemCountAtObservation = groundItems;
		this.fingerprintSha256 = fingerprint(
			runtimeComparisonFingerprintSha256, planned);
	}

	public static LayeredPackedRegionAuthoredObjectDetachmentPlan define(
		final LayeredPackedRegionReloadRecipe reloadRecipe,
		final LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison
			runtimeComparison) {
		return new LayeredPackedRegionAuthoredObjectDetachmentPlan(
			reloadRecipe, runtimeComparison);
	}

	public long getGeneration() { return generation; }
	public long getRequirementsObservedAtTick() {
		return requirementsObservedAtTick;
	}
	public long getRecipeObservedAtTick() { return recipeObservedAtTick; }
	public long getRuntimeObservedAtTick() { return runtimeObservedAtTick; }
	public long getResidencyMirrorVersion() {
		return residencyMirrorVersion;
	}
	public long getAuthoredGeneration() { return authoredGeneration; }
	public List<SourcePlan> getSources() { return sources; }
	public int getSourceCount() { return sources.size(); }
	public long getAuthoredObjectCount() { return authoredObjectCount; }
	public long getPlayerCountAtObservation() {
		return playerCountAtObservation;
	}
	public long getNpcCountAtObservation() {
		return npcCountAtObservation;
	}
	public long getIdentitylessDynamicObjectCount() {
		return identitylessDynamicObjectCount;
	}
	public long getGroundItemCountAtObservation() {
		return groundItemCountAtObservation;
	}
	public String getRuntimeComparisonFingerprintSha256() {
		return runtimeComparisonFingerprintSha256;
	}
	public String getFingerprintSha256() { return fingerprintSha256; }

	public boolean isExactRuntimeBaselineRequired() { return true; }
	public boolean isReverseStableAuthoredOrder() { return true; }
	public boolean isSchedulerCorrelationRequired() { return true; }
	public boolean isFreshAtomicRuntimeRevalidationRequired() { return true; }
	public boolean isCollisionDetachmentRequired() {
		return authoredObjectCount > 0L;
	}
	public boolean isPlayerPreservationRequired() {
		return playerCountAtObservation > 0L;
	}
	public boolean isNpcPreservationRequired() {
		return npcCountAtObservation > 0L;
	}
	public boolean isDynamicObjectPreservationRequired() {
		return identitylessDynamicObjectCount > 0L;
	}
	public boolean isGroundItemPreservationRequired() {
		return groundItemCountAtObservation > 0L;
	}
	public boolean isRollbackRequired() { return true; }
	public boolean isArrivalGateRequired() { return true; }
	public boolean isVisibilityGateRequired() { return true; }

	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedDefinitionOnly() { return true; }
	public boolean isExecutableDetachment() { return false; }
	public boolean isRuntimeLookupPerformed() { return false; }
	public boolean isSharedCollisionTileReadPerformed() { return false; }
	public boolean isSchedulerCorrelationPerformed() { return false; }
	public boolean isRuntimeMutationAuthorized() { return false; }
	public boolean isRuntimeMutationPerformed() { return false; }
	public boolean isRuntimeCacheInvalidated() { return false; }
	public boolean isSourceAbsencePerformed() { return false; }
	public boolean isSourceReconstructionPerformed() { return false; }
	public boolean isRegionRegistryMutated() { return false; }
	public boolean isResidencyMirrorMutated() { return false; }
	public boolean isVisibilityCacheMutated() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isVisibilityReleased() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	/** Reverse construction-order definition for one exact selected source. */
	public static final class SourcePlan {
		private final int selectedSourceOrdinal;
		private final int packedRegionX;
		private final int packedRegionY;
		private final int playerCountAtObservation;
		private final int npcCountAtObservation;
		private final int identitylessDynamicObjectCount;
		private final int groundItemCountAtObservation;
		private final List<ObjectDetachment> objects;
		private final String runtimeRegistrationFingerprintSha256;
		private final String baselineRegistrationFingerprintSha256;
		private final String fingerprintSha256;

		private SourcePlan(
			final LayeredPackedRegionReloadRecipe.SourceRecipe recipe,
			final LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison
				.SourceComparison comparison,
			final int selectedSourceOrdinal,
			final long authoredGeneration) {
			if (comparison.getSourceOrdinal() != selectedSourceOrdinal
				|| comparison.getPackedRegionX() != recipe.getPackedRegionX()
				|| comparison.getPackedRegionY() != recipe.getPackedRegionY()
				|| comparison.getOutcome()
					!= LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison
						.Outcome.EXACT_BASELINE_MATCH
				|| !comparison.isRegistrationFingerprintMatched()) {
				throw new IllegalArgumentException(
					"Authored-object detachment source is not an exact baseline");
			}
			this.selectedSourceOrdinal = selectedSourceOrdinal;
			this.packedRegionX = recipe.getPackedRegionX();
			this.packedRegionY = recipe.getPackedRegionY();
			this.playerCountAtObservation =
				recipe.getPlayerCountAtObservation();
			this.npcCountAtObservation = recipe.getNpcCountAtObservation();
			this.identitylessDynamicObjectCount =
				comparison.getIdentitylessDynamicObjectCount();
			this.groundItemCountAtObservation =
				recipe.getGroundItemCountAtObservation();
			this.runtimeRegistrationFingerprintSha256 =
				comparison.getRuntimeRegistrationFingerprintSha256();
			this.baselineRegistrationFingerprintSha256 =
				comparison.getBaselineRegistrationFingerprintSha256();

			List<ObjectDetachment> reverse =
				new ArrayList<ObjectDetachment>(
					comparison.getExpectedAuthoredObjectCount());
			List<ReconstructionPlacement> placements =
				recipe.getAuthoredPlacements();
			for (int index = placements.size() - 1;
				index >= 0; index--) {
				ReconstructionPlacement reconstruction =
					Objects.requireNonNull(
						placements.get(index), "reconstructionPlacement");
				if (!isObjectKind(reconstruction.getKind())) { continue; }
				reverse.add(new ObjectDetachment(
					reconstruction.getPlacement(), reverse.size(),
					authoredGeneration, packedRegionX, packedRegionY));
			}
			if (reverse.size() != comparison.getExpectedAuthoredObjectCount()) {
				throw new IllegalArgumentException(
					"Authored-object detachment count differs from runtime baseline");
			}
			this.objects = Collections.unmodifiableList(reverse);
			this.fingerprintSha256 = fingerprintSource(
				selectedSourceOrdinal, packedRegionX, packedRegionY,
				runtimeRegistrationFingerprintSha256,
				baselineRegistrationFingerprintSha256, reverse);
		}

		public int getSelectedSourceOrdinal() {
			return selectedSourceOrdinal;
		}
		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getPlayerCountAtObservation() {
			return playerCountAtObservation;
		}
		public int getNpcCountAtObservation() {
			return npcCountAtObservation;
		}
		public int getIdentitylessDynamicObjectCount() {
			return identitylessDynamicObjectCount;
		}
		public int getGroundItemCountAtObservation() {
			return groundItemCountAtObservation;
		}
		public List<ObjectDetachment> getObjects() { return objects; }
		public int getObjectCount() { return objects.size(); }
		public String getRuntimeRegistrationFingerprintSha256() {
			return runtimeRegistrationFingerprintSha256;
		}
		public String getBaselineRegistrationFingerprintSha256() {
			return baselineRegistrationFingerprintSha256;
		}
		public String getFingerprintSha256() { return fingerprintSha256; }
	}

	/** Primitive authored identity and constructor required for one removal. */
	public static final class ObjectDetachment {
		private final int detachmentOrdinal;
		private final long authoredGeneration;
		private final int sourcePackedRegionX;
		private final int sourcePackedRegionY;
		private final int authoredSourceOrdinal;
		private final ConstructionKind constructionKind;
		private final int objectId;
		private final int permanentObjectId;
		private final int packedX;
		private final int packedY;
		private final int direction;
		private final int objectType;
		private final String objectOwner;

		private ObjectDetachment(
			final AuthoredPlacement placement,
			final int detachmentOrdinal,
			final long expectedGeneration,
			final int expectedPackedRegionX,
			final int expectedPackedRegionY) {
			AuthoredPlacement definition =
				Objects.requireNonNull(placement, "placement");
			if (detachmentOrdinal < 0
				|| definition.getIdentity().getGeneration()
					!= expectedGeneration
				|| definition.getIdentity().getPackedRegionX()
					!= expectedPackedRegionX
				|| definition.getIdentity().getPackedRegionY()
					!= expectedPackedRegionY
				|| !isObjectKind(definition.getKind())
				|| Math.floorDiv(
					definition.getPackedX(), Constants.REGION_SIZE)
						!= expectedPackedRegionX
				|| Math.floorDiv(
					definition.getPackedY(), Constants.REGION_SIZE)
						!= expectedPackedRegionY) {
				throw new IllegalArgumentException(
					"Authored-object detachment identity is inconsistent");
			}
			this.detachmentOrdinal = detachmentOrdinal;
			this.authoredGeneration = expectedGeneration;
			this.sourcePackedRegionX = expectedPackedRegionX;
			this.sourcePackedRegionY = expectedPackedRegionY;
			this.authoredSourceOrdinal = definition.getSourceOrdinal();
			this.constructionKind = definition.getKind();
			this.objectId = definition.getConstructedEntityId();
			this.permanentObjectId = definition.getPermanentObjectId();
			this.packedX = definition.getPackedX();
			this.packedY = definition.getPackedY();
			this.direction = definition.getDirection();
			this.objectType = definition.getObjectType();
			this.objectOwner = definition.getObjectOwner();
		}

		public int getDetachmentOrdinal() { return detachmentOrdinal; }
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
		public int getObjectId() { return objectId; }
		public int getPermanentObjectId() { return permanentObjectId; }
		public int getPackedX() { return packedX; }
		public int getPackedY() { return packedY; }
		public int getDirection() { return direction; }
		public int getObjectType() { return objectType; }
		public String getObjectOwner() { return objectOwner; }
	}

	private static boolean isObjectKind(final ConstructionKind kind) {
		return kind == ConstructionKind.SCENERY
			|| kind == ConstructionKind.BOUNDARY
			|| kind == ConstructionKind.HARVESTING_SCENERY;
	}

	private static String fingerprint(
		final String comparisonFingerprint,
		final List<SourcePlan> sources) {
		MessageDigest digest = sha256();
		updateString(digest, comparisonFingerprint);
		updateInt(digest, sources.size());
		for (SourcePlan source : sources) {
			updateString(digest, source.getFingerprintSha256());
		}
		return hex(digest.digest());
	}

	private static String fingerprintSource(
		final int sourceOrdinal,
		final int packedRegionX,
		final int packedRegionY,
		final String runtimeRegistrationFingerprint,
		final String baselineRegistrationFingerprint,
		final List<ObjectDetachment> objects) {
		MessageDigest digest = sha256();
		updateInt(digest, sourceOrdinal);
		updateInt(digest, packedRegionX);
		updateInt(digest, packedRegionY);
		updateString(digest, runtimeRegistrationFingerprint);
		updateString(digest, baselineRegistrationFingerprint);
		updateInt(digest, objects.size());
		for (ObjectDetachment object : objects) {
			updateInt(digest, object.getDetachmentOrdinal());
			updateLong(digest, object.getAuthoredGeneration());
			updateInt(digest, object.getSourcePackedRegionX());
			updateInt(digest, object.getSourcePackedRegionY());
			updateInt(digest, object.getAuthoredSourceOrdinal());
			updateInt(digest, object.getConstructionKind().ordinal());
			updateInt(digest, object.getObjectId());
			updateInt(digest, object.getPermanentObjectId());
			updateInt(digest, object.getPackedX());
			updateInt(digest, object.getPackedY());
			updateInt(digest, object.getDirection());
			updateInt(digest, object.getObjectType());
			updateString(digest, object.getObjectOwner());
		}
		return hex(digest.digest());
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static void updateInt(
		final MessageDigest digest, final int value) {
		digest.update((byte) (value >>> 24));
		digest.update((byte) (value >>> 16));
		digest.update((byte) (value >>> 8));
		digest.update((byte) value);
	}

	private static void updateLong(
		final MessageDigest digest, final long value) {
		digest.update((byte) (value >>> 56));
		digest.update((byte) (value >>> 48));
		digest.update((byte) (value >>> 40));
		digest.update((byte) (value >>> 32));
		digest.update((byte) (value >>> 24));
		digest.update((byte) (value >>> 16));
		digest.update((byte) (value >>> 8));
		digest.update((byte) value);
	}

	private static void updateString(
		final MessageDigest digest, final String value) {
		if (value == null) {
			updateInt(digest, -1);
			return;
		}
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		updateInt(digest, bytes.length);
		digest.update(bytes);
	}

	private static String hex(final byte[] bytes) {
		StringBuilder result = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) {
			result.append(String.format("%02x", value & 0xff));
		}
		return result.toString();
	}
}
