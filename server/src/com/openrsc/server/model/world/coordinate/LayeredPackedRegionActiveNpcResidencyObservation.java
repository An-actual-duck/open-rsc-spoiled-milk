package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Classifies a bounded point-in-time NPC census around exact safety sources.
 *
 * <p>Valid authored identity establishes replay ownership while current packed
 * coordinates establish residency. Those two facts remain independent: one
 * selected-owned NPC may currently be outside the selection, while an NPC
 * authored elsewhere may currently be inside it. Invalid identity is retained
 * as unresolved evidence instead of being assigned an owner.</p>
 *
 * <p>This immutable value contains detached primitive evidence only. It has no
 * entity, Region, event, registry, cache, callback, movement, arrival, respawn,
 * combat, retention, loading, release, teardown, reconstruction, transaction,
 * rollback, permit, lease, commit, or lifecycle authority.</p>
 */
public final class LayeredPackedRegionActiveNpcResidencyObservation {
	public static final int MAXIMUM_INSTANCES =
		LayeredPackedRegionAuthoredReconstructionRecipe
			.MAXIMUM_AUTHORED_PLACEMENTS;

	private final long generation;
	private final long safetyObservedAtTick;
	private final long censusObservedAtTick;
	private final int selectedSourceCount;
	private final int observedInstanceCount;
	private final int activeInstanceCount;
	private final int inactiveInstanceCount;
	private final int activeRecognizedInstanceCount;
	private final int activeUnrecognizedInstanceCount;
	private final int uniqueActiveRecognizedIdentityCount;
	private final int duplicateActiveRecognizedIdentityInstanceCount;
	private final int relevantActiveInstanceCount;
	private final int irrelevantActiveInstanceCount;
	private final int selectedOwnerInsideCount;
	private final int selectedOwnerOutsideCount;
	private final int externalOwnerInsideCount;
	private final int unresolvedInsideCount;
	private final int unresolvedClaimedSelectedOwnerOutsideCount;
	private final int inactiveRelevantInstanceCount;
	private final int inactiveIrrelevantInstanceCount;
	private final List<InstanceEvidence> relevantActiveInstances;
	private final List<IdentityStatusCount> identityStatuses;

