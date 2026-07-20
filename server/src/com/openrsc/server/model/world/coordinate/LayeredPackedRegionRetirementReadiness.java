package com.openrsc.server.model.world.coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, dormant packed-source readiness projected from current logical
 * Region retirement decisions.
 *
 * <p>A legacy packed Region may contribute tiles to more than one logical
 * Region, including logical Regions on opposite sides of a legacy 944-tile
 * plane boundary. A packed source is ready only when every logical Region it
 * covers has an eligible decision in the same bounded, same-tick input. Edge
 * sources that extend outside the legacy domain remain explicitly blocked.
 * Readiness is evidence only: it contains no Region handle and cannot unload,
 * unregister, remove, or evict packed storage.</p>
 */
public final class LayeredPackedRegionRetirementReadiness {
	public static final int MAX_PACKED_SOURCES_PER_LOGICAL_REGION = 2;

	private final long observedAtTick;
	private final long ownershipVersion;
	private final long residencyMirrorVersion;
	private final int logicalDecisionCount;
	private final List<SourceReadiness> sources;
	private final int readySourceCount;

	private LayeredPackedRegionRetirementReadiness(
		final long observedAtTick,
		final long ownershipVersion,
		final long residencyMirrorVersion,
		final int logicalDecisionCount,
		final List<SourceReadiness> sources) {
		this.observedAtTick = observedAtTick;
		this.ownershipVersion = ownershipVersion;
		this.residencyMirrorVersion = residencyMirrorVersion;
		this.logicalDecisionCount = logicalDecisionCount;
		this.sources = Collections.unmodifiableList(sources);
		int readyCount = 0;
		for (SourceReadiness source : sources) {
			if (source.isReady()) {
				readyCount++;
			}
		}
		this.readySourceCount = readyCount;
	}

	/**
	 * Aggregates one bounded, atomic logical-decision batch by packed source.
	 */
	public static LayeredPackedRegionRetirementReadiness fromDecisions(
		final List<LayeredRegionRetirementDecisionArbiter.Decision> decisions,
		final int maximumLogicalRegions,
		final int maximumPackedSources) {
		if (decisions == null) {
			throw new NullPointerException("decisions");
		}
		if (maximumLogicalRegions < 0
			|| maximumPackedSources < 0
			|| decisions.size() > maximumLogicalRegions) {
			throw new IllegalArgumentException(
				"Packed retirement readiness exceeds its bounded input budget");
		}
		if (decisions.isEmpty()) {
			return new LayeredPackedRegionRetirementReadiness(
				-1L, -1L, -1L, 0, new ArrayList<SourceReadiness>());
		}

		Map<WorldRegionKey, LayeredRegionRetirementDecisionArbiter.Decision>
			decisionsByKey = new LinkedHashMap<WorldRegionKey,
				LayeredRegionRetirementDecisionArbiter.Decision>();
		long observedTick = -1L;
		long currentOwnershipVersion = -1L;
		long currentResidencyVersion = -1L;
		for (LayeredRegionRetirementDecisionArbiter.Decision decision
			: decisions) {
			LayeredRegionRetirementDecisionArbiter.Decision checked =
				Objects.requireNonNull(decision, "decision");
			if (decisionsByKey.put(checked.getLogicalRegionKey(), checked) != null) {
				throw new IllegalArgumentException(
					"Packed retirement decisions must identify unique logical Regions");
			}
			if (observedTick < 0L) {
				observedTick = checked.getObservedAtTick();
				currentOwnershipVersion = checked.getCurrentOwnershipVersion();
				currentResidencyVersion =
					checked.getCurrentResidencyMirrorVersion();
			} else if (observedTick != checked.getObservedAtTick()
				|| currentOwnershipVersion
					!= checked.getCurrentOwnershipVersion()
				|| currentResidencyVersion
					!= checked.getCurrentResidencyMirrorVersion()) {
				throw new IllegalArgumentException(
					"Packed retirement decisions must share one current snapshot");
			}
		}

		Map<PackedSourceKey, LegacyPackedRegionCoverage> sourceCoverage =
			new LinkedHashMap<PackedSourceKey, LegacyPackedRegionCoverage>();
		for (WorldRegionKey logicalKey : decisionsByKey.keySet()) {
			LegacyLogicalRegionAssembly assembly =
				LegacyLogicalRegionAssembly.fromLogicalRegionKey(logicalKey);
			if (assembly.getSourceFragments().size()
				> MAX_PACKED_SOURCES_PER_LOGICAL_REGION) {
				throw new IllegalStateException(
					"Logical Region exceeds the checked packed-source fanout");
			}
			for (LegacyLogicalRegionAssembly.SourceFragment fragment
				: assembly.getSourceFragments()) {
				PackedSourceKey sourceKey = new PackedSourceKey(
					fragment.getPackedRegionX(), fragment.getPackedRegionY());
				if (!sourceCoverage.containsKey(sourceKey)) {
					sourceCoverage.put(sourceKey,
						LegacyPackedRegionCoverage.fromPackedRegionCoordinates(
							sourceKey.packedRegionX, sourceKey.packedRegionY));
					if (sourceCoverage.size() > maximumPackedSources) {
						throw new IllegalArgumentException(
							"Packed retirement readiness exceeds its source budget");
					}
				}
			}
		}

		List<SourceReadiness> sourceResults =
			new ArrayList<SourceReadiness>(sourceCoverage.size());
		for (Map.Entry<PackedSourceKey, LegacyPackedRegionCoverage> entry
			: sourceCoverage.entrySet()) {
			LegacyPackedRegionCoverage coverage = entry.getValue();
			List<WorldRegionKey> missing = new ArrayList<WorldRegionKey>();
			List<WorldRegionKey> refused = new ArrayList<WorldRegionKey>();
			List<WorldRegionKey> partialResidency =
				new ArrayList<WorldRegionKey>();
			for (WorldRegionKey coveredKey : coverage.getCoveredKeys()) {
				LayeredRegionRetirementDecisionArbiter.Decision decision =
					decisionsByKey.get(coveredKey);
				if (decision == null) {
					missing.add(coveredKey);
				} else if (!decision.isEligible()) {
					refused.add(coveredKey);
				} else if (!decision.isCurrentResidencyComplete()) {
					partialResidency.add(coveredKey);
				}
			}
			SourceState state;
			if (!coverage.isFullyInsideLegacyDomain()) {
				state = SourceState.PARTIAL_LEGACY_DOMAIN;
			} else if (!missing.isEmpty()) {
				state = SourceState.INCOMPLETE_COVERAGE;
			} else if (!refused.isEmpty()) {
				state = SourceState.REFUSED_COVERAGE;
			} else if (!partialResidency.isEmpty()) {
				state = SourceState.PARTIAL_RESIDENCY;
			} else {
				state = SourceState.READY;
			}
			sourceResults.add(new SourceReadiness(
				entry.getKey().packedRegionX, entry.getKey().packedRegionY,
				coverage.getCoveredKeys(), missing, refused, partialResidency,
				coverage.spansLevels(), state));
		}
		return new LayeredPackedRegionRetirementReadiness(
			observedTick, currentOwnershipVersion, currentResidencyVersion,
			decisions.size(), sourceResults);
	}

