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
 * Bounded fixed-point analysis of authored reconstruction dependencies.
 *
 * <p>Exact retirement-safety sources are seeds. A required packed source with
 * final-live authored content joins the cohort and contributes its own
 * dependencies in the next expansion round. A required coordinate without
 * final-live authored content remains an external support requirement. That
 * distinction prevents an empty neighbor reached by an object footprint or an
 * NPC roaming envelope from being mistaken for reconstructable content.</p>
 *
 * <p>This analysis is detached evidence only. It retains no entity, Region,
 * tile, archive, event, registry, cache, callback, claim, permit, lease,
 * transaction, commit, load, teardown, reconstruction, or rollback authority.
 * A self-contained result is not permission to alter runtime lifecycle.</p>
 */
public final class LayeredPackedRegionAuthoredReconstructionCohortAnalysis {
	private final long generation;
	private final long safetyObservedAtTick;
	private final int seedSourceCount;
	private final List<SourceAnalysis> sources;
	private final List<RequirementAnalysis> requirements;
	private final int authoredContentSourceCount;
	private final int reconstructionPlacementCount;
	private final int crossSourcePlacementCount;
	private final int affectedSourceReferenceCount;
	private final int cohortRequirementSourceCount;
	private final int externalSupportRequirementSourceCount;
	private final int maximumExpansionRound;

	private LayeredPackedRegionAuthoredReconstructionCohortAnalysis(
		final LayeredPackedRegionAuthoredReconstructionRecipe recipe,
		final LayeredPackedRegionRetirementSafetyAssessment safety,
		final List<SourceAnalysis> sources,
		final List<RequirementAnalysis> requirements) {
		this.generation = recipe.getGeneration();
		this.safetyObservedAtTick = safety.getObservedAtTick();
		this.seedSourceCount = safety.getSourceCount();
		this.sources = Collections.unmodifiableList(
			new ArrayList<SourceAnalysis>(sources));
		this.requirements = Collections.unmodifiableList(
			new ArrayList<RequirementAnalysis>(requirements));
		int authoredSources = 0;
		int placements = 0;
		int crossSource = 0;
		int sourceReferences = 0;
		int maximumRound = 0;
		for (SourceAnalysis source : sources) {
			authoredSources += source.hasAuthoredContent() ? 1 : 0;
			placements = Math.addExact(
				placements, source.getReconstructionPlacementCount());
			crossSource = Math.addExact(
				crossSource, source.getCrossSourcePlacementCount());
			sourceReferences = Math.addExact(
				sourceReferences, source.getAffectedSourceReferenceCount());
			maximumRound = Math.max(maximumRound, source.getExpansionRound());
		}
		int cohortRequirements = 0;
		int supportRequirements = 0;
		for (RequirementAnalysis requirement : requirements) {
			if (requirement.isCohortSource()) {
				cohortRequirements = Math.incrementExact(cohortRequirements);
			} else {
				if (requirement.hasAuthoredContent()) {
					throw new IllegalArgumentException(
						"Authored dependency remained outside the fixed-point cohort");
				}
				supportRequirements = Math.incrementExact(supportRequirements);
			}
		}
		this.authoredContentSourceCount = authoredSources;
		this.reconstructionPlacementCount = placements;
		this.crossSourcePlacementCount = crossSource;
		this.affectedSourceReferenceCount = sourceReferences;
		this.cohortRequirementSourceCount = cohortRequirements;
		this.externalSupportRequirementSourceCount = supportRequirements;
		this.maximumExpansionRound = maximumRound;
	}

