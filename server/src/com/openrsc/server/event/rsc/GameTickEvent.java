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
					ticksBeforeRun--;
					runNow = running && ticksBeforeRun <= 0;
				}
				if (runNow) {
					// Never hold the timing monitor across arbitrary callback code.
					// Callbacks may own plugin or entity monitors while diagnostics
					// capture this event's detached timing tuple.
					run();
					synchronized (timingLock) {
						timesRan++;
						ticksBeforeRun = delayTicks;
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
			running = false;
		}
	}

	public boolean isRunning() {
		return running;
	}

	protected void setDelayTicks(long delayTicks) {
		synchronized (timingLock) {
			this.delayTicks = delayTicks;
			resetCountdown();
		}
	}

	public void resetCountdown() {
		synchronized (timingLock) {
			ticksBeforeRun = delayTicks;
		}
	}

	public void tick() {
		synchronized (timingLock) {
			ticksBeforeRun--;
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
				running, ticksBeforeRun, timesRan);
		}
	}

	/** Immutable, detached event-local timing evidence. */
	public static final class AtomicTimingSnapshot {
		private final boolean running;
		private final long ticksBeforeRun;
		private final int timesRan;

		private AtomicTimingSnapshot(
			final boolean running,
			final long ticksBeforeRun,
			final int timesRan) {
			this.running = running;
			this.ticksBeforeRun = ticksBeforeRun;
			this.timesRan = timesRan;
		}

		public boolean isRunning() { return running; }
		public long getTicksBeforeRun() { return ticksBeforeRun; }
		public int getTimesRan() { return timesRan; }
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
