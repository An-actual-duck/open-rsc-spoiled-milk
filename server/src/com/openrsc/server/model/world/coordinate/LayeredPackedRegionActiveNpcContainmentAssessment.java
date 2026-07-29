package com.openrsc.server.model.world.coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assesses point-in-time active NPC containment around one exact source set.
 *
 * <p>Containment means only that the detached census has no observed NPC
 * ownership/residency relationship crossing the selected boundary and no
 * unresolved relevant evidence. Active NPCs inside still require a separately
 * designed preservation and reload path. Containment is never lifecycle readiness.</p>
 *
 * <p>This immutable value has no entity, Region, registry, arrival, movement,
 * retention, loading, release, teardown, reconstruction, rollback, or
 * lifecycle authority.</p>
 */
public final class LayeredPackedRegionActiveNpcContainmentAssessment {
	private final long generation;
	private final long safetyObservedAtTick;
	private final long censusObservedAtTick;
	private final int selectedSourceCount;
	private final int activeInstanceCount;
	private final int relevantActiveInstanceCount;
	private final int selectedOwnerInsideCount;
	private final int sameSourceSelectedOwnerInsideCount;
	private final int crossSourceSelectedOwnerInsideCount;
	private final int currentInsideCount;
	private final int activePreservationRequiredInstanceCount;
	private final int relevantDuplicateIdentityInstanceCount;
	private final int blockingConditionCount;
	private final int blockingEvidenceCount;
	private final boolean boundaryContained;
	private final List<BlockerCount> blockers;

	private LayeredPackedRegionActiveNpcContainmentAssessment(
		final LayeredPackedRegionActiveNpcResidencyObservation observation,
		final int sameSourceSelectedOwnerInsideCount,
		final int crossSourceSelectedOwnerInsideCount,
		final int relevantDuplicateIdentityInstanceCount,
		final List<BlockerCount> blockers) {
		this.generation = observation.getGeneration();
		this.safetyObservedAtTick = observation.getSafetyObservedAtTick();
		this.censusObservedAtTick = observation.getCensusObservedAtTick();
		this.selectedSourceCount = observation.getSelectedSourceCount();
		this.activeInstanceCount = observation.getActiveInstanceCount();
		this.relevantActiveInstanceCount =
			observation.getRelevantActiveInstanceCount();
		this.selectedOwnerInsideCount = observation.getSelectedOwnerInsideCount();
		this.sameSourceSelectedOwnerInsideCount =
			sameSourceSelectedOwnerInsideCount;
		this.crossSourceSelectedOwnerInsideCount =
			crossSourceSelectedOwnerInsideCount;
		this.currentInsideCount = Math.addExact(
			Math.addExact(
				observation.getSelectedOwnerInsideCount(),
				observation.getExternalOwnerInsideCount()),
			observation.getUnresolvedInsideCount());
		this.activePreservationRequiredInstanceCount =
			observation.getRelevantActiveInstanceCount();
		this.relevantDuplicateIdentityInstanceCount =
			relevantDuplicateIdentityInstanceCount;
		this.blockers = Collections.unmodifiableList(
			new ArrayList<BlockerCount>(blockers));
		int conditionCount = 0;
		int evidenceCount = 0;
		for (BlockerCount blocker : blockers) {
			if (blocker.getInstanceCount() > 0) {
				conditionCount = Math.incrementExact(conditionCount);
			}
			evidenceCount = Math.addExact(
				evidenceCount, blocker.getInstanceCount());
		}
		this.blockingConditionCount = conditionCount;
		this.blockingEvidenceCount = evidenceCount;
		this.boundaryContained = conditionCount == 0;

		if (selectedOwnerInsideCount
				!= sameSourceSelectedOwnerInsideCount
					+ crossSourceSelectedOwnerInsideCount
			|| blockers.size() != BlockerKind.values().length) {
			throw new IllegalArgumentException(
				"Active NPC containment arithmetic is inconsistent");
		}
	}

