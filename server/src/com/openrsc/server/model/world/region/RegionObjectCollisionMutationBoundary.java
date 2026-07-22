package com.openrsc.server.model.world.region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Package-local runtime foundation for canonically ordered Region boundaries.
 *
 * <p>The read-only entry point remains non-authoritative. A separate package-
 * local mutation entry point is consumed only by the disconnected exact
 * collision executor; ordinary World object paths do not yet use it. Neither
 * entry point retains its operation or result.</p>
 */
final class RegionObjectCollisionMutationBoundary {
	static final int MAXIMUM_BOUNDARIES = 4097;

	private final int regionX;
	private final int regionY;
	private final Object monitor = new Object();

	RegionObjectCollisionMutationBoundary(
		final int regionX,
		final int regionY) {
		this.regionX = regionX;
		this.regionY = regionY;
	}

	/**
	 * Acquires every boundary in strict `(regionX, regionY)` order and runs one
	 * read-only operation at the deepest point. Reverse-order release follows
	 * Java monitor unwinding. Invalid order refuses before any monitor is held.
	 */
	static Execution executeReadOnly(
		final List<RegionObjectCollisionMutationBoundary> boundaries,
		final ReadOnlyOperation operation) {
		List<RegionObjectCollisionMutationBoundary> checked =
			checkedCanonicalBoundaries(boundaries);
		ReadOnlyOperation checkedOperation = Objects.requireNonNull(
			operation, "operation");
		boolean[] allHeldDuringOperation = new boolean[]{false};
		executeAt(
			checked, 0, checkedOperation, allHeldDuringOperation);
		return Execution.completed(
			checked.size(), allHeldDuringOperation[0]);
	}

	static Execution refuseUnavailable(final int declaredBoundaryCount) {
		if (declaredBoundaryCount <= 0
			|| declaredBoundaryCount > MAXIMUM_BOUNDARIES) {
			throw new IllegalArgumentException(
				"Unavailable boundary count is invalid");
		}
		return Execution.refused(
			Reason.REQUIRED_REGION_UNAVAILABLE, declaredBoundaryCount);
	}

	/** Acquires the same canonical monitor set for one explicit mutation. */
	static MutationExecution executeMutation(
		final List<RegionObjectCollisionMutationBoundary> boundaries,
		final MutationOperation operation) {
		List<RegionObjectCollisionMutationBoundary> checked =
			checkedCanonicalBoundaries(boundaries);
		MutationOperation checkedOperation = Objects.requireNonNull(
			operation, "operation");
		boolean[] allHeldDuringOperation = new boolean[]{false};
		executeMutationAt(
			checked, 0, checkedOperation, allHeldDuringOperation);
		return MutationExecution.completed(
			checked.size(), allHeldDuringOperation[0]);
	}

	private static List<RegionObjectCollisionMutationBoundary>
		checkedCanonicalBoundaries(
			final List<RegionObjectCollisionMutationBoundary> boundaries) {
		List<RegionObjectCollisionMutationBoundary> checked =
			Objects.requireNonNull(boundaries, "boundaries");
		if (checked.isEmpty() || checked.size() > MAXIMUM_BOUNDARIES
			|| checked.contains(null)) {
			throw new IllegalArgumentException(
				"Object/collision mutation boundary set is invalid");
		}
		RegionObjectCollisionMutationBoundary previous = null;
		for (RegionObjectCollisionMutationBoundary current : checked) {
			if (previous != null && compare(previous, current) >= 0) {
				throw new IllegalArgumentException(
					"Object/collision mutation boundaries are not canonical");
			}
			previous = current;
		}
		return Collections.unmodifiableList(
			new ArrayList<RegionObjectCollisionMutationBoundary>(checked));
	}

	private static int compare(
		final RegionObjectCollisionMutationBoundary left,
		final RegionObjectCollisionMutationBoundary right) {
		int compared = Integer.compare(left.regionX, right.regionX);
		return compared != 0
			? compared : Integer.compare(left.regionY, right.regionY);
	}

