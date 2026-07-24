package com.openrsc.server.model.world.region;

import com.openrsc.server.event.rsc
	.GameTickEventRestorationCollisionFootprintPlanner;
import com.openrsc.server.event.rsc
	.GameTickEventRestorationCollisionFootprintPlanner.ConstructorState;
import com.openrsc.server.event.rsc
	.GameTickEventRestorationCollisionFootprintPlanner.Definition;
import com.openrsc.server.event.rsc
	.GameTickEventRestorationCollisionFootprintPlanner.Operation;
import com.openrsc.server.event.rsc
	.GameTickEventRestorationCollisionFootprintPlanner.Result;
import com.openrsc.server.event.rsc
	.GameTickEventRestorationCollisionFootprintPlanner.WorldBounds;
import com.openrsc.server.event.rsc
	.GameTickEventRestorationCollisionTransactionContract
		.PackedRegionCoordinate;
import com.openrsc.server.event.rsc
	.GameTickEventRestorationTransientRollbackSnapshot
		.CollisionContribution;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;
import com.openrsc.server.model.world.region
	.LayeredPackedRegionAuthoredReplayPlan.AuthoredReplayPlacement;

import java.nio.charset.StandardCharsets;
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
 * Exact detached register-collision definitions for the authored objects in
 * one isolated-membership-verified source.
 *
 * <p>Definition inputs are caller-supplied detached scalars. This plan does not
 * capture a runtime definition table, retain an entity, acquire a Region
 * boundary, apply a contribution, attach collision provenance, or publish the
 * disposable Region.</p>
 */
public final class LayeredPackedRegionAuthoredCollisionFootprintPlan {
	private final long generation;
	private final long requirementsObservedAtTick;
	private final long observedAtTick;
	private final long residencyMirrorVersion;
	private final long authoredGeneration;
	private final int sourceOrdinal;
	private final int packedRegionX;
	private final int packedRegionY;
	private final String authoredReplayFingerprintSha256;
	private final List<AuthoredObjectCollisionFootprint> footprints;
	private final List<RequiredPackedRegion> requiredRegions;
	private final int definitionBackedObjectCount;
	private final int specialCollisionlessObjectCount;
	private final int zeroContributionObjectCount;
	private final int crossSourceCollisionObjectCount;
	private final int collisionBeyondAuthoredDependencyObjectCount;
	private final int contributionTileReferenceCount;
	private final int requiredRegionReferenceCount;
	private final String fingerprintSha256;

