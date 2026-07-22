package com.openrsc.server.event.rsc.handler;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Key;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.PluginTickEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

class GameTickEventStore {
    private static final Logger LOGGER = LogManager.getLogger(GameTickEventStore.class);

    private final Object LOCK = new Object();

    /**
     * Tracks whether the event should be added using the criteria determined by the key
     */
    private final Map<GameTickKey, GameTickEvent> events = new LinkedHashMap<>();

    /**
     * Process-local identity for one accepted stay in this store. This is not
     * the event UUID or store key and is removed with the registration.
     */
    private final Map<GameTickEvent, Long> registrationSequences =
        new IdentityHashMap<>();
    private final String schedulerInstanceIdentity =
        UUID.randomUUID().toString();
    private long nextRegistrationSequence;
    private long registrationVersion;

	private static final int REMOVE_RETRY = 0;
	private static final int REMOVE_COMPLETE = 1;

    /**
     * Indexes events by username for fast-lookup during individual player tick processing
     */
    private final Multimap<Long, GameTickEvent> byUsernameHash = ArrayListMultimap.create();

    /**
     * Keep the non player events ready
     */
    private final Map<GameTickKey, GameTickEvent> nonPlayerEvents = new LinkedHashMap<>();

    /**
     * Index by event type to quickly know if a certain event type exists (i.e. instanceof)
     */
    private final Multimap<Key<? extends GameTickEvent>, GameTickEvent> byType = LinkedHashMultimap.create();

    public boolean add(GameTickEvent event) {
		final GameTickEvent checked = Objects.requireNonNull(event, "event");
		return checked.withinExecutionBoundary(() -> {
			synchronized (LOCK) {
				final GameTickKey eventKey = getKey(checked);
				if (events.containsKey(eventKey)) {
					// We already have an instance of this event.
					return false;
				}
				registerAccepted(eventKey, checked, checked);
				return true;
			}
		});
    }

	public boolean addOrUpdate(GameTickEvent event) {
		final GameTickEvent replacement = Objects.requireNonNull(event, "event");
		final GameTickKey eventKey = getKey(replacement);
		while (true) {
			final GameTickEvent existing;
			synchronized (LOCK) {
				existing = events.get(eventKey);
			}
			if (existing == null) {
				Boolean added = replacement.withinExecutionBoundary(() -> {
					synchronized (LOCK) {
						if (events.containsKey(eventKey)) {
							return null;
						}
						registerAccepted(
							eventKey, replacement, replacement);
						return Boolean.TRUE;
					}
				});
				if (added != null) {
					return added.booleanValue();
				}
				continue;
			}
			if (existing.isRunning()) {
				synchronized (LOCK) {
					if (events.get(eventKey) == existing) {
						return false;
					}
				}
				continue;
			}
			Boolean replaced = existing.withinExecutionBoundary(() -> {
					synchronized (LOCK) {
						if (events.get(eventKey) != existing) {
							return null;
						}
						if (existing.isRunning()) {
							return Boolean.FALSE;
						}
						unregisterAccepted(eventKey, existing);
						registerAccepted(
							eventKey, replacement, existing);
						return Boolean.TRUE;
					}
				});
			if (replaced != null) {
				return replaced.booleanValue();
			}
		}
	}

    public boolean eventIsContained(GameTickEvent event) {
		final GameTickKey eventKey = getKey(event);
		synchronized (LOCK) {
			return events.containsKey(eventKey);
		}
    }

    public void remove(GameTickEvent event) {
		final GameTickEvent requested = Objects.requireNonNull(event, "event");
		final GameTickKey eventKey = getKey(requested);
		boolean observedRegistration = false;
		while (true) {
			final GameTickEvent registered;
			synchronized (LOCK) {
				registered = events.get(eventKey);
			}
			if (registered == null) {
				if (!observedRegistration) {
					LOGGER.warn("Failed to remove event: {}", eventKey);
				}
				return;
			}
			observedRegistration = true;
			Integer result = registered.withinExecutionBoundary(() -> {
				synchronized (LOCK) {
					if (events.get(eventKey) != registered) {
						return Integer.valueOf(REMOVE_RETRY);
					}
					unregisterAccepted(eventKey, registered);
					return Integer.valueOf(REMOVE_COMPLETE);
				}
			});
			if (result.intValue() == REMOVE_COMPLETE) {
				return;
			}
		}
    }