	/**
	 * Expands exact safety seeds until no dependency with authored content is
	 * outside the cohort. Either budget overflow is refused in full.
	 */
	public static LayeredPackedRegionAuthoredReconstructionCohortAnalysis
		analyze(
			final LayeredPackedRegionAuthoredReconstructionRecipe recipe,
			final LayeredPackedRegionRetirementSafetyAssessment safety,
			final int maximumCohortSources,
			final int maximumRequirementSources) {
		if (recipe == null) {
			throw new NullPointerException("recipe");
		}
		if (safety == null) {
			throw new NullPointerException("safety");
		}
		if (maximumCohortSources < 0
			|| maximumCohortSources
				> LayeredPackedRegionAuthoredReconstructionRecipe
					.MAXIMUM_PACKED_SOURCES
			|| maximumRequirementSources < 0
			|| maximumRequirementSources
				> LayeredPackedRegionAuthoredReconstructionRecipe
					.MAXIMUM_PACKED_SOURCES
			|| safety.getSourceCount() > maximumCohortSources) {
			throw new IllegalArgumentException(
				"Reconstruction cohort analysis exceeds its source budget");
		}

		Map<Long, MutableSource> cohort =
			new LinkedHashMap<Long, MutableSource>();
		List<MutableSource> orderedSources =
			new ArrayList<MutableSource>(safety.getSourceCount());
		for (LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment
			seed : safety.getSources()) {
			MutableSource source = new MutableSource(
				seed.getPackedRegionX(), seed.getPackedRegionY(),
				CohortRole.SEED, 0,
				recipe.findSource(
					seed.getPackedRegionX(), seed.getPackedRegionY()));
			Long key = Long.valueOf(packedSourceKey(
				source.packedRegionX, source.packedRegionY));
			if (cohort.put(key, source) != null) {
				throw new IllegalArgumentException(
					"Safety observation contains a duplicate packed source");
			}
			orderedSources.add(source);
		}

		Map<Long, MutableRequirement> requirements =
			new LinkedHashMap<Long, MutableRequirement>();
		for (int sourceIndex = 0;
			sourceIndex < orderedSources.size(); sourceIndex++) {
			MutableSource source = orderedSources.get(sourceIndex);
			if (!source.hasAuthoredContent()) {
				continue;
			}
			for (LayeredPackedRegionAuthoredReconstructionRecipe
				.ReconstructionPlacement placement
					: source.recipe.getPlacements()) {
				LayeredPackedRegionAuthoredPlacementDependencyInventory
					.PlacementDependency dependency = placement.getDependency();
				for (int x = dependency.getMinimumPackedRegionX();
					x <= dependency.getMaximumPackedRegionX(); x++) {
					for (int y = dependency.getMinimumPackedRegionY();
						y <= dependency.getMaximumPackedRegionY(); y++) {
						Long key = Long.valueOf(packedSourceKey(x, y));
						LayeredPackedRegionAuthoredReconstructionRecipe
							.PackedSourceRecipe requiredRecipe =
								recipe.findSource(x, y);
						MutableRequirement requirement = requirements.get(key);
						if (requirement == null) {
							if (requirements.size() >= maximumRequirementSources) {
								throw new IllegalArgumentException(
									"Reconstruction cohort requirements exceed "
										+ "their source budget");
							}
							requirement = new MutableRequirement(
								x, y, requiredRecipe);
							requirements.put(key, requirement);
						}
						requirement.placementReferenceCount = Math.incrementExact(
							requirement.placementReferenceCount);
						if (source.requirements.add(key)) {
							requirement.ownerSourceCount = Math.incrementExact(
								requirement.ownerSourceCount);
						}
						if (hasAuthoredContent(requiredRecipe)
							&& !cohort.containsKey(key)) {
							if (cohort.size() >= maximumCohortSources) {
								throw new IllegalArgumentException(
									"Authored reconstruction cohort exceeds "
										+ "its source budget");
							}
							MutableSource expanded = new MutableSource(
								x, y, CohortRole.EXPANDED_AUTHORED,
								Math.incrementExact(source.expansionRound),
								requiredRecipe);
							cohort.put(key, expanded);
							orderedSources.add(expanded);
						}
					}
				}
			}
		}

		List<SourceAnalysis> sourceAnalyses =
			new ArrayList<SourceAnalysis>(orderedSources.size());
		for (MutableSource source : orderedSources) {
			int cohortRequirements = 0;
			for (Long requirement : source.requirements) {
				cohortRequirements += cohort.containsKey(requirement) ? 1 : 0;
			}
			sourceAnalyses.add(new SourceAnalysis(
				source, cohortRequirements));
		}
		List<MutableRequirement> orderedRequirements =
			new ArrayList<MutableRequirement>(requirements.values());
		Collections.sort(orderedRequirements,
			new Comparator<MutableRequirement>() {
				@Override
				public int compare(
					final MutableRequirement left,
					final MutableRequirement right) {
					int x = Integer.compare(
						left.packedRegionX, right.packedRegionX);
					return x != 0 ? x : Integer.compare(
						left.packedRegionY, right.packedRegionY);
				}
			});
		List<RequirementAnalysis> requirementAnalyses =
			new ArrayList<RequirementAnalysis>(orderedRequirements.size());
		for (MutableRequirement requirement : orderedRequirements) {
			requirementAnalyses.add(new RequirementAnalysis(
				requirement,
				cohort.containsKey(Long.valueOf(packedSourceKey(
					requirement.packedRegionX,
					requirement.packedRegionY)))));
		}
		return new LayeredPackedRegionAuthoredReconstructionCohortAnalysis(
			recipe, safety, sourceAnalyses, requirementAnalyses);
	}

