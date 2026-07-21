package com.openrsc.server.model.world.coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, dormant constructor-state record for identity-less scenery in one
 * exact packed-region refinement selection.
 *
 * <p>The record is deliberately narrower than a restoration snapshot. It
 * detaches every value accepted by the current {@code GameObject}
 * constructors, plus the count of opaque runtime attributes that were not
 * copied. Event ownership is external to a Region and remains unobserved.
 * Consequently this value can prove deterministic constructor-state capture,
 * but it cannot prove that recreating an object would preserve gameplay state
 * or scheduled behavior.</p>
 *
 * <p>No entity, Region, collection, attribute, event, or registry handle is
 * retained. Recording never unregisters, removes, creates, reloads, or mutates
 * an object and grants no retirement or lifecycle authority.</p>
 */
public final class LayeredPackedRegionDynamicObjectPreservationRecord {
	public static final int MAXIMUM_DYNAMIC_OBJECTS = 65536;

	private final long proposalGeneration;
	private final long observedAtTick;
	private final List<SourceRecord> sources;
	private final int dynamicObjectCount;
	private final int objectsWithRuntimeAttributesCount;

	private LayeredPackedRegionDynamicObjectPreservationRecord(
		final long proposalGeneration,
		final long observedAtTick,
		final List<SourceRecord> sources,
		final int dynamicObjectCount,
		final int objectsWithRuntimeAttributesCount) {
		this.proposalGeneration = proposalGeneration;
		this.observedAtTick = observedAtTick;
		this.sources = Collections.unmodifiableList(sources);
		this.dynamicObjectCount = dynamicObjectCount;
		this.objectsWithRuntimeAttributesCount =
			objectsWithRuntimeAttributesCount;
	}

	/**
	 * Copies a bounded set of already-detached Region observations. Source order
	 * must be the canonical proposal order; object order is normalized without
	 * dropping duplicates.
	 */
	public static LayeredPackedRegionDynamicObjectPreservationRecord record(
		final long proposalGeneration,
		final long observedAtTick,
		final List<PackedSourceCapture> captures,
		final int maximumPackedSources,
		final int maximumDynamicObjects) {
		Objects.requireNonNull(captures, "captures");
		if (proposalGeneration < 0L || observedAtTick < 0L
			|| maximumPackedSources < 0
			|| maximumPackedSources
				> LayeredPackedRegionRetirementRefinementProposal
					.MAXIMUM_CANDIDATE_SOURCES
			|| captures.size() > maximumPackedSources
			|| maximumDynamicObjects < 0
			|| maximumDynamicObjects > MAXIMUM_DYNAMIC_OBJECTS) {
			throw new IllegalArgumentException(
				"Dynamic-object preservation record exceeds its budget");
		}

		List<SourceRecord> sources =
			new ArrayList<SourceRecord>(captures.size());
		int totalObjects = 0;
		int objectsWithAttributes = 0;
		PackedSourceCapture previous = null;
		for (int sourceIndex = 0; sourceIndex < captures.size(); sourceIndex++) {
			PackedSourceCapture capture = Objects.requireNonNull(
				captures.get(sourceIndex), "captures[" + sourceIndex + "]");
			if (previous != null && SOURCE_ORDER.compare(previous, capture) >= 0) {
				throw new IllegalArgumentException(
					"Packed sources must be unique and canonically ordered");
			}
			previous = capture;
			totalObjects = Math.addExact(
				totalObjects, capture.getDynamicObjects().size());
			if (totalObjects > maximumDynamicObjects) {
				throw new IllegalArgumentException(
					"Dynamic-object preservation record exceeds its object budget");
			}
			List<DynamicObjectRecord> objects =
				new ArrayList<DynamicObjectRecord>(
					capture.getDynamicObjects().size());
			List<DynamicObjectState> ordered =
				new ArrayList<DynamicObjectState>(capture.getDynamicObjects());
			Collections.sort(ordered, DynamicObjectState.ORDER);
			for (int objectIndex = 0; objectIndex < ordered.size(); objectIndex++) {
				DynamicObjectState state = ordered.get(objectIndex);
				objects.add(new DynamicObjectRecord(objectIndex, state));
				objectsWithAttributes += state.getRuntimeAttributeCount() > 0
					? 1 : 0;
			}
			sources.add(new SourceRecord(capture, objects));
		}
		return new LayeredPackedRegionDynamicObjectPreservationRecord(
			proposalGeneration, observedAtTick, sources, totalObjects,
			objectsWithAttributes);
	}

