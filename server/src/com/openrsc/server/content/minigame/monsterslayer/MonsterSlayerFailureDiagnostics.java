package com.openrsc.server.content.minigame.monsterslayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded duplicate suppression for optional Slayer-credit diagnostics.
 *
 * <p>This is deliberately process-transient: it suppresses repeated failures
 * without becoming account state or retaining an unbounded history of players.
 * The synchronized access-order map evicts the least recently seen entry.</p>
 */
public final class MonsterSlayerFailureDiagnostics {
	private static final int MAX_ENTRIES = 256;
	private static final Map<String, Boolean> RECENT_FAILURES =
		new LinkedHashMap<String, Boolean>(MAX_ENTRIES + 1, 0.75F, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
				return size() > MAX_ENTRIES;
			}
		};

	private MonsterSlayerFailureDiagnostics() {
	}

	/** Returns true only for the first retained occurrence of a failure key. */
	public static synchronized boolean shouldLog(UUID playerId, int npcId, String reason) {
		String key = playerId + ":" + npcId + ":" + reason;
		if (RECENT_FAILURES.containsKey(key)) {
			RECENT_FAILURES.get(key);
			return false;
		}
		RECENT_FAILURES.put(key, Boolean.TRUE);
		return true;
	}

	/** Exposed for bounded diagnostic health checks and executable regression coverage. */
	public static synchronized int retainedEntryCount() {
		return RECENT_FAILURES.size();
	}

	/** Test-only reset; this state never carries gameplay meaning. */
	public static synchronized void resetForTests() {
		RECENT_FAILURES.clear();
	}
}
