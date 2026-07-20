package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable count-only comparison of authored definitions with runtime
 * identity metadata for exact retirement-safety sources.
 *
 * <p>This observation distinguishes origin, roaming, inactive-respawn,
 * replacement, absent, duplicate, stale-generation, and unrecognized states.
 * It retains no entity or lifecycle handle and cannot alter runtime state.</p>
 */
public final class LayeredPackedRegionAuthoredProvenanceObservation {
	public static final int MAXIMUM_RUNTIME_INSTANCES = 524288;

	private final long generation;
	private final long safetyObservedAtTick;
	private final long runtimeObservedAtTick;
	private final List<SourceObservation> sources;
	private final MutableCounts totals;

	private LayeredPackedRegionAuthoredProvenanceObservation(
		final long generation,
		final long safetyObservedAtTick,
		final long runtimeObservedAtTick,
		final List<SourceObservation> sources,
		final MutableCounts totals) {
		this.generation = generation;
		this.safetyObservedAtTick = safetyObservedAtTick;
		this.runtimeObservedAtTick = runtimeObservedAtTick;
		this.sources = Collections.unmodifiableList(sources);
		this.totals = totals.copy();
	}

	public static Builder builder(
		final LayeredPackedRegionAuthoredPlacementManifest manifest,
		final LayeredPackedRegionRetirementSafetyAssessment safety,
		final long runtimeObservedAtTick) {
		return new Builder(manifest, safety, runtimeObservedAtTick);
	}

	public long getGeneration() { return generation; }
	public long getSafetyObservedAtTick() { return safetyObservedAtTick; }
	public long getRuntimeObservedAtTick() { return runtimeObservedAtTick; }
	public List<SourceObservation> getSources() { return sources; }
	public int getSourceCount() { return sources.size(); }
	public int getExpectedPlacementCount() {
		return totals.expectedPlacementCount;
	}
	public int getMatchedIdentityCount() { return totals.matchedIdentityCount; }
	public int getAbsentIdentityCount() { return totals.absentIdentityCount; }
	public int getDuplicateIdentityCount() { return totals.duplicateIdentityCount; }
	public int getRuntimeInstanceCount() { return totals.runtimeInstanceCount; }
	public int getActiveRuntimeInstanceCount() {
		return totals.activeRuntimeInstanceCount;
	}
	public int getInactiveRuntimeInstanceCount() {
		return totals.inactiveRuntimeInstanceCount;
	}
	public int getAtAuthoredSourceInstanceCount() {
		return totals.atAuthoredSourceInstanceCount;
	}
	public int getAwayFromAuthoredSourceInstanceCount() {
		return totals.awayFromAuthoredSourceInstanceCount;
	}
	public int getReplacementObjectInstanceCount() {
		return totals.replacementObjectInstanceCount;
	}
	public int getStaleGenerationInstanceCount() {
		return totals.staleGenerationInstanceCount;
	}
	public int getUnrecognizedIdentityInstanceCount() {
		return totals.unrecognizedIdentityInstanceCount;
	}
	public int getExpectedSceneryCount() { return totals.expectedSceneryCount; }
	public int getExpectedBoundaryCount() { return totals.expectedBoundaryCount; }
	public int getExpectedNpcSpawnCount() { return totals.expectedNpcSpawnCount; }
	public int getExpectedGroundItemSpawnCount() {
		return totals.expectedGroundItemSpawnCount;
	}
	public int getExpectedHarvestingSceneryCount() {
		return totals.expectedHarvestingSceneryCount;
	}
	public int getRuntimeSceneryCount() { return totals.runtimeSceneryCount; }
	public int getRuntimeBoundaryCount() { return totals.runtimeBoundaryCount; }
	public int getRuntimeNpcSpawnCount() { return totals.runtimeNpcSpawnCount; }
	public int getRuntimeGroundItemSpawnCount() {
		return totals.runtimeGroundItemSpawnCount;
	}
	public int getRuntimeHarvestingSceneryCount() {
		return totals.runtimeHarvestingSceneryCount;
	}