	private LayeredPackedRegionAuthoredCollisionFootprintPlan(
		final LayeredPackedRegionAuthoredReplayPlan replayPlan,
		final LayeredPackedRegionIsolatedAuthoredObjectVerification
			membershipVerification,
		final List<AuthoredObjectCollisionDefinition> definitionInputs,
		final String[] projectileClipAllowedNames,
		final int worldWidth,
		final int worldHeight) {
		LayeredPackedRegionAuthoredReplayPlan replay =
			Objects.requireNonNull(replayPlan, "replayPlan");
		LayeredPackedRegionIsolatedAuthoredObjectVerification membership =
			Objects.requireNonNull(
				membershipVerification, "membershipVerification");
		List<AuthoredObjectCollisionDefinition> definitions =
			new ArrayList<AuthoredObjectCollisionDefinition>(
				Objects.requireNonNull(
					definitionInputs, "definitionInputs"));
		String[] projectileAllowlist = Objects.requireNonNull(
			projectileClipAllowedNames,
			"projectileClipAllowedNames").clone();
		for (String name : projectileAllowlist) {
			Objects.requireNonNull(name, "projectileClipAllowedNames entry");
		}
		requireAligned(replay, membership);
		if (definitions.size() != replay.getAuthoredObjectPlacementCount()
			|| definitions.contains(null)) {
			throw new IllegalArgumentException(
				"Collision definitions do not cover every authored object");
		}
		WorldBounds bounds = WorldBounds.of(worldWidth, worldHeight);

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

		List<AuthoredObjectCollisionFootprint> planned =
			new ArrayList<AuthoredObjectCollisionFootprint>(
				definitions.size());
		Map<Long, RequiredPackedRegion> uniqueRequiredRegions =
			new LinkedHashMap<Long, RequiredPackedRegion>();
		int definitionBacked = 0;
		int specialCollisionless = 0;
		int zeroContribution = 0;
		int crossSource = 0;
		int beyondAuthoredDependency = 0;
		int contributionReferences = 0;
		int requiredRegionReferences = 0;
		int definitionIndex = 0;
		for (AuthoredReplayPlacement placement : replay.getPlacements()) {
			if (!isObjectFamily(placement.getConstructionKind())) {
				continue;
			}
			AuthoredObjectCollisionDefinition input =
				definitions.get(definitionIndex++);
			input.requireMatches(placement);
			Definition definition =
				input.toPlannerDefinition(projectileAllowlist);
			Result result =
				GameTickEventRestorationCollisionFootprintPlanner.plan(
					Operation.REGISTER,
					ConstructorState.of(
						placement.getConstructedEntityId(),
						placement.getPackedX(), placement.getPackedY(),
						placement.getDirection(),
						placement.getObjectType()),
					definition, false, bounds);
			if (!result.isFootprintAvailable()
				|| result.getOperation() != Operation.REGISTER
				|| result.isLegacySaturatingUnregister()
				|| result.isRuntimeObservationPerformed()
				|| result.isRuntimeBoundaryAcquired()
				|| result.isMutationAuthorized()
				|| result.isMutationPerformed()
				|| result.isRollbackAuthorized()
				|| result.isRollbackPerformed()
				|| result.isExecutableRestoration()
				|| result.isCommitToken()
				|| result.isArrivalGate()
				|| result.isLifecycleAuthority()) {
				throw new IllegalArgumentException(
					"Authored collision footprint refused: "
						+ result.getReason());
			}
			AuthoredObjectCollisionFootprint footprint =
				AuthoredObjectCollisionFootprint.copyOf(
					placement, input, definition, result);
			planned.add(footprint);
			definitionBacked += input.isDefinitionAvailable() ? 1 : 0;
			specialCollisionless += input.isDefinitionAvailable() ? 0 : 1;
			zeroContribution +=
				footprint.isZeroContributionFootprint() ? 1 : 0;
			crossSource += footprint.isCrossSourceCollision() ? 1 : 0;
			beyondAuthoredDependency +=
				footprint.isCollisionBeyondAuthoredDependency() ? 1 : 0;
			contributionReferences = Math.addExact(
				contributionReferences,
				footprint.getContributionTileCount());
			requiredRegionReferences = Math.addExact(
				requiredRegionReferences,
				footprint.getRequiredRegionCount());
			for (RequiredPackedRegion region
					: footprint.getRequiredRegions()) {
				uniqueRequiredRegions.put(
					packedRegionKey(
						region.getPackedRegionX(),
						region.getPackedRegionY()),
					region);
			}
		}
		if (definitionIndex != definitions.size()) {
			throw new IllegalArgumentException(
				"Collision definition order is incomplete");
		}
		List<RequiredPackedRegion> required =
			new ArrayList<RequiredPackedRegion>(
				uniqueRequiredRegions.values());
		Collections.sort(required, RequiredPackedRegion.ORDER);
		this.footprints = Collections.unmodifiableList(planned);
		this.requiredRegions = Collections.unmodifiableList(required);
		this.definitionBackedObjectCount = definitionBacked;
		this.specialCollisionlessObjectCount = specialCollisionless;
		this.zeroContributionObjectCount = zeroContribution;
		this.crossSourceCollisionObjectCount = crossSource;
		this.collisionBeyondAuthoredDependencyObjectCount =
			beyondAuthoredDependency;
		this.contributionTileReferenceCount = contributionReferences;
		this.requiredRegionReferenceCount = requiredRegionReferences;
		this.fingerprintSha256 = fingerprint(planned, required);
	}

