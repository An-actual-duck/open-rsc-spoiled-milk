package com.openrsc.server.event.rsc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant specification for an ordered multi-Region collision transaction.
 *
 * <p>The contract derives the exact packed Region set from a Slice 128
 * rollback snapshot, then checks detached declarations for canonical lock
 * order, availability, shared object/collision mutation boundaries, fresh
 * comparison, and rollback revalidation. It acquires no lock and performs no
 * runtime comparison, object operation, collision operation, or rollback.</p>
 */
public final class GameTickEventRestorationCollisionTransactionContract {
	public static final int PACKED_REGION_SIZE = 48;

	private final Outcome outcome;
	private final Reason reason;
	private final List<PackedRegionCoordinate> requiredRegions;

	private GameTickEventRestorationCollisionTransactionContract(
		final Outcome outcome,
		final Reason reason,
		final List<PackedRegionCoordinate> requiredRegions) {
		this.outcome = Objects.requireNonNull(outcome, "outcome");
		this.reason = Objects.requireNonNull(reason, "reason");
		this.requiredRegions = Collections.unmodifiableList(
			new ArrayList<PackedRegionCoordinate>(
				Objects.requireNonNull(requiredRegions, "requiredRegions")));
		boolean satisfied = outcome == Outcome.TRANSACTION_PRECONDITION_SATISFIED;
		if (satisfied
			!= (reason == Reason.TRANSACTION_PRECONDITION_REVALIDATED)) {
			throw new IllegalArgumentException(
				"Collision transaction contract result is inconsistent");
		}
	}

	/** Applies the complete dormant boundary/refusal ordering. */
	public static GameTickEventRestorationCollisionTransactionContract
		evaluate(
			final GameTickEventRestorationTransientRollbackSnapshot snapshot,
			final BoundaryDeclaration boundaryDeclaration) {
		GameTickEventRestorationTransientRollbackSnapshot checkedSnapshot =
			Objects.requireNonNull(snapshot, "snapshot");
		BoundaryDeclaration boundary = Objects.requireNonNull(
			boundaryDeclaration, "boundaryDeclaration");
		List<PackedRegionCoordinate> required = requiredRegions(
			checkedSnapshot);
		if (!boundary.isEventExecutionBoundaryHeld()) {
			return refused(Reason.EVENT_EXECUTION_BOUNDARY_MISSING, required);
		}
		if (boundary.isSchedulerStoreBoundaryHeld()) {
			return refused(Reason.SCHEDULER_STORE_BOUNDARY_HELD, required);
		}
		if (!boundary.isTargetRegionObjectBoundaryHeld()) {
			return refused(
				Reason.TARGET_REGION_OBJECT_BOUNDARY_MISSING, required);
		}
		if (!boundary.isExactTargetRevalidatedInsideBoundary()) {
			return refused(Reason.EXACT_TARGET_NOT_REVALIDATED, required);
		}
		if (!isCanonical(boundary.getRegions())) {
			return refused(Reason.REGION_LOCK_ORDER_NOT_CANONICAL, required);
		}
		List<PackedRegionCoordinate> observed =
			new ArrayList<PackedRegionCoordinate>();
		for (RegionBoundary region : boundary.getRegions()) {
			observed.add(region.getCoordinate());
		}
		if (!required.equals(observed)) {
			return refused(Reason.REGION_COVERAGE_MISMATCH, required);
		}
		for (RegionBoundary region : boundary.getRegions()) {
			if (!region.isRegionAvailable()) {
				return refused(Reason.REQUIRED_REGION_UNAVAILABLE, required);
			}
			if (!region.isCollisionMutationBoundaryHeld()) {
				return refused(
					Reason.COLLISION_MUTATION_BOUNDARY_MISSING, required);
			}
		}
		if (!boundary.isEveryObjectCollisionMutationSharingBoundary()) {
			return refused(
				Reason.SHARED_MUTATION_BOUNDARY_INCOMPLETE, required);
		}
		if (!boundary.isFreshCollisionStateComparedToSnapshot()) {
			return refused(
				Reason.COLLISION_SNAPSHOT_NOT_FRESHLY_COMPARED, required);
		}
		if (!boundary.isRollbackUnchangedStateCheckRequired()) {
			return refused(
				Reason.ROLLBACK_UNCHANGED_STATE_CHECK_MISSING, required);
		}
		return new GameTickEventRestorationCollisionTransactionContract(
			Outcome.TRANSACTION_PRECONDITION_SATISFIED,
			Reason.TRANSACTION_PRECONDITION_REVALIDATED, required);
	}

