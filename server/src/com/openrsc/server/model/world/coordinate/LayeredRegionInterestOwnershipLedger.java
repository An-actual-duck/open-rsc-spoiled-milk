package com.openrsc.server.model.world.coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant, process-local ownership model for logical Region interest.
 *
 * <p>Owner tokens are allocated by this ledger and never reused. They are not
 * database IDs, username hashes, or reusable entity indexes. The ledger only
 * counts interest references; it cannot load, retain, release, or evict a
 * Region.</p>
 */
public final class LayeredRegionInterestOwnershipLedger {
	private final Map<OwnerToken, OwnerState> owners =
		new HashMap<OwnerToken, OwnerState>();
	private final Map<WorldRegionKey, Integer> referenceCounts =
		new HashMap<WorldRegionKey, Integer>();
	private long nextOwnerToken = 1L;
	private long version;

	/** Opens one identity for a process-local interest source. */
	public synchronized OwnerToken openOwner() {
		if (nextOwnerToken <= 0L || nextOwnerToken == Long.MAX_VALUE) {
			throw new IllegalStateException("Layered interest owner tokens exhausted");
		}
		OwnerToken ownerToken = new OwnerToken(this, nextOwnerToken);
		nextOwnerToken = Math.addExact(nextOwnerToken, 1L);
		if (owners.put(ownerToken, OwnerState.empty()) != null) {
			throw new IllegalStateException("Layered interest owner token was reused");
		}
		version = Math.addExact(version, 1L);
		return ownerToken;
	}

	/**
	 * Replaces one owner's complete logical interest window atomically.
	 *
	 * <p>The caller-owned limit is an allocation budget, not a world-capacity
	 * limit.</p>
	 */
	public synchronized Change synchronizeOwner(
		final OwnerToken ownerToken,
		final WorldRegionWindow currentWindow,
		final int maximumRegionsPerWindow) {
		OwnerState previous = requireOpenOwner(ownerToken);
		WorldRegionWindow current = Objects.requireNonNull(
			currentWindow, "currentWindow");
		List<WorldRegionKey> currentKeys = WorldRegionInterestDelta.materializeKeys(
			current, maximumRegionsPerWindow);
		return replace(ownerToken, previous, current, currentKeys, false);
	}

	/**
	 * Closes one owner and removes all of its references.
	 *
	 * <p>A repeated close is an idempotent no-op so duplicated disconnect cleanup
	 * cannot decrement another owner's references.</p>
	 */
	public synchronized Change closeOwner(final OwnerToken ownerToken) {
		requireLedgerToken(ownerToken);
		if (ownerToken.closed) {
			return Change.closedNoOp(
				ownerToken.sequence, version, owners.size(), referenceCounts.size());
		}
		OwnerState previous = owners.get(ownerToken);
		if (previous == null) {
			throw new IllegalStateException(
				"Open owner token is missing from its allocating ledger");
		}
		return replace(
			ownerToken, previous, null, Collections.<WorldRegionKey>emptyList(), true);
	}

	/** Captures one immutable count without exposing owner identity. */
	public synchronized Snapshot snapshot(final WorldRegionKey logicalRegionKey) {
		WorldRegionKey key = Objects.requireNonNull(
			logicalRegionKey, "logicalRegionKey");
		return new Snapshot(key, version, referenceCount(key));
	}

	public synchronized long getVersion() {
		return version;
	}

	public synchronized int getOpenOwnerCount() {
		return owners.size();
	}

	public synchronized int getReferencedRegionCount() {
		return referenceCounts.size();
	}

	/** Clears process-local ownership during a future authoritative world unload. */
	public synchronized boolean clear() {
		if (owners.isEmpty() && referenceCounts.isEmpty()) {
			return false;
		}
		for (OwnerToken ownerToken : owners.keySet()) {
			ownerToken.closed = true;
		}
		owners.clear();
		referenceCounts.clear();
		version = Math.addExact(version, 1L);
		return true;
	}

