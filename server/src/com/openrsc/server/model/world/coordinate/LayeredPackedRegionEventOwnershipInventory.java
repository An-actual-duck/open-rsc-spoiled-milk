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
	private final List<SourceRecord> sources;
	private final List<EventRecord> events;
	private final int spatialReferenceCount;
	private final int exactSpatialEventCount;
	private final int ownerPositionHintEventCount;
	private final int nonSpatialGlobalEventCount;
	private final int unattributedEventCount;
	private final int candidateRelatedEventCount;

	private LayeredPackedRegionEventOwnershipInventory(
		final long proposalGeneration,
		final long observedAtTick,
		final List<SourceRecord> sources,
		final List<EventRecord> events,
		final int spatialReferenceCount,
		final int exactSpatialEventCount,
		final int ownerPositionHintEventCount,
		final int nonSpatialGlobalEventCount,
		final int unattributedEventCount,
		final int candidateRelatedEventCount) {
		this.proposalGeneration = proposalGeneration;
		this.observedAtTick = observedAtTick;
		this.sources = Collections.unmodifiableList(sources);
		this.events = Collections.unmodifiableList(events);
		this.spatialReferenceCount = spatialReferenceCount;
		this.exactSpatialEventCount = exactSpatialEventCount;
		this.ownerPositionHintEventCount = ownerPositionHintEventCount;
		this.nonSpatialGlobalEventCount = nonSpatialGlobalEventCount;
		this.unattributedEventCount = unattributedEventCount;
		this.candidateRelatedEventCount = candidateRelatedEventCount;
	}

	/**
	 * Correlates one bounded detached event snapshot with one exact canonical
	 * candidate order. Event ordinals must be contiguous snapshot order.
	 */
	public static LayeredPackedRegionEventOwnershipInventory inventory(
		final long proposalGeneration,
		final long observedAtTick,
		final List<PackedSource> packedSources,
		final List<EventState> eventStates,
		final int maximumPackedSources,
		final int maximumEvents,
		final int maximumSpatialReferences) {
		Objects.requireNonNull(packedSources, "packedSources");
		Objects.requireNonNull(eventStates, "eventStates");
		if (proposalGeneration < 0L || observedAtTick < 0L
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
		List<EventRecord> eventRecords =
			new ArrayList<EventRecord>(eventStates.size());
		int referenceCount = 0;
		int exactCount = 0;
		int hintCount = 0;
		int globalCount = 0;
		int unknownCount = 0;
		int candidateEventCount = 0;
		for (int index = 0; index < eventStates.size(); index++) {
			EventState state = Objects.requireNonNull(
				eventStates.get(index), "eventStates[" + index + "]");
			if (state.getSnapshotOrdinal() != index) {
				throw new IllegalArgumentException(
					"Event ordinals must preserve contiguous snapshot order");
			}
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
			}
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
				hintsBySource.get(index), unknownCount));
		}
		return new LayeredPackedRegionEventOwnershipInventory(
			proposalGeneration, observedAtTick, sourceRecords, eventRecords,
			referenceCount, exactCount, hintCount, globalCount, unknownCount,
			candidateEventCount);
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
	public boolean isCandidateAttributionComplete() {
		return ownerPositionHintEventCount == 0 && unattributedEventCount == 0;
	}
	public int getRestorationStateCompleteEventCount() { return 0; }

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

	/** One event's detached scheduler and affinity facts. */
	public static final class EventState {
		private final int snapshotOrdinal;
		private final OwnerKind ownerKind;
		private final AttributionKind attributionKind;
		private final boolean running;
		private final long ticksBeforeRun;
		private final int timesRan;
		private final List<SpatialReference> spatialReferences;

		private EventState(
			final int snapshotOrdinal,
			final OwnerKind ownerKind,
			final AttributionKind attributionKind,
			final boolean running,
			final long ticksBeforeRun,
			final int timesRan,
			final List<SpatialReference> spatialReferences) {
			if (snapshotOrdinal < 0 || timesRan < 0) {
				throw new IllegalArgumentException(
					"Event scheduler state is invalid");
			}
			this.snapshotOrdinal = snapshotOrdinal;
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
			this.running = running;
			this.ticksBeforeRun = ticksBeforeRun;
			this.timesRan = timesRan;
			this.spatialReferences = Collections.unmodifiableList(copied);
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
			final OwnerKind ownerKind,
			final AttributionKind attributionKind,
			final boolean running,
			final long ticksBeforeRun,
			final int timesRan,
			final List<SpatialReference> spatialReferences) {
			return new EventState(
				snapshotOrdinal, ownerKind, attributionKind, running,
				ticksBeforeRun, timesRan, spatialReferences);
		}

		public int getSnapshotOrdinal() { return snapshotOrdinal; }
		public OwnerKind getOwnerKind() { return ownerKind; }
		public AttributionKind getAttributionKind() {
			return attributionKind;
		}
		public boolean isRunning() { return running; }
		public long getTicksBeforeRun() { return ticksBeforeRun; }
		public int getTimesRan() { return timesRan; }
		public List<SpatialReference> getSpatialReferences() {
			return spatialReferences;
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
		public OwnerKind getOwnerKind() { return state.getOwnerKind(); }
		public AttributionKind getAttributionKind() {
			return state.getAttributionKind();
		}
		public boolean isRunning() { return state.isRunning(); }
		public long getTicksBeforeRun() { return state.getTicksBeforeRun(); }
		public int getTimesRan() { return state.getTimesRan(); }
		public List<SpatialReference> getSpatialReferences() {
			return state.getSpatialReferences();
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
		private final int unattributedEventCount;

		private SourceRecord(
			final PackedSource source,
			final List<Integer> exactSpatialEventOrdinals,
			final List<Integer> ownerPositionHintEventOrdinals,
			final int unattributedEventCount) {
			this.source = source;
			this.exactSpatialEventOrdinals = Collections.unmodifiableList(
				new ArrayList<Integer>(exactSpatialEventOrdinals));
			this.ownerPositionHintEventOrdinals = Collections.unmodifiableList(
				new ArrayList<Integer>(ownerPositionHintEventOrdinals));
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
		public int getUnattributedEventCount() {
			return unattributedEventCount;
		}
		public boolean isAttributionComplete() {
			return ownerPositionHintEventOrdinals.isEmpty()
				&& unattributedEventCount == 0;
		}
	}
}