	/** One count-only provenance result for an exact packed source. */
	public static final class SourceObservation {
		private final int packedRegionX;
		private final int packedRegionY;
		private final MutableCounts counts;

		private SourceObservation(
			final int packedRegionX,
			final int packedRegionY,
			final MutableCounts counts) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.counts = counts.copy();
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getExpectedPlacementCount() {
			return counts.expectedPlacementCount;
		}
		public int getMatchedIdentityCount() {
			return counts.matchedIdentityCount;
		}
		public int getAbsentIdentityCount() {
			return counts.absentIdentityCount;
		}
		public int getDuplicateIdentityCount() {
			return counts.duplicateIdentityCount;
		}
		public int getRuntimeInstanceCount() {
			return counts.runtimeInstanceCount;
		}
		public int getActiveRuntimeInstanceCount() {
			return counts.activeRuntimeInstanceCount;
		}
		public int getInactiveRuntimeInstanceCount() {
			return counts.inactiveRuntimeInstanceCount;
		}
		public int getAtAuthoredSourceInstanceCount() {
			return counts.atAuthoredSourceInstanceCount;
		}
		public int getAwayFromAuthoredSourceInstanceCount() {
			return counts.awayFromAuthoredSourceInstanceCount;
		}
		public int getReplacementObjectInstanceCount() {
			return counts.replacementObjectInstanceCount;
		}
		public int getStaleGenerationInstanceCount() {
			return counts.staleGenerationInstanceCount;
		}
		public int getUnrecognizedIdentityInstanceCount() {
			return counts.unrecognizedIdentityInstanceCount;
		}
		public int getExpectedSceneryCount() {
			return counts.expectedSceneryCount;
		}
		public int getExpectedBoundaryCount() {
			return counts.expectedBoundaryCount;
		}
		public int getExpectedNpcSpawnCount() {
			return counts.expectedNpcSpawnCount;
		}
		public int getExpectedGroundItemSpawnCount() {
			return counts.expectedGroundItemSpawnCount;
		}
		public int getExpectedHarvestingSceneryCount() {
			return counts.expectedHarvestingSceneryCount;
		}
		public int getRuntimeSceneryCount() {
			return counts.runtimeSceneryCount;
		}
		public int getRuntimeBoundaryCount() {
			return counts.runtimeBoundaryCount;
		}
		public int getRuntimeNpcSpawnCount() {
			return counts.runtimeNpcSpawnCount;
		}
		public int getRuntimeGroundItemSpawnCount() {
			return counts.runtimeGroundItemSpawnCount;
		}
		public int getRuntimeHarvestingSceneryCount() {
			return counts.runtimeHarvestingSceneryCount;
		}
	}

	/** Runtime-only accumulator that accepts detached primitive observations. */
	public static final class Builder {
		private final LayeredPackedRegionAuthoredPlacementManifest manifest;
		private final long safetyObservedAtTick;
		private final long runtimeObservedAtTick;
		private final Map<Long, MutableSource> sources =
			new LinkedHashMap<Long, MutableSource>();
		private final Map<LayeredAuthoredPlacementIdentity, MutableExpected>
			expected =
				new LinkedHashMap<LayeredAuthoredPlacementIdentity, MutableExpected>();
		private int runtimeRecords;
		private boolean built;