	public long getObservedAtTick() {
		return observedAtTick;
	}

	public long getOwnershipVersion() {
		return ownershipVersion;
	}

	public long getResidencyMirrorVersion() {
		return residencyMirrorVersion;
	}

	public int getLogicalDecisionCount() {
		return logicalDecisionCount;
	}

	public List<SourceReadiness> getSources() {
		return sources;
	}

	public int getSourceCount() {
		return sources.size();
	}

	public int getReadySourceCount() {
		return readySourceCount;
	}

	public int getBlockedSourceCount() {
		return getSourceCount() - readySourceCount;
	}

	public enum SourceState {
		READY,
		INCOMPLETE_COVERAGE,
		REFUSED_COVERAGE,
		PARTIAL_RESIDENCY,
		PARTIAL_LEGACY_DOMAIN
	}

	/** One immutable packed-source result with no mutable Region handle. */
	public static final class SourceReadiness {
		private final int packedRegionX;
		private final int packedRegionY;
		private final List<WorldRegionKey> coveredLogicalRegions;
		private final List<WorldRegionKey> missingLogicalDecisions;
		private final List<WorldRegionKey> refusedLogicalDecisions;
		private final List<WorldRegionKey> partialResidencyLogicalDecisions;
		private final boolean spansLevels;
		private final SourceState sourceState;

		private SourceReadiness(
			final int packedRegionX,
			final int packedRegionY,
			final List<WorldRegionKey> coveredLogicalRegions,
			final List<WorldRegionKey> missingLogicalDecisions,
			final List<WorldRegionKey> refusedLogicalDecisions,
			final List<WorldRegionKey> partialResidencyLogicalDecisions,
			final boolean spansLevels,
			final SourceState sourceState) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.coveredLogicalRegions = immutableUniqueCopy(
				coveredLogicalRegions, "coveredLogicalRegions");
			this.missingLogicalDecisions = immutableUniqueCopy(
				missingLogicalDecisions, "missingLogicalDecisions");
			this.refusedLogicalDecisions = immutableUniqueCopy(
				refusedLogicalDecisions, "refusedLogicalDecisions");
			this.partialResidencyLogicalDecisions = immutableUniqueCopy(
				partialResidencyLogicalDecisions,
				"partialResidencyLogicalDecisions");
			this.spansLevels = spansLevels;
			this.sourceState = Objects.requireNonNull(sourceState, "sourceState");
		}

		private static List<WorldRegionKey> immutableUniqueCopy(
			final List<WorldRegionKey> values,
			final String label) {
			Set<WorldRegionKey> unique =
				new LinkedHashSet<WorldRegionKey>(values);
			if (unique.size() != values.size() || unique.contains(null)) {
				throw new IllegalArgumentException(label + " must be non-null and unique");
			}
			return Collections.unmodifiableList(
				new ArrayList<WorldRegionKey>(values));
		}

		public int getPackedRegionX() {
			return packedRegionX;
		}

		public int getPackedRegionY() {
			return packedRegionY;
		}

		public List<WorldRegionKey> getCoveredLogicalRegions() {
			return coveredLogicalRegions;
		}

		public List<WorldRegionKey> getMissingLogicalDecisions() {
			return missingLogicalDecisions;
		}

		public List<WorldRegionKey> getRefusedLogicalDecisions() {
			return refusedLogicalDecisions;
		}

		public List<WorldRegionKey> getPartialResidencyLogicalDecisions() {
			return partialResidencyLogicalDecisions;
		}

		public boolean spansLevels() {
			return spansLevels;
		}

		public SourceState getSourceState() {
			return sourceState;
		}

		public boolean isReady() {
			return sourceState == SourceState.READY;
		}
	}

	private static final class PackedSourceKey {
		private final int packedRegionX;
		private final int packedRegionY;

		private PackedSourceKey(
			final int packedRegionX,
			final int packedRegionY) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
		}

		@Override
		public boolean equals(final Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof PackedSourceKey)) {
				return false;
			}
			PackedSourceKey key = (PackedSourceKey) other;
			return packedRegionX == key.packedRegionX
				&& packedRegionY == key.packedRegionY;
		}

		@Override
		public int hashCode() {
			return 31 * packedRegionX + packedRegionY;
		}
	}
}
