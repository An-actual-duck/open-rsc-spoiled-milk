package com.openrsc.server.event.rsc;

import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

public abstract class GameTickEvent implements Callable<Integer> {
	/**
	 * Logger instance
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	private final Object executionLock = new Object();
	private final Object timingLock = new Object();
	private final GameTickEventOwnerPreservationLifecycleGate
		ownerPreservationLifecycleGate =
			new GameTickEventOwnerPreservationLifecycleGate();
	protected volatile boolean running = true;
	private final Mob owner;
	private final World world;
	private long delayTicks;
	private long ticksBeforeRun = -1;
	private long lifecycleVersion;
	private String descriptor;
	private long lastEventDuration = 0;
	private final UUID uuid;
	private final DuplicationStrategy duplicationStrategy;
	private volatile int timesRan;

	public GameTickEvent(final World world, final Mob owner, final long ticks, final String descriptor, DuplicationStrategy duplicationStrategy) {
		this.world = world;
		this.owner = owner;
		this.setDescriptor(descriptor);
		this.setDelayTicks(ticks);
		this.resetCountdown();
		this.uuid = UUID.randomUUID();
		this.duplicationStrategy = duplicationStrategy;
		this.timesRan = 0;
	}

	public abstract void run();

	public final long doRun() {
		beginOwnerPreservationLifecycleOperation();
		try {
			synchronized (executionLock) {
				lastEventDuration = getWorld().getServer().bench(() -> {
					final boolean runNow;
					synchronized (timingLock) {
						requireLifecycleVersionAvailable();
						ticksBeforeRun--;
						advanceLifecycleVersion();
						runNow = running && ticksBeforeRun <= 0;
					}
					if (runNow) {
						// Never hold the timing monitor across arbitrary callback code.
						// Callbacks may own plugin or entity monitors while diagnostics
						// capture this event's detached timing tuple.
						run();
						synchronized (timingLock) {
							requireLifecycleVersionAvailable();
							timesRan++;
							ticksBeforeRun = delayTicks;
							advanceLifecycleVersion();
						}
					}
				});
			}
		} finally {
			endOwnerPreservationLifecycleOperation();
		}

		return lastEventDuration;
	}

	/**
	 * Executes one scheduler-internal operation while this event's existing
	 * execution boundary is held. The monitor itself never escapes, and the
	 * operation cannot retain the boundary after this method returns.
	 *
	 * <p>This is a lock-order seam, not callback, restoration, or mutation
	 * authority. Scheduler registration changes use it before acquiring their
	 * store monitor so a future inner Region operation never needs to carry the
	 * scheduler-store monitor inward.</p>
	 */
	public final <T> T withinExecutionBoundary(
		final ExecutionBoundaryOperation<T> operation) {
		if (operation == null) {
			throw new NullPointerException("operation");
		}
		beginOwnerPreservationLifecycleOperation();
		try {
			synchronized (executionLock) {
				return operation.execute();
			}
		} finally {
			endOwnerPreservationLifecycleOperation();
		}
	}

	/** Current-thread proof used only while an execution-bound operation runs. */
	public final boolean isExecutionBoundaryHeldByCurrentThread() {
		return Thread.holdsLock(executionLock);
	}

	/**
	 * Runs one narrow scheduler-internal operation while both the existing event
	 * execution boundary and an unchanged zero-run lifecycle tuple are held.
	 *
	 * <p>This is intentionally separate from callback execution. The supplied
	 * operation must receive only closed restoration scalars and must not invoke
	 * arbitrary callback, plugin, packet, or owner code. Holding the timing
	 * boundary prevents stop/reset/tick from invalidating a future Region commit
	 * between its final lifecycle check and completion.</p>
	 */
	public final boolean withinStableRestorationLifecycleBoundary(
		final long expectedLifecycleVersion,
		final StableRestorationLifecycleOperation operation) {
		if (expectedLifecycleVersion <= 0L) {
			throw new IllegalArgumentException(
				"Expected restoration lifecycle version must be positive");
		}
		if (operation == null) {
			throw new NullPointerException("operation");
		}
		if (!isExecutionBoundaryHeldByCurrentThread()) {
			throw new IllegalStateException(
				"Restoration lifecycle boundary requires event execution boundary");
		}
		synchronized (timingLock) {
			if (!running || timesRan != 0
				|| lifecycleVersion != expectedLifecycleVersion) {
				return false;
			}
			operation.execute(
				new StableRestorationLifecycleBoundary(
					lifecycleVersion, Thread.holdsLock(timingLock)));
			if (lifecycleVersion != expectedLifecycleVersion) {
				throw new IllegalStateException(
					"Restoration operation changed its guarded event lifecycle");
			}
			return true;
		}
	}

