package com.openrsc.server.model.entity.npc;

import java.util.IdentityHashMap;
import java.util.List;

/**
 * Per-NPC exclusion gate for a short owner-preservation observation.
 *
 * <p>Normal lifecycle operations never hold this monitor while executing
 * arbitrary gameplay or event code. Instead they increment a bounded in-flight
 * count. Preservation refuses if work is already active; once accepted, new
 * lifecycle work waits until the observation returns.</p>
 */
final class NpcOwnerPreservationLifecycleGate {
	private final Object lock = new Object();
	private int operationsInProgress;
	private boolean preservationBoundaryActive;
	private Thread preservationBoundaryThread;

	boolean withinPreservationBoundary(
		final PreservationOperation operation) {
		if (operation == null) {
			throw new NullPointerException("operation");
		}
		if (!tryEnterPreservationBoundary()) {
			return false;
		}
		try {
			operation.execute(new Boundary(true, 0));
			return true;
		} finally {
			exitPreservationBoundary();
		}
	}

	static boolean withinPreservationBoundaries(
		final List<NpcOwnerPreservationLifecycleGate> gates,
		final PreservationSetOperation operation) {
		if (gates == null) {
			throw new NullPointerException("gates");
		}
		if (operation == null) {
			throw new NullPointerException("operation");
		}
		IdentityHashMap<NpcOwnerPreservationLifecycleGate, Boolean> seen =
			new IdentityHashMap<NpcOwnerPreservationLifecycleGate, Boolean>();
		for (int index = 0; index < gates.size(); index++) {
			NpcOwnerPreservationLifecycleGate gate = gates.get(index);
			if (gate == null) {
				throw new NullPointerException("gates[" + index + "]");
			}
			if (seen.put(gate, Boolean.TRUE) != null) {
				throw new IllegalArgumentException(
					"NPC preservation gate set contains a duplicate");
			}
		}

		int acquired = 0;
		try {
			for (; acquired < gates.size(); acquired++) {
				if (!gates.get(acquired).tryEnterPreservationBoundary()) {
					return false;
				}
			}
			operation.execute(new BoundarySet(gates.size()));
			return true;
		} finally {
			for (int index = acquired - 1; index >= 0; index--) {
				gates.get(index).exitPreservationBoundary();
			}
		}
	}

	void beginOperation() {
		boolean interrupted = false;
		synchronized (lock) {
			while (preservationBoundaryActive) {
				if (preservationBoundaryThread == Thread.currentThread()) {
					throw new IllegalStateException(
						"Preservation boundary cannot invoke NPC lifecycle");
				}
				try {
					lock.wait();
				} catch (InterruptedException interruptedException) {
					interrupted = true;
				}
			}
			operationsInProgress++;
			if (operationsInProgress <= 0) {
				throw new IllegalStateException(
					"NPC owner lifecycle operation count overflow");
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	void endOperation() {
		synchronized (lock) {
			if (operationsInProgress <= 0) {
				throw new IllegalStateException(
					"NPC owner lifecycle operation count underflow");
			}
			operationsInProgress--;
			if (operationsInProgress == 0) {
				lock.notifyAll();
			}
		}
	}

	private boolean tryEnterPreservationBoundary() {
		synchronized (lock) {
			if (preservationBoundaryActive || operationsInProgress != 0) {
				return false;
			}
			preservationBoundaryActive = true;
			preservationBoundaryThread = Thread.currentThread();
			return true;
		}
	}

	private void exitPreservationBoundary() {
		synchronized (lock) {
			if (!preservationBoundaryActive
				|| preservationBoundaryThread != Thread.currentThread()
				|| operationsInProgress != 0) {
				throw new IllegalStateException(
					"NPC owner preservation lifecycle gate changed");
			}
			preservationBoundaryActive = false;
			preservationBoundaryThread = null;
			lock.notifyAll();
		}
	}

	@FunctionalInterface
	interface PreservationOperation {
		void execute(Boundary boundary);
	}

	@FunctionalInterface
	interface PreservationSetOperation {
		void execute(BoundarySet boundary);
	}

	/** Closed facts valid only while one complete iterative set is held. */
	static final class BoundarySet {
		private final int ownerCount;

		private BoundarySet(final int ownerCount) {
			if (ownerCount < 0) {
				throw new IllegalArgumentException(
					"NPC preservation boundary count must not be negative");
			}
			this.ownerCount = ownerCount;
		}

		int getOwnerCount() {
			return ownerCount;
		}

		boolean isCompleteSetHeld() {
			return true;
		}
	}

	/** Closed facts valid only during one accepted operation. */
	static final class Boundary {
		private final boolean preservationGateActive;
		private final int lifecycleOperationsAtEntry;

		private Boundary(
			final boolean preservationGateActive,
			final int lifecycleOperationsAtEntry) {
			this.preservationGateActive = preservationGateActive;
			this.lifecycleOperationsAtEntry = lifecycleOperationsAtEntry;
			if (!preservationGateActive || lifecycleOperationsAtEntry != 0) {
				throw new IllegalArgumentException(
					"NPC owner preservation lifecycle boundary is invalid");
			}
		}

		boolean isPreservationGateActive() {
			return preservationGateActive;
		}
		int getLifecycleOperationsAtEntry() {
			return lifecycleOperationsAtEntry;
		}
	}
}
