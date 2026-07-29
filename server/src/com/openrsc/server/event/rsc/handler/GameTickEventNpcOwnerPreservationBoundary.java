package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.world.coordinate.LayeredAuthoredPlacementIdentity;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerPreservationBoundaryObservation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerPreservationBoundaryObservation.OwnerBoundaryState;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerPreservationRequirements;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerPreservationRequirements.OwnerRequirement;
import com.openrsc.server.util.EntityList;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Package-local adapter between detached layered-map owner requirements and the
 * generic scheduler Store.
 *
 * <p>Every registration uses the Store's existing execution/registration
 * fence. Event and NPC lifecycle gates are acquired iteratively in stable
 * order, then World-list correlation runs inside that complete scope without
 * adding coordinate, NPC, or World dependencies to the scheduler Store
 * itself.</p>
 */
final class GameTickEventNpcOwnerPreservationBoundary {
	private GameTickEventNpcOwnerPreservationBoundary() {
	}

	static LayeredPackedRegionNpcOwnerPreservationBoundaryObservation capture(
		final GameTickEventStore eventStore,
		final EntityList<Npc> worldNpcs,
		final LayeredPackedRegionNpcOwnerPreservationRequirements requirements,
		final long boundaryObservedAtTick,
		final int maximumOwners,
		final boolean regionLifecycleBoundaryHeld) {
		return capture(
			eventStore, worldNpcs, requirements, boundaryObservedAtTick,
			maximumOwners, regionLifecycleBoundaryHeld, null);
	}

	/**
	 * Runs one caller operation only while the complete source-bound owner
	 * scope remains active. A refused boundary never invokes the operation, and
	 * the supplied scope is invalidated before any enclosing gate releases.
	 */
	static boolean withinPreservationScope(
		final GameTickEventStore eventStore,
		final EntityList<Npc> worldNpcs,
		final LayeredPackedRegionNpcOwnerPreservationRequirements requirements,
		final long boundaryObservedAtTick,
		final int maximumOwners,
		final boolean regionLifecycleBoundaryHeld,
		final ScopedPreservationOperation operation) {
		final ScopedPreservationOperation checkedOperation =
			Objects.requireNonNull(operation, "operation");
		final boolean[] invoked = new boolean[1];
		capture(
			eventStore, worldNpcs, requirements, boundaryObservedAtTick,
			maximumOwners, regionLifecycleBoundaryHeld, scope -> {
				checkedOperation.execute(scope);
				invoked[0] = true;
			});
		return invoked[0];
	}

	private static
		LayeredPackedRegionNpcOwnerPreservationBoundaryObservation capture(
			final GameTickEventStore eventStore,
			final EntityList<Npc> worldNpcs,
			final LayeredPackedRegionNpcOwnerPreservationRequirements
				requirements,
			final long boundaryObservedAtTick,
			final int maximumOwners,
			final boolean regionLifecycleBoundaryHeld,
			final ScopedPreservationOperation scopeOperation) {
		GameTickEventStore checkedStore =
			Objects.requireNonNull(eventStore, "eventStore");
		EntityList<Npc> checkedWorldNpcs =
			Objects.requireNonNull(worldNpcs, "worldNpcs");
		LayeredPackedRegionNpcOwnerPreservationRequirements checked =
			Objects.requireNonNull(requirements, "requirements");
		if (boundaryObservedAtTick < checked.getEventObservedAtTick()) {
			throw new IllegalArgumentException(
				"NPC owner preservation boundary is older than requirements");
		}

		GameTickEventStore.RegistrationSnapshot snapshot =
			checkedStore.getTrackedEventRegistrationSnapshot();
		boolean schedulerMatched =
			snapshot.getSchedulerInstanceIdentity().equals(
				checked.getSchedulerInstanceIdentity());
		NpcOwnerBoundaryCapture capture = new NpcOwnerBoundaryCapture();
		if (!checked.isNpcRequirementSetComplete() || !schedulerMatched) {
			return observation(
				checked, boundaryObservedAtTick, schedulerMatched, false,
				capture, maximumOwners);
		}

		List<ExpectedNpcOwner> expectedOwners = expectedOwners(checked);
		List<ExpectedNpcOwnerRegistration> expectedRegistrations =
			resolveRegistrations(snapshot, expectedOwners, capture);
		if (capture.registrationSetComplete) {
			captureIterativeBoundaries(
				checkedStore, expectedRegistrations, expectedOwners,
				checkedWorldNpcs, checked, capture,
				regionLifecycleBoundaryHeld, scopeOperation);
		}
		return observation(
			checked, boundaryObservedAtTick, true,
			capture.registrationSetComplete, capture, maximumOwners);
	}

