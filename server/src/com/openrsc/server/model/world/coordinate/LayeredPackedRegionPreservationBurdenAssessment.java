package com.openrsc.server.model.world.coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, dormant inventory of runtime state that must either block packed
 * Region retirement or receive an explicit preservation/reload strategy.
 *
 * <p>The five families intentionally remain distinct. Players are a hard
 * point-in-time blocker rather than Region-owned state. Dynamic objects,
 * ground items, and owned events require preservation and restoration of their
 * runtime state. Collision products are derived state and require a checked
 * rebuild path rather than blind serialization. Partial or unavailable
 * evidence always remains a blocker.</p>
 *
 * <p>This value consumes one exact safety-source order but neither observes nor
 * changes runtime state. It cannot load, retain, retire, reconstruct, register,
 * gate, or mutate a Region or entity.</p>
 */
public final class LayeredPackedRegionPreservationBurdenAssessment {
	private final long observedAtTick;
	private final long safetyObservedAtTick;
	private final boolean retirementReadinessEvidence;
	private final List<SourceAssessment> sources;
	private final List<FamilySummary> familySummaries;
	private final int burdenSatisfiedSourceCount;

	private LayeredPackedRegionPreservationBurdenAssessment(
		final long observedAtTick,
		final LayeredPackedRegionRetirementSafetyAssessment safety,
		final List<SourceAssessment> sources,
		final List<FamilySummary> familySummaries) {
		this.observedAtTick = observedAtTick;
		this.safetyObservedAtTick = safety.getObservedAtTick();
		this.retirementReadinessEvidence =
			safety.hasRetirementReadinessEvidence();
		this.sources = Collections.unmodifiableList(sources);
		this.familySummaries = Collections.unmodifiableList(familySummaries);
		int satisfied = 0;
		for (SourceAssessment source : sources) {
			satisfied += source.isBurdenSatisfiedAtObservation() ? 1 : 0;
		}
		this.burdenSatisfiedSourceCount = satisfied;
	}

	/**
	 * Correlates one exact, bounded safety selection with same-order family
	 * inventories. No collection, entity, Region, event, or tile handle is kept.
	 */
	public static LayeredPackedRegionPreservationBurdenAssessment assess(
		final LayeredPackedRegionRetirementSafetyAssessment safety,
		final List<PackedSourceInventory> inventories,
		final long observedAtTick,
		final int maximumPackedSources) {
		LayeredPackedRegionRetirementSafetyAssessment checkedSafety =
			Objects.requireNonNull(safety, "safety");
		Objects.requireNonNull(inventories, "inventories");
		if (observedAtTick < 0L
			|| observedAtTick < checkedSafety.getObservedAtTick()
			|| maximumPackedSources < 0
			|| checkedSafety.getSourceCount() > maximumPackedSources
			|| inventories.size() != checkedSafety.getSourceCount()) {
			throw new IllegalArgumentException(
				"Preservation burden assessment is stale, incomplete, or unbounded");
		}

		List<SourceAssessment> assessed =
			new ArrayList<SourceAssessment>(inventories.size());
		for (int index = 0; index < inventories.size(); index++) {
			LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment
				safetySource = checkedSafety.getSources().get(index);
			PackedSourceInventory inventory = Objects.requireNonNull(
				inventories.get(index), "inventories[" + index + "]");
			if (safetySource.getPackedRegionX() != inventory.getPackedRegionX()
				|| safetySource.getPackedRegionY()
					!= inventory.getPackedRegionY()) {
				throw new IllegalArgumentException(
					"Preservation inventory must match safety source order");
			}
			assessed.add(SourceAssessment.from(safetySource, inventory));
		}

		List<FamilySummary> summaries = summarize(assessed);
		return new LayeredPackedRegionPreservationBurdenAssessment(
			observedAtTick, checkedSafety, assessed, summaries);
	}

	private static List<FamilySummary> summarize(
		final List<SourceAssessment> sources) {
		List<FamilySummary> summaries =
			new ArrayList<FamilySummary>(BurdenFamily.values().length);
		for (BurdenFamily family : BurdenFamily.values()) {
			int complete = 0;
			int partial = 0;
			int unavailable = 0;
			int blocked = 0;
			long knownInstances = 0L;
			for (SourceAssessment source : sources) {
				FamilyAssessment assessment =
					source.getFamilyAssessment(family);
				switch (assessment.getEvidenceCompleteness()) {
					case COMPLETE:
						complete++;
						break;
					case PARTIAL:
						partial++;
						break;
					case UNAVAILABLE:
						unavailable++;
						break;
					default:
						throw new IllegalStateException(
							"Unhandled preservation evidence completeness");
				}
				if (assessment.getObservedInstanceCount() >= 0) {
					knownInstances += assessment.getObservedInstanceCount();
				}
				blocked += assessment.isBurdenSatisfiedAtObservation() ? 0 : 1;
			}
			summaries.add(new FamilySummary(
				family, complete, partial, unavailable, blocked,
				knownInstances));
		}
		return summaries;
	}

