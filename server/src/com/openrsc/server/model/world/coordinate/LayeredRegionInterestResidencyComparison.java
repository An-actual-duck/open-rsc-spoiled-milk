package com.openrsc.server.model.world.coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant comparison of one logical interest delta with Region residency.
 *
 * <p>Load and release candidates are evidence only. This value cannot create,
 * retain, evict, or unload a Region.</p>
 */
public final class LayeredRegionInterestResidencyComparison {
	private final WorldRegionInterestDelta interestDelta;
	private final long mirrorVersion;
	private final List<Entry> entries;
	private final List<Entry> loadCandidates;
	private final List<Entry> releaseCandidates;
	private final List<Entry> unsupportedCurrent;

	private LayeredRegionInterestResidencyComparison(
		final WorldRegionInterestDelta interestDelta,
		final long mirrorVersion,
		final List<Entry> entries,
		final List<Entry> loadCandidates,
		final List<Entry> releaseCandidates,
		final List<Entry> unsupportedCurrent) {
		this.interestDelta = interestDelta;
		this.mirrorVersion = mirrorVersion;
		this.entries = Collections.unmodifiableList(entries);
		this.loadCandidates = Collections.unmodifiableList(loadCandidates);
		this.releaseCandidates = Collections.unmodifiableList(releaseCandidates);
		this.unsupportedCurrent = Collections.unmodifiableList(unsupportedCurrent);
	}

	/**
	 * Composes snapshots in deterministic entered, retained, then exited order.
	 */
	public static LayeredRegionInterestResidencyComparison compare(
		final WorldRegionInterestDelta interestDelta,
		final List<LayeredRegionResidencyMirror.Snapshot> snapshots) {
		WorldRegionInterestDelta delta = Objects.requireNonNull(
			interestDelta, "interestDelta");
		Objects.requireNonNull(snapshots, "snapshots");
		List<WorldRegionKey> expectedKeys = expectedKeys(delta);
		if (snapshots.size() != expectedKeys.size()) {
			throw new IllegalArgumentException(
				"Residency snapshots must match the complete interest delta");
		}

		List<Entry> entries = new ArrayList<Entry>(snapshots.size());
		List<Entry> loads = new ArrayList<Entry>();
		List<Entry> releases = new ArrayList<Entry>();
		List<Entry> unsupported = new ArrayList<Entry>();
		Set<WorldRegionKey> uniqueKeys = new HashSet<WorldRegionKey>();
		Long version = null;
		for (int index = 0; index < snapshots.size(); index++) {
			LayeredRegionResidencyMirror.Snapshot snapshot = Objects.requireNonNull(
				snapshots.get(index), "snapshots[" + index + "]");
			WorldRegionKey expectedKey = expectedKeys.get(index);
			if (!expectedKey.equals(snapshot.getLogicalRegionKey())) {
				throw new IllegalArgumentException(
					"Residency snapshot order or logical identity differs from the delta");
			}
			if (!uniqueKeys.add(expectedKey)) {
				throw new IllegalArgumentException(
					"Interest delta contains duplicate logical Region keys");
			}
			if (version == null) {
				version = snapshot.getMirrorVersion();
			} else if (version.longValue() != snapshot.getMirrorVersion()) {
				throw new IllegalArgumentException(
					"Residency snapshots must share one mirror version");
			}

			InterestState interestState = interestState(
				index, delta.getEntered().size(), delta.getRetained().size());
			ResidencyState residencyState = residencyState(snapshot);
			Entry entry = new Entry(interestState, residencyState, snapshot);
			entries.add(entry);
			if (interestState != InterestState.EXITED) {
				if (residencyState == ResidencyState.MISSING
					|| residencyState == ResidencyState.PARTIAL) {
					loads.add(entry);
				} else if (residencyState == ResidencyState.UNSUPPORTED) {
					unsupported.add(entry);
				}
			} else if (snapshot.getResidentSourceCount() > 0) {
				releases.add(entry);
			}
		}

		if (version == null) {
			throw new IllegalArgumentException(
				"Interest delta must contain at least one logical Region");
		}
		return new LayeredRegionInterestResidencyComparison(
			delta, version.longValue(), entries, loads, releases, unsupported);
	}

