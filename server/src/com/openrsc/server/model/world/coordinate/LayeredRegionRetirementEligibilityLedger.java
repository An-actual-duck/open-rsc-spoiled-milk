package com.openrsc.server.model.world.coordinate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant logical-Region pin and retirement-cooldown projection.
 *
 * <p>A positive global interest count is a logical pin. The first observed
 * transition to zero begins a tick-based cooldown, and a later global
 * acquisition cancels that cooldown. Expiry produces evidence for a future
 * source-level retirement arbiter; it cannot retain, load, release, unload, or
 * evict a Region.</p>
 */
public final class LayeredRegionRetirementEligibilityLedger {
	private final long minimumCooldownTicks;
	private final Map<WorldRegionKey, Integer> referenceCounts =
		new HashMap<WorldRegionKey, Integer>();
	private final Map<WorldRegionKey, ReleaseRecord> releases =
		new HashMap<WorldRegionKey, ReleaseRecord>();
	private long latestOwnershipVersion;
	private long latestObservationTick = -1L;

	public LayeredRegionRetirementEligibilityLedger(
		final long minimumCooldownTicks) {
		if (minimumCooldownTicks < 1L) {
			throw new IllegalArgumentException(
				"Retirement cooldown must be at least one tick");
		}
		this.minimumCooldownTicks = minimumCooldownTicks;
	}

	/**
	 * Applies one exact ownership transition without touching Region lifecycle.
	 */
	public synchronized void observeOwnershipChange(
		final LayeredRegionInterestOwnershipLedger.Change ownershipChange,
		final long currentTick) {
		LayeredRegionInterestOwnershipLedger.Change change =
			Objects.requireNonNull(ownershipChange, "ownershipChange");
		requireMonotonicTick(currentTick);
		if (change.getLedgerVersion() < latestOwnershipVersion
			|| !change.isNoOp()
				&& change.getLedgerVersion() <= latestOwnershipVersion) {
			throw new IllegalArgumentException(
				"Ownership changes must follow increasing ledger versions");
		}

		Set<WorldRegionKey> uniqueKeys = new HashSet<WorldRegionKey>();
		for (LayeredRegionInterestOwnershipLedger.Entry entry
			: change.getEntries()) {
			WorldRegionKey key = Objects.requireNonNull(
				entry.getLogicalRegionKey(), "logicalRegionKey");
			if (!uniqueKeys.add(key)) {
				throw new IllegalArgumentException(
					"Ownership change contains duplicate logical Region keys");
			}
			int expectedBefore = referenceCount(key);
			if (entry.getPreviousReferenceCount() != expectedBefore) {
				throw new IllegalStateException(
					"Retirement projection differs from ownership before-count");
			}
		}

		for (LayeredRegionInterestOwnershipLedger.Entry entry
			: change.getEntries()) {
			WorldRegionKey key = entry.getLogicalRegionKey();
			int after = entry.getCurrentReferenceCount();
			if (after == 0) {
				referenceCounts.remove(key);
				if (entry.isGloballyReleased()) {
					releases.put(key, new ReleaseRecord(
						change.getLedgerVersion(), currentTick,
						Math.addExact(currentTick, minimumCooldownTicks)));
				}
			} else {
				referenceCounts.put(key, Integer.valueOf(after));
				if (entry.isGloballyAcquired()) {
					releases.remove(key);
				}
			}
		}

		if (referenceCounts.size() != change.getReferencedRegionCount()) {
			throw new IllegalStateException(
				"Retirement projection differs from distinct ownership count");
		}
		if (!change.isNoOp()) {
			latestOwnershipVersion = change.getLedgerVersion();
		}
		latestObservationTick = currentTick;
	}

	/**
	 * Captures conservative retirement evidence at one monotonic server tick.
	 */
	public synchronized Snapshot snapshot(
		final LayeredRegionInterestOwnershipLedger.Snapshot ownershipSnapshot,
		final LayeredRegionResidencyMirror.Snapshot residencySnapshot,
		final long currentTick) {
		LayeredRegionInterestOwnershipLedger.Snapshot ownership =
			Objects.requireNonNull(ownershipSnapshot, "ownershipSnapshot");
		LayeredRegionResidencyMirror.Snapshot residency =
			Objects.requireNonNull(residencySnapshot, "residencySnapshot");
		requireMonotonicTick(currentTick);
		WorldRegionKey key = ownership.getLogicalRegionKey();
		if (!key.equals(residency.getLogicalRegionKey())) {
			throw new IllegalArgumentException(
				"Ownership and residency snapshots identify different Regions");
		}
		if (ownership.getLedgerVersion() < latestOwnershipVersion) {
			throw new IllegalArgumentException(
				"Ownership snapshot predates the retirement projection");
		}
		int referenceCount = referenceCount(key);
		if (ownership.getReferenceCount() != referenceCount) {
			throw new IllegalStateException(
				"Retirement projection differs from current ownership count");
		}

		ReleaseRecord release = releases.get(key);
		RetirementState state;
		if (referenceCount > 0) {
			if (release != null) {
				throw new IllegalStateException(
					"Pinned Region retains a stale retirement cooldown");
			}
			state = RetirementState.PINNED;
		} else if (!residency.isLegacySupported()) {
			state = RetirementState.UNSUPPORTED;
		} else if (residency.getResidentSourceCount() == 0) {
			state = RetirementState.NOT_RESIDENT;
		} else if (release == null) {
			state = RetirementState.UNTRACKED;
		} else if (currentTick < release.eligibleAtTick) {
			state = RetirementState.COOLING_DOWN;
		} else {
			state = RetirementState.RETIREMENT_ELIGIBLE;
		}

		latestObservationTick = currentTick;
		return new Snapshot(
			key, ownership.getLedgerVersion(), residency.getMirrorVersion(),
			currentTick, minimumCooldownTicks, referenceCount,
			residency.isLegacySupported(), residency.getSourceCount(),
			residency.getResidentSourceCount(), state,
			release == null ? null : Long.valueOf(release.ownershipVersion),
			release == null ? null : Long.valueOf(release.releasedAtTick),
			release == null ? null : Long.valueOf(release.eligibleAtTick));
	}