    public Collection<GameTickEvent> getPlayerEvents(Player player) {
        synchronized (LOCK) {
            return new ArrayList<>(byUsernameHash.get(player.getUsernameHash()));
        }
    }

    public Collection<GameTickEvent> getPlayerEvents(Long usernameHash) {
        synchronized (LOCK) {
            return new ArrayList<>(byUsernameHash.get(usernameHash));
        }
    }

    public Collection<GameTickEvent> getNonPlayerEvents() {
        synchronized (LOCK) {
            return new ArrayList<>(nonPlayerEvents.values());
        }
    }

    public Collection<GameTickEvent> getEvents(Class<? extends GameTickEvent> type) {
        synchronized (LOCK) {
            return new ArrayList<>(byType.get(Key.get(type)));
        }
    }

    public boolean hasEvent(Class<? extends GameTickEvent> eventType) {
        synchronized (LOCK) {
            return byType.containsKey(Key.get(eventType));
        }
    }

    public Collection<GameTickEvent> getTrackedEvents() {
        synchronized (LOCK) {
            return new ArrayList<>(events.values());
        }
    }

    /** One atomic scheduler-local order/identity view for read-only capture. */
    List<RegisteredEvent> getTrackedEventRegistrations() {
        return getTrackedEventRegistrationSnapshot().getRegistrations();
    }

    /**
     * One atomic scheduler-instance/order/identity view for read-only capture.
     * The opaque instance identity scopes registration sequences to this store
     * lifetime; it is not an event UUID, key, callback, or scheduler handle.
     */
    RegistrationSnapshot getTrackedEventRegistrationSnapshot() {
        synchronized (LOCK) {
            List<RegisteredEvent> registrations =
                new ArrayList<>(events.size());
            for (GameTickEvent event : events.values()) {
                Long sequence = registrationSequences.get(event);
                if (sequence == null) {
                    throw new IllegalStateException(
                        "Tracked event has no registration identity");
                }
                registrations.add(new RegisteredEvent(
                    event, sequence.longValue()));
            }
            return new RegistrationSnapshot(
                schedulerInstanceIdentity,
                registrationVersion,
                Collections.unmodifiableList(registrations));
        }
    }

    /**
     * Captures registration identity and each event's timing tuple without
     * holding the store lock while acquiring an event lifecycle lock. A
     * changed registration set refuses the whole snapshot rather than mixing
     * timing from one accepted stay with another.
     */
    StoreAtomicTimingSnapshot getTrackedEventAtomicTimingSnapshot(
        final long observedAtTick) {
        if (observedAtTick < 0L) {
            throw new IllegalArgumentException(
                "Atomic timing observation tick must be non-negative");
        }
        RegistrationSnapshot registrations =
            getTrackedEventRegistrationSnapshot();
        List<AtomicTimedRegisteredEvent> timedRegistrations =
            new ArrayList<>(registrations.getRegistrations().size());
        for (RegisteredEvent registration : registrations.getRegistrations()) {
            timedRegistrations.add(new AtomicTimedRegisteredEvent(
                registration.getEvent(),
                registration.getRegistrationSequence(),
                registration.getEvent().captureAtomicTimingSnapshot()));
        }
        synchronized (LOCK) {
            if (registrationVersion != registrations.getRegistrationVersion()) {
                throw new IllegalStateException(
                    "Scheduler registrations changed during atomic timing snapshot");
            }
            return new StoreAtomicTimingSnapshot(
                registrations.getSchedulerInstanceIdentity(), observedAtTick,
                Collections.unmodifiableList(timedRegistrations));
        }
    }