		private Builder(
			final LayeredPackedRegionAuthoredPlacementManifest manifest,
			final LayeredPackedRegionRetirementSafetyAssessment safety,
			final long runtimeObservedAtTick) {
			if (manifest == null) {
				throw new NullPointerException("manifest");
			}
			if (safety == null) {
				throw new NullPointerException("safety");
			}
			if (runtimeObservedAtTick < 0L) {
				throw new IllegalArgumentException(
					"Runtime observation tick must not be negative");
			}
			this.manifest = manifest;
			this.safetyObservedAtTick = safety.getObservedAtTick();
			this.runtimeObservedAtTick = runtimeObservedAtTick;
			for (LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment
				safetySource : safety.getSources()) {
				MutableSource source = new MutableSource(
					safetySource.getPackedRegionX(),
					safetySource.getPackedRegionY());
				sources.put(Long.valueOf(packedSourceKey(
					source.packedRegionX, source.packedRegionY)), source);
				LayeredPackedRegionAuthoredPlacementManifest.PackedSourceManifest
					definitions = manifest.findSource(
						source.packedRegionX, source.packedRegionY);
				if (definitions == null) {
					continue;
				}
				for (LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement
					placement : definitions.getPlacements()) {
					MutableExpected value = new MutableExpected(
						source, placement.getIdentity(),
						placement.getConstructedEntityId());
					if (expected.put(placement.getIdentity(), value) != null) {
						throw new IllegalArgumentException(
							"Duplicate authored placement identity in manifest");
					}
					source.counts.recordExpected(placement.getKind());
				}
			}
		}

		public Builder recordRuntimeInstance(
			final LayeredAuthoredPlacementIdentity identity,
			final int runtimeEntityId,
			final int currentPackedRegionX,
			final int currentPackedRegionY,
			final boolean active) {
			checkOpen();
			if (identity == null) {
				throw new NullPointerException("identity");
			}
			if (runtimeEntityId < 0 || currentPackedRegionX < 0
				|| currentPackedRegionY < 0) {
				throw new IllegalArgumentException(
					"Runtime identity observations must not be negative");
			}
			if (runtimeRecords >= MAXIMUM_RUNTIME_INSTANCES) {
				throw new IllegalArgumentException(
					"Runtime provenance observation exceeds its budget");
			}
			runtimeRecords = Math.incrementExact(runtimeRecords);
			MutableSource source = sources.get(Long.valueOf(packedSourceKey(
				identity.getPackedRegionX(), identity.getPackedRegionY())));
			if (source == null) {
				return this;
			}
			if (identity.getGeneration() != manifest.getGeneration()) {
				source.counts.staleGenerationInstanceCount = Math.incrementExact(
					source.counts.staleGenerationInstanceCount);
				return this;
			}
			MutableExpected value = expected.get(identity);
			if (value == null) {
				source.counts.unrecognizedIdentityInstanceCount =
					Math.incrementExact(
						source.counts.unrecognizedIdentityInstanceCount);
				return this;
			}
			value.record(
				runtimeEntityId, currentPackedRegionX,
				currentPackedRegionY, active);
			return this;
		}

		public LayeredPackedRegionAuthoredProvenanceObservation build() {
			checkOpen();
			built = true;
			for (MutableExpected value : expected.values()) {
				value.finish();
			}
			List<SourceObservation> immutable =
				new ArrayList<SourceObservation>(sources.size());
			MutableCounts totals = new MutableCounts();
			for (MutableSource source : sources.values()) {
				immutable.add(new SourceObservation(
					source.packedRegionX, source.packedRegionY,
					source.counts));
				totals.add(source.counts);
			}
			return new LayeredPackedRegionAuthoredProvenanceObservation(
				manifest.getGeneration(), safetyObservedAtTick,
				runtimeObservedAtTick, immutable, totals);
		}

		private void checkOpen() {
			if (built) {
				throw new IllegalStateException(
					"Runtime provenance builder is already complete");
			}
		}
	}

	private static final class MutableExpected {
		private final MutableSource source;
		private final LayeredAuthoredPlacementIdentity identity;
		private final int expectedEntityId;
		private int instanceCount;

		private MutableExpected(
			final MutableSource source,
			final LayeredAuthoredPlacementIdentity identity,
			final int expectedEntityId) {
			this.source = source;
			this.identity = identity;
			this.expectedEntityId = expectedEntityId;
		}

