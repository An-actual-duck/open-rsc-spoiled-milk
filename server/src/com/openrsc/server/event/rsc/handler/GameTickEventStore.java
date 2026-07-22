package com.openrsc.server.event.rsc.handler;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Key;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.GameTickEventRestorationState;
import com.openrsc.server.event.rsc.GameTickEventSpatialAffinity;
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

	/**
	 * Locates one exact registration internally and validates its authored
	 * scenery generation behind the registration fence. The caller supplies no
	 * event handle, and the operation receives only closed detached scalars.
	 */
	RestorationRegistrationFenceExecution
		withValidatedRestorationRegistrationFence(
			final String expectedSchedulerInstanceIdentity,
			final long expectedRegistrationSequence,
			final long expectedProposalGeneration,
			final RestorationRegistrationFenceOperation operation) {
		if (expectedSchedulerInstanceIdentity == null
			|| expectedSchedulerInstanceIdentity.isEmpty()
			|| expectedRegistrationSequence <= 0L
			|| expectedProposalGeneration <= 0L) {
			throw new IllegalArgumentException(
				"Expected restoration registration is invalid");
		}
		final RestorationRegistrationFenceOperation checkedOperation =
			Objects.requireNonNull(operation, "operation");
		final GameTickEvent candidate;
		synchronized (LOCK) {
			if (!schedulerInstanceIdentity.equals(
				expectedSchedulerInstanceIdentity)) {
				return RestorationRegistrationFenceExecution.refused(
					RestorationRegistrationFenceReason
						.SCHEDULER_INSTANCE_MISMATCH);
			}
			GameTickEvent found = null;
			for (Map.Entry<GameTickEvent, Long> registration
				: registrationSequences.entrySet()) {
				Long sequence = registration.getValue();
				if (sequence != null
					&& sequence.longValue()
						== expectedRegistrationSequence) {
					if (found != null) {
						return RestorationRegistrationFenceExecution.refused(
							RestorationRegistrationFenceReason
								.DUPLICATE_REGISTRATION_SEQUENCE);
					}
					found = registration.getKey();
				}
			}
			if (found == null || events.get(getKey(found)) != found) {
				return RestorationRegistrationFenceExecution.refused(
					RestorationRegistrationFenceReason.EVENT_NOT_REGISTERED);
			}
			candidate = found;
		}
		final RestorationRegistrationFenceExecution[] execution =
			new RestorationRegistrationFenceExecution[1];
		RegistrationFenceExecution registrationExecution =
			withValidatedRegistrationFence(
				candidate, expectedSchedulerInstanceIdentity,
				expectedRegistrationSequence, registrationFence ->
					execution[0] = executeRestorationRegistrationFence(
						candidate, registrationFence,
						expectedProposalGeneration, checkedOperation));
		if (!registrationExecution.isAccepted()) {
			return RestorationRegistrationFenceExecution.refused(
				mapRegistrationFenceReason(
					registrationExecution.getReason()));
		}
		if (execution[0] == null) {
			throw new IllegalStateException(
				"Accepted restoration registration did not execute");
		}
		return execution[0];
	}

	private static RestorationRegistrationFenceExecution
		executeRestorationRegistrationFence(
			final GameTickEvent event,
			final RegistrationFence registrationFence,
			final long expectedProposalGeneration,
			final RestorationRegistrationFenceOperation operation) {
		GameTickEventRestorationState state = Objects.requireNonNull(
			event.getRestorationState(), "event restoration state");
		if (state.getKind()
			== GameTickEventRestorationState.Kind.UNAVAILABLE) {
			return RestorationRegistrationFenceExecution.refused(
				RestorationRegistrationFenceReason
					.RESTORATION_STATE_UNAVAILABLE);
		}
		GameTickEvent.AtomicTimingSnapshot timing =
			event.captureAtomicTimingSnapshot();
		if (!timing.isRunning()) {
			return RestorationRegistrationFenceExecution.refused(
				RestorationRegistrationFenceReason.EVENT_NOT_RUNNING);
		}
		if (timing.getTimesRan() != 0) {
			return RestorationRegistrationFenceExecution.refused(
				RestorationRegistrationFenceReason.EVENT_ALREADY_EXECUTED);
		}
		GameTickEventRestorationState.SceneryState scenery =
			state.getScenery();
		if (scenery == null || !state.isDetachedCallbackPayloadComplete()) {
			return RestorationRegistrationFenceExecution.refused(
				RestorationRegistrationFenceReason
					.RESTORATION_PAYLOAD_INCOMPLETE);
		}
		GameTickEventRestorationState.AuthoredPlacementState authored =
			scenery.getAuthoredPlacement();
		if (authored == null) {
			return RestorationRegistrationFenceExecution.refused(
				RestorationRegistrationFenceReason
					.AUTHORED_IDENTITY_MISSING);
		}
		if (scenery.hasOwner()) {
			return RestorationRegistrationFenceExecution.refused(
				RestorationRegistrationFenceReason
					.OWNER_BOUND_STATE_REFUSED);
		}
		if (scenery.getRuntimeAttributeCount() != 0) {
			return RestorationRegistrationFenceExecution.refused(
				RestorationRegistrationFenceReason
					.RUNTIME_ATTRIBUTE_STATE_INCOMPLETE);
		}
		if (!matchesSceneryConstructionKind(
			authored.getConstructionKind(), scenery.getType())) {
			return RestorationRegistrationFenceExecution.refused(
				RestorationRegistrationFenceReason
					.AUTHORED_CONSTRUCTION_KIND_MISMATCH);
		}
		GameTickEventSpatialAffinity affinity = Objects.requireNonNull(
			event.getSpatialAffinity(), "event spatial affinity");
		if (!matchesExactSceneryAffinity(affinity, scenery)) {
			return RestorationRegistrationFenceExecution.refused(
				RestorationRegistrationFenceReason
					.SPATIAL_AFFINITY_MISMATCH);
		}
		if (authored.getGeneration() != expectedProposalGeneration) {
			return RestorationRegistrationFenceExecution.refused(
				RestorationRegistrationFenceReason
					.PROPOSAL_GENERATION_MISMATCH);
		}
		RestorationRegistrationFence fence =
			new RestorationRegistrationFence(
				registrationFence.getSchedulerInstanceIdentity(),
				registrationFence.getRegistrationSequence(),
				registrationFence.isEventExecutionBoundaryHeld(),
				registrationFence.isSchedulerStoreBoundaryHeld(),
				registrationFence
					.isRegistrationValidatedBeforeInnerBoundary(),
				RestorationKind.valueOf(state.getKind().name()),
				expectedProposalGeneration, authored.getGeneration(),
				timing.getTicksBeforeRun(), timing.getTimesRan(),
				timing.getLifecycleVersion(),
				scenery.getObjectId(), scenery.getPermanentObjectId(),
				scenery.getX(), scenery.getY(), scenery.getDirection(),
				scenery.getType(), state.isForceFullBlock(),
				authored.getPackedRegionX(), authored.getPackedRegionY(),
				authored.getSourceOrdinal(),
				AuthoredConstructionKind.valueOf(
					authored.getConstructionKind().name()));
		operation.execute(fence);
		GameTickEvent.AtomicTimingSnapshot after =
			event.captureAtomicTimingSnapshot();
		if (after.getLifecycleVersion() != timing.getLifecycleVersion()) {
			return RestorationRegistrationFenceExecution.refusedAfterOperation(
				RestorationRegistrationFenceReason
					.EVENT_LIFECYCLE_CHANGED_DURING_OPERATION,
				timing.getLifecycleVersion(), after.getLifecycleVersion());
		}
		return RestorationRegistrationFenceExecution.accepted(
			fence, timing.getLifecycleVersion());
	}

	private static boolean matchesExactSceneryAffinity(
		final GameTickEventSpatialAffinity affinity,
		final GameTickEventRestorationState.SceneryState scenery) {
		if (affinity.getScope()
			!= GameTickEventSpatialAffinity.Scope.EXACT_SPATIAL
			|| affinity.getReferences().size() != 1) {
			return false;
		}
		GameTickEventSpatialAffinity.Reference reference =
			affinity.getReferences().get(0);
		return reference.getRole()
				== GameTickEventSpatialAffinity.Role.FIXED_EFFECT_LOCATION
			&& reference.getX() == scenery.getX()
			&& reference.getY() == scenery.getY();
	}

	private static boolean matchesSceneryConstructionKind(
		final GameTickEventRestorationState.AuthoredConstructionKind kind,
		final int objectType) {
		return (kind
				== GameTickEventRestorationState.AuthoredConstructionKind
					.SCENERY
			|| kind
				== GameTickEventRestorationState.AuthoredConstructionKind
					.HARVESTING_SCENERY)
			? objectType == 0
			: kind
				== GameTickEventRestorationState.AuthoredConstructionKind
					.BOUNDARY
				&& objectType == 1;
	}

	private static RestorationRegistrationFenceReason
		mapRegistrationFenceReason(final RegistrationFenceReason reason) {
		switch (reason) {
			case SCHEDULER_INSTANCE_MISMATCH:
				return RestorationRegistrationFenceReason
					.SCHEDULER_INSTANCE_MISMATCH;
			case EVENT_NOT_REGISTERED:
				return RestorationRegistrationFenceReason
					.EVENT_NOT_REGISTERED;
			case REGISTRATION_SEQUENCE_MISMATCH:
				return RestorationRegistrationFenceReason
					.REGISTRATION_SEQUENCE_MISMATCH;
			default:
				throw new IllegalStateException(
					"Accepted registration fence cannot be mapped as refusal");
		}
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

	@FunctionalInterface
	interface RestorationRegistrationFenceOperation {
		void execute(RestorationRegistrationFence fence);
	}

	enum RestorationRegistrationFenceReason {
		SCHEDULER_INSTANCE_MISMATCH,
		EVENT_NOT_REGISTERED,
		DUPLICATE_REGISTRATION_SEQUENCE,
		REGISTRATION_SEQUENCE_MISMATCH,
		RESTORATION_STATE_UNAVAILABLE,
		EVENT_NOT_RUNNING,
		EVENT_ALREADY_EXECUTED,
		RESTORATION_PAYLOAD_INCOMPLETE,
		AUTHORED_IDENTITY_MISSING,
		OWNER_BOUND_STATE_REFUSED,
		RUNTIME_ATTRIBUTE_STATE_INCOMPLETE,
		AUTHORED_CONSTRUCTION_KIND_MISMATCH,
		SPATIAL_AFFINITY_MISMATCH,
		PROPOSAL_GENERATION_MISMATCH,
		EVENT_LIFECYCLE_CHANGED_DURING_OPERATION,
		OPERATION_COMPLETED
	}

	enum RestorationKind {
		SCENERY_SPAWN,
		SCENERY_REMOVE
	}

	enum AuthoredConstructionKind {
		SCENERY,
		BOUNDARY,
		NPC_SPAWN,
		GROUND_ITEM_SPAWN,
		HARVESTING_SCENERY
	}

	/**
	 * Detached live callback facts proven while the execution/registration
	 * fence is held. No owner text or runtime handle is retained.
	 */
	static final class RestorationRegistrationFence {
		private final String schedulerInstanceIdentity;
		private final long registrationSequence;
		private final boolean eventExecutionBoundaryHeld;
		private final boolean schedulerStoreBoundaryHeld;
		private final boolean registrationValidatedBeforeInnerBoundary;
		private final RestorationKind restorationKind;
		private final long expectedProposalGeneration;
		private final long observedAuthoredGeneration;
		private final long ticksBeforeRun;
		private final int timesRan;
		private final long lifecycleVersion;
		private final int objectId;
		private final int permanentObjectId;
		private final int x;
		private final int y;
		private final int direction;
		private final int type;
		private final boolean forceFullBlock;
		private final int authoredPackedRegionX;
		private final int authoredPackedRegionY;
		private final int authoredSourceOrdinal;
		private final AuthoredConstructionKind authoredConstructionKind;

		private RestorationRegistrationFence(
			final String schedulerInstanceIdentity,
			final long registrationSequence,
			final boolean eventExecutionBoundaryHeld,
			final boolean schedulerStoreBoundaryHeld,
			final boolean registrationValidatedBeforeInnerBoundary,
			final RestorationKind restorationKind,
			final long expectedProposalGeneration,
			final long observedAuthoredGeneration,
			final long ticksBeforeRun,
			final int timesRan,
			final long lifecycleVersion,
			final int objectId,
			final int permanentObjectId,
			final int x,
			final int y,
			final int direction,
			final int type,
			final boolean forceFullBlock,
			final int authoredPackedRegionX,
			final int authoredPackedRegionY,
			final int authoredSourceOrdinal,
			final AuthoredConstructionKind authoredConstructionKind) {
			this.schedulerInstanceIdentity = schedulerInstanceIdentity;
			this.registrationSequence = registrationSequence;
			this.eventExecutionBoundaryHeld = eventExecutionBoundaryHeld;
			this.schedulerStoreBoundaryHeld = schedulerStoreBoundaryHeld;
			this.registrationValidatedBeforeInnerBoundary =
				registrationValidatedBeforeInnerBoundary;
			this.restorationKind = Objects.requireNonNull(
				restorationKind, "restorationKind");
			this.expectedProposalGeneration = expectedProposalGeneration;
			this.observedAuthoredGeneration = observedAuthoredGeneration;
			this.ticksBeforeRun = ticksBeforeRun;
			this.timesRan = timesRan;
			this.lifecycleVersion = lifecycleVersion;
			this.objectId = objectId;
			this.permanentObjectId = permanentObjectId;
			this.x = x;
			this.y = y;
			this.direction = direction;
			this.type = type;
			this.forceFullBlock = forceFullBlock;
			this.authoredPackedRegionX = authoredPackedRegionX;
			this.authoredPackedRegionY = authoredPackedRegionY;
			this.authoredSourceOrdinal = authoredSourceOrdinal;
			this.authoredConstructionKind = Objects.requireNonNull(
				authoredConstructionKind, "authoredConstructionKind");
			if (schedulerInstanceIdentity == null
				|| schedulerInstanceIdentity.isEmpty()
				|| registrationSequence <= 0L
				|| !eventExecutionBoundaryHeld
				|| schedulerStoreBoundaryHeld
				|| !registrationValidatedBeforeInnerBoundary
				|| expectedProposalGeneration <= 0L
				|| observedAuthoredGeneration
					!= expectedProposalGeneration
				|| timesRan != 0
				|| lifecycleVersion <= 0L
				|| objectId < 0 || permanentObjectId < 0
				|| x < 0 || y < 0
				|| direction < 0 || direction > 7
				|| (type != 0 && type != 1)
				|| authoredPackedRegionX < 0
				|| authoredPackedRegionY < 0
				|| authoredSourceOrdinal <= 0) {
				throw new IllegalArgumentException(
					"Restoration registration fence is invalid");
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
		RestorationKind getRestorationKind() { return restorationKind; }
		long getExpectedProposalGeneration() {
			return expectedProposalGeneration;
		}
		long getObservedAuthoredGeneration() {
			return observedAuthoredGeneration;
		}
		long getTicksBeforeRun() { return ticksBeforeRun; }
		int getTimesRan() { return timesRan; }
		long getLifecycleVersion() { return lifecycleVersion; }
		boolean isAtomicTimingCaptured() { return true; }
		boolean isTimingStableAcrossOperation() { return false; }
		boolean isEventCancellationExcluded() { return false; }
		int getObjectId() { return objectId; }
		int getPermanentObjectId() { return permanentObjectId; }
		int getX() { return x; }
		int getY() { return y; }
		int getDirection() { return direction; }
		int getType() { return type; }
		boolean isForceFullBlock() { return forceFullBlock; }
		int getAuthoredPackedRegionX() { return authoredPackedRegionX; }
		int getAuthoredPackedRegionY() { return authoredPackedRegionY; }
		int getAuthoredSourceOrdinal() { return authoredSourceOrdinal; }
		AuthoredConstructionKind getAuthoredConstructionKind() {
			return authoredConstructionKind;
		}
		boolean isSpatialAffinityValidated() { return true; }
		boolean isAuthoredGenerationValidated() { return true; }
		boolean isOwnerStateRetained() { return false; }
		boolean isRuntimeAttributeStateRetained() { return false; }
		boolean isEventHandleRetained() { return false; }
		boolean isStoreHandleRetained() { return false; }
	}

	static final class RestorationRegistrationFenceExecution {
		private final RestorationRegistrationFenceReason reason;
		private final RestorationRegistrationFence fence;
		private final long lifecycleVersionBeforeOperation;
		private final long lifecycleVersionAfterOperation;
		private final boolean operationInvoked;

		private RestorationRegistrationFenceExecution(
			final RestorationRegistrationFenceReason reason,
			final RestorationRegistrationFence fence,
			final long lifecycleVersionBeforeOperation,
			final long lifecycleVersionAfterOperation,
			final boolean operationInvoked) {
			this.reason = Objects.requireNonNull(reason, "reason");
			this.fence = fence;
			this.lifecycleVersionBeforeOperation =
				lifecycleVersionBeforeOperation;
			this.lifecycleVersionAfterOperation =
				lifecycleVersionAfterOperation;
			this.operationInvoked = operationInvoked;
			if ((reason
				== RestorationRegistrationFenceReason.OPERATION_COMPLETED)
				!= (fence != null)
				|| (operationInvoked
					!= (lifecycleVersionBeforeOperation > 0L
						&& lifecycleVersionAfterOperation > 0L))
				|| (fence != null
					&& (lifecycleVersionBeforeOperation
							!= lifecycleVersionAfterOperation
						|| fence.getLifecycleVersion()
							!= lifecycleVersionBeforeOperation))
				|| (reason
					== RestorationRegistrationFenceReason
						.EVENT_LIFECYCLE_CHANGED_DURING_OPERATION
					&& (!operationInvoked
						|| lifecycleVersionBeforeOperation
							== lifecycleVersionAfterOperation))) {
				throw new IllegalArgumentException(
					"Restoration registration result is inconsistent");
			}
		}

		private static RestorationRegistrationFenceExecution refused(
			final RestorationRegistrationFenceReason reason) {
			return new RestorationRegistrationFenceExecution(
				reason, null, -1L, -1L, false);
		}
		private static RestorationRegistrationFenceExecution
			refusedAfterOperation(
				final RestorationRegistrationFenceReason reason,
				final long lifecycleVersionBeforeOperation,
				final long lifecycleVersionAfterOperation) {
			return new RestorationRegistrationFenceExecution(
				reason, null, lifecycleVersionBeforeOperation,
				lifecycleVersionAfterOperation, true);
		}
		private static RestorationRegistrationFenceExecution accepted(
			final RestorationRegistrationFence fence,
			final long lifecycleVersion) {
			return new RestorationRegistrationFenceExecution(
				RestorationRegistrationFenceReason.OPERATION_COMPLETED, fence,
				lifecycleVersion, lifecycleVersion, true);
		}

		boolean isAccepted() {
			return reason
				== RestorationRegistrationFenceReason.OPERATION_COMPLETED;
		}
		RestorationRegistrationFenceReason getReason() { return reason; }
		RestorationRegistrationFence getFence() { return fence; }
		long getLifecycleVersionBeforeOperation() {
			return lifecycleVersionBeforeOperation;
		}
		long getLifecycleVersionAfterOperation() {
			return lifecycleVersionAfterOperation;
		}
		boolean isOperationInvoked() { return operationInvoked; }
		boolean isTimingStableAcrossOperation() {
			return isAccepted()
				&& lifecycleVersionBeforeOperation
					== lifecycleVersionAfterOperation;
		}
		boolean isEventLifecycleChangeDetected() {
			return reason
				== RestorationRegistrationFenceReason
					.EVENT_LIFECYCLE_CHANGED_DURING_OPERATION;
		}
		boolean isRuntimeTargetLookupPerformed() { return false; }
		boolean isRuntimeRevalidationPerformed() { return false; }
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