	private LayeredPackedRegionActiveNpcResidencyObservation(
		final LayeredPackedRegionAuthoredReconstructionRecipe recipe,
		final LayeredPackedRegionRetirementSafetyAssessment safety,
		final long censusObservedAtTick,
		final List<NpcInstanceSnapshot> instances,
		final List<InstanceEvidence> relevantActiveInstances,
		final List<IdentityStatusCount> identityStatuses,
		final int activeRecognizedInstanceCount,
		final int activeUnrecognizedInstanceCount,
		final int uniqueActiveRecognizedIdentityCount,
		final int duplicateActiveRecognizedIdentityInstanceCount,
		final int inactiveRelevantInstanceCount,
		final int inactiveIrrelevantInstanceCount) {
		this.generation = recipe.getGeneration();
		this.safetyObservedAtTick = safety.getObservedAtTick();
		this.censusObservedAtTick = censusObservedAtTick;
		this.selectedSourceCount = safety.getSourceCount();
		this.observedInstanceCount = instances.size();
		this.relevantActiveInstances = Collections.unmodifiableList(
			new ArrayList<InstanceEvidence>(relevantActiveInstances));
		this.identityStatuses = Collections.unmodifiableList(
			new ArrayList<IdentityStatusCount>(identityStatuses));
		this.activeRecognizedInstanceCount = activeRecognizedInstanceCount;
		this.activeUnrecognizedInstanceCount = activeUnrecognizedInstanceCount;
		this.uniqueActiveRecognizedIdentityCount =
			uniqueActiveRecognizedIdentityCount;
		this.duplicateActiveRecognizedIdentityInstanceCount =
			duplicateActiveRecognizedIdentityInstanceCount;
		this.inactiveRelevantInstanceCount = inactiveRelevantInstanceCount;
		this.inactiveIrrelevantInstanceCount = inactiveIrrelevantInstanceCount;

		int active = 0;
		for (NpcInstanceSnapshot instance : instances) {
			active += instance.isActive() ? 1 : 0;
		}
		this.activeInstanceCount = active;
		this.inactiveInstanceCount = instances.size() - active;
		int selectedInside = 0;
		int selectedOutside = 0;
		int externalInside = 0;
		int unresolvedInside = 0;
		int unresolvedClaimedOutside = 0;
		for (InstanceEvidence evidence : relevantActiveInstances) {
			switch (evidence.getClassification()) {
				case SELECTED_OWNER_INSIDE:
					selectedInside = Math.incrementExact(selectedInside);
					break;
				case SELECTED_OWNER_OUTSIDE:
					selectedOutside = Math.incrementExact(selectedOutside);
					break;
				case EXTERNAL_OWNER_INSIDE:
					externalInside = Math.incrementExact(externalInside);
					break;
				case UNRESOLVED_INSIDE:
					unresolvedInside = Math.incrementExact(unresolvedInside);
					break;
				case UNRESOLVED_CLAIMED_SELECTED_OWNER_OUTSIDE:
					unresolvedClaimedOutside = Math.incrementExact(
						unresolvedClaimedOutside);
					break;
				default:
					throw new IllegalArgumentException(
						"Unsupported active NPC residency classification");
			}
		}
		this.selectedOwnerInsideCount = selectedInside;
		this.selectedOwnerOutsideCount = selectedOutside;
		this.externalOwnerInsideCount = externalInside;
		this.unresolvedInsideCount = unresolvedInside;
		this.unresolvedClaimedSelectedOwnerOutsideCount =
			unresolvedClaimedOutside;
		this.relevantActiveInstanceCount = relevantActiveInstances.size();
		this.irrelevantActiveInstanceCount = active - relevantActiveInstances.size();

		int statusInstances = 0;
		for (IdentityStatusCount status : identityStatuses) {
			statusInstances = Math.addExact(
				statusInstances, status.getActiveInstanceCount());
		}
		if (statusInstances != active
			|| active != activeRecognizedInstanceCount
				+ activeUnrecognizedInstanceCount
			|| activeRecognizedInstanceCount
				!= uniqueActiveRecognizedIdentityCount
					+ duplicateActiveRecognizedIdentityInstanceCount
			|| inactiveInstanceCount
				!= inactiveRelevantInstanceCount
					+ inactiveIrrelevantInstanceCount
			|| relevantActiveInstanceCount
				!= selectedInside + selectedOutside + externalInside
					+ unresolvedInside + unresolvedClaimedOutside) {
			throw new IllegalArgumentException(
				"Active NPC residency arithmetic is inconsistent");
		}
	}