	/** Builds detached containment evidence from one complete bounded census. */
	public static LayeredPackedRegionActiveNpcContainmentAssessment assess(
		final LayeredPackedRegionActiveNpcResidencyObservation observation) {
		if (observation == null) {
			throw new NullPointerException("observation");
		}
		int sameSourceInside = 0;
		int crossSourceInside = 0;
		Map<LayeredAuthoredPlacementIdentity, Integer> relevantIdentityCounts =
			new LinkedHashMap<LayeredAuthoredPlacementIdentity, Integer>();
		for (LayeredPackedRegionActiveNpcResidencyObservation.InstanceEvidence
			evidence : observation.getRelevantActiveInstances()) {
			if (evidence.hasAuthoredIdentity()
				&& evidence.getIdentityStatus()
					== LayeredPackedRegionActiveNpcResidencyObservation
						.IdentityStatus.RECOGNIZED) {
				LayeredAuthoredPlacementIdentity identity = evidence.getIdentity();
				Integer previous = relevantIdentityCounts.get(identity);
				relevantIdentityCounts.put(identity, Integer.valueOf(
					previous == null ? 1
						: Math.incrementExact(previous.intValue())));
			}
			if (evidence.getClassification()
				!= LayeredPackedRegionActiveNpcResidencyObservation
					.ActiveResidencyClassification.SELECTED_OWNER_INSIDE) {
				continue;
			}
			if (!evidence.hasAuthoredIdentity()
				|| evidence.getIdentityStatus()
					!= LayeredPackedRegionActiveNpcResidencyObservation
						.IdentityStatus.RECOGNIZED) {
				throw new IllegalArgumentException(
					"Selected-owner-inside evidence lacks recognized identity");
			}
			if (evidence.getIdentityPackedRegionX()
					== evidence.getCurrentPackedRegionX()
				&& evidence.getIdentityPackedRegionY()
					== evidence.getCurrentPackedRegionY()) {
				sameSourceInside = Math.incrementExact(sameSourceInside);
			} else {
				crossSourceInside = Math.incrementExact(crossSourceInside);
			}
		}
		int relevantDuplicates = 0;
		for (Integer count : relevantIdentityCounts.values()) {
			if (count.intValue() > 1) {
				relevantDuplicates = Math.addExact(
					relevantDuplicates, count.intValue() - 1);
			}
		}

		List<BlockerCount> blockers = new ArrayList<BlockerCount>(
			BlockerKind.values().length);
		blockers.add(new BlockerCount(
			BlockerKind.SELECTED_OWNER_OUTSIDE,
			observation.getSelectedOwnerOutsideCount()));
		blockers.add(new BlockerCount(
			BlockerKind.EXTERNAL_OWNER_INSIDE,
			observation.getExternalOwnerInsideCount()));
		blockers.add(new BlockerCount(
			BlockerKind.UNRESOLVED_INSIDE,
			observation.getUnresolvedInsideCount()));
		blockers.add(new BlockerCount(
			BlockerKind.UNRESOLVED_CLAIMED_SELECTED_OWNER_OUTSIDE,
			observation.getUnresolvedClaimedSelectedOwnerOutsideCount()));
		blockers.add(new BlockerCount(
			BlockerKind.RELEVANT_INACTIVE,
			observation.getInactiveRelevantInstanceCount()));
		blockers.add(new BlockerCount(
			BlockerKind.RELEVANT_DUPLICATE_IDENTITY, relevantDuplicates));
		return new LayeredPackedRegionActiveNpcContainmentAssessment(
			observation, sameSourceInside, crossSourceInside,
			relevantDuplicates, blockers);
	}

	public long getGeneration() { return generation; }
	public long getSafetyObservedAtTick() { return safetyObservedAtTick; }
	public long getCensusObservedAtTick() { return censusObservedAtTick; }
	public int getSelectedSourceCount() { return selectedSourceCount; }
	public int getActiveInstanceCount() { return activeInstanceCount; }
	public int getRelevantActiveInstanceCount() {
		return relevantActiveInstanceCount;
	}
	public int getSelectedOwnerInsideCount() {
		return selectedOwnerInsideCount;
	}
	public int getSameSourceSelectedOwnerInsideCount() {
		return sameSourceSelectedOwnerInsideCount;
	}
	public int getCrossSourceSelectedOwnerInsideCount() {
		return crossSourceSelectedOwnerInsideCount;
	}
	public int getCurrentInsideCount() { return currentInsideCount; }
	public int getActivePreservationRequiredInstanceCount() {
		return activePreservationRequiredInstanceCount;
	}
	public int getRelevantDuplicateIdentityInstanceCount() {
		return relevantDuplicateIdentityInstanceCount;
	}
	public int getBlockingConditionCount() { return blockingConditionCount; }
	public int getBlockingEvidenceCount() { return blockingEvidenceCount; }
	public boolean isBoundaryContained() { return boundaryContained; }
	public List<BlockerCount> getBlockers() { return blockers; }
	public boolean isPointInTimeOnly() { return true; }
	public boolean isContainmentEvidence() { return true; }
	public boolean isEntityPreservationRequired() {
		return activePreservationRequiredInstanceCount > 0;
	}
	public boolean isLifecycleReady() { return false; }
	public boolean isEntityRegistry() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	public enum BlockerKind {
		SELECTED_OWNER_OUTSIDE,
		EXTERNAL_OWNER_INSIDE,
		UNRESOLVED_INSIDE,
		UNRESOLVED_CLAIMED_SELECTED_OWNER_OUTSIDE,
		RELEVANT_INACTIVE,
		RELEVANT_DUPLICATE_IDENTITY
	}

	/** Stable count for one possible boundary-containment blocker. */
	public static final class BlockerCount {
		private final BlockerKind kind;
		private final int instanceCount;

		private BlockerCount(
			final BlockerKind kind,
			final int instanceCount) {
			if (kind == null) {
				throw new NullPointerException("kind");
			}
			if (instanceCount < 0) {
				throw new IllegalArgumentException(
					"Containment blocker count must not be negative");
			}
			this.kind = kind;
			this.instanceCount = instanceCount;
		}

		public BlockerKind getKind() { return kind; }
		public int getInstanceCount() { return instanceCount; }
	}
}
