package com.openrsc.server.model.world.coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Combines static authored and point-in-time active-NPC source evidence into
 * one detached candidate union for a later retirement reassessment.
 *
 * <p>Original safety sources remain distinguishable from recursively added
 * authored sources and active-NPC boundary requirements. Empty static support
 * coordinates remain support evidence rather than becoming candidate sources
 * unless an independent active-NPC requirement also names that coordinate.</p>
 *
 * <p>This proposal never mutates a selection and never proves fixed-point
 * closure. Every added candidate explicitly lacks fresh safety and NPC-census
 * evidence. It has no Region, entity, registry, arrival, loading, retention,
 * release, teardown, reconstruction, transaction, rollback, or lifecycle
 * authority.</p>
 */
public final class LayeredPackedRegionRetirementRefinementProposal {
	public static final int MAXIMUM_CANDIDATE_SOURCES =
		LayeredPackedRegionAuthoredReconstructionRecipe.MAXIMUM_PACKED_SOURCES;
	public static final int MAXIMUM_SUPPORT_SOURCES =
		LayeredPackedRegionAuthoredReconstructionRecipe.MAXIMUM_PACKED_SOURCES;

	private static final Comparator<CandidateSource> CANDIDATE_COMPARATOR =
		new Comparator<CandidateSource>() {
			@Override
			public int compare(
				final CandidateSource left,
				final CandidateSource right) {
				int byY = Integer.compare(
					left.getPackedRegionY(), right.getPackedRegionY());
				return byY != 0 ? byY : Integer.compare(
					left.getPackedRegionX(), right.getPackedRegionX());
			}
		};
	private static final Comparator<SupportRequirement> SUPPORT_COMPARATOR =
		new Comparator<SupportRequirement>() {
			@Override
			public int compare(
				final SupportRequirement left,
				final SupportRequirement right) {
				int byY = Integer.compare(
					left.getPackedRegionY(), right.getPackedRegionY());
				return byY != 0 ? byY : Integer.compare(
					left.getPackedRegionX(), right.getPackedRegionX());
			}
		};

	private final long generation;
	private final long safetyObservedAtTick;
	private final long censusObservedAtTick;
	private final int originalSafetySourceCount;
	private final int authoredCohortSourceCount;
	private final int expandedAuthoredSourceCount;
	private final int activeNpcRequirementSourceCount;
	private final int candidateSourceCount;
	private final int addedCandidateSourceCount;
	private final int activeNpcAndAuthoredOverlapSourceCount;
	private final int externalSupportRequirementSourceCount;
	private final int supportPromotedToCandidateSourceCount;
	private final int hardBlockingConditionCount;
	private final int hardBlockingEvidenceCount;
	private final boolean boundaryContainedAtInput;
	private final List<CandidateSource> candidates;
	private final List<SupportRequirement> externalSupportRequirements;

	private LayeredPackedRegionRetirementRefinementProposal(
		final LayeredPackedRegionAuthoredReconstructionCohortAnalysis cohort,
		final LayeredPackedRegionActiveNpcBoundaryRequirementProjection active,
		final List<CandidateSource> candidates,
		final List<SupportRequirement> supportRequirements) {
		this.generation = cohort.getGeneration();
		this.safetyObservedAtTick = cohort.getSafetyObservedAtTick();
		this.censusObservedAtTick = active.getCensusObservedAtTick();
		this.originalSafetySourceCount = cohort.getSeedSourceCount();
		this.authoredCohortSourceCount = cohort.getCohortSourceCount();
		this.expandedAuthoredSourceCount =
			cohort.getExpandedAuthoredSourceCount();
		this.activeNpcRequirementSourceCount =
			active.getUniqueRequiredSourceCount();
		this.candidateSourceCount = candidates.size();
		int added = 0;
		int activeAuthoredOverlap = 0;
		int promotedSupport = 0;
		for (CandidateSource candidate : candidates) {
			added += candidate.isAddedBeyondOriginalSafety() ? 1 : 0;
			activeAuthoredOverlap += candidate.isActiveNpcBoundarySource()
				&& candidate.isAuthoredCohortSource() ? 1 : 0;
			promotedSupport += candidate.isExternalStaticSupportSource() ? 1 : 0;
		}
		this.addedCandidateSourceCount = added;
		this.activeNpcAndAuthoredOverlapSourceCount = activeAuthoredOverlap;
		this.externalSupportRequirementSourceCount = supportRequirements.size();
		this.supportPromotedToCandidateSourceCount = promotedSupport;
		this.hardBlockingConditionCount =
			active.getHardBlockingConditionCount();
		this.hardBlockingEvidenceCount = active.getHardBlockingEvidenceCount();
		this.boundaryContainedAtInput = active.isBoundaryContainedNow();
		this.candidates = Collections.unmodifiableList(
			new ArrayList<CandidateSource>(candidates));
		this.externalSupportRequirements = Collections.unmodifiableList(
			new ArrayList<SupportRequirement>(supportRequirements));
	}