	public synchronized long getLatestOwnershipVersion() {
		return latestOwnershipVersion;
	}

	public synchronized int getReferencedRegionCount() {
		return referenceCounts.size();
	}

	public synchronized int getTrackedReleaseCount() {
		return releases.size();
	}

	/** Clears only projection state during world unload. */
	public synchronized boolean clear() {
		if (referenceCounts.isEmpty() && releases.isEmpty()
			&& latestOwnershipVersion == 0L && latestObservationTick == -1L) {
			return false;
		}
		referenceCounts.clear();
		releases.clear();
		latestOwnershipVersion = 0L;
		latestObservationTick = -1L;
		return true;
	}

	private void requireMonotonicTick(final long currentTick) {
		if (currentTick < 0L || currentTick < latestObservationTick) {
			throw new IllegalArgumentException(
				"Retirement observation tick must be non-negative and monotonic");
		}
	}

	private int referenceCount(final WorldRegionKey key) {
		Integer count = referenceCounts.get(key);
		return count == null ? 0 : count.intValue();
	}

	public enum RetirementState {
		PINNED,
		COOLING_DOWN,
		RETIREMENT_ELIGIBLE,
		NOT_RESIDENT,
		UNSUPPORTED,
		UNTRACKED
	}

	/** Immutable logical evidence; never an unload or eviction order. */
	public static final class Snapshot {
		private final WorldRegionKey logicalRegionKey;
		private final long ownershipVersion;
		private final long residencyMirrorVersion;
		private final long observedAtTick;
		private final long minimumCooldownTicks;
		private final int referenceCount;
		private final boolean legacySupported;
		private final int sourceCount;
		private final int residentSourceCount;
		private final RetirementState retirementState;
		private final Long releasedAtOwnershipVersion;
		private final Long releasedAtTick;
		private final Long eligibleAtTick;

		private Snapshot(
			final WorldRegionKey logicalRegionKey,
			final long ownershipVersion,
			final long residencyMirrorVersion,
			final long observedAtTick,
			final long minimumCooldownTicks,
			final int referenceCount,
			final boolean legacySupported,
			final int sourceCount,
			final int residentSourceCount,
			final RetirementState retirementState,
			final Long releasedAtOwnershipVersion,
			final Long releasedAtTick,
			final Long eligibleAtTick) {
			this.logicalRegionKey = Objects.requireNonNull(
				logicalRegionKey, "logicalRegionKey");
			this.ownershipVersion = ownershipVersion;
			this.residencyMirrorVersion = residencyMirrorVersion;
			this.observedAtTick = observedAtTick;
			this.minimumCooldownTicks = minimumCooldownTicks;
			this.referenceCount = referenceCount;
			this.legacySupported = legacySupported;
			this.sourceCount = sourceCount;
			this.residentSourceCount = residentSourceCount;
			this.retirementState = Objects.requireNonNull(
				retirementState, "retirementState");
			this.releasedAtOwnershipVersion = releasedAtOwnershipVersion;
			this.releasedAtTick = releasedAtTick;
			this.eligibleAtTick = eligibleAtTick;
		}

		public WorldRegionKey getLogicalRegionKey() {
			return logicalRegionKey;
		}

		public long getOwnershipVersion() {
			return ownershipVersion;
		}

		public long getResidencyMirrorVersion() {
			return residencyMirrorVersion;
		}

		public long getObservedAtTick() {
			return observedAtTick;
		}

		public long getMinimumCooldownTicks() {
			return minimumCooldownTicks;
		}

		public int getReferenceCount() {
			return referenceCount;
		}

		public boolean isLegacySupported() {
			return legacySupported;
		}

		public int getSourceCount() {
			return sourceCount;
		}

		public int getResidentSourceCount() {
			return residentSourceCount;
		}

		public RetirementState getRetirementState() {
			return retirementState;
		}

		public Long getReleasedAtOwnershipVersion() {
			return releasedAtOwnershipVersion;
		}

		public Long getReleasedAtTick() {
			return releasedAtTick;
		}

		public Long getEligibleAtTick() {
			return eligibleAtTick;
		}

		public long getRemainingCooldownTicks() {
			if (retirementState != RetirementState.COOLING_DOWN) {
				return 0L;
			}
			return eligibleAtTick.longValue() - observedAtTick;
		}

		public boolean isRetirementEligible() {
			return retirementState == RetirementState.RETIREMENT_ELIGIBLE;
		}
	}

	private static final class ReleaseRecord {
		private final long ownershipVersion;
		private final long releasedAtTick;
		private final long eligibleAtTick;

		private ReleaseRecord(
			final long ownershipVersion,
			final long releasedAtTick,
			final long eligibleAtTick) {
			this.ownershipVersion = ownershipVersion;
			this.releasedAtTick = releasedAtTick;
			this.eligibleAtTick = eligibleAtTick;
		}
	}
}