	/**
	 * Runs one internal owner-preservation operation while this event is both
	 * execution-fenced and running with an unchanged timing lifecycle.
	 *
	 * <p>Unlike scenery restoration, owner-bound periodic callbacks may already
	 * have run many times. This boundary therefore requires only a currently
	 * running event. It invokes no callback and grants no mutation authority.</p>
	 */
	public final boolean withinRunningOwnerPreservationLifecycleBoundary(
		final OwnerPreservationLifecycleOperation operation) {
		if (operation == null) {
			throw new NullPointerException("operation");
		}
		if (!isExecutionBoundaryHeldByCurrentThread()) {
			throw new IllegalStateException(
				"Owner preservation lifecycle requires event execution boundary");
		}
		synchronized (timingLock) {
			if (!running) {
				return false;
			}
			long expectedLifecycleVersion = lifecycleVersion;
			operation.execute(new OwnerPreservationLifecycleBoundary(
				ticksBeforeRun, timesRan, lifecycleVersion,
				Thread.holdsLock(timingLock)));
			if (lifecycleVersion != expectedLifecycleVersion) {
				throw new IllegalStateException(
					"Owner preservation operation changed event lifecycle");
			}
			return true;
		}
	}

	/**
	 * Iteratively excludes execution and timing changes for one exact event set.
	 *
	 * <p>Acquisition is stable-order and non-blocking. If any event is active,
	 * every earlier gate is released and the operation is not invoked. The
	 * supplied operation receives closed facts only; no event handle or permit
	 * escapes after return.</p>
	 */
	public static boolean withinOwnerPreservationLifecycleBoundaries(
		final List<GameTickEvent> events,
		final OwnerPreservationLifecycleSetOperation operation) {
		if (events == null) {
			throw new NullPointerException("events");
		}
		if (operation == null) {
			throw new NullPointerException("operation");
		}
		final List<GameTickEvent> checked =
			new ArrayList<GameTickEvent>(events.size());
		final List<GameTickEventOwnerPreservationLifecycleGate> gates =
			new ArrayList<GameTickEventOwnerPreservationLifecycleGate>(
				events.size());
		for (int index = 0; index < events.size(); index++) {
			GameTickEvent event = events.get(index);
			if (event == null) {
				throw new NullPointerException("events[" + index + "]");
			}
			checked.add(event);
			gates.add(event.ownerPreservationLifecycleGate);
		}

		final boolean[] operationCompleted = new boolean[1];
		boolean entered =
			GameTickEventOwnerPreservationLifecycleGate
				.withinPreservationBoundaries(gates, boundary -> {
					long[] lifecycleVersions = new long[checked.size()];
					for (int index = 0; index < checked.size(); index++) {
						GameTickEvent event = checked.get(index);
						if (!event.running) {
							return;
						}
						lifecycleVersions[index] = event.lifecycleVersion;
					}
					operation.execute(
						new OwnerPreservationLifecycleSetBoundary(
							boundary.getEventCount(),
							boundary.isCompleteSetHeld()));
					for (int index = 0; index < checked.size(); index++) {
						GameTickEvent event = checked.get(index);
						if (!event.running
							|| event.lifecycleVersion
								!= lifecycleVersions[index]) {
							throw new IllegalStateException(
								"Event owner preservation lifecycle changed");
						}
					}
					operationCompleted[0] = true;
				});
		return entered && operationCompleted[0];
	}

	private void beginOwnerPreservationLifecycleOperation() {
		ownerPreservationLifecycleGate.beginOperation();
	}

	private void endOwnerPreservationLifecycleOperation() {
		ownerPreservationLifecycleGate.endOperation();
	}