	/**
	 * Produces one bounded, provenance-tagged candidate union. Any mismatch or
	 * overflow refuses the whole proposal instead of dropping evidence.
	 */
	public static LayeredPackedRegionRetirementRefinementProposal propose(
		final LayeredPackedRegionRetirementSafetyAssessment safety,
		final LayeredPackedRegionAuthoredReconstructionCohortAnalysis cohort,
		final LayeredPackedRegionActiveNpcBoundaryRequirementProjection active,
		final int maximumCandidateSources,
		final int maximumSupportSources) {
		if (safety == null) { throw new NullPointerException("safety"); }
		if (cohort == null) { throw new NullPointerException("cohort"); }
		if (active == null) { throw new NullPointerException("active"); }
		if (maximumCandidateSources < 0
			|| maximumCandidateSources > MAXIMUM_CANDIDATE_SOURCES
			|| maximumSupportSources < 0
			|| maximumSupportSources > MAXIMUM_SUPPORT_SOURCES
			|| safety.getSourceCount() > maximumCandidateSources) {
			throw new IllegalArgumentException(
				"Retirement refinement proposal exceeds its source budget");
		}
		requireAlignedInputs(safety, cohort, active);

		Map<Long, MutableCandidate> bySource =
			new LinkedHashMap<Long, MutableCandidate>();
		Set<Long> originalSafetySources = new LinkedHashSet<Long>();
		for (LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment
			source : safety.getSources()) {
			long key = sourceKey(
				source.getPackedRegionX(), source.getPackedRegionY());
			if (!originalSafetySources.add(Long.valueOf(key))) {
				throw new IllegalArgumentException(
					"Retirement safety contains a duplicate source");
			}
			MutableCandidate candidate = requireCandidate(
				bySource, source.getPackedRegionX(), source.getPackedRegionY(),
				maximumCandidateSources);
			candidate.originalSafetySource = true;
		}

		for (LayeredPackedRegionAuthoredReconstructionCohortAnalysis
			.SourceAnalysis source : cohort.getSources()) {
			MutableCandidate candidate = requireCandidate(
				bySource, source.getPackedRegionX(), source.getPackedRegionY(),
				maximumCandidateSources);
			candidate.authoredCohortSource = true;
			candidate.authoredExpansionRound = Integer.valueOf(
				source.getExpansionRound());
		}

		List<LayeredPackedRegionAuthoredReconstructionCohortAnalysis
			.RequirementAnalysis> externalSupportAnalyses =
				new ArrayList<LayeredPackedRegionAuthoredReconstructionCohortAnalysis
					.RequirementAnalysis>();
		for (LayeredPackedRegionAuthoredReconstructionCohortAnalysis
			.RequirementAnalysis requirement : cohort.getRequirements()) {
			if (!requirement.isExternalSupportRequired()) { continue; }
			if (externalSupportAnalyses.size() >= maximumSupportSources) {
				throw new IllegalArgumentException(
					"Retirement refinement support exceeds its source budget");
			}
			externalSupportAnalyses.add(requirement);
		}

		for (LayeredPackedRegionActiveNpcBoundaryRequirementProjection
			.SourceRequirement requirement : active.getRequirements()) {
			long key = sourceKey(
				requirement.getPackedRegionX(), requirement.getPackedRegionY());
			if (originalSafetySources.contains(Long.valueOf(key))) {
				throw new IllegalArgumentException(
					"Active NPC requirement is already an original safety source");
			}
			MutableCandidate candidate = requireCandidate(
				bySource, requirement.getPackedRegionX(),
				requirement.getPackedRegionY(), maximumCandidateSources);
			candidate.selectedOwnerCurrentSourceInstanceCount = Math.addExact(
				candidate.selectedOwnerCurrentSourceInstanceCount,
				requirement.getSelectedOwnerCurrentSourceInstanceCount());
			candidate.externalOwnerAuthoredSourceInstanceCount = Math.addExact(
				candidate.externalOwnerAuthoredSourceInstanceCount,
				requirement.getExternalOwnerAuthoredSourceInstanceCount());
		}

		List<SupportRequirement> supportRequirements =
			new ArrayList<SupportRequirement>(externalSupportAnalyses.size());
		for (LayeredPackedRegionAuthoredReconstructionCohortAnalysis
			.RequirementAnalysis support : externalSupportAnalyses) {
			MutableCandidate candidate = bySource.get(Long.valueOf(sourceKey(
				support.getPackedRegionX(), support.getPackedRegionY())));
			if (candidate != null) {
				candidate.externalStaticSupportSource = true;
				candidate.staticSupportOwnerSourceCount =
					support.getOwnerSourceCount();
				candidate.staticSupportPlacementReferenceCount =
					support.getPlacementReferenceCount();
			}
			supportRequirements.add(new SupportRequirement(
				support, candidate != null));
		}

		List<CandidateSource> candidates =
			new ArrayList<CandidateSource>(bySource.size());
		for (MutableCandidate candidate : bySource.values()) {
			candidates.add(new CandidateSource(candidate));
		}
		Collections.sort(candidates, CANDIDATE_COMPARATOR);
		Collections.sort(supportRequirements, SUPPORT_COMPARATOR);
		return new LayeredPackedRegionRetirementRefinementProposal(
			cohort, active, candidates, supportRequirements);
	}

