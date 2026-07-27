package orsc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Connection-local access-order cache for exact protocol-v6 terrain sectors. */
public final class NativeLayeredTerrainResidentCache {
	public static final int DEFAULT_CAPACITY = 64;

	private final int capacity;
	private final LinkedHashMap<String, NativeLayeredTerrainChunk> resident;
	private long version;

	public NativeLayeredTerrainResidentCache() {
		this(DEFAULT_CAPACITY);
	}

	public NativeLayeredTerrainResidentCache(final int capacity) {
		if (capacity < 9 || capacity > 4096) {
			throw new IllegalArgumentException(
				"Native terrain resident-cache capacity is invalid");
		}
		this.capacity = capacity;
		this.resident =
			new LinkedHashMap<String, NativeLayeredTerrainChunk>(
				capacity, 0.75F, true);
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

	public synchronized void clear() {
		resident.clear();
		version = Math.addExact(version, 1L);
	}

	private synchronized void commit(final Transaction transaction) {
		if (transaction.owner != this || transaction.committed) {
			throw new IllegalStateException(
				"Native terrain resident transaction is invalid");
		}
		if (transaction.baseVersion != version) {
			throw new IllegalStateException(
				"Native terrain resident cache changed before receipt commit");
		}
		resident.clear();
		resident.putAll(transaction.staged);
		version = Math.addExact(version, 1L);
		transaction.committed = true;
	}

	private static LinkedHashMap<String, NativeLayeredTerrainChunk>
		copyAccessOrder(
			final LinkedHashMap<String, NativeLayeredTerrainChunk> source,
			final int capacity) {
		LinkedHashMap<String, NativeLayeredTerrainChunk> copy =
			new LinkedHashMap<String, NativeLayeredTerrainChunk>(
				capacity, 0.75F, true);
		for (Map.Entry<String, NativeLayeredTerrainChunk> entry
			: source.entrySet()) {
			copy.put(entry.getKey(), entry.getValue());
		}
		return copy;
	}

	private static void trim(
		final LinkedHashMap<String, NativeLayeredTerrainChunk> values,
		final int capacity) {
		while (values.size() > capacity) {
			String eldest = values.keySet().iterator().next();
			values.remove(eldest);
		}
	}

	private static String requireIdentity(final String value) {
		String checked = Objects.requireNonNull(
			value, "contentIdentity");
		if (checked.isEmpty() || checked.length() > 2048) {
			throw new IllegalArgumentException(
				"Native terrain resident identity is invalid");
		}
		return checked;
	}

	public static final class Transaction {
		private final NativeLayeredTerrainResidentCache owner;
		private final long baseVersion;
		private final LinkedHashMap<String, NativeLayeredTerrainChunk> staged;
		private boolean committed;

		private Transaction(
			final NativeLayeredTerrainResidentCache owner,
			final long baseVersion,
			final LinkedHashMap<String, NativeLayeredTerrainChunk> staged) {
			this.owner = owner;
			this.baseVersion = baseVersion;
			this.staged = staged;
		}

		public NativeLayeredTerrainChunk acceptPayload(
			final String contentIdentity,
			final NativeLayeredTerrainChunk chunk) {
			requireOpen();
			String checkedIdentity = requireIdentity(contentIdentity);
			NativeLayeredTerrainChunk checkedChunk = Objects.requireNonNull(
				chunk, "chunk");
			if (!checkedChunk.isAvailable()) {
				throw new IllegalArgumentException(
					"Native terrain resident payload must be available");
			}
			staged.put(checkedIdentity, checkedChunk);
			trim(staged, owner.capacity);
			return checkedChunk;
		}

		public NativeLayeredTerrainChunk resolveReference(
			final String contentIdentity) {
			requireOpen();
			NativeLayeredTerrainChunk chunk =
				staged.get(requireIdentity(contentIdentity));
			if (chunk == null) {
				throw new IllegalStateException(
					"Native terrain receipt references a missing resident sector");
			}
			return chunk;
		}

		public void commit() {
			requireOpen();
			owner.commit(this);
		}

		private void requireOpen() {
			if (committed) {
				throw new IllegalStateException(
					"Native terrain resident transaction is already committed");
			}
		}
	}
}
