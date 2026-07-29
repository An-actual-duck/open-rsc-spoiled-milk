package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredPlacementDependencyInventory.DependencyKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded attribution of the exact detached dependencies in one authored
 * reconstruction cohort.
 *
 * <p>Every owner-to-requirement edge is aggregated by construction and
 * dependency kind. Cross-source placements additionally retain primitive
 * identity and envelope metadata so expansion-frontier and external-support
 * bridges can be explained without retaining a placement definition.</p>
 *
 * <p>This value is diagnostic evidence only. It retains no entity, Region,
 * tile, archive, event, registry, cache, callback, claim, permit, lease,
 * transaction, commit, load, teardown, reconstruction, or rollback authority.
 * Attribution never changes the conservative dependency envelope.</p>
 */
public final class
	LayeredPackedRegionAuthoredReconstructionCohortAttribution {
	private final long generation;
	private final long safetyObservedAtTick;
	private final List<KindAttribution> kinds;
	private final List<EdgeAttribution> edges;
	private final List<BridgePlacementAttribution> bridgePlacements;
	private final int placementCount;
	private final int crossSourcePlacementCount;
	private final int affectedSourceReferenceCount;
	private final int crossSourceReferenceCount;
	private final int expansionFrontierReferenceCount;
	private final int externalSupportReferenceCount;
	private final int selfEdgeCount;
	private final int expansionFrontierEdgeCount;
	private final int externalSupportEdgeCount;

	private LayeredPackedRegionAuthoredReconstructionCohortAttribution(
		final LayeredPackedRegionAuthoredReconstructionCohortAnalysis cohort,
		final List<KindAttribution> kinds,
		final List<EdgeAttribution> edges,
		final List<BridgePlacementAttribution> bridgePlacements) {
		this.generation = cohort.getGeneration();
		this.safetyObservedAtTick = cohort.getSafetyObservedAtTick();
		this.kinds = Collections.unmodifiableList(
			new ArrayList<KindAttribution>(kinds));
		this.edges = Collections.unmodifiableList(
			new ArrayList<EdgeAttribution>(edges));
		this.bridgePlacements = Collections.unmodifiableList(
			new ArrayList<BridgePlacementAttribution>(bridgePlacements));
		int placements = 0;
		int crossSourcePlacements = 0;
		int references = 0;
		int crossSourceReferences = 0;
		int frontierReferences = 0;
		int supportReferences = 0;
		for (KindAttribution kind : kinds) {
			placements = Math.addExact(
				placements, kind.getPlacementCount());
			crossSourcePlacements = Math.addExact(
				crossSourcePlacements, kind.getCrossSourcePlacementCount());
			references = Math.addExact(
				references, kind.getAffectedSourceReferenceCount());
			crossSourceReferences = Math.addExact(
				crossSourceReferences, kind.getCrossSourceReferenceCount());
			frontierReferences = Math.addExact(
				frontierReferences,
				kind.getExpansionFrontierReferenceCount());
			supportReferences = Math.addExact(
				supportReferences, kind.getExternalSupportReferenceCount());
		}
		int edgeReferences = 0;
		int selfEdges = 0;
		int frontierEdges = 0;
		int supportEdges = 0;
		for (EdgeAttribution edge : edges) {
			edgeReferences = Math.addExact(
				edgeReferences, edge.getPlacementReferenceCount());
			selfEdges += edge.isSelfReference() ? 1 : 0;
			frontierEdges += edge.isExpansionFrontier() ? 1 : 0;
			supportEdges += edge.isExternalSupportRequired() ? 1 : 0;
		}
		if (placements != cohort.getReconstructionPlacementCount()
			|| crossSourcePlacements != cohort.getCrossSourcePlacementCount()
			|| references != cohort.getAffectedSourceReferenceCount()
			|| edgeReferences != references
			|| bridgePlacements.size() != crossSourcePlacements) {
			throw new IllegalArgumentException(
				"Cohort attribution arithmetic differs from its source cohort");
		}
		this.placementCount = placements;
		this.crossSourcePlacementCount = crossSourcePlacements;
		this.affectedSourceReferenceCount = references;
		this.crossSourceReferenceCount = crossSourceReferences;
		this.expansionFrontierReferenceCount = frontierReferences;
		this.externalSupportReferenceCount = supportReferences;
		this.selfEdgeCount = selfEdges;
		this.expansionFrontierEdgeCount = frontierEdges;
		this.externalSupportEdgeCount = supportEdges;
	}

	/**
	 * Attributes one completed cohort or refuses before returning a partial
	 * edge or bridge-placement set.
	 */
	public static
		LayeredPackedRegionAuthoredReconstructionCohortAttribution analyze(
			final LayeredPackedRegionAuthoredReconstructionRecipe recipe,
			final LayeredPackedRegionAuthoredReconstructionCohortAnalysis cohort,
			final int maximumEdges,
			final int maximumBridgePlacements) {
		if (recipe == null) {
			throw new NullPointerException("recipe");
		}
		if (cohort == null) {
			throw new NullPointerException("cohort");
		}
		if (recipe.getGeneration() != cohort.getGeneration()) {
			throw new IllegalArgumentException(
				"Attribution recipe and cohort generations differ");
		}
		validateBudget(maximumEdges, "edge");
		validateBudget(maximumBridgePlacements, "bridge-placement");

		Map<Long, LayeredPackedRegionAuthoredReconstructionCohortAnalysis
			.SourceAnalysis> sources =
				new LinkedHashMap<Long,
					LayeredPackedRegionAuthoredReconstructionCohortAnalysis
						.SourceAnalysis>();
		for (LayeredPackedRegionAuthoredReconstructionCohortAnalysis
			.SourceAnalysis source : cohort.getSources()) {
			Long key = Long.valueOf(packedSourceKey(
				source.getPackedRegionX(), source.getPackedRegionY()));
			if (sources.put(key, source) != null) {
				throw new IllegalArgumentException(
					"Cohort attribution contains a duplicate source");
			}
		}
		Map<Long, LayeredPackedRegionAuthoredReconstructionCohortAnalysis
			.RequirementAnalysis> requirements =
				new LinkedHashMap<Long,
					LayeredPackedRegionAuthoredReconstructionCohortAnalysis
						.RequirementAnalysis>();
		Map<Long, MutableRequirementCheck> requirementChecks =
			new LinkedHashMap<Long, MutableRequirementCheck>();
		for (LayeredPackedRegionAuthoredReconstructionCohortAnalysis
			.RequirementAnalysis requirement : cohort.getRequirements()) {
			Long key = Long.valueOf(packedSourceKey(
				requirement.getPackedRegionX(),
				requirement.getPackedRegionY()));
			if (requirements.put(key, requirement) != null) {
				throw new IllegalArgumentException(
					"Cohort attribution contains a duplicate requirement");
			}
			if (requirement.isCohortSource() != sources.containsKey(key)) {
				throw new IllegalArgumentException(
					"Cohort requirement membership is inconsistent");
			}
			requirementChecks.put(key, new MutableRequirementCheck());
		}

		MutableKindAttribution[][] mutableKinds =
			new MutableKindAttribution[ConstructionKind.values().length]
				[DependencyKind.values().length];
		Map<EdgeKey, MutableEdgeAttribution> mutableEdges =
			new LinkedHashMap<EdgeKey, MutableEdgeAttribution>();
		List<BridgePlacementAttribution> bridgePlacements =
			new ArrayList<BridgePlacementAttribution>();

		for (LayeredPackedRegionAuthoredReconstructionCohortAnalysis
			.SourceAnalysis source : cohort.getSources()) {
			LayeredPackedRegionAuthoredReconstructionRecipe.PackedSourceRecipe
				sourceRecipe = recipe.findSource(
					source.getPackedRegionX(), source.getPackedRegionY());
			validateSource(source, sourceRecipe);
			if (!source.hasAuthoredContent()) {
				continue;
			}
			Set<Long> directRequirements = new LinkedHashSet<Long>();
			int directCohortRequirements = 0;
			int directExternalRequirements = 0;
			for (LayeredPackedRegionAuthoredReconstructionRecipe
				.ReconstructionPlacement placement
					: sourceRecipe.getPlacements()) {
				LayeredPackedRegionAuthoredPlacementDependencyInventory
					.PlacementDependency dependency = placement.getDependency();
				MutableKindAttribution kind = mutableKinds[
					placement.getKind().ordinal()][
						dependency.getDependencyKind().ordinal()];
				if (kind == null) {
					kind = new MutableKindAttribution(
						placement.getKind(), dependency.getDependencyKind());
					mutableKinds[placement.getKind().ordinal()][
						dependency.getDependencyKind().ordinal()] = kind;
				}
				kind.placementCount = Math.incrementExact(kind.placementCount);
				MutableBridgePlacement bridge = null;
				if (placement.isCrossSource()) {
					kind.crossSourcePlacementCount = Math.incrementExact(
						kind.crossSourcePlacementCount);
					if (bridgePlacements.size() >= maximumBridgePlacements) {
						throw new IllegalArgumentException(
							"Cohort attribution exceeds its bridge-placement budget");
					}
					bridge = new MutableBridgePlacement(source, placement);
				}
				for (int x = dependency.getMinimumPackedRegionX();
					x <= dependency.getMaximumPackedRegionX(); x++) {
					for (int y = dependency.getMinimumPackedRegionY();
						y <= dependency.getMaximumPackedRegionY(); y++) {
						Long targetKey = Long.valueOf(packedSourceKey(x, y));
						LayeredPackedRegionAuthoredReconstructionCohortAnalysis
							.RequirementAnalysis requirement =
								requirements.get(targetKey);
						if (requirement == null) {
							throw new IllegalArgumentException(
								"Recipe dependency is absent from cohort requirements");
						}
						boolean firstDirect = directRequirements.add(targetKey);
						if (firstDirect && requirement.isCohortSource()) {
							directCohortRequirements = Math.incrementExact(
								directCohortRequirements);
						} else if (firstDirect) {
							directExternalRequirements = Math.incrementExact(
								directExternalRequirements);
						}
						LayeredPackedRegionAuthoredReconstructionCohortAnalysis
							.SourceAnalysis target = sources.get(targetKey);
						boolean frontier = isExpansionFrontier(source, target);
						boolean self = source.getPackedRegionX() == x
							&& source.getPackedRegionY() == y;
						EdgeKey edgeKey = new EdgeKey(
							source.getPackedRegionX(), source.getPackedRegionY(),
							x, y);
						MutableEdgeAttribution edge = mutableEdges.get(edgeKey);
						if (edge == null) {
							if (mutableEdges.size() >= maximumEdges) {
								throw new IllegalArgumentException(
									"Cohort attribution exceeds its edge budget");
							}
							edge = new MutableEdgeAttribution(
								source, target, requirement, self, frontier);
							mutableEdges.put(edgeKey, edge);
						}
						edge.record(placement.getKind(),
							dependency.getDependencyKind());
						kind.affectedSourceReferenceCount = Math.incrementExact(
							kind.affectedSourceReferenceCount);
						if (!self) {
							kind.crossSourceReferenceCount = Math.incrementExact(
								kind.crossSourceReferenceCount);
						}
						if (frontier) {
							kind.expansionFrontierReferenceCount = Math.incrementExact(
								kind.expansionFrontierReferenceCount);
						}
						if (requirement.isExternalSupportRequired()) {
							kind.externalSupportReferenceCount = Math.incrementExact(
								kind.externalSupportReferenceCount);
						}
						MutableRequirementCheck check =
							requirementChecks.get(targetKey);
						check.placementReferenceCount = Math.incrementExact(
							check.placementReferenceCount);
						check.ownerSources.add(Long.valueOf(packedSourceKey(
							source.getPackedRegionX(),
							source.getPackedRegionY())));
						if (bridge != null) {
							bridge.record(requirement, frontier);
						}
					}
				}
				if (bridge != null) {
					bridgePlacements.add(bridge.freeze());
				}
			}
			if (directRequirements.size() != source.getRequirementSourceCount()
				|| directCohortRequirements
					!= source.getCohortRequirementSourceCount()
				|| directExternalRequirements
					!= source.getExternalSupportRequirementSourceCount()) {
				throw new IllegalArgumentException(
					"Source attribution differs from cohort requirements");
			}
		}

		for (Map.Entry<Long,
			LayeredPackedRegionAuthoredReconstructionCohortAnalysis
				.RequirementAnalysis> entry : requirements.entrySet()) {
			MutableRequirementCheck check = requirementChecks.get(entry.getKey());
			LayeredPackedRegionAuthoredReconstructionCohortAnalysis
				.RequirementAnalysis requirement = entry.getValue();
			if (check.ownerSources.size() != requirement.getOwnerSourceCount()
				|| check.placementReferenceCount
					!= requirement.getPlacementReferenceCount()) {
				throw new IllegalArgumentException(
					"Requirement attribution differs from cohort arithmetic");
			}
		}

		List<KindAttribution> kinds = new ArrayList<KindAttribution>();
		for (ConstructionKind constructionKind : ConstructionKind.values()) {
			for (DependencyKind dependencyKind : DependencyKind.values()) {
				MutableKindAttribution kind = mutableKinds[
					constructionKind.ordinal()][dependencyKind.ordinal()];
				if (kind != null) {
					kinds.add(kind.freeze());
				}
			}
		}
		List<MutableEdgeAttribution> orderedEdges =
			new ArrayList<MutableEdgeAttribution>(mutableEdges.values());
		Collections.sort(orderedEdges,
			new Comparator<MutableEdgeAttribution>() {
				@Override
				public int compare(
					final MutableEdgeAttribution left,
					final MutableEdgeAttribution right) {
					return left.key.compareTo(right.key);
				}
			});
		List<EdgeAttribution> edges =
			new ArrayList<EdgeAttribution>(orderedEdges.size());
		for (MutableEdgeAttribution edge : orderedEdges) {
			edges.add(edge.freeze());
		}
		return new
			LayeredPackedRegionAuthoredReconstructionCohortAttribution(
				cohort, kinds, edges, bridgePlacements);
	}

	public long getGeneration() { return generation; }
	public long getSafetyObservedAtTick() { return safetyObservedAtTick; }
	public List<KindAttribution> getKinds() { return kinds; }
	public int getKindCount() { return kinds.size(); }
	public List<EdgeAttribution> getEdges() { return edges; }
	public int getEdgeCount() { return edges.size(); }
	public List<BridgePlacementAttribution> getBridgePlacements() {
		return bridgePlacements;
	}
	public int getBridgePlacementCount() { return bridgePlacements.size(); }
	public int getPlacementCount() { return placementCount; }
	public int getCrossSourcePlacementCount() {
		return crossSourcePlacementCount;
	}
	public int getAffectedSourceReferenceCount() {
		return affectedSourceReferenceCount;
	}
	public int getCrossSourceReferenceCount() {
		return crossSourceReferenceCount;
	}
	public int getExpansionFrontierReferenceCount() {
		return expansionFrontierReferenceCount;
	}
	public int getExternalSupportReferenceCount() {
		return externalSupportReferenceCount;
	}
	public int getSelfEdgeCount() { return selfEdgeCount; }
	public int getExpansionFrontierEdgeCount() {
		return expansionFrontierEdgeCount;
	}
	public int getExternalSupportEdgeCount() {
		return externalSupportEdgeCount;
	}
	public boolean isIdentityMetadataOnly() { return true; }
	public boolean isEntityRegistry() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	/** Aggregate for one construction/dependency-kind pair. */
	public static final class KindAttribution {
		private final ConstructionKind constructionKind;
		private final DependencyKind dependencyKind;
		private final int placementCount;
		private final int crossSourcePlacementCount;
		private final int affectedSourceReferenceCount;
		private final int crossSourceReferenceCount;
		private final int expansionFrontierReferenceCount;
		private final int externalSupportReferenceCount;

		private KindAttribution(final MutableKindAttribution source) {
			this.constructionKind = source.constructionKind;
			this.dependencyKind = source.dependencyKind;
			this.placementCount = source.placementCount;
			this.crossSourcePlacementCount = source.crossSourcePlacementCount;
			this.affectedSourceReferenceCount =
				source.affectedSourceReferenceCount;
			this.crossSourceReferenceCount = source.crossSourceReferenceCount;
			this.expansionFrontierReferenceCount =
				source.expansionFrontierReferenceCount;
			this.externalSupportReferenceCount =
				source.externalSupportReferenceCount;
		}

		public ConstructionKind getConstructionKind() {
			return constructionKind;
		}
		public DependencyKind getDependencyKind() { return dependencyKind; }
		public int getPlacementCount() { return placementCount; }
		public int getCrossSourcePlacementCount() {
			return crossSourcePlacementCount;
		}
		public int getAffectedSourceReferenceCount() {
			return affectedSourceReferenceCount;
		}
		public int getCrossSourceReferenceCount() {
			return crossSourceReferenceCount;
		}
		public int getExpansionFrontierReferenceCount() {
			return expansionFrontierReferenceCount;
		}
		public int getExternalSupportReferenceCount() {
			return externalSupportReferenceCount;
		}
	}

	/** Exact aggregate for one owner-to-requirement packed-source edge. */
	public static final class EdgeAttribution {
		private final int ownerPackedRegionX;
		private final int ownerPackedRegionY;
		private final int ownerExpansionRound;
		private final int requiredPackedRegionX;
		private final int requiredPackedRegionY;
		private final int requiredExpansionRound;
		private final boolean selfReference;
		private final boolean cohortSource;
		private final boolean expansionFrontier;
		private final boolean externalSupportRequired;
		private final int placementReferenceCount;
		private final int[] constructionKindReferenceCounts;
		private final int[] dependencyKindReferenceCounts;
		private final List<TypedReferenceAttribution>
			constructionKindReferences;
		private final List<TypedReferenceAttribution>
			dependencyKindReferences;

		private EdgeAttribution(final MutableEdgeAttribution source) {
			this.ownerPackedRegionX = source.key.ownerPackedRegionX;
			this.ownerPackedRegionY = source.key.ownerPackedRegionY;
			this.ownerExpansionRound = source.ownerExpansionRound;
			this.requiredPackedRegionX = source.key.requiredPackedRegionX;
			this.requiredPackedRegionY = source.key.requiredPackedRegionY;
			this.requiredExpansionRound = source.requiredExpansionRound;
			this.selfReference = source.selfReference;
			this.cohortSource = source.cohortSource;
			this.expansionFrontier = source.expansionFrontier;
			this.externalSupportRequired = source.externalSupportRequired;
			this.placementReferenceCount = source.placementReferenceCount;
			this.constructionKindReferenceCounts =
				source.constructionKindReferenceCounts.clone();
			this.dependencyKindReferenceCounts =
				source.dependencyKindReferenceCounts.clone();
			List<TypedReferenceAttribution> constructionReferences =
				new ArrayList<TypedReferenceAttribution>();
			for (ConstructionKind kind : ConstructionKind.values()) {
				int count = constructionKindReferenceCounts[kind.ordinal()];
				if (count > 0) {
					constructionReferences.add(
						new TypedReferenceAttribution(kind.name(), count));
				}
			}
			this.constructionKindReferences = Collections.unmodifiableList(
				constructionReferences);
			List<TypedReferenceAttribution> dependencyReferences =
				new ArrayList<TypedReferenceAttribution>();
			for (DependencyKind kind : DependencyKind.values()) {
				int count = dependencyKindReferenceCounts[kind.ordinal()];
				if (count > 0) {
					dependencyReferences.add(
						new TypedReferenceAttribution(kind.name(), count));
				}
			}
			this.dependencyKindReferences = Collections.unmodifiableList(
				dependencyReferences);
		}

		public int getOwnerPackedRegionX() { return ownerPackedRegionX; }
		public int getOwnerPackedRegionY() { return ownerPackedRegionY; }
		public int getOwnerExpansionRound() { return ownerExpansionRound; }
		public int getRequiredPackedRegionX() {
			return requiredPackedRegionX;
		}
		public int getRequiredPackedRegionY() {
			return requiredPackedRegionY;
		}
		public int getRequiredExpansionRound() {
			return requiredExpansionRound;
		}
		public boolean isSelfReference() { return selfReference; }
		public boolean isCohortSource() { return cohortSource; }
		public boolean isExpansionFrontier() { return expansionFrontier; }
		public boolean isExternalSupportRequired() {
			return externalSupportRequired;
		}
		public int getPlacementReferenceCount() {
			return placementReferenceCount;
		}
		public int getReferenceCount(final ConstructionKind kind) {
			if (kind == null) { throw new NullPointerException("kind"); }
			return constructionKindReferenceCounts[kind.ordinal()];
		}
		public int getReferenceCount(final DependencyKind kind) {
			if (kind == null) { throw new NullPointerException("kind"); }
			return dependencyKindReferenceCounts[kind.ordinal()];
		}
		public List<TypedReferenceAttribution>
			getConstructionKindReferences() {
			return constructionKindReferences;
		}
		public List<TypedReferenceAttribution>
			getDependencyKindReferences() {
			return dependencyKindReferences;
		}
	}

	/** One nonzero enum-name/reference-count pair detached for serialization. */
	public static final class TypedReferenceAttribution {
		private final String kindName;
		private final int referenceCount;

		private TypedReferenceAttribution(
			final String kindName,
			final int referenceCount) {
			this.kindName = kindName;
			this.referenceCount = referenceCount;
		}

		public String getKindName() { return kindName; }
		public int getReferenceCount() { return referenceCount; }
	}

	/** Primitive metadata for one final-live cross-source placement. */
	public static final class BridgePlacementAttribution {
		private final long identityGeneration;
		private final int ownerPackedRegionX;
		private final int ownerPackedRegionY;
		private final int ownerExpansionRound;
		private final int sourceOrdinal;
		private final ConstructionKind constructionKind;
		private final DependencyKind dependencyKind;
		private final int authoredDefinitionId;
		private final int constructedEntityId;
		private final int minimumPackedRegionX;
		private final int maximumPackedRegionX;
		private final int minimumPackedRegionY;
		private final int maximumPackedRegionY;
		private final int affectedSourceCount;
		private final int cohortRequirementSourceCount;
		private final int expansionFrontierSourceCount;
		private final int externalSupportRequirementSourceCount;

		private BridgePlacementAttribution(
			final MutableBridgePlacement source) {
			LayeredAuthoredPlacementIdentity identity =
				source.placement.getIdentity();
			LayeredPackedRegionAuthoredPlacementDependencyInventory
				.PlacementDependency dependency =
					source.placement.getDependency();
			this.identityGeneration = identity.getGeneration();
			this.ownerPackedRegionX = source.owner.getPackedRegionX();
			this.ownerPackedRegionY = source.owner.getPackedRegionY();
			this.ownerExpansionRound = source.owner.getExpansionRound();
			this.sourceOrdinal = source.placement.getSourceOrdinal();
			this.constructionKind = source.placement.getKind();
			this.dependencyKind = dependency.getDependencyKind();
			this.authoredDefinitionId = source.placement.getPlacement()
				.getAuthoredDefinitionId();
			this.constructedEntityId = source.placement.getPlacement()
				.getConstructedEntityId();
			this.minimumPackedRegionX = dependency.getMinimumPackedRegionX();
			this.maximumPackedRegionX = dependency.getMaximumPackedRegionX();
			this.minimumPackedRegionY = dependency.getMinimumPackedRegionY();
			this.maximumPackedRegionY = dependency.getMaximumPackedRegionY();
			this.affectedSourceCount = dependency.getAffectedSourceCount();
			this.cohortRequirementSourceCount =
				source.cohortRequirementSourceCount;
			this.expansionFrontierSourceCount =
				source.expansionFrontierSourceCount;
			this.externalSupportRequirementSourceCount =
				source.externalSupportRequirementSourceCount;
		}

		public long getIdentityGeneration() { return identityGeneration; }
		public int getOwnerPackedRegionX() { return ownerPackedRegionX; }
		public int getOwnerPackedRegionY() { return ownerPackedRegionY; }
		public int getOwnerExpansionRound() { return ownerExpansionRound; }
		public int getSourceOrdinal() { return sourceOrdinal; }
		public ConstructionKind getConstructionKind() {
			return constructionKind;
		}
		public DependencyKind getDependencyKind() { return dependencyKind; }
		public int getAuthoredDefinitionId() { return authoredDefinitionId; }
		public int getConstructedEntityId() { return constructedEntityId; }
		public int getMinimumPackedRegionX() { return minimumPackedRegionX; }
		public int getMaximumPackedRegionX() { return maximumPackedRegionX; }
		public int getMinimumPackedRegionY() { return minimumPackedRegionY; }
		public int getMaximumPackedRegionY() { return maximumPackedRegionY; }
		public int getAffectedSourceCount() { return affectedSourceCount; }
		public int getCohortRequirementSourceCount() {
			return cohortRequirementSourceCount;
		}
		public int getExpansionFrontierSourceCount() {
			return expansionFrontierSourceCount;
		}
		public int getExternalSupportRequirementSourceCount() {
			return externalSupportRequirementSourceCount;
		}
	}

	private static void validateSource(
		final LayeredPackedRegionAuthoredReconstructionCohortAnalysis
			.SourceAnalysis source,
		final LayeredPackedRegionAuthoredReconstructionRecipe.PackedSourceRecipe
			recipe) {
		if (source.isRecipeSourcePresent() != (recipe != null)
			|| source.getReconstructionPlacementCount()
				!= (recipe == null ? 0 : recipe.getReconstructionPlacementCount())
			|| source.getCrossSourcePlacementCount()
				!= (recipe == null ? 0 : recipe.getCrossSourcePlacementCount())
			|| source.getAffectedSourceReferenceCount()
				!= (recipe == null ? 0
					: recipe.getAffectedSourceReferenceCount())) {
			throw new IllegalArgumentException(
				"Attribution recipe differs from cohort source arithmetic");
		}
	}

	private static boolean isExpansionFrontier(
		final LayeredPackedRegionAuthoredReconstructionCohortAnalysis
			.SourceAnalysis owner,
		final LayeredPackedRegionAuthoredReconstructionCohortAnalysis
			.SourceAnalysis required) {
		return required != null
			&& required.getRole()
				== LayeredPackedRegionAuthoredReconstructionCohortAnalysis
					.CohortRole.EXPANDED_AUTHORED
			&& required.getExpansionRound()
				== Math.incrementExact(owner.getExpansionRound());
	}

	private static void validateBudget(
		final int budget,
		final String label) {
		if (budget < 0
			|| budget
				> LayeredPackedRegionAuthoredReconstructionRecipe
					.MAXIMUM_AUTHORED_PLACEMENTS) {
			throw new IllegalArgumentException(
				"Invalid cohort attribution " + label + " budget");
		}
	}

	private static long packedSourceKey(
		final int packedRegionX,
		final int packedRegionY) {
		return ((long) packedRegionX << 32)
			^ (packedRegionY & 0xFFFFFFFFL);
	}

	private static final class MutableKindAttribution {
		private final ConstructionKind constructionKind;
		private final DependencyKind dependencyKind;
		private int placementCount;
		private int crossSourcePlacementCount;
		private int affectedSourceReferenceCount;
		private int crossSourceReferenceCount;
		private int expansionFrontierReferenceCount;
		private int externalSupportReferenceCount;

		private MutableKindAttribution(
			final ConstructionKind constructionKind,
			final DependencyKind dependencyKind) {
			this.constructionKind = constructionKind;
			this.dependencyKind = dependencyKind;
		}

		private KindAttribution freeze() {
			return new KindAttribution(this);
		}
	}

	private static final class MutableEdgeAttribution {
		private final EdgeKey key;
		private final int ownerExpansionRound;
		private final int requiredExpansionRound;
		private final boolean selfReference;
		private final boolean cohortSource;
		private final boolean expansionFrontier;
		private final boolean externalSupportRequired;
		private int placementReferenceCount;
		private final int[] constructionKindReferenceCounts =
			new int[ConstructionKind.values().length];
		private final int[] dependencyKindReferenceCounts =
			new int[DependencyKind.values().length];

		private MutableEdgeAttribution(
			final LayeredPackedRegionAuthoredReconstructionCohortAnalysis
				.SourceAnalysis owner,
			final LayeredPackedRegionAuthoredReconstructionCohortAnalysis
				.SourceAnalysis required,
			final LayeredPackedRegionAuthoredReconstructionCohortAnalysis
				.RequirementAnalysis requirement,
			final boolean selfReference,
			final boolean expansionFrontier) {
			this.key = new EdgeKey(
				owner.getPackedRegionX(), owner.getPackedRegionY(),
				requirement.getPackedRegionX(),
				requirement.getPackedRegionY());
			this.ownerExpansionRound = owner.getExpansionRound();
			this.requiredExpansionRound =
				required == null ? -1 : required.getExpansionRound();
			this.selfReference = selfReference;
			this.cohortSource = requirement.isCohortSource();
			this.expansionFrontier = expansionFrontier;
			this.externalSupportRequired =
				requirement.isExternalSupportRequired();
		}

		private void record(
			final ConstructionKind constructionKind,
			final DependencyKind dependencyKind) {
			placementReferenceCount = Math.incrementExact(
				placementReferenceCount);
			int constructionIndex = constructionKind.ordinal();
			constructionKindReferenceCounts[constructionIndex] =
				Math.incrementExact(
					constructionKindReferenceCounts[constructionIndex]);
			int dependencyIndex = dependencyKind.ordinal();
			dependencyKindReferenceCounts[dependencyIndex] =
				Math.incrementExact(
					dependencyKindReferenceCounts[dependencyIndex]);
		}

		private EdgeAttribution freeze() {
			return new EdgeAttribution(this);
		}
	}

	private static final class MutableBridgePlacement {
		private final LayeredPackedRegionAuthoredReconstructionCohortAnalysis
			.SourceAnalysis owner;
		private final LayeredPackedRegionAuthoredReconstructionRecipe
			.ReconstructionPlacement placement;
		private int cohortRequirementSourceCount;
		private int expansionFrontierSourceCount;
		private int externalSupportRequirementSourceCount;

		private MutableBridgePlacement(
			final LayeredPackedRegionAuthoredReconstructionCohortAnalysis
				.SourceAnalysis owner,
			final LayeredPackedRegionAuthoredReconstructionRecipe
				.ReconstructionPlacement placement) {
			this.owner = owner;
			this.placement = placement;
		}

		private void record(
			final LayeredPackedRegionAuthoredReconstructionCohortAnalysis
				.RequirementAnalysis requirement,
			final boolean expansionFrontier) {
			if (requirement.isCohortSource()) {
				cohortRequirementSourceCount = Math.incrementExact(
					cohortRequirementSourceCount);
			} else {
				externalSupportRequirementSourceCount = Math.incrementExact(
					externalSupportRequirementSourceCount);
			}
			if (expansionFrontier) {
				expansionFrontierSourceCount = Math.incrementExact(
					expansionFrontierSourceCount);
			}
		}

		private BridgePlacementAttribution freeze() {
			return new BridgePlacementAttribution(this);
		}
	}

	private static final class MutableRequirementCheck {
		private final Set<Long> ownerSources = new LinkedHashSet<Long>();
		private int placementReferenceCount;
	}

	private static final class EdgeKey implements Comparable<EdgeKey> {
		private final int ownerPackedRegionX;
		private final int ownerPackedRegionY;
		private final int requiredPackedRegionX;
		private final int requiredPackedRegionY;

		private EdgeKey(
			final int ownerPackedRegionX,
			final int ownerPackedRegionY,
			final int requiredPackedRegionX,
			final int requiredPackedRegionY) {
			this.ownerPackedRegionX = ownerPackedRegionX;
			this.ownerPackedRegionY = ownerPackedRegionY;
			this.requiredPackedRegionX = requiredPackedRegionX;
			this.requiredPackedRegionY = requiredPackedRegionY;
		}

		@Override
		public int compareTo(final EdgeKey other) {
			int value = Integer.compare(
				ownerPackedRegionX, other.ownerPackedRegionX);
			if (value == 0) {
				value = Integer.compare(
					ownerPackedRegionY, other.ownerPackedRegionY);
			}
			if (value == 0) {
				value = Integer.compare(
					requiredPackedRegionX, other.requiredPackedRegionX);
			}
			return value != 0 ? value : Integer.compare(
				requiredPackedRegionY, other.requiredPackedRegionY);
		}

		@Override
		public boolean equals(final Object value) {
			if (this == value) { return true; }
			if (!(value instanceof EdgeKey)) { return false; }
			EdgeKey other = (EdgeKey) value;
			return ownerPackedRegionX == other.ownerPackedRegionX
				&& ownerPackedRegionY == other.ownerPackedRegionY
				&& requiredPackedRegionX == other.requiredPackedRegionX
				&& requiredPackedRegionY == other.requiredPackedRegionY;
		}

		@Override
		public int hashCode() {
			int result = ownerPackedRegionX;
			result = 31 * result + ownerPackedRegionY;
			result = 31 * result + requiredPackedRegionX;
			return 31 * result + requiredPackedRegionY;
		}
	}
}