	/**
	 * Runs one internal operation behind a validated scheduler-registration
	 * fence. The event execution boundary is acquired before the store monitor;
	 * the store monitor is released before the operation begins. Because every
	 * registration mutation follows the same outer-to-inner order, removal or
	 * replacement cannot invalidate the accepted registration until the
	 * operation returns.
	 *
	 * <p>The detached fence is not a commit token and is stale after the
	 * operation. This seam does not inspect a Region, invoke the event callback,
	 * or perform restoration.</p>
	 */
	RegistrationFenceExecution withValidatedRegistrationFence(
		final GameTickEvent event,
		final String expectedSchedulerInstanceIdentity,
		final long expectedRegistrationSequence,
		final RegistrationFenceOperation operation) {
		final GameTickEvent checked = Objects.requireNonNull(event, "event");
		if (expectedSchedulerInstanceIdentity == null
			|| expectedSchedulerInstanceIdentity.isEmpty()
			|| expectedRegistrationSequence <= 0L) {
			throw new IllegalArgumentException(
				"Expected scheduler registration is invalid");
		}
		final RegistrationFenceOperation checkedOperation =
			Objects.requireNonNull(operation, "operation");
		return checked.withinExecutionBoundary(() -> {
			final long observedRegistrationSequence;
			synchronized (LOCK) {
				if (!schedulerInstanceIdentity.equals(
					expectedSchedulerInstanceIdentity)) {
					return RegistrationFenceExecution.refused(
						RegistrationFenceReason
							.SCHEDULER_INSTANCE_MISMATCH);
				}
				Long observed = registrationSequences.get(checked);
				if (events.get(getKey(checked)) != checked
					|| observed == null) {
					return RegistrationFenceExecution.refused(
						RegistrationFenceReason.EVENT_NOT_REGISTERED);
				}
				observedRegistrationSequence = observed.longValue();
				if (observedRegistrationSequence
					!= expectedRegistrationSequence) {
					return RegistrationFenceExecution.refused(
						RegistrationFenceReason
							.REGISTRATION_SEQUENCE_MISMATCH);
				}
			}
			RegistrationFence fence = new RegistrationFence(
				schedulerInstanceIdentity, observedRegistrationSequence,
				checked.isExecutionBoundaryHeldByCurrentThread(),
				Thread.holdsLock(LOCK), true);
			checkedOperation.execute(fence);
			return RegistrationFenceExecution.accepted(fence);
		});
	}

    private void registerAccepted(
        final GameTickKey eventKey,
        final GameTickEvent event,
		final GameTickEvent mutationBoundaryEvent) {
		if (!Thread.holdsLock(LOCK)
			|| !mutationBoundaryEvent
				.isExecutionBoundaryHeldByCurrentThread()) {
			throw new IllegalStateException(
				"Registration requires event boundary before store boundary");
		}
        if (nextRegistrationSequence == Long.MAX_VALUE) {
            throw new IllegalStateException(
                "Event registration identity exhausted");
        }
        if (registrationVersion == Long.MAX_VALUE) {
            throw new IllegalStateException(
                "Event registration version exhausted");
        }
        long registrationSequence = nextRegistrationSequence + 1L;
        events.put(eventKey, event);
        byType.put(Key.get(event.getClass()), event);
        if (isPlayerOwner(event)) {
            byUsernameHash.put(
                ((Player) event.getOwner()).getUsernameHash(), event);
        } else {
            nonPlayerEvents.put(eventKey, event);
        }
        registrationSequences.put(event, Long.valueOf(registrationSequence));
        nextRegistrationSequence = registrationSequence;
        advanceRegistrationVersion();
    }

	private void unregisterAccepted(
		final GameTickKey eventKey,
		final GameTickEvent registeredEvent) {
		if (!Thread.holdsLock(LOCK)
			|| !registeredEvent.isExecutionBoundaryHeldByCurrentThread()) {
			throw new IllegalStateException(
				"Removal requires event boundary before store boundary");
		}
		if (registrationVersion == Long.MAX_VALUE) {
			throw new IllegalStateException(
				"Event registration version exhausted");
		}
		events.remove(eventKey);
		byType.remove(
			Key.get(registeredEvent.getClass()), registeredEvent);
		if (isPlayerOwner(registeredEvent)) {
			byUsernameHash.remove(
				((Player) registeredEvent.getOwner()).getUsernameHash(),
				registeredEvent);
		} else {
			nonPlayerEvents.remove(eventKey);
		}
		registrationSequences.remove(registeredEvent);
		advanceRegistrationVersion();
	}

    private void advanceRegistrationVersion() {
        if (registrationVersion == Long.MAX_VALUE) {
            throw new IllegalStateException(
                "Event registration version exhausted");
        }
        registrationVersion++;
    }