		private void record(
			final int runtimeEntityId,
			final int currentPackedRegionX,
			final int currentPackedRegionY,
			final boolean active) {
			instanceCount = Math.incrementExact(instanceCount);
			source.counts.runtimeInstanceCount = Math.incrementExact(
				source.counts.runtimeInstanceCount);
			source.counts.recordRuntime(identity.getConstructionKind());
			if (active) {
				source.counts.activeRuntimeInstanceCount = Math.incrementExact(
					source.counts.activeRuntimeInstanceCount);
				if (currentPackedRegionX == identity.getPackedRegionX()
					&& currentPackedRegionY == identity.getPackedRegionY()) {
					source.counts.atAuthoredSourceInstanceCount =
						Math.incrementExact(
							source.counts.atAuthoredSourceInstanceCount);
				} else {
					source.counts.awayFromAuthoredSourceInstanceCount =
						Math.incrementExact(
							source.counts.awayFromAuthoredSourceInstanceCount);
				}
			} else {
				source.counts.inactiveRuntimeInstanceCount = Math.incrementExact(
					source.counts.inactiveRuntimeInstanceCount);
			}
			if (active && isObjectFamily(identity.getConstructionKind())
				&& runtimeEntityId != expectedEntityId) {
				source.counts.replacementObjectInstanceCount =
					Math.incrementExact(
						source.counts.replacementObjectInstanceCount);
			}
		}

		private void finish() {
			if (instanceCount == 0) {
				source.counts.absentIdentityCount = Math.incrementExact(
					source.counts.absentIdentityCount);
			} else if (instanceCount == 1) {
				source.counts.matchedIdentityCount = Math.incrementExact(
					source.counts.matchedIdentityCount);
			} else {
				source.counts.duplicateIdentityCount = Math.incrementExact(
					source.counts.duplicateIdentityCount);
			}
		}
	}

	private static final class MutableSource {
		private final int packedRegionX;
		private final int packedRegionY;
		private final MutableCounts counts = new MutableCounts();