	private static List<WorldRegionKey> expectedKeys(
		final WorldRegionInterestDelta delta) {
		List<WorldRegionKey> keys = new ArrayList<WorldRegionKey>(
			delta.getEntered().size() + delta.getRetained().size()
				+ delta.getExited().size());
		keys.addAll(delta.getEntered());
		keys.addAll(delta.getRetained());
		keys.addAll(delta.getExited());
		return keys;
	}

	private static InterestState interestState(
		final int index,
		final int enteredCount,
		final int retainedCount) {
		if (index < enteredCount) {
			return InterestState.ENTERED;
		}
		if (index < enteredCount + retainedCount) {
			return InterestState.RETAINED;
		}
		return InterestState.EXITED;
	}

	private static ResidencyState residencyState(
		final LayeredRegionResidencyMirror.Snapshot snapshot) {
		if (!snapshot.isLegacySupported()) {
			return ResidencyState.UNSUPPORTED;
		}
		if (snapshot.isResident()) {
			return ResidencyState.RESIDENT;
		}
		return snapshot.getResidentSourceCount() == 0
			? ResidencyState.MISSING : ResidencyState.PARTIAL;
	}

	public WorldRegionInterestDelta getInterestDelta() {
		return interestDelta;
	}

	public long getMirrorVersion() {
		return mirrorVersion;
	}

	public List<Entry> getEntries() {
		return entries;
	}

	public List<Entry> getLoadCandidates() {
		return loadCandidates;
	}

	public List<Entry> getReleaseCandidates() {
		return releaseCandidates;
	}

	public List<Entry> getUnsupportedCurrent() {
		return unsupportedCurrent;
	}

	public int getResidentCurrentCount() {
		int count = 0;
		for (Entry entry : entries) {
			if (entry.getInterestState() != InterestState.EXITED
				&& entry.getResidencyState() == ResidencyState.RESIDENT) {
				count++;
			}
		}
		return count;
	}

	public int getPartialCurrentCount() {
		int count = 0;
		for (Entry entry : entries) {
			if (entry.getInterestState() != InterestState.EXITED
				&& entry.getResidencyState() == ResidencyState.PARTIAL) {
				count++;
			}
		}
		return count;
	}

	public int getMissingCurrentCount() {
		int count = 0;
		for (Entry entry : entries) {
			if (entry.getInterestState() != InterestState.EXITED
				&& entry.getResidencyState() == ResidencyState.MISSING) {
				count++;
			}
		}
		return count;
	}

	@Override
	public String toString() {
		return "LayeredRegionInterestResidencyComparison{mirrorVersion="
			+ mirrorVersion + ", entries=" + entries.size() + ", loadCandidates="
			+ loadCandidates.size() + ", releaseCandidates="
			+ releaseCandidates.size() + ", unsupportedCurrent="
			+ unsupportedCurrent.size() + "}";
	}

	public enum InterestState {
		ENTERED,
		RETAINED,
		EXITED
	}

	public enum ResidencyState {
		RESIDENT,
		PARTIAL,
		MISSING,
		UNSUPPORTED
	}

	/** One immutable interest classification paired with a residency snapshot. */
	public static final class Entry {
		private final InterestState interestState;
		private final ResidencyState residencyState;
		private final LayeredRegionResidencyMirror.Snapshot residencySnapshot;

		private Entry(
			final InterestState interestState,
			final ResidencyState residencyState,
			final LayeredRegionResidencyMirror.Snapshot residencySnapshot) {
			this.interestState = Objects.requireNonNull(
				interestState, "interestState");
			this.residencyState = Objects.requireNonNull(
				residencyState, "residencyState");
			this.residencySnapshot = Objects.requireNonNull(
				residencySnapshot, "residencySnapshot");
		}

		public WorldRegionKey getLogicalRegionKey() {
			return residencySnapshot.getLogicalRegionKey();
		}

		public InterestState getInterestState() {
			return interestState;
		}

		public ResidencyState getResidencyState() {
			return residencyState;
		}

		public LayeredRegionResidencyMirror.Snapshot getResidencySnapshot() {
			return residencySnapshot;
		}

		@Override
		public String toString() {
			return "Entry{logicalRegionKey=" + getLogicalRegionKey()
				+ ", interestState=" + interestState + ", residencyState="
				+ residencyState + "}";
		}
	}
}