    /** Internal handle/identity pair; no reference crosses diagnostics. */
    static final class RegisteredEvent {
        private final GameTickEvent event;
        private final long registrationSequence;

        private RegisteredEvent(
            final GameTickEvent event,
            final long registrationSequence) {
            this.event = event;
            this.registrationSequence = registrationSequence;
        }

        GameTickEvent getEvent() {
            return event;
        }

        long getRegistrationSequence() {
            return registrationSequence;
        }
    }

    /** Immutable store-lifetime identity plus one atomic registration view. */
    static final class RegistrationSnapshot {
        private final String schedulerInstanceIdentity;
        private final long registrationVersion;
        private final List<RegisteredEvent> registrations;

        private RegistrationSnapshot(
            final String schedulerInstanceIdentity,
            final long registrationVersion,
            final List<RegisteredEvent> registrations) {
            this.schedulerInstanceIdentity = schedulerInstanceIdentity;
            this.registrationVersion = registrationVersion;
            this.registrations = registrations;
        }

        String getSchedulerInstanceIdentity() {
            return schedulerInstanceIdentity;
        }

        long getRegistrationVersion() {
            return registrationVersion;
        }

        List<RegisteredEvent> getRegistrations() {
            return registrations;
        }
    }

    /** Internal registration plus a detached event-local timing tuple. */
    static final class AtomicTimedRegisteredEvent {
        private final GameTickEvent event;
        private final long registrationSequence;
        private final GameTickEvent.AtomicTimingSnapshot timing;

        private AtomicTimedRegisteredEvent(
            final GameTickEvent event,
            final long registrationSequence,
            final GameTickEvent.AtomicTimingSnapshot timing) {
            this.event = event;
            this.registrationSequence = registrationSequence;
            this.timing = timing;
        }

        GameTickEvent getEvent() { return event; }
        long getRegistrationSequence() { return registrationSequence; }
        GameTickEvent.AtomicTimingSnapshot getTiming() { return timing; }
    }

    /** Immutable store-scope/tick/registration/timing observation. */
    static final class StoreAtomicTimingSnapshot {
        private final String schedulerInstanceIdentity;
        private final long observedAtTick;
        private final List<AtomicTimedRegisteredEvent> registrations;

        private StoreAtomicTimingSnapshot(
            final String schedulerInstanceIdentity,
            final long observedAtTick,
            final List<AtomicTimedRegisteredEvent> registrations) {
            this.schedulerInstanceIdentity = schedulerInstanceIdentity;
            this.observedAtTick = observedAtTick;
            this.registrations = registrations;
        }

        String getSchedulerInstanceIdentity() {
            return schedulerInstanceIdentity;
        }

        long getObservedAtTick() { return observedAtTick; }

        List<AtomicTimedRegisteredEvent> getRegistrations() {
            return registrations;
        }
    }

	@FunctionalInterface
	interface RegistrationFenceOperation {
		void execute(RegistrationFence fence);
	}

	/** Detached facts that are valid only during the supplied operation. */
	static final class RegistrationFence {
		private final String schedulerInstanceIdentity;
		private final long registrationSequence;
		private final boolean eventExecutionBoundaryHeld;
		private final boolean schedulerStoreBoundaryHeld;
		private final boolean registrationValidatedBeforeInnerBoundary;

		private RegistrationFence(
			final String schedulerInstanceIdentity,
			final long registrationSequence,
			final boolean eventExecutionBoundaryHeld,
			final boolean schedulerStoreBoundaryHeld,
			final boolean registrationValidatedBeforeInnerBoundary) {
			this.schedulerInstanceIdentity = schedulerInstanceIdentity;
			this.registrationSequence = registrationSequence;
			this.eventExecutionBoundaryHeld = eventExecutionBoundaryHeld;
			this.schedulerStoreBoundaryHeld = schedulerStoreBoundaryHeld;
			this.registrationValidatedBeforeInnerBoundary =
				registrationValidatedBeforeInnerBoundary;
			if (schedulerInstanceIdentity == null
				|| schedulerInstanceIdentity.isEmpty()
				|| registrationSequence <= 0L
				|| !eventExecutionBoundaryHeld
				|| schedulerStoreBoundaryHeld
				|| !registrationValidatedBeforeInnerBoundary) {
				throw new IllegalStateException(
					"Accepted scheduler-registration fence is invalid");
			}
		}

