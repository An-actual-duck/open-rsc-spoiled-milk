package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;
import com.openrsc.server.model.world.region
	.LayeredPackedRegionAuthoredCollisionFootprintPlan
		.AuthoredObjectCollisionDefinition;
import com.openrsc.server.model.world.region
	.LayeredPackedRegionAuthoredReplayPlan.AuthoredReplayPlacement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Detached read-only capture of the active definition scalars required by one
 * isolated-membership-verified authored replay.
 *
 * <p>The lookup operation and returned definition-table objects are reduced
 * immediately. Only collision-plan input values survive.</p>
 */
public final class LayeredPackedRegionAuthoredCollisionDefinitionCapture {
	private final long generation;
	private final long requirementsObservedAtTick;
	private final long observedAtTick;
	private final long residencyMirrorVersion;
	private final long authoredGeneration;
	private final int sourceOrdinal;
	private final int packedRegionX;
	private final int packedRegionY;
	private final String authoredReplayFingerprintSha256;
	private final List<AuthoredObjectCollisionDefinition> definitions;
	private final int sceneryDefinitionCount;
	private final int boundaryDefinitionCount;
	private final int harvestingSceneryDefinitionCount;
	private final int specialCollisionlessObjectCount;
	private final int definitionLookupCount;
	private final String fingerprintSha256;

	private LayeredPackedRegionAuthoredCollisionDefinitionCapture(
		final LayeredPackedRegionAuthoredReplayPlan replayPlan,
		final LayeredPackedRegionIsolatedAuthoredObjectVerification
			membershipVerification,
		final DefinitionLookup definitionLookup) {
		LayeredPackedRegionAuthoredReplayPlan replay =
			Objects.requireNonNull(replayPlan, "replayPlan");
		LayeredPackedRegionIsolatedAuthoredObjectVerification membership =
			Objects.requireNonNull(
				membershipVerification, "membershipVerification");
		DefinitionLookup lookup = Objects.requireNonNull(
			definitionLookup, "definitionLookup");
		requireAligned(replay, membership);

		this.generation = replay.getGeneration();
		this.requirementsObservedAtTick =
			replay.getRequirementsObservedAtTick();
		this.observedAtTick = replay.getObservedAtTick();
		this.residencyMirrorVersion = replay.getResidencyMirrorVersion();
		this.authoredGeneration = replay.getAuthoredGeneration();
		this.sourceOrdinal = replay.getSelectedSourceOrdinal();
		this.packedRegionX = replay.getPackedRegionX();
		this.packedRegionY = replay.getPackedRegionY();
		this.authoredReplayFingerprintSha256 =
			replay.getFingerprintSha256();

		List<AuthoredObjectCollisionDefinition> copied =
			new ArrayList<AuthoredObjectCollisionDefinition>(
				replay.getAuthoredObjectPlacementCount());
		int scenery = 0;
		int boundaries = 0;
		int harvesting = 0;
		int special = 0;
		int lookups = 0;
		for (AuthoredReplayPlacement placement : replay.getPlacements()) {
			if (!isObjectFamily(placement.getConstructionKind())) {
				continue;
			}
			if (placement.getConstructedEntityId() == 1147
				&& placement.getObjectType() == 0) {
				copied.add(
					AuthoredObjectCollisionDefinition
						.specialCollisionlessScenery(
							placement.getAuthoredSourceOrdinal()));
				special = Math.incrementExact(special);
			} else {
				DefinitionSnapshot snapshot =
					placement.getObjectType() == 0
						? lookup.lookupScenery(
							placement.getConstructedEntityId())
						: lookup.lookupBoundary(
							placement.getConstructedEntityId());
				lookups = Math.incrementExact(lookups);
				if (snapshot == null
					|| snapshot.getObjectType()
						!= placement.getObjectType()) {
					throw new IllegalArgumentException(
						"Active authored object definition is unavailable");
				}
				copied.add(snapshot.toDefinitionInput(
					placement.getAuthoredSourceOrdinal(),
					placement.getConstructedEntityId()));
			}
			switch (placement.getConstructionKind()) {
				case SCENERY:
					scenery = Math.incrementExact(scenery);
					break;
				case BOUNDARY:
					boundaries = Math.incrementExact(boundaries);
					break;
				case HARVESTING_SCENERY:
					harvesting = Math.incrementExact(harvesting);
					break;
				default:
					throw new IllegalArgumentException(
						"Non-object family entered definition capture");
			}
		}
		if (copied.size() != replay.getAuthoredObjectPlacementCount()
			|| scenery != replay.getSceneryPlacementCount()
			|| boundaries != replay.getBoundaryPlacementCount()
			|| harvesting
				!= replay.getHarvestingSceneryPlacementCount()
			|| lookups + special != copied.size()) {
			throw new IllegalArgumentException(
				"Captured authored definitions do not reconcile");
		}
		this.definitions = Collections.unmodifiableList(copied);
		this.sceneryDefinitionCount = scenery;
		this.boundaryDefinitionCount = boundaries;
		this.harvestingSceneryDefinitionCount = harvesting;
		this.specialCollisionlessObjectCount = special;
		this.definitionLookupCount = lookups;
		this.fingerprintSha256 = fingerprint(copied);
	}