	/**
	 * Classifies one detached census. Overflow refuses the complete observation;
	 * no instance or relevant detail is silently dropped.
	 */
	public static LayeredPackedRegionActiveNpcResidencyObservation observe(
		final LayeredPackedRegionAuthoredReconstructionRecipe recipe,
		final LayeredPackedRegionRetirementSafetyAssessment safety,
		final long censusObservedAtTick,
		final List<NpcInstanceSnapshot> instances,
		final int maximumInstances,
		final int maximumRelevantDetails) {
		if (recipe == null) {
			throw new NullPointerException("recipe");
		}
		if (safety == null) {
			throw new NullPointerException("safety");
		}
		if (instances == null) {
			throw new NullPointerException("instances");
		}
		validateBudget(maximumInstances, "instance");
		validateBudget(maximumRelevantDetails, "relevant-detail");
		if (censusObservedAtTick < 0L) {
			throw new IllegalArgumentException(
				"NPC census observation tick must not be negative");
		}
		if (instances.size() > maximumInstances) {
			throw new IllegalArgumentException(
				"NPC census exceeds its instance budget");
		}

		Map<LayeredAuthoredPlacementIdentity,
			LayeredPackedRegionAuthoredReconstructionRecipe
				.ReconstructionPlacement> npcRecipes =
					new LinkedHashMap<LayeredAuthoredPlacementIdentity,
						LayeredPackedRegionAuthoredReconstructionRecipe
							.ReconstructionPlacement>();
		for (LayeredPackedRegionAuthoredReconstructionRecipe.PackedSourceRecipe
			source : recipe.getSources()) {
			for (LayeredPackedRegionAuthoredReconstructionRecipe
				.ReconstructionPlacement placement : source.getPlacements()) {
				if (placement.getKind() != ConstructionKind.NPC_SPAWN) {
					continue;
				}
				if (npcRecipes.put(placement.getIdentity(), placement) != null) {
					throw new IllegalArgumentException(
						"Recipe contains a duplicate NPC authored identity");
				}
			}
		}
		Set<Long> selected = new LinkedHashSet<Long>();
		for (LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment
			source : safety.getSources()) {
			if (!selected.add(Long.valueOf(sourceKey(
				source.getPackedRegionX(), source.getPackedRegionY())))) {
				throw new IllegalArgumentException(
					"Safety contains a duplicate packed source");
			}
		}

		int[] activeStatusCounts = new int[IdentityStatus.values().length];
		Map<LayeredAuthoredPlacementIdentity, Integer> recognizedCounts =
			new LinkedHashMap<LayeredAuthoredPlacementIdentity, Integer>();
		List<InstanceEvidence> relevant = new ArrayList<InstanceEvidence>();
		int recognized = 0;
		int unrecognized = 0;
		int inactiveRelevant = 0;
		int inactiveIrrelevant = 0;
		for (NpcInstanceSnapshot instance : instances) {
			if (instance == null) {
				throw new NullPointerException("instance");
			}
			IdentityResolution resolution = resolveIdentity(
				recipe, npcRecipes, instance);
			boolean currentSelected = selected.contains(Long.valueOf(sourceKey(
				instance.getCurrentPackedRegionX(),
				instance.getCurrentPackedRegionY())));
			LayeredAuthoredPlacementIdentity identity = instance.getIdentity();
			boolean claimedOwnerSelected = identity != null
				&& selected.contains(Long.valueOf(sourceKey(
					identity.getPackedRegionX(), identity.getPackedRegionY())));
			if (!instance.isActive()) {
				if (currentSelected || claimedOwnerSelected) {
					inactiveRelevant = Math.incrementExact(inactiveRelevant);
				} else {
					inactiveIrrelevant = Math.incrementExact(inactiveIrrelevant);
				}
				continue;
			}
			activeStatusCounts[resolution.status.ordinal()] = Math.incrementExact(
				activeStatusCounts[resolution.status.ordinal()]);
			if (resolution.status == IdentityStatus.RECOGNIZED) {
				recognized = Math.incrementExact(recognized);
				Integer previous = recognizedCounts.get(identity);
				recognizedCounts.put(identity, Integer.valueOf(
					previous == null ? 1 : Math.incrementExact(previous.intValue())));
			} else {
				unrecognized = Math.incrementExact(unrecognized);
			}

			ActiveResidencyClassification classification = null;
			if (resolution.status == IdentityStatus.RECOGNIZED) {
				if (claimedOwnerSelected && currentSelected) {
					classification =
						ActiveResidencyClassification.SELECTED_OWNER_INSIDE;
				} else if (claimedOwnerSelected) {
					classification =
						ActiveResidencyClassification.SELECTED_OWNER_OUTSIDE;
				} else if (currentSelected) {
					classification =
						ActiveResidencyClassification.EXTERNAL_OWNER_INSIDE;
				}
			} else if (currentSelected) {
				classification = ActiveResidencyClassification.UNRESOLVED_INSIDE;
			} else if (claimedOwnerSelected) {
				classification = ActiveResidencyClassification
					.UNRESOLVED_CLAIMED_SELECTED_OWNER_OUTSIDE;
			}
			if (classification != null) {
				if (relevant.size() >= maximumRelevantDetails) {
					throw new IllegalArgumentException(
						"NPC census exceeds its relevant-detail budget");
				}
				relevant.add(new InstanceEvidence(
					instance, resolution, classification));
			}
		}
		Collections.sort(relevant, EVIDENCE_COMPARATOR);
		List<IdentityStatusCount> statuses =
			new ArrayList<IdentityStatusCount>(IdentityStatus.values().length);
		for (IdentityStatus status : IdentityStatus.values()) {
			statuses.add(new IdentityStatusCount(
				status, activeStatusCounts[status.ordinal()]));
		}
		int uniqueRecognized = recognizedCounts.size();
		int duplicateRecognized = recognized - uniqueRecognized;
		return new LayeredPackedRegionActiveNpcResidencyObservation(
			recipe, safety, censusObservedAtTick, instances, relevant, statuses,
			recognized, unrecognized, uniqueRecognized, duplicateRecognized,
			inactiveRelevant, inactiveIrrelevant);
	}