		String getSchedulerInstanceIdentity() {
			return schedulerInstanceIdentity;
		}
		long getRegistrationSequence() { return registrationSequence; }
		boolean isEventExecutionBoundaryHeld() {
			return eventExecutionBoundaryHeld;
		}
		boolean isSchedulerStoreBoundaryHeld() {
			return schedulerStoreBoundaryHeld;
		}
		boolean isRegistrationValidatedBeforeInnerBoundary() {
			return registrationValidatedBeforeInnerBoundary;
		}
	}

	enum RegistrationFenceReason {
		SCHEDULER_INSTANCE_MISMATCH,
		EVENT_NOT_REGISTERED,
		REGISTRATION_SEQUENCE_MISMATCH,
		OPERATION_COMPLETED
	}

	/** Accepted/refused result; neither the event nor the store is retained. */
	static final class RegistrationFenceExecution {
		private final RegistrationFenceReason reason;
		private final RegistrationFence fence;

		private RegistrationFenceExecution(
			final RegistrationFenceReason reason,
			final RegistrationFence fence) {
			this.reason = Objects.requireNonNull(reason, "reason");
			this.fence = fence;
			if ((reason == RegistrationFenceReason.OPERATION_COMPLETED)
				!= (fence != null)) {
				throw new IllegalArgumentException(
					"Registration-fence result is inconsistent");
			}
		}

		private static RegistrationFenceExecution refused(
			final RegistrationFenceReason reason) {
			return new RegistrationFenceExecution(reason, null);
		}
		private static RegistrationFenceExecution accepted(
			final RegistrationFence fence) {
			return new RegistrationFenceExecution(
				RegistrationFenceReason.OPERATION_COMPLETED, fence);
		}

		boolean isAccepted() {
			return reason == RegistrationFenceReason.OPERATION_COMPLETED;
		}
		RegistrationFenceReason getReason() { return reason; }
		RegistrationFence getFence() { return fence; }
		boolean isCommitToken() { return false; }
		boolean isMutationPerformed() { return false; }
		boolean isExecutableRestoration() { return false; }
		boolean isArrivalGate() { return false; }
		boolean isLifecycleAuthority() { return false; }
	}

    private boolean isPlayerOwner(GameTickEvent event) {
        return event.hasOwner() && event.getOwner() instanceof Player;
    }

    private GameTickKey getKey(GameTickEvent event) {
        if(event instanceof PluginTickEvent) {
            return new GameTickKey((PluginTickEvent) event);
        }
        return new GameTickKey(event);
    }

    class GameTickKey {
        private final String name;
        private final Boolean isPlayerEvent;
        private final UUID ownerUUID;

        private GameTickKey(GameTickEvent event) {
            this.name = resolveName(event);
            this.isPlayerEvent = isPlayerOwner(event);
            this.ownerUUID = resolveUUID(event);
        }

        private String resolveName(GameTickEvent event) {
            if(event instanceof PluginTickEvent) {
                return ((PluginTickEvent) event).getPluginName();
            }

            return String.valueOf(event.getClass());
        }

        private UUID resolveUUID(GameTickEvent event) {
            DuplicationStrategy strategy = event.getDuplicationStrategy();
            if(strategy == DuplicationStrategy.ALLOW_MULTIPLE) {
                return event.getUUID();
            } else if(strategy == DuplicationStrategy.ONE_PER_SERVER) {
                return UUID.nameUUIDFromBytes(resolveName(event).getBytes());
            } else if(strategy == DuplicationStrategy.ONE_PER_MOB) {
                return Optional.ofNullable(event.getOwner())
                        .map(Mob::getUUID)
                        .orElse(event.getUUID());
            }

            throw new IllegalArgumentException(
                    MessageFormat.format("Unknown duplication strategy {0}", strategy)
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GameTickKey that = (GameTickKey) o;
            return new EqualsBuilder()
                    .append(name, that.name)
                    .append(isPlayerEvent, that.isPlayerEvent)
                    .append(ownerUUID, that.ownerUUID).isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37)
                    .append(name)
                    .append(isPlayerEvent)
                    .append(ownerUUID)
                    .toHashCode();
        }
    }
}