	static LayeredPackedRegionAuthoredCollisionDefinitionCapture capture(
		final LayeredPackedRegionAuthoredReplayPlan replayPlan,
		final LayeredPackedRegionIsolatedAuthoredObjectVerification
			membershipVerification,
		final DefinitionLookup definitionLookup) {
		return new LayeredPackedRegionAuthoredCollisionDefinitionCapture(
			replayPlan, membershipVerification, definitionLookup);
	}

	private static void requireAligned(
		final LayeredPackedRegionAuthoredReplayPlan replay,
		final LayeredPackedRegionIsolatedAuthoredObjectVerification
			membership) {
		if (membership.getGeneration() != replay.getGeneration()
			|| membership.getRequirementsObservedAtTick()
				!= replay.getRequirementsObservedAtTick()
			|| membership.getObservedAtTick() != replay.getObservedAtTick()
			|| membership.getResidencyMirrorVersion()
				!= replay.getResidencyMirrorVersion()
			|| membership.getAuthoredGeneration()
				!= replay.getAuthoredGeneration()
			|| membership.getSourceOrdinal()
				!= replay.getSelectedSourceOrdinal()
			|| membership.getPackedRegionX() != replay.getPackedRegionX()
			|| membership.getPackedRegionY() != replay.getPackedRegionY()
			|| membership.getConstructedObjectCount()
				!= replay.getAuthoredObjectPlacementCount()
			|| !membership.getAuthoredReplayFingerprintSha256().equals(
				replay.getFingerprintSha256())
			|| !membership.isVerificationOnly()
			|| !membership.isAuthoredSceneryMembershipApplied()
			|| !membership.isExactObjectMembershipMatchedAfterReplay()
			|| membership.isCollisionDerived()
			|| membership.isCollisionRegistrationAttached()
			|| membership.isRuntimeHandleRetained()
			|| membership.isRuntimeSourceMutated()
			|| membership.isRegionRegistryMutated()
			|| membership.isResidencyMirrorMutated()
			|| membership.isVisibilityCacheMutated()
			|| membership.isArrivalGate()
			|| membership.isVisibilityReleased()
			|| membership.isLifecycleAuthority()) {
			throw new IllegalArgumentException(
				"Definition capture does not match isolated membership");
		}
	}

	private static boolean isObjectFamily(final ConstructionKind kind) {
		return kind == ConstructionKind.SCENERY
			|| kind == ConstructionKind.BOUNDARY
			|| kind == ConstructionKind.HARVESTING_SCENERY;
	}