	private static final Comparator<PackedSourceCapture> SOURCE_ORDER =
		new Comparator<PackedSourceCapture>() {
			@Override
			public int compare(
				final PackedSourceCapture left,
				final PackedSourceCapture right) {
				int byY = Integer.compare(
					left.getPackedRegionY(), right.getPackedRegionY());
				return byY != 0 ? byY : Integer.compare(
					left.getPackedRegionX(), right.getPackedRegionX());
			}
		};

	public long getProposalGeneration() { return proposalGeneration; }
	public long getObservedAtTick() { return observedAtTick; }
	public List<SourceRecord> getSources() { return sources; }
	public int getSourceCount() { return sources.size(); }
	public int getDynamicObjectCount() { return dynamicObjectCount; }
	public int getObjectsWithRuntimeAttributesCount() {
		return objectsWithRuntimeAttributesCount;
	}
	public int getConstructorStateCompleteObjectCount() {
		return dynamicObjectCount;
	}
	public int getStandaloneRestorationCompleteObjectCount() { return 0; }

	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedPrimitiveCopy() { return true; }
	public boolean isRuntimeAttributesCaptured() { return false; }
	public boolean isEventOwnershipCaptured() { return false; }
	public boolean isPreservationPerformed() { return false; }
	public boolean isReloadRequest() { return false; }
	public boolean isEntityRegistry() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isTeardownTransaction() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	/** One Region-local detached input supplied to {@link #record}. */
	public static final class PackedSourceCapture {
		private final int packedRegionX;
		private final int packedRegionY;
		private final boolean regionPresent;
		private final List<DynamicObjectState> dynamicObjects;

		private PackedSourceCapture(
			final int packedRegionX,
			final int packedRegionY,
			final boolean regionPresent,
			final List<DynamicObjectState> dynamicObjects) {
			if (packedRegionX < 0 || packedRegionY < 0) {
				throw new IllegalArgumentException(
					"Packed source coordinates must be non-negative");
			}
			Objects.requireNonNull(dynamicObjects, "dynamicObjects");
			if (!regionPresent && !dynamicObjects.isEmpty()) {
				throw new IllegalArgumentException(
					"An absent Region cannot contain dynamic objects");
			}
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.regionPresent = regionPresent;
			List<DynamicObjectState> copied =
				new ArrayList<DynamicObjectState>(dynamicObjects.size());
			for (int index = 0; index < dynamicObjects.size(); index++) {
				copied.add(Objects.requireNonNull(
					dynamicObjects.get(index),
					"dynamicObjects[" + index + "]"));
			}
			this.dynamicObjects = Collections.unmodifiableList(copied);
		}

		public static PackedSourceCapture of(
			final int packedRegionX,
			final int packedRegionY,
			final boolean regionPresent,
			final List<DynamicObjectState> dynamicObjects) {
			return new PackedSourceCapture(
				packedRegionX, packedRegionY, regionPresent, dynamicObjects);
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public boolean isRegionPresent() { return regionPresent; }
		public List<DynamicObjectState> getDynamicObjects() {
			return dynamicObjects;
		}
	}

	/** Constructor-state input detached from a live {@code GameObject}. */
	public static final class DynamicObjectState {
		private static final Comparator<DynamicObjectState> ORDER =
			new Comparator<DynamicObjectState>() {
				@Override
				public int compare(
					final DynamicObjectState left,
					final DynamicObjectState right) {
					int compared = Integer.compare(left.y, right.y);
					if (compared != 0) { return compared; }
					compared = Integer.compare(left.x, right.x);
					if (compared != 0) { return compared; }
					compared = Integer.compare(left.type, right.type);
					if (compared != 0) { return compared; }
					compared = Integer.compare(left.direction, right.direction);
					if (compared != 0) { return compared; }
					compared = Integer.compare(left.objectId, right.objectId);
					if (compared != 0) { return compared; }
					compared = Integer.compare(
						left.permanentObjectId, right.permanentObjectId);
					if (compared != 0) { return compared; }
					if (left.owner == null) { return right.owner == null ? 0 : -1; }
					if (right.owner == null) { return 1; }
					compared = left.owner.compareTo(right.owner);
					return compared != 0 ? compared : Integer.compare(
						left.runtimeAttributeCount,
						right.runtimeAttributeCount);
				}
			};