	private Change replace(
		final OwnerToken ownerToken,
		final OwnerState previous,
		final WorldRegionWindow currentWindow,
		final List<WorldRegionKey> currentKeys,
		final boolean close) {
		List<WorldRegionKey> previousKeys = previous.keys;
		Set<WorldRegionKey> previousSet =
			new LinkedHashSet<WorldRegionKey>(previousKeys);
		Set<WorldRegionKey> currentSet =
			new LinkedHashSet<WorldRegionKey>(currentKeys);
		if (previousSet.size() != previousKeys.size()
			|| currentSet.size() != currentKeys.size()) {
			throw new IllegalStateException(
				"Logical interest window contains duplicate Region keys");
		}

		List<WorldRegionKey> entered = new ArrayList<WorldRegionKey>();
		List<WorldRegionKey> retained = new ArrayList<WorldRegionKey>();
		for (WorldRegionKey key : currentKeys) {
			if (previousSet.contains(key)) {
				retained.add(key);
			} else {
				entered.add(key);
			}
		}
		List<WorldRegionKey> exited = new ArrayList<WorldRegionKey>();
		for (WorldRegionKey key : previousKeys) {
			if (!currentSet.contains(key)) {
				exited.add(key);
			}
		}

		List<Entry> entries = new ArrayList<Entry>(
			entered.size() + retained.size() + exited.size());
		for (WorldRegionKey key : entered) {
			int before = referenceCount(key);
			int after = Math.addExact(before, 1);
			referenceCounts.put(key, after);
			entries.add(new Entry(key, InterestState.ENTERED, before, after));
		}
		for (WorldRegionKey key : retained) {
			int count = requirePositiveReferenceCount(key);
			entries.add(new Entry(key, InterestState.RETAINED, count, count));
		}
		for (WorldRegionKey key : exited) {
			int before = requirePositiveReferenceCount(key);
			int after = before - 1;
			if (after == 0) {
				referenceCounts.remove(key);
			} else {
				referenceCounts.put(key, after);
			}
			entries.add(new Entry(key, InterestState.EXITED, before, after));
		}

		if (close) {
			owners.remove(ownerToken);
			ownerToken.closed = true;
		} else {
			owners.put(ownerToken, new OwnerState(currentWindow, currentKeys));
		}
		if (close || !entered.isEmpty() || !exited.isEmpty()) {
			version = Math.addExact(version, 1L);
		}
		return new Change(
			ownerToken.sequence, previous.window, currentWindow, version, owners.size(),
			referenceCounts.size(), close,
			close || !entered.isEmpty() || !exited.isEmpty(), entries);
	}

	private OwnerState requireOpenOwner(final OwnerToken ownerToken) {
		requireLedgerToken(ownerToken);
		if (ownerToken.closed) {
			throw new IllegalArgumentException("Owner token is closed");
		}
		OwnerState owner = owners.get(ownerToken);
		if (owner == null) {
			throw new IllegalStateException(
				"Open owner token is missing from its allocating ledger");
		}
		return owner;
	}

	private void requireLedgerToken(final OwnerToken ownerToken) {
		OwnerToken token = Objects.requireNonNull(ownerToken, "ownerToken");
		if (token.ledger != this) {
			throw new IllegalArgumentException(
				"Owner token belongs to a different interest ledger");
		}
	}

	private int requirePositiveReferenceCount(final WorldRegionKey key) {
		int count = referenceCount(key);
		if (count < 1) {
			throw new IllegalStateException(
				"Open owner refers to a Region absent from the global ledger");
		}
		return count;
	}

	private int referenceCount(final WorldRegionKey key) {
		Integer count = referenceCounts.get(key);
		return count == null ? 0 : count.intValue();
	}

	private static final class OwnerState {
		private final WorldRegionWindow window;
		private final List<WorldRegionKey> keys;

		private OwnerState(
			final WorldRegionWindow window,
			final List<WorldRegionKey> keys) {
			this.window = window;
			this.keys = Collections.unmodifiableList(
				new ArrayList<WorldRegionKey>(keys));
		}

		private static OwnerState empty() {
			return new OwnerState(null, Collections.<WorldRegionKey>emptyList());
		}
	}

	/** Opaque, ledger-bound identity for one process-local interest source. */
	public static final class OwnerToken {
		private final LayeredRegionInterestOwnershipLedger ledger;
		private final long sequence;
		private volatile boolean closed;

		private OwnerToken(
			final LayeredRegionInterestOwnershipLedger ledger,
			final long sequence) {
			this.ledger = ledger;
			this.sequence = sequence;
		}

		public long getSequence() {
			return sequence;
		}

		public boolean isClosed() {
			return closed;
		}
	}

	public enum InterestState {
		ENTERED,
		RETAINED,
		EXITED
	}

	/** One immutable before/after reference-count transition. */
	public static final class Entry {
		private final WorldRegionKey logicalRegionKey;
		private final InterestState interestState;
		private final int previousReferenceCount;
		private final int currentReferenceCount;

		private Entry(
			final WorldRegionKey logicalRegionKey,
			final InterestState interestState,
			final int previousReferenceCount,
			final int currentReferenceCount) {
			this.logicalRegionKey = Objects.requireNonNull(
				logicalRegionKey, "logicalRegionKey");
			this.interestState = Objects.requireNonNull(
				interestState, "interestState");
			if (previousReferenceCount < 0 || currentReferenceCount < 0) {
				throw new IllegalArgumentException(
					"Reference counts must not be negative");
			}
			if (interestState == InterestState.ENTERED
				&& currentReferenceCount != previousReferenceCount + 1
				|| interestState == InterestState.RETAINED
				&& currentReferenceCount != previousReferenceCount
				|| interestState == InterestState.EXITED
				&& currentReferenceCount != previousReferenceCount - 1) {
				throw new IllegalArgumentException(
					"Reference-count transition differs from its interest state");
			}
			this.previousReferenceCount = previousReferenceCount;
			this.currentReferenceCount = currentReferenceCount;
		}

		public WorldRegionKey getLogicalRegionKey() {
			return logicalRegionKey;
		}

		public InterestState getInterestState() {
			return interestState;
		}

		public int getPreviousReferenceCount() {
			return previousReferenceCount;
		}