	private static List<ExpectedNpcOwnerRegistration> resolveRegistrations(
		final GameTickEventStore.RegistrationSnapshot snapshot,
		final List<ExpectedNpcOwner> owners,
		final NpcOwnerBoundaryCapture capture) {
		Map<Long, GameTickEvent> bySequence =
			new LinkedHashMap<Long, GameTickEvent>();
		for (GameTickEventStore.RegisteredEvent registration
			: snapshot.getRegistrations()) {
			Long sequence =
				Long.valueOf(registration.getRegistrationSequence());
			if (bySequence.put(sequence, registration.getEvent()) != null) {
				capture.registrationSetComplete = false;
				return new ArrayList<ExpectedNpcOwnerRegistration>();
			}
		}

		List<ExpectedNpcOwnerRegistration> resolved =
			new ArrayList<ExpectedNpcOwnerRegistration>();
		IdentityHashMap<GameTickEvent, Boolean> seenEvents =
			new IdentityHashMap<GameTickEvent, Boolean>();
		for (ExpectedNpcOwner owner : owners) {
			for (Long sequence
				: owner.requirement.getEventRegistrationSequences()) {
				GameTickEvent event = bySequence.get(sequence);
				if (event == null || seenEvents.put(event, Boolean.TRUE) != null) {
					capture.registrationSetComplete = false;
					return resolved;
				}
				ExpectedNpcOwnerRegistration registration =
					new ExpectedNpcOwnerRegistration(
						sequence.longValue(), event);
				owner.registrations.add(registration);
				resolved.add(registration);
			}
		}
		return resolved;
	}

	private static void captureIterativeBoundaries(
		final GameTickEventStore eventStore,
		final List<ExpectedNpcOwnerRegistration> registrations,
		final List<ExpectedNpcOwner> owners,
		final EntityList<Npc> worldNpcs,
		final LayeredPackedRegionNpcOwnerPreservationRequirements requirements,
		final NpcOwnerBoundaryCapture capture,
		final boolean regionLifecycleBoundaryHeld,
		final ScopedPreservationOperation scopeOperation) {
		List<GameTickEvent> events =
			new ArrayList<GameTickEvent>(registrations.size());
		List<Long> registrationSequences =
			new ArrayList<Long>(registrations.size());
		for (ExpectedNpcOwnerRegistration registration : registrations) {
			events.add(registration.event);
			registrationSequences.add(
				Long.valueOf(registration.registrationSequence));
		}
		GameTickEvent.withinOwnerPreservationLifecycleBoundaries(
			events, eventBoundary -> {
				if (!eventBoundary.isCompleteSetHeld()
					|| eventBoundary.getEventCount()
						!= registrations.size()) {
					throw new IllegalStateException(
						"Event preservation set supplied invalid evidence");
				}
				capture.eventExecutionBoundaryCount =
					eventBoundary.getEventCount();
				capture.eventTimingBoundaryCount =
					eventBoundary.getEventCount();
				boolean registrationsHeld =
					eventStore.withValidatedRegistrationSetFence(
						events, registrationSequences,
						requirements.getSchedulerInstanceIdentity(),
						registrationBoundary -> {
							if (!registrationBoundary
									.isEventBoundarySetHeld()
								|| registrationBoundary
									.getRegistrationCount()
										!= registrations.size()) {
								throw new IllegalStateException(
									"Registration set supplied invalid evidence");
							}
							captureWorldAndNpcBoundaries(
								owners, worldNpcs, requirements, capture,
								regionLifecycleBoundaryHeld, scopeOperation);
						});
				if (!registrationsHeld) {
					capture.registrationSetComplete = false;
				}
			});
	}

