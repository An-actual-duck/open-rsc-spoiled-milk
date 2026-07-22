package com.openrsc.server.event.rsc;

import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.concurrent.Callable;

public abstract class GameTickEvent implements Callable<Integer> {
	/**
	 * Logger instance
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	private final Object executionLock = new Object();
	private final Object timingLock = new Object();
	protected volatile boolean running = true;
	private final Mob owner;
	private final World world;
	private volatile long delayTicks;
	private volatile long ticksBeforeRun = -1;
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
		synchronized (executionLock) {
			return operation.execute();
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
		return running && ticksBeforeRun <= 0;
	}

	public void stop() {
		synchronized (timingLock) {
			if (running) {
				requireLifecycleVersionAvailable();
				running = false;
				advanceLifecycleVersion();
			}
		}
	}

	public boolean isRunning() {
		return running;
	}

	protected void setDelayTicks(long delayTicks) {
		synchronized (timingLock) {
			requireLifecycleVersionAvailable();
			this.delayTicks = delayTicks;
			ticksBeforeRun = delayTicks;
			advanceLifecycleVersion();
		}
	}

	public void resetCountdown() {
		synchronized (timingLock) {
			requireLifecycleVersionAvailable();
			ticksBeforeRun = delayTicks;
			advanceLifecycleVersion();
		}
	}

	public void tick() {
		synchronized (timingLock) {
			requireLifecycleVersionAvailable();
			ticksBeforeRun--;
			advanceLifecycleVersion();
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
		return ticksBeforeRun;
	}

	public final long getLastEventDuration() {
		return lastEventDuration;
	}

	public long getDelayTicks() {
		return delayTicks;
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
		synchronized (timingLock) {
			return new AtomicTimingSnapshot(
				running, ticksBeforeRun, timesRan, lifecycleVersion);
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