	@FunctionalInterface
	public interface OwnerPreservationLifecycleSetOperation {
		void execute(OwnerPreservationLifecycleSetBoundary boundary);
	}

	/** Closed facts valid only while one complete iterative event set is held. */
	public static final class OwnerPreservationLifecycleSetBoundary {
		private final int eventCount;
		private final boolean completeSetHeld;

		private OwnerPreservationLifecycleSetBoundary(
			final int eventCount,
			final boolean completeSetHeld) {
			if (eventCount < 0 || !completeSetHeld) {
				throw new IllegalArgumentException(
					"Event preservation lifecycle set is invalid");
			}
			this.eventCount = eventCount;
			this.completeSetHeld = true;
		}

		public int getEventCount() {
			return eventCount;
		}
		public boolean isCompleteSetHeld() {
			return completeSetHeld;
		}
		public boolean isPointInTimeOnly() { return true; }
		public boolean isRuntimeHandleRetained() { return false; }
		public boolean isMutationAuthorized() { return false; }
		public boolean isLifecycleAuthority() { return false; }
	}

	@FunctionalInterface
	public interface OwnerPreservationLifecycleOperation {
		void execute(OwnerPreservationLifecycleBoundary boundary);
	}

	/** Closed facts valid only during a running owner-preservation operation. */
	public static final class OwnerPreservationLifecycleBoundary {
		private final long ticksBeforeRun;
		private final int timesRan;
		private final long lifecycleVersion;
		private final boolean lifecycleBoundaryHeld;

		private OwnerPreservationLifecycleBoundary(
			final long ticksBeforeRun,
			final int timesRan,
			final long lifecycleVersion,
			final boolean lifecycleBoundaryHeld) {
			if (timesRan < 0 || lifecycleVersion <= 0L
				|| !lifecycleBoundaryHeld) {
				throw new IllegalStateException(
					"Owner preservation lifecycle boundary is invalid");
			}
			this.ticksBeforeRun = ticksBeforeRun;
			this.timesRan = timesRan;
			this.lifecycleVersion = lifecycleVersion;
			this.lifecycleBoundaryHeld = true;
		}

		public long getTicksBeforeRun() { return ticksBeforeRun; }
		public int getTimesRan() { return timesRan; }
		public long getLifecycleVersion() { return lifecycleVersion; }
		public boolean isLifecycleBoundaryHeld() {
			return lifecycleBoundaryHeld;
		}
		public boolean isCallbackInvoked() { return false; }
		public boolean isMutationAuthorized() { return false; }
		public boolean isReusablePermit() { return false; }
		public boolean isLifecycleAuthority() { return false; }
	}

	@FunctionalInterface
	public interface StableRestorationLifecycleOperation {
		void execute(StableRestorationLifecycleBoundary boundary);
	}

	/** Closed proof valid only during a stable restoration operation. */
	public static final class StableRestorationLifecycleBoundary {
		private final long lifecycleVersion;
		private final boolean lifecycleBoundaryHeld;

		private StableRestorationLifecycleBoundary(
			final long lifecycleVersion,
			final boolean lifecycleBoundaryHeld) {
			if (lifecycleVersion <= 0L || !lifecycleBoundaryHeld) {
				throw new IllegalStateException(
					"Stable restoration lifecycle boundary is invalid");
			}
			this.lifecycleVersion = lifecycleVersion;
			this.lifecycleBoundaryHeld = true;
		}

		public long getLifecycleVersion() { return lifecycleVersion; }
		public boolean isLifecycleBoundaryHeld() {
			return lifecycleBoundaryHeld;
		}
		public boolean isReusablePermit() { return false; }
		public boolean isMutationAuthorized() { return false; }
		public boolean isLifecycleAuthority() { return false; }
	}