	private static GameTickEventRestorationCollisionTransactionContract refused(
		final Reason reason,
		final List<PackedRegionCoordinate> requiredRegions) {
		if (reason == Reason.TRANSACTION_PRECONDITION_REVALIDATED) {
			throw new IllegalArgumentException(
				"Satisfied reason cannot refuse a collision transaction");
		}
		return new GameTickEventRestorationCollisionTransactionContract(
			Outcome.REFUSED, reason, requiredRegions);
	}

	private static List<PackedRegionCoordinate> requiredRegions(
		final GameTickEventRestorationTransientRollbackSnapshot snapshot) {
		Set<PackedRegionCoordinate> unique =
			new LinkedHashSet<PackedRegionCoordinate>();
		unique.add(PackedRegionCoordinate.fromTile(
			snapshot.getX(), snapshot.getY()));
		for (GameTickEventRestorationTransientRollbackSnapshot
				.CollisionContribution contribution
					: snapshot.getCollisionContributions()) {
			unique.add(PackedRegionCoordinate.fromTile(
				contribution.getX(), contribution.getY()));
		}
		List<PackedRegionCoordinate> canonical =
			new ArrayList<PackedRegionCoordinate>(unique);
		Collections.sort(canonical, PackedRegionCoordinate.ORDER);
		return Collections.unmodifiableList(canonical);
	}

	private static boolean isCanonical(final List<RegionBoundary> regions) {
		PackedRegionCoordinate previous = null;
		for (RegionBoundary region : regions) {
			PackedRegionCoordinate current = region.getCoordinate();
			if (previous != null
				&& PackedRegionCoordinate.ORDER.compare(previous, current) >= 0) {
				return false;
			}
			previous = current;
		}
		return true;
	}

	public Outcome getOutcome() { return outcome; }
	public Reason getReason() { return reason; }
	public List<PackedRegionCoordinate> getRequiredRegions() {
		return requiredRegions;
	}
	public int getRequiredRegionCount() { return requiredRegions.size(); }
	public boolean isRefused() { return outcome == Outcome.REFUSED; }
	public boolean isTransactionPreconditionSatisfied() {
		return outcome == Outcome.TRANSACTION_PRECONDITION_SATISFIED;
	}

	public boolean isDormantContract() { return true; }
	public boolean isRuntimeLockAcquired() { return false; }
	public boolean isRuntimeComparisonPerformed() { return false; }
	public boolean isSnapshotRetainedByRuntime() { return false; }
	public boolean isReusablePermit() { return false; }
	public boolean isAtomicityClaimed() { return false; }
	public boolean isRollbackAuthorized() { return false; }
	public boolean isRollbackPerformed() { return false; }
	public boolean isMutationAuthorized() { return false; }
	public boolean isMutationPerformed() { return false; }
	public boolean isExecutableRestoration() { return false; }
	public boolean isCommitToken() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	public enum Outcome {
		REFUSED,
		TRANSACTION_PRECONDITION_SATISFIED
	}