	private static void executeAt(
		final List<RegionObjectCollisionMutationBoundary> boundaries,
		final int index,
		final ReadOnlyOperation operation,
		final boolean[] allHeldDuringOperation) {
		RegionObjectCollisionMutationBoundary boundary = boundaries.get(index);
		synchronized (boundary.monitor) {
			if (index + 1 < boundaries.size()) {
				executeAt(
					boundaries, index + 1, operation,
					allHeldDuringOperation);
			} else {
				boolean allHeld = true;
				List<Coordinate> coordinates = new ArrayList<Coordinate>();
				for (RegionObjectCollisionMutationBoundary candidate
						: boundaries) {
					allHeld &= Thread.holdsLock(candidate.monitor);
					coordinates.add(new Coordinate(
						candidate.regionX, candidate.regionY));
				}
				allHeldDuringOperation[0] = allHeld;
				if (!allHeld) {
					throw new IllegalStateException(
						"Canonical boundary escaped before operation");
				}
				operation.run(new HeldBoundarySet(coordinates, true));
			}
		}
	}

	private static void executeMutationAt(
		final List<RegionObjectCollisionMutationBoundary> boundaries,
		final int index,
		final MutationOperation operation,
		final boolean[] allHeldDuringOperation) {
		RegionObjectCollisionMutationBoundary boundary = boundaries.get(index);
		synchronized (boundary.monitor) {
			if (index + 1 < boundaries.size()) {
				executeMutationAt(
					boundaries, index + 1, operation,
					allHeldDuringOperation);
			} else {
				boolean allHeld = true;
				List<Coordinate> coordinates = new ArrayList<Coordinate>();
				for (RegionObjectCollisionMutationBoundary candidate
						: boundaries) {
					allHeld &= Thread.holdsLock(candidate.monitor);
					coordinates.add(new Coordinate(
						candidate.regionX, candidate.regionY));
				}
				allHeldDuringOperation[0] = allHeld;
				if (!allHeld) {
					throw new IllegalStateException(
						"Canonical boundary escaped before mutation");
				}
				operation.run(new HeldMutationBoundarySet(
					coordinates, true));
			}
		}
	}

	int getRegionX() { return regionX; }
	int getRegionY() { return regionY; }
	boolean isHeldByCurrentThread() { return Thread.holdsLock(monitor); }

	interface ReadOnlyOperation {
		void run(HeldBoundarySet heldBoundaries);
	}

	interface MutationOperation {
		void run(HeldMutationBoundarySet heldBoundaries);
	}

	static final class HeldBoundarySet {
		private final List<Coordinate> coordinates;
		private final boolean allBoundariesHeld;

		private HeldBoundarySet(
			final List<Coordinate> coordinates,
			final boolean allBoundariesHeld) {
			this.coordinates = Collections.unmodifiableList(
				new ArrayList<Coordinate>(coordinates));
			this.allBoundariesHeld = allBoundariesHeld;
		}

		List<Coordinate> getCoordinates() { return coordinates; }
		int getBoundaryCount() { return coordinates.size(); }
		boolean areAllBoundariesHeld() { return allBoundariesHeld; }
		boolean isMutationAuthorized() { return false; }
		boolean isMutationPerformed() { return false; }
		boolean isRollbackAuthorized() { return false; }
		boolean isLifecycleAuthority() { return false; }
	}

	static final class Coordinate {
		private final int regionX;
		private final int regionY;

		private Coordinate(final int regionX, final int regionY) {
			this.regionX = regionX;
			this.regionY = regionY;
		}

		int getRegionX() { return regionX; }
		int getRegionY() { return regionY; }
	}

	static final class HeldMutationBoundarySet {
		private final List<Coordinate> coordinates;
		private final boolean allBoundariesHeld;

		private HeldMutationBoundarySet(
			final List<Coordinate> coordinates,
			final boolean allBoundariesHeld) {
			this.coordinates = Collections.unmodifiableList(
				new ArrayList<Coordinate>(coordinates));
			this.allBoundariesHeld = allBoundariesHeld;
		}

		List<Coordinate> getCoordinates() { return coordinates; }
		int getBoundaryCount() { return coordinates.size(); }
		boolean areAllBoundariesHeld() { return allBoundariesHeld; }
		boolean isMutationAuthorized() { return allBoundariesHeld; }
		boolean isRollbackAuthorized() { return false; }
		boolean isLifecycleAuthority() { return false; }
	}

	enum Outcome {
		REFUSED,
		READ_ONLY_OPERATION_COMPLETED
	}

