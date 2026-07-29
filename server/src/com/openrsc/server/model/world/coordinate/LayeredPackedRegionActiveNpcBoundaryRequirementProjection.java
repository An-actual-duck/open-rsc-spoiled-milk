package com.openrsc.server.model.world.coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Projects exact missing sources from one detached active-NPC observation.
 *
 * <p>A recognized selected-owned NPC outside requires its current source. A
 * recognized external-owned NPC inside requires its authored owner source.
 * Unresolved identity, relevant inactive instances, and duplicate identity
 * remain hard evidence that source expansion cannot resolve.</p>
 *
 * <p>The projection never mutates a selection and never proves closure. Every
 * proposed source still requires a fresh complete safety assessment and NPC
 * census. This value has no entity, Region, registry, arrival, loading,
 * retention, release, teardown, reconstruction, or lifecycle authority.</p>
 */
public final class LayeredPackedRegionActiveNpcBoundaryRequirementProjection {
	public static final int MAXIMUM_REQUIREMENTS =
		LayeredPackedRegionActiveNpcResidencyObservation.MAXIMUM_INSTANCES;

	private static final Comparator<SourceRequirement> REQUIREMENT_COMPARATOR =
		new Comparator<SourceRequirement>() {
			@Override
			public int compare(
				final SourceRequirement left,
				final SourceRequirement right) {
				int byY = Integer.compare(
					left.getPackedRegionY(), right.getPackedRegionY());
				return byY != 0 ? byY : Integer.compare(
					left.getPackedRegionX(), right.getPackedRegionX());
			}
		};

	private final long generation;
	private final long safetyObservedAtTick;
	private final long censusObservedAtTick;
	private final int selectedSourceCount;
	private final boolean boundaryContainedNow;
	private final int selectedOwnerOutsideInstanceCount;
	private final int externalOwnerInsideInstanceCount;
	private final int expandableBoundaryInstanceCount;
	private final int uniqueRequiredSourceCount;
	private final int unresolvedInsideInstanceCount;
	private final int unresolvedClaimedSelectedOwnerOutsideInstanceCount;
	private final int relevantInactiveInstanceCount;
	private final int relevantDuplicateIdentityInstanceCount;
	private final int hardBlockingConditionCount;
	private final int hardBlockingEvidenceCount;
	private final List<SourceRequirement> requirements;

	private LayeredPackedRegionActiveNpcBoundaryRequirementProjection(
		final LayeredPackedRegionActiveNpcResidencyObservation observation,
		final LayeredPackedRegionActiveNpcContainmentAssessment assessment,
		final List<SourceRequirement> requirements) {
		this.generation = observation.getGeneration();
		this.safetyObservedAtTick = observation.getSafetyObservedAtTick();
		this.censusObservedAtTick = observation.getCensusObservedAtTick();
		this.selectedSourceCount = observation.getSelectedSourceCount();
		this.boundaryContainedNow = assessment.isBoundaryContained();
		this.selectedOwnerOutsideInstanceCount =
			observation.getSelectedOwnerOutsideCount();
		this.externalOwnerInsideInstanceCount =
			observation.getExternalOwnerInsideCount();
		this.expandableBoundaryInstanceCount = Math.addExact(
			selectedOwnerOutsideInstanceCount,
			externalOwnerInsideInstanceCount);
		this.uniqueRequiredSourceCount = requirements.size();
		this.unresolvedInsideInstanceCount =
			observation.getUnresolvedInsideCount();
		this.unresolvedClaimedSelectedOwnerOutsideInstanceCount =
			observation.getUnresolvedClaimedSelectedOwnerOutsideCount();
		this.relevantInactiveInstanceCount =
			observation.getInactiveRelevantInstanceCount();
		this.relevantDuplicateIdentityInstanceCount =
			assessment.getRelevantDuplicateIdentityInstanceCount();
		this.hardBlockingConditionCount = countPositive(
			unresolvedInsideInstanceCount,
			unresolvedClaimedSelectedOwnerOutsideInstanceCount,
			relevantInactiveInstanceCount,
			relevantDuplicateIdentityInstanceCount);
		this.hardBlockingEvidenceCount = Math.addExact(
			Math.addExact(
				unresolvedInsideInstanceCount,
				unresolvedClaimedSelectedOwnerOutsideInstanceCount),
			Math.addExact(
				relevantInactiveInstanceCount,
				relevantDuplicateIdentityInstanceCount));
		this.requirements = Collections.unmodifiableList(
			new ArrayList<SourceRequirement>(requirements));

		int projectedInstances = 0;
		for (SourceRequirement requirement : requirements) {
			projectedInstances = Math.addExact(
				projectedInstances, requirement.getBoundaryInstanceCount());
		}
		if (projectedInstances != expandableBoundaryInstanceCount) {
			throw new IllegalArgumentException(
				"Active NPC boundary requirement arithmetic is inconsistent");
		}
	}