	/**
	 * Runs one scheduler-internal restoration outcome while the same stable
	 * zero-run lifecycle boundary is held. Only this method may apply the
	 * returned terminal-consumption disposition; the operation receives no
	 * mutable event handle or lifecycle authority.
	 */
	public final StableRestorationConsumptionExecution
		withinStableRestorationConsumptionBoundary(
			final long expectedLifecycleVersion,
			final StableRestorationConsumptionOperation operation) {
		if (expectedLifecycleVersion <= 0L) {
			throw new IllegalArgumentException(
				"Expected restoration lifecycle version must be positive");
		}
		if (operation == null) {
			throw new NullPointerException("operation");
		}
		if (!isExecutionBoundaryHeldByCurrentThread()) {
			throw new IllegalStateException(
				"Restoration consumption requires event execution boundary");
		}
		synchronized (timingLock) {
			if (!running || timesRan != 0
				|| lifecycleVersion != expectedLifecycleVersion
				|| lifecycleVersion == Long.MAX_VALUE) {
				return StableRestorationConsumptionExecution.refused(
					expectedLifecycleVersion, lifecycleVersion);
			}
			RestorationLifecycleDisposition disposition =
				operation.execute(
					new StableRestorationLifecycleBoundary(
						lifecycleVersion, Thread.holdsLock(timingLock)));
			if (disposition == null) {
				throw new NullPointerException("restoration disposition");
			}
			if (lifecycleVersion != expectedLifecycleVersion) {
				throw new IllegalStateException(
					"Restoration operation changed its guarded event lifecycle");
			}
			if (disposition
					== RestorationLifecycleDisposition.TERMINALLY_CONSUME) {
				requireLifecycleVersionAvailable();
				running = false;
				advanceLifecycleVersion();
			}
			return StableRestorationConsumptionExecution.completed(
				disposition, expectedLifecycleVersion, lifecycleVersion);
		}
	}

	@FunctionalInterface
	public interface StableRestorationConsumptionOperation {
		RestorationLifecycleDisposition execute(
			StableRestorationLifecycleBoundary boundary);
	}

	public enum RestorationLifecycleDisposition {
		RETAIN_SCHEDULED,
		TERMINALLY_CONSUME
	}

	/** Closed lifecycle result with no event, callback, or monitor handle. */
	public static final class StableRestorationConsumptionExecution {
		private final boolean boundaryEntered;
		private final RestorationLifecycleDisposition disposition;
		private final long lifecycleVersionBefore;
		private final long lifecycleVersionAfter;

		private StableRestorationConsumptionExecution(
			final boolean boundaryEntered,
			final RestorationLifecycleDisposition disposition,
			final long lifecycleVersionBefore,
			final long lifecycleVersionAfter) {
			this.boundaryEntered = boundaryEntered;
			this.disposition = disposition;
			this.lifecycleVersionBefore = lifecycleVersionBefore;
			this.lifecycleVersionAfter = lifecycleVersionAfter;
			if (lifecycleVersionBefore <= 0L
				|| lifecycleVersionAfter <= 0L
				|| boundaryEntered != (disposition != null)
				|| (disposition
						== RestorationLifecycleDisposition.RETAIN_SCHEDULED
					&& lifecycleVersionAfter != lifecycleVersionBefore)
				|| (disposition
						== RestorationLifecycleDisposition.TERMINALLY_CONSUME
					&& lifecycleVersionAfter != lifecycleVersionBefore + 1L)) {
				throw new IllegalArgumentException(
					"Restoration consumption result is inconsistent");
			}
		}

		private static StableRestorationConsumptionExecution refused(
			final long expectedVersion,
			final long observedVersion) {
			return new StableRestorationConsumptionExecution(
				false, null, expectedVersion, observedVersion);
		}

		private static StableRestorationConsumptionExecution completed(
			final RestorationLifecycleDisposition disposition,
			final long before,
			final long after) {
			return new StableRestorationConsumptionExecution(
				true, disposition, before, after);
		}

		public boolean isBoundaryEntered() { return boundaryEntered; }
		public RestorationLifecycleDisposition getDisposition() {
			return disposition;
		}
		public long getLifecycleVersionBefore() {
			return lifecycleVersionBefore;
		}
		public long getLifecycleVersionAfter() {
			return lifecycleVersionAfter;
		}
		public boolean isTerminallyConsumed() {
			return disposition
				== RestorationLifecycleDisposition.TERMINALLY_CONSUME;
		}
		public boolean isRuntimeHandleRetained() { return false; }
		public boolean isCallbackInvoked() { return false; }
		public boolean isEventReschedule() { return false; }
		public boolean isCommitToken() { return false; }
		public boolean isArrivalGate() { return false; }
		public boolean isLifecycleAuthority() { return false; }
	}