		public int getCurrentReferenceCount() {
			return currentReferenceCount;
		}

		public boolean isGloballyAcquired() {
			return interestState == InterestState.ENTERED
				&& previousReferenceCount == 0;
		}

		public boolean isSharedAcquisition() {
			return interestState == InterestState.ENTERED
				&& previousReferenceCount > 0;
		}

		public boolean isGloballyReleased() {
			return interestState == InterestState.EXITED
				&& currentReferenceCount == 0;
		}

		public boolean isSharedRelease() {
			return interestState == InterestState.EXITED
				&& currentReferenceCount > 0;
		}
	}

	/** Immutable result of one complete owner-window replacement or close. */
	public static final class Change {
		private final long ownerSequence;
		private final WorldRegionWindow previousWindow;
		private final WorldRegionWindow currentWindow;
		private final long ledgerVersion;
		private final int openOwnerCount;
		private final int referencedRegionCount;
		private final boolean ownerClosed;
		private final boolean stateChanged;
		private final List<Entry> entries;

		private Change(
			final long ownerSequence,
			final WorldRegionWindow previousWindow,
			final WorldRegionWindow currentWindow,
			final long ledgerVersion,
			final int openOwnerCount,
			final int referencedRegionCount,
			final boolean ownerClosed,
			final boolean stateChanged,
			final List<Entry> entries) {
			this.ownerSequence = ownerSequence;
			this.previousWindow = previousWindow;
			this.currentWindow = currentWindow;
			this.ledgerVersion = ledgerVersion;
			this.openOwnerCount = openOwnerCount;
			this.referencedRegionCount = referencedRegionCount;
			this.ownerClosed = ownerClosed;
			this.stateChanged = stateChanged;
			this.entries = Collections.unmodifiableList(
				new ArrayList<Entry>(entries));
		}

		private static Change closedNoOp(
			final long ownerSequence,
			final long ledgerVersion,
			final int openOwnerCount,
			final int referencedRegionCount) {
			return new Change(
				ownerSequence, null, null, ledgerVersion, openOwnerCount,
				referencedRegionCount, true, false,
				Collections.<Entry>emptyList());
		}

		public long getOwnerSequence() {
			return ownerSequence;
		}

		public WorldRegionWindow getPreviousWindow() {
			return previousWindow;
		}

		public WorldRegionWindow getCurrentWindow() {
			return currentWindow;
		}

		public long getLedgerVersion() {
			return ledgerVersion;
		}

		public int getOpenOwnerCount() {
			return openOwnerCount;
		}

		public int getReferencedRegionCount() {
			return referencedRegionCount;
		}

		public boolean isOwnerClosed() {
			return ownerClosed;
		}

		public List<Entry> getEntries() {
			return entries;
		}

		public boolean isNoOp() {
			return !stateChanged;
		}

		public List<Entry> getGloballyAcquired() {
			return selectEntries(EntrySelection.GLOBALLY_ACQUIRED);
		}

		public List<Entry> getSharedAcquisitions() {
			return selectEntries(EntrySelection.SHARED_ACQUISITION);
		}

		public List<Entry> getGloballyReleased() {
			return selectEntries(EntrySelection.GLOBALLY_RELEASED);
		}

		public List<Entry> getSharedReleases() {
			return selectEntries(EntrySelection.SHARED_RELEASE);
		}

		private List<Entry> selectEntries(final EntrySelection selection) {
			List<Entry> selected = new ArrayList<Entry>();
			for (Entry entry : entries) {
				if (selection.matches(entry)) {
					selected.add(entry);
				}
			}
			return Collections.unmodifiableList(selected);
		}
	}

	private enum EntrySelection {
		GLOBALLY_ACQUIRED {
			@Override
			boolean matches(final Entry entry) {
				return entry.isGloballyAcquired();
			}
		},
		SHARED_ACQUISITION {
			@Override
			boolean matches(final Entry entry) {
				return entry.isSharedAcquisition();
			}
		},
		GLOBALLY_RELEASED {
			@Override
			boolean matches(final Entry entry) {
				return entry.isGloballyReleased();
			}
		},
		SHARED_RELEASE {
			@Override
			boolean matches(final Entry entry) {
				return entry.isSharedRelease();
			}
		};

		abstract boolean matches(Entry entry);
	}

	/** Immutable reference count at one ledger version. */
	public static final class Snapshot {
		private final WorldRegionKey logicalRegionKey;
		private final long ledgerVersion;
		private final int referenceCount;

		private Snapshot(
			final WorldRegionKey logicalRegionKey,
			final long ledgerVersion,
			final int referenceCount) {
			this.logicalRegionKey = logicalRegionKey;
			this.ledgerVersion = ledgerVersion;
			this.referenceCount = referenceCount;
		}

		public WorldRegionKey getLogicalRegionKey() {
			return logicalRegionKey;
		}

		public long getLedgerVersion() {
			return ledgerVersion;
		}

		public int getReferenceCount() {
			return referenceCount;
		}

		public boolean isReferenced() {
			return referenceCount > 0;
		}
	}
}