	private static String fingerprint(
		final List<AuthoredObjectCollisionDefinition> definitions) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (AuthoredObjectCollisionDefinition definition : definitions) {
				updateInt(digest, definition.getAuthoredSourceOrdinal());
				updateInt(digest, definition.getConstructedEntityId());
				updateInt(digest, definition.getObjectType());
				digest.update((byte) (
					definition.isDefinitionAvailable() ? 1 : 0));
				updateInt(digest, definition.getCollisionType());
				updateInt(digest, definition.getWidth());
				updateInt(digest, definition.getHeight());
				updateString(digest, definition.getName());
			}
			StringBuilder result = new StringBuilder(64);
			for (byte value : digest.digest()) {
				result.append(String.format("%02x", value & 0xff));
			}
			return result.toString();
		} catch (NoSuchAlgorithmException unavailable) {
			throw new IllegalStateException(
				"SHA-256 is unavailable for definition capture identity",
				unavailable);
		}
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
	public String getAuthoredReplayFingerprintSha256() {
		return authoredReplayFingerprintSha256;
	}
	public List<AuthoredObjectCollisionDefinition> getDefinitions() {
		return definitions;
	}
	public int getDefinitionCount() { return definitions.size(); }
	public int getSceneryDefinitionCount() {
		return sceneryDefinitionCount;
	}
	public int getBoundaryDefinitionCount() {
		return boundaryDefinitionCount;
	}
	public int getHarvestingSceneryDefinitionCount() {
		return harvestingSceneryDefinitionCount;
	}
	public int getSpecialCollisionlessObjectCount() {
		return specialCollisionlessObjectCount;
	}
	public int getDefinitionLookupCount() {
		return definitionLookupCount;
	}
	public String getFingerprintSha256() { return fingerprintSha256; }

	public boolean isReadOnlyDefinitionCapture() { return true; }
	public boolean isDefinitionSequenceMatched() { return true; }
	public boolean isDefinitionLookupRetained() { return false; }
	public boolean isDefinitionTableObjectRetained() { return false; }
	public boolean isRegionLookupPerformed() { return false; }
	public boolean isRegionBoundaryAcquired() { return false; }
	public boolean isCollisionApplied() { return false; }
	public boolean isCollisionRegistrationAttached() { return false; }
	public boolean isRuntimeSourceMutated() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isRegionRegistryMutated() { return false; }
	public boolean isResidencyMirrorMutated() { return false; }
	public boolean isVisibilityCacheMutated() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isVisibilityReleased() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	interface DefinitionLookup {
		DefinitionSnapshot lookupScenery(int objectId);
		DefinitionSnapshot lookupBoundary(int objectId);
	}

	/** Immediate scalar reduction of one definition-table entry. */
	static final class DefinitionSnapshot {
		private final int objectType;
		private final int collisionType;
		private final int width;
		private final int height;
		private final String name;

		private DefinitionSnapshot(
			final int objectType,
			final int collisionType,
			final int width,
			final int height,
			final String name) {
			if ((objectType != 0 && objectType != 1)
				|| collisionType < 0 || width < 0 || height < 0
				|| name == null) {
				throw new IllegalArgumentException(
					"Definition snapshot is invalid");
			}
			this.objectType = objectType;
			this.collisionType = collisionType;
			this.width = width;
			this.height = height;
			this.name = name;
		}

		static DefinitionSnapshot scenery(
			final int collisionType,
			final int width,
			final int height,
			final String name) {
			return new DefinitionSnapshot(
				0, collisionType, width, height,
				Objects.requireNonNull(name, "name"));
		}

		static DefinitionSnapshot boundary(
			final int doorType,
			final String name) {
			return new DefinitionSnapshot(
				1, doorType, 1, 1, Objects.requireNonNull(name, "name"));
		}

		private AuthoredObjectCollisionDefinition toDefinitionInput(
			final int authoredSourceOrdinal,
			final int constructedEntityId) {
			return objectType == 0
				? AuthoredObjectCollisionDefinition.scenery(
					authoredSourceOrdinal, constructedEntityId,
					collisionType, width, height, name)
				: AuthoredObjectCollisionDefinition.boundary(
					authoredSourceOrdinal, constructedEntityId,
					collisionType, name);
		}

		int getObjectType() { return objectType; }
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

	private static void updateInt(
		final MessageDigest digest,
		final int value) {
		digest.update((byte) (value >>> 24));
		digest.update((byte) (value >>> 16));
		digest.update((byte) (value >>> 8));
		digest.update((byte) value);
	}
}
