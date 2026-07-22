package com.openrsc.server.model.world.coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable, dormant classification of scheduled-event affinity to one exact
 * packed-region candidate order.
 *
 * <p>Event ownership and event spatial effect are deliberately independent. A
 * Player or NPC owner supplies only a current-position hint unless the event
 * itself declares an exact spatial effect. A null owner supplies no evidence
 * that an event is global: anonymous scenery, item, and NPC callbacks may
 * capture a location that the current scheduler cannot inspect.</p>
 *
 * <p>This value accepts detached primitive inputs only. It neither reads nor
 * retains a live event, Mob, Region, scheduler, callback, UUID, descriptor, or
 * implementation class. It cannot stop, remove, reschedule, recreate, or run
 * an event and grants no preservation, reload, teardown, or lifecycle
 * authority.</p>
 */
public final class LayeredPackedRegionEventOwnershipInventory {
	public static final int MAXIMUM_EVENTS = 65536;
	public static final int MAXIMUM_SPATIAL_REFERENCES = 262144;
	private static final Pattern SCHEDULER_INSTANCE_IDENTITY = Pattern.compile(
		"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

	private static final Comparator<PackedSource> SOURCE_ORDER =
		new Comparator<PackedSource>() {
			@Override
			public int compare(
				final PackedSource left,
				final PackedSource right) {
				int byY = Integer.compare(
					left.getPackedRegionY(), right.getPackedRegionY());
				return byY != 0 ? byY : Integer.compare(
					left.getPackedRegionX(), right.getPackedRegionX());
			}
		};

	private static final Comparator<SpatialReference> REFERENCE_ORDER =
		new Comparator<SpatialReference>() {
			@Override
			public int compare(
				final SpatialReference left,
				final SpatialReference right) {
				int compared = Integer.compare(left.getY(), right.getY());
				if (compared != 0) { return compared; }
				compared = Integer.compare(left.getX(), right.getX());
				if (compared != 0) { return compared; }
				return left.getRole().compareTo(right.getRole());
			}
		};

	private final long proposalGeneration;
	private final long observedAtTick;
	private final String schedulerInstanceIdentity;
	private final List<SourceRecord> sources;
	private final List<EventRecord> events;
	private final int spatialReferenceCount;
	private final int exactSpatialEventCount;
	private final int ownerPositionHintEventCount;
	private final int nonSpatialGlobalEventCount;
	private final int unattributedEventCount;
	private final int candidateRelatedEventCount;
	private final int restorationStateAvailableEventCount;
	private final int detachedCallbackPayloadCompleteEventCount;
	private final int executionSemanticsCapturedEventCount;
	private final int atomicTimingCapturedEventCount;
	private final int targetBindingRequirementCapturedEventCount;
	private final int targetBindingCompleteEventCount;
	private final int arrivalOrderingCapturedEventCount;

	private LayeredPackedRegionEventOwnershipInventory(
		final long proposalGeneration,
		final long observedAtTick,
		final String schedulerInstanceIdentity,
		final List<SourceRecord> sources,
		final List<EventRecord> events,
		final int spatialReferenceCount,
		final int exactSpatialEventCount,
		final int ownerPositionHintEventCount,
		final int nonSpatialGlobalEventCount,
		final int unattributedEventCount,
		final int candidateRelatedEventCount,
		final int restorationStateAvailableEventCount,
		final int detachedCallbackPayloadCompleteEventCount,
		final int executionSemanticsCapturedEventCount,
		final int atomicTimingCapturedEventCount,
		final int targetBindingRequirementCapturedEventCount,
		final int targetBindingCompleteEventCount,
		final int arrivalOrderingCapturedEventCount) {
		this.proposalGeneration = proposalGeneration;
		this.observedAtTick = observedAtTick;
		this.schedulerInstanceIdentity = schedulerInstanceIdentity;
		this.sources = Collections.unmodifiableList(sources);
		this.events = Collections.unmodifiableList(events);
		this.spatialReferenceCount = spatialReferenceCount;
		this.exactSpatialEventCount = exactSpatialEventCount;
		this.ownerPositionHintEventCount = ownerPositionHintEventCount;
		this.nonSpatialGlobalEventCount = nonSpatialGlobalEventCount;
		this.unattributedEventCount = unattributedEventCount;
		this.candidateRelatedEventCount = candidateRelatedEventCount;
		this.restorationStateAvailableEventCount =
			restorationStateAvailableEventCount;
		this.detachedCallbackPayloadCompleteEventCount =
			detachedCallbackPayloadCompleteEventCount;
		this.executionSemanticsCapturedEventCount =
			executionSemanticsCapturedEventCount;
		this.atomicTimingCapturedEventCount = atomicTimingCapturedEventCount;
		this.targetBindingRequirementCapturedEventCount =
			targetBindingRequirementCapturedEventCount;
		this.targetBindingCompleteEventCount =
			targetBindingCompleteEventCount;
		this.arrivalOrderingCapturedEventCount =
			arrivalOrderingCapturedEventCount;
	}

	/**
	 * Correlates one bounded detached event snapshot with one exact canonical
	 * candidate order. Event ordinals must be contiguous snapshot order.
	 */
	public static LayeredPackedRegionEventOwnershipInventory inventory(
		final long proposalGeneration,
		final long observedAtTick,
		final String schedulerInstanceIdentity,
		final List<PackedSource> packedSources,
		final List<EventState> eventStates,
		final int maximumPackedSources,
		final int maximumEvents,
		final int maximumSpatialReferences) {
		Objects.requireNonNull(packedSources, "packedSources");
		Objects.requireNonNull(eventStates, "eventStates");
		if (proposalGeneration < 0L || observedAtTick < 0L
			|| schedulerInstanceIdentity == null
			|| !SCHEDULER_INSTANCE_IDENTITY.matcher(
				schedulerInstanceIdentity).matches()
			|| maximumPackedSources < 0
			|| maximumPackedSources
				> LayeredPackedRegionRetirementRefinementProposal
					.MAXIMUM_CANDIDATE_SOURCES
			|| packedSources.size() > maximumPackedSources
			|| maximumEvents < 0 || maximumEvents > MAXIMUM_EVENTS
			|| eventStates.size() > maximumEvents
			|| maximumSpatialReferences < 0
			|| maximumSpatialReferences > MAXIMUM_SPATIAL_REFERENCES) {
			throw new IllegalArgumentException(
				"Event ownership inventory exceeds its budget");
		}

		List<PackedSource> checkedSources =
			new ArrayList<PackedSource>(packedSources.size());
		Map<Long, Integer> sourceOrdinals =
			new LinkedHashMap<Long, Integer>();
		PackedSource previousSource = null;
		for (int index = 0; index < packedSources.size(); index++) {
			PackedSource source = Objects.requireNonNull(
				packedSources.get(index), "packedSources[" + index + "]");
			if (previousSource != null
				&& SOURCE_ORDER.compare(previousSource, source) >= 0) {
				throw new IllegalArgumentException(
					"Packed sources must be unique and canonically ordered");
			}
			previousSource = source;
			checkedSources.add(source);
			sourceOrdinals.put(Long.valueOf(sourceKey(
				source.getPackedRegionX(), source.getPackedRegionY())),
				Integer.valueOf(index));
		}

		List<List<Integer>> exactBySource =
			emptyEventOrdinalsBySource(checkedSources.size());
		List<List<Integer>> hintsBySource =
			emptyEventOrdinalsBySource(checkedSources.size());
		List<List<Integer>> restorationBySource =
			emptyEventOrdinalsBySource(checkedSources.size());
		List<EventRecord> eventRecords =
			new ArrayList<EventRecord>(eventStates.size());
		int referenceCount = 0;
		int exactCount = 0;
		int hintCount = 0;
		int globalCount = 0;
		int unknownCount = 0;
		int candidateEventCount = 0;
		int restorationAvailableCount = 0;
		int callbackPayloadCompleteCount = 0;
		int executionSemanticsCapturedCount = 0;
		int atomicTimingCapturedCount = 0;
		int targetBindingRequirementCapturedCount = 0;
		int targetBindingCompleteCount = 0;
		int arrivalOrderingCapturedCount = 0;
		long previousRegistrationSequence = 0L;
		for (int index = 0; index < eventStates.size(); index++) {
			EventState state = Objects.requireNonNull(
				eventStates.get(index), "eventStates[" + index + "]");
			if (state.getSnapshotOrdinal() != index) {
				throw new IllegalArgumentException(
					"Event ordinals must preserve contiguous snapshot order");
			}
			if (state.getRegistrationSequence()
				<= previousRegistrationSequence) {
				throw new IllegalArgumentException(
					"Event registration identities must preserve accepted order");
			}
			previousRegistrationSequence = state.getRegistrationSequence();
			referenceCount = Math.addExact(
				referenceCount, state.getSpatialReferences().size());
			if (referenceCount > maximumSpatialReferences) {
				throw new IllegalArgumentException(
					"Event ownership inventory exceeds its reference budget");
			}

			Set<Integer> candidateOrdinals = new LinkedHashSet<Integer>();
			for (SpatialReference reference : state.getSpatialReferences()) {
				Integer sourceOrdinal = sourceOrdinals.get(Long.valueOf(sourceKey(
					reference.getX() / WorldRegionKey.REGION_SIZE,
					reference.getY() / WorldRegionKey.REGION_SIZE)));
				if (sourceOrdinal != null) {
					candidateOrdinals.add(sourceOrdinal);
				}
			}
			for (Integer sourceOrdinal : candidateOrdinals) {
				List<List<Integer>> destination =
					state.getAttributionKind() == AttributionKind.EXACT_SPATIAL
						? exactBySource : hintsBySource;
				destination.get(sourceOrdinal.intValue()).add(Integer.valueOf(index));
				if (state.getRestorationState().getKind()
						!= RestorationKind.UNAVAILABLE) {
					restorationBySource.get(sourceOrdinal.intValue()).add(
						Integer.valueOf(index));
				}
			}
			if (state.getRestorationState().getKind()
					!= RestorationKind.UNAVAILABLE) {
				restorationAvailableCount++;
				callbackPayloadCompleteCount += state.getRestorationState()
					.isDetachedCallbackPayloadComplete() ? 1 : 0;
				executionSemanticsCapturedCount += state.getRestorationState()
					.isExecutionSemanticsCaptured() ? 1 : 0;
				targetBindingRequirementCapturedCount +=
					state.getRestorationState()
						.isTargetBindingRequirementCaptured() ? 1 : 0;
				targetBindingCompleteCount += state.getRestorationState()
					.isTargetBindingComplete() ? 1 : 0;
				arrivalOrderingCapturedCount += state.getRestorationState()
					.isArrivalOrderingCaptured() ? 1 : 0;
			}
			atomicTimingCapturedCount +=
				state.isAtomicTimingCaptured() ? 1 : 0;
			candidateEventCount += candidateOrdinals.isEmpty() ? 0 : 1;
			switch (state.getAttributionKind()) {
				case EXACT_SPATIAL:
					exactCount++;
					break;
				case OWNER_POSITION_HINT:
					hintCount++;
					break;
				case NON_SPATIAL_GLOBAL:
					globalCount++;
					break;
				case UNATTRIBUTED:
					unknownCount++;
					break;
				default:
					throw new IllegalStateException(
						"Unhandled event attribution kind");
			}
			eventRecords.add(new EventRecord(state, candidateOrdinals));
		}

		List<SourceRecord> sourceRecords =
			new ArrayList<SourceRecord>(checkedSources.size());
		for (int index = 0; index < checkedSources.size(); index++) {
			sourceRecords.add(new SourceRecord(
				checkedSources.get(index), exactBySource.get(index),
				hintsBySource.get(index), restorationBySource.get(index),
				unknownCount));
		}
		return new LayeredPackedRegionEventOwnershipInventory(
			proposalGeneration, observedAtTick, schedulerInstanceIdentity,
			sourceRecords, eventRecords,
			referenceCount, exactCount, hintCount, globalCount, unknownCount,
			candidateEventCount, restorationAvailableCount,
			callbackPayloadCompleteCount, executionSemanticsCapturedCount,
			atomicTimingCapturedCount,
			targetBindingRequirementCapturedCount,
			targetBindingCompleteCount, arrivalOrderingCapturedCount);
	}

	private static List<List<Integer>> emptyEventOrdinalsBySource(
		final int sourceCount) {
		List<List<Integer>> values =
			new ArrayList<List<Integer>>(sourceCount);
		for (int index = 0; index < sourceCount; index++) {
			values.add(new ArrayList<Integer>());
		}
		return values;
	}

	private static long sourceKey(
		final int packedRegionX,
		final int packedRegionY) {
		return ((long) packedRegionX << 32)
			^ (packedRegionY & 0xffffffffL);
	}

	public long getProposalGeneration() { return proposalGeneration; }
	public long getObservedAtTick() { return observedAtTick; }
	public String getSchedulerInstanceIdentity() {
		return schedulerInstanceIdentity;
	}
	public List<SourceRecord> getSources() { return sources; }
	public int getSourceCount() { return sources.size(); }
	public List<EventRecord> getEvents() { return events; }
	public int getEventCount() { return events.size(); }
	public int getSpatialReferenceCount() { return spatialReferenceCount; }
	public int getExactSpatialEventCount() { return exactSpatialEventCount; }
	public int getOwnerPositionHintEventCount() {
		return ownerPositionHintEventCount;
	}
	public int getNonSpatialGlobalEventCount() {
		return nonSpatialGlobalEventCount;
	}
	public int getUnattributedEventCount() { return unattributedEventCount; }
	public int getCandidateRelatedEventCount() {
		return candidateRelatedEventCount;
	}
	public int getRestorationStateAvailableEventCount() {
		return restorationStateAvailableEventCount;
	}
	public int getDetachedCallbackPayloadCompleteEventCount() {
		return detachedCallbackPayloadCompleteEventCount;
	}
	public int getExecutionSemanticsCapturedEventCount() {
		return executionSemanticsCapturedEventCount;
	}
	public boolean isExecutionSemanticsCaptured() {
		return executionSemanticsCapturedEventCount > 0;
	}
	public boolean isExecutionSemanticsComplete() {
		return executionSemanticsCapturedEventCount
			== restorationStateAvailableEventCount;
	}
	public int getAtomicTimingCapturedEventCount() {
		return atomicTimingCapturedEventCount;
	}
	public boolean isAtomicTimingCaptured() {
		return atomicTimingCapturedEventCount > 0;
	}
	public boolean isAtomicTimingComplete() {
		return atomicTimingCapturedEventCount
			== restorationStateAvailableEventCount;
	}
	public int getTargetBindingRequirementCapturedEventCount() {
		return targetBindingRequirementCapturedEventCount;
	}
	public boolean isTargetBindingRequirementCaptured() {
		return targetBindingRequirementCapturedEventCount > 0;
	}
	public boolean isTargetBindingRequirementComplete() {
		return targetBindingRequirementCapturedEventCount
			== restorationStateAvailableEventCount;
	}
	public int getTargetBindingCompleteEventCount() {
		return targetBindingCompleteEventCount;
	}
	public boolean isTargetBindingComplete() {
		return targetBindingCompleteEventCount
			== restorationStateAvailableEventCount;
	}
	public int getArrivalOrderingCapturedEventCount() {
		return arrivalOrderingCapturedEventCount;
	}
	public boolean isArrivalOrderingCaptured() {
		return arrivalOrderingCapturedEventCount > 0;
	}
	public boolean isArrivalOrderingComplete() {
		return arrivalOrderingCapturedEventCount
			== restorationStateAvailableEventCount;
	}
	public boolean isCandidateAttributionComplete() {
		return ownerPositionHintEventCount == 0 && unattributedEventCount == 0;
	}
	public int getRestorationStateCompleteEventCount() { return 0; }
	public int getRegistrationIdentityCapturedEventCount() {
		return events.size();
	}
	public boolean isRegistrationIdentityCaptured() { return true; }
	public boolean isRegistrationIdentityComplete() { return true; }
	public boolean isSchedulerInstanceIdentityCaptured() { return true; }

	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedPrimitiveCopy() { return true; }
	public boolean isCallbackStateCaptured() { return false; }
	public boolean isSchedulerIdentityCaptured() { return false; }
	public boolean isPreservationPerformed() { return false; }
	public boolean isReloadRequest() { return false; }
	public boolean isEventCancellation() { return false; }
	public boolean isEventReschedule() { return false; }
	public boolean isEntityRegistry() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isTeardownTransaction() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	public enum OwnerKind {
		NONE,
		PLAYER,
		NPC
	}

	public enum AttributionKind {
		EXACT_SPATIAL,
		OWNER_POSITION_HINT,
		NON_SPATIAL_GLOBAL,
		UNATTRIBUTED
	}

	public enum SpatialRole {
		OWNER_CURRENT_POSITION,
		SUBJECT_CURRENT_POSITION,
		TARGET_CURRENT_POSITION,
		FIXED_EFFECT_LOCATION
	}

	public enum RestorationKind {
		UNAVAILABLE,
		SCENERY_SPAWN,
		SCENERY_REMOVE
	}

	public enum TargetBindingEvidence {
		UNAVAILABLE,
		NOT_REQUIRED,
		AUTHORED_PLACEMENT_IDENTITY,
		LIVE_ENTITY_REFERENCE_ONLY
	}

	public enum TargetSubject {
		UNAVAILABLE,
		AUTHORED_DESTINATION_SLOT,
		AUTHORED_EXISTING_ENTITY
	}

	public enum BindingEvidence {
		UNAVAILABLE,
		AUTHORED_PLACEMENT_IDENTITY,
		MISSING_AUTHORED_PLACEMENT_IDENTITY
	}

	public enum TargetConflictPolicy {
		UNAVAILABLE,
		REFUSE_MISMATCH_OR_AMBIGUITY
	}

	public enum ArrivalOrderingRequirement {
		UNAVAILABLE,
		RECONCILE_BEFORE_FIRST_VISIBILITY
	}

	public enum ExecutionSemantics {
		UNAVAILABLE,
		ONE_SHOT
	}

	public enum TimeProgressionPolicy {
		UNAVAILABLE,
		CONTINUE_SERVER_TICKS
	}

	public enum AuthoredConstructionKind {
		SCENERY,
		BOUNDARY,
		NPC_SPAWN,
		GROUND_ITEM_SPAWN,
		HARVESTING_SCENERY
	}

	/** One exact candidate source supplied in canonical proposal order. */
	public static final class PackedSource {
		private final int packedRegionX;
		private final int packedRegionY;

		private PackedSource(
			final int packedRegionX,
			final int packedRegionY) {
			if (packedRegionX < 0 || packedRegionY < 0) {
				throw new IllegalArgumentException(
					"Packed source coordinates must be non-negative");
			}
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
		}

		public static PackedSource of(
			final int packedRegionX,
			final int packedRegionY) {
			return new PackedSource(packedRegionX, packedRegionY);
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
	}

	/** One detached spatial fact supplied by an event-aware runtime seam. */
	public static final class SpatialReference {
		private final SpatialRole role;
		private final int x;
		private final int y;

		private SpatialReference(
			final SpatialRole role,
			final int x,
			final int y) {
			this.role = Objects.requireNonNull(role, "role");
			if (x < 0 || y < 0) {
				throw new IllegalArgumentException(
					"Event spatial coordinates must be non-negative");
			}
			this.x = x;
			this.y = y;
		}

		public static SpatialReference of(
			final SpatialRole role,
			final int x,
			final int y) {
			return new SpatialReference(role, x, y);
		}

		public SpatialRole getRole() { return role; }
		public int getX() { return x; }
		public int getY() { return y; }
	}

	/** Detached callback input, still incapable of replay or rescheduling. */
	public static final class EventRestorationState {
		private static final EventRestorationState UNAVAILABLE =
			new EventRestorationState(
				RestorationKind.UNAVAILABLE, null, false,
				TargetBindingEvidence.UNAVAILABLE, false,
				ExecutionSemantics.UNAVAILABLE,
				TimeProgressionPolicy.UNAVAILABLE,
				TargetSubject.UNAVAILABLE, BindingEvidence.UNAVAILABLE,
				TargetConflictPolicy.UNAVAILABLE,
				ArrivalOrderingRequirement.UNAVAILABLE);

		private final RestorationKind kind;
		private final SceneryRestorationState scenery;
		private final boolean forceFullBlock;
		private final TargetBindingEvidence targetBindingEvidence;
		private final boolean detachedCallbackPayloadComplete;
		private final ExecutionSemantics executionSemantics;
		private final TimeProgressionPolicy timeProgressionPolicy;
		private final TargetSubject targetSubject;
		private final BindingEvidence bindingEvidence;
		private final TargetConflictPolicy targetConflictPolicy;
		private final ArrivalOrderingRequirement arrivalOrderingRequirement;

		private EventRestorationState(
			final RestorationKind kind,
			final SceneryRestorationState scenery,
			final boolean forceFullBlock,
			final TargetBindingEvidence targetBindingEvidence,
			final boolean detachedCallbackPayloadComplete,
			final ExecutionSemantics executionSemantics,
			final TimeProgressionPolicy timeProgressionPolicy,
			final TargetSubject targetSubject,
			final BindingEvidence bindingEvidence,
			final TargetConflictPolicy targetConflictPolicy,
			final ArrivalOrderingRequirement arrivalOrderingRequirement) {
			this.kind = Objects.requireNonNull(kind, "kind");
			this.scenery = scenery;
			this.forceFullBlock = forceFullBlock;
			this.targetBindingEvidence = Objects.requireNonNull(
				targetBindingEvidence, "targetBindingEvidence");
			this.detachedCallbackPayloadComplete =
				detachedCallbackPayloadComplete;
			this.executionSemantics = Objects.requireNonNull(
				executionSemantics, "executionSemantics");
			this.timeProgressionPolicy = Objects.requireNonNull(
				timeProgressionPolicy, "timeProgressionPolicy");
			this.targetSubject = Objects.requireNonNull(
				targetSubject, "targetSubject");
			this.bindingEvidence = Objects.requireNonNull(
				bindingEvidence, "bindingEvidence");
			this.targetConflictPolicy = Objects.requireNonNull(
				targetConflictPolicy, "targetConflictPolicy");
			this.arrivalOrderingRequirement = Objects.requireNonNull(
				arrivalOrderingRequirement, "arrivalOrderingRequirement");
			if (kind == RestorationKind.UNAVAILABLE) {
				if (scenery != null || forceFullBlock
					|| targetBindingEvidence
						!= TargetBindingEvidence.UNAVAILABLE
					|| detachedCallbackPayloadComplete
					|| executionSemantics != ExecutionSemantics.UNAVAILABLE
					|| timeProgressionPolicy
						!= TimeProgressionPolicy.UNAVAILABLE
					|| targetSubject != TargetSubject.UNAVAILABLE
					|| bindingEvidence != BindingEvidence.UNAVAILABLE
					|| targetConflictPolicy
						!= TargetConflictPolicy.UNAVAILABLE
					|| arrivalOrderingRequirement
						!= ArrivalOrderingRequirement.UNAVAILABLE) {
					throw new IllegalArgumentException(
						"Unavailable event restoration state contains data");
				}
			} else {
				if (scenery == null) {
					throw new IllegalArgumentException(
						"Scenery event restoration state requires scenery data");
				}
				if (executionSemantics != ExecutionSemantics.ONE_SHOT
					|| timeProgressionPolicy
						!= TimeProgressionPolicy.CONTINUE_SERVER_TICKS) {
					throw new IllegalArgumentException(
						"Known scenery state requires one-shot timing semantics");
				}
				TargetSubject requiredSubject = kind
					== RestorationKind.SCENERY_SPAWN
						? TargetSubject.AUTHORED_DESTINATION_SLOT
						: TargetSubject.AUTHORED_EXISTING_ENTITY;
				boolean authored = scenery.getAuthoredPlacement() != null;
				BindingEvidence requiredEvidence = authored
					? BindingEvidence.AUTHORED_PLACEMENT_IDENTITY
					: BindingEvidence.MISSING_AUTHORED_PLACEMENT_IDENTITY;
				if (targetSubject != requiredSubject
					|| bindingEvidence != requiredEvidence
					|| targetConflictPolicy
						!= TargetConflictPolicy
							.REFUSE_MISMATCH_OR_AMBIGUITY
					|| arrivalOrderingRequirement
						!= ArrivalOrderingRequirement
							.RECONCILE_BEFORE_FIRST_VISIBILITY) {
					throw new IllegalArgumentException(
						"Scenery restoration requirement does not match its target");
				}
			}
		}

		public static EventRestorationState unavailable() {
			return UNAVAILABLE;
		}

		public static EventRestorationState scenerySpawn(
			final SceneryRestorationState scenery,
			final boolean forceFullBlock,
			final ExecutionSemantics executionSemantics,
			final TimeProgressionPolicy timeProgressionPolicy) {
			SceneryRestorationState checked = Objects.requireNonNull(
				scenery, "scenery");
			return scenerySpawn(
				checked, forceFullBlock, executionSemantics,
				timeProgressionPolicy,
				TargetSubject.AUTHORED_DESTINATION_SLOT,
				checked.getAuthoredPlacement() == null
					? BindingEvidence.MISSING_AUTHORED_PLACEMENT_IDENTITY
					: BindingEvidence.AUTHORED_PLACEMENT_IDENTITY,
				TargetConflictPolicy.REFUSE_MISMATCH_OR_AMBIGUITY,
				ArrivalOrderingRequirement
					.RECONCILE_BEFORE_FIRST_VISIBILITY);
		}

		public static EventRestorationState scenerySpawn(
			final SceneryRestorationState scenery,
			final boolean forceFullBlock,
			final ExecutionSemantics executionSemantics,
			final TimeProgressionPolicy timeProgressionPolicy,
			final TargetSubject targetSubject,
			final BindingEvidence bindingEvidence,
			final TargetConflictPolicy targetConflictPolicy,
			final ArrivalOrderingRequirement arrivalOrderingRequirement) {
			return new EventRestorationState(
				RestorationKind.SCENERY_SPAWN,
				Objects.requireNonNull(scenery, "scenery"), forceFullBlock,
				TargetBindingEvidence.NOT_REQUIRED, true,
				executionSemantics, timeProgressionPolicy,
				targetSubject, bindingEvidence, targetConflictPolicy,
				arrivalOrderingRequirement);
		}

		public static EventRestorationState sceneryRemove(
			final SceneryRestorationState scenery,
			final ExecutionSemantics executionSemantics,
			final TimeProgressionPolicy timeProgressionPolicy) {
			SceneryRestorationState checked = Objects.requireNonNull(
				scenery, "scenery");
			return sceneryRemove(
				checked, executionSemantics, timeProgressionPolicy,
				TargetSubject.AUTHORED_EXISTING_ENTITY,
				checked.getAuthoredPlacement() == null
					? BindingEvidence.MISSING_AUTHORED_PLACEMENT_IDENTITY
					: BindingEvidence.AUTHORED_PLACEMENT_IDENTITY,
				TargetConflictPolicy.REFUSE_MISMATCH_OR_AMBIGUITY,
				ArrivalOrderingRequirement
					.RECONCILE_BEFORE_FIRST_VISIBILITY);
		}

		public static EventRestorationState sceneryRemove(
			final SceneryRestorationState scenery,
			final ExecutionSemantics executionSemantics,
			final TimeProgressionPolicy timeProgressionPolicy,
			final TargetSubject targetSubject,
			final BindingEvidence bindingEvidence,
			final TargetConflictPolicy targetConflictPolicy,
			final ArrivalOrderingRequirement arrivalOrderingRequirement) {
			SceneryRestorationState checked = Objects.requireNonNull(
				scenery, "scenery");
			boolean authored = checked.getAuthoredPlacement() != null;
			return new EventRestorationState(
				RestorationKind.SCENERY_REMOVE, checked, false,
				authored
					? TargetBindingEvidence.AUTHORED_PLACEMENT_IDENTITY
					: TargetBindingEvidence.LIVE_ENTITY_REFERENCE_ONLY,
				authored, executionSemantics, timeProgressionPolicy,
				targetSubject, bindingEvidence, targetConflictPolicy,
				arrivalOrderingRequirement);
		}

		public RestorationKind getKind() { return kind; }
		public SceneryRestorationState getScenery() { return scenery; }
		public boolean isForceFullBlock() { return forceFullBlock; }
		public TargetBindingEvidence getTargetBindingEvidence() {
			return targetBindingEvidence;
		}
		public boolean isDetachedCallbackPayloadComplete() {
			return detachedCallbackPayloadComplete;
		}
		public ExecutionSemantics getExecutionSemantics() {
			return executionSemantics;
		}
		public TimeProgressionPolicy getTimeProgressionPolicy() {
			return timeProgressionPolicy;
		}
		public TargetSubject getTargetSubject() { return targetSubject; }
		public BindingEvidence getBindingEvidence() {
			return bindingEvidence;
		}
		public TargetConflictPolicy getTargetConflictPolicy() {
			return targetConflictPolicy;
		}
		public ArrivalOrderingRequirement getArrivalOrderingRequirement() {
			return arrivalOrderingRequirement;
		}
		public boolean isExecutionSemanticsCaptured() {
			return executionSemantics != ExecutionSemantics.UNAVAILABLE;
		}
		public boolean isTargetBindingRequirementCaptured() {
			return targetSubject != TargetSubject.UNAVAILABLE
				&& bindingEvidence != BindingEvidence.UNAVAILABLE
				&& targetConflictPolicy
					!= TargetConflictPolicy.UNAVAILABLE;
		}
		public boolean isTargetBindingComplete() {
			return bindingEvidence
				== BindingEvidence.AUTHORED_PLACEMENT_IDENTITY
				&& scenery != null
				&& scenery.getAuthoredPlacement() != null;
		}
		public boolean isArrivalOrderingCaptured() {
			return arrivalOrderingRequirement
				!= ArrivalOrderingRequirement.UNAVAILABLE;
		}
		public boolean isAtomicTimingCaptured() { return false; }
		public boolean isSchedulerIdentityCaptured() { return false; }
		public boolean isTargetBindingLookupPerformed() { return false; }
		public boolean isStandaloneRestorationComplete() { return false; }
	}

	/** Privacy-sensitive constructor input retained internally, not in JSON. */
	public static final class SceneryRestorationState {
		private final int objectId;
		private final int permanentObjectId;
		private final int x;
		private final int y;
		private final int direction;
		private final int type;
		private final String owner;
		private final int runtimeAttributeCount;
		private final AuthoredPlacementRestorationState authoredPlacement;

		private SceneryRestorationState(
			final int objectId,
			final int permanentObjectId,
			final int x,
			final int y,
			final int direction,
			final int type,
			final String owner,
			final int runtimeAttributeCount,
			final AuthoredPlacementRestorationState authoredPlacement) {
			if (objectId < 0 || permanentObjectId < 0 || x < 0 || y < 0
				|| direction < 0 || direction > 7
				|| (type != 0 && type != 1)
				|| runtimeAttributeCount < 0) {
				throw new IllegalArgumentException(
					"Scenery event restoration state is invalid");
			}
			this.objectId = objectId;
			this.permanentObjectId = permanentObjectId;
			this.x = x;
			this.y = y;
			this.direction = direction;
			this.type = type;
			this.owner = owner;
			this.runtimeAttributeCount = runtimeAttributeCount;
			this.authoredPlacement = authoredPlacement;
		}

		public static SceneryRestorationState of(
			final int objectId,
			final int permanentObjectId,
			final int x,
			final int y,
			final int direction,
			final int type,
			final String owner,
			final int runtimeAttributeCount,
			final AuthoredPlacementRestorationState authoredPlacement) {
			return new SceneryRestorationState(
				objectId, permanentObjectId, x, y, direction, type, owner,
				runtimeAttributeCount, authoredPlacement);
		}

		public int getObjectId() { return objectId; }
		public int getPermanentObjectId() { return permanentObjectId; }
		public int getX() { return x; }
		public int getY() { return y; }
		public int getDirection() { return direction; }
		public int getType() { return type; }
		public String getOwner() { return owner; }
		public boolean hasOwner() { return owner != null; }
		public int getRuntimeAttributeCount() {
			return runtimeAttributeCount;
		}
		public AuthoredPlacementRestorationState getAuthoredPlacement() {
			return authoredPlacement;
		}
	}

	public static final class AuthoredPlacementRestorationState {
		private final long generation;
		private final int packedRegionX;
		private final int packedRegionY;
		private final int sourceOrdinal;
		private final AuthoredConstructionKind constructionKind;

		private AuthoredPlacementRestorationState(
			final long generation,
			final int packedRegionX,
			final int packedRegionY,
			final int sourceOrdinal,
			final AuthoredConstructionKind constructionKind) {
			if (generation <= 0L || packedRegionX < 0 || packedRegionY < 0
				|| sourceOrdinal <= 0
				|| sourceOrdinal > 262144) {
				throw new IllegalArgumentException(
					"Authored event restoration state is invalid");
			}
			this.generation = generation;
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.sourceOrdinal = sourceOrdinal;
			this.constructionKind = Objects.requireNonNull(
				constructionKind, "constructionKind");
		}

		public static AuthoredPlacementRestorationState of(
			final long generation,
			final int packedRegionX,
			final int packedRegionY,
			final int sourceOrdinal,
			final AuthoredConstructionKind constructionKind) {
			return new AuthoredPlacementRestorationState(
				generation, packedRegionX, packedRegionY, sourceOrdinal,
				constructionKind);
		}

		public long getGeneration() { return generation; }
		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getSourceOrdinal() { return sourceOrdinal; }
		public AuthoredConstructionKind getConstructionKind() {
			return constructionKind;
		}
	}

	/** One event's detached scheduler and affinity facts. */
	public static final class EventState {
		private final int snapshotOrdinal;
		private final long registrationSequence;
		private final OwnerKind ownerKind;
		private final AttributionKind attributionKind;
		private final boolean running;
		private final long ticksBeforeRun;
		private final int timesRan;
		private final boolean atomicTimingCaptured;
		private final List<SpatialReference> spatialReferences;
		private final EventRestorationState restorationState;

		private EventState(
			final int snapshotOrdinal,
			final long registrationSequence,
			final OwnerKind ownerKind,
			final AttributionKind attributionKind,
			final boolean running,
			final long ticksBeforeRun,
			final int timesRan,
			final List<SpatialReference> spatialReferences,
			final EventRestorationState restorationState,
			final boolean atomicTimingCaptured) {
			if (snapshotOrdinal < 0 || registrationSequence <= 0L
				|| timesRan < 0) {
				throw new IllegalArgumentException(
					"Event scheduler state is invalid");
			}
			this.snapshotOrdinal = snapshotOrdinal;
			this.registrationSequence = registrationSequence;
			this.ownerKind = Objects.requireNonNull(ownerKind, "ownerKind");
			this.attributionKind = Objects.requireNonNull(
				attributionKind, "attributionKind");
			Objects.requireNonNull(spatialReferences, "spatialReferences");
			List<SpatialReference> copied =
				new ArrayList<SpatialReference>(spatialReferences.size());
			for (int index = 0; index < spatialReferences.size(); index++) {
				copied.add(Objects.requireNonNull(
					spatialReferences.get(index),
					"spatialReferences[" + index + "]"));
			}
			Collections.sort(copied, REFERENCE_ORDER);
			validateAttribution(ownerKind, attributionKind, copied);
			this.restorationState = Objects.requireNonNull(
				restorationState, "restorationState");
			validateRestoration(attributionKind, copied, restorationState);
			if (atomicTimingCaptured
				&& !restorationState.isExecutionSemanticsCaptured()) {
				throw new IllegalArgumentException(
					"Atomic timing requires explicit execution semantics");
			}
			this.running = running;
			this.ticksBeforeRun = ticksBeforeRun;
			this.timesRan = timesRan;
			this.atomicTimingCaptured = atomicTimingCaptured;
			this.spatialReferences = Collections.unmodifiableList(copied);
		}

		private static void validateRestoration(
			final AttributionKind attributionKind,
			final List<SpatialReference> references,
			final EventRestorationState restorationState) {
			if (restorationState.getKind() == RestorationKind.UNAVAILABLE) {
				return;
			}
			if (attributionKind != AttributionKind.EXACT_SPATIAL) {
				throw new IllegalArgumentException(
					"Restoration state requires exact spatial attribution");
			}
			SceneryRestorationState scenery = restorationState.getScenery();
			for (SpatialReference reference : references) {
				if (reference.getRole() == SpatialRole.FIXED_EFFECT_LOCATION
					&& reference.getX() == scenery.getX()
					&& reference.getY() == scenery.getY()) {
					return;
				}
			}
			throw new IllegalArgumentException(
				"Scenery restoration coordinate does not match fixed affinity");
		}

		private static void validateAttribution(
			final OwnerKind ownerKind,
			final AttributionKind attributionKind,
			final List<SpatialReference> references) {
			if (attributionKind == AttributionKind.EXACT_SPATIAL
				&& references.isEmpty()) {
				throw new IllegalArgumentException(
					"Exact spatial events require an explicit location");
			}
			if (attributionKind == AttributionKind.OWNER_POSITION_HINT) {
				if (ownerKind == OwnerKind.NONE || references.size() != 1
					|| references.get(0).getRole()
						!= SpatialRole.OWNER_CURRENT_POSITION) {
					throw new IllegalArgumentException(
						"Owner hints require one Mob owner position");
				}
			}
			if ((attributionKind == AttributionKind.NON_SPATIAL_GLOBAL
					|| attributionKind == AttributionKind.UNATTRIBUTED)
				&& !references.isEmpty()) {
				throw new IllegalArgumentException(
					"Non-spatial or unattributed events cannot imply a location");
			}
		}

		public static EventState of(
			final int snapshotOrdinal,
			final long registrationSequence,
			final OwnerKind ownerKind,
			final AttributionKind attributionKind,
			final boolean running,
			final long ticksBeforeRun,
			final int timesRan,
			final List<SpatialReference> spatialReferences) {
			return new EventState(
				snapshotOrdinal, registrationSequence, ownerKind, attributionKind,
				running,
				ticksBeforeRun, timesRan, spatialReferences,
				EventRestorationState.unavailable(), false);
		}

		public static EventState of(
			final int snapshotOrdinal,
			final long registrationSequence,
			final OwnerKind ownerKind,
			final AttributionKind attributionKind,
			final boolean running,
			final long ticksBeforeRun,
			final int timesRan,
			final List<SpatialReference> spatialReferences,
			final EventRestorationState restorationState) {
			return new EventState(
				snapshotOrdinal, registrationSequence, ownerKind, attributionKind,
				running,
				ticksBeforeRun, timesRan, spatialReferences,
				restorationState, false);
		}

		public static EventState of(
			final int snapshotOrdinal,
			final long registrationSequence,
			final OwnerKind ownerKind,
			final AttributionKind attributionKind,
			final boolean running,
			final long ticksBeforeRun,
			final int timesRan,
			final List<SpatialReference> spatialReferences,
			final EventRestorationState restorationState,
			final boolean atomicTimingCaptured) {
			return new EventState(
				snapshotOrdinal, registrationSequence, ownerKind, attributionKind,
				running, ticksBeforeRun, timesRan, spatialReferences,
				restorationState, atomicTimingCaptured);
		}

		public int getSnapshotOrdinal() { return snapshotOrdinal; }
		public long getRegistrationSequence() { return registrationSequence; }
		public OwnerKind getOwnerKind() { return ownerKind; }
		public AttributionKind getAttributionKind() {
			return attributionKind;
		}
		public boolean isRunning() { return running; }
		public long getTicksBeforeRun() { return ticksBeforeRun; }
		public int getTimesRan() { return timesRan; }
		public boolean isAtomicTimingCaptured() {
			return atomicTimingCaptured;
		}
		public List<SpatialReference> getSpatialReferences() {
			return spatialReferences;
		}
		public EventRestorationState getRestorationState() {
			return restorationState;
		}
	}

	/** Stable record for one detached event snapshot entry. */
	public static final class EventRecord {
		private final EventState state;
		private final List<Integer> candidateSourceOrdinals;

		private EventRecord(
			final EventState state,
			final Set<Integer> candidateSourceOrdinals) {
			this.state = state;
			this.candidateSourceOrdinals = Collections.unmodifiableList(
				new ArrayList<Integer>(candidateSourceOrdinals));
		}

		public int getSnapshotOrdinal() { return state.getSnapshotOrdinal(); }
		public long getRegistrationSequence() {
			return state.getRegistrationSequence();
		}
		public OwnerKind getOwnerKind() { return state.getOwnerKind(); }
		public AttributionKind getAttributionKind() {
			return state.getAttributionKind();
		}
		public boolean isRunning() { return state.isRunning(); }
		public long getTicksBeforeRun() { return state.getTicksBeforeRun(); }
		public int getTimesRan() { return state.getTimesRan(); }
		public boolean isAtomicTimingCaptured() {
			return state.isAtomicTimingCaptured();
		}
		public List<SpatialReference> getSpatialReferences() {
			return state.getSpatialReferences();
		}
		public EventRestorationState getRestorationState() {
			return state.getRestorationState();
		}
		public List<Integer> getCandidateSourceOrdinals() {
			return candidateSourceOrdinals;
		}
		public boolean isCandidateRelated() {
			return !candidateSourceOrdinals.isEmpty();
		}
		public boolean isAttributionComplete() {
			return state.getAttributionKind() == AttributionKind.EXACT_SPATIAL
				|| state.getAttributionKind()
					== AttributionKind.NON_SPATIAL_GLOBAL;
		}
		public boolean isRestorationStateComplete() { return false; }
	}

	/** Proposal-ordered view of events associated with one candidate source. */
	public static final class SourceRecord {
		private final PackedSource source;
		private final List<Integer> exactSpatialEventOrdinals;
		private final List<Integer> ownerPositionHintEventOrdinals;
		private final List<Integer> restorationStateEventOrdinals;
		private final int unattributedEventCount;

		private SourceRecord(
			final PackedSource source,
			final List<Integer> exactSpatialEventOrdinals,
			final List<Integer> ownerPositionHintEventOrdinals,
			final List<Integer> restorationStateEventOrdinals,
			final int unattributedEventCount) {
			this.source = source;
			this.exactSpatialEventOrdinals = Collections.unmodifiableList(
				new ArrayList<Integer>(exactSpatialEventOrdinals));
			this.ownerPositionHintEventOrdinals = Collections.unmodifiableList(
				new ArrayList<Integer>(ownerPositionHintEventOrdinals));
			this.restorationStateEventOrdinals = Collections.unmodifiableList(
				new ArrayList<Integer>(restorationStateEventOrdinals));
			this.unattributedEventCount = unattributedEventCount;
		}

		public int getPackedRegionX() { return source.getPackedRegionX(); }
		public int getPackedRegionY() { return source.getPackedRegionY(); }
		public List<Integer> getExactSpatialEventOrdinals() {
			return exactSpatialEventOrdinals;
		}
		public int getExactSpatialEventCount() {
			return exactSpatialEventOrdinals.size();
		}
		public List<Integer> getOwnerPositionHintEventOrdinals() {
			return ownerPositionHintEventOrdinals;
		}
		public int getOwnerPositionHintEventCount() {
			return ownerPositionHintEventOrdinals.size();
		}
		public List<Integer> getRestorationStateEventOrdinals() {
			return restorationStateEventOrdinals;
		}
		public int getRestorationStateEventCount() {
			return restorationStateEventOrdinals.size();
		}
		public int getUnattributedEventCount() {
			return unattributedEventCount;
		}
		public boolean isAttributionComplete() {
			return ownerPositionHintEventOrdinals.isEmpty()
				&& unattributedEventCount == 0;
		}
	}
}