	public long getGeneration() { return generation; }
	public long getSafetyObservedAtTick() { return safetyObservedAtTick; }
	public long getCensusObservedAtTick() { return censusObservedAtTick; }
	public int getSelectedSourceCount() { return selectedSourceCount; }
	public int getObservedInstanceCount() { return observedInstanceCount; }
	public int getActiveInstanceCount() { return activeInstanceCount; }
	public int getInactiveInstanceCount() { return inactiveInstanceCount; }
	public int getActiveRecognizedInstanceCount() {
		return activeRecognizedInstanceCount;
	}
	public int getActiveUnrecognizedInstanceCount() {
		return activeUnrecognizedInstanceCount;
	}
	public int getUniqueActiveRecognizedIdentityCount() {
		return uniqueActiveRecognizedIdentityCount;
	}
	public int getDuplicateActiveRecognizedIdentityInstanceCount() {
		return duplicateActiveRecognizedIdentityInstanceCount;
	}
	public int getRelevantActiveInstanceCount() {
		return relevantActiveInstanceCount;
	}
	public int getIrrelevantActiveInstanceCount() {
		return irrelevantActiveInstanceCount;
	}
	public int getSelectedOwnerInsideCount() {
		return selectedOwnerInsideCount;
	}
	public int getSelectedOwnerOutsideCount() {
		return selectedOwnerOutsideCount;
	}
	public int getExternalOwnerInsideCount() {
		return externalOwnerInsideCount;
	}
	public int getUnresolvedInsideCount() { return unresolvedInsideCount; }
	public int getUnresolvedClaimedSelectedOwnerOutsideCount() {
		return unresolvedClaimedSelectedOwnerOutsideCount;
	}
	public int getInactiveRelevantInstanceCount() {
		return inactiveRelevantInstanceCount;
	}
	public int getInactiveIrrelevantInstanceCount() {
		return inactiveIrrelevantInstanceCount;
	}
	public List<InstanceEvidence> getRelevantActiveInstances() {
		return relevantActiveInstances;
	}
	public List<IdentityStatusCount> getIdentityStatuses() {
		return identityStatuses;
	}
	public boolean isPointInTimeCensus() { return true; }
	public boolean isActiveInstanceEvidence() { return true; }
	public boolean isEntityRegistry() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	public enum IdentityStatus {
		RECOGNIZED,
		MISSING_AUTHORED_IDENTITY,
		STALE_GENERATION,
		NON_NPC_IDENTITY,
		UNKNOWN_RECIPE_IDENTITY,
		RUNTIME_ID_MISMATCH
	}

	public enum ActiveResidencyClassification {
		SELECTED_OWNER_INSIDE,
		SELECTED_OWNER_OUTSIDE,
		EXTERNAL_OWNER_INSIDE,
		UNRESOLVED_INSIDE,
		UNRESOLVED_CLAIMED_SELECTED_OWNER_OUTSIDE
	}