	public long getGeneration() { return generation; }
	public long getSafetyObservedAtTick() { return safetyObservedAtTick; }
	public int getSeedSourceCount() { return seedSourceCount; }
	public List<SourceAnalysis> getSources() { return sources; }
	public int getCohortSourceCount() { return sources.size(); }
	public int getExpandedAuthoredSourceCount() {
		return sources.size() - seedSourceCount;
	}
	public int getAuthoredContentSourceCount() {
		return authoredContentSourceCount;
	}
	public int getReconstructionPlacementCount() {
		return reconstructionPlacementCount;
	}
	public int getCrossSourcePlacementCount() {
		return crossSourcePlacementCount;
	}
	public int getAffectedSourceReferenceCount() {
		return affectedSourceReferenceCount;
	}
	public List<RequirementAnalysis> getRequirements() {
		return requirements;
	}
	public int getRequirementSourceCount() { return requirements.size(); }
	public int getCohortRequirementSourceCount() {
		return cohortRequirementSourceCount;
	}
	public int getExternalSupportRequirementSourceCount() {
		return externalSupportRequirementSourceCount;
	}
	public int getMaximumExpansionRound() { return maximumExpansionRound; }
	public boolean isAuthoredClosureComplete() { return true; }
	public boolean isFullySelfContained() {
		return externalSupportRequirementSourceCount == 0;
	}

	public enum CohortRole {
		SEED,
		EXPANDED_AUTHORED
	}

	/** One seed or recursively required final-live authored source. */
	public static final class SourceAnalysis {
		private final int packedRegionX;
		private final int packedRegionY;
		private final CohortRole role;
		private final int expansionRound;
		private final boolean recipeSourcePresent;
		private final int reconstructionPlacementCount;
		private final int crossSourcePlacementCount;
		private final int affectedSourceReferenceCount;
		private final int requirementSourceCount;
		private final int cohortRequirementSourceCount;
		private final int externalSupportRequirementSourceCount;

		private SourceAnalysis(
			final MutableSource source,
			final int cohortRequirementSourceCount) {
			this.packedRegionX = source.packedRegionX;
			this.packedRegionY = source.packedRegionY;
			this.role = source.role;
			this.expansionRound = source.expansionRound;
			this.recipeSourcePresent = source.recipe != null;
			this.reconstructionPlacementCount = source.recipe == null ? 0
				: source.recipe.getReconstructionPlacementCount();
			this.crossSourcePlacementCount = source.recipe == null ? 0
				: source.recipe.getCrossSourcePlacementCount();
			this.affectedSourceReferenceCount = source.recipe == null ? 0
				: source.recipe.getAffectedSourceReferenceCount();
			this.requirementSourceCount = source.requirements.size();
			this.cohortRequirementSourceCount = cohortRequirementSourceCount;
			this.externalSupportRequirementSourceCount =
				requirementSourceCount - cohortRequirementSourceCount;
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public CohortRole getRole() { return role; }
		public int getExpansionRound() { return expansionRound; }
		public boolean isRecipeSourcePresent() {
			return recipeSourcePresent;
		}
		public boolean hasAuthoredContent() {
			return reconstructionPlacementCount > 0;
		}
		public int getReconstructionPlacementCount() {
			return reconstructionPlacementCount;
		}
		public int getCrossSourcePlacementCount() {
			return crossSourcePlacementCount;
		}
		public int getAffectedSourceReferenceCount() {
			return affectedSourceReferenceCount;
		}
		public int getRequirementSourceCount() {
			return requirementSourceCount;
		}
		public int getCohortRequirementSourceCount() {
			return cohortRequirementSourceCount;
		}
		public int getExternalSupportRequirementSourceCount() {
			return externalSupportRequirementSourceCount;
		}
		public boolean isDependencySelfContained() {
			return externalSupportRequirementSourceCount == 0;
		}
	}

