package com.openrsc.server.event.rsc.handler;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Key;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.GameTickEventRestorationCommitRequest;
import com.openrsc.server.event.rsc.GameTickEventRestorationOneShotConsumptionContract;
import com.openrsc.server.event.rsc.GameTickEventRestorationOneShotConsumptionContract.RegionCommitOutcome;
import com.openrsc.server.event.rsc.GameTickEventRestorationOneShotConsumptionContract.RequiredAction;
import com.openrsc.server.event.rsc.GameTickEventRestorationTargetDecision;
import com.openrsc.server.event.rsc.GameTickEventRestorationTargetRevalidation;
import com.openrsc.server.event.rsc.GameTickEventRestorationTargetRevalidationRequest;
import com.openrsc.server.event.rsc.GameTickEventRestorationState;
import com.openrsc.server.event.rsc.GameTickEventSpatialAffinity;
import com.openrsc.server.event.rsc.PluginTickEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.region.RegionManager;
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

	/**
	 * Composes the handle-free scheduler/generation fence with one real,
	 * read-only Region-boundary target classification. The RegionManager handle
	 * remains local to this operation, and a lifecycle-version mismatch discards
	 * the detached target result before returning.
	 */
	RestorationTargetRevalidationExecution
		withValidatedRestorationTargetRevalidation(
			final RegionManager regionManager,
			final String expectedSchedulerInstanceIdentity,
			final long expectedRegistrationSequence,
			final long expectedProposalGeneration) {
		final RegionManager checkedRegionManager = Objects.requireNonNull(
			regionManager, "regionManager");
		final GameTickEventRestorationTargetRevalidation[] target =
			new GameTickEventRestorationTargetRevalidation[1];
		RestorationRegistrationFenceExecution outer =
			withValidatedRestorationRegistrationFence(
				expectedSchedulerInstanceIdentity,
				expectedRegistrationSequence,
				expectedProposalGeneration, fence -> {
					GameTickEventRestorationTargetRevalidationRequest request =
						GameTickEventRestorationTargetRevalidationRequest.request(
							fence.getSchedulerInstanceIdentity(),
							fence.getRegistrationSequence(),
							fence.getExpectedProposalGeneration(),
							fence.getObservedAuthoredGeneration(),
							fence.isEventExecutionBoundaryHeld(),
							fence.isSchedulerStoreBoundaryHeld(),
							fence
								.isRegistrationValidatedBeforeInnerBoundary(),
							GameTickEventRestorationTargetDecision
								.TargetOperation.valueOf(
									fence.getRestorationKind().name()),
							fence.getObjectId(), fence.getPermanentObjectId(),
							fence.getX(), fence.getY(), fence.getDirection(),
							fence.getType(), fence.isForceFullBlock(),
							fence.getAuthoredPackedRegionX(),
							fence.getAuthoredPackedRegionY(),
							fence.getAuthoredSourceOrdinal(),
							fence.getAuthoredConstructionKind().name());
					target[0] = checkedRegionManager
						.captureGameTickEventRestorationTargetRevalidation(
							request);
				});
		if (!outer.isAccepted()) {
			return RestorationTargetRevalidationExecution.refused(
				outer.getReason(),
				outer.getLifecycleVersionBeforeOperation(),
				outer.getLifecycleVersionAfterOperation(),
				outer.isOperationInvoked());
		}
		if (target[0] == null) {
			throw new IllegalStateException(
				"Accepted restoration target revalidation did not execute");
		}
		return RestorationTargetRevalidationExecution.observed(
			target[0], outer.getLifecycleVersionBeforeOperation(),
			outer.getLifecycleVersionAfterOperation());
	}

	/**
	 * Produces one ephemeral, generation-checked commit request while the exact
	 * scheduler registration, event execution boundary, and unchanged event
	 * lifecycle boundary remain valid. No mutation consumer is attached here;
	 * the operation receives only the closed request.
	 */
	RestorationCommitRequestExecution withValidatedRestorationCommitRequest(
		final String expectedSchedulerInstanceIdentity,
		final long expectedRegistrationSequence,
		final long expectedProposalGeneration,
		final RestorationCommitRequestOperation operation) {
		if (expectedSchedulerInstanceIdentity == null
			|| expectedSchedulerInstanceIdentity.isEmpty()
			|| expectedRegistrationSequence <= 0L
			|| expectedProposalGeneration <= 0L) {
			throw new IllegalArgumentException(
				"Expected restoration commit registration is invalid");
		}
		final RestorationCommitRequestOperation checkedOperation =
			Objects.requireNonNull(operation, "operation");
		final GameTickEvent candidate;
		synchronized (LOCK) {
			if (!schedulerInstanceIdentity.equals(
				expectedSchedulerInstanceIdentity)) {
				return RestorationCommitRequestExecution.refused(
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
						return RestorationCommitRequestExecution.refused(
							RestorationRegistrationFenceReason
								.DUPLICATE_REGISTRATION_SEQUENCE);
					}
					found = registration.getKey();
				}
			}
			if (found == null || events.get(getKey(found)) != found) {
				return RestorationCommitRequestExecution.refused(
					RestorationRegistrationFenceReason.EVENT_NOT_REGISTERED);
			}
			candidate = found;
		}

		final RestorationRegistrationFenceExecution[] restoration =
			new RestorationRegistrationFenceExecution[1];
		final boolean[] lifecycleBoundaryEntered = new boolean[1];
		final boolean[] requestDelivered = new boolean[1];
		final long[] commitLifecycleVersion = new long[] { -1L };
		RegistrationFenceExecution registration =
			withValidatedRegistrationFence(
				candidate, expectedSchedulerInstanceIdentity,
				expectedRegistrationSequence, registrationFence ->
					restoration[0] = executeRestorationRegistrationFence(
						candidate, registrationFence,
						expectedProposalGeneration, fence -> {
							boolean entered = candidate
								.withinStableRestorationLifecycleBoundary(
									fence.getLifecycleVersion(), lifecycle -> {
										lifecycleBoundaryEntered[0] = true;
										commitLifecycleVersion[0] =
											lifecycle.getLifecycleVersion();
										GameTickEventRestorationCommitRequest request =
											GameTickEventRestorationCommitRequest.request(
												fence.getSchedulerInstanceIdentity(),
												fence.getRegistrationSequence(),
												fence.getExpectedProposalGeneration(),
												fence.getObservedAuthoredGeneration(),
												lifecycle.getLifecycleVersion(),
												fence.isEventExecutionBoundaryHeld(),
												fence.isSchedulerStoreBoundaryHeld(),
												fence
													.isRegistrationValidatedBeforeInnerBoundary(),
												lifecycle.isLifecycleBoundaryHeld(),
												GameTickEventRestorationTargetDecision
													.TargetOperation.valueOf(
														fence.getRestorationKind().name()),
												fence.getObjectId(),
												fence.getPermanentObjectId(),
												fence.getX(), fence.getY(),
												fence.getDirection(), fence.getType(),
												fence.isForceFullBlock(),
												fence.getAuthoredPackedRegionX(),
												fence.getAuthoredPackedRegionY(),
												fence.getAuthoredSourceOrdinal(),
												fence.getAuthoredConstructionKind().name());
										checkedOperation.execute(request);
										requestDelivered[0] = true;
									});
							if (!entered) {
								lifecycleBoundaryEntered[0] = false;
							}
						}));
		if (!registration.isAccepted()) {
			return RestorationCommitRequestExecution.refused(
				mapRegistrationFenceReason(registration.getReason()));
		}
		if (restoration[0] == null) {
			throw new IllegalStateException(
				"Accepted restoration commit request did not execute");
		}
		if (!restoration[0].isAccepted()) {
			if (requestDelivered[0] && lifecycleBoundaryEntered[0]) {
				return RestorationCommitRequestExecution.delivered(
					commitLifecycleVersion[0]);
			}
			return RestorationCommitRequestExecution.refusedAfterOperation(
				restoration[0].getReason(),
				restoration[0].getLifecycleVersionBeforeOperation(),
				restoration[0].getLifecycleVersionAfterOperation(),
				lifecycleBoundaryEntered[0], requestDelivered[0]);
		}
		if (!lifecycleBoundaryEntered[0] || !requestDelivered[0]) {
			throw new IllegalStateException(
				"Accepted restoration lifecycle boundary delivered no request");
		}
		return RestorationCommitRequestExecution.delivered(
			commitLifecycleVersion[0]);
	}

	/**
	 * Runs one handle-free operation while the exact restoration registration,
	 * event execution boundary, and unchanged zero-run lifecycle are held. This
	 * scheduler seam supplies facts only; it grants no mutation or callback
	 * authority and retains the event with its original countdown.
	 */
	RestorationStableLifecycleExecution
		withValidatedRestorationStableLifecycle(
			final String expectedSchedulerInstanceIdentity,
			final long expectedRegistrationSequence,
			final long expectedProposalGeneration,
			final RestorationStableLifecycleOperation operation) {
		if (expectedSchedulerInstanceIdentity == null
			|| expectedSchedulerInstanceIdentity.isEmpty()
			|| expectedRegistrationSequence <= 0L
			|| expectedProposalGeneration <= 0L) {
			throw new IllegalArgumentException(
				"Expected stable restoration registration is invalid");
		}
		final RestorationStableLifecycleOperation checkedOperation =
			Objects.requireNonNull(operation, "operation");
		final GameTickEvent candidate;
		synchronized (LOCK) {
			if (!schedulerInstanceIdentity.equals(
				expectedSchedulerInstanceIdentity)) {
				return RestorationStableLifecycleExecution.refused(
					RestorationRegistrationFenceReason
						.SCHEDULER_INSTANCE_MISMATCH);
			}
			GameTickEvent found = null;
			for (Map.Entry<GameTickEvent, Long> registration
					: registrationSequences.entrySet()) {
				Long sequence = registration.getValue();
				if (sequence != null
					&& sequence.longValue() == expectedRegistrationSequence) {
					if (found != null) {
						return RestorationStableLifecycleExecution.refused(
							RestorationRegistrationFenceReason
								.DUPLICATE_REGISTRATION_SEQUENCE);
					}
					found = registration.getKey();
				}
			}
			if (found == null || events.get(getKey(found)) != found) {
				return RestorationStableLifecycleExecution.refused(
					RestorationRegistrationFenceReason.EVENT_NOT_REGISTERED);
			}
			candidate = found;
		}

		final RestorationRegistrationFenceExecution[] restoration =
			new RestorationRegistrationFenceExecution[1];
		final boolean[] lifecycleBoundaryEntered = new boolean[1];
		final boolean[] operationInvoked = new boolean[1];
		RegistrationFenceExecution registration =
			withValidatedRegistrationFence(
				candidate, expectedSchedulerInstanceIdentity,
				expectedRegistrationSequence, registrationFence ->
					restoration[0] = executeRestorationRegistrationFence(
						candidate, registrationFence,
						expectedProposalGeneration, fence -> {
							boolean entered = candidate
								.withinStableRestorationLifecycleBoundary(
									fence.getLifecycleVersion(), lifecycle -> {
										lifecycleBoundaryEntered[0] = true;
										checkedOperation.execute(fence);
										operationInvoked[0] = true;
									});
							if (!entered) {
								lifecycleBoundaryEntered[0] = false;
							}
						}));
		if (!registration.isAccepted()) {
			return RestorationStableLifecycleExecution.refused(
				mapRegistrationFenceReason(registration.getReason()));
		}
		if (restoration[0] == null) {
			throw new IllegalStateException(
				"Accepted stable restoration operation did not execute");
		}
		if (!restoration[0].isAccepted()) {
			if (lifecycleBoundaryEntered[0] && operationInvoked[0]) {
				return RestorationStableLifecycleExecution.completed(
					restoration[0].getLifecycleVersionBeforeOperation());
			}
			return RestorationStableLifecycleExecution.refusedAfterOperation(
				restoration[0].getReason(),
				restoration[0].getLifecycleVersionBeforeOperation(),
				restoration[0].getLifecycleVersionAfterOperation(),
				lifecycleBoundaryEntered[0], operationInvoked[0]);
		}
		if (!lifecycleBoundaryEntered[0] || !operationInvoked[0]) {
			throw new IllegalStateException(
				"Accepted stable lifecycle boundary invoked no operation");
		}
		return RestorationStableLifecycleExecution.completed(
			restoration[0].getLifecycleVersionBeforeOperation());
	}

	/**
	 * Exercises exact one-shot disposition behind the real scheduler and event
	 * boundaries using a caller-supplied detached Region outcome. It owns no
	 * Region handle; Slice 142 supplies the separately bounded runtime adapter.
	 */
	RestorationOneShotConsumptionExecution
		withValidatedRestorationOneShotConsumption(
			final String expectedSchedulerInstanceIdentity,
			final long expectedRegistrationSequence,
			final long expectedProposalGeneration,
			final RestorationRegionOutcomeOperation operation) {
		return withValidatedRestorationOneShotConsumptionInternal(
			expectedSchedulerInstanceIdentity,
			expectedRegistrationSequence, expectedProposalGeneration,
			false, 0L, 0L, operation);
	}

	/** Exact planned-timing variant used only by recovery directives. */
	RestorationOneShotConsumptionExecution
		withValidatedRestorationOneShotConsumption(
			final String expectedSchedulerInstanceIdentity,
			final long expectedRegistrationSequence,
			final long expectedProposalGeneration,
			final long expectedLifecycleVersion,
			final long expectedTicksBeforeRun,
			final RestorationRegionOutcomeOperation operation) {
		if (expectedLifecycleVersion <= 0L) {
			throw new IllegalArgumentException(
				"Expected directive lifecycle version must be positive");
		}
		return withValidatedRestorationOneShotConsumptionInternal(
			expectedSchedulerInstanceIdentity,
			expectedRegistrationSequence, expectedProposalGeneration,
			true, expectedLifecycleVersion, expectedTicksBeforeRun,
			operation);
	}

	private RestorationOneShotConsumptionExecution
		withValidatedRestorationOneShotConsumptionInternal(
			final String expectedSchedulerInstanceIdentity,
			final long expectedRegistrationSequence,
			final long expectedProposalGeneration,
			final boolean requireExactPlannedTiming,
			final long expectedLifecycleVersion,
			final long expectedTicksBeforeRun,
			final RestorationRegionOutcomeOperation operation) {
		if (expectedSchedulerInstanceIdentity == null
			|| expectedSchedulerInstanceIdentity.isEmpty()
			|| expectedRegistrationSequence <= 0L
			|| expectedProposalGeneration <= 0L) {
			throw new IllegalArgumentException(
				"Expected restoration consumption registration is invalid");
		}
		final RestorationRegionOutcomeOperation checkedOperation =
			Objects.requireNonNull(operation, "operation");
		final GameTickEvent candidate;
		synchronized (LOCK) {
			if (!schedulerInstanceIdentity.equals(
				expectedSchedulerInstanceIdentity)) {
				return RestorationOneShotConsumptionExecution.refused(
					RestorationOneShotConsumptionReason
						.SCHEDULER_INSTANCE_MISMATCH);
			}
			GameTickEvent found = null;
			for (Map.Entry<GameTickEvent, Long> registration
					: registrationSequences.entrySet()) {
				Long sequence = registration.getValue();
				if (sequence != null
					&& sequence.longValue() == expectedRegistrationSequence) {
					if (found != null) {
						return RestorationOneShotConsumptionExecution.refused(
							RestorationOneShotConsumptionReason
								.DUPLICATE_REGISTRATION_SEQUENCE);
					}
					found = registration.getKey();
				}
			}
			if (found == null || events.get(getKey(found)) != found) {
				return RestorationOneShotConsumptionExecution.refused(
					RestorationOneShotConsumptionReason.EVENT_NOT_REGISTERED);
			}
			candidate = found;
		}

		final RestorationOneShotConsumptionExecution[] result =
			new RestorationOneShotConsumptionExecution[1];
		RegistrationFenceExecution registration =
			withValidatedRegistrationFence(
				candidate, expectedSchedulerInstanceIdentity,
				expectedRegistrationSequence, registrationFence -> {
					RestorationRegistrationFencePreparation preparation =
						prepareRestorationRegistrationFence(
							candidate, registrationFence,
							expectedProposalGeneration);
					if (preparation.isRefused()) {
						result[0] = RestorationOneShotConsumptionExecution
							.refused(mapOneShotPreparationReason(
								preparation.getReason()));
						return;
					}
					RestorationRegistrationFence fence =
						preparation.getFence();
					if (!fence.isOneShotExecution()
						|| !fence.isContinuingServerTickProgression()) {
						result[0] = RestorationOneShotConsumptionExecution
							.refused(RestorationOneShotConsumptionReason
								.EXECUTION_SEMANTICS_REFUSED);
						return;
					}
					if (requireExactPlannedTiming
						&& (fence.getLifecycleVersion()
								!= expectedLifecycleVersion
							|| fence.getTicksBeforeRun()
								!= expectedTicksBeforeRun)) {
						result[0] = RestorationOneShotConsumptionExecution
							.refused(RestorationOneShotConsumptionReason
								.DIRECTIVE_TIMING_MISMATCH);
						return;
					}
					final RegionCommitOutcome[] regionOutcome =
						new RegionCommitOutcome[1];
					final GameTickEventRestorationOneShotConsumptionContract
						.Decision[] decision =
							new GameTickEventRestorationOneShotConsumptionContract
								.Decision[1];
					final boolean[] registrationRemoved = new boolean[1];
					GameTickEvent.StableRestorationConsumptionExecution
						lifecycle = candidate
							.withinStableRestorationConsumptionBoundary(
								fence.getLifecycleVersion(), boundary -> {
									GameTickEventRestorationCommitRequest request =
										GameTickEventRestorationCommitRequest.request(
											fence.getSchedulerInstanceIdentity(),
											fence.getRegistrationSequence(),
											fence.getExpectedProposalGeneration(),
											fence.getObservedAuthoredGeneration(),
											boundary.getLifecycleVersion(),
											fence.isEventExecutionBoundaryHeld(),
											fence.isSchedulerStoreBoundaryHeld(),
											fence
												.isRegistrationValidatedBeforeInnerBoundary(),
											boundary.isLifecycleBoundaryHeld(),
											GameTickEventRestorationTargetDecision
												.TargetOperation.valueOf(
													fence.getRestorationKind().name()),
											fence.getObjectId(),
											fence.getPermanentObjectId(),
											fence.getX(), fence.getY(),
											fence.getDirection(), fence.getType(),
											fence.isForceFullBlock(),
											fence.getAuthoredPackedRegionX(),
											fence.getAuthoredPackedRegionY(),
											fence.getAuthoredSourceOrdinal(),
											fence.getAuthoredConstructionKind().name());
									regionOutcome[0] = Objects.requireNonNull(
										checkedOperation.execute(request),
										"region outcome");
									decision[0] =
										GameTickEventRestorationOneShotConsumptionContract
											.assess(
												GameTickEventRestorationOneShotConsumptionContract
													.Precondition.declare(
														regionOutcome[0], true, true, true,
														fence.isEventExecutionBoundaryHeld(),
														fence.isSchedulerStoreBoundaryHeld(),
														boundary.isLifecycleBoundaryHeld(),
														fence.isOneShotExecution(),
														fence
															.isContinuingServerTickProgression(),
														true, fence.getTimesRan(),
														boundary.getLifecycleVersion(),
														regionOutcome[0]
															== RegionCommitOutcome.APPLIED,
														regionOutcome[0]
															!= RegionCommitOutcome.REFUSED,
														false));
									if (decision[0].isRefused()) {
										throw new IllegalStateException(
											"Closed one-shot consumption contract refused");
									}
									if (decision[0].getRequiredAction()
											== RequiredAction.TERMINALLY_CONSUME) {
										synchronized (LOCK) {
											Long observed =
												registrationSequences.get(candidate);
											if (events.get(getKey(candidate)) != candidate
												|| observed == null
												|| observed.longValue()
													!= expectedRegistrationSequence) {
												throw new IllegalStateException(
													"Restoration registration changed inside its execution boundary");
											}
											unregisterAccepted(
												getKey(candidate), candidate);
											registrationRemoved[0] = true;
										}
									}
									return decision[0].getRequiredAction()
										== RequiredAction.TERMINALLY_CONSUME
											? GameTickEvent
												.RestorationLifecycleDisposition
													.TERMINALLY_CONSUME
											: GameTickEvent
												.RestorationLifecycleDisposition
													.RETAIN_SCHEDULED;
								});
					if (!lifecycle.isBoundaryEntered()) {
						result[0] = RestorationOneShotConsumptionExecution
							.refused(RestorationOneShotConsumptionReason
								.EVENT_LIFECYCLE_CHANGED_BEFORE_OPERATION);
						return;
					}
					GameTickEvent.AtomicTimingSnapshot after =
						candidate.captureAtomicTimingSnapshot();
					boolean registrationPresent;
					boolean sameRegistrationPresent;
					synchronized (LOCK) {
						Long observed = registrationSequences.get(candidate);
						registrationPresent =
							events.get(getKey(candidate)) == candidate;
						sameRegistrationPresent = registrationPresent
							&& observed != null
							&& observed.longValue()
								== expectedRegistrationSequence;
					}
					GameTickEventRestorationOneShotConsumptionContract
						.Verification verification =
							GameTickEventRestorationOneShotConsumptionContract
								.verifyPostcondition(
									decision[0],
									GameTickEventRestorationOneShotConsumptionContract
										.Postcondition.declare(
											registrationPresent,
											sameRegistrationPresent,
											registrationRemoved[0],
											after.isRunning(),
											after.getTimesRan(),
											after.getLifecycleVersion(),
											false, false,
											registrationRemoved[0]));
					if (!verification.isSatisfied()) {
						throw new IllegalStateException(
							"One-shot consumption postcondition refused: "
								+ verification.getReason());
					}
					result[0] = RestorationOneShotConsumptionExecution.completed(
						regionOutcome[0], decision[0].getRequiredAction(),
						lifecycle.getLifecycleVersionBefore(),
						lifecycle.getLifecycleVersionAfter(),
						registrationRemoved[0]);
				});
		if (!registration.isAccepted()) {
			return RestorationOneShotConsumptionExecution.refused(
				mapOneShotRegistrationReason(registration.getReason()));
		}
		if (result[0] == null) {
			throw new IllegalStateException(
				"Accepted one-shot consumption produced no result");
		}
		return result[0];
	}

	private static RestorationRegistrationFenceExecution
		executeRestorationRegistrationFence(
			final GameTickEvent event,
			final RegistrationFence registrationFence,
			final long expectedProposalGeneration,
			final RestorationRegistrationFenceOperation operation) {
		RestorationRegistrationFencePreparation preparation =
			prepareRestorationRegistrationFence(
				event, registrationFence, expectedProposalGeneration);
		if (preparation.isRefused()) {
			return RestorationRegistrationFenceExecution.refused(
				preparation.getReason());
		}
		RestorationRegistrationFence fence = preparation.getFence();
		operation.execute(fence);
		GameTickEvent.AtomicTimingSnapshot after =
			event.captureAtomicTimingSnapshot();
		if (after.getLifecycleVersion() != fence.getLifecycleVersion()) {
			return RestorationRegistrationFenceExecution.refusedAfterOperation(
				RestorationRegistrationFenceReason
					.EVENT_LIFECYCLE_CHANGED_DURING_OPERATION,
				fence.getLifecycleVersion(), after.getLifecycleVersion());
		}
		return RestorationRegistrationFenceExecution.accepted(
			fence, fence.getLifecycleVersion());
	}

	private static RestorationRegistrationFencePreparation
		prepareRestorationRegistrationFence(
			final GameTickEvent event,
			final RegistrationFence registrationFence,
			final long expectedProposalGeneration) {
		GameTickEventRestorationState state = Objects.requireNonNull(
			event.getRestorationState(), "event restoration state");
		if (state.getKind()
			== GameTickEventRestorationState.Kind.UNAVAILABLE) {
			return RestorationRegistrationFencePreparation.refused(
				RestorationRegistrationFenceReason
					.RESTORATION_STATE_UNAVAILABLE);
		}
		GameTickEvent.AtomicTimingSnapshot timing =
			event.captureAtomicTimingSnapshot();
		if (!timing.isRunning()) {
			return RestorationRegistrationFencePreparation.refused(
				RestorationRegistrationFenceReason.EVENT_NOT_RUNNING);
		}
		if (timing.getTimesRan() != 0) {
			return RestorationRegistrationFencePreparation.refused(
				RestorationRegistrationFenceReason.EVENT_ALREADY_EXECUTED);
		}
		GameTickEventRestorationState.SceneryState scenery =
			state.getScenery();
		if (scenery == null || !state.isDetachedCallbackPayloadComplete()) {
			return RestorationRegistrationFencePreparation.refused(
				RestorationRegistrationFenceReason
					.RESTORATION_PAYLOAD_INCOMPLETE);
		}
		GameTickEventRestorationState.AuthoredPlacementState authored =
			scenery.getAuthoredPlacement();
		if (authored == null) {
			return RestorationRegistrationFencePreparation.refused(
				RestorationRegistrationFenceReason.AUTHORED_IDENTITY_MISSING);
		}
		if (scenery.hasOwner()) {
			return RestorationRegistrationFencePreparation.refused(
				RestorationRegistrationFenceReason.OWNER_BOUND_STATE_REFUSED);
		}
		if (scenery.getRuntimeAttributeCount() != 0) {
			return RestorationRegistrationFencePreparation.refused(
				RestorationRegistrationFenceReason
					.RUNTIME_ATTRIBUTE_STATE_INCOMPLETE);
		}
		if (!matchesSceneryConstructionKind(
				authored.getConstructionKind(), scenery.getType())) {
			return RestorationRegistrationFencePreparation.refused(
				RestorationRegistrationFenceReason
					.AUTHORED_CONSTRUCTION_KIND_MISMATCH);
		}
		GameTickEventSpatialAffinity affinity = Objects.requireNonNull(
			event.getSpatialAffinity(), "event spatial affinity");
		if (!matchesExactSceneryAffinity(affinity, scenery)) {
			return RestorationRegistrationFencePreparation.refused(
				RestorationRegistrationFenceReason.SPATIAL_AFFINITY_MISMATCH);
		}
		if (authored.getGeneration() != expectedProposalGeneration) {
			return RestorationRegistrationFencePreparation.refused(
				RestorationRegistrationFenceReason
					.PROPOSAL_GENERATION_MISMATCH);
		}
		return RestorationRegistrationFencePreparation.accepted(
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
					authored.getConstructionKind().name()),
				state.getExecutionSemantics()
					== GameTickEventRestorationState.ExecutionSemantics.ONE_SHOT,
				state.getTimeProgressionPolicy()
					== GameTickEventRestorationState.TimeProgressionPolicy
						.CONTINUE_SERVER_TICKS));
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

	/**
	 * Composes the exact scheduler-local consumption boundary with the real
	 * Region commit seam. No arrival or gameplay path calls this operation yet.
	 */
	RestorationRegionCommitConsumptionExecution
		withValidatedRestorationRegionCommitConsumption(
			final RegionManager regionManager,
			final String expectedSchedulerInstanceIdentity,
			final long expectedRegistrationSequence,
			final long expectedProposalGeneration) {
		return withValidatedRestorationRegionCommitConsumptionInternal(
			regionManager, expectedSchedulerInstanceIdentity,
			expectedRegistrationSequence, expectedProposalGeneration,
			false, 0L, 0L);
	}

	RestorationRegionCommitConsumptionExecution
		withValidatedRestorationRegionCommitConsumption(
			final RegionManager regionManager,
			final String expectedSchedulerInstanceIdentity,
			final long expectedRegistrationSequence,
			final long expectedProposalGeneration,
			final long expectedLifecycleVersion,
			final long expectedTicksBeforeRun) {
		if (expectedLifecycleVersion <= 0L) {
			throw new IllegalArgumentException(
				"Expected directive lifecycle version must be positive");
		}
		return withValidatedRestorationRegionCommitConsumptionInternal(
			regionManager, expectedSchedulerInstanceIdentity,
			expectedRegistrationSequence, expectedProposalGeneration,
			true, expectedLifecycleVersion, expectedTicksBeforeRun);
	}

	private RestorationRegionCommitConsumptionExecution
		withValidatedRestorationRegionCommitConsumptionInternal(
			final RegionManager regionManager,
			final String expectedSchedulerInstanceIdentity,
			final long expectedRegistrationSequence,
			final long expectedProposalGeneration,
			final boolean requireExactPlannedTiming,
			final long expectedLifecycleVersion,
			final long expectedTicksBeforeRun) {
		final RegionManager checkedRegionManager = Objects.requireNonNull(
			regionManager, "regionManager");
		final RegionManager.RestorationCommitResult[] regionResult =
			new RegionManager.RestorationCommitResult[1];
		RestorationOneShotConsumptionExecution schedulerResult =
			withValidatedRestorationOneShotConsumptionInternal(
				expectedSchedulerInstanceIdentity,
				expectedRegistrationSequence,
				expectedProposalGeneration, requireExactPlannedTiming,
				expectedLifecycleVersion, expectedTicksBeforeRun, request -> {
					regionResult[0] = Objects.requireNonNull(
						checkedRegionManager
							.applyGameTickEventRestorationCommitRequest(request),
						"region restoration result");
					return RegionCommitOutcome.valueOf(
						regionResult[0].getOutcome().name());
				});
		return RestorationRegionCommitConsumptionExecution.compose(
			schedulerResult, regionResult[0]);
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

	private static RestorationOneShotConsumptionReason
		mapOneShotRegistrationReason(final RegistrationFenceReason reason) {
		switch (reason) {
			case SCHEDULER_INSTANCE_MISMATCH:
				return RestorationOneShotConsumptionReason
					.SCHEDULER_INSTANCE_MISMATCH;
			case EVENT_NOT_REGISTERED:
				return RestorationOneShotConsumptionReason.EVENT_NOT_REGISTERED;
			case REGISTRATION_SEQUENCE_MISMATCH:
				return RestorationOneShotConsumptionReason
					.REGISTRATION_SEQUENCE_MISMATCH;
			default:
				throw new IllegalStateException(
					"Accepted registration fence cannot be mapped as refusal");
		}
	}

	private static RestorationOneShotConsumptionReason
		mapOneShotPreparationReason(
			final RestorationRegistrationFenceReason reason) {
		switch (reason) {
			case RESTORATION_STATE_UNAVAILABLE:
				return RestorationOneShotConsumptionReason
					.RESTORATION_STATE_UNAVAILABLE;
			case EVENT_NOT_RUNNING:
				return RestorationOneShotConsumptionReason.EVENT_NOT_RUNNING;
			case EVENT_ALREADY_EXECUTED:
				return RestorationOneShotConsumptionReason.EVENT_ALREADY_EXECUTED;
			case RESTORATION_PAYLOAD_INCOMPLETE:
				return RestorationOneShotConsumptionReason
					.RESTORATION_PAYLOAD_INCOMPLETE;
			case AUTHORED_IDENTITY_MISSING:
				return RestorationOneShotConsumptionReason
					.AUTHORED_IDENTITY_MISSING;
			case OWNER_BOUND_STATE_REFUSED:
				return RestorationOneShotConsumptionReason
					.OWNER_BOUND_STATE_REFUSED;
			case RUNTIME_ATTRIBUTE_STATE_INCOMPLETE:
				return RestorationOneShotConsumptionReason
					.RUNTIME_ATTRIBUTE_STATE_INCOMPLETE;
			case AUTHORED_CONSTRUCTION_KIND_MISMATCH:
				return RestorationOneShotConsumptionReason
					.AUTHORED_CONSTRUCTION_KIND_MISMATCH;
			case SPATIAL_AFFINITY_MISMATCH:
				return RestorationOneShotConsumptionReason
					.SPATIAL_AFFINITY_MISMATCH;
			case PROPOSAL_GENERATION_MISMATCH:
				return RestorationOneShotConsumptionReason
					.PROPOSAL_GENERATION_MISMATCH;
			default:
				throw new IllegalStateException(
					"Accepted restoration preparation cannot be mapped as refusal");
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

	@FunctionalInterface
	interface RestorationCommitRequestOperation {
		void execute(GameTickEventRestorationCommitRequest request);
	}

	@FunctionalInterface
	interface RestorationStableLifecycleOperation {
		void execute(RestorationRegistrationFence fence);
	}

	@FunctionalInterface
	interface RestorationRegionOutcomeOperation {
		RegionCommitOutcome execute(
			GameTickEventRestorationCommitRequest request);
	}

	enum RestorationOneShotConsumptionReason {
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
		EXECUTION_SEMANTICS_REFUSED,
		DIRECTIVE_TIMING_MISMATCH,
		EVENT_LIFECYCLE_CHANGED_BEFORE_OPERATION,
		REGION_COMMIT_REFUSED_RETAINED,
		EVENT_TERMINALLY_CONSUMED
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
		private final boolean oneShotExecution;
		private final boolean continuingServerTickProgression;

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
			final AuthoredConstructionKind authoredConstructionKind,
			final boolean oneShotExecution,
			final boolean continuingServerTickProgression) {
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
			this.oneShotExecution = oneShotExecution;
			this.continuingServerTickProgression =
				continuingServerTickProgression;
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
		boolean isOneShotExecution() { return oneShotExecution; }
		boolean isContinuingServerTickProgression() {
			return continuingServerTickProgression;
		}
		boolean isSpatialAffinityValidated() { return true; }
		boolean isAuthoredGenerationValidated() { return true; }
		boolean isOwnerStateRetained() { return false; }
		boolean isRuntimeAttributeStateRetained() { return false; }
		boolean isEventHandleRetained() { return false; }
		boolean isStoreHandleRetained() { return false; }
	}

	/** Internal preparation result; no event or Store handle is retained. */
	private static final class RestorationRegistrationFencePreparation {
		private final RestorationRegistrationFenceReason reason;
		private final RestorationRegistrationFence fence;

		private RestorationRegistrationFencePreparation(
			final RestorationRegistrationFenceReason reason,
			final RestorationRegistrationFence fence) {
			this.reason = Objects.requireNonNull(reason, "reason");
			this.fence = fence;
			if ((reason
					== RestorationRegistrationFenceReason.OPERATION_COMPLETED)
				!= (fence != null)) {
				throw new IllegalArgumentException(
					"Restoration fence preparation is inconsistent");
			}
		}

		private static RestorationRegistrationFencePreparation refused(
			final RestorationRegistrationFenceReason reason) {
			return new RestorationRegistrationFencePreparation(reason, null);
		}
		private static RestorationRegistrationFencePreparation accepted(
			final RestorationRegistrationFence fence) {
			return new RestorationRegistrationFencePreparation(
				RestorationRegistrationFenceReason.OPERATION_COMPLETED, fence);
		}

		boolean isRefused() { return fence == null; }
		RestorationRegistrationFenceReason getReason() { return reason; }
		RestorationRegistrationFence getFence() { return fence; }
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

	/** Closed proof that a handle-free operation ran under stable event timing. */
	static final class RestorationStableLifecycleExecution {
		private final RestorationRegistrationFenceReason reason;
		private final long lifecycleVersionBeforeOperation;
		private final long lifecycleVersionAfterOperation;
		private final boolean lifecycleBoundaryEntered;
		private final boolean operationInvoked;

		private RestorationStableLifecycleExecution(
			final RestorationRegistrationFenceReason reason,
			final long lifecycleVersionBeforeOperation,
			final long lifecycleVersionAfterOperation,
			final boolean lifecycleBoundaryEntered,
			final boolean operationInvoked) {
			this.reason = Objects.requireNonNull(reason, "reason");
			this.lifecycleVersionBeforeOperation =
				lifecycleVersionBeforeOperation;
			this.lifecycleVersionAfterOperation =
				lifecycleVersionAfterOperation;
			this.lifecycleBoundaryEntered = lifecycleBoundaryEntered;
			this.operationInvoked = operationInvoked;
			boolean completed = reason
				== RestorationRegistrationFenceReason.OPERATION_COMPLETED;
			if ((completed
					&& (!operationInvoked || !lifecycleBoundaryEntered))
				|| (operationInvoked && !lifecycleBoundaryEntered)
				|| (completed
					&& (lifecycleVersionBeforeOperation <= 0L
						|| lifecycleVersionBeforeOperation
							!= lifecycleVersionAfterOperation))
				|| ((lifecycleVersionBeforeOperation > 0L)
					!= (lifecycleVersionAfterOperation > 0L))
				|| (!completed && lifecycleVersionBeforeOperation > 0L
					&& lifecycleVersionBeforeOperation
						== lifecycleVersionAfterOperation)
				|| (lifecycleVersionBeforeOperation <= 0L
					&& (operationInvoked || lifecycleBoundaryEntered))) {
				throw new IllegalArgumentException(
					"Stable restoration execution is inconsistent");
			}
		}

		private static RestorationStableLifecycleExecution refused(
			final RestorationRegistrationFenceReason reason) {
			return new RestorationStableLifecycleExecution(
				reason, -1L, -1L, false, false);
		}

		private static RestorationStableLifecycleExecution
			refusedAfterOperation(
				final RestorationRegistrationFenceReason reason,
				final long lifecycleVersionBeforeOperation,
				final long lifecycleVersionAfterOperation,
				final boolean lifecycleBoundaryEntered,
				final boolean operationInvoked) {
			return new RestorationStableLifecycleExecution(
				reason, lifecycleVersionBeforeOperation,
				lifecycleVersionAfterOperation,
				lifecycleBoundaryEntered, operationInvoked);
		}

		private static RestorationStableLifecycleExecution completed(
			final long lifecycleVersion) {
			return new RestorationStableLifecycleExecution(
				RestorationRegistrationFenceReason.OPERATION_COMPLETED,
				lifecycleVersion, lifecycleVersion, true, true);
		}

		RestorationRegistrationFenceReason getReason() { return reason; }
		long getLifecycleVersionBeforeOperation() {
			return lifecycleVersionBeforeOperation;
		}
		long getLifecycleVersionAfterOperation() {
			return lifecycleVersionAfterOperation;
		}
		boolean isAccepted() {
			return reason
				== RestorationRegistrationFenceReason.OPERATION_COMPLETED;
		}
		boolean isLifecycleBoundaryEntered() {
			return lifecycleBoundaryEntered;
		}
		boolean isOperationInvoked() { return operationInvoked; }
		boolean isExactRegistrationRetained() { return isAccepted(); }
		boolean isCountdownRetained() { return isAccepted(); }
		boolean isEventHandleRetained() { return false; }
		boolean isRegistrationHandleRetained() { return false; }
		boolean isMutationAuthorized() { return false; }
		boolean isMutationPerformed() { return false; }
		boolean isCallbackInvoked() { return false; }
		boolean isEventCancellation() { return false; }
		boolean isEventReschedule() { return false; }
		boolean isCommitToken() { return false; }
		boolean isArrivalGate() { return false; }
		boolean isLifecycleAuthority() { return false; }
	}

	/**
	 * Closed scheduler-side outcome. The ephemeral request is never retained by
	 * this value and no mutation result is claimed.
	 */
	static final class RestorationCommitRequestExecution {
		private final RestorationRegistrationFenceReason reason;
		private final long lifecycleVersionBeforeOperation;
		private final long lifecycleVersionAfterOperation;
		private final boolean lifecycleBoundaryEntered;
		private final boolean requestDelivered;

		private RestorationCommitRequestExecution(
			final RestorationRegistrationFenceReason reason,
			final long lifecycleVersionBeforeOperation,
			final long lifecycleVersionAfterOperation,
			final boolean lifecycleBoundaryEntered,
			final boolean requestDelivered) {
			this.reason = Objects.requireNonNull(reason, "reason");
			this.lifecycleVersionBeforeOperation =
				lifecycleVersionBeforeOperation;
			this.lifecycleVersionAfterOperation =
				lifecycleVersionAfterOperation;
			this.lifecycleBoundaryEntered = lifecycleBoundaryEntered;
			this.requestDelivered = requestDelivered;
			boolean delivered = reason
				== RestorationRegistrationFenceReason.OPERATION_COMPLETED;
			if (delivered != requestDelivered
				|| requestDelivered != lifecycleBoundaryEntered
				|| (requestDelivered
					&& (lifecycleVersionBeforeOperation <= 0L
						|| lifecycleVersionBeforeOperation
							!= lifecycleVersionAfterOperation))
				|| (!requestDelivered
					&& ((lifecycleVersionBeforeOperation > 0L)
						!= (lifecycleVersionAfterOperation > 0L)))) {
				throw new IllegalArgumentException(
					"Restoration commit-request result is inconsistent");
			}
		}

		private static RestorationCommitRequestExecution refused(
			final RestorationRegistrationFenceReason reason) {
			return new RestorationCommitRequestExecution(
				reason, -1L, -1L, false, false);
		}

		private static RestorationCommitRequestExecution refusedAfterOperation(
			final RestorationRegistrationFenceReason reason,
			final long lifecycleVersionBeforeOperation,
			final long lifecycleVersionAfterOperation,
			final boolean lifecycleBoundaryEntered,
			final boolean requestDelivered) {
			return new RestorationCommitRequestExecution(
				reason, lifecycleVersionBeforeOperation,
				lifecycleVersionAfterOperation, lifecycleBoundaryEntered,
				requestDelivered);
		}

		private static RestorationCommitRequestExecution delivered(
			final long lifecycleVersion) {
			return new RestorationCommitRequestExecution(
				RestorationRegistrationFenceReason.OPERATION_COMPLETED,
				lifecycleVersion, lifecycleVersion, true, true);
		}

		boolean isRequestDelivered() { return requestDelivered; }
		RestorationRegistrationFenceReason getReason() { return reason; }
		long getLifecycleVersionBeforeOperation() {
			return lifecycleVersionBeforeOperation;
		}
		long getLifecycleVersionAfterOperation() {
			return lifecycleVersionAfterOperation;
		}
		boolean isLifecycleBoundaryEntered() {
			return lifecycleBoundaryEntered;
		}
		boolean isRequestRetained() { return false; }
		boolean isMutationAuthorized() { return false; }
		boolean isMutationPerformed() { return false; }
		boolean isCallbackInvoked() { return false; }
		boolean isEventCancellation() { return false; }
		boolean isEventReschedule() { return false; }
		boolean isExecutableRestoration() { return false; }
		boolean isCommitToken() { return false; }
		boolean isArrivalGate() { return false; }
		boolean isLifecycleAuthority() { return false; }
	}

	/**
	 * Closed result of one scheduler-local consumption attempt. The supplied
	 * Region outcome is detached evidence; this value retains no request,
	 * event, Store, callback, or lifecycle handle.
	 */
	static final class RestorationOneShotConsumptionExecution {
		private final RestorationOneShotConsumptionReason reason;
		private final RegionCommitOutcome regionCommitOutcome;
		private final RequiredAction requiredAction;
		private final long lifecycleVersionBefore;
		private final long lifecycleVersionAfter;
		private final boolean requestDelivered;
		private final boolean registrationRemoved;

		private RestorationOneShotConsumptionExecution(
			final RestorationOneShotConsumptionReason reason,
			final RegionCommitOutcome regionCommitOutcome,
			final RequiredAction requiredAction,
			final long lifecycleVersionBefore,
			final long lifecycleVersionAfter,
			final boolean requestDelivered,
			final boolean registrationRemoved) {
			this.reason = Objects.requireNonNull(reason, "reason");
			this.regionCommitOutcome = regionCommitOutcome;
			this.requiredAction = Objects.requireNonNull(
				requiredAction, "requiredAction");
			this.lifecycleVersionBefore = lifecycleVersionBefore;
			this.lifecycleVersionAfter = lifecycleVersionAfter;
			this.requestDelivered = requestDelivered;
			this.registrationRemoved = registrationRemoved;
			boolean completed = regionCommitOutcome != null;
			boolean consumed = requiredAction
				== RequiredAction.TERMINALLY_CONSUME;
			boolean retained = requiredAction
				== RequiredAction.RETAIN_SCHEDULED;
			if (completed != requestDelivered
				|| completed != (consumed || retained)
				|| registrationRemoved != consumed
				|| (!completed
					&& (requiredAction != RequiredAction.NONE
						|| lifecycleVersionBefore != -1L
						|| lifecycleVersionAfter != -1L))
				|| (retained
					&& (reason
							!= RestorationOneShotConsumptionReason
								.REGION_COMMIT_REFUSED_RETAINED
						|| regionCommitOutcome != RegionCommitOutcome.REFUSED
						|| lifecycleVersionBefore <= 0L
						|| lifecycleVersionAfter
							!= lifecycleVersionBefore))
				|| (consumed
					&& (reason
							!= RestorationOneShotConsumptionReason
								.EVENT_TERMINALLY_CONSUMED
						|| regionCommitOutcome == RegionCommitOutcome.REFUSED
						|| lifecycleVersionBefore <= 0L
						|| lifecycleVersionAfter
							!= lifecycleVersionBefore + 1L))) {
				throw new IllegalArgumentException(
					"One-shot consumption execution is inconsistent");
			}
		}

		private static RestorationOneShotConsumptionExecution refused(
			final RestorationOneShotConsumptionReason reason) {
			return new RestorationOneShotConsumptionExecution(
				reason, null, RequiredAction.NONE, -1L, -1L,
				false, false);
		}

		private static RestorationOneShotConsumptionExecution completed(
			final RegionCommitOutcome outcome,
			final RequiredAction requiredAction,
			final long lifecycleVersionBefore,
			final long lifecycleVersionAfter,
			final boolean registrationRemoved) {
			return new RestorationOneShotConsumptionExecution(
				requiredAction == RequiredAction.TERMINALLY_CONSUME
					? RestorationOneShotConsumptionReason
						.EVENT_TERMINALLY_CONSUMED
					: RestorationOneShotConsumptionReason
						.REGION_COMMIT_REFUSED_RETAINED,
				Objects.requireNonNull(outcome, "outcome"), requiredAction,
				lifecycleVersionBefore, lifecycleVersionAfter, true,
				registrationRemoved);
		}

		RestorationOneShotConsumptionReason getReason() { return reason; }
		RegionCommitOutcome getRegionCommitOutcome() {
			return regionCommitOutcome;
		}
		RequiredAction getRequiredAction() { return requiredAction; }
		long getLifecycleVersionBefore() { return lifecycleVersionBefore; }
		long getLifecycleVersionAfter() { return lifecycleVersionAfter; }
		boolean isRequestDelivered() { return requestDelivered; }
		boolean isRegistrationRemoved() { return registrationRemoved; }
		boolean isExactRegistrationRetained() {
			return requiredAction == RequiredAction.RETAIN_SCHEDULED;
		}
		boolean isEventTerminallyConsumed() {
			return requiredAction == RequiredAction.TERMINALLY_CONSUME;
		}
		boolean isRegionMutationReported() {
			return regionCommitOutcome == RegionCommitOutcome.APPLIED;
		}
		boolean isRegionManagerHandleRetained() { return false; }
		boolean isRequestRetained() { return false; }
		boolean isRuntimeHandleRetained() { return false; }
		boolean isMutationAuthorized() { return false; }
		boolean isCallbackInvoked() { return false; }
		boolean isEventReschedule() { return false; }
		boolean isExecutableRestoration() { return false; }
		boolean isCommitToken() { return false; }
		boolean isArrivalGate() { return false; }
		boolean isLifecycleAuthority() { return false; }
	}

	/**
	 * Detached composition result. Region reason and membership counts are
	 * copied from the closed Region result; neither runtime handle is retained.
	 */
	static final class RestorationRegionCommitConsumptionExecution {
		private final RestorationOneShotConsumptionExecution scheduler;
		private final RegionManager.RestorationCommitOutcome regionOutcome;
		private final RegionManager.RestorationCommitReason regionReason;
		private final boolean membershipRemoved;
		private final boolean membershipRegistered;
		private final int boundaryCount;

		private RestorationRegionCommitConsumptionExecution(
			final RestorationOneShotConsumptionExecution scheduler,
			final RegionManager.RestorationCommitOutcome regionOutcome,
			final RegionManager.RestorationCommitReason regionReason,
			final boolean membershipRemoved,
			final boolean membershipRegistered,
			final int boundaryCount) {
			this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
			this.regionOutcome = regionOutcome;
			this.regionReason = regionReason;
			this.membershipRemoved = membershipRemoved;
			this.membershipRegistered = membershipRegistered;
			this.boundaryCount = boundaryCount;
			boolean invoked = regionOutcome != null;
			if (invoked != scheduler.isRequestDelivered()
				|| invoked != (regionReason != null)
				|| boundaryCount < 0
				|| (!invoked
					&& (membershipRemoved || membershipRegistered
						|| boundaryCount != 0))
				|| (invoked
					&& RegionCommitOutcome.valueOf(regionOutcome.name())
						!= scheduler.getRegionCommitOutcome())
				|| (regionOutcome
						!= RegionManager.RestorationCommitOutcome.APPLIED
					&& (membershipRemoved || membershipRegistered))) {
				throw new IllegalArgumentException(
					"Region/scheduler consumption composition is inconsistent");
			}
		}

		private static RestorationRegionCommitConsumptionExecution compose(
			final RestorationOneShotConsumptionExecution scheduler,
			final RegionManager.RestorationCommitResult region) {
			return region == null
				? new RestorationRegionCommitConsumptionExecution(
					scheduler, null, null, false, false, 0)
				: new RestorationRegionCommitConsumptionExecution(
					scheduler, region.getOutcome(), region.getReason(),
					region.isMembershipRemoved(),
					region.isMembershipRegistered(),
					region.getBoundaryCount());
		}

		RestorationOneShotConsumptionExecution getSchedulerResult() {
			return scheduler;
		}
		RegionManager.RestorationCommitOutcome getRegionOutcome() {
			return regionOutcome;
		}
		RegionManager.RestorationCommitReason getRegionReason() {
			return regionReason;
		}
		boolean isRegionCommitInvoked() { return regionOutcome != null; }
		boolean isMembershipRemoved() { return membershipRemoved; }
		boolean isMembershipRegistered() { return membershipRegistered; }
		int getBoundaryCount() { return boundaryCount; }
		boolean isMutationPerformed() {
			return regionOutcome
				== RegionManager.RestorationCommitOutcome.APPLIED;
		}
		boolean isEventTerminallyConsumed() {
			return scheduler.isEventTerminallyConsumed();
		}
		boolean isExactRegistrationRetained() {
			return scheduler.isExactRegistrationRetained();
		}
		boolean isRegionResultRetained() { return false; }
		boolean isRequestRetained() { return false; }
		boolean isRuntimeHandleRetained() { return false; }
		boolean isCallbackInvoked() { return false; }
		boolean isEventReschedule() { return false; }
		boolean isExecutableRestoration() { return false; }
		boolean isCommitToken() { return false; }
		boolean isArrivalGate() { return false; }
		boolean isLifecycleAuthority() { return false; }
	}

	/**
	 * Detached outcome of the composed outer scheduler fence and inner Region
	 * target observation. A failed outer postcheck never retains target facts.
	 */
	static final class RestorationTargetRevalidationExecution {
		private final RestorationRegistrationFenceReason outerFenceReason;
		private final GameTickEventRestorationTargetRevalidation target;
		private final long lifecycleVersionBeforeOperation;
		private final long lifecycleVersionAfterOperation;
		private final boolean operationInvoked;

		private RestorationTargetRevalidationExecution(
			final RestorationRegistrationFenceReason outerFenceReason,
			final GameTickEventRestorationTargetRevalidation target,
			final long lifecycleVersionBeforeOperation,
			final long lifecycleVersionAfterOperation,
			final boolean operationInvoked) {
			this.outerFenceReason = Objects.requireNonNull(
				outerFenceReason, "outerFenceReason");
			this.target = target;
			this.lifecycleVersionBeforeOperation =
				lifecycleVersionBeforeOperation;
			this.lifecycleVersionAfterOperation =
				lifecycleVersionAfterOperation;
			this.operationInvoked = operationInvoked;
			boolean outerAccepted = outerFenceReason
				== RestorationRegistrationFenceReason.OPERATION_COMPLETED;
			if (outerAccepted != (target != null)
				|| (operationInvoked
					!= (lifecycleVersionBeforeOperation > 0L
						&& lifecycleVersionAfterOperation > 0L))
				|| (outerAccepted
					&& (!operationInvoked
						|| lifecycleVersionBeforeOperation
							!= lifecycleVersionAfterOperation))
				|| (!outerAccepted && target != null)) {
				throw new IllegalArgumentException(
					"Restoration target revalidation result is inconsistent");
			}
		}

		private static RestorationTargetRevalidationExecution refused(
			final RestorationRegistrationFenceReason outerFenceReason,
			final long lifecycleVersionBeforeOperation,
			final long lifecycleVersionAfterOperation,
			final boolean operationInvoked) {
			return new RestorationTargetRevalidationExecution(
				outerFenceReason, null, lifecycleVersionBeforeOperation,
				lifecycleVersionAfterOperation, operationInvoked);
		}

		private static RestorationTargetRevalidationExecution observed(
			final GameTickEventRestorationTargetRevalidation target,
			final long lifecycleVersionBeforeOperation,
			final long lifecycleVersionAfterOperation) {
			return new RestorationTargetRevalidationExecution(
				RestorationRegistrationFenceReason.OPERATION_COMPLETED,
				Objects.requireNonNull(target, "target"),
				lifecycleVersionBeforeOperation,
				lifecycleVersionAfterOperation, true);
		}

		RestorationRegistrationFenceReason getOuterFenceReason() {
			return outerFenceReason;
		}
		GameTickEventRestorationTargetRevalidation getTarget() {
			return target;
		}
		long getLifecycleVersionBeforeOperation() {
			return lifecycleVersionBeforeOperation;
		}
		long getLifecycleVersionAfterOperation() {
			return lifecycleVersionAfterOperation;
		}
		boolean isOperationInvoked() { return operationInvoked; }
		boolean isOuterFenceAccepted() {
			return outerFenceReason
				== RestorationRegistrationFenceReason.OPERATION_COMPLETED;
		}
		boolean isTimingStableAcrossOperation() {
			return isOuterFenceAccepted()
				&& lifecycleVersionBeforeOperation
					== lifecycleVersionAfterOperation;
		}
		boolean isRuntimeTargetLookupPerformed() { return operationInvoked; }
		boolean isRuntimeRevalidationPerformed() {
			return target != null && target.isRuntimeRevalidationPerformed();
		}
		boolean isEventHandleRetained() { return false; }
		boolean isRegionHandleRetained() { return false; }
		boolean isEntityHandleRetained() { return false; }
		boolean isMutationPerformed() { return false; }
		boolean isExecutableRestoration() { return false; }
		boolean isCommitToken() { return false; }
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