	/** One detached runtime census input; identity may be absent. */
	public static final class NpcInstanceSnapshot {
		private final LayeredAuthoredPlacementIdentity identity;
		private final int runtimeNpcId;
		private final int currentPackedRegionX;
		private final int currentPackedRegionY;
		private final boolean active;

		public NpcInstanceSnapshot(
			final LayeredAuthoredPlacementIdentity identity,
			final int runtimeNpcId,
			final int currentPackedRegionX,
			final int currentPackedRegionY,
			final boolean active) {
			if (runtimeNpcId < 0) {
				throw new IllegalArgumentException(
					"Runtime NPC ID must not be negative");
			}
			if (currentPackedRegionX < 0 || currentPackedRegionY < 0) {
				throw new IllegalArgumentException(
					"Runtime NPC packed source must not be negative");
			}
			this.identity = identity;
			this.runtimeNpcId = runtimeNpcId;
			this.currentPackedRegionX = currentPackedRegionX;
			this.currentPackedRegionY = currentPackedRegionY;
			this.active = active;
		}

		public LayeredAuthoredPlacementIdentity getIdentity() { return identity; }
		public int getRuntimeNpcId() { return runtimeNpcId; }
		public int getCurrentPackedRegionX() { return currentPackedRegionX; }
		public int getCurrentPackedRegionY() { return currentPackedRegionY; }
		public boolean isActive() { return active; }
	}

	/** One relevant active instance, detached from the runtime NPC object. */
	public static final class InstanceEvidence {
		private final LayeredAuthoredPlacementIdentity identity;
		private final int runtimeNpcId;
		private final int currentPackedRegionX;
		private final int currentPackedRegionY;
		private final IdentityStatus identityStatus;
		private final Integer expectedRuntimeNpcId;
		private final ActiveResidencyClassification classification;

		private InstanceEvidence(
			final NpcInstanceSnapshot instance,
			final IdentityResolution resolution,
			final ActiveResidencyClassification classification) {
			this.identity = instance.getIdentity();
			this.runtimeNpcId = instance.getRuntimeNpcId();
			this.currentPackedRegionX = instance.getCurrentPackedRegionX();
			this.currentPackedRegionY = instance.getCurrentPackedRegionY();
			this.identityStatus = resolution.status;
			this.expectedRuntimeNpcId = resolution.expectedRuntimeNpcId;
			this.classification = classification;
		}

		public LayeredAuthoredPlacementIdentity getIdentity() { return identity; }
		public int getRuntimeNpcId() { return runtimeNpcId; }
		public int getCurrentPackedRegionX() { return currentPackedRegionX; }
		public int getCurrentPackedRegionY() { return currentPackedRegionY; }
		public IdentityStatus getIdentityStatus() { return identityStatus; }
		public Integer getExpectedRuntimeNpcId() { return expectedRuntimeNpcId; }
		public ActiveResidencyClassification getClassification() {
			return classification;
		}
	}

	/** Whole-census active count for one identity-resolution result. */
	public static final class IdentityStatusCount {
		private final IdentityStatus status;
		private final int activeInstanceCount;

		private IdentityStatusCount(
			final IdentityStatus status,
			final int activeInstanceCount) {
			this.status = status;
			this.activeInstanceCount = activeInstanceCount;
		}

		public IdentityStatus getStatus() { return status; }
		public int getActiveInstanceCount() { return activeInstanceCount; }
	}