	public long getObservedAtTick() { return observedAtTick; }
	public long getSafetyObservedAtTick() { return safetyObservedAtTick; }
	public boolean hasRetirementReadinessEvidence() {
		return retirementReadinessEvidence;
	}
	public List<SourceAssessment> getSources() { return sources; }
	public int getSourceCount() { return sources.size(); }
	public int getBurdenSatisfiedSourceCount() {
		return burdenSatisfiedSourceCount;
	}
	public int getBlockedSourceCount() {
		return getSourceCount() - burdenSatisfiedSourceCount;
	}
	public List<FamilySummary> getFamilySummaries() { return familySummaries; }
	public FamilySummary getFamilySummary(final BurdenFamily family) {
		return familySummaries.get(
			Objects.requireNonNull(family, "family").ordinal());
	}

	public boolean isPointInTimeOnly() { return true; }
	public boolean isCandidateSelectionMutated() { return false; }
	public boolean isPreservationPerformed() { return false; }
	public boolean isReloadRequest() { return false; }
	public boolean isEntityRegistry() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isTeardownTransaction() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	public enum BurdenPolicy {
		BLOCK_WHEN_PRESENT,
		PRESERVE_AND_RESTORE,
		REBUILD_DERIVED_STATE
	}

	public enum BurdenFamily {
		PLAYER_SESSION(BurdenPolicy.BLOCK_WHEN_PRESENT),
		DYNAMIC_OBJECT(BurdenPolicy.PRESERVE_AND_RESTORE),
		GROUND_ITEM(BurdenPolicy.PRESERVE_AND_RESTORE),
		COLLISION_PRODUCT(BurdenPolicy.REBUILD_DERIVED_STATE),
		OWNED_EVENT(BurdenPolicy.PRESERVE_AND_RESTORE);

		private final BurdenPolicy policy;

		BurdenFamily(final BurdenPolicy policy) {
			this.policy = policy;
		}

		public BurdenPolicy getPolicy() { return policy; }
	}

	public enum EvidenceCompleteness {
		COMPLETE,
		PARTIAL,
		UNAVAILABLE
	}

	public enum Blocker {
		EVIDENCE_PARTIAL,
		EVIDENCE_UNAVAILABLE,
		ACTIVE_PLAYERS_PRESENT,
		PRESERVATION_PATH_UNAVAILABLE,
		RELOAD_PATH_UNAVAILABLE
	}

	/** Detached evidence supplied for one family in one packed source. */
	public static final class FamilyEvidence {
		private final BurdenFamily family;
		private final EvidenceCompleteness evidenceCompleteness;
		private final int observedInstanceCount;
		private final boolean preservationSupported;
		private final boolean reloadSupported;

		private FamilyEvidence(
			final BurdenFamily family,
			final EvidenceCompleteness evidenceCompleteness,
			final int observedInstanceCount,
			final boolean preservationSupported,
			final boolean reloadSupported) {
			this.family = Objects.requireNonNull(family, "family");
			this.evidenceCompleteness = Objects.requireNonNull(
				evidenceCompleteness, "evidenceCompleteness");
			if ((evidenceCompleteness == EvidenceCompleteness.UNAVAILABLE
					&& observedInstanceCount != -1)
				|| (evidenceCompleteness != EvidenceCompleteness.UNAVAILABLE
					&& observedInstanceCount < 0)) {
				throw new IllegalArgumentException(
					"Unavailable evidence uses -1; observed evidence uses a count");
			}
			this.observedInstanceCount = observedInstanceCount;
			this.preservationSupported = preservationSupported;
			this.reloadSupported = reloadSupported;
		}

		public static FamilyEvidence of(
			final BurdenFamily family,
			final EvidenceCompleteness evidenceCompleteness,
			final int observedInstanceCount,
			final boolean preservationSupported,
			final boolean reloadSupported) {
			return new FamilyEvidence(
				family, evidenceCompleteness, observedInstanceCount,
				preservationSupported, reloadSupported);
		}

		public BurdenFamily getFamily() { return family; }
		public EvidenceCompleteness getEvidenceCompleteness() {
			return evidenceCompleteness;
		}
		public int getObservedInstanceCount() { return observedInstanceCount; }
		public boolean isPreservationSupported() {
			return preservationSupported;
		}
		public boolean isReloadSupported() { return reloadSupported; }
	}

