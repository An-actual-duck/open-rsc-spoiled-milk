package orsc;

import java.util.LinkedHashMap;
import java.util.Map;

/** Exact failed-context quarantine; not permission to ignore arbitrary mismatches. */
final class NativeLayeredTerrainRecoveryState {
	private static final int MAX_REJECTED_CONTEXTS = 16;
	private final Map<Integer, String> rejected = new LinkedHashMap<Integer, String>();
	private boolean pending;

	boolean begin() {
		if (pending) return false;
		pending = true;
		return true;
	}
	boolean isPending() { return pending; }
	boolean hasContextGap() { return !rejected.isEmpty(); }
	int rejectedContextCount() { return rejected.size(); }
	void rejectContext(int sequence, String worldSpace, int level) {
		String scope = worldSpace + ":L" + level;
		String previous = rejected.get(sequence);
		if (previous != null && !previous.equals(scope)) {
			throw new IllegalStateException("Failed terrain context changed scope");
		}
		if (previous == null && rejected.size() >= MAX_REJECTED_CONTEXTS) {
			throw new IllegalStateException("Too many failed native terrain recovery contexts");
		}
		rejected.put(sequence, scope);
	}
	boolean rejects(int sequence) { return rejected.containsKey(sequence); }
	void reset() { pending = false; rejected.clear(); }
}
