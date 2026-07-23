package com.openrsc.server.model.entity.npc;

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
		synchronized (lock) {
			if (preservationBoundaryActive || operationsInProgress != 0) {
				return false;
			}
			preservationBoundaryActive = true;
			preservationBoundaryThread = Thread.currentThread();
		}
		try {
			operation.execute(new Boundary(true, 0));
			return true;
		} finally {
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

	@FunctionalInterface
	interface PreservationOperation {
		void execute(Boundary boundary);
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
