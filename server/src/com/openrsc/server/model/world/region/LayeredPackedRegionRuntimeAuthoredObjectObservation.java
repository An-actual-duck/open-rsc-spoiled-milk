package com.openrsc.server.model.world.region;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GameObjectCollisionRegistrationState;
import com.openrsc.server.model.world.coordinate.LayeredAuthoredPlacementIdentity;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionAuthoredReconstructionRecipe.ReconstructionPlacement;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded point-in-time comparison of resident authored objects with one
 * selected-source reload recipe.
 *
 * <p>Final-live constructor matches, authored transient replacements, missing
 * expected identities, duplicate identities, unrecognized authored identities,
 * and identity-less dynamic objects remain separate facts. A valid collision
 * registration means only that the immutable receipt matches the current
 * object's constructor; this observation does not compare shared live tile
 * counters or claim that a transient object should equal the final-live
 * recipe.</p>
 *
 * <p>The complete observation retains only per-source counts and fingerprints.
 * Temporary captures contain detached primitive copies and are discarded by
 * {@link #observe}. No entity, Region, tile, registration, registry, event,
 * cache, lifecycle, loading, teardown, reconstruction, mutation, or arrival
 * authority survives.</p>
 */
public final class LayeredPackedRegionRuntimeAuthoredObjectObservation {
	public static final int MAXIMUM_OBJECT_INSTANCES = 100000;

	private final long generation;
	private final long requirementsObservedAtTick;
	private final long recipeObservedAtTick;
	private final long runtimeObservedAtTick;
	private final long residencyMirrorVersion;
	private final List<SourceObservation> sources;
	private final long expectedAuthoredObjectCount;
	private final long observedObjectCount;
	private final long identitylessDynamicObjectCount;
	private final long authoredIdentityObjectCount;
	private final long recognizedAuthoredInstanceCount;
	private final long unrecognizedAuthoredInstanceCount;
	private final long uniqueRecognizedIdentityCount;
	private final long duplicateRecognizedIdentityInstanceCount;
	private final long missingExpectedIdentityCount;
	private final long exactFinalLiveInstanceCount;
	private final long authoredTransientInstanceCount;
	private final long collisionRegistrationPresentCount;
	private final long collisionRegistrationMissingCount;
	private final long collisionRegistrationConstructorMismatchCount;
	private final long collisionRegistrationContributionCount;
	private final long collisionRegistrationRegionReferenceCount;
	private final String fingerprintSha256;

	private LayeredPackedRegionRuntimeAuthoredObjectObservation(
		final LayeredPackedRegionReloadRecipe recipe,
		final long runtimeObservedAtTick,
		final List<SourceCapture> captures,
		final int maximumObjectInstances) {
		if (runtimeObservedAtTick < recipe.getObservedAtTick()) {
			throw new IllegalArgumentException(
				"Runtime authored-object observation predates its recipe");
		}
		if (captures.size() != recipe.getSourceCount()) {
			throw new IllegalArgumentException(
				"Runtime authored-object source count differs from its recipe");
		}
		this.generation = recipe.getGeneration();
		this.requirementsObservedAtTick =
			recipe.getRequirementsObservedAtTick();
		this.recipeObservedAtTick = recipe.getObservedAtTick();
		this.runtimeObservedAtTick = runtimeObservedAtTick;
		this.residencyMirrorVersion = recipe.getResidencyMirrorVersion();

		List<SourceObservation> observed =
			new ArrayList<SourceObservation>(captures.size());
		long expectedObjects = 0L;
		long runtimeObjects = 0L;
		long dynamicObjects = 0L;
		long authoredObjects = 0L;
		long recognizedObjects = 0L;
		long unrecognizedObjects = 0L;
		long uniqueRecognized = 0L;
		long duplicateRecognized = 0L;
		long missingExpected = 0L;
		long exactFinal = 0L;
		long transientAuthored = 0L;
		long registrationsPresent = 0L;
		long registrationsMissing = 0L;
		long registrationsMismatched = 0L;
		long registrationContributions = 0L;
		long registrationRegions = 0L;
		for (int index = 0; index < captures.size(); index++) {
			LayeredPackedRegionReloadRecipe.SourceRecipe sourceRecipe =
				recipe.getSources().get(index);
			SourceCapture capture = Objects.requireNonNull(
				captures.get(index), "sourceCapture");
			if (sourceRecipe.getPackedRegionX() != capture.getPackedRegionX()
				|| sourceRecipe.getPackedRegionY()
					!= capture.getPackedRegionY()
				|| !capture.isObjectBoundaryHeldDuringCapture()) {
				throw new IllegalArgumentException(
					"Runtime authored-object capture escaped source order or boundary");
			}
			runtimeObjects = Math.addExact(
				runtimeObjects, (long) capture.getObjects().size());
			if (runtimeObjects > maximumObjectInstances) {
				throw new IllegalArgumentException(
					"Runtime authored-object observation exceeds its object budget");
			}
			SourceObservation source = new SourceObservation(
				sourceRecipe, capture, index, recipe.getAuthoredGeneration());
			observed.add(source);
			expectedObjects = Math.addExact(
				expectedObjects, (long) source.getExpectedAuthoredObjectCount());
			dynamicObjects = Math.addExact(
				dynamicObjects,
				(long) source.getIdentitylessDynamicObjectCount());
			authoredObjects = Math.addExact(
				authoredObjects, (long) source.getAuthoredIdentityObjectCount());
			recognizedObjects = Math.addExact(
				recognizedObjects,
				(long) source.getRecognizedAuthoredInstanceCount());
			unrecognizedObjects = Math.addExact(
				unrecognizedObjects,
				(long) source.getUnrecognizedAuthoredInstanceCount());
			uniqueRecognized = Math.addExact(
				uniqueRecognized,
				(long) source.getUniqueRecognizedIdentityCount());
			duplicateRecognized = Math.addExact(
				duplicateRecognized,
				(long) source.getDuplicateRecognizedIdentityInstanceCount());
			missingExpected = Math.addExact(
				missingExpected,
				(long) source.getMissingExpectedIdentityCount());
			exactFinal = Math.addExact(
				exactFinal, (long) source.getExactFinalLiveInstanceCount());
			transientAuthored = Math.addExact(
				transientAuthored,
				(long) source.getAuthoredTransientInstanceCount());
			registrationsPresent = Math.addExact(
				registrationsPresent,
				(long) source.getCollisionRegistrationPresentCount());
			registrationsMissing = Math.addExact(
				registrationsMissing,
				(long) source.getCollisionRegistrationMissingCount());
			registrationsMismatched = Math.addExact(
				registrationsMismatched,
				(long) source
					.getCollisionRegistrationConstructorMismatchCount());
			registrationContributions = Math.addExact(
				registrationContributions,
				(long) source.getCollisionRegistrationContributionCount());
			registrationRegions = Math.addExact(
				registrationRegions,
				(long) source
					.getCollisionRegistrationRegionReferenceCount());
		}
		if (runtimeObjects != dynamicObjects + authoredObjects
			|| authoredObjects != recognizedObjects + unrecognizedObjects
			|| recognizedObjects != uniqueRecognized + duplicateRecognized
			|| uniqueRecognized != expectedObjects - missingExpected
			|| recognizedObjects != exactFinal + transientAuthored
			|| authoredObjects != registrationsPresent
				+ registrationsMissing
				+ registrationsMismatched) {
			throw new IllegalArgumentException(
				"Runtime authored-object aggregate arithmetic is inconsistent");
		}
		this.sources = Collections.unmodifiableList(observed);
		this.expectedAuthoredObjectCount = expectedObjects;
		this.observedObjectCount = runtimeObjects;
		this.identitylessDynamicObjectCount = dynamicObjects;
		this.authoredIdentityObjectCount = authoredObjects;
		this.recognizedAuthoredInstanceCount = recognizedObjects;
		this.unrecognizedAuthoredInstanceCount = unrecognizedObjects;
		this.uniqueRecognizedIdentityCount = uniqueRecognized;
		this.duplicateRecognizedIdentityInstanceCount = duplicateRecognized;
		this.missingExpectedIdentityCount = missingExpected;
		this.exactFinalLiveInstanceCount = exactFinal;
		this.authoredTransientInstanceCount = transientAuthored;
		this.collisionRegistrationPresentCount = registrationsPresent;
		this.collisionRegistrationMissingCount = registrationsMissing;
		this.collisionRegistrationConstructorMismatchCount =
			registrationsMismatched;
		this.collisionRegistrationContributionCount =
			registrationContributions;
		this.collisionRegistrationRegionReferenceCount =
			registrationRegions;
		this.fingerprintSha256 = fingerprint(observed);
	}

	/**
	 * Reduces exact source-ordered temporary captures to a detached summary.
	 * Overflow refuses the whole observation; no instance is silently dropped.
	 */
	public static LayeredPackedRegionRuntimeAuthoredObjectObservation observe(
		final LayeredPackedRegionReloadRecipe recipe,
		final long runtimeObservedAtTick,
		final List<SourceCapture> captures,
		final int maximumObjectInstances) {
		if (maximumObjectInstances < 0
			|| maximumObjectInstances > MAXIMUM_OBJECT_INSTANCES) {
			throw new IllegalArgumentException(
				"Runtime authored-object budget is invalid");
		}
		return new LayeredPackedRegionRuntimeAuthoredObjectObservation(
			Objects.requireNonNull(recipe, "recipe"),
			runtimeObservedAtTick,
			new ArrayList<SourceCapture>(
				Objects.requireNonNull(captures, "captures")),
			maximumObjectInstances);
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
	public List<SourceObservation> getSources() { return sources; }
	public int getSourceCount() { return sources.size(); }
	public long getExpectedAuthoredObjectCount() {
		return expectedAuthoredObjectCount;
	}
	public long getObservedObjectCount() { return observedObjectCount; }
	public long getIdentitylessDynamicObjectCount() {
		return identitylessDynamicObjectCount;
	}
	public long getAuthoredIdentityObjectCount() {
		return authoredIdentityObjectCount;
	}
	public long getRecognizedAuthoredInstanceCount() {
		return recognizedAuthoredInstanceCount;
	}
	public long getUnrecognizedAuthoredInstanceCount() {
		return unrecognizedAuthoredInstanceCount;
	}
	public long getUniqueRecognizedIdentityCount() {
		return uniqueRecognizedIdentityCount;
	}
	public long getDuplicateRecognizedIdentityInstanceCount() {
		return duplicateRecognizedIdentityInstanceCount;
	}
	public long getMissingExpectedIdentityCount() {
		return missingExpectedIdentityCount;
	}
	public long getExactFinalLiveInstanceCount() {
		return exactFinalLiveInstanceCount;
	}
	public long getAuthoredTransientInstanceCount() {
		return authoredTransientInstanceCount;
	}
	public long getCollisionRegistrationPresentCount() {
		return collisionRegistrationPresentCount;
	}
	public long getCollisionRegistrationMissingCount() {
		return collisionRegistrationMissingCount;
	}
	public long getCollisionRegistrationConstructorMismatchCount() {
		return collisionRegistrationConstructorMismatchCount;
	}
	public long getCollisionRegistrationContributionCount() {
		return collisionRegistrationContributionCount;
	}
	public long getCollisionRegistrationRegionReferenceCount() {
		return collisionRegistrationRegionReferenceCount;
	}
	public String getFingerprintSha256() { return fingerprintSha256; }
	public boolean areAllObjectBoundariesHeldDuringCapture() { return true; }
	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedSummaryOnly() { return true; }
	public boolean isSharedCollisionTileComparisonPerformed() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isSourceAbsencePerformed() { return false; }
	public boolean isSourceReconstructionPerformed() { return false; }
	public boolean isRuntimeMutationAuthorized() { return false; }
	public boolean isRuntimeMutationPerformed() { return false; }
	public boolean isRuntimeCacheInvalidated() { return false; }
	public boolean isRegionRegistryMutated() { return false; }
	public boolean isResidencyMirrorMutated() { return false; }
	public boolean isVisibilityCacheMutated() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isVisibilityReleased() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	/** Temporary detached capture for one exact resident source. */
	public static final class SourceCapture {
		private final int packedRegionX;
		private final int packedRegionY;
		private final List<ObjectSnapshot> objects;
		private final boolean objectBoundaryHeldDuringCapture;

		private SourceCapture(
			final int packedRegionX,
			final int packedRegionY,
			final List<ObjectSnapshot> objects,
			final boolean objectBoundaryHeldDuringCapture) {
			if (packedRegionX < 0 || packedRegionY < 0
				|| !objectBoundaryHeldDuringCapture) {
				throw new IllegalArgumentException(
					"Runtime authored-object source capture is invalid");
			}
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			List<ObjectSnapshot> copied = new ArrayList<ObjectSnapshot>(
				Objects.requireNonNull(objects, "objects"));
			if (copied.contains(null)) {
				throw new NullPointerException("objectSnapshot");
			}
			for (ObjectSnapshot object : copied) {
				if (Math.floorDiv(
						object.getX(), Constants.REGION_SIZE)
						!= packedRegionX
					|| Math.floorDiv(
						object.getY(), Constants.REGION_SIZE)
						!= packedRegionY) {
					throw new IllegalArgumentException(
						"Runtime object anchor escaped its captured source");
				}
			}
			Collections.sort(copied, OBJECT_ORDER);
			this.objects = Collections.unmodifiableList(copied);
			this.objectBoundaryHeldDuringCapture = true;
		}

		static SourceCapture capture(
			final int packedRegionX,
			final int packedRegionY,
			final List<ObjectSnapshot> objects,
			final boolean objectBoundaryHeldDuringCapture) {
			return new SourceCapture(
				packedRegionX, packedRegionY, objects,
				objectBoundaryHeldDuringCapture);
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		List<ObjectSnapshot> getObjects() { return objects; }
		public int getObjectCount() { return objects.size(); }
		public boolean isObjectBoundaryHeldDuringCapture() {
			return objectBoundaryHeldDuringCapture;
		}
	}

	/** Temporary detached constructor, identity, and registration copy. */
	static final class ObjectSnapshot {
		private final int objectId;
		private final int permanentObjectId;
		private final int x;
		private final int y;
		private final int direction;
		private final int type;
		private final String owner;
		private final int runtimeAttributeCount;
		private final LayeredAuthoredPlacementIdentity identity;
		private final RegistrationSnapshot registration;

		private ObjectSnapshot(
			final int objectId,
			final int permanentObjectId,
			final int x,
			final int y,
			final int direction,
			final int type,
			final String owner,
			final int runtimeAttributeCount,
			final LayeredAuthoredPlacementIdentity identity,
			final RegistrationSnapshot registration) {
			if (objectId < 0 || permanentObjectId < 0 || x < 0 || y < 0
				|| direction < 0 || direction > 7
				|| (type != 0 && type != 1)
				|| runtimeAttributeCount < 0) {
				throw new IllegalArgumentException(
					"Runtime authored-object constructor copy is invalid");
			}
			this.objectId = objectId;
			this.permanentObjectId = permanentObjectId;
			this.x = x;
			this.y = y;
			this.direction = direction;
			this.type = type;
			this.owner = owner;
			this.runtimeAttributeCount = runtimeAttributeCount;
			this.identity = identity == null ? null
				: new LayeredAuthoredPlacementIdentity(
					identity.getGeneration(), identity.getPackedRegionX(),
					identity.getPackedRegionY(), identity.getSourceOrdinal(),
					identity.getConstructionKind());
			this.registration = registration;
		}

		static ObjectSnapshot capture(final GameObject object) {
			GameObject checked = Objects.requireNonNull(object, "object");
			return new ObjectSnapshot(
				checked.getID(), checked.getLoc().getPermId(),
				checked.getX(), checked.getY(), checked.getDirection(),
				checked.getType(), checked.getOwner(),
				checked.getRuntimeAttributeCount(),
				checked.getAuthoredPlacementIdentity(),
				RegistrationSnapshot.capture(
					checked.getCollisionRegistrationState()));
		}

		static ObjectSnapshot declare(
			final int objectId,
			final int permanentObjectId,
			final int x,
			final int y,
			final int direction,
			final int type,
			final String owner,
			final int runtimeAttributeCount,
			final LayeredAuthoredPlacementIdentity identity,
			final RegistrationSnapshot registration) {
			return new ObjectSnapshot(
				objectId, permanentObjectId, x, y, direction, type, owner,
				runtimeAttributeCount, identity, registration);
		}

		int getObjectId() { return objectId; }
		int getPermanentObjectId() { return permanentObjectId; }
		int getX() { return x; }
		int getY() { return y; }
		int getDirection() { return direction; }
		int getType() { return type; }
		String getOwner() { return owner; }
		int getRuntimeAttributeCount() { return runtimeAttributeCount; }
		LayeredAuthoredPlacementIdentity getIdentity() { return identity; }
		RegistrationSnapshot getRegistration() { return registration; }

		boolean matchesFinalLive(
			final ReconstructionPlacement expected) {
			AuthoredPlacement placement = expected.getPlacement();
			return objectId == placement.getConstructedEntityId()
				&& permanentObjectId == placement.getPermanentObjectId()
				&& x == placement.getPackedX()
				&& y == placement.getPackedY()
				&& direction == placement.getDirection()
				&& type == placement.getObjectType()
				&& Objects.equals(owner, placement.getObjectOwner())
				&& runtimeAttributeCount == 0;
		}
	}

	/** Temporary detached primitive collision-registration copy. */
	static final class RegistrationSnapshot {
		private final int objectId;
		private final int permanentObjectId;
		private final int x;
		private final int y;
		private final int direction;
		private final int type;
		private final List<ContributionSnapshot> contributions;
		private final List<RegionSnapshot> requiredRegions;

		private RegistrationSnapshot(
			final int objectId,
			final int permanentObjectId,
			final int x,
			final int y,
			final int direction,
			final int type,
			final List<ContributionSnapshot> contributions,
			final List<RegionSnapshot> requiredRegions) {
			if (objectId < 0 || permanentObjectId < 0 || x < 0 || y < 0
				|| direction < 0 || direction > 7
				|| (type != 0 && type != 1)
				|| contributions == null || contributions.contains(null)
				|| requiredRegions == null || requiredRegions.isEmpty()
				|| requiredRegions.contains(null)) {
				throw new IllegalArgumentException(
					"Runtime collision registration copy is invalid");
			}
			this.objectId = objectId;
			this.permanentObjectId = permanentObjectId;
			this.x = x;
			this.y = y;
			this.direction = direction;
			this.type = type;
			this.contributions = Collections.unmodifiableList(
				new ArrayList<ContributionSnapshot>(contributions));
			this.requiredRegions = Collections.unmodifiableList(
				new ArrayList<RegionSnapshot>(requiredRegions));
		}

		static RegistrationSnapshot capture(
			final GameObjectCollisionRegistrationState state) {
			if (state == null) { return null; }
			List<ContributionSnapshot> contributions =
				new ArrayList<ContributionSnapshot>(
					state.getContributionTileCount());
			for (GameObjectCollisionRegistrationState.CollisionContribution
					contribution : state.getContributions()) {
				contributions.add(new ContributionSnapshot(
					contribution.getX(), contribution.getY(),
					contribution.getBlockingSceneryCount(),
					contribution.getDynamicCollisionCounts(),
					contribution.getDynamicProjectileCount()));
			}
			List<RegionSnapshot> regions =
				new ArrayList<RegionSnapshot>(
					state.getRequiredRegionCount());
			for (GameObjectCollisionRegistrationState.PackedRegionCoordinate
					region : state.getRequiredRegions()) {
				regions.add(new RegionSnapshot(
					region.getRegionX(), region.getRegionY()));
			}
			return new RegistrationSnapshot(
				state.getObjectId(), state.getPermanentObjectId(),
				state.getX(), state.getY(), state.getDirection(),
				state.getType(), contributions, regions);
		}

		static RegistrationSnapshot declare(
			final int objectId,
			final int permanentObjectId,
			final int x,
			final int y,
			final int direction,
			final int type,
			final List<ContributionSnapshot> contributions,
			final List<RegionSnapshot> requiredRegions) {
			return new RegistrationSnapshot(
				objectId, permanentObjectId, x, y, direction, type,
				contributions, requiredRegions);
		}

		boolean matchesConstructor(final ObjectSnapshot object) {
			return object != null
				&& objectId == object.getObjectId()
				&& permanentObjectId == object.getPermanentObjectId()
				&& x == object.getX() && y == object.getY()
				&& direction == object.getDirection()
				&& type == object.getType();
		}

		int getContributionCount() { return contributions.size(); }
		int getRequiredRegionCount() { return requiredRegions.size(); }

		void updateFingerprint(final MessageDigest digest) {
			updateInt(digest, objectId);
			updateInt(digest, permanentObjectId);
			updateInt(digest, x);
			updateInt(digest, y);
			updateInt(digest, direction);
			updateInt(digest, type);
			updateInt(digest, contributions.size());
			for (ContributionSnapshot contribution : contributions) {
				contribution.updateFingerprint(digest);
			}
			updateInt(digest, requiredRegions.size());
			for (RegionSnapshot region : requiredRegions) {
				region.updateFingerprint(digest);
			}
		}
	}

	static final class ContributionSnapshot {
		private final int x;
		private final int y;
		private final int blockingSceneryCount;
		private final int[] dynamicCollisionCounts;
		private final int dynamicProjectileCount;

		ContributionSnapshot(
			final int x,
			final int y,
			final int blockingSceneryCount,
			final int[] dynamicCollisionCounts,
			final int dynamicProjectileCount) {
			if (x < 0 || y < 0 || blockingSceneryCount < 0
				|| dynamicCollisionCounts == null
				|| dynamicCollisionCounts.length != 6
				|| dynamicProjectileCount < 0) {
				throw new IllegalArgumentException(
					"Runtime collision contribution copy is invalid");
			}
			this.x = x;
			this.y = y;
			this.blockingSceneryCount = blockingSceneryCount;
			this.dynamicCollisionCounts = dynamicCollisionCounts.clone();
			for (int count : this.dynamicCollisionCounts) {
				if (count < 0) {
					throw new IllegalArgumentException(
						"Runtime collision contribution is negative");
				}
			}
			this.dynamicProjectileCount = dynamicProjectileCount;
		}

		private void updateFingerprint(final MessageDigest digest) {
			updateInt(digest, x);
			updateInt(digest, y);
			updateInt(digest, blockingSceneryCount);
			for (int count : dynamicCollisionCounts) {
				updateInt(digest, count);
			}
			updateInt(digest, dynamicProjectileCount);
		}
	}

	static final class RegionSnapshot {
		private final int regionX;
		private final int regionY;

		RegionSnapshot(final int regionX, final int regionY) {
			if (regionX < 0 || regionY < 0) {
				throw new IllegalArgumentException(
					"Runtime registration Region copy is invalid");
			}
			this.regionX = regionX;
			this.regionY = regionY;
		}

		private void updateFingerprint(final MessageDigest digest) {
			updateInt(digest, regionX);
			updateInt(digest, regionY);
		}
	}

	/** Count/fingerprint-only comparison for one exact selected source. */
	public static final class SourceObservation {
		private final int sourceOrdinal;
		private final int packedRegionX;
		private final int packedRegionY;
		private final int expectedAuthoredObjectCount;
		private final int observedObjectCount;
		private final int identitylessDynamicObjectCount;
		private final int authoredIdentityObjectCount;
		private final int recognizedAuthoredInstanceCount;
		private final int unrecognizedAuthoredInstanceCount;
		private final int staleGenerationInstanceCount;
		private final int nonObjectIdentityInstanceCount;
		private final int unknownRecipeIdentityInstanceCount;
		private final int uniqueRecognizedIdentityCount;
		private final int duplicateRecognizedIdentityInstanceCount;
		private final int missingExpectedIdentityCount;
		private final int exactFinalLiveInstanceCount;
		private final int authoredTransientInstanceCount;
		private final int collisionRegistrationPresentCount;
		private final int collisionRegistrationMissingCount;
		private final int collisionRegistrationConstructorMismatchCount;
		private final int collisionRegistrationContributionCount;
		private final int collisionRegistrationRegionReferenceCount;
		private final String collisionRegistrationFingerprintSha256;
		private final String fingerprintSha256;

		private SourceObservation(
			final LayeredPackedRegionReloadRecipe.SourceRecipe recipe,
			final SourceCapture capture,
			final int sourceOrdinal,
			final long authoredGeneration) {
			this.sourceOrdinal = sourceOrdinal;
			this.packedRegionX = recipe.getPackedRegionX();
			this.packedRegionY = recipe.getPackedRegionY();
			Map<LayeredAuthoredPlacementIdentity, ReconstructionPlacement>
				expected = expectedObjects(recipe);
			this.expectedAuthoredObjectCount = expected.size();
			this.observedObjectCount = capture.getObjects().size();

			Map<LayeredAuthoredPlacementIdentity, Integer> recognizedCounts =
				new LinkedHashMap<LayeredAuthoredPlacementIdentity, Integer>();
			List<ObjectSnapshot> recognized =
				new ArrayList<ObjectSnapshot>();
			int dynamic = 0;
			int unrecognized = 0;
			int stale = 0;
			int nonObject = 0;
			int unknown = 0;
			int exact = 0;
			int transientCount = 0;
			int registrationPresent = 0;
			int registrationMissing = 0;
			int registrationMismatch = 0;
			int contributionCount = 0;
			int regionReferenceCount = 0;
			for (ObjectSnapshot object : capture.getObjects()) {
				LayeredAuthoredPlacementIdentity identity =
					object.getIdentity();
				if (identity == null) {
					dynamic = Math.incrementExact(dynamic);
					continue;
				}
				RegistrationSnapshot registration = object.getRegistration();
				if (registration == null) {
					registrationMissing = Math.incrementExact(
						registrationMissing);
				} else if (!registration.matchesConstructor(object)) {
					registrationMismatch = Math.incrementExact(
						registrationMismatch);
				} else {
					registrationPresent = Math.incrementExact(
						registrationPresent);
					contributionCount = Math.addExact(
						contributionCount,
						registration.getContributionCount());
					regionReferenceCount = Math.addExact(
						regionReferenceCount,
						registration.getRequiredRegionCount());
				}
				IdentityStatus status = resolveIdentity(
					expected, identity, authoredGeneration);
				if (status != IdentityStatus.RECOGNIZED) {
					unrecognized = Math.incrementExact(unrecognized);
					switch (status) {
						case STALE_GENERATION:
							stale = Math.incrementExact(stale);
							break;
						case NON_OBJECT_IDENTITY:
							nonObject = Math.incrementExact(nonObject);
							break;
						case UNKNOWN_RECIPE_IDENTITY:
							unknown = Math.incrementExact(unknown);
							break;
						default:
							throw new IllegalArgumentException(
								"Unsupported authored-object identity result");
					}
					continue;
				}
				recognized.add(object);
				Integer prior = recognizedCounts.get(identity);
				recognizedCounts.put(identity, Integer.valueOf(
					prior == null ? 1
						: Math.incrementExact(prior.intValue())));
				if (object.matchesFinalLive(expected.get(identity))) {
					exact = Math.incrementExact(exact);
				} else {
					transientCount = Math.incrementExact(transientCount);
				}
			}
			int duplicate = recognized.size() - recognizedCounts.size();
			int missing = expected.size() - recognizedCounts.size();
			if (missing < 0
				|| unrecognized != stale + nonObject + unknown
				|| capture.getObjects().size()
					!= dynamic + recognized.size() + unrecognized
				|| recognized.size() != exact + transientCount) {
				throw new IllegalArgumentException(
					"Runtime authored-object source arithmetic is inconsistent");
			}
			this.identitylessDynamicObjectCount = dynamic;
			this.authoredIdentityObjectCount =
				capture.getObjects().size() - dynamic;
			this.recognizedAuthoredInstanceCount = recognized.size();
			this.unrecognizedAuthoredInstanceCount = unrecognized;
			this.staleGenerationInstanceCount = stale;
			this.nonObjectIdentityInstanceCount = nonObject;
			this.unknownRecipeIdentityInstanceCount = unknown;
			this.uniqueRecognizedIdentityCount = recognizedCounts.size();
			this.duplicateRecognizedIdentityInstanceCount = duplicate;
			this.missingExpectedIdentityCount = missing;
			this.exactFinalLiveInstanceCount = exact;
			this.authoredTransientInstanceCount = transientCount;
			this.collisionRegistrationPresentCount = registrationPresent;
			this.collisionRegistrationMissingCount = registrationMissing;
			this.collisionRegistrationConstructorMismatchCount =
				registrationMismatch;
			this.collisionRegistrationContributionCount =
				contributionCount;
			this.collisionRegistrationRegionReferenceCount =
				regionReferenceCount;
			this.collisionRegistrationFingerprintSha256 =
				fingerprintRegistrations(recognized);
			this.fingerprintSha256 = fingerprintSource(this);
		}

		public int getSourceOrdinal() { return sourceOrdinal; }
		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getExpectedAuthoredObjectCount() {
			return expectedAuthoredObjectCount;
		}
		public int getObservedObjectCount() { return observedObjectCount; }
		public int getIdentitylessDynamicObjectCount() {
			return identitylessDynamicObjectCount;
		}
		public int getAuthoredIdentityObjectCount() {
			return authoredIdentityObjectCount;
		}
		public int getRecognizedAuthoredInstanceCount() {
			return recognizedAuthoredInstanceCount;
		}
		public int getUnrecognizedAuthoredInstanceCount() {
			return unrecognizedAuthoredInstanceCount;
		}
		public int getStaleGenerationInstanceCount() {
			return staleGenerationInstanceCount;
		}
		public int getNonObjectIdentityInstanceCount() {
			return nonObjectIdentityInstanceCount;
		}
		public int getUnknownRecipeIdentityInstanceCount() {
			return unknownRecipeIdentityInstanceCount;
		}
		public int getUniqueRecognizedIdentityCount() {
			return uniqueRecognizedIdentityCount;
		}
		public int getDuplicateRecognizedIdentityInstanceCount() {
			return duplicateRecognizedIdentityInstanceCount;
		}
		public int getMissingExpectedIdentityCount() {
			return missingExpectedIdentityCount;
		}
		public int getExactFinalLiveInstanceCount() {
			return exactFinalLiveInstanceCount;
		}
		public int getAuthoredTransientInstanceCount() {
			return authoredTransientInstanceCount;
		}
		public int getCollisionRegistrationPresentCount() {
			return collisionRegistrationPresentCount;
		}
		public int getCollisionRegistrationMissingCount() {
			return collisionRegistrationMissingCount;
		}
		public int getCollisionRegistrationConstructorMismatchCount() {
			return collisionRegistrationConstructorMismatchCount;
		}
		public int getCollisionRegistrationContributionCount() {
			return collisionRegistrationContributionCount;
		}
		public int getCollisionRegistrationRegionReferenceCount() {
			return collisionRegistrationRegionReferenceCount;
		}
		public String getCollisionRegistrationFingerprintSha256() {
			return collisionRegistrationFingerprintSha256;
		}
		public String getFingerprintSha256() { return fingerprintSha256; }
		public boolean isFinalLiveAuthoredSetPresent() {
			return uniqueRecognizedIdentityCount
					== expectedAuthoredObjectCount
				&& duplicateRecognizedIdentityInstanceCount == 0
				&& unrecognizedAuthoredInstanceCount == 0
				&& exactFinalLiveInstanceCount
					== expectedAuthoredObjectCount;
		}
		public boolean areRecognizedRegistrationsConstructorMatched() {
			return collisionRegistrationPresentCount
					== authoredIdentityObjectCount
				&& collisionRegistrationMissingCount == 0
				&& collisionRegistrationConstructorMismatchCount == 0;
		}
		public boolean isObjectBoundaryHeldDuringCapture() { return true; }
	}

	public enum IdentityStatus {
		RECOGNIZED,
		STALE_GENERATION,
		NON_OBJECT_IDENTITY,
		UNKNOWN_RECIPE_IDENTITY
	}

	private static Map<LayeredAuthoredPlacementIdentity, ReconstructionPlacement>
		expectedObjects(
			final LayeredPackedRegionReloadRecipe.SourceRecipe recipe) {
		Map<LayeredAuthoredPlacementIdentity, ReconstructionPlacement>
			expected = new LinkedHashMap<
				LayeredAuthoredPlacementIdentity, ReconstructionPlacement>();
		for (ReconstructionPlacement placement
				: recipe.getAuthoredPlacements()) {
			if (!isObjectKind(placement.getKind())) { continue; }
			if (expected.put(placement.getIdentity(), placement) != null) {
				throw new IllegalArgumentException(
					"Runtime recipe contains a duplicate object identity");
			}
		}
		return expected;
	}

	private static IdentityStatus resolveIdentity(
		final Map<LayeredAuthoredPlacementIdentity, ReconstructionPlacement>
			expected,
		final LayeredAuthoredPlacementIdentity identity,
		final long authoredGeneration) {
		if (identity.getGeneration()
			!= authoredGeneration) {
			return IdentityStatus.STALE_GENERATION;
		}
		if (!isObjectKind(identity.getConstructionKind())) {
			return IdentityStatus.NON_OBJECT_IDENTITY;
		}
		return expected.containsKey(identity)
			? IdentityStatus.RECOGNIZED
			: IdentityStatus.UNKNOWN_RECIPE_IDENTITY;
	}

	private static boolean isObjectKind(final ConstructionKind kind) {
		return kind == ConstructionKind.SCENERY
			|| kind == ConstructionKind.BOUNDARY
			|| kind == ConstructionKind.HARVESTING_SCENERY;
	}

	private static String fingerprintRegistrations(
		final List<ObjectSnapshot> recognized) {
		List<ObjectSnapshot> ordered = new ArrayList<ObjectSnapshot>(recognized);
		Collections.sort(ordered, IDENTITY_ORDER);
		MessageDigest digest = sha256();
		updateInt(digest, ordered.size());
		for (ObjectSnapshot object : ordered) {
			RegistrationSnapshot registration = object.getRegistration();
			if (registration == null) {
				// A valid complete sequence never contains this sentinel, so
				// exact final-live sequences remain byte-compatible with the
				// disposable transactional registration fingerprint.
				updateInt(digest, -1);
				continue;
			}
			registration.updateFingerprint(digest);
		}
		return hex(digest.digest());
	}

	private static String fingerprintSource(final SourceObservation source) {
		MessageDigest digest = sha256();
		updateInt(digest, source.getSourceOrdinal());
		updateInt(digest, source.getPackedRegionX());
		updateInt(digest, source.getPackedRegionY());
		updateInt(digest, source.getExpectedAuthoredObjectCount());
		updateInt(digest, source.getObservedObjectCount());
		updateInt(digest, source.getIdentitylessDynamicObjectCount());
		updateInt(digest, source.getRecognizedAuthoredInstanceCount());
		updateInt(digest, source.getUnrecognizedAuthoredInstanceCount());
		updateInt(digest, source.getUniqueRecognizedIdentityCount());
		updateInt(
			digest, source.getDuplicateRecognizedIdentityInstanceCount());
		updateInt(digest, source.getMissingExpectedIdentityCount());
		updateInt(digest, source.getExactFinalLiveInstanceCount());
		updateInt(digest, source.getAuthoredTransientInstanceCount());
		updateInt(digest, source.getCollisionRegistrationPresentCount());
		updateInt(digest, source.getCollisionRegistrationMissingCount());
		updateInt(
			digest,
			source.getCollisionRegistrationConstructorMismatchCount());
		updateString(
			digest, source.getCollisionRegistrationFingerprintSha256());
		return hex(digest.digest());
	}

	private static String fingerprint(
		final List<SourceObservation> sources) {
		MessageDigest digest = sha256();
		updateInt(digest, sources.size());
		for (SourceObservation source : sources) {
			updateString(digest, source.getFingerprintSha256());
		}
		return hex(digest.digest());
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(
				"SHA-256 is unavailable for runtime authored-object observation",
				impossible);
		}
	}

	private static void updateInt(
		final MessageDigest digest,
		final int value) {
		digest.update(ByteBuffer.allocate(4).putInt(value).array());
	}

	private static void updateString(
		final MessageDigest digest,
		final String value) {
		byte[] encoded = Objects.requireNonNull(value, "fingerprint")
			.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		updateInt(digest, encoded.length);
		digest.update(encoded);
	}

	private static String hex(final byte[] bytes) {
		StringBuilder result = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) {
			result.append(String.format("%02x", value & 0xff));
		}
		return result.toString();
	}

	private static final Comparator<ObjectSnapshot> OBJECT_ORDER =
		new Comparator<ObjectSnapshot>() {
			@Override
			public int compare(
				final ObjectSnapshot left,
				final ObjectSnapshot right) {
				int compared = Integer.compare(left.getY(), right.getY());
				if (compared != 0) { return compared; }
				compared = Integer.compare(left.getX(), right.getX());
				if (compared != 0) { return compared; }
				compared = Integer.compare(left.getType(), right.getType());
				if (compared != 0) { return compared; }
				compared = Integer.compare(
					left.getDirection(), right.getDirection());
				if (compared != 0) { return compared; }
				compared = Integer.compare(
					left.getObjectId(), right.getObjectId());
				if (compared != 0) { return compared; }
				LayeredAuthoredPlacementIdentity leftIdentity =
					left.getIdentity();
				LayeredAuthoredPlacementIdentity rightIdentity =
					right.getIdentity();
				if (leftIdentity == null || rightIdentity == null) {
					return leftIdentity == rightIdentity ? 0
						: leftIdentity == null ? -1 : 1;
				}
				return IDENTITY_COMPARATOR.compare(
					leftIdentity, rightIdentity);
			}
		};

	private static final Comparator<ObjectSnapshot> IDENTITY_ORDER =
		new Comparator<ObjectSnapshot>() {
			@Override
			public int compare(
				final ObjectSnapshot left,
				final ObjectSnapshot right) {
				LayeredAuthoredPlacementIdentity leftIdentity =
					left.getIdentity();
				LayeredAuthoredPlacementIdentity rightIdentity =
					right.getIdentity();
				if (leftIdentity == null || rightIdentity == null) {
					if (leftIdentity != rightIdentity) {
						return leftIdentity == null ? -1 : 1;
					}
				} else {
					int compared = IDENTITY_COMPARATOR.compare(
						leftIdentity, rightIdentity);
					if (compared != 0) { return compared; }
				}
				return OBJECT_ORDER.compare(left, right);
			}
		};

	private static final Comparator<LayeredAuthoredPlacementIdentity>
		IDENTITY_COMPARATOR =
			new Comparator<LayeredAuthoredPlacementIdentity>() {
				@Override
				public int compare(
					final LayeredAuthoredPlacementIdentity left,
					final LayeredAuthoredPlacementIdentity right) {
					int compared = Long.compare(
						left.getGeneration(), right.getGeneration());
					if (compared != 0) { return compared; }
					compared = Integer.compare(
						left.getPackedRegionX(),
						right.getPackedRegionX());
					if (compared != 0) { return compared; }
					compared = Integer.compare(
						left.getPackedRegionY(),
						right.getPackedRegionY());
					if (compared != 0) { return compared; }
					compared = Integer.compare(
						left.getSourceOrdinal(),
						right.getSourceOrdinal());
					return compared != 0 ? compared
						: left.getConstructionKind().compareTo(
							right.getConstructionKind());
				}
			};
}