	private static void requireAlignedInputs(
		final LayeredPackedRegionRetirementSafetyAssessment safety,
		final LayeredPackedRegionAuthoredReconstructionCohortAnalysis cohort,
		final LayeredPackedRegionActiveNpcBoundaryRequirementProjection active) {
		if (cohort.getGeneration() != active.getGeneration()
			|| safety.getObservedAtTick() != cohort.getSafetyObservedAtTick()
			|| safety.getObservedAtTick() != active.getSafetyObservedAtTick()
			|| safety.getSourceCount() != cohort.getSeedSourceCount()
			|| safety.getSourceCount() != active.getSelectedSourceCount()
			|| cohort.getSources().size() < safety.getSourceCount()) {
			throw new IllegalArgumentException(
				"Retirement refinement inputs are not one atomic source set");
		}
		for (int index = 0; index < safety.getSourceCount(); index++) {
			LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment seed =
				safety.getSources().get(index);
			LayeredPackedRegionAuthoredReconstructionCohortAnalysis.SourceAnalysis
				cohortSeed = cohort.getSources().get(index);
			if (cohortSeed.getRole()
					!= LayeredPackedRegionAuthoredReconstructionCohortAnalysis
						.CohortRole.SEED
				|| seed.getPackedRegionX() != cohortSeed.getPackedRegionX()
				|| seed.getPackedRegionY() != cohortSeed.getPackedRegionY()) {
				throw new IllegalArgumentException(
					"Authored cohort seeds differ from retirement safety");
			}
		}
		for (int index = safety.getSourceCount();
			index < cohort.getSources().size(); index++) {
			if (cohort.getSources().get(index).getRole()
				!= LayeredPackedRegionAuthoredReconstructionCohortAnalysis
					.CohortRole.EXPANDED_AUTHORED) {
				throw new IllegalArgumentException(
					"Authored cohort expansion contains a misplaced seed");
			}
		}
	}