	/**
	 * Produces a bounded, detached source proposal. Overflow refuses the whole
	 * projection instead of truncating a boundary requirement.
	 */
	public static LayeredPackedRegionActiveNpcBoundaryRequirementProjection
		project(
			final LayeredPackedRegionActiveNpcResidencyObservation observation,
			final int maximumRequirements) {
		if (observation == null) {
			throw new NullPointerException("observation");
		}
		if (maximumRequirements < 0
			|| maximumRequirements > MAXIMUM_REQUIREMENTS) {
			throw new IllegalArgumentException(
				"Active NPC boundary requirement budget is out of range");
		}
		LayeredPackedRegionActiveNpcContainmentAssessment assessment =
			LayeredPackedRegionActiveNpcContainmentAssessment.assess(observation);
		Map<Long, MutableRequirement> bySource =
			new LinkedHashMap<Long, MutableRequirement>();
		for (LayeredPackedRegionActiveNpcResidencyObservation.InstanceEvidence
			evidence : observation.getRelevantActiveInstances()) {
			RequirementReason reason;
			int packedRegionX;
			int packedRegionY;
			switch (evidence.getClassification()) {
				case SELECTED_OWNER_OUTSIDE:
					requireRecognized(evidence);
					reason = RequirementReason.SELECTED_OWNER_CURRENT_SOURCE;
					packedRegionX = evidence.getCurrentPackedRegionX();
					packedRegionY = evidence.getCurrentPackedRegionY();
					break;
				case EXTERNAL_OWNER_INSIDE:
					requireRecognized(evidence);
					reason = RequirementReason.EXTERNAL_OWNER_AUTHORED_SOURCE;
					packedRegionX = evidence.getIdentityPackedRegionX();
					packedRegionY = evidence.getIdentityPackedRegionY();
					break;
				default:
					continue;
			}
			long key = sourceKey(packedRegionX, packedRegionY);
			MutableRequirement mutable = bySource.get(Long.valueOf(key));
			if (mutable == null) {
				if (bySource.size() >= maximumRequirements) {
					throw new IllegalArgumentException(
						"Active NPC boundary requirements exceed their budget");
				}
				mutable = new MutableRequirement(packedRegionX, packedRegionY);
				bySource.put(Long.valueOf(key), mutable);
			}
			mutable.record(reason);
		}
		List<SourceRequirement> requirements =
			new ArrayList<SourceRequirement>(bySource.size());
		for (MutableRequirement mutable : bySource.values()) {
			requirements.add(mutable.freeze());
		}
		Collections.sort(requirements, REQUIREMENT_COMPARATOR);
		return new LayeredPackedRegionActiveNpcBoundaryRequirementProjection(
			observation, assessment, requirements);
	}

	private static void requireRecognized(
		final LayeredPackedRegionActiveNpcResidencyObservation.InstanceEvidence
			evidence) {
		if (!evidence.hasAuthoredIdentity()
			|| evidence.getIdentityStatus()
				!= LayeredPackedRegionActiveNpcResidencyObservation
					.IdentityStatus.RECOGNIZED) {
			throw new IllegalArgumentException(
				"Expandable NPC boundary evidence lacks recognized identity");
		}
	}

	private static long sourceKey(
		final int packedRegionX,
		final int packedRegionY) {
		return ((long) packedRegionX << 32)
			^ (packedRegionY & 0xffffffffL);
	}

	private static int countPositive(final int... counts) {
		int result = 0;
		for (int count : counts) {
			if (count > 0) {
				result = Math.incrementExact(result);
			}
		}
		return result;
	}