	public enum Reason {
		EVENT_EXECUTION_BOUNDARY_MISSING,
		SCHEDULER_STORE_BOUNDARY_HELD,
		TARGET_REGION_OBJECT_BOUNDARY_MISSING,
		EXACT_TARGET_NOT_REVALIDATED,
		REGION_LOCK_ORDER_NOT_CANONICAL,
		REGION_COVERAGE_MISMATCH,
		REQUIRED_REGION_UNAVAILABLE,
		COLLISION_MUTATION_BOUNDARY_MISSING,
		SHARED_MUTATION_BOUNDARY_INCOMPLETE,
		COLLISION_SNAPSHOT_NOT_FRESHLY_COMPARED,
		ROLLBACK_UNCHANGED_STATE_CHECK_MISSING,
		TRANSACTION_PRECONDITION_REVALIDATED
	}

	/** Detached declarations; they do not acquire or prove any boundary. */
	public static final class BoundaryDeclaration {
		private final boolean eventExecutionBoundaryHeld;
		private final boolean schedulerStoreBoundaryHeld;
		private final boolean targetRegionObjectBoundaryHeld;
		private final boolean exactTargetRevalidatedInsideBoundary;
		private final boolean everyObjectCollisionMutationSharingBoundary;
		private final boolean freshCollisionStateComparedToSnapshot;
		private final boolean rollbackUnchangedStateCheckRequired;
		private final List<RegionBoundary> regions;

		private BoundaryDeclaration(
			final boolean eventExecutionBoundaryHeld,
			final boolean schedulerStoreBoundaryHeld,
			final boolean targetRegionObjectBoundaryHeld,
			final boolean exactTargetRevalidatedInsideBoundary,
			final boolean everyObjectCollisionMutationSharingBoundary,
			final boolean freshCollisionStateComparedToSnapshot,
			final boolean rollbackUnchangedStateCheckRequired,
			final List<RegionBoundary> regions) {
			List<RegionBoundary> checked = Objects.requireNonNull(
				regions, "regions");
			if (checked.isEmpty()
				|| checked.size()
					> GameTickEventRestorationTransientRollbackSnapshot
						.MAXIMUM_COLLISION_CONTRIBUTION_TILES + 1
				|| checked.contains(null)) {
				throw new IllegalArgumentException(
					"Collision transaction Region declaration is invalid");
			}
			this.eventExecutionBoundaryHeld = eventExecutionBoundaryHeld;
			this.schedulerStoreBoundaryHeld = schedulerStoreBoundaryHeld;
			this.targetRegionObjectBoundaryHeld =
				targetRegionObjectBoundaryHeld;
			this.exactTargetRevalidatedInsideBoundary =
				exactTargetRevalidatedInsideBoundary;
			this.everyObjectCollisionMutationSharingBoundary =
				everyObjectCollisionMutationSharingBoundary;
			this.freshCollisionStateComparedToSnapshot =
				freshCollisionStateComparedToSnapshot;
			this.rollbackUnchangedStateCheckRequired =
				rollbackUnchangedStateCheckRequired;
			this.regions = Collections.unmodifiableList(
				new ArrayList<RegionBoundary>(checked));
		}

		public static BoundaryDeclaration declare(
			final boolean eventExecutionBoundaryHeld,
			final boolean schedulerStoreBoundaryHeld,
			final boolean targetRegionObjectBoundaryHeld,
			final boolean exactTargetRevalidatedInsideBoundary,
			final boolean everyObjectCollisionMutationSharingBoundary,
			final boolean freshCollisionStateComparedToSnapshot,
			final boolean rollbackUnchangedStateCheckRequired,
			final List<RegionBoundary> regions) {
			return new BoundaryDeclaration(
				eventExecutionBoundaryHeld, schedulerStoreBoundaryHeld,
				targetRegionObjectBoundaryHeld,
				exactTargetRevalidatedInsideBoundary,
				everyObjectCollisionMutationSharingBoundary,
				freshCollisionStateComparedToSnapshot,
				rollbackUnchangedStateCheckRequired, regions);
		}