		private final int objectId;
		private final int permanentObjectId;
		private final int x;
		private final int y;
		private final int direction;
		private final int type;
		private final String owner;
		private final int runtimeAttributeCount;

		private DynamicObjectState(
			final int objectId,
			final int permanentObjectId,
			final int x,
			final int y,
			final int direction,
			final int type,
			final String owner,
			final int runtimeAttributeCount) {
			if (objectId < 0 || permanentObjectId < 0 || x < 0 || y < 0
				|| direction < 0 || direction > 7
				|| (type != 0 && type != 1)
				|| runtimeAttributeCount < 0) {
				throw new IllegalArgumentException(
					"Dynamic-object constructor state is invalid");
			}
			this.objectId = objectId;
			this.permanentObjectId = permanentObjectId;
			this.x = x;
			this.y = y;
			this.direction = direction;
			this.type = type;
			this.owner = owner;
			this.runtimeAttributeCount = runtimeAttributeCount;
		}

		public static DynamicObjectState of(
			final int objectId,
			final int permanentObjectId,
			final int x,
			final int y,
			final int direction,
			final int type,
			final String owner,
			final int runtimeAttributeCount) {
			return new DynamicObjectState(
				objectId, permanentObjectId, x, y, direction, type, owner,
				runtimeAttributeCount);
		}

		public int getObjectId() { return objectId; }
		public int getPermanentObjectId() { return permanentObjectId; }
		public int getX() { return x; }
		public int getY() { return y; }
		public int getDirection() { return direction; }
		public int getType() { return type; }
		public String getOwner() { return owner; }
		public boolean hasOwner() { return owner != null; }
		public int getRuntimeAttributeCount() { return runtimeAttributeCount; }
	}

	/** Stable, source-local ordered record produced from one state value. */
	public static final class DynamicObjectRecord {
		private final int sourceOrdinal;
		private final DynamicObjectState state;

		private DynamicObjectRecord(
			final int sourceOrdinal,
			final DynamicObjectState state) {
			this.sourceOrdinal = sourceOrdinal;
			this.state = state;
		}

		public int getSourceOrdinal() { return sourceOrdinal; }
		public int getObjectId() { return state.getObjectId(); }
		public int getPermanentObjectId() {
			return state.getPermanentObjectId();
		}
		public int getX() { return state.getX(); }
		public int getY() { return state.getY(); }
		public int getDirection() { return state.getDirection(); }
		public int getType() { return state.getType(); }
		public String getOwner() { return state.getOwner(); }
		public boolean hasOwner() { return state.hasOwner(); }
		public int getRuntimeAttributeCount() {
			return state.getRuntimeAttributeCount();
		}
		public boolean isConstructorStateComplete() { return true; }
		public boolean isStandaloneRestorationComplete() { return false; }
	}

	/** One proposal-ordered packed source and its deterministic object records. */
	public static final class SourceRecord {
		private final int packedRegionX;
		private final int packedRegionY;
		private final boolean regionPresent;
		private final List<DynamicObjectRecord> dynamicObjects;

		private SourceRecord(
			final PackedSourceCapture capture,
			final List<DynamicObjectRecord> dynamicObjects) {
			this.packedRegionX = capture.getPackedRegionX();
			this.packedRegionY = capture.getPackedRegionY();
			this.regionPresent = capture.isRegionPresent();
			this.dynamicObjects = Collections.unmodifiableList(dynamicObjects);
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public boolean isRegionPresent() { return regionPresent; }
		public List<DynamicObjectRecord> getDynamicObjects() {
			return dynamicObjects;
		}
		public int getDynamicObjectCount() { return dynamicObjects.size(); }
	}
}