	enum Reason {
		REQUIRED_REGION_UNAVAILABLE,
		READ_ONLY_OPERATION_COMPLETED
	}

	static final class Execution {
		private final Outcome outcome;
		private final Reason reason;
		private final int declaredBoundaryCount;
		private final boolean allBoundariesHeldDuringOperation;

		private Execution(
			final Outcome outcome,
			final Reason reason,
			final int declaredBoundaryCount,
			final boolean allBoundariesHeldDuringOperation) {
			if (declaredBoundaryCount <= 0
				|| declaredBoundaryCount > MAXIMUM_BOUNDARIES
				|| (outcome == Outcome.READ_ONLY_OPERATION_COMPLETED)
					!= (reason == Reason.READ_ONLY_OPERATION_COMPLETED)
				|| (outcome == Outcome.READ_ONLY_OPERATION_COMPLETED)
					!= allBoundariesHeldDuringOperation) {
				throw new IllegalArgumentException(
					"Object/collision boundary execution is inconsistent");
			}
			this.outcome = Objects.requireNonNull(outcome, "outcome");
			this.reason = Objects.requireNonNull(reason, "reason");
			this.declaredBoundaryCount = declaredBoundaryCount;
			this.allBoundariesHeldDuringOperation =
				allBoundariesHeldDuringOperation;
		}

		private static Execution completed(
			final int boundaryCount,
			final boolean allHeld) {
			return new Execution(
				Outcome.READ_ONLY_OPERATION_COMPLETED,
				Reason.READ_ONLY_OPERATION_COMPLETED,
				boundaryCount, allHeld);
		}

		private static Execution refused(
			final Reason reason,
			final int boundaryCount) {
			return new Execution(
				Outcome.REFUSED, reason, boundaryCount, false);
		}

		Outcome getOutcome() { return outcome; }
		Reason getReason() { return reason; }
		int getDeclaredBoundaryCount() { return declaredBoundaryCount; }
		boolean isRefused() { return outcome == Outcome.REFUSED; }
		boolean isReadOnlyOperationCompleted() {
			return outcome == Outcome.READ_ONLY_OPERATION_COMPLETED;
		}
		boolean wereAllBoundariesHeldDuringOperation() {
			return allBoundariesHeldDuringOperation;
		}
		boolean isOperationRetained() { return false; }
		boolean isResultValueRetained() { return false; }
		boolean isMutationAuthorized() { return false; }
		boolean isMutationPerformed() { return false; }
		boolean isRollbackAuthorized() { return false; }
		boolean isRollbackPerformed() { return false; }
		boolean isExecutableRestoration() { return false; }
		boolean isCommitToken() { return false; }
		boolean isArrivalGate() { return false; }
		boolean isLifecycleAuthority() { return false; }
	}

	static final class MutationExecution {
		private final int declaredBoundaryCount;
		private final boolean allBoundariesHeldDuringOperation;

		private MutationExecution(
			final int declaredBoundaryCount,
			final boolean allBoundariesHeldDuringOperation) {
			if (declaredBoundaryCount <= 0
				|| declaredBoundaryCount > MAXIMUM_BOUNDARIES
				|| !allBoundariesHeldDuringOperation) {
				throw new IllegalArgumentException(
					"Object/collision mutation execution is inconsistent");
			}
			this.declaredBoundaryCount = declaredBoundaryCount;
			this.allBoundariesHeldDuringOperation =
				allBoundariesHeldDuringOperation;
		}

		private static MutationExecution completed(
			final int boundaryCount,
			final boolean allHeld) {
			return new MutationExecution(boundaryCount, allHeld);
		}

		int getDeclaredBoundaryCount() { return declaredBoundaryCount; }
		boolean wereAllBoundariesHeldDuringOperation() {
			return allBoundariesHeldDuringOperation;
		}
		boolean isOperationRetained() { return false; }
		boolean isMutationAuthorized() { return true; }
		boolean isMutationOperationInvoked() { return true; }
		boolean isRollbackAuthorized() { return false; }
		boolean isExecutableRestoration() { return false; }
		boolean isCommitToken() { return false; }
		boolean isArrivalGate() { return false; }
		boolean isLifecycleAuthority() { return false; }
	}
}