	private static void captureWorldAndNpcBoundaries(
		final List<ExpectedNpcOwner> owners,
		final EntityList<Npc> worldNpcs,
		final LayeredPackedRegionNpcOwnerPreservationRequirements requirements,
		final NpcOwnerBoundaryCapture capture,
		final boolean regionLifecycleBoundaryHeld,
		final ScopedPreservationOperation scopeOperation) {
		synchronized (worldNpcs) {
			capture.worldRegistrationBoundaryHeld =
				Thread.holdsLock(worldNpcs);
			List<CorrelatedOwner> correlatedOwners =
				new ArrayList<CorrelatedOwner>(owners.size());
			for (ExpectedNpcOwner owner : owners) {
				CorrelatedOwner correlated =
					captureOwnerCorrelation(owner, worldNpcs);
				correlatedOwners.add(correlated);
				capture.ownerStates.add(correlated.state);
			}
			if (allExact(correlatedOwners)) {
				captureIterativeNpcLifecycleBoundaries(
					correlatedOwners, worldNpcs, requirements, capture,
					regionLifecycleBoundaryHeld, scopeOperation);
			}
		}
	}

	private static boolean allExact(
		final List<CorrelatedOwner> owners) {
		for (CorrelatedOwner owner : owners) {
			if (!owner.exact) {
				return false;
			}
		}
		return true;
	}

	private static void captureIterativeNpcLifecycleBoundaries(
		final List<CorrelatedOwner> owners,
		final EntityList<Npc> worldNpcs,
		final LayeredPackedRegionNpcOwnerPreservationRequirements requirements,
		final NpcOwnerBoundaryCapture capture,
		final boolean regionLifecycleBoundaryHeld,
		final ScopedPreservationOperation scopeOperation) {
		List<Npc> ownerNpcs = new ArrayList<Npc>(owners.size());
		for (CorrelatedOwner owner : owners) {
			ownerNpcs.add(owner.npc);
		}
		Npc.withinLayeredOwnerPreservationLifecycleBoundaries(
			ownerNpcs, boundary -> {
				if (!boundary.isCompleteSetHeld()
					|| boundary.getOwnerCount() != owners.size()) {
					throw new IllegalStateException(
						"NPC preservation set supplied invalid evidence");
				}
				List<OwnerBoundaryState> revalidated =
					new ArrayList<OwnerBoundaryState>(owners.size());
				for (CorrelatedOwner owner : owners) {
					CorrelatedOwner current =
						captureOwnerCorrelation(owner.expected, worldNpcs);
					if (!current.exact || current.npc != owner.npc) {
						return;
					}
					revalidated.add(current.state);
				}
				capture.ownerStates.clear();
				capture.ownerStates.addAll(revalidated);
				capture.npcLifecycleBoundaryCount =
					boundary.getOwnerCount();
				capture.regionAbsenceQuiescenceHeld =
					regionLifecycleBoundaryHeld;
				if (scopeOperation != null
					&& capture.regionAbsenceQuiescenceHeld) {
					GameTickEventNpcOwnerPreservationScope scope =
						GameTickEventNpcOwnerPreservationScope.open(
							requirements,
							capture.eventExecutionBoundaryCount,
							capture.npcLifecycleBoundaryCount,
							capture.worldRegistrationBoundaryHeld,
							capture.regionAbsenceQuiescenceHeld);
					try {
						scopeOperation.execute(scope);
					} finally {
						scope.invalidate();
					}
				}
			});
	}

	private static CorrelatedOwner captureOwnerCorrelation(
		final ExpectedNpcOwner expected,
		final EntityList<Npc> worldNpcs) {
		Npc eventOwner = null;
		boolean ownerIdentityMatched = true;
		boolean sameInstance = true;
		int validatedLinks = 0;
		for (ExpectedNpcOwnerRegistration registration
			: expected.registrations) {
			Npc observed = registration.event.getNpcOwner();
			validatedLinks++;
			if (observed == null
				|| !matches(expected.requirement, observed)) {
				ownerIdentityMatched = false;
			}
			if (eventOwner == null) {
				eventOwner = observed;
			} else if (eventOwner != observed) {
				sameInstance = false;
			}
		}

		int worldIdentityMatches = 0;
		boolean sameInstanceInWorld = false;
		for (Npc npc : worldNpcs) {
			if (matches(expected.requirement, npc)) {
				worldIdentityMatches++;
				sameInstanceInWorld |= npc == eventOwner;
			}
		}
		boolean active = sameInstanceInWorld && eventOwner != null
			&& !eventOwner.isRemoved() && !eventOwner.isRespawning()
			&& !eventOwner.isUnregistering();
		OwnerBoundaryState state = OwnerBoundaryState.observe(
			expected.requirement, validatedLinks, ownerIdentityMatched,
			sameInstance, worldIdentityMatches, sameInstanceInWorld,
			active);
		boolean exact =
			validatedLinks
				== expected.requirement
					.getEventRegistrationSequences().size()
			&& ownerIdentityMatched && sameInstance
			&& worldIdentityMatches == 1 && sameInstanceInWorld && active;
		return new CorrelatedOwner(
			expected, state, exact ? eventOwner : null, exact);
	}