	private static MutableCandidate requireCandidate(
		final Map<Long, MutableCandidate> bySource,
		final int packedRegionX,
		final int packedRegionY,
		final int maximumCandidateSources) {
		Long key = Long.valueOf(sourceKey(packedRegionX, packedRegionY));
		MutableCandidate candidate = bySource.get(key);
		if (candidate != null) { return candidate; }
		if (bySource.size() >= maximumCandidateSources) {
			throw new IllegalArgumentException(
				"Retirement refinement candidates exceed their source budget");
		}
		candidate = new MutableCandidate(packedRegionX, packedRegionY);
		bySource.put(key, candidate);
		return candidate;
	}

	private static long sourceKey(
		final int packedRegionX,
		final int packedRegionY) {
		return ((long) packedRegionX << 32)
			^ (packedRegionY & 0xffffffffL);
	}

	public long getGeneration() { return generation; }
	public long getSafetyObservedAtTick() { return safetyObservedAtTick; }
	public long getCensusObservedAtTick() { return censusObservedAtTick; }
	public int getOriginalSafetySourceCount() {
		return originalSafetySourceCount;
	}
	public int getAuthoredCohortSourceCount() {
		return authoredCohortSourceCount;
	}
	public int getExpandedAuthoredSourceCount() {
		return expandedAuthoredSourceCount;
	}
	public int getActiveNpcRequirementSourceCount() {
		return activeNpcRequirementSourceCount;
	}
	public int getCandidateSourceCount() { return candidateSourceCount; }
	public int getAddedCandidateSourceCount() {
		return addedCandidateSourceCount;
	}
	public int getActiveNpcAndAuthoredOverlapSourceCount() {
		return activeNpcAndAuthoredOverlapSourceCount;
	}
	public int getExternalSupportRequirementSourceCount() {
		return externalSupportRequirementSourceCount;
	}
	public int getSupportPromotedToCandidateSourceCount() {
		return supportPromotedToCandidateSourceCount;
	}
	public int getHardBlockingConditionCount() {
		return hardBlockingConditionCount;
	}
	public int getHardBlockingEvidenceCount() {
		return hardBlockingEvidenceCount;
	}
	public boolean isBoundaryContainedAtInput() {
		return boundaryContainedAtInput;
	}
	public List<CandidateSource> getCandidates() { return candidates; }
	public List<SupportRequirement> getExternalSupportRequirements() {
		return externalSupportRequirements;
	}
	public boolean hasNonExpandableHardBlockers() {
		return hardBlockingConditionCount > 0;
	}
	public boolean isFreshSafetyAssessmentRequired() { return true; }
	public boolean isFreshNpcCensusRequired() { return true; }
	public boolean isReassessmentRequired() { return true; }
	public boolean isCandidateSelectionMutated() { return false; }
	public boolean isFixedPointClosureProved() { return false; }
	public boolean isLoadRequest() { return false; }
	public boolean isEntityRegistry() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	/** One candidate coordinate with every reason for its inclusion retained. */
	public static final class CandidateSource {
		private final int packedRegionX;
		private final int packedRegionY;
		private final boolean originalSafetySource;
		private final boolean authoredCohortSource;
		private final Integer authoredExpansionRound;
		private final boolean externalStaticSupportSource;
		private final int staticSupportOwnerSourceCount;
		private final int staticSupportPlacementReferenceCount;
		private final int selectedOwnerCurrentSourceInstanceCount;
		private final int externalOwnerAuthoredSourceInstanceCount;