	/** Fixed, complete family inventory for one exact packed source. */
	public static final class PackedSourceInventory {
		private final int packedRegionX;
		private final int packedRegionY;
		private final List<FamilyEvidence> families;

		private PackedSourceInventory(
			final int packedRegionX,
			final int packedRegionY,
			final List<FamilyEvidence> families) {
			if (packedRegionX < 0 || packedRegionY < 0) {
				throw new IllegalArgumentException(
					"Packed preservation source coordinates must not be negative");
			}
			Objects.requireNonNull(families, "families");
			Map<BurdenFamily, FamilyEvidence> byFamily =
				new EnumMap<BurdenFamily, FamilyEvidence>(BurdenFamily.class);
			for (FamilyEvidence evidence : families) {
				FamilyEvidence checked = Objects.requireNonNull(
					evidence, "family evidence");
				if (byFamily.put(checked.getFamily(), checked) != null) {
					throw new IllegalArgumentException(
						"Packed preservation families must be unique");
				}
			}
			if (byFamily.size() != BurdenFamily.values().length) {
				throw new IllegalArgumentException(
					"Packed preservation inventory must include every family");
			}
			List<FamilyEvidence> ordered =
				new ArrayList<FamilyEvidence>(BurdenFamily.values().length);
			for (BurdenFamily family : BurdenFamily.values()) {
				ordered.add(byFamily.get(family));
			}
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.families = Collections.unmodifiableList(ordered);
		}

		public static PackedSourceInventory of(
			final int packedRegionX,
			final int packedRegionY,
			final List<FamilyEvidence> families) {
			return new PackedSourceInventory(
				packedRegionX, packedRegionY, families);
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public List<FamilyEvidence> getFamilies() { return families; }
	}

	/** One immutable family result with stable blocker ordering. */
	public static final class FamilyAssessment {
		private final BurdenFamily family;
		private final EvidenceCompleteness evidenceCompleteness;
		private final int observedInstanceCount;
		private final boolean preservationSupported;
		private final boolean reloadSupported;
		private final List<Blocker> blockers;

		private FamilyAssessment(
			final FamilyEvidence evidence,
			final List<Blocker> blockers) {
			this.family = evidence.getFamily();
			this.evidenceCompleteness = evidence.getEvidenceCompleteness();
			this.observedInstanceCount = evidence.getObservedInstanceCount();
			this.preservationSupported = evidence.isPreservationSupported();
			this.reloadSupported = evidence.isReloadSupported();
			this.blockers = Collections.unmodifiableList(blockers);
		}

		private static FamilyAssessment from(final FamilyEvidence evidence) {
			List<Blocker> blockers = new ArrayList<Blocker>();
			if (evidence.getEvidenceCompleteness()
				== EvidenceCompleteness.PARTIAL) {
				blockers.add(Blocker.EVIDENCE_PARTIAL);
			} else if (evidence.getEvidenceCompleteness()
				== EvidenceCompleteness.UNAVAILABLE) {
				blockers.add(Blocker.EVIDENCE_UNAVAILABLE);
			}
			if (evidence.getObservedInstanceCount() > 0) {
				switch (evidence.getFamily().getPolicy()) {
					case BLOCK_WHEN_PRESENT:
						blockers.add(Blocker.ACTIVE_PLAYERS_PRESENT);
						break;
					case PRESERVE_AND_RESTORE:
						if (!evidence.isPreservationSupported()) {
							blockers.add(Blocker.PRESERVATION_PATH_UNAVAILABLE);
						}
						if (!evidence.isReloadSupported()) {
							blockers.add(Blocker.RELOAD_PATH_UNAVAILABLE);
						}
						break;
					case REBUILD_DERIVED_STATE:
						if (!evidence.isReloadSupported()) {
							blockers.add(Blocker.RELOAD_PATH_UNAVAILABLE);
						}
						break;
					default:
						throw new IllegalStateException(
							"Unhandled preservation burden policy");
				}
			}
			return new FamilyAssessment(evidence, blockers);
		}

		public BurdenFamily getFamily() { return family; }
		public BurdenPolicy getPolicy() { return family.getPolicy(); }
		public EvidenceCompleteness getEvidenceCompleteness() {
			return evidenceCompleteness;
		}
		public int getObservedInstanceCount() { return observedInstanceCount; }
		public boolean isPreservationSupported() {
			return preservationSupported;
		}
		public boolean isReloadSupported() { return reloadSupported; }
		public List<Blocker> getBlockers() { return blockers; }
		public boolean isBurdenSatisfiedAtObservation() {
			return blockers.isEmpty();
		}
	}