	private static IdentityResolution resolveIdentity(
		final LayeredPackedRegionAuthoredReconstructionRecipe recipe,
		final Map<LayeredAuthoredPlacementIdentity,
			LayeredPackedRegionAuthoredReconstructionRecipe
				.ReconstructionPlacement> npcRecipes,
		final NpcInstanceSnapshot instance) {
		LayeredAuthoredPlacementIdentity identity = instance.getIdentity();
		if (identity == null) {
			return new IdentityResolution(
				IdentityStatus.MISSING_AUTHORED_IDENTITY, null);
		}
		if (identity.getGeneration() != recipe.getGeneration()) {
			return new IdentityResolution(IdentityStatus.STALE_GENERATION, null);
		}
		if (identity.getConstructionKind() != ConstructionKind.NPC_SPAWN) {
			return new IdentityResolution(IdentityStatus.NON_NPC_IDENTITY, null);
		}
		LayeredPackedRegionAuthoredReconstructionRecipe.ReconstructionPlacement
			placement = npcRecipes.get(identity);
		if (placement == null) {
			return new IdentityResolution(
				IdentityStatus.UNKNOWN_RECIPE_IDENTITY, null);
		}
		int expected = placement.getPlacement().getConstructedEntityId();
		if (expected != instance.getRuntimeNpcId()) {
			return new IdentityResolution(
				IdentityStatus.RUNTIME_ID_MISMATCH, Integer.valueOf(expected));
		}
		return new IdentityResolution(
			IdentityStatus.RECOGNIZED, Integer.valueOf(expected));
	}

	private static void validateBudget(final int budget, final String label) {
		if (budget < 0 || budget > MAXIMUM_INSTANCES) {
			throw new IllegalArgumentException(
				"Active NPC " + label + " budget is invalid");
		}
	}

	private static long sourceKey(
		final int packedRegionX,
		final int packedRegionY) {
		return ((long) packedRegionX << 32)
			^ (packedRegionY & 0xFFFFFFFFL);
	}

	private static final class IdentityResolution {
		private final IdentityStatus status;
		private final Integer expectedRuntimeNpcId;

		private IdentityResolution(
			final IdentityStatus status,
			final Integer expectedRuntimeNpcId) {
			this.status = status;
			this.expectedRuntimeNpcId = expectedRuntimeNpcId;
		}
	}

	private static final Comparator<InstanceEvidence> EVIDENCE_COMPARATOR =
		new Comparator<InstanceEvidence>() {
			@Override
			public int compare(
				final InstanceEvidence left,
				final InstanceEvidence right) {
				int classification = left.getClassification().compareTo(
					right.getClassification());
				if (classification != 0) { return classification; }
				int x = Integer.compare(
					left.getCurrentPackedRegionX(),
					right.getCurrentPackedRegionX());
				if (x != 0) { return x; }
				int y = Integer.compare(
					left.getCurrentPackedRegionY(),
					right.getCurrentPackedRegionY());
				if (y != 0) { return y; }
				LayeredAuthoredPlacementIdentity leftIdentity = left.getIdentity();
				LayeredAuthoredPlacementIdentity rightIdentity = right.getIdentity();
				if (leftIdentity == null || rightIdentity == null) {
					if (leftIdentity != rightIdentity) {
						return leftIdentity == null ? -1 : 1;
					}
				} else {
					int generation = Long.compare(
						leftIdentity.getGeneration(),
						rightIdentity.getGeneration());
					if (generation != 0) { return generation; }
					int ownerX = Integer.compare(
						leftIdentity.getPackedRegionX(),
						rightIdentity.getPackedRegionX());
					if (ownerX != 0) { return ownerX; }
					int ownerY = Integer.compare(
						leftIdentity.getPackedRegionY(),
						rightIdentity.getPackedRegionY());
					if (ownerY != 0) { return ownerY; }
					int ordinal = Integer.compare(
						leftIdentity.getSourceOrdinal(),
						rightIdentity.getSourceOrdinal());
					if (ordinal != 0) { return ordinal; }
					int kind = leftIdentity.getConstructionKind().compareTo(
						rightIdentity.getConstructionKind());
					if (kind != 0) { return kind; }
				}
				int status = left.getIdentityStatus().compareTo(
					right.getIdentityStatus());
				return status != 0 ? status
					: Integer.compare(left.getRuntimeNpcId(), right.getRuntimeNpcId());
			}
		};
}