	private static boolean matches(
		final OwnerRequirement requirement,
		final Npc npc) {
		LayeredAuthoredPlacementIdentity identity =
			npc.getAuthoredPlacementIdentity();
		return identity != null
			&& identity.getGeneration() == requirement.getGeneration()
			&& identity.getPackedRegionX()
				== requirement.getPackedRegionX()
			&& identity.getPackedRegionY()
				== requirement.getPackedRegionY()
			&& identity.getSourceOrdinal()
				== requirement.getSourceOrdinal()
			&& "NPC_SPAWN".equals(
				identity.getConstructionKind().name())
			&& npc.getID() == requirement.getRuntimeNpcId();
	}

	private static List<ExpectedNpcOwner> expectedOwners(
		final LayeredPackedRegionNpcOwnerPreservationRequirements
			requirements) {
		List<ExpectedNpcOwner> owners =
			new ArrayList<ExpectedNpcOwner>(
				requirements.getUniqueNpcOwnerCount());
		for (OwnerRequirement requirement : requirements.getOwners()) {
			owners.add(new ExpectedNpcOwner(requirement));
		}
		return owners;
	}

	private static
		LayeredPackedRegionNpcOwnerPreservationBoundaryObservation observation(
			final LayeredPackedRegionNpcOwnerPreservationRequirements
				requirements,
			final long boundaryObservedAtTick,
			final boolean schedulerMatched,
			final boolean registrationSetComplete,
			final NpcOwnerBoundaryCapture capture,
			final int maximumOwners) {
		return LayeredPackedRegionNpcOwnerPreservationBoundaryObservation
			.observe(
				requirements, boundaryObservedAtTick, schedulerMatched,
				registrationSetComplete,
				capture.eventExecutionBoundaryCount,
				capture.eventTimingBoundaryCount,
				capture.worldRegistrationBoundaryHeld,
				capture.npcLifecycleBoundaryCount,
				capture.regionAbsenceQuiescenceHeld,
				capture.ownerStates, maximumOwners);
	}

	private static final class ExpectedNpcOwner {
		private final OwnerRequirement requirement;
		private final List<ExpectedNpcOwnerRegistration> registrations =
			new ArrayList<ExpectedNpcOwnerRegistration>();

		private ExpectedNpcOwner(final OwnerRequirement requirement) {
			this.requirement = requirement;
		}
	}

	private static final class ExpectedNpcOwnerRegistration {
		private final long registrationSequence;
		private final GameTickEvent event;

		private ExpectedNpcOwnerRegistration(
			final long registrationSequence,
			final GameTickEvent event) {
			this.registrationSequence = registrationSequence;
			this.event = event;
		}
	}

	private static final class CorrelatedOwner {
		private final ExpectedNpcOwner expected;
		private final OwnerBoundaryState state;
		private final Npc npc;
		private final boolean exact;

		private CorrelatedOwner(
			final ExpectedNpcOwner expected,
			final OwnerBoundaryState state,
			final Npc npc,
			final boolean exact) {
			this.expected = expected;
			this.state = state;
			this.npc = npc;
			this.exact = exact;
		}
	}

	private static final class NpcOwnerBoundaryCapture {
		private boolean registrationSetComplete = true;
		private int eventExecutionBoundaryCount;
		private int eventTimingBoundaryCount;
		private boolean worldRegistrationBoundaryHeld;
		private int npcLifecycleBoundaryCount;
		private boolean regionAbsenceQuiescenceHeld;
		private final List<OwnerBoundaryState> ownerStates =
			new ArrayList<OwnerBoundaryState>();
	}

	@FunctionalInterface
	interface ScopedPreservationOperation {
		void execute(GameTickEventNpcOwnerPreservationScope scope);
	}
}