	public long getGeneration() { return generation; }
	public long getSafetyObservedAtTick() { return safetyObservedAtTick; }
	public long getCensusObservedAtTick() { return censusObservedAtTick; }
	public int getSelectedSourceCount() { return selectedSourceCount; }
	public boolean isBoundaryContainedNow() { return boundaryContainedNow; }
	public int getSelectedOwnerOutsideInstanceCount() {
		return selectedOwnerOutsideInstanceCount;
	}
	public int getExternalOwnerInsideInstanceCount() {
		return externalOwnerInsideInstanceCount;
	}
	public int getExpandableBoundaryInstanceCount() {
		return expandableBoundaryInstanceCount;
	}
	public int getUniqueRequiredSourceCount() {
		return uniqueRequiredSourceCount;
	}
	public int getUnresolvedInsideInstanceCount() {
		return unresolvedInsideInstanceCount;
	}
	public int getUnresolvedClaimedSelectedOwnerOutsideInstanceCount() {
		return unresolvedClaimedSelectedOwnerOutsideInstanceCount;
	}
	public int getRelevantInactiveInstanceCount() {
		return relevantInactiveInstanceCount;
	}
	public int getRelevantDuplicateIdentityInstanceCount() {
		return relevantDuplicateIdentityInstanceCount;
	}
	public int getHardBlockingConditionCount() {
		return hardBlockingConditionCount;
	}
	public int getHardBlockingEvidenceCount() {
		return hardBlockingEvidenceCount;
	}
	public List<SourceRequirement> getRequirements() { return requirements; }
	public boolean isFreshSafetyAssessmentRequired() { return true; }
	public boolean isFreshNpcCensusRequired() { return true; }
	public boolean isSelectionMutated() { return false; }
	public boolean isBoundaryClosureProved() { return false; }
	public boolean isEntityRegistry() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	public enum RequirementReason {
		SELECTED_OWNER_CURRENT_SOURCE,
		EXTERNAL_OWNER_AUTHORED_SOURCE
	}

	/** Exact proposed source with stable reason-specific instance counts. */
	public static final class SourceRequirement {
		private final int packedRegionX;
		private final int packedRegionY;
		private final int selectedOwnerCurrentSourceInstanceCount;
		private final int externalOwnerAuthoredSourceInstanceCount;
		private final int boundaryInstanceCount;
		private final List<ReasonCount> reasons;

		private SourceRequirement(
			final MutableRequirement mutable) {
			this.packedRegionX = mutable.packedRegionX;
			this.packedRegionY = mutable.packedRegionY;
			this.selectedOwnerCurrentSourceInstanceCount =
				mutable.selectedOwnerCurrentSourceInstanceCount;
			this.externalOwnerAuthoredSourceInstanceCount =
				mutable.externalOwnerAuthoredSourceInstanceCount;
			this.boundaryInstanceCount = Math.addExact(
				selectedOwnerCurrentSourceInstanceCount,
				externalOwnerAuthoredSourceInstanceCount);
			List<ReasonCount> builtReasons = new ArrayList<ReasonCount>(2);
			builtReasons.add(new ReasonCount(
				RequirementReason.SELECTED_OWNER_CURRENT_SOURCE,
				selectedOwnerCurrentSourceInstanceCount));
			builtReasons.add(new ReasonCount(
				RequirementReason.EXTERNAL_OWNER_AUTHORED_SOURCE,
				externalOwnerAuthoredSourceInstanceCount));
			this.reasons = Collections.unmodifiableList(builtReasons);
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getSelectedOwnerCurrentSourceInstanceCount() {
			return selectedOwnerCurrentSourceInstanceCount;
		}
		public int getExternalOwnerAuthoredSourceInstanceCount() {
			return externalOwnerAuthoredSourceInstanceCount;
		}
		public int getBoundaryInstanceCount() { return boundaryInstanceCount; }
		public List<ReasonCount> getReasons() { return reasons; }
	}

	/** Stable count for one possible expansion reason. */
	public static final class ReasonCount {
		private final RequirementReason reason;
		private final int instanceCount;

		private ReasonCount(
			final RequirementReason reason,
			final int instanceCount) {
			this.reason = reason;
			this.instanceCount = instanceCount;
		}

		public RequirementReason getReason() { return reason; }
		public int getInstanceCount() { return instanceCount; }
	}

	private static final class MutableRequirement {
		final int packedRegionX;
		final int packedRegionY;
		int selectedOwnerCurrentSourceInstanceCount;
		int externalOwnerAuthoredSourceInstanceCount;

		MutableRequirement(
			final int packedRegionX,
			final int packedRegionY) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
		}

		void record(final RequirementReason reason) {
			switch (reason) {
				case SELECTED_OWNER_CURRENT_SOURCE:
					selectedOwnerCurrentSourceInstanceCount = Math.incrementExact(
						selectedOwnerCurrentSourceInstanceCount);
					break;
				case EXTERNAL_OWNER_AUTHORED_SOURCE:
					externalOwnerAuthoredSourceInstanceCount = Math.incrementExact(
						externalOwnerAuthoredSourceInstanceCount);
					break;
				default:
					throw new IllegalArgumentException(
						"Unsupported active NPC boundary requirement reason");
			}
		}

		SourceRequirement freeze() { return new SourceRequirement(this); }
	}
}