	/** One unique dependency coordinate required by the expanded cohort. */
	public static final class RequirementAnalysis {
		private final int packedRegionX;
		private final int packedRegionY;
		private final boolean cohortSource;
		private final boolean recipeSourcePresent;
		private final boolean authoredContentPresent;
		private final int ownerSourceCount;
		private final int placementReferenceCount;

		private RequirementAnalysis(
			final MutableRequirement requirement,
			final boolean cohortSource) {
			this.packedRegionX = requirement.packedRegionX;
			this.packedRegionY = requirement.packedRegionY;
			this.cohortSource = cohortSource;
			this.recipeSourcePresent = requirement.recipe != null;
			this.authoredContentPresent =
				LayeredPackedRegionAuthoredReconstructionCohortAnalysis
					.hasAuthoredContent(requirement.recipe);
			this.ownerSourceCount = requirement.ownerSourceCount;
			this.placementReferenceCount =
				requirement.placementReferenceCount;
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public boolean isCohortSource() { return cohortSource; }
		public boolean isRecipeSourcePresent() {
			return recipeSourcePresent;
		}
		public boolean hasAuthoredContent() {
			return authoredContentPresent;
		}
		public boolean isExternalSupportRequired() {
			return !cohortSource;
		}
		public int getOwnerSourceCount() { return ownerSourceCount; }
		public int getPlacementReferenceCount() {
			return placementReferenceCount;
		}
	}

	private static boolean hasAuthoredContent(
		final LayeredPackedRegionAuthoredReconstructionRecipe.PackedSourceRecipe
			recipe) {
		return recipe != null && recipe.getReconstructionPlacementCount() > 0;
	}

	private static long packedSourceKey(
		final int packedRegionX,
		final int packedRegionY) {
		return ((long) packedRegionX << 32)
			^ (packedRegionY & 0xFFFFFFFFL);
	}

	private static final class MutableSource {
		private final int packedRegionX;
		private final int packedRegionY;
		private final CohortRole role;
		private final int expansionRound;
		private final LayeredPackedRegionAuthoredReconstructionRecipe
			.PackedSourceRecipe recipe;
		private final Set<Long> requirements = new LinkedHashSet<Long>();

		private MutableSource(
			final int packedRegionX,
			final int packedRegionY,
			final CohortRole role,
			final int expansionRound,
			final LayeredPackedRegionAuthoredReconstructionRecipe
				.PackedSourceRecipe recipe) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.role = role;
			this.expansionRound = expansionRound;
			this.recipe = recipe;
		}

		private boolean hasAuthoredContent() {
			return LayeredPackedRegionAuthoredReconstructionCohortAnalysis
				.hasAuthoredContent(recipe);
		}
	}

	private static final class MutableRequirement {
		private final int packedRegionX;
		private final int packedRegionY;
		private final LayeredPackedRegionAuthoredReconstructionRecipe
			.PackedSourceRecipe recipe;
		private int ownerSourceCount;
		private int placementReferenceCount;

		private MutableRequirement(
			final int packedRegionX,
			final int packedRegionY,
			final LayeredPackedRegionAuthoredReconstructionRecipe
				.PackedSourceRecipe recipe) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.recipe = recipe;
		}
	}
}