	@FunctionalInterface
	public interface ExecutionBoundaryOperation<T> {
		T execute();
	}

	@Override
	public Integer call() {
		try {
			doRun();
		} catch (Exception e) {
			LOGGER.error("Exception while executing GameTickEvent call()", e);
			stop();
			return 1;
		}
		return 0;
	}

	public final boolean shouldRun() {
		synchronized (timingLock) {
			return running && ticksBeforeRun <= 0;
		}
	}

	public void stop() {
		beginOwnerPreservationLifecycleOperation();
		try {
			synchronized (timingLock) {
				if (running) {
					requireLifecycleVersionAvailable();
					running = false;
					advanceLifecycleVersion();
				}
			}
		} finally {
			endOwnerPreservationLifecycleOperation();
		}
	}

	public boolean isRunning() {
		return running;
	}

	protected void setDelayTicks(long delayTicks) {
		beginOwnerPreservationLifecycleOperation();
		try {
			synchronized (timingLock) {
				requireLifecycleVersionAvailable();
				this.delayTicks = delayTicks;
				ticksBeforeRun = delayTicks;
				advanceLifecycleVersion();
			}
		} finally {
			endOwnerPreservationLifecycleOperation();
		}
	}

	public void resetCountdown() {
		beginOwnerPreservationLifecycleOperation();
		try {
			synchronized (timingLock) {
				requireLifecycleVersionAvailable();
				ticksBeforeRun = delayTicks;
				advanceLifecycleVersion();
			}
		} finally {
			endOwnerPreservationLifecycleOperation();
		}
	}

	public void tick() {
		beginOwnerPreservationLifecycleOperation();
		try {
			synchronized (timingLock) {
				requireLifecycleVersionAvailable();
				ticksBeforeRun--;
				advanceLifecycleVersion();
			}
		} finally {
			endOwnerPreservationLifecycleOperation();
		}
	}

	public long timeTillNextRun() {
		synchronized (timingLock) {
			return System.currentTimeMillis()
				+ (ticksBeforeRun * getWorld().getServer().getConfig().GAME_TICK);
		}
	}

	public final boolean shouldRemove() {
		return !running;
	}

	public boolean belongsTo(Mob owner2) {
		return owner != null && owner.equals(owner2);
	}

	public Mob getOwner() {
		return owner;
	}

	public boolean hasOwner() {
		return owner != null;
	}

	protected Player getPlayerOwner() {
		return owner != null && owner.isPlayer() ? (Player) owner : null;
	}

	public int getPriority() {
		final Player owner = getPlayerOwner();
		if (owner == null)
			return -1;
		return owner.getIndex();
	}

	public Npc getNpcOwner() {
		return owner != null && owner.isNpc() ? (Npc) owner : null;
	}

	public long getTicksBeforeRun() {
		synchronized (timingLock) {
			return ticksBeforeRun;
		}
	}

	public final long getLastEventDuration() {
		return lastEventDuration;
	}

	public long getDelayTicks() {
		synchronized (timingLock) {
			return delayTicks;
		}
	}

	public String getDescriptor() {
		return descriptor;
	}

	protected void setDescriptor(final String descriptor) {
		this.descriptor = descriptor;
	}

	public World getWorld() {
		return world;
	}

	public DuplicationStrategy getDuplicationStrategy() {
		return duplicationStrategy;
	}

	public UUID getUUID() {
		return uuid;
	}

	public int getTimesRan() {
		return timesRan;
	}

	/**
	 * Captures the smallest replay-relevant timing tuple under one event-local
	 * lifecycle lock. Registration identity and the observing server tick are
	 * bound by the scheduler store; this value has no mutation capability.
	 */
	public final AtomicTimingSnapshot captureAtomicTimingSnapshot() {
		beginOwnerPreservationLifecycleOperation();
		try {
			synchronized (timingLock) {
				return new AtomicTimingSnapshot(
					running, ticksBeforeRun, timesRan, lifecycleVersion);
			}
		} finally {
			endOwnerPreservationLifecycleOperation();
		}
	}

