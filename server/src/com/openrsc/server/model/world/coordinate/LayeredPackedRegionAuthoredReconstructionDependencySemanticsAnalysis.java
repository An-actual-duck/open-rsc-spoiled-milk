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
 * Separates authored replay ownership from conservative spatial support.
 *
 * <p>Only exact retirement-safety sources own replay in this analysis. Every
 * affected coordinate remains visible as outbound support, even when that
 * coordinate has unrelated authored content. Placements owned outside the
 * selection that can reach inward are reported separately as potential
 * incoming support. They are not imported into the replay set.</p>
 *
 * <p>This value contains potential reach, not active-instance evidence. It is
 * detached diagnostic evidence only and retains no entity, Region, tile,
 * archive, event, registry, cache, callback, claim, permit, lease,
 * transaction, commit, load, teardown, reconstruction, or rollback authority.</p>
 */
public final class
	LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis {
	public static final int MAXIMUM_SOURCES =
		LayeredPackedRegionAuthoredReconstructionRecipe.MAXIMUM_PACKED_SOURCES;
	public static final int MAXIMUM_PLACEMENTS =
		LayeredPackedRegionAuthoredReconstructionRecipe
			.MAXIMUM_AUTHORED_PLACEMENTS;

	private final long generation;
	private final long safetyObservedAtTick;
	private final List<SelectedSource> selectedSources;
	private final List<SupportSource> outboundSupportSources;
	private final List<IncomingOwner> incomingOwners;
	private final List<KindSemantics> kinds;
	private final int selectedAuthoredReplaySourceCount;
	private final int replayPlacementCount;
	private final int externalOutboundSupportSourceCount;
	private final int outboundSupportReferenceCount;
	private final int externalOutboundSupportReferenceCount;
	private final int incomingPlacementCount;
	private final int incomingReferenceCount;

	private
		LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis(
			final LayeredPackedRegionAuthoredReconstructionRecipe recipe,
			final LayeredPackedRegionRetirementSafetyAssessment safety,
			final List<SelectedSource> selectedSources,
			final List<SupportSource> outboundSupportSources,
			final List<IncomingOwner> incomingOwners,
			final List<KindSemantics> kinds) {
		this.generation = recipe.getGeneration();
		this.safetyObservedAtTick = safety.getObservedAtTick();
		this.selectedSources = Collections.unmodifiableList(
			new ArrayList<SelectedSource>(selectedSources));
		this.outboundSupportSources = Collections.unmodifiableList(
			new ArrayList<SupportSource>(outboundSupportSources));
		this.incomingOwners = Collections.unmodifiableList(
			new ArrayList<IncomingOwner>(incomingOwners));
		this.kinds = Collections.unmodifiableList(
			new ArrayList<KindSemantics>(kinds));

		int replaySources = 0;
		int replayPlacements = 0;
		for (SelectedSource source : selectedSources) {
			replaySources += source.hasAuthoredContent() ? 1 : 0;
			replayPlacements = Math.addExact(
				replayPlacements, source.getReplayPlacementCount());
		}
		int externalSupportSources = 0;
		int supportReferences = 0;
		int externalSupportReferences = 0;
		for (SupportSource support : outboundSupportSources) {
			externalSupportSources += support.isSelectedSource() ? 0 : 1;
			supportReferences = Math.addExact(
				supportReferences, support.getPlacementReferenceCount());
			if (!support.isSelectedSource()) {
				externalSupportReferences = Math.addExact(
					externalSupportReferences,
					support.getPlacementReferenceCount());
			}
		}
		int incomingPlacements = 0;
		int incomingReferences = 0;
		for (IncomingOwner owner : incomingOwners) {
			incomingPlacements = Math.addExact(
				incomingPlacements, owner.getIncomingPlacementCount());
			incomingReferences = Math.addExact(
				incomingReferences, owner.getSelectedSourceReferenceCount());
		}
		int kindReplayPlacements = 0;
		int kindSupportReferences = 0;
		int kindExternalSupportReferences = 0;
		int kindIncomingPlacements = 0;
		int kindIncomingReferences = 0;
		for (KindSemantics kind : kinds) {
			kindReplayPlacements = Math.addExact(
				kindReplayPlacements, kind.getReplayPlacementCount());
			kindSupportReferences = Math.addExact(
				kindSupportReferences, kind.getOutboundSupportReferenceCount());
			kindExternalSupportReferences = Math.addExact(
				kindExternalSupportReferences,
				kind.getExternalOutboundSupportReferenceCount());
			kindIncomingPlacements = Math.addExact(
				kindIncomingPlacements, kind.getIncomingPlacementCount());
			kindIncomingReferences = Math.addExact(
				kindIncomingReferences, kind.getIncomingReferenceCount());
		}
		if (replayPlacements != kindReplayPlacements
			|| supportReferences != kindSupportReferences
			|| externalSupportReferences != kindExternalSupportReferences
			|| incomingPlacements != kindIncomingPlacements
			|| incomingReferences != kindIncomingReferences) {
			throw new IllegalArgumentException(
				"Dependency-semantics kind arithmetic differs from its evidence");
		}
		this.selectedAuthoredReplaySourceCount = replaySources;
		this.replayPlacementCount = replayPlacements;
		this.externalOutboundSupportSourceCount = externalSupportSources;
		this.outboundSupportReferenceCount = supportReferences;
		this.externalOutboundSupportReferenceCount =
			externalSupportReferences;
		this.incomingPlacementCount = incomingPlacements;
		this.incomingReferenceCount = incomingReferences;
	}

	/**
	 * Classifies one exact safety selection without recursively adding recipes.
	 * Every bounded collection refuses overflow before a result is returned.
	 */
	public static
		LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
			analyze(
				final LayeredPackedRegionAuthoredReconstructionRecipe recipe,
				final LayeredPackedRegionRetirementSafetyAssessment safety,
				final int maximumSelectedSources,
				final int maximumSupportSources,
				final int maximumIncomingOwners,
				final int maximumIncomingPlacements) {
		if (recipe == null) {
			throw new NullPointerException("recipe");
		}
		if (safety == null) {
			throw new NullPointerException("safety");
		}
		validateSourceBudget(maximumSelectedSources, "selected");
		validateSourceBudget(maximumSupportSources, "support");
		validateSourceBudget(maximumIncomingOwners, "incoming-owner");
		if (maximumIncomingPlacements < 0
			|| maximumIncomingPlacements > MAXIMUM_PLACEMENTS) {
			throw new IllegalArgumentException(
				"Dependency-semantics incoming-placement budget is invalid");
		}
		if (safety.getSourceCount() > maximumSelectedSources) {
			throw new IllegalArgumentException(
				"Dependency semantics exceeds its selected-source budget");
		}

		Map<Long, LayeredPackedRegionAuthoredReconstructionRecipe
			.PackedSourceRecipe> selected =
				new LinkedHashMap<Long,
					LayeredPackedRegionAuthoredReconstructionRecipe
						.PackedSourceRecipe>();
		List<SelectedSource> selectedEvidence =
			new ArrayList<SelectedSource>(safety.getSourceCount());
		for (LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment
			source : safety.getSources()) {
			long key = packedSourceKey(
				source.getPackedRegionX(), source.getPackedRegionY());
			LayeredPackedRegionAuthoredReconstructionRecipe.PackedSourceRecipe
				sourceRecipe = recipe.findSource(
					source.getPackedRegionX(), source.getPackedRegionY());
			if (selected.put(Long.valueOf(key), sourceRecipe) != null
				|| selectedEvidenceContains(
					selectedEvidence, source.getPackedRegionX(),
					source.getPackedRegionY())) {
				throw new IllegalArgumentException(
					"Dependency semantics contains a duplicate selected source");
			}
			selectedEvidence.add(new SelectedSource(
				source.getPackedRegionX(), source.getPackedRegionY(),
				sourceRecipe));
		}

		MutableKindSemantics[][] mutableKinds = new MutableKindSemantics[
			ConstructionKind.values().length][DependencyKind.values().length];
		Map<Long, MutableSupportSource> support =
			new LinkedHashMap<Long, MutableSupportSource>();
		for (Map.Entry<Long, LayeredPackedRegionAuthoredReconstructionRecipe
			.PackedSourceRecipe> entry : selected.entrySet()) {
			LayeredPackedRegionAuthoredReconstructionRecipe.PackedSourceRecipe
				sourceRecipe = entry.getValue();
			if (!hasAuthoredContent(sourceRecipe)) {
				continue;
			}
			for (LayeredPackedRegionAuthoredReconstructionRecipe
				.ReconstructionPlacement placement
					: sourceRecipe.getPlacements()) {
				LayeredPackedRegionAuthoredPlacementDependencyInventory
					.PlacementDependency dependency = placement.getDependency();
				MutableKindSemantics kind = mutableKind(
					mutableKinds, placement.getKind(),
					dependency.getDependencyKind());
				kind.replayPlacementCount = Math.incrementExact(
					kind.replayPlacementCount);
				for (int x = dependency.getMinimumPackedRegionX();
					x <= dependency.getMaximumPackedRegionX(); x++) {
					for (int y = dependency.getMinimumPackedRegionY();
						y <= dependency.getMaximumPackedRegionY(); y++) {
						long targetKey = packedSourceKey(x, y);
						Long boxedTarget = Long.valueOf(targetKey);
						MutableSupportSource target = support.get(boxedTarget);
						if (target == null) {
							if (support.size() >= maximumSupportSources) {
								throw new IllegalArgumentException(
									"Dependency semantics exceeds its support-source budget");
							}
							LayeredPackedRegionAuthoredReconstructionRecipe
								.PackedSourceRecipe targetRecipe =
									recipe.findSource(x, y);
							target = new MutableSupportSource(
								x, y, selected.containsKey(boxedTarget), targetRecipe);
							support.put(boxedTarget, target);
						}
						target.record(entry.getKey().longValue(),
							semanticsFor(dependency.getDependencyKind()));
						kind.outboundSupportReferenceCount = Math.incrementExact(
							kind.outboundSupportReferenceCount);
						if (!selected.containsKey(boxedTarget)) {
							kind.externalOutboundSupportReferenceCount =
								Math.incrementExact(
									kind.externalOutboundSupportReferenceCount);
						}
					}
				}
			}
		}

		Map<Long, MutableIncomingOwner> incoming =
			new LinkedHashMap<Long, MutableIncomingOwner>();
		int incomingPlacements = 0;
		for (LayeredPackedRegionAuthoredReconstructionRecipe.PackedSourceRecipe
			owner : recipe.getSources()) {
			long ownerKey = packedSourceKey(
				owner.getPackedRegionX(), owner.getPackedRegionY());
			if (selected.containsKey(Long.valueOf(ownerKey))
				|| !hasAuthoredContent(owner)) {
				continue;
			}
			for (LayeredPackedRegionAuthoredReconstructionRecipe
				.ReconstructionPlacement placement : owner.getPlacements()) {
				LayeredPackedRegionAuthoredPlacementDependencyInventory
					.PlacementDependency dependency = placement.getDependency();
				int selectedReferences = 0;
				for (int x = dependency.getMinimumPackedRegionX();
					x <= dependency.getMaximumPackedRegionX(); x++) {
					for (int y = dependency.getMinimumPackedRegionY();
						y <= dependency.getMaximumPackedRegionY(); y++) {
						if (selected.containsKey(Long.valueOf(
							packedSourceKey(x, y)))) {
							selectedReferences = Math.incrementExact(
								selectedReferences);
						}
					}
				}
				if (selectedReferences == 0) {
					continue;
				}
				if (incomingPlacements >= maximumIncomingPlacements) {
					throw new IllegalArgumentException(
						"Dependency semantics exceeds its incoming-placement budget");
				}
				incomingPlacements = Math.incrementExact(incomingPlacements);
				Long boxedOwner = Long.valueOf(ownerKey);
				MutableIncomingOwner ownerEvidence = incoming.get(boxedOwner);
				if (ownerEvidence == null) {
					if (incoming.size() >= maximumIncomingOwners) {
						throw new IllegalArgumentException(
							"Dependency semantics exceeds its incoming-owner budget");
					}
					ownerEvidence = new MutableIncomingOwner(owner);
					incoming.put(boxedOwner, ownerEvidence);
				}
				DependencySemantics semantics =
					semanticsFor(dependency.getDependencyKind());
				ownerEvidence.record(semantics, selectedReferences);
				MutableKindSemantics kind = mutableKind(
					mutableKinds, placement.getKind(),
					dependency.getDependencyKind());
				kind.incomingPlacementCount = Math.incrementExact(
					kind.incomingPlacementCount);
				kind.incomingReferenceCount = Math.addExact(
					kind.incomingReferenceCount, selectedReferences);
			}
		}

		List<MutableSupportSource> orderedSupport =
			new ArrayList<MutableSupportSource>(support.values());
		Collections.sort(orderedSupport, SOURCE_COMPARATOR);
		List<SupportSource> supportEvidence =
			new ArrayList<SupportSource>(orderedSupport.size());
		for (MutableSupportSource source : orderedSupport) {
			supportEvidence.add(new SupportSource(source));
		}
		List<MutableIncomingOwner> orderedIncoming =
			new ArrayList<MutableIncomingOwner>(incoming.values());
		Collections.sort(orderedIncoming, INCOMING_COMPARATOR);
		List<IncomingOwner> incomingEvidence =
			new ArrayList<IncomingOwner>(orderedIncoming.size());
		for (MutableIncomingOwner owner : orderedIncoming) {
			incomingEvidence.add(new IncomingOwner(owner));
		}
		List<KindSemantics> kindEvidence = new ArrayList<KindSemantics>();
		for (ConstructionKind constructionKind : ConstructionKind.values()) {
			for (DependencyKind dependencyKind : DependencyKind.values()) {
				MutableKindSemantics kind = mutableKinds[
					constructionKind.ordinal()][dependencyKind.ordinal()];
				if (kind != null) {
					kindEvidence.add(new KindSemantics(kind));
				}
			}
		}
		return new
			LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis(
				recipe, safety, selectedEvidence, supportEvidence,
				incomingEvidence, kindEvidence);
	}

	public long getGeneration() { return generation; }
	public long getSafetyObservedAtTick() { return safetyObservedAtTick; }
	public List<SelectedSource> getSelectedSources() { return selectedSources; }
	public int getSelectedSourceCount() { return selectedSources.size(); }
	public int getSelectedAuthoredReplaySourceCount() {
		return selectedAuthoredReplaySourceCount;
	}
	public int getSelectedContentEmptySourceCount() {
		return selectedSources.size() - selectedAuthoredReplaySourceCount;
	}
	public int getReplayPlacementCount() { return replayPlacementCount; }
	public List<SupportSource> getOutboundSupportSources() {
		return outboundSupportSources;
	}
	public int getOutboundSupportSourceCount() {
		return outboundSupportSources.size();
	}
	public int getExternalOutboundSupportSourceCount() {
		return externalOutboundSupportSourceCount;
	}
	public int getOutboundSupportReferenceCount() {
		return outboundSupportReferenceCount;
	}
	public int getExternalOutboundSupportReferenceCount() {
		return externalOutboundSupportReferenceCount;
	}
	public List<IncomingOwner> getIncomingOwners() { return incomingOwners; }
	public int getIncomingOwnerSourceCount() { return incomingOwners.size(); }
	public int getIncomingPlacementCount() { return incomingPlacementCount; }
	public int getIncomingReferenceCount() { return incomingReferenceCount; }
	public List<KindSemantics> getKinds() { return kinds; }
	public int getKindCount() { return kinds.size(); }
	public boolean isSourceLocalReplay() { return true; }
	public boolean isSpatialReachPreserved() { return true; }
	public boolean isActiveInstanceEvidence() { return false; }
	public boolean isEntityRegistry() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	public enum DependencySemantics {
		STATIC_FOOTPRINT_SUPPORT,
		POTENTIAL_MOBILE_SUPPORT,
		ANCHOR_ONLY_SUPPORT
	}

	/** One exact safety source; only its own recipe may enter replay. */
	public static final class SelectedSource {
		private final int packedRegionX;
		private final int packedRegionY;
		private final boolean recipeSourcePresent;
		private final int replayPlacementCount;

		private SelectedSource(
			final int packedRegionX,
			final int packedRegionY,
			final LayeredPackedRegionAuthoredReconstructionRecipe
				.PackedSourceRecipe recipe) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.recipeSourcePresent = recipe != null;
			this.replayPlacementCount = recipe == null ? 0
				: recipe.getReconstructionPlacementCount();
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public boolean isRecipeSourcePresent() { return recipeSourcePresent; }
		public boolean hasAuthoredContent() { return replayPlacementCount > 0; }
		public int getReplayPlacementCount() { return replayPlacementCount; }
	}

	/** One coordinate conservatively affected by selected replay placements. */
	public static final class SupportSource {
		private final int packedRegionX;
		private final int packedRegionY;
		private final boolean selectedSource;
		private final boolean recipeSourcePresent;
		private final boolean authoredContentPresent;
		private final int ownerSourceCount;
		private final int placementReferenceCount;
		private final int staticFootprintReferenceCount;
		private final int potentialMobileReferenceCount;
		private final int anchorOnlyReferenceCount;

		private SupportSource(final MutableSupportSource source) {
			this.packedRegionX = source.packedRegionX;
			this.packedRegionY = source.packedRegionY;
			this.selectedSource = source.selectedSource;
			this.recipeSourcePresent = source.recipe != null;
			this.authoredContentPresent =
				LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
					.hasAuthoredContent(source.recipe);
			this.ownerSourceCount = source.owners.size();
			this.placementReferenceCount = source.placementReferenceCount;
			this.staticFootprintReferenceCount =
				source.staticFootprintReferenceCount;
			this.potentialMobileReferenceCount =
				source.potentialMobileReferenceCount;
			this.anchorOnlyReferenceCount = source.anchorOnlyReferenceCount;
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public boolean isSelectedSource() { return selectedSource; }
		public boolean isExternalSupportSource() { return !selectedSource; }
		public boolean isRecipeSourcePresent() { return recipeSourcePresent; }
		public boolean hasAuthoredContent() { return authoredContentPresent; }
		public int getOwnerSourceCount() { return ownerSourceCount; }
		public int getPlacementReferenceCount() {
			return placementReferenceCount;
		}
		public int getStaticFootprintReferenceCount() {
			return staticFootprintReferenceCount;
		}
		public int getPotentialMobileReferenceCount() {
			return potentialMobileReferenceCount;
		}
		public int getAnchorOnlyReferenceCount() {
			return anchorOnlyReferenceCount;
		}
	}

	/** One external authored owner with placements that may reach inward. */
	public static final class IncomingOwner {
		private final int packedRegionX;
		private final int packedRegionY;
		private final int ownerReplayPlacementCount;
		private final int incomingPlacementCount;
		private final int selectedSourceReferenceCount;
		private final int staticFootprintPlacementCount;
		private final int potentialMobilePlacementCount;
		private final int anchorOnlyPlacementCount;

		private IncomingOwner(final MutableIncomingOwner owner) {
			this.packedRegionX = owner.recipe.getPackedRegionX();
			this.packedRegionY = owner.recipe.getPackedRegionY();
			this.ownerReplayPlacementCount =
				owner.recipe.getReconstructionPlacementCount();
			this.incomingPlacementCount = owner.incomingPlacementCount;
			this.selectedSourceReferenceCount =
				owner.selectedSourceReferenceCount;
			this.staticFootprintPlacementCount =
				owner.staticFootprintPlacementCount;
			this.potentialMobilePlacementCount =
				owner.potentialMobilePlacementCount;
			this.anchorOnlyPlacementCount = owner.anchorOnlyPlacementCount;
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getOwnerReplayPlacementCount() {
			return ownerReplayPlacementCount;
		}
		public int getIncomingPlacementCount() {
			return incomingPlacementCount;
		}
		public int getSelectedSourceReferenceCount() {
			return selectedSourceReferenceCount;
		}
		public int getStaticFootprintPlacementCount() {
			return staticFootprintPlacementCount;
		}
		public int getPotentialMobilePlacementCount() {
			return potentialMobilePlacementCount;
		}
		public int getAnchorOnlyPlacementCount() {
			return anchorOnlyPlacementCount;
		}
	}

	/** Exact totals for one construction/dependency family and its semantics. */
	public static final class KindSemantics {
		private final ConstructionKind constructionKind;
		private final DependencyKind dependencyKind;
		private final DependencySemantics semantics;
		private final int replayPlacementCount;
		private final int outboundSupportReferenceCount;
		private final int externalOutboundSupportReferenceCount;
		private final int incomingPlacementCount;
		private final int incomingReferenceCount;

		private KindSemantics(final MutableKindSemantics kind) {
			this.constructionKind = kind.constructionKind;
			this.dependencyKind = kind.dependencyKind;
			this.semantics = semanticsFor(kind.dependencyKind);
			this.replayPlacementCount = kind.replayPlacementCount;
			this.outboundSupportReferenceCount =
				kind.outboundSupportReferenceCount;
			this.externalOutboundSupportReferenceCount =
				kind.externalOutboundSupportReferenceCount;
			this.incomingPlacementCount = kind.incomingPlacementCount;
			this.incomingReferenceCount = kind.incomingReferenceCount;
		}

		public ConstructionKind getConstructionKind() {
			return constructionKind;
		}
		public DependencyKind getDependencyKind() { return dependencyKind; }
		public DependencySemantics getSemantics() { return semantics; }
		public int getReplayPlacementCount() { return replayPlacementCount; }
		public int getOutboundSupportReferenceCount() {
			return outboundSupportReferenceCount;
		}
		public int getExternalOutboundSupportReferenceCount() {
			return externalOutboundSupportReferenceCount;
		}
		public int getIncomingPlacementCount() {
			return incomingPlacementCount;
		}
		public int getIncomingReferenceCount() {
			return incomingReferenceCount;
		}
	}

	private static MutableKindSemantics mutableKind(
		final MutableKindSemantics[][] kinds,
		final ConstructionKind constructionKind,
		final DependencyKind dependencyKind) {
		MutableKindSemantics kind = kinds[constructionKind.ordinal()]
			[dependencyKind.ordinal()];
		if (kind == null) {
			kind = new MutableKindSemantics(constructionKind, dependencyKind);
			kinds[constructionKind.ordinal()][dependencyKind.ordinal()] = kind;
		}
		return kind;
	}

	private static DependencySemantics semanticsFor(
		final DependencyKind dependencyKind) {
		switch (dependencyKind) {
			case OBJECT_FOOTPRINT:
				return DependencySemantics.STATIC_FOOTPRINT_SUPPORT;
			case NPC_ROAMING:
				return DependencySemantics.POTENTIAL_MOBILE_SUPPORT;
			case ANCHOR_ONLY:
				return DependencySemantics.ANCHOR_ONLY_SUPPORT;
			default:
				throw new IllegalArgumentException(
					"Unsupported dependency kind: " + dependencyKind);
		}
	}

	private static boolean hasAuthoredContent(
		final LayeredPackedRegionAuthoredReconstructionRecipe.PackedSourceRecipe
			recipe) {
		return recipe != null && recipe.getReconstructionPlacementCount() > 0;
	}

	private static boolean selectedEvidenceContains(
		final List<SelectedSource> sources,
		final int packedRegionX,
		final int packedRegionY) {
		for (SelectedSource source : sources) {
			if (source.getPackedRegionX() == packedRegionX
				&& source.getPackedRegionY() == packedRegionY) {
				return true;
			}
		}
		return false;
	}

	private static void validateSourceBudget(
		final int budget,
		final String label) {
		if (budget < 0 || budget > MAXIMUM_SOURCES) {
			throw new IllegalArgumentException(
				"Dependency-semantics " + label + " budget is invalid");
		}
	}

	private static long packedSourceKey(
		final int packedRegionX,
		final int packedRegionY) {
		return ((long) packedRegionX << 32)
			^ (packedRegionY & 0xFFFFFFFFL);
	}

	private static final Comparator<MutableSupportSource> SOURCE_COMPARATOR =
		new Comparator<MutableSupportSource>() {
			@Override
			public int compare(
				final MutableSupportSource left,
				final MutableSupportSource right) {
				int x = Integer.compare(left.packedRegionX, right.packedRegionX);
				return x != 0 ? x
					: Integer.compare(left.packedRegionY, right.packedRegionY);
			}
		};

	private static final Comparator<MutableIncomingOwner> INCOMING_COMPARATOR =
		new Comparator<MutableIncomingOwner>() {
			@Override
			public int compare(
				final MutableIncomingOwner left,
				final MutableIncomingOwner right) {
				int x = Integer.compare(
					left.recipe.getPackedRegionX(),
					right.recipe.getPackedRegionX());
				return x != 0 ? x : Integer.compare(
					left.recipe.getPackedRegionY(),
					right.recipe.getPackedRegionY());
			}
		};

	private static final class MutableSupportSource {
		private final int packedRegionX;
		private final int packedRegionY;
		private final boolean selectedSource;
		private final LayeredPackedRegionAuthoredReconstructionRecipe
			.PackedSourceRecipe recipe;
		private final Set<Long> owners = new LinkedHashSet<Long>();
		private int placementReferenceCount;
		private int staticFootprintReferenceCount;
		private int potentialMobileReferenceCount;
		private int anchorOnlyReferenceCount;

		private MutableSupportSource(
			final int packedRegionX,
			final int packedRegionY,
			final boolean selectedSource,
			final LayeredPackedRegionAuthoredReconstructionRecipe
				.PackedSourceRecipe recipe) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.selectedSource = selectedSource;
			this.recipe = recipe;
		}

		private void record(
			final long owner,
			final DependencySemantics semantics) {
			owners.add(Long.valueOf(owner));
			placementReferenceCount = Math.incrementExact(
				placementReferenceCount);
			switch (semantics) {
				case STATIC_FOOTPRINT_SUPPORT:
					staticFootprintReferenceCount = Math.incrementExact(
						staticFootprintReferenceCount);
					break;
				case POTENTIAL_MOBILE_SUPPORT:
					potentialMobileReferenceCount = Math.incrementExact(
						potentialMobileReferenceCount);
					break;
				case ANCHOR_ONLY_SUPPORT:
					anchorOnlyReferenceCount = Math.incrementExact(
						anchorOnlyReferenceCount);
					break;
				default:
					throw new IllegalArgumentException(
						"Unsupported dependency semantics: " + semantics);
			}
		}
	}

	private static final class MutableIncomingOwner {
		private final LayeredPackedRegionAuthoredReconstructionRecipe
			.PackedSourceRecipe recipe;
		private int incomingPlacementCount;
		private int selectedSourceReferenceCount;
		private int staticFootprintPlacementCount;
		private int potentialMobilePlacementCount;
		private int anchorOnlyPlacementCount;

		private MutableIncomingOwner(
			final LayeredPackedRegionAuthoredReconstructionRecipe
				.PackedSourceRecipe recipe) {
			this.recipe = recipe;
		}

		private void record(
			final DependencySemantics semantics,
			final int references) {
			incomingPlacementCount = Math.incrementExact(incomingPlacementCount);
			selectedSourceReferenceCount = Math.addExact(
				selectedSourceReferenceCount, references);
			switch (semantics) {
				case STATIC_FOOTPRINT_SUPPORT:
					staticFootprintPlacementCount = Math.incrementExact(
						staticFootprintPlacementCount);
					break;
				case POTENTIAL_MOBILE_SUPPORT:
					potentialMobilePlacementCount = Math.incrementExact(
						potentialMobilePlacementCount);
					break;
				case ANCHOR_ONLY_SUPPORT:
					anchorOnlyPlacementCount = Math.incrementExact(
						anchorOnlyPlacementCount);
					break;
				default:
					throw new IllegalArgumentException(
						"Unsupported dependency semantics: " + semantics);
			}
		}
	}

	private static final class MutableKindSemantics {
		private final ConstructionKind constructionKind;
		private final DependencyKind dependencyKind;
		private int replayPlacementCount;
		private int outboundSupportReferenceCount;
		private int externalOutboundSupportReferenceCount;
		private int incomingPlacementCount;
		private int incomingReferenceCount;

		private MutableKindSemantics(
			final ConstructionKind constructionKind,
			final DependencyKind dependencyKind) {
			this.constructionKind = constructionKind;
			this.dependencyKind = dependencyKind;
		}
	}
}