	public static LayeredPackedRegionAuthoredCollisionFootprintPlan define(
		final LayeredPackedRegionAuthoredReplayPlan replayPlan,
		final LayeredPackedRegionIsolatedAuthoredObjectVerification
			membershipVerification,
		final List<AuthoredObjectCollisionDefinition> definitionInputs,
		final String[] projectileClipAllowedNames,
		final int worldWidth,
		final int worldHeight) {
		return new LayeredPackedRegionAuthoredCollisionFootprintPlan(
			replayPlan, membershipVerification, definitionInputs,
			projectileClipAllowedNames, worldWidth, worldHeight);
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
			|| !membership.isDisposableRegionConstructed()
			|| !membership.isTerrainAppliedBeforeObjectMembership()
			|| !membership.isTerrainMatchedAfterObjectMembership()
			|| !membership.isAuthoredSceneryMembershipApplied()
			|| !membership.isExactObjectMembershipMatchedAfterReplay()
			|| membership.isNpcMembershipApplied()
			|| membership.isGroundItemMembershipApplied()
			|| membership.isCollisionDerived()
			|| membership.isCollisionRegistrationAttached()
			|| membership.isDynamicCollisionStateChanged()
			|| membership.isRuntimeHandleRetained()
			|| membership.isRuntimeSourceMutated()
			|| membership.isRegionRegistryMutated()
			|| membership.isResidencyMirrorMutated()
			|| membership.isVisibilityCacheMutated()
			|| membership.isArrivalGate()
			|| membership.isVisibilityReleased()
			|| membership.isLifecycleAuthority()) {
			throw new IllegalArgumentException(
				"Collision plan does not match isolated object membership");
		}
	}

	private static boolean isObjectFamily(final ConstructionKind kind) {
		return kind == ConstructionKind.SCENERY
			|| kind == ConstructionKind.BOUNDARY
			|| kind == ConstructionKind.HARVESTING_SCENERY;
	}

	private static long packedRegionKey(final int x, final int y) {
		return ((long) x << 32) ^ (y & 0xffffffffL);
	}

	private static String fingerprint(
		final List<AuthoredObjectCollisionFootprint> footprints,
		final List<RequiredPackedRegion> requiredRegions) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (AuthoredObjectCollisionFootprint footprint : footprints) {
				footprint.updateDigest(digest);
			}
			updateInt(digest, requiredRegions.size());
			for (RequiredPackedRegion region : requiredRegions) {
				updateInt(digest, region.getPackedRegionX());
				updateInt(digest, region.getPackedRegionY());
			}
			StringBuilder result = new StringBuilder(64);
			for (byte value : digest.digest()) {
				result.append(String.format("%02x", value & 0xff));
			}
			return result.toString();
		} catch (NoSuchAlgorithmException unavailable) {
			throw new IllegalStateException(
				"SHA-256 is unavailable for authored collision identity",
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
	public List<AuthoredObjectCollisionFootprint> getFootprints() {
		return footprints;
	}
	public int getObjectFootprintCount() { return footprints.size(); }
	public List<RequiredPackedRegion> getRequiredRegions() {
		return requiredRegions;
	}
	public int getUniqueRequiredRegionCount() {
		return requiredRegions.size();
	}
	public int getDefinitionBackedObjectCount() {
		return definitionBackedObjectCount;
	}
	public int getSpecialCollisionlessObjectCount() {
		return specialCollisionlessObjectCount;
	}
	public int getZeroContributionObjectCount() {
		return zeroContributionObjectCount;
	}
	public int getCrossSourceCollisionObjectCount() {
		return crossSourceCollisionObjectCount;
	}
	public int getCollisionBeyondAuthoredDependencyObjectCount() {
		return collisionBeyondAuthoredDependencyObjectCount;
	}
	public int getContributionTileReferenceCount() {
		return contributionTileReferenceCount;
	}
	public int getRequiredRegionReferenceCount() {
		return requiredRegionReferenceCount;
	}
	public String getFingerprintSha256() { return fingerprintSha256; }

	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedCollisionDefinition() { return true; }
	public boolean isIsolatedMembershipVerificationMatched() { return true; }
	public boolean isDefinitionIdentityMatched() { return true; }
	public boolean isRegisterFootprintDerived() { return true; }
	public boolean isForceFullBlockEnabled() { return false; }
	public boolean isRuntimeDefinitionCapturePerformed() { return false; }
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

	/** One detached runtime-definition input matched by authored source order. */
	public static final class AuthoredObjectCollisionDefinition {
		private static final int SPECIAL_COLLISIONLESS_OBJECT_ID = 1147;
		private static final int NOT_APPLICABLE = -1;

		private final int authoredSourceOrdinal;
		private final int constructedEntityId;
		private final int objectType;
		private final boolean definitionAvailable;
		private final int collisionType;
		private final int width;
		private final int height;
		private final String name;

		private AuthoredObjectCollisionDefinition(
			final int authoredSourceOrdinal,
			final int constructedEntityId,
			final int objectType,
			final boolean definitionAvailable,
			final int collisionType,
			final int width,
			final int height,
			final String name) {
			if (authoredSourceOrdinal <= 0 || constructedEntityId < 0
				|| (objectType != ConstructorState.SCENERY
					&& objectType != ConstructorState.BOUNDARY)
				|| (definitionAvailable
					&& (collisionType < 0 || width < 0 || height < 0
						|| name == null))
				|| (!definitionAvailable
					&& (constructedEntityId
							!= SPECIAL_COLLISIONLESS_OBJECT_ID
						|| objectType != ConstructorState.SCENERY
						|| collisionType != NOT_APPLICABLE
						|| width != NOT_APPLICABLE
						|| height != NOT_APPLICABLE
						|| name != null))) {
				throw new IllegalArgumentException(
					"Authored collision definition is invalid");
			}
			this.authoredSourceOrdinal = authoredSourceOrdinal;
			this.constructedEntityId = constructedEntityId;
			this.objectType = objectType;
			this.definitionAvailable = definitionAvailable;
			this.collisionType = collisionType;
			this.width = width;
			this.height = height;
			this.name = name;
		}

		public static AuthoredObjectCollisionDefinition scenery(
			final int authoredSourceOrdinal,
			final int constructedEntityId,
			final int collisionType,
			final int width,
			final int height,
			final String name) {
			return new AuthoredObjectCollisionDefinition(
				authoredSourceOrdinal, constructedEntityId,
				ConstructorState.SCENERY, true, collisionType,
				width, height, Objects.requireNonNull(name, "name"));
		}

		public static AuthoredObjectCollisionDefinition boundary(
			final int authoredSourceOrdinal,
			final int constructedEntityId,
			final int doorType,
			final String name) {
			return new AuthoredObjectCollisionDefinition(
				authoredSourceOrdinal, constructedEntityId,
				ConstructorState.BOUNDARY, true, doorType,
				1, 1, Objects.requireNonNull(name, "name"));
		}

		public static AuthoredObjectCollisionDefinition
			specialCollisionlessScenery(final int authoredSourceOrdinal) {
			return new AuthoredObjectCollisionDefinition(
				authoredSourceOrdinal, SPECIAL_COLLISIONLESS_OBJECT_ID,
				ConstructorState.SCENERY, false,
				NOT_APPLICABLE, NOT_APPLICABLE, NOT_APPLICABLE, null);
		}

		private void requireMatches(
			final AuthoredReplayPlacement placement) {
			if (authoredSourceOrdinal
					!= placement.getAuthoredSourceOrdinal()
				|| constructedEntityId
					!= placement.getConstructedEntityId()
				|| objectType != placement.getObjectType()) {
				throw new IllegalArgumentException(
					"Collision definition does not match authored order");
			}
		}

		private Definition toPlannerDefinition(
			final String[] projectileClipAllowedNames) {
			if (!definitionAvailable) {
				return null;
			}
			return objectType == ConstructorState.SCENERY
				? Definition.scenery(
					collisionType, width, height, name,
					projectileClipAllowedNames)
				: Definition.boundary(
					collisionType, name, projectileClipAllowedNames);
		}

		public int getAuthoredSourceOrdinal() {
			return authoredSourceOrdinal;
		}
		public int getConstructedEntityId() {
			return constructedEntityId;
		}
		public int getObjectType() { return objectType; }
		public boolean isDefinitionAvailable() {
			return definitionAvailable;
		}
		public int getCollisionType() { return collisionType; }
		public int getWidth() { return width; }
		public int getHeight() { return height; }
		public String getName() { return name; }
	}

	/** One exact immutable register footprint in stable authored order. */
	public static final class AuthoredObjectCollisionFootprint {
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
		private final boolean definitionAvailable;
		private final int collisionType;
		private final int definitionWidth;
		private final int definitionHeight;
		private final String definitionName;
		private final boolean projectileClipAllowed;
		private final List<Contribution> contributions;
		private final List<RequiredPackedRegion> requiredRegions;
		private final boolean crossSourceCollision;
		private final boolean collisionBeyondAuthoredDependency;

		private AuthoredObjectCollisionFootprint(
			final AuthoredReplayPlacement placement,
			final AuthoredObjectCollisionDefinition input,
			final Definition definition,
			final Result result) {
			this.authoredGeneration = placement.getAuthoredGeneration();
			this.sourcePackedRegionX =
				placement.getSourcePackedRegionX();
			this.sourcePackedRegionY =
				placement.getSourcePackedRegionY();
			this.authoredSourceOrdinal =
				placement.getAuthoredSourceOrdinal();
			this.constructionKind = placement.getConstructionKind();
			this.objectId = placement.getConstructedEntityId();
			this.permanentObjectId = placement.getPermanentObjectId();
			this.packedX = placement.getPackedX();
			this.packedY = placement.getPackedY();
			this.direction = placement.getDirection();
			this.objectType = placement.getObjectType();
			this.objectOwner = placement.getObjectOwner();
			this.definitionAvailable = input.isDefinitionAvailable();
			this.collisionType = input.getCollisionType();
			this.definitionWidth = input.getWidth();
			this.definitionHeight = input.getHeight();
			this.definitionName = input.getName();
			this.projectileClipAllowed =
				definition != null && definition.isProjectileClipAllowed();

			List<Contribution> copiedContributions =
				new ArrayList<Contribution>(
					result.getContributionTileCount());
			boolean beyondDependency = false;
			for (CollisionContribution contribution
					: result.getContributions()) {
				Contribution copied = Contribution.copyOf(contribution);
				copiedContributions.add(copied);
				beyondDependency |=
					copied.getPackedX() < placement.getMinimumPackedX()
						|| copied.getPackedX()
							> placement.getMaximumPackedX()
						|| copied.getPackedY()
							< placement.getMinimumPackedY()
						|| copied.getPackedY()
							> placement.getMaximumPackedY();
			}
			this.contributions = Collections.unmodifiableList(
				copiedContributions);

			List<RequiredPackedRegion> copiedRegions =
				new ArrayList<RequiredPackedRegion>(
					result.getRequiredRegionCount());
			boolean crossSource = false;
			for (PackedRegionCoordinate region
					: result.getRequiredRegions()) {
				RequiredPackedRegion copied =
					RequiredPackedRegion.copyOf(region);
				copiedRegions.add(copied);
				crossSource |=
					copied.getPackedRegionX() != sourcePackedRegionX
						|| copied.getPackedRegionY()
							!= sourcePackedRegionY;
				beyondDependency |=
					copied.getPackedRegionX()
							< placement.getMinimumPackedRegionX()
						|| copied.getPackedRegionX()
							> placement.getMaximumPackedRegionX()
						|| copied.getPackedRegionY()
							< placement.getMinimumPackedRegionY()
						|| copied.getPackedRegionY()
							> placement.getMaximumPackedRegionY();
			}
			this.requiredRegions = Collections.unmodifiableList(copiedRegions);
			this.crossSourceCollision = crossSource;
			this.collisionBeyondAuthoredDependency = beyondDependency;
		}

		private static AuthoredObjectCollisionFootprint copyOf(
			final AuthoredReplayPlacement placement,
			final AuthoredObjectCollisionDefinition input,
			final Definition definition,
			final Result result) {
			return new AuthoredObjectCollisionFootprint(
				placement, input, definition, result);
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
		public int getObjectId() { return objectId; }
		public int getPermanentObjectId() { return permanentObjectId; }
		public int getPackedX() { return packedX; }
		public int getPackedY() { return packedY; }
		public int getDirection() { return direction; }
		public int getObjectType() { return objectType; }
		public String getObjectOwner() { return objectOwner; }
		public boolean isDefinitionAvailable() {
			return definitionAvailable;
		}
		public int getCollisionType() { return collisionType; }
		public int getDefinitionWidth() { return definitionWidth; }
		public int getDefinitionHeight() { return definitionHeight; }
		public String getDefinitionName() { return definitionName; }
		public boolean isProjectileClipAllowed() {
			return projectileClipAllowed;
		}
		public List<Contribution> getContributions() {
			return contributions;
		}
		public int getContributionTileCount() {
			return contributions.size();
		}
		public boolean isZeroContributionFootprint() {
			return contributions.isEmpty();
		}
		public List<RequiredPackedRegion> getRequiredRegions() {
			return requiredRegions;
		}
		public int getRequiredRegionCount() {
			return requiredRegions.size();
		}
		public boolean isCrossSourceCollision() {
			return crossSourceCollision;
		}
		public boolean isCollisionBeyondAuthoredDependency() {
			return collisionBeyondAuthoredDependency;
		}

		private void updateDigest(final MessageDigest digest) {
			updateLong(digest, authoredGeneration);
			updateInt(digest, sourcePackedRegionX);
			updateInt(digest, sourcePackedRegionY);
			updateInt(digest, authoredSourceOrdinal);
			updateInt(digest, constructionKind.ordinal());
			updateInt(digest, objectId);
			updateInt(digest, permanentObjectId);
			updateInt(digest, packedX);
			updateInt(digest, packedY);
			updateInt(digest, direction);
			updateInt(digest, objectType);
			updateString(digest, objectOwner);
			digest.update((byte) (definitionAvailable ? 1 : 0));
			updateInt(digest, collisionType);
			updateInt(digest, definitionWidth);
			updateInt(digest, definitionHeight);
			updateString(digest, definitionName);
			digest.update((byte) (projectileClipAllowed ? 1 : 0));
			updateInt(digest, contributions.size());
			for (Contribution contribution : contributions) {
				contribution.updateDigest(digest);
			}
			updateInt(digest, requiredRegions.size());
			for (RequiredPackedRegion region : requiredRegions) {
				updateInt(digest, region.getPackedRegionX());
				updateInt(digest, region.getPackedRegionY());
			}
			digest.update((byte) (crossSourceCollision ? 1 : 0));
			digest.update((byte) (
				collisionBeyondAuthoredDependency ? 1 : 0));
		}
	}

	/** One exact per-tile collision contribution copied as primitive counts. */
	public static final class Contribution {
		private final int packedX;
		private final int packedY;
		private final int blockingSceneryCount;
		private final int[] dynamicCollisionCounts;
		private final int dynamicProjectileCount;

		private Contribution(final CollisionContribution source) {
			this.packedX = source.getX();
			this.packedY = source.getY();
			this.blockingSceneryCount =
				source.getBlockingSceneryCount();
			this.dynamicCollisionCounts =
				source.getDynamicCollisionCounts();
			this.dynamicProjectileCount =
				source.getDynamicProjectileCount();
		}

		private static Contribution copyOf(
			final CollisionContribution source) {
			return new Contribution(
				Objects.requireNonNull(source, "collisionContribution"));
		}

		public int getPackedX() { return packedX; }
		public int getPackedY() { return packedY; }
		public int getBlockingSceneryCount() {
			return blockingSceneryCount;
		}
		public int[] getDynamicCollisionCounts() {
			return dynamicCollisionCounts.clone();
		}
		public int getDynamicCollisionCount(final int bit) {
			if (bit < 0 || bit >= dynamicCollisionCounts.length) {
				throw new IllegalArgumentException(
					"Dynamic collision bit is invalid");
			}
			return dynamicCollisionCounts[bit];
		}
		public int getDynamicProjectileCount() {
			return dynamicProjectileCount;
		}

		private void updateDigest(final MessageDigest digest) {
			updateInt(digest, packedX);
			updateInt(digest, packedY);
			updateInt(digest, blockingSceneryCount);
			for (int count : dynamicCollisionCounts) {
				updateInt(digest, count);
			}
			updateInt(digest, dynamicProjectileCount);
		}
	}

	/** One canonical packed Region needed by a future collision transaction. */
	public static final class RequiredPackedRegion {
		private static final Comparator<RequiredPackedRegion> ORDER =
			new Comparator<RequiredPackedRegion>() {
				@Override
				public int compare(
					final RequiredPackedRegion left,
					final RequiredPackedRegion right) {
					int compared = Integer.compare(
						left.packedRegionX, right.packedRegionX);
					return compared != 0 ? compared : Integer.compare(
						left.packedRegionY, right.packedRegionY);
				}
			};

		private final int packedRegionX;
		private final int packedRegionY;

		private RequiredPackedRegion(
			final int packedRegionX,
			final int packedRegionY) {
			if (packedRegionX < 0 || packedRegionY < 0) {
				throw new IllegalArgumentException(
					"Required packed Region is invalid");
			}
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
		}

		private static RequiredPackedRegion copyOf(
			final PackedRegionCoordinate source) {
			return new RequiredPackedRegion(
				source.getRegionX(), source.getRegionY());
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
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