	private void requireLifecycleVersionAvailable() {
		if (lifecycleVersion == Long.MAX_VALUE) {
			throw new IllegalStateException(
				"Event lifecycle version exhausted");
		}
	}

	private void advanceLifecycleVersion() {
		lifecycleVersion++;
	}

	/** Immutable, detached event-local timing evidence. */
	public static final class AtomicTimingSnapshot {
		private final boolean running;
		private final long ticksBeforeRun;
		private final int timesRan;
		private final long lifecycleVersion;

		private AtomicTimingSnapshot(
			final boolean running,
			final long ticksBeforeRun,
			final int timesRan,
			final long lifecycleVersion) {
			this.running = running;
			this.ticksBeforeRun = ticksBeforeRun;
			this.timesRan = timesRan;
			this.lifecycleVersion = lifecycleVersion;
		}

		public boolean isRunning() { return running; }
		public long getTicksBeforeRun() { return ticksBeforeRun; }
		public int getTimesRan() { return timesRan; }
		public long getLifecycleVersion() { return lifecycleVersion; }
	}

	/**
	 * Declares spatial effect only when the event implementation can do so
	 * explicitly. Owner position is classified separately by diagnostics.
	 */
	public GameTickEventSpatialAffinity getSpatialAffinity() {
		return GameTickEventSpatialAffinity.unspecified();
	}

	/**
	 * Declares detached callback inputs only when an event implementation can do
	 * so explicitly. The value is inert and is not read by the scheduler.
	 */
	public GameTickEventRestorationState getRestorationState() {
		return GameTickEventRestorationState.unavailable();
	}
}

/**
 * Per-event exclusion gate for a short owner-preservation observation.
 *
 * <p>Normal execution and timing operations increment an in-flight count
 * without retaining this monitor while arbitrary callback code runs. A
 * preservation boundary refuses instead of waiting when any required event is
 * active, then acquires an entire stable-order set iteratively so runtime
 * cardinality never becomes Java call-stack depth.</p>
 */
final class GameTickEventOwnerPreservationLifecycleGate {
	private final Object lock = new Object();
	private int operationsInProgress;
	private boolean preservationBoundaryActive;
	private Thread preservationBoundaryThread;

	static boolean withinPreservationBoundaries(
		final List<GameTickEventOwnerPreservationLifecycleGate> gates,
		final PreservationSetOperation operation) {
		if (gates == null) {
			throw new NullPointerException("gates");
		}
		if (operation == null) {
			throw new NullPointerException("operation");
		}
		IdentityHashMap<GameTickEventOwnerPreservationLifecycleGate, Boolean>
			seen =
				new IdentityHashMap<
					GameTickEventOwnerPreservationLifecycleGate, Boolean>();
		for (int index = 0; index < gates.size(); index++) {
			GameTickEventOwnerPreservationLifecycleGate gate =
				gates.get(index);
			if (gate == null) {
				throw new NullPointerException("gates[" + index + "]");
			}
			if (seen.put(gate, Boolean.TRUE) != null) {
				throw new IllegalArgumentException(
					"Event preservation gate set contains a duplicate");
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
						"Preservation boundary cannot invoke event lifecycle");
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
					"Event owner lifecycle operation count overflow");
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
					"Event owner lifecycle operation count underflow");
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
					"Event owner preservation lifecycle gate changed");
			}
			preservationBoundaryActive = false;
			preservationBoundaryThread = null;
			lock.notifyAll();
		}
	}

	@FunctionalInterface
	interface PreservationSetOperation {
		void execute(BoundarySet boundary);
	}

	/** Closed facts valid only while one complete iterative set is held. */
	static final class BoundarySet {
		private final int eventCount;

		private BoundarySet(final int eventCount) {
			if (eventCount < 0) {
				throw new IllegalArgumentException(
					"Event preservation boundary count must not be negative");
			}
			this.eventCount = eventCount;
		}

		int getEventCount() {
			return eventCount;
		}

		boolean isCompleteSetHeld() {
			return true;
		}
	}
}
