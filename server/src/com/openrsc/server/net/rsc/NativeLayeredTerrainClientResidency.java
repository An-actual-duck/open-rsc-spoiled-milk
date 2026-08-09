package com.openrsc.server.net.rsc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic connection-local mirror of the matched client's native sector
 * cache.
 *
 * <p>A transaction simulates the exact access-order LRU changes needed by one
 * scene receipt, but does not publish them until the packet has been queued.
 * TCP ordering then guarantees that a later reference follows the payload
 * which established it. A reconnect creates a new Player and therefore an
 * empty mirror.</p>
 */
public final class NativeLayeredTerrainClientResidency {
	public static final int DEFAULT_CAPACITY = 64;

	private final int capacity;
	private final LinkedHashMap<String, Boolean> resident;
	private long version;

	public NativeLayeredTerrainClientResidency() {
		this(DEFAULT_CAPACITY);
	}

	public NativeLayeredTerrainClientResidency(final int capacity) {
		if (capacity < 9 || capacity > 4096) {
			throw new IllegalArgumentException(
				"Native terrain client residency capacity is invalid");
		}
		this.capacity = capacity;
		this.resident =
			new LinkedHashMap<String, Boolean>(capacity, 0.75F, true);
	}

	public synchronized Transaction begin() {
		return new Transaction(
			this,
			version,
			copyAccessOrder(resident, capacity));
	}

	public synchronized int size() {
		return resident.size();
	}

	/**
	 * Returns a read-only least-recently-used to most-recently-used snapshot of
	 * the server's model of the client cache. This supports diagnostics and
	 * parity tests without exposing mutable residency state.
	 */
	public synchronized List<String> getAccessOrder() {
		return Collections.unmodifiableList(
			new ArrayList<String>(resident.keySet()));
	}

	public synchronized void clear() {
		resident.clear();
		version = Math.addExact(version, 1L);
	}

	private synchronized void commit(final Transaction transaction) {
		if (transaction.owner != this || transaction.committed) {
			throw new IllegalStateException(
				"Native terrain residency transaction is invalid");
		}
		if (transaction.baseVersion != version) {
			throw new IllegalStateException(
				"Native terrain residency changed before packet commit");
		}
		resident.clear();
		resident.putAll(transaction.staged);
		version = Math.addExact(version, 1L);
		transaction.committed = true;
	}

	private static LinkedHashMap<String, Boolean> copyAccessOrder(
		final LinkedHashMap<String, Boolean> source,
		final int capacity) {
		LinkedHashMap<String, Boolean> copy =
			new LinkedHashMap<String, Boolean>(capacity, 0.75F, true);
		for (Map.Entry<String, Boolean> entry : source.entrySet()) {
			copy.put(entry.getKey(), Boolean.TRUE);
		}
		return copy;
	}

	private static void trim(
		final LinkedHashMap<String, Boolean> values,
		final int capacity) {
		while (values.size() > capacity) {
			String eldest = values.keySet().iterator().next();
			values.remove(eldest);
		}
	}

	public static final class Transaction {
		private final NativeLayeredTerrainClientResidency owner;
		private final long baseVersion;
		private final LinkedHashMap<String, Boolean> staged;
		private boolean committed;

		private Transaction(
			final NativeLayeredTerrainClientResidency owner,
			final long baseVersion,
			final LinkedHashMap<String, Boolean> staged) {
			this.owner = owner;
			this.baseVersion = baseVersion;
			this.staged = staged;
		}

		/**
		 * Returns true when this exact content must accompany the receipt.
		 * Every call also applies the same access-order touch the client will
		 * perform while decoding the slot.
		 */
		public boolean requiresPayload(final String contentIdentity) {
			if (committed) {
				throw new IllegalStateException(
					"Native terrain residency transaction is already committed");
			}
			String checked = Objects.requireNonNull(
				contentIdentity, "contentIdentity");
			if (checked.isEmpty() || checked.length() > 2048) {
				throw new IllegalArgumentException(
					"Native terrain residency identity is invalid");
			}
			boolean missing = !staged.containsKey(checked);
			staged.put(checked, Boolean.TRUE);
			trim(staged, owner.capacity);
			return missing;
		}

		public void commit() {
			owner.commit(this);
		}
	}
}