		public boolean isEventExecutionBoundaryHeld() {
			return eventExecutionBoundaryHeld;
		}
		public boolean isSchedulerStoreBoundaryHeld() {
			return schedulerStoreBoundaryHeld;
		}
		public boolean isTargetRegionObjectBoundaryHeld() {
			return targetRegionObjectBoundaryHeld;
		}
		public boolean isExactTargetRevalidatedInsideBoundary() {
			return exactTargetRevalidatedInsideBoundary;
		}
		public boolean isEveryObjectCollisionMutationSharingBoundary() {
			return everyObjectCollisionMutationSharingBoundary;
		}
		public boolean isFreshCollisionStateComparedToSnapshot() {
			return freshCollisionStateComparedToSnapshot;
		}
		public boolean isRollbackUnchangedStateCheckRequired() {
			return rollbackUnchangedStateCheckRequired;
		}
		public List<RegionBoundary> getRegions() { return regions; }
	}

	/** One required Region in the caller-declared canonical acquisition order. */
	public static final class RegionBoundary {
		private final PackedRegionCoordinate coordinate;
		private final boolean regionAvailable;
		private final boolean collisionMutationBoundaryHeld;

		private RegionBoundary(
			final PackedRegionCoordinate coordinate,
			final boolean regionAvailable,
			final boolean collisionMutationBoundaryHeld) {
			this.coordinate = Objects.requireNonNull(
				coordinate, "coordinate");
			this.regionAvailable = regionAvailable;
			this.collisionMutationBoundaryHeld =
				collisionMutationBoundaryHeld;
		}

		public static RegionBoundary declare(
			final int regionX,
			final int regionY,
			final boolean regionAvailable,
			final boolean collisionMutationBoundaryHeld) {
			return new RegionBoundary(
				PackedRegionCoordinate.of(regionX, regionY),
				regionAvailable, collisionMutationBoundaryHeld);
		}

		public PackedRegionCoordinate getCoordinate() { return coordinate; }
		public boolean isRegionAvailable() { return regionAvailable; }
		public boolean isCollisionMutationBoundaryHeld() {
			return collisionMutationBoundaryHeld;
		}
	}

	/** Canonically ordered packed Region identity, independent of a live Region. */
	public static final class PackedRegionCoordinate {
		private static final Comparator<PackedRegionCoordinate> ORDER =
			new Comparator<PackedRegionCoordinate>() {
				@Override
				public int compare(
					final PackedRegionCoordinate left,
					final PackedRegionCoordinate right) {
					int compared = Integer.compare(
						left.regionX, right.regionX);
					return compared != 0 ? compared : Integer.compare(
						left.regionY, right.regionY);
				}
			};

		private final int regionX;
		private final int regionY;

		private PackedRegionCoordinate(
			final int regionX,
			final int regionY) {
			if (regionX < 0 || regionY < 0) {
				throw new IllegalArgumentException(
					"Packed Region coordinate is invalid");
			}
			this.regionX = regionX;
			this.regionY = regionY;
		}

		public static PackedRegionCoordinate of(
			final int regionX,
			final int regionY) {
			return new PackedRegionCoordinate(regionX, regionY);
		}

		private static PackedRegionCoordinate fromTile(
			final int x,
			final int y) {
			return of(
				Math.floorDiv(x, PACKED_REGION_SIZE),
				Math.floorDiv(y, PACKED_REGION_SIZE));
		}

		public int getRegionX() { return regionX; }
		public int getRegionY() { return regionY; }

		@Override
		public boolean equals(final Object other) {
			if (this == other) { return true; }
			if (!(other instanceof PackedRegionCoordinate)) { return false; }
			PackedRegionCoordinate coordinate =
				(PackedRegionCoordinate) other;
			return regionX == coordinate.regionX
				&& regionY == coordinate.regionY;
		}

		@Override
		public int hashCode() { return 31 * regionX + regionY; }
	}
}