		private MutableSource(
			final int packedRegionX,
			final int packedRegionY) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
		}
	}

	private static final class MutableCounts {
		private int expectedPlacementCount;
		private int matchedIdentityCount;
		private int absentIdentityCount;
		private int duplicateIdentityCount;
		private int runtimeInstanceCount;
		private int activeRuntimeInstanceCount;
		private int inactiveRuntimeInstanceCount;
		private int atAuthoredSourceInstanceCount;
		private int awayFromAuthoredSourceInstanceCount;
		private int replacementObjectInstanceCount;
		private int staleGenerationInstanceCount;
		private int unrecognizedIdentityInstanceCount;
		private int expectedSceneryCount;
		private int expectedBoundaryCount;
		private int expectedNpcSpawnCount;
		private int expectedGroundItemSpawnCount;
		private int expectedHarvestingSceneryCount;
		private int runtimeSceneryCount;
		private int runtimeBoundaryCount;
		private int runtimeNpcSpawnCount;
		private int runtimeGroundItemSpawnCount;
		private int runtimeHarvestingSceneryCount;

		private void recordExpected(final ConstructionKind kind) {
			expectedPlacementCount = Math.incrementExact(expectedPlacementCount);
			switch (kind) {
				case SCENERY:
					expectedSceneryCount = Math.incrementExact(
						expectedSceneryCount);
					break;
				case BOUNDARY:
					expectedBoundaryCount = Math.incrementExact(
						expectedBoundaryCount);
					break;
				case NPC_SPAWN:
					expectedNpcSpawnCount = Math.incrementExact(
						expectedNpcSpawnCount);
					break;
				case GROUND_ITEM_SPAWN:
					expectedGroundItemSpawnCount = Math.incrementExact(
						expectedGroundItemSpawnCount);
					break;
				case HARVESTING_SCENERY:
					expectedHarvestingSceneryCount = Math.incrementExact(
						expectedHarvestingSceneryCount);
					break;
				default:
					throw new IllegalArgumentException(
						"Unsupported kind: " + kind);
			}
		}

		private void recordRuntime(final ConstructionKind kind) {
			switch (kind) {
				case SCENERY:
					runtimeSceneryCount = Math.incrementExact(
						runtimeSceneryCount);
					break;
				case BOUNDARY:
					runtimeBoundaryCount = Math.incrementExact(
						runtimeBoundaryCount);
					break;
				case NPC_SPAWN:
					runtimeNpcSpawnCount = Math.incrementExact(
						runtimeNpcSpawnCount);
					break;
				case GROUND_ITEM_SPAWN:
					runtimeGroundItemSpawnCount = Math.incrementExact(
						runtimeGroundItemSpawnCount);
					break;
				case HARVESTING_SCENERY:
					runtimeHarvestingSceneryCount = Math.incrementExact(
						runtimeHarvestingSceneryCount);
					break;
				default:
					throw new IllegalArgumentException(
						"Unsupported kind: " + kind);
			}
		}

		private void add(final MutableCounts other) {
			expectedPlacementCount = Math.addExact(expectedPlacementCount, other.expectedPlacementCount);
			matchedIdentityCount = Math.addExact(matchedIdentityCount, other.matchedIdentityCount);
			absentIdentityCount = Math.addExact(absentIdentityCount, other.absentIdentityCount);
			duplicateIdentityCount = Math.addExact(duplicateIdentityCount, other.duplicateIdentityCount);
			runtimeInstanceCount = Math.addExact(runtimeInstanceCount, other.runtimeInstanceCount);
			activeRuntimeInstanceCount = Math.addExact(activeRuntimeInstanceCount, other.activeRuntimeInstanceCount);
			inactiveRuntimeInstanceCount = Math.addExact(inactiveRuntimeInstanceCount, other.inactiveRuntimeInstanceCount);
			atAuthoredSourceInstanceCount = Math.addExact(atAuthoredSourceInstanceCount, other.atAuthoredSourceInstanceCount);
			awayFromAuthoredSourceInstanceCount = Math.addExact(awayFromAuthoredSourceInstanceCount, other.awayFromAuthoredSourceInstanceCount);
			replacementObjectInstanceCount = Math.addExact(replacementObjectInstanceCount, other.replacementObjectInstanceCount);
			staleGenerationInstanceCount = Math.addExact(staleGenerationInstanceCount, other.staleGenerationInstanceCount);
			unrecognizedIdentityInstanceCount = Math.addExact(unrecognizedIdentityInstanceCount, other.unrecognizedIdentityInstanceCount);
			expectedSceneryCount = Math.addExact(expectedSceneryCount, other.expectedSceneryCount);
			expectedBoundaryCount = Math.addExact(expectedBoundaryCount, other.expectedBoundaryCount);
			expectedNpcSpawnCount = Math.addExact(expectedNpcSpawnCount, other.expectedNpcSpawnCount);
			expectedGroundItemSpawnCount = Math.addExact(expectedGroundItemSpawnCount, other.expectedGroundItemSpawnCount);
			expectedHarvestingSceneryCount = Math.addExact(expectedHarvestingSceneryCount, other.expectedHarvestingSceneryCount);
			runtimeSceneryCount = Math.addExact(runtimeSceneryCount, other.runtimeSceneryCount);
			runtimeBoundaryCount = Math.addExact(runtimeBoundaryCount, other.runtimeBoundaryCount);
			runtimeNpcSpawnCount = Math.addExact(runtimeNpcSpawnCount, other.runtimeNpcSpawnCount);
			runtimeGroundItemSpawnCount = Math.addExact(runtimeGroundItemSpawnCount, other.runtimeGroundItemSpawnCount);
			runtimeHarvestingSceneryCount = Math.addExact(runtimeHarvestingSceneryCount, other.runtimeHarvestingSceneryCount);
		}

		private MutableCounts copy() {
			MutableCounts copy = new MutableCounts();
			copy.add(this);
			return copy;
		}
	}

	private static boolean isObjectFamily(final ConstructionKind kind) {
		return kind == ConstructionKind.SCENERY
			|| kind == ConstructionKind.BOUNDARY
			|| kind == ConstructionKind.HARVESTING_SCENERY;
	}

	private static long packedSourceKey(
		final int packedRegionX,
		final int packedRegionY) {
		return ((long) packedRegionX << 32)
			^ (packedRegionY & 0xFFFFFFFFL);
	}
}
