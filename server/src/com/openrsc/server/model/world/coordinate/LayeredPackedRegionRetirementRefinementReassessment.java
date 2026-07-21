package com.openrsc.server.model.world.coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Reassesses one retirement-refinement candidate set against strictly newer,
 * atomically aligned safety, authored-cohort, and active-NPC evidence.
 *
 * <p>The previous proposal's ordered candidates must be the exact source set
 * observed by the fresh safety assessment. The fresh parents are combined by
 * {@link LayeredPackedRegionRetirementRefinementProposal#propose} again, so a
 * stable result means only that the candidate set did not grow at that one
 * observation. New active arrivals, authored-generation changes, or later
 * runtime mutations can invalidate it immediately.</p>
 *
 * <p>This value has no Region, entity, registry, arrival, loading, retention,
 * release, teardown, reconstruction, transaction, rollback, or lifecycle
 * authority. In particular, candidate-set convergence is not retirement
 * readiness and cannot serve as a commit token.</p>
 */
public final class LayeredPackedRegionRetirementRefinementReassessment {
	private final LayeredPackedRegionRetirementRefinementProposal
		previousProposal;
	private final LayeredPackedRegionRetirementSafetyAssessment freshSafety;
	private final LayeredPackedRegionRetirementRefinementProposal nextProposal;
	private final List<LayeredPackedRegionRetirementRefinementProposal
		.CandidateSource> newCandidates;

	private LayeredPackedRegionRetirementRefinementReassessment(
		final LayeredPackedRegionRetirementRefinementProposal previousProposal,
		final LayeredPackedRegionRetirementSafetyAssessment freshSafety,
		final LayeredPackedRegionRetirementRefinementProposal nextProposal,
		final List<LayeredPackedRegionRetirementRefinementProposal
			.CandidateSource> newCandidates) {
		this.previousProposal = previousProposal;
		this.freshSafety = freshSafety;
		this.nextProposal = nextProposal;
		this.newCandidates = Collections.unmodifiableList(
			new ArrayList<LayeredPackedRegionRetirementRefinementProposal
				.CandidateSource>(newCandidates));
	}

	/**
	 * Rebuilds the refinement proposal from one strictly newer atomic snapshot.
	 * Any stale, incomplete, reordered, foreign-generation, or overflowing
	 * input refuses the whole reassessment.
	 */
	public static LayeredPackedRegionRetirementRefinementReassessment reassess(
		final LayeredPackedRegionRetirementRefinementProposal previousProposal,
		final LayeredPackedRegionRetirementSafetyAssessment freshSafety,
		final LayeredPackedRegionAuthoredReconstructionCohortAnalysis freshCohort,
		final LayeredPackedRegionActiveNpcBoundaryRequirementProjection
			freshActiveNpcRequirements,
		final int maximumCandidateSources,
		final int maximumSupportSources) {
		LayeredPackedRegionRetirementRefinementProposal previous =
			Objects.requireNonNull(previousProposal, "previousProposal");
		LayeredPackedRegionRetirementSafetyAssessment safety =
			Objects.requireNonNull(freshSafety, "freshSafety");
		LayeredPackedRegionAuthoredReconstructionCohortAnalysis cohort =
			Objects.requireNonNull(freshCohort, "freshCohort");
		LayeredPackedRegionActiveNpcBoundaryRequirementProjection active =
			Objects.requireNonNull(
				freshActiveNpcRequirements, "freshActiveNpcRequirements");

		requireFreshCandidateObservation(previous, safety, cohort, active);
		LayeredPackedRegionRetirementRefinementProposal next =
			LayeredPackedRegionRetirementRefinementProposal.propose(
				safety, cohort, active, maximumCandidateSources,
				maximumSupportSources);
		if (next.getOriginalSafetySourceCount()
			!= previous.getCandidateSourceCount()) {
			throw new IllegalArgumentException(
				"Reassessment dropped a previously proposed candidate");
		}

		List<LayeredPackedRegionRetirementRefinementProposal.CandidateSource>
			additions = new ArrayList<
				LayeredPackedRegionRetirementRefinementProposal.CandidateSource>();
		for (LayeredPackedRegionRetirementRefinementProposal.CandidateSource
			candidate : next.getCandidates()) {
			if (candidate.isAddedBeyondOriginalSafety()) {
				additions.add(candidate);
			}
		}
		if (next.getCandidateSourceCount()
			!= Math.addExact(previous.getCandidateSourceCount(), additions.size())) {
			throw new IllegalArgumentException(
				"Reassessment candidate arithmetic is inconsistent");
		}
		return new LayeredPackedRegionRetirementRefinementReassessment(
			previous, safety, next, additions);
	}

	private static void requireFreshCandidateObservation(
		final LayeredPackedRegionRetirementRefinementProposal previous,
		final LayeredPackedRegionRetirementSafetyAssessment safety,
		final LayeredPackedRegionAuthoredReconstructionCohortAnalysis cohort,
		final LayeredPackedRegionActiveNpcBoundaryRequirementProjection active) {
		if (cohort.getGeneration() != previous.getGeneration()
			|| active.getGeneration() != previous.getGeneration()
			|| safety.getObservedAtTick()
				<= previous.getSafetyObservedAtTick()
			|| active.getCensusObservedAtTick()
				<= previous.getCensusObservedAtTick()
			|| safety.getSourceCount() != previous.getCandidateSourceCount()) {
			throw new IllegalArgumentException(
				"Retirement refinement reassessment is not fresh and complete");
		}
		List<LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment>
			freshSources = safety.getSources();
		List<LayeredPackedRegionRetirementRefinementProposal.CandidateSource>
			previousCandidates = previous.getCandidates();
		for (int index = 0; index < previousCandidates.size(); index++) {
			LayeredPackedRegionRetirementRefinementProposal.CandidateSource
				candidate = previousCandidates.get(index);
			LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment source =
				freshSources.get(index);
			if (candidate.getPackedRegionX() != source.getPackedRegionX()
				|| candidate.getPackedRegionY() != source.getPackedRegionY()) {
				throw new IllegalArgumentException(
					"Fresh safety sources differ from the proposed candidates");
			}
		}
	}

	public long getGeneration() { return nextProposal.getGeneration(); }
	public long getPreviousSafetyObservedAtTick() {
		return previousProposal.getSafetyObservedAtTick();
	}
	public long getPreviousCensusObservedAtTick() {
		return previousProposal.getCensusObservedAtTick();
	}
	public long getReassessmentSafetyObservedAtTick() {
		return nextProposal.getSafetyObservedAtTick();
	}
	public long getReassessmentCensusObservedAtTick() {
		return nextProposal.getCensusObservedAtTick();
	}
	public int getPreviousCandidateSourceCount() {
		return previousProposal.getCandidateSourceCount();
	}
	public int getReassessedSourceCount() { return freshSafety.getSourceCount(); }
	public int getRetainedCandidateSourceCount() {
		return previousProposal.getCandidateSourceCount();
	}
	public int getNextCandidateSourceCount() {
		return nextProposal.getCandidateSourceCount();
	}
	public int getNewCandidateSourceCount() { return newCandidates.size(); }
	public int getNextExternalSupportRequirementSourceCount() {
		return nextProposal.getExternalSupportRequirementSourceCount();
	}
	public int getHardBlockingConditionCount() {
		return nextProposal.getHardBlockingConditionCount();
	}
	public int getHardBlockingEvidenceCount() {
		return nextProposal.getHardBlockingEvidenceCount();
	}
	public int getLifecycleReadyEvidenceSourceCount() {
		return freshSafety.getLifecycleReadySourceCount();
	}
	public LayeredPackedRegionRetirementRefinementProposal getNextProposal() {
		return nextProposal;
	}
	public List<LayeredPackedRegionRetirementRefinementProposal.CandidateSource>
		getNewCandidates() {
		return newCandidates;
	}
	public boolean isFreshEvidenceAligned() { return true; }
	public boolean isCandidateSetStableAtObservation() {
		return newCandidates.isEmpty();
	}
	public boolean isFurtherRefinementRequired() {
		return !isCandidateSetStableAtObservation();
	}
	public boolean hasNonExpandableHardBlockers() {
		return nextProposal.hasNonExpandableHardBlockers();
	}
	public boolean isRefinementConvergedAtObservation() {
		return isCandidateSetStableAtObservation()
			&& !hasNonExpandableHardBlockers();
	}
	public boolean isAllReassessedSourcesLifecycleReadyEvidence() {
		return freshSafety.getSourceCount() > 0
			&& freshSafety.getLifecycleReadySourceCount()
				== freshSafety.getSourceCount();
	}
	public boolean isPointInTimeOnly() { return true; }
	public boolean isCandidateSelectionMutated() { return false; }
	public boolean isFixedPointLifecycleClosureProved() { return false; }
	public boolean isLoadRequest() { return false; }
	public boolean isEntityRegistry() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isRetirementCommitToken() { return false; }
	public boolean isLifecycleAuthority() { return false; }
}