		private CandidateSource(final MutableCandidate source) {
			this.packedRegionX = source.packedRegionX;
			this.packedRegionY = source.packedRegionY;
			this.originalSafetySource = source.originalSafetySource;
			this.authoredCohortSource = source.authoredCohortSource;
			this.authoredExpansionRound = source.authoredExpansionRound;
			this.externalStaticSupportSource =
				source.externalStaticSupportSource;
			this.staticSupportOwnerSourceCount =
				source.staticSupportOwnerSourceCount;
			this.staticSupportPlacementReferenceCount =
				source.staticSupportPlacementReferenceCount;
			this.selectedOwnerCurrentSourceInstanceCount =
				source.selectedOwnerCurrentSourceInstanceCount;
			this.externalOwnerAuthoredSourceInstanceCount =
				source.externalOwnerAuthoredSourceInstanceCount;
			if (authoredCohortSource != (authoredExpansionRound != null)
				|| (originalSafetySource && !authoredCohortSource)
				|| (externalStaticSupportSource
					&& staticSupportPlacementReferenceCount <= 0)
				|| (!externalStaticSupportSource
					&& (staticSupportOwnerSourceCount != 0
						|| staticSupportPlacementReferenceCount != 0))) {
				throw new IllegalArgumentException(
					"Retirement refinement candidate provenance is inconsistent");
			}
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public boolean isOriginalSafetySource() {
			return originalSafetySource;
		}
		public boolean isAuthoredCohortSource() {
			return authoredCohortSource;
		}
		public Integer getAuthoredExpansionRound() {
			return authoredExpansionRound;
		}
		public boolean isExternalStaticSupportSource() {
			return externalStaticSupportSource;
		}
		public int getStaticSupportOwnerSourceCount() {
			return staticSupportOwnerSourceCount;
		}
		public int getStaticSupportPlacementReferenceCount() {
			return staticSupportPlacementReferenceCount;
		}
		public int getSelectedOwnerCurrentSourceInstanceCount() {
			return selectedOwnerCurrentSourceInstanceCount;
		}
		public int getExternalOwnerAuthoredSourceInstanceCount() {
			return externalOwnerAuthoredSourceInstanceCount;
		}
		public int getActiveNpcBoundaryInstanceCount() {
			return Math.addExact(
				selectedOwnerCurrentSourceInstanceCount,
				externalOwnerAuthoredSourceInstanceCount);
		}
		public boolean isActiveNpcBoundarySource() {
			return getActiveNpcBoundaryInstanceCount() > 0;
		}
		public boolean isAddedBeyondOriginalSafety() {
			return !originalSafetySource;
		}
		public boolean isFreshSafetyEvidenceRequired() {
			return isAddedBeyondOriginalSafety();
		}
		public boolean isFreshNpcCensusRequired() {
			return isAddedBeyondOriginalSafety();
		}
	}

	/** One static support coordinate, possibly promoted for an active reason. */
	public static final class SupportRequirement {
		private final int packedRegionX;
		private final int packedRegionY;
		private final int ownerSourceCount;
		private final int placementReferenceCount;
		private final boolean candidateSource;

		private SupportRequirement(
			final LayeredPackedRegionAuthoredReconstructionCohortAnalysis
				.RequirementAnalysis requirement,
			final boolean candidateSource) {
			this.packedRegionX = requirement.getPackedRegionX();
			this.packedRegionY = requirement.getPackedRegionY();
			this.ownerSourceCount = requirement.getOwnerSourceCount();
			this.placementReferenceCount =
				requirement.getPlacementReferenceCount();
			this.candidateSource = candidateSource;
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getOwnerSourceCount() { return ownerSourceCount; }
		public int getPlacementReferenceCount() {
			return placementReferenceCount;
		}
		public boolean isCandidateSource() { return candidateSource; }
		public boolean isExternalStaticSupportRequired() { return true; }
	}

	private static final class MutableCandidate {
		private final int packedRegionX;
		private final int packedRegionY;
		private boolean originalSafetySource;
		private boolean authoredCohortSource;
		private Integer authoredExpansionRound;
		private boolean externalStaticSupportSource;
		private int staticSupportOwnerSourceCount;
		private int staticSupportPlacementReferenceCount;
		private int selectedOwnerCurrentSourceInstanceCount;
		private int externalOwnerAuthoredSourceInstanceCount;

		private MutableCandidate(
			final int packedRegionX,
			final int packedRegionY) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
		}
	}
}