	/** One exact safety source correlated with all five runtime families. */
	public static final class SourceAssessment {
		private final int packedRegionX;
		private final int packedRegionY;
		private final boolean safetyContentQuiescent;
		private final boolean safetyLifecycleReady;
		private final List<FamilyAssessment> families;
		private final int blockedFamilyCount;

		private SourceAssessment(
			final LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment
				safetySource,
			final List<FamilyAssessment> families) {
			this.packedRegionX = safetySource.getPackedRegionX();
			this.packedRegionY = safetySource.getPackedRegionY();
			this.safetyContentQuiescent = safetySource.isContentQuiescent();
			this.safetyLifecycleReady = safetySource.isLifecycleReady();
			this.families = Collections.unmodifiableList(families);
			int blocked = 0;
			for (FamilyAssessment family : families) {
				blocked += family.isBurdenSatisfiedAtObservation() ? 0 : 1;
			}
			this.blockedFamilyCount = blocked;
		}

		private static SourceAssessment from(
			final LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment
				safetySource,
			final PackedSourceInventory inventory) {
			List<FamilyAssessment> assessed =
				new ArrayList<FamilyAssessment>(BurdenFamily.values().length);
			for (FamilyEvidence evidence : inventory.getFamilies()) {
				validateAgainstSafety(safetySource, evidence);
				assessed.add(FamilyAssessment.from(evidence));
			}
			return new SourceAssessment(safetySource, assessed);
		}

		private static void validateAgainstSafety(
			final LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment
				safetySource,
			final FamilyEvidence evidence) {
			if (evidence.getObservedInstanceCount() < 0) {
				return;
			}
			int count = evidence.getObservedInstanceCount();
			boolean complete = evidence.getEvidenceCompleteness()
				== EvidenceCompleteness.COMPLETE;
			switch (evidence.getFamily()) {
				case PLAYER_SESSION:
					if ((complete && count != safetySource.getPlayerCount())
						|| (!complete && count > safetySource.getPlayerCount())) {
						throw new IllegalArgumentException(
							"Player burden count conflicts with safety evidence");
					}
					break;
				case DYNAMIC_OBJECT:
					if (count > safetySource.getObjectCount()) {
						throw new IllegalArgumentException(
							"Dynamic object burden exceeds safety object count");
					}
					break;
				case GROUND_ITEM:
					if ((complete && count != safetySource.getGroundItemCount())
						|| (!complete
							&& count > safetySource.getGroundItemCount())) {
						throw new IllegalArgumentException(
							"Ground-item burden count conflicts with safety evidence");
					}
					break;
				case COLLISION_PRODUCT:
				case OWNED_EVENT:
					break;
				default:
					throw new IllegalStateException(
						"Unhandled preservation burden family");
			}
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public boolean isSafetyContentQuiescent() {
			return safetyContentQuiescent;
		}
		public boolean isSafetyLifecycleReady() { return safetyLifecycleReady; }
		public List<FamilyAssessment> getFamilies() { return families; }
		public FamilyAssessment getFamilyAssessment(final BurdenFamily family) {
			return families.get(
				Objects.requireNonNull(family, "family").ordinal());
		}
		public int getBlockedFamilyCount() { return blockedFamilyCount; }
		public boolean isBurdenSatisfiedAtObservation() {
			return blockedFamilyCount == 0;
		}
	}

	/** Stable per-family aggregate; unknown observations are not counted as 0. */
	public static final class FamilySummary {
		private final BurdenFamily family;
		private final int completeSourceCount;
		private final int partialSourceCount;
		private final int unavailableSourceCount;
		private final int blockedSourceCount;
		private final long knownObservedInstanceCount;

		private FamilySummary(
			final BurdenFamily family,
			final int completeSourceCount,
			final int partialSourceCount,
			final int unavailableSourceCount,
			final int blockedSourceCount,
			final long knownObservedInstanceCount) {
			this.family = family;
			this.completeSourceCount = completeSourceCount;
			this.partialSourceCount = partialSourceCount;
			this.unavailableSourceCount = unavailableSourceCount;
			this.blockedSourceCount = blockedSourceCount;
			this.knownObservedInstanceCount = knownObservedInstanceCount;
		}

		public BurdenFamily getFamily() { return family; }
		public int getCompleteSourceCount() { return completeSourceCount; }
		public int getPartialSourceCount() { return partialSourceCount; }
		public int getUnavailableSourceCount() { return unavailableSourceCount; }
		public int getBlockedSourceCount() { return blockedSourceCount; }
		public long getKnownObservedInstanceCount() {
			return knownObservedInstanceCount;
		}
	}
}
